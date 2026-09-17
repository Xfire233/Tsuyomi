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
import org.tsuyomi.core.database.room.RemoteMirrorBindingEntity
import org.tsuyomi.core.database.room.RemoteMirrorItemEntity
import org.tsuyomi.core.database.room.RemoteMirrorTargetEntity
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.SourceAvailability
import org.tsuyomi.shared.librarydomain.SourceRemotePolicy
import org.tsuyomi.shared.librarydomain.RemoteMirrorBinding
import org.tsuyomi.shared.librarydomain.RemoteMirrorSnapshot
import org.tsuyomi.shared.librarydomain.RemoteMirrorTargetSnapshot
import org.tsuyomi.shared.librarydomain.RemoteMirrorBookSnapshot
import org.tsuyomi.shared.librarydomain.RemoteReconciliationRecord
import org.tsuyomi.shared.librarydomain.RemoteReconciliationState

/** One lease-checked remote library snapshot accepted into local persistence. */
data class RemoteLibraryMergeRequest(
    val sourceId: String,
    val books: List<LibraryBook>,
    val expectedVersion: String,
    val expectedCapabilityFingerprint: String,
    val expectedGeneration: Long,
    val importedAt: Instant,
)

/** Durable identity and package lease for one user-authorized remote mutation (add, remove, move). */
data class RemoteMutationRequest(
    val book: LibraryBook,
    val operation: String = "ADD",
    val targetId: String? = null,
    val targetName: String? = null,
    val packageDigest: String,
    val packageVersion: String,
    val capabilitySetFingerprint: String,
    val registryGeneration: Long,
    val startedAt: Instant,
)

data class RemoteMirrorReplaceRequest(
    val sourceId: String,
    val sourceName: String,
    val books: List<RemoteMirrorBookSnapshot>,
    val targets: List<RemoteMirrorTargetSnapshot>,
    val updatedAt: Instant,
)

typealias RemoteAddRequest = RemoteMutationRequest

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

    suspend fun markMissingSourcesUnavailable(installedSourceIds: List<String>): Int =
        dao.markMissingSourcesUnavailable(installedSourceIds)

    suspend fun sourceRemotePolicy(sourceId: String): SourceRemotePolicy? = dao.sourceRemotePolicy(sourceId)?.let {
        SourceRemotePolicy(
            it.sourceId,
            it.trustedPublisherFingerprint,
            it.capabilitySetFingerprint,
            it.approvedOrigin,
            it.addWritebackEnabled,
            it.firstImportPromptDismissed,
            it.removeWritebackEnabled,
            it.moveWritebackEnabled,
        )
    }


    suspend fun saveRemoteMirrorSnapshot(request: RemoteMirrorReplaceRequest) = database.withTransaction {
        require(request.sourceId.isNotBlank() && request.sourceName.isNotBlank())
        require(request.books.map { it.book.identity }.distinct().size == request.books.size)
        require(request.books.all { it.book.identity.sourceId == request.sourceId })
        require(request.targets.all { it.sourceId == request.sourceId })
        val updatedAt = request.updatedAt.epochSecond
        dao.upsertRemoteMirrorBinding(
            RemoteMirrorBindingEntity(request.sourceId, request.sourceName, frozen = false, updatedAt),
        )
        dao.freezeRemoteMirrorTargets(request.sourceId)
        if (request.targets.isNotEmpty()) {
            dao.upsertRemoteMirrorTargets(
                request.targets.map {
                    RemoteMirrorTargetEntity(
                        sourceId = it.sourceId,
                        targetId = it.targetId,
                        displayName = it.displayName,
                        parentId = it.parentId,
                        kind = it.kind,
                        frozen = false,
                        updatedAtEpochSecond = updatedAt,
                    )
                },
            )
        }
        request.books.forEach { saveRemoteBook(it.book) }
        dao.deleteRemoteMirrorItems(request.sourceId)
        if (request.books.isNotEmpty()) {
            dao.upsertRemoteMirrorItems(
                request.books.map {
                    RemoteMirrorItemEntity(
                        sourceId = request.sourceId,
                        remoteBookId = it.book.identity.remoteBookId,
                        targetId = it.targetId,
                        updatedAtEpochSecond = updatedAt,
                    )
                },
            )
        }
    }

    suspend fun ensureRemoteMirrorBinding(sourceId: String, sourceName: String, now: Instant = Instant.now()) {
        val current = dao.remoteMirrorBinding(sourceId)
        dao.upsertRemoteMirrorBinding(
            RemoteMirrorBindingEntity(
                sourceId = sourceId,
                displayName = sourceName,
                frozen = current?.frozen ?: false,
                updatedAtEpochSecond = current?.updatedAtEpochSecond ?: now.epochSecond,
            ),
        )
    }

    suspend fun remoteMirrorBindings(): List<RemoteMirrorBinding> = dao.remoteMirrorBindings().map {
        RemoteMirrorBinding(it.sourceId, it.displayName, it.frozen, it.updatedAtEpochSecond)
    }

    suspend fun remoteMirrorSnapshot(sourceId: String): RemoteMirrorSnapshot? {
        val binding = dao.remoteMirrorBinding(sourceId) ?: return null
        return RemoteMirrorSnapshot(
            binding = RemoteMirrorBinding(binding.sourceId, binding.displayName, binding.frozen, binding.updatedAtEpochSecond),
            targets = dao.remoteMirrorTargets(sourceId).map {
                RemoteMirrorTargetSnapshot(
                    sourceId = it.sourceId,
                    targetId = it.targetId,
                    displayName = it.displayName,
                    parentId = it.parentId,
                    kind = it.kind,
                    frozen = it.frozen,
                    updatedAtEpochSecond = it.updatedAtEpochSecond,
                )
            },
            books = dao.remoteMirrorBooks(sourceId).map { RemoteMirrorBookSnapshot(it.book.toDomain(), it.targetId) },
        )
    }

    suspend fun updateRemoteMirrorBookTarget(identity: org.tsuyomi.shared.model.BookIdentity, targetId: String?, now: Instant): Boolean =
        dao.updateRemoteMirrorBookTarget(identity.sourceId, identity.remoteBookId, targetId, now.epochSecond) == 1

    suspend fun upsertRemoteMirrorBook(book: LibraryBook, targetId: String?, now: Instant) = database.withTransaction {
        saveRemoteBook(book)
        dao.upsertRemoteMirrorItems(
            listOf(
                RemoteMirrorItemEntity(
                    sourceId = book.identity.sourceId,
                    remoteBookId = book.identity.remoteBookId,
                    targetId = targetId,
                    updatedAtEpochSecond = now.epochSecond,
                ),
            ),
        )
    }

    suspend fun removeRemoteMirrorBook(identity: org.tsuyomi.shared.model.BookIdentity): Boolean =
        dao.deleteRemoteMirrorBook(identity.sourceId, identity.remoteBookId) == 1

    suspend fun remoteMirrorTargetCount(sourceId: String, targetId: String): Int =
        dao.remoteMirrorTargetCount(sourceId, targetId)
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
            saveRemoteBook(book)
            if (insertOrPinLibraryEntry(book, request.importedAt)) {
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

    suspend fun setRemoveWritebackEnabled(sourceId: String, capabilityFingerprint: String, enabled: Boolean): Boolean =
        dao.setRemoveWritebackEnabled(sourceId, capabilityFingerprint, enabled) == 1

    suspend fun setMoveWritebackEnabled(sourceId: String, capabilityFingerprint: String, enabled: Boolean): Boolean =
        dao.setMoveWritebackEnabled(sourceId, capabilityFingerprint, enabled) == 1

    suspend fun saveSourceRemotePolicy(policy: SourceRemotePolicy) {
        dao.upsertSourceRemotePolicy(
            SourceRemotePolicyEntity(
                policy.sourceId,
                policy.trustedPublisherFingerprint,
                policy.capabilitySetFingerprint,
                policy.approvedOrigin,
                policy.addWritebackEnabled,
                policy.firstImportPromptDismissed,
                policy.removeWritebackEnabled,
                policy.moveWritebackEnabled,
            ),
        )
    }
    suspend fun beginRemoteMutation(
        request: RemoteMutationRequest,
        retryingUnresolvedId: String? = null,
    ): String = database.withTransaction {
        val book = request.book
        saveRemoteBook(book)
        if (request.operation.equals("ADD", ignoreCase = true)) {
            insertOrPinLibraryEntry(book, request.startedAt)
        }
        val active = dao.activeReconciliation(book.identity.sourceId, book.identity.remoteBookId)
        if (retryingUnresolvedId == null) {
            check(active == null) { "Remote operation already active or unresolved for this book" }
        } else {
            check(
                active?.id == retryingUnresolvedId &&
                    active.state == RemoteReconciliationState.UNRESOLVED.name &&
                    active.operation.equals(request.operation, ignoreCase = true),
            ) { "Remote retry does not match the current unresolved operation" }
        }
        val id = UUID.randomUUID().toString()
        dao.insertReconciliation(
            RemoteLibraryReconciliationEntity(
                id = id,
                sourceId = book.identity.sourceId,
                remoteBookId = book.identity.remoteBookId,
                packageDigest = request.packageDigest,
                packageVersion = request.packageVersion,
                capabilitySetFingerprint = request.capabilitySetFingerprint,
                registryGeneration = request.registryGeneration,
                state = RemoteReconciliationState.PENDING_USER_ACTION.name,
                createdAtEpochSecond = request.startedAt.epochSecond,
                updatedAtEpochSecond = request.startedAt.epochSecond,
                diagnosticId = null,
                operation = request.operation,
                targetId = request.targetId,
                targetName = request.targetName,
            ),
        )
        id
    }

    suspend fun beginRemoteAdd(request: RemoteAddRequest, retryingUnresolvedAddId: String? = null): String =
        beginRemoteMutation(request, retryingUnresolvedAddId)

    suspend fun transitionRemoteMutation(
        id: String,
        expected: RemoteReconciliationState,
        next: RemoteReconciliationState,
        now: Instant,
        diagnosticId: String? = null,
    ): Boolean {
        if (expected == RemoteReconciliationState.UNRESOLVED && next == RemoteReconciliationState.CANCELLED) {
            check(dao.reconciliation(id)?.operation?.uppercase() != "ADD") {
                "Accepted remote ADD cannot be cancelled"
            }
        }
        require(next in expected.allowedNextStates()) { "Invalid reconciliation transition: $expected -> $next" }
        return dao.transitionReconciliation(id, expected.name, next.name, now.epochSecond, diagnosticId) == 1
    }

    suspend fun transitionRemoteAdd(
        id: String,
        expected: RemoteReconciliationState,
        next: RemoteReconciliationState,
        now: Instant,
        diagnosticId: String? = null,
    ): Boolean = transitionRemoteMutation(id, expected, next, now, diagnosticId)

    suspend fun confirmRemoteMutation(
        id: String,
        identity: BookIdentity,
        operation: String,
        resolvesPriorUnresolved: Boolean,
        now: Instant,
    ): Boolean = database.withTransaction {
        if (dao.transitionReconciliation(
                id,
                RemoteReconciliationState.IN_FLIGHT.name,
                RemoteReconciliationState.CONFIRMED.name,
                now.epochSecond,
                null,
            ) != 1
        ) return@withTransaction false
        if (resolvesPriorUnresolved) {
            check(
                dao.confirmUnresolvedMutations(
                    identity.sourceId,
                    identity.remoteBookId,
                    operation,
                    now.epochSecond,
                ) > 0,
            ) { "Remote retry lost its unresolved predecessor" }
        }
        true
    }

    suspend fun cancelUnresolvedMutations(
        identity: BookIdentity,
        operation: String,
        now: Instant,
    ): Boolean = database.withTransaction {
        require(!operation.equals("ADD", ignoreCase = true)) { "Accepted remote ADD cannot be cancelled" }
        dao.cancelUnresolvedMutations(
            identity.sourceId,
            identity.remoteBookId,
            operation,
            now.epochSecond,
        ) > 0
    }

    suspend fun confirmRemoteAdd(
        id: String,
        identity: BookIdentity,
        resolvesPriorUnresolved: Boolean,
        now: Instant,
    ): Boolean = confirmRemoteMutation(id, identity, "ADD", resolvesPriorUnresolved, now)

    suspend fun unresolvedReconciliations(): List<RemoteReconciliationRecord> =
        dao.unresolvedReconciliations().map { it.toDomain() }

    suspend fun unresolvedReconciliationsForSource(sourceId: String): List<RemoteReconciliationRecord> =
        dao.unresolvedReconciliationsForSource(sourceId).map { it.toDomain() }

    suspend fun bookReconciliation(sourceId: String, remoteBookId: String): RemoteReconciliationRecord? =
        (dao.activeReconciliation(sourceId, remoteBookId) ?: dao.latestReconciliation(sourceId, remoteBookId))?.toDomain()
    private suspend fun saveRemoteBook(incoming: LibraryBook) {
        val existing = catalog.book(incoming.identity)
        val merged = existing?.let { current ->
            incoming.copy(
                author = incoming.author ?: current.author,
                authors = current.authors + incoming.authors,
                coverUrl = incoming.coverUrl ?: current.coverUrl,
                canonicalUrl = incoming.canonicalUrl ?: current.canonicalUrl,
                status = current.status,
                remoteTags = current.remoteTags,
            )
        } ?: incoming
        catalog.saveBook(merged)
    }

    private suspend fun insertOrPinLibraryEntry(book: LibraryBook, addedAt: Instant): Boolean =
        dao.insertLibraryEntry(
            LibraryEntryEntity(
                sourceId = book.identity.sourceId,
                remoteBookId = book.identity.remoteBookId,
                addedAtEpochSecond = addedAt.epochSecond,
                addedAtNano = addedAt.nano,
                rating = null,
            ),
        ) != -1L || dao.pinLibraryEntry(book.identity.sourceId, book.identity.remoteBookId) != 0

}

private fun RemoteReconciliationState.allowedNextStates(): Set<RemoteReconciliationState> = when (this) {
    RemoteReconciliationState.PENDING_USER_ACTION ->
        setOf(RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.CANCELLED)
    RemoteReconciliationState.IN_FLIGHT ->
        setOf(RemoteReconciliationState.CONFIRMED, RemoteReconciliationState.UNRESOLVED)
    RemoteReconciliationState.UNRESOLVED ->
        setOf(RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.CONFIRMED, RemoteReconciliationState.CANCELLED)
    RemoteReconciliationState.CONFIRMED,
    RemoteReconciliationState.CANCELLED,
    -> emptySet()
}

internal fun RemoteLibraryReconciliationEntity.toDomain(): RemoteReconciliationRecord = RemoteReconciliationRecord(
    id = id,
    sourceId = sourceId,
    remoteBookId = remoteBookId,
    state = runCatching { RemoteReconciliationState.valueOf(state) }.getOrDefault(RemoteReconciliationState.UNRESOLVED),
    operation = operation,
    targetId = targetId,
    targetName = targetName,
    diagnosticId = diagnosticId,
    updatedAtEpochSecond = updatedAtEpochSecond,
)
