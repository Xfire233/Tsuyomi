/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy

class HelpScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun replay_marks_only_the_versioned_explanation_after_acknowledgement() {
        var seen: Pair<String, Int>? = null
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                MaterialTheme {
                    HelpScreen(
                        introductionsEnabled = true,
                        seenVersions = emptySet(),
                        onIntroductionsEnabledChanged = {},
                        onIntroductionSeen = { id, version -> seen = id to version },
                        onResetSeenVersions = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("网站镜像").performScrollTo().performClick()
        assertNull(seen)
        composeRule.onNodeWithText("知道了").performClick()

        assertEquals("website-mirror" to 1, seen)
    }

    @Test
    fun helpDoesNotExposeInterfacePreferenceReset() {
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                MaterialTheme {
                    HelpScreen(
                        introductionsEnabled = true,
                        seenVersions = emptySet(),
                        onIntroductionsEnabledChanged = {},
                        onIntroductionSeen = { _, _ -> },
                        onResetSeenVersions = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("重置界面与阅读偏好").assertCountEquals(0)
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
