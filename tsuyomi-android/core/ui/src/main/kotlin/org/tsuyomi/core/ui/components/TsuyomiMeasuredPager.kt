/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

/** Actual measured item lane after the owning pager reserves its persistent controls. */
val LocalTsuyomiPagerMaxContentHeight = compositionLocalOf<Dp?> { null }
/** True only for placed pager content; measurement/lookahead must not acquire visible media work. */
val LocalTsuyomiPagerItemVisible = compositionLocalOf { true }
internal val LocalTsuyomiPagerItemNavigation = compositionLocalOf<TsuyomiPagerItemNavigation?> { null }

internal class TsuyomiPagerItemNavigation {
    val children = mutableStateListOf<TsuyomiMeasuredPagerState>()
    var maxContentHeightPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            if (plannedChildren.isNotEmpty()) plannedChildren.values.forEach { it.measureGeneration++ }
        }
    // Keep measured state, not composition, so an offscreen local endpoint remains knowable.
    val plannedChildren = mutableStateMapOf<Int, TsuyomiMeasuredPagerState>()
}

/** Nested measured content shares its containing page's already-reserved controls. */
@Composable
internal fun registerTsuyomiNestedPager(state: TsuyomiMeasuredPagerState): Boolean {
    val navigation = LocalTsuyomiPagerItemNavigation.current
    if (navigation != null) navigation.plannedChildren[currentCompositeKeyHash] = state
    DisposableEffect(navigation, state) {
        navigation?.children?.add(state)
        onDispose { navigation?.children?.remove(state) }
    }
    return navigation != null
}

/** Read a nested page's live measurement budget, including footer changes through subcomposition. */
@Composable
internal fun Modifier.boundTsuyomiPagerViewport(state: TsuyomiMeasuredPagerState): Modifier {
    val navigation = LocalTsuyomiPagerItemNavigation.current
    val inheritedMaxHeight = LocalTsuyomiPagerMaxContentHeight.current
    return if (navigation == null) {
        if (inheritedMaxHeight == null) this else Modifier.heightIn(max = inheritedMaxHeight).then(this)
    } else {
        Modifier.layout { measurable, constraints ->
            @Suppress("UNUSED_VARIABLE") val generation = state.measureGeneration
            val maxHeight = minOf(constraints.maxHeight, navigation.maxContentHeightPx)
            val placeable = measurable.measure(constraints.copy(
                minHeight = minOf(constraints.minHeight, maxHeight),
                maxHeight = maxHeight,
            ))
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }.then(this)
    }
}

internal data class MeasuredPageAnchor(val key: String, val index: Int)

/** Saveable, identity-anchored page history for [TsuyomiMeasuredPager]. */
@Stable
class TsuyomiMeasuredPagerState internal constructor(initialAnchorKey: String?) {
    private val anchors = mutableStateListOf<MeasuredPageAnchor>().apply {
        if (initialAnchorKey != null) add(MeasuredPageAnchor(initialAnchorKey, 0))
    }

    internal val currentAnchor: MeasuredPageAnchor? get() = anchors.lastOrNull()
    val currentAnchorKey: String? get() = anchors.lastOrNull()?.key
    private var navigationPage by mutableIntStateOf(1)
    private val children = mutableStateListOf<TsuyomiMeasuredPagerState>()
    private val pageEnds = mutableStateMapOf<String, MeasuredPageAnchor>()
    private var previousAnchor by mutableStateOf<MeasuredPageAnchor?>(null)
    internal val forwardBoundary: MeasuredPageAnchor? get() = currentAnchorKey?.let(pageEnds::get)
    private var plannedPages by mutableStateOf<List<MeasuredPageAnchor>>(emptyList())
    private var plannedPageCount by mutableStateOf<Int?>(null)
    /** Real finite local page count, available once bounded measurement finishes. */
    val pageCount: Int? get() = plannedPageCount
    internal var measuredPages: List<MeasuredPageAnchor>? = null
    internal var measuredPageCount: Int? = null
    internal var measuredFirstPageHeight: Int? = null
    internal var measuredLastPageHeight: Int? = null
    internal var measuredVisibleHeight: Int = 0
    internal var measureGeneration by mutableIntStateOf(0)
    internal var navigationRevision by mutableIntStateOf(0)
    internal var isTextPager: Boolean = false
    internal var textBoundaryGeneration by mutableIntStateOf(0)
    internal fun textBoundary(offset: Int): Int? = pageEnds["text:$offset"]?.index

    private fun retainBoundary(from: MeasuredPageAnchor, end: MeasuredPageAnchor) {
        pageEnds[from.key] = end
        if (isTextPager) {
            val pages = measuredPages
            val index = pages?.binarySearchBy(from.index) { it.index } ?: -1
            if (index < 0 || pages?.getOrNull(index + 1) != end) textBoundaryGeneration++
        }
    }
    private var childEndpoint: Boolean? = null
    private var measuredWidth: Int? = null
    private var measuredHeight: Int? = null

    internal fun commitIndexedViewport(width: Int, height: Int) {
        if (measuredWidth != null && (measuredWidth != width || measuredHeight != height)) {
            // Item-sized historical caps belong to their old geometry, not the semantic identity.
            pageEnds.clear()
            val current = anchors.lastOrNull()
            anchors.clear()
            if (current != null) anchors.add(current)
        }
        measuredWidth = width
        measuredHeight = height
    }

    internal fun matchesIndexedViewport(width: Int, height: Int): Boolean =
        measuredWidth == width && measuredHeight == height

    /** Jumps directly to the first measured page; nested content uses the same plan. */
    fun goFirst() {
        val first = (measuredPages ?: plannedPages).firstOrNull() ?: return
        navigationRevision++
        anchors.clear()
        anchors.add(first)
        if (!isTextPager) pageEnds.clear()
        nextAnchor = null
        previousAnchor = null
        navigationPage = 1
        childEndpoint = if (isTextPager) null else false
        children.forEach { it.goFirst() }
        children.clear()
    }

    /** Jumps directly to the actual finite last page, without fetching remote content. */
    fun goLast() {
        val pages = measuredPages ?: plannedPages
        val count = measuredPageCount ?: plannedPageCount ?: return
        if (pages.isEmpty()) return
        navigationRevision++
        anchors.clear()
        anchors.addAll(pages)
        if (!isTextPager) pageEnds.clear()
        nextAnchor = null
        previousAnchor = null
        navigationPage = count
        childEndpoint = if (isTextPager) null else true
        children.clear()
    }

    internal fun commitMeasuredPlan(pages: List<MeasuredPageAnchor>?, count: Int?, ordinal: Int? = null) {
        if (pages != null && plannedPages != pages) plannedPages = pages
        if (pages != null && plannedPageCount != count) plannedPageCount = count
        if (ordinal != null && childEndpoint == null) navigationPage = ordinal.coerceAtLeast(1)
    }
    val currentPage: Int get() = navigationPage
    val canGoPrevious: Boolean get() = anchors.size > 1 || previousAnchor != null || children.any { it.canGoPrevious }

    private var nextAnchor by mutableStateOf<MeasuredPageAnchor?>(null)
    val canGoNext: Boolean get() = nextAnchor != null || children.any { it.canGoNext }

    /** Moves to a stable identity and begins a fresh previous/next history. */
    fun moveToAnchor(itemKey: String) {
        anchors.clear()
        anchors.add(MeasuredPageAnchor(itemKey, 0))
        nextAnchor = null
        navigationPage = 1
        children.clear()
        previousAnchor = null
        pageEnds.clear()
        navigationRevision++
    }

    /** Clears page history and optionally starts at a stable identity. */
    fun reset(initialAnchorKey: String? = null) {
        anchors.clear()
        if (initialAnchorKey != null) anchors.add(MeasuredPageAnchor(initialAnchorKey, 0))
        nextAnchor = null
        navigationPage = 1
        children.clear()
        previousAnchor = null
        pageEnds.clear()
        plannedPages = emptyList()
        plannedPageCount = null
        measuredPages = null
        measuredPageCount = null
        childEndpoint = null
        navigationRevision++
    }

    internal fun goPrevious() {
        val child = children.lastOrNull { it.canGoPrevious }
        when {
            child != null -> child.goPrevious()
            anchors.size > 1 -> {
                val forward = anchors.removeAt(anchors.lastIndex)
                retainBoundary(anchors.last(), forward)
                nextAnchor = null
                children.clear()
                childEndpoint = if (isTextPager) null else true
            }
            previousAnchor != null && currentAnchor != null -> {
                val previous = previousAnchor!!
                retainBoundary(previous, currentAnchor!!)
                anchors.clear()
                anchors.add(previous)
                nextAnchor = null
                children.clear()
                childEndpoint = if (isTextPager) null else true
            }
            else -> return
        }
        navigationPage = (navigationPage - 1).coerceAtLeast(1)
        navigationRevision++
    }

    internal fun goNext() {
        val child = children.firstOrNull { it.canGoNext }
        if (child != null) {
            child.goNext()
        } else {
            val target = nextAnchor ?: return
            if (anchors.lastOrNull() != target) anchors.add(target)
            nextAnchor = null
            childEndpoint = if (isTextPager) null else false
            children.clear()
        }
        navigationPage++
        navigationRevision++
    }

    internal fun commitChildren(nestedStates: List<TsuyomiMeasuredPagerState>) {
        if (children != nestedStates) {
            children.clear()
            children.addAll(nestedStates)
        }
        childEndpoint?.let { last ->
            if (children.all { it.measuredPageCount != null }) {
                children.forEach { if (last) it.goLast() else it.goFirst() }
                childEndpoint = null
            }
        }
    }

    internal fun reanchorCurrent(anchor: MeasuredPageAnchor) {
        if (anchors.isEmpty()) anchors.add(anchor) else anchors[anchors.lastIndex] = anchor
    }

    internal fun reconcileItemMap(keys: List<String>, start: MeasuredPageAnchor?) {
        if (start == null) {
            reset()
            return
        }
        fun resolve(anchor: MeasuredPageAnchor): Int =
            if (anchor.index in keys.indices && keys[anchor.index] == anchor.key) anchor.index else keys.indexOf(anchor.key)

        val retainedChildPage = if (currentAnchorKey == start.key) 1 + children.sumOf { it.currentPage - 1 } else 1
        var precedingIndex = start.index
        var removedHistory = false
        for (index in anchors.lastIndex - 1 downTo 0) {
            val previous = anchors[index]
            val resolved = resolve(previous)
            if (resolved < 0 || resolved >= precedingIndex) {
                anchors.removeAt(index)
                removedHistory = true
            } else {
                if (previous.index != resolved) anchors[index] = MeasuredPageAnchor(previous.key, resolved)
                precedingIndex = resolved
            }
        }
        reanchorCurrent(start)
        // Surviving endpoints do not prove the intervening ordered items are unchanged.
        pageEnds.clear()
        plannedPages = emptyList()
        plannedPageCount = null
        measuredPages = null
        measuredPageCount = null
        measuredFirstPageHeight = null
        measuredLastPageHeight = null
        if (removedHistory || start.index == 0) navigationPage = retainedChildPage
        nextAnchor = null
        previousAnchor = null
        children.clear()
    }

    internal fun commitMeasuredPage(start: MeasuredPageAnchor, next: MeasuredPageAnchor?, previous: MeasuredPageAnchor? = null) {
        if (anchors.isEmpty()) {
            anchors.add(start)
        } else if (anchors.last() != start) {
            anchors[anchors.lastIndex] = start
        }
        if (nextAnchor != next) nextAnchor = next
        if (previousAnchor != previous) previousAnchor = previous
    }

    internal fun saveableAnchors(): List<String> = anchors.flatMap { listOf(it.key, it.index.toString()) }

    companion object {
        internal val Saver: Saver<TsuyomiMeasuredPagerState, Any> = mapSaver(
            save = { state -> mapOf(
                "anchors" to state.saveableAnchors(),
                "boundaryVersion" to 2,
                "page" to state.navigationPage,
                "width" to state.measuredWidth,
                "height" to state.measuredHeight,
                "ends" to state.pageEnds.flatMap { (start, end) -> listOf(start, end.key, end.index.toString()) },
            ) },
            restore = { saved ->
                TsuyomiMeasuredPagerState(null).apply {
                    measuredWidth = saved["width"] as? Int
                    measuredHeight = saved["height"] as? Int
                    navigationPage = (saved["page"] as? Int ?: 1).coerceAtLeast(1)
                    val values = saved["anchors"] as? List<*> ?: emptyList<Any>()
                    values.chunked(2).forEach { pair ->
                        val key = pair.getOrNull(0) as? String ?: return@forEach
                        val index = (pair.getOrNull(1) as? String)?.toIntOrNull() ?: 0
                        anchors.add(MeasuredPageAnchor(key, index.coerceAtLeast(0)))
                    }
                    val currentBoundaryFormat = saved["boundaryVersion"] == 2
                    if (!currentBoundaryFormat && anchors.isNotEmpty()) {
                        val current = anchors.last()
                        anchors.clear()
                        anchors.add(current)
                        navigationPage = 1
                    }
                    val ends = if (currentBoundaryFormat) saved["ends"] as? List<*> ?: emptyList<Any>() else emptyList<Any>()
                    ends.chunked(3).forEach { entry ->
                        val start = entry.getOrNull(0) as? String ?: return@forEach
                        val end = entry.getOrNull(1) as? String ?: return@forEach
                        val index = (entry.getOrNull(2) as? String)?.toIntOrNull() ?: return@forEach
                        pageEnds[start] = MeasuredPageAnchor(end, index.coerceAtLeast(0))
                    }
                }
            },
        )
    }
}

/**
 * Remembers an identity-anchored measured page history. Item keys must be unique, stable strings
 * that the Compose saveable-state registry can retain. [initialAnchorKey] is used only when the
 * state is first created; call [TsuyomiMeasuredPagerState.moveToAnchor] for later jumps.
 */
@Composable
fun rememberTsuyomiMeasuredPagerState(
    initialAnchorKey: String? = null,
): TsuyomiMeasuredPagerState = rememberSaveable(saver = TsuyomiMeasuredPagerState.Saver) {
    TsuyomiMeasuredPagerState(initialAnchorKey)
}

private object PagerControlsSlot
private object PagerDecorationSlot

// Equality retires changed item maps; a stable composition hash keeps each item's saveable keys.
private class PagerItemMap(val keys: List<String>) {
    override fun equals(other: Any?): Boolean =
        this === other || other is PagerItemMap && keys == other.keys

    override fun hashCode(): Int = 0
}

/**
 * A non-scrollable indexed pager with a finite viewport and complete measured items/rows. `columns`
 * packs complete grid rows; `wrapItems` packs intrinsic-width controls from the start edge. Controls
 * reserve their measured height only when overflow requires controls. `fillViewport = false` uses
 * the fitted content's natural height. Finite page boundaries are learned in bounded batches and
 * reused; off-page measurement is never placed or exposed as visible content.
 *
 * Item keys must be unique, stable strings that the Compose saveable-state registry can retain.
 * Each indexed item is measured at its actual finite cell width to learn its natural complete
 * height, then checked against the finite remaining viewport. Expose oversized content as indexed
 * pieces at actual text-line or control seams; an item taller than the viewport is rejected rather
 * than cropped or placed over controls.
 *
 * The caller owns semantic itemization. Standard scrolling surfaces should keep their existing
 * Standard implementation and use this helper only for explicit button-paged content.
 */
@Composable
fun TsuyomiMeasuredPager(
    itemCount: Int,
    itemKey: (index: Int) -> String,
    itemContent: @Composable (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    state: TsuyomiMeasuredPagerState = rememberTsuyomiMeasuredPagerState(),
    columns: Int = 1,
    horizontalSpacing: Dp = 0.dp,
    verticalSpacing: Dp = 0.dp,
    fillViewport: Boolean = true,
    wrapItems: Boolean = false,
    alwaysShowPageControls: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    pageDecoration: (@Composable () -> Unit)? = null,
    pageControls: (@Composable (
        page: Int,
        canGoPrevious: Boolean,
        canGoNext: Boolean,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
        pageCount: Int?,
        onFirst: () -> Unit,
        onLast: () -> Unit,
    ) -> Unit)? = null,
) {
    require(itemCount >= 0) { "itemCount must not be negative" }
    val itemStateHolder = rememberSaveableStateHolder()
    require(columns > 0) { "columns must be positive" }
    // Observe provider changes coherently, but retain equal keys for measurement-lambda stability.
    val observedKeys = List(itemCount, itemKey)
    val itemMap = remember(observedKeys) { PagerItemMap(observedKeys) }
    val itemKeys = itemMap.keys
    var previousItemMap by remember { mutableStateOf(itemMap) }
    val delegated = registerTsuyomiNestedPager(state)
    val itemNavigations = remember(itemMap, itemContent) { HashMap<String, TsuyomiPagerItemNavigation>() }

    val anchor = state.currentAnchor
    val resolvedStartIndex = when {
        itemCount == 0 || anchor == null -> 0
        else -> {
            val fallback = anchor.index.coerceIn(0, itemCount - 1)
            if (itemKeys[fallback] == anchor.key) fallback
            else itemKeys.indexOf(anchor.key).takeIf { it >= 0 } ?: fallback
        }
    }
    val resolvedStartKey = if (itemCount == 0) null else itemKeys[resolvedStartIndex]
    val requestedAnchorStillPresent = anchor == null || resolvedStartKey == anchor.key
    val boundary = if (previousItemMap === itemMap) state.forwardBoundary else null
    val measureSnapshot = remember { PagerMeasureSnapshot() }
    val pagePlan = remember(itemMap, itemContent, columns, horizontalSpacing, verticalSpacing, wrapItems) {
        PagerPagePlan(itemCount)
    }
    var planGeneration by remember { mutableIntStateOf(0) }
    var measuredFits by remember { mutableStateOf(false) }
    val inheritedVisible = LocalTsuyomiPagerItemVisible.current
    var visibleKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val requestedNavigationRevision = state.navigationRevision
    val currentPage = if (itemCount == 0) 1 else state.currentPage
    val canGoPrevious = itemCount > 0 && state.canGoPrevious
    val canGoNext = itemCount > 0 && state.canGoNext
    val boundedModifier = modifier.boundTsuyomiPagerViewport(state)

    // Retire old indexed subcompositions before the live callback sees a new item map.
    key(itemMap) {
        SubcomposeLayout(
            modifier = boundedModifier.onGloballyPositioned { coordinates ->
                // Position callbacks run after measurement; reject a retired projection or layout.
                if (measureSnapshot.itemMap !== itemMap || previousItemMap !== itemMap ||
                    measureSnapshot.pagePlan !== pagePlan ||
                    measureSnapshot.navigationRevision != state.navigationRevision ||
                    coordinates.size.width != measureSnapshot.width ||
                    coordinates.size.height != measureSnapshot.layoutHeight) return@onGloballyPositioned
                measureSnapshot.pageStart?.let { start ->
                    state.commitMeasuredPage(start, measureSnapshot.nextStart, measureSnapshot.previousStart)
                }
                state.commitMeasuredPlan(measureSnapshot.pages, measureSnapshot.pageCount, measureSnapshot.ordinal)
                state.commitChildren(measureSnapshot.visibleNavigations.flatMap { it.plannedChildren.values })
                state.commitIndexedViewport(measureSnapshot.width, measureSnapshot.height)
                if (measureSnapshot.visibilityReady && visibleKeys != measureSnapshot.visibleKeys) visibleKeys = measureSnapshot.visibleKeys
                if (measureSnapshot.pageCount == 1 && state.currentAnchor != measureSnapshot.pages?.firstOrNull()) state.goFirst()
                measureSnapshot.fitsWithoutControls?.let { fits ->
                    measuredFits = fits
                    if (fits && !alwaysShowPageControls && (state.canGoPrevious || state.canGoNext ||
                        state.forwardBoundary != null || state.currentAnchor != measureSnapshot.pages?.firstOrNull())) state.goFirst()
                }
                if (measureSnapshot.needsPlanMeasure) planGeneration++
                measureSnapshot.pendingPlans.forEach { it.measureGeneration++ }
            },
        ) { constraints ->
            measureSnapshot.navigationRevision = requestedNavigationRevision
            measureSnapshot.itemMap = itemMap
            measureSnapshot.pagePlan = pagePlan
            @Suppress("UNUSED_VARIABLE") val generation = planGeneration
            @Suppress("UNUSED_VARIABLE") val nestedGeneration = state.measureGeneration
            require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
                "TsuyomiMeasuredPager requires a finite width and height"
            }

            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val controls = subcompose(PagerControlsSlot) {
                Box(Modifier.fillMaxWidth()) {
                    if (delegated || (!alwaysShowPageControls && (measuredFits ||
                        ((state.pageCount ?: 1) <= 1 && !canGoPrevious && !canGoNext)))) {
                        // A multi-page plan keeps its lane while endpoint navigation resets flags.
                        // Genuinely fitting content still removes it through measuredFits.
                    } else if (pageControls == null) {
                        PaginationBar(
                            page = currentPage,
                            pageCount = state.pageCount,
                            onPrevious = state::goPrevious,
                            onNext = state::goNext,
                            modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                            hasPreviousPage = canGoPrevious,
                            hasNextPage = canGoNext,
                            onFirst = state::goFirst,
                            onLast = state::goLast,
                        )
                    } else {
                        pageControls(
                            currentPage,
                            canGoPrevious,
                            canGoNext,
                            state::goPrevious,
                            state::goNext,
                            state.pageCount,
                            state::goFirst,
                            state::goLast,
                        )
                    }
                }
            }.single()
            val controlPlaceable = controls.measure(
                Constraints(
                    minWidth = width,
                    maxWidth = width,
                    minHeight = 0,
                    maxHeight = height,
                ),
            )
            val contentHeight = height - controlPlaceable.height
            require(contentHeight > 0 || itemCount == 0) {
                "Measured pager needs viewport space after its persistent controls"
            }
            val contentMaxHeight = contentHeight.toDp()
            val horizontalGap = horizontalSpacing.roundToPx().coerceAtLeast(0)
            val verticalGap = verticalSpacing.roundToPx().coerceAtLeast(0)
            val cellWidth = ((width - horizontalGap * (columns - 1)).coerceAtLeast(0) / columns)
            require(cellWidth > 0 || itemCount == 0) { "Measured pager has no width for its cells" }
            val viewportBoundary = boundary.takeIf { state.matchesIndexedViewport(width, contentHeight) }
            val endIndex = if (viewportBoundary == null || itemCount == 0) itemCount else {
                val fallback = viewportBoundary.index.coerceIn(0, itemCount - 1)
                val resolved = if (itemKeys[fallback] == viewportBoundary.key) fallback else
                    itemKeys.indexOf(viewportBoundary.key).takeIf { it >= 0 }
                resolved?.takeIf { it > resolvedStartIndex } ?: itemCount
            }

            val content = ArrayList<List<Placeable>>()
            val measuredItems = HashMap<Int, Placeable>()
            pagePlan.resize(width, contentHeight)
            fun measureItem(itemIndex: Int): Placeable = measuredItems.getOrPut(itemIndex) {
                val navigation = itemNavigations.getOrPut(itemKeys[itemIndex]) { TsuyomiPagerItemNavigation() }
                navigation.maxContentHeightPx = contentHeight
                subcompose(itemKeys[itemIndex]) {
                    itemStateHolder.SaveableStateProvider(itemKeys[itemIndex]) {
                        CompositionLocalProvider(
                            LocalTsuyomiPagerMaxContentHeight provides contentMaxHeight,
                            LocalTsuyomiPagerItemNavigation provides navigation,
                            LocalTsuyomiPagerItemVisible provides (inheritedVisible && previousItemMap == itemMap &&
                                itemKeys[itemIndex] in visibleKeys && itemIndex >= resolvedStartIndex && itemIndex < endIndex),
                        ) {
                            Box { itemContent(itemIndex) }
                        }
                    }
                }.single().measure(
                    Constraints(maxWidth = if (wrapItems) width else cellWidth),
                ).also { item ->
                    val nested = navigation.plannedChildren.values
                    val firstDelta = nested.sumOf { (it.measuredFirstPageHeight ?: it.measuredVisibleHeight) - it.measuredVisibleHeight }
                    val lastDelta = nested.sumOf { (it.measuredLastPageHeight ?: it.measuredVisibleHeight) - it.measuredVisibleHeight }
                    val extraPages = nested.sumOf { ((it.measuredPageCount ?: 1) - 1).coerceAtLeast(0) }
                    pagePlan.record(itemIndex, item.width, item.height + firstDelta, item.height + lastDelta, extraPages,
                        nested.any { it.measuredPageCount == null })
                }
            }
            var index = resolvedStartIndex
            var usedHeight = 0
            var nextStart: MeasuredPageAnchor? = null

            while (index < endIndex) {
                var rowEnd = if (wrapItems) index else minOf(index + columns, endIndex)
                val rowItems = ArrayList<Placeable>(if (wrapItems) 4 else rowEnd - index)
                var rowWidth = 0
                for (itemIndex in index until if (wrapItems) endIndex else rowEnd) {
                    val item = measureItem(itemIndex)
                    if (wrapItems) {
                        val gap = if (rowItems.isEmpty()) 0 else horizontalGap
                        if (rowItems.isNotEmpty() && rowWidth + gap + item.width > width) break
                        rowWidth += gap + item.width
                        rowEnd = itemIndex + 1
                    }
                    rowItems += item
                }
                val rowHeight = rowItems.maxOfOrNull { it.height } ?: 0
                require(content.isNotEmpty() || rowHeight <= contentHeight) {
                    "A complete measured pager row ($rowHeight px) exceeds the content viewport ($contentHeight px); index=$index, viewport=$height px, controls=${controlPlaceable.height} px"
                }
                val gapBefore = if (content.isEmpty()) 0 else verticalGap
                if (content.isNotEmpty() && usedHeight + gapBefore + rowHeight > contentHeight) {
                    nextStart = MeasuredPageAnchor(itemKeys[index], index)
                    break
                }
                content += rowItems
                usedHeight += gapBefore + rowHeight
                index = rowEnd
                if (usedHeight >= contentHeight && index < itemCount) {
                    nextStart = MeasuredPageAnchor(itemKeys[index], index)
                    break
                }
            }
            if (nextStart == null && index < itemCount) {
                nextStart = MeasuredPageAnchor(itemKeys[index], index)
            }
            val pageStart = if (itemCount == 0) null else MeasuredPageAnchor(
                key = resolvedStartKey ?: itemKeys[resolvedStartIndex],
                index = resolvedStartIndex,
            )
            measureSnapshot.pageStart = pageStart
            measureSnapshot.nextStart = nextStart
            measureSnapshot.previousStart = null
            measureSnapshot.visibleNavigations = (resolvedStartIndex until index).mapNotNull { itemNavigations[itemKeys[it]] }
            measureSnapshot.visibleKeys = (resolvedStartIndex until index).mapTo(HashSet()) { itemKeys[it] }
            // Learn only a fixed batch of unseen local items per pass; no source discovery occurs.
            var remainingProbes = 24
            while (pagePlan.nextUnmeasured < itemCount && remainingProbes-- > 0) {
                measureItem(pagePlan.nextUnmeasured)
            }
            val pendingPlans = ArrayList<TsuyomiMeasuredPagerState>()
            for (itemIndex in pagePlan.pendingIndices.toList()) {
                val pending = itemNavigations[itemKeys[itemIndex]]?.plannedChildren?.values?.filter { it.measuredPageCount == null }.orEmpty()
                if (pending.isNotEmpty()) {
                    if (remainingProbes-- > 0) measureItem(itemIndex)
                    pendingPlans.addAll(pending)
                }
            }
            val plannedStarts = pagePlan.pageStarts(columns, horizontalGap, verticalGap, wrapItems)
            val pages = if (plannedStarts != null && pagePlan.pendingIndices.isEmpty()) pagePlan.anchors(plannedStarts, itemKeys) else null
            val count = pages?.let { it.size + pagePlan.additionalPagesBefore(itemCount) }
            val previousIndex = pagePlan.previousPageStart(resolvedStartIndex, columns, horizontalGap, verticalGap, wrapItems)
            measureSnapshot.previousStart = previousIndex?.let { MeasuredPageAnchor(itemKeys[it], it) }
            val basePage = pages?.let { starts ->
                val exact = starts.binarySearchBy(resolvedStartIndex) { it.index }
                if (exact >= 0) exact + 1 else (-exact - 1).coerceAtLeast(1)
            }
            val precedingNestedPages = pagePlan.additionalPagesBefore(resolvedStartIndex)
            measureSnapshot.ordinal = basePage?.let { it + precedingNestedPages +
                measureSnapshot.visibleNavigations.sumOf { nav -> nav.plannedChildren.values.sumOf { child -> child.currentPage - 1 } } }
            measureSnapshot.pages = pages
            measureSnapshot.width = width
            measureSnapshot.height = contentHeight
            measureSnapshot.pageCount = count
            measureSnapshot.fitsWithoutControls = if (pages == null) null else {
                pagePlan.pageStarts(columns, horizontalGap, verticalGap, wrapItems, height)?.size == 1 &&
                    pagePlan.additionalPagesBefore(itemCount) == 0
            }
            val fitting = measureSnapshot.fitsWithoutControls == true && !alwaysShowPageControls
            val endpointLaneSettled = ((state.pageCount ?: 0) > PAGINATION_JUMP_THRESHOLD) == ((count ?: 0) > PAGINATION_JUMP_THRESHOLD)
            measureSnapshot.visibilityReady = pages != null && when {
                delegated -> true
                fitting -> resolvedStartIndex == 0 && viewportBoundary == null && controlPlaceable.height == 0
                else -> controlPlaceable.height > 0 && endpointLaneSettled
            }
            measureSnapshot.needsPlanMeasure = pages == null && itemCount > 0
            measureSnapshot.pendingPlans = pendingPlans
            state.measuredPages = pages
            state.measuredPageCount = count
            // Decoration never displaces a semantic item or creates a decoration-only page.
            val decoration = pageDecoration?.let { content ->
                subcompose(PagerDecorationSlot) { Box(Modifier.fillMaxWidth()) { content() } }
                    .single().measure(Constraints(minWidth = width, maxWidth = width))
                    .takeIf { it.height <= contentHeight - usedHeight }
            }

            val layoutHeight = if (fillViewport || (delegated && nextStart != null)) height else
                constraints.copy(minHeight = 0).constrainHeight(usedHeight + (decoration?.height ?: 0) + controlPlaceable.height)
            measureSnapshot.layoutHeight = layoutHeight
            state.measuredVisibleHeight = layoutHeight
            state.measuredFirstPageHeight = if (pages == null) null else if ((count ?: 1) > 1) height else layoutHeight
            state.measuredLastPageHeight = if (pages == null) null else
                if (fillViewport) height else pagePlan.lastPageHeight + controlPlaceable.height
            layout(width, layoutHeight) {
                var y = verticalAlignment.align(usedHeight + (decoration?.height ?: 0), layoutHeight - controlPlaceable.height)
                decoration?.let {
                    it.placeRelative(0, y)
                    y += it.height
                }
                content.forEachIndexed { rowIndex, cells ->
                    if (rowIndex > 0) y += verticalGap
                    var x = 0
                    cells.forEach { cell ->
                        val laneWidth = if (wrapItems) cell.width else cellWidth
                        val absoluteLaneStart = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) x else width - x - laneWidth
                        cell.place(absoluteLaneStart + horizontalAlignment.align(cell.width, laneWidth, layoutDirection), y)
                        x += laneWidth + horizontalGap
                    }
                    y += cells.maxOfOrNull { it.height } ?: 0
                }
                controlPlaceable.placeRelative(0, layoutHeight - controlPlaceable.height)
            }
        }
    }

    SideEffect {
        if (previousItemMap != itemMap) {
            previousItemMap = itemMap
            visibleKeys = emptySet()
            state.reconcileItemMap(itemKeys, resolvedStartKey?.let { MeasuredPageAnchor(it, resolvedStartIndex) })
        }
        if (itemCount > 0 && (!requestedAnchorStillPresent || anchor == null)) {
            state.reanchorCurrent(MeasuredPageAnchor(resolvedStartKey!!, resolvedStartIndex))
        }
    }
}

private class PagerMeasureSnapshot {
    var itemMap: PagerItemMap? = null
    var pagePlan: PagerPagePlan? = null
    var pageStart: MeasuredPageAnchor? = null
    var nextStart: MeasuredPageAnchor? = null
    var previousStart: MeasuredPageAnchor? = null
    var visibleNavigations: List<TsuyomiPagerItemNavigation> = emptyList()
    var pages: List<MeasuredPageAnchor>? = null
    var pageCount: Int? = null
    var needsPlanMeasure: Boolean = false
    var pendingPlans: List<TsuyomiMeasuredPagerState> = emptyList()
    var ordinal: Int? = null
    var width: Int = 0
    var height: Int = 0
    var layoutHeight: Int = 0
    var fitsWithoutControls: Boolean? = null
    var navigationRevision: Int = -1
    var visibleKeys: Set<String> = emptySet()
    var visibilityReady: Boolean = false
}

/** Cached natural dimensions, not retained offscreen compositions or guessed uniform row sizes. */
private class PagerPagePlan(itemCount: Int) {
    private val widths = IntArray(itemCount) { -1 }
    private val heights = IntArray(itemCount) { -1 }
    private val lastHeights = IntArray(itemCount) { -1 }
    private val extraPages = IntArray(itemCount)
    var lastPageHeight = 0
        private set
    private var width = -1
    private var height = -1
    private val rowStarts = IntArray(itemCount)
    private val rowHeights = IntArray(itemCount)
    var nextUnmeasured = 0
        private set
    val pendingIndices = LinkedHashSet<Int>()
    private val pageCache = HashMap<Int, Pair<List<Int>, Int>>()
    private var anchorStarts: List<Int>? = null
    private var cachedAnchors: List<MeasuredPageAnchor> = emptyList()
    private var previousEnd = -1
    private var cachedPrevious: Int? = null
    private val additionalPagePrefix = IntArray(itemCount + 1)
    private var prefixDirty = true

    fun anchors(starts: List<Int>, keys: List<String>): List<MeasuredPageAnchor> {
        if (anchorStarts !== starts) {
            anchorStarts = starts
            cachedAnchors = starts.map { MeasuredPageAnchor(keys[it], it) }
        }
        return cachedAnchors
    }

    fun additionalPagesBefore(index: Int): Int {
        if (prefixDirty) {
            for (item in extraPages.indices) additionalPagePrefix[item + 1] = additionalPagePrefix[item] + extraPages[item]
            prefixDirty = false
        }
        return additionalPagePrefix[index]
    }

    private fun invalidate() {
        pageCache.clear()
        anchorStarts = null
        previousEnd = -1
        prefixDirty = true
    }

    fun resize(width: Int, height: Int) {
        if (this.width == width && this.height == height) return
        this.width = width
        this.height = height
        widths.fill(-1)
        heights.fill(-1)
        lastHeights.fill(-1)
        extraPages.fill(0)
        pendingIndices.clear()
        invalidate()
        nextUnmeasured = 0
    }

    fun record(index: Int, width: Int, firstHeight: Int, lastHeight: Int, additionalPages: Int, pending: Boolean) {
        if (pending) pendingIndices.add(index) else pendingIndices.remove(index)
        if (widths[index] == width && heights[index] == firstHeight && lastHeights[index] == lastHeight && extraPages[index] == additionalPages) return
        invalidate()
        widths[index] = width
        heights[index] = firstHeight
        lastHeights[index] = lastHeight
        extraPages[index] = additionalPages
        while (nextUnmeasured < heights.size && heights[nextUnmeasured] >= 0) nextUnmeasured++
    }

    fun previousPageStart(end: Int, columns: Int, horizontalGap: Int, verticalGap: Int, wrap: Boolean): Int? {
        if (previousEnd == end) return cachedPrevious
        if (end == 0 || (0 until end).any { heights[it] < 0 }) return null
        var index = 0
        var rowCount = 0
        while (index < end) {
            var rowEnd = if (wrap) index + 1 else minOf(index + columns, end)
            if (wrap) {
                var rowWidth = widths[index]
                while (rowEnd < end && rowWidth + horizontalGap + widths[rowEnd] <= width) {
                    rowWidth += horizontalGap + widths[rowEnd]
                    rowEnd++
                }
            }
            rowStarts[rowCount] = index
            var rowHeight = 0
            for (cell in index until rowEnd) rowHeight = maxOf(rowHeight, heights[cell])
            rowHeights[rowCount++] = rowHeight
            index = rowEnd
        }
        var usedHeight = 0
        var start = end
        for (row in rowCount - 1 downTo 0) {
            val gap = if (start == end) 0 else verticalGap
            if (usedHeight + gap + rowHeights[row] > height) break
            usedHeight += gap + rowHeights[row]
            start = rowStarts[row]
        }
        previousEnd = end
        cachedPrevious = start.takeIf { it < end }
        return cachedPrevious
    }

    fun pageStarts(columns: Int, horizontalGap: Int, verticalGap: Int, wrap: Boolean, viewportHeight: Int = height): List<Int>? {
        if (nextUnmeasured < heights.size) return null
        if (heights.isEmpty()) return emptyList()
        pageCache[viewportHeight]?.let { (starts, lastHeight) ->
            if (viewportHeight == height) lastPageHeight = lastHeight
            return starts
        }
        val pages = arrayListOf(0)
        var index = 0
        var usedHeight = 0
        while (index < heights.size) {
            var end = if (wrap) index + 1 else minOf(index + columns, heights.size)
            if (wrap) {
                var rowWidth = widths[index]
                while (end < widths.size && rowWidth + horizontalGap + widths[end] <= width) {
                    rowWidth += horizontalGap + widths[end]
                    end++
                }
            }
            var rowHeight = 0
            var lastRowHeight = 0
            var additionalPages = 0
            for (cell in index until end) {
                rowHeight = maxOf(rowHeight, heights[cell])
                lastRowHeight = maxOf(lastRowHeight, lastHeights[cell])
                additionalPages += extraPages[cell]
            }
            require(rowHeight <= viewportHeight) { "A complete measured pager row exceeds the content viewport; index=$index" }
            val gap = if (usedHeight == 0) 0 else verticalGap
            if (usedHeight > 0 && usedHeight + gap + rowHeight > viewportHeight) {
                pages.add(index)
                usedHeight = rowHeight
            } else {
                usedHeight += gap + rowHeight
            }
            if (additionalPages > 0) usedHeight = lastRowHeight
            index = end
        }
        if (viewportHeight == height) lastPageHeight = usedHeight
        pageCache[viewportHeight] = pages to usedHeight
        return pages
    }
}
