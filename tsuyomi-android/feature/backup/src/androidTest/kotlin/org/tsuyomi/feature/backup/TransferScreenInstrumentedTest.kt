/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.backup

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy

class TransferScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dataOwnsCanonicalPreferenceResetAndRequiresConfirmation() {
        var resets = 0
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                MaterialTheme {
                    TransferScreen(
                        state = TransferUiState.Idle,
                        onChooseImport = {},
                        onConfirmImport = {},
                        onCancelImport = {},
                        onExport = {},
                        onDismissResult = {},
                        onRetryRecovery = {},
                        onAbortRecovery = {},
                        onResetInterfacePreferences = { resets++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText("重置界面与阅读偏好").assertIsDisplayed().performClick()
        assertEquals(0, resets)
        composeRule.onNodeWithText(
            "仅清除界面与阅读偏好并恢复当前宪章默认值。书籍、收藏夹、进度、历史、来源包、登录凭据、网站镜像和缓存均不会改动。",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("确认重置").performClick()

        assertEquals(1, resets)
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
