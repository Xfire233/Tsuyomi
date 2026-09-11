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
}
