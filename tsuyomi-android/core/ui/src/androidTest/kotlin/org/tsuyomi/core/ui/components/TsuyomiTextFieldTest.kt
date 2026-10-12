/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.ui.theme.TsuyomiTheme

@RunWith(AndroidJUnit4::class)
class TsuyomiTextFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionAwareFieldKeepsTheInsertionPointAcrossRecomposition() {
        var value by mutableStateOf(TextFieldValue("AB", selection = TextRange(1)))
        val environment = DisplayEnvironment(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.STANDARD),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.INSTANT,
            redrawEpoch = 0L,
        )
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    TsuyomiTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = "颜色代码",
                        singleLine = true,
                        modifier = Modifier.testTag("selection-aware-field"),
                    )
                }
            }
        }

        val field = composeRule.onNodeWithTag("selection-aware-field")
        field.performTextInput("X")
        composeRule.runOnIdle {
            assertEquals("AXB", value.text)
            assertEquals(TextRange(2), value.selection)
        }
        field.performTextInput("Y")
        composeRule.runOnIdle {
            assertEquals("AXYB", value.text)
            assertEquals(TextRange(3), value.selection)
        }
    }
}
