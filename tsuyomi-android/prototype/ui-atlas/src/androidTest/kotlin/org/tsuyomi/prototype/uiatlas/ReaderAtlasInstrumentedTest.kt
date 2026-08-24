/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas

import android.content.Intent
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

        val firstParagraph = SourceAtlasFixtures.readerPageText(SourceAtlasFixtures.READER_DEFAULT_PAGE)
            .substringBefore("\n\n")
        composeRule.onNodeWithText(firstParagraph).assertExists()
        composeRule.onNodeWithTag("reader-content-surface").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(firstParagraph).assertExists()

        val expandedBounds = composeRule.onNodeWithTag("reader-content-surface").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("reader-content-surface").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("设置").assertDoesNotExist()
        composeRule.onNodeWithTag("reader-reading-info")
            .assert(hasStateDescription("本章进度 6%，第 1 / 7 页"))
        val hiddenBounds = composeRule.onNodeWithTag("reader-content-surface").fetchSemanticsNode().boundsInRoot
        assertEquals(expandedBounds, hiddenBounds)

        composeRule.onNodeWithTag("reader-content-surface").performClick()
        composeRule.onNodeWithTag("reader-chapter-progress-slider").performTouchInput { click(center) }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("灯火不会替人指路，但会让归来的人知道还有一扇门没有关。")
                .fetchSemanticsNodes().isNotEmpty()
        }
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
        composeRule.onNodeWithTag("reader-settings-content").performTouchInput { swipeUp() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader-settings-expanded-content").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("作用范围").assertDoesNotExist()
        composeRule.onNodeWithText("排版").assertExists()
        composeRule.onNodeWithText("页面").assertExists()
        composeRule.onNodeWithText("导航").assertExists()
        composeRule.onNodeWithText("设备").assertExists()
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
    fun standard_reader_renders_mixed_media_through_the_shared_surface() {
        relaunchReader("continue")
        assertTrue(composeRule.onAllNodesWithText("河图残卷").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("reader-content-surface").performTouchInput { swipeLeft() }
        composeRule.onNodeWithContentDescription(
            "河图残卷。泛黄纸面上绘有三条河道、山脊和一盏红色纸灯。点按查看大图",
        ).performClick()
        composeRule.onNodeWithContentDescription("关闭大图").assertExists()
        pressBack()
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

    private fun relaunchReader(view: String) {
        scenario.close()
        scenario = ActivityScenario.launch(readerIntent(view = view, capture = true))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-content-surface").assertExists()
    }

    private fun readerIntent(view: String, capture: Boolean): Intent {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        return Intent(targetContext, MainActivity::class.java)
            .putExtra("route", "BOOK_READER")
            .putExtra("profile", "STANDARD")
            .putExtra("capture", capture.toString())
            .putExtra("view", view)
    }
}
