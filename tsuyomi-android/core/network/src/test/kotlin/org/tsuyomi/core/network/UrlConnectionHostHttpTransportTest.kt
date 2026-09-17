/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import java.net.ServerSocket
import java.net.URI
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertThrows
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.NetworkMethod

class UrlConnectionHostHttpTransportTest {
    @Test
    fun preserves_each_repeated_response_header_from_the_platform_connection() = runBlocking {
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true) {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    while (input.readLine()?.isNotEmpty() == true) Unit
                    socket.getOutputStream().bufferedWriter().use { output ->
                        output.write(
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/html; charset=GB18030\r\n" +
                                "Set-Cookie: session=one; Path=/foo; Expires=Wed, 21 Oct 2037 07:28:00 GMT\r\n" +
                                "Set-Cookie: preference=light; Path=/foo\r\n" +
                                "Content-Length: 2\r\n" +
                                "Connection: close\r\n\r\nok",
                        )
                        output.flush()
                    }
                }
            }
            val response = UrlConnectionHostHttpTransport().execute(
                HostHttpRequest(
                    url = URI("http://127.0.0.1:${server.localPort}/foo/document"),
                    method = NetworkMethod.GET,
                    headers = emptyMap(),
                    decode = DecodeMode.AUTO,
                    body = null,
                    referrer = null,
                    timeoutMs = 5_000,
                    maxResponseBytes = 1_024,
                ),
            )
            worker.join(5_000)

            val cookies = mutableListOf<String>()
            response.headers.forEachValue("set-cookie", cookies::add)
            assertEquals("text/html; charset=GB18030", response.headers.first("content-type"))
            assertEquals(
                setOf(
                    "session=one; Path=/foo; Expires=Wed, 21 Oct 2037 07:28:00 GMT",
                    "preference=light; Path=/foo",
                ),
                cookies.toSet(),
            )
        }
    }
    @Test
    fun rejects_a_truncated_fixed_length_response_as_a_network_failure() {
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true) {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    while (input.readLine()?.isNotEmpty() == true) Unit
                    socket.getOutputStream().bufferedWriter().use { output ->
                        output.write(
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/html; charset=UTF-8\r\n" +
                                "Content-Length: 128\r\n" +
                                "Connection: close\r\n\r\n<html><body>partial",
                        )
                        output.flush()
                    }
                }
            }

            val error = assertThrows(HostNetworkException::class.java) {
                runBlocking {
                    UrlConnectionHostHttpTransport().execute(
                        HostHttpRequest(
                            url = URI("http://127.0.0.1:${server.localPort}/interrupted"),
                            method = NetworkMethod.GET,
                            headers = emptyMap(),
                            decode = DecodeMode.AUTO,
                            body = null,
                            referrer = null,
                            timeoutMs = 5_000,
                            maxResponseBytes = 1_024,
                        ),
                    )
                }
            }
            worker.join(5_000)

            assertEquals(HostNetworkError.OFFLINE, error.error)
        }
    }
}
