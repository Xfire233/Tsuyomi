// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0
package org.tsuyomi.reader.engine

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument

import org.tsuyomi.shared.model.BookIdentity
class ReaderDocumentSessionTest {
    @Test
    fun presentations_share_one_semantic_locator() {
        val session = ReaderDocumentSession(document(), null, ReaderPresentation.SCROLL)
        session.navigateToBlock(1, 3)
        val before = session.capture(Instant.EPOCH)

        session.switchPresentation(ReaderPresentation.PAGED)
        session.switchPresentation(ReaderPresentation.DUAL_PAGE)

        assertEquals(before.copy(capturedAt = session.position.locator.capturedAt), session.position.locator)
        assertEquals(1, session.position.blockIndex)
        assertEquals(3, session.position.characterOffset)
    }

    @Test
    fun requestedDualTemporarilyPagesOnNarrowWindowsWithoutChangingTheLocator() {
        val session = ReaderDocumentSession(document(), null, ReaderPresentation.DUAL_PAGE)
        session.navigateToBlock(1, 3)
        val locator = session.capture(Instant.EPOCH)

        assertEquals(ReaderPresentation.PAGED, effectiveReaderPresentation(ReaderPresentation.DUAL_PAGE, false))
        assertEquals(ReaderPresentation.DUAL_PAGE, effectiveReaderPresentation(ReaderPresentation.DUAL_PAGE, true))
        assertEquals(locator.copy(capturedAt = session.position.locator.capturedAt), session.position.locator)
        assertEquals(1, session.position.blockIndex)
        assertEquals(3, session.position.characterOffset)
    }


    @Test
    fun previewPositionDoesNotMoveTheCommittedReaderPosition() {
        val session = ReaderDocumentSession(document(), null, ReaderPresentation.SCROLL)
        session.navigateToBlock(0, 1)

        val preview = session.previewPositionAtBlock(2, 4)

        assertEquals(2, preview.blockIndex)
        assertEquals(4, preview.characterOffset)
        assertEquals(0, session.position.blockIndex)
        assertEquals(1, session.position.characterOffset)
    }
    @Test
    fun missing_anchor_uses_bounded_progress_and_reports_degraded_precision() {
        val locator = ReaderLocator(
            document = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.wenku8", "1234"), "10001"),
            blockId = "removed-block",
            textAnchorDigest = "a".repeat(64),
            characterOffset = 900,
            chapterProgress = 1.0,
            capturedAt = Instant.EPOCH,
        )

        val session = ReaderDocumentSession(document(), locator, ReaderPresentation.PAGED)

        assertEquals(LocatorPrecision.DEGRADED, session.position.precision)
        assertEquals(2, session.position.blockIndex)
        assertEquals(5, session.position.characterOffset)
    }

    @Test
    fun entering_final_paragraph_is_not_chapter_completion() {
        val session = ReaderDocumentSession(document(), null, ReaderPresentation.SCROLL)

        val startOfFinalParagraph = session.navigateToBlock(2, 0).locator.chapterProgress
        val endOfFinalParagraph = session.navigateToBlock(2, 5).locator.chapterProgress

        assertEquals(11.0 / 16.0, startOfFinalParagraph)
        assertEquals(1.0, endOfFinalParagraph)
    }

    @Test
    fun eink_navigation_is_immediate_and_cache_is_bounded() {
        val session = ReaderDocumentSession(document(), null, defaultReaderPresentation(isEInk = true))
        assertEquals(ReaderPresentation.PAGED, session.presentation)
        assertEquals(1, session.navigateByBlock(1).blockIndex)

        val cache = ReaderDocumentCache(capacity = 2)
        cache.put(document("1"))
        cache.put(document("2"))
        cache.put(document("3"))
        assertEquals(2, cache.size())
        assertEquals(null, cache.get(document("1").identity))
    }

    @Test
    fun exactCodePointAnchorWinsOverConflictingProgressAndSurvivesRevisionChange() {
        val local = ReaderDocument(
            identity = DocumentIdentity(BookIdentity.Local("12345678-1234-1234-1234-123456789abc"), "section", "r2"),
            title = "Unicode",
            blocks = listOf(ReaderBlock.Paragraph("first", "甲😀乙"), ReaderBlock.Paragraph("last", "终点")),
        )
        val captured = ReaderDocumentSession(local, null, ReaderPresentation.PAGED)
            .navigateToBlock(0, 2).locator.copy(
                document = local.identity.copy(revision = "r1"), chapterProgress = 1.0,
            )
        val restored = ReaderDocumentSession(local, captured, ReaderPresentation.SCROLL)
        assertEquals(0, restored.position.blockIndex)
        assertEquals(2, restored.position.characterOffset)
        assertEquals(LocatorPrecision.EXACT, restored.position.precision)
    }

    @Test
    fun relocatedDigestBeatsAReusedBlockIdAndInvalidExactOffsetsAreDegraded() {
        val old = document()
        val captured = ReaderDocumentSession(old, null, ReaderPresentation.PAGED).navigateToBlock(0, 3).locator
        val reordered = old.copy(blocks = listOf(
            ReaderBlock.Paragraph("p-1", "different"), ReaderBlock.Paragraph("replacement", "first"),
        ))
        val restored = ReaderDocumentSession(reordered, captured, ReaderPresentation.PAGED)
        assertEquals(1, restored.position.blockIndex)
        assertEquals(3, restored.position.characterOffset)
        assertEquals(LocatorPrecision.DEGRADED, restored.position.precision)
        val invalid = ReaderDocumentSession(old, captured.copy(characterOffset = 99), ReaderPresentation.PAGED)
        assertEquals(5, invalid.position.characterOffset)
        assertEquals(LocatorPrecision.DEGRADED, invalid.position.precision)
    }

    @Test
    fun identicalContentIdsFromDifferentOriginsNeverRestoreOrShareCacheEntries() {
        val remote = document()
        val local = remote.copy(identity = DocumentIdentity(
            BookIdentity.Local("12345678-1234-1234-1234-123456789abc"), remote.identity.contentId,
        ))
        val remotePosition = ReaderDocumentSession(remote, null, ReaderPresentation.PAGED).navigateToBlock(2, 4).locator
        val restored = ReaderDocumentSession(local, remotePosition, ReaderPresentation.PAGED)
        assertEquals(0, restored.position.blockIndex)
        assertEquals(LocatorPrecision.DEGRADED, restored.position.precision)
        val cache = ReaderDocumentCache(2)
        cache.put(remote)
        cache.put(local)
        assertEquals(remote, cache.get(remote.identity))
        assertEquals(local, cache.get(local.identity))
    }

    private fun document(contentId: String = "10001") = ReaderDocument(
        identity = DocumentIdentity(BookIdentity.Remote("org.tsuyomi.wenku8", "1234"), contentId),
        title = "Fixture",
        blocks = listOf(
            ReaderBlock.Paragraph("p-1", "first"),
            ReaderBlock.Paragraph("p-2", "second"),
            ReaderBlock.Paragraph("p-3", "third"),
        ),
    )
}
