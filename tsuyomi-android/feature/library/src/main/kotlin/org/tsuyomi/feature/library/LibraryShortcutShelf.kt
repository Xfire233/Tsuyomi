/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.database.CollectionKind
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.shared.model.BookIdentity

internal const val ShortcutTileWidthDp = 80

fun libraryBookShortcutId(identity: BookIdentity): String =
    "book:${identity.sourceId.length}:${identity.sourceId}${identity.remoteBookId}"

internal data class ProductionShortcut(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val filter: SystemLibraryFilter? = null,
    val collection: LibraryCollection? = null,
    val entry: LibraryEntry? = null,
)

internal enum class ShortcutShelfPresentation {
    INLINE,
    COLLAPSED,
    OVERLAY_EXPANDED,
}

internal fun buildShortcuts(
    entries: List<LibraryEntry>,
    collections: List<LibraryCollection>,
    order: List<String>,
): List<ProductionShortcut> = buildList {
    val hiddenIds = order.asSequence()
        .filter { it.startsWith("hidden:") }
        .mapTo(hashSetOf()) { it.removePrefix("hidden:") }
    if ("continue" !in hiddenIds) {
        add(ProductionShortcut("continue", "继续阅读", TsuyomiIcons.ContinueReading, SystemLibraryFilter.CONTINUE))
    }
    if ("recent" !in hiddenIds) {
        add(ProductionShortcut("recent", "最近阅读", TsuyomiIcons.Recent, SystemLibraryFilter.RECENT))
    }
    if ("read-later" !in hiddenIds) {
        add(ProductionShortcut("read-later", "稍后再读", TsuyomiIcons.Bookmark, SystemLibraryFilter.READ_LATER))
    }
    if ("updates" !in hiddenIds) {
        add(ProductionShortcut("updates", "追更", TsuyomiIcons.Updates, SystemLibraryFilter.UNREAD))
    }
    collections.forEach { collection ->
        val id = "collection:${collection.collectionId}"
        if (id !in hiddenIds) {
            add(
                ProductionShortcut(
                    id = id,
                    label = collection.title,
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
                    icon = TsuyomiIcons.Shelf,
                    entry = entry,
                ),
            )
        }
    }
}

internal fun openShortcut(
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

internal fun orderShortcuts(
    shortcuts: List<ProductionShortcut>,
    order: List<String>,
): List<ProductionShortcut> {
    if (order.isEmpty()) return shortcuts
    val byId = shortcuts.associateBy(ProductionShortcut::id)
    val ordered = order.mapNotNull(byId::get)
    return ordered + shortcuts.filterNot { candidate -> ordered.any { it.id == candidate.id } }
}

@Composable
internal fun Modifier.optionalAnimateItem(scope: LazyItemScope): Modifier =
    if (LocalInspectionMode.current) this else with(scope) { this@optionalAnimateItem.animateItem() }

@Composable
internal fun Modifier.optionalAnimateItem(scope: LazyGridItemScope): Modifier =
    if (LocalInspectionMode.current) this else with(scope) { this@optionalAnimateItem.animateItem() }

@Composable
internal fun ShortcutShelf(
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = TsuyomiSpacing.Md, end = TsuyomiSpacing.Xs),
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
internal fun ShortcutTile(
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
        Column(Modifier.fillMaxWidth().padding(TsuyomiSpacing.Xs)) {
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
                        modifier = Modifier.align(Alignment.TopEnd).padding(TsuyomiSpacing.Xs).size(24.dp),
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
internal fun ShortcutAllPage(
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = TsuyomiSpacing.Xs),
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(TsuyomiSpacing.Md),
            )
        }
    }
}

@Composable
internal fun ShortcutInsertionGap(
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
                Modifier.fillMaxSize().padding(TsuyomiSpacing.Xs),
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
internal fun ShortcutShelfOverlay(
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
internal fun ShortcutShelfHandle(coordinator: LibraryDragCoordinator, onExpand: () -> Unit) {
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
