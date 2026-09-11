/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositePublisherKeyResolverTest {
    @Test
    fun conflictingUserKeyCannotShadowPinnedBuiltInMaterial() {
        val official = PublisherKey("pinned-official", ByteArray(32) { 1 }, PublisherTrust.BUILT_IN_OFFICIAL)
        val impostor = PublisherKey(official.keyId, ByteArray(32) { 2 }, PublisherTrust.USER_ADDED)
        val resolver = CompositePublisherKeyResolver(listOf(
            RevocationFixtureResolver(impostor, setOf(official.fingerprint), setOf("d".repeat(64))),
            RevocationFixtureResolver(official),
        ))
        assertEquals(official.fingerprint, resolver.resolve(official.keyId)?.fingerprint)
        assertFalse(resolver.isRevokedPackage("d".repeat(64), official.keyId, official.fingerprint))
    }

    @Test
    fun userRootCannotRevokeBuiltInPublisherOrPackageWithMatchingIdentity() {
        val official = PublisherKey(
            keyId = "official-publisher-key",
            publicKey = ByteArray(32) { (it + 1).toByte() },
            trust = PublisherTrust.BUILT_IN_OFFICIAL,
        )
        val maliciousUserRoot = PublisherKey(
            keyId = official.keyId,
            publicKey = official.publicKey.copyOf(),
            trust = PublisherTrust.USER_ADDED,
        )
        val packageDigest = "a".repeat(64)
        val resolver = CompositePublisherKeyResolver(
            listOf(
                RevocationFixtureResolver(official),
                RevocationFixtureResolver(maliciousUserRoot, setOf(official.fingerprint), setOf(packageDigest)),
            ),
        )

        assertEquals(PublisherTrust.BUILT_IN_OFFICIAL, resolver.resolve(official.keyId)?.trust)
        assertFalse(resolver.isRevokedPublisher(official.keyId, official.fingerprint))
        assertFalse(resolver.isRevokedPackage(packageDigest, official.keyId, official.fingerprint))
    }

    @Test
    fun rootRevocationStillAppliesToItsOwnUserAddedPublisher() {
        val publisher = PublisherKey(
            keyId = "user-root-publisher",
            publicKey = ByteArray(32) { (it + 33).toByte() },
            trust = PublisherTrust.USER_ADDED,
        )
        val packageDigest = "b".repeat(64)
        val resolver = CompositePublisherKeyResolver(
            listOf(RevocationFixtureResolver(publisher, setOf(publisher.fingerprint), setOf(packageDigest))),
        )

        assertTrue(resolver.isRevokedPublisher(publisher.keyId, publisher.fingerprint))
        assertTrue(resolver.isRevokedPackage(packageDigest, publisher.keyId, publisher.fingerprint))
    }

    @Test
    fun identicalLocalAndRepositoryUserKeyMaterialRemainsResolvableAndScoped() {
        val local = PublisherKey(
            keyId = "shared-user-publisher",
            publicKey = ByteArray(32) { (it + 18).toByte() },
            trust = PublisherTrust.USER_ADDED,
        )
        val repositoryAlias = PublisherKey(local.keyId, local.publicKey.copyOf(), PublisherTrust.USER_ADDED)
        val resolver = CompositePublisherKeyResolver(
            listOf(
                RevocationFixtureResolver(local),
                RevocationFixtureResolver(repositoryAlias, revokedPackages = setOf("c".repeat(64))),
            ),
        )

        assertEquals(PublisherTrust.USER_ADDED, resolver.resolve(local.keyId)?.trust)
        assertTrue(resolver.isRevokedPackage("c".repeat(64), local.keyId, local.fingerprint))
    }

    private class RevocationFixtureResolver(
        private val key: PublisherKey,
        private val revokedPublishers: Set<String> = emptySet(),
        private val revokedPackages: Set<String> = emptySet(),
    ) : PublisherKeyResolver {
        override fun resolve(keyId: String): PublisherKey? = key.takeIf { it.keyId == keyId }
        override fun isRevokedFingerprint(fingerprint: String): Boolean = fingerprint in revokedPublishers
        override fun isRevokedPackage(packageSha256: String): Boolean = packageSha256 in revokedPackages
    }
}
