/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties
import org.tsuyomi.shared.backup.MAX_TRANSFER_BYTES
import org.tsuyomi.shared.backup.TransferCodec

internal data class ExportPreflightOwnership(
    val ownerGeneration: Long,
    val canonicalDigest: String,
)

internal class TransferExportPreflightStore(
    private val directory: File,
) {
    private val metadataFile = File(directory, "active.properties")
    private var lastGeneration = 0L

    fun prepare(bytes: ByteArray): PreparedExport {
        require(bytes.size <= MAX_TRANSFER_BYTES)
        require(directory.isDirectory || directory.mkdirs())
        val previous = currentOwnership()
        previous?.let(::clearOwned)
        val ownerGeneration = maxOf(lastGeneration, previous?.ownerGeneration ?: 0L) + 1L
        val canonicalDigest = TransferCodec.digest(bytes)
        val fileName = fileName(ownerGeneration, canonicalDigest)
        val target = checkedPath(fileName)
        val temporary = File(directory, "$fileName.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        require(temporary.renameTo(target)) { "Unable to commit export preflight" }
        val ownership = ExportPreflightOwnership(ownerGeneration, canonicalDigest)
        writeMetadata(ownership)
        lastGeneration = ownerGeneration
        return PreparedExport("tsuyomi-${FILE_DATE.format(Instant.now())}.json", ownerGeneration, canonicalDigest)
    }

    fun verifiedFile(ownership: ExportPreflightOwnership): File? {
        if (currentOwnership() != ownership) return null
        val file = checkedPath(fileName(ownership.ownerGeneration, ownership.canonicalDigest))
        if (!file.isFile || file.length() > MAX_TRANSFER_BYTES.toLong()) return null
        return file.takeIf { TransferCodec.digest(it.readBytes()) == ownership.canonicalDigest }
    }

    fun clearIfOwned(ownership: ExportPreflightOwnership): Boolean =
        (currentOwnership() == ownership).also { if (it) clearOwned(ownership) }

    fun sweepOrphans() {
        if (!directory.isDirectory) return
        val active = currentOwnership()
        directory.listFiles()?.forEach { listedFile ->
            val match = PREFLIGHT_FILE.matchEntire(listedFile.name) ?: return@forEach
            val file = runCatching { checkedPath(listedFile.name) }.getOrNull() ?: return@forEach
            val ownership = ExportPreflightOwnership(match.groupValues[1].toLong(), match.groupValues[2])
            if (ownership != active && file.isFile && file.length() <= MAX_TRANSFER_BYTES.toLong() &&
                TransferCodec.digest(file.readBytes()) == ownership.canonicalDigest
            ) {
                file.delete()
            }
        }
    }

    private fun currentOwnership(): ExportPreflightOwnership? = runCatching {
        if (!metadataFile.isFile) return@runCatching null
        val properties = Properties().apply { FileInputStream(metadataFile).use { input -> load(input) } }
        val ownerGeneration = properties.getProperty("ownerGeneration")?.toLongOrNull()?.takeIf { it > 0L }
            ?: return@runCatching null
        val canonicalDigest = properties.getProperty("canonicalDigest")?.takeIf { DIGEST.matches(it) }
            ?: return@runCatching null
        val fileName = properties.getProperty("fileName") ?: return@runCatching null
        require(fileName == fileName(ownerGeneration, canonicalDigest))
        checkedPath(fileName)
        ExportPreflightOwnership(ownerGeneration, canonicalDigest)
    }.getOrNull()

    private fun writeMetadata(ownership: ExportPreflightOwnership) {
        val temporary = File(directory, "active.tmp")
        Properties().apply {
            setProperty("ownerGeneration", ownership.ownerGeneration.toString())
            setProperty("canonicalDigest", ownership.canonicalDigest)
            setProperty("fileName", fileName(ownership.ownerGeneration, ownership.canonicalDigest))
            FileOutputStream(temporary).use { output ->
                store(output, null)
                output.fd.sync()
            }
        }
        if (metadataFile.exists()) require(metadataFile.delete()) { "Unable to replace export metadata" }
        require(temporary.renameTo(metadataFile)) { "Unable to commit export metadata" }
    }

    private fun checkedPath(fileName: String): File {
        require(directory.isDirectory || directory.mkdirs())
        val canonicalDirectory = directory.canonicalFile
        val file = File(canonicalDirectory, fileName).canonicalFile
        require(file.parentFile == canonicalDirectory && file.name == fileName) { "Invalid export preflight path" }
        return file
    }

    private fun clearOwned(ownership: ExportPreflightOwnership) {
        if (currentOwnership() != ownership) return
        val file = checkedPath(fileName(ownership.ownerGeneration, ownership.canonicalDigest))
        if (file.exists() && verifiedFile(ownership) != null) file.delete()
        if (!file.exists()) metadataFile.delete()
    }

    private fun fileName(ownerGeneration: Long, canonicalDigest: String): String =
        "preflight-$ownerGeneration-$canonicalDigest.json"

    private companion object {
        val DIGEST = Regex("[0-9a-f]{64}")
        val PREFLIGHT_FILE = Regex("preflight-([1-9][0-9]*)-([0-9a-f]{64})\\.json")
        val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    }
}
