/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.tsuyomi.shared.librarydomain.CollectionKind

@Entity(
    tableName = "books",
    primaryKeys = ["source_id", "remote_book_id"],
)
internal data class BookEntity(
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "remote_book_id")
    val remoteBookId: String,
    val title: String,
    @ColumnInfo(name = "authors_json", defaultValue = "'[]'")
    val authorsJson: String,
    @ColumnInfo(name = "author_sort_key")
    val authorSortKey: ByteArray?,
    @ColumnInfo(name = "cover_url")
    val coverUrl: String? = null,
    @ColumnInfo(name = "canonical_url")
    val canonicalUrl: String? = null,
    val status: String? = null,
    @ColumnInfo(name = "remote_tags_json", defaultValue = "'[]'")
    val remoteTagsJson: String,
    @ColumnInfo(name = "source_update_key")
    val legacySourceUpdateKey: String? = null,
    @ColumnInfo(name = "has_unread_update", defaultValue = "0")
    val legacyHasUnreadUpdate: Boolean = false,
    @ColumnInfo(name = "added_at_epoch_second")
    val addedAtEpochSecond: Long,
    @ColumnInfo(name = "added_at_nano")
    val addedAtNano: Int,
    @ColumnInfo(name = "metadata_updated_at_epoch_second")
    val metadataUpdatedAtEpochSecond: Long,
    @ColumnInfo(name = "metadata_updated_at_nano")
    val metadataUpdatedAtNano: Int,
)

@Entity(
    tableName = "collections",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["collection_id"],
            childColumns = ["parent_collection_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["parent_collection_id"])],
)
internal data class CollectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "collection_id")
    val collectionId: String,
    val kind: CollectionKind,
    val title: String,
    @ColumnInfo(name = "parent_collection_id")
    val parentCollectionId: String?,
    @ColumnInfo(name = "display_order")
    val displayOrder: Long,
    @ColumnInfo(name = "created_at_epoch_second", defaultValue = "0") val createdAtEpochSecond: Long,
    @ColumnInfo(name = "created_at_nano", defaultValue = "0") val createdAtNano: Int,
    @ColumnInfo(name = "updated_at_epoch_second", defaultValue = "0") val updatedAtEpochSecond: Long,
    @ColumnInfo(name = "updated_at_nano", defaultValue = "0") val updatedAtNano: Int,
)

@Entity(
    tableName = "manual_collection_memberships",
    primaryKeys = ["collection_id", "source_id", "remote_book_id"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["collection_id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LibraryEntryEntity::class,
            parentColumns = ["source_id", "remote_book_id"],
            childColumns = ["source_id", "remote_book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["source_id", "remote_book_id"])],
)
internal data class ManualCollectionMembershipEntity(
    @ColumnInfo(name = "collection_id")
    val collectionId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "remote_book_id")
    val remoteBookId: String,
    @ColumnInfo(name = "added_at_epoch_second") val addedAtEpochSecond: Long,
    @ColumnInfo(name = "added_at_nano") val addedAtNano: Int,
    @ColumnInfo(name = "display_order") val displayOrder: Long,
)

/**
 * Locator v1 fields are stored column-for-column. There are intentionally no rendered page,
 * viewport, scroll-extent, or Room row-id columns.
 */
@Entity(
    tableName = "reading_progress",
    primaryKeys = ["source_id", "remote_book_id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["source_id", "remote_book_id"],
            childColumns = ["source_id", "remote_book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ReadingProgressEntity(
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "remote_book_id")
    val remoteBookId: String,
    @ColumnInfo(name = "content_id")
    val contentId: String,
    val revision: String?,
    @ColumnInfo(name = "block_id")
    val blockId: String?,
    @ColumnInfo(name = "text_anchor_digest")
    val textAnchorDigest: String?,
    @ColumnInfo(name = "character_offset")
    val characterOffset: Int?,
    @ColumnInfo(name = "chapter_progress")
    val chapterProgress: Double?,
    @ColumnInfo(name = "book_progress")
    val bookProgress: Double?,
    @ColumnInfo(name = "updated_at_epoch_second")
    val updatedAtEpochSecond: Long,
    @ColumnInfo(name = "updated_at_nano")
    val updatedAtNano: Int,
)

/**
 * A successful Reader admission. This is deliberately separate from browsing metadata and
 * semantic progress: opening a completed book still matters, while Detail/cache activity does not.
 */
@Entity(
    tableName = "reader_history",
    primaryKeys = ["source_id", "remote_book_id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["source_id", "remote_book_id"],
            childColumns = ["source_id", "remote_book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ReaderHistoryEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    @ColumnInfo(name = "last_visited_at_epoch_second") val lastVisitedAtEpochSecond: Long,
    @ColumnInfo(name = "last_visited_at_nano") val lastVisitedAtNano: Int,
)
@Entity(
    tableName = "completed_chapters",
    primaryKeys = ["source_id", "remote_book_id", "chapter_id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["source_id", "remote_book_id"],
            childColumns = ["source_id", "remote_book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class CompletedChapterEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "completed_at_epoch_second") val completedAtEpochSecond: Long,
    @ColumnInfo(name = "completed_at_nano") val completedAtNano: Int,
)

/**
 * Semantic bookmarks intentionally do not reference [BookEntity]. They remain durable when a
 * local pin is removed or a source is unavailable, and they never own reading progress.
 */
@Entity(
    tableName = "reader_bookmarks",
    primaryKeys = ["source_id", "remote_book_id", "bookmark_position_key"],
)
internal data class ReaderBookmarkEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    @ColumnInfo(name = "bookmark_position_key") val bookmarkPositionKey: String,
    @ColumnInfo(name = "content_id") val contentId: String,
    val revision: String?,
    @ColumnInfo(name = "block_id") val blockId: String?,
    @ColumnInfo(name = "text_anchor_digest") val textAnchorDigest: String?,
    @ColumnInfo(name = "character_offset") val characterOffset: Int?,
    @ColumnInfo(name = "chapter_progress") val chapterProgress: Double?,
    @ColumnInfo(name = "book_progress") val bookProgress: Double?,
    @ColumnInfo(name = "captured_at_epoch_second") val capturedAtEpochSecond: Long,
    @ColumnInfo(name = "captured_at_nano") val capturedAtNano: Int,
)


@Entity(
    tableName = "library_entries",
    primaryKeys = ["source_id", "remote_book_id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["source_id", "remote_book_id"],
            childColumns = ["source_id", "remote_book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class LibraryEntryEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    @ColumnInfo(name = "added_at_epoch_second") val addedAtEpochSecond: Long,
    @ColumnInfo(name = "added_at_nano") val addedAtNano: Int,
    val rating: Int?,
    @ColumnInfo(name = "read_later", defaultValue = "0") val readLater: Boolean = false,
    @ColumnInfo(name = "display_order", defaultValue = "2147483647") val displayOrder: Int = Int.MAX_VALUE,
    @ColumnInfo(name = "local_pin", defaultValue = "1") val locallyPinned: Boolean = true,
)

@Entity(
    tableName = "local_book_tags",
    primaryKeys = ["source_id", "remote_book_id", "normalized_tag"],
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntryEntity::class,
            parentColumns = ["source_id", "remote_book_id"],
            childColumns = ["source_id", "remote_book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class LocalBookTagEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    @ColumnInfo(name = "normalized_tag") val normalizedTag: String,
    @ColumnInfo(name = "display_tag") val displayTag: String,
)

@Entity(tableName = "source_availability")
internal data class SourceAvailabilityEntity(
    @PrimaryKey @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "verified_version") val verifiedVersion: String?,
    val available: Boolean,
    val generation: Long,
)

@Entity(tableName = "source_remote_policy")
internal data class SourceRemotePolicyEntity(
    @PrimaryKey @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "trusted_publisher_fingerprint") val trustedPublisherFingerprint: String,
    @ColumnInfo(name = "capability_set_fingerprint") val capabilitySetFingerprint: String,
    @ColumnInfo(name = "approved_origin") val approvedOrigin: String,
    @ColumnInfo(name = "add_writeback_enabled") val addWritebackEnabled: Boolean,
    @ColumnInfo(name = "first_import_prompt_dismissed") val firstImportPromptDismissed: Boolean,
    @ColumnInfo(name = "remove_writeback_enabled", defaultValue = "0") val removeWritebackEnabled: Boolean = false,
    @ColumnInfo(name = "move_writeback_enabled", defaultValue = "0") val moveWritebackEnabled: Boolean = false,
)

@Entity(tableName = "remote_mirror_bindings")
internal data class RemoteMirrorBindingEntity(
    @PrimaryKey @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val frozen: Boolean,
    @ColumnInfo(name = "updated_at_epoch_second") val updatedAtEpochSecond: Long,
)

@Entity(
    tableName = "remote_mirror_targets",
    primaryKeys = ["source_id", "target_id"],
)
internal data class RemoteMirrorTargetEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "target_id") val targetId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    val kind: String,
    val frozen: Boolean,
    @ColumnInfo(name = "updated_at_epoch_second") val updatedAtEpochSecond: Long,
)

@Entity(
    tableName = "remote_mirror_items",
    primaryKeys = ["source_id", "remote_book_id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["source_id", "remote_book_id"],
            childColumns = ["source_id", "remote_book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["source_id", "remote_book_id"])],
)
internal data class RemoteMirrorItemEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    @ColumnInfo(name = "target_id") val targetId: String?,
    @ColumnInfo(name = "updated_at_epoch_second") val updatedAtEpochSecond: Long,
)

internal data class RemoteMirrorBookRow(
    @androidx.room.Embedded val book: BookEntity,
    @ColumnInfo(name = "mirror_target_id") val targetId: String?,
)

@Entity(
    tableName = "remote_library_reconciliation",
    indices = [Index(value = ["source_id", "remote_book_id"])],
)
internal data class RemoteLibraryReconciliationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    @ColumnInfo(name = "package_digest") val packageDigest: String,
    @ColumnInfo(name = "package_version") val packageVersion: String,
    @ColumnInfo(name = "capability_set_fingerprint") val capabilitySetFingerprint: String,
    @ColumnInfo(name = "registry_generation") val registryGeneration: Long,
    val state: String,
    @ColumnInfo(name = "created_at_epoch_second") val createdAtEpochSecond: Long,
    @ColumnInfo(name = "updated_at_epoch_second") val updatedAtEpochSecond: Long,
    @ColumnInfo(name = "diagnostic_id") val diagnosticId: String?,
    @ColumnInfo(name = "operation", defaultValue = "'ADD'") val operation: String = "ADD",
    @ColumnInfo(name = "target_id") val targetId: String? = null,
    @ColumnInfo(name = "target_name") val targetName: String? = null,
)

@Entity(
    tableName = "smart_rules",
    foreignKeys = [ForeignKey(entity = CollectionEntity::class, parentColumns = ["collection_id"], childColumns = ["collection_id"], onDelete = ForeignKey.CASCADE)],
)
internal data class SmartRuleEntity(
    @PrimaryKey @ColumnInfo(name = "collection_id") val collectionId: String,
    @ColumnInfo(name = "rule_version") val ruleVersion: Int,
    @ColumnInfo(name = "ast_json") val astJson: String,
    @ColumnInfo(name = "compiled_projection_version") val compiledProjectionVersion: Int,
)

@Entity(
    tableName = "subscription_drafts",
    foreignKeys = [ForeignKey(entity = CollectionEntity::class, parentColumns = ["collection_id"], childColumns = ["collection_id"], onDelete = ForeignKey.CASCADE)],
)
internal data class SubscriptionDraftEntity(
    @PrimaryKey @ColumnInfo(name = "collection_id") val collectionId: String,
    val mode: String,
    @ColumnInfo(name = "source_scope_json") val sourceScopeJson: String,
    @ColumnInfo(name = "query_json") val queryJson: String,
    val enabled: Boolean = false,
    @ColumnInfo(name = "import_session_id") val importSessionId: String?,
)

@Entity(tableName = "search_history", primaryKeys = ["source_id", "normalized_query"])
internal data class SearchHistoryEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "normalized_query") val normalizedQuery: String,
    @ColumnInfo(name = "display_query") val displayQuery: String,
    @ColumnInfo(name = "last_used_at_epoch_second") val lastUsedAtEpochSecond: Long,
    @ColumnInfo(name = "last_used_at_nano") val lastUsedAtNano: Int,
)

@Entity(
    tableName = "browsing_history",
    primaryKeys = ["source_id", "remote_book_id"],
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["source_id", "remote_book_id"], childColumns = ["source_id", "remote_book_id"], onDelete = ForeignKey.CASCADE)],
)
internal data class BrowsingHistoryEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_book_id") val remoteBookId: String,
    @ColumnInfo(name = "last_viewed_at_epoch_second") val lastViewedAtEpochSecond: Long,
    @ColumnInfo(name = "last_viewed_at_nano") val lastViewedAtNano: Int,
)

@Entity(tableName = "import_sessions")
internal data class ImportSessionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    @ColumnInfo(name = "plan_digest") val planDigest: String,
    @ColumnInfo(name = "normalized_plan_path") val normalizedPlanPath: String,
    val status: String,
    @ColumnInfo(name = "source_created_at_epoch_second") val sourceCreatedAtEpochSecond: Long,
    @ColumnInfo(name = "started_at_epoch_second") val startedAtEpochSecond: Long,
    @ColumnInfo(name = "completed_at_epoch_second") val completedAtEpochSecond: Long?,
    @ColumnInfo(name = "preference_patch_json") val preferencePatchJson: String,
    @ColumnInfo(name = "summary_json") val summaryJson: String?,
)

@Entity(
    tableName = "import_warnings",
    primaryKeys = ["session_id", "ordinal"],
    foreignKeys = [ForeignKey(entity = ImportSessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)],
)
internal data class ImportWarningEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    val ordinal: Int,
    @ColumnInfo(name = "safe_code") val safeCode: String,
    @ColumnInfo(name = "safe_record_ref") val safeRecordRef: String?,
    @ColumnInfo(name = "field_name") val fieldName: String?,
    val severity: String,
)
