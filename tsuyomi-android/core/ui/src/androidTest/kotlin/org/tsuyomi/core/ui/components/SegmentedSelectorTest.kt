/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Column
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
}
