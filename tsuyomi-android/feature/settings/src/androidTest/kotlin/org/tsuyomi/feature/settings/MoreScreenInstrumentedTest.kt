/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy

class MoreScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun moreUsesOneFlatFiveRowHierarchyInCanonicalOrder() {
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                MaterialTheme {
                    MoreScreen(
                        onOpenDisplaySettings = {},
                        onOpenReaderSettings = {},
                        onOpenDataTransfer = {},
                        onOpenHelp = {},
                        onOpenAbout = {},
                    )
                }
            }
        }

        val labels = listOf("显示", "阅读", "数据", "帮助", "关于")
        val topEdges = labels.map { composeRule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(topEdges.zipWithNext().all { (first, second) -> first < second })
        composeRule.onAllNodesWithText("设置").assertCountEquals(0)
        composeRule.onAllNodesWithText("支持").assertCountEquals(0)
    }
    private companion object {
        val standardEnvironment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.STANDARD),
            effectiveProfile = DisplayProfile.STANDARD,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.STANDARD,
            redrawEpoch = 0,
        )
    }
}
