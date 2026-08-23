/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.After
import org.tsuyomi.prototype.uiatlas.components.libraryDragPreviewSize
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.currentLayoutIcon
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortDirection
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortMode
import org.tsuyomi.prototype.uiatlas.components.orderedForLibrary
import org.tsuyomi.prototype.uiatlas.components.layoutToggleContentDescription
import org.tsuyomi.prototype.uiatlas.fixtures.LibraryAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryShortcutDragInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val stateFile: File
        get() = File(
            InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
            "interactive-prototype-state-v1.json",
        )

    @Before
    fun resetPrototypeState() {
        stateFile.delete()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
    }

    @After
    fun closeScenario() {
        scenario.close()
    }

    @Test
    fun long_pressing_a_book_into_the_shortcut_shelf_adds_and_persists_it() {
        val anchorTopBefore = composeRule
            .onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        drag(
            sourceDescription = "山中邮差，长按多选，移动可拖动至快捷书架",
            targetText = "快捷书架",
        )

        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ShortcutBookDropped")
        }
        val anchorTopAfter = composeRule
            .onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(kotlin.math.abs(anchorTopAfter - anchorTopBefore) <= 1f)
        val persisted = stateFile.readText()
        assertTrue(persisted.contains("library.shortcuts.order"))

        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        assertTrue(composeRule.onAllNodesWithContentDescription("山中邮差，长按多选，移动可拖动排序").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun dropping_a_book_on_a_collection_adds_to_collection_not_root() {
        composeRule.onNodeWithContentDescription("继续阅读，长按多选，移动可拖动排序").performTouchInput {
            down(centerRight)
            moveTo(centerLeft, 200)
            up()
        }
        composeRule.waitForIdle()

        drag(
            sourceDescription = "山中邮差，长按多选，移动可拖动至快捷书架",
            targetDescription = "夜航船，长按多选，移动可拖动排序",
        )

        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ShortcutBookDroppedIntoCollection")
        }
        val persisted = stateFile.readText()
        assertTrue(persisted.contains("library.collection.night-boat.books"))
        assertTrue(!persisted.contains("library.shortcuts.order"))
    }

    @Test
    fun long_pressing_a_shortcut_past_the_next_tile_reorders_and_persists_it() {
        drag(
            sourceDescription = "继续阅读，长按多选，移动可拖动排序",
            targetDescription = "最近阅读，长按多选，移动可拖动排序",
            dropAfterTarget = true,
        )

        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ShortcutMoved")
        }
        val persisted = stateFile.readText()
        assertTrue(persisted.indexOf("recent") < persisted.indexOf("continue"))
    }

    @Test
    fun fifth_shortcut_is_partially_visible_and_can_be_dragged() {
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val fourthBounds = composeRule
            .onNodeWithContentDescription("追更，长按多选，移动可拖动排序")
            .fetchSemanticsNode()
            .boundsInRoot
        val fifthBounds = composeRule
            .onNodeWithContentDescription("夜航船，长按多选，移动可拖动排序")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(fourthBounds.right < rootBounds.right)
        assertTrue(fifthBounds.left < rootBounds.right)
        assertTrue(fifthBounds.width in (fourthBounds.width * 0.4f)..(fourthBounds.width * 0.7f))

        drag(
            sourceDescription = "夜航船，长按多选，移动可拖动排序",
            targetDescription = "继续阅读，长按多选，移动可拖动排序",
        )

        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ShortcutMoved")
        }
        val order = storedList("library.shortcuts.order")
        assertTrue(order.indexOf("night-boat") < order.indexOf("recent"))
    }

    @Test
    fun collection_shortcut_uses_stacked_cover_preview() {
        composeRule
            .onNodeWithTag("shortcut-collection-stack-night-boat", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun delete_target_stays_close_to_bottom_navigation() {
        val targetBounds = composeRule
            .onNodeWithTag("shortcut-delete-drop-target")
            .fetchSemanticsNode()
            .boundsInRoot
        val reviewCenterY = composeRule
            .onNodeWithText("审阅")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        val gap = reviewCenterY - targetBounds.center.y
        assertTrue("delete target gap=$gap bounds=$targetBounds", gap in 0f..(targetBounds.height * 2.75f))
    }

    private fun assertStandardCoverRatio(width: Float, height: Float) {
        val ratio = height / width
        assertTrue("expected 4:3 cover ratio, actual=$ratio ($width x $height)", kotlin.math.abs(ratio - 4f / 3f) <= 0.03f)
    }
    @Test
    fun library_grid_uses_three_columns() {
        val books = composeRule
            .onAllNodesWithContentDescription("长按多选，移动可拖动至快捷书架", substring = true)
            .fetchSemanticsNodes()
            .sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
        assertTrue(books.size >= 4)
        assertTrue(books[0].boundsInRoot.top == books[1].boundsInRoot.top)

        assertTrue(books[1].boundsInRoot.top == books[2].boundsInRoot.top)
        assertTrue(books[3].boundsInRoot.top > books[0].boundsInRoot.top)
    }

    @Test
    fun layout_toggle_reports_the_current_layout_globally() {
        assertTrue(AtlasLayout.LIST.currentLayoutIcon() == AtlasIcons.LayoutList)
        assertTrue(AtlasLayout.COMPACT.currentLayoutIcon() == AtlasIcons.LayoutCompact)
        assertTrue(AtlasLayout.GRID.currentLayoutIcon() == AtlasIcons.LayoutGrid)
        assertTrue(AtlasLayout.LIST.layoutToggleContentDescription() == "当前布局：列表，点按切换布局")
        assertTrue(AtlasLayout.COMPACT.layoutToggleContentDescription() == "当前布局：紧凑列表，点按切换布局")
        assertTrue(AtlasLayout.GRID.layoutToggleContentDescription() == "当前布局：网格，点按切换布局")

        composeRule.onNodeWithContentDescription("当前布局：网格，点按切换布局").performClick()
        composeRule.onNodeWithContentDescription("当前布局：列表，点按切换布局").assertExists()
    }

    @Test
    fun view_all_uses_three_columns_and_child_collections_share_the_same_grid() {
        val shelfWidth = composeRule
            .onNodeWithContentDescription("继续阅读，长按多选，移动可拖动排序")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        composeRule.onNodeWithText("全部快捷内容").assertExists()
        val expanded = composeRule
            .onAllNodesWithContentDescription("长按多选，移动可拖动排序", substring = true)
            .fetchSemanticsNodes()
            .sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
        assertStandardCoverRatio(expanded[0].boundsInRoot.width, expanded[0].boundsInRoot.height)
        assertTrue(expanded.size >= 4)
        assertTrue(expanded[0].boundsInRoot.top == expanded[1].boundsInRoot.top)
        assertTrue(expanded[1].boundsInRoot.top == expanded[2].boundsInRoot.top)
        assertTrue(expanded[3].boundsInRoot.top > expanded[0].boundsInRoot.top)
        val expandedWidth = expanded[0].boundsInRoot.width
        assertTrue(expandedWidth > shelfWidth)

        pressBack()
        composeRule.onNodeWithContentDescription("夜航船，长按多选，移动可拖动排序").performClick()
        composeRule.onNodeWithText("收藏夹（2）").assertExists()
        val childBounds = composeRule
            .onNodeWithContentDescription("志怪选，长按多选，移动可拖动排序")
            .fetchSemanticsNode()
            .boundsInRoot
        val siblingBounds = composeRule
            .onNodeWithContentDescription("科幻·未读，长按多选，移动可拖动排序")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue("expanded=$expandedWidth child=${childBounds.width}", kotlin.math.abs(expandedWidth - childBounds.width) <= 1f)
        assertTrue(kotlin.math.abs(childBounds.width - siblingBounds.width) <= 1f)
        assertTrue(kotlin.math.abs(childBounds.height - siblingBounds.height) <= 1f)
        assertTrue(kotlin.math.abs(childBounds.top - siblingBounds.top) <= 1f)
        assertStandardCoverRatio(childBounds.width, childBounds.height)

        composeRule.onNodeWithContentDescription("当前布局：列表，点按切换布局").performClick()
        composeRule.onNodeWithContentDescription("当前布局：紧凑列表，点按切换布局").performClick()
        val directBookBounds = composeRule
            .onAllNodesWithContentDescription("长按多选，移动可拖动排序或移出收藏夹", substring = true)[0]
            .fetchSemanticsNode()
            .boundsInRoot
        val refreshedChildBounds = composeRule
            .onNodeWithContentDescription("志怪选，长按多选，移动可拖动排序")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(kotlin.math.abs(refreshedChildBounds.width - directBookBounds.width) <= 1f)
        assertStandardCoverRatio(directBookBounds.width, directBookBounds.height)
    }

    @Test
    fun smart_child_back_returns_to_parent_before_library_root() {
        composeRule.onNodeWithContentDescription("夜航船，长按多选，移动可拖动排序").performClick()
        composeRule.onNodeWithContentDescription("科幻·未读，长按多选，移动可拖动排序").performClick()
        composeRule.onNodeWithText("规则").assertExists()

        composeRule.onNodeWithContentDescription("返回上级").performClick()
        composeRule.onNodeWithText("收藏夹（2）").assertExists()

        composeRule.onNodeWithContentDescription("科幻·未读，长按多选，移动可拖动排序").performClick()
        pressBack()
        composeRule.onNodeWithText("收藏夹（2）").assertExists()

        pressBack()
        composeRule.onNodeWithText("快捷书架").assertExists()
    }

    @Test
    fun manual_child_collections_open_as_dedicated_pages_without_teaching_copy() {
        composeRule.onNodeWithContentDescription("夜航船，长按多选，移动可拖动排序").performClick()
        composeRule.onNodeWithText("不含子收藏夹", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("不计入直属", substring = true).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("志怪选，长按多选，移动可拖动排序").performClick()
        composeRule.onNodeWithText("志怪选").assertExists()
        composeRule.onNodeWithText("收藏夹（1）").assertExists()
        composeRule.onNodeWithContentDescription("已读归档，长按多选，移动可拖动排序").performClick()
        composeRule.onNodeWithText("已读归档").assertExists()

        pressBack()
        composeRule.onNodeWithText("志怪选").assertExists()
        pressBack()
        composeRule.onNodeWithText("夜航船").assertExists()
        pressBack()
        composeRule.onNodeWithText("快捷书架").assertExists()
    }

    @Test
    fun website_mirror_folders_use_the_same_page_grid_and_hierarchy() {
        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        composeRule.onNodeWithContentDescription("源·松镜像，长按多选，移动可拖动排序").performClick()
        composeRule.onNodeWithText("收藏夹（3）").assertExists()
        val rootFolder = composeRule.onNodeWithContentDescription("默认收藏夹").fetchSemanticsNode().boundsInRoot
        assertStandardCoverRatio(rootFolder.width, rootFolder.height)

        composeRule.onNodeWithContentDescription("默认收藏夹").performClick()
        composeRule.onNodeWithText("默认收藏夹").assertExists()
        composeRule.onNodeWithText("收藏夹（2）").assertExists()
        composeRule.onNodeWithContentDescription("武侠").performClick()
        composeRule.onNodeWithText("武侠").assertExists()
        composeRule.onNodeWithText("纸灯巷的守夜人").assertExists()

        pressBack()
        composeRule.onNodeWithText("默认收藏夹").assertExists()
        pressBack()
        composeRule.onNodeWithText("源·松").assertExists()
        pressBack()
        composeRule.onNodeWithText("快捷书架").assertExists()
    }

    @Test
    fun library_content_meets_bottom_navigation_without_blank_reserve() {
        val contentBottom = composeRule.onNodeWithTag("library-book-surface").fetchSemanticsNode().boundsInRoot.bottom
        val navigationTop = composeRule.onNodeWithTag("atlas-bottom-navigation").fetchSemanticsNode().boundsInRoot.top
        assertTrue("contentBottom=$contentBottom navigationTop=$navigationTop", kotlin.math.abs(contentBottom - navigationTop) <= 1f)
    }

    @Test
    fun expanded_shortcuts_and_collection_pages_enter_batch_mode_from_long_press() {
        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        composeRule.onAllNodesWithContentDescription("حكاية المقهى القديم 老咖啡馆的故事，长按多选，移动可拖动排序")[0]
            .performTouchInput { longClick() }
        assertTrue(composeRule.onAllNodesWithContentDescription("移出快捷书架", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithContentDescription("退出选择").performClick()
        pressBack()
        composeRule.onNodeWithContentDescription("夜航船，长按多选，移动可拖动排序").performClick()
        composeRule.onAllNodesWithText(LibraryAtlasFixtures.manualDetailBooks.first().title)[0]
            .performTouchInput { longClick() }
        assertTrue(composeRule.onAllNodesWithContentDescription("移出此收藏夹", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithContentDescription("退出选择").performClick()
        composeRule.onNodeWithContentDescription("科幻·未读，长按多选，移动可拖动排序").performClick()
        composeRule.onAllNodesWithText(LibraryAtlasFixtures.smartDetailBooks.first().title)[0]
            .performTouchInput { longClick() }
        assertTrue(composeRule.onAllNodesWithContentDescription("移出总书架", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun library_sorting_supports_title_recent_reading_and_rating_in_both_directions() {
        val books = LibraryAtlasFixtures.viewFixture(org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView.ALL).books

        val titleAscending = books.orderedForLibrary(
            LibraryBookSortMode.TITLE,
            LibraryBookSortDirection.ASCENDING,
        )
        val titleDescending = books.orderedForLibrary(
            LibraryBookSortMode.TITLE,
            LibraryBookSortDirection.DESCENDING,
        )
        assertEquals(titleAscending.map { it.title }.reversed(), titleDescending.map { it.title })

        val recentDescending = books.orderedForLibrary(
            LibraryBookSortMode.RECENTLY_READ,
            LibraryBookSortDirection.DESCENDING,
        )
        val recentValues = recentDescending.mapNotNull { it.lastReadAtEpochMillis }
        assertEquals(recentValues.sortedDescending(), recentValues)
        assertTrue(recentDescending.drop(recentValues.size).all { it.lastReadAtEpochMillis == null })

        val ratingAscending = books.orderedForLibrary(
            LibraryBookSortMode.RATING,
            LibraryBookSortDirection.ASCENDING,
        )
        val ratingValues = ratingAscending.mapNotNull { it.rating }
        assertEquals(ratingValues.sorted(), ratingValues)
        assertTrue(ratingAscending.drop(ratingValues.size).all { it.rating == null })
    }

    @Test
    fun sort_dialog_exposes_all_modes_and_persists_direction() {
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("排序：自定义").performClick()
        composeRule.onNodeWithText("标题").assertExists()
        assertTrue(composeRule.onAllNodesWithText("最近阅读").fetchSemanticsNodes().size >= 2)
        composeRule.onNodeWithText("评分").assertExists()

        composeRule.onNodeWithText("标题").performClick()
        composeRule.onNodeWithText("降序").performClick()
        composeRule.onNodeWithText("完成").performClick()
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("排序：标题 · 降序").assertExists()
        assertTrue(stateFile.readText().contains("LibrarySortDirectionChanged"))

        scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("排序：标题 · 降序").assertExists()
    }

    @Test
    fun library_sync_and_update_check_is_a_visible_top_bar_action() {
        composeRule.onNodeWithContentDescription("同步并检查更新").assertExists().performClick()
        composeRule.onNodeWithText("正在同步书架并检查更新…").assertExists()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("PrototypeActionFinished")
        }
        composeRule.onNodeWithText("同步完成", substring = true).assertExists()
    }

    @Test
    fun stationary_long_press_enters_selection_at_timeout_before_release() {
        val book = composeRule.onNodeWithContentDescription("山中邮差，长按多选，移动可拖动至快捷书架")
        book.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("已选择").assertExists()
        composeRule.onNodeWithContentDescription("用所选新建收藏夹").assertExists()
        composeRule.onNodeWithContentDescription("移入收藏夹").assertExists()
        composeRule.onNodeWithContentDescription("移出书架").assertExists()
        composeRule.onNodeWithText("进入多选").assertDoesNotExist()
    }

    @Test
    fun title_sorted_library_does_not_accept_manual_reordering() {
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("排序：自定义").performClick()
        composeRule.onNodeWithText("标题").performClick()
        composeRule.onNodeWithText("完成").performClick()
        drag(
            sourceDescription = "纸灯巷的守夜人，长按多选，移动可拖动至快捷书架",
            targetDescription = "半亩方塘一鉴开，长按多选，移动可拖动至快捷书架",
        )
        assertTrue(!stateFile.exists() || !stateFile.readText().contains("LibraryBooksReordered"))
        assertTrue(storedList("library.order.all").isEmpty())
    }

    @Test
    fun list_and_compact_drags_use_distinct_layout_preview_contracts() {
        val list = libraryDragPreviewSize(AtlasLayout.LIST)
        val compact = libraryDragPreviewSize(AtlasLayout.COMPACT)
        val grid = libraryDragPreviewSize(AtlasLayout.GRID)

        assertTrue(list.height > compact.height)
        assertTrue(list.width > compact.width)
        assertTrue(grid.height > list.height)
    }

    @Test
    fun locked_shortcuts_and_collection_children_enter_selection_without_item_menus() {
        composeRule.onNodeWithContentDescription("快捷书架未锁定，点按锁定").performClick()
        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        composeRule.onAllNodesWithContentDescription("حكاية المقهى القديم 老咖啡馆的故事")[0].performTouchInput {
            longClick()
        }
        composeRule.onNodeWithContentDescription("移出快捷书架").assertExists()
        composeRule.onNodeWithText("进入多选").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("退出选择").performClick()
        pressBack()

        composeRule.onNodeWithContentDescription("夜航船").performClick()
        composeRule.onNodeWithContentDescription("志怪选，长按多选，移动可拖动排序").performTouchInput {
            longClick()
        }
        composeRule.onNodeWithContentDescription("删除收藏夹").assertExists()
        composeRule.onNodeWithText("编辑文件夹规则").assertDoesNotExist()
    }


    @Test
    fun manual_collection_drop_and_removals_keep_local_scope() {
        composeRule.onNodeWithContentDescription("夜航船，长按多选，移动可拖动排序").performClick()
        composeRule.onNodeWithContentDescription("当前布局：列表，点按切换布局").performClick()
        composeRule.onNodeWithContentDescription("当前布局：紧凑列表，点按切换布局").performClick()
        val book = LibraryAtlasFixtures.manualDetailBooks.first()
        val description = "${book.title}，长按多选，移动可拖动排序或移出收藏夹"

        drag(description, targetDescription = "志怪选，长按多选，移动可拖动排序")
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("CollectionBooksDroppedIntoChild")
        }
        assertTrue(book.id in storedList("library.collection.col-zhiguai.books"))

        composeRule.onAllNodesWithContentDescription(description)[0].performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("移出此收藏夹").performClick()
        composeRule.onNodeWithText("从「夜航船」移出 1 本书？").assertExists()
        composeRule.onNodeWithText("仅移除当前收藏关系；书籍仍保留在总书架。").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithContentDescription("退出选择").performClick()

        composeRule.onNodeWithContentDescription("志怪选，长按多选，移动可拖动排序").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("删除收藏夹").performClick()
        composeRule.onNodeWithText("删除 1 个收藏夹？").assertExists()
        composeRule.onNodeWithText("书籍仍保留在总书架；收藏关系会被移除。").assertExists()
        composeRule.onNodeWithText("取消").performClick()
    }
    @Test
    fun rightmost_shortcut_and_more_navigation_are_clickable() {
        composeRule.onNodeWithContentDescription("追更，长按多选，移动可拖动排序").performClick()
        composeRule.onNodeWithText("追更").assertExists()

        composeRule.onNodeWithText("更多").performClick()
        composeRule.onNodeWithText("界面与阅读").assertExists()
    }

    @Test
    fun history_removal_actions_require_confirmation_before_persisting() {
        composeRule.onNodeWithContentDescription("最近阅读，长按多选，移动可拖动排序").performClick()

        composeRule.onAllNodesWithContentDescription("从历史移除", substring = true)[0].performClick()
        composeRule.onNodeWithText("从历史中移除？").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        assertTrue(storedList("history.removed").isEmpty())

        composeRule.onAllNodesWithText(LibraryAtlasFixtures.historyGroups.first().entries.first().book.title)[0]
            .performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("移除所选").performClick()
        composeRule.onNodeWithText("从历史中移除 1 条记录？").assertExists()

        composeRule.onNodeWithText("取消").performClick()
        assertTrue(storedList("history.removed").isEmpty())
        composeRule.onNodeWithContentDescription("退出选择").performClick()
        composeRule.onAllNodesWithText(
            LibraryAtlasFixtures.historyGroups.first().entries.first().book.title,
        )[0].performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("退出选择").assertExists()
        composeRule.onNodeWithContentDescription("退出选择").performClick()

        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("清空历史").performClick()
        composeRule.onNodeWithText("清空全部历史？").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        assertTrue(storedList("history.removed").isEmpty())

        composeRule.onAllNodesWithContentDescription("从历史移除", substring = true)[0].performClick()
        composeRule.onNodeWithText("移除").performClick()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("HistoryEntryRemoved")
        }
        assertTrue(storedList("history.removed").isNotEmpty())
    }

    @Test
    fun dragging_a_shortcut_to_the_delete_target_requires_confirmation_then_persists() {
        dragToDelete("继续阅读，长按多选，移动可拖动排序")

        composeRule.onNodeWithText("移出快捷书架？").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        assertFalse(stateFile.exists())
        assertTrue(
            composeRule.onAllNodesWithContentDescription("继续阅读，长按多选，移动可拖动排序")
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )

        dragToDelete("继续阅读，长按多选，移动可拖动排序")
        composeRule.onNodeWithText("移出").performClick()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ShortcutRemoved")
        }
        assertFalse("continue" in storedList("library.shortcuts.order"))
        assertTrue("continue" in storedList("library.shortcuts.hidden"))

        scenario.recreate()
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithContentDescription("继续阅读，长按多选，移动可拖动排序")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun dragging_a_user_collection_to_delete_still_requires_confirmation() {
        createUserCollection("待删夹")
        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        dragToDelete("待删夹，长按多选，移动可拖动排序")

        composeRule.onNodeWithText("删除收藏夹？").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithContentDescription("待删夹，长按多选，移动可拖动排序").assertExists()
    }

    @Test
    fun dragging_a_shortcut_book_to_delete_only_removes_its_shortcut() {
        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        val description = duplicateShortcutBookDescription()
        val initialShortcutCount = composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

        dragToDelete(description, sourceIndex = 0)

        composeRule.onNodeWithText("移出快捷书架？").assertDoesNotExist()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ShortcutRemoved")
        }
        assertTrue(storedList("library.removed.bookIds").isEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size == initialShortcutCount - 1)

        composeRule.onNodeWithContentDescription("返回上级").performClick()
        assertTrue(
            composeRule.onAllNodesWithContentDescription(
                description.replace("，长按多选，移动可拖动排序", "，长按多选，移动可拖动至快捷书架"),
            ).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun dragging_a_book_to_the_delete_target_requires_confirmation_then_removes_from_library() {
        val initialBookCount = composeRule
            .onAllNodesWithContentDescription("山中邮差，长按多选，移动可拖动至快捷书架")
            .fetchSemanticsNodes()
            .size
        dragToDelete("山中邮差，长按多选，移动可拖动至快捷书架")

        composeRule.onNodeWithText("移出总书架？").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        assertFalse(stateFile.exists())
        assertTrue(
            composeRule.onAllNodesWithContentDescription("山中邮差，长按多选，移动可拖动至快捷书架")
                .fetchSemanticsNodes()
                .size == initialBookCount,
        )

        dragToDelete("山中邮差，长按多选，移动可拖动至快捷书架")
        composeRule.onNodeWithText("移出").performClick()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("BookRemovedFromLibrary")
        }
        assertTrue(storedList("library.removed.bookIds").isNotEmpty())
        assertTrue(
            composeRule.onAllNodesWithContentDescription("山中邮差，长按多选，移动可拖动至快捷书架")
                .fetchSemanticsNodes()
                .size == initialBookCount - 1,
        )

        scenario.recreate()
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithContentDescription("山中邮差，长按多选，移动可拖动至快捷书架")
                .fetchSemanticsNodes()
                .size == initialBookCount - 1,
        )
    }

    @Test
    fun library_has_no_explicit_multiselect_entry_and_long_press_exposes_batch_actions() {
        composeRule.onNodeWithContentDescription("当前布局：网格，点按切换布局").assertExists()
        composeRule.onNodeWithContentDescription("多选").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("山中邮差，长按多选，移动可拖动至快捷书架").performTouchInput {
            longClick()
        }
        composeRule.onNodeWithContentDescription("已选择").assertExists()
        composeRule.onNodeWithContentDescription("用所选新建收藏夹").assertExists()
        composeRule.onNodeWithContentDescription("移入收藏夹").assertExists()
        composeRule.onNodeWithContentDescription("移出书架").assertExists()
    }

    @Test
    fun select_all_toggles_to_clear_all_and_updates_its_action_semantics() {
        val allBookCount = LibraryAtlasFixtures.viewFixture(org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView.ALL).books.size
        composeRule.onNodeWithContentDescription("山中邮差，长按多选，移动可拖动至快捷书架")
            .performTouchInput { longClick() }

        composeRule.onNodeWithContentDescription("全选").performClick()
        composeRule.onNodeWithText("已选 $allBookCount").assertExists()
        composeRule.onNodeWithContentDescription("清除所有选择").assertExists()
        composeRule.onNodeWithContentDescription("全选").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("清除所有选择").performClick()
        composeRule.onNodeWithText("已选 0").assertExists()
        composeRule.onNodeWithContentDescription("全选").assertExists()
        composeRule.onNodeWithContentDescription("清除所有选择").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("移出书架").assertDoesNotExist()
    }

    @Test
    fun long_pressing_unselected_books_only_adds_selection_and_stationary_selected_hold_does_not_drop() {
        composeRule.onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
            .performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("星海拾荒者，长按多选，移动可拖动至快捷书架")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("已选 2").assertExists()

        composeRule.onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("已选 2").assertExists()
        assertFalse(stateFile.exists())
    }

    @Test
    fun selected_books_create_a_collection_from_the_selection_bar() {
        composeRule.onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
            .performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("星海拾荒者，长按多选，移动可拖动至快捷书架").performClick()
        composeRule.onNodeWithContentDescription("用所选新建收藏夹").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("双书夹")
        composeRule.onNodeWithText("创建").performClick()

        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("CollectionShortcutCreated")
        }
        assertEquals(2, storedList("library.collection.user-1.books").size)
        composeRule.onNodeWithContentDescription("退出选择").assertDoesNotExist()
    }

    @Test
    fun library_shortcut_header_opens_collection_creation_dialog() {
        composeRule.onNodeWithContentDescription("新建收藏夹").performClick()
        composeRule.onNodeWithText("新建收藏夹").assertExists()
        composeRule.onNodeWithText("收藏夹名称").assertExists()
        composeRule.onNodeWithText("取消").performClick()
    }

    @Test
    fun long_pressing_a_book_enters_selection_without_an_item_menu() {
        composeRule.onNodeWithContentDescription("山中邮差，长按多选，移动可拖动至快捷书架").performTouchInput {
            longClick()
        }
        composeRule.onNodeWithContentDescription("已选择").assertExists()
        composeRule.onNodeWithContentDescription("移入收藏夹").assertExists()
        composeRule.onNodeWithContentDescription("移出书架").assertExists()
        composeRule.onNodeWithText("进入多选").assertDoesNotExist()
        composeRule.onNodeWithText("移出书架…").assertDoesNotExist()
    }


    @Test
    fun system_nodes_open_dedicated_pages_and_back_to_library_root() {
        composeRule.onNodeWithContentDescription("继续阅读，长按多选，移动可拖动排序").performClick()
        composeRule.onNodeWithText("继续阅读").assertExists()
        composeRule.onNodeWithText("快捷书架").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回上级").performClick()
        composeRule.onNodeWithContentDescription("继续阅读，长按多选，移动可拖动排序").assertExists()

        composeRule.onNodeWithContentDescription("稍后再读，长按多选，移动可拖动排序").performClick()
        composeRule.onNodeWithText("稍后再读").assertExists()
        composeRule.onNodeWithText("快捷书架").assertDoesNotExist()
        pressBack()
        composeRule.onNodeWithContentDescription("稍后再读，长按多选，移动可拖动排序").assertExists()
    }
    @Test
    fun exiting_selection_restores_book_dragging() {
        composeRule.onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
            .performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("退出选择").performClick()

        drag(
            sourceDescription = "纸灯巷的守夜人，长按多选，移动可拖动至快捷书架",
            targetText = "快捷书架",
        )
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("ShortcutBookDropped")
        }
    }

    @Test
    fun dragging_a_book_over_another_library_book_reorders_the_default_library() {
        drag(
            sourceDescription = "纸灯巷的守夜人，长按多选，移动可拖动至快捷书架",
            targetDescription = "半亩方塘一鉴开，长按多选，移动可拖动至快捷书架",
        )
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("LibraryBooksReordered")
        }
        assertTrue(storedList("library.order.all").size >= 20)
        val first = composeRule.onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithContentDescription("半亩方塘一鉴开，长按多选，移动可拖动至快捷书架").fetchSemanticsNode().boundsInRoot
        assertTrue(second.top < first.top || second.left < first.left)
    }

    @Test
    fun selected_books_drag_into_a_collection_as_one_batch() {
        composeRule.onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
            .performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("星海拾荒者，长按多选，移动可拖动至快捷书架").performClick()
        composeRule.onNodeWithContentDescription("继续阅读，长按多选，移动可拖动排序").performTouchInput {
            down(centerRight)
            moveTo(centerLeft, 200)
            up()
        }
        composeRule.waitForIdle()

        dragSelected(
            sourceDescription = "纸灯巷的守夜人，长按多选，移动可拖动至快捷书架",
            targetDescription = "夜航船，长按多选，移动可拖动排序",
        )
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("BooksDroppedIntoCollection")
        }
        assertTrue(storedList("library.collection.night-boat.books").size == 2)
    }

    @Test
    fun selected_books_long_press_drag_to_delete_uses_the_batch_payload() {
        composeRule.onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
            .performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("星海拾荒者，长按多选，移动可拖动至快捷书架").performClick()

        dragSelectedToDelete("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
        composeRule.onNodeWithText("移出 2 本书？").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("已选 2").assertExists()
    }

    @Test
    fun selected_books_dropped_on_the_shortcut_shelf_create_one_named_collection() {
        val allBooks = LibraryAtlasFixtures.viewFixture(org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView.ALL).books
        val firstBook = allBooks.first { it.title == "纸灯巷的守夜人" }
        val secondBook = allBooks.first { it.title == "星海拾荒者" }
        composeRule.onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
            .performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("星海拾荒者，长按多选，移动可拖动至快捷书架").performClick()

        dragSelected(
            sourceDescription = "纸灯巷的守夜人，长按多选，移动可拖动至快捷书架",
            targetDescription = "继续阅读，长按多选，移动可拖动排序",
        )
        composeRule.onNodeWithText("新建收藏夹").assertExists()
        composeRule.onNode(hasSetTextAction()).performTextInput("批量新夹")
        composeRule.onNodeWithText("创建").performClick()

        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("CollectionShortcutCreated")
        }
        assertEquals(setOf(firstBook.id, secondBook.id), storedList("library.collection.user-1.books").toSet())
        val shortcutOrder = storedList("library.shortcuts.order")
        assertTrue("user-1" in shortcutOrder)
        assertTrue(firstBook.id !in shortcutOrder && secondBook.id !in shortcutOrder)
        assertFalse(stateFile.readText().contains("BooksDroppedIntoShortcutShelf"))
    }

    @Test
    fun dropping_one_book_on_a_shortcut_book_creates_and_replaces_with_a_named_collection() {
        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        pinDuplicateShortcutBooks()
        val shortcutBook = duplicateShortcutBookDescription()
        drag(
            sourceDescription = shortcutBook,
            targetDescription = shortcutBook,
            sourceIndex = 0,
            targetIndex = 1,
        )
        composeRule.onNodeWithText("新建收藏夹").assertExists()
        composeRule.onNode(hasSetTextAction()).performTextInput("同行")
        composeRule.onNodeWithText("创建").performClick()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("CollectionShortcutCreated")
        }
        assertTrue(storedList("library.collection.user-1.books").size == 2)
        assertTrue("user-1" in storedList("library.shortcuts.order"))
        assertTrue(storedList("library.shortcuts.hidden").size == 1)
    }

    @Test
    fun dropping_selected_books_on_a_shortcut_book_creates_one_collection_with_all_members() {
        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        pinDuplicateShortcutBooks()
        val shortcutBook = duplicateShortcutBookDescription()
        val mainBook = shortcutBook.replace("，长按多选，移动可拖动排序", "，长按多选，移动可拖动至快捷书架")
        composeRule.onNodeWithContentDescription("返回上级").performClick()
        val extraBook = availableMainBookDescriptions().first { it != mainBook }
        composeRule.onAllNodesWithContentDescription(mainBook)[0].performTouchInput { longClick() }
        composeRule.onAllNodesWithContentDescription(extraBook)[0].performClick()
        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        drag(
            sourceDescription = shortcutBook,
            targetDescription = shortcutBook,
            sourceIndex = 0,
            targetIndex = 1,
        )
        composeRule.onNode(hasSetTextAction()).performTextInput("三人行")
        composeRule.onNodeWithText("创建").performClick()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("CollectionShortcutCreated")
        }
        assertTrue(storedList("library.collection.user-1.books").size == 3)
    }

    @Test
    fun batch_move_button_uses_the_collection_picker() {
        createUserCollection("收集箱")
        composeRule.onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
            .performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("星海拾荒者，长按多选，移动可拖动至快捷书架").performClick()
        composeRule.onNodeWithContentDescription("移入收藏夹").performClick()
        composeRule.onNodeWithText("收集箱").performClick()
        composeRule.waitUntil(5_000) { stateFile.readText().contains("BooksMovedIntoCollection") }
        assertTrue(storedList("library.collection.user-1.books").size == 2)
    }

    @Test
    fun batch_book_removal_requires_confirmation() {
        composeRule.onNodeWithContentDescription("纸灯巷的守夜人，长按多选，移动可拖动至快捷书架")
            .performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("星海拾荒者，长按多选，移动可拖动至快捷书架").performClick()
        composeRule.onNodeWithContentDescription("移出书架").performClick()
        composeRule.onNodeWithText("移出 2 本书？").assertExists()
        composeRule.onNodeWithText("取消").performClick()
        assertFalse(stateFile.exists())

        composeRule.onNodeWithContentDescription("移出书架").performClick()
        composeRule.onNodeWithText("移出").performClick()
        composeRule.waitUntil(5_000) { stateFile.readText().contains("BooksRemovedFromLibrary") }
        assertTrue(storedList("library.removed.bookIds").size == 2)
    }

    @Test
    fun book_and_user_collection_selection_cannot_be_mixed() {
        createUserCollection("用户夹")
        composeRule.onNodeWithContentDescription("查看全部快捷书架").performClick()
        composeRule.onNodeWithContentDescription("用户夹，长按多选，移动可拖动排序").performTouchInput {
            longClick()
        }
        assertTrue(composeRule.onAllNodes(hasStateDescription("已选择")).fetchSemanticsNodes().size == 1)

        composeRule.onAllNodesWithContentDescription(duplicateShortcutBookDescription())[0].performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodes(hasStateDescription("已选择")).fetchSemanticsNodes().size == 1)
    }

    private fun createUserCollection(name: String) {
        composeRule.onNodeWithContentDescription("新建收藏夹").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(name)
        composeRule.onNodeWithText("创建").performClick()
        composeRule.waitUntil(5_000) {
            stateFile.exists() && stateFile.readText().contains("CollectionShortcutCreated")
        }
    }

    private fun pinDuplicateShortcutBooks() {
        val description = duplicateShortcutBookDescription()
        drag(sourceDescription = description, targetDescription = "继续阅读，长按多选，移动可拖动排序", sourceIndex = 0)
        val remainingIndex = composeRule.onAllNodesWithContentDescription(description)
            .fetchSemanticsNodes()
            .withIndex()
            .maxBy { (_, node) -> node.boundsInRoot.top * 10_000f + node.boundsInRoot.left }
            .index
        drag(sourceDescription = description, targetDescription = "最近阅读，长按多选，移动可拖动排序", sourceIndex = remainingIndex)
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size >= 2)
    }


    private fun duplicateShortcutBookDescription(): String {
        val description = "حكاية المقهى القديم 老咖啡馆的故事，长按多选，移动可拖动排序"
        assertTrue(composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size >= 2)
        return description
    }

    private fun availableMainBookDescriptions(): List<String> = fixtureTitles
        .map { "$it，长按多选，移动可拖动至快捷书架" }
        .filter { composeRule.onAllNodesWithContentDescription(it).fetchSemanticsNodes().isNotEmpty() }

    private val fixtureTitles = listOf(
        "纸灯巷的守夜人", "半亩方塘一鉴开", "星海拾荒者", "青石镇异闻录",
        "凌晨四点的面包房", "雾都棋士", "沙丘译丛：失落航线", "猫、雨与旧书店",
        "山中邮差", "无名氏的植物图鉴", "霓虹深渊漫游指南 Neon Abyss Guide 2049",
        "风之谷的第三封信 ✉", "关于我在异世界经营深夜食堂却意外卷入魔王讨伐战并不得不一边炖汤一边拯救世界这件小事",
        "حكاية المقهى القديم 老咖啡馆的故事",
    )

    private fun dragSelectedToDelete(sourceDescription: String, sourceIndex: Int = 0) {
        val source = composeRule.onAllNodesWithContentDescription(sourceDescription)[sourceIndex]
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val destination = composeRule
            .onNodeWithTag("shortcut-delete-drop-target")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
        val delta = destination - sourceBounds.center

        source.performTouchInput {
            down(center)
            advanceEventTime(600)
            moveTo(center + delta, 300)
            up()
        }
        composeRule.waitForIdle()
    }

    private fun dragSelected(
        sourceDescription: String,
        targetDescription: String,
        sourceIndex: Int = 0,
        targetIndex: Int = 0,
    ) {
        val source = composeRule.onAllNodesWithContentDescription(sourceDescription)[sourceIndex]
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val destination = composeRule.onAllNodesWithContentDescription(targetDescription)[targetIndex]
            .fetchSemanticsNode()
            .boundsInRoot
            .center
        val delta = destination - sourceBounds.center

        source.performTouchInput {
            down(center)
            advanceEventTime(600)
            moveTo(center + delta, 300)
            up()
        }
        composeRule.waitForIdle()
    }

    private fun dragToDelete(sourceDescription: String, sourceIndex: Int = 0) {
        val source = composeRule.onAllNodesWithContentDescription(sourceDescription)[sourceIndex]
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val targetBounds = composeRule
            .onNodeWithTag("shortcut-delete-drop-target")
            .fetchSemanticsNode()
            .boundsInRoot
        val destination = targetBounds.center
        val delta = destination - sourceBounds.center

        source.performTouchInput {
            down(center)
            moveTo(center + Offset(36f, 0f), 160)
            advanceEventTime(500)
            moveTo(center + delta, 300)
            up()
        }
        composeRule.waitForIdle()
    }

    private fun drag(
        sourceDescription: String,
        targetText: String? = null,
        targetDescription: String? = null,
        dropAfterTarget: Boolean = false,
        sourceIndex: Int = 0,
        targetIndex: Int = 0,
    ) {
        val source = composeRule.onAllNodesWithContentDescription(sourceDescription)[sourceIndex]
        val target = if (targetText != null) {
            composeRule.onNodeWithText(targetText)
        } else {
            composeRule.onAllNodesWithContentDescription(requireNotNull(targetDescription))[targetIndex]
        }
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val targetBounds = target.fetchSemanticsNode().boundsInRoot
        val destination = if (dropAfterTarget) {
            Offset(targetBounds.right - 4f, targetBounds.center.y)
        } else {
            targetBounds.center
        }
        val delta = destination - sourceBounds.center

        source.performTouchInput {
            down(center)
            moveTo(center + Offset(36f, 0f), 160)
            advanceEventTime(500)
            moveTo(center + delta, 300)
            up()
        }
        composeRule.waitForIdle()
    }

    private fun storedList(key: String): List<String> {
        val encoded = JSONObject(stateFile.readText())
            .getJSONObject("values")
            .optString(key)
        return encoded.split('\u001f').filter(String::isNotEmpty)
    }

}
