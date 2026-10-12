/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.search

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.core.ui.components.CoverCardPresentationProvider
import org.tsuyomi.shared.model.CoverCardPresentation

import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceBookSummary

class SearchScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun listCoverContainsTheCompleteTextStackAtLargeFonts() {
        val fontScale = mutableStateOf(2f)
        val book = SourceBookSummary(
            identity = BookIdentity.Remote("source.test", "long-title"),
            title = "这是一本在大字号下仍须保持封面和文字边界的很长书名 A long title",
            author = "作者 Author",
            coverUrl = null,
            canonicalUrl = "https://example.test/book",
        )
        var selected: BookIdentity? = null
        val environment = DisplayEnvironment(
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
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(412.dp, 900.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale.value)) {
                    DisplayEnvironmentProvider(environment) {
                        TsuyomiTheme(environment) {
                            SearchScreen(
                                query = "book",
                                state = SearchResultState.Results(listOf(book)),
                                layout = SearchLayout.LIST,
                                onQueryChange = {},
                                onSearch = {},
                                onSelectBook = { selected = it.identity },
                                onRetry = {},
                                onUseOfflineCache = {},
                                onOpenVerification = {},
                            )
                        }
                    }
                }
            }
        }
        fun assertTextBounds() {
            val cover = composeRule.onNodeWithTag("search-list-cover-source.test:long-title", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val title = composeRule.onNodeWithText(book.title, useUnmergedTree = true)
            val layout = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertTrue(layout.single().lineCount <= 2)
            val titleBounds = title.fetchSemanticsNode().boundsInRoot
            val sourceBounds = composeRule.onNodeWithText("source.test", substring = true, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Title crosses cover top: $titleBounds versus $cover", titleBounds.top >= cover.top - 1f)
            assertTrue("Source crosses cover bottom: $sourceBounds versus $cover", sourceBounds.bottom <= cover.bottom + 1f)
            assertEquals(5f / 7f, cover.width / cover.height, 0.01f)
        }
        assertTextBounds()
        composeRule.runOnIdle { fontScale.value = 1f }
        assertTextBounds()
        composeRule.onNodeWithText(book.title).performClick()
        composeRule.runOnIdle { assertEquals(book.identity, selected) }
    }

    @Test
    fun gridCardsFollowTheSelectedSharedCoverPresentation() {
        val presentation = mutableStateOf(CoverCardPresentation.STANDARD)
        val book = SourceBookSummary(
            identity = BookIdentity.Remote("source.test", "grid-book"),
            title = "网格书籍",
            author = "作者",
            coverUrl = null,
            canonicalUrl = "https://example.test/grid-book",
        )
        val environment = DisplayEnvironment(
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
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                CoverCardPresentationProvider(presentation.value) {
                    TsuyomiTheme(environment) {
                        SearchScreen(
                            query = "grid",
                            state = SearchResultState.Results(listOf(book)),
                            layout = SearchLayout.GRID,
                            onQueryChange = {},
                            onSearch = {},
                            onSelectBook = {},
                            onRetry = {},
                            onUseOfflineCache = {},
                            onOpenVerification = {},
                        )
                    }
                }
            }
        }

        fun cardRatio(): Float {
            val bounds = composeRule.onNodeWithTag("search-grid-source.test:grid-book")
                .fetchSemanticsNode().boundsInRoot
            return bounds.width / bounds.height
        }

        assertEquals(5f / 7f, cardRatio(), 0.01f)
        composeRule.runOnIdle { presentation.value = CoverCardPresentation.WIDE }
        assertEquals(16f / 9f, cardRatio(), 0.01f)
    }

    @Test
    fun loadingSearchDisablesTheOnlySubmitAction() {
        val environment = DisplayEnvironment(
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
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    SearchScreen(
                        query = "进行中的搜索",
                        state = SearchResultState.Loading,
                        layout = SearchLayout.LIST,
                        onQueryChange = {},
                        onSearch = {},
                        onSelectBook = {},
                        onRetry = {},
                        onUseOfflineCache = {},
                        onOpenVerification = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("提交搜索").assertIsNotEnabled()
    }

    @Test
    fun einkSearchPagesKeepCompleteRowsAndRestoreTheSelectedResultPage() {
        val restoration = StateRestorationTester(composeRule)
        val books = List(25) { index ->
            SourceBookSummary(
                identity = BookIdentity.Remote("source.test", "book-$index"),
                title = "Book $index",
                author = "Author $index",
                coverUrl = null,
                canonicalUrl = "https://example.test/book-$index",
            )
        }
        val einkEnvironment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.EINK),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_EINK,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.INSTANT,
            redrawEpoch = 0L,
        )
        val standardEnvironment = einkEnvironment.copy(
            preferences = einkEnvironment.preferences.copy(displayPreference = DisplayPreference.STANDARD),
            effectiveProfile = DisplayProfile.STANDARD,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            motionPolicy = MotionPolicy.STANDARD,
        )
        val einkProfile = mutableStateOf(true)
        var selected: BookIdentity? = null
        restoration.setContent {
            val environment = if (einkProfile.value) einkEnvironment else standardEnvironment
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    SearchScreen(
                        query = "books",
                        state = SearchResultState.Results(books),
                        layout = SearchLayout.LIST,
                        onQueryChange = {},
                        onSearch = {},
                        onSelectBook = { selected = it.identity },
                        onRetry = {},
                        onUseOfflineCache = {},
                        onOpenVerification = {},
                    )
                }
            }
        }

        fun visibleBooks(): Set<Int> = buildSet {
            books.indices.forEach { index ->
                if (composeRule.onNodeWithText("Book $index").isDisplayed()) add(index)
            }
        }

        fun assertVisibleTextFits() {
            val viewport = composeRule.onNodeWithTag("search-eink-results-viewport")
                .fetchSemanticsNode().boundsInRoot
            val controlsTop = composeRule.onNodeWithText("下一页").fetchSemanticsNode().boundsInRoot.top
            visibleBooks().forEach { index ->
                val bounds = composeRule.onNodeWithText("Book $index", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val cover = composeRule.onNodeWithTag("search-list-cover-source.test:book-$index", useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                composeRule.onNodeWithText("作者：Author $index", useUnmergedTree = true).assertIsDisplayed()
                assertTrue(bounds.left >= viewport.left - 1f)
                assertTrue(bounds.right <= viewport.right + 1f)
                assertTrue(bounds.top >= cover.top - 1f)
                assertTrue(bounds.bottom <= cover.bottom + 1f)
                assertTrue(cover.top >= viewport.top - 1f)
                assertTrue(cover.bottom <= controlsTop + 1f)
            }
        }

        composeRule.onAllNodes(hasScrollToNodeAction()).assertCountEquals(0)
        composeRule.onNodeWithText("上一页").assertIsNotEnabled()
        val seenBooks = mutableSetOf<Int>()
        var pageTransitions = 0
        while (true) {
            val visible = visibleBooks()
            seenBooks += visible
            assertVisibleTextFits()
            if (12 in visible && selected == null) {
                composeRule.onNodeWithText("Book 12").performClick()
                composeRule.runOnIdle { assertEquals(books[12].identity, selected) }
            }
            val next = composeRule.onNodeWithText("下一页")
            if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
            next.performClick()
            composeRule.waitForIdle()
            pageTransitions += 1
            assertTrue("Search did not reach its endpoint", pageTransitions <= books.size)
            composeRule.onAllNodes(hasScrollToNodeAction()).assertCountEquals(0)
        }
        assertEquals(books.indices.toSet(), seenBooks)
        composeRule.onNodeWithText("下一页").assertIsNotEnabled()

        val selectedPage = visibleBooks()
        assertTrue(24 in selectedPage)
        assertVisibleTextFits()

        composeRule.runOnIdle { einkProfile.value = false }
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Book 0"))
        composeRule.onNodeWithText("Book 0").assertIsDisplayed()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Book 24"))
        composeRule.onNodeWithText("Book 24").assertIsDisplayed()
        composeRule.runOnIdle { einkProfile.value = true }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollToNodeAction()).assertCountEquals(0)
        assertEquals(selectedPage, visibleBooks())

        restoration.emulateSavedInstanceStateRestore()
        assertEquals(selectedPage, visibleBooks())
        composeRule.onNodeWithText("上一页").performClick()
        assertTrue(selectedPage != visibleBooks())
    }


    @Test
    fun einkSearchPagingPreservesQueryEditsAndStandardScrollRemainsUnchanged() {
        val books = List(72) { index ->
            SourceBookSummary(
                identity = BookIdentity.Remote("source.test", "paged-$index"),
                title = "Book $index",
                author = "Author $index",
                coverUrl = null,
                canonicalUrl = "https://example.test/paged-$index",
            )
        }
        val resultState = SearchResultState.Results(books)
        val query = mutableStateOf("submitted query")
        val einkEnvironment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.EINK),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_EINK,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.INSTANT,
            redrawEpoch = 0L,
        )
        val standardEnvironment = einkEnvironment.copy(
            preferences = einkEnvironment.preferences.copy(displayPreference = DisplayPreference.STANDARD),
            effectiveProfile = DisplayProfile.STANDARD,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            motionPolicy = MotionPolicy.STANDARD,
        )
        val environment = mutableStateOf(einkEnvironment)

        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                val display = environment.value
                DisplayEnvironmentProvider(display) {
                    TsuyomiTheme(display) {
                        SearchScreen(
                            query = query.value,
                            state = resultState,
                            layout = SearchLayout.LIST,
                            onQueryChange = { query.value = it },
                            onSearch = {},
                            onSelectBook = {},
                            onRetry = {},
                            onUseOfflineCache = {},
                            onOpenVerification = {},
                        )
                    }
                }
            }
        }

        composeRule.onAllNodes(hasScrollToNodeAction()).assertCountEquals(0)
        var pageTransitions = 0
        while (!composeRule.onNodeWithText("Book 42").isDisplayed()) {
            val next = composeRule.onNodeWithText("下一页")
            assertTrue("Book 42 was not reachable by paging", !next.fetchSemanticsNode().config
                .contains(SemanticsProperties.Disabled))
            next.performClick()
            composeRule.waitForIdle()
            pageTransitions += 1
            assertTrue("Search paging did not advance", pageTransitions <= books.size * 3)
        }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("unsubmitted query edit")
        composeRule.onNodeWithText("Book 42").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollToNodeAction()).assertCountEquals(0)

        composeRule.runOnIdle { environment.value = standardEnvironment }
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Book 71"))
        composeRule.onNodeWithText("Book 71").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("another unsubmitted edit")
        composeRule.onNodeWithText("Book 71").assertIsDisplayed()

        composeRule.runOnIdle { environment.value = einkEnvironment }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollToNodeAction()).assertCountEquals(0)
        composeRule.onNodeWithText("Book 42").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun einkSearchGridKeepsAllResultsReachableAtNarrowAndWideLargeFontWidths() {
        val width = mutableStateOf(320.dp)
        val books = List(25) { index ->
            SourceBookSummary(
                identity = BookIdentity.Remote("source.test", "adaptive-$index"),
                title = "网格作品 $index",
                author = "作者 $index",
                coverUrl = null,
                canonicalUrl = "https://example.test/adaptive-$index",
            )
        }
        val environment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.EINK),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_EINK,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.INSTANT,
            redrawEpoch = 0L,
        )
        var selected: BookIdentity? = null
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.value, 840.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    DisplayEnvironmentProvider(environment) {
                        TsuyomiTheme(environment) {
                            SearchScreen(
                                query = "adaptive",
                                state = SearchResultState.Results(books),
                                layout = SearchLayout.GRID,
                                onQueryChange = {},
                                onSearch = {},
                                onSelectBook = { selected = it.identity },
                                onRetry = {},
                                onUseOfflineCache = {},
                                onOpenVerification = {},
                            )
                        }
                    }
                }
            }
        }

        fun bounds(index: Int) = composeRule.onNodeWithTag("search-grid-source.test:adaptive-$index")
            .fetchSemanticsNode().boundsInRoot
        for (viewportWidth in listOf(320.dp, 900.dp)) {
            composeRule.runOnIdle { width.value = viewportWidth }
            composeRule.waitForIdle()
            val first = bounds(0)
            val second = bounds(1)
            assertEquals(first.top, second.top, 1f)
            assertTrue(second.left > first.left)
            val viewport = composeRule.onNodeWithTag("search-eink-results-viewport")
                .fetchSemanticsNode().boundsInRoot
            val controlsTop = composeRule.onNodeWithText("下一页").fetchSemanticsNode().boundsInRoot.top
            fun visibleIndices(): Set<Int> = buildSet {
                books.indices.forEach { index ->
                    val nodes = composeRule.onAllNodesWithTag("search-grid-source.test:adaptive-$index")
                        .fetchSemanticsNodes()
                    if (nodes.any { node ->
                            val bounds = node.boundsInRoot
                            bounds.height > 0f && bounds.left >= viewport.left - 1f &&
                                bounds.right <= viewport.right + 1f && bounds.top >= viewport.top - 1f &&
                                bounds.bottom <= controlsTop + 1f
                        }
                    ) {
                        add(index)
                    }
                }
            }
            fun assertVisibleCardsFit() {
                visibleIndices().forEach { index ->
                    val cardBounds = bounds(index)
                    assertTrue(cardBounds.left >= viewport.left - 1f)
                    assertTrue(cardBounds.right <= viewport.right + 1f)
                    assertTrue(cardBounds.top >= viewport.top - 1f)
                    assertTrue(cardBounds.bottom <= controlsTop + 1f)
                }
            }

            composeRule.onAllNodes(hasScrollToNodeAction()).assertCountEquals(0)
            composeRule.onNodeWithText("上一页").assertIsNotEnabled()
            val seen = mutableSetOf<Int>()
            var pageTransitions = 0
            while (true) {
                val visible = visibleIndices()
                seen += visible
                assertVisibleCardsFit()
                if (12 in visible && selected == null && composeRule.onNodeWithText("网格作品 12").isDisplayed()) {
                    composeRule.onNodeWithText("网格作品 12").performClick()
                    composeRule.runOnIdle { assertEquals(books[12].identity, selected) }
                }
                val next = composeRule.onNodeWithText("下一页")
                if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
                next.performClick()
                composeRule.waitForIdle()
                pageTransitions += 1
                assertTrue("Search grid did not reach its endpoint", pageTransitions <= books.size * 6)
                composeRule.onAllNodes(hasScrollToNodeAction()).assertCountEquals(0)
            }
            assertEquals(books.indices.toSet(), seen)
            composeRule.onNodeWithText("下一页").assertIsNotEnabled()
            val previous = composeRule.onNodeWithText("上一页")
            var reverseTransitions = 0
            while (!previous.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) {
                previous.performClick()
                composeRule.waitForIdle()
                reverseTransitions += 1
                assertTrue("Search grid did not return to its start", reverseTransitions <= books.size * 6)
            }
        }
        composeRule.runOnIdle { assertEquals(books[12].identity, selected) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun einkMeasuredPlansOnlyAcquirePlacedSearchCovers() {
        val books = List(72) { index ->
            SourceBookSummary(
                identity = BookIdentity.Remote("source.test", "probe-$index"),
                title = "Probe book $index", author = "Author $index",
                coverUrl = "https://example.test/probe-$index.jpg",
                canonicalUrl = "https://example.test/probe-$index",
            )
        }
        val layout = mutableStateOf(SearchLayout.LIST)
        val activeCovers = mutableSetOf<String>()
        val acquiredCovers = mutableSetOf<String>()
        var searchRequests = 0
        val environment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.EINK),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_EINK,
            detectedDeviceLabel = null, dynamicColorEligible = false,
            dynamicColorEffective = false, effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.INSTANT, redrawEpoch = 0L,
        )
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        SearchScreen(
                            query = "submitted query", state = SearchResultState.Results(books), layout = layout.value,
                            onQueryChange = {}, onSearch = { searchRequests++ }, onSelectBook = {},
                            onRetry = { searchRequests++ }, onUseOfflineCache = { searchRequests++ },
                            onOpenVerification = {},
                            onCoverVisibility = { book, visible ->
                                val id = book.identity.remoteBookId
                                if (visible) {
                                    activeCovers.add(id)
                                    acquiredCovers.add(id)
                                } else {
                                    assertTrue("Only an acquired cover may be released", activeCovers.remove(id))
                                }
                            },
                        )
                    }
                }
            }
        }
        fun visibleIds(): Set<String> = composeRule.onAllNodes(hasText("Probe book ", substring = true))
            .fetchSemanticsNodes().filter { node ->
                // Measured probes retain title semantics; public display checks placement and viewport clipping.
                node.config[SemanticsProperties.Text].any { title ->
                    title.text.startsWith("Probe book ") && composeRule.onNodeWithText(title.text).isDisplayed()
                }
            }.flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
            .filter { it.startsWith("Probe book ") }.map { "probe-${it.removePrefix("Probe book ")}" }.toSet()
        fun assertOnlyPlacedCovers(): Set<String> {
            val placed = visibleIds()
            assertTrue("A real content page must contain results", placed.isNotEmpty())
            composeRule.runOnIdle { assertEquals("Probes cannot activate unplaced covers", placed, activeCovers) }
            return placed
        }
        listOf(SearchLayout.LIST, SearchLayout.GRID).forEach { nextLayout ->
            composeRule.runOnIdle { layout.value = nextLayout }
            val firstPage = assertOnlyPlacedCovers()
            if (nextLayout == SearchLayout.LIST) {
                composeRule.runOnIdle { assertEquals("The complete initial plan stays inert", firstPage, acquiredCovers) }
            }
            composeRule.onNodeWithText("首页").assertIsNotEnabled()
            composeRule.runOnIdle { acquiredCovers.clear() }
            composeRule.onNodeWithText("末页").performClick()
            val lastPage = assertOnlyPlacedCovers()
            assertTrue("The last result must be genuinely placed", "probe-71" in lastPage)
            composeRule.runOnIdle { assertEquals("Endpoint planning does not acquire intermediate covers", lastPage, acquiredCovers) }
            composeRule.onNodeWithText("末页").assertIsNotEnabled()
            composeRule.onNodeWithText("首页").performClick()
            assertEquals(firstPage, assertOnlyPlacedCovers())
            composeRule.onNodeWithText("下一页").performClick()
            assertOnlyPlacedCovers()
            composeRule.onNodeWithText("上一页").performClick()
            assertEquals(firstPage, assertOnlyPlacedCovers())
        }
        composeRule.runOnIdle { layout.value = SearchLayout.COMPACT }
        composeRule.onNodeWithText("Probe book 0").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue("Compact rows acquire no cover work", activeCovers.isEmpty())
            assertEquals("Pagination never submits or retries source search", 0, searchRequests)
        }
    }

}
