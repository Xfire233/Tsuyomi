// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0
package org.tsuyomi.reader.engine

import java.security.MessageDigest
import java.time.Instant
import java.util.LinkedHashMap
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import org.tsuyomi.shared.sourcecontract.canonicalAnchorText
import org.tsuyomi.shared.sourcecontract.inlineSpans

enum class ReaderPresentation { SCROLL, PAGED, DUAL_PAGE }

/**
 * Resolves the presentation that can be rendered in the current window without
 * rewriting the user's requested presentation. A narrow window temporarily
 * renders a requested dual page as a single paged surface; it automatically
 * becomes dual again once the window is eligible.
 */
fun effectiveReaderPresentation(
    requested: ReaderPresentation,
    dualPageEligible: Boolean,
): ReaderPresentation =
    if (requested == ReaderPresentation.DUAL_PAGE && !dualPageEligible) {
        ReaderPresentation.PAGED
    } else {
        requested
    }

data class ResolvedReaderPosition(
    val blockIndex: Int,
    val characterOffset: Int,
    val precision: LocatorPrecision,
    val locator: ReaderLocator,
)

/** One structured document and one semantic position shared by every compatible presentation. */
class ReaderDocumentSession(
    val document: ReaderDocument,
    initialLocator: ReaderLocator?,
    initialPresentation: ReaderPresentation,
) {
    val identity: DocumentIdentity = document.identity
    val documentRevision: String = document.identity.revision ?: "unversioned"
    val contentDigest: String = readerContentDigest(document)

    private val blockCodePointLengths = IntArray(document.blocks.size) { index ->
        document.blocks[index].textLengthCodePoints()
    }
    private val cumulativeCodePoints = LongArray(document.blocks.size + 1).also { cumulative ->
        blockCodePointLengths.indices.forEach { index ->
            cumulative[index + 1] = cumulative[index] + blockCodePointLengths[index]
        }
    }
    private val totalCodePoints = cumulativeCodePoints.last()
    var presentation: ReaderPresentation = initialPresentation
        private set
    var position: ResolvedReaderPosition = resolve(initialLocator)
        private set

    fun switchPresentation(target: ReaderPresentation): ResolvedReaderPosition {
        presentation = target
        return position
    }

    /** Immediate semantic navigation; no animation, debounce, or renderer-specific offset. */
    fun navigateToBlock(blockIndex: Int, characterOffset: Int = 0, exact: Boolean = true): ResolvedReaderPosition {
        val boundedIndex = blockIndex.coerceIn(document.blocks.indices)
        val block = document.blocks[boundedIndex]
        val boundedOffset = characterOffset.coerceIn(0, block.textLengthCodePoints())
        position = if (exact) exactPosition(boundedIndex, boundedOffset) else degradedAt(boundedIndex, boundedOffset)
        return position
    }

    /** Resolves a preview target without moving the durable semantic position. */
    fun previewPositionAtBlock(blockIndex: Int, characterOffset: Int = 0, exact: Boolean = true): ResolvedReaderPosition {
        val boundedIndex = blockIndex.coerceIn(document.blocks.indices)
        val block = document.blocks[boundedIndex]
        val boundedOffset = characterOffset.coerceIn(0, block.textLengthCodePoints())
        return if (exact) exactPosition(boundedIndex, boundedOffset) else degradedAt(boundedIndex, boundedOffset)
    }

    fun navigateByBlock(delta: Int): ResolvedReaderPosition = navigateToBlock(position.blockIndex + delta)

    fun capture(capturedAt: Instant = Instant.now()): ReaderLocator = position.locator.copy(capturedAt = capturedAt)

    private fun resolve(candidate: ReaderLocator?): ResolvedReaderPosition {
        if (candidate == null || !candidate.document.namesSameDocumentAs(identity)) return degradedAt(0, 0)
        val blockIndex = candidate.blockId?.let { id ->
            document.blocks.indexOfFirst { it.semanticBlockId() == id || it.blockId == id }
        } ?: -1
        if (blockIndex >= 0) {
            val block = document.blocks[blockIndex]
            val offset = candidate.characterOffset ?: 0
            if (candidate.textAnchorDigest == block.anchorDigest()) {
                if (candidate.characterOffset != null && offset <= blockCodePointLengths[blockIndex]) {
                    return exactPosition(blockIndex, offset)
                }
                return degradedAt(blockIndex, offset)
            }
            if (candidate.textAnchorDigest == null) return degradedAt(blockIndex, offset)
        }
        candidate.textAnchorDigest?.let { digest ->
            val anchorIndex = document.blocks.indexOfFirst { it.anchorDigest() == digest }
            if (anchorIndex >= 0) return degradedAt(anchorIndex, candidate.characterOffset ?: 0)
        }
        if (blockIndex >= 0) return degradedAt(blockIndex, 0)
        val progress = candidate.chapterProgress ?: return degradedAt(0, 0)
        if (totalCodePoints == 0L) {
            val fallbackIndex = (progress * (document.blocks.size - 1)).toInt().coerceIn(document.blocks.indices)
            return degradedAt(fallbackIndex, 0)
        }
        val target = (progress.coerceIn(0.0, 1.0) * totalCodePoints).toLong().coerceAtMost(totalCodePoints)
        val search = cumulativeCodePoints.binarySearch(target)
        val fallbackIndex = if (search >= 0) {
            search.coerceAtMost(document.blocks.lastIndex)
        } else {
            (-search - 2).coerceIn(document.blocks.indices)
        }
        val fallbackOffset = (target - cumulativeCodePoints[fallbackIndex])
            .coerceAtMost(blockCodePointLengths[fallbackIndex].toLong())
            .toInt()
        return degradedAt(fallbackIndex, fallbackOffset)
    }

    private fun exactPosition(index: Int, offset: Int): ResolvedReaderPosition = positionAt(
        index = index,
        offset = offset,
        precision = LocatorPrecision.EXACT,
        includeExactAnchor = true,
    )

    private fun degradedAt(index: Int, offset: Int): ResolvedReaderPosition = positionAt(
        index = index.coerceIn(document.blocks.indices),
        offset = offset.coerceAtLeast(0),
        precision = LocatorPrecision.DEGRADED,
        includeExactAnchor = false,
    )

    private fun positionAt(
        index: Int,
        offset: Int,
        precision: LocatorPrecision,
        includeExactAnchor: Boolean,
    ): ResolvedReaderPosition {
        val block = document.blocks[index]
        val boundedOffset = offset.coerceIn(0, block.textLengthCodePoints())
        val progress = if (totalCodePoints == 0L) {
            if (document.blocks.size == 1) 0.0 else index.toDouble() / (document.blocks.size - 1)
        } else {
            (cumulativeCodePoints[index] + boundedOffset).toDouble() / totalCodePoints
        }
        val locator = ReaderLocator(
            document = identity,
            blockId = block.semanticBlockId(),
            textAnchorDigest = block.anchorDigest().takeIf { includeExactAnchor },
            characterOffset = boundedOffset.takeIf { includeExactAnchor },
            chapterProgress = progress,
            capturedAt = Instant.now(),
        )
        return ResolvedReaderPosition(index, boundedOffset, precision, locator)
    }
}

/** Bounded LRU of neighboring structured documents; eviction never changes active progress. */
class ReaderDocumentCache(private val capacity: Int = 5) {
    private val documents = object : LinkedHashMap<String, ReaderDocument>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReaderDocument>?): Boolean = size > capacity
    }

    init {
        require(capacity in 1..16)
    }

    @Synchronized
    fun put(document: ReaderDocument) {
        documents[key(document.identity)] = document
    }

    @Synchronized
    fun get(identity: DocumentIdentity): ReaderDocument? = documents[key(identity)]

    @Synchronized
    fun size(): Int = documents.size

    private fun key(identity: DocumentIdentity): String = buildString {
        append(identity.book.storageKey.length).append(':').append(identity.book.storageKey)
        append(identity.contentId.encodeToByteArray().size).append(':').append(identity.contentId)
        val revision = identity.revision
        if (revision == null) append("-:") else append(revision.encodeToByteArray().size).append(':').append(revision)
    }
}

fun defaultReaderPresentation(isEInk: Boolean): ReaderPresentation =
    if (isEInk) ReaderPresentation.PAGED else ReaderPresentation.SCROLL

private fun readerContentDigest(document: ReaderDocument): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateField(document.identity.book.storageKey)
    digest.updateField(document.identity.contentId)
    digest.updateField(document.identity.revision)
    digest.updateField(document.title)
    document.blocks.forEach { it.updateDigest(digest) }
    document.anchors.toSortedMap().forEach { (anchorId, target) ->
        digest.updateField(anchorId)
        digest.updateField(target.blockId)
        digest.updateField(target.codePointOffset.toString())
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun ReaderBlock.updateDigest(digest: MessageDigest) {
    digest.updateField(when (this) {
        is ReaderBlock.Paragraph -> "paragraph"
        is ReaderBlock.Heading -> "heading"
        is ReaderBlock.Image -> "image"
        is ReaderBlock.Divider -> "divider"
        is ReaderBlock.Quote -> "quote"
        is ReaderBlock.Listing -> "list"
        is ReaderBlock.Note -> "note"
        is ReaderBlock.Table -> "table"
        is ReaderBlock.Post -> "post"
        is ReaderBlock.Unsupported -> "unsupported"
    })
    digest.updateField(blockId)
    digest.updateField(anchorId)
    digest.updateField(canonicalAnchorText())
    inlineSpans().forEach { span ->
        digest.updateField(span.startCodePoint.toString())
        digest.updateField(span.endCodePoint.toString())
        digest.updateField(span.kind.toString())
    }
    when (this) {
        is ReaderBlock.Paragraph -> digest.updateField(preformatted.toString())
        is ReaderBlock.Heading -> digest.updateField(level.toString())
        is ReaderBlock.Image -> {
            when (val ref = resource) {
                is ReaderResourceRef.RemoteHttps -> { digest.updateField("https"); digest.updateField(ref.url) }
                is ReaderResourceRef.Local -> {
                    digest.updateField("local"); digest.updateField(ref.publicationId); digest.updateField(ref.resourceId)
                    digest.updateField(ref.mimeType); digest.updateField(ref.sha256)
                }
            }
            digest.updateField(width?.toString()); digest.updateField(height?.toString())
            aspectRatio?.let { digest.updateField("aspectRatio"); digest.updateField(it.toString()) }
        }
        is ReaderBlock.Divider -> Unit
        is ReaderBlock.Quote -> digest.updateField(attribution)
        is ReaderBlock.Listing -> {
            digest.updateField(ordered.toString()); digest.updateField(start.toString())
            items.forEach { item -> digest.updateField(item.itemId); item.blocks.forEach { it.updateDigest(digest) } }
        }
        is ReaderBlock.Note -> {
            digest.updateField(noteId); digest.updateField(label); blocks.forEach { it.updateDigest(digest) }
        }
        is ReaderBlock.Table -> rows.forEach { row -> row.cells.forEach { cell ->
            digest.updateField(cell.text); digest.updateField(cell.header.toString())
            cell.spans.forEach { span -> digest.updateField("${span.startCodePoint}:${span.endCodePoint}:${span.kind}") }
        } }
        is ReaderBlock.Post -> {
            digest.updateField(postId); digest.updateField(authorId); digest.updateField(authorName); digest.updateField(floor?.toString())
            digest.updateField(createdAt); digest.updateField(replyToPostId); blocks.forEach { it.updateDigest(digest) }
        }
        is ReaderBlock.Unsupported -> digest.updateField(featureCode)
    }
}

private fun MessageDigest.updateField(value: String?) {
    if (value == null) {
        update(0xFF.toByte())
        return
    }
    val bytes = value.encodeToByteArray()
    update((bytes.size ushr 24).toByte())
    update((bytes.size ushr 16).toByte())
    update((bytes.size ushr 8).toByte())
    update(bytes.size.toByte())
    update(bytes)
}

private fun ReaderBlock.semanticBlockId(): String = (this as? ReaderBlock.Post)?.postId ?: blockId

private fun ReaderBlock.anchorDigest(): String = MessageDigest.getInstance("SHA-256")
    .digest(canonicalAnchorText().encodeToByteArray())
    .joinToString("") { "%02x".format(it) }

private fun ReaderBlock.textLengthCodePoints(): Int {
    val text = canonicalAnchorText()
    return text.codePointCount(0, text.length)
}
