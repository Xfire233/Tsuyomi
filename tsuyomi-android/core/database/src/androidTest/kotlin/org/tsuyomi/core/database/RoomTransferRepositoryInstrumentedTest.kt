/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.shared.backup.ImportKind
import org.tsuyomi.shared.backup.ImportPlan
import org.tsuyomi.shared.backup.ImportParseResult
import org.tsuyomi.shared.backup.TransferBook
import org.tsuyomi.shared.backup.TransferCodec
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.locator.bookmarkPositionKey
import org.tsuyomi.shared.model.BookIdentity

@RunWith(AndroidJUnit4::class)
class RoomTransferRepositoryInstrumentedTest {
    @Test
    fun retainedUnpinnedMetadataRoundTripsWithoutRestoringLocalPin() = runBlocking {
        val sourceDatabase = inMemoryDatabase()
        val destinationDatabase = inMemoryDatabase()
        try {
            val identity = BookIdentity("fixture.source", "retained-unpinned")
            val sourceLibrary = RoomLibraryRepository(sourceDatabase)
            sourceLibrary.addToLibrary(
                LibraryBook(identity, "保留的书", Instant.EPOCH, Instant.EPOCH),
            )
            sourceLibrary.setRating(identity, 4)
            sourceLibrary.setReadLater(identity, true)
            sourceLibrary.setLocalTags(identity, setOf("收藏", "待续"))
            assertTrue(sourceLibrary.removeFromLibrary(identity))

            val snapshot = RoomTransferRepository(sourceDatabase).exportSnapshot(Instant.ofEpochSecond(100), null)
            val exported = snapshot.library.single()
            assertFalse(exported.localPin)
            assertEquals(4.0, exported.rating)
            assertTrue(exported.readLater)
            assertEquals(setOf("待续", "收藏"), exported.localTags)

            val plan = requireNotNull(
                (TransferCodec.parse(TransferCodec.encode(snapshot)) as? ImportParseResult.Ready)?.plan,
            )
            val destinationTransfer = RoomTransferRepository(destinationDatabase)
            destinationTransfer.prepare("retained-unpinned", plan, "retained-unpinned-digest", "retained-unpinned.json", "{}", Instant.ofEpochSecond(101))
            destinationTransfer.applyRoomPlan("retained-unpinned", "retained-unpinned-digest", plan)

            val restored = requireNotNull(RoomLibraryRepository(destinationDatabase).libraryEntry(identity))
            assertFalse(restored.localMembership)
            assertEquals(4, restored.rating)
            assertTrue(restored.readLater)
            assertEquals(setOf("待续", "收藏"), restored.localTags)
            assertTrue(RoomLibraryRepository(destinationDatabase).libraryEntries().isEmpty())
        } finally {
            destinationDatabase.close()
            sourceDatabase.close()
        }
    }

    @Test
    fun incomingUnpinnedRecordDoesNotClearAnExistingLocalPin() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val identity = BookIdentity("fixture.source", "existing-pin")
            val repository = RoomLibraryRepository(database)
            repository.addToLibrary(
                LibraryBook(identity, "本地固定", Instant.EPOCH, Instant.EPOCH),
            )
            val importedAt = Instant.ofEpochSecond(100)
            val plan = ImportPlan(
                kind = ImportKind.TSUYOMI_TRANSFER,
                sourceCreatedAt = importedAt,
                books = listOf(
                    TransferBook(
                        identity = identity,
                        title = "导入但未固定",
                        localPin = false,
                        updatedAt = importedAt,
                    ),
                ),
                shelves = emptyList(),
                readerPreferences = null,
            )
            val transfer = RoomTransferRepository(database)
            transfer.prepare("existing-pin", plan, "existing-pin-digest", "existing-pin.json", "{}", importedAt)
            transfer.applyRoomPlan("existing-pin", "existing-pin-digest", plan)

            assertTrue(requireNotNull(repository.libraryEntry(identity)).localMembership)
        } finally {
            database.close()
        }
    }


    @Test
    fun bookmarked_unpinned_book_round_trips_exact_positions_without_creating_a_pin_or_erasing_marks_on_reapply() = runBlocking {
        val sourceDatabase = inMemoryDatabase()
        val destinationDatabase = inMemoryDatabase()
        try {
            val identity = BookIdentity("fixture.source", "bookmark-only")
            val sourceLibrary = RoomLibraryRepository(sourceDatabase)
            sourceLibrary.saveBook(LibraryBook(identity, "只含书签", Instant.EPOCH, Instant.EPOCH))
            val first = bookmark(identity, "chapter-2", 12, Instant.EPOCH)
            val second = bookmark(identity, "chapter-2", 13, Instant.EPOCH.plusSeconds(1))
            assertTrue(sourceLibrary.toggleBookmark(first))
            assertTrue(sourceLibrary.toggleBookmark(second))

            val snapshot = RoomTransferRepository(sourceDatabase).exportSnapshot(Instant.ofEpochSecond(100), null)
            val exported = snapshot.library.single()
            assertFalse(exported.localPin)
            assertEquals(listOf(first, second).sortedBy(ReaderLocator::bookmarkPositionKey), exported.bookmarks)

            val plan = requireNotNull(
                (TransferCodec.parse(TransferCodec.encode(snapshot)) as? ImportParseResult.Ready)?.plan,
            )
            val destinationTransfer = RoomTransferRepository(destinationDatabase)
            destinationTransfer.prepare("bookmark-only", plan, "bookmark-only-digest", "bookmark-only.json", "{}", Instant.ofEpochSecond(101))
            destinationTransfer.applyRoomPlan("bookmark-only", "bookmark-only-digest", plan)
            destinationTransfer.applyRoomPlan("bookmark-only", "bookmark-only-digest", plan)

            val destinationLibrary = RoomLibraryRepository(destinationDatabase)
            assertFalse(requireNotNull(destinationLibrary.libraryEntry(identity)).localMembership)
            assertTrue(destinationLibrary.libraryEntries().isEmpty())
            assertEquals(listOf(first, second).sortedBy(ReaderLocator::bookmarkPositionKey), destinationLibrary.bookmarks(identity))
        } finally {
            destinationDatabase.close()
            sourceDatabase.close()
        }
    }

    @Test
    fun legacy_transfer_without_bookmarks_never_erases_existing_semantic_marks() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val identity = BookIdentity("fixture.source", "legacy-bookmarks")
            val library = RoomLibraryRepository(database)
            library.saveBook(LibraryBook(identity, "现有元数据", Instant.EPOCH, Instant.EPOCH))
            val preserved = bookmark(identity, "preserved-chapter", 9, Instant.EPOCH)
            assertTrue(library.toggleBookmark(preserved))
            val bytes = """{"format":"tsuyomi-transfer","version":3,"createdAt":"2026-08-08T00:00:00Z","library":[{"identity":{"sourceId":"fixture.source","remoteBookId":"legacy-bookmarks"},"title":"导入元数据","updatedAt":"2026-08-08T00:00:01Z","completedChapterIds":[],"localPin":false}],"shelves":[]}""".encodeToByteArray()
            val plan = requireNotNull((TransferCodec.parse(bytes) as? ImportParseResult.Ready)?.plan)
            val transfer = RoomTransferRepository(database)
            transfer.prepare("legacy-bookmarks", plan, "legacy-bookmarks-digest", "legacy-bookmarks.json", "{}", Instant.ofEpochSecond(101))
            transfer.applyRoomPlan("legacy-bookmarks", "legacy-bookmarks-digest", plan)

            assertEquals(listOf(preserved), library.bookmarks(identity))
        } finally {
            database.close()
        }
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
    private fun inMemoryDatabase(): TsuyomiDatabase = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        TsuyomiDatabase::class.java,
    ).allowMainThreadQueries().build()
}
