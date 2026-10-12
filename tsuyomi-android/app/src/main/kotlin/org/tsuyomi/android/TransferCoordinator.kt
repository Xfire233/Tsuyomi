/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.PushbackInputStream
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tsuyomi.core.database.RoomTransferRepository
import org.tsuyomi.core.preferences.PortableReaderPreferencesRepository
import org.tsuyomi.core.publication.PublicationArchiveInspection
import org.tsuyomi.core.publication.PublicationArchiveOriginal
import org.tsuyomi.core.publication.PublicationArchiveOriginalStatus
import org.tsuyomi.core.publication.PublicationOriginalArchive
import org.tsuyomi.feature.backup.TransferUiState
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.backup.TransferCodec
import org.tsuyomi.shared.model.BookIdentity

data class PreparedExport(
    val suggestedFileName: String,
    val ownerGeneration: Long,
    val canonicalDigest: String,
    val includesOriginals: Boolean = false,
)

class TransferCoordinator(
    context: Context,
    private val repository: RoomTransferRepository,
    preferences: PortableReaderPreferencesRepository,
    applyPreferenceImport: suspend (PortableReaderPreferences?, Boolean, String) -> Unit = preferences::applyImport,
) {
    private val appContext = context.applicationContext
    private val application get() = appContext as TsuyomiApplication
    private val presentation = TransferPresentationState()
    private val recoveryMutex = Mutex()
    private val exportPreflightStore = TransferExportPreflightStore(File(appContext.cacheDir, "transfer-export-preflight"))
    private val importOwner = TransferImportSessionOwner(
        appContext,
        repository,
        applyPreferenceImport,
        presentation,
    ) { application.localPublications.originals }
    private var pendingArchive: PublicationArchiveInspection? = null
    private val archives by lazy { PublicationOriginalArchive(application.localPublications.originals) }

    val state: TransferUiState get() = presentation.state
    val recoveryReady: Boolean get() = presentation.recoveryReady

    suspend fun recoverPendingImport(): Set<String>? = recoveryMutex.withLock {
        if (!presentation.recoveryReady) {
            withContext(Dispatchers.IO) { exportPreflightStore.sweepOrphans() }
            importOwner.recoverPendingImport()
        }
        importOwner.pendingPreparedPublicationIds()
    }

    suspend fun retryRecovery() = recoveryMutex.withLock { importOwner.retryRecovery() }
    suspend fun abortRecovery() = recoveryMutex.withLock { importOwner.abortRecovery() }

    suspend fun readForReview(uri: Uri, resolver: ContentResolver) {
        pendingArchive?.close()
        pendingArchive = null
        var inspection: PublicationArchiveInspection? = null
        presentation.state = TransferUiState.Working(appContext.getString(R.string.transfer_reading_file))
        try {
            application.localPublications.recover()
            withContext(Dispatchers.IO) {
                openPublicationInput(resolver, uri).use { owned ->
                    PushbackInputStream(owned, 4).use { input ->
                        val prefix = ByteArray(4)
                        var count = 0
                        while (count < prefix.size) {
                            val value = input.read()
                            if (value < 0) break
                            prefix[count++] = value.toByte()
                        }
                        input.unread(prefix, 0, count)
                        if (count == 4 && prefix.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04))) {
                            inspection = archives.inspect(input)
                            checkNotNull(inspection).openTransferPayload().use { importOwner.readForReview(it) }
                        } else importOwner.readForReview(input)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            val reviewed = presentation.state as? TransferUiState.Review
            val archive = inspection
            if (reviewed != null && archive != null) {
                val originals = importOwner.reviewedOriginals
                require(archive.originals.all { originals[it.publicationId] == it }) { "Archive manifest does not match transfer metadata" }
                pendingArchive = archive
                inspection = null
                presentation.state = reviewed.copy(value = reviewed.value.copy(archiveOriginalCount = archive.originals.size))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            importOwner.cancelReview()
            presentation.state = TransferUiState.Failure(appContext.getString(R.string.transfer_import_failed_code, "invalid-transfer"))
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { inspection?.close() }
        }
    }

    suspend fun confirmImport() {
        val inspection = pendingArchive
        if (inspection == null) { importOwner.confirmImport(); return }
        try {
            withContext(Dispatchers.IO) {
                val catalog = application.libraryRepository.localPublications().catalogOriginals()
                val result = archives.restore(inspection, catalog) { _, originals ->
                    val restored = originals.filter { it.status == PublicationArchiveOriginalStatus.RESTORED ||
                        it.status == PublicationArchiveOriginalStatus.ALREADY_PRESENT }
                        .mapTo(linkedSetOf()) { BookIdentity.Local(it.original.publicationId) }
                    // The existing private import journal carries these validated IDs. Availability and
                    // metadata commit in one Room transaction, including process-death replay.
                    withContext(NonCancellable) { importOwner.confirmImport(restored) }
                }
                if (result.metadataApplied && presentation.state is TransferUiState.Completed) {
                    val completed = presentation.state as TransferUiState.Completed
                    val conflicts = result.originals.count { it.status == PublicationArchiveOriginalStatus.CONFLICT }
                    presentation.state = completed.copy(value = completed.value.copy(originalConflicts = conflicts))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            presentation.state = TransferUiState.Failure(appContext.getString(R.string.transfer_apply_failed))
        } finally {
            if (presentation.state !is TransferUiState.Review) {
                pendingArchive = null
                withContext(NonCancellable + Dispatchers.IO) { inspection.close() }
            }
        }
    }

    fun cancelReview() {
        pendingArchive?.close()
        pendingArchive = null
        importOwner.cancelReview()
    }

    fun dismissResult() {
        pendingArchive?.close()
        pendingArchive = null
        importOwner.dismissResult()
    }

    suspend fun prepareExport(readerPreferences: PortableReaderPreferences, includesOriginals: Boolean = false): PreparedExport? {
        presentation.state = TransferUiState.Working(appContext.getString(R.string.transfer_preparing_export))
        try {
            val prepared = withContext(Dispatchers.IO) {
                val snapshot = repository.exportSnapshot(Instant.now(), readerPreferences)
                val bytes = TransferCodec.encodeBounded(snapshot) ?: run {
                    presentation.state = TransferUiState.Failure(appContext.getString(R.string.transfer_export_too_large))
                    return@withContext null
                }
                if (!includesOriginals) return@withContext exportPreflightStore.prepare(bytes)
                application.localPublications.recover()
                val selected = snapshot.library.mapNotNull { book ->
                    val identity = book.identity as? BookIdentity.Local ?: return@mapNotNull null
                    val metadata = checkNotNull(book.localPublication)
                    val original = application.localPublications.originals.original(identity.publicationId) ?: return@mapNotNull null
                    require(original.format == metadata.format && original.sha256 == metadata.originalSha256 && original.sizeBytes == metadata.originalSizeBytes)
                    PublicationArchiveOriginal(identity.publicationId, metadata.format, metadata.originalSha256, metadata.originalSizeBytes)
                }
                val temporary = exportPreflightStore.createStagingFile()
                try {
                    FileOutputStream(temporary).use { output -> archives.export(bytes, selected, output); output.fd.sync() }
                    exportPreflightStore.prepareArchive(temporary)
                } finally { temporary.delete() }
            }
            if (prepared != null) presentation.state = TransferUiState.Idle
            return prepared
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            presentation.state = TransferUiState.Failure(appContext.getString(R.string.transfer_export_failed))
            return null
        }
    }

    suspend fun restorePreparedExport(ownerGeneration: Long, canonicalDigest: String, includesOriginals: Boolean = false): PreparedExport? {
        val ownership = ExportPreflightOwnership(ownerGeneration, canonicalDigest, includesOriginals)
        return withContext(Dispatchers.IO) {
            exportPreflightStore.restore(ownership) ?: run { exportPreflightStore.clearIfOwned(ownership); null }
        }
    }

    suspend fun cancelPreparedExport(ownerGeneration: Long, canonicalDigest: String, includesOriginals: Boolean = false) {
        val owned = withContext(Dispatchers.IO) {
            exportPreflightStore.clearIfOwned(ExportPreflightOwnership(ownerGeneration, canonicalDigest, includesOriginals))
        }
        if (owned) presentation.state = TransferUiState.Idle
    }

    suspend fun writePreparedExport(uri: Uri, resolver: ContentResolver, ownerGeneration: Long, canonicalDigest: String, includesOriginals: Boolean = false) {
        val ownership = ExportPreflightOwnership(ownerGeneration, canonicalDigest, includesOriginals)
        val coroutine = currentCoroutineContext()
        val success = withContext(Dispatchers.IO) {
            exportPreflightStore.consumeVerifiedFile(ownership) { source ->
                try {
                    val output = resolver.openOutputStream(uri, "w") ?: error("output-unavailable")
                    output.use { stream ->
                        source.inputStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                coroutine.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                stream.write(buffer, 0, count)
                            }
                        }
                        stream.flush()
                    }
                    true
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { false }
            }
        }
        if (success != null) presentation.state = if (success) TransferUiState.Exported
            else TransferUiState.Failure(appContext.getString(R.string.transfer_export_failed))
    }
}
