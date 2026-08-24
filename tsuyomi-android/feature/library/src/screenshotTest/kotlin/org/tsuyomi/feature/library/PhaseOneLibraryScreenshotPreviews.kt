/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.library

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

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "phone-portrait", device = "spec:width=360dp,height=800dp,dpi=420", locale = "zh-rCN")
@Preview(name = "phone-landscape", device = "spec:width=800dp,height=360dp,dpi=420", locale = "zh-rCN")
@Preview(name = "double-compact", device = "spec:width=360dp,height=320dp,dpi=420", locale = "zh-rCN")
@Preview(name = "breakpoint-below", device = "spec:width=599dp,height=800dp,dpi=320", locale = "zh-rCN")
@Preview(name = "breakpoint-at", device = "spec:width=600dp,height=800dp,dpi=320", locale = "zh-rCN")
@Preview(name = "expanded", device = "spec:width=840dp,height=900dp,dpi=240", locale = "zh-rCN")
private annotation class PhaseOneLibraryDevices

@PreviewTest
@PhaseOneLibraryDevices
@Composable
fun LibraryEmptyStateScreenshots() {
    val environment = DisplayEnvironment(
        preferences = DisplayPreferences(
            displayPreference = DisplayPreference.STANDARD,
            colorSchemePreference = ColorSchemePreference.LIGHT,
        ),
        effectiveProfile = DisplayProfile.STANDARD,
        decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
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
                LibraryScreen(
                    state = LibraryUiState(loading = false),
                    collections = emptyList(),
                    selectedCollectionId = null,
                    onCollectionChange = {},
                    onQueryChange = {},
                    onFilterChange = {},
                    onOpenBook = {},
                    onRetry = {},
                    onManageCollections = {},
                )
            }
        }
    }
}
