/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import org.tsuyomi.core.database.CollectionKind
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.core.ui.icons.TsuyomiIcons

private const val ShortcutTileWidthDp = 80
fun libraryBookShortcutId(identity: BookIdentity): String =
    "book:${identity.sourceId.length}:${identity.sourceId}${identity.remoteBookId}"


private data class ProductionShortcut(
    val id: String,
    val label: String,
    val supporting: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val filter: SystemLibraryFilter? = null,
    val collection: LibraryCollection? = null,
    val entry: LibraryEntry? = null,
)

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

private fun buildShortcuts(
    entries: List<LibraryEntry>,
    collections: List<LibraryCollection>,
    order: List<String>,
): List<ProductionShortcut> = buildList {
    val hiddenIds = order.asSequence()
        .filter { it.startsWith("hidden:") }
        .mapTo(hashSetOf()) { it.removePrefix("hidden:") }
    val continueCount = entries.count { entry ->
        entry.progress?.locator?.bookProgress?.let { it < 1.0 } ?: (entry.progress != null)
    }
    val recentCount = entries.count { it.progress != null }
    val readLaterCount = entries.count(LibraryEntry::readLater)
    val updateCount = entries.count { it.book.hasUnreadUpdate }
    if ("continue" !in hiddenIds) {
        add(ProductionShortcut("continue", "继续阅读", "$continueCount 本", TsuyomiIcons.ContinueReading, SystemLibraryFilter.CONTINUE))
    }
    if ("recent" !in hiddenIds) {
        add(ProductionShortcut("recent", "最近阅读", "$recentCount 本", TsuyomiIcons.Recent, SystemLibraryFilter.RECENT))
    }
    if ("read-later" !in hiddenIds) {
        add(ProductionShortcut("read-later", "稍后再读", "$readLaterCount 本", TsuyomiIcons.Bookmark, SystemLibraryFilter.READ_LATER))
    }
    if ("updates" !in hiddenIds) {
        add(ProductionShortcut("updates", "追更", "$updateCount 本有更新", TsuyomiIcons.Updates, SystemLibraryFilter.UNREAD))
    }
    collections.forEach { collection ->
        val id = "collection:${collection.collectionId}"
        if (id !in hiddenIds) {
            add(
                ProductionShortcut(
                    id = id,
                    label = collection.title,
                    supporting = when (collection.kind) {
                        CollectionKind.MANUAL -> "收藏夹"
                        CollectionKind.SMART -> "智能收藏夹"
                        CollectionKind.SUBSCRIPTION -> "网站镜像"
                    },
                    icon = when (collection.kind) {
                        CollectionKind.MANUAL -> TsuyomiIcons.Folder
                        CollectionKind.SMART -> TsuyomiIcons.SmartCollection
                        CollectionKind.SUBSCRIPTION -> TsuyomiIcons.Compass
                    },
                    collection = collection,
                ),
            )
        }
    }
    val orderedIds = order.toHashSet()
    entries.forEach { entry ->
        val id = libraryBookShortcutId(entry.book.identity)
        if (id in orderedIds) {
            add(
                ProductionShortcut(
                    id = id,
                    label = entry.book.title,
                    supporting = entry.progress?.locator?.bookProgress?.let { "读至 ${(it * 100).toInt()}%" } ?: "书籍",
                    icon = TsuyomiIcons.Shelf,
                    entry = entry,
                ),
            )
        }
    }
}

private fun openShortcut(
    shortcut: ProductionShortcut,
    onOpenSystemNode: (SystemLibraryFilter) -> Unit,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenBook: (LibraryEntry) -> Unit,
) {
    when {
        shortcut.filter != null -> onOpenSystemNode(shortcut.filter)
        shortcut.collection != null -> onOpenCollection(shortcut.collection)
        shortcut.entry != null -> onOpenBook(shortcut.entry)
    }
}

private fun orderShortcuts(
    shortcuts: List<ProductionShortcut>,
    order: List<String>,
): List<ProductionShortcut> {
    if (order.isEmpty()) return shortcuts
    val byId = shortcuts.associateBy(ProductionShortcut::id)
    val ordered = order.mapNotNull(byId::get)
    return ordered + shortcuts.filterNot { candidate -> ordered.any { it.id == candidate.id } }
}


@Composable
private fun ShortcutShelf(
    shortcuts: List<ProductionShortcut>,
    locked: Boolean,
    onLocked: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onViewAll: () -> Unit,
    onOpen: (ProductionShortcut) -> Unit,
    dragCoordinator: LibraryDragCoordinator,
    selectionKind: LibrarySelectionKind?,
    selectedBookIds: Set<BookIdentity>,
    selectedCollectionIds: Set<String>,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
    onLongPressCollection: (String) -> Unit,
    onToggleCollectionSelection: (String) -> Unit,
    coverState: (LibraryEntry) -> CoverUiState,
) {

    val dragActive = dragCoordinator.activePayload != null
    val dropActive = dragActive && dragCoordinator.isOverShelf
    val collectionTarget = dragCoordinator.collectionTargetId?.let { targetId ->
        shortcuts.firstOrNull { it.collection?.collectionId == targetId }
    }
    val bookTarget = dragCoordinator.bookTargetIdentity?.let { targetIdentity ->
        shortcuts.firstOrNull { it.entry?.book?.identity == targetIdentity }
    }
    val gapIndex = dragCoordinator.rootInsertionIndex
        .takeIf { dropActive && collectionTarget == null && bookTarget == null && it >= 0 }
        ?.coerceIn(0, shortcuts.size)
    val batchCount = dragCoordinator.activeBookIds.size
    val dropHint = when {
        collectionTarget != null -> "松开以加入「${collectionTarget.label}」"
        bookTarget != null -> "松开以和「${bookTarget.label}」新建收藏夹"
        gapIndex != null && batchCount > 1 -> "松开以新建收藏夹并放到第 ${gapIndex + 1} 位"
        gapIndex != null -> "松开以放到快捷书架第 ${gapIndex + 1} 位"
        dragActive && batchCount > 1 -> "拖到快捷书架空位新建收藏夹，或拖入现有收藏夹"
        dragActive && dragCoordinator.activeBookIds.isNotEmpty() -> "拖到快捷书架空位、书籍或收藏夹"
        else -> null
    }
    Column(
        Modifier.fillMaxWidth()
            .libraryShelfDropTarget(dragCoordinator)
            .background(if (dropActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("快捷书架", style = MaterialTheme.typography.titleSmall)
                dropHint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onCreate, modifier = Modifier.size(48.dp)) {
                Icon(TsuyomiIcons.Add, contentDescription = "新建收藏夹")
            }
            IconButton(onClick = onViewAll, modifier = Modifier.size(48.dp)) {
                Icon(TsuyomiIcons.ViewAll, contentDescription = "查看全部快捷书架")
            }
            IconButton(onClick = { onLocked(!locked) }, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (locked) TsuyomiIcons.Lock else TsuyomiIcons.LockOpen,
                    contentDescription = if (locked) "快捷书架已锁定，点按解锁" else "快捷书架未锁定，点按锁定",
                )
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().testTag("library-shortcut-shelf"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val visualCount = shortcuts.size + if (gapIndex != null) 1 else 0
            items(
                count = visualCount,
                key = { visualIndex ->
                    if (visualIndex == gapIndex) "shortcut-root-drop-gap"
                    else shortcuts[if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex].id
                },
            ) { visualIndex ->
                if (visualIndex == gapIndex) {
                    ShortcutInsertionGap(Modifier.optionalAnimateItem(this))
                } else {
                    val itemIndex = if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex
                    val shortcut = shortcuts[itemIndex]
                    ShortcutTile(
                        shortcut = shortcut,
                        index = itemIndex,
                        locked = locked,
                        onOpen = { onOpen(shortcut) },
                        dragCoordinator = dragCoordinator,
                        selectionKind = selectionKind,
                        selectedBookIds = selectedBookIds,
                        selectedCollectionIds = selectedCollectionIds,
                        onLongPressBook = onLongPressBook,
                        onToggleBookSelection = onToggleBookSelection,
                        onLongPressCollection = onLongPressCollection,
                        onToggleCollectionSelection = onToggleCollectionSelection,
                        coverState = coverState,
                        modifier = Modifier.width(ShortcutTileWidthDp.dp).optionalAnimateItem(this),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(
    shortcut: ProductionShortcut,
    index: Int,
    locked: Boolean,
    onOpen: () -> Unit,
    dragCoordinator: LibraryDragCoordinator,
    selectionKind: LibrarySelectionKind?,
    selectedBookIds: Set<BookIdentity>,
    selectedCollectionIds: Set<String>,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
    onLongPressCollection: (String) -> Unit,
    onToggleCollectionSelection: (String) -> Unit,
    coverState: (LibraryEntry) -> CoverUiState,
    modifier: Modifier = Modifier,
) {
    val collectionId = shortcut.collection?.takeIf { it.kind == CollectionKind.MANUAL }?.collectionId
    val bookIdentity = shortcut.entry?.book?.identity
    val selected = when {
        collectionId != null -> collectionId in selectedCollectionIds
        bookIdentity != null -> bookIdentity in selectedBookIds
        else -> false
    }
    val targetActive = (collectionId != null && dragCoordinator.collectionTargetId == collectionId) ||
        (bookIdentity != null && dragCoordinator.bookTargetShortcutId == shortcut.id)
    val instant = LocalDisplayEnvironment.current.instantMotion
    val targetScale by animateFloatAsState(
        targetValue = if (targetActive) 1.05f else 1f,
        animationSpec = if (instant) snap() else spring(stiffness = 520f, dampingRatio = 0.72f),
        label = "libraryShortcutTargetScale",
    )
    val targetContainer by animateColorAsState(
        targetValue = if (targetActive || selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS),
        label = "libraryShortcutTargetContainer",
    )
    val targetOutline by animateColorAsState(
        targetValue = if (targetActive || selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS),
        label = "libraryShortcutTargetOutline",
    )
    val tileClick = {
        when {
            selectionKind == LibrarySelectionKind.COLLECTION && collectionId != null ->
                onToggleCollectionSelection(collectionId)
            selectionKind == LibrarySelectionKind.BOOK && bookIdentity != null ->
                onToggleBookSelection(bookIdentity)
            selectionKind == null -> onOpen()
        }
    }
    val tileLongPress = {
        when {
            collectionId != null -> onLongPressCollection(collectionId)
            bookIdentity != null -> onLongPressBook(bookIdentity)
        }
    }
    val dropKind = when {
        collectionId != null -> LibraryShortcutDropKind.COLLECTION
        bookIdentity != null -> LibraryShortcutDropKind.BOOK
        else -> LibraryShortcutDropKind.ITEM
    }
    val payload = if (bookIdentity != null) {
        { LibraryDragPayload.Books(setOf(bookIdentity), fromShortcut = true) }
    } else {
        { LibraryDragPayload.Shortcut(shortcut.id) }
    }
    Surface(
        modifier = modifier
            .testTag("library-shortcut-${shortcut.id}")
            .height(116.dp)
            .graphicsLayer {
                scaleX = targetScale
                scaleY = targetScale
            }
            .libraryShortcutDropTarget(
                coordinator = dragCoordinator,
                id = shortcut.id,
                index = index,
                kind = dropKind,
                bookIdentity = bookIdentity,
            )
            .libraryShortcutGestures(
                subjectKey = shortcut.id,
                coordinator = dragCoordinator,
                payload = payload,
                selected = selected,
                selectionActive = selectionKind != null,
                dragEnabled = true,
                canRemove = true,
                scrollOrientation = Orientation.Horizontal,
                onTap = tileClick,
                onLongPress = tileLongPress,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = shortcut.label
                stateDescription = when {
                    targetActive -> "当前拖放目标"
                    locked -> "快捷书架已固定，可拖动"
                    else -> "快捷书架随内容滚动，可拖动"
                }
                this.selected = selected
            },
        shape = MaterialTheme.shapes.small,
        color = targetContainer,
        border = BorderStroke(if (selected || targetActive) 2.dp else 1.dp, targetOutline),
        shadowElevation = if (targetActive) 8.dp else 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(4.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(76.dp)
                    .testTag("library-shortcut-media-${shortcut.id}")
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                shortcut.entry?.let { entry ->
                    CoverImage(
                        state = coverState(entry),
                        modifier = Modifier.fillMaxSize(),
                    )
                } ?: Icon(shortcut.icon, contentDescription = null, modifier = Modifier.size(28.dp))
                if (selected) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                TsuyomiIcons.Selected,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            Text(
                shortcut.label,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
@Composable
private fun ShortcutAllPage(
    shortcuts: List<ProductionShortcut>,
    locked: Boolean,
    onLocked: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: (ProductionShortcut) -> Unit,
    dragCoordinator: LibraryDragCoordinator,
    selectionKind: LibrarySelectionKind?,
    selectedBookIds: Set<BookIdentity>,
    selectedCollectionIds: Set<String>,
    onLongPressBook: (BookIdentity) -> Unit,
    onToggleBookSelection: (BookIdentity) -> Unit,
    onLongPressCollection: (String) -> Unit,
    onToggleCollectionSelection: (String) -> Unit,
    coverState: (LibraryEntry) -> CoverUiState,
    modifier: Modifier,
) {

    val gridState = rememberLazyGridState()
    val gapIndex = dragCoordinator.rootInsertionIndex
        .takeIf { dragCoordinator.isOverShelf && it >= 0 }
        ?.coerceIn(0, shortcuts.size)
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(TsuyomiIcons.Back, contentDescription = "返回")
            }
            Text("全部快捷书架", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onCreate, modifier = Modifier.size(48.dp)) {
                Icon(TsuyomiIcons.Add, contentDescription = "新建收藏夹")
            }
            IconButton(onClick = { onLocked(!locked) }, modifier = Modifier.size(48.dp)) {
                Icon(if (locked) TsuyomiIcons.Lock else TsuyomiIcons.LockOpen, contentDescription = "切换快捷书架锁定状态")
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize().libraryShelfDropTarget(dragCoordinator),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val visualCount = shortcuts.size + if (gapIndex != null) 1 else 0
                items(
                    count = visualCount,
                    key = { visualIndex ->
                        if (visualIndex == gapIndex) "shortcut-root-drop-gap"
                        else shortcuts[if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex].id
                    },
                ) { visualIndex ->
                    if (visualIndex == gapIndex) {
                        ShortcutInsertionGap(Modifier.fillMaxWidth().optionalAnimateItem(this), expanded = true)
                    } else {
                        val itemIndex = if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex
                        val shortcut = shortcuts[itemIndex]
                        ShortcutTile(
                            shortcut = shortcut,
                            index = itemIndex,
                            locked = locked,
                            onOpen = { onOpen(shortcut) },
                            dragCoordinator = dragCoordinator,
                            selectionKind = selectionKind,
                            selectedBookIds = selectedBookIds,
                            selectedCollectionIds = selectedCollectionIds,
                            onLongPressBook = onLongPressBook,
                            onToggleBookSelection = onToggleBookSelection,
                            onLongPressCollection = onLongPressCollection,
                            onToggleCollectionSelection = onToggleCollectionSelection,
                            coverState = coverState,
                            modifier = Modifier.fillMaxWidth().optionalAnimateItem(this),
                        )
                    }
                }
            }
            TsuyomiAdaptiveListFab(
                state = gridState,
                topLabel = "顶部",
                endLabel = "末尾",
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

private enum class ShortcutShelfPresentation {
    INLINE,
    COLLAPSED,
    OVERLAY_EXPANDED,
}

private enum class LibraryScrollDirection {
    FORWARD,
    BACKWARD,
    IDLE,
}

private data class LibraryViewport(
    val headerVisible: Boolean,
    val atStart: Boolean,
    val direction: LibraryScrollDirection,
)

@Composable
private fun ShortcutInsertionGap(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    var revealed by remember { mutableStateOf(false) }
    val instant = LocalDisplayEnvironment.current.instantMotion
    LaunchedEffect(Unit) { revealed = true }
    AnimatedVisibility(
        visible = revealed,
        modifier = modifier.testTag("library-shortcut-insertion-gap"),
        enter = expandHorizontally(
            animationSpec = if (instant) snap() else spring(dampingRatio = 0.78f, stiffness = 430f),
            expandFrom = Alignment.CenterHorizontally,
        ) + fadeIn(if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS)),
        exit = fadeOut(if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS)),
    ) {
        Surface(
            modifier = (if (expanded) {
                Modifier.fillMaxWidth().aspectRatio(3f / 4f)
            } else {
                Modifier.width(ShortcutTileWidthDp.dp).height(116.dp)
            }).clearAndSetSemantics { },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.66f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) {
            Column(
                Modifier.fillMaxSize().padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(TsuyomiIcons.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                Text("放到这里", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun Modifier.optionalAnimateItem(scope: LazyItemScope): Modifier =
    if (LocalInspectionMode.current) this else with(scope) { this@optionalAnimateItem.animateItem() }

@Composable
private fun Modifier.optionalAnimateItem(scope: LazyGridItemScope): Modifier =
    if (LocalInspectionMode.current) this else with(scope) { this@optionalAnimateItem.animateItem() }

@Composable
private fun ShortcutShelfOverlay(
    expanded: Boolean,
    coordinator: LibraryDragCoordinator,
    onExpand: () -> Unit,
    shelf: @Composable () -> Unit,
) {
    val instant = LocalDisplayEnvironment.current.instantMotion
    if (instant) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (expanded) shelf() else ShortcutShelfHandle(coordinator, onExpand)
        }
        return
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier.testTag("library-shortcut-overlay-expanded")
                .semantics { stateDescription = "快捷书架已展开" },
            enter = expandVertically(
                tween(TsuyomiMotion.EXPAND_DURATION_MS, easing = TsuyomiMotion.Easing),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(TsuyomiMotion.EXPAND_DURATION_MS)),
            exit = shrinkVertically(
                tween(TsuyomiMotion.EXPAND_DURATION_MS, easing = TsuyomiMotion.Easing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(TsuyomiMotion.EXPAND_DURATION_MS)),
        ) { shelf() }
        AnimatedVisibility(
            visible = !expanded,
            enter = expandVertically(
                tween(TsuyomiMotion.EXPAND_DURATION_MS, easing = TsuyomiMotion.Easing),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(TsuyomiMotion.EXPAND_DURATION_MS)),
            exit = shrinkVertically(
                tween(TsuyomiMotion.EXPAND_DURATION_MS, easing = TsuyomiMotion.Easing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(TsuyomiMotion.EXPAND_DURATION_MS)),
        ) { ShortcutShelfHandle(coordinator, onExpand) }
    }
}

@Composable
private fun ShortcutShelfHandle(coordinator: LibraryDragCoordinator, onExpand: () -> Unit) {
    Surface(
        onClick = onExpand,
        modifier = Modifier.size(48.dp)
            .libraryShelfDropTarget(coordinator, onHover = onExpand)
            .testTag("library-shortcut-overlay-collapsed")
            .semantics {
                contentDescription = "展开快捷书架"
                stateDescription = if (coordinator.activePayload != null && coordinator.isOverShelf) {
                    "当前拖放目标"
                } else {
                    "快捷书架已收起"
                }
                role = Role.Button
            },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(TsuyomiIcons.Disclosure, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun LibraryDragVisualOverlay(
    coordinator: LibraryDragCoordinator,
    entries: List<LibraryEntry>,
    shortcuts: List<ProductionShortcut>,
    layout: LibraryLayout,
    coverState: (LibraryEntry) -> CoverUiState,
    modifier: Modifier = Modifier,
) {
    val payload = coordinator.activePayload
    val instant = LocalDisplayEnvironment.current.instantMotion
    AnimatedVisibility(
        visible = payload != null,
        modifier = modifier.testTag("library-drag-overlay").semantics { hideFromAccessibility() },
        enter = fadeIn(if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS)) +
            scaleIn(if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS), initialScale = 0.96f),
        exit = fadeOut(if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS)) +
            scaleOut(if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS), targetScale = 0.96f),
    ) {
        Box(Modifier.fillMaxSize()) {
            payload?.let { active ->
                val previewSize = dragPreviewSize(active, layout)
                val density = LocalDensity.current
                val host = coordinator.hostTopLeft()
                val previewWidthPx = with(density) { previewSize.width.toPx() }
                val previewHeightPx = with(density) { previewSize.height.toPx() }
                val pointer = coordinator.ghostPositionInWindow
                val x = pointer.x - host.x - previewWidthPx / 2f
                val y = pointer.y - host.y - previewHeightPx * 0.34f
                Box(
                    modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .testTag("library-drag-preview")
                        .graphicsLayer { alpha = 0.94f },
                ) {
                    when (active) {
                        is LibraryDragPayload.Books -> {
                            val activeEntries = entries.filter { it.book.identity in active.identities }
                            if (active.fromShortcut && activeEntries.size == 1) {
                                ShortcutBookDragPreview(activeEntries.first(), coverState)
                            } else {
                                LibraryBookDragPreview(activeEntries, layout, coverState)
                            }
                        }
                        is LibraryDragPayload.Shortcut -> {
                            shortcuts.firstOrNull { it.id == active.id }?.let {
                                ShortcutDragPreview(it, coverState)
                            }
                        }
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .heightIn(min = 48.dp)
                        .libraryDeleteDropTarget(coordinator)
                        .testTag("library-delete-drop-target"),
                    color = if (coordinator.isOverDelete) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = if (coordinator.isOverDelete) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.error)
                    } else null,
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(TsuyomiIcons.Delete, contentDescription = null)
                        val shortcutRemoval = active is LibraryDragPayload.Shortcut ||
                            (active is LibraryDragPayload.Books && active.fromShortcut)
                        Text(
                            when {
                                coordinator.isOverDelete && shortcutRemoval -> "松开以移出快捷书架"
                                coordinator.isOverDelete -> "松开以移出书架"
                                shortcutRemoval -> "拖到这里移出快捷书架"
                                else -> "拖到这里移出书架"
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun dragPreviewSize(payload: LibraryDragPayload, layout: LibraryLayout): DpSize = when (payload) {
    is LibraryDragPayload.Shortcut -> DpSize(84.dp, 116.dp)
    is LibraryDragPayload.Books -> if (payload.fromShortcut) {
        DpSize(84.dp, 116.dp)
    } else when (layout) {
        LibraryLayout.GRID -> DpSize(132.dp, 180.dp)
        LibraryLayout.LIST -> DpSize(292.dp, 104.dp)
        LibraryLayout.COMPACT -> DpSize(272.dp, 64.dp)
    }
}

@Composable
private fun LibraryBookDragPreview(
    entries: List<LibraryEntry>,
    layout: LibraryLayout,
    coverState: (LibraryEntry) -> CoverUiState,
) {
    val lead = entries.firstOrNull() ?: return
    when (layout) {
        LibraryLayout.GRID -> Surface(
            modifier = Modifier.size(width = 132.dp, height = 180.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
        ) {
            Box {
                CoverImage(coverState(lead), modifier = Modifier.fillMaxSize())
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))))
                        .padding(start = 8.dp, top = 30.dp, end = 8.dp, bottom = 8.dp),
                ) {
                    Text(lead.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
                }
                DragBatchBadge(entries.size, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        LibraryLayout.LIST -> Surface(
            modifier = Modifier.size(width = 292.dp, height = 104.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
        ) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(coverState(lead), modifier = Modifier.size(width = 68.dp, height = 92.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(lead.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(lead.book.authors.joinToString("、"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DragBatchBadge(entries.size, Modifier.padding(end = 6.dp))
            }
        }
        LibraryLayout.COMPACT -> Surface(
            modifier = Modifier.size(width = 272.dp, height = 64.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
        ) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(lead.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                DragBatchBadge(entries.size)
            }
        }
    }
}

@Composable
private fun ShortcutBookDragPreview(entry: LibraryEntry, coverState: (LibraryEntry) -> CoverUiState) {
    Surface(
        modifier = Modifier.size(width = 84.dp, height = 116.dp),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(4.dp)) {
            CoverImage(coverState(entry), modifier = Modifier.fillMaxWidth().height(76.dp))
            Text(entry.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ShortcutDragPreview(shortcut: ProductionShortcut, coverState: (LibraryEntry) -> CoverUiState) {
    Surface(
        modifier = Modifier.size(width = 84.dp, height = 116.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(4.dp)) {
            Box(
                Modifier.fillMaxWidth().height(76.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                shortcut.entry?.let { CoverImage(coverState(it), modifier = Modifier.fillMaxSize()) }
                    ?: Icon(shortcut.icon, contentDescription = null)
            }
            Text(shortcut.label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DragBatchBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary) {
        Text(
            count.toString(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AtlasBookSurface(
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
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
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
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
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
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryBookInsertionGap(layout: LibraryLayout) {
    val modifier = when (layout) {
        LibraryLayout.GRID -> Modifier.fillMaxWidth().aspectRatio(3f / 4f)
        LibraryLayout.LIST -> Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 16.dp, vertical = 4.dp)
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
private fun ObserveLibraryViewport(
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
private fun AtlasBookGridCard(
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
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
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
                    .padding(start = 8.dp, top = 28.dp, end = 8.dp, bottom = 6.dp),
            ) {
                Text(entry.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun AtlasBookListRow(
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
private fun AtlasCompactBookRow(
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
private fun ProductionBookCover(
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
private fun AtlasLibrarySortDialog(
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


private fun entryKey(entry: LibraryEntry): String =
    "${entry.book.identity.sourceId}\u0000${entry.book.identity.remoteBookId}"
