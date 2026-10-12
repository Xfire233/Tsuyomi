/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.model.CoverCardPresentation


@RunWith(AndroidJUnit4::class)
class TsuyomiCoverGridCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun einkSharedCoverBackingFitsOnlyActualTitleLinesAndPadding() {
        val title = mutableStateOf("短标题")
        val environment = standardEnvironment.copy(effectiveProfile = DisplayProfile.EINK, motionPolicy = MotionPolicy.INSTANT)
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    Box(Modifier.width(140.dp)) {
                        TsuyomiCoverCardContent(
                            title = title.value,
                            supportingText = null,
                            cover = { Box(Modifier.fillMaxSize().background(Color.White)) },
                            modifier = Modifier.testTag("eink-cover"),
                            metadataModifier = Modifier.testTag("title-backing"),
                        )
                    }
                }
            }
        }
        for (value in listOf("短标题", "一个足够长而需要两行并且保留省略行为的封面标题")) {
            composeRule.runOnIdle { title.value = value }
            val backing = composeRule.onNodeWithTag("title-backing").fetchSemanticsNode().boundsInRoot
            val text = composeRule.onNodeWithText(value).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val cover = composeRule.onNodeWithTag("eink-cover").fetchSemanticsNode().boundsInRoot
            assertEquals(with(composeRule.density) { TsuyomiSpacing.Xs.toPx() }, text.top - backing.top, 1f)
            assertEquals(with(composeRule.density) { 6.dp.toPx() }, backing.bottom - text.bottom, 1f)
            assertEquals(5f / 7f, cover.width / cover.height, 0.02f)
            assertTrue(backing.top >= cover.top && backing.bottom <= cover.bottom)
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(value).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.single().lineCount <= 2)
            assertEquals(if (value == "短标题") 1 else 2, layouts.single().lineCount)
        }
    }

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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun standardThreeColumnTitleUsesAvailableLineWidth() {
        // 1080px / 420dpi = 411dp; the Home grid's 16dp gutters and 8dp gaps leave ~121dp per card.
        val title = "奇招百出的维多利亚"
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1f)) {
                DisplayEnvironmentProvider(standardEnvironment) {
                    TsuyomiTheme(environment = standardEnvironment) {
                        Box(Modifier.width(121.dp)) {
                            TsuyomiCoverGridCard(
                                title = title,
                                supportingText = null,
                                onClick = {},
                                cover = { Box(Modifier.fillMaxSize()) },
                                modifier = Modifier.testTag("three-column-cover-card"),
                                titleInsideCover = true,
                            )
                        }
                    }
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(title, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue("Expected two title lines at actual width: ${layout.lineCount}", layout.lineCount == 2)
        val firstLineEnd = layout.getLineEnd(0, visibleEnd = true)
        assertTrue("First line unexpectedly contains the whole title", firstLineEnd < title.length)
        val trailingSlack = layout.size.width - layout.getLineRight(0)
        val nextGlyphWidth = layout.getBoundingBox(firstLineEnd).width
        val card = composeRule.onNodeWithTag("three-column-cover-card").fetchSemanticsNode().boundsInRoot
        val symmetricInset = with(composeRule.density) { 8.dp.toPx() }
        assertTrue("Unexpected three-column card width ${card.width}px", kotlin.math.abs(card.width - with(composeRule.density) { 121.dp.toPx() }) <= 2f)
        assertTrue("Unexpected title viewport ${layout.size.width}px in ${card.width}px card", kotlin.math.abs(layout.size.width - (card.width - symmetricInset * 2)) <= 2f)
        assertTrue("First line breaks at $firstLineEnd with ${trailingSlack}px unused (next glyph ${nextGlyphWidth}px)",
            firstLineEnd >= 7 && trailingSlack < nextGlyphWidth + 1f)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun standardThreeColumnTitleKeepsClosingPunctuationWithItsPrecedingGlyph() {
        val title = "暴怒千金发誓复仇。～凭借魔导书之力打垮祖国～"
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1f)) {
                DisplayEnvironmentProvider(standardEnvironment) {
                    TsuyomiTheme(environment = standardEnvironment) {
                        Box(Modifier.width(121.dp)) {
                            TsuyomiCoverGridCard(
                                title = title,
                                supportingText = null,
                                onClick = {},
                                cover = { Box(Modifier.fillMaxSize()) },
                                titleInsideCover = true,
                            )
                        }
                    }
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(title, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val firstLineEnd = layout.getLineEnd(0, visibleEnd = true)
        val slack = layout.size.width - layout.getLineRight(0)
        val firstGlyphWidth = layout.getBoundingBox(firstLineEnd).width
        val closingPunctuationWidth = layout.getBoundingBox(firstLineEnd + 1).width

        assertTrue("Closing punctuation must not start a new line", firstLineEnd == title.indexOf("仇。"))
        assertTrue("A single glyph fits but the legal glyph/punctuation pair does not: slack=$slack",
            slack >= firstGlyphWidth && slack < firstGlyphWidth + closingPunctuationWidth)
    }

    @Test
    fun eInkArtworkIgnoresStoredWidePresentation() {
        composeRule.setContent {
            DisplayEnvironmentProvider(eInkEnvironment) {
                CoverCardPresentationProvider(CoverCardPresentation.WIDE) {
                    TsuyomiTheme(environment = eInkEnvironment) {
                        Box(Modifier.width(140.dp)) {
                            TsuyomiCoverGridCard(
                                title = "E-ink cover",
                                supportingText = null,
                                onClick = {},
                                cover = { Box(Modifier.fillMaxSize()) },
                                modifier = Modifier.testTag("eink-cover-card"),
                                titleInsideCover = true,
                            )
                        }
                    }
                }
            }
        }

        val card = composeRule.onNodeWithTag("eink-cover-card").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(card.width / card.height - 5f / 7f) < 0.02f)
    }

    @Test
    fun eInkCoverAndMediaImagesRenderBitmapPixelsInGrayscale() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.RED)
        }
        composeRule.setContent {
            DisplayEnvironmentProvider(eInkEnvironment) {
                TsuyomiTheme(environment = eInkEnvironment) {
                    Row {
                        CoverImage(
                            state = CoverUiState.Ready(bitmap),
                            modifier = Modifier.size(48.dp).testTag("eink-cover-image"),
                        )
                        HostMediaImage(
                            state = CoverUiState.Ready(bitmap),
                            altText = "Illustration",
                            onRetry = {},
                            modifier = Modifier.size(48.dp).testTag("eink-host-media-image"),
                        )
                    }
                }
            }
        }

        listOf("eink-cover-image", "eink-host-media-image").forEach { tag ->
            val image = composeRule.onNodeWithTag(tag).captureToImage()
            val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
            assertEquals("Red input must be rendered without chroma", pixel.red, pixel.green, 0.01f)
            assertEquals("Red input must be rendered without chroma", pixel.green, pixel.blue, 0.01f)
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
        val eInkEnvironment = standardEnvironment.copy(
            preferences = DisplayPreferences(
                displayPreference = DisplayPreference.EINK,
                coverCardPresentation = CoverCardPresentation.WIDE,
            ),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_EINK,
            motionPolicy = MotionPolicy.INSTANT,
        )
    }
}
