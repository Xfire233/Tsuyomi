/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertNotEquals
import org.erdtman.jcs.JsonCanonicalizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.shared.sourcecontract.SourceId
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkMethod

class HxpArchiveVerificationTest {
    @Test
    fun validSignedArchiveVerifiesAndActivatesAtomically() {
        val fixture = signedFixture()
        val keyStore = InMemoryPublisherKeyStore(listOf(fixture.publisher))
        val verifier = HxpArchiveVerifier(keyStore)
        val candidate = fixture.writeToTemporaryFile()
        val verified = verifier.verify(candidate)
        assertEquals("org.tsuyomi.wenku8", verified.manifest.sourceId.value)
        assertEquals(fixture.publisher.fingerprint, verified.publisherFingerprint)

        val root = Files.createTempDirectory("hxp-store").toFile()
        val quotaStore = QuotaFileStore(
            roots = StorageRoots(File(root, "no-backup"), File(root, "cache")),
            root = StorageRoot.NO_BACKUP,
            namespace = "extensions",
            quota = StorageQuota(maxBytes = 4L * 1024 * 1024, maxEntries = 16),
        )
        val store = InstalledExtensionStore(quotaStore)
        val installer = ExtensionInstaller(verifier, store, File(root, "staging"))
        val prepared = installer.prepare(candidate)
        assertEquals(
            listOf(
                "cookies-origin:https://www.wenku8.net",
                "cookies:sourceScoped",
                "network:https://www.wenku8.net",
                "web-login",
                "web-login-origin:https://www.wenku8.net",
            ),
            prepared.addedCapabilities,
        )
        assertEquals(
            listOf(
                ResourceLimitIncrease(ResourceLimit.MAX_EXECUTION_WALL_TIME_MS, 0, 15_000),
                ResourceLimitIncrease(ResourceLimit.MAX_MEMORY_BYTES, 0, 16_777_216),
                ResourceLimitIncrease(ResourceLimit.STORAGE_QUOTA_BYTES, 0, 1_048_576),
                ResourceLimitIncrease(ResourceLimit.NETWORK_CONCURRENT_REQUESTS, 0, 2),
                ResourceLimitIncrease(ResourceLimit.NETWORK_REQUEST_TIMEOUT_MS, 0, 15_000),
                ResourceLimitIncrease(ResourceLimit.NETWORK_RESPONSE_BYTES, 0, 1_048_576),
            ),
            prepared.resourceLimitIncreases,
        )
        installer.activate(prepared, ExtensionInstallApproval.approve(prepared))
        val active = installer.readVerifiedActive(SourceId("org.tsuyomi.wenku8"))
        assertNotNull(active)
        assertEquals(verified.packageSha256, active?.packageSha256)
    }

    @Test
    fun payloadMutationIsRejectedBeforeRuntimeEvaluation() {
        val fixture = signedFixture(payloadInArchive = "export const changed = true;".toByteArray())
        val error = assertThrows(HxpVerificationException::class.java) {
            HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(fixture.publisher)))
                .verify(fixture.writeToTemporaryFile())
        }
        assertEquals(HxpVerificationError.INTEGRITY_MISMATCH, error.error)
    }

    @Test
    fun entryModuleComesFromSignedCentralDirectoryEntry() {
        val fixture = signedFixture()
        val archive = withUnindexedLeadingLocalEntry(
            archive = fixture.bytes,
            name = ENTRY_PATH,
            content = "export const source = 'attacker';".toByteArray(StandardCharsets.UTF_8),
        )

        val verified = HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(fixture.publisher)))
            .verify(fixture.writeToTemporaryFile(archive))

        assertArrayEquals(ENTRY_BYTES, verified.readVerifiedEntryModule())
        verified.readVerifiedEntryModule()[0] = 'X'.code.toByte()
        assertArrayEquals(ENTRY_BYTES, verified.readVerifiedEntryModule())
    }

    @Test
    fun unknownAndRevokedPublishersAreRejected() {
        val fixture = signedFixture()
        val unknown = assertThrows(HxpVerificationException::class.java) {
            HxpArchiveVerifier(InMemoryPublisherKeyStore(emptyList())).verify(fixture.writeToTemporaryFile())
        }
        assertEquals(HxpVerificationError.UNKNOWN_PUBLISHER, unknown.error)

        val revokedStore = InMemoryPublisherKeyStore(listOf(fixture.publisher)).also {
            it.revokeFingerprint(fixture.publisher.fingerprint)
        }
        val revoked = assertThrows(HxpVerificationException::class.java) {
            HxpArchiveVerifier(revokedStore).verify(fixture.writeToTemporaryFile())
        }
        assertEquals(HxpVerificationError.REVOKED_PUBLISHER, revoked.error)
    }


}
