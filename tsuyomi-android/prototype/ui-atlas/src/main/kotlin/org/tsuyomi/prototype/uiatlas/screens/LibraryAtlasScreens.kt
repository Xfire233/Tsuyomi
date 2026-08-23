/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasFeatureIntroduction
import org.tsuyomi.prototype.uiatlas.components.AtlasChip
import org.tsuyomi.prototype.uiatlas.components.AtlasCoverImage
import org.tsuyomi.prototype.uiatlas.components.AtlasIconButton
import org.tsuyomi.prototype.uiatlas.components.LibraryBookDragPreview
import org.tsuyomi.prototype.uiatlas.components.LibraryBookInsertionGap
import org.tsuyomi.prototype.uiatlas.components.LibraryBookInteractionCapabilities
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortDirection
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortMode
import org.tsuyomi.prototype.uiatlas.components.libraryDragPreviewSize
import org.tsuyomi.prototype.uiatlas.components.currentLayoutIcon
import org.tsuyomi.prototype.uiatlas.components.layoutToggleContentDescription
import org.tsuyomi.prototype.uiatlas.components.nextAtlasLayout
import org.tsuyomi.prototype.uiatlas.components.summary
import org.tsuyomi.prototype.uiatlas.components.orderedForLibrary
import org.tsuyomi.prototype.uiatlas.components.LibraryDragCoordinator
import org.tsuyomi.prototype.uiatlas.components.LibraryDropDestination
import org.tsuyomi.prototype.uiatlas.components.LibraryDropItemKind
import org.tsuyomi.prototype.uiatlas.components.libraryBookDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryContentDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryDeleteDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryDragOverlayHost
import org.tsuyomi.prototype.uiatlas.components.libraryDragSource
import org.tsuyomi.prototype.uiatlas.components.libraryItemDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryShelfDropTarget
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasIdentityOption
import org.tsuyomi.prototype.uiatlas.components.AtlasInfoBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationPhase
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationStatus
import org.tsuyomi.prototype.uiatlas.components.AtlasOverflowItem
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasSelectionBar
import org.tsuyomi.prototype.uiatlas.components.AtlasStateKind
import org.tsuyomi.prototype.uiatlas.components.AtlasStateView
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarSelector
import org.tsuyomi.prototype.uiatlas.components.BookGridCard
import org.tsuyomi.prototype.uiatlas.components.BookListItemRow
import org.tsuyomi.prototype.uiatlas.components.CompactBookListItem
import org.tsuyomi.prototype.uiatlas.components.SourceIdentityBand
import org.tsuyomi.prototype.uiatlas.fixtures.LibraryAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasFamily
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.runtime.PrototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasMotion
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

/** Library-family atlas routes #1–11. Book surfaces #12–14 are owned by SourceAtlasScreen. */
@Composable
fun LibraryAtlasScreen(context: AtlasContext, modifier: Modifier = Modifier) {
    when (context.route) {
        AtlasRoute.LIBRARY, AtlasRoute.LIBRARY_SYSTEM -> LibraryRoot(context, modifier)
        AtlasRoute.LIBRARY_HISTORY -> LibraryHistory(context, modifier)
        AtlasRoute.LIBRARY_UPDATES -> LibraryUpdates(context, modifier)
        AtlasRoute.LIBRARY_COLLECTION,
        AtlasRoute.LIBRARY_COLLECTION_CHILD,
        AtlasRoute.LIBRARY_COLLECTION_GRANDCHILD,
        -> CollectionDetail(context, modifier)
        AtlasRoute.LIBRARY_COLLECTION_RULE -> CollectionRule(context, modifier)
        AtlasRoute.LIBRARY_TAGS -> LibraryTags(context, modifier)
        AtlasRoute.LIBRARY_MIRROR,
        AtlasRoute.LIBRARY_MIRROR_FOLDER,
        AtlasRoute.LIBRARY_MIRROR_SUBFOLDER,
        -> LibraryMirror(context, modifier)
        else -> Box(modifier.fillMaxSize())
    }
}

private fun AtlasContext.isVariant(id: Char, option: String): Boolean =
    variant?.id == id && variant.option == option

private val AtlasPageState.showsContent: Boolean
    get() = when (this) {
        AtlasPageState.LOADING, AtlasPageState.EMPTY, AtlasPageState.ERROR -> false
        else -> true
    }

@Composable
private fun StateOrContent(
    state: AtlasPageState,
    emptyTitle: String,
    emptyMessage: String?,
    errorTitle: String,
    errorMessage: String?,
    emptyAction: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val repository = prototypeRepository()
    when (state) {
        AtlasPageState.LOADING -> AtlasStateView(AtlasStateKind.LOADING, AtlasStrings.LOADING)
        AtlasPageState.EMPTY -> AtlasStateView(
            AtlasStateKind.EMPTY,
            emptyTitle,
            message = emptyMessage,
            actionLabel = emptyAction,
            onAction = onEmptyAction,
        )
        AtlasPageState.ERROR -> AtlasStateView(
            AtlasStateKind.ERROR,
            errorTitle,
            message = errorMessage,
            actionLabel = AtlasStrings.RETRY,
            onAction = { repository.record("RetryRequested", "library-state", "queued") },
        )
        else -> content()
    }
}

@Composable
private fun OverlayState(
    state: AtlasPageState,
    mutation: AtlasMutationStatus?,
    unresolved: AtlasMutationStatus? = null,
) {
    val repository = prototypeRepository()
    when (state) {
        AtlasPageState.OFFLINE -> AtlasInfoBanner(
            AtlasBanner(
                title = AtlasStrings.OFFLINE_TITLE,
                message = "展示本地缓存内容；联网后可刷新。",
                actionLabel = AtlasStrings.RETRY,
                onAction = { repository.record("RetryRequested", "library-offline", "queued") },
            ),
        )
        AtlasPageState.REFRESHING -> AtlasInfoBanner(AtlasBanner(AtlasStrings.REFRESHING_TITLE))
        AtlasPageState.MUTATION -> mutation?.let { AtlasMutationBanner(it) }
        AtlasPageState.UNRESOLVED -> (unresolved ?: mutation)?.let { AtlasMutationBanner(it) }
        else -> mutation?.let { AtlasMutationBanner(it) }
    }
}

@Composable
private fun FullDialog(
    title: String,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = !destructive),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = if (eInk) BorderStroke(1.5.dp, AtlasEInkPalette.Ink) else null,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(AtlasSpacing.Lg),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(AtlasSpacing.Md))
                content()
            }
        }
    }
}

@Composable
private fun DialogButtons(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AtlasSpacing.Lg),
        horizontalArrangement = Arrangement.End,
    ) {
        AtlasButton(AtlasStrings.CANCEL, onDismiss, style = AtlasButtonStyle.TEXT)
        Spacer(Modifier.size(AtlasSpacing.Sm))
        AtlasButton(confirmLabel, onConfirm)
    }
}

@Composable
private fun LibrarySortDialog(
    mode: LibraryBookSortMode,
    direction: LibraryBookSortDirection,
    onModeChange: (LibraryBookSortMode) -> Unit,
    onDirectionChange: (LibraryBookSortDirection) -> Unit,
    onDismiss: () -> Unit,
    allowCustom: Boolean = true,
) {
    FullDialog("排序", onDismiss) {
        Text("排序依据", style = MaterialTheme.typography.titleMedium)
        LibraryBookSortMode.entries
            .filter { allowCustom || it != LibraryBookSortMode.CUSTOM }
            .forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.RadioButton) { onModeChange(option) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == option, onClick = { onModeChange(option) })
                    Text(option.label, modifier = Modifier.padding(start = AtlasSpacing.Sm))
                }
            }
        if (mode != LibraryBookSortMode.CUSTOM) {
            Text(
                "顺序",
                modifier = Modifier.padding(top = AtlasSpacing.Md),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                LibraryBookSortDirection.entries.forEach { option ->
                    FilterChip(
                        selected = direction == option,
                        onClick = { onDirectionChange(option) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = AtlasSpacing.Lg),
            horizontalArrangement = Arrangement.End,
        ) {
            AtlasButton("完成", onDismiss)
        }
    }
}

private data class PageSlice<T>(
    val items: List<T>,
    val page: Int,
    val pages: Int,
    val setPage: (Int) -> Unit,
)

@Composable
private fun <T> pageSlice(key: String, values: List<T>, size: Int): PageSlice<T> {
    var page by rememberSaveable(key) { mutableIntStateOf(0) }
    val pages = max(1, (values.size + size - 1) / size)
    LaunchedEffect(key, pages) { if (page >= pages) page = pages - 1 }
    val clamped = page.coerceIn(0, pages - 1)
    return PageSlice(
        values.drop(clamped * size).take(size),
        clamped,
        pages,
    ) { page = it.coerceIn(0, pages - 1) }
}

@Composable
private fun PaginationFooter(page: Int, pages: Int, setPage: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (LocalAtlasEnvironment.current.eInk) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = AtlasSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            AtlasIconButton(AtlasIcons.Prev, "上一页", { setPage(page - 1) }, enabled = page > 0)
            Text(
                AtlasStrings.pageOf(page + 1, pages),
                modifier = Modifier
                    .padding(horizontal = AtlasSpacing.Lg)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge,
            )
            AtlasIconButton(
                AtlasIcons.Next,
                "下一页",
                { setPage(page + 1) },
                enabled = page < pages - 1,
            )
        }
    }
}

private fun selectionTopBar(
    selected: Set<String>,
    allIds: Set<String>,
    close: () -> Unit,
    setAll: (Set<String>) -> Unit,
    bulkIcon: androidx.compose.ui.graphics.vector.ImageVector,
    bulkLabel: String,
    bulkAction: () -> Unit,
): AtlasSelectionBar = AtlasSelectionBar(
    count = selected.size,
    onClose = close,
    allSelected = allIds.isNotEmpty() && selected.containsAll(allIds),
    onToggleAll = {
        setAll(if (allIds.isNotEmpty() && selected.containsAll(allIds)) emptySet() else allIds)
    },
    bulkActions = listOf(AtlasTopBarAction(bulkIcon, bulkLabel, bulkAction)),
)

@Composable
private fun BookSurface(
    context: AtlasContext,
    books: List<AtlasBook>,
    layout: AtlasLayout,
    selected: Set<String>,
    toggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectionActive: Boolean = false,
    selectionConflictTarget: String? = null,
    selectionConflictSignal: Int = 0,
    showSource: Boolean = false,
    continueView: Boolean = false,
    interaction: LibraryBookInteractionCapabilities = LibraryBookInteractionCapabilities(),
    denseGrid: Boolean = false,
    dragCoordinator: LibraryDragCoordinator? = null,
    dragDescriptionSuffix: String = "长按多选，移动可拖动至快捷书架",
    onLongPress: ((AtlasBook) -> Unit)? = null,
    onRemoveRequest: ((AtlasBook) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    val dragEnabled = interaction.drag && !eInk && dragCoordinator != null
    val reorderEnabled = interaction.reorder && dragEnabled
    val gapIndex = dragCoordinator?.let { coordinator ->
        if (
            reorderEnabled && coordinator.activeSubjectKey?.startsWith("book:") == true &&
            coordinator.activeBookIds.isNotEmpty() && coordinator.libraryInsertionIndex >= 0
        ) coordinator.libraryInsertionIndex.coerceIn(0, books.size) else null
    }
    val onBook: (AtlasBook) -> Unit = { book ->
        if (selectionActive) toggle(book.id) else navigation.navigate(AtlasRoute.BOOK_DETAIL)
    }
    val trailing: @Composable (AtlasBook) -> Unit = { book ->
        if (continueView) {
            if (context.isVariant('B', "b")) RowActionMenu(book, onRemoveRequest)
            else AtlasButton("继续", {
                repository.record("ContinueReading", book.id, "success")
                navigation.navigate(AtlasRoute.BOOK_READER)
            }, style = AtlasButtonStyle.TEXT)
        }
    }
    val itemModifier: @Composable (AtlasBook) -> Modifier = { book ->
        val dragModifier = dragCoordinator?.let { coordinator ->
            val index = books.indexOfFirst { it.id == book.id }
            val payload = bookDragPayload(book.id, selected)
            Modifier
                .libraryBookDropTarget(book.id, index, coordinator)
                .libraryDragSource(
                    payload = payload,
                    subjectKey = "book:${book.id}",
                    enabled = dragEnabled,
                    coordinator = coordinator,
                    draggedBookIds = payloadBookIds(payload),
                    startDragOnLongPress = selectionActive && book.id in selected,
                    canRemove = true,
                    libraryReorderSource = true,
                    tapStateKey = if (selectionActive) 1 else 0,
                    bookId = book.id,
                    onTap = { onBook(book) },
                )
        } ?: Modifier
        dragModifier
    }
    val longPressFor: (AtlasBook) -> (() -> Unit)? = { book ->
        if (dragEnabled || !interaction.longPress) null else ({ onLongPress?.invoke(book) ?: toggle(book.id) })
    }
    val row: @Composable (AtlasBook) -> Unit = { book ->
        if (layout == AtlasLayout.COMPACT) {
            CompactBookListItem(
                book = book,
                onClick = { onBook(book) },
                modifier = itemModifier(book).then(
                    if (selectionConflictTarget == "book:${book.id}") Modifier.selectionShake(selectionConflictSignal) else Modifier,
                ),
                selected = book.id in selected,
                onLongClick = longPressFor(book),
                trailing = if (continueView) ({ trailing(book) }) else null,
                gesturesHandledExternally = dragEnabled,
            )
        } else {
            BookListItemRow(
                book = book,
                onClick = { onBook(book) },
                modifier = itemModifier(book).then(
                    if (selectionConflictTarget == "book:${book.id}") Modifier.selectionShake(selectionConflictSignal) else Modifier,
                ),
                showSourceChip = showSource,
                selected = book.id in selected,
                onLongClick = longPressFor(book),
                trailing = if (continueView) ({ trailing(book) }) else null,
                gesturesHandledExternally = dragEnabled,
            )
        }
    }
    val card: @Composable (AtlasBook) -> Unit = { book ->
        BookGridCard(
            book = book,
            onClick = { onBook(book) },
            modifier = itemModifier(book).then(
                if (selectionConflictTarget == "book:${book.id}") Modifier.selectionShake(selectionConflictSignal) else Modifier,
            ),
            showSourceChip = showSource,
            selected = book.id in selected,
            onLongClick = longPressFor(book),
            dragDescription = if (dragEnabled) "${book.title}，$dragDescriptionSuffix" else null,
            gesturesHandledExternally = dragEnabled,
        )
    }
    if (layout != AtlasLayout.GRID) {
        if (eInk) {
            Column(modifier) {
                header?.invoke()
                books.forEach { row(it) }
            }
        } else {
            val visualCount = books.size + if (gapIndex != null) 1 else 0
            LazyColumn(
                modifier.then(if (dragCoordinator != null) Modifier.libraryContentDropTarget(dragCoordinator, reorderEnabled) else Modifier).testTag("library-book-surface"),
            ) {
                header?.let { headerContent -> item { headerContent() } }
                items(
                    count = visualCount,
                    key = { visualIndex ->
                        if (visualIndex == gapIndex) "library-book-drop-gap"
                        else {
                            val bookIndex = if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex
                            books[bookIndex].id
                        }
                    },
                ) { visualIndex ->
                    if (visualIndex == gapIndex) LibraryBookInsertionGap(layout, Modifier.animateItem().testTag("library-book-drop-gap"))
                    else {
                        val bookIndex = if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex
                        Box(if (reorderEnabled) Modifier.animateItem() else Modifier) { row(books[bookIndex]) }
                    }
                }
            }
        }
    } else if (eInk) {
        BoxWithConstraints(modifier) {
            val columns = when {
                maxWidth < 360.dp -> 2
                maxWidth < 600.dp -> 3
                else -> max(4, floor((maxWidth.value - 32f) / 150f).toInt())
            }
            Column {
                header?.invoke()
                books.chunked(columns).forEach { group ->
                    Row(
                        modifier = Modifier.padding(horizontal = AtlasSpacing.Md),
                        horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                    ) {
                        group.forEach { book -> Box(Modifier.weight(1f).padding(vertical = AtlasSpacing.Xs)) { card(book) } }
                        repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    } else {
        val spacing = if (denseGrid) AtlasSpacing.Sm else AtlasSpacing.Md
        val visualCount = books.size + if (gapIndex != null) 1 else 0
        LazyVerticalGrid(
            columns = if (denseGrid) GridCells.Fixed(3) else GridCells.Adaptive(120.dp),
            modifier = modifier.then(if (dragCoordinator != null) Modifier.libraryContentDropTarget(dragCoordinator, reorderEnabled) else Modifier).testTag("library-book-surface"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing),
            horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
        ) {
            header?.let { headerContent -> item(span = { GridItemSpan(maxLineSpan) }) { headerContent() } }
            items(
                count = visualCount,
                key = { visualIndex ->
                    if (visualIndex == gapIndex) "library-book-drop-gap"
                    else {
                        val bookIndex = if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex
                        books[bookIndex].id
                    }
                },
            ) { visualIndex ->
                if (visualIndex == gapIndex) LibraryBookInsertionGap(layout, Modifier.animateItem().testTag("library-book-drop-gap"))
                else {
                    val bookIndex = if (gapIndex != null && visualIndex > gapIndex) visualIndex - 1 else visualIndex
                    Box(if (reorderEnabled) Modifier.animateItem() else Modifier) { card(books[bookIndex]) }
                }
            }
        }
    }
}

@Composable
private fun RowActionMenu(book: AtlasBook, onRemoveRequest: ((AtlasBook) -> Unit)?) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    var open by remember(book.id) { mutableStateOf(false) }
    Box {
        AtlasIconButton(AtlasIcons.Overflow, "${book.title} 操作", { open = true })
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("继续阅读") }, {
                open = false
                repository.record("ContinueReading", book.id, "success")
                navigation.navigate(AtlasRoute.BOOK_READER)
            })
            DropdownMenuItem({ Text("查看详情") }, { open = false; navigation.navigate(AtlasRoute.BOOK_DETAIL) })
            onRemoveRequest?.let { request ->
                DropdownMenuItem({ Text("移出书架…") }, {
                    open = false
                    request(book)
                })
            }
        }
    }
}

private enum class ShortcutKind { SYSTEM, COLLECTION, MIRROR, BOOK }

private enum class LibrarySelectionKind { BOOK, COLLECTION }

private data class UserCollection(
    val id: String,
    val name: String,
    val bookIds: Set<String>,
    val parentId: String? = null,
)

private data class Rc21ShortcutItem(
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


private data class PendingCollectionCreation(
    val memberBookIds: Set<String> = emptySet(),
    val replacedShortcutIds: Set<String> = emptySet(),
    val insertIndex: Int? = null,
)

private sealed interface CollectionPickerRequest {
    data class Books(val ids: Set<String>) : CollectionPickerRequest
    data class Collections(val ids: Set<String>) : CollectionPickerRequest
}

private const val BOOK_DRAG_PREFIX = "tsuyomi:book:"
private const val BOOK_BATCH_DRAG_PREFIX = "tsuyomi:books:"
private const val SHORTCUT_BOOK_DRAG_PREFIX = "tsuyomi:shortcut-book:"
private const val SHORTCUT_BOOK_BATCH_DRAG_PREFIX = "tsuyomi:shortcut-books:"
private const val SHORTCUT_DRAG_PREFIX = "tsuyomi:shortcut:"

private val shortcutTileWidth = 80.dp
private val shortcutTileHeight = 116.dp

private fun bookDragPayload(bookId: String, selectedIds: Set<String> = emptySet()): String =
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

private fun payloadBookIds(payload: String): Set<String> = when {
    payload.startsWith(BOOK_DRAG_PREFIX) -> setOf(payload.removePrefix(BOOK_DRAG_PREFIX))
    payload.startsWith(BOOK_BATCH_DRAG_PREFIX) -> payload.removePrefix(BOOK_BATCH_DRAG_PREFIX).split(',').filter(String::isNotEmpty).toSet()
    payload.startsWith(SHORTCUT_BOOK_DRAG_PREFIX) -> setOf(payload.removePrefix(SHORTCUT_BOOK_DRAG_PREFIX))
    payload.startsWith(SHORTCUT_BOOK_BATCH_DRAG_PREFIX) -> payload.removePrefix(SHORTCUT_BOOK_BATCH_DRAG_PREFIX).split(',').filter(String::isNotEmpty).toSet()
    else -> emptySet()
}

private fun isShortcutBookPayload(payload: String): Boolean =
    payload.startsWith(SHORTCUT_BOOK_DRAG_PREFIX) || payload.startsWith(SHORTCUT_BOOK_BATCH_DRAG_PREFIX)

private fun shortcutDragPayload(shortcutId: String): String = "$SHORTCUT_DRAG_PREFIX$shortcutId"


private sealed interface LibraryRemovalRequest {
    data class Shortcut(val id: String, val label: String) : LibraryRemovalRequest
    data class Book(val book: AtlasBook) : LibraryRemovalRequest
    data class Books(val books: List<AtlasBook>) : LibraryRemovalRequest
    data class Collection(val id: String, val label: String) : LibraryRemovalRequest
    data class Collections(val items: List<Rc21ShortcutItem>) : LibraryRemovalRequest
}


private fun bookShortcut(book: AtlasBook): Rc21ShortcutItem = Rc21ShortcutItem(
    id = "book-${book.id}",
    label = book.title,
    supporting = book.progressLabel ?: "书籍",
    kind = ShortcutKind.BOOK,
    icon = AtlasIcons.Shelf,
    route = AtlasRoute.BOOK_DETAIL,
    book = book,
)

private fun loadUserCollections(repository: PrototypeRepository): List<UserCollection> =
    repository.stringList("library.collections.ids").map { id ->
        UserCollection(
            id = id,
            name = repository.string("library.collection.$id.name", "未命名收藏夹"),
            bookIds = repository.stringList("library.collection.$id.books").toSet(),
            parentId = repository.string("library.collection.$id.parent").takeIf(String::isNotBlank),
        )
    }

private fun createPersistedUserCollection(
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

private fun collectionShortcut(
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

private fun collectionFixtureShortcut(
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

private fun collectionRouteForDepth(depth: Int): AtlasRoute = when (depth) {
    0 -> AtlasRoute.LIBRARY_COLLECTION
    1 -> AtlasRoute.LIBRARY_COLLECTION_CHILD
    else -> AtlasRoute.LIBRARY_COLLECTION_GRANDCHILD
}

private fun mirrorRouteForDepth(depth: Int): AtlasRoute = when (depth) {
    0 -> AtlasRoute.LIBRARY_MIRROR
    1 -> AtlasRoute.LIBRARY_MIRROR_FOLDER
    else -> AtlasRoute.LIBRARY_MIRROR_SUBFOLDER
}

private fun List<LibraryAtlasFixtures.CollectionFixture>.findCollectionFixture(id: String): LibraryAtlasFixtures.CollectionFixture? =
    firstNotNullOfOrNull { fixture ->
        if (fixture.id == id) fixture else fixture.children.findCollectionFixture(id)
    }

private fun List<LibraryAtlasFixtures.MirrorNodeFixture>.findMirrorNode(id: String): LibraryAtlasFixtures.MirrorNodeFixture? =
    firstNotNullOfOrNull { node ->
        if (node.id == id) node else node.children.findMirrorNode(id)
    }

private fun shortcutItems(
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
private fun Modifier.selectionShake(signal: Int): Modifier {
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
                    canRemove = true,
                    libraryReorderSource = false,
                    bookId = item.book?.id,
                    tapStateKey = (if (active) 1 else 0) or (if (selectionMode) 2 else 0) or (if (selected) 4 else 0),
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
private fun ShortcutDragGhost(
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
private fun ShortcutDeleteDropTarget(
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
private fun ShortcutShelf(
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
private fun ShortcutExpandedGrid(
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
private fun ShortcutAllPage(
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

@Composable
private fun LibraryRoot(context: AtlasContext, modifier: Modifier) {
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val coroutineScope = rememberCoroutineScope()
    val standaloneNodePage = context.route == AtlasRoute.LIBRARY_SYSTEM
    val view = if (standaloneNodePage) context.libraryView else AtlasLibraryView.ALL
    val eInk = LocalAtlasEnvironment.current.eInk
    var layout by rememberSaveable(context.profile.name, context.layout?.name, runtime.persistent) {
        mutableStateOf(
            if (runtime.persistent) AtlasLayout.entries.firstOrNull { it.name == repository.string("library.layout") }
                ?: context.effectiveLayout else context.effectiveLayout,
        )
    }
    var sortMode by rememberSaveable(runtime.persistent) {
        mutableStateOf(
            if (runtime.persistent) LibraryBookSortMode.entries.firstOrNull { it.name == repository.string("library.sort") }
                ?: LibraryBookSortMode.CUSTOM else LibraryBookSortMode.CUSTOM,
        )
    }
    var sortDirection by rememberSaveable(runtime.persistent) {
        mutableStateOf(
            if (runtime.persistent) LibraryBookSortDirection.entries.firstOrNull {
                it.name == repository.string("library.sort.direction")
            } ?: LibraryBookSortDirection.ASCENDING else LibraryBookSortDirection.ASCENDING,
        )
    }
    val fixture = LibraryAtlasFixtures.viewFixture(view)
    val rawBooks = if (context.variant?.id == 'E') LibraryAtlasFixtures.variantEBooks else fixture.books
    var removedBookIds by remember(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.stringList("library.removed.bookIds").toSet() else emptySet())
    }
    var orderedBookIds by remember(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.stringList("library.order.all") else emptyList())
    }
    val baseBooks = rawBooks.filterNot { it.id in removedBookIds }
    val books = baseBooks.orderedForLibrary(sortMode, sortDirection, orderedBookIds)
    val shortcutBookCatalog = (LibraryAtlasFixtures.viewFixture(AtlasLibraryView.ALL).books + rawBooks)
        .distinctBy { it.id }
        .filterNot { it.id in removedBookIds }
    val shortcutBooksById = shortcutBookCatalog.associateBy(AtlasBook::id)
    var userCollections by remember(runtime.persistent) { mutableStateOf(loadUserCollections(repository)) }
    var hiddenShortcutIds by remember(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.stringList("library.shortcuts.hidden").toSet() else emptySet())
    }
    var shortcuts by remember(runtime.persistent) {
        mutableStateOf(
            shortcutItems(
                shortcutBookCatalog,
                if (runtime.persistent) repository.stringList("library.shortcuts.order") else emptyList(),
                hiddenShortcutIds,
                userCollections,
            ),
        )
    }
    var shortcutExpanded by rememberSaveable(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.boolean("library.shortcuts.expanded") else false)
    }
    var shortcutLocked by rememberSaveable(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.boolean("library.shortcuts.locked") else false)
    }
    val libraryReorderEnabled = view == AtlasLibraryView.ALL && sortMode == LibraryBookSortMode.CUSTOM && !shortcutLocked
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<AtlasMutationStatus?>(null) }
    val runLibrarySyncCheck: () -> Unit = {
        if (syncStatus?.phase != AtlasMutationPhase.WORKING) {
            coroutineScope.launch {
                syncStatus = AtlasMutationStatus(AtlasMutationPhase.WORKING, "正在同步书架并检查更新…")
                val result = runtime.scenarios.run("updates-check", "library")
                syncStatus = if (result.successful) {
                    AtlasMutationStatus(
                        AtlasMutationPhase.SUCCESS,
                        "同步完成 · 已检查 ${LibraryAtlasFixtures.updateEntries.size} 项更新",
                    )
                } else {
                    AtlasMutationStatus(
                        AtlasMutationPhase.ERROR,
                        "同步或检查更新未完成：${result.outcome}",
                    )
                }
            }
        }
    }
    var shortcutEditing by rememberSaveable { mutableStateOf(false) }
    var shortcutPageOpen by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<LibraryRemovalRequest?>(null) }
    var pendingCollectionCreation by remember { mutableStateOf<PendingCollectionCreation?>(null) }
    var collectionName by remember { mutableStateOf("") }
    var collectionPicker by remember { mutableStateOf<CollectionPickerRequest?>(null) }
    var selectionKind by remember { mutableStateOf<LibrarySelectionKind?>(null) }
    var selectedBookIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedCollectionIds by remember { mutableStateOf(emptySet<String>()) }
    var selectionConflictSignal by remember { mutableIntStateOf(0) }
    var selectionConflictTarget by remember { mutableStateOf<String?>(null) }
    val dragCoordinator = remember { LibraryDragCoordinator() }

    fun clearSelection() {
        selectionKind = null
        selectedBookIds = emptySet()
        selectedCollectionIds = emptySet()
        selectionConflictTarget = null
    }
    fun selectBooks(ids: Set<String>) {
        when (selectionKind) {
            null, LibrarySelectionKind.BOOK -> {
                selectionConflictTarget = null
                selectionKind = LibrarySelectionKind.BOOK
                selectedBookIds = ids
            }
            LibrarySelectionKind.COLLECTION -> {
                selectionConflictTarget = ids.firstOrNull()?.let { "book:$it" }
                selectionConflictSignal++
            }
        }
    }
    fun toggleBook(id: String) {
        when (selectionKind) {
            null, LibrarySelectionKind.BOOK -> {
                selectionConflictTarget = null
                selectionKind = LibrarySelectionKind.BOOK
                selectedBookIds = if (id in selectedBookIds) selectedBookIds - id else selectedBookIds + id
            }
            LibrarySelectionKind.COLLECTION -> {
                selectionConflictTarget = "book:$id"
                selectionConflictSignal++
            }
        }
    }
    fun toggleCollection(id: String) {
        if (id !in userCollections.map { it.id }) {
            selectionConflictTarget = "shortcut:$id"
            selectionConflictSignal++
            return
        }
        when (selectionKind) {
            null, LibrarySelectionKind.COLLECTION -> {
                selectionConflictTarget = null
                selectionKind = LibrarySelectionKind.COLLECTION
                selectedCollectionIds = if (id in selectedCollectionIds) selectedCollectionIds - id else selectedCollectionIds + id
            }
            LibrarySelectionKind.BOOK -> {
                selectionConflictTarget = "shortcut:$id"
                selectionConflictSignal++
            }
        }
    }
    fun setShortcuts(updated: List<Rc21ShortcutItem>, eventName: String) {
        shortcuts = updated
        repository.putStringList("library.shortcuts.order", updated.map { it.id }, eventName, "library.shortcuts")
    }
    fun removeShortcutItems(target: List<Rc21ShortcutItem>) {
        val ids = target.map { it.id }.toSet()
        if (ids.isEmpty()) return
        hiddenShortcutIds += ids
        repository.putStringList(
            "library.shortcuts.hidden",
            hiddenShortcutIds.sorted(),
            if (ids.size == 1) "ShortcutHidden" else "ShortcutsHidden",
            ids.sorted().joinToString(","),
        )
        setShortcuts(
            shortcuts.filterNot { it.id in ids },
            if (ids.size == 1) "ShortcutRemoved" else "ShortcutsRemoved",
        )
    }
    fun addBooksToCollection(collectionId: String, ids: Set<String>, eventName: String) {
        val key = "library.collection.$collectionId.books"
        val current = repository.stringList(key).toSet()
        val updated = current + ids
        repository.putStringList(key, updated.sorted(), eventName, collectionId)
        userCollections.firstOrNull { it.id == collectionId }?.let { collection ->
            userCollections = userCollections.map { if (it.id == collectionId) collection.copy(bookIds = updated) else it }
            shortcuts = shortcuts.map { if (it.id == collectionId) collectionShortcut(collection.copy(bookIds = updated), shortcutBooksById) else it }
        }
    }
    fun removeBooks(target: List<AtlasBook>) {
        val updatedRemoved = removedBookIds + target.map { it.id }
        removedBookIds = updatedRemoved
        repository.putStringList("library.removed.bookIds", updatedRemoved.sorted(), if (target.size == 1) "BookRemovedFromLibrary" else "BooksRemovedFromLibrary", target.joinToString(",") { it.id })
        setShortcuts(shortcuts.filterNot { it.book?.id in target.map { book -> book.id }.toSet() }, "ShortcutBooksRemovedWithLibraryBooks")
    }
    fun removeCollection(id: String) {
        val item = shortcuts.firstOrNull { it.id == id } ?: return
        if (id in userCollections.map { it.id }) {
            userCollections = userCollections.filterNot { it.id == id }
            repository.putStringList("library.collections.ids", userCollections.map { it.id }, "CollectionRemoved", id)
        } else {
            hiddenShortcutIds = hiddenShortcutIds + id
            repository.putStringList("library.shortcuts.hidden", hiddenShortcutIds.sorted(), "ShortcutHidden", id)
        }
        setShortcuts(shortcuts.filterNot { it.id == item.id }, "CollectionRemovedFromShortcutShelf")
    }
    fun createCollection(request: PendingCollectionCreation, name: String) {
        val collection = createPersistedUserCollection(
            repository = repository,
            name = name,
            bookIds = request.memberBookIds,
        )
        userCollections = userCollections + collection
        if (request.replacedShortcutIds.isNotEmpty()) {
            hiddenShortcutIds += request.replacedShortcutIds
            repository.putStringList("library.shortcuts.hidden", hiddenShortcutIds.sorted(), "ShortcutBooksReplacedByCollection", collection.id)
        }
        val replacement = collectionShortcut(collection, shortcutBooksById)
        val mutable = shortcuts.filterNot { it.id in request.replacedShortcutIds }.toMutableList()
        mutable.add((request.insertIndex ?: mutable.size).coerceIn(0, mutable.size), replacement)
        setShortcuts(mutable, "CollectionShortcutCreated")
    }
    dragCoordinator.onLongPress = { subjectKey ->
        when {
            subjectKey.startsWith("shortcut-book:") -> {
                val id = subjectKey.removePrefix("shortcut-book:")
                shortcutPageOpen = true
                toggleBook(id)
            }
            subjectKey.startsWith("book:") -> {
                toggleBook(subjectKey.removePrefix("book:"))
            }
            subjectKey.startsWith("shortcut:") -> {
                val id = subjectKey.removePrefix("shortcut:")
                if (shortcuts.any { it.id == id && it.kind == ShortcutKind.COLLECTION } && id in userCollections.map { it.id }) {
                    shortcutPageOpen = true
                    toggleCollection(id)
                }
            }
        }
    }
    dragCoordinator.onDrop = drop@ { payload, destination ->
        if (shortcutLocked) return@drop false
        val bookIds = payloadBookIds(payload)
        when (destination) {
            LibraryDropDestination.Remove -> when {
                isShortcutBookPayload(payload) -> {
                    val target = shortcuts.filter { it.book?.id in bookIds }
                    if (target.isEmpty()) return@drop false
                    removeShortcutItems(target)
                    clearSelection()
                    true
                }
                bookIds.isNotEmpty() -> {
                    val target = shortcutBookCatalog.filter { it.id in bookIds }
                    pendingRemoval = if (target.size == 1) LibraryRemovalRequest.Book(target.single()) else LibraryRemovalRequest.Books(target)
                    true
                }
                payload.startsWith(SHORTCUT_DRAG_PREFIX) -> {
                    val item = shortcuts.firstOrNull { it.id == payload.removePrefix(SHORTCUT_DRAG_PREFIX) } ?: return@drop false
                    pendingRemoval = if (item.kind == ShortcutKind.COLLECTION) LibraryRemovalRequest.Collection(item.id, item.label)
                    else LibraryRemovalRequest.Shortcut(item.id, item.label)
                    true
                }
                else -> false
            }
            is LibraryDropDestination.Collection -> {
                if (bookIds.isEmpty()) return@drop false
                addBooksToCollection(destination.id, bookIds, if (bookIds.size == 1) "ShortcutBookDroppedIntoCollection" else "BooksDroppedIntoCollection")
                clearSelection()
                true
            }
            is LibraryDropDestination.Book -> {
                if (bookIds.isEmpty()) return@drop false
                val target = shortcuts.firstOrNull { it.id == destination.id }?.book ?: return@drop false
                pendingCollectionCreation = PendingCollectionCreation(
                    memberBookIds = bookIds + target.id,
                    replacedShortcutIds = setOf(destination.id),
                    insertIndex = shortcuts.indexOfFirst { it.id == destination.id },
                )
                collectionName = ""
                true
            }
            is LibraryDropDestination.Root -> {
                if (bookIds.size > 1) {
                    pendingCollectionCreation = PendingCollectionCreation(
                        memberBookIds = bookIds,
                        insertIndex = destination.index,
                    )
                    collectionName = ""
                    true
                } else if (bookIds.size == 1) {
                    val additions = shortcutBookCatalog.filter { it.id in bookIds }.map(::bookShortcut)
                    val additionIds = additions.map { it.id }.toSet()
                    if (hiddenShortcutIds.any { it in additionIds }) {
                        hiddenShortcutIds -= additionIds
                        repository.putStringList("library.shortcuts.hidden", hiddenShortcutIds.sorted(), "ShortcutBooksUnhidden", additionIds.sorted().joinToString(","))
                    }
                    val mutable = shortcuts.toMutableList()
                    additions.forEachIndexed { offset, item ->
                        mutable.removeAll { it.id == item.id }
                        mutable.add((destination.index + offset).coerceIn(0, mutable.size), item)
                    }
                    setShortcuts(mutable, "ShortcutBookDropped")
                    clearSelection()
                    true
                } else if (payload.startsWith(SHORTCUT_DRAG_PREFIX)) {
                    val id = payload.removePrefix(SHORTCUT_DRAG_PREFIX)
                    val mutable = shortcuts.toMutableList()
                    val from = mutable.indexOfFirst { it.id == id }
                    if (from < 0) return@drop false
                    val item = mutable.removeAt(from)
                    mutable.add(destination.index.coerceIn(0, mutable.size), item)
                    setShortcuts(mutable, "ShortcutMoved")
                    true
                } else false
            }
            is LibraryDropDestination.Library -> {
                if (bookIds.isEmpty() || !libraryReorderEnabled || !payload.startsWith(BOOK_DRAG_PREFIX)) return@drop false
                val mutable = books.toMutableList()
                val moved = mutable.filter { it.id in bookIds }
                mutable.removeAll(moved.toSet())
                mutable.addAll(destination.index.coerceIn(0, mutable.size), moved)
                orderedBookIds = mutable.map { it.id }
                repository.putStringList("library.order.all", orderedBookIds, "LibraryBooksReordered", bookIds.joinToString(","))
                clearSelection()
                true
            }
        }
    }
    BackHandler(selectionKind != null) { clearSelection() }
    val selectionTargetIds = when (selectionKind) {
        LibrarySelectionKind.BOOK -> if (shortcutPageOpen) shortcuts.mapNotNull { it.book?.id }.toSet() else books.map { it.id }.toSet()
        LibrarySelectionKind.COLLECTION -> shortcuts
            .filter { it.kind == ShortcutKind.COLLECTION && it.id in userCollections.map { collection -> collection.id } }
            .map { it.id }
            .toSet()
        null -> emptySet()
    }
    val currentSelectionIds = if (selectionKind == LibrarySelectionKind.BOOK) selectedBookIds else selectedCollectionIds
    val allSelectionTargetsSelected = selectionTargetIds.isNotEmpty() && currentSelectionIds.containsAll(selectionTargetIds)
    val selectedCount = selectedBookIds.size + selectedCollectionIds.size
    val selectionBar = if (selectionKind != null) AtlasSelectionBar(
        count = selectedCount,
        onClose = ::clearSelection,
        allSelected = allSelectionTargetsSelected,
        onToggleAll = {
            if (selectionKind == LibrarySelectionKind.BOOK) {
                selectBooks(if (allSelectionTargetsSelected) emptySet() else selectionTargetIds)
            } else {
                selectedCollectionIds = if (allSelectionTargetsSelected) emptySet() else selectionTargetIds
            }
        },
        bulkActions = if (selectionKind == LibrarySelectionKind.BOOK) {
            listOf(
                AtlasTopBarAction(AtlasIcons.FolderAdd, "用所选新建收藏夹") {
                    pendingCollectionCreation = PendingCollectionCreation(memberBookIds = selectedBookIds)
                    collectionName = ""
                },
                AtlasTopBarAction(AtlasIcons.FolderMove, "移入收藏夹") {
                    collectionPicker = CollectionPickerRequest.Books(selectedBookIds)
                },
                AtlasTopBarAction(AtlasIcons.Delete, if (shortcutPageOpen) "移出快捷书架" else "移出书架") {
                    if (shortcutPageOpen) removeShortcutItems(shortcuts.filter { it.book?.id in selectedBookIds })
                    else pendingRemoval = LibraryRemovalRequest.Books(shortcutBookCatalog.filter { it.id in selectedBookIds })
                },
            )
        } else {
            listOf(
                AtlasTopBarAction(AtlasIcons.FolderMove, "移入收藏夹") {
                    collectionPicker = CollectionPickerRequest.Collections(selectedCollectionIds)
                },
                AtlasTopBarAction(AtlasIcons.Delete, "删除收藏夹") {
                    pendingRemoval = LibraryRemovalRequest.Collections(shortcuts.filter { it.id in selectedCollectionIds })
                },
            )
        },
    ) else null
    val page = pageSlice(
        "library-${view.name}-${layout.name}-${sortMode.name}-${sortDirection.name}",
        books,
        if (layout == AtlasLayout.GRID) 8 else 11,
    )
    val shownBooks = if (eInk) page.items else books
    val openShortcut: (Rc21ShortcutItem) -> Unit = { item ->
        item.view?.let { selectedView ->
            navigation.selectLibraryView(selectedView)
            repository.putString("library.view", selectedView.name, "LibraryViewChanged", item.id)
        }
        when (item.kind) {
            ShortcutKind.COLLECTION -> repository.putString("library.collection.level.0.id", item.id, "CollectionPageSelected", item.id)
            ShortcutKind.MIRROR -> {
                val bindingId = if (item.id == "pine-mirror") "mirror-pine" else item.id
                repository.putString("library.mirror.binding", bindingId, "MirrorPageSelected", bindingId)
            }
            else -> Unit
        }
        repository.record("ShortcutOpened", item.id, "success")
        if (item.route != AtlasRoute.LIBRARY) navigation.navigate(item.route)
    }
    val shortcutShelf: @Composable () -> Unit = {
        ShortcutShelf(
            items = shortcuts,
            expanded = shortcutExpanded,
            locked = shortcutLocked,
            editing = shortcutEditing,
            onExpanded = { shortcutExpanded = it; repository.putBoolean("library.shortcuts.expanded", it, "ShortcutExpanded") },
            onLocked = { if (it) dragCoordinator.cancel(); shortcutLocked = it; repository.putBoolean("library.shortcuts.locked", it, "ShortcutLocked") },
            onEditing = { shortcutEditing = it },
            onOpen = openShortcut,
            onReturnToAll = {},
            onMove = { from, to -> if (to in shortcuts.indices) setShortcuts(shortcuts.toMutableList().also { it.add(to, it.removeAt(from)) }, "ShortcutMoved") },
            onRemove = { id ->
                shortcuts.firstOrNull { it.id == id }?.let { item ->
                    when {
                        item.book != null -> removeShortcutItems(listOf(item))
                        item.kind == ShortcutKind.COLLECTION -> pendingRemoval = LibraryRemovalRequest.Collection(item.id, item.label)
                        else -> pendingRemoval = LibraryRemovalRequest.Shortcut(item.id, item.label)
                    }
                }
            },
            onViewAll = { shortcutPageOpen = true },
            activeView = AtlasLibraryView.ALL,
            onCreate = { pendingCollectionCreation = PendingCollectionCreation(); collectionName = "" },
            selectionKind = selectionKind,
            selectedBookIds = selectedBookIds,
            selectedCollectionIds = selectedCollectionIds,
            onToggleBook = ::toggleBook,
            onToggleCollection = ::toggleCollection,
            conflictSignal = selectionConflictSignal,
            conflictTargetKey = selectionConflictTarget,
            dragCoordinator = dragCoordinator,
        )
    }
    val activeDragItem = dragCoordinator.activeBookIds.firstOrNull()?.let { id -> shortcutBookCatalog.firstOrNull { it.id == id }?.let(::bookShortcut) }
        ?: dragCoordinator.activePayload?.removePrefix(SHORTCUT_DRAG_PREFIX)?.let { id -> shortcuts.firstOrNull { it.id == id } }
    Box(modifier.fillMaxSize().libraryDragOverlayHost(dragCoordinator)) {
        AtlasScaffold(
            modifier = Modifier.fillMaxSize().then(if (shortcutPageOpen) Modifier.clearAndSetSemantics { } else Modifier),
            topBar = {
                Column {
                    AtlasTopBar(
                        title = if (standaloneNodePage) view.label else AtlasStrings.ROOT_LIBRARY,
                        subtitle = "${books.size} 本",
                        onUp = if (standaloneNodePage) navigation.up else null,
                        selection = selectionBar,
                        actionBudgetOverride = 3,
                        actions = listOf(
                            AtlasTopBarAction(AtlasIcons.Refresh, "同步并检查更新", runLibrarySyncCheck),
                            AtlasTopBarAction(AtlasIcons.Search, "搜索") { navigation.navigateSearch(null) },
                            AtlasTopBarAction(layout.currentLayoutIcon(), layout.layoutToggleContentDescription()) {
                                layout = layout.nextAtlasLayout()
                                repository.putString("library.layout", layout.name, "LibraryLayoutChanged")
                            },
                        ),
                        overflow = listOf(
                            AtlasOverflowItem("排序：${sortMode.summary(sortDirection)}") { sortOpen = true },
                            AtlasOverflowItem("标签") { navigation.navigate(AtlasRoute.LIBRARY_TAGS) },
                        ),
                    )
                    syncStatus?.let { AtlasMutationBanner(it) }
                }
            },
            footer = if (eInk && context.state.showsContent && books.isNotEmpty()) { { PaginationFooter(page.page, page.pages, page.setPage) } } else null,
        ) {
            StateOrContent(context.state, fixture.emptyTitle, fixture.emptyMessage, "书架加载失败", "本地书架索引不可用；书籍数据未受影响。", fixture.emptyActionLabel, { repository.record("LibraryEmptyAction", view.name, "success") }) {
                Column(Modifier.fillMaxSize()) {
                    if (!eInk) {
                        if (!standaloneNodePage) shortcutShelf()
                        BookSurface(
                            context = context,
                            books = shownBooks,
                            layout = layout,
                            selected = if (selectionKind == LibrarySelectionKind.BOOK) selectedBookIds else emptySet(),
                            selectionActive = selectionKind == LibrarySelectionKind.BOOK,
                            selectionConflictTarget = selectionConflictTarget,
                            selectionConflictSignal = selectionConflictSignal,
                            toggle = ::toggleBook,
                            interaction = LibraryBookInteractionCapabilities(
                                drag = !shortcutLocked,
                                reorder = libraryReorderEnabled,
                            ),
                            denseGrid = true,
                            dragCoordinator = dragCoordinator,
                            onLongPress = { toggleBook(it.id) },
                            onRemoveRequest = { pendingRemoval = LibraryRemovalRequest.Book(it) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        if (!standaloneNodePage) shortcutShelf()
                        BookSurface(
                            context = context,
                            books = shownBooks,
                            layout = layout,
                            selected = if (selectionKind == LibrarySelectionKind.BOOK) selectedBookIds else emptySet(),
                            selectionActive = selectionKind == LibrarySelectionKind.BOOK,
                            toggle = ::toggleBook,
                            interaction = LibraryBookInteractionCapabilities(
                                drag = !shortcutLocked,
                                reorder = libraryReorderEnabled,
                            ),
                            selectionConflictTarget = selectionConflictTarget,
                            selectionConflictSignal = selectionConflictSignal,
                            dragCoordinator = dragCoordinator,
                            onLongPress = { toggleBook(it.id) },
                            onRemoveRequest = { pendingRemoval = LibraryRemovalRequest.Book(it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (shortcutPageOpen) {
            ShortcutAllPage(
                onReturnToAll = {},
                items = shortcuts,
                activeView = AtlasLibraryView.ALL,
                locked = shortcutLocked,
                onDismiss = { shortcutPageOpen = false },
                onOpen = { shortcutPageOpen = false; openShortcut(it) },
                selectionKind = selectionKind,
                selection = selectionBar,
                selectedBookIds = selectedBookIds,
                selectedCollectionIds = selectedCollectionIds,
                onClearSelection = ::clearSelection,
                onToggleBook = ::toggleBook,
                onToggleCollection = ::toggleCollection,
                conflictSignal = selectionConflictSignal,
                conflictTargetKey = selectionConflictTarget,
                dragCoordinator = dragCoordinator,
            )
        }
        ShortcutDeleteDropTarget(
            visible = dragCoordinator.activePayload?.let { payloadBookIds(it).isNotEmpty() || it.startsWith(SHORTCUT_DRAG_PREFIX) } == true,
            label = if (dragCoordinator.activePayload?.let { payloadBookIds(it).isNotEmpty() && !isShortcutBookPayload(it) } == true) "移出总书架" else "移出快捷书架",
            active = dragCoordinator.isOverDelete,
            dragCoordinator = dragCoordinator,
            modifier = Modifier.fillMaxSize(),
        )
        ShortcutDragGhost(activeItem = activeDragItem, dragCoordinator = dragCoordinator, bookLayout = layout, modifier = Modifier.fillMaxSize())
    }
    if (sortOpen) {
        LibrarySortDialog(
            mode = sortMode,
            direction = sortDirection,
            onModeChange = {
                sortMode = it
                repository.putString("library.sort", it.name, "LibrarySortChanged")
            },
            onDirectionChange = {
                sortDirection = it
                repository.putString("library.sort.direction", it.name, "LibrarySortDirectionChanged")
            },
            onDismiss = { sortOpen = false },
        )
    }
    pendingCollectionCreation?.let { request ->
        FullDialog("新建收藏夹", { pendingCollectionCreation = null }) {
            OutlinedTextField(collectionName, { collectionName = it }, label = { Text("收藏夹名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DialogButtons("创建", {
                if (collectionName.isNotBlank()) {
                    createCollection(request, collectionName)
                    pendingCollectionCreation = null
                    clearSelection()
                }
            }, { pendingCollectionCreation = null })
        }
    }
    collectionPicker?.let { request ->
        val candidates = shortcuts.filter { it.kind == ShortcutKind.COLLECTION && it.id !in selectedCollectionIds }
        FullDialog("移入收藏夹", { collectionPicker = null }) {
            if (candidates.isEmpty()) Text("还没有可用收藏夹。")
            candidates.forEach { collection ->
                AtlasButton(collection.label, {
                    when (request) {
                        is CollectionPickerRequest.Books -> addBooksToCollection(collection.id, request.ids, "BooksMovedIntoCollection")
                        is CollectionPickerRequest.Collections -> {
                            request.ids.forEach { id -> repository.putString("library.collection.$id.parent", collection.id, "CollectionsMovedIntoCollection", id) }
                        }
                    }
                    clearSelection()
                    collectionPicker = null
                }, modifier = Modifier.fillMaxWidth(), style = AtlasButtonStyle.TEXT)
            }
        }
    }
    pendingRemoval?.let { request ->
        val (title, message, confirm) = when (request) {
            is LibraryRemovalRequest.Shortcut -> Triple("移出快捷书架？", "「${request.label}」将只从快捷书架移除；总书架、收藏关系和阅读进度不受影响。") { removeShortcutItems(shortcuts.filter { it.id == request.id }) }
            is LibraryRemovalRequest.Book -> Triple("移出总书架？", "《${request.book.title}》将从总书架及其快捷项移出；本地文件、收藏关系和阅读进度不受影响。") { removeBooks(listOf(request.book)) }
            is LibraryRemovalRequest.Books -> Triple("移出 ${request.books.size} 本书？", "所选书籍将从总书架及其快捷项移出；本地文件、收藏关系和阅读进度不受影响。") { removeBooks(request.books) }
            is LibraryRemovalRequest.Collection -> Triple("删除收藏夹？", "「${request.label}」将从快捷书架移除；书籍、阅读进度和本地文件不受影响。") { removeCollection(request.id) }
            is LibraryRemovalRequest.Collections -> Triple("删除 ${request.items.size} 个收藏夹？", "所选收藏夹将从快捷书架移除；书籍、阅读进度和本地文件不受影响。") { request.items.forEach { removeCollection(it.id) } }
        }
        FullDialog(title, { pendingRemoval = null }, destructive = true) {
            Text(message)
            DialogButtons(
                if (request is LibraryRemovalRequest.Book || request is LibraryRemovalRequest.Books || request is LibraryRemovalRequest.Shortcut) "移出" else "删除",
                { confirm(); clearSelection(); pendingRemoval = null },
                { pendingRemoval = null },
            )
        }
    }
}
// -------------------------------------------------------------------------------------------
// #3 — library/history
// -------------------------------------------------------------------------------------------

private data class HistoryLine(
    val header: String?,
    val entry: LibraryAtlasFixtures.HistoryEntryFixture,
)

@Composable
private fun LibraryHistory(context: AtlasContext, modifier: Modifier) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    val removedIds = repository.stringList("history.removed").toSet()
    val lines = LibraryAtlasFixtures.historyGroups.flatMap { group ->
        group.entries.mapIndexed { index, entry -> HistoryLine(if (index == 0) group.label else null, entry) }
    }.filterNot { it.entry.book.id in removedIds }
    val books = lines.map { it.entry.book }
    var selected by remember(context.state) {
        mutableStateOf(if (context.state == AtlasPageState.SELECTION) books.take(3).map { it.id }.toSet() else emptySet())
    }
    var selectionActive by remember(context.state) { mutableStateOf(context.state == AtlasPageState.SELECTION) }
    var removeBook by remember(context.state) {
        mutableStateOf(if (context.state == AtlasPageState.MODAL) books.firstOrNull() else null)
    }
    var clearOpen by remember { mutableStateOf(false) }
    var pendingHistoryRemovalIds by remember { mutableStateOf<Set<String>?>(null) }
    BackHandler(selectionActive) {
        selectionActive = false
        selected = emptySet()
    }
    BackHandler(removeBook != null || clearOpen || pendingHistoryRemovalIds != null) {
        removeBook = null
        clearOpen = false
        pendingHistoryRemovalIds = null
    }
    val selectionBar = selectionTopBar(
        selected,
        books.map { it.id }.toSet(),
        { selectionActive = false; selected = emptySet() },
        { selected = it },
        AtlasIcons.Close,
        "移除所选",
    ) {
        pendingHistoryRemovalIds = selected
    }
    val mutation = if (context.state == AtlasPageState.MUTATION) {
        AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "已移除 1 条历史记录；书架与阅读进度未受影响")
    } else {
        null
    }
    val page = pageSlice("history", lines, 8)
    val visible = if (eInk) page.items else lines
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            Column {
                AtlasTopBar(
                    title = "历史",
                    subtitle = "${books.size} 条记录",
                    onUp = navigation.up,
                    selection = if (selectionActive) selectionBar else null,
                    overflow = listOf(AtlasOverflowItem("清空历史", { clearOpen = true })),
                )
                OverlayState(context.state, mutation)
            }
        },
        footer = if (eInk && context.state.showsContent && lines.isNotEmpty()) {
            { PaginationFooter(page.page, page.pages, page.setPage) }
        } else {
            null
        },
    ) {
        StateOrContent(
            context.state,
            "还没有阅读历史",
            "开始阅读后，最近读过的书会按时间分组出现在这里。",
            "历史记录加载失败",
            "本地历史索引不可用；书架与阅读进度未受影响。",
        ) {
            val body: @Composable (HistoryLine) -> Unit = { line ->
                line.header?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val book = line.entry.book
                BookListItemRow(
                    book = book,
                    onClick = {
                        if (selectionActive) {
                            selected = if (book.id in selected) selected - book.id else selected + book.id
                        } else {
                            navigation.navigate(AtlasRoute.BOOK_DETAIL)
                        }
                    },
                    onLongClick = {
                        selectionActive = true
                        selected = selected + book.id
                    },
                    showSourceChip = false,
                    selected = book.id in selected,
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                line.entry.timeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AtlasIconButton(
                                AtlasIcons.Close,
                                "从历史移除《${book.title}》",
                                { removeBook = book },
                            )
                        }
                    },
                )
            }
            if (eInk) {
                Column { visible.forEach { body(it) } }
            } else {
                LazyColumn { items(visible, key = { it.entry.book.id }) { body(it) } }
            }
        }
    }
    removeBook?.let { book ->
        FullDialog("从历史中移除？", { removeBook = null }, destructive = true) {
            Text("《${book.title}》将从历史记录中移除。书架、收藏与阅读进度不受影响。")
            DialogButtons("移除", {
                repository.putStringList(
                    "history.removed",
                    (removedIds + book.id).toList(),
                    "HistoryEntryRemoved",
                    book.id,
                )
                removeBook = null
            }, { removeBook = null })
        }
    }
    pendingHistoryRemovalIds?.let { ids ->
        FullDialog("从历史中移除 ${ids.size} 条记录？", { pendingHistoryRemovalIds = null }, destructive = true) {
            Text("所选历史记录将被移除。书架、收藏与阅读进度不受影响。")
            DialogButtons("移除", {
                repository.putStringList(
                    "history.removed",
                    (removedIds + ids).toList(),
                    "HistoryEntriesRemoved",
                    "history",
                )
                selected = emptySet()
                selectionActive = false
                pendingHistoryRemovalIds = null
            }, { pendingHistoryRemovalIds = null })
        }
    }
    if (clearOpen) {
        FullDialog("清空全部历史？", { clearOpen = false }, destructive = true) {
            Text("${books.size} 条历史记录将被清空，此操作不可撤销。书籍、收藏与阅读进度不受影响。")
            DialogButtons("清空历史", {
                repository.putStringList(
                    "history.removed",
                    (removedIds + books.map { it.id }).toList(),
                    "HistoryCleared",
                    "history",
                )
                clearOpen = false
            }, { clearOpen = false })
        }
    }
}

// -------------------------------------------------------------------------------------------
// #4 — library/updates
// -------------------------------------------------------------------------------------------

@Composable
private fun LibraryUpdates(context: AtlasContext, modifier: Modifier) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val coroutineScope = rememberCoroutineScope()
    val runCheck: () -> Unit = {
        coroutineScope.launch { runtime.scenarios.run("updates-check", "library/updates") }
    }
    val updates = LibraryAtlasFixtures.updateEntries
    val excluded = LibraryAtlasFixtures.updateExclusions
    val page = pageSlice("updates", updates, 6)
    val sessionVisible = context.state == AtlasPageState.REFRESHING
    val visible = when {
        sessionVisible -> updates.take(LibraryAtlasFixtures.UPDATE_RUNNING_FOUND)
        eInk -> page.items
        else -> updates
    }
    var layout by rememberSaveable(context.layout?.name) { mutableStateOf(context.layout ?: AtlasLayout.LIST) }
    var settingsOpen by remember(context.state) { mutableStateOf(context.state == AtlasPageState.MODAL) }
    var scheduleEnabled by rememberSaveable { mutableStateOf(false) }
    var tutorialOpen by remember(context.tutorial) { mutableStateOf(context.tutorial) }
    var schedulePeriod by rememberSaveable { mutableStateOf("每日") }
    val mutation = if (context.state == AtlasPageState.MUTATION) {
        AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "已处理 ${updates.size} 条更新；追更待办已清空")
    } else {
        null
    }
    val unresolved = AtlasMutationStatus(
        AtlasMutationPhase.ERROR,
        "本地标记未保存：exact update anchor 仍保持未处理；没有发送网络请求，也不会阻止网站操作。",
        "重试本地保存",
        { repository.record("UpdateAnchorSaveRetried", "library/updates", "queued") },
    )
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            Column {
                AtlasTopBar(
                    title = "追更",
                    subtitle = "${updates.size} 本有更新 · ${excluded.size} 本已排除",
                    onUp = navigation.up,
                    actions = listOf(
                        AtlasTopBarAction(AtlasIcons.Refresh, "检查全部更新", runCheck),
                        AtlasTopBarAction(layout.currentLayoutIcon(), layout.layoutToggleContentDescription()) {
                            layout = layout.nextAtlasLayout()
                            repository.putString("updates.layout", layout.name, "UpdateLayoutChanged")
                        },
                    ),
                    overflow = listOf(
                        AtlasOverflowItem("追更设置") { settingsOpen = true },
                        AtlasOverflowItem("功能说明") { tutorialOpen = true },
                        AtlasOverflowItem("全部确认已看过") { repository.putBoolean("updates.allSeen", true, "AllUpdatesMarkedSeen") },
                    ),
                )
                if (!sessionVisible) OverlayState(context.state, mutation, unresolved)
            }
        },
        footer = if (eInk && context.primaryState == AtlasPageState.CONTENT && updates.isNotEmpty()) {
            { PaginationFooter(page.page, page.pages, page.setPage) }
        } else {
            null
        },
    ) {
        StateOrContent(
            context.state,
            "没有待处理的更新",
            "检查更新后，新章节会出现在这里。打开此页不会自动确认更新。",
            "更新检查失败",
            "源·柏凭据过期；源·松连接超时。已缓存的结果仍可查看。",
            "检查全部更新",
            runCheck,
        ) {
            when (layout) {
                AtlasLayout.GRID -> Column {
                    if (sessionVisible) UpdateSessionSurface()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(AtlasSpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                        horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                    ) {
                        gridItems(visible, key = { it.book.id }) { update ->
                            BookGridCard(
                                update.book.copy(progressLabel = "${update.anchorLabel} · ${update.updatedAtLabel}"),
                                { repository.record("UpdateBookOpened", update.book.id, "success"); navigation.navigate(AtlasRoute.BOOK_DETAIL) },
                                modifier = Modifier.semantics { stateDescription = "${update.newChapters} 章更新 · ${update.anchorLabel} · ${update.updatedAtLabel}" },
                            )
                        }
                    }
                }
                AtlasLayout.COMPACT -> Column {
                    if (sessionVisible) UpdateSessionSurface()
                    visible.forEach { update ->
                        CompactBookListItem(update.book.copy(progressLabel = "${update.anchorLabel} · ${update.updatedAtLabel}"), {
                            repository.record("UpdateBookOpened", update.book.id, "success")
                            navigation.navigate(AtlasRoute.BOOK_DETAIL)
                        })
                    }
                }
                AtlasLayout.LIST -> if (eInk) {
                    Column { if (sessionVisible) UpdateSessionSurface(); visible.forEach { UpdateBookRow(it) }; if (!sessionVisible) UpdateExcluded(excluded) }
                } else {
                    LazyColumn { if (sessionVisible) item { UpdateSessionSurface() }; items(visible, key = { it.book.id }) { UpdateBookRow(it) }; if (!sessionVisible) item { UpdateExcluded(excluded) } }
                }
            }
        }
    }
    if (tutorialOpen) {
        AtlasFeatureIntroduction(
            featureId = "updates",
            tutorialVersion = 1,
            title = "功能说明：追更",
            summary = "打开追更不会自动检查或标记已处理。",
            points = listOf(
                "自动检查默认关闭，只能在追更设置中启用。",
                "隐藏或重建追更视图不会改变调度。",
                "标记已处理只保存本地 exact anchor，不向来源写入。",
                "说明关闭后，手动检查和设置仍需要你的明确操作。",
            ),
            onDismiss = { tutorialOpen = false },
        )
    }
    if (settingsOpen) {
        FullDialog("追更设置", { settingsOpen = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                Text("自动检查默认关闭。隐藏、删除或重建「追更」视图都不会改变这里的调度。")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                        scheduleEnabled = !scheduleEnabled
                        repository.putBoolean("updates.schedule.enabled", scheduleEnabled, "UpdateScheduleChanged")
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(scheduleEnabled, {
                        scheduleEnabled = it
                        repository.putBoolean("updates.schedule.enabled", it, "UpdateScheduleChanged")
                    })
                    Text(if (scheduleEnabled) "自动检查：开启" else "自动检查：关闭", Modifier.padding(start = AtlasSpacing.Md))
                }
                listOf("每 12 小时", "每日", "每 3 天", "每周").forEach { period ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = scheduleEnabled) {
                            schedulePeriod = period
                            repository.putString("updates.schedule.period", period, "UpdateSchedulePeriodChanged")
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(schedulePeriod == period, {
                            schedulePeriod = period
                            repository.putString("updates.schedule.period", period, "UpdateSchedulePeriodChanged")
                        }, enabled = scheduleEnabled)
                        Text(period, Modifier.padding(start = AtlasSpacing.Sm))
                    }
                }
                Text("有效约束：设备联网且系统允许后台任务；不要求充电。通知显示会话计数并提供「取消本次检查」。通知权限被拒绝时，持久的应用内会话状态仍可查看和取消。")
                AtlasButton("保存设置", {
                    repository.record("UpdateSettingsSaved", "library/updates", "success")
                    settingsOpen = false
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun UpdateSessionSurface() {
    val eInk = LocalAtlasEnvironment.current.eInk
    val repository = prototypeRepository()
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(AtlasSpacing.Md),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (eInk) BorderStroke(1.5.dp, AtlasEInkPalette.Ink) else null,
    ) {
        Column(Modifier.padding(AtlasSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (eInk) Icon(AtlasIcons.Refresh, contentDescription = "正在检查", modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(start = if (eInk) AtlasSpacing.Sm else 0.dp)) {
                    Text("正在检查更新", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${LibraryAtlasFixtures.UPDATE_RUNNING_CHECKED} / ${LibraryAtlasFixtures.UPDATE_RUNNING_TOTAL}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AtlasButton(if (expanded) "收起" else "查看详情", { expanded = !expanded }, style = AtlasButtonStyle.TEXT)
            }
            if (!eInk) {
                LinearProgressIndicator(
                    progress = { LibraryAtlasFixtures.UPDATE_RUNNING_CHECKED.toFloat() / LibraryAtlasFixtures.UPDATE_RUNNING_TOTAL },
                    modifier = Modifier.fillMaxWidth().padding(top = AtlasSpacing.Sm),
                )
            }
            if (expanded) {
                Text(
                    "上次部分完成：125 / 128 成功 · ${LibraryAtlasFixtures.UPDATE_PARTIAL_FAILED} 本失败",
                    modifier = Modifier.padding(top = AtlasSpacing.Md),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(LibraryAtlasFixtures.UPDATE_FAILED_SOURCE_LINE, style = MaterialTheme.typography.bodySmall)
                Text(LibraryAtlasFixtures.UPDATE_DORMANT_LINE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                    AtlasButton("重试失败项", {
                        repository.record("FailedUpdatesRetried", "library/updates", "queued")
                    }, style = AtlasButtonStyle.SECONDARY)
                    AtlasButton("取消本次检查", {
                        repository.record("UpdateCheckCancelled", "library/updates", "cancelled")
                    }, style = AtlasButtonStyle.TEXT)
                }
            }
        }
    }
}

@Composable
private fun UpdateBookRow(update: LibraryAtlasFixtures.UpdateEntryFixture) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    BookListItemRow(
        book = update.book,
        onClick = {
            repository.record("UpdateBookOpened", update.book.id, "success")
            navigation.navigate(AtlasRoute.BOOK_DETAIL)
        },
        showSourceChip = false,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(update.anchorLabel, style = MaterialTheme.typography.bodySmall)
                Text(update.updatedAtLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AtlasIconButton(AtlasIcons.Check, "确认已看过", {
                    repository.putBoolean("updates.${update.book.id}.seen", true, "UpdateMarkedSeen", update.book.id)
                })
            }
        },
    )
}

@Composable
private fun UpdateExcluded(excluded: List<LibraryAtlasFixtures.ExcludedBookFixture>) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    Text(
        "已排除（${excluded.size}）",
        modifier = Modifier.padding(AtlasSpacing.Md),
        style = MaterialTheme.typography.titleMedium,
    )
    excluded.forEach { item ->
        BookListItemRow(
            book = item.book,
            onClick = { navigation.navigate(AtlasRoute.BOOK_DETAIL) },
            showSourceChip = false,
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(item.reason, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    AtlasButton("恢复检查", {
                        repository.putBoolean("updates.${item.book.id}.excluded", false, "UpdateCheckRestored", item.book.id)
                    }, style = AtlasButtonStyle.TEXT)
                }
            },
        )
    }
}

// -------------------------------------------------------------------------------------------
// #7/#8 — manual / smart collection detail
// -------------------------------------------------------------------------------------------

@Composable
private fun CollectionDetail(context: AtlasContext, modifier: Modifier) {
    val repository = prototypeRepository()
    val depth = when (context.route) {
        AtlasRoute.LIBRARY_COLLECTION -> 0
        AtlasRoute.LIBRARY_COLLECTION_CHILD -> 1
        else -> 2
    }
    val defaultCollection = if (context.libraryView == AtlasLibraryView.COLLECTION) {
        LibraryAtlasFixtures.smartSciFi
    } else {
        LibraryAtlasFixtures.manualNightBoat
    }
    val collectionId = repository.string("library.collection.level.$depth.id", defaultCollection.id)
    val collection = LibraryAtlasFixtures.collectionTree.findCollectionFixture(collectionId) ?: defaultCollection
    if (collection.smart) SmartCollection(context, modifier, collection)
    else ManualCollection(context, modifier, collection, depth)
}

@Composable
private fun ManualCollection(
    context: AtlasContext,
    modifier: Modifier,
    collection: LibraryAtlasFixtures.CollectionFixture,
    depth: Int,
) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    val eInk = LocalAtlasEnvironment.current.eInk
    val collectionId = collection.id
    val membershipKey = "library.collection.$collectionId.books"
    val fixtureBooks = LibraryAtlasFixtures.booksForCollection(collection)
    var books by remember(collectionId) {
        val storedIds = repository.stringList(membershipKey).toSet()
        val initialized = repository.boolean("$membershipKey.initialized")
        mutableStateOf(if (initialized) fixtureBooks.filter { it.id in storedIds } else fixtureBooks)
    }
    var orderedBookIds by remember(collectionId) { mutableStateOf(repository.stringList("library.collection.$collectionId.order")) }
    var sortMode by rememberSaveable(collectionId) {
        mutableStateOf(
            LibraryBookSortMode.entries.firstOrNull { it.name == repository.string("library.collection.$collectionId.sort") }
                ?: LibraryBookSortMode.CUSTOM,
        )
    }
    var sortDirection by rememberSaveable(collectionId) {
        mutableStateOf(
            LibraryBookSortDirection.entries.firstOrNull {
                it.name == repository.string("library.collection.$collectionId.sort.direction")
            } ?: LibraryBookSortDirection.ASCENDING,
        )
    }
    val shownBooksInOrder = books.orderedForLibrary(sortMode, sortDirection, orderedBookIds)
    var childItems by remember(collectionId) {
        val childRoute = collectionRouteForDepth(depth + 1)
        val fixtures = collection.children.map { child -> collectionFixtureShortcut(child, childRoute) }
        val stored = repository.stringList("library.collection.$collectionId.children.order")
        val byId = fixtures.associateBy { it.id }
        mutableStateOf(stored.mapNotNull(byId::get) + fixtures.filterNot { it.id in stored })
    }
    var layout by rememberSaveable(context.profile.name, collectionId) {
        mutableStateOf(
            AtlasLayout.entries.firstOrNull { it.name == repository.string("library.collection.$collectionId.layout") }
                ?: context.layout ?: if (eInk) AtlasLayout.GRID else AtlasLayout.LIST,
        )
    }
    var selectionKind by remember(context.state) {
        mutableStateOf<LibrarySelectionKind?>(if (context.state == AtlasPageState.SELECTION) LibrarySelectionKind.BOOK else null)
    }
    var selectedBookIds by remember(context.state) {
        mutableStateOf(if (context.state == AtlasPageState.SELECTION) books.take(3).map { it.id }.toSet() else emptySet())
    }
    var selectedCollectionIds by remember { mutableStateOf(emptySet<String>()) }
    var conflictTarget by remember { mutableStateOf<String?>(null) }
    var conflictSignal by remember { mutableIntStateOf(0) }
    var sheet by remember(context.state) { mutableStateOf(context.state == AtlasPageState.MODAL) }
    var sortOpen by rememberSaveable(collectionId) { mutableStateOf(false) }
    var pendingChildCollectionName by remember { mutableStateOf<String?>(null) }
    var pendingBookRemoval by remember { mutableStateOf(emptySet<String>()) }
    var pendingChildRemoval by remember { mutableStateOf(emptySet<String>()) }
    val dragCoordinator = remember { LibraryDragCoordinator() }
    fun clearSelection() {
        selectionKind = null
        selectedBookIds = emptySet()
        selectedCollectionIds = emptySet()
        conflictTarget = null
    }
    fun toggleBook(id: String) {
        if (selectionKind == null || selectionKind == LibrarySelectionKind.BOOK) {
            selectionKind = LibrarySelectionKind.BOOK
            selectedBookIds = if (id in selectedBookIds) selectedBookIds - id else selectedBookIds + id
        } else {
            conflictTarget = "book:$id"
            conflictSignal += 1
        }
    }
    fun toggleCollection(id: String) {
        if (selectionKind == null || selectionKind == LibrarySelectionKind.COLLECTION) {
            selectionKind = LibrarySelectionKind.COLLECTION
            selectedCollectionIds = if (id in selectedCollectionIds) selectedCollectionIds - id else selectedCollectionIds + id
        } else {
            conflictTarget = "shortcut:$id"
            conflictSignal += 1
        }
    }
    fun persistBooks(updated: List<AtlasBook>, event: String) {
        books = updated
        repository.putBoolean("$membershipKey.initialized", true, "CollectionMembershipInitialized")
        repository.putStringList(membershipKey, updated.map { it.id }, event, collectionId)
    }
    fun persistChildren(updated: List<Rc21ShortcutItem>, event: String) {
        childItems = updated
        repository.putStringList("library.collection.$collectionId.children.order", updated.map { it.id }, event, collectionId)
    }
    fun createChildCollection(name: String) {
        val created = createPersistedUserCollection(
            repository = repository,
            name = name,
            bookIds = selectedBookIds,
            parentId = collectionId,
        )
        val item = collectionShortcut(created, books.associateBy(AtlasBook::id)).copy(
            route = collectionRouteForDepth(depth + 1),
        )
        persistChildren(childItems + item, "CollectionChildCreatedFromSelection")
        clearSelection()
    }
    dragCoordinator.onLongPress = { subjectKey ->
        when {
            subjectKey.startsWith("book:") -> toggleBook(subjectKey.removePrefix("book:"))
            subjectKey.startsWith("shortcut:") -> toggleCollection(subjectKey.removePrefix("shortcut:"))
        }
    }
    dragCoordinator.onDrop = drop@ { payload, destination ->
        val bookIds = payloadBookIds(payload)
        when (destination) {
            LibraryDropDestination.Remove -> {
                if (bookIds.isNotEmpty()) pendingBookRemoval = bookIds
                else if (payload.startsWith(SHORTCUT_DRAG_PREFIX)) pendingChildRemoval = setOf(payload.removePrefix(SHORTCUT_DRAG_PREFIX))
                else return@drop false
                true
            }
            is LibraryDropDestination.Collection -> {
                if (bookIds.isEmpty()) return@drop false
                val key = "library.collection.${destination.id}.books"
                repository.putStringList(key, (repository.stringList(key).toSet() + bookIds).sorted(), "CollectionBooksDroppedIntoChild", destination.id)
                clearSelection()
                true
            }
            is LibraryDropDestination.Root -> {
                if (!payload.startsWith(SHORTCUT_DRAG_PREFIX)) return@drop false
                val id = payload.removePrefix(SHORTCUT_DRAG_PREFIX)
                val updated = childItems.toMutableList()
                val from = updated.indexOfFirst { it.id == id }
                if (from < 0) return@drop false
                val item = updated.removeAt(from)
                updated.add(destination.index.coerceIn(0, updated.size), item)
                persistChildren(updated, "CollectionChildMoved")
                true
            }
            is LibraryDropDestination.Library -> {
                if (sortMode != LibraryBookSortMode.CUSTOM || !payload.startsWith(BOOK_DRAG_PREFIX) || bookIds.isEmpty()) return@drop false
                val updated = shownBooksInOrder.toMutableList()
                val moved = updated.filter { it.id in bookIds }
                updated.removeAll(moved.toSet())
                updated.addAll(destination.index.coerceIn(0, updated.size), moved)
                orderedBookIds = updated.map { it.id }
                repository.putStringList("library.collection.$collectionId.order", orderedBookIds, "CollectionBooksReordered", collectionId)
                clearSelection()
                true
            }
            is LibraryDropDestination.Book -> false
        }
    }
    BackHandler(selectionKind != null) { clearSelection() }
    BackHandler(sheet) { sheet = false }
    val selectedCount = selectedBookIds.size + selectedCollectionIds.size
    val selectionBar = selectionKind?.let { kind ->
        val allIds = if (kind == LibrarySelectionKind.BOOK) shownBooksInOrder.map { it.id }.toSet() else childItems.map { it.id }.toSet()
        val selectedIds = if (kind == LibrarySelectionKind.BOOK) selectedBookIds else selectedCollectionIds
        val allSelected = allIds.isNotEmpty() && selectedIds.containsAll(allIds)
        AtlasSelectionBar(
            count = selectedCount,
            onClose = ::clearSelection,
            allSelected = allSelected,
            onToggleAll = {
                if (kind == LibrarySelectionKind.BOOK) selectedBookIds = if (allSelected) emptySet() else allIds
                else selectedCollectionIds = if (allSelected) emptySet() else allIds
            },
            bulkActions = if (kind == LibrarySelectionKind.BOOK) {
                buildList {
                    if (depth < 2) {
                        add(AtlasTopBarAction(AtlasIcons.FolderAdd, "用所选新建子收藏夹") {
                            pendingChildCollectionName = ""
                        })
                    }
                    add(AtlasTopBarAction(AtlasIcons.FolderMove, "加入其他收藏夹") { sheet = true })
                    add(AtlasTopBarAction(AtlasIcons.Delete, "移出此收藏夹") { pendingBookRemoval = selectedBookIds })
                }
            } else {
                listOf(
                    AtlasTopBarAction(AtlasIcons.FolderMove, "移到上级") {
                        val parentId = repository.string("library.collection.$collectionId.parent")
                        selectedCollectionIds.forEach { id ->
                            repository.putString("library.collection.$id.parent", parentId, "CollectionMovedToParent", id)
                        }
                        persistChildren(childItems.filterNot { it.id in selectedCollectionIds }, "CollectionChildrenMovedToParent")
                        clearSelection()
                    },
                    AtlasTopBarAction(AtlasIcons.Delete, "删除收藏夹") { pendingChildRemoval = selectedCollectionIds },
                )
            },
        )
    }
    val page = pageSlice(
        "manual-$collectionId-${layout.name}-${sortMode.name}-${sortDirection.name}",
        shownBooksInOrder,
        if (layout == AtlasLayout.GRID) 9 else if (layout == AtlasLayout.COMPACT) 8 else 6,
    )
    val shown = if (eInk) page.items else shownBooksInOrder
    val mutation = if (context.state == AtlasPageState.MUTATION) {
        AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "已更新《纸灯巷的守夜人》的收藏夹归属（2 个）")
    } else null
    val activeDragItem = dragCoordinator.activeBookIds.firstOrNull()?.let { id -> books.firstOrNull { it.id == id }?.let(::bookShortcut) }
        ?: dragCoordinator.activePayload?.removePrefix(SHORTCUT_DRAG_PREFIX)?.let { id -> childItems.firstOrNull { it.id == id } }
    Box(modifier.fillMaxSize().libraryDragOverlayHost(dragCoordinator)) {
        AtlasScaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    AtlasTopBar(
                        title = collection.name,
                        subtitle = if (childItems.isEmpty()) "${books.size} 本" else "${books.size} 本 · ${childItems.size} 个收藏夹",
                        onUp = navigation.up,
                        selection = selectionBar,
                        actionBudgetOverride = 3,
                        actions = listOf(
                            AtlasTopBarAction(
                                layout.currentLayoutIcon(),
                                layout.layoutToggleContentDescription(),
                            ) {
                                layout = layout.nextAtlasLayout()
                                repository.putString("library.collection.$collectionId.layout", layout.name, "CollectionLayoutChanged", collectionId)
                            },
                        ),
                        overflow = listOf(
                            AtlasOverflowItem("排序：${sortMode.summary(sortDirection)}") { sortOpen = true },
                            AtlasOverflowItem("编辑收藏夹规则") { navigation.navigate(AtlasRoute.LIBRARY_COLLECTION_RULE) },
                            AtlasOverflowItem("添加书籍") { sheet = true },
                        ),
                    )
                    OverlayState(context.state, mutation)
                }
            },
            footer = if (eInk && context.state.showsContent && books.isNotEmpty()) {
                { PaginationFooter(page.page, page.pages, page.setPage) }
            } else null,
        ) {
            StateOrContent(
                context.state,
                "收藏夹为空",
                null,
                "收藏夹加载失败",
                "本地收藏夹索引不可用；收藏关系未受影响。",
                "添加书籍",
                { sheet = true },
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (childItems.isNotEmpty()) {
                        Text("收藏夹（${childItems.size}）", modifier = Modifier.padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm), style = MaterialTheme.typography.titleMedium)
                        ShortcutExpandedGrid(
                            items = childItems,
                            locked = eInk,
                            onOpen = { item ->
                                val childDepth = depth + 1
                                repository.putString("library.collection.level.$childDepth.id", item.id, "CollectionChildOpened", item.id)
                                navigation.navigate(item.route)
                            },
                            dragCoordinator = dragCoordinator,
                            modifier = Modifier.fillMaxWidth().height(196.dp),
                            selectionKind = selectionKind,
                            selectedCollectionIds = selectedCollectionIds,
                            onToggleCollection = ::toggleCollection,
                            conflictSignal = conflictSignal,
                            conflictTargetKey = conflictTarget,
                            acceptBookAtRoot = false,
                        )
                    }
                    Text("书籍（${books.size}）", modifier = Modifier.padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm), style = MaterialTheme.typography.titleMedium)
                    BookSurface(
                        context = context,
                        books = shown,
                        layout = layout,
                        selected = if (selectionKind == LibrarySelectionKind.BOOK) selectedBookIds else emptySet(),
                        toggle = ::toggleBook,
                        selectionActive = selectionKind == LibrarySelectionKind.BOOK,
                        selectionConflictTarget = conflictTarget,
                        selectionConflictSignal = conflictSignal,
                        interaction = LibraryBookInteractionCapabilities(drag = !eInk, reorder = sortMode == LibraryBookSortMode.CUSTOM),
                        dragCoordinator = dragCoordinator,
                        dragDescriptionSuffix = if (sortMode == LibraryBookSortMode.CUSTOM) "长按多选，移动可拖动排序或移出收藏夹" else "长按多选，移动可拖动移出收藏夹",
                        onLongPress = { toggleBook(it.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        ShortcutDeleteDropTarget(
            visible = dragCoordinator.activePayload != null,
            label = if (dragCoordinator.activePayload?.startsWith(SHORTCUT_DRAG_PREFIX) == true) "删除收藏夹" else "移出此收藏夹",
            active = dragCoordinator.isOverDelete,
            dragCoordinator = dragCoordinator,
        )
        ShortcutDragGhost(activeItem = activeDragItem, dragCoordinator = dragCoordinator, bookLayout = layout, modifier = Modifier.fillMaxSize())
    }
    if (sortOpen) {
        LibrarySortDialog(
            mode = sortMode,
            direction = sortDirection,
            onModeChange = {
                sortMode = it
                repository.putString("library.collection.$collectionId.sort", it.name, "CollectionSortChanged", collectionId)
            },
            onDirectionChange = {
                sortDirection = it
                repository.putString(
                    "library.collection.$collectionId.sort.direction",
                    it.name,
                    "CollectionSortDirectionChanged",
                    collectionId,
                )
            },
            onDismiss = { sortOpen = false },
        )
    }
    pendingChildCollectionName?.let { name ->
        FullDialog("新建子收藏夹", { pendingChildCollectionName = null }) {
            OutlinedTextField(
                value = name,
                onValueChange = { pendingChildCollectionName = it },
                label = { Text("收藏夹名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DialogButtons("创建", {
                if (name.isNotBlank()) {
                    createChildCollection(name)
                    pendingChildCollectionName = null
                }
            }, { pendingChildCollectionName = null })
        }
    }
    if (pendingBookRemoval.isNotEmpty()) {
        FullDialog("从「${collection.name}」移出 ${pendingBookRemoval.size} 本书？", { pendingBookRemoval = emptySet() }, destructive = true) {
            Text("仅移除当前收藏关系；书籍仍保留在总书架。")
            DialogButtons("移出", {
                persistBooks(books.filterNot { it.id in pendingBookRemoval }, "CollectionBooksRemoved")
                pendingBookRemoval = emptySet()
                clearSelection()
            }, { pendingBookRemoval = emptySet() })
        }
    }
    if (pendingChildRemoval.isNotEmpty()) {
        FullDialog("删除 ${pendingChildRemoval.size} 个收藏夹？", { pendingChildRemoval = emptySet() }, destructive = true) {
            Text("书籍仍保留在总书架；收藏关系会被移除。")
            DialogButtons("删除", {
                persistChildren(childItems.filterNot { it.id in pendingChildRemoval }, "CollectionChildrenDeleted")
                pendingChildRemoval = emptySet()
                clearSelection()
            }, { pendingChildRemoval = emptySet() })
        }
    }
    if (sheet) MembershipSheet(context, { sheet = false }) { sheet = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembershipSheet(context: AtlasContext, dismiss: () -> Unit, done: () -> Unit) {
    val repository = prototypeRepository()
    val full = LocalAtlasEnvironment.current.eInk || context.isVariant('C', "b")
    var members by remember {
        mutableStateOf(LibraryAtlasFixtures.membershipRows.associate { it.id to it.member })
    }
    val content: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("管理收藏夹成员", style = MaterialTheme.typography.titleLarge)
                Text("《纸灯巷的守夜人》", style = MaterialTheme.typography.bodySmall)
            }
            if (full) AtlasButton(AtlasStrings.CLOSE, dismiss, style = AtlasButtonStyle.SECONDARY)
        }
        LibraryAtlasFixtures.membershipRows.forEach { row ->
            val checked = members[row.id] ?: row.member
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !row.smartLocked) { members = members + (row.id to !checked) }
                    .heightIn(min = 48.dp)
                    .padding(start = AtlasSpacing.Md + AtlasSpacing.Lg * row.depth, end = AtlasSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked, null, enabled = !row.smartLocked)
                Text(row.name, modifier = Modifier.weight(1f).padding(start = AtlasSpacing.Md))
                if (row.smartLocked) AtlasChip("规则维护")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = AtlasSpacing.Md),
            horizontalArrangement = Arrangement.End,
        ) {
            AtlasButton(AtlasStrings.CANCEL, dismiss, style = AtlasButtonStyle.TEXT)
            AtlasButton("完成", {
                repository.putStringList("collection.memberships", members.filterValues { it }.keys.toList(), "CollectionMembershipChanged")
                done()
            })
        }
    }
    if (full) {
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(AtlasSpacing.Md), content = content)
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = dismiss) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(AtlasSpacing.Md),
                content = content,
            )
        }
    }
}

@Composable
private fun SmartCollection(
    context: AtlasContext,
    modifier: Modifier,
    collection: LibraryAtlasFixtures.CollectionFixture,
) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    val eInk = LocalAtlasEnvironment.current.eInk
    var removedBookIds by remember(collection.id) {
        mutableStateOf(repository.stringList("library.removed.bookIds").toSet())
    }
    val books = LibraryAtlasFixtures.booksForCollection(collection).filterNot { it.id in removedBookIds }
    var layout by rememberSaveable(context.profile.name, collection.id) {
        mutableStateOf(
            AtlasLayout.entries.firstOrNull { it.name == repository.string("library.collection.${collection.id}.layout") }
                ?: context.layout ?: if (eInk) AtlasLayout.GRID else AtlasLayout.LIST,
        )
    }
    var sortMode by rememberSaveable(collection.id) {
        mutableStateOf(
            LibraryBookSortMode.entries.firstOrNull {
                it.name == repository.string("library.collection.${collection.id}.sort")
            }?.takeUnless { it == LibraryBookSortMode.CUSTOM } ?: LibraryBookSortMode.RECENTLY_READ,
        )
    }
    var sortDirection by rememberSaveable(collection.id) {
        mutableStateOf(
            LibraryBookSortDirection.entries.firstOrNull {
                it.name == repository.string("library.collection.${collection.id}.sort.direction")
            } ?: LibraryBookSortDirection.DESCENDING,
        )
    }
    val sortedBooks = books.orderedForLibrary(sortMode, sortDirection)
    var selectionActive by remember(context.state) { mutableStateOf(context.state == AtlasPageState.SELECTION) }
    var selected by remember(context.state) {
        mutableStateOf(if (context.state == AtlasPageState.SELECTION) sortedBooks.take(3).map { it.id }.toSet() else emptySet())
    }
    var sortOpen by rememberSaveable(collection.id) { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var pendingCollectionName by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf(emptySet<String>()) }
    fun clearSelection() {
        selectionActive = false
        selected = emptySet()
    }
    fun toggleBook(id: String) {
        selectionActive = true
        selected = if (id in selected) selected - id else selected + id
    }
    BackHandler(selectionActive) { clearSelection() }
    BackHandler(sheet) { sheet = false }
    val selectionBar = if (selectionActive) {
        val allIds = sortedBooks.map { it.id }.toSet()
        val allSelected = allIds.isNotEmpty() && selected.containsAll(allIds)
        AtlasSelectionBar(
            count = selected.size,
            onClose = ::clearSelection,
            allSelected = allSelected,
            onToggleAll = { selected = if (allSelected) emptySet() else allIds },
            bulkActions = listOf(
                AtlasTopBarAction(AtlasIcons.FolderAdd, "用所选新建收藏夹") { pendingCollectionName = "" },
                AtlasTopBarAction(AtlasIcons.FolderMove, "加入其他收藏夹") { sheet = true },
                AtlasTopBarAction(AtlasIcons.Delete, "移出总书架") { pendingRemoval = selected },
            ),
        )
    } else null
    val page = pageSlice(
        "smart-${collection.id}-${layout.name}-${sortMode.name}-${sortDirection.name}",
        sortedBooks,
        if (layout == AtlasLayout.GRID) 9 else if (layout == AtlasLayout.COMPACT) 8 else 6,
    )
    val shown = if (eInk) page.items else sortedBooks
    val ruleConditions = collection.ruleSummary.orEmpty().split(" · ").filter(String::isNotBlank)
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            AtlasTopBar(
                title = collection.name,
                subtitle = "${books.size} 本",
                onUp = navigation.up,
                selection = selectionBar,
                actionBudgetOverride = 3,
                actions = listOf(
                    AtlasTopBarAction(
                        layout.currentLayoutIcon(),
                        layout.layoutToggleContentDescription(),
                    ) {
                        layout = layout.nextAtlasLayout()
                        repository.putString("library.collection.${collection.id}.layout", layout.name, "SmartCollectionLayoutChanged", collection.id)
                    },
                ),
                overflow = listOf(
                    AtlasOverflowItem("排序：${sortMode.summary(sortDirection)}") { sortOpen = true },
                    AtlasOverflowItem("编辑规则") {
                        repository.record("SmartRuleEditOpened", collection.id, "success")
                        navigation.navigate(AtlasRoute.LIBRARY_COLLECTION_RULE)
                    },
                ),
            )
        },
        footer = if (eInk && context.state.showsContent && books.isNotEmpty()) {
            { PaginationFooter(page.page, page.pages, page.setPage) }
        } else null,
    ) {
        StateOrContent(
            context.state,
            "没有书籍匹配当前规则",
            null,
            "规则求值失败",
            "规则定义未受影响；可重试匹配。",
        ) {
            Column(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(AtlasSpacing.Md),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(AtlasSpacing.Md)) {
                        Text("规则", style = MaterialTheme.typography.titleMedium)
                        ruleConditions.forEach { condition ->
                            Text(condition, modifier = Modifier.padding(top = AtlasSpacing.Xs))
                        }
                        AtlasButton("编辑规则", {
                            repository.record("SmartRuleEditOpened", collection.id, "success")
                            navigation.navigate(AtlasRoute.LIBRARY_COLLECTION_RULE)
                        }, modifier = Modifier.padding(top = AtlasSpacing.Sm), style = AtlasButtonStyle.SECONDARY)
                    }
                }
                BookSurface(
                    context = context,
                    books = shown,
                    layout = layout,
                    selected = selected,
                    toggle = ::toggleBook,
                    selectionActive = selectionActive,
                    interaction = LibraryBookInteractionCapabilities(drag = false, reorder = false),
                    onLongPress = { toggleBook(it.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (sortOpen) {
        LibrarySortDialog(
            mode = sortMode,
            direction = sortDirection,
            onModeChange = {
                sortMode = it
                repository.putString("library.collection.${collection.id}.sort", it.name, "SmartCollectionSortChanged", collection.id)
            },
            onDirectionChange = {
                sortDirection = it
                repository.putString(
                    "library.collection.${collection.id}.sort.direction",
                    it.name,
                    "SmartCollectionSortDirectionChanged",
                    collection.id,
                )
            },
            onDismiss = { sortOpen = false },
            allowCustom = false,
        )
    }
    pendingCollectionName?.let { name ->
        FullDialog("新建收藏夹", { pendingCollectionName = null }) {
            OutlinedTextField(
                value = name,
                onValueChange = { pendingCollectionName = it },
                label = { Text("收藏夹名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DialogButtons("创建", {
                if (name.isNotBlank()) {
                    createPersistedUserCollection(repository, name, selected)
                    repository.record("SmartSelectionCollectionCreated", collection.id, "success")
                    pendingCollectionName = null
                    clearSelection()
                }
            }, { pendingCollectionName = null })
        }
    }
    if (pendingRemoval.isNotEmpty()) {
        FullDialog("移出总书架 ${pendingRemoval.size} 本书？", { pendingRemoval = emptySet() }, destructive = true) {
            Text("书籍文件、收藏关系和阅读进度都会从当前本地书架视图中移除。")
            DialogButtons("移出", {
                removedBookIds += pendingRemoval
                repository.putStringList("library.removed.bookIds", removedBookIds.sorted(), "SmartCollectionBooksRemovedFromLibrary")
                pendingRemoval = emptySet()
                clearSelection()
            }, { pendingRemoval = emptySet() })
        }
    }
    if (sheet) MembershipSheet(context, { sheet = false }) { sheet = false }
}

// -------------------------------------------------------------------------------------------
// #9 — library/collections/{id}/rule
// -------------------------------------------------------------------------------------------

@Composable
private fun CollectionRule(context: AtlasContext, modifier: Modifier) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    var groups by remember(context.state) { mutableStateOf(LibraryAtlasFixtures.ruleGroups) }
    var dirty by remember(context.state) { mutableStateOf(context.state == AtlasPageState.MODAL) }
    var confirmOpen by remember(context.state) { mutableStateOf(context.state == AtlasPageState.MODAL) }
    var tutorialOpen by remember(context.tutorial) { mutableStateOf(context.tutorial) }
    BackHandler(dirty && !confirmOpen) { confirmOpen = true }
    BackHandler(confirmOpen) { confirmOpen = false }
    val count = groups.sumOf { it.conditions.size }
    val mutation = if (context.state == AtlasPageState.MUTATION) {
        AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "规则已保存 · 重新匹配到 9 本书")
    } else {
        null
    }
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            Column {
                AtlasTopBar(
                    title = "规则收藏夹",
                    subtitle = "从预设开始 · 科幻·未读 · 条件 $count / ${LibraryAtlasFixtures.RULE_CONDITION_CAP}",
                    onUp = { if (dirty) confirmOpen = true else navigation.up() },
                    actions = listOf(AtlasTopBarAction(AtlasIcons.Check, "保存") {
                        repository.putStringList(
                            "collection.rule.values",
                            groups.flatMap { group -> group.conditions.map { it.value } },
                            "CollectionRuleSaved",
                            "smart-sci-fi",
                        )
                        dirty = false
                    }),
                    overflow = listOf(
                        AtlasOverflowItem("功能说明") { tutorialOpen = true },
                        AtlasOverflowItem("重置为已保存") {
                            groups = LibraryAtlasFixtures.ruleGroups
                            dirty = false
                            repository.record("CollectionRuleReset", "smart-sci-fi", "success")
                        },
                    ),
                )
                OverlayState(context.state, mutation)
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(AtlasSpacing.Md),
            ) {
                groups.forEachIndexed { groupIndex, group ->
                    if (groupIndex > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            AtlasChip("或（满足任一分组）")
                        }
                    }
                    Text(
                        "分组 ${groupIndex + 1} · 组内「且」",
                        modifier = Modifier.padding(vertical = AtlasSpacing.Sm),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    group.conditions.forEachIndexed { conditionIndex, condition ->
                        OutlinedTextField(
                            value = condition.value,
                            onValueChange = { value ->
                                dirty = true
                                groups = groups.mapIndexed { gi, oldGroup ->
                                    if (gi != groupIndex) oldGroup else oldGroup.copy(
                                        conditions = oldGroup.conditions.mapIndexed { ci, old ->
                                            if (ci == conditionIndex) old.copy(value = value) else old
                                        },
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = AtlasSpacing.Sm),
                            label = { Text("条件 ${groupIndex * 4 + conditionIndex + 1} · ${condition.field} ${condition.operator}") },
                            isError = condition.error != null,
                            supportingText = condition.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            trailingIcon = condition.error?.let {
                                { Icon(AtlasIcons.Warning, "条件错误", tint = MaterialTheme.colorScheme.error) }
                            },
                        )
                    }
                    AtlasButton("从预设添加条件", {
                        dirty = true
                        val preset = LibraryAtlasFixtures.ruleGroups.first().conditions.first()
                        groups = groups.mapIndexed { index, group ->
                            if (index == 0 && count < LibraryAtlasFixtures.RULE_CONDITION_CAP) group.copy(conditions = group.conditions + preset) else group
                        }
                    }, modifier = Modifier.padding(top = AtlasSpacing.Xs), style = AtlasButtonStyle.TEXT)
                    AtlasButton("添加条件", {
                        dirty = true
                        val blank = LibraryAtlasFixtures.RuleConditionFixture("标签", "包含", "")
                        groups = groups.mapIndexed { index, group ->
                            if (index == 0 && count < LibraryAtlasFixtures.RULE_CONDITION_CAP) group.copy(conditions = group.conditions + blank) else group
                        }
                    }, modifier = Modifier.padding(top = AtlasSpacing.Sm), style = AtlasButtonStyle.SECONDARY)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = AtlasSpacing.Lg),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        "条件 $count / ${LibraryAtlasFixtures.RULE_CONDITION_CAP} · 最长值 ${LibraryAtlasFixtures.RULE_LONGEST_VALUE} / ${LibraryAtlasFixtures.RULE_VALUE_CAP} 字符",
                        modifier = Modifier.padding(AtlasSpacing.Md),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (tutorialOpen) {
        AtlasFeatureIntroduction(
            featureId = "smart-rule-editor",
            tutorialVersion = 1,
            title = "功能说明：智能收藏规则",
            summary = "规则是受限、可读的本地筛选，不执行 SQL、脚本或来源代码。",
            points = listOf(
                "本地标签与指定来源标签必须明确选择。",
                "规则变化只改变派生结果，不删除书籍标注。",
                "保存前会显示逐项错误和可读摘要。",
                "离开未保存编辑会再次确认。",
            ),
            onDismiss = { tutorialOpen = false },
        )
    }
    if (confirmOpen) {
        FullDialog("放弃未保存的修改？", { confirmOpen = false }, destructive = true) {
            Text("规则条件有未保存的修改。放弃后将恢复到上次保存的内容。")
            DialogButtons(
                "放弃修改",
                {
                    groups = LibraryAtlasFixtures.ruleGroups
                    dirty = false
                    confirmOpen = false
                },
                { confirmOpen = false },
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// #10 — library/tags
// -------------------------------------------------------------------------------------------

@Composable
private fun LibraryTags(context: AtlasContext, modifier: Modifier) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    var dialog by remember(context.state) {
        mutableStateOf(if (context.state == AtlasPageState.MODAL) "merge" else null)
    }
    var ownership by rememberSaveable { mutableStateOf("local") }
    var layout by rememberSaveable(context.layout?.name) { mutableStateOf(context.layout ?: AtlasLayout.COMPACT) }
    var target by remember { mutableStateOf(LibraryAtlasFixtures.localTags.first()) }
    var renameValue by remember { mutableStateOf(target.name) }
    BackHandler(dialog != null) { dialog = null }
    val mutation = if (context.state == AtlasPageState.MUTATION) {
        AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "标签已合并")
    } else null
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            Column {
                AtlasTopBar(
                    title = "标签",
                    onUp = navigation.up,
                    actions = listOf(
                        AtlasTopBarAction(AtlasIcons.Search, "搜索标签") { repository.record("TagSearchOpened", "library/tags", "success") },
                        AtlasTopBarAction(AtlasIcons.Sort, "排序标签") { repository.record("TagsSorted", "library/tags", "success") },
                        AtlasTopBarAction(layout.currentLayoutIcon(), layout.layoutToggleContentDescription()) {
                            layout = if (layout == AtlasLayout.COMPACT) AtlasLayout.LIST else AtlasLayout.COMPACT
                            repository.putString("tags.layout", layout.name, "TagLayoutChanged")
                        },
                    ),
                    overflow = listOf(AtlasOverflowItem("新建标签") {
                        renameValue = ""
                        dialog = "create"
                    }),
                )
                OverlayState(context.state, mutation)
            }
        },
    ) {
        StateOrContent(
            context.state,
            "还没有本地标签",
            "在书籍详情中添加标签后，会显示在这里。",
            "标签加载失败",
            "本地标签索引不可用；书籍上的标签未受影响。",
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().padding(AtlasSpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                ) {
                    FilterChip(ownership == "local", { ownership = "local"; repository.putString("tags.ownership", "local", "TagOwnershipChanged") }, { Text("本地") }, modifier = Modifier.weight(1f))
                    FilterChip(ownership == "source", { ownership = "source"; repository.putString("tags.ownership", "source", "TagOwnershipChanged") }, { Text("来源") }, modifier = Modifier.weight(1f))
                }
                if (layout == AtlasLayout.COMPACT) {
                    FlowRow(
                        modifier = Modifier.padding(AtlasSpacing.Md),
                        horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                    ) {
                        if (ownership == "local") {
                            LibraryAtlasFixtures.localTags.forEach { tag ->
                                FilterChip(
                                    selected = false,
                                    onClick = { target = tag; renameValue = tag.name; dialog = "rename" },
                                    label = { Text(tag.name) },
                                )
                            }
                        } else {
                            LibraryAtlasFixtures.sourceTagGroups.forEach { group ->
                                group.tags.forEach { tag ->
                                    FilterChip(selected = false, onClick = {}, enabled = false, label = { Text(tag.name) })
                                }
                            }
                        }
                    }
                } else if (ownership == "local") {
                    LibraryAtlasFixtures.localTags.forEach { tag ->
                        TagManagerRow(tag) { action -> target = tag; renameValue = tag.name; dialog = action }
                    }
                } else {
                    LibraryAtlasFixtures.sourceTagGroups.forEach { group ->
                        group.tags.forEach { tag ->
                            ListItem(
                                headlineContent = { Text(tag.name) },
                                supportingContent = { Text("${tag.bookCount} 本") },
                                trailingContent = { AtlasChip("只读") },
                            )
                        }
                    }
                }
            }
        }
    }
    when (dialog) {
        "rename" -> FullDialog("重命名标签", { dialog = null }) {
            OutlinedTextField(
                renameValue,
                { renameValue = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新名称") },
                supportingText = { Text("最多 256 个字符；名称冲突会转为合并确认。") },
            )
            DialogButtons("重命名", {
                repository.putString("tag.${target.id}.name", renameValue, "TagRenamed", target.id)
                dialog = null
            }, { dialog = null })
        }
        "create" -> FullDialog("新建标签", { dialog = null }) {
            OutlinedTextField(renameValue, { renameValue = it }, modifier = Modifier.fillMaxWidth(), label = { Text("名称") })
            DialogButtons("新建", {
                repository.putStringList("tags.created", repository.stringList("tags.created") + renameValue, "TagCreated", renameValue)
                dialog = null
            }, { dialog = null })
        }
        "merge" -> FullDialog("标签名称冲突", { dialog = null }, destructive = true) {
            Text("名称冲突。确认后将使用目标标签；书籍、评分与收藏关系保留。")
            DialogButtons("仍然合并", { repository.record("TagsMerged", target.id, "success"); dialog = null }, { dialog = null })
        }
        "delete" -> FullDialog("删除标签「${target.name}」？", { dialog = null }, destructive = true) {
            Text("书籍、评分与收藏关系保留。")
            DialogButtons("删除", { repository.putBoolean("tag.${target.id}.deleted", true, "TagDeleted", target.id); dialog = null }, { dialog = null })
        }
    }
}

@Composable
private fun TagManagerRow(
    tag: LibraryAtlasFixtures.TagFixture,
    action: (String) -> Unit,
) {
    var open by remember(tag.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = AtlasSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AtlasIcons.Tune, null, modifier = Modifier.size(24.dp))
        Text(tag.name, modifier = Modifier.weight(1f).padding(start = AtlasSpacing.Md))
        Text("${tag.bookCount} 本", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(AtlasSpacing.Sm))
        Box {
            AtlasIconButton(AtlasIcons.Overflow, "标签「${tag.name}」操作", { open = true })
            DropdownMenu(open, { open = false }) {
                DropdownMenuItem({ Text("重命名") }, {
                    open = false
                    action("rename")
                })
                DropdownMenuItem({ Text("合并到…") }, {
                    open = false
                    action("merge")
                })
                DropdownMenuItem({ Text("删除…") }, {
                    open = false
                    action("delete")
                })
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// #11 — library/mirror/{bindingId}
// -------------------------------------------------------------------------------------------

private const val MIRROR_LOCAL_NODE_ID = "mirror-local-organization"

private fun mirrorShortcut(
    node: LibraryAtlasFixtures.MirrorNodeFixture,
    route: AtlasRoute,
    booksByTitle: Map<String, AtlasBook>,
): Rc21ShortcutItem = Rc21ShortcutItem(
    id = node.id,
    label = node.name,
    supporting = "${node.childCount} 项",
    kind = ShortcutKind.MIRROR,
    icon = AtlasIcons.Folder,
    route = route,
    collectionBooks = node.children
        .filter { it.kind == LibraryAtlasFixtures.MirrorNodeKind.BOOK }
        .mapNotNull { booksByTitle[it.name] }
        .take(3),
)

@Composable
private fun LibraryMirror(context: AtlasContext, modifier: Modifier) {
    val bindingDefault = if (context.libraryView == AtlasLibraryView.MIRROR) "mirror-bamboo" else "mirror-pine"
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val eInk = LocalAtlasEnvironment.current.eInk
    val bindingId = repository.string("library.mirror.binding", bindingDefault)
    val binding = if (bindingId == LibraryAtlasFixtures.mirrorBamboo.id) {
        LibraryAtlasFixtures.mirrorBamboo
    } else {
        LibraryAtlasFixtures.mirrorPine
    }
    val depth = when (context.route) {
        AtlasRoute.LIBRARY_MIRROR -> 0
        AtlasRoute.LIBRARY_MIRROR_FOLDER -> 1
        else -> 2
    }
    val nodeId = if (depth == 0) null else repository.string("library.mirror.level.$depth.id")
    val currentNode = nodeId?.let(binding.roots::findMirrorNode)
    val localPage = nodeId == MIRROR_LOCAL_NODE_ID
    val pageNodes = currentNode?.children ?: if (depth == 0) binding.roots else emptyList()
    val folderNodes = pageNodes.filter { it.kind == LibraryAtlasFixtures.MirrorNodeKind.FOLDER }
    val bookNodes = pageNodes.filter { it.kind == LibraryAtlasFixtures.MirrorNodeKind.BOOK }
    val bookCatalog = (LibraryAtlasFixtures.viewFixture(AtlasLibraryView.MIRROR).books +
        LibraryAtlasFixtures.viewFixture(AtlasLibraryView.ALL).books).associateBy(AtlasBook::title)
    val books = bookNodes.map { node ->
        val template = bookCatalog[node.name] ?: LibraryAtlasFixtures.viewFixture(AtlasLibraryView.MIRROR).books.first()
        template.copy(id = "mirror:${binding.id}:${node.id}", title = node.name, source = binding.source)
    }
    var layout by rememberSaveable(context.profile.name, binding.id, nodeId) {
        mutableStateOf(
            AtlasLayout.entries.firstOrNull { it.name == repository.string("library.mirror.${binding.id}.layout") }
                ?: context.layout ?: if (eInk) AtlasLayout.GRID else AtlasLayout.LIST,
        )
    }
    var sortMode by rememberSaveable(binding.id, nodeId) {
        mutableStateOf(
            LibraryBookSortMode.entries.firstOrNull {
                it.name == repository.string("library.mirror.${binding.id}.sort")
            }?.takeUnless { it == LibraryBookSortMode.CUSTOM } ?: LibraryBookSortMode.TITLE,
        )
    }
    var sortDirection by rememberSaveable(binding.id, nodeId) {
        mutableStateOf(
            LibraryBookSortDirection.entries.firstOrNull {
                it.name == repository.string("library.mirror.${binding.id}.sort.direction")
            } ?: LibraryBookSortDirection.ASCENDING,
        )
    }
    val sortedBooks = books.orderedForLibrary(sortMode, sortDirection)
    var sortOpen by rememberSaveable(binding.id, nodeId) { mutableStateOf(false) }
    var disableOpen by remember(context.state) { mutableStateOf(context.state == AtlasPageState.MODAL) }
    var localOrganizationCreated by rememberSaveable(binding.id) {
        mutableStateOf(repository.boolean("mirror.${binding.id}.localOrganization"))
    }
    val dragCoordinator = remember { LibraryDragCoordinator() }
    val coroutineScope = rememberCoroutineScope()
    val calibrate: () -> Unit = {
        coroutineScope.launch { runtime.scenarios.run("mirror-calibration", binding.id) }
    }
    BackHandler(disableOpen) { disableOpen = false }
    val calibration = when (context.variant?.option) {
        "b" -> LibraryAtlasFixtures.CalibrationPhase.SUCCESS
        "c" -> LibraryAtlasFixtures.CalibrationPhase.FAILED
        else -> LibraryAtlasFixtures.CalibrationPhase.WORKING
    }
    val mutation = if (context.state == AtlasPageState.MUTATION) {
        when (calibration) {
            LibraryAtlasFixtures.CalibrationPhase.WORKING -> AtlasMutationStatus(AtlasMutationPhase.WORKING, LibraryAtlasFixtures.calibrationMessage(calibration))
            LibraryAtlasFixtures.CalibrationPhase.SUCCESS -> AtlasMutationStatus(AtlasMutationPhase.SUCCESS, LibraryAtlasFixtures.calibrationMessage(calibration))
            LibraryAtlasFixtures.CalibrationPhase.FAILED -> AtlasMutationStatus(AtlasMutationPhase.ERROR, LibraryAtlasFixtures.calibrationMessage(calibration), "重新校准", calibrate)
        }
    } else null
    val mirrorState = if (context.state == AtlasPageState.UNRESOLVED) AtlasPageState.ERROR else context.state
    val nextDepth = depth + 1
    val nextRoute = mirrorRouteForDepth(nextDepth)
    val folderItems = buildList {
        addAll(folderNodes.map { mirrorShortcut(it, nextRoute, bookCatalog) })
        if (depth == 0 && localOrganizationCreated) {
            add(
                Rc21ShortcutItem(
                    id = MIRROR_LOCAL_NODE_ID,
                    label = "本地整理",
                    supporting = "本地",
                    kind = ShortcutKind.MIRROR,
                    icon = AtlasIcons.Folder,
                    route = nextRoute,
                ),
            )
        }
    }
    val subtitle = when {
        localPage -> "本地"
        depth == 0 && binding.frozen -> "网站镜像 · ${AtlasStrings.FROZEN_MIRROR}"
        depth == 0 -> "网站镜像"
        folderItems.isEmpty() -> "${books.size} 本"
        else -> "${books.size} 本 · ${folderItems.size} 个收藏夹"
    }
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            Column {
                AtlasTopBar(
                    title = if (localPage) "本地整理" else currentNode?.name ?: binding.source.name,
                    subtitle = subtitle,
                    onUp = navigation.up,
                    actionBudgetOverride = 2,
                    actions = buildList {
                        if (!localPage) {
                            add(AtlasTopBarAction(layout.currentLayoutIcon(), layout.layoutToggleContentDescription()) {
                                layout = layout.nextAtlasLayout()
                                repository.putString("library.mirror.${binding.id}.layout", layout.name, "MirrorLayoutChanged", binding.id)
                            })
                        }
                        add(AtlasTopBarAction(AtlasIcons.Refresh, "校准镜像", calibrate))
                    },
                    overflow = buildList {
                        if (books.isNotEmpty()) {
                            add(AtlasOverflowItem("排序：${sortMode.summary(sortDirection)}") { sortOpen = true })
                        }
                        if (depth == 0 && !localOrganizationCreated) add(AtlasOverflowItem("新建本地整理") {
                            localOrganizationCreated = true
                            repository.putBoolean("mirror.${binding.id}.localOrganization", true, "MirrorLocalOrganizationCreated", binding.id)
                        })
                        add(AtlasOverflowItem(if (binding.frozen) "启用镜像" else "停用镜像…") { disableOpen = true })
                        add(AtlasOverflowItem("在浏览中打开来源") { navigation.selectRoot(AtlasFamily.SOURCE) })
                    },
                )
                if (binding.frozen) {
                    AtlasInfoBanner(AtlasBanner(AtlasStrings.FROZEN_MIRROR, "展示最后完整快照。"))
                }
                OverlayState(mirrorState, mutation)
            }
        },
    ) {
        if (localPage) {
            Column(Modifier.fillMaxSize()) {
                ListItem(
                    headlineContent = { Text("快捷书架") },
                    trailingContent = {
                        Switch(
                            checked = repository.boolean("mirror.${binding.id}.shortcut", false),
                            onCheckedChange = { repository.putBoolean("mirror.${binding.id}.shortcut", it, "MirrorShortcutChanged", binding.id) },
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("本地标签") },
                    trailingContent = { AtlasIconButton(AtlasIcons.Edit, "整理本地标签", { navigation.navigate(AtlasRoute.LIBRARY_TAGS) }) },
                )
            }
        } else {
            StateOrContent(
                mirrorState,
                "暂无内容",
                null,
                "快照加载失败",
                "最后完整快照保持不变。",
                "校准网站镜像",
                calibrate,
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (folderItems.isNotEmpty()) {
                        Text("收藏夹（${folderItems.size}）", modifier = Modifier.padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm), style = MaterialTheme.typography.titleMedium)
                        val rows = (folderItems.size + 2) / 3
                        ShortcutExpandedGrid(
                            items = folderItems,
                            locked = true,
                            onOpen = { item ->
                                repository.putString("library.mirror.level.$nextDepth.id", item.id, "MirrorFolderOpened", item.id)
                                navigation.navigate(item.route)
                            },
                            dragCoordinator = dragCoordinator,
                            modifier = Modifier.fillMaxWidth().height((196 * rows).dp),
                            acceptBookAtRoot = false,
                        )
                    }
                    if (books.isNotEmpty()) {
                        Text("书籍（${books.size}）", modifier = Modifier.padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm), style = MaterialTheme.typography.titleMedium)
                        BookSurface(
                            context = context,
                            books = sortedBooks,
                            layout = layout,
                            selected = emptySet(),
                            toggle = {},
                            interaction = LibraryBookInteractionCapabilities(multiSelect = false, longPress = false, drag = false, reorder = false),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
    if (sortOpen) {
        LibrarySortDialog(
            mode = sortMode,
            direction = sortDirection,
            onModeChange = {
                sortMode = it
                repository.putString("library.mirror.${binding.id}.sort", it.name, "MirrorSortChanged", binding.id)
            },
            onDirectionChange = {
                sortDirection = it
                repository.putString(
                    "library.mirror.${binding.id}.sort.direction",
                    it.name,
                    "MirrorSortDirectionChanged",
                    binding.id,
                )
            },
            onDismiss = { sortOpen = false },
            allowCustom = false,
        )
    }
    if (disableOpen) {
        FullDialog(if (binding.frozen) "启用镜像？" else "停用镜像？", { disableOpen = false }) {
            Text("只改变本地镜像状态；不会自动读取或写入网站。")
            DialogButtons(if (binding.frozen) "启用镜像" else "停用镜像", {
                repository.putBoolean("mirror.${binding.id}.enabled", binding.frozen, "MirrorEnabledChanged", binding.id)
                disableOpen = false
            }, { disableOpen = false })
        }
    }
}
