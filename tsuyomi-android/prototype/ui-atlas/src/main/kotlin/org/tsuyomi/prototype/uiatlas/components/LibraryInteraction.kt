/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing

/**
 * Layout-toggle invariant: the icon and accessibility label describe the current layout.
 * Activation advances to the next supported layout; the control never previews its destination.
 */
internal fun AtlasLayout.currentLayoutIcon(): ImageVector = when (this) {
    AtlasLayout.LIST -> AtlasIcons.LayoutList
    AtlasLayout.COMPACT -> AtlasIcons.LayoutCompact
    AtlasLayout.GRID -> AtlasIcons.LayoutGrid
}

internal fun AtlasLayout.currentLayoutLabel(): String = when (this) {
    AtlasLayout.LIST -> "列表"
    AtlasLayout.COMPACT -> "紧凑列表"
    AtlasLayout.GRID -> "网格"
}

internal fun AtlasLayout.layoutToggleContentDescription(): String =
    "当前布局：${currentLayoutLabel()}，点按切换布局"

internal fun AtlasLayout.nextAtlasLayout(): AtlasLayout =
    AtlasLayout.entries[(ordinal + 1) % AtlasLayout.entries.size]

internal enum class LibraryBookSortMode(val label: String) {
    CUSTOM("自定义"),
    TITLE("标题"),
    RECENTLY_READ("最近阅读"),
    RATING("评分"),
}

internal enum class LibraryBookSortDirection(val label: String) {
    ASCENDING("升序"),
    DESCENDING("降序"),
}

internal fun LibraryBookSortMode.summary(direction: LibraryBookSortDirection): String =
    if (this == LibraryBookSortMode.CUSTOM) label else "$label · ${direction.label}"

@Immutable
internal data class LibraryBookInteractionCapabilities(
    val multiSelect: Boolean = true,
    val longPress: Boolean = true,
    val drag: Boolean = false,
    val reorder: Boolean = false,
)

internal fun List<AtlasBook>.orderedForLibrary(
    mode: LibraryBookSortMode,
    direction: LibraryBookSortDirection,
    customOrder: List<String> = emptyList(),
): List<AtlasBook> = when (mode) {
    LibraryBookSortMode.CUSTOM -> {
        if (customOrder.isEmpty()) this
        else {
            val byId = associateBy(AtlasBook::id)
            customOrder.mapNotNull(byId::get) + filterNot { it.id in customOrder }
        }
    }
    LibraryBookSortMode.TITLE -> sortedWith { left, right ->
        val comparison = String.CASE_INSENSITIVE_ORDER.compare(left.title, right.title)
        if (direction == LibraryBookSortDirection.ASCENDING) comparison else -comparison
    }
    LibraryBookSortMode.RECENTLY_READ -> sortedWith(
        nullableLastComparator(direction, AtlasBook::lastReadAtEpochMillis),
    )
    LibraryBookSortMode.RATING -> sortedWith(
        nullableLastComparator(direction, AtlasBook::rating),
    )
}

private fun <T : Comparable<T>> nullableLastComparator(
    direction: LibraryBookSortDirection,
    selector: (AtlasBook) -> T?,
): Comparator<AtlasBook> = Comparator { left, right ->
    val leftValue = selector(left)
    val rightValue = selector(right)
    when {
        leftValue == null && rightValue == null -> String.CASE_INSENSITIVE_ORDER.compare(left.title, right.title)
        leftValue == null -> 1
        rightValue == null -> -1
        direction == LibraryBookSortDirection.ASCENDING -> leftValue.compareTo(rightValue)
        else -> rightValue.compareTo(leftValue)
    }
}

internal fun libraryDragPreviewSize(layout: AtlasLayout): DpSize = when (layout) {
    AtlasLayout.GRID -> DpSize(112.dp, 156.dp)
    AtlasLayout.LIST -> DpSize(300.dp, 88.dp)
    AtlasLayout.COMPACT -> DpSize(280.dp, 64.dp)
}

@Composable
internal fun LibraryBookDragPreview(
    book: AtlasBook,
    layout: AtlasLayout,
    batchCount: Int,
    modifier: Modifier = Modifier,
) {
    val size = libraryDragPreviewSize(layout)
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 14.dp,
    ) {
        if (layout == AtlasLayout.GRID) {
            Column(Modifier.fillMaxSize().padding(AtlasSpacing.Xs)) {
                AtlasCoverImage(
                    cover = book.cover,
                    title = book.title,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                )
                Text(
                    book.title,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            val coverWidth = if (layout == AtlasLayout.COMPACT) 36.dp else 48.dp
            Row(
                Modifier.fillMaxSize().padding(AtlasSpacing.Xs),
                horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtlasCoverImage(
                    cover = book.cover,
                    title = book.title,
                    modifier = Modifier.width(coverWidth).fillMaxSize(),
                )
                Column(Modifier.weight(1f)) {
                    Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (layout == AtlasLayout.LIST) {
                        Text(book.progressLabel ?: "书籍", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (batchCount > 1) {
            Box(Modifier.fillMaxSize().padding(AtlasSpacing.Xs), contentAlignment = Alignment.TopEnd) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text("$batchCount 本", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun LibraryBookInsertionGap(layout: AtlasLayout, modifier: Modifier = Modifier) {
    val height = when (layout) {
        AtlasLayout.GRID -> 156.dp
        AtlasLayout.LIST -> 88.dp
        AtlasLayout.COMPACT -> 64.dp
    }
    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("放到这里", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

internal enum class LibraryDropItemKind {
    ITEM,
    COLLECTION,
    BOOK,
}

internal sealed interface LibraryDropDestination {
    data class Root(val index: Int) : LibraryDropDestination
    data class Collection(val id: String) : LibraryDropDestination
    data class Book(val id: String) : LibraryDropDestination
    data class Library(val index: Int) : LibraryDropDestination
    data object Remove : LibraryDropDestination
}

private data class LibraryItemTargetBounds(
    val index: Int,
    val kind: LibraryDropItemKind,
    val bookId: String?,
    val bounds: Rect,
)

private data class LibraryBookTargetBounds(val index: Int, val bounds: Rect)

private data class LibraryShelfTargetBounds(
    val bounds: Rect,
    val allowsBookRoot: Boolean,
    val onHover: (() -> Unit)?,
)

internal class LibraryDragCoordinator {
    var activePayload by mutableStateOf<String?>(null)
        private set
    var activeSubjectKey by mutableStateOf<String?>(null)
        private set
    var activeBookIds by mutableStateOf(emptySet<String>())
        private set
    var pointerInWindow by mutableStateOf<Offset?>(null)
        private set
    var ghostPositionInWindow by mutableStateOf(Offset.Zero)
        private set
    var folderTargetId by mutableStateOf<String?>(null)
        private set
    var bookTargetId by mutableStateOf<String?>(null)
        private set
    var rootInsertionIndex by mutableIntStateOf(-1)
        private set
    var libraryInsertionIndex by mutableIntStateOf(-1)
        private set
    var isOverShelf by mutableStateOf(false)
        private set
    var isOverDelete by mutableStateOf(false)
        private set

    var onDrop: (String, LibraryDropDestination) -> Boolean = { _, _ -> false }
    var onLongPress: (String) -> Unit = {}

    private var activeCanRemove = false
    private var activeLibraryReorderSource = false
    private val shelfTargets = mutableMapOf<Int, LibraryShelfTargetBounds>()
    private var nextShelfTargetId = 0
    private var activeShelfTargetId: Int? = null
    private var libraryBounds: Rect? = null
    private var libraryReorderEnabled = false
    private var hostBounds: Rect? = null
    private var deleteBounds: Rect? = null
    private val sourceBounds = mutableMapOf<String, Rect>()
    private val itemBounds = mutableMapOf<String, LibraryItemTargetBounds>()
    private val bookBounds = mutableMapOf<String, LibraryBookTargetBounds>()
    private var dragTargetBounds: Map<String, LibraryItemTargetBounds> = emptyMap()
    private var dragBookBounds: Map<String, LibraryBookTargetBounds> = emptyMap()
    private var dragDistance = 0f

    fun registerSource(subjectKey: String, bounds: Rect) {
        sourceBounds[subjectKey] = bounds
    }

    fun allocateShelfTargetId(): Int = nextShelfTargetId++

    fun registerShelf(id: Int, bounds: Rect, allowsBookRoot: Boolean, onHover: (() -> Unit)?) {
        shelfTargets[id] = LibraryShelfTargetBounds(bounds, allowsBookRoot, onHover)
        updateTarget()
    }

    fun unregisterShelf(id: Int) {
        shelfTargets.remove(id)
        updateTarget()
    }

    fun registerLibrary(bounds: Rect, reorderEnabled: Boolean) {
        libraryBounds = bounds
        libraryReorderEnabled = reorderEnabled
        if (!reorderEnabled) libraryInsertionIndex = -1
        updateTarget()
    }

    fun registerHost(bounds: Rect) {
        hostBounds = bounds
    }

    fun registerDeleteTarget(bounds: Rect) {
        deleteBounds = bounds
        updateTarget()
    }

    fun hostTopLeft(): Offset = hostBounds?.topLeft ?: Offset.Zero

    fun registerItem(
        id: String,
        index: Int,
        kind: LibraryDropItemKind,
        bookId: String?,
        bounds: Rect,
    ) {
        itemBounds[id] = LibraryItemTargetBounds(index, kind, bookId, bounds)
        updateTarget()
    }

    fun clearItemRegistrations() {
        shelfTargets.clear()
        activeShelfTargetId = null
        itemBounds.clear()
        sourceBounds.clear()
        dragTargetBounds = emptyMap()
    }

    fun registerBook(id: String, index: Int, bounds: Rect) {
        bookBounds[id] = LibraryBookTargetBounds(index, bounds)
        updateTarget()
    }

    fun start(
        subjectKey: String,
        payload: String,
        localPosition: Offset,
        draggedBookIds: Set<String>,
        canRemove: Boolean,
        libraryReorderSource: Boolean,
    ) {
        val bounds = sourceBounds[subjectKey] ?: return
        dragTargetBounds = itemBounds.toMap()
        dragBookBounds = bookBounds.toMap()
        activeSubjectKey = subjectKey
        activePayload = payload
        activeBookIds = draggedBookIds
        activeCanRemove = canRemove
        activeLibraryReorderSource = libraryReorderSource
        dragDistance = 0f
        pointerInWindow = bounds.topLeft + localPosition
        ghostPositionInWindow = pointerInWindow ?: Offset.Zero
        updateTarget()
    }

    fun moveBy(delta: Offset) {
        dragDistance += delta.getDistance()
        pointerInWindow = pointerInWindow?.plus(delta)
        pointerInWindow?.let { ghostPositionInWindow = it }
        updateTarget()
    }

    fun finish(minimumDragDistance: Float = 0f) {
        val payload = activePayload
        val destination = when {
            isOverDelete -> LibraryDropDestination.Remove
            folderTargetId != null -> LibraryDropDestination.Collection(requireNotNull(folderTargetId))
            bookTargetId != null -> LibraryDropDestination.Book(requireNotNull(bookTargetId))
            rootInsertionIndex >= 0 -> LibraryDropDestination.Root(rootInsertionIndex)
            libraryInsertionIndex >= 0 -> LibraryDropDestination.Library(libraryInsertionIndex)
            else -> null
        }
        if (payload != null && destination != null && dragDistance >= minimumDragDistance) onDrop(payload, destination)
        cancel()
    }

    fun cancel() {
        activePayload = null
        activeSubjectKey = null
        activeBookIds = emptySet()
        activeCanRemove = false
        activeLibraryReorderSource = false
        pointerInWindow = null
        folderTargetId = null
        bookTargetId = null
        rootInsertionIndex = -1
        libraryInsertionIndex = -1
        dragTargetBounds = emptyMap()
        dragBookBounds = emptyMap()
        isOverShelf = false
        activeShelfTargetId = null
        isOverDelete = false
        dragDistance = 0f
    }

    private fun updateTarget() {
        val pointer = pointerInWindow
        isOverDelete = pointer != null && activeCanRemove && deleteBounds?.contains(pointer) == true
        if (isOverDelete) {
            isOverShelf = false
            activeShelfTargetId = null
            folderTargetId = null
            bookTargetId = null
            rootInsertionIndex = -1
            libraryInsertionIndex = -1
            return
        }

        val shelfTarget = pointer?.let { point ->
            shelfTargets.entries.firstOrNull { it.value.bounds.contains(point) }
        }
        val shelfTargetId = shelfTarget?.key
        if (shelfTargetId == null) {
            activeShelfTargetId = null
        } else if (activePayload != null && activeShelfTargetId != shelfTargetId) {
            activeShelfTargetId = shelfTargetId
            shelfTarget.value.onHover?.invoke()
        }
        val activeShelfTarget = shelfTarget?.value
        isOverShelf = activeShelfTarget != null
        if (activeShelfTarget != null) {
            val shelfPointer = requireNotNull(pointer)
            libraryInsertionIndex = -1
            val targets = if (dragTargetBounds.isNotEmpty()) dragTargetBounds else itemBounds
            val hit = targets.entries.firstOrNull { (_, target) -> target.bounds.contains(shelfPointer) }
            if (activeBookIds.isNotEmpty() && hit?.value?.kind == LibraryDropItemKind.COLLECTION &&
                hit.value.bounds.collectionDropBounds().contains(shelfPointer)
            ) {
                folderTargetId = hit.key
                bookTargetId = null
                rootInsertionIndex = -1
                return
            }
            if (activeBookIds.isNotEmpty() && hit?.value?.kind == LibraryDropItemKind.BOOK &&
                hit.value.bookId !in activeBookIds && hit.value.bounds.collectionDropBounds().contains(shelfPointer)
            ) {
                folderTargetId = null
                bookTargetId = hit.key
                rootInsertionIndex = -1
                return
            }
            folderTargetId = null
            bookTargetId = null
            val target = hit?.value ?: targets.values.minByOrNull { candidate ->
                val delta = candidate.bounds.center - shelfPointer
                delta.x * delta.x + delta.y * delta.y
            }
            rootInsertionIndex = if (activeBookIds.isNotEmpty() && !activeShelfTarget.allowsBookRoot) {
                -1
            } else {
                target?.let { if (shelfPointer.x < it.bounds.center.x) it.index else it.index + 1 } ?: 0
            }
            return
        }

        folderTargetId = null
        bookTargetId = null
        rootInsertionIndex = -1
        val overLibrary = pointer != null && libraryReorderEnabled && activeLibraryReorderSource &&
            libraryBounds?.contains(pointer) == true && activeBookIds.isNotEmpty()
        if (!overLibrary) {
            libraryInsertionIndex = -1
            return
        }
        val targets = (if (dragBookBounds.isNotEmpty()) dragBookBounds else bookBounds)
            .filterKeys { it !in activeBookIds }
        val hit = targets.values.firstOrNull { it.bounds.contains(pointer) }
        val target = hit ?: targets.values.minByOrNull { candidate ->
            val delta = candidate.bounds.center - pointer
            delta.x * delta.x + delta.y * delta.y
        }
        libraryInsertionIndex = target?.let {
            if (pointer.y < it.bounds.center.y || pointer.x < it.bounds.center.x) it.index else it.index + 1
        } ?: 0
    }
}

private fun Rect.collectionDropBounds(): Rect = Rect(
    left = left + width * 0.18f,
    top = top + height * 0.10f,
    right = right - width * 0.18f,
    bottom = bottom - height * 0.10f,
)

private fun Offset.isDominantScrollMovement(orientation: Orientation, touchSlop: Float): Boolean {
    val primary = kotlin.math.abs(if (orientation == Orientation.Horizontal) x else y)
    val cross = kotlin.math.abs(if (orientation == Orientation.Horizontal) y else x)
    return primary > touchSlop && primary >= cross
}

@Composable
internal fun Modifier.libraryDragSource(
    payload: String,
    subjectKey: String,
    enabled: Boolean,
    coordinator: LibraryDragCoordinator,
    draggedBookIds: Set<String> = emptySet(),
    canRemove: Boolean = true,
    libraryReorderSource: Boolean = false,
    startDragOnLongPress: Boolean = false,
    scrollOrientation: Orientation,
    bookId: String? = null,
    onTap: () -> Unit,
): Modifier {
    if (!enabled) return this
    val interactionSource = remember(subjectKey, coordinator) { MutableInteractionSource() }
    val indication = LocalIndication.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentStartDragOnLongPress by rememberUpdatedState(startDragOnLongPress)
    return onGloballyPositioned { coordinator.registerSource(subjectKey, it.boundsInWindow()) }
        .semantics { onClick { onTap(); true } }
        .graphicsLayer {
            alpha = if (coordinator.activeSubjectKey == subjectKey || bookId in coordinator.activeBookIds) 0.34f else 1f
        }
        .indication(interactionSource, indication)
        .pointerInput(payload, subjectKey, coordinator, interactionSource, scrollOrientation) {
            coroutineScope gestureScope@{
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dragOnLongPress = currentStartDragOnLongPress
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    var current = down
                    var preHoldDistance = 0f
                    var started = false
                    var longPressActivated = false
                    var scrollGestureWon = false
                    var interactionFinished = false

                    fun startDrag() {
                        coordinator.start(
                            subjectKey = subjectKey,
                            payload = payload,
                            localPosition = down.position,
                            draggedBookIds = draggedBookIds,
                            canRemove = canRemove,
                            libraryReorderSource = libraryReorderSource,
                        )
                        val displacement = current.position - down.position
                        if (displacement != Offset.Zero) coordinator.moveBy(displacement)
                        started = true
                    }

                    val longPressJob = this@gestureScope.launch {
                        delay(viewConfiguration.longPressTimeoutMillis)
                        if (scrollGestureWon) return@launch
                        longPressActivated = true
                        if (!interactionFinished) {
                            interactionSource.tryEmit(PressInteraction.Release(press))
                            interactionFinished = true
                        }
                        if (dragOnLongPress || preHoldDistance > viewConfiguration.touchSlop) {
                            startDrag()
                        } else {
                            coordinator.onLongPress(subjectKey)
                        }
                    }
                    try {
                        while (current.pressed && !scrollGestureWon) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                .firstOrNull { it.id == down.id }
                                ?: break
                            current = change
                            val displacement = change.position - down.position
                            preHoldDistance = displacement.getDistance()
                            when {
                                started -> {
                                    val delta = change.positionChange()
                                    change.consume()
                                    if (delta != Offset.Zero) coordinator.moveBy(delta)
                                }

                                longPressActivated && preHoldDistance > viewConfiguration.touchSlop -> {
                                    startDrag()
                                    change.consume()
                                }

                                !longPressActivated && displacement.isDominantScrollMovement(
                                    scrollOrientation,
                                    viewConfiguration.touchSlop,
                                ) -> {
                                    scrollGestureWon = true
                                    longPressJob.cancel()
                                    if (!interactionFinished) {
                                        interactionSource.tryEmit(PressInteraction.Cancel(press))
                                        interactionFinished = true
                                    }
                                }
                            }
                        }
                        if (!scrollGestureWon) {
                            when {
                                started -> coordinator.finish(viewConfiguration.touchSlop)
                                !longPressActivated -> {
                                    longPressJob.cancel()
                                    if (!interactionFinished) {
                                        if (preHoldDistance <= viewConfiguration.touchSlop) {
                                            interactionSource.tryEmit(PressInteraction.Release(press))
                                        } else {
                                            interactionSource.tryEmit(PressInteraction.Cancel(press))
                                        }
                                        interactionFinished = true
                                    }
                                    if (preHoldDistance <= viewConfiguration.touchSlop) currentOnTap()
                                }
                            }
                        }
                    } finally {
                        longPressJob.cancel()
                        if (!interactionFinished) interactionSource.tryEmit(PressInteraction.Cancel(press))
                        if (started && coordinator.activeSubjectKey == subjectKey) coordinator.cancel()
                    }
                }
            }
        }

}

internal fun Modifier.libraryShelfDropTarget(
    coordinator: LibraryDragCoordinator,
    allowsBookRoot: Boolean = true,
    onHover: (() -> Unit)? = null,
): Modifier = composed {
    val targetId = remember(coordinator) { coordinator.allocateShelfTargetId() }
    val currentOnHover by rememberUpdatedState(onHover)
    DisposableEffect(coordinator, targetId) {
        onDispose { coordinator.unregisterShelf(targetId) }
    }
    onGloballyPositioned {
        coordinator.registerShelf(
            id = targetId,
            bounds = it.boundsInWindow(),
            allowsBookRoot = allowsBookRoot,
            onHover = currentOnHover?.let { callback -> { callback() } },
        )
    }
}

internal fun Modifier.libraryContentDropTarget(
    coordinator: LibraryDragCoordinator,
    reorderEnabled: Boolean,
): Modifier = onGloballyPositioned { coordinator.registerLibrary(it.boundsInWindow(), reorderEnabled) }

internal fun Modifier.libraryDragOverlayHost(coordinator: LibraryDragCoordinator): Modifier =
    onGloballyPositioned { coordinator.registerHost(it.boundsInWindow()) }

internal fun Modifier.libraryItemDropTarget(
    id: String,
    index: Int,
    kind: LibraryDropItemKind,
    bookId: String?,
    coordinator: LibraryDragCoordinator,
): Modifier = onGloballyPositioned { coordinator.registerItem(id, index, kind, bookId, it.boundsInWindow()) }

internal fun Modifier.libraryBookDropTarget(
    bookId: String,
    index: Int,
    coordinator: LibraryDragCoordinator,
): Modifier = onGloballyPositioned { coordinator.registerBook(bookId, index, it.boundsInWindow()) }

internal fun Modifier.libraryDeleteDropTarget(coordinator: LibraryDragCoordinator): Modifier =
    onGloballyPositioned { coordinator.registerDeleteTarget(it.boundsInWindow()) }
