/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.preferences.ColorSchemePreference
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.CoverFailureReason
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.core.ui.components.CoverCardPresentationProvider
import org.tsuyomi.core.ui.components.TsuyomiFilterCapsuleOption
import org.tsuyomi.core.ui.components.TsuyomiFilterCapsulePanel
import org.tsuyomi.core.ui.components.TsuyomiFilterCapsuleOptionRow
import org.tsuyomi.shared.model.CoverCardPresentation

import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceHomeFilter
import org.tsuyomi.shared.sourcecontract.SourceHomeFeature
import org.tsuyomi.shared.sourcecontract.SourceHomeFilterOption
import org.tsuyomi.shared.sourcecontract.SourceHomePage
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceHomeSection

@RunWith(AndroidJUnit4::class)
class SourceHomeScreenInstrumentedTest {
    @Test
    fun verification_failure_offers_login_and_cached_content_actions() {
        var verificationRequested = false
        var cacheRequested = false
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(environment = standardEnvironment) {
                    SourceHomeScreen(
                        sourceName = "测试来源",
                        state = SourceHomeViewState.Failure(
                            SourceErrorCode.VERIFICATION_REQUIRED,
                            "verification-required",
                        ),
                        remoteLibraryAvailable = true,
                        verificationAvailable = true,
                        onSelectPrimary = {},
                        onSelectFilters = {},
                        onRefresh = {},
                        onLoadMore = {},
                        onRetryReplacement = {},
                        onUseOfflineCache = { cacheRequested = true },
                        onSearch = {},
                        onOpenRemoteLibrary = {},
                        onOpenBook = {},
                        onOpenFeature = {},
                        onOpenVerification = { verificationRequested = true },
                        onScrollPositionChanged = { _, _, _, _ -> },
                        coverState = { CoverUiState.Fallback(FallbackSpec("缓存", "source")) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("verification-required").assertDoesNotExist()
        composeRule.onNodeWithText("此来源需要先完成登录或安全验证。现有本地内容未改动。").assertIsDisplayed()
        composeRule.onNodeWithText("前往登录验证").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(verificationRequested) }
        composeRule.onNodeWithText("使用已缓存内容").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(cacheRequested) }
    }

    @Test
    fun verification_failure_keeps_cached_home_visible_and_offers_direct_login() {
        val primary = primaryFilter()
        val selection = mapOf("view" to "recommend")
        var verificationRequested = false
        var retryRequested = false
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(environment = standardEnvironment) {
                    SourceHomeScreen(
                        sourceName = "Wenku8",
                        state = SourceHomeViewState.Content(
                            title = "Wenku8 书库",
                            primaryFilter = primary,
                            selectedPrimary = "recommend",
                            pages = mapOf(
                                "recommend" to SourceHomePageViewState(
                                    queryKey = "recommend-query",
                                    selectedFilters = selection,
                                    page = page(
                                        filters = listOf(primary),
                                        selectedFilters = selection,
                                        books = listOf(book(1, "缓存作品")),
                                        sectionTitle = "缓存推荐",
                                    ),
                                    replacementFailure = SourceHomeFailure(
                                        SourceErrorCode.SESSION_REQUIRED,
                                        "session-required",
                                    ),
                                ),
                            ),
                        ),
                        remoteLibraryAvailable = true,
                        verificationAvailable = true,
                        onSelectPrimary = {},
                        onSelectFilters = {},
                        onRefresh = {},
                        onLoadMore = {},
                        onRetryReplacement = { retryRequested = true },
                        onUseOfflineCache = {},
                        onSearch = {},
                        onOpenRemoteLibrary = {},
                        onOpenBook = {},
                        onOpenFeature = {},
                        onOpenVerification = { verificationRequested = true },
                        onScrollPositionChanged = { _, _, _, _ -> },
                        coverState = { CoverUiState.Fallback(FallbackSpec("缓存", "source")) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("缓存作品 1").assertIsDisplayed()
        composeRule.onNodeWithText("登录状态已失效", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("前往登录验证").performClick()
        composeRule.runOnIdle {
            assertTrue(verificationRequested)
            assertFalse(retryRequested)
        }
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun category_quick_filters_keep_source_order_when_selected_and_reveal_offscreen_choice() {
        val options = listOf(
            TsuyomiFilterCapsuleOption("school", "校园"),
            TsuyomiFilterCapsuleOption("youth", "青春"),
            TsuyomiFilterCapsuleOption("love", "恋爱"),
            TsuyomiFilterCapsuleOption("fantasy", "奇幻"),
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(environment = standardEnvironment) {
                    var selected by remember { mutableStateOf("school") }
                    var expanded by remember { mutableStateOf(false) }
                    Column {
                        TsuyomiFilterCapsuleOptionRow(
                            options = options,
                            selectedKey = selected,
                            expanded = expanded,
                            expandedStateDescription = "题材，收起选项",
                            collapsedStateDescription = "题材，展开选项",
                            onToggleExpanded = { expanded = !expanded },
                            onSelect = { selected = it; expanded = false },
                            modifier = Modifier.width(280.dp).testTag("quick-filters"),
                        )
                        if (expanded) {
                            TsuyomiFilterCapsulePanel(options, selected, onSelect = { selected = it; expanded = false })
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("青春").performClick()
        composeRule.onNodeWithText("青春").assertIsSelected()
        val school = composeRule.onNodeWithText("校园").fetchSemanticsNode().boundsInRoot
        val youth = composeRule.onNodeWithText("青春").fetchSemanticsNode().boundsInRoot
        assertTrue("Selection must not move the chip to the front", school.left < youth.left)
        composeRule.onNodeWithText("青春").performClick()
        composeRule.onNodeWithText("青春").assertIsSelected()
        assertEquals(youth.left, composeRule.onNodeWithText("青春").fetchSemanticsNode().boundsInRoot.left, 1f)

        composeRule.onNodeWithContentDescription("题材，展开选项").performClick()
        composeRule.onNodeWithTag("filter-capsule-panel").assertIsDisplayed()
        val fantasyOptions = composeRule.onAllNodesWithText("奇幻")
        fantasyOptions[fantasyOptions.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.onNodeWithText("奇幻").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("filter-capsule-scroll").performTouchInput { swipeRight() }
        composeRule.onNodeWithText("校园").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("题材，展开选项").performClick()
        composeRule.onNodeWithContentDescription("题材，收起选项").performClick()
        composeRule.onNodeWithText("奇幻").assertIsDisplayed().assertIsSelected()
        val viewport = composeRule.onNodeWithTag("filter-capsule-scroll").fetchSemanticsNode().boundsInRoot
        val restoredSelection = composeRule.onNodeWithText("奇幻").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Closing without changing the filter must restore its complete highlight",
            restoredSelection.left >= viewport.left - 1f && restoredSelection.right <= viewport.right + 1f,
        )
        composeRule.onNodeWithTag("filter-capsule-scroll").performTouchInput { swipeRight() }
        composeRule.onNodeWithText("校园").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("题材，展开选项").performClick()
        val sameFantasyOptions = composeRule.onAllNodesWithText("奇幻")
        sameFantasyOptions[sameFantasyOptions.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.onNodeWithText("奇幻").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithContentDescription("题材，展开选项").performClick()
        val schoolOptions = composeRule.onAllNodesWithText("校园")
        schoolOptions[schoolOptions.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.onNodeWithText("校园").assertIsDisplayed().assertIsSelected()
        assertTrue(
            composeRule.onNodeWithText("校园").fetchSemanticsNode().boundsInRoot.left <
                composeRule.onNodeWithText("青春").fetchSemanticsNode().boundsInRoot.left,
        )
    }

    @Test
    fun collapsed_category_filters_keep_partial_tags_visible_after_drag_release() {
        val options = listOf(
            TsuyomiFilterCapsuleOption("school", "校园"),
            TsuyomiFilterCapsuleOption("youth", "青春"),
            TsuyomiFilterCapsuleOption("love", "恋爱"),
            TsuyomiFilterCapsuleOption("fantasy", "奇幻"),
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(environment = standardEnvironment) {
                    var selected by remember { mutableStateOf("school") }
                    TsuyomiFilterCapsuleOptionRow(
                        options = options,
                        selectedKey = selected,
                        expanded = false,
                        expandedStateDescription = "收起选项",
                        collapsedStateDescription = "展开选项",
                        onToggleExpanded = {},
                        onSelect = { selected = it },
                        modifier = Modifier.width(176.dp),
                    )
                }
            }
        }

        val scroll = composeRule.onNodeWithTag("filter-capsule-scroll")
        val viewport = scroll.getUnclippedBoundsInRoot()
        val youth = composeRule.onNodeWithText("青春").assertIsDisplayed().assertIsEnabled()
            .getUnclippedBoundsInRoot()
        assertTrue("The resting edge tag must remain visible", youth.left < viewport.right && youth.right > viewport.right)
        composeRule.mainClock.autoAdvance = false
        try {
            scroll.performTouchInput {
                down(Offset(width * 0.8f, center.y))
                moveBy(Offset(-width * 0.25f, 0f), delayMillis = 600)
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.onNodeWithText("校园").assertIsDisplayed().assertIsSelected()
            val love = composeRule.onNodeWithText("恋爱").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("The drag must expose a partial trailing tag", love.left < viewport.right && love.right > viewport.right)
            val held = scroll.captureToImage().asAndroidBitmap()
            val heldSchool = composeRule.onNodeWithText("校园").getUnclippedBoundsInRoot()
            scroll.performTouchInput {
                moveBy(Offset.Zero, delayMillis = 200)
                up()
            }
            composeRule.mainClock.advanceTimeBy(500)
            val released = scroll.captureToImage().asAndroidBitmap()
            // The middle chip retains native press feedback; untouched edge tags must be pixel-stable.
            val pixelsPerDp = held.width / (viewport.right - viewport.left).value
            val schoolRight = ((heldSchool.right.value - viewport.left.value) * pixelsPerDp).roundToInt()
            val loveLeft = ((love.left.value - viewport.left.value) * pixelsPerDp).roundToInt()
            var edgePixelsMatch = true
            pixels@ for (y in 0 until held.height) {
                for (x in 0 until held.width) {
                    if ((x < schoolRight || x >= loveLeft) && held.getPixel(x, y) != released.getPixel(x, y)) {
                        edgePixelsMatch = false
                        break@pixels
                    }
                }
            }
            assertTrue("Releasing a stationary drag must not hide or change partial edge tags", edgePixelsMatch)
            assertEquals(heldSchool, composeRule.onNodeWithText("校园").getUnclippedBoundsInRoot())
            assertEquals(love, composeRule.onNodeWithText("恋爱").getUnclippedBoundsInRoot())
            composeRule.onNodeWithText("校园").assertIsDisplayed().assertIsSelected()
            composeRule.onNodeWithText("恋爱").assertIsDisplayed().assertIsEnabled()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        val visibleLove = composeRule.onNodeWithText("恋爱").fetchSemanticsNode().boundsInRoot
        val visibleViewport = scroll.fetchSemanticsNode().boundsInRoot
        scroll.performTouchInput {
            down(visibleLove.center - visibleViewport.topLeft)
            up()
        }
        composeRule.onNodeWithText("恋爱").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun category_quick_filters_restore_offscreen_selection_at_large_font_in_rtl() {
        val restoration = StateRestorationTester(composeRule)
        val options = listOf(
            TsuyomiFilterCapsuleOption("school", "校园"),
            TsuyomiFilterCapsuleOption("youth", "青春"),
            TsuyomiFilterCapsuleOption("love", "恋爱"),
            TsuyomiFilterCapsuleOption("fantasy", "奇幻"),
        )
        restoration.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                DisplayEnvironmentProvider(standardEnvironment) {
                    TsuyomiTheme(environment = standardEnvironment) {
                        var selected by rememberSaveable { mutableStateOf("fantasy") }
                        TsuyomiFilterCapsuleOptionRow(
                            options = options,
                            selectedKey = selected,
                            expanded = false,
                            expandedStateDescription = "收起选项",
                            collapsedStateDescription = "展开选项",
                            onToggleExpanded = {},
                            onSelect = { selected = it },
                            modifier = Modifier.width(280.dp),
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("奇幻").assertIsDisplayed().assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("奇幻").assertIsDisplayed().assertIsSelected()
        val viewport = composeRule.onNodeWithTag("filter-capsule-scroll").fetchSemanticsNode().boundsInRoot
        val fantasy = composeRule.onNodeWithText("奇幻").fetchSemanticsNode().boundsInRoot
        val love = composeRule.onNodeWithText("恋爱").fetchSemanticsNode().boundsInRoot
        assertTrue("Restored selection must be fully visible", fantasy.left >= viewport.left && fantasy.right <= viewport.right)
        assertTrue("RTL keeps source order instead of moving selection first", fantasy.right < love.right)
        composeRule.onNodeWithText("恋爱").performClick()
        composeRule.onNodeWithText("恋爱").assertIsDisplayed().assertIsSelected()
    }

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
            books = (1..30).map(::book),
            nextCursor = "page-2",
            complete = false,
            sectionTitle = "校园 · 按更新",
        )
        val readyCover = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val submitted = mutableStateOf<Map<String, String>?>(null)
        val filterRequests = AtomicInteger()
        val refreshRequests = AtomicInteger()
        val appendRequests = AtomicInteger()
        val appending = mutableStateOf(false)
        val outerGridIndex = AtomicInteger()
        var openedBook: SourceBookSummary? = null
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
                                    appending = appending.value,
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
                        onRefresh = { refreshRequests.incrementAndGet() },
                        onLoadMore = {
                            appendRequests.incrementAndGet()
                            appending.value = true
                        },
                        onRetryReplacement = {},
                        onUseOfflineCache = {},
                        onSearch = {},
                        onOpenRemoteLibrary = {},
                        onOpenBook = { openedBook = it },
                        onOpenFeature = {},
                        onOpenVerification = {},
                        onScrollPositionChanged = { _, _, index, offset ->
                            outerGridIndex.set(index)
                            outerGridOffset.set(offset)
                        },
                        coverState = { summary ->
                            val fallback = FallbackSpec(summary.title, "Wenku8")
                            when (summary.identity.remoteBookId) {
                                "1" -> CoverUiState.Ready(readyCover)
                                "2" -> CoverUiState.Absent(fallback)
                                "3" -> CoverUiState.Failed(CoverFailureReason.NETWORK, fallback)
                                else -> CoverUiState.Fallback(fallback)
                            }
                        },
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
        val recommendLabel = composeRule.onNodeWithText("推荐", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val categoryLabel = composeRule.onNodeWithText("分类", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val labelPadding = with(composeRule.density) { 8.dp.toPx() }
        val expectedLabelGap = with(composeRule.density) { 24.dp.toPx() }
        val visibleLabelGap = categoryLabel.left - recommendLabel.right
        assertTrue(abs((firstTab.left - tabRow.left) - labelPadding) <= 1f)
        assertEquals("Visible text-label gap", expectedLabelGap, visibleLabelGap, 1f)
        assertTrue(lastTab.width > firstTab.width)
        assertTrue(lastTab.right < tabRow.right)

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
        assertTrue(abs(firstCard.top - thirdCard.top) < 1f)
        val titleBounds = composeRule.onNodeWithText("轻小说 1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(firstCard.width / firstCard.height - 5f / 7f) < 0.02f)
        assertTrue(firstCard.top <= titleBounds.top)
        (1..3).forEach { index ->
            composeRule.onNodeWithText("轻小说 $index", useUnmergedTree = true).assertIsDisplayed()
            assertTrue("Cover $index must show title only regardless of cover state",
                composeRule.onAllNodesWithText("作者 $index", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        }
        composeRule.onNodeWithTag("source-home-book-1").performClick()
        composeRule.runOnIdle { assertEquals("1", openedBook?.identity?.remoteBookId) }
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
        val fantasyOptions = composeRule.onAllNodesWithText("奇幻")
        fantasyOptions[fantasyOptions.fetchSemanticsNodes().lastIndex].performClick()
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
        val refreshRequestsBeforePanelDrag = refreshRequests.get()
        val replacementRequestsBeforePanelDrag = filterRequests.get()
        val gridAnchorBeforePanelDrag = outerGridIndex.get() to outerGridOffset.get()
        repeat(12) {
            composeRule.onNodeWithTag("source-home-filter-panel").performTouchInput { swipeUp() }
        }
        repeat(12) {
            composeRule.onNodeWithTag("source-home-filter-panel").performTouchInput { swipeDown() }
        }
        composeRule.onNodeWithTag("source-home-filter-panel").performTouchInput { swipeDown() }
        composeRule.runOnIdle {
            assertEquals(
                "Panel drags must leave the observed grid index and offset unchanged",
                gridAnchorBeforePanelDrag,
                outerGridIndex.get() to outerGridOffset.get(),
            )
        }
        composeRule.onNodeWithTag("source-home-filter-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("source-home-primary-tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("source-home-page-controls").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(replacementRequestsBeforePanelDrag, filterRequests.get()) }
        composeRule.runOnIdle { assertEquals(refreshRequestsBeforePanelDrag, refreshRequests.get()) }
        composeRule.runOnIdle { assertEquals(0, appendRequests.get()) }
        composeRule.onNodeWithContentDescription("题材，收起选项").performClick()

        composeRule.onNode(hasStateDescription("排序，展开选项")).performClick()
        composeRule.onNodeWithText("按热门").performClick()
        composeRule.runOnIdle {
            assertEquals(2, filterRequests.get())
            assertEquals("1", submitted.value?.get("sort"))
        }
        composeRule.onNodeWithTag("source-home-filter-sheet").assertDoesNotExist()

        composeRule.onNodeWithTag("source-home-book-grid-category").performScrollToIndex(16)
        composeRule.waitUntil(timeoutMillis = 5_000) { appendRequests.get() >= 1 }
        composeRule.runOnIdle { assertEquals("Append request count after scrolling", 1, appendRequests.get()) }
    }

    @Test
    fun source_home_chrome_collapses_and_reveals_without_moving_catalog_or_requesting_data() {
        val primary = primaryFilter()
        val category = SourceHomeFilter(
            id = "tag",
            label = "题材",
            options = (0..24).map { index ->
                SourceHomeFilterOption(if (index == 0) "school" else "tag$index", if (index == 0) "校园" else "题材 $index")
            },
        )
        val sort = SourceHomeFilter(
            id = "sort",
            label = "排序",
            options = listOf(SourceHomeFilterOption("0", "按更新"), SourceHomeFilterOption("1", "按热门")),
        )
        val selection = mapOf("view" to "category", "tag" to "school", "sort" to "0")
        val page = page(
            filters = listOf(primary, category, sort),
            selectedFilters = selection,
            books = (1..60).map(::book),
            sectionTitle = "分类目录",
        )
        val latestAnchor = java.util.concurrent.atomic.AtomicReference(0 to 0)
        val refreshRequests = AtomicInteger()
        val replacementRequests = AtomicInteger()
        val appendRequests = AtomicInteger()

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
                                    queryKey = "category-school",
                                    selectedFilters = selection,
                                    page = page,
                                ),
                            ),
                        ),
                        remoteLibraryAvailable = true,
                        verificationAvailable = true,
                        onSelectPrimary = {},
                        onSelectFilters = { replacementRequests.incrementAndGet() },
                        onRefresh = { refreshRequests.incrementAndGet() },
                        onLoadMore = { appendRequests.incrementAndGet() },
                        onRetryReplacement = {},
                        onUseOfflineCache = {},
                        onSearch = {},
                        onOpenRemoteLibrary = {},
                        onOpenBook = {},
                        onOpenFeature = {},
                        onOpenVerification = {},
                        onScrollPositionChanged = { _, _, index, offset ->
                            latestAnchor.set(index to offset)
                        },
                        coverState = { summary -> CoverUiState.Fallback(FallbackSpec(summary.title, "Wenku8")) },
                    )
                }
            }
        }

        val grid = composeRule.onNodeWithTag("source-home-book-grid-category")
        composeRule.waitForIdle()
        repeat(5) { grid.performTouchInput { swipeUp() } }
        composeRule.waitForIdle()
        val deepAnchor = latestAnchor.get()
        assertTrue("The catalog swipe must move beyond the initial anchor: $deepAnchor", deepAnchor.first > 0)
        assertTrue("Both context controls should be absent while collapsed",
            composeRule.onAllNodesWithTag("source-home-primary-tabs").fetchSemanticsNodes().isEmpty())
        assertTrue("Collapsed page controls must leave no invisible semantic descendants",
            composeRule.onAllNodesWithTag("source-home-page-controls").fetchSemanticsNodes().isEmpty())
        assertTrue("Collapsed tabs must not retain an invisible click target",
            composeRule.onAllNodesWithTag("tsuyomi-tab-category").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("source-home-pager").performSemanticsAction(SemanticsActions.Expand)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("source-home-primary-tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("source-home-primary-filter-capsule").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("Semantic reveal must not change the grid anchor", deepAnchor, latestAnchor.get()) }
        val revealDistance = (
            composeRule.onNodeWithTag("source-home-primary-tabs").fetchSemanticsNode().boundsInRoot.height +
                composeRule.onNodeWithTag("source-home-page-controls").fetchSemanticsNode().boundsInRoot.height
            ) * 0.75f

        // Re-collapse by scrolling later in the catalog, then pull only a short distance from deep content.
        repeat(2) { grid.performTouchInput { swipeUp() } }
        composeRule.waitForIdle()
        val beforePull = latestAnchor.get()
        assertTrue(beforePull.first >= deepAnchor.first)
        composeRule.mainClock.autoAdvance = false
        try {
            grid.performTouchInput {
                down(Offset(center.x, height * 0.2f))
                moveBy(Offset(0f, revealDistance), delayMillis = 600)
                advanceEventTime(300)
                up()
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle {
                assertEquals("A short reveal pull must be consumed by chrome, not the catalog", beforePull, latestAnchor.get())
                assertEquals(0, refreshRequests.get())
                assertEquals(0, replacementRequests.get())
                assertEquals(0, appendRequests.get())
            }
            composeRule.onNodeWithTag("source-home-primary-tabs").assertIsDisplayed()
            composeRule.onNodeWithTag("source-home-primary-filter-capsule").assertIsDisplayed()
            composeRule.onNodeWithText("校园").assertIsSelected()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }


    @Test
    fun wideSourceHomeCardsKeepThreeColumnsAndASeparatePortraitArtworkLane() {
        val books = (1..3).map(::book)
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                CoverCardPresentationProvider(CoverCardPresentation.WIDE) {
                    TsuyomiTheme(environment = standardEnvironment) {
                        SourceHomeScreen(
                            sourceName = "Wenku8",
                            state = SourceHomeViewState.Content(
                                title = "Wenku8 书库",
                                primaryFilter = null,
                                selectedPrimary = "home",
                                pages = mapOf(
                                    "home" to SourceHomePageViewState(
                                        queryKey = "wide-home",
                                        selectedFilters = emptyMap(),
                                        page = page(
                                            filters = emptyList(),
                                            selectedFilters = emptyMap(),
                                            books = books,
                                            sectionTitle = "宽屏推荐",
                                        ),
                                    ),
                                ),
                            ),
                            remoteLibraryAvailable = false,
                            verificationAvailable = false,
                            onSelectPrimary = {},
                            onSelectFilters = {},
                            onRefresh = {},
                            onLoadMore = {},
                            onRetryReplacement = {},
                            onUseOfflineCache = {},
                            onSearch = {},
                            onOpenRemoteLibrary = {},
                            onOpenBook = {},
                            onOpenFeature = {},
                            onOpenVerification = {},
                            onScrollPositionChanged = { _, _, _, _ -> },
                            coverState = { summary ->
                                CoverUiState.Fallback(FallbackSpec(summary.title, "Wenku8"))
                            },
                        )
                    }
                }
            }
        }

        val first = composeRule.onNodeWithTag("source-home-book-1").fetchSemanticsNode().boundsInRoot
        val third = composeRule.onNodeWithTag("source-home-book-3").fetchSemanticsNode().boundsInRoot

        assertTrue(abs(first.width / first.height - 16f / 9f) < 0.02f)
        assertTrue(abs(first.top - third.top) < 1f)
        assertTrue(third.right > first.right)
    }

    @Test
    fun pager_switches_cached_pages_without_full_screen_loading() {
        val primary = primaryFilter()
        val recommendSelection = mapOf("view" to "recommend")
        val categorySelection = mapOf("view" to "category", "tag" to "school", "sort" to "0")
        val categoryTag = SourceHomeFilter(
            id = "tag",
            label = "题材",
            options = listOf(
                SourceHomeFilterOption("love", "恋爱"),
                SourceHomeFilterOption("school", "校园"),
            ) + (2..31).map { SourceHomeFilterOption("tag$it", "题材 $it") },
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
        val replacementRequests = AtomicInteger()
        val refreshRequests = AtomicInteger()
        val appendRequests = AtomicInteger()
        val activePrimary = java.util.concurrent.atomic.AtomicReference("recommend")
        val observedScroll = mutableMapOf<String, Pair<Int, Int>>()

        composeRule.setContent {
            var selectedPrimary by remember { mutableStateOf(activePrimary.get()) }
            var anchors by remember { mutableStateOf(emptyMap<String, Pair<Int, Int>>()) }
            val pageStates = mapOf(
                "recommend" to SourceHomePageViewState(
                    queryKey = "recommend-query",
                    selectedFilters = recommendSelection,
                    page = recommendPage,
                    firstVisibleItemIndex = anchors["recommend"]?.first ?: 0,
                    firstVisibleItemScrollOffset = anchors["recommend"]?.second ?: 0,
                ),
                "category" to SourceHomePageViewState(
                    queryKey = "category-query",
                    selectedFilters = categorySelection,
                    page = categoryPage,
                    firstVisibleItemIndex = anchors["category"]?.first ?: 0,
                    firstVisibleItemScrollOffset = anchors["category"]?.second ?: 0,
                ),
            )
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                DisplayEnvironmentProvider(standardEnvironment) {
                    TsuyomiTheme(environment = standardEnvironment) {
                        SourceHomeScreen(
                            sourceName = "Wenku8",
                            state = SourceHomeViewState.Content(
                                title = "Wenku8 书库",
                                primaryFilter = primary,
                                selectedPrimary = selectedPrimary,
                                pages = pageStates,
                            ),
                            remoteLibraryAvailable = true,
                            verificationAvailable = true,
                            onSelectPrimary = { selectedPrimary = it; activePrimary.set(it) },
                            onSelectFilters = { replacementRequests.incrementAndGet() },
                            onRefresh = { refreshRequests.incrementAndGet() },
                            onLoadMore = { appendRequests.incrementAndGet() },
                            onRetryReplacement = {},
                            onUseOfflineCache = {},
                            onSearch = {},
                            onOpenRemoteLibrary = {},
                            onOpenFeature = {},
                            onOpenBook = {},
                            onOpenVerification = {},
                            onScrollPositionChanged = { primaryValue, _, index, offset ->
                                val pair = index to offset
                                observedScroll[primaryValue] = pair
                                anchors = anchors + (primaryValue to pair)
                            },
                            coverState = { summary -> CoverUiState.Fallback(FallbackSpec(summary.title, "Wenku8")) },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("推荐 1").assertIsDisplayed()
        composeRule.onNodeWithText("7月新番").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("source-home-primary-filter-capsule").fetchSemanticsNodes().isEmpty())
        val pagerBounds = composeRule.onNodeWithTag("source-home-pager").getUnclippedBoundsInRoot()
        var sawSelectedTagInViewport = false
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("tsuyomi-tab-category").performClick()
            for (frame in 0 until 120) {
                composeRule.mainClock.advanceTimeByFrame()
                if (composeRule.onAllNodesWithTag("filter-capsule-scroll").fetchSemanticsNodes().isNotEmpty()) {
                    val viewport = composeRule.onNodeWithTag("filter-capsule-scroll").getUnclippedBoundsInRoot()
                    if (viewport.left >= pagerBounds.left && viewport.right <= pagerBounds.right) {
                        val school = composeRule.onNodeWithText("校园").assertIsDisplayed().assertIsSelected()
                            .getUnclippedBoundsInRoot()
                        assertTrue("Newly active selected tag clipped on the left", school.left >= viewport.left)
                        assertTrue("Newly active selected tag clipped on the right", school.right <= viewport.right)
                        sawSelectedTagInViewport = true
                    }
                }
                val gridNodes = composeRule.onAllNodesWithTag("source-home-book-grid-category").fetchSemanticsNodes()
                if (sawSelectedTagInViewport && gridNodes.isNotEmpty()) {
                    val settledPage = composeRule.onNodeWithTag("source-home-book-grid-category").getUnclippedBoundsInRoot()
                    if (kotlin.math.abs(settledPage.left.value - pagerBounds.left.value) <= 1f &&
                        kotlin.math.abs(settledPage.right.value - pagerBounds.right.value) <= 1f
                    ) break
                }
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("source-home-book-grid-category").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val page = composeRule.onNodeWithTag("source-home-book-grid-category").getUnclippedBoundsInRoot()
            kotlin.math.abs(page.left.value - pagerBounds.left.value) <= 1f &&
                kotlin.math.abs(page.right.value - pagerBounds.right.value) <= 1f
        }
        assertTrue("Active page must reveal its selected tag before entry settles", sawSelectedTagInViewport)
        composeRule.onNodeWithText("分类 21").assertIsDisplayed()
        composeRule.onNodeWithTag("source-home-book-grid-category").performScrollToIndex(12)
        composeRule.waitUntil(timeoutMillis = 5_000) { (observedScroll["category"]?.first ?: 0) > 0 }
        composeRule.waitForIdle()
        val categoryAnchor = requireNotNull(observedScroll["category"])

        val tagScroll = composeRule.onNodeWithTag("filter-capsule-scroll")
        fun moveSchoolOffscreen() {
            repeat(3) { tagScroll.performTouchInput { swipeRight() } }
            composeRule.waitForIdle()
            val school = composeRule.onNodeWithText("校园").getUnclippedBoundsInRoot()
            val viewport = tagScroll.getUnclippedBoundsInRoot()
            assertTrue("User browsing must move the selected school tag out of view",
                school.left < viewport.left || school.right > viewport.right)
            composeRule.onNodeWithTag("tsuyomi-tab-category").assertIsSelected()
            composeRule.onNodeWithTag("source-home-primary-tabs").assertIsDisplayed()
            composeRule.onNodeWithTag("source-home-page-controls").assertIsDisplayed()
        }
        fun assertSchoolVisible() {
            composeRule.onNodeWithText("校园").assertIsDisplayed().assertIsSelected()
            val school = composeRule.onNodeWithText("校园").getUnclippedBoundsInRoot()
            val viewport = tagScroll.getUnclippedBoundsInRoot()
            assertTrue(school.left >= viewport.left && school.right <= viewport.right)
            val love = composeRule.onNodeWithText("恋爱").getUnclippedBoundsInRoot()
            assertTrue("RTL must retain source tag order", love.right > school.right)
        }

        moveSchoolOffscreen()
        composeRule.onNodeWithTag("tsuyomi-tab-recommend").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { activePrimary.get() == "recommend" }
        composeRule.onNodeWithTag("tsuyomi-tab-recommend").assertIsSelected()
        composeRule.onNodeWithTag("source-home-book-grid-recommend").performScrollToIndex(7)
        composeRule.waitUntil(timeoutMillis = 5_000) { (observedScroll["recommend"]?.first ?: 0) > 0 }
        val recommendationAnchor = requireNotNull(observedScroll["recommend"])
        composeRule.onNodeWithTag("tsuyomi-tab-category").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { activePrimary.get() == "category" }
        composeRule.onNodeWithTag("tsuyomi-tab-category").assertIsSelected()
        composeRule.waitUntil(timeoutMillis = 5_000) { observedScroll["category"] == categoryAnchor }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("source-home-page-controls").assertIsDisplayed()
        assertSchoolVisible()
        assertEquals("Tab return restores the exact catalog index and offset", categoryAnchor, observedScroll["category"])
        assertEquals("Tab return keeps recommendation's independent anchor", recommendationAnchor, observedScroll["recommend"])

        moveSchoolOffscreen()
        val pager = composeRule.onNodeWithTag("source-home-pager")
        pager.performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 5_000) { activePrimary.get() == "recommend" }
        composeRule.onNodeWithTag("tsuyomi-tab-recommend").assertIsSelected()
        assertEquals("Pager preserves recommendation's own catalog anchor", recommendationAnchor, observedScroll["recommend"])
        pager.performTouchInput { swipeRight() }
        composeRule.waitUntil(timeoutMillis = 5_000) { activePrimary.get() == "category" }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tsuyomi-tab-category").assertIsSelected()
        composeRule.onNodeWithTag("source-home-page-controls").assertIsDisplayed()
        assertSchoolVisible()
        assertEquals("Pager return restores the exact category index and offset", categoryAnchor, observedScroll["category"])
        assertEquals("Pager return preserves the independent recommendation anchor", recommendationAnchor, observedScroll["recommend"])
        assertEquals(0, replacementRequests.get())
        assertEquals(0, refreshRequests.get())
        assertEquals(0, appendRequests.get())
    }


    @Test
    fun primaryPagerShortCommitReturnFlingAndReversalSettleWithAlignedSelection() {
        val primary = primaryFilter()
        val selectedFilters = primary.options.associate { option -> option.value to mapOf("view" to option.value) }
        val labels = mapOf(
            "recommend" to "推荐",
            "category" to "分类",
            "ranking" to "排行",
            "completed" to "完结作品",
        )
        val pages = primary.options.mapIndexed { index, option ->
            val selection = selectedFilters.getValue(option.value)
            option.value to SourceHomePageViewState(
                queryKey = "pager-${option.value}",
                selectedFilters = selection,
                page = page(
                    filters = listOf(primary),
                    selectedFilters = selection,
                    books = listOf(book(index + 1, labels.getValue(option.value))),
                    sectionTitle = "${labels.getValue(option.value)}栏目",
                ),
            )
        }.toMap()
        var selectedPrimary by mutableStateOf("recommend")

        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(environment = standardEnvironment) {
                    SourceHomeScreen(
                        sourceName = "Wenku8",
                        state = SourceHomeViewState.Content(
                            title = "Wenku8 书库",
                            primaryFilter = primary,
                            selectedPrimary = selectedPrimary,
                            pages = pages,
                        ),
                        remoteLibraryAvailable = true,
                        verificationAvailable = true,
                        onSelectPrimary = { selectedPrimary = it },
                        onSelectFilters = {},
                        onRefresh = {},
                        onLoadMore = {},
                        onRetryReplacement = {},
                        onUseOfflineCache = {},
                        onSearch = {},
                        onOpenRemoteLibrary = {},
                        onOpenBook = {},
                        onOpenFeature = {},
                        onOpenVerification = {},
                        onScrollPositionChanged = { _, _, _, _ -> },
                        coverState = { summary -> CoverUiState.Fallback(FallbackSpec(summary.title, "Wenku8")) },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val pager = composeRule.onNodeWithTag("source-home-pager")
        val pagerBounds = pager.fetchSemanticsNode().boundsInRoot
        fun assertSelectedContent(label: String) {
            composeRule.onNodeWithTag("tsuyomi-tab-$selectedPrimary").assertIsSelected()
            val contentBounds = composeRule.onNodeWithText(label, useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$label is outside $pagerBounds: $contentBounds", contentBounds.left >= pagerBounds.left)
            assertTrue("$label is outside $pagerBounds: $contentBounds", contentBounds.right <= pagerBounds.right)
            val bookIndex = primary.options.indexOfFirst { it.value == selectedPrimary } + 1
            assertTrue("$selectedPrimary cover must show title only",
                composeRule.onAllNodesWithText("作者 $bookIndex", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
            val pageBounds = composeRule.onNodeWithTag("source-home-book-grid-$selectedPrimary")
                .fetchSemanticsNode().boundsInRoot
            assertEquals("Selected page is horizontally displaced: $pageBounds in $pagerBounds", pagerBounds.left, pageBounds.left, 1f)
            assertEquals("Selected page does not fill the viewport: $pageBounds in $pagerBounds", pagerBounds.right, pageBounds.right, 1f)
        }

        composeRule.mainClock.autoAdvance = false
        try {
            pager.performTouchInput {
                down(center)
                moveBy(Offset(-center.x * 0.4f, 0f), delayMillis = 240)
            }
            val draggedPageBounds = composeRule.onNodeWithTag("source-home-book-grid-recommend")
                .fetchSemanticsNode().boundsInRoot
            pager.performTouchInput { up() }
            composeRule.mainClock.advanceTimeBy(180)
            val returningPageBounds = composeRule.onNodeWithTag("source-home-book-grid-recommend")
                .fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Sub-threshold return did not continue toward rest: $draggedPageBounds to $returningPageBounds",
                returningPageBounds.right > draggedPageBounds.right,
            )
            assertTrue(
                "Sub-threshold return jumped instead of settling: $returningPageBounds in $pagerBounds",
                returningPageBounds.right > pagerBounds.left && returningPageBounds.right < pagerBounds.right,
            )
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals("recommend", selectedPrimary) }
            assertSelectedContent("推荐 1")

            pager.performTouchInput {
                down(Offset(width * 0.85f, center.y))
                moveBy(Offset(-width * 0.7f, 0f), delayMillis = 600)
            }
            composeRule.mainClock.advanceTimeByFrame()
            pager.performTouchInput {
                up()
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals("category", selectedPrimary) }
            assertSelectedContent("分类 2")

            pager.performTouchInput {
                swipe(start = center, end = Offset(center.x - width * 0.15f, center.y), durationMillis = 60)
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals("ranking", selectedPrimary) }
            assertSelectedContent("排行 3")

            pager.performTouchInput {
                down(center)
                moveBy(Offset(-center.x * 0.7f, 0f), delayMillis = 160)
                moveBy(Offset(center.x * 0.45f, 0f), delayMillis = 160)
                up()
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals("ranking", selectedPrimary) }
            assertSelectedContent("排行 3")

            pager.performTouchInput {
                down(center)
                moveBy(Offset(-center.x * 0.6f, 0f), delayMillis = 240)
                up()
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals("completed", selectedPrimary) }
            assertSelectedContent("完结作品 4")

            pager.performTouchInput {
                down(center)
                moveBy(Offset(center.x * 0.6f, 0f), delayMillis = 240)
                up()
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals("ranking", selectedPrimary) }
            assertSelectedContent("排行 3")
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
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
                        onUseOfflineCache = {},
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
        options = listOf("recommend" to "推荐", "category" to "分类", "ranking" to "排行", "completed" to "完结作品")
            .map { (value, label) -> SourceHomeFilterOption(value, label) }
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
