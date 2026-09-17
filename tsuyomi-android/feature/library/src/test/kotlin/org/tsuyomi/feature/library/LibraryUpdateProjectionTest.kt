/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.librarydomain.ReadingProgress
import org.tsuyomi.shared.librarydomain.UnresolvedUpdate
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceUpdateChapter
import org.tsuyomi.shared.librarydomain.UpdateSessionStates
import org.tsuyomi.shared.librarydomain.UpdateSessionSummary

class LibraryUpdateProjectionTest {
    @Test
    fun updatesOnlyIncludesLocalAndMirrorOnlyUnresolvedBooks() {
        val localUpdated = entry("local-updated")
        val localQuiet = entry("local-quiet")
        val mirrorUpdated = entry("mirror-updated", localMembership = false)
        val updates = listOf(update(localUpdated, detectedAt = 30), update(mirrorUpdated, detectedAt = 20))
            .associateBy(UnresolvedUpdate::identity)

        val projected = LibraryUiState(
            loading = false,
            entries = listOf(localUpdated, localQuiet),
            updateOnlyEntries = listOf(mirrorUpdated),
            updates = updates,
            updateFilter = LibraryUpdateFilter.UPDATES_ONLY,
        ).projectedEntries()

        assertEquals(setOf(localUpdated.book.identity, mirrorUpdated.book.identity), projected.map { it.book.identity }.toSet())
    }

    @Test
    fun smartOrderPinsOnlyTheLastReadUpdatedBookThenUsesPublicationOrder() {
        val quietFirst = entry("quiet-first")
        val datedOlder = entry("dated-older")
        val neverReadNewest = entry("never-read-newest")
        val lastRead = entry("last-read", readAt = Instant.ofEpochSecond(90))
        val quietSecond = entry("quiet-second")
        val updates = listOf(
            update(datedOlder, detectedAt = 200, publicationDate = "2026-09-01"),
            update(neverReadNewest, detectedAt = 100, publicationDate = "2026-09-03"),
            update(lastRead, detectedAt = 50, publicationDate = "2026-08-01"),
        ).associateBy(UnresolvedUpdate::identity)

        val projected = LibraryUiState(
            loading = false,
            entries = listOf(quietFirst, datedOlder, neverReadNewest, lastRead, quietSecond),
            updates = updates,
            sortMode = LibrarySortMode.SMART,
        ).projectedEntries()

        assertEquals(
            listOf("last-read", "never-read-newest", "dated-older", "quiet-first", "quiet-second"),
            projected.map { it.book.identity.remoteBookId },
        )
    }

    @Test
    fun explicitTitleSortDoesNotCreateAnUpdatePartition() {
        val zUpdated = entry("z-updated", title = "终点")
        val aQuiet = entry("a-quiet", title = "岸边")

        val projected = LibraryUiState(
            loading = false,
            entries = listOf(zUpdated, aQuiet),
            updates = mapOf(zUpdated.book.identity to update(zUpdated, detectedAt = 10)),
            sortMode = LibrarySortMode.TITLE,
        ).projectedEntries()

        assertEquals(listOf("a-quiet", "z-updated"), projected.map { it.book.identity.remoteBookId })
    }

    @Test
    fun completedSuccessStripUsesDurableFinishTimeInsteadOfReappearingAfterRestart() {
        val session = UpdateSessionSummary(
            id = "session",
            state = UpdateSessionStates.COMPLETED,
            total = 3,
            completed = 3,
            updated = 1,
            failed = 0,
            finishedAt = 10_000L,
        )

        assertEquals(1_500L, updateSuccessVisibilityMillis(session, 10_500L))
        assertEquals(0L, updateSuccessVisibilityMillis(session, 12_001L))
        assertEquals(0L, updateSuccessVisibilityMillis(session.copy(finishedAt = null), 12_001L))
        assertEquals(
            null,
            updateSuccessVisibilityMillis(session.copy(state = UpdateSessionStates.PARTIAL), 10_500L),
        )
        assertEquals(null, updateSuccessVisibilityMillis(session.copy(failed = 1), 10_500L))
    }

    @Test
    fun sessionLifecycleAndBookOutcomeLabelsRemainDistinct() {
        assertEquals(R.string.updates_session_completed, updateSessionStateLabelRes(UpdateSessionStates.COMPLETED))
        assertEquals(R.string.updates_state_unchanged, updateItemStateLabelRes("UNCHANGED"))
        assertEquals(R.string.updates_state_updated, updateItemStateLabelRes("UPDATED"))
        assertEquals(R.string.updates_reason_source_unavailable, updateReasonLabelRes("source-extension-cancelled"))
    }

    private fun entry(
        id: String,
        title: String = id,
        localMembership: Boolean = true,
        readAt: Instant? = null,
    ): LibraryEntry {
        val identity = BookIdentity("fixture.source", id)
        return LibraryEntry(
            book = LibraryBook(identity, title, Instant.EPOCH, Instant.EPOCH),
            libraryAddedAt = Instant.EPOCH,
            rating = null,
            localTags = emptySet(),
            sourceAvailable = true,
            reconciliation = null,
            progress = readAt?.let { updatedAt ->
                ReadingProgress(
                    identity = identity,
                    locator = ReaderLocator(
                        document = DocumentIdentity(identity.sourceId, identity.remoteBookId, "chapter-1"),
                        blockId = "block-1",
                        characterOffset = 0,
                        chapterProgress = 0.25,
                        bookProgress = 0.25,
                        capturedAt = updatedAt,
                    ),
                )
            },
            localMembership = localMembership,
        )
    }

    private fun update(
        entry: LibraryEntry,
        detectedAt: Long,
        publicationDate: String? = null,
    ) = UnresolvedUpdate(
        identity = entry.book.identity,
        title = entry.book.title,
        anchor = "anchor-${entry.book.identity.remoteBookId}",
        chapters = listOf(SourceUpdateChapter("chapter-2", "第二章")),
        newChapterIds = listOf("chapter-2"),
        lastUpdatedDate = publicationDate,
        detectedAt = detectedAt,
    )
}
