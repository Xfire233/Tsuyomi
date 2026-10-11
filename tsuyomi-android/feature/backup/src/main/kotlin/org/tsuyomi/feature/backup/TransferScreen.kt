/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.SettingsActionRow
import org.tsuyomi.core.ui.components.SettingsGroup
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiDialog
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

private data class TransferPagerItem(
    val key: String,
    val content: @Composable () -> Unit,
)

private const val TransferReviewPreviewLimit = 50

@Composable
fun TransferScreen(
    state: TransferUiState,
    onChooseImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onExport: (includesOriginals: Boolean) -> Unit,
    onDismissResult: () -> Unit,
    onRetryRecovery: () -> Unit,
    onAbortRecovery: () -> Unit,
    onResetInterfacePreferences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var resetVisible by rememberSaveable { mutableStateOf(false) }
    val review = (state as? TransferUiState.Review)?.value
    var expandedConflicts by rememberSaveable(review?.conflictCodes) { mutableStateOf(false) }
    var expandedWarnings by rememberSaveable(review?.warningCodes) { mutableStateOf(false) }
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val pageStateHolder = rememberSaveableStateHolder()

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
        else -> if (eInk) {
            val saveablePageKey = transferPageStateKey(state)
            pageStateHolder.SaveableStateProvider(saveablePageKey) {
                BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                    val contentWidth = (maxWidth.coerceAtMost(560.dp) - TsuyomiSpacing.Md * 2)
                        .coerceAtLeast(1.dp)
                    val items = transferPagerItems(
                        state = state,
                        contentWidth = contentWidth,
                        expandedConflicts = expandedConflicts,
                        onExpandedConflictsChanged = { expandedConflicts = it },
                        expandedWarnings = expandedWarnings,
                        onExpandedWarningsChanged = { expandedWarnings = it },
                        onChooseImport = onChooseImport,
                        onConfirmImport = onConfirmImport,
                        onCancelImport = onCancelImport,
                        onExport = onExport,
                        onDismissResult = onDismissResult,
                        onRetryRecovery = onRetryRecovery,
                        onAbortRecovery = onAbortRecovery,
                        onOpenReset = { resetVisible = true },
                    )
                    val pagerState = rememberTsuyomiMeasuredPagerState(
                        initialAnchorKey = items.firstOrNull()?.key,
                    )
                    TsuyomiMeasuredPager(
                        itemCount = items.size,
                        itemKey = { index -> items[index].key },
                        itemContent = { index -> items[index].content() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .fillMaxSize()
                            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
                        state = pagerState,
                        verticalSpacing = TsuyomiSpacing.Sm,
                    )
                }
            }
        } else {
            StandardTransferContent(
                state = state,
                expandedConflicts = expandedConflicts,
                onExpandedConflictsChanged = { expandedConflicts = it },
                expandedWarnings = expandedWarnings,
                onExpandedWarningsChanged = { expandedWarnings = it },
                onChooseImport = onChooseImport,
                onConfirmImport = onConfirmImport,
                onCancelImport = onCancelImport,
                onExport = onExport,
                onDismissResult = onDismissResult,
                onRetryRecovery = onRetryRecovery,
                onAbortRecovery = onAbortRecovery,
                onOpenReset = { resetVisible = true },
                modifier = modifier,
            )
        }
    }

    if (resetVisible) {
        TsuyomiDialog(
            onDismissRequest = { resetVisible = false },
            title = stringResource(R.string.transfer_reset_preferences_dialog_title),
            text = stringResource(R.string.transfer_reset_preferences_dialog_message),
            confirmLabel = stringResource(R.string.transfer_reset_preferences_confirm),
            dismissLabel = stringResource(R.string.transfer_reset_preferences_cancel),
            destructive = true,
            onConfirm = {
                resetVisible = false
                onResetInterfacePreferences()
            },
        )
    }
}

@Composable
private fun StandardTransferContent(
    state: TransferUiState,
    expandedConflicts: Boolean,
    onExpandedConflictsChanged: (Boolean) -> Unit,
    expandedWarnings: Boolean,
    onExpandedWarningsChanged: (Boolean) -> Unit,
    onChooseImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onExport: (includesOriginals: Boolean) -> Unit,
    onDismissResult: () -> Unit,
    onRetryRecovery: () -> Unit,
    onAbortRecovery: () -> Unit,
    onOpenReset: () -> Unit,
    modifier: Modifier,
) {
    Column(
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
                    onClick = { onExport(false) },
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.SECONDARY,
                )
                TsuyomiButton(
                    text = stringResource(R.string.transfer_export_archive_action),
                    onClick = { onExport(true) },
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.SECONDARY,
                )
                Text(stringResource(R.string.transfer_export_scope))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SettingsGroup {
                    SettingsActionRow(
                        title = stringResource(R.string.transfer_reset_preferences_title),
                        summary = stringResource(R.string.transfer_reset_preferences_summary),
                        onClick = onOpenReset,
                    )
                }
            }
            is TransferUiState.Review -> {
                val value = state.value
                Text(stringResource(R.string.transfer_review_title))
                Text(stringResource(R.string.transfer_review_format, value.formatLabel))
                Text(stringResource(R.string.transfer_review_counts, value.bookCount, value.shelfCount))
                value.archiveOriginalCount?.let {
                    Text(stringResource(R.string.transfer_review_originals, it))
                }
                if (value.smartCollectionCount > 0 || value.disabledDraftCount > 0) {
                    Text(stringResource(R.string.transfer_review_local_counts, value.smartCollectionCount, value.disabledDraftCount))
                }
                if (value.conflictCodes.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.transfer_conflict_title, value.conflictCodes.size))
                    val visible = if (expandedConflicts) value.conflictCodes else value.conflictCodes.take(TransferReviewPreviewLimit)
                    visible.forEach { Text("• ${conflictLabel(it)}") }
                    if (value.conflictCodes.size > TransferReviewPreviewLimit) {
                        TransferExpansionRow(
                            expanded = expandedConflicts,
                            totalCount = value.conflictCodes.size,
                            onClick = { onExpandedConflictsChanged(!expandedConflicts) },
                        )
                    }
                }
                if (value.warningCodes.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.transfer_warning_title, value.warningCodes.size))
                    val visible = if (expandedWarnings) value.warningCodes else value.warningCodes.take(TransferReviewPreviewLimit)
                    visible.forEach { Text("• $it") }
                    if (value.warningCodes.size > TransferReviewPreviewLimit) {
                        TransferExpansionRow(
                            expanded = expandedWarnings,
                            totalCount = value.warningCodes.size,
                            onClick = { onExpandedWarningsChanged(!expandedWarnings) },
                        )
                    }
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
                if (state.value.originalConflicts > 0) {
                    Text(stringResource(R.string.transfer_original_conflicts, state.value.originalConflicts))
                }
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

@Composable
private fun transferPagerItems(
    state: TransferUiState,
    contentWidth: Dp,
    expandedConflicts: Boolean,
    onExpandedConflictsChanged: (Boolean) -> Unit,
    expandedWarnings: Boolean,
    onExpandedWarningsChanged: (Boolean) -> Unit,
    onChooseImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onExport: (includesOriginals: Boolean) -> Unit,
    onDismissResult: () -> Unit,
    onRetryRecovery: () -> Unit,
    onAbortRecovery: () -> Unit,
    onOpenReset: () -> Unit,
): List<TransferPagerItem> {
    val items = mutableListOf<TransferPagerItem>()
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurface

    when (state) {
        TransferUiState.Idle -> {
            appendTransferParagraph(items, "idle:notice", stringResource(R.string.transfer_local_notice), contentWidth, style, color)
            items += TransferPagerItem("idle:import") {
                TsuyomiButton(
                    text = stringResource(R.string.transfer_import_action),
                    onClick = onChooseImport,
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.PRIMARY,
                )
            }
            items += TransferPagerItem("idle:export-json") {
                TsuyomiButton(
                    text = stringResource(R.string.transfer_export_action),
                    onClick = { onExport(false) },
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.SECONDARY,
                )
            }
            items += TransferPagerItem("idle:export-archive") {
                TsuyomiButton(
                    text = stringResource(R.string.transfer_export_archive_action),
                    onClick = { onExport(true) },
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.SECONDARY,
                )
            }
            appendTransferParagraph(items, "idle:export-scope", stringResource(R.string.transfer_export_scope), contentWidth, style, color)
            items += TransferPagerItem("idle:reset-preferences") {
                SettingsActionRow(
                    title = stringResource(R.string.transfer_reset_preferences_title),
                    summary = stringResource(R.string.transfer_reset_preferences_summary),
                    onClick = onOpenReset,
                )
            }
        }
        is TransferUiState.Review -> {
            val value = state.value
            appendTransferParagraph(items, "review:title", stringResource(R.string.transfer_review_title), contentWidth, style, color)
            appendTransferParagraph(items, "review:format", stringResource(R.string.transfer_review_format, value.formatLabel), contentWidth, style, color)
            appendTransferParagraph(items, "review:counts", stringResource(R.string.transfer_review_counts, value.bookCount, value.shelfCount), contentWidth, style, color)
            value.archiveOriginalCount?.let {
                appendTransferParagraph(items, "review:archive-originals", stringResource(R.string.transfer_review_originals, it), contentWidth, style, color)
            }
            if (value.smartCollectionCount > 0 || value.disabledDraftCount > 0) {
                appendTransferParagraph(
                    items,
                    "review:local-counts",
                    stringResource(R.string.transfer_review_local_counts, value.smartCollectionCount, value.disabledDraftCount),
                    contentWidth,
                    style,
                    color,
                )
            }
            if (value.conflictCodes.isNotEmpty()) {
                appendTransferParagraph(items, "review:conflicts-title", stringResource(R.string.transfer_conflict_title, value.conflictCodes.size), contentWidth, style, color)
                items += TransferPagerItem("review:conflicts") {
                    TsuyomiMeasuredPager(itemCount = value.conflictCodes.size, itemKey = { "conflict:$it:${value.conflictCodes[it]}" }, fillViewport = false,
                        itemContent = { index -> TsuyomiMeasuredTextPager("• ${conflictLabel(value.conflictCodes[index])}", fillViewport = false, alwaysShowPageControls = false) })
                }
            }
            if (value.warningCodes.isNotEmpty()) {
                appendTransferParagraph(items, "review:warnings-title", stringResource(R.string.transfer_warning_title, value.warningCodes.size), contentWidth, style, color)
                items += TransferPagerItem("review:warnings") {
                    TsuyomiMeasuredPager(itemCount = value.warningCodes.size, itemKey = { "warning:$it:${value.warningCodes[it]}" }, fillViewport = false,
                        itemContent = { index -> TsuyomiMeasuredTextPager("• ${value.warningCodes[index]}", fillViewport = false, alwaysShowPageControls = false) })
                }
            }
            appendTransferParagraph(items, "review:sensitive-notice", stringResource(R.string.transfer_sensitive_notice), contentWidth, style, color)
            items += TransferPagerItem("review:confirm") {
                TsuyomiButton(
                    text = stringResource(R.string.transfer_confirm_action),
                    onClick = onConfirmImport,
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.PRIMARY,
                )
            }
            items += TransferPagerItem("review:cancel") {
                TsuyomiButton(
                    text = stringResource(R.string.transfer_cancel_action),
                    onClick = onCancelImport,
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.SECONDARY,
                )
            }
        }
        is TransferUiState.Completed -> {
            appendTransferParagraph(items, "complete:title", stringResource(R.string.transfer_complete_title), contentWidth, style, color)
            appendTransferParagraph(
                items,
                "complete:counts",
                stringResource(R.string.transfer_complete_counts, state.value.importedBooks, state.value.importedShelves, state.value.warningCount),
                contentWidth,
                style,
                color,
            )
            if (state.value.originalConflicts > 0) {
                appendTransferParagraph(items, "complete:original-conflicts", stringResource(R.string.transfer_original_conflicts, state.value.originalConflicts), contentWidth, style, color)
            }
            items += TransferPagerItem("complete:dismiss") {
                TsuyomiButton(
                    text = stringResource(R.string.transfer_done_action),
                    onClick = onDismissResult,
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.PRIMARY,
                )
            }
        }
        TransferUiState.Exported -> {
            appendTransferParagraph(items, "exported:title", stringResource(R.string.transfer_export_complete_title), contentWidth, style, color)
            items += TransferPagerItem("exported:dismiss") {
                TsuyomiButton(
                    text = stringResource(R.string.transfer_done_action),
                    onClick = onDismissResult,
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.PRIMARY,
                )
            }
        }
        is TransferUiState.Failure -> {
            appendTransferParagraph(items, "failure:title", stringResource(R.string.transfer_failure_title), contentWidth, style, color)
            appendTransferParagraph(items, "failure:message", state.safeMessage, contentWidth, style, color)
            items += TransferPagerItem("failure:dismiss") {
                TsuyomiButton(
                    text = stringResource(R.string.transfer_done_action),
                    onClick = onDismissResult,
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.PRIMARY,
                )
            }
        }
        is TransferUiState.RecoveryFailure -> {
            appendTransferParagraph(items, "recovery-failure:title", stringResource(R.string.transfer_failure_title), contentWidth, style, color)
            appendTransferParagraph(items, "recovery-failure:message", state.safeMessage, contentWidth, style, color)
            items += TransferPagerItem("recovery-failure:retry") {
                TsuyomiButton(
                    text = stringResource(if (state.canRetryCleanup) R.string.transfer_cleanup_retry_action else R.string.transfer_recovery_retry_action),
                    onClick = onRetryRecovery,
                    modifier = Modifier.fillMaxWidth(),
                    style = TsuyomiButtonStyle.PRIMARY,
                )
            }
            if (state.canAbort) {
                items += TransferPagerItem("recovery-failure:abort") {
                    TsuyomiButton(
                        text = stringResource(R.string.transfer_recovery_abort_action),
                        onClick = onAbortRecovery,
                        modifier = Modifier.fillMaxWidth(),
                        style = TsuyomiButtonStyle.SECONDARY,
                    )
                }
            }
        }
        is TransferUiState.Working, is TransferUiState.Recovery -> Unit
    }
    return items
}

private fun appendTransferParagraph(
    target: MutableList<TransferPagerItem>,
    key: String,
    text: String,
    maxWidth: Dp,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
) {
    target += TransferPagerItem(key) {
        TsuyomiMeasuredTextPager(text, style = style, color = color, fillViewport = false, alwaysShowPageControls = false)
    }
}

@Composable
private fun TransferExpansionRow(
    expanded: Boolean,
    totalCount: Int,
    onClick: () -> Unit,
) {
    val title = if (expanded) {
        stringResource(R.string.transfer_collapse)
    } else {
        stringResource(R.string.transfer_expand_all, totalCount)
    }
    SettingsActionRow(
        title = title,
        modifier = Modifier.semantics {
            if (expanded) collapse { onClick(); true } else expand { onClick(); true }
        },
        onClick = onClick,
    )
}

private fun transferPageStateKey(state: TransferUiState): String = when (state) {
    TransferUiState.Idle -> "transfer:idle"
    is TransferUiState.Review -> "transfer:review"
    is TransferUiState.Completed -> "transfer:completed"
    TransferUiState.Exported -> "transfer:exported"
    is TransferUiState.Failure -> "transfer:failure"
    is TransferUiState.RecoveryFailure -> "transfer:recovery-failure"
    is TransferUiState.Working -> "transfer:working"
    is TransferUiState.Recovery -> "transfer:recovery"
}

data class TransferReview(
    val formatLabel: String,
    val bookCount: Int,
    val shelfCount: Int,
    val smartCollectionCount: Int,
    val disabledDraftCount: Int,
    val warningCodes: List<String>,
    val conflictCodes: List<String>,
    val archiveOriginalCount: Int? = null,
)

data class TransferCompletion(
    val importedBooks: Int,
    val importedShelves: Int,
    val warningCount: Int,
    val originalConflicts: Int = 0,
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
private fun conflictLabel(label: String): String {
    val parts = label.split(" · ")
    val message = stringResource(
        when (parts.firstOrNull()) {
            "existing-shelf-retained" -> R.string.transfer_conflict_shelf
            "existing-book-metadata-retained" -> R.string.transfer_conflict_metadata
            "existing-local-original-retained" -> R.string.transfer_conflict_original
            "existing-rating-retained" -> R.string.transfer_conflict_rating
            "local-tags-capacity-conflict" -> R.string.transfer_conflict_tags
            "existing-progress-retained" -> R.string.transfer_conflict_progress
            else -> R.string.transfer_conflict_unknown
        },
    )
    val detail = parts.drop(1).joinToString(" · ")
    return if (detail.isEmpty()) message else "$message（$detail）"
}