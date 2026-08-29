/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkCacheMode
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.shared.sourcecontract.SourceNetworkRequest

class HostNetworkGatewayRemotePolicyTest {
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
}
