/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.browse

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.ui.theme.TsuyomiTheme

@PreviewTest
@Preview(name = "standard", device = "spec:width=360dp,height=800dp,dpi=420", locale = "zh-rCN")
@Composable
fun BrowseEmptyStandardScreenshot() {
    BrowsePreview(DisplayProfile.STANDARD)
}

@PreviewTest
@Preview(name = "eink", device = "spec:width=843dp,height=1120dp,dpi=240", locale = "zh-rCN")
@Composable
fun BrowseEmptyEInkScreenshot() {
    BrowsePreview(DisplayProfile.EINK)
}

@Preview(name = "approval-resource-limit-increase", device = "spec:width=360dp,height=800dp,dpi=420", locale = "zh-rCN")
@Composable
fun BrowseApprovalResourceLimitPreview() {
    BrowsePreview(
        profile = DisplayProfile.STANDARD,
        state = BrowseUiState.Approval(
            sourceName = "Wenku8",
            sourceId = "org.tsuyomi.wenku8",
            version = "0.2.0",
            publisherFingerprint = "a1b2c3d4",
            capabilities = emptyList(),
            resourceLimitIncreases = listOf(
                BrowseResourceLimitIncrease(
                    limit = BrowseResourceLimit.MAX_EXECUTION_WALL_TIME_MS,
                    activeValue = 15_000,
                    candidateValue = 30_000,
                ),
                BrowseResourceLimitIncrease(
                    limit = BrowseResourceLimit.MAX_MEMORY_BYTES,
                    activeValue = 16_777_216,
                    candidateValue = 33_554_432,
                ),
                BrowseResourceLimitIncrease(
                    limit = BrowseResourceLimit.STORAGE_QUOTA_BYTES,
                    activeValue = 1_048_576,
                    candidateValue = 2_097_152,
                ),
                BrowseResourceLimitIncrease(
                    limit = BrowseResourceLimit.NETWORK_CONCURRENT_REQUESTS,
                    activeValue = 2,
                    candidateValue = 4,
                ),
                BrowseResourceLimitIncrease(
                    limit = BrowseResourceLimit.NETWORK_REQUEST_TIMEOUT_MS,
                    activeValue = 15_000,
                    candidateValue = 30_000,
                ),
                BrowseResourceLimitIncrease(
                    limit = BrowseResourceLimit.NETWORK_RESPONSE_BYTES,
                    activeValue = 1_048_576,
                    candidateValue = 2_097_152,
                ),
            ),
            isDowngrade = false,
        ),
    )
}

@Composable
private fun BrowsePreview(profile: DisplayProfile, state: BrowseUiState = BrowseUiState.Empty) {
    val eInk = profile == DisplayProfile.EINK
    val environment = DisplayEnvironment(
        preferences = DisplayPreferences(
            displayPreference = if (eInk) DisplayPreference.EINK else DisplayPreference.STANDARD,
            colorSchemePreference = ColorSchemePreference.LIGHT,
        ),
        effectiveProfile = profile,
        decisionReason = if (eInk) {
            DisplayDecisionReason.MANUAL_EINK
        } else {
            DisplayDecisionReason.MANUAL_STANDARD
        },
        detectedDeviceLabel = null,
        dynamicColorEligible = false,
        dynamicColorEffective = false,
        effectiveDarkTheme = false,
        motionPolicy = MotionPolicy.INSTANT,
        redrawEpoch = 0,
    )
    DisplayEnvironmentProvider(environment) {
        TsuyomiTheme(environment) {
            Surface(Modifier.fillMaxSize()) {
                BrowseScreen(
                    state = state,
                    onRequestImport = {},
                    onOpenInstalledSource = {},
                    onApproveInstall = {},
                    onDismissApproval = {},
                    onDismissFailure = {},
                )
            }
        }
    }
}
