/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.security.MessageDigest
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.core.network.FileHostNetworkCache
import org.tsuyomi.core.network.HostHttpResponse
import org.tsuyomi.core.network.HostHttpTransport
import org.tsuyomi.core.network.HostNetworkCache
import org.tsuyomi.core.network.HostNetworkError
import org.tsuyomi.core.network.HostNetworkException
import org.tsuyomi.core.network.HostNetworkGateway
import org.tsuyomi.core.network.InMemoryHostNetworkCache
import org.tsuyomi.core.network.SourceNetworkGrant
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.shared.sourcecontract.SourceCookieMode
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

internal object SourceGatewayFactory {
    fun create(
        context: Context,
        packageInfo: VerifiedHxpPackage,
        transport: HostHttpTransport,
        directActionTokens: DirectActionTokenRegistry,
        cache: HostNetworkCache? = null,
    ): HostNetworkGateway {
        val manifest = packageInfo.manifest
        val grant = networkGrant(packageInfo)
        val browserSessions = VerifiedBrowserSessionStore(context)
        val credentialSnapshots = manifest.capabilities.cookies.origins.mapNotNull { origin ->
            browserSessions.getSnapshot(SourceCredentialPartition(manifest.sourceId.value, origin))
                ?.let { snapshot -> origin to snapshot }
        }
        val verifiedUserAgents = credentialSnapshots.associate { (origin, snapshot) ->
            origin.canonical to snapshot.session.userAgent
        }
        val sourceUserAgent = verifiedUserAgents.values.distinct().singleOrNull()
        val sessionTransport = HostHttpTransport { request ->
            val userAgent = verifiedUserAgents[request.url.httpsOrigin()] ?: sourceUserAgent
            transport.execute(
                if (userAgent == null) request else request.copy(
                    headers = request.headers + ("User-Agent" to userAgent),
                ),
            )
        }
        val cachePartition = if (credentialSnapshots.isEmpty()) {
            "anonymous"
        } else {
            sha256(
                buildString {
                    credentialSnapshots.sortedBy { it.first.canonical }.forEach { (origin, snapshot) ->
                        append(origin.canonical)
                        append('\u0000')
                        append(snapshot.cachePartitionId)
                        append('\n')
                    }
                }.encodeToByteArray(),
            )
        }
        val gateway = HostNetworkGateway(
            transport = sessionTransport,
            cache = cache ?: FileHostNetworkCache(
                files = QuotaFileStore(
                    roots = StorageRoots.from(context),
                    root = StorageRoot.CACHE,
                    namespace = "source-network-cache",
                    quota = StorageQuota(maxBytes = 64L * 1024 * 1024, maxEntries = 512),
                ),
                partition = cachePartition,
            ),
            directActionTokens = directActionTokens,
        )
        credentialSnapshots.forEach { (origin, snapshot) ->
            gateway.importSourceCookies(
                grant,
                origin,
                snapshot.session.requestCookies,
            )
        }
        return gateway
    }

    fun networkGrant(packageInfo: VerifiedHxpPackage): SourceNetworkGrant {
        val manifest = packageInfo.manifest
        return SourceNetworkGrant(
            sourceId = manifest.sourceId.value,
            extensionVersion = manifest.version.original,
            origins = manifest.capabilities.network.origins,
            cookieMode = if (manifest.capabilities.cookies.sourceScoped) SourceCookieMode.SOURCE_SCOPED else SourceCookieMode.NONE,
            cookieOrigins = manifest.capabilities.cookies.origins,
            maxConcurrentRequests = manifest.capabilities.network.maxConcurrentRequests,
            requestTimeoutMs = manifest.capabilities.network.requestTimeoutMs,
            maxResponseBytes = manifest.capabilities.network.maxResponseBytes,
        )
    }

    /** Stable opaque revision for display caches; source session bytes never leave this factory. */
    fun mediaCredentialRevision(context: Context, packageInfo: VerifiedHxpPackage): String {
        val manifest = packageInfo.manifest
        val snapshots = manifest.capabilities.cookies.origins.mapNotNull { origin ->
            VerifiedBrowserSessionStore(context).getSnapshot(SourceCredentialPartition(manifest.sourceId.value, origin))
                ?.let { snapshot -> origin.canonical to snapshot.cachePartitionId }
        }
        if (snapshots.isEmpty()) return "anonymous"
        return sha256(
            snapshots.sortedBy { it.first }.joinToString(separator = "\n") { (origin, partition) -> "$origin\u0000$partition" }
                .encodeToByteArray(),
        )
    }

    fun createVerifiedPage(
        context: Context,
        packageInfo: VerifiedHxpPackage,
        snapshot: CapturedVerifiedPage,
        directActionTokens: DirectActionTokenRegistry,
    ): HostNetworkGateway {
        val requestUrl = runCatching { URI(snapshot.requestUrl).withoutFragment() }
            .getOrElse { throw HostNetworkException(HostNetworkError.INVALID_REQUEST) }
        val pageUrl = runCatching { URI(snapshot.pageUrl).withoutFragment() }
            .getOrElse { throw HostNetworkException(HostNetworkError.INVALID_REQUEST) }
        val transportState = AtomicInteger(0)
        val transport = HostHttpTransport { request ->
            if (request.method != org.tsuyomi.shared.sourcecontract.NetworkMethod.GET) {
                throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
            }
            val currentUrl = request.url.withoutFragment()
            if (requestUrl != pageUrl && currentUrl == requestUrl && transportState.compareAndSet(0, 1)) {
                return@HostHttpTransport HostHttpResponse(
                    status = 302,
                    finalUrl = requestUrl,
                    headers = mapOf("location" to pageUrl.toString()),
                    bytes = ByteArray(0),
                )
            }
            val expectedState = if (requestUrl == pageUrl) 0 else 1
            if (currentUrl != pageUrl || !transportState.compareAndSet(expectedState, 2)) {
                throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
            }
            val (charset, contentTypeCharset) = request.decode.snapshotCharset()
            val bytes = snapshot.html.encodeStrict(charset)
            if (bytes.size > request.maxResponseBytes) {
                throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            }
            HostHttpResponse(
                status = 200,
                finalUrl = pageUrl,
                headers = mapOf("content-type" to "text/html; charset=$contentTypeCharset"),
                bytes = bytes,
            )
        }
        return create(
            context = context,
            packageInfo = packageInfo,
            transport = transport,
            directActionTokens = directActionTokens,
            cache = InMemoryHostNetworkCache(),
        )
    }

    private fun URI.httpsOrigin(): String = buildString {
        append("https://")
        append(host.lowercase())
        if (port in 1..65535 && port != 443) append(":$port")
    }

    private fun URI.withoutFragment(): URI = if (rawFragment == null) this else URI(toASCIIString().substringBefore('#'))

    private fun DecodeMode.snapshotCharset(): Pair<Charset, String> = when (this) {
        DecodeMode.AUTO, DecodeMode.UTF8 -> StandardCharsets.UTF_8 to "utf-8"
        DecodeMode.GB18030 -> Charset.forName("GB18030") to "gb18030"
        DecodeMode.BIG5_HKSCS -> Charset.forName("Big5-HKSCS") to "big5-hkscs"
    }

    private fun String.encodeStrict(charset: Charset): ByteArray = try {
        val encoded: ByteBuffer = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(this))
        ByteArray(encoded.remaining()).also(encoded::get)
    } catch (_: CharacterCodingException) {
        throw HostNetworkException(HostNetworkError.DECODE)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
