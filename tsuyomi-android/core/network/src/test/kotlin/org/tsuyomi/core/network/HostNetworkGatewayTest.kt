/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import java.net.URI
import java.nio.charset.Charset
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

class HostNetworkGatewayTest {
    private val addPolicy = RemoteOperationRequestPolicy(
        origin = HttpsOrigin("https://www.wenku8.net"),
        method = NetworkMethod.POST,
        path = "/remote/shelf",
        fixedParameters = mapOf("mode" to "add"),
        remoteBookIdParameter = "bid",
    )
    private val grant = SourceNetworkGrant(
        sourceId = "org.tsuyomi.wenku8",
        extensionVersion = "0.1.0",
        origins = setOf(HttpsOrigin("https://www.wenku8.net")),
        cookieMode = SourceCookieMode.SOURCE_SCOPED,
        cookieOrigins = setOf(HttpsOrigin("https://www.wenku8.net")),
        maxConcurrentRequests = 2,
        requestTimeoutMs = 15_000,
        maxResponseBytes = 1_024,
        remoteAddPolicy = addPolicy,
    )

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
    fun cache_is_namespaced_by_extension_version_and_offline_returns_stale_marker() = runBlocking {
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
        assertEquals(NetworkCacheState.MISS, gateway.request(updatedGrant, request).cacheState)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun redirect_to_an_undeclared_origin_is_rejected_before_following_it() = runBlocking {
        val transport = HostHttpTransport { request ->
            HostHttpResponse(
                status = 302,
                finalUrl = request.url,
                headers = mapOf("location" to "https://outside.example/redirected"),
                bytes = byteArrayOf(),
            )
        }

        val failure = assertHostFailure { HostNetworkGateway(transport).request(grant, request()) }

        assertEquals(HostNetworkError.REDIRECT_DISALLOWED, failure.error)
    }

    @Test
    fun host_managed_cookies_are_hidden_and_isolated_by_source_version() = runBlocking {
        val requests = mutableListOf<HostHttpRequest>()
        val gateway = HostNetworkGateway(HostHttpTransport { received ->
            requests += received
            HostHttpResponse(
                status = 200,
                finalUrl = received.url,
                headers = mapOf("set-cookie" to "session=opaque; Path=/; Secure"),
                bytes = "fixture".encodeToByteArray(),
            )
        })

        val first = gateway.request(grant, request())
        gateway.request(grant, request())
        gateway.request(grant.copy(extensionVersion = "0.1.1"), request())

        assertEquals(null, first.headers["set-cookie"])
        assertEquals("session=opaque", requests[1].headers["cookie"])
        assertEquals(null, requests[2].headers["cookie"])
    }

    @Test
    fun cookie_none_drops_server_set_cookie() = runBlocking {
        val requests = mutableListOf<HostHttpRequest>()
        val gateway = HostNetworkGateway(HostHttpTransport { received ->
            requests += received
            HostHttpResponse(
                status = 200,
                finalUrl = received.url,
                headers = mapOf("set-cookie" to "server=unapproved; Path=/; Secure"),
                bytes = "fixture".encodeToByteArray(),
            )
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
            HostHttpResponse(
                status = 200,
                finalUrl = received.url,
                headers = mapOf("set-cookie" to "server=approved; Path=/; Secure"),
                bytes = "fixture".encodeToByteArray(),
            )
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
    fun response_limit_and_legacy_decoder_are_host_enforced() = runBlocking {
        val oversized = HostNetworkGateway(
            HostHttpTransport {
                HostHttpResponse(200, URI("https://www.wenku8.net/book/1234.htm"), emptyMap(), ByteArray(1_025))
            },
        )
        val limitFailure = assertHostFailure { oversized.request(grant, request()) }
        assertEquals(HostNetworkError.RESPONSE_LIMIT, limitFailure.error)

        val gb18030 = "雾港".toByteArray(Charset.forName("GB18030"))
        val legacy = HostNetworkGateway(
            HostHttpTransport {
                HostHttpResponse(200, URI("https://www.wenku8.net/book/1234.htm"), emptyMap(), gb18030)
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
    fun remote_operation_context_rejects_altered_literals_before_transport() = runBlocking {
        val transport = RecordingTransport()
        val context = remoteLibraryReadContext(
            RemoteOperationRequestPolicy(
                origin = HttpsOrigin("https://www.wenku8.net"),
                method = NetworkMethod.GET,
                path = "/remote/shelf",
                fixedParameters = mapOf("mode" to "list"),
                cursorParameter = "cursor",
            ),
            cursor = null,
        )
        val gateway = HostNetworkGateway(transport)
        val failure = assertHostFailure {
            gateway.request(grant, request(url = "https://www.wenku8.net/remote/shelf?mode=add"), context)
        }

        assertEquals(HostNetworkError.INVALID_REQUEST, failure.error)
        assertEquals(0, transport.requests.size)
        gateway.request(grant, request(url = "https://www.wenku8.net/remote/shelf?mode=list"), context)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun signed_context_follows_only_declared_success_redirect() = runBlocking {
        val requests = mutableListOf<HostHttpRequest>()
        val transport = HostHttpTransport { received ->
            requests += received
            if (requests.size == 1) {
                HostHttpResponse(302, received.url, mapOf("location" to "/remote/complete?status=ok"), byteArrayOf())
            } else {
                HostHttpResponse(200, received.url, emptyMap(), "ok".encodeToByteArray())
            }
        }
        val policy = RemoteOperationRequestPolicy(
            origin = HttpsOrigin("https://www.wenku8.net"),
            method = NetworkMethod.GET,
            path = "/remote/shelf",
            fixedParameters = mapOf("mode" to "list"),
            redirects = listOf(
                RemoteOperationRedirectPolicy(
                    origin = HttpsOrigin("https://www.wenku8.net"),
                    method = NetworkMethod.GET,
                    path = "/remote/complete",
                    fixedParameters = mapOf("status" to "ok"),
                ),
            ),
        )

        val result = HostNetworkGateway(transport).request(
            grant,
            request(url = "https://www.wenku8.net/remote/shelf?mode=list"),
            remoteLibraryReadContext(policy, cursor = null),
        )

        assertEquals("ok", result.text)
        assertEquals(listOf("/remote/shelf", "/remote/complete"), requests.map { it.url.path })
        assertEquals(NetworkMethod.GET, requests.last().method)
    }

    @Test
    fun signed_context_rejects_undeclared_success_redirect() = runBlocking {
        val transport = HostHttpTransport { received ->
            HostHttpResponse(302, received.url, mapOf("location" to "/remote/complete?status=ok"), byteArrayOf())
        }
        val policy = RemoteOperationRequestPolicy(
            origin = HttpsOrigin("https://www.wenku8.net"),
            method = NetworkMethod.GET,
            path = "/remote/shelf",
            fixedParameters = mapOf("mode" to "list"),
        )

        val failure = assertHostFailure {
            HostNetworkGateway(transport).request(
                grant,
                request(url = "https://www.wenku8.net/remote/shelf?mode=list"),
                remoteLibraryReadContext(policy, cursor = null),
            )
        }

        assertEquals(HostNetworkError.REDIRECT_DISALLOWED, failure.error)
    }

    @Test
    fun signed_add_can_change_post_to_declared_get_success_target() = runBlocking {
        val requests = mutableListOf<HostHttpRequest>()
        val transport = HostHttpTransport { received ->
            requests += received
            if (requests.size == 1) {
                HostHttpResponse(302, received.url, mapOf("location" to "/remote/complete?status=added"), byteArrayOf())
            } else {
                HostHttpResponse(200, received.url, emptyMap(), "confirmed".encodeToByteArray())
            }
        }
        val registry = DirectActionTokenRegistry()
        val policy = RemoteOperationRequestPolicy(
            origin = HttpsOrigin("https://www.wenku8.net"),
            method = NetworkMethod.POST,
            path = "/remote/shelf",
            fixedParameters = mapOf("mode" to "add"),
            remoteBookIdParameter = "bid",
            redirects = listOf(
                RemoteOperationRedirectPolicy(
                    origin = HttpsOrigin("https://www.wenku8.net"),
                    method = NetworkMethod.GET,
                    path = "/remote/complete",
                    fixedParameters = mapOf("status" to "added"),
                ),
            ),
        )
        val token = registry.mint(
            DirectActionBinding("org.tsuyomi.wenku8", "42", "reconcile", "digest", "0.2.0", "capability", 7, 9),
        ) { true }
        val addRequest = SourceNetworkRequest(
            url = "https://www.wenku8.net/remote/shelf",
            method = NetworkMethod.POST,
            form = mapOf("mode" to "add", "bid" to "42"),
            cache = NetworkCacheMode.NETWORK_ONLY,
        )

        val response = HostNetworkGateway(transport, directActionTokens = registry).request(
            grant.copy(remoteAddPolicy = policy),
            addRequest,
            remoteLibraryAddContext(policy, "42", token),
        )

        assertEquals("confirmed", response.text)
        assertEquals(listOf(NetworkMethod.POST, NetworkMethod.GET), requests.map { it.method })
        assertEquals(null, requests.last().body)
        val genericTransport = RecordingTransport()
        val genericFailure = assertHostFailure {
            HostNetworkGateway(genericTransport).request(
                grant.copy(remoteAddPolicy = policy),
                request(url = "https://www.wenku8.net/remote/complete?status=added"),
            )
        }
        assertEquals(HostNetworkError.INVALID_REQUEST, genericFailure.error)
        assertEquals(0, genericTransport.requests.size)
        val readContextFailure = assertHostFailure {
            HostNetworkGateway(genericTransport).request(
                grant.copy(remoteAddPolicy = policy),
                request(url = "https://www.wenku8.net/remote/complete?status=added"),
                remoteLibraryReadContext(
                    RemoteOperationRequestPolicy(
                        origin = HttpsOrigin("https://www.wenku8.net"),
                        method = NetworkMethod.GET,
                        path = "/remote/complete",
                        fixedParameters = mapOf("status" to "added"),
                    ),
                    cursor = null,
                ),
            )
        }
        assertEquals(HostNetworkError.INVALID_REQUEST, readContextFailure.error)
        assertEquals(0, genericTransport.requests.size)
    }

    @Test
    fun generic_redirect_cannot_enter_a_signed_add_alias() = runBlocking {
        val requests = mutableListOf<HostHttpRequest>()
        val transport = HostHttpTransport { received ->
            requests += received
            HostHttpResponse(302, received.url, mapOf("location" to "/remote/complete?status=added"), byteArrayOf())
        }
        val policy = RemoteOperationRequestPolicy(
            origin = HttpsOrigin("https://www.wenku8.net"),
            method = NetworkMethod.POST,
            path = "/remote/shelf",
            fixedParameters = mapOf("mode" to "add"),
            remoteBookIdParameter = "bid",
            redirects = listOf(
                RemoteOperationRedirectPolicy(
                    origin = HttpsOrigin("https://www.wenku8.net"),
                    method = NetworkMethod.GET,
                    path = "/remote/complete",
                    fixedParameters = mapOf("status" to "added"),
                ),
            ),
        )

        val failure = assertHostFailure {
            HostNetworkGateway(transport).request(
                grant.copy(remoteAddPolicy = policy),
                request(url = "https://www.wenku8.net/start"),
            )
        }

        assertEquals(HostNetworkError.INVALID_REQUEST, failure.error)
        assertEquals(1, requests.size)
    }

    @Test
    fun signed_read_redirect_cannot_enter_a_signed_add_alias() = runBlocking {
        val requests = mutableListOf<HostHttpRequest>()
        val transport = HostHttpTransport { request ->
            requests += request
            HostHttpResponse(
                status = 302,
                finalUrl = request.url,
                headers = mapOf("location" to "/remote/complete?status=added"),
                bytes = ByteArray(0),
            )
        }
        val alias = RemoteOperationRedirectPolicy(
            origin = HttpsOrigin("https://www.wenku8.net"),
            method = NetworkMethod.GET,
            path = "/remote/complete",
            fixedParameters = mapOf("status" to "added"),
        )
        val readPolicy = RemoteOperationRequestPolicy(
            origin = HttpsOrigin("https://www.wenku8.net"),
            method = NetworkMethod.GET,
            path = "/remote/list",
            fixedParameters = mapOf("mode" to "list"),
            redirects = listOf(alias),
        )
        val protectedAddPolicy = addPolicy.copy(redirects = listOf(alias))

        val failure = assertHostFailure {
            HostNetworkGateway(transport).request(
                grant.copy(remoteAddPolicy = protectedAddPolicy),
                request(url = "https://www.wenku8.net/remote/list?mode=list"),
                remoteLibraryReadContext(readPolicy, cursor = null),
            )
        }

        assertEquals(HostNetworkError.INVALID_REQUEST, failure.error)
        assertEquals(1, requests.size)
    }
    @Test
    fun generic_context_cannot_reach_the_signed_add_surface() = runBlocking {
        val transport = RecordingTransport()
        val gateway = HostNetworkGateway(transport)
        val addRequest = addRequest()

        val genericFailure = assertHostFailure { gateway.request(grant, addRequest) }
        val readFailure = assertHostFailure {
            gateway.request(
                grant,
                addRequest,
                remoteLibraryReadContext(
                    RemoteOperationRequestPolicy(
                        origin = HttpsOrigin("https://www.wenku8.net"),
                        method = NetworkMethod.GET,
                        path = "/remote/shelf",
                        fixedParameters = mapOf("mode" to "list"),
                    ),
                    cursor = null,
                ),
            )
        }

        assertEquals(HostNetworkError.INVALID_REQUEST, genericFailure.error)
        assertEquals(HostNetworkError.INVALID_REQUEST, readFailure.error)
        assertEquals(0, transport.requests.size)
        val parameterBypassFailure = assertHostFailure {
            gateway.request(grant, addRequest(form = mapOf("mode" to "list", "bid" to "42")))
        }
        assertEquals(HostNetworkError.INVALID_REQUEST, parameterBypassFailure.error)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun remote_add_rejects_cache_modes_before_token_acceptance() = runBlocking {
        val transport = RecordingTransport()
        val registry = DirectActionTokenRegistry()
        val gateway = HostNetworkGateway(transport, directActionTokens = registry)
        var acceptCalls = 0
        val token = registry.mint(
            DirectActionBinding("org.tsuyomi.wenku8", "42", "reconcile", "digest", "0.2.0", "capability", 7, 9),
        ) {
            acceptCalls += 1
            true
        }
        val cacheablePolicy = addPolicy.copy(method = NetworkMethod.GET)
        val context = remoteLibraryAddContext(cacheablePolicy, "42", token)
        val cachedAdd = request(
            url = "https://www.wenku8.net/remote/shelf?mode=add&bid=42",
            cache = NetworkCacheMode.DEFAULT,
            semanticCacheKey = "remote-add:42",
        )

        val failure = assertHostFailure { gateway.request(grant.copy(remoteAddPolicy = cacheablePolicy), cachedAdd, context) }

        assertEquals(HostNetworkError.INVALID_REQUEST, failure.error)
        assertEquals(0, acceptCalls)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun remote_add_acceptance_is_single_use_and_rejection_has_zero_transport() = runBlocking {
        val transport = RecordingTransport()
        val registry = DirectActionTokenRegistry()
        val gateway = HostNetworkGateway(transport, directActionTokens = registry)
        val policy = addPolicy
        val binding = DirectActionBinding("org.tsuyomi.wenku8", "42", "reconcile", "digest", "0.2.0", "capability", 7, 9)

        val rejectedToken = registry.mint(binding) { false }
        val rejected = assertHostFailure {
            gateway.request(
                grant,
                addRequest(),
                remoteLibraryAddContext(policy, "42", rejectedToken),
            )
        }
        assertEquals(HostNetworkError.CANCELLED, rejected.error)
        assertEquals(0, transport.requests.size)

        val acceptedToken = registry.mint(binding) {
            assertEquals(0, transport.requests.size)
            true
        }
        val context = remoteLibraryAddContext(policy, "42", acceptedToken)
        gateway.request(grant, addRequest(), context)
        assertEquals(1, transport.requests.size)
        val replay = assertHostFailure {
            gateway.request(grant, addRequest(), context)
        }
        assertEquals(HostNetworkError.CANCELLED, replay.error)
        assertEquals(1, transport.requests.size)
    }

    private fun request(
        url: String = "https://www.wenku8.net/book/1234.htm",
        headers: Map<String, String> = emptyMap(),
        decode: DecodeMode = DecodeMode.UTF8,
        cache: NetworkCacheMode = NetworkCacheMode.NETWORK_ONLY,
        semanticCacheKey: String? = null,
    ) = SourceNetworkRequest(
        url = url,
        method = NetworkMethod.GET,
        headers = headers,
        decode = decode,
        cache = cache,
        semanticCacheKey = semanticCacheKey,
    )

    private fun addRequest(form: Map<String, String> = mapOf("mode" to "add", "bid" to "42")) = SourceNetworkRequest(
        url = "https://www.wenku8.net/remote/shelf",
        method = NetworkMethod.POST,
        form = form,
        decode = DecodeMode.UTF8,
        cache = NetworkCacheMode.NETWORK_ONLY,
    )

    private suspend fun assertHostFailure(action: suspend () -> Unit): HostNetworkException = try {
        action()
        throw AssertionError("Expected HostNetworkException")
    } catch (error: HostNetworkException) {
        error
    }

    private class RecordingTransport : HostHttpTransport {
        val requests = mutableListOf<HostHttpRequest>()

        override suspend fun execute(request: HostHttpRequest): HostHttpResponse {
            requests += request
            return HostHttpResponse(
                status = 200,
                finalUrl = request.url,
                headers = mapOf("content-type" to "text/html; charset=utf-8", "set-cookie" to "hidden"),
                bytes = "fixture".encodeToByteArray(),
            )
        }
    }
}
