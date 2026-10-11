/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.backup

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.then
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy

class TransferScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dataOwnsCanonicalPreferenceResetAndRequiresConfirmation() {
        var resets = 0
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                MaterialTheme {
                    TransferScreen(
                        state = TransferUiState.Idle,
                        onChooseImport = {},
                        onConfirmImport = {},
                        onCancelImport = {},
                        onExport = {},
                        onDismissResult = {},
                        onRetryRecovery = {},
                        onAbortRecovery = {},
                        onResetInterfacePreferences = { resets++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText("重置界面与阅读偏好").assertIsDisplayed().performClick()
        assertEquals(0, resets)
        composeRule.onNodeWithText(
            "仅清除界面与阅读偏好并恢复当前宪章默认值。书籍、收藏夹、进度、历史、来源包、登录凭据、网站镜像和缓存均不会改动。",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("确认重置").performClick()

        assertEquals(1, resets)
    }
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun einkReviewReachesEveryConflictAndWarningAtLargeTypeAndRestoresWithoutImporting() {
        val restoration = StateRestorationTester(composeRule)
        val review = TransferReview("JSON", 120, 3, 0, 0,
            warningCodes = List(120) { "W$it" },
            conflictCodes = List(120) { "existing-progress-retained · C$it" })
        var eink by mutableStateOf(true)
        var imports = 0
        val einkEnvironment = standardEnvironment.copy(
            preferences = standardEnvironment.preferences.copy(displayPreference = DisplayPreference.EINK),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_EINK,
            motionPolicy = MotionPolicy.INSTANT)
        restoration.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 440.dp))
                .then(DeviceConfigurationOverride.FontScale(2f))) {
                DisplayEnvironmentProvider(if (eink) einkEnvironment else standardEnvironment) {
                    MaterialTheme {
                        TransferScreen(TransferUiState.Review(review), {}, { imports++ }, {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        val warningMatchers = review.warningCodes.associateWith { hasText("• $it") }
        val conflictMatchers = review.conflictCodes.indices.associateWith { hasText("C$it）", substring = true) }
        fun reviewPage() = captureReviewPage(warningMatchers, conflictMatchers)
        val seenWarnings = mutableSetOf<String>()
        val seenConflicts = mutableSetOf<Int>()
        var restored = false
        var transitions = 0
        composeRule.onNodeWithText("上一页").assertIsNotEnabled()
        while (true) {
            composeRule.onAllNodes(hasScrollToNodeAction()).assertCountEquals(0)
            val page = reviewPage()
            seenWarnings += page.warningBounds.keys
            val viewport = page.viewport
            val controlsTop = composeRule.onNodeWithText("上一页").fetchSemanticsNode().boundsInRoot.top
            page.warningBounds.values.forEach { bounds ->
                assertTrue(bounds.left >= viewport.left && bounds.right <= viewport.right)
                assertTrue(bounds.top >= viewport.top && bounds.bottom <= controlsTop)
            }
            seenConflicts += page.conflicts
            if (!restored && "W75" in page.warningBounds) {
                val before = page.warningBounds.keys
                composeRule.runOnIdle { eink = false }
                composeRule.waitForIdle()
                composeRule.runOnIdle { eink = true }
                composeRule.waitForIdle()
                assertEquals(before, reviewPage().warningBounds.keys)
                restoration.emulateSavedInstanceStateRestore()
                assertEquals(before, reviewPage().warningBounds.keys)
                restored = true
            }
            val next = composeRule.onNodeWithText("下一页")
            if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
            next.performClick()
            transitions++
            assertTrue("Transfer review endpoint was unreachable", transitions < review.conflictCodes.sumOf(String::length) + review.warningCodes.sumOf(String::length))
            assertEquals(0, imports)
        }
        assertEquals(review.warningCodes.toSet(), seenWarnings)
        assertEquals(review.conflictCodes.indices.toSet(), seenConflicts)
        assertTrue(restored)
        composeRule.onNodeWithText("下一页").assertIsNotEnabled()
        if (!composeRule.onNodeWithText("确认并导入").isDisplayed()) {
            repeat(transitions) {
                if (!composeRule.onNodeWithText("确认并导入").isDisplayed()) composeRule.onNodeWithText("上一页").performClick()
            }
        }
        composeRule.onNodeWithText("确认并导入").assertIsEnabled().performClick()
        assertEquals(1, imports)
    }

    private data class ReviewPageSnapshot(
        val viewport: Rect,
        val warningBounds: Map<String, Rect>,
        val conflicts: Set<Int>,
    )

    private fun captureReviewPage(
        warningMatchers: Map<String, SemanticsMatcher>,
        conflictMatchers: Map<Int, SemanticsMatcher>,
    ): ReviewPageSnapshot {
        // Synchronize the full review-text collection, not every absent code on every page.
        val nodes = composeRule.onAllNodes(SemanticsMatcher("Transfer review text") { node ->
            warningMatchers.values.any { it.matches(node) } || conflictMatchers.values.any { it.matches(node) }
        }).fetchSemanticsNodes()
        val placedMatchers = composeRule.runOnUiThread {
            fun placedMatch(matcher: SemanticsMatcher): Boolean {
                var selected: SemanticsNode? = null
                for (node in nodes) {
                    if (!matcher.matches(node)) continue
                    assertTrue("Expected at most one review node matching ${matcher.description}", selected == null)
                    selected = node
                }
                var layout: LayoutInfo? = selected?.layoutInfo ?: return false
                // ui-test 1.12 isDisplayed rejects these before window/clipping checks.
                while (layout != null) {
                    if (!layout.isPlaced) return false
                    layout = layout.parentInfo
                }
                return true
            }
            warningMatchers.filterValues(::placedMatch) to conflictMatchers.filterValues(::placedMatch)
        }
        // Keep public isDisplayed and bounds behavior for every mounted candidate.
        val warnings = placedMatchers.first.mapNotNull { (code, matcher) ->
            val candidate = composeRule.onNode(matcher)
            if (candidate.isDisplayed()) code to candidate.fetchSemanticsNode().boundsInRoot else null
        }.toMap()
        val conflicts = placedMatchers.second.mapNotNull { (index, matcher) ->
            if (composeRule.onNode(matcher).isDisplayed()) index else null
        }.toSet()
        // Only value copies survive this stable page; profile/restore changes capture anew.
        return ReviewPageSnapshot(composeRule.onRoot().fetchSemanticsNode().boundsInRoot, warnings, conflicts)
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
    }
}
