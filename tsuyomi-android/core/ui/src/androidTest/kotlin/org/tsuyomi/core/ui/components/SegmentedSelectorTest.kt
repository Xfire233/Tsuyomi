/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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
}
