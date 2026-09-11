/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsRepositoryFetcherTest {
    @Test
    fun unexpectedHttpsHeaderDisconnectGetsOneFreshAttempt() = withLoopbackHttpsServer(
        listOf(
            response(disconnectBeforeHeaders = true),
            response(body = HEADER_RECOVERY_BYTES),
        ),
    ) { server ->
        val bytes = fetcher().fetch(server.url("/signed-index"), HEADER_RECOVERY_BYTES.size + 1)

        assertArrayEquals(HEADER_RECOVERY_BYTES, bytes)
        assertTrue(server.awaitRequests())
        val requests = server.requests()
        assertEquals(listOf("/signed-index", "/signed-index"), requests.map(RecordedRequest::target))
    }

    @Test
    fun truncatedHttpsBodyRestartsFromSignedUrlAndDiscardsPartialBytes() = withLoopbackHttpsServer(
        listOf(
            redirect("/archive"),
            response(body = STALE_PARTIAL_BYTES, contentLength = PACKAGE_BYTES.size),
            redirect("/archive"),
            response(body = PACKAGE_BYTES),
        ),
    ) { server ->
        val bytes = fetcher().fetch(server.url("/signed-index"), PACKAGE_BYTES.size + 1)

        assertArrayEquals(PACKAGE_BYTES, bytes)
        assertTrue(server.awaitRequests())
        val requests = server.requests()
        assertEquals(
            listOf("/signed-index", "/archive", "/signed-index", "/archive"),
            requests.map(RecordedRequest::target),
        )
    }

    @Test
    fun retryNeverResetsTheTotalDeadlineOrExceedsTwoAttempts() = withLoopbackHttpsServer(
        listOf(
            response(
                body = STALE_PARTIAL_BYTES,
                contentLength = PACKAGE_BYTES.size,
                holdOpenMs = 1_200,
            ),
            response(body = PACKAGE_BYTES, delayBeforeResponseMs = 1_200),
        ),
    ) { server ->
        val failure = assertThrows(RepositoryFetchException::class.java) {
            fetcher(totalTimeoutMs = 2_000).fetch(server.url("/signed-index"), PACKAGE_BYTES.size + 1)
        }

        assertEquals(RepositoryFetchError.TIMEOUT, failure.error)
        assertTrue(server.awaitRequests())
        assertEquals(2, server.requests().size)
    }

    @Test
    fun repeatedBodyTruncationStopsAfterTwoAttempts() = withLoopbackHttpsServer(
        List(2) { response(body = STALE_PARTIAL_BYTES, contentLength = PACKAGE_BYTES.size) },
    ) { server ->
        val failure = assertThrows(RepositoryFetchException::class.java) {
            fetcher().fetch(server.url("/signed-index"), PACKAGE_BYTES.size + 1)
        }

        assertEquals(RepositoryFetchError.NETWORK, failure.error)
        assertTrue(server.awaitRequests())
        assertEquals(2, server.requests().size)
    }

    @Test
    fun httpRedirectAndHostnameFailuresAreFailClosedWithoutRetry() {
        withLoopbackHttpsServer(listOf(response(status = "503 Service Unavailable"))) { server ->
            val failure = assertThrows(RepositoryFetchException::class.java) {
                fetcher().fetch(server.url("/signed-index"), PACKAGE_BYTES.size + 1)
            }

            assertEquals(RepositoryFetchError.HTTP_STATUS, failure.error)
            assertTrue(server.awaitRequests())
            assertEquals(1, server.requests().size)
        }
        withLoopbackHttpsServer(listOf(redirect("http://localhost/not-https"))) { server ->
            val failure = assertThrows(RepositoryFetchException::class.java) {
                fetcher().fetch(server.url("/signed-index"), PACKAGE_BYTES.size + 1)
            }

            assertEquals(RepositoryFetchError.INVALID_REDIRECT, failure.error)
            assertTrue(server.awaitRequests())
            assertEquals(1, server.requests().size)
        }
        withLoopbackHttpsServer(listOf(response(body = PACKAGE_BYTES))) { server ->
            val failure = assertThrows(RepositoryFetchException::class.java) {
                fetcher().fetch(server.url("/signed-index", hostname = "127.0.0.1"), PACKAGE_BYTES.size + 1)
            }

            assertEquals(RepositoryFetchError.NETWORK, failure.error)
            assertTrue(server.awaitConnections())
            assertEquals(1, server.acceptedConnections())
        }
    }

    private fun fetcher(
        totalTimeoutMs: Int = 5_000,
        readTimeoutMs: Int = 2_000,
    ): HttpsRepositoryFetcher = HttpsRepositoryFetcher(
        connectTimeoutMs = 1_000,
        readTimeoutMs = readTimeoutMs,
        totalTimeoutMs = totalTimeoutMs,
    )

    private fun <T> withLoopbackHttpsServer(
        responses: List<ScriptedResponse>,
        block: (LoopbackHttpsServer) -> T,
    ): T = synchronized(HTTPS_DEFAULTS_LOCK) {
        val tls = loopbackTls()
        LoopbackHttpsServer(tls.serverContext, responses).use { server ->
            val previousSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
            HttpsURLConnection.setDefaultSSLSocketFactory(tls.clientContext.socketFactory)
            try {
                block(server)
            } finally {
                HttpsURLConnection.setDefaultSSLSocketFactory(previousSocketFactory)
            }
        }
    }

    private fun loopbackTls(): LoopbackTls {
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(ByteArrayInputStream(Base64.getMimeDecoder().decode(LOOPBACK_KEYSTORE)), KEYSTORE_PASSWORD)
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, KEYSTORE_PASSWORD)
        }.keyManagers
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry(LOOPBACK_KEYSTORE_ALIAS, keyStore.getCertificate(LOOPBACK_KEYSTORE_ALIAS))
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }.trustManagers
        return LoopbackTls(
            serverContext = SSLContext.getInstance("TLS").apply { init(keyManagers, null, null) },
            clientContext = SSLContext.getInstance("TLS").apply { init(null, trustManagers, null) },
        )
    }

    private fun response(
        status: String = "200 OK",
        body: ByteArray = byteArrayOf(),
        contentLength: Int = body.size,
        headers: Map<String, String> = emptyMap(),
        disconnectBeforeHeaders: Boolean = false,
        delayBeforeResponseMs: Long = 0,
        holdOpenMs: Long = 0,
    ): ScriptedResponse = ScriptedResponse(
        status = status,
        body = body,
        contentLength = contentLength,
        headers = headers,
        disconnectBeforeHeaders = disconnectBeforeHeaders,
        delayBeforeResponseMs = delayBeforeResponseMs,
        holdOpenMs = holdOpenMs,
    )

    private fun redirect(location: String): ScriptedResponse = response(
        status = "302 Found",
        headers = mapOf("Location" to location),
    )

    private data class LoopbackTls(val serverContext: SSLContext, val clientContext: SSLContext)

    private data class ScriptedResponse(
        val status: String,
        val body: ByteArray,
        val contentLength: Int,
        val headers: Map<String, String>,
        val disconnectBeforeHeaders: Boolean,
        val delayBeforeResponseMs: Long,
        val holdOpenMs: Long,
    )

    private data class RecordedRequest(val target: String, val headers: Map<String, String>)

    private class LoopbackHttpsServer(
        serverContext: SSLContext,
        private val responses: List<ScriptedResponse>,
    ) : AutoCloseable {
        private val closing = AtomicBoolean(false)
        private val accepted = CountDownLatch(responses.size)
        private val receivedRequests = CountDownLatch(responses.size)
        private val requestList = Collections.synchronizedList(mutableListOf<RecordedRequest>())
        private val connectionCount = AtomicInteger()
        private val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "TsuyomiRepositoryFetcherLoopbackTls").apply { isDaemon = true }
        }
        private val serverSocket = (serverContext.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("localhost"), 0))
        }

        @Volatile
        private var activeSocket: SSLSocket? = null

        init {
            worker.execute(::serveResponses)
        }

        fun url(path: String, hostname: String = "localhost"): String =
            "https://" + hostname + ":" + serverSocket.localPort + path

        fun awaitConnections(): Boolean = accepted.await(SERVER_WAIT_SECONDS, TimeUnit.SECONDS)

        fun awaitRequests(): Boolean = receivedRequests.await(SERVER_WAIT_SECONDS, TimeUnit.SECONDS)

        fun acceptedConnections(): Int = connectionCount.get()

        fun requests(): List<RecordedRequest> = synchronized(requestList) { requestList.toList() }

        private fun serveResponses() {
            for (response in responses) {
                val socket = try {
                    serverSocket.accept() as SSLSocket
                } catch (error: IOException) {
                    return
                }
                connectionCount.incrementAndGet()
                accepted.countDown()
                activeSocket = socket
                try {
                    socket.use { connection ->
                        val request = readRequest(connection)
                        requestList += request
                        receivedRequests.countDown()
                        if (!response.disconnectBeforeHeaders) writeResponse(connection, response)
                    }
                } catch (error: IOException) {
                    if (closing.get()) return
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                } finally {
                    activeSocket = null
                }
            }
        }

        private fun readRequest(socket: SSLSocket): RecordedRequest {
            val input = BufferedReader(InputStreamReader(socket.inputStream, StandardCharsets.US_ASCII))
            val requestLine = input.readLine() ?: throw IOException("Missing loopback request line")
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: throw IOException("Missing loopback request terminator")
                if (line.isEmpty()) break
                val delimiter = line.indexOf(':')
                if (delimiter > 0) {
                    headers[line.substring(0, delimiter).lowercase(Locale.ROOT)] = line.substring(delimiter + 1).trim()
                }
            }
            return RecordedRequest(
                target = requestLine.substringAfter(' ').substringBefore(' '),
                headers = headers,
            )
        }

        private fun writeResponse(socket: SSLSocket, response: ScriptedResponse) {
            if (response.delayBeforeResponseMs > 0) Thread.sleep(response.delayBeforeResponseMs)
            val headers = linkedMapOf(
                "Content-Length" to response.contentLength.toString(),
                "Connection" to "close",
            ).apply { putAll(response.headers) }
            socket.outputStream.use { output ->
                output.write(
                    buildString {
                        append("HTTP/1.1 ")
                        append(response.status)
                        append("\r\n")
                        headers.forEach { (name, value) ->
                            append(name)
                            append(": ")
                            append(value)
                            append("\r\n")
                        }
                        append("\r\n")
                    }.toByteArray(StandardCharsets.US_ASCII),
                )
                output.write(response.body)
                output.flush()
                if (response.holdOpenMs > 0) Thread.sleep(response.holdOpenMs)
            }
        }

        override fun close() {
            closing.set(true)
            activeSocket?.close()
            serverSocket.close()
            worker.shutdownNow()
            try {
                worker.awaitTermination(SERVER_WAIT_SECONDS, TimeUnit.SECONDS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private companion object {
        val HEADER_RECOVERY_BYTES = "header-recovered".toByteArray(StandardCharsets.US_ASCII)
        val STALE_PARTIAL_BYTES = "stale".toByteArray(StandardCharsets.US_ASCII)
        val PACKAGE_BYTES = "replacement-package".toByteArray(StandardCharsets.US_ASCII)
        val HTTPS_DEFAULTS_LOCK = Any()
        val KEYSTORE_PASSWORD = "changeit".toCharArray()
        const val LOOPBACK_KEYSTORE_ALIAS = "loopback"
        const val SERVER_WAIT_SECONDS = 3L
        // Public localhost-only TLS test material; never a production trust anchor or secret.
        val LOOPBACK_KEYSTORE = """
MIIEfQIBAzCCBDMGCSqGSIb3DQEHAaCCBCQEggQgMIIEHDCCAqoGCSqGSIb3DQEHBqCCApswggKX
AgEAMIICkAYJKoZIhvcNAQcBMF8GCSqGSIb3DQEFDTBSMDEGCSqGSIb3DQEFDDAkBBDhf3MwmnNC
NO22NMvIhnTYAgIIADAMBggqhkiG9w0CCQUAMB0GCWCGSAFlAwQBKgQQmcuk3CKiSNMrCS6wG9T7
VoCCAiAVcJVgROKrJ+kBGjEGFvYYKaeeVOoS+tFjUVT69IeYPZRdpt2j0H02QGk3EV0/0C9GWhFW
WzvaRz5QZlRB32KpmubxteXf9gle/hyUT0NMhzKx25ChHTHVxCXkWDg5ZIOFOlcj+MPwmyuWk6qa
vik6OgPOsrstiKq+ajkBNGkVUKrFz8KDeRtWpT5JvR6JkkORKskvpipGWhqJi/Wr+9LnYj3HLfJy
/QNOJjy6PxRcLaE5DVxNeVni8Jv474D+D37VjwfJI8GJgpKqmAdd7udmttr2+k2Mm8fJeVLiWCpL
Poc6Eng8PzGd9krEftGOZ9wL3OfknwC36M5N8cQGBSkbrf0aGjPijnzDS3CglUQtUTcPKxnUUjC/
08W0Lv7NzS2LR7lOrA5aWZ77vVD4Y69Weudzfa1QiTmBs1eeAz1GY06C8s4IBMjubVfiaEnpPuGu
NbKjm43S8YxTzVGnSDiC1VP5Uz2DpL8hrfPXdLdBKRYXTaG1jmgyKEiJvHtbuIMvYm3i46oeSXmW
8wGdpxcX7n8n73qVOS9w8dTXOrPaVEcUd0cOa5/i/GvSjDQltbUNahSg/ez72cJYKaFEwXQvB+2T
BEJXWAFe6YP7w/I+2VyMnI1x2KBJM9Px+gDXm/EB6oGxPIdcT50L7vzi3rvq4UXU8nmeYyR5NnBX
XPPd7aeRAHFlFVmKYsd/GYKsSC0NCfNuPBnaUWp8IZIWaZgYMIIBagYJKoZIhvcNAQcBoIIBWwSC
AVcwggFTMIIBTwYLKoZIhvcNAQwKAQKggfcwgfQwXwYJKoZIhvcNAQUNMFIwMQYJKoZIhvcNAQUM
MCQEEMw+qr1zfdf+Skz5RFcp+PQCAggAMAwGCCqGSIb3DQIJBQAwHQYJYIZIAWUDBAEqBBCa1MDJ
3APeEVKwIWTQmXdrBIGQPVyv73LrvhD/BvBLS/o9jKBdJojtl0OJRnMIvoBv0g+A8J+oisrqjo/F
Ec/GnWXFCTwtbe4nTYo4ohIokPTMgS/bDa3crQX2FENwOmFvplx4UWS7ErvFpjl7CnvkR8L/KBiC
Mk5DT4+aS5wbxC2zhHLYNRFBveXc8dVow/fMdZaP6STO95e7+E5ya5Y2YfAtMUYwHwYJKoZIhvcN
AQkUMRIeEABsAG8AbwBwAGIAYQBjAGswIwYJKoZIhvcNAQkVMRYEFNZJUfYf1kNuQHXV8qBjMMgY
4CtUMEEwMTANBglghkgBZQMEAgEFAAQgtRrQmGOfiAZE96LVEp/h1g9vZGg797Q0YtYl8u6lHCkE
CBVGFHYemujoAgIIAA==
""".trimIndent()
    }
}
