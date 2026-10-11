/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class TsuyomiTopBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun titleMenuKeepsLongChoiceCompleteAndExposesSelectedState() {
        val longSourceName = "超长来源名称用于验证窄宽度菜单仍完整呈现来源身份而不截断或省略任何必要内容"
        val alternative = "备用来源"
        var current by mutableStateOf(longSourceName)
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    TsuyomiTheme(standardEnvironment) {
                        TsuyomiTopBar(
                            title = current,
                            modifier = Modifier.width(280.dp),
                            titleMenu = TsuyomiTopBarTitleMenu(
                                contentDescription = "切换来源",
                                panelTitle = "选择来源",
                                actions = listOf(longSourceName, alternative).map { name ->
                                    TsuyomiOverflowAction(
                                        label = name,
                                        onClick = { current = name },
                                        selected = current == name,
                                    )
                                },
                            ),
                        )
                    }
                }
            }
        }

        val title = composeRule.onNodeWithText(longSourceName).assertHasClickAction()
        val titleNode = title.fetchSemanticsNode()
        assertTrue(titleNode.touchBoundsInRoot.height >= with(titleNode.layoutInfo.density) { 48.dp.toPx() })
        title.performClick()
        val selected = composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        selected.assertIsDisplayed().assertTextEquals(longSourceName)
        val layouts = mutableListOf<TextLayoutResult>()
        selected.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse(layout.hasVisualOverflow)
        assertTrue(layout.lineCount > 1)

        composeRule.onNodeWithText(alternative).performClick()
        composeRule.onNodeWithText(alternative).assertHasClickAction().performClick()
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertTextEquals(alternative)
    }

    @Test
    fun einkTitleBaselineAndChromeHeightDoNotDependOnSubtitle() {
        val environment = standardEnvironment.copy(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.EINK),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_EINK,
            motionPolicy = MotionPolicy.INSTANT,
        )
        var subtitle by mutableStateOf<String?>("3 本")
        var fontScale by mutableStateOf(1f)
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    TsuyomiTheme(environment) {
                        Column {
                            TsuyomiTopBar("书架", Modifier.testTag("root-bar"), subtitle = subtitle)
                            androidx.compose.material3.Text("正文起点", Modifier.testTag("body-start"))
                        }
                    }
                }
            }
        }
        fun titleBaseline(): Float {
            val title = composeRule.onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString("书架"))),
                useUnmergedTree = true,
            )
            val layouts = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return title.fetchSemanticsNode().boundsInRoot.top + layouts.single().firstBaseline
        }
        for (scale in listOf(1f, 1.15f, 2f)) {
            composeRule.runOnIdle { fontScale = scale; subtitle = "3 本" }
            val baseline = titleBaseline()
            val barHeight = composeRule.onNodeWithTag("root-bar").fetchSemanticsNode().boundsInRoot.height
            val contentTop = composeRule.onNodeWithTag("body-start").fetchSemanticsNode().boundsInRoot.top
            for (value in listOf(null, "128 本", "3 books", "")) {
                composeRule.runOnIdle { subtitle = value }
                assertEquals(baseline, titleBaseline(), 0f)
                assertEquals(barHeight, composeRule.onNodeWithTag("root-bar").fetchSemanticsNode().boundsInRoot.height, 0f)
                assertEquals(contentTop, composeRule.onNodeWithTag("body-start").fetchSemanticsNode().boundsInRoot.top, 0f)
                if (value == null) composeRule.onNodeWithText("3 本").assertDoesNotExist()
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
