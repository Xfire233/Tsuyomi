/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.database.room.ReadingProgressEntity
import org.tsuyomi.shared.backup.ImportKind
import org.tsuyomi.shared.backup.ImportSeverity
import org.tsuyomi.shared.backup.ImportPlan
import org.tsuyomi.shared.backup.TransferBook
import org.tsuyomi.shared.backup.TransferProgress
import org.tsuyomi.shared.backup.TransferShelf
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.smartshelf.MatchMode
import org.tsuyomi.shared.smartshelf.ProgressState
import org.tsuyomi.shared.smartshelf.SmartPredicate
import org.tsuyomi.shared.smartshelf.SmartRule
import org.tsuyomi.shared.smartshelf.SmartRuleNode

@RunWith(AndroidJUnit4::class)
class RoomLibraryRepositoryInstrumentedTest {
    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        TsuyomiDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val repository = RoomLibraryRepository(database)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun manualMembershipIsUniqueByCollectionAndStableBookIdentity() = runBlocking {
        val identity = BookIdentity("fixture.source", "book-42")
        repository.addToLibrary(LibraryBook(identity, "First title", addedAt = Instant.EPOCH, metadataUpdatedAt = Instant.EPOCH))
        repository.createCollection(LibraryCollection("favorites", CollectionKind.MANUAL, "收藏", null, 0))

        assertTrue(repository.addManualMembership("favorites", identity))
        assertFalse(repository.addManualMembership("favorites", identity))
    }

    @Test
    fun manualMembershipAppendUsesNextFreeOrderAfterDeletion() = runBlocking {
        val identities = (1..4).map { BookIdentity("fixture.source", "ordered-$it") }
        identities.forEach { identity ->
            repository.addToLibrary(LibraryBook(identity, identity.remoteBookId, addedAt = Instant.EPOCH, metadataUpdatedAt = Instant.EPOCH))
        }
        repository.createCollection(LibraryCollection("ordered", CollectionKind.MANUAL, "顺序", null, 0))
        identities.take(3).forEach { assertTrue(repository.addManualMembership("ordered", it)) }

        assertTrue(repository.removeManualMembership("ordered", identities[1]))
        assertTrue(repository.addManualMembership("ordered", identities[3]))

        assertEquals(listOf(0L, 2L, 3L), database.libraryDao().manualMemberships("ordered").map { it.displayOrder })
        assertEquals(listOf(identities[0], identities[2], identities[3]), repository.collectionEntries("ordered").map { it.book.identity })
    }

    @Test
    fun importTagMergePreservesExistingTagsAndFillsOnlyRemainingCapacity() = runBlocking {
        val identity = BookIdentity("fixture.source", "tag-merge")
        repository.addToLibrary(LibraryBook(identity, "标签", addedAt = Instant.EPOCH, metadataUpdatedAt = Instant.EPOCH))
        val existing = (0 until 63).mapTo(linkedSetOf()) { "existing-${it.toString().padStart(2, '0')}" }
        repository.setLocalTags(identity, existing)
        val transfer = RoomTransferRepository(database)
        val plan = ImportPlan(
            kind = ImportKind.TSUYOMI_TRANSFER,
            sourceCreatedAt = Instant.EPOCH,
            books = listOf(
                TransferBook(
                    identity = identity,
                    title = "标签",
                    localTags = setOf("new-b", "new-a"),
                    updatedAt = Instant.EPOCH.plusSeconds(1),
                ),
            ),
            shelves = emptyList(),
            readerPreferences = null,
        )
        transfer.prepare("tag-session", plan, "tag-digest", "tag-plan.json", "{}", Instant.EPOCH)

        transfer.applyRoomPlan("tag-session", "tag-digest", plan)

        val tags = repository.libraryEntries().single { it.book.identity == identity }.localTags
        assertEquals(64, tags.size)
        assertTrue(tags.containsAll(existing))
        assertTrue("new-a" in tags)
        assertFalse("new-b" in tags)
    }

    @Test
    fun importReviewSurfacesExistingRoomConflictsBeforeConfirmation() = runBlocking {
        val identity = BookIdentity("fixture.source", "review-conflicts")
        val existingAt = Instant.parse("2026-08-09T00:00:00Z")
        repository.addToLibrary(LibraryBook(identity, "本地标题", Instant.EPOCH, existingAt, authors = setOf("本地作者")))
        repository.setRating(identity, 5)
        repository.setLocalTags(identity, (0 until 64).map { "existing-$it" })
        repository.createCollection(LibraryCollection("shared-shelf", CollectionKind.MANUAL, "本地集合", null, 0))
        repository.saveProgress(progress(identity, "local-chapter", 20, 0.4, existingAt))
        val plan = ImportPlan(
            kind = ImportKind.TSUYOMI_TRANSFER,
            sourceCreatedAt = Instant.EPOCH,
            books = listOf(
                TransferBook(
                    identity = identity,
                    title = "导入标题",
                    authors = setOf("导入作者"),
                    localTags = setOf("new-tag"),
                    shelfIds = setOf("shared-shelf"),
                    rating = 2.0,
                    updatedAt = existingAt.minusSeconds(1),
                    progress = TransferProgress(
                        chapterId = "imported-chapter",
                        characterOffset = 10,
                        bookProgress = 0.2,
                        updatedAt = existingAt.minusSeconds(1),
                    ),
                ),
            ),
            shelves = listOf(TransferShelf("shared-shelf", "导入集合", position = 1)),
            readerPreferences = null,
        )

        val reviewed = RoomTransferRepository(database).withDatabaseConflicts(plan)

        assertEquals(
            setOf(
                "existing-shelf-retained",
                "existing-book-metadata-retained",
                "existing-rating-retained",
                "local-tags-capacity-conflict",
                "existing-progress-retained",
            ),
            reviewed.warnings.mapTo(linkedSetOf()) { it.safeCode },
        )
        assertTrue(reviewed.warnings.all { it.severity == ImportSeverity.CONFLICT })
    }

    @Test
    fun importReviewReportsRetainedRatingWithoutTreatingUnknownStatusAsMetadata() = runBlocking {
        val identity = BookIdentity("fixture.source", "review-null-rating")
        val existingAt = Instant.parse("2026-08-09T00:00:00Z")
        repository.addToLibrary(
            LibraryBook(
                identity = identity,
                title = "相同标题",
                addedAt = Instant.EPOCH,
                metadataUpdatedAt = existingAt,
                status = "ongoing",
            ),
        )
        val plan = ImportPlan(
            kind = ImportKind.TSUYOMI_TRANSFER,
            sourceCreatedAt = Instant.EPOCH,
            books = listOf(
                TransferBook(
                    identity = identity,
                    title = "相同标题",
                    rating = 2.0,
                    updatedAt = existingAt.minusSeconds(1),
                ),
            ),
            shelves = emptyList(),
            readerPreferences = null,
        )

        val reviewed = RoomTransferRepository(database).withDatabaseConflicts(plan)

        assertEquals(listOf("existing-rating-retained"), reviewed.warnings.map { it.safeCode })
    }

    @Test
    fun newerSemanticProgressWinsEvenWhenItMovesBackwardsAndTimestampTiesKeepExisting() = runBlocking {
        val identity = BookIdentity("fixture.source", "book-42")
        repository.saveBook(LibraryBook(identity, "First title", Instant.EPOCH, Instant.EPOCH))
        val firstAt = Instant.parse("2026-08-08T00:00:00Z")
        val laterAt = firstAt.plusSeconds(1)
        val first = progress(identity, contentId = "chapter-1", offset = 900, bookProgress = 0.9, at = firstAt)
        val backwards = progress(identity, contentId = "chapter-1", offset = 100, bookProgress = 0.1, at = laterAt)
        val staleForward = progress(identity, contentId = "chapter-1", offset = 950, bookProgress = 0.95, at = firstAt)
        val tiedDifferent = progress(identity, contentId = "chapter-2", offset = 500, bookProgress = 0.5, at = laterAt)

        assertEquals(ProgressWriteResult.APPLIED, repository.saveProgress(first))
        assertEquals(ProgressWriteResult.APPLIED, repository.saveProgress(backwards))
        assertEquals(ProgressWriteResult.KEPT_EXISTING, repository.saveProgress(staleForward))
        assertEquals(ProgressWriteResult.KEPT_EXISTING, repository.saveProgress(tiedDifferent))

        val stored = requireNotNull(repository.progress(identity))
        assertEquals("chapter-1", stored.locator.document.contentId)
        assertEquals(100, stored.locator.characterOffset)
        assertEquals(0.1, stored.locator.bookProgress!!, 0.0)
        assertEquals(laterAt, stored.updatedAt)
    }

    @Test
    fun concurrentProgressWritesKeepTheStrictlyNewerCapture() = runBlocking {
        val identity = BookIdentity("fixture.source", "concurrent-progress")
        repository.saveBook(LibraryBook(identity, "First title", Instant.EPOCH, Instant.EPOCH))
        val earlier = progress(
            identity,
            contentId = "chapter-earlier",
            offset = 100,
            bookProgress = 0.1,
            at = Instant.parse("2026-08-08T00:00:00Z"),
        )
        val later = progress(
            identity,
            contentId = "chapter-later",
            offset = 900,
            bookProgress = 0.9,
            at = Instant.parse("2026-08-08T00:00:01Z"),
        )

        val results = concurrently(
            { repository.saveProgress(earlier) },
            { repository.saveProgress(later) },
        )

        assertEquals(ProgressWriteResult.APPLIED, results[1])
        val stored = requireNotNull(repository.progress(identity))
        assertEquals("chapter-later", stored.locator.document.contentId)
        assertEquals(later.updatedAt, stored.updatedAt)
    }

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
    fun remoteLibraryMergeRejectsStaleOrUnavailableLeaseWithoutPartialRows() = runBlocking {
        val sourceId = "org.tsuyomi.wenku8"
        val identity = BookIdentity(sourceId, "remote-42")
        repository.setSourceAvailability(sourceId, "0.2.0", true, 4)
        repository.saveSourceRemotePolicy(SourceRemotePolicy(sourceId, "publisher", "capability", "https://www.wenku8.net", false, false))
        val book = LibraryBook(identity, "远程收藏", Instant.EPOCH, Instant.EPOCH)

        val staleVersion = runCatching { repository.mergeRemoteLibrary(sourceId, listOf(book), "0.1.0", "capability", 4, Instant.EPOCH) }
        val staleCapability = runCatching { repository.mergeRemoteLibrary(sourceId, listOf(book), "0.2.0", "other", 4, Instant.EPOCH) }
        val staleGeneration = runCatching { repository.mergeRemoteLibrary(sourceId, listOf(book), "0.2.0", "capability", 3, Instant.EPOCH) }
        repository.setSourceAvailability(sourceId, "0.2.0", false, 4)
        val unavailableSource = runCatching { repository.mergeRemoteLibrary(sourceId, listOf(book), "0.2.0", "capability", 4, Instant.EPOCH) }
        val unavailableEmptySource = runCatching { repository.mergeRemoteLibrary(sourceId, emptyList(), "0.2.0", "capability", 4, Instant.EPOCH) }

        assertTrue(staleVersion.isFailure)
        assertTrue(staleCapability.isFailure)
        assertTrue(staleGeneration.isFailure)
        assertTrue(unavailableSource.isFailure)
        assertTrue(unavailableEmptySource.isFailure)
        assertTrue(repository.libraryEntries().none { it.book.identity == identity })
        repository.setSourceAvailability(sourceId, "0.2.0", true, 4)
        assertEquals(1, repository.mergeRemoteLibrary(sourceId, listOf(book), "0.2.0", "capability", 4, Instant.EPOCH))
        assertEquals(0, repository.mergeRemoteLibrary(sourceId, emptyList(), "0.2.0", "capability", 4, Instant.EPOCH))
    }

    @Test
    fun remoteAddTransitionNeverTreatsPostAcceptanceFailureAsSafeCancellation() = runBlocking {
        val identity = BookIdentity("org.tsuyomi.wenku8", "add-42")
        val id = repository.beginRemoteAdd(
            LibraryBook(identity, "待同步", Instant.EPOCH, Instant.EPOCH),
            packageDigest = "digest",
            packageVersion = "0.2.0",
            capabilitySetFingerprint = "capability",
            registryGeneration = 2,
            now = Instant.EPOCH,
        )

        assertTrue(repository.transitionRemoteAdd(id, RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.IN_FLIGHT, Instant.EPOCH.plusSeconds(1)))
        assertTrue(repository.transitionRemoteAdd(id, RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.UNRESOLVED, Instant.EPOCH.plusSeconds(2)))
        val illegalCancellation = runCatching {
            repository.transitionRemoteAdd(id, RemoteReconciliationState.UNRESOLVED, RemoteReconciliationState.CANCELLED, Instant.EPOCH.plusSeconds(3))
        }
        assertTrue(illegalCancellation.isFailure)
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
    fun importReplacesInvalidProgressAndDisablesEveryRemoteAddPolicy() = runBlocking {
        val identity = BookIdentity("fixture.source", "import-progress")
        val invalidTimestamp = Instant.parse("2099-01-01T00:00:00Z")
        val importedAt = Instant.parse("2026-08-09T00:00:00Z")
        repository.saveBook(LibraryBook(identity, "Old", Instant.EPOCH, Instant.EPOCH))
        database.libraryDao().insertProgressIfAbsent(
            ReadingProgressEntity(
                identity.sourceId,
                identity.remoteBookId,
                "invalid-chapter",
                null,
                "block",
                null,
                -1,
                null,
                1.0,
                invalidTimestamp.epochSecond,
                invalidTimestamp.nano,
            ),
        )
        listOf("fixture.source", "other.source").forEach { sourceId ->
            repository.saveSourceRemotePolicy(
                SourceRemotePolicy(sourceId, "publisher", "capability-$sourceId", "https://example.invalid", true, false),
            )
        }
        val plan = ImportPlan(
            kind = ImportKind.TSUYOMI_TRANSFER,
            sourceCreatedAt = importedAt,
            books = listOf(
                TransferBook(
                    identity = identity,
                    title = "Imported",
                    updatedAt = importedAt,
                    progress = TransferProgress(chapterId = "imported-chapter", bookProgress = 0.5, updatedAt = importedAt),
                ),
            ),
            shelves = emptyList(),
            readerPreferences = null,
        )
        val transfer = RoomTransferRepository(database)

        transfer.prepare("import-progress", plan, "digest", "cache/import", "{}", importedAt)
        transfer.applyRoomPlan("import-progress", "digest", plan)

        val stored = requireNotNull(repository.progress(identity))
        assertEquals("imported-chapter", stored.locator.document.contentId)
        assertEquals(0.5, stored.locator.bookProgress!!, 0.0)
        assertEquals(importedAt, stored.updatedAt)
        listOf("fixture.source", "other.source").forEach { sourceId ->
            assertFalse(requireNotNull(repository.sourceRemotePolicy(sourceId)).addWritebackEnabled)
        }
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

    private fun progress(
        identity: BookIdentity,
        contentId: String,
        offset: Int,
        bookProgress: Double,
        at: Instant,
    ): ReadingProgress = ReadingProgress(
        identity = identity,
        locator = ReaderLocator(
            document = DocumentIdentity(identity.sourceId, identity.remoteBookId, contentId),
            blockId = "block-1",
            characterOffset = offset,
            bookProgress = bookProgress,
            capturedAt = at,
        ),
    )
}

private suspend fun <T> concurrently(vararg actions: suspend () -> T): List<T> = coroutineScope {
    val start = CompletableDeferred<Unit>()
    val jobs = actions.map { action ->
        async(Dispatchers.IO) {
            start.await()
            action()
        }
    }
    start.complete(Unit)
    jobs.awaitAll()
}
