/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.shared.model.BookIdentity

@Composable
internal fun AtlasLibraryPresentation(
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
    coverState: (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
    onLongPressCollection: (String) -> Unit,
    onShortcutLockedChanged: (Boolean) -> Unit,
    onToggleCollectionSelection: (String) -> Unit,
    onDropBooks: (LibraryDragPayload, LibraryDropDestination) -> Unit,
    reorderEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val dragCoordinator = remember { LibraryDragCoordinator() }
    dragCoordinator.onLongPress = onLongPressBook
    dragCoordinator.onDrop = onDropBooks
    val filtered = state.projectedEntries()
    if (!showNavigationNodes) {
        Box(modifier.fillMaxSize().libraryDragOverlayHost(dragCoordinator)) {
            AtlasBookSurface(
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
                empty = {
                    StateView(
                        kind = TsuyomiStateKind.EMPTY,
                        title = "没有匹配的书籍",
                        message = "此书架当前没有书籍。",
                        actionLabel = "刷新",
                        onAction = onRetry,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
            LibraryDragVisualOverlay(
                coordinator = dragCoordinator,
                entries = state.entries,
                shortcuts = emptyList(),
                layout = state.layout,
                coverState = coverState,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AtlasLibrarySortDialog(state, onDismissSort, onSelectSort, onSelectSortDirection)
        return
    }

    val shortcutOrder = state.shortcutOrder
    val shortcutLocked = state.shortcutLocked
    val shortcuts = buildShortcuts(state.entries, collections, shortcutOrder)
    val orderedShortcuts = orderShortcuts(shortcuts, shortcutOrder)
    var showAllShortcuts by rememberSaveable { mutableStateOf(false) }
    var shortcutPresentation by rememberSaveable { mutableStateOf(ShortcutShelfPresentation.INLINE) }
    var libraryAtStart by rememberSaveable { mutableStateOf(true) }
    var keepUnlockedShelfPinned by rememberSaveable { mutableStateOf(false) }
    val instantMotion = LocalDisplayEnvironment.current.instantMotion

    val shortcutShelf: @Composable () -> Unit = {
        ShortcutShelf(
            shortcuts = orderedShortcuts,
            locked = shortcutLocked,
            onLocked = { locked ->
                if (locked) {
                    keepUnlockedShelfPinned = false
                } else {
                    keepUnlockedShelfPinned = !libraryAtStart
                    shortcutPresentation = if (libraryAtStart) {
                        ShortcutShelfPresentation.INLINE
                    } else {
                        ShortcutShelfPresentation.OVERLAY_EXPANDED
                    }
                }
                onShortcutLockedChanged(locked)
            },
            onCreate = onCreateCollection,
            onViewAll = { showAllShortcuts = true },
            onOpen = { openShortcut(it, onOpenSystemNode, onOpenCollection, onOpenBook) },
            selectionKind = state.selectionKind,
            selectedBookIds = state.selectedBookIds,
            selectedCollectionIds = state.selectedCollectionIds,
            onLongPressBook = onLongPressBook,
            onToggleBookSelection = onToggleBookSelection,
            onLongPressCollection = onLongPressCollection,
            onToggleCollectionSelection = onToggleCollectionSelection,
            dragCoordinator = dragCoordinator,
            coverState = coverState,
        )
    }

    if (showAllShortcuts) {
        Box(modifier.fillMaxSize().libraryDragOverlayHost(dragCoordinator)) {
            ShortcutAllPage(
                shortcuts = orderedShortcuts,
                locked = shortcutLocked,
                onLocked = onShortcutLockedChanged,
                onCreate = onCreateCollection,
                onDismiss = { showAllShortcuts = false },
                onOpen = { shortcut ->
                    showAllShortcuts = false
                    openShortcut(shortcut, onOpenSystemNode, onOpenCollection, onOpenBook)
                },
                dragCoordinator = dragCoordinator,
                selectionKind = state.selectionKind,
                selectedBookIds = state.selectedBookIds,
                selectedCollectionIds = state.selectedCollectionIds,
                onLongPressBook = onLongPressBook,
                onToggleBookSelection = onToggleBookSelection,
                onLongPressCollection = onLongPressCollection,
                onToggleCollectionSelection = onToggleCollectionSelection,
                coverState = coverState,
                modifier = Modifier.fillMaxSize(),
            )
            LibraryDragVisualOverlay(
                coordinator = dragCoordinator,
                entries = state.entries,
                shortcuts = orderedShortcuts,
                layout = state.layout,
                coverState = coverState,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    Box(modifier.fillMaxSize().libraryDragOverlayHost(dragCoordinator)) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = shortcutLocked || keepUnlockedShelfPinned,
                enter = expandVertically(
                    animationSpec = if (instantMotion) snap() else tween(
                        TsuyomiMotion.EXPAND_DURATION_MS,
                        easing = TsuyomiMotion.Easing,
                    ),
                    expandFrom = Alignment.Top,
                ) + fadeIn(if (instantMotion) snap() else tween(TsuyomiMotion.EXPAND_DURATION_MS)),
                exit = shrinkVertically(
                    animationSpec = if (instantMotion) snap() else tween(
                        TsuyomiMotion.EXPAND_DURATION_MS,
                        easing = TsuyomiMotion.Easing,
                    ),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(if (instantMotion) snap() else tween(TsuyomiMotion.EXPAND_DURATION_MS)),
            ) {
                shortcutShelf()
            }
            AtlasBookSurface(
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
                header = if (shortcutLocked || keepUnlockedShelfPinned) null else shortcutShelf,
                onViewportChanged = { viewport ->
                    libraryAtStart = viewport.atStart
                    if (keepUnlockedShelfPinned) {
                        if (viewport.direction == LibraryScrollDirection.FORWARD) {
                            keepUnlockedShelfPinned = false
                            shortcutPresentation = ShortcutShelfPresentation.COLLAPSED
                        }
                    } else if (!shortcutLocked) {
                        shortcutPresentation = when {
                            viewport.headerVisible -> ShortcutShelfPresentation.INLINE
                            viewport.direction == LibraryScrollDirection.FORWARD -> ShortcutShelfPresentation.COLLAPSED
                            viewport.direction == LibraryScrollDirection.BACKWARD &&
                                shortcutPresentation == ShortcutShelfPresentation.COLLAPSED -> {
                                ShortcutShelfPresentation.OVERLAY_EXPANDED
                            }
                            else -> shortcutPresentation
                        }
                    }
                },
                empty = {
                    StateView(
                        kind = TsuyomiStateKind.EMPTY,
                        title = "书架为空",
                        message = "从浏览或搜索中加入书籍。",
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (!shortcutLocked && !keepUnlockedShelfPinned &&
            shortcutPresentation != ShortcutShelfPresentation.INLINE
        ) {
            ShortcutShelfOverlay(
                expanded = shortcutPresentation == ShortcutShelfPresentation.OVERLAY_EXPANDED,
                coordinator = dragCoordinator,
                onExpand = { shortcutPresentation = ShortcutShelfPresentation.OVERLAY_EXPANDED },
                shelf = shortcutShelf,
            )
        }
        LibraryDragVisualOverlay(
            coordinator = dragCoordinator,
            entries = state.entries,
            shortcuts = orderedShortcuts,
            layout = state.layout,
            coverState = coverState,
            modifier = Modifier.fillMaxSize(),
        )
    }
    AtlasLibrarySortDialog(state, onDismissSort, onSelectSort, onSelectSortDirection)
}
