/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.erdtman.jcs.JsonCanonicalizer
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots

internal const val ENTRY_PATH = "index.mjs"
internal val ENTRY_BYTES = "export const source = 'wenku8';".toByteArray(StandardCharsets.UTF_8)

internal data class FixtureLimits(
    val maxExecutionWallTimeMs: Int = 15_000,
    val maxMemoryBytes: Int = 16_777_216,
    val storageQuotaBytes: Int = 1_048_576,
    val maxConcurrentRequests: Int = 2,
    val requestTimeoutMs: Int = 15_000,
    val maxResponseBytes: Int = 1_048_576,
)

internal data class SignedFixture(val publisher: PublisherKey, val bytes: ByteArray) {
    fun writeToTemporaryFile(archive: ByteArray = bytes): File =
        Files.createTempFile("wenku8-fixture", ".hxp").toFile().apply {
            writeBytes(archive)
            deleteOnExit()
        }
}

internal fun signedFixture(
    version: String = "0.1.0",
    limits: FixtureLimits = FixtureLimits(),
    payloadInArchive: ByteArray = ENTRY_BYTES,
    remoteLibrary: JsonObject = JsonObject(
        mapOf("read" to JsonPrimitive(false), "writeOperations" to JsonArray(emptyList())),
    ),
): SignedFixture {
    val privateKey = Ed25519PrivateKeyParameters(ByteArray(32) { (it + 1).toByte() }, 0)
    val publisher = PublisherKey(
        keyId = "tsuyomi-fixture-key",
        publicKey = privateKey.generatePublicKey().encoded,
        trust = PublisherTrust.BUILT_IN_TEST,
    )
    val files = JsonObject(mapOf(ENTRY_PATH to JsonPrimitive(sha256(ENTRY_BYTES))))
    val contentDigest = sha256(JsonCanonicalizer(files.toString()).encodedUTF8)
    val manifest = manifest(contentDigest, files, version, limits, remoteLibrary)
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

internal fun newInstaller(root: File, verifier: HxpArchiveVerifier): ExtensionInstaller = ExtensionInstaller(
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

internal fun withUnindexedLeadingLocalEntry(archive: ByteArray, name: String, content: ByteArray): ByteArray {
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

private fun manifest(
    contentDigest: String,
    files: JsonObject,
    version: String,
    limits: FixtureLimits,
    remoteLibrary: JsonObject,
): String = JsonObject(
    linkedMapOf(
        "format" to JsonPrimitive("tsuyomi-hxp"),
        "manifestVersion" to JsonPrimitive(1),
        "id" to JsonPrimitive("org.tsuyomi.wenku8"),
        "version" to JsonPrimitive(version),
        "display" to JsonObject(mapOf("name" to JsonPrimitive("Wenku8"), "summary" to JsonPrimitive("Test fixture"))),
        "hostApi" to JsonObject(mapOf("minInclusive" to JsonPrimitive("1.0.0"), "maxExclusive" to JsonPrimitive("2.0.0"))),
        "entry" to JsonPrimitive(ENTRY_PATH),
        "integrity" to JsonObject(
            mapOf(
                "algorithm" to JsonPrimitive("sha256"),
                "contentDigest" to JsonPrimitive(contentDigest),
                "files" to files,
            ),
        ),
        "signing" to JsonObject(
            mapOf(
                "algorithm" to JsonPrimitive("Ed25519"),
                "keyId" to JsonPrimitive("tsuyomi-fixture-key"),
                "signatureFile" to JsonPrimitive("signature.ed25519"),
            ),
        ),
        "capabilities" to JsonObject(
            mapOf(
                "network" to JsonObject(
                    mapOf(
                        "origins" to JsonArray(listOf(JsonPrimitive("https://www.wenku8.net"))),
                        "maxConcurrentRequests" to JsonPrimitive(limits.maxConcurrentRequests),
                        "requestTimeoutMs" to JsonPrimitive(limits.requestTimeoutMs),
                        "maxResponseBytes" to JsonPrimitive(limits.maxResponseBytes),
                    ),
                ),
                "cookies" to JsonObject(
                    mapOf(
                        "mode" to JsonPrimitive("sourceScoped"),
                        "origins" to JsonArray(listOf(JsonPrimitive("https://www.wenku8.net"))),
                    ),
                ),
                "webLogin" to JsonObject(
                    mapOf(
                        "enabled" to JsonPrimitive(true),
                        "origins" to JsonArray(listOf(JsonPrimitive("https://www.wenku8.net"))),
                    ),
                ),
                "remoteLibrary" to remoteLibrary,
                "storage" to JsonObject(mapOf("quotaBytes" to JsonPrimitive(limits.storageQuotaBytes))),
            ),
        ),
        "resourceLimits" to JsonObject(
            mapOf(
                "maxExecutionWallTimeMs" to JsonPrimitive(limits.maxExecutionWallTimeMs),
                "maxMemoryBytes" to JsonPrimitive(limits.maxMemoryBytes),
            ),
        ),
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
    (size - END_OF_CENTRAL_DIRECTORY_MIN_SIZE downTo
        maxOf(0, size - MAX_ZIP_COMMENT_SIZE - END_OF_CENTRAL_DIRECTORY_MIN_SIZE))
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

private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
private const val CENTRAL_FILE_HEADER_SIGNATURE = 0x02014b50
private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
private const val LOCAL_FILE_HEADER_SIZE = 30
private const val CENTRAL_FILE_HEADER_SIZE = 46
private const val END_OF_CENTRAL_DIRECTORY_MIN_SIZE = 22
private const val MAX_ZIP_COMMENT_SIZE = 65_535
