// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.shared.locator

import java.time.Instant
import org.tsuyomi.shared.model.BookIdentity

/**
 * Identity of one resolved reading unit. [revision] is optional in the wire
 * protocol, because an old durable locator may predate revision support.
 */
data class DocumentIdentity(
    val sourceId: String,
    val remoteBookId: String,
    val contentId: String,
    val revision: String? = null,
) {
    /** Stable identity of the parent book, without a database implementation key. */
    val book: BookIdentity = BookIdentity(sourceId = sourceId, remoteBookId = remoteBookId)

    init {
        BookIdentity.requireRemoteId(contentId, "contentId")
        revision?.let { requireBounded(it, "revision", 256) }
    }

    /** True only when both values name the same resolved content, irrespective of revision. */
    fun namesSameDocumentAs(other: DocumentIdentity): Boolean =
        sourceId == other.sourceId &&
            remoteBookId == other.remoteBookId &&
            contentId == other.contentId

    /** True only when content identity and known revision are identical. */
    fun namesSameRevisionAs(other: DocumentIdentity): Boolean =
        namesSameDocumentAs(other) && revision == other.revision
}

/** The restoration quality represented by a semantic locator. */
enum class LocatorPrecision {
    /** Stable block, anchor digest, and Unicode code-point offset are all known. */
    EXACT,

    /** A semantic locator exists, but at least one exact-anchor component is absent. */
    DEGRADED,

    /** No semantic locator could be recovered. */
    UNAVAILABLE,
}

/**
 * Durable semantic reader position defined by reader-locator-v1.
 *
 * It intentionally has no renderer-specific page, spread, pixel, or scroll fields.
 */
data class ReaderLocator(
    val document: DocumentIdentity,
    val blockId: String? = null,
    val textAnchorDigest: String? = null,
    val characterOffset: Int? = null,
    val chapterProgress: Double? = null,
    val bookProgress: Double? = null,
    val capturedAt: Instant,
) {
    init {
        blockId?.let { requireBounded(it, "blockId", 1024) }
        textAnchorDigest?.let {
            require(SHA_256.matches(it)) { "textAnchorDigest must be a lowercase SHA-256 digest" }
        }
        characterOffset?.let {
            require(it >= 0) { "characterOffset must not be negative" }
            require(blockId != null) { "characterOffset requires blockId" }
        }
        if (textAnchorDigest != null) {
            require(blockId != null) { "textAnchorDigest requires blockId" }
        }
        chapterProgress?.let { requireProgress(it, "chapterProgress") }
        bookProgress?.let { requireProgress(it, "bookProgress") }
        require(
            (blockId != null && characterOffset != null) ||
                (blockId != null && textAnchorDigest != null) ||
                chapterProgress != null ||
                bookProgress != null,
        ) { "a locator requires a block anchor or bounded progress fallback" }
    }

    /** Protocol-compatible precision based only on the available semantic evidence. */
    val precision: LocatorPrecision
        get() = if (blockId != null && textAnchorDigest != null && characterOffset != null) {
            LocatorPrecision.EXACT
        } else {
            LocatorPrecision.DEGRADED
        }

    companion object {
        private val SHA_256 = Regex("^[a-f0-9]{64}$")
    }
}

internal fun requireBounded(value: String, fieldName: String, maximumLength: Int) {
    require(value.codePointCount(0, value.length) in 1..maximumLength) {
        "$fieldName must contain 1..$maximumLength Unicode code points"
    }
}

private fun requireProgress(value: Double, fieldName: String) {
    require(value.isFinite() && value in 0.0..1.0) { "$fieldName must be finite and within 0..1" }
}
