/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tsuyomi.shared.librarydomain.CollectionKind
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.librarydomain.LocalPublicationMetadata
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LibraryCollection
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.model.BookIdentity

class LibraryRootContentTest {
    @Test
    fun ruleOrderingKeepsStructuralNodesFirstAndDeduplicatesBooks() {
        val firstBook = entry("book-a")
        val secondBook = entry("book-b")
        val collection = collection("manual", "收藏夹", 0)
        val rootMirror = LibraryMirrorShortcut("source", null, "网站", 4, false)
        val folderMirror = LibraryMirrorShortcut("source", "favorites", "特别收藏", 2, false)

        val items = buildLibraryRootItems(
            entries = listOf(firstBook, secondBook, firstBook),
            collections = listOf(collection),
            collectionCounts = mapOf(collection.collectionId to 2),
            mirrors = listOf(rootMirror, folderMirror),
            placements = emptyList(),
            customOrder = false,
        )

        assertEquals(
            listOf(libraryCollectionRootId("manual"), libraryMirrorRootId("source")),
            items.take(2).map { it.key },
        )
        assertEquals(listOf("book-a", "book-b"), items.filterIsInstance<LibraryRootItem.Book>()
            .map { (it.entry.book.identity as BookIdentity.Remote).remoteBookId })
        assertEquals(1, items.filterIsInstance<LibraryRootItem.Mirror>().size)
        assertTrue(items.none { it.key.contains("favorites") })
    }

    @Test
    fun customOrderingIntermixesStructuralNodesAtPersistedBookOffsets() {
        val collection = collection("manual", "收藏夹", 0)
        val mirror = LibraryMirrorShortcut("source", null, "网站", 0, false)

        val items = buildLibraryRootItems(
            entries = listOf(entry("book-a"), entry("book-b"), entry("book-c")),
            collections = listOf(collection),
            collectionCounts = emptyMap(),
            mirrors = listOf(mirror),
            placements = listOf(
                LibraryRootNodePlacement(libraryMirrorRootId("source"), 1),
                LibraryRootNodePlacement(libraryCollectionRootId("manual"), 2),
            ),
            customOrder = true,
        )

        assertEquals(
            listOf(
                "book:r14:fixture.source6:book-a",
                libraryMirrorRootId("source"),
                "book:r14:fixture.source6:book-b",
                libraryCollectionRootId("manual"),
                "book:r14:fixture.source6:book-c",
            ),
            items.map { it.key },
        )
    }

    @Test
    fun localPublicationsRemainRootBooksWithoutRemoteSourceAvailabilitySemantics() {
        val available = localEntry("00000000-0000-4000-8000-000000000002")
        val missing = available.copy(
            book = available.book.copy(
                localPublication = requireNotNull(available.book.localPublication).copy(originalAvailable = false),
            ),
        )
        val items = buildLibraryRootItems(
            entries = listOf(available),
            collections = emptyList(),
            collectionCounts = emptyMap(),
            mirrors = emptyList(),
            placements = emptyList(),
            customOrder = false,
        )

        assertEquals(listOf("book:${available.book.identity.storageKey}"), items.map { it.key })
        assertEquals("未开始", available.libraryStatusLabel())
        assertEquals("原书文件缺失，可重新关联", missing.libraryStatusLabel())
        assertEquals("来源未安装", entry("remote").copy(sourceAvailable = false).libraryStatusLabel())
        val continued = available.copy(readerVisitedAt = Instant.EPOCH)
        val later = available.copy(readLater = true)
        assertEquals(
            listOf(continued.book.identity),
            LibraryUiState(entries = listOf(continued), filter = SystemLibraryFilter.CONTINUE, isRootProjection = false)
                .projectedEntries().map { it.book.identity },
        )
        assertEquals(
            listOf(later.book.identity),
            LibraryUiState(entries = listOf(later), filter = SystemLibraryFilter.READ_LATER, isRootProjection = false)
                .projectedEntries().map { it.book.identity },
        )
        assertTrue(
            LibraryUiState(entries = listOf(available), filter = SystemLibraryFilter.DORMANT, isRootProjection = false)
                .projectedEntries().isEmpty(),
        )
    }

    @Test
    fun primaryPagerMapsTapAndSwipeToTheSameTab() {
        val tapTarget = libraryPrimaryPageFor(SystemLibraryFilter.READ_LATER)
        val swipeTarget = libraryPrimaryTabAt(2)

        assertEquals(SystemLibraryFilter.READ_LATER, libraryPrimaryTabAt(tapTarget))
        assertEquals(SystemLibraryFilter.READ_LATER, swipeTarget)
    }

    private fun localEntry(id: String): LibraryEntry {
        val identity = BookIdentity.Local(id)
        return LibraryEntry(
            book = LibraryBook(
                identity = identity,
                title = "本地书籍",
                addedAt = Instant.EPOCH,
                metadataUpdatedAt = Instant.EPOCH,
                localPublication = LocalPublicationMetadata(
                    format = LocalPublicationFormat.EPUB,
                    originalSha256 = "a".repeat(64),
                    originalSizeBytes = 1,
                    originalAvailable = true,
                ),
            ),
            libraryAddedAt = Instant.EPOCH,
            rating = null,
            localTags = emptySet(),
            sourceAvailable = false,
            reconciliation = null,
        )
    }

    private fun entry(id: String): LibraryEntry {
        val identity = BookIdentity.Remote("fixture.source", id)
        return LibraryEntry(
            book = LibraryBook(identity, id, Instant.EPOCH, Instant.EPOCH),
            libraryAddedAt = Instant.EPOCH,
            rating = null,
            localTags = emptySet(),
            sourceAvailable = true,
            reconciliation = null,
        )
    }

    private fun collection(id: String, title: String, order: Long) = LibraryCollection(
        collectionId = id,
        kind = CollectionKind.MANUAL,
        title = title,
        parentCollectionId = null,
        displayOrder = order,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
