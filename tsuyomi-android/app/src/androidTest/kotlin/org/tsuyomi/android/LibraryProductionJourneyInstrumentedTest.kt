/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.yield
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.database.LibraryBook
import org.tsuyomi.core.database.CollectionKind
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.ReadingProgress
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.feature.library.projectedEntries

@RunWith(AndroidJUnit4::class)
class LibraryProductionJourneyInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val identity = BookIdentity("fixture.production.library", "journey-read-later")
    private val behaviorNewer = BookIdentity("fixture.production.library", "behavior-newer")
    private val selectionCollectionTitle = "批量选择收藏夹"
    private val rootBatchCollectionTitle = "根书架批量收藏夹"
    private val behaviorOlder = BookIdentity("fixture.production.library", "behavior-older")
    private val behaviorUnstarted = BookIdentity("fixture.production.library", "behavior-unstarted")
    private val behaviorCollectionId = "behavior-collection"
    private val searchCollectionId = "local-search-collection"

    private val libraryPreferences
        get() = (composeRule.activity.application as TsuyomiApplication).libraryPreferencesRepository

    @Before
    fun resetLibraryUpdateFilter() {
        runBlocking {
            libraryPreferences.updateShowUpdatesOnly(false)
        }
    }

    @After
    fun removeFixtureBook() {
        runBlocking {
            val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
            repository.collections().filter {
                it.title == selectionCollectionTitle ||
                    it.title == rootBatchCollectionTitle
            }.forEach {
                repository.deleteCollection(it.collectionId)
            }
            repository.deleteCollection(behaviorCollectionId)
            repository.deleteCollection(searchCollectionId)
            listOf(identity, behaviorNewer, behaviorOlder, behaviorUnstarted).forEach {
                repository.removeFromLibrary(it)
            }
        }
    }

    @Test
    fun standard_library_promotes_atlas_surface_over_interim_ui() {
        val application = composeRule.activity.application as TsuyomiApplication
        val title = "生产书架旅程"
        runBlocking {
            application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
            application.libraryRepository.removeFromLibrary(identity)
        }
        waitForText("书架")

        assertTrue(composeRule.onAllNodesWithText("本地藏书").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("library-shortcut-shelf").assertDoesNotExist()
        assertTrue(composeRule.onAllNodesWithText("书架").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("tsuyomi-tab-CONTINUE").assertIsDisplayed()
        composeRule.onNodeWithTag("tsuyomi-tab-READ_LATER").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("刷新").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("搜索").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("切换布局；当前为网格").performClick()
        composeRule.onNodeWithContentDescription("切换布局；当前为列表").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("新建收藏夹").assertIsDisplayed()
        composeRule.onNodeWithText("标签").performClick()
        waitForText("还没有本地标签")
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForText("书架")

        runBlocking {
            application.libraryRepository.addToLibrary(
                LibraryBook(
                    identity = identity,
                    title = title,
                    addedAt = Instant.EPOCH,
                    metadataUpdatedAt = Instant.EPOCH,
                ),
            )
            application.libraryRepository.setReadLater(identity, true)
        }
        composeRule.activityRule.scenario.recreate()
        waitForText(title)

        composeRule.onNodeWithTag("tsuyomi-tab-READ_LATER").performClick()
        waitForText(title)
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        waitForText("书架")
    }

    @Test
    fun library_search_is_live_recommended_and_local_only() {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        runBlocking {
            application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
            repository.deleteCollection(searchCollectionId)
            repository.removeFromLibrary(behaviorNewer)
            repository.removeFromLibrary(behaviorOlder)
            repository.addToLibrary(book(behaviorNewer, "本地搜索目标"))
            repository.addToLibrary(book(behaviorOlder, "集合内书籍"))
            repository.setLocalTags(behaviorNewer, setOf("离线标签"))
            repository.createCollection(
                LibraryCollection(
                    collectionId = searchCollectionId,
                    kind = CollectionKind.MANUAL,
                    title = "搜索目标收藏夹",
                    parentCollectionId = null,
                    displayOrder = Long.MAX_VALUE,
                ),
            )
            assertTrue(repository.addManualMembership(searchCollectionId, behaviorOlder))
        }
        Phase2SourceGateway.resetOperationCounts()
        waitForText("书架")
        composeRule.activityRule.scenario.recreate()
        waitForText("搜索目标收藏夹")
        composeRule.onNodeWithText("搜索目标收藏夹").performClick()
        waitForText("集合内书籍")
        composeRule.onNodeWithContentDescription("搜索").performClick()
        waitForText("搜索书架")
        waitForText("最近阅读与加入")
        composeRule.onNodeWithText("搜索目标收藏夹").assertExists()
        assertEquals(0, Phase2SourceGateway.searchRequestCount())

        composeRule.onNode(hasSetTextAction()).performTextReplacement("搜索目标收藏夹")
        waitForText("手动集合")
        assertEquals(0, Phase2SourceGateway.searchRequestCount())

        composeRule.onNode(hasSetTextAction()).performTextReplacement("不会保留的旧查询")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("离线标签")
        waitForText("本地搜索目标")
        composeRule.onNodeWithText("没有找到“不会保留的旧查询”").assertDoesNotExist()
        assertEquals(0, Phase2SourceGateway.searchRequestCount())
        composeRule.activityRule.scenario.recreate()
        waitForText("本地搜索目标")
        assertEquals(0, Phase2SourceGateway.searchRequestCount())
    }

    @Test
    fun standard_library_removes_recent_node_but_preserves_recent_sort_and_child_counts() {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val newerAt = Instant.parse("2090-01-01T00:00:00Z")
        val olderAt = Instant.parse("2080-01-01T00:00:00Z")
        runBlocking {
            application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
            repository.deleteCollection(behaviorCollectionId)
            listOf(behaviorNewer, behaviorOlder, behaviorUnstarted).forEach { repository.removeFromLibrary(it) }
            repository.addToLibrary(book(behaviorNewer, "最近阅读·新"))
            repository.addToLibrary(book(behaviorOlder, "最近阅读·旧"))
            repository.addToLibrary(book(behaviorUnstarted, "尚未阅读"))
            repository.saveProgress(progress(behaviorNewer, newerAt, 0.4))
            repository.saveProgress(progress(behaviorOlder, olderAt, 1.0))
            repository.createCollection(
                LibraryCollection(
                    collectionId = behaviorCollectionId,
                    kind = CollectionKind.MANUAL,
                    title = "行为测试收藏夹",
                    parentCollectionId = null,
                    displayOrder = Long.MAX_VALUE,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                ),
            )
            assertTrue(repository.addManualMembership(behaviorCollectionId, behaviorOlder))
        }

        waitForText("书架")
        composeRule.activityRule.scenario.recreate()
        waitForText("最近阅读·新")
        val expectedRootCount = runBlocking { repository.libraryEntries().size }

        assertTrue(composeRule.onAllNodesWithContentDescription("最近阅读").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("书籍排序：智能 · 正序").performClick()
        composeRule.onNodeWithText("书籍排序依据 · 最近阅读").performClick()
        composeRule.onAllNodesWithText("完成").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("书籍排序：最近阅读 · 正序").performClick()
        composeRule.onNodeWithText("顺序 · 倒序").performClick()

        val newerBounds = composeRule.onNodeWithText("最近阅读·新").fetchSemanticsNode().boundsInRoot
        val olderBounds = composeRule.onNodeWithText("最近阅读·旧").fetchSemanticsNode().boundsInRoot
        val unstartedBounds = composeRule.onNodeWithText("尚未阅读").fetchSemanticsNode().boundsInRoot
        assertTrue(
            newerBounds.top < olderBounds.top ||
                (newerBounds.top == olderBounds.top && newerBounds.left < olderBounds.left),
        )
        assertTrue(
            olderBounds.top < unstartedBounds.top ||
                (olderBounds.top == unstartedBounds.top && olderBounds.left < unstartedBounds.left),
        )
        composeRule.onNodeWithText("$expectedRootCount 本").assertExists()

        composeRule.onNodeWithText("行为测试收藏夹").performClick()
        waitForText("最近阅读·旧")
        composeRule.onNodeWithText("1 本").assertExists()
        assertTrue(composeRule.onAllNodesWithText("最近阅读·新").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForText("书架")
        composeRule.onNodeWithText("$expectedRootCount 本").assertExists()
        composeRule.onNodeWithTag("library-shortcut-shelf").assertDoesNotExist()
        composeRule.onNodeWithTag("library-root-node-collection:$behaviorCollectionId").assertExists()
        composeRule.onNodeWithTag("library-book-surface").assertExists()
    }

    @Test
    fun standard_library_long_press_enters_and_exits_selection_mode() {
        val application = composeRule.activity.application as TsuyomiApplication
        runBlocking {
            application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
            listOf(behaviorNewer, behaviorOlder).forEach { application.libraryRepository.removeFromLibrary(it) }
            application.libraryRepository.addToLibrary(book(behaviorNewer, "选择测试 A"))
            application.libraryRepository.addToLibrary(book(behaviorOlder, "选择测试 B"))
        }
        waitForText("书架")
        composeRule.activityRule.scenario.recreate()
        waitForText("选择测试 A")

        val first = composeRule.onNodeWithTag("library-book-${behaviorNewer.sourceId}-${behaviorNewer.remoteBookId}")
        val second = composeRule.onNodeWithTag("library-book-${behaviorOlder.sourceId}-${behaviorOlder.remoteBookId}")
        first.performSemanticsAction(SemanticsActions.OnLongClick)
        waitForText("已选 1 项")
        first.assertIsSelected()
        composeRule.onNodeWithContentDescription("全选").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("用所选新建收藏夹").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("加入收藏夹").fetchSemanticsNode()

        first.performSemanticsAction(SemanticsActions.OnLongClick)
        waitForText("已选 1 项")
        second.performClick()
        waitForText("已选 2 项")
        first.performClick()
        waitForText("已选 1 项")

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        waitForText("书架")
        assertTrue(composeRule.onAllNodesWithText("已选 1 项").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithContentDescription("刷新").fetchSemanticsNode()
    }


    @Test
    fun standard_library_touch_slop_and_drag_upgrade_follow_atlas_contract() {
        val application = composeRule.activity.application as TsuyomiApplication
        runBlocking {
            application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
            listOf(behaviorNewer, behaviorOlder).forEach { application.libraryRepository.removeFromLibrary(it) }
            application.libraryRepository.addToLibrary(book(behaviorNewer, "拖放测试 A"))
            application.libraryRepository.addToLibrary(book(behaviorOlder, "拖放测试 B"))
        }
        waitForText("书架")
        composeRule.activityRule.scenario.recreate()
        waitForText("拖放测试 A")

        val firstTag = "library-book-${behaviorNewer.sourceId}-${behaviorNewer.remoteBookId}"
        val secondTag = "library-book-${behaviorOlder.sourceId}-${behaviorOlder.remoteBookId}"
        val first = composeRule.onNodeWithTag(firstTag)
        first.performTouchInput {
            down(center)
            moveBy(Offset(0f, -120f))
            up()
        }
        assertTrue(composeRule.onAllNodesWithText("已选 1 项").fetchSemanticsNodes().isEmpty())

        first.performTouchInput { longClick() }
        waitForText("已选 1 项")
        val firstBounds = first.fetchSemanticsNode().boundsInRoot
        val secondBounds = composeRule.onNodeWithTag(secondTag).fetchSemanticsNode().boundsInRoot
        first.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(secondBounds.center - firstBounds.topLeft, delayMillis = 100)
            up()
        }
        waitForText("用所选书籍新建收藏夹")
        composeRule.onNodeWithText("取消").performClick()
        waitForText("已选 2 项")
        composeRule.onNodeWithContentDescription("退出选择").performClick()
        waitForText("书架")
    }

    @Test
    fun library_controller_bulk_actions_are_local_atomic_and_scope_aware() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        repository.collections().filter { it.title == selectionCollectionTitle }.forEach {
            repository.deleteCollection(it.collectionId)
        }
        listOf(behaviorNewer, behaviorOlder).forEach { repository.removeFromLibrary(it) }
        repository.addToLibrary(book(behaviorNewer, "批量操作 A"))
        repository.addToLibrary(book(behaviorOlder, "批量操作 B"))
        val controller = LibraryFlowController(repository, libraryPreferences)
        controller.reload("failed")

        controller.longPressBook(behaviorNewer)
        controller.toggleBookSelection(behaviorOlder)
        assertEquals(setOf(behaviorNewer, behaviorOlder), controller.state.selectedBookIds)
        assertTrue(controller.createCollectionFromSelection(selectionCollectionTitle, "failed"))
        val collection = repository.collections().single { it.title == selectionCollectionTitle }
        assertEquals(
            setOf(behaviorNewer, behaviorOlder),
            repository.collectionEntries(collection.collectionId).mapTo(linkedSetOf()) { it.book.identity },
        )
        assertTrue(controller.state.selectedBookIds.isEmpty())

        controller.selectCollection(collection.collectionId)
        controller.reload("failed")
        controller.longPressBook(behaviorNewer)
        assertTrue(controller.removeSelection("failed"))
        assertEquals(
            listOf(behaviorOlder),
            repository.collectionEntries(collection.collectionId).map { it.book.identity },
        )
        assertTrue(repository.libraryEntry(behaviorNewer) != null)

        controller.restoreLibraryHome()
        controller.longPressBook(behaviorNewer)
        assertTrue(controller.removeSelection("failed"))
        assertTrue(repository.libraryEntry(behaviorNewer) == null)
        assertTrue(repository.libraryEntry(behaviorOlder) != null)
    }
    @Test
    fun library_controller_restores_root_projection_without_collection_flash() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        repository.deleteCollection(behaviorCollectionId)
        listOf(behaviorNewer, behaviorOlder).forEach { repository.removeFromLibrary(it) }
        repository.addToLibrary(book(behaviorNewer, "根书架 A"))
        repository.addToLibrary(book(behaviorOlder, "根书架 B"))
        repository.createCollection(
            LibraryCollection(
                collectionId = behaviorCollectionId,
                kind = CollectionKind.MANUAL,
                title = "即时恢复收藏夹",
                parentCollectionId = null,
                displayOrder = Long.MAX_VALUE,
            ),
        )
        assertTrue(repository.addManualMembership(behaviorCollectionId, behaviorOlder))
        val controller = LibraryFlowController(repository, libraryPreferences)
        controller.reload("failed")
        val rootIdentities = controller.state.entries.map { it.book.identity }.toSet()

        controller.selectCollection(behaviorCollectionId)
        assertTrue(controller.state.loading)
        assertTrue(controller.state.entries.isEmpty())
        controller.reload("failed")
        assertEquals(listOf(behaviorOlder), controller.state.entries.map { it.book.identity })

        controller.restoreLibraryHome()
        assertFalse(controller.state.loading)
        assertEquals(rootIdentities, controller.state.entries.map { it.book.identity }.toSet())
    }

    @Test
    fun detail_local_destinations_replace_manual_memberships_exactly() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val firstId = "fixture.detail.destination.first"
        val secondId = "fixture.detail.destination.second"
        val now = Instant.parse("2091-02-01T00:00:00Z")
        try {
            listOf(firstId, secondId).forEach { repository.deleteCollection(it) }
            repository.removeFromLibrary(behaviorNewer)
            repository.addToLibrary(book(behaviorNewer, "目的地替换"))
            repository.createCollection(LibraryCollection(firstId, CollectionKind.MANUAL, "第一收藏", null, 0L, now, now))
            repository.createCollection(LibraryCollection(secondId, CollectionKind.MANUAL, "第二收藏", null, 1L, now, now))
            assertTrue(repository.addManualMembership(firstId, behaviorNewer))

            val controller = LibraryFlowController(repository, libraryPreferences)
            controller.reload("failed")
            assertTrue(controller.applyBookDestinations(behaviorNewer, setOf(secondId), "failed"))
            assertTrue(repository.collectionEntries(firstId).isEmpty())
            assertEquals(listOf(behaviorNewer), repository.collectionEntries(secondId).map { it.book.identity })

            assertTrue(controller.applyBookDestinations(behaviorNewer, emptySet(), "failed"))
            assertTrue(repository.collectionEntries(secondId).isEmpty())
        } finally {
            listOf(firstId, secondId).forEach { repository.deleteCollection(it) }
            repository.removeFromLibrary(behaviorNewer)
        }
    }

    @Test
    fun mirror_roots_are_default_visible_while_remote_folders_stay_nested() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val original = libraryPreferences.preferences.first()
        val sourceId = "fixture.mirror.typed-root"
        val now = Instant.parse("2091-01-01T00:00:00Z")
        try {
            libraryPreferences.updateRootNodes(emptyList())
            libraryPreferences.clearWebsiteGroupingOverride(sourceId)
            repository.saveRemoteMirrorSnapshot(
                org.tsuyomi.core.database.RemoteMirrorReplaceRequest(
                    sourceId = sourceId,
                    sourceName = "测试网站",
                    books = emptyList(),
                    targets = listOf(
                        org.tsuyomi.core.database.RemoteMirrorTargetSnapshot(
                            targetId = "favorites",
                            sourceId = sourceId,
                            displayName = "特别收藏",
                            parentId = null,
                            kind = "folder",
                            frozen = false,
                            updatedAtEpochSecond = now.epochSecond,
                        ),
                    ),
                    updatedAt = now,
                ),
            )
            val controller = LibraryFlowController(repository, libraryPreferences)
            controller.configureInstalledMirrorRoots {
                listOf(org.tsuyomi.feature.library.LibraryMirrorShortcut(sourceId, null, "测试网站", 0, false))
            }
            controller.reload("failed")

            val rootId = org.tsuyomi.feature.library.libraryMirrorRootId(sourceId)
            assertTrue(controller.state.rootNodePlacements.any { it.id == rootId })
            assertTrue(controller.state.mirrorShortcuts.any { it.sourceId == sourceId && it.targetId == "favorites" })
            val projected = org.tsuyomi.feature.library.buildLibraryRootItems(
                entries = controller.state.projectedEntries(),
                collections = controller.collections,
                collectionCounts = controller.state.collectionCounts,
                mirrors = controller.state.mirrorShortcuts,
                placements = controller.state.rootNodePlacements,
                customOrder = true,
            )
            assertEquals(1, projected.filterIsInstance<org.tsuyomi.feature.library.LibraryRootItem.Mirror>()
                .count { it.mirror.sourceId == sourceId })
            assertTrue(projected.none { it.key.contains("favorites") })

            assertTrue(controller.setWebsiteGroupingEnabled(sourceId, true, "failed"))
            controller.reload("failed")
            assertTrue(controller.isWebsiteGroupingEnabled(sourceId))
            assertEquals(1, controller.state.rootNodePlacements.count { it.id == rootId })
        } finally {
            libraryPreferences.updateRootNodes(original.rootNodes)
            original.websiteGroupingBySource[sourceId]?.let { enabled ->
                libraryPreferences.updateWebsiteGrouping(sourceId, enabled)
            } ?: libraryPreferences.clearWebsiteGroupingOverride(sourceId)
        }
    }

    @Test
    fun library_controller_persists_independent_state_for_each_fixed_tab() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val profile = "FIXTURE_TABS"
        libraryPreferences.updateTabPresentation(
            "$profile:ALL",
            org.tsuyomi.core.preferences.LibraryTabPresentationPreferences("GRID", "TITLE", false, 4, 12),
        )
        libraryPreferences.updateTabPresentation(
            "$profile:CONTINUE",
            org.tsuyomi.core.preferences.LibraryTabPresentationPreferences("LIST", "RECENT", true, 2, 8),
        )
        libraryPreferences.updateTabPresentation(
            "$profile:READ_LATER",
            org.tsuyomi.core.preferences.LibraryTabPresentationPreferences("COMPACT", "ADDED", true, 1, 4),
        )

        val controller = LibraryFlowController(repository, libraryPreferences, profile)
        controller.reload("failed")
        assertEquals(org.tsuyomi.feature.library.LibraryLayout.GRID, controller.state.layout)
        assertEquals(org.tsuyomi.feature.library.LibrarySortMode.TITLE, controller.state.sortMode)
        assertEquals(4, controller.state.firstVisibleIndex)

        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE)
        assertEquals(org.tsuyomi.feature.library.LibraryLayout.LIST, controller.state.layout)
        assertEquals(2, controller.state.firstVisibleIndex)
        controller.persistViewport(6, 20)
        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.ALL)
        assertEquals(4, controller.state.firstVisibleIndex)

        val restored = LibraryFlowController(repository, libraryPreferences, profile)
        restored.reload("failed")
        restored.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE)
        assertEquals(6, restored.state.firstVisibleIndex)
        assertEquals(20, restored.state.firstVisibleOffset)
        restored.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.READ_LATER)
        assertEquals(org.tsuyomi.feature.library.LibraryLayout.COMPACT, restored.state.layout)
        assertEquals(1, restored.state.firstVisibleIndex)
    }

    @Test
    fun root_book_drop_creates_one_collection_with_complete_deduplicated_batch() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        repository.collections().filter { it.title == rootBatchCollectionTitle }.forEach {
            repository.deleteCollection(it.collectionId)
        }
        listOf(behaviorNewer, behaviorOlder, behaviorUnstarted).forEach { repository.removeFromLibrary(it) }
        repository.addToLibrary(book(behaviorNewer, "批量 A"))
        repository.addToLibrary(book(behaviorOlder, "批量 B"))
        repository.addToLibrary(book(behaviorUnstarted, "目标 C"))

        val controller = LibraryFlowController(repository, libraryPreferences, "FIXTURE_BATCH")
        controller.reload("failed")
        controller.requestRootCollectionCreation(
            moved = setOf(behaviorNewer, behaviorOlder, behaviorNewer),
            target = behaviorUnstarted,
        )
        assertEquals(setOf(behaviorNewer, behaviorOlder, behaviorUnstarted), controller.state.selectedBookIds)
        assertTrue(controller.createCollectionFromSelection(rootBatchCollectionTitle, "failed"))

        val created = repository.collections().single { it.title == rootBatchCollectionTitle }
        assertEquals(
            setOf(behaviorNewer, behaviorOlder, behaviorUnstarted),
            repository.collectionEntries(created.collectionId).mapTo(linkedSetOf()) { it.book.identity },
        )
        assertTrue(repository.libraryEntries().map { it.book.identity }
            .containsAll(setOf(behaviorNewer, behaviorOlder, behaviorUnstarted)))
        assertEquals(
            1,
            controller.state.rootNodePlacements.count {
                it.id == org.tsuyomi.feature.library.libraryCollectionRootId(created.collectionId)
            },
        )
    }

    @Test
    fun library_controller_retains_recent_cover_state_across_visibility_gap() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val controller = LibraryFlowController(repository, libraryPreferences)
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        try {
            controller.configureCoverRepository(
                repository = object : CoverRepository {
                    override fun observe(request: CoverRequest) = flowOf(CoverUiState.Ready(bitmap))
                },
                sourceId = "fixture.cover",
                packageRevision = "package-revision",
                credentialRevision = "credential-revision",
                scope = this,
            )
            val entries = (0 until 25).map { index ->
                LibraryEntry(
                    book = book(BookIdentity("fixture.cover", "cover-$index"), "封面 $index").copy(
                        coverUrl = "https://example.com/cover-$index.png",
                        canonicalUrl = "https://example.com/book-$index",
                    ),
                    libraryAddedAt = Instant.EPOCH,
                    rating = null,
                    localTags = emptySet(),
                    sourceAvailable = true,
                    reconciliation = null,
                )
            }
            entries.forEach { entry ->
                controller.setCoverVisible(entry, true)
                yield()
                controller.setCoverVisible(entry, false)
            }
            assertTrue(controller.coverState(entries.last()) is CoverUiState.Ready)
            assertTrue(controller.coverStates.size <= 24)
        } finally {
            bitmap.recycle()
        }
    }

    private fun book(identity: BookIdentity, title: String): LibraryBook = LibraryBook(
        identity = identity,
        title = title,
        addedAt = Instant.EPOCH,
        metadataUpdatedAt = Instant.EPOCH,
    )

    private fun progress(identity: BookIdentity, at: Instant, bookProgress: Double): ReadingProgress = ReadingProgress(
        identity = identity,
        locator = ReaderLocator(
            document = DocumentIdentity(identity.sourceId, identity.remoteBookId, "chapter-1"),
            blockId = "block-1",
            characterOffset = 10,
            bookProgress = bookProgress,
            capturedAt = at,
        ),
    )
    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
