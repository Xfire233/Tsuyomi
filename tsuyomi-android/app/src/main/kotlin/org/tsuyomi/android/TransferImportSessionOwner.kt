/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tsuyomi.core.database.ImportSession
import org.tsuyomi.core.database.ImportSessionStatus
import org.tsuyomi.core.database.RoomTransferRepository
import org.tsuyomi.feature.backup.TransferCompletion
import org.tsuyomi.feature.backup.TransferReview
import org.tsuyomi.feature.backup.TransferUiState
import org.tsuyomi.shared.backup.ImportKind
import org.tsuyomi.shared.backup.ImportParseResult
import org.tsuyomi.shared.backup.ImportPlan
import org.tsuyomi.shared.backup.ImportPlanCodec
import org.tsuyomi.shared.backup.ImportSeverity
import org.tsuyomi.shared.backup.ImportSummary
import org.tsuyomi.shared.backup.ImportWarning
import org.tsuyomi.shared.backup.MAX_TRANSFER_BYTES
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.backup.TransferCodec

@Stable
internal class TransferPresentationState {
    var state: TransferUiState by mutableStateOf(TransferUiState.Idle)
    var recoveryReady: Boolean by mutableStateOf(false)
}

internal class TransferImportSessionOwner(
    context: Context,
    private val repository: RoomTransferRepository,
    private val applyPreferenceImport: suspend (PortableReaderPreferences?, Boolean, String) -> Unit,
    private val presentation: TransferPresentationState,
) {
    private val appContext = context.applicationContext
    private val planDirectory = File(appContext.noBackupFilesDir, "import-plans")
    private var pendingPlan: ImportPlan? = null
    private var pendingBasePlan: ImportPlan? = null
    private var pendingPlanBytes: ByteArray? = null
    private var pendingPlanDigest: String? = null

    suspend fun recoverPendingImport() {
        if (presentation.recoveryReady) return
        recoverCurrentImport()
    }

    suspend fun retryRecovery() {
        recoverCurrentImport()
    }

    suspend fun abortRecovery() {
        val session = repository.pending() ?: run {
            presentation.state = TransferUiState.Idle
            presentation.recoveryReady = true
            return
        }
        if (session.status != ImportSessionStatus.PREPARED) return

        presentation.state = TransferUiState.Recovery(appContext.getString(R.string.transfer_recovery_working))
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
        presentation.recoveryReady = true
        presentation.state = if (removed) {
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
        presentation.state = TransferUiState.Working(appContext.getString(R.string.transfer_reading_file))
        val parse = try {
            withContext(Dispatchers.IO) {
                val bytes = readBounded(uri, resolver)
                TransferCodec.parse(bytes)
            }
        } catch (_: Throwable) {
            clearPendingPlan()
            presentation.state = TransferUiState.Failure(
                appContext.getString(R.string.transfer_import_failed_code, "invalid-transfer"),
            )
            return
        }
        when (parse) {
            is ImportParseResult.Fatal -> {
                clearPendingPlan()
                presentation.state = TransferUiState.Failure(
                    appContext.getString(R.string.transfer_import_failed_code, parse.safeCode),
                )
            }
            is ImportParseResult.Ready -> prepareReview(parse)
        }
    }

    suspend fun confirmImport() {
        val basePlan = pendingBasePlan ?: return
        val reviewedPlan = pendingPlan ?: return
        val refreshedPlan = try {
            repository.withDatabaseConflicts(basePlan)
        } catch (_: Throwable) {
            clearPendingPlan()
            presentation.state = TransferUiState.Failure(
                appContext.getString(R.string.transfer_import_failed_code, "conflict-review-failed"),
            )
            return
        }
        if (refreshedPlan != reviewedPlan) {
            stageReview(refreshedPlan)
            return
        }
        val plan = reviewedPlan
        val bytes = pendingPlanBytes ?: return
        val digest = pendingPlanDigest ?: return
        presentation.state = TransferUiState.Working(appContext.getString(R.string.transfer_applying_file))
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
        } catch (_: Throwable) {
            settleFailedConfirmation(sessionId, digest, file)
            clearPendingPlan()
        }
    }

    fun cancelReview() {
        clearPendingPlan()
        presentation.state = TransferUiState.Idle
    }

    fun dismissResult() {
        clearPendingPlan()
        presentation.state = TransferUiState.Idle
    }

    private suspend fun prepareReview(parse: ImportParseResult.Ready) {
        val reviewed = try {
            val plan = repository.withDatabaseConflicts(parse.plan)
            plan to withContext(Dispatchers.IO) { ImportPlanCodec.encode(plan) }
        } catch (_: Throwable) {
            clearPendingPlan()
            presentation.state = TransferUiState.Failure(
                appContext.getString(R.string.transfer_import_failed_code, "conflict-review-failed"),
            )
            return
        }
        val (reviewedPlan, normalized) = reviewed
        pendingBasePlan = parse.plan
        pendingPlan = reviewedPlan
        pendingPlanBytes = normalized
        pendingPlanDigest = TransferCodec.digest(normalized)
        presentation.state = TransferUiState.Review(reviewedPlan.toReview())
    }

    private suspend fun stageReview(plan: ImportPlan) {
        val normalized = withContext(Dispatchers.IO) { ImportPlanCodec.encode(plan) }
        pendingPlan = plan
        pendingPlanBytes = normalized
        pendingPlanDigest = TransferCodec.digest(normalized)
        presentation.state = TransferUiState.Review(plan.toReview())
    }

    private suspend fun settleFailedConfirmation(sessionId: String, digest: String, file: File) {
        val active = repository.pending()?.takeIf { it.id == sessionId }
        when (active?.status) {
            ImportSessionStatus.PREPARED -> {
                val removed = withContext(Dispatchers.IO) { file.delete() || !file.exists() }
                if (!repository.abort(sessionId, digest, cleanupPending = !removed)) {
                    exposeRecoveryFailure(repository.pending()?.takeIf { it.id == sessionId } ?: active)
                } else if (removed) {
                    presentation.state = TransferUiState.Failure(appContext.getString(R.string.transfer_apply_failed))
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
                presentation.state = TransferUiState.Failure(appContext.getString(R.string.transfer_apply_failed))
            }
        }
    }

    private suspend fun recoverCurrentImport() {
        val pending = repository.pending()
        if (pending == null) {
            presentation.recoveryReady = true
            if (presentation.state is TransferUiState.Recovery || presentation.state is TransferUiState.RecoveryFailure) {
                presentation.state = TransferUiState.Idle
            }
            return
        }
        presentation.state = TransferUiState.Recovery(appContext.getString(R.string.transfer_recovery_working))
        try {
            resume(pending)
            presentation.recoveryReady = true
            if (presentation.state is TransferUiState.Recovery) presentation.state = TransferUiState.Idle
        } catch (_: Throwable) {
            val current = repository.pending()
            if (current == null) {
                presentation.recoveryReady = true
                presentation.state = TransferUiState.Idle
            } else {
                exposeRecoveryFailure(current)
            }
        }
    }

    private fun exposeRecoveryFailure(session: ImportSession) {
        presentation.recoveryReady = true
        presentation.state = TransferUiState.RecoveryFailure(
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
            ImportSessionStatus.COMPLETED,
            ImportSessionStatus.ABORTED,
            -> withContext(Dispatchers.IO) { file.delete() }
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
        val summary = ImportSummary(
            sessionId,
            plan.kind,
            plan.books.size,
            plan.shelves.size,
            plan.warnings.size,
            completedAt,
        )
        val status = repository.pending()?.takeIf { it.id == sessionId }?.status
        if (status == ImportSessionStatus.PREFERENCES_APPLIED) check(repository.complete(sessionId, digest, summary))
        withContext(Dispatchers.IO) { file.delete() }
        clearPendingPlan()
        presentation.state = TransferUiState.Completed(
            TransferCompletion(plan.books.size, plan.shelves.size, plan.warnings.size),
        )
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
        pendingBasePlan = null
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
            warningCodes = warnings.filter { it.severity == ImportSeverity.WARNING }
                .sortedBy { it.ordinal }
                .map(ImportWarning::safeLabel),
            conflictCodes = warnings.filter { it.severity == ImportSeverity.CONFLICT }
                .sortedBy { it.ordinal }
                .map(ImportWarning::safeLabel),
        )
    }
}
