/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.shared.sourcecontract.SourceId

class HxpArchiveActivationTest {
    @Test
    fun equalVersionReplayIsRejectedWithoutReplacingActivePackage() {
        val fixture = signedFixture()
        val verifier = HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(fixture.publisher)))
        val root = Files.createTempDirectory("hxp-replay").toFile()
        val store = InstalledExtensionStore(
            QuotaFileStore(
                StorageRoots(File(root, "no-backup"), File(root, "cache")),
                StorageRoot.NO_BACKUP,
                "extensions",
                StorageQuota(4L * 1024 * 1024, 16),
            ),
        )
        val installer = ExtensionInstaller(verifier, store, File(root, "staging"))
        val archive = fixture.writeToTemporaryFile()
        val first = installer.prepare(archive)
        installer.activate(first, ExtensionInstallApproval.approve(first))
        val replay = assertThrows(ExtensionInstallException::class.java) { installer.prepare(archive) }
        assertEquals(ExtensionInstallError.REPLAY_REJECTED, replay.error)
        assertEquals(first.candidate.packageSha256, installer.readVerifiedActive(first.candidate.manifest.sourceId)?.packageSha256)
    }

    @Test
    fun expandedResourceLimitsRequireFreshApprovalAndPreserveTheActiveArchiveOnRejection() {
        val restrictedLimits = FixtureLimits()
        val expandedLimits = FixtureLimits(
            maxExecutionWallTimeMs = 30_000,
            maxMemoryBytes = 33_554_432,
            storageQuotaBytes = 2_097_152,
            maxConcurrentRequests = 4,
            requestTimeoutMs = 30_000,
            maxResponseBytes = 2_097_152,
        )
        val restricted = signedFixture(version = "0.1.0", limits = restrictedLimits)
        val expandedBase = signedFixture(version = "0.1.0", limits = expandedLimits)
        val candidate = signedFixture(version = "0.2.0", limits = expandedLimits)
        val verifier = HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(restricted.publisher)))

        val restrictedInstaller = newInstaller(Files.createTempDirectory("hxp-restricted").toFile(), verifier)
        val expandedInstaller = newInstaller(Files.createTempDirectory("hxp-expanded").toFile(), verifier)
        val restrictedPrepared = restrictedInstaller.prepare(restricted.writeToTemporaryFile())
        restrictedInstaller.activate(restrictedPrepared, ExtensionInstallApproval.approve(restrictedPrepared))
        val expandedPrepared = expandedInstaller.prepare(expandedBase.writeToTemporaryFile())
        expandedInstaller.activate(expandedPrepared, ExtensionInstallApproval.approve(expandedPrepared))

        val widened = restrictedInstaller.prepare(candidate.writeToTemporaryFile())
        val unchanged = expandedInstaller.prepare(candidate.writeToTemporaryFile())

        assertEquals(emptyList<String>(), widened.addedCapabilities)
        assertEquals(
            listOf(
                ResourceLimitIncrease(ResourceLimit.MAX_EXECUTION_WALL_TIME_MS, 15_000, 30_000),
                ResourceLimitIncrease(ResourceLimit.MAX_MEMORY_BYTES, 16_777_216, 33_554_432),
                ResourceLimitIncrease(ResourceLimit.STORAGE_QUOTA_BYTES, 1_048_576, 2_097_152),
                ResourceLimitIncrease(ResourceLimit.NETWORK_CONCURRENT_REQUESTS, 2, 4),
                ResourceLimitIncrease(ResourceLimit.NETWORK_REQUEST_TIMEOUT_MS, 15_000, 30_000),
                ResourceLimitIncrease(ResourceLimit.NETWORK_RESPONSE_BYTES, 1_048_576, 2_097_152),
            ),
            widened.resourceLimitIncreases,
        )
        assertEquals(emptyList<ResourceLimitIncrease>(), unchanged.resourceLimitIncreases)
        assertEquals(widened.candidate.packageSha256, unchanged.candidate.packageSha256)
        assertNotEquals(widened.capabilityGrantFingerprint, unchanged.capabilityGrantFingerprint)
        assertEquals(
            restrictedPrepared.remoteCapabilitySetFingerprint,
            widened.remoteCapabilitySetFingerprint,
        )

        val staleApproval = ExtensionInstallApproval.approve(unchanged)
        val rejection = assertThrows(ExtensionInstallException::class.java) {
            restrictedInstaller.activate(widened, staleApproval)
        }
        assertEquals(ExtensionInstallError.APPROVAL_MISMATCH, rejection.error)
        assertEquals(
            restrictedPrepared.candidate.packageSha256,
            restrictedInstaller.readVerifiedActive(SourceId("org.tsuyomi.wenku8"))?.packageSha256,
        )
    }

    @Test
    fun contractedResourceLimitsDoNotEnterTheApprovalSummary() {
        val installed = signedFixture(version = "0.1.0")
        val contracted = signedFixture(
            version = "0.2.0",
            limits = FixtureLimits(
                maxExecutionWallTimeMs = 10_000,
                maxMemoryBytes = 8_388_608,
                storageQuotaBytes = 524_288,
                maxConcurrentRequests = 1,
                requestTimeoutMs = 10_000,
                maxResponseBytes = 524_288,
            ),
        )
        val verifier = HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(installed.publisher)))
        val installer = newInstaller(Files.createTempDirectory("hxp-contracted").toFile(), verifier)
        val preparedInstalled = installer.prepare(installed.writeToTemporaryFile())
        installer.activate(preparedInstalled, ExtensionInstallApproval.approve(preparedInstalled))

        val preparedContracted = installer.prepare(contracted.writeToTemporaryFile())

        assertEquals(emptyList<String>(), preparedContracted.addedCapabilities)
        assertEquals(emptyList<ResourceLimitIncrease>(), preparedContracted.resourceLimitIncreases)
    }

}
