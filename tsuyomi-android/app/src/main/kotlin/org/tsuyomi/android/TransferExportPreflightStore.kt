/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import org.tsuyomi.core.publication.PublicationArchiveLimits
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties
import org.tsuyomi.shared.backup.MAX_TRANSFER_BYTES
import org.tsuyomi.shared.backup.TransferCodec

internal data class ExportPreflightOwnership(
    val ownerGeneration: Long,
    val canonicalDigest: String,
    val includesOriginals: Boolean = false,
)

internal class TransferExportPreflightStore(
    private val directory: File,
) {
    private val metadataFile = File(directory, "active.properties")
    private var lastGeneration = 0L

    @Synchronized
    fun prepare(bytes: ByteArray): PreparedExport {
        require(bytes.size <= MAX_TRANSFER_BYTES)
        val temporary = createStagingFile()
        try {
            FileOutputStream(temporary).use { output -> output.write(bytes); output.fd.sync() }
            return adopt(temporary, TransferCodec.digest(bytes), includesOriginals = false)
        } finally { temporary.delete() }
    }

    @Synchronized
    fun createStagingFile(): File {
        require(directory.isDirectory || directory.mkdirs())
        return File.createTempFile("preparing-", ".tmp", directory)
    }

    @Synchronized
    fun prepareArchive(temporary: File): PreparedExport {
        require(temporary.canonicalFile.parentFile == directory.canonicalFile && temporary.name.startsWith("preparing-"))
        require(temporary.length() in 1..PublicationArchiveLimits.maxArchiveBytes)
        return adopt(temporary, fileDigest(temporary), includesOriginals = true)
    }

    private fun adopt(temporary: File, digest: String, includesOriginals: Boolean): PreparedExport {
        val previous = currentOwnership()
        val baseGeneration = maxOf(lastGeneration, previous?.ownerGeneration ?: 0L)
        require(baseGeneration < Long.MAX_VALUE)
        val ownership = ExportPreflightOwnership(baseGeneration + 1L, digest, includesOriginals)
        previous?.let(::clearOwned)
        require(temporary.renameTo(checkedPath(fileName(ownership.ownerGeneration, digest, includesOriginals)))) {
            "Unable to commit export preflight"
        }
        writeMetadata(ownership)
        lastGeneration = ownership.ownerGeneration
        return prepared(ownership)
    }

    @Synchronized
    fun restore(ownership: ExportPreflightOwnership): PreparedExport? {
        if (!ownership.isValid()) return null
        lastGeneration = maxOf(lastGeneration, ownership.ownerGeneration)
        return verifiedFile(ownership)?.let { prepared(ownership) }
    }

    @Synchronized
    fun verifiedFile(ownership: ExportPreflightOwnership): File? {
        if (currentOwnership() != ownership) return null
        val file = checkedPath(fileName(ownership.ownerGeneration, ownership.canonicalDigest, ownership.includesOriginals))
        if (!file.isFile || file.length() > ownership.maximumBytes()) return null
        return file.takeIf { fileDigest(it) == ownership.canonicalDigest }
    }

    @Synchronized
    fun consumeVerifiedFile(ownership: ExportPreflightOwnership, write: (File) -> Boolean): Boolean? {
        val source = verifiedFile(ownership) ?: return null
        return try {
            write(source)
        } finally {
            clearOwned(ownership)
        }
    }

    @Synchronized
    fun clearIfOwned(ownership: ExportPreflightOwnership): Boolean =
        (currentOwnership() == ownership).also { if (it) clearOwned(ownership) }

    @Synchronized
    fun sweepOrphans() {
        if (!directory.isDirectory) return
        val active = currentOwnership()
        directory.listFiles()?.forEach { listedFile ->
            if (listedFile.name.startsWith("preparing-") && listedFile.name.endsWith(".tmp")) {
                checkedPath(listedFile.name).delete()
                return@forEach
            }
            val match = PREFLIGHT_FILE.matchEntire(listedFile.name) ?: return@forEach
            val file = runCatching { checkedPath(listedFile.name) }.getOrNull() ?: return@forEach
            val ownership = ExportPreflightOwnership(match.groupValues[1].toLong(), match.groupValues[2], match.groupValues[3] == "zip")
            if (ownership != active && file.isFile && file.length() <= ownership.maximumBytes() &&
                fileDigest(file) == ownership.canonicalDigest
            ) {
                file.delete()
            }
        }
    }

    private fun currentOwnership(): ExportPreflightOwnership? = runCatching {
        if (!metadataFile.isFile) return@runCatching null
        val properties = Properties().apply { FileInputStream(metadataFile).use { input -> load(input) } }
        val ownerGeneration = properties.getProperty("ownerGeneration")?.toLongOrNull()?.takeIf { it in 1 until Long.MAX_VALUE }
            ?: return@runCatching null
        val canonicalDigest = properties.getProperty("canonicalDigest")?.takeIf { DIGEST.matches(it) }
            ?: return@runCatching null
        val includesOriginals = properties.getProperty("includesOriginals", "false").toBooleanStrict()
        val fileName = properties.getProperty("fileName") ?: return@runCatching null
        require(fileName == fileName(ownerGeneration, canonicalDigest, includesOriginals))
        checkedPath(fileName)
        ExportPreflightOwnership(ownerGeneration, canonicalDigest, includesOriginals)
    }.getOrNull()

    private fun writeMetadata(ownership: ExportPreflightOwnership) {
        val temporary = File(directory, "active.tmp")
        Properties().apply {
            setProperty("ownerGeneration", ownership.ownerGeneration.toString())
            setProperty("canonicalDigest", ownership.canonicalDigest)
            setProperty("includesOriginals", ownership.includesOriginals.toString())
            setProperty("fileName", fileName(ownership.ownerGeneration, ownership.canonicalDigest, ownership.includesOriginals))
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
        val file = checkedPath(fileName(ownership.ownerGeneration, ownership.canonicalDigest, ownership.includesOriginals))
        if (file.isFile) file.delete()
        metadataFile.delete()
    }

    private fun prepared(ownership: ExportPreflightOwnership): PreparedExport = PreparedExport(
        suggestedFileName = "tsuyomi-${FILE_DATE.format(Instant.now())}.${if (ownership.includesOriginals) "zip" else "json"}",
        ownerGeneration = ownership.ownerGeneration,
        canonicalDigest = ownership.canonicalDigest,
        includesOriginals = ownership.includesOriginals,
    )

    private fun ExportPreflightOwnership.isValid(): Boolean =
        ownerGeneration in 1 until Long.MAX_VALUE && DIGEST.matches(canonicalDigest)

    private fun fileName(ownerGeneration: Long, canonicalDigest: String, includesOriginals: Boolean): String =
        "preflight-$ownerGeneration-$canonicalDigest.${if (includesOriginals) "zip" else "json"}"

    private fun ExportPreflightOwnership.maximumBytes(): Long =
        if (includesOriginals) PublicationArchiveLimits.maxArchiveBytes else MAX_TRANSFER_BYTES.toLong()

    private fun fileDigest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        file.inputStream().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private companion object {
        val DIGEST = Regex("[0-9a-f]{64}")
        val PREFLIGHT_FILE = Regex("preflight-([1-9][0-9]*)-([0-9a-f]{64})\\.(json|zip)")
        val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    }
}
