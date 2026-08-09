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

class HxpArchiveVerifierTest {
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

    private fun signedFixture(
        version: String = "0.1.0",
        limits: FixtureLimits = FixtureLimits(),
        payloadInArchive: ByteArray = ENTRY_BYTES,
    ): SignedFixture {
        val privateKey = Ed25519PrivateKeyParameters(ByteArray(32) { (it + 1).toByte() }, 0)
        val publisher = PublisherKey(
            keyId = "tsuyomi-fixture-key",
            publicKey = privateKey.generatePublicKey().encoded,
            trust = PublisherTrust.BUILT_IN_TEST,
        )
        val files = JsonObject(mapOf(ENTRY_PATH to JsonPrimitive(sha256(ENTRY_BYTES))))
        val contentDigest = sha256(JsonCanonicalizer(files.toString()).encodedUTF8)
        val manifest = manifest(contentDigest, files, version, limits)
        val canonicalManifest = JsonCanonicalizer(manifest).encodedUTF8
        val message = ByteArrayOutputStream().use { output ->
            output.write("tsuyomi-hxp-v1\u0000".toByteArray(StandardCharsets.US_ASCII))
            output.write(canonicalManifest)
            output.write(0)
            output.write(contentDigest.toByteArray(StandardCharsets.US_ASCII))
            output.toByteArray()
        }
        val signature = Ed25519Signer().apply {
            init(true, privateKey)
            update(message, 0, message.size)
        }.generateSignature()
        val archive = zip(
            linkedMapOf(
                "manifest.json" to manifest.toByteArray(StandardCharsets.UTF_8),
                ENTRY_PATH to payloadInArchive,
                "signature.ed25519" to signature,
            ),
        )
        return SignedFixture(publisher, archive)
    }

    private fun manifest(
        contentDigest: String,
        files: JsonObject,
        version: String,
        limits: FixtureLimits,
    ): String = JsonObject(
        linkedMapOf(
            "format" to JsonPrimitive("tsuyomi-hxp"),
            "manifestVersion" to JsonPrimitive(1),
            "id" to JsonPrimitive("org.tsuyomi.wenku8"),
            "version" to JsonPrimitive(version),
            "display" to JsonObject(mapOf("name" to JsonPrimitive("Wenku8"), "summary" to JsonPrimitive("Test fixture"))),
            "hostApi" to JsonObject(mapOf("minInclusive" to JsonPrimitive("1.0.0"), "maxExclusive" to JsonPrimitive("2.0.0"))),
            "entry" to JsonPrimitive(ENTRY_PATH),
            "integrity" to JsonObject(mapOf("algorithm" to JsonPrimitive("sha256"), "contentDigest" to JsonPrimitive(contentDigest), "files" to files)),
            "signing" to JsonObject(mapOf("algorithm" to JsonPrimitive("Ed25519"), "keyId" to JsonPrimitive("tsuyomi-fixture-key"), "signatureFile" to JsonPrimitive("signature.ed25519"))),
            "capabilities" to JsonObject(
                mapOf(
                    "network" to JsonObject(mapOf("origins" to JsonArray(listOf(JsonPrimitive("https://www.wenku8.net"))), "maxConcurrentRequests" to JsonPrimitive(limits.maxConcurrentRequests), "requestTimeoutMs" to JsonPrimitive(limits.requestTimeoutMs), "maxResponseBytes" to JsonPrimitive(limits.maxResponseBytes))),
                    "cookies" to JsonObject(mapOf("mode" to JsonPrimitive("sourceScoped"), "origins" to JsonArray(listOf(JsonPrimitive("https://www.wenku8.net"))))),
                    "webLogin" to JsonObject(mapOf("enabled" to JsonPrimitive(true), "origins" to JsonArray(listOf(JsonPrimitive("https://www.wenku8.net"))))),
                    "remoteLibrary" to JsonObject(mapOf("read" to JsonPrimitive(false), "writeOperations" to JsonArray(emptyList()))),
                    "storage" to JsonObject(mapOf("quotaBytes" to JsonPrimitive(limits.storageQuotaBytes))),
                ),
            ),
            "resourceLimits" to JsonObject(mapOf("maxExecutionWallTimeMs" to JsonPrimitive(limits.maxExecutionWallTimeMs), "maxMemoryBytes" to JsonPrimitive(limits.maxMemoryBytes))),
            "update" to JsonObject(mapOf("channel" to JsonPrimitive("stable"))),
        ),
    ).toString()

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        bytes.toByteArray()
    }

    private fun newInstaller(root: File, verifier: HxpArchiveVerifier): ExtensionInstaller = ExtensionInstaller(
        verifier = verifier,
        store = InstalledExtensionStore(
            QuotaFileStore(
                roots = StorageRoots(File(root, "no-backup"), File(root, "cache")),
                root = StorageRoot.NO_BACKUP,
                namespace = "extensions",
                quota = StorageQuota(maxBytes = 4L * 1024 * 1024, maxEntries = 16),
            ),
        ),
        stagingDirectory = File(root, "staging"),
    )

    private data class FixtureLimits(
        val maxExecutionWallTimeMs: Int = 15_000,
        val maxMemoryBytes: Int = 16_777_216,
        val storageQuotaBytes: Int = 1_048_576,
        val maxConcurrentRequests: Int = 2,
        val requestTimeoutMs: Int = 15_000,
        val maxResponseBytes: Int = 1_048_576,
    )

    private fun withUnindexedLeadingLocalEntry(archive: ByteArray, name: String, content: ByteArray): ByteArray {
        val prefix = localFileEntry(name, content)
        val adjustedArchive = archive.copyOf()
        val endOfCentralDirectory = adjustedArchive.findEndOfCentralDirectory()
        val centralDirectoryOffset = adjustedArchive.readUInt32LE(endOfCentralDirectory + 16).toInt()
        var entryOffset = centralDirectoryOffset
        while (entryOffset < endOfCentralDirectory) {
            require(adjustedArchive.readUInt32LE(entryOffset) == CENTRAL_FILE_HEADER_SIGNATURE.toLong())
            val nameLength = adjustedArchive.readUInt16LE(entryOffset + 28)
            val extraLength = adjustedArchive.readUInt16LE(entryOffset + 30)
            val commentLength = adjustedArchive.readUInt16LE(entryOffset + 32)
            val localHeaderOffset = adjustedArchive.readUInt32LE(entryOffset + 42)
            adjustedArchive.writeUInt32LE(entryOffset + 42, localHeaderOffset + prefix.size)
            entryOffset += CENTRAL_FILE_HEADER_SIZE + nameLength + extraLength + commentLength
        }
        require(entryOffset == endOfCentralDirectory)
        adjustedArchive.writeUInt32LE(endOfCentralDirectory + 16, (centralDirectoryOffset + prefix.size).toLong())
        return prefix + adjustedArchive
    }

    private fun localFileEntry(name: String, content: ByteArray): ByteArray {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val checksum = java.util.zip.CRC32().apply { update(content) }.value
        return ByteBuffer.allocate(LOCAL_FILE_HEADER_SIZE + nameBytes.size + content.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(LOCAL_FILE_HEADER_SIGNATURE)
            .putShort(20.toShort())
            .putShort(0.toShort())
            .putShort(ZipEntry.STORED.toShort())
            .putShort(0.toShort())
            .putShort(0.toShort())
            .putInt(checksum.toInt())
            .putInt(content.size)
            .putInt(content.size)
            .putShort(nameBytes.size.toShort())
            .putShort(0.toShort())
            .put(nameBytes)
            .put(content)
            .array()
    }

    private fun ByteArray.findEndOfCentralDirectory(): Int =
        (size - END_OF_CENTRAL_DIRECTORY_MIN_SIZE downTo maxOf(0, size - MAX_ZIP_COMMENT_SIZE - END_OF_CENTRAL_DIRECTORY_MIN_SIZE))
            .firstOrNull { offset ->
                readUInt32LE(offset) == END_OF_CENTRAL_DIRECTORY_SIGNATURE.toLong() &&
                    offset + END_OF_CENTRAL_DIRECTORY_MIN_SIZE + readUInt16LE(offset + 20) == size
            }
            ?: error("Missing end of central directory")

    private fun ByteArray.readUInt16LE(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.readUInt32LE(offset: Int): Long =
        (this[offset].toLong() and 0xff) or
            ((this[offset + 1].toLong() and 0xff) shl 8) or
            ((this[offset + 2].toLong() and 0xff) shl 16) or
            ((this[offset + 3].toLong() and 0xff) shl 24)

    private fun ByteArray.writeUInt32LE(offset: Int, value: Long) {
        require(value in 0..0xffff_ffffL)
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
        this[offset + 2] = (value ushr 16).toByte()
        this[offset + 3] = (value ushr 24).toByte()
    }

    private data class SignedFixture(val publisher: PublisherKey, val bytes: ByteArray) {
        fun writeToTemporaryFile(archive: ByteArray = bytes): File = Files.createTempFile("wenku8-fixture", ".hxp").toFile().apply {
            writeBytes(archive)
            deleteOnExit()
        }
    }

    private companion object {
        const val ENTRY_PATH = "index.mjs"
        val ENTRY_BYTES = "export const source = 'wenku8';".toByteArray(StandardCharsets.UTF_8)
        const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
        const val CENTRAL_FILE_HEADER_SIGNATURE = 0x02014b50
        const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
        const val LOCAL_FILE_HEADER_SIZE = 30
        const val CENTRAL_FILE_HEADER_SIZE = 46
        const val END_OF_CENTRAL_DIRECTORY_MIN_SIZE = 22
        const val MAX_ZIP_COMMENT_SIZE = 65_535
    }
}
