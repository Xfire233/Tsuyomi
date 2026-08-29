/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas

import android.content.Intent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.prototype.uiatlas.review.ReviewJsonExporter
import org.tsuyomi.prototype.uiatlas.review.ReviewRepository

@RunWith(AndroidJUnit4::class)
class AtlasCloseoutInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val stateFile: File
        get() = File(
            InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
            "interactive-prototype-state-v1.json",
        )

    @Before
    fun resetPrototypeState() {
        stateFile.delete()
    }

    @After
    fun closeScenario() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun updates_check_exposes_working_and_result_feedback() {
        launch("LIBRARY_UPDATES")

        composeRule.onNodeWithContentDescription("检查全部更新").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("正在检查更新…").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("检查完成", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun mirror_calibration_exposes_working_and_result_feedback() {
        launch("LIBRARY_MIRROR")

        composeRule.onNodeWithContentDescription("校准镜像").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("正在校准网站镜像…").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("校准完成", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun book_detail_cache_exposes_working_and_result_feedback() {
        launch("BOOK_DETAIL")

        composeRule.onNodeWithContentDescription("缓存本书").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("缓存本书…").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("缓存本书 已完成。").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun reader_error_retry_returns_to_the_reading_surface() {
        launch("BOOK_READER", state = "ERROR")

        composeRule.onNodeWithText("重试").performClick()
        composeRule.onNodeWithText("正在重试章节…").assertExists()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("第12章 · 旧信").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun smart_rule_editor_confirms_unsaved_back_navigation() {
        launch("LIBRARY_COLLECTION_RULE")

        composeRule.onAllNodesWithText("添加条件")[0].performScrollTo().performClick()
        pressBack()
        composeRule.onNodeWithText("放弃未保存的修改？").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("放弃未保存的修改？").assertDoesNotExist()
    }

    @Test
    fun more_root_opens_every_destination_and_returns() {
        launch("MORE")

        listOf("显示", "阅读", "数据", "帮助", "关于").forEach { destination ->
            composeRule.onNodeWithText(destination).performClick()
            composeRule.onNodeWithText(destination).assertExists()
            pressBack()
            composeRule.onNodeWithText("界面与阅读").assertExists()
        }
    }

    @Test
    fun display_theme_persists_and_reset_reports_exact_scope() {
        launch("MORE_DISPLAY", persistent = true)

        composeRule.onNodeWithText("深色").performClick()
        composeRule.onNodeWithText("已保存主题：深色。").assertExists()
        launch("MORE_DISPLAY", persistent = true)
        composeRule.onNodeWithText("已保存并生效：深色").assertExists()

        composeRule.onNodeWithText("重置界面设置").performScrollTo().performClick()
        val resetActions = composeRule.onAllNodesWithText("重置界面设置")
        resetActions[resetActions.fetchSemanticsNodes().lastIndex].performClick()
        composeRule.onNodeWithText("界面设置已恢复为宪章默认值", substring = true).assertExists()
    }

    @Test
    fun reader_defaults_persist_selected_layout() {
        launch("MORE_READER", persistent = true)

        composeRule.onNodeWithText("分页").performClick()
        composeRule.onNodeWithText("首选阅读布局已设为：分页。").assertExists()

        launch("MORE_READER", persistent = true)
        composeRule.onNodeWithText("当前有效：分页").assertExists()
    }

    @Test
    fun data_import_cancel_and_export_preview_are_zero_effect_paths() {
        launch("MORE_DATA")

        composeRule.onNodeWithText("导入 Tsuyomi 数据").performClick()
        composeRule.onNodeWithText("固定 Tsuyomi 数据包样例", substring = true).assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("固定 Tsuyomi 数据包样例", substring = true).assertDoesNotExist()

        composeRule.onNodeWithText("导出").performClick()
        composeRule.onNodeWithText("导出预览").assertExists()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithText("未创建文件", substring = true).assertExists()
    }

    @Test
    fun import_report_expands_warnings_and_blocks_back_until_recovery() {
        launch("MORE_DATA_REPORT")

        composeRule.onNodeWithText("展开全部 87 条").performScrollTo().performClick()
        composeRule.onNodeWithText("收起到前 50 条").assertExists()
        pressBack()
        composeRule.onNodeWithText("解决恢复门").assertExists()
        composeRule.onNodeWithText("保留当前结果").performClick()
        composeRule.onNodeWithText("已保留当前结果；恢复门已解除。").assertExists()
    }

    @Test
    fun help_searches_expands_answers_and_replays_feature_introduction() {
        launch("MORE_HELP")

        composeRule.onNode(hasSetTextAction()).performTextInput("进度")
        composeRule.onNodeWithText("阅读进度如何保存？").performClick()
        composeRule.onNodeWithText("Tsuyomi 保存章节与语义定位器", substring = true).assertExists()

        launch("MORE_HELP")
        composeRule.onNodeWithText("功能说明：追更").performScrollTo().performClick()
        composeRule.onNodeWithText("关闭或标记已读只控制帮助内容", substring = true).assertExists()
        composeRule.onNodeWithText("知道了").performClick()
        composeRule.onNodeWithText("关闭或标记已读只控制帮助内容", substring = true).assertDoesNotExist()
    }

    @Test
    fun about_opens_and_closes_the_offline_license_notice() {
        launch("MORE_ABOUT")

        composeRule.onNodeWithText("查看完整许可说明").performScrollTo().performClick()
        composeRule.onNodeWithText("Licensed under the Apache License", substring = true).assertExists()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithText("Licensed under the Apache License", substring = true).assertDoesNotExist()
    }

    @Test
    fun review_export_identifies_current_and_deferred_evidence_stages() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = ReviewRepository(targetContext, persistent = false)
        val root = Json.parseToJsonElement(
            ReviewJsonExporter.export(targetContext, repository.snapshot.value, includeStaleBuilds = false),
        ).jsonObject
        val build = root.getValue("build").jsonObject
        val catalog = root.getValue("reviewCatalog").jsonArray

        assertEquals(5, build.getValue("reviewCatalogVersion").jsonPrimitive.int)
        assertEquals(28, catalog.size)
        assertEquals(
            "atlas_ui",
            catalog.single { it.jsonObject.getValue("id").jsonPrimitive.content == "L01" }
                .jsonObject.getValue("evidenceStage").jsonPrimitive.content,
        )
        assertEquals(
            "actual_online_scenario",
            catalog.single { it.jsonObject.getValue("id").jsonPrimitive.content == "S01" }
                .jsonObject.getValue("evidenceStage").jsonPrimitive.content,
        )
    }

    private fun launch(route: String, persistent: Boolean = false, state: String = "CONTENT") {
        if (::scenario.isInitialized) scenario.close()
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(targetContext, MainActivity::class.java)
            .putExtra("route", route)
            .putExtra("profile", "STANDARD")
            .putExtra("state", state)
            .putExtra("capture", (!persistent).toString())
        scenario = ActivityScenario.launch(intent)
        composeRule.waitForIdle()
    }
}
