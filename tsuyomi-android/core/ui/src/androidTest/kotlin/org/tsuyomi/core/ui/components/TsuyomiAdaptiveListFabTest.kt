/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.ui.theme.TsuyomiTheme

@RunWith(AndroidJUnit4::class)
class TsuyomiAdaptiveListFabTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lazyListUsesEndForDownwardAndTopForUpwardScroll() {
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    Box(Modifier.fillMaxSize()) {
                        val state = androidx.compose.foundation.lazy.rememberLazyListState(
                            initialFirstVisibleItemIndex = 10,
                        )
                        LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("list")) {
                            items((1..80).toList()) { Text("条目 $it", Modifier.height(64.dp)) }
                        }
                        TsuyomiAdaptiveListFab(
                            state = state,
                            topLabel = "顶部",
                            endLabel = "末尾",
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }
        }

        verifyDirectionChanges("list")
    }

    @Test
    fun lazyGridUsesEndForDownwardAndTopForUpwardScroll() {
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    Box(Modifier.fillMaxSize()) {
                        val state = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
                            initialFirstVisibleItemIndex = 10,
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = state,
                            modifier = Modifier.fillMaxSize().testTag("grid"),
                        ) {
                            items((1..80).toList()) { Text("卡片 $it", Modifier.height(96.dp)) }
                        }
                        TsuyomiAdaptiveListFab(
                            state = state,
                            topLabel = "顶部",
                            endLabel = "末尾",
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }
        }

        verifyDirectionChanges("grid")
    }

    private fun verifyDirectionChanges(scrollTag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("末尾", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("末尾", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("末尾").assertDoesNotExist()

        repeat(2) {
            composeRule.onNodeWithTag(scrollTag).performTouchInput { swipeDown(durationMillis = 400) }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("顶部", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("顶部", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("顶部").assertDoesNotExist()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("顶部", useUnmergedTree = true).assertIsDisplayed()

        repeat(2) {
            composeRule.onNodeWithTag(scrollTag).performTouchInput { swipeUp(durationMillis = 400) }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("末尾", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("末尾", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("末尾").assertDoesNotExist()
    }

    private val standardEnvironment = DisplayEnvironment(
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
