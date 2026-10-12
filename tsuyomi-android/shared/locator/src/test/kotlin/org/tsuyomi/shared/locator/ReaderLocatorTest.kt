// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.shared.locator

import java.time.Instant
import org.tsuyomi.shared.model.BookIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReaderLocatorTest {
    private val document = DocumentIdentity(
        book = BookIdentity.Remote("org.tsuyomi.example", "book-42"),
        contentId = "chapter-7",
        revision = "revision-1",
    )

    @Test
    fun exactLocatorCarriesOnlySemanticProtocolFields() {
        val locator = ReaderLocator(
            document = document,
            blockId = "block-12",
            textAnchorDigest = "a".repeat(64),
            characterOffset = 12,
            chapterProgress = 0.2,
            bookProgress = 0.1,
            capturedAt = Instant.parse("2026-08-08T00:00:00Z"),
        )

        assertEquals(LocatorPrecision.EXACT, locator.precision)
        assertEquals(BookIdentity.Remote("org.tsuyomi.example", "book-42"), locator.document.book)
    }

    @Test
    fun bookmarkIdentityKeepsDistinctOffsetsButIgnoresCaptureAndRenderFallbacks() {
        val firstCapture = ReaderLocator(
            document = document,
            blockId = "block-12",
            textAnchorDigest = "a".repeat(64),
            characterOffset = 12,
            chapterProgress = 0.2,
            bookProgress = 0.1,
            capturedAt = Instant.EPOCH,
        )
        val recapturedPosition = firstCapture.copy(
            textAnchorDigest = "b".repeat(64),
            chapterProgress = 0.9,
            bookProgress = 0.8,
            capturedAt = Instant.EPOCH.plusSeconds(1),
        )
        val laterPosition = firstCapture.copy(characterOffset = 13)
        val differentRevision = firstCapture.copy(document = document.copy(revision = "revision-2"))
        val differentContent = firstCapture.copy(document = document.copy(contentId = "chapter-8"))

        assertEquals(firstCapture.bookmarkPositionKey(), recapturedPosition.bookmarkPositionKey())
        assertEquals(true, firstCapture.namesSameBookmarkPositionAs(recapturedPosition))
        assertEquals(false, firstCapture.namesSameBookmarkPositionAs(laterPosition))
        assertEquals(false, firstCapture.namesSameBookmarkPositionAs(differentRevision))
        assertEquals(false, firstCapture.bookmarkPositionKey() == differentRevision.bookmarkPositionKey())
        assertEquals(false, firstCapture.bookmarkPositionKey() == differentContent.bookmarkPositionKey())
        assertEquals(false, firstCapture.namesSameBookmarkPositionAs(differentContent))
    }

    @Test
    fun localDocumentIdentityAndBookmarkKeysArePublicationScoped() {
        val identity = BookIdentity.Local("f2b8ef64-9375-4fc1-b7c9-274b4beafed1")
        val locator = ReaderLocator(
            document = DocumentIdentity(identity, "section-1"),
            blockId = "block-1",
            characterOffset = 7,
            capturedAt = Instant.EPOCH,
        )
        val samePosition = locator.copy(capturedAt = Instant.EPOCH.plusSeconds(1))
        val independentCopy = locator.copy(
            document = DocumentIdentity(
                BookIdentity.Local("f2b8ef64-9375-4fc1-b7c9-274b4beafed2"),
                "section-1",
            ),
        )

        assertEquals(true, locator.document.namesSameDocumentAs(samePosition.document))
        assertEquals(false, locator.document.namesSameDocumentAs(independentCopy.document))
        assertEquals(locator.bookmarkPositionKey(), samePosition.bookmarkPositionKey())
        assertEquals(false, locator.namesSameBookmarkPositionAs(independentCopy))
        assertEquals(false, locator.bookmarkPositionKey() == independentCopy.bookmarkPositionKey())
        val differentContent = locator.copy(document = locator.document.copy(contentId = "section-2"))
        val differentRevision = locator.copy(document = locator.document.copy(revision = "revision-2"))
        assertEquals(false, locator.bookmarkPositionKey() == differentContent.bookmarkPositionKey())
        assertEquals(false, locator.bookmarkPositionKey() == differentRevision.bookmarkPositionKey())
    }

    @Test
    fun remoteBookmarkPositionKeyRetainsV1Representation() {
        val locator = ReaderLocator(
            document = document,
            blockId = "block-12",
            characterOffset = 12,
            capturedAt = Instant.EPOCH,
        )

        assertEquals(
            "v1|D|6F72672E747375796F6D692E6578616D706C65|626F6F6B2D3432|636861707465722D37|7265766973696F6E2D31|O|626C6F636B2D3132|12",
            locator.bookmarkPositionKey(),
        )
    }
    @Test
    fun strictInvariantsRejectUnanchoredAndInvalidProtocolValues() {
        assertFailsWith<IllegalArgumentException> {
            ReaderLocator(document = document, capturedAt = Instant.EPOCH)
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderLocator(document = document, characterOffset = 0, capturedAt = Instant.EPOCH)
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderLocator(
                document = document,
                blockId = "block",
                textAnchorDigest = "A".repeat(64),
                capturedAt = Instant.EPOCH,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderLocator(document = document, bookProgress = Double.NaN, capturedAt = Instant.EPOCH)
        }
    }
}
