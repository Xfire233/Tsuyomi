/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasIconButton
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.BookGridCard
import org.tsuyomi.prototype.uiatlas.components.BookListItemRow
import org.tsuyomi.prototype.uiatlas.components.CompactBookListItem
import org.tsuyomi.prototype.uiatlas.components.LibraryBookDragPreview
import org.tsuyomi.prototype.uiatlas.components.LibraryBookInsertionGap
import org.tsuyomi.prototype.uiatlas.components.LibraryBookInteractionCapabilities
import org.tsuyomi.prototype.uiatlas.components.LibraryDragCoordinator
import org.tsuyomi.prototype.uiatlas.components.libraryBookDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryContentDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryDragSource
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment


@Composable
internal fun BookSurface(
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
                    scrollOrientation = Orientation.Vertical,
                    canRemove = true,
                    libraryReorderSource = true,
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