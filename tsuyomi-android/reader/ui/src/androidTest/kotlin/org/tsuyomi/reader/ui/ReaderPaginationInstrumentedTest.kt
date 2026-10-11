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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
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
import org.tsuyomi.shared.sourcecontract.ReaderNavigationTarget
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import org.tsuyomi.shared.model.BookIdentity

class ReaderPaginationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun persistedTypographyUpdatesAndResetReachTheMountedReader() {
        val initial = PortableReaderPreferences(flow = "paged", fontScale = 1.0, lineHeight = 1.5, theme = "paper")
        val preferences = mutableStateOf(initial)
        val commits = AtomicInteger()
        val document = ReaderDocument(
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "live-preferences"), "chapter-1", null),
            title = "排版与主题",
            blocks = (1..12).map { ReaderBlock.Paragraph("paragraph-$it", paragraph(it)) },
        )
        val chapter = ReaderNavigationTarget("chapter-1", document.title)
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> commits.incrementAndGet() },
                    navigationTargets = listOf(chapter),
                    currentContentId = chapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = {},
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
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
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "night-settings-contrast"), "chapter-1", null),
            title = "夜间设置对比度",
            blocks = listOf(ReaderBlock.Paragraph("paragraph-1", paragraph(1))),
        )
        val chapter = ReaderNavigationTarget("chapter-1", document.title)
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    navigationTargets = listOf(chapter),
                    currentContentId = chapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = {},
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
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
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "settings-scroll"), "chapter-1", null),
            title = "设置滚动验证",
            blocks = listOf(ReaderBlock.Paragraph("paragraph-1", paragraph(1))),
        )
        val chapter = ReaderNavigationTarget("chapter-1", document.title)

        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(411.43f.dp, 914.29f.dp)) then
                    DeviceConfigurationOverride.FontScale(1f),
            ) {
                val environment = standardEnvironment()
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        ReaderSurface(document = document,
                        auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                        restoredLocator = null,
                        onLocatorChanged = { _, _ -> },
                        navigationTargets = listOf(chapter),
                        currentContentId = chapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                        hasFlowOverride = false,
                        onFlowOverrideChanged = {},
                        onSelectContent = {},
                        onNavigateUp = {},
                        onNavigateLink = {},
                        onReturnToOrigin = {},
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
    fun unfinishedReaderColorDraftAndSelectionSurviveBothProfileChanges() {
        val environment = mutableStateOf(eInkEnvironment())
        val actions = mutableListOf<ReaderSettingsAction>()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment.value) {
                TsuyomiTheme(environment.value) {
                    ReaderSettingsSheet(
                        settingsIdentity = "draft-book",
                        state = ReaderSettingsUiState(foregroundColor = "#123456"),
                        effectiveFlow = ReaderFlow.PAGED,
                        dualPageEligible = false,
                        hasFlowOverride = false,
                        onAction = { actions += it },
                        onFollowGlobalFlow = {},
                        onDismiss = {},
                    )
                }
            }
        }
        fun showEinkColorField() {
            repeat(50) {
                if (runCatching { composeRule.onNodeWithTag("reader-foreground-color").assertIsDisplayed() }.isSuccess) return
                composeRule.onNode(hasText("下一页") and hasAnyAncestor(hasTestTag("reader-eink-setting-controls")))
                    .assertIsEnabled().performClick()
            }
            composeRule.onNodeWithTag("reader-foreground-color").assertIsDisplayed()
        }
        showEinkColorField()
        composeRule.onNodeWithTag("reader-foreground-color").performTextReplacement("#12")
        composeRule.onNodeWithTag("reader-foreground-color")
            .performSemanticsAction(SemanticsActions.SetSelection) { it(1, 1, false) }
        composeRule.runOnIdle { environment.value = standardEnvironment() }
        composeRule.onNodeWithText("全部设置").performClick()
        composeRule.onNodeWithTag("reader-full-settings-scroll").performScrollToNode(hasTestTag("reader-foreground-color"))
        val standardField = composeRule.onNodeWithTag("reader-foreground-color").fetchSemanticsNode().config
        assertEquals("#12", standardField[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(1), standardField[SemanticsProperties.TextSelectionRange])
        composeRule.runOnIdle { environment.value = eInkEnvironment() }
        showEinkColorField()
        val einkField = composeRule.onNodeWithTag("reader-foreground-color").fetchSemanticsNode().config
        assertEquals("#12", einkField[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(1), einkField[SemanticsProperties.TextSelectionRange])
        assertEquals(emptyList<ReaderSettingsAction>(), actions)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun einkReaderKeepsPageEndpointsAndRestoresRequestedStandardFlow() {
        val environment = mutableStateOf(eInkEnvironment())
        val preferences = mutableStateOf(PortableReaderPreferences(flow = "scroll", fontScale = 1.0))
        val document = ReaderDocument(
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "eink-page-controls"), "chapter-1", null),
            title = "墨水屏分页控制",
            blocks = (1..60).map { ReaderBlock.Paragraph("paragraph-$it", paragraph(it)) },
        )
        val chapter = ReaderNavigationTarget("chapter-1", document.title)
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(420.dp, 760.dp))) {
                DisplayEnvironmentProvider(environment.value) {
                    TsuyomiTheme(environment.value) {
                        ReaderSurface(
                            document = document,
                            auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                            restoredLocator = null,
                            restorationGeneration = 0L,
                            onLocatorChanged = { _, _ -> },
                            navigationTargets = listOf(chapter),
                            currentContentId = chapter.contentId,
                            bookmarks = emptyList(),
                            onToggleBookmark = {},
                            onRemoveBookmark = {},
                            onSelectBookmark = {},
                            requestedFlow = "scroll",
                            hasFlowOverride = true,
                            onFlowOverrideChanged = {},
                            onSelectContent = {},
                            onNavigateLink = {},
                            onNavigateUp = {},
                            onReturnToOrigin = {},
                            preferences = preferences.value,
                            onPreferencesChanged = { preferences.value = it },
                        )
                    }
                }
            }
        }

        val previousPage = composeRule.onNodeWithTag("reader-page-previous")
        val nextPage = composeRule.onNodeWithTag("reader-page-next")
        previousPage.assertIsNotEnabled()
        nextPage.assertIsEnabled()
        val progressSlider = composeRule.onNodeWithTag("reader-chapter-progress-slider").assertIsDisplayed()
        val range = progressSlider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertTrue(range.range.endInclusive > 1f)
        assertEquals(range.range.endInclusive.toInt() - 1, range.steps)

        fun currentPageStatus(): String = composeRule.onNodeWithTag("reader-page-progress")
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
        fun currentPageNumber(): Int = currentPageStatus().substringBefore(' ').substringBefore('/').toInt()
        val pageCount = currentPageStatus().substringBefore(' ').substringAfter('/').toInt()
        var expectedPage = 1
        while (expectedPage < pageCount) {
            nextPage.performClick()
            composeRule.waitForIdle()
            expectedPage++
            assertEquals(expectedPage, currentPageNumber())
            previousPage.assertIsEnabled()
        }
        nextPage.assertIsNotEnabled()
        composeRule.onNodeWithText(paragraph(60)).assertIsDisplayed()

        composeRule.onNodeWithTag("reader-bottom-action-设置").performClick()
        composeRule.onNodeWithTag("reader-eink-project-typography").assertIsDisplayed().performClick()
        val settingsSlider = composeRule.onNodeWithTag("reader-eink-font-size-slider")
        settingsSlider.assertIsDisplayed().performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        val settingsRange = settingsSlider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(12f..32f, settingsRange.range)
        assertEquals(19, settingsRange.steps)
        val navigation = composeRule.onNodeWithTag("reader-eink-settings-navigation").fetchSemanticsNode().boundsInRoot
        val controls = composeRule.onNodeWithTag("reader-eink-project-content").fetchSemanticsNode().boundsInRoot
        val label = composeRule.onNode(hasText("排版") and hasAnyAncestor(hasTestTag("reader-eink-settings-navigation")), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val padding = with(settingsSlider.fetchSemanticsNode().layoutInfo.density) { 24.dp.toPx() }
        assertTrue("Navigation is only a measured label and touch padding", navigation.width <= label.width + padding + 1f)
        assertTrue("The remaining Reader settings lane is wider than navigation", controls.width > navigation.width * 2f)
        assertReaderSettingsButtonSpacing(
            listOf("typography", "page", "navigation", "device").map { "reader-eink-project-$it" },
            org.tsuyomi.core.ui.theme.TsuyomiSpacing.Sm,
        )
        composeRule.runOnIdle { assertTrue((preferences.value.fontScale ?: 1.0) > 1.0) }
        composeRule.onAllNodesWithTag("reader-eink-settings-navigation").assertCountEquals(1)
        composeRule.onAllNodesWithTag("reader-eink-project-navigation").assertCountEquals(1)
        composeRule.onNodeWithTag("reader-eink-project-navigation").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("reader-eink-project-navigation").assertIsSelected()
        composeRule.onNodeWithTag("reader-eink-volume-paging").assertIsDisplayed()
        assertReaderSettingsButtonSpacing(
            listOf("reader-eink-progress", "reader-eink-volume-paging", "reader-eink-immersive"),
            org.tsuyomi.core.ui.theme.TsuyomiSpacing.Md,
        )
        composeRule.onNodeWithTag("reader-eink-project-page").performClick()
        composeRule.onNodeWithTag("reader-eink-option-paged").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-quick-settings-scroll").assertDoesNotExist()

        pressBack()
        composeRule.waitForIdle()
        composeRule.runOnIdle { environment.value = standardEnvironment() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-document-scroll").assertIsDisplayed()
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
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "packing"), "chapter-1", null),
            title = "分页排版验证",
            blocks = blocks,
        )
        val chapter = ReaderNavigationTarget("chapter-1", document.title)
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    navigationTargets = listOf(chapter),
                    currentContentId = chapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = {},
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
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
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "short-fixture"), "chapter-1", null),
            title = "第一章 雾中的灯塔",
            blocks = listOf(
                ReaderBlock.Paragraph("paragraph-1", first),
                ReaderBlock.Paragraph("paragraph-2", second),
            ),
        )
        val chapter = ReaderNavigationTarget("chapter-1", document.title)
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    navigationTargets = listOf(chapter),
                    currentContentId = chapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = {},
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
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
            resource = ReaderResourceRef.RemoteHttps("https://img.example.test/chapter/image.webp"),

            altText = "章节插图",
            width = 900,
            height = 1200,
        )
        val document = ReaderDocument(
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "illustrations"), "chapter-image", null),
            title = "插图章节",
            blocks = listOf(
                ReaderBlock.Paragraph("paragraph-before", before),
                image,
                ReaderBlock.Paragraph("paragraph-after", after),
            ),
        )
        val chapter = ReaderNavigationTarget(document.identity.contentId, document.title)
        val visibleCount = AtomicInteger()
        val retryCount = AtomicInteger()
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    navigationTargets = listOf(chapter),
                    currentContentId = chapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = {},
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
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
        assertTrue(beforeTop < imageTop)

        composeRule.onNodeWithText("重试图片").performClick()
        composeRule.waitUntil { retryCount.get() == 1 }
        composeRule.onNodeWithTag("reader-document-scroll").performScrollToNode(hasText(after))
        composeRule.onNodeWithText(after).assertIsDisplayed()
        val afterTop = composeRule.onNodeWithText(after).fetchSemanticsNode().boundsInRoot.top
        val scrolledImageTop = composeRule.onNodeWithText("章节插图").fetchSemanticsNode().boundsInRoot.top
        assertTrue(scrolledImageTop < afterTop)
    }

    @Test
    fun imageOnlyChapterRendersAsReaderContentInsteadOfAnEmptyDocument() {
        val image = ReaderBlock.Image(
            blockId = "image-only",
            resource = ReaderResourceRef.RemoteHttps("https://img.example.test/chapter/image.webp"),

            altText = "纯插图章节",
            width = null,
            height = null,
        )
        val document = ReaderDocument(
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "illustrations"), "chapter-image-only", null),
            title = "插图页",
            blocks = listOf(image),
        )
        val chapter = ReaderNavigationTarget(document.identity.contentId, document.title)
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    navigationTargets = listOf(chapter),
                    currentContentId = chapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = {},
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
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
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "chrome-toggle"), "chapter-1", null),
            title = "Chrome 双向切换",
            blocks = (1..80).map { index ->
                ReaderBlock.Paragraph("paragraph-$index", paragraph(index))
            },
        )
        val chapter = ReaderNavigationTarget(document.identity.contentId, document.title)
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    navigationTargets = listOf(chapter),
                    currentContentId = chapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = {},
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
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
        val chapter = ReaderNavigationTarget("chapter-bookmarks", "书签章节")
        val document = ReaderDocument(
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "locator-bookmarks"), chapter.contentId, null),
            title = chapter.title,
            blocks = listOf(ReaderBlock.Paragraph("first", paragraph(1))),
        )
        val bookmarkDocument = document.identity
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
                        auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                        restoredLocator = null,
                        onLocatorChanged = { _, _ -> },
                        navigationTargets = listOf(chapter),
                        currentContentId = chapter.contentId,
                        bookmarks = listOf(firstBookmark, laterBookmark),
                        onToggleBookmark = {},
                        onRemoveBookmark = {},
                        onSelectBookmark = { selected += it },
                        restorationGeneration = 0L,
                        requestedFlow = "scroll",
                        hasFlowOverride = false,
                        onFlowOverrideChanged = {},
                        onSelectContent = {},
                        onNavigateUp = {},
                        onNavigateLink = {},
                        onReturnToOrigin = {},
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
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "track-tap"), "chapter-1", null),
            title = "进度条单点跳转",
            blocks = blocks,
        )
        val chapter = ReaderNavigationTarget(document.identity.contentId, document.title)
        val commitCount = AtomicInteger()
        val committedLocator = AtomicReference<org.tsuyomi.shared.locator.ReaderLocator?>()
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { locator, _ ->
                        committedLocator.set(locator)
                        commitCount.incrementAndGet()
                    },
                    navigationTargets = listOf(chapter),
                    currentContentId = chapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = {},
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
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
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "cancel-scroll-preview"), "chapter-1", null),
            title = "取消滚动预览",
            blocks = (1..100).map { ReaderBlock.Paragraph("paragraph-$it", paragraph(it)) },
        )
        val chapter = ReaderNavigationTarget(document.identity.contentId, document.title)
        val commits = AtomicInteger()
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> commits.incrementAndGet() },
                    navigationTargets = listOf(chapter),
                    currentContentId = chapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = {},
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
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
        val current = ReaderNavigationTarget("chapter-2", "第二章")
        val next = ReaderNavigationTarget("chapter-3", "第三章")
        val completed = mutableListOf<String>()
        val selected = mutableListOf<String>()
        val document = ReaderDocument(
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "non-sequential"), current.contentId, null),
            title = current.title,
            blocks = listOf(ReaderBlock.Paragraph("only-block", "这一章完整显示在当前页面。")),
        )
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    navigationTargets = listOf(
                        ReaderNavigationTarget("chapter-1", "第一章"),
                        current,
                        next,
                    ),
                    currentContentId = current.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = { selected += it.contentId },
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
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
        val finalChapter = ReaderNavigationTarget("chapter-final", "最终章")
        val completed = mutableListOf<String>()
        val document = ReaderDocument(
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "final-seam"), finalChapter.contentId, null),
            title = finalChapter.title,
            blocks = listOf(ReaderBlock.Paragraph("only-block", "最终章完整显示。")),
        )
        val environment = standardEnvironment()
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderSurface(document = document,
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    navigationTargets = listOf(finalChapter),
                    currentContentId = finalChapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "paged",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = {},
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
                    onChapterCompleted = { completed += it },
                    preferences = PortableReaderPreferences(flow = "paged"),)
                }
            }
        }

        composeRule.onNodeWithText("最终章完整显示。").assertIsDisplayed()
        val content = composeRule.onNodeWithTag("reader-content-surface")
        content.performTouchInput { click(Offset(width * 0.9f, center.y)) }
        composeRule.runOnIdle { assertEquals(listOf(finalChapter.contentId), completed) }
        content.performTouchInput { click(Offset(width * 0.9f, center.y)) }

        composeRule.runOnIdle { assertEquals(listOf(finalChapter.contentId), completed) }
    }

    @Test
    fun scroll_bottom_completes_current_chapter_even_when_first_visible_block_is_earlier() {
        val current = ReaderNavigationTarget("chapter-2", "第二章")
        val next = ReaderNavigationTarget("chapter-3", "第三章")
        val completed = mutableListOf<String>()
        val selected = mutableListOf<String>()
        val document = ReaderDocument(
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "scroll-end"), current.contentId, null),
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
                    auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                    restoredLocator = null,
                    onLocatorChanged = { _, _ -> },
                    navigationTargets = listOf(current, next),
                    currentContentId = current.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "scroll",
                    hasFlowOverride = false,
                    onFlowOverrideChanged = {},
                    onSelectContent = { selected += it.contentId },
                    onNavigateUp = {},
                    onNavigateLink = {},
                    onReturnToOrigin = {},
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
                        eInk = false,
                        canPreviousPage = false,
                        canNextPage = false,
                        onPreviousPage = {},
                        onNextPage = {},
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
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "adaptive-dual"), "chapter-1", null),
            title = "双页自适应验证",
            blocks = blocks,
        )
        val chapter = ReaderNavigationTarget("chapter-1", document.title)
        val forcedSize = mutableStateOf(DpSize(420.dp, 760.dp))
        val commits = AtomicInteger(0)
        val restored = ReaderLocator(
            document = document.identity,
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
                        auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                        restoredLocator = restored,
                        onLocatorChanged = { _, _ -> commits.incrementAndGet() },
                        navigationTargets = listOf(chapter),
                        currentContentId = chapter.contentId, bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = 0L, requestedFlow = "dual",
                        hasFlowOverride = true,
                        onFlowOverrideChanged = {},
                        onSelectContent = {},
                        onNavigateUp = {},
                        onNavigateLink = {},
                        onReturnToOrigin = {},
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
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "restoration-generation"), "chapter-1", null),
            title = "恢复代际验证",
            blocks = blocks,
        )
        val chapter = ReaderNavigationTarget(document.identity.contentId, document.title)
        fun locator(index: Int) = ReaderLocator(
            document = document.identity,
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
                        auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                        restoredLocator = restoredLocator.value,
                        onLocatorChanged = { _, _ -> },
                        navigationTargets = listOf(chapter),
                        currentContentId = chapter.contentId,
                        bookmarks = emptyList(), onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {}, restorationGeneration = restorationGeneration.value, requestedFlow = "paged",
                        hasFlowOverride = false,
                        onFlowOverrideChanged = {},
                        onSelectContent = {},
                        onNavigateUp = {},
                        onNavigateLink = {},
                        onReturnToOrigin = {},
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
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "scroll-semantic-bookmarks"), "chapter-1", null),
            title = "滚动语义书签",
            blocks = listOf(ReaderBlock.Paragraph("long-paragraph", text), ReaderBlock.Paragraph("ending", "章末")),
        )
        val chapter = ReaderNavigationTarget(document.identity.contentId, document.title)
        val otherChapter = ReaderNavigationTarget("chapter-2", "另一章")
        val otherDocument = document.copy(identity = document.identity.copy(contentId = otherChapter.contentId), title = otherChapter.title)
        val currentDocument = mutableStateOf(document)
        val requestedFlow = mutableStateOf("paged")
        fun locator(line: Int) = ReaderLocator(
            document = document.identity,
            blockId = "long-paragraph",
            characterOffset = text.codePointCount(0, text.indexOf("𠮷第${line}行")) + 3,
            capturedAt = Instant.EPOCH,
        )
        val first = locator(40)
        val second = locator(90)
        val crossChapter = locator(100).copy(document = first.document.copy(contentId = otherChapter.contentId))
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
                            auxiliarySheetState = rememberReaderAuxiliarySheetState(document.identity.book),
                            restoredLocator = restored.value,
                            restorationGeneration = generation.value,
                            onLocatorChanged = { value, _ -> committed.set(value); writes.incrementAndGet() },
                            navigationTargets = listOf(chapter, otherChapter),
                            currentContentId = currentDocument.value.identity.contentId,
                            bookmarks = listOf(first, second, crossChapter),
                            onToggleBookmark = captured::set,
                            onRemoveBookmark = {},
                            onSelectBookmark = { target ->
                                currentDocument.value = if (target.document.contentId == otherChapter.contentId) otherDocument else document
                                restored.value = target
                                generation.value += 1L
                            },
                            requestedFlow = requestedFlow.value,
                            hasFlowOverride = false,
                            onFlowOverrideChanged = {},
                            onSelectContent = {},
                            onNavigateUp = {},
                            onNavigateLink = {},
                            onReturnToOrigin = {},
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
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) { committed.get() != null }
        val settled = requireNotNull(committed.get())
        assertEquals("long-paragraph", settled.blockId)
        assertEquals(otherChapter.contentId, settled.document.contentId)
        assertTrue(requireNotNull(settled.characterOffset) > requireNotNull(crossChapter.characterOffset))
        composeRule.onNodeWithContentDescription("添加书签").performClick()
        assertEquals(settled.bookmarkPositionKey(), captured.get().bookmarkPositionKey())
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun mountedBookmarkSelectionRestoresSameAndCrossChapterPositionsWithoutProgressWrites() {
        val firstChapter = ReaderNavigationTarget("chapter-1", "第一章")
        val secondChapter = ReaderNavigationTarget("chapter-2", "第二章")
        fun document(chapter: ReaderNavigationTarget, prefix: String) = ReaderDocument(
            identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "mounted-bookmarks"), chapter.contentId, "revision-1"),
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
                    book = document.identity.book,
                    contentId = document.identity.contentId,
                    revision = document.identity.revision,
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
                        val auxiliarySheetState = rememberReaderAuxiliarySheetState(firstDocument.identity.book)
                        if (readerVisible.value) {
                            ReaderSurface(
                                document = activeDocument.value,
                                auxiliarySheetState = auxiliarySheetState,
                                restoredLocator = restoredLocator.value,
                                restorationGeneration = restorationGeneration.value,
                                onLocatorChanged = { _, _ -> progressWrites.incrementAndGet() },
                                navigationTargets = listOf(firstChapter, secondChapter),
                                currentContentId = currentChapter.value.contentId,
                                bookmarks = listOf(sameChapterBookmark, crossChapterBookmark),
                                onToggleBookmark = {},
                                onRemoveBookmark = {},
                                onSelectBookmark = { bookmark ->
                                    if (bookmark.document.contentId == secondChapter.contentId) {
                                        activeDocument.value = secondDocument
                                        currentChapter.value = secondChapter
                                    }
                                    restoredLocator.value = bookmark
                                    restorationGeneration.value += 1L
                                },
                                requestedFlow = "paged",
                                hasFlowOverride = false,
                                onFlowOverrideChanged = {},
                                onSelectContent = {},
                                onNavigateUp = {},
                                onNavigateLink = {},
                                onReturnToOrigin = {},
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

    @Test
    fun einkDualSeekUpdatesTheMountedSpreadWithoutCommittingEarly() {
        val document = mutableStateOf(seekDocument("dual-seek"))
        val writes = AtomicInteger()
        renderEinkSeekReader(document, viewport = DpSize(840.dp, 760.dp), onLocatorChanged = { writes.incrementAndGet() })
        composeRule.onNodeWithTag("reader-bottom-action-设置").performClick()
        composeRule.onNodeWithTag("reader-eink-project-page").performClick()
        composeRule.onNodeWithTag("reader-eink-option-dual").performClick()
        pressBack()
        composeRule.onAllNodes(hasTestTag("reader-page-column-1") and hasAnyAncestor(hasTestTag("reader-content-surface")), useUnmergedTree = true)
            .assertCountEquals(1)
        val opening = readerTextWithin("reader-content-surface")
        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        val lastStop = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range.endInclusive
        assertTrue("The fixture includes an interior complete spread", lastStop >= 2f)
        slider.performTouchInput { down(Offset(width * 0.05f, centerY)); moveTo(Offset(width / lastStop, centerY)) }
        composeRule.onAllNodes(hasTestTag("reader-page-column-1") and hasAnyAncestor(hasTestTag("reader-content-surface")), useUnmergedTree = true)
            .assertCountEquals(1)
        val target = readerTextWithin("reader-content-surface")
        assertEquals(0, writes.get())
        assertTrue(target.isNotEmpty() && target != opening)
        slider.performTouchInput { up() }
        composeRule.waitUntil(5_000) { writes.get() == 1 }
        assertEquals(target, readerTextWithin("reader-content-surface"))
        assertEquals(1, writes.get())
    }

    @Test
    fun einkHeldSeekUsesTheMountedBodyAndCommitsTheLastTargetOnce() {
        val document = mutableStateOf(seekDocument("held-seek"))
        val writes = AtomicInteger()
        val lastLocator = AtomicReference<ReaderLocator?>()
        renderEinkSeekReader(document, onLocatorChanged = { locator -> lastLocator.set(locator); writes.incrementAndGet() })
        val openingBody = readerTextWithin("reader-content-surface")
        assertTrue(openingBody.isNotEmpty())
        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        val sliderIdentity = slider.fetchSemanticsNode().id
        val settledBounds = composeRule.onNodeWithTag("reader-content-surface").fetchSemanticsNode().boundsInRoot
        slider.performTouchInput { down(Offset(width * 0.05f, centerY)); moveTo(Offset(width * 0.65f, centerY)) }
        assertTrue(openingBody != readerTextWithin("reader-content-surface"))
        assertEquals(0, writes.get())
        val firstTargetBody = readerTextWithin("reader-content-surface")
        assertEquals("Live scrub must retain the native drag owner", sliderIdentity, slider.fetchSemanticsNode().id)
        slider.performTouchInput { moveTo(Offset(width * 0.87f, centerY)) }
        composeRule.waitForIdle()
        val targetBody = readerTextWithin("reader-content-surface")
        assertTrue(targetBody.isNotEmpty() && targetBody != openingBody)
        assertTrue("The mounted native slider must continue updating during the same drag", targetBody != firstTargetBody)
        assertEquals(0, writes.get())
        assertEquals("Seek chrome must not change the measured reading canvas", settledBounds,
            composeRule.onNodeWithTag("reader-content-surface").fetchSemanticsNode().boundsInRoot)
        slider.performTouchInput { up() }
        composeRule.waitUntil(5_000) { writes.get() == 1 }
        assertEquals(targetBody, readerTextWithin("reader-content-surface"))
        assertEquals(document.value.identity, lastLocator.get()?.document)
        assertEquals(1, writes.get())
        composeRule.onNodeWithTag("reader-bottom-action-设置").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-page-next").assertIsDisplayed()
        assertEquals(sliderIdentity, slider.fetchSemanticsNode().id)
    }



    @Test
    fun einkShortLandscapeSeekUsesTheMountedPageWithoutCommittingEarly() {
        val document = mutableStateOf(seekDocument("landscape-seek"))
        val writes = AtomicInteger()
        renderEinkSeekReader(document, viewport = DpSize(760.dp, 420.dp), onLocatorChanged = { writes.incrementAndGet() })
        val opening = readerTextWithin("reader-content-surface")
        val settledBounds = composeRule.onNodeWithTag("reader-content-surface").fetchSemanticsNode().boundsInRoot
        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        slider.performTouchInput { down(Offset(width * 0.1f, centerY)); moveTo(Offset(width * 0.8f, centerY)) }
        val target = readerTextWithin("reader-content-surface")
        assertTrue(target.isNotEmpty() && target != opening)
        assertEquals(settledBounds, composeRule.onNodeWithTag("reader-content-surface").fetchSemanticsNode().boundsInRoot)
        assertEquals(0, writes.get())
        slider.performTouchInput { up() }
        composeRule.waitUntil(5_000) { writes.get() == 1 }
        assertEquals(target, readerTextWithin("reader-content-surface"))
        composeRule.onNodeWithTag("reader-bottom-action-设置").assertIsDisplayed()
        assertEquals(1, writes.get())
    }


    @Test
    fun einkPointerCancelAndBackDiscardTheHeldTargetWithoutProgressWrites() {
        val document = mutableStateOf(seekDocument("cancel-seek"))
        val writes = AtomicInteger()
        renderEinkSeekReader(document, onLocatorChanged = { writes.incrementAndGet() })
        val openingBody = readerTextWithin("reader-content-surface")
        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        slider.performTouchInput { down(Offset(width * 0.1f, centerY)); moveTo(Offset(width * 0.75f, centerY)) }
        assertTrue(openingBody != readerTextWithin("reader-content-surface"))
        slider.performTouchInput { cancel() }
        composeRule.waitForIdle()
        assertEquals(openingBody, readerTextWithin("reader-content-surface"))
        assertEquals(0, writes.get())
        slider.performTouchInput { down(Offset(width * 0.1f, centerY)); moveTo(Offset(width * 0.85f, centerY)) }
        assertTrue(openingBody != readerTextWithin("reader-content-surface"))
        pressBack()
        slider.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(openingBody, readerTextWithin("reader-content-surface"))
        assertEquals(0, writes.get())
        composeRule.onNodeWithTag("reader-bottom-action-设置").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-page-previous").assertIsDisplayed()
    }

    @Test
    fun einkReflowRestorationAndDocumentReplacementFenceTheOldReleaseFromTheNewOwner() {
        val document = mutableStateOf(seekDocument("epoch-seek"))
        val preferences = mutableStateOf(PortableReaderPreferences(flow = "paged", fontScale = 1.0))
        val generation = mutableStateOf(0L)
        val writes = AtomicInteger()
        val lastLocator = AtomicReference<ReaderLocator?>()
        renderEinkSeekReader(document, preferences, restorationGeneration = generation,
            onLocatorChanged = { locator -> lastLocator.set(locator); writes.incrementAndGet() })
        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        slider.performTouchInput { down(Offset(width * 0.1f, centerY)); moveTo(Offset(width * 0.8f, centerY)) }
        composeRule.runOnIdle { preferences.value = preferences.value.copy(fontScale = 1.25) }
        slider.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(0, writes.get())
        slider.performTouchInput { down(Offset(width * 0.1f, centerY)); moveTo(Offset(width * 0.8f, centerY)) }
        composeRule.runOnIdle { generation.value += 1 }
        slider.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(0, writes.get())
        slider.performTouchInput { down(Offset(width * 0.1f, centerY)); moveTo(Offset(width * 0.8f, centerY)) }
        composeRule.runOnIdle { document.value = seekDocument("replacement-owner") }
        slider.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(0, writes.get())
        slider.performTouchInput { click(Offset(width * 0.6f, centerY)) }
        composeRule.waitUntil(5_000) { writes.get() == 1 }
        assertEquals(document.value.identity, lastLocator.get()?.document)
        assertEquals(1, writes.get())
    }

    @Test
    fun einkSpreadSliderRetainsEveryStopBeyondTheIntegerPercentRangeAndCancelsNativeFinish() {
        val environment = eInkEnvironment()
        val preview = AtomicInteger(-1)
        val committed = AtomicInteger(-1)
        val finishes = AtomicInteger()
        val cancels = AtomicInteger()
        val pageCount = mutableStateOf(257)
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    ReaderBottomChrome(
                        chapterIndex = 0, chapterCount = 1, chapterProgress = 0,
                        position = ReaderPosition.fromPageIndex(0, pageCount.value, 2), eInk = true,
                        canPreviousPage = false, canNextPage = pageCount.value > 1, onPreviousPage = {}, onNextPage = {},
                        continuousSeek = false, seekPreview = null, onSeekPreview = { preview.set(it) },
                        onSeekCommit = { committed.set(it); finishes.incrementAndGet() },
                        onSeekCancel = { cancels.incrementAndGet() },
                        onPreviousChapter = {}, onOpenContents = {}, onOpenSettings = {}, onNextChapter = {},
                    )
                }
            }
        }
        val slider = composeRule.onNodeWithTag("reader-chapter-progress-slider")
        val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0f..128f, range.range)
        assertEquals(127, range.steps)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(93f) }
        assertEquals(186, preview.get())
        assertEquals(186, committed.get())
        assertEquals(1, finishes.get())
        slider.performTouchInput { down(Offset(width * 0.1f, centerY)); moveTo(Offset(width * 0.8f, centerY)); cancel() }
        composeRule.waitForIdle()
        assertEquals("Native drag cancellation must be intercepted before its finish callback", 1, finishes.get())
        assertEquals(1, cancels.get())
        composeRule.runOnIdle { pageCount.value = 1 }
        slider.assertDoesNotExist()
        composeRule.onNodeWithTag("reader-chapter-position").assertIsDisplayed()
    }

    private fun seekDocument(bookId: String) = ReaderDocument(
        identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", bookId), "chapter-1", null),
        title = "实测目标页预览",
        blocks = (1..90).map { ReaderBlock.Paragraph("paragraph-$it", paragraph(it)) },
    )

    @OptIn(ExperimentalTestApi::class)
    private fun renderEinkSeekReader(
        document: androidx.compose.runtime.MutableState<ReaderDocument>,
        preferences: androidx.compose.runtime.MutableState<PortableReaderPreferences> = mutableStateOf(PortableReaderPreferences(flow = "paged", fontScale = 1.0)),
        viewport: DpSize = DpSize(420.dp, 760.dp),
        restorationGeneration: androidx.compose.runtime.MutableState<Long> = mutableStateOf(0L),
        onLocatorChanged: (ReaderLocator) -> Unit,
    ) {
        val environment = eInkEnvironment()
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport)) {
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        val current = document.value
                        ReaderSurface(
                            document = current, auxiliarySheetState = rememberReaderAuxiliarySheetState(current.identity.book),
                            restoredLocator = null, restorationGeneration = restorationGeneration.value,
                            onLocatorChanged = { locator, _ -> onLocatorChanged(locator) },
                            navigationTargets = listOf(ReaderNavigationTarget(current.identity.contentId, current.title)),
                            currentContentId = current.identity.contentId, bookmarks = emptyList(),
                            onToggleBookmark = {}, onRemoveBookmark = {}, onSelectBookmark = {},
                            requestedFlow = "paged", hasFlowOverride = false, onFlowOverrideChanged = {},
                            onSelectContent = {}, onNavigateLink = {}, onNavigateUp = {}, onReturnToOrigin = {},
                            preferences = preferences.value,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }


    private fun assertReaderSettingsButtonSpacing(tags: List<String>, spacing: androidx.compose.ui.unit.Dp) {
        val buttons = tags.map { composeRule.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode() }
            .sortedBy { it.boundsInRoot.top }
        val gap = with(composeRule.density) { spacing.roundToPx() }
        buttons.forEach { button ->
            val minimum = with(button.layoutInfo.density) { 48.dp.roundToPx() }
            assertTrue(button.touchBoundsInRoot.width >= minimum && button.touchBoundsInRoot.height >= minimum)
        }
        buttons.zipWithNext().forEach { (previous, next) ->
            assertTrue("Adjacent setting button frames must retain their measured vertical spacing",
                next.boundsInRoot.top - previous.boundsInRoot.bottom >= gap - 1f)
        }
    }


    private fun readerTextWithin(tag: String): List<String> = composeRule.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and hasAnyAncestor(hasTestTag(tag)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

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
    private fun eInkEnvironment() = DisplayEnvironment(
        preferences = DisplayPreferences(
            displayPreference = DisplayPreference.EINK,
            colorSchemePreference = ColorSchemePreference.LIGHT,
        ),
        effectiveProfile = DisplayProfile.EINK,
        decisionReason = DisplayDecisionReason.MANUAL_EINK,
        detectedDeviceLabel = null,
        dynamicColorEligible = false,
        dynamicColorEffective = false,
        effectiveDarkTheme = false,
        motionPolicy = MotionPolicy.STANDARD,
        redrawEpoch = 0,
    )

}
