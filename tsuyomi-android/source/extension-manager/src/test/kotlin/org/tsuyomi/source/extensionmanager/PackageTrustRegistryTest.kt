/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.io.File
import java.nio.file.Files
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class PackageTrustRegistryTest {
    @Test
    fun declinedSecondUserAddedPackageGrantPreservesActiveArchive() {
        val root = Files.createTempDirectory("package-grant-decline").toFile()
        val first = signedFixture(version = "1.0.0")
        val second = signedFixture(version = "1.1.0")
        val userPublisher = PublisherKey(
            first.publisher.keyId,
            first.publisher.publicKey.copyOf(),
            PublisherTrust.USER_ADDED,
        )
        val trust = PackageTrustRegistry(File(root, "package-trust"))
        val installer = newInstaller(
            root,
            HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(userPublisher))),
            trust,
        )

        val installed = installer.prepare(first.writeToTemporaryFile())
        trust.approve(installed, userPublisher, retainPublisherKey = true)
        installer.activate(installed, ExtensionInstallApproval.approve(installed))
        val restartedTrust = PackageTrustRegistry(File(root, "package-trust"))
        val restored = newInstaller(root, HxpArchiveVerifier(restartedTrust.publisherKeys), restartedTrust)
            .readVerifiedActive(installed.candidate.manifest.sourceId)
        assertEquals(installed.candidate.packageSha256, restored?.packageSha256)

        val declined = installer.prepare(second.writeToTemporaryFile())
        val rejection = assertThrows(ExtensionInstallException::class.java) {
            installer.activate(declined, ExtensionInstallApproval.approve(declined))
        }

        assertEquals(ExtensionInstallError.PACKAGE_GRANT_REQUIRED, rejection.error)
        assertEquals(installed.candidate.packageSha256, installer.readVerifiedActive(installed.candidate.manifest.sourceId)?.packageSha256)

        val failingTrust = object : PackageActivationTrust by trust {
            override fun activationSucceeded(prepared: PreparedExtensionInstall) {
                throw PackageTrustException(PackageTrustError.STORAGE_UNAVAILABLE)
            }
        }
        trust.approve(declined, userPublisher)
        assertEquals(PackageTrustError.STORAGE_UNAVAILABLE, assertThrows(PackageTrustException::class.java) {
            newInstaller(root, HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(userPublisher))), failingTrust)
                .activate(declined, ExtensionInstallApproval.approve(declined))
        }.error)
        val afterFailure = PackageTrustRegistry(File(root, "package-trust"))
        assertTrue(afterFailure.isApproved(installed.candidate))
        assertFalse(afterFailure.isApproved(declined.candidate))
        assertEquals(userPublisher.fingerprint, afterFailure.publisherKeys.resolve(userPublisher.keyId)?.fingerprint)
        assertEquals(installed.candidate.packageSha256, installer.readVerifiedActive(installed.candidate.manifest.sourceId)?.packageSha256)

        trust.approve(declined, userPublisher)
        installer.activate(declined, ExtensionInstallApproval.approve(declined))
        val afterUpdate = PackageTrustRegistry(File(root, "package-trust"))
        assertTrue(afterUpdate.isApproved(declined.candidate))
        assertEquals(userPublisher.fingerprint, afterUpdate.publisherKeys.resolve(userPublisher.keyId)?.fingerprint)
        assertEquals(declined.candidate.packageSha256,
            newInstaller(root, HxpArchiveVerifier(afterUpdate.publisherKeys), afterUpdate)
                .readVerifiedActive(declined.candidate.manifest.sourceId)?.packageSha256)
    }

    @Test
    fun localKeyIdIsOnlyAnUntrustedLabelUntilRawKeyVerifiesWholeArchive() {
        val root = Files.createTempDirectory("package-key-entry").toFile()
        val fixture = signedFixture()
        val trust = PackageTrustRegistry(File(root, "package-trust"))
        val archive = fixture.writeToTemporaryFile()
        val inspector = HxpArchiveVerifier(InMemoryPublisherKeyStore(emptyList()))

        assertEquals(fixture.publisher.keyId, trust.inspectLocalArchive(archive, inspector).keyId)
        val wrongKey = Base64.getEncoder().encodeToString(ByteArray(32) { 99 })
        val failure = assertThrows(HxpVerificationException::class.java) {
            trust.verifyUserAddedArchive(archive, fixture.publisher.keyId, wrongKey, InMemoryPublisherKeyStore(emptyList()))
        }
        assertEquals(HxpVerificationError.INVALID_SIGNATURE, failure.error)

        val verified = trust.verifyUserAddedArchive(
            archive,
            fixture.publisher.keyId,
            Base64.getEncoder().encodeToString(fixture.publisher.publicKey),
            InMemoryPublisherKeyStore(emptyList()),
        )
        assertFalse(trust.isApproved(verified.packageInfo))
    }

    @Test
    fun approvedMigrationReceiptPreservesOldPinUntilExactSwappedArchiveRecoversIt() {
        val root = Files.createTempDirectory("package-migration-receipt").toFile()
        val original = signedFixture(version = "1.0.0")
        val replacement = signedFixture(
            version = "1.1.0",
            publisherKeyId = "official-replacement-key",
            publisherPrivateKey = ByteArray(32) { (it + 42).toByte() },
        )
        val originalKey = PublisherKey(original.publisher.keyId, original.publisher.publicKey.copyOf(), PublisherTrust.BUILT_IN_OFFICIAL)
        val replacementKey = PublisherKey(replacement.publisher.keyId, replacement.publisher.publicKey.copyOf(), PublisherTrust.BUILT_IN_OFFICIAL)
        val verifier = HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(originalKey, replacementKey)))
        val oldPackage = verifier.verify(original.writeToTemporaryFile())
        val newPackage = verifier.verify(replacement.writeToTemporaryFile())
        val trust = PackageTrustRegistry(File(root, "package-trust"))
        trust.pinInstalled(oldPackage, originalKey)
        val preparedMigration = PreparedExtensionInstall(
            candidate = newPackage,
            active = oldPackage,
            addedCapabilities = emptyList(),
            resourceLimitIncreases = emptyList(),
            capabilityGrantFingerprint = "grant",
            remoteCapabilitySetFingerprint = "remote",
            isDowngrade = false,
            isLegacyMigration = true,
        )

        trust.approve(preparedMigration, replacementKey)
        trust.pinInstalled(oldPackage, originalKey)
        val wrongArchive = verifier.verify(signedFixture(
            version = "1.2.0",
            publisherKeyId = replacementKey.keyId,
            publisherPrivateKey = ByteArray(32) { (it + 42).toByte() },
        ).writeToTemporaryFile())
        val wrongRecovery = assertThrows(PackageTrustException::class.java) {
            trust.pinInstalled(wrongArchive, replacementKey)
        }
        assertEquals(PackageTrustError.SOURCE_IDENTITY_CONFLICT, wrongRecovery.error)

        trust.pinInstalled(newPackage, replacementKey)
        val oldAfterRecovery = assertThrows(PackageTrustException::class.java) {
            trust.pinInstalled(oldPackage, originalKey)
        }
        assertEquals(PackageTrustError.SOURCE_IDENTITY_CONFLICT, oldAfterRecovery.error)
    }

    @Test
    fun sourcePinSurvivesUninstallAndRejectsForeignPublisherBeforeActivation() {
        val root = Files.createTempDirectory("package-publisher-pin").toFile()
        val trust = PackageTrustRegistry(File(root, "trust"))
        val original = signedFixture(version = "1.0.0")
        val replacement = signedFixture(
            version = "1.1.0",
            publisherKeyId = "foreign-publisher-key",
            publisherPrivateKey = ByteArray(32) { (it + 71).toByte() },
        )
        val originalKey = PublisherKey(original.publisher.keyId, original.publisher.publicKey.copyOf(), PublisherTrust.USER_ADDED)
        val foreignKey = PublisherKey(replacement.publisher.keyId, replacement.publisher.publicKey.copyOf(), PublisherTrust.USER_ADDED)
        val installer = newInstaller(
            root,
            HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(originalKey, foreignKey))),
            trust,
        )
        val preparedOriginal = installer.prepare(original.writeToTemporaryFile())
        trust.approve(preparedOriginal, originalKey, retainPublisherKey = true)
        installer.activate(preparedOriginal, ExtensionInstallApproval.approve(preparedOriginal))
        trust.pinInstalled(preparedOriginal.candidate, originalKey)
        File(root, "no-backup/extensions/active/org.tsuyomi.wenku8.hxp").delete()

        val foreign = installer.prepare(replacement.writeToTemporaryFile())
        val rejected = assertThrows(PackageTrustException::class.java) {
            trust.approve(foreign, foreignKey)
        }
        assertEquals(PackageTrustError.SOURCE_IDENTITY_CONFLICT, rejected.error)
    }
    @Test
    fun unsignedLocalGrantIsExactAndNeverAdmittedThroughRepositoryBinding() {
        val root = Files.createTempDirectory("unsigned-exact-grant").toFile()
        val trust = PackageTrustRegistry(File(root, "trust"))
        val verifier = HxpArchiveVerifier(InMemoryPublisherKeyStore(emptyList()))
        val installer = newInstaller(root, verifier, trust)
        val first = installer.prepare(unsignedFixture().writeToTemporaryFile())
        assertEquals(PublisherTrust.LOCAL_UNSIGNED, first.candidate.publisherTrust)
        assertFalse(trust.isApproved(first.candidate))
        assertEquals(ExtensionInstallError.PACKAGE_GRANT_REQUIRED, assertThrows(ExtensionInstallException::class.java) {
            installer.activate(first, ExtensionInstallApproval.approve(first))
        }.error)
        val binding = RepositoryInstallBinding(
            sourceId = first.candidate.manifest.sourceId,
            version = first.candidate.manifest.version,
            packageSha256 = first.candidate.packageSha256,
            publisherKeyId = "official-fixture-key",
            publisherFingerprint = "a".repeat(64),
            hostApi = RepositoryHostApi(first.candidate.manifest.hostApiMinInclusive, first.candidate.manifest.hostApiMaxExclusive),
            legacyMigration = null,
        )
        assertEquals(ExtensionInstallError.UNSIGNED_REPOSITORY_REJECTED, assertThrows(ExtensionInstallException::class.java) {
            installer.prepareRepository(unsignedFixture().writeToTemporaryFile(), binding)
        }.error)

        trust.approveLocalUnsigned(first, allowPublisherTransition = false)
        assertFalse(trust.isApproved(first.candidate))
        installer.activate(first, ExtensionInstallApproval.approve(first))
        val restarted = PackageTrustRegistry(File(root, "trust"))
        assertTrue(restarted.isApproved(first.candidate))
        val modified = installer.prepare(unsignedFixture(version = "0.3.0", payload = "export const changed = true;".toByteArray()).writeToTemporaryFile())
        assertFalse(restarted.isApproved(modified.candidate))
        assertEquals(ExtensionInstallError.PACKAGE_GRANT_REQUIRED, assertThrows(ExtensionInstallException::class.java) {
            installer.activate(modified, ExtensionInstallApproval.approve(modified))
        }.error)
        assertEquals(first.candidate.packageSha256, installer.readVerifiedActive(first.candidate.manifest.sourceId)?.packageSha256)
    }

    @Test
    fun pinnedSignedIdentityTransitionsOnlyAfterUninstallAndExplicitExactConsentThenCanReturn() {
        val root = Files.createTempDirectory("unsigned-pin-transition").toFile()
        val signed = signedFixture(version = "1.0.0")
        val signedReturn = signedFixture(version = "3.0.0")
        val key = PublisherKey(signed.publisher.keyId, signed.publisher.publicKey.copyOf(), PublisherTrust.USER_ADDED)
        val trust = PackageTrustRegistry(File(root, "trust"))
        val installer = newInstaller(root, HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(key))), trust)
        val original = installer.prepare(signed.writeToTemporaryFile())
        trust.approve(original, key, retainPublisherKey = true)
        installer.activate(original, ExtensionInstallApproval.approve(original))
        assertEquals(ExtensionInstallError.KEY_ROTATION_NOT_AUTHORIZED, assertThrows(ExtensionInstallException::class.java) {
            installer.prepare(unsignedFixture().writeToTemporaryFile())
        }.error)
        File(root, "no-backup/extensions/active/org.tsuyomi.wenku8.hxp").delete()
        val local = installer.prepare(unsignedFixture().writeToTemporaryFile())
        assertTrue(local.requiresPublisherTransition)
        assertEquals(PackageTrustError.PUBLISHER_TRANSITION_REQUIRES_CONFIRMATION, assertThrows(PackageTrustException::class.java) {
            trust.approveLocalUnsigned(local, allowPublisherTransition = false)
        }.error)
        trust.approveLocalUnsigned(local, allowPublisherTransition = true)
        assertFalse(trust.isApproved(local.candidate))
        assertEquals(ExtensionInstallError.APPROVAL_MISMATCH, assertThrows(ExtensionInstallException::class.java) {
            installer.activate(local, ExtensionInstallApproval.approve(original))
        }.error)
        assertFalse(trust.isApproved(local.candidate))
        assertEquals(ExtensionInstallError.PACKAGE_GRANT_REQUIRED, assertThrows(ExtensionInstallException::class.java) {
            installer.activate(local, ExtensionInstallApproval.approve(local))
        }.error)
        trust.approveLocalUnsigned(local, allowPublisherTransition = true)
        installer.activate(local, ExtensionInstallApproval.approve(local))
        val restarted = PackageTrustRegistry(File(root, "trust"))
        assertTrue(restarted.isApproved(local.candidate))
        assertEquals(key.fingerprint, restarted.publisherKeys.resolve(key.keyId)?.fingerprint)

        File(root, "no-backup/extensions/active/org.tsuyomi.wenku8.hxp").delete()
        val returning = installer.prepare(signedReturn.writeToTemporaryFile())
        assertTrue(returning.requiresPublisherTransition)
        assertEquals(PackageTrustError.SOURCE_IDENTITY_CONFLICT, assertThrows(PackageTrustException::class.java) {
            trust.approve(returning, key)
        }.error)
        trust.approve(returning, key, allowPublisherTransition = true)
        assertFalse(trust.isApproved(returning.candidate))
        assertEquals(ExtensionInstallError.APPROVAL_MISMATCH, assertThrows(ExtensionInstallException::class.java) {
            installer.activate(returning, ExtensionInstallApproval.approve(local))
        }.error)
        assertFalse(trust.isApproved(returning.candidate))
        assertEquals(ExtensionInstallError.PACKAGE_GRANT_REQUIRED, assertThrows(ExtensionInstallException::class.java) {
            installer.activate(returning, ExtensionInstallApproval.approve(returning))
        }.error)
        trust.approve(returning, key, allowPublisherTransition = true)
        installer.activate(returning, ExtensionInstallApproval.approve(returning))
        assertTrue(PackageTrustRegistry(File(root, "trust")).isApproved(returning.candidate))
        assertFalse(PackageTrustRegistry(File(root, "trust")).isApproved(local.candidate))
    }

    @Test
    fun failedPostWriteTrustCommitRestoresPreviousArchiveAndForgetsPendingConsent() {
        val root = Files.createTempDirectory("unsigned-rollback").toFile()
        val trust = PackageTrustRegistry(File(root, "trust"))
        val failingTrust = object : PackageActivationTrust by trust {
            override fun activationSucceeded(prepared: PreparedExtensionInstall) {
                throw PackageTrustException(PackageTrustError.STORAGE_UNAVAILABLE)
            }
        }
        val installer = newInstaller(root, HxpArchiveVerifier(InMemoryPublisherKeyStore(emptyList())), failingTrust)
        val prepared = installer.prepare(unsignedFixture().writeToTemporaryFile())
        trust.approveLocalUnsigned(prepared, allowPublisherTransition = false)
        assertEquals(PackageTrustError.STORAGE_UNAVAILABLE, assertThrows(PackageTrustException::class.java) {
            installer.activate(prepared, ExtensionInstallApproval.approve(prepared))
        }.error)
        assertFalse(trust.isApproved(prepared.candidate))
        assertEquals(null, installer.readVerifiedActive(prepared.candidate.manifest.sourceId))
        assertEquals(ExtensionInstallError.PACKAGE_GRANT_REQUIRED, assertThrows(ExtensionInstallException::class.java) {
            installer.activate(prepared, ExtensionInstallApproval.approve(prepared))
        }.error)
    }

    @Test
    fun failedSignedActivationDoesNotRetainUnknownKeyOrGrant() {
        for (postWriteFailure in listOf(false, true)) {
            val root = Files.createTempDirectory("signed-activation-failure").toFile()
            try {
                val fixture = signedFixture()
                val publisher = PublisherKey(fixture.publisher.keyId, fixture.publisher.publicKey, PublisherTrust.USER_ADDED)
                val trustDirectory = File(root, "trust")
                val trust = PackageTrustRegistry(trustDirectory)
                val activationTrust = if (postWriteFailure) object : PackageActivationTrust by trust {
                    override fun activationSucceeded(prepared: PreparedExtensionInstall) {
                        throw PackageTrustException(PackageTrustError.STORAGE_UNAVAILABLE)
                    }
                } else trust
                val installer = newInstaller(root, HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(publisher))), activationTrust)
                val archive = fixture.writeToTemporaryFile()
                val prepared = try { installer.prepare(archive) } finally { archive.delete() }
                if (!postWriteFailure) {
                    repeat(16) { File(root, "no-backup/extensions/occupied-$it").writeText("occupied") }
                }
                trust.approve(prepared, publisher, retainPublisherKey = true)
                if (postWriteFailure) {
                    assertEquals(PackageTrustError.STORAGE_UNAVAILABLE, assertThrows(PackageTrustException::class.java) {
                        installer.activate(prepared, ExtensionInstallApproval.approve(prepared))
                    }.error)
                } else {
                    assertEquals(ExtensionInstallError.STORAGE_UNAVAILABLE, assertThrows(ExtensionInstallException::class.java) {
                        installer.activate(prepared, ExtensionInstallApproval.approve(prepared))
                    }.error)
                }
                val restarted = PackageTrustRegistry(trustDirectory)
                assertEquals(null, restarted.publisherKeys.resolve(publisher.keyId))
                assertFalse(restarted.isApproved(prepared.candidate))
                assertEquals(null, installer.readVerifiedActive(prepared.candidate.manifest.sourceId))
                assertEquals(ExtensionInstallError.PACKAGE_GRANT_REQUIRED, assertThrows(ExtensionInstallException::class.java) {
                    trust.requireExecutable(prepared.candidate)
                }.error)
            } finally {
                root.deleteRecursively()
            }
        }
    }

}
