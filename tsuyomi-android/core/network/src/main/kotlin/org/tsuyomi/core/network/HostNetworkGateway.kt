/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import java.net.URI
import java.net.HttpCookie
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkCacheMode
import org.tsuyomi.shared.sourcecontract.NetworkCacheState
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.shared.sourcecontract.SourceCookieMode
import org.tsuyomi.shared.sourcecontract.SourceNetworkRequest
import org.tsuyomi.shared.sourcecontract.SourceNetworkResponse

/** Immutable grant derived from the active signed manifest and the user's accepted review. */
data class SourceNetworkGrant(
    val sourceId: String,
    val extensionVersion: String,
    val origins: Set<HttpsOrigin>,
    val cookieMode: SourceCookieMode,
    val cookieOrigins: Set<HttpsOrigin>,
    val maxConcurrentRequests: Int,
    val requestTimeoutMs: Int,
    val maxResponseBytes: Int,
    val remoteAddPolicy: RemoteOperationRequestPolicy? = null,
) {
    init {
        require(sourceId.isNotBlank() && extensionVersion.isNotBlank())
        require(origins.isNotEmpty())
        require(maxConcurrentRequests in 1..8)
        require(requestTimeoutMs in 1_000..120_000)
        require(maxResponseBytes in 1_024..16_777_216)
        require(cookieMode != SourceCookieMode.NONE || cookieOrigins.isEmpty())
        require(cookieOrigins.all { cookieOrigin -> origins.any { it.canonical == cookieOrigin.canonical } })
        require(remoteAddPolicy == null || remoteAddPolicy.remoteBookIdParameter != null)
        require(remoteAddPolicy == null || remoteAddPolicy.cursorParameter == null)
        require(remoteAddPolicy == null || origins.any { it.canonical == remoteAddPolicy.origin.canonical })
    }

    fun allowsCookies(origin: HttpsOrigin): Boolean =
        cookieMode == SourceCookieMode.SOURCE_SCOPED && cookieOrigins.any { it.canonical == origin.canonical }
}

data class HostHttpRequest(
    val url: URI,
    val method: NetworkMethod,
    val headers: Map<String, String>,
    val body: ByteArray?,
    val referrer: URI?,
    val timeoutMs: Int,
    val maxResponseBytes: Int,
)

data class HostHttpResponse(
    val status: Int,
    val finalUrl: URI,
    val headers: Map<String, String>,
    val bytes: ByteArray,
)

/** Host-only transport. It never exposes an HTTP response, cookies, or streams to extension code. */
fun interface HostHttpTransport {
    suspend fun execute(request: HostHttpRequest): HostHttpResponse
}

enum class HostNetworkError {
    INVALID_REQUEST,
    DISALLOWED_ORIGIN,
    HEADER_DISALLOWED,
    BODY_LIMIT,
    RESPONSE_LIMIT,
    OFFLINE,
    TIMEOUT,
    CANCELLED,
    REDIRECT_LIMIT,
    REDIRECT_DISALLOWED,
    OFFLINE_MISS,
    DECODE,
    TRANSPORT,
}

class HostNetworkException(
    val error: HostNetworkError,
    val diagnosticId: String = UUID.randomUUID().toString(),
) : Exception(error.name)

/**
 * Validates all extension-controlled fields before a host transport runs. Caches are source/version
 * namespaced; no request headers or raw bytes leave this class through [SourceNetworkResponse].
 */
class HostNetworkGateway(
    private val transport: HostHttpTransport,
    private val cache: HostNetworkCache = InMemoryHostNetworkCache(),
    private val directActionTokens: DirectActionTokenRegistry = DirectActionTokenRegistry(),
) {
    private val cookieJar = SourceCookieJar()

    /** Imports user-approved request cookies into exactly one signed source/version origin scope. */
    fun importSourceCookies(grant: SourceNetworkGrant, origin: HttpsOrigin, rawCookie: String) {
        require(grant.allowsCookies(origin)) { "Cookie origin is not granted" }
        cookieJar.seed(grant, URI(origin.canonical), rawCookie)
    }
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun request(
        grant: SourceNetworkGrant,
        request: SourceNetworkRequest,
        operationContext: SourceOperationContext? = null,
    ): SourceNetworkResponse {
        validateOperationBoundary(grant, request, operationContext)
        val uri = parseAllowedUri(request.url, grant)
        val referrer = request.referrerUrl?.let { parseAllowedUri(it, grant) }
        val body = requestBody(request)
        val cacheKey = cacheKey(grant, request, uri)
        if (request.cache == NetworkCacheMode.OFFLINE_ONLY) {
            val cached = cacheKey?.let(cache::get) ?: throw HostNetworkException(HostNetworkError.OFFLINE_MISS)
            return cached.copy(cacheState = NetworkCacheState.STALE_OFFLINE)
        }
        val cached = cacheKey?.let(cache::get)
        if (request.cache == NetworkCacheMode.DEFAULT && cached != null) {
            return cached.copy(cacheState = NetworkCacheState.FRESH)
        }
        val lock = locks.computeIfAbsent("${grant.sourceId}\u0000${grant.extensionVersion}") { Mutex() }
        return lock.withLock {
            val current = cacheKey?.let(cache::get)
            if (request.cache == NetworkCacheMode.DEFAULT && current != null) {
                return@withLock current.copy(cacheState = NetworkCacheState.FRESH)
            }
            val response = executeFollowingRedirects(
                grant = grant,
                initialUrl = uri,
                request = request,
                headers = allowedHeaders(request.headers),
                body = body,
                referrer = referrer,
                operationContext = operationContext,
            )
            if (response.status !in 100..599) throw HostNetworkException(HostNetworkError.TRANSPORT)
            if (response.bytes.size > grant.maxResponseBytes) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            val finalUrl = parseAllowedUri(response.finalUrl.toString(), grant)
            val decoded = decode(response.bytes, request.decode, response.headers["content-type"])
            val value = SourceNetworkResponse(
                status = response.status,
                finalUrl = finalUrl.toString(),
                headers = response.headers.filterKeys { it.lowercase() in EXPOSED_RESPONSE_HEADERS },
                text = decoded.text,
                bytes = null,
                decodeUsed = decoded.mode,
                cacheState = when (request.cache) {
                    NetworkCacheMode.NETWORK_ONLY -> NetworkCacheState.BYPASSED
                    NetworkCacheMode.VALIDATE -> NetworkCacheState.VALIDATED
                    else -> NetworkCacheState.MISS
                },
                diagnosticId = UUID.randomUUID().toString(),
            )
            if (cacheKey != null && request.method != NetworkMethod.POST) cache.put(cacheKey, value)
            value
        }
    }

    private fun parseAllowedUri(value: String, grant: SourceNetworkGrant): URI {
        val uri = runCatching { URI(value) }.getOrElse { throw HostNetworkException(HostNetworkError.INVALID_REQUEST) }
        if (!uri.isAbsolute || !uri.scheme.equals("https", true) || uri.userInfo != null || uri.host.isNullOrBlank()) {
            throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
        }
        val origin = runCatching { HttpsOrigin("https://${uri.host}${if (uri.port in 1..65535 && uri.port != 443) ":${uri.port}" else ""}") }
            .getOrElse { throw HostNetworkException(HostNetworkError.INVALID_REQUEST) }
        if (grant.origins.none { it.canonical == origin.canonical }) {
            throw HostNetworkException(HostNetworkError.DISALLOWED_ORIGIN)
        }
        return uri
    }

    private suspend fun executeFollowingRedirects(
        grant: SourceNetworkGrant,
        initialUrl: URI,
        request: SourceNetworkRequest,
        headers: Map<String, String>,
        body: ByteArray?,
        referrer: URI?,
        operationContext: SourceOperationContext?,
    ): HostHttpResponse {
        if (operationContext?.kind == SourceOperationKind.REMOTE_LIBRARY_ADD) {
            directActionTokens.accept(
                sourceId = grant.sourceId,
                remoteBookId = requireNotNull(operationContext.remoteBookId),
                token = requireNotNull(operationContext.addToken),
            )
        }
        var url = initialUrl
        var method = request.method
        var currentBody = body
        var currentReferrer = referrer
        var currentRedirect: RemoteOperationRedirectPolicy? = null
        var redirects = 0
        while (true) {
            if (redirects == 0) {
                validateOperationBoundary(grant, request.copy(url = url.toString()), operationContext)
            } else if (operationContext != null) {
                val redirect = currentRedirect ?: throw HostNetworkException(HostNetworkError.REDIRECT_DISALLOWED)
                if (method != redirect.method) throw HostNetworkException(HostNetworkError.REDIRECT_DISALLOWED)
                val expectedReferrer = redirect.referrerPath?.let { URI(redirect.origin.canonical + it) }
                if (currentReferrer != expectedReferrer) throw HostNetworkException(HostNetworkError.REDIRECT_DISALLOWED)
                validateProtectedAddSurface(
                    grant,
                    request.copy(
                        url = url.toString(),
                        method = method,
                        form = request.form.takeIf { method == NetworkMethod.POST },
                        utf8Body = request.utf8Body.takeIf { method == NetworkMethod.POST },
                    ),
                    operationContext,
                )
            } else {
                validateOperationBoundary(
                    grant,
                    request.copy(
                        url = url.toString(),
                        method = method,
                        form = request.form.takeIf { method == NetworkMethod.POST },
                        utf8Body = request.utf8Body.takeIf { method == NetworkMethod.POST },
                    ),
                    null,
                )
            }
            val response = try {
                transport.execute(
                    HostHttpRequest(
                        url = url,
                        method = method,
                        headers = headers + cookieJar.requestHeader(grant, url),
                        body = currentBody,
                        referrer = currentReferrer,
                        timeoutMs = grant.requestTimeoutMs,
                        maxResponseBytes = grant.maxResponseBytes,
                    ),
                )
            } catch (error: HostNetworkException) {
                throw error
            } catch (_: Throwable) {
                throw HostNetworkException(HostNetworkError.TRANSPORT)
            }
            if (response.finalUrl != url) throw HostNetworkException(HostNetworkError.REDIRECT_DISALLOWED)
            cookieJar.store(grant, url, response.headers)
            if (response.status !in 300..399) return response
            if (response.status !in REDIRECT_STATUSES) return response
            val location = response.headers.entries.firstOrNull { it.key.equals("location", ignoreCase = true) }?.value
                ?: return response
            if (++redirects > MAX_REDIRECTS) throw HostNetworkException(HostNetworkError.REDIRECT_LIMIT)
            url = try {
                parseAllowedUri(url.resolve(location).toString(), grant)
            } catch (_: HostNetworkException) {
                throw HostNetworkException(HostNetworkError.REDIRECT_DISALLOWED)
            }
            if (operationContext != null) {
                val redirect = operationContext.redirectFor(url)
                    ?: throw HostNetworkException(HostNetworkError.REDIRECT_DISALLOWED)
                if (response.status in 307..308 && redirect.method != method) {
                    throw HostNetworkException(HostNetworkError.REDIRECT_DISALLOWED)
                }
                currentRedirect = redirect
                method = redirect.method
                currentBody = null
                currentReferrer = redirect.referrerPath?.let { path -> URI(redirect.origin.canonical + path) }
            } else if (response.status == 303 || method == NetworkMethod.POST && response.status in 301..302) {
                method = NetworkMethod.GET
                currentBody = null
            }
        }
    }

    private fun validateOperationBoundary(
        grant: SourceNetworkGrant,
        request: SourceNetworkRequest,
        operationContext: SourceOperationContext?,
    ) {
        if (operationContext?.kind == SourceOperationKind.REMOTE_LIBRARY_ADD) {
            if (grant.remoteAddPolicy != operationContext.policy || request.cache != NetworkCacheMode.NETWORK_ONLY) {
                throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
            }
            operationContext.validate(request)
            return
        }
        validateProtectedAddSurface(grant, request, operationContext)
        operationContext?.validate(request)
    }

    private fun validateProtectedAddSurface(
        grant: SourceNetworkGrant,
        request: SourceNetworkRequest,
        operationContext: SourceOperationContext?,
    ) {
        if (operationContext?.kind != SourceOperationKind.REMOTE_LIBRARY_ADD && grant.remoteAddPolicy?.matchesSurface(request) == true) {
            throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
        }
    }

    private fun RemoteOperationRequestPolicy.matchesSurface(request: SourceNetworkRequest): Boolean {
        val uri = runCatching { URI(request.url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank()) return false
        val requestOrigin = runCatching {
            HttpsOrigin("https://${uri.host}${if (uri.port in 1..65535 && uri.port != 443) ":${uri.port}" else ""}")
        }.getOrNull() ?: return false
        if (request.method == method && uri.path == path && requestOrigin.canonical == origin.canonical) return true
        return redirects.any { redirect ->
            request.method == redirect.method && uri.path == redirect.path && requestOrigin.canonical == redirect.origin.canonical
        }
    }

    private fun allowedHeaders(headers: Map<String, String>): Map<String, String> {
        if (headers.size > 32) throw HostNetworkException(HostNetworkError.HEADER_DISALLOWED)
        return buildMap {
            headers.forEach { (name, value) ->
                val normalized = name.lowercase()
                if ('\u0000' in name || '\u0000' in value || normalized !in REQUEST_HEADER_ALLOWLIST) {
                    throw HostNetworkException(HostNetworkError.HEADER_DISALLOWED)
                }
                put(normalized, value)
            }
        }
    }

    private fun requestBody(request: SourceNetworkRequest): ByteArray? {
        if (request.method != NetworkMethod.POST) return null
        val form = request.form
        val utf8Body = request.utf8Body
        val body = when {
            form != null -> form.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, StandardCharsets.UTF_8.name())}=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
            }.encodeToByteArray()
            utf8Body != null -> utf8Body.encodeToByteArray()
            else -> byteArrayOf()
        }
        if (body.size > MAX_BODY_BYTES) throw HostNetworkException(HostNetworkError.BODY_LIMIT)
        return body
    }

    private fun cacheKey(grant: SourceNetworkGrant, request: SourceNetworkRequest, uri: URI): HostNetworkCacheKey? {
        if (request.method == NetworkMethod.POST || request.cache == NetworkCacheMode.NETWORK_ONLY) return null
        return HostNetworkCacheKey(
            sourceId = grant.sourceId,
            extensionVersion = grant.extensionVersion,
            identity = request.semanticCacheKey ?: "${request.method.name}:${uri}",
            decode = request.decode,
        )
    }

    private fun decode(bytes: ByteArray, requested: DecodeMode, contentType: String?): DecodedText {
        val mode = when (requested) {
            DecodeMode.AUTO -> charsetFromBom(bytes) ?: charsetFromContentType(contentType) ?: DecodeMode.UTF8
            else -> requested
        }
        val charset = when (mode) {
            DecodeMode.UTF8 -> StandardCharsets.UTF_8
            DecodeMode.GB18030 -> runCatching { Charset.forName("GB18030") }
                .getOrElse { throw HostNetworkException(HostNetworkError.DECODE) }
            DecodeMode.BIG5_HKSCS -> runCatching { Charset.forName("Big5-HKSCS") }
                .getOrElse { throw HostNetworkException(HostNetworkError.DECODE) }
            DecodeMode.AUTO -> error("AUTO is resolved before decoding")
        }
        val offset = if (mode == DecodeMode.UTF8 && bytes.startsWith(UTF8_BOM)) UTF8_BOM.size else 0
        val text = try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                .toString()
        } catch (_: CharacterCodingException) {
            throw HostNetworkException(HostNetworkError.DECODE)
        }
        return DecodedText(text, mode)
    }

    private fun charsetFromBom(bytes: ByteArray): DecodeMode? = if (bytes.startsWith(UTF8_BOM)) DecodeMode.UTF8 else null

    private fun charsetFromContentType(contentType: String?): DecodeMode? = when (
        Regex("charset\\s*=\\s*['\\\"]?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE).find(contentType.orEmpty())
            ?.groupValues?.get(1)?.lowercase()
    ) {
        "utf-8", "utf8" -> DecodeMode.UTF8
        "gb18030", "gbk", "gb2312" -> DecodeMode.GB18030
        "big5", "big5-hkscs" -> DecodeMode.BIG5_HKSCS
        else -> null
    }

    private data class DecodedText(val text: String, val mode: DecodeMode)

    private companion object {
        const val MAX_BODY_BYTES = 64 * 1024
        const val MAX_REDIRECTS = 5
        val UTF8_BOM = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())
        val REQUEST_HEADER_ALLOWLIST = setOf("accept", "accept-language", "if-none-match", "if-modified-since")
        val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
        val EXPOSED_RESPONSE_HEADERS = setOf("content-type", "etag", "last-modified")
    }
}

private class SourceCookieJar {
    private val cookies = ConcurrentHashMap<SourceScope, MutableList<StoredCookie>>()

    fun requestHeader(grant: SourceNetworkGrant, uri: URI): Map<String, String> {
        if (!grant.allowsCookies(uri.asHttpsOrigin())) return emptyMap()
        val scope = SourceScope(grant.sourceId, grant.extensionVersion)
        val host = uri.host.lowercase()
        val path = uri.path.ifBlank { "/" }
        val values = cookies[scope]?.let { entries ->
            synchronized(entries) {
                entries.removeAll { it.cookie.hasExpired() }
                entries.filter { it.matches(host, path) }.joinToString("; ") { "${it.cookie.name}=${it.cookie.value}" }
            }
        }.orEmpty()
        return if (values.isEmpty()) emptyMap() else mapOf("cookie" to values)
    }

    fun seed(grant: SourceNetworkGrant, origin: URI, rawCookie: String) {
        require(grant.allowsCookies(origin.asHttpsOrigin())) { "Cookie origin is not granted" }
        val scope = SourceScope(grant.sourceId, grant.extensionVersion)
        val entries = cookies.getOrPut(scope) { mutableListOf() }
        val host = origin.host.lowercase()
        rawCookie.split(';').map(String::trim).filter(String::isNotEmpty).forEach { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@forEach
            val name = pair.substring(0, separator).trim()
            val value = pair.substring(separator + 1).trim()
            if (!COOKIE_NAME.matches(name) || '\u0000' in value) return@forEach
            val stored = StoredCookie(HttpCookie(name, value), host, hostOnly = true, path = "/")
            synchronized(entries) {
                entries.removeAll { it.cookie.name == name && it.domain == host && it.path == "/" }
                entries += stored
            }
        }
    }

    fun store(grant: SourceNetworkGrant, requestUri: URI, headers: Map<String, String>) {
        if (!grant.allowsCookies(requestUri.asHttpsOrigin())) return
        val scope = SourceScope(grant.sourceId, grant.extensionVersion)
        val entries = cookies.getOrPut(scope) { mutableListOf() }
        headers.filterKeys { it.equals("set-cookie", ignoreCase = true) }.values
            .flatMap { value -> runCatching { HttpCookie.parse(value) }.getOrDefault(emptyList()) }
            .forEach { cookie ->
                val domain = cookie.domain?.trimStart('.')?.lowercase().orEmpty()
                val requestHost = requestUri.host.lowercase()
                if (domain.isNotEmpty() && requestHost != domain && !requestHost.endsWith(".$domain")) return@forEach
                val stored = StoredCookie(
                    cookie = cookie,
                    domain = domain.ifEmpty { requestHost },
                    hostOnly = domain.isEmpty(),
                    path = cookie.path?.takeIf { it.startsWith('/') } ?: "/",
                )
                synchronized(entries) {
                    entries.removeAll {
                        it.cookie.name == stored.cookie.name && it.domain == stored.domain && it.path == stored.path
                    }
                    if (!cookie.hasExpired()) entries += stored
                }
            }
    }

    private data class SourceScope(val sourceId: String, val extensionVersion: String)

    private data class StoredCookie(
        val cookie: HttpCookie,
        val domain: String,
        val hostOnly: Boolean,
        val path: String,
    ) {
        fun matches(host: String, requestPath: String): Boolean {
            val domainMatches = if (hostOnly) host == domain else host == domain || host.endsWith(".$domain")
            return domainMatches && requestPath.startsWith(path)
        }
    }

    private companion object {
        val COOKIE_NAME = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}$")
    }
}

private fun URI.asHttpsOrigin(): HttpsOrigin = HttpsOrigin(
    "https://${host}${if (port in 1..65535 && port != 443) ":$port" else ""}",
)

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
