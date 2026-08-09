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

enum class ReaderPresentation { SCROLL, PAGED, DUAL_PAGE }

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
    var presentation: ReaderPresentation = initialPresentation
        private set
    var position: ResolvedReaderPosition = resolve(initialLocator)
        private set

    fun switchPresentation(target: ReaderPresentation): ResolvedReaderPosition {
        presentation = target
        return position
    }

    /** Immediate semantic navigation; no animation, debounce, or renderer-specific offset. */
    fun navigateToBlock(blockIndex: Int, characterOffset: Int = 0): ResolvedReaderPosition {
        val boundedIndex = blockIndex.coerceIn(document.blocks.indices)
        val block = document.blocks[boundedIndex]
        val boundedOffset = characterOffset.coerceIn(0, block.textLengthCodePoints())
        position = exactPosition(boundedIndex, boundedOffset)
        return position
    }

    fun navigateByBlock(delta: Int): ResolvedReaderPosition = navigateToBlock(position.blockIndex + delta)

    fun capture(capturedAt: Instant = Instant.now()): ReaderLocator = position.locator.copy(capturedAt = capturedAt)

    private fun resolve(candidate: ReaderLocator?): ResolvedReaderPosition {
        if (candidate == null || !candidate.document.namesSameDocumentAs(identity())) return degradedAt(0, 0)
        val blockIndex = candidate.blockId?.let { id -> document.blocks.indexOfFirst { it.blockId == id } } ?: -1
        if (blockIndex >= 0) {
            val block = document.blocks[blockIndex]
            val digestMatches = candidate.textAnchorDigest == null || candidate.textAnchorDigest == block.anchorDigest()
            val offset = candidate.characterOffset?.coerceIn(0, block.textLengthCodePoints()) ?: 0
            if (digestMatches && candidate.textAnchorDigest != null && candidate.characterOffset != null) {
                return exactPosition(blockIndex, offset)
            }
            return degradedAt(blockIndex, offset)
        }
        candidate.textAnchorDigest?.let { digest ->
            val anchorIndex = document.blocks.indexOfFirst { it.anchorDigest() == digest }
            if (anchorIndex >= 0) return degradedAt(anchorIndex, candidate.characterOffset ?: 0)
        }
        val progress = candidate.chapterProgress
        val fallbackIndex = if (progress == null) 0 else {
            (progress * (document.blocks.size - 1)).toInt().coerceIn(document.blocks.indices)
        }
        return degradedAt(fallbackIndex, 0)
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
        val progress = if (document.blocks.size == 1) 0.0 else index.toDouble() / (document.blocks.size - 1)
        val locator = ReaderLocator(
            document = identity(),
            blockId = block.blockId,
            textAnchorDigest = block.anchorDigest().takeIf { includeExactAnchor },
            characterOffset = boundedOffset.takeIf { includeExactAnchor },
            chapterProgress = progress,
            capturedAt = Instant.now(),
        )
        return ResolvedReaderPosition(index, boundedOffset, precision, locator)
    }

    private fun identity() = DocumentIdentity(
        sourceId = document.sourceId,
        remoteBookId = document.remoteBookId,
        contentId = document.contentId,
        revision = document.revision,
    )
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
        documents[key(document.sourceId, document.remoteBookId, document.contentId)] = document
    }

    @Synchronized
    fun get(sourceId: String, remoteBookId: String, contentId: String): ReaderDocument? =
        documents[key(sourceId, remoteBookId, contentId)]

    @Synchronized
    fun size(): Int = documents.size

    private fun key(sourceId: String, remoteBookId: String, contentId: String) = "$sourceId\u0000$remoteBookId\u0000$contentId"
}

fun defaultReaderPresentation(isEInk: Boolean): ReaderPresentation =
    if (isEInk) ReaderPresentation.PAGED else ReaderPresentation.SCROLL

private fun ReaderBlock.anchorDigest(): String = MessageDigest.getInstance("SHA-256")
    .digest(anchorText().encodeToByteArray())
    .joinToString("") { "%02x".format(it) }

private fun ReaderBlock.anchorText(): String = when (this) {
    is ReaderBlock.Paragraph -> text
    is ReaderBlock.Heading -> text
    is ReaderBlock.Image -> altText.orEmpty()
}

private fun ReaderBlock.textLengthCodePoints(): Int = anchorText().codePointCount(0, anchorText().length)
