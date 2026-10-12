/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.database.RoomUpdateStore
import org.tsuyomi.core.library.UpdateCoordinator
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.UpdateRunOutcome
import org.tsuyomi.shared.librarydomain.UpdateSessionStates
import org.tsuyomi.shared.model.BookIdentity

@RunWith(AndroidJUnit4::class)
internal class UpdateSourceResumeInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun resumed_pending_candidate_records_source_unavailable_without_evidence_or_request() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val identity = BookIdentity.Remote(sourceId, "resume-unavailable")
        assertTrue(
            library.addToLibrary(
                LibraryBook(
                    identity = identity,
                    title = "恢复中的远程书",
                    addedAt = Instant.EPOCH,
                    metadataUpdatedAt = Instant.EPOCH,
                ),
            ),
        )

        val captureRuntime = UpdateSourceRuntime(context, library)
        val candidate = captureRuntime.candidates().single { it.identity == identity }
        assertTrue(candidate.eligible)
        val availability = requireNotNull(library.sourceAvailability(sourceId))
        assertTrue(availability.available)

        val store = RoomUpdateStore(database)
        val now = System.currentTimeMillis()
        val sessionLease = requireNotNull(
            store.startSession(
                id = "resume-unavailable",
                trigger = "manual",
                candidates = listOf(candidate),
                now = now,
                leaseExpiresAt = now + 60_000L,
            ),
        )
        assertEquals(listOf(identity), store.pendingCandidates(sessionLease, 8).map { it.identity })
        assertTrue(store.relinquishLease(sessionLease, now))
        assertEquals(UpdateSessionStates.QUEUED, store.snapshots.first().session?.state)

        library.setSourceAvailability(
            sourceId = sourceId,
            version = packageInfo.manifest.version.original,
            available = false,
            generation = availability.generation + 1L,
        )
        val resumedRuntime = UpdateSourceRuntime(context, library)
        Phase2SourceGateway.resetOperationCounts()
        val coordinator = UpdateCoordinator(store, UpdateSourceProbe(context, resumedRuntime), resumedRuntime::candidates)

        assertEquals(UpdateRunOutcome.COMPLETED, withTimeout(10_000L) { coordinator.runDetailed("manual") })

        val snapshot = store.snapshots.first()
        val session = requireNotNull(snapshot.session)
        val item = snapshot.sessionItems.single()
        assertEquals(UpdateSessionStates.PARTIAL, session.state)
        assertEquals(1, session.completed)
        assertEquals(0, session.failed)
        assertEquals("unavailable=1", session.reason)
        assertEquals(identity, item.identity)
        assertEquals("UNAVAILABLE", item.state)
        assertEquals("source-unavailable", item.reason)
        assertNull(item.anchor)
        assertNull(store.previousAnchor(identity))
        assertTrue(snapshot.updates.isEmpty())
        assertEquals(0, Phase2SourceGateway.sourceRequestCount())
        assertEquals(0, Phase2SourceGateway.liveTransportRequestCount())
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())
    }
}
