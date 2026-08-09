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

@Composable
private fun BrowsePreview(profile: DisplayProfile) {
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
                    state = BrowseUiState.Empty,
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
