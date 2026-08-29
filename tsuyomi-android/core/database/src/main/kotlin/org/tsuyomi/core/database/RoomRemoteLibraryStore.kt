/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.database

import androidx.room.withTransaction
import java.time.Instant
import java.util.UUID
import org.tsuyomi.core.database.room.LibraryDao
import org.tsuyomi.core.database.room.LibraryEntryEntity
import org.tsuyomi.core.database.room.RemoteLibraryReconciliationEntity
import org.tsuyomi.core.database.room.SourceAvailabilityEntity
import org.tsuyomi.core.database.room.SourceRemotePolicyEntity

/** One lease-checked remote library snapshot accepted into local persistence. */
data class RemoteLibraryMergeRequest(
    val sourceId: String,
    val books: List<LibraryBook>,
    val expectedVersion: String,
    val expectedCapabilityFingerprint: String,
    val expectedGeneration: Long,
    val importedAt: Instant,
)

/** Durable identity and package lease for one user-authorized remote add. */
data class RemoteAddRequest(
    val book: LibraryBook,
    val packageDigest: String,
    val packageVersion: String,
    val capabilitySetFingerprint: String,
    val registryGeneration: Long,
    val startedAt: Instant,
)

internal class RoomRemoteLibraryStore(
    private val database: TsuyomiDatabase,
    private val dao: LibraryDao,
    private val catalog: RoomLibraryCatalogStore,
) {
    suspend fun setSourceAvailability(sourceId: String, version: String?, available: Boolean, generation: Long) {
        dao.upsertSourceAvailability(SourceAvailabilityEntity(sourceId, version, available, generation))
    }

    suspend fun sourceAvailability(sourceId: String): SourceAvailability? = dao.sourceAvailability(sourceId)?.let {
        SourceAvailability(it.sourceId, it.verifiedVersion, it.available, it.generation)
    }

    suspend fun sourceRemotePolicy(sourceId: String): SourceRemotePolicy? = dao.sourceRemotePolicy(sourceId)?.let {
        SourceRemotePolicy(
            it.sourceId,
            it.trustedPublisherFingerprint,
            it.capabilitySetFingerprint,
            it.approvedOrigin,
            it.addWritebackEnabled,
            it.firstImportPromptDismissed,
        )
    }

    suspend fun merge(request: RemoteLibraryMergeRequest): Int = database.withTransaction {
        require(request.sourceId.isNotBlank()) { "Remote library source is required" }
        require(request.books.map { it.identity }.distinct().size == request.books.size) {
            "Duplicate remote library identity"
        }
        require(request.books.all { it.identity.sourceId == request.sourceId }) { "Remote library source mismatch" }
        suspend fun leaseValid(): Boolean {
            val availability = dao.sourceAvailability(request.sourceId) ?: return false
            val policy = dao.sourceRemotePolicy(request.sourceId) ?: return false
            return availability.available && availability.verifiedVersion == request.expectedVersion &&
                availability.generation == request.expectedGeneration &&
                policy.capabilitySetFingerprint == request.expectedCapabilityFingerprint
        }
        check(leaseValid()) { "Source changed before remote merge" }
        var added = 0
        request.books.forEach { book ->
            catalog.saveBook(book)
            if (
                dao.insertLibraryEntry(
                    LibraryEntryEntity(
                        book.identity.sourceId,
                        book.identity.remoteBookId,
                        request.importedAt.epochSecond,
                        request.importedAt.nano,
                        null,
                    ),
                ) != -1L
            ) {
                added++
            }
        }
        check(leaseValid()) { "Source changed during remote merge" }
        added
    }

    suspend fun dismissFirstRemoteImportPrompt(sourceId: String, capabilityFingerprint: String): Boolean =
        dao.dismissFirstImportPrompt(sourceId, capabilityFingerprint) == 1

    suspend fun setAddWritebackEnabled(sourceId: String, capabilityFingerprint: String, enabled: Boolean): Boolean =
        dao.setAddWritebackEnabled(sourceId, capabilityFingerprint, enabled) == 1

    suspend fun saveSourceRemotePolicy(policy: SourceRemotePolicy) {
        dao.upsertSourceRemotePolicy(
            SourceRemotePolicyEntity(
                policy.sourceId,
                policy.trustedPublisherFingerprint,
                policy.capabilitySetFingerprint,
                policy.approvedOrigin,
                policy.addWritebackEnabled,
                policy.firstImportPromptDismissed,
            ),
        )
    }

    suspend fun beginRemoteAdd(request: RemoteAddRequest): String = database.withTransaction {
        val book = request.book
        catalog.saveBook(book)
        dao.insertLibraryEntry(
            LibraryEntryEntity(
                book.identity.sourceId,
                book.identity.remoteBookId,
                request.startedAt.epochSecond,
                request.startedAt.nano,
                null,
            ),
        )
        check(dao.activeReconciliation(book.identity.sourceId, book.identity.remoteBookId) == null) {
            "Remote add already active"
        }
        val id = UUID.randomUUID().toString()
        dao.insertReconciliation(
            RemoteLibraryReconciliationEntity(
                id,
                book.identity.sourceId,
                book.identity.remoteBookId,
                request.packageDigest,
                request.packageVersion,
                request.capabilitySetFingerprint,
                request.registryGeneration,
                RemoteReconciliationState.PENDING_USER_ACTION.name,
                request.startedAt.epochSecond,
                request.startedAt.epochSecond,
                null,
            ),
        )
        id
    }

    suspend fun transitionRemoteAdd(
        id: String,
        expected: RemoteReconciliationState,
        next: RemoteReconciliationState,
        now: Instant,
        diagnosticId: String? = null,
    ): Boolean {
        require(next in expected.allowedNextStates()) { "Invalid reconciliation transition: $expected -> $next" }
        return dao.transitionReconciliation(id, expected.name, next.name, now.epochSecond, diagnosticId) == 1
    }
}

private fun RemoteReconciliationState.allowedNextStates(): Set<RemoteReconciliationState> = when (this) {
    RemoteReconciliationState.PENDING_USER_ACTION ->
        setOf(RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.CANCELLED)
    RemoteReconciliationState.IN_FLIGHT ->
        setOf(RemoteReconciliationState.CONFIRMED, RemoteReconciliationState.UNRESOLVED)
    RemoteReconciliationState.CONFIRMED,
    RemoteReconciliationState.UNRESOLVED,
    RemoteReconciliationState.CANCELLED,
    -> emptySet()
}
