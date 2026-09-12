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

    private fun inMemoryDatabase(): TsuyomiDatabase = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        TsuyomiDatabase::class.java,
    ).allowMainThreadQueries().build()
}
