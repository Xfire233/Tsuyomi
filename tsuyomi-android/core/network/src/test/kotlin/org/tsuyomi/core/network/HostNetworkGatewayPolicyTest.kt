/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkCacheMode
import org.tsuyomi.shared.sourcecontract.NetworkCacheState
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.shared.sourcecontract.SourceNetworkRequest
import org.tsuyomi.shared.sourcecontract.SourceCookieMode

class HostNetworkGatewayPolicyTest {
    @Test
    fun foreground_and_background_gateways_share_a_source_lane_across_versions() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        fun transport(name: String, blocked: Boolean = false) = HostHttpTransport { received ->
            calls += name
            if (blocked) release.await()
            HostHttpResponse(200, received.url, responseHeaders(), "fixture".encodeToByteArray())
        }
        val foreground = async(start = CoroutineStart.UNDISPATCHED) {
            HostNetworkGateway(transport("foreground", blocked = true)).request(grant, request())
        }
        val background = async(start = CoroutineStart.UNDISPATCHED) {
            HostNetworkGateway(transport("background"))
                .request(grant.copy(extensionVersion = "0.1.1"), request())
        }
        try {
            HostNetworkGateway(transport("other-source"))
                .request(grant.copy(sourceId = "fixture.independent.source"), request())
            assertEquals(listOf("foreground", "other-source"), calls)
        } finally {
            release.complete(Unit)
            foreground.await()
            background.await()
        }
        assertEquals(listOf("foreground", "other-source", "background"), calls)
    }

    @Test
    fun disallowed_origin_and_protected_headers_never_reach_transport() = runBlocking {
        val transport = RecordingTransport()
        val gateway = HostNetworkGateway(transport)

        val originFailure = assertHostFailure {
            gateway.request(grant, request(url = "https://outside.example/chapter"))
        }
        assertEquals(HostNetworkError.DISALLOWED_ORIGIN, originFailure.error)
        val headerFailure = assertHostFailure {
            gateway.request(grant, request(headers = mapOf("Cookie" to "secret=session")))
        }
        assertEquals(HostNetworkError.HEADER_DISALLOWED, headerFailure.error)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun cache_is_shared_across_extension_versions_and_offline_returns_stale_marker() = runBlocking {
        val transport = RecordingTransport()
        val gateway = HostNetworkGateway(transport)
        val request = request(cache = NetworkCacheMode.DEFAULT, semanticCacheKey = "detail:1234")

        assertEquals(NetworkCacheState.MISS, gateway.request(grant, request).cacheState)
        assertEquals(NetworkCacheState.FRESH, gateway.request(grant, request).cacheState)
        assertEquals(1, transport.requests.size)

        val offline = gateway.request(grant, request.copy(cache = NetworkCacheMode.OFFLINE_ONLY))
        assertEquals(NetworkCacheState.STALE_OFFLINE, offline.cacheState)
        assertEquals(1, transport.requests.size)

        val updatedGrant = grant.copy(extensionVersion = "0.1.1")
        assertEquals(NetworkCacheState.FRESH, gateway.request(updatedGrant, request).cacheState)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun validate_mode_never_admits_raw_response_before_caller_validation() = runBlocking {
        val transport = RecordingTransport()
        val gateway = HostNetworkGateway(transport)
        val validate = request(cache = NetworkCacheMode.VALIDATE, semanticCacheKey = "detail:1234")

        assertEquals(NetworkCacheState.VALIDATED, gateway.request(grant, validate).cacheState)
        val offlineFailure = assertHostFailure {
            gateway.request(grant, validate.copy(cache = NetworkCacheMode.OFFLINE_ONLY))
        }

        assertEquals(HostNetworkError.OFFLINE_MISS, offlineFailure.error)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun redirect_to_an_undeclared_origin_is_rejected_before_following_it() = runBlocking {
        val transport = HostHttpTransport { request ->
            HostHttpResponse(status = 302, finalUrl = request.url, headers = responseHeaders("location" to "https://outside.example/redirected"), bytes = byteArrayOf())
        }

        val failure = assertHostFailure { HostNetworkGateway(transport).request(grant, request()) }

        assertEquals(HostNetworkError.REDIRECT_DISALLOWED, failure.error)
    }

    @Test
    fun host_managed_cookies_are_hidden_and_shared_across_source_versions() = runBlocking {
        val requests = mutableListOf<HostHttpRequest>()
        val gateway = HostNetworkGateway(HostHttpTransport { received ->
            requests += received
            HostHttpResponse(status = 200, finalUrl = received.url, headers = responseHeaders("set-cookie" to "session=opaque; Path=/; Secure"), bytes = "fixture".encodeToByteArray())
        })

        val first = gateway.request(grant, request())
        gateway.request(grant, request())
        gateway.request(grant.copy(extensionVersion = "0.1.1"), request())

        assertEquals(null, first.headers["set-cookie"])
        assertEquals("session=opaque", requests[1].headers["cookie"])
        assertEquals("session=opaque", requests[2].headers["cookie"])
    }

    @Test
    fun cookie_none_drops_server_set_cookie() = runBlocking {
        val requests = mutableListOf<HostHttpRequest>()
        val gateway = HostNetworkGateway(HostHttpTransport { received ->
            requests += received
            HostHttpResponse(status = 200, finalUrl = received.url, headers = responseHeaders("set-cookie" to "server=unapproved; Path=/; Secure"), bytes = "fixture".encodeToByteArray())
        })
        val noCookieGrant = grant.copy(cookieMode = SourceCookieMode.NONE, cookieOrigins = emptySet())
        val importFailure = runCatching {
            gateway.importSourceCookies(noCookieGrant, HttpsOrigin("https://www.wenku8.net"), "handoff=unapproved")
        }.exceptionOrNull()
        assertTrue(importFailure is IllegalArgumentException)

        gateway.request(noCookieGrant, request())
        gateway.request(noCookieGrant, request())

        assertEquals(null, requests[1].headers["cookie"])
    }

    @Test
    fun source_scoped_cookies_reject_other_origins_and_preserve_allowed_handoff() = runBlocking {
        val wwwOrigin = HttpsOrigin("https://www.wenku8.net")
        val apiOrigin = HttpsOrigin("https://api.wenku8.net")
        val scopedGrant = grant.copy(
            origins = setOf(wwwOrigin, apiOrigin),
            cookieOrigins = setOf(wwwOrigin),
        )
        val requests = mutableListOf<HostHttpRequest>()
        val gateway = HostNetworkGateway(HostHttpTransport { received ->
            requests += received
            HostHttpResponse(status = 200, finalUrl = received.url, headers = responseHeaders("set-cookie" to "server=approved; Path=/; Secure"), bytes = "fixture".encodeToByteArray())
        })

        gateway.importSourceCookies(scopedGrant, wwwOrigin, "handoff=approved")
        val importFailure = runCatching {
            gateway.importSourceCookies(scopedGrant, apiOrigin, "handoff=unapproved")
        }.exceptionOrNull()
        assertTrue(importFailure is IllegalArgumentException)

        gateway.request(scopedGrant, request(url = "https://www.wenku8.net/search"))
        gateway.request(scopedGrant, request(url = "https://www.wenku8.net/detail"))
        gateway.request(scopedGrant, request(url = "https://api.wenku8.net/search"))
        gateway.request(scopedGrant, request(url = "https://api.wenku8.net/detail"))

        assertEquals("handoff=approved", requests[0].headers["cookie"])
        assertEquals("handoff=approved; server=approved", requests[1].headers["cookie"])
        assertEquals(null, requests[3].headers["cookie"])
    }

    @Test
    fun auto_decode_uses_mixed_case_content_type_without_exposing_cookies() = runBlocking {
        val encoded = "雾港".toByteArray(Charset.forName("GB18030"))
        val gateway = HostNetworkGateway(HostHttpTransport { received ->
            HostHttpResponse(
                status = 200,
                finalUrl = received.url,
                headers = responseHeaders(
                    "cOnTeNt-TyPe" to "text/html; charset=GB18030",
                    "SeT-CoOkIe" to "session=opaque; Path=/; Secure",
                ),
                bytes = encoded,
            )
        })

        val response = gateway.request(grant, request(decode = DecodeMode.AUTO))

        assertEquals("雾港", response.text)
        assertEquals("text/html; charset=GB18030", response.headers["content-type"])
        assertEquals(null, response.headers["set-cookie"])
    }

    @Test
    fun separate_set_cookie_headers_preserve_expires_updates_and_path_boundaries() = runBlocking {
        val requests = mutableListOf<HostHttpRequest>()
        var responseCount = 0
        val gateway = HostNetworkGateway(HostHttpTransport { received ->
            requests += received
            val headers = when (responseCount++) {
                0 -> responseHeaders(
                    "SET-Cookie" to "session=stale; Path=/foo; Expires=Wed, 21 Oct 2037 07:28:00 GMT; Secure",
                    "Set-Cookie" to "preference=light; Path=/foo; Secure",
                )
                1 -> responseHeaders(
                    "Set-Cookie" to "session=fresh; Path=/foo; Secure",
                    "Set-Cookie" to "preference=removed; Path=/foo; Max-Age=0; Secure",
                )
                else -> responseHeaders()
            }
            HostHttpResponse(200, received.url, headers, "fixture".encodeToByteArray())
        })

        gateway.request(grant, request(url = "https://www.wenku8.net/foo/first"))
        gateway.request(grant, request(url = "https://www.wenku8.net/foo/second"))
        gateway.request(grant, request(url = "https://www.wenku8.net/foo/third"))
        gateway.request(grant, request(url = "https://www.wenku8.net/foobar"))

        assertEquals("session=stale; preference=light", requests[1].headers["cookie"])
        assertEquals("session=fresh", requests[2].headers["cookie"])
        assertEquals(null, requests[3].headers["cookie"])
    }

    @Test
    fun transport_cancellation_is_not_mapped_to_a_network_failure() = runBlocking {
        val gateway = HostNetworkGateway(HostHttpTransport {
            throw CancellationException("test cancellation")
        })

        val failure = runCatching { gateway.request(grant, request()) }.exceptionOrNull()

        assertTrue(failure is CancellationException)
    }

    @Test
    fun response_limit_and_legacy_decoder_are_host_enforced() = runBlocking {
        val oversized = HostNetworkGateway(
            HostHttpTransport {
                HostHttpResponse(200, URI("https://www.wenku8.net/book/1234.htm"), responseHeaders(), ByteArray(1_025))
            },
        )
        val limitFailure = assertHostFailure { oversized.request(grant, request()) }
        assertEquals(HostNetworkError.RESPONSE_LIMIT, limitFailure.error)

        val gb18030 = "雾港".toByteArray(Charset.forName("GB18030"))
        val legacy = HostNetworkGateway(
            HostHttpTransport {
                HostHttpResponse(200, URI("https://www.wenku8.net/book/1234.htm"), responseHeaders(), gb18030)
            },
        )
        assertEquals("雾港", legacy.request(grant, request(decode = DecodeMode.GB18030)).text)
    }

    @Test
    fun post_is_never_cached_and_body_is_hard_bounded() = runBlocking {
        val transport = RecordingTransport()
        val gateway = HostNetworkGateway(transport)
        val post = SourceNetworkRequest(
            url = "https://www.wenku8.net/login",
            method = NetworkMethod.POST,
            utf8Body = "a=1",
            decode = DecodeMode.UTF8,
            cache = NetworkCacheMode.NETWORK_ONLY,
        )
        gateway.request(grant, post)
        gateway.request(grant, post)
        assertEquals(2, transport.requests.size)
        val bodyFailure = assertHostFailure { gateway.request(grant, post.copy(utf8Body = "x".repeat(65 * 1024))) }
        assertEquals(HostNetworkError.BODY_LIMIT, bodyFailure.error)
    }

    @Test
    fun network_only_is_not_last_good_until_remembered() = runBlocking {
        val transport = RecordingTransport()
        val gateway = HostNetworkGateway(transport)
        val live = request(cache = NetworkCacheMode.NETWORK_ONLY, semanticCacheKey = "home:ranking")
        val challenge = gateway.request(grant, live)
        val miss = assertHostFailure {
            gateway.request(grant, live.copy(cache = NetworkCacheMode.OFFLINE_ONLY))
        }
        assertEquals(HostNetworkError.OFFLINE_MISS, miss.error)
        gateway.rememberLastGood(grant, live, challenge)
        val offline = gateway.request(grant.copy(extensionVersion = "0.2.29"), live.copy(cache = NetworkCacheMode.OFFLINE_ONLY))
        assertEquals("fixture", offline.text)
        assertEquals(NetworkCacheState.STALE_OFFLINE, offline.cacheState)
        gateway.forgetLastGood(grant, live)
        val forgotten = assertHostFailure {
            gateway.request(grant, live.copy(cache = NetworkCacheMode.OFFLINE_ONLY))
        }
        assertEquals(HostNetworkError.OFFLINE_MISS, forgotten.error)
        assertEquals(1, transport.requests.size)
    }


    @Test
    fun media_uses_only_granted_origin_referrer_and_source_scoped_cookie() = runBlocking {
        val sourceOrigin = HttpsOrigin("https://www.wenku8.net")
        val coverOrigin = HttpsOrigin("https://pic.wenku8.com")
        val mediaGrant = grant.copy(origins = setOf(sourceOrigin, coverOrigin), cookieOrigins = setOf(sourceOrigin))
        val requests = mutableListOf<HostHttpRequest>()
        val gateway = HostNetworkGateway(HostHttpTransport { request ->
            requests += request
            HostHttpResponse(200, request.url, responseHeaders("content-type" to "image/jpeg"), byteArrayOf(1, 2, 3))
        })
        gateway.importSourceCookies(mediaGrant, sourceOrigin, "session=verified")

        val response = gateway.fetchMedia(
            grant = mediaGrant,
            url = "https://pic.wenku8.com/files/article/image/12/1234/1234.jpg",
            referrerUrl = "https://www.wenku8.net/book/1234.htm",
        )

        assertEquals("image/jpeg", response.contentType)
        assertEquals(3, response.bytes.size)
        assertEquals(URI("https://www.wenku8.net/book/1234.htm"), requests.single().referrer)
        assertEquals(null, requests.single().headers["cookie"])
    }

}
