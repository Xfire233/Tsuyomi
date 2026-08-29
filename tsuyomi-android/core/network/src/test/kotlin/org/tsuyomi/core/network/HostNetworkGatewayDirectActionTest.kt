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

class HostNetworkGatewayDirectActionTest {
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
}
