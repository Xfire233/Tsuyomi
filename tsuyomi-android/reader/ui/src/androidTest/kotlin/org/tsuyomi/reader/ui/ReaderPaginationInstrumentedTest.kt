/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.then
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.espresso.Espresso.pressBack
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.time.Instant
import androidx.compose.ui.test.swipeUp
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.preferences.ColorSchemePreference
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.media.api.CoverFailureReason
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.locator.bookmarkPositionKey
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceChapter

class ReaderPaginationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun persistedTypographyUpdatesAndResetReachTheMountedReader() {
        val initial = PortableReaderPreferences(flow = "paged", fontScale = 1.0, lineHeight = 1.5, theme = "paper")
        val preferences = mutableStateOf(initial)
        val commits = AtomicInteger()
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "live-preferences",
            contentId = "chapter-1",
            revision = null,
            title = "排版与主题",
            blocks = (1..12).map { ReaderBlock.Paragraph("paragraph-$it", paragraph(it)) },
        )
        val chapter = SourceChapter("chapter-1", document.title, "https://example.test/chapter-1")
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> commits.incrementAndGet() },
                    chapters = listOf(chapter),
                    currentChapterId = chapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectChapter = {},
                    onNavigateUp = {},
                    preferences = preferences.value,
                    onPreferencesChanged = { preferences.value = it },)
                }
            }
        }
        composeRule.onNodeWithTag("reader-content-surface").performClick()
        val body = composeRule.onNodeWithText(paragraph(1))
        val originalHeight = body.fetchSemanticsNode().boundsInRoot.height
        composeRule.runOnIdle {
            preferences.value = initial.copy(fontScale = 1.4, lineHeight = 1.9, fontFamily = "serif", fontWeight = 500)
        }
        body.assertIsDisplayed()
        assertTrue("Persisted typography must reflow mounted text", body.fetchSemanticsNode().boundsInRoot.height > originalHeight)
        val backgrounds = mutableSetOf<Color>()
        for (theme in listOf("paper", "warmGray", "nightInk", "black", "inkGreen")) {
            composeRule.runOnIdle { preferences.value = preferences.value.copy(theme = theme) }
            val background = composeRule.onNodeWithTag("reader-content-surface").captureToImage()
                .toPixelMap(width = 1, height = 1)[0, 0]
            backgrounds += background
            val bodyImage = body.captureToImage()
            val pixels = bodyImage.toPixelMap(width = minOf(160, bodyImage.width), height = minOf(160, bodyImage.height))
            val backgroundLuminance = background.luminance()
            var strongestContrast = 1f
            for (y in 0 until pixels.height step 2) {
                for (x in 0 until pixels.width step 2) {
                    val foregroundLuminance = pixels[x, y].luminance()
                    strongestContrast = maxOf(strongestContrast,
                        (maxOf(backgroundLuminance, foregroundLuminance) + 0.05f) /
                            (minOf(backgroundLuminance, foregroundLuminance) + 0.05f))
                }
            }
            assertTrue("$theme must render readable body text", strongestContrast >= 4.5f)
        }
        assertEquals("Each preset must change the rendered background", 5, backgrounds.size)
        composeRule.runOnIdle { preferences.value = initial }
        body.assertIsDisplayed()
        assertEquals(originalHeight, body.fetchSemanticsNode().boundsInRoot.height, 1f)
        composeRule.runOnIdle { assertEquals(0, commits.get()) }
    }

    @Test
    fun nightReaderSettingsTypographyTextContrastsWithItsSheetSurface() {
        val environment = standardEnvironment()
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "night-settings-contrast",
            contentId = "chapter-1",
            revision = null,
            title = "夜间设置对比度",
            blocks = listOf(ReaderBlock.Paragraph("paragraph-1", paragraph(1))),
        )
        val chapter = SourceChapter("chapter-1", document.title, "https://example.test/chapter-1")
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    chapters = listOf(chapter),
                    currentChapterId = chapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectChapter = {},
                    onNavigateUp = {},
                    preferences = PortableReaderPreferences(flow = "paged", theme = "nightInk"),)
                }
            }
        }

        composeRule.onNodeWithTag("reader-bottom-action-设置").performClick()
        composeRule.onNodeWithTag("reader-settings-sheet").assertIsDisplayed()
        val label = composeRule.onNodeWithText("字号")
        label.assertIsDisplayed()
        val pixels = label.captureToImage().toPixelMap()
        val backgroundLuminance = pixels[0, 0].luminance()
        var strongestContrast = 1f
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val foregroundLuminance = pixels[x, y].luminance()
                strongestContrast = maxOf(
                    strongestContrast,
                    (maxOf(backgroundLuminance, foregroundLuminance) + 0.05f) /
                        (minOf(backgroundLuminance, foregroundLuminance) + 0.05f),
                )
            }
        }
        assertTrue(
            "Night Ink settings typography text must remain legible against its actual sheet surface",
            strongestContrast >= 4.5f,
        )
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun quickSettingsContainEveryControlAndFullSettingsScrollWithoutLosingValues() {
        val preferences = mutableStateOf(
            PortableReaderPreferences(
                flow = "paged",
                fontScale = 1.0,
                lineHeight = 1.5,
                horizontalMargin = 24.0,
                paragraphSpacing = 12.0,
                theme = "paper",
            ),
        )
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "settings-scroll",
            contentId = "chapter-1",
            revision = null,
            title = "设置滚动验证",
            blocks = listOf(ReaderBlock.Paragraph("paragraph-1", paragraph(1))),
        )
        val chapter = SourceChapter("chapter-1", document.title, "https://example.test/chapter-1")

        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(411.43f.dp, 914.29f.dp)) then
                    DeviceConfigurationOverride.FontScale(1f),
            ) {
                val environment = standardEnvironment()
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        ReaderSurface(document = document,
                        auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                        restoredLocator = null,
                        onLocatorChanged = { _, _ -> },
                        chapters = listOf(chapter),
                        currentChapterId = chapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                        hasFlowOverride = false,
                        onFlowOverrideChanged = {},
                        onSelectChapter = {},
                        onNavigateUp = {},
                        preferences = preferences.value,
                        onPreferencesChanged = { preferences.value = it },)
                    }
                }
            }
        }

        composeRule.onNodeWithTag("reader-bottom-action-设置").performClick()
        composeRule.waitForIdle()
        val quickScroll = composeRule.onNodeWithTag("reader-quick-settings-scroll")
        quickScroll.assertIsDisplayed()
        val quickBounds = quickScroll.fetchSemanticsNode().boundsInRoot
        val quickSliderTags = listOf(
            "reader-typography-font-size-slider",
            "reader-typography-line-spacing-slider",
            "reader-typography-margin-slider",
            "reader-typography-paragraph-spacing-slider",
        )
        quickSliderTags.forEach { tag ->
            val slider = composeRule.onNodeWithTag(tag)
            slider.assertIsDisplayed()
            val bounds = slider.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$tag must remain entirely within the quick-settings viewport",
                bounds.left >= quickBounds.left && bounds.top >= quickBounds.top &&
                    bounds.right <= quickBounds.right && bounds.bottom <= quickBounds.bottom,
            )
        }
        listOf("18sp", "1.5", "24dp", "12dp").forEach { value ->
            composeRule.onNodeWithText(value).assertIsDisplayed()
        }
        listOf(
            "reader-quick-lock-portrait",
            "reader-quick-reading-info",
            "reader-quick-immersive",
            "reader-quick-flow",
        ).forEach { tag -> composeRule.onNodeWithTag(tag).assertIsDisplayed() }

        val paragraphSlider = composeRule.onNodeWithTag("reader-typography-paragraph-spacing-slider")
        val paragraphSliderBounds = paragraphSlider.fetchSemanticsNode().boundsInRoot
        val paragraphValueBounds = composeRule.onNodeWithText("12dp").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Paragraph spacing control and value must be entirely reachable in the quick sheet",
            paragraphSliderBounds.left >= quickBounds.left && paragraphSliderBounds.top >= quickBounds.top &&
                paragraphSliderBounds.right <= quickBounds.right && paragraphSliderBounds.bottom <= quickBounds.bottom &&
                paragraphValueBounds.left >= quickBounds.left && paragraphValueBounds.top >= quickBounds.top &&
                paragraphValueBounds.right <= quickBounds.right && paragraphValueBounds.bottom <= quickBounds.bottom,
        )
        paragraphSlider.performSemanticsAction(SemanticsActions.SetProgress) { it(28f) }
        composeRule.runOnIdle {
            assertEquals(28.0, preferences.value.paragraphSpacing ?: Double.NaN, 0.0)
        }

        composeRule.onNodeWithText("全部设置").performClick()
        composeRule.waitForIdle()
        val fullScroll = composeRule.onNodeWithTag("reader-full-settings-scroll")
        fullScroll.assertIsDisplayed()
        val fullBounds = fullScroll.fetchSemanticsNode().boundsInRoot
        val typography = composeRule.onNodeWithTag("reader-full-settings-typography")
        assertTrue(
            "Expanded typography must begin inside the full-settings viewport",
            typography.fetchSemanticsNode().boundsInRoot.top >= fullBounds.top,
        )
        fullScroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        composeRule.waitForIdle()
        assertTrue(
            "Expanded typography must scroll completely above the viewport with the rest of settings",
            typography.fetchSemanticsNode().boundsInRoot.bottom <= fullBounds.top,
        )
        val lockPortrait = composeRule.onNodeWithText("锁定竖屏")
        lockPortrait.assertIsDisplayed()
        lockPortrait.performClick()
        composeRule.runOnIdle {
            assertTrue(preferences.value.lockPortrait == true)
            assertEquals(28.0, preferences.value.paragraphSpacing ?: Double.NaN, 0.0)
        }

        pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-quick-settings-scroll").assertIsDisplayed()
        composeRule.onNodeWithText("28dp").assertIsDisplayed()
        pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-settings-sheet").assertDoesNotExist()

        composeRule.onNodeWithTag("reader-bottom-action-设置").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-quick-settings-scroll").assertIsDisplayed()
        composeRule.onNodeWithText("28dp").assertIsDisplayed()
    }

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
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    chapters = listOf(chapter),
                    currentChapterId = chapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectChapter = {},
                    onNavigateUp = {},
                    preferences = PortableReaderPreferences(
                        flow = "paged",
                        fontScale = 1.0,
                        lineHeight = 1.5,
                        theme = "paper",
                    ),)
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
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    chapters = listOf(chapter),
                    currentChapterId = chapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectChapter = {},
                    onNavigateUp = {},
                    preferences = PortableReaderPreferences(
                        flow = "paged",
                        fontScale = 1.0,
                        lineHeight = 1.5,
                        theme = "paper",
                    ),)
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
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    chapters = listOf(chapter),
                    currentChapterId = chapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
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
                    onRetryImage = { retryCount.incrementAndGet() },)
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
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    chapters = listOf(chapter),
                    currentChapterId = chapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectChapter = {},
                    onNavigateUp = {},
                    preferences = PortableReaderPreferences(flow = "scroll"),
                    imageStates = mapOf(
                        image.blockId to CoverUiState.Failed(
                            CoverFailureReason.NETWORK,
                            FallbackSpec(document.title, null),
                        ),
                    ),)
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
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    chapters = listOf(chapter),
                    currentChapterId = chapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectChapter = {},
                    onNavigateUp = {},
                    preferences = PortableReaderPreferences(flow = "scroll"),)
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
    fun bookmarksTabSelectsDistinctLocatorsWithinTheSameChapter() {
        val chapter = SourceChapter("chapter-bookmarks", "书签章节", "https://example.test/chapter-bookmarks")
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "locator-bookmarks",
            contentId = chapter.chapterId,
            revision = null,
            title = chapter.title,
            blocks = listOf(ReaderBlock.Paragraph("first", paragraph(1))),
        )
        val bookmarkDocument = DocumentIdentity(document.sourceId, document.remoteBookId, document.contentId)
        val firstBookmark = ReaderLocator(
            document = bookmarkDocument,
            blockId = "first",
            characterOffset = 0,
            capturedAt = Instant.EPOCH,
        )
        val laterBookmark = ReaderLocator(
            document = bookmarkDocument,
            blockId = "first",
            characterOffset = 12,
            capturedAt = Instant.EPOCH,
        )
        val selected = mutableListOf<ReaderLocator>()
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(
                        document = document,
                        auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                        restoredLocator = null,
                        onLocatorChanged = { _, _ -> },
                        chapters = listOf(chapter),
                        currentChapterId = chapter.chapterId,
                        bookmarks = listOf(firstBookmark, laterBookmark),
                        onToggleBookmark = {},
                        onRemoveBookmark = {},
                        onSelectBookmark = { selected += it },
                        restorationGeneration = 0L,
                        requestedFlow = "scroll",
                        hasFlowOverride = false,
                        onFlowOverrideChanged = {},
                        onSelectChapter = {},
                        onNavigateUp = {},
                        preferences = PortableReaderPreferences(flow = "scroll"),
                    )
                }
            }
        }

        fun openBookmarks() {
            composeRule.onNodeWithTag("reader-bottom-action-目录").performClick()
            composeRule.onNodeWithText("书签").performClick()
            composeRule.onNodeWithTag("reader-bookmark-list").assertIsDisplayed()
        }

        openBookmarks()
        composeRule.onNodeWithText("first · 第 1 字").assertIsDisplayed()
        composeRule.onNodeWithText("first · 第 13 字").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-bookmark-row-${firstBookmark.bookmarkPositionKey()}").performClick()
        composeRule.runOnIdle { assertEquals(listOf(firstBookmark), selected) }
        openBookmarks()
        composeRule.onNodeWithTag("reader-bookmark-row-${laterBookmark.bookmarkPositionKey()}").performClick()
        composeRule.runOnIdle { assertEquals(listOf(firstBookmark, laterBookmark), selected) }
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
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { locator, _ ->
                        committedLocator.set(locator)
                        commitCount.incrementAndGet()
                    },
                    chapters = listOf(chapter),
                    currentChapterId = chapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectChapter = {},
                    onNavigateUp = {},
                    preferences = PortableReaderPreferences(flow = "scroll"),)
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
    fun cancellingContinuousSeekRestoresTheMountedViewportWithoutWritingProgress() {
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "cancel-scroll-preview",
            contentId = "chapter-1",
            revision = null,
            title = "取消滚动预览",
            blocks = (1..100).map { ReaderBlock.Paragraph("paragraph-$it", paragraph(it)) },
        )
        val chapter = SourceChapter(document.contentId, document.title, "https://example.test/cancel-preview")
        val commits = AtomicInteger()
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> commits.incrementAndGet() },
                    chapters = listOf(chapter),
                    currentChapterId = chapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectChapter = {},
                    onNavigateUp = {},
                    preferences = PortableReaderPreferences(flow = "scroll"),)
                }
            }
        }
        val body = composeRule.onNodeWithTag("reader-document-scroll")
        body.performScrollToIndex(17)
        body.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 37f) }
        composeRule.waitForIdle()
        fun viewport() = body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val openingViewport = viewport()
        val openingParagraphTop = composeRule.onNodeWithText(paragraph(19)).fetchSemanticsNode().boundsInRoot.top
        commits.set(0)
        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        slider.performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveTo(Offset(width * 0.8f, centerY))
        }
        composeRule.waitUntil(5_000) { viewport() > openingViewport + 10f }
        assertEquals(0, commits.get())
        pressBack()
        slider.performTouchInput { cancel() }
        composeRule.waitForIdle()
        assertEquals(openingViewport, viewport(), 0f)
        assertEquals(openingParagraphTop, composeRule.onNodeWithText(paragraph(19)).fetchSemanticsNode().boundsInRoot.top, 0.5f)
        assertEquals(0, commits.get())
    }

    @Test
    fun advancingFromChapterEndCompletesOnlyTheCurrentChapter() {
        val current = SourceChapter("chapter-2", "第二章", "https://example.test/chapter-2")
        val next = SourceChapter("chapter-3", "第三章", "https://example.test/chapter-3")
        val completed = mutableListOf<String>()
        val selected = mutableListOf<String>()
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "non-sequential",
            contentId = current.chapterId,
            revision = null,
            title = current.title,
            blocks = listOf(ReaderBlock.Paragraph("only-block", "这一章完整显示在当前页面。")),
        )
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    chapters = listOf(
                        SourceChapter("chapter-1", "第一章", "https://example.test/chapter-1"),
                        current,
                        next,
                    ),
                    currentChapterId = current.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectChapter = { selected += it.chapterId },
                    onNavigateUp = {},
                    onChapterCompleted = { completed += it },
                    preferences = PortableReaderPreferences(flow = "paged"),)
                }
            }
        }

        composeRule.onNodeWithText("下一章").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("chapter-2"), completed)
            assertEquals(listOf("chapter-3"), selected)
        }
    }

    @Test
    fun advancingPastFinalChapterReportsItsExactIdOnce() {
        val finalChapter = SourceChapter("chapter-final", "最终章", "https://example.test/final")
        val completed = mutableListOf<String>()
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "final-seam",
            contentId = finalChapter.chapterId,
            revision = null,
            title = finalChapter.title,
            blocks = listOf(ReaderBlock.Paragraph("only-block", "最终章完整显示。")),
        )
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    chapters = listOf(finalChapter),
                    currentChapterId = finalChapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectChapter = {},
                    onNavigateUp = {},
                    onChapterCompleted = { completed += it },
                    preferences = PortableReaderPreferences(flow = "paged"),)
                }
            }
        }

        composeRule.onNodeWithText("最终章完整显示。").assertIsDisplayed()
        val content = composeRule.onNodeWithTag("reader-content-surface")
        content.performTouchInput { click(Offset(width * 0.9f, center.y)) }
        composeRule.runOnIdle { assertEquals(listOf(finalChapter.chapterId), completed) }
        content.performTouchInput { click(Offset(width * 0.9f, center.y)) }

        composeRule.runOnIdle { assertEquals(listOf(finalChapter.chapterId), completed) }
    }

    @Test
    fun scroll_bottom_completes_current_chapter_even_when_first_visible_block_is_earlier() {
        val current = SourceChapter("chapter-2", "第二章", "https://example.test/chapter-2")
        val next = SourceChapter("chapter-3", "第三章", "https://example.test/chapter-3")
        val completed = mutableListOf<String>()
        val selected = mutableListOf<String>()
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "scroll-end",
            contentId = current.chapterId,
            revision = null,
            title = current.title,
            blocks = (1..20).map { index ->
                ReaderBlock.Paragraph("block-$index", "第 $index 段。" + "正文".repeat(80))
            },
        )
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    chapters = listOf(current, next),
                    currentChapterId = current.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectChapter = { selected += it.chapterId },
                    onNavigateUp = {},
                    onChapterCompleted = { completed += it },
                    preferences = PortableReaderPreferences(flow = "scroll"),)
                }
            }
        }

        composeRule.onNodeWithTag("reader-document-scroll").performScrollToIndex(document.blocks.lastIndex)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("下一章").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("chapter-2"), completed)
            assertEquals(listOf("chapter-3"), selected)
        }
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

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun requestedDualFallsBackNarrowAndRestoresWideWithoutMovingSemanticPosition() {
        val blocks = (1..42).map { index ->
            ReaderBlock.Paragraph("paragraph-$index", paragraph(index))
        }
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "adaptive-dual",
            contentId = "chapter-1",
            revision = null,
            title = "双页自适应验证",
            blocks = blocks,
        )
        val chapter = SourceChapter("chapter-1", document.title, "https://example.test/chapter-1")
        val forcedSize = mutableStateOf(DpSize(420.dp, 760.dp))
        val commits = AtomicInteger(0)
        val restored = ReaderLocator(
            document = DocumentIdentity(document.sourceId, document.remoteBookId, document.contentId),
            blockId = "paragraph-20",
            characterOffset = 0,
            capturedAt = Instant.EPOCH,
        )

        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(forcedSize.value)) {
                val environment = standardEnvironment()
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        ReaderSurface(document = document,
                        auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                        restoredLocator = restored,
                        onLocatorChanged = { _, _ -> commits.incrementAndGet() },
                        chapters = listOf(chapter),
                        currentChapterId = chapter.chapterId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "dual",
                        hasFlowOverride = true,
                        onFlowOverrideChanged = {},
                        onSelectChapter = {},
                        onNavigateUp = {},
                        preferences = PortableReaderPreferences(flow = "dual", fontScale = 1.0, lineHeight = 1.5, theme = "paper"),)
                    }
                }
            }
        }

        composeRule.onNodeWithText(paragraph(20)).assertIsDisplayed()
        assertEquals(1, composeRule.onAllNodesWithTag("reader-page-column-0").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("reader-page-column-1").fetchSemanticsNodes().size)

        forcedSize.value = DpSize(840.dp, 760.dp)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(paragraph(20)).assertIsDisplayed()
        assertEquals(1, composeRule.onAllNodesWithTag("reader-page-column-0").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithTag("reader-page-column-1").fetchSemanticsNodes().size)

        forcedSize.value = DpSize(420.dp, 760.dp)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(paragraph(20)).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("reader-page-column-1").fetchSemanticsNodes().size)
        assertEquals(0, commits.get())
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun sameDocumentLocatorAppliesOnlyAfterRestorationGenerationChanges() {
        val blocks = (1..42).map { index ->
            ReaderBlock.Paragraph("paragraph-$index", paragraph(index))
        }
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "restoration-generation",
            contentId = "chapter-1",
            revision = null,
            title = "恢复代际验证",
            blocks = blocks,
        )
        val chapter = SourceChapter(document.contentId, document.title, "https://example.test/restoration-generation")
        fun locator(index: Int) = ReaderLocator(
            document = DocumentIdentity(document.sourceId, document.remoteBookId, document.contentId),
            blockId = "paragraph-$index",
            characterOffset = 0,
            capturedAt = Instant.EPOCH,
        )

        val restoredLocator = mutableStateOf(locator(5))
        val restorationGeneration = mutableStateOf(0L)
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(420.dp, 760.dp))) {
                val environment = standardEnvironment()
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        ReaderSurface(document = document,
                        auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                        restoredLocator = restoredLocator.value,
                        onLocatorChanged = { _, _ -> },
                        chapters = listOf(chapter),
                        currentChapterId = chapter.chapterId,
                        bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = restorationGeneration.value, requestedFlow = "paged",
                        hasFlowOverride = false,
                        onFlowOverrideChanged = {},
                        onSelectChapter = {},
                        onNavigateUp = {},
                        preferences = PortableReaderPreferences(flow = "paged"),)
                    }
                }
            }
        }

        composeRule.onNodeWithText(paragraph(5)).assertIsDisplayed()
        composeRule.runOnIdle { restoredLocator.value = locator(20) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(paragraph(5)).assertIsDisplayed()
        composeRule.onNodeWithText(paragraph(20)).assertDoesNotExist()

        composeRule.runOnIdle { restorationGeneration.value += 1L }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(paragraph(20)).assertIsDisplayed()
        composeRule.onNodeWithText(paragraph(5)).assertDoesNotExist()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun scrollBookmarksRestoreWithinParagraphsAcrossGenerationsAndReflowWithoutWritingProgress() {
        val text = (1..160).joinToString("\n") { "𠮷第${it}行：书签位置不受文字重排影响。" }
        val document = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "scroll-semantic-bookmarks",
            contentId = "chapter-1",
            revision = null,
            title = "滚动语义书签",
            blocks = listOf(ReaderBlock.Paragraph("long-paragraph", text), ReaderBlock.Paragraph("ending", "章末")),
        )
        val chapter = SourceChapter(document.contentId, document.title, "https://example.test/scroll-bookmarks")
        val otherChapter = SourceChapter("chapter-2", "另一章", "https://example.test/scroll-bookmarks-2")
        val otherDocument = document.copy(contentId = otherChapter.chapterId, title = otherChapter.title)
        val currentDocument = mutableStateOf(document)
        val requestedFlow = mutableStateOf("paged")
        fun locator(line: Int) = ReaderLocator(
            document = DocumentIdentity(document.sourceId, document.remoteBookId, document.contentId),
            blockId = "long-paragraph",
            characterOffset = text.codePointCount(0, text.indexOf("𠮷第${line}行")) + 3,
            capturedAt = Instant.EPOCH,
        )
        val first = locator(40)
        val second = locator(90)
        val crossChapter = locator(100).copy(document = first.document.copy(contentId = otherChapter.chapterId))
        val restored = mutableStateOf(first)
        val generation = mutableStateOf(0L)
        val preferences = mutableStateOf(PortableReaderPreferences(flow = "paged"))
        val writes = AtomicInteger()
        val committed = AtomicReference<ReaderLocator>()
        val captured = AtomicReference<ReaderLocator>()
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(420.dp, 760.dp))) {
                val environment = standardEnvironment()
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        ReaderSurface(
                            document = currentDocument.value,
                            auxiliarySheetState = rememberReaderAuxiliarySheetState(document.sourceId, document.remoteBookId),
                            restoredLocator = restored.value,
                            restorationGeneration = generation.value,
                            onLocatorChanged = { value, _ -> committed.set(value); writes.incrementAndGet() },
                            chapters = listOf(chapter, otherChapter),
                            currentChapterId = currentDocument.value.contentId,
                            bookmarks = listOf(first, second, crossChapter),
                            onToggleBookmark = captured::set,
                            onRemoveBookmark = {},
                            onSelectBookmark = { target ->
                                currentDocument.value = if (target.document.contentId == otherChapter.chapterId) otherDocument else document
                                restored.value = target
                                generation.value += 1L
                            },
                            requestedFlow = requestedFlow.value,
                            hasFlowOverride = false,
                            onFlowOverrideChanged = {},
                            onSelectChapter = {},
                            onNavigateUp = {},
                            preferences = preferences.value,
                        )
                    }
                }
            }
        }
        fun assertTargetVisible(target: ReaderLocator) {
            composeRule.waitForIdle()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            val paragraph = composeRule.onNodeWithText(text, useUnmergedTree = true)
            paragraph.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val line = layout.getLineForOffset(text.offsetByCodePoints(0, requireNotNull(target.characterOffset)))
            val top = paragraph.fetchSemanticsNode().positionInRoot.y + layout.getLineTop(line)
            val bottom = paragraph.fetchSemanticsNode().positionInRoot.y + layout.getLineBottom(line)
            val viewport = composeRule.onNodeWithTag("reader-document-scroll").fetchSemanticsNode().boundsInRoot
            assertTrue("Restored line is above the viewport: $top < ${viewport.top}", top >= viewport.top - 1f)
            assertTrue("Restored line is below the viewport: $bottom > ${viewport.bottom}", bottom <= viewport.bottom + 1f)
        }
        composeRule.onNodeWithText("𠮷第40行", substring = true).assertIsDisplayed()
        composeRule.runOnIdle {
            requestedFlow.value = "scroll"
            preferences.value = preferences.value.copy(flow = "scroll")
        }
        assertTargetVisible(first)
        composeRule.onNodeWithTag("reader-bottom-action-目录").performClick()
        composeRule.onNodeWithText("书签").performClick()
        composeRule.onNodeWithTag("reader-bookmark-row-${second.bookmarkPositionKey()}").performClick()
        assertTargetVisible(second)
        composeRule.runOnIdle { preferences.value = preferences.value.copy(fontScale = 1.5) }
        assertTargetVisible(second)
        composeRule.onNodeWithTag("reader-bottom-action-目录").performClick()
        composeRule.onNodeWithText("书签").performClick()
        composeRule.onNodeWithTag("reader-bookmark-row-${crossChapter.bookmarkPositionKey()}").performClick()
        assertTargetVisible(crossChapter)
        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        val scrollBody = composeRule.onNodeWithTag("reader-document-scroll")
        fun scrollPosition() = scrollBody.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val openingPosition = scrollPosition()
        slider.performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveTo(Offset(width * 0.9f, centerY))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { scrollPosition() > openingPosition + 1f }
        assertEquals(0, writes.get())
        composeRule.runOnIdle { preferences.value = preferences.value.copy(fontScale = 1.25) }
        slider.performTouchInput { up() }
        assertTargetVisible(crossChapter)
        assertEquals(0, writes.get())

        val body = composeRule.onNodeWithTag("reader-document-scroll")
        body.performTouchInput { swipeUp(startY = height * 0.65f, endY = height * 0.25f) }
        composeRule.waitUntil(timeoutMillis = 5_000) { committed.get() != null }
        val settled = requireNotNull(committed.get())
        assertEquals("long-paragraph", settled.blockId)
        assertEquals(otherChapter.chapterId, settled.document.contentId)
        assertTrue(requireNotNull(settled.characterOffset) > requireNotNull(crossChapter.characterOffset))
        composeRule.onNodeWithContentDescription("添加书签").performClick()
        assertEquals(settled.bookmarkPositionKey(), captured.get().bookmarkPositionKey())
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun mountedBookmarkSelectionRestoresSameAndCrossChapterPositionsWithoutProgressWrites() {
        val firstChapter = SourceChapter("chapter-1", "第一章", "https://example.test/chapter-1")
        val secondChapter = SourceChapter("chapter-2", "第二章", "https://example.test/chapter-2")
        fun document(chapter: SourceChapter, prefix: String) = ReaderDocument(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "mounted-bookmarks",
            contentId = chapter.chapterId,
            revision = "revision-1",
            title = chapter.title,
            blocks = (1..42).map { index ->
                ReaderBlock.Paragraph("$prefix-$index", "$prefix ${paragraph(index)}")
            },
        )
        val firstDocument = document(firstChapter, "第一章")
        val secondDocument = document(secondChapter, "第二章")
        fun locator(document: ReaderDocument, blockId: String): ReaderLocator {
            val block = document.blocks.single { it.blockId == blockId } as ReaderBlock.Paragraph
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(block.text.encodeToByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
            return ReaderLocator(
                document = DocumentIdentity(
                    sourceId = document.sourceId,
                    remoteBookId = document.remoteBookId,
                    contentId = document.contentId,
                    revision = document.revision,
                ),
                blockId = blockId,
                textAnchorDigest = digest,
                characterOffset = 0,
                capturedAt = Instant.EPOCH,
            )
        }
        val sameChapterBookmark = locator(firstDocument, "第一章-20")
        val crossChapterBookmark = locator(secondDocument, "第二章-21")
        val activeDocument = mutableStateOf(firstDocument)
        val currentChapter = mutableStateOf(firstChapter)
        val restoredLocator = mutableStateOf<ReaderLocator?>(null)
        val restorationGeneration = mutableStateOf(0L)
        val preferences = mutableStateOf(PortableReaderPreferences(flow = "paged", fontScale = 1.0, lineHeight = 1.5))
        val progressWrites = AtomicInteger()
        val environment = standardEnvironment()
        val readerVisible = mutableStateOf(true)
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(420.dp, 760.dp))) {
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        val auxiliarySheetState = rememberReaderAuxiliarySheetState(firstDocument.sourceId, firstDocument.remoteBookId)
                        if (readerVisible.value) {
                            ReaderSurface(
                                document = activeDocument.value,
                                auxiliarySheetState = auxiliarySheetState,
                                restoredLocator = restoredLocator.value,
                                restorationGeneration = restorationGeneration.value,
                                onLocatorChanged = { _, _ -> progressWrites.incrementAndGet() },
                                chapters = listOf(firstChapter, secondChapter),
                                currentChapterId = currentChapter.value.chapterId,
                                bookmarks = listOf(sameChapterBookmark, crossChapterBookmark),
                                onToggleBookmark = {},
                                onRemoveBookmark = {},
                                onSelectBookmark = { bookmark ->
                                    if (bookmark.document.contentId == secondChapter.chapterId) {
                                        activeDocument.value = secondDocument
                                        currentChapter.value = secondChapter
                                    }
                                    restoredLocator.value = bookmark
                                    restorationGeneration.value += 1L
                                },
                                requestedFlow = "paged",
                                hasFlowOverride = false,
                                onFlowOverrideChanged = {},
                                onSelectChapter = {},
                                onNavigateUp = {},
                                preferences = preferences.value,
                                onPreferencesChanged = { preferences.value = it },
                            )
                        }
                    }
                }
            }
        }

        fun openBookmarks() {
            composeRule.onNodeWithTag("reader-bottom-action-目录").performClick()
            composeRule.onNodeWithText("书签").performClick()
        }

        openBookmarks()
        composeRule.onNodeWithTag("reader-bookmark-row-${sameChapterBookmark.bookmarkPositionKey()}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第一章 ${paragraph(20)}").assertIsDisplayed()
        assertEquals(0, progressWrites.get())

        preferences.value = preferences.value.copy(fontScale = 1.35, lineHeight = 1.9)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第一章 ${paragraph(20)}").assertIsDisplayed()
        assertEquals(0, progressWrites.get())

        openBookmarks()
        composeRule.onNodeWithTag("reader-bookmark-row-${crossChapterBookmark.bookmarkPositionKey()}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第二章 ${paragraph(21)}").assertIsDisplayed()
        assertEquals(0, progressWrites.get())

        readerVisible.value = false
        composeRule.waitForIdle()
        readerVisible.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第二章 ${paragraph(21)}").assertIsDisplayed()
        assertEquals(0, progressWrites.get())
        composeRule.onNodeWithTag("reader-bottom-action-目录").performClick()
        composeRule.onNodeWithTag("reader-bookmark-row-${crossChapterBookmark.bookmarkPositionKey()}").assertIsDisplayed()
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
