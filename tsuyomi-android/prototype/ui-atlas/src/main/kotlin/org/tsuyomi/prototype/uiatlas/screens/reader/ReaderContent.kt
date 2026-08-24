/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

@Immutable
internal data class ReaderTextSettings(
    val fontSize: Float,
    val lineHeightMultiplier: Float,
    val horizontalMargin: Float,
    val verticalMargin: Float,
    val paragraphSpacing: Float,
    val letterSpacing: Float,
    val firstLineIndent: Float,
    val fontWeight: String,
    val alignment: String,
) {
    fun applyTo(style: TextStyle, indent: Boolean = true): TextStyle = style.copy(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * lineHeightMultiplier).sp,
        letterSpacing = letterSpacing.sp,
        fontWeight = if (fontWeight == "加粗") FontWeight.Medium else FontWeight.Normal,
        textAlign = if (alignment == "两端对齐") TextAlign.Justify else TextAlign.Start,
        textIndent = TextIndent(firstLine = if (indent) (fontSize * firstLineIndent).sp else 0.sp),
    )
}

@Composable
internal fun ReaderDocumentView(
    document: ReaderDocument,
    flow: ReaderFlow,
    textSettings: ReaderTextSettings,
    progress: Int,
    seeking: Boolean,
    onPositionChanged: (ReaderPosition) -> Unit,
    onImageClick: (ReaderImage) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val effectiveFlow = if (flow == ReaderFlow.DUAL && maxWidth < 600.dp) ReaderFlow.PAGED else flow
        when (effectiveFlow) {
            ReaderFlow.SCROLL -> ReaderScrollSurface(
                document,
                textSettings,
                progress,
                seeking,
                onPositionChanged,
                onImageClick,
                onLinkClick,
            )

            ReaderFlow.PAGED -> ReaderPagedSurface(
                document,
                textSettings,
                progress,
                seeking,
                onPositionChanged,
                onImageClick,
                onLinkClick,
            )

            ReaderFlow.DUAL -> ReaderDualPageSurface(
                document,
                textSettings,
                progress,
                seeking,
                onPositionChanged,
                onImageClick,
                onLinkClick,
            )
        }
    }
}

@Composable
private fun ReaderScrollSurface(
    document: ReaderDocument,
    textSettings: ReaderTextSettings,
    progress: Int,
    seeking: Boolean,
    onPositionChanged: (ReaderPosition) -> Unit,
    onImageClick: (ReaderImage) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    val pageCount = document.blocks.size.coerceAtLeast(1)
    val targetIndex = progressToPageIndex(progress, pageCount)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)
    val currentOnPositionChanged = rememberUpdatedState(onPositionChanged)

    LaunchedEffect(document.id, targetIndex, progress) {
        if (listState.firstVisibleItemIndex != targetIndex && (seeking || !listState.isScrollInProgress)) {
            listState.scrollToItem(targetIndex)
        }
        currentOnPositionChanged.value(ReaderPosition(progress.coerceIn(0, 100), targetIndex + 1, pageCount))
    }
    LaunchedEffect(listState, seeking, pageCount) {
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { (scrolling, firstVisibleItemIndex) ->
                if (scrolling && !seeking) {
                    currentOnPositionChanged.value(positionForPageIndex(firstVisibleItemIndex, pageCount))
                }
            }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth(),
            contentPadding = readerContentPadding(textSettings),
            verticalArrangement = Arrangement.spacedBy((textSettings.paragraphSpacing * 16f).dp),
        ) {
            items(document.blocks, key = ReaderBlock::id) { block ->
                ReaderBlockView(block, textSettings, onImageClick, onLinkClick)
            }
        }
    }
}

@Composable
private fun ReaderPagedSurface(
    document: ReaderDocument,
    textSettings: ReaderTextSettings,
    progress: Int,
    seeking: Boolean,
    onPositionChanged: (ReaderPosition) -> Unit,
    onImageClick: (ReaderImage) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    val pages = remember(document.id) { document.toReaderPages() }
    val targetPage = progressToPageIndex(progress, pages.size)
    val pagerState = rememberPagerState(initialPage = targetPage, pageCount = pages::size)
    val currentOnPositionChanged = rememberUpdatedState(onPositionChanged)

    LaunchedEffect(document.id, targetPage, progress) {
        if (pagerState.currentPage != targetPage) pagerState.scrollToPage(targetPage)
        currentOnPositionChanged.value(ReaderPosition(progress.coerceIn(0, 100), targetPage + 1, pages.size))
    }
    LaunchedEffect(pagerState, seeking, pages.size) {
        var userPaging = false
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .distinctUntilChanged()
            .collect { (scrolling, settledPage) ->
                if (scrolling && !seeking) userPaging = true
                if (!scrolling && userPaging) {
                    currentOnPositionChanged.value(positionForPageIndex(settledPage, pages.size))
                    userPaging = false
                }
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { index -> pages[index].firstOrNull()?.id ?: "page-$index" },
    ) { pageIndex ->
        ReaderStaticPage(pages[pageIndex], textSettings, onImageClick, onLinkClick)
    }
}

@Composable
private fun ReaderDualPageSurface(
    document: ReaderDocument,
    textSettings: ReaderTextSettings,
    progress: Int,
    seeking: Boolean,
    onPositionChanged: (ReaderPosition) -> Unit,
    onImageClick: (ReaderImage) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    val pages = remember(document.id) { document.toReaderPages() }
    val spreads = remember(pages) { pages.chunked(2) }
    val targetSpread = progressToPageIndex(progress, spreads.size)
    val pagerState = rememberPagerState(initialPage = targetSpread, pageCount = spreads::size)
    val currentOnPositionChanged = rememberUpdatedState(onPositionChanged)

    LaunchedEffect(document.id, targetSpread, progress) {
        if (pagerState.currentPage != targetSpread) pagerState.scrollToPage(targetSpread)
        val firstPage = (targetSpread * 2).coerceAtMost(pages.lastIndex)
        currentOnPositionChanged.value(ReaderPosition(progress.coerceIn(0, 100), firstPage + 1, pages.size))
    }
    LaunchedEffect(pagerState, seeking, spreads.size) {
        var userPaging = false
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .distinctUntilChanged()
            .collect { (scrolling, settledSpread) ->
                if (scrolling && !seeking) userPaging = true
                if (!scrolling && userPaging) {
                    val firstPage = (settledSpread * 2).coerceAtMost(pages.lastIndex)
                    val settledProgress = if (spreads.size == 1) 0 else {
                        (settledSpread * 100f / (spreads.size - 1)).roundToInt()
                    }
                    currentOnPositionChanged.value(ReaderPosition(settledProgress, firstPage + 1, pages.size))
                    userPaging = false
                }
            }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { spreadIndex ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = AtlasSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
        ) {
            spreads[spreadIndex].forEachIndexed { pageIndex, blocks ->
                ReaderStaticPage(
                    blocks = blocks,
                    textSettings = textSettings,
                    onImageClick = onImageClick,
                    onLinkClick = onLinkClick,
                    modifier = Modifier.weight(1f),
                )
                if (pageIndex == 0 && spreads[spreadIndex].size > 1) {
                    HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
                }
            }
        }
    }
}

@Composable
private fun ReaderStaticPage(
    blocks: List<ReaderBlock>,
    textSettings: ReaderTextSettings,
    onImageClick: (ReaderImage) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .clipToBounds()
                .padding(readerContentPadding(textSettings)),
            verticalArrangement = Arrangement.spacedBy((textSettings.paragraphSpacing * 16f).dp),
        ) {
            blocks.forEach { block -> ReaderBlockView(block, textSettings, onImageClick, onLinkClick) }
        }
    }
}

private fun ReaderDocument.toReaderPages(): List<List<ReaderBlock>> {
    if (blocks.isEmpty()) return listOf(emptyList())
    return buildList {
        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            if (block is ReaderHeading && index + 1 < blocks.size) {
                add(listOf(block, blocks[index + 1]))
                index += 2
            } else {
                add(listOf(block))
                index += 1
            }
        }
    }
}

private fun progressToPageIndex(progress: Int, pageCount: Int): Int {
    val safeCount = pageCount.coerceAtLeast(1)
    return if (safeCount == 1) 0 else {
        ((progress.coerceIn(0, 100) / 100f) * (safeCount - 1)).roundToInt().coerceIn(0, safeCount - 1)
    }
}

private fun positionForPageIndex(pageIndex: Int, pageCount: Int): ReaderPosition {
    val safeCount = pageCount.coerceAtLeast(1)
    val safeIndex = pageIndex.coerceIn(0, safeCount - 1)
    val progress = if (safeCount == 1) 0 else (safeIndex * 100f / (safeCount - 1)).roundToInt()
    return ReaderPosition(progress, safeIndex + 1, safeCount)
}

private fun readerContentPadding(settings: ReaderTextSettings) = PaddingValues(
    start = settings.horizontalMargin.dp,
    end = settings.horizontalMargin.dp,
    top = settings.verticalMargin.dp,
    bottom = (settings.verticalMargin + 16f).dp,
)

@Composable
private fun ReaderBlockView(
    block: ReaderBlock,
    textSettings: ReaderTextSettings,
    onImageClick: (ReaderImage) -> Unit,
    onLinkClick: (String) -> Unit,
    paragraphIndent: Boolean = true,
) {
    key(block.id) {
        when (block) {
            is ReaderHeading -> ReaderHeadingView(block)
            is ReaderParagraph -> ReaderRichText(block.content, textSettings, onLinkClick, paragraphIndent)
            is ReaderImage -> ReaderImageView(block, onImageClick)
            is ReaderQuote -> ReaderQuoteView(block, textSettings, onLinkClick)
            is ReaderDivider -> HorizontalDivider()
            is ReaderListBlock -> ReaderListView(block, textSettings, onLinkClick)
            is ReaderCodeBlock -> ReaderCodeView(block)
            is ReaderTableBlock -> ReaderTableView(block)
            is ReaderReplyReference -> ReaderReplyReferenceView(block)
            is ReaderAttachment -> ReaderAttachmentView(block)
            is ReaderPost -> ReaderPostView(block, textSettings, onImageClick, onLinkClick)
        }
    }
}

@Composable
private fun ReaderHeadingView(block: ReaderHeading) {
    Text(
        text = block.text,
        style = if (block.level == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = if (block.level == 1) AtlasSpacing.Sm else AtlasSpacing.Xs),
    )
}

@Composable
private fun ReaderRichText(
    content: List<ReaderInline>,
    textSettings: ReaderTextSettings,
    onLinkClick: (String) -> Unit,
    indent: Boolean = true,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(content, linkColor) {
        content.toAnnotatedString(linkColor)
    }
    Column(verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs)) {
        Text(
            text = annotated,
            style = textSettings.applyTo(MaterialTheme.typography.bodyLarge, indent),
        )
        content.filterIsInstance<ReaderInline.Link>().forEach { link ->
            Text(
                text = link.destination,
                modifier = Modifier.clickable { onLinkClick(link.destination) },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

private fun List<ReaderInline>.toAnnotatedString(linkColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        forEach { run ->
            val style = when (run) {
                is ReaderInline.Plain -> null
                is ReaderInline.Strong -> SpanStyle(fontWeight = FontWeight.Bold)
                is ReaderInline.Emphasis -> SpanStyle(fontWeight = FontWeight.Medium)
                is ReaderInline.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                is ReaderInline.Code -> SpanStyle(fontFamily = FontFamily.Monospace)
                is ReaderInline.Link -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                is ReaderInline.Ruby -> SpanStyle(fontWeight = FontWeight.Medium)
            }

            if (style != null) pushStyle(style)
            append(
                when (run) {
                    is ReaderInline.Ruby -> "${run.text}〔${run.reading}〕"
                    else -> run.text
                },
            )
            if (style != null) pop()
        }
    }

@Composable
private fun ReaderQuoteView(
    block: ReaderQuote,
    textSettings: ReaderTextSettings,
    onLinkClick: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .heightIn(min = 72.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
            ReaderRichText(block.content, textSettings, onLinkClick, indent = false)
            block.attribution?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


@Composable
private fun ReaderImageView(block: ReaderImage, onClick: (ReaderImage) -> Unit) {
    Surface(
        onClick = { onClick(block) },
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "${block.title}。${block.alternative}。点按查看大图"
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            ReaderIllustration(
                title = block.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(block.aspectRatio.coerceIn(0.65f, 2.1f)),
            )
            Text(
                text = block.caption,
                modifier = Modifier.padding(AtlasSpacing.Md),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ReaderIllustration(title: String, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val river = Path().apply {
                moveTo(0f, size.height * .78f)
                cubicTo(size.width * .25f, size.height * .58f, size.width * .6f, size.height * .92f, size.width, size.height * .62f)
            }
            drawPath(river, ink, style = Stroke(width = 5f))
            val ridge = Path().apply {
                moveTo(0f, size.height * .62f)
                lineTo(size.width * .22f, size.height * .28f)
                lineTo(size.width * .42f, size.height * .54f)
                lineTo(size.width * .64f, size.height * .2f)
                lineTo(size.width, size.height * .56f)
            }
            drawPath(ridge, ink, style = Stroke(width = 4f))
            drawCircle(accent, radius = size.minDimension * .045f, center = Offset(size.width * .72f, size.height * .34f))
        }
        Text(
            title,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(AtlasSpacing.Md)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .86f), MaterialTheme.shapes.small)
                .padding(horizontal = AtlasSpacing.Sm, vertical = AtlasSpacing.Xs),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ReaderListView(
    block: ReaderListBlock,
    textSettings: ReaderTextSettings,
    onLinkClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
        block.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                Text(if (block.ordered) "${index + 1}." else "•", style = MaterialTheme.typography.bodyLarge)
                Box(Modifier.weight(1f)) {
                    ReaderRichText(item, textSettings, onLinkClick, indent = false)
                }
            }
        }
    }
}


@Composable
private fun ReaderCodeView(block: ReaderCodeBlock) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(AtlasSpacing.Md), verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
            block.language?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(block.code, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

@Composable
private fun ReaderTableView(block: ReaderTableBlock) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .widthIn(min = 320.dp),
    ) {
        ReaderTableRow(block.headers, header = true)
        block.rows.forEach { row ->
            HorizontalDivider()
            ReaderTableRow(row, header = false)
        }
    }
}

@Composable
private fun ReaderTableRow(cells: List<String>, header: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell,
                modifier = Modifier
                    .width(160.dp)
                    .background(if (header) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                    .padding(AtlasSpacing.Sm),
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            )
            if (index != cells.lastIndex) Spacer(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline))
        }
    }
}

@Composable
private fun ReaderReplyReferenceView(block: ReaderReplyReference) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(AtlasSpacing.Sm)) {
            Text("回复 ${block.floor} · ${block.author}", style = MaterialTheme.typography.labelLarge)
            Text(block.excerpt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReaderAttachmentView(block: ReaderAttachment) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(AtlasSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
        ) {
            Icon(AtlasIcons.Document, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(block.name, style = MaterialTheme.typography.bodyMedium)
                Text(block.meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReaderPostView(
    block: ReaderPost,
    textSettings: ReaderTextSettings,
    onImageClick: (ReaderImage) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(AtlasSpacing.Md), verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(
                            if (block.isOriginalPoster) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        block.author.take(1),
                        color = if (block.isOriginalPoster) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.width(AtlasSpacing.Sm))
                Column(Modifier.weight(1f)) {
                    Text(block.author, style = MaterialTheme.typography.titleMedium)
                    Text(block.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(block.floor, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            block.blocks.forEach { child ->
                ReaderBlockView(child, textSettings, onImageClick, onLinkClick, paragraphIndent = false)
            }
        }
    }
}
