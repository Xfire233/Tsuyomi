/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.tsuyomi.shared.model.BookIdentity
import kotlin.math.abs

enum class LibrarySelectionKind {
    BOOK,
    COLLECTION,
}

enum class LibrarySelectionDialog {
    CREATE_COLLECTION,
    ADD_TO_COLLECTION,
    CONFIRM_REMOVE,
}

sealed interface LibraryDragPayload {
    data class Books(
        val identities: Set<BookIdentity>,
        val fromShortcut: Boolean = false,
    ) : LibraryDragPayload

    data class Shortcut(val id: String) : LibraryDragPayload
}

sealed interface LibraryDropDestination {
    data class Root(val index: Int) : LibraryDropDestination
    data class Collection(val id: String) : LibraryDropDestination
    data class Book(
        val identity: BookIdentity,
        val shortcutId: String? = null,
    ) : LibraryDropDestination
    data class Library(val index: Int) : LibraryDropDestination
    data object Remove : LibraryDropDestination
}

internal enum class LibraryShortcutDropKind {
    ITEM,
    COLLECTION,
    BOOK,
}

private data class LibraryShortcutTarget(
    val id: String,
    val index: Int,
    val kind: LibraryShortcutDropKind,
    val bookIdentity: BookIdentity?,
    val bounds: Rect,
)

private data class LibraryCollectionTarget(
    val id: String,
    val bounds: Rect,
)

private data class LibraryBookTarget(
    val identity: BookIdentity,
    val index: Int,
    val bounds: Rect,
)

private data class LibraryShelfTarget(
    val bounds: Rect,
    val allowsBookRoot: Boolean,
    val onHover: (() -> Unit)?,
)

@Stable
internal class LibraryDragCoordinator {
    var activePayload by mutableStateOf<LibraryDragPayload?>(null)
        private set
    var activeSubjectKey by mutableStateOf<String?>(null)
        private set
    var activeBookIds by mutableStateOf(emptySet<BookIdentity>())
        private set
    var pointerInWindow by mutableStateOf<Offset?>(null)
        private set
    var ghostPositionInWindow by mutableStateOf(Offset.Zero)
        private set
    var collectionTargetId by mutableStateOf<String?>(null)
        private set
    var bookTargetIdentity by mutableStateOf<BookIdentity?>(null)
        private set
    var bookTargetShortcutId by mutableStateOf<String?>(null)
        private set
    var rootInsertionIndex by mutableIntStateOf(-1)
        private set
    var libraryInsertionIndex by mutableIntStateOf(-1)
        private set
    var isOverShelf by mutableStateOf(false)
        private set
    var isOverDelete by mutableStateOf(false)
        private set

    var onLongPress: (BookIdentity) -> Unit = {}
    var onDrop: (LibraryDragPayload, LibraryDropDestination) -> Unit = { _, _ -> }

    private var activeCanRemove = false
    private var activeLibraryReorderSource = false
    private val shelfTargets = mutableMapOf<Int, LibraryShelfTarget>()
    private var nextShelfTargetId = 0
    private var activeShelfTargetId: Int? = null
    private var libraryBounds: Rect? = null
    private var libraryReorderEnabled = false
    private var hostBounds: Rect? = null
    private var deleteBounds: Rect? = null
    private val sourceBounds = mutableMapOf<String, Rect>()
    private val shortcutBounds = mutableMapOf<String, LibraryShortcutTarget>()
    private val collectionBounds = mutableMapOf<String, LibraryCollectionTarget>()
    private val bookBounds = mutableMapOf<String, LibraryBookTarget>()
    private var dragShortcutBounds: Map<String, LibraryShortcutTarget> = emptyMap()
    private var dragCollectionBounds: Map<String, LibraryCollectionTarget> = emptyMap()
    private var dragBookBounds: Map<String, LibraryBookTarget> = emptyMap()
    private var dragDistance = 0f

    fun registerSource(subjectKey: String, bounds: Rect) {
        sourceBounds[subjectKey] = bounds
    }

    fun allocateShelfTargetId(): Int = nextShelfTargetId++

    fun registerShelf(id: Int, bounds: Rect, allowsBookRoot: Boolean, onHover: (() -> Unit)?) {
        shelfTargets[id] = LibraryShelfTarget(bounds, allowsBookRoot, onHover)
        updateTarget()
    }

    fun unregisterShelf(id: Int) {
        shelfTargets.remove(id)
        updateTarget()
    }


    fun registerHost(bounds: Rect) {
        hostBounds = bounds
    }

    fun hostTopLeft(): Offset = hostBounds?.topLeft ?: Offset.Zero

    fun registerShortcut(
        id: String,
        index: Int,
        kind: LibraryShortcutDropKind,
        bookIdentity: BookIdentity?,
        bounds: Rect,
    ) {
        val target = LibraryShortcutTarget(id, index, kind, bookIdentity, bounds)
        shortcutBounds[id] = target
        if (activePayload != null) dragShortcutBounds = dragShortcutBounds + (id to target)
        updateTarget()
    }

    fun registerCollection(id: String, bounds: Rect) {
        collectionBounds[id] = LibraryCollectionTarget(id, bounds)
        updateTarget()
    }

    fun registerBook(identity: BookIdentity, index: Int, bounds: Rect) {
        bookBounds[identity.stableKey()] = LibraryBookTarget(identity, index, bounds)
        updateTarget()
    }

    fun registerLibrary(bounds: Rect, reorderEnabled: Boolean) {
        libraryBounds = bounds
        libraryReorderEnabled = reorderEnabled
        if (!reorderEnabled) libraryInsertionIndex = -1
        updateTarget()
    }

    fun registerDeleteTarget(bounds: Rect) {
        deleteBounds = bounds
        updateTarget()
    }

    fun start(
        subjectKey: String,
        localPosition: Offset,
        payload: LibraryDragPayload,
        canRemove: Boolean,
        libraryReorderSource: Boolean,
    ) {
        val bounds = sourceBounds[subjectKey] ?: return
        dragShortcutBounds = shortcutBounds.toMap()
        dragCollectionBounds = collectionBounds.toMap()
        dragBookBounds = bookBounds.toMap()
        activeSubjectKey = subjectKey
        activePayload = payload
        activeBookIds = (payload as? LibraryDragPayload.Books)?.identities.orEmpty()
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

    fun finish(minimumDragDistance: Float) {
        val payload = activePayload
        val destination = when {
            isOverDelete -> LibraryDropDestination.Remove
            collectionTargetId != null -> LibraryDropDestination.Collection(requireNotNull(collectionTargetId))
            bookTargetIdentity != null -> LibraryDropDestination.Book(
                identity = requireNotNull(bookTargetIdentity),
                shortcutId = bookTargetShortcutId,
            )
            rootInsertionIndex >= 0 -> LibraryDropDestination.Root(rootInsertionIndex)
            libraryInsertionIndex >= 0 -> LibraryDropDestination.Library(libraryInsertionIndex)
            else -> null
        }
        if (payload != null && destination != null && dragDistance >= minimumDragDistance) {
            onDrop(payload, destination)
        }
        cancel()
    }

    fun cancel() {
        activePayload = null
        activeSubjectKey = null
        activeBookIds = emptySet()
        activeCanRemove = false
        activeLibraryReorderSource = false
        pointerInWindow = null
        collectionTargetId = null
        bookTargetIdentity = null
        bookTargetShortcutId = null
        rootInsertionIndex = -1
        libraryInsertionIndex = -1
        isOverShelf = false
        activeShelfTargetId = null
        isOverDelete = false
        dragShortcutBounds = emptyMap()
        dragCollectionBounds = emptyMap()
        dragBookBounds = emptyMap()
        dragDistance = 0f
    }

    private fun updateTarget() {
        val pointer = pointerInWindow
        isOverDelete = pointer != null && activeCanRemove && deleteBounds?.contains(pointer) == true
        if (isOverDelete) {
            clearDestinationExceptDelete()
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
            val targets = if (dragShortcutBounds.isNotEmpty()) dragShortcutBounds else shortcutBounds
            val hit = targets.entries.firstOrNull { (_, target) -> target.bounds.contains(shelfPointer) }
            if (activeBookIds.isNotEmpty() && hit?.value?.kind == LibraryShortcutDropKind.COLLECTION &&
                hit.value.bounds.collectionDropBounds().contains(shelfPointer)
            ) {
                collectionTargetId = hit.key.removePrefix("collection:")
                bookTargetIdentity = null
                bookTargetShortcutId = null
                rootInsertionIndex = -1
                return
            }
            if (activeBookIds.isNotEmpty() && hit?.value?.kind == LibraryShortcutDropKind.BOOK &&
                hit.value.bookIdentity !in activeBookIds && hit.value.bounds.collectionDropBounds().contains(shelfPointer)
            ) {
                collectionTargetId = null
                bookTargetIdentity = hit.value.bookIdentity
                bookTargetShortcutId = hit.key
                rootInsertionIndex = -1
                return
            }
            collectionTargetId = null
            bookTargetIdentity = null
            bookTargetShortcutId = null
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

        isOverShelf = false
        rootInsertionIndex = -1
        val collection = pointer?.let { point ->
            dragCollectionBounds.values.firstOrNull { it.bounds.collectionDropBounds().contains(point) }
        }
        if (collection != null && activeBookIds.isNotEmpty()) {
            collectionTargetId = collection.id
            bookTargetIdentity = null
            bookTargetShortcutId = null
            libraryInsertionIndex = -1
            return
        }
        collectionTargetId = null

        val targets = dragBookBounds.values.filter { it.identity !in activeBookIds }
        val book = pointer?.let { point ->
            targets.firstOrNull { it.bounds.collectionDropBounds().contains(point) }
        }
        if (book != null && activeBookIds.isNotEmpty()) {
            bookTargetIdentity = book.identity
            bookTargetShortcutId = null
            libraryInsertionIndex = -1
            return
        }
        bookTargetIdentity = null
        bookTargetShortcutId = null

        val overLibrary = pointer != null && activeLibraryReorderSource && libraryReorderEnabled &&
            libraryBounds?.contains(pointer) == true && activeBookIds.isNotEmpty()
        if (!overLibrary) {
            libraryInsertionIndex = -1
            return
        }
        val target = targets.firstOrNull { it.bounds.contains(requireNotNull(pointer)) }
            ?: targets.minByOrNull { candidate ->
                val delta = candidate.bounds.center - requireNotNull(pointer)
                delta.x * delta.x + delta.y * delta.y
            }
        libraryInsertionIndex = target?.let {
            if (requireNotNull(pointer).y < it.bounds.center.y || requireNotNull(pointer).x < it.bounds.center.x) {
                it.index
            } else {
                it.index + 1
            }
        } ?: 0
    }

    private fun clearDestinationExceptDelete() {
        isOverShelf = false
        activeShelfTargetId = null
        collectionTargetId = null
        bookTargetIdentity = null
        bookTargetShortcutId = null
        rootInsertionIndex = -1
        libraryInsertionIndex = -1
    }
}

@Composable
internal fun Modifier.libraryBookGestures(
    identity: BookIdentity,
    coordinator: LibraryDragCoordinator,
    selected: Boolean,
    selectionActive: Boolean,
    selectedBookIds: Set<BookIdentity>,
    dragEnabled: Boolean,
    canRemove: Boolean,
    reorderSource: Boolean,
    scrollOrientation: Orientation,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
): Modifier {
    val subjectKey = identity.stableKey()
    val currentSelectionActive by rememberUpdatedState(selectionActive)
    val currentSelected by rememberUpdatedState(selected)
    val currentSelectedBookIds by rememberUpdatedState(selectedBookIds)
    return semantics {
        this.selected = selected
        onClick {
            onTap()
            true
        }
        onLongClick(label = "选择此书") {
            onLongPress()
            true
        }
    }
        .graphicsLayer {
            alpha = if (coordinator.activeSubjectKey == subjectKey || identity in coordinator.activeBookIds) 0.34f else 1f
        }
        .libraryPointerDragGestures(
            subjectKey = subjectKey,
            coordinator = coordinator,
            dragEnabled = dragEnabled,
            canRemove = canRemove,
            reorderSource = reorderSource,
            startDragOnLongPress = { !currentSelectionActive || currentSelected },
            payload = {
                LibraryDragPayload.Books(
                    identities = if (currentSelectionActive && currentSelected) currentSelectedBookIds else setOf(identity),
                )
            },
            scrollOrientation = scrollOrientation,
            onTap = onTap,
            onLongPress = onLongPress,
        )
}

@Composable
internal fun Modifier.libraryShortcutGestures(
    subjectKey: String,
    coordinator: LibraryDragCoordinator,
    payload: () -> LibraryDragPayload,
    selected: Boolean,
    selectionActive: Boolean,
    dragEnabled: Boolean,
    canRemove: Boolean,
    scrollOrientation: Orientation,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
): Modifier {
    val currentSelected by rememberUpdatedState(selected)
    val currentSelectionActive by rememberUpdatedState(selectionActive)
    return graphicsLayer {
        alpha = if (coordinator.activeSubjectKey == subjectKey) 0.34f else 1f
    }.libraryPointerDragGestures(
        subjectKey = subjectKey,
        coordinator = coordinator,
        dragEnabled = dragEnabled,
        canRemove = canRemove,
        reorderSource = false,
        startDragOnLongPress = { !currentSelectionActive || currentSelected },
        payload = payload,
        scrollOrientation = scrollOrientation,
        onTap = onTap,
        onLongPress = onLongPress,
    )
}

@Composable
private fun Modifier.libraryPointerDragGestures(
    subjectKey: String,
    coordinator: LibraryDragCoordinator,
    dragEnabled: Boolean,
    canRemove: Boolean,
    reorderSource: Boolean,
    startDragOnLongPress: () -> Boolean,
    payload: () -> LibraryDragPayload,
    scrollOrientation: Orientation,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
): Modifier {
    val interactionSource = remember(subjectKey, coordinator) { MutableInteractionSource() }
    val indication: Indication = LocalIndication.current
    val haptic = LocalHapticFeedback.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentStartDragOnLongPress by rememberUpdatedState(startDragOnLongPress)
    val currentPayload by rememberUpdatedState(payload)
    return onGloballyPositioned { coordinator.registerSource(subjectKey, it.boundsInWindow()) }
        .pointerInput(subjectKey, coordinator, dragEnabled, interactionSource, scrollOrientation) {
            coroutineScope {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    var current = down
                    var preHoldDistance = 0f
                    var started = false
                    var longPressActivated = false
                    var scrollGestureWon = false
                    var interactionFinished = false

                    fun startDrag() {
                        if (!dragEnabled || started) return
                        coordinator.start(
                            subjectKey = subjectKey,
                            localPosition = down.position,
                            payload = currentPayload(),
                            canRemove = canRemove,
                            libraryReorderSource = reorderSource,
                        )
                        val displacement = current.position - down.position
                        if (displacement != Offset.Zero) coordinator.moveBy(displacement)
                        started = coordinator.activeSubjectKey == subjectKey
                    }

                    val longPressJob = launch {
                        delay(viewConfiguration.longPressTimeoutMillis)
                        if (scrollGestureWon) return@launch
                        longPressActivated = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!interactionFinished) {
                            interactionSource.tryEmit(PressInteraction.Release(press))
                            interactionFinished = true
                        }
                        currentOnLongPress()
                        if (currentStartDragOnLongPress() || preHoldDistance > viewConfiguration.touchSlop) {
                            startDrag()
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
                                    if (started) change.consume()
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
        .indication(interactionSource, indication)
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

internal fun Modifier.libraryDragOverlayHost(coordinator: LibraryDragCoordinator): Modifier =
    onGloballyPositioned { coordinator.registerHost(it.boundsInWindow()) }

internal fun Modifier.libraryShortcutDropTarget(
    coordinator: LibraryDragCoordinator,
    id: String,
    index: Int,
    kind: LibraryShortcutDropKind,
    bookIdentity: BookIdentity?,
): Modifier = onGloballyPositioned {
    coordinator.registerShortcut(id, index, kind, bookIdentity, it.boundsInWindow())
}

internal fun Modifier.libraryBookDropTarget(
    coordinator: LibraryDragCoordinator,
    identity: BookIdentity,
    index: Int,
): Modifier = onGloballyPositioned { coordinator.registerBook(identity, index, it.boundsInWindow()) }

internal fun Modifier.libraryCollectionDropTarget(
    coordinator: LibraryDragCoordinator,
    collectionId: String,
): Modifier = onGloballyPositioned { coordinator.registerCollection(collectionId, it.boundsInWindow()) }

internal fun Modifier.libraryContentDropTarget(
    coordinator: LibraryDragCoordinator,
    reorderEnabled: Boolean,
): Modifier = onGloballyPositioned { coordinator.registerLibrary(it.boundsInWindow(), reorderEnabled) }

internal fun Modifier.libraryDeleteDropTarget(coordinator: LibraryDragCoordinator): Modifier =
    onGloballyPositioned { coordinator.registerDeleteTarget(it.boundsInWindow()) }

private fun BookIdentity.stableKey(): String = "$sourceId\u0000$remoteBookId"

private fun Rect.collectionDropBounds(): Rect = Rect(
    left = left + width * 0.18f,
    top = top + height * 0.10f,
    right = right - width * 0.18f,
    bottom = bottom - height * 0.10f,
)

private fun Offset.isDominantScrollMovement(orientation: Orientation, touchSlop: Float): Boolean {
    val primary = abs(if (orientation == Orientation.Horizontal) x else y)
    val cross = abs(if (orientation == Orientation.Horizontal) y else x)
    return primary > touchSlop && primary >= cross
}
