/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensiontestkit

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tsuyomi.source.extensionmanager.HxpArchiveVerifier
import org.tsuyomi.source.extensionmanager.InMemoryPublisherKeyStore

class Gate2TestPublisherTest {
    @Test
    fun verifiesTheDeterministicWenku8Fixture() {
        val fixture = File("../../../tsuyomi-extensions/fixtures/wenku8/wenku8-fixture.hxp")
        check(fixture.isFile) { "Build tsuyomi-extensions fixture before Android verification" }

        val verified = HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(Gate2TestPublisher.key))).verify(fixture)

        assertEquals("org.tsuyomi.wenku8", verified.manifest.sourceId.value)
        assertEquals("0.2.0", verified.manifest.version.original)
        assertEquals(Gate2TestPublisher.key.fingerprint, verified.publisherFingerprint)
    }
}
