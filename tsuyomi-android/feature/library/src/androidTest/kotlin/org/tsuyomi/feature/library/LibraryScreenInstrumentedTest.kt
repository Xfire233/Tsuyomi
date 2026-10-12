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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.longClick
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.test.platform.app.InstrumentationRegistry
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
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.librarydomain.LocalPublicationMetadata
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.model.CoverCardPresentation

@RunWith(AndroidJUnit4::class)
class LibraryScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun einkLocalSearchJumpsToTrueEndpointsWithoutSubmittingTheQueryDraft() {
        val entries = (1..40).map { entry("search-$it", "搜索书籍${it.toString().padStart(2, '0')}") }
        var openedBook: BookIdentity? = null
        var submissions = 0
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 480.dp))) {
                DisplayEnvironmentProvider(einkEnvironment) {
                    TsuyomiTheme(einkEnvironment) {
                        LibrarySearchScreen(
                            query = "未提交草稿",
                            searchQuery = "搜索书籍",
                            books = entries,
                            collections = emptyList(),
                            preferredCollectionId = null,
                            loading = false,
                            failure = null,
                            onQueryChange = {},
                            onSearch = { submissions++ },
                            onRetry = {},
                            onOpenCollection = {},
                            onOpenBook = { openedBook = it.book.identity },
                        )
                    }
                }
            }
        }
        val contentPager = hasAnyAncestor(hasTestTag("library-search-eink-pagination"))
        composeRule.waitUntil { composeRule.onNode(hasText("末页") and contentPager).isDisplayed() }
        val first = composeRule.onNode(hasText("首页") and contentPager)
        val last = composeRule.onNode(hasText("末页") and contentPager)
        first.assertIsNotEnabled()
        last.assertIsEnabled().performClick()
        composeRule.onNodeWithText(entries.last().book.title).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(entries.last().book.identity, openedBook) }
        last.assertIsNotEnabled()
        composeRule.onNode(hasText("下一页") and contentPager).assertIsNotEnabled()
        first.assertIsEnabled().performClick()
        composeRule.onNodeWithText(entries.first().book.title).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(entries.first().book.identity, openedBook); assertEquals(0, submissions) }
        first.assertIsNotEnabled()
        composeRule.onNode(hasText("上一页") and contentPager).assertIsNotEnabled()
        composeRule.onNodeWithText("未提交草稿").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun einkPagePlanProbesNeverActivateUnplacedLibraryCovers() {
        val entries = (1..40).map { entry("cover-probe-$it", "测量封面$it") }
        val activeCovers = mutableSetOf<BookIdentity>()
        val activatedCovers = mutableSetOf<BookIdentity>()
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 600.dp))) {
                DisplayEnvironmentProvider(einkEnvironment) {
                    TsuyomiTheme(einkEnvironment) {
                        LibraryScreen(
                            state = state(SystemLibraryFilter.ALL, entries).copy(isRootProjection = false, layout = LibraryLayout.LIST),
                            collections = emptyList(),
                            showNavigationNodes = false,
                            onSelectTab = {},
                            onOpenCollection = {},
                            onOpenBook = {},
                            onRetry = {},
                            onCoverVisibility = { entry, visible ->
                                if (visible) {
                                    activeCovers += entry.book.identity
                                    activatedCovers += entry.book.identity
                                } else activeCovers -= entry.book.identity
                            },
                        )
                    }
                }
            }
        }
        val controls = hasAnyAncestor(hasTestTag("library-eink-pagination"))
        composeRule.waitUntil { composeRule.onNode(hasText("末页") and controls).isDisplayed() }
        fun placedIdentities() = entries.filter {
            composeRule.onNodeWithTag(libraryBookTestTag(it.book.identity)).isDisplayed()
        }.map { it.book.identity }.toSet()
        val firstPage = placedIdentities()
        assertTrue(firstPage.isNotEmpty() && firstPage.size < entries.size)
        composeRule.runOnIdle {
            assertEquals(firstPage, activeCovers)
            assertEquals("Unplaced measurement probes must never announce visible covers", firstPage, activatedCovers)
        }
        composeRule.onNode(hasText("末页") and controls).performClick()
        val lastPage = placedIdentities()
        assertTrue(entries.last().book.identity in lastPage)
        composeRule.runOnIdle {
            assertEquals(lastPage, activeCovers)
            assertEquals(firstPage + lastPage, activatedCovers)
        }
        composeRule.onNode(hasText("首页") and controls).performClick()
        composeRule.runOnIdle { assertEquals(firstPage, activeCovers); assertEquals(firstPage + lastPage, activatedCovers) }
    }

    @Test
    fun einkBookLongPressAndSemanticLongClickSelectExactMembersInEveryLayout() {
        val first = entry("select-first", "第一本选择书")
        val second = entry("select-second", "第二本选择书")
        var library by mutableStateOf(state(SystemLibraryFilter.ALL, listOf(first, second)))
        var opens = 0
        composeRule.setContent {
            DisplayEnvironmentProvider(einkEnvironment) {
                TsuyomiTheme(einkEnvironment) {
                    LibraryScreen(
                        state = library, collections = emptyList(), showNavigationNodes = false,
                        onSelectTab = {}, onOpenCollection = {}, onOpenBook = { opens++ }, onRetry = {},
                        onLongPressBook = { library = library.copy(selectionKind = LibrarySelectionKind.BOOK, selectedBookIds = setOf(it)) },
                        onToggleBookSelection = { identity ->
                            library = library.copy(selectedBookIds = if (identity in library.selectedBookIds)
                                library.selectedBookIds - identity else library.selectedBookIds + identity)
                        },
                    )
                }
            }
        }
        for (layout in LibraryLayout.entries) {
            composeRule.runOnIdle { library = library.copy(layout = layout, selectionKind = null, selectedBookIds = emptySet()) }
            composeRule.onNodeWithTag(libraryBookTestTag(first.book.identity)).performTouchInput { longClick() }
            composeRule.runOnIdle {
                assertEquals(LibrarySelectionKind.BOOK, library.selectionKind)
                assertEquals(setOf(first.book.identity), library.selectedBookIds)
                assertEquals(0, opens)
            }
            composeRule.onNodeWithTag(libraryBookTestTag(second.book.identity)).performClick()
            composeRule.runOnIdle { assertEquals(setOf(first.book.identity, second.book.identity), library.selectedBookIds) }
            composeRule.onNodeWithTag(libraryBookTestTag(first.book.identity)).performClick()
            composeRule.runOnIdle { assertEquals(setOf(second.book.identity), library.selectedBookIds) }
            composeRule.onNodeWithContentDescription("选择${first.book.title}").assertDoesNotExist()
            composeRule.runOnIdle { library = library.copy(selectionKind = null, selectedBookIds = emptySet()) }
            composeRule.onNodeWithTag(libraryBookTestTag(second.book.identity))
                .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            composeRule.runOnIdle { assertEquals(setOf(second.book.identity), library.selectedBookIds) }
        }
    }

    @Test
    fun einkImportCentersIdleAndCompletedPrincipalActionAndKeepsPreviewActionsAfterContent() {
        var importState by mutableStateOf(LocalBookImportUiState())
        val actions = mutableListOf<LocalBookImportAction>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            DisplayEnvironmentProvider(einkEnvironment) {
                TsuyomiTheme(einkEnvironment) {
                    LocalBookImportScreen(importState, { actions += it }, Modifier.fillMaxSize().testTag("import-body"))
                }
            }
        }
        val choose = composeRule.onNodeWithText(context.getString(R.string.local_book_choose_files))
        repeat(2) { iteration ->
            if (iteration == 1) composeRule.runOnIdle {
                importState = LocalBookImportUiState(results = listOf(LocalBookImportResult("result", "book.epub", "完成", "publication")))
            }
            val body = composeRule.onNodeWithTag("import-body").fetchSemanticsNode().boundsInRoot
            val button = choose.fetchSemanticsNode().boundsInRoot
            assertTrue(kotlin.math.abs(body.center.x - button.center.x) <= 1f)
            assertTrue(kotlin.math.abs(body.center.y - button.center.y) <= 1f)
            choose.performClick()
        }
        composeRule.runOnIdle {
            assertEquals(listOf(LocalBookImportAction.ChooseFiles, LocalBookImportAction.ChooseFiles), actions)
            importState = importState.copy(fileName = "preview.epub", review = LocalBookImportReview(title = "预览书籍", format = "EPUB", ready = true))
        }
        val preview = composeRule.onNodeWithText("预览书籍").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val confirm = composeRule.onNodeWithText(context.getString(R.string.local_book_confirm_import)).assertIsEnabled()
        val cancel = composeRule.onNodeWithText(context.getString(R.string.local_book_cancel_current)).assertIsEnabled()
        assertTrue(confirm.fetchSemanticsNode().boundsInRoot.top >= preview.bottom)
        assertTrue(cancel.fetchSemanticsNode().boundsInRoot.top >= preview.bottom)
        confirm.performClick()
        cancel.performClick()
        composeRule.runOnIdle { assertEquals(listOf(LocalBookImportAction.Confirm, LocalBookImportAction.Cancel), actions.takeLast(2)) }
    }

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
    fun einkLibraryUsesDiscreteControlsForEveryProjectionAndKeepsLocalBooksAvailable() {
        var selectedTab by mutableStateOf(SystemLibraryFilter.ALL)
        val local = localEntry("00000000-0000-4000-8000-000000000001", "本地出版物")
        val states = mapOf(
            SystemLibraryFilter.ALL to state(SystemLibraryFilter.ALL, listOf(entry("shelf", "书架页书籍"))),
            SystemLibraryFilter.CONTINUE to state(SystemLibraryFilter.CONTINUE, listOf(local.copy(readerVisitedAt = Instant.EPOCH))),
            SystemLibraryFilter.READ_LATER to state(SystemLibraryFilter.READ_LATER, listOf(local.copy(readLater = true))),
        )

        composeRule.setContent {
            DisplayEnvironmentProvider(einkEnvironment) {
                TsuyomiTheme(einkEnvironment) {
                    LibraryScreen(
                        state = states.getValue(selectedTab),
                        primaryTabStates = states,
                        collections = emptyList(),
                        showNavigationNodes = true,
                        onSelectTab = { selectedTab = it },
                        onOpenCollection = {},
                        onOpenBook = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("library-primary-pager").assertDoesNotExist()
        composeRule.onNodeWithTag("library-primary-tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("tsuyomi-tab-CONTINUE").performClick()
        composeRule.waitUntil(5_000) { selectedTab == SystemLibraryFilter.CONTINUE }
        composeRule.onNodeWithText("本地出版物").assertIsDisplayed()
        composeRule.onNodeWithTag(libraryBookTestTag(local.book.identity)).assertIsDisplayed()

        composeRule.onNodeWithTag("tsuyomi-tab-READ_LATER").performClick()
        composeRule.waitUntil(5_000) { selectedTab == SystemLibraryFilter.READ_LATER }
        composeRule.onNodeWithText("本地出版物").assertIsDisplayed()
        composeRule.onNodeWithTag("tsuyomi-tab-READ_LATER").assertIsSelected()

        composeRule.onNodeWithTag("tsuyomi-tab-ALL").performClick()
        composeRule.waitUntil(5_000) { selectedTab == SystemLibraryFilter.ALL }
        composeRule.onNodeWithText("书架页书籍").assertIsDisplayed()
        composeRule.onNodeWithTag("library-eink-pagination").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun einkLibraryPagesTheCurrentProjectionWithExplicitBoundedControls() {
        val entries = (1..40).map { entry("page-$it", "分页书籍$it") }
        val states = mapOf(
            SystemLibraryFilter.ALL to state(SystemLibraryFilter.ALL, entries).copy(layout = LibraryLayout.LIST),
            SystemLibraryFilter.CONTINUE to state(SystemLibraryFilter.CONTINUE, emptyList()),
            SystemLibraryFilter.READ_LATER to state(SystemLibraryFilter.READ_LATER, emptyList()),
        )

        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 480.dp))
                .then(DeviceConfigurationOverride.FontScale(2f))) {
                DisplayEnvironmentProvider(einkEnvironment) {
                    TsuyomiTheme(einkEnvironment) {
                        LibraryScreen(
                            state = states.getValue(SystemLibraryFilter.ALL),
                            primaryTabStates = states,
                            collections = emptyList(),
                            showNavigationNodes = true,
                            onSelectTab = {},
                            onOpenCollection = {},
                            onOpenBook = {},
                            onRetry = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("library-eink-pagination").assertIsDisplayed()
        val contentPager = hasAnyAncestor(hasTestTag("library-eink-pagination"))
        composeRule.onNode(hasText("上一页") and contentPager).assertIsNotEnabled()
        val first = composeRule.onNode(hasText("首页") and contentPager)
        val last = composeRule.onNode(hasText("末页") and contentPager)
        first.assertIsNotEnabled()
        last.assertIsEnabled().performClick()
        composeRule.onNodeWithTag(libraryBookTestTag(entries.last().book.identity)).assertIsDisplayed()
        last.assertIsNotEnabled()
        composeRule.onNode(hasText("下一页") and contentPager).assertIsNotEnabled()
        first.assertIsEnabled().performClick()
        composeRule.onNodeWithTag(libraryBookTestTag(entries.first().book.identity)).assertIsDisplayed()
        first.assertIsNotEnabled()
        composeRule.onNode(hasText("上一页") and contentPager).assertIsNotEnabled()
        composeRule.onNode(hasText("下一页") and contentPager).assertIsEnabled().performClick()
        composeRule.onNode(hasText("上一页") and contentPager).assertIsEnabled().performClick()
        val visited = mutableSetOf<BookIdentity>()
        fun collectVisibleEntries() {
            entries.forEach { entry ->
                val tag = libraryBookTestTag(entry.book.identity)
                if (composeRule.onNode(hasTestTag(tag)).isDisplayed()) {
                    visited += entry.book.identity
                }
            }
        }
        var pageTransitions = 0
        while (true) {
            collectVisibleEntries()
            val next = composeRule.onNode(hasText("下一页") and contentPager)
            if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
            next.performClick()
            pageTransitions++
            assertTrue("Pager did not advance within the finite result set", pageTransitions < entries.size)
        }
        assertTrue("Expected the finite fixture to span multiple pages", pageTransitions > 1)
        assertEquals(entries.map { it.book.identity }.toSet(), visited)
        composeRule.onNode(hasText("上一页") and contentPager).assertIsEnabled().performClick()
        composeRule.onNode(hasText("下一页") and contentPager).assertIsEnabled()
    }


    @OptIn(ExperimentalTestApi::class)
    @Test
    fun einkCollectionPageSurvivesProfileChangesWithoutChangingTheOpenedBook() {
        var environment by mutableStateOf(einkEnvironment)
        val entries = (1..25).map { entry("collection-$it", "集合书籍$it") }
        val collectionState = state(SystemLibraryFilter.ALL, entries).copy(isRootProjection = false, layout = LibraryLayout.LIST)
        var openedBook: BookIdentity? = null
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 480.dp))) {
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        LibraryScreen(
                            state = collectionState,
                            collections = emptyList(),
                            currentCollectionId = "collection",
                            showNavigationNodes = false,
                            onOpenCollection = {},
                            onOpenBook = { openedBook = it.book.identity },
                            onRetry = {},
                        )
                    }
                }
            }
        }
        val target = composeRule.onNodeWithTag(libraryBookTestTag(entries[14].book.identity))
        var pages = 0
        while (!target.isDisplayed()) {
            composeRule.onNodeWithText("下一页").assertIsEnabled().performClick()
            assertTrue("Collection book was unreachable", ++pages < entries.size * 3)
        }
        composeRule.runOnIdle { environment = standardEnvironment }
        composeRule.waitForIdle()
        composeRule.runOnIdle { environment = einkEnvironment }
        composeRule.waitForIdle()
        target.assertIsDisplayed().performClick()
        assertEquals(entries[14].book.identity, openedBook)
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
                        onRetry = {},
                    )
                }
            }
        }

        val book = composeRule.onNodeWithTag(libraryBookTestTag(shelfBook.book.identity))
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
            identity = book.book.identity as BookIdentity.Remote,
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
            val remoteIdentity = book.book.identity as BookIdentity.Remote
            val action = composeRule.onNodeWithTag("library-update-actions-${remoteIdentity.sourceId}-${remoteIdentity.remoteBookId}")
            val target = action.fetchSemanticsNode().boundsInRoot
            val card = composeRule.onNodeWithTag(libraryBookTestTag(book.book.identity))
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
            composeRule.onNodeWithTag(libraryBookTestTag(book.book.identity))
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
                                onRetry = {},
                            )
                        }
                    }
                }
            }
        }
        val card = composeRule.onNodeWithTag(libraryBookTestTag(book.book.identity))
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
                identity = BookIdentity.Remote("fixture.library.pager", "locator-only"),
                locator = org.tsuyomi.shared.locator.ReaderLocator(
                    document = org.tsuyomi.shared.locator.DocumentIdentity(
                        BookIdentity.Remote("fixture.library.pager", "locator-only"),
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

    @Test
    fun smartRuleControlsKeepCodesTagsAndFractionalRatings() {
        var minimumModeHeightPx = 0f
        var tree by mutableStateOf(SmartDraftNode.Group(true, listOf(
            SmartDraftNode.Condition(SmartConditionDraft(field = SmartField.SOURCE)),
        )))
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    minimumModeHeightPx = with(LocalDensity.current) { 48.dp.toPx() }
                    CollectionRuleScreen(
                        title = "示例规则",
                        tree = tree,
                        collections = emptyList(),
                        sourceIds = listOf("source-a", "source-b"),
                        sourceLabels = mapOf("source-a" to "同名书源", "source-b" to "同名书源"),
                        tagChoices = listOf("科幻", "奇幻"),
                        nameError = false,
                        invalidConditions = emptySet(),
                        saving = false,
                        onTitleChange = {},
                        onTreeChange = { tree = it },
                        onSave = {},
                    )
                }
            }
        }

        val modeBounds = composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
        ).fetchSemanticsNode().boundsInRoot
        val saveBounds = composeRule.onNodeWithText("保存智能收藏夹").fetchSemanticsNode().boundsInRoot
        assertTrue("Mode switch must stay paired on the left: $modeBounds vs $saveBounds",
            modeBounds.right < saveBounds.left + saveBounds.width * 0.75f)
        assertTrue("Mode target must remain 48dp high: $modeBounds", modeBounds.height >= minimumModeHeightPx - 1f)
        composeRule.onNodeWithText("选择值").performClick()
        composeRule.onNodeWithText("同名书源（source-b）").performClick()
        composeRule.onNodeWithText("同名书源（source-b）").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("source-b", tree.conditions().single().value) }

        composeRule.onNodeWithText("按来源筛选").performClick()
        composeRule.onNodeWithText("连载状态").performClick()
        composeRule.onNodeWithText("选择值").performClick()
        composeRule.onNodeWithText("已完结").performClick()
        composeRule.onNodeWithText("已完结").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("completed", tree.conditions().single().value) }

        composeRule.onNodeWithText("按连载状态筛选").performClick()
        composeRule.onNodeWithText("阅读状态").performClick()
        composeRule.onNodeWithText("选择值").performClick()
        composeRule.onNodeWithText("阅读中").performClick()
        composeRule.onNodeWithText("阅读中").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("reading", tree.conditions().single().value) }

        composeRule.runOnIdle {
            tree = SmartDraftNode.Group(true, listOf(SmartDraftNode.Condition(
                SmartConditionDraft(field = SmartField.TAG, value = "旧标签"),
            )))
        }
        composeRule.onNodeWithText("已选 1 个标签：旧标签").performClick()
        composeRule.onNodeWithText("科幻").performClick()
        composeRule.onNodeWithText("奇幻").performClick()
        composeRule.onNodeWithText("选好了").performClick()
        composeRule.runOnIdle {
            assertEquals(setOf("旧标签", "科幻", "奇幻"), parseSmartTerms(tree.conditions().single().value)?.toSet())
        }
        composeRule.onNodeWithText("已选 3 个标签：旧标签、科幻…").assertIsDisplayed()
        composeRule.onNodeWithText("包含任一已选标签").assertIsDisplayed()
        composeRule.onNodeWithText("包含任一已选标签").performClick()
        composeRule.onNodeWithText("包含所有已选标签").assertIsDisplayed()
        composeRule.onNodeWithText("按标签筛选").performClick()
        composeRule.onNodeWithText("标签").performClick()
        composeRule.runOnIdle {
            assertEquals(setOf("旧标签", "科幻", "奇幻"), parseSmartTerms(tree.conditions().single().value)?.toSet())
            assertTrue(tree.conditions().single().matchAllTags)
        }

        composeRule.runOnIdle {
            tree = SmartDraftNode.Group(true, listOf(SmartDraftNode.Condition(
                SmartConditionDraft(field = SmartField.RATING, value = "3.5,5"),
            )))
        }
        composeRule.onNodeWithContentDescription("最高评分：5.0")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        composeRule.runOnIdle {
            val bounds = tree.conditions().single().value.split(',')
            assertEquals("3.5", bounds[0])
            assertEquals(4f, bounds[1].toFloat())
        }
    }

    @Test
    fun folderDeletePreviewExplainsBothPoliciesAndCancelDoesNotConfirm() {
        var subtree by mutableStateOf(false)
        var cancelled = false
        var confirmed = false
        composeRule.setContent {
            DisplayEnvironmentProvider(environment) {
                TsuyomiTheme(environment) {
                    LibraryScreen(
                        state = state(SystemLibraryFilter.ALL, emptyList()).copy(
                            selectionKind = LibrarySelectionKind.COLLECTION,
                            selectedCollectionIds = setOf("parent"),
                            selectionDialog = LibrarySelectionDialog.CONFIRM_REMOVE,
                        ),
                        collections = emptyList(),
                        showNavigationNodes = false,
                        onOpenCollection = {},
                        onOpenBook = {},
                        onRetry = {},
                        deletionFolderCount = if (subtree) 3 else 1,
                        deletionMembershipCount = if (subtree) 4 else 2,
                        deletionSubtree = subtree,
                        onDeletionPolicyChange = { subtree = it },
                        onDismissSelectionDialog = { cancelled = true },
                        onRemoveSelection = { confirmed = true },
                    )
                }
            }
        }

        composeRule.onNodeWithText("将删除 1 个收藏夹，移除 2 条书籍所属关系。", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("删除所选及全部子收藏夹").performClick()
        composeRule.onNodeWithText("将删除 3 个收藏夹，移除 4 条书籍所属关系。", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle {
            assertTrue(cancelled)
            assertTrue(!confirmed)
        }
    }

    private fun localEntry(id: String, title: String): LibraryEntry = LibraryEntry(
        book = LibraryBook(
            identity = BookIdentity.Local(id),
            title = title,
            addedAt = Instant.EPOCH,
            metadataUpdatedAt = Instant.EPOCH,
            localPublication = LocalPublicationMetadata(
                format = LocalPublicationFormat.EPUB,
                originalSha256 = "a".repeat(64),
                originalSizeBytes = 1,
                originalAvailable = true,
            ),
        ),
        libraryAddedAt = Instant.EPOCH,
        rating = null,
        localTags = emptySet(),
        sourceAvailable = false,
        reconciliation = null,
    )

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
            identity = BookIdentity.Remote("fixture.library.pager", id),
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
                identity = BookIdentity.Remote("fixture.library.pager", id),
                locator = org.tsuyomi.shared.locator.ReaderLocator(
                    document = org.tsuyomi.shared.locator.DocumentIdentity(BookIdentity.Remote("fixture.library.pager", id), "chapter-1"),
                    bookProgress = it,
                    capturedAt = Instant.EPOCH,
                ),
            )
        },
        readerVisitedAt = Instant.EPOCH.takeIf { readerVisited },
    )

        val einkEnvironment = environment.copy(
            preferences = DisplayPreferences(DisplayPreference.EINK, ColorSchemePreference.LIGHT),
            effectiveProfile = DisplayProfile.EINK,
            decisionReason = DisplayDecisionReason.MANUAL_EINK,
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
