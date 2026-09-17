/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.network.HostHttpRequest
import org.tsuyomi.core.network.HostHttpResponse
import org.tsuyomi.core.network.HostHttpTransport
import org.tsuyomi.core.network.HostNetworkGateway
import org.tsuyomi.core.network.HostResponseHeaders
import org.tsuyomi.core.network.SourceNetworkGrant
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkCacheMode
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.shared.sourcecontract.SourceCookieMode
import org.tsuyomi.shared.sourcecontract.SourceNetworkRequest

/** Runs charset and HttpCookie behavior on Android's implementation, not the host JDK. */
@RunWith(AndroidJUnit4::class)
class HostResponseBoundaryInstrumentedTest {
    @Test
    fun mixedCaseCharsetAndRepeatedCookiesRemainScopedToTheSourceAndLegalPath() = runBlocking {
        val origin = HttpsOrigin("https://reader-fixture.example")
        val grant = SourceNetworkGrant(
            sourceId = "fixture.android.headers",
            extensionVersion = "1.0.0",
            origins = setOf(origin),
            cookieMode = SourceCookieMode.SOURCE_SCOPED,
            cookieOrigins = setOf(origin),
            maxConcurrentRequests = 1,
            requestTimeoutMs = 5_000,
            maxResponseBytes = 1_024,
        )
        val requests = mutableListOf<HostHttpRequest>()
        val gateway = HostNetworkGateway(HostHttpTransport { request ->
            requests += request
            HostHttpResponse(
                status = 200,
                finalUrl = request.url,
                headers = if (requests.size == 1) HostResponseHeaders.of(
                    "Content-Type" to "text/html; charset=GB18030",
                    "Set-Cookie" to "session=one; Path=/foo; Expires=Wed, 21 Oct 2037 07:28:00 GMT",
                    "Set-Cookie" to "preference=light",
                ) else HostResponseHeaders.of("Content-Type" to "text/html; charset=GB18030"),
                bytes = "雾港".toByteArray(Charset.forName("GB18030")),
            )
        })
        fun request(path: String) = SourceNetworkRequest(
            url = "${origin.canonical}$path",
            method = NetworkMethod.GET,
            decode = DecodeMode.AUTO,
            cache = NetworkCacheMode.NETWORK_ONLY,
        )

        val first = gateway.request(grant, request("/foo/start"))
        assertEquals("雾港", first.text)
        assertNull(first.headers["set-cookie"])
        gateway.request(grant.copy(extensionVersion = "1.0.1"), request("/foo/next"))
        gateway.request(grant, request("/foobar"))
        gateway.request(grant.copy(sourceId = "fixture.android.other"), request("/foo/next"))

        assertEquals(
            setOf("session=one", "preference=light"),
            requests[1].headers["cookie"]?.split(';')?.map(String::trim)?.toSet(),
        )
        assertNull(requests[2].headers["cookie"])
        assertNull(requests[3].headers["cookie"])
    }
}
