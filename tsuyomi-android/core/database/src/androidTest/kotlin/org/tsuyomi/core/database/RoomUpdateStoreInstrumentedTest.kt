/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import java.time.Instant
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.shared.librarydomain.UpdateCandidate
import org.tsuyomi.shared.librarydomain.UpdateSessionStates
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceUpdateChapter
import org.tsuyomi.shared.sourcecontract.SourceUpdateOutcome
import org.tsuyomi.shared.sourcecontract.SourceUpdateProbeResult

@RunWith(AndroidJUnit4::class)
class RoomUpdateStoreInstrumentedTest {
    @Test
    fun ignore_and_undo_are_exact_anchor_compare_and_set_operations() = runBlocking {
        var now = 1_000L
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TsuyomiDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val store = RoomUpdateStore(database) { now }
            val candidate = UpdateCandidate(BookIdentity("fixture.source", "book-42"), "测试书")
            addLibraryBook(database, candidate)

            val initial = requireNotNull(store.startSession("s1", "manual", listOf(candidate), now, now + 1_000L))
            assertEquals(
                true,
                store.recordProbe(
                    initial,
                    candidate,
                    result(
                        outcome = SourceUpdateOutcome.UNCHANGED,
                        previousAnchor = null,
                        anchor = anchorOne,
                        chapters = listOf(SourceUpdateChapter("chapter-1", "第一章")),
                    ),
                    now,
                ),
            )

            now += 1
            val update = requireNotNull(store.startSession("s2", "manual", listOf(candidate), now, now + 1_000L))
            assertEquals(
                true,
                store.recordProbe(
                    update,
                    candidate,
                    result(
                        outcome = SourceUpdateOutcome.UPDATED,
                        previousAnchor = anchorOne,
                        anchor = anchorTwo,
                        chapters = listOf(
                            SourceUpdateChapter("chapter-1", "第一章"),
                            SourceUpdateChapter("chapter-2", "第二章"),
                        ),
                        newChapterIds = listOf("chapter-2"),
                    ),
                    now,
                ),
            )

            assertNull(store.ignore(candidate.identity, anchorOne))
            val undo = requireNotNull(store.ignore(candidate.identity, anchorTwo))

            now += 1
            val laterUpdate = requireNotNull(store.startSession("s3", "manual", listOf(candidate), now, now + 1_000L))
            assertEquals(
                true,
                store.recordProbe(
                    laterUpdate,
                    candidate,
                    result(
                        outcome = SourceUpdateOutcome.UPDATED,
                        previousAnchor = anchorTwo,
                        anchor = anchorThree,
                        chapters = listOf(
                            SourceUpdateChapter("chapter-1", "第一章"),
                            SourceUpdateChapter("chapter-2", "第二章"),
                            SourceUpdateChapter("chapter-3", "第三章"),
                        ),
                        newChapterIds = listOf("chapter-3"),
                    ),
                    now,
                ),
            )

            assertFalse(store.undo(undo))
            val updateState = store.snapshots.first().updates.single()
            assertEquals(anchorThree, updateState.anchor)
            assertEquals(listOf("chapter-3"), updateState.newChapterIds)
        } finally {
            database.close()
        }
    }

    @Test
    fun trusted_appends_merge_until_every_admitted_chapter_is_complete() = runBlocking {
        var now = 2_000L
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TsuyomiDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val store = RoomUpdateStore(database) { now }
            val candidate = UpdateCandidate(BookIdentity("fixture.source", "book-merge"), "合并测试")
            addLibraryBook(database, candidate)

            suspend fun record(sessionId: String, previous: String?, anchor: String, newIds: List<String>) {
                val lease = requireNotNull(store.startSession(sessionId, "manual", listOf(candidate), now, now + 1_000L))
                val chapters = listOf(
                    SourceUpdateChapter("chapter-1", "第一章"),
                    SourceUpdateChapter("chapter-2", "第二章"),
                    SourceUpdateChapter("chapter-3", "第三章"),
                ).take(if (anchor == anchorOne) 1 else if (anchor == anchorTwo) 2 else 3)
                assertTrue(
                    store.recordProbe(
                        lease,
                        candidate,
                        result(
                            identity = candidate.identity,
                            outcome = if (previous == null) SourceUpdateOutcome.UNCHANGED else SourceUpdateOutcome.UPDATED,
                            previousAnchor = previous,
                            anchor = anchor,
                            chapters = chapters,
                            newChapterIds = newIds,
                        ),
                        now,
                    ),
                )
                now++
            }

            record("merge-1", null, anchorOne, emptyList())
            record("merge-2", anchorOne, anchorTwo, listOf("chapter-2"))
            record("merge-3", anchorTwo, anchorThree, listOf("chapter-3"))
            assertEquals(listOf("chapter-2", "chapter-3"), store.snapshots.first().updates.single().newChapterIds)

            addCompletedChapter(database, candidate.identity, "chapter-3", now)
            store.reconcileCompleted(candidate.identity, setOf("chapter-3"))
            assertEquals(1, store.snapshots.first().updates.size)

            addCompletedChapter(database, candidate.identity, "chapter-2", now + 1)
            store.reconcileCompleted(candidate.identity, setOf("chapter-2", "chapter-3"))
            assertTrue(store.snapshots.first().updates.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun expired_lease_queues_pending_work_fences_old_owner_and_terminalizes_cancellation() = runBlocking {
        var now = 1_000L
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TsuyomiDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val store = RoomUpdateStore(database) { now }
            val first = UpdateCandidate(BookIdentity("fixture.source", "book-1"), "第一本")
            val second = UpdateCandidate(BookIdentity("fixture.source", "book-2"), "第二本")
            addLibraryBook(database, first)
            addLibraryBook(database, second)
            val firstLease = requireNotNull(store.startSession("recover", "manual", listOf(first, second), now, now + 10L))
            assertTrue(
                store.recordProbe(
                    firstLease,
                    first,
                    result(
                        identity = first.identity,
                        outcome = SourceUpdateOutcome.UNCHANGED,
                        previousAnchor = null,
                        anchor = anchorOne,
                        chapters = listOf(SourceUpdateChapter("chapter-1", "第一章")),
                    ),
                    now,
                ),
            )

            now += 11L
            store.recoverExpiredSessions(now)
            assertEquals(UpdateSessionStates.QUEUED, requireNotNull(store.snapshots.first().session).state)
            val recovered = requireNotNull(store.startSession("ignored", "manual", listOf(first), now, now + 1_000L))
            assertEquals("recover", recovered.id)
            assertFalse(recovered.ownerToken == firstLease.ownerToken)
            assertEquals(listOf(second.identity), store.pendingCandidates(recovered, 8).map(UpdateCandidate::identity))
            assertFalse(store.recordFailure(firstLease, second, "stale-source-lease", now))
            assertTrue(
                store.recordProbe(
                    recovered,
                    second,
                    result(
                        identity = second.identity,
                        outcome = SourceUpdateOutcome.UNCHANGED,
                        previousAnchor = null,
                        anchor = anchorOne,
                        chapters = listOf(SourceUpdateChapter("chapter-1", "第一章")),
                    ),
                    now,
                ),
            )
            assertEquals(UpdateSessionStates.COMPLETED, requireNotNull(store.snapshots.first().session).state)

            val failed = UpdateCandidate(BookIdentity("fixture.source", "book-3"), "失败书")
            val failedLease = requireNotNull(store.startSession("failed", "manual", listOf(failed), now, now + 1_000L))
            assertTrue(store.recordFailure(failedLease, failed, "probe-failed", now))
            assertEquals(UpdateSessionStates.FAILED, requireNotNull(store.snapshots.first().session).state)

            val cancelA = UpdateCandidate(BookIdentity("fixture.source", "book-4"), "取消一")
            val cancelB = UpdateCandidate(BookIdentity("fixture.source", "book-5"), "取消二")
            val relinquished = requireNotNull(store.startSession("cancel", "manual", listOf(cancelA, cancelB), now, now + 1_000L))
            assertTrue(store.relinquishLease(relinquished, now))
            val queued = store.snapshots.first()
            assertEquals(UpdateSessionStates.QUEUED, requireNotNull(queued.session).state)
            assertEquals(2, queued.sessionItems.count { it.state == "PENDING" })
            val cancelLease = requireNotNull(store.claimQueuedSession(now, now + 1_000L))
            assertFalse(cancelLease.ownerToken == relinquished.ownerToken)
            assertTrue(store.cancelLease(cancelLease, now))
            val cancelled = store.snapshots.first()
            assertEquals(UpdateSessionStates.CANCELLED, requireNotNull(cancelled.session).state)
            assertEquals(2, requireNotNull(cancelled.session).completed)
            assertTrue(cancelled.sessionItems.all { it.state == "CANCELLED" })
        } finally {
            database.close()
        }
    }

    @Test
    fun cancelling_queued_work_terminalizes_pending_items_and_prevents_resume() = runBlocking {
        val now = 1_000L
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TsuyomiDatabase::class.java,
        ).build()
        try {
            val store = RoomUpdateStore(database) { now }
            val candidate = UpdateCandidate(BookIdentity("fixture.source", "queued"), "等待检查")
            val lease = requireNotNull(store.startSession("queued-cancel", "manual", listOf(candidate), now, now + 1_000))
            assertTrue(store.relinquishLease(lease, now))
            assertFalse(store.cancelLease(lease, now))

            assertTrue(store.cancelActiveSession(now))

            val cancelled = store.snapshots.first()
            assertEquals(UpdateSessionStates.CANCELLED, cancelled.session?.state)
            assertEquals("CANCELLED", cancelled.sessionItems.single().state)
            assertEquals(1, cancelled.session?.completed)
            store.recoverExpiredSessions(now + 2_000)
            assertNull(store.claimQueuedSession(now + 2_000, now + 3_000))
        } finally {
            database.close()
        }
    }

    @Test
    fun removing_last_eligible_membership_discards_an_inflight_update() = runBlocking {
        val now = 1_000L
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TsuyomiDatabase::class.java,
        ).build()
        try {
            val candidate = UpdateCandidate(BookIdentity("fixture.source", "removed"), "已移出的书")
            addLibraryBook(database, candidate)
            val store = RoomUpdateStore(database) { now }
            val lease = requireNotNull(store.startSession("removed", "manual", listOf(candidate), now, now + 1_000))
            assertTrue(store.canProbe(lease, candidate, now))
            val repository = RoomLibraryRepository(database)
            repository.setSourceAvailability(candidate.identity.sourceId, "1.0.0", true, 1L)
            repository.ensureRemoteMirrorBinding(candidate.identity.sourceId, "测试来源")
            repository.removeFromLibrary(candidate.identity)
            assertFalse(store.canProbe(lease, candidate, now))
            repository.upsertRemoteMirrorBook(requireNotNull(repository.book(candidate.identity)), targetId = null)
            assertTrue(store.canProbe(lease, candidate, now))
            repository.removeRemoteMirrorBook(candidate.identity)

            assertFalse(store.canProbe(lease, candidate, now))
            assertFalse(store.recordProbe(lease, candidate, result(
                identity = candidate.identity,
                outcome = SourceUpdateOutcome.UNCHANGED,
                previousAnchor = null,
                anchor = anchorOne,
                chapters = listOf(SourceUpdateChapter("chapter-1", "第一章")),
            ), now))
            assertNull(store.previousAnchor(candidate.identity))
            assertEquals("SKIPPED", store.snapshots.first().sessionItems.single().state)
        } finally {
            database.close()
        }
    }

    @Test
    fun new_session_resets_bounded_report_page() = runBlocking {
        var now = 1_000L
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TsuyomiDatabase::class.java,
        ).build()
        try {
            val store = RoomUpdateStore(database) { now }
            val candidates = (1..150).map { index ->
                UpdateCandidate(BookIdentity("fixture.source", "book-$index"), "书籍 $index")
            }
            val firstLease = requireNotNull(
                store.startSession("first-page", "manual", candidates, now, now + 1_000L),
            )
            assertEquals(100, store.snapshots.first { it.session?.id == "first-page" }.sessionItems.size)
            assertTrue(store.loadMoreSessionItems())
            assertEquals(150, store.snapshots.first { it.sessionItems.size == 150 }.sessionItems.size)
            assertTrue(store.cancelLease(firstLease, now))

            now += 1
            requireNotNull(store.startSession("second-page", "manual", candidates, now, now + 1_000L))
            val second = store.snapshots.first { it.session?.id == "second-page" }
            assertEquals(100, second.sessionItems.size)
            assertTrue(second.hasMoreSessionItems)
        } finally {
            database.close()
        }
    }

    private suspend fun addLibraryBook(database: TsuyomiDatabase, candidate: UpdateCandidate) {
        RoomLibraryRepository(database).addToLibrary(org.tsuyomi.shared.librarydomain.LibraryBook(
            identity = candidate.identity,
            title = candidate.title,
            addedAt = Instant.EPOCH,
            metadataUpdatedAt = Instant.EPOCH,
        ))
    }
    private fun addCompletedChapter(
        database: TsuyomiDatabase,
        identity: BookIdentity,
        chapterId: String,
        completedAt: Long,
    ) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO completed_chapters(source_id, remote_book_id, chapter_id, completed_at_epoch_second, completed_at_nano) VALUES (?, ?, ?, ?, 0)",
            arrayOf<Any>(identity.sourceId, identity.remoteBookId, chapterId, completedAt),
        )
    }


    private companion object {
        val anchorOne = "update-check-v2.1." + "a".repeat(64)
        val anchorTwo = "update-check-v2.2." + "b".repeat(64)
        val anchorThree = "update-check-v2.3." + "c".repeat(64)
    }

    private fun result(
        identity: BookIdentity = BookIdentity("fixture.source", "book-42"),
        outcome: SourceUpdateOutcome,
        previousAnchor: String?,
        anchor: String?,
        chapters: List<SourceUpdateChapter>,
        newChapterIds: List<String> = emptyList(),
    ) = SourceUpdateProbeResult(
        identity = identity,
        sourceVersion = "1",
        packageSha256 = "a".repeat(64),
        checkedAt = 1_000L,
        outcome = outcome,
        previousAnchor = previousAnchor,
        anchor = anchor,
        chapters = chapters,
        newChapterIds = newChapterIds,
        lastUpdatedDate = null,
        reason = null,
    )
}
