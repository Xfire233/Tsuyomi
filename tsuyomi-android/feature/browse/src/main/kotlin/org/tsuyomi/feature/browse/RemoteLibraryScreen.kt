/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiTopBarAction
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.shared.sourcecontract.SourceBookSummary

enum class RemoteLibraryViewState {
    IDLE,
    LOADING,
    CONTENT,
    EMPTY,
    ERROR,
    LOGIN_REQUIRED,
    VERIFICATION_REQUIRED,
    CANCELLED,
    COPIED,
}

@Composable
fun RemoteLibraryScreen(
    sourceName: String,
    books: List<SourceBookSummary>,
    selectedIds: Set<String>,
    state: RemoteLibraryViewState,
    message: String?,
    copyConfirmationVisible: Boolean,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSelection: (SourceBookSummary) -> Unit,
    onClearSelection: () -> Unit,
    onRequestCopy: () -> Unit,
    onDismissCopy: () -> Unit,
    onConfirmCopy: () -> Unit,
    onOpenVerification: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        FrozenEInkRemoteLibraryScreen(
            books = books,
            state = state,
            message = message,
            onRefresh = onRefresh,
            onOpenBook = onOpenBook,
            modifier = modifier,
        )
        return
    }

    val selectedCount = selectedIds.size
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TsuyomiTopBar(
                title = if (selectedCount == 0) sourceName else stringResource(R.string.remote_library_selected_title, selectedCount),
                subtitle = stringResource(R.string.remote_library_subtitle, books.size),
                onNavigateUp = if (selectedCount == 0) onNavigateUp else onClearSelection,
                actions = listOf(
                    TsuyomiTopBarAction(
                        icon = TsuyomiIcons.Refresh,
                        label = stringResource(R.string.remote_library_refresh),
                        onClick = onRefresh,
                    ),
                    TsuyomiTopBarAction(
                        icon = TsuyomiIcons.Add,
                        label = stringResource(if (selectedCount == 0) R.string.remote_library_copy_all else R.string.remote_library_copy_selected),
                        onClick = onRequestCopy,
                    ),
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("remote-library-surface"),
        ) {
            message?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (state) {
                RemoteLibraryViewState.LOADING -> StateView(
                    kind = TsuyomiStateKind.LOADING,
                    title = stringResource(R.string.remote_library_loading),
                    message = stringResource(R.string.remote_library_loading_message),
                    modifier = Modifier.fillMaxSize(),
                )
                RemoteLibraryViewState.IDLE -> StateView(
                    kind = TsuyomiStateKind.EMPTY,
                    title = stringResource(R.string.remote_library_idle_title),
                    message = stringResource(R.string.remote_library_idle_message),
                    actionLabel = stringResource(R.string.remote_library_refresh),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                )
                RemoteLibraryViewState.EMPTY -> StateView(
                    kind = TsuyomiStateKind.EMPTY,
                    title = stringResource(R.string.remote_library_empty_title),
                    message = stringResource(R.string.remote_library_read_only_empty_message),
                    actionLabel = stringResource(R.string.remote_library_refresh),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                )
                RemoteLibraryViewState.LOGIN_REQUIRED,
                RemoteLibraryViewState.VERIFICATION_REQUIRED,
                -> StateView(
                    kind = TsuyomiStateKind.EMPTY,
                    title = stringResource(R.string.remote_library_login_title),
                    message = stringResource(R.string.remote_library_login_message),
                    actionLabel = stringResource(R.string.remote_library_open_verification),
                    onAction = onOpenVerification,
                    modifier = Modifier.fillMaxSize(),
                )
                RemoteLibraryViewState.ERROR,
                RemoteLibraryViewState.CANCELLED,
                -> StateView(
                    kind = TsuyomiStateKind.ERROR,
                    title = stringResource(R.string.remote_library_error_title),
                    message = stringResource(R.string.remote_library_error_message),
                    actionLabel = stringResource(R.string.remote_library_retry),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                )
                RemoteLibraryViewState.CONTENT,
                RemoteLibraryViewState.COPIED,
                -> RemoteLibraryContent(
                    books = books,
                    selectedIds = selectedIds,
                    onToggleSelection = onToggleSelection,
                    onOpenBook = onOpenBook,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (copyConfirmationVisible) {
        AlertDialog(
            onDismissRequest = onDismissCopy,
            title = { Text(stringResource(R.string.remote_library_copy_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.remote_library_copy_dialog_message,
                        if (selectedCount == 0) books.size else selectedCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmCopy) {
                    Text(stringResource(R.string.remote_library_copy_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCopy) { Text(stringResource(R.string.remote_library_copy_cancel)) }
            },
        )
    }
}

@Composable
private fun RemoteLibraryContent(
    books: List<SourceBookSummary>,
    selectedIds: Set<String>,
    onToggleSelection: (SourceBookSummary) -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("remote-library-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(books, key = SourceBookSummary::canonicalUrl) { book ->
            val selected = book.canonicalUrl in selectedIds
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBook(book) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelection(book) },
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium)
                        book.author?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrozenEInkRemoteLibraryScreen(
    books: List<SourceBookSummary>,
    state: RemoteLibraryViewState,
    message: String?,
    onRefresh: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.remote_library_manual_notice))
        TsuyomiButton(
            text = stringResource(R.string.remote_library_refresh),
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            style = TsuyomiButtonStyle.PRIMARY,
        )
        message?.let { Text(it) }
        if (state == RemoteLibraryViewState.LOADING) {
            Text(stringResource(R.string.remote_library_loading))
        } else {
            books.forEach { book ->
                Text(book.title, Modifier.fillMaxWidth().clickable { onOpenBook(book) }.padding(vertical = 12.dp))
            }
        }
    }
}
