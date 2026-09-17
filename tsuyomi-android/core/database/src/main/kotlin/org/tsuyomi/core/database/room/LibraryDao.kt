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
import kotlinx.coroutines.flow.Flow

internal data class BookIdentityRow(
    @androidx.room.ColumnInfo(name = "source_id") val sourceId: String,
    @androidx.room.ColumnInfo(name = "remote_book_id") val remoteBookId: String,
)

/** A durable Reader visit, or a legacy semantic-progress fallback when [explicitVisit] is false. */
internal data class ReaderHistoryRow(
    @androidx.room.ColumnInfo(name = "source_id") val sourceId: String,
    @androidx.room.ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    @androidx.room.ColumnInfo(name = "visited_at_epoch_second") val visitedAtEpochSecond: Long,
    @androidx.room.ColumnInfo(name = "visited_at_nano") val visitedAtNano: Int,
    @androidx.room.ColumnInfo(name = "explicit_visit") val explicitVisit: Boolean,
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
            source_update_key = NULL,
            has_unread_update = 0,
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
        metadataUpdatedAtEpochSecond: Long,
        metadataUpdatedAtNano: Int,
    ): Int

    @Query("SELECT * FROM books WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun book(sourceId: String, remoteBookId: String): BookEntity?

    @Query("SELECT books.* FROM books INNER JOIN library_entries USING(source_id, remote_book_id) WHERE library_entries.local_pin = 1 ORDER BY library_entries.display_order, library_entries.added_at_epoch_second DESC, books.title COLLATE NOCASE, books.source_id, books.remote_book_id")
    suspend fun libraryBooks(): List<BookEntity>

    @Query("SELECT books.* FROM books INNER JOIN library_entries USING(source_id, remote_book_id) WHERE library_entries.read_later = 1 ORDER BY library_entries.display_order, library_entries.added_at_epoch_second DESC, books.title COLLATE NOCASE, books.source_id, books.remote_book_id")
    suspend fun readLaterBooks(): List<BookEntity>

    @Query("SELECT * FROM books ORDER BY source_id, remote_book_id")
    suspend fun allBooks(): List<BookEntity>

    @Query("SELECT * FROM library_entries ORDER BY source_id, remote_book_id")
    suspend fun allLibraryEntries(): List<LibraryEntryEntity>

    @Query("SELECT * FROM collections ORDER BY parent_collection_id, display_order, collection_id")
    suspend fun allCollections(): List<CollectionEntity>

    @Query("SELECT m.source_id, m.remote_book_id FROM manual_collection_memberships m INNER JOIN library_entries le ON le.source_id = m.source_id AND le.remote_book_id = m.remote_book_id WHERE m.collection_id = :collectionId AND le.local_pin = 1 ORDER BY m.display_order, m.source_id, m.remote_book_id")
    suspend fun manualCollectionIdentities(collectionId: String): List<BookIdentityRow>

    @Query("SELECT m.collection_id FROM manual_collection_memberships m INNER JOIN library_entries le ON le.source_id = m.source_id AND le.remote_book_id = m.remote_book_id WHERE m.source_id = :sourceId AND m.remote_book_id = :remoteBookId AND le.local_pin = 1 ORDER BY m.collection_id")
    suspend fun manualCollectionIds(sourceId: String, remoteBookId: String): List<String>

    @RawQuery
    suspend fun smartCollectionIdentities(query: SupportSQLiteQuery): List<BookIdentityRow>

    @Query("SELECT * FROM manual_collection_memberships ORDER BY collection_id, display_order, source_id, remote_book_id")
    suspend fun allManualMemberships(): List<ManualCollectionMembershipEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLibraryEntry(entity: LibraryEntryEntity): Long

    @Query("SELECT * FROM library_entries WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun libraryEntry(sourceId: String, remoteBookId: String): LibraryEntryEntity?

    @Query("UPDATE library_entries SET local_pin = 1 WHERE source_id = :sourceId AND remote_book_id = :remoteBookId AND local_pin = 0")
    suspend fun pinLibraryEntry(sourceId: String, remoteBookId: String): Int

    @Query("UPDATE library_entries SET local_pin = 0 WHERE source_id = :sourceId AND remote_book_id = :remoteBookId AND local_pin = 1")
    suspend fun unpinLibraryEntry(sourceId: String, remoteBookId: String): Int

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

    @Query("UPDATE source_availability SET available = 0, generation = generation + 1 WHERE available = 1 AND source_id NOT IN (:installedSourceIds)")
    suspend fun markMissingSourcesUnavailable(installedSourceIds: List<String>): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceRemotePolicy(entity: SourceRemotePolicyEntity)

    @Query("SELECT * FROM source_remote_policy WHERE source_id = :sourceId")
    suspend fun sourceRemotePolicy(sourceId: String): SourceRemotePolicyEntity?

    @Query("UPDATE source_remote_policy SET add_writeback_enabled = :enabled WHERE source_id = :sourceId AND capability_set_fingerprint = :capabilityFingerprint")
    suspend fun setAddWritebackEnabled(sourceId: String, capabilityFingerprint: String, enabled: Boolean): Int
    @Query("UPDATE source_remote_policy SET remove_writeback_enabled = :enabled WHERE source_id = :sourceId AND capability_set_fingerprint = :capabilityFingerprint")
    suspend fun setRemoveWritebackEnabled(sourceId: String, capabilityFingerprint: String, enabled: Boolean): Int
    @Query("UPDATE source_remote_policy SET move_writeback_enabled = :enabled WHERE source_id = :sourceId AND capability_set_fingerprint = :capabilityFingerprint")
    suspend fun setMoveWritebackEnabled(sourceId: String, capabilityFingerprint: String, enabled: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRemoteMirrorBinding(entity: RemoteMirrorBindingEntity)

    @Query("SELECT * FROM remote_mirror_bindings ORDER BY source_id")
    suspend fun remoteMirrorBindings(): List<RemoteMirrorBindingEntity>

    @Query("SELECT * FROM remote_mirror_bindings WHERE source_id = :sourceId")
    suspend fun remoteMirrorBinding(sourceId: String): RemoteMirrorBindingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRemoteMirrorTargets(entities: List<RemoteMirrorTargetEntity>)

    @Query("UPDATE remote_mirror_targets SET frozen = 1 WHERE source_id = :sourceId")
    suspend fun freezeRemoteMirrorTargets(sourceId: String)

    @Query("SELECT * FROM remote_mirror_targets WHERE source_id = :sourceId ORDER BY frozen, target_id")
    suspend fun remoteMirrorTargets(sourceId: String): List<RemoteMirrorTargetEntity>

    @Query("DELETE FROM remote_mirror_items WHERE source_id = :sourceId")
    suspend fun deleteRemoteMirrorItems(sourceId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRemoteMirrorItems(entities: List<RemoteMirrorItemEntity>)

    @Query("SELECT b.*, i.target_id AS mirror_target_id FROM books b INNER JOIN remote_mirror_items i ON i.source_id = b.source_id AND i.remote_book_id = b.remote_book_id WHERE i.source_id = :sourceId ORDER BY b.title COLLATE NOCASE, b.remote_book_id")
    suspend fun remoteMirrorBooks(sourceId: String): List<RemoteMirrorBookRow>

    @Query("UPDATE remote_mirror_items SET target_id = :targetId, updated_at_epoch_second = :updatedAt WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun updateRemoteMirrorBookTarget(sourceId: String, remoteBookId: String, targetId: String?, updatedAt: Long): Int

    @Query("DELETE FROM remote_mirror_items WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun deleteRemoteMirrorBook(sourceId: String, remoteBookId: String): Int

    @Query("SELECT COUNT(*) FROM remote_mirror_items WHERE source_id = :sourceId AND target_id = :targetId")
    suspend fun remoteMirrorTargetCount(sourceId: String, targetId: String): Int
    @Query("UPDATE source_remote_policy SET add_writeback_enabled = 0 WHERE add_writeback_enabled = 1")
    suspend fun disableAllAddWriteback(): Int
    @Query("UPDATE source_remote_policy SET first_import_prompt_dismissed = 1 WHERE source_id = :sourceId AND capability_set_fingerprint = :capabilityFingerprint AND first_import_prompt_dismissed = 0")
    suspend fun dismissFirstImportPrompt(sourceId: String, capabilityFingerprint: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReconciliation(entity: RemoteLibraryReconciliationEntity)

    @Query("SELECT * FROM remote_library_reconciliation WHERE source_id = :sourceId AND remote_book_id = :remoteBookId AND state IN ('PENDING_USER_ACTION','IN_FLIGHT','UNRESOLVED') ORDER BY rowid DESC LIMIT 1")
    suspend fun activeReconciliation(sourceId: String, remoteBookId: String): RemoteLibraryReconciliationEntity?

    @Query("SELECT * FROM remote_library_reconciliation WHERE source_id = :sourceId AND remote_book_id = :remoteBookId ORDER BY rowid DESC LIMIT 1")
    suspend fun latestReconciliation(sourceId: String, remoteBookId: String): RemoteLibraryReconciliationEntity?

    @Query("UPDATE remote_library_reconciliation SET state = :nextState, updated_at_epoch_second = :updatedAt, diagnostic_id = :diagnosticId WHERE id = :id AND state = :expectedState")
    suspend fun transitionReconciliation(id: String, expectedState: String, nextState: String, updatedAt: Long, diagnosticId: String?): Int

    @Query("UPDATE remote_library_reconciliation SET state = 'CONFIRMED', updated_at_epoch_second = :updatedAt, diagnostic_id = NULL WHERE source_id = :sourceId AND remote_book_id = :remoteBookId AND UPPER(operation) = UPPER(:operation) AND state = 'UNRESOLVED'")
    suspend fun confirmUnresolvedMutations(sourceId: String, remoteBookId: String, operation: String, updatedAt: Long): Int

    @Query("UPDATE remote_library_reconciliation SET state = 'CANCELLED', updated_at_epoch_second = :updatedAt, diagnostic_id = NULL WHERE source_id = :sourceId AND remote_book_id = :remoteBookId AND UPPER(operation) = UPPER(:operation) AND state = 'UNRESOLVED'")
    suspend fun cancelUnresolvedMutations(sourceId: String, remoteBookId: String, operation: String, updatedAt: Long): Int

    @Query("SELECT * FROM remote_library_reconciliation WHERE id = :id")
    suspend fun reconciliation(id: String): RemoteLibraryReconciliationEntity?

    @Query("SELECT * FROM remote_library_reconciliation WHERE state = 'UNRESOLVED'")
    suspend fun unresolvedReconciliations(): List<RemoteLibraryReconciliationEntity>

    @Query("SELECT * FROM remote_library_reconciliation WHERE source_id = :sourceId AND state = 'UNRESOLVED'")
    suspend fun unresolvedReconciliationsForSource(sourceId: String): List<RemoteLibraryReconciliationEntity>

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

    @Query(
        """
        DELETE FROM manual_collection_memberships
        WHERE source_id = :sourceId AND remote_book_id = :remoteBookId
        """,
    )
    suspend fun deleteManualMembershipsForLibraryEntry(sourceId: String, remoteBookId: String): Int

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReaderHistoryIfAbsent(entity: ReaderHistoryEntity): Long

    @Query("SELECT * FROM reader_history WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun readerHistory(sourceId: String, remoteBookId: String): ReaderHistoryEntity?

    /** Keeps a visit's order monotonic if two admissions race. */
    @Query(
        """
        UPDATE reader_history
        SET last_visited_at_epoch_second = :visitedAtEpochSecond,
            last_visited_at_nano = :visitedAtNano
        WHERE source_id = :sourceId AND remote_book_id = :remoteBookId
          AND (
            last_visited_at_epoch_second < :visitedAtEpochSecond
            OR (last_visited_at_epoch_second = :visitedAtEpochSecond AND last_visited_at_nano < :visitedAtNano)
          )
        """,
    )
    suspend fun updateReaderHistoryIfNewer(
        sourceId: String,
        remoteBookId: String,
        visitedAtEpochSecond: Long,
        visitedAtNano: Int,
    ): Int

    /**
     * Explicit Reader visits win. Existing semantic progress is an honest pre-history fallback;
     * it is validated at the domain boundary before projection.
     */
    @Query(
        """
        SELECT source_id, remote_book_id,
            last_visited_at_epoch_second AS visited_at_epoch_second,
            last_visited_at_nano AS visited_at_nano,
            1 AS explicit_visit
        FROM reader_history
        UNION ALL
        SELECT p.source_id, p.remote_book_id,
            p.updated_at_epoch_second AS visited_at_epoch_second,
            p.updated_at_nano AS visited_at_nano,
            0 AS explicit_visit
        FROM reading_progress p
        WHERE NOT EXISTS (
            SELECT 1 FROM reader_history h
            WHERE h.source_id = p.source_id AND h.remote_book_id = p.remote_book_id
        )
        ORDER BY visited_at_epoch_second DESC, visited_at_nano DESC, source_id, remote_book_id
        """,
    )
    suspend fun readerHistoryRows(): List<ReaderHistoryRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletedChapter(entity: CompletedChapterEntity): Long

    @Query("SELECT chapter_id FROM completed_chapters WHERE source_id = :sourceId AND remote_book_id = :remoteBookId ORDER BY completed_at_epoch_second, completed_at_nano, chapter_id")
    suspend fun completedChapterIds(sourceId: String, remoteBookId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReaderBookmark(entity: ReaderBookmarkEntity): Long

    @Query("DELETE FROM reader_bookmarks WHERE source_id = :sourceId AND remote_book_id = :remoteBookId AND bookmark_position_key = :positionKey")
    suspend fun deleteReaderBookmark(sourceId: String, remoteBookId: String, positionKey: String): Int

    @Query("SELECT * FROM reader_bookmarks WHERE source_id = :sourceId AND remote_book_id = :remoteBookId ORDER BY bookmark_position_key LIMIT :limit")
    suspend fun firstBookmarkPage(sourceId: String, remoteBookId: String, limit: Int): List<ReaderBookmarkEntity>

    @Query("SELECT COUNT(*) FROM reader_bookmarks WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun readerBookmarkCount(sourceId: String, remoteBookId: String): Int

    @Query("SELECT * FROM reader_bookmarks WHERE source_id = :sourceId AND remote_book_id = :remoteBookId AND bookmark_position_key > :afterPositionKey ORDER BY bookmark_position_key LIMIT :limit")
    suspend fun bookmarkPageAfter(sourceId: String, remoteBookId: String, afterPositionKey: String, limit: Int): List<ReaderBookmarkEntity>

    @Query("SELECT DISTINCT source_id, remote_book_id FROM reader_bookmarks")
    suspend fun readerBookmarkIdentities(): List<BookIdentityRow>
}
