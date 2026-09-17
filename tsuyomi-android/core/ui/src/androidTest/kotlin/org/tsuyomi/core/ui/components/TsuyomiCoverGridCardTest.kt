/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.tsuyomi.shared.model.CoverCardPresentation


@RunWith(AndroidJUnit4::class)
class TsuyomiCoverGridCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun in_cover_metadata_stays_bounded_at_large_font_scale() {
        val title = "在大字号下仍需完整保留层级的超长书名"
        val supporting = "支持信息也必须留在封面边界以内"
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                DisplayEnvironmentProvider(standardEnvironment) {
                    TsuyomiTheme(environment = standardEnvironment) {
                        Box(Modifier.width(108.dp)) {
                            TsuyomiCoverGridCard(
                                title = title,
                                supportingText = supporting,
                                onClick = {},
                                cover = { Box(Modifier.fillMaxSize().background(Color.White).testTag("cover-grid-card-cover")) },
                                modifier = Modifier.testTag("cover-grid-card"),
                                titleInsideCover = true,
                            )
                        }
                    }
                }
            }
        }

        val titleNode = composeRule.onNodeWithText(title, useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        titleNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val cardBounds = composeRule.onNodeWithTag("cover-grid-card").fetchSemanticsNode().boundsInRoot
        val coverBounds = composeRule.onNodeWithTag("cover-grid-card-cover", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val titleBounds = titleNode.fetchSemanticsNode().boundsInRoot
        val supportingBounds = composeRule.onNodeWithText(supporting, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag("cover-grid-card").assertIsDisplayed()
        assertTrue(layouts.single().lineCount <= 2)
        assertTrue(coverBounds.top <= titleBounds.top)
        assertTrue(titleBounds.bottom <= supportingBounds.top)
        assertTrue(supportingBounds.bottom <= coverBounds.bottom)
        assertTrue(coverBounds.bottom <= cardBounds.bottom)
        val pixels = composeRule.onNodeWithTag("cover-grid-card").captureToImage().toPixelMap()
        val firstLineMiddle = (layouts.single().getLineTop(0) + layouts.single().getLineBottom(0)) / 2f
        val sampleX = ((titleBounds.left - cardBounds.left) / 2f).toInt()
        val sampleY = (titleBounds.top - cardBounds.top + firstLineMiddle).toInt()
        val contrast = 1.05f / (pixels[sampleX, sampleY].luminance() + 0.05f)
        assertTrue("Large cover title must retain at least 3:1 contrast on bright artwork, actual=$contrast", contrast >= 3f)
    }

    @Test
    fun standardCoverCardsUseFiveToSevenGeometry() {
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(environment = standardEnvironment) {
                    Box(Modifier.width(140.dp)) {
                        TsuyomiCoverGridCard(
                            title = "标准封面",
                            supportingText = "作者",
                            onClick = {},
                            cover = { Box(Modifier.fillMaxSize().testTag("standard-card-artwork")) },
                            modifier = Modifier.testTag("standard-cover-card"),
                            titleInsideCover = true,
                        )
                    }
                }
            }
        }

        val card = composeRule.onNodeWithTag("standard-cover-card").fetchSemanticsNode().boundsInRoot
        val artwork = composeRule.onNodeWithTag("standard-card-artwork", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(kotlin.math.abs(card.width / card.height - 5f / 7f) < 0.02f)
        assertTrue(kotlin.math.abs(artwork.width / artwork.height - 5f / 7f) < 0.02f)
    }

    @Test
    fun wideCoverCardsKeepPortraitArtworkBesideTheTitleLane() {
        val title = "完整保留的纵向封面"
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                CoverCardPresentationProvider(CoverCardPresentation.WIDE) {
                    TsuyomiTheme(environment = standardEnvironment) {
                        Box(Modifier.width(320.dp)) {
                            TsuyomiCoverGridCard(
                                title = title,
                                supportingText = "作者",
                                onClick = {},
                                cover = { Box(Modifier.fillMaxSize().testTag("wide-card-artwork")) },
                                modifier = Modifier.testTag("wide-cover-card"),
                                titleInsideCover = true,
                            )
                        }
                    }
                }
            }
        }

        val card = composeRule.onNodeWithTag("wide-cover-card").fetchSemanticsNode().boundsInRoot
        val artwork = composeRule.onNodeWithTag("wide-card-artwork", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val titleBounds = composeRule.onNodeWithText(title, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(kotlin.math.abs(card.width / card.height - 16f / 9f) < 0.02f)
        assertTrue(kotlin.math.abs(artwork.width / artwork.height - 5f / 7f) < 0.02f)
        assertTrue(titleBounds.left >= artwork.right)
        assertTrue(titleBounds.bottom <= card.bottom)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun narrowWideCardsRetainStatusWithoutClippingAtLargeFonts() {
        val fontScale = mutableFloatStateOf(1f)
        val title = "需要保留阅读状态的长书名"
        val status = "未开始"
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale.floatValue)) {
                DisplayEnvironmentProvider(standardEnvironment) {
                    CoverCardPresentationProvider(CoverCardPresentation.WIDE) {
                        TsuyomiTheme(environment = standardEnvironment) {
                            Box(Modifier.width(120.dp)) {
                                TsuyomiCoverGridCard(
                                    title = title,
                                    supportingText = status,
                                    onClick = {},
                                    cover = { Box(Modifier.fillMaxSize()) },
                                    modifier = Modifier.testTag("narrow-wide-card"),
                                    titleInsideCover = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        for (scale in listOf(1f, 2f)) {
            composeRule.runOnIdle { fontScale.floatValue = scale }
            val card = composeRule.onNodeWithTag("narrow-wide-card").fetchSemanticsNode().boundsInRoot
            val titleNode = composeRule.onNodeWithText(title, useUnmergedTree = true)
            val statusNode = composeRule.onNodeWithText(status, useUnmergedTree = true)
            statusNode.assertIsDisplayed()
            val titleBounds = titleNode.fetchSemanticsNode().boundsInRoot
            val statusBounds = statusNode.fetchSemanticsNode().boundsInRoot
            assertTrue(titleBounds.bottom <= statusBounds.top)
            assertTrue(statusBounds.bottom <= card.bottom)
        }
    }

    @Test
    fun shortCjkCoverTitleUsesBalancedTwoLineBreaking() {
        val title = "邻家的天使大人不知不觉把我惯成废人"
        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(environment = standardEnvironment) {
                    Box(Modifier.width(140.dp)) {
                        TsuyomiCoverGridCard(
                            title = title,
                            supportingText = "阅读中",
                            onClick = {},
                            cover = { Box(Modifier.fillMaxSize()) },
                            modifier = Modifier.testTag("balanced-cover-card"),
                            titleInsideCover = true,
                        )
                    }
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(title, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue("Expected a two-line title, found ${layout.lineCount}", layout.lineCount == 2)
        val firstWidth = layout.getLineRight(0) - layout.getLineLeft(0)
        val secondWidth = layout.getLineRight(1) - layout.getLineLeft(1)
        val balance = minOf(firstWidth, secondWidth) / maxOf(firstWidth, secondWidth)
        assertTrue("CJK title lines are visibly unbalanced: $firstWidth vs $secondWidth", balance >= 0.8f)
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
