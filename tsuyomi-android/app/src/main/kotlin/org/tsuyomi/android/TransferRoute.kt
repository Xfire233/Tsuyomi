/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
    onResetInterfacePreferences: suspend () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparedExportGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var preparedExportDigest by rememberSaveable { mutableStateOf<String?>(null) }
    var exportPickerGeneration by remember { mutableStateOf<Long?>(null) }
    var exportPickerDigest by remember { mutableStateOf<String?>(null) }
    var exportPickerFileName by remember { mutableStateOf<String?>(null) }
    var exportPreparationInFlight by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { document -> scope.launch { coordinator.readForReview(document, context.contentResolver) } }
    }
    val pickerGeneration = exportPickerGeneration
    val pickerDigest = exportPickerDigest
    val pickerFileName = exportPickerFileName
    if (pickerGeneration != null && pickerDigest != null && pickerFileName != null) {
        ExportDocumentPicker(
            coordinator = coordinator,
            ownerGeneration = pickerGeneration,
            canonicalDigest = pickerDigest,
            suggestedFileName = pickerFileName,
            onFinished = {
                if (exportPickerGeneration == pickerGeneration && exportPickerDigest == pickerDigest) {
                    exportPickerGeneration = null
                    exportPickerDigest = null
                    exportPickerFileName = null
                }
                if (preparedExportGeneration == pickerGeneration && preparedExportDigest == pickerDigest) {
                    preparedExportGeneration = null
                    preparedExportDigest = null
                }
            },
        )
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
            if (!exportPreparationInFlight && exportPickerGeneration == null && exportPickerDigest == null) {
                exportPreparationInFlight = true
                scope.launch {
                    try {
                        val savedGeneration = preparedExportGeneration
                        val savedDigest = preparedExportDigest
                        val restored = if (savedGeneration != null && savedDigest != null) {
                            coordinator.restorePreparedExport(savedGeneration, savedDigest)
                        } else {
                            null
                        }
                        val prepared = restored ?: run {
                            preparedExportGeneration = null
                            preparedExportDigest = null
                            coordinator.prepareExport(readerPreferences)
                        }
                        prepared?.let {
                            preparedExportGeneration = it.ownerGeneration
                            preparedExportDigest = it.canonicalDigest
                            exportPickerGeneration = it.ownerGeneration
                            exportPickerDigest = it.canonicalDigest
                            exportPickerFileName = it.suggestedFileName
                        }
                    } finally {
                        exportPreparationInFlight = false
                    }
                }
            }
        },
        onDismissResult = coordinator::dismissResult,
        onRetryRecovery = { scope.launch { coordinator.retryRecovery() } },
        onAbortRecovery = { scope.launch { coordinator.abortRecovery() } },
        onResetInterfacePreferences = { scope.launch { onResetInterfacePreferences() } },
    )
}

@Composable
private fun ExportDocumentPicker(
    coordinator: TransferCoordinator,
    ownerGeneration: Long,
    canonicalDigest: String,
    suggestedFileName: String,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    key(ownerGeneration, canonicalDigest) {
        val exportPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            scope.launch {
                if (uri == null) {
                    coordinator.cancelPreparedExport(ownerGeneration, canonicalDigest)
                } else {
                    coordinator.writePreparedExport(uri, context.contentResolver, ownerGeneration, canonicalDigest)
                }
                onFinished()
            }
        }
        LaunchedEffect(ownerGeneration, canonicalDigest) {
            exportPicker.launch(suggestedFileName)
        }
    }
}
