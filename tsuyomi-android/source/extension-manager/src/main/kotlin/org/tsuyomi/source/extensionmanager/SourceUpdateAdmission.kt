/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.nio.charset.StandardCharsets
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceUpdateChapter
import org.tsuyomi.shared.sourcecontract.SourceUpdateOutcome

internal data class ParsedSourceUpdateCheck(
    val identity: BookIdentity,
    val chapters: List<SourceUpdateChapter>,
    val lastUpdatedDate: String?,
)

internal data class AdmittedSourceUpdateCheck(
    val outcome: SourceUpdateOutcome,
    val anchor: String?,
    val chapters: List<SourceUpdateChapter>,
    val newChapterIds: List<String>,
    val lastUpdatedDate: String?,
    val reason: String?,
)

/**
 * Converts a normalized source directory into update evidence without ever inferring a baseline.
 *
 * An anchor binds source identity and every ordered chapter ID. A later result can claim an update
 * only when its prefix reproduces that prior identity sequence exactly; titles remain latest display
 * metadata, so a title correction cannot manufacture an order change or block a later append.
 */
internal fun admitSourceUpdateCheck(
    expectedIdentity: BookIdentity,
    previousAnchor: String?,
    parsed: ParsedSourceUpdateCheck,
): AdmittedSourceUpdateCheck {
    if (parsed.identity != expectedIdentity) return failedUpdate("identity-mismatch")
    if (parsed.chapters.isEmpty() || parsed.chapters.size > MAX_UPDATE_CHAPTERS) {
        return failedUpdate("invalid-chapter-evidence")
    }
    val chapterIds = parsed.chapters.map(SourceUpdateChapter::chapterId)
    if (chapterIds.toHashSet().size != chapterIds.size) return failedUpdate("duplicate-chapter-id")

    if (previousAnchor == null) {
        return AdmittedSourceUpdateCheck(
            outcome = SourceUpdateOutcome.UNCHANGED,
            anchor = updateAnchor(expectedIdentity, parsed.chapters),
            chapters = parsed.chapters,
            newChapterIds = emptyList(),
            lastUpdatedDate = parsed.lastUpdatedDate,
            reason = null,
        )
    }

    val prior = parseAnchor(previousAnchor) ?: return unavailableUpdate("prior-anchor-invalid")
    if (prior.chapterCount > parsed.chapters.size) return unavailableUpdate("prior-anchor-not-prefix")
    if (updateAnchor(expectedIdentity, parsed.chapters.take(prior.chapterCount)) != previousAnchor) {
        return unavailableUpdate("prior-anchor-not-prefix")
    }

    val anchor = updateAnchor(expectedIdentity, parsed.chapters)
    val newChapterIds = chapterIds.drop(prior.chapterCount)
    return AdmittedSourceUpdateCheck(
        outcome = if (newChapterIds.isEmpty()) SourceUpdateOutcome.UNCHANGED else SourceUpdateOutcome.UPDATED,
        anchor = anchor,
        chapters = parsed.chapters,
        newChapterIds = newChapterIds,
        lastUpdatedDate = parsed.lastUpdatedDate,
        reason = null,
    )
}

private const val MAX_UPDATE_CHAPTERS = 20_000
private val UPDATE_ANCHOR = Regex("^update-check-v2\\.([1-9][0-9]{0,4})\\.([a-f0-9]{64})$")

private data class PriorAnchor(val chapterCount: Int)

private fun parseAnchor(anchor: String): PriorAnchor? {
    val match = UPDATE_ANCHOR.matchEntire(anchor) ?: return null
    val count = match.groupValues[1].toIntOrNull() ?: return null
    return PriorAnchor(count).takeIf { it.chapterCount in 1..MAX_UPDATE_CHAPTERS }
}

private fun updateAnchor(identity: BookIdentity, chapters: List<SourceUpdateChapter>): String {
    val evidence = buildString {
        appendField("version", "update-check-v2")
        appendField("source", identity.sourceId)
        appendField("book", identity.remoteBookId)
        chapters.forEach { chapter ->
            appendField("chapter-id", chapter.chapterId)
        }
    }
    return "update-check-v2.${chapters.size}.${sha256(evidence.toByteArray(StandardCharsets.UTF_8))}"
}

private fun StringBuilder.appendField(name: String, value: String) {
    append(name).append(':').append(value.length).append(':').append(value).append('\n')
}

private fun unavailableUpdate(reason: String) = AdmittedSourceUpdateCheck(
    outcome = SourceUpdateOutcome.UNAVAILABLE,
    anchor = null,
    chapters = emptyList(),
    newChapterIds = emptyList(),
    lastUpdatedDate = null,
    reason = reason,
)

private fun failedUpdate(reason: String) = AdmittedSourceUpdateCheck(
    outcome = SourceUpdateOutcome.FAILED,
    anchor = null,
    chapters = emptyList(),
    newChapterIds = emptyList(),
    lastUpdatedDate = null,
    reason = reason,
)
