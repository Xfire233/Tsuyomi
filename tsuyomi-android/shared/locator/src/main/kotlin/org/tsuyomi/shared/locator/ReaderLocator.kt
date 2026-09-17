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

/** Maximum semantic bookmarks retained for one book across storage and portable transfer. */
const val MAX_READER_BOOKMARKS_PER_BOOK = 20_000

/**
 * Stable identity for a durable bookmark position.
 *
 * The key includes the exact [DocumentIdentity], then the strongest available semantic anchor.
 * It deliberately excludes capture time and progress metrics whenever a block/code-point position
 * is available, so the same position captured again cannot create another bookmark.
 */
fun ReaderLocator.bookmarkPositionKey(): String = buildString {
    append("v1|D|")
    appendBookmarkKeyPart(document.sourceId)
    append('|')
    appendBookmarkKeyPart(document.remoteBookId)
    append('|')
    appendBookmarkKeyPart(document.contentId)
    append('|')
    appendBookmarkKeyPart(document.revision)
    when (bookmarkPositionKind) {
        BookmarkPositionKind.OFFSET -> {
            append("|O|")
            appendBookmarkKeyPart(blockId)
            append('|')
            append(characterOffset)
        }

        BookmarkPositionKind.ANCHOR -> {
            append("|A|")
            appendBookmarkKeyPart(blockId)
            append('|')
            appendBookmarkKeyPart(textAnchorDigest)
        }

        BookmarkPositionKind.FALLBACK -> {
            append("|P|")
            appendBookmarkKeyPart(blockId)
            append('|')
            appendBookmarkProgressKeyPart(chapterProgress)
            append('|')
            appendBookmarkProgressKeyPart(bookProgress)
        }
    }
}

/** True when two captures name one semantic bookmark position. */
fun ReaderLocator.namesSameBookmarkPositionAs(other: ReaderLocator): Boolean {
    if (!document.namesSameRevisionAs(other.document) || bookmarkPositionKind != other.bookmarkPositionKind) return false
    return when (bookmarkPositionKind) {
        BookmarkPositionKind.OFFSET -> blockId == other.blockId && characterOffset == other.characterOffset
        BookmarkPositionKind.ANCHOR -> blockId == other.blockId && textAnchorDigest == other.textAnchorDigest
        BookmarkPositionKind.FALLBACK -> blockId == other.blockId &&
            chapterProgress.namesSameBookmarkProgressAs(other.chapterProgress) &&
            bookProgress.namesSameBookmarkProgressAs(other.bookProgress)
    }
}

private val ReaderLocator.bookmarkPositionKind: BookmarkPositionKind
    get() = when {
        blockId != null && characterOffset != null -> BookmarkPositionKind.OFFSET
        blockId != null && textAnchorDigest != null -> BookmarkPositionKind.ANCHOR
        else -> BookmarkPositionKind.FALLBACK
    }

private fun Double?.namesSameBookmarkProgressAs(other: Double?): Boolean = when {
    this == null -> other == null
    other == null -> false
    else -> this!! == other!!
}

private enum class BookmarkPositionKind { OFFSET, ANCHOR, FALLBACK }

private fun StringBuilder.appendBookmarkKeyPart(value: String?) {
    if (value == null) {
        append('-')
        return
    }
    value.encodeToByteArray().forEach { byte ->
        append(BOOKMARK_KEY_HEX_DIGITS[(byte.toInt() ushr 4) and 0xF])
        append(BOOKMARK_KEY_HEX_DIGITS[byte.toInt() and 0xF])
    }
}

private fun StringBuilder.appendBookmarkProgressKeyPart(value: Double?) {
    if (value == null) {
        append('-')
    } else {
        append((if (value == 0.0) 0.0 else value).toBits().toString(16))
    }
}

private const val BOOKMARK_KEY_HEX_DIGITS = "0123456789ABCDEF"

internal fun requireBounded(value: String, fieldName: String, maximumLength: Int) {
    require(value.codePointCount(0, value.length) in 1..maximumLength) {
        "$fieldName must contain 1..$maximumLength Unicode code points"
    }
}

private fun requireProgress(value: Double, fieldName: String) {
    require(value.isFinite() && value in 0.0..1.0) { "$fieldName must be finite and within 0..1" }
}
