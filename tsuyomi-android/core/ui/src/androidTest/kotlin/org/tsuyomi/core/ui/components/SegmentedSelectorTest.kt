/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import org.tsuyomi.core.ui.theme.TsuyomiTheme

@RunWith(AndroidJUnit4::class)
class SegmentedSelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selector_does_not_consume_the_weighted_content_viewport() {
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
                    Column(Modifier.fillMaxSize()) {
                        SegmentedSelector(
                            options = listOf(TsuyomiSegment(0, "未处理"), TsuyomiSegment(1, "全部\n更新")),
                            selected = 0,
                            onSelect = {},
                        )
                        LazyColumn(Modifier.weight(1f)) {
                            item { Text("可见的书籍更新") }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText("可见的书籍更新").assertIsDisplayed()
        assertEquals(
            composeRule.onNodeWithText("全部\n更新").fetchSemanticsNode().boundsInRoot.height,
            composeRule.onNodeWithText("未处理").fetchSemanticsNode().boundsInRoot.height,
            0f,
        )
    }

    @Test
    fun long_segments_remain_reachable_and_show_an_externally_selected_option() {
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
                    var selected by remember { mutableIntStateOf(0) }
                    var shortLabels by remember { mutableStateOf(false) }
                    Column {
                        Button(onClick = { selected = 0 }) { Text("切回首项") }
                        Button(onClick = { shortLabels = true }) { Text("标准频率") }
                        SegmentedSelector(
                            options = if (shortLabels) listOf(
                                TsuyomiSegment(0, "关闭"),
                                TsuyomiSegment(1, "每 12 小时"),
                                TsuyomiSegment(2, "每天"),
                                TsuyomiSegment(3, "每 3 天"),
                                TsuyomiSegment(4, "每周"),
                            ) else listOf(
                                TsuyomiSegment(0, "关闭"),
                                TsuyomiSegment(1, "每十二小时检查更新"),
                                TsuyomiSegment(2, "每天检查更新"),
                                TsuyomiSegment(3, "每三天检查更新"),
                                TsuyomiSegment(4, "每周检查更新"),
                            ),
                            selected = selected,
                            onSelect = { selected = it },
                            modifier = Modifier.width(if (shortLabels) 348.dp else 300.dp),
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("每周检查更新").performScrollTo().performClick()
        composeRule.onNodeWithText("每周检查更新").assertIsSelected().assertIsDisplayed()
        composeRule.onNodeWithText("切回首项").performClick()
        composeRule.onNodeWithText("关闭").assertIsSelected().assertIsDisplayed()
        composeRule.onNodeWithText("标准频率").performClick()
        composeRule.onNodeWithText("每周").assertIsDisplayed()
    }

    @Test
    fun eInkTabsStayIntrinsicReachEveryOptionAndRestoreSelectionAtDoubleFont() {
        val environment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.STANDARD),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.STANDARD,
            redrawEpoch = 0L,
        )
        val restoration = StateRestorationTester(composeRule)
        val options = List(12) { TsuyomiTabOption("option-$it", "项目$it") }
        var selected by mutableStateOf(options.first().key)
        var density = 0f
        restoration.setContent {
            DisplayEnvironmentProvider(environment) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    TsuyomiTheme(environment) {
                        density = LocalDensity.current.density
                        Box(Modifier.size(280.dp, 240.dp).testTag("tab-host")) {
                            TsuyomiTabRow(options, selected, { selected = it })
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText("上一页").assertIsNotEnabled()
        val observed = linkedSetOf<String>()
        var transitions = 0
        while (true) {
            val host = composeRule.onNodeWithTag("tab-host").fetchSemanticsNode().boundsInRoot
            val controlsTop = composeRule.onNodeWithText("第", substring = true).fetchSemanticsNode().boundsInRoot.top
            val tabs = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNodes()
                .filter { tab ->
                    runCatching {
                        composeRule.onNodeWithText(tab.config[SemanticsProperties.Text].last().text).assertIsDisplayed()
                    }.isSuccess
                }
            assertTrue(tabs.isNotEmpty())
            for (tab in tabs) {
                val bounds = tab.boundsInRoot
                assertTrue(bounds.width >= 48.dp.value * density && bounds.width < host.width / 2)
                assertTrue(bounds.height >= 48.dp.value * density)
                assertTrue(bounds.left >= host.left && bounds.right <= host.right && bounds.bottom <= controlsTop)
                observed += tab.config[SemanticsProperties.Text].map { it.text }
            }
            val lastLabel = tabs.last().config[SemanticsProperties.Text].last().text
            composeRule.onNodeWithText(lastLabel).performClick().assertIsSelected().assertIsDisplayed()
            val next = composeRule.onNodeWithText("下一页")
            if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
            next.performClick()
            assertTrue("Tabs did not reach their measured endpoint", ++transitions <= 12)
        }
        assertEquals(options.map { it.label }.toSet(), observed)
        composeRule.onNodeWithText(options.last().label).assertIsSelected().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText(options.last().label).assertIsSelected().assertIsDisplayed()
        composeRule.onNodeWithText("下一页").assertIsNotEnabled()
        composeRule.onNodeWithText("上一页").performClick()
        composeRule.onNodeWithText("下一页").performClick()
        composeRule.onNodeWithText(options.last().label).assertIsSelected().assertIsDisplayed()
    }
}
