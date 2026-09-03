/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.tsuyomi.core.database.CollectionKind
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.PaginationBar
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.icons.TsuyomiIcons

enum class SystemLibraryFilter { ALL, CONTINUE, RECENT, READ_LATER, UNREAD, DORMANT }

enum class LibraryLayout {
    GRID,
    LIST,
    COMPACT;

    fun next(): LibraryLayout = entries[(ordinal + 1) % entries.size]
}

enum class LibrarySortMode(val label: String) {
    CUSTOM("自定义"),
    TITLE("书名"),
    ADDED("加入时间"),
    RECENT("最近阅读"),
}

data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val loading: Boolean = true,
    val failure: String? = null,
    val refreshing: Boolean = false,
    val refreshFailure: String? = null,
    val shortcutOrder: List<String> = emptyList(),
    val shortcutLocked: Boolean = false,
    val filter: SystemLibraryFilter = SystemLibraryFilter.ALL,
    val layout: LibraryLayout = LibraryLayout.GRID,
    val sortMode: LibrarySortMode = LibrarySortMode.CUSTOM,
    val sortDescending: Boolean = false,
    val sortOpen: Boolean = false,
    val selectionKind: LibrarySelectionKind? = null,
    val selectedBookIds: Set<BookIdentity> = emptySet(),
    val selectedCollectionIds: Set<String> = emptySet(),
    val selectionDialog: LibrarySelectionDialog? = null,
)
fun LibraryUiState.projectedEntries(): List<LibraryEntry> {
    val filtered = entries.filter(filter::accepts)
    return when (sortMode) {
        LibrarySortMode.CUSTOM -> when (filter) {
            SystemLibraryFilter.CONTINUE,
            SystemLibraryFilter.RECENT,
            -> filtered.sortedByDescending { it.progress?.updatedAt }
            SystemLibraryFilter.UNREAD -> filtered.sortedByDescending { it.book.metadataUpdatedAt }
            SystemLibraryFilter.ALL,
            SystemLibraryFilter.READ_LATER,
            SystemLibraryFilter.DORMANT,
            -> filtered
        }
        LibrarySortMode.TITLE -> filtered.sortedBy { it.book.title }.let { if (sortDescending) it.asReversed() else it }
        LibrarySortMode.ADDED -> filtered.sortedBy { it.libraryAddedAt }.let { if (sortDescending) it.asReversed() else it }
        LibrarySortMode.RECENT -> {
            val withHistory = filtered.filter { it.progress != null }.sortedBy { it.progress?.updatedAt }
            val orderedHistory = if (sortDescending) withHistory.asReversed() else withHistory
            orderedHistory + filtered.filter { it.progress == null }
        }
    }
}

private fun SystemLibraryFilter.accepts(entry: LibraryEntry): Boolean = when (this) {
    SystemLibraryFilter.ALL -> true
    SystemLibraryFilter.CONTINUE -> entry.progress?.locator?.bookProgress?.let { it < 1.0 } ?: (entry.progress != null)
    SystemLibraryFilter.RECENT -> entry.progress != null
    SystemLibraryFilter.READ_LATER -> entry.readLater
    SystemLibraryFilter.UNREAD -> entry.book.hasUnreadUpdate
    SystemLibraryFilter.DORMANT -> !entry.sourceAvailable
}


@Composable
fun LibraryScreen(
    state: LibraryUiState,
    collections: List<LibraryCollection>,
    showNavigationNodes: Boolean,
    onOpenSystemNode: (SystemLibraryFilter) -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    onCreateCollection: () -> Unit,
    onRetry: () -> Unit,
    onDismissSort: () -> Unit,
    onSelectSort: (LibrarySortMode) -> Unit,
    onSelectSortDirection: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLongPressBook: (BookIdentity) -> Unit = {},
    onToggleBookSelection: (BookIdentity) -> Unit = {},
    onLongPressCollection: (String) -> Unit = {},
    onToggleCollectionSelection: (String) -> Unit = {},
    onDropBooks: (LibraryDragPayload, LibraryDropDestination) -> Unit = { _, _ -> },
    reorderEnabled: Boolean = false,
    onShortcutLockedChanged: (Boolean) -> Unit = {},
    onDismissSelectionDialog: () -> Unit = {},
    onCreateCollectionFromSelection: (String) -> Unit = {},
    onAddSelectionToCollection: (String) -> Unit = {},
    onRemoveSelection: () -> Unit = {},
    coverState: (LibraryEntry) -> CoverUiState = { entry ->
        CoverUiState.Fallback(FallbackSpec(entry.book.title, entry.book.identity.sourceId))
    },
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit = { _, _ -> },
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
        LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK -> FrozenEInkLibraryContent(
            state = state,
            collections = collections,
            showNavigationNodes = showNavigationNodes,
            onOpenSystemNode = onOpenSystemNode,
            onOpenCollection = onOpenCollection,
            onOpenBook = onOpenBook,
            modifier = modifier.fillMaxSize(),
        )
        else -> AtlasLibraryPresentation(
            state = state,
            collections = collections,
            showNavigationNodes = showNavigationNodes,
            onOpenSystemNode = onOpenSystemNode,
            onOpenCollection = onOpenCollection,
            onOpenBook = onOpenBook,
            coverState = coverState,
            onCoverVisibility = onCoverVisibility,
            onCreateCollection = onCreateCollection,
            onRetry = onRetry,
            onDismissSort = onDismissSort,
            onSelectSort = onSelectSort,
            onSelectSortDirection = onSelectSortDirection,
            onLongPressBook = onLongPressBook,
            onToggleBookSelection = onToggleBookSelection,
            onLongPressCollection = onLongPressCollection,
            onToggleCollectionSelection = onToggleCollectionSelection,
            onShortcutLockedChanged = onShortcutLockedChanged,
            onDropBooks = onDropBooks,
            reorderEnabled = reorderEnabled,
            modifier = modifier,
        )
    }
    LibrarySelectionDialogs(
        state = state,
        collections = collections,
        onDismiss = onDismissSelectionDialog,
        onCreateCollection = onCreateCollectionFromSelection,
        onAddToCollection = onAddSelectionToCollection,
        onRemove = onRemoveSelection,
    )
}

@Composable
private fun LibrarySelectionDialogs(
    state: LibraryUiState,
    collections: List<LibraryCollection>,
    onDismiss: () -> Unit,
    onCreateCollection: (String) -> Unit,
    onAddToCollection: (String) -> Unit,
    onRemove: () -> Unit,
) {
    when (state.selectionDialog) {
        LibrarySelectionDialog.CREATE_COLLECTION -> {
            var name by rememberSaveable { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("用所选书籍新建收藏夹") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("收藏夹名称") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { onCreateCollection(name.trim()) },
                        enabled = name.isNotBlank(),
                    ) { Text("创建") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
            )
        }
        LibrarySelectionDialog.ADD_TO_COLLECTION -> {
            val manualCollections = collections.filter {
                it.kind == CollectionKind.MANUAL && it.collectionId !in state.selectedCollectionIds
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(if (state.selectionKind == LibrarySelectionKind.BOOK) "加入收藏夹" else "移入收藏夹")
                },
                text = {
                    if (manualCollections.isEmpty()) {
                        Text("没有可用的目标收藏夹。")
                    } else {
                        Column {
                            manualCollections.forEach { collection ->
                                TextButton(
                                    onClick = { onAddToCollection(collection.collectionId) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(collection.title) }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
            )
        }
        LibrarySelectionDialog.CONFIRM_REMOVE -> {
            val selectingCollections = state.selectionKind == LibrarySelectionKind.COLLECTION
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(if (selectingCollections) "删除所选收藏夹？" else "移除所选书籍？") },
                text = {
                    Text(
                        if (selectingCollections) {
                            "将删除 ${state.selectedCollectionIds.size} 个本地收藏夹。收藏夹内书籍仍保留在书架。"
                        } else {
                            "将处理 ${state.selectedBookIds.size} 本书。网站书架不会被修改。"
                        },
                    )
                },
                confirmButton = { TextButton(onClick = onRemove) { Text(if (selectingCollections) "删除" else "移除") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
            )
        }
        null -> Unit
    }
}

private data class LibraryNode(
    val key: String,
    val title: String,
    val kind: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun FrozenEInkLibraryContent(
    state: LibraryUiState,
    collections: List<LibraryCollection>,
    showNavigationNodes: Boolean,
    onOpenSystemNode: (SystemLibraryFilter) -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    modifier: Modifier,
) {
    val wideWindow = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp
    }
    val environment = LocalDisplayEnvironment.current
    val filtered = state.projectedEntries()
    val pageSize = if (environment.effectiveProfile == DisplayProfile.EINK) 6 else filtered.size.coerceAtLeast(1)
    val pageCount = ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    var page by rememberSaveable(state.filter, filtered.size) { mutableIntStateOf(1) }
    val visible = filtered.drop((page - 1) * pageSize).take(pageSize)
    val nodes = if (showNavigationNodes) {
        buildList {
            listOf(
                SystemLibraryFilter.CONTINUE,
                SystemLibraryFilter.RECENT,
                SystemLibraryFilter.READ_LATER,
                SystemLibraryFilter.DORMANT,
            ).forEach { filter ->
                add(
                    LibraryNode(
                        key = "system:${filter.name}",
                        title = stringResource(filter.label()),
                        kind = stringResource(R.string.library_node_system),
                        icon = when (filter) {
                            SystemLibraryFilter.CONTINUE -> TsuyomiIcons.ContinueReading
                            SystemLibraryFilter.RECENT -> TsuyomiIcons.Recent
                            SystemLibraryFilter.READ_LATER -> TsuyomiIcons.Bookmark
                            SystemLibraryFilter.DORMANT -> TsuyomiIcons.Dormant
                            SystemLibraryFilter.ALL, SystemLibraryFilter.UNREAD -> TsuyomiIcons.Shelf
                        },
                        onClick = { onOpenSystemNode(filter) },
                    ),
                )
            }
            collections.forEach { collection ->
                add(
                    LibraryNode(
                        key = "collection:${collection.collectionId}",
                        title = collection.title,
                        kind = stringResource(collection.kind.nodeKindLabel()),
                        icon = when (collection.kind) {
                            CollectionKind.MANUAL -> TsuyomiIcons.Folder
                            CollectionKind.SMART -> TsuyomiIcons.SmartCollection
                            CollectionKind.SUBSCRIPTION -> TsuyomiIcons.Mirror
                        },
                        onClick = { onOpenCollection(collection) },
                    ),
                )
            }
        }
    } else {
        emptyList()
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        state.refreshFailure?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
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
        when (state.layout) {
            LibraryLayout.GRID -> LazyVerticalGrid(
                columns = if (wideWindow) GridCells.Adaptive(160.dp) else GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).padding(top = 8.dp).testTag("library-scroll-domain"),
            ) {
                items(nodes, key = { it.key }) { node -> LibraryNodeGridCard(node) }
                items(visible, key = ::libraryEntryKey) { entry -> LibraryGridCard(entry, onOpenBook) }
            }
            LibraryLayout.LIST -> LazyColumn(
                Modifier.weight(1f).padding(top = 8.dp).testTag("library-scroll-domain"),
            ) {
                items(nodes, key = { it.key }) { node ->
                    LibraryNodeRow(node, compact = false)
                    HorizontalDivider()
                }
                items(visible, key = ::libraryEntryKey) { entry ->
                    LibraryRow(entry, onOpenBook)
                    HorizontalDivider()
                }
            }
            LibraryLayout.COMPACT -> LazyColumn(
                Modifier.weight(1f).padding(top = 8.dp).testTag("library-scroll-domain"),
            ) {
                items(nodes, key = { it.key }) { node ->
                    LibraryNodeRow(node, compact = true)
                    HorizontalDivider()
                }
                items(visible, key = ::libraryEntryKey) { entry ->
                    LibraryCompactRow(entry, onOpenBook)
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
private fun LibraryNodeGridCard(node: LibraryNode) {
    Card(onClick = node.onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = node.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(node.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(
                    node.kind,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LibraryNodeRow(node: LibraryNode, compact: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = node.onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = 4.dp,
                vertical = if (compact) 10.dp else 14.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = node.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                node.title,
                style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(node.kind, style = MaterialTheme.typography.labelMedium)
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
private fun LibraryGridCard(entry: LibraryEntry, onOpenBook: (LibraryEntry) -> Unit) {
    Card(
        onClick = { onOpenBook(entry) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.book.title.firstOrNull()?.toString().orEmpty(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = entry.book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LibraryCompactRow(entry: LibraryEntry, onOpenBook: (LibraryEntry) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenBook(entry) },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(entry.book.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (entry.readLater) {
                Text(stringResource(R.string.library_filter_read_later), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun libraryEntryKey(entry: LibraryEntry): String =
    "${entry.book.identity.sourceId}\u0000${entry.book.identity.remoteBookId}"


internal fun SystemLibraryFilter.label(): Int = when (this) {
    SystemLibraryFilter.ALL -> R.string.library_filter_all
    SystemLibraryFilter.CONTINUE -> R.string.library_filter_continue
    SystemLibraryFilter.RECENT -> R.string.library_filter_recent
    SystemLibraryFilter.READ_LATER -> R.string.library_filter_read_later
    SystemLibraryFilter.UNREAD -> R.string.library_filter_unread
    SystemLibraryFilter.DORMANT -> R.string.library_filter_dormant
}

private fun CollectionKind.nodeKindLabel(): Int = when (this) {
    CollectionKind.MANUAL -> R.string.library_node_manual_collection
    CollectionKind.SMART -> R.string.library_node_smart_collection
    CollectionKind.SUBSCRIPTION -> R.string.library_node_mirror
}


private fun RemoteReconciliationState.label(): Int = when (this) {
    RemoteReconciliationState.PENDING_USER_ACTION -> R.string.library_reconciliation_pending_user_action
    RemoteReconciliationState.IN_FLIGHT -> R.string.library_reconciliation_in_flight
    RemoteReconciliationState.CONFIRMED -> R.string.library_reconciliation_confirmed
    RemoteReconciliationState.UNRESOLVED -> R.string.library_reconciliation_unresolved
    RemoteReconciliationState.CANCELLED -> R.string.library_reconciliation_cancelled
}
