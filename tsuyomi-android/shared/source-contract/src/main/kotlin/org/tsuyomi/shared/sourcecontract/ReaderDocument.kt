/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.sourcecontract

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.model.BookIdentity

private val READER_SHA256 = Regex("^[a-f0-9]{64}$")
private val RESOURCE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
private val NOTE_ID = Regex("^[A-Za-z][A-Za-z0-9_.:-]{0,255}$")

/** A resource reference, never a filesystem path or a provider URI. */
sealed interface ReaderResourceRef {
    /** Remote images remain inert HTTPS references and require the host's existing remote-media grant. */
    data class RemoteHttps(val url: String) : ReaderResourceRef {
        init {
            val uri = runCatching { URI(url) }.getOrNull()
            require(url.length in 1..4096 && uri != null && uri.scheme.equals("https", true)) {
                "Reader remote resource must use HTTPS"
            }
            require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawFragment == null) {
                "Invalid Reader remote resource URL"
            }
        }
    }

    /** Opaque ID within one private publication; it cannot be used to derive a path or provider URI. */
    data class Local(
        val publicationId: String,
        val resourceId: String,
        val mimeType: String,
        val sha256: String,
    ) : ReaderResourceRef {
        init {
            BookIdentity.Local(publicationId)
            require(isCanonicalUuid(resourceId)) { "Reader resource ID must be a canonical UUID" }
            require(mimeType in RESOURCE_MIME_TYPES) { "Unsupported Reader image MIME type" }
            require(READER_SHA256.matches(sha256)) { "Reader resource SHA-256 must be lowercase hexadecimal" }
        }
    }
}

/** A typed navigation target. External targets are always explicit, inert HTTPS links. */
sealed interface ReaderLinkTarget {
    data class Internal(val contentId: String, val fragment: String? = null) : ReaderLinkTarget {
        init {
            requireReaderTextId(contentId, "internal content ID", 1024)
            fragment?.let { requireReaderTextId(it, "internal fragment", 256) }
        }
    }

    data class ExternalHttps(val url: String) : ReaderLinkTarget {
        init {
            val uri = runCatching { URI(url) }.getOrNull()
            require(url.length in 1..4096 && uri != null && uri.scheme.equals("https", true)) {
                "Reader external link must use HTTPS"
            }
            require(!uri.host.isNullOrBlank() && uri.userInfo == null) { "Invalid Reader external link" }
        }
    }
}

/** Styles attach to code-point ranges of the canonical plain text, not rendered or UTF-16 offsets. */
sealed interface ReaderInlineKind {
    data object Emphasis : ReaderInlineKind
    data object Strong : ReaderInlineKind
    data object Underline : ReaderInlineKind
    data object Strike : ReaderInlineKind
    data object Code : ReaderInlineKind
    data class Link(val target: ReaderLinkTarget) : ReaderInlineKind
    data class Ruby(val annotation: String) : ReaderInlineKind {
        init { requireReaderTextId(annotation, "ruby annotation", 256) }
    }
    data class NoteReference(val noteId: String) : ReaderInlineKind {
        init { require(NOTE_ID.matches(noteId)) { "Invalid note reference ID" } }
    }
}

data class ReaderSpan(
    val startCodePoint: Int,
    val endCodePoint: Int,
    val kind: ReaderInlineKind,
) {
    init {
        require(startCodePoint >= 0 && endCodePoint > startCodePoint) { "Invalid Reader span range" }
    }
}

data class ReaderTableCell(
    val text: String,
    val spans: List<ReaderSpan> = emptyList(),
    val header: Boolean = false,
) {
    init {
        require(text.codePointCount(0, text.length) <= MAX_READER_TABLE_TEXT) { "Reader table cell is too long" }
        validateReaderSpans(text, spans)
    }
}

data class ReaderTableRow(val cells: List<ReaderTableCell>) {
    init { require(cells.isNotEmpty() && cells.size <= MAX_READER_TABLE_COLUMNS) { "Invalid Reader table row" } }
}

data class ReaderListItem(
    val itemId: String,
    val blocks: List<ReaderBlock>,
) {
    init {
        requireReaderBlockId(itemId)
        require(blocks.isNotEmpty() && blocks.size <= MAX_READER_BODY_BLOCKS) { "Invalid Reader list item block count" }
        require(blocks.none { it is ReaderBlock.Post }) { "Posts cannot be nested in list items" }
    }
}

/** Source-neutral navigation row used by the shared Reader directory and search. */
data class ReaderNavigationTarget(
    val contentId: String,
    val title: String,
    val sectionTitle: String? = null,
    val fragment: String? = null,
) {
    init {
        requireReaderTextId(contentId, "navigation content ID", 256)
        requireReaderTextId(title, "navigation title", 512)
        sectionTitle?.let { requireReaderTextId(it, "navigation section title", 512) }
        fragment?.let { requireReaderTextId(it, "navigation fragment", 256) }
    }
}

sealed interface ReaderBlock {
    val blockId: String
    val anchorId: String? get() = null

    data class Paragraph(
        override val blockId: String,
        val text: String,
        val spans: List<ReaderSpan> = emptyList(),
        override val anchorId: String? = null,
        val preformatted: Boolean = false,
    ) : ReaderBlock {
        init {
            requireReaderBlockId(blockId)
            anchorId?.let { requireReaderTextId(it, "anchor ID", 256) }
            requireReaderTextId(text, "paragraph", MAX_READER_BLOCK_TEXT, allowEmpty = false)
            validateReaderSpans(text, spans)
        }
    }

    data class Heading(
        override val blockId: String,
        val text: String,
        val level: Int,
        val spans: List<ReaderSpan> = emptyList(),
        override val anchorId: String? = null,
    ) : ReaderBlock {
        init {
            requireReaderBlockId(blockId)
            anchorId?.let { requireReaderTextId(it, "anchor ID", 256) }
            requireReaderTextId(text, "heading", MAX_READER_BLOCK_TEXT, allowEmpty = false)
            require(level in 1..6) { "Invalid heading level" }
            validateReaderSpans(text, spans)
        }
    }

    data class Image(
        override val blockId: String,
        val resource: ReaderResourceRef,
        val altText: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        override val anchorId: String? = null,
        val aspectRatio: Double? = null,
    ) : ReaderBlock {
        init {
            requireReaderBlockId(blockId)
            anchorId?.let { requireReaderTextId(it, "anchor ID", 256) }
            altText?.let { requireReaderTextId(it, "image alternative text", 4096, allowEmpty = true) }
            require(aspectRatio == null || aspectRatio.isFinite() && aspectRatio > 0.0 && aspectRatio <= 100.0) {
                "Invalid Reader image aspect ratio"
            }
            require(width == null || width in 1..100_000)
            require(height == null || height in 1..100_000)
            require(width == null || height == null || width.toLong() * height <= MAX_READER_IMAGE_PIXELS) {
                "Reader image dimensions exceed the pixel limit"
            }
        }
    }

    data class Divider(
        override val blockId: String,
        override val anchorId: String? = null,
    ) : ReaderBlock {
        init {
            requireReaderBlockId(blockId)
            anchorId?.let { requireReaderTextId(it, "anchor ID", 256) }
        }
    }

    data class Quote(
        override val blockId: String,
        val text: String,
        val spans: List<ReaderSpan> = emptyList(),
        val attribution: String? = null,
        override val anchorId: String? = null,
    ) : ReaderBlock {
        init {
            requireReaderBlockId(blockId)
            anchorId?.let { requireReaderTextId(it, "anchor ID", 256) }
            requireReaderTextId(text, "quote", MAX_READER_BLOCK_TEXT, allowEmpty = false)
            attribution?.let { requireReaderTextId(it, "quote attribution", 4096, allowEmpty = true) }
            validateReaderSpans(text, spans)
        }
    }

    data class Listing(
        override val blockId: String,
        val ordered: Boolean,
        val start: Int = 1,
        val items: List<ReaderListItem>,
        override val anchorId: String? = null,
    ) : ReaderBlock {
        init {
            requireReaderBlockId(blockId)
            anchorId?.let { requireReaderTextId(it, "anchor ID", 256) }
            require(start in 1..1_000_000 && items.isNotEmpty() && items.size <= MAX_READER_LIST_ITEMS) {
                "Invalid Reader list"
            }
            require(items.map { it.itemId }.distinct().size == items.size) { "Duplicate Reader list item ID" }
        }
    }

    data class Note(
        override val blockId: String,
        val noteId: String,
        val label: String?,
        val blocks: List<ReaderBlock>,
        override val anchorId: String? = noteId,
    ) : ReaderBlock {
        init {
            requireReaderBlockId(blockId)
            require(NOTE_ID.matches(noteId)) { "Invalid Reader note ID" }
            label?.let { requireReaderTextId(it, "note label", 256, allowEmpty = true) }
            anchorId?.let { requireReaderTextId(it, "anchor ID", 256) }
            require(blocks.isNotEmpty() && blocks.size <= MAX_READER_BODY_BLOCKS && blocks.none { it is ReaderBlock.Post }) {
                "Invalid Reader note body"
            }
        }
    }

    data class Table(
        override val blockId: String,
        val rows: List<ReaderTableRow>,
        override val anchorId: String? = null,
    ) : ReaderBlock {
        init {
            requireReaderBlockId(blockId)
            anchorId?.let { requireReaderTextId(it, "anchor ID", 256) }
            require(rows.isNotEmpty() && rows.size <= MAX_READER_TABLE_ROWS) { "Invalid Reader table row count" }
            val length = rows.sumOf { row -> row.cells.sumOf { it.text.codePointCount(0, it.text.length).toLong() } + row.cells.size - 1 } + rows.size - 1
            require(length <= MAX_READER_TABLE_TEXT) { "Reader table exceeds canonical text limit" }
        }
    }

    data class Post(
        override val blockId: String,
        val postId: String,
        val authorId: String? = null,
        val authorName: String? = null,
        val floor: Int? = null,
        val createdAt: String? = null,
        val replyToPostId: String? = null,
        val blocks: List<ReaderBlock>,
        override val anchorId: String? = null,
    ) : ReaderBlock {
        init {
            requireReaderBlockId(blockId)
            requireReaderBlockId(postId)
            authorId?.let { requireReaderTextId(it, "post author ID", 1024) }
            authorName?.let { requireReaderTextId(it, "post author name", 1024) }
            require(floor == null || floor > 0) { "Invalid post floor" }
            createdAt?.let { require(runCatching { OffsetDateTime.parse(it) }.isSuccess) { "Invalid post timestamp" } }
            replyToPostId?.let { requireReaderTextId(it, "reply post ID", 1024) }
            anchorId?.let { requireReaderTextId(it, "anchor ID", 256) }
            require(blocks.isNotEmpty() && blocks.size <= MAX_READER_BODY_BLOCKS) { "Invalid Reader post block count" }
            require(blocks.none { it is ReaderBlock.Post }) { "Reader posts cannot nest posts" }
        }
    }

    /** Visible, typed notice emitted by a local normalizer when content cannot be represented faithfully. */
    data class Unsupported(
        override val blockId: String,
        val featureCode: String,
        val visibleText: String,
        override val anchorId: String? = null,
    ) : ReaderBlock {
        init {
            requireReaderBlockId(blockId)
            require(featureCode.matches(Regex("^[a-z][a-z0-9.-]{0,63}$"))) { "Invalid unsupported-feature code" }
            requireReaderTextId(visibleText, "unsupported-feature notice", 4096)
            anchorId?.let { requireReaderTextId(it, "anchor ID", 256) }
        }
    }
}

/** Stable target of an authored anchor, expressed in canonical Unicode code-point offsets. */
data class ReaderAnchorTarget(
    val blockId: String,
    val codePointOffset: Int,
) {
    init {
        requireReaderBlockId(blockId)
        require(codePointOffset >= 0) { "Reader anchor offset must not be negative" }
    }
}

/** Host-normalized presentation; it never enables execution of publication markup. */
enum class ReaderDocumentPresentation { REFLOWABLE, FIXED_IMAGE }

enum class ReaderReadingDirection { LTR, RTL }

/** Retains source-owned chapter versus physical thread-page admission; unknown never enables chapter edges. */
enum class ReaderDocumentKind { UNKNOWN, CHAPTER, THREAD_PAGE }

/** One resolved reading unit. Host-owned local documents and validated remote source results share this DTO. */
data class ReaderDocument(
    val identity: DocumentIdentity,
    val title: String,
    val blocks: List<ReaderBlock>,
    val anchors: Map<String, ReaderAnchorTarget> = emptyMap(),
    val presentation: ReaderDocumentPresentation = ReaderDocumentPresentation.REFLOWABLE,
    val readingDirection: ReaderReadingDirection = ReaderReadingDirection.LTR,
    val kind: ReaderDocumentKind = ReaderDocumentKind.UNKNOWN,
) {
    init {
        requireReaderTextId(title, "document title", 4096)
        require(blocks.isNotEmpty() && blocks.size <= MAX_READER_DOCUMENT_BLOCKS) { "Invalid Reader document block count" }
        if (presentation == ReaderDocumentPresentation.FIXED_IMAGE) {
            require(identity.book is BookIdentity.Local && blocks.all { it is ReaderBlock.Image }) {
                "Fixed-image presentation requires a normalized local image sequence"
            }
        }
        val flattened = collectReaderDocumentBlocks(blocks)
        val blockIds = flattened.associateBy { it.blockId }
        require(blockIds.size == flattened.size) { "Duplicate Reader block identity" }
        val noteIds = HashSet<String>()
        for (block in flattened) {
            if (block is ReaderBlock.Note) {
                require(noteIds.add(block.noteId)) { "Duplicate Reader note identity" }
            }
        }
        val postIds = flattened.filterIsInstance<ReaderBlock.Post>().map { it.postId }
        require(postIds.distinct().size == postIds.size) { "Duplicate Reader post identity" }
        val localResources = flattened.filterIsInstance<ReaderBlock.Image>()
            .mapNotNull { it.resource as? ReaderResourceRef.Local }
        val book = identity.book
        if (book is BookIdentity.Local) {
            require(localResources.all { it.publicationId == book.publicationId }) {
                "Local Reader resource belongs to a different publication"
            }
        } else {
            require(localResources.isEmpty()) { "Remote Reader document cannot carry local resources" }
        }
        for (block in flattened) {
            for (span in block.inlineSpans()) {
                val note = span.kind as? ReaderInlineKind.NoteReference ?: continue
                require(note.noteId in noteIds) { "Reader note reference has no matching note body" }
            }
        }
        require(anchors.size <= MAX_READER_ANCHORS) { "Too many Reader anchors" }
        val allAnchors = HashMap<String, ReaderAnchorTarget>(flattened.size + anchors.size)
        flattened.forEach { block ->
            block.anchorId?.let { anchorId ->
                require(allAnchors.put(anchorId, ReaderAnchorTarget(block.blockId, 0)) == null) {
                    "Duplicate Reader anchor identity"
                }
            }
        }
        anchors.forEach { (anchorId, target) ->
            requireReaderTextId(anchorId, "anchor ID", MAX_READER_ANCHOR_ID)
            val block = blockIds[target.blockId] ?: error("Reader anchor points to an unknown block")
            val text = block.canonicalAnchorText()
            require(target.codePointOffset <= text.codePointCount(0, text.length)) {
                "Reader anchor offset exceeds canonical block text"
            }
            val existing = allAnchors.putIfAbsent(anchorId, target)
            require(existing == null || existing == target) { "Conflicting Reader anchor identity" }
        }
    }

    /** Source revision/fingerprint; rendered content digests are computed independently by the engine. */
    val fingerprint: String? get() = identity.revision
}

fun ReaderDocument.targetForAnchor(anchorId: String): ReaderAnchorTarget? {
    val target = anchors[anchorId] ?: blocks.asSequence()
        .flatMap { it.nestedBlocks().asSequence() }
        .firstOrNull { it.anchorId == anchorId }
        ?.let { ReaderAnchorTarget(it.blockId, 0) }
        ?: return null
    return rootTarget(target)
}

fun ReaderDocument.targetForNote(noteId: String): ReaderAnchorTarget? {
    val note = blocks.asSequence().flatMap { it.nestedBlocks().asSequence() }
        .filterIsInstance<ReaderBlock.Note>()
        .firstOrNull { it.noteId == noteId }
        ?: return null
    return rootTarget(ReaderAnchorTarget(note.blockId, 0))
}

private fun ReaderDocument.rootTarget(target: ReaderAnchorTarget): ReaderAnchorTarget? {
    for (root in blocks) {
        if (root.blockId == target.blockId) return target
        val offset = root.offsetOfDescendant(target.blockId, target.codePointOffset)
        if (offset != null) return ReaderAnchorTarget(root.blockId, offset)
    }
    return null
}

private fun ReaderBlock.offsetOfDescendant(blockId: String, codePointOffset: Int): Int? {
    val children = when (this) {
        is ReaderBlock.Listing -> items.asSequence().flatMap { it.blocks.asSequence() }
        is ReaderBlock.Note -> blocks.asSequence()
        is ReaderBlock.Post -> blocks.asSequence()
        else -> return null
    }
    var offset = 0
    var first = true
    for (child in children) {
        if (!first) offset++
        first = false
        if (child.blockId == blockId) return offset + codePointOffset
        child.offsetOfDescendant(blockId, codePointOffset)?.let { return offset + it }
        val text = child.canonicalAnchorText()
        offset += text.codePointCount(0, text.length)
    }
    return null
}

/** The source-owned text used by semantic anchors and code-point offsets. */
fun ReaderBlock.canonicalAnchorText(): String = when (this) {
    is ReaderBlock.Paragraph -> text
    is ReaderBlock.Heading -> text
    is ReaderBlock.Image -> altText.orEmpty()
    is ReaderBlock.Divider -> ""
    is ReaderBlock.Quote -> text + (attribution?.let { "\n$it" } ?: "")
    is ReaderBlock.Listing -> items.joinToString("\n") { item -> item.blocks.joinToString("\n") { it.canonicalAnchorText() } }
    is ReaderBlock.Note -> blocks.joinToString("\n") { it.canonicalAnchorText() }
    is ReaderBlock.Table -> rows.joinToString("\n") { row -> row.cells.joinToString("\t") { it.text } }
    is ReaderBlock.Post -> blocks.joinToString("\n") { it.canonicalAnchorText() }
    is ReaderBlock.Unsupported -> visibleText
}

fun ReaderBlock.inlineSpans(): List<ReaderSpan> = when (this) {
    is ReaderBlock.Paragraph -> spans
    is ReaderBlock.Heading -> spans
    is ReaderBlock.Quote -> spans
    is ReaderBlock.Table -> buildList {
        var offset = 0
        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) offset++
            row.cells.forEachIndexed { cellIndex, cell ->
                if (cellIndex > 0) offset++
                cell.spans.forEach { add(it.copy(startCodePoint = it.startCodePoint + offset, endCodePoint = it.endCodePoint + offset)) }
                offset += cell.text.codePointCount(0, cell.text.length)
            }
        }
    }
    else -> emptyList()
}

fun ReaderBlock.nestedBlocks(): List<ReaderBlock> = when (this) {
    is ReaderBlock.Listing -> listOf(this) + items.flatMap { item -> item.blocks.flatMap(ReaderBlock::nestedBlocks) }
    is ReaderBlock.Note -> listOf(this) + blocks.flatMap(ReaderBlock::nestedBlocks)
    is ReaderBlock.Post -> listOf(this) + blocks.flatMap(ReaderBlock::nestedBlocks)
    else -> listOf(this)
}

private fun collectReaderDocumentBlocks(roots: List<ReaderBlock>): List<ReaderBlock> {
    val flattened = ArrayList<ReaderBlock>(roots.size)
    fun append(block: ReaderBlock, depth: Int) {
        require(depth <= MAX_READER_BLOCK_DEPTH) { "Reader document exceeds block depth limit" }
        require(flattened.size < MAX_READER_NESTED_BLOCKS) { "Reader document exceeds nested block limit" }
        flattened.add(block)
        when (block) {
            is ReaderBlock.Listing -> for (item in block.items) for (child in item.blocks) append(child, depth + 1)
            is ReaderBlock.Note -> for (child in block.blocks) append(child, depth + 1)
            is ReaderBlock.Post -> for (child in block.blocks) append(child, depth + 1)
            else -> Unit
        }
    }
    for (root in roots) append(root, 1)
    return flattened
}

private fun validateReaderSpans(text: String, spans: List<ReaderSpan>) {
    require(spans.size <= MAX_READER_SPANS) { "Too many Reader spans" }
    if (spans.isEmpty()) return
    val length = text.codePointCount(0, text.length)
    if (spans.size == 1) {
        require(spans[0].endCodePoint <= length) { "Reader span exceeds canonical text" }
        return
    }
    val endpoints = IntArray(spans.size)
    var stackSize = 0
    var previousStart = -1
    for (span in spans) {
        require(span.endCodePoint <= length) { "Reader span exceeds canonical text" }
        require(span.startCodePoint >= previousStart) {
            "Reader spans must be ordered by canonical code-point offset"
        }
        previousStart = span.startCodePoint
        while (stackSize > 0 && span.startCodePoint >= endpoints[stackSize - 1]) stackSize--
        require(stackSize == 0 || span.endCodePoint <= endpoints[stackSize - 1]) {
            "Crossing Reader spans are not supported"
        }
        endpoints[stackSize++] = span.endCodePoint
    }
}

private fun requireReaderBlockId(value: String) = requireReaderTextId(value, "block ID", 1024)

private fun requireReaderTextId(value: String, field: String, maxCodePoints: Int, allowEmpty: Boolean = false) {
    val length = value.codePointCount(0, value.length)
    require(if (allowEmpty) length <= maxCodePoints else length in 1..maxCodePoints) { "Invalid Reader $field" }
}

private fun isCanonicalUuid(value: String): Boolean = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

private const val MAX_READER_BLOCK_TEXT = 200_000
private const val MAX_READER_TABLE_TEXT = 100_000
const val MAX_READER_DOCUMENT_BLOCKS = 50_000
const val MAX_READER_NESTED_BLOCKS = 50_000
const val MAX_READER_BLOCK_DEPTH = 96
const val MAX_READER_BODY_BLOCKS = 5000
private const val MAX_READER_SPANS = 20_000
private const val MAX_READER_LIST_ITEMS = 10_000
private const val MAX_READER_TABLE_ROWS = 20_000
private const val MAX_READER_TABLE_COLUMNS = 16
private const val MAX_READER_ANCHORS = 50_000
private const val MAX_READER_ANCHOR_ID = 512
private const val MAX_READER_IMAGE_PIXELS = 50_000_000L
