/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceHomeFilter
import org.tsuyomi.shared.sourcecontract.SourceHomeFeature
import org.tsuyomi.shared.sourcecontract.SourceHomeFilterOption
import org.tsuyomi.shared.sourcecontract.SourceHomePage
import org.tsuyomi.shared.sourcecontract.SourceHomeSection

@RunWith(AndroidJUnit4::class)
class SourceHomeScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standard_home_uses_dual_capsule_filters_with_immediate_selection_and_automatic_append() {
        val primary = primaryFilter()
        val tag = SourceHomeFilter(
            id = "tag",
            label = "题材",
            options = (listOf(
                "school" to "校园",
                "love" to "恋爱",
                "fantasy" to "奇幻",
                "adventure" to "冒险",
                "science_fiction" to "科幻",
                "magic" to "魔法",
                "suspense" to "悬疑",
                "game" to "游戏",
                "history" to "历史",
                "military" to "军事",
                "sports" to "运动",
                "music" to "音乐",
                "healing" to "治愈",
            ) + (14..32).map { "tag$it" to "题材 $it" })
                .map { (value, label) -> SourceHomeFilterOption(value, label) },
        )
        val sort = SourceHomeFilter(
            id = "sort",
            label = "排序",
            options = listOf("0" to "按更新", "1" to "按热门", "2" to "只看完结", "3" to "只看动画化")
                .map { (value, label) -> SourceHomeFilterOption(value, label) },
        )
        val selected = mapOf("view" to "category", "tag" to "school", "sort" to "0")
        val page = page(
            filters = listOf(primary, tag, sort),
            selectedFilters = selected,
            books = (1..15).map(::book),
            nextCursor = "page-2",
            complete = false,
            sectionTitle = "校园 · 按更新",
        )
        val submitted = mutableStateOf<Map<String, String>?>(null)
        val filterRequests = AtomicInteger()
        val appendRequests = AtomicInteger()
        val outerGridIndex = AtomicInteger()
        val outerGridOffset = AtomicInteger()

        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(environment = standardEnvironment) {
                    SourceHomeScreen(
                        sourceName = "Wenku8",
                        state = SourceHomeViewState.Content(
                            title = "Wenku8 书库",
                            primaryFilter = primary,
                            selectedPrimary = "category",
                            pages = mapOf(
                                "category" to SourceHomePageViewState(
                                    queryKey = "revision|sort=0&tag=school&view=category",
                                    selectedFilters = selected,
                                    page = page,
                                ),
                            ),
                        ),
                        remoteLibraryAvailable = true,
                        verificationAvailable = true,
                        onSelectPrimary = {},
                        onSelectFilters = {
                            submitted.value = it
                            filterRequests.incrementAndGet()
                        },
                        onRefresh = {},
                        onLoadMore = { appendRequests.incrementAndGet() },
                        onRetryReplacement = {},
                        onSearch = {},
                        onOpenRemoteLibrary = {},
                        onOpenBook = {},
                        onOpenFeature = {},
                        onOpenVerification = {},
                        onScrollPositionChanged = { _, _, index, offset ->
                            outerGridIndex.set(index)
                            outerGridOffset.set(offset)
                        },
                        coverState = { summary -> CoverUiState.Fallback(FallbackSpec(summary.title, "Wenku8")) },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("source-home-primary-tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("source-home-pager").assertIsDisplayed()
        composeRule.onNodeWithTag("source-home-primary-filter-capsule").assertIsDisplayed()
        composeRule.onNodeWithTag("source-home-secondary-filter-capsule-0").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("source-home-quick-actions").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("source-home-side-rail").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("加载下一页").fetchSemanticsNodes().isEmpty())
        val tabRow = composeRule.onNodeWithTag("source-home-primary-tabs").fetchSemanticsNode().boundsInRoot
        val firstTab = composeRule.onNodeWithTag("tsuyomi-tab-recommend").fetchSemanticsNode().boundsInRoot
        val lastTab = composeRule.onNodeWithTag("tsuyomi-tab-completed").fetchSemanticsNode().boundsInRoot
        assertTrue(abs((firstTab.left - tabRow.left) - (tabRow.right - lastTab.right)) < 1f)
        assertTrue(abs(firstTab.width - lastTab.width) < 1f)

        val grid = composeRule.onNodeWithTag("source-home-book-grid-category").fetchSemanticsNode().boundsInRoot
        val filterRow = composeRule.onNodeWithTag("source-home-filter-row").fetchSemanticsNode().boundsInRoot
        assertTrue(filterRow.left > grid.left)
        assertTrue(abs((filterRow.left - grid.left) - (grid.right - filterRow.right)) < 1f)

        val heading = composeRule.onAllNodesWithTag("source-home-section-heading")
            .fetchSemanticsNodes().first().boundsInRoot
        val firstCard = composeRule.onNodeWithTag("source-home-book-1").fetchSemanticsNode().boundsInRoot
        val thirdCard = composeRule.onNodeWithTag("source-home-book-3").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(heading.left - filterRow.left) < 1f)
        assertTrue(abs(heading.right - filterRow.right) < 1f)
        assertTrue(abs(firstCard.left - filterRow.left) < 1f)
        assertTrue(abs(thirdCard.right - filterRow.right) < 1f)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("题材，展开选项").performClick()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag("source-home-filter-panel").assertExists()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("source-home-filter-panel").assertIsDisplayed()
        val panel = composeRule.onNodeWithTag("source-home-filter-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("panel=$panel filterRow=$filterRow", abs(panel.left - filterRow.left) < 1f)
        assertTrue("panel=$panel filterRow=$filterRow", abs(panel.right - filterRow.right) < 1f)
        val visiblePanelOptions = composeRule.onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { bounds ->
                bounds.left >= panel.left &&
                    bounds.right <= panel.right &&
                    bounds.top >= panel.top &&
                    bounds.bottom <= panel.bottom
            }
        assertTrue(visiblePanelOptions.isNotEmpty())
        val firstOptionRowTop = visiblePanelOptions.minOf { it.top }
        val firstOptionRow = visiblePanelOptions.filter { abs(it.top - firstOptionRowTop) < 1f }
        val firstOptionRowLeft = firstOptionRow.minOf { it.left }
        val firstOptionRowRight = firstOptionRow.maxOf { it.right }
        assertTrue(
            "panel=$panel rowLeft=$firstOptionRowLeft rowRight=$firstOptionRowRight",
            abs(
                (firstOptionRowLeft - panel.left) -
                    (panel.right - firstOptionRowRight),
            ) <= 1f,
        )
        composeRule.onAllNodesWithText("奇幻")[1].performClick()
        composeRule.runOnIdle {
            assertEquals(1, filterRequests.get())
            assertEquals("fantasy", submitted.value?.get("tag"))
            assertEquals("0", submitted.value?.get("sort"))
        }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag("source-home-filter-panel").assertExists()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("source-home-filter-panel").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithContentDescription("题材，展开选项").performClick()
        repeat(12) {
            composeRule.onNodeWithTag("source-home-filter-panel").performTouchInput { swipeUp() }
        }
        composeRule.runOnIdle {
            assertEquals(0, outerGridIndex.get())
            assertEquals(0, outerGridOffset.get())
        }
        composeRule.onNodeWithContentDescription("题材，收起选项").performClick()

        composeRule.onNode(hasStateDescription("排序，展开选项")).performClick()
        composeRule.onNodeWithText("按热门").performClick()
        composeRule.runOnIdle {
            assertEquals(2, filterRequests.get())
            assertEquals("1", submitted.value?.get("sort"))
        }
        composeRule.onNodeWithTag("source-home-filter-sheet").assertDoesNotExist()

        composeRule.onNodeWithTag("source-home-book-grid-category").performScrollToIndex(16)
        composeRule.waitUntil(timeoutMillis = 5_000) { appendRequests.get() == 1 }
    }

    @Test
    fun pager_switches_cached_pages_without_full_screen_loading() {
        val primary = primaryFilter()
        val recommendSelection = mapOf("view" to "recommend")
        val categorySelection = mapOf("view" to "category", "tag" to "school", "sort" to "0")
        val categoryTag = SourceHomeFilter(
            id = "tag",
            label = "题材",
            options = listOf(SourceHomeFilterOption("school", "校园")) +
                (2..13).map { SourceHomeFilterOption("tag$it", "题材 $it") },
        )
        val categorySort = SourceHomeFilter(
            id = "sort",
            label = "排序",
            options = listOf(SourceHomeFilterOption("0", "按更新")),
        )
        val recommendPage = SourceHomePage(
            title = "Wenku8 书库",
            schemaVersion = 1,
            filters = listOf(primary),
            selectedFilters = recommendSelection,
            sections = listOf(
                SourceHomeSection("seasonal", "7月新番", (1..6).map { book(it, "推荐") }),
                SourceHomeSection("new-books", "新书风云榜", (7..12).map { book(it, "推荐") }),
                SourceHomeSection("members", "本周会员推荐榜", (13..18).map { book(it, "推荐") }),
            ),
            nextCursor = null,
            complete = true,
        )
        val categoryPage = page(
            filters = listOf(primary, categoryTag, categorySort),
            selectedFilters = categorySelection,
            books = (21..38).map { book(it, "分类") },
            sectionTitle = "校园 · 按更新",
        )
        val observedScroll = mutableMapOf<String, Int>()

        composeRule.setContent {
            var selectedPrimary by remember { mutableStateOf("recommend") }
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(environment = standardEnvironment) {
                    SourceHomeScreen(
                        sourceName = "Wenku8",
                        state = SourceHomeViewState.Content(
                            title = "Wenku8 书库",
                            primaryFilter = primary,
                            selectedPrimary = selectedPrimary,
                            pages = mapOf(
                                "recommend" to SourceHomePageViewState(
                                    queryKey = "recommend-query",
                                    selectedFilters = recommendSelection,
                                    page = recommendPage,
                                ),
                                "category" to SourceHomePageViewState(
                                    queryKey = "category-query",
                                    selectedFilters = categorySelection,
                                    page = categoryPage,
                                ),
                            ),
                        ),
                        remoteLibraryAvailable = true,
                        verificationAvailable = true,
                        onSelectPrimary = { selectedPrimary = it },
                        onSelectFilters = {},
                        onRefresh = {},
                        onLoadMore = {},
                        onRetryReplacement = {},
                        onSearch = {},
                        onOpenRemoteLibrary = {},
                        onOpenFeature = {},
                        onOpenBook = {},
                        onOpenVerification = {},
                        onScrollPositionChanged = { primaryValue, _, index, _ -> observedScroll[primaryValue] = index },
                        coverState = { summary -> CoverUiState.Fallback(FallbackSpec(summary.title, "Wenku8")) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("推荐 1").assertIsDisplayed()
        composeRule.onNodeWithText("7月新番").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("source-home-primary-filter-capsule").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("分类").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("source-home-book-grid-category").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("分类 21").assertIsDisplayed()
        composeRule.onNodeWithTag("source-home-book-grid-category").performScrollToIndex(12)
        composeRule.waitUntil(timeoutMillis = 5_000) { (observedScroll["category"] ?: 0) > 0 }

        composeRule.onNodeWithText("推荐").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("source-home-book-grid-recommend").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("推荐 1").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("正在打开栏目").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun feature_card_opens_dedicated_page_without_primary_tabs() {
        val primary = primaryFilter()
        val feature = SourceHomeFeature(
            id = "sugoi-2026",
            title = "这本轻小说真厉害！2026",
            supportingText = "TOP20 榜单",
            selectedFilters = mapOf("view" to "recommend", "feature" to "sugoi-2026"),
        )
        val rootPage = SourceHomePage(
            title = "Wenku8 书库",
            schemaVersion = 1,
            filters = listOf(primary),
            selectedFilters = mapOf("view" to "recommend"),
            sections = listOf(SourceHomeSection("seasonal", "7月新番", listOf(book(1, "推荐")))),
            features = listOf(feature),
            nextCursor = null,
            complete = true,
        )
        val awardPage = SourceHomePage(
            title = feature.title,
            schemaVersion = 1,
            filters = listOf(primary),
            selectedFilters = mapOf("view" to "recommend"),
            sections = listOf(
                SourceHomeSection("bunko", "文库部门 TOP10", listOf(book(2, "文库"))),
                SourceHomeSection("tankobon", "单行本部门 TOP10", listOf(book(3, "单行本"))),
            ),
            nextCursor = null,
            complete = true,
        )
        var openedSelection: Map<String, String>? = null

        composeRule.setContent {
            var featureOpen by remember { mutableStateOf(false) }
            val activePage = if (featureOpen) awardPage else rootPage
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(environment = standardEnvironment) {
                    SourceHomeScreen(
                        sourceName = "Wenku8",
                        state = SourceHomeViewState.Content(
                            title = activePage.title,
                            primaryFilter = primary,
                            selectedPrimary = "recommend",
                            pages = mapOf(
                                "recommend" to SourceHomePageViewState(
                                    queryKey = if (featureOpen) "award-query" else "recommend-query",
                                    selectedFilters = if (featureOpen) feature.selectedFilters else rootPage.selectedFilters,
                                    page = activePage,
                                ),
                            ),
                            featureOpen = featureOpen,
                        ),
                        remoteLibraryAvailable = true,
                        verificationAvailable = true,
                        onSelectPrimary = {},
                        onSelectFilters = {},
                        onRefresh = {},
                        onLoadMore = {},
                        onRetryReplacement = {},
                        onSearch = {},
                        onOpenRemoteLibrary = {},
                        onOpenBook = {},
                        onOpenFeature = {
                            openedSelection = it.selectedFilters
                            featureOpen = true
                        },
                        onOpenVerification = {},
                        onScrollPositionChanged = { _, _, _, _ -> },
                        coverState = { summary -> CoverUiState.Fallback(FallbackSpec(summary.title, "Wenku8")) },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("source-home-feature-sugoi-2026").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(feature.selectedFilters, openedSelection)
        }
        assertTrue(composeRule.onAllNodesWithTag("source-home-primary-tabs").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("source-home-pager").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("文库部门 TOP10").assertIsDisplayed()
        composeRule.onNodeWithText("单行本部门 TOP10").assertIsDisplayed()
    }

    private fun primaryFilter() = SourceHomeFilter(
        id = "view",
        label = "栏目",
        options = listOf("recommend" to "推荐", "category" to "分类", "ranking" to "排行", "completed" to "完结")
            .map { (value, label) -> SourceHomeFilterOption(value, label) },
    )

    private fun page(
        filters: List<SourceHomeFilter>,
        selectedFilters: Map<String, String>,
        books: List<SourceBookSummary>,
        nextCursor: String? = null,
        complete: Boolean = true,
        sectionTitle: String,
    ) = SourceHomePage(
        title = "Wenku8 书库",
        schemaVersion = 1,
        filters = filters,
        selectedFilters = selectedFilters,
        sections = listOf(SourceHomeSection("catalog", sectionTitle, books)),
        nextCursor = nextCursor,
        complete = complete,
    )

    private fun book(index: Int, prefix: String = "轻小说") = SourceBookSummary(
        identity = BookIdentity("org.tsuyomi.wenku8", index.toString()),
        title = "$prefix $index",
        author = "作者 $index",
        coverUrl = null,
        canonicalUrl = "https://www.wenku8.net/book/$index.htm",
    )

    private val standardEnvironment = DisplayEnvironment(
        preferences = DisplayPreferences(
            displayPreference = DisplayPreference.STANDARD,
            colorSchemePreference = ColorSchemePreference.LIGHT,
            dynamicColorEnabled = false,
        ),
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
