/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
internal interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(entity: BookEntity): Long

    @Query(
        """
        UPDATE books
        SET title = :title,
            metadata_updated_at_epoch_second = :metadataUpdatedAtEpochSecond,
            metadata_updated_at_nano = :metadataUpdatedAtNano
        WHERE source_id = :sourceId AND remote_book_id = :remoteBookId
        """,
    )
    suspend fun updateBookMetadata(
        sourceId: String,
        remoteBookId: String,
        title: String,
        metadataUpdatedAtEpochSecond: Long,
        metadataUpdatedAtNano: Int,
    ): Int

    @Query("SELECT * FROM books WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun book(sourceId: String, remoteBookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(entity: CollectionEntity)

    @Query("SELECT * FROM collections WHERE collection_id = :collectionId")
    suspend fun collection(collectionId: String): CollectionEntity?

    @Query("SELECT parent_collection_id FROM collections WHERE collection_id = :collectionId")
    suspend fun parentCollectionId(collectionId: String): String?

    @Query(
        """
        UPDATE collections
        SET parent_collection_id = :parentCollectionId,
            display_order = :displayOrder
        WHERE collection_id = :collectionId
        """,
    )
    suspend fun updateCollectionPresentation(
        collectionId: String,
        parentCollectionId: String?,
        displayOrder: Long,
    ): Int


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertManualMembership(entity: ManualCollectionMembershipEntity): Long

    @Query(
        """
        DELETE FROM manual_collection_memberships
        WHERE collection_id = :collectionId AND source_id = :sourceId AND remote_book_id = :remoteBookId
        """,
    )
    suspend fun deleteManualMembership(collectionId: String, sourceId: String, remoteBookId: String): Int

    @Query("SELECT * FROM reading_progress WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun progress(sourceId: String, remoteBookId: String): ReadingProgressEntity?

    /**
     * This comparison is deliberately timestamp-only: backwards reading is valid progress. The
     * strict comparison also preserves the existing valid row when timestamps are equal.
     */
    @Query(
        """
        UPDATE reading_progress
        SET content_id = :contentId,
            revision = :revision,
            block_id = :blockId,
            text_anchor_digest = :textAnchorDigest,
            character_offset = :characterOffset,
            chapter_progress = :chapterProgress,
            book_progress = :bookProgress,
            updated_at_epoch_second = :updatedAtEpochSecond,
            updated_at_nano = :updatedAtNano
        WHERE source_id = :sourceId AND remote_book_id = :remoteBookId
          AND (
            updated_at_epoch_second < :updatedAtEpochSecond
            OR (updated_at_epoch_second = :updatedAtEpochSecond AND updated_at_nano < :updatedAtNano)
          )
        """,
    )
    suspend fun updateProgressIfNewer(
        sourceId: String,
        remoteBookId: String,
        contentId: String,
        revision: String?,
        blockId: String?,
        textAnchorDigest: String?,
        characterOffset: Int?,
        chapterProgress: Double?,
        bookProgress: Double?,
        updatedAtEpochSecond: Long,
        updatedAtNano: Int,
    ): Int

    @Update
    suspend fun replaceProgress(entity: ReadingProgressEntity): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgressIfAbsent(entity: ReadingProgressEntity): Long
}
