/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.erdtman.jcs.JsonCanonicalizer

class HxpArchiveVerifier(
    private val publisherKeys: PublisherKeyResolver,
    private val hostApiVersion: SemanticVersion = SemanticVersion.parse("1.1.0"),
    private val limits: HxpArchiveLimits = HxpArchiveLimits(),
) {
    fun verify(file: File): VerifiedHxpPackage {
        if (!file.isFile || file.length() !in 1..limits.maxArchiveBytes) {
            fail(HxpVerificationError.ARCHIVE_TOO_LARGE)
        }
        val archiveBytes = runCatching { file.readBytes() }
            .getOrElse { fail(HxpVerificationError.INVALID_ARCHIVE_ENTRY) }
        if (archiveBytes.size.toLong() !in 1..limits.maxArchiveBytes) fail(HxpVerificationError.ARCHIVE_TOO_LARGE)
        return runCatching { verifyArchive(archiveBytes) }
            .getOrElse { error ->
                if (error is HxpVerificationException) throw error
                fail(HxpVerificationError.INVALID_ARCHIVE_ENTRY)
            }
    }

    private fun verifyArchive(archiveBytes: ByteArray): VerifiedHxpPackage {
        ZipFile.builder().setSeekableByteChannel(SeekableInMemoryByteChannel(archiveBytes)).get().use { zip ->
            val entries = mutableMapOf<String, ByteArray>()
            var totalUncompressed = 0L
            val enumeration = zip.entries
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                val name = entry.name
                if (entries.size >= limits.maxFileCount) fail(HxpVerificationError.TOO_MANY_FILES)
                if (!isSafeArchivePath(name) || entry.isDirectory || entries.containsKey(name)) {
                    fail(HxpVerificationError.INVALID_ARCHIVE_ENTRY)
                }
                if (entry.generalPurposeBit.usesEncryption()) fail(HxpVerificationError.ENCRYPTED_ENTRY)
                if (entry.isUnixSymlink) fail(HxpVerificationError.SYMLINK_ENTRY)
                if (entry.method != ZipEntry.STORED && entry.method != ZipEntry.DEFLATED) {
                    fail(HxpVerificationError.UNSUPPORTED_COMPRESSION)
                }
                if (entry.size < 0 || entry.size > limits.maxFileBytes) fail(HxpVerificationError.FILE_TOO_LARGE)
                if (entry.compressedSize < 0) fail(HxpVerificationError.INVALID_ARCHIVE_ENTRY)
                if (entry.size > 0 && entry.compressedSize == 0L) {
                    fail(HxpVerificationError.COMPRESSION_RATIO_EXCEEDED)
                }
                if (entry.compressedSize > 0 && entry.size > entry.compressedSize * limits.maxCompressionRatio) {
                    fail(HxpVerificationError.COMPRESSION_RATIO_EXCEEDED)
                }
                totalUncompressed += entry.size
                if (totalUncompressed > limits.maxUncompressedBytes) fail(HxpVerificationError.ARCHIVE_TOO_LARGE)
                val bytes = zip.getInputStream(entry).use { input ->
                    val output = ByteArrayOutputStream(entry.size.toInt())
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > entry.size || total > limits.maxFileBytes) fail(HxpVerificationError.FILE_TOO_LARGE)
                        output.write(buffer, 0, count)
                    }
                    if (total != entry.size) fail(HxpVerificationError.INVALID_ARCHIVE_ENTRY)
                    output.toByteArray()
                }
                entries[name] = bytes
            }

            val manifestBytes = entries[MANIFEST] ?: fail(HxpVerificationError.MISSING_REQUIRED_FILE)
            val signature = entries[SIGNATURE] ?: fail(HxpVerificationError.MISSING_REQUIRED_FILE)
            if (signature.size != 64) fail(HxpVerificationError.INVALID_SIGNATURE)
            val parsed = HxpManifestParser.parse(manifestBytes, hostApiVersion)
            val manifest = parsed.manifest
            if (manifest.entry !in entries) fail(HxpVerificationError.MISSING_REQUIRED_FILE)

            val expectedArchiveFiles = manifest.files.keys + MANIFEST + SIGNATURE
            if (entries.keys != expectedArchiveFiles) fail(HxpVerificationError.INTEGRITY_MISMATCH)
            for ((path, expectedDigest) in manifest.files) {
                val actual = entries[path]?.let(::sha256) ?: fail(HxpVerificationError.INTEGRITY_MISMATCH)
                if (actual != expectedDigest) fail(HxpVerificationError.INTEGRITY_MISMATCH)
            }
            val canonicalFiles = JsonCanonicalizer(
                JsonObject(manifest.files.mapValues { JsonPrimitive(it.value) }).toString(),
            ).encodedUTF8
            if (sha256(canonicalFiles) != manifest.contentDigest) fail(HxpVerificationError.INTEGRITY_MISMATCH)

            val publisher = publisherKeys.resolve(manifest.publisherKeyId)
                ?: fail(HxpVerificationError.UNKNOWN_PUBLISHER)
            if (publisherKeys.isRevokedFingerprint(publisher.fingerprint)) {
                fail(HxpVerificationError.REVOKED_PUBLISHER)
            }
            if (publisherKeys.isRevokedPackage(manifest.contentDigest)) {
                fail(HxpVerificationError.REVOKED_PACKAGE)
            }
            val signedMessage = signatureMessage(parsed.canonicalBytes, manifest.contentDigest)
            if (!verifyEd25519(publisher.publicKey, signedMessage, signature)) {
                fail(HxpVerificationError.INVALID_SIGNATURE)
            }
            return VerifiedHxpPackage(
                manifest = manifest,
                packageSha256 = sha256(archiveBytes),
                publisherFingerprint = publisher.fingerprint,
                archiveBytes = archiveBytes,
                entryModuleBytes = entries.getValue(manifest.entry),
            )
        }
    }

    private fun signatureMessage(canonicalManifest: ByteArray, contentDigest: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            output.write(SIGNATURE_PREFIX)
            output.write(canonicalManifest)
            output.write(0)
            output.write(contentDigest.toByteArray(StandardCharsets.US_ASCII))
            output.toByteArray()
        }

    private fun verifyEd25519(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
        runCatching {
            Ed25519Signer().apply {
                init(false, Ed25519PublicKeyParameters(publicKey, 0))
                update(message, 0, message.size)
            }.verifySignature(signature)
        }.getOrDefault(false)

    private companion object {
        const val MANIFEST = "manifest.json"
        const val SIGNATURE = "signature.ed25519"
        val SIGNATURE_PREFIX = "tsuyomi-hxp-v1\u0000".toByteArray(StandardCharsets.US_ASCII)
    }
}

data class HxpArchiveLimits(
    val maxArchiveBytes: Long = 16L * 1024 * 1024,
    val maxUncompressedBytes: Long = 32L * 1024 * 1024,
    val maxFileBytes: Long = 8L * 1024 * 1024,
    val maxFileCount: Int = 256,
    val maxCompressionRatio: Long = 100,
)

private fun fail(error: HxpVerificationError): Nothing = throw HxpVerificationException(error)
