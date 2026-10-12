/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.zip.ZipFile
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val ORIGINAL_MAX_BYTES = 32L * 1024 * 1024
private const val STORE_MAX_BYTES = 512L * 1024 * 1024
private const val RESOURCE_MAX_BYTES = 8L * 1024 * 1024
private const val RESOURCES_PER_PUBLICATION_MAX_BYTES = 32L * 1024 * 1024
private const val RESOURCES_PER_PUBLICATION_MAX_ENTRIES = 512
private const val COPY_BUFFER_BYTES = 32 * 1024
private const val STORE_MAGIC = 0x54595053 // TYPS
private const val STORE_VERSION = 1
private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
private val SUPPORTED_RESOURCE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

/** Fixed, non-user-overridable admission bounds; P0 parser-specific budgets remain independent. */
object PublicationStorageLimits {
    const val maxOriginalBytes: Long = ORIGINAL_MAX_BYTES
    const val maxOwnedBytes: Long = STORE_MAX_BYTES
    const val maxResourceBytes: Long = RESOURCE_MAX_BYTES
    const val maxResourcesPerPublicationBytes: Long = RESOURCES_PER_PUBLICATION_MAX_BYTES
    const val maxResourcesPerPublication: Int = RESOURCES_PER_PUBLICATION_MAX_ENTRIES
    const val maxAdmissionRecordBytes: Long = PublicationAdmissionCodec.MAX_BYTES
}

enum class PublicationStorageLimit {
    ORIGINAL_BYTES,
    RESOURCE_BYTES,
    PUBLICATION_RESOURCE_BYTES,
    OWNED_STORAGE_BYTES,
    ADMISSION_RECORD_BYTES,
}

class PublicationLimitException(
    val limit: PublicationStorageLimit,
    val maximumBytes: Long,
) : IOException("Publication storage limit exceeded: $limit ($maximumBytes bytes)")

class PublicationStorageException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The exact verified fingerprint currently associated with a catalog publication. */
data class OriginalCatalogState(
    val format: LocalPublicationFormat,
    val sha256: String,
    val sizeBytes: Long,
    val originalAvailable: Boolean,
) {
    init {
        require(SHA256_PATTERN.matches(sha256)) { "Invalid catalog original SHA-256" }
        require(sizeBytes in 1..ORIGINAL_MAX_BYTES) { "Catalog original size is out of bounds" }
    }
}

data class PublicationOriginalMatch(
    val publicationId: String,
    val format: LocalPublicationFormat,
    val sha256: String,
    val sizeBytes: Long,
)

data class PublicationStoredResource(
    val resourceId: String,
    val mimeType: String,
    val sha256: String,
    val sizeBytes: Long,
)

enum class PublicationJobKind { ADMISSION, REMOVE_ORIGINAL, DELETE_PUBLICATION, ARCHIVE_STAGING }
enum class PublicationJobStatus { RECLAIMED, COMMITTED, RESTORED, DELETED, PRESERVED, CONFLICT, CORRUPT }

data class PublicationRecoveryResult(
    val jobId: String,
    val kind: PublicationJobKind,
    val publicationIds: List<String>,
    val status: PublicationJobStatus,
)

enum class PublicationDeleteStatus { COMPLETED, CATALOG_REJECTED }

/** Owns the complete private original and its indispensable publication resources. */
class PublicationOriginalStore(roots: StorageRoots) {
    private val lock = Any()
    private val rootDirectory: File
    private val jobsDirectory: File
    private val booksDirectory: File
    private val deletionsDirectory: File
    private val archiveJobsDirectory: File
    private var accountedBytes: Long

    init {
        val noBackup = roots.noBackupDirectory.canonicalFile
        rootDirectory = File(noBackup, "publication-originals").canonicalFile
        require(isDescendant(noBackup, rootDirectory)) { "Invalid publication store root" }
        ensureDirectory(rootDirectory)
        jobsDirectory = childDirectory(rootDirectory, "jobs").also(::ensureDirectory)
        booksDirectory = childDirectory(rootDirectory, "books").also(::ensureDirectory)
        deletionsDirectory = childDirectory(rootDirectory, "deletions").also(::ensureDirectory)
        archiveJobsDirectory = childDirectory(rootDirectory, "archive-jobs").also(::ensureDirectory)
        accountedBytes = scanBytes()
    }

    /** Copies from the caller-owned stream without closing it or retaining the provider URI. */
    suspend fun stage(input: InputStream): StagedOriginal {
        val jobId = UUID.randomUUID().toString()
        val jobDirectory = childDirectory(jobsDirectory, jobId)
        synchronized(lock) {
            if (!jobDirectory.mkdir()) throw PublicationStorageException("Cannot create admission staging directory")
            refreshBytes()
        }
        val originalPart = childFile(jobDirectory, "original.part")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            writeJournal(jobDirectory, AdmissionJournal(jobId, AdmissionState.STAGING))
            FileOutputStream(originalPart).use { fileOutput ->
                val output = BufferedOutputStream(fileOutput, COPY_BUFFER_BYTES)
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                val context = currentCoroutineContext()
                while (true) {
                    context.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (count.toLong() > ORIGINAL_MAX_BYTES - total) {
                        throw PublicationLimitException(PublicationStorageLimit.ORIGINAL_BYTES, ORIGINAL_MAX_BYTES)
                    }
                    synchronized(lock) {
                        if (count.toLong() > STORE_MAX_BYTES - accountedBytes) {
                            throw PublicationLimitException(PublicationStorageLimit.OWNED_STORAGE_BYTES, STORE_MAX_BYTES)
                        }
                        output.write(buffer, 0, count)
                        accountedBytes += count
                    }
                    digest.update(buffer, 0, count)
                    total += count
                }
                output.flush()
                fileOutput.fd.sync()
            }
            if (total == 0L) throw PublicationStorageException("An original cannot be empty")
            val sha256 = digest.toHex()
            writeJournal(jobDirectory, AdmissionJournal(jobId, AdmissionState.READY, sha256 = sha256, sizeBytes = total))
            return StagedOriginal(this, jobId, jobDirectory, sha256, total)
        } catch (error: Throwable) {
            deleteOwnedTree(jobDirectory)
            throw error
        }
    }

    /** Exact digest matches are suggestions only; this method never associates or merges books. */
    fun matchingOriginals(staged: StagedOriginal): List<PublicationOriginalMatch> {
        requireOwned(staged)
        val matches = ArrayList<PublicationOriginalMatch>()
        synchronized(lock) {
            booksDirectory.listFiles()?.forEach { directory ->
                if (!directory.isDirectory || directory.name.startsWith(".")) return@forEach
                val publicationId = canonicalUuidOrNull(directory.name) ?: return@forEach
                val metadata = readOriginalMetadata(directory) ?: return@forEach
                if (metadata.sha256 == staged.sha256 && metadata.sizeBytes == staged.sizeBytes &&
                    !childFile(directory, "job.bin").exists() && !hasPendingDeletion(publicationId) &&
                    verifiedOriginalFile(directory, metadata)
                ) {
                    matches += PublicationOriginalMatch(publicationId, metadata.format, metadata.sha256, metadata.sizeBytes)
                }
            }
        }
        return matches.sortedBy(PublicationOriginalMatch::publicationId)
    }

    /**
     * Promotes only a parser-approved staged original. Existing publication files are never replaced.
     * [expected] is REQUIRED when restoring/re-associating into an existing metadata-only publication.
     */
    fun commit(
        staged: StagedOriginal,
        publicationId: String,
        format: LocalPublicationFormat,
        expected: OriginalCatalogState? = null,
    ): PublicationCommit {
        requireOwned(staged)
        BookIdentity.Local(publicationId)
        if (expected != null && (expected.format != format || expected.sha256 != staged.sha256 || expected.sizeBytes != staged.sizeBytes)) {
            throw PublicationStorageException("Staged original does not match the expected publication fingerprint")
        }
        synchronized(lock) {
            if (staged.admissionPublicationId != null &&
                (staged.admissionPublicationId != publicationId || staged.admissionFormat != format)) {
                throw PublicationStorageException("Staged native admission belongs to another publication")
            }
            val finalDirectory = childDirectory(booksDirectory, publicationId)
            if (finalDirectory.exists() || hasPendingDeletion(publicationId)) {
                throw PublicationStorageException("Publication already has private original state")
            }
            val jobDirectory = staged.jobDirectory
            val journal = readJournal(jobDirectory)
                ?: throw PublicationStorageException("Admission staging journal is missing")
            if (journal.jobId != staged.jobId || journal.state != AdmissionState.READY ||
                journal.sha256 != staged.sha256 || journal.sizeBytes != staged.sizeBytes
            ) {
                throw PublicationStorageException("Admission staging handle is stale")
            }
            val committing = journal.copy(
                state = AdmissionState.COMMITTING,
                publicationId = publicationId,
                format = format,
            )
            writeJournal(jobDirectory, committing)
            val pendingDirectory = childDirectory(booksDirectory, ".pending-${staged.jobId}")
            if (pendingDirectory.exists()) throw PublicationStorageException("Admission promotion is already pending")
            moveAtomically(jobDirectory, pendingDirectory)
            writeJournal(pendingDirectory, committing)
            val pendingOriginal = childFile(pendingDirectory, "original.part")
            moveAtomically(pendingOriginal, childFile(pendingDirectory, "original.bin"))
            val pendingResources = childDirectory(pendingDirectory, "resources")
            pendingResources.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { part ->
                val resourceId = part.name.removeSuffix(".part")
                if (canonicalUuidOrNull(resourceId) == null) throw PublicationStorageException("Invalid staged resource ID")
                moveAtomically(part, childFile(pendingResources, "$resourceId.bin"))
            }
            writeOriginalMetadata(pendingDirectory, OriginalMetadata(format, staged.sha256, staged.sizeBytes))
            moveAtomically(pendingDirectory, finalDirectory)
            refreshBytes()
            val committed = CommittedOriginal(this, publicationId, finalDirectory, format, staged.sha256, staged.sizeBytes)
            return PublicationCommit(this, staged.jobId, committed)
        }
    }

    /** Returns only a verified stable original; a digest/size mismatch is treated as unavailable. */
    fun original(publicationId: String): CommittedOriginal? {
        BookIdentity.Local(publicationId)
        synchronized(lock) {
            val directory = childDirectory(booksDirectory, publicationId)
            if (childFile(directory, "job.bin").exists() || hasPendingDeletion(publicationId)) return null
            val metadata = readOriginalMetadata(directory) ?: return null
            if (!verifiedOriginalFile(directory, metadata)) return null
            return CommittedOriginal(this, publicationId, directory, metadata.format, metadata.sha256, metadata.sizeBytes)
        }
    }

    /** Verifies a stable original or promoted admission for transfer replay without exposing a Reader source. */
    fun verifyOriginalForTransferRecovery(publicationId: String, expected: OriginalCatalogState): Boolean {
        BookIdentity.Local(publicationId)
        synchronized(lock) {
            if (hasPendingDeletion(publicationId)) return false
            val directory = childDirectory(booksDirectory, publicationId)
            val metadata = OriginalMetadata(expected.format, expected.sha256, expected.sizeBytes)
            if (readOriginalMetadata(directory) != metadata || !verifiedOriginalFile(directory, metadata)) return false
            if (!childFile(directory, "job.bin").exists()) return true
            val journal = readJournal(directory) ?: return false
            return journal.state == AdmissionState.COMMITTING && journal.publicationId == publicationId &&
                journal.format == metadata.format && journal.sha256 == metadata.sha256 && journal.sizeBytes == metadata.sizeBytes
        }
    }

    /** Reassociation requires the exact publication selected by the user and its full fingerprint. */
    fun verifyReassociation(
        staged: StagedOriginal,
        publicationId: String,
        expected: OriginalCatalogState,
    ): Boolean {
        BookIdentity.Local(publicationId)
        requireOwned(staged)
        return staged.sha256 == expected.sha256 && staged.sizeBytes == expected.sizeBytes
    }

    /** Only the current, verified private original may reuse completed native admission. */
    fun admissionRecord(original: CommittedOriginal, cancelled: () -> Unit = {}): PublicationAdmissionRecord? = synchronized(lock) {
        requireCurrentAdmissionOriginal(original)
        cancelled()
        val file = childFile(original.directory, "admission.bin")
        if (!file.isFile || file.length() !in 1..PublicationAdmissionCodec.MAX_BYTES) return@synchronized null
        try {
            BufferedInputStream(FileInputStream(file)).use { input ->
                PublicationAdmissionCodec.read(input, cancelled).also {
                    it.requireBinding(original.publicationId, original.format, original.sha256, original.sizeBytes)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        } catch (_: NoSuchElementException) {
            null
        }
    }

    /** Atomically replaces only derived admission state, never original or reading-state bytes. */
    fun putAdmissionRecord(original: CommittedOriginal, record: PublicationAdmissionRecord, cancelled: () -> Unit = {}) = synchronized(lock) {
        requireCurrentAdmissionOriginal(original)
        record.requireBinding(original.publicationId, original.format, original.sha256, original.sizeBytes)
        writeAdmissionFile(original.directory, record, cancelled)
    }

    private fun requireCurrentAdmissionOriginal(original: CommittedOriginal) {
        require(original.store === this)
        val metadata = currentMetadata(original)
        if (metadata == null || childFile(original.directory, "job.bin").exists() || hasPendingDeletion(original.publicationId) ||
            !verifiedOriginalFile(original.directory, metadata)) {
            throw PublicationStorageException("Publication admission original is not current and verified")
        }
    }

    private fun stageAdmissionRecord(staged: StagedOriginal, record: PublicationAdmissionRecord, cancelled: () -> Unit) = synchronized(lock) {
        requireOwned(staged)
        record.requireBinding(record.publicationId, record.format, staged.sha256, staged.sizeBytes)
        writeAdmissionFile(staged.jobDirectory, record, cancelled)
        staged.admissionPublicationId = record.publicationId
        staged.admissionFormat = record.format
    }

    private fun writeAdmissionFile(directory: File, record: PublicationAdmissionRecord, cancelled: () -> Unit) {
        removeAdmissionTemporaryFiles(directory)
        val target = childFile(directory, "admission.bin")
        val temporary = childFile(directory, ".admission.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { file ->
                var total = 0L
                val limited = object : OutputStream() {
                    override fun write(value: Int) {
                        reserve(1); file.write(value)
                    }
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        reserve(length); file.write(bytes, offset, length)
                    }
                    private fun reserve(count: Int) {
                        cancelled()
                        if (count.toLong() > PublicationAdmissionCodec.MAX_BYTES - total) {
                            throw PublicationLimitException(PublicationStorageLimit.ADMISSION_RECORD_BYTES, PublicationAdmissionCodec.MAX_BYTES)
                        }
                        if (count.toLong() > STORE_MAX_BYTES - accountedBytes) {
                            throw PublicationLimitException(PublicationStorageLimit.OWNED_STORAGE_BYTES, STORE_MAX_BYTES)
                        }
                        total += count; accountedBytes += count
                    }
                }
                val buffered = BufferedOutputStream(limited, COPY_BUFFER_BYTES)
                PublicationAdmissionCodec.write(record, buffered, cancelled)
                buffered.flush(); file.fd.sync()
            }
            cancelled()
            moveAtomically(temporary, target, replace = true)
            refreshBytes()
        } catch (error: Throwable) {
            temporary.delete(); refreshBytes()
            throw error
        }
    }

    /** Stages an indispensable local image resource with the original before catalog admission. */
    fun stageResource(
        staged: StagedOriginal,
        resourceId: String,
        mimeType: String,
        input: InputStream,
        expectedSha256: String? = null,
    ): PublicationStoredResource {
        requireOwned(staged)
        requireResourceReference(resourceId, mimeType, expectedSha256)
        val directory = childDirectory(staged.jobDirectory, "resources")
        synchronized(lock) { ensureDirectory(directory) }
        return copyResource(directory, resourceId, mimeType, input, expectedSha256)
    }

    /** Adds a rebuildable-from-original image after file commit; it remains publication-scoped. */
    fun putResource(
        publicationId: String,
        resourceId: String,
        mimeType: String,
        input: InputStream,
        expectedSha256: String? = null,
        expectedOriginal: CommittedOriginal? = null,
    ): PublicationStoredResource {
        BookIdentity.Local(publicationId)
        requireResourceReference(resourceId, mimeType, expectedSha256)
        expectedOriginal?.let { require(it.store === this && it.publicationId == publicationId) { "Wrong original owner" } }
        synchronized(lock) {
            val book = childDirectory(booksDirectory, publicationId)
            if (childFile(book, "job.bin").exists() || hasPendingDeletion(publicationId)) {
                throw PublicationStorageException("Publication original is not catalog-confirmed")
            }
            val original = readOriginalMetadata(book) ?: throw PublicationStorageException("Publication original is unavailable")
            if (expectedOriginal != null && original != OriginalMetadata(expectedOriginal.format, expectedOriginal.sha256, expectedOriginal.sizeBytes)) {
                throw PublicationStorageException("Publication original changed before resource persistence")
            }
            val originalFile = childFile(book, "original.bin")
            if (!originalFile.isFile || originalFile.length() != original.sizeBytes) {
                throw PublicationStorageException("Publication original is unavailable")
            }
            val directory = childDirectory(book, "resources")
            ensureDirectory(directory)
            removeResourceTemporaryFiles(directory, resourceId)
            refreshBytes()
            val data = childFile(directory, "$resourceId.bin")
            val part = childFile(directory, "$resourceId.part")
            val metadataFile = childFile(directory, "$resourceId.meta")
            val stored = readResourceMetadata(metadataFile)
            if (stored != null && (stored.mimeType != mimeType || expectedSha256 != null && stored.sha256 != expectedSha256)) {
                throw PublicationStorageException("Resource ID is bound to a different publication resource")
            }
            if (stored != null && verifiedResourceFile(data, stored)) {
                deletePrivateFile(part)
                removeResourceTemporaryFiles(directory, resourceId)
                refreshBytes()
                return PublicationStoredResource(resourceId, stored.mimeType, stored.sha256, stored.sizeBytes)
            }
            if (stored != null && verifiedResourceFile(part, stored)) {
                moveAtomically(part, data, replace = data.exists())
                removeResourceTemporaryFiles(directory, resourceId)
                refreshBytes()
                return PublicationStoredResource(resourceId, stored.mimeType, stored.sha256, stored.sizeBytes)
            }

            val expected = expectedSha256 ?: stored?.sha256
            if (stored == null && expected != null) {
                val dataFingerprint = resourceFingerprint(data)
                val candidate = dataFingerprint?.takeIf { it.first == expected }?.let { data to it }
                    ?: resourceFingerprint(part)?.takeIf { it.first == expected }?.let { part to it }
                if (candidate != null) {
                    val (file, fingerprint) = candidate
                    if (file == part) moveAtomically(part, data, replace = data.exists()) else deletePrivateFile(part)
                    writeResourceMetadata(metadataFile, PublicationResourceMetadata(mimeType, fingerprint.first, fingerprint.second))
                    removeResourceTemporaryFiles(directory, resourceId)
                    refreshBytes()
                    return PublicationStoredResource(resourceId, mimeType, fingerprint.first, fingerprint.second)
                }
            }

            removeResourceFiles(directory, resourceId)
            val rebuilt = copyResource(directory, resourceId, mimeType, input, expected, publishImmediately = true)
            refreshBytes()
            return rebuilt
        }
    }

    /** Returns bounded resource metadata from the current publication's validated sidecar. */
    fun resource(publicationId: String, resourceId: String): PublicationStoredResource? {
        BookIdentity.Local(publicationId)
        requireCanonicalUuid(resourceId, "resourceId")
        synchronized(lock) {
            val book = childDirectory(booksDirectory, publicationId)
            if (childFile(book, "job.bin").exists() || hasPendingDeletion(publicationId)) return null
            val original = readOriginalMetadata(book) ?: return null
            val originalFile = childFile(book, "original.bin")
            if (!originalFile.isFile || originalFile.length() != original.sizeBytes) return null
            val metadata = readResourceMetadata(childFile(childDirectory(book, "resources"), "$resourceId.meta")) ?: return null
            return PublicationStoredResource(resourceId, metadata.mimeType, metadata.sha256, metadata.sizeBytes)
        }
    }

    /** Opens bytes only when owner, opaque IDs, MIME, stored metadata, byte count, and SHA-256 all match. */
    fun openResource(reference: ReaderResourceRef.Local): InputStream? {
        BookIdentity.Local(reference.publicationId)
        requireCanonicalUuid(reference.resourceId, "resourceId")
        if (reference.mimeType !in SUPPORTED_RESOURCE_MIME_TYPES || !SHA256_PATTERN.matches(reference.sha256)) return null
        synchronized(lock) {
            val book = childDirectory(booksDirectory, reference.publicationId)
            if (childFile(book, "job.bin").exists() || hasPendingDeletion(reference.publicationId)) return null
            val originalMetadata = readOriginalMetadata(book) ?: return null
            val originalFile = childFile(book, "original.bin")
            if (!originalFile.isFile || originalFile.length() != originalMetadata.sizeBytes) return null
            val resourceDirectory = childDirectory(book, "resources")
            val data = childFile(resourceDirectory, "${reference.resourceId}.bin")
            val sidecar = childFile(resourceDirectory, "${reference.resourceId}.meta")
            if (!data.isFile || !sidecar.isFile) return null
            val metadata = readResourceMetadata(sidecar) ?: return null
            if (metadata.mimeType != reference.mimeType || metadata.sha256 != reference.sha256 || data.length() != metadata.sizeBytes) return null
            val stream = FileInputStream(data)
            try {
                if (digestAndCount(stream) != (metadata.sha256 to metadata.sizeBytes)) return null.also { stream.close() }
                stream.channel.position(0L)
                return stream
            } catch (error: Throwable) {
                stream.close()
                throw error
            }
        }
    }

    /**
     * Moves only this publication's original/resource directory to a journaled boundary, then invokes
     * the caller's catalog transaction. A thrown callback leaves the journal for [recover] to resolve.
     */
    suspend fun removeOriginal(
        publicationId: String,
        markUnavailable: suspend (BookIdentity.Local) -> Boolean,
    ): PublicationDeleteStatus = deleteBoundary(publicationId, DeleteKind.REMOVE_ORIGINAL, markUnavailable)

    /** Removes only this publication's original/resources and its catalog-owned state after callback success. */
    suspend fun deletePublication(
        publicationId: String,
        deleteCatalogState: suspend (BookIdentity.Local) -> Boolean,
    ): PublicationDeleteStatus = deleteBoundary(publicationId, DeleteKind.DELETE_PUBLICATION, deleteCatalogState)

    /**
     * Resolves only journals created by this store. [catalog] must be a fresh snapshot from Room;
     * directories lacking our valid journal are never deleted.
     * [preservedPublicationIds] retain verified admissions owned by unresolved PREPARED transfers for replay.
     */
    fun recover(
        catalog: Map<String, OriginalCatalogState>,
        preservedPublicationIds: Set<String> = emptySet(),
    ): List<PublicationRecoveryResult> = synchronized(lock) {
        catalog.keys.forEach { BookIdentity.Local(it) }
        preservedPublicationIds.forEach { BookIdentity.Local(it) }
        val results = ArrayList<PublicationRecoveryResult>()
        jobsDirectory.listFiles()?.forEach { job ->
            val id = canonicalUuidOrNull(job.name) ?: return@forEach
            if (!job.isDirectory) {
                results += PublicationRecoveryResult(id, PublicationJobKind.ADMISSION, emptyList(), PublicationJobStatus.CORRUPT)
                return@forEach
            }
            val journal = readJournal(job)
            if (journal == null || journal.jobId != id) {
                results += PublicationRecoveryResult(id, PublicationJobKind.ADMISSION, listOfNotNull(journal?.publicationId), PublicationJobStatus.CORRUPT)
                return@forEach
            }
            val publicationId = journal.publicationId
            val catalogClaimsOriginal = journal.state == AdmissionState.COMMITTING &&
                publicationId?.let { catalog[it]?.originalAvailable } == true
            if (catalogClaimsOriginal) {
                results += PublicationRecoveryResult(id, PublicationJobKind.ADMISSION, listOfNotNull(publicationId), PublicationJobStatus.CONFLICT)
                return@forEach
            }
            deleteOwnedTree(job)
            results += PublicationRecoveryResult(id, PublicationJobKind.ADMISSION, listOfNotNull(publicationId), PublicationJobStatus.RECLAIMED)
        }
        booksDirectory.listFiles()?.filterNot { it.name.startsWith(".") }?.forEach { directory ->
            val publicationId = canonicalUuidOrNull(directory.name) ?: return@forEach
            if (!directory.isDirectory) return@forEach
            val journal = readJournal(directory)
            if (journal == null) {
                if (childFile(directory, "job.bin").exists()) {
                    results += PublicationRecoveryResult(publicationId, PublicationJobKind.ADMISSION, listOf(publicationId), PublicationJobStatus.CORRUPT)
                } else {
                    val metadata = readOriginalMetadata(directory)
                    if (metadata != null && !hasPendingDeletion(publicationId)) {
                        removeAdmissionTemporaryFiles(directory, metadata)
                    }
                }
                return@forEach
            }
            if (journal.state != AdmissionState.COMMITTING || journal.publicationId != publicationId ||
                canonicalUuidOrNull(journal.jobId) == null || journal.format == null
            ) {
                results += PublicationRecoveryResult(journal.jobId, PublicationJobKind.ADMISSION, listOf(publicationId), PublicationJobStatus.CORRUPT)
                return@forEach
            }
            val expected = OriginalMetadata(journal.format, journal.sha256 ?: return@forEach, journal.sizeBytes)
            if (readOriginalMetadata(directory) != expected || !verifiedOriginalFile(directory, expected)) {
                results += PublicationRecoveryResult(journal.jobId, PublicationJobKind.ADMISSION, listOf(publicationId), PublicationJobStatus.CORRUPT)
            } else {
                val state = catalog[publicationId]
                val stateMatches = state != null && state.format == expected.format && state.sha256 == expected.sha256 &&
                    state.sizeBytes == expected.sizeBytes
                if (stateMatches && state.originalAvailable) {
                    childFile(directory, "job.bin").delete()
                    results += PublicationRecoveryResult(journal.jobId, PublicationJobKind.ADMISSION, listOf(publicationId), PublicationJobStatus.COMMITTED)
                } else if (publicationId in preservedPublicationIds && (state == null || stateMatches)) {
                    results += PublicationRecoveryResult(journal.jobId, PublicationJobKind.ADMISSION, listOf(publicationId), PublicationJobStatus.PRESERVED)
                } else if (state == null || stateMatches) {
                    deleteOwnedTree(directory)
                    results += PublicationRecoveryResult(journal.jobId, PublicationJobKind.ADMISSION, listOf(publicationId), PublicationJobStatus.RECLAIMED)
                } else {
                    results += PublicationRecoveryResult(journal.jobId, PublicationJobKind.ADMISSION, listOf(publicationId), PublicationJobStatus.CONFLICT)
                }
            }
        }
        booksDirectory.listFiles()?.filter { it.name.startsWith(".pending-") }?.forEach { pending ->
            val jobId = canonicalUuidOrNull(pending.name.removePrefix(".pending-")) ?: return@forEach
            if (!pending.isDirectory) {
                results += PublicationRecoveryResult(jobId, PublicationJobKind.ADMISSION, emptyList(), PublicationJobStatus.CORRUPT)
                return@forEach
            }
            val journal = readJournal(pending)
            if (journal == null || journal.jobId != jobId || journal.state != AdmissionState.COMMITTING ||
                journal.publicationId == null || pending.name != ".pending-${journal.jobId}"
            ) {
                results += PublicationRecoveryResult(jobId, PublicationJobKind.ADMISSION, listOfNotNull(journal?.publicationId), PublicationJobStatus.CORRUPT)
                return@forEach
            }
            val catalogClaimsOriginal = catalog[journal.publicationId]?.originalAvailable == true
            if (catalogClaimsOriginal) {
                results += PublicationRecoveryResult(
                    journal.jobId,
                    PublicationJobKind.ADMISSION,
                    listOf(journal.publicationId),
                    PublicationJobStatus.CONFLICT,
                )
                return@forEach
            }
            val publicationId = journal.publicationId
            val expected = OriginalMetadata(journal.format ?: return@forEach, journal.sha256 ?: return@forEach, journal.sizeBytes)
            val admissionVerified = readOriginalMetadata(pending) == expected && verifiedOriginalFile(pending, expected)
            val state = catalog[publicationId]
            val stateMatches = state != null && state.format == expected.format && state.sha256 == expected.sha256 &&
                state.sizeBytes == expected.sizeBytes
            if (publicationId in preservedPublicationIds && admissionVerified && (state == null || stateMatches)) {
                results += PublicationRecoveryResult(
                    journal.jobId,
                    PublicationJobKind.ADMISSION,
                    listOf(publicationId),
                    PublicationJobStatus.PRESERVED,
                )
            } else {
                deleteOwnedTree(pending)
                results += PublicationRecoveryResult(
                    journal.jobId,
                    PublicationJobKind.ADMISSION,
                    listOf(publicationId),
                    PublicationJobStatus.RECLAIMED,
                )
            }
        }
        deletionsDirectory.listFiles()?.filter { it.name.startsWith(".pending-") }?.forEach { staging ->
            val operationId = canonicalUuidOrNull(staging.name.removePrefix(".pending-")) ?: return@forEach
            val journal = readDeleteJournal(staging)
            if (!staging.isDirectory || journal == null || journal.jobId != operationId) {
                if (safeToReclaimJournallessDeletion(staging)) {
                    deleteOwnedTree(staging)
                    results += PublicationRecoveryResult(operationId, PublicationJobKind.REMOVE_ORIGINAL, emptyList(), PublicationJobStatus.RECLAIMED)
                } else {
                    results += PublicationRecoveryResult(operationId, PublicationJobKind.REMOVE_ORIGINAL, emptyList(), PublicationJobStatus.CORRUPT)
                }
                return@forEach
            }
            val operation = childDirectory(deletionsDirectory, operationId)
            if (operation.exists()) {
                results += PublicationRecoveryResult(operationId, journal.kind.toJobKind(), listOf(journal.publicationId), PublicationJobStatus.CONFLICT)
                return@forEach
            }
            moveAtomically(staging, operation)
        }
        deletionsDirectory.listFiles()?.forEach { operation ->
            val operationId = canonicalUuidOrNull(operation.name) ?: return@forEach
            val journal = readDeleteJournal(operation)
            if (journal == null || journal.jobId != operationId) {
                if (safeToReclaimJournallessDeletion(operation)) {
                    deleteOwnedTree(operation)
                    results += PublicationRecoveryResult(operationId, PublicationJobKind.REMOVE_ORIGINAL, emptyList(), PublicationJobStatus.RECLAIMED)
                } else {
                    results += PublicationRecoveryResult(operationId, PublicationJobKind.REMOVE_ORIGINAL, emptyList(), PublicationJobStatus.CORRUPT)
                }
                return@forEach
            }
            val id = journal.publicationId
            val state = catalog[id]
            val payload = childDirectory(operation, "payload")
            val expected = OriginalMetadata(journal.format, journal.sha256, journal.sizeBytes)
            val payloadEmpty = payload.isDirectory && payload.list()?.isEmpty() == true
            val payloadValid = !payload.exists() || payloadEmpty ||
                readOriginalMetadata(payload) == expected && verifiedOriginalFile(payload, expected)
            if (!payloadValid) {
                results += PublicationRecoveryResult(journal.jobId, journal.kind.toJobKind(), listOf(id), PublicationJobStatus.CORRUPT)
                return@forEach
            }
            val stateMatches = state != null && state.format == journal.format && state.sha256 == journal.sha256 &&
                state.sizeBytes == journal.sizeBytes
            val shouldFinalize = when (journal.kind) {
                DeleteKind.REMOVE_ORIGINAL -> stateMatches && !state.originalAvailable
                DeleteKind.DELETE_PUBLICATION -> state == null
            }
            val shouldRestore = stateMatches && state.originalAvailable
            val destination = childDirectory(booksDirectory, id)
            when {
                shouldFinalize && destination.exists() -> {
                    results += PublicationRecoveryResult(journal.jobId, journal.kind.toJobKind(), listOf(id), PublicationJobStatus.CONFLICT)
                }
                shouldFinalize -> {
                    deleteOwnedTree(operation)
                    results += PublicationRecoveryResult(journal.jobId, journal.kind.toJobKind(), listOf(id), PublicationJobStatus.DELETED)
                }
                shouldRestore && payload.exists() && !payloadEmpty && !destination.exists() -> {
                    moveAtomically(payload, destination)
                    deleteOwnedTree(operation)
                    results += PublicationRecoveryResult(journal.jobId, journal.kind.toJobKind(), listOf(id), PublicationJobStatus.RESTORED)
                }
                shouldRestore && (!payload.exists() || payloadEmpty) && destination.exists() &&
                    readOriginalMetadata(destination) == expected && verifiedOriginalFile(destination, expected) -> {
                    deleteOwnedTree(operation)
                    results += PublicationRecoveryResult(journal.jobId, journal.kind.toJobKind(), listOf(id), PublicationJobStatus.RESTORED)
                }
                else -> results += PublicationRecoveryResult(journal.jobId, journal.kind.toJobKind(), listOf(id), PublicationJobStatus.CONFLICT)
            }
        }
        archiveJobsDirectory.listFiles()?.forEach { job ->
            val id = canonicalUuidOrNull(job.name) ?: return@forEach
            deleteOwnedTree(job)
            results += PublicationRecoveryResult(id, PublicationJobKind.ARCHIVE_STAGING, emptyList(), PublicationJobStatus.RECLAIMED)
        }
        refreshBytes()
        results.sortedWith(compareBy(PublicationRecoveryResult::jobId, PublicationRecoveryResult::kind))
    }

    internal fun createArchiveStagingDirectory(jobId: String): File = synchronized(lock) {
        requireCanonicalUuid(jobId, "archive jobId")
        val directory = childDirectory(archiveJobsDirectory, jobId)
        if (!directory.mkdir()) throw PublicationStorageException("Cannot create archive staging directory")
        refreshBytes()
        directory
    }

    internal suspend fun copyArchiveInput(input: InputStream, destination: File, maximumBytes: Long): Pair<String, Long> {
        ensureConfined(destination)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            FileOutputStream(destination).use { fileOutput ->
                val output = BufferedOutputStream(fileOutput, COPY_BUFFER_BYTES)
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                val context = currentCoroutineContext()
                while (true) {
                    context.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (count.toLong() > maximumBytes - total) {
                        throw PublicationLimitException(PublicationStorageLimit.OWNED_STORAGE_BYTES, maximumBytes)
                    }
                    synchronized(lock) {
                        if (count.toLong() > STORE_MAX_BYTES - accountedBytes) {
                            throw PublicationLimitException(PublicationStorageLimit.OWNED_STORAGE_BYTES, STORE_MAX_BYTES)
                        }
                        output.write(buffer, 0, count)
                        accountedBytes += count
                    }
                    digest.update(buffer, 0, count)
                    total += count
                }
                output.flush()
                fileOutput.fd.sync()
            }
            return digest.toHex() to total
        } catch (error: Throwable) {
            synchronized(lock) {
                if (destination.exists()) destination.delete()
                refreshBytes()
            }
            throw error
        }
    }

    internal fun deleteArchiveStagingDirectory(directory: File) = deleteOwnedTree(directory)

    internal fun originalForArchive(publicationId: String): CommittedOriginal? = synchronized(lock) {
        BookIdentity.Local(publicationId)
        val directory = childDirectory(booksDirectory, publicationId)
        if (childFile(directory, "job.bin").exists() || hasPendingDeletion(publicationId)) return@synchronized null
        val metadata = readOriginalMetadata(directory) ?: return@synchronized null
        val file = childFile(directory, "original.bin")
        if (!file.isFile || file.length() != metadata.sizeBytes) return@synchronized null
        CommittedOriginal(this, publicationId, directory, metadata.format, metadata.sha256, metadata.sizeBytes)
    }

    internal fun originalSlotOccupied(publicationId: String): Boolean = synchronized(lock) {
        BookIdentity.Local(publicationId)
        childDirectory(booksDirectory, publicationId).exists() || hasPendingDeletion(publicationId)
    }

    internal fun commitArchiveOriginal(
        staged: StagedOriginal,
        descriptor: PublicationArchiveOriginal,
        expectedCatalog: OriginalCatalogState?,
    ): PublicationCommit = commit(staged, descriptor.publicationId, descriptor.format, expectedCatalog)

    internal suspend fun stageArchiveOriginal(
        input: InputStream,
        descriptor: PublicationArchiveOriginal,
    ): StagedOriginal {
        val staged = stage(input)
        if (staged.sha256 != descriptor.sha256 || staged.sizeBytes != descriptor.sizeBytes) {
            staged.close()
            throw PublicationStorageException("Archive original does not match its manifest fingerprint")
        }
        return staged
    }

    private suspend fun deleteBoundary(
        publicationId: String,
        kind: DeleteKind,
        callback: suspend (BookIdentity.Local) -> Boolean,
    ): PublicationDeleteStatus {
        BookIdentity.Local(publicationId)
        val jobId = UUID.randomUUID().toString()
        val operation = childDirectory(deletionsDirectory, jobId)
        val staging = childDirectory(deletionsDirectory, ".pending-$jobId")
        val source = childDirectory(booksDirectory, publicationId)
        val metadata: OriginalMetadata?
        synchronized(lock) {
            val pendingAdmission = booksDirectory.listFiles()?.any {
                it.name.startsWith(".pending-") && readJournal(it)?.publicationId == publicationId
            } == true
            if (childFile(source, "job.bin").exists() || hasPendingDeletion(publicationId) || pendingAdmission) {
                throw PublicationStorageException("Publication admission or deletion is not catalog-confirmed")
            }
            metadata = if (!source.exists()) null else {
                readOriginalMetadata(source) ?: throw PublicationStorageException("Occupied publication original metadata is invalid")
            }
            if (metadata == null) return@synchronized
            if (!verifiedOriginalFile(source, metadata)) throw PublicationStorageException("Publication original failed verification")
            if (operation.exists() || !staging.mkdir()) throw PublicationStorageException("Cannot create deletion journal")
            try {
                writeDeleteJournal(staging, DeleteJournal(jobId, publicationId, kind, metadata.format, metadata.sha256, metadata.sizeBytes))
            } catch (error: Throwable) {
                try {
                    deleteOwnedTree(staging)
                } catch (cleanupError: Throwable) {
                    error.addSuppressed(cleanupError)
                }
                throw error
            }
            moveAtomically(staging, operation)
            val payload = childDirectory(operation, "payload")
            moveAtomically(source, payload)
            refreshBytes()
        }
        if (metadata == null) return if (callback(BookIdentity.Local(publicationId)))
            PublicationDeleteStatus.COMPLETED else PublicationDeleteStatus.CATALOG_REJECTED
        val accepted = callback(BookIdentity.Local(publicationId))
        if (!accepted) {
            synchronized(lock) {
                val payload = childDirectory(operation, "payload")
                val destination = childDirectory(booksDirectory, publicationId)
                if (payload.exists() && destination.exists()) return PublicationDeleteStatus.CATALOG_REJECTED
                if (payload.exists()) moveAtomically(payload, destination)
                deleteOwnedTree(operation)
            }
            return PublicationDeleteStatus.CATALOG_REJECTED
        }
        synchronized(lock) {
            writeDeleteJournal(
                operation,
                DeleteJournal(jobId, publicationId, kind, metadata.format, metadata.sha256, metadata.sizeBytes, catalogCommitted = true),
            )
            deleteOwnedTree(operation)
        }
        return PublicationDeleteStatus.COMPLETED
    }

    private fun copyResource(
        directory: File,
        resourceId: String,
        mimeType: String,
        input: InputStream,
        expectedSha256: String?,
        publishImmediately: Boolean = false,
    ): PublicationStoredResource {
        require(mimeType in SUPPORTED_RESOURCE_MIME_TYPES) { "Unsupported local resource MIME type" }
        expectedSha256?.let { require(SHA256_PATTERN.matches(it)) { "Invalid expected resource SHA-256" } }
        synchronized(lock) {
            val data = childFile(directory, "$resourceId.bin")
            val part = childFile(directory, "$resourceId.part")
            val metadataFile = childFile(directory, "$resourceId.meta")
            if (data.exists() || part.exists() || metadataFile.exists()) throw PublicationStorageException("Resource ID already exists")
            val currentMetadataFiles = resourceMetadataFiles(directory)
            if (currentMetadataFiles.size >= RESOURCES_PER_PUBLICATION_MAX_ENTRIES) {
                throw PublicationLimitException(PublicationStorageLimit.PUBLICATION_RESOURCE_BYTES, RESOURCES_PER_PUBLICATION_MAX_BYTES)
            }
            val currentResources = currentMetadataFiles.mapNotNull(::readResourceMetadata)
            val currentResourceBytes = currentResources.sumOf(PublicationResourceMetadata::sizeBytes)
            val temporary = childFile(directory, ".$resourceId.${UUID.randomUUID()}.tmp")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            try {
                FileOutputStream(temporary).use { fileOutput ->
                    val output = BufferedOutputStream(fileOutput, COPY_BUFFER_BYTES)
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        if (count.toLong() > RESOURCE_MAX_BYTES - total) {
                            throw PublicationLimitException(PublicationStorageLimit.RESOURCE_BYTES, RESOURCE_MAX_BYTES)
                        }
                        if (count.toLong() > RESOURCES_PER_PUBLICATION_MAX_BYTES - currentResourceBytes - total) {
                            throw PublicationLimitException(PublicationStorageLimit.PUBLICATION_RESOURCE_BYTES, RESOURCES_PER_PUBLICATION_MAX_BYTES)
                        }
                        if (count.toLong() > STORE_MAX_BYTES - accountedBytes) {
                            throw PublicationLimitException(PublicationStorageLimit.OWNED_STORAGE_BYTES, STORE_MAX_BYTES)
                        }
                        output.write(buffer, 0, count)
                        accountedBytes += count
                        digest.update(buffer, 0, count)
                        total += count
                    }
                    output.flush()
                    fileOutput.fd.sync()
                }
                if (total == 0L) throw PublicationStorageException("A local image resource cannot be empty")
                val sha256 = digest.toHex()
                if (expectedSha256 != null && expectedSha256 != sha256) {
                    throw PublicationStorageException("Local resource SHA-256 does not match the parser reference")
                }
                val target = if (publishImmediately) data else part
                moveAtomically(temporary, target)
                writeResourceMetadata(metadataFile, PublicationResourceMetadata(mimeType, sha256, total))
                refreshBytes()
                return PublicationStoredResource(resourceId, mimeType, sha256, total)
            } catch (error: Throwable) {
                temporary.delete()
                if (publishImmediately) data.delete() else part.delete()
                metadataFile.delete()
                refreshBytes()
                throw error
            }
        }
    }

    private fun requireOwned(staged: StagedOriginal) {
        require(staged.store === this) { "Staged original belongs to another store" }
        if (staged.isClosed) throw PublicationStorageException("Staged original is closed")
        ensureConfined(staged.jobDirectory)
        val journal = readJournal(staged.jobDirectory)
        if (journal?.jobId != staged.jobId || journal.state != AdmissionState.READY ||
            journal.sha256 != staged.sha256 || journal.sizeBytes != staged.sizeBytes
        ) throw PublicationStorageException("Staged original is no longer available")
    }

    private fun writeJournal(directory: File, journal: AdmissionJournal) {
        writeAtomic(childFile(directory, "job.bin")) { output ->
            output.writeInt(STORE_MAGIC)
            output.writeInt(STORE_VERSION)
            output.writeByte(1)
            output.writeUTF(journal.jobId)
            output.writeByte(journal.state.ordinal)
            output.writeBoolean(journal.publicationId != null)
            journal.publicationId?.let(output::writeUTF)
            output.writeBoolean(journal.format != null)
            journal.format?.let { output.writeUTF(it.name) }
            output.writeBoolean(journal.sha256 != null)
            journal.sha256?.let(output::writeUTF)
            output.writeLong(journal.sizeBytes)
        }
    }

    private fun readJournal(directory: File): AdmissionJournal? = readRecord(childFile(directory, "job.bin")) { input ->
        if (input.readInt() != STORE_MAGIC || input.readInt() != STORE_VERSION || input.readUnsignedByte() != 1) return@readRecord null
        val jobId = input.readUTF().also { requireCanonicalUuid(it, "jobId") }
        val state = AdmissionState.entries.getOrNull(input.readUnsignedByte()) ?: return@readRecord null
        val publicationId = if (input.readBoolean()) input.readUTF().also { BookIdentity.Local(it) } else null
        val format = if (input.readBoolean()) LocalPublicationFormat.valueOf(input.readUTF()) else null
        val sha = if (input.readBoolean()) input.readUTF().also { require(SHA256_PATTERN.matches(it)) } else null
        val size = input.readLong()
        val readyValid = state != AdmissionState.READY || sha != null && size in 1..ORIGINAL_MAX_BYTES
        val commitValid = state != AdmissionState.COMMITTING ||
            publicationId != null && format != null && sha != null && size in 1..ORIGINAL_MAX_BYTES
        if (input.available() != 0 || size < 0L || !readyValid || !commitValid) return@readRecord null
        AdmissionJournal(jobId, state, publicationId, format, sha, size)
    }
    private fun writeAtomic(target: File, encode: (DataOutputStream) -> Unit) {
        ensureConfined(target)
        val parent = target.parentFile ?: throw PublicationStorageException("Invalid publication journal path")
        ensureDirectory(parent)
        val temporary = childFile(parent, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { fileOutput ->
                val output = DataOutputStream(BufferedOutputStream(fileOutput))
                encode(output)
                output.flush()
                fileOutput.fd.sync()
            }
            synchronized(lock) {
                val oldSize = if (target.isFile) target.length() else 0L
                val newSize = temporary.length()
                if (newSize > STORE_MAX_BYTES - accountedBytes) {
                    throw PublicationLimitException(PublicationStorageLimit.OWNED_STORAGE_BYTES, STORE_MAX_BYTES)
                }
                moveAtomically(temporary, target, replace = true)
                accountedBytes = (accountedBytes - oldSize + newSize).coerceAtLeast(0L)
            }
        } catch (error: Throwable) {
            temporary.delete()
            refreshBytes()
            throw error
        }
    }

    private fun writeOriginalMetadata(directory: File, metadata: OriginalMetadata) {
        writeAtomic(childFile(directory, "original.meta")) { output ->
            output.writeInt(STORE_MAGIC)
            output.writeInt(STORE_VERSION)
            output.writeUTF(metadata.format.name)
            output.writeUTF(metadata.sha256)
            output.writeLong(metadata.sizeBytes)
        }
    }

    private fun readOriginalMetadata(directory: File): OriginalMetadata? = readRecord(childFile(directory, "original.meta")) { input ->
        if (input.readInt() != STORE_MAGIC || input.readInt() != STORE_VERSION) return@readRecord null
        val format = LocalPublicationFormat.valueOf(input.readUTF())
        val sha = input.readUTF().also { require(SHA256_PATTERN.matches(it)) }
        val size = input.readLong()
        if (input.available() != 0 || size !in 1..ORIGINAL_MAX_BYTES) return@readRecord null
        OriginalMetadata(format, sha, size)
    }

    private fun writeResourceMetadata(file: File, metadata: PublicationResourceMetadata) {
        writeAtomic(file) { output ->
            output.writeInt(STORE_MAGIC)
            output.writeInt(STORE_VERSION)
            output.writeUTF(metadata.mimeType)
            output.writeUTF(metadata.sha256)
            output.writeLong(metadata.sizeBytes)
        }
    }

    private fun readResourceMetadata(file: File): PublicationResourceMetadata? = readRecord(file) { input ->
        if (input.readInt() != STORE_MAGIC || input.readInt() != STORE_VERSION) return@readRecord null
        val mime = input.readUTF()
        if (mime !in SUPPORTED_RESOURCE_MIME_TYPES) return@readRecord null
        val sha = input.readUTF().also { require(SHA256_PATTERN.matches(it)) }
        val size = input.readLong()
        if (input.available() != 0 || size !in 1..RESOURCE_MAX_BYTES) return@readRecord null
        PublicationResourceMetadata(mime, sha, size)
    }

    private fun resourceFingerprint(file: File): Pair<String, Long>? {
        if (!file.isFile || file.length() !in 1..RESOURCE_MAX_BYTES) return null
        return try {
            FileInputStream(file).use { digestAndCount(it) }
        } catch (_: IOException) {
            null
        }
    }

    private fun verifiedResourceFile(file: File, metadata: PublicationResourceMetadata): Boolean =
        resourceFingerprint(file) == (metadata.sha256 to metadata.sizeBytes)

    private fun deletePrivateFile(file: File) {
        ensureConfined(file)
        if (file.exists() && !file.delete() && file.exists()) {
            throw PublicationStorageException("Cannot remove derived publication resource data")
        }
    }

    // Native writes hold this store's lock until promotion: a prior temporary has no live writer here.
    private fun removeAdmissionTemporaryFiles(directory: File, expectedOriginal: OriginalMetadata? = null) {
        ensureConfined(directory)
        var removed = false
        directory.listFiles()?.forEach { file ->
            if (!file.isFile || !file.name.startsWith(".admission.") || !file.name.endsWith(".tmp") ||
                canonicalUuidOrNull(file.name.removePrefix(".admission.").removeSuffix(".tmp")) == null) return@forEach
            if (!removed && expectedOriginal != null && !verifiedOriginalFile(directory, expectedOriginal)) return
            deletePrivateFile(file)
            removed = true
        }
        if (removed) refreshBytes()
    }

    private fun removeResourceTemporaryFiles(directory: File, resourceId: String) {
        directory.listFiles()?.forEach { file ->
            if (file.name.startsWith(".$resourceId.") && file.name.endsWith(".tmp")) deletePrivateFile(file)
        }
    }

    private fun removeResourceFiles(directory: File, resourceId: String) {
        deletePrivateFile(childFile(directory, "$resourceId.bin"))
        deletePrivateFile(childFile(directory, "$resourceId.part"))
        deletePrivateFile(childFile(directory, "$resourceId.meta"))
        removeResourceTemporaryFiles(directory, resourceId)
        refreshBytes()
    }

    private fun writeDeleteJournal(directory: File, journal: DeleteJournal) {
        writeAtomic(childFile(directory, "delete.bin")) { output ->
            output.writeInt(STORE_MAGIC)
            output.writeInt(STORE_VERSION)
            output.writeByte(2)
            output.writeUTF(journal.jobId)
            output.writeUTF(journal.publicationId)
            output.writeByte(journal.kind.ordinal)
            output.writeUTF(journal.format.name)
            output.writeUTF(journal.sha256)
            output.writeLong(journal.sizeBytes)
            output.writeBoolean(journal.catalogCommitted)
        }
    }

    private fun readDeleteJournal(directory: File): DeleteJournal? = readRecord(childFile(directory, "delete.bin")) { input ->
        if (input.readInt() != STORE_MAGIC || input.readInt() != STORE_VERSION || input.readUnsignedByte() != 2) return@readRecord null
        val jobId = input.readUTF().also { requireCanonicalUuid(it, "jobId") }
        val id = input.readUTF().also { BookIdentity.Local(it) }
        val kind = DeleteKind.entries.getOrNull(input.readUnsignedByte()) ?: return@readRecord null
        val format = LocalPublicationFormat.valueOf(input.readUTF())
        val sha = input.readUTF().also { require(SHA256_PATTERN.matches(it)) }
        val size = input.readLong()
        val committed = input.readBoolean()
        if (input.available() != 0 || size !in 1..ORIGINAL_MAX_BYTES) return@readRecord null
        DeleteJournal(jobId, id, kind, format, sha, size, committed)
    }

    private inline fun <T> readRecord(file: File, decode: (DataInputStream) -> T?): T? {
        if (!file.isFile || file.length() !in 1..4096) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use(decode)
        } catch (_: Exception) {
            null
        }
    }


    private fun digestAndCount(input: InputStream): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            digest.update(buffer, 0, count)
            total += count
        }
        return digest.toHex() to total
    }

    private fun verifiedOriginalFile(directory: File, metadata: OriginalMetadata): Boolean {
        val file = childFile(directory, "original.bin")
        if (!file.isFile || file.length() != metadata.sizeBytes) return false
        return try {
            FileInputStream(file).use { digestAndCount(it) == (metadata.sha256 to metadata.sizeBytes) }
        } catch (_: IOException) {
            false
        }
    }

    private fun resourceMetadataFiles(directory: File): List<File> = directory.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".meta") }
        .orEmpty()


    private fun refreshBytes() {
        accountedBytes = scanBytes()
    }

    private fun scanBytes(): Long {
        var total = 0L
        rootDirectory.walkTopDown().forEach { file ->
            if (file.isFile) {
                ensureConfined(file)
                val size = file.length()
                if (size > STORE_MAX_BYTES - total) return STORE_MAX_BYTES
                total += size
            }
        }
        return total
    }

    private fun deleteOwnedTree(directory: File) {
        if (!directory.exists()) return
        ensureConfined(directory)
        directory.walkBottomUp().forEach { path ->
            ensureConfined(path)
            if (!path.delete() && path.exists()) throw PublicationStorageException("Cannot remove owned publication staging data")
        }
        synchronized(lock) { refreshBytes() }
    }

    private fun childDirectory(parent: File, name: String): File {
        require(name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != "..")
        val child = File(parent, name).canonicalFile
        ensureConfined(parent)
        ensureConfined(child)
        return child
    }

    private fun childFile(parent: File, name: String): File = childDirectory(parent, name)

    private fun ensureDirectory(directory: File) {
        ensureConfined(directory)
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw PublicationStorageException("Cannot create private publication directory")
        }
        ensureConfined(directory)
    }

    private fun ensureConfined(file: File) {
        val canonical = try {
            file.canonicalFile
        } catch (error: IOException) {
            throw PublicationStorageException("Cannot resolve private publication storage path", error)
        }
        if (canonical != rootDirectory && !isDescendant(rootDirectory, canonical)) {
            throw PublicationStorageException("Private publication storage path escapes its owner")
        }
        if (Files.isSymbolicLink(file.toPath())) throw PublicationStorageException("Symbolic links are not allowed in publication storage")
    }

    private fun moveAtomically(from: File, to: File, replace: Boolean = false) {
        ensureConfined(from)
        ensureConfined(to.parentFile ?: throw PublicationStorageException("Invalid atomic promotion target"))
        if (!replace && to.exists()) throw PublicationStorageException("Refusing to replace existing publication data")
        try {
            if (replace) Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            else Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (error: IOException) {
            throw PublicationStorageException("Unable to atomically promote private publication data", error)
        }
    }

    private fun requireResourceReference(resourceId: String, mimeType: String, expectedSha256: String?) {
        requireCanonicalUuid(resourceId, "resourceId")
        require(mimeType in SUPPORTED_RESOURCE_MIME_TYPES) { "Unsupported local resource MIME type" }
        expectedSha256?.let { require(SHA256_PATTERN.matches(it)) { "Invalid expected resource SHA-256" } }
    }

    private fun safeToReclaimJournallessDeletion(operation: File): Boolean {
        if (!operation.isDirectory) return false
        val entries = operation.listFiles() ?: return false
        return entries.all { entry ->
            entry.isFile && entry.name.startsWith(".delete.bin.") && entry.name.endsWith(".tmp") &&
                canonicalUuidOrNull(entry.name.removePrefix(".delete.bin.").removeSuffix(".tmp")) != null
        }
    }

    private fun hasPendingDeletion(publicationId: String): Boolean = synchronized(lock) {
        deletionsDirectory.listFiles()?.any { operation ->
            val ownerName = if (operation.name.startsWith(".pending-")) operation.name.removePrefix(".pending-") else operation.name
            val operationId = canonicalUuidOrNull(ownerName) ?: return@any false
            val journal = readDeleteJournal(operation)
            journal != null && journal.jobId == operationId && journal.publicationId == publicationId
        } == true
    }
    private fun commitJournalMatches(directory: File, jobId: String): Boolean = readJournal(directory)?.jobId == jobId

    private fun committedOriginalPath(value: CommittedOriginal): File {
        ensureConfined(value.directory)
        return childFile(value.directory, "original.bin")
    }

    private fun currentMetadata(value: CommittedOriginal): OriginalMetadata? {
        synchronized(lock) {
            val metadata = readOriginalMetadata(value.directory) ?: return null
            return metadata.takeIf { metadata.format == value.format && metadata.sha256 == value.sha256 && metadata.sizeBytes == value.sizeBytes }
        }
    }

    private fun confirmCommit(receipt: PublicationCommit): Boolean = synchronized(lock) {
        val directory = childDirectory(booksDirectory, receipt.original.publicationId)
        val jobFile = childFile(directory, "job.bin")
        if (!jobFile.exists()) {
            val expected = OriginalMetadata(receipt.original.format, receipt.original.sha256, receipt.original.sizeBytes)
            return readOriginalMetadata(directory) == expected && verifiedOriginalFile(directory, expected)
        }
        val journal = readJournal(directory) ?: return false
        val expected = OriginalMetadata(receipt.original.format, receipt.original.sha256, receipt.original.sizeBytes)
        if (journal.jobId != receipt.jobId || journal.state != AdmissionState.COMMITTING ||
            journal.publicationId != receipt.original.publicationId || journal.format != expected.format ||
            journal.sha256 != expected.sha256 || journal.sizeBytes != expected.sizeBytes ||
            readOriginalMetadata(directory) != expected || !verifiedOriginalFile(directory, expected)
        ) return false
        val removed = childFile(directory, "job.bin").delete()
        if (removed) refreshBytes()
        removed
    }

    private fun rollbackCommit(receipt: PublicationCommit): Boolean = synchronized(lock) {
        val directory = childDirectory(booksDirectory, receipt.original.publicationId)
        if (!directory.isDirectory || !commitJournalMatches(directory, receipt.jobId)) return false
        val journal = readJournal(directory) ?: return false
        val expected = OriginalMetadata(receipt.original.format, receipt.original.sha256, receipt.original.sizeBytes)
        if (journal.state != AdmissionState.COMMITTING || journal.publicationId != receipt.original.publicationId ||
            journal.format != expected.format || journal.sha256 != expected.sha256 || journal.sizeBytes != expected.sizeBytes ||
            readOriginalMetadata(directory) != expected || !verifiedOriginalFile(directory, expected)
        ) return false
        deleteOwnedTree(directory)
        true
    }

    private fun stagePath(staged: StagedOriginal): File {
        requireOwned(staged)
        val file = childFile(staged.jobDirectory, "original.part")
        if (!file.isFile || file.length() != staged.sizeBytes) throw PublicationStorageException("Staged original bytes are unavailable")
        return file
    }


    private fun ownedSourceFile(source: PublicationSource): File = when (source) {
        is StagedOriginal -> stagePath(source)
        is CommittedOriginal -> committedOriginalPath(source)
        else -> throw IllegalArgumentException("Unsupported publication source owner")
    }

    private fun openSource(source: PublicationSource): InputStream {
        val file = ownedSourceFile(source)
        if (source is CommittedOriginal &&
            (currentMetadata(source) == null || childFile(source.directory, "job.bin").exists() || hasPendingDeletion(source.publicationId))
        ) throw PublicationStorageException("Committed original is not catalog-confirmed and verified")
        if (file.length() != source.sizeBytes) throw PublicationStorageException("Original byte count changed")
        return FileInputStream(file)
    }

    private fun openArchive(source: PublicationSource): PublicationZipArchive {
        val file = ownedSourceFile(source)
        if (source is CommittedOriginal &&
            (currentMetadata(source) == null || childFile(source.directory, "job.bin").exists() || hasPendingDeletion(source.publicationId))
        ) throw PublicationStorageException("Committed original is not catalog-confirmed and verified")
        if (file.length() != source.sizeBytes) throw PublicationStorageException("Original byte count changed")
        val zip = try {
            ZipFile(file)
        } catch (error: IOException) {
            throw PublicationStorageException("Original is not a readable ZIP archive", error)
        }
        return object : PublicationZipArchive {
            override fun entries(): Sequence<PublicationZipEntry> = sequence {
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    yield(
                        PublicationZipEntry(
                            name = entry.name,
                            sizeBytes = entry.size,
                            compressedSizeBytes = entry.compressedSize,
                            compressionMethod = entry.method,
                            isDirectory = entry.isDirectory,
                        ),
                    )
                }
            }

            override fun openEntry(name: String): InputStream {
                val entry = zip.getEntry(name) ?: throw IOException("Publication ZIP entry is missing")
                if (entry.isDirectory) throw IOException("Publication ZIP entry is a directory")
                return zip.getInputStream(entry)
            }

            override fun close() = zip.close()
        }
    }


    private data class OriginalMetadata(val format: LocalPublicationFormat, val sha256: String, val sizeBytes: Long)
    private data class PublicationResourceMetadata(val mimeType: String, val sha256: String, val sizeBytes: Long)
    private data class AdmissionJournal(
        val jobId: String,
        val state: AdmissionState,
        val publicationId: String? = null,
        val format: LocalPublicationFormat? = null,
        val sha256: String? = null,
        val sizeBytes: Long = 0L,
    )
    private data class DeleteJournal(
        val jobId: String,
        val publicationId: String,
        val kind: DeleteKind,
        val format: LocalPublicationFormat,
        val sha256: String,
        val sizeBytes: Long,
        val catalogCommitted: Boolean = false,
    )

    private enum class AdmissionState { STAGING, READY, COMMITTING }
    private enum class DeleteKind { REMOVE_ORIGINAL, DELETE_PUBLICATION }

    private fun DeleteKind.toJobKind(): PublicationJobKind = when (this) {
        DeleteKind.REMOVE_ORIGINAL -> PublicationJobKind.REMOVE_ORIGINAL
        DeleteKind.DELETE_PUBLICATION -> PublicationJobKind.DELETE_PUBLICATION
    }

    class StagedOriginal internal constructor(
        internal val store: PublicationOriginalStore,
        internal val jobId: String,
        internal val jobDirectory: File,
        override val sha256: String,
        override val sizeBytes: Long,
    ) : PublicationSource, Closeable {
        @Volatile internal var isClosed: Boolean = false
            private set
        internal var admissionPublicationId: String? = null
        internal var admissionFormat: LocalPublicationFormat? = null

        override fun openInputStream(): InputStream = store.openSource(this)
        override fun openArchive(): PublicationZipArchive = store.openArchive(this)

        fun addResource(
            resourceId: String,
            mimeType: String,
            input: InputStream,
            expectedSha256: String? = null,
        ): PublicationStoredResource = store.stageResource(this, resourceId, mimeType, input, expectedSha256)

        fun addAdmissionRecord(record: PublicationAdmissionRecord, cancelled: () -> Unit = {}) {
            store.stageAdmissionRecord(this, record, cancelled)
        }

        override fun close() {
            if (isClosed) return
            isClosed = true
            if (jobDirectory.exists()) store.deleteOwnedTree(jobDirectory)
        }
    }

    class CommittedOriginal internal constructor(
        internal val store: PublicationOriginalStore,
        val publicationId: String,
        internal val directory: File,
        val format: LocalPublicationFormat,
        override val sha256: String,
        override val sizeBytes: Long,
    ) : PublicationSource {
        init { BookIdentity.Local(publicationId) }
        override fun openInputStream(): InputStream = store.openSource(this)
        override fun openArchive(): PublicationZipArchive = store.openArchive(this)
    }

    class PublicationCommit internal constructor(
        private val store: PublicationOriginalStore,
        internal val jobId: String,
        val original: CommittedOriginal,
    ) {
        /** Remove the recovery journal after the caller's catalog transaction has committed. */
        fun confirmCatalogCommit(): Boolean = store.confirmCommit(this)

        /** Roll back only this exact unconfirmed promotion; never removes a pre-existing publication. */
        fun rollbackUncatalogued(): Boolean = store.rollbackCommit(this)
    }


    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    private fun MessageDigest.toHex(): String = toHex(digest())

    private fun requireCanonicalUuid(value: String, name: String) {
        val parsed = runCatching { UUID.fromString(value) }.getOrNull()
        require(parsed != null && parsed.toString() == value) { "$name must be a canonical lowercase UUID" }
    }

    private fun canonicalUuidOrNull(value: String): String? =
        runCatching { UUID.fromString(value).toString().takeIf { it == value } }.getOrNull()

    private fun isDescendant(parent: File, candidate: File): Boolean {
        val base = parent.path.trimEnd(File.separatorChar) + File.separatorChar
        return candidate.path == parent.path || candidate.path.startsWith(base)
    }

}
