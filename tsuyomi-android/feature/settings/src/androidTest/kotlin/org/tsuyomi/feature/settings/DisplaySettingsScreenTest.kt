/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.then
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

import org.tsuyomi.core.preferences.ColorSchemePreference
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.shared.model.CoverCardPresentation

class DisplaySettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standardProfileDoesNotExposeEInkRefreshControls() {
        render(DisplayProfile.STANDARD)

        composeRule.onAllNodesWithText("墨水屏刷新").assertCountEquals(0)
        composeRule.onAllNodesWithText("立即重绘界面").assertCountEquals(0)
    }

    @Test
    fun eInkProfileExposesRefreshControls() {
        var refreshRequests = 0
        render(
            profile = DisplayProfile.EINK,
            actions = DisplaySettingsActions(
                onDisplayPreferenceChange = {},
                onColorSchemePreferenceChange = {},
                onDynamicColorEnabledChange = {},
                onCoverCardPresentationChange = {},
                onRefreshNow = { refreshRequests += 1 },
                onRetryWrite = {},
                onAcknowledgeWriteFailure = {},
            ),
        )

        showText("墨水屏刷新")
        composeRule.onAllNodesWithText("墨水屏刷新").assertCountEquals(1)
        showText("立即重绘界面")
        composeRule.onNodeWithText("立即重绘界面")
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, refreshRequests) }
    }

    @Test
    fun eInkWriteFailureActionsRemainReachableAcrossMeasuredPages() {
        var retries = 0
        var acknowledgements = 0
        render(
            profile = DisplayProfile.EINK,
            actions = DisplaySettingsActions(
                onDisplayPreferenceChange = {},
                onColorSchemePreferenceChange = {},
                onDynamicColorEnabledChange = {},
                onCoverCardPresentationChange = {},
                onRefreshNow = {},
                onRetryWrite = { retries += 1 },
                onAcknowledgeWriteFailure = { acknowledgements += 1 },
            ),
            writeFailure = DisplayWriteFailure(1),
        )

        showText("重试")
        composeRule.onNodeWithText("重试").assertIsEnabled().performClick()
        showText("知道了")
        composeRule.onNodeWithText("知道了").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, acknowledgements)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun eInkDisplayItemsFitNarrowLargeTypeViewportWithoutScrollSemantics() {
        val expectedItems = setOf(
            "settings-display-item-write-failure-1",
            "settings-display-item-profile",
            "settings-display-item-appearance",
            "settings-display-item-refresh",
        )
        val visitedItems = mutableSetOf<String>()
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 840.dp))
                    .then(DeviceConfigurationOverride.FontScale(2f)),
            ) {
                val display = environment(DisplayProfile.EINK)
                DisplayEnvironmentProvider(display) {
                    TsuyomiTheme(display) {
                        DisplaySettingsScreen(
                            state = DisplaySettingsUiState(display, DisplayWriteFailure(1)),
                            actions = noOpActions,
                        )
                    }
                }
            }
        }

        assertTrue(composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isEmpty())
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        var pages = 0
        var hasNextPage = true
        while (hasNextPage && pages < 12) {
            val footerTop = composeRule.onAllNodesWithText("上一页").fetchSemanticsNodes()
                .firstOrNull()?.boundsInRoot?.top ?: rootBounds.bottom
            val items = composeRule.onAllNodesWithTag("settings-display-item")
            val visibleItems = items.fetchSemanticsNodes().filterIndexed { index, _ -> items[index].isDisplayed() }
            assertTrue("A measured Display item must be visible on every page", visibleItems.isNotEmpty())
            visibleItems.forEach { item ->
                assertTrue(item.boundsInRoot.left >= rootBounds.left)
                assertTrue(item.boundsInRoot.right <= rootBounds.right)
                assertTrue("Display content must remain above the page controls", item.boundsInRoot.bottom <= footerTop)
            }
            expectedItems.forEach { tag ->
                if (composeRule.onNodeWithTag(tag).isDisplayed()) {
                    composeRule.onNodeWithTag(tag).assertIsDisplayed()
                    visitedItems += tag
                }
            }

            val next = composeRule.onNodeWithText("下一页")
            hasNextPage = composeRule.onAllNodesWithText("下一页").fetchSemanticsNodes().isNotEmpty() &&
                !next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
            if (hasNextPage) {
                next.performClick()
                pages += 1
            }
        }

        assertEquals(expectedItems, visitedItems)
        assertTrue(pages > 0)
        assertTrue(composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun einkProfileRetainsHiddenDisplayPreferencesWhenApplicabilityChanges() {
        val savedPreferences = DisplayPreferences(
            displayPreference = DisplayPreference.STANDARD,
            colorSchemePreference = ColorSchemePreference.DARK,
            dynamicColorEnabled = true,
            coverCardPresentation = CoverCardPresentation.WIDE,
        )
        val displayState = mutableStateOf(
            environment(DisplayProfile.STANDARD).copy(preferences = savedPreferences),
        )
        composeRule.setContent {
            val display = displayState.value
            DisplayEnvironmentProvider(display) {
                TsuyomiTheme(display) {
                    DisplaySettingsScreen(
                        state = DisplaySettingsUiState(display),
                        actions = noOpActions,
                    )
                }
            }
        }

        composeRule.onNodeWithText("宽屏 16:9").assertIsSelected()
        composeRule.runOnIdle {
            displayState.value = environment(DisplayProfile.EINK).copy(preferences = savedPreferences)
        }
        composeRule.onAllNodesWithText("宽屏 16:9").assertCountEquals(0)
        showText("深色")
        composeRule.onNodeWithText("深色").assertIsSelected()
        showText("动态颜色")
        composeRule.onNodeWithText("动态颜色").assertIsOn().assertIsNotEnabled()

        composeRule.runOnIdle {
            displayState.value = environment(DisplayProfile.STANDARD).copy(preferences = savedPreferences)
        }
        showText("宽屏 16:9")
        composeRule.onNodeWithText("宽屏 16:9").assertIsSelected()
        showText("动态颜色")
        composeRule.onNodeWithText("动态颜色").assertIsOn()
    }

    @Test
    fun displayDoesNotExposeCanonicalReset() {
        render(DisplayProfile.STANDARD)

        composeRule.onAllNodesWithText("重置界面与阅读偏好").assertCountEquals(0)
    }

    @Test
    fun standardProfileWritesTheSelectedCoverPresentation() {
        var selected: CoverCardPresentation? = null
        render(
            profile = DisplayProfile.STANDARD,
            actions = DisplaySettingsActions(
                onDisplayPreferenceChange = {},
                onColorSchemePreferenceChange = {},
                onDynamicColorEnabledChange = {},
                onCoverCardPresentationChange = { selected = it },
                onRefreshNow = {},
                onRetryWrite = {},
                onAcknowledgeWriteFailure = {},
            ),
        )

        composeRule.onNodeWithText("宽屏 16:9").assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals(CoverCardPresentation.WIDE, selected) }

    }

    @Test
    fun eInkProfileKeepsTheCoverPresentationSelectorInactive() {
        render(profile = DisplayProfile.EINK)

        composeRule.onAllNodesWithText("宽屏 16:9").assertCountEquals(0)
    }

    @Test
    fun unsupportedCoverPresentationDisablesBothChoices() {
        render(
            profile = DisplayProfile.STANDARD,
            preferences = DisplayPreferences(coverCardPresentationReadOnly = true),
        )

        composeRule.onNodeWithText("宽屏 16:9").assertIsNotEnabled()
        composeRule.onNodeWithText("标准 5:7").assertIsNotEnabled()
    }

    private fun render(
        profile: DisplayProfile,
        actions: DisplaySettingsActions = noOpActions,
        preferences: DisplayPreferences? = null,
        writeFailure: DisplayWriteFailure? = null,
    ) {
        val baseEnvironment = environment(profile)
        val environment = preferences?.let { baseEnvironment.copy(preferences = it) } ?: baseEnvironment
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    DisplaySettingsScreen(
                        state = DisplaySettingsUiState(environment, writeFailure),
                        actions = actions,
                    )
                }
            }
        }
    }

    private fun showText(text: String) {
        repeat(12) {
            if (composeRule.onNodeWithText(text).isDisplayed()) return
            val next = composeRule.onNodeWithText("下一页")
            if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) return
            next.performClick()
        }
    }

    private fun environment(profile: DisplayProfile) = DisplayEnvironment(
        preferences = DisplayPreferences(
            displayPreference = if (profile == DisplayProfile.EINK) {
                DisplayPreference.EINK
            } else {
                DisplayPreference.STANDARD
            },
            colorSchemePreference = ColorSchemePreference.LIGHT,
        ),
        effectiveProfile = profile,
        decisionReason = if (profile == DisplayProfile.EINK) {
            DisplayDecisionReason.MANUAL_EINK
        } else {
            DisplayDecisionReason.MANUAL_STANDARD
        },
        detectedDeviceLabel = null,
        dynamicColorEligible = profile == DisplayProfile.STANDARD,
        dynamicColorEffective = false,
        effectiveDarkTheme = false,
        motionPolicy = if (profile == DisplayProfile.EINK) {
            MotionPolicy.INSTANT
        } else {
            MotionPolicy.STANDARD
        },
        redrawEpoch = 0,
    )

    private companion object {
        val noOpActions = DisplaySettingsActions(
            onDisplayPreferenceChange = {},
            onColorSchemePreferenceChange = {},
            onDynamicColorEnabledChange = {},
            onCoverCardPresentationChange = {},
            onRefreshNow = {},
            onRetryWrite = {},
            onAcknowledgeWriteFailure = {},
        )
    }
}
