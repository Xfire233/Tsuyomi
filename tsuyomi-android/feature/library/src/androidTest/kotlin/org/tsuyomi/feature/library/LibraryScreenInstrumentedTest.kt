/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
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
import org.tsuyomi.core.preferences.ColorSchemePreference
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.core.ui.components.CoverCardPresentationProvider
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.model.CoverCardPresentation

@RunWith(AndroidJUnit4::class)
class LibraryScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryPagerTapAndSwipeSelectTheSameLibraryContext() {
        var selectedTab by mutableStateOf(SystemLibraryFilter.ALL)
        val states = mapOf(
            SystemLibraryFilter.ALL to state(
                filter = SystemLibraryFilter.ALL,
                entries = listOf(entry("shelf", "书架页书籍")),
            ),
            SystemLibraryFilter.CONTINUE to state(
                filter = SystemLibraryFilter.CONTINUE,
                entries = emptyList(),
            ),
            SystemLibraryFilter.READ_LATER to state(
                filter = SystemLibraryFilter.READ_LATER,
                entries = listOf(entry("later", "稍后页书籍", readLater = true)),
            ),
        )

        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    LibraryScreen(
                        state = states.getValue(selectedTab),
                        primaryTabStates = states,
                        collections = emptyList(),
                        showNavigationNodes = true,
                        onSelectTab = { selectedTab = it },
                        onOpenCollection = {},
                        onOpenBook = {},
                        onCreateCollection = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("tsuyomi-tab-CONTINUE").performClick()
        composeRule.waitUntil(5_000) { selectedTab == SystemLibraryFilter.CONTINUE }
        composeRule.onNodeWithText("没有继续阅读的书籍").assertIsDisplayed()

        composeRule.onNodeWithTag("library-primary-pager").performTouchInput {
            down(center)
            moveBy(Offset(-center.x * 1.5f, 0f), delayMillis = 100)
            up()
        }
        composeRule.waitUntil(5_000) { selectedTab == SystemLibraryFilter.READ_LATER }
        composeRule.onNodeWithText("稍后页书籍").assertIsDisplayed()
        val pagerBounds = composeRule.onNodeWithTag("library-primary-pager")
            .fetchSemanticsNode().boundsInRoot
        val selectedContentBounds = composeRule.onNodeWithText("稍后页书籍")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(selectedContentBounds.left >= pagerBounds.left)
        assertTrue(selectedContentBounds.right <= pagerBounds.right)
        assertEquals(SystemLibraryFilter.READ_LATER, selectedTab)
    }


    @Test
    fun primaryPagerShortCommitReturnFlingAndReversalSettleWithAlignedSelection() {
        var selectedTab by mutableStateOf(SystemLibraryFilter.ALL)
        val states = mapOf(
            SystemLibraryFilter.ALL to state(SystemLibraryFilter.ALL, listOf(entry("shelf", "书架页书籍"))),
            SystemLibraryFilter.CONTINUE to state(SystemLibraryFilter.CONTINUE, listOf(entry("continue", "继续页书籍", progress = 0.4))),
            SystemLibraryFilter.READ_LATER to state(
                SystemLibraryFilter.READ_LATER,
                listOf(entry("later", "稍后页书籍", readLater = true)),
            ),
        )

        composeRule.setContent {
            DisplayEnvironmentProvider(standardEnvironment) {
                TsuyomiTheme(standardEnvironment) {
                    LibraryScreen(
                        state = states.getValue(selectedTab),
                        primaryTabStates = states,
                        collections = emptyList(),
                        showNavigationNodes = true,
                        onSelectTab = { selectedTab = it },
                        onOpenCollection = {},
                        onOpenBook = {},
                        onCreateCollection = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val pager = composeRule.onNodeWithTag("library-primary-pager")
        val pagerBounds = pager.fetchSemanticsNode().boundsInRoot
        fun assertSelectedContent(label: String) {
            composeRule.onNodeWithTag("tsuyomi-tab-${selectedTab.name}").assertIsSelected()
            val content = composeRule.onNodeWithText(label, useUnmergedTree = true)
            val contentBounds = content.fetchSemanticsNode().boundsInRoot
            val pageBounds = composeRule.onNodeWithTag("library-primary-page-${selectedTab.name}")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$label is not visible: content=$contentBounds page=$pageBounds pager=$pagerBounds", content.isDisplayed())
            assertTrue("$label is outside $pagerBounds: $contentBounds", contentBounds.left >= pagerBounds.left)
            assertTrue("$label is outside $pagerBounds: $contentBounds", contentBounds.right <= pagerBounds.right)
            assertEquals("Selected page is horizontally displaced: $pageBounds in $pagerBounds", pagerBounds.left, pageBounds.left, 1f)
            assertEquals("Selected page does not fill the viewport: $pageBounds in $pagerBounds", pagerBounds.right, pageBounds.right, 1f)
        }

        composeRule.mainClock.autoAdvance = false
        try {
            pager.performTouchInput {
                down(center)
                moveBy(Offset(-center.x * 0.4f, 0f), delayMillis = 240)
            }
            val draggedPageBounds = composeRule.onNodeWithTag("library-primary-page-ALL")
                .fetchSemanticsNode().boundsInRoot
            pager.performTouchInput { up() }
            composeRule.mainClock.advanceTimeBy(180)
            val returningPageBounds = composeRule.onNodeWithTag("library-primary-page-ALL")
                .fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Sub-threshold return did not continue toward rest: $draggedPageBounds to $returningPageBounds",
                returningPageBounds.right > draggedPageBounds.right,
            )
            assertTrue(
                "Sub-threshold return jumped instead of settling: $returningPageBounds in $pagerBounds",
                returningPageBounds.right > pagerBounds.left && returningPageBounds.right < pagerBounds.right,
            )
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals(SystemLibraryFilter.ALL, selectedTab) }
            assertSelectedContent("书架页书籍")

            pager.performTouchInput {
                down(Offset(width * 0.85f, center.y))
                moveBy(Offset(-width * 0.7f, 0f), delayMillis = 600)
            }
            composeRule.mainClock.advanceTimeByFrame()
            pager.performTouchInput {
                up()
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals(SystemLibraryFilter.CONTINUE, selectedTab) }
            assertSelectedContent("继续页书籍")

            pager.performTouchInput {
                swipe(start = center, end = Offset(center.x - width * 0.15f, center.y), durationMillis = 60)
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals(SystemLibraryFilter.READ_LATER, selectedTab) }
            assertSelectedContent("稍后页书籍")

            pager.performTouchInput {
                down(center)
                moveBy(Offset(center.x * 0.7f, 0f), delayMillis = 160)
                moveBy(Offset(-center.x * 0.45f, 0f), delayMillis = 160)
                up()
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals(SystemLibraryFilter.READ_LATER, selectedTab) }
            assertSelectedContent("稍后页书籍")

            pager.performTouchInput {
                down(center)
                moveBy(Offset(center.x * 0.6f, 0f), delayMillis = 240)
                up()
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals(SystemLibraryFilter.CONTINUE, selectedTab) }
            assertSelectedContent("继续页书籍")

            pager.performTouchInput {
                down(center)
                moveBy(Offset(center.x * 0.6f, 0f), delayMillis = 240)
                up()
            }
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.runOnIdle { assertEquals(SystemLibraryFilter.ALL, selectedTab) }
            assertSelectedContent("书架页书籍")
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun activeBookDragCannotSelectAnotherPrimaryPagerPage() {
        var selectedTab by mutableStateOf(SystemLibraryFilter.ALL)
        val shelfBook = entry("drag-shelf", "拖动中的书架书籍")
        val states = mapOf(
            SystemLibraryFilter.ALL to state(SystemLibraryFilter.ALL, listOf(shelfBook)),
            SystemLibraryFilter.CONTINUE to state(SystemLibraryFilter.CONTINUE, emptyList()),
            SystemLibraryFilter.READ_LATER to state(
                SystemLibraryFilter.READ_LATER,
                listOf(entry("drag-later", "不应切换到此页", readLater = true)),
            ),
        )

        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    LibraryScreen(
                        state = states.getValue(selectedTab),
                        primaryTabStates = states,
                        collections = emptyList(),
                        showNavigationNodes = true,
                        onSelectTab = { selectedTab = it },
                        onOpenCollection = {},
                        onOpenBook = {},
                        onCreateCollection = {},
                        onRetry = {},
                    )
                }
            }
        }

        val book = composeRule.onNodeWithTag("library-book-${shelfBook.book.identity.sourceId}-${shelfBook.book.identity.remoteBookId}")
        book.performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveBy(Offset(0f, 80f), delayMillis = 120)
        }
        composeRule.onNodeWithTag("library-drag-preview").assertExists()
        book.performTouchInput {
            moveBy(Offset(-1_000f, 0f), delayMillis = 120)
            up()
        }

        composeRule.waitForIdle()
        assertEquals(SystemLibraryFilter.ALL, selectedTab)
    }
    @Test
    fun continueProjectionRequiresReaderAdmissionAndIncludesCompletedVisits() {
        val unpinnedVisit = entry("reader-open", "未收藏的阅读记录", readerVisited = true).copy(localMembership = false)
        val completedVisit = entry("reader-completed", "已读完的阅读记录", progress = 1.0)
            .copy(readerVisitedAt = Instant.EPOCH.plusSeconds(1))
        val metadataOnly = entry("metadata-only", "只浏览详情", progress = 0.4, readerVisited = false)

        val projected = LibraryUiState(
            entries = listOf(metadataOnly, completedVisit, unpinnedVisit),
            loading = false,
            filter = SystemLibraryFilter.CONTINUE,
            sortMode = LibrarySortMode.RECENT,
            sortDescending = true,
            isRootProjection = false,
        ).projectedEntries()

        assertEquals(listOf(completedVisit.book.identity, unpinnedVisit.book.identity), projected.map { it.book.identity })
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun wideGridUpdateMenuRetainsItsFullHitTargetWithoutCollidingWithTheCount() {
        val book = entry("wide-updated", "操作边界验证")
        val update = org.tsuyomi.shared.librarydomain.UnresolvedUpdate(
            identity = book.book.identity,
            title = book.book.title,
            anchor = "anchor",
            chapters = emptyList(),
            newChapterIds = listOf("chapter-2", "chapter-3"),
            lastUpdatedDate = null,
            detectedAt = 0L,
        )
        var library by mutableStateOf(
            state(SystemLibraryFilter.ALL, listOf(book, entry("second", "第二本"), entry("third", "第三本")))
                .copy(updates = mapOf(book.book.identity to update)),
        )
        var fontScale by mutableStateOf(1f)
        var minimumTargetPx = 0f
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 800.dp))
                    .then(DeviceConfigurationOverride.FontScale(fontScale)),
            ) {
                minimumTargetPx = with(LocalDensity.current) { 48.dp.toPx() }
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        CoverCardPresentationProvider(CoverCardPresentation.WIDE) {
                            LibraryScreen(
                                state = library,
                                collections = emptyList(),
                                showNavigationNodes = false,
                                onSelectTab = {},
                                onOpenCollection = {},
                                onOpenBook = {},
                                onCreateCollection = {},
                                onRetry = {},
                                onLongPressBook = { identity ->
                                    library = library.copy(selectionKind = LibrarySelectionKind.BOOK, selectedBookIds = setOf(identity))
                                },
                            )
                        }
                    }
                }
            }
        }
        for (scale in listOf(1f, 2f)) {
            composeRule.runOnIdle { fontScale = scale }
            val action = composeRule.onNodeWithTag("library-update-actions-${book.book.identity.sourceId}-${book.book.identity.remoteBookId}")
            val target = action.fetchSemanticsNode().boundsInRoot
            val card = composeRule.onNodeWithTag("library-book-${book.book.identity.sourceId}-${book.book.identity.remoteBookId}")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Update target width ${target.width} is below $minimumTargetPx", target.width >= minimumTargetPx - 1f)
            assertTrue("Update target height ${target.height} is below $minimumTargetPx", target.height >= minimumTargetPx - 1f)
            assertTrue(target.left >= card.left && target.right <= card.right && target.top >= card.top && target.bottom <= card.bottom)
            val badge = composeRule.onNodeWithText("+2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val icon = composeRule.onNodeWithContentDescription("${book.book.title} 的更新操作", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue("Update icon overlaps the count", badge.bottom <= icon.top || badge.right <= icon.left || icon.right <= badge.left)
            action.performTouchInput { click(Offset(width - 1f, centerY)) }
            composeRule.onNodeWithText("忽略当前更新").assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            composeRule.onNodeWithTag("library-book-${book.book.identity.sourceId}-${book.book.identity.remoteBookId}")
                .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            val selected = composeRule.onNodeWithContentDescription("已选择", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val selectedBadge = composeRule.onNodeWithText("+2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Selected icon overlaps the update count", selectedBadge.bottom <= selected.top)
            composeRule.runOnIdle { library = library.copy(selectionKind = null, selectedBookIds = emptySet()) }
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun wideGridDragPreviewKeepsItsStatusReadableAtLargeFont() {
        val book = entry("wide-drag", "大字号拖动预览")
        val library = state(SystemLibraryFilter.ALL, listOf(book))
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 800.dp))
                    .then(DeviceConfigurationOverride.FontScale(2f)),
            ) {
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        CoverCardPresentationProvider(CoverCardPresentation.WIDE) {
                            LibraryScreen(
                                state = library,
                                collections = emptyList(),
                                showNavigationNodes = false,
                                onSelectTab = {},
                                onOpenCollection = {},
                                onOpenBook = {},
                                onCreateCollection = {},
                                onRetry = {},
                            )
                        }
                    }
                }
            }
        }
        val card = composeRule.onNodeWithTag("library-book-${book.book.identity.sourceId}-${book.book.identity.remoteBookId}")
        card.performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveBy(Offset(0f, 80f), delayMillis = 120)
        }
        try {
            val preview = composeRule.onNodeWithTag("library-drag-preview-grid-content")
            preview.assertIsDisplayed()
            val status = composeRule.onNode(
                hasText("未开始") and hasAnyAncestor(hasTestTag("library-drag-preview-grid-content")),
                useUnmergedTree = true,
            )
            status.assertIsDisplayed()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            status.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layoutResult = layouts.single()
            assertEquals("Status text must remain on one line", 1, layoutResult.lineCount)
            assertEquals(
                "Status text must remain fully represented",
                layoutResult.layoutInput.text.length,
                layoutResult.getLineEnd(0),
            )
            val previewBounds = preview.fetchSemanticsNode().boundsInRoot
            val statusBounds = status.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Status text must not be ellipsized: preview=$previewBounds, status=$statusBounds",
                !layoutResult.isLineEllipsized(0),
            )
        } finally {
            card.performTouchInput { up() }
        }
    }

    @Test
    fun everyLibraryLayoutShowsTruthfulDurableProgressState() {
        var layout by mutableStateOf(LibraryLayout.GRID)
        val locatorOnly = entry("locator-only", "缺少全书比例").copy(
            progress = org.tsuyomi.shared.librarydomain.ReadingProgress(
                identity = BookIdentity("fixture.library.pager", "locator-only"),
                locator = org.tsuyomi.shared.locator.ReaderLocator(
                    document = org.tsuyomi.shared.locator.DocumentIdentity(
                        "fixture.library.pager",
                        "locator-only",
                        "chapter-2",
                    ),
                    blockId = "paragraph-4",
                    characterOffset = 7,
                    capturedAt = Instant.EPOCH,
                ),
            ),
            readerVisitedAt = Instant.EPOCH,
        )
        val entries = listOf(locatorOnly, entry("partial", "部分进度", progress = 0.42), entry("finished", "完成进度", progress = 1.0))

        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    LibraryScreen(
                        state = state(SystemLibraryFilter.ALL, entries).copy(layout = layout),
                        collections = emptyList(),
                        showNavigationNodes = false,
                        onOpenCollection = {},
                        onOpenBook = {},
                        onCreateCollection = {},
                        onRetry = {},
                    )
                }
            }
        }

        LibraryLayout.entries.forEach { targetLayout ->
            composeRule.runOnIdle { layout = targetLayout }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("阅读中").assertIsDisplayed()
            composeRule.onNodeWithText("读至 42%").assertIsDisplayed()
            composeRule.onNodeWithText("已读完").assertIsDisplayed()
            composeRule.onNodeWithText("未开始").assertDoesNotExist()
        }
    }

    private fun state(filter: SystemLibraryFilter, entries: List<LibraryEntry>) = LibraryUiState(
        entries = entries,
        loading = false,
        filter = filter,
        isRootProjection = filter == SystemLibraryFilter.ALL,
    )

    private fun entry(
        id: String,
        title: String,
        readLater: Boolean = false,
        progress: Double? = null,
        readerVisited: Boolean = progress != null,
    ): LibraryEntry = LibraryEntry(
        book = LibraryBook(
            identity = BookIdentity("fixture.library.pager", id),
            title = title,
            addedAt = Instant.EPOCH,
            metadataUpdatedAt = Instant.EPOCH,
        ),
        libraryAddedAt = Instant.EPOCH,
        rating = null,
        localTags = emptySet(),
        sourceAvailable = true,
        reconciliation = null,
        readLater = readLater,
        progress = progress?.let {
            org.tsuyomi.shared.librarydomain.ReadingProgress(
                identity = BookIdentity("fixture.library.pager", id),
                locator = org.tsuyomi.shared.locator.ReaderLocator(
                    document = org.tsuyomi.shared.locator.DocumentIdentity("fixture.library.pager", id, "chapter-1"),
                    bookProgress = it,
                    capturedAt = Instant.EPOCH,
                ),
            )
        },
        readerVisitedAt = Instant.EPOCH.takeIf { readerVisited },
    )

    private companion object {
        val environment = DisplayEnvironment(
            preferences = DisplayPreferences(DisplayPreference.STANDARD, ColorSchemePreference.LIGHT),
            effectiveProfile = DisplayProfile.STANDARD,
            decisionReason = DisplayDecisionReason.MANUAL_STANDARD,
            detectedDeviceLabel = null,
            dynamicColorEligible = false,
            dynamicColorEffective = false,
            effectiveDarkTheme = false,
            motionPolicy = MotionPolicy.INSTANT,
            redrawEpoch = 0,
        )
        val standardEnvironment = environment.copy(motionPolicy = MotionPolicy.STANDARD)
    }
}
