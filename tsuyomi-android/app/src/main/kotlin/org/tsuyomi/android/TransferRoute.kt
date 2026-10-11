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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    onResetInterfacePreferences: suspend () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparedExportGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var preparedExportDigest by rememberSaveable { mutableStateOf<String?>(null) }
    var preparedIncludesOriginals by rememberSaveable { mutableStateOf(false) }
    var exportPickerGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var exportPickerDigest by rememberSaveable { mutableStateOf<String?>(null) }
    var exportPickerFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var exportPickerIncludesOriginals by rememberSaveable { mutableStateOf(false) }
    var exportPreparationInFlight by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { document -> scope.launch { coordinator.readForReview(document, context.contentResolver) } }
    }
    val pickerGeneration = exportPickerGeneration
    val pickerDigest = exportPickerDigest
    val pickerFileName = exportPickerFileName
    val pickerIncludesOriginals = exportPickerIncludesOriginals
    if (pickerGeneration != null && pickerDigest != null && pickerFileName != null) {
        ExportDocumentPicker(
            coordinator = coordinator,
            ownerGeneration = pickerGeneration,
            canonicalDigest = pickerDigest,
            includesOriginals = pickerIncludesOriginals,
            suggestedFileName = pickerFileName,
            onFinished = {
                if (exportPickerGeneration == pickerGeneration && exportPickerDigest == pickerDigest &&
                    exportPickerIncludesOriginals == pickerIncludesOriginals
                ) {
                    exportPickerGeneration = null
                    exportPickerDigest = null
                    exportPickerFileName = null
                }
                if (preparedExportGeneration == pickerGeneration && preparedExportDigest == pickerDigest &&
                    preparedIncludesOriginals == pickerIncludesOriginals
                ) {
                    preparedExportGeneration = null
                    preparedExportDigest = null
                }
            },
        )
    }

    TransferScreen(
        state = coordinator.state,
        onChooseImport = { importPicker.launch(arrayOf("application/json", "application/zip", "application/octet-stream")) },
        onConfirmImport = {
            scope.launch { coordinator.confirmImport(); onImportConfirmed() }
        },
        onCancelImport = coordinator::cancelReview,
        onExport = { includesOriginals ->
            if (!exportPreparationInFlight && exportPickerGeneration == null && exportPickerDigest == null) {
                exportPreparationInFlight = true
                scope.launch {
                    try {
                        val savedGeneration = preparedExportGeneration
                        val savedDigest = preparedExportDigest
                        val restored = if (savedGeneration != null && savedDigest != null) {
                            if (preparedIncludesOriginals == includesOriginals) {
                                coordinator.restorePreparedExport(savedGeneration, savedDigest, includesOriginals)
                            } else {
                                coordinator.cancelPreparedExport(savedGeneration, savedDigest, preparedIncludesOriginals)
                                null
                            }
                        } else null
                        val prepared = restored ?: run {
                            preparedExportGeneration = null
                            preparedExportDigest = null
                            coordinator.prepareExport(readerPreferences, includesOriginals)
                        }
                        prepared?.let {
                            preparedExportGeneration = it.ownerGeneration
                            preparedExportDigest = it.canonicalDigest
                            preparedIncludesOriginals = it.includesOriginals
                            exportPickerGeneration = it.ownerGeneration
                            exportPickerDigest = it.canonicalDigest
                            exportPickerIncludesOriginals = it.includesOriginals
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
    includesOriginals: Boolean,
    suggestedFileName: String,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    key(ownerGeneration, canonicalDigest, includesOriginals) {
        var launched by rememberSaveable { mutableStateOf(false) }
        val exportPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(if (includesOriginals) "application/zip" else "application/json"),
        ) { uri ->
            scope.launch {
                try {
                    if (uri == null) coordinator.cancelPreparedExport(ownerGeneration, canonicalDigest, includesOriginals)
                    else coordinator.writePreparedExport(uri, context.contentResolver, ownerGeneration, canonicalDigest, includesOriginals)
                } finally { onFinished() }
            }
        }
        LaunchedEffect(ownerGeneration, canonicalDigest, includesOriginals) {
            if (!launched) {
                launched = true
                exportPicker.launch(suggestedFileName)
            }
        }
    }
}
