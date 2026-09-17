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
    preferences: PortableReaderPreferencesRepository,
    applyPreferenceImport: suspend (PortableReaderPreferences?, Boolean, String) -> Unit = preferences::applyImport,
) {
    private val appContext = context.applicationContext
    private val presentation = TransferPresentationState()
    private val exportPreflightStore = TransferExportPreflightStore(
        File(appContext.cacheDir, "transfer-export-preflight"),
    )
    private val importOwner = TransferImportSessionOwner(
        context = appContext,
        repository = repository,
        applyPreferenceImport = applyPreferenceImport,
        presentation = presentation,
    )

    val state: TransferUiState
        get() = presentation.state
    val recoveryReady: Boolean
        get() = presentation.recoveryReady

    suspend fun recoverPendingImport() {
        if (presentation.recoveryReady) return
        withContext(Dispatchers.IO) { runCatching(exportPreflightStore::sweepOrphans) }
        importOwner.recoverPendingImport()
    }

    suspend fun retryRecovery() = importOwner.retryRecovery()

    suspend fun abortRecovery() = importOwner.abortRecovery()

    suspend fun readForReview(uri: Uri, resolver: ContentResolver) = importOwner.readForReview(uri, resolver)

    suspend fun confirmImport() = importOwner.confirmImport()

    fun cancelReview() = importOwner.cancelReview()

    fun dismissResult() = importOwner.dismissResult()

    suspend fun prepareExport(readerPreferences: PortableReaderPreferences): PreparedExport? {
        presentation.state = TransferUiState.Working(appContext.getString(R.string.transfer_preparing_export))
        val bytes = withContext(Dispatchers.IO) {
            val snapshot = repository.exportSnapshot(Instant.now(), readerPreferences)
            TransferCodec.encodeBounded(snapshot)
        }
        if (bytes == null) {
            presentation.state = TransferUiState.Failure(appContext.getString(R.string.transfer_export_too_large))
            return null
        }
        val prepared = runCatching {
            withContext(Dispatchers.IO) { exportPreflightStore.prepare(bytes) }
        }.getOrElse {
            presentation.state = TransferUiState.Failure(appContext.getString(R.string.transfer_export_failed))
            return null
        }
        presentation.state = TransferUiState.Idle
        return prepared
    }

    suspend fun restorePreparedExport(ownerGeneration: Long, canonicalDigest: String): PreparedExport? {
        val ownership = ExportPreflightOwnership(ownerGeneration, canonicalDigest)
        return withContext(Dispatchers.IO) {
            exportPreflightStore.restore(ownership) ?: run {
                exportPreflightStore.clearIfOwned(ownership)
                null
            }
        }
    }

    suspend fun cancelPreparedExport(ownerGeneration: Long, canonicalDigest: String) {
        val owned = withContext(Dispatchers.IO) {
            exportPreflightStore.clearIfOwned(ExportPreflightOwnership(ownerGeneration, canonicalDigest))
        }
        if (owned) presentation.state = TransferUiState.Idle
    }

    suspend fun writePreparedExport(
        uri: Uri,
        resolver: ContentResolver,
        ownerGeneration: Long,
        canonicalDigest: String,
    ) {
        val ownership = ExportPreflightOwnership(ownerGeneration, canonicalDigest)
        val success = withContext(Dispatchers.IO) {
            exportPreflightStore.consumeVerifiedFile(ownership) { source ->
                runCatching {
                    val output = resolver.openOutputStream(uri, "w") ?: error("output-unavailable")
                    output.use { stream ->
                        source.inputStream().use { input -> input.copyTo(stream) }
                        stream.flush()
                    }
                }.isSuccess
            }
        }
        if (success != null) {
            presentation.state = if (success) {
                TransferUiState.Exported
            } else {
                TransferUiState.Failure(appContext.getString(R.string.transfer_export_failed))
            }
        }
    }
}
