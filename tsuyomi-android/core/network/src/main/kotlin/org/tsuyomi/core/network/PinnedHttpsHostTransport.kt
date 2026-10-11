/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.tsuyomi.shared.sourcecontract.NetworkMethod

/** Conservative public-unicast policy shared by DNS answers and the pinned transport. */
object PublicInternetAddressPolicy {
    private val nonPublicIpv4 = listOf(
        cidr4("0.0.0.0", 8),
        cidr4("10.0.0.0", 8),
        cidr4("100.64.0.0", 10),
        cidr4("127.0.0.0", 8),
        cidr4("169.254.0.0", 16),
        cidr4("172.16.0.0", 12),
        cidr4("192.0.0.0", 24),
        cidr4("192.0.2.0", 24),
        cidr4("192.88.99.0", 24),
        cidr4("192.168.0.0", 16),
        cidr4("198.18.0.0", 15),
        cidr4("198.51.100.0", 24),
        cidr4("203.0.113.0", 24),
        cidr4("224.0.0.0", 4),
        cidr4("240.0.0.0", 4),
    )
    private val nonPublicIpv6 = listOf(
        cidr6("2001::", 23),
        cidr6("2001:db8::", 32),
        cidr6("2002::", 16),
        cidr6("3ffe::", 16),
        cidr6("3fff::", 20),
    )

    fun isPublicGlobalUnicast(address: InetAddress): Boolean {
        val bytes = address.address
        return when {
            address is Inet4Address && bytes.size == 4 -> nonPublicIpv4.none { (base, prefix) -> contains(bytes, base, prefix) }
            address is Inet6Address && bytes.size == 16 && address.scopeId == 0 -> {
                if (isIpv4Mapped(bytes)) return false
                // Only 2000::/3 global unicast is admitted; this excludes local, multicast,
                // unspecified, IPv4-compatible and NAT64 well-known ranges.
                (bytes[0].toInt() and 0xe0) == 0x20 && nonPublicIpv6.none { (base, prefix) -> contains(bytes, base, prefix) }
            }
            else -> false
        }
    }

    private fun isIpv4Mapped(bytes: ByteArray): Boolean =
        bytes.take(10).all { it == 0.toByte() } && bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()

    private fun cidr4(value: String, prefix: Int): Pair<ByteArray, Int> =
        InetAddress.getByName(value).address to prefix

    private fun cidr6(value: String, prefix: Int): Pair<ByteArray, Int> =
        InetAddress.getByName(value).address to prefix

    private fun contains(address: ByteArray, base: ByteArray, prefix: Int): Boolean {
        val wholeBytes = prefix / 8
        val remainingBits = prefix % 8
        for (index in 0 until wholeBytes) if (address[index] != base[index]) return false
        if (remainingBits == 0) return true
        val mask = 0xff shl (8 - remainingBits)
        return (address[wholeBytes].toInt() and mask) == (base[wholeBytes].toInt() and mask)
    }
}

/**
 * HTTPS-only transport which connects exclusively to caller-vetted numeric address pins. The
 * URL hostname is retained for SNI, default platform trust and explicit HTTPS hostname checks.
 * Redirects are deliberately returned to the resource owner and never followed here.
 */
class PinnedHttpsHostTransport internal constructor(
    private val createSocket: () -> Socket,
    private val connectSocket: (Socket, InetAddress, Int, Int) -> Unit,
    private val tlsSocketFactory: SSLSocketFactory,
    private val hostnameVerifier: HostnameVerifier,
) {
    constructor() : this(
        createSocket = { Socket() },
        connectSocket = { socket, address, port, timeoutMs ->
            socket.connect(InetSocketAddress(address, port), timeoutMs)
        },
        tlsSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
        hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier(),
    )

    @OptIn(InternalCoroutinesApi::class)
    suspend fun execute(request: HostHttpRequest, vettedAddresses: List<InetAddress>): HostHttpResponse =
        withContext(Dispatchers.IO) {
            val target = validateRequest(request)
            val pins = vettedAddresses
                .asSequence()
                .map { address ->
                    if (!PublicInternetAddressPolicy.isPublicGlobalUnicast(address)) {
                        throw HostNetworkException(HostNetworkError.DISALLOWED_ORIGIN)
                    }
                    // Discard any resolver-associated hostname; the socket address is numeric only.
                    InetAddress.getByAddress(address.address)
                }
                .distinctBy { it.address.toList() }
                .toList()
            if (pins.isEmpty()) throw HostNetworkException(HostNetworkError.DISALLOWED_ORIGIN)

            val timeoutMs = minOf(request.timeoutMs, MAX_TOTAL_TIMEOUT_MS)
            val startedAt = System.nanoTime()
            val activeSocket = AtomicReference<Socket?>(null)
            val job = currentCoroutineContext().job
            val cancellationHandle = job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
                if (cause != null) activeSocket.getAndSet(null)?.let { runCatching { it.close() } }
            }
            val watchdog = DEADLINE_WATCHDOG.schedule(
                { activeSocket.getAndSet(null)?.let { runCatching { it.close() } } },
                timeoutMs.toLong(),
                TimeUnit.MILLISECONDS,
            )
            try {
                currentCoroutineContext().ensureActive()
                for (pin in pins) {
                    currentCoroutineContext().ensureActive()
                    val remainingMs = remainingMillis(startedAt, timeoutMs)
                    if (remainingMs <= 0) throw HostNetworkException(HostNetworkError.TIMEOUT)
                    var tcp: Socket? = null
                    try {
                        tcp = createSocket()
                        activeSocket.set(tcp)
                        connectSocket(tcp, pin, HTTPS_PORT, minOf(remainingMs, CONNECT_TIMEOUT_MS))
                        tcp.soTimeout = minOf(remainingMillis(startedAt, timeoutMs), SOCKET_READ_TIMEOUT_MS).coerceAtLeast(1)
                        return@withContext executeTls(request, target, tcp, activeSocket, startedAt, timeoutMs)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: HostNetworkException) {
                        throw failure
                    } catch (failure: SocketTimeoutException) {
                        if (remainingMillis(startedAt, timeoutMs) <= 0) throw HostNetworkException(HostNetworkError.TIMEOUT)
                    } catch (failure: IOException) {
                        if (remainingMillis(startedAt, timeoutMs) <= 0) throw HostNetworkException(HostNetworkError.TIMEOUT)
                    } finally {
                        tcp?.let { socket ->
                            activeSocket.compareAndSet(socket, null)
                            runCatching { socket.close() }
                        }
                    }
                }
                throw HostNetworkException(HostNetworkError.OFFLINE)
            } finally {
                watchdog.cancel(false)
                cancellationHandle.dispose()
                activeSocket.getAndSet(null)?.let { runCatching { it.close() } }
            }
        }

    private fun executeTls(
        request: HostHttpRequest,
        target: Target,
        tcp: Socket,
        activeSocket: AtomicReference<Socket?>,
        startedAt: Long,
        timeoutMs: Int,
    ): HostHttpResponse {
        val tls = tlsSocketFactory.createSocket(tcp, target.host, HTTPS_PORT, true) as? SSLSocket
            ?: throw HostNetworkException(HostNetworkError.TRANSPORT)
        activeSocket.set(tls)
        tls.soTimeout = minOf(remainingMillis(startedAt, timeoutMs), SOCKET_READ_TIMEOUT_MS).coerceAtLeast(1)
        val parameters = tls.sslParameters
        parameters.endpointIdentificationAlgorithm = "HTTPS"
        if (!target.isIpLiteral) parameters.serverNames = listOf(SNIHostName(target.host))
        tls.sslParameters = parameters
        tls.startHandshake()
        if (!hostnameVerifier.verify(target.host, tls.session)) {
            throw HostNetworkException(HostNetworkError.TRANSPORT)
        }
        tls.soTimeout = minOf(remainingMillis(startedAt, timeoutMs), SOCKET_READ_TIMEOUT_MS).coerceAtLeast(1)
        val output = tls.getOutputStream()
        output.write(target.requestBytes())
        output.flush()
        tls.soTimeout = minOf(remainingMillis(startedAt, timeoutMs), SOCKET_READ_TIMEOUT_MS).coerceAtLeast(1)
        val input = tls.getInputStream()
        return readResponse(input, request, target.uri)
    }

    private fun validateRequest(request: HostHttpRequest): Target {
        val uri = request.url
        if (
            request.method != NetworkMethod.GET || request.body != null || request.referrer != null ||
            request.timeoutMs <= 0 || request.maxResponseBytes !in 1..MAX_RESPONSE_BODY_BYTES ||
            uri.scheme?.equals("https", ignoreCase = true) != true || uri.isOpaque ||
            uri.rawUserInfo != null || uri.rawFragment != null || (uri.port != -1 && uri.port != HTTPS_PORT)
        ) throw HostNetworkException(HostNetworkError.INVALID_REQUEST)

        val parsedHost = uri.host?.removeSurrounding("[", "]")
            ?.takeIf { it.isNotBlank() && '%' !in it }
            ?: throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
        val host = parsedHost.lowercase(Locale.ROOT)
        val isIpLiteral = isStrictIpv4(host) || (':' in host && isStrictIpv6Literal(host))
        if ((':' in host && !isIpLiteral) || (!isIpLiteral && host.all { it in '0'..'9' || it == '.' })) {
            throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
        }
        val path = uri.rawPath.ifEmpty { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val target = path + query
        if (target.length > MAX_REQUEST_TARGET_CHARS || target.any { it.code !in 0x21..0x7e }) {
            throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
        }

        val acceptHeaders = request.headers.entries.filter { it.key.equals("Accept", ignoreCase = true) }
        if (request.headers.size != acceptHeaders.size || acceptHeaders.size > 1) {
            throw HostNetworkException(HostNetworkError.HEADER_DISALLOWED)
        }
        val accept = acceptHeaders.singleOrNull()?.value ?: "*/*"
        if (accept.isBlank() || accept.length > MAX_ACCEPT_LENGTH || accept.any { it <= '\u001f' || it == '\u007f' }) {
            throw HostNetworkException(HostNetworkError.HEADER_DISALLOWED)
        }
        return Target(uri, host, isIpLiteral, target, accept)
    }

    internal fun readResponse(input: java.io.InputStream, request: HostHttpRequest, finalUrl: URI): HostHttpResponse {
        val budget = HeaderBudget()
        val statusLine = readLine(input, budget, MAX_RESPONSE_LINE_BYTES)
            ?: throw HostNetworkException(HostNetworkError.TRANSPORT)
        val statusMatch = STATUS_LINE.matchEntire(statusLine)
            ?: throw HostNetworkException(HostNetworkError.TRANSPORT)
        val status = statusMatch.groupValues[1].toInt()
        if (status !in 200..599) throw HostNetworkException(HostNetworkError.TRANSPORT)

        val entries = ArrayList<HostResponseHeaders.Entry>()
        while (true) {
            val line = readLine(input, budget, MAX_RESPONSE_LINE_BYTES)
                ?: throw HostNetworkException(HostNetworkError.TRANSPORT)
            if (line.isEmpty()) break
            if (entries.size >= MAX_RESPONSE_HEADERS) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            val colon = line.indexOf(':')
            if (colon <= 0 || line[0] == ' ' || line[0] == '\t') throw HostNetworkException(HostNetworkError.TRANSPORT)
            val name = line.substring(0, colon)
            if (!name.all(::isTokenCharacter)) throw HostNetworkException(HostNetworkError.TRANSPORT)
            val value = line.substring(colon + 1).trim(' ', '\t')
            if (value.any { it == '\u0000' || it == '\r' || it == '\n' || (it < ' ' && it != '\t') || it == '\u007f' }) {
                throw HostNetworkException(HostNetworkError.TRANSPORT)
            }
            entries += HostResponseHeaders.Entry(name, value)
        }

        val contentLengths = entries.filter { it.name.equals("content-length", ignoreCase = true) }
        val transferEncodings = entries.filter { it.name.equals("transfer-encoding", ignoreCase = true) }
        if (contentLengths.size > 1 || (contentLengths.isNotEmpty() && transferEncodings.isNotEmpty())) {
            throw HostNetworkException(HostNetworkError.TRANSPORT)
        }
        if (entries.any { it.name.equals("content-encoding", ignoreCase = true) && !it.value.equals("identity", true) }) {
            throw HostNetworkException(HostNetworkError.TRANSPORT)
        }
        val declaredLength = contentLengths.singleOrNull()?.value?.let(::parseContentLength)
        if (contentLengths.isNotEmpty() && declaredLength == null) throw HostNetworkException(HostNetworkError.TRANSPORT)
        if (declaredLength != null && declaredLength > request.maxResponseBytes) {
            throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
        }
        val body = when {
            status == 204 || status == 304 -> byteArrayOf()
            transferEncodings.isNotEmpty() -> {
                if (transferEncodings.size != 1 || !transferEncodings.single().value.equals("chunked", ignoreCase = true)) {
                    throw HostNetworkException(HostNetworkError.TRANSPORT)
                }
                readChunked(input, request.maxResponseBytes, budget)
            }
            declaredLength != null -> readExactly(input, declaredLength.toInt())
            else -> readUntilEof(input, request.maxResponseBytes)
        }
        return HostHttpResponse(
            status = status,
            finalUrl = finalUrl,
            headers = HostResponseHeaders.fromHeaderFields(
                linkedMapOf<String?, List<String>?>().apply {
                    entries.groupBy { it.name }.forEach { (name, values) -> put(name, values.map { it.value }) }
                },
            ),
            bytes = body,
        )
    }

    private fun readChunked(input: java.io.InputStream, maxBytes: Int, headerBudget: HeaderBudget): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
        var chunks = 0
        var framingBytes = 0
        while (true) {
            if (++chunks > MAX_CHUNKS) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            val line = readLine(input, headerBudget, MAX_RESPONSE_LINE_BYTES)
                ?: throw HostNetworkException(HostNetworkError.TRANSPORT)
            framingBytes += line.length + 2
            if (framingBytes > MAX_CHUNK_FRAMING_BYTES) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            val sizeText = line.substringBefore(';')
            if (sizeText.isEmpty() || sizeText.length > 16 || sizeText.any { it.digitToIntOrNull(16) == null }) {
                throw HostNetworkException(HostNetworkError.TRANSPORT)
            }
            val size = sizeText.toLongOrNull(16) ?: throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            if (size == 0L) {
                var trailers = 0
                while (true) {
                    val trailer = readLine(input, headerBudget, MAX_RESPONSE_LINE_BYTES)
                        ?: throw HostNetworkException(HostNetworkError.TRANSPORT)
                    framingBytes += trailer.length + 2
                    if (framingBytes > MAX_CHUNK_FRAMING_BYTES) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
                    if (trailer.isEmpty()) return output.toByteArray()
                    if (++trailers > MAX_RESPONSE_HEADERS || trailer.startsWith(' ') || trailer.startsWith('\t') || ':' !in trailer) {
                        throw HostNetworkException(HostNetworkError.TRANSPORT)
                    }
                    val name = trailer.substringBefore(':')
                    if (!name.all(::isTokenCharacter) || name.equals("content-length", true) || name.equals("transfer-encoding", true)) {
                        throw HostNetworkException(HostNetworkError.TRANSPORT)
                    }
                }
            }
            if (size > maxBytes.toLong() - output.size()) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            val remaining = size.toInt()
            val buffer = ByteArray(minOf(BODY_BUFFER_BYTES, remaining))
            var left = remaining
            while (left > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, left))
                if (count < 0) throw HostNetworkException(HostNetworkError.OFFLINE)
                if (count == 0) continue
                output.write(buffer, 0, count)
                left -= count
            }
            expectCrlf(input)
            framingBytes += 2
            if (framingBytes > MAX_CHUNK_FRAMING_BYTES) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
        }
    }

    private fun readExactly(input: java.io.InputStream, count: Int): ByteArray {
        val output = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(output, offset, count - offset)
            if (read < 0) throw HostNetworkException(HostNetworkError.OFFLINE)
            if (read > 0) offset += read
        }
        return output
    }

    private fun readUntilEof(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
        val buffer = ByteArray(BODY_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() > maxBytes - count) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            output.write(buffer, 0, count)
        }
    }

    private fun readLine(input: java.io.InputStream, budget: HeaderBudget, maxLineBytes: Int): String? {
        val bytes = ByteArrayOutputStream()
        var previous = -1
        while (true) {
            val next = input.read()
            if (next < 0) {
                if (bytes.size() == 0) return null
                throw HostNetworkException(HostNetworkError.TRANSPORT)
            }
            budget.add(1)
            if (budget.bytes > MAX_RESPONSE_HEADER_BYTES || bytes.size() >= maxLineBytes) {
                throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            }
            if (previous == '\r'.code && next == '\n'.code) {
                val line = bytes.toByteArray()
                if (line.isEmpty() || line.last() != '\r'.toByte()) throw HostNetworkException(HostNetworkError.TRANSPORT)
                return String(line, 0, line.size - 1, StandardCharsets.ISO_8859_1)
            }
            if (previous == '\r'.code || next == '\n'.code || next > 0x7e || next < 0x20 && next != '\r'.code) {
                throw HostNetworkException(HostNetworkError.TRANSPORT)
            }
            bytes.write(next)
            previous = next
        }
    }

    private fun expectCrlf(input: java.io.InputStream) {
        if (input.read() != '\r'.code || input.read() != '\n'.code) throw HostNetworkException(HostNetworkError.TRANSPORT)
    }

    private fun remainingMillis(startedAtNanos: Long, timeoutMs: Int): Int {
        val elapsedNanos = System.nanoTime() - startedAtNanos
        val remainingNanos = timeoutMs.toLong() * 1_000_000 - elapsedNanos
        return if (remainingNanos <= 0) 0 else ((remainingNanos + 999_999) / 1_000_000).toInt()
    }

    private fun isStrictIpv4(host: String): Boolean {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && (part == "0" || !part.startsWith('0')) && part.all { it in '0'..'9' } &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun isStrictIpv6Literal(host: String): Boolean =
        host.contains(':') && host.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' } &&
            runCatching { InetAddress.getByName(host) is Inet6Address }.getOrDefault(false)

    private fun parseContentLength(value: String): Long? =
        if (value.isEmpty() || value.any { it !in '0'..'9' }) null else value.toLongOrNull()

    private fun isTokenCharacter(char: Char): Boolean =
        char.isLetterOrDigit() || char in "!#$%&'*+-.^_`|~"

    private data class Target(
        val uri: URI,
        val host: String,
        val isIpLiteral: Boolean,
        val pathAndQuery: String,
        val accept: String,
    ) {
        fun requestBytes(): ByteArray {
            val hostField = if (':' in host) "[$host]" else host
            return buildString {
                append("GET ").append(pathAndQuery).append(" HTTP/1.1\r\n")
                append("Host: ").append(hostField).append("\r\n")
                append("Accept: ").append(accept).append("\r\n")
                append("Accept-Encoding: identity\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.ISO_8859_1)
        }
    }

    private class HeaderBudget(var bytes: Int = 0) {
        fun add(count: Int) {
            bytes += count
        }
    }

    private companion object {
        const val HTTPS_PORT = 443
        const val CONNECT_TIMEOUT_MS = 5_000
        const val SOCKET_READ_TIMEOUT_MS = 5_000
        const val MAX_TOTAL_TIMEOUT_MS = 20_000
        const val MAX_RESPONSE_HEADER_BYTES = 16 * 1024
        const val MAX_RESPONSE_LINE_BYTES = 8 * 1024
        const val MAX_RESPONSE_HEADERS = 128
        const val MAX_RESPONSE_BODY_BYTES = 16 * 1024 * 1024
        const val MAX_CHUNKS = 4_096
        const val MAX_CHUNK_FRAMING_BYTES = 16 * 1024
        const val BODY_BUFFER_BYTES = 4 * 1024
        const val MAX_ACCEPT_LENGTH = 512
        const val MAX_REQUEST_TARGET_CHARS = 4_096
        val STATUS_LINE = Regex("HTTP/1\\.[01] ([2-5][0-9][0-9])(?: [^\\r\\n]*)?")
        val DEADLINE_WATCHDOG = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "tsuyomi-pinned-https-deadline").apply { isDaemon = true }
        }
    }
}
