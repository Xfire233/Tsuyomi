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
import org.tsuyomi.shared.librarydomain.ReadingProgress
import org.tsuyomi.shared.librarydomain.ProgressWriteResult

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
            when (val existing = dao.progressByIdentityKey(entity.identityKey)) {
                null -> if (dao.insertProgressIfAbsent(entity) != -1L) {
                    ProgressWriteResult.APPLIED
                } else {
                    ProgressWriteResult.KEPT_EXISTING
                }
                else -> if (!existing.isSemanticallyValid()) {
                    check(dao.replaceProgress(entity) == 1)
                    ProgressWriteResult.APPLIED
                } else if (
                    dao.updateProgressIfNewerByIdentityKey(
                        identityKey = entity.identityKey,
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
        dao.progressByIdentityKey(identity.storageKey)?.toDomainOrNull(identity)

    suspend fun markChapterCompleted(identity: BookIdentity, chapterId: String, completedAt: Instant): Boolean {
        require(chapterId.isNotBlank() && chapterId.codePointCount(0, chapterId.length) <= 512) { "Invalid chapter ID" }
        val remote = identity as? BookIdentity.Remote
        return dao.insertCompletedChapter(
            org.tsuyomi.core.database.room.CompletedChapterEntity(
                identityKey = identity.storageKey,
                sourceId = remote?.sourceId,
                remoteBookId = remote?.remoteBookId,
                chapterId = chapterId,
                completedAtEpochSecond = completedAt.epochSecond,
                completedAtNano = completedAt.nano,
            ),
        ) != -1L
    }

    suspend fun completedChapterIds(identity: BookIdentity): Set<String> =
        dao.completedChapterIdsByIdentityKey(identity.storageKey).toSet()
}

private fun ReadingProgress.toEntity(): ReadingProgressEntity {
    val remote = identity as? BookIdentity.Remote
    return ReadingProgressEntity(
        identityKey = identity.storageKey,
        sourceId = remote?.sourceId,
        remoteBookId = remote?.remoteBookId,
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
}

private fun ReadingProgressEntity.timestamp(): Instant =
    Instant.ofEpochSecond(updatedAtEpochSecond, updatedAtNano.toLong())

private fun ReadingProgressEntity.identity(): BookIdentity = when {
    sourceId != null && remoteBookId != null -> BookIdentity.Remote(sourceId, remoteBookId).also {
        check(it.storageKey == identityKey)
    }
    sourceId == null && remoteBookId == null -> BookIdentity.Local(identityKey.substringAfter(':')).also {
        check(it.storageKey == identityKey)
    }
    else -> error("Invalid persisted book identity")
}

internal fun ReadingProgressEntity.isSemanticallyValid(): Boolean = runCatching { toDomainOrNull(identity()) != null }.getOrDefault(false)

internal fun ReadingProgressEntity.toDomainOrNull(identity: BookIdentity): ReadingProgress? = runCatching {
    require(identityKey == identity.storageKey)
    val remote = identity as? BookIdentity.Remote
    require(sourceId == remote?.sourceId && remoteBookId == remote?.remoteBookId)
    val timestamp = timestamp()
    ReadingProgress(
        identity = identity,
        locator = ReaderLocator(
            document = DocumentIdentity(identity, contentId, revision),
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
    require(locator.document.book == identity)
    require(updatedAt == locator.capturedAt)
    val characterOffset = locator.characterOffset
    val chapterProgress = locator.chapterProgress
    val bookProgress = locator.bookProgress
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
