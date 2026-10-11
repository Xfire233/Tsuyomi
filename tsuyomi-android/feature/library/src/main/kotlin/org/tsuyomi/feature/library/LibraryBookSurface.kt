/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.components.PaginationBar
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.LocalTsuyomiPagerMaxContentHeight
import org.tsuyomi.core.ui.components.LocalTsuyomiPagerItemVisible
import org.tsuyomi.core.ui.components.TsuyomiIconButton
import org.tsuyomi.core.ui.components.TsuyomiToggleChip
import androidx.compose.foundation.layout.FlowRow
import org.tsuyomi.core.ui.components.TsuyomiCoverCardContent
import org.tsuyomi.core.ui.components.currentCoverCardLayout

import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.tsuyomiAnimateColorAsState
import org.tsuyomi.core.ui.theme.tsuyomiAnimateFloatAsState
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.components.coverCardHeight
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.core.ui.theme.activeAccent

enum class LibraryScrollDirection {
    FORWARD,
    BACKWARD,
    IDLE,
}

data class LibraryViewport(
    val headerVisible: Boolean,
    val atStart: Boolean,
    val direction: LibraryScrollDirection,
    val firstVisibleIndex: Int,
    val firstVisibleOffset: Int,
)

internal fun entryKey(entry: LibraryEntry): String = entry.book.identity.storageKey

internal fun libraryBookTestTag(identity: BookIdentity): String = "library-book-${identity.storageKey}"

@Composable
internal fun LibraryBookSurface(
    entries: List<LibraryEntry>,
    state: LibraryUiState,
    onOpenBook: (LibraryEntry) -> Unit,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
    onIgnoreUpdate: (org.tsuyomi.shared.librarydomain.UnresolvedUpdate) -> Unit = {},
    dragCoordinator: LibraryDragCoordinator,
    dragEnabled: Boolean,
    reorderEnabled: Boolean,
    manualReorderEnabled: Boolean = false,
    canRemove: Boolean = true,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    header: (@Composable () -> Unit)? = null,
    leadingItemCount: Int = 0,
    leadingItemKey: (Int) -> String = { "leading:$it" },
    leadingItemContent: @Composable (Int) -> Unit = {},
    onViewportChanged: ((LibraryViewport) -> Unit)? = null,
    onViewportSettled: suspend (Int, Int) -> Unit = { _, _ -> },
    rootItems: List<LibraryRootItem>? = null,
    onOpenCollection: (org.tsuyomi.shared.librarydomain.LibraryCollection) -> Unit = {},
    onOpenMirror: (LibraryMirrorShortcut) -> Unit = {},
    onLongPressCollection: (String) -> Unit = {},
    onToggleCollectionSelection: (String) -> Unit = {},
    viewportScopeKey: String = "library",
    onManualMove: (LibraryDragPayload, LibraryDropDestination) -> Unit = { _, _ -> },
    empty: @Composable () -> Unit,
    modifier: Modifier,
) {
    val eink = LocalDisplayEnvironment.current.effectiveProfile == org.tsuyomi.core.display.DisplayProfile.EINK
    val projectedItems = rootItems ?: entries.map { LibraryRootItem.Book(it) }
    val manualOrderBookOffset = if (state.isRootProjection) 0 else {
        projectedItems.indexOfFirst { it is LibraryRootItem.Book }.takeIf { it >= 0 } ?: projectedItems.size
    }
    val manualOrderItemCount = if (state.isRootProjection) projectedItems.size else projectedItems.size - manualOrderBookOffset
    if (projectedItems.isEmpty() && (!eink || leadingItemCount == 0)) {
        Column(modifier.fillMaxSize()) {
            header?.invoke()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { empty() }
        }
        return
    }
    val items = projectedItems
    val bookSelectionActive = state.selectionKind == LibrarySelectionKind.BOOK
    val nodeSelectionActive = state.selectionKind != null
    var selectedBookFirstManualOrderIndex = -1
    var selectedBookLastManualOrderIndex = -1
    if (manualReorderEnabled && bookSelectionActive && state.selectedBookIds.isNotEmpty()) {
        for (projectedIndex in manualOrderBookOffset..projectedItems.lastIndex) {
            val item = projectedItems[projectedIndex]
            if (item is LibraryRootItem.Book && item.entry.book.identity in state.selectedBookIds) {
                val manualOrderIndex = projectedIndex - manualOrderBookOffset
                if (selectedBookFirstManualOrderIndex < 0) selectedBookFirstManualOrderIndex = manualOrderIndex
                selectedBookLastManualOrderIndex = manualOrderIndex
            }
        }
    }
    val viewportWidth = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val initialAnchorKey = if (leadingItemCount > 0) {
        leadingItemKey(0)
    } else {
        projectedItems.getOrNull(state.firstVisibleIndex.coerceAtLeast(0))?.key
            ?: projectedItems.firstOrNull()?.key
    }
    val pagerState = rememberTsuyomiMeasuredPagerState(initialAnchorKey)
    if (eink) {
        val measuredItemCount = leadingItemCount + projectedItems.size
        val pageItemContent: @Composable (Int, Boolean) -> Unit = { itemIndex, grid ->
            if (itemIndex < leadingItemCount) leadingItemContent(itemIndex)
            else {
                val projectedIndex = itemIndex - leadingItemCount
                val item = projectedItems[projectedIndex]
                val manualOrderIndex = if (item is LibraryRootItem.Book && !state.isRootProjection) projectedIndex - manualOrderBookOffset else projectedIndex
                val movesSelectedBooks = item is LibraryRootItem.Book && bookSelectionActive && item.entry.book.identity in state.selectedBookIds
                val moveUpIndex = if (movesSelectedBooks && selectedBookFirstManualOrderIndex >= 0) selectedBookFirstManualOrderIndex else manualOrderIndex
                val moveDownIndex = if (movesSelectedBooks && selectedBookLastManualOrderIndex >= 0) selectedBookLastManualOrderIndex else manualOrderIndex
                val selected = when (item) {
                    is LibraryRootItem.Book -> item.entry.book.identity in state.selectedBookIds
                    is LibraryRootItem.Collection -> item.collection.collectionId in state.selectedCollectionIds
                    is LibraryRootItem.Mirror -> false
                }
                Column(Modifier.fillMaxWidth()) {
                    when (item) {
                        is LibraryRootItem.Book -> when (state.layout) {
                            LibraryLayout.GRID -> LibraryBookGridCard(
                                item.entry, state.updates[item.entry.book.identity], projectedIndex,
                                selected, bookSelectionActive, state.selectedBookIds, dragCoordinator,
                                false, canRemove, coverState, onCoverVisibility, onOpenBook,
                                onLongPressBook, onToggleBookSelection, onIgnoreUpdate,
                            )
                            LibraryLayout.LIST -> LibraryBookListRow(
                                item.entry, state.updates[item.entry.book.identity], projectedIndex,
                                selected, bookSelectionActive, state.selectedBookIds, dragCoordinator,
                                false, canRemove, coverState, onCoverVisibility, onOpenBook,
                                onLongPressBook, onToggleBookSelection, onIgnoreUpdate,
                            )
                            LibraryLayout.COMPACT -> LibraryCompactBookRow(
                                item.entry, state.updates[item.entry.book.identity], projectedIndex,
                                selected, bookSelectionActive, state.selectedBookIds, dragCoordinator,
                                false, canRemove, onOpenBook, onLongPressBook,
                                onToggleBookSelection, onIgnoreUpdate,
                            )
                        }
                        is LibraryRootItem.Collection, is LibraryRootItem.Mirror -> if (grid) {
                            LibraryRootNodeGridCard(
                                item, projectedIndex, selected, nodeSelectionActive, false,
                                dragCoordinator, onOpenCollection, onOpenMirror,
                                onLongPressCollection, onToggleCollectionSelection,
                            )
                        } else {
                            LibraryRootNodeListRow(
                                item, projectedIndex, state.layout == LibraryLayout.COMPACT,
                                selected, nodeSelectionActive, false, dragCoordinator,
                                onOpenCollection, onOpenMirror, onLongPressCollection,
                                onToggleCollectionSelection,
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs)) {
                        when (item) {
                            is LibraryRootItem.Book -> Unit
                            is LibraryRootItem.Collection -> TsuyomiToggleChip("选择", selected, if (selected) "已选择" else "未选择", {
                                if (nodeSelectionActive) onToggleCollectionSelection(item.collection.collectionId)
                                else onLongPressCollection(item.collection.collectionId)
                            }, modifier = Modifier.semantics {
                                contentDescription = "选择收藏夹${item.collection.title}"
                            })
                            is LibraryRootItem.Mirror -> Unit
                        }
                        LibraryManualOrderControls(
                            item, moveUpIndex, manualOrderItemCount, moveDownIndex,
                            state.isRootProjection,
                            if (item is LibraryRootItem.Book) bookSelectionActive else nodeSelectionActive,
                            state.selectedBookIds, manualReorderEnabled, onManualMove,
                        )
                    }
                    if (!grid) HorizontalDivider()
                }
            }
        }
        val itemKey: (Int) -> String = { index ->
            if (index < leadingItemCount) leadingItemKey(index)
            else projectedItems[index - leadingItemCount].key
        }
        val pageControls: @Composable (Int, Boolean, Boolean, () -> Unit, () -> Unit, Int?, () -> Unit, () -> Unit) -> Unit =
            { page, canGoPrevious, canGoNext, onPrevious, onNext, pageCount, onFirst, onLast ->
                PaginationBar(
                    page = page,
                    pageCount = pageCount,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    hasPreviousPage = canGoPrevious,
                    hasNextPage = canGoNext,
                    onFirst = onFirst,
                    onLast = onLast,
                    modifier = Modifier.testTag("library-eink-pagination")
                        .padding(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Xs),
                )
            }
        val measuredModifier = (LocalTsuyomiPagerMaxContentHeight.current?.let { modifier.heightIn(max = it) } ?: modifier)
            .fillMaxSize().testTag("library-book-surface")
        when (state.layout) {
            LibraryLayout.GRID -> BoxWithConstraints(
                modifier = measuredModifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
            ) {
                val columns = if (maxWidth >= 600.dp) {
                    ((maxWidth + 8.dp) / (150.dp + 8.dp)).toInt().coerceAtLeast(1)
                } else 3
                TsuyomiMeasuredPager(
                    itemCount = measuredItemCount,
                    itemKey = itemKey,
                    itemContent = { index -> pageItemContent(index, true) },
                    modifier = Modifier.fillMaxSize(),
                    state = pagerState,
                    columns = columns,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    pageControls = pageControls,
                )
            }
            LibraryLayout.LIST, LibraryLayout.COMPACT -> TsuyomiMeasuredPager(
                itemCount = measuredItemCount,
                itemKey = itemKey,
                itemContent = { index -> pageItemContent(index, false) },
                modifier = measuredModifier,
                state = pagerState,
                pageControls = pageControls,
            )
        }
        return
    }
    val viewportChanged = onViewportChanged
    val viewportSettled = onViewportSettled
    val libraryGapIndex = dragCoordinator.libraryInsertionIndex
        .takeIf { dragCoordinator.activePayload != null && it >= 0 }
        ?.coerceIn(0, items.size)
    val surfaceModifier = Modifier.fillMaxSize()
        .testTag("library-book-surface")
        .libraryContentDropTarget(dragCoordinator, reorderEnabled && !eink)
    val scrollKey = "$viewportScopeKey:${state.isRootProjection}:${state.filter}:${state.layout}:${if (eink) "EINK" else "STANDARD"}"
    androidx.compose.runtime.key(scrollKey) {
        when (state.layout) {
            LibraryLayout.GRID -> {
                val wide = viewportWidth >= 600.dp
                val gridState = rememberLazyGridState(
                    initialFirstVisibleItemIndex = state.firstVisibleIndex.coerceAtMost(items.lastIndex),
                    initialFirstVisibleItemScrollOffset = state.firstVisibleOffset,
                )
                ObserveLibraryViewport(
                    firstVisibleIndex = { gridState.firstVisibleItemIndex },
                    firstVisibleOffset = { gridState.firstVisibleItemScrollOffset },
                    isScrollInProgress = { gridState.isScrollInProgress },
                    hasHeader = header != null,
                    onChanged = viewportChanged,
                    onSettled = viewportSettled,
                )
                Box(modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = if (wide) GridCells.Adaptive(150.dp) else GridCells.Fixed(3),
                        state = gridState,
                        modifier = surfaceModifier,
                        contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        header?.let { headerContent ->
                            item(key = "library-header", span = { GridItemSpan(maxLineSpan) }) { headerContent() }
                        }
                        val visualCount = items.size + if (libraryGapIndex != null) 1 else 0
                        items(
                            count = visualCount,
                            key = { visualIndex ->
                                if (visualIndex == libraryGapIndex) "library-drop-gap"
                                else items[if (libraryGapIndex != null && visualIndex > libraryGapIndex) visualIndex - 1 else visualIndex].key
                            },
                        ) { visualIndex ->
                            if (visualIndex == libraryGapIndex) {
                                LibraryBookInsertionGap(LibraryLayout.GRID, Modifier.optionalAnimateItem(this))
                            } else {
                                val index = if (libraryGapIndex != null && visualIndex > libraryGapIndex) visualIndex - 1 else visualIndex
                                val projectedIndex = index
                                when (val item = items[index]) {
                                    is LibraryRootItem.Book -> Column(Modifier.fillMaxWidth().optionalAnimateItem(this)) {
                                        LibraryBookGridCard(
                                            entry = item.entry,
                                            update = state.updates[item.entry.book.identity],
                                            index = projectedIndex,
                                            selected = item.entry.book.identity in state.selectedBookIds,
                                            selectionActive = bookSelectionActive,
                                            selectedBookIds = state.selectedBookIds,
                                            dragCoordinator = dragCoordinator,
                                            dragEnabled = dragEnabled,
                                            canRemove = canRemove,
                                            coverState = coverState,
                                            onCoverVisibility = onCoverVisibility,
                                            onOpenBook = onOpenBook,
                                            onLongPressBook = onLongPressBook,
                                            onToggleBookSelection = onToggleBookSelection,
                                            onIgnoreUpdate = onIgnoreUpdate,
                                        )
                                    }
                                    is LibraryRootItem.Collection, is LibraryRootItem.Mirror -> Column(
                                        Modifier.fillMaxWidth().optionalAnimateItem(this),
                                    ) {
                                        LibraryRootNodeGridCard(
                                            item = item,
                                            index = projectedIndex,
                                            selected = item is LibraryRootItem.Collection &&
                                                item.collection.collectionId in state.selectedCollectionIds,
                                            selectionActive = nodeSelectionActive,
                                            reorderEnabled = reorderEnabled,
                                            dragCoordinator = dragCoordinator,
                                            onOpenCollection = onOpenCollection,
                                            onOpenMirror = onOpenMirror,
                                            onLongPressCollection = onLongPressCollection,
                                            onToggleCollectionSelection = onToggleCollectionSelection,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            LibraryLayout.LIST, LibraryLayout.COMPACT -> {
                val compact = state.layout == LibraryLayout.COMPACT
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = state.firstVisibleIndex.coerceAtMost(items.lastIndex),
                    initialFirstVisibleItemScrollOffset = state.firstVisibleOffset,
                )
                ObserveLibraryViewport(
                    firstVisibleIndex = { listState.firstVisibleItemIndex },
                    firstVisibleOffset = { listState.firstVisibleItemScrollOffset },
                    isScrollInProgress = { listState.isScrollInProgress },
                    hasHeader = header != null,
                    onChanged = viewportChanged,
                    onSettled = viewportSettled,
                )
                Box(modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = surfaceModifier,
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        header?.let { headerContent -> item(key = "library-header") { headerContent() } }
                        val visualCount = items.size + if (libraryGapIndex != null) 1 else 0
                        items(
                            count = visualCount,
                            key = { visualIndex ->
                                if (visualIndex == libraryGapIndex) "library-drop-gap"
                                else items[if (libraryGapIndex != null && visualIndex > libraryGapIndex) visualIndex - 1 else visualIndex].key
                            },
                        ) { visualIndex ->
                            if (visualIndex == libraryGapIndex) {
                                LibraryBookInsertionGap(state.layout, Modifier.optionalAnimateItem(this))
                            } else {
                                val index = if (libraryGapIndex != null && visualIndex > libraryGapIndex) visualIndex - 1 else visualIndex
                                Column(Modifier.fillMaxWidth().optionalAnimateItem(this)) {
                                    val projectedIndex = index
                                    val item = items[index]
                                    when (item) {
                                        is LibraryRootItem.Book -> if (compact) {
                                            LibraryCompactBookRow(
                                                entry = item.entry,
                                                update = state.updates[item.entry.book.identity],
                                                index = projectedIndex,
                                                selected = item.entry.book.identity in state.selectedBookIds,
                                                selectionActive = bookSelectionActive,
                                                selectedBookIds = state.selectedBookIds,
                                                dragCoordinator = dragCoordinator,
                                                dragEnabled = dragEnabled,
                                                canRemove = canRemove,
                                                onOpenBook = onOpenBook,
                                                onLongPressBook = onLongPressBook,
                                                onToggleBookSelection = onToggleBookSelection,
                                                onIgnoreUpdate = onIgnoreUpdate,
                                            )
                                        } else {
                                            LibraryBookListRow(
                                                entry = item.entry,
                                                update = state.updates[item.entry.book.identity],
                                                index = projectedIndex,
                                                selected = item.entry.book.identity in state.selectedBookIds,
                                                selectionActive = bookSelectionActive,
                                                selectedBookIds = state.selectedBookIds,
                                                dragCoordinator = dragCoordinator,
                                                dragEnabled = dragEnabled,
                                                canRemove = canRemove,
                                                coverState = coverState,
                                                onCoverVisibility = onCoverVisibility,
                                                onOpenBook = onOpenBook,
                                                onLongPressBook = onLongPressBook,
                                                onToggleBookSelection = onToggleBookSelection,
                                                onIgnoreUpdate = onIgnoreUpdate,
                                            )
                                        }
                                        is LibraryRootItem.Collection, is LibraryRootItem.Mirror -> LibraryRootNodeListRow(
                                            item = item,
                                            index = projectedIndex,
                                            compact = compact,
                                            selected = item is LibraryRootItem.Collection &&
                                                item.collection.collectionId in state.selectedCollectionIds,
                                            selectionActive = nodeSelectionActive,
                                            reorderEnabled = reorderEnabled,
                                            dragCoordinator = dragCoordinator,
                                            onOpenCollection = onOpenCollection,
                                            onOpenMirror = onOpenMirror,
                                            onLongPressCollection = onLongPressCollection,
                                            onToggleCollectionSelection = onToggleCollectionSelection,
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                    TsuyomiAdaptiveListFab(
                        state = listState,
                        topLabel = "顶部",
                        endLabel = "末尾",
                        modifier = Modifier.align(Alignment.BottomEnd).padding(TsuyomiSpacing.Md),
                    )
                }
            }
        }
    }
}


@Composable
private fun LibraryManualOrderControls(
    item: LibraryRootItem,
    moveUpIndex: Int,
    itemCount: Int,
    moveDownIndex: Int = moveUpIndex,
    isRootProjection: Boolean,
    selectionActive: Boolean,
    selectedBookIds: Set<BookIdentity>,
    enabled: Boolean,
    onMove: (LibraryDragPayload, LibraryDropDestination) -> Unit,
) {
    if (!enabled || itemCount < 2 || (!isRootProjection && item !is LibraryRootItem.Book)) return
    val title = when (item) {
        is LibraryRootItem.Book -> item.entry.book.title
        is LibraryRootItem.Collection -> item.collection.title
        is LibraryRootItem.Mirror -> item.mirror.label
    }
    val moveUpDescription = stringResource(R.string.library_eink_move_up_accessibility, title)
    val moveDownDescription = stringResource(R.string.library_eink_move_down_accessibility, title)
    val payload = when (item) {
        is LibraryRootItem.Book -> if (item.entry.localMembership) {
            val identity = item.entry.book.identity
            LibraryDragPayload.Books(
                if (selectionActive && identity in selectedBookIds) selectedBookIds else setOf(identity),
            )
        } else {
            null
        }
        is LibraryRootItem.Collection, is LibraryRootItem.Mirror -> LibraryDragPayload.Shortcut(item.key)
    } ?: return
    val move: (Int) -> Unit = { delta ->
        val target = if (delta > 0) moveDownIndex + delta + 1 else moveUpIndex + delta
        if (target in 0..itemCount) {
            val destination = when (item) {
                is LibraryRootItem.Book -> LibraryDropDestination.Library(target)
                is LibraryRootItem.Collection, is LibraryRootItem.Mirror -> LibraryDropDestination.Root(target)
            }
            onMove(payload, destination)
        }
    }
    TsuyomiIconButton(
        imageVector = TsuyomiIcons.ToTop,
        contentDescription = moveUpDescription,
        onClick = { move(-1) },
        modifier = Modifier.testTag("library-eink-move-up-${item.key}"),
        enabled = moveUpIndex > 0,
    )
    TsuyomiIconButton(
        imageVector = TsuyomiIcons.ToBottom,
        contentDescription = moveDownDescription,
        onClick = { move(1) },
        modifier = Modifier.testTag("library-eink-move-down-${item.key}"),
        enabled = moveDownIndex < itemCount - 1,
    )
}

@Composable
internal fun LibraryBookInsertionGap(layout: LibraryLayout, modifier: Modifier = Modifier) = BoxWithConstraints(modifier.fillMaxWidth()) {
    val gapModifier = when (layout) {
        LibraryLayout.GRID -> Modifier.fillMaxWidth().height(coverCardHeight(maxWidth))

        LibraryLayout.LIST -> Modifier.fillMaxWidth().height(TsuyomiSpacing.Md)
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs)
        LibraryLayout.COMPACT -> Modifier.fillMaxWidth().height(12.dp)
            .padding(horizontal = 16.dp, vertical = 3.dp)
    }
    Surface(
        modifier = gapModifier.clearAndSetSemantics { }.testTag("library-book-insertion-gap"),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {}
}

@Composable
internal fun ObserveLibraryViewport(
    firstVisibleIndex: () -> Int,
    firstVisibleOffset: () -> Int,
    isScrollInProgress: () -> Boolean,
    hasHeader: Boolean,
    onChanged: ((LibraryViewport) -> Unit)?,
    onSettled: suspend (Int, Int) -> Unit,
) {
    val currentOnChanged by rememberUpdatedState(onChanged)
    val currentOnSettled by rememberUpdatedState(onSettled)
    LaunchedEffect(firstVisibleIndex, firstVisibleOffset, isScrollInProgress, hasHeader) {
        var previousIndex = firstVisibleIndex()
        var previousOffset = firstVisibleOffset()
        snapshotFlow { Triple(firstVisibleIndex(), firstVisibleOffset(), isScrollInProgress()) }
            .collect { (index, offset, scrolling) ->
                val direction = when {
                    index > previousIndex || (index == previousIndex && offset > previousOffset) -> LibraryScrollDirection.FORWARD
                    index < previousIndex || (index == previousIndex && offset < previousOffset) -> LibraryScrollDirection.BACKWARD
                    else -> LibraryScrollDirection.IDLE
                }
                currentOnChanged?.invoke(
                    LibraryViewport(
                        headerVisible = hasHeader && index == 0,
                        atStart = index == 0 && offset == 0,
                        direction = direction,
                        firstVisibleIndex = index,
                        firstVisibleOffset = offset,
                    ),
                )
                if (!scrolling) currentOnSettled(index, offset)
                previousIndex = index
                previousOffset = offset
            }
    }
}

internal fun LibraryEntry.readingStatusLabel(): String? = progress?.let { readingProgress ->
    val wholeBookProgress = readingProgress.locator.bookProgress?.takeIf(Double::isFinite)
        ?: return@let "阅读中"
    val bounded = wholeBookProgress.coerceIn(0.0, 1.0)
    if (bounded >= 1.0) "已读完" else "读至 ${(bounded * 100).toInt()}%"
}

internal fun LibraryEntry.libraryStatusLabel(): String = readingStatusLabel()
    ?: when {
        readLater -> "稍后再读"
        book.identity is BookIdentity.Remote && !sourceAvailable -> "来源未安装"
        book.identity is BookIdentity.Local && book.localPublication?.originalAvailable == false -> "原书文件缺失，可重新关联"
        else -> "未开始"
    }

@Composable
internal fun LibraryBookGridCard(
    entry: LibraryEntry,
    update: org.tsuyomi.shared.librarydomain.UnresolvedUpdate?,
    index: Int,
    selected: Boolean,
    selectionActive: Boolean,
    selectedBookIds: Set<BookIdentity>,
    dragCoordinator: LibraryDragCoordinator,
    dragEnabled: Boolean,
    canRemove: Boolean,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
    onIgnoreUpdate: (org.tsuyomi.shared.librarydomain.UnresolvedUpdate) -> Unit,
) {
    val identity = entry.book.identity
    val wide = currentCoverCardLayout().usesWideTitleLane
    val status = entry.libraryStatusLabel()
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == org.tsuyomi.core.display.DisplayProfile.EINK
    val targeted = dragCoordinator.bookTargetIdentity == identity
    val targetScale = tsuyomiAnimateFloatAsState(
        target = if (targeted) 1.025f else 1f,
        instant = LocalDisplayEnvironment.current.instantMotion,
        label = "libraryBookTargetScale",
    )
    val targetContainer = tsuyomiAnimateColorAsState(
        target = if (selected || targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        instant = LocalDisplayEnvironment.current.instantMotion,
        label = "libraryBookTargetContainer",
    )
    val targetOutline = tsuyomiAnimateColorAsState(
        target = if (selected || targeted) MaterialTheme.colorScheme.primary else Color.Transparent,
        instant = LocalDisplayEnvironment.current.instantMotion,
        label = "libraryBookTargetOutline",
    )
    Card(
        modifier = Modifier.fillMaxWidth()
            .testTag(libraryBookTestTag(identity))
            .libraryBookDropTarget(dragCoordinator, identity, index)
            .libraryBookGestures(
                identity = identity,
                coordinator = dragCoordinator,
                selected = selected,
                selectionActive = selectionActive,
                selectedBookIds = selectedBookIds,
                dragEnabled = dragEnabled && entry.localMembership,
                longPressEnabled = entry.localMembership,
                canRemove = canRemove && entry.localMembership,
                reorderSource = true,
                scrollOrientation = Orientation.Vertical,
                onTap = {
                    if (selectionActive && entry.localMembership) onToggleBookSelection(identity) else onOpenBook(entry)
                },
                onLongPress = { if (entry.localMembership) onLongPressBook(identity) },
            )
            .graphicsLayer {
                scaleX = targetScale
                scaleY = targetScale
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = targetContainer),
        border = BorderStroke(2.dp, targetOutline),
    ) {
        TsuyomiCoverCardContent(
            title = entry.book.title,
            supportingText = status,
            cover = {
                ProductionBookCover(entry, coverState, onCoverVisibility, Modifier.fillMaxSize())
            },
            metadataModifier = Modifier.testTag("library-book-metadata-${identity.storageKey}"),
            artworkOverlay = {
                update?.let { unresolved ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart)
                            .padding(horizontal = TsuyomiSpacing.Xs, vertical = if (wide) 0.dp else TsuyomiSpacing.Xs),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            "+${unresolved.newChapterIds.size}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (selected) {
                    Surface(
                        modifier = Modifier.align(if (wide) Alignment.BottomStart else Alignment.TopEnd)
                            .padding(6.dp).size(32.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (eInk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                TsuyomiIcons.Selected,
                                contentDescription = "已选择",
                                tint = if (eInk) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.activeAccent,
                            )
                        }
                    }
                } else {
                    update?.let { unresolved ->
                        LibraryUpdateActionButton(
                            unresolved,
                            onIgnoreUpdate,
                            if (wide) Modifier.align(Alignment.BottomStart)
                            else Modifier.align(Alignment.TopEnd).padding(TsuyomiSpacing.Xs),
                        )
                    }
                }
            },
        )

    }
}

@Composable
internal fun LibraryBookListRow(
    entry: LibraryEntry,
    update: org.tsuyomi.shared.librarydomain.UnresolvedUpdate?,
    index: Int,
    selected: Boolean,
    selectionActive: Boolean,
    selectedBookIds: Set<BookIdentity>,
    dragCoordinator: LibraryDragCoordinator,
    dragEnabled: Boolean,
    canRemove: Boolean,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
    onIgnoreUpdate: (org.tsuyomi.shared.librarydomain.UnresolvedUpdate) -> Unit,
) {
    val identity = entry.book.identity
    val targeted = dragCoordinator.bookTargetIdentity == identity
    val targetContainer = tsuyomiAnimateColorAsState(
        target = if (selected || targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        instant = LocalDisplayEnvironment.current.instantMotion,
        label = "libraryBookListTargetContainer",
    )
    ListItem(
        headlineContent = { Text(entry.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        overlineContent = entry.book.authors.joinToString("、").takeIf(String::isNotBlank)?.let { authors ->
            { Text(authors, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        supportingContent = { Text(entry.libraryStatusLabel()) },
        leadingContent = {
            ProductionBookCover(
                entry,
                coverState,
                onCoverVisibility,
                Modifier.height(112.dp).aspectRatio(currentCoverCardLayout().leadingArtworkAspectRatio),
            )
        },
        trailingContent = when {
            selected -> ({ Icon(TsuyomiIcons.Selected, contentDescription = "已选择", tint = MaterialTheme.colorScheme.activeAccent) })
            update != null -> ({ LibraryUpdateActionButton(update, onIgnoreUpdate) })
            else -> null
        },
        modifier = Modifier.fillMaxWidth()
            .testTag(libraryBookTestTag(identity))
            .libraryBookDropTarget(dragCoordinator, identity, index)
            .libraryBookGestures(
                identity = identity,
                coordinator = dragCoordinator,
                selected = selected,
                selectionActive = selectionActive,
                selectedBookIds = selectedBookIds,
                dragEnabled = dragEnabled && entry.localMembership,
                longPressEnabled = entry.localMembership,
                canRemove = canRemove && entry.localMembership,
                reorderSource = true,
                scrollOrientation = Orientation.Vertical,
                onTap = {
                    if (selectionActive && entry.localMembership) onToggleBookSelection(identity) else onOpenBook(entry)
                },
                onLongPress = { if (entry.localMembership) onLongPressBook(identity) },
            ),
        colors = ListItemDefaults.colors(containerColor = targetContainer),
    )
}

@Composable
internal fun LibraryCompactBookRow(
    entry: LibraryEntry,
    update: org.tsuyomi.shared.librarydomain.UnresolvedUpdate?,
    index: Int,
    selected: Boolean,
    selectionActive: Boolean,
    selectedBookIds: Set<BookIdentity>,
    dragCoordinator: LibraryDragCoordinator,
    dragEnabled: Boolean,
    canRemove: Boolean,
    onOpenBook: (LibraryEntry) -> Unit,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
    onIgnoreUpdate: (org.tsuyomi.shared.librarydomain.UnresolvedUpdate) -> Unit,
) {
    val identity = entry.book.identity
    val targeted = dragCoordinator.bookTargetIdentity == identity
    val targetContainer = tsuyomiAnimateColorAsState(
        target = if (selected || targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        instant = LocalDisplayEnvironment.current.instantMotion,
        label = "libraryBookCompactTargetContainer",
    )
    ListItem(
        headlineContent = { Text(entry.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val supporting = entry.readingStatusLabel() ?: entry.book.authors.joinToString("、")
            if (supporting.isNotBlank()) Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = when {
            selected -> ({ Icon(TsuyomiIcons.Selected, contentDescription = "已选择", tint = MaterialTheme.colorScheme.activeAccent) })
            update != null -> ({ LibraryUpdateActionButton(update, onIgnoreUpdate) })
            else -> entry.rating?.let { rating -> { Text("★ $rating") } }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .testTag(libraryBookTestTag(identity))
            .libraryBookDropTarget(dragCoordinator, identity, index)
            .libraryBookGestures(
                identity = identity,
                coordinator = dragCoordinator,
                selected = selected,
                selectionActive = selectionActive,
                selectedBookIds = selectedBookIds,
                dragEnabled = dragEnabled && entry.localMembership,
                longPressEnabled = entry.localMembership,
                canRemove = canRemove && entry.localMembership,
                reorderSource = true,
                scrollOrientation = Orientation.Vertical,
                onTap = {
                    if (selectionActive && entry.localMembership) onToggleBookSelection(identity) else onOpenBook(entry)
                },
                onLongPress = { if (entry.localMembership) onLongPressBook(identity) },
            ),
        colors = ListItemDefaults.colors(containerColor = targetContainer),
    )
}

@Composable
internal fun ProductionBookCover(
    entry: LibraryEntry,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    modifier: Modifier,
) {
    val visible = LocalTsuyomiPagerItemVisible.current
    DisposableEffect(entryKey(entry), visible) {
        if (visible) onCoverVisibility(entry, true)
        onDispose { if (visible) onCoverVisibility(entry, false) }
    }
    val unresolved = entry.reconciliation == org.tsuyomi.shared.librarydomain.RemoteReconciliationState.UNRESOLVED
    val unresolvedDescription = if (unresolved) stringResource(
        when (entry.reconciliationOperation?.uppercase()) {
            "ADD" -> R.string.remote_library_pending_add_description
            "MOVE" -> R.string.remote_library_pending_move_description
            "REMOVE" -> R.string.remote_library_pending_remove_description
            else -> R.string.remote_library_pending_other_description
        },
    ) else null
    CoverImage(
        state = coverState(entry),
        modifier = modifier,
        unresolvedBadge = unresolved,
        unresolvedDescription = unresolvedDescription,
    )
}

