/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.TsuyomiMenuItem
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiDialog
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTextField
import org.tsuyomi.shared.librarydomain.CollectionKind
import org.tsuyomi.shared.librarydomain.LibraryCollection
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.model.BookIdentity

enum class SystemLibraryFilter {
    ALL,
    CONTINUE,
    READ_LATER,
    UNREAD,
    DORMANT;

    companion object {
        val primaryTabs = listOf(ALL, CONTINUE, READ_LATER)
    }
}

enum class LibraryUpdateFilter { ALL, UPDATES_ONLY }

enum class LibraryLayout {
    GRID,
    LIST,
    COMPACT;

    fun next(): LibraryLayout = entries[(ordinal + 1) % entries.size]
}

enum class LibrarySortMode(val label: String) {
    SMART("智能"),
    CUSTOM("自定义"),
    TITLE("书名"),
    ADDED("加入时间"),
    RECENT("最近阅读"),
}

data class LibraryMirrorShortcut(
    val sourceId: String,
    val targetId: String?,
    val label: String,
    val count: Int,
    val frozen: Boolean,
)

data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val loading: Boolean = true,
    val failure: String? = null,
    val refreshing: Boolean = false,
    val refreshFailure: String? = null,
    val rootNodePlacements: List<LibraryRootNodePlacement> = emptyList(),
    val collectionCounts: Map<String, Int> = emptyMap(),
    val filter: SystemLibraryFilter = SystemLibraryFilter.ALL,
    val updateFilter: LibraryUpdateFilter = LibraryUpdateFilter.ALL,
    val isRootProjection: Boolean = true,
    val updates: Map<BookIdentity, org.tsuyomi.shared.librarydomain.UnresolvedUpdate> = emptyMap(),
    val updateSession: org.tsuyomi.shared.librarydomain.UpdateSessionSummary? = null,
    val updateOnlyEntries: List<LibraryEntry> = emptyList(),
    val layout: LibraryLayout = LibraryLayout.GRID,
    val sortMode: LibrarySortMode = LibrarySortMode.SMART,
    val sortDescending: Boolean = false,
    val firstVisibleIndex: Int = 0,
    val firstVisibleOffset: Int = 0,
    val selectionKind: LibrarySelectionKind? = null,
    val selectedBookIds: Set<BookIdentity> = emptySet(),
    val selectedCollectionIds: Set<String> = emptySet(),
    val selectionDialog: LibrarySelectionDialog? = null,
    val mirrorShortcuts: List<LibraryMirrorShortcut> = emptyList(),
    val websiteGroupingSourceIds: Set<String> = emptySet(),
)

fun LibraryUiState.projectedEntries(): List<LibraryEntry> {
    val candidates = if (isRootProjection && updateFilter == LibraryUpdateFilter.UPDATES_ONLY) {
        entries + updateOnlyEntries
    } else {
        entries
    }
    val filtered = candidates
        .filter(filter::accepts)
        .filter { !isRootProjection || updateFilter == LibraryUpdateFilter.ALL || it.book.identity in updates }
    return when (sortMode) {
        LibrarySortMode.SMART -> if (isRootProjection) smartOrder(filtered, updates) else filtered
        LibrarySortMode.CUSTOM -> when (filter) {
            SystemLibraryFilter.CONTINUE -> filtered.sortedByDescending(LibraryEntry::readerActivityAt)
            SystemLibraryFilter.UNREAD -> filtered.sortedByDescending { it.book.metadataUpdatedAt }
            SystemLibraryFilter.ALL,
            SystemLibraryFilter.READ_LATER,
            SystemLibraryFilter.DORMANT,
            -> filtered
        }
        LibrarySortMode.TITLE -> filtered.sortedBy { it.book.title }.let { if (sortDescending) it.asReversed() else it }
        LibrarySortMode.ADDED -> filtered.sortedBy { it.libraryAddedAt }.let { if (sortDescending) it.asReversed() else it }
        LibrarySortMode.RECENT -> {
            val withHistory = filtered.filter { it.readerActivityAt() != null }.sortedBy(LibraryEntry::readerActivityAt)
            val orderedHistory = if (sortDescending) withHistory.asReversed() else withHistory
            orderedHistory + filtered.filter { it.readerActivityAt() == null }
        }
    }
}

private fun smartOrder(
    entries: List<LibraryEntry>,
    updates: Map<BookIdentity, org.tsuyomi.shared.librarydomain.UnresolvedUpdate>,
): List<LibraryEntry> {
    val updated = entries.filter { it.book.identity in updates }
    val lastRead = updated.filter { it.progress != null }.maxByOrNull { requireNotNull(it.progress).updatedAt }
    val remainingUpdates = updated.filterNot { it === lastRead }.sortedWith(
        compareByDescending<LibraryEntry> { entry ->
            val update = requireNotNull(updates[entry.book.identity])
            update.lastUpdatedDate?.let { raw ->
                runCatching { java.time.LocalDate.parse(raw).toEpochDay() * 86_400_000L }.getOrNull()
            } ?: update.detectedAt
        }.thenByDescending { entry -> requireNotNull(updates[entry.book.identity]).detectedAt },
    )
    return listOfNotNull(lastRead) + remainingUpdates + entries.filterNot { it.book.identity in updates }
}

private fun SystemLibraryFilter.accepts(entry: LibraryEntry): Boolean = when (this) {
    SystemLibraryFilter.ALL -> true
    SystemLibraryFilter.CONTINUE -> entry.readerVisitedAt != null
    SystemLibraryFilter.READ_LATER -> entry.readLater
    SystemLibraryFilter.UNREAD -> false
    SystemLibraryFilter.DORMANT -> entry.book.identity is BookIdentity.Remote && !entry.sourceAvailable
}

private fun LibraryEntry.readerActivityAt() = readerVisitedAt ?: progress?.updatedAt


@Composable
fun LibraryScreen(
    state: LibraryUiState,
    collections: List<LibraryCollection>,
    showNavigationNodes: Boolean,
    modifier: Modifier = Modifier,
    currentCollectionId: String? = null,
    primaryTabStates: Map<SystemLibraryFilter, LibraryUiState> = emptyMap(),
    onSelectTab: suspend (SystemLibraryFilter) -> Unit = {},
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    onOpenMirror: (LibraryMirrorShortcut) -> Unit = {},
    onOpenUpdateSettings: () -> Unit = {},
    onCancelUpdateScan: () -> Unit = {},
    onRefreshUpdates: () -> Unit = {},
    onEditFilter: () -> Unit = {},
    onClearFilter: () -> Unit = {},
    onRetry: () -> Unit,
    onImportLocalBooks: (() -> Unit)? = null,
    onLongPressBook: (BookIdentity) -> Unit = {},
    onToggleBookSelection: (BookIdentity) -> Unit = {},
    onLongPressCollection: (String) -> Unit = {},
    onToggleCollectionSelection: (String) -> Unit = {},
    onDropBooks: (LibraryDragPayload, LibraryDropDestination) -> Unit = { _, _ -> },
    reorderEnabled: Boolean = false,
    onDismissSelectionDialog: () -> Unit = {},
    onCreateCollectionFromSelection: (String) -> Unit = {},
    onAddSelectionToCollection: (String) -> Unit = {},
    deletionFolderCount: Int? = null,
    deletionMembershipCount: Int = 0,
    deletionMessage: String? = null,
    deletionConfirmEnabled: Boolean = true,
    deletionSubtree: Boolean = false,
    onDeletionPolicyChange: (Boolean) -> Unit = {},
    onRemoveSelection: () -> Unit = {},
    onViewportChanged: (LibraryViewport) -> Unit = {},
    onViewportSettled: suspend (Int, Int) -> Unit = { _, _ -> },
    coverState: @Composable (LibraryEntry) -> CoverUiState = { entry ->
        val sourceLabel = (entry.book.identity as? BookIdentity.Remote)?.sourceId
        CoverUiState.Fallback(FallbackSpec(entry.book.title, sourceLabel))
    },
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit = { _, _ -> },
    onIgnoreUpdate: suspend (org.tsuyomi.shared.librarydomain.UnresolvedUpdate) -> org.tsuyomi.shared.librarydomain.UpdateUndo? = { null },
    onUndoUpdate: suspend (org.tsuyomi.shared.librarydomain.UpdateUndo) -> Unit = {},
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
        else -> LibraryPresentation(
            state = state,
            primaryTabStates = primaryTabStates,
            collections = collections,
            currentCollectionId = currentCollectionId,
            showNavigationNodes = showNavigationNodes,
            onOpenCollection = onOpenCollection,
            onSelectTab = onSelectTab,
            onOpenBook = { entry ->
                if (state.selectionKind == LibrarySelectionKind.BOOK && entry.localMembership) {
                    onToggleBookSelection(entry.book.identity)
                } else {
                    onOpenBook(entry)
                }
            },
            onOpenMirror = onOpenMirror,
            onOpenUpdateSettings = onOpenUpdateSettings,
            onCancelUpdateScan = onCancelUpdateScan,
            onRefreshUpdates = onRefreshUpdates,
            onEditFilter = onEditFilter,
            onClearFilter = onClearFilter,
            onRetry = onRetry,
            onImportLocalBooks = onImportLocalBooks,
            onLongPressBook = onLongPressBook,
            onToggleBookSelection = onToggleBookSelection,
            onLongPressCollection = onLongPressCollection,
            onUndoUpdate = onUndoUpdate,
            onToggleCollectionSelection = onToggleCollectionSelection,
            onDropBooks = onDropBooks,
            reorderEnabled = reorderEnabled,
            onIgnoreUpdate = onIgnoreUpdate,
            onViewportChanged = onViewportChanged,
            onViewportSettled = onViewportSettled,
            coverState = coverState,
            onCoverVisibility = onCoverVisibility,
            modifier = modifier,
        )
    }
    LibrarySelectionDialogs(
        state = state,
        collections = collections,
        onDismiss = onDismissSelectionDialog,
        onCreateCollection = onCreateCollectionFromSelection,
        deletionFolderCount = deletionFolderCount,
        deletionMembershipCount = deletionMembershipCount,
        deletionMessage = deletionMessage,
        deletionConfirmEnabled = deletionConfirmEnabled,
        deletionSubtree = deletionSubtree,
        onDeletionPolicyChange = onDeletionPolicyChange,
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
    deletionFolderCount: Int?,
    deletionMembershipCount: Int,
    deletionMessage: String?,
    deletionConfirmEnabled: Boolean,
    deletionSubtree: Boolean,
    onDeletionPolicyChange: (Boolean) -> Unit,
    onAddToCollection: (String) -> Unit,
    onRemove: () -> Unit,
) {
    when (state.selectionDialog) {
        LibrarySelectionDialog.CREATE_COLLECTION -> {
            var name by rememberSaveable { mutableStateOf("") }
            TsuyomiDialog(
                onDismissRequest = onDismiss,
                title = "用所选书籍新建收藏夹",
                body = {
                    TsuyomiTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "收藏夹名称",
                        singleLine = true,
                    )
                },
                confirmLabel = "创建",
                onConfirm = { onCreateCollection(name.trim()) },
                confirmEnabled = name.isNotBlank(),
                dismissLabel = "取消",
            )
        }
        LibrarySelectionDialog.ADD_TO_COLLECTION -> {
            val manualCollections = collections.filter {
                it.kind == CollectionKind.MANUAL && it.collectionId !in state.selectedCollectionIds
            }
            val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
            TsuyomiDialog(
                onDismissRequest = onDismiss,
                title = if (state.selectionKind == LibrarySelectionKind.BOOK) "加入收藏夹" else "移入收藏夹",
                body = {
                    if (!eInk) {
                        if (manualCollections.isEmpty()) {
                            Text("没有可用的目标收藏夹。")
                        } else {
                            manualCollections.forEach { collection ->
                                TsuyomiButton(
                                    text = collection.title,
                                    onClick = { onAddToCollection(collection.collectionId) },
                                    modifier = Modifier.fillMaxWidth(),
                                    style = TsuyomiButtonStyle.TEXT,
                                )
                            }
                        }
                    } else {
                        TsuyomiMeasuredPager(itemCount = maxOf(1, manualCollections.size),
                            itemKey = { if (manualCollections.isEmpty()) "empty" else "collection:${manualCollections[it].collectionId}" },
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag("library-add-to-collection-pager"),
                            fillViewport = false,
                            itemContent = { index ->
                                if (manualCollections.isEmpty()) TsuyomiMeasuredTextPager("没有可用的目标收藏夹。", fillViewport = false, alwaysShowPageControls = false)
                                else {
                                    val collection = manualCollections[index]
                                    TsuyomiMenuItem(collection.title, { onAddToCollection(collection.collectionId) }, modifier = Modifier.testTag("library-add-collection-${collection.collectionId}"))
                                }
                            })
                    }
                },
                dismissLabel = "取消",
            )
        }
        LibrarySelectionDialog.CONFIRM_REMOVE -> {
            val selectingCollections = deletionFolderCount != null || state.selectionKind == LibrarySelectionKind.COLLECTION
            val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
            val deletionCopy = if (selectingCollections) {
                if (deletionFolderCount == null) stringResource(R.string.collection_delete_loading)
                else stringResource(R.string.collection_delete_preview, deletionFolderCount, deletionMembershipCount) + "\n" +
                    stringResource(if (deletionSubtree) R.string.collection_delete_subtree_consequence
                        else R.string.collection_delete_reparent_consequence)
            } else "将处理 ${state.selectedBookIds.size} 本书。网站书架不会被修改。"
            TsuyomiDialog(
                onDismissRequest = onDismiss,
                title = if (selectingCollections) stringResource(
                    if (deletionSubtree) R.string.collection_delete_subtree_title else R.string.collection_delete_reparent_title,
                ) else "移除所选书籍？",
                text = if (selectingCollections && eInk) null else deletionCopy,
                body = {
                    if (selectingCollections) {
                        if (eInk) {
                            TsuyomiMeasuredPager(itemCount = 4, itemKey = { "delete:$it" }, modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag("library-collection-delete-pager"), fillViewport = false,
                                itemContent = { index -> when (index) {
                                    0 -> TsuyomiMeasuredTextPager(deletionCopy, fillViewport = false, alwaysShowPageControls = false)
                                    1 -> deletionMessage?.let { TsuyomiMeasuredTextPager(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fillViewport = false, alwaysShowPageControls = false) }
                                    2 -> TsuyomiMenuItem(stringResource(R.string.collection_delete_reparent), { onDeletionPolicyChange(false) }, selected = !deletionSubtree,
                                        enabled = deletionConfirmEnabled || deletionMessage != null, modifier = Modifier.testTag("library-delete-policy-reparent"))
                                    else -> TsuyomiMenuItem(stringResource(R.string.collection_delete_subtree), { onDeletionPolicyChange(true) }, selected = deletionSubtree,
                                        enabled = deletionConfirmEnabled || deletionMessage != null, modifier = Modifier.testTag("library-delete-policy-subtree"))
                                } })
                        } else {
                            deletionMessage?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                            TsuyomiButton(
                                text = stringResource(R.string.collection_delete_reparent),
                                onClick = { onDeletionPolicyChange(false) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = deletionConfirmEnabled || deletionMessage != null,
                                style = if (deletionSubtree) TsuyomiButtonStyle.SECONDARY else TsuyomiButtonStyle.PRIMARY,
                            )
                            TsuyomiButton(
                                text = stringResource(R.string.collection_delete_subtree),
                                onClick = { onDeletionPolicyChange(true) },
                                modifier = Modifier.fillMaxWidth(),
                                style = if (deletionSubtree) TsuyomiButtonStyle.PRIMARY else TsuyomiButtonStyle.SECONDARY,
                                enabled = deletionConfirmEnabled || deletionMessage != null,
                            )
                        }
                    }
                },
                confirmLabel = if (selectingCollections) stringResource(
                    if (deletionSubtree) R.string.collection_delete_subtree_confirm
                    else R.string.collection_delete_reparent_confirm,
                ) else "移除",
                onConfirm = onRemove,
                confirmEnabled = !selectingCollections || (deletionFolderCount != null && deletionConfirmEnabled),
                dismissLabel = stringResource(R.string.collection_delete_cancel),
                destructive = selectingCollections,
            )
        }
        null -> Unit
    }
}




internal fun SystemLibraryFilter.label(): Int = when (this) {
    SystemLibraryFilter.ALL -> R.string.library_filter_all
    SystemLibraryFilter.CONTINUE -> R.string.library_filter_continue
    SystemLibraryFilter.READ_LATER -> R.string.library_filter_read_later
    SystemLibraryFilter.UNREAD -> R.string.library_filter_unread
    SystemLibraryFilter.DORMANT -> R.string.library_filter_dormant
}



