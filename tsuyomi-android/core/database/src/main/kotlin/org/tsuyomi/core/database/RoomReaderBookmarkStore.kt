/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.withTransaction
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.tsuyomi.core.database.room.LibraryDao
import org.tsuyomi.core.database.room.ReaderBookmarkEntity
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.MAX_READER_BOOKMARKS_PER_BOOK
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.locator.bookmarkPositionKey
import org.tsuyomi.shared.model.BookIdentity

internal class RoomReaderBookmarkStore(
    private val database: TsuyomiDatabase,
    private val dao: LibraryDao,
) {
    fun observeBookmarks(identity: BookIdentity): Flow<List<ReaderLocator>> =
        database.invalidationTracker.createFlow("reader_bookmarks")
            .map { bookmarks(identity) }

    suspend fun bookmarks(identity: BookIdentity): List<ReaderLocator> = database.withTransaction {
        val result = ArrayList<ReaderLocator>()
        var page = dao.firstBookmarkPage(identity.sourceId, identity.remoteBookId, 128)
        while (page.isNotEmpty()) {
            page.forEach { result.add(it.toLocator(identity)) }
            page = dao.bookmarkPageAfter(identity.sourceId, identity.remoteBookId, page.last().bookmarkPositionKey, 128)
        }
        result
    }

    /** Returns whether [locator]'s semantic position is bookmarked after the atomic toggle. */
    suspend fun toggleBookmark(locator: ReaderLocator): Boolean {
        val identity = locator.document.book
        return database.withTransaction {
            if (dao.deleteReaderBookmark(identity.sourceId, identity.remoteBookId, locator.bookmarkPositionKey()) != 0) {
                false
            } else {
                require(dao.readerBookmarkCount(identity.sourceId, identity.remoteBookId) < MAX_READER_BOOKMARKS_PER_BOOK) {
                    "Too many bookmarks"
                }
                check(dao.insertReaderBookmark(locator.toEntity()) != -1L)
                true
            }
        }
    }

    /** Removes one semantic position without allowing a stale repeat action to re-add it. */
    suspend fun removeBookmark(locator: ReaderLocator): Boolean {
        val identity = locator.document.book
        return database.withTransaction {
            dao.deleteReaderBookmark(identity.sourceId, identity.remoteBookId, locator.bookmarkPositionKey()) != 0
        }
    }

    /** Adds the union of [locators] and existing positions, rolling every insertion back on overflow. */
    suspend fun addBookmarks(identity: BookIdentity, locators: List<ReaderLocator>) {
        require(locators.size <= MAX_READER_BOOKMARKS_PER_BOOK) { "Too many bookmarks" }
        locators.forEach { locator ->
            require(locator.document.sourceId == identity.sourceId && locator.document.remoteBookId == identity.remoteBookId) {
                "Bookmark document must belong to its book"
            }
        }
        database.withTransaction {
            locators.forEach { locator -> dao.insertReaderBookmark(locator.toEntity()) }
            require(dao.readerBookmarkCount(identity.sourceId, identity.remoteBookId) <= MAX_READER_BOOKMARKS_PER_BOOK) {
                "Too many bookmarks"
            }
        }
    }
}

private fun ReaderBookmarkEntity.toLocator(identity: BookIdentity): ReaderLocator = ReaderLocator(
    document = DocumentIdentity(identity.sourceId, identity.remoteBookId, contentId, revision),
    blockId = blockId,
    textAnchorDigest = textAnchorDigest,
    characterOffset = characterOffset,
    chapterProgress = chapterProgress,
    bookProgress = bookProgress,
    capturedAt = Instant.ofEpochSecond(capturedAtEpochSecond, capturedAtNano.toLong()),
).also { locator ->
    check(locator.bookmarkPositionKey() == bookmarkPositionKey) { "Invalid persisted bookmark key" }
}

private fun ReaderLocator.toEntity(): ReaderBookmarkEntity = ReaderBookmarkEntity(
    sourceId = document.sourceId,
    remoteBookId = document.remoteBookId,
    bookmarkPositionKey = bookmarkPositionKey(),
    contentId = document.contentId,
    revision = document.revision,
    blockId = blockId,
    textAnchorDigest = textAnchorDigest,
    characterOffset = characterOffset,
    chapterProgress = chapterProgress,
    bookProgress = bookProgress,
    capturedAtEpochSecond = capturedAt.epochSecond,
    capturedAtNano = capturedAt.nano,
)
