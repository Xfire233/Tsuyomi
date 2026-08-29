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
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.database.room.ReadingProgressEntity
import org.tsuyomi.shared.backup.ImportKind
import org.tsuyomi.shared.backup.ImportPlan
import org.tsuyomi.shared.backup.TransferBook
import org.tsuyomi.shared.backup.TransferProgress
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
}
