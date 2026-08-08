// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.shared.locator

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReaderLocatorTest {
    private val document = DocumentIdentity(
        sourceId = "org.tsuyomi.example",
        remoteBookId = "book-42",
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
        assertEquals(document.book.sourceId, locator.document.sourceId)
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
