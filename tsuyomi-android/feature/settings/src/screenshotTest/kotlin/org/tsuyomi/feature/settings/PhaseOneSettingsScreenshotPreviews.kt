/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.tsuyomi.core.ui.theme.TsuyomiDarkColorScheme
import org.tsuyomi.core.ui.theme.TsuyomiLightColorScheme
import org.tsuyomi.core.ui.theme.TsuyomiTheme

private const val PhonePortrait = "spec:width=360dp,height=800dp,dpi=420"
private const val PhoneLandscape = "spec:width=800dp,height=360dp,dpi=420"
private const val EInkPortrait = "spec:width=843dp,height=1120dp,dpi=240"
private const val EInkLandscape = "spec:width=1120dp,height=843dp,dpi=240"

@PreviewTest
@Preview(name = "standard-portrait", device = PhonePortrait, locale = "zh-rCN")
@Preview(name = "standard-landscape", device = PhoneLandscape, locale = "zh-rCN")
@Preview(
    name = "standard-font-200",
    device = PhonePortrait,
    locale = "zh-rCN",
    fontScale = 2f,
)
@Composable
fun StandardDisplaySettingsScreenshots() {
    SettingsPreview(environment = standardEnvironment())
}

@PreviewTest
@Preview(name = "eink-portrait", device = EInkPortrait, locale = "zh-rCN")
@Preview(name = "eink-landscape", device = EInkLandscape, locale = "zh-rCN")
@Preview(
    name = "eink-font-200",
    device = EInkPortrait,
    locale = "zh-rCN",
    fontScale = 2f,
)
@Composable
fun EInkDisplaySettingsScreenshots() {
    SettingsPreview(environment = eInkEnvironment())
}

@PreviewTest
@Preview(name = "auto-unknown-standard", device = PhonePortrait, locale = "zh-rCN")
@Composable
fun AutoUnknownDisplaySettingsScreenshot() {
    SettingsPreview(
        environment = standardEnvironment(
            preference = DisplayPreference.AUTO,
            reason = DisplayDecisionReason.UNKNOWN_DEVICE,
        ),
    )
}

@PreviewTest
@Preview(name = "auto-recognized-eink", device = EInkPortrait, locale = "zh-rCN")
@Composable
fun AutoRecognizedEInkSettingsScreenshot() {
    SettingsPreview(
        environment = eInkEnvironment(
            preference = DisplayPreference.AUTO,
            reason = DisplayDecisionReason.RECOGNIZED_EINK,
            detectedDeviceLabel = "BOOX",
        ),
    )
}

@PreviewTest
@Preview(name = "standard-dark-restored", device = PhonePortrait, locale = "zh-rCN")
@Composable
fun RestoredDarkDisplaySettingsScreenshot() {
    SettingsPreview(
        environment = standardEnvironment(
            colorSchemePreference = ColorSchemePreference.DARK,
            dark = true,
        ),
    )
}

@PreviewTest
@Preview(name = "dynamic-light-fixed", device = PhonePortrait, locale = "zh-rCN")
@Composable
fun FixedDynamicLightSettingsScreenshot() {
    SettingsPreview(
        environment = standardEnvironment(dynamic = true),
        dynamicColorScheme = FixedDynamicLight,
    )
}

@PreviewTest
@Preview(name = "dynamic-dark-fixed", device = PhonePortrait, locale = "zh-rCN")
@Composable
fun FixedDynamicDarkSettingsScreenshot() {
    SettingsPreview(
        environment = standardEnvironment(
            colorSchemePreference = ColorSchemePreference.DARK,
            dark = true,
            dynamic = true,
        ),
        dynamicColorScheme = FixedDynamicDark,
    )
}

@PreviewTest
@Preview(name = "more", device = PhonePortrait, locale = "zh-rCN")
@Composable
fun MoreScreenScreenshot() {
    ScreenSurface(standardEnvironment()) {
        MoreScreen(onOpenDisplaySettings = {}, onOpenAbout = {})
    }
}

@PreviewTest
@Preview(name = "about", device = PhonePortrait, locale = "zh-rCN")
@Composable
fun AboutScreenScreenshot() {
    ScreenSurface(standardEnvironment()) {
        AboutScreen(applicationName = "Tsuyomi", versionName = "0.1.0")
    }
}

@Composable
private fun SettingsPreview(
    environment: DisplayEnvironment,
    dynamicColorScheme: ColorScheme? = null,
) {
    ScreenSurface(environment, dynamicColorScheme) {
        DisplaySettingsScreen(
            state = DisplaySettingsUiState(environment),
            actions = PreviewActions,
        )
    }
}

@Composable
private fun ScreenSurface(
    environment: DisplayEnvironment,
    dynamicColorScheme: ColorScheme? = null,
    content: @Composable () -> Unit,
) {
    DisplayEnvironmentProvider(environment) {
        TsuyomiTheme(environment, dynamicColorScheme) {
            Surface(Modifier.fillMaxSize(), content = content)
        }
    }
}

private fun standardEnvironment(
    preference: DisplayPreference = DisplayPreference.STANDARD,
    reason: DisplayDecisionReason = DisplayDecisionReason.MANUAL_STANDARD,
    colorSchemePreference: ColorSchemePreference = ColorSchemePreference.LIGHT,
    dark: Boolean = false,
    dynamic: Boolean = false,
) = DisplayEnvironment(
    preferences = DisplayPreferences(
        displayPreference = preference,
        colorSchemePreference = colorSchemePreference,
        dynamicColorEnabled = dynamic,
    ),
    effectiveProfile = DisplayProfile.STANDARD,
    decisionReason = reason,
    detectedDeviceLabel = null,
    dynamicColorEligible = true,
    dynamicColorEffective = dynamic,
    effectiveDarkTheme = dark,
    motionPolicy = MotionPolicy.INSTANT,
    redrawEpoch = 0,
)

private fun eInkEnvironment(
    preference: DisplayPreference = DisplayPreference.EINK,
    reason: DisplayDecisionReason = DisplayDecisionReason.MANUAL_EINK,
    detectedDeviceLabel: String? = null,
) = DisplayEnvironment(
    preferences = DisplayPreferences(
        displayPreference = preference,
        colorSchemePreference = ColorSchemePreference.DARK,
        dynamicColorEnabled = true,
    ),
    effectiveProfile = DisplayProfile.EINK,
    decisionReason = reason,
    detectedDeviceLabel = detectedDeviceLabel,
    dynamicColorEligible = false,
    dynamicColorEffective = false,
    effectiveDarkTheme = false,
    motionPolicy = MotionPolicy.INSTANT,
    redrawEpoch = 0,
)

private val FixedDynamicLight = TsuyomiLightColorScheme.copy(
    primary = Color(0xFF34515D),
    secondaryContainer = Color(0xFFD8E5EA),
)

private val FixedDynamicDark = TsuyomiDarkColorScheme.copy(
    primary = Color(0xFFB4CCD5),
    secondaryContainer = Color(0xFF344A54),
)

private val PreviewActions = DisplaySettingsActions(
    onDisplayPreferenceChange = {},
    onColorSchemePreferenceChange = {},
    onDynamicColorEnabledChange = {},
    onRefreshNow = {},
    onRetryWrite = {},
    onAcknowledgeWriteFailure = {},
)
