/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import java.net.Inet6Address
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.NetworkMethod

class PinnedHttpsHostTransportTest {
    @Test
    fun conservative_address_policy_rejects_reserved_v4_and_transition_or_mapped_v6() {
        val nonPublicV4 = listOf(
            "0.0.0.1",
            "10.0.0.1",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.1.1",
            "172.16.0.1",
            "192.0.0.1",
            "192.0.2.1",
            "192.88.99.1",
            "192.168.1.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "224.0.0.1",
            "240.0.0.1",
        )
        nonPublicV4.forEach { address ->
            assertFalse(address, PublicInternetAddressPolicy.isPublicGlobalUnicast(InetAddress.getByName(address)))
        }
        assertTrue(PublicInternetAddressPolicy.isPublicGlobalUnicast(InetAddress.getByName("8.8.8.8")))
        assertTrue(PublicInternetAddressPolicy.isPublicGlobalUnicast(InetAddress.getByName("2001:4860:4860::8888")))

        listOf("2001::1", "2001:db8::1", "2002::1", "3ffe::1", "3fff::1", "64:ff9b::808:808").forEach { address ->
            assertFalse(address, PublicInternetAddressPolicy.isPublicGlobalUnicast(InetAddress.getByName(address)))
        }
        val mapped = ByteArray(16).apply {
            this[10] = 0xff.toByte()
            this[11] = 0xff.toByte()
            this[12] = 8
            this[13] = 8
            this[14] = 8
            this[15] = 8
        }
        assertFalse(PublicInternetAddressPolicy.isPublicGlobalUnicast(Inet6Address.getByAddress(null, mapped, 0)))
    }

    @Test
    fun all_pins_are_validated_before_any_socket_is_created() = runBlocking {
        var socketCreations = 0
        val transport = transport(createSocket = { socketCreations++; Socket() })
        val public = InetAddress.getByName("8.8.8.8")
        val private = InetAddress.getByName("10.0.0.1")

        assertNetworkError(HostNetworkError.DISALLOWED_ORIGIN) {
            transport.execute(request(), listOf(public, private))
        }
        assertNetworkError(HostNetworkError.DISALLOWED_ORIGIN) {
            transport.execute(request(), emptyList())
        }
        assertEquals(0, socketCreations)
    }

    @Test
    fun the_socket_dialer_receives_only_the_approved_numeric_address_and_port() = runBlocking {
        var socketCreations = 0
        var dialedAddress: InetAddress? = null
        var dialedPort = 0
        val requestedPin = InetAddress.getByAddress("assets.example", byteArrayOf(8, 8, 8, 8))
        val transport = transport(
            createSocket = { socketCreations++; Socket() },
            connectSocket = { _, address, port, _ ->
                dialedAddress = address
                dialedPort = port
            },
        )

        val failure = runCatching { transport.execute(request(), listOf(requestedPin)) }.exceptionOrNull()
        assertTrue(failure is HostNetworkException)
        assertEquals(HostNetworkError.OFFLINE, (failure as HostNetworkException).error)
        assertEquals(1, socketCreations)
        assertEquals("8.8.8.8", dialedAddress?.hostAddress)
        assertEquals(443, dialedPort)
    }

    @Test
    fun insecure_urls_and_credential_or_cookie_headers_are_rejected_before_dial() = runBlocking {
        var socketCreations = 0
        val transport = transport(createSocket = { socketCreations++; Socket() })
        val pin = InetAddress.getByName("8.8.8.8")

        listOf(
            request(url = "http://assets.example/image.png"),
            request(url = "https://127.1/image.png"),
            request(url = "https://assets.example:8443/image.png"),
            request(url = "https://user@assets.example/image.png"),
            request(headers = mapOf("Cookie" to "sid=secret")),
            request(headers = mapOf("Authorization" to "Bearer secret")),
        ).forEach { invalid ->
            val error = runCatching { transport.execute(invalid, listOf(pin)) }.exceptionOrNull()
            assertTrue(error is HostNetworkException)
        }
        assertEquals(0, socketCreations)
    }

    @Test
    fun response_parser_rejects_te_cl_ambiguity_and_truncated_fixed_body() {
        val transport = transport()
        val request = request()
        val ambiguous = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n"
        assertNetworkError(HostNetworkError.TRANSPORT) {
            transport.readResponse(ambiguous.byteInputStream(), request, request.url)
        }

        val truncated = "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nabc"
        assertNetworkError(HostNetworkError.OFFLINE) {
            transport.readResponse(truncated.byteInputStream(), request, request.url)
        }
    }

    @Test
    fun response_parser_bounds_declared_and_chunked_bodies_and_checks_chunk_truncation() {
        val transport = transport()
        val request = request()
        val body = "x".repeat(request.maxResponseBytes + 1)
        val oversized = ByteArrayInputStream(
            "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n\r\n$body".encodeToByteArray(),
        )
        assertNetworkError(HostNetworkError.RESPONSE_LIMIT) {
            transport.readResponse(oversized, request, request.url)
        }
        assertEquals(body.length, oversized.available())

        val smallRequest = request.copy(maxResponseBytes = 3)
        val tooManyChunkBytes = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\ntest\r\n0\r\n\r\n"
        assertNetworkError(HostNetworkError.RESPONSE_LIMIT) {
            transport.readResponse(tooManyChunkBytes.byteInputStream(), smallRequest, smallRequest.url)
        }
        val truncatedChunk = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nab"
        assertNetworkError(HostNetworkError.OFFLINE) {
            transport.readResponse(truncatedChunk.byteInputStream(), request, request.url)
        }
    }

    @Test
    fun response_parser_accepts_bounded_chunked_bytes_and_preserves_repeated_headers() {
        val transport = transport()
        val request = request()
        val raw = "HTTP/1.1 200 OK\r\nX-Repeat: one\r\nX-Repeat: two\r\n" +
            "Transfer-Encoding: chunked\r\n\r\n4\r\ntest\r\n0\r\n\r\n"
        val response = transport.readResponse(raw.byteInputStream(), request, request.url)
        assertArrayEquals("test".encodeToByteArray(), response.bytes)
        val repeated = mutableListOf<String>()
        response.headers.forEachValue("x-repeat", repeated::add)
        assertEquals(listOf("one", "two"), repeated)
    }

    private fun transport(
        createSocket: () -> Socket = { Socket() },
        connectSocket: (Socket, InetAddress, Int, Int) -> Unit = { _, _, _, _ -> },
    ) = PinnedHttpsHostTransport(
        createSocket = createSocket,
        connectSocket = connectSocket,
        tlsSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
        hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier(),
    )

    private fun assertNetworkError(error: HostNetworkError, block: suspend () -> Unit) {
        val failure = runCatching { runBlocking { block() } }.exceptionOrNull()
        assertTrue(failure is HostNetworkException)
        assertEquals(error, (failure as HostNetworkException).error)
    }

    private fun request(
        url: String = "https://assets.example/image.png",
        headers: Map<String, String> = mapOf("Accept" to "image/png"),
    ) = HostHttpRequest(
        url = URI(url),
        method = NetworkMethod.GET,
        headers = headers,
        decode = DecodeMode.AUTO,
        body = null,
        referrer = null,
        timeoutMs = 2_000,
        maxResponseBytes = 1_024,
    )
}
