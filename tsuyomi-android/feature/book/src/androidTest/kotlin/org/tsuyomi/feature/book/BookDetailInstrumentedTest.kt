/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
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
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayPreferences
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
            var unreadOnly by remember { mutableStateOf(false) }
            MaterialTheme {
                StandardBookDetailScreen(
                    state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "连载")),
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
                    onSetRating = {},
                    onSearchAuthor = {},
                    onAddTag = {},
                    onToggleUnreadOnly = { unreadOnly = !unreadOnly },
                    onToggleOrder = {},
                    onSelectChapter = {},
                    onContinueReading = {},
                    onAddToLibrary = {},
                    onRetry = {},
                    onUseOfflineCache = {},
                    onOpenVerification = {},
                )
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
    fun unresolvedAddCannotBeLocallyUnlocked() {
        val book = sourceBook()
        var operation by mutableStateOf("ADD")
        compose.setContent {
            DisplayEnvironmentProvider(standardTestEnvironment) {
                MaterialTheme {
                    StandardBookDetailScreen(
                        state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "连载")),
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
                        onSetRating = {},
                        onSearchAuthor = {},
                        onAddTag = {},
                        onToggleUnreadOnly = {},
                        onToggleOrder = {},
                        onSelectChapter = {},
                        onContinueReading = {},
                        onAddToLibrary = {},
                        onRetry = {},
                        onUseOfflineCache = {},
                        onOpenVerification = {},
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
                                onOpenDestinations = {},
                                destinationMenuExpanded = false,
                                onDestinationMenuExpandedChange = {},
                                destinationMenuContent = {},
                            )
                            DetailTagActionsModule(
                                tags = listOf("奇幻"),
                                enabled = localState.inLibrary,
                                onAddTag = {},
                            )
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
        assertTrue(abs(narrowRating.right - narrowSplit.right) <= 1f)
        assertTrue(abs(bounds("detail-rating-band").height - 36f * density) <= 1f)
        assertTrue(abs(narrowSplit.right - bounds("detail-identity-module").right) <= 1f)
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
        assertTrue(bounds("detail-rating-star-3-glyph").height <= 20f * density + 1f)
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
        compose.setContent {
            MaterialTheme(typography = TsuyomiTypography) {
                StandardBookDetailScreen(
                    state = SourceBookState.Content(SourceBookDetail(sourceBook(), "简介", emptyList(), null, null)),
                    directoryState = SourceBookState.Content(
                        SourceDirectory(sourceBook().identity, listOf(chapter("c1", "第一章", "第一卷"))),
                    ),
                    localState = DetailLocalState(),
                    mutation = null,
                    coverState = CoverUiState.Fallback(FallbackSpec("测试", null)),
                    unreadOnly = false,
                    descending = false,
                    selectedChapterId = null,
                    onSetRating = {},
                    onSearchAuthor = {},
                    onAddTag = {},
                    onToggleUnreadOnly = {},
                    onToggleOrder = {},
                    onSelectChapter = {},
                    onContinueReading = {},
                    onAddToLibrary = {},
                    onRetry = {},
                    onUseOfflineCache = {},
                    onOpenVerification = {},
                    destinationMenuExpanded = false,
                    destinationMessage = "已加入默认书架，目标移动尚未完成",
                    partialMoveTargetName = "特别收藏",
                    onRetryMoveOnly = { retryCount += 1 },
                )
            }
        }

        compose.onNodeWithText("已加入默认书架，目标移动尚未完成").assertIsDisplayed()
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
        compose.setContent {
            var menuExpanded by remember { mutableStateOf(false) }
            var localState by remember { mutableStateOf(DetailLocalState()) }
            MaterialTheme(typography = TsuyomiTypography) {
                StandardBookDetailScreen(
                    state = SourceBookState.Content(SourceBookDetail(book, description, emptyList(), "已完结", "2026-09-04")),
                    directoryState = SourceBookState.Content(
                        SourceDirectory(book.identity, listOf(chapter("c1", "第一章", "第一卷"))),
                    ),
                    localState = localState,
                    mutation = null,
                    coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                    unreadOnly = false,
                    descending = false,
                    selectedChapterId = null,
                    onSetRating = { localState = localState.copy(rating = it) },
                    onSearchAuthor = { searchedAuthor = it; authorSearchCount++ },
                    onAddTag = {},
                    onToggleUnreadOnly = {},
                    onToggleOrder = {},
                    onSelectChapter = {},
                    onContinueReading = {},
                    onAddToLibrary = { localState = localState.copy(inLibrary = true) },
                    onOpenDestinations = { destinationsOpened = true },
                    destinationMenuExpanded = menuExpanded,
                    onDestinationMenuExpandedChange = { menuExpanded = it },
                    destinationMenuContent = { dismissMenu ->
                        BookDestinationMenu(
                            readLater = localState.readLater,
                            shortcutPinned = false,
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
                            onToggleShortcut = {},
                            onToggleCollection = {},
                            onApplyWebsite = {},
                            onDismiss = dismissMenu,
                        )
                    },
                    onRetry = {},
                    onUseOfflineCache = {},
                    onOpenVerification = {},
                )
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("上次更新：2026-09-04").assertIsDisplayed()
        val authorText = compose.onNodeWithTag("detail-author").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single()
        val authorLink = authorText.getLinkAnnotations(0, authorText.length).single().item as LinkAnnotation.Clickable
        assertTrue(authorLink.styles?.style?.color == Color(0xFF4A6E8A))
        assertTrue(authorLink.styles?.style?.textDecoration == null)
        compose.onNodeWithText("尚未开始").assertDoesNotExist()
        compose.onNodeWithText("已有阅读进度").assertDoesNotExist()
        assertTrue(authorSearchCount == 0)
        compose.onNodeWithTag("detail-author").performClick()
        compose.waitForIdle()
        assertTrue(searchedAuthor == book.author && authorSearchCount == 1)
        compose.onNodeWithTag("detail-publication-status", useUnmergedTree = true).assertIsDisplayed()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val coverBounds = compose.onNodeWithTag("detail-cover").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(coverBounds.width - 135f * density) <= 1f)
        assertTrue(kotlin.math.abs(coverBounds.height - 180f * density) <= 1f)

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
            MaterialTheme(typography = TsuyomiTypography) {
                StandardBookDetailScreen(
                    state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "已完结")),
                    directoryState = SourceBookState.Content(
                        SourceDirectory(book.identity, listOf(chapter("c1", "第一章", "第一卷"))),
                    ),
                    localState = DetailLocalState(),
                    mutation = null,
                    coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                    unreadOnly = false,
                    descending = false,
                    selectedChapterId = null,
                    onSetRating = {},
                    onSearchAuthor = {},
                    onAddTag = {},
                    onToggleUnreadOnly = {},
                    onToggleOrder = {},
                    onSelectChapter = {},
                    onContinueReading = {},
                    onAddToLibrary = {},
                    onRetry = {},
                    onUseOfflineCache = {},
                    onOpenVerification = {},
                )
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("detail-last-updated").assertDoesNotExist()
        compose.onNodeWithText("尚未开始").assertDoesNotExist()
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

    @Test
    fun simpleWebsiteDestinationShowsOneAggregateTarget() {
        var appliedTargetId: String? = null
        compose.setContent {
            MaterialTheme {
                DropdownMenu(expanded = true, onDismissRequest = {}) {
                    BookDestinationMenu(
                        readLater = false,
                        shortcutPinned = false,
                        collections = emptyList(),
                        remoteTargets = listOf(
                            RemoteTarget("default", "默认书架", null, "folder"),
                            RemoteTarget("favorites", "特别收藏", null, "folder"),
                        ),
                        selectedRemoteTargetId = "default",
                        loadingRemoteTargets = false,
                        websiteGroupingEnabled = false,
                        onToggleReadLater = {},
                        onToggleShortcut = {},
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
    fun directoryFabHidesDuringScrollAndReturnsWhenScrollStops() {
        val book = sourceBook()
        val chapters = (1..80).map { chapter("c$it", "第 $it 章", "第一卷") }
        compose.setContent {
            MaterialTheme {
                StandardBookDetailScreen(
                    state = SourceBookState.Content(SourceBookDetail(book, "简介", emptyList(), "连载")),
                    directoryState = SourceBookState.Content(SourceDirectory(book.identity, chapters)),
                    localState = DetailLocalState(inLibrary = true, progressChapterId = "c1"),
                    mutation = null,
                    coverState = CoverUiState.Fallback(FallbackSpec(book.title, null)),
                    unreadOnly = false,
                    descending = false,
                    selectedChapterId = null,
                    onSetRating = {},
                    onSearchAuthor = {},
                    onAddTag = {},
                    onToggleUnreadOnly = {},
                    onToggleOrder = {},
                    onSelectChapter = {},
                    onContinueReading = {},
                    onAddToLibrary = {},
                    onRetry = {},
                    onUseOfflineCache = {},
                    onOpenVerification = {},
                )
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
