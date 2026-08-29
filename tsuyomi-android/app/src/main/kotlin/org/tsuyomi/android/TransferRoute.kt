/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.tsuyomi.feature.backup.TransferScreen
import org.tsuyomi.shared.backup.PortableReaderPreferences

@Composable
internal fun TransferRoute(
    coordinator: TransferCoordinator,
    readerPreferences: PortableReaderPreferences,
    onImportConfirmed: suspend () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparedExportGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var preparedExportDigest by rememberSaveable { mutableStateOf<String?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { document -> scope.launch { coordinator.readForReview(document, context.contentResolver) } }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val ownerGeneration = preparedExportGeneration
        val canonicalDigest = preparedExportDigest
        if (ownerGeneration != null && canonicalDigest != null) {
            scope.launch {
                if (uri == null) {
                    coordinator.cancelPreparedExport(ownerGeneration, canonicalDigest)
                } else {
                    coordinator.writePreparedExport(uri, context.contentResolver, ownerGeneration, canonicalDigest)
                }
                if (preparedExportGeneration == ownerGeneration && preparedExportDigest == canonicalDigest) {
                    preparedExportGeneration = null
                    preparedExportDigest = null
                }
            }
        }
    }

    TransferScreen(
        state = coordinator.state,
        onChooseImport = { importPicker.launch(arrayOf("application/json", "application/octet-stream")) },
        onConfirmImport = {
            scope.launch {
                coordinator.confirmImport()
                onImportConfirmed()
            }
        },
        onCancelImport = coordinator::cancelReview,
        onExport = {
            if (preparedExportGeneration == null && preparedExportDigest == null) {
                scope.launch {
                    coordinator.prepareExport(readerPreferences)?.let { prepared ->
                        preparedExportGeneration = prepared.ownerGeneration
                        preparedExportDigest = prepared.canonicalDigest
                        exportPicker.launch(prepared.suggestedFileName)
                    }
                }
            }
        },
        onDismissResult = coordinator::dismissResult,
        onRetryRecovery = { scope.launch { coordinator.retryRecovery() } },
        onAbortRecovery = { scope.launch { coordinator.abortRecovery() } },
    )
}
