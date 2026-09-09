/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "update_sessions",
    indices = [Index(value = ["state", "lease_expires_at_millis"])],
)
internal data class UpdateSessionEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val id: String,
    val trigger: String,
    val state: String,
    val total: Int,
    val completed: Int,
    val updated: Int,
    val failed: Int,
    val reason: String?,
    @ColumnInfo(name = "lease_expires_at_millis") val leaseExpiresAt: Long?,
    @ColumnInfo(name = "lease_owner_token") val leaseOwnerToken: String?,
    @ColumnInfo(name = "cancellation_requested") val cancellationRequested: Boolean,
    @ColumnInfo(name = "started_at_millis") val startedAt: Long,
    @ColumnInfo(name = "finished_at_millis") val finishedAt: Long?,
)

@Entity(
    tableName = "update_session_items",
    primaryKeys = ["session_id", "source_id", "remote_book_id"],
    indices = [Index(value = ["session_id", "state"])],
)
internal data class UpdateSessionItemEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    @ColumnInfo(name = "captured_title") val capturedTitle: String,
    val state: String,
    val reason: String?,
    val anchor: String?,
)

@Entity(tableName = "update_baselines", primaryKeys = ["source_id", "remote_book_id"])
internal data class UpdateBaselineEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    val anchor: String,
    @ColumnInfo(name = "chapters_json") val chaptersJson: String,
    @ColumnInfo(name = "last_updated_date") val lastUpdatedDate: String?,
    @ColumnInfo(name = "updated_at_millis") val updatedAt: Long,
)

@Entity(
    tableName = "unresolved_updates",
    primaryKeys = ["source_id", "remote_book_id"],
    indices = [Index(value = ["detected_at_millis"])],
)
internal data class UnresolvedUpdateEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    val title: String,
    val anchor: String,
    @ColumnInfo(name = "chapters_json") val chaptersJson: String,
    @ColumnInfo(name = "new_chapter_ids_json") val newChapterIdsJson: String,
    @ColumnInfo(name = "last_updated_date") val lastUpdatedDate: String?,
    @ColumnInfo(name = "detected_at_millis") val detectedAt: Long,
    val revision: Long,
)

@Entity(tableName = "update_policy")
internal data class UpdatePolicyEntity(
    @PrimaryKey val id: String,
    val cadence: String,
    @ColumnInfo(name = "unmetered_only") val unmeteredOnly: Boolean,
    @ColumnInfo(name = "requires_charging") val requiresCharging: Boolean,
    @ColumnInfo(name = "battery_not_low") val batteryNotLow: Boolean,
)

@Entity(tableName = "update_book_exclusions", primaryKeys = ["source_id", "remote_book_id"])
internal data class UpdateBookExclusionEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
)

@Entity(tableName = "update_source_exclusions")
internal data class UpdateSourceExclusionEntity(
    @PrimaryKey @ColumnInfo(name = "source_id") val sourceId: String,
)

@Entity(
    tableName = "update_ignore_undos",
    indices = [Index(value = ["expires_at_millis"])],
)
internal data class UpdateUndoEntity(
    @PrimaryKey @ColumnInfo(name = "token_id") val tokenId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    val title: String,
    val anchor: String,
    @ColumnInfo(name = "chapters_json") val chaptersJson: String,
    @ColumnInfo(name = "new_chapter_ids_json") val newChapterIdsJson: String,
    @ColumnInfo(name = "last_updated_date") val lastUpdatedDate: String?,
    @ColumnInfo(name = "detected_at_millis") val detectedAt: Long,
    val revision: Long,
    @ColumnInfo(name = "expires_at_millis") val expiresAt: Long,
)
