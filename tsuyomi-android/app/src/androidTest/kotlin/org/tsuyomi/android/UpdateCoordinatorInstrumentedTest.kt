/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import java.time.Instant
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.database.RoomUpdateStore
import org.tsuyomi.core.database.TsuyomiDatabase
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.library.UpdateCoordinator
import org.tsuyomi.shared.librarydomain.UpdateCandidate
import org.tsuyomi.shared.librarydomain.UpdateProbe
import org.tsuyomi.shared.librarydomain.UpdateRunOutcome
import org.tsuyomi.shared.librarydomain.UpdateSessionStates
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceUpdateChapter
import org.tsuyomi.shared.sourcecontract.SourceUpdateOutcome
import org.tsuyomi.shared.sourcecontract.SourceUpdateProbeResult

/** Real Room/coordinator lifecycle tests; signed transport and visible routes have separate owners. */
@RunWith(AndroidJUnit4::class)
class UpdateCoordinatorInstrumentedTest {
    @Test
    fun stale_source_result_terminalizes_once_without_baseline_or_reprobe() = runBlocking {
        val database = database()
        try {
            val store = RoomUpdateStore(database)
            val candidate = candidate("stale")
            addLibraryBooks(database, candidate)
            var checks = 0
            val coordinator = UpdateCoordinator(store, object : UpdateProbe {
                override suspend fun check(candidate: UpdateCandidate, previousAnchor: String?): SourceUpdateProbeResult {
                    checks++
                    return baseline(candidate.identity, previousAnchor)
                }
                override suspend fun isStillValid(candidate: UpdateCandidate) = false
            }, { listOf(candidate, candidate) })

            withTimeout(10_000) { coordinator.run() }

            assertEquals(1, checks)
            assertNull(store.previousAnchor(candidate.identity))
            val snapshot = store.snapshots.first()
            assertEquals(UpdateSessionStates.FAILED, snapshot.session?.state)
            assertEquals(1, snapshot.session?.completed)
            assertEquals("stale-source-lease", snapshot.sessionItems.single().reason)
            assertTrue(snapshot.updates.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun interrupted_run_resumes_only_pending_items_and_keeps_original_capture() = runBlocking {
        val database = database()
        try {
            val store = RoomUpdateStore(database)
            val first = candidate("first")
            val second = candidate("second")
            addLibraryBooks(database, first, second)
            val enteredSecond = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            var interruptSecond = true
            var captures = 0
            val coordinator = UpdateCoordinator(store, object : UpdateProbe {
                override suspend fun check(candidate: UpdateCandidate, previousAnchor: String?): SourceUpdateProbeResult {
                    calls += candidate.identity.remoteBookId
                    if (candidate.identity == second.identity && interruptSecond) {
                        enteredSecond.complete(Unit)
                        awaitCancellation()
                    }
                    return baseline(candidate.identity, previousAnchor)
                }
            }, {
                captures++
                listOf(first, second)
            })
            val running = async { coordinator.run() }
            withTimeout(10_000) { enteredSecond.await() }
            val originalSession = store.snapshots.first().session!!.id
            running.cancelAndJoin()
            assertEquals(UpdateSessionStates.QUEUED, store.snapshots.first().session?.state)
            assertEquals(1, store.snapshots.first().session?.completed)

            interruptSecond = false
            withTimeout(10_000) { coordinator.run() }

            assertEquals(listOf("first", "second", "second"), calls)
            assertEquals(1, captures)
            val recovered = store.snapshots.first()
            assertEquals(originalSession, recovered.session?.id)
            assertEquals(UpdateSessionStates.COMPLETED, recovered.session?.state)
            assertEquals(2, recovered.session?.completed)
            assertTrue(recovered.sessionItems.all { it.state == "UNCHANGED" })
        } finally {
            database.close()
        }
    }

    @Test
    fun concurrent_manual_and_scheduled_runs_do_not_queue_a_second_scan() = runBlocking {
        val database = database()
        try {
            val store = RoomUpdateStore(database)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val candidate = candidate("deduplicated")
            addLibraryBooks(database, candidate)
            var checks = 0
            val probe = object : UpdateProbe {
                override suspend fun check(candidate: UpdateCandidate, previousAnchor: String?): SourceUpdateProbeResult {
                    checks++
                    entered.complete(Unit)
                    release.await()
                    return baseline(candidate.identity, previousAnchor)
                }
            }
            val first = UpdateCoordinator(store, probe, { listOf(candidate) })
            val second = UpdateCoordinator(store, probe, { listOf(candidate) })
            val running = async { first.runDetailed("manual") }
            try {
                withTimeout(10_000) { entered.await() }
                assertEquals(UpdateRunOutcome.COALESCED, first.runDetailed("scheduled"))
                assertEquals(UpdateRunOutcome.BUSY, second.runDetailed("scheduled"))
            } finally {
                release.complete(Unit)
                withTimeout(10_000) { running.await() }
            }
            assertEquals(1, checks)
            assertEquals(1, store.snapshots.first().session?.completed)
        } finally {
            database.close()
        }
    }

    @Test
    fun long_probe_keeps_durable_lease_until_commit() = runBlocking {
        val database = database()
        try {
            val store = RoomUpdateStore(database)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val candidate = candidate("long-probe")
            addLibraryBooks(database, candidate)
            var checks = 0
            val probe = object : UpdateProbe {
                override suspend fun check(candidate: UpdateCandidate, previousAnchor: String?): SourceUpdateProbeResult {
                    checks++
                    if (checks == 1) {
                        entered.complete(Unit)
                        release.await()
                    }
                    return baseline(candidate.identity, previousAnchor)
                }
            }
            fun coordinator() = UpdateCoordinator(
                store = store,
                probe = probe,
                candidates = { listOf(candidate) },
                leaseDurationMillis = 120L,
                leaseHeartbeatMillis = 30L,
            )
            val first = coordinator()
            val running = async { first.runDetailed("manual") }
            try {
                withTimeout(10_000) { entered.await() }
                delay(250L)
                assertEquals(UpdateRunOutcome.BUSY, coordinator().runDetailed("scheduled"))
            } finally {
                release.complete(Unit)
                withTimeout(10_000) { running.await() }
            }
            assertEquals(1, checks)
            assertEquals(UpdateSessionStates.COMPLETED, store.snapshots.first().session?.state)
        } finally {
            database.close()
        }
    }

    @Test
    fun execution_prerequisite_deferral_keeps_pending_and_fatal_failure_is_not_user_cancel() = runBlocking {
        val database = database()
        try {
            val store = RoomUpdateStore(database)
            val candidate = candidate("foreground")
            addLibraryBooks(database, candidate)
            var checks = 0
            val coordinator = UpdateCoordinator(store, object : UpdateProbe {
                override suspend fun check(candidate: UpdateCandidate, previousAnchor: String?): SourceUpdateProbeResult {
                    checks++
                    return baseline(candidate.identity, previousAnchor)
                }
            }, { listOf(candidate) })

            coordinator.runDetailed(onSessionStarted = { false })
            val deferred = store.snapshots.first()
            assertEquals(UpdateSessionStates.QUEUED, deferred.session?.state)
            assertEquals("PENDING", deferred.sessionItems.single().state)
            assertEquals(0, checks)

            coordinator.runDetailed(onSessionStarted = { lease ->
                coordinator.failPending(lease, "foreground-security-blocked")
                false
            })
            val failed = store.snapshots.first()
            assertEquals(deferred.session?.id, failed.session?.id)
            assertEquals(UpdateSessionStates.FAILED, failed.session?.state)
            assertEquals("foreground-security-blocked", failed.sessionItems.single().reason)
            assertEquals(0, checks)
            assertNull(store.previousAnchor(candidate.identity))
        } finally {
            database.close()
        }
    }

    private suspend fun addLibraryBooks(database: TsuyomiDatabase, vararg candidates: UpdateCandidate) {
        val repository = RoomLibraryRepository(database)
        for (candidate in candidates) {
            repository.addToLibrary(LibraryBook(
                identity = candidate.identity,
                title = candidate.title,
                addedAt = Instant.EPOCH,
                metadataUpdatedAt = Instant.EPOCH,
            ))
        }
    }

    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        TsuyomiDatabase::class.java,
    ).build()

    private fun candidate(id: String) = UpdateCandidate(BookIdentity("fixture.update.coordinator", id), id)

    private fun baseline(identity: BookIdentity, previousAnchor: String?) = SourceUpdateProbeResult(
        identity = identity,
        sourceVersion = "1.0.0",
        packageSha256 = "a".repeat(64),
        checkedAt = System.currentTimeMillis(),
        outcome = SourceUpdateOutcome.UNCHANGED,
        previousAnchor = previousAnchor,
        anchor = "update-check-v2.1." + "b".repeat(64),
        chapters = listOf(SourceUpdateChapter("chapter-1", "第一章")),
        newChapterIds = emptyList(),
        lastUpdatedDate = null,
        reason = null,
    )
}
