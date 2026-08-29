/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.core.database.room.ReadingProgressEntity
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.smartshelf.MatchMode
import org.tsuyomi.shared.smartshelf.ProgressState
import org.tsuyomi.shared.smartshelf.SmartPredicate
import org.tsuyomi.shared.smartshelf.SmartRule
import org.tsuyomi.shared.smartshelf.SmartRuleNode

@RunWith(AndroidJUnit4::class)
class RoomCollectionInstrumentedTest {
    private val fixture = RoomLibraryRepositoryInstrumentedTestFixture()
    private val database get() = fixture.database
    private val repository get() = fixture.repository

    @After
    fun closeDatabase() = fixture.close()
    @Test
    fun concurrentOppositeParentAssignmentsLeaveExactlyOneAcyclicDirection() = runBlocking {
        repository.createCollection(LibraryCollection("alpha", CollectionKind.MANUAL, "Alpha", null, 0))
        repository.createCollection(LibraryCollection("beta", CollectionKind.MANUAL, "Beta", null, 0))

        val results = concurrently(
            {
                runCatching {
                    repository.updateCollectionPresentation("alpha", "beta", 1)
                }
            },
            {
                runCatching {
                    repository.updateCollectionPresentation("beta", "alpha", 1)
                }
            },
        )

        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.any { it.exceptionOrNull() is IllegalArgumentException })
        val alphaParent = database.libraryDao().collection("alpha")?.parentCollectionId
        val betaParent = database.libraryDao().collection("beta")?.parentCollectionId
        assertTrue((alphaParent == "beta") xor (betaParent == "alpha"))
    }
    @Test
    fun smartCollectionEvaluatesTypedAstAgainstLiveRoomState() = runBlocking {
        val matching = BookIdentity("org.tsuyomi.wenku8", "smart-1")
        val excluded = BookIdentity("org.tsuyomi.other", "smart-2")
        repository.addToLibrary(LibraryBook(matching, "100% 奇幻", Instant.EPOCH, Instant.EPOCH))
        repository.addToLibrary(LibraryBook(excluded, "普通", Instant.EPOCH, Instant.EPOCH))
        repository.setLocalTags(matching, listOf("奇幻"))
        repository.saveProgress(
            ReadingProgress(
                matching,
                ReaderLocator(
                    document = DocumentIdentity(matching.sourceId, matching.remoteBookId, "chapter"),
                    bookProgress = 0.5,
                    capturedAt = Instant.EPOCH.plusSeconds(1),
                ),
            ),
        )
        val rule = SmartRule(
            root = SmartRuleNode.All(
                listOf(
                    SmartRuleNode.Predicate(SmartPredicate.SourceIn(setOf(matching.sourceId))),
                    SmartRuleNode.Predicate(SmartPredicate.TagContains(org.tsuyomi.shared.smartshelf.MatchMode.ALL, setOf("奇幻"))),
                    SmartRuleNode.Predicate(SmartPredicate.TitleContains(setOf("100%"))),
                    SmartRuleNode.Predicate(SmartPredicate.ProgressIn(setOf(ProgressState.READING))),
                ),
            ),
        )
        repository.createSmartCollection(
            LibraryCollection("smart", CollectionKind.SMART, "智能", null, 0, Instant.EPOCH, Instant.EPOCH),
            rule,
        )

        assertEquals(listOf(matching), repository.collectionEntries("smart", Instant.EPOCH.plusSeconds(10)).map { it.book.identity })
        repository.setLocalTags(matching, emptyList())
        assertTrue(repository.collectionEntries("smart", Instant.EPOCH.plusSeconds(10)).isEmpty())
    }

    @Test
    fun smartRulesIgnoreInvalidProgressAndRejectRulesOverTheSqlArgumentLimit() = runBlocking {
        val identity = BookIdentity("fixture.source", "invalid-smart-progress")
        repository.addToLibrary(LibraryBook(identity, "Invalid progress", Instant.EPOCH, Instant.EPOCH))
        database.libraryDao().insertProgressIfAbsent(
            ReadingProgressEntity(
                identity.sourceId,
                identity.remoteBookId,
                "chapter",
                null,
                "block",
                null,
                -1,
                null,
                1.0,
                Instant.EPOCH.epochSecond,
                Instant.EPOCH.nano,
            ),
        )
        fun progressRule(state: ProgressState) = SmartRule(
            root = SmartRuleNode.Predicate(SmartPredicate.ProgressIn(setOf(state))),
        )
        listOf(ProgressState.UNSTARTED, ProgressState.READING, ProgressState.FINISHED).forEach { state ->
            repository.createSmartCollection(
                LibraryCollection("progress-$state", CollectionKind.SMART, state.name, null, 0),
                progressRule(state),
            )
        }

        assertEquals(listOf(identity), repository.collectionEntries("progress-UNSTARTED").map { it.book.identity })
        assertTrue(repository.collectionEntries("progress-READING").isEmpty())
        assertTrue(repository.collectionEntries("progress-FINISHED").isEmpty())

        val tags = (1..64).mapTo(linkedSetOf()) { "tag-$it" }
        val overBudgetRule = SmartRule(
            root = SmartRuleNode.All(
                List(15) { SmartRuleNode.Predicate(SmartPredicate.TagContains(MatchMode.ALL, tags)) },
            ),
        )
        assertTrue(
            runCatching {
                repository.createSmartCollection(
                    LibraryCollection("over-budget", CollectionKind.SMART, "Over budget", null, 0),
                    overBudgetRule,
                )
            }.isFailure,
        )
        assertEquals(null, database.libraryDao().collection("over-budget"))
    }

    @Test
    fun deleteCollectionReparentsChildrenAndCompactsBothAffectedSiblingGroups() = runBlocking {
        repository.createCollection(LibraryCollection("grandparent", CollectionKind.MANUAL, "Grandparent", null, 0))
        repository.createCollection(LibraryCollection("parent", CollectionKind.MANUAL, "Parent", "grandparent", 10))
        repository.createCollection(LibraryCollection("former-sibling", CollectionKind.MANUAL, "Former sibling", "grandparent", 30))
        repository.createCollection(LibraryCollection("root", CollectionKind.MANUAL, "Root", null, 5))
        repository.createCollection(LibraryCollection("child-b", CollectionKind.MANUAL, "Child B", "parent", 7))
        repository.createCollection(LibraryCollection("child-a", CollectionKind.MANUAL, "Child A", "parent", 7))

        assertTrue(repository.deleteCollection("parent"))

        val collections = repository.collections().associateBy { it.collectionId }
        assertEquals(null, collections["parent"])
        assertEquals(null, requireNotNull(collections["child-a"]).parentCollectionId)
        assertEquals(null, requireNotNull(collections["child-b"]).parentCollectionId)
        assertEquals(0L, requireNotNull(collections["former-sibling"]).displayOrder)
        assertEquals(1L, requireNotNull(collections["root"]).displayOrder)
        assertEquals(2L, requireNotNull(collections["child-a"]).displayOrder)
        assertEquals(3L, requireNotNull(collections["child-b"]).displayOrder)
    }
}
