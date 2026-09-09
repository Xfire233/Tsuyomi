/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.tsuyomi.core.database.room.UpdateBaselineEntity
import org.tsuyomi.core.database.room.UpdateBookExclusionEntity
import org.tsuyomi.core.database.room.UpdateDao
import org.tsuyomi.core.database.room.UnresolvedUpdateEntity
import org.tsuyomi.core.database.room.UpdatePolicyEntity
import org.tsuyomi.core.database.room.UpdateSessionCounts
import org.tsuyomi.core.database.room.UpdateSessionEntity
import org.tsuyomi.core.database.room.UpdateSessionItemEntity
import org.tsuyomi.core.database.room.UpdateSourceExclusionEntity
import org.tsuyomi.core.database.room.UpdateUndoEntity
import org.tsuyomi.shared.librarydomain.UpdateCadence
import org.tsuyomi.shared.librarydomain.UpdateCandidate
import org.tsuyomi.shared.librarydomain.UpdatePolicy
import org.tsuyomi.shared.librarydomain.UnresolvedUpdate
import org.tsuyomi.shared.librarydomain.UpdateSessionItemSummary
import org.tsuyomi.shared.librarydomain.UpdateSessionLease
import org.tsuyomi.shared.librarydomain.UpdateSessionStates
import org.tsuyomi.shared.librarydomain.UpdateSessionSummary
import org.tsuyomi.shared.librarydomain.UpdateSnapshot
import org.tsuyomi.shared.librarydomain.UpdateStore
import org.tsuyomi.shared.librarydomain.UpdateUndo
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceUpdateChapter
import org.tsuyomi.shared.sourcecontract.SourceUpdateOutcome
import org.tsuyomi.shared.sourcecontract.SourceUpdateProbeResult

/**
 * Transactional update storage. Every lease-sensitive state change fences on the persisted owner
 * token, so a worker that has lost a lease cannot commit or cancel recovered work.
 */
class RoomUpdateStore(
    private val database: TsuyomiDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : UpdateStore {
    private val dao: UpdateDao = database.updateDao()
    private val itemLimit = MutableStateFlow(INITIAL_ITEM_PAGE_SIZE)
    private val pagingLock = Mutex()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val visibleSessionItems: Flow<VisibleSessionItems> = itemLimit.flatMapLatest { limit ->
        dao.observeLatestSessionItems(limit + 1).map { entities ->
            VisibleSessionItems(entities.take(limit), entities.size > limit)
        }
    }

    private val persistentState: Flow<PersistentState> = combine(
        dao.observeLatestSession(),
        dao.observePolicy(),
        dao.observeBookExclusions(),
        dao.observeSourceExclusions(),
    ) { session, policy, bookExclusions, sourceExclusions ->
        PersistentState(
            session = session?.toSummary(),
            policy = policy?.toDomain() ?: UpdatePolicy(),
            excludedBooks = bookExclusions.mapTo(linkedSetOf<BookIdentity>()) { BookIdentity(it.sourceId, it.remoteBookId) },
            excludedSources = sourceExclusions.mapTo(linkedSetOf<String>()) { it.sourceId },
        )
    }

    private val snapshotPages: Flow<SnapshotPages> = combine(
        dao.observeUnresolvedUpdates(),
        visibleSessionItems,
        dao.observeLatestSessionItemCount(),
    ) { updates, items, totalItems ->
        SnapshotPages(updates, items, totalItems)
    }

    override val snapshots: Flow<UpdateSnapshot> = combine(snapshotPages, persistentState) { pages, state ->
        UpdateSnapshot(
            updates = pages.updates.mapNotNull { it.toDomainOrNull() },
            session = state.session,
            policy = state.policy,
            excludedBooks = state.excludedBooks,
            excludedSources = state.excludedSources,
            sessionItems = pages.items.entities.mapNotNull { it.toSummaryOrNull() },
            totalSessionItems = pages.totalItems,
            hasMoreSessionItems = pages.items.hasMore,
        )
    }

    override suspend fun loadMoreSessionItems(): Boolean = pagingLock.withLock {
        val total = dao.latestSessionItemCount()
        val current = itemLimit.value
        if (current >= total) return@withLock false
        itemLimit.value = minOf(current + ITEM_PAGE_SIZE, total)
        true
    }

    override suspend fun recoverExpiredSessions(now: Long) {
        database.withTransaction { dao.queueExpiredSessions(now) }
    }

    override suspend fun startSession(
        id: String,
        trigger: String,
        candidates: List<UpdateCandidate>,
        now: Long,
        leaseExpiresAt: Long,
    ): UpdateSessionLease? = database.withTransaction {
        require(id.isNotBlank()) { "Update session ID is required" }
        require(leaseExpiresAt > now) { "Update lease must expire in the future" }
        dao.queueExpiredSessions(now)
        if (dao.activeSession(now) != null) return@withTransaction null

        claimQueuedSessionLocked(leaseExpiresAt)?.let { return@withTransaction it }
        itemLimit.value = INITIAL_ITEM_PAGE_SIZE
        val ownerToken = UUID.randomUUID().toString()

        val captured = LinkedHashMap<BookIdentity, UpdateCandidate>()
        candidates.forEach { candidate -> captured.putIfAbsent(candidate.identity, candidate) }
        dao.insertSession(
            UpdateSessionEntity(
                id = id,
                trigger = trigger,
                state = UpdateSessionStates.RUNNING,
                total = captured.size,
                completed = 0,
                updated = 0,
                failed = 0,
                reason = null,
                leaseExpiresAt = leaseExpiresAt,
                leaseOwnerToken = ownerToken,
                cancellationRequested = false,
                startedAt = now,
                finishedAt = null,
            ),
        )
        dao.insertSessionItems(
            captured.values.map { candidate ->
                val excluded = candidate.eligible && isExcluded(candidate.identity)
                val state = if (candidate.eligible && !excluded) ITEM_PENDING else ITEM_SKIPPED
                UpdateSessionItemEntity(
                    sessionId = id,
                    sourceId = candidate.identity.sourceId,
                    remoteBookId = candidate.identity.remoteBookId,
                    capturedTitle = candidate.title,
                    state = state,
                    reason = when {
                        state == ITEM_PENDING -> null
                        !candidate.eligible -> candidate.reason ?: ITEM_INELIGIBLE_REASON
                        else -> ITEM_EXCLUDED_REASON
                    },
                    anchor = null,
                )
            },
        )
        val lease = UpdateSessionLease(id, ownerToken, leaseExpiresAt)
        refreshSession(lease, now)
        lease
    }

    override suspend fun claimQueuedSession(now: Long, leaseExpiresAt: Long): UpdateSessionLease? = database.withTransaction {
        require(leaseExpiresAt > now) { "Update lease must expire in the future" }
        dao.queueExpiredSessions(now)
        if (dao.activeSession(now) != null) return@withTransaction null
        claimQueuedSessionLocked(leaseExpiresAt)
    }

    override suspend fun previousAnchor(identity: BookIdentity): String? =
        dao.baseline(identity.sourceId, identity.remoteBookId)?.anchor

    override suspend fun renewLease(lease: UpdateSessionLease, now: Long, leaseExpiresAt: Long): Boolean {
        require(leaseExpiresAt > now) { "Update lease must expire in the future" }
        return database.withTransaction {
            if (dao.renewLease(lease.id, lease.ownerToken, now, leaseExpiresAt) == 1) {
                true
            } else {
                val current = dao.session(lease.id)
                if (current?.state == UpdateSessionStates.RUNNING &&
                    current.leaseOwnerToken == lease.ownerToken &&
                    current.leaseExpiresAt?.let { it <= now } == true
                ) {
                    dao.queueExpiredSessions(now)
                }
                false
            }
        }
    }

    override suspend fun canProbe(lease: UpdateSessionLease, candidate: UpdateCandidate, now: Long): Boolean =
        database.withTransaction {
            isActive(lease, now) && isPending(lease, candidate) && ineligibilityReason(candidate) == null
        }

    override suspend fun pendingCandidates(lease: UpdateSessionLease, limit: Int): List<UpdateCandidate> {
        require(limit in 1..MAX_CANDIDATE_PAGE_SIZE) { "Invalid update candidate page size" }
        return dao.pendingItems(lease.id, limit).map {
            UpdateCandidate(BookIdentity(it.sourceId, it.remoteBookId), it.capturedTitle)
        }
    }

    override suspend fun recordSkipped(
        lease: UpdateSessionLease,
        candidate: UpdateCandidate,
        reason: String,
        now: Long,
    ): Boolean = database.withTransaction {
        if (!isActive(lease, now) || !isPending(lease, candidate)) return@withTransaction false
        val outcome = ineligibilityReason(candidate) ?: reason
        val changed = dao.finishPendingItem(
            lease.id,
            candidate.identity.sourceId,
            candidate.identity.remoteBookId,
            ITEM_SKIPPED,
            outcome,
            null,
        ) == 1
        if (changed) refreshSession(lease, now)
        changed
    }

    override suspend fun recordProbe(
        lease: UpdateSessionLease,
        candidate: UpdateCandidate,
        result: SourceUpdateProbeResult,
        now: Long,
    ): Boolean = database.withTransaction {
        if (!isActive(lease, now) || !isPending(lease, candidate)) return@withTransaction false
        val rejection = ineligibilityReason(candidate)
        if (rejection != null) {
            val changed = dao.finishPendingItem(
                lease.id,
                candidate.identity.sourceId,
                candidate.identity.remoteBookId,
                ITEM_SKIPPED,
                rejection,
                null,
            ) == 1
            if (changed) refreshSession(lease, now)
            return@withTransaction false
        }
        val baseline = dao.baseline(candidate.identity.sourceId, candidate.identity.remoteBookId)
        if (!isAdmissible(result, candidate.identity, baseline?.anchor)) {
            dao.finishPendingItem(
                lease.id,
                candidate.identity.sourceId,
                candidate.identity.remoteBookId,
                ITEM_FAILED,
                INVALID_PROBE_RESULT,
                null,
            )
            refreshSession(lease, now)
            return@withTransaction false
        }

        when (result.outcome) {
            SourceUpdateOutcome.UNCHANGED -> dao.upsertBaseline(result.toBaseline(now))
            SourceUpdateOutcome.UPDATED -> {
                val existing = dao.unresolved(candidate.identity.sourceId, candidate.identity.remoteBookId)
                val mergedIds = linkedSetOf<String>().apply {
                    addAll(existing?.newChapterIds().orEmpty())
                    addAll(result.newChapterIds)
                }.toList()
                val chapterIds = result.chapters.mapTo(hashSetOf()) { it.chapterId }
                if (!chapterIds.containsAll(mergedIds)) {
                    dao.finishPendingItem(
                        lease.id,
                        candidate.identity.sourceId,
                        candidate.identity.remoteBookId,
                        ITEM_FAILED,
                        INVALID_PROBE_RESULT,
                        null,
                    )
                    refreshSession(lease, now)
                    return@withTransaction false
                }
                val completedIds = dao.completedChapterIds(candidate.identity.sourceId, candidate.identity.remoteBookId).toSet()
                dao.upsertBaseline(result.toBaseline(now))
                if (mergedIds.isNotEmpty() && !mergedIds.all(completedIds::contains)) {
                    dao.upsertUnresolved(
                        UnresolvedUpdateEntity(
                            sourceId = candidate.identity.sourceId,
                            remoteBookId = candidate.identity.remoteBookId,
                            title = candidate.title,
                            anchor = requireNotNull(result.anchor),
                            chaptersJson = encodeChapters(result.chapters),
                            newChapterIdsJson = encodeIds(mergedIds),
                            lastUpdatedDate = result.lastUpdatedDate,
                            detectedAt = now,
                            revision = (existing?.revision ?: -1L) + 1L,
                        ),
                    )
                } else if (existing != null) {
                    dao.deleteUnresolved(existing.sourceId, existing.remoteBookId, existing.anchor, existing.revision)
                }
            }
            SourceUpdateOutcome.UNAVAILABLE,
            SourceUpdateOutcome.FAILED -> Unit
        }
        dao.finishPendingItem(
            lease.id,
            candidate.identity.sourceId,
            candidate.identity.remoteBookId,
            result.outcome.name,
            result.reason,
            result.anchor,
        )
        refreshSession(lease, now)
        true
    }

    override suspend fun recordFailure(
        lease: UpdateSessionLease,
        candidate: UpdateCandidate,
        reason: String?,
        now: Long,
    ): Boolean = database.withTransaction {
        if (!isActive(lease, now)) return@withTransaction false
        val changed = dao.finishPendingItem(
            lease.id,
            candidate.identity.sourceId,
            candidate.identity.remoteBookId,
            ITEM_FAILED,
            reason ?: PROBE_FAILED,
            null,
        ) == 1
        if (changed) refreshSession(lease, now)
        changed
    }

    override suspend fun cancelActiveSession(now: Long): Boolean = database.withTransaction {
        dao.queueExpiredSessions(now)
        val active = dao.activeSession(now) ?: dao.queuedSession() ?: return@withTransaction false
        cancelById(active, now)
    }

    override suspend fun cancelLease(lease: UpdateSessionLease, now: Long): Boolean = database.withTransaction {
        val active = dao.session(lease.id) ?: return@withTransaction false
        if (!isActive(lease, now)) return@withTransaction false
        dao.cancelPendingItems(lease.id)
        val counts = dao.sessionCounts(lease.id)
        dao.cancelFencedSession(
            sessionId = lease.id,
            ownerToken = lease.ownerToken,
            now = now,
            completed = counts.completed,
            updated = counts.updated,
            failed = counts.failed,
            reason = counts.reason(),
        ) == 1
    }
    override suspend fun relinquishLease(lease: UpdateSessionLease, now: Long): Boolean = database.withTransaction {
        dao.relinquishFencedSession(lease.id, lease.ownerToken) == 1
    }


    override suspend fun cancelSession(expectedSessionId: String, now: Long): Boolean = database.withTransaction {
        dao.queueExpiredSessions(now)
        val active = dao.session(expectedSessionId) ?: return@withTransaction false
        if (active.state != UpdateSessionStates.RUNNING && active.state != UpdateSessionStates.QUEUED) {
            return@withTransaction false
        }
        cancelById(active, now)
    }

    override suspend fun ignore(identity: BookIdentity, anchor: String): UpdateUndo? = database.withTransaction {
        val now = clock()
        dao.deleteExpiredUndos(now)
        val update = dao.unresolved(identity.sourceId, identity.remoteBookId) ?: return@withTransaction null
        if (update.anchor != anchor) return@withTransaction null
        if (dao.deleteUnresolved(identity.sourceId, identity.remoteBookId, anchor, update.revision) != 1) {
            return@withTransaction null
        }
        val token = UUID.randomUUID().toString()
        dao.insertUndo(
            UpdateUndoEntity(
                tokenId = token,
                sourceId = update.sourceId,
                remoteBookId = update.remoteBookId,
                title = update.title,
                anchor = update.anchor,
                chaptersJson = update.chaptersJson,
                newChapterIdsJson = update.newChapterIdsJson,
                lastUpdatedDate = update.lastUpdatedDate,
                detectedAt = update.detectedAt,
                revision = update.revision + 1L,
                expiresAt = now + UNDO_DURATION_MILLIS,
            ),
        )
        UpdateUndo(token)
    }

    override suspend fun undo(token: UpdateUndo): Boolean = database.withTransaction {
        val now = clock()
        dao.deleteExpiredUndos(now)
        val stored = dao.undo(token.id) ?: return@withTransaction false
        if (stored.expiresAt <= now) {
            dao.deleteUndo(stored.tokenId)
            return@withTransaction false
        }
        val restored = dao.insertUnresolved(stored.toUnresolvedEntity()) != -1L
        dao.deleteUndo(stored.tokenId)
        restored
    }

    override suspend fun reconcileCompleted(identity: BookIdentity, chapterIds: Set<String>) {
        if (chapterIds.isEmpty()) return
        database.withTransaction {
            val update = dao.unresolved(identity.sourceId, identity.remoteBookId) ?: return@withTransaction
            val completedIds = dao.completedChapterIds(identity.sourceId, identity.remoteBookId).toSet()
            if (!completedIds.containsAll(chapterIds)) return@withTransaction
            val unresolved = update.newChapterIds()
            if (unresolved.isNotEmpty() && unresolved.all(completedIds::contains)) {
                dao.deleteUnresolved(identity.sourceId, identity.remoteBookId, update.anchor, update.revision)
            }
        }
    }

    override suspend fun setPolicy(policy: UpdatePolicy) {
        dao.upsertPolicy(policy.toEntity())
    }

    override suspend fun excludeBook(identity: BookIdentity, excluded: Boolean) {
        if (excluded) {
            dao.insertBookExclusion(UpdateBookExclusionEntity(identity.sourceId, identity.remoteBookId))
        } else {
            dao.deleteBookExclusion(identity.sourceId, identity.remoteBookId)
        }
    }

    override suspend fun excludeSource(sourceId: String, excluded: Boolean) {
        require(sourceId.isNotBlank()) { "Source ID is required" }
        if (excluded) {
            dao.insertSourceExclusion(UpdateSourceExclusionEntity(sourceId))
        } else {
            dao.deleteSourceExclusion(sourceId)
        }
    }

    private suspend fun claimQueuedSessionLocked(leaseExpiresAt: Long): UpdateSessionLease? {
        val queued = dao.queuedSession() ?: return null
        val ownerToken = UUID.randomUUID().toString()
        if (dao.claimQueuedSession(queued.id, ownerToken, leaseExpiresAt) != 1) return null
        return UpdateSessionLease(queued.id, ownerToken, leaseExpiresAt)
    }

    private suspend fun cancelById(active: UpdateSessionEntity, now: Long): Boolean {
        dao.cancelPendingItems(active.id)
        val counts = dao.sessionCounts(active.id)
        return dao.cancelSessionById(
            sessionId = active.id,
            now = now,
            completed = counts.completed,
            updated = counts.updated,
            failed = counts.failed,
            reason = counts.reason(),
        ) == 1
    }

    private suspend fun isActive(lease: UpdateSessionLease, now: Long): Boolean {
        val session = dao.session(lease.id) ?: return false
        return session.state == UpdateSessionStates.RUNNING &&
            !session.cancellationRequested &&
            session.leaseOwnerToken == lease.ownerToken &&
            session.leaseExpiresAt?.let { it > now } == true
    }

    private suspend fun isPending(lease: UpdateSessionLease, candidate: UpdateCandidate): Boolean =
        dao.pendingItemCount(lease.id, candidate.identity.sourceId, candidate.identity.remoteBookId) == 1

    private suspend fun isExcluded(identity: BookIdentity): Boolean =
        dao.bookExclusionCount(identity.sourceId, identity.remoteBookId) != 0 ||
            dao.sourceExclusionCount(identity.sourceId) != 0

    private suspend fun ineligibilityReason(candidate: UpdateCandidate): String? = when {
        !candidate.eligible -> candidate.reason ?: ITEM_INELIGIBLE_REASON
        isExcluded(candidate.identity) -> ITEM_EXCLUDED_REASON
        !dao.isBookInUpdateScope(candidate.identity.sourceId, candidate.identity.remoteBookId) -> ITEM_INELIGIBLE_REASON
        else -> null
    }

    private suspend fun refreshSession(lease: UpdateSessionLease, now: Long) {
        val current = dao.session(lease.id) ?: return
        if (current.state != UpdateSessionStates.RUNNING || current.leaseOwnerToken != lease.ownerToken) return
        val counts = dao.sessionCounts(lease.id)
        val terminal = counts.completed == counts.total
        val state = when {
            !terminal -> UpdateSessionStates.RUNNING
            counts.total != 0 && counts.failed == counts.total -> UpdateSessionStates.FAILED
            counts.failed != 0 || counts.unavailable != 0 -> UpdateSessionStates.PARTIAL
            else -> UpdateSessionStates.COMPLETED
        }
        dao.updateRunningSession(
            sessionId = lease.id,
            expectedOwnerToken = lease.ownerToken,
            state = state,
            completed = counts.completed,
            updated = counts.updated,
            failed = counts.failed,
            reason = counts.reason(),
            leaseExpiresAt = if (terminal) null else current.leaseExpiresAt,
            ownerToken = if (terminal) null else lease.ownerToken,
            finishedAt = if (terminal) now else null,
        )
    }

    private fun isAdmissible(
        result: SourceUpdateProbeResult,
        identity: BookIdentity,
        expectedPreviousAnchor: String?,
    ): Boolean {
        if (result.identity != identity || result.previousAnchor != expectedPreviousAnchor) return false
        return when (result.outcome) {
            SourceUpdateOutcome.UNCHANGED -> true
            SourceUpdateOutcome.UPDATED -> result.anchor != expectedPreviousAnchor
            SourceUpdateOutcome.UNAVAILABLE,
            SourceUpdateOutcome.FAILED -> true
        }
    }

    private fun SourceUpdateProbeResult.toBaseline(now: Long) = UpdateBaselineEntity(
        sourceId = identity.sourceId,
        remoteBookId = identity.remoteBookId,
        anchor = requireNotNull(anchor),
        chaptersJson = encodeChapters(chapters),
        lastUpdatedDate = lastUpdatedDate,
        updatedAt = now,
    )

    private fun UpdateSessionEntity.toSummary() = UpdateSessionSummary(
        id = id,
        state = state,
        total = total,
        completed = completed,
        updated = updated,
        failed = failed,
        reason = reason,
        finishedAt = finishedAt,
    )

    private fun UpdateSessionItemEntity.toSummaryOrNull(): UpdateSessionItemSummary? = runCatching {
        UpdateSessionItemSummary(
            identity = BookIdentity(sourceId, remoteBookId),
            title = capturedTitle,
            state = state,
            reason = reason,
            anchor = anchor,
        )
    }.getOrNull()

    private fun UpdatePolicyEntity.toDomain() = UpdatePolicy(
        cadence = runCatching { UpdateCadence.valueOf(cadence) }.getOrDefault(UpdateCadence.OFF),
        unmeteredOnly = unmeteredOnly,
        requiresCharging = requiresCharging,
        batteryNotLow = batteryNotLow,
    )

    private fun UpdatePolicy.toEntity() = UpdatePolicyEntity(
        id = POLICY_ID,
        cadence = cadence.name,
        unmeteredOnly = unmeteredOnly,
        requiresCharging = requiresCharging,
        batteryNotLow = batteryNotLow,
    )

    private fun UpdateSessionCounts.reason(): String? = buildList {
        if (skipped != 0) add("skipped=$skipped")
        if (cancelled != 0) add("cancelled=$cancelled")
        if (unavailable != 0) add("unavailable=$unavailable")
        if (failed != 0) add("failed=$failed")
    }.joinToString(separator = "; ").ifBlank { null }

    private fun UnresolvedUpdateEntity.toDomainOrNull(): UnresolvedUpdate? = runCatching {
        UnresolvedUpdate(
            identity = BookIdentity(sourceId, remoteBookId),
            title = title,
            anchor = anchor,
            chapters = decodeChapters(chaptersJson),
            newChapterIds = newChapterIds(),
            lastUpdatedDate = lastUpdatedDate,
            detectedAt = detectedAt,
        )
    }.getOrNull()

    private fun UnresolvedUpdateEntity.newChapterIds(): List<String> = decodeIds(newChapterIdsJson)

    private fun UpdateUndoEntity.toUnresolvedEntity() = UnresolvedUpdateEntity(
        sourceId = sourceId,
        remoteBookId = remoteBookId,
        title = title,
        anchor = anchor,
        chaptersJson = chaptersJson,
        newChapterIdsJson = newChapterIdsJson,
        lastUpdatedDate = lastUpdatedDate,
        detectedAt = detectedAt,
        revision = revision,
    )

    private data class VisibleSessionItems(val entities: List<UpdateSessionItemEntity>, val hasMore: Boolean)
    private data class SnapshotPages(
        val updates: List<UnresolvedUpdateEntity>,
        val items: VisibleSessionItems,
        val totalItems: Int,
    )
    private data class PersistentState(
        val session: UpdateSessionSummary?,
        val policy: UpdatePolicy,
        val excludedBooks: Set<BookIdentity>,
        val excludedSources: Set<String>,
    )

    private companion object {
        const val POLICY_ID = "default"
        const val ITEM_PENDING = "PENDING"
        const val ITEM_FAILED = "FAILED"
        const val ITEM_SKIPPED = "SKIPPED"
        const val ITEM_EXCLUDED_REASON = "excluded"
        const val ITEM_INELIGIBLE_REASON = "ineligible"
        const val INVALID_PROBE_RESULT = "invalid-probe-result"
        const val PROBE_FAILED = "probe-failed"
        const val INITIAL_ITEM_PAGE_SIZE = 100
        const val ITEM_PAGE_SIZE = 100
        const val MAX_CANDIDATE_PAGE_SIZE = 128
        const val UNDO_DURATION_MILLIS = 30_000L
    }
}

private fun encodeChapters(chapters: List<SourceUpdateChapter>): String = buildJsonArray {
    chapters.forEach { chapter ->
        add(JsonObject(mapOf("id" to JsonPrimitive(chapter.chapterId), "title" to JsonPrimitive(chapter.title))))
    }
}.toString()

private fun decodeChapters(encoded: String): List<SourceUpdateChapter> = runCatching {
    Json.parseToJsonElement(encoded).jsonArray.map { entry ->
        val objectValue = entry.jsonObject
        SourceUpdateChapter(
            chapterId = objectValue.getValue("id").jsonPrimitive.content,
            title = objectValue.getValue("title").jsonPrimitive.content,
        )
    }
}.getOrDefault(emptyList())

private fun encodeIds(ids: List<String>): String = JsonArray(ids.map(::JsonPrimitive)).toString()

private fun decodeIds(encoded: String): List<String> = runCatching {
    Json.parseToJsonElement(encoded).jsonArray.map { it.jsonPrimitive.content }
}.getOrDefault(emptyList())
