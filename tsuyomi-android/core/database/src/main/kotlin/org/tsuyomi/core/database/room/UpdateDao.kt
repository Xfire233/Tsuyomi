/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

internal data class UpdateSessionCounts(
    val total: Int,
    val completed: Int,
    val updated: Int,
    val failed: Int,
    val unavailable: Int,
    val skipped: Int,
    val cancelled: Int,
)

private const val LATEST_UPDATE_SESSION_ID = "SELECT session_id FROM update_sessions " +
    "ORDER BY CASE WHEN state IN ('RUNNING', 'QUEUED') THEN 0 ELSE 1 END, " +
    "COALESCE(finished_at_millis, started_at_millis) DESC, rowid DESC LIMIT 1"

@Dao
internal interface UpdateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(entity: UpdateSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionItems(entities: List<UpdateSessionItemEntity>)

    @Query("SELECT * FROM update_sessions WHERE session_id = :id")
    suspend fun session(id: String): UpdateSessionEntity?

    @Query("SELECT * FROM update_sessions WHERE state = 'RUNNING' AND cancellation_requested = 0 AND lease_expires_at_millis > :now ORDER BY started_at_millis LIMIT 1")
    suspend fun activeSession(now: Long): UpdateSessionEntity?

    @Query("SELECT * FROM update_sessions WHERE state = 'QUEUED' ORDER BY started_at_millis LIMIT 1")
    suspend fun queuedSession(): UpdateSessionEntity?

    @Query("UPDATE update_sessions SET state = 'QUEUED', lease_expires_at_millis = NULL, lease_owner_token = NULL WHERE state = 'RUNNING' AND (lease_expires_at_millis IS NULL OR lease_expires_at_millis <= :now)")
    suspend fun queueExpiredSessions(now: Long): Int

    @Query("UPDATE update_sessions SET state = 'RUNNING', lease_expires_at_millis = :leaseExpiresAt, lease_owner_token = :ownerToken WHERE session_id = :sessionId AND state = 'QUEUED'")
    suspend fun claimQueuedSession(sessionId: String, ownerToken: String, leaseExpiresAt: Long): Int

    @Query("UPDATE update_sessions SET lease_expires_at_millis = :leaseExpiresAt WHERE session_id = :sessionId AND state = 'RUNNING' AND cancellation_requested = 0 AND lease_owner_token = :ownerToken AND lease_expires_at_millis > :now")
    suspend fun renewLease(sessionId: String, ownerToken: String, now: Long, leaseExpiresAt: Long): Int

    @Query("SELECT * FROM update_session_items WHERE session_id = :sessionId AND state = 'PENDING' ORDER BY source_id, remote_book_id LIMIT :limit")
    suspend fun pendingItems(sessionId: String, limit: Int): List<UpdateSessionItemEntity>

    @Query("SELECT COUNT(*) FROM update_session_items WHERE session_id = :sessionId AND source_id = :sourceId AND remote_book_id = :remoteBookId AND state = 'PENDING'")
    suspend fun pendingItemCount(sessionId: String, sourceId: String, remoteBookId: String): Int

    @Query("SELECT COUNT(*) FROM update_book_exclusions WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun bookExclusionCount(sourceId: String, remoteBookId: String): Int

    @Query("SELECT COUNT(*) FROM update_source_exclusions WHERE source_id = :sourceId")
    suspend fun sourceExclusionCount(sourceId: String): Int

    @Query("UPDATE update_session_items SET state = :state, reason = :reason, anchor = :anchor WHERE session_id = :sessionId AND source_id = :sourceId AND remote_book_id = :remoteBookId AND state = 'PENDING'")
    suspend fun finishPendingItem(
        sessionId: String,
        sourceId: String,
        remoteBookId: String,
        state: String,
        reason: String?,
        anchor: String?,
    ): Int

    @Query("UPDATE update_session_items SET state = 'CANCELLED', reason = 'cancelled' WHERE session_id = :sessionId AND state = 'PENDING'")
    suspend fun cancelPendingItems(sessionId: String): Int

    @Query(
        """
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN state <> 'PENDING' THEN 1 ELSE 0 END), 0) AS completed,
               COALESCE(SUM(CASE WHEN state = 'UPDATED' THEN 1 ELSE 0 END), 0) AS updated,
               COALESCE(SUM(CASE WHEN state = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed,
               COALESCE(SUM(CASE WHEN state = 'UNAVAILABLE' THEN 1 ELSE 0 END), 0) AS unavailable,
               COALESCE(SUM(CASE WHEN state = 'SKIPPED' THEN 1 ELSE 0 END), 0) AS skipped,
               COALESCE(SUM(CASE WHEN state = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled
        FROM update_session_items
        WHERE session_id = :sessionId
        """,
    )
    suspend fun sessionCounts(sessionId: String): UpdateSessionCounts

    @Query("UPDATE update_sessions SET state = :state, completed = :completed, updated = :updated, failed = :failed, reason = :reason, lease_expires_at_millis = :leaseExpiresAt, lease_owner_token = :ownerToken, finished_at_millis = :finishedAt WHERE session_id = :sessionId AND state = 'RUNNING' AND lease_owner_token = :expectedOwnerToken")
    suspend fun updateRunningSession(
        sessionId: String,
        expectedOwnerToken: String,
        state: String,
        completed: Int,
        updated: Int,
        failed: Int,
        reason: String?,
        leaseExpiresAt: Long?,
        ownerToken: String?,
        finishedAt: Long?,
    ): Int

    @Query("UPDATE update_sessions SET state = 'CANCELLED', cancellation_requested = 1, completed = :completed, updated = :updated, failed = :failed, reason = :reason, lease_expires_at_millis = NULL, lease_owner_token = NULL, finished_at_millis = :now WHERE session_id = :sessionId AND state = 'RUNNING' AND cancellation_requested = 0 AND lease_owner_token = :ownerToken AND lease_expires_at_millis > :now")
    suspend fun cancelFencedSession(
        sessionId: String,
        ownerToken: String,
        now: Long,
        completed: Int,
        updated: Int,
        failed: Int,
        reason: String?,
    ): Int
    @Query("UPDATE update_sessions SET state = 'QUEUED', lease_expires_at_millis = NULL, lease_owner_token = NULL WHERE session_id = :sessionId AND state = 'RUNNING' AND cancellation_requested = 0 AND lease_owner_token = :ownerToken")
    suspend fun relinquishFencedSession(sessionId: String, ownerToken: String): Int


    @Query("UPDATE update_sessions SET state = 'CANCELLED', cancellation_requested = 1, completed = :completed, updated = :updated, failed = :failed, reason = :reason, lease_expires_at_millis = NULL, lease_owner_token = NULL, finished_at_millis = :now WHERE session_id = :sessionId AND state IN ('RUNNING', 'QUEUED') AND cancellation_requested = 0 AND (state = 'QUEUED' OR lease_expires_at_millis > :now)")
    suspend fun cancelSessionById(
        sessionId: String,
        now: Long,
        completed: Int,
        updated: Int,
        failed: Int,
        reason: String?,
    ): Int

    @Query("SELECT EXISTS(SELECT 1 FROM library_entries WHERE source_id = :sourceId AND remote_book_id = :remoteBookId) OR EXISTS(SELECT 1 FROM remote_mirror_items i INNER JOIN remote_mirror_bindings b ON b.source_id = i.source_id WHERE i.source_id = :sourceId AND i.remote_book_id = :remoteBookId AND b.frozen = 0)")
    suspend fun isBookInUpdateScope(sourceId: String, remoteBookId: String): Boolean

    @Query("SELECT * FROM update_baselines WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun baseline(sourceId: String, remoteBookId: String): UpdateBaselineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBaseline(entity: UpdateBaselineEntity)

    @Query("SELECT * FROM unresolved_updates WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun unresolved(sourceId: String, remoteBookId: String): UnresolvedUpdateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUnresolved(entity: UnresolvedUpdateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUnresolved(entity: UnresolvedUpdateEntity): Long

    @Query("DELETE FROM unresolved_updates WHERE source_id = :sourceId AND remote_book_id = :remoteBookId AND anchor = :anchor AND revision = :revision")
    suspend fun deleteUnresolved(sourceId: String, remoteBookId: String, anchor: String, revision: Long): Int

    @Query("SELECT chapter_id FROM completed_chapters WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun completedChapterIds(sourceId: String, remoteBookId: String): List<String>

    @Query("SELECT * FROM update_policy WHERE id = 'default'")
    suspend fun policy(): UpdatePolicyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPolicy(entity: UpdatePolicyEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookExclusion(entity: UpdateBookExclusionEntity): Long

    @Query("DELETE FROM update_book_exclusions WHERE source_id = :sourceId AND remote_book_id = :remoteBookId")
    suspend fun deleteBookExclusion(sourceId: String, remoteBookId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourceExclusion(entity: UpdateSourceExclusionEntity): Long

    @Query("DELETE FROM update_source_exclusions WHERE source_id = :sourceId")
    suspend fun deleteSourceExclusion(sourceId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUndo(entity: UpdateUndoEntity)

    @Query("SELECT * FROM update_ignore_undos WHERE token_id = :tokenId")
    suspend fun undo(tokenId: String): UpdateUndoEntity?

    @Query("DELETE FROM update_ignore_undos WHERE token_id = :tokenId")
    suspend fun deleteUndo(tokenId: String): Int

    @Query("DELETE FROM update_ignore_undos WHERE expires_at_millis <= :now")
    suspend fun deleteExpiredUndos(now: Long): Int

    @Query("SELECT * FROM unresolved_updates ORDER BY detected_at_millis DESC, source_id, remote_book_id")
    fun observeUnresolvedUpdates(): Flow<List<UnresolvedUpdateEntity>>

    @Query("SELECT * FROM update_sessions WHERE session_id = (" + LATEST_UPDATE_SESSION_ID + ")")
    fun observeLatestSession(): Flow<UpdateSessionEntity?>

    @Query("SELECT i.* FROM update_session_items i WHERE i.session_id = (" + LATEST_UPDATE_SESSION_ID + ") ORDER BY i.state, i.source_id, i.remote_book_id LIMIT :limit")
    fun observeLatestSessionItems(limit: Int): Flow<List<UpdateSessionItemEntity>>

    @Query("SELECT COUNT(*) FROM update_session_items WHERE session_id = (" + LATEST_UPDATE_SESSION_ID + ")")
    fun observeLatestSessionItemCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM update_session_items WHERE session_id = (" + LATEST_UPDATE_SESSION_ID + ")")
    suspend fun latestSessionItemCount(): Int

    @Query("SELECT * FROM update_policy WHERE id = 'default'")
    fun observePolicy(): Flow<UpdatePolicyEntity?>

    @Query("SELECT * FROM update_book_exclusions ORDER BY source_id, remote_book_id")
    fun observeBookExclusions(): Flow<List<UpdateBookExclusionEntity>>

    @Query("SELECT * FROM update_source_exclusions ORDER BY source_id")
    fun observeSourceExclusions(): Flow<List<UpdateSourceExclusionEntity>>
}
