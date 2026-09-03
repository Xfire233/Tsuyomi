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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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

@RunWith(AndroidJUnit4::class)
class LibraryProductionJourneyInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val identity = BookIdentity("fixture.production.library", "journey-read-later")
    private val behaviorNewer = BookIdentity("fixture.production.library", "behavior-newer")
    private val selectionCollectionTitle = "批量选择收藏夹"
    private val shortcutDropCollectionTitle = "快捷拖放收藏夹"
    private val shortcutReplacementCollectionTitle = "快捷书籍替换收藏夹"
    private val behaviorOlder = BookIdentity("fixture.production.library", "behavior-older")
    private val behaviorUnstarted = BookIdentity("fixture.production.library", "behavior-unstarted")
    private val behaviorCollectionId = "behavior-collection"

    private val libraryPreferences
        get() = (composeRule.activity.application as TsuyomiApplication).libraryPreferencesRepository

    @After
    fun removeFixtureBook() {
        runBlocking {
            val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
            repository.collections().filter {
                it.title == selectionCollectionTitle ||
                    it.title == shortcutDropCollectionTitle ||
                    it.title == shortcutReplacementCollectionTitle
            }.forEach {
                repository.deleteCollection(it.collectionId)
            }
            repository.deleteCollection(behaviorCollectionId)
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
        waitForText("快捷书架")

        assertTrue(composeRule.onAllNodesWithText("本地藏书").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("library-shortcut-shelf").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("同步并检查更新").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("搜索").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("新建收藏夹").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("切换布局；当前为网格").performClick()
        composeRule.onNodeWithContentDescription("切换布局；当前为列表").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        waitForText("标签")
        composeRule.onNodeWithText("标签").performClick()
        waitForText("还没有本地标签")
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForText("快捷书架")

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
        composeRule.onNodeWithContentDescription("同步并检查更新").performClick()
        waitForText(title)

        composeRule.onNodeWithContentDescription("稍后再读").performClick()
        waitForText(title)
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForText("快捷书架")
    }

    @Test
    fun standard_library_projects_recent_history_and_child_counts() {
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

        waitForText("快捷书架")
        composeRule.onNodeWithContentDescription("同步并检查更新").performClick()
        waitForText("最近阅读·新")
        val expectedRootCount = runBlocking { repository.libraryEntries().size }
        val expectedRecentCount = runBlocking { repository.libraryEntries().count { it.progress != null } }

        composeRule.onNodeWithContentDescription("最近阅读").performClick()
        waitForText("最近阅读·新")
        composeRule.onNodeWithText("$expectedRecentCount 本").assertExists()
        val newerBounds = composeRule.onNodeWithText("最近阅读·新").fetchSemanticsNode().boundsInRoot
        val olderBounds = composeRule.onNodeWithText("最近阅读·旧").fetchSemanticsNode().boundsInRoot
        assertTrue(
            newerBounds.top < olderBounds.top ||
                (newerBounds.top == olderBounds.top && newerBounds.left < olderBounds.left),
        )
        assertTrue(composeRule.onAllNodesWithText("尚未阅读").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForText("快捷书架")
        composeRule.onNodeWithText("$expectedRootCount 本").assertExists()
        composeRule.onNodeWithTag("library-book-surface").assertExists()
        composeRule.onNodeWithTag("library-shortcut-shelf").assertExists()

        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        waitForText("全部快捷书架")
        composeRule.onNodeWithText("行为测试收藏夹").performClick()
        waitForText("最近阅读·旧")
        composeRule.onNodeWithText("1 本").assertExists()
        assertTrue(composeRule.onAllNodesWithText("最近阅读·新").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForText("快捷书架")
        composeRule.onNodeWithText("$expectedRootCount 本").assertExists()
        composeRule.onNodeWithTag("library-shortcut-shelf").assertExists()
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
        waitForText("快捷书架")
        composeRule.onNodeWithContentDescription("同步并检查更新").performClick()
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
        waitForText("快捷书架")
        assertTrue(composeRule.onAllNodesWithText("已选 1 项").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithContentDescription("同步并检查更新").fetchSemanticsNode()
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
        waitForText("快捷书架")
        composeRule.onNodeWithContentDescription("同步并检查更新").performClick()
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
        waitForText("快捷书架")
    }

    @Test
    fun standard_library_drag_preview_is_immediate_and_book_drops_into_shortcut_shelf() {
        val application = composeRule.activity.application as TsuyomiApplication
        val originalPreferences = runBlocking { libraryPreferences.preferences.first() }
        val shortcutId = org.tsuyomi.feature.library.libraryBookShortcutId(behaviorNewer)
        try {
            runBlocking {
                application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
                libraryPreferences.updateShortcutLocked(false)
                libraryPreferences.updateShortcutOrder(originalPreferences.shortcutOrder.filterNot { it == shortcutId })
                listOf(behaviorNewer, behaviorOlder).forEach { application.libraryRepository.removeFromLibrary(it) }
                application.libraryRepository.addToLibrary(book(behaviorNewer, "快捷拖放 A"))
                application.libraryRepository.addToLibrary(book(behaviorOlder, "快捷拖放 B"))
            }
            composeRule.activityRule.scenario.recreate()
            waitForText("快捷拖放 A")

            val firstTag = "library-book-${behaviorNewer.sourceId}-${behaviorNewer.remoteBookId}"
            val first = composeRule.onNodeWithTag(firstTag)
            first.performSemanticsAction(SemanticsActions.OnLongClick)
            waitForText("已选 1 项")

            first.performTouchInput {
                down(center)
                advanceEventTime(700)
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { composeRule.onNodeWithTag("library-drag-preview").fetchSemanticsNode() }.isSuccess
            }
            composeRule.onNodeWithTag("library-drag-preview").fetchSemanticsNode()

            val firstBounds = first.fetchSemanticsNode().boundsInRoot
            val shelfBounds = composeRule.onNodeWithTag("library-shortcut-shelf").fetchSemanticsNode().boundsInRoot
            first.performTouchInput {
                moveTo(shelfBounds.center - firstBounds.topLeft, delayMillis = 100)
            }
            first.performTouchInput { up() }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { composeRule.onNodeWithTag("library-shortcut-$shortcutId").fetchSemanticsNode() }.isSuccess
            }
            assertTrue(runBlocking { libraryPreferences.preferences.first().shortcutOrder.contains(shortcutId) })
        } finally {
            runBlocking {
                libraryPreferences.updateShortcutOrder(originalPreferences.shortcutOrder)
                libraryPreferences.updateShortcutLocked(originalPreferences.shortcutLocked)
            }
        }
    }

    @Test
    fun standard_library_shortcut_gap_pushes_siblings_and_collection_target_morphs() {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val originalPreferences = runBlocking { libraryPreferences.preferences.first() }
        val collectionShortcutId = "collection:$behaviorCollectionId"
        try {
            runBlocking {
                application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
                libraryPreferences.updateShortcutLocked(false)
                libraryPreferences.updateShortcutOrder(
                    listOf("continue", collectionShortcutId, "recent", "read-later", "updates"),
                )
                repository.deleteCollection(behaviorCollectionId)
                listOf(behaviorNewer, behaviorOlder).forEach { repository.removeFromLibrary(it) }
                repository.addToLibrary(book(behaviorNewer, "动态插入 A"))
                repository.addToLibrary(book(behaviorOlder, "动态插入 B"))
                repository.createCollection(
                    LibraryCollection(
                        collectionId = behaviorCollectionId,
                        kind = CollectionKind.MANUAL,
                        title = "拖放目标收藏夹",
                        parentCollectionId = null,
                        displayOrder = Long.MAX_VALUE,
                        createdAt = Instant.EPOCH,
                        updatedAt = Instant.EPOCH,
                    ),
                )
                assertTrue(repository.addManualMembership(behaviorCollectionId, behaviorOlder))
            }
            composeRule.activityRule.scenario.recreate()
            waitForText("动态插入 A")
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithContentDescription("快捷书架未锁定，点按锁定").fetchSemanticsNode()
                }.isSuccess
            }

            val continueTile = composeRule.onNodeWithTag("library-shortcut-continue")
            val continueMedia = composeRule.onNodeWithTag(
                "library-shortcut-media-continue",
                useUnmergedTree = true,
            )
            val tileBounds = continueTile.fetchSemanticsNode().boundsInRoot
            val mediaBounds = continueMedia.fetchSemanticsNode().boundsInRoot
            assertTrue(abs(tileBounds.width / tileBounds.height - 80f / 116f) < 0.03f)
            assertTrue(abs(mediaBounds.height / tileBounds.height - 76f / 116f) < 0.03f)

            val first = composeRule.onNodeWithTag(
                "library-book-${behaviorNewer.sourceId}-${behaviorNewer.remoteBookId}",
            )
            first.performSemanticsAction(SemanticsActions.OnLongClick)
            waitForText("已选 1 项")
            first.performTouchInput {
                down(center)
                advanceEventTime(700)
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { composeRule.onNodeWithTag("library-drag-preview").fetchSemanticsNode() }.isSuccess
            }
            val firstBounds = first.fetchSemanticsNode().boundsInRoot
            val movingBefore = composeRule.onNodeWithTag("library-shortcut-recent").fetchSemanticsNode().boundsInRoot
            val shelfBounds = composeRule.onNodeWithTag("library-shortcut-shelf")
                .fetchSemanticsNode().boundsInRoot
            first.performTouchInput {
                moveTo(Offset(shelfBounds.center.x, shelfBounds.top - 20f) - firstBounds.topLeft, delayMillis = 100)
            }
            val dropHints = composeRule.onAllNodesWithText("松开以", substring = true).fetchSemanticsNodes()
                .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }
                .joinToString { it.text }
            assertTrue(
                "Unexpected shelf destination: ${dropHints.map { "U+%04X".format(it.code) }}; shelf=$shelfBounds source=$firstBounds",
                dropHints.contains("放到快捷书架"),
            )
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    val gapNodes = composeRule.onAllNodesWithTag(
                        "library-shortcut-insertion-gap",
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes()
                    gapNodes.isNotEmpty() && gapNodes.first().boundsInRoot.width > tileBounds.width * 0.5f
                }.getOrDefault(false)
            }
            val gapBounds = composeRule.onNodeWithTag(
                "library-shortcut-insertion-gap",
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            val movingAfter = composeRule.onNodeWithTag("library-shortcut-recent")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("gap=$gapBounds tile=$tileBounds", gapBounds.width > tileBounds.width * 0.5f)
            assertTrue(movingAfter.left > movingBefore.left)
            val collectionAfter = composeRule.onNodeWithTag("library-shortcut-$collectionShortcutId")
                .fetchSemanticsNode().boundsInRoot
            first.performTouchInput {
                moveTo(collectionAfter.center - firstBounds.topLeft, delayMillis = 100)
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag("library-shortcut-$collectionShortcutId")
                        .fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "当前拖放目标"
                }.getOrDefault(false)
            }
            assertTrue(
                composeRule.onAllNodesWithTag("library-shortcut-insertion-gap").fetchSemanticsNodes().isEmpty(),
            )

            val continueBounds = continueTile.fetchSemanticsNode().boundsInRoot
            first.performTouchInput {
                moveTo(continueBounds.center - firstBounds.topLeft, delayMillis = 100)
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag("library-shortcut-$collectionShortcutId")
                        .fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "快捷书架随内容滚动，可拖动"
                }.getOrDefault(false)
            }
            first.performTouchInput { up() }
        } finally {
            runBlocking {
                repository.deleteCollection(behaviorCollectionId)
                libraryPreferences.updateShortcutOrder(originalPreferences.shortcutOrder)
                libraryPreferences.updateShortcutLocked(originalPreferences.shortcutLocked)
            }
        }
    }

    @Test
    fun locked_shortcut_shelf_stays_pinned_and_keeps_root_and_book_targets_active() {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val originalPreferences = runBlocking { libraryPreferences.preferences.first() }
        val identities = (0 until 24).map { BookIdentity("fixture.production.library", "locked-shelf-$it") }
        val sourceIdentity = identities[12]
        val targetIdentity = identities[13]
        val shortcutId = org.tsuyomi.feature.library.libraryBookShortcutId(sourceIdentity)
        try {
            runBlocking {
                application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
                libraryPreferences.updateShortcutLocked(false)
                libraryPreferences.updateShortcutOrder(originalPreferences.shortcutOrder.filterNot { it == shortcutId })
                identities.forEachIndexed { index, id ->
                    repository.removeFromLibrary(id)
                    repository.addToLibrary(book(id, "固定快捷栏 $index"))
                }
            }
            composeRule.activityRule.scenario.recreate()
            waitForText("固定快捷栏 0")
            composeRule.onNodeWithContentDescription("快捷书架未锁定，点按锁定").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription("快捷书架已锁定，点按解锁")
                    .fetchSemanticsNodes().isNotEmpty()
            }

            val surface = composeRule.onNodeWithTag("library-book-surface")
            val shelfTopBefore = composeRule.onNodeWithTag("library-shortcut-shelf")
                .fetchSemanticsNode().boundsInRoot.top
            val sourceTag = "library-book-${sourceIdentity.sourceId}-${sourceIdentity.remoteBookId}"
            var scrollAttempts = 0
            while (composeRule.onAllNodesWithTag(sourceTag).fetchSemanticsNodes().isEmpty() && scrollAttempts < 8) {
                surface.performTouchInput {
                    down(center)
                    moveBy(Offset(0f, -900f), delayMillis = 300)
                    up()
                }
                scrollAttempts++
            }
            val shelfTopAfter = composeRule.onNodeWithTag("library-shortcut-shelf")
                .fetchSemanticsNode().boundsInRoot.top
            assertTrue("pinned shelf moved: before=$shelfTopBefore after=$shelfTopAfter", abs(shelfTopAfter - shelfTopBefore) <= 1f)

            val source = composeRule.onNodeWithTag(sourceTag)
            val sourceBounds = source.fetchSemanticsNode().boundsInRoot
            val shelfBounds = composeRule.onNodeWithTag("library-shortcut-shelf").fetchSemanticsNode().boundsInRoot
            source.performTouchInput {
                down(center)
                advanceEventTime(700)
            }
            waitForText("已选 1 项")
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("library-drag-preview").fetchSemanticsNodes().isNotEmpty()
            }
            source.performTouchInput {
                moveTo(Offset(shelfBounds.center.x, shelfBounds.top - 20f) - sourceBounds.topLeft, delayMillis = 100)
            }
            val lockedDropHints = composeRule.onAllNodesWithText("松开以", substring = true).fetchSemanticsNodes()
                .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }
                .joinToString { it.text }
            assertTrue("locked shelf hint=$lockedDropHints shelf=$shelfBounds source=$sourceBounds", lockedDropHints.contains("放到快捷书架"))
            source.performTouchInput { up() }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("library-shortcut-$shortcutId").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(runBlocking { libraryPreferences.preferences.first().shortcutOrder.contains(shortcutId) })

            composeRule.onNodeWithContentDescription("退出选择").performClick()
            val targetTag = "library-book-${targetIdentity.sourceId}-${targetIdentity.remoteBookId}"
            val target = composeRule.onNodeWithTag(targetTag)
            target.performSemanticsAction(SemanticsActions.OnLongClick)
            waitForText("已选 1 项")
            val selectedTarget = composeRule.onNodeWithTag(targetTag)
            selectedTarget.performTouchInput {
                down(center)
                advanceEventTime(700)
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("library-drag-preview").fetchSemanticsNodes().isNotEmpty()
            }
            val targetBounds = selectedTarget.fetchSemanticsNode().boundsInRoot
            composeRule.onNodeWithTag("library-shortcut-$shortcutId").performScrollTo()
            val shortcutBounds = composeRule.onNodeWithTag("library-shortcut-$shortcutId")
                .fetchSemanticsNode().boundsInRoot
            selectedTarget.performTouchInput {
                moveTo(shortcutBounds.center - targetBounds.topLeft, delayMillis = 100)
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onNodeWithTag("library-shortcut-$shortcutId")
                    .fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "当前拖放目标"
            }
            selectedTarget.performTouchInput { up() }
            waitForText("用所选书籍新建收藏夹")
            composeRule.onNodeWithText("取消").performClick()

            composeRule.onNodeWithContentDescription("退出选择").performClick()
            composeRule.onNodeWithTag("library-shortcut-$shortcutId").performScrollTo()
            val shortcut = composeRule.onNodeWithTag("library-shortcut-$shortcutId")
            val shortcutBoundsBeforeReorder = shortcut.fetchSemanticsNode().boundsInRoot
            val shelfBoundsBeforeReorder = composeRule.onNodeWithTag("library-shortcut-shelf")
                .fetchSemanticsNode().boundsInRoot
            shortcut.performTouchInput {
                down(center)
                advanceEventTime(700)
            }
            waitForText("已选 1 项")
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("library-drag-preview").fetchSemanticsNodes().isNotEmpty()
            }
            shortcut.performTouchInput {
                moveTo(
                    Offset(shelfBoundsBeforeReorder.left + 8f, shelfBoundsBeforeReorder.center.y) -
                        shortcutBoundsBeforeReorder.topLeft,
                    delayMillis = 150,
                )
                up()
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking { libraryPreferences.preferences.first().shortcutOrder.firstOrNull() == shortcutId }
            }
        } finally {
            runBlocking {
                identities.forEach { repository.removeFromLibrary(it) }
                libraryPreferences.updateShortcutOrder(originalPreferences.shortcutOrder)
                libraryPreferences.updateShortcutLocked(originalPreferences.shortcutLocked)
            }
        }
    }

    @Test
    fun unlocked_shortcut_shelf_scrolls_then_collapses_and_back_scroll_expands_overlay() {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val originalPreferences = runBlocking { libraryPreferences.preferences.first() }
        val identities = (0 until 24).map { BookIdentity("fixture.production.library", "shelf-scroll-$it") }
        try {
            runBlocking {
                libraryPreferences.updateShortcutLocked(false)
                identities.forEachIndexed { index, id ->
                    repository.removeFromLibrary(id)
                    repository.addToLibrary(book(id, "快捷栏滚动 $index"))
                }
            }
            composeRule.activityRule.scenario.recreate()
            waitForText("快捷栏滚动 0")
            val surface = composeRule.onNodeWithTag("library-book-surface")
            repeat(3) {
                surface.performTouchInput {
                    down(center)
                    moveBy(Offset(0f, -900f), delayMillis = 300)
                    up()
                }
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { composeRule.onNodeWithTag("library-shortcut-overlay-collapsed").fetchSemanticsNode() }.isSuccess
            }
            val handleBounds = composeRule.onNodeWithTag("library-shortcut-overlay-collapsed")
                .fetchSemanticsNode().boundsInRoot
            val density = composeRule.activity.resources.displayMetrics.density
            assertTrue(handleBounds.width >= 48f * density && handleBounds.height >= 48f * density)
            surface.performTouchInput {
                down(center)
                moveBy(Offset(0f, 180f), delayMillis = 200)
                up()
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("library-shortcut-overlay-expanded").fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithContentDescription("快捷书架未锁定，点按锁定").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription("快捷书架已锁定，点按解锁")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val visibleAnchor = identities.firstNotNullOf { id ->
                val nodes = composeRule.onAllNodesWithTag("library-book-${id.sourceId}-${id.remoteBookId}")
                    .fetchSemanticsNodes()
                nodes.firstOrNull()?.let { id }
            }
            val anchorTag = "library-book-${visibleAnchor.sourceId}-${visibleAnchor.remoteBookId}"
            val anchorTopBeforeUnlock = composeRule.onNodeWithTag(anchorTag).fetchSemanticsNode().boundsInRoot.top
            composeRule.onNodeWithContentDescription("快捷书架已锁定，点按解锁").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription("快捷书架未锁定，点按锁定")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val anchorTopAfterUnlock = composeRule.onNodeWithTag(anchorTag).fetchSemanticsNode().boundsInRoot.top
            assertTrue(
                "unlock jumped book anchor: before=$anchorTopBeforeUnlock after=$anchorTopAfterUnlock",
                abs(anchorTopAfterUnlock - anchorTopBeforeUnlock) <= 1f,
            )
            assertTrue(composeRule.onAllNodesWithTag("library-shortcut-overlay-collapsed").fetchSemanticsNodes().isEmpty())
            surface.performTouchInput {
                down(center)
                moveBy(Offset(0f, 900f), delayMillis = 300)
                up()
            }
            assertTrue(composeRule.onAllNodesWithTag("library-shortcut-overlay-collapsed").fetchSemanticsNodes().isEmpty())
            surface.performTouchInput {
                down(center)
                moveBy(Offset(0f, -240f), delayMillis = 200)
                up()
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("library-shortcut-overlay-collapsed").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("展开快捷书架").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("library-shortcut-overlay-expanded").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            runBlocking {
                identities.forEach { repository.removeFromLibrary(it) }
                libraryPreferences.updateShortcutOrder(originalPreferences.shortcutOrder)
                libraryPreferences.updateShortcutLocked(originalPreferences.shortcutLocked)
            }
        }
    }

    @Test
    fun collapsed_shortcut_handle_expands_when_a_book_drag_hovers_it() {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val originalPreferences = runBlocking { libraryPreferences.preferences.first() }
        val identities = (0 until 24).map { BookIdentity("fixture.production.library", "shelf-hover-$it") }
        try {
            runBlocking {
                libraryPreferences.updateShortcutLocked(false)
                identities.forEachIndexed { index, id ->
                    repository.removeFromLibrary(id)
                    repository.addToLibrary(book(id, "快捷栏悬停 $index"))
                }
            }
            composeRule.activityRule.scenario.recreate()
            waitForText("快捷栏悬停 0")
            val surface = composeRule.onNodeWithTag("library-book-surface")
            repeat(3) {
                surface.performTouchInput {
                    down(center)
                    moveBy(Offset(0f, -900f), delayMillis = 300)
                    up()
                }
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("library-shortcut-overlay-collapsed").fetchSemanticsNodes().isNotEmpty()
            }
            val visibleSource = identities.firstNotNullOf { id ->
                val tag = "library-book-${id.sourceId}-${id.remoteBookId}"
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.let { id }
            }
            val sourceTag = "library-book-${visibleSource.sourceId}-${visibleSource.remoteBookId}"
            composeRule.onNodeWithTag(sourceTag).performSemanticsAction(SemanticsActions.OnLongClick)
            waitForText("已选 1 项")
            val source = composeRule.onNodeWithTag(sourceTag)
            source.performTouchInput {
                down(center)
                advanceEventTime(700)
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("library-drag-preview").fetchSemanticsNodes().isNotEmpty()
            }
            val handleBounds = composeRule.onNodeWithTag("library-shortcut-overlay-collapsed")
                .fetchSemanticsNode().boundsInRoot
            val initialPreviewBounds = composeRule.onNodeWithTag("library-drag-preview")
                .fetchSemanticsNode().boundsInRoot
            val pointer = Offset(
                initialPreviewBounds.center.x,
                initialPreviewBounds.top + initialPreviewBounds.height * 0.34f,
            )
            source.performTouchInput {
                val hoverDelta = handleBounds.center - pointer
                moveBy(hoverDelta, delayMillis = 100)
                moveBy(-hoverDelta, delayMillis = 100)
                cancel()
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("library-shortcut-overlay-expanded").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("退出选择").performClick()
        } finally {
            runBlocking {
                identities.forEach { repository.removeFromLibrary(it) }
                libraryPreferences.updateShortcutOrder(originalPreferences.shortcutOrder)
                libraryPreferences.updateShortcutLocked(originalPreferences.shortcutLocked)
            }
        }
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

        controller.selectRoot()
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

        controller.selectRoot()
        assertFalse(controller.state.loading)
        assertEquals(rootIdentities, controller.state.entries.map { it.book.identity }.toSet())
    }

    @Test
    fun library_shortcut_order_and_lock_survive_controller_recreation() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val original = libraryPreferences.preferences.first()
        try {
            val firstController = LibraryFlowController(repository, libraryPreferences)
            firstController.setShortcutOrder(listOf("updates", "continue", "recent"))
            firstController.setShortcutLocked(false)

            val restoredController = LibraryFlowController(repository, libraryPreferences)
            restoredController.reload("failed")
            assertEquals(listOf("updates", "continue", "recent"), restoredController.state.shortcutOrder)
            assertFalse(restoredController.state.shortcutLocked)
        } finally {
            libraryPreferences.updateShortcutOrder(original.shortcutOrder)
            libraryPreferences.updateShortcutLocked(original.shortcutLocked)
        }
    }

    @Test
    fun library_controller_persists_direct_batch_and_book_target_shortcut_drops() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val original = libraryPreferences.preferences.first()
        val bookShortcutId = org.tsuyomi.feature.library.libraryBookShortcutId(behaviorNewer)
        try {
            repository.collections().filter {
                it.title == shortcutDropCollectionTitle || it.title == shortcutReplacementCollectionTitle
            }.forEach { repository.deleteCollection(it.collectionId) }
            listOf(behaviorNewer, behaviorOlder).forEach { repository.removeFromLibrary(it) }
            repository.addToLibrary(book(behaviorNewer, "快捷持久化 A"))
            repository.addToLibrary(book(behaviorOlder, "快捷持久化 B"))
            libraryPreferences.updateShortcutOrder(emptyList())
            libraryPreferences.updateShortcutLocked(false)

            val controller = LibraryFlowController(repository, libraryPreferences)
            controller.reload("failed")
            assertTrue(controller.dropBooksOnShortcutRoot(setOf(behaviorNewer), 0, "failed"))
            assertEquals(bookShortcutId, controller.state.shortcutOrder.first())

            val restored = LibraryFlowController(repository, libraryPreferences)
            restored.reload("failed")
            assertEquals(bookShortcutId, restored.state.shortcutOrder.first())
            restored.requestShortcutCollectionCreation(
                moved = setOf(behaviorOlder),
                target = behaviorNewer,
                insertionIndex = restored.shortcutIndex(bookShortcutId),
                replacementShortcutIds = setOf(bookShortcutId),
            )
            assertTrue(restored.createCollectionFromSelection(shortcutReplacementCollectionTitle, "failed"))
            val replacement = repository.collections().single { it.title == shortcutReplacementCollectionTitle }
            assertEquals(
                setOf(behaviorNewer, behaviorOlder),
                repository.collectionEntries(replacement.collectionId).mapTo(linkedSetOf()) { it.book.identity },
            )
            assertFalse(restored.state.shortcutOrder.contains(bookShortcutId))
            assertEquals("collection:${replacement.collectionId}", restored.state.shortcutOrder.first())

            assertTrue(restored.dropBooksOnShortcutRoot(setOf(behaviorNewer, behaviorOlder), 1, "failed"))
            assertEquals(org.tsuyomi.feature.library.LibrarySelectionDialog.CREATE_COLLECTION, restored.state.selectionDialog)
            assertTrue(restored.createCollectionFromSelection(shortcutDropCollectionTitle, "failed"))
            val batch = repository.collections().single { it.title == shortcutDropCollectionTitle }
            assertEquals("collection:${batch.collectionId}", restored.state.shortcutOrder[1])
        } finally {
            libraryPreferences.updateShortcutOrder(original.shortcutOrder)
            libraryPreferences.updateShortcutLocked(original.shortcutLocked)
        }
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
