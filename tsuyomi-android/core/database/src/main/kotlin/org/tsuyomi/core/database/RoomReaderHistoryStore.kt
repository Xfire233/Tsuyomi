/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.database

import androidx.room.withTransaction
import java.time.Instant
import org.tsuyomi.core.database.room.LibraryDao
import org.tsuyomi.core.database.room.ReaderHistoryEntity
import org.tsuyomi.shared.model.BookIdentity

/** Durable successful-Reader admissions, independent from Detail browsing and progress capture. */
internal class RoomReaderHistoryStore(
    private val database: TsuyomiDatabase,
    private val dao: LibraryDao,
) {
    suspend fun recordReaderVisit(identity: BookIdentity, visitedAt: Instant) = database.withTransaction {
        check(dao.bookByIdentityKey(identity.storageKey) != null) { "Reader history requires retained book metadata" }
        val remote = identity as? BookIdentity.Remote
        val visit = ReaderHistoryEntity(
            identityKey = identity.storageKey,
            sourceId = remote?.sourceId,
            remoteBookId = remote?.remoteBookId,
            lastVisitedAtEpochSecond = visitedAt.epochSecond,
            lastVisitedAtNano = visitedAt.nano,
        )
        if (dao.insertReaderHistoryIfAbsentByIdentityKey(visit) == -1L) {
            dao.updateReaderHistoryIfNewerByIdentityKey(identity.storageKey, visitedAt.epochSecond, visitedAt.nano)
        }
    }
}
