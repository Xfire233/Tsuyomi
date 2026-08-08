/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.ui.theme.TsuyomiTheme

class DisplaySettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standardProfileDoesNotExposeEInkRefreshControls() {
        render(DisplayProfile.STANDARD)

        composeRule.onAllNodesWithText("墨水屏刷新").assertCountEquals(0)
        composeRule.onAllNodesWithText("立即重绘界面").assertCountEquals(0)
    }

    @Test
    fun eInkProfileExposesRefreshControls() {
        render(DisplayProfile.EINK)

        composeRule.onAllNodesWithText("墨水屏刷新").assertCountEquals(1)
        composeRule.onAllNodesWithText("立即重绘界面").assertCountEquals(1)
        composeRule.onNodeWithText("立即重绘界面")
            .assertIsEnabled()
            .assertHasClickAction()
    }

    private fun render(profile: DisplayProfile) {
        val environment = environment(profile)
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    DisplaySettingsScreen(
                        state = DisplaySettingsUiState(environment),
                        actions = noOpActions,
                    )
                }
            }
        }
    }

    private fun environment(profile: DisplayProfile) = DisplayEnvironment(
        preferences = DisplayPreferences(
            displayPreference = if (profile == DisplayProfile.EINK) {
                DisplayPreference.EINK
            } else {
                DisplayPreference.STANDARD
            },
            colorSchemePreference = ColorSchemePreference.LIGHT,
        ),
        effectiveProfile = profile,
        decisionReason = if (profile == DisplayProfile.EINK) {
            DisplayDecisionReason.MANUAL_EINK
        } else {
            DisplayDecisionReason.MANUAL_STANDARD
        },
        detectedDeviceLabel = null,
        dynamicColorEligible = false,
        dynamicColorEffective = false,
        effectiveDarkTheme = false,
        motionPolicy = if (profile == DisplayProfile.EINK) {
            MotionPolicy.INSTANT
        } else {
            MotionPolicy.STANDARD
        },
        redrawEpoch = 0,
    )

    private companion object {
        val noOpActions = DisplaySettingsActions(
            onDisplayPreferenceChange = {},
            onColorSchemePreferenceChange = {},
            onDynamicColorEnabledChange = {},
            onRefreshNow = {},
            onRetryWrite = {},
            onAcknowledgeWriteFailure = {},
        )
    }
}
