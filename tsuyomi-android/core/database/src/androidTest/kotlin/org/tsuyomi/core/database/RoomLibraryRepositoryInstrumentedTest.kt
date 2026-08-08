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
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity

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
        repository.saveBook(LibraryBook(identity, "First title", Instant.EPOCH, Instant.EPOCH))
        repository.createCollection(LibraryCollection("favorites", CollectionKind.MANUAL, "收藏", null, 0))

        assertTrue(repository.addManualMembership("favorites", identity))
        assertFalse(repository.addManualMembership("favorites", identity))
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
