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
import org.tsuyomi.shared.backup.TransferLocalPublicationMetadata
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.librarydomain.LocalPublicationMetadata
import org.tsuyomi.shared.librarydomain.LocalTextHeading
import org.tsuyomi.shared.librarydomain.LocalTextParseOptions
import org.tsuyomi.shared.librarydomain.LibraryCollection
import org.tsuyomi.shared.librarydomain.CollectionKind
import org.tsuyomi.shared.librarydomain.RemoteMirrorBookSnapshot
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
            val identity = BookIdentity.Remote("fixture.source", "retained-unpinned")
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
            val identity = BookIdentity.Remote("fixture.source", "existing-pin")
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
            val identity = BookIdentity.Remote("fixture.source", "bookmark-only")
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
            val identity = BookIdentity.Remote("fixture.source", "legacy-bookmarks")
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

    @Test
    fun local_txt_metadata_options_cover_and_generic_state_restore_without_original_availability() = runBlocking {
        val source = inMemoryDatabase()
        val destination = inMemoryDatabase()
        try {
            val identity = BookIdentity.Local("f2b8ef64-9375-4fc1-b7c9-274b4beafed1")
            val independent = BookIdentity.Local("f2b8ef64-9375-4fc1-b7c9-274b4beafed2")
            val options = LocalTextParseOptions("GB18030", listOf(LocalTextHeading(0, "序章"), LocalTextHeading(42, "第二节")), 256)
            val metadata = LocalPublicationMetadata(LocalPublicationFormat.TXT, "a".repeat(64), 4096, true, options, "4637570b-1109-48ba-8a1b-256e868ca14c")
            val book = LibraryBook(identity, "TXT", Instant.EPOCH, Instant.EPOCH, localPublication = metadata)
            val library = RoomLibraryRepository(source)
            library.addToLibrary(book)
            library.addToLibrary(book.copy(identity = independent))
            library.setRating(identity, 4)
            library.setReadLater(identity, true)
            library.setLocalTags(identity, listOf("收藏"))
            library.createCollection(LibraryCollection("favorites", CollectionKind.MANUAL, "收藏", null, 0))
            library.addManualMembership("favorites", identity)
            val progress = progress(identity, "section-42", 12, 0.4, Instant.ofEpochSecond(10))
            library.saveProgress(progress)
            library.recordReaderVisit(identity, Instant.ofEpochSecond(11))
            library.markChapterCompleted(identity, "section-0", Instant.ofEpochSecond(12))
            val mark = bookmark(identity, "section-42", 12, Instant.ofEpochSecond(13))
            library.toggleBookmark(mark)
            val replacementCover = "4637570b-1109-48ba-8a1b-256e868ca14d"
            library.saveBook(book.copy(metadataUpdatedAt = Instant.ofEpochSecond(20), localPublication = metadata.copy(coverResourceId = replacementCover)))
            assertEquals(options, library.book(identity)?.localPublication?.txtOptions)
            assertEquals(replacementCover, library.book(identity)?.localPublication?.coverResourceId)
            assertEquals(Instant.ofEpochSecond(11), library.readerHistoryEntries().single().readerVisitedAt)

            val snapshot = RoomTransferRepository(source).exportSnapshot(Instant.ofEpochSecond(30), null)
            val bytes = TransferCodec.encode(snapshot)
            assertFalse(bytes.decodeToString().contains("originalAvailable"))
            assertFalse(bytes.decodeToString().contains("content://"))
            val plan = (TransferCodec.parse(bytes) as ImportParseResult.Ready).plan
            val transfer = RoomTransferRepository(destination)
            transfer.prepare("local-txt", plan, "local-txt-digest", "local-txt.json", "{}", Instant.ofEpochSecond(31))
            transfer.applyRoomPlan("local-txt", "local-txt-digest", plan)
            val restoredLibrary = RoomLibraryRepository(destination)
            val restored = requireNotNull(restoredLibrary.libraryEntry(identity))
            val restoredProgress = progress.copy(locator = progress.locator.copy(blockId = "transfer-anchor"))
            assertEquals(metadata.copy(originalAvailable = false, coverResourceId = replacementCover), restored.book.localPublication)
            assertEquals(4, restored.rating)
            assertTrue(restored.readLater)
            assertEquals(setOf("收藏"), restored.localTags)
            assertEquals(restoredProgress, restored.progress)
            assertEquals(setOf("section-0"), restoredLibrary.completedChapterIds(identity))
            assertEquals(listOf(mark), restoredLibrary.bookmarks(identity))
            assertEquals(setOf("favorites"), restoredLibrary.manualCollectionIds(identity))
            assertFalse(restored.sourceAvailable)
            assertEquals(null, restored.reconciliation)
            assertFalse(requireNotNull(restoredLibrary.book(independent)?.localPublication).originalAvailable)
            assertTrue(restoredLibrary.bookmarks(independent).isEmpty())
            assertEquals(null, restoredLibrary.progress(independent))
            assertTrue(restoredLibrary.removeFromLibrary(identity))
            assertEquals(listOf(mark), restoredLibrary.bookmarks(identity))
            assertEquals(restoredProgress, restoredLibrary.progress(identity))
            assertTrue(restoredLibrary.manualCollectionIds(identity).isEmpty())
            assertTrue(restoredLibrary.deleteLocalPublication(identity))
            assertEquals(null, restoredLibrary.book(identity))
            assertTrue(restoredLibrary.bookmarks(identity).isEmpty())
            assertEquals(null, restoredLibrary.progress(identity))
            assertTrue(restoredLibrary.completedChapterIds(identity).isEmpty())
            assertEquals(1, restoredLibrary.localPublications().size)
            assertEquals(independent, restoredLibrary.localPublications().single().identity)
            destination.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally {
            source.close()
            destination.close()
        }
    }

    @Test
    fun local_origin_cannot_enter_remote_mirrors_reconciliation_or_remote_update_scope() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val library = RoomLibraryRepository(database)
            val identity = BookIdentity.Local("f2b8ef64-9375-4fc1-b7c9-274b4beafed1")
            val book = LibraryBook(identity, "Local", Instant.EPOCH, Instant.EPOCH,
                localPublication = LocalPublicationMetadata(LocalPublicationFormat.EPUB, "a".repeat(64), 1234, true))
            library.addToLibrary(book)
            library.setSourceAvailability("fixture.source", "1", true, 1)
            library.ensureRemoteMirrorBinding("fixture.source", "来源")
            assertTrue(runCatching { library.upsertRemoteMirrorBook(book, null) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { RemoteMirrorBookSnapshot(book, null) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { RemoteLibraryMergeRequest("fixture.source", listOf(book), "1", "capability", 1, Instant.EPOCH) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { RemoteMutationRequest(book, packageDigest = "package", packageVersion = "1", capabilitySetFingerprint = "capability", registryGeneration = 1, startedAt = Instant.EPOCH) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(library.unresolvedReconciliations().isEmpty())
            assertTrue(requireNotNull(library.remoteMirrorSnapshot("fixture.source")).books.isEmpty())
            assertFalse(database.updateDao().isBookInUpdateScope("fixture.source", identity.publicationId))
            val remote = BookIdentity.Remote("fixture.source", identity.publicationId)
            library.addToLibrary(LibraryBook(remote, "Remote", Instant.EPOCH, Instant.EPOCH))
            assertTrue(database.updateDao().isBookInUpdateScope(remote.sourceId, remote.remoteBookId))
            assertTrue(library.deleteLocalPublication(identity))
            assertEquals(remote, library.libraryEntries().single().book.identity)
            assertTrue(database.updateDao().isBookInUpdateScope(remote.sourceId, remote.remoteBookId))
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally {
            database.close()
        }
    }

    @Test
    fun import_retains_existing_original_descriptor_when_newer_metadata_claims_different_bytes() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val identity = BookIdentity.Local("f2b8ef64-9375-4fc1-b7c9-274b4beafed1")
            val library = RoomLibraryRepository(database)
            val metadata = LocalPublicationMetadata(LocalPublicationFormat.TXT, "a".repeat(64), 4096, true, LocalTextParseOptions())
            library.addToLibrary(LibraryBook(identity, "Local", Instant.EPOCH, Instant.EPOCH, localPublication = metadata))
            val options = LocalTextParseOptions("UTF-8", listOf(LocalTextHeading(0, "序章")), 16)
            val portable = TransferLocalPublicationMetadata(metadata.format, metadata.originalSha256, metadata.originalSizeBytes, options, "4637570b-1109-48ba-8a1b-256e868ca14c")
            val transfer = RoomTransferRepository(database)
            suspend fun restore(id: String, imported: TransferLocalPublicationMetadata, at: Instant) {
                val plan = ImportPlan(ImportKind.TSUYOMI_TRANSFER, at, listOf(TransferBook(identity, "Imported", updatedAt = at, localPublication = imported)), emptyList(), null)
                transfer.prepare(id, plan, id, "$id.json", "{}", at)
                transfer.applyRoomPlan(id, id, plan)
                assertTrue(transfer.markPreferencesApplied(id, id))
                assertTrue(transfer.complete(id, id, org.tsuyomi.shared.backup.ImportSummary(id, ImportKind.TSUYOMI_TRANSFER, 1, 0, 0, at)))
            }
            restore("same-original", portable, Instant.ofEpochSecond(10))
            assertEquals(metadata.copy(txtOptions = options, coverResourceId = portable.coverResourceId), library.book(identity)?.localPublication)
            restore("different-original", portable.copy(originalSha256 = "b".repeat(64)), Instant.ofEpochSecond(20))
            assertEquals(metadata.copy(txtOptions = options, coverResourceId = portable.coverResourceId), library.book(identity)?.localPublication)
            assertEquals("Imported", library.book(identity)?.title)
        } finally {
            database.close()
        }
    }

    private fun bookmark(identity: BookIdentity, chapterId: String, offset: Int, capturedAt: Instant): ReaderLocator = ReaderLocator(
        document = DocumentIdentity(identity, chapterId),
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
