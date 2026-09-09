/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTabOption
import org.tsuyomi.core.ui.components.TsuyomiLibraryTabRow
import org.tsuyomi.shared.model.BookIdentity

@Composable
internal fun LibraryPresentation(
    state: LibraryUiState,
    collections: List<LibraryCollection>,
    showNavigationNodes: Boolean,
    onOpenSystemNode: (SystemLibraryFilter) -> Unit,
    onSelectTab: (SystemLibraryFilter) -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    onOpenMirror: (LibraryMirrorShortcut) -> Unit,
    onCreateCollection: () -> Unit,
    onClearFilter: () -> Unit,
    onOpenUpdateSettings: () -> Unit,
    onCancelUpdateScan: () -> Unit,
    onRefreshUpdates: () -> Unit,
    onEditFilter: () -> Unit,
    onRetry: () -> Unit,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
    onLongPressCollection: (String) -> Unit,
    onToggleCollectionSelection: (String) -> Unit,
    onDropBooks: (LibraryDragPayload, LibraryDropDestination) -> Unit,
    onIgnoreUpdate: suspend (org.tsuyomi.shared.librarydomain.UnresolvedUpdate) -> org.tsuyomi.shared.librarydomain.UpdateUndo?,
    onUndoUpdate: suspend (org.tsuyomi.shared.librarydomain.UpdateUndo) -> Unit,
    onViewportChanged: (LibraryViewport) -> Unit,
    onViewportSettled: suspend (Int, Int) -> Unit,
    reorderEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val dragCoordinator = remember { LibraryDragCoordinator() }
    dragCoordinator.onLongPress = onLongPressBook
    dragCoordinator.onDrop = onDropBooks
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val ignoreUpdateMessage = stringResource(R.string.updates_ignore_confirmed)
    val undoLabel = stringResource(R.string.updates_undo)
    val ignoreUpdate: (org.tsuyomi.shared.librarydomain.UnresolvedUpdate) -> Unit = { update ->
        scope.launch {
            val undo = onIgnoreUpdate(update) ?: return@launch
            if (snackbarHostState.showSnackbar(ignoreUpdateMessage, undoLabel) == SnackbarResult.ActionPerformed) {
                onUndoUpdate(undo)
            }
        }
    }
    val filtered = state.projectedEntries()
    val structuralCollections = collections.filter { it.parentCollectionId == null }
    val rootMirrors = state.mirrorShortcuts.filter { it.targetId == null }
    val rootItems = if (showNavigationNodes && state.isRootProjection) {
        buildLibraryRootItems(
            entries = filtered,
            collections = structuralCollections,
            collectionCounts = state.collectionCounts,
            mirrors = rootMirrors,
            placements = state.rootNodePlacements,
            customOrder = state.sortMode == LibrarySortMode.CUSTOM,
        )
    } else {
        null
    }
    val showDestinationRail = showNavigationNodes && state.isRootProjection &&
        dragCoordinator.activeBookIds.isNotEmpty()

    Box(modifier.fillMaxSize().libraryDragOverlayHost(dragCoordinator)) {
        Column(Modifier.fillMaxSize()) {
            if (showNavigationNodes) {
                TsuyomiLibraryTabRow(
                    options = listOf(
                        TsuyomiTabOption(SystemLibraryFilter.ALL.name, "书架"),
                        TsuyomiTabOption(SystemLibraryFilter.CONTINUE.name, "继续阅读"),
                        TsuyomiTabOption(SystemLibraryFilter.READ_LATER.name, "稍后再读"),
                    ),
                    selectedKey = state.filter.name,
                    onSelect = { key ->
                        SystemLibraryFilter.entries.firstOrNull { it.name == key }?.let(onSelectTab)
                    },
                    modifier = Modifier,
                )
            }
            if (showNavigationNodes && state.isRootProjection && state.updateFilter == LibraryUpdateFilter.UPDATES_ONLY) {
                LibraryFilterSummary(
                    count = filtered.size,
                    onEdit = onEditFilter,
                    onClear = onClearFilter,
                )
            }
            if (showDestinationRail) {
                LibraryDragDestinationRail(
                    collections = structuralCollections,
                    mirrors = rootMirrors,
                    coordinator = dragCoordinator,
                )
            }
            if (showNavigationNodes && state.isRootProjection) {
                LibraryUpdateHeader(
                    state = state,
                    onCancelScan = onCancelUpdateScan,
                    onRetryScan = onRefreshUpdates,
                    onOpenSettings = onOpenUpdateSettings,
                )
            }
            val content: @Composable () -> Unit = {
                LibraryBookSurface(
                    entries = filtered,
                    state = state,
                    onOpenBook = onOpenBook,
                    onLongPressBook = onLongPressBook,
                    onToggleBookSelection = onToggleBookSelection,
                    dragCoordinator = dragCoordinator,
                    dragEnabled = true,
                    reorderEnabled = reorderEnabled,
                    coverState = coverState,
                    onCoverVisibility = onCoverVisibility,
                    onIgnoreUpdate = ignoreUpdate,
                    onViewportChanged = onViewportChanged,
                    onViewportSettled = onViewportSettled,
                    rootItems = rootItems,
                    onOpenCollection = onOpenCollection,
                    onOpenMirror = onOpenMirror,
                    onLongPressCollection = onLongPressCollection,
                    onToggleCollectionSelection = onToggleCollectionSelection,
                    empty = {
                        StateView(
                            kind = TsuyomiStateKind.EMPTY,
                            title = when {
                                state.updateFilter == LibraryUpdateFilter.UPDATES_ONLY -> "没有待处理更新"
                                state.filter == SystemLibraryFilter.CONTINUE -> "没有继续阅读的书籍"
                                state.filter == SystemLibraryFilter.READ_LATER -> "稍后再读为空"
                                else -> "书架为空"
                            },
                            message = when {
                                state.updateFilter == LibraryUpdateFilter.UPDATES_ONLY -> "检查发现的新章节会显示在这里。"
                                state.filter == SystemLibraryFilter.CONTINUE -> "开始阅读后，未完成的书籍会显示在这里。"
                                state.filter == SystemLibraryFilter.READ_LATER -> "在书籍详情中加入稍后再读。"
                                else -> "从浏览或搜索中加入书籍。"
                            },
                            actionLabel = if (state.isRootProjection) null else "刷新",
                            onAction = if (state.isRootProjection) null else onRetry,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showNavigationNodes && state.isRootProjection) {
                org.tsuyomi.core.ui.components.TsuyomiPullToRefresh(
                    isRefreshing = state.updateSession?.state in setOf(
                        org.tsuyomi.shared.librarydomain.UpdateSessionStates.QUEUED,
                        org.tsuyomi.shared.librarydomain.UpdateSessionStates.RUNNING,
                    ),
                    onRefresh = onRefreshUpdates,
                    modifier = Modifier.weight(1f),
                ) { content() }
            } else {
                Box(Modifier.weight(1f)) { content() }
            }
        }
        LibraryDragVisualOverlay(
            coordinator = dragCoordinator,
            entries = state.entries,
            layout = state.layout,
            coverState = coverState,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
