/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy

class BrowseScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun installedSourceCardOmitsPermanentLoginAction() {
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                MaterialTheme {
                    BrowseScreen(
                        state = BrowseUiState.Installed("Wenku8", "0.2.26"),
                        installedSource = BrowseInstalledSource(
                            sourceId = "org.tsuyomi.source.wenku8",
                            name = "Wenku8",
                            version = "0.2.26",
                            summary = "轻小说来源",
                            homeAvailable = true,
                            remoteLibraryAvailable = true,
                            verificationAvailable = true,
                        ),
                        onRequestImport = {},
                        onOpenInstalledSource = {},
                        onApproveInstall = {},
                        onDismissApproval = {},
                        onDismissFailure = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("搜索此来源").assertIsDisplayed()
        composeRule.onNodeWithText("网站收藏").assertIsDisplayed()
        composeRule.onAllNodesWithText("登录验证", substring = false).assertCountEquals(0)
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
