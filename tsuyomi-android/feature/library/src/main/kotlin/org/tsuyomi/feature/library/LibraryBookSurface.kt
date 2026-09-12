/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.shared.model.BookIdentity

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

internal fun entryKey(entry: LibraryEntry): String =
    "${entry.book.identity.sourceId}\u0000${entry.book.identity.remoteBookId}"

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
    canRemove: Boolean = true,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    header: (@Composable () -> Unit)? = null,
    onViewportChanged: ((LibraryViewport) -> Unit)? = null,
    onViewportSettled: suspend (Int, Int) -> Unit = { _, _ -> },
    rootItems: List<LibraryRootItem>? = null,
    onOpenCollection: (org.tsuyomi.core.database.LibraryCollection) -> Unit = {},
    onOpenMirror: (LibraryMirrorShortcut) -> Unit = {},
    onLongPressCollection: (String) -> Unit = {},
    onToggleCollectionSelection: (String) -> Unit = {},
    empty: @Composable () -> Unit,
    modifier: Modifier,
) {
    val items = rootItems ?: entries.map { LibraryRootItem.Book(it) }
    if (items.isEmpty()) {
        Column(modifier.fillMaxSize()) {
            header?.invoke()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { empty() }
        }
        return
    }
    val bookSelectionActive = state.selectionKind == LibrarySelectionKind.BOOK
    val nodeSelectionActive = state.selectionKind != null
    val libraryGapIndex = dragCoordinator.libraryInsertionIndex
        .takeIf { dragCoordinator.activePayload != null && it >= 0 }
        ?.coerceIn(0, items.size)
    val surfaceModifier = Modifier.fillMaxSize()
        .testTag("library-book-surface")
        .libraryContentDropTarget(dragCoordinator, reorderEnabled)
    val scrollKey = "${state.isRootProjection}:${state.filter}:${state.layout}"
    androidx.compose.runtime.key(scrollKey) {
        when (state.layout) {
            LibraryLayout.GRID -> {
                val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp }
                val gridState = rememberLazyGridState(
                    initialFirstVisibleItemIndex = state.firstVisibleIndex.coerceAtMost(items.lastIndex),
                    initialFirstVisibleItemScrollOffset = state.firstVisibleOffset,
                )
                ObserveLibraryViewport(
                    firstVisibleIndex = { gridState.firstVisibleItemIndex },
                    firstVisibleOffset = { gridState.firstVisibleItemScrollOffset },
                    isScrollInProgress = { gridState.isScrollInProgress },
                    hasHeader = header != null,
                    onChanged = onViewportChanged,
                    onSettled = onViewportSettled,
                )
                Box(modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = if (wide) GridCells.Adaptive(120.dp) else GridCells.Fixed(3),
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
                                when (val item = items[index]) {
                                    is LibraryRootItem.Book -> Box(Modifier.fillMaxWidth().optionalAnimateItem(this)) {
                                        LibraryBookGridCard(
                                            entry = item.entry,
                                            update = state.updates[item.entry.book.identity],
                                            index = index,
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
                                    is LibraryRootItem.Collection, is LibraryRootItem.Mirror -> Box(
                                        Modifier.fillMaxWidth().optionalAnimateItem(this),
                                    ) {
                                        LibraryRootNodeGridCard(
                                            item = item,
                                            index = index,
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
                    TsuyomiAdaptiveListFab(
                        state = gridState,
                        topLabel = "顶部",
                        endLabel = "末尾",
                        modifier = Modifier.align(Alignment.BottomEnd).padding(TsuyomiSpacing.Md),
                    )
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
                    onChanged = onViewportChanged,
                    onSettled = onViewportSettled,
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
                                    when (val item = items[index]) {
                                        is LibraryRootItem.Book -> if (compact) {
                                            LibraryCompactBookRow(
                                                entry = item.entry,
                                                update = state.updates[item.entry.book.identity],
                                                index = index,
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
                                                index = index,
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
                                            index = index,
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
internal fun LibraryBookInsertionGap(layout: LibraryLayout, modifier: Modifier = Modifier) {
    val gapModifier = when (layout) {
        LibraryLayout.GRID -> modifier.fillMaxWidth().aspectRatio(3f / 4f)
        LibraryLayout.LIST -> modifier.fillMaxWidth().height(TsuyomiSpacing.Md)
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs)
        LibraryLayout.COMPACT -> modifier.fillMaxWidth().height(12.dp)
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
    val status = entry.progress?.locator?.bookProgress?.let { "读至 ${(it * 100).toInt()}%" }
        ?: update?.let { "新增 ${it.newChapterIds.size} 章" }
        ?: when {
            entry.readLater -> "稍后再读"
            !entry.sourceAvailable -> "来源未安装"
            else -> "未开始"
        }
    val targeted = dragCoordinator.bookTargetIdentity == identity
    val instant = LocalDisplayEnvironment.current.instantMotion
    val targetScale by animateFloatAsState(
        targetValue = if (targeted) 1.025f else 1f,
        animationSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS, easing = TsuyomiMotion.Easing),
        label = "libraryBookTargetScale",
    )
    val targetContainer by animateColorAsState(
        targetValue = if (selected || targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS, easing = TsuyomiMotion.Easing),
        label = "libraryBookTargetContainer",
    )
    val targetOutline by animateColorAsState(
        targetValue = if (selected || targeted) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS, easing = TsuyomiMotion.Easing),
        label = "libraryBookTargetOutline",
    )
    Card(
        modifier = Modifier.fillMaxWidth()
            .testTag("library-book-${identity.sourceId}-${identity.remoteBookId}")
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
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            ProductionBookCover(entry, coverState, onCoverVisibility, Modifier.fillMaxSize())
            update?.let { unresolved ->
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(TsuyomiSpacing.Xs),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text("+${unresolved.newChapterIds.size}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(TsuyomiIcons.Selected, contentDescription = "已选择", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
                update?.let { unresolved ->
                    LibraryUpdateActionButton(unresolved, onIgnoreUpdate, Modifier.align(Alignment.TopEnd).padding(TsuyomiSpacing.Xs))
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                    .padding(start = TsuyomiSpacing.Sm, top = 28.dp, end = TsuyomiSpacing.Sm, bottom = 6.dp)
                    .testTag("library-book-metadata-${identity.sourceId}-${identity.remoteBookId}"),
            ) {
                Text(entry.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }
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
    val instant = LocalDisplayEnvironment.current.instantMotion
    val targetContainer by animateColorAsState(
        targetValue = if (selected || targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS, easing = TsuyomiMotion.Easing),
        label = "libraryBookListTargetContainer",
    )
    ListItem(
        headlineContent = { Text(entry.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        overlineContent = entry.book.authors.joinToString("、").takeIf(String::isNotBlank)?.let { authors ->
            { Text(authors, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        supportingContent = {
            Text(update?.let { "新增 ${it.newChapterIds.size} 章" } ?: entry.progress?.locator?.bookProgress?.let { "读至 ${(it * 100).toInt()}%" } ?: if (entry.readLater) "稍后再读" else "未开始")
        },
        leadingContent = {
            ProductionBookCover(entry, coverState, onCoverVisibility, Modifier.size(width = 84.dp, height = 112.dp))
        },
        trailingContent = when {
            selected -> ({ Icon(TsuyomiIcons.Selected, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary) })
            update != null -> ({ LibraryUpdateActionButton(update, onIgnoreUpdate) })
            else -> null
        },
        modifier = Modifier.fillMaxWidth()
            .testTag("library-book-${identity.sourceId}-${identity.remoteBookId}")
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
    val instant = LocalDisplayEnvironment.current.instantMotion
    val targetContainer by animateColorAsState(
        targetValue = if (selected || targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS, easing = TsuyomiMotion.Easing),
        label = "libraryBookCompactTargetContainer",
    )
    ListItem(
        headlineContent = { Text(entry.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val supporting = update?.let { "新增 ${it.newChapterIds.size} 章" }
                ?: entry.progress?.locator?.bookProgress?.let { "读至 ${(it * 100).toInt()}%" }
                ?: entry.book.authors.joinToString("、")
            if (supporting.isNotBlank()) Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = when {
            selected -> ({ Icon(TsuyomiIcons.Selected, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary) })
            update != null -> ({ LibraryUpdateActionButton(update, onIgnoreUpdate) })
            else -> entry.rating?.let { rating -> { Text("★ $rating") } }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .testTag("library-book-${identity.sourceId}-${identity.remoteBookId}")
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
    DisposableEffect(entryKey(entry)) {
        onCoverVisibility(entry, true)
        onDispose { onCoverVisibility(entry, false) }
    }
    CoverImage(
        state = coverState(entry),
        modifier = modifier,
        unresolvedBadge = entry.reconciliation == org.tsuyomi.core.database.RemoteReconciliationState.UNRESOLVED,
    )
}

