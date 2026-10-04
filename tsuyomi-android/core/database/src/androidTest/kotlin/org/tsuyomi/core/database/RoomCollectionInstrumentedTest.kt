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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.core.database.room.ReadingProgressEntity
import org.tsuyomi.shared.librarydomain.CollectionKind
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LibraryCollection
import org.tsuyomi.shared.librarydomain.ReadingProgress
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
        assertTrue(repository.removeFromLibrary(matching))
        assertTrue(repository.collectionEntries("smart", Instant.EPOCH.plusSeconds(10)).isEmpty())
        assertTrue(repository.addToLibrary(LibraryBook(matching, "100% 奇幻", Instant.EPOCH, Instant.EPOCH)))
        repository.setLocalTags(matching, emptyList())
        assertTrue(repository.collectionEntries("smart", Instant.EPOCH.plusSeconds(10)).isEmpty())
    }

    @Test
    fun smartRulesMatchJsonEncodedAuthorsAndRemoteTagsLiterally() = runBlocking {
        val matching = BookIdentity("fixture.source", "encoded-match")
        val nearAuthor = BookIdentity("fixture.source", "encoded-near-author")
        val nearTag = BookIdentity("fixture.source", "encoded-near-tag")
        val author = "Ada \"Quoted\" \\ Backslash"
        val tag = "100%_literal"
        repository.addToLibrary(
            LibraryBook(
                matching,
                "Matching",
                Instant.EPOCH,
                Instant.EPOCH,
                authors = setOf(author),
                remoteTags = setOf(tag),
            ),
        )
        repository.addToLibrary(
            LibraryBook(
                nearAuthor,
                "Near author",
                Instant.EPOCH,
                Instant.EPOCH,
                authors = setOf("Ada \"Quoted\" / Backslash"),
            ),
        )
        repository.addToLibrary(
            LibraryBook(
                nearTag,
                "Near tag",
                Instant.EPOCH,
                Instant.EPOCH,
                remoteTags = setOf("100Axliteral"),
            ),
        )
        repository.createSmartCollection(
            LibraryCollection("encoded-author", CollectionKind.SMART, "Encoded author", null, 0),
            SmartRule(root = SmartRuleNode.Predicate(SmartPredicate.AuthorContains(setOf(author)))),
        )
        repository.createSmartCollection(
            LibraryCollection("encoded-tag", CollectionKind.SMART, "Encoded tag", null, 0),
            SmartRule(root = SmartRuleNode.Predicate(SmartPredicate.TagContains(MatchMode.ALL, setOf(tag)))),
        )

        assertEquals(listOf(matching), repository.collectionEntries("encoded-author").map { it.book.identity })
        assertEquals(listOf(matching), repository.collectionEntries("encoded-tag").map { it.book.identity })
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
    fun deletionPreviewAndReparentPreserveNearestParentOrderAndBookRecord() = runBlocking {
        val book = LibraryBook(BookIdentity("source", "member"), "Book", Instant.EPOCH, Instant.EPOCH)
        repository.addToLibrary(book)
        repository.createCollection(LibraryCollection("grandparent", CollectionKind.MANUAL, "Grandparent", null, 0))
        repository.createCollection(LibraryCollection("parent", CollectionKind.MANUAL, "Parent", "grandparent", 10))
        repository.createCollection(LibraryCollection("former-sibling", CollectionKind.MANUAL, "Former sibling", "grandparent", 30))
        repository.createCollection(LibraryCollection("root", CollectionKind.MANUAL, "Root", null, 5))
        repository.createCollection(LibraryCollection("child-b", CollectionKind.MANUAL, "Child B", "parent", 7))
        repository.createManualCollectionWithMemberships(
            LibraryCollection("child-a", CollectionKind.MANUAL, "Child A", "parent", 7), setOf(book.identity),
        )
        val plan = repository.previewCollectionDeletion(setOf("parent"), CollectionDeletionPolicy.REPARENT_CHILDREN)
        assertEquals(1, plan.folderCount)
        assertEquals(0, plan.membershipCount)
        assertEquals("parent", repository.collections().first { it.collectionId == "child-a" }.parentCollectionId)
        assertTrue(repository.deleteCollections(plan))

        val rows = repository.collections().associateBy { it.collectionId }
        assertFalse("parent" in rows)
        assertEquals("grandparent", rows.getValue("child-a").parentCollectionId)
        assertEquals("grandparent", rows.getValue("child-b").parentCollectionId)
        assertEquals(0L, rows.getValue("child-a").displayOrder)
        assertEquals(1L, rows.getValue("child-b").displayOrder)
        assertEquals(2L, rows.getValue("former-sibling").displayOrder)
        assertEquals(5L, rows.getValue("root").displayOrder)
        assertEquals(listOf(book.identity), repository.collectionEntries("child-a").map { it.book.identity })
        assertEquals("Book", repository.book(book.identity)?.title)
    }

    @Test
    fun overlappingBatchSubtreeDeletionIsAtomicAndLeavesBooksIntact() = runBlocking {
        val identity = BookIdentity("source", "member")
        repository.addToLibrary(LibraryBook(identity, "Book", Instant.EPOCH, Instant.EPOCH))
        repository.createCollection(LibraryCollection("parent", CollectionKind.MANUAL, "Parent", null, 0))
        repository.createManualCollectionWithMemberships(
            LibraryCollection("child", CollectionKind.MANUAL, "Child", "parent", 0), setOf(identity),
        )
        repository.createCollection(LibraryCollection("sibling", CollectionKind.MANUAL, "Sibling", null, 1))
        val plan = repository.previewCollectionDeletion(setOf("parent", "child"), CollectionDeletionPolicy.DELETE_SUBTREES)
        assertEquals(2, plan.folderCount)
        assertEquals(1, plan.membershipCount)
        // Preview and cancel perform no mutation; a stale preview cannot silently delete changed memberships.
        assertEquals(3, repository.collections().size)
        assertEquals(listOf(identity), repository.collectionEntries("child").map { it.book.identity })
        val late = BookIdentity("source", "late")
        repository.addToLibrary(LibraryBook(late, "Late", Instant.EPOCH, Instant.EPOCH))
        assertTrue(repository.addManualMembership("child", late))
        assertFalse(repository.deleteCollections(plan))
        assertEquals(3, repository.collections().size)
        val currentPlan = repository.previewCollectionDeletion(setOf("parent", "child"), CollectionDeletionPolicy.DELETE_SUBTREES)
        assertEquals(2, currentPlan.membershipCount)
        assertTrue(repository.deleteCollections(currentPlan))
        assertEquals(listOf("sibling"), repository.collections().map { it.collectionId })
        assertEquals(0L, repository.collections().single().displayOrder)
        assertEquals(emptySet<String>(), repository.manualCollectionIds(identity))
        assertEquals("Book", repository.book(identity)?.title)
        assertEquals("Late", repository.book(late)?.title)
    }

    @Test
    fun overlappingBatchReparentPromotesOnlySurvivorsInOriginalOrder() = runBlocking {
        val identity = BookIdentity("source", "survivor")
        repository.addToLibrary(LibraryBook(identity, "Survivor", Instant.EPOCH, Instant.EPOCH))
        repository.createCollection(LibraryCollection("parent", CollectionKind.MANUAL, "Parent", null, 0))
        repository.createCollection(LibraryCollection("child", CollectionKind.MANUAL, "Child", "parent", 0))
        repository.createManualCollectionWithMemberships(
            LibraryCollection("grandchild", CollectionKind.MANUAL, "Grandchild", "child", 0), setOf(identity),
        )
        repository.createCollection(LibraryCollection("sibling", CollectionKind.MANUAL, "Sibling", null, 1))
        val plan = repository.previewCollectionDeletion(
            setOf("child", "parent"), CollectionDeletionPolicy.REPARENT_CHILDREN,
        )
        assertEquals(2, plan.folderCount)
        assertEquals(0, plan.membershipCount)
        assertTrue(repository.deleteCollections(plan))
        val survivors = repository.collections()
        assertEquals(listOf("grandchild", "sibling"), survivors.map { it.collectionId })
        assertEquals(null, survivors.first().parentCollectionId)
        assertEquals(0L, survivors.first().displayOrder)
        assertEquals(listOf(identity), repository.collectionEntries("grandchild").map { it.book.identity })
    }

    @Test
    fun smartRuleUpdatePreservesIdentityAndRejectsManualConversion() = runBlocking {
        val identity = BookIdentity("source", "match")
        repository.addToLibrary(LibraryBook(identity, "Book", Instant.EPOCH, Instant.EPOCH))
        val initial = SmartRule(root = SmartRuleNode.All(listOf(
            SmartRuleNode.Predicate(SmartPredicate.SourceIn(setOf("other"))),
        )))
        repository.createSmartCollection(LibraryCollection("smart", CollectionKind.SMART, "Old", null, 0), initial)
        val replacement = SmartRule(root = SmartRuleNode.All(listOf(
            SmartRuleNode.Predicate(SmartPredicate.SourceIn(setOf("source"))),
        )))
        repository.updateSmartCollection("smart", "New", replacement)
        assertEquals("New", repository.collections().single().title)
        assertEquals(replacement, repository.smartRule("smart"))
        assertEquals(listOf(identity), repository.collectionEntries("smart").map { it.book.identity })
        repository.createCollection(LibraryCollection("manual", CollectionKind.MANUAL, "Manual", null, 1))
        assertTrue(runCatching { repository.updateSmartCollection("manual", "Never", replacement) }.isFailure)
        assertEquals(CollectionKind.MANUAL, repository.collections().first { it.collectionId == "manual" }.kind)
    }
}
