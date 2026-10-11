/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.Collections
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.tsuyomi.core.publication.PublicationOriginalStore.CommittedOriginal
import org.tsuyomi.core.publication.PublicationOriginalStore.PublicationCommit
import org.tsuyomi.core.publication.PublicationOriginalStore.StagedOriginal
import org.tsuyomi.shared.backup.MAX_TRANSFER_BYTES
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.model.BookIdentity

private const val ARCHIVE_VERSION = 1
private const val MANIFEST_MAGIC = 0x54595041 // TYPA
private const val MAX_ARCHIVE_ENTRIES = 512
private const val MAX_ARCHIVE_EXPANDED_BYTES = 512L * 1024 * 1024
private const val ZIP_OVERHEAD_BYTES = 1024L * 1024
private const val MAX_ARCHIVE_FILE_BYTES = MAX_ARCHIVE_EXPANDED_BYTES + ZIP_OVERHEAD_BYTES
private const val MAX_ARCHIVE_MANIFEST_BYTES = 256L * 1024
private const val MAX_ORIGINAL_BYTES_PER_ENTRY = 32L * 1024 * 1024
private const val MAX_COMPRESSION_RATIO = 200L
private const val ARCHIVE_COPY_BUFFER_BYTES = 32 * 1024
private const val MANIFEST_PATH = "manifest.bin"
private const val TRANSFER_PATH = "transfer.json"
private const val ORIGINALS_PREFIX = "originals/"

/** Fixed archive limits; the independent v6 JSON payload keeps its own MAX_TRANSFER_BYTES bound. */
object PublicationArchiveLimits {
    const val maxEntries: Int = MAX_ARCHIVE_ENTRIES
    const val maxOriginalBytesPerEntry: Long = MAX_ORIGINAL_BYTES_PER_ENTRY
    const val maxExpandedBytes: Long = MAX_ARCHIVE_EXPANDED_BYTES
    const val maxArchiveBytes: Long = MAX_ARCHIVE_FILE_BYTES
    const val maxManifestBytes: Long = MAX_ARCHIVE_MANIFEST_BYTES
    const val maxCompressionRatio: Long = MAX_COMPRESSION_RATIO
}

/** One original selected by the parent from a v6 backup snapshot. */
data class PublicationArchiveOriginal(
    val publicationId: String,
    val format: LocalPublicationFormat,
    val sha256: String,
    val sizeBytes: Long,
) {
    init {
        BookIdentity.Local(publicationId)
        require(sha256.matches(Regex("^[0-9a-f]{64}$"))) { "Invalid archive original SHA-256" }
        require(sizeBytes in 1..MAX_ORIGINAL_BYTES_PER_ENTRY) { "Invalid archive original size" }
    }

    internal val entryPath: String get() = "$ORIGINALS_PREFIX$publicationId.bin"
}

enum class PublicationArchiveError {
    INVALID_ZIP,
    UNSUPPORTED_VERSION,
    INVALID_MANIFEST,
    UNSAFE_ENTRY,
    DUPLICATE_ENTRY,
    EXTRA_ENTRY,
    MISSING_ENTRY,
    UNSUPPORTED_COMPRESSION,
    ENTRY_LIMIT,
    EXPANDED_SIZE_LIMIT,
    ARCHIVE_SIZE_LIMIT,
    COMPRESSION_RATIO_LIMIT,
    CHECKSUM_MISMATCH,
    STORAGE_CONFLICT,
}

class PublicationArchiveException(
    val reason: PublicationArchiveError,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

data class PublicationArchiveSummary(
    val transferSha256: String,
    val transferSizeBytes: Long,
    val originals: List<PublicationArchiveOriginal>,
)

internal data class PublicationArchiveManifest(
    val transferSha256: String,
    val transferSizeBytes: Long,
    val originals: List<PublicationArchiveOriginal>,
)
class PublicationOriginalArchive(private val store: PublicationOriginalStore) {
    /** Writes the exact shared-backup v6 JSON bytes plus a closed manifest and selected original bytes. */
    suspend fun export(
        transferJson: ByteArray,
        originals: Collection<PublicationArchiveOriginal>,
        output: OutputStream,
    ): PublicationArchiveSummary {
        if (transferJson.isEmpty() || transferJson.size > MAX_TRANSFER_BYTES) {
            throw PublicationArchiveException(PublicationArchiveError.ARCHIVE_SIZE_LIMIT, "Transfer JSON exceeds its 32 MiB bound")
        }
        val ordered = originals.sortedBy(PublicationArchiveOriginal::publicationId)
        if (ordered.size + 2 > MAX_ARCHIVE_ENTRIES) {
            throw PublicationArchiveException(PublicationArchiveError.ENTRY_LIMIT, "Original archive has too many entries")
        }
        if (ordered.map(PublicationArchiveOriginal::publicationId).distinct().size != ordered.size) {
            throw PublicationArchiveException(PublicationArchiveError.INVALID_MANIFEST, "Duplicate publication ID in archive selection")
        }
        val context = currentCoroutineContext()
        val sources = ArrayList<CommittedOriginal>(ordered.size)
        var expanded = transferJson.size.toLong()
        for (descriptor in ordered) {
            context.ensureActive()
            val source = store.originalForArchive(descriptor.publicationId)
                ?: throw PublicationArchiveException(PublicationArchiveError.STORAGE_CONFLICT, "Selected original is unavailable")
            if (source.format != descriptor.format || source.sha256 != descriptor.sha256 || source.sizeBytes != descriptor.sizeBytes) {
                throw PublicationArchiveException(PublicationArchiveError.STORAGE_CONFLICT, "Selected original does not match v6 metadata")
            }
            if (descriptor.sizeBytes > MAX_ARCHIVE_EXPANDED_BYTES - expanded) {
                throw PublicationArchiveException(PublicationArchiveError.EXPANDED_SIZE_LIMIT, "Original archive exceeds its expanded-byte limit")
            }
            sources += source
            expanded += descriptor.sizeBytes
        }
        val transferSha256 = sha256(transferJson)
        val manifest = encodeManifest(transferSha256, transferJson.size.toLong(), ordered)
        if (manifest.size.toLong() > MAX_ARCHIVE_MANIFEST_BYTES || manifest.size.toLong() > MAX_ARCHIVE_EXPANDED_BYTES - expanded) {
            throw PublicationArchiveException(PublicationArchiveError.EXPANDED_SIZE_LIMIT, "Original archive manifest exceeds its bound")
        }
        expanded += manifest.size

        val bounded = BoundedOutputStream(NonClosingOutputStream(output), MAX_ARCHIVE_FILE_BYTES)
        ZipOutputStream(bounded, StandardCharsets.UTF_8).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            zip.putNextEntry(ZipEntry(MANIFEST_PATH).apply { time = 0L })
            zip.write(manifest)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(TRANSFER_PATH).apply { time = 0L })
            writeCancellable(zip, transferJson)
            zip.closeEntry()
            for (index in ordered.indices) {
                context.ensureActive()
                val descriptor = ordered[index]
                zip.putNextEntry(ZipEntry(descriptor.entryPath).apply { time = 0L })
                sources[index].openInputStream().use { copyVerifiedOriginal(it, zip, descriptor) }
                zip.closeEntry()
            }
        }
        context.ensureActive()
        return PublicationArchiveSummary(transferSha256, transferJson.size.toLong(), ordered)
    }

    /** Spools, validates every ZIP entry and digest, and exposes only a pathless inspection handle. */
    suspend fun inspect(input: InputStream): PublicationArchiveInspection {
        val jobId = UUID.randomUUID().toString()
        val directory = store.createArchiveStagingDirectory(jobId)
        val archiveFile = File(directory, "archive.zip")
        try {
            val (_, archiveSize) = store.copyArchiveInput(input, archiveFile, MAX_ARCHIVE_FILE_BYTES)
            if (archiveSize == 0L) throw PublicationArchiveException(PublicationArchiveError.INVALID_ZIP, "Original archive is empty")
            val manifest = inspectZip(archiveFile)
            return PublicationArchiveInspection(this, store, directory, archiveFile, manifest)
        } catch (error: Throwable) {
            store.deleteArchiveStagingDirectory(directory)
            throw error
        }
    }

    /**
     * Imports originals before invoking the parent’s single v6 metadata transaction. Existing
     * same-ID/different-fingerprint originals are reported and never replaced. A false callback
     * result rolls back only this operation’s newly promoted files.
     */
    suspend fun restore(
        inspection: PublicationArchiveInspection,
        catalog: Map<String, OriginalCatalogState>,
        applyTransferMetadata: suspend (
            transferJson: InputStream,
            originals: List<PublicationArchiveOriginalResult>,
        ) -> Boolean,
    ): PublicationArchiveRestoreResult {
        inspection.requireUsable(this)
        catalog.keys.forEach { BookIdentity.Local(it) }
        val receipts = ArrayList<PublicationCommit>()
        val statuses = ArrayList<PublicationArchiveOriginalResult>(inspection.originals.size)
        var metadataApplyStarted = false
        try {
            ZipFile(inspection.archiveFile).use { zip ->
                val context = currentCoroutineContext()
                for (descriptor in inspection.originals) {
                    context.ensureActive()
                    val existing = catalog[descriptor.publicationId]
                    if (existing != null && !existing.matches(descriptor)) {
                        statuses += PublicationArchiveOriginalResult(descriptor, PublicationArchiveOriginalStatus.CONFLICT)
                        continue
                    }
                    val current = store.original(descriptor.publicationId)
                    if (current != null) {
                        if (existing != null && existing.matches(descriptor) && matches(current, descriptor)) {
                            statuses += PublicationArchiveOriginalResult(descriptor, PublicationArchiveOriginalStatus.ALREADY_PRESENT)
                        } else {
                            statuses += PublicationArchiveOriginalResult(descriptor, PublicationArchiveOriginalStatus.CONFLICT)
                        }
                        continue
                    }
                    if (store.originalSlotOccupied(descriptor.publicationId)) {
                        statuses += PublicationArchiveOriginalResult(descriptor, PublicationArchiveOriginalStatus.CONFLICT)
                        continue
                    }
                    val entry = zip.getEntry(descriptor.entryPath)
                        ?: throw PublicationArchiveException(PublicationArchiveError.MISSING_ENTRY, "Manifest original entry is missing")
                    val entryInput = zip.getInputStream(entry)
                    val staged = try {
                        store.stageArchiveOriginal(entryInput, descriptor)
                    } finally {
                        entryInput.close()
                    }
                    try {
                        val commit = store.commitArchiveOriginal(staged, descriptor, existing)
                        receipts += commit
                        statuses += PublicationArchiveOriginalResult(descriptor, PublicationArchiveOriginalStatus.RESTORED)
                    } finally {
                        staged.close()
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            val metadataApplied = inspection.openTransferPayload().use { transferJson ->
                metadataApplyStarted = true
                applyTransferMetadata(transferJson, statuses.toList())
            }
            if (!metadataApplied) {
                val rolledBack = HashSet<String>()
                var finalizationPending = false
                for (receipt in receipts.asReversed()) {
                    if (receipt.rollbackUncatalogued()) rolledBack += receipt.original.publicationId
                    else finalizationPending = true
                }
                return PublicationArchiveRestoreResult(
                    metadataApplied = false,
                    originals = statuses.map { result ->
                        if (result.status == PublicationArchiveOriginalStatus.RESTORED &&
                            result.original.publicationId in rolledBack
                        ) {
                            result.copy(status = PublicationArchiveOriginalStatus.ROLLED_BACK)
                        } else result
                    },
                    finalizationPending = finalizationPending,
                )
            }
            val pending = receipts.any { !it.confirmCatalogCommit() }
            return PublicationArchiveRestoreResult(true, statuses.toList(), pending)
        } catch (error: Throwable) {
            if (!metadataApplyStarted) receipts.asReversed().forEach(PublicationCommit::rollbackUncatalogued)
            throw error
        }
    }

    private suspend fun inspectZip(file: File): PublicationArchiveManifest {
        val zip = try {
            ZipFile(file)
        } catch (error: IOException) {
            throw PublicationArchiveException(PublicationArchiveError.INVALID_ZIP, "Unable to open original archive ZIP", error)
        }
        zip.use { archive ->
            val names = LinkedHashMap<String, ZipEntry>()
            var expandedBytes = 0L
            val enumeration = archive.entries()
            val context = currentCoroutineContext()
            while (enumeration.hasMoreElements()) {
                context.ensureActive()
                val entry = enumeration.nextElement()
                if (names.size >= MAX_ARCHIVE_ENTRIES) {
                    throw PublicationArchiveException(PublicationArchiveError.ENTRY_LIMIT, "Original archive has too many ZIP entries")
                }
                if (entry.isDirectory || !isPermittedEntryName(entry.name)) {
                    throw PublicationArchiveException(PublicationArchiveError.UNSAFE_ENTRY, "Original archive contains an unsafe or unexpected entry")
                }
                if (names.put(entry.name, entry) != null) {
                    throw PublicationArchiveException(PublicationArchiveError.DUPLICATE_ENTRY, "Original archive contains a duplicate entry")
                }
                if (entry.method != ZipEntry.STORED && entry.method != ZipEntry.DEFLATED) {
                    throw PublicationArchiveException(PublicationArchiveError.UNSUPPORTED_COMPRESSION, "Original archive uses unsupported ZIP compression")
                }
                val entryLimit = when (entry.name) {
                    MANIFEST_PATH -> MAX_ARCHIVE_MANIFEST_BYTES
                    TRANSFER_PATH -> MAX_TRANSFER_BYTES.toLong()
                    else -> MAX_ORIGINAL_BYTES_PER_ENTRY
                }
                if (entry.size !in 1..entryLimit || entry.compressedSize <= 0L ||
                    (entry.method == ZipEntry.STORED && entry.size != entry.compressedSize)
                ) {
                    throw PublicationArchiveException(PublicationArchiveError.ENTRY_LIMIT, "Original archive entry has an invalid size")
                }
                if (entry.size > entry.compressedSize * MAX_COMPRESSION_RATIO) {
                    throw PublicationArchiveException(PublicationArchiveError.COMPRESSION_RATIO_LIMIT, "Original archive entry exceeds the compression-ratio limit")
                }
                if (entry.size > MAX_ARCHIVE_EXPANDED_BYTES - expandedBytes) {
                    throw PublicationArchiveException(PublicationArchiveError.EXPANDED_SIZE_LIMIT, "Original archive exceeds its expanded-byte limit")
                }
                expandedBytes += entry.size
            }
            val manifestEntry = names[MANIFEST_PATH]
                ?: throw PublicationArchiveException(PublicationArchiveError.MISSING_ENTRY, "Original archive manifest is missing")
            val manifestBytes = archive.getInputStream(manifestEntry).use { readBounded(it, MAX_ARCHIVE_MANIFEST_BYTES) }
            val manifest = decodeManifest(manifestBytes)
            val expectedNames = buildSet {
                add(MANIFEST_PATH)
                add(TRANSFER_PATH)
                manifest.originals.forEach { add(it.entryPath) }
            }
            if (names.keys != expectedNames) {
                val extra = names.keys - expectedNames
                val missing = expectedNames - names.keys
                val reason = if (extra.isNotEmpty()) PublicationArchiveError.EXTRA_ENTRY else PublicationArchiveError.MISSING_ENTRY
                throw PublicationArchiveException(reason, "Original archive entries do not exactly match the manifest")
            }
            val transferEntry = names[TRANSFER_PATH]
                ?: throw PublicationArchiveException(PublicationArchiveError.MISSING_ENTRY, "Transfer JSON is missing")
            if (transferEntry.size != manifest.transferSizeBytes ||
                digestEntry(archive, transferEntry, MAX_TRANSFER_BYTES.toLong()) !=
                (manifest.transferSha256 to manifest.transferSizeBytes)
            ) {
                throw PublicationArchiveException(PublicationArchiveError.CHECKSUM_MISMATCH, "Transfer JSON does not match the archive manifest")
            }
            for (descriptor in manifest.originals) {
                val entry = names[descriptor.entryPath]
                    ?: throw PublicationArchiveException(PublicationArchiveError.MISSING_ENTRY, "Manifest original entry is missing")
                if (entry.size != descriptor.sizeBytes ||
                    digestEntry(archive, entry, MAX_ORIGINAL_BYTES_PER_ENTRY) != (descriptor.sha256 to descriptor.sizeBytes)
                ) {
                    throw PublicationArchiveException(PublicationArchiveError.CHECKSUM_MISMATCH, "Original bytes do not match the archive manifest")
                }
            }
            return manifest
        }
    }

    private fun encodeManifest(
        transferSha256: String,
        transferSizeBytes: Long,
        originals: List<PublicationArchiveOriginal>,
    ): ByteArray {
        val bytes = ByteArrayOutputStream(minOf(128 * 1024, 64 + originals.size * 160))
        DataOutputStream(bytes).use { output ->
            output.writeInt(MANIFEST_MAGIC)
            output.writeInt(ARCHIVE_VERSION)
            output.writeUTF(TRANSFER_PATH)
            output.writeLong(transferSizeBytes)
            output.writeUTF(transferSha256)
            output.writeInt(originals.size)
            originals.forEach { descriptor ->
                output.writeUTF(descriptor.publicationId)
                output.writeUTF(descriptor.format.name)
                output.writeLong(descriptor.sizeBytes)
                output.writeUTF(descriptor.sha256)
                output.writeUTF(descriptor.entryPath)
            }
        }
        return bytes.toByteArray()
    }

    private fun decodeManifest(bytes: ByteArray): PublicationArchiveManifest {
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MANIFEST_MAGIC) throw PublicationArchiveException(PublicationArchiveError.INVALID_MANIFEST, "Invalid original archive manifest")
                if (input.readInt() != ARCHIVE_VERSION) throw PublicationArchiveException(PublicationArchiveError.UNSUPPORTED_VERSION, "Unsupported original archive version")
                if (input.readUTF() != TRANSFER_PATH) throw PublicationArchiveException(PublicationArchiveError.INVALID_MANIFEST, "Invalid transfer entry name")
                val transferSize = input.readLong()
                val transferSha = input.readUTF()
                if (transferSize !in 1..MAX_TRANSFER_BYTES.toLong() || !isSha256(transferSha)) {
                    throw PublicationArchiveException(PublicationArchiveError.INVALID_MANIFEST, "Invalid transfer fingerprint in archive manifest")
                }
                val count = input.readInt()
                if (count < 0 || count + 2 > MAX_ARCHIVE_ENTRIES) {
                    throw PublicationArchiveException(PublicationArchiveError.ENTRY_LIMIT, "Original archive manifest has too many entries")
                }
                val ids = HashSet<String>(count)
                val originals = ArrayList<PublicationArchiveOriginal>(count)
                repeat(count) {
                    val publicationId = input.readUTF()
                    val formatName = input.readUTF()
                    val size = input.readLong()
                    val sha = input.readUTF()
                    val entryPath = input.readUTF()
                    if (canonicalUuidOrNull(publicationId) == null || !ids.add(publicationId) || !isSha256(sha) ||
                        size !in 1..MAX_ORIGINAL_BYTES_PER_ENTRY || entryPath != "$ORIGINALS_PREFIX$publicationId.bin"
                    ) {
                        throw PublicationArchiveException(PublicationArchiveError.INVALID_MANIFEST, "Invalid original identity, fingerprint, or entry path")
                    }
                    val format = runCatching { LocalPublicationFormat.valueOf(formatName) }.getOrElse {
                        throw PublicationArchiveException(PublicationArchiveError.INVALID_MANIFEST, "Unknown local publication format", it)
                    }
                    originals += PublicationArchiveOriginal(publicationId, format, sha, size)
                }
                if (input.read() != -1) throw PublicationArchiveException(PublicationArchiveError.INVALID_MANIFEST, "Unexpected trailing archive manifest data")
                return PublicationArchiveManifest(transferSha, transferSize, originals)
            }
        } catch (error: PublicationArchiveException) {
            throw error
        } catch (error: Exception) {
            throw PublicationArchiveException(PublicationArchiveError.INVALID_MANIFEST, "Unable to decode original archive manifest", error)
        }
    }

    private suspend fun digestEntry(zip: ZipFile, entry: ZipEntry, maximumBytes: Long): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val context = currentCoroutineContext()
        var total = 0L
        val buffer = ByteArray(ARCHIVE_COPY_BUFFER_BYTES)
        zip.getInputStream(entry).use { input ->
            while (true) {
                context.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (count.toLong() > maximumBytes - total) {
                    throw PublicationArchiveException(PublicationArchiveError.ENTRY_LIMIT, "Expanded ZIP entry exceeds its bound")
                }
                digest.update(buffer, 0, count)
                total += count
            }
        }
        return digest.digest().toHex() to total
    }

    private suspend fun readBounded(input: InputStream, maximumBytes: Long): ByteArray {
        val output = ByteArrayOutputStream(minOf(maximumBytes.toInt(), 32 * 1024))
        val context = currentCoroutineContext()
        val buffer = ByteArray(ARCHIVE_COPY_BUFFER_BYTES)
        var total = 0L
        while (true) {
            context.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count.toLong() > maximumBytes - total) {
                throw PublicationArchiveException(PublicationArchiveError.ENTRY_LIMIT, "Manifest exceeds its bound")
            }
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }

    private suspend fun copyVerifiedOriginal(input: InputStream, output: OutputStream, descriptor: PublicationArchiveOriginal) {
        val digest = MessageDigest.getInstance("SHA-256")
        val context = currentCoroutineContext()
        val buffer = ByteArray(ARCHIVE_COPY_BUFFER_BYTES)
        var total = 0L
        while (true) {
            context.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count.toLong() > descriptor.sizeBytes - total) {
                throw PublicationArchiveException(PublicationArchiveError.CHECKSUM_MISMATCH, "Original grew during archive export")
            }
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
            total += count
        }
        if (total != descriptor.sizeBytes || digest.digest().toHex() != descriptor.sha256) {
            throw PublicationArchiveException(PublicationArchiveError.CHECKSUM_MISMATCH, "Original changed during archive export")
        }
    }

    private fun isPermittedEntryName(name: String): Boolean = when (name) {
        MANIFEST_PATH, TRANSFER_PATH -> true
        else -> {
            if (!name.startsWith(ORIGINALS_PREFIX) || !name.endsWith(".bin")) return false
            val id = name.removePrefix(ORIGINALS_PREFIX).removeSuffix(".bin")
            canonicalUuidOrNull(id) != null && name == "$ORIGINALS_PREFIX$id.bin"
        }
    }

    private fun isSha256(value: String): Boolean = value.matches(Regex("^[0-9a-f]{64}$"))

    private suspend fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val context = currentCoroutineContext()
        var offset = 0
        while (offset < bytes.size) {
            context.ensureActive()
            val count = minOf(ARCHIVE_COPY_BUFFER_BYTES, bytes.size - offset)
            digest.update(bytes, offset, count)
            offset += count
        }
        return digest.digest().toHex()
    }

    private suspend fun writeCancellable(output: OutputStream, bytes: ByteArray) {
        val context = currentCoroutineContext()
        var offset = 0
        while (offset < bytes.size) {
            context.ensureActive()
            val count = minOf(ARCHIVE_COPY_BUFFER_BYTES, bytes.size - offset)
            output.write(bytes, offset, count)
            offset += count
        }
    }


    private class BoundedOutputStream(
        output: OutputStream,
        private val maximumBytes: Long,
    ) : OutputStream() {
        private val delegate = output
        private var total = 0L

        override fun write(value: Int) {
            if (total >= maximumBytes) throw PublicationArchiveException(PublicationArchiveError.ARCHIVE_SIZE_LIMIT, "Original archive exceeds its byte limit")
            delegate.write(value)
            total++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (length.toLong() > maximumBytes - total) {
                throw PublicationArchiveException(PublicationArchiveError.ARCHIVE_SIZE_LIMIT, "Original archive exceeds its byte limit")
            }
            delegate.write(buffer, offset, length)
            total += length
        }

        override fun flush() = delegate.flush()
        override fun close() = delegate.close()
    }

    private class NonClosingOutputStream(output: OutputStream) : OutputStream() {
        private val delegate = output
        override fun write(value: Int) = delegate.write(value)
        override fun write(buffer: ByteArray, offset: Int, length: Int) = delegate.write(buffer, offset, length)
        override fun flush() = delegate.flush()
        override fun close() = delegate.flush()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.matches(other: PublicationArchiveOriginal): Boolean =
        this == other.sha256 && length.toLong() == other.sizeBytes

    private fun OriginalCatalogState.matches(descriptor: PublicationArchiveOriginal): Boolean =
        format == descriptor.format && sha256 == descriptor.sha256 && sizeBytes == descriptor.sizeBytes

    private fun matches(original: CommittedOriginal, descriptor: PublicationArchiveOriginal): Boolean =
        original.format == descriptor.format && original.sha256 == descriptor.sha256 && original.sizeBytes == descriptor.sizeBytes

    private fun canonicalUuidOrNull(value: String): String? =
        runCatching { UUID.fromString(value).toString().takeIf { it == value } }.getOrNull()
}

enum class PublicationArchiveOriginalStatus { RESTORED, ALREADY_PRESENT, CONFLICT, ROLLED_BACK }

data class PublicationArchiveOriginalResult(
    val original: PublicationArchiveOriginal,
    val status: PublicationArchiveOriginalStatus,
)

data class PublicationArchiveRestoreResult(
    val metadataApplied: Boolean,
    val originals: List<PublicationArchiveOriginalResult>,
    val finalizationPending: Boolean,
)

/** Opaque validated archive handle. Its ZIP path stays private to the owner implementation. */
class PublicationArchiveInspection internal constructor(
    private val owner: PublicationOriginalArchive,
    private val store: PublicationOriginalStore,
    private val directory: File,
    internal val archiveFile: File,
    private val manifest: PublicationArchiveManifest,
) : Closeable {
    private var closed = false
    val originals: List<PublicationArchiveOriginal> = Collections.unmodifiableList(ArrayList(manifest.originals))
    val transferSha256: String get() = manifest.transferSha256
    val transferSizeBytes: Long get() = manifest.transferSizeBytes


    fun openTransferPayload(): InputStream {
        check(!closed) { "Archive inspection is closed" }
        val zip = ZipFile(archiveFile)
        val entry = zip.getEntry(TRANSFER_PATH)
            ?: run { zip.close(); throw PublicationArchiveException(PublicationArchiveError.MISSING_ENTRY, "Transfer JSON is missing") }
        val input = try {
            zip.getInputStream(entry)
        } catch (error: Throwable) {
            zip.close()
            throw error
        }
        return object : FilterInputStream(input) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    zip.close()
                }
            }
        }
    }

    internal fun requireUsable(expectedOwner: PublicationOriginalArchive) {
        check(!closed) { "Archive inspection is closed" }
        require(owner === expectedOwner) { "Archive inspection belongs to another archive owner" }
        check(archiveFile.isFile) { "Archive inspection bytes are no longer available" }
    }

    override fun close() {
        if (closed) return
        closed = true
        store.deleteArchiveStagingDirectory(directory)
    }
}
