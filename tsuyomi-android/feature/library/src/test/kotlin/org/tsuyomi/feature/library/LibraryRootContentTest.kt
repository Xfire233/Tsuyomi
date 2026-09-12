/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tsuyomi.core.database.CollectionKind
import org.tsuyomi.core.database.LibraryBook
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.LibraryEntry
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
            .map { it.entry.book.identity.remoteBookId })
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
                "book:fixture.source\u0000book-a",
                libraryMirrorRootId("source"),
                "book:fixture.source\u0000book-b",
                libraryCollectionRootId("manual"),
                "book:fixture.source\u0000book-c",
            ),
            items.map { it.key },
        )
    }

    @Test
    fun tabDestinationFadeRunsOnlyForNonStaticFixedTabChanges() {
        assertFalse(shouldFadeLibraryTabDestination(null, SystemLibraryFilter.ALL, staticMotion = false))
        assertTrue(shouldFadeLibraryTabDestination(SystemLibraryFilter.ALL, SystemLibraryFilter.CONTINUE, staticMotion = false))
        assertFalse(shouldFadeLibraryTabDestination(SystemLibraryFilter.ALL, SystemLibraryFilter.ALL, staticMotion = false))
        assertFalse(shouldFadeLibraryTabDestination(SystemLibraryFilter.ALL, SystemLibraryFilter.CONTINUE, staticMotion = true))
    }

    private fun entry(id: String): LibraryEntry {
        val identity = BookIdentity("fixture.source", id)
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
