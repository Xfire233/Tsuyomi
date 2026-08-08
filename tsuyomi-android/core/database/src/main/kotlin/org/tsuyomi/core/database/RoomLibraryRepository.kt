/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.withTransaction
import java.time.Instant
import org.tsuyomi.core.database.room.BookEntity
import org.tsuyomi.core.database.room.CollectionEntity
import org.tsuyomi.core.database.room.LibraryDao
import org.tsuyomi.core.database.room.ManualCollectionMembershipEntity
import org.tsuyomi.core.database.room.ReadingProgressEntity
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity

/**
 * The only public persistence boundary for the initial library schema. It exposes stable identities
 * and semantic locators, never Room entities or storage row identifiers.
 */
class RoomLibraryRepository(private val database: TsuyomiDatabase) {
    private val dao: LibraryDao = database.libraryDao()

    suspend fun saveBook(book: LibraryBook) {
        val entity = book.toEntity()
        if (dao.insertBook(entity) == -1L) {
            dao.updateBookMetadata(
                sourceId = entity.sourceId,
                remoteBookId = entity.remoteBookId,
                title = entity.title,
                metadataUpdatedAtEpochSecond = entity.metadataUpdatedAtEpochSecond,
                metadataUpdatedAtNano = entity.metadataUpdatedAtNano,
            )
        }
    }

    suspend fun book(identity: BookIdentity): LibraryBook? =
        dao.book(identity.sourceId, identity.remoteBookId)?.toDomain()

    suspend fun createCollection(collection: LibraryCollection) {
        database.withTransaction {
            val parentId = collection.parentCollectionId
            require(parentId == null || dao.collection(parentId) != null) { "Unknown parent collection" }
            require(parentId == null || !wouldCreateCycle(collection.collectionId, parentId)) {
                "Collection hierarchy is cyclic or exceeds its depth bound"
            }
            dao.insertCollection(collection.toEntity())
        }
    }

    /** Changes only presentation hierarchy; it never changes collection membership semantics. */
    suspend fun updateCollectionPresentation(
        collectionId: String,
        parentCollectionId: String?,
        displayOrder: Long,
    ) {
        database.withTransaction {
            val collection = requireNotNull(dao.collection(collectionId)) { "Unknown collection" }
            require(parentCollectionId != collectionId) { "A collection cannot parent itself" }
            if (parentCollectionId != null) {
                require(dao.collection(parentCollectionId) != null) { "Unknown parent collection" }
                require(!wouldCreateCycle(collectionId, parentCollectionId)) { "Collection parent cycle" }
            }
            check(dao.updateCollectionPresentation(collection.collectionId, parentCollectionId, displayOrder) == 1)
        }
    }

    suspend fun addManualMembership(collectionId: String, identity: BookIdentity): Boolean {
        val collection = requireNotNull(dao.collection(collectionId)) { "Unknown collection" }
        require(collection.kind == CollectionKind.MANUAL) { "Only manual collections have stored membership" }
        require(dao.book(identity.sourceId, identity.remoteBookId) != null) { "Unknown book" }
        return dao.insertManualMembership(
            ManualCollectionMembershipEntity(collectionId, identity.sourceId, identity.remoteBookId),
        ) != -1L
    }

    suspend fun removeManualMembership(collectionId: String, identity: BookIdentity): Boolean =
        dao.deleteManualMembership(collectionId, identity.sourceId, identity.remoteBookId) != 0

    /**
     * Applies a semantic capture only when its [ReadingProgress.updatedAt] is strictly newer. A
     * timestamp tie deliberately retains the existing valid record; neither percentage nor offset
     * is used as a tie-breaker because a user may intentionally read backwards.
     */
    suspend fun saveProgress(incoming: ReadingProgress): ProgressWriteResult {
        require(incoming.isSemanticallyValid()) { "Invalid semantic progress" }
        val entity = incoming.toEntity()
        return database.withTransaction {
            when (val existing = dao.progress(entity.sourceId, entity.remoteBookId)) {
                null -> if (dao.insertProgressIfAbsent(entity) != -1L) {
                    ProgressWriteResult.APPLIED
                } else {
                    ProgressWriteResult.KEPT_EXISTING
                }

                else -> if (existing.toDomainOrNull() == null) {
                    check(dao.replaceProgress(entity) == 1)
                    ProgressWriteResult.APPLIED
                } else if (
                    dao.updateProgressIfNewer(
                        sourceId = entity.sourceId,
                        remoteBookId = entity.remoteBookId,
                        contentId = entity.contentId,
                        revision = entity.revision,
                        blockId = entity.blockId,
                        textAnchorDigest = entity.textAnchorDigest,
                        characterOffset = entity.characterOffset,
                        chapterProgress = entity.chapterProgress,
                        bookProgress = entity.bookProgress,
                        updatedAtEpochSecond = entity.updatedAtEpochSecond,
                        updatedAtNano = entity.updatedAtNano,
                    ) == 1
                ) {
                    ProgressWriteResult.APPLIED
                } else {
                    ProgressWriteResult.KEPT_EXISTING
                }
            }
        }
    }

    suspend fun progress(identity: BookIdentity): ReadingProgress? =
        dao.progress(identity.sourceId, identity.remoteBookId)?.toDomainOrNull()

    private suspend fun wouldCreateCycle(collectionId: String, prospectiveParentId: String): Boolean {
        var cursor: String? = prospectiveParentId
        var ancestorCount = 0
        while (cursor != null) {
            if (cursor == collectionId || ancestorCount >= MAX_COLLECTION_DEPTH - 1) return true
            cursor = dao.parentCollectionId(cursor)
            ancestorCount++
        }
        return false
    }

    private companion object {
        const val MAX_COLLECTION_DEPTH = 32
    }
}

private fun LibraryBook.toEntity() = BookEntity(
    sourceId = identity.sourceId,
    remoteBookId = identity.remoteBookId,
    title = title,
    addedAtEpochSecond = addedAt.epochSecond,
    addedAtNano = addedAt.nano,
    metadataUpdatedAtEpochSecond = metadataUpdatedAt.epochSecond,
    metadataUpdatedAtNano = metadataUpdatedAt.nano,
)

private fun BookEntity.toDomain() = LibraryBook(
    identity = BookIdentity(sourceId, remoteBookId),
    title = title,
    addedAt = Instant.ofEpochSecond(addedAtEpochSecond, addedAtNano.toLong()),
    metadataUpdatedAt = Instant.ofEpochSecond(metadataUpdatedAtEpochSecond, metadataUpdatedAtNano.toLong()),
)

private fun LibraryCollection.toEntity() = CollectionEntity(
    collectionId = collectionId,
    kind = kind,
    title = title,
    parentCollectionId = parentCollectionId,
    displayOrder = displayOrder,
)

private fun ReadingProgress.toEntity() = ReadingProgressEntity(
    sourceId = identity.sourceId,
    remoteBookId = identity.remoteBookId,
    contentId = locator.document.contentId,
    revision = locator.document.revision,
    blockId = locator.blockId,
    textAnchorDigest = locator.textAnchorDigest,
    characterOffset = locator.characterOffset,
    chapterProgress = locator.chapterProgress,
    bookProgress = locator.bookProgress,
    updatedAtEpochSecond = updatedAt.epochSecond,
    updatedAtNano = updatedAt.nano,
)


private fun ReadingProgressEntity.timestamp(): Instant =
    Instant.ofEpochSecond(updatedAtEpochSecond, updatedAtNano.toLong())

private fun ReadingProgressEntity.toDomainOrNull(): ReadingProgress? = runCatching {
    val timestamp = timestamp()
    ReadingProgress(
        identity = BookIdentity(sourceId, remoteBookId),
        locator = ReaderLocator(
            document = DocumentIdentity(sourceId, remoteBookId, contentId, revision),
            blockId = blockId,
            textAnchorDigest = textAnchorDigest,
            characterOffset = characterOffset,
            chapterProgress = chapterProgress,
            bookProgress = bookProgress,
            capturedAt = timestamp,
        ),
        updatedAt = timestamp,
    )
}.getOrNull()

private fun ReadingProgress.isSemanticallyValid(): Boolean = runCatching {
    val locator = locator
    val characterOffset = locator.characterOffset
    val chapterProgress = locator.chapterProgress
    val bookProgress = locator.bookProgress
    require(locator.document.sourceId == identity.sourceId)
    require(locator.document.remoteBookId == identity.remoteBookId)
    require(updatedAt == locator.capturedAt)
    require(characterOffset == null || characterOffset >= 0)
    require(chapterProgress == null || chapterProgress.isFinite() && chapterProgress in 0.0..1.0)
    require(bookProgress == null || bookProgress.isFinite() && bookProgress in 0.0..1.0)
    require(
        (locator.blockId != null && locator.characterOffset != null) ||
            (locator.blockId != null && locator.textAnchorDigest != null) ||
            locator.chapterProgress != null ||
            locator.bookProgress != null,
    )
}.isSuccess
