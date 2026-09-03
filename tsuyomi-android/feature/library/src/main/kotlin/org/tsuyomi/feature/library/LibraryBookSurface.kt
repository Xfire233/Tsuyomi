/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.shared.model.BookIdentity

internal enum class LibraryScrollDirection {
    FORWARD,
    BACKWARD,
    IDLE,
}

internal data class LibraryViewport(
    val headerVisible: Boolean,
    val atStart: Boolean,
    val direction: LibraryScrollDirection,
)

internal fun entryKey(entry: LibraryEntry): String =
    "${entry.book.identity.sourceId}\u0000${entry.book.identity.remoteBookId}"

@Composable
internal fun AtlasBookSurface(
    entries: List<LibraryEntry>,
    state: LibraryUiState,
    onOpenBook: (LibraryEntry) -> Unit,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
    dragCoordinator: LibraryDragCoordinator,
    dragEnabled: Boolean,
    reorderEnabled: Boolean,
    coverState: (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    header: (@Composable () -> Unit)? = null,
    onViewportChanged: ((LibraryViewport) -> Unit)? = null,
    empty: @Composable () -> Unit,
    modifier: Modifier,
) {
    if (entries.isEmpty()) {
        Column(modifier.fillMaxSize()) {
            header?.invoke()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { empty() }
        }
        return
    }
    val selectionActive = state.selectionKind == LibrarySelectionKind.BOOK
    val libraryGapIndex = dragCoordinator.libraryInsertionIndex
        .takeIf { dragCoordinator.activeBookIds.isNotEmpty() && it >= 0 }
        ?.coerceIn(0, entries.size)
    val surfaceModifier = Modifier.fillMaxSize()
        .testTag("library-book-surface")
        .libraryContentDropTarget(dragCoordinator, reorderEnabled)
    when (state.layout) {
        LibraryLayout.GRID -> {
            val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp }
            val gridState = rememberLazyGridState()
            ObserveLibraryViewport(
                firstVisibleIndex = { gridState.firstVisibleItemIndex },
                firstVisibleOffset = { gridState.firstVisibleItemScrollOffset },
                hasHeader = header != null,
                onChanged = onViewportChanged,
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
                        item(key = "shortcut-header", span = { GridItemSpan(maxLineSpan) }) { headerContent() }
                    }
                    val visualCount = entries.size + if (libraryGapIndex != null) 1 else 0
                    items(
                        count = visualCount,
                        key = { visualIndex ->
                            if (visualIndex == libraryGapIndex) "library-drop-gap"
                            else entryKey(entries[if (libraryGapIndex != null && visualIndex > libraryGapIndex) visualIndex - 1 else visualIndex])
                        },
                    ) { visualIndex ->
                        if (visualIndex == libraryGapIndex) {
                            LibraryBookInsertionGap(LibraryLayout.GRID)
                        } else {
                            val index = if (libraryGapIndex != null && visualIndex > libraryGapIndex) visualIndex - 1 else visualIndex
                            val entry = entries[index]
                            AtlasBookGridCard(
                                entry = entry,
                                index = index,
                                selected = entry.book.identity in state.selectedBookIds,
                                selectionActive = selectionActive,
                                selectedBookIds = state.selectedBookIds,
                                dragCoordinator = dragCoordinator,
                                dragEnabled = dragEnabled,
                                coverState = coverState,
                                onCoverVisibility = onCoverVisibility,
                                onOpenBook = onOpenBook,
                                onLongPressBook = onLongPressBook,
                                onToggleBookSelection = onToggleBookSelection,
                            )
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
        LibraryLayout.LIST -> {
            val listState = rememberLazyListState()
            ObserveLibraryViewport(
                firstVisibleIndex = { listState.firstVisibleItemIndex },
                firstVisibleOffset = { listState.firstVisibleItemScrollOffset },
                hasHeader = header != null,
                onChanged = onViewportChanged,
            )
            Box(modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = surfaceModifier,
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    header?.let { headerContent -> item(key = "shortcut-header") { headerContent() } }
                    val visualCount = entries.size + if (libraryGapIndex != null) 1 else 0
                    items(
                        count = visualCount,
                        key = { visualIndex ->
                            if (visualIndex == libraryGapIndex) "library-drop-gap"
                            else entryKey(entries[if (libraryGapIndex != null && visualIndex > libraryGapIndex) visualIndex - 1 else visualIndex])
                        },
                    ) { visualIndex ->
                        if (visualIndex == libraryGapIndex) {
                            LibraryBookInsertionGap(LibraryLayout.LIST)
                        } else {
                            val index = if (libraryGapIndex != null && visualIndex > libraryGapIndex) visualIndex - 1 else visualIndex
                            val entry = entries[index]
                            AtlasBookListRow(
                                entry = entry,
                                index = index,
                                selected = entry.book.identity in state.selectedBookIds,
                                selectionActive = selectionActive,
                                selectedBookIds = state.selectedBookIds,
                                dragCoordinator = dragCoordinator,
                                dragEnabled = dragEnabled,
                                coverState = coverState,
                                onCoverVisibility = onCoverVisibility,
                                onOpenBook = onOpenBook,
                                onLongPressBook = onLongPressBook,
                                onToggleBookSelection = onToggleBookSelection,
                            )
                            HorizontalDivider()
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
        LibraryLayout.COMPACT -> {
            val listState = rememberLazyListState()
            ObserveLibraryViewport(
                firstVisibleIndex = { listState.firstVisibleItemIndex },
                firstVisibleOffset = { listState.firstVisibleItemScrollOffset },
                hasHeader = header != null,
                onChanged = onViewportChanged,
            )
            Box(modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = surfaceModifier,
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    header?.let { headerContent -> item(key = "shortcut-header") { headerContent() } }
                    val visualCount = entries.size + if (libraryGapIndex != null) 1 else 0
                    items(
                        count = visualCount,
                        key = { visualIndex ->
                            if (visualIndex == libraryGapIndex) "library-drop-gap"
                            else entryKey(entries[if (libraryGapIndex != null && visualIndex > libraryGapIndex) visualIndex - 1 else visualIndex])
                        },
                    ) { visualIndex ->
                        if (visualIndex == libraryGapIndex) {
                            LibraryBookInsertionGap(LibraryLayout.COMPACT)
                        } else {
                            val index = if (libraryGapIndex != null && visualIndex > libraryGapIndex) visualIndex - 1 else visualIndex
                            val entry = entries[index]
                            AtlasCompactBookRow(
                                entry = entry,
                                index = index,
                                selected = entry.book.identity in state.selectedBookIds,
                                selectionActive = selectionActive,
                                selectedBookIds = state.selectedBookIds,
                                dragCoordinator = dragCoordinator,
                                dragEnabled = dragEnabled,
                                onOpenBook = onOpenBook,
                                onLongPressBook = onLongPressBook,
                                onToggleBookSelection = onToggleBookSelection,
                            )
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

@Composable
internal fun LibraryBookInsertionGap(layout: LibraryLayout) {
    val modifier = when (layout) {
        LibraryLayout.GRID -> Modifier.fillMaxWidth().aspectRatio(3f / 4f)
        LibraryLayout.LIST -> Modifier.fillMaxWidth().height(TsuyomiSpacing.Md).padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs)
        LibraryLayout.COMPACT -> Modifier.fillMaxWidth().height(12.dp).padding(horizontal = 16.dp, vertical = 3.dp)
    }
    Surface(
        modifier = modifier.clearAndSetSemantics { }.testTag("library-book-insertion-gap"),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {}
}

@Composable
internal fun ObserveLibraryViewport(
    firstVisibleIndex: () -> Int,
    firstVisibleOffset: () -> Int,
    hasHeader: Boolean,
    onChanged: ((LibraryViewport) -> Unit)?,
) {
    val currentOnChanged by rememberUpdatedState(onChanged)
    LaunchedEffect(firstVisibleIndex, firstVisibleOffset, hasHeader) {
        var previousIndex = firstVisibleIndex()
        var previousOffset = firstVisibleOffset()
        snapshotFlow { firstVisibleIndex() to firstVisibleOffset() }.collect { (index, offset) ->
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
                ),
            )
            previousIndex = index
            previousOffset = offset
        }
    }
}

@Composable
internal fun AtlasBookGridCard(
    entry: LibraryEntry,
    index: Int,
    selected: Boolean,
    selectionActive: Boolean,
    selectedBookIds: Set<BookIdentity>,
    dragCoordinator: LibraryDragCoordinator,
    dragEnabled: Boolean,
    coverState: (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
) {
    val identity = entry.book.identity
    val status = entry.progress?.locator?.bookProgress?.let { "读至 ${(it * 100).toInt()}%" }
        ?: when {
            entry.book.hasUnreadUpdate -> "有更新"
            entry.readLater -> "稍后再读"
            !entry.sourceAvailable -> "来源未安装"
            else -> "未开始"
        }
    val targeted = dragCoordinator.bookTargetIdentity == identity
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
                dragEnabled = dragEnabled,
                canRemove = true,
                reorderSource = true,
                scrollOrientation = Orientation.Vertical,
                onTap = { if (selectionActive) onToggleBookSelection(identity) else onOpenBook(entry) },
                onLongPress = { onLongPressBook(identity) },
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected || targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = if (selected || targeted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            ProductionBookCover(entry, coverState, onCoverVisibility, Modifier.fillMaxSize())
            if (entry.book.hasUnreadUpdate) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(TsuyomiSpacing.Xs),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text("有更新", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
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
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                    .padding(start = TsuyomiSpacing.Sm, top = 28.dp, end = TsuyomiSpacing.Sm, bottom = 6.dp),
            ) {
                Text(entry.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
internal fun AtlasBookListRow(
    entry: LibraryEntry,
    index: Int,
    selected: Boolean,
    selectionActive: Boolean,
    selectedBookIds: Set<BookIdentity>,
    dragCoordinator: LibraryDragCoordinator,
    dragEnabled: Boolean,
    coverState: (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
) {
    val identity = entry.book.identity
    val targeted = dragCoordinator.bookTargetIdentity == identity
    ListItem(
        headlineContent = { Text(entry.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        overlineContent = entry.book.authors.joinToString("、").takeIf(String::isNotBlank)?.let { authors ->
            { Text(authors, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        supportingContent = {
            Text(entry.progress?.locator?.bookProgress?.let { "读至 ${(it * 100).toInt()}%" } ?: if (entry.readLater) "稍后再读" else "未开始")
        },
        leadingContent = {
            ProductionBookCover(entry, coverState, onCoverVisibility, Modifier.size(width = 84.dp, height = 112.dp))
        },
        trailingContent = if (selected) {
            { Icon(TsuyomiIcons.Selected, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary) }
        } else null,
        modifier = Modifier.fillMaxWidth()
            .testTag("library-book-${identity.sourceId}-${identity.remoteBookId}")
            .libraryBookDropTarget(dragCoordinator, identity, index)
            .libraryBookGestures(
                identity = identity,
                coordinator = dragCoordinator,
                selected = selected,
                selectionActive = selectionActive,
                selectedBookIds = selectedBookIds,
                dragEnabled = dragEnabled,
                canRemove = true,
                reorderSource = true,
                scrollOrientation = Orientation.Vertical,
                onTap = { if (selectionActive) onToggleBookSelection(identity) else onOpenBook(entry) },
                onLongPress = { onLongPressBook(identity) },
            ),
        colors = ListItemDefaults.colors(
            containerColor = if (selected || targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
internal fun AtlasCompactBookRow(
    entry: LibraryEntry,
    index: Int,
    selected: Boolean,
    selectionActive: Boolean,
    selectedBookIds: Set<BookIdentity>,
    dragCoordinator: LibraryDragCoordinator,
    dragEnabled: Boolean,
    onOpenBook: (LibraryEntry) -> Unit,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
) {
    val identity = entry.book.identity
    val targeted = dragCoordinator.bookTargetIdentity == identity
    ListItem(
        headlineContent = { Text(entry.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val supporting = entry.progress?.locator?.bookProgress?.let { "读至 ${(it * 100).toInt()}%" }
                ?: entry.book.authors.joinToString("、")
            if (supporting.isNotBlank()) Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = if (selected) {
            { Icon(TsuyomiIcons.Selected, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary) }
        } else entry.rating?.let { rating -> { Text("★ $rating") } },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .testTag("library-book-${identity.sourceId}-${identity.remoteBookId}")
            .libraryBookDropTarget(dragCoordinator, identity, index)
            .libraryBookGestures(
                identity = identity,
                coordinator = dragCoordinator,
                selected = selected,
                selectionActive = selectionActive,
                selectedBookIds = selectedBookIds,
                dragEnabled = dragEnabled,
                canRemove = true,
                reorderSource = true,
                scrollOrientation = Orientation.Vertical,
                onTap = { if (selectionActive) onToggleBookSelection(identity) else onOpenBook(entry) },
                onLongPress = { onLongPressBook(identity) },
            ),
        colors = ListItemDefaults.colors(
            containerColor = if (selected || targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
internal fun ProductionBookCover(
    entry: LibraryEntry,
    coverState: (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    modifier: Modifier,
) {
    DisposableEffect(entryKey(entry)) {
        onCoverVisibility(entry, true)
        onDispose { onCoverVisibility(entry, false) }
    }
    CoverImage(coverState(entry), modifier)
}

@Composable
internal fun AtlasLibrarySortDialog(
    state: LibraryUiState,
    onDismiss: () -> Unit,
    onSelectSort: (LibrarySortMode) -> Unit,
    onSelectDirection: (Boolean) -> Unit,
) {
    if (!state.sortOpen) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("排序依据", style = MaterialTheme.typography.titleMedium)
                LibrarySortMode.entries.forEach { option ->
                    FilterChip(
                        selected = state.sortMode == option,
                        onClick = { onSelectSort(option) },
                        label = { Text(option.label) },
                    )
                }
                Text("顺序", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !state.sortDescending, onClick = { onSelectDirection(false) }, label = { Text("正序") })
                    FilterChip(selected = state.sortDescending, onClick = { onSelectDirection(true) }, label = { Text("倒序") })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
