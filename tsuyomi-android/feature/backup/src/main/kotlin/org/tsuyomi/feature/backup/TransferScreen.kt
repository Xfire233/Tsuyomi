/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind

data class TransferReview(
    val formatLabel: String,
    val bookCount: Int,
    val shelfCount: Int,
    val smartCollectionCount: Int,
    val disabledDraftCount: Int,
    val warningCodes: List<String>,
)

data class TransferCompletion(
    val importedBooks: Int,
    val importedShelves: Int,
    val warningCount: Int,
)

sealed interface TransferUiState {
    data object Idle : TransferUiState
    data class Working(val message: String) : TransferUiState
    data class Review(val value: TransferReview) : TransferUiState
    data class Completed(val value: TransferCompletion) : TransferUiState
    data object Exported : TransferUiState
    data class Failure(val safeMessage: String) : TransferUiState
    data class RecoveryFailure(
        val safeMessage: String,
        val canAbort: Boolean,
        val canRetryCleanup: Boolean,
    ) : TransferUiState
    data class Recovery(val message: String) : TransferUiState
}

@Composable
fun TransferScreen(
    state: TransferUiState,
    onChooseImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onExport: () -> Unit,
    onDismissResult: () -> Unit,
    onRetryRecovery: () -> Unit,
    onAbortRecovery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is TransferUiState.Working -> StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.transfer_working_title),
            message = state.message,
            modifier = modifier,
        )
        is TransferUiState.Recovery -> StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.transfer_recovery_title),
            message = state.message,
            modifier = modifier,
        )
        else -> Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.transfer_local_notice))
            when (state) {
                TransferUiState.Idle -> {
                    TsuyomiButton(
                        text = stringResource(R.string.transfer_import_action),
                        onClick = onChooseImport,
                        modifier = Modifier.fillMaxWidth(),
                        style = TsuyomiButtonStyle.PRIMARY,
                    )
                    TsuyomiButton(
                        text = stringResource(R.string.transfer_export_action),
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth(),
                        style = TsuyomiButtonStyle.SECONDARY,
                    )
                }
                is TransferUiState.Review -> {
                    val review = state.value
                    Text(stringResource(R.string.transfer_review_title))
                    Text(stringResource(R.string.transfer_review_format, review.formatLabel))
                    Text(stringResource(R.string.transfer_review_counts, review.bookCount, review.shelfCount))
                    if (review.smartCollectionCount > 0 || review.disabledDraftCount > 0) {
                        Text(stringResource(R.string.transfer_review_local_counts, review.smartCollectionCount, review.disabledDraftCount))
                    }
                    if (review.warningCodes.isNotEmpty()) {
                        HorizontalDivider()
                        Text(stringResource(R.string.transfer_warning_title, review.warningCodes.size))
                        review.warningCodes.take(50).forEach { Text("• $it") }
                        if (review.warningCodes.size > 50) Text(stringResource(R.string.transfer_warning_more, review.warningCodes.size - 50))
                    }
                    Text(stringResource(R.string.transfer_sensitive_notice))
                    TsuyomiButton(
                        text = stringResource(R.string.transfer_confirm_action),
                        onClick = onConfirmImport,
                        modifier = Modifier.fillMaxWidth(),
                        style = TsuyomiButtonStyle.PRIMARY,
                    )
                    TsuyomiButton(
                        text = stringResource(R.string.transfer_cancel_action),
                        onClick = onCancelImport,
                        modifier = Modifier.fillMaxWidth(),
                        style = TsuyomiButtonStyle.SECONDARY,
                    )
                }
                is TransferUiState.Completed -> {
                    Text(stringResource(R.string.transfer_complete_title))
                    Text(stringResource(R.string.transfer_complete_counts, state.value.importedBooks, state.value.importedShelves, state.value.warningCount))
                    TsuyomiButton(
                        text = stringResource(R.string.transfer_done_action),
                        onClick = onDismissResult,
                        modifier = Modifier.fillMaxWidth(),
                        style = TsuyomiButtonStyle.PRIMARY,
                    )
                }
                TransferUiState.Exported -> {
                    Text(stringResource(R.string.transfer_export_complete_title))
                    TsuyomiButton(
                        text = stringResource(R.string.transfer_done_action),
                        onClick = onDismissResult,
                        modifier = Modifier.fillMaxWidth(),
                        style = TsuyomiButtonStyle.PRIMARY,
                    )
                }
                is TransferUiState.Failure -> {
                    Text(stringResource(R.string.transfer_failure_title))
                    Text(state.safeMessage)
                    TsuyomiButton(
                        text = stringResource(R.string.transfer_done_action),
                        onClick = onDismissResult,
                        modifier = Modifier.fillMaxWidth(),
                        style = TsuyomiButtonStyle.PRIMARY,
                    )
                }
                is TransferUiState.RecoveryFailure -> {
                    Text(stringResource(R.string.transfer_failure_title))
                    Text(state.safeMessage)
                    TsuyomiButton(
                        text = stringResource(if (state.canRetryCleanup) R.string.transfer_cleanup_retry_action else R.string.transfer_recovery_retry_action),
                        onClick = onRetryRecovery,
                        modifier = Modifier.fillMaxWidth(),
                        style = TsuyomiButtonStyle.PRIMARY,
                    )
                    if (state.canAbort) {
                        TsuyomiButton(
                            text = stringResource(R.string.transfer_recovery_abort_action),
                            onClick = onAbortRecovery,
                            modifier = Modifier.fillMaxWidth(),
                            style = TsuyomiButtonStyle.SECONDARY,
                        )
                    }
                }
                is TransferUiState.Working, is TransferUiState.Recovery -> Unit
            }
        }
    }
}
