/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.reader.engine.ReaderDocumentSession
import org.tsuyomi.reader.engine.ReaderPresentation
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.locator.bookmarkPositionKey
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.ReaderInlineKind
import org.tsuyomi.shared.sourcecontract.ReaderLinkTarget
import org.tsuyomi.shared.sourcecontract.ReaderListItem
import org.tsuyomi.shared.sourcecontract.ReaderNavigationTarget
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import org.tsuyomi.shared.sourcecontract.ReaderSpan
import org.tsuyomi.shared.sourcecontract.ReaderTableCell
import org.tsuyomi.shared.sourcecontract.canonicalAnchorText
import org.tsuyomi.shared.sourcecontract.ReaderTableRow

/** Production renderer seams, not a second proof surface or screenshot-only approval. */
@OptIn(ExperimentalTestApi::class)
class ReaderRichContentInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rubyAndSupplementaryCharactersKeepCanonicalAnchorsAndNativePlaceholderGeometry() {
        val source = "甲😀漢字乙"
        val rich = ReaderRichText.create(source, listOf(ReaderSpan(2, 4, ReaderInlineKind.Ruby("かんじ"))))
        assertEquals(source, rich.accessibleText(0, rich.displayText.length))
        assertEquals(2, rich.codePointOffsetAt(rich.displayOffsetAt(2)))
        assertEquals(4, rich.codePointOffsetAt(rich.displayOffsetAt(4)))
        assertEquals(5, rich.codePointOffsetAt(rich.displayText.length))
        val initialDocument = document(listOf(ReaderBlock.Paragraph("ruby", source, listOf(
            ReaderSpan(0, 2, ReaderInlineKind.Strong), ReaderSpan(2, 4, ReaderInlineKind.Ruby("かんじ")),
        ))))
        val link = ReaderInlineKind.Link(ReaderLinkTarget.ExternalHttps("https://example.com/ruby"))
        val layeredSpans = listOf(
            ReaderSpan(0, 2, ReaderInlineKind.Strong),
            ReaderSpan(2, 4, ReaderInlineKind.Ruby("外読")),
            ReaderSpan(2, 4, ReaderInlineKind.Ruby("同読")),
            ReaderSpan(2, 4, link),
            ReaderSpan(2, 3, ReaderInlineKind.Ruby("内")),
            ReaderSpan(4, 5, ReaderInlineKind.Ruby("おつ")),
        )
        val layeredDocument = document(listOf(ReaderBlock.Paragraph("ruby", source, layeredSpans)))
        val layeredRich = ReaderRichText.create(source, layeredSpans)
        assertEquals(listOf("外読", "同読", "内", "おつ"), layeredRich.rubies.flatMap { it.annotations }.map { it.text })
        assertEquals(source, layeredRich.accessibleText(0, layeredRich.displayText.length))
        val readings = List(40) { "層${it.toString().padStart(2, '0')}" + "注".repeat(240) }
        val overflowDocument = document(listOf(ReaderBlock.Paragraph("overflow", "漢字",
            readings.map { ReaderSpan(0, 2, ReaderInlineKind.Ruby(it)) } + ReaderSpan(0, 2, link))))
        val maximumSpans = List(20_000) { ReaderSpan(0, 1, ReaderInlineKind.Ruby("注")) }
        ReaderBlock.Paragraph("maximum", "漢", maximumSpans)
        val targetA = ReaderLinkTarget.ExternalHttps("https://example.com/ruby-a")
        val targetB = ReaderLinkTarget.ExternalHttps("https://example.com/ruby-b")
        val dualActions = listOf(ReaderSpan(0, 1, ReaderInlineKind.Strong),
            ReaderSpan(0, 1, ReaderInlineKind.Link(targetA)), ReaderSpan(1, 2, ReaderInlineKind.Emphasis),
            ReaderSpan(1, 2, ReaderInlineKind.Link(targetB)))
        val dualSpans = listOf(ReaderSpan(0, 2, ReaderInlineKind.Ruby("よみ"))) + dualActions
        val dualRich = ReaderRichText.create("AB", dualSpans)
        val dualDocument = document(listOf(ReaderBlock.Paragraph("dual-links", "AB", dualSpans)))
        val dualOverflowSpans = readings.map { ReaderSpan(0, 2, ReaderInlineKind.Ruby(it)) } + dualActions
        val dualOverflowDocument = document(listOf(ReaderBlock.Paragraph("dual-links", "AB", dualOverflowSpans)))
        val styled = ReaderRichText.create("abcd", listOf(ReaderSpan(0, 4, ReaderInlineKind.Strong),
            ReaderSpan(0, 4, ReaderInlineKind.Strong), ReaderSpan(0, 1, ReaderInlineKind.Ruby("a")),
            ReaderSpan(0, 1, ReaderInlineKind.Emphasis), ReaderSpan(2, 3, ReaderInlineKind.Ruby("c")),
            ReaderSpan(2, 3, ReaderInlineKind.Strike)))
        assertEquals("abcd", styled.accessibleText(0, styled.displayText.length))
        assertTrue(styled.rubies.all { it.base.spanStyles.single().item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
        assertEquals(androidx.compose.ui.text.font.FontStyle.Italic, styled.rubies.first().base.spanStyles.single().item.fontStyle)
        assertEquals(androidx.compose.ui.text.style.TextDecoration.LineThrough, styled.rubies.last().base.spanStyles.single().item.textDecoration)
        val activeDocument = mutableStateOf(initialDocument)
        val activeFlow = mutableStateOf("paged")
        val restoredLocator = mutableStateOf<ReaderLocator?>(null)
        val layeredMeasurement = AtomicReference<MeasuredReaderText>()
        val overflowLayout = AtomicReference<ReaderPageLayout>()
        val maximumFragments = AtomicReference<List<ReaderRichText>>()
        val dualProof = AtomicReference<Pair<ReaderRichText, MeasuredReaderText>>()
        val dualOverflowProof = AtomicReference<Pair<ReaderRichText, MeasuredReaderText>>()
        val emojiProof = AtomicReference<Pair<ReaderRichText, MeasuredReaderText>>()
        val lastTarget = AtomicReference<ReaderLinkTarget>()
        val captured = AtomicReference<ReaderLocator>()
        val precision = AtomicReference<LocatorPrecision>()
        val links = AtomicInteger()
        val nativeSize = IntSize(192, 128)
        val settings = ReaderSettingsUiState(horizontalMargin = 0f, verticalMargin = 0f, paragraphSpacing = 0f)
        composeRule.setContent {
            ReaderTestTheme {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val style = readerBodyStyle(settings)
                val layered = remember(style, density) {
                    val prepared = readerRubyFragments(layeredRich, style, measurer, nativeSize.width, 512).single()
                    measureReaderText(prepared, 0, prepared.displayText.length, style, measurer, density, nativeSize.width)
                }
                val maximum = remember(style, density) {
                    readerRubyFragments(ReaderRichText.create("漢", maximumSpans), style, measurer, nativeSize.width, nativeSize.height)
                }
                val dual = remember(style, density) {
                    val prepared = readerRubyFragments(dualRich, style, measurer, nativeSize.width, 512).single()
                    prepared to measureReaderText(prepared, 0, prepared.displayText.length, style, measurer, density, nativeSize.width)
                }
                val dualOverflow = remember(style, density) {
                    val first = readerRubyFragments(ReaderRichText.create("AB", dualOverflowSpans), style, measurer,
                        nativeSize.width, nativeSize.height).first()
                    first to measureReaderText(first, 0, first.displayText.length, style, measurer, density, nativeSize.width)
                }
                val emoji = remember(style, density) {
                    val prepared = readerRubyFragments(ReaderRichText.create("😀B", dualSpans), style, measurer,
                        nativeSize.width, 512).single()
                    prepared to measureReaderText(prepared, 0, prepared.displayText.length, style, measurer, density, nativeSize.width)
                }
                val items = rememberReaderLayoutItems(overflowDocument)
                val layout = rememberReaderPageLayout(overflowDocument, items, emptyMap(), settings, nativeSize, dual = false, enabled = true)
                SideEffect {
                    layeredMeasurement.set(layered)
                    overflowLayout.set(layout)
                    maximumFragments.set(maximum)
                    dualProof.set(dual)
                    dualOverflowProof.set(dualOverflow)
                    emojiProof.set(emoji)
                }
                TestReader(activeDocument.value, requestedFlow = activeFlow.value, restoredLocator = restoredLocator.value,
                    restorationGeneration = if (restoredLocator.value == null) 0L else 1L,
                    onLocatorChanged = captured::set, onLocatorPrecisionChanged = precision::set,
                    onNavigateLink = { target -> lastTarget.set(target); links.incrementAndGet() })
            }
        }
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        composeRule.onNodeWithText(source).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        val rubyBounds = requireNotNull(layout.placeholderRects.single())
        assertTrue("Ruby bounds=$rubyBounds body=${layout.size} constraints=${layout.layoutInput.constraints}",
            rubyBounds.width > 0f && rubyBounds.height > 0f)
        assertTrue("Ruby bounds=$rubyBounds body=${layout.size} constraints=${layout.layoutInput.constraints}",
            rubyBounds.left >= 0f && rubyBounds.right <= layout.size.width)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, layout.layoutInput.text.spanStyles.first().item.fontWeight)
        val layered = composeRule.runOnIdle { requireNotNull(layeredMeasurement.get()) }
        assertEquals(2, layered.rubies.size)
        assertEquals(listOf(1, 2, 4), layered.rubies.first().annotations.map { it.sourceIndex })
        assertTrue(layered.rubies.first().annotations.zipWithNext().all { (first, next) ->
            first.y + first.layout.size.height <= next.y })
        assertEquals(layered.rubies.first().annotations.sumOf { it.layout.size.height }, layered.rubies.first().baseTopPx)
        layered.body.placeholderRects.filterNotNull().forEach { bounds ->
            assertTrue("Ruby bounds=$bounds body=${layered.body.size} constraints=${layered.body.layoutInput.constraints}",
                bounds.left >= 0 && bounds.right <= layered.body.size.width)
            assertTrue("Ruby bounds=$bounds body=${layered.body.size} constraints=${layered.body.layoutInput.constraints} lineHeight=${layered.body.layoutInput.style.lineHeight} " +
                "lineHeightStyle=${layered.body.layoutInput.style.lineHeightStyle} nativeHeights=${layered.rubies.map { it.heightPx }} " +
                "lines=${(0 until layered.body.lineCount).map { line -> Triple(layered.body.getLineTop(line), layered.body.getLineBaseline(line), layered.body.getLineBottom(line)) }}",
                bounds.top >= 0 && bounds.bottom <= layered.body.size.height)
        }
        val continued = composeRule.runOnIdle { requireNotNull(overflowLayout.get()) }
        val parts = continued.items.mapNotNull { it.richText }
        val rebuiltReadings = Array(readings.size) { StringBuilder() }
        val readingOrder = ArrayList<Int>()
        parts.forEach { part ->
            part.rubies.forEach { ruby -> ruby.measured!!.annotations.forEach { annotation ->
                rebuiltReadings[annotation.sourceIndex].append(annotation.layout.layoutInput.text.text)
                readingOrder += annotation.sourceIndex
                assertEquals(annotation.endCharacterIndex - annotation.startCharacterIndex, annotation.layout.layoutInput.text.length)
            } }
        }
        assertEquals(readings, rebuiltReadings.map { it.toString() })
        assertEquals(readings.indices.toList(), readingOrder.distinct())
        assertTrue(readingOrder.zipWithNext().all { (first, next) -> first <= next })
        assertEquals("漢字", parts.joinToString("") { it.accessibleText(0, it.displayText.length) })
        assertTrue(parts.filter { it.annotationOnly }.all { !it.hasExactPositionAt(0) })
        assertTrue(continued.pages.size > 1)
        assertTrue(continued.pages.first().positionIsExact.not())
        continued.pages.forEach { page -> page.segments.forEach { segment ->
            assertTrue(segment.measuredHeightPx in 1..nativeSize.height)
            val measurement = requireNotNull(segment.measuredText)
            val part = requireNotNull(continued.items[segment.itemIndex].richText)
            if (part.annotationOnly) {
                val bounds = requireNotNull(measurement.body.placeholderRects.single())
                val layer = measurement.rubies.single().annotations.single()
                val glyph = layer.layout.getBoundingBox(0)
                assertEquals(link, part.actionAt(Offset(bounds.left + layer.x + glyph.center.x,
                    bounds.top + layer.y + glyph.center.y), measurement, segment.startCharacterIndex))
            }
            measurement.body.placeholderRects.filterNotNull().forEach { bounds ->
                val geometry = "Ruby continuation bounds=$bounds body=${measurement.body.size} " +
                    "constraints=${measurement.body.layoutInput.constraints} viewport=$nativeSize " +
                    "item=${segment.itemIndex} range=${segment.startCharacterIndex}..${segment.endCharacterIndex} " +
                    "annotationOnly=${part.annotationOnly} nativeSizes=${measurement.rubies.map { it.widthPx to it.heightPx }} " +
                    "placeholders=${measurement.body.layoutInput.placeholders} density=${measurement.body.layoutInput.density}"
                assertTrue(geometry, bounds.left >= 0 && bounds.right <= nativeSize.width)
                assertTrue(geometry, bounds.top >= 0 && bounds.bottom <= measurement.body.size.height)
            }
        } }
        val maximum = composeRule.runOnIdle { requireNotNull(maximumFragments.get()) }
        var maximumLayerCount = 0
        var maximumHeightsFit = true
        for (partIndex in maximum.indices) {
            val rubies = maximum[partIndex].rubies
            for (rubyIndex in rubies.indices) {
                val measured = rubies[rubyIndex].measured!!
                maximumHeightsFit = maximumHeightsFit && measured.heightPx in 1..nativeSize.height
                for (annotationIndex in measured.annotations.indices) {
                    assertEquals(maximumLayerCount, measured.annotations[annotationIndex].sourceIndex)
                    maximumLayerCount++
                }
            }
        }
        assertEquals(20_000, maximumLayerCount)
        assertEquals("漢", maximum.joinToString("") { it.accessibleText(0, it.displayText.length) })
        assertTrue(maximumHeightsFit)
        composeRule.runOnIdle { activeDocument.value = layeredDocument }
        activate(source, "漢字")
        assertEquals(1, links.get())
        composeRule.runOnIdle { activeDocument.value = overflowDocument }
        val firstReading = composeRule.onNodeWithContentDescription(readings.first())
        firstReading.assertIsDisplayed()
        val action = firstReading.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "漢字" }
        composeRule.runOnIdle { assertTrue(action.action()) }
        assertEquals(2, links.get())
        composeRule.onNodeWithTag("reader-content-surface").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        composeRule.waitUntil { precision.get() == LocatorPrecision.DEGRADED }
        val continuationLocator = captured.get()
        assertEquals(null, continuationLocator.characterOffset)
        assertEquals(null, continuationLocator.textAnchorDigest)
        assertEquals(LocatorPrecision.DEGRADED,
            ReaderDocumentSession(overflowDocument, continuationLocator, ReaderPresentation.PAGED).position.precision)
        composeRule.runOnIdle {
            restoredLocator.value = continuationLocator
            activeFlow.value = "scroll"
        }
        composeRule.onNodeWithContentDescription(readings.first()).assertIsDisplayed()
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToNode(hasText("漢字"))
        composeRule.onNodeWithText("漢字").assertIsDisplayed()
        activate("漢字", "漢字")
        assertEquals(3, links.get())
        assertEquals(LocatorPrecision.DEGRADED, precision.get())
        val (dualPrepared, dualMeasured) = composeRule.runOnIdle { requireNotNull(dualProof.get()) }
        assertEquals(listOf("A", "B"), dualPrepared.actions(0, dualPrepared.displayText.length).map { it.first })
        val readingBounds = requireNotNull(dualMeasured.body.placeholderRects.single())
        val reading = dualMeasured.rubies.single().annotations.single()
        val readingGlyph = reading.layout.getBoundingBox(0)
        assertEquals(null, dualPrepared.actionAt(Offset(readingBounds.left + reading.x + readingGlyph.center.x,
            readingBounds.top + reading.y + readingGlyph.center.y), dualMeasured))
        val (firstReadingPart, firstReadingMeasured) = composeRule.runOnIdle { requireNotNull(dualOverflowProof.get()) }
        val firstBounds = requireNotNull(firstReadingMeasured.body.placeholderRects.single())
        val firstLayer = firstReadingMeasured.rubies.single().annotations.single()
        val firstGlyph = firstLayer.layout.getBoundingBox(0)
        assertEquals(null, firstReadingPart.actionAt(Offset(firstBounds.left + firstLayer.x + firstGlyph.center.x,
            firstBounds.top + firstLayer.y + firstGlyph.center.y), firstReadingMeasured))
        assertEquals(listOf("A", "B"), firstReadingPart.actions(0, firstReadingPart.displayText.length).map { it.first })
        val (emojiPrepared, emojiMeasured) = composeRule.runOnIdle { requireNotNull(emojiProof.get()) }
        val emojiBounds = requireNotNull(emojiMeasured.body.placeholderRects.single())
        val emojiBase = requireNotNull(emojiMeasured.rubies.single().base)
        for (codePoint in 0..1) {
            val glyph = emojiBase.getBoundingBox(emojiBase.layoutInput.text.text.offsetByCodePoints(0, codePoint))
            val hit = Offset(emojiBounds.left + (emojiBounds.width - emojiBase.size.width) / 2f + glyph.center.x,
                emojiBounds.top + emojiMeasured.rubies.single().baseTopPx + glyph.center.y)
            assertEquals(ReaderInlineKind.Link(if (codePoint == 0) targetA else targetB), emojiPrepared.actionAt(hit, emojiMeasured))
        }
        fun tapDualBase(codePoint: Int, expected: ReaderLinkTarget) {
            composeRule.onNodeWithTag("reader-top-chrome").assertDoesNotExist()
            val node = composeRule.onNodeWithText("AB")
            val nativeLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(nativeLayouts) }
            val bounds = requireNotNull(nativeLayouts.single().placeholderRects.single())
            val base = requireNotNull(dualMeasured.rubies.single().base)
            val glyph = base.getBoundingBox(base.layoutInput.text.text.offsetByCodePoints(0, codePoint))
            node.performTouchInput { click(Offset(bounds.left + (bounds.width - base.size.width) / 2f + glyph.center.x,
                bounds.bottom - base.size.height + glyph.center.y)) }
            composeRule.runOnIdle { assertEquals(expected, lastTarget.get()) }
        }
        composeRule.runOnIdle {
            restoredLocator.value = null
            activeFlow.value = "paged"
            activeDocument.value = dualDocument
        }
        // Prepare unobstructed native hits without tapping linked overflow content.
        composeRule.onNodeWithTag("reader-content-surface").performSemanticsAction(SemanticsActions.OnClick) {
            it()
        }
        tapDualBase(0, targetA)
        tapDualBase(1, targetB)
        assertEquals(5, links.get())
        composeRule.runOnIdle {
            activeFlow.value = "scroll"
            activeDocument.value = dualOverflowDocument
        }
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToNode(hasText("AB"))
        tapDualBase(0, targetA)
        tapDualBase(1, targetB)
        assertEquals(7, links.get())
    }

    @Test
    fun measuredPagesPackRichBlocksAndSplitTallTableRowsWithoutLosingCellText() {
        val firstCell = (1..80).joinToString("\n") { "甲😀第${it}行" }
        val secondCell = (1..100).joinToString("\n") { "第二列${it}行" }
        val blocks = listOf(
            ReaderBlock.Paragraph("short-a", "短段一"),
            ReaderBlock.Paragraph("short-b", "短段二"),
            ReaderBlock.Quote("quote", "引用 " + "甲😀乙".repeat(30), attribution = "作者"),
            ReaderBlock.Listing("list", ordered = true, items = listOf(
                ReaderListItem("item", listOf(ReaderBlock.Paragraph("listed", "列表正文"))),
            )),
            ReaderBlock.Table("table", listOf(ReaderTableRow(listOf(
                ReaderTableCell(firstCell), ReaderTableCell(secondCell),
            )))),
            ReaderBlock.Paragraph("after-table", "表格后的完整正文"),
        )
        val document = document(blocks)
        val measured = AtomicReference<ReaderPageLayout>()
        val pageSize = IntSize(360, 240)
        val settings = ReaderSettingsUiState(horizontalMargin = 16f, verticalMargin = 12f, paragraphSpacing = 8f)
        composeRule.setContent {
            ReaderTestTheme {
                val items = rememberReaderLayoutItems(document)
                val layout = rememberReaderPageLayout(document, items, emptyMap(), settings, pageSize, dual = false, enabled = true)
                SideEffect { measured.set(layout) }
            }
        }
        val layout = composeRule.runOnIdle { requireNotNull(measured.get()) }
        val allSegments = layout.pages.flatMap { it.segments }
        assertTrue(layout.pages.first().segments.size >= 2)
        val tableSegments = allSegments.filter { layout.items[it.itemIndex].kind == ReaderLayoutItemKind.TABLE_ROW }
        assertTrue("A tall row must continue over real pages rather than be clipped", tableSegments.size > 1)
        for ((column, expected) in listOf(firstCell, secondCell).withIndex()) {
            val actual = tableSegments.joinToString("") { segment ->
                val cell = segment.tableCells[column]
                cell.richText.accessibleText(cell.start, cell.end)
            }
            assertEquals(expected, actual)
        }
        val density = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val height = pageSize.height - (settings.verticalMargin * density).toInt() * 2
        val spacing = (settings.paragraphSpacing * density).toInt()
        layout.pages.forEach { page ->
            assertTrue("The measured content must fit its page", page.segments.sumOf { it.measuredHeightPx } +
                spacing * (page.segments.size - 1) <= height + 2)
            page.segments.forEach { segment ->
                segment.measuredText?.let { assertTrue(it.body.size.height <= segment.measuredHeightPx) }
                val text = layout.items[segment.itemIndex].richText?.displayText?.text ?: return@forEach
                assertTrue(segment.startCharacterIndex == text.length || !Character.isLowSurrogate(text[segment.startCharacterIndex]))
                assertTrue(segment.endCharacterIndex == text.length || !Character.isLowSurrogate(text[segment.endCharacterIndex]))
            }
        }
        assertTrue(allSegments.any { layout.items[it.itemIndex].block.blockId == "after-table" })
        val firstTablePage = layout.pages.indexOfFirst { page -> page.segments.any { it.tableCells.isNotEmpty() } }
        val secondColumnStart = firstCell.codePointCount(0, firstCell.length) + 1
        assertEquals("A second-column anchor must not jump to the page where column one ends",
            firstTablePage, layout.pageIndexFor(4, secondColumnStart))
    }

    @Test
    fun duplicateMetadataAnchorsRestoreTheFirstPageAndOrdinaryPageBoundariesRemainDistinct() {
        val layout = ReaderPageLayout(emptyList(), listOf(
            ReaderPage(emptyList(), 0, 0), ReaderPage(emptyList(), 0, 0),
            ReaderPage(emptyList(), 0, 20), ReaderPage(emptyList(), 1, 0),
        ))
        assertEquals(0, layout.pageIndexFor(0, 0))
        assertEquals(1, layout.pageIndexFor(0, 19))
        assertEquals(2, layout.pageIndexFor(0, 20))
        assertEquals(3, layout.pageIndexFor(1, 0))
        val position = ReaderPosition.fromSeekProgress(100, 5, 2)
        assertEquals(5, position.page)
        assertEquals(null, position.adjacentPage(1))
        assertEquals(3, position.adjacentPage(-1)?.page)
        val oddRestoredPosition = ReaderPosition.fromPageIndex(1, 5, 2)
        assertEquals(1, oddRestoredPosition.adjacentPage(-1)?.page)
    }

    @Test
    fun notePreviewReturnsToItsExactOriginWithoutProgressWritesAndHostLinksDispatchOnce() {
        val text = "正文 注释 外链 跨章 内部"
        val external = ReaderLinkTarget.ExternalHttps("https://example.test/reference")
        val crossContent = ReaderLinkTarget.Internal("next", "opening")
        val spans = listOf(
            span(text, "注释", ReaderInlineKind.NoteReference("note-1")),
            span(text, "外链", ReaderInlineKind.Link(external)),
            span(text, "跨章", ReaderInlineKind.Link(crossContent)),
            span(text, "内部", ReaderInlineKind.Link(ReaderLinkTarget.Internal("section", "ending"))),
        )
        val document = document(listOf(ReaderBlock.Paragraph("origin", text, spans)) +
            (1..30).map { ReaderBlock.Paragraph("filler-$it", "段落 $it " + "正文".repeat(100)) } + listOf(
                ReaderBlock.Note("note", "note-1", "1", listOf(ReaderBlock.Paragraph("note-body", "注释的完整正文"))),
                ReaderBlock.Paragraph("ending", "内部锚点正文", anchorId = "ending"),
            ))
        val origin = ReaderDocumentSession(document, null, ReaderPresentation.PAGED).navigateToBlock(0).locator
        val restored = mutableStateOf(origin)
        val generation = mutableStateOf(0L)
        val writes = AtomicInteger()
        val links = mutableListOf<ReaderLinkTarget>()
        val returned = AtomicReference<ReaderLocator>()
        composeRule.setContent {
            ReaderTestTheme {
                TestReader(document, restoredLocator = restored.value, restorationGeneration = generation.value,
                    onLocatorChanged = { writes.incrementAndGet() }, onNavigateLink = { links += it },
                    onReturnToOrigin = { target -> returned.set(target); restored.value = target; generation.value++ })
            }
        }
        activate(text, "注释")
        composeRule.onNodeWithText("注释的完整正文").assertIsDisplayed()
        assertEquals(0, writes.get())
        composeRule.onNodeWithTag("reader-return-origin").performClick()
        composeRule.onNodeWithText(text).assertIsDisplayed()
        assertEquals(origin.bookmarkPositionKey(), returned.get().bookmarkPositionKey())
        assertEquals(0, writes.get())
        activate(text, "外链")
        activate(text, "跨章")
        composeRule.runOnIdle { assertEquals(listOf(external, crossContent), links) }
        activate(text, "内部")
        composeRule.onNodeWithText("内部锚点正文").assertIsDisplayed()
        assertEquals(1, writes.get())
        composeRule.onNodeWithTag("reader-return-origin").performClick()
        composeRule.onNodeWithText(text).assertIsDisplayed()
        assertEquals(origin.bookmarkPositionKey(), returned.get().bookmarkPositionKey())
    }

    @Test
    fun einkMeasuredPagesAndDualPreserveLocatorAndStandardRequestedFlow() {
        val document = document(listOf(ReaderBlock.Paragraph("long", "甲😀乙正文。".repeat(1500))))
        val initial = ReaderDocumentSession(document, null, ReaderPresentation.SCROLL).navigateToBlock(0, 250).locator
        val environment = mutableStateOf(environment(DisplayProfile.STANDARD))
        val writes = AtomicInteger()
        val flowWrites = AtomicInteger()
        val current = AtomicReference<ReaderLocator>()
        val captured = AtomicReference<ReaderLocator>()
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(840.dp, 760.dp))) {
                ReaderTestTheme(environment.value) {
                    TestReader(document, requestedFlow = "scroll", restoredLocator = initial,
                        onLocatorChanged = { current.set(it); writes.incrementAndGet() },
                        onToggleBookmark = captured::set, onFlowOverrideChanged = { flowWrites.incrementAndGet() })
                }
            }
        }
        composeRule.onNodeWithTag("reader-document-scroll").assertIsDisplayed()
        composeRule.runOnIdle { environment.value = environment(DisplayProfile.EINK) }
        composeRule.onNodeWithTag("reader-page-column-0").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("添加书签").performClick()
        assertEquals(initial.bookmarkPositionKey(), captured.get().bookmarkPositionKey())
        assertEquals(0, writes.get())
        composeRule.onNodeWithTag("reader-content-surface").performKeyInput { keyDown(Key.VolumeDown); keyUp(Key.VolumeDown) }
        composeRule.waitUntil { writes.get() == 1 }
        assertTrue(requireNotNull(current.get().characterOffset) > 250)
        assertEquals(1, composeRule.onAllNodesWithTag("reader-page-column-0").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("reader-bottom-action-设置").performClick()
        composeRule.onNodeWithTag("reader-settings-page").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-eink-project-page").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("reader-eink-option-dual").assertIsDisplayed().performClick()
        pressBack()
        composeRule.onNodeWithTag("reader-page-column-1").assertIsDisplayed()
        assertEquals(1, writes.get())
        val before = requireNotNull(current.get().characterOffset)
        composeRule.onNodeWithTag("reader-content-surface").performKeyInput { keyDown(Key.VolumeDown); keyUp(Key.VolumeDown) }
        composeRule.waitUntil { writes.get() == 2 }
        assertTrue(requireNotNull(current.get().characterOffset) > before)
        composeRule.runOnIdle { environment.value = environment(DisplayProfile.STANDARD) }
        composeRule.onNodeWithTag("reader-document-scroll").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("添加书签").performClick()
        assertEquals(current.get().bookmarkPositionKey(), captured.get().bookmarkPositionKey())
        assertEquals(0, flowWrites.get())
        assertEquals(2, writes.get())
    }

    @Test
    fun publicationIllustrationsUsePublicGrayscaleFilterOnlyForEink() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val image = ReaderBlock.Image("illustration", ReaderResourceRef.Local(
            publicationId, "87654321-4321-4321-4321-cba987654321", "image/png", "a".repeat(64),
        ), altText = "colour illustration", width = 16, height = 16)
        val document = document(listOf(image))
        val environment = mutableStateOf(environment(DisplayProfile.EINK))
        composeRule.setContent {
            ReaderTestTheme(environment.value) { TestReader(document, imageStates = mapOf(image.blockId to CoverUiState.Ready(bitmap))) }
        }
        val imageNode = composeRule.onNodeWithContentDescription("colour illustration")
        val monochrome = imageNode.captureToImage().toPixelMap()
        val gray = monochrome[monochrome.width / 2, monochrome.height / 2]
        assertEquals(gray.red, gray.green, 0.01f)
        assertEquals(gray.green, gray.blue, 0.01f)
        composeRule.runOnIdle { environment.value = environment(DisplayProfile.STANDARD) }
        val coloured = imageNode.captureToImage().toPixelMap()
        val red = coloured[coloured.width / 2, coloured.height / 2]
        assertTrue(red.red > red.green + 0.5f && red.red > red.blue + 0.5f)
    }

    @Test
    fun mediaReflowInvalidatesAnInFlightPagedSeekWithoutCommittingItsOldPage() {
        val image = ReaderBlock.Image("illustration", ReaderResourceRef.RemoteHttps("https://example.test/image.png"), width = 100, height = 100)
        val document = document(listOf(image) + (1..40).map { ReaderBlock.Paragraph("p-$it", "正文".repeat(100)) })
        val states = mutableStateOf<Map<String, CoverUiState>>(emptyMap())
        val writes = AtomicInteger()
        val captured = AtomicReference<ReaderLocator>()
        composeRule.setContent { ReaderTestTheme {
            TestReader(document, imageStates = states.value, onLocatorChanged = { writes.incrementAndGet() }, onToggleBookmark = captured::set)
        } }
        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        slider.performTouchInput { down(Offset(width * 0.2f, centerY)); moveTo(Offset(width * 0.8f, centerY)) }
        composeRule.runOnIdle {
            states.value = mapOf(image.blockId to CoverUiState.Ready(Bitmap.createBitmap(100, 500, Bitmap.Config.ARGB_8888)))
        }
        composeRule.waitForIdle()
        slider.performTouchInput { up() }
        composeRule.onNodeWithContentDescription("添加书签").performClick()
        assertEquals(0, writes.get())
        assertEquals(image.blockId, captured.get().blockId)
    }
    @Test
    fun replacingTheDocumentDuringADragRejectsRetiredReleaseCallbacks() {
        val first = document(listOf(ReaderBlock.Paragraph("old", "正文".repeat(6000))))
        val replacement = first.copy(identity = first.identity.copy(contentId = "replacement"),
            blocks = listOf(ReaderBlock.Paragraph("new", "替换后的正文")))
        val active = mutableStateOf(first)
        val writes = mutableListOf<ReaderLocator>()
        composeRule.setContent { ReaderTestTheme { TestReader(active.value, onLocatorChanged = { writes += it }) } }
        val body = composeRule.onNodeWithTag("reader-content-surface")
        composeRule.mainClock.autoAdvance = false
        try {
            body.performTouchInput {
                down(Offset(width * 0.85f, centerY))
                moveTo(Offset(width * 0.45f, centerY))
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync { active.value = replacement }
            composeRule.mainClock.advanceTimeByFrame()
            body.performTouchInput { up() }
            composeRule.mainClock.advanceTimeBy(500)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.onNodeWithText("替换后的正文").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(writes.isEmpty()) }
    }

    @Test
    fun tableBookmarksRestoreTheSecondColumnLineWithoutAProgressWrite() {
        val left = (1..80).joinToString("\n") { "左列${it}行" }
        val right = (1..100).joinToString("\n") { "右列${it}行" }
        val table = ReaderBlock.Table("table", listOf(ReaderTableRow(listOf(ReaderTableCell(left), ReaderTableCell(right)))))
        val document = document(listOf(table))
        val offset = left.codePointCount(0, left.length) + 1 + right.codePointCount(0, right.indexOf("右列60行"))
        val locator = ReaderDocumentSession(document, null, ReaderPresentation.SCROLL).navigateToBlock(0, offset).locator
        val writes = AtomicInteger()
        val captured = AtomicReference<ReaderLocator>()
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(420.dp, 760.dp))) {
                ReaderTestTheme { TestReader(document, requestedFlow = "scroll", restoredLocator = locator,
                    onLocatorChanged = { writes.incrementAndGet() }, onToggleBookmark = captured::set) }
            }
        }
        val cell = composeRule.onNodeWithText(right)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        cell.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val line = layout.getLineForOffset(right.indexOf("右列60行"))
        val position = cell.fetchSemanticsNode().positionInRoot.y + layout.getLineTop(line)
        val viewport = composeRule.onNodeWithTag("reader-document-scroll").fetchSemanticsNode().boundsInRoot
        assertTrue(position >= viewport.top - 1f && position + layout.getLineBottom(line) - layout.getLineTop(line) <= viewport.bottom + 1f)
        composeRule.onNodeWithContentDescription("添加书签").performClick()
        assertEquals(locator.bookmarkPositionKey(), captured.get().bookmarkPositionKey())
        assertEquals(0, writes.get())
    }

    @Test
    fun wideTablesKeepReadableColumnsAndRestoreTheSameColumnAcrossRowsAndProfiles() {
        val table = ReaderBlock.Table("wide", listOf(
            ReaderTableRow((0..15).map { ReaderTableCell(if (it == 15) "头尾" else "表头$it", header = true) }),
            ReaderTableRow((0..15).map { ReaderTableCell(if (it == 15) "末列😀" else "数据$it") }),
        ))
        val document = document(listOf(table))
        val canonical = table.canonicalAnchorText()
        val offset = canonical.codePointCount(0, canonical.indexOf("末列😀"))
        val locator = ReaderDocumentSession(document, null, ReaderPresentation.PAGED).navigateToBlock(0, offset).locator
        val profile = mutableStateOf(DisplayProfile.STANDARD)
        val captured = AtomicReference<ReaderLocator>()
        val writes = AtomicInteger()
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 760.dp))) {
                ReaderTestTheme(environment(profile.value)) { TestReader(document, restoredLocator = locator,
                    onLocatorChanged = { writes.incrementAndGet() }, onToggleBookmark = captured::set) }
            }
        }
        fun assertColumns() {
            composeRule.onNodeWithText("头尾").assertIsDisplayed()
            val cell = composeRule.onNodeWithText("末列😀").assertIsDisplayed()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            cell.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val readableWidth = with(layout.layoutInput.density) { layout.layoutInput.style.fontSize.toPx() * 2 }
            assertTrue(layout.size.width >= readableWidth - 1)
        }
        assertColumns()
        composeRule.runOnIdle { profile.value = DisplayProfile.EINK }
        assertColumns()
        composeRule.onNodeWithContentDescription("添加书签").performClick()
        assertEquals(locator.bookmarkPositionKey(), captured.get().bookmarkPositionKey())
        assertEquals(0, writes.get())
    }

    @Test
    fun einkWideTableSeekPreviewsAndReleasesTheSameRestoredVisibleColumns() {
        val table = ReaderBlock.Table("wide-seek", (0..47).map { row ->
            ReaderTableRow((0..15).map { column ->
                ReaderTableCell(if (column == 15) "行${row}末列😀" else "行${row}列$column")
            })
        })
        val document = document(listOf(table))
        val canonical = table.canonicalAnchorText()
        val offset = canonical.codePointCount(0, canonical.indexOf("行0末列😀"))
        val locator = ReaderDocumentSession(document, null, ReaderPresentation.PAGED).navigateToBlock(0, offset).locator
        val writes = AtomicInteger()
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 760.dp))) {
                ReaderTestTheme(environment(DisplayProfile.EINK)) {
                    TestReader(document, restoredLocator = locator, onLocatorChanged = { writes.incrementAndGet() })
                }
            }
        }
        fun visibleCells(tag: String): List<String> {
            val cells = composeRule.onAllNodes(
                hasText("行", substring = true) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true,
            )
            return cells.fetchSemanticsNodes().filterIndexed { index, _ -> cells[index].isDisplayed() }
                .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        }
        val opening = visibleCells("reader-content-surface")
        assertTrue("Restoration must expose the actual late table column", opening.any { "末列" in it })
        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        slider.performTouchInput { down(Offset(width * 0.05f, centerY)); moveTo(Offset(width * 0.7f, centerY)) }
        val target = visibleCells("reader-content-surface")
        assertTrue(target.isNotEmpty() && target != opening)
        assertTrue("The actual target preview must retain the restored late table column", target.any { "末列" in it })
        assertEquals(0, writes.get())
        slider.performTouchInput { up() }
        composeRule.waitUntil(5_000) { writes.get() == 1 }
        assertEquals("The released page must display exactly the cells shown in its preview", target, visibleCells("reader-content-surface"))
        assertEquals(1, writes.get())
    }

    @Test
    fun failedReturnRetainsTheFirstOriginUntilThreeOrdinaryAdvances() {
        val session = ReaderDocumentSession(document(listOf(ReaderBlock.Paragraph("text", "甲😀乙丙丁"))), null, ReaderPresentation.PAGED)
        val first = session.previewPositionAtBlock(0, 1).locator
        val second = session.previewPositionAtBlock(0, 3).locator
        val state = ReaderReturnAnchorState()
        state.commitInternalJump(first, second)
        state.commitInternalJump(second, session.previewPositionAtBlock(0, 4).locator)
        assertEquals(first, state.anchor)
        state.returnToOrigin { state.navigationFailed() }
        state.navigationMounted()
        assertEquals(first, state.anchor)
        repeat(2) { state.ordinaryAdvance() }
        assertEquals(first, state.anchor)
        state.ordinaryAdvance()
        assertEquals(null, state.anchor)
    }


    private fun activate(text: String, label: String) {
        val action = composeRule.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == label }
        composeRule.runOnIdle { assertTrue(action.action()) }
    }

    @Composable
    private fun TestReader(
        document: ReaderDocument,
        requestedFlow: String = "paged",
        restoredLocator: ReaderLocator? = null,
        restorationGeneration: Long = 0L,
        imageStates: Map<String, CoverUiState> = emptyMap(),
        onLocatorChanged: (ReaderLocator) -> Unit = {},
        onNavigateLink: (ReaderLinkTarget) -> Unit = {},
        onReturnToOrigin: (ReaderLocator) -> Unit = {},
        onToggleBookmark: (ReaderLocator) -> Unit = {},
        onFlowOverrideChanged: (String?) -> Unit = {},
        onLocatorPrecisionChanged: (LocatorPrecision) -> Unit = {},
    ) {
        ReaderSurface(
            document = document, auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
            restoredLocator = restoredLocator, restorationGeneration = restorationGeneration,
            onLocatorChanged = { locator, precision -> onLocatorChanged(locator); onLocatorPrecisionChanged(precision) },
            navigationTargets = listOf(ReaderNavigationTarget(document.identity.contentId, document.title)),
            currentContentId = document.identity.contentId, bookmarks = emptyList(),
            onToggleBookmark = onToggleBookmark, onRemoveBookmark = {}, onSelectBookmark = {},
            requestedFlow = requestedFlow, hasFlowOverride = false, onFlowOverrideChanged = onFlowOverrideChanged,
            onSelectContent = {}, onNavigateLink = onNavigateLink, onNavigateUp = {},
            onReturnToOrigin = onReturnToOrigin, imageStates = imageStates,
            preferences = PortableReaderPreferences(flow = requestedFlow),
        )
    }

    @Composable
    private fun ReaderTestTheme(environment: DisplayEnvironment = environment(DisplayProfile.STANDARD), content: @Composable () -> Unit) {
        DisplayEnvironmentProvider(environment) { TsuyomiTheme(environment, content = content) }
    }

    private fun span(text: String, value: String, kind: ReaderInlineKind): ReaderSpan {
        val start = text.indexOf(value)
        return ReaderSpan(text.codePointCount(0, start), text.codePointCount(0, start + value.length), kind)
    }

    private fun document(blocks: List<ReaderBlock>) = ReaderDocument(
        DocumentIdentity(BookIdentity.Local(publicationId), "section", "normalizer-1"), "本地语义阅读", blocks,
    )

    private fun environment(profile: DisplayProfile) = DisplayEnvironment(
        preferences = DisplayPreferences(displayPreference = if (profile == DisplayProfile.EINK) DisplayPreference.EINK else DisplayPreference.STANDARD),
        effectiveProfile = profile,
        decisionReason = if (profile == DisplayProfile.EINK) DisplayDecisionReason.MANUAL_EINK else DisplayDecisionReason.MANUAL_STANDARD,
        detectedDeviceLabel = null, dynamicColorEligible = false, dynamicColorEffective = false, effectiveDarkTheme = false,
        // Deliberately inconsistent policy proves the renderer itself never animates EINK page turns.
        motionPolicy = MotionPolicy.STANDARD, redrawEpoch = 0L,
    )

    private companion object { const val publicationId = "12345678-1234-1234-1234-123456789abc" }
}
