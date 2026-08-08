/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy

class TsuyomiThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standardProfileUsesStaticDarkSchemeWhenDynamicColorIsInactive() {
        assertPrimaryColor(
            environment = environment(
                profile = DisplayProfile.STANDARD,
                effectiveDarkTheme = true,
                dynamicColorEffective = false,
            ),
            expected = TsuyomiDarkColorScheme.primary,
        )
    }

    @Test
    fun eInkProfileOverridesAnEffectiveDynamicColorPreference() {
        assertPrimaryColor(
            environment = environment(
                profile = DisplayProfile.EINK,
                effectiveDarkTheme = false,
                dynamicColorEffective = true,
            ),
            expected = TsuyomiEInkColorScheme.primary,
        )
    }

    @Test
    fun standardDynamicColorUsesInjectedDeterministicScheme() {
        val injected = TsuyomiLightColorScheme.copy(primary = Color(0xFF123456))

        assertPrimaryColor(
            environment = environment(
                profile = DisplayProfile.STANDARD,
                effectiveDarkTheme = false,
                dynamicColorEffective = true,
            ),
            expected = injected.primary,
            dynamicColorScheme = injected,
        )
    }

    private fun assertPrimaryColor(
        environment: DisplayEnvironment,
        expected: Color,
        dynamicColorScheme: androidx.compose.material3.ColorScheme? = null,
    ) {
        var actual: Color? = null
        composeRule.setContent {
            TsuyomiTheme(environment, dynamicColorScheme) {
                actual = MaterialTheme.colorScheme.primary
            }
        }
        composeRule.runOnIdle {
            assertEquals(expected, actual)
        }
    }

    private fun environment(
        profile: DisplayProfile,
        effectiveDarkTheme: Boolean,
        dynamicColorEffective: Boolean,
    ) = DisplayEnvironment(
        preferences = DisplayPreferences(
            displayPreference = if (profile == DisplayProfile.EINK) {
                DisplayPreference.EINK
            } else {
                DisplayPreference.STANDARD
            },
            colorSchemePreference = if (effectiveDarkTheme) {
                ColorSchemePreference.DARK
            } else {
                ColorSchemePreference.LIGHT
            },
            dynamicColorEnabled = dynamicColorEffective,
        ),
        effectiveProfile = profile,
        decisionReason = if (profile == DisplayProfile.EINK) {
            DisplayDecisionReason.MANUAL_EINK
        } else {
            DisplayDecisionReason.MANUAL_STANDARD
        },
        detectedDeviceLabel = null,
        dynamicColorEligible = dynamicColorEffective,
        dynamicColorEffective = dynamicColorEffective,
        effectiveDarkTheme = effectiveDarkTheme,
        motionPolicy = if (profile == DisplayProfile.EINK) {
            MotionPolicy.INSTANT
        } else {
            MotionPolicy.STANDARD
        },
        redrawEpoch = 0L,
    )
}
