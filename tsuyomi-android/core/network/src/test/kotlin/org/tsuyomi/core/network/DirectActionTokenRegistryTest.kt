/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DirectActionTokenRegistryTest {
    private val binding = DirectActionBinding(
        sourceId = "org.tsuyomi.wenku8",
        remoteBookId = "42",
        reconciliationId = "reconcile",
        packageDigest = "digest",
        packageVersion = "0.2.0",
        capabilitySetFingerprint = "capability",
        registryGeneration = 3,
        ownerGeneration = 5,
    )

    @Test
    fun pre_accept_revoke_returns_binding_and_prevents_acceptance() = runBlocking {
        val registry = DirectActionTokenRegistry()
        var accepted = 0
        val token = registry.mint(binding) { accepted += 1; true }

        assertEquals(binding, registry.revoke(token))
        assertNull(registry.revoke(token))
        val failure = runCatching { registry.accept(binding.sourceId, binding.remoteBookId, token) }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(HostNetworkError.CANCELLED, (failure as HostNetworkException).error)
        assertEquals(0, accepted)
    }

    @Test
    fun mismatched_identity_does_not_consume_callback() = runBlocking {
        val registry = DirectActionTokenRegistry()
        var accepted = 0
        val token = registry.mint(binding) { accepted += 1; true }

        val failure = runCatching { registry.accept(binding.sourceId, "other", token) }.exceptionOrNull()
        assertEquals(HostNetworkError.CANCELLED, (failure as HostNetworkException).error)
        assertEquals(0, accepted)
        assertEquals(binding, registry.revoke(token))
    }
}
