/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.librarydomain.UnresolvedUpdate
import org.tsuyomi.shared.librarydomain.UpdateSessionStates

@Composable
internal fun LibraryUpdateHeader(
    state: LibraryUiState,
    onCancelScan: () -> Unit,
    onRetryScan: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isRootProjection) return
    LibraryUpdateStatusStrip(
        state = state,
        onCancelScan = onCancelScan,
        onRetryScan = onRetryScan,
        onOpenSettings = onOpenSettings,
        modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs),
    )
}

@Composable
private fun LibraryUpdateStatusStrip(
    state: LibraryUiState,
    onCancelScan: () -> Unit,
    onRetryScan: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.updateSession ?: return
    var dismissedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    if (dismissedSessionId == session.id) return
    val active = session.state in setOf(UpdateSessionStates.QUEUED, UpdateSessionStates.RUNNING)
    val successful = session.state == UpdateSessionStates.COMPLETED && session.failed == 0
    val successVisibleMillis = remember(session.id, session.finishedAt, successful) {
        updateSuccessVisibilityMillis(session, System.currentTimeMillis())
    }
    if (successful && successVisibleMillis == 0L) return
    LaunchedEffect(session.id, successVisibleMillis) {
        if (successVisibleMillis != null) {
            delay(successVisibleMillis)
            dismissedSessionId = session.id
        }
    }
    val label = when {
        active -> stringResource(R.string.updates_status_running, session.completed, session.total)
        session.state == UpdateSessionStates.PARTIAL -> stringResource(R.string.updates_status_partial)
        session.state == UpdateSessionStates.FAILED -> stringResource(R.string.updates_status_failed)
        session.state == UpdateSessionStates.CANCELLED -> stringResource(R.string.updates_status_cancelled)
        else -> stringResource(R.string.updates_status_completed)
    }
    Surface(
        modifier = modifier.fillMaxWidth().testTag("library-update-status"),
        shape = MaterialTheme.shapes.medium,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = TsuyomiSpacing.Md, end = TsuyomiSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (active) {
                TextButton(onClick = onCancelScan) { Text(stringResource(R.string.updates_cancel)) }
            } else {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.updates_show_reports, session.completed, session.total))
                }
                if (!successful) {
                    TextButton(onClick = onRetryScan) { Text(stringResource(R.string.updates_retry)) }
                    TextButton(onClick = { dismissedSessionId = session.id }) {
                        Text(stringResource(R.string.updates_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryUpdateActionButton(
    update: UnresolvedUpdate,
    onIgnore: (UnresolvedUpdate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(update.identity.sourceId, update.identity.remoteBookId, update.anchor) {
        mutableStateOf(false)
    }
    IconButton(
        onClick = { expanded = true },
        modifier = modifier.testTag("library-update-actions-${update.identity.sourceId}-${update.identity.remoteBookId}"),
    ) {
        Icon(TsuyomiIcons.More, contentDescription = "${update.title} 的更新操作")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.updates_ignore_current)) },
            onClick = {
                expanded = false
                onIgnore(update)
            },
            modifier = Modifier.testTag(
                "library-update-ignore-${update.identity.sourceId}-${update.identity.remoteBookId}",
            ),
        )
    }
}

internal fun updateSuccessVisibilityMillis(
    session: org.tsuyomi.shared.librarydomain.UpdateSessionSummary,
    now: Long,
): Long? {
    if (session.state != UpdateSessionStates.COMPLETED || session.failed != 0) return null
    val finishedAt = session.finishedAt ?: return 0L
    return (finishedAt + SUCCESS_REPORT_DURATION_MILLIS - now).coerceIn(0L, SUCCESS_REPORT_DURATION_MILLIS)
}

private const val SUCCESS_REPORT_DURATION_MILLIS = 2_000L
