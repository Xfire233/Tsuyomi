/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderInlineKind
import org.tsuyomi.shared.sourcecontract.ReaderSpan
import org.tsuyomi.shared.sourcecontract.canonicalAnchorText

internal class ReaderRichText private constructor(
    val displayText: AnnotatedString,
    val sourceText: String,
    val rubies: List<ReaderRuby>,
    private val displayBoundaryToCodePoint: IntArray,
    private val spans: List<ReaderSpan>,
    val annotationOnly: Boolean = false,
    private val associatedStart: Int = displayBoundaryToCodePoint.first(),
    private val associatedEnd: Int = displayBoundaryToCodePoint.last(),
    private val ambiguousStart: Int? = null,
    val sourceCodePointLength: Int = sourceText.codePointCount(0, sourceText.length),
    private val actionSpanIndices: IntArray = readerActionSpanIndices(spans),
) {
    fun codePointOffsetAt(displayOffset: Int): Int =
        displayBoundaryToCodePoint[displayOffset.coerceIn(0, displayBoundaryToCodePoint.lastIndex)]

    fun displayOffsetAt(codePointOffset: Int): Int = readerDisplayOffsetAt(displayBoundaryToCodePoint, codePointOffset)

    fun hasExactPositionAt(displayOffset: Int): Boolean = hasExactCodePointPosition(codePointOffsetAt(displayOffset))

    fun hasExactCodePointPosition(offset: Int): Boolean = !annotationOnly && offset != ambiguousStart

    fun containsCodePointOffset(offset: Int): Boolean = !annotationOnly && offset >= displayBoundaryToCodePoint.first() &&
        (offset < displayBoundaryToCodePoint.last() || offset == sourceCodePointLength && offset == displayBoundaryToCodePoint.last())

    fun accessibleText(start: Int, end: Int): String {
        val first = codePointOffsetAt(start)
        val last = codePointOffsetAt(end)
        if (first == last) return ""
        return sourceText.substring(sourceText.offsetByCodePoints(0, first), sourceText.offsetByCodePoints(0, last))
    }

    fun actions(start: Int, end: Int): List<Pair<String, ReaderInlineKind>> = buildList {
        if (annotationOnly && 0 !in start until end) return@buildList
        for (index in actionSpanIndices) {
            val span = spans[index]
            if (span.endCodePoint <= associatedStart || span.startCodePoint >= associatedEnd) continue
            if (!annotationOnly) {
                val spanStart = displayOffsetAt(span.startCodePoint)
                val spanEnd = maxOf(displayOffsetAt(span.endCodePoint), spanStart + 1)
                if (spanEnd <= start || spanStart >= end) continue
            }
            add(sourceText.substring(sourceText.offsetByCodePoints(0, span.startCodePoint),
                sourceText.offsetByCodePoints(0, span.endCodePoint)) to span.kind)
        }
    }

    fun actionAt(position: Offset, measurement: MeasuredReaderText, start: Int = 0): ReaderInlineKind? {
        val displayEnd = start + measurement.body.layoutInput.text.length
        var measuredIndex = 0
        for (ruby in rubies) {
            if (ruby.displayOffset !in start until displayEnd) continue
            val measured = measurement.rubies[measuredIndex]
            val bounds = measurement.body.placeholderRects.getOrNull(measuredIndex++) ?: continue
            if (!bounds.contains(position)) continue
            measured.base?.let { base ->
                val local = position - Offset(bounds.left + (bounds.width - base.size.width) / 2f,
                    bounds.top + measured.baseTopPx)
                val offset = readerTextHitOffset(base, local)
                if (offset != null) return actionAtCodePoint(codePointOffsetAt(ruby.displayOffset) +
                    base.layoutInput.text.text.codePointCount(0, offset))
            }
            for (annotation in measured.annotations) {
                val local = position - Offset(bounds.left + annotation.x, bounds.top + annotation.y)
                if (readerTextHitOffset(annotation.layout, local) == null) continue
                val span = spans[annotation.sourceIndex]
                return actionCoveringRange(span.startCodePoint, span.endCodePoint)
            }
            return null
        }
        val offset = readerTextHitOffset(measurement.body, position) ?: return null
        val displayOffset = start + offset
        if (annotationOnly || rubies.any { it.displayOffset == displayOffset }) return null
        return actionAtCodePoint(codePointOffsetAt(displayOffset))
    }

    private fun actionAtCodePoint(offset: Int): ReaderInlineKind? {
        for (index in actionSpanIndices) {
            val span = spans[index]
            if (offset >= span.startCodePoint && offset < span.endCodePoint) return span.kind
        }
        return null
    }

    private fun actionCoveringRange(start: Int, end: Int): ReaderInlineKind? {
        for (index in actionSpanIndices) {
            val span = spans[index]
            if (span.startCodePoint <= start && span.endCodePoint >= end) return span.kind
        }
        return null
    }

    fun withRubyMeasurements(measurements: List<MeasuredReaderRuby>): ReaderRichText = ReaderRichText(
        displayText, sourceText, rubies.mapIndexed { index, ruby -> ruby.copy(measured = measurements[index]) },
        displayBoundaryToCodePoint, spans, sourceCodePointLength = sourceCodePointLength, actionSpanIndices = actionSpanIndices,
    )

    fun displayFragment(start: Int, end: Int, measuredRuby: MeasuredReaderRuby? = null,
        ruby: ReaderRuby? = null): ReaderRichText = ReaderRichText(
        displayText.subSequence(start, end), sourceText,
        if (ruby == null) emptyList() else listOf(ruby.copy(displayOffset = ruby.displayOffset - start, measured = measuredRuby)),
        displayBoundaryToCodePoint.copyOfRange(start, end + 1), spans,
        sourceCodePointLength = sourceCodePointLength, actionSpanIndices = actionSpanIndices,
    )

    fun rubyFragment(ruby: ReaderRuby, measured: MeasuredReaderRuby, start: Int, end: Int,
        rangeStart: Int, rangeEnd: Int, annotationOnly: Boolean): ReaderRichText {
        return ReaderRichText(AnnotatedString("\uFFFC"), sourceText,
            listOf(ruby.copy(displayOffset = 0, measured = measured)), intArrayOf(start, end), spans,
            annotationOnly, rangeStart, rangeEnd, ruby.startCodePoint, sourceCodePointLength, actionSpanIndices)
    }

    companion object {
        fun create(text: String, spans: List<ReaderSpan>): ReaderRichText {
            val codePointLength = text.codePointCount(0, text.length)
            val codePointToUtf16 = IntArray(codePointLength + 1)
            var utf16 = 0
            for (offset in 0 until codePointLength) {
                codePointToUtf16[offset] = utf16
                utf16 += Character.charCount(text.codePointAt(utf16))
            }
            codePointToUtf16[codePointLength] = text.length
            val annotations = ArrayList<ReaderRubyAnnotation>()
            val styleRuns = readerStyleRuns(spans)
            spans.forEachIndexed { index, span ->
                val ruby = span.kind as? ReaderInlineKind.Ruby
                if (ruby != null) annotations += ReaderRubyAnnotation(span.startCodePoint, span.endCodePoint, ruby.annotation, index)
            }
            val displayToCodePoint = ArrayList<Int>(text.length + 1).apply { add(0) }
            val builder = AnnotatedString.Builder()
            var codePointCursor = 0
            fun appendSourceThrough(endCodePoint: Int) {
                while (codePointCursor < endCodePoint) {
                    val sourceStart = codePointToUtf16[codePointCursor]
                    val sourceEnd = codePointToUtf16[codePointCursor + 1]
                    builder.append(text, sourceStart, sourceEnd)
                    repeat(sourceEnd - sourceStart) { charIndex ->
                        displayToCodePoint += if (sourceStart + charIndex + 1 == sourceEnd) codePointCursor + 1 else codePointCursor
                    }
                    codePointCursor++
                }
            }
            val rubies = ArrayList<ReaderRuby>()
            var annotationIndex = 0
            var styleRunIndex = 0
            while (annotationIndex < annotations.size) {
                val outer = annotations[annotationIndex]
                var next = annotationIndex + 1
                while (next < annotations.size && annotations[next].startCodePoint < outer.endCodePoint) next++
                appendSourceThrough(outer.startCodePoint)
                val displayStart = builder.length
                val sourceStart = codePointToUtf16[outer.startCodePoint]
                val sourceEnd = codePointToUtf16[outer.endCodePoint]
                while (styleRunIndex < styleRuns.size && styleRuns[styleRunIndex].end <= outer.startCodePoint) styleRunIndex++
                val baseStyles = ArrayList<AnnotatedString.Range<SpanStyle>>()
                var runIndex = styleRunIndex
                while (runIndex < styleRuns.size && styleRuns[runIndex].start < outer.endCodePoint) {
                    val run = styleRuns[runIndex++]
                    val first = maxOf(run.start, outer.startCodePoint)
                    val last = minOf(run.end, outer.endCodePoint)
                    baseStyles += AnnotatedString.Range(run.style, codePointToUtf16[first] - sourceStart,
                        codePointToUtf16[last] - sourceStart)
                }
                rubies += ReaderRuby(displayStart, AnnotatedString(text.substring(sourceStart, sourceEnd), spanStyles = baseStyles),
                    annotations.subList(annotationIndex, next), outer.startCodePoint, outer.endCodePoint)
                builder.append('\uFFFC')
                displayToCodePoint += outer.endCodePoint
                codePointCursor = outer.endCodePoint
                annotationIndex = next
            }
            appendSourceThrough(codePointLength)
            val boundaries = displayToCodePoint.toIntArray()
            for (run in styleRuns) {
                val start = readerDisplayOffsetAt(boundaries, run.start)
                val end = maxOf(readerDisplayOffsetAt(boundaries, run.end), start + 1).coerceAtMost(builder.length)
                if (end > start) builder.addStyle(run.style, start, end)
            }
            return ReaderRichText(builder.toAnnotatedString(), text, rubies, boundaries, spans, sourceCodePointLength = codePointLength)
        }
    }
}

/** Native caret rounding may point just past a glyph; keep the hit inside its actual bounds. */
private fun readerTextHitOffset(layout: TextLayoutResult, position: Offset): Int? {
    val text = layout.layoutInput.text.text
    if (text.isEmpty() || position.x < 0f || position.y < 0f ||
        position.x >= layout.size.width || position.y >= layout.size.height) return null
    var offset = layout.getOffsetForPosition(position).coerceIn(0, text.lastIndex)
    if (offset > 0 && Character.isLowSurrogate(text[offset]) && Character.isHighSurrogate(text[offset - 1])) offset--
    if (!layout.getBoundingBox(offset).contains(position)) {
        if (offset == 0) return null
        offset--
        if (offset > 0 && Character.isLowSurrogate(text[offset]) && Character.isHighSurrogate(text[offset - 1])) offset--
        if (!layout.getBoundingBox(offset).contains(position)) return null
    }
    return offset
}

private class ReaderStyleRun(val start: Int, var end: Int, val style: SpanStyle)

private fun appendReaderStyleRun(runs: ArrayList<ReaderStyleRun>, start: Int, end: Int, style: SpanStyle?) {
    if (end <= start || style == null) return
    val previous = runs.lastOrNull()
    if (previous != null && previous.end == start && previous.style == style) previous.end = end
    else runs += ReaderStyleRun(start, end, style)
}

/** Laminar source order supplies the endpoint stack; one effective run replaces repeated enclosing styles. */
private fun readerStyleRuns(spans: List<ReaderSpan>): List<ReaderStyleRun> {
    val count = spans.count { readerSpanStyle(it.kind) != null }
    if (count == 0) return emptyList()
    val ends = IntArray(count)
    val effective = arrayOfNulls<SpanStyle>(count)
    val runs = ArrayList<ReaderStyleRun>()
    var depth = 0
    var cursor = 0
    for (span in spans) {
        val style = readerSpanStyle(span.kind) ?: continue
        while (depth > 0 && ends[depth - 1] <= span.startCodePoint) {
            val end = ends[depth - 1]
            appendReaderStyleRun(runs, cursor, end, effective[depth - 1])
            cursor = end
            depth--
        }
        val outer = if (depth > 0) requireNotNull(effective[depth - 1]) else null
        appendReaderStyleRun(runs, cursor, span.startCodePoint, outer)
        cursor = span.startCodePoint
        effective[depth] = if (outer == null) style else if (
            (style.fontWeight == null || style.fontWeight == outer.fontWeight) &&
            (style.fontStyle == null || style.fontStyle == outer.fontStyle) &&
            (style.fontFamily == null || style.fontFamily == outer.fontFamily) &&
            (style.textDecoration == null || style.textDecoration == outer.textDecoration)
        ) outer else outer.merge(style)
        ends[depth++] = span.endCodePoint
    }
    while (depth > 0) {
        val end = ends[depth - 1]
        appendReaderStyleRun(runs, cursor, end, effective[depth - 1])
        cursor = end
        depth--
    }
    return runs
}

private fun readerActionSpanIndices(spans: List<ReaderSpan>): IntArray {
    val indices = IntArray(spans.count { it.kind is ReaderInlineKind.Link || it.kind is ReaderInlineKind.NoteReference })
    var next = 0
    spans.forEachIndexed { index, span ->
        if (span.kind is ReaderInlineKind.Link || span.kind is ReaderInlineKind.NoteReference) indices[next++] = index
    }
    return indices
}

private fun readerDisplayOffsetAt(boundaries: IntArray, codePointOffset: Int): Int {
    var low = 0
    var high = boundaries.size
    while (low < high) {
        val middle = (low + high).ushr(1)
        if (boundaries[middle] < codePointOffset) low = middle + 1 else high = middle
    }
    return if (low < boundaries.size && boundaries[low] == codePointOffset) low
        else (low - 1).coerceIn(0, boundaries.lastIndex)
}

private val READER_EMPHASIS_STYLE = SpanStyle(fontStyle = FontStyle.Italic)
private val READER_STRONG_STYLE = SpanStyle(fontWeight = FontWeight.Bold)
private val READER_UNDERLINE_STYLE = SpanStyle(textDecoration = TextDecoration.Underline)
private val READER_STRIKE_STYLE = SpanStyle(textDecoration = TextDecoration.LineThrough)
private val READER_CODE_STYLE = SpanStyle(fontFamily = FontFamily.Monospace)
private val READER_NOTE_STYLE = SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)

private fun readerSpanStyle(kind: ReaderInlineKind): SpanStyle? = when (kind) {
    ReaderInlineKind.Emphasis -> READER_EMPHASIS_STYLE
    ReaderInlineKind.Strong -> READER_STRONG_STYLE
    ReaderInlineKind.Underline -> READER_UNDERLINE_STYLE
    ReaderInlineKind.Strike -> READER_STRIKE_STYLE
    ReaderInlineKind.Code -> READER_CODE_STYLE
    is ReaderInlineKind.Link -> READER_UNDERLINE_STYLE
    is ReaderInlineKind.NoteReference -> READER_NOTE_STYLE
    is ReaderInlineKind.Ruby -> null
}

internal data class ReaderRubyAnnotation(val startCodePoint: Int, val endCodePoint: Int, val text: String, val sourceIndex: Int)
internal data class ReaderRuby(val displayOffset: Int, val base: AnnotatedString, val annotations: List<ReaderRubyAnnotation>,
    val startCodePoint: Int, val endCodePoint: Int, val measured: MeasuredReaderRuby? = null)
internal data class MeasuredReaderRubyAnnotation(val layout: TextLayoutResult, val x: Float, val y: Float,
    val sourceIndex: Int, val startCharacterIndex: Int, val endCharacterIndex: Int)
internal data class MeasuredReaderRuby(val base: TextLayoutResult?, val annotations: List<MeasuredReaderRubyAnnotation>,
    val widthPx: Int, val heightPx: Int, val baseTopPx: Int, val fits: Boolean = true)
internal data class MeasuredReaderText(val body: TextLayoutResult, val rubies: List<MeasuredReaderRuby>)

private fun rubyAnnotationStyle(style: TextStyle): TextStyle = style.copy(
    fontSize = style.fontSize * 0.5f, lineHeight = style.fontSize * 0.6f,
    textIndent = androidx.compose.ui.text.style.TextIndent.None,
)

private fun rubyNativeLineLimit(style: TextStyle, measurer: TextMeasurer, widthPx: Int, heightPx: Int): Int {
    val line = measurer.measure("漢", style = style, maxLines = 1,
        constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)))
    return (heightPx / line.size.height.coerceAtLeast(1)).coerceAtLeast(1)
}

private fun measureReaderRuby(ruby: ReaderRuby, style: TextStyle, measurer: TextMeasurer,
    widthPx: Int, heightPx: Int): MeasuredReaderRuby {
    val baseStyle = style.copy(textIndent = androidx.compose.ui.text.style.TextIndent.None)
    val base = measurer.measure(ruby.base, style = baseStyle,
        maxLines = rubyNativeLineLimit(baseStyle, measurer, widthPx, heightPx),
        constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)))
    if (base.multiParagraph.didExceedMaxLines || base.size.height > heightPx) {
        return MeasuredReaderRuby(base, emptyList(), base.size.width, base.size.height, 0, fits = false)
    }
    val annotationStyle = rubyAnnotationStyle(style)
    val lineLimit = rubyNativeLineLimit(annotationStyle, measurer, widthPx, heightPx)
    val annotations = ArrayList<MeasuredReaderRubyAnnotation>()
    var annotationHeight = 0
    var width = base.size.width
    for (annotation in ruby.annotations) {
        val layout = measurer.measure(annotation.text, style = annotationStyle, maxLines = lineLimit,
            constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)))
        if (layout.multiParagraph.didExceedMaxLines || layout.size.height.toLong() + annotationHeight + base.size.height > heightPx) {
            return MeasuredReaderRuby(base, annotations, width, annotationHeight + base.size.height, annotationHeight, fits = false)
        }
        annotations += MeasuredReaderRubyAnnotation(layout, 0f, annotationHeight.toFloat(),
            annotation.sourceIndex, 0, annotation.text.length)
        annotationHeight += layout.size.height
        width = maxOf(width, layout.size.width)
    }
    val baseLeft = (width - base.size.width) / 2f
    val positioned = annotations.mapIndexed { index, measured ->
        val annotation = ruby.annotations[index]
        val first = ruby.base.text.offsetByCodePoints(0, annotation.startCodePoint - ruby.startCodePoint)
        val end = ruby.base.text.offsetByCodePoints(0, annotation.endCodePoint - ruby.startCodePoint)
        val center = if (base.getLineForOffset(first) == base.getLineForOffset((end - 1).coerceAtLeast(first))) {
            (base.getHorizontalPosition(first, true) + base.getHorizontalPosition(end, true)) / 2f
        } else base.size.width / 2f
        measured.copy(x = (baseLeft + center - measured.layout.size.width / 2f)
            .coerceIn(0f, (width - measured.layout.size.width).coerceAtLeast(0).toFloat()))
    }
    return MeasuredReaderRuby(base, positioned, width, annotationHeight + base.size.height, annotationHeight)
}

/** A shaping window, not an admission cap: every unconsumed character enters a later native fragment. */
private inline fun forEachRubyNativeFragment(text: AnnotatedString, style: TextStyle, measurer: TextMeasurer,
    widthPx: Int, heightPx: Int, consume: (Int, Int, TextLayoutResult) -> Unit) {
    val lineLimit = rubyNativeLineLimit(style, measurer, widthPx, heightPx)
    val boundaries = if (text.length > 2048) java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
        .apply { setText(text.text) } else null
    var start = 0
    while (start < text.length) {
        var windowEnd = minOf(start.toLong() + 2048, text.length.toLong()).toInt()
        if (windowEnd < text.length) {
            windowEnd = requireNotNull(boundaries).preceding(windowEnd + 1)
            if (windowEnd <= start) windowEnd = boundaries.following(start)
        }
        val window = text.subSequence(start, windowEnd)
        val layout = measurer.measure(window, style = style, maxLines = lineLimit,
            constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)))
        var lastLine = layout.lineCount - 1
        while (lastLine > 0 && layout.getLineBottom(lastLine) > heightPx) lastLine--
        val consumed = layout.getLineEnd(lastLine, visibleEnd = false)
            .coerceAtLeast(Character.charCount(window.text.codePointAt(0))).coerceAtMost(window.length)
        val fitted = if (consumed == window.length) layout else measurer.measure(window.subSequence(0, consumed),
            style = style, maxLines = lineLimit, constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)))
        consume(start, start + consumed, fitted)
        start += consumed
    }
}

internal fun readerRubyFragments(richText: ReaderRichText, style: TextStyle, measurer: TextMeasurer,
    widthPx: Int, heightPx: Int): List<ReaderRichText> {
    if (richText.rubies.isEmpty()) return listOf(richText)
    val nativeLine = measurer.measure("漢", style = style, maxLines = 1,
        constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)))
    val descent = kotlin.math.ceil((nativeLine.size.height - nativeLine.firstBaseline).coerceAtLeast(0f)).toInt()
    val fragmentHeight = (heightPx - descent).coerceAtLeast(1)
    val measurements = richText.rubies.map { measureReaderRuby(it, style, measurer, widthPx, fragmentHeight) }
    if (measurements.all { it.fits }) {
        return listOf(richText.withRubyMeasurements(measurements))
    }
    val fragments = ArrayList<ReaderRichText>()
    var displayCursor = 0
    richText.rubies.forEachIndexed { index, ruby ->
        if (displayCursor < ruby.displayOffset) fragments += richText.displayFragment(displayCursor, ruby.displayOffset)
        val measured = measurements[index]
        if (measured.fits) {
            fragments += richText.displayFragment(ruby.displayOffset, ruby.displayOffset + 1, measured, ruby)
        } else {
            for (annotation in ruby.annotations) {
                forEachRubyNativeFragment(AnnotatedString(annotation.text), rubyAnnotationStyle(style), measurer, widthPx, fragmentHeight) { first, end, layout ->
                    val layer = MeasuredReaderRubyAnnotation(layout, 0f, 0f, annotation.sourceIndex, first, end)
                    fragments += richText.rubyFragment(ruby, MeasuredReaderRuby(null, listOf(layer), layout.size.width, layout.size.height, 0),
                        ruby.startCodePoint, ruby.startCodePoint, annotation.startCodePoint, annotation.endCodePoint, annotationOnly = true)
                }
            }
            var sourceCursor = ruby.startCodePoint
            forEachRubyNativeFragment(ruby.base, style.copy(textIndent = androidx.compose.ui.text.style.TextIndent.None),
                measurer, widthPx, fragmentHeight) { _, _, layout ->
                val sourceEnd = sourceCursor + layout.layoutInput.text.text.codePointCount(0, layout.layoutInput.text.length)
                fragments += richText.rubyFragment(ruby, MeasuredReaderRuby(layout, emptyList(), layout.size.width, layout.size.height, 0),
                    sourceCursor, sourceEnd, sourceCursor, sourceEnd, annotationOnly = false)
                sourceCursor = sourceEnd
            }
        }
        displayCursor = ruby.displayOffset + 1
    }
    if (displayCursor < richText.displayText.length) fragments += richText.displayFragment(displayCursor, richText.displayText.length)
    return fragments
}

private fun readerRubyBoundsFitLines(body: TextLayoutResult, rubies: List<ReaderRuby>, start: Int): Boolean {
    for (index in rubies.indices) {
        val bounds = body.placeholderRects[index] ?: continue
        val line = body.getLineForOffset(rubies[index].displayOffset - start)
        if (bounds.top < body.getLineTop(line) || bounds.bottom > body.getLineBottom(line)) return false
    }
    return true
}

/** Native PlaceholderSpan ceilings pixels; preserve the measured integer instead of rounding it up again. */
private fun Density.readerPlaceholderDimension(sizePx: Int): TextUnit {
    var dimension = sizePx.toSp()
    while (dimension.toPx() > sizePx) dimension = java.lang.Math.nextDown(dimension.value).sp
    return dimension
}

/** Pagination and drawing retain exactly this native measurement, including ruby geometry. */
internal fun measureReaderText(
    richText: ReaderRichText,
    start: Int,
    end: Int,
    style: TextStyle,
    measurer: TextMeasurer,
    density: Density,
    widthPx: Int,
): MeasuredReaderText {
    // A consumed hard break belongs to the next page boundary, not an extra empty line
    // in this fragment. Keep the original range for locators and accessible text.
    val textEnd = if (end < richText.displayText.length && end > start) {
        when (richText.displayText[end - 1]) {
            '\n' -> end - if (end - start > 1 && richText.displayText[end - 2] == '\r') 2 else 1
            '\r', '\u2028', '\u2029' -> end - 1
            else -> end
        }
    } else end
    val rubies = richText.rubies.filter { it.displayOffset in start until end }
    val measuredRubies = rubies.map { ruby -> ruby.measured ?: measureReaderRuby(ruby, style, measurer, widthPx, Int.MAX_VALUE) }
    val placeholders = rubies.mapIndexed { index, ruby ->
        val measured = measuredRubies[index]
        AnnotatedString.Range(Placeholder(
            width = density.readerPlaceholderDimension(measured.widthPx),
            height = density.readerPlaceholderDimension(measured.heightPx),
            placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
        ), ruby.displayOffset - start, ruby.displayOffset - start + 1)
    }
    val text = richText.displayText.subSequence(start, textEnd)
    val constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1))
    var body = measurer.measure(text, style = style, placeholders = placeholders, constraints = constraints)
    if (!readerRubyBoundsFitLines(body, rubies, start)) {
        // Android caches explicit line-height metrics from the first line, before a later tall ruby.
        // Use actual natural native metrics, not clipped rectangles or a guessed pixel allowance.
        val natural = measurer.measure(text, style = style.copy(lineHeight = TextUnit.Unspecified, lineHeightStyle = null),
            placeholders = placeholders, constraints = constraints)
        var rowHeight = when {
            style.lineHeight.isSp -> with(density) { style.lineHeight.toPx() }
            style.lineHeight.isEm -> with(density) { style.fontSize.toPx() } * style.lineHeight.value
            else -> 0f
        }
        var aboveBaseline = 0f
        var belowBaseline = 0f
        for (line in 0 until natural.lineCount) {
            val baseline = natural.getLineBaseline(line)
            aboveBaseline = maxOf(aboveBaseline, baseline - natural.getLineTop(line))
            belowBaseline = maxOf(belowBaseline, natural.getLineBottom(line) - baseline)
        }
        rowHeight = maxOf(rowHeight, kotlin.math.ceil(aboveBaseline + belowBaseline))
        val nativeStyle = style.copy(lineHeight = with(density) { rowHeight.toSp() },
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Bottom, LineHeightStyle.Trim.None,
                LineHeightStyle.Mode.Minimum))
        body = measurer.measure(text, style = nativeStyle, placeholders = placeholders, constraints = constraints)
    }
    return MeasuredReaderText(body, measuredRubies)
}

internal enum class ReaderLayoutItemKind {
    CONTENT,
    TABLE_ROW,
    NOTE_HEADER,
    POST_HEADER,
}

internal data class ReaderLayoutItem(
    val key: String,
    val rootBlockIndex: Int,
    val block: ReaderBlock,
    val rootCodePointStart: Int,
    val kind: ReaderLayoutItemKind,
    val richText: ReaderRichText?,
    val listMarker: String? = null,
    val listDepth: Int = 0,
    val tableRowIndex: Int? = null,
    val tableColumnCount: Int = 0,
    val tableCells: List<ReaderRichText>? = null,
    val continuation: Boolean = false,
)

internal fun readerListMarkerWidthPx(item: ReaderLayoutItem, style: TextStyle, measurer: TextMeasurer, density: Density): Int {
    val marker = item.listMarker ?: return 0
    val measured = measurer.measure(marker, style = style.copy(textIndent = androidx.compose.ui.text.style.TextIndent.None))
    return maxOf(with(density) { 28.dp.roundToPx() }, measured.size.width) +
        with(density) { ((item.listDepth - 1).coerceAtLeast(0) * 18).dp.roundToPx() }
}

internal fun readerTableColumnWidthPx(item: ReaderLayoutItem, widthPx: Int, style: TextStyle, density: Density): Int {
    val columns = item.tableColumnCount
    val padding = with(density) { 4.dp.roundToPx() }
    val glyphWidth = with(density) { kotlin.math.ceil(style.fontSize.toPx() * 2).toInt() }
    return maxOf(widthPx / columns, glyphWidth + padding * 2)
}

internal fun prepareReaderRubyItems(items: List<ReaderLayoutItem>, bodyStyle: TextStyle, headingStyle: TextStyle,
    measurer: TextMeasurer, density: Density, widthPx: Int, heightPx: Int): List<ReaderLayoutItem> = buildList {
    for (item in items) {
        if (item.kind == ReaderLayoutItemKind.TABLE_ROW && item.tableCells.orEmpty().any { it.rubies.isNotEmpty() }) {
            val row = (item.block as ReaderBlock.Table).rows[requireNotNull(item.tableRowIndex)]
            val padding = with(density) { 4.dp.roundToPx() }
            val width = (readerTableColumnWidthPx(item, widthPx, bodyStyle, density) - padding * 2).coerceAtLeast(1)
            val cells = item.tableCells.orEmpty().mapIndexed { index, cell ->
                readerRubyFragments(cell, bodyStyle.copy(fontWeight = if (row.cells[index].header) FontWeight.SemiBold else bodyStyle.fontWeight),
                    measurer, width, (heightPx - padding * 2).coerceAtLeast(1))
            }
            repeat(cells.maxOf { it.size }) { part ->
                add(item.copy(key = if (part == 0) item.key else "${item.key}:ruby:$part", continuation = part > 0,
                    tableCells = cells.mapIndexed { index, fragments -> fragments.getOrNull(part)
                        ?: item.tableCells!![index].let { it.displayFragment(it.displayText.length, it.displayText.length) } }))
            }
        } else if (item.richText?.rubies?.isNotEmpty() == true) {
            val style = when (val block = item.block) {
                is ReaderBlock.Heading -> headingStyle
                is ReaderBlock.Paragraph -> if (block.preformatted) bodyStyle.copy(fontFamily = FontFamily.Monospace) else bodyStyle
                else -> bodyStyle
            }
            val quotePadding = if (item.block is ReaderBlock.Quote) with(density) { 16.dp.roundToPx() } else 0
            val width = (widthPx - quotePadding - readerListMarkerWidthPx(item, style, measurer, density)).coerceAtLeast(1)
            readerRubyFragments(item.richText, style, measurer, width, (heightPx - quotePadding).coerceAtLeast(1)).forEachIndexed { part, richText ->
                add(item.copy(key = if (part == 0) item.key else "${item.key}:ruby:$part", richText = richText,
                    continuation = part > 0))
            }
        } else add(item)
    }
}

internal fun readerLayoutItems(blocks: List<ReaderBlock>): List<ReaderLayoutItem> =
    ReaderLayoutItemBuilder().build(blocks)

private class ReaderLayoutItemBuilder {
    private val items = ArrayList<ReaderLayoutItem>()

    fun build(blocks: List<ReaderBlock>): List<ReaderLayoutItem> {
        blocks.forEachIndexed { rootIndex, block -> addBlock(block, rootIndex, 0, 0) }
        return items
    }

    private fun addBlock(
        block: ReaderBlock,
        rootBlockIndex: Int,
        rootCodePointStart: Int,
        listDepth: Int,
    ) {
        when (block) {
            is ReaderBlock.Listing -> {
                var contentOffset = rootCodePointStart
                block.items.forEachIndexed { itemIndex, item ->
                    if (itemIndex > 0) contentOffset++
                    val itemMarker = if (block.ordered) "${block.start + itemIndex}." else "•"
                    var markerApplied = false
                    item.blocks.forEachIndexed { childIndex, child ->
                        if (childIndex > 0) contentOffset++
                        val firstUnit = items.size
                        addBlock(child, rootBlockIndex, contentOffset, listDepth + 1)
                        if (!markerApplied && items.size > firstUnit) {
                            val first = items[firstUnit]
                            val combinedMarker = listOfNotNull(first.listMarker, itemMarker).joinToString(" ")
                            items[firstUnit] = first.copy(
                                listMarker = combinedMarker,
                                listDepth = maxOf(first.listDepth, listDepth),
                            )
                            markerApplied = true
                        }
                        val childText = child.canonicalAnchorText()
                        contentOffset += childText.codePointCount(0, childText.length)
                    }
                }
            }
            is ReaderBlock.Note -> {
                val header = "注释 · ${block.label ?: block.noteId}"
                items += ReaderLayoutItem(
                    key = "${block.blockId}:note-header",
                    rootBlockIndex = rootBlockIndex,
                    block = block,
                    rootCodePointStart = rootCodePointStart,
                    kind = ReaderLayoutItemKind.NOTE_HEADER,
                    richText = ReaderRichText.create(header, emptyList()),
                )
                addNestedBlocks(block.blocks, rootBlockIndex, rootCodePointStart, listDepth)
            }
            is ReaderBlock.Post -> {
                val metadata = buildList {
                    add("#${block.postId}")
                    block.floor?.let { add("$it 楼") }
                    (block.authorName ?: block.authorId)?.let(::add)
                    block.createdAt?.let(::add)
                    block.replyToPostId?.let { add("回复 $it") }
                }.joinToString(" · ")
                items += ReaderLayoutItem(
                    key = "${block.blockId}:post-header",
                    rootBlockIndex = rootBlockIndex,
                    block = block,
                    rootCodePointStart = rootCodePointStart,
                    kind = ReaderLayoutItemKind.POST_HEADER,
                    richText = ReaderRichText.create(metadata, emptyList()),
                )
                addNestedBlocks(block.blocks, rootBlockIndex, rootCodePointStart, listDepth)
            }
            is ReaderBlock.Table -> {
                val columnCount = block.rows.maxOf { it.cells.size }
                var contentOffset = rootCodePointStart
                block.rows.forEachIndexed { rowIndex, row ->
                    if (rowIndex > 0) contentOffset++
                    items += ReaderLayoutItem(
                        key = "${block.blockId}:row:$rowIndex",
                        rootBlockIndex = rootBlockIndex,
                        block = block,
                        rootCodePointStart = contentOffset,
                        kind = ReaderLayoutItemKind.TABLE_ROW,
                        richText = null,
                        tableRowIndex = rowIndex,
                        tableCells = row.cells.map { ReaderRichText.create(it.text, it.spans) },
                        tableColumnCount = columnCount,
                    )
                    contentOffset += row.cells.sumOf { cell -> cell.text.codePointCount(0, cell.text.length) } +
                        (row.cells.size - 1).coerceAtLeast(0)
                }
            }
            else -> items += ReaderLayoutItem(
                key = block.blockId,
                rootBlockIndex = rootBlockIndex,
                block = block,
                rootCodePointStart = rootCodePointStart,
                kind = ReaderLayoutItemKind.CONTENT,
                richText = block.readerRichText(),
            )
        }
    }

    private fun addNestedBlocks(
        children: List<ReaderBlock>,
        rootBlockIndex: Int,
        rootCodePointStart: Int,
        listDepth: Int,
    ) {
        var contentOffset = rootCodePointStart
        children.forEachIndexed { index, child ->
            if (index > 0) contentOffset++
            addBlock(child, rootBlockIndex, contentOffset, listDepth)
            val childText = child.canonicalAnchorText()
            contentOffset += childText.codePointCount(0, childText.length)
        }
    }
}

private fun ReaderBlock.readerRichText(): ReaderRichText? = when (this) {
    is ReaderBlock.Paragraph -> ReaderRichText.create(text, spans)
    is ReaderBlock.Heading -> ReaderRichText.create(text, spans)
    is ReaderBlock.Quote -> ReaderRichText.create(canonicalAnchorText(), spans)
    is ReaderBlock.Unsupported -> ReaderRichText.create(visibleText, emptyList())
    else -> null
}
