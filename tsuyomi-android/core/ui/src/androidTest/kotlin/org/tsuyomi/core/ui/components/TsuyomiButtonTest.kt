/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.ui.theme.TsuyomiTheme

@RunWith(AndroidJUnit4::class)
class TsuyomiButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryButtonKeepsItsTouchGeometryDuringAHeldPress() {
        var density = 0f
        var activations = 0
        setStandardContent {
            density = LocalDensity.current.density
            TsuyomiButton(text = "保存更改", onClick = { activations++ })
        }

        val button = composeRule.onNode(hasText("保存更改") and hasClickAction())
        val before = button.fetchSemanticsNode().boundsInRoot
        assertTrue(before.height >= 48.dp.value * density)

        composeRule.mainClock.autoAdvance = false
        try {
            button.performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeBy(250)
            val whileHeld = button.fetchSemanticsNode().boundsInRoot
            assertEquals(before, whileHeld)
            button.performTouchInput { cancel() }
            composeRule.runOnIdle { assertEquals(0, activations) }
            button.performTouchInput { repeat(2) { click(center) } }
            composeRule.mainClock.advanceTimeBy(250)
            composeRule.runOnIdle { assertEquals(2, activations) }
            assertEquals(before, button.fetchSemanticsNode().boundsInRoot)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun textButtonAcceptsKeyboardFocus() {
        val focusRequester = FocusRequester()
        lateinit var inputModeManager: InputModeManager
        setStandardContent {
            inputModeManager = LocalInputModeManager.current
            TsuyomiButton(
                text = "取消",
                onClick = {},
                modifier = Modifier.focusRequester(focusRequester),
                style = TsuyomiButtonStyle.TEXT,
            )
        }

        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { focusRequester.requestFocus() }
        composeRule.onNode(hasText("取消") and hasClickAction()).assertIsFocused()
    }

    @Test
    fun disabledSecondaryButtonCannotBeActivated() {
        var activated = false
        setStandardContent {
            TsuyomiButton(
                text = "不可用操作",
                onClick = { activated = true },
                style = TsuyomiButtonStyle.SECONDARY,
                enabled = false,
            )
        }

        val button = composeRule.onNode(hasText("不可用操作"))
        button.assertIsNotEnabled()
        button.performTouchInput { click(center) }
        composeRule.runOnIdle { assertFalse(activated) }
    }

    @Test
    fun instantButtonCommitsPressFeedbackWithoutIntermediateFrames() {
        assertInstantPressFeedback(TsuyomiButtonStyle.PRIMARY)
    }

    @Test
    fun instantTextButtonRetainsVisiblePressFeedback() {
        assertInstantPressFeedback(TsuyomiButtonStyle.TEXT)
    }

    private fun assertInstantPressFeedback(style: TsuyomiButtonStyle) {
        setStandardContent(environment = standardInstantEnvironment) {
            TsuyomiButton(text = "保存更改", onClick = {}, style = style)
        }
        val button = composeRule.onNode(hasText("保存更改") and hasClickAction())
        val released = button.captureToImage().asAndroidBitmap()
        composeRule.mainClock.autoAdvance = false
        try {
            button.performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeByFrame()
            val firstPressedFrame = button.captureToImage().asAndroidBitmap()
            composeRule.mainClock.advanceTimeBy(250)
            val settled = button.captureToImage().asAndroidBitmap()
            assertFalse("Static policy must retain visible press feedback", released.sameAs(settled))
            assertTrue("Static press feedback must not animate between frames", firstPressedFrame.sameAs(settled))
            button.performTouchInput { up() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun setStandardContent(
        environment: DisplayEnvironment = standardEnvironment,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment, content = content)
            }
        }
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
            redrawEpoch = 0L,
        )

        val standardInstantEnvironment = standardEnvironment.copy(
            motionPolicy = MotionPolicy.INSTANT,
        )
    }
}
