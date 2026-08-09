/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.security

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Source/origin-partitioned protected credential store. Files are rooted in noBackupFilesDir, are
 * opaque by name, and hold only a versioned AEAD record. It never uses Room, DataStore, or logs.
 */
class SourceCredentialStore(
    context: Context,
    private val aead: AeadPort = AndroidKeyStoreAesGcm(),
) {
    private val directory: File = File(context.noBackupFilesDir, DIRECTORY_NAME).canonicalFile

    init {
        require(directory.isDirectory || directory.mkdirs()) { "Credential storage is unavailable" }
        require(isInside(context.noBackupFilesDir.canonicalFile, directory)) { "Credential storage is unavailable" }
    }

    @Synchronized
    fun put(partition: SourceCredentialPartition, plaintext: ByteArray) {
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Credential payload exceeds storage limit" }
        val encrypted = aead.encrypt(plaintext, partition.aad())
        if (encrypted.iv.size != GCM_IV_BYTES || encrypted.ciphertext.size > MAX_CIPHERTEXT_BYTES) {
            throw CredentialStorageException(CredentialStorageError.UNAVAILABLE)
        }
        val destination = fileFor(partition)
        try {
            writeAtomically(destination, CredentialRecord(encrypted.iv, encrypted.ciphertext).encode())
        } catch (_: IOException) {
            throw CredentialStorageException(CredentialStorageError.UNAVAILABLE)
        }
    }

    /** Returns the decrypted value together with an opaque revision for credential-bound caches. */
    @Synchronized
    fun getSnapshot(partition: SourceCredentialPartition): SourceCredentialSnapshot? {
        val source = fileFor(partition)
        if (!source.exists()) return null
        val encoded = try {
            source.readBytes()
        } catch (_: IOException) {
            throw CredentialStorageException(CredentialStorageError.UNAVAILABLE)
        }
        val record = try {
            CredentialRecord.decode(encoded)
        } catch (_: IOException) {
            invalidate(source)
            throw CredentialStorageException(CredentialStorageError.CORRUPT_OR_UNAUTHENTICATED)
        }
        val plaintext = try {
            aead.decrypt(AeadCiphertext(record.iv, record.ciphertext), partition.aad())
        } catch (failure: CredentialStorageException) {
            if (failure.error == CredentialStorageError.CORRUPT_OR_UNAUTHENTICATED) {
                invalidate(source)
            }
            throw failure
        }
        return SourceCredentialSnapshot(
            plaintext = plaintext,
            cachePartitionId = sha256(encoded),
        )
    }

    /** Returns null only for a missing partition. Confirmed corrupt records are cleared in-place. */
    fun get(partition: SourceCredentialPartition): ByteArray? = getSnapshot(partition)?.plaintext

    /** Deletes exactly one source/origin partition and leaves every other record untouched. */
    @Synchronized
    fun delete(partition: SourceCredentialPartition): Boolean {
        val target = fileFor(partition)
        if (!target.exists()) return false
        if (!target.delete()) throw CredentialStorageException(CredentialStorageError.DELETE_FAILED)
        return true
    }

    /** The credential directory is itself below [Context.noBackupFilesDir]. */
    fun noBackupDirectory(): File = directory

    private fun invalidate(file: File) {
        // Failure remains scoped to this file. Do not scan or clear adjacent source partitions.
        if (file.exists() && !file.delete()) {
            throw CredentialStorageException(CredentialStorageError.DELETE_FAILED)
        }
    }

    private fun fileFor(partition: SourceCredentialPartition): File {
        val digest = sha256("${partition.sourceId}\u0000${partition.origin.value}".toByteArray(StandardCharsets.UTF_8))
        return File(directory, "$digest.record")
    }

    private fun writeAtomically(destination: File, content: ByteArray) {
        val temporary = File(directory, ".${destination.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(content)
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private data class CredentialRecord(val iv: ByteArray, val ciphertext: ByteArray) {
        fun encode(): ByteArray = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(RECORD_MAGIC)
                output.writeShort(CREDENTIAL_SCHEMA_VERSION)
                output.writeShort(CREDENTIAL_KEY_VERSION)
                output.writeByte(iv.size)
                output.writeInt(ciphertext.size)
                output.write(iv)
                output.write(ciphertext)
            }
            bytes.toByteArray()
        }

        companion object {
            fun decode(encoded: ByteArray): CredentialRecord {
                if (encoded.size !in MIN_RECORD_BYTES..MAX_RECORD_BYTES) throw IOException("Invalid credential record")
                return DataInputStream(ByteArrayInputStream(encoded)).use { input ->
                    if (input.readInt() != RECORD_MAGIC) throw IOException("Invalid credential record")
                    if (input.readUnsignedShort() != CREDENTIAL_SCHEMA_VERSION) throw IOException("Invalid credential record")
                    if (input.readUnsignedShort() != CREDENTIAL_KEY_VERSION) throw IOException("Invalid credential record")
                    val ivLength = input.readUnsignedByte()
                    val ciphertextLength = input.readInt()
                    if (ivLength != GCM_IV_BYTES || ciphertextLength !in 16..MAX_CIPHERTEXT_BYTES) {
                        throw IOException("Invalid credential record")
                    }
                    if (encoded.size != RECORD_HEADER_BYTES + ivLength + ciphertextLength) {
                        throw IOException("Invalid credential record")
                    }
                    val iv = ByteArray(ivLength).also(input::readFully)
                    val ciphertext = ByteArray(ciphertextLength).also(input::readFully)
                    if (input.read() != -1) throw IOException("Invalid credential record")
                    CredentialRecord(iv, ciphertext)
                }
            }
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "source-credentials"
        const val RECORD_MAGIC = 0x54534352 // TSCR
        const val RECORD_HEADER_BYTES = 4 + 2 + 2 + 1 + 4
        const val MIN_RECORD_BYTES = RECORD_HEADER_BYTES + GCM_IV_BYTES + 16
        const val MAX_PLAINTEXT_BYTES = 1024 * 1024
        const val MAX_CIPHERTEXT_BYTES = MAX_PLAINTEXT_BYTES + 16
        const val MAX_RECORD_BYTES = RECORD_HEADER_BYTES + GCM_IV_BYTES + MAX_CIPHERTEXT_BYTES
    }
}

/**
 * Decrypted source credentials plus a non-secret revision derived from the randomized encrypted
 * record. The revision changes on every explicit credential write without revealing cookie bytes.
 */
class SourceCredentialSnapshot internal constructor(
    val plaintext: ByteArray,
    val cachePartitionId: String,
)

internal fun SourceCredentialPartition.aad(): ByteArray = ByteArrayOutputStream().use { bytes ->
    DataOutputStream(bytes).use { output ->
        output.writeUTF("tsuyomi-source-credentials")
        output.writeInt(CREDENTIAL_SCHEMA_VERSION)
        output.writeInt(CREDENTIAL_KEY_VERSION)
        output.writeUTF(sourceId)
        output.writeUTF(origin.value)
    }
    bytes.toByteArray()
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun isInside(parent: File, child: File): Boolean {
    val prefix = parent.path.trimEnd(File.separatorChar) + File.separatorChar
    return child.path.startsWith(prefix)
}
