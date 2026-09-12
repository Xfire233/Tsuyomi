/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.librarydomain

import kotlinx.coroutines.flow.Flow
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceUpdateChapter
import org.tsuyomi.shared.sourcecontract.SourceUpdateProbeResult

/** A local or enabled-mirror book captured at the beginning of an update session. */
data class UpdateCandidate(
    val identity: BookIdentity,
    val title: String,
    val eligible: Boolean = true,
    val reason: String? = null,
)

enum class UpdateCadence {
    OFF,
    HOURS_12,
    DAILY,
    DAYS_3,
    WEEKLY,
}

data class UpdatePolicy(
    val cadence: UpdateCadence = UpdateCadence.OFF,
    val unmeteredOnly: Boolean = false,
    val requiresCharging: Boolean = false,
    val batteryNotLow: Boolean = true,
)

/** Exact unresolved chapter delta for one current trusted source anchor. */
data class UnresolvedUpdate(
    val identity: BookIdentity,
    val title: String,
    val anchor: String,
    val chapters: List<SourceUpdateChapter>,
    val newChapterIds: List<String>,
    val lastUpdatedDate: String?,
    val detectedAt: Long,
)

data class UpdateSessionSummary(
    val id: String,
    val state: String,
    val total: Int,
    val completed: Int,
    val updated: Int,
    val failed: Int,
    val finishedAt: Long?,
    val reason: String? = null,
)
data class UpdateSessionItemSummary(
    val identity: BookIdentity,
    val title: String,
    val state: String,
    val reason: String?,
    val anchor: String?,
)


object UpdateSessionStates {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val COMPLETED = "COMPLETED"
    const val PARTIAL = "PARTIAL"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}
enum class UpdateRunOutcome {
    COMPLETED,
    /** A durable owner may be orphaned; retry after its lease can be recovered. */
    BUSY,
    RELINQUISHED,
    /** This process already owns the scan; finish this request without a follow-on retry. */
    COALESCED,
}


data class UpdateSnapshot(
    val updates: List<UnresolvedUpdate>,
    val session: UpdateSessionSummary?,
    val policy: UpdatePolicy,
    val excludedBooks: Set<BookIdentity>,
    val excludedSources: Set<String>,
    val sessionItems: List<UpdateSessionItemSummary> = emptyList(),
    val totalSessionItems: Int = sessionItems.size,
    val hasMoreSessionItems: Boolean = false,
)

/**
 * The source boundary for update checks. Runtime owns source/package/credential lease validation.
 * Returning false from [isStillValid] prevents a checked result from being committed.
 */
interface UpdateProbe {
    suspend fun check(candidate: UpdateCandidate, previousAnchor: String?): SourceUpdateProbeResult

    suspend fun isStillValid(candidate: UpdateCandidate): Boolean = true
}

/** Opaque, short-lived, durable compare-and-set token returned after acknowledgement. */
data class UpdateUndo(val id: String)

/** A fenced durable lease owned by one coordinator invocation. */
data class UpdateSessionLease(
    val id: String,
    val ownerToken: String,
    val expiresAt: Long,
)

/**
 * Durable storage protocol for trusted update baselines, current unresolved batches, bounded Undo,
 * and fenced scan reports. Implementations keep every state transition transactional.
 */
interface UpdateStore {
    val snapshots: Flow<UpdateSnapshot>

    /** Expands the bounded report for the current/recent session without hiding item outcomes. */
    suspend fun loadMoreSessionItems(): Boolean

    /** Queues abandoned running work; terminal sessions are never resumed. */
    suspend fun recoverExpiredSessions(now: Long)

    /** Returns null when another durable running session still owns the lane. */
    suspend fun startSession(
        id: String,
        trigger: String,
        candidates: List<UpdateCandidate>,
        now: Long,
        leaseExpiresAt: Long,
    ): UpdateSessionLease?
    /** Claims only pending work from an expired/recovered session without re-enumerating candidates. */
    suspend fun claimQueuedSession(now: Long, leaseExpiresAt: Long): UpdateSessionLease?


    suspend fun previousAnchor(identity: BookIdentity): String?
    suspend fun renewLease(lease: UpdateSessionLease, now: Long, leaseExpiresAt: Long): Boolean
    suspend fun canProbe(lease: UpdateSessionLease, candidate: UpdateCandidate, now: Long): Boolean
    suspend fun pendingCandidates(lease: UpdateSessionLease, limit: Int): List<UpdateCandidate>


    /** Records a candidate that became ineligible or excluded after the session was captured. */
    suspend fun recordSkipped(lease: UpdateSessionLease, candidate: UpdateCandidate, reason: String, now: Long): Boolean

    /**
     * Commits only if the lease is live and candidate remains eligible. False means the probe result
     * was deliberately discarded and must not be replayed.
     */
    suspend fun recordProbe(
        lease: UpdateSessionLease,
        candidate: UpdateCandidate,
        result: SourceUpdateProbeResult,
        now: Long,
    ): Boolean

    suspend fun recordFailure(lease: UpdateSessionLease, candidate: UpdateCandidate, reason: String?, now: Long): Boolean
    /** Cancels the current running or queued session as an explicit user action. */
    suspend fun cancelActiveSession(now: Long): Boolean
    /** Cancels only the matching fenced lease; stale owners cannot cancel recovered work. */
    suspend fun cancelLease(lease: UpdateSessionLease, now: Long): Boolean
    /** Releases only the matching owner token, retaining pending items for a later claim. */
    suspend fun relinquishLease(lease: UpdateSessionLease, now: Long): Boolean
    /** Atomically cancels only the running or queued session with the advertised identifier. */
    suspend fun cancelSession(expectedSessionId: String, now: Long): Boolean
    suspend fun ignore(identity: BookIdentity, anchor: String): UpdateUndo?
    suspend fun undo(token: UpdateUndo): Boolean
    suspend fun reconcileCompleted(identity: BookIdentity, chapterIds: Set<String>)

    suspend fun setPolicy(policy: UpdatePolicy)
    suspend fun excludeBook(identity: BookIdentity, excluded: Boolean)
    suspend fun excludeSource(sourceId: String, excluded: Boolean)
}
