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
import org.tsuyomi.core.database.CollectionKind

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
            entity = BookEntity::class,
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
