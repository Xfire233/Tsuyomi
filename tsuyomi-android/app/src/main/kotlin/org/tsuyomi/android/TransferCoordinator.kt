/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.tsuyomi.core.database.ImportSession
import org.tsuyomi.core.database.ImportSessionStatus
import org.tsuyomi.core.database.RoomTransferRepository
import org.tsuyomi.core.preferences.PortableReaderPreferencesRepository
import org.tsuyomi.feature.backup.TransferCompletion
import org.tsuyomi.feature.backup.TransferReview
import org.tsuyomi.feature.backup.TransferUiState
import org.tsuyomi.shared.backup.ImportKind
import org.tsuyomi.shared.backup.ImportSeverity
import org.tsuyomi.shared.backup.ImportParseResult
import org.tsuyomi.shared.backup.ImportPlan
import org.tsuyomi.shared.backup.ImportWarning
import org.tsuyomi.shared.backup.ImportPlanCodec
import org.tsuyomi.shared.backup.ImportSummary
import org.tsuyomi.shared.backup.MAX_TRANSFER_BYTES
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.backup.TransferCodec

data class PreparedExport(
    val suggestedFileName: String,
    val ownerGeneration: Long,
    val canonicalDigest: String,
)

class TransferCoordinator(
    context: Context,
    private val repository: RoomTransferRepository,
    private val preferences: PortableReaderPreferencesRepository,
    private val applyPreferenceImport: suspend (PortableReaderPreferences?, Boolean, String) -> Unit = preferences::applyImport,
) {
    private val appContext = context.applicationContext
    private val planDirectory = File(appContext.noBackupFilesDir, "import-plans")
    private val exportDirectory = File(appContext.cacheDir, "transfer-export-preflight")
    private val exportMetadataFile = File(exportDirectory, "active.properties")
    private var pendingPlan: ImportPlan? = null
    private var pendingPlanBytes: ByteArray? = null
    private var pendingPlanDigest: String? = null
    private var lastExportGeneration = 0L

    var state: TransferUiState by mutableStateOf(TransferUiState.Idle)
        private set
    var recoveryReady: Boolean by mutableStateOf(false)
        private set

    suspend fun recoverPendingImport() {
        if (recoveryReady) return
        withContext(Dispatchers.IO) { runCatching(::sweepOrphanedPreflights) }
        recoverCurrentImport()
    }

    suspend fun retryRecovery() {
        recoverCurrentImport()
    }

    suspend fun abortRecovery() {
        val session = repository.pending() ?: run {
            state = TransferUiState.Idle
            recoveryReady = true
            return
        }
        if (session.status != ImportSessionStatus.PREPARED) return

        state = TransferUiState.Recovery(appContext.getString(R.string.transfer_recovery_working))
        val removed = runCatching {
            withContext(Dispatchers.IO) {
                val file = checkedPlanPath(session.normalizedPlanPath)
                file.delete() || !file.exists()
            }
        }.getOrDefault(false)
        if (!repository.abort(session.id, session.planDigest, cleanupPending = !removed)) {
            exposeRecoveryFailure(session)
            return
        }
        recoveryReady = true
        state = if (removed) {
            TransferUiState.Idle
        } else {
            TransferUiState.RecoveryFailure(
                safeMessage = appContext.getString(R.string.transfer_recovery_blocked),
                canAbort = false,
                canRetryCleanup = true,
            )
        }
    }

    suspend fun readForReview(uri: Uri, resolver: ContentResolver) {
        state = TransferUiState.Working(appContext.getString(R.string.transfer_reading_file))
        val parse = try {
            withContext(Dispatchers.IO) {
                val bytes = readBounded(uri, resolver)
                TransferCodec.parse(bytes)
            }
        } catch (_: Throwable) {
            clearPendingPlan()
            state = TransferUiState.Failure(appContext.getString(R.string.transfer_import_failed_code, "invalid-transfer"))
            return
        }
        when (parse) {
            is ImportParseResult.Fatal -> {
                clearPendingPlan()
                state = TransferUiState.Failure(appContext.getString(R.string.transfer_import_failed_code, parse.safeCode))
            }
            is ImportParseResult.Ready -> {
                val reviewed = try {
                    val plan = repository.withDatabaseConflicts(parse.plan)
                    plan to withContext(Dispatchers.IO) { ImportPlanCodec.encode(plan) }
                } catch (_: Throwable) {
                    clearPendingPlan()
                    state = TransferUiState.Failure(appContext.getString(R.string.transfer_import_failed_code, "conflict-review-failed"))
                    return
                }
                val (reviewedPlan, normalized) = reviewed
                pendingPlan = reviewedPlan
                pendingPlanBytes = normalized
                pendingPlanDigest = TransferCodec.digest(normalized)
                state = TransferUiState.Review(reviewedPlan.toReview())
            }
        }
    }

    suspend fun confirmImport() {
        val plan = pendingPlan ?: return
        val bytes = pendingPlanBytes ?: return
        val digest = pendingPlanDigest ?: return
        state = TransferUiState.Working(appContext.getString(R.string.transfer_applying_file))
        val sessionId = UUID.randomUUID().toString()
        val file = withContext(Dispatchers.IO) { writeNormalizedPlan(sessionId, bytes) }
        try {
            repository.prepare(
                sessionId = sessionId,
                plan = plan,
                planDigest = digest,
                normalizedPlanPath = file.absolutePath,
                preferencePatchJson = preferencePatch(plan),
                startedAt = Instant.now(),
            )
            applyPrepared(sessionId, digest, plan, file)
        } catch (error: Throwable) {
            val active = repository.pending()?.takeIf { it.id == sessionId }
            when (active?.status) {
                ImportSessionStatus.PREPARED -> {
                    val removed = withContext(Dispatchers.IO) { file.delete() || !file.exists() }
                    if (!repository.abort(sessionId, digest, cleanupPending = !removed)) {
                        exposeRecoveryFailure(repository.pending()?.takeIf { it.id == sessionId } ?: active)
                    } else if (removed) {
                        state = TransferUiState.Failure(appContext.getString(R.string.transfer_apply_failed))
                    } else {
                        exposeRecoveryFailure(requireNotNull(repository.pending()?.takeIf { it.id == sessionId }))
                    }
                }
                ImportSessionStatus.ROOM_APPLIED,
                ImportSessionStatus.PREFERENCES_APPLIED,
                ImportSessionStatus.ABORTED_CLEANUP_PENDING,
                -> exposeRecoveryFailure(active)
                ImportSessionStatus.COMPLETED,
                ImportSessionStatus.ABORTED,
                null,
                -> {
                    withContext(Dispatchers.IO) { file.delete() }
                    state = TransferUiState.Failure(appContext.getString(R.string.transfer_apply_failed))
                }
            }
            clearPendingPlan()
        }
    }

    fun cancelReview() {
        clearPendingPlan()
        state = TransferUiState.Idle
    }

    fun dismissResult() {
        clearPendingPlan()
        state = TransferUiState.Idle
    }

    suspend fun prepareExport(readerPreferences: PortableReaderPreferences): PreparedExport? {
        state = TransferUiState.Working(appContext.getString(R.string.transfer_preparing_export))
        val bytes = withContext(Dispatchers.IO) {
            val snapshot = repository.exportSnapshot(Instant.now(), readerPreferences)
            TransferCodec.encodeBounded(snapshot)
        }
        if (bytes == null) {
            state = TransferUiState.Failure(appContext.getString(R.string.transfer_export_too_large))
            return null
        }
        val prepared = runCatching {
            withContext(Dispatchers.IO) { createExportPreflight(bytes) }
        }.getOrElse {
            state = TransferUiState.Failure(appContext.getString(R.string.transfer_export_failed))
            return null
        }
        state = TransferUiState.Idle
        return prepared
    }

    suspend fun cancelPreparedExport(ownerGeneration: Long, canonicalDigest: String) {
        val owned = withContext(Dispatchers.IO) {
            val ownership = PreflightOwnership(ownerGeneration, canonicalDigest)
            (currentPreflightOwnership() == ownership).also { if (it) clearOwnedPreflight(ownership) }
        }
        if (owned) state = TransferUiState.Idle
    }

    suspend fun writePreparedExport(
        uri: Uri,
        resolver: ContentResolver,
        ownerGeneration: Long,
        canonicalDigest: String,
    ) {
        val ownership = PreflightOwnership(ownerGeneration, canonicalDigest)
        val source = withContext(Dispatchers.IO) { verifiedPreflightFile(ownership) } ?: return
        state = TransferUiState.Working(appContext.getString(R.string.transfer_writing_export))
        val success = withContext(Dispatchers.IO) {
            runCatching {
                check(verifiedPreflightFile(ownership) == source)
                val output = resolver.openOutputStream(uri, "w") ?: error("output-unavailable")
                output.use { stream ->
                    source.inputStream().use { input -> input.copyTo(stream) }
                    stream.flush()
                }
            }.isSuccess
        }
        val stillOwned = withContext(Dispatchers.IO) {
            (currentPreflightOwnership() == ownership).also { if (it) clearOwnedPreflight(ownership) }
        }
        if (stillOwned) {
            state = if (success) {
                TransferUiState.Exported
            } else {
                TransferUiState.Failure(appContext.getString(R.string.transfer_export_failed))
            }
        }
    }

    private suspend fun recoverCurrentImport() {
        val pending = repository.pending()
        if (pending == null) {
            recoveryReady = true
            if (state is TransferUiState.Recovery || state is TransferUiState.RecoveryFailure) state = TransferUiState.Idle
            return
        }
        state = TransferUiState.Recovery(appContext.getString(R.string.transfer_recovery_working))
        try {
            resume(pending)
            recoveryReady = true
            if (state is TransferUiState.Recovery) state = TransferUiState.Idle
        } catch (_: Throwable) {
            val current = repository.pending()
            if (current == null) {
                recoveryReady = true
                state = TransferUiState.Idle
            } else {
                exposeRecoveryFailure(current)
            }
        }
    }

    private fun exposeRecoveryFailure(session: ImportSession) {
        recoveryReady = true
        state = TransferUiState.RecoveryFailure(
            safeMessage = appContext.getString(R.string.transfer_recovery_blocked),
            canAbort = session.status == ImportSessionStatus.PREPARED,
            canRetryCleanup = session.status == ImportSessionStatus.ABORTED_CLEANUP_PENDING,
        )
    }

    private suspend fun resume(session: ImportSession) {
        if (session.status == ImportSessionStatus.ABORTED_CLEANUP_PENDING) {
            val file = checkedPlanPath(session.normalizedPlanPath)
            val removed = withContext(Dispatchers.IO) { file.delete() || !file.exists() }
            check(removed && repository.markAbortCleanupComplete(session.id, session.planDigest))
            return
        }
        val file = checkedPlanFile(session.normalizedPlanPath)
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        require(TransferCodec.digest(bytes) == session.planDigest) { "Normalized plan digest mismatch" }
        val plan = ImportPlanCodec.decode(bytes).getOrThrow()
        when (session.status) {
            ImportSessionStatus.PREPARED -> applyPrepared(session.id, session.planDigest, plan, file)
            ImportSessionStatus.ROOM_APPLIED -> applyPreferencesAndComplete(session.id, session.planDigest, plan, file)
            ImportSessionStatus.PREFERENCES_APPLIED -> finish(session.id, session.planDigest, plan, file)
            ImportSessionStatus.COMPLETED, ImportSessionStatus.ABORTED -> withContext(Dispatchers.IO) { file.delete() }
            ImportSessionStatus.ABORTED_CLEANUP_PENDING -> Unit
        }
    }

    private suspend fun applyPrepared(sessionId: String, digest: String, plan: ImportPlan, file: File) {
        repository.applyRoomPlan(sessionId, digest, plan)
        applyPreferencesAndComplete(sessionId, digest, plan, file)
    }

    private suspend fun applyPreferencesAndComplete(sessionId: String, digest: String, plan: ImportPlan, file: File) {
        applyPreferenceImport(plan.readerPreferences, plan.forceManualEInk, digest)
        val status = repository.pending()?.takeIf { it.id == sessionId }?.status
        if (status == ImportSessionStatus.ROOM_APPLIED) check(repository.markPreferencesApplied(sessionId, digest))
        finish(sessionId, digest, plan, file)
    }

    private suspend fun finish(sessionId: String, digest: String, plan: ImportPlan, file: File) {
        val completedAt = Instant.now()
        val summary = ImportSummary(sessionId, plan.kind, plan.books.size, plan.shelves.size, plan.warnings.size, completedAt)
        val status = repository.pending()?.takeIf { it.id == sessionId }?.status
        if (status == ImportSessionStatus.PREFERENCES_APPLIED) check(repository.complete(sessionId, digest, summary))
        withContext(Dispatchers.IO) { file.delete() }
        clearPendingPlan()
        state = TransferUiState.Completed(TransferCompletion(plan.books.size, plan.shelves.size, plan.warnings.size))
    }

    private fun checkedPlanPath(path: String): File {
        require(planDirectory.isDirectory || planDirectory.mkdirs())
        val directory = planDirectory.canonicalFile
        val file = File(path).canonicalFile
        require(file.parentFile == directory && file.name.endsWith(".json")) { "Invalid normalized plan path" }
        return file
    }

    private fun checkedPlanFile(path: String): File = checkedPlanPath(path).also {
        require(it.isFile) { "Normalized plan missing" }
    }

    private fun writeNormalizedPlan(sessionId: String, bytes: ByteArray): File {
        require(planDirectory.isDirectory || planDirectory.mkdirs())
        val target = File(planDirectory, "$sessionId.json")
        val temporary = File(planDirectory, "$sessionId.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        require(temporary.renameTo(target)) { "Unable to commit normalized plan" }
        return target
    }

    private fun createExportPreflight(bytes: ByteArray): PreparedExport {
        require(bytes.size <= MAX_TRANSFER_BYTES)
        require(exportDirectory.isDirectory || exportDirectory.mkdirs())
        val previous = currentPreflightOwnership()
        previous?.let(::clearOwnedPreflight)
        val ownerGeneration = maxOf(lastExportGeneration, previous?.ownerGeneration ?: 0L) + 1L
        val canonicalDigest = TransferCodec.digest(bytes)
        val fileName = preflightFileName(ownerGeneration, canonicalDigest)
        val target = checkedPreflightPath(fileName)
        val temporary = File(exportDirectory, "$fileName.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        require(temporary.renameTo(target)) { "Unable to commit export preflight" }
        val ownership = PreflightOwnership(ownerGeneration, canonicalDigest)
        writePreflightMetadata(ownership)
        lastExportGeneration = ownerGeneration
        return PreparedExport("tsuyomi-${FILE_DATE.format(Instant.now())}.json", ownerGeneration, canonicalDigest)
    }

    private fun currentPreflightOwnership(): PreflightOwnership? {
        return runCatching {
            if (!exportMetadataFile.isFile) return@runCatching null
            val properties = Properties().apply { FileInputStream(exportMetadataFile).use { input -> load(input) } }
            val ownerGeneration = properties.getProperty("ownerGeneration")?.toLongOrNull()?.takeIf { it > 0L }
                ?: return@runCatching null
            val canonicalDigest = properties.getProperty("canonicalDigest")?.takeIf { DIGEST.matches(it) }
                ?: return@runCatching null
            val fileName = properties.getProperty("fileName") ?: return@runCatching null
            require(fileName == preflightFileName(ownerGeneration, canonicalDigest))
            checkedPreflightPath(fileName)
            PreflightOwnership(ownerGeneration, canonicalDigest)
        }.getOrNull()
    }

    private fun writePreflightMetadata(ownership: PreflightOwnership) {
        val temporary = File(exportDirectory, "active.tmp")
        Properties().apply {
            setProperty("ownerGeneration", ownership.ownerGeneration.toString())
            setProperty("canonicalDigest", ownership.canonicalDigest)
            setProperty("fileName", preflightFileName(ownership.ownerGeneration, ownership.canonicalDigest))
            FileOutputStream(temporary).use { output ->
                store(output, null)
                output.fd.sync()
            }
        }
        if (exportMetadataFile.exists()) require(exportMetadataFile.delete()) { "Unable to replace export metadata" }
        require(temporary.renameTo(exportMetadataFile)) { "Unable to commit export metadata" }
    }

    private fun checkedPreflightPath(fileName: String): File {
        require(exportDirectory.isDirectory || exportDirectory.mkdirs())
        val directory = exportDirectory.canonicalFile
        val file = File(directory, fileName).canonicalFile
        require(file.parentFile == directory && file.name == fileName) { "Invalid export preflight path" }
        return file
    }

    private fun verifiedPreflightFile(ownership: PreflightOwnership): File? {
        if (currentPreflightOwnership() != ownership) return null
        val file = checkedPreflightPath(preflightFileName(ownership.ownerGeneration, ownership.canonicalDigest))
        if (!file.isFile || file.length() > MAX_TRANSFER_BYTES.toLong()) return null
        return file.takeIf { TransferCodec.digest(it.readBytes()) == ownership.canonicalDigest }
    }

    private fun clearOwnedPreflight(ownership: PreflightOwnership) {
        if (currentPreflightOwnership() != ownership) return
        val file = checkedPreflightPath(preflightFileName(ownership.ownerGeneration, ownership.canonicalDigest))
        if (file.exists() && verifiedPreflightFile(ownership) != null) file.delete()
        if (!file.exists()) exportMetadataFile.delete()
    }

    private fun sweepOrphanedPreflights() {
        if (!exportDirectory.isDirectory) return
        val active = currentPreflightOwnership()
        exportDirectory.listFiles()?.forEach { listedFile ->
            val match = PREFLIGHT_FILE.matchEntire(listedFile.name) ?: return@forEach
            val file = runCatching { checkedPreflightPath(listedFile.name) }.getOrNull() ?: return@forEach
            val ownership = PreflightOwnership(match.groupValues[1].toLong(), match.groupValues[2])
            if (ownership != active && file.isFile && file.length() <= MAX_TRANSFER_BYTES.toLong() &&
                TransferCodec.digest(file.readBytes()) == ownership.canonicalDigest
            ) {
                file.delete()
            }
        }
    }

    private fun preflightFileName(ownerGeneration: Long, canonicalDigest: String): String =
        "preflight-$ownerGeneration-$canonicalDigest.json"

    private fun readBounded(uri: Uri, resolver: ContentResolver): ByteArray {
        val input = resolver.openInputStream(uri) ?: error("input-unavailable")
        input.use {
            val output = java.io.ByteArrayOutputStream(minOf(64 * 1024, MAX_TRANSFER_BYTES))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_TRANSFER_BYTES) return ByteArray(MAX_TRANSFER_BYTES + 1)
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun clearPendingPlan() {
        pendingPlan = null
        pendingPlanBytes = null
        pendingPlanDigest = null
    }

    private fun preferencePatch(plan: ImportPlan): String =
        "{\"reader\":${plan.readerPreferences != null},\"forceManualEInk\":${plan.forceManualEInk}}"

    private fun ImportPlan.toReview(): TransferReview {
        fun ImportWarning.safeLabel(): String =
            listOfNotNull(safeCode, safeRecordRef, fieldName).joinToString(" · ")
        return TransferReview(
            formatLabel = if (kind == ImportKind.HIKARI_BACKUP) "Hikari Novel" else "Tsuyomi transfer v1",
            bookCount = books.size,
            shelfCount = shelves.size,
            smartCollectionCount = smartCollections.size,
            disabledDraftCount = subscriptionDrafts.size,
            warningCodes = warnings.filter { it.severity == ImportSeverity.WARNING }.sortedBy { it.ordinal }.map(ImportWarning::safeLabel),
            conflictCodes = warnings.filter { it.severity == ImportSeverity.CONFLICT }.sortedBy { it.ordinal }.map(ImportWarning::safeLabel),
        )
    }

    private data class PreflightOwnership(
        val ownerGeneration: Long,
        val canonicalDigest: String,
    )

    private companion object {
        val DIGEST = Regex("[0-9a-f]{64}")
        val PREFLIGHT_FILE = Regex("preflight-([1-9][0-9]*)-([0-9a-f]{64})\\.json")
        val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    }
}
