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
import org.tsuyomi.core.database.room.SubscriptionDraftEntity
import org.tsuyomi.core.database.room.ReadingProgressEntity
import org.tsuyomi.shared.backup.ImportKind
import org.tsuyomi.shared.backup.ImportSeverity
import org.tsuyomi.shared.backup.ImportPlan
import org.tsuyomi.shared.backup.TransferBook
import org.tsuyomi.shared.backup.TransferProgress
import org.tsuyomi.shared.backup.TransferShelf
import org.tsuyomi.shared.backup.ImportedSmartCollection
import org.tsuyomi.shared.backup.ImportedSubscriptionDraft
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.smartshelf.MatchMode
import org.tsuyomi.shared.smartshelf.ProgressState
import org.tsuyomi.shared.smartshelf.SmartPredicate
import org.tsuyomi.shared.smartshelf.SmartRule
import org.tsuyomi.shared.smartshelf.SmartRuleNode
import org.tsuyomi.shared.smartshelf.SmartRuleCodec

@RunWith(AndroidJUnit4::class)
class RoomLibraryCatalogInstrumentedTest {
    private val fixture = RoomLibraryRepositoryInstrumentedTestFixture()
    private val database get() = fixture.database
    private val repository get() = fixture.repository

    @After
    fun closeDatabase() = fixture.close()

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
    fun importReviewRetainsCollidingSmartRulesAndSubscriptionDrafts() = runBlocking {
        val transfer = RoomTransferRepository(database)
        val existingRule = SmartRule(root = SmartRuleNode.Predicate(SmartPredicate.HasUnreadUpdate))
        val importedRule = SmartRule(root = SmartRuleNode.Predicate(SmartPredicate.ProgressIn(setOf(ProgressState.READING))))
        val existingRuleJson = SmartRuleCodec.encode(existingRule)
        repository.createSmartCollection(
            LibraryCollection("smart-collision", CollectionKind.SMART, "本地智能集合", null, 0),
            existingRule,
        )
        repository.createCollection(LibraryCollection("subscription-collision", CollectionKind.SUBSCRIPTION, "本地订阅草稿", null, 1))
        database.libraryDao().upsertSubscriptionDraft(
            SubscriptionDraftEntity("subscription-collision", "existing", "{\"source\":\"local\"}", "{\"query\":\"local\"}", false, "local"),
        )
        val plan = ImportPlan(
            kind = ImportKind.TSUYOMI_TRANSFER,
            sourceCreatedAt = Instant.EPOCH,
            books = emptyList(),
            shelves = emptyList(),
            readerPreferences = null,
            smartCollections = listOf(ImportedSmartCollection("smart-collision", "导入智能集合", SmartRuleCodec.encode(importedRule))),
            subscriptionDrafts = listOf(ImportedSubscriptionDraft("subscription-collision", "导入订阅草稿", "imported", "{}", "{}")),
        )

        val reviewed = transfer.withDatabaseConflicts(plan)

        assertEquals(
            setOf("existing-smart-collection-retained", "existing-subscription-draft-retained"),
            reviewed.warnings.mapTo(linkedSetOf()) { it.safeCode },
        )
        transfer.prepare("collection-collision", reviewed, "digest", "plan.json", "{}", Instant.EPOCH)
        transfer.applyRoomPlan("collection-collision", "digest", reviewed)
        assertEquals(existingRuleJson, database.libraryDao().smartRule("smart-collision")?.astJson)
        assertEquals("existing", database.libraryDao().subscriptionDraft("subscription-collision")?.mode)
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






}

