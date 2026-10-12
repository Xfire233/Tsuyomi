/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
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
import org.tsuyomi.core.ui.components.AppScaffold
import org.tsuyomi.core.ui.components.LocalTsuyomiModalController
import org.tsuyomi.core.ui.components.TsuyomiModalController
import org.tsuyomi.core.ui.layout.TsuyomiWindowSize
class MoreScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun moreUsesOneFlatFiveRowHierarchyInCanonicalOrder() {
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                MaterialTheme {
                    MoreScreen(
                        onOpenDisplaySettings = {},
                        onOpenReaderSettings = {},
                        onOpenDataTransfer = {},
                        onOpenHelp = {},
                        onOpenAbout = {},
                    )
                }
            }
        }

        val labels = listOf("显示", "阅读", "数据", "帮助", "关于")
        val topEdges = labels.map { composeRule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(topEdges.zipWithNext().all { (first, second) -> first < second })
        composeRule.onAllNodesWithText("设置").assertCountEquals(0)
        composeRule.onAllNodesWithText("支持").assertCountEquals(0)
    }
    @Test
    fun fittingEinkMoreKeepsTheCompleteListWithoutPageControls() {
        composeRule.setContent {
            Box(Modifier.size(360.dp, 800.dp)) {
                DisplayEnvironmentProvider(einkEnvironment) {
                    MaterialTheme {
                        MoreScreen({}, {}, {}, {}, {})
                    }
                }
            }
        }
        listOf("显示", "阅读", "数据", "帮助", "关于").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onAllNodesWithText("上一页").assertCountEquals(0)
        composeRule.onAllNodesWithText("下一页").assertCountEquals(0)
        assertNoScrollActions()
    }

    @Test
    fun einkMoreKeepsLaterActionsReachableWithoutScroll() {
        var aboutOpens = 0
        composeRule.setContent {
            Box(Modifier.size(260.dp, 320.dp)) {
                DisplayEnvironmentProvider(einkEnvironment) {
                    MaterialTheme {
                        MoreScreen(
                            onOpenDisplaySettings = {},
                            onOpenReaderSettings = {},
                            onOpenDataTransfer = {},
                            onOpenHelp = {},
                            onOpenAbout = { aboutOpens++ },
                        )
                    }
                }
            }
        }

        assertNoScrollActions()
        advanceUntilDisplayed("关于")
        composeRule.onNodeWithText("关于").performClick()
        assertEquals(1, aboutOpens)
    }

    @Test
    fun einkAboutLicenseUsesMeasuredTextPagesAndCanClose() {
        var licenseVisible by androidx.compose.runtime.mutableStateOf(false)
        var modalController: TsuyomiModalController? = null
        val license = (0 until 90).joinToString("\n") { "License clause $it preserves all complete words and punctuation." } +
            "\nEND OF TERMS AND CONDITIONS"
        composeRule.setContent {
            Box(Modifier.size(320.dp, 540.dp)) {
                DisplayEnvironmentProvider(einkEnvironment) {
                    MaterialTheme {
                        AppScaffold(
                            windowSize = TsuyomiWindowSize(320, 540),
                            topBar = {},
                            navigation = {},
                            modifier = Modifier.fillMaxSize().testTag("settings-about-host"),
                        ) {
                            modalController = LocalTsuyomiModalController.current
                            AboutScreen(
                                applicationName = "Tsuyomi",
                                versionName = "0.5.0",
                                licenseText = license,
                                licenseVisible = licenseVisible,
                                onLicenseVisibilityChanged = { licenseVisible = it },
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("开源许可证").performClick()
        composeRule.runOnIdle { assertTrue(checkNotNull(modalController).modalActive) }
        composeRule.onNodeWithTag("settings-about-host", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        val licensePane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "开源许可证")
        val inLicensePane = hasAnyAncestor(licensePane)
        assertNoScrollActions()
        advanceUntilDisplayed("END OF TERMS AND CONDITIONS", scope = inLicensePane)
        advanceUntilDisplayed("关闭", scope = inLicensePane)
        composeRule.onNode(hasText("关闭") and inLicensePane).assertIsDisplayed().performClick()
        assertEquals(false, licenseVisible)
        composeRule.runOnIdle { assertTrue(!checkNotNull(modalController).modalActive) }
        composeRule.onNodeWithTag("settings-about-host", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
        composeRule.onNodeWithText("开源许可证").assertIsDisplayed().performClick()
        advanceUntilDisplayed("关闭", scope = inLicensePane)
        composeRule.onNode(hasText("关闭") and inLicensePane).assertIsDisplayed().performClick()
    }

    private fun assertNoScrollActions() {
        composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    private fun advanceUntilDisplayed(
        text: String,
        maxPageTransitions: Int = 100,
        scope: SemanticsMatcher = SemanticsMatcher("Any surface") { true },
    ) {
        val target = hasText(text, substring = true) and scope
        repeat(maxPageTransitions) {
            val nodes = composeRule.onAllNodes(target)
            if (nodes.fetchSemanticsNodes().indices.any { nodes[it].isDisplayed() }) return
            composeRule.onNode(hasText("下一页") and scope).assertIsDisplayed().assertIsEnabled().performClick()
        }
        composeRule.onNode(target).assertIsDisplayed()
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

