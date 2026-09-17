/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.Density
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
import org.tsuyomi.shared.sourcecontract.SourceChapter

class ReaderBookmarkInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bookmarkRowsKeepExactPositionsSeparateAndReturnTheirOriginalLocators() {
        val firstIdentity = DocumentIdentity(
            sourceId = "org.tsuyomi.reader.test",
            remoteBookId = "semantic-bookmarks",
            contentId = "chapter-1",
            revision = "revision-1",
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
        val chapters = listOf(
            SourceChapter("chapter-1", "第一章：语义书签", "https://example.test/chapter-1"),
            SourceChapter("chapter-2", "第二章：旧版位置", "https://example.test/chapter-2"),
        )

        composeRule.setContent {
            ReaderAuxiliaryTestTheme {
                ReaderAuxiliarySheet(
                    state = rememberReaderAuxiliarySheetState(firstIdentity.sourceId, firstIdentity.remoteBookId, ReaderAuxiliaryTab.BOOKMARKS),
                    chapters = chapters,
                    currentChapterId = "chapter-1",
                    bookmarks = bookmarks.value,
                    onDismiss = {},
                    onSelectChapter = {},
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
        val chapters = listOf(
            SourceChapter("chapter-1", "第一章：目录定位", "https://example.test/chapter-1"),
            SourceChapter("chapter-2", "第二章：搜索定位", "https://example.test/chapter-2"),
        )
        composeRule.setContent {
            ReaderAuxiliaryTestTheme {
                ReaderAuxiliarySheet(
                    state = rememberReaderAuxiliarySheetState("org.tsuyomi.reader.test", "directory"),
                    chapters = chapters,
                    currentChapterId = "chapter-1",
                    bookmarks = emptyList(),
                    onDismiss = {},
                    onSelectChapter = { selectedChapterIds += it.chapterId },
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
        val chapters = List(25) { index ->
            SourceChapter("chapter-$index", "第 ${index + 1} 章", "https://example.test/chapter-$index")
        }
        val current = chapters[12]
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                ReaderAuxiliaryTestTheme {
                    ReaderAuxiliarySheet(
                        state = rememberReaderAuxiliarySheetState("org.tsuyomi.reader.test", "directory"),
                        chapters = chapters,
                        currentChapterId = current.chapterId,
                        bookmarks = emptyList(),
                        onDismiss = {},
                        onSelectChapter = {},
                        onSelectBookmark = {},
                        onRemoveBookmark = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("reader-expand-full-contents").assertIsDisplayed()
        val currentRow = composeRule.onNodeWithTag("reader-chapter-row-${current.chapterId}")
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
        val chapters = List(25) { index ->
            SourceChapter("chapter-$index", "第 ${index + 1} 章", "https://example.test/chapter-$index")
        }
        val currentChapterId = mutableStateOf(chapters.first().chapterId)
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                ReaderAuxiliaryTestTheme {
                    ReaderAuxiliarySheet(
                        state = rememberReaderAuxiliarySheetState("org.tsuyomi.reader.test", "directory"),
                        chapters = chapters,
                        currentChapterId = currentChapterId.value,
                        bookmarks = emptyList(),
                        onDismiss = {},
                        onSelectChapter = {},
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

        val firstBounds = composeRule.onNodeWithTag("reader-chapter-row-${chapters.first().chapterId}")
            .fetchSemanticsNode().boundsInRoot
        val (firstVisibleTop, _) = actualVisibleBounds()
        assertTrue("First chapter must not scroll beyond the partial viewport start", firstBounds.top >= firstVisibleTop - 1f)

        composeRule.runOnIdle { currentChapterId.value = chapters.last().chapterId }
        composeRule.waitForIdle()
        val lastBounds = composeRule.onNodeWithTag("reader-chapter-row-${chapters.last().chapterId}")
            .fetchSemanticsNode().boundsInRoot
        val (_, lastVisibleBottom) = actualVisibleBounds()
        assertTrue("Last chapter must not scroll beyond the partial viewport end", lastBounds.bottom <= lastVisibleBottom + 1f)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun directoryAndBookmarkRowsKeep48DpTouchTargetsAtOneAndTwoDensity() {
        val density = mutableStateOf(1f)
        val chapter = SourceChapter("chapter-1", "第一章：触控尺寸", "https://example.test/chapter-1")
        val bookmark = ReaderLocator(
            document = DocumentIdentity("org.tsuyomi.reader.test", "bookmark-touch-target", chapter.chapterId),
            blockId = "paragraph-1",
            textAnchorDigest = "c".repeat(64),
            characterOffset = 0,
            capturedAt = Instant.EPOCH,
        )
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                key(density.value) {
                    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides Density(density.value)) {
                        ReaderAuxiliaryTestTheme {
                            ReaderAuxiliarySheet(
                                state = rememberReaderAuxiliarySheetState(bookmark.document.sourceId, bookmark.document.remoteBookId),
                                chapters = listOf(chapter),
                                currentChapterId = chapter.chapterId,
                                bookmarks = listOf(bookmark),
                                onDismiss = {},
                                onSelectChapter = {},
                                onSelectBookmark = {},
                                onRemoveBookmark = {},
                            )
                        }
                    }
                }
            }
        }

        for (scale in listOf(1f, 2f)) {
            composeRule.runOnIdle { density.value = scale }
            composeRule.waitForIdle()
            val minimumPixels = 48f * scale
            val directoryBounds = composeRule.onNodeWithTag("reader-chapter-row-${chapter.chapterId}")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Directory target must be at least 48dp at ${scale}x", directoryBounds.height >= minimumPixels - 0.5f)

            composeRule.onNodeWithText("书签").performClick()
            val bookmarkBounds = composeRule.onNodeWithTag("reader-bookmark-row-${bookmark.bookmarkPositionKey()}")
                .fetchSemanticsNode().boundsInRoot
            val removeBounds = composeRule.onNodeWithTag("reader-bookmark-remove-${bookmark.bookmarkPositionKey()}")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Bookmark row must be at least 48dp at ${scale}x", bookmarkBounds.height >= minimumPixels - 0.5f)
            assertTrue("Bookmark remove target must be at least 48dp at ${scale}x", removeBounds.height >= minimumPixels - 0.5f)
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun expandingTheDirectoryKeepsTheExistingBackDismissBehavior() {
        val dismissals = AtomicInteger()
        val visible = mutableStateOf(true)
        val chapters = List(25) { index ->
            SourceChapter("chapter-$index", "第 ${index + 1} 章", "https://example.test/chapter-$index")
        }
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                ReaderAuxiliaryTestTheme {
                    val sheetState = rememberReaderAuxiliarySheetState("org.tsuyomi.reader.test", "directory")
                    if (visible.value) {
                        ReaderAuxiliarySheet(
                            state = sheetState,
                            chapters = chapters,
                            currentChapterId = chapters[12].chapterId,
                            bookmarks = emptyList(),
                            onDismiss = {
                                dismissals.incrementAndGet()
                                visible.value = false
                            },
                            onSelectChapter = {},
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
        val chapters = List(25) { index ->
            SourceChapter("first-frame-$index", "第 ${index + 1} 章", "https://example.test/first-frame-$index")
        }
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                    ReaderAuxiliaryTestTheme {
                        ReaderAuxiliarySheet(
                            state = rememberReaderAuxiliarySheetState("org.tsuyomi.reader.test", "directory"),
                            chapters = chapters,
                            currentChapterId = chapters[12].chapterId,
                            bookmarks = emptyList(),
                            onDismiss = {},
                            onSelectChapter = {},
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
        val currentBounds = composeRule.onNodeWithTag("reader-chapter-row-${chapters[12].chapterId}")
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
        val chapters = List(30) { index ->
            SourceChapter(
                chapterId = "chapter-$index",
                title = "第 ${index + 1} 章",
                url = "https://example.test/chapter-$index",
                volumeTitle = if (index < 10) "上卷" else "下卷",
            )
        }
        val bookmark = ReaderLocator(
            document = DocumentIdentity("org.tsuyomi.reader.test", "auxiliary-state", chapters[14].chapterId),
            blockId = "paragraph-14",
            textAnchorDigest = "d".repeat(64),
            characterOffset = 6,
            capturedAt = Instant.EPOCH,
        )
        val visible = mutableStateOf(true)
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))) {
                ReaderAuxiliaryTestTheme {
                    val state = rememberReaderAuxiliarySheetState(
                        sourceId = "org.tsuyomi.reader.test",
                        remoteBookId = "auxiliary-state",
                    )
                    if (visible.value) {
                        ReaderAuxiliarySheet(
                            state = state,
                            chapters = chapters,
                            currentChapterId = chapters[14].chapterId,
                            bookmarks = listOf(bookmark),
                            onDismiss = { visible.value = false },
                            onSelectChapter = {},
                            onSelectBookmark = {},
                            onRemoveBookmark = {},
                        )
                    }
                }
            }
        }

        val directory = composeRule.onNodeWithTag("reader-directory-list")
        directory.performScrollToIndex(11)
        composeRule.onNodeWithTag("reader-volume-boundary-volume:10:下卷").assertIsDisplayed()
        val volumeBounds = composeRule.onNodeWithTag("reader-volume-boundary-volume:10:下卷")
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

}

@Composable
private fun ReaderAuxiliaryTestTheme(content: @Composable () -> Unit) {
    val environment = DisplayEnvironment(
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
    DisplayEnvironmentProvider(environment) {
        TsuyomiTheme(environment, content = content)
    }
}
