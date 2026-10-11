/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

/** A complete line laid out at its original paragraph width and font metrics. */
data class TsuyomiMeasuredTextLine internal constructor(
    val content: String,
    internal val layout: TextLayoutResult,
    internal val topPx: Float,
    internal val heightPx: Int,
)

/**
 * Measures [text] at the supplied actual cell width and returns complete rendered lines. Use the
 * returned lines as indexed items in [TsuyomiMeasuredPager] when a paragraph belongs to a larger
 * list item; render each segment with [TsuyomiMeasuredTextLine].
 */
@Composable
fun rememberTsuyomiMeasuredTextLines(
    text: String,
    maxWidth: Dp,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Start,
): List<TsuyomiMeasuredTextLine> {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val widthPx = with(density) { maxWidth.roundToPx() }
    return remember(text, maxWidth, style, color, textAlign, density, layoutDirection, textMeasurer) {
        if (text.isEmpty() || widthPx <= 0) {
            emptyList()
        } else {
            val layout = textMeasurer.measure(
                text = AnnotatedString(text),
                style = style.copy(color = color, textAlign = textAlign),
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                constraints = Constraints.fixedWidth(widthPx),
                layoutDirection = layoutDirection,
            )
            List(layout.lineCount) { lineIndex ->
                val start = layout.getLineStart(lineIndex)
                val end = layout.getLineEnd(lineIndex)
                val top = layout.getLineTop(lineIndex)
                val bottom = layout.getLineBottom(lineIndex)
                TsuyomiMeasuredTextLine(
                    content = text.substring(start, end),
                    layout = layout,
                    topPx = top,
                    heightPx = ceil(bottom - top).toInt().coerceAtLeast(1),
                )
            }
        }
    }
}

/** Renders one complete measured line without reflowing or dropping its original characters. */
@Composable
fun TsuyomiMeasuredTextLine(
    line: TsuyomiMeasuredTextLine,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val height = with(density) { line.heightPx.toDp() }
    val pagerMaxHeight = LocalTsuyomiPagerMaxContentHeight.current
    if (pagerMaxHeight != null) {
        require(line.heightPx <= with(density) { pagerMaxHeight.roundToPx() }) {
            "A complete measured text line exceeds the content viewport; split or resize the content"
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.text = AnnotatedString(line.content) },
    ) {
        clipRect {
            drawText(line.layout, topLeft = Offset(0f, -line.topPx))
        }
    }
}

private object TextPagerControlsSlot
private object TextPagerContentSlot

/**
 * Displays a long plain-text passage as complete measured text-line slices. Line wrapping uses the
 * actual finite content width, current font resolver, density, layout direction and style. Page
 * controls reserve their measured height; only the current slice is drawn, and previous/next
 * history is saved by the first visible character offset. Use an item-keyed [state] when the source
 * paragraph is one of several independent values.
 */
@Composable
fun TsuyomiMeasuredTextPager(
    text: String,
    modifier: Modifier = Modifier,
    state: TsuyomiMeasuredPagerState = rememberSaveable(text, saver = TsuyomiMeasuredPagerState.Saver) {
        TsuyomiMeasuredPagerState("text:0")
    },
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Start,
    fillViewport: Boolean = true,
    alwaysShowPageControls: Boolean = true,
    fillWidth: Boolean = true,
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
    state.isTextPager = true
    val delegated = registerTsuyomiNestedPager(state)
    val textMeasurer = rememberTextMeasurer()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val measureSnapshot = remember { TextPagerMeasureSnapshot() }
    val plan = remember(text, style, color, textAlign, layoutDirection, density) { TextPagerPlan() }
    var planGeneration by remember { mutableIntStateOf(0) }
    val requestedNavigationRevision = state.navigationRevision
    val currentPage = state.currentPage
    val canGoPrevious = state.canGoPrevious
    val canGoNext = state.canGoNext
    val anchorKey = state.currentAnchorKey
    val startOffset = anchorKey?.removePrefix("text:")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val savedEndOffset = state.forwardBoundary?.takeIf { it.key.startsWith("text:") }?.index
    val boundedModifier = modifier.boundTsuyomiPagerViewport(state)

    SubcomposeLayout(
        modifier = boundedModifier.onGloballyPositioned {
            if (measureSnapshot.navigationRevision != state.navigationRevision) return@onGloballyPositioned
            measureSnapshot.pageStart?.let { state.commitMeasuredPage(it, measureSnapshot.nextStart) }
            measureSnapshot.previousStart?.let { previous ->
                measureSnapshot.pageStart?.let { state.commitMeasuredPage(it, measureSnapshot.nextStart, previous) }
            }
            state.commitMeasuredPlan(measureSnapshot.pages, measureSnapshot.pageCount, measureSnapshot.ordinal)
            if (measureSnapshot.needsPlanMeasure) planGeneration++
        },
    ) { constraints ->
        measureSnapshot.navigationRevision = requestedNavigationRevision
        @Suppress("UNUSED_VARIABLE") val generation = planGeneration
        @Suppress("UNUSED_VARIABLE") val nestedGeneration = state.measureGeneration
        require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
            "TsuyomiMeasuredTextPager requires a finite width and height"
        }
        val maxWidth = constraints.maxWidth
        val height = constraints.maxHeight
        val requestedOffset = startOffset.coerceIn(0, text.length)
        val availableEnd = savedEndOffset?.coerceIn(requestedOffset, text.length) ?: text.length
        val lineProbe = textMeasurer.measure(
            text = "Mg\nMg",
            style = style,
            maxLines = 2,
            constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(1)),
            layoutDirection = layoutDirection,
        )
        val minimumLineHeight = (0 until lineProbe.lineCount).minOf {
            floor(lineProbe.getLineBottom(it) - lineProbe.getLineTop(it)).toInt().coerceAtLeast(1)
        }

        // Each probe is bounded by a viewport plus two lookahead lines, including long paragraphs.
        fun measureSlice(start: Int, end: Int, viewportHeight: Int): MeasuredTextSlice {
            if (start == end || maxWidth == 0) return MeasuredTextSlice(null, start, 0)
            var probeEnd = start + minOf(64, end - start)
            fun measurePrefix(): TextLayoutResult {
                if (probeEnd < end && text[probeEnd - 1].isHighSurrogate() && text[probeEnd].isLowSurrogate()) probeEnd++
                return textMeasurer.measure(
                    text = AnnotatedString(text.substring(start, probeEnd)),
                    style = style.copy(color = color, textAlign = textAlign),
                    softWrap = true,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                    maxLines = viewportHeight.coerceAtLeast(1) / minimumLineHeight + 2,
                    constraints = Constraints(maxWidth = maxWidth),
                    layoutDirection = layoutDirection,
                )
            }
            var result = measurePrefix()
            while (!result.hasVisualOverflow && probeEnd < end) {
                probeEnd += minOf(probeEnd - start, end - probeEnd)
                result = measurePrefix()
            }
            var endLine = 0
            var segmentHeight = 0
            while (endLine < result.lineCount) {
                val lineHeight = ceil(result.getLineBottom(endLine) - result.getLineTop(endLine)).toInt().coerceAtLeast(1)
                require(lineHeight <= viewportHeight) { "A complete measured text line exceeds the content viewport" }
                if (segmentHeight + lineHeight > viewportHeight) break
                segmentHeight += lineHeight
                endLine++
            }
            val visibleEnd = start + when {
                endLine < result.lineCount -> result.getLineStart(endLine)
                !result.hasVisualOverflow -> probeEnd - start
                else -> result.getLineEnd(endLine - 1)
            }
            require(visibleEnd > start) { "Measured text pagination must advance by complete characters" }
            return MeasuredTextSlice(result, visibleEnd, segmentHeight)
        }

        val probe = measureSlice(requestedOffset, availableEnd, height)
        val needsControls = !delegated && (alwaysShowPageControls || state.canGoPrevious || availableEnd < text.length || probe.end < text.length)
        val width = if (fillWidth || needsControls) maxWidth else constraints.constrainWidth(probe.layout?.size?.width ?: 0)
        val controls = subcompose(TextPagerControlsSlot) {
            Box(Modifier.fillMaxWidth()) {
                if (needsControls) {
                    if (pageControls == null) {
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
                        pageControls(currentPage, canGoPrevious, canGoNext, state::goPrevious, state::goNext,
                            state.pageCount, state::goFirst, state::goLast)
                    }
                }
            }
        }.single().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
        val contentHeight = height - controls.height
        require(contentHeight > 0 || text.isEmpty()) { "Measured text pager needs viewport space after its persistent controls" }
        val slice = if (contentHeight == height) probe else measureSlice(requestedOffset, availableEnd, contentHeight)
        val visibleText = text.substring(requestedOffset, slice.end)
        val pageStart = if (text.isEmpty()) null else MeasuredPageAnchor("text:$requestedOffset", requestedOffset)
        val nextStart = if (slice.end < text.length) MeasuredPageAnchor("text:${slice.end}", slice.end) else null
        measureSnapshot.pageStart = pageStart
        measureSnapshot.nextStart = nextStart

        plan.resize(maxWidth, contentHeight, state.textBoundaryGeneration)
        var remainingProbes = 16
        while (plan.nextOffset < text.length && remainingProbes-- > 0) {
            val start = plan.nextOffset
            val end = state.textBoundary(start)?.takeIf { it > start }?.coerceAtMost(text.length) ?: text.length
            val measured = measureSlice(start, end, contentHeight)
            plan.record(start, measured.end, measured.height)
        }
        val pages = plan.completedPages(text.length)
        measureSnapshot.pages = pages
        measureSnapshot.pageCount = pages?.size?.coerceAtLeast(1)
        val exact = pages?.binarySearchBy(requestedOffset) { it.index }
        val ordinal = exact?.let { if (it >= 0) it + 1 else (-it - 1).coerceAtLeast(1) }
        measureSnapshot.ordinal = ordinal
        measureSnapshot.previousStart = ordinal?.takeIf { it > 1 }?.let { pages?.getOrNull(it - 2) }
        measureSnapshot.needsPlanMeasure = pages == null
        state.measuredPages = pages
        state.measuredPageCount = measureSnapshot.pageCount

        val textPlaceable = slice.layout?.let { textLayout ->
            subcompose(TextPagerContentSlot) {
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(with(density) { slice.height.toDp() })
                        .semantics { this.text = AnnotatedString(visibleText) },
                ) {
                    clipRect { drawText(textLayout, topLeft = Offset.Zero) }
                }
            }.single().measure(Constraints.fixed(width, slice.height))
        }
        // A fitting nested passage has natural height, never an inherited full-viewport minimum.
        val layoutHeight = if (fillViewport || (delegated && nextStart != null)) height else
            constraints.copy(minHeight = 0).constrainHeight(slice.height + controls.height)
        state.measuredVisibleHeight = layoutHeight
        state.measuredFirstPageHeight = pages?.let { if (it.size > 1) height else layoutHeight }
        state.measuredLastPageHeight = pages?.let { if (fillViewport) height else plan.lastPageHeight + controls.height }
        layout(width, layoutHeight) {
            textPlaceable?.placeRelative(0, 0)
            controls.placeRelative(0, layoutHeight - controls.height)
        }
    }
}

private class TextPagerMeasureSnapshot {
    var pageStart: MeasuredPageAnchor? = null
    var nextStart: MeasuredPageAnchor? = null
    var ordinal: Int? = null
    var previousStart: MeasuredPageAnchor? = null
    var pages: List<MeasuredPageAnchor>? = null
    var pageCount: Int? = null
    var needsPlanMeasure: Boolean = false
    var navigationRevision: Int = -1
}

private data class MeasuredTextSlice(val layout: TextLayoutResult?, val end: Int, val height: Int)

/** Only character boundaries are retained; complete passage layouts are never materialized. */
private class TextPagerPlan {
    private var width = -1
    private var boundaryGeneration = -1
    private var height = -1
    val pages = ArrayList<MeasuredPageAnchor>()
    var nextOffset = 0
        private set
    private var completedPages: List<MeasuredPageAnchor>? = null
    var lastPageHeight: Int = 0
        private set

    fun completedPages(textLength: Int): List<MeasuredPageAnchor>? {
        if (nextOffset != textLength) return null
        if (completedPages == null) completedPages = pages.toList()
        return completedPages
    }

    fun resize(width: Int, height: Int, boundaryGeneration: Int) {
        if (this.width == width && this.height == height && this.boundaryGeneration == boundaryGeneration) return
        this.boundaryGeneration = boundaryGeneration
        this.width = width
        this.height = height
        pages.clear()
        nextOffset = 0
        completedPages = null
    }

    fun record(start: Int, end: Int, height: Int) {
        pages.add(MeasuredPageAnchor("text:$start", start))
        nextOffset = end
        lastPageHeight = height
    }
}
