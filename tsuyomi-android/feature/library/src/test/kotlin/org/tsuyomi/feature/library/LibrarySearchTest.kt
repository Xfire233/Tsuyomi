/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tsuyomi.core.database.CollectionKind
import org.tsuyomi.core.database.LibraryBook
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.ReadingProgress
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity

class LibrarySearchTest {
    @Test
    fun folderTitleSearchIncludesNestedManualAndSmartCollections() {
        val collections = listOf(
            collection("parent", "幻想书库", CollectionKind.MANUAL),
            collection("child", "待读短篇", CollectionKind.MANUAL, parentId = "parent"),
            collection("smart", "近期更新", CollectionKind.SMART),
            collection("draft", "待读兼容草稿", CollectionKind.SUBSCRIPTION),
        )

        val result = searchLocalLibrary("待读", emptyList(), collections)

        assertEquals(listOf("child"), result.collections.map { it.collectionId })
        assertTrue(result.books.isEmpty())
    }

    @Test
    fun bookMetadataSearchNormalizesWidthCaseAndWhitespace() {
        val target = entry(
            id = "target",
            title = "雾港纪事",
            authors = setOf("Alice Writer"),
            localTags = setOf("ＳＣＩ   ＦＩ"),
            remoteTags = setOf("冒险"),
        )
        val other = entry(id = "other", title = "星环邮差")

        val result = searchLocalLibrary("sci fi", listOf(other, target), emptyList())

        assertEquals(listOf(target.book.identity), result.books.map { it.book.identity })
        assertTrue(result.collections.isEmpty())
    }

    @Test
    fun blankQueryReturnsNoResultsInsteadOfTheWholeLibrary() {
        val result = searchLocalLibrary(
            query = "　 ",
            books = listOf(entry(id = "book", title = "不应出现")),
            collections = listOf(collection("folder", "不应出现", CollectionKind.MANUAL)),
        )

        assertTrue(result.books.isEmpty())
        assertTrue(result.collections.isEmpty())
    }

    @Test
    fun recommendationsPrioritizeReadingThenRecentAdditionAndCallerCollection() {
        val readBook = entry(
            id = "read",
            title = "较早读过",
            libraryAddedAt = Instant.parse("2020-01-01T00:00:00Z"),
            progressAt = Instant.parse("2021-01-01T00:00:00Z"),
        )
        val recentAddition = entry(
            id = "recent",
            title = "最近加入",
            libraryAddedAt = Instant.parse("2090-01-01T00:00:00Z"),
        )
        val result = recommendLocalLibrary(
            books = listOf(recentAddition, readBook),
            collections = listOf(
                collection("first", "首个收藏夹", CollectionKind.MANUAL, displayOrder = 0),
                collection("caller", "当前收藏夹", CollectionKind.MANUAL, displayOrder = 10),
                collection("draft", "兼容草稿", CollectionKind.SUBSCRIPTION, displayOrder = -1),
            ),
            preferredCollectionId = "caller",
        )

        assertEquals(listOf("read", "recent"), result.books.map { it.book.identity.remoteBookId })
        assertEquals(listOf("caller", "first"), result.collections.map { it.collectionId })
    }

    private fun entry(
        id: String,
        title: String,
        authors: Set<String> = emptySet(),
        localTags: Set<String> = emptySet(),
        remoteTags: Set<String> = emptySet(),
        libraryAddedAt: Instant = Instant.EPOCH,
        progressAt: Instant? = null,
    ): LibraryEntry = LibraryEntry(
        book = LibraryBook(
            identity = BookIdentity("fixture.local", id),
            title = title,
            addedAt = Instant.EPOCH,
            metadataUpdatedAt = Instant.EPOCH,
            author = authors.firstOrNull(),
            authors = authors,
            remoteTags = remoteTags,
        ),
        libraryAddedAt = libraryAddedAt,
        rating = null,
        localTags = localTags,
        sourceAvailable = true,
        reconciliation = null,
        progress = progressAt?.let { capturedAt ->
            ReadingProgress(
                identity = BookIdentity("fixture.local", id),
                locator = ReaderLocator(
                    document = DocumentIdentity("fixture.local", id, "chapter"),
                    bookProgress = 0.5,
                    capturedAt = capturedAt,
                ),
            )
        },
    )

    private fun collection(
        id: String,
        title: String,
        kind: CollectionKind,
        parentId: String? = null,
        displayOrder: Long = 0,
    ): LibraryCollection = LibraryCollection(
        collectionId = id,
        kind = kind,
        title = title,
        parentCollectionId = parentId,
        displayOrder = displayOrder,
    )
}
