/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextRange
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.locator.bookmarkPositionKey
import org.tsuyomi.shared.sourcecontract.ReaderNavigationTarget
import org.tsuyomi.shared.model.BookIdentity

class ReaderBookmarkInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bookmarkRowsKeepExactPositionsSeparateAndReturnTheirOriginalLocators() {
        val firstIdentity = DocumentIdentity(
            book = BookIdentity.Remote("org.tsuyomi.reader.test", "semantic-bookmarks"),
            contentId = "chapter-1", revision = "revision-1",
        )
        val first = ReaderLocator(
            document = firstIdentity,
            blockId = "paragraph-7",
            textAnchorDigest = "a".repeat(64),
            characterOffset = 8,
            chapterProgress = 0.2,
            capturedAt = Instant.EPOCH,
        )
        val second = first.copy(
            textAnchorDigest = "b".repeat(64),
            characterOffset = 42,
            chapterProgress = 0.8,
        )
        val legacy = ReaderLocator(
            document = firstIdentity.copy(contentId = "chapter-2"),
            chapterProgress = 0.0,
            capturedAt = Instant.EPOCH,
        )
        val bookmarks = mutableStateOf(listOf(first, second, legacy))
        val selected = mutableStateOf<ReaderLocator?>(null)
        val navigationTargets = listOf(
            ReaderNavigationTarget("chapter-1", "第一章：语义书签"),
            ReaderNavigationTarget("chapter-2", "第二章：旧版位置"),
        )

        composeRule.setContent {
            ReaderAuxiliaryTestTheme {
                ReaderAuxiliarySheet(
                    state = rememberReaderAuxiliarySheetState(firstIdentity.book, ReaderAuxiliaryTab.BOOKMARKS),
                    navigationTargets = navigationTargets,
                    currentContentId = "chapter-1",
                    bookmarks = bookmarks.value,
                    onDismiss = {},
                    onSelectContent = {},
                    onSelectBookmark = { selected.value = it },
                    onRemoveBookmark = { removed ->
                        bookmarks.value = bookmarks.value.filterNot {
                            it.bookmarkPositionKey() == removed.bookmarkPositionKey()
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("reader-bookmark-list").assertIsDisplayed()
        composeRule.onNodeWithText("paragraph-7 · 第 9 字").assertIsDisplayed()
        composeRule.onNodeWithText("paragraph-7 · 第 43 字").assertIsDisplayed()
        composeRule.onNodeWithText("旧版章节起点（降级定位）").assertIsDisplayed()

        composeRule.onNodeWithTag("reader-bookmark-row-${second.bookmarkPositionKey()}").performClick()
        composeRule.runOnIdle {
            assertEquals(second, selected.value)
            assertEquals(firstIdentity, selected.value?.document)
        }

        composeRule.onNodeWithTag("reader-bookmark-remove-${first.bookmarkPositionKey()}").performClick()
        composeRule.onNodeWithTag("reader-bookmark-row-${first.bookmarkPositionKey()}").assertDoesNotExist()
        composeRule.onNodeWithTag("reader-bookmark-row-${second.bookmarkPositionKey()}").assertExists()
    }

    @Test
    fun directoryAndSearchOfferChapterSelectionWithoutChapterBookmarkControls() {
        val selectedChapterIds = mutableListOf<String>()
        val navigationTargets = listOf(
            ReaderNavigationTarget("chapter-1", "第一章：目录定位"),
            ReaderNavigationTarget("chapter-2", "第二章：搜索定位"),
        )
        composeRule.setContent {
            ReaderAuxiliaryTestTheme {
                ReaderAuxiliarySheet(
                    state = rememberReaderAuxiliarySheetState(BookIdentity.Remote("org.tsuyomi.reader.test", "directory")),
                    navigationTargets = navigationTargets,
                    currentContentId = "chapter-1",
                    bookmarks = emptyList(),
                    onDismiss = {},
                    onSelectContent = { selectedChapterIds += it.contentId },
                    onSelectBookmark = {},
                    onRemoveBookmark = {},
                )
            }
        }

        composeRule.onNodeWithTag("reader-chapter-row-chapter-1").performClick()
        composeRule.onNodeWithTag("reader-chapter-bookmark-chapter-1").assertDoesNotExist()
        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithTag("reader-search-input").performTextInput("第二章")
        composeRule.onNodeWithTag("reader-search-input").performImeAction()
        composeRule.onNodeWithTag("reader-search-results").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-chapter-bookmark-chapter-2").assertDoesNotExist()
        composeRule.onNodeWithTag("reader-chapter-row-chapter-2").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("chapter-1", "chapter-2"), selectedChapterIds)
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun partialDirectoryCentersItsMiddleChapterInTheActuallyVisibleViewportAndKeepsItThroughExpansion() {
        val navigationTargets = List(25) { index ->
            ReaderNavigationTarget("chapter-$index", "第 ${index + 1} 章")
        }
        val current = navigationTargets[12]
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                ReaderAuxiliaryTestTheme {
                    ReaderAuxiliarySheet(
                        state = rememberReaderAuxiliarySheetState(BookIdentity.Remote("org.tsuyomi.reader.test", "directory")),
                        navigationTargets = navigationTargets,
                        currentContentId = current.contentId,
                        bookmarks = emptyList(),
                        onDismiss = {},
                        onSelectContent = {},
                        onSelectBookmark = {},
                        onRemoveBookmark = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("reader-expand-full-contents").assertIsDisplayed()
        val currentRow = composeRule.onNodeWithTag("reader-chapter-row-${current.contentId}")
        currentRow.assertIsDisplayed()
        val currentBounds = currentRow.fetchSemanticsNode().boundsInRoot
        val listBounds = composeRule.onNodeWithTag("reader-directory-list").fetchSemanticsNode().boundsInRoot
        val rootBounds = composeRule.onNode(isRoot() and hasAnyDescendant(hasTestTag("reader-directory-list")))
            .fetchSemanticsNode().boundsInRoot
        val actualVisibleTop = maxOf(listBounds.top, rootBounds.top)
        val actualVisibleBottom = minOf(listBounds.bottom, rootBounds.bottom)
        assertEquals(
            "Middle chapter must use the actual visible partial-list viewport, not hidden full-sheet height",
            (actualVisibleTop + actualVisibleBottom) / 2f,
            (currentBounds.top + currentBounds.bottom) / 2f,
            8f,
        )

        composeRule.onNodeWithTag("reader-expand-full-contents").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-expand-full-contents").assertDoesNotExist()
        currentRow.assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun partialDirectoryNaturallyClampsTheFirstAndLastChapterAtVisibleViewportBoundaries() {
        val navigationTargets = List(25) { index ->
            ReaderNavigationTarget("chapter-$index", "第 ${index + 1} 章")
        }
        val currentContentId = mutableStateOf(navigationTargets.first().contentId)
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                ReaderAuxiliaryTestTheme {
                    ReaderAuxiliarySheet(
                        state = rememberReaderAuxiliarySheetState(BookIdentity.Remote("org.tsuyomi.reader.test", "directory")),
                        navigationTargets = navigationTargets,
                        currentContentId = currentContentId.value,
                        bookmarks = emptyList(),
                        onDismiss = {},
                        onSelectContent = {},
                        onSelectBookmark = {},
                        onRemoveBookmark = {},
                    )
                }
            }
        }

        fun actualVisibleBounds(): Pair<Float, Float> {
            val listBounds = composeRule.onNodeWithTag("reader-directory-list").fetchSemanticsNode().boundsInRoot
            val rootBounds = composeRule.onNode(isRoot() and hasAnyDescendant(hasTestTag("reader-directory-list")))
                .fetchSemanticsNode().boundsInRoot
            return maxOf(listBounds.top, rootBounds.top) to minOf(listBounds.bottom, rootBounds.bottom)
        }

        val firstBounds = composeRule.onNodeWithTag("reader-chapter-row-${navigationTargets.first().contentId}")
            .fetchSemanticsNode().boundsInRoot
        val (firstVisibleTop, _) = actualVisibleBounds()
        assertTrue("First chapter must not scroll beyond the partial viewport start", firstBounds.top >= firstVisibleTop - 1f)

        composeRule.runOnIdle { currentContentId.value = navigationTargets.last().contentId }
        composeRule.waitForIdle()
        val lastBounds = composeRule.onNodeWithTag("reader-chapter-row-${navigationTargets.last().contentId}")
            .fetchSemanticsNode().boundsInRoot
        val (_, lastVisibleBottom) = actualVisibleBounds()
        assertTrue("Last chapter must not scroll beyond the partial viewport end", lastBounds.bottom <= lastVisibleBottom + 1f)
    }

    @Test
    fun directoryAndBookmarkRowsKeep48DpTouchTargetsAtDeviceDensity() {
        val chapter = ReaderNavigationTarget("chapter-1", "第一章：触控尺寸")
        val bookmark = ReaderLocator(
            document = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "bookmark-touch-target"), chapter.contentId),
            blockId = "paragraph-1",
            textAnchorDigest = "c".repeat(64),
            characterOffset = 0,
            capturedAt = Instant.EPOCH,
        )
        composeRule.setContent {
            ReaderAuxiliaryTestTheme {
                ReaderAuxiliarySheet(
                    state = rememberReaderAuxiliarySheetState(bookmark.document.book),
                    navigationTargets = listOf(chapter),
                    currentContentId = chapter.contentId,
                    bookmarks = listOf(bookmark),
                    onDismiss = {},
                    onSelectContent = {},
                    onSelectBookmark = {},
                    onRemoveBookmark = {},
                )
            }
        }

        val minimumPixels = with(composeRule.density) { 48.dp.toPx() }
        val directoryBounds = composeRule.onNodeWithTag("reader-chapter-row-${chapter.contentId}")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Directory target must be at least 48dp", directoryBounds.height >= minimumPixels - 0.5f)

        composeRule.onNodeWithText("书签").performClick()
        val bookmarkBounds = composeRule.onNodeWithTag("reader-bookmark-row-${bookmark.bookmarkPositionKey()}")
            .fetchSemanticsNode().boundsInRoot
        val removeBounds = composeRule.onNodeWithTag("reader-bookmark-remove-${bookmark.bookmarkPositionKey()}")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Bookmark row must be at least 48dp", bookmarkBounds.height >= minimumPixels - 0.5f)
        assertTrue("Bookmark remove target must be at least 48dp", removeBounds.height >= minimumPixels - 0.5f)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun expandingTheDirectoryKeepsTheExistingBackDismissBehavior() {
        val dismissals = AtomicInteger()
        val visible = mutableStateOf(true)
        val navigationTargets = List(25) { index ->
            ReaderNavigationTarget("chapter-$index", "第 ${index + 1} 章")
        }
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                ReaderAuxiliaryTestTheme {
                    val sheetState = rememberReaderAuxiliarySheetState(BookIdentity.Remote("org.tsuyomi.reader.test", "directory"))
                    if (visible.value) {
                        ReaderAuxiliarySheet(
                            state = sheetState,
                            navigationTargets = navigationTargets,
                            currentContentId = navigationTargets[12].contentId,
                            bookmarks = emptyList(),
                            onDismiss = {
                                dismissals.incrementAndGet()
                                visible.value = false
                            },
                            onSelectContent = {},
                            onSelectBookmark = {},
                            onRemoveBookmark = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("reader-expand-full-contents").performClick()
        pressBack()
        composeRule.waitUntil { dismissals.get() == 1 }
        composeRule.onNodeWithTag("reader-auxiliary-sheet").assertDoesNotExist()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun directoryDoesNotExposeAnUncenteredFirstFrameBeforeItsCurrentChapterIsPositioned() {
        val navigationTargets = List(25) { index ->
            ReaderNavigationTarget("first-frame-$index", "第 ${index + 1} 章")
        }
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                    ReaderAuxiliaryTestTheme {
                        ReaderAuxiliarySheet(
                            state = rememberReaderAuxiliarySheetState(BookIdentity.Remote("org.tsuyomi.reader.test", "directory")),
                            navigationTargets = navigationTargets,
                            currentContentId = navigationTargets[12].contentId,
                            bookmarks = emptyList(),
                            onDismiss = {},
                            onSelectContent = {},
                            onSelectBookmark = {},
                            onRemoveBookmark = {},
                        )
                    }
                }
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag("reader-directory-list").assertDoesNotExist()
            composeRule.mainClock.advanceTimeBy(48)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-directory-list").assertIsDisplayed()
        val currentBounds = composeRule.onNodeWithTag("reader-chapter-row-${navigationTargets[12].contentId}")
            .fetchSemanticsNode().boundsInRoot
        val listBounds = composeRule.onNodeWithTag("reader-directory-list").fetchSemanticsNode().boundsInRoot
        val rootBounds = composeRule.onNode(isRoot() and hasAnyDescendant(hasTestTag("reader-directory-list")))
            .fetchSemanticsNode().boundsInRoot
        val visibleTop = maxOf(listBounds.top, rootBounds.top)
        val visibleBottom = minOf(listBounds.bottom, rootBounds.bottom)
        assertEquals((visibleTop + visibleBottom) / 2f, (currentBounds.top + currentBounds.bottom) / 2f, 8f)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun auxiliarySheetKeepsItsTabViewportAndAnchorsAcrossPartialExpandedAndReopenedStates() {
        val navigationTargets = List(30) { index ->
            ReaderNavigationTarget(
                contentId = "chapter-$index",
                title = "第 ${index + 1} 章",

                sectionTitle = if (index < 10) "上卷" else "下卷",
            )
        }
        val bookmark = ReaderLocator(
            document = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.reader.test", "auxiliary-state"), navigationTargets[14].contentId),
            blockId = "paragraph-14",
            textAnchorDigest = "d".repeat(64),
            characterOffset = 6,
            capturedAt = Instant.EPOCH,
        )
        val visible = mutableStateOf(true)
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                ReaderAuxiliaryTestTheme {
                    val state = rememberReaderAuxiliarySheetState(BookIdentity.Remote("org.tsuyomi.reader.test", "auxiliary-state"))
                    if (visible.value) {
                        ReaderAuxiliarySheet(
                            state = state,
                            navigationTargets = navigationTargets,
                            currentContentId = navigationTargets[14].contentId,
                            bookmarks = listOf(bookmark),
                            onDismiss = { visible.value = false },
                            onSelectContent = {},
                            onSelectBookmark = {},
                            onRemoveBookmark = {},
                        )
                    }
                }
            }
        }

        val directory = composeRule.onNodeWithTag("reader-directory-list")
        directory.performScrollToIndex(11)
        composeRule.onNodeWithTag("reader-volume-boundary-section:10:下卷").assertIsDisplayed()
        val volumeBounds = composeRule.onNodeWithTag("reader-volume-boundary-section:10:下卷")
            .fetchSemanticsNode().boundsInRoot
        val directoryBounds = directory.fetchSemanticsNode().boundsInRoot
        assertEquals(directoryBounds.left, volumeBounds.left, 1f)
        assertEquals(directoryBounds.right, volumeBounds.right, 1f)
        directory.performScrollToIndex(20)
        composeRule.onNodeWithTag("reader-chapter-row-chapter-18").assertIsDisplayed()

        composeRule.onNodeWithText("书签").performClick()
        composeRule.onNodeWithTag("reader-bookmark-list").assertIsDisplayed()
        composeRule.onNodeWithText("目录").performClick()
        composeRule.onNodeWithTag("reader-expand-full-contents").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-expand-full-contents").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-expand-full-contents").assertDoesNotExist()
        composeRule.onNodeWithText("书签").performClick()
        composeRule.onNodeWithText("目录").performClick()
        composeRule.onNodeWithTag("reader-expand-full-contents").assertDoesNotExist()

        composeRule.onNodeWithText("书签").performClick()
        pressBack()
        composeRule.waitUntil { !visible.value }
        visible.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-bookmark-list").assertIsDisplayed()
        composeRule.onNodeWithText("目录").performClick()
        composeRule.onNodeWithTag("reader-expand-full-contents").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-chapter-row-chapter-18").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun einkAuxiliaryModesMeasureTheirSidebarAndPreserveDirectoryQueryAndBookmarkIdentity() {
        val book = BookIdentity.Remote("org.tsuyomi.reader.test", "eink-auxiliary-modes")
        val targets = List(40) { index -> ReaderNavigationTarget("chapter-$index", "第${index + 1}章", sectionTitle = "正文") }
        val bookmark = ReaderLocator(
            document = DocumentIdentity(book, targets[14].contentId),
            blockId = "paragraph-14", characterOffset = 6, textAnchorDigest = "e".repeat(64),
            chapterProgress = 0.2, capturedAt = Instant.EPOCH,
        )
        val selectedBookmark = AtomicReference<ReaderLocator?>()
        val sheetState = AtomicReference<ReaderAuxiliarySheetState>()
        val visible = mutableStateOf(true)
        val fontScale = mutableStateOf(1f)
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 640.dp))
                    .then(DeviceConfigurationOverride.FontScale(fontScale.value)),
            ) {
                ReaderAuxiliaryTestTheme(DisplayProfile.EINK) {
                    val state = rememberReaderAuxiliarySheetState(book)
                    SideEffect { sheetState.set(state) }
                    if (visible.value) {
                        ReaderAuxiliarySheet(
                            state = state, navigationTargets = targets, currentContentId = targets[14].contentId,
                            bookmarks = listOf(bookmark), onDismiss = { visible.value = false },
                            onSelectContent = {}, onSelectBookmark = { selectedBookmark.set(it) }, onRemoveBookmark = {},
                        )
                    }
                }
            }
        }
        val navigation = composeRule.onNodeWithTag("reader-eink-auxiliary-navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-eink-mode-contents").assertIsSelected()
        composeRule.onNodeWithTag("reader-chapter-row-${targets[14].contentId}").assertIsSelected()
        val label = composeRule.onNode(hasText("目录") and hasAnyAncestor(hasTestTag("reader-eink-auxiliary-navigation")), useUnmergedTree = true)
            .fetchSemanticsNode()
        val navBounds = navigation.fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag("reader-eink-auxiliary-content").fetchSemanticsNode().boundsInRoot
        assertTrue(navBounds.width <= label.boundsInRoot.width + with(label.layoutInfo.density) { 24.dp.toPx() } + 1f)
        assertTrue(contentBounds.left >= navBounds.right && contentBounds.width > navBounds.width * 2f)
        composeRule.onNode(hasText("下一页") and hasAnyAncestor(hasTestTag("reader-directory-pager")))
            .assertIsEnabled().performClick()
        val directoryAnchor = composeRule.runOnIdle { sheetState.get().contentsPagerState.currentAnchorKey }
        composeRule.onNodeWithTag("reader-eink-mode-search").performClick().assertIsSelected()
        composeRule.onNodeWithTag("reader-search-input").performTextInput("第2")
        composeRule.onNodeWithTag("reader-search-input").performSemanticsAction(SemanticsActions.SetSelection) { it(1, 1, false) }
        composeRule.onNodeWithTag("reader-eink-search-submit").performClick()
        composeRule.onNodeWithTag("reader-search-results-pager").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-eink-mode-bookmarks").performClick().assertIsSelected()
        composeRule.onNodeWithTag("reader-bookmark-row-${bookmark.bookmarkPositionKey()}").assertIsDisplayed().performClick()
        assertEquals(bookmark, selectedBookmark.get())
        composeRule.onNodeWithTag("reader-eink-mode-contents").performClick()
        composeRule.runOnIdle { assertEquals(directoryAnchor, sheetState.get().contentsPagerState.currentAnchorKey) }
        composeRule.onNodeWithTag("reader-eink-mode-search").performClick()
        val input = composeRule.onNodeWithTag("reader-search-input").fetchSemanticsNode().config
        assertEquals("第2", input[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(1), input[SemanticsProperties.TextSelectionRange])
        composeRule.runOnIdle { fontScale.value = 2f }
        listOf("contents", "bookmarks", "search").forEach { mode ->
            val button = composeRule.onNodeWithTag("reader-eink-mode-$mode").assertIsDisplayed()
            val node = button.fetchSemanticsNode()
            val minimum = with(node.layoutInfo.density) { 48.dp.toPx() }
            assertTrue(node.touchBoundsInRoot.width >= minimum && node.touchBoundsInRoot.height >= minimum)
        }
        val largeInput = composeRule.onNodeWithTag("reader-search-input").assertIsDisplayed().fetchSemanticsNode()
        val largePane = composeRule.onNodeWithTag("reader-eink-auxiliary-content").fetchSemanticsNode().boundsInRoot
        assertTrue(largeInput.boundsInRoot.left >= largePane.left && largeInput.boundsInRoot.right <= largePane.right)
        composeRule.onNodeWithTag("reader-eink-search-submit").assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange), useUnmergedTree = true)
            .fetchSemanticsNodes().isEmpty())
        composeRule.runOnIdle { visible.value = false }
        composeRule.runOnIdle { visible.value = true }
        composeRule.onNodeWithTag("reader-eink-mode-search").assertIsSelected()
        assertEquals("第2", composeRule.onNodeWithTag("reader-search-input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        composeRule.onNodeWithTag("reader-eink-mode-contents").performClick()
        composeRule.runOnIdle { assertEquals(directoryAnchor, sheetState.get().contentsPagerState.currentAnchorKey) }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun einkLongDirectoryReachesItsRealFirstAndLastPagesWithoutSequentialTraversal() {
        val book = BookIdentity.Remote("org.tsuyomi.reader.test", "eink-directory-endpoints")
        val targets = List(100) { index -> ReaderNavigationTarget("chapter-$index", "第${index + 1}章", sectionTitle = "正文") }
        val selected = AtomicReference<ReaderNavigationTarget?>()
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 640.dp))) {
                ReaderAuxiliaryTestTheme(DisplayProfile.EINK) {
                    ReaderAuxiliarySheet(
                        state = rememberReaderAuxiliarySheetState(book), navigationTargets = targets,
                        currentContentId = targets.first().contentId, bookmarks = emptyList(), onDismiss = {},
                        onSelectContent = { selected.set(it) }, onSelectBookmark = {}, onRemoveBookmark = {},
                    )
                }
            }
        }
        val footer = hasAnyAncestor(hasTestTag("reader-eink-pagination-controls"))
        val first = composeRule.onNode(hasText("首页") and footer)
        val last = composeRule.onNode(hasText("末页") and footer)
        last.assertIsDisplayed().assertIsEnabled().performClick()
        last.assertIsNotEnabled()
        composeRule.onNodeWithTag("reader-chapter-row-${targets.last().contentId}").assertIsDisplayed().performClick()
        assertEquals(targets.last(), selected.get())
        first.assertIsDisplayed().assertIsEnabled().performClick()
        first.assertIsNotEnabled()
        last.assertIsEnabled()
        composeRule.onNodeWithTag("reader-chapter-row-${targets.first().contentId}").assertIsDisplayed().performClick()
        assertEquals(targets.first(), selected.get())
    }

}

@Composable
private fun ReaderAuxiliaryTestTheme(profile: DisplayProfile = DisplayProfile.STANDARD, content: @Composable () -> Unit) {
    val environment = DisplayEnvironment(
        preferences = DisplayPreferences(
            displayPreference = if (profile == DisplayProfile.EINK) DisplayPreference.EINK else DisplayPreference.STANDARD,
            colorSchemePreference = ColorSchemePreference.LIGHT,
        ),
        effectiveProfile = profile,
        decisionReason = if (profile == DisplayProfile.EINK) DisplayDecisionReason.MANUAL_EINK else DisplayDecisionReason.MANUAL_STANDARD,
        detectedDeviceLabel = null,
        dynamicColorEligible = false,
        dynamicColorEffective = false,
        effectiveDarkTheme = false,
        motionPolicy = if (profile == DisplayProfile.EINK) MotionPolicy.INSTANT else MotionPolicy.STANDARD,
        redrawEpoch = 0,
    )
    DisplayEnvironmentProvider(environment) {
        TsuyomiTheme(environment, content = content)
    }
}
