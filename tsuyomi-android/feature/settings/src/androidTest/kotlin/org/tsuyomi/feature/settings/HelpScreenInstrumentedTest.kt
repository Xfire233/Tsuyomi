/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
class HelpScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun replay_marks_only_the_versioned_explanation_after_acknowledgement() {
        var seen: Pair<String, Int>? = null
        var selectedIntroductionId by mutableStateOf<String?>(null)
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                MaterialTheme {
                    HelpScreen(
                        introductionsEnabled = true,
                        seenVersions = emptySet(),
                        onIntroductionsEnabledChanged = {},
                        selectedIntroductionId = selectedIntroductionId,
                        onIntroductionSelected = { selectedIntroductionId = it },
                        onIntroductionSeen = { id, version -> seen = id to version },
                        onResetSeenVersions = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("网站镜像").performScrollTo().performClick()
        assertNull(seen)
        composeRule.onNodeWithText("知道了").performClick()

        assertEquals("website-mirror" to 1, seen)
    }

    @Test
    fun helpDoesNotExposeInterfacePreferenceReset() {
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                MaterialTheme {
                    HelpScreen(
                        introductionsEnabled = true,
                        seenVersions = emptySet(),
                        onIntroductionsEnabledChanged = {},
                        selectedIntroductionId = null,
                        onIntroductionSelected = {},
                        onIntroductionSeen = { _, _ -> },
                        onResetSeenVersions = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("重置界面与阅读偏好").assertCountEquals(0)
    }

    @Test
    fun einkKeepsLaterIntroductionActionReachableWithoutScroll() {
        var selectedIntroductionId by mutableStateOf<String?>(null)
        var seen: Pair<String, Int>? = null
        composeRule.setContent {
            Box(Modifier.size(280.dp, 360.dp)) {
                DisplayEnvironmentProvider(einkEnvironment) {
                    MaterialTheme {
                        HelpScreen(
                            introductionsEnabled = true,
                            seenVersions = emptySet(),
                            onIntroductionsEnabledChanged = {},
                            selectedIntroductionId = selectedIntroductionId,
                            onIntroductionSelected = { selectedIntroductionId = it },
                            onIntroductionSeen = { id, version -> seen = id to version },
                            onResetSeenVersions = {},
                        )
                    }
                }
            }
        }

        assertNoScrollActions()
        advanceUntilDisplayed("数据导入与导出")
        composeRule.onNodeWithText("数据导入与导出").performClick()
        composeRule.onNodeWithText("知道了").performClick()
        assertEquals("data-transfer" to 1, seen)
    }

    @Test
    fun helpQueryAndExpandedAnswerSurvivePageAndProfileChanges() {
        var profile by mutableStateOf(DisplayProfile.EINK)
        composeRule.setContent {
            Box(Modifier.size(320.dp, 480.dp)) {
                val environment = if (profile == DisplayProfile.EINK) einkEnvironment else standardEnvironment
                DisplayEnvironmentProvider(environment) {
                    MaterialTheme {
                        HelpScreen(
                            introductionsEnabled = true,
                            seenVersions = emptySet(),
                            onIntroductionsEnabledChanged = {},
                            selectedIntroductionId = null,
                            onIntroductionSelected = {},
                            onIntroductionSeen = { _, _ -> },
                            onResetSeenVersions = {},
                        )
                    }
                }
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("缓存")
        composeRule.onNode(hasSetTextAction()).performTextInputSelection(TextRange(1))
        composeRule.onNode(hasSetTextAction()).assertDraft("缓存", TextRange(1))
        advanceUntilDisplayed("缓存包含什么？")
        composeRule.onNodeWithText("缓存包含什么？").performClick()
        assertTrue(isQuestionExpanded())
        advanceUntilDisplayed("重置功能说明已读版本")
        showSearchField()
        composeRule.onNode(hasSetTextAction()).assertDraft("缓存", TextRange(1))
        advanceUntilDisplayed("缓存包含什么？")
        assertTrue(isQuestionExpanded())

        composeRule.runOnIdle { profile = DisplayProfile.STANDARD }
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performScrollTo().assertDraft("缓存", TextRange(1))
        composeRule.onNodeWithText("缓存包含什么？").performScrollTo()
        assertTrue(isQuestionExpanded())

        composeRule.runOnIdle { profile = DisplayProfile.EINK }
        composeRule.waitForIdle()
        assertNoScrollActions()
        showSearchField()
        composeRule.onNode(hasSetTextAction()).assertDraft("缓存", TextRange(1))
        advanceUntilDisplayed("缓存包含什么？")
        assertTrue(isQuestionExpanded())
        advanceUntilDisplayed("缓存保存已验证")
        showSearchField()
        composeRule.onNode(hasSetTextAction()).assertDraft("缓存", TextRange(1)).performTextInput("命中")
        composeRule.onNode(hasSetTextAction()).assertDraft("缓命中存", TextRange(3))
    }

    private fun isQuestionExpanded(): Boolean = composeRule
        .onNodeWithText("缓存包含什么？")
        .fetchSemanticsNode()
        .config.contains(SemanticsActions.Collapse)

    private fun SemanticsNodeInteraction.assertDraft(text: String, selection: TextRange) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))

    private fun showSearchField() {
        repeat(100) {
            if (composeRule.onNode(hasSetTextAction()).isDisplayed()) return
            composeRule.onNodeWithText("上一页").assertIsDisplayed().assertIsEnabled().performClick()
        }
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    private fun assertNoScrollActions() {
        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    private fun advanceUntilDisplayed(text: String, maxPageTransitions: Int = 100) {
        repeat(maxPageTransitions) {
            val displayed = composeRule.onAllNodes(hasText(text, substring = true))
            if (displayed.fetchSemanticsNodes().indices.any { displayed[it].isDisplayed() }) return
            composeRule.onNodeWithText("下一页").assertIsDisplayed().assertIsEnabled().performClick()
        }
        composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
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
            redrawEpoch = 0,
        )
        val einkEnvironment = standardEnvironment.copy(
            preferences = standardEnvironment.preferences.copy(displayPreference = DisplayPreference.EINK),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_EINK,
            motionPolicy = MotionPolicy.INSTANT,
        )
    }
}
