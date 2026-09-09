/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.sourcecontract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.tsuyomi.shared.model.BookIdentity

class SourceUpdateContractsTest {
    private val identity = BookIdentity("org.tsuyomi.wenku8", "1234")
    private val chapters = listOf(
        SourceUpdateChapter("10001", "第一章 雾中的灯塔"),
        SourceUpdateChapter("10002", "第二章 旧船票"),
        SourceUpdateChapter("10003", "第三章 潮汐信标"),
    )

    @Test
    fun admittedBaselineAndAppendOnlyEvidenceAreBoundedAndNormalized() {
        val baseline = result(
            outcome = SourceUpdateOutcome.UNCHANGED,
            previousAnchor = null,
            anchor = "update-check-v2.2.${"a".repeat(64)}",
            chapters = chapters.take(2),
            newChapterIds = emptyList(),
            lastUpdatedDate = "2026-09-07",
        )
        val appended = result(
            outcome = SourceUpdateOutcome.UPDATED,
            previousAnchor = baseline.anchor,
            anchor = "update-check-v2.3.${"b".repeat(64)}",
            chapters = chapters,
            newChapterIds = listOf("10003"),
            lastUpdatedDate = "2026-09-07",
        )

        assertEquals("10003", appended.newChapterIds.single())
        assertEquals("2026-09-07", baseline.lastUpdatedDate)
    }

    @Test
    fun resultRejectsFabricatedDeltaAndFailurePayloads() {
        assertThrows(IllegalArgumentException::class.java) {
            result(
                outcome = SourceUpdateOutcome.UPDATED,
                previousAnchor = "update-check-v2.2.${"a".repeat(64)}",
                anchor = "update-check-v2.3.${"b".repeat(64)}",
                chapters = chapters,
                newChapterIds = listOf("not-a-chapter"),
                lastUpdatedDate = null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            result(
                outcome = SourceUpdateOutcome.UNAVAILABLE,
                previousAnchor = "update-check-v2.2.${"a".repeat(64)}",
                anchor = null,
                chapters = chapters.take(2),
                newChapterIds = emptyList(),
                lastUpdatedDate = null,
                reason = "order-changed",
            )
        }
    }

    private fun result(
        outcome: SourceUpdateOutcome,
        previousAnchor: String?,
        anchor: String?,
        chapters: List<SourceUpdateChapter>,
        newChapterIds: List<String>,
        lastUpdatedDate: String?,
        reason: String? = null,
    ) = SourceUpdateProbeResult(
        identity = identity,
        sourceVersion = "0.2.29",
        packageSha256 = "c".repeat(64),
        checkedAt = 1_725_667_200_000,
        outcome = outcome,
        previousAnchor = previousAnchor,
        anchor = anchor,
        chapters = chapters,
        newChapterIds = newChapterIds,
        lastUpdatedDate = lastUpdatedDate,
        reason = reason,
    )
}
