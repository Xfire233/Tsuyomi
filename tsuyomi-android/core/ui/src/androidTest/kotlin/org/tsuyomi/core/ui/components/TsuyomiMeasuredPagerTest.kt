/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.ui.theme.TsuyomiTheme

@RunWith(AndroidJUnit4::class)
class TsuyomiMeasuredPagerTest {
    @get:Rule
    val composeRule = createComposeRule()
    @Test
    fun expandedSubcomposedIntroductionUsesCurrentFooterBudgetAndKeepsAnchorWhenViewportGrows() {
        val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK, motionPolicy = MotionPolicy.INSTANT)
        val expanded = mutableStateOf(false)
        val height = mutableStateOf(320.dp)
        val lineStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        lateinit var outer: TsuyomiMeasuredPagerState
        lateinit var introduction: TsuyomiMeasuredPagerState
        fun assertTerminalCollapse() {
            val action = composeRule.onNodeWithText("Collapse introduction")
            val displayed = action.isDisplayed()
            assertTrue("Last must render the terminal action; parent=${outer.currentPage}/${outer.pageCount}, " +
                "child=${introduction.currentAnchorKey}:${introduction.currentPage}/${introduction.pageCount}, " +
                "rawLast=${introduction.measuredPages?.lastOrNull()}, firstHeight=${introduction.measuredFirstPageHeight}, " +
                "lastHeight=${introduction.measuredLastPageHeight}, visibleHeight=${introduction.measuredVisibleHeight}", displayed)
            assertEquals(requireNotNull(outer.pageCount), outer.currentPage)
            val bounds = action.fetchSemanticsNode().boundsInRoot
            val footer = composeRule.onNodeWithText("上一页").fetchSemanticsNode().boundsInRoot
            assertTrue("The complete terminal action must remain above the footer", bounds.bottom <= footer.top)
        }
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    outer = rememberTsuyomiMeasuredPagerState()
                    TsuyomiMeasuredPager(2, { if (it == 0) "identity" else "introduction" }, { index ->
                        if (index == 0) Text("Identity", Modifier.height(80.dp))
                        else if (!expanded.value) TsuyomiButton("Expand introduction", { expanded.value = true })
                        else SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
                            // Keep intentional nested subcomposition and the pager's live inherited budget.
                            val child = subcompose(Unit) {
                                introduction = rememberTsuyomiMeasuredPagerState()
                                TsuyomiMeasuredPager(122, { child -> when (child) {
                                    0 -> "introduction:header"
                                    121 -> "introduction:collapse"
                                    else -> "introduction:line:${child - 1}"
                                } }, { child -> when (child) {
                                    0 -> Text("Introduction", style = MaterialTheme.typography.titleLarge)
                                    121 -> TsuyomiButton("Collapse introduction", { expanded.value = false })
                                    else -> Text("intro-line-${child - 1}", style = lineStyle)
                                } }, state = introduction, fillViewport = false)
                            }.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
                            layout(maxOf(constraints.minWidth, child.width), maxOf(constraints.minHeight, child.height)) {
                                child.placeRelative(0, 0)
                            }
                        }
                    }, Modifier.size(320.dp, height.value), state = outer)
                }
            }
        }
        composeRule.onNodeWithText("下一页").assertDoesNotExist()
        composeRule.onNodeWithText("Expand introduction").performClick()
        composeRule.runOnIdle {
            assertTrue("Expanded fixture needs >5 actual pages; parent=${outer.pageCount}, child=${introduction.pageCount}, rawChild=${introduction.measuredPageCount}",
                requireNotNull(outer.pageCount) > PAGINATION_JUMP_THRESHOLD)
        }
        composeRule.onNodeWithText("末页").assertIsEnabled().performClick()
        assertTerminalCollapse()
        composeRule.onNodeWithText("末页").assertIsNotEnabled()
        composeRule.onNodeWithText("下一页").assertIsNotEnabled()
        if (!composeRule.onNodeWithText("intro-line-119").isDisplayed()) {
            composeRule.onNodeWithText("上一页").performClick()
            composeRule.onNodeWithText("intro-line-119").assertIsDisplayed()
            composeRule.onNodeWithText("下一页").performClick()
            assertTerminalCollapse()
        }
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("Identity").assertIsDisplayed()
        composeRule.onNodeWithText("下一页").performClick()
        composeRule.onNodeWithText("下一页").performClick()
        val anchor = introduction.currentAnchorKey
        val firstLine = visibleLines("intro-line-").first()
        composeRule.runOnIdle { height.value = 440.dp }
        composeRule.onNodeWithText(firstLine).assertIsDisplayed()
        assertEquals(anchor, introduction.currentAnchorKey)
        composeRule.runOnIdle {
            assertTrue("Grown fixture needs >5 actual pages; parent=${outer.pageCount}, child=${introduction.pageCount}, rawChild=${introduction.measuredPageCount}",
                requireNotNull(outer.pageCount) > PAGINATION_JUMP_THRESHOLD)
        }
        composeRule.onNodeWithText("末页").performClick()
        assertTerminalCollapse()
        if (!composeRule.onNodeWithText("intro-line-119").isDisplayed()) {
            composeRule.onNodeWithText("上一页").performClick()
            composeRule.onNodeWithText("intro-line-119").assertIsDisplayed()
            composeRule.onNodeWithText("下一页").performClick()
            assertTerminalCollapse()
        }
        composeRule.onNodeWithText("首页").performClick()
        val observed = mutableListOf<String>()
        var pages = 1
        while (true) {
            observed += visibleLines("intro-line-")
            val next = composeRule.onNodeWithText("下一页")
            if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
            next.performClick()
            assertTrue(++pages <= 123)
        }
        assertEquals((0..119).map { "intro-line-$it" }, observed)
        assertEquals(requireNotNull(outer.pageCount), pages)
    }


    @Test
    fun importShapedNestedChapterPlanLastIsOneJumpToFinalChapterAndTrailingDraft() {
        val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK, motionPolicy = MotionPolicy.INSTANT)
        val draft = mutableStateOf("128")
        lateinit var outer: TsuyomiMeasuredPagerState
        lateinit var chapters: TsuyomiMeasuredPagerState
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    outer = rememberTsuyomiMeasuredPagerState()
                    TsuyomiMeasuredPager(3, { listOf("identity", "headings", "review-actions")[it] }, { index ->
                        when (index) {
                            0 -> Text("Import review identity", Modifier.height(72.dp))
                            1 -> {
                                chapters = rememberTsuyomiMeasuredPagerState()
                                TsuyomiMeasuredPager(80, { "heading-$it" }, { chapter ->
                                    Column {
                                        TsuyomiCheckboxRow("import-chapter-$chapter", true, {})
                                        Text("Chapter title $chapter", style = MaterialTheme.typography.bodyLarge)
                                    }
                                }, state = chapters, fillViewport = false)
                            }
                            else -> Column {
                                TsuyomiTextField(draft.value, { draft.value = it }, label = "Paragraphs per section", singleLine = true)
                                TsuyomiButton("Confirm import", {})
                            }
                        }
                    }, Modifier.size(440.dp, 640.dp), state = outer, verticalSpacing = 8.dp)
                }
            }
        }
        composeRule.onNodeWithText("首页").assertIsNotEnabled()
        val total = requireNotNull(outer.pageCount)
        assertTrue(total > 5)
        composeRule.onNodeWithText("末页").performClick()
        composeRule.onNodeWithText("import-chapter-79").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm import").assertIsDisplayed()
        composeRule.onNodeWithText("末页").assertIsNotEnabled()
        composeRule.onNodeWithText("下一页").assertIsNotEnabled()
        assertEquals(total, outer.currentPage)
        assertEquals(requireNotNull(chapters.pageCount), chapters.currentPage)
        composeRule.onNodeWithText("Paragraphs per section").performTextInputSelection(TextRange(0))
        composeRule.onNodeWithText("Paragraphs per section").performTextInput("4")
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("Import review identity").assertIsDisplayed()
        composeRule.onNodeWithText("末页").performClick()
        composeRule.onNodeWithText("import-chapter-79").assertIsDisplayed()
        composeRule.onNodeWithText("Paragraphs per section").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText, AnnotatedString("4128")))
        assertEquals(total, outer.currentPage)
    }

    @Test
    fun existingPaginationOnBothProfilesShowsOnlyTruthfulPairedEndpointsAboveFive() {
        val profile = mutableStateOf(DisplayProfile.EINK)
        val count = mutableStateOf<Int?>(5)
        val page = mutableStateOf(1)
        composeRule.setContent {
            val environment = standardEnvironment.copy(effectiveProfile = profile.value)
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    PaginationBar(page.value, count.value, { page.value-- }, { page.value++ },
                        modifier = Modifier.width(320.dp), hasNextPage = true,
                        onFirst = { page.value = 1 }, onLast = { page.value = requireNotNull(count.value) })
                }
            }
        }
        composeRule.onNodeWithText("首页").assertDoesNotExist()
        composeRule.onNodeWithText("末页").assertDoesNotExist()
        for (display in listOf(DisplayProfile.EINK, DisplayProfile.STANDARD)) {
            composeRule.runOnIdle { profile.value = display; count.value = 6; page.value = 1 }
            composeRule.onNodeWithText("首页").assertIsNotEnabled()
            composeRule.onNodeWithText("末页").assertIsEnabled().performClick()
            assertEquals(6, page.value)
            composeRule.onNodeWithText("末页").assertIsNotEnabled()
            composeRule.onNodeWithText("首页").assertIsEnabled().performClick()
            assertEquals(1, page.value)
            composeRule.runOnIdle { count.value = null }
            composeRule.onNodeWithText("首页").assertDoesNotExist()
            composeRule.onNodeWithText("末页").assertDoesNotExist()
        }
    }

    @Test
    fun nestedTextAndTrailingActionUseTheSameTruthfulLastPageAndOneControlLane() {
        val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK, motionPolicy = MotionPolicy.INSTANT)
        val text = (0..89).joinToString("\n") { "endpoint-line-${it.toString().padStart(2, '0')}" }
        lateinit var state: TsuyomiMeasuredPagerState
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    state = rememberTsuyomiMeasuredPagerState()
                    TsuyomiMeasuredPager(2, { if (it == 0) "nested-text" else "trailing-action" }, { index ->
                        if (index == 0) TsuyomiMeasuredTextPager(text, style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
                            fillViewport = false, alwaysShowPageControls = false)
                        else TsuyomiButton("Finish passage", {})
                    }, Modifier.size(320.dp, 320.dp), state = state)
                }
            }
        }
        composeRule.onAllNodesWithText("首页").assertCountEquals(1)
        val total = requireNotNull(state.pageCount)
        composeRule.onNodeWithText("末页").performClick()
        composeRule.onNodeWithText("Finish passage").assertIsDisplayed()
        composeRule.onNodeWithText("endpoint-line-89", substring = true).assertIsDisplayed()
        val actionBounds = composeRule.onNodeWithText("Finish passage").fetchSemanticsNode().boundsInRoot
        val previousBounds = composeRule.onNodeWithText("上一页").fetchSemanticsNode().boundsInRoot
        val nextBounds = composeRule.onNodeWithText("下一页").fetchSemanticsNode().boundsInRoot
        val firstBounds = composeRule.onNodeWithText("首页").fetchSemanticsNode().boundsInRoot
        val lastBounds = composeRule.onNodeWithText("末页").fetchSemanticsNode().boundsInRoot
        val statusBounds = composeRule.onNodeWithText("$total / $total").fetchSemanticsNode().boundsInRoot
        assertTrue("The complete terminal action must stay above the persistent footer", actionBounds.bottom <= previousBounds.top)
        assertEquals(previousBounds.center.y, statusBounds.center.y, 1f)
        assertEquals(previousBounds.top, nextBounds.top, 0f)
        assertEquals(firstBounds.top, lastBounds.top, 0f)
        assertTrue("First/last must follow the ordinary previous/status/next group", firstBounds.top > previousBounds.bottom)
        composeRule.onNodeWithText("末页").assertIsNotEnabled()
        composeRule.onNodeWithText("下一页").assertIsNotEnabled()
        assertEquals(total, state.currentPage)
        composeRule.onNodeWithText("首页").performClick()
        val observed = mutableListOf<String>()
        var pageCount = 0
        while (true) {
            observed += visibleLines("endpoint-line-")
            pageCount++
            composeRule.onAllNodesWithText("下一页").assertCountEquals(1)
            val next = composeRule.onNodeWithText("下一页")
            if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
            next.performClick()
            assertTrue(pageCount <= 91)
        }
        assertEquals((0..89).map { "endpoint-line-${it.toString().padStart(2, '0')}" }, observed)
        assertEquals(total, pageCount)
    }

    @Test
    fun finiteVariableHeightPlanJumpsToRealEndpointsAndTraversesEveryItemOnce() {
        val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK, motionPolicy = MotionPolicy.INSTANT)
        val restoration = StateRestorationTester(composeRule)
        lateinit var state: TsuyomiMeasuredPagerState
        restoration.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    state = rememberTsuyomiMeasuredPagerState()
                    TsuyomiMeasuredPager(
                        itemCount = 28,
                        itemKey = { "variable-item-$it" },
                        itemContent = { Text("variable-item-$it", Modifier.height((44 + it % 3 * 16).dp)) },
                        modifier = Modifier.size(320.dp, 360.dp).testTag("finite-items"),
                        state = state,
                        verticalSpacing = 8.dp,
                    )
                }
            }
        }
        composeRule.onNodeWithText("首页").assertIsNotEnabled()
        val pageCount = requireNotNull(state.pageCount)
        assertTrue(pageCount > 5)
        composeRule.onNodeWithText("末页").assertIsEnabled().performClick()
        composeRule.onNodeWithText("variable-item-27").assertIsDisplayed()
        composeRule.onNodeWithText("末页").assertIsNotEnabled()
        composeRule.onNodeWithText("下一页").assertIsNotEnabled()
        assertEquals(pageCount, state.currentPage)
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("variable-item-27").assertIsDisplayed()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("variable-item-0").assertIsDisplayed()
        composeRule.onNodeWithText("上一页").assertIsNotEnabled()
        val observed = mutableListOf<String>()
        var pages = 0
        while (true) {
            observed += visibleLines("variable-item-")
            pages++
            val next = composeRule.onNodeWithText("下一页")
            if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
            next.performClick()
            assertTrue(pages <= 28)
        }
        assertEquals((0..27).map { "variable-item-$it" }, observed)
        assertEquals(pageCount, pages)
        val viewport = composeRule.onNodeWithTag("finite-items").fetchSemanticsNode().boundsInRoot
        for (label in listOf("上一页", "下一页", "首页", "末页")) {
            val node = composeRule.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode()
            assertTrue(node.boundsInRoot.top >= viewport.top && node.boundsInRoot.bottom <= viewport.bottom)
            assertTrue(node.touchBoundsInRoot.height >= with(node.layoutInfo.density) { 48.dp.toPx() })
        }
    }

    @Test
    fun finiteTextPlanJumpsToLastCompleteLinesAndRestoresItsCharacterAnchor() {
        val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK, motionPolicy = MotionPolicy.INSTANT)
        val restoration = StateRestorationTester(composeRule)
        val text = (0..89).joinToString("\n") { "finite-line-${it.toString().padStart(2, '0')}" }
        restoration.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    TsuyomiMeasuredTextPager(text, Modifier.size(320.dp, 320.dp),
                        style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp))
                }
            }
        }
        composeRule.onNodeWithText("首页").assertIsNotEnabled()
        composeRule.onNodeWithText("末页").performClick()
        composeRule.onNodeWithText("finite-line-89", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("末页").assertIsNotEnabled()
        val lastLines = visibleLines("finite-line-")
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(lastLines, visibleLines("finite-line-"))
        composeRule.onNodeWithText("首页").performClick()
        val observed = mutableListOf<String>()
        var pages = 0
        while (true) {
            observed += visibleLines("finite-line-")
            val next = composeRule.onNodeWithText("下一页")
            if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
            next.performClick()
            assertTrue(++pages <= 90)
        }
        assertEquals((0..89).map { "finite-line-${it.toString().padStart(2, '0')}" }, observed)
    }

    @Test
    fun legacyOneItemBoundaryIsDiscardedWithoutLosingItsCurrentIdentity() {
        val state = requireNotNull(TsuyomiMeasuredPagerState.Saver.restore(listOf(
            "anchors", listOf("module-0", "0"),
            "page", 1,
            "ends", listOf("module-0", "module-1", "1"),
        )))
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    TsuyomiMeasuredPager(3, { "module-$it" }, { Text("module-$it", Modifier.height(60.dp)) },
                        Modifier.size(320.dp, 300.dp), state = state)
                }
            }
        }
        for (index in 0..2) composeRule.onNodeWithText("module-$index").assertIsDisplayed()
        composeRule.onNodeWithText("Previous").assertDoesNotExist()
        composeRule.onNodeWithText("上一页").assertDoesNotExist()
        assertEquals("module-0", state.currentAnchorKey)
    }

    @Test
    fun restoredIndexedHistoryReflowsFittingModulesWhenViewportGrows() {
        val restoration = StateRestorationTester(composeRule)
        val height = mutableStateOf(220.dp)
        restoration.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    TsuyomiMeasuredPager(8, { "reflow-$it" }, { Text("reflow-$it", Modifier.height(60.dp)) },
                        Modifier.size(320.dp, height.value), state = rememberTsuyomiMeasuredPagerState("reflow-5"))
                }
            }
        }
        composeRule.onNodeWithText("上一页").performClick()
        composeRule.onNodeWithText("reflow-3").assertIsDisplayed()
        composeRule.onNodeWithText("reflow-4").assertIsDisplayed()
        composeRule.onNodeWithText("reflow-5").assertIsNotDisplayed()
        composeRule.runOnIdle { height.value = 600.dp }
        restoration.emulateSavedInstanceStateRestore()
        for (index in 0..7) composeRule.onNodeWithText("reflow-$index").assertIsDisplayed()
        composeRule.onNodeWithText("上一页").assertDoesNotExist()
        composeRule.onNodeWithText("下一页").assertDoesNotExist()
    }

    @Test
    fun fittingControlGroupKeepsIntrinsicWidthsAndSharedRows() {
        val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK, motionPolicy = MotionPolicy.INSTANT)
        val labels = listOf("A", "BBB", "C")
        val activated = mutableListOf<String>()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    TsuyomiMeasuredPager(
                        itemCount = labels.size,
                        itemKey = { labels[it] },
                        itemContent = { index -> TsuyomiButton(labels[index], { activated += labels[index] }) },
                        modifier = Modifier.size(320.dp, 240.dp).testTag("control-group"),
                        wrapItems = true,
                        horizontalSpacing = 8.dp,
                        verticalSpacing = 8.dp,
                    )
                }
            }
        }
        val host = composeRule.onNodeWithTag("control-group").fetchSemanticsNode().boundsInRoot
        val controls = labels.map { composeRule.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        assertTrue(controls.all { it.width < host.width / 2 })
        assertEquals(controls[0].top, controls[1].top, 1f)
        assertTrue(controls[0].right < controls[1].left)
        assertTrue(controls.all { it.left >= host.left && it.right <= host.right && it.bottom <= host.bottom })
        composeRule.onAllNodesWithText("上一页").assertCountEquals(0)
        composeRule.onAllNodesWithText("下一页").assertCountEquals(0)
        labels.forEach { composeRule.onNodeWithText(it).performClick() }
        composeRule.runOnIdle { assertEquals(labels, activated) }
    }

    @Test
    fun einkPaginationMeasuresCompleteCentredGroupsBeforeWrapping() {
        val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK, motionPolicy = MotionPolicy.INSTANT)
        val width = mutableStateOf(320.dp)
        val fontScale = mutableStateOf(1f)
        var previousActions = 0
        var nextActions = 0
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.value)) {
                    TsuyomiTheme(environment) {
                        PaginationBar(
                            page = 2,
                            pageCount = null,
                            onPrevious = { previousActions++ },
                            onNext = { nextActions++ },
                            hasNextPage = true,
                            modifier = Modifier.width(width.value).testTag("pagination-group"),
                        )
                    }
                }
            }
        }
        for ((laneWidth, scale) in listOf(320.dp to 1f, 216.dp to 2f, 160.dp to 2f)) {
            composeRule.runOnIdle { width.value = laneWidth; fontScale.value = scale }
            val host = composeRule.onNodeWithTag("pagination-group").fetchSemanticsNode().boundsInRoot
            val previousNode = composeRule.onNodeWithText("上一页").assertIsDisplayed().fetchSemanticsNode()
            val nextNode = composeRule.onNodeWithText("下一页").assertIsDisplayed().fetchSemanticsNode()
            val previous = previousNode.boundsInRoot
            val next = nextNode.boundsInRoot
            val status = composeRule.onNodeWithText("第", substring = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            for (node in listOf(previousNode, nextNode)) {
                val minimumHeight = with(node.layoutInfo.density) { 48.dp.toPx() }
                assertTrue(node.boundsInRoot.height >= minimumHeight)
                assertTrue(node.touchBoundsInRoot.height >= minimumHeight)
                assertTrue(node.boundsInRoot.left >= host.left && node.boundsInRoot.right <= host.right)
                assertTrue(node.boundsInRoot.top >= host.top && node.boundsInRoot.bottom <= host.bottom)
            }
            if (scale == 1f) {
                assertEquals(previous.center.y, status.center.y, 1f)
                assertEquals(previous.top, next.top, 0f)
            } else {
                assertEquals(host.center.x, status.center.x, 1f)
                assertTrue(status.bottom < previous.top)
                if (laneWidth == 216.dp) {
                    assertEquals(previous.top, next.top, 0f)
                } else {
                    assertEquals(host.center.x, previous.center.x, 1f)
                    assertEquals(host.center.x, next.center.x, 1f)
                    assertTrue(previous.bottom < next.top)
                }
            }
            assertEquals(host.center.x, (previous.left + next.right) / 2, 1f)
            composeRule.onNodeWithText("上一页").performClick()
            composeRule.onNodeWithText("下一页").performClick()
        }
        composeRule.runOnIdle {
            assertEquals(3, previousActions)
            assertEquals(3, nextActions)
        }
    }

    @Test
    fun indexedPagerShowsWholeRowsAndMovesOnlyWithItsButtons() {
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    Box(Modifier.width(320.dp).height(220.dp)) {
                        TsuyomiMeasuredPager(
                            itemCount = 5,
                            itemKey = { "item-$it" },
                            itemContent = { index ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .testTag("page-item-$index"),
                                ) {
                                    Text("Item $index")
                                }
                            },
                            pageControls = { page, canGoPrevious, canGoNext, onPrevious, onNext, _, _, _ ->
                                TestPagerControls(page, canGoPrevious, canGoNext, onPrevious, onNext)
                            },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("page-item-0").assertIsDisplayed()
        composeRule.onNodeWithTag("page-item-1").assertIsDisplayed()
        composeRule.onNodeWithTag("page-item-2").assertIsNotDisplayed()
        composeRule.onNodeWithText("Next").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("page-item-0").assertIsNotDisplayed()
        composeRule.onNodeWithTag("page-item-2").assertIsDisplayed()
    }

    @Test
    fun textPagerKeepsMeasuredLinesWholeAcrossPages() {
        val paragraph = (0..19).joinToString("\n") { "line-${it.toString().padStart(2, '0')}" }
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    Box(Modifier.size(width = 320.dp, height = 220.dp)) {
                        TsuyomiMeasuredTextPager(
                            text = paragraph,
                            modifier = Modifier.fillMaxSize(),
                            style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
                            pageControls = { page, canGoPrevious, canGoNext, onPrevious, onNext, _, _, _ ->
                                TestPagerControls(page, canGoPrevious, canGoNext, onPrevious, onNext)
                            },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("line-00", substring = true).assertExists()
        composeRule.onNodeWithText("line-10", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Next").assertIsEnabled().performClick()
        composeRule.onNodeWithText("line-00", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("line-10", substring = true).assertExists()
    }


    @Test
    fun eInkDialogKeepsPagedBodyAndActionInFiniteViewport() {
        val environment = standardEnvironment.copy(
            effectiveProfile = DisplayProfile.EINK,
            motionPolicy = MotionPolicy.INSTANT,
        )
        val paragraph = (0..19).joinToString("\n") { "dialog-line-${it.toString().padStart(2, '0')}" }
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    Box(Modifier.size(width = 320.dp, height = 420.dp)) {
                        TsuyomiDialogPane(
                            title = "License",
                            body = {
                                TsuyomiMeasuredTextPager(
                                    text = paragraph,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
                                    pageControls = { page, canGoPrevious, canGoNext, onPrevious, onNext, _, _, _ ->
                                        TestPagerControls(page, canGoPrevious, canGoNext, onPrevious, onNext)
                                    },
                                )
                            },
                            confirmLabel = "Done",
                            onConfirm = {},
                            fullWindow = true,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("License").assertIsDisplayed()
        val observed = mutableListOf<String>()
        var transitions = 0
        while (true) {
            observed += visibleLines("dialog-line-")
            composeRule.onAllNodesWithText("下一页").assertCountEquals(1)
            val next = composeRule.onNodeWithText("下一页")
            if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
            next.performClick()
            assertTrue("Dialog did not reach its real endpoint", ++transitions <= 25)
        }
        assertEquals((0..19).map { "dialog-line-${it.toString().padStart(2, '0')}" }, observed)
        composeRule.onNodeWithText("Done").assertIsDisplayed()
        composeRule.onNodeWithText("下一页").assertIsNotEnabled()
    }

    @Test
    fun naturalPagerMeasuresOnlyItsCompleteContentAndControls() {
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    Box(Modifier.size(320.dp, 480.dp).testTag("natural-host")) {
                        TsuyomiMeasuredPager(
                            itemCount = 2,
                            itemKey = { "natural-$it" },
                            itemContent = { Text("Natural item $it", Modifier.height(48.dp)) },
                            modifier = Modifier.testTag("natural-pager"),
                            fillViewport = false,
                            pageControls = { page, previous, next, onPrevious, onNext, _, _, _ ->
                                TestPagerControls(page, previous, next, onPrevious, onNext)
                            },
                        )
                    }
                }
            }
        }
        val host = composeRule.onNodeWithTag("natural-host").fetchSemanticsNode().boundsInRoot
        val pager = composeRule.onNodeWithTag("natural-pager").fetchSemanticsNode().boundsInRoot
        assertTrue(pager.height < host.height)
        composeRule.onNodeWithText("Natural item 0").assertIsDisplayed()
        composeRule.onNodeWithText("Natural item 1").assertIsDisplayed()
        composeRule.onNodeWithText("Previous").assertDoesNotExist()
        composeRule.onNodeWithText("Next").assertDoesNotExist()
    }

    @Test
    fun restoredIdentityJumpCanPageBackAndReturnWithoutOverlappingTheTarget() {
        val restoration = StateRestorationTester(composeRule)
        val keys = mutableStateOf(List(8) { "identity-$it" })
        restoration.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    TsuyomiMeasuredPager(
                        itemCount = keys.value.size,
                        itemKey = { keys.value[it] },
                        itemContent = { Text(keys.value[it], Modifier.height(60.dp)) },
                        state = rememberTsuyomiMeasuredPagerState("identity-5"),
                        modifier = Modifier.size(320.dp, 220.dp),
                        pageControls = { page, previous, next, onPrevious, onNext, _, _, _ ->
                            TestPagerControls(page, previous, next, onPrevious, onNext)
                        },
                    )
                }
            }
        }
        composeRule.onNodeWithText("identity-5").assertIsDisplayed()
        composeRule.onNodeWithText("Previous").assertIsEnabled().performClick()
        composeRule.onNodeWithText("identity-3").assertIsDisplayed()
        composeRule.onNodeWithText("identity-4").assertIsDisplayed()
        composeRule.onNodeWithText("identity-5").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle { keys.value = listOf("inserted") + keys.value }
        composeRule.onNodeWithText("identity-3").assertIsDisplayed()
        composeRule.onNodeWithText("identity-4").assertIsDisplayed()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("identity-5").assertIsDisplayed()
        composeRule.onNodeWithText("identity-4").assertDoesNotExist()
        composeRule.onNodeWithText("Previous").performClick()
        composeRule.onNodeWithText("identity-3").assertIsDisplayed()
        composeRule.onNodeWithText("identity-4").assertIsDisplayed()
        composeRule.onNodeWithText("Previous").performClick()
        composeRule.onNodeWithText("identity-1").assertIsDisplayed()
        composeRule.onNodeWithText("identity-2").assertIsDisplayed()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("identity-3").assertIsDisplayed()
        composeRule.onNodeWithText("identity-4").assertIsDisplayed()
        composeRule.onNodeWithText("identity-5").assertDoesNotExist()
    }

    @Test
    fun nestedLongTextSharesOneControlLaneAndRestoresItsMeasuredSegmentAtDoubleFont() {
        val restoration = StateRestorationTester(composeRule)
        val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK, motionPolicy = MotionPolicy.INSTANT)
        val paragraph = (0..29).joinToString("\n") { "nested-line-${it.toString().padStart(2, '0')}" }
        restoration.setContent {
            DisplayEnvironmentProvider(environment) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    TsuyomiTheme(environment) {
                        TsuyomiMeasuredPager(
                            itemCount = 2,
                            itemKey = { if (it == 0) "passage" else "action" },
                            itemContent = { index ->
                                if (index == 0) TsuyomiMeasuredTextPager(
                                    paragraph,
                                    style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
                                    fillViewport = false,
                                    alwaysShowPageControls = false,
                                ) else TsuyomiButton("Finish", {})
                            },
                            modifier = Modifier.size(280.dp, 240.dp).testTag("nested-viewport"),
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("上一页").assertIsNotEnabled()
        val observed = mutableListOf<String>()
        var transitions = 0
        while (true) {
            observed += visibleLines("nested-line-")
            composeRule.onAllNodesWithText("上一页").assertCountEquals(1)
            composeRule.onAllNodesWithText("下一页").assertCountEquals(1)
            val viewport = composeRule.onNodeWithTag("nested-viewport").fetchSemanticsNode().boundsInRoot
            for (node in composeRule.onAllNodesWithText("nested-line-", substring = true).fetchSemanticsNodes()) {
                assertTrue(node.boundsInRoot.top >= viewport.top && node.boundsInRoot.bottom <= viewport.bottom)
            }
            val next = composeRule.onNodeWithText("下一页")
            if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
            next.performClick()
            assertTrue("Nested passage did not reach its endpoint", ++transitions <= 35)
        }
        assertEquals((0..29).map { "nested-line-${it.toString().padStart(2, '0')}" }, observed)
        composeRule.onNodeWithText("Finish").assertIsDisplayed()
        composeRule.onNodeWithText("上一页").performClick()
        val savedSegment = visibleLines("nested-line-")
        assertTrue(savedSegment.isNotEmpty())
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(savedSegment, visibleLines("nested-line-"))
        composeRule.onNodeWithText("上一页").performClick()
        assertTrue(savedSegment != visibleLines("nested-line-"))
        composeRule.onNodeWithText("下一页").performClick()
        assertEquals(savedSegment, visibleLines("nested-line-"))
    }

    @Test
    fun previousTextPageKeepsItsForwardBoundaryAfterViewportGrows() {
        val height = mutableStateOf(220.dp)
        val paragraph = (0..39).joinToString("\n") { "history-line-${it.toString().padStart(2, '0')}" }
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    Box(Modifier.size(width = 320.dp, height = height.value)) {
                        TsuyomiMeasuredTextPager(
                            text = paragraph,
                            modifier = Modifier.fillMaxSize(),
                            style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
                            pageControls = { page, previous, next, onPrevious, onNext, _, _, _ ->
                                TestPagerControls(page, previous, next, onPrevious, onNext)
                            },
                        )
                    }
                }
            }
        }
        val firstPage = visibleLines("history-line-")
        composeRule.onNodeWithText("Next").performClick()
        val nextPage = visibleLines("history-line-")
        composeRule.runOnIdle { height.value = 400.dp }
        composeRule.onNodeWithText("Previous").performClick()
        assertEquals(firstPage, visibleLines("history-line-"))
        composeRule.onNodeWithText("Next").performClick()
        assertEquals(nextPage.first(), visibleLines("history-line-").first())
    }

    @Test
    fun removingAnotherItemKeepsTheCurrentNestedTextSegment() {
        val extra = mutableStateOf(true)
        val paragraph = (0..59).joinToString("\n") { "retained-line-${it.toString().padStart(2, '0')}" }
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    val keys = if (extra.value) listOf("text", "extra") else listOf("text")
                    Column(Modifier.size(320.dp, 320.dp)) {
                        TsuyomiButton("Remove extra", { extra.value = false })
                        TsuyomiMeasuredPager(
                            itemCount = keys.size,
                            itemKey = { keys[it] },
                            modifier = Modifier.weight(1f),
                            itemContent = { index ->
                                if (keys[index] == "text") {
                                    TsuyomiMeasuredTextPager(
                                        text = paragraph,
                                        style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
                                        fillViewport = false,
                                    )
                                } else {
                                    Text("Extra")
                                }
                            },
                            pageControls = { page, previous, next, onPrevious, onNext, _, _, _ ->
                                TestPagerControls(page, previous, next, onPrevious, onNext)
                            },
                        )
                    }
                }
            }
        }
        repeat(3) { composeRule.onNodeWithText("Next").assertIsEnabled().performClick() }
        val savedSegment = visibleLines("retained-line-")
        assertTrue("The pager must have left its initial segment", savedSegment.first() != "retained-line-00")
        composeRule.onNodeWithText("Remove extra").performClick()
        assertEquals(savedSegment, visibleLines("retained-line-"))
        composeRule.onNodeWithText("Previous").assertIsEnabled()
    }

    @Test
    fun reorderedAndShrinkingItemsKeepTheVisibleIdentityAndCorrectEndpoints() {
        val items = mutableStateOf((0..19).map { "item-$it" })
        val pagerState = TsuyomiMeasuredPagerState(null)
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    val snapshot = items.value
                    Column(Modifier.size(320.dp, 360.dp)) {
                        TsuyomiButton("Reverse", { items.value = snapshot.reversed() })
                        TsuyomiMeasuredPager(
                            itemCount = snapshot.size,
                            itemKey = { items.value[it] },
                            itemContent = { index ->
                                val identity = snapshot[index]
                                TsuyomiButton(identity, { items.value = listOf(identity) })
                            },
                            modifier = Modifier.weight(1f),
                            state = pagerState,
                            pageControls = { page, previous, next, onPrevious, onNext, _, _, _ ->
                                TestPagerControls(page, previous, next, onPrevious, onNext)
                            },
                        )
                    }
                }
            }
        }
        var transitions = 0
        while (runCatching { composeRule.onNodeWithText("item-19").assertIsDisplayed() }.isFailure) {
            composeRule.onNodeWithText("Next").assertIsEnabled().performClick()
            assertTrue("The last item must remain reachable", ++transitions < 20)
        }
        composeRule.onNodeWithText("item-19").assertIsDisplayed()
        val retainedIdentity = requireNotNull(pagerState.currentAnchorKey)
        composeRule.onNodeWithText("Reverse").performClick()
        val previousIdentity = items.value[items.value.indexOf(retainedIdentity) - 1]
        composeRule.onNodeWithText("Previous").assertIsEnabled().performClick()
        composeRule.onNodeWithText(previousIdentity).assertIsDisplayed()
        composeRule.onNodeWithText(retainedIdentity).assertIsNotDisplayed()
        composeRule.onNodeWithText("Next").assertIsEnabled().performClick()
        composeRule.onNodeWithText(retainedIdentity).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(retainedIdentity).assertIsDisplayed()
        composeRule.onNodeWithText("item-0").assertDoesNotExist()
        composeRule.onNodeWithText("Previous").assertDoesNotExist()
        composeRule.onNodeWithText("Next").assertDoesNotExist()
        assertEquals(listOf(retainedIdentity), items.value)
    }

    @Test
    fun unchangedItemIdentitiesKeepEditingFocusAndCursorWhenContentChanges() {
        val input = mutableStateOf("draft")
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    val snapshot = listOf("editor" to input.value)
                    Box(Modifier.size(320.dp, 320.dp)) {
                        TsuyomiMeasuredPager(
                            itemCount = snapshot.size,
                            itemKey = { snapshot[it].first },
                            itemContent = { index ->
                                TsuyomiTextField(
                                    value = snapshot[index].second,
                                    onValueChange = { input.value = it },
                                    label = "Draft",
                                )
                            },
                        )
                    }
                }
            }
        }
        val field = composeRule.onNodeWithText("Draft")
        // Selection input requests focus semantically; a physical tap also places the cursor.
        field.performTextInputSelection(TextRange(2))
        field.assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("draft")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(2)))
        field.performTextInput("x")
        field.assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("drxaft")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(3)))
        field.performTextInput("y")
        field.assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("drxyaft")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(4)))
    }

    @Test
    fun dialogKeepsItsEinkTextPageAcrossProfileChanges() {
        val einkEnvironment = standardEnvironment.copy(
            effectiveProfile = DisplayProfile.EINK,
            motionPolicy = MotionPolicy.INSTANT,
        )
        val environment = mutableStateOf(einkEnvironment)
        val paragraph = (0..119).joinToString("\n") { "profile-line-${it.toString().padStart(3, '0')}" }
        composeRule.setContent {
            DisplayEnvironmentProvider(environment.value) {
                TsuyomiTheme(environment.value) {
                    TsuyomiDialog(title = "License", text = paragraph, onDismissRequest = {})
                }
            }
        }
        repeat(2) { composeRule.onNodeWithText("下一页").assertIsEnabled().performClick() }
        val savedPage = visibleLines("profile-line-")
        assertTrue("Dialog paging must leave its initial text anchor", savedPage.first() != "profile-line-000")
        composeRule.runOnIdle { environment.value = standardEnvironment }
        composeRule.waitForIdle()
        composeRule.runOnIdle { environment.value = einkEnvironment }
        composeRule.waitForIdle()
        assertEquals(savedPage, visibleLines("profile-line-"))
        composeRule.onNodeWithText("上一页").assertIsEnabled()
    }

    private fun visibleLines(prefix: String): List<String> =
        composeRule.onAllNodesWithText(prefix, substring = true).fetchSemanticsNodes()
            .filter { node ->
                runCatching {
                    composeRule.onNodeWithText(node.config[SemanticsProperties.Text].single().text).assertIsDisplayed()
                }.isSuccess
            }
            .flatMap { it.config[SemanticsProperties.Text] }
            .flatMap { it.text.lineSequence().filter { line -> line.startsWith(prefix) }.toList() }

    private companion object {
        val standardEnvironment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.STANDARD),
            effectiveProfile = DisplayProfile.STANDARD,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.STANDARD,
            redrawEpoch = 0L,
        )
    }
}

@Composable
private fun TestPagerControls(
    page: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TsuyomiButton(text = "Previous", onClick = onPrevious, enabled = canGoPrevious)
        Text("Page $page")
        TsuyomiButton(text = "Next", onClick = onNext, enabled = canGoNext)
    }
}
