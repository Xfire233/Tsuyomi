/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.yield
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import org.tsuyomi.core.preferences.LibraryPreferencesRepository
import java.io.File
import java.util.UUID
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.CollectionKind
import org.tsuyomi.shared.librarydomain.LibraryCollection
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.librarydomain.ReadingProgress
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.feature.library.projectedEntries
import org.tsuyomi.feature.library.SmartDraftNode
import org.tsuyomi.feature.library.updateAt

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

    @Before
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
                if (repository.libraryEntry(it) != null) {
                    repository.setReadLater(it, false)
                    repository.removeFromLibrary(it)
                }
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
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onNodeWithTag("tsuyomi-tab-CONTINUE").isDisplayed()
        }

        assertTrue(composeRule.onAllNodesWithText("本地藏书").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("library-shortcut-shelf").assertDoesNotExist()
        assertTrue(composeRule.onAllNodesWithText("书架").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("tsuyomi-tab-CONTINUE").assertIsDisplayed()
        composeRule.onNodeWithTag("tsuyomi-tab-READ_LATER").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("刷新").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("搜索").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("切换布局；当前为", substring = true).performClick()
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
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        waitForText("书架")
    }

    @Test
    fun root_manual_creation_picks_local_book_and_saves_membership() {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val title = "手动选书-${UUID.randomUUID()}"
        try {
            runBlocking { repository.addToLibrary(book(identity, "待选书")) }
            waitForText("书架")
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithText("新建收藏夹").performClick()
            waitForText("收藏夹名称")
            composeRule.onNodeWithText("选择书籍（已选 0 本）").assertIsDisplayed()
            composeRule.onNode(hasSetTextAction()).performTextReplacement(title)
            composeRule.onNodeWithText("选择书籍（已选 0 本）").performClick()
            composeRule.onNodeWithText("待选书").performClick()
            composeRule.onNodeWithContentDescription("完成选择").performClick()
            waitForText(title)
            composeRule.onNodeWithText("选择书籍（已选 1 本）").assertIsDisplayed()
            composeRule.onNodeWithText("选择书籍（已选 1 本）").performClick()
            composeRule.onNodeWithText("待选书").performClick()
            composeRule.onNodeWithContentDescription("取消选择").performClick()
            composeRule.onNodeWithText("选择书籍（已选 1 本）").assertIsDisplayed()
            composeRule.onNodeWithText("高级选项", substring = true).performClick()
            composeRule.onNodeWithText("选择书籍（已选 1 本）").assertDoesNotExist()
            composeRule.onNodeWithText("高级选项", substring = true).performClick()
            composeRule.onNodeWithText("选择书籍（已选 1 本）").assertIsDisplayed()
            composeRule.onNodeWithText("创建收藏夹").performClick()
            composeRule.waitUntil(10_000) { runBlocking { repository.collections().any { it.title == title } } }
            val created = runBlocking { repository.collections().single { it.title == title } }
            assertEquals(CollectionKind.MANUAL, created.kind)
            assertEquals(setOf(identity), runBlocking {
                repository.collectionEntries(created.collectionId).map { it.book.identity }.toSet()
            })
            assertEquals(null, runBlocking { repository.smartRule(created.collectionId) })
        } finally {
            runBlocking {
                repository.collections().filter { it.title == title }.forEach { repository.deleteCollection(it.collectionId) }
            }
        }
    }

    @Test
    fun root_creation_discloses_smart_fields_and_persists_chosen_no_value_filter() {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val title = "未安装来源筛选-${UUID.randomUUID()}"
        try {
            waitForText("书架")
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithText("新建收藏夹").performClick()
            composeRule.onNodeWithText("来源未安装").assertDoesNotExist()
            composeRule.onNodeWithText("高级选项", substring = true).performClick()
            composeRule.onNodeWithText("选择书籍（已选 0 本）").assertDoesNotExist()
            composeRule.onNodeWithText("按标签筛选").performScrollTo().performClick()
            composeRule.onNodeWithText("来源未安装").performScrollTo().performClick()
            composeRule.onNodeWithText("按来源未安装筛选").assertExists()
            composeRule.onNodeWithText("高级选项", substring = true).performClick()
            composeRule.onNodeWithText("选择书籍（已选 0 本）").assertIsDisplayed()
            composeRule.onNodeWithText("按来源未安装筛选").assertDoesNotExist()
            assertTrue(runBlocking { repository.collections().none { it.title == title } })
            composeRule.onNodeWithText("高级选项", substring = true).performClick()
            composeRule.onNodeWithText("按来源未安装筛选").assertExists()
            composeRule.onNode(hasSetTextAction()).performTextReplacement(title)
            saveVisibleRule()
            composeRule.waitUntil(10_000) { runBlocking { repository.collections().any { it.title == title } } }
            val created = runBlocking { repository.collections().single { it.title == title } }
            assertEquals(CollectionKind.SMART, created.kind)
            assertEquals(org.tsuyomi.shared.smartshelf.SmartPredicate.IsDormantSource,
                ((runBlocking { repository.smartRule(created.collectionId) }?.root as
                    org.tsuyomi.shared.smartshelf.SmartRuleNode.All).children.single() as
                    org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate).value)
        } finally {
            runBlocking { repository.collections().filter { it.title == title }.forEach {
                repository.deleteCollection(it.collectionId)
            } }
        }
    }

    @Test
    fun focused_nested_rule_recreates_invalid_facet_then_saves_same_identity() {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val id = "focus-${UUID.randomUUID()}"
        val original = org.tsuyomi.shared.smartshelf.SmartRule(root = org.tsuyomi.shared.smartshelf.SmartRuleNode.All(
            listOf(org.tsuyomi.shared.smartshelf.SmartRuleNode.Any(listOf(
                org.tsuyomi.shared.smartshelf.SmartRuleNode.All(listOf(
                    org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate(
                        org.tsuyomi.shared.smartshelf.SmartPredicate.FacetIn("source", setOf("分类,甲", "乙")),
                    ),
                )),
            ))),
        ))
        try {
            runBlocking {
                application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
                repository.createSmartCollection(LibraryCollection(id, CollectionKind.SMART, "深层规则-$id", null, 0), original)
            }
            waitForText("书架")
            composeRule.onNodeWithContentDescription("搜索").performClick()
            waitForText("搜索书架")
            composeRule.onNode(hasSetTextAction()).performTextReplacement("深层规则-$id")
            waitForText("深层规则-$id")
            composeRule.onNode(hasText("深层规则-$id") and hasClickAction() and !hasSetTextAction())
                .performClick()
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithText("编辑规则").performClick()
            waitForText("编辑智能收藏夹")
            val collapsedGroup = hasStateDescription("已收起")
            val expandedGroup = hasStateDescription("已展开")
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(collapsedGroup, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodes(expandedGroup, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            if (composeRule.onAllNodes(collapsedGroup, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNode(collapsedGroup, useUnmergedTree = true).performClick()
            }
            composeRule.onNodeWithText("进入分组：匹配全部 ›", useUnmergedTree = true)
                .performTouchInput { down(center); up() }
            waitForText("返回上级条件")
            composeRule.onAllNodes(hasSetTextAction())[1].performTextReplacement("")
            saveVisibleRule()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("请修正此条件", useUnmergedTree = true).assertExists()
            assertEquals(original, runBlocking { repository.smartRule(id) })
            composeRule.activityRule.scenario.recreate()
            waitForText("返回上级条件")
            assertTrue(composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty())
            composeRule.onNodeWithText("请修正此条件", useUnmergedTree = true).assertExists()
            composeRule.onAllNodes(hasSetTextAction())[1].performTextReplacement("other-source")
            saveVisibleRule()
            composeRule.waitUntil(10_000) {
                runBlocking {
                    val root = repository.smartRule(id)?.root as? org.tsuyomi.shared.smartshelf.SmartRuleNode.All
                    val any = root?.children?.singleOrNull() as? org.tsuyomi.shared.smartshelf.SmartRuleNode.Any
                    val all = any?.children?.singleOrNull() as? org.tsuyomi.shared.smartshelf.SmartRuleNode.All
                    val facet = (all?.children?.singleOrNull() as? org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate)?.value
                        as? org.tsuyomi.shared.smartshelf.SmartPredicate.FacetIn
                    facet?.sourceId == "other-source" && facet.facetIds == setOf("分类,甲", "乙")
                }
            }
            assertEquals(id, runBlocking { repository.collections().single { it.title == "深层规则-$id" }.collectionId })
        } finally {
            runBlocking { repository.deleteCollection(id) }
        }
    }

    @Test
    fun large_valid_smart_rule_recreates_without_bundling_tree_and_keeps_identity() {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val id = "large-draft-${UUID.randomUUID()}"
        val originalTitle = "大规则-$id"
        val changedTitle = "大规则修改-" + "书".repeat(290)
        val original = org.tsuyomi.shared.smartshelf.SmartRule(root =
            org.tsuyomi.shared.smartshelf.SmartRuleNode.All((0 until 13).map { group ->
                org.tsuyomi.shared.smartshelf.SmartRuleNode.Any(listOf(
                    org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate(
                        org.tsuyomi.shared.smartshelf.SmartPredicate.FacetIn(
                            "fixture.unmatched.source", (0 until 64).map { term ->
                                "${group}-${term}-" + "书".repeat(250)
                            }.toSet(),
                        ),
                    ),
                ))
            }),
        )
        try {
            runBlocking {
                application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
                repository.createSmartCollection(LibraryCollection(id, CollectionKind.SMART, originalTitle, null, 0), original)
                val controller = LibraryFlowController(repository, libraryPreferences)
                controller.reload("read failed")
                val completeTree = encodeRuleDraft(requireNotNull(controller.collectionRuleDraft(id)).tree).toString()
                assertTrue("Full original-predicate draft must exceed Binder-safe size",
                    completeTree.toByteArray(Charsets.UTF_8).size > 1_048_576)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val saved = SavedStateHandle()
                    val owner = CollectionRuleDraftOwner(application, saved)
                    var coldOwner: CollectionRuleDraftOwner? = null
                    try {
                        owner.open(id) { requireNotNull(controller.collectionRuleDraft(id)) }
                        owner.edit { it.copy(title = changedTitle) }
                        assertTrue(owner.flush())
                        assertEquals(setOf("collection.rule.draft-id", "collection.rule.revision"), saved.keys())
                        coldOwner = CollectionRuleDraftOwner(application, SavedStateHandle(mapOf(
                            "collection.rule.draft-id" to owner.id,
                            "collection.rule.revision" to requireNotNull(owner.draft).revision,
                        )))
                        coldOwner.open(id) { error("Cold restore must use the durable multi-megabyte draft") }
                        assertEquals(owner.draft, coldOwner.draft)
                        assertEquals(changedTitle, coldOwner.draft?.title)
                    } finally {
                        coldOwner?.discard()
                        owner.discard()
                    }
                }
            }
            waitForText("书架")
            composeRule.onNodeWithContentDescription("搜索").performClick()
            waitForText("搜索书架")
            composeRule.onNode(hasSetTextAction()).performTextReplacement(originalTitle)
            waitForText(originalTitle)
            composeRule.onNode(hasText(originalTitle) and hasClickAction() and !hasSetTextAction()).performClick()
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithText("编辑规则").performClick()
            composeRule.waitUntil(30_000) {
                composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasSetTextAction()).performTextReplacement(changedTitle)
            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(30_000) {
                composeRule.onAllNodes(hasSetTextAction() and hasText(changedTitle))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(original, runBlocking { repository.smartRule(id) })
            saveVisibleRule()
            composeRule.waitUntil(30_000) {
                runBlocking { repository.collections().any { it.collectionId == id && it.title == changedTitle } }
            }
            assertEquals(original, runBlocking { repository.smartRule(id) })
            assertEquals(id, runBlocking { repository.collections().single { it.title == changedTitle }.collectionId })
        } finally {
            runBlocking { repository.deleteCollection(id) }
        }
    }

    @Test
    fun smart_rule_cold_restore_retains_route_state_and_missing_file_fails_closed() {
        val application = composeRule.activity.application as TsuyomiApplication
        runBlocking(Dispatchers.Main) {
        val originalPredicate = org.tsuyomi.shared.smartshelf.SmartRuleCodec.encode(
            org.tsuyomi.shared.smartshelf.SmartRule(root = org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate(
                org.tsuyomi.shared.smartshelf.SmartPredicate.TagContains(
                    org.tsuyomi.shared.smartshelf.MatchMode.ANY, setOf("原始标签"),
                ),
            )),
        )
        val initialTree = SmartDraftNode.Group(true, listOf(SmartDraftNode.Group(true, listOf(
            SmartDraftNode.Condition(
                org.tsuyomi.feature.library.SmartConditionDraft(value = "原始标签"),
                originalPredicateJson = originalPredicate,
            ),
        ))))
        val saved = SavedStateHandle()
        val owner = CollectionRuleDraftOwner(application, saved)
        var restored: CollectionRuleDraftOwner? = null
        try {
            owner.open(null) { CollectionRuleDraft("原始标题", initialTree) }
            val editedTree = initialTree.updateAt(listOf(0, 0)) { node ->
                (node as SmartDraftNode.Condition).copy(draft = node.draft.copy(value = "更改标签"))
            }
            owner.edit { it.copy(title = "更改标题", tree = editedTree,
                encodedTree = encodeRuleDraft(editedTree).toString(), focus = "0", attempted = true) }
            assertTrue(owner.flush())
            restored = CollectionRuleDraftOwner(application, SavedStateHandle(mapOf(
                "collection.rule.draft-id" to owner.id,
                "collection.rule.revision" to requireNotNull(owner.draft).revision,
            )))
            restored.open(null) { error("Active draft must not reload from collection") }
            assertEquals(owner.draft, restored.draft)
            assertFalse(restored.failed)

            val missing = CollectionRuleDraftOwner(application, SavedStateHandle(mapOf(
                "collection.rule.draft-id" to UUID.randomUUID().toString(),
                "collection.rule.revision" to 1,
            )))
            try {
                missing.open(null) { error("A missing active draft must not reload the original") }
                assertTrue(missing.failed)
                assertEquals(null, missing.draft)
            } finally {
                missing.discard()
            }
        } finally {
            restored?.discard()
            owner.discard()
        }
    }
    }

    @Test
    fun new_smart_rule_retry_after_commit_updates_same_identity_without_duplication() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val draftId = "retry-${UUID.randomUUID()}"
        val manualId = "manual-retry-${UUID.randomUUID()}"
        val first = SmartDraftNode.Group(true, listOf(SmartDraftNode.Condition(
            org.tsuyomi.feature.library.SmartConditionDraft(value = "奇幻"),
        )))
        val edited = first.updateAt(listOf(0)) { node ->
            (node as SmartDraftNode.Condition).copy(draft = node.draft.copy(value = "科幻"))
        }
        try {
            val controller = LibraryFlowController(repository, libraryPreferences)
            controller.reload("read failed")
            assertTrue(controller.saveSmartCollection(null, "首次提交", first, "read failed", draftId))
            // The route can survive a process death between Room's commit and the suspend reload.
            assertTrue(controller.saveSmartCollection(null, "继续修改", edited, "read failed", draftId))
            assertEquals(1, repository.collections().count { it.collectionId == draftId })
            assertEquals("继续修改", repository.collections().single { it.collectionId == draftId }.title)
            val root = repository.smartRule(draftId)?.root as org.tsuyomi.shared.smartshelf.SmartRuleNode.All
            val predicate = root.children.single() as org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate
            assertEquals(setOf("科幻"), (predicate.value as org.tsuyomi.shared.smartshelf.SmartPredicate.TagContains).tags)

            repository.createCollection(LibraryCollection(manualId, CollectionKind.MANUAL, "手动保留", null, 0))
            assertFalse(controller.saveSmartCollection(null, "不能改写", first, "read failed", manualId))
            assertEquals(CollectionKind.MANUAL, repository.collections().single { it.collectionId == manualId }.kind)
            assertEquals(null, repository.smartRule(manualId))
        } finally {
            repository.deleteCollection(draftId)
            repository.deleteCollection(manualId)
        }
    }

    @Test
    fun nested_group_facet_edit_preserves_untouched_nodes_and_collection_identity() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val id = "nested-${UUID.randomUUID()}"
        val original = org.tsuyomi.shared.smartshelf.SmartRule(root = org.tsuyomi.shared.smartshelf.SmartRuleNode.All(
            listOf(org.tsuyomi.shared.smartshelf.SmartRuleNode.Any(listOf(
                org.tsuyomi.shared.smartshelf.SmartRuleNode.Not(org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate(
                    org.tsuyomi.shared.smartshelf.SmartPredicate.FacetIn("source", setOf("分组,一", "收藏")),
                )),
                org.tsuyomi.shared.smartshelf.SmartRuleNode.Not(org.tsuyomi.shared.smartshelf.SmartRuleNode.Not(
                    org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate(
                        org.tsuyomi.shared.smartshelf.SmartPredicate.TagContains(
                            org.tsuyomi.shared.smartshelf.MatchMode.ALL, setOf("奇幻", "完结"),
                        ),
                    ),
                )),
            ))),
        ))
        try {
            repository.createSmartCollection(LibraryCollection(id, CollectionKind.SMART, "分组原名", null, 0), original)
            val controller = LibraryFlowController(repository, libraryPreferences)
            controller.reload("read failed")
            val draft = requireNotNull(controller.collectionRuleDraft(id))
            val nested = draft.tree.children.single() as SmartDraftNode.Group
            val facet = nested.children.first() as SmartDraftNode.Condition
            assertEquals(1, facet.negations)
            assertEquals(org.tsuyomi.feature.library.SmartField.FACET, facet.draft.field)
            val edited = draft.tree.updateAt(listOf(0, 0)) { node ->
                (node as SmartDraftNode.Condition).copy(draft = node.draft.copy(facetSourceId = "source-next"))
            }
            assertTrue(controller.saveSmartCollection(id, "分组改名", edited, "read failed"))
            val expected = original.copy(root = org.tsuyomi.shared.smartshelf.SmartRuleNode.All(listOf(
                org.tsuyomi.shared.smartshelf.SmartRuleNode.Any(listOf(
                    org.tsuyomi.shared.smartshelf.SmartRuleNode.Not(org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate(
                        org.tsuyomi.shared.smartshelf.SmartPredicate.FacetIn("source-next", setOf("分组,一", "收藏")),
                    )),
                    (original.root as org.tsuyomi.shared.smartshelf.SmartRuleNode.All)
                        .children.single().let { (it as org.tsuyomi.shared.smartshelf.SmartRuleNode.Any).children[1] },
                )),
            )))
            assertEquals(expected, repository.smartRule(id))
            assertEquals(id, repository.collections().single { it.title == "分组改名" }.collectionId)
        } finally {
            repository.deleteCollection(id)
        }
    }

    @Test
    fun existing_all_tag_rule_is_not_coerced_to_any_when_edited() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val id = "tag-all-${UUID.randomUUID()}"
        val original = org.tsuyomi.shared.smartshelf.SmartRule(root = org.tsuyomi.shared.smartshelf.SmartRuleNode.All(
            listOf(org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate(
                org.tsuyomi.shared.smartshelf.SmartPredicate.TagContains(
                    org.tsuyomi.shared.smartshelf.MatchMode.ALL, setOf("奇幻", "完结"),
                ),
            )),
        ))
        try {
            repository.createSmartCollection(LibraryCollection(id, CollectionKind.SMART, "原规则", null, 0), original)
            val controller = LibraryFlowController(repository, libraryPreferences)
            controller.reload("read failed")
            val draft = requireNotNull(controller.collectionRuleDraft(id))
            assertTrue(draft.conditions.single().matchAllTags)
            assertTrue(controller.saveSmartCollection(id, "改名后", draft.tree, "read failed"))
            assertEquals(original, repository.smartRule(id))
            assertEquals(id, repository.collections().single { it.title == "改名后" }.collectionId)
        } finally {
            repository.deleteCollection(id)
        }
    }

    @Test
    fun smart_rule_picks_existing_local_and_source_tags_then_filters_saved_books() {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val title = "筛选已有标签-${UUID.randomUUID()}"
        val books = listOf(identity, behaviorNewer, behaviorOlder, behaviorUnstarted)
        try {
            runBlocking {
                application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
                repository.addToLibrary(book(identity, "无标签"))
                repository.addToLibrary(book(behaviorNewer, "本地标签书"))
                repository.addToLibrary(book(behaviorOlder, "来源标签书").copy(remoteTags = setOf("来源甲")))
                repository.addToLibrary(book(behaviorUnstarted, "两个标签书").copy(remoteTags = setOf("来源甲")))
                repository.setLocalTags(behaviorNewer, setOf("本地乙"))
                repository.setLocalTags(behaviorUnstarted, setOf("本地乙"))
            }
            waitForText("书架")
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithText("新建收藏夹").performClick()
            composeRule.onNodeWithText("高级选项", substring = true).performClick()
            composeRule.onNode(hasSetTextAction()).performTextReplacement(title)
            composeRule.onNodeWithText("选择已有标签").performScrollTo().performClick()
            composeRule.onNodeWithText("本地乙").performClick()
            composeRule.onNodeWithText("来源甲").performClick()
            composeRule.onNodeWithText("选好了").performClick()
            composeRule.onNodeWithText("已选 2 个标签：", substring = true).assertExists()
            composeRule.onNodeWithText("包含任一已选标签").performScrollTo().performClick()
            saveVisibleRule()
            composeRule.waitUntil(10_000) { runBlocking { repository.collections().any { it.title == title } } }
            val created = runBlocking { repository.collections().single { it.title == title } }
            assertEquals(setOf(behaviorUnstarted), runBlocking {
                repository.collectionEntries(created.collectionId).map { it.book.identity }.toSet()
            })
            val savedAll = (runBlocking { repository.smartRule(created.collectionId) }?.root as
                org.tsuyomi.shared.smartshelf.SmartRuleNode.All).children.single() as
                org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate
            val allTags = savedAll.value as org.tsuyomi.shared.smartshelf.SmartPredicate.TagContains
            assertEquals(setOf("本地乙", "来源甲"), allTags.tags)
            assertEquals(org.tsuyomi.shared.smartshelf.MatchMode.ALL, allTags.mode)

            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithContentDescription("搜索").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("搜索").performClick()
            waitForText("搜索书架")
            composeRule.onNode(hasSetTextAction()).performTextReplacement(title)
            waitForText(title)
            composeRule.onNode(hasText(title) and hasClickAction() and !hasSetTextAction()).performClick()
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithText("编辑规则").performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("包含所有已选标签").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("包含所有已选标签").performScrollTo().performClick()
            saveVisibleRule()
            composeRule.waitUntil(10_000) { runBlocking {
                repository.collectionEntries(created.collectionId).map { it.book.identity }.toSet() ==
                    setOf(behaviorNewer, behaviorOlder, behaviorUnstarted)
            } }
            assertEquals(setOf(behaviorNewer, behaviorOlder, behaviorUnstarted), runBlocking {
                repository.collectionEntries(created.collectionId).map { it.book.identity }.toSet()
            })
            assertEquals(created.collectionId, runBlocking { repository.collections().single { it.title == title } }.collectionId)
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithContentDescription("搜索").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("搜索").performClick()
            waitForText("搜索书架")
            composeRule.onNode(hasSetTextAction()).performTextReplacement(title)
            waitForText(title)
            composeRule.onNode(hasText(title) and hasClickAction() and !hasSetTextAction()).performClick()
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithText("编辑规则").performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("排除匹配此条件的书籍").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("排除匹配此条件的书籍").performScrollTo().performClick()
            saveVisibleRule()
            composeRule.waitUntil(10_000) { runBlocking {
                repository.collectionEntries(created.collectionId).map { it.book.identity }.toSet() == setOf(identity)
            } }
            assertEquals(setOf(identity), runBlocking {
                repository.collectionEntries(created.collectionId).map { it.book.identity }.toSet()
            })
            assertEquals(created.collectionId, runBlocking { repository.collections().single { it.title == title } }.collectionId)
        } finally {
            runBlocking {
                repository.collections().firstOrNull { it.title == title }?.let { repository.deleteCollection(it.collectionId) }
                books.forEach {
                    repository.setLocalTags(it, emptySet())
                    repository.removeFromLibrary(it)
                }
            }
        }
    }

    @Test
    fun smart_rule_invalid_draft_survives_recreation_and_stay_then_creates_once() {
        val application = composeRule.activity.application as TsuyomiApplication
        val title = "智能草稿-${UUID.randomUUID()}"
        val repository = application.libraryRepository
        fun editCompletionCondition() {
            val field = hasSetTextAction() and hasText("完结")
            val disclosure = hasText("输入其他标签") and !hasSetTextAction()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(field).fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodes(disclosure).fetchSemanticsNodes().isNotEmpty()
            }
            if (composeRule.onAllNodes(field).fetchSemanticsNodes().isEmpty()) {
                val disclosures = composeRule.onAllNodes(disclosure)
                disclosures[disclosures.fetchSemanticsNodes().lastIndex].performScrollTo().performClick()
            }
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(field).fetchSemanticsNodes().size == 1
            }
            composeRule.onNode(field).performScrollTo().performTextReplacement("科幻")
        }
        try {
            runBlocking { application.displayController.setDisplayPreference(DisplayPreference.STANDARD) }
            waitForText("书架")
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithText("新建收藏夹").performClick()
            composeRule.onNodeWithText("高级选项", substring = true).performClick()
            composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement(title)
            if (composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1) {
                composeRule.onNodeWithText("输入其他标签").performScrollTo().performClick()
            }
            composeRule.onAllNodes(hasSetTextAction())[1].performTextReplacement("奇幻")
            closeSoftKeyboard()
            composeRule.onNodeWithText("添加筛选条件").performScrollTo().performClick()
            composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("按标签筛选").fetchSemanticsNodes().size == 2 }
            saveVisibleRule()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("请修正此条件", useUnmergedTree = true).assertExists()
            assertTrue(runBlocking { repository.collections().none { it.title == title } })

            composeRule.activityRule.scenario.recreate()
            waitForText(title)
            assertTrue(composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty())
            composeRule.onNodeWithText("请修正此条件", useUnmergedTree = true).assertExists()
            composeRule.onNodeWithContentDescription("返回").performClick()
            waitForText("放弃修改？")
            composeRule.onNodeWithText("继续编辑").performClick()
            if (composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error) and hasSetTextAction(),
                    useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                composeRule.onNode(hasText("输入其他标签") and !hasSetTextAction()).performScrollTo().performClick()
            }
            composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error) and hasSetTextAction(),
                useUnmergedTree = true).performScrollTo().performTextReplacement("完结")
            saveVisibleRule()
            composeRule.waitUntil(10_000) {
                runBlocking { repository.collections().count { it.title == title } == 1 }
            }
            val created = runBlocking { repository.collections().single { it.title == title } }
            assertEquals(CollectionKind.SMART, created.kind)
            assertEquals(2, (runBlocking { repository.smartRule(created.collectionId) }?.root as
                org.tsuyomi.shared.smartshelf.SmartRuleNode.All).children.size)
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithContentDescription("搜索").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("搜索").performClick()
            waitForText("搜索书架")
            composeRule.onNode(hasSetTextAction()).performTextReplacement(title)
            waitForText("智能收藏夹")
            composeRule.onNode(hasText(title) and hasClickAction() and !hasSetTextAction()).performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithContentDescription("更多操作").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithText("编辑规则").performClick()
            waitForText("编辑智能收藏夹")
            editCompletionCondition()
            closeSoftKeyboard()
            composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            waitForText("放弃修改？")
            composeRule.onNodeWithText("放弃修改").performClick()
            assertEquals("奇幻", ((runBlocking { repository.smartRule(created.collectionId) }?.root as
                org.tsuyomi.shared.smartshelf.SmartRuleNode.All).children[0] as
                org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate).let {
                (it.value as org.tsuyomi.shared.smartshelf.SmartPredicate.TagContains).tags.single()
            })
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithContentDescription("更多操作").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithText("编辑规则").performClick()
            waitForText("编辑智能收藏夹")
            editCompletionCondition()
            saveVisibleRule()
            composeRule.waitUntil(10_000) {
                runBlocking {
                    val rule = repository.smartRule(created.collectionId)?.root as?
                        org.tsuyomi.shared.smartshelf.SmartRuleNode.All
                    val edited = rule?.children?.getOrNull(1) as?
                        org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate
                    (edited?.value as? org.tsuyomi.shared.smartshelf.SmartPredicate.TagContains)
                        ?.tags == setOf("科幻")
                }
            }
            assertEquals("奇幻", ((runBlocking { repository.smartRule(created.collectionId) }?.root as
                org.tsuyomi.shared.smartshelf.SmartRuleNode.All).children[0] as
                org.tsuyomi.shared.smartshelf.SmartRuleNode.Predicate).let {
                (it.value as org.tsuyomi.shared.smartshelf.SmartPredicate.TagContains).tags.single()
            })
            assertEquals(1, runBlocking { repository.collections().count { it.collectionId == created.collectionId } })
        } finally {
            runBlocking {
                repository.collections().filter { it.title == title }.forEach { repository.deleteCollection(it.collectionId) }
            }
        }
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
        waitForText("手动收藏夹")
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
        composeRule.onAllNodesWithText("书籍排序：智能 · 正序").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("筛选与排序；筛选：全部；排序：", substring = true).performClick()
        composeRule.onNodeWithText("排序方式：最近阅读").performClick()
        composeRule.onNodeWithContentDescription("筛选与排序；筛选：全部；排序：最近阅读", substring = true).performClick()
        composeRule.onNodeWithText("排序方向：倒序").performClick()

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

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
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
    fun selected_folders_share_one_preview_cancel_and_atomic_subtree_confirmation() {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val prefix = "delete-${UUID.randomUUID()}"
        val parentA = "$prefix-a"
        val parentB = "$prefix-b"
        val childA = "$prefix-a-child"
        val childB = "$prefix-b-child"
        val identity = BookIdentity("fixture.collection", "$prefix-book")
        try {
            runBlocking {
                application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
                repository.addToLibrary(book(identity, "删除后保留的书"))
                repository.createCollection(LibraryCollection(parentA, CollectionKind.MANUAL, "删除父甲-$prefix", null, 0))
                repository.createCollection(LibraryCollection(parentB, CollectionKind.MANUAL, "删除父乙-$prefix", null, 1))
                repository.createCollection(LibraryCollection(childA, CollectionKind.MANUAL, "删除子甲", parentA, 0))
                repository.createCollection(LibraryCollection(childB, CollectionKind.MANUAL, "删除子乙", parentB, 0))
                assertTrue(repository.addManualMembership(childA, identity))
            }
            waitForText("书架")
            composeRule.activityRule.scenario.recreate()
            waitForText("删除父甲-$prefix")
            composeRule.onNodeWithTag("library-root-node-collection:$parentA")
                .performTouchInput { longClick() }
            waitForText("已选 1 项")
            composeRule.onNodeWithTag("library-root-node-collection:$parentB").performClick()
            waitForText("已选 2 项")
            composeRule.onNodeWithContentDescription("移除所选").performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("2 个收藏夹", substring = true).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithText("0 条书籍所属关系", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("取消").performClick()
            waitForText("已选 2 项")
            assertEquals(4, runBlocking { repository.collections().count { it.collectionId.startsWith(prefix) } })
            assertEquals(listOf(identity), runBlocking { repository.collectionEntries(childA).map { it.book.identity } })
            composeRule.onNodeWithContentDescription("移除所选").performClick()
            composeRule.onNodeWithText("删除所选及全部子收藏夹").performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("4 个收藏夹", substring = true).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithText("1 条书籍所属关系", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("连同子收藏夹删除").performClick()
            composeRule.waitUntil(10_000) {
                runBlocking { repository.collections().none { it.collectionId.startsWith(prefix) } }
            }
            assertEquals(identity, runBlocking { repository.libraryEntry(identity)?.book?.identity })
        } finally {
            runBlocking {
                listOf(childA, childB, parentA, parentB).forEach { repository.deleteCollection(it) }
                repository.removeFromLibrary(identity)
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

        controller.restoreLibraryHome()
        controller.longPressBook(behaviorNewer)
        assertTrue(controller.removeSelection("failed"))
        assertEquals(false, repository.libraryEntry(behaviorNewer)?.localMembership)
        assertEquals(true, repository.libraryEntry(behaviorOlder)?.localMembership)
    }
    @Test
    fun library_controller_projects_actual_unpinned_reader_history_and_keeps_root_membership() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val suffix = UUID.randomUUID().toString()
        val reopened = BookIdentity("fixture.reader.history", "opened-$suffix")
        val completed = BookIdentity("fixture.reader.history", "completed-$suffix")
        val metadataOnly = BookIdentity("fixture.reader.history", "metadata-$suffix")
        val initialVisit = Instant.parse("2200-09-14T01:00:00Z")
        val completedVisit = initialVisit.plusSeconds(10)
        val returnVisit = initialVisit.plusSeconds(20)

        repository.saveBook(book(reopened, "未收藏的继续阅读"))
        repository.saveBook(book(completed, "已完成的继续阅读"))
        repository.saveBook(book(metadataOnly, "只打开详情"))
        repository.recordReaderVisit(reopened, initialVisit)
        repository.saveProgress(progress(completed, completedVisit.minusSeconds(1), 1.0))
        repository.recordReaderVisit(completed, completedVisit)

        val controller = LibraryFlowController(repository, libraryPreferences, "FIXTURE_READER_HISTORY_$suffix")
        controller.reload("failed")
        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE)

        assertEquals(listOf(completed, reopened), controller.state.entries.take(2).map { it.book.identity })
        assertTrue(controller.state.entries.take(2).none { it.localMembership })
        assertEquals(null, controller.state.entries.first { it.book.identity == reopened }.progress)
        assertTrue(controller.state.entries.none { it.book.identity == metadataOnly })
        assertEquals(null, repository.libraryEntry(reopened))
        assertEquals(null, repository.libraryEntry(completed))

        repository.recordReaderVisit(reopened, returnVisit)
        controller.reload("failed")
        assertEquals(reopened, controller.state.entries.first().book.identity)
        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.ALL)
        assertTrue(controller.state.entries.none { it.book.identity in setOf(reopened, completed) })
    }

    @Test
    fun library_controller_restores_actual_caller_tab_without_collection_flash() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        repository.deleteCollection(behaviorCollectionId)
        listOf(behaviorNewer, behaviorOlder).forEach { repository.removeFromLibrary(it) }
        repository.addToLibrary(book(behaviorNewer, "根书架 A"))
        repository.addToLibrary(book(behaviorOlder, "根书架 B"))
        repository.saveProgress(progress(behaviorOlder, Instant.EPOCH, 0.4))
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
        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE)
        val callerIdentities = controller.state.entries.map { it.book.identity }.toSet()

        controller.selectCollection(behaviorCollectionId)
        assertTrue(controller.state.loading)
        assertTrue(controller.state.entries.isEmpty())
        assertEquals(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE, controller.rootScreenState().filter)
        assertFalse(controller.rootScreenState().loading)
        assertEquals(callerIdentities, controller.rootScreenState().entries.map { it.book.identity }.toSet())
        controller.reload("failed")
        assertEquals(listOf(behaviorOlder), controller.state.entries.map { it.book.identity })
        assertEquals(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE, controller.rootScreenState().filter)
        assertEquals(callerIdentities, controller.rootScreenState().entries.map { it.book.identity }.toSet())

        controller.restoreLibraryHome()
        assertFalse(controller.state.loading)
        assertEquals(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE, controller.state.filter)
        assertEquals(callerIdentities, controller.state.entries.map { it.book.identity }.toSet())
        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.ALL)
        val rootIdentities = controller.state.entries.map { it.book.identity }.toSet()
        controller.selectCollection(behaviorCollectionId)
        assertTrue(controller.rootScreenState().isRootProjection)
        assertEquals(rootIdentities, controller.rootScreenState().entries.map { it.book.identity }.toSet())

        controller.restoreLibraryHome()
        repository.setReadLater(behaviorNewer, true)
        controller.reload("failed")
        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.READ_LATER)
        val readLaterIdentities = controller.state.entries.map { it.book.identity }.toSet()
        assertEquals(setOf(behaviorNewer), readLaterIdentities)
        controller.selectCollection(behaviorCollectionId)
        assertFalse(controller.state.isRootProjection)
        assertEquals(listOf(behaviorOlder), controller.state.entries.map { it.book.identity })
        assertEquals(org.tsuyomi.feature.library.SystemLibraryFilter.READ_LATER, controller.rootScreenState().filter)
        assertEquals(readLaterIdentities, controller.rootScreenState().entries.map { it.book.identity }.toSet())
        controller.reload("failed")
        assertEquals(readLaterIdentities, controller.rootScreenState().entries.map { it.book.identity }.toSet())
        controller.restoreLibraryHome()
        assertEquals(readLaterIdentities, controller.state.entries.map { it.book.identity }.toSet())
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
                        org.tsuyomi.shared.librarydomain.RemoteMirrorTargetSnapshot(
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
    fun library_controller_persists_independent_state_for_each_fixed_tab() = withControllerPreferences { libraryPreferences ->
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
        controller.primaryTabStates().let { pages ->
            assertEquals(org.tsuyomi.feature.library.LibraryLayout.GRID, pages.getValue(org.tsuyomi.feature.library.SystemLibraryFilter.ALL).layout)
            assertEquals(org.tsuyomi.feature.library.LibraryLayout.LIST, pages.getValue(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE).layout)
            assertEquals(org.tsuyomi.feature.library.LibraryLayout.COMPACT, pages.getValue(org.tsuyomi.feature.library.SystemLibraryFilter.READ_LATER).layout)
        }

        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE)
        assertEquals(org.tsuyomi.feature.library.LibraryLayout.LIST, controller.state.layout)
        assertEquals(2, controller.state.firstVisibleIndex)
        controller.persistViewport(6, 20)
        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.READ_LATER)
        assertEquals(org.tsuyomi.feature.library.LibraryLayout.COMPACT, controller.state.layout)
        controller.primaryTabStates().let { pages ->
            assertEquals(4, pages.getValue(org.tsuyomi.feature.library.SystemLibraryFilter.ALL).firstVisibleIndex)
            assertEquals(6, pages.getValue(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE).firstVisibleIndex)
            assertEquals(1, pages.getValue(org.tsuyomi.feature.library.SystemLibraryFilter.READ_LATER).firstVisibleIndex)
        }
        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE)
        assertEquals(6, controller.state.firstVisibleIndex)
        assertEquals(20, controller.state.firstVisibleOffset)
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
    fun library_controller_keeps_root_filter_out_of_system_tabs_and_clearing_it_keeps_sort() = withControllerPreferences { libraryPreferences ->
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        repository.removeFromLibrary(behaviorNewer)
        repository.addToLibrary(book(behaviorNewer, "筛选隔离"))
        repository.setReadLater(behaviorNewer, true)
        val controller = LibraryFlowController(repository, libraryPreferences, "FIXTURE_FILTER_ISOLATION")
        controller.reload("failed")
        controller.selectSort(org.tsuyomi.feature.library.LibrarySortMode.TITLE)
        controller.selectSortDirection(true)
        controller.setUpdateFilter(org.tsuyomi.feature.library.LibraryUpdateFilter.UPDATES_ONLY)
        assertTrue(controller.state.projectedEntries().isEmpty())
        controller.primaryTabStates().let { pages ->
            assertTrue(pages.getValue(org.tsuyomi.feature.library.SystemLibraryFilter.ALL).projectedEntries().isEmpty())
            assertEquals(
                listOf(behaviorNewer),
                pages.getValue(org.tsuyomi.feature.library.SystemLibraryFilter.READ_LATER)
                    .projectedEntries()
                    .map { it.book.identity },
            )
        }

        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.READ_LATER)
        assertEquals(listOf(behaviorNewer), controller.state.projectedEntries().map { it.book.identity })
        assertEquals(org.tsuyomi.feature.library.LibraryUpdateFilter.UPDATES_ONLY, controller.state.updateFilter)

        controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.ALL)
        assertEquals(org.tsuyomi.feature.library.LibrarySortMode.TITLE, controller.state.sortMode)
        assertTrue(controller.state.sortDescending)
        controller.setUpdateFilter(org.tsuyomi.feature.library.LibraryUpdateFilter.ALL)
        assertEquals(org.tsuyomi.feature.library.LibrarySortMode.TITLE, controller.state.sortMode)
        assertTrue(controller.state.sortDescending)
    }

    @Test
    fun tag_layout_and_source_ownership_survive_recreation_without_changing_associations() {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val fixtures = listOf(behaviorNewer, behaviorOlder)
        val sourceRow = "library-tag-SOURCE-${behaviorNewer.sourceId}-重建来源标签"
        try {
            runBlocking {
                fixtures.forEachIndexed { index, identity ->
                    repository.addToLibrary(book(identity, "标签重建书籍$index").copy(remoteTags = setOf("重建来源标签")))
                    repository.setLocalTags(identity, setOf("重建本地标签"))
                }
            }
            composeRule.activityRule.scenario.recreate()
            waitForText("标签重建书籍0")
            composeRule.onNodeWithContentDescription("更多操作").performClick()
            composeRule.onNodeWithText("标签").performClick()
            waitForText("重建本地标签")
            composeRule.onNodeWithContentDescription("切换布局；当前为", substring = true).performClick()
            composeRule.onNodeWithTag("tsuyomi-tab-SOURCE").performClick()
            composeRule.onNode(
                hasTestTag(sourceRow) and hasAnyDescendant(hasText("2 本")),
                useUnmergedTree = true,
            ).assertIsDisplayed()
            composeRule.activityRule.scenario.recreate()
            waitForText("重建来源标签")
            composeRule.onNodeWithTag("tsuyomi-tab-SOURCE").assertIsSelected()
            composeRule.onNode(
                hasTestTag(sourceRow) and hasAnyDescendant(hasText("2 本")),
                useUnmergedTree = true,
            ).assertIsDisplayed().performClick()
            waitForText("标签重建书籍0")
            composeRule.onNodeWithText("标签重建书籍1").assertIsDisplayed()
            runBlocking {
                fixtures.forEach { identity ->
                    assertEquals(setOf("重建本地标签"), requireNotNull(repository.libraryEntry(identity)).localTags)
                    assertEquals(setOf("重建来源标签"), requireNotNull(repository.book(identity)).remoteTags)
                }
            }
        } finally {
            runBlocking { fixtures.forEach { repository.setLocalTags(it, emptySet()) } }
        }
    }

    @Test
    fun tags_use_the_complete_local_snapshot_without_mutating_the_calling_root() = withControllerPreferences { libraryPreferences ->
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val controller = LibraryFlowController(repository, libraryPreferences, "FIXTURE_TAGS")
        try {
            listOf(behaviorNewer, behaviorOlder).forEach {
                repository.removeFromLibrary(it)
            }
            repository.addToLibrary(book(behaviorNewer, "标签继续阅读").copy(remoteTags = setOf("来源标签")))
            repository.addToLibrary(book(behaviorOlder, "标签完整快照").copy(remoteTags = setOf("来源标签")))
            repository.setLocalTags(behaviorNewer, setOf("共同标签"))
            repository.setLocalTags(behaviorOlder, setOf("共同标签"))
            repository.setReadLater(behaviorOlder, true)
            repository.removeFromLibrary(behaviorOlder)
            repository.saveProgress(progress(behaviorNewer, Instant.EPOCH, 0.4))
            controller.reload("failed")
            controller.selectTab(org.tsuyomi.feature.library.SystemLibraryFilter.CONTINUE)
            controller.setUpdateFilter(org.tsuyomi.feature.library.LibraryUpdateFilter.UPDATES_ONLY)
            val rootBeforeTag = controller.state

            val tagState = controller.tagProjection(
                org.tsuyomi.feature.library.LibraryTagOwnership.LOCAL,
                sourceId = null,
                normalizedTag = "共同标签",
            )

            assertEquals(
                setOf(behaviorNewer, behaviorOlder),
                tagState.entries.mapTo(linkedSetOf()) { it.book.identity },
            )
            val sourceTagState = controller.tagProjection(
                org.tsuyomi.feature.library.LibraryTagOwnership.SOURCE,
                sourceId = behaviorNewer.sourceId,
                normalizedTag = "来源标签",
            )
            assertEquals(
                setOf(behaviorNewer, behaviorOlder),
                sourceTagState.entries.mapTo(linkedSetOf()) { it.book.identity },
            )
            assertEquals(org.tsuyomi.feature.library.SystemLibraryFilter.ALL, tagState.filter)
            assertEquals(org.tsuyomi.feature.library.LibraryUpdateFilter.ALL, tagState.updateFilter)
            assertFalse(tagState.isRootProjection)
            assertEquals(rootBeforeTag, controller.state)
        } finally {
            repository.setReadLater(behaviorOlder, false)
            listOf(behaviorNewer, behaviorOlder).forEach {
                repository.setLocalTags(it, emptySet())
                repository.removeFromLibrary(it)
            }
            libraryPreferences.updateShowUpdatesOnly(false)
        }
    }

    @Test
    fun root_book_drop_creates_one_collection_with_complete_deduplicated_batch() = withControllerPreferences { libraryPreferences ->
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
                    override fun cached(request: CoverRequest) = CoverUiState.Ready(bitmap)
                    override fun observe(request: CoverRequest) = flowOf(CoverUiState.Ready(bitmap))
                },
                sourceId = "fixture.cover",
                packageRevision = "package-revision",
                credentialRevision = "credential-revision",
                scope = this,
            )
            val entries = (0 until 33).map { index ->
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
            assertTrue(controller.coverStates.size <= 32)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun library_controller_keeps_ready_cover_while_visibility_restarts() = runBlocking {
        val repository = (composeRule.activity.application as TsuyomiApplication).libraryRepository
        val controller = LibraryFlowController(repository, libraryPreferences)
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val secondObservationStarted = CompletableDeferred<Unit>()
        var observations = 0
        val coverRepository = object : CoverRepository {
            override fun cached(request: CoverRequest): CoverUiState.Ready? = null

            override fun observe(request: CoverRequest): Flow<CoverUiState> = flow {
                observations += 1
                if (observations == 1) {
                    emit(CoverUiState.Ready(bitmap))
                } else {
                    emit(CoverUiState.Loading(request.fallback))
                    secondObservationStarted.complete(Unit)
                    awaitCancellation()
                }
            }
        }
        val entry = LibraryEntry(
            book = book(BookIdentity("fixture.cover", "foreground"), "前后台封面").copy(
                coverUrl = "https://example.com/foreground.png",
                canonicalUrl = "https://example.com/book/foreground",
            ),
            libraryAddedAt = Instant.EPOCH,
            rating = null,
            localTags = emptySet(),
            sourceAvailable = true,
            reconciliation = null,
        )
        try {
            controller.configureCoverRepository(
                repository = coverRepository,
                sourceId = "fixture.cover",
                packageRevision = "package",
                credentialRevision = "credential",
                scope = this,
            )
            controller.setCoverVisible(entry, true)
            withTimeout(5_000) {
                while (controller.coverState(entry) !is CoverUiState.Ready) yield()
            }
            controller.setCoverVisible(entry, false)
            controller.setCoverVisible(entry, true)
            withTimeout(5_000) { secondObservationStarted.await() }

            val retained = controller.coverState(entry)
            assertTrue(retained is CoverUiState.StaleReady)
            assertSame(bitmap, (retained as CoverUiState.StaleReady).bitmap)
        } finally {
            controller.setCoverVisible(entry, false)
            bitmap.recycle()
        }
    }

    private fun withControllerPreferences(action: suspend (LibraryPreferencesRepository) -> Unit) = runBlocking {
        val job = SupervisorJob()
        val file = File(composeRule.activity.cacheDir, "controller-${UUID.randomUUID()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file },
        )
        try {
            action(LibraryPreferencesRepository(store))
        } finally {
            job.cancel()
            job.join()
            file.delete()
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
    private fun saveVisibleRule() {
        closeSoftKeyboard()
        val action = if (composeRule.onAllNodesWithText("创建智能收藏夹").fetchSemanticsNodes().isNotEmpty())
            "创建智能收藏夹" else "保存智能收藏夹"
        composeRule.onNodeWithText(action).performScrollTo().performClick()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
