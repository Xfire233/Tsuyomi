/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceUpdateChapter
import org.tsuyomi.shared.sourcecontract.SourceUpdateOutcome

class SourceUpdateAdmissionTest {
    private val identity = BookIdentity("org.tsuyomi.wenku8", "1234")
    private val first = SourceUpdateChapter("10001", "第一章 雾中的灯塔")
    private val second = SourceUpdateChapter("10002", "第二章 旧船票")
    private val third = SourceUpdateChapter("10003", "第三章 潮汐信标")

    @Test
    fun firstBaselineThenExactAppendProducesOnlyTheTrustedDelta() {
        val baseline = admit(previousAnchor = null, chapters = listOf(first, second))
        val appended = admit(previousAnchor = baseline.anchor, chapters = listOf(first, second, third))

        assertEquals(SourceUpdateOutcome.UNCHANGED, baseline.outcome)
        assertTrue(baseline.anchor!!.startsWith("update-check-v2.2."))
        assertEquals(SourceUpdateOutcome.UPDATED, appended.outcome)
        assertEquals(listOf("10003"), appended.newChapterIds)
        assertEquals(listOf(first, second, third), appended.chapters)
    }

    @Test
    fun titleCorrectionsKeepTheSameIdentityBaselineAndPermitAnAppend() {
        val baseline = admit(previousAnchor = null, chapters = listOf(first, second))
        val renamedFirst = first.copy(title = "第一章 雾中灯塔（修订）")
        val renamedSecond = second.copy(title = "第二章 旧船票（修订）")

        val renameOnly = admit(previousAnchor = baseline.anchor, chapters = listOf(renamedFirst, renamedSecond))
        val renamedThenAppended = admit(previousAnchor = baseline.anchor, chapters = listOf(renamedFirst, renamedSecond, third))

        assertEquals(SourceUpdateOutcome.UNCHANGED, renameOnly.outcome)
        assertEquals(baseline.anchor, renameOnly.anchor)
        assertEquals(listOf(renamedFirst, renamedSecond), renameOnly.chapters)
        assertEquals(SourceUpdateOutcome.UPDATED, renamedThenAppended.outcome)
        assertEquals(listOf("10003"), renamedThenAppended.newChapterIds)
        assertEquals(listOf(renamedFirst, renamedSecond, third), renamedThenAppended.chapters)
    }

    @Test
    fun reorderedOrMissingPriorAnchorNeverFabricatesAnAppend() {
        val baseline = admit(previousAnchor = null, chapters = listOf(first, second))
        val reordered = admit(previousAnchor = baseline.anchor, chapters = listOf(second, first, third))
        val missing = admit(previousAnchor = "", chapters = listOf(first, second, third))

        assertEquals(SourceUpdateOutcome.UNAVAILABLE, reordered.outcome)
        assertEquals("prior-anchor-not-prefix", reordered.reason)
        assertTrue(reordered.newChapterIds.isEmpty())
        assertEquals(SourceUpdateOutcome.UNAVAILABLE, missing.outcome)
        assertEquals("prior-anchor-invalid", missing.reason)
    }

    @Test
    fun wrongIdentityAndPartialEvidenceFailClosed() {
        val wrongIdentity = admitSourceUpdateCheck(
            expectedIdentity = identity,
            previousAnchor = null,
            parsed = ParsedSourceUpdateCheck(
                identity = BookIdentity("org.tsuyomi.wenku8", "9999"),
                chapters = listOf(first, second),
                lastUpdatedDate = null,
            ),
        )
        val partial = admit(previousAnchor = null, chapters = emptyList())

        assertEquals(SourceUpdateOutcome.FAILED, wrongIdentity.outcome)
        assertEquals("identity-mismatch", wrongIdentity.reason)
        assertEquals(SourceUpdateOutcome.FAILED, partial.outcome)
        assertEquals("invalid-chapter-evidence", partial.reason)
    }

    private fun admit(
        previousAnchor: String?,
        chapters: List<SourceUpdateChapter>,
    ) = admitSourceUpdateCheck(
        expectedIdentity = identity,
        previousAnchor = previousAnchor,
        parsed = ParsedSourceUpdateCheck(identity, chapters, lastUpdatedDate = "2026-09-07"),
    )
}
