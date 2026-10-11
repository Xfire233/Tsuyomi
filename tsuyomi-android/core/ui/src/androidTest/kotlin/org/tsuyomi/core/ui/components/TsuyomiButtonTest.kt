/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.tsuyomi.core.display.DisplayProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.ui.theme.TsuyomiTheme

@RunWith(AndroidJUnit4::class)
class TsuyomiButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun einkPaginationCentersCompletePreviousPageNextGroupAndKeepsEndpoints() {
        var nextCalls = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setStandardContent(standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK)) {
            Box(Modifier.fillMaxWidth().testTag("footer")) {
                PaginationBar(page = 1, pageCount = 2, onPrevious = {}, onNext = { nextCalls++ })
            }
        }
        val previous = composeRule.onNodeWithText(context.getString(org.tsuyomi.core.ui.R.string.coreui_pagination_previous))
        val next = composeRule.onNodeWithText(context.getString(org.tsuyomi.core.ui.R.string.coreui_pagination_next))
        previous.assertIsNotEnabled()
        val start = previous.fetchSemanticsNode().boundsInRoot
        val end = next.fetchSemanticsNode().boundsInRoot
        val footer = composeRule.onNodeWithTag("footer").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs((start.left + end.right) / 2f - footer.center.x) <= 1f)
        composeRule.onNodeWithText(context.getString(org.tsuyomi.core.ui.R.string.coreui_pagination_page_status, 1, 2)).assertIsDisplayed()
        next.performClick()
        composeRule.runOnIdle { assertEquals(1, nextCalls) }
    }

    @Test
    fun einkTextActionHasWhiteInteriorThinGrayBoundaryAndButtonSemantics() {
        var activations = 0
        setStandardContent(standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK)) {
            Box(Modifier.background(androidx.compose.ui.graphics.Color.White)) {
                TsuyomiButton("作者", { activations++ }, Modifier.testTag("text-action"), style = TsuyomiButtonStyle.TEXT)
            }
        }
        val action = composeRule.onNodeWithTag("text-action")
        action.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val image = action.captureToImage().asAndroidBitmap()
        assertOpaqueDominant(image, android.graphics.Color.WHITE)
        val node = action.fetchSemanticsNode()
        val minimum = with(node.layoutInfo.density) { 48.dp.toPx() }
        assertTrue(node.boundsInRoot.height >= minimum && node.touchBoundsInRoot.height >= minimum)
        val greyRows = (0 until image.height).filter { y ->
            val pixel = image.getPixel(image.width / 2, y)
            android.graphics.Color.red(pixel) in 1..254 &&
                android.graphics.Color.red(pixel) == android.graphics.Color.green(pixel) &&
                android.graphics.Color.red(pixel) == android.graphics.Color.blue(pixel)
        }
        assertTrue("Compact frame must not occupy the entire 48dp target", greyRows.first() > 0 && greyRows.last() < image.height - 1)
        val edge = image.getPixel(image.width / 2, greyRows.first())
        assertEquals(255, android.graphics.Color.alpha(edge))
        action.performClick()
        composeRule.runOnIdle { assertEquals(1, activations) }
        action.performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, 1f)) }
        composeRule.runOnIdle { assertEquals(2, activations) }
    }

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
        var environment by mutableStateOf(standardEnvironment)
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    inputModeManager = LocalInputModeManager.current
                    TsuyomiButton(
                        text = "取消",
                        onClick = {},
                        modifier = Modifier.focusRequester(focusRequester),
                        style = TsuyomiButtonStyle.TEXT,
                    )
                }
            }
        }

        for (profile in DisplayProfile.entries) {
            composeRule.runOnIdle { environment = standardEnvironment.copy(effectiveProfile = profile) }
            composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
            composeRule.runOnIdle { focusRequester.requestFocus() }
            composeRule.onNode(hasText("取消") and hasClickAction()).assertIsFocused()
        }
    }

    @Test
    fun disabledSecondaryButtonCannotBeActivated() {
        var activated = false
        var environment by mutableStateOf(standardEnvironment)
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    TsuyomiButton(
                        text = "下移",
                        onClick = { activated = true },
                        modifier = Modifier.semantics { contentDescription = "将这本书下移" },
                        style = TsuyomiButtonStyle.SECONDARY,
                        enabled = false,
                    )
                }
            }
        }

        for (profile in DisplayProfile.entries) {
            composeRule.runOnIdle { environment = standardEnvironment.copy(effectiveProfile = profile) }
            val button = composeRule.onNodeWithContentDescription("将这本书下移")
            button.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            button.assertIsNotEnabled()
            button.performTouchInput { click(center) }
            composeRule.runOnIdle { assertFalse(activated) }
        }
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

    @Test
    fun eInkChipSlotKeepsOpaqueSelectionCheckboxSemanticsAndDisabledEndpoint() {
        var selected by mutableStateOf(true)
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK)
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    TsuyomiToggleChip(
                        text = "Filter",
                        selected = selected,
                        stateDescription = if (selected) "Selected" else "Not selected",
                        onClick = { selected = !selected },
                        enabled = enabled,
                        modifier = Modifier.testTag("eink-filter"),
                        labelContent = { Text("Filter slot") },
                    )
                }
            }
        }
        val chip = composeRule.onNodeWithTag("eink-filter")
        chip.assertIsSelected().assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        composeRule.onNodeWithText("Filter slot").assertIsDisplayed()
        assertOpaqueDominant(chip.captureToImage().asAndroidBitmap(), android.graphics.Color.BLACK)
        chip.performClick()
        chip.assertIsNotSelected().assertIsOff()
        assertOpaqueDominant(chip.captureToImage().asAndroidBitmap(), android.graphics.Color.WHITE)
        composeRule.runOnIdle { selected = true; enabled = false }
        chip.assertIsSelected().assertIsOn().assertIsNotEnabled()
        assertOpaqueDominant(chip.captureToImage().asAndroidBitmap(), android.graphics.Color.BLACK)
        chip.performTouchInput { click() }
        composeRule.runOnIdle { assertTrue(selected) }
    }

    @Test
    fun eInkCheckboxAndSwitchExposeNativeCheckedAndDisabledStates() {
        var checked by mutableStateOf(false)
        var switched by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK)
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    Column {
                        TsuyomiCheckboxRow("Remember", checked, { checked = it }, Modifier.testTag("eink-checkbox"), enabled = enabled)
                        TsuyomiSwitch(switched, { switched = it }, Modifier.testTag("eink-switch"), enabled = enabled)
                    }
                }
            }
        }
        val checkbox = composeRule.onNodeWithTag("eink-checkbox")
        val switch = composeRule.onNodeWithTag("eink-switch")
        checkbox.assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).performClick()
        switch.assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()
        checkbox.assertIsOn()
        switch.assertIsOn()
        composeRule.runOnIdle { enabled = false }
        checkbox.assertIsOn().assertIsNotEnabled()
        switch.assertIsOn().assertIsNotEnabled()
        checkbox.performTouchInput { click() }
        switch.performTouchInput { click() }
        composeRule.runOnIdle { assertTrue(checked && switched) }
    }

    private fun assertOpaqueDominant(bitmap: android.graphics.Bitmap, expected: Int) {
        var sampled = 0
        var matching = 0
        for (y in bitmap.height / 4 until bitmap.height * 3 / 4) {
            for (x in bitmap.width / 4 until bitmap.width * 3 / 4) {
                val pixel = bitmap.getPixel(x, y)
                assertEquals(255, android.graphics.Color.alpha(pixel))
                if (pixel == expected) matching++
                sampled++
            }
        }
        assertTrue("Control interior did not retain its opaque selection color", matching > sampled / 2)
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
