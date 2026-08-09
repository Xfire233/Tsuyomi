/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

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
import org.erdtman.jcs.JsonCanonicalizer
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
                "storage:1048576",
                "web-login",
                "web-login-origin:https://www.wenku8.net",
            ),
            prepared.addedCapabilities,
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

    private fun signedFixture(
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
        val manifest = manifest(contentDigest, files)
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

    private fun manifest(contentDigest: String, files: JsonObject): String = JsonObject(
        linkedMapOf(
            "format" to JsonPrimitive("tsuyomi-hxp"),
            "manifestVersion" to JsonPrimitive(1),
            "id" to JsonPrimitive("org.tsuyomi.wenku8"),
            "version" to JsonPrimitive("0.1.0"),
            "display" to JsonObject(mapOf("name" to JsonPrimitive("Wenku8"), "summary" to JsonPrimitive("Test fixture"))),
            "hostApi" to JsonObject(mapOf("minInclusive" to JsonPrimitive("1.0.0"), "maxExclusive" to JsonPrimitive("2.0.0"))),
            "entry" to JsonPrimitive(ENTRY_PATH),
            "integrity" to JsonObject(mapOf("algorithm" to JsonPrimitive("sha256"), "contentDigest" to JsonPrimitive(contentDigest), "files" to files)),
            "signing" to JsonObject(mapOf("algorithm" to JsonPrimitive("Ed25519"), "keyId" to JsonPrimitive("tsuyomi-fixture-key"), "signatureFile" to JsonPrimitive("signature.ed25519"))),
            "capabilities" to JsonObject(
                mapOf(
                    "network" to JsonObject(mapOf("origins" to JsonArray(listOf(JsonPrimitive("https://www.wenku8.net"))), "maxConcurrentRequests" to JsonPrimitive(2), "requestTimeoutMs" to JsonPrimitive(15_000), "maxResponseBytes" to JsonPrimitive(1_048_576))),
                    "cookies" to JsonObject(mapOf("mode" to JsonPrimitive("sourceScoped"), "origins" to JsonArray(listOf(JsonPrimitive("https://www.wenku8.net"))))),
                    "webLogin" to JsonObject(mapOf("enabled" to JsonPrimitive(true), "origins" to JsonArray(listOf(JsonPrimitive("https://www.wenku8.net"))))),
                    "remoteLibrary" to JsonObject(mapOf("read" to JsonPrimitive(false), "writeOperations" to JsonArray(emptyList()))),
                    "storage" to JsonObject(mapOf("quotaBytes" to JsonPrimitive(1_048_576))),
                ),
            ),
            "resourceLimits" to JsonObject(mapOf("maxExecutionWallTimeMs" to JsonPrimitive(15_000), "maxMemoryBytes" to JsonPrimitive(16_777_216))),
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

    private data class SignedFixture(val publisher: PublisherKey, val bytes: ByteArray) {
        fun writeToTemporaryFile(): File = Files.createTempFile("wenku8-fixture", ".hxp").toFile().apply {
            writeBytes(bytes)
            deleteOnExit()
        }
    }

    private companion object {
        const val ENTRY_PATH = "index.mjs"
        val ENTRY_BYTES = "export const source = 'wenku8';".toByteArray(StandardCharsets.UTF_8)
    }
}
