/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.PaginationBar
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind

enum class SystemLibraryFilter { ALL, CONTINUE, RECENT, UNREAD, DORMANT }

data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val loading: Boolean = true,
    val failure: String? = null,
    val query: String = "",
    val filter: SystemLibraryFilter = SystemLibraryFilter.ALL,
)

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    collections: List<LibraryCollection>,
    selectedCollectionId: String?,
    onCollectionChange: (String?) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SystemLibraryFilter) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    onRetry: () -> Unit,
    onManageCollections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading -> StateView(TsuyomiStateKind.LOADING, stringResource(R.string.library_loading), modifier = modifier)
        state.failure != null -> StateView(
            TsuyomiStateKind.ERROR,
            stringResource(R.string.library_load_failed),
            message = state.failure,
            actionLabel = stringResource(R.string.library_retry),
            onAction = onRetry,
            modifier = modifier,
        )
        state.entries.isEmpty() && state.query.isBlank() && state.filter == SystemLibraryFilter.ALL && selectedCollectionId == null -> StateView(
            kind = TsuyomiStateKind.EMPTY,
            title = stringResource(R.string.library_empty_title),
            message = stringResource(R.string.library_empty_message),
            actionLabel = stringResource(R.string.library_action_manage_collections),
            onAction = onManageCollections,
            modifier = modifier,
        )
        else -> LibraryContent(state, collections, selectedCollectionId, onCollectionChange, onQueryChange, onFilterChange, onOpenBook, onManageCollections, modifier)
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    collections: List<LibraryCollection>,
    selectedCollectionId: String?,
    onCollectionChange: (String?) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SystemLibraryFilter) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    onManageCollections: () -> Unit,
    modifier: Modifier,
) {
    val environment = LocalDisplayEnvironment.current
    val filtered = state.entries.filter { entry ->
        val queryMatches = state.query.isBlank() || listOf(entry.book.title, entry.book.authors.joinToString(" "), entry.localTags.joinToString(" "))
            .any { it.contains(state.query.trim(), ignoreCase = true) }
        val filterMatches = when (state.filter) {
            SystemLibraryFilter.ALL -> true
            SystemLibraryFilter.CONTINUE -> entry.progress?.locator?.bookProgress?.let { it < 1.0 } ?: (entry.progress != null)
            SystemLibraryFilter.RECENT -> entry.progress != null
            SystemLibraryFilter.UNREAD -> entry.book.hasUnreadUpdate
            SystemLibraryFilter.DORMANT -> !entry.sourceAvailable
        }
        queryMatches && filterMatches
    }
    val pageSize = if (environment.effectiveProfile == DisplayProfile.EINK) 6 else filtered.size.coerceAtLeast(1)
    val pageCount = ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    var page by rememberSaveable(state.query, state.filter, filtered.size) { mutableIntStateOf(1) }
    val visible = filtered.drop((page - 1) * pageSize).take(pageSize)

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.library_archive_heading),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
        Text(
            text = stringResource(R.string.library_count, filtered.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TsuyomiButton(
            text = stringResource(R.string.library_action_manage_collections),
            onClick = onManageCollections,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            style = TsuyomiButtonStyle.SECONDARY,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedCollectionId == null,
                    onClick = { onCollectionChange(null) },
                    label = { Text(stringResource(R.string.library_collection_all)) },
                )
            }
            items(collections, key = { it.collectionId }) { collection ->
                FilterChip(
                    selected = selectedCollectionId == collection.collectionId,
                    onClick = { onCollectionChange(collection.collectionId) },
                    label = { Text(collection.title) },
                )
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = { onQueryChange(it.take(100)) },
            label = { Text(stringResource(R.string.library_search_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(SystemLibraryFilter.ALL, SystemLibraryFilter.CONTINUE, SystemLibraryFilter.UNREAD, SystemLibraryFilter.DORMANT).forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(stringResource(filter.label())) },
                )
            }
        }
        if (visible.isEmpty()) {
            StateView(
                TsuyomiStateKind.EMPTY,
                stringResource(R.string.library_no_results),
                message = stringResource(R.string.library_no_results_message),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                items(visible, key = { "${it.book.identity.sourceId}\u0000${it.book.identity.remoteBookId}" }) { entry ->
                    LibraryRow(entry, onOpenBook)
                    HorizontalDivider()
                }
            }
        }
        if (environment.effectiveProfile == DisplayProfile.EINK && filtered.isNotEmpty()) {
            PaginationBar(
                page = page.coerceAtMost(pageCount),
                pageCount = pageCount,
                onPrevious = { page = (page - 1).coerceAtLeast(1) },
                onNext = { page = (page + 1).coerceAtMost(pageCount) },
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LibraryRow(entry: LibraryEntry, onOpenBook: (LibraryEntry) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenBook(entry) },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 14.dp)) {
            Text(entry.book.title, style = MaterialTheme.typography.titleMedium)
            val authors = entry.book.authors.joinToString("、").ifBlank { stringResource(R.string.library_unknown_author) }
            Text(authors, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                entry.rating?.let { Text(stringResource(R.string.library_rating, it), style = MaterialTheme.typography.labelLarge) }
                if (!entry.sourceAvailable) Text(stringResource(R.string.library_dormant), style = MaterialTheme.typography.labelLarge)
                entry.reconciliation?.let { Text(stringResource(it.label()), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
fun LocalBookDetailsScreen(
    entry: LibraryEntry,
    tagDraft: String,
    onTagDraftChange: (String) -> Unit,
    onSaveTags: () -> Unit,
    onSetRating: (Int?) -> Unit,
    onOpenSource: () -> Unit,
    onRetryRemoteSync: () -> Unit,
    remoteRetryMessage: String?,
    remoteRetryEnabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(entry.book.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(entry.book.authors.joinToString("、").ifBlank { stringResource(R.string.library_unknown_author) })
        if (!entry.sourceAvailable) {
            Text(stringResource(R.string.library_source_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.library_rating_heading), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { rating ->
                TsuyomiButton(
                    text = rating.toString(),
                    onClick = { onSetRating(if (entry.rating == rating) null else rating) },
                    style = if (entry.rating == rating) TsuyomiButtonStyle.PRIMARY else TsuyomiButtonStyle.SECONDARY,
                )
            }
        }
        OutlinedTextField(
            value = tagDraft,
            onValueChange = { onTagDraftChange(it.take(512)) },
            label = { Text(stringResource(R.string.library_tags_label)) },
            supportingText = { Text(stringResource(R.string.library_tags_help)) },
            modifier = Modifier.fillMaxWidth(),
        )
        TsuyomiButton(text = stringResource(R.string.library_save_tags), onClick = onSaveTags)
        entry.reconciliation?.let { Text(stringResource(it.label()), style = MaterialTheme.typography.titleMedium) }
        if (entry.reconciliation in setOf(RemoteReconciliationState.UNRESOLVED, RemoteReconciliationState.CANCELLED)) {
            TsuyomiButton(
                text = stringResource(R.string.library_retry_remote_sync),
                onClick = onRetryRemoteSync,
                enabled = remoteRetryEnabled,
            )
            Text(stringResource(R.string.library_retry_remote_sync_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
            remoteRetryMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        TsuyomiButton(text = stringResource(R.string.library_open_source), onClick = onOpenSource)
        TsuyomiButton(text = stringResource(R.string.library_remove), onClick = onRemove, style = TsuyomiButtonStyle.SECONDARY)
    }
}

private fun SystemLibraryFilter.label(): Int = when (this) {
    SystemLibraryFilter.ALL -> R.string.library_filter_all
    SystemLibraryFilter.CONTINUE -> R.string.library_filter_continue
    SystemLibraryFilter.RECENT -> R.string.library_filter_recent
    SystemLibraryFilter.UNREAD -> R.string.library_filter_unread
    SystemLibraryFilter.DORMANT -> R.string.library_filter_dormant
}

private fun RemoteReconciliationState.label(): Int = when (this) {
    RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.IN_FLIGHT -> R.string.library_sync_pending
    RemoteReconciliationState.CONFIRMED -> R.string.library_sync_confirmed
    RemoteReconciliationState.UNRESOLVED -> R.string.library_sync_unresolved
    RemoteReconciliationState.CANCELLED -> R.string.library_sync_cancelled
}
