/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.database

import androidx.room.withTransaction
import java.time.Instant
import org.tsuyomi.core.database.room.LibraryDao
import org.tsuyomi.core.database.room.ReadingProgressEntity
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity

internal class RoomReadingProgressStore(
    private val database: TsuyomiDatabase,
    private val dao: LibraryDao,
) {
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
                else -> if (!existing.isSemanticallyValid()) {
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
}

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

internal fun ReadingProgressEntity.isSemanticallyValid(): Boolean = toDomainOrNull() != null

internal fun ReadingProgressEntity.toDomainOrNull(): ReadingProgress? = runCatching {
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
