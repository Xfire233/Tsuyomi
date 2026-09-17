/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

import org.tsuyomi.core.preferences.ColorSchemePreference
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.shared.model.CoverCardPresentation

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

    @Test
    fun displayDoesNotExposeCanonicalReset() {
        render(DisplayProfile.STANDARD)

        composeRule.onAllNodesWithText("重置界面与阅读偏好").assertCountEquals(0)
    }

    @Test
    fun standardProfileWritesTheSelectedCoverPresentation() {
        var selected: CoverCardPresentation? = null
        render(
            profile = DisplayProfile.STANDARD,
            actions = DisplaySettingsActions(
                onDisplayPreferenceChange = {},
                onColorSchemePreferenceChange = {},
                onDynamicColorEnabledChange = {},
                onCoverCardPresentationChange = { selected = it },
                onRefreshNow = {},
                onRetryWrite = {},
                onAcknowledgeWriteFailure = {},
            ),
        )

        composeRule.onNodeWithText("宽屏 16:9").assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals(CoverCardPresentation.WIDE, selected) }

    }

    @Test
    fun eInkProfileKeepsTheCoverPresentationSelectorInactive() {
        render(profile = DisplayProfile.EINK)

        composeRule.onAllNodesWithText("宽屏 16:9").assertCountEquals(0)
    }

    @Test
    fun unsupportedCoverPresentationDisablesBothChoices() {
        render(
            profile = DisplayProfile.STANDARD,
            preferences = DisplayPreferences(coverCardPresentationReadOnly = true),
        )

        composeRule.onNodeWithText("宽屏 16:9").assertIsNotEnabled()
        composeRule.onNodeWithText("标准 5:7").assertIsNotEnabled()
    }

    private fun render(
        profile: DisplayProfile,
        actions: DisplaySettingsActions = noOpActions,
        preferences: DisplayPreferences? = null,
    ) {
        val baseEnvironment = environment(profile)
        val environment = preferences?.let { baseEnvironment.copy(preferences = it) } ?: baseEnvironment
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    DisplaySettingsScreen(
                        state = DisplaySettingsUiState(environment),
                        actions = actions,
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
            onCoverCardPresentationChange = {},
            onRefreshNow = {},
            onRetryWrite = {},
            onAcknowledgeWriteFailure = {},
        )
    }
}
