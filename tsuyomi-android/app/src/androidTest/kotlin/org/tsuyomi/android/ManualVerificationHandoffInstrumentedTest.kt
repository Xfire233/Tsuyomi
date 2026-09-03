/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.net.Uri
import android.webkit.CookieManager
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
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
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("搜索此来源")
        composeRule.onNodeWithText("搜索此来源").performClick()
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("login")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        waitForText("此来源需要用户手动登录。")
        assertEquals(1, Phase2SourceGateway.searchRequestCount())

        composeRule.onNodeWithText("手动登录或验证").performClick()
        waitForText("打开对应搜索页面")
        waitForText("使用当前页面")
        val searchHtml = targetContext.assets.open("search.html").bufferedReader().use { it.readText() }
        val searchUrl =
            "https://www.wenku8.net/modules/article/search.php?searchtype=articlename&searchkey=login&page=1"
        loadAndAwaitVerifiedPage(searchUrl, searchHtml)
        composeRule.onNodeWithText("使用当前页面").performClick()
        waitForText("雾港纪事")
        assertEquals(1, Phase2SourceGateway.searchRequestCount())
        composeRule.onNodeWithText("雾港纪事").performClick()
        waitForText("简介")
        composeRule.onNodeWithTag("book-detail-scroll").performScrollToIndex(3)
        waitForText("第一章 雾中的灯塔")
        composeRule.onNodeWithText("第一章 雾中的灯塔").performClick()
        waitForText("设置")
        waitForText("第一章 雾中的灯塔")
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
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("搜索此来源")
        composeRule.onNodeWithText("搜索此来源").performClick()
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        waitForText("雾港纪事")
        Phase2SourceGateway.requireVerificationForNextDetailRequest()
        composeRule.onNodeWithText("雾港纪事").performClick()
        waitForText("打开手动登录或验证")
        assertEquals(1, Phase2SourceGateway.detailRequestCount())
        assertEquals(1, Phase2SourceGateway.directoryRequestCount())

        composeRule.onNodeWithText("打开手动登录或验证").performClick()
        waitForText("打开对应详情页面")
        waitForText("使用当前页面")
        val detailHtml = targetContext.assets.open("detail.html").bufferedReader().use { it.readText() }
        val detailUrl = "https://www.wenku8.net/book/1234.htm"
        loadAndAwaitVerifiedPage(detailUrl, detailHtml)
        composeRule.onNodeWithText("使用当前页面").performClick()
        waitForText("简介")
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
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("搜索此来源")
        composeRule.onNodeWithText("搜索此来源").performClick()
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        waitForText("雾港纪事")
        composeRule.onNodeWithText("雾港纪事").performClick()
        waitForText("简介")
        composeRule.onNodeWithTag("book-detail-scroll").performScrollToIndex(3)
        waitForText("第一章 雾中的灯塔")
        Phase2SourceGateway.requireVerificationForNextChapterRequest()
        composeRule.onNodeWithText("第一章 雾中的灯塔").performClick()
        waitForText("打开手动登录或验证")
        assertEquals(1, Phase2SourceGateway.chapterRequestCount())
        assertEquals(1, Phase2SourceGateway.directoryRequestCount())

        composeRule.onNodeWithText("打开手动登录或验证").performClick()
        waitForText("打开对应章节页面")
        waitForText("使用当前页面")
        val chapterHtml = targetContext.assets.open("chapter.html").bufferedReader().use { it.readText() }
        val chapterUrl = "https://www.wenku8.net/modules/article/reader.php?aid=1234&cid=10001"
        loadAndAwaitVerifiedPage(chapterUrl, chapterHtml)
        composeRule.onNodeWithText("使用当前页面").performClick()
        waitForText("第一章 雾中的灯塔")
        assertEquals(1, Phase2SourceGateway.chapterRequestCount())

        composeRule.onNodeWithText("下一章").assertIsEnabled().performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) { Phase2SourceGateway.chapterRequestCount() == 2 }
        waitForText("第二章 旧船票")
    }


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
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("搜索此来源")
        composeRule.onNodeWithText("搜索此来源").performClick()
        waitForText("输入关键词后搜索")
        waitForQuickJsLaneCount(1)

        composeRule.activityRule.scenario.recreate()

        waitForText("输入关键词后搜索")
        waitForQuickJsLaneCount(1)
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        composeRule.onNode(hasSetTextAction()).performImeAction()
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
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("搜索此来源")
        composeRule.onNodeWithText("搜索此来源").performClick()
        waitForText("输入关键词后搜索")
        waitForQuickJsLaneCount(1)

        pressBack()
        waitForText("搜索此来源")
        waitForQuickJsLaneCount(1)
        pressBack()
        waitForText("书架")
        waitForQuickJsLaneCount(0)
    }

    @Test
    fun standard_detail_uses_integrated_atlas_modules_and_local_only_metadata() {
        cleanSessionState()
        runBlocking {
            val application = composeRule.activity.application as TsuyomiApplication
            application.libraryRepository.libraryEntries()
                .filter { it.book.title == "雾港纪事" }
                .forEach { application.libraryRepository.removeFromLibrary(it.book.identity) }
            application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
        }
        waitForText("书架")
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("搜索此来源")
        composeRule.onNodeWithText("搜索此来源").performClick()
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        waitForText("雾港纪事")
        composeRule.onNodeWithText("雾港纪事").performClick()

        waitForText("简介")
        waitForText("稍后再读")
        composeRule.onNodeWithText("稍后再读").performClick()
        waitForText("已在书架")
        waitForStateDescription("detail-read-later-action", "已稍后再读")
        waitForText("已完成：更新稍后再读")
        composeRule.onNodeWithText("稍后再读").performClick()
        waitForStateDescription("detail-read-later-action", "未稍后再读")
        composeRule.onNodeWithTag("book-detail-scroll").performScrollToIndex(3)
        waitForText("全文目录")
        waitForText("第一章 雾中的灯塔")
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
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("搜索此来源")
        composeRule.onNodeWithText("搜索此来源").performClick()
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        waitForText("雾港纪事")
        composeRule.onNodeWithText("雾港纪事").performClick()
        waitForText("简介")
        composeRule.onNodeWithText("稍后再读").performClick()
        waitForText("已在书架")

        composeRule.onNodeWithText("书架").performClick()
        waitForText("快捷书架")
        waitForText("雾港纪事")
        composeRule.onNodeWithText("雾港纪事").performClick()
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
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("搜索此来源")
        composeRule.onNodeWithText("搜索此来源").performClick()
        waitForText("输入关键词后搜索")
        composeRule.onNode(hasSetTextAction()).performTextInput("fixture")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        waitForText("雾港纪事")
        composeRule.onNodeWithText("雾港纪事").performClick()
        waitForText("简介")
        composeRule.onNodeWithTag("book-detail-scroll").performScrollToIndex(3)
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
        composeRule.onNodeWithTag("reader-settings-content").performTouchInput { swipeDown() }
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
        waitForText("清晨的海雾漫过石阶，灯塔只剩一圈微光。")
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
        pressBack()
        waitForText("输入关键词后搜索")
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
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("Wenku8")
        composeRule.onNodeWithText("Wenku8").performClick()
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
            VerifiedBrowserSessionStore(composeRule.activity).put(
                SourceCredentialPartition(WENKU8_SOURCE_ID, WENKU8_ORIGIN),
                VerifiedBrowserSession("fixture_session=accepted", "fixture-webview-agent/1"),
            )
        }

        waitForText("书架")
        composeRule.onNodeWithText("浏览").performClick()
        waitForText("网站收藏")
        composeRule.onNodeWithText("网站收藏").performClick()

        waitForText("尚未读取网站收藏")
        assertEquals(0, Phase2SourceGateway.remoteLibraryReadCount())
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())
        runBlocking {
            val application = composeRule.activity.application as TsuyomiApplication
            assertTrue(application.libraryRepository.libraryEntries().none { it.book.identity.sourceId == WENKU8_SOURCE_ID })
        }

        composeRule.onNodeWithText("刷新列表").performClick()
        waitForText("雾港纪事")
        waitForText("星环邮差")
        assertEquals(2, Phase2SourceGateway.remoteLibraryReadCount())
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())

        composeRule.onAllNodes(isToggleable())[0].performClick()
        waitForText("已选择 1 项")
        composeRule.onNodeWithContentDescription("复制所选").performClick()
        waitForText("复制网站收藏到本地书架")
        composeRule.onNodeWithText("确认复制到本地书架").performClick()
        waitForText("已复制 1 本，新增 1 本到本地书架；未向网站写入。")
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())

        composeRule.onNodeWithContentDescription("全部复制").performClick()
        waitForText("复制网站收藏到本地书架")
        composeRule.onNodeWithText("确认复制到本地书架").performClick()
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
        waitForText("尚未读取网站收藏")
        assertEquals(2, Phase2SourceGateway.remoteLibraryReadCount())
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())
    }


    private fun exerciseVerificationHandoff(profile: DisplayPreference) {
        cleanSessionState()
        runBlocking {
            (composeRule.activity.application as TsuyomiApplication).displayController
                .setDisplayPreference(profile)
        }
        waitForText("书架")
        composeRule.onNodeWithText("浏览").performClick()
        val sourceEntryLabel = if (profile == DisplayPreference.EINK) "进入内容源" else "搜索此来源"
        waitForText(sourceEntryLabel)
        composeRule.onNodeWithText(sourceEntryLabel).performClick()
        val queryLabel = if (profile == DisplayPreference.EINK) "搜索书名" else "搜索"
        waitForText(queryLabel)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("challenge")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        waitForText("此来源要求用户手动完成安全验证。")
        composeRule.onNodeWithText("手动登录或验证").performClick()
        val completionLabel = if (profile == DisplayPreference.EINK) "已完成" else "我已完成验证"
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
        waitForText(queryLabel)

        val storedSession = requireNotNull(
            VerifiedBrowserSessionStore(targetContext).getSnapshot(
                SourceCredentialPartition(WENKU8_SOURCE_ID, WENKU8_ORIGIN),
            ),
        ).session
        assertTrue(storedSession.requestCookies.contains("fixture_session=accepted"))
        assertTrue(storedSession.userAgent.isNotBlank())
        composeRule.onNode(hasSetTextAction()).performImeAction()
        waitForText("雾港纪事")

        if (profile != DisplayPreference.STANDARD) return

        pressBack()
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithText("登录验证").fetchSemanticsNodes().isEmpty()) {
            pressBack()
        }
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

        val cancelLabel = if (profile == DisplayPreference.EINK) "取消" else "取消验证"
        composeRule.onNodeWithText(cancelLabel).performClick()
        waitForText(sourceEntryLabel)
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

    private fun loadAndAwaitVerifiedPage(url: String, html: String) {
        composeRule.runOnUiThread {
            requireNotNull(findWebView(composeRule.activity.window.decorView)).loadDataWithBaseURL(
                url,
                html,
                "text/html",
                "utf-8",
                url,
            )
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.runOnUiThread {
                val webView = findWebView(composeRule.activity.window.decorView)
                webView != null && webView.url == url && webView.progress == 100
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTextGone(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForStateDescription(tag: String, stateDescription: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).assert(hasStateDescription(stateDescription))
            }.isSuccess
        }
    }

    private fun waitForQuickJsLaneCount(expected: Int) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            Thread.getAllStackTraces().keys.count { thread ->
                thread.isAlive && thread.name.startsWith("tsuyomi-quickjs-")
            } == expected
        }
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
