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
    fun missing_anchor_uses_bounded_progress_and_reports_degraded_precision() {
        val locator = ReaderLocator(
            document = DocumentIdentity("org.tsuyomi.wenku8", "1234", "10001"),
            blockId = "removed-block",
            textAnchorDigest = "a".repeat(64),
            characterOffset = 900,
            chapterProgress = 1.0,
            capturedAt = Instant.EPOCH,
        )

        val session = ReaderDocumentSession(document(), locator, ReaderPresentation.PAGED)

        assertEquals(LocatorPrecision.DEGRADED, session.position.precision)
        assertEquals(2, session.position.blockIndex)
        assertEquals(0, session.position.characterOffset)
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
        assertEquals(null, cache.get("org.tsuyomi.wenku8", "1234", "1"))
    }

    private fun document(contentId: String = "10001") = ReaderDocument(
        sourceId = "org.tsuyomi.wenku8",
        remoteBookId = "1234",
        contentId = contentId,
        revision = null,
        title = "Fixture",
        blocks = listOf(
            ReaderBlock.Paragraph("p-1", "first"),
            ReaderBlock.Paragraph("p-2", "second"),
            ReaderBlock.Paragraph("p-3", "third"),
        ),
    )
}
