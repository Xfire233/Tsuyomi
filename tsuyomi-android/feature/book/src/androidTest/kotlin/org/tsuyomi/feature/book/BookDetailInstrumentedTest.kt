/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import org.tsuyomi.core.ui.components.TsuyomiDropdownMenu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.ui.theme.TsuyomiTypography
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import kotlin.math.abs


@RunWith(AndroidJUnit4::class)
class BookDetailInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun directoryFilterAndOrderingRemainReachableAtNarrowLargeFontWidths() {
        var fontScale by mutableStateOf(1f)
        var unreadOnly by mutableStateOf(false)
        var descending by mutableStateOf(false)
        var density = 1f
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 640.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    density = LocalDensity.current.density
                    DisplayEnvironmentProvider(standardTestEnvironment) {
                        MaterialTheme {
                            Column(Modifier.fillMaxWidth().testTag("directory-controls-viewport")) {
                                DetailDirectoryHeader(
                                    totalChapters = 213,
                                    unreadOnly = unreadOnly,
                                    unreadFilterAvailable = true,
                                    descending = descending,
                                    onToggleUnreadOnly = { unreadOnly = !unreadOnly },
                                    onToggleOrder = { descending = !descending },
                                )
                            }
                        }
                    }
                }
            }
        }
        for (scale in listOf(1f, 2f)) {
            compose.runOnIdle {
                fontScale = scale
                unreadOnly = false
                descending = false
            }
            val viewport = compose.onNodeWithTag("directory-controls-viewport")
                .fetchSemanticsNode().boundsInRoot
            val filter = compose.onNodeWithTag("detail-unread-filter")
            val order = compose.onNodeWithContentDescription(context.getString(R.string.book_order_ascending))
            for (control in listOf(filter, order)) {
                control.assertIsDisplayed()
                val bounds = control.fetchSemanticsNode().touchBoundsInRoot
                assertTrue("control clipped at fontScale=$scale: $bounds vs $viewport",
                    bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f)
                assertTrue(bounds.width >= 48f * density - 1f && bounds.height >= 48f * density - 1f)
            }
            filter.performTouchInput { click() }
            filter.assertIsSelected()
            order.performTouchInput { click() }
            compose.onNodeWithContentDescription(context.getString(R.string.book_order_descending)).assertIsDisplayed()
            compose.runOnIdle { assertTrue(descending && unreadOnly) }
        }
    }

    @Test
    fun directoryGroupsChaptersByVolumeAndDefaultsToCurrentVolume() {
        val book = sourceBook()
        val chapters = listOf(
            chapter("v1-c1", "第一卷 第一章", "第一卷"),
            chapter("v1-c2", "第一卷 第二章", "第一卷"),
            chapter("v2-c1", "第二卷 第一章", "第二卷"),
            chapter("v2-c2", "第二卷 第二章", "第二卷"),
            chapter("v3-c1", "第三卷 第一章", "第三卷"),
        )
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
            var unreadOnly by remember { mutableStateOf(false) }
            MaterialTheme {
                StandardBookDetailScreen(state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "连载")),
                directoryState = SourceBookState.Content(SourceDirectory(book.identity, chapters)),
                localState = DetailLocalState(
                    inLibrary = true,
                    progressChapterId = "v2-c1",
                    progressChapterFraction = 0.4,
                    completedChapterIds = setOf("v1-c1", "v1-c2"),
                ),
                mutation = null,
                coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                unreadOnly = unreadOnly,
                descending = false,
                selectedChapterId = null,
                cacheState = DetailCacheState(),
                onCacheAction = {},
                onSetRating = {},
                onSearchAuthor = {},
                onConfirmTag = {},
                onToggleUnreadOnly = { unreadOnly = !unreadOnly },
                onToggleOrder = {},
                onSelectChapter = {},
                onContinueReading = {},
                onAddToLibrary = {},
                onRequestRemoveFromLibrary = {},
                onRetry = {},
                onUseOfflineCache = {},
                onOpenVerification = {},
                onKeepDefaultLibrary = {},
                )
            }
            }
        }

        compose.onNodeWithText("第二卷").assert(hasStateDescription("已展开"))
        compose.onNodeWithText("第二卷 第一章").assertIsDisplayed()
        compose.onNodeWithText("第一卷").assert(hasStateDescription("已收起"))
        compose.onNodeWithText("第一卷 第一章").assertDoesNotExist()
        compose.onNodeWithContentDescription("当前顺序：正序，点按切换").assertExists()
        compose.onNodeWithText("仅看未读").assertIsNotSelected().assert(hasStateDescription("当前筛选：全部章节"))

        compose.onNodeWithText("第一卷").performClick()
        compose.onNodeWithText("第一卷").assert(hasStateDescription("已展开"))
        compose.onNodeWithText("第一卷 第一章").assertIsDisplayed()
        compose.onNodeWithText("仅看未读").performClick()
        compose.onNodeWithText("仅看未读").assertIsSelected().assert(hasStateDescription("当前筛选：仅看未读"))
        compose.onNodeWithText("第一卷").assertDoesNotExist()
        compose.onNodeWithText("第二卷 第一章").assertIsDisplayed()
    }

    @Test
    fun exactUpdateFocusExpandsAndScrollsToItsChapterWithoutChangingResume() {
        val book = sourceBook()
        val chapters = listOf(
            chapter("v1-c1", "第一卷 第一章", "第一卷"),
            chapter("v2-c1", "第二卷 第一章", "第二卷"),
            chapter("v3-c1", "第三卷 第一章", "第三卷"),
        )
        var focusHandled = 0
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
            MaterialTheme {
                StandardBookDetailScreen(state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "连载")),
                directoryState = SourceBookState.Content(SourceDirectory(book.identity, chapters)),
                localState = DetailLocalState(
                    inLibrary = true,
                    progressChapterId = "v1-c1",
                ),
                mutation = null,
                coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                unreadOnly = false,
                descending = false,
                selectedChapterId = null,
                cacheState = DetailCacheState(),
                onCacheAction = {},
                onSetRating = {},
                onSearchAuthor = {},
                onConfirmTag = {},
                onToggleUnreadOnly = {},
                onToggleOrder = {},
                onSelectChapter = {},
                onContinueReading = {},
                onAddToLibrary = {},
                onRequestRemoveFromLibrary = {},
                onRetry = {},
                onUseOfflineCache = {},
                onOpenVerification = {},
                onKeepDefaultLibrary = {},
                focusChapterId = "v3-c1",
                onFocusHandled = { focusHandled++ },
                )
            }
            }
        }

        compose.waitUntil(5_000) { focusHandled == 1 }
        compose.onNodeWithTag("detail-chapter-v3-c1").assertIsDisplayed()
        compose.onNodeWithTag("detail-chapter-v1-c1").assert(hasStateDescription("未读，当前阅读"))
    }

    @Test
    fun unresolvedAddCannotBeLocallyUnlocked() {
        val book = sourceBook()
        var operation by mutableStateOf("ADD")
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
                MaterialTheme {
                    StandardBookDetailScreen(state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "连载")),
                    directoryState = SourceBookState.Loading,
                    localState = DetailLocalState(
                        reconciliationOperation = operation,
                        reconciliation = "UNRESOLVED",
                    ),
                    mutation = null,
                    coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                    unreadOnly = false,
                    descending = false,
                    selectedChapterId = null,
                    cacheState = DetailCacheState(),
                    onCacheAction = {},
                    onSetRating = {},
                    onSearchAuthor = {},
                    onConfirmTag = {},
                    onToggleUnreadOnly = {},
                    onToggleOrder = {},
                    onSelectChapter = {},
                    onContinueReading = {},
                    onAddToLibrary = {},
                    onRequestRemoveFromLibrary = {},
                    onRetry = {},
                    onUseOfflineCache = {},
                    onOpenVerification = {},
                    onKeepDefaultLibrary = {},
                    )
                }
            }
        }

        compose.onNodeWithText("重试加入网站收藏").assertIsDisplayed()
        compose.onNodeWithText("仅解除锁定").assertDoesNotExist()

        operation = "MOVE"
        compose.onNodeWithText("重试移动网站收藏").assertIsDisplayed()
        compose.onNodeWithText("仅解除锁定").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun adaptiveHeaderMovesTheWholeActionGroupAndPreservesTouchTargets() {
        val baselineWidth = (1080f * 160f / 420f).dp
        var viewportWidth by mutableStateOf(baselineWidth)
        var fontScale by mutableStateOf(1f)
        var title by mutableStateOf("文学少女")
        var density = 1f
        lateinit var inputModeManager: InputModeManager
        var lastUpdatedDate by mutableStateOf<String?>("2026-02-03")
        var localState by mutableStateOf(DetailLocalState(inLibrary = true))
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(viewportWidth, 1000.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    density = LocalDensity.current.density
                    inputModeManager = LocalInputModeManager.current
                    MaterialTheme(typography = TsuyomiTypography) {
                        val book = sourceBook().copy(title = title)
                        Column {
                            DetailIdentityModule(
                                detail = SourceBookDetail(book, "简介", emptyList(), "连载中", lastUpdatedDate),
                                coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                                localState = localState,
                                onSetRating = { localState = localState.copy(rating = it) },
                                onSearchAuthor = {},
                                onAddToLibrary = {},
                                onRequestRemoveFromLibrary = {},
                                primaryActionEnabled = true,
                                onOpenDestinations = {},
                                destinationMenuExpanded = false,
                                onDestinationMenuExpandedChange = {},
                                destinationMenuContent = {},
                            )
                            DetailTagActionsModule(
                                tags = listOf("奇幻"),
                                enabled = localState.inLibrary,
                                tagEditorOpen = false,
                                tagDraft = "",
                                onOpenTagEditor = {},
                                onTagDraftChange = {},
                                onDismissTagEditor = {},
                                onConfirmTag = {},
                            )
                        }
                    }
                }
            }
            }
        }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        fun titleLines(): Int {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("detail-title-flow", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().lineCount
        }
        fun assertCompletePrimaryLabel() {
            val label = if (localState.inLibrary) "已在书架" else "加入书架"
            val labelLayouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(labelLayouts) }
            val labelLayout = labelLayouts.single()
            val labelWidth = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().size.width
            assertTrue(
                "The complete primary label must remain on one line",
                labelLayout.lineCount == 1 &&
                    labelLayout.getLineEnd(0, visibleEnd = true) == labelLayout.layoutInput.text.length,
            )
            for (index in labelLayout.layoutInput.text.indices) {
                val glyph = labelLayout.getBoundingBox(index)
                assertTrue("Primary glyph $index must fit: $glyph, width=$labelWidth", glyph.left >= 0f && glyph.right <= labelWidth + 1f)
            }
        }
        fun assertDistributedCoverColumn(expectedTitleLines: Int) {
            val cover = bounds("detail-cover")
            val titleBlock = bounds("detail-title-block")
            val authorRow = bounds("detail-author-row")
            val metadata = bounds("detail-metadata-row")
            val rating = bounds("detail-rating-row")
            val split = bounds("detail-library-action")
            assertTrue("Expected $expectedTitleLines title lines, found ${titleLines()}", titleLines() == expectedTitleLines)
            assertTrue(abs(titleBlock.top - cover.top) <= 1f)
            assertTrue(titleBlock.bottom <= authorRow.top)
            assertTrue(authorRow.bottom <= metadata.top)
            assertTrue(metadata.bottom <= rating.top)
            assertTrue(rating.bottom <= split.top)
            assertTrue(abs(split.bottom - cover.bottom) <= 1f)
            assertTrue(abs(titleBlock.left - authorRow.left) <= 1f)
            assertTrue(abs(authorRow.left - metadata.left) <= 1f)
            assertTrue(abs(metadata.left - rating.left) <= 1f)
            assertTrue(abs(rating.left - split.left) <= 1f)
            assertTrue(abs(bounds("detail-rating-star-1-glyph").left - titleBlock.left) <= 1f)
            val gaps = listOf(
                authorRow.top - titleBlock.bottom,
                metadata.top - authorRow.bottom,
                rating.top - metadata.bottom,
                split.top - rating.bottom,
            )
            assertTrue("Cover-right gaps must be non-negative: $gaps", gaps.all { it >= 0f })
            assertTrue("Cover-right remaining height must be evenly distributed: $gaps", gaps.max() - gaps.min() <= 1f)
            assertTrue(abs(rating.height - 36f * density) <= 1f)
            assertTrue(abs(split.height - 48f * density) <= 1f)
            assertTrue(rating.right < bounds("detail-identity-module").right)
            assertTrue(split.right < bounds("detail-identity-module").right)
            assertCompletePrimaryLabel()
        }
        fun assertRelocatedHorizontal(minimumTitleLines: Int) {
            val cover = bounds("detail-cover")
            val titleBlock = bounds("detail-title-block")
            val authorRow = bounds("detail-author-row")
            val metadata = bounds("detail-metadata-row")
            val rating = bounds("detail-rating-row")
            val split = bounds("detail-library-action")
            val precedingBottom = maxOf(cover.bottom, titleBlock.bottom, authorRow.bottom, metadata.bottom)
            assertTrue("Expected at least $minimumTitleLines title lines, found ${titleLines()}", titleLines() >= minimumTitleLines)
            assertTrue(abs(minOf(rating.top, split.top) - precedingBottom - 8f * density) <= 1f)
            assertTrue(abs(rating.center.y - split.center.y) <= 1f)
            assertTrue(split.left >= rating.right)
            assertTrue(split.right < bounds("detail-identity-module").right)
            assertTrue(abs(bounds("detail-tag-surface").top - maxOf(rating.bottom, split.bottom) - 8f * density) <= 1f)
            assertCompletePrimaryLabel()
        }

        compose.waitForIdle()
        val baselineTag = bounds("detail-tag-label")
        assertTrue(abs(baselineTag.height - 40f * density) <= 1f)
        val addTagTarget = compose.onNodeWithTag("detail-add-tag", useUnmergedTree = true)
            .fetchSemanticsNode().touchBoundsInRoot
        assertTrue(addTagTarget.width >= 48f * density - 1f && addTagTarget.height >= 48f * density - 1f)
        val tagRegion = bounds("detail-tag-region")
        val tagFlow = bounds("detail-tag-module")
        val addTagBounds = bounds("detail-add-tag")
        val addTagGapBounds = bounds("detail-add-tag-gap")
        val addTagGlyphBounds = bounds("detail-add-tag-glyph")
        val tagTitleBounds = bounds("detail-tag-title")
        assertTrue(abs(addTagBounds.center.y - tagRegion.top) <= 1f)
        assertTrue(abs(addTagGapBounds.center.y - tagRegion.top) <= 1f)
        assertTrue(abs(tagTitleBounds.center.y - tagRegion.top) <= 1f)
        assertTrue("Legend text must align with the first tag's text, not its chip frame", abs(tagTitleBounds.left - (baselineTag.left + 8f * density)) <= 1f)
        val legendToTagGap = baselineTag.top - tagTitleBounds.bottom
        assertTrue("Visible legend-to-tag gap must stay within 8dp: $legendToTagGap", legendToTagGap in 0f..8f * density)
        assertTrue(abs(addTagGlyphBounds.width - 16f * density) <= 1f)
        assertTrue(abs(addTagGlyphBounds.height - 16f * density) <= 1f)
        assertTrue(addTagGapBounds.width <= 24f * density + 1f)
        assertTrue(addTagGapBounds.height <= 20f * density + 1f)
        assertTrue(tagFlow.left >= tagRegion.left && tagFlow.right <= tagRegion.right)
        assertDistributedCoverColumn(expectedTitleLines = 1)
        compose.onNodeWithTag("detail-title-overflow").assertDoesNotExist()

        compose.runOnIdle { title = "落第贤者的学院无双～二度转生的最强贤者～" }
        compose.waitForIdle()
        assertDistributedCoverColumn(expectedTitleLines = 2)
        compose.onNodeWithTag("detail-title-overflow").assertDoesNotExist()

        compose.runOnIdle {
            lastUpdatedDate = null
            localState = localState.copy(inLibrary = false)
        }
        compose.waitForIdle()
        assertDistributedCoverColumn(expectedTitleLines = 2)
        repeat(5) { compose.onNodeWithTag("detail-rating-star-${it + 1}-touch").assertIsNotEnabled() }

        compose.runOnIdle {
            title = "文学少女与渴望死亡的小丑以及被隐藏在漫长书页后的秘密".repeat(2)
            lastUpdatedDate = "2026-02-03"
            localState = localState.copy(inLibrary = true)
        }
        compose.waitForIdle()
        assertTrue("Collapsed long title must stay at two lines", titleLines() == 2)
        compose.onNodeWithTag("detail-title-overflow", useUnmergedTree = true)
            .assert(hasStateDescription("完整标题已收起"))
        val titleToggleTarget = compose.onNodeWithTag("detail-title-overflow", useUnmergedTree = true)
            .fetchSemanticsNode().touchBoundsInRoot
        assertTrue(titleToggleTarget.width >= 48f * density - 1f && titleToggleTarget.height >= 48f * density - 1f)
        assertDistributedCoverColumn(expectedTitleLines = 2)
        compose.onNodeWithContentDescription("查看完整标题", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("detail-title-overflow", useUnmergedTree = true)
            .assert(hasStateDescription("完整标题已展开"))
        assertRelocatedHorizontal(minimumTitleLines = 3)
        val ratingBounds = bounds("detail-rating-row")
        for (value in 1..4) {
            val boundary = (bounds("detail-rating-star-$value-glyph").center.x +
                bounds("detail-rating-star-${value + 1}-glyph").center.x) / 2f
            for ((offset, expected) in listOf(-2f to value, 2f to value + 1)) {
                val tap = Offset(boundary - ratingBounds.left + offset * density, ratingBounds.height / 2f)
                compose.onNodeWithTag("detail-rating-row").performTouchInput { click(tap) }
                compose.onNodeWithTag("detail-rating-star-$expected-touch").assertIsSelected()
                if (expected < 5) compose.onNodeWithTag("detail-rating-star-${expected + 1}-touch").assertIsNotSelected()
                compose.onNodeWithTag("detail-rating-row").performTouchInput { click(tap) }
                repeat(5) { compose.onNodeWithTag("detail-rating-star-${it + 1}-touch").assertIsNotSelected() }
            }
        }
        compose.runOnIdle { assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard)) }
        for (value in 1..5) {
            val star = compose.onNodeWithTag("detail-rating-star-$value-touch")
            star.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
            star.performKeyInput { pressKey(Key.Enter) }.assertIsSelected()
            star.performKeyInput { pressKey(Key.Enter) }.assertIsNotSelected()
        }
        compose.onNodeWithContentDescription("收起完整标题", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertDistributedCoverColumn(expectedTitleLines = 2)

        compose.runOnIdle {
            viewportWidth = 320.dp
            fontScale = 2f
            title = "文学少女"
        }
        compose.waitForIdle()
        val narrowRating = bounds("detail-rating-row")
        val narrowSplit = bounds("detail-library-action")
        assertTrue(narrowSplit.top >= narrowRating.bottom)
        assertTrue(abs(narrowRating.left - narrowSplit.left) <= 1f)
        assertTrue(abs(bounds("detail-rating-band").height - 36f * density) <= 1f)
        assertTrue("The relocated button must not stretch across spare width", narrowSplit.right < bounds("detail-identity-module").right)
        assertCompletePrimaryLabel()
        val narrowDisclosure = bounds("tsuyomi-split-trailing")
        assertTrue(abs(narrowDisclosure.width - 48f * density) <= 1f)
        assertTrue(abs(narrowDisclosure.right - narrowSplit.right) <= 1f)
        val statusLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("detail-publication-status", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(statusLayouts) }
        val statusLayout = statusLayouts.single()
        assertTrue(
            "Publication status metadata must remain complete at fontScale 2.0",
            statusLayout.lineCount == 1 &&
                statusLayout.getLineEnd(0, visibleEnd = true) == statusLayout.layoutInput.text.length,
        )
        for (index in statusLayout.layoutInput.text.indices) {
            val glyph = statusLayout.getBoundingBox(index)
            assertTrue(glyph.left >= 0f && glyph.right <= statusLayout.size.width + 1f)
            assertTrue(glyph.top >= 0f && glyph.bottom <= statusLayout.size.height + 1f)
        }
        val largeFontTag = bounds("detail-tag-label")
        val largeFontTagText = compose.onNodeWithText("奇幻", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(largeFontTag.height >= 40f * density - 1f)
        assertTrue(largeFontTag.height <= 48f * density + 1f)
        assertTrue(largeFontTagText.top >= largeFontTag.top && largeFontTagText.bottom <= largeFontTag.bottom)
        assertTrue(abs(bounds("detail-rating-star-3-glyph").height - 20f * density) <= 1f)
        repeat(5) { index ->
            val target = compose.onNodeWithTag("detail-rating-star-${index + 1}-touch")
                .fetchSemanticsNode().touchBoundsInRoot
            assertTrue(target.width >= 48f * density - 1f && target.height >= 48f * density - 1f)
        }
        compose.onNodeWithTag("detail-rating-star-3-touch").performClick().assertIsSelected()
    }

    @Test
    fun destinationOutcomeRemainsVisibleAfterMenuDismissal() {
        var retryCount = 0
        var keepDefaultCount = 0
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
                MaterialTheme(typography = TsuyomiTypography) {
                StandardBookDetailScreen(state = SourceBookState.Content(SourceBookDetail(sourceBook(), "简介", emptyList(), null, null)),
                directoryState = SourceBookState.Content(
                    SourceDirectory(sourceBook().identity, listOf(chapter("c1", "第一章", "第一卷"))),
                ),
                localState = DetailLocalState(),
                mutation = null,
                coverState = CoverUiState.Fallback(FallbackSpec("测试", null)),
                unreadOnly = false,
                descending = false,
                selectedChapterId = null,
                cacheState = DetailCacheState(),
                onCacheAction = {},
                onSetRating = {},
                onSearchAuthor = {},
                onConfirmTag = {},
                onToggleUnreadOnly = {},
                onToggleOrder = {},
                onSelectChapter = {},
                onContinueReading = {},
                onAddToLibrary = {},
                onRequestRemoveFromLibrary = {},
                onRetry = {},
                onUseOfflineCache = {},
                onOpenVerification = {},
                onKeepDefaultLibrary = { keepDefaultCount += 1 },
                destinationMenuExpanded = false,
                destinationMessage = "已加入默认书架，目标移动尚未完成",
                partialMoveTargetName = "特别收藏",
                onRetryMoveOnly = { retryCount += 1 },
                )
            }
            }
        }

        compose.onNodeWithText("已加入默认书架，目标移动尚未完成").assertIsDisplayed()
        compose.onNodeWithText("保留在默认书架").performClick()
        compose.runOnIdle { assertTrue(keepDefaultCount == 1) }
        compose.onNodeWithText("继续移至特别收藏").performClick()
        compose.runOnIdle { assertTrue(retryCount == 1) }
    }

    @Test
    fun headerAndIntroductionUseCompactDensity() {
        val book = sourceBook().copy(title = "文学少女")
        val description = "文艺社的两位成员调查十年前的人间失格事件。随着线索逐步出现，他们发现每个人都在用自己的方式保护重要的人，也必须面对被隐藏多年的真相。这个过程改变了他们对故事、记忆与彼此关系的理解。"
        var destinationsOpened = false
        var searchedAuthor: String? = null
        var authorSearchCount = 0
        var removeRequests = 0
        var confirmRemoval: (() -> Unit)? = null
        var mutation by mutableStateOf<DetailMutationStatus?>(null)
        compose.setContent {
            var menuExpanded by remember { mutableStateOf(false) }
            var localState by remember { mutableStateOf(DetailLocalState()) }
            DisplayEnvironmentProvider(standardTestEnvironment) {
            MaterialTheme(typography = TsuyomiTypography) {
                StandardBookDetailScreen(state = SourceBookState.Content(SourceBookDetail(book, description, listOf("奇幻"), "已完结", "2026-09-04")),
                directoryState = SourceBookState.Content(
                    SourceDirectory(book.identity, listOf(chapter("c1", "第一章", "第一卷"))),
                ),
                coverState = CoverUiState.Fallback(FallbackSpec(book.title, book.identity.sourceId)),
                localState = localState,
                mutation = mutation,
                unreadOnly = false,
                descending = false,
                selectedChapterId = null,
                cacheState = DetailCacheState(),
                onCacheAction = {},
                onSetRating = { localState = localState.copy(rating = it) },
                onSearchAuthor = { searchedAuthor = it; authorSearchCount++ },
                onConfirmTag = {},
                onToggleUnreadOnly = {},
                onToggleOrder = {},
                onSelectChapter = {},
                onContinueReading = {},
                onAddToLibrary = { localState = localState.copy(inLibrary = true) },
                onRequestRemoveFromLibrary = {
                    removeRequests++
                    confirmRemoval = { localState = localState.copy(inLibrary = false) }
                },
                onOpenDestinations = { destinationsOpened = true },
                destinationMenuExpanded = menuExpanded,
                onDestinationMenuExpandedChange = { menuExpanded = it },
                destinationMenuContent = { dismissMenu ->
                    BookDestinationMenu(
                        readLater = localState.readLater,
                        collections = emptyList(),
                        remoteTargets = emptyList(),
                        selectedRemoteTargetId = null,
                        loadingRemoteTargets = false,
                        websiteGroupingEnabled = false,
                        onToggleReadLater = {
                            localState = localState.copy(
                                inLibrary = true,
                                readLater = !localState.readLater,
                            )
                        },
                        onToggleCollection = {},
                        onApplyWebsite = {},
                        onDismiss = dismissMenu,
                    )
                },
                onRetry = {},
                onUseOfflineCache = {},
                onOpenVerification = {},
                onKeepDefaultLibrary = {},
                )
            }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("上次更新：2026-09-04").assertIsDisplayed()
        val authorText = compose.onNodeWithTag("detail-author").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single()
        val authorLink = authorText.getLinkAnnotations(0, authorText.length).single().item as LinkAnnotation.Clickable
        assertTrue(authorLink.styles?.style?.color == Color(0xFF4A6E8A))
        assertTrue(authorLink.styles?.style?.textDecoration == null)
        assertTrue(authorSearchCount == 0)
        compose.onNodeWithTag("detail-author").performClick()
        compose.waitForIdle()
        assertTrue(searchedAuthor == book.author && authorSearchCount == 1)
        compose.onNodeWithTag("detail-publication-status", useUnmergedTree = true).assertIsDisplayed()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val coverBounds = compose.onNodeWithTag("detail-cover").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(coverBounds.width - 135f * density) <= 1f)
        assertTrue(kotlin.math.abs(coverBounds.height - 189f * density) <= 1f)
        val tagVisualBounds = compose.onNodeWithTag("detail-tag-label", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(tagVisualBounds.height - 40f * density) <= 1f)
        val unreadFilterTarget = compose.onNodeWithTag("detail-unread-filter", useUnmergedTree = true)
            .fetchSemanticsNode().touchBoundsInRoot
        assertTrue(unreadFilterTarget.height >= 48f * density - 1f)

        val statusBounds = compose.onNodeWithTag("detail-publication-status", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val authorBounds = compose.onNodeWithTag("detail-author", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val authorRowBounds = compose.onNodeWithTag("detail-author-row").fetchSemanticsNode().boundsInRoot
        val dateBounds = compose.onNodeWithTag("detail-last-updated", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val metadataBounds = compose.onNodeWithTag("detail-metadata-row").fetchSemanticsNode().boundsInRoot
        val compactRatingBounds = compose.onNodeWithTag("detail-rating-row").fetchSemanticsNode().boundsInRoot
        val introductionBounds = compose.onNodeWithTag("detail-introduction-module").fetchSemanticsNode().boundsInRoot
        assertTrue(authorBounds.bottom <= statusBounds.top)
        assertTrue(statusBounds.left < dateBounds.left)
        assertTrue(abs(statusBounds.top - dateBounds.top) <= 1f)
        assertTrue(abs(statusBounds.height - dateBounds.height) <= 1f)
        assertTrue(authorRowBounds.bottom <= metadataBounds.top)
        assertTrue(compactRatingBounds.bottom <= introductionBounds.top)
        repeat(5) { index ->
            val touchBounds = compose.onNodeWithTag("detail-rating-star-${index + 1}-touch")
                .fetchSemanticsNode().touchBoundsInRoot
            assertTrue(touchBounds.width >= 48f * density - 1f)
            assertTrue(touchBounds.height >= 48f * density - 1f)
        }

        val previewBounds = compose.onNodeWithTag("detail-introduction-preview").fetchSemanticsNode().boundsInRoot
        val expandBounds = compose.onNodeWithTag("detail-introduction-expand").fetchSemanticsNode().boundsInRoot
        val expandLabelBounds = compose.onNodeWithTag(
            "detail-introduction-expand-label",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(expandBounds.bottom <= previewBounds.bottom + 1f)
        assertTrue(expandBounds.top >= previewBounds.bottom - 48f * density - 1f)
        assertTrue(previewBounds.bottom - expandLabelBounds.bottom <= 2f * density)
        val identityBounds = compose.onNodeWithTag("detail-identity-module").fetchSemanticsNode().boundsInRoot
        val titleBlockBounds = compose.onNodeWithTag("detail-title-block").fetchSemanticsNode().boundsInRoot
        val libraryActionBounds = compose.onNodeWithTag("detail-library-action").fetchSemanticsNode().boundsInRoot
        val splitLeadingBounds = compose.onNodeWithTag("tsuyomi-split-leading", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val splitTrailingBounds = compose.onNodeWithTag("tsuyomi-split-trailing", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val addLabelBounds = compose.onNodeWithText("加入书架", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val destinationBounds = compose.onNodeWithContentDescription("更多加入选项")
            .fetchSemanticsNode().boundsInRoot
        val tagBounds = compose.onNodeWithTag("detail-tag-module").fetchSemanticsNode().boundsInRoot
        val tagSurfaceBounds = compose.onNodeWithTag("detail-tag-surface").fetchSemanticsNode().boundsInRoot
        val ratingBounds = compose.onNodeWithTag("detail-rating-row").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(libraryActionBounds.left - titleBlockBounds.left) <= 1f)
        assertTrue(abs(ratingBounds.left - titleBlockBounds.left) <= 1f)
        assertTrue(abs(compose.onNodeWithTag("detail-rating-star-1-glyph", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left - titleBlockBounds.left) <= 1f)
        assertTrue(abs(libraryActionBounds.bottom - coverBounds.bottom) <= 1f)
        val distributedGaps = listOf(
            authorRowBounds.top - titleBlockBounds.bottom,
            metadataBounds.top - authorRowBounds.bottom,
            ratingBounds.top - metadataBounds.bottom,
            libraryActionBounds.top - ratingBounds.bottom,
        )
        assertTrue(distributedGaps.all { it >= 0f })
        assertTrue(distributedGaps.max() - distributedGaps.min() <= 1f)
        assertTrue(libraryActionBounds.right < identityBounds.right)
        assertTrue(kotlin.math.abs(ratingBounds.height - 36f * density) <= 1f)
        assertTrue(kotlin.math.abs(splitTrailingBounds.right - libraryActionBounds.right) <= 1f)
        assertTrue(kotlin.math.abs(splitTrailingBounds.width - 48f * density) <= 1f)
        val readingFabBounds = compose.onNodeWithTag("detail-reading-fab").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(readingFabBounds.height - 56f * density) <= 1f)
        assertTrue(kotlin.math.abs(splitLeadingBounds.top - splitTrailingBounds.top) <= 1f)
        assertTrue(kotlin.math.abs(splitLeadingBounds.bottom - splitTrailingBounds.bottom) <= 1f)
        assertTrue(kotlin.math.abs(splitLeadingBounds.height - 48f * density) <= 1f)
        assertTrue(kotlin.math.abs(splitTrailingBounds.height - 48f * density) <= 1f)
        assertTrue(addLabelBounds.height <= 28f * density)
        assertTrue(destinationBounds.left >= libraryActionBounds.left)
        assertTrue(destinationBounds.right <= libraryActionBounds.right + 1f)
        assertTrue(libraryActionBounds.bottom <= identityBounds.bottom + 1f)
        assertTrue(kotlin.math.abs(tagSurfaceBounds.top - coverBounds.bottom - 8f * density) <= 1f)
        assertTrue(tagBounds.top >= tagSurfaceBounds.top)
        compose.onNodeWithTag("detail-read-later-action").assertDoesNotExist()
        compose.onNodeWithText("加入书架").performClick()
        compose.onNodeWithText("已在书架").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(removeRequests == 1) }
        // A dismissed host confirmation does not change local membership.
        compose.onNodeWithText("已在书架").assertIsDisplayed()
        mutation = DetailMutationStatus(DetailMutationOperation.REMOVE_FROM_LIBRARY, DetailMutationPhase.WORKING)
        compose.onNodeWithText("已在书架").assertIsNotEnabled()
        mutation = null
        compose.runOnIdle {
            assertTrue(confirmRemoval != null)
            confirmRemoval?.invoke()
        }
        compose.onNodeWithText("加入书架").assertIsDisplayed().performClick()
        compose.onNodeWithText("已在书架").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多加入选项").assertIsEnabled().performClick()
        assertTrue(destinationsOpened)
        compose.onNodeWithTag("detail-destination-menu").assertIsDisplayed()
        compose.onNodeWithTag("detail-read-later-action")
            .assert(hasStateDescription("未稍后再读"))
            .performClick()
        compose.onNodeWithTag("detail-destination-menu").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多加入选项").performClick()
        compose.onNodeWithTag("detail-read-later-action")
            .assert(hasStateDescription("已稍后再读"))
        compose.onNodeWithTag("detail-library-action").assert(hasStateDescription("已在书架"))
        compose.onNodeWithTag("detail-rating-star-1-touch")
            .assertIsEnabled()
            .performClick()
            .assertIsSelected()
        compose.onNodeWithText("已在书架").assertIsDisplayed()
        val collapsedHeight = compose.onNodeWithTag("detail-introduction-text").fetchSemanticsNode().boundsInRoot.height

        compose.onNodeWithTag("detail-introduction-expand").performClick()
        compose.onNodeWithTag("detail-introduction-collapse").assertExists()
        val expandedHeight = compose.onNodeWithTag("detail-introduction-text").fetchSemanticsNode().boundsInRoot.height
        assertTrue(expandedHeight > collapsedHeight)
    }

    @Test
    fun twoLineTitleUsesDedicatedAuthorAndStatusRowsAcrossCoverHeight() {
        val book = sourceBook().copy(title = "落第贤者的学院无双～二度转生的最强贤者～")
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
            MaterialTheme(typography = TsuyomiTypography) {
                StandardBookDetailScreen(state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "已完结")),
                directoryState = SourceBookState.Content(
                    SourceDirectory(book.identity, listOf(chapter("c1", "第一章", "第一卷"))),
                ),
                localState = DetailLocalState(),
                mutation = null,
                coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                unreadOnly = false,
                descending = false,
                selectedChapterId = null,
                cacheState = DetailCacheState(),
                onCacheAction = {},
                onSetRating = {},
                onSearchAuthor = {},
                onConfirmTag = {},
                onToggleUnreadOnly = {},
                onToggleOrder = {},
                onSelectChapter = {},
                onContinueReading = {},
                onAddToLibrary = {},
                onRequestRemoveFromLibrary = {},
                onRetry = {},
                onUseOfflineCache = {},
                onOpenVerification = {},
                onKeepDefaultLibrary = {},
                )
            }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("detail-last-updated").assertDoesNotExist()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("detail-title-flow", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val titleLayout = layouts.single()
        assertTrue(titleLayout.lineCount == 2)
        assertTrue(titleLayout.getLineEnd(1, visibleEnd = true) == book.title.length)
        compose.onNodeWithTag("detail-title-overflow", useUnmergedTree = true).assertDoesNotExist()

        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val coverBounds = compose.onNodeWithTag("detail-cover").fetchSemanticsNode().boundsInRoot
        val titleBounds = compose.onNodeWithTag("detail-title-block").fetchSemanticsNode().boundsInRoot
        val authorRowBounds = compose.onNodeWithTag("detail-author-row").fetchSemanticsNode().boundsInRoot
        val metadataBounds = compose.onNodeWithTag("detail-metadata-row").fetchSemanticsNode().boundsInRoot
        val statusBounds = compose.onNodeWithTag("detail-publication-status", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val authorBounds = compose.onNodeWithTag("detail-author", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val ratingGroupBounds = compose.onNodeWithTag("detail-rating-row").fetchSemanticsNode().boundsInRoot
        val splitBounds = compose.onNodeWithTag("detail-library-action").fetchSemanticsNode().boundsInRoot
        assertTrue(titleBounds.bottom <= authorBounds.top)
        assertTrue(authorRowBounds.bottom <= statusBounds.top)
        assertTrue(abs(splitBounds.bottom - coverBounds.bottom) <= 1f)
        val distributedGaps = listOf(
            authorRowBounds.top - titleBounds.bottom,
            metadataBounds.top - authorRowBounds.bottom,
            ratingGroupBounds.top - metadataBounds.bottom,
            splitBounds.top - ratingGroupBounds.bottom,
        )
        assertTrue(distributedGaps.all { it >= 0f })
        assertTrue(distributedGaps.max() - distributedGaps.min() <= 1f)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun defaultFontLongTitleKeepsCompactCoverRightActionsUntilExpanded() {
        val book = sourceBook().copy(title = "在默认系统字体下仍应保持紧凑详情头部布局的超长书名".repeat(6))
        var viewportWidth by mutableStateOf(411.dp)
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(viewportWidth, 640.dp))) {
                    MaterialTheme(typography = TsuyomiTypography) {
                        DetailIdentityModule(
                            detail = SourceBookDetail(book, "简介", emptyList(), "连载中", "2026-09-14"),
                            coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                            localState = DetailLocalState(inLibrary = true),
                            onSetRating = {},
                            onSearchAuthor = {},
                            onAddToLibrary = {},
                            onRequestRemoveFromLibrary = {},
                            primaryActionEnabled = true,
                            onOpenDestinations = {},
                            destinationMenuExpanded = false,
                            onDestinationMenuExpandedChange = {},
                            destinationMenuContent = {},
                        )
                    }
                }
            }
        }

        fun titleLines(): Int {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("detail-title-flow", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().lineCount
        }

        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val cover = compose.onNodeWithTag("detail-cover").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("detail-title-block").fetchSemanticsNode().boundsInRoot
        val author = compose.onNodeWithTag("detail-author-row").fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithTag("detail-metadata-row").fetchSemanticsNode().boundsInRoot
        val rating = compose.onNodeWithTag("detail-rating-row").fetchSemanticsNode().boundsInRoot
        val libraryAction = compose.onNodeWithTag("detail-library-action").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(cover.width - 135f * density) <= 1f && abs(cover.height - 189f * density) <= 1f)
        assertTrue(titleLines() == 2)
        compose.onNodeWithTag("detail-title-overflow").assertIsDisplayed()
        listOf(author, metadata, rating, libraryAction).forEach { block ->
            assertTrue(block.left >= cover.right && block.bottom <= cover.bottom + 1f)
        }

        compose.runOnIdle { viewportWidth = 360.dp }
        val narrowCover = compose.onNodeWithTag("detail-cover").fetchSemanticsNode().boundsInRoot
        listOf("detail-rating-row", "detail-library-action").forEach { tag ->
            val block = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("Collapsed title must retain the right column at regular phone width", block.left >= narrowCover.right)
        }
        assertTrue(titleLines() == 2)
        compose.runOnIdle { viewportWidth = 411.dp }

        compose.onNodeWithTag("detail-title-overflow").performClick()
        assertTrue(titleLines() > 2)
        val expandedCover = compose.onNodeWithTag("detail-cover").fetchSemanticsNode().boundsInRoot
        val expandedLibraryAction = compose.onNodeWithTag("detail-library-action").fetchSemanticsNode().boundsInRoot
        assertTrue(expandedLibraryAction.top >= expandedCover.bottom)
    }

    @Test
    fun simpleWebsiteDestinationShowsOneAggregateTarget() {
        var appliedTargetId: String? = null
        compose.setContent {
            MaterialTheme {
                TsuyomiDropdownMenu(expanded = true, onDismissRequest = {}) {
                    BookDestinationMenu(
                        readLater = false,
                        collections = List(30) { index ->
                            DetailCollectionDestination("local-$index", "本地收藏夹 $index", false)
                        },
                        remoteTargets = listOf(
                            RemoteTarget("default", "默认书架", null, "folder"),
                            RemoteTarget("favorites", "特别收藏", null, "folder"),
                        ),
                        selectedRemoteTargetId = "default",
                        loadingRemoteTargets = false,
                        websiteGroupingEnabled = false,
                        onToggleReadLater = {},
                        onToggleCollection = {},
                        onApplyWebsite = { appliedTargetId = it },
                        onDismiss = {},
                    )
                }
            }
        }

        compose.onNodeWithText("稍后再读").assertIsDisplayed()
        compose.onNodeWithText("全部网站收藏").assertIsDisplayed()
        compose.onNodeWithText("默认书架").assertDoesNotExist()
        compose.onNodeWithText("特别收藏").assertDoesNotExist()
        compose.onNodeWithText("全部网站收藏").performClick()
        assertTrue(appliedTargetId == "default")
    }

    @Test
    fun readingFabStartsWithoutProgressAndResumesOnlyAValidSavedChapter() {
        val book = sourceBook()
        val chapters = listOf(
            chapter("c1", "第一章", "第一卷"),
            chapter("c2", "第二章", "第一卷"),
        )
        var localState by mutableStateOf(DetailLocalState(inLibrary = true))
        val openedChapterIds = mutableListOf<String>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
                MaterialTheme {
                    StandardBookDetailScreen(
                        state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "连载")),
                        directoryState = SourceBookState.Content(SourceDirectory(book.identity, chapters)),
                        localState = localState,
                        mutation = null,
                        coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                        unreadOnly = false,
                        descending = false,
                        selectedChapterId = null,
                        cacheState = DetailCacheState(),
                        onCacheAction = {},
                        onSetRating = {},
                        onSearchAuthor = {},
                        onConfirmTag = {},
                        onToggleUnreadOnly = {},
                        onToggleOrder = {},
                        onSelectChapter = {},
                        onContinueReading = { openedChapterIds += it.chapterId },
                        onAddToLibrary = {},
                        onRequestRemoveFromLibrary = {},
                        onRetry = {},
                        onUseOfflineCache = {},
                        onOpenVerification = {},
                        onKeepDefaultLibrary = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("detail-reading-fab").assertIsDisplayed()
            .assert(hasContentDescription(context.getString(R.string.book_start_reading)))
        compose.onNodeWithTag("detail-reading-fab").performClick()
        compose.runOnIdle { assertTrue(openedChapterIds.lastOrNull() == "c1") }

        compose.runOnIdle {
            localState = localState.copy(progressChapterId = "missing", progressChapterFraction = 0.4)
        }
        compose.onNodeWithTag("detail-reading-fab").assertIsDisplayed()
            .assert(hasContentDescription(context.getString(R.string.book_start_reading)))
        compose.onNodeWithTag("detail-reading-fab").performClick()
        compose.runOnIdle { assertTrue(openedChapterIds.lastOrNull() == "c1") }

        compose.runOnIdle {
            localState = localState.copy(progressChapterId = "c2", progressChapterFraction = 0.4)
        }
        compose.onNodeWithTag("detail-reading-fab").assertIsDisplayed()
            .assert(hasContentDescription(context.getString(R.string.book_continue_reading)))
        compose.onNodeWithTag("detail-reading-fab").performClick()
        compose.runOnIdle { assertTrue(openedChapterIds.lastOrNull() == "c2") }
    }

    @Test
    fun directoryFabHidesDuringScrollAndReturnsWhenScrollStops() {
        val book = sourceBook()
        val chapters = (1..80).map { chapter("c$it", "第 $it 章", "第一卷") }
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
            MaterialTheme {
                StandardBookDetailScreen(state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "连载")),
                directoryState = SourceBookState.Content(SourceDirectory(book.identity, chapters)),
                localState = DetailLocalState(inLibrary = true, progressChapterId = "c1"),
                mutation = null,
                coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                unreadOnly = false,
                descending = false,
                selectedChapterId = null,
                cacheState = DetailCacheState(),
                onCacheAction = {},
                onSetRating = {},
                onSearchAuthor = {},
                onConfirmTag = {},
                onToggleUnreadOnly = {},
                onToggleOrder = {},
                onSelectChapter = {},
                onContinueReading = {},
                onAddToLibrary = {},
                onRequestRemoveFromLibrary = {},
                onRetry = {},
                onUseOfflineCache = {},
                onOpenVerification = {},
                onKeepDefaultLibrary = {},
                )
            }
            }
        }

        val scroll = compose.onNodeWithTag("book-detail-scroll")
        scroll.performScrollToIndex(12)
        compose.onNodeWithTag("adaptive-list-fab").assertIsDisplayed()
        scroll.performTouchInput {
            down(center)
            moveBy(Offset(0f, -240f), delayMillis = 120)
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("adaptive-list-fab").fetchSemanticsNodes().isEmpty()
        }
        scroll.performTouchInput { up() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("adaptive-list-fab").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun cacheSelectionTogglesChaptersWithoutOpeningReaderAndOnlyCancelsWhileWorking() {
        val book = sourceBook()
        val chapters = listOf(
            chapter("c1", "第一章", "第一卷"),
            chapter("c2", "第二章", "第一卷"),
        )
        var cacheState by mutableStateOf(DetailCacheState(selecting = true))
        var unreadOnly by mutableStateOf(false)
        var completedChapterIds by mutableStateOf(emptySet<String>())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheActions = mutableListOf<DetailCacheAction>()
        var openedChapterCount = 0
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
            MaterialTheme {
                StandardBookDetailScreen(
                    state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "连载")),
                    directoryState = SourceBookState.Content(SourceDirectory(book.identity, chapters)),
                    localState = DetailLocalState(completedChapterIds = completedChapterIds),
                    mutation = null,
                    coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                    unreadOnly = unreadOnly,
                    descending = false,
                    selectedChapterId = null,
                    cacheState = cacheState,
                    onCacheAction = { action ->
                        cacheActions += action
                        when (action) {
                            is DetailCacheAction.Toggle -> {
                                cacheState = cacheState.copy(
                                    selectedChapterIds = cacheState.selectedChapterIds.let { selected ->
                                        if (action.chapterId in selected) selected - action.chapterId else selected + action.chapterId
                                    },
                                )
                            }
                            is DetailCacheAction.ToggleAll -> {
                                val scopedChapterIds = action.chapterIds
                                cacheState = cacheState.copy(
                                    selectedChapterIds = if (cacheState.selectedChapterIds.containsAll(scopedChapterIds)) {
                                        cacheState.selectedChapterIds - scopedChapterIds
                                    } else {
                                        cacheState.selectedChapterIds + scopedChapterIds
                                    },
                                )
                            }
                            else -> Unit
                        }
                    },
                    onSetRating = {},
                    onSearchAuthor = {},
                    onConfirmTag = {},
                    onToggleUnreadOnly = { unreadOnly = !unreadOnly },
                    onToggleOrder = {},
                    onSelectChapter = { openedChapterCount++ },
                    onContinueReading = {},
                    onAddToLibrary = {},
                    onRequestRemoveFromLibrary = {},
                    onRetry = {},
                    onUseOfflineCache = {},
                    onOpenVerification = {},
                    onKeepDefaultLibrary = {},
                )
            }
            }
            }
        }
        val cacheSelectAll = compose.onNodeWithTag("detail-cache-toggle-all")
        cacheSelectAll.assertIsFocused().assertIsOff()
        cacheSelectAll.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        compose.onNodeWithText(context.getString(R.string.book_cache_start_selected, 0), useUnmergedTree = true).assertIsDisplayed()
        val startBounds = compose.onNodeWithTag("detail-cache-start").fetchSemanticsNode().boundsInRoot
        val viewportBounds = compose.onNodeWithTag("book-detail-scroll").fetchSemanticsNode().boundsInRoot
        assertTrue("Cache action must retain intrinsic width", startBounds.width < viewportBounds.width)

        cacheSelectAll.performClick().assertIsOn()
        compose.runOnIdle {
            assertTrue(cacheActions.last() == DetailCacheAction.ToggleAll(setOf("c1", "c2")))
        }
        compose.onNodeWithTag("detail-cache-chapter-c1").assertIsOn()
        compose.onNodeWithTag("detail-cache-chapter-c2").assertIsOn()
        compose.runOnIdle {
            completedChapterIds = setOf("c1")
            unreadOnly = true
        }
        compose.onNodeWithTag("detail-cache-chapter-c1").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.book_cache_start_selected, 2), useUnmergedTree = true).assertIsDisplayed()
        cacheSelectAll.performClick().assertIsOff()
        compose.onNodeWithText(context.getString(R.string.book_cache_start_selected, 1), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("detail-cache-start").assertIsEnabled()
        compose.runOnIdle {
            unreadOnly = false
            completedChapterIds = emptySet()
            cacheState = cacheState.copy(selectedChapterIds = setOf("c1", "c2"))
        }
        cacheSelectAll.performClick().assertIsOff()

        compose.onNodeWithTag("detail-cache-start").assertIsNotEnabled()
        compose.onNodeWithTag("detail-cache-chapter-c1").performClick()
        compose.onNodeWithTag("detail-cache-toggle-all").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate),
        )
        compose.runOnIdle {
            assertTrue(cacheActions.last() == DetailCacheAction.Toggle("c1"))
            assertTrue(openedChapterCount == 0)
        }
        compose.onNodeWithTag("detail-cache-start").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(cacheActions.last() == DetailCacheAction.Start) }
        compose.onNodeWithContentDescription("关闭章节缓存选择").performClick()
        compose.runOnIdle { assertTrue(cacheActions.last() == DetailCacheAction.Close) }
        compose.runOnIdle {
            cacheState = DetailCacheState(
                selecting = true,
                chapters = mapOf("c2" to DetailChapterCachePhase.CACHED),
            )
        }
        compose.onNodeWithTag("detail-cache-chapter-c2").assertIsNotEnabled()
        compose.onNodeWithTag("detail-cache-toggle-all").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle {
            assertTrue(cacheActions.last() == DetailCacheAction.ToggleAll(setOf("c1")))
        }

        compose.runOnIdle {
            cacheState = cacheState.copy(chapters = mapOf("c1" to DetailChapterCachePhase.CACHING))
        }
        compose.onNodeWithTag("detail-cache-chapter-c1").assertIsNotEnabled()
        compose.onNodeWithTag("detail-cache-start").assertDoesNotExist()
        compose.onNodeWithTag("detail-cache-cancel").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(cacheActions.last() == DetailCacheAction.Cancel) }
        compose.runOnIdle {
            cacheState = DetailCacheState(chapters = mapOf("c1" to DetailChapterCachePhase.CACHED))
        }
        compose.onNodeWithTag("detail-chapter-c1").assert(hasStateDescription("未读，已下载"))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun addTagTouchTargetActivatesOnceAcrossWrappedAndLargeFontFlows() {
        var tags by mutableStateOf(listOf("奇幻"))
        var fontScale by mutableStateOf(1f)
        var mutation by mutableStateOf<DetailMutationStatus?>(null)
        var tagEditorOpen by mutableStateOf(false)
        var tagDraft by mutableStateOf("")
        val submittedTags = mutableListOf<String>()
        var density = 1f
        val actionColor = Color(0xFFB000FF)
        val outlineColor = Color(0xFF007A35)
        val pageBackground = Color(0xFFF8F4F8)
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(240.dp, 1000.dp))) {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                        density = LocalDensity.current.density
                        MaterialTheme(
                            colorScheme = lightColorScheme(
                                primary = actionColor,
                                outlineVariant = outlineColor,
                                background = pageBackground,
                            ),
                            typography = TsuyomiTypography,
                        ) {
                            DetailTagActionsModule(
                                tags = tags,
                                enabled = true,
                                mutation = mutation,
                                tagEditorOpen = tagEditorOpen,
                                tagDraft = tagDraft,
                                onOpenTagEditor = { tagEditorOpen = true },
                                onTagDraftChange = { tagDraft = it },
                                onDismissTagEditor = { tagEditorOpen = false },
                                onConfirmTag = {
                                    submittedTags += tagDraft
                                    mutation = DetailMutationStatus(
                                        DetailMutationOperation.ADD_TAG,
                                        DetailMutationPhase.WORKING,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        fun mostDistinctPixel(tag: String): Color {
            val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
            var distinct = pixels[0, 0]
            var distance = -1f
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    val candidate = pixels[x, y]
                    val candidateDistance =
                        (candidate.red - pageBackground.red) * (candidate.red - pageBackground.red) +
                            (candidate.green - pageBackground.green) * (candidate.green - pageBackground.green) +
                            (candidate.blue - pageBackground.blue) * (candidate.blue - pageBackground.blue)
                    if (candidateDistance > distance) {
                        distinct = candidate
                        distance = candidateDistance
                    }
                }
            }
            return distinct
        }

        fun assertColorNear(expected: Color, actual: Color, label: String) {
            assertTrue(
                "$label must use its Material theme role: expected=$expected actual=$actual",
                abs(expected.red - actual.red) <= 0.03f &&
                    abs(expected.green - actual.green) <= 0.03f &&
                    abs(expected.blue - actual.blue) <= 0.03f,
            )
        }

        assertColorNear(outlineColor, mostDistinctPixel("detail-tag-title"), "Tag legend")
        assertColorNear(actionColor, mostDistinctPixel("detail-add-tag-glyph"), "Add-tag action")

        fun submitFrom(offset: Offset, tag: String, outcome: DetailMutationPhase = DetailMutationPhase.SUCCESS) {
            compose.onNodeWithTag("detail-add-tag").performTouchInput { click(offset) }
            compose.onNodeWithTag("detail-add-tag-input").assertIsDisplayed().performTextInput(tag)
            compose.onAllNodesWithText("添加").filterToOne(hasClickAction()).performClick()
            compose.runOnIdle {
                assertTrue(submittedTags.lastOrNull() == tag)
                assertTrue(submittedTags.count { it == tag } == 1)
            }
            compose.onNodeWithTag("detail-add-tag-input").assertIsNotEnabled()
            compose.runOnIdle {
                if (outcome == DetailMutationPhase.SUCCESS) {
                    tags = tags + tag
                    tagDraft = ""
                    tagEditorOpen = false
                }
                mutation = DetailMutationStatus(DetailMutationOperation.ADD_TAG, outcome)
            }
            if (outcome == DetailMutationPhase.SUCCESS) {
                compose.waitUntil(5_000) {
                    compose.onAllNodesWithTag("detail-add-tag-input").fetchSemanticsNodes().isEmpty()
                }
            } else {
                compose.onNodeWithTag("detail-add-tag-input").assertIsEnabled()
                compose.onNodeWithText(tag).assertIsDisplayed()
                compose.onAllNodesWithText("取消").filterToOne(hasClickAction()).performClick()
            }
            compose.runOnIdle { mutation = null }
        }

        submitFrom(compose.onNodeWithTag("detail-add-tag").fetchSemanticsNode().size.let { size ->
            Offset(size.width / 2f, size.height / 2f)
        }, "中心")

        compose.runOnIdle {
            tags = listOf("第一标签", "第二标签", "第三标签", "第四标签", "第五标签")
        }
        compose.waitForIdle()
        val wrappedLabels = compose.onAllNodesWithTag("detail-tag-label", useUnmergedTree = true)
            .fetchSemanticsNodes()
        val wrappedLabelBounds = wrappedLabels.map { it.boundsInRoot }
        val wrappedFlow = compose.onNodeWithTag("detail-tag-module", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val wrappedRegion = compose.onNodeWithTag("detail-tag-region", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val wrappedAddTarget = compose.onNodeWithTag("detail-add-tag", useUnmergedTree = true)
            .fetchSemanticsNode().touchBoundsInRoot
        assertTrue("Every tag must remain in the wrapping tag flow", wrappedLabels.size == tags.size)
        assertTrue("Tags must wrap within the full-width tag flow", wrappedLabelBounds.any { it.top > wrappedLabelBounds.first().top })
        val wrappedTitle = compose.onNodeWithTag("detail-tag-title", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val wrappedAddGlyph = compose.onNodeWithTag("detail-add-tag-glyph", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val wrappedAddGap = compose.onNodeWithTag("detail-add-tag-gap", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Add action must be centred directly on the outlined top edge",
            abs((wrappedAddTarget.top + wrappedAddTarget.bottom) / 2f - wrappedRegion.top) <= 1f)
        assertTrue("Compact add-action outline gap must be centred on the outlined top edge",
            abs(wrappedAddGap.center.y - wrappedRegion.top) <= 1f)
        assertTrue("Tag label must be centred directly on the outlined top edge",
            abs((wrappedTitle.top + wrappedTitle.bottom) / 2f - wrappedRegion.top) <= 1f)
        val wrappedFirstTagText = compose.onNodeWithText(tags.first(), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Visible tag title must align with the first tag's visible text, not its chip frame",
            abs(wrappedTitle.left - wrappedFirstTagText.left) <= 1f,
        )
        val wrappedLegendToTagGap = wrappedLabelBounds.first().top - wrappedTitle.bottom
        assertTrue(
            "Visible legend-to-tag gap must stay within 8dp: $wrappedLegendToTagGap",
            wrappedLegendToTagGap in 0f..8f * density,
        )
        assertTrue("Tag flow must receive the complete consistently inset region width",
            wrappedFlow.left >= wrappedRegion.left && wrappedFlow.right <= wrappedRegion.right)
        assertTrue(abs(wrappedAddGlyph.width - 16f * density) <= 1f)
        assertTrue(abs(wrappedAddGlyph.height - 16f * density) <= 1f)
        assertTrue(wrappedAddGap.width <= 24f * density + 1f)
        assertTrue(wrappedAddGap.height <= 20f * density + 1f)
        assertTrue(wrappedAddTarget.width >= 48f * density - 1f && wrappedAddTarget.height >= 48f * density - 1f)
        submitFrom(Offset(2f, 2f), "边缘")

        compose.runOnIdle {
            tags = listOf("大型字体标签", "第二个标签")
            fontScale = 2f
        }
        compose.waitForIdle()
        val largeFontRegion = compose.onNodeWithTag("detail-tag-region", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val largeFontAdd = compose.onNodeWithTag("detail-add-tag", useUnmergedTree = true).fetchSemanticsNode().touchBoundsInRoot
        assertTrue(abs((largeFontAdd.top + largeFontAdd.bottom) / 2f - largeFontRegion.top) <= 1f)
        submitFrom(compose.onNodeWithTag("detail-add-tag").fetchSemanticsNode().size.let { size ->
            Offset(size.width - 2f, size.height - 2f)
        }, "大字边缘")
        submitFrom(compose.onNodeWithTag("detail-add-tag").fetchSemanticsNode().size.let { size ->
            Offset(size.width / 2f, size.height / 2f)
        }, "失败后保留", DetailMutationPhase.ERROR)

        val submittedBeforeTagTouch = submittedTags.size
        compose.onNodeWithText("大型字体标签", useUnmergedTree = true).performTouchInput { click(center) }
        compose.onAllNodesWithTag("detail-add-tag-input").assertCountEquals(0)
        compose.runOnIdle { assertTrue(submittedTags.size == submittedBeforeTagTouch) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun fullTagRowsShareOneJustifiedGapWhileSparseRowsStayCompact() {
        var tags by mutableStateOf(listOf("奇幻"))
        var density = 1f
        val viewportWidth = 320.dp
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(viewportWidth, 1000.dp))) {
                    MaterialTheme(typography = TsuyomiTypography) {
                        density = LocalDensity.current.density
                        DetailTagActionsModule(
                            tags = tags,
                            enabled = true,
                            tagEditorOpen = false,
                            tagDraft = "",
                            onOpenTagEditor = {},
                            onTagDraftChange = {},
                            onDismissTagEditor = {},
                            onConfirmTag = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        fun rowBounds(): List<Pair<Float, List<androidx.compose.ui.geometry.Rect>>> {
            val labels = compose.onAllNodesWithTag("detail-tag-label", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
            val grouped = sortedMapOf<Float, MutableList<androidx.compose.ui.geometry.Rect>>()
            labels.forEach { grouped.getOrPut(it.top) { mutableListOf() } += it }
            return grouped.map { (top, rects) -> top to rects.sortedBy { it.left } }
        }

        fun gaps(row: List<androidx.compose.ui.geometry.Rect>): List<Float> =
            (1 until row.size).map { row[it].left - row[it - 1].right }

        val flow = compose.onNodeWithTag("detail-tag-module", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val region = compose.onNodeWithTag("detail-tag-region", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        // Mixed widths. The packer only starts a new row when the next tag no longer fits,
        // so every row but the last is full by construction and must share one justified
        // gap. Per-row gaps are the reported defect: rows that share a leading edge drift
        // apart by the gap difference at every tag, visible by the fourth tag.
        compose.runOnIdle {
            tags = listOf(
                "奇幻冒险异世界", "热血穿越系统", "日常魔法机甲", "末世甜宠悬疑",
                "奇幻", "冒险", "异世", "热血", "穿越", "系统",
                "日常", "魔法", "机甲", "末世", "甜宠", "悬疑",
                "奇幻", "冒险", "异世", "热血",
            )
        }
        compose.waitForIdle()
        val mixedRows = rowBounds()
        assertTrue("Expected at least three rows, found ${mixedRows.size}", mixedRows.size >= 3)
        val mixedGaps = mixedRows.map { gaps(it.second) }
        val fullRowGaps = mixedGaps.dropLast(1).flatten().distinct()
        assertTrue(
            "Every full row must share one justified gap, found $mixedGaps",
            fullRowGaps.size == 1,
        )

        mixedRows.forEachIndexed { index, (_, row) ->
            assertTrue(
                "Row $index must start at the flow's leading edge: ${row.first().left} vs ${flow.left}",
                abs(row.first().left - flow.left) <= 1f,
            )
            assertTrue(
                "Row $index must stay inside the outlined region",
                row.first().left >= region.left && row.last().right <= region.right,
            )
        }

        // Identical tags wrap into rows of equal content width, so the shared gap fills
        // each full row to the trailing edge: justified and column-aligned together.
        compose.runOnIdle {
            tags = listOf(
                "奇幻", "冒险", "异世", "热血", "穿越", "系统", "日常",
                "魔法", "机甲", "末世", "甜宠", "悬疑", "金丹", "剑修",
            )
        }
        compose.waitForIdle()
        val uniformRows = rowBounds()
        assertTrue("Expected two or more rows, found ${uniformRows.size}", uniformRows.size >= 2)
        val uniformGaps = uniformRows.map { gaps(it.second) }.flatten().distinct()
        assertTrue("Identical tags must share one gap, found $uniformGaps", uniformGaps.size == 1)
        uniformRows.dropLast(1).forEachIndexed { index, (_, row) ->
            assertTrue(
                "A full row of identical tags must reach the trailing edge, row $index ends at ${row.last().right} vs ${flow.right}",
                abs(row.last().right - flow.right) <= 1f,
            )
        }

        // Two tags leave most of the row empty, so the row is not full. It keeps the
        // minimum gap on the leading edge; stretching it to both edges is not alignment,
        // and a wider default here would cost row capacity.
        compose.runOnIdle { tags = listOf("奇幻", "冒险") }
        compose.waitForIdle()
        val sparse = rowBounds().single().second
        val sparseGap = gaps(sparse).single()
        assertTrue(
            "A sparse row must use the minimum gap, found ${sparseGap / density}dp",
            abs(sparseGap / density - 4f) <= 1.5f,
        )
        assertTrue(
            "A sparse row must not be stretched to the trailing edge: ${sparse.last().right} vs ${flow.right}",
            sparse.last().right < flow.right - 1f,
        )

        // A single tag cannot be justified: it keeps the region's leading edge.
        compose.runOnIdle { tags = listOf("奇幻") }
        compose.waitForIdle()
        val single = rowBounds().single().second
        assertTrue("A single tag row must start at the flow's leading edge", abs(single.single().left - flow.left) <= 1f)
        assertTrue("A single tag must not be stretched", single.single().right < flow.right - 1f)
    }


    // Packing and placement must use the same gap. Packing at a smaller gap than the one
    // actually placed lets a row that only just fitted overflow once the wider gap is
    // applied, and since the wrap decision was already taken the row never wraps - the
    // tags run past the region edge. That regression shipped once; this sweep is the
    // guard, because a single fixture at one width does not reach the boundary.
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tagRowsNeverOverflowTheOutlinedRegionAcrossWidthsAndCounts() {
        var tags by mutableStateOf(listOf("奇幻"))
        var viewportWidthDp by mutableStateOf(320)
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.ForcedSize(DpSize(viewportWidthDp.dp, 1400.dp)),
                ) {
                    MaterialTheme(typography = TsuyomiTypography) {
                        DetailTagActionsModule(
                            tags = tags,
                            enabled = true,
                            tagEditorOpen = false,
                            tagDraft = "",
                            onOpenTagEditor = {},
                            onTagDraftChange = {},
                            onDismissTagEditor = {},
                            onConfirmTag = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        val pool = listOf(
            "奇幻", "冒险", "异世", "热血", "穿越", "系统", "日常", "魔法",
            "机甲", "末世", "甜宠", "悬疑", "金丹", "剑修",
            "奇幻冒险异世界", "热血穿越系统流", "日常魔法机甲师",
        )
        var worst = 0f
        var worstCase = ""
        var worstPremature = 0f
        var worstPrematureCase = ""
        val minGapPx = org.tsuyomi.core.ui.theme.TsuyomiSpacing.Xs.value *
            InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        for (width in 300..430 step 5) {
            for (count in 2..14) {
                compose.runOnIdle { viewportWidthDp = width; tags = pool.take(count) }
                compose.waitForIdle()
                val flow = compose.onNodeWithTag("detail-tag-module", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val region = compose.onNodeWithTag("detail-tag-region", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val labels = compose.onAllNodesWithTag("detail-tag-label", useUnmergedTree = true)
                    .fetchSemanticsNodes().map { it.boundsInRoot }
                val grouped = sortedMapOf<Float, MutableList<androidx.compose.ui.geometry.Rect>>()
                labels.forEach { grouped.getOrPut(it.top) { mutableListOf() } += it }
                val rows = grouped.values.map { it.sortedBy { rect -> rect.left } }
                rows.forEachIndexed { index, row ->
                    val pastFlow = row.last().right - flow.right
                    val pastRegion = row.last().right - region.right
                    if (pastFlow > 1f || pastRegion > 1f) {
                        val over = maxOf(pastFlow, pastRegion)
                        if (over > worst) {
                            worst = over
                            worstCase = "width=${width}dp count=$count row=$index members=${row.size}"
                        }
                    }
                }
                // A row may only be broken when its successor genuinely cannot fit at the
                // packing gap. Breaking a row while the next tag would still have fitted
                // costs capacity: a six-tag row wraps with a visible hole beside it.
                rows.dropLast(1).forEachIndexed { index, row ->
                    val content: Float = row.fold(0f) { acc, rect -> acc + rect.width }
                    val packingWidth: Float = content + minGapPx * (row.size - 1).toFloat()
                    val needed: Float = packingWidth + minGapPx + rows[index + 1].first().width
                    val slack: Float = flow.width - needed
                    if (slack > 1f && slack > worstPremature) {
                        worstPremature = slack
                        worstPrematureCase =
                            "width=${width}dp count=$count row=$index members=${row.size} slack=${slack}px"
                    }
                }
            }
        }
        assertTrue(
            "Tag rows must not overflow the flow or the outlined region, worst ${worst}px at $worstCase",
            worst <= 1f,
        )
        assertTrue(
            "A row must not wrap while its successor still fits: $worstPrematureCase",
            worstPremature <= 1f,
        )
    }

    @Test
    fun tagEditingIsAvailableForAnAdmittedDetailWithoutLibraryMembership() {
        val book = sourceBook()
        var localState by mutableStateOf(DetailLocalState())
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
                MaterialTheme {
                    StandardBookDetailScreen(
                        state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "连载")),
                        directoryState = SourceBookState.Loading,
                        localState = localState,
                        mutation = null,
                        coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                        unreadOnly = false,
                        descending = false,
                        selectedChapterId = null,
                        cacheState = DetailCacheState(),
                        onCacheAction = {},
                        onSetRating = {},
                        onSearchAuthor = {},
                        onConfirmTag = {},
                        onToggleUnreadOnly = {},
                        onToggleOrder = {},
                        onSelectChapter = {},
                        onContinueReading = {},
                        onAddToLibrary = {},
                        onRequestRemoveFromLibrary = {},
                        onRetry = {},
                        onUseOfflineCache = {},
                        onOpenVerification = {},
                        onKeepDefaultLibrary = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("detail-add-tag").assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(!localState.inLibrary)
            localState = localState.copy(localTagsEditable = true)
        }
        compose.onNodeWithTag("detail-add-tag").assertIsEnabled()
    }

    @Test
    fun detailTopBarExposesWebsiteMoveAndRemoveActions() {
        var moved = false
        var removed = false
        compose.setContent {
            MaterialTheme {
                BookDetailTopBar(
                    title = "网站书籍",
                    inLibrary = true,
                    onNavigateUp = {},
                    onCacheDetail = {},
                    onRefresh = {},
                    onRemoveFromLibrary = {},
                    remoteRemoveAvailable = true,
                    remoteMoveAvailable = true,
                    onRemoveFromRemote = { removed = true },
                    onMoveRemote = { moved = true },
                )
            }
        }

        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("移动网站收藏").performClick()
        assertTrue(moved)
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("从网站收藏移除").performClick()
        assertTrue(removed)
    }

    private companion object {
        val standardTestEnvironment = DisplayEnvironment(
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

    private fun sourceBook(): SourceBookSummary = SourceBookSummary(
        identity = BookIdentity("org.tsuyomi.fixture", "book-1"),
        title = "分卷目录测试",
        author = "作者",
        coverUrl = null,
        canonicalUrl = "https://example.com/book/1",
    )

    private fun chapter(id: String, title: String, volume: String): SourceChapter = SourceChapter(
        chapterId = id,
        title = title,
        url = "https://example.com/chapter/$id",
        volumeTitle = volume,
    )
}
