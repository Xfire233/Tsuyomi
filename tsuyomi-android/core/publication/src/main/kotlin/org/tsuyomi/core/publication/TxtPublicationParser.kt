/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.librarydomain.LocalTextHeading
import org.tsuyomi.shared.librarydomain.LocalTextParseOptions
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.sourcecontract.ReaderAnchorTarget
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument

internal class TxtPublicationParser(
    private val publicationId: String,
    private val source: PublicationSource,
) {
    fun parse(options: PublicationOptions, cancelled: () -> Unit): PublicationParseResult {
        checkCancelled(cancelled)
        val bytes = readOriginal(cancelled)
        val bom = detectBom(bytes)
        val requested = options.txt.charsetName
        val selected = when {
            requested != null -> {
                if (bom != null && bom.charsetName != requested) {
                    fail(PublicationFailureCode.INVALID_TEXT_ENCODING, "The selected charset conflicts with the original BOM.")
                }
                decodeCandidate(bytes, requested, bom?.byteCount ?: 0, cancelled)
                    ?: fail(PublicationFailureCode.INVALID_TEXT_ENCODING)
            }
            bom != null -> decodeCandidate(bytes, bom.charsetName, bom.byteCount, cancelled)
                ?: fail(PublicationFailureCode.INVALID_TEXT_ENCODING)
            else -> decodeCandidate(bytes, "UTF-8", 0, cancelled) ?: run {
                val candidates = charsetNames.mapNotNull { name ->
                    val decoded = try {
                        decodeCandidate(bytes, name, 0, cancelled)
                    } catch (error: PublicationParseException) {
                        if (error.code in setOf(PublicationFailureCode.INVALID_TEXT_CONTENT, PublicationFailureCode.INVALID_TEXT_ENCODING, PublicationFailureCode.TEXT_LIMIT)) null
                        else throw error
                    } ?: return@mapNotNull null
                    TxtCharsetCandidate(name, preview(decoded.text), headingSuggestions(decoded.paragraphs))
                }
                when (candidates.size) {
                    0 -> fail(PublicationFailureCode.INVALID_TEXT_ENCODING, "No supported charset strictly decodes this text.")
                    1 -> decodeCandidate(bytes, candidates.single().charsetName, 0, cancelled)
                        ?: fail(PublicationFailureCode.INVALID_TEXT_ENCODING)
                    else -> return PublicationParseResult.NeedsCharsetChoice(candidates)
                }
            }
        }
        checkCancelled(cancelled)
        return PublicationParseResult.Ready(buildPublication(
            bytes = bytes,
            decoded = selected,
            requestedOptions = options.txt,
            cancelled = cancelled,
        ))
    }

    private fun buildPublication(
        bytes: ByteArray,
        decoded: DecodedText,
        requestedOptions: LocalTextParseOptions,
        cancelled: () -> Unit,
    ): ParsedPublication {
        val paragraphs = decoded.paragraphs
        if (paragraphs.isEmpty()) fail(PublicationFailureCode.EMPTY_TEXT)
        if (requestedOptions.headingSelections.any { it.paragraphOrdinal >= paragraphs.size }) {
            fail(PublicationFailureCode.INVALID_HEADING_SELECTION, "A selected TXT heading is outside the decoded document.")
        }
        val selections = requestedOptions.headingSelections.associateBy(LocalTextHeading::paragraphOrdinal)
        val boundaryIndexes = if (selections.isEmpty()) {
            paragraphs.indices.filter { it % requestedOptions.paragraphsPerSection == 0 }
        } else {
            (listOf(0) + selections.keys).distinct().sorted()
        }
        if (boundaryIndexes.size > MAX_SECTIONS) fail(PublicationFailureCode.SECTION_LIMIT)
        val sectionRanges = boundaryIndexes.mapIndexed { index, start ->
            start until (boundaryIndexes.getOrNull(index + 1) ?: paragraphs.size)
        }
        val sections = ArrayList<PublicationSection>(sectionRanges.size)
        val sectionById = LinkedHashMap<String, List<TxtParagraph>>()
        val toc = ArrayList<PublicationTocEntry>()
        for ((sectionOrdinal, range) in sectionRanges.withIndex()) {
            checkCancelled(cancelled)
            val sectionParagraphs = paragraphs.subList(range.first, range.last + 1)
            val first = sectionParagraphs.first()
            val sectionId = contentId(first.ordinal, source.sha256)
            val heading = selections[first.ordinal]
            val title = heading?.title ?: first.text.takeCodePoints(MAX_TITLE_CODE_POINTS).ifBlank { "Section ${sectionOrdinal + 1}" }
            val anchors = LinkedHashMap<String, ReaderAnchorTarget>(sectionParagraphs.size)
            for (paragraph in sectionParagraphs) {
                anchors[anchorId(paragraph.ordinal)] = ReaderAnchorTarget(blockId(paragraph.ordinal), 0)
            }
            sections += PublicationSection(
                contentId = sectionId,
                title = title,
                ordinal = sectionOrdinal,
                anchors = anchors,
                codePointStart = first.codePointStart,
            )
            sectionById[sectionId] = sectionParagraphs
            if (heading != null) toc += PublicationTocEntry(title, sectionId, anchorId(first.ordinal))
        }
        if (sections.isEmpty()) fail(PublicationFailureCode.EMPTY_TEXT)

        val effectiveOptions = PublicationOptions(requestedOptions.copy(charsetName = decoded.charsetName))
        val contentRevision = contentRevision(source.sha256, decoded.charsetName, requestedOptions)
        val metadataTitle = paragraphs.first().text.takeCodePoints(MAX_TITLE_CODE_POINTS).ifBlank { "Untitled text" }
        return ParsedPublication(
            publicationId = publicationId,
            format = LocalPublicationFormat.TXT,
            originalSha256 = source.sha256,
            originalSizeBytes = source.sizeBytes,
            contentRevision = contentRevision,
            options = effectiveOptions,
            metadata = PublicationMetadata(LocalPublicationFormat.TXT, metadataTitle, emptyList()),
            sections = sections,
            tableOfContents = toc,
            resources = emptyList(),
            compatibility = PublicationCompatibilityReport(PublicationCompatibilityLevel.SUPPORTED, emptyList()),
            txtPreview = TxtCharsetCandidate(decoded.charsetName, preview(decoded.text), headingSuggestions(paragraphs)),
            loadSectionBlock = { contentId, identity, check ->
                val book = identity.book
                if (book !is org.tsuyomi.shared.model.BookIdentity.Local || book.publicationId != publicationId) {
                    fail(PublicationFailureCode.INVALID_ORIGINAL, "Document identity belongs to a different local publication.")
                }
                val section = sectionById[contentId] ?: fail(PublicationFailureCode.MISSING_SPINE_ITEM)
                loadSection(contentId, section, selections, identity, check)
            },
            openResourceStream = { _, _ -> fail(PublicationFailureCode.MISSING_RESOURCE, "TXT publications contain no resources.") },
        )
    }

    private fun loadSection(
        contentId: String,
        paragraphs: List<TxtParagraph>,
        selections: Map<Int, LocalTextHeading>,
        identity: DocumentIdentity,
        cancelled: () -> Unit,
    ): ReaderDocument {
        checkCancelled(cancelled)
        val blocks = ArrayList<ReaderBlock>()
        val anchors = LinkedHashMap<String, ReaderAnchorTarget>(paragraphs.size)
        for (paragraph in paragraphs) {
            checkCancelled(cancelled)
            val text = paragraph.text
            val heading = selections[paragraph.ordinal]
            val pieces = splitForReader(text)
            for ((pieceIndex, piece) in pieces.withIndex()) {
                val id = if (pieceIndex == 0) blockId(paragraph.ordinal) else "${blockId(paragraph.ordinal)}.part$pieceIndex"
                val anchor = if (pieceIndex == 0) anchorId(paragraph.ordinal) else null
                val block = if (heading != null && pieceIndex == 0) {
                    ReaderBlock.Heading(id, piece, level = 1, anchorId = anchor)
                } else ReaderBlock.Paragraph(id, piece, anchorId = anchor)
                blocks += block
                if (blocks.size > MAX_READER_SECTION_BLOCKS) fail(PublicationFailureCode.BLOCK_LIMIT)
                if (pieceIndex == 0) anchors[anchorId(paragraph.ordinal)] = ReaderAnchorTarget(id, 0)
            }
        }
        if (blocks.isEmpty()) fail(PublicationFailureCode.EMPTY_TEXT)
        return ReaderDocument(identity, paragraphs.first().let { selections[it.ordinal]?.title ?: it.text.takeCodePoints(MAX_TITLE_CODE_POINTS) }, blocks, anchors,
            kind = org.tsuyomi.shared.sourcecontract.ReaderDocumentKind.CHAPTER)
    }

    private fun readOriginal(cancelled: () -> Unit): ByteArray {
        if (source.sizeBytes <= 0 || source.sizeBytes > MAX_TEXT_BYTES || !SHA256.matches(source.sha256)) {
            fail(PublicationFailureCode.ORIGINAL_LIMIT)
        }
        val output = ByteArrayOutputStream(minOf(source.sizeBytes.toInt(), 8192))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            source.openInputStream().use { input ->
                while (true) {
                    checkCancelled(cancelled)
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) {
                        val single = input.read()
                        if (single < 0) break
                        if (output.size().toLong() >= MAX_TEXT_BYTES) fail(PublicationFailureCode.TEXT_LIMIT)
                        output.write(single)
                    } else {
                        if (count.toLong() > MAX_TEXT_BYTES - output.size()) fail(PublicationFailureCode.TEXT_LIMIT)
                        output.write(buffer, 0, count)
                    }
                }
            }
        } catch (error: PublicationParseException) {
            throw error
        } catch (error: IOException) {
            throw PublicationParseException(PublicationFailureCode.INVALID_ORIGINAL, cause = error)
        }
        if (output.size().toLong() != source.sizeBytes) fail(PublicationFailureCode.INVALID_ORIGINAL, "TXT source size changed while reading.")
        return output.toByteArray()
    }

    private fun decodeCandidate(bytes: ByteArray, charsetName: String, offset: Int, cancelled: () -> Unit): DecodedText? {
        checkCancelled(cancelled)
        val charset = try {
            Charset.forName(charsetName)
        } catch (error: Exception) {
            return null
        }
        val text = try {
            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        validateDecodedText(text, cancelled)
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val paragraphs = splitLines(normalized, cancelled)
        return DecodedText(charset.name(), normalized, paragraphs)
    }

    private fun validateDecodedText(text: String, cancelled: () -> Unit) {
        var index = 0
        var checked = 0
        while (index < text.length) {
            if ((checked++ and 0x3fff) == 0) checkCancelled(cancelled)
            val current = text[index]
            if ((current.code < 0x20 && current != '\n' && current != '\r' && current != '\t') || current == '\u007f') {
                fail(PublicationFailureCode.INVALID_TEXT_CONTENT, "Decoded text contains forbidden control characters.")
            }
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= text.length || !Character.isLowSurrogate(text[index + 1])) {
                    fail(PublicationFailureCode.INVALID_TEXT_ENCODING)
                }
                index += 2
            } else {
                if (Character.isLowSurrogate(current)) fail(PublicationFailureCode.INVALID_TEXT_ENCODING)
                index++
            }
        }
        if (text.codePointCount(0, text.length) > MAX_TEXT_CODE_POINTS) fail(PublicationFailureCode.TEXT_LIMIT)
    }

    private fun splitLines(text: String, cancelled: () -> Unit): List<TxtParagraph> {
        val paragraphs = ArrayList<TxtParagraph>()
        var lineStart = 0
        var codePointStart = 0L
        while (lineStart <= text.length) {
            checkCancelled(cancelled)
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, lineEnd)
            if (line.isNotBlank()) {
                if (paragraphs.size >= MAX_PARAGRAPHS) fail(PublicationFailureCode.TEXT_LIMIT)
                paragraphs += TxtParagraph(paragraphs.size, line, codePointStart)
            }
            codePointStart += text.codePointCount(lineStart, lineEnd) + if (lineEnd < text.length) 1 else 0
            if (lineEnd == text.length) break
            lineStart = lineEnd + 1
        }
        return paragraphs
    }

    private fun headingSuggestions(paragraphs: List<TxtParagraph>): List<TxtHeadingSuggestion> =
        paragraphs.asSequence().filter { CHAPTER_HEADING.matches(it.text.trim()) }
            .take(MAX_HEADING_SUGGESTIONS).map { TxtHeadingSuggestion(it.ordinal, it.text.trim().takeCodePoints(MAX_TITLE_CODE_POINTS)) }
            .toList()

    private fun preview(text: String): String = text.takeCodePoints(MAX_PREVIEW_CODE_POINTS)

    private fun splitForReader(text: String): List<String> {
        val output = ArrayList<String>()
        var start = 0
        var points = 0
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            val width = Character.charCount(point)
            if (points == MAX_READER_BLOCK_CODE_POINTS) {
                output += text.substring(start, index)
                start = index
                points = 0
            }
            points++
            index += width
        }
        if (start < text.length) output += text.substring(start)
        return output.ifEmpty { listOf(text) }
    }

    private fun contentId(paragraphOrdinal: Int, digest: String) =
        "txt:$paragraphOrdinal:${digest.take(16)}"

    private fun blockId(paragraphOrdinal: Int) = "txt:p:$paragraphOrdinal"
    private fun anchorId(paragraphOrdinal: Int) = "p:$paragraphOrdinal"

    private fun contentRevision(digest: String, charsetName: String, options: LocalTextParseOptions): String {
        val normalized = buildString {
            append(digest).append('|').append(charsetName).append('|').append(options.paragraphsPerSection)
            for (heading in options.headingSelections) append('|').append(heading.paragraphOrdinal).append(':').append(heading.title)
        }
        return sha256(normalized.toByteArray(Charsets.UTF_8))
    }

    private fun checkCancelled(cancelled: () -> Unit) {
        if (Thread.currentThread().isInterrupted) throw CancellationException("TXT parsing was cancelled")
        cancelled()
    }

    private fun fail(code: PublicationFailureCode, message: String = code.name): Nothing =
        throw PublicationParseException(code, message)

    private data class Bom(val charsetName: String, val byteCount: Int)
    private data class TxtParagraph(val ordinal: Int, val text: String, val codePointStart: Long)
    private data class DecodedText(val charsetName: String, val text: String, val paragraphs: List<TxtParagraph>)

    private fun detectBom(bytes: ByteArray): Bom? = when {
        bytes.startsWith(byteArrayOf(0x00, 0x00, 0xfe.toByte(), 0xff.toByte())) -> Bom("UTF-32BE", 4)
        bytes.startsWith(byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x00, 0x00)) -> Bom("UTF-32LE", 4)
        bytes.startsWith(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())) -> Bom("UTF-8", 3)
        bytes.startsWith(byteArrayOf(0xfe.toByte(), 0xff.toByte())) -> Bom("UTF-16BE", 2)
        bytes.startsWith(byteArrayOf(0xff.toByte(), 0xfe.toByte())) -> Bom("UTF-16LE", 2)
        else -> null
    }

    private fun String.takeCodePoints(count: Int): String {
        val end = offsetByCodePoints(0, minOf(count, codePointCount(0, length)))
        return substring(0, end)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private const val MAX_TEXT_BYTES = 32 * 1024 * 1024L
        private const val MAX_TEXT_CODE_POINTS = 16_000_000
        private const val MAX_PARAGRAPHS = 20_000
        private const val MAX_SECTIONS = 20_000
        private const val MAX_HEADING_SUGGESTIONS = 20_000
        private const val MAX_PREVIEW_CODE_POINTS = 320
        private const val MAX_TITLE_CODE_POINTS = 512
        private const val MAX_READER_BLOCK_CODE_POINTS = 100_000
        private const val MAX_READER_SECTION_BLOCKS = 20_000
        private val SHA256 = Regex("^[0-9a-f]{64}$")
        private val CHAPTER_HEADING = Regex("^第[零〇一二三四五六七八九十百千万两0-9０-９]+[章回卷节部][\\s　:：、.-]*.{0,64}$")
        private val charsetNames = listOf("GB18030", "Big5", "UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE")
    }
}
