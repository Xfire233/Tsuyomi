/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
class StateViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingKaomojiAdvancesOnlyAfterTheLowFrequencyCadence() {
        composeRule.mainClock.autoAdvance = false
        try {
            setState(TsuyomiStateKind.LOADING, standardEnvironment)
            composeRule.mainClock.advanceTimeByFrame()
            val art = composeRule.onNodeWithTag(StateKaomojiTestTag, useUnmergedTree = true)
            val initial = art.captureToImage().asAndroidBitmap()

            composeRule.mainClock.advanceTimeBy(MinimumLoadingKaomojiFrameMillis - 1)
            val beforeCadence = art.captureToImage().asAndroidBitmap()
            assertTrue("Loading art must not update faster than the contract cadence", initial.sameAs(beforeCadence))

            composeRule.mainClock.advanceTimeBy(
                LoadingKaomojiFrameMillis - MinimumLoadingKaomojiFrameMillis + 2,
            )
            val advanced = art.captureToImage().asAndroidBitmap()
            assertFalse("Standard loading art must advance after its bounded cadence", initial.sameAs(advanced))
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun instantMotionKeepsTheLoadingKaomojiStatic() {
        composeRule.mainClock.autoAdvance = false
        try {
            setState(TsuyomiStateKind.LOADING, standardEnvironment.copy(motionPolicy = MotionPolicy.INSTANT))
            composeRule.mainClock.advanceTimeByFrame()
            val art = composeRule.onNodeWithTag(StateKaomojiTestTag, useUnmergedTree = true)
            val initial = art.captureToImage().asAndroidBitmap()

            composeRule.mainClock.advanceTimeBy(LoadingKaomojiFrameMillis * 3)
            val later = art.captureToImage().asAndroidBitmap()
            assertTrue("Reduced-motion and E-ink policy must keep state art static", initial.sameAs(later))
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun emptyAndErrorArtUseTheAcceptedBoundedPresetSet() {
        assertEquals(EmptyKaomoji, stateKaomoji(TsuyomiStateKind.EMPTY, "空", null))
        assertTrue(LoadingKaomojiFrameMillis >= MinimumLoadingKaomojiFrameMillis)
        assertEquals(
            listOf("(・_・)", "(・_・)ノ", "(・ω・)ノ本", "本ヽ(・ω・)", "ヽ(・_・)"),
            LoadingKaomojiFrames.map(LoadingKaomojiFrame::text),
        )

        val observedErrors = (0 until 100)
            .map { index -> errorKaomoji("错误 $index", "原因 $index") }
            .toSet()
        assertEquals(ErrorKaomojiFaces.toSet(), observedErrors)
        assertNotEquals(errorKaomoji("错误 0", "原因 0"), errorKaomoji("错误 1", "原因 1"))
    }

    @Test
    fun kaomojiIsDecorativeWhileTextKeepsTheStateMeaning() {
        setState(TsuyomiStateKind.ERROR, standardEnvironment)

        composeRule.onNodeWithTag(StateKaomojiTestTag, useUnmergedTree = true).fetchSemanticsNode()
        composeRule.onNodeWithText("无法加载").fetchSemanticsNode()
        composeRule.onNodeWithText("请检查后重试").fetchSemanticsNode()
    }

    private fun setState(kind: TsuyomiStateKind, environment: DisplayEnvironment) {
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    Surface {
                        StateView(
                            kind = kind,
                            title = if (kind == TsuyomiStateKind.ERROR) "无法加载" else "正在加载",
                            message = if (kind == TsuyomiStateKind.ERROR) "请检查后重试" else null,
                        )
                    }
                }
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
    }
}
