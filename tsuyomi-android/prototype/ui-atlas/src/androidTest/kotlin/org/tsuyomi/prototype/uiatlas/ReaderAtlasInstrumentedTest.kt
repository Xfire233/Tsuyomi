/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures

@RunWith(AndroidJUnit4::class)
class ReaderAtlasInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val stateFile: File
        get() = File(
            InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
            "interactive-prototype-state-v1.json",
        )

    @Before
    fun launchReader() {
        stateFile.delete()
        scenario = ActivityScenario.launch(readerIntent(view = "all", capture = false))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-content-surface").assertExists()
    }

    @After
    fun closeScenario() {
        scenario.close()
    }

    @Test
    fun standard_reader_uses_chapter_progress_overlay_chrome_and_non_scrolling_pages() {
        composeRule.onNodeWithContentDescription("章节目录").assertDoesNotExist()
        composeRule.onNodeWithText("上一章").assertExists()
        composeRule.onNodeWithText("目录").assertExists()
        composeRule.onNodeWithText("设置").assertExists()
        composeRule.onNodeWithText("下一章").assertExists()

        composeRule.onNodeWithText("门楣下刻着两个极浅的字", substring = true).assertExists()
        composeRule.onNodeWithTag("reader-content-surface").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("门楣下刻着两个极浅的字", substring = true).assertExists()
        val expandedHeadingBounds = composeRule.onNodeWithTag("reader-block-prose-title").fetchSemanticsNode().boundsInRoot
        val topChromeBounds = composeRule.onNodeWithTag("reader-top-chrome").fetchSemanticsNode().boundsInRoot
        assertTrue(expandedHeadingBounds.top < topChromeBounds.bottom)

        val expandedBounds = composeRule.onNodeWithTag("reader-content-surface").fetchSemanticsNode().boundsInRoot
        val readingInfoBounds = composeRule.onNodeWithTag("reader-reading-info").fetchSemanticsNode().boundsInRoot
        val pagedLastBlockBounds = composeRule.onNodeWithTag("reader-block-prose-paragraph-7").fetchSemanticsNode().boundsInRoot
        assertTrue(expandedBounds.bottom <= readingInfoBounds.top)
        assertTrue(pagedLastBlockBounds.bottom <= readingInfoBounds.top)
        composeRule.onNodeWithTag("reader-content-surface").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("设置").assertDoesNotExist()
        composeRule.onNodeWithTag("reader-reading-info")
            .assert(hasStateDescription("本章进度 6%，第 1 / 4 页"))
        val hiddenHeadingBounds = composeRule.onNodeWithTag("reader-block-prose-title").fetchSemanticsNode().boundsInRoot
        assertEquals(expandedHeadingBounds, hiddenHeadingBounds)
        val hiddenBounds = composeRule.onNodeWithTag("reader-content-surface").fetchSemanticsNode().boundsInRoot
        assertEquals(expandedBounds, hiddenBounds)

        composeRule.onNodeWithTag("reader-content-surface").performTouchInput { swipeLeft() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(
                hasStateDescription("本章进度 33%，第 2 / 4 页"),
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("reader-content-surface").performClick()
        composeRule.onNodeWithTag("reader-chapter-progress-slider").performTouchInput { click(center) }
        assertTrue(composeRule.onAllNodesWithText(SourceAtlasFixtures.chapters[11].title).fetchSemanticsNodes().isNotEmpty())
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("LocatorCommit")
        }

        composeRule.onNodeWithText("下一章").performClick()
        assertTrue(composeRule.onAllNodesWithText(SourceAtlasFixtures.chapters[12].title).fetchSemanticsNodes().isNotEmpty())
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ReaderChapterNext")
        }
    }

    @Test
    fun continuous_reader_scrolls_final_block_above_reading_status_lane() {
        enableContinuousFlow()
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToKey("prose-paragraph-24")
        composeRule.waitForIdle()

        val contentBounds = composeRule.onNodeWithTag("reader-content-surface").fetchSemanticsNode().boundsInRoot
        val statusBounds = composeRule.onNodeWithTag("reader-reading-info").fetchSemanticsNode().boundsInRoot
        val finalBlockBounds = composeRule.onNodeWithTag("reader-block-prose-paragraph-24").fetchSemanticsNode().boundsInRoot
        assertTrue(contentBounds.bottom <= statusBounds.top)
        assertTrue(finalBlockBounds.bottom <= statusBounds.top)
        composeRule.onNodeWithTag("reader-reading-info")
            .assert(hasStateDescription("本章进度 100%，第 31 / 31 页"))
    }

    @Test
    fun reader_side_taps_page_within_chapter_then_continue_to_next_chapter() {
        val chapterTwelve = SourceAtlasFixtures.chapters[11].title
        val chapterThirteen = SourceAtlasFixtures.chapters[12].title

        repeat(3) {
            tapReaderSide(0.85f)
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("reader-reading-info")
            .assert(hasStateDescription("本章进度 100%，第 4 / 4 页"))
        assertTrue(composeRule.onAllNodesWithText(chapterTwelve).fetchSemanticsNodes().isNotEmpty())

        tapReaderSide(0.85f)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(chapterThirteen).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodes(
                    hasStateDescription("本章进度 0%，第 1 / 4 页"),
                ).fetchSemanticsNodes().isNotEmpty()
        }

        tapReaderSide(0.15f)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-reading-info")
            .assert(hasStateDescription("本章进度 0%，第 1 / 4 页"))
        assertTrue(composeRule.onAllNodesWithText(chapterThirteen).fetchSemanticsNodes().isNotEmpty())
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ReaderPageBoundaryNextChapter")
        }
    }

    @Test
    fun detail_directory_opens_the_exact_selected_chapter() {
        relaunchRoute("BOOK_DETAIL")
        val selected = SourceAtlasFixtures.chapters.first()

        composeRule.onNodeWithText(selected.title).performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader-content-surface").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(selected.title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(SourceAtlasFixtures.chapters[11].title).assertDoesNotExist()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ChapterOpened")
        }
    }

    @Test
    fun standard_reader_auxiliary_sheet_switches_between_directory_bookmarks_and_search() {
        composeRule.onNodeWithText("目录").performClick()
        composeRule.onNodeWithTag("reader-auxiliary-sheet").assertExists()
        assertTrue(composeRule.onAllNodesWithText("目录").fetchSemanticsNodes().size >= 2)
        composeRule.onNodeWithText("书签").assertExists()
        composeRule.onNodeWithText("搜索").assertExists()
        composeRule.onNode(hasStateDescription("当前阅读")).assertExists()

        composeRule.onNodeWithText("书签").performClick()
        composeRule.onNodeWithText("还没有书签").assertExists()
        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("旧信")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        assertTrue(composeRule.onAllNodesWithText("旧信", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun standard_reader_quick_settings_persist_toggles_and_cycle_reading_flow() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithTag("reader-settings-sheet").assertExists()
        composeRule.onNodeWithText("字号").assertExists()
        composeRule.onNodeWithText("行距").assertExists()
        composeRule.onNodeWithText("边距").assertExists()
        composeRule.onNodeWithText("段距").assertExists()
        composeRule.onNodeWithTag("reader-quick-lock-portrait").assertExists().performClick()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ReaderLockPortraitChanged")
        }
        composeRule.onNodeWithTag("reader-quick-reading-info").assertExists().performClick()
        composeRule.onNodeWithTag("reader-quick-immersive").assertExists().performClick()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ReaderImmersiveChanged")
        }
        composeRule.onNodeWithText("左右分页").assertExists()
        composeRule.onNodeWithTag("reader-quick-flow").performClick()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ReaderBookFlowChanged")
        }
        composeRule.onNodeWithText("连续滚动").assertExists()
        composeRule.onNodeWithTag("reader-quick-flow").performClick()
        composeRule.onNodeWithText("左右分页").assertExists()
        composeRule.onNodeWithTag("reader-quick-flow").performClick()
        composeRule.onNodeWithText("连续滚动").assertExists()
    }

    @Test
    fun standard_reader_settings_expand_and_dismiss_without_exposing_unsupported_options() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("作用范围").assertDoesNotExist()
        composeRule.onNodeWithText("全部设置").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader-settings-expanded-content").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("作用范围").assertDoesNotExist()
        composeRule.onNodeWithText("排版").assertExists()
        composeRule.onNodeWithText("页面").assertExists()
        composeRule.onNodeWithText("导航").assertExists()
        composeRule.onNodeWithText("设备").assertExists()
        composeRule.onNodeWithText("点击区域：左侧上一页 · 中间工具栏 · 右侧下一页").assertExists()
        composeRule.onNodeWithText("双页").assertDoesNotExist()
        composeRule.onNodeWithText("双页需要至少 600dp 可用宽度；偏好会保留，但当前窗口不启用。").assertDoesNotExist()
        composeRule.onNodeWithTag("reader-settings-content").performTouchInput { swipeDown() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader-settings-sheet").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("reader-settings-sheet").assertDoesNotExist()
        composeRule.onNodeWithText("设置").assertExists()
    }

    @Test
    fun standard_reader_prose_fixture_reaches_a_complete_ending_in_continuous_flow() {
        enableContinuousFlow()
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToKey("prose-paragraph-24")
        val ending = "“信收到了吗？”来人问。许砚没有立刻回头。他先看了一眼门上的纸灯，确认火苗安稳，才把手按在木盒上回答：“收到了。路也记下了。”"
        composeRule.onNodeWithText(ending).assertExists()
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToKey("prose-section-home")
        composeRule.onNodeWithText("三、灯下归人").assertExists()
    }

    @Test
    fun standard_reader_renders_and_operates_the_complete_mixed_media_document() {
        relaunchReader("continue")
        enableContinuousFlow()
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToKey("mixed-conclusion-title")
        composeRule.onNodeWithText("复原结果").assertExists()
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToKey("tide-table")
        composeRule.onNodeWithText("卯时").assertExists()
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToKey("river-map")
        composeRule.onNodeWithContentDescription(
            "河图残卷。泛黄纸面上绘有三条河道、山脊、七码头石阶和一盏红色纸灯。点按查看大图",
        ).performClick()
        composeRule.onNodeWithContentDescription("关闭大图").assertExists()
        pressBack()

        composeRule.onNodeWithTag("reader-document-scroll").performScrollToKey("attachment")
        composeRule.onNodeWithContentDescription("打开附件 河图残卷题记.txt，UTF-8 · 2.4 KB · SHA-256 已验证 · 已下载")
            .performClick()
        composeRule.onNodeWithText("已打开附件：河图残卷题记.txt").assertExists()
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToKey("mixed-conclusion-1")
        composeRule.onNodeWithContentDescription("打开链接 《南河渡口沿革》")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("已打开链接：tsuyomi://note/south-river").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun standard_reader_renders_reply_chain_and_jumps_to_stable_target_posts() {
        relaunchReader("recent")
        enableContinuousFlow()
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToKey("post-1122")
        composeRule.onNodeWithText("7楼").assertExists()
        composeRule.onNodeWithContentDescription("跳转至 6楼 南河档案室 的回复").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("南河档案室").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("已跳转至 6楼 · 南河档案室").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToKey("post-1122")
        composeRule.onNodeWithText("最终灯号").assertExists()
    }

    @Test
    fun standard_reader_renders_reply_stream_through_the_shared_surface() {
        relaunchReader("recent")
        composeRule.onNodeWithText("灯影从哪里来").assertExists()
        composeRule.onNodeWithTag("reader-content-surface").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("河岸听雨").assertExists()
        composeRule.onNodeWithText("楼主").assertExists()
        composeRule.onNodeWithTag("reader-content-surface").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2楼").assertExists()
    }

    private fun enableContinuousFlow() {
        if (composeRule.onAllNodesWithText("设置").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("reader-content-surface").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithTag("reader-quick-flow").performClick()
        composeRule.onNodeWithText("连续滚动").assertExists()
        pressBack()
        composeRule.waitForIdle()
    }

    private fun tapReaderSide(horizontalFraction: Float) {
        val surface = composeRule.onNodeWithTag("reader-content-surface")
        val bounds = surface.fetchSemanticsNode().boundsInRoot
        surface.performTouchInput {
            click(Offset(bounds.width * horizontalFraction, bounds.height / 2f))
        }
    }

    private fun relaunchRoute(route: String) {
        scenario.close()
        stateFile.delete()
        scenario = ActivityScenario.launch(atlasIntent(route = route, view = "all", capture = false))
        composeRule.waitForIdle()
    }

    private fun relaunchReader(view: String, capture: Boolean = true) {
        scenario.close()
        scenario = ActivityScenario.launch(readerIntent(view = view, capture = capture))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-content-surface").assertExists()
    }

    private fun readerIntent(view: String, capture: Boolean): Intent = atlasIntent(
        route = "BOOK_READER",
        view = view,
        capture = capture,
    )

    private fun atlasIntent(route: String, view: String, capture: Boolean): Intent {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        return Intent(targetContext, MainActivity::class.java)
            .putExtra("route", route)
            .putExtra("profile", "STANDARD")
            .putExtra("capture", capture.toString())
            .putExtra("view", view)
    }
}
