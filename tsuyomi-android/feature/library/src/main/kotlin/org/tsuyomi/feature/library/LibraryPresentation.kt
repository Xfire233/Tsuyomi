/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiSnackbarHost
import org.tsuyomi.core.ui.components.TsuyomiSnackbarResult
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTabOption
import org.tsuyomi.core.ui.components.TsuyomiTextTabRow
import org.tsuyomi.core.ui.components.TsuyomiTextTabPagerDefaults
import org.tsuyomi.core.ui.components.rememberTsuyomiSnackbarState
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.shared.librarydomain.LibraryCollection
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.model.BookIdentity

@Composable
internal fun LibraryPresentation(
    state: LibraryUiState,
    primaryTabStates: Map<SystemLibraryFilter, LibraryUiState>,
    collections: List<LibraryCollection>,
    showNavigationNodes: Boolean,
    onSelectTab: suspend (SystemLibraryFilter) -> Unit,
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
    val scope = rememberCoroutineScope()
    val snackbarHostState = rememberTsuyomiSnackbarState()
    val ignoreUpdateMessage = stringResource(R.string.updates_ignore_confirmed)
    val undoLabel = stringResource(R.string.updates_undo)
    val ignoreUpdate: (org.tsuyomi.shared.librarydomain.UnresolvedUpdate) -> Unit = { update ->
        scope.launch {
            val undo = onIgnoreUpdate(update) ?: return@launch
            if (snackbarHostState.showMessage(ignoreUpdateMessage, undoLabel) == TsuyomiSnackbarResult.ACTION_PERFORMED) {
                onUndoUpdate(undo)
            }
        }
    }
    if (!showNavigationNodes) {
        val dragCoordinator = remember { LibraryDragCoordinator() }
        Box(modifier.fillMaxSize()) {
            LibraryPrimaryTabPage(
                state = state,
                collections = collections,
                active = true,
                dragCoordinator = dragCoordinator,
                onOpenCollection = onOpenCollection,
                onOpenBook = onOpenBook,
                onOpenMirror = onOpenMirror,
                onRetry = onRetry,
                coverState = coverState,
                onCoverVisibility = onCoverVisibility,
                onLongPressBook = onLongPressBook,
                onToggleBookSelection = onToggleBookSelection,
                onLongPressCollection = onLongPressCollection,
                onToggleCollectionSelection = onToggleCollectionSelection,
                onDropBooks = onDropBooks,
                onIgnoreUpdate = ignoreUpdate,
                onViewportChanged = onViewportChanged,
                onViewportSettled = onViewportSettled,
                reorderEnabled = reorderEnabled,
            )
            TsuyomiSnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
        return
    }
    val primaryTabs = SystemLibraryFilter.primaryTabs
    val selectedTab = state.filter.takeIf { it in primaryTabs } ?: SystemLibraryFilter.ALL
    val pagerState = rememberPagerState(
        initialPage = libraryPrimaryPageFor(selectedTab),
        pageCount = primaryTabs::size,
    )
    val pageDragCoordinators = remember {
        primaryTabs.associateWith { LibraryDragCoordinator() }
    }
    val activeDragCoordinator = pageDragCoordinators.getValue(selectedTab)
    val instantMotion = LocalDisplayEnvironment.current.instantMotion || rememberSystemReducedMotion()
    var tabTransitionJob by remember { mutableStateOf<Job?>(null) }
    val currentOnSelectTab by rememberUpdatedState(onSelectTab)
    val currentSelectedTab by rememberUpdatedState(selectedTab)
    val currentDragActive by rememberUpdatedState(activeDragCoordinator.activePayload != null)
    val filtered = state.projectedEntries()
    val structuralCollections = collections.filter { it.parentCollectionId == null }
    val rootMirrors = state.mirrorShortcuts.filter { it.targetId == null }
    val showDestinationRail = showNavigationNodes && state.isRootProjection &&
        activeDragCoordinator.activeBookIds.isNotEmpty()

    LaunchedEffect(selectedTab) {
        pageDragCoordinators
            .filterKeys { it != selectedTab }
            .values
            .forEach(LibraryDragCoordinator::cancel)
    }
    LaunchedEffect(selectedTab, instantMotion) {
        val target = libraryPrimaryPageFor(selectedTab)
        if (pagerState.settledPage != target) {
            if (instantMotion) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState) {
        // Nested pre-fling can briefly report idle before the page is aligned.
        snapshotFlow {
            pagerState.settledPage.takeIf {
                !pagerState.isScrollInProgress && pagerState.currentPageOffsetFraction == 0f
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { page ->
                val tab = libraryPrimaryTabAt(page) ?: return@collect
                if (!currentDragActive && tab != currentSelectedTab) {
                    currentOnSelectTab(tab)
                }
            }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (showNavigationNodes) {
                TsuyomiTextTabRow(
                    options = primaryTabs.map { tab ->
                        TsuyomiTabOption(
                            key = tab.name,
                            label = when (tab) {
                                SystemLibraryFilter.ALL -> "书架"
                                SystemLibraryFilter.CONTINUE -> "继续阅读"
                                SystemLibraryFilter.READ_LATER -> "稍后再读"
                                SystemLibraryFilter.UNREAD, SystemLibraryFilter.DORMANT -> error("Not a primary tab")
                            },
                        )
                    },
                    selectedKey = libraryPrimaryTabAt(pagerState.currentPage)?.name ?: selectedTab.name,
                    onSelect = { key ->
                        val target = primaryTabs.indexOfFirst { it.name == key }
                        if (target >= 0 && !currentDragActive &&
                            (target != pagerState.settledPage || pagerState.isScrollInProgress)
                        ) {
                            tabTransitionJob?.cancel()
                            tabTransitionJob = scope.launch {
                                if (instantMotion) pagerState.scrollToPage(target)
                                else pagerState.animateScrollToPage(target)
                            }
                        }
                    },
                    modifier = Modifier.testTag("library-primary-tabs"),
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
                    coordinator = activeDragCoordinator,
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
            val pagerContent: @Composable () -> Unit = {
                HorizontalPager(
                    state = pagerState,
                    key = { page -> primaryTabs[page].name },
                    flingBehavior = TsuyomiTextTabPagerDefaults.flingBehavior(pagerState),
                    userScrollEnabled = libraryPagerUserScrollEnabled(activeDragCoordinator.activePayload != null),
                    modifier = Modifier.fillMaxSize().testTag("library-primary-pager"),
                ) { page ->
                    val tab = primaryTabs[page]
                    val pageState = requireNotNull(primaryTabStates[tab]) {
                        "Library primary page state missing for $tab"
                    }
                    LibraryPrimaryTabPage(
                        state = pageState,
                        collections = collections,
                        active = page == pagerState.settledPage && tab == selectedTab,
                        dragCoordinator = pageDragCoordinators.getValue(tab),
                        onOpenCollection = onOpenCollection,
                        onOpenBook = onOpenBook,
                        onOpenMirror = onOpenMirror,
                        onRetry = onRetry,
                        coverState = coverState,
                        onCoverVisibility = onCoverVisibility,
                        onLongPressBook = onLongPressBook,
                        onToggleBookSelection = onToggleBookSelection,
                        onLongPressCollection = onLongPressCollection,
                        onToggleCollectionSelection = onToggleCollectionSelection,
                        onDropBooks = onDropBooks,
                        onIgnoreUpdate = ignoreUpdate,
                        onViewportChanged = onViewportChanged,
                        onViewportSettled = onViewportSettled,
                        reorderEnabled = reorderEnabled,
                    )
                }
            }
            if (showNavigationNodes && state.isRootProjection) {
                org.tsuyomi.core.ui.components.TsuyomiPullToRefresh(
                    isRefreshing = state.updateSession?.state in setOf(
                        org.tsuyomi.shared.librarydomain.UpdateSessionStates.QUEUED,
                        org.tsuyomi.shared.librarydomain.UpdateSessionStates.RUNNING,
                    ),
                    onRefresh = onRefreshUpdates,
                    modifier = Modifier.weight(1f),
                ) { pagerContent() }
            } else {
                Box(Modifier.weight(1f)) { pagerContent() }
            }
        }
        TsuyomiSnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun LibraryPrimaryTabPage(
    state: LibraryUiState,
    collections: List<LibraryCollection>,
    active: Boolean,
    dragCoordinator: LibraryDragCoordinator,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    onOpenMirror: (LibraryMirrorShortcut) -> Unit,
    onRetry: () -> Unit,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
    onLongPressCollection: (String) -> Unit,
    onToggleCollectionSelection: (String) -> Unit,
    onDropBooks: (LibraryDragPayload, LibraryDropDestination) -> Unit,
    onIgnoreUpdate: (org.tsuyomi.shared.librarydomain.UnresolvedUpdate) -> Unit,
    onViewportChanged: (LibraryViewport) -> Unit,
    onViewportSettled: suspend (Int, Int) -> Unit,
    reorderEnabled: Boolean,
) {
    dragCoordinator.onLongPress = onLongPressBook
    dragCoordinator.onDrop = onDropBooks
    val filtered = state.projectedEntries()
    val structuralCollections = collections.filter { it.parentCollectionId == null }
    val rootItems = if (state.isRootProjection) {
        buildLibraryRootItems(
            entries = filtered,
            collections = structuralCollections,
            collectionCounts = state.collectionCounts,
            mirrors = state.mirrorShortcuts.filter { it.targetId == null },
            placements = state.rootNodePlacements,
            customOrder = state.sortMode == LibrarySortMode.CUSTOM,
        )
    } else {
        null
    }

    Box(Modifier.fillMaxSize().testTag("library-primary-page-${state.filter.name}").libraryDragOverlayHost(dragCoordinator)) {
        key(active) {
            LibraryBookSurface(
                entries = filtered,
                state = state,
                onOpenBook = onOpenBook,
                onLongPressBook = onLongPressBook,
                onToggleBookSelection = onToggleBookSelection,
                dragCoordinator = dragCoordinator,
                dragEnabled = active,
                reorderEnabled = active && reorderEnabled,
                coverState = coverState,
                onCoverVisibility = { entry, visible -> if (active) onCoverVisibility(entry, visible) },
                onIgnoreUpdate = onIgnoreUpdate,
                onViewportChanged = { viewport -> if (active) onViewportChanged(viewport) },
                onViewportSettled = { index, offset -> if (active) onViewportSettled(index, offset) },
                rootItems = rootItems,
                onOpenCollection = onOpenCollection,
                onOpenMirror = onOpenMirror,
                onLongPressCollection = onLongPressCollection,
                onToggleCollectionSelection = onToggleCollectionSelection,
                empty = {
                    StateView(
                        kind = TsuyomiStateKind.EMPTY,
                        title = when {
                            state.isRootProjection && state.updateFilter == LibraryUpdateFilter.UPDATES_ONLY -> "没有待处理更新"
                            state.filter == SystemLibraryFilter.CONTINUE -> "没有继续阅读的书籍"
                            state.filter == SystemLibraryFilter.READ_LATER -> "稍后再读为空"
                            else -> "书架为空"
                        },
                        message = when {
                            state.isRootProjection && state.updateFilter == LibraryUpdateFilter.UPDATES_ONLY -> "检查发现的新章节会显示在这里。"
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
        if (active) {
            LibraryDragVisualOverlay(
                coordinator = dragCoordinator,
                entries = state.entries,
                layout = state.layout,
                coverState = coverState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun libraryPrimaryPageFor(tab: SystemLibraryFilter): Int =
    SystemLibraryFilter.primaryTabs.indexOf(tab).coerceAtLeast(0)

internal fun libraryPrimaryTabAt(page: Int): SystemLibraryFilter? =
    SystemLibraryFilter.primaryTabs.getOrNull(page)

internal fun libraryPagerUserScrollEnabled(activeBookDrag: Boolean): Boolean = !activeBookDrag

