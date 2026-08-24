/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.tsuyomi.prototype.uiatlas.components.AtlasCoverImage
import org.tsuyomi.prototype.uiatlas.components.AtlasIconButton
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasSelectionBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.LibraryBookDragPreview
import org.tsuyomi.prototype.uiatlas.components.LibraryDragCoordinator
import org.tsuyomi.prototype.uiatlas.components.LibraryDropItemKind
import org.tsuyomi.prototype.uiatlas.components.libraryDeleteDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryDragSource
import org.tsuyomi.prototype.uiatlas.components.libraryItemDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryShelfDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryDragPreviewSize
import org.tsuyomi.prototype.uiatlas.fixtures.LibraryAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.runtime.PrototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasMotion
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment



internal enum class ShortcutKind { SYSTEM, COLLECTION, MIRROR, BOOK }

internal enum class LibrarySelectionKind { BOOK, COLLECTION }

internal data class UserCollection(
    val id: String,
    val name: String,
    val bookIds: Set<String>,
    val parentId: String? = null,
)

internal data class Rc21ShortcutItem(
    val id: String,
    val label: String,
    val supporting: String,
    val kind: ShortcutKind,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: AtlasRoute,
    val view: AtlasLibraryView? = null,
    val book: AtlasBook? = null,
    val collectionBooks: List<AtlasBook> = emptyList(),
)


internal data class PendingCollectionCreation(
    val memberBookIds: Set<String> = emptySet(),
    val replacedShortcutIds: Set<String> = emptySet(),
    val insertIndex: Int? = null,
)

internal sealed interface CollectionPickerRequest {
    data class Books(val ids: Set<String>) : CollectionPickerRequest
    data class Collections(val ids: Set<String>) : CollectionPickerRequest
}

internal const val BOOK_DRAG_PREFIX = "tsuyomi:book:"
private const val BOOK_BATCH_DRAG_PREFIX = "tsuyomi:books:"
private const val SHORTCUT_BOOK_DRAG_PREFIX = "tsuyomi:shortcut-book:"
private const val SHORTCUT_BOOK_BATCH_DRAG_PREFIX = "tsuyomi:shortcut-books:"
internal const val SHORTCUT_DRAG_PREFIX = "tsuyomi:shortcut:"

private val shortcutTileWidth = 80.dp
private val shortcutTileHeight = 116.dp

internal fun bookDragPayload(bookId: String, selectedIds: Set<String> = emptySet()): String =
    if (bookId in selectedIds && selectedIds.size > 1) {
        "$BOOK_BATCH_DRAG_PREFIX${selectedIds.sorted().joinToString(",")}"
    } else {
        "$BOOK_DRAG_PREFIX$bookId"
    }

private fun shortcutBookDragPayload(bookId: String, selectedIds: Set<String> = emptySet()): String =
    if (bookId in selectedIds && selectedIds.size > 1) {
        "$SHORTCUT_BOOK_BATCH_DRAG_PREFIX${selectedIds.sorted().joinToString(",")}"
    } else {
        "$SHORTCUT_BOOK_DRAG_PREFIX$bookId"
    }

internal fun payloadBookIds(payload: String): Set<String> = when {
    payload.startsWith(BOOK_DRAG_PREFIX) -> setOf(payload.removePrefix(BOOK_DRAG_PREFIX))
    payload.startsWith(BOOK_BATCH_DRAG_PREFIX) -> payload.removePrefix(BOOK_BATCH_DRAG_PREFIX).split(',').filter(String::isNotEmpty).toSet()
    payload.startsWith(SHORTCUT_BOOK_DRAG_PREFIX) -> setOf(payload.removePrefix(SHORTCUT_BOOK_DRAG_PREFIX))
    payload.startsWith(SHORTCUT_BOOK_BATCH_DRAG_PREFIX) -> payload.removePrefix(SHORTCUT_BOOK_BATCH_DRAG_PREFIX).split(',').filter(String::isNotEmpty).toSet()
    else -> emptySet()
}

internal fun isShortcutBookPayload(payload: String): Boolean =
    payload.startsWith(SHORTCUT_BOOK_DRAG_PREFIX) || payload.startsWith(SHORTCUT_BOOK_BATCH_DRAG_PREFIX)

private fun shortcutDragPayload(shortcutId: String): String = "$SHORTCUT_DRAG_PREFIX$shortcutId"


internal sealed interface LibraryRemovalRequest {
    data class Shortcut(val id: String, val label: String) : LibraryRemovalRequest
    data class Book(val book: AtlasBook) : LibraryRemovalRequest
    data class Books(val books: List<AtlasBook>) : LibraryRemovalRequest
    data class Collection(val id: String, val label: String) : LibraryRemovalRequest
    data class Collections(val items: List<Rc21ShortcutItem>) : LibraryRemovalRequest
}


internal fun bookShortcut(book: AtlasBook): Rc21ShortcutItem = Rc21ShortcutItem(
    id = "book-${book.id}",
    label = book.title,
    supporting = book.progressLabel ?: "书籍",
    kind = ShortcutKind.BOOK,
    icon = AtlasIcons.Shelf,
    route = AtlasRoute.BOOK_DETAIL,
    book = book,
)

internal fun loadUserCollections(repository: PrototypeRepository): List<UserCollection> =
    repository.stringList("library.collections.ids").map { id ->
        UserCollection(
            id = id,
            name = repository.string("library.collection.$id.name", "未命名收藏夹"),
            bookIds = repository.stringList("library.collection.$id.books").toSet(),
            parentId = repository.string("library.collection.$id.parent").takeIf(String::isNotBlank),
        )
    }

internal fun createPersistedUserCollection(
    repository: PrototypeRepository,
    name: String,
    bookIds: Set<String>,
    parentId: String? = null,
): UserCollection {
    val nextId = repository.int("library.collections.nextId", 1)
    val id = "user-$nextId"
    repository.putInt("library.collections.nextId", nextId + 1, "CollectionIdAllocated", id)
    val collection = UserCollection(id, name.trim(), bookIds, parentId)
    repository.putStringList(
        "library.collections.ids",
        (repository.stringList("library.collections.ids") + id).distinct(),
        "CollectionCreated",
        id,
    )
    repository.putString("library.collection.$id.name", collection.name, "CollectionNamed", id)
    repository.putStringList("library.collection.$id.books", collection.bookIds.sorted(), "CollectionBooksSet", id)
    repository.putString("library.collection.$id.parent", collection.parentId.orEmpty(), "CollectionParentSet", id)
    return collection
}

internal fun collectionShortcut(
    collection: UserCollection,
    booksById: Map<String, AtlasBook>,
): Rc21ShortcutItem = Rc21ShortcutItem(
    id = collection.id,
    label = collection.name,
    supporting = "${collection.bookIds.size} 本",
    kind = ShortcutKind.COLLECTION,
    icon = AtlasIcons.Folder,
    route = AtlasRoute.LIBRARY_COLLECTION,
    view = null,
    collectionBooks = collection.bookIds.mapNotNull(booksById::get).take(3),
)

internal fun collectionFixtureShortcut(
    collection: LibraryAtlasFixtures.CollectionFixture,
    route: AtlasRoute = AtlasRoute.LIBRARY_COLLECTION,
): Rc21ShortcutItem = Rc21ShortcutItem(
    id = collection.id,
    label = collection.name,
    supporting = "${collection.bookCount} 本",
    kind = ShortcutKind.COLLECTION,
    icon = if (collection.smart) AtlasIcons.Tune else AtlasIcons.Folder,
    route = route,
    view = null,
)

internal fun collectionRouteForDepth(depth: Int): AtlasRoute = when (depth) {
    0 -> AtlasRoute.LIBRARY_COLLECTION
    1 -> AtlasRoute.LIBRARY_COLLECTION_CHILD
    else -> AtlasRoute.LIBRARY_COLLECTION_GRANDCHILD
}

internal fun mirrorRouteForDepth(depth: Int): AtlasRoute = when (depth) {
    0 -> AtlasRoute.LIBRARY_MIRROR
    1 -> AtlasRoute.LIBRARY_MIRROR_FOLDER
    else -> AtlasRoute.LIBRARY_MIRROR_SUBFOLDER
}

internal fun List<LibraryAtlasFixtures.CollectionFixture>.findCollectionFixture(id: String): LibraryAtlasFixtures.CollectionFixture? =
    firstNotNullOfOrNull { fixture ->
        if (fixture.id == id) fixture else fixture.children.findCollectionFixture(id)
    }

internal fun List<LibraryAtlasFixtures.MirrorNodeFixture>.findMirrorNode(id: String): LibraryAtlasFixtures.MirrorNodeFixture? =
    firstNotNullOfOrNull { node ->
        if (node.id == id) node else node.children.findMirrorNode(id)
    }

internal fun shortcutItems(
    books: List<AtlasBook>,
    storedOrder: List<String> = emptyList(),
    hiddenIds: Set<String> = emptySet(),
    userCollections: List<UserCollection> = emptyList(),
): List<Rc21ShortcutItem> {
    val booksById = books.associateBy(AtlasBook::id)
    val defaults = listOf(
        Rc21ShortcutItem("continue", "继续阅读", "14 本", ShortcutKind.SYSTEM, AtlasIcons.Shelf, AtlasRoute.LIBRARY_SYSTEM, AtlasLibraryView.CONTINUE),
        Rc21ShortcutItem("recent", "最近阅读", "20 本", ShortcutKind.SYSTEM, AtlasIcons.History, AtlasRoute.LIBRARY_HISTORY, AtlasLibraryView.RECENT),
        Rc21ShortcutItem("read-later", "稍后再读", "6 本", ShortcutKind.SYSTEM, AtlasIcons.ReadLater, AtlasRoute.LIBRARY_SYSTEM, AtlasLibraryView.READ_LATER),
        Rc21ShortcutItem("updates", "追更", "9 本有更新", ShortcutKind.SYSTEM, AtlasIcons.Updates, AtlasRoute.LIBRARY_UPDATES),
        Rc21ShortcutItem(
            "night-boat",
            "夜航船",
            "23 本",
            ShortcutKind.COLLECTION,
            AtlasIcons.LayoutGrid,
            AtlasRoute.LIBRARY_COLLECTION,
            null,
            collectionBooks = books.drop(3).take(3),
        ),
        Rc21ShortcutItem("pine-mirror", "源·松镜像", "网站 31 · 本地 4", ShortcutKind.MIRROR, AtlasIcons.Compass, AtlasRoute.LIBRARY_MIRROR, AtlasLibraryView.MIRROR),
    ) + userCollections.map { collection -> collectionShortcut(collection, booksById) } + books.take(2).map(::bookShortcut)
    val visibleDefaults = defaults.filterNot { it.id in hiddenIds }
    if (storedOrder.isEmpty()) return visibleDefaults
    val available = (defaults + books.map(::bookShortcut))
        .filterNot { it.id in hiddenIds }
        .associateBy { it.id }
    val restored = storedOrder.mapNotNull(available::get).distinctBy { it.id }
    return restored + visibleDefaults.filterNot { candidate -> restored.any { it.id == candidate.id } }
}

@Composable
internal fun Modifier.selectionShake(signal: Int): Modifier {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(signal) {
        if (signal > 0) {
            offset.snapTo(0f)
            offset.animateTo(
                0f,
                animationSpec = keyframes {
                    durationMillis = 320
                    -14f at 50
                    14f at 100
                    -10f at 160
                    10f at 220
                    0f at 320
                },
            )
        }
    }
    return graphicsLayer { translationX = offset.value }
}

@Composable
private fun ShortcutTile(
    item: Rc21ShortcutItem,
    index: Int,
    locked: Boolean,
    onOpen: (Rc21ShortcutItem) -> Unit,
    dragCoordinator: LibraryDragCoordinator,
    scrollOrientation: Orientation,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onReturnToAll: () -> Unit = {},
    selectionMode: Boolean = false,
    selectedBookIds: Set<String> = emptySet(),
    selectedCollectionIds: Set<String> = emptySet(),
    onToggleBook: (String) -> Unit = {},
    onToggleCollection: (String) -> Unit = {},
    conflictSignal: Int = 0,
    conflictTargetKey: String? = null,
    expanded: Boolean = false,
) {
    val dropActive = dragCoordinator.folderTargetId == item.id || dragCoordinator.bookTargetId == item.id
    val selected = item.book?.id in selectedBookIds || item.id in selectedCollectionIds
    val selectable = item.book != null || item.kind == ShortcutKind.COLLECTION
    val scale by animateFloatAsState(
        targetValue = if (dropActive) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "ShortcutFolderTargetScale",
    )
    val container by animateColorAsState(
        targetValue = if (dropActive || selected || active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(AtlasMotion.FADE_IN_MS),
        label = "ShortcutFolderTargetColor",
    )
    val outline by animateColorAsState(
        targetValue = if (dropActive || selected || active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(AtlasMotion.FADE_IN_MS),
        label = "ShortcutFolderTargetBorder",
    )
    val payload = item.book?.let { shortcutBookDragPayload(it.id, selectedBookIds) } ?: shortcutDragPayload(item.id)
    val subjectKey = item.book?.let { "shortcut-book:${it.id}" } ?: "shortcut:${item.id}"
    val selectionKey = item.book?.let { "book:${it.id}" } ?: "shortcut:${item.id}"
    val handleClick = {
        when {
            selectionMode && item.book != null -> onToggleBook(item.book.id)
            selectionMode && item.kind == ShortcutKind.COLLECTION -> onToggleCollection(item.id)
            selectionMode && !selectable -> Unit
            active -> onReturnToAll()
            else -> onOpen(item)
        }
    }
    val dragGestureEnabled = !locked && (!selectionMode || selected)
    Surface(
        modifier = modifier
            .selectionShake(if (conflictTargetKey == selectionKey) conflictSignal else 0)
            .then(if (expanded) Modifier.aspectRatio(3f / 4f) else Modifier.height(shortcutTileHeight))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .libraryItemDropTarget(
                id = item.id,
                index = index,
                kind = when (item.kind) {
                    ShortcutKind.COLLECTION -> LibraryDropItemKind.COLLECTION
                    ShortcutKind.BOOK -> LibraryDropItemKind.BOOK
                    else -> LibraryDropItemKind.ITEM
                },
                bookId = item.book?.id,
                coordinator = dragCoordinator,
            )
            .then(
                if (dragGestureEnabled) Modifier.libraryDragSource(
                    payload = payload,
                    subjectKey = subjectKey,
                    enabled = true,
                    coordinator = dragCoordinator,
                    draggedBookIds = payloadBookIds(payload),
                    startDragOnLongPress = selectionMode && selected,
                    scrollOrientation = scrollOrientation,
                    canRemove = true,
                    libraryReorderSource = false,
                    onTap = handleClick,
                ) else Modifier.combinedClickable(
                    role = Role.Button,
                    onClick = handleClick,
                    onLongClick = { dragCoordinator.onLongPress(subjectKey) },
                ),
            )
            .semantics(mergeDescendants = true) {
                contentDescription = when {
                    active -> "${item.label}，当前视图，返回全部书籍"
                    locked -> item.label
                    else -> "${item.label}，长按多选，移动可拖动排序"
                }
                if (selected) stateDescription = "已选择"
                role = Role.Button
            },
        shape = MaterialTheme.shapes.small,
        color = container,
        border = BorderStroke(if (dropActive || selected || active) 2.dp else 1.dp, outline),
        shadowElevation = if (dropActive) 8.dp else 0.dp,
    ) {
        if (expanded) ExpandedShortcutTileContent(item, active) else ShortcutTileContent(item, active)
    }
}

@Composable
private fun ShortcutTileContent(item: Rc21ShortcutItem, active: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(AtlasSpacing.Xs), horizontalAlignment = Alignment.Start) {
        when {
            item.book != null -> AtlasCoverImage(
                cover = item.book.cover,
                title = item.book.title,
                modifier = Modifier.fillMaxWidth().height(76.dp),
            )
            active -> ShortcutIconPreview(AtlasIcons.Back, Modifier.fillMaxWidth().height(76.dp))
            item.kind == ShortcutKind.COLLECTION && item.collectionBooks.isNotEmpty() -> CollectionCoverStack(
                item,
                Modifier.fillMaxWidth().height(76.dp),
            )
            else -> ShortcutIconPreview(
                if (item.kind == ShortcutKind.COLLECTION) AtlasIcons.Folder else item.icon,
                Modifier.fillMaxWidth().height(76.dp),
            )
        }
        Text(
            item.label,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ExpandedShortcutTileContent(item: Rc21ShortcutItem, active: Boolean) {
    val eInk = LocalAtlasEnvironment.current.eInk
    Box(Modifier.fillMaxSize()) {
        when {
            item.book != null -> AtlasCoverImage(
                cover = item.book.cover,
                title = item.book.title,
                modifier = Modifier.fillMaxSize(),
            )
            active -> ShortcutIconPreview(AtlasIcons.Back, Modifier.fillMaxSize())
            item.kind == ShortcutKind.COLLECTION && item.collectionBooks.isNotEmpty() -> CollectionCoverStack(
                item,
                Modifier.fillMaxSize(),
                expanded = true,
            )
            else -> ShortcutIconPreview(
                if (item.kind == ShortcutKind.COLLECTION) AtlasIcons.Folder else item.icon,
                Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    if (eInk) Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
                    else Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))),
                )
                .padding(start = AtlasSpacing.Sm, top = 24.dp, end = AtlasSpacing.Sm, bottom = AtlasSpacing.Xs),
        ) {
            Text(
                item.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = if (eInk) MaterialTheme.colorScheme.onSurface else Color.White,
            )
            Text(
                item.supporting,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = if (eInk) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.88f),
            )
        }
    }
}

@Composable
private fun ShortcutIconPreview(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun CollectionCoverStack(
    item: Rc21ShortcutItem,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val previews = item.collectionBooks.take(3)
    val previewWidth = if (expanded) 54.dp else 30.dp
    val previewHeight = if (expanded) 72.dp else 40.dp
    val previewOffset = if (expanded) 16f else 10f
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag("shortcut-collection-stack-${item.id}"),
        contentAlignment = Alignment.Center,
    ) {
        previews.forEachIndexed { index, book ->
            val centeredIndex = index - (previews.lastIndex / 2f)
            AtlasCoverImage(
                cover = book.cover,
                title = book.title,
                modifier = Modifier
                    .width(previewWidth)
                    .height(previewHeight)
                    .offset(x = (centeredIndex * previewOffset).dp, y = (index - 1).dp),
            )
        }
        if (!expanded) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(item.supporting, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ShortcutInsertionGap(modifier: Modifier = Modifier, expanded: Boolean = false) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    AnimatedVisibility(
        visible = revealed,
        modifier = modifier,
        enter = expandHorizontally(
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 430f),
            expandFrom = Alignment.CenterHorizontally,
        ) + fadeIn(tween(AtlasMotion.FADE_IN_MS)),
        exit = fadeOut(tween(AtlasMotion.FADE_OUT_MS)),
    ) {
        Surface(
            modifier = if (expanded) Modifier.fillMaxWidth().aspectRatio(3f / 4f) else Modifier.width(shortcutTileWidth).height(shortcutTileHeight),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.66f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) {
            Column(
                Modifier.fillMaxSize().padding(AtlasSpacing.Xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(AtlasIcons.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                Text("放到这里", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun ShortcutDragGhost(
    activeItem: Rc21ShortcutItem?,
    dragCoordinator: LibraryDragCoordinator,
    bookLayout: AtlasLayout,
    modifier: Modifier = Modifier,
) {
    var displayedItem by remember { mutableStateOf<Rc21ShortcutItem?>(null) }
    var displayedFromLibrary by remember { mutableStateOf(false) }
    var displayedLayout by remember { mutableStateOf(bookLayout) }
    var displayedBatchCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(activeItem, dragCoordinator.activeSubjectKey, bookLayout) {
        if (activeItem != null) {
            displayedItem = activeItem
            displayedFromLibrary = dragCoordinator.activeSubjectKey?.startsWith("book:") == true
            displayedLayout = bookLayout
            displayedBatchCount = dragCoordinator.activeBookIds.size.coerceAtLeast(1)
        } else {
            delay(AtlasMotion.FADE_OUT_MS.toLong())
            displayedItem = null
        }
    }
    val density = LocalDensity.current
    val host = dragCoordinator.hostTopLeft()
    val pointer = dragCoordinator.ghostPositionInWindow
    val previewSize = if (displayedFromLibrary) libraryDragPreviewSize(displayedLayout) else androidx.compose.ui.unit.DpSize(shortcutTileWidth, shortcutTileHeight)
    val widthPx = with(density) { previewSize.width.toPx() }
    val fingerOffsetPx = with(density) { (previewSize.height * 0.72f).toPx() }
    val previewModifier = Modifier
        .offset {
            IntOffset(
                x = (pointer.x - host.x - widthPx / 2f).roundToInt(),
                y = (pointer.y - host.y - fingerOffsetPx).roundToInt(),
            )
        }
        .graphicsLayer { alpha = 0.88f }
        .clearAndSetSemantics { }
        .testTag(if (displayedFromLibrary) "library-book-drag-ghost" else "shortcut-drag-ghost")
    Box(modifier) {
        AnimatedVisibility(
            visible = activeItem != null,
            enter = fadeIn(tween(AtlasMotion.FADE_IN_MS)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
            ),
            exit = fadeOut(tween(AtlasMotion.FADE_OUT_MS)) + scaleOut(targetScale = 0.92f),
        ) {
            displayedItem?.let { item ->
                if (displayedFromLibrary && item.book != null) {
                    LibraryBookDragPreview(
                        book = item.book,
                        layout = displayedLayout,
                        batchCount = displayedBatchCount,
                        modifier = previewModifier,
                    )
                } else {
                    Surface(
                        modifier = previewModifier.width(shortcutTileWidth).height(shortcutTileHeight),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        shadowElevation = 14.dp,
                    ) {
                        ShortcutTileContent(item)
                    }
                }
            }
        }
    }
}
@Composable
internal fun ShortcutDeleteDropTarget(
    visible: Boolean,
    label: String,
    active: Boolean,
    dragCoordinator: LibraryDragCoordinator,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "ShortcutDeleteTargetScale",
    )
    val container by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(AtlasMotion.FADE_IN_MS),
        label = "ShortcutDeleteTargetColor",
    )
    val outline by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        animationSpec = tween(AtlasMotion.FADE_IN_MS),
        label = "ShortcutDeleteTargetOutline",
    )
    Box(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .offset(y = (-16).dp)
                .width(184.dp)
                .height(64.dp)
                .libraryDeleteDropTarget(dragCoordinator)
                .testTag("shortcut-delete-drop-target"),
        ) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(AtlasMotion.FADE_IN_MS)) + scaleIn(
                    initialScale = 0.9f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                ),
                exit = fadeOut(tween(AtlasMotion.FADE_OUT_MS)) + scaleOut(targetScale = 0.9f),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .semantics {
                            contentDescription = if (active) "松开以$label" else "拖到这里$label"
                            stateDescription = if (active) "删除目标已选中" else "删除目标"
                        },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = container,
                    contentColor = if (active) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(if (active) 2.dp else 1.dp, outline),
                    shadowElevation = if (active) 12.dp else 6.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = AtlasSpacing.Md),
                        horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AtlasIcons.Delete, contentDescription = null, modifier = Modifier.size(28.dp))
                        Text(
                            if (active) "松开以$label" else label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShortcutShelf(
    items: List<Rc21ShortcutItem>,
    expanded: Boolean,
    locked: Boolean,
    editing: Boolean,
    onExpanded: (Boolean) -> Unit,
    onLocked: (Boolean) -> Unit,
    onEditing: (Boolean) -> Unit,
    onOpen: (Rc21ShortcutItem) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (String) -> Unit,
    onReturnToAll: () -> Unit = {},
    onViewAll: () -> Unit,
    onCreate: () -> Unit = {},
    activeView: AtlasLibraryView = AtlasLibraryView.ALL,
    selectionKind: LibrarySelectionKind? = null,
    selectedBookIds: Set<String> = emptySet(),
    selectedCollectionIds: Set<String> = emptySet(),
    onToggleBook: (String) -> Unit = {},
    onToggleCollection: (String) -> Unit = {},
    conflictSignal: Int = 0,
    conflictTargetKey: String? = null,
    dragCoordinator: LibraryDragCoordinator,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    if (!eInk) {
        val dragActive = dragCoordinator.activePayload != null
        val dropActive = dragActive && dragCoordinator.isOverShelf
        val batchBookDrop = dragCoordinator.activeBookIds.size > 1
        val folderTargetLabel = items.firstOrNull { it.id == dragCoordinator.folderTargetId }?.label
        val gapIndex = dragCoordinator.rootInsertionIndex
            .takeIf { dragActive && dropActive && folderTargetLabel == null && it >= 0 }
            ?.coerceIn(0, items.size)
        val dropHint = when {
            folderTargetLabel != null -> "松开以加入「$folderTargetLabel」"
            gapIndex != null && batchBookDrop -> "松开以新建收藏夹并放到第 ${gapIndex + 1} 位"
            gapIndex != null -> "松开以放到快捷书架第 ${gapIndex + 1} 位"
            dragActive && batchBookDrop -> "拖到快捷书架根目录新建收藏夹，或拖入现有收藏夹"
            dragActive -> "拖到快捷书架根目录或收藏夹"
            else -> null
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .libraryShelfDropTarget(dragCoordinator)
                .background(if (dropActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = AtlasSpacing.Md, end = AtlasSpacing.Xs),
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
                AtlasIconButton(AtlasIcons.Add, "新建收藏夹", onCreate)
                AtlasIconButton(AtlasIcons.ViewAll, "查看全部快捷书架", onViewAll)
                AtlasIconButton(
                    if (locked) AtlasIcons.Lock else AtlasIcons.LockOpen,
                    if (locked) "快捷书架已锁定，点按解锁" else "快捷书架未锁定，点按锁定",
                    { onLocked(!locked) },
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = AtlasSpacing.Md,
                    end = AtlasSpacing.Md,
                    bottom = AtlasSpacing.Xs,
                ),
                horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
            ) {
                val visualCount = items.size + if (gapIndex != null) 1 else 0
                items(
                    count = visualCount,
                    key = { visualIndex ->
                        if (visualIndex == gapIndex) "shortcut-root-drop-gap"
                        else {
                            val itemIndex = if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex
                            items[itemIndex].id
                        }
                    },
                ) { visualIndex ->
                    if (visualIndex == gapIndex) {
                        ShortcutInsertionGap(Modifier.animateItem())
                    } else {
                        val itemIndex = if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex
                        val item = items[itemIndex]
                        ShortcutTile(
                            item = item,
                            index = itemIndex,
                            active = item.view == activeView && activeView != AtlasLibraryView.ALL,
                            locked = locked,
                            conflictTargetKey = conflictTargetKey,
                            onOpen = onOpen,
                            onReturnToAll = onReturnToAll,
                            modifier = Modifier.width(shortcutTileWidth).animateItem(),
                            dragCoordinator = dragCoordinator,
                            scrollOrientation = Orientation.Horizontal,
                            selectionMode = selectionKind != null,
                            selectedBookIds = selectedBookIds,
                            selectedCollectionIds = selectedCollectionIds,
                            onToggleBook = onToggleBook,
                            onToggleCollection = onToggleCollection,
                            conflictSignal = conflictSignal,
                        )
                    }
                }
            }
        }
        return
    }

    val visible = if (expanded) items else items.take(4)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Xs),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = AtlasSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("快捷书架", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                AtlasIconButton(if (locked) AtlasIcons.Lock else AtlasIcons.LockOpen, if (locked) "快捷书架已锁定，点按解锁" else "快捷书架未锁定，点按锁定", { onLocked(!locked) })
                AtlasIconButton(AtlasIcons.Edit, if (editing) "完成编辑" else "编辑快捷书架", { onEditing(!editing) })
                AtlasIconButton(if (expanded) AtlasIcons.Collapse else AtlasIcons.Expand, if (expanded) "完全收起" else "展开快捷书架", { onExpanded(!expanded) })
            }
            if (visible.isNotEmpty()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = max(4, floor(maxWidth.value / 124f).toInt())
                    Column(Modifier.padding(horizontal = AtlasSpacing.Sm, vertical = AtlasSpacing.Xs)) {
                        visible.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs)) {
                                row.forEach { item ->
                                    val index = items.indexOfFirst { it.id == item.id }
                                    val active = item.view == activeView && activeView != AtlasLibraryView.ALL
                                    Surface(
                                        modifier = Modifier.weight(1f).heightIn(min = if (editing) 118.dp else 88.dp),
                                        shape = MaterialTheme.shapes.small,
                                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(if (active) 2.dp else 1.5.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                        onClick = { if (!editing) onOpen(item) },
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(AtlasSpacing.Sm), horizontalAlignment = Alignment.Start) {
                                            Icon(if (active) AtlasIcons.Back else item.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Text(item.label, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                            Text(item.supporting, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                AtlasIconButton(AtlasIcons.MoveEarlier, "前移 ${item.label}", { onMove(index, index - 1) }, enabled = index > 0)
                                                AtlasIconButton(AtlasIcons.Close, "移出快捷书架 ${item.label}", { onRemove(item.id) })
                                                AtlasIconButton(AtlasIcons.MoveLater, "后移 ${item.label}", { onMove(index, index + 1) }, enabled = index < items.lastIndex)
                                            }
                                        }
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShortcutExpandedGrid(
    items: List<Rc21ShortcutItem>,
    locked: Boolean,
    onOpen: (Rc21ShortcutItem) -> Unit,
    dragCoordinator: LibraryDragCoordinator,
    modifier: Modifier = Modifier,
    onReturnToAll: () -> Unit = {},
    activeView: AtlasLibraryView = AtlasLibraryView.ALL,
    selectionKind: LibrarySelectionKind? = null,
    selectedBookIds: Set<String> = emptySet(),
    selectedCollectionIds: Set<String> = emptySet(),
    onToggleBook: (String) -> Unit = {},
    onToggleCollection: (String) -> Unit = {},
    conflictSignal: Int = 0,
    conflictTargetKey: String? = null,
    acceptBookAtRoot: Boolean = true,
) {
    val gapIndex = if (dragCoordinator.activePayload != null && dragCoordinator.rootInsertionIndex >= 0) {
        dragCoordinator.rootInsertionIndex.coerceIn(0, items.size)
    } else {
        null
    }
    val visualCount = items.size + if (gapIndex != null) 1 else 0
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.libraryShelfDropTarget(dragCoordinator, acceptBookAtRoot),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(AtlasSpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
    ) {
        items(
            count = visualCount,
            key = { visualIndex ->
                if (visualIndex == gapIndex) "shortcut-expanded-drop-gap"
                else {
                    val itemIndex = if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex
                    items[itemIndex].id
                }
            },
        ) { visualIndex ->
            if (visualIndex == gapIndex) {
                ShortcutInsertionGap(Modifier.animateItem(), expanded = true)
            } else {
                val itemIndex = if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex
                val item = items[itemIndex]
                ShortcutTile(
                    onReturnToAll = onReturnToAll,
                    item = item,
                    index = itemIndex,
                    active = item.view == activeView && activeView != AtlasLibraryView.ALL,
                    locked = locked,
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    expanded = true,
                    dragCoordinator = dragCoordinator,
                    scrollOrientation = Orientation.Vertical,
                    selectionMode = selectionKind != null,
                    selectedBookIds = selectedBookIds,
                    selectedCollectionIds = selectedCollectionIds,
                    onToggleBook = onToggleBook,
                    onToggleCollection = onToggleCollection,
                    conflictSignal = conflictSignal,
                    conflictTargetKey = conflictTargetKey,
                )
            }
        }
    }
}

@Composable
internal fun ShortcutAllPage(
    onReturnToAll: () -> Unit = {},
    items: List<Rc21ShortcutItem>,
    locked: Boolean,
    onDismiss: () -> Unit,
    onOpen: (Rc21ShortcutItem) -> Unit,
    activeView: AtlasLibraryView = AtlasLibraryView.ALL,
    selectionKind: LibrarySelectionKind? = null,
    selection: AtlasSelectionBar? = null,
    selectedBookIds: Set<String> = emptySet(),
    selectedCollectionIds: Set<String> = emptySet(),
    onClearSelection: () -> Unit = {},
    onToggleBook: (String) -> Unit = {},
    onToggleCollection: (String) -> Unit = {},
    conflictSignal: Int = 0,
    conflictTargetKey: String? = null,
    dragCoordinator: LibraryDragCoordinator,
) {
    DisposableEffect(dragCoordinator) {
        onDispose { dragCoordinator.clearItemRegistrations() }
    }
    val dismissOrClear = if (selection != null) onClearSelection else onDismiss
    Dialog(
        onDismissRequest = dismissOrClear,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                AtlasTopBar(
                    title = "全部快捷内容",
                    subtitle = "${items.size} 项",
                    onUp = dismissOrClear,
                    selection = selection,
                    actionBudgetOverride = 3,
                )
                ShortcutExpandedGrid(
                    items = items,
                    locked = locked,
                    onOpen = onOpen,
                    dragCoordinator = dragCoordinator,
                    modifier = Modifier.fillMaxSize(),
                    onReturnToAll = onReturnToAll,
                    activeView = activeView,
                    selectionKind = selectionKind,
                    selectedBookIds = selectedBookIds,
                    selectedCollectionIds = selectedCollectionIds,
                    onToggleBook = onToggleBook,
                    onToggleCollection = onToggleCollection,
                    conflictSignal = conflictSignal,
                    conflictTargetKey = conflictTargetKey,
                )
            }
        }
    }
}
