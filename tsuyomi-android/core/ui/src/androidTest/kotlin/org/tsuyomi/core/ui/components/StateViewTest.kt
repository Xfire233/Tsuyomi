/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    @Test
    fun eInkPrimaryStatesPageEveryLineAndActionWithoutScrollAtDoubleFont() {
        val kind = mutableStateOf(TsuyomiStateKind.LOADING)
        val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK, motionPolicy = MotionPolicy.INSTANT)
        val message = (0..9).joinToString("\n") { "state-line-${it.toString().padStart(2, '0')}" }
        var primaryActions = 0
        var secondaryActions = 0
        var density = 0f
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    TsuyomiTheme(environment) {
                        density = LocalDensity.current.density
                        Box(Modifier.size(280.dp, 220.dp).testTag("state-host")) {
                            StateView(
                                kind = kind.value,
                                title = "状态标题",
                                message = message,
                                actionLabel = "重试",
                                onAction = { primaryActions++ },
                                secondaryActionLabel = "返回",
                                onSecondaryAction = { secondaryActions++ },
                            )
                        }
                    }
                }
            }
        }

        for (stateKind in TsuyomiStateKind.entries) {
            composeRule.runOnIdle { kind.value = stateKind }
            composeRule.onNodeWithText("状态标题").assertIsDisplayed()
            composeRule.onNodeWithText("上一页").assertIsNotEnabled()
            val observed = mutableListOf<String>()
            var primaryActivated = false
            var secondaryActivated = false
            var transitions = 0
            while (true) {
                composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).assertCountEquals(0)
                val host = composeRule.onNodeWithTag("state-host").fetchSemanticsNode().boundsInRoot
                val controlsTop = composeRule.onNode(SemanticsMatcher("Pagination status") {
                    it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("第") == true
                }).fetchSemanticsNode().boundsInRoot.top
                val textNodes = composeRule.onAllNodesWithText("state-line-", substring = true).fetchSemanticsNodes()
                    .filter { node ->
                        runCatching {
                            composeRule.onNodeWithText(node.config[SemanticsProperties.Text].single().text).assertIsDisplayed()
                        }.isSuccess
                    }
                observed += textNodes.flatMap { it.config[SemanticsProperties.Text] }
                    .flatMap { it.text.lineSequence().filter { line -> line.startsWith("state-line-") }.toList() }
                for (node in textNodes) {
                    assertTrue(node.boundsInRoot.top >= host.top && node.boundsInRoot.bottom <= controlsTop)
                }
                val previousBounds = composeRule.onNodeWithText("上一页").fetchSemanticsNode().boundsInRoot
                val nextBounds = composeRule.onNodeWithText("下一页").fetchSemanticsNode().boundsInRoot
                assertEquals("Page actions remain one centred group", previousBounds.top, nextBounds.top, 0f)
                assertEquals(host.center.x, (previousBounds.left + nextBounds.right) / 2, 1f)
                for (bounds in listOf(previousBounds, nextBounds)) {
                    assertTrue(bounds.height >= 48.dp.value * density)
                    assertTrue(bounds.left >= host.left && bounds.right <= host.right && bounds.bottom <= host.bottom)
                }
                if (!primaryActivated && runCatching { composeRule.onNodeWithText("重试").assertIsDisplayed() }.isSuccess) {
                    val action = composeRule.onNodeWithText("重试").assertIsDisplayed()
                    val bounds = action.fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.height >= 48.dp.value * density)
                    assertTrue(bounds.top >= host.top && bounds.bottom <= controlsTop)
                    action.performClick()
                    primaryActivated = true
                }
                if (!secondaryActivated && runCatching { composeRule.onNodeWithText("返回").assertIsDisplayed() }.isSuccess) {
                    val action = composeRule.onNodeWithText("返回").assertIsDisplayed()
                    val bounds = action.fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.height >= 48.dp.value * density)
                    assertTrue(bounds.top >= host.top && bounds.bottom <= controlsTop)
                    action.performClick()
                    secondaryActivated = true
                }
                val next = composeRule.onNodeWithText("下一页")
                if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
                next.performClick()
                assertTrue("Primary state did not reach its endpoint", ++transitions <= 20)
            }
            assertEquals((0..9).map { "state-line-${it.toString().padStart(2, '0')}" }, observed)
            assertTrue(primaryActivated && secondaryActivated)
            composeRule.onNodeWithText("下一页").assertIsNotEnabled()
        }
        composeRule.runOnIdle {
            assertEquals(3, primaryActions)
            assertEquals(3, secondaryActions)
        }
    }

    @Test
    fun fittingEinkStateKeepsCentredIntrinsicActionsWithoutPageControls() {
        val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK, motionPolicy = MotionPolicy.INSTANT)
        var actions = 0
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    Box(Modifier.size(320.dp, 600.dp).testTag("state-host")) {
                        StateView(TsuyomiStateKind.ERROR, "无法加载", message = "请检查后重试",
                            actionLabel = "重试", onAction = { actions++ },
                            secondaryActionLabel = "返回", onSecondaryAction = { actions += 10 })
                    }
                }
            }
        }
        val host = composeRule.onNodeWithTag("state-host").fetchSemanticsNode().boundsInRoot
        val art = composeRule.onNodeWithTag(StateKaomojiTestTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val retry = composeRule.onNodeWithText("重试").fetchSemanticsNode().boundsInRoot
        val back = composeRule.onNodeWithText("返回").fetchSemanticsNode().boundsInRoot
        assertTrue("State actions must keep intrinsic width", retry.width < host.width / 2 && back.width < host.width / 2)
        assertEquals(host.center.x, retry.center.x, 1f)
        assertEquals(host.center.x, back.center.x, 1f)
        assertEquals("The complete state group stays centred", host.center.y, (art.top + back.bottom) / 2, 1f)
        composeRule.onAllNodesWithText("上一页").assertCountEquals(0)
        composeRule.onAllNodesWithText("下一页").assertCountEquals(0)
        composeRule.onNodeWithText("重试").performClick()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.runOnIdle { assertEquals(11, actions) }
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
