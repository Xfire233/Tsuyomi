/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.erdtman.jcs.JsonCanonicalizer
import org.tsuyomi.shared.sourcecontract.SourceId

/** Explicit trust anchor for a repository. No default production root exists. */
data class RepositoryRoot(
    val repositoryId: String,
    val indexUrl: String,
    val signingKey: PublisherKey,
) {
    init {
        SourceId(repositoryId)
        require(signingKey.trust in setOf(
            PublisherTrust.BUILT_IN_OFFICIAL,
            PublisherTrust.BUILT_IN_TEST,
            PublisherTrust.USER_ADDED,
        )) { "Repository root has an unsupported trust class" }
        requireHttpsRepositoryUrl(indexUrl)
    }

    /** A root's classification, not link text or catalog content, classifies its publishers. */
    val publisherTrust: PublisherTrust
        get() = signingKey.trust
}

/** Host API interval declared by a repository package and bound to the downloaded HXP manifest. */
data class RepositoryHostApi(
    val minInclusive: SemanticVersion,
    val maxExclusive: SemanticVersion,
) {
    init {
        require(minInclusive < maxExclusive) { "Repository host API interval is invalid" }
    }

    fun contains(hostVersion: SemanticVersion): Boolean = hostVersion >= minInclusive && hostVersion < maxExclusive
}

/** Root-authorized transition from one exact active archive to a new publisher. */
data class RepositoryLegacyMigration(
    val fromPublisherFingerprint: String,
    val fromPackageSha256: String,
)

/** Immutable metadata for one root-authorized downloadable HXP package. */
data class RepositoryPackage(
    val id: String,
    val name: String,
    val version: String,
    val summary: String,
    val language: String,
    val license: String,
    val sourceUrl: String,
    val sourceRevision: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
    val hostApi: RepositoryHostApi,
    val publisherKeyId: String,
    val legacyMigration: RepositoryLegacyMigration? = null,
) {
    private val parsedVersion: SemanticVersion = SemanticVersion.parse(version)

    fun isCompatible(hostVersion: SemanticVersion): Boolean = hostApi.contains(hostVersion)

    internal fun binding(publisherFingerprint: String): RepositoryInstallBinding = RepositoryInstallBinding(
        sourceId = SourceId(id),
        version = parsedVersion,
        packageSha256 = sha256,
        publisherKeyId = publisherKeyId,
        publisherFingerprint = publisherFingerprint,
        hostApi = hostApi,
        legacyMigration = legacyMigration,
    )
}

/** Verified catalog metadata. Expired instances remain observable but cannot authorize an install. */
class RepositoryCatalog internal constructor(
    val repositoryId: String,
    val sequence: Long,
    val issuedAt: Instant,
    val expiresAt: Instant,
    publishers: List<PublisherKey>,
    packages: List<RepositoryPackage>,
    revokedPublisherFingerprints: Set<String>,
    revokedPackageDigests: Set<String>,
    internal val signedDigest: String,
    internal val encodedEnvelope: ByteArray,
) {
    private val authenticatedPublishers = Collections.unmodifiableList(publishers.map(::copyPublisherKey))
    private val authenticatedPackages = Collections.unmodifiableList(packages.toList())
    private val authenticatedRevokedPublisherFingerprints = Collections.unmodifiableSet(revokedPublisherFingerprints.toSet())
    private val authenticatedRevokedPackageDigests = Collections.unmodifiableSet(revokedPackageDigests.toSet())

    val publishers: List<PublisherKey>
        get() = authenticatedPublishers.map(::copyPublisherKey)
    val packages: List<RepositoryPackage>
        get() = authenticatedPackages
    val revokedPublisherFingerprints: Set<String>
        get() = authenticatedRevokedPublisherFingerprints
    val revokedPackageDigests: Set<String>
        get() = authenticatedRevokedPackageDigests

    fun isExpired(clock: Instant = Instant.now()): Boolean = !expiresAt.isAfter(clock)

    internal fun publisher(keyId: String): PublisherKey? = authenticatedPublishers.singleOrNull { it.keyId == keyId }?.let(::copyPublisherKey)
}

/** Bounded transport seam. Production uses [HttpsRepositoryFetcher]; tests may inject deterministic bytes. */
interface RepositoryFetcher {
    fun fetch(url: String, maxBytes: Int): ByteArray
}

/** HTTPS-only, cookie-free repository transport with validated redirects and finite timeouts. */
class HttpsRepositoryFetcher(
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val totalTimeoutMs: Int = DEFAULT_TOTAL_TIMEOUT_MS,
) : RepositoryFetcher {
    init {
        require(connectTimeoutMs in 1_000..60_000) { "Invalid repository connect timeout" }
        require(readTimeoutMs in 1_000..120_000) { "Invalid repository read timeout" }
        require(totalTimeoutMs in 1_000..180_000) { "Invalid repository total timeout" }
    }

    override fun fetch(url: String, maxBytes: Int): ByteArray {
        require(maxBytes in 1..MAX_PACKAGE_BYTES) { "Invalid repository response bound" }
        val deadlineNanos = System.nanoTime() + totalTimeoutMs * NANOS_PER_MILLISECOND
        val initialUrl = requireHttpsRepositoryUrl(url).toASCIIString()
        var retryingWithFreshConnection = false
        while (true) {
            try {
                return fetchAttempt(initialUrl, maxBytes, deadlineNanos, retryingWithFreshConnection)
            } catch (error: RepositoryFetchException) {
                if (
                    retryingWithFreshConnection ||
                    error.error != RepositoryFetchError.NETWORK ||
                    !isRetryableTruncationOrReset(error.cause)
                ) {
                    throw error
                }
                remainingDeadlineNanos(deadlineNanos)
                retryingWithFreshConnection = true
            }
        }
    }

    private fun fetchAttempt(
        initialUrl: String,
        maxBytes: Int,
        deadlineNanos: Long,
        closeConnection: Boolean,
    ): ByteArray {
        var current = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = try {
                (URL(current).openConnection() as? HttpURLConnection)
                    ?: throw RepositoryFetchException(RepositoryFetchError.NETWORK)
            } catch (error: RepositoryFetchException) {
                throw error
            } catch (error: IOException) {
                throw RepositoryFetchException(RepositoryFetchError.NETWORK, error)
            }
            val deadlineReached = AtomicBoolean(false)
            var deadlineTask: ScheduledFuture<*>? = null
            try {
                deadlineTask = scheduleDeadlineDisconnect(connection, deadlineNanos, deadlineReached)
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.connectTimeout = minOf(connectTimeoutMs, remainingTimeoutMs(deadlineNanos))
                connection.readTimeout = minOf(readTimeoutMs, remainingTimeoutMs(deadlineNanos))
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json,application/octet-stream;q=0.9")
                connection.setRequestProperty("Accept-Encoding", "identity")
                // Repository traffic is deliberately separate from extension browser sessions.
                connection.setRequestProperty("Cookie", "")
                connection.setRequestProperty("Cookie2", "")
                if (closeConnection) connection.setRequestProperty("Connection", "close")

                val status = connection.responseCode
                remainingTimeoutMs(deadlineNanos)
                if (status in REDIRECT_STATUS_CODES) {
                    if (redirectCount == MAX_REDIRECTS) throw RepositoryFetchException(RepositoryFetchError.REDIRECT_LIMIT)
                    val location = connection.getHeaderField("Location")
                        ?: throw RepositoryFetchException(RepositoryFetchError.HTTP_STATUS)
                    current = try {
                        requireHttpsRepositoryUrl(URI(current).resolve(location).toASCIIString()).toASCIIString()
                    } catch (error: RepositoryCatalogException) {
                        throw RepositoryFetchException(RepositoryFetchError.INVALID_REDIRECT, error)
                    } catch (error: IllegalArgumentException) {
                        throw RepositoryFetchException(RepositoryFetchError.INVALID_REDIRECT, error)
                    }
                    return@repeat
                }
                if (status !in 200..299) throw RepositoryFetchException(RepositoryFetchError.HTTP_STATUS)
                if (connection.contentLengthLong > maxBytes) throw RepositoryFetchException(RepositoryFetchError.RESPONSE_TOO_LARGE)
                connection.readTimeout = minOf(readTimeoutMs, remainingTimeoutMs(deadlineNanos))
                val input = connection.inputStream
                return readBounded(connection, input, maxBytes, deadlineNanos)
            } catch (error: RepositoryFetchException) {
                throw error
            } catch (error: SocketTimeoutException) {
                val kind = if (Thread.currentThread().isInterrupted) RepositoryFetchError.CANCELLED else RepositoryFetchError.TIMEOUT
                throw RepositoryFetchException(kind, error)
            } catch (error: IOException) {
                val kind = when {
                    Thread.currentThread().isInterrupted -> RepositoryFetchError.CANCELLED
                    deadlineReached.get() || System.nanoTime() >= deadlineNanos -> RepositoryFetchError.TIMEOUT
                    else -> RepositoryFetchError.NETWORK
                }
                throw RepositoryFetchException(kind, error)
            } finally {
                deadlineTask?.cancel(false)
                connection.disconnect()
            }
        }
        throw RepositoryFetchException(RepositoryFetchError.REDIRECT_LIMIT)
    }

    private fun readBounded(
        connection: HttpURLConnection,
        input: java.io.InputStream,
        maxBytes: Int,
        deadlineNanos: Long,
    ): ByteArray = input.use { stream ->
        val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        val buffer = ByteArray(16 * 1024)
        val expectedLength = connection.contentLengthLong
        while (true) {
            // HttpURLConnection applies the current read timeout to its active socket on Android.
            connection.readTimeout = minOf(readTimeoutMs, remainingTimeoutMs(deadlineNanos))
            val count = stream.read(buffer)
            if (count < 0) {
                if (expectedLength >= 0L && output.size().toLong() != expectedLength) {
                    throw EOFException("Repository response ended before its declared length")
                }
                remainingTimeoutMs(deadlineNanos)
                break
            }
            if (output.size() > maxBytes - count) throw RepositoryFetchException(RepositoryFetchError.RESPONSE_TOO_LARGE)
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    private fun isRetryableTruncationOrReset(error: Throwable?): Boolean {
        var current = error
        var truncationOrReset = false
        repeat(MAX_RETRYABLE_CAUSE_DEPTH) {
            val cause = current ?: return truncationOrReset
            when (cause) {
                is InterruptedIOException,
                is ProtocolException,
                is javax.net.ssl.SSLException,
                -> return false
                is EOFException -> truncationOrReset = true
                is SocketException -> {
                    if (
                        cause.message?.contains("connection reset", ignoreCase = true) == true ||
                        isUnexpectedTruncationMessage(cause.message)
                    ) {
                        truncationOrReset = true
                    }
                }
                is IOException -> {
                    if (isUnexpectedTruncationMessage(cause.message)) truncationOrReset = true
                }
            }
            current = cause.cause
        }
        return false
    }

    private fun isUnexpectedTruncationMessage(message: String?): Boolean =
        message?.let {
            it.contains("unexpected end of stream", ignoreCase = true) ||
                it.contains("unexpected end of file", ignoreCase = true)
        } == true

    private fun scheduleDeadlineDisconnect(
        connection: HttpURLConnection,
        deadlineNanos: Long,
        deadlineReached: AtomicBoolean,
    ): ScheduledFuture<*> = DEADLINE_EXECUTOR.schedule(
        {
            deadlineReached.set(true)
            connection.disconnect()
        },
        remainingDeadlineNanos(deadlineNanos),
        TimeUnit.NANOSECONDS,
    )

    private fun remainingTimeoutMs(deadlineNanos: Long): Int =
        ((remainingDeadlineNanos(deadlineNanos) + NANOS_PER_MILLISECOND - 1) / NANOS_PER_MILLISECOND)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    private fun remainingDeadlineNanos(deadlineNanos: Long): Long {
        if (Thread.currentThread().isInterrupted) throw RepositoryFetchException(RepositoryFetchError.CANCELLED)
        return (deadlineNanos - System.nanoTime()).takeIf { it > 0 }
            ?: throw RepositoryFetchException(RepositoryFetchError.TIMEOUT)
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
        const val DEFAULT_READ_TIMEOUT_MS = 20_000
        const val DEFAULT_TOTAL_TIMEOUT_MS = 30_000
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAX_RETRYABLE_CAUSE_DEPTH = 8
        val DEADLINE_EXECUTOR = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "TsuyomiRepositoryDeadline").apply { isDaemon = true }
        }
    }
}

enum class RepositoryFetchError {
    NETWORK,
    TIMEOUT,
    CANCELLED,
    HTTP_STATUS,
    INVALID_REDIRECT,
    REDIRECT_LIMIT,
    RESPONSE_TOO_LARGE,
}

class RepositoryFetchException(
    val error: RepositoryFetchError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

enum class RepositoryCatalogError {
    STORAGE_UNAVAILABLE,
    INVALID_CATALOG,
    INVALID_SIGNATURE,
    ROLLBACK_REJECTED,
    EQUIVOCATION_REJECTED,
    CATALOG_EXPIRED,
    CATALOG_UNAVAILABLE,
    PACKAGE_NOT_FOUND,
    PUBLISHER_REVOKED,
    PACKAGE_REVOKED,
    PACKAGE_DOWNLOAD_INVALID,
    PACKAGE_BINDING_MISMATCH,
    PREPARED_INSTALL_INVALID,
}

/** Stable categories for hosts to map without exposing transport or parser details. */
class RepositoryCatalogException(
    val error: RepositoryCatalogError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

/**
 * Read-only root-signed catalog client. A synced authenticated recovery envelope precedes the
 * repository-bound append-only floor, which in turn precedes atomically replacing current state.
 */
class OfficialRepositoryClient(
    private val root: RepositoryRoot,
    storageDirectory: File,
    private val fetcher: RepositoryFetcher = HttpsRepositoryFetcher(),
    private val clock: () -> Instant = { Instant.now() },
) {
    private val lock = Any()
    private val directory: File
    private val stateFile: File
    private val transactionFile: File
    private val highWaterFile: File
    private val recoveryFile: File
    private var cachedCatalog: RepositoryCatalog? = null
    private var cacheFailure: RepositoryCatalogException? = null

    /** Resolver for root-authenticated publishers and revocations, suitable for an app composite resolver. */
    val publisherKeys: PublisherKeyResolver = RepositoryPublisherKeyResolver(root.signingKey.trust)

    init {
        directory = storageDirectory.canonicalFile
        val createdDirectory = !directory.isDirectory
        require(!createdDirectory || directory.mkdirs() || directory.isDirectory) { "Repository catalog storage is unavailable" }
        if (createdDirectory) directory.parentFile?.let(::syncDirectoryMetadata)
        stateFile = File(directory, STATE_FILE_NAME).canonicalFile
        recoveryFile = File(directory, RECOVERY_FILE_NAME).canonicalFile
        highWaterFile = File(directory, HIGH_WATER_FILE_NAME).canonicalFile
        transactionFile = File(directory, TRANSACTION_FILE_NAME).canonicalFile
        require(
            stateFile.parentFile == directory && highWaterFile.parentFile == directory &&
                recoveryFile.parentFile == directory && transactionFile.parentFile == directory,
        ) { "Repository catalog state path is invalid" }
    }

    /** Fetches, verifies, anti-rolls-back, and atomically persists a current catalog. */
    fun refresh(): RepositoryCatalog = synchronized(lock) {
        loadCachedLocked()
        val now = clock()
        val bytes = try {
            fetcher.fetch(root.indexUrl, MAX_CATALOG_BYTES)
        } catch (error: RepositoryFetchException) {
            throw RepositoryCatalogException(RepositoryCatalogError.CATALOG_UNAVAILABLE, error)
        } catch (error: Throwable) {
            throw RepositoryCatalogException(RepositoryCatalogError.CATALOG_UNAVAILABLE, error)
        }
        val incoming = RepositoryCatalogParser.parse(bytes, root, now)
        if (incoming.isExpired(now)) throw RepositoryCatalogException(RepositoryCatalogError.CATALOG_EXPIRED)

        return withDurableTransaction {
            val durable = readDurableStateLocked()
            applyDurableStateLocked(durable)
            durable.highWater?.let { floor ->
                when {
                    incoming.sequence < floor.sequence -> throw RepositoryCatalogException(RepositoryCatalogError.ROLLBACK_REJECTED)
                    incoming.sequence == floor.sequence && incoming.signedDigest != floor.signedDigest -> {
                        throw RepositoryCatalogException(RepositoryCatalogError.EQUIVOCATION_REJECTED)
                    }
                }
            }
            // Stabilize a recovered floor before replacing its sole durable recovery envelope.
            durable.catalog?.let(::persistLocked)
            persistRecoveryLocked(incoming)
            val nextHighWater = appendHighWaterLocked(incoming, durable.highWater, durable.highWaterEntryCount)
            val nextEntryCount = when {
                nextHighWater == durable.highWater -> durable.highWaterEntryCount
                durable.highWaterEntryCount >= MAX_HIGH_WATER_ENTRIES -> 1
                else -> durable.highWaterEntryCount + 1
            }
            val committed = DurableCatalogState(
                catalog = incoming,
                highWater = nextHighWater,
                highWaterEntryCount = nextEntryCount,
            )
            try {
                persistLocked(incoming)
            } catch (error: RepositoryCatalogException) {
                // The synced recovery snapshot and floor are already stricter than the old state.
                applyDurableStateLocked(committed)
                throw error
            }
            applyDurableStateLocked(committed)
            incoming
        }
    }

    /** Returns authenticated cached metadata, including expired data needed for identity/revocation enforcement. */
    fun cached(): RepositoryCatalog? = synchronized(lock) { loadCachedLocked() }

    /**
     * Downloads one exact catalog package, checks its byte binding, and delegates HXP validation to
     * the caller's installer. The prepared result remains inactive until explicit approval.
     */
    fun prepare(sourceId: String, installer: ExtensionInstaller): PreparedExtensionInstall = synchronized(lock) {
        val catalog = requireCurrentCatalogLocked()
        val source = try {
            SourceId(sourceId)
        } catch (error: IllegalArgumentException) {
            throw RepositoryCatalogException(RepositoryCatalogError.PACKAGE_NOT_FOUND, error)
        }
        val entry = catalog.packages.singleOrNull { it.id == source.value }
            ?: throw RepositoryCatalogException(RepositoryCatalogError.PACKAGE_NOT_FOUND)
        val publisher = catalog.publisher(entry.publisherKeyId)
            ?: throw RepositoryCatalogException(RepositoryCatalogError.PACKAGE_BINDING_MISMATCH)
        requireNotRevoked(catalog, entry, publisher)

        val archive = try {
            fetcher.fetch(entry.downloadUrl, entry.size.toInt())
        } catch (error: RepositoryFetchException) {
            throw RepositoryCatalogException(RepositoryCatalogError.PACKAGE_DOWNLOAD_INVALID, error)
        } catch (error: Throwable) {
            throw RepositoryCatalogException(RepositoryCatalogError.PACKAGE_DOWNLOAD_INVALID, error)
        }
        if (archive.size.toLong() != entry.size || sha256(archive) != entry.sha256) {
            throw RepositoryCatalogException(RepositoryCatalogError.PACKAGE_DOWNLOAD_INVALID)
        }
        val temporary = try {
            File.createTempFile("repository-", ".hxp", directory)
        } catch (error: IOException) {
            throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE, error)
        }
        try {
            temporary.writeBytes(archive)
        } catch (error: IOException) {
            temporary.delete()
            throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE, error)
        }
        try {
            return installer.prepareRepository(temporary, entry.binding(publisher.fingerprint))
        } catch (error: RepositoryCatalogException) {
            throw error
        } finally {
            temporary.delete()
        }
    }

    /** Rechecks expiry, revocation, and the current catalog package binding immediately before activation. */
    fun validatePreparedRepositoryInstall(prepared: PreparedExtensionInstall) = synchronized(lock) {
        val binding = prepared.repositoryBinding
            ?: throw RepositoryCatalogException(RepositoryCatalogError.PREPARED_INSTALL_INVALID)
        val catalog = requireCurrentCatalogLocked()
        val entry = catalog.packages.singleOrNull { it.id == binding.sourceId.value && it.sha256 == binding.packageSha256 }
            ?: throw RepositoryCatalogException(RepositoryCatalogError.PREPARED_INSTALL_INVALID)
        val publisher = catalog.publisher(entry.publisherKeyId)
            ?: throw RepositoryCatalogException(RepositoryCatalogError.PREPARED_INSTALL_INVALID)
        requireNotRevoked(catalog, entry, publisher)
        if (!binding.matches(prepared.candidate) || entry.binding(publisher.fingerprint) != binding) {
            throw RepositoryCatalogException(RepositoryCatalogError.PREPARED_INSTALL_INVALID)
        }
    }

    private fun requireCurrentCatalogLocked(): RepositoryCatalog {
        val catalog = loadCachedLocked() ?: throw RepositoryCatalogException(RepositoryCatalogError.CATALOG_UNAVAILABLE)
        if (catalog.isExpired(clock())) throw RepositoryCatalogException(RepositoryCatalogError.CATALOG_EXPIRED)
        return catalog
    }

    private fun requireNotRevoked(catalog: RepositoryCatalog, entry: RepositoryPackage, publisher: PublisherKey) {
        if (publisher.fingerprint in catalog.revokedPublisherFingerprints) {
            throw RepositoryCatalogException(RepositoryCatalogError.PUBLISHER_REVOKED)
        }
        if (entry.sha256 in catalog.revokedPackageDigests) {
            throw RepositoryCatalogException(RepositoryCatalogError.PACKAGE_REVOKED)
        }
    }

    /** Every public operation reads the locked durable floor so sibling clients cannot stay stale. */
    private fun loadCachedLocked(): RepositoryCatalog? {
        cacheFailure?.takeUnless { it.error == RepositoryCatalogError.STORAGE_UNAVAILABLE }?.let { throw it }
        try {
            val catalog = withDurableTransaction {
                applyDurableStateLocked(readDurableStateLocked())
                cachedCatalog
            }
            cacheFailure = null
            return catalog
        } catch (error: RepositoryCatalogException) {
            restoreRestrictiveTrustLocked()
            cacheFailure = error
            throw error
        } catch (error: Throwable) {
            val failure = RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE, error)
            restoreRestrictiveTrustLocked()
            cacheFailure = failure
            throw failure
        }
    }

    /** A corrupt floor blocks catalog use, but valid signed remnants must still revoke installed code. */
    private fun restoreRestrictiveTrustLocked() {
        val restrictions = try {
            withDurableTransaction {
                listOfNotNull(
                    runCatching { readCatalogFileLocked(stateFile) }.getOrNull(),
                    runCatching { readCatalogFileLocked(recoveryFile) }.getOrNull(),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
        val resolver = publisherKeys as RepositoryPublisherKeyResolver
        if (restrictions.isEmpty()) resolver.denyAll() else resolver.restrictTo(restrictions)
    }

    private fun readDurableStateLocked(): DurableCatalogState {
        val journal = readHighWaterJournalLocked()
        var stateFailure: RepositoryCatalogException? = null
        val stateCatalog = try {
            readCatalogFileLocked(stateFile)
        } catch (error: RepositoryCatalogException) {
            stateFailure = error
            null
        } catch (error: IOException) {
            stateFailure = RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE, error)
            null
        }
        val stateRecord = stateCatalog?.let { HighWaterRecord(it.repositoryId, it.sequence, it.signedDigest) }
        val journalRecord = journal.record
        if (journalRecord == null) {
            stateFailure?.let { throw it }
            return DurableCatalogState(stateCatalog, stateRecord, journal.entryCount)
        }
        if (stateFailure == null && stateRecord == journalRecord) {
            return DurableCatalogState(stateCatalog, journalRecord, journal.entryCount)
        }
        if (stateRecord != null && stateRecord.sequence > journalRecord.sequence) {
            throw RepositoryCatalogException(RepositoryCatalogError.INVALID_CATALOG)
        }
        val recoveryCatalog = readCatalogFileLocked(recoveryFile)
            ?: throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE)
        val recoveryRecord = HighWaterRecord(recoveryCatalog.repositoryId, recoveryCatalog.sequence, recoveryCatalog.signedDigest)
        if (recoveryRecord != journalRecord) throw RepositoryCatalogException(RepositoryCatalogError.INVALID_CATALOG)
        // A state rename can lag its floor after a crash; retain this root-authenticated revocation snapshot.
        return DurableCatalogState(recoveryCatalog, journalRecord, journal.entryCount)
    }

    private fun readCatalogFileLocked(file: File): RepositoryCatalog? {
        if (!file.exists()) return null
        if (!file.isFile) throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE)
        val bytes = readBoundedFile(file, MAX_STATE_BYTES)
        if (bytes.isEmpty()) throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE)
        val state = RepositoryCatalogParser.parseState(bytes)
        val catalog = RepositoryCatalogParser.parse(state.envelope, root, clock())
        if (catalog.repositoryId != state.repositoryId || catalog.sequence != state.sequence || catalog.signedDigest != state.signedDigest) {
            throw RepositoryCatalogException(RepositoryCatalogError.INVALID_CATALOG)
        }
        return catalog
    }

    private fun readHighWaterJournalLocked(): HighWaterJournal {
        if (!highWaterFile.exists()) return HighWaterJournal(record = null, entryCount = 0)
        if (!highWaterFile.isFile) throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE)
        val bytes = readBoundedFile(highWaterFile, MAX_HIGH_WATER_BYTES)
        if (bytes.isEmpty()) throw RepositoryCatalogException(RepositoryCatalogError.INVALID_CATALOG)
        val text = decodeUtf8(bytes)
        if (!text.endsWith('\n')) throw RepositoryCatalogException(RepositoryCatalogError.INVALID_CATALOG)
        val lines = text.dropLast(1).split('\n')
        if (lines.size !in 1..MAX_HIGH_WATER_ENTRIES) throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE)
        var prior: HighWaterRecord? = null
        lines.forEach { line ->
            val repositorySeparator = line.indexOf(':')
            val sequenceSeparator = line.indexOf(':', repositorySeparator + 1)
            if (
                repositorySeparator !in 1 until line.lastIndex ||
                sequenceSeparator !in (repositorySeparator + 2) until line.lastIndex ||
                line.substring(0, repositorySeparator) != root.repositoryId
            ) {
                throw RepositoryCatalogException(RepositoryCatalogError.INVALID_CATALOG)
            }
            val sequenceText = line.substring(repositorySeparator + 1, sequenceSeparator)
            val sequence = sequenceText.toLongOrNull()
                ?.takeIf { it in 1..MAX_SAFE_JSON_INTEGER && sequenceText == it.toString() }
                ?: throw RepositoryCatalogException(RepositoryCatalogError.INVALID_CATALOG)
            val digest = line.substring(sequenceSeparator + 1)
            if (!SHA_256_DIGEST.matches(digest) || prior != null && sequence <= prior!!.sequence) {
                throw RepositoryCatalogException(RepositoryCatalogError.INVALID_CATALOG)
            }
            prior = HighWaterRecord(root.repositoryId, sequence, digest)
        }
        return HighWaterJournal(prior, lines.size)
    }

    private fun appendHighWaterLocked(
        catalog: RepositoryCatalog,
        current: HighWaterRecord?,
        entryCount: Int,
    ): HighWaterRecord {
        val next = HighWaterRecord(catalog.repositoryId, catalog.sequence, catalog.signedDigest)
        if (current != null) {
            when {
                next.sequence < current.sequence -> throw RepositoryCatalogException(RepositoryCatalogError.ROLLBACK_REJECTED)
                next.sequence == current.sequence && next != current -> throw RepositoryCatalogException(RepositoryCatalogError.EQUIVOCATION_REJECTED)
                next == current -> return current
            }
        }
        val encoded = "${next.repositoryId}:${next.sequence}:${next.signedDigest}\n".toByteArray(StandardCharsets.US_ASCII)
        if (entryCount >= MAX_HIGH_WATER_ENTRIES) {
            replaceHighWaterLocked(encoded)
            return next
        }
        try {
            FileOutputStream(highWaterFile, true).use { output ->
                output.write(encoded)
                output.fd.sync()
            }
        } catch (error: IOException) {
            throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE, error)
        }
        syncDirectoryMetadata()
        return next
    }

    /** A synced compacted floor is safe before state replacement: stale state is then withheld. */
    private fun replaceHighWaterLocked(encoded: ByteArray) {
        val temporary = File(directory, ".${HIGH_WATER_FILE_NAME}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(encoded)
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                highWaterFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            syncDirectoryMetadata()
        } catch (error: IOException) {
            throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE, error)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun applyDurableStateLocked(state: DurableCatalogState) {
        cachedCatalog = state.catalog
        val resolver = publisherKeys as RepositoryPublisherKeyResolver
        state.catalog?.let(resolver::replace) ?: resolver.clear()
    }

    private fun <T> withDurableTransaction(block: () -> T): T {
        val monitor = DIRECTORY_TRANSACTIONS.computeIfAbsent(directory.path) { Any() }
        return synchronized(monitor) {
            try {
                RandomAccessFile(transactionFile, "rw").channel.use { channel ->
                    val acquired = channel.lock()
                    try {
                        block()
                    } finally {
                        acquired.release()
                    }
                }
            } catch (error: RepositoryCatalogException) {
                throw error
            } catch (error: IOException) {
                throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE, error)
            } catch (error: Throwable) {
                throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE, error)
            }
        }
    }

    private fun persistLocked(catalog: RepositoryCatalog) = persistCatalogFileLocked(stateFile, catalog)

    private fun persistRecoveryLocked(catalog: RepositoryCatalog) = persistCatalogFileLocked(recoveryFile, catalog)

    private fun persistCatalogFileLocked(target: File, catalog: RepositoryCatalog) {
        val state = JsonObject(
            mapOf(
                "repositoryId" to JsonPrimitive(catalog.repositoryId),
                "sequence" to JsonPrimitive(catalog.sequence),
                "signedDigest" to JsonPrimitive(catalog.signedDigest),
                "envelope" to JsonPrimitive(Base64.getEncoder().encodeToString(catalog.encodedEnvelope)),
            ),
        ).toString().toByteArray(StandardCharsets.UTF_8)
        if (state.size > MAX_STATE_BYTES) throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE)
        val temporary = File(directory, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(state)
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            syncDirectoryMetadata()
        } catch (error: IOException) {
            throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE, error)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    /** Android exposes directory fsync through android.system.Os; JVM unit hosts have no equivalent. */
    private fun syncDirectoryMetadata(target: File = directory) {
        val androidRuntime = System.getProperty("java.vm.name", "").contains("Dalvik", ignoreCase = true) ||
            System.getProperty("java.runtime.name", "").contains("Android", ignoreCase = true)
        if (!androidRuntime) return
        try {
            val os = Class.forName("android.system.Os")
            val constants = Class.forName("android.system.OsConstants")
            val open = os.getMethod("open", String::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
            val descriptor = open.invoke(null, target.path, constants.getField("O_RDONLY").getInt(null), 0) as java.io.FileDescriptor
            try {
                os.getMethod("fsync", java.io.FileDescriptor::class.java).invoke(null, descriptor)
            } finally {
                os.getMethod("close", java.io.FileDescriptor::class.java).invoke(null, descriptor)
            }
        } catch (error: Throwable) {
            throw RepositoryCatalogException(RepositoryCatalogError.STORAGE_UNAVAILABLE, error)
        }
    }

    private companion object {
        const val RECOVERY_FILE_NAME = "repository-catalog-v1.recovery"
        const val STATE_FILE_NAME = "repository-catalog-v1.state"
        const val HIGH_WATER_FILE_NAME = "repository-catalog-v1.high-water"
        const val TRANSACTION_FILE_NAME = "repository-catalog-v1.lock"
        const val MAX_STATE_BYTES = 1_500_000
        const val MAX_HIGH_WATER_BYTES = 1_024 * 1024
        const val MAX_HIGH_WATER_ENTRIES = 4_096
        val DIRECTORY_TRANSACTIONS = ConcurrentHashMap<String, Any>()
    }
}

/**
 * A snapshot resolver ensures a refresh atomically changes publisher identity and revocation state.
 * Returning copies prevents a caller from mutating a cached key array.
 */
private class RepositoryPublisherKeyResolver(rootTrust: PublisherTrust) : PublisherKeyResolver {
    override val hasGlobalRevocationAuthority = rootTrust == PublisherTrust.BUILT_IN_OFFICIAL
    @Volatile
    private var snapshot = PublisherSnapshot(emptyMap(), emptySet(), emptySet(), denyAll = false)

    override fun resolve(keyId: String): PublisherKey? = snapshot.keys[keyId]?.let(::copyPublisherKey)

    override fun isRevokedFingerprint(fingerprint: String): Boolean = snapshot.denyAll || fingerprint in snapshot.revokedFingerprints

    override fun isRevokedPackage(packageSha256: String): Boolean = snapshot.denyAll || packageSha256 in snapshot.revokedPackages

    fun replace(catalog: RepositoryCatalog) {
        val current = snapshot
        snapshot = PublisherSnapshot(
            // Keep prior authenticated identities in-process so a newer catalog can revoke a
            // removed publisher/package instead of losing the subject before revocation checks.
            keys = current.keys + catalog.publishers.associateBy(PublisherKey::keyId).mapValues { (_, key) -> copyPublisherKey(key) },
            revokedFingerprints = current.revokedFingerprints + catalog.revokedPublisherFingerprints,
            revokedPackages = current.revokedPackages + catalog.revokedPackageDigests,
            denyAll = false,
        )
    }

    /** Applies authenticated revocations without restoring keys that could authorize a new install. */
    fun restrictTo(catalogs: Collection<RepositoryCatalog>) {
        snapshot = PublisherSnapshot(
            keys = emptyMap(),
            revokedFingerprints = catalogs.flatMap(RepositoryCatalog::revokedPublisherFingerprints).toSet(),
            revokedPackages = catalogs.flatMap(RepositoryCatalog::revokedPackageDigests).toSet(),
            denyAll = false,
        )
    }

    fun denyAll() {
        snapshot = PublisherSnapshot(emptyMap(), emptySet(), emptySet(), denyAll = true)
    }

    fun clear() {
        snapshot = PublisherSnapshot(emptyMap(), emptySet(), emptySet(), denyAll = false)
    }

    private data class PublisherSnapshot(
        val keys: Map<String, PublisherKey>,
        val revokedFingerprints: Set<String>,
        val revokedPackages: Set<String>,
        val denyAll: Boolean,
    )
}

/** This type has no public constructor: only a verified root catalog can authorize repository mode. */
class RepositoryInstallBinding internal constructor(
    internal val sourceId: SourceId,
    internal val version: SemanticVersion,
    internal val packageSha256: String,
    internal val publisherKeyId: String,
    internal val publisherFingerprint: String,
    internal val hostApi: RepositoryHostApi,
    internal val legacyMigration: RepositoryLegacyMigration?,
) {
    internal fun matches(candidate: VerifiedHxpPackage): Boolean =
        candidate.manifest.sourceId == sourceId &&
            candidate.manifest.version == version &&
            candidate.packageSha256 == packageSha256 &&
            candidate.manifest.publisherKeyId == publisherKeyId &&
            candidate.publisherFingerprint == publisherFingerprint &&
            candidate.manifest.hostApiMinInclusive == hostApi.minInclusive &&
            candidate.manifest.hostApiMaxExclusive == hostApi.maxExclusive

    override fun equals(other: Any?): Boolean = other is RepositoryInstallBinding &&
        sourceId == other.sourceId &&
        version == other.version &&
        packageSha256 == other.packageSha256 &&
        publisherKeyId == other.publisherKeyId &&
        publisherFingerprint == other.publisherFingerprint &&
        hostApi == other.hostApi &&
        legacyMigration == other.legacyMigration

    override fun hashCode(): Int = listOf(
        sourceId,
        version,
        packageSha256,
        publisherKeyId,
        publisherFingerprint,
        hostApi,
        legacyMigration,
    ).hashCode()
}

private data class CachedCatalogState(
    val repositoryId: String,
    val sequence: Long,
    val signedDigest: String,
    val envelope: ByteArray,
)

private data class HighWaterRecord(val repositoryId: String, val sequence: Long, val signedDigest: String)

private data class DurableCatalogState(
    val catalog: RepositoryCatalog?,
    val highWater: HighWaterRecord?,
    val highWaterEntryCount: Int,
)

private data class HighWaterJournal(val record: HighWaterRecord?, val entryCount: Int)

private object RepositoryCatalogParser {
    private val JSON = Json { isLenient = false; ignoreUnknownKeys = false }
    private val KEY_ID = Regex("^[A-Za-z0-9._-]{8,128}$")
    private val SHA_256 = Regex("^[a-f0-9]{64}$")
    private val REVISION = Regex("^[a-f0-9]{40}$")

    fun parse(bytes: ByteArray, root: RepositoryRoot, now: Instant): RepositoryCatalog {
        if (bytes.size !in 1..MAX_CATALOG_BYTES) invalid()
        val text = decodeUtf8(bytes)
        try {
            StrictJson.validate(text)
        } catch (_: Throwable) {
            invalid()
        }
        val envelope = try {
            JSON.parseToJsonElement(text).asObject()
        } catch (_: Throwable) {
            invalid()
        }
        envelope.requireKeys(setOf("format", "version", "keyId", "signed", "signature"))
        if (envelope.string("format") != "tsuyomi-repository" || envelope.int("version") != 1) invalid()
        val rootKeyId = envelope.string("keyId").also { if (!KEY_ID.matches(it) || it != root.signingKey.keyId) invalid() }
        val signed = envelope.obj("signed")
        val canonicalSigned = try {
            JsonCanonicalizer(signed.toString()).encodedUTF8
        } catch (_: Throwable) {
            invalid()
        }
        val signature = decodeBase64(envelope.string("signature"), expectedBytes = 64)
        if (!verifyEd25519(root.signingKey.publicKey, repositorySignatureMessage(canonicalSigned), signature)) {
            throw RepositoryCatalogException(RepositoryCatalogError.INVALID_SIGNATURE)
        }
        val metadata = parseSigned(signed, root, now)
        return RepositoryCatalog(
            repositoryId = metadata.repositoryId,
            sequence = metadata.sequence,
            issuedAt = metadata.issuedAt,
            expiresAt = metadata.expiresAt,
            publishers = metadata.publishers,
            packages = metadata.packages,
            revokedPublisherFingerprints = metadata.revokedPublisherFingerprints,
            revokedPackageDigests = metadata.revokedPackageDigests,
            signedDigest = sha256(canonicalSigned),
            encodedEnvelope = bytes.copyOf(),
        )
    }

    fun parseState(bytes: ByteArray): CachedCatalogState {
        val text = decodeUtf8(bytes)
        try {
            StrictJson.validate(text)
        } catch (_: Throwable) {
            invalid()
        }
        val state = try {
            JSON.parseToJsonElement(text).asObject()
        } catch (_: Throwable) {
            invalid()
        }
        state.requireKeys(setOf("repositoryId", "sequence", "signedDigest", "envelope"))
        val repositoryId = try {
            SourceId(state.string("repositoryId")).value
        } catch (_: IllegalArgumentException) {
            invalid()
        }
        val sequence = state.long("sequence").also { if (it !in 1..MAX_SAFE_JSON_INTEGER) invalid() }
        val signedDigest = state.string("signedDigest").also { if (!SHA_256.matches(it)) invalid() }
        val envelope = decodeBase64(state.string("envelope"), expectedBytes = null)
        if (envelope.size !in 1..MAX_CATALOG_BYTES) invalid()
        return CachedCatalogState(repositoryId, sequence, signedDigest, envelope)
    }

    private fun parseSigned(value: JsonObject, root: RepositoryRoot, now: Instant): ParsedCatalog {
        value.requireKeys(setOf("repositoryId", "sequence", "issuedAt", "expiresAt", "publishers", "packages", "revocations"))
        val repositoryId = try {
            SourceId(value.string("repositoryId")).value
        } catch (_: IllegalArgumentException) {
            invalid()
        }
        if (repositoryId != root.repositoryId) invalid()
        val sequence = value.long("sequence").also { if (it !in 1..MAX_SAFE_JSON_INTEGER) invalid() }
        val issuedAt = parseUtcInstant(value.string("issuedAt"))
        val expiresAt = parseUtcInstant(value.string("expiresAt"))
        if (!expiresAt.isAfter(issuedAt) || Duration.between(issuedAt, expiresAt) > MAX_CATALOG_LIFETIME || issuedAt > now.plus(MAX_ISSUED_FUTURE)) {
            invalid()
        }

        val publishers = value.array("publishers")
        if (publishers.size > MAX_PUBLISHERS) invalid()
        val publisherIds = mutableSetOf<String>()
        val publisherFingerprints = mutableSetOf<String>()
        val parsedPublishers = publishers.map { raw ->
            val publisher = raw.asObject()
            publisher.requireKeys(setOf("keyId", "publicKey", "fingerprint"))
            val keyId = publisher.string("keyId").also { if (!KEY_ID.matches(it) || !publisherIds.add(it)) invalid() }
            val publicKey = decodeBase64(publisher.string("publicKey"), expectedBytes = 32)
            val fingerprint = publisher.string("fingerprint").also {
                if (!SHA_256.matches(it) || it != sha256(publicKey) || !publisherFingerprints.add(it)) invalid()
            }
            PublisherKey(keyId, publicKey, root.publisherTrust)
        }

        val packages = value.array("packages")
        if (packages.size > MAX_PACKAGES) invalid()
        val packageIds = mutableSetOf<String>()
        val parsedPackages = packages.map { raw ->
            val entry = raw.asObject()
            entry.requireKeys(
                required = setOf(
                    "id", "name", "version", "summary", "language", "license", "sourceUrl", "sourceRevision",
                    "downloadUrl", "size", "sha256", "hostApi", "publisherKeyId",
                ),
                optional = setOf("legacyMigration"),
            )
            val id = try {
                SourceId(entry.string("id")).value
            } catch (_: IllegalArgumentException) {
                invalid()
            }
            if (!packageIds.add(id)) invalid()
            val name = entry.string("name").bounded(1, 128)
            val version = entry.string("version").also { parseSemanticVersion(it) }
            val summary = entry.string("summary").bounded(1, 1_024)
            val language = entry.string("language").bounded(1, 64)
            val license = entry.string("license").bounded(1, 128)
            val sourceUrl = parseHttpsUrl(entry.string("sourceUrl"))
            val sourceRevision = entry.string("sourceRevision").also { if (!REVISION.matches(it)) invalid() }
            val downloadUrl = parseHttpsUrl(entry.string("downloadUrl"))
            val size = entry.long("size").also { if (it !in 1..MAX_PACKAGE_BYTES.toLong()) invalid() }
            val digest = entry.string("sha256").also { if (!SHA_256.matches(it)) invalid() }
            val host = entry.obj("hostApi").also { it.requireKeys(setOf("minInclusive", "maxExclusive")) }
            val hostApi = RepositoryHostApi(
                minInclusive = parseSemanticVersion(host.string("minInclusive")),
                maxExclusive = parseSemanticVersion(host.string("maxExclusive")),
            )
            val publisherKeyId = entry.string("publisherKeyId").also { if (!KEY_ID.matches(it) || it !in publisherIds) invalid() }
            val migration = entry["legacyMigration"]?.let {
                if (root.signingKey.trust != PublisherTrust.BUILT_IN_OFFICIAL) invalid()
                parseLegacyMigration(it.asObject())
            }
            RepositoryPackage(
                id = id,
                name = name,
                version = version,
                summary = summary,
                language = language,
                license = license,
                sourceUrl = sourceUrl,
                sourceRevision = sourceRevision,
                downloadUrl = downloadUrl,
                size = size,
                sha256 = digest,
                hostApi = hostApi,
                publisherKeyId = publisherKeyId,
                legacyMigration = migration,
            )
        }

        val revocations = value.obj("revocations").also { it.requireKeys(setOf("publisherFingerprints", "packageDigests")) }
        val revokedPublishers = parseDigestSet(revocations.array("publisherFingerprints"), MAX_PUBLISHERS)
        val revokedPackages = parseDigestSet(revocations.array("packageDigests"), MAX_PACKAGES)
        return ParsedCatalog(
            repositoryId,
            sequence,
            issuedAt,
            expiresAt,
            parsedPublishers,
            parsedPackages,
            revokedPublishers,
            revokedPackages,
        )
    }

    private fun parseLegacyMigration(value: JsonObject): RepositoryLegacyMigration {
        value.requireKeys(setOf("fromPublisherFingerprint", "fromPackageSha256"))
        val fingerprint = value.string("fromPublisherFingerprint").also { if (!SHA_256.matches(it)) invalid() }
        val digest = value.string("fromPackageSha256").also { if (!SHA_256.matches(it)) invalid() }
        return RepositoryLegacyMigration(fingerprint, digest)
    }

    private fun parseDigestSet(value: JsonArray, maximum: Int): Set<String> {
        if (value.size > maximum) invalid()
        val result = value.map { it.asPrimitive().stringValue().also { digest -> if (!SHA_256.matches(digest)) invalid() } }.toSet()
        if (result.size != value.size) invalid()
        return result
    }

    private fun parseUtcInstant(value: String): Instant = try {
        if (!value.endsWith("Z")) invalid()
        Instant.parse(value)
    } catch (_: Throwable) {
        invalid()
    }

    private fun parseSemanticVersion(value: String): SemanticVersion = try {
        SemanticVersion.parse(value)
    } catch (_: IllegalArgumentException) {
        invalid()
    }
    private fun parseHttpsUrl(value: String): String = try {
        requireHttpsRepositoryUrl(value).toASCIIString()
    } catch (_: IllegalArgumentException) {
        invalid()
    }


    private fun decodeBase64(value: String, expectedBytes: Int?): ByteArray {
        if (value.length !in 4..2_000_000 || value.any { it !in BASE64_CHARS }) invalid()
        val decoded = try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            invalid()
        }
        if (Base64.getEncoder().encodeToString(decoded) != value || expectedBytes != null && decoded.size != expectedBytes) invalid()
        return decoded
    }

    private fun JsonObject.requireKeys(required: Set<String>, optional: Set<String> = emptySet()) {
        if (!keys.containsAll(required) || keys.any { it !in required && it !in optional }) invalid()
    }

    private fun JsonObject.obj(name: String): JsonObject = get(name)?.asObject() ?: invalid()
    private fun JsonObject.array(name: String): JsonArray = get(name) as? JsonArray ?: invalid()
    private fun JsonObject.string(name: String): String = get(name)?.asPrimitive()?.stringValue() ?: invalid()
    private fun JsonObject.int(name: String): Int? = get(name)?.asPrimitive()?.intValue()
    private fun JsonObject.long(name: String): Long = get(name)?.asPrimitive()?.longValue() ?: invalid()
    private fun JsonElement.asObject(): JsonObject = this as? JsonObject ?: invalid()
    private fun JsonElement.asPrimitive(): JsonPrimitive = this as? JsonPrimitive ?: invalid()
    private fun JsonPrimitive.stringValue(): String = takeIf { isString }?.content ?: invalid()
    private fun JsonPrimitive.intValue(): Int? = if (!isString) content.toIntOrNull() else null
    private fun JsonPrimitive.longValue(): Long? = if (!isString) content.toLongOrNull() else null
    private fun String.bounded(minimum: Int, maximum: Int): String = also {
        if (codePointCount(0, length) !in minimum..maximum) invalid()
    }

    private data class ParsedCatalog(
        val repositoryId: String,
        val sequence: Long,
        val issuedAt: Instant,
        val expiresAt: Instant,
        val publishers: List<PublisherKey>,
        val packages: List<RepositoryPackage>,
        val revokedPublisherFingerprints: Set<String>,
        val revokedPackageDigests: Set<String>,
    )
}

private fun decodeUtf8(bytes: ByteArray): String = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: Throwable) {
    invalid()
}

private fun repositorySignatureMessage(canonicalSigned: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
    output.write(REPOSITORY_SIGNATURE_PREFIX)
    output.write(canonicalSigned)
    output.toByteArray()
}

private fun verifyEd25519(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = runCatching {
    Ed25519Signer().apply {
        init(false, Ed25519PublicKeyParameters(publicKey, 0))
        update(message, 0, message.size)
    }.verifySignature(signature)
}.getOrDefault(false)

internal fun requireHttpsRepositoryUrl(value: String): URI {
    val uri = try {
        URI(value)
    } catch (error: Throwable) {
        throw IllegalArgumentException("Repository URL is invalid", error)
    }
    require(value.length in 12..4_096 && uri.scheme.equals("https", ignoreCase = true)) { "Repository URL must use HTTPS" }
    require(uri.isAbsolute && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawFragment == null) { "Repository URL is invalid" }
    require(uri.port in -1..65_535) { "Repository URL is invalid" }
    return uri
}

internal fun copyPublisherKey(key: PublisherKey): PublisherKey = PublisherKey(
    keyId = key.keyId,
    publicKey = key.publicKey.copyOf(),
    trust = key.trust,
)

private fun invalid(): Nothing = throw RepositoryCatalogException(RepositoryCatalogError.INVALID_CATALOG)
private fun readBoundedFile(file: File, maximumBytes: Int): ByteArray = FileInputStream(file).use { input ->
    val output = ByteArrayOutputStream(minOf(maximumBytes, 16 * 1024))
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size() > maximumBytes - count) throw IOException("Repository cache exceeds its bound")
        output.write(buffer, 0, count)
    }
    output.toByteArray()
}


/** Strict JSON preflight: Kotlin serialization otherwise accepts a duplicate object's last value. */
private class StrictJson(private val text: String) {
    private var index = 0
    private var nesting = 0

    fun validate() {
        whitespace()
        value()
        whitespace()
        if (index != text.length) malformed()
    }

    private fun value() {
        whitespace()
        when (peek()) {
            '{' -> nested(::objectValue)
            '[' -> nested(::arrayValue)
            '"' -> stringValue()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            '-' -> number()
            else -> if (hasDigit()) number() else malformed()
        }
    }

    private fun nested(parse: () -> Unit) {
        if (++nesting > MAX_JSON_NESTING) malformed()
        try {
            parse()
        } finally {
            nesting--
        }
    }

    private fun objectValue() {
        take('{')
        whitespace()
        val keys = mutableSetOf<String>()
        if (peek() == '}') {
            index++
            return
        }
        while (true) {
            whitespace()
            val key = stringValue()
            if (!keys.add(key)) malformed()
            whitespace()
            take(':')
            value()
            whitespace()
            when (peek()) {
                ',' -> index++
                '}' -> {
                    index++
                    return
                }
                else -> malformed()
            }
        }
    }

    private fun arrayValue() {
        take('[')
        whitespace()
        if (peek() == ']') {
            index++
            return
        }
        while (true) {
            value()
            whitespace()
            when (peek()) {
                ',' -> index++
                ']' -> {
                    index++
                    return
                }
                else -> malformed()
            }
        }
    }

    private fun stringValue(): String {
        take('"')
        val result = StringBuilder()
        while (index < text.length) {
            when (val character = text[index++]) {
                '"' -> return result.toString()
                '\\' -> result.append(escapedCharacter())
                in '\u0000'..'\u001f' -> malformed()
                else -> result.append(character)
            }
        }
        malformed()
    }

    private fun escapedCharacter(): Char = when (val escaped = next()) {
        '"', '\\', '/' -> escaped
        'b' -> '\b'
        'f' -> '\u000c'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> unicodeEscape()
        else -> malformed()
    }

    private fun unicodeEscape(): Char {
        if (index + 4 > text.length) malformed()
        var value = 0
        repeat(4) {
            val digit = text[index++].digitToIntOrNull(16) ?: malformed()
            value = value * 16 + digit
        }
        return value.toChar()
    }

    private fun number() {
        if (peek() == '-') index++
        when (peek()) {
            '0' -> index++
            else -> if (hasNonZeroDigit()) while (hasDigit()) index++ else malformed()
        }
        if (peek() == '.') {
            index++
            if (!hasDigit()) malformed()
            while (hasDigit()) index++
        }
        if (peek() == 'e' || peek() == 'E') {
            index++
            if (peek() == '+' || peek() == '-') index++
            if (!hasDigit()) malformed()
            while (hasDigit()) index++
        }
    }

    private fun literal(value: String) {
        if (!text.startsWith(value, index)) malformed()
        index += value.length
    }

    private fun whitespace() {
        while (peek()?.let { it in JSON_WHITESPACE } == true) index++
    }

    private fun peek(): Char? = text.getOrNull(index)
    private fun hasDigit(): Boolean = peek()?.let { it in '0'..'9' } == true

    private fun hasNonZeroDigit(): Boolean = peek()?.let { it in '1'..'9' } == true

    private fun next(): Char = text.getOrNull(index++) ?: malformed()

    private fun take(expected: Char) {
        if (next() != expected) malformed()
    }

    private fun malformed(): Nothing = throw IllegalArgumentException("Malformed JSON")

    companion object {
        fun validate(text: String) = StrictJson(text).validate()
    }
}

private const val MAX_CATALOG_BYTES = 1 * 1024 * 1024
private const val MAX_PACKAGE_BYTES = 16 * 1024 * 1024
private const val MAX_PUBLISHERS = 32
private const val MAX_PACKAGES = 512
private const val MAX_REDIRECTS = 3
private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
private val MAX_CATALOG_LIFETIME: Duration = Duration.ofDays(30)
private val MAX_ISSUED_FUTURE: Duration = Duration.ofMinutes(5)
private val REPOSITORY_SIGNATURE_PREFIX = "tsuyomi-repository-v1\u0000".toByteArray(StandardCharsets.US_ASCII)
private const val MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L
private const val MAX_JSON_NESTING = 64
private val SHA_256_DIGEST = Regex("^[a-f0-9]{64}$")
private val BASE64_CHARS = ('A'..'Z').toSet() + ('a'..'z').toSet() + ('0'..'9').toSet() + setOf('+', '/', '=')
private val JSON_WHITESPACE = setOf(' ', '\t', '\n', '\r')
