/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.media.api.CoverFailureReason
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceChapter

class ReaderPaginationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pagedReaderPacksMultipleShortBlocksIntoEachMeasuredPage() {
        val blocks = (1..42).map { index ->
            ReaderBlock.Paragraph(
                blockId = "paragraph-$index",
                text = paragraph(index),
            )
        }
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "packing",
            contentId = "chapter-1",
            revision = null,
            title = "分页排版验证",
            blocks = blocks,
        )
        val chapter = SourceChapter("chapter-1", document.title, "https://example.test/chapter-1")
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(
                        document = document,
                        restoredLocator = null,
                        onLocatorChanged = { _, _ -> },
                        chapters = listOf(chapter),
                        currentChapterId = chapter.chapterId,
                        onSelectChapter = {},
                        onNavigateUp = {},
                        preferences = PortableReaderPreferences(
                            flow = "paged",
                            fontScale = 1.0,
                            lineHeight = 1.5,
                            theme = "paper",
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("reader-content-surface").performClick()
        composeRule.onNodeWithText(paragraph(1)).assertIsDisplayed()
        composeRule.onNodeWithText(paragraph(2)).assertIsDisplayed()
        composeRule.onNodeWithText(paragraph(42)).assertDoesNotExist()
    }

    @Test
    fun pagedReaderShowsEveryShortFixtureParagraphOnFirstPage() {
        val first = "清晨的海雾漫过石阶，灯塔只剩一圈微光。"
        val second = "邮差把未署名的信收入防水袋，沿着旧轨道继续前行。"
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "short-fixture",
            contentId = "chapter-1",
            revision = null,
            title = "第一章 雾中的灯塔",
            blocks = listOf(
                ReaderBlock.Paragraph("paragraph-1", first),
                ReaderBlock.Paragraph("paragraph-2", second),
            ),
        )
        val chapter = SourceChapter("chapter-1", document.title, "https://example.test/chapter-1")
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(
                        document = document,
                        restoredLocator = null,
                        onLocatorChanged = { _, _ -> },
                        chapters = listOf(chapter),
                        currentChapterId = chapter.chapterId,
                        onSelectChapter = {},
                        onNavigateUp = {},
                        preferences = PortableReaderPreferences(
                            flow = "paged",
                            fontScale = 1.0,
                            lineHeight = 1.5,
                            theme = "paper",
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithText(first).assertIsDisplayed()
        composeRule.onNodeWithText(second).assertIsDisplayed()
    }

    @Test
    fun scrollReaderKeepsMixedBlockOrderAndRetriesOnlyTheFailedImage() {
        val before = "插图前的正文。"
        val after = "插图后的正文。"
        val image = ReaderBlock.Image(
            blockId = "image-1",
            url = "https://img.example.test/chapter/image.webp",
            altText = "章节插图",
            width = 900,
            height = 1200,
        )
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "illustrations",
            contentId = "chapter-image",
            revision = null,
            title = "插图章节",
            blocks = listOf(
                ReaderBlock.Paragraph("paragraph-before", before),
                image,
                ReaderBlock.Paragraph("paragraph-after", after),
            ),
        )
        val chapter = SourceChapter(document.contentId, document.title, "https://example.test/chapter-image")
        val visibleCount = AtomicInteger()
        val retryCount = AtomicInteger()
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(
                        document = document,
                        restoredLocator = null,
                        onLocatorChanged = { _, _ -> },
                        chapters = listOf(chapter),
                        currentChapterId = chapter.chapterId,
                        onSelectChapter = {},
                        onNavigateUp = {},
                        preferences = PortableReaderPreferences(flow = "scroll"),
                        imageStates = mapOf(
                            image.blockId to CoverUiState.Failed(
                                CoverFailureReason.NETWORK,
                                FallbackSpec(document.title, null),
                            ),
                        ),
                        onImageVisible = { visibleCount.incrementAndGet() },
                        onRetryImage = { retryCount.incrementAndGet() },
                    )
                }
            }
        }

        composeRule.waitUntil { visibleCount.get() == 1 }
        val beforeTop = composeRule.onNodeWithText(before).fetchSemanticsNode().boundsInRoot.top
        val imageTop = composeRule.onNodeWithText("章节插图").fetchSemanticsNode().boundsInRoot.top
        val afterTop = composeRule.onNodeWithText(after).fetchSemanticsNode().boundsInRoot.top
        assertTrue(beforeTop < imageTop)
        assertTrue(imageTop < afterTop)

        composeRule.onNodeWithText("重试图片").performClick()
        composeRule.waitUntil { retryCount.get() == 1 }
    }

    @Test
    fun imageOnlyChapterRendersAsReaderContentInsteadOfAnEmptyDocument() {
        val image = ReaderBlock.Image(
            blockId = "image-only",
            url = "https://img.example.test/chapter/image.webp",
            altText = "纯插图章节",
            width = null,
            height = null,
        )
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "illustrations",
            contentId = "chapter-image-only",
            revision = null,
            title = "插图页",
            blocks = listOf(image),
        )
        val chapter = SourceChapter(document.contentId, document.title, "https://example.test/chapter-image-only")
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(
                        document = document,
                        restoredLocator = null,
                        onLocatorChanged = { _, _ -> },
                        chapters = listOf(chapter),
                        currentChapterId = chapter.chapterId,
                        onSelectChapter = {},
                        onNavigateUp = {},
                        preferences = PortableReaderPreferences(flow = "scroll"),
                        imageStates = mapOf(
                            image.blockId to CoverUiState.Failed(
                                CoverFailureReason.NETWORK,
                                FallbackSpec(document.title, null),
                            ),
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithText("纯插图章节").assertIsDisplayed()
        composeRule.onNodeWithText("重试图片").assertIsDisplayed()
    }

    @Test
    fun centerTapHidesChromeAgainAfterRevealingItWithoutConsumingScrollDrag() {
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "chrome-toggle",
            contentId = "chapter-1",
            revision = null,
            title = "Chrome 双向切换",
            blocks = (1..80).map { index ->
                ReaderBlock.Paragraph("paragraph-$index", paragraph(index))
            },
        )
        val chapter = SourceChapter(document.contentId, document.title, "https://example.test/chrome-toggle")
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(
                        document = document,
                        restoredLocator = null,
                        onLocatorChanged = { _, _ -> },
                        chapters = listOf(chapter),
                        currentChapterId = chapter.chapterId,
                        onSelectChapter = {},
                        onNavigateUp = {},
                        preferences = PortableReaderPreferences(flow = "scroll"),
                    )
                }
            }
        }

        val content = composeRule.onNodeWithTag("reader-content-surface")
        content.performTouchInput { click(center) }
        composeRule.onNodeWithTag("reader-top-chrome").assertDoesNotExist()

        content.performTouchInput { click(center) }
        composeRule.onNodeWithTag("reader-top-chrome").assertExists()

        content.performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("reader-top-chrome").assertExists()

        content.performTouchInput { click(center) }
        composeRule.onNodeWithTag("reader-top-chrome").assertDoesNotExist()
    }

    @Test
    fun directTrackTapCommitsItsFinalContinuousTargetExactlyOnce() {
        val blocks = (1..100).map { index ->
            ReaderBlock.Paragraph("paragraph-$index", paragraph(index))
        }
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "track-tap",
            contentId = "chapter-1",
            revision = null,
            title = "进度条单点跳转",
            blocks = blocks,
        )
        val chapter = SourceChapter(document.contentId, document.title, "https://example.test/track-tap")
        val commitCount = AtomicInteger()
        val committedLocator = AtomicReference<org.tsuyomi.shared.locator.ReaderLocator?>()
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(
                        document = document,
                        restoredLocator = null,
                        onLocatorChanged = { locator, _ ->
                            committedLocator.set(locator)
                            commitCount.incrementAndGet()
                        },
                        chapters = listOf(chapter),
                        currentChapterId = chapter.chapterId,
                        onSelectChapter = {},
                        onNavigateUp = {},
                        preferences = PortableReaderPreferences(flow = "scroll"),
                    )
                }
            }
        }

        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        val bounds = slider.fetchSemanticsNode().boundsInRoot
        slider.performTouchInput {
            click(Offset(bounds.width * 0.78f, bounds.height / 2f))
        }

        composeRule.waitUntil(5_000) { commitCount.get() == 1 }
        assertTrue(committedLocator.get()?.blockId != blocks.first().blockId)
        composeRule.runOnIdle { assertEquals(1, commitCount.get()) }
    }

    @Test
    fun pagedSeekUsesDiscreteStopsWhileContinuousSeekKeepsAContinuousRange() {
        val continuous = mutableStateOf(false)
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    val isContinuous = continuous.value
                    ReaderBottomChrome(
                        chapterIndex = 0,
                        chapterCount = 1,
                        chapterProgress = 0,
                        position = ReaderPosition.fromPageIndex(
                            index = 0,
                            pageCount = if (isContinuous) 100 else 4,
                        ),
                        continuousSeek = isContinuous,
                        seekPreview = null,
                        onSeekPreview = {},
                        onSeekCommit = {},
                        onPreviousChapter = {},
                        onOpenContents = {},
                        onOpenSettings = {},
                        onNextChapter = {},
                    )
                }
            }
        }

        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        assertEquals(2, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].steps)

        composeRule.runOnIdle { continuous.value = true }
        composeRule.waitForIdle()
        assertEquals(0, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].steps)
    }

    private fun paragraph(index: Int): String =
        "第 $index 段正文用于验证分页器会按实际视口连续填充多个段落，而不是把每一个段落错误地当成完整的一页。"

    private fun standardEnvironment() = DisplayEnvironment(
        preferences = DisplayPreferences(
            displayPreference = DisplayPreference.STANDARD,
            colorSchemePreference = ColorSchemePreference.LIGHT,
        ),
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
