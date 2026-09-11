/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.pm.ActivityInfo
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.BeforeClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.core.security.VerifiedBrowserSession
import org.tsuyomi.feature.browse.BrowseUiState
import org.tsuyomi.shared.sourcecontract.HttpsOrigin

@RunWith(AndroidJUnit4::class)
class ManualVerificationHandoffInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun awaitPreviousSourceRuntimeCleanup() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (quickJsLaneCount() != 0 && System.nanoTime() < deadline) {
            Thread.sleep(50)
        }
        assertEquals(0, quickJsLaneCount())
    }

    @Test
    fun standard_profile_completes_blocked_navigation_and_browser_session_handoff() {
        exerciseVerificationHandoff(DisplayPreference.STANDARD)
    }

    @Test
    fun standard_search_consumes_one_explicit_verified_page_without_native_retry() {
        cleanSessionState()
        Phase2SourceGateway.resetOperationCounts()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(DisplayPreference.STANDARD)
        }
        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("聚合搜索")
        performPlatformClick("聚合搜索")
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("login")
        waitForText("login")
        performPlatformClick("提交搜索")
        waitForText("此来源需要用户手动登录。")
        assertEquals(1, Phase2SourceGateway.searchRequestCount())

        performPlatformClick("手动登录或验证")
        composeRule.onNodeWithContentDescription("打开对应搜索页面").assertIsDisplayed()
        waitForWebViewSettled()
        composeRule.onNodeWithTag("verification-host-identity", useUnmergedTree = true).assertIsDisplayed()
        val identityBounds = composeRule.onNodeWithTag("verification-host-identity", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInWindow
        composeRule.onNodeWithTag("verification-action-dock", useUnmergedTree = true).assertIsDisplayed()
        val webViewBounds = composeRule.onNodeWithTag("verification-webview", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInWindow
        val windowHeight = composeRule.runOnUiThread { composeRule.activity.window.decorView.height.toFloat() }
        val windowWidth = composeRule.runOnUiThread { composeRule.activity.window.decorView.width.toFloat() }
        assertTrue("Verification WebView is not full-window: $webViewBounds", webViewBounds.height >= windowHeight * 0.95f)
        assertTrue(
            "Verification host identity should stay away from common left-aligned site titles: $identityBounds",
            identityBounds.center.x > windowWidth * 0.5f,
        )
        val actionCenters = listOf(
            composeRule.onNodeWithContentDescription("取消验证").fetchSemanticsNode().boundsInWindow.center.y,
            composeRule.onNodeWithContentDescription("打开对应搜索页面").fetchSemanticsNode().boundsInWindow.center.y,
            composeRule.onNodeWithText("使用当前页面").fetchSemanticsNode().boundsInWindow.center.y,
            composeRule.onNodeWithText("保存会话并返回").fetchSemanticsNode().boundsInWindow.center.y,
        )
        assertTrue(
            "Verification actions are not one compact horizontal group: $actionCenters",
            requireNotNull(actionCenters.maxOrNull()) - requireNotNull(actionCenters.minOrNull()) < 2f,
        )
        composeRule.onNodeWithText("此验证由宿主应用发起。", substring = true).assertDoesNotExist()
        val searchHtml = targetContext.assets.open("search.html").bufferedReader().use { it.readText() }
        val searchUrl =
            "https://www.wenku8.net/modules/article/search.php?searchtype=articlename&searchkey=login&page=1"
        installVerifiedPageFixture(searchUrl, searchHtml)
        composeRule.onNodeWithContentDescription("打开对应搜索页面").performClick()
        waitForText("使用当前页面")
        waitForWebViewSettled()
        composeRule.onNodeWithText("使用当前页面").performClick()
        composeRule.waitUntil(15_000) {
            runCatching {
                composeRule.onNodeWithTag("verification-action-dock", useUnmergedTree = true).assertDoesNotExist()
            }.isSuccess
        }
        waitForVerifiedOutcome(
            successText = "雾港纪事",
            unboundText = "当前页面未与暂停的搜索请求绑定。请点击“打开对应搜索页面”，等待自动跳转和页面加载完成后重试。",
        )
        assertEquals(1, Phase2SourceGateway.searchRequestCount())
        performPlatformClick("雾港纪事")
        waitForText("简介")
    }

    @Test
    fun standard_detail_controls_stay_inside_landscape_system_bars() {
        cleanSessionState()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(DisplayPreference.STANDARD)
        }
        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("聚合搜索")
        performPlatformClick("聚合搜索")
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        waitForText("fixture")
        performPlatformClick("提交搜索")
        waitForText("雾港纪事")
        performPlatformClick("雾港纪事")
        waitForText("上次更新：2026-02-03")
        composeRule.onNodeWithTag("book-detail-scroll", useUnmergedTree = true).performScrollToIndex(0)
        try {
            composeRule.runOnUiThread {
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            composeRule.waitUntil(15_000) {
                composeRule.runOnUiThread {
                    val view = composeRule.activity.window.decorView
                    view.width > view.height
                }
            }
            waitForText("上次更新：2026-02-03", timeoutMillis = 30_000)
            val (safeLeft, safeRight) = composeRule.runOnUiThread {
                val view = composeRule.activity.window.decorView
                val insets = requireNotNull(ViewCompat.getRootWindowInsets(view)).getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
                insets.left.toFloat() to (view.width - insets.right).toFloat()
            }
            listOf("detail-rating-row", "detail-library-action").forEach { tag ->
                val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                node.performScrollTo()
                val bounds = node.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
                assertTrue("$tag extends beneath the left system bar: $bounds", bounds.left >= safeLeft)
                assertTrue("$tag extends beneath the right system bar: $bounds > $safeRight", bounds.right <= safeRight)
            }
            composeRule.onNodeWithContentDescription("更多加入选项").performClick()
            waitForText("稍后再读")
            pressBack()
        } finally {
            composeRule.runOnUiThread {
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            composeRule.waitUntil(15_000) {
                composeRule.runOnUiThread {
                    val view = composeRule.activity.window.decorView
                    view.width < view.height
                }
            }
        }
    }

    @Test
    fun standard_detail_author_link_submits_once_and_restores_applied_results() {
        cleanSessionState()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(DisplayPreference.STANDARD)
        }
        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("聚合搜索")
        performPlatformClick("聚合搜索")
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        waitForText("fixture")
        performPlatformClick("提交搜索")
        waitForText("雾港纪事")
        composeRule.onNodeWithText("星环邮差").assertIsDisplayed()
        performPlatformClick("雾港纪事")
        waitForText("上次更新：2026-02-03")
        composeRule.onNodeWithText("尚未开始").assertDoesNotExist()
        composeRule.onNodeWithText("已有阅读进度").assertDoesNotExist()
        Phase2SourceGateway.resetOperationCounts()

        composeRule.onNodeWithTag("detail-author").performClick()
        waitForText("搜索作者")
        waitForText("雾港纪事")
        composeRule.onNode(hasSetTextAction()).assertTextContains("林川")
        composeRule.onNodeWithText("星环邮差").assertDoesNotExist()
        assertEquals(1, Phase2SourceGateway.searchRequestCount())

        performPlatformClick("雾港纪事")
        waitForText("上次更新：2026-02-03")
        pressBack()
        waitForText("搜索作者")
        composeRule.onNodeWithText("雾港纪事").assertIsDisplayed()
        composeRule.onNodeWithText("星环邮差").assertDoesNotExist()
        assertEquals(1, Phase2SourceGateway.searchRequestCount())
        composeRule.activityRule.scenario.recreate()
        waitForText("搜索作者")
        composeRule.onNodeWithText("雾港纪事").assertIsDisplayed()
        assertEquals(1, Phase2SourceGateway.searchRequestCount())
        composeRule.onNode(hasSetTextAction()).performTextReplacement("fixture")
        waitForText("fixture")
        performPlatformClick("提交搜索")
        waitForText("星环邮差")
        assertEquals(2, Phase2SourceGateway.searchRequestCount())
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())
    }

    @Test
    fun standard_detail_consumes_one_explicit_verified_page_without_native_replay() {
        cleanSessionState()
        Phase2SourceGateway.resetOperationCounts()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(DisplayPreference.STANDARD)
        }
        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("聚合搜索")
        performPlatformClick("聚合搜索")
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        waitForText("fixture")
        performPlatformClick("提交搜索")
        waitForText("雾港纪事")
        Phase2SourceGateway.requireVerificationForNextDetailRequest()
        performPlatformClick("雾港纪事")
        waitForText("打开手动登录或验证")
        assertEquals(1, Phase2SourceGateway.detailRequestCount())
        assertEquals(1, Phase2SourceGateway.directoryRequestCount())

        composeRule.onNodeWithText("打开手动登录或验证").performClick()
        composeRule.onNodeWithContentDescription("打开对应详情页面").assertIsDisplayed()
        waitForWebViewSettled()
        val detailHtml = targetContext.assets.open("detail.html").bufferedReader().use { it.readText() }
        val detailUrl = "https://www.wenku8.net/book/1234.htm"
        installVerifiedPageFixture(detailUrl, detailHtml)
        composeRule.onNodeWithContentDescription("打开对应详情页面").performClick()
        waitForText("使用当前页面")
        waitForWebViewSettled()
        composeRule.onNodeWithText("使用当前页面").performClick()
        waitForVerifiedOutcome(
            successText = "简介",
            unboundText = "当前页面未与暂停的详情请求绑定。请点击“打开对应详情页面”，等待自动跳转和页面加载完成后重试。",
        )
        assertEquals(1, Phase2SourceGateway.detailRequestCount())
        assertEquals(2, Phase2SourceGateway.directoryRequestCount())
    }

    @Test
    fun standard_chapter_handoff_retains_directory_for_adjacent_navigation() {
        cleanSessionState()
        Phase2SourceGateway.resetOperationCounts()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(DisplayPreference.STANDARD)
        }
        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("聚合搜索")
        performPlatformClick("聚合搜索")
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        waitForText("fixture")
        performPlatformClick("提交搜索")
        waitForText("雾港纪事")
        performPlatformClick("雾港纪事")
        waitForText("简介")
        waitForDirectoryChapterIndex()
        waitForText("第一章 雾中的灯塔")
        Phase2SourceGateway.requireVerificationForNextChapterRequest()
        performPlatformClick("第一章 雾中的灯塔")
        waitForText("打开手动登录或验证")
        assertEquals(1, Phase2SourceGateway.chapterRequestCount())
        assertEquals(1, Phase2SourceGateway.directoryRequestCount())

        performPlatformClick("打开手动登录或验证")
        composeRule.onNodeWithContentDescription("打开对应章节页面").assertIsDisplayed()
        waitForWebViewSettled()
        val chapterHtml = targetContext.assets.open("chapter.html").bufferedReader().use { it.readText() }
        val chapterUrl = "https://www.wenku8.net/modules/article/reader.php?aid=1234&cid=10001"
        installVerifiedPageFixture(chapterUrl, chapterHtml)
        composeRule.onNodeWithContentDescription("打开对应章节页面").performClick()
        waitForText("使用当前页面")
        waitForWebViewSettled()
        composeRule.onNodeWithText("使用当前页面").performClick()
        waitForVerifiedOutcome(
            successText = "第一章 雾中的灯塔",
            unboundText = "当前页面未与暂停的章节请求绑定。请点击“打开对应章节页面”，等待自动跳转和页面加载完成后重试。",
        )
        assertEquals(1, Phase2SourceGateway.chapterRequestCount())

        composeRule.onNodeWithText("下一章").assertIsEnabled().performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) { Phase2SourceGateway.chapterRequestCount() == 2 }
        waitForText("第二章 旧船票")
    }


    @Ignore("E-ink is frozen by review-policy.json; retain for profile restoration only")
    @Test
    fun e_ink_profile_completes_blocked_navigation_and_browser_session_handoff() {
        exerciseVerificationHandoff(DisplayPreference.EINK)
    }

    @Test
    fun recreation_closes_the_old_source_runtime_before_opening_a_new_session() {
        cleanSessionState()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(DisplayPreference.STANDARD)
        }
        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("聚合搜索")
        performPlatformClick("聚合搜索")
        waitForText("输入关键词后搜索")
        waitForQuickJsLaneCount(1)

        composeRule.activityRule.scenario.recreate()

        waitForText("输入关键词后搜索")
        waitForQuickJsLaneCount(1)
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        waitForText("fixture")
        performPlatformClick("提交搜索")
        waitForText("雾港纪事")
    }

    @Test
    fun popping_the_browse_entry_closes_its_source_runtime() {
        cleanSessionState()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(DisplayPreference.STANDARD)
        }
        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("聚合搜索")
        performPlatformClick("聚合搜索")
        waitForText("输入关键词后搜索")
        waitForQuickJsLaneCount(1)

        pressBack()
        waitForText("聚合搜索")
        waitForQuickJsLaneCount(1)
        pressBack()
        waitForText("书架")
        waitForQuickJsLaneCount(0)
    }

    @Test
    fun standard_detail_uses_stable_app_bar_title_and_visible_cache_action() {
        cleanSessionState()
        runBlocking {
            val application = composeRule.activity.application as TsuyomiApplication
            application.libraryRepository.libraryEntries()
                .filter { it.book.title == "雾港纪事" }
                .forEach { application.libraryRepository.removeFromLibrary(it.book.identity) }
            application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
        }
        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("聚合搜索")
        performPlatformClick("聚合搜索")
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        waitForText("fixture")
        performPlatformClick("提交搜索")
        waitForText("雾港纪事")
        performPlatformClick("雾港纪事")

        waitForText("书籍详情")
        composeRule.onNodeWithContentDescription("缓存详情与目录").assertIsDisplayed()
        waitForText("简介")
        composeRule.onNodeWithContentDescription("更多加入选项").performClick()
        waitForText("稍后再读")
        composeRule.onNodeWithText("稍后再读").performClick()
        waitForText("已完成：更新稍后再读")
        composeRule.onNodeWithContentDescription("更多加入选项").performClick()
        waitForStateDescription("detail-read-later-action", "已稍后再读")
        composeRule.onNodeWithText("稍后再读").performClick()
        composeRule.onNodeWithContentDescription("更多加入选项").performClick()
        waitForStateDescription("detail-read-later-action", "未稍后再读")
    }

    @Test
    fun standard_library_book_reopens_canonical_source_detail_without_browse_anchor() {
        cleanSessionState()
        runBlocking {
            val application = composeRule.activity.application as TsuyomiApplication
            application.libraryRepository.libraryEntries()
                .filter { it.book.title == "雾港纪事" }
                .forEach { application.libraryRepository.removeFromLibrary(it.book.identity) }
            application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
        }
        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("聚合搜索")
        performPlatformClick("聚合搜索")
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        waitForText("fixture")
        performPlatformClick("提交搜索")
        waitForText("雾港纪事")
        performPlatformClick("雾港纪事")
        waitForText("简介")
        composeRule.onNodeWithContentDescription("更多加入选项").performClick()
        composeRule.onNodeWithText("稍后再读").performClick()
        waitForText("已完成：更新稍后再读")

        performPlatformClick("书架")
        waitForText("书架")
        waitForText("雾港纪事")
        performPlatformClick("雾港纪事")
        waitForText("简介")
        composeRule.onNodeWithTag("detail-cover").fetchSemanticsNode()
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())

        runBlocking {
            val application = composeRule.activity.application as TsuyomiApplication
            application.libraryRepository.libraryEntries()
                .filter { it.book.title == "雾港纪事" }
                .forEach { application.libraryRepository.removeFromLibrary(it.book.identity) }
        }
    }
    @Test
    fun standard_reader_promotes_atlas_chrome_and_adjacent_chapter_navigation() {
        cleanSessionState()
        runBlocking {
            val application = composeRule.activity.application as TsuyomiApplication
            application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
            application.libraryRepository.libraryEntries()
                .filter { it.book.title == "雾港纪事" }
                .forEach { application.libraryRepository.removeFromLibrary(it.book.identity) }
            application.readerPreferencesRepository.update(
                PortableReaderPreferences(flow = "scroll", fontScale = 1.0, lineHeight = 1.5, theme = "paper"),
            )
        }
        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("聚合搜索")
        performPlatformClick("聚合搜索")
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        waitForText("fixture")
        performPlatformClick("提交搜索")
        waitForText("雾港纪事")
        performPlatformClick("雾港纪事")
        waitForText("简介")
        waitForDirectoryChapterIndex()
        waitForText("第一章 雾中的灯塔")
        composeRule.onNodeWithText("第一章 雾中的灯塔").performClick()

        waitForText("第一章 雾中的灯塔")
        waitForText("设置")
        composeRule.onNodeWithText("书架").assertDoesNotExist()
        val progressSlider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        progressSlider.performTouchInput { click(center) }
        waitForText("邮差把未署名的信收入防水袋，沿着旧轨道继续前行。")

        composeRule.onNodeWithTag("reader-content-surface").performTouchInput { click(center) }
        waitForTextGone("设置")
        composeRule.onNodeWithTag("reader-content-surface").performTouchInput { click(center) }
        waitForText("设置")
        composeRule.onNodeWithTag("reader-content-surface").performTouchInput { click(center) }
        waitForTextGone("设置")
        composeRule.onNodeWithTag("reader-content-surface").performTouchInput { click(center) }
        waitForText("设置")

        composeRule.onNodeWithText("设置").performClick()
        val quickActionTags = listOf(
            "reader-quick-lock-portrait",
            "reader-quick-reading-info",
            "reader-quick-immersive",
            "reader-quick-flow",
        )
        val typographySliderTags = listOf(
            "reader-typography-font-size-slider",
            "reader-typography-line-spacing-slider",
            "reader-typography-margin-slider",
            "reader-typography-paragraph-spacing-slider",
        )
        val quickActionBounds = quickActionTags.map { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
        }
        val density = composeRule.activity.resources.displayMetrics.density
        quickActionBounds.forEach { bounds ->
            assertTrue(bounds.width >= 48f * density - 1f)
            assertTrue(bounds.height >= 48f * density - 1f)
        }
        val compactSliderWidths = typographySliderTags.map { tag ->
            val sliderBounds = composeRule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(sliderBounds.width >= 220f * density - 1f)
            sliderBounds.width
        }
        assertEquals(quickActionBounds[0].top, quickActionBounds[1].top, 1f)
        assertEquals(quickActionBounds[2].top, quickActionBounds[3].top, 1f)
        assertTrue(quickActionBounds[2].top > quickActionBounds[0].top)
        waitForText("全部设置")
        composeRule.onNodeWithText("连续滚动").performClick()
        waitForText("分页")
        composeRule.onNodeWithText("全部设置").performClick()
        waitForText("排版")
        waitForText("页面")
        quickActionTags.forEach { tag -> composeRule.onNodeWithTag(tag).assertDoesNotExist() }
        composeRule.onNodeWithTag("reader-full-settings-groups").assertIsDisplayed()
        val expandedSliderWidths = typographySliderTags.map { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.width
        }
        expandedSliderWidths.forEachIndexed { index, width ->
            assertTrue(width >= compactSliderWidths[index] + 32f * density)
        }
        composeRule.onNodeWithTag("reader-settings-content").performTouchInput {
            swipe(
                start = center,
                end = Offset(center.x, bottom + 400f),
                durationMillis = 500,
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader-settings-sheet").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("reader-settings-sheet").assertDoesNotExist()
        waitForText("设置")

        composeRule.onNodeWithText("设置").performClick()
        waitForText("全部设置")
        composeRule.onNodeWithText("全部设置").performClick()
        waitForText("排版")
        composeRule.onNodeWithTag("reader-full-settings-groups").assertIsDisplayed()

        pressBack()
        waitForText("全部设置")
        waitForText("分页")
        quickActionTags.forEach { tag -> composeRule.onNodeWithTag(tag).assertIsDisplayed() }
        typographySliderTags.forEach { tag -> composeRule.onNodeWithTag(tag).assertIsDisplayed() }
        composeRule.onNodeWithTag("reader-full-settings-groups").assertDoesNotExist()
        pressBack()
        waitForTextGone("全部设置")
        waitForText("邮差把未署名的信收入防水袋，沿着旧轨道继续前行。")
        composeRule.onNodeWithTag("reader-content-surface").performTouchInput {
            click(centerRight)
        }
        waitForText("第二章 旧船票")

        composeRule.onNodeWithText("目录").performClick()
        waitForText("第一章 雾中的灯塔")
        composeRule.onNodeWithText("第一章 雾中的灯塔").performClick()
        waitForText("第一章 雾中的灯塔")
        waitForText("邮差把未署名的信收入防水袋，沿着旧轨道继续前行。")

        composeRule.activityRule.scenario.recreate()

        waitForText("第一章 雾中的灯塔")
        waitForText("邮差把未署名的信收入防水袋，沿着旧轨道继续前行。")
        pressBack()
        waitForTextGone("设置")
        pressBack()
        waitForText("简介")
    }


    @Test
    fun standard_source_home_consumes_one_explicit_verified_page_without_native_retry() {
        cleanSessionState()
        Phase2SourceGateway.resetOperationCounts()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(DisplayPreference.STANDARD)
        }

        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("Wenku8")
        Phase2SourceGateway.requireVerificationForNextHomeRequest()
        performPlatformClick("Wenku8")
        waitForText("来源主页需要登录验证")
        assertEquals(1, Phase2SourceGateway.homeRequestCount())

        composeRule.onNodeWithText("前往登录验证").performClick()
        composeRule.onNodeWithContentDescription("打开对应主页").assertIsDisplayed()
        waitForWebViewSettled()
        val homeHtml = targetContext.assets.open("home-index.html").bufferedReader().use { it.readText() }
        val homeUrl = "https://www.wenku8.net/index.php"
        installVerifiedPageFixture(homeUrl, homeHtml)
        composeRule.onNodeWithContentDescription("打开对应主页").performClick()
        waitForText("使用当前页面")
        waitForWebViewSettled()
        composeRule.onNodeWithText("使用当前页面").performClick()

        waitForVerifiedOutcome(
            successText = "Wenku8 书库",
            unboundText = "当前页面未与暂停的搜索请求绑定。请点击“打开对应搜索页面”，等待自动跳转和页面加载完成后重试。",
        )
        waitForText("推荐")
        assertEquals(1, Phase2SourceGateway.homeRequestCount())
    }

    @Test
    fun standard_source_home_uses_cached_tab_pager_and_automatic_append() {
        cleanSessionState()
        Phase2SourceGateway.resetOperationCounts()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(DisplayPreference.STANDARD)
        }

        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("Wenku8")
        performPlatformClick("Wenku8")
        waitForText("Wenku8 书库")
        assertTrue(composeRule.onAllNodesWithText("来源主页").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Wenku8 · 本页 2 本").fetchSemanticsNodes().isEmpty())
        assertEquals(1, composeRule.onAllNodesWithText("Wenku8 书库").fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("搜索此来源").assertIsDisplayed()

        listOf("推荐", "分类", "排行", "完结").forEach(::waitForText)
        composeRule.onNodeWithTag("source-home-pager").assertIsDisplayed()
        composeRule.onNodeWithTag("source-home-book-grid-recommend").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("source-home-quick-actions").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("加载下一页").fetchSemanticsNodes().isEmpty())
        listOf("7月新番", "新书风云榜", "本周会员推荐榜").forEach(::waitForText)
        assertTrue(composeRule.onAllNodesWithTag("source-home-primary-filter-capsule").fetchSemanticsNodes().isEmpty())
        composeRule.waitUntil(timeoutMillis = 15_000) { Phase2SourceGateway.homeRequestCount() == 1 }
        val first = composeRule.onNodeWithTag("source-home-book-1234").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithTag("source-home-book-5678").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(first.top - second.top) < 1f)
        assertTrue(first.left < second.left)
        composeRule.onNodeWithTag("source-home-book-grid-recommend").performScrollToIndex(9)
        composeRule.onNodeWithTag("source-home-feature-sugoi-2026").assertIsDisplayed().performClick()
        waitForText("文库部门 TOP10")
        waitForText("单行本部门 TOP10")
        assertTrue(composeRule.onAllNodesWithTag("source-home-primary-tabs").fetchSemanticsNodes().isEmpty())
        composeRule.waitUntil(timeoutMillis = 15_000) { Phase2SourceGateway.homeRequestCount() == 2 }
        pressBack()
        waitForText("Wenku8 书库")
        composeRule.onNodeWithTag("source-home-feature-sugoi-2026").assertIsDisplayed()
        assertEquals(2, Phase2SourceGateway.homeRequestCount())



        composeRule.onNodeWithText("分类").performClick()
        waitForText("按更新")
        composeRule.onNodeWithContentDescription("题材，展开选项").performClick()
        waitForText("奇幻")
        composeRule.onAllNodesWithText("奇幻")[1].performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) { Phase2SourceGateway.homeRequestCount() == 6 }

        composeRule.onNodeWithText("推荐").performClick()
        composeRule.onNodeWithTag("source-home-book-grid-recommend").assertIsDisplayed()
        assertEquals(6, Phase2SourceGateway.homeRequestCount())
        composeRule.onNodeWithText("分类").performClick()
        composeRule.onNodeWithTag("source-home-book-grid-category").assertIsDisplayed()
        assertEquals(6, Phase2SourceGateway.homeRequestCount())

        composeRule.onNodeWithTag("source-home-book-1234").performClick()
        waitForText("简介")
        assertEquals(6, Phase2SourceGateway.homeRequestCount())
        pressBack()
        waitForText("Wenku8 书库")
        composeRule.onNodeWithTag("source-home-book-grid-category").assertIsDisplayed()
        assertEquals(6, Phase2SourceGateway.homeRequestCount())
    }

    @Test
    fun standard_remote_library_requires_explicit_refresh_and_copies_locally_only() {
        cleanSessionState()
        Phase2SourceGateway.resetOperationCounts()
        runBlocking {
            val application = composeRule.activity.application as TsuyomiApplication
            application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
            application.libraryRepository.libraryEntries()
                .filter { it.book.identity.sourceId == WENKU8_SOURCE_ID }
                .forEach { application.libraryRepository.removeFromLibrary(it.book.identity) }
            val remotePolicy = requireNotNull(application.libraryRepository.sourceRemotePolicy(WENKU8_SOURCE_ID))
            application.libraryRepository.saveSourceRemotePolicy(
                remotePolicy.copy(firstImportPromptDismissed = false),
            )
            VerifiedBrowserSessionStore(composeRule.activity).put(
                SourceCredentialPartition(WENKU8_SOURCE_ID, WENKU8_ORIGIN),
                VerifiedBrowserSession("fixture_session=accepted", "fixture-webview-agent/1"),
            )
        }

        waitForText("书架")
        performPlatformClick("浏览")
        waitForText("Wenku8")
        composeRule.onNodeWithContentDescription("更多 Wenku8 操作").performClick()
        waitForText("网站收藏")
        composeRule.onNodeWithText("网站收藏").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("remote-library-surface").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("remote-library-surface").assertIsDisplayed()
        if (platformHasText("网站镜像")) {
            composeRule.onNodeWithText("知道了").performClick()
            waitForTextGone("网站镜像")
        }
        assertEquals(0, Phase2SourceGateway.remoteLibraryReadCount())
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())
        runBlocking {
            val application = composeRule.activity.application as TsuyomiApplication
            assertTrue(application.libraryRepository.libraryEntries().none { it.book.identity.sourceId == WENKU8_SOURCE_ID })
        }

        composeRule.onNodeWithContentDescription("刷新列表").performClick()
        waitForText("雾港纪事")
        waitForText("星环邮差")
        val readCountAfterRefresh = Phase2SourceGateway.remoteLibraryReadCount()
        assertTrue(readCountAfterRefresh > 0)
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())

        composeRule.onNodeWithTag("library-book-$WENKU8_SOURCE_ID-1234").performTouchInput { longClick() }
        waitForText("已选择 1 项")
        composeRule.onNodeWithContentDescription("复制所选到本地书架").performClick()
        waitForText("复制网站收藏到本地书架")
        composeRule.onNodeWithText("确认复制到本地书架").performClick()
        waitForText("已复制 1 本，新增 1 本到本地书架；未向网站写入。")
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())

        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("全部复制到本地书架").performClick()
        waitForText("已复制 2 本，新增 1 本到本地书架；未向网站写入。")
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())
        runBlocking {
            val application = composeRule.activity.application as TsuyomiApplication
            assertEquals(
                setOf("1234", "5678"),
                application.libraryRepository.libraryEntries()
                    .filter { it.book.identity.sourceId == WENKU8_SOURCE_ID }
                    .map { it.book.identity.remoteBookId }
                    .toSet(),
            )
        }

        composeRule.activityRule.scenario.recreate()
        waitForText("雾港纪事")
        waitForText("星环邮差")
        assertEquals(readCountAfterRefresh, Phase2SourceGateway.remoteLibraryReadCount())
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())
    }


    private fun exerciseVerificationHandoff(profile: DisplayPreference) {
        cleanSessionState()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(profile)
        }
        waitForText("书架")
        performPlatformClick("浏览")
        val sourceEntryLabel = if (profile == DisplayPreference.EINK) "进入内容源" else "聚合搜索"
        waitForText(sourceEntryLabel)
        performPlatformClick(sourceEntryLabel)
        val queryLabel = if (profile == DisplayPreference.EINK) "搜索书名" else "搜索"
        waitForText(queryLabel)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("challenge")
        waitForText("challenge")
        performPlatformClick(if (profile == DisplayPreference.EINK) "搜索" else "提交搜索")
        waitForText("此来源要求用户手动完成安全验证。")
        performPlatformClick("手动登录或验证")
        val completionLabel = if (profile == DisplayPreference.EINK) "已完成" else "保存会话并返回"
        waitForText(completionLabel)
        composeRule.runOnUiThread {
            requireNotNull(findWebView(composeRule.activity.window.decorView))
                .loadUrl("https://outside.example/blocked")
        }
        waitForText("已阻止跳转到未授权站点。仅允许此内容源声明的 HTTPS 站点。")


        val cookieAccepted = AtomicBoolean(false)
        val cookieSet = CountDownLatch(1)
        composeRule.runOnUiThread {
            CookieManager.getInstance().setCookie(
                WENKU8_ORIGIN.canonical,
                "fixture_session=accepted; Path=/; Secure",
            ) { accepted ->
                cookieAccepted.set(accepted)
                cookieSet.countDown()
            }
        }
        assertTrue(cookieSet.await(5, TimeUnit.SECONDS))
        assertTrue(cookieAccepted.get())

        composeRule.onNodeWithText(completionLabel).performClick()
        waitForTextGone(completionLabel)

        val storedSession = requireNotNull(
            VerifiedBrowserSessionStore(targetContext).getSnapshot(
                SourceCredentialPartition(WENKU8_SOURCE_ID, WENKU8_ORIGIN),
            ),
        ).session
        assertTrue(storedSession.requestCookies.contains("fixture_session=accepted"))
        assertTrue(storedSession.userAgent.isNotBlank())
        waitForText("challenge")
        performPlatformClick(if (profile == DisplayPreference.EINK) "搜索" else "提交搜索")
        waitForText("雾港纪事")

        if (profile != DisplayPreference.STANDARD) return

        pressBack()
        waitForText("聚合搜索")
        waitForText("Wenku8")
        performPlatformClick("Wenku8")
        waitForText("Wenku8 书库")
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        waitForText("登录验证")
        composeRule.onNodeWithText("登录验证").performClick()
        waitForText(completionLabel)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.runOnUiThread {
                val view = findWebView(composeRule.activity.window.decorView)
                val restoredCookies = CookieManager.getInstance().getCookie(WENKU8_ORIGIN.canonical).orEmpty()
                view?.settings?.userAgentString == storedSession.userAgent &&
                    restoredCookies.contains("fixture_session=accepted")
            }
        }

        if (profile == DisplayPreference.EINK) {
            composeRule.onNodeWithText("取消").performClick()
            waitForText(sourceEntryLabel)
        } else {
            composeRule.onNodeWithContentDescription("取消验证").performClick()
            waitForText("搜索此来源")
        }
        val preservedSession = requireNotNull(
            VerifiedBrowserSessionStore(targetContext).getSnapshot(
                SourceCredentialPartition(WENKU8_SOURCE_ID, WENKU8_ORIGIN),
            ),
        ).session
        assertTrue(preservedSession.requestCookies.contains("fixture_session=accepted"))
        assertEquals(storedSession.userAgent, preservedSession.userAgent)
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { index ->
            findWebView(view.getChildAt(index))
        }
        else -> null
    }


    private fun waitForWebViewSettled() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.runOnUiThread {
                val webView = findWebView(composeRule.activity.window.decorView)
                webView != null && !webView.url.isNullOrBlank() && webView.progress == 100
            }
        }
    }


    private fun installVerifiedPageFixture(url: String, html: String) {
        composeRule.runOnUiThread {
            val webView = requireNotNull(findWebView(composeRule.activity.window.decorView))
            val delegate = webView.webViewClient
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    delegate.shouldOverrideUrlLoading(view, request)

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = when {
                    request.isForMainFrame && request.url.toString() == url -> WebResourceResponse(
                        "text/html",
                        "utf-8",
                        200,
                        "OK",
                        emptyMap(),
                        ByteArrayInputStream(html.encodeToByteArray()),
                    )
                    request.url.host == "www.wenku8.net" -> WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0)),
                    )
                    else -> delegate.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    delegate.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    delegate.onPageFinished(view, url)
                }
            }
        }
    }

    private fun waitForVerifiedOutcome(successText: String, unboundText: String) {
        val rejectedText = "当前页面与刚才请求不一致，请重新打开对应页面"
        var failure: String? = null
        composeRule.waitUntil(timeoutMillis = 60_000) {
            when {
                platformHasText(rejectedText) -> {
                    failure = rejectedText
                    true
                }
                platformHasText(unboundText) -> {
                    failure = unboundText
                    true
                }
                !platformHasText("使用当前页面") && platformHasText(successText) -> true
                else -> false
            }
        }
        failure?.let(::error)
    }

    private fun waitForDirectoryChapterIndex() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("2章", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("book-detail-scroll").performScrollToIndex(5)
    }

    private fun waitForText(text: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            platformHasText(text)
        }
    }

    private fun waitForTextGone(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !platformHasText(text)
        }
    }



    private fun platformHasText(text: String): Boolean =
        runCatching {
            composeRule.onAllNodesWithText(text, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithContentDescription(text, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false) || runCatching {
            traversePlatformNodes { node ->
                node.text?.toString() == text || node.contentDescription?.toString() == text
            }
        }.getOrDefault(false)

    private fun performPlatformClick(text: String) {
        val composeClicked = runCatching {
            composeRule.onNodeWithText(text).performClick()
            true
        }.getOrDefault(false) || runCatching {
            composeRule.onNodeWithContentDescription(text).performClick()
            true
        }.getOrDefault(false)
        if (composeClicked) return

        val clicked = runCatching {
            traversePlatformNodes { node ->
                val matches =
                    node.text?.toString() == text || node.contentDescription?.toString() == text
                matches && performPlatformNodeClick(node)
            }
        }.getOrDefault(false)
        check(clicked) { "Could not click platform node: $text; accessibility=${platformTextSnapshot()}" }
    }

    private fun performPlatformNodeClick(
        node: android.view.accessibility.AccessibilityNodeInfo,
    ): Boolean {
        var candidate = node
        var ownsCandidate = false
        return try {
            while (!candidate.isClickable) {
                val parent = candidate.parent ?: return false
                if (ownsCandidate) candidate.recycle()
                candidate = parent
                ownsCandidate = true
            }
            candidate.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        } finally {
            if (ownsCandidate) candidate.recycle()
        }
    }

    private inline fun traversePlatformNodes(
        visit: (android.view.accessibility.AccessibilityNodeInfo) -> Boolean,
    ): Boolean {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return false
        val pending = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 512) {
            val node = pending.removeFirst()
            val matched = try {
                if (visit(node)) {
                    true
                } else {
                    repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
                    false
                }
            } finally {
                node.recycle()
            }
            if (matched) {
                pending.forEach(android.view.accessibility.AccessibilityNodeInfo::recycle)
                return true
            }
        }
        pending.forEach(android.view.accessibility.AccessibilityNodeInfo::recycle)
        return false
    }

    private fun platformTextSnapshot(): String = runCatching {
        val values = mutableListOf<String>()
        traversePlatformNodes { node ->
            node.text?.toString()?.let(values::add)
            node.contentDescription?.toString()?.let(values::add)
            false
        }
        "texts=${values.distinct().joinToString(" | ")}"
    }.getOrElse { error -> "error=${error::class.java.simpleName}:${error.message}" }

    private fun waitForStateDescription(tag: String, stateDescription: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).assert(hasStateDescription(stateDescription))
            }.isSuccess
        }
    }

    private fun waitForQuickJsLaneCount(expected: Int) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            quickJsLaneCount() == expected
        }
    }

    private fun quickJsLaneCount(): Int = Thread.getAllStackTraces().keys.count { thread ->
        thread.isAlive && thread.name.startsWith("tsuyomi-quickjs-")
    }

    companion object {
        private const val WENKU8_SOURCE_ID = "org.tsuyomi.wenku8"
        private val WENKU8_ORIGIN = HttpsOrigin("https://www.wenku8.net")
        private val targetContext
            get() = InstrumentationRegistry.getInstrumentation().targetContext

        @BeforeClass
        @JvmStatic
        fun installFixtureSource() = runBlocking {
            cleanPrivateState()
            val fixture = File(targetContext.cacheDir, "wenku8-fixture.hxp")
            targetContext.assets.open("wenku8-fixture.hxp").use { input ->
                fixture.outputStream().use(input::copyTo)
            }
            val application = targetContext.applicationContext as TsuyomiApplication
            val controller = SourceInstallController(targetContext, application.libraryRepository)
            controller.prepare(Uri.fromFile(fixture), targetContext.contentResolver)
            check(controller.state is BrowseUiState.Approval) { "Fixture source was not prepared" }
            controller.approve(allowDowngrade = false)
            check(controller.state is BrowseUiState.Installed) { "Fixture source was not installed" }
        }

        @AfterClass
        @JvmStatic
        fun cleanUpFixtureSource() {
            cleanPrivateState()
        }

        private fun cleanSessionState() {
            Phase2SourceGateway.clearLiveValidationMode(targetContext)
            File(targetContext.noBackupFilesDir, "normalized-source-content").deleteRecursively()
            File(targetContext.noBackupFilesDir, "source-credentials").deleteRecursively()
            File(targetContext.cacheDir, "source-network-cache").deleteRecursively()
        }

        private fun cleanPrivateState() {
            File(targetContext.noBackupFilesDir, "extensions").deleteRecursively()
            cleanSessionState()
            File(targetContext.cacheDir, "hxp-staging").deleteRecursively()
        }
    }
}
