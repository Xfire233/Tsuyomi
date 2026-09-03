/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.room.Query
import androidx.room.Update

internal data class BookIdentityRow(
    @androidx.room.ColumnInfo(name = "source_id") val sourceId: String,
    @androidx.room.ColumnInfo(name = "remote_book_id") val remoteBookId: String,
)

@Dao
internal interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(entity: BookEntity): Long

    @Query(
        """
        UPDATE books
        SET title = :title,
            authors_json = :authorsJson,
            author_sort_key = :authorSortKey,
            cover_url = :coverUrl,
            canonical_url = :canonicalUrl,
            status = :status,
            remote_tags_json = :remoteTagsJson,
            source_update_key = :sourceUpdateKey,
            has_unread_update = :hasUnreadUpdate,
            metadata_updated_at_epoch_second = :metadataUpdatedAtEpochSecond,
            metadata_updated_at_nano = :metadataUpdatedAtNano
        WHERE source_id = :sourceId AND remote_book_id = :remoteBookId
        """,
    )
    suspend fun updateBookMetadata(
        sourceId: String,
        remoteBookId: String,
        title: String,
        authorsJson: String,
        authorSortKey: ByteArray?,
        coverUrl: String?,
        canonicalUrl: String?,
        status: String?,
        remoteTagsJson: String,
        sourceUpdateKey: String?,
        hasUnreadUpdate: Boolean,
        metadataUpdatedAtEpochSecond: Long,
        metadataUpdatedAtNano: Int,
    ): Int

    @Query("SELECT * FROM books WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun book(sourceId: String, remoteBookId: String): BookEntity?

    @Query("SELECT books.* FROM books INNER JOIN library_entries USING(source_id, remote_book_id) ORDER BY library_entries.display_order, library_entries.added_at_epoch_second DESC, books.title COLLATE NOCASE, books.source_id, books.remote_book_id")
    suspend fun libraryBooks(): List<BookEntity>

    @Query("SELECT * FROM books ORDER BY source_id, remote_book_id")
    suspend fun allBooks(): List<BookEntity>

    @Query("SELECT * FROM library_entries ORDER BY source_id, remote_book_id")
    suspend fun allLibraryEntries(): List<LibraryEntryEntity>

    @Query("SELECT * FROM collections ORDER BY parent_collection_id, display_order, collection_id")
    suspend fun allCollections(): List<CollectionEntity>

    @Query("SELECT source_id, remote_book_id FROM manual_collection_memberships WHERE collection_id = :collectionId ORDER BY display_order, source_id, remote_book_id")
    suspend fun manualCollectionIdentities(collectionId: String): List<BookIdentityRow>

    @RawQuery
    suspend fun smartCollectionIdentities(query: SupportSQLiteQuery): List<BookIdentityRow>

    @Query("SELECT * FROM manual_collection_memberships ORDER BY collection_id, display_order, source_id, remote_book_id")
    suspend fun allManualMemberships(): List<ManualCollectionMembershipEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLibraryEntry(entity: LibraryEntryEntity): Long

    @Query("SELECT * FROM library_entries WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun libraryEntry(sourceId: String, remoteBookId: String): LibraryEntryEntity?

    @Query("DELETE FROM library_entries WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun deleteLibraryEntry(sourceId: String, remoteBookId: String): Int

    @Query("UPDATE library_entries SET rating = :rating WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun updateRating(sourceId: String, remoteBookId: String, rating: Int?): Int

    @Query("UPDATE library_entries SET read_later = :readLater WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun updateReadLater(sourceId: String, remoteBookId: String, readLater: Boolean): Int

    @Query("UPDATE library_entries SET display_order = :displayOrder WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun updateLibraryDisplayOrder(sourceId: String, remoteBookId: String, displayOrder: Int): Int

    @Query("SELECT * FROM local_book_tags WHERE source_id = :sourceId AND remote_book_id = :remoteBookId ORDER BY normalized_tag")
    suspend fun localTags(sourceId: String, remoteBookId: String): List<LocalBookTagEntity>

    @Query("DELETE FROM local_book_tags WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun deleteLocalTags(sourceId: String, remoteBookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalTags(entities: List<LocalBookTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceAvailability(entity: SourceAvailabilityEntity)

    @Query("SELECT * FROM source_availability WHERE source_id = :sourceId")
    suspend fun sourceAvailability(sourceId: String): SourceAvailabilityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceRemotePolicy(entity: SourceRemotePolicyEntity)

    @Query("SELECT * FROM source_remote_policy WHERE source_id = :sourceId")
    suspend fun sourceRemotePolicy(sourceId: String): SourceRemotePolicyEntity?

    @Query("UPDATE source_remote_policy SET add_writeback_enabled = :enabled WHERE source_id = :sourceId AND capability_set_fingerprint = :capabilityFingerprint")
    suspend fun setAddWritebackEnabled(sourceId: String, capabilityFingerprint: String, enabled: Boolean): Int
    @Query("UPDATE source_remote_policy SET add_writeback_enabled = 0 WHERE add_writeback_enabled = 1")
    suspend fun disableAllAddWriteback(): Int
    @Query("UPDATE source_remote_policy SET first_import_prompt_dismissed = 1 WHERE source_id = :sourceId AND capability_set_fingerprint = :capabilityFingerprint AND first_import_prompt_dismissed = 0")
    suspend fun dismissFirstImportPrompt(sourceId: String, capabilityFingerprint: String): Int


    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReconciliation(entity: RemoteLibraryReconciliationEntity)

    @Query("SELECT * FROM remote_library_reconciliation WHERE source_id = :sourceId AND remote_book_id = :remoteBookId AND state IN ('PENDING_USER_ACTION','IN_FLIGHT') LIMIT 1")
    suspend fun activeReconciliation(sourceId: String, remoteBookId: String): RemoteLibraryReconciliationEntity?

    @Query("SELECT * FROM remote_library_reconciliation WHERE source_id = :sourceId AND remote_book_id = :remoteBookId ORDER BY rowid DESC LIMIT 1")
    suspend fun latestReconciliation(sourceId: String, remoteBookId: String): RemoteLibraryReconciliationEntity?

    @Query("UPDATE remote_library_reconciliation SET state = :nextState, updated_at_epoch_second = :updatedAt, diagnostic_id = :diagnosticId WHERE id = :id AND state = :expectedState")
    suspend fun transitionReconciliation(id: String, expectedState: String, nextState: String, updatedAt: Long, diagnosticId: String?): Int

    @Query("SELECT * FROM remote_library_reconciliation WHERE id = :id")
    suspend fun reconciliation(id: String): RemoteLibraryReconciliationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(entity: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollectionIfAbsent(entity: CollectionEntity): Long

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
    @Query("SELECT * FROM collections WHERE parent_collection_id IS :parentCollectionId ORDER BY display_order, collection_id")
    suspend fun collectionSiblings(parentCollectionId: String?): List<CollectionEntity>

    @Query("UPDATE collections SET parent_collection_id = :parentCollectionId WHERE parent_collection_id = :collectionId")
    suspend fun reparentChildren(collectionId: String, parentCollectionId: String?): Int

    @Query("UPDATE collections SET display_order = :displayOrder WHERE collection_id = :collectionId")
    suspend fun updateCollectionDisplayOrder(collectionId: String, displayOrder: Long): Int
    @Query("UPDATE collections SET title = :title, updated_at_epoch_second = :updatedAtEpochSecond, updated_at_nano = :updatedAtNano WHERE collection_id = :collectionId")
    suspend fun renameCollection(collectionId: String, title: String, updatedAtEpochSecond: Long, updatedAtNano: Int): Int

    @Query("DELETE FROM collections WHERE collection_id = :collectionId")
    suspend fun deleteCollection(collectionId: String): Int



    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertManualMembership(entity: ManualCollectionMembershipEntity): Long

    @Query(
        """
        DELETE FROM manual_collection_memberships
        WHERE collection_id = :collectionId AND source_id = :sourceId AND remote_book_id = :remoteBookId
        """,
    )
    suspend fun deleteManualMembership(collectionId: String, sourceId: String, remoteBookId: String): Int

    @Query("UPDATE manual_collection_memberships SET display_order = :displayOrder WHERE collection_id = :collectionId AND source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun updateManualMembershipDisplayOrder(
        collectionId: String,
        sourceId: String,
        remoteBookId: String,
        displayOrder: Long,
    ): Int
    @Query("SELECT * FROM smart_rules WHERE collection_id = :collectionId")
    suspend fun smartRule(collectionId: String): SmartRuleEntity?


    @Query("SELECT * FROM manual_collection_memberships WHERE collection_id = :collectionId ORDER BY display_order, source_id, remote_book_id")
    suspend fun manualMemberships(collectionId: String): List<ManualCollectionMembershipEntity>

    @Query("SELECT COALESCE(MAX(display_order), -1) + 1 FROM manual_collection_memberships WHERE collection_id = :collectionId")
    suspend fun nextManualMembershipOrder(collectionId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSmartRule(entity: SmartRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscriptionDraft(entity: SubscriptionDraftEntity)
    @Query("SELECT * FROM subscription_drafts WHERE collection_id = :collectionId")
    suspend fun subscriptionDraft(collectionId: String): SubscriptionDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(entity: SearchHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBrowsingHistory(entity: BrowsingHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImportSession(entity: ImportSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportWarnings(entities: List<ImportWarningEntity>)

    @Query("SELECT * FROM import_sessions WHERE status IN ('PREPARED','ROOM_APPLIED','PREFERENCES_APPLIED','ABORTED_CLEANUP_PENDING') ORDER BY started_at_epoch_second LIMIT 1")
    suspend fun pendingImportSession(): ImportSessionEntity?

    @Query("SELECT * FROM import_sessions WHERE id = :id")
    suspend fun importSession(id: String): ImportSessionEntity?

    @Query("SELECT * FROM import_sessions ORDER BY started_at_epoch_second DESC LIMIT 1")
    suspend fun latestImportSession(): ImportSessionEntity?

    @Query("SELECT * FROM import_warnings WHERE session_id = :sessionId ORDER BY ordinal")
    suspend fun importWarnings(sessionId: String): List<ImportWarningEntity>

    @Query("UPDATE import_sessions SET status = :nextStatus, completed_at_epoch_second = :completedAt, summary_json = COALESCE(:summaryJson, summary_json) WHERE id = :id AND plan_digest = :digest AND status = :expectedStatus")
    suspend fun transitionImportSession(id: String, digest: String, expectedStatus: String, nextStatus: String, completedAt: Long?, summaryJson: String?): Int

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
