/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.tsuyomi.shared.librarydomain.SourceRemotePolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.database.room.ReadingProgressEntity
import org.tsuyomi.shared.backup.ImportKind
import org.tsuyomi.shared.backup.ImportPlan
import org.tsuyomi.shared.backup.TransferBook
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.ProgressWriteResult
import org.tsuyomi.shared.backup.TransferProgress
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.locator.bookmarkPositionKey
import org.tsuyomi.shared.model.BookIdentity

@RunWith(AndroidJUnit4::class)
class RoomReadingProgressInstrumentedTest {
    private val fixture = RoomLibraryRepositoryInstrumentedTestFixture()
    private val database get() = fixture.database
    private val repository get() = fixture.repository

    @After
    fun closeDatabase() = fixture.close()
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
    fun readerHistory_is_unpinned_deduplicated_completed_and_excludes_metadata_only() = runBlocking {
        val reopened = BookIdentity("fixture.reader.history", "reopened")
        val completed = BookIdentity("fixture.reader.history", "completed")
        val legacyProgress = BookIdentity("fixture.reader.history", "legacy-progress")
        val metadataOnly = BookIdentity("fixture.reader.history", "metadata-only")
        val firstVisit = Instant.parse("2026-09-14T01:00:00Z")
        val completedVisit = firstVisit.plusSeconds(10)
        val latestVisit = firstVisit.plusSeconds(20)

        listOf(reopened, completed, legacyProgress, metadataOnly).forEach { identity ->
            repository.saveBook(LibraryBook(identity, identity.remoteBookId, Instant.EPOCH, Instant.EPOCH))
        }
        repository.ensureRetainedLibraryEntry(
            LibraryBook(metadataOnly, "metadata-only", Instant.EPOCH, Instant.EPOCH),
        )
        repository.recordReaderVisit(reopened, firstVisit)
        repository.recordReaderVisit(completed, completedVisit)
        repository.saveProgress(progress(legacyProgress, "chapter-1", 10, 0.4, firstVisit))
        repository.saveProgress(progress(completed, "chapter-final", 99, 1.0, completedVisit))
        repository.recordReaderVisit(reopened, firstVisit.plusSeconds(1))
        repository.recordReaderVisit(reopened, latestVisit)

        val history = repository.readerHistoryEntries()
        assertEquals(listOf(reopened, completed, legacyProgress), history.map { it.book.identity })
        assertEquals(latestVisit, history.first().readerVisitedAt)
        assertFalse(history.first().localMembership)
        assertEquals(null, history.first().progress)
        assertEquals(1.0, requireNotNull(history[1].progress).locator.bookProgress!!, 0.0)
        assertTrue(repository.libraryEntries().isEmpty())
        assertEquals(null, repository.libraryEntry(reopened))
    }

    @Test
    fun readerHistory_survives_database_recreation_without_pinning() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "reader-history-${UUID.randomUUID()}"
        val identity = BookIdentity("fixture.reader.history", "recreated")
        val visitedAt = Instant.parse("2026-09-14T02:00:00Z")
        context.deleteDatabase(databaseName)
        var first: TsuyomiDatabase? = null
        try {
            val initial = Room.databaseBuilder(context, TsuyomiDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            first = initial
            RoomLibraryRepository(initial).apply {
                saveBook(LibraryBook(identity, "重建后仍可继续", Instant.EPOCH, Instant.EPOCH))
                recordReaderVisit(identity, visitedAt)
            }
            initial.close()
            first = null

            val recreated = Room.databaseBuilder(context, TsuyomiDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            try {
                val history = RoomLibraryRepository(recreated).readerHistoryEntries()
                assertEquals(listOf(identity), history.map { it.book.identity })
                assertEquals(visitedAt, history.single().readerVisitedAt)
                assertFalse(history.single().localMembership)
            } finally {
                recreated.close()
            }
        } finally {
            first?.close()
            context.deleteDatabase(databaseName)
        }
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
                    completedChapterIds = setOf("imported-chapter-2", "imported-chapter-5"),
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
        assertEquals(setOf("imported-chapter-2", "imported-chapter-5"), repository.completedChapterIds(identity))
        listOf("fixture.source", "other.source").forEach { sourceId ->
            assertFalse(requireNotNull(repository.sourceRemotePolicy(sourceId)).addWritebackEnabled)
        }
    }

    @Test
    fun completedChaptersPersistAsAnExactNonSequentialSet() = runBlocking {
        val identity = BookIdentity("fixture.source", "non-sequential")
        repository.addToLibrary(LibraryBook(identity, "章节状态", Instant.EPOCH, Instant.EPOCH))

        assertEquals(true, repository.markChapterCompleted(identity, "chapter-2", Instant.ofEpochSecond(20)))
        assertEquals(true, repository.markChapterCompleted(identity, "chapter-5", Instant.ofEpochSecond(50)))
        assertEquals(false, repository.markChapterCompleted(identity, "chapter-2", Instant.ofEpochSecond(60)))
        assertEquals(setOf("chapter-2", "chapter-5"), repository.completedChapterIds(identity))
        val exported = RoomTransferRepository(database).exportSnapshot(Instant.ofEpochSecond(70), readerPreferences = null)
        assertEquals(setOf("chapter-2", "chapter-5"), exported.library.single().completedChapterIds)

        val recreatedRepository = RoomLibraryRepository(database)
        assertEquals(setOf("chapter-2", "chapter-5"), recreatedRepository.completedChapterIds(identity))
    }

    @Test
    fun bookmarks_keep_positions_distinct_ignore_recapture_and_survive_unpin_without_progress_side_effects() = runBlocking {
        val primary = BookIdentity("fixture.source", "bookmark-book")
        val sameChapterElsewhere = BookIdentity("other.source", "bookmark-book")
        repository.addToLibrary(LibraryBook(primary, "书签状态", Instant.EPOCH, Instant.EPOCH))
        repository.saveBook(LibraryBook(sameChapterElsewhere, "另一来源", Instant.EPOCH, Instant.EPOCH))
        val first = bookmark(primary, "chapter-2", offset = 12, capturedAt = Instant.EPOCH)
        val laterInChapter = bookmark(primary, "chapter-2", offset = 13, capturedAt = Instant.EPOCH)
        val recapturedFirst = first.copy(
            textAnchorDigest = "b".repeat(64),
            chapterProgress = 0.9,
            bookProgress = 0.8,
            capturedAt = Instant.EPOCH.plusSeconds(1),
        )
        val elsewhere = bookmark(sameChapterElsewhere, "chapter-2", offset = 12, capturedAt = Instant.EPOCH)

        assertTrue(runCatching { repository.addBookmarks(primary, listOf(elsewhere)) }.exceptionOrNull() is IllegalArgumentException)

        repository.addBookmarks(primary, listOf(first, laterInChapter, recapturedFirst))
        assertTrue(repository.toggleBookmark(elsewhere))
        assertEquals(
            listOf(first, laterInChapter).sortedBy(ReaderLocator::bookmarkPositionKey),
            repository.observeBookmarks(primary).first(),
        )
        assertEquals(listOf(elsewhere), repository.bookmarks(sameChapterElsewhere))
        assertEquals(null, repository.progress(primary))
        assertTrue(repository.completedChapterIds(primary).isEmpty())
        repository.setSourceAvailability(primary.sourceId, "1", available = true, generation = 0)
        repository.markMissingSourcesUnavailable(listOf(sameChapterElsewhere.sourceId))
        assertFalse(requireNotNull(repository.sourceAvailability(primary.sourceId)).available)
        assertEquals(listOf(first, laterInChapter).sortedBy(ReaderLocator::bookmarkPositionKey), repository.bookmarks(primary))

        assertTrue(repository.removeFromLibrary(primary))
        assertTrue(repository.removeBookmark(recapturedFirst))
        assertFalse(repository.removeBookmark(recapturedFirst))
        assertEquals(listOf(laterInChapter), repository.bookmarks(primary))
        assertEquals(listOf(elsewhere), repository.bookmarks(sameChapterElsewhere))
    }

    @Test
    fun bookmark_capacity_rolls_back_an_overflowing_union_but_allows_recapture_deduplication_and_removal() = runBlocking {
        val identity = BookIdentity("fixture.source", "bookmark-capacity")
        repository.saveBook(LibraryBook(identity, "书签容量", Instant.EPOCH, Instant.EPOCH))
        val initial = (1..20_000).map { index ->
            bookmark(identity, "chapter-$index", offset = index, capturedAt = Instant.EPOCH)
        }
        repository.addBookmarks(identity, initial)
        repository.addBookmarks(identity, listOf(initial.first().copy(capturedAt = Instant.EPOCH.plusSeconds(1))))
        val overflow = bookmark(identity, "overflow", offset = 1, capturedAt = Instant.EPOCH)

        assertTrue(runCatching { repository.toggleBookmark(overflow) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(
            runCatching { repository.addBookmarks(identity, listOf(initial.first().copy(capturedAt = Instant.EPOCH.plusSeconds(2)), overflow)) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertEquals(initial.sortedBy(ReaderLocator::bookmarkPositionKey), repository.bookmarks(identity))
        assertTrue(repository.removeBookmark(initial.first()))
        assertTrue(repository.toggleBookmark(overflow))
        assertEquals(20_000, repository.bookmarks(identity).size)
    }

    private fun bookmark(identity: BookIdentity, chapterId: String, offset: Int, capturedAt: Instant): ReaderLocator = ReaderLocator(
        document = DocumentIdentity(identity.sourceId, identity.remoteBookId, chapterId),
        blockId = "block-1",
        textAnchorDigest = "a".repeat(64),
        characterOffset = offset,
        chapterProgress = 0.5,
        bookProgress = 0.5,
        capturedAt = capturedAt,
    )
}
