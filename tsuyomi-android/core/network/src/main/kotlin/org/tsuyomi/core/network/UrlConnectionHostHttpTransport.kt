/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Platform transport deliberately does not follow redirects: [HostNetworkGateway] validates every
 * location against a source grant before issuing the next request.
 */
class UrlConnectionHostHttpTransport : HostHttpTransport {
    override suspend fun execute(request: HostHttpRequest): HostHttpResponse = withContext(Dispatchers.IO) {
        val connection = (request.url.toURL().openConnection() as? HttpURLConnection)
            ?: throw HostNetworkException(HostNetworkError.TRANSPORT)
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = request.method.name
            connection.connectTimeout = request.timeoutMs
            connection.readTimeout = request.timeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Accept-Encoding", "identity")
            request.referrer?.let { connection.setRequestProperty("Referer", it.toASCIIString()) }
            request.headers.forEach(connection::setRequestProperty)
            request.body?.let { body ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }

            val status = connection.responseCode
            val contentLength = connection.contentLengthLong
            if (contentLength > request.maxResponseBytes) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.use { input -> input.readBounded(request.maxResponseBytes) } ?: byteArrayOf()
            HostHttpResponse(
                status = status,
                finalUrl = URI(connection.url.toString()),
                headers = connection.headerFields
                    .filterKeys { it != null }
                    .mapValues { (_, values) -> values.orEmpty().joinToString(", ") },
                bytes = bytes,
            )
        } catch (_: SocketTimeoutException) {
            throw HostNetworkException(HostNetworkError.TIMEOUT)
        } catch (_: UnknownHostException) {
            throw HostNetworkException(HostNetworkError.OFFLINE)
        } catch (_: NoRouteToHostException) {
            throw HostNetworkException(HostNetworkError.OFFLINE)
        } catch (_: ConnectException) {
            throw HostNetworkException(HostNetworkError.OFFLINE)
        } catch (_: SocketException) {
            throw HostNetworkException(HostNetworkError.OFFLINE)
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() > maxBytes - count) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            output.write(buffer, 0, count)
        }
    }
}
