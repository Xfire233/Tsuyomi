/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.library

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.tsuyomi.shared.librarydomain.UpdateCandidate
import org.tsuyomi.shared.librarydomain.UpdatePolicy
import org.tsuyomi.shared.librarydomain.UpdateProbe
import org.tsuyomi.shared.librarydomain.UpdateSnapshot
import org.tsuyomi.shared.librarydomain.UpdateStore
import org.tsuyomi.shared.librarydomain.UpdateUndo
import org.tsuyomi.shared.librarydomain.UpdateSessionLease
import org.tsuyomi.shared.librarydomain.UpdateRunOutcome
import org.tsuyomi.shared.model.BookIdentity

/**
 * Serializes source checks through one durable session lane. The store remains the authority for
 * cross-process deduplication, expiry recovery, cancellation, and every commit boundary.
 */
class UpdateCoordinator(
    private val store: UpdateStore,
    private val probe: UpdateProbe,
    private val candidates: suspend () -> List<UpdateCandidate>,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sessionId: () -> String = { UUID.randomUUID().toString() },
    private val leaseDurationMillis: Long = DEFAULT_LEASE_DURATION_MILLIS,
    private val leaseHeartbeatMillis: Long = leaseDurationMillis / 3,
) {
    init {
        require(leaseDurationMillis > 0) { "Update lease duration must be positive" }
        require(leaseHeartbeatMillis in 1 until leaseDurationMillis) {
            "Update lease heartbeat must occur before expiry"
        }
    }
    val snapshots: Flow<UpdateSnapshot> = store.snapshots.onStart { store.recoverExpiredSessions(clock()) }

    private val sourceLane = Mutex()

    suspend fun run(trigger: String = "manual") {
        runDetailed(trigger)
    }


    suspend fun runDetailed(
        trigger: String = "manual",
        onSessionStarted: suspend (UpdateSessionLease) -> Boolean = { true },
    ): UpdateRunOutcome {
        if (!sourceLane.tryLock()) return UpdateRunOutcome.COALESCED
        try {
            val now = clock()
            store.recoverExpiredSessions(now)
            val leaseExpiresAt = now + leaseDurationMillis
            val lease = store.claimQueuedSession(now, leaseExpiresAt) ?: store.startSession(
                id = sessionId(),
                trigger = trigger,
                candidates = captureCandidates(),
                now = now,
                leaseExpiresAt = leaseExpiresAt,
            ) ?: return UpdateRunOutcome.BUSY

            try {
                if (!onSessionStarted(lease)) {
                    store.relinquishLease(lease, clock())
                    return UpdateRunOutcome.RELINQUISHED
                }
                while (true) {
                    val page = store.pendingCandidates(lease, CANDIDATE_PAGE_SIZE)
                    if (page.isEmpty()) return UpdateRunOutcome.COMPLETED
                    for (candidate in page) {
                        val beforeProbe = clock()
                        if (!store.renewLease(lease, beforeProbe, beforeProbe + leaseDurationMillis)) {
                            return UpdateRunOutcome.RELINQUISHED
                        }
                        if (!store.canProbe(lease, candidate, beforeProbe)) {
                            if (!store.recordSkipped(lease, candidate, EXCLUDED_OR_INELIGIBLE, beforeProbe)) {
                                return UpdateRunOutcome.RELINQUISHED
                            }
                            continue
                        }

                        val result = try {
                            probeWithLeaseHeartbeat(lease, candidate) ?: return UpdateRunOutcome.RELINQUISHED
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            if (!store.recordFailure(lease, candidate, PROBE_FAILED, clock())) {
                                return UpdateRunOutcome.RELINQUISHED
                            }
                            continue
                        }

                        if (!isStillValid(lease, candidate)) continue
                        val beforeCommit = clock()
                        if (!store.renewLease(lease, beforeCommit, beforeCommit + leaseDurationMillis)) {
                            return UpdateRunOutcome.RELINQUISHED
                        }
                        if (!isStillValid(lease, candidate)) continue
                        store.recordProbe(lease, candidate, result, beforeCommit)
                    }
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { store.relinquishLease(lease, clock()) }
                throw cancelled
            } catch (failure: Throwable) {
                withContext(NonCancellable) { store.relinquishLease(lease, clock()) }
                throw failure
            }
        } finally {
            sourceLane.unlock()
        }
    }

    /** Durable cancellation is visible before the active source call is allowed to stop. */
    suspend fun cancel() {
        store.cancelActiveSession(clock())
    }
    suspend fun cancel(expectedSessionId: String): Boolean = store.cancelSession(expectedSessionId, clock())
    suspend fun cancel(expectedLease: UpdateSessionLease): Boolean = store.cancelLease(expectedLease, clock())

    /** Reports an unrecoverable execution prerequisite failure without calling it user cancellation. */
    suspend fun failPending(lease: UpdateSessionLease, reason: String) {
        while (true) {
            val pending = store.pendingCandidates(lease, CANDIDATE_PAGE_SIZE)
            if (pending.isEmpty()) return
            for (candidate in pending) {
                if (!store.recordFailure(lease, candidate, reason, clock())) return
            }
        }
    }

    suspend fun loadMoreSessionItems(): Boolean = store.loadMoreSessionItems()

    suspend fun ignore(identity: BookIdentity, anchor: String): UpdateUndo? =
        store.ignore(identity, anchor)

    suspend fun undo(token: UpdateUndo) {
        store.undo(token)
    }

    suspend fun reconcileCompleted(identity: BookIdentity, chapterIds: Set<String>) {
        store.reconcileCompleted(identity, chapterIds)
    }

    suspend fun setPolicy(policy: UpdatePolicy) {
        store.setPolicy(policy)
    }

    private suspend fun probeWithLeaseHeartbeat(
        lease: UpdateSessionLease,
        candidate: UpdateCandidate,
    ) = try {
        coroutineScope {
            val heartbeat = launch {
                while (true) {
                    delay(leaseHeartbeatMillis)
                    val now = clock()
                    if (!store.renewLease(lease, now, now + leaseDurationMillis)) {
                        throw UpdateLeaseLostException()
                    }
                }
            }
            try {
                probe.check(candidate, store.previousAnchor(candidate.identity))
            } finally {
                heartbeat.cancelAndJoin()
            }
        }
    } catch (_: UpdateLeaseLostException) {
        null
    }

    suspend fun excludeBook(identity: BookIdentity, excluded: Boolean) {
        store.excludeBook(identity, excluded)
    }

    suspend fun excludeSource(sourceId: String, excluded: Boolean) {
        store.excludeSource(sourceId, excluded)
    }

    private suspend fun isStillValid(lease: UpdateSessionLease, candidate: UpdateCandidate): Boolean =
        try {
            if (probe.isStillValid(candidate)) {
                true
            } else {
                store.recordFailure(lease, candidate, STALE_SOURCE_LEASE, clock())
                false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            store.recordFailure(lease, candidate, STALE_SOURCE_LEASE, clock())
            false
        }

    private suspend fun captureCandidates(): List<UpdateCandidate> {
        val unique = LinkedHashMap<BookIdentity, UpdateCandidate>()
        candidates().forEach { candidate -> unique.putIfAbsent(candidate.identity, candidate) }
        return unique.values.toList()
    }

    private class UpdateLeaseLostException : RuntimeException()

    private companion object {
        const val DEFAULT_LEASE_DURATION_MILLIS = 2 * 60 * 1000L
        const val EXCLUDED_OR_INELIGIBLE = "excluded-or-ineligible"
        const val PROBE_FAILED = "probe-failed"
        const val STALE_SOURCE_LEASE = "stale-source-lease"
        const val CANDIDATE_PAGE_SIZE = 32
    }
}
