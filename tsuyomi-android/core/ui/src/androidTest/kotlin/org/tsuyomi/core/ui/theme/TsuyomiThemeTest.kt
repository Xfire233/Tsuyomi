/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.displayGrayscaleLayer
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.preferences.ColorSchemePreference
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences

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

    @Test
    fun eInkProfileSuppliesImmediateMotionToMaterialComponents() {
        val environment = environment(
            profile = DisplayProfile.EINK,
            effectiveDarkTheme = false,
            dynamicColorEffective = false,
        )
        var actualMotionScheme: MotionScheme? = null
        composeRule.setContent {
            TsuyomiTheme(environment) {
                actualMotionScheme = MaterialTheme.motionScheme
            }
        }

        composeRule.runOnIdle {
            assertSame(TsuyomiInstantMotionScheme, actualMotionScheme)
        }
    }

    @Test
    fun changingDisplayProfilePreservesRememberedContentState() {
        val activeEnvironment = mutableStateOf(
            environment(
                profile = DisplayProfile.EINK,
                effectiveDarkTheme = false,
                dynamicColorEffective = false,
            ),
        )
        composeRule.setContent {
            TsuyomiTheme(activeEnvironment.value) {
                val count = remember { mutableStateOf(0) }
                Column {
                    Text("Actions ${count.value}")
                    Button(onClick = { count.value++ }) { Text("Increment") }
                }
            }
        }

        composeRule.onNodeWithText("Increment").performClick()
        composeRule.onNodeWithText("Actions 1").assertIsDisplayed()
        composeRule.runOnIdle {
            activeEnvironment.value = environment(
                profile = DisplayProfile.STANDARD,
                effectiveDarkTheme = false,
                dynamicColorEffective = false,
            )
        }
        composeRule.onNodeWithText("Actions 1").assertIsDisplayed()
    }

    @Test
    fun eInkChromePaletteIsOpaqueAndGrayscale() {
        var actualColors: List<Color>? = null
        composeRule.setContent {
            TsuyomiTheme(
                environment(
                    profile = DisplayProfile.EINK,
                    effectiveDarkTheme = false,
                    dynamicColorEffective = true,
                ),
            ) {
                val scheme = MaterialTheme.colorScheme
                actualColors = listOf(
                    scheme.primary,
                    scheme.onPrimary,
                    scheme.primaryContainer,
                    scheme.onPrimaryContainer,
                    scheme.inversePrimary,
                    scheme.secondary,
                    scheme.onSecondary,
                    scheme.secondaryContainer,
                    scheme.onSecondaryContainer,
                    scheme.tertiary,
                    scheme.onTertiary,
                    scheme.tertiaryContainer,
                    scheme.onTertiaryContainer,
                    scheme.error,
                    scheme.onError,
                    scheme.errorContainer,
                    scheme.onErrorContainer,
                    scheme.background,
                    scheme.onBackground,
                    scheme.surface,
                    scheme.onSurface,
                    scheme.surfaceVariant,
                    scheme.onSurfaceVariant,
                    scheme.scrim,
                    scheme.outline,
                    scheme.outlineVariant,
                    scheme.inverseSurface,
                    scheme.inverseOnSurface,
                    scheme.surfaceDim,
                    scheme.surfaceBright,
                    scheme.surfaceContainerLowest,
                    scheme.surfaceContainerLow,
                    scheme.surfaceContainer,
                    scheme.surfaceContainerHigh,
                    scheme.surfaceContainerHighest,
                )
            }
        }

        composeRule.runOnIdle {
            checkNotNull(actualColors).forEach { color ->
                assertEquals(1f, color.alpha, 0f)
                assertEquals(color.red, color.green, 0f)
                assertEquals(color.green, color.blue, 0f)
            }
        }
    }

    @Test
    fun eInkRasterConvertsColorGlyphsAndRestoresStandardWithoutRemounting() {
        val active = mutableStateOf(environment(DisplayProfile.STANDARD, false, false))
        composeRule.setContent {
            DisplayEnvironmentProvider(active.value) {
                TsuyomiTheme(active.value) {
                    val count = remember { mutableStateOf(0) }
                    Column(Modifier.displayGrayscaleLayer().background(Color.White).testTag("display-raster")) {
                        Text("😀 🌈 📚")
                        Box(Modifier.size(32.dp).background(Color.Red))
                        Button(onClick = { count.value++ }) { Text("Actions ${count.value}") }
                    }
                }
            }
        }
        composeRule.onNodeWithText("Actions 0").performClick()
        assertTrue(rasterColorDifference() > 0.5f)
        composeRule.runOnIdle { active.value = environment(DisplayProfile.EINK, false, false) }
        composeRule.onNodeWithText("Actions 1").assertIsDisplayed()
        assertTrue("Rendered glyphs and artwork must be grayscale", rasterColorDifference() < 0.01f)
        composeRule.runOnIdle { active.value = environment(DisplayProfile.STANDARD, false, false) }
        composeRule.onNodeWithText("Actions 1").assertIsDisplayed()
        assertTrue("Standard must regain its original colors", rasterColorDifference() > 0.5f)
    }

    private fun rasterColorDifference(): Float {
        val pixels = composeRule.onNodeWithTag("display-raster").captureToImage().toPixelMap()
        var difference = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            difference = maxOf(difference, abs(color.red - color.green), abs(color.green - color.blue))
        }
        return difference
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
