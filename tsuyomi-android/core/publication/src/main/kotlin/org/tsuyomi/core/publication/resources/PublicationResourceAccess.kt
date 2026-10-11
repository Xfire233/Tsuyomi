/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication.resources

import android.content.Context
import android.os.CancellationSignal
import android.net.DnsResolver
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executor
import java.util.zip.CRC32
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.core.files.StoredFile
import org.tsuyomi.core.network.HostHttpRequest
import org.tsuyomi.core.network.HostHttpResponse
import org.tsuyomi.core.network.HostNetworkError
import org.tsuyomi.core.network.HostNetworkException
import org.tsuyomi.core.network.HostResponseHeaders
import org.tsuyomi.core.network.PinnedHttpsHostTransport
import org.tsuyomi.core.network.PublicInternetAddressPolicy
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.NetworkMethod

/** Host-only fetch port for an explicitly granted, publication-bound inert HTTPS image. */
interface PublicationReaderResourceAccess {
    suspend fun fetch(
        publicationId: String,
        originalSha256: String,
        url: String,
    ): PublicationResourceFetchResult
}

data class PublicationResourceGrant(
    val publicationId: String,
    val originalSha256: String,
    val origins: Set<String>,
    val mimeTypes: Set<String>,
)

sealed interface PublicationResourceFetchResult {
    data class Success(
        val bytes: ByteArray,
        val mimeType: String,
    ) : PublicationResourceFetchResult

    data class Rejected(
        val status: PublicationResourceRejection,
    ) : PublicationResourceFetchResult
}

enum class PublicationResourceRejection {
    INVALID_INPUT,
    NO_GRANT,
    GRANT_MISMATCH,
    INVALID_URL,
    ORIGIN_NOT_GRANTED,
    DNS_UNRESOLVED,
    NON_PUBLIC_ADDRESS,
    NETWORK_FAILURE,
    TIMEOUT,
    REDIRECT_REJECTED,
    REDIRECT_LIMIT,
    HTTP_STATUS,
    CONTENT_TYPE,
    RESOURCE_LIMIT,
    PUBLICATION_QUOTA,
    INVALID_IMAGE,
    REVOKED,
    STORAGE_FAILURE,
}

internal fun interface PublicationResourceAddressResolver {
    suspend fun resolve(hostname: String): List<InetAddress>
}

internal fun interface PublicationPinnedTransport {
    suspend fun execute(request: HostHttpRequest, vettedAddresses: List<InetAddress>): HostHttpResponse
}

internal interface PublicationGrantPersistence {
    fun load(): Map<String, StoredPublicationResourceGrant>
    fun replace(grants: Map<String, StoredPublicationResourceGrant>)
}

internal interface PublicationResourceCache {
    fun read(relativePath: String): CachedPublicationResource?
    fun write(relativePath: String, resource: CachedPublicationResource)
    fun entries(): List<StoredFile>
    fun delete(relativePath: String): Boolean
}

internal data class StoredPublicationResourceGrant(
    val publicationId: String,
    val originalSha256: String,
    val origins: Set<String>,
    val mimeTypes: Set<String>,
    val generation: String,
)

internal data class CachedPublicationResource(
    val bytes: ByteArray,
    val mimeType: String,
)

internal data class PublicationResourceBudgets(
    val maxResourceBytes: Int = MAX_RESOURCE_BYTES,
    val maxPublicationBytes: Long = MAX_PUBLICATION_BYTES,
) {
    init {
        require(maxResourceBytes in 1..MAX_RESOURCE_BYTES)
        require(maxPublicationBytes >= maxResourceBytes)
    }
}

/**
 * Default-off HTTPS image gateway for a local publication. Grants are durable private host state;
 * fetched resources are disposable cache data and never become part of the publication original.
 */
class PublicationResourceAccess internal constructor(
    private val grantPersistence: PublicationGrantPersistence,
    private val addressResolver: PublicationResourceAddressResolver,
    private val transport: PublicationPinnedTransport,
    private val cache: PublicationResourceCache,
    private val budgets: PublicationResourceBudgets = PublicationResourceBudgets(),
) : PublicationReaderResourceAccess {
    private val lock = Mutex()
    private val activeFetches = mutableMapOf<String, MutableSet<ActiveFetch>>()
    private val publicationBudgets = mutableMapOf<BudgetKey, BudgetState>()

    constructor(context: Context) : this(defaultDependencies(context.applicationContext))

    private constructor(dependencies: ResourceAccessDependencies) : this(
        grantPersistence = dependencies.grantPersistence,
        addressResolver = dependencies.addressResolver,
        transport = dependencies.transport,
        cache = dependencies.cache,
        budgets = PublicationResourceBudgets(),
    )

    /** Replaces this publication's permission; a new generation invalidates every in-flight fetch. */
    suspend fun grant(
        publicationId: String,
        originalSha256: String,
        origins: Set<String>,
        mimeTypes: Set<String>,
    ): PublicationResourceGrant = withContext(Dispatchers.IO) {
        val normalized = normalizeGrant(publicationId, originalSha256, origins, mimeTypes)
        lock.withLock {
            val current = grantPersistence.load().toMutableMap()
            val stored = normalized
            current[publicationId] = stored
            grantPersistence.replace(current)
            invalidatePublication(publicationId)
            clearPublicationCache(publicationId)
            publicationBudgets.keys.removeAll { it.publicationId == publicationId }
            normalized.toPublicGrant()
        }
    }

    /** Revokes all network origins for this publication and prevents late results/cache writes. */
    suspend fun revoke(publicationId: String): Boolean = withContext(Dispatchers.IO) {
        requireCanonicalPublicationId(publicationId)
        lock.withLock {
            val current = grantPersistence.load().toMutableMap()
            if (current.remove(publicationId) == null) return@withLock false
            grantPersistence.replace(current)
            invalidatePublication(publicationId)
            clearPublicationCache(publicationId)
            publicationBudgets.keys.removeAll { it.publicationId == publicationId }
            true
        }
    }

    /** Returns the complete persisted permission only when its source digest still matches. */
    suspend fun grantSnapshot(
        publicationId: String,
        originalSha256: String,
    ): PublicationResourceGrant? = withContext(Dispatchers.IO) {
        if (!isCanonicalPublicationId(publicationId) || !isSha256(originalSha256)) return@withContext null
        lock.withLock {
            val stored = runCatching { grantPersistence.load()[publicationId] }.getOrNull()
            stored?.takeIf { it.originalSha256 == originalSha256 }?.toPublicGrant()
        }
    }

    /** Returns only origins bound to the exact current original digest; absent/mismatched is empty. */
    suspend fun origins(publicationId: String, originalSha256: String): Set<String> = withContext(Dispatchers.IO) {
        if (!isCanonicalPublicationId(publicationId) || !isSha256(originalSha256)) return@withContext emptySet()
        lock.withLock {
            val stored = runCatching { grantPersistence.load()[publicationId] }.getOrNull()
            if (stored?.originalSha256 == originalSha256) stored.origins.toSortedSet() else emptySet()
        }
    }

    override suspend fun fetch(
        publicationId: String,
        originalSha256: String,
        url: String,
    ): PublicationResourceFetchResult = withContext(Dispatchers.IO) {
        if (!isCanonicalPublicationId(publicationId) || !isSha256(originalSha256)) {
            return@withContext rejected(PublicationResourceRejection.INVALID_INPUT)
        }
        val preparation = lock.withLock { prepareFetch(publicationId, originalSha256, url) }
        when (preparation) {
            is Preparation.Denied -> rejected(preparation.status)
            is Preparation.Cached -> preparation.result
            is Preparation.Ready -> fetchPrepared(preparation.value)
        }
    }

    private fun prepareFetch(
        publicationId: String,
        originalSha256: String,
        rawUrl: String,
    ): Preparation {
        val stored = try {
            grantPersistence.load()[publicationId]
        } catch (_: Exception) {
            return Preparation.Denied(PublicationResourceRejection.STORAGE_FAILURE)
        } ?: return Preparation.Denied(PublicationResourceRejection.NO_GRANT)
        if (stored.originalSha256 != originalSha256) {
            invalidatePublication(publicationId)
            return Preparation.Denied(PublicationResourceRejection.GRANT_MISMATCH)
        }
        val initial = parseHttpsUrl(rawUrl)
            ?: return Preparation.Denied(PublicationResourceRejection.INVALID_URL)
        if (initial.origin !in stored.origins) {
            return Preparation.Denied(PublicationResourceRejection.ORIGIN_NOT_GRANTED)
        }

        val cachePath = cachePath(stored, initial.uri)
        val cached = runCatching { cache.read(cachePath) }.getOrNull()
        if (cached != null) {
            if (cached.mimeType in stored.mimeTypes && validateInertRaster(cached.bytes, cached.mimeType)) {
                return Preparation.Cached(PublicationResourceFetchResult.Success(cached.bytes, cached.mimeType))
            }
            runCatching { cache.delete(cachePath) }
        }

        val budgetKey = BudgetKey(publicationId, originalSha256)
        val budget = publicationBudgets.getOrPut(budgetKey) {
            val prefix = "$publicationId/$originalSha256/"
            BudgetState(usedBytes = runCatching {
                cache.entries().asSequence()
                    .filter { it.relativePath.startsWith(prefix) }
                    .sumOf { it.byteCount }
            }.getOrDefault(0L))
        }
        val available = budgets.maxPublicationBytes - budget.usedBytes - budget.reservedBytes
        if (available <= 0L) return Preparation.Denied(PublicationResourceRejection.PUBLICATION_QUOTA)
        val reservationBytes = minOf(budgets.maxResourceBytes.toLong(), available).toInt()
        budget.reservedBytes += reservationBytes
        val active = ActiveFetch()
        activeFetches.getOrPut(publicationId) { linkedSetOf() }.add(active)
        return Preparation.Ready(
            PreparedFetch(
                grant = stored,
                initial = initial,
                cachePath = cachePath,
                budget = budget,
                reservationBytes = reservationBytes,
                active = active,
            ),
        )
    }

    private suspend fun fetchPrepared(prepared: PreparedFetch): PublicationResourceFetchResult {
        var committed = false
        try {
            val downloaded = withTimeout(TOTAL_FETCH_TIMEOUT_MS) { fetchUntilComplete(prepared) }
            if (downloaded is DownloadResult.Denied) return rejected(downloaded.status)
            val resource = (downloaded as DownloadResult.Value).resource
            return lock.withLock {
                val current = try {
                    grantPersistence.load()[prepared.grant.publicationId]
                } catch (_: Exception) {
                    null
                }
                val stillLive = current != null && current.generation == prepared.grant.generation &&
                    current.originalSha256 == prepared.grant.originalSha256
                if (prepared.active.invalidated.isCompleted || !stillLive) {
                    return@withLock rejected(PublicationResourceRejection.REVOKED)
                }
                if (resource.bytes.size > prepared.reservationBytes) {
                    return@withLock rejected(
                        if (prepared.reservationBytes < budgets.maxResourceBytes) {
                            PublicationResourceRejection.PUBLICATION_QUOTA
                        } else {
                            PublicationResourceRejection.RESOURCE_LIMIT
                        },
                    )
                }
                val budget = prepared.budget
                budget.reservedBytes -= prepared.reservationBytes
                budget.usedBytes += resource.bytes.size
                committed = true
                runCatching { cache.write(prepared.cachePath, resource) }
                PublicationResourceFetchResult.Success(resource.bytes, resource.mimeType)
            }
        } catch (timeout: TimeoutCancellationException) {
            if (currentCoroutineContext().isActive) return rejected(PublicationResourceRejection.TIMEOUT)
            throw timeout
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return rejected(PublicationResourceRejection.NETWORK_FAILURE)
        } finally {
            withContext(NonCancellable) {
                lock.withLock {
                    if (!committed) prepared.budget.reservedBytes -= prepared.reservationBytes
                    activeFetches[prepared.grant.publicationId]?.let { active ->
                        active.remove(prepared.active)
                        if (active.isEmpty()) activeFetches.remove(prepared.grant.publicationId)
                    }
                }
            }
        }
    }

    private suspend fun fetchUntilComplete(prepared: PreparedFetch): DownloadResult = coroutineScope<DownloadResult> {
        if (prepared.active.invalidated.isCompleted) {
            return@coroutineScope DownloadResult.Denied(PublicationResourceRejection.REVOKED)
        }
        val operation = async { performFetch(prepared) }
        try {
            select<DownloadResult> {
                operation.onAwait { it }
                prepared.active.invalidated.onAwait {
                    DownloadResult.Denied(PublicationResourceRejection.REVOKED)
                }
            }
        } finally {
            if (!operation.isCompleted) operation.cancel()
        }
    }

    private suspend fun performFetch(prepared: PreparedFetch): DownloadResult {
        val requestBudget = prepared.reservationBytes
        var current = prepared.initial
        var redirects = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (prepared.active.invalidated.isCompleted) return DownloadResult.Denied(PublicationResourceRejection.REVOKED)
            val resolution = resolvePublicAddresses(current)
            if (resolution is AddressResolution.Denied) return DownloadResult.Denied(resolution.status)
            val addresses = (resolution as AddressResolution.Allowed).addresses
            if (prepared.active.invalidated.isCompleted) return DownloadResult.Denied(PublicationResourceRejection.REVOKED)
            currentCoroutineContext().ensureActive()
            val request = HostHttpRequest(
                url = current.uri,
                method = NetworkMethod.GET,
                headers = mapOf("Accept" to prepared.grant.mimeTypes.sorted().joinToString(", ")),
                decode = DecodeMode.AUTO,
                body = null,
                referrer = null,
                timeoutMs = NETWORK_TIMEOUT_MS,
                maxResponseBytes = requestBudget,
            )
            val response = try {
                transport.execute(request, addresses)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: HostNetworkException) {
                return DownloadResult.Denied(failure.toPublicationRejection(requestBudget))
            } catch (_: Exception) {
                return DownloadResult.Denied(PublicationResourceRejection.NETWORK_FAILURE)
            }
            currentCoroutineContext().ensureActive()
            if (response.finalUrl.toASCIIString() != current.uri.toASCIIString()) {
                return DownloadResult.Denied(PublicationResourceRejection.REDIRECT_REJECTED)
            }
            if (response.status in REDIRECT_STATUSES) {
                if (redirects >= MAX_REDIRECTS) {
                    return DownloadResult.Denied(PublicationResourceRejection.REDIRECT_LIMIT)
                }
                val locations = response.headerValues("location")
                if (locations.size != 1) return DownloadResult.Denied(PublicationResourceRejection.REDIRECT_REJECTED)
                val nextUri = runCatching { current.uri.resolve(URI(locations.single())) }.getOrNull()
                    ?: return DownloadResult.Denied(PublicationResourceRejection.REDIRECT_REJECTED)
                val next = parseHttpsUrl(nextUri.toASCIIString())
                    ?: return DownloadResult.Denied(PublicationResourceRejection.REDIRECT_REJECTED)
                if (next.origin != prepared.initial.origin) {
                    return DownloadResult.Denied(PublicationResourceRejection.REDIRECT_REJECTED)
                }
                current = next
                redirects++
                continue
            }
            if (response.status != 200) return DownloadResult.Denied(PublicationResourceRejection.HTTP_STATUS)

            val contentTypes = response.headerValues("content-type")
            if (contentTypes.size != 1) return DownloadResult.Denied(PublicationResourceRejection.CONTENT_TYPE)
            val mimeType = contentTypes.single().substringBefore(';').trim().lowercase(Locale.ROOT)
            if (mimeType !in prepared.grant.mimeTypes || mimeType !in SUPPORTED_RASTER_MIME_TYPES) {
                return DownloadResult.Denied(PublicationResourceRejection.CONTENT_TYPE)
            }
            val contentEncodings = response.headerValues("content-encoding")
            if (contentEncodings.any { !it.equals("identity", ignoreCase = true) }) {
                return DownloadResult.Denied(PublicationResourceRejection.NETWORK_FAILURE)
            }
            val contentLengths = response.headerValues("content-length")
            val transferEncodings = response.headerValues("transfer-encoding")
            if (contentLengths.size > 1 || (contentLengths.isNotEmpty() && transferEncodings.isNotEmpty())) {
                return DownloadResult.Denied(PublicationResourceRejection.NETWORK_FAILURE)
            }
            if (contentLengths.isNotEmpty()) {
                val declaredLength = parseContentLength(contentLengths.single())
                    ?: return DownloadResult.Denied(PublicationResourceRejection.NETWORK_FAILURE)
                if (declaredLength > budgets.maxResourceBytes) {
                    return DownloadResult.Denied(PublicationResourceRejection.RESOURCE_LIMIT)
                }
                if (declaredLength > requestBudget) {
                    return DownloadResult.Denied(
                        if (requestBudget < budgets.maxResourceBytes) {
                            PublicationResourceRejection.PUBLICATION_QUOTA
                        } else {
                            PublicationResourceRejection.RESOURCE_LIMIT
                        },
                    )
                }
            }
            if (response.bytes.size > budgets.maxResourceBytes) {
                return DownloadResult.Denied(PublicationResourceRejection.RESOURCE_LIMIT)
            }
            if (response.bytes.size > requestBudget) {
                return DownloadResult.Denied(
                    if (requestBudget < budgets.maxResourceBytes) {
                        PublicationResourceRejection.PUBLICATION_QUOTA
                    } else {
                        PublicationResourceRejection.RESOURCE_LIMIT
                    },
                )
            }
            if (!validateInertRaster(response.bytes, mimeType)) {
                return DownloadResult.Denied(PublicationResourceRejection.INVALID_IMAGE)
            }
            return DownloadResult.Value(CachedPublicationResource(response.bytes, mimeType))
        }
    }


    private suspend fun resolvePublicAddresses(target: HttpsTarget): AddressResolution {
        val addresses = if (target.literalAddress != null) {
            listOf(target.literalAddress)
        } else {
            try {
                addressResolver.resolve(target.host)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return AddressResolution.Denied(PublicationResourceRejection.DNS_UNRESOLVED)
            }
        }
        if (addresses.isEmpty()) return AddressResolution.Denied(PublicationResourceRejection.DNS_UNRESOLVED)
        val unique = addresses.distinct()
        if (unique.any { !PublicInternetAddressPolicy.isPublicGlobalUnicast(it) }) {
            return AddressResolution.Denied(PublicationResourceRejection.NON_PUBLIC_ADDRESS)
        }
        return AddressResolution.Allowed(unique)
    }

    private fun invalidatePublication(publicationId: String) {
        activeFetches.remove(publicationId)?.forEach { it.invalidated.complete(Unit) }
    }

    private fun clearPublicationCache(publicationId: String) {
        runCatching {
            cache.entries().asSequence()
                .filter { it.relativePath.startsWith("$publicationId/") }
                .forEach { cache.delete(it.relativePath) }
        }
    }

    private data class ActiveFetch(val invalidated: CompletableDeferred<Unit> = CompletableDeferred())

    private data class BudgetKey(val publicationId: String, val originalSha256: String)

    private data class BudgetState(var usedBytes: Long, var reservedBytes: Long = 0)

    private data class PreparedFetch(
        val grant: StoredPublicationResourceGrant,
        val initial: HttpsTarget,
        val cachePath: String,
        val budget: BudgetState,
        val reservationBytes: Int,
        val active: ActiveFetch,
    )

    private sealed interface Preparation {
        data class Denied(val status: PublicationResourceRejection) : Preparation
        data class Cached(val result: PublicationResourceFetchResult) : Preparation
        data class Ready(val value: PreparedFetch) : Preparation
    }

    private sealed interface AddressResolution {
        data class Denied(val status: PublicationResourceRejection) : AddressResolution
        data class Allowed(val addresses: List<InetAddress>) : AddressResolution
    }
    private sealed interface DownloadResult {
        data class Denied(val status: PublicationResourceRejection) : DownloadResult
        data class Value(val resource: CachedPublicationResource) : DownloadResult
    }



    private fun StoredPublicationResourceGrant.toPublicGrant() = PublicationResourceGrant(
        publicationId = publicationId,
        originalSha256 = originalSha256,
        origins = origins.toSortedSet(),
        mimeTypes = mimeTypes.toSortedSet(),
    )

    private fun HostHttpResponse.headerValues(name: String): List<String> = buildList {
        headers.forEachValue(name) { add(it) }
    }

    private fun HostNetworkException.toPublicationRejection(reservationBytes: Int): PublicationResourceRejection = when (error) {
        HostNetworkError.TIMEOUT -> PublicationResourceRejection.TIMEOUT
        HostNetworkError.RESPONSE_LIMIT -> if (reservationBytes < budgets.maxResourceBytes) {
            PublicationResourceRejection.PUBLICATION_QUOTA
        } else {
            PublicationResourceRejection.RESOURCE_LIMIT
        }
        HostNetworkError.DISALLOWED_ORIGIN -> PublicationResourceRejection.NON_PUBLIC_ADDRESS
        HostNetworkError.INVALID_REQUEST,
        HostNetworkError.HEADER_DISALLOWED,
        HostNetworkError.REDIRECT_DISALLOWED,
        HostNetworkError.REDIRECT_LIMIT,
        -> PublicationResourceRejection.REDIRECT_REJECTED
        else -> PublicationResourceRejection.NETWORK_FAILURE
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val TOTAL_FETCH_TIMEOUT_MS = 25_000L
        const val NETWORK_TIMEOUT_MS = 10_000
        const val MAX_RESOURCE_BYTES = 4 * 1024 * 1024
        val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
        val SUPPORTED_RASTER_MIME_TYPES = setOf("image/jpeg", "image/png")
    }
}

private data class ResourceAccessDependencies(
    val grantPersistence: PublicationGrantPersistence,
    val addressResolver: PublicationResourceAddressResolver,
    val transport: PublicationPinnedTransport,
    val cache: PublicationResourceCache,
)

private fun defaultDependencies(context: Context): ResourceAccessDependencies {
    val roots = StorageRoots.from(context)
    val pinnedTransport = PinnedHttpsHostTransport()
    return ResourceAccessDependencies(
        grantPersistence = PrivatePublicationGrantPersistence(
            QuotaFileStore(
                roots = roots,
                root = StorageRoot.NO_BACKUP,
                namespace = "publication-resource-grants",
                quota = StorageQuota(maxBytes = MAX_GRANT_STORE_BYTES, maxEntries = 1),
            ),
        ),
        addressResolver = AndroidPublicationResourceAddressResolver(),
        transport = PublicationPinnedTransport(pinnedTransport::execute),
        cache = QuotaPublicationResourceCache(
            QuotaFileStore(
                roots = roots,
                root = StorageRoot.CACHE,
                namespace = "publication-resource-cache",
                quota = StorageQuota(maxBytes = MAX_RESOURCE_CACHE_BYTES, maxEntries = MAX_RESOURCE_CACHE_ENTRIES),
            ),
        ),
    )
}

private data class HttpsTarget(
    val uri: URI,
    val host: String,
    val origin: String,
    val literalAddress: InetAddress?,
)

private fun parseHttpsUrl(value: String): HttpsTarget? {
    val parsed = runCatching { URI(value) }.getOrNull() ?: return null
    if (
        parsed.scheme?.equals("https", ignoreCase = true) != true || parsed.isOpaque ||
        parsed.rawUserInfo != null || parsed.port != -1 && parsed.port != 443
    ) return null
    val rawHost = parsed.host?.removeSurrounding("[", "]")?.takeIf { it.isNotBlank() && '%' !in it } ?: return null
    val host = rawHost.lowercase(Locale.ROOT)
    val literal = parseAddressLiteral(host)
    if (literal == null && (':' in host || host.all { it in '0'..'9' || it == '.' })) return null
    if (isReservedHostname(host)) return null
    if (literal == null && !isPublicDnsName(host)) return null
    val originHost = if (':' in host) "[$host]" else host
    val origin = "https://$originHost"
    val ascii = runCatching { URI(parsed.toASCIIString().substringBefore('#')) }.getOrNull() ?: return null
    if (ascii.rawPath?.any { it == '\r' || it == '\n' } == true || ascii.rawQuery?.any { it == '\r' || it == '\n' } == true) return null
    return HttpsTarget(ascii, host, origin, literal)
}

private fun parseGrantOrigin(value: String): String? {
    val parsed = runCatching { URI(value) }.getOrNull() ?: return null
    if (!parsed.rawPath.isNullOrEmpty() || parsed.rawQuery != null || parsed.rawFragment != null) return null
    val target = parseHttpsUrl(value) ?: return null
    return target.origin.takeIf { value == it }
}

private fun parseAddressLiteral(host: String): InetAddress? {
    if (isStrictIpv4(host)) {
        val bytes = host.split('.').map { it.toInt().toByte() }.toByteArray()
        return InetAddress.getByAddress(bytes)
    }
    if (':' !in host) return null
    if (host.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' && it != ':' && it != '.' }) return null
    val parsed = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
    return parsed.takeIf { it is Inet6Address && it.address.size == 16 }
}

private fun isStrictIpv4(host: String): Boolean {
    val parts = host.split('.')
    return parts.size == 4 && parts.all { part ->
        part.isNotEmpty() && (part == "0" || !part.startsWith('0')) && part.all { it in '0'..'9' } &&
            part.toIntOrNull()?.let { it in 0..255 } == true
    }
}

private fun isPublicDnsName(host: String): Boolean {
    if (host.length !in 1..253 || '.' !in host || host.endsWith('.')) return false
    return host.split('.').all { label ->
        if (label.length !in 1..63) {
            false
        } else {
            val first = label.first()
            val last = label.last()
            (first in 'a'..'z' || first in '0'..'9') &&
                (last in 'a'..'z' || last in '0'..'9') &&
                label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
        }
    }
}

private fun isReservedHostname(host: String): Boolean =
    host == "localhost" || listOf(".localhost", ".local", ".internal", ".test", ".invalid").any(host::endsWith)

private fun isCanonicalPublicationId(value: String): Boolean =
    runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

private fun requireCanonicalPublicationId(value: String) {
    require(isCanonicalPublicationId(value)) { "publicationId must be a canonical lowercase UUID" }
}

private fun isSha256(value: String): Boolean = SHA256_PATTERN.matches(value)

private fun normalizeGrant(
    publicationId: String,
    originalSha256: String,
    origins: Set<String>,
    mimeTypes: Set<String>,
): StoredPublicationResourceGrant {
    requireCanonicalPublicationId(publicationId)
    require(isSha256(originalSha256)) { "originalSha256 must be 64 lowercase hexadecimal characters" }
    require(origins.size in 1..MAX_ORIGINS_PER_GRANT) { "A grant needs 1..$MAX_ORIGINS_PER_GRANT origins" }
    val canonicalOrigins = origins.map { value ->
        val canonical = parseGrantOrigin(value)
            ?: throw IllegalArgumentException("Origin must be a canonical public HTTPS origin")
        val target = parseHttpsUrl(canonical) ?: throw IllegalArgumentException("Invalid public HTTPS origin")
        require(target.literalAddress == null || PublicInternetAddressPolicy.isPublicGlobalUnicast(target.literalAddress)) {
            "Grant origins must be globally reachable"
        }
        canonical
    }.toSet()
    require(canonicalOrigins.size == origins.size) { "Grant origins must be unique after canonicalization" }
    require(mimeTypes.isNotEmpty() && mimeTypes.all { it in SUPPORTED_MIME_TYPES }) {
        "Only explicitly selected image/jpeg and image/png resources are supported"
    }
    return StoredPublicationResourceGrant(
        publicationId = publicationId,
        originalSha256 = originalSha256,
        origins = canonicalOrigins,
        mimeTypes = mimeTypes.toSet(),
        generation = UUID.randomUUID().toString(),
    )
}


internal class PrivatePublicationGrantPersistence(private val files: QuotaFileStore) : PublicationGrantPersistence {
    override fun load(): Map<String, StoredPublicationResourceGrant> {
        val encoded = files.read(GRANT_FILE) ?: return emptyMap()
        return try {
            decodeGrants(encoded)
        } catch (_: Exception) {
            runCatching { files.delete(GRANT_FILE) }
            emptyMap()
        }
    }

    override fun replace(grants: Map<String, StoredPublicationResourceGrant>) {
        require(grants.size <= MAX_STORED_GRANTS)
        files.write(GRANT_FILE, encodeGrants(grants))
    }

    private fun encodeGrants(grants: Map<String, StoredPublicationResourceGrant>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(GRANTS_MAGIC)
            output.writeInt(grants.size)
            grants.toSortedMap().forEach { (publicationId, grant) ->
                output.writeUTF(publicationId)
                output.writeUTF(grant.originalSha256)
                output.writeUTF(grant.generation)
                output.writeInt(grant.origins.size)
                grant.origins.sorted().forEach(output::writeUTF)
                output.writeInt(grant.mimeTypes.size)
                grant.mimeTypes.sorted().forEach(output::writeUTF)
            }
        }
        return bytes.toByteArray()
    }

    private fun decodeGrants(bytes: ByteArray): Map<String, StoredPublicationResourceGrant> {
        require(bytes.size <= MAX_GRANT_STORE_BYTES.toInt())
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == GRANTS_MAGIC)
            val count = input.readInt()
            require(count in 0..MAX_STORED_GRANTS)
            val grants = LinkedHashMap<String, StoredPublicationResourceGrant>(count)
            repeat(count) {
                val publicationId = input.readUTF()
                val digest = input.readUTF()
                val generation = input.readUTF()
                val originCount = input.readInt()
                require(originCount in 1..MAX_ORIGINS_PER_GRANT)
                val origins = List(originCount) { input.readUTF() }.toSet()
                val mimeCount = input.readInt()
                require(mimeCount in 1..SUPPORTED_MIME_TYPES.size)
                val mimeTypes = List(mimeCount) { input.readUTF() }.toSet()
                require(isCanonicalPublicationId(publicationId) && isSha256(digest))
                require(UUID.fromString(generation).toString() == generation)
                require(origins.size == originCount && origins.all { parseGrantOrigin(it) == it })
                require(mimeTypes.size == mimeCount && mimeTypes.all { it in SUPPORTED_MIME_TYPES })
                require(grants.put(publicationId, StoredPublicationResourceGrant(
                    publicationId = publicationId,
                    originalSha256 = digest,
                    origins = origins,
                    mimeTypes = mimeTypes,
                    generation = generation,
                )) == null)
            }
            require(input.available() == 0)
            return grants
        }
    }

    private companion object {
        const val GRANT_FILE = "grants.bin"
    }
}

private class QuotaPublicationResourceCache(private val files: QuotaFileStore) : PublicationResourceCache {
    override fun read(relativePath: String): CachedPublicationResource? {
        val bytes = files.read(relativePath) ?: return null
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == CACHE_MAGIC)
                val mimeType = input.readUTF()
                require(mimeType in SUPPORTED_MIME_TYPES)
                val size = input.readInt()
                require(size in 0..MAX_RESOURCE_BYTES && size == input.available())
                CachedPublicationResource(ByteArray(size).also(input::readFully), mimeType)
            }
        }.getOrNull()
    }

    override fun write(relativePath: String, resource: CachedPublicationResource) {
        val bytes = ByteArrayOutputStream(resource.bytes.size + 64)
        DataOutputStream(bytes).use { output ->
            output.writeInt(CACHE_MAGIC)
            output.writeUTF(resource.mimeType)
            output.writeInt(resource.bytes.size)
            output.write(resource.bytes)
        }
        files.write(relativePath, bytes.toByteArray())
    }

    override fun entries(): List<StoredFile> = files.entries()

    override fun delete(relativePath: String): Boolean = files.delete(relativePath)
}

private class AndroidPublicationResourceAddressResolver : PublicationResourceAddressResolver {
    override suspend fun resolve(hostname: String): List<InetAddress> = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            DnsResolver.getInstance().query(
                null,
                hostname,
                DnsResolver.FLAG_NO_CACHE_LOOKUP,
                DIRECT_EXECUTOR,
                signal,
                object : DnsResolver.Callback<List<InetAddress>> {
                    override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                        if (!continuation.isActive) return
                        if (rcode != 0) {
                            continuation.resumeWith(Result.failure(IllegalStateException("DNS response failed")))
                        } else {
                            continuation.resumeWith(Result.success(answer.toList()))
                        }
                    }

                    override fun onError(error: DnsResolver.DnsException) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                    }
                },
            )
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(error))
        }
    }

    private companion object {
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}


private fun cachePath(grant: StoredPublicationResourceGrant, uri: URI): String =
    "${grant.publicationId}/${grant.originalSha256}/${grant.generation}/${sha256Hex(uri.toASCIIString())}.bin"

private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
}

private fun validateInertRaster(bytes: ByteArray, mimeType: String): Boolean = when (mimeType) {
    "image/png" -> validatePng(bytes)
    "image/jpeg" -> validateJpeg(bytes)
    else -> false
}

private fun validatePng(bytes: ByteArray): Boolean {
    val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    if (bytes.size < 45 || !bytes.copyOfRange(0, signature.size).contentEquals(signature)) return false
    var offset = signature.size
    var chunks = 0
    var width = 0L
    var height = 0L
    var colorType = -1
    var sawHeader = false
    var sawPalette = false
    var sawImageData = false
    var endedImageData = false
    while (offset <= bytes.size - 12 && ++chunks <= MAX_PNG_CHUNKS) {
        val length = readU32(bytes, offset)
        if (length > (bytes.size - offset - 12).toLong()) return false
        val typeOffset = offset + 4
        val type = String(bytes, typeOffset, 4, StandardCharsets.US_ASCII)
        val dataOffset = typeOffset + 4
        val crcOffset = dataOffset + length.toInt()
        val crc = CRC32().apply { update(bytes, typeOffset, 4 + length.toInt()) }.value
        if (crc != readU32(bytes, crcOffset)) return false
        if (!type.all { it in 'A'..'Z' || it in 'a'..'z' }) return false
        if (!sawHeader && (type != "IHDR" || length != 13L)) return false
        if (type == "acTL" || type == "fcTL" || type == "fdAT") return false
        if (sawImageData && type != "IDAT" && type != "IEND") endedImageData = true
        when (type) {
            "IHDR" -> {
                if (sawHeader || length != 13L) return false
                width = readU32(bytes, dataOffset)
                height = readU32(bytes, dataOffset + 4)
                val bitDepth = bytes[dataOffset + 8].toInt() and 0xff
                colorType = bytes[dataOffset + 9].toInt() and 0xff
                val compression = bytes[dataOffset + 10].toInt() and 0xff
                val filter = bytes[dataOffset + 11].toInt() and 0xff
                val interlace = bytes[dataOffset + 12].toInt() and 0xff
                if (!validPngBitDepth(colorType, bitDepth) || compression != 0 || filter != 0 || interlace !in 0..1) return false
                if (!validImageGeometry(width, height)) return false
                sawHeader = true
            }
            "PLTE" -> {
                if (!sawHeader || sawImageData || length == 0L || length > 768L || length % 3L != 0L) return false
                sawPalette = true
            }
            "IDAT" -> {
                if (!sawHeader || endedImageData) return false
                sawImageData = true
            }
            "IEND" -> {
                if (length != 0L || !sawImageData || (colorType == 3 && !sawPalette)) return false
                return crcOffset + 4 == bytes.size
            }
            else -> if (type[0] in 'A'..'Z') return false
        }
        offset = crcOffset + 4
    }
    return false
}

private fun validPngBitDepth(colorType: Int, bitDepth: Int): Boolean = when (colorType) {
    0 -> bitDepth in setOf(1, 2, 4, 8, 16)
    2 -> bitDepth == 8 || bitDepth == 16
    3 -> bitDepth in setOf(1, 2, 4, 8)
    4, 6 -> bitDepth == 8 || bitDepth == 16
    else -> false
}

private fun validateJpeg(bytes: ByteArray): Boolean {
    if (bytes.size < 16 || bytes[0] != 0xff.toByte() || bytes[1] != 0xd8.toByte()) return false
    if (bytes[bytes.lastIndex - 1] != 0xff.toByte() || bytes.last() != 0xd9.toByte()) return false
    var offset = 2
    var markers = 0
    var sawFrame = false
    while (offset < bytes.size - 2 && ++markers <= MAX_JPEG_MARKERS) {
        if (bytes[offset] != 0xff.toByte()) return false
        while (offset < bytes.size && bytes[offset] == 0xff.toByte()) offset++
        if (offset >= bytes.size) return false
        val marker = bytes[offset++].toInt() and 0xff
        if (marker == 0xd9) return sawFrame && offset == bytes.size
        if (marker == 0xd8 || marker == 0x01 || marker in 0xd0..0xd7) continue
        if (offset > bytes.size - 2) return false
        val segmentLength = readU16(bytes, offset)
        if (segmentLength < 2 || offset + segmentLength > bytes.size) return false
        val isFrame = marker in setOf(0xc0, 0xc1, 0xc2)
        if (marker in SOF_MARKERS && !isFrame) return false
        if (isFrame) {
            if (sawFrame || segmentLength < 8) return false
            val precision = bytes[offset + 2].toInt() and 0xff
            val height = readU16(bytes, offset + 3).toLong()
            val width = readU16(bytes, offset + 5).toLong()
            val components = bytes[offset + 7].toInt() and 0xff
            if (precision != 8 || components !in 1..4 || segmentLength != 8 + 3 * components || !validImageGeometry(width, height)) return false
            sawFrame = true
        }
        if (marker == 0xda) {
            if (!sawFrame || segmentLength < 6) return false
            val scanStart = offset + segmentLength
            for (index in scanStart until bytes.size - 1) {
                if (bytes[index] == 0xff.toByte() && bytes[index + 1] == 0xd9.toByte()) {
                    return index + 2 == bytes.size
                }
            }
            return false
        }
        offset += segmentLength
    }
    return false
}

private fun validImageGeometry(width: Long, height: Long): Boolean =
    width in 1..MAX_IMAGE_EDGE.toLong() && height in 1..MAX_IMAGE_EDGE.toLong() && width * height <= MAX_IMAGE_PIXELS

private fun readU16(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

private fun readU32(bytes: ByteArray, offset: Int): Long =
    ((bytes[offset].toLong() and 0xff) shl 24) or
        ((bytes[offset + 1].toLong() and 0xff) shl 16) or
        ((bytes[offset + 2].toLong() and 0xff) shl 8) or
        (bytes[offset + 3].toLong() and 0xff)

private fun parseContentLength(value: String): Long? =
    if (value.isEmpty() || value.any { it !in '0'..'9' }) null else value.toLongOrNull()

private fun rejected(status: PublicationResourceRejection) = PublicationResourceFetchResult.Rejected(status)

private const val SHA256_PATTERN_SOURCE = "^[a-f0-9]{64}$"
private val SHA256_PATTERN = Regex(SHA256_PATTERN_SOURCE)
private val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png")
private val SOF_MARKERS = setOf(0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf)
private const val MAX_JPEG_MARKERS = 1_024
private const val MAX_RESOURCE_BYTES = 4 * 1024 * 1024
private const val MAX_PUBLICATION_BYTES = 32L * 1024 * 1024
private const val MAX_IMAGE_EDGE = 16_384
private const val MAX_IMAGE_PIXELS = 40_000_000L
private const val MAX_PNG_CHUNKS = 4_096
private const val MAX_GRANT_STORE_BYTES = 8L * 1024 * 1024
private const val MAX_STORED_GRANTS = 1_024
private const val MAX_ORIGINS_PER_GRANT = 16
private const val GRANTS_MAGIC = 0x50524731
private const val CACHE_MAGIC = 0x50524331
private const val MAX_RESOURCE_CACHE_BYTES = 128L * 1024 * 1024
private const val MAX_RESOURCE_CACHE_ENTRIES = 2_048
