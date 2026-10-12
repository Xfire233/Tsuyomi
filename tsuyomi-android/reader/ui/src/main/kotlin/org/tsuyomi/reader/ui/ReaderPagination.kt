/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderReadingDirection
import org.tsuyomi.shared.sourcecontract.ReaderDocumentPresentation

@Immutable
internal data class ReaderPageSegment(
    val itemIndex: Int,
    val startCharacterIndex: Int,
    val endCharacterIndex: Int,
    val startCodePointOffset: Int,
    val measuredHeightPx: Int,
    val measuredText: MeasuredReaderText? = null,
    val tableCells: List<ReaderTableSegmentCell> = emptyList(),
    val positionIsExact: Boolean = true,
)

internal data class ReaderTableSegmentCell(
    val richText: ReaderRichText,
    val start: Int,
    val end: Int,
    val measurement: MeasuredReaderText,
)

@Immutable
internal data class ReaderPage(
    val segments: List<ReaderPageSegment>,
    val startBlockIndex: Int,
    val startCodePointOffset: Int,
    val positionIsExact: Boolean = true,
)

@Immutable
internal class ReaderPageLayout(
    val items: List<ReaderLayoutItem>,
    val pages: List<ReaderPage>,
    val artworkOnly: Boolean = false,
    val rightToLeft: Boolean = false,
    val pairArtwork: Boolean = false,
) {
    private val tableSegmentsByRoot = buildMap<Int, MutableList<Pair<Int, ReaderPageSegment>>> {
        pages.forEachIndexed { pageIndex, page ->
            page.segments.forEach { segment ->
                if (segment.tableCells.isNotEmpty()) {
                    val root = items[segment.itemIndex].rootBlockIndex
                    getOrPut(root) { ArrayList() }.add(pageIndex to segment)
                }
            }
        }
    }

    fun pageIndexFor(blockIndex: Int, codePointOffset: Int): Int {
        if (pages.isEmpty()) return 0
        // Columns are simultaneous on screen but consecutive in canonical text. A locator
        // in column two must resolve to its actual row slice, not the last column-one page.
        tableSegmentsByRoot[blockIndex]?.forEach { (pageIndex, segment) ->
            var cellOffset = items[segment.itemIndex].rootCodePointStart
            segment.tableCells.forEach { cell ->
                val start = cellOffset + cell.richText.codePointOffsetAt(cell.start)
                val end = cellOffset + cell.richText.codePointOffsetAt(cell.end)
                if (codePointOffset >= start && (codePointOffset < end ||
                    codePointOffset == end && cell.end == cell.richText.displayText.length)
                ) return pageIndex
                cellOffset += cell.richText.sourceCodePointLength + 1
            }
        }
        var low = 0
        var high = pages.lastIndex
        var match = 0
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val page = pages[middle]
            val startsBeforeOrAt = page.startBlockIndex < blockIndex ||
                (page.startBlockIndex == blockIndex && page.startCodePointOffset <= codePointOffset)
            if (startsBeforeOrAt) {
                match = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        while (match > 0 && pages[match].startBlockIndex == blockIndex &&
            pages[match].startCodePointOffset == codePointOffset &&
            pages[match - 1].startBlockIndex == blockIndex &&
            pages[match - 1].startCodePointOffset == codePointOffset
        ) {
            match--
        }
        return match
    }

    companion object {
        val Empty = ReaderPageLayout(emptyList(), emptyList())
    }
}

@Composable
internal fun rememberReaderLayoutItems(document: ReaderDocument): List<ReaderLayoutItem> =
    remember(document) { readerLayoutItems(document.blocks) }

@Composable
internal fun rememberReaderPageLayout(
    document: ReaderDocument,
    items: List<ReaderLayoutItem>,
    imageSizesPx: Map<String, IntSize>,
    settings: ReaderSettingsUiState,
    viewportSize: IntSize,
    dual: Boolean,
    enabled: Boolean,
): ReaderPageLayout {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val bodyStyle = readerBodyStyle(settings)
    val headingStyle = readerHeadingStyle(settings)
    val artworkOnly = remember(document, items) {
        document.identity.book is BookIdentity.Local &&
            items.isNotEmpty() && items.all { it.block is ReaderBlock.Image }
    }
    val pairArtwork = document.presentation == ReaderDocumentPresentation.FIXED_IMAGE
    val horizontalMarginPx = if (artworkOnly) 0 else with(density) { settings.horizontalMargin.dp.toPx().roundToInt() }
    val verticalMarginPx = if (artworkOnly) 0 else with(density) { settings.verticalMargin.dp.toPx().roundToInt() }
    val paragraphSpacingPx = with(density) { settings.paragraphSpacing.dp.toPx().roundToInt() }
    val dividerHeightPx = with(density) { 1.dp.roundToPx().coerceAtLeast(1) }
    val cellPaddingPx = with(density) { 4.dp.roundToPx() }
    val availableWidth = (viewportSize.width - horizontalMarginPx * 2).coerceAtLeast(0)
    val columnWidth = if (dual && (!artworkOnly || pairArtwork && items.size > 1)) {
        ((availableWidth - horizontalMarginPx).coerceAtLeast(0) / 2)
    } else {
        availableWidth
    }
    val pageHeight = (viewportSize.height - verticalMarginPx * 2).coerceAtLeast(0)
    // Artwork always occupies the viewport; decoding changes pixels, never its page geometry.
    val paginationImageSizes = if (artworkOnly) emptyMap() else imageSizesPx
    return remember(
        enabled,
        document,
        items,
        paginationImageSizes,
        bodyStyle,
        headingStyle,
        columnWidth,
        pageHeight,
        paragraphSpacingPx,
        dividerHeightPx,
        cellPaddingPx,
        density.density,
        density.fontScale,
    ) {
        if (columnWidth == 0 || pageHeight == 0) return@remember ReaderPageLayout.Empty
        val preparedItems = if (items.any { it.richText?.rubies?.isNotEmpty() == true ||
                it.tableCells.orEmpty().any { cell -> cell.rubies.isNotEmpty() } }) {
            prepareReaderRubyItems(items, bodyStyle, headingStyle, textMeasurer, density, columnWidth, pageHeight)
        } else items
        if (!enabled) return@remember ReaderPageLayout(preparedItems, emptyList())
        if (artworkOnly) {
            ReaderPageLayout(items, items.mapIndexed { index, item ->
                ReaderPage(
                    segments = listOf(ReaderPageSegment(index, 0, 0, item.rootCodePointStart, pageHeight)),
                    startBlockIndex = item.rootBlockIndex,
                    startCodePointOffset = item.rootCodePointStart,
                )
            }, artworkOnly = true, rightToLeft = document.readingDirection == ReaderReadingDirection.RTL,
                pairArtwork = pairArtwork)
        } else {
            paginateReaderDocument(
                items = preparedItems,
                imageSizesPx = paginationImageSizes,
                textMeasurer = textMeasurer,
                density = density,
                bodyStyle = bodyStyle,
                headingStyle = headingStyle,
                pageWidthPx = columnWidth,
                pageHeightPx = pageHeight,
                paragraphSpacingPx = paragraphSpacingPx,
                dividerHeightPx = dividerHeightPx,
                cellPaddingPx = cellPaddingPx,
                rightToLeft = document.readingDirection == ReaderReadingDirection.RTL,
            )
        }
    }
}

@Composable
internal fun readerBodyStyle(settings: ReaderSettingsUiState): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontFamily = settings.fontFamily.fontFamily(),
    fontWeight = FontWeight(settings.fontWeight),
    fontSize = settings.fontSize.sp,
    letterSpacing = settings.letterSpacing.sp,
    lineHeight = (settings.fontSize * settings.lineHeight).sp,
    textAlign = settings.textAlignment.textAlign(),
    textIndent = TextIndent(firstLine = settings.firstLineIndent.em),
)

@Composable
internal fun readerHeadingStyle(settings: ReaderSettingsUiState): TextStyle = MaterialTheme.typography.titleLarge.copy(
    fontFamily = settings.fontFamily.fontFamily(),
    fontWeight = FontWeight(settings.fontWeight),
    fontSize = (settings.fontSize * 1.12f).sp,
    letterSpacing = settings.letterSpacing.sp,
    lineHeight = (settings.fontSize * settings.lineHeight * 1.12f).sp,
    textAlign = settings.textAlignment.textAlign(),
)

private fun String.fontFamily(): FontFamily = when (this) {
    "sans" -> FontFamily.SansSerif
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    else -> FontFamily.Default
}

private fun String.textAlign(): TextAlign = when (this) {
    "justify" -> TextAlign.Justify
    "center" -> TextAlign.Center
    "end" -> TextAlign.End
    else -> TextAlign.Start
}


private fun paginateReaderDocument(
    items: List<ReaderLayoutItem>,
    imageSizesPx: Map<String, IntSize>,
    textMeasurer: TextMeasurer,
    density: Density,
    bodyStyle: TextStyle,
    headingStyle: TextStyle,
    pageWidthPx: Int,
    pageHeightPx: Int,
    paragraphSpacingPx: Int,
    dividerHeightPx: Int,
    cellPaddingPx: Int,
    rightToLeft: Boolean,
): ReaderPageLayout {
    val pages = ArrayList<ReaderPage>()
    var itemIndex = 0
    var characterIndex = 0
    while (itemIndex < items.size) {
        val segments = ArrayList<ReaderPageSegment>()
        var remainingHeight = pageHeightPx
        while (itemIndex < items.size) {
            val item = items[itemIndex]
            val spacing = if (segments.isEmpty() || item.continuation) 0 else paragraphSpacingPx
            val availableHeight = remainingHeight - spacing
            val forceFirst = segments.isEmpty()
            val segment = when (item.kind) {
                ReaderLayoutItemKind.TABLE_ROW -> measureTableRowSegment(
                    item = item,
                    itemIndex = itemIndex,
                    startCharacterIndex = characterIndex,
                    textMeasurer = textMeasurer,
                    density = density,
                    style = bodyStyle,
                    pageWidthPx = pageWidthPx,
                    pageHeightPx = pageHeightPx,
                    availableHeightPx = availableHeight,
                    cellPaddingPx = cellPaddingPx,
                    force = forceFirst,
                )
                ReaderLayoutItemKind.CONTENT -> when (val block = item.block) {
                    is ReaderBlock.Image -> measureImageSegment(
                        item = item,
                        itemIndex = itemIndex,
                        imageSize = imageSizesPx[block.blockId],
                        pageWidthPx = pageWidthPx,
                        pageHeightPx = pageHeightPx,
                        availableHeightPx = availableHeight,
                        force = forceFirst,
                    )
                    is ReaderBlock.Divider -> measureFixedSegment(
                        item = item,
                        itemIndex = itemIndex,
                        heightPx = dividerHeightPx,
                        availableHeightPx = availableHeight,
                        force = forceFirst,
                    )
                    else -> measureTextSegment(
                        item = item,
                        itemIndex = itemIndex,
                        startCharacterIndex = characterIndex,
                        style = when (block) {
                            is ReaderBlock.Heading -> headingStyle
                            is ReaderBlock.Paragraph -> if (block.preformatted) bodyStyle.copy(fontFamily = FontFamily.Monospace) else bodyStyle
                            else -> bodyStyle
                        },
                        textMeasurer = textMeasurer,
                        density = density,
                        pageWidthPx = pageWidthPx,
                        availableHeightPx = availableHeight,
                        forceOneLine = forceFirst,
                    )
                }
                ReaderLayoutItemKind.NOTE_HEADER,
                ReaderLayoutItemKind.POST_HEADER,
                -> measureTextSegment(
                    item = item,
                    itemIndex = itemIndex,
                    startCharacterIndex = characterIndex,
                    style = bodyStyle.copy(fontWeight = FontWeight.SemiBold),
                    textMeasurer = textMeasurer,
                    density = density,
                    pageWidthPx = pageWidthPx,
                    availableHeightPx = availableHeight,
                    forceOneLine = forceFirst,
                )
            } ?: break
            val next = items.getOrNull(itemIndex + 1)
            if (!forceFirst && item.kind != ReaderLayoutItemKind.CONTENT && item.richText != null &&
                segment.endCharacterIndex >= item.richText.displayText.length &&
                next != null && next.rootBlockIndex == item.rootBlockIndex && next.richText != null
            ) {
                val followingStyle = if (next.block is ReaderBlock.Heading) headingStyle else bodyStyle
                val following = measureTextSegment(next, itemIndex + 1, 0, followingStyle,
                    textMeasurer, density, pageWidthPx, availableHeightPx = 0, forceOneLine = true)
                if (following != null && segment.measuredHeightPx + paragraphSpacingPx + following.measuredHeightPx > availableHeight) break
            }

            segments += segment
            remainingHeight -= spacing + segment.measuredHeightPx
            val displayTextLength = item.richText?.displayText?.length ?: 0
            val itemComplete = when (item.kind) {
                ReaderLayoutItemKind.CONTENT -> when (item.block) {
                    is ReaderBlock.Paragraph, is ReaderBlock.Heading, is ReaderBlock.Quote,
                    is ReaderBlock.Unsupported -> segment.endCharacterIndex >= displayTextLength
                    else -> true
                }
                ReaderLayoutItemKind.TABLE_ROW -> segment.endCharacterIndex == 0
                ReaderLayoutItemKind.NOTE_HEADER,
                ReaderLayoutItemKind.POST_HEADER,
                -> segment.endCharacterIndex >= displayTextLength
            }
            if (itemComplete) {
                itemIndex += 1
                characterIndex = 0
            } else {
                characterIndex = segment.endCharacterIndex
                break
            }
        }
        check(segments.isNotEmpty()) { "Reader pagination made no progress" }
        val first = segments.first()
        val firstItem = items[first.itemIndex]
        pages += ReaderPage(
            segments = segments,
            startBlockIndex = firstItem.rootBlockIndex,
            startCodePointOffset = first.startCodePointOffset,
            positionIsExact = first.positionIsExact,
        )
    }
    return ReaderPageLayout(items, pages, rightToLeft = rightToLeft)
}

private fun measureTextSegment(
    item: ReaderLayoutItem,
    itemIndex: Int,
    startCharacterIndex: Int,
    style: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    pageWidthPx: Int,
    availableHeightPx: Int,
    forceOneLine: Boolean,
): ReaderPageSegment? {
    val richText = item.richText ?: return null
    val displayText = richText.displayText
    if (availableHeightPx <= 0 && !forceOneLine) return null
    val quotePadding = if (item.block is ReaderBlock.Quote) with(density) { 16.dp.roundToPx() } else 0
    val markerWidth = readerListMarkerWidthPx(item, style, textMeasurer, density)
    val width = (pageWidthPx - quotePadding - markerWidth).coerceAtLeast(1)
    val segmentStyle = if (startCharacterIndex == 0 && !item.continuation) style else style.copy(textIndent = TextIndent.None)
    val measured = measureReaderText(richText, startCharacterIndex, displayText.length,
        segmentStyle, textMeasurer, density, width)
    val result = measured.body
    var lastFittingLine = -1
    for (line in 0 until result.lineCount) {
        if (ceil(result.getLineBottom(line)).toInt() <= availableHeightPx - quotePadding) {
            lastFittingLine = line
        } else {
            break
        }
    }
    if (lastFittingLine < 0) {
        if (!forceOneLine || result.lineCount == 0) return null
        lastFittingLine = 0
    }
    val minimumEnd = if (startCharacterIndex < displayText.length) {
        Character.charCount(displayText.text.codePointAt(startCharacterIndex))
    } else 0
    val relativeEnd = result.getLineEnd(lastFittingLine, visibleEnd = false).coerceAtLeast(minimumEnd)
    val endCharacterIndex = (startCharacterIndex + relativeEnd).coerceAtMost(displayText.length)
    val sourceCodePointOffset = if (item.kind == ReaderLayoutItemKind.CONTENT) {
        richText.codePointOffsetAt(startCharacterIndex)
    } else {
        0
    }
    val fitted = measureReaderText(richText, startCharacterIndex, endCharacterIndex,
        segmentStyle, textMeasurer, density, width)
    return ReaderPageSegment(
        itemIndex = itemIndex,
        startCharacterIndex = startCharacterIndex,
        endCharacterIndex = endCharacterIndex,
        startCodePointOffset = item.rootCodePointStart + sourceCodePointOffset,
        measuredHeightPx = fitted.body.size.height + quotePadding,
        measuredText = fitted,
        positionIsExact = richText.hasExactPositionAt(startCharacterIndex),
    )
}

private fun measureTableRowSegment(
    item: ReaderLayoutItem,
    itemIndex: Int,
    startCharacterIndex: Int,
    textMeasurer: TextMeasurer,
    density: Density,
    style: TextStyle,
    pageWidthPx: Int,
    pageHeightPx: Int,
    availableHeightPx: Int,
    cellPaddingPx: Int,
    force: Boolean,
): ReaderPageSegment? {
    if (availableHeightPx <= 0 && !force) return null
    val cells = item.tableCells.orEmpty()
    if (cells.isEmpty()) return null
    val cellWidth = readerTableColumnWidthPx(item, pageWidthPx, style, density) - cellPaddingPx * 2
    val row = (item.block as ReaderBlock.Table).rows[item.tableRowIndex ?: return null]
    val layouts = cells.mapIndexed { index, richText ->
        measureReaderText(richText, 0, richText.displayText.length,
            style.copy(fontWeight = if (row.cells[index].header) FontWeight.SemiBold else style.fontWeight),
            textMeasurer, density, cellWidth)
    }
    val totalLines = layouts.maxOf { it.body.lineCount }
    val startLine = startCharacterIndex
    val budget = availableHeightPx - cellPaddingPx * 2
    var endLine = startLine
    var height = 0
    while (endLine < totalLines) {
        val nextHeight = layouts.maxOf { measured ->
            val body = measured.body
            if (startLine >= body.lineCount) 0 else ceil(
                body.getLineBottom(minOf(endLine, body.lineCount - 1)) - body.getLineTop(startLine),
            ).toInt()
        }
        if (nextHeight > budget && (!force || endLine > startLine)) break
        height = nextHeight
        endLine++
    }
    if (endLine == startLine) return null
    val visibleCells = cells.mapIndexed { index, richText ->
        val body = layouts[index].body
        val start = if (startLine < body.lineCount) body.getLineStart(startLine) else richText.displayText.length
        val end = if (endLine < body.lineCount) body.getLineStart(endLine) else richText.displayText.length
        val cellStyle = style.copy(fontWeight = if (row.cells[index].header) FontWeight.SemiBold else style.fontWeight,
            textIndent = if (startLine == 0) style.textIndent else TextIndent.None)
        ReaderTableSegmentCell(richText, start, end,
            measureReaderText(richText, start, end, cellStyle, textMeasurer, density, cellWidth))
    }
    var cellOffset = item.rootCodePointStart
    var firstVisibleOffset: Int? = null
    var positionIsExact = true
    visibleCells.forEach { cell ->
        if (firstVisibleOffset == null && cell.end > cell.start) {
            firstVisibleOffset = cellOffset + cell.richText.codePointOffsetAt(cell.start)
            positionIsExact = cell.richText.hasExactPositionAt(cell.start)
        }
        cellOffset += cell.richText.sourceCodePointLength + 1
    }
    return ReaderPageSegment(
        itemIndex = itemIndex,
        startCharacterIndex = startLine,
        endCharacterIndex = if (endLine >= totalLines) 0 else endLine,
        startCodePointOffset = firstVisibleOffset ?: item.rootCodePointStart,
        measuredHeightPx = height + cellPaddingPx * 2,
        tableCells = visibleCells,
        positionIsExact = positionIsExact,
    )
}

private fun measureFixedSegment(
    item: ReaderLayoutItem,
    itemIndex: Int,
    heightPx: Int,
    availableHeightPx: Int,
    force: Boolean,
): ReaderPageSegment? {
    if (heightPx > availableHeightPx && !force) return null
    return ReaderPageSegment(
        itemIndex = itemIndex,
        startCharacterIndex = 0,
        endCharacterIndex = 0,
        startCodePointOffset = item.rootCodePointStart,
        measuredHeightPx = heightPx,
    )
}

private fun measureImageSegment(
    item: ReaderLayoutItem,
    itemIndex: Int,
    imageSize: IntSize?,
    pageWidthPx: Int,
    pageHeightPx: Int,
    availableHeightPx: Int,
    force: Boolean,
): ReaderPageSegment? {
    val block = item.block as ReaderBlock.Image
    val sourceWidth = imageSize?.width?.takeIf { it > 0 } ?: block.width
    val sourceHeight = imageSize?.height?.takeIf { it > 0 } ?: block.height
    val aspectRatio = block.aspectRatio
    val measuredHeight = when {
        sourceWidth != null && sourceHeight != null ->
            (pageWidthPx.toLong() * sourceHeight / sourceWidth).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        aspectRatio != null ->
            (pageWidthPx.toDouble() / aspectRatio).coerceAtMost(pageHeightPx.toDouble()).toInt()
        else -> pageHeightPx / 3
    }.coerceIn(1, pageHeightPx)
    if (measuredHeight > availableHeightPx && !force) return null
    return ReaderPageSegment(
        itemIndex = itemIndex,
        startCharacterIndex = 0,
        endCharacterIndex = 0,
        startCodePointOffset = item.rootCodePointStart,
        measuredHeightPx = measuredHeight.coerceAtMost(pageHeightPx),
    )
}
