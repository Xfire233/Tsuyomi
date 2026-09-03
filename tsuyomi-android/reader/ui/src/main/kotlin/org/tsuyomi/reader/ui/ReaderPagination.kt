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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument

@Immutable
internal data class ReaderPageSegment(
    val blockIndex: Int,
    val startCharacterIndex: Int,
    val endCharacterIndex: Int,
    val measuredHeightPx: Int,
)

@Immutable
internal data class ReaderPage(
    val segments: List<ReaderPageSegment>,
    val startBlockIndex: Int,
    val startCodePointOffset: Int,
)

@Immutable
internal class ReaderPageLayout(val pages: List<ReaderPage>) {
    fun pageIndexFor(blockIndex: Int, codePointOffset: Int): Int {
        if (pages.isEmpty()) return 0
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
        return match
    }

    companion object {
        val Empty = ReaderPageLayout(emptyList())
    }
}

@Composable
internal fun rememberReaderPageLayout(
    document: ReaderDocument,
    settings: ReaderSettingsUiState,
    viewportSize: IntSize,
    dual: Boolean,
): ReaderPageLayout {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val bodyStyle = readerBodyStyle(settings)
    val headingStyle = readerHeadingStyle(settings)
    val marginPx = with(density) { settings.horizontalMargin.dp.toPx().roundToInt() }
    val paragraphSpacingPx = with(density) { settings.paragraphSpacing.dp.toPx().roundToInt() }
    val availableWidth = (viewportSize.width - marginPx * 2).coerceAtLeast(0)
    val columnWidth = if (dual) {
        ((availableWidth - marginPx).coerceAtLeast(0) / 2)
    } else {
        availableWidth
    }
    val pageHeight = (viewportSize.height - marginPx * 2).coerceAtLeast(0)
    return remember(
        document,
        bodyStyle,
        headingStyle,
        columnWidth,
        pageHeight,
        paragraphSpacingPx,
        density.density,
        density.fontScale,
    ) {
        if (columnWidth == 0 || pageHeight == 0) {
            ReaderPageLayout.Empty
        } else {
            paginateReaderDocument(
                document = document,
                textMeasurer = textMeasurer,
                bodyStyle = bodyStyle,
                headingStyle = headingStyle,
                pageWidthPx = columnWidth,
                pageHeightPx = pageHeight,
                paragraphSpacingPx = paragraphSpacingPx,
            )
        }
    }
}

@Composable
internal fun readerBodyStyle(settings: ReaderSettingsUiState): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontSize = settings.fontSize.sp,
    lineHeight = (settings.fontSize * settings.lineHeight).sp,
)

@Composable
internal fun readerHeadingStyle(settings: ReaderSettingsUiState): TextStyle = MaterialTheme.typography.titleLarge.copy(
    fontSize = (settings.fontSize * 1.12f).sp,
    lineHeight = (settings.fontSize * settings.lineHeight * 1.12f).sp,
    fontWeight = FontWeight.Medium,
)

private fun paginateReaderDocument(
    document: ReaderDocument,
    textMeasurer: TextMeasurer,
    bodyStyle: TextStyle,
    headingStyle: TextStyle,
    pageWidthPx: Int,
    pageHeightPx: Int,
    paragraphSpacingPx: Int,
): ReaderPageLayout {
    val pages = ArrayList<ReaderPage>()
    var blockIndex = 0
    var characterIndex = 0
    while (blockIndex < document.blocks.size) {
        val segments = ArrayList<ReaderPageSegment>()
        var remainingHeight = pageHeightPx
        while (blockIndex < document.blocks.size) {
            val block = document.blocks[blockIndex]
            val spacing = if (segments.isEmpty()) 0 else paragraphSpacingPx
            val segment = when (block) {
                is ReaderBlock.Paragraph -> measureTextSegment(
                    text = block.text,
                    startCharacterIndex = characterIndex,
                    style = bodyStyle,
                    textMeasurer = textMeasurer,
                    pageWidthPx = pageWidthPx,
                    availableHeightPx = remainingHeight - spacing,
                    forceOneLine = segments.isEmpty(),
                    blockIndex = blockIndex,
                )
                is ReaderBlock.Heading -> measureTextSegment(
                    text = block.text,
                    startCharacterIndex = characterIndex,
                    style = headingStyle,
                    textMeasurer = textMeasurer,
                    pageWidthPx = pageWidthPx,
                    availableHeightPx = remainingHeight - spacing,
                    forceOneLine = segments.isEmpty(),
                    blockIndex = blockIndex,
                )
                is ReaderBlock.Image -> measureImageSegment(
                    block = block,
                    blockIndex = blockIndex,
                    pageWidthPx = pageWidthPx,
                    pageHeightPx = pageHeightPx,
                    availableHeightPx = remainingHeight - spacing,
                    force = segments.isEmpty(),
                )
            } ?: break
            segments += segment
            remainingHeight -= spacing + segment.measuredHeightPx
            val blockComplete = when (block) {
                is ReaderBlock.Paragraph -> segment.endCharacterIndex >= block.text.length
                is ReaderBlock.Heading -> segment.endCharacterIndex >= block.text.length
                is ReaderBlock.Image -> true
            }
            if (blockComplete) {
                blockIndex += 1
                characterIndex = 0
            } else {
                characterIndex = segment.endCharacterIndex
                break
            }
        }
        check(segments.isNotEmpty()) { "Reader pagination made no progress" }
        val first = segments.first()
        val firstBlock = document.blocks[first.blockIndex]
        pages += ReaderPage(
            segments = segments,
            startBlockIndex = first.blockIndex,
            startCodePointOffset = firstBlock.codePointOffset(first.startCharacterIndex),
        )
    }
    return ReaderPageLayout(pages)
}

private fun measureTextSegment(
    text: String,
    startCharacterIndex: Int,
    style: TextStyle,
    textMeasurer: TextMeasurer,
    pageWidthPx: Int,
    availableHeightPx: Int,
    forceOneLine: Boolean,
    blockIndex: Int,
): ReaderPageSegment? {
    if (availableHeightPx <= 0 && !forceOneLine) return null
    val remainingText = text.substring(startCharacterIndex)
    val result = textMeasurer.measure(
        text = remainingText,
        style = style,
        constraints = Constraints(maxWidth = pageWidthPx),
    )
    var lastFittingLine = -1
    for (line in 0 until result.lineCount) {
        if (ceil(result.getLineBottom(line)).toInt() <= availableHeightPx) {
            lastFittingLine = line
        } else {
            break
        }
    }
    if (lastFittingLine < 0) {
        if (!forceOneLine || result.lineCount == 0) return null
        lastFittingLine = 0
    }
    val relativeEnd = result.getLineEnd(lastFittingLine, visibleEnd = false)
        .coerceAtLeast(remainingText.offsetByCodePoints(0, 1))
    return ReaderPageSegment(
        blockIndex = blockIndex,
        startCharacterIndex = startCharacterIndex,
        endCharacterIndex = (startCharacterIndex + relativeEnd).coerceAtMost(text.length),
        measuredHeightPx = ceil(result.getLineBottom(lastFittingLine)).toInt().coerceAtLeast(1),
    )
}

private fun measureImageSegment(
    block: ReaderBlock.Image,
    blockIndex: Int,
    pageWidthPx: Int,
    pageHeightPx: Int,
    availableHeightPx: Int,
    force: Boolean,
): ReaderPageSegment? {
    val sourceWidth = block.width
    val sourceHeight = block.height
    val measuredHeight = if (sourceWidth != null && sourceHeight != null) {
        (pageWidthPx.toLong() * sourceHeight / sourceWidth).toInt()
    } else {
        pageHeightPx / 3
    }.coerceIn(1, pageHeightPx)
    if (measuredHeight > availableHeightPx && !force) return null
    return ReaderPageSegment(
        blockIndex = blockIndex,
        startCharacterIndex = 0,
        endCharacterIndex = 0,
        measuredHeightPx = measuredHeight.coerceAtMost(pageHeightPx),
    )
}

private fun ReaderBlock.codePointOffset(characterIndex: Int): Int = when (this) {
    is ReaderBlock.Paragraph -> text.codePointCount(0, characterIndex.coerceIn(0, text.length))
    is ReaderBlock.Heading -> text.codePointCount(0, characterIndex.coerceIn(0, text.length))
    is ReaderBlock.Image -> 0
}
