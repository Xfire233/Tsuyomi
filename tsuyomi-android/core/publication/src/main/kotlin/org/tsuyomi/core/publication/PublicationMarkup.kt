/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.util.ArrayDeque
import java.util.Locale
import org.tsuyomi.shared.sourcecontract.ReaderAnchorTarget
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderInlineKind
import org.tsuyomi.shared.sourcecontract.ReaderLinkTarget
import org.tsuyomi.shared.sourcecontract.ReaderListItem
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import org.tsuyomi.shared.sourcecontract.ReaderSpan
import org.tsuyomi.shared.sourcecontract.ReaderTableCell
import org.tsuyomi.shared.sourcecontract.ReaderTableRow

/** Parser-independent, inert markup tree used by EPUB and future MOBI/KF8 adapters. */
sealed interface PublicationMarkupPart {
    data class Text(val value: String) : PublicationMarkupPart
    data class Element(val value: PublicationMarkupElement) : PublicationMarkupPart
}

data class PublicationMarkupAttribute(val namespace: String, val name: String, val value: String)

data class PublicationMarkupElement(
    val namespace: String,
    val name: String,
    val attributes: List<PublicationMarkupAttribute>,
    val children: List<PublicationMarkupPart>,
) {
    fun attribute(name: String, namespace: String = ""): String? =
        attributes.firstOrNull { it.name == name && it.namespace == namespace }?.value

    fun elements(name: String? = null, namespace: String? = null): List<PublicationMarkupElement> =
        children.mapNotNull { (it as? PublicationMarkupPart.Element)?.value }
            .filter { (name == null || it.name.equals(name, true)) && (namespace == null || it.namespace == namespace) }

    fun textContent(): String = buildString {
        fun append(element: PublicationMarkupElement) {
            for (part in element.children) when (part) {
                is PublicationMarkupPart.Text -> append(part.value)
                is PublicationMarkupPart.Element -> append(part.value)
            }
        }
        append(this@PublicationMarkupElement)
    }
}

data class PublicationStylesheet(val sourceName: String, val text: String)

data class PublicationMarkupSection(
    val contentId: String,
    val body: PublicationMarkupElement,
    val stylesheets: List<PublicationStylesheet> = emptyList(),
    val resolveLink: (String) -> ReaderLinkTarget?,
    val resolveImage: (String) -> ReaderResourceRef?,
)

data class NormalizedPublicationMarkup(
    val blocks: List<ReaderBlock>,
    val anchors: Map<String, ReaderAnchorTarget>,
    val findings: List<PublicationCompatibilityFinding>,
    val firstHeading: String?,
)

/**
 * Converts a bounded inert markup tree into Reader DTOs. It never interprets HTML, CSS, scripts,
 * URLs, or resource bytes as executable content. CSS support is intentionally limited to simple
 * type/class/id selectors and emphasis/weight/decoration declarations.
 */
class PublicationMarkupNormalizer {
    fun normalize(
        section: PublicationMarkupSection,
        cancelled: () -> Unit = {},
    ): NormalizedPublicationMarkup = Normalization(section, cancelled).run()

    fun normalizeHtml(
        contentId: String,
        html: String,
        stylesheets: List<PublicationStylesheet> = emptyList(),
        resolveLink: (String) -> ReaderLinkTarget?,
        resolveImage: (String) -> ReaderResourceRef?,
        cancelled: () -> Unit = {},
    ): NormalizedPublicationMarkup {
        val document = PublicationHtmlTokenizer(html, cancelled).parse()
        val normalized = normalize(
            PublicationMarkupSection(
                contentId = contentId,
                body = document.body,
                stylesheets = stylesheets + document.stylesheets,
                resolveLink = resolveLink,
                resolveImage = resolveImage,
            ),
            cancelled,
        )
        return normalized.copy(findings = (document.findings + normalized.findings).distinctBy {
            Triple(it.code, it.contentId, it.blockId)
        })
    }

    private class Normalization(
        private val section: PublicationMarkupSection,
        private val cancelled: () -> Unit,
    ) {
        private var workCount = 0
        private var cssWorkUnits = 0L
        private val blocks = ArrayList<ReaderBlock>()
        private val anchors = LinkedHashMap<String, ReaderAnchorTarget>()
        private val findings = LinkedHashMap<String, PublicationCompatibilityFinding>()
        private val pendingAnchors = ArrayDeque<String>()
        private val styleRules = ArrayList<CssRule>()
        private var blockOrdinal = 0
        private var heading: String? = null
        private var noteIdsByAnchor: Map<String, String> = emptyMap()

        fun run(): NormalizedPublicationMarkup {
            checkpoint()
            if (section.stylesheets.size > MAX_SECTION_STYLESHEET_REFERENCES ||
                section.stylesheets.sumOf { it.text.length.toLong() } > MAX_SECTION_STYLESHEET_CHARACTERS) {
                fail(PublicationFailureCode.BLOCK_LIMIT, "Section stylesheet applications exceeded the content work budget")
            }
            section.stylesheets.forEach(::parseStylesheet)
            noteIdsByAnchor = collectNoteIds(section.body)
            flow(section.body)
            if (blocks.size > MAX_MARKUP_BLOCKS) fail(PublicationFailureCode.BLOCK_LIMIT)
            return NormalizedPublicationMarkup(blocks.toList(), anchors.toMap(), findings.values.toList(), heading)
        }

        private fun flow(element: PublicationMarkupElement) {
            checkpoint()
            val name = element.name.lowercase(Locale.ROOT)
            when {
                name in setOf("head", "title", "meta", "link", "style") -> Unit
                name in BLOCK_TEXT_TAGS -> textBlock(element, name)
                name == "img" -> image(element)
                name == "svg" -> svgImage(element)
                name == "hr" -> divider(element)
                name == "ul" || name == "ol" -> listing(element, name == "ol")
                name == "table" -> table(element)
                name == "aside" && isNote(element) -> note(element)
                name in UNSUPPORTED_BLOCK_TAGS -> unsupported(element, name)
                name in GENERIC_BLOCK_TAGS || element.namespace != XHTML_NAMESPACE -> textBlock(element, "p")
                else -> {
                    if (element.attribute("id") != null) element.attribute("id")?.let(pendingAnchors::addLast)
                    val text = plainText(element).text
                    if (text.isNotBlank()) {
                        issue("UNSUPPORTED_MARKUP_ELEMENT", PublicationCompatibilitySeverity.DEGRADED,
                            "The ${safeTag(name)} element was retained as plain text.")
                        val accumulator = TextAccumulator()
                        accumulator.append(text)
                        emitText(element, "p", accumulator)
                    } else {
                        issue("UNSUPPORTED_MARKUP_ELEMENT", PublicationCompatibilitySeverity.DEGRADED,
                            "The ${safeTag(name)} element has no supported Reader representation.")
                    }
                }
            }
        }

        private fun textBlock(element: PublicationMarkupElement, name: String, includeElementAnchor: Boolean = true) {
            val initial = ArrayList<String>()
            while (pendingAnchors.isNotEmpty()) initial += pendingAnchors.removeFirst()
            if (includeElementAnchor) element.attribute("id")?.let(initial::add)
            val level = name.removePrefix("h").toIntOrNull()
            val forcedKind = if (name == "pre") ReaderInlineKind.Code else null
            val accumulator = TextAccumulator()
            val blockAnchors = ArrayList<Pair<String, Int>>()
            blockAnchors += initial.map { it to 0 }
                checkpoint()
            var firstFragment = true
            fun flush() {
                if (!accumulator.hasText()) {
                    if (blockAnchors.isNotEmpty()) firstFragment = false
                    blockAnchors.forEach { pendingAnchors.addLast(it.first) }
                    blockAnchors.clear()
                    accumulator.resetSegment()
                    return
                }
                emitText(element, name, accumulator, blockAnchors.toList(), forcedKind, firstFragment && includeElementAnchor)
                firstFragment = false
                accumulator.resetSegment()
                blockAnchors.clear()
            }
            val kinds = inlineKinds(element, name)
            accumulator.withKinds(kinds) {
                inlineParts(element.children, kinds, accumulator, blockAnchors, name == "pre",
                    flattenBlockDescendants = name == "blockquote", flush = ::flush)
                flush()
            }
            if (level != null && heading == null) heading = collapse(element.textContent())
        }

        private fun inlineParts(
            parts: List<PublicationMarkupPart>,
            inherited: List<ReaderInlineKind>,
            accumulator: TextAccumulator,
            blockAnchors: MutableList<Pair<String, Int>>,
            preformatted: Boolean,
            flattenBlockDescendants: Boolean,
            flush: () -> Unit,
        ) {
            for (part in parts) {
                checkpoint()
                when (part) {
                is PublicationMarkupPart.Text -> accumulator.append(part.value, preformatted)
                is PublicationMarkupPart.Element -> {
                    val element = part.value
                    val name = element.name.lowercase(Locale.ROOT)
                    when {
                        name == "img" -> {
                            flush()
                            image(element)
                        }
                        name == "svg" -> {
                            flush()
                            svgImage(element)
                        }
                        name in BLOCK_BOUNDARY_TAGS && flattenBlockDescendants -> {
                            element.attribute("id")?.let { blockAnchors += it to accumulator.codePointLength() }
                            if (name == "ul" || name == "ol" || name == "table" || name == "aside") {
                                issue("QUOTE_NESTED_CONTENT_DEGRADED", PublicationCompatibilitySeverity.DEGRADED,
                                    "Nested list, table, or note content inside a quote was retained as plain text.")
                                accumulator.append(plainText(element).text, preformatted)
                            } else {
                                accumulator.append(" ", preformatted)
                                inlineParts(element.children, inherited, accumulator, blockAnchors, preformatted, true, flush)
                                accumulator.append(" ", preformatted)
                            }
                        }
                        name in BLOCK_BOUNDARY_TAGS -> {
                            flush()
                            flow(element)
                        }
                        name == "br" || name == "wbr" -> if (name == "br") accumulator.lineBreak(preformatted)
                        name in NON_RENDERED_TAGS -> {
                            if (name == "script") {
                                flush()
                                unsupported(element, name)
                            } else issue("UNSUPPORTED_MARKUP_ELEMENT", PublicationCompatibilitySeverity.DEGRADED,
                                "The ${safeTag(name)} element was not rendered.")
                        }
                        name in UNSUPPORTED_INLINE_TAGS -> {
                            flush()
                            unsupported(element, name)
                        }
                        else -> {
                            element.attribute("id")?.let { blockAnchors += it to accumulator.codePointLength() }
                            val ownKinds = inlineKinds(element, name)
                            val link = if (name == "a") element.attribute("href") else null
                            val target = link?.let { raw ->
                                val resolved = section.resolveLink(raw)
                                if (resolved == null) issue("UNSUPPORTED_LINK_TARGET", PublicationCompatibilitySeverity.DEGRADED,
                                    "A link target with an unsupported scheme or destination was disabled.")
                                resolved
                            }
                            val isNoteref = element.attribute("type", EPUB_NAMESPACE).orEmpty()
                                .split(WHITESPACE).contains("noteref")
                            val internal = target as? ReaderLinkTarget.Internal
                            val noteReference = if (isNoteref && internal?.contentId == section.contentId) {
                                internal.fragment?.let(noteIdsByAnchor::get)?.let { ReaderInlineKind.NoteReference(it) }
                            } else null
                            val linkKind = noteReference ?: target?.let(ReaderInlineKind::Link)
                            val localKinds = ownKinds + listOfNotNull(linkKind)
                            val completeKinds = inherited + localKinds
                            val start = accumulator.codePointLength()
                            val ruby = if (name == "ruby") rubyAnnotation(element) else null
                            val appliedKinds = localKinds + listOfNotNull(ruby?.let(ReaderInlineKind::Ruby))
                            accumulator.withKinds(appliedKinds) {
                                if (ruby != null) {
                                    rubyBaseParts(element.children, completeKinds, accumulator, blockAnchors,
                                        preformatted, flattenBlockDescendants, flush)
                                } else {
                                    inlineParts(element.children, completeKinds, accumulator, blockAnchors,
                                        preformatted, flattenBlockDescendants, flush)
                                }
                            }
                            if (start == accumulator.codePointLength() && name == "a" && link != null) {
                                issue("EMPTY_LINK", PublicationCompatibilitySeverity.DEGRADED,
                                    "An empty link has no visible Reader target.")
                            }
                        }
                    }
                }
            }
            }
        }

        private fun rubyBaseParts(
            children: List<PublicationMarkupPart>,
            inherited: List<ReaderInlineKind>,
            accumulator: TextAccumulator,
            blockAnchors: MutableList<Pair<String, Int>>,
            preformatted: Boolean,
            flattenBlockDescendants: Boolean,
            flush: () -> Unit,
        ) {
            inlineParts(children.filterNot { part ->
                val child = (part as? PublicationMarkupPart.Element)?.value ?: return@filterNot false
                child.name.equals("rt", true) || child.name.equals("rp", true)
            }, inherited, accumulator, blockAnchors, preformatted, flattenBlockDescendants, flush)
        }

        private fun rubyAnnotation(element: PublicationMarkupElement): String? {
            val readings = element.elements().filter { it.name.equals("rt", true) }.map { collapse(it.textContent()) }
            val annotation = readings.filter(String::isNotEmpty).joinToString("")
            if (annotation.isEmpty()) return null
            if (annotation.codePointCount(0, annotation.length) > MAX_RUBY_CODE_POINTS) {
                issue("RUBY_ANNOTATION_LIMIT", PublicationCompatibilitySeverity.DEGRADED,
                    "Ruby annotation exceeded the supported length and was shown as plain text.")
                return null
            }
            return annotation
        }

        private fun inlineKinds(element: PublicationMarkupElement, name: String): List<ReaderInlineKind> {
            val kinds = LinkedHashSet<ReaderInlineKind>()
            when (name) {
                "b", "strong" -> kinds += ReaderInlineKind.Strong
                "i", "em" -> kinds += ReaderInlineKind.Emphasis
                "u", "ins" -> kinds += ReaderInlineKind.Underline
                "s", "strike", "del" -> kinds += ReaderInlineKind.Strike
                "code", "kbd", "samp" -> kinds += ReaderInlineKind.Code
            }
            if (styleRules.isNotEmpty()) {
                val selectorWork = 1L + element.name.length +
                    element.attributes.sumOf { it.name.length.toLong() + it.value.length }
                for (rule in styleRules) {
                    accountCssWork(selectorWork)
                    if (rule.selector.matches(element)) kinds += rule.kinds
                }
            }
            element.attribute("style")?.let { parseDeclarations(it, "inline style", kinds) }
            return kinds.toList()
        }

        private fun emitText(
            element: PublicationMarkupElement,
            name: String,
            accumulator: TextAccumulator,
            explicitAnchors: List<Pair<String, Int>> = emptyList(),
            forcedKind: ReaderInlineKind? = null,
            includeElementId: Boolean = true,
        ) {
            val text = accumulator.value()
            if (text.isBlank()) return
            val allAnchors = ArrayList<Pair<String, Int>>()
            while (pendingAnchors.isNotEmpty()) allAnchors += pendingAnchors.removeFirst() to 0
            allAnchors += explicitAnchors
            val authoredId = if (includeElementId) element.attribute("id") else null
            if (authoredId != null && allAnchors.none { it.first == authoredId }) allAnchors += authoredId to 0
            val blockId = stableBlockId(authoredId, blockOrdinal++)
            val blockAnchor = allAnchors.firstOrNull { it.second == 0 }?.first?.takeIf { it.length <= MAX_READER_ANCHOR_LENGTH }
            val spans = (accumulator.spans() + listOfNotNull(forcedKind?.let { ReaderSpan(0, text.codePointCount(0, text.length), it) }))
                .filter { it.endCodePoint > it.startCodePoint }
                .sortedWith(compareBy<ReaderSpan> { it.startCodePoint }.thenByDescending { it.endCodePoint })
            val level = name.removePrefix("h").toIntOrNull()
            val block = when {
                level != null -> ReaderBlock.Heading(blockId, text, level.coerceIn(1, 6), spans, blockAnchor)
                name == "blockquote" -> ReaderBlock.Quote(blockId, text, spans, anchorId = blockAnchor)
                else -> ReaderBlock.Paragraph(blockId, text, spans, blockAnchor, preformatted = name == "pre")
            }
            blocks += block
            registerAnchors(allAnchors, blockId, text.codePointCount(0, text.length))
        }

        private fun svgImage(element: PublicationMarkupElement) {
            // This is not an SVG renderer. Only a plain, single raster-image wrapper is admitted;
            // vector drawing, transforms, clipping, scripts and external resources remain inert.
            val children = descendants(element)
            val image = children.singleOrNull { it.namespace == SVG_NAMESPACE && it.name == "image" }
            val safe = element.namespace == SVG_NAMESPACE && image != null &&
                children.all { it.namespace == SVG_NAMESPACE && it.name in SVG_WRAPPER_TAGS } &&
                (children + element).none { node ->
                    node.attributes.any { attribute ->
                        attribute.name.startsWith("on", true) || attribute.name in SVG_UNSAFE_ATTRIBUTES
                    }
                } && image.attribute("x").orEmpty() in setOf("", "0", "0px") &&
                image.attribute("y").orEmpty() in setOf("", "0", "0px")
            if (!safe || image == null) {
                unsupported(element, "svg")
                return
            }
            val href = image.attribute("href") ?: image.attribute("href", XLINK_NAMESPACE)
            val uri = href?.let { runCatching { java.net.URI(it) }.getOrNull() }
            if (href == null || uri == null || uri.isAbsolute || uri.rawAuthority != null || uri.rawFragment != null) {
                unsupported(element, "svg")
                return
            }
            val resource = try {
                section.resolveImage(href) as? ReaderResourceRef.Local
            } catch (error: PublicationParseException) {
                if (error.code != PublicationFailureCode.UNSUPPORTED_RESOURCE_TYPE) throw error
                null
            }
            if (resource == null) {
                unsupported(element, "svg")
                return
            }
            element.attribute("id")?.let(pendingAnchors::addLast)
            val attributes = image.attributes.filter { it.name in setOf("id", "width", "height") } +
                PublicationMarkupAttribute("", "src", href) +
                listOfNotNull(element.attribute("aria-label")?.let { PublicationMarkupAttribute("", "alt", it) })
            this.image(PublicationMarkupElement(XHTML_NAMESPACE, "img", attributes, emptyList()), resource)
        }

        private fun image(element: PublicationMarkupElement, admittedResource: ReaderResourceRef? = null) {
            val src = element.attribute("src")
            if (src == null) {
                unsupported(element, "image-without-source")
                return
            }
            val resource = admittedResource ?: section.resolveImage(src)
            if (resource == null) {
                unsupported(element, "unsupported-image-target")
                issue("UNSUPPORTED_IMAGE_TARGET", PublicationCompatibilitySeverity.DEGRADED,
                    "An image with an unsupported resource target could not be loaded.")
                return
            }
            val alt = element.attribute("alt")?.let(::collapse)?.takeIf(String::isNotEmpty)
            val width = parseDimension(element.attribute("width"), "width")
            val height = parseDimension(element.attribute("height"), "height")
            if (width != null && height != null && width.toLong() * height > MAX_IMAGE_PIXELS) {
                fail(PublicationFailureCode.INVALID_RESOURCE, "Declared image dimensions exceed the pixel budget")
            }
            val authoredId = element.attribute("id")
            val id = stableBlockId(authoredId, blockOrdinal++)
            val pending = ArrayList<String>()
            while (pendingAnchors.isNotEmpty()) pending += pendingAnchors.removeFirst()
            authoredId?.let(pending::add)
            val anchor = pending.firstOrNull()?.takeIf { it.length <= MAX_READER_ANCHOR_LENGTH }
            blocks += ReaderBlock.Image(id, resource, alt, width, height, anchor)
            registerAnchors(pending.map { it to 0 }, id, 0)
            if (resource is ReaderResourceRef.RemoteHttps) {
                issue("EXTERNAL_HTTPS_RESOURCE_DISABLED", PublicationCompatibilitySeverity.RESOURCE_DISABLED,
                    "This HTTPS image is not fetched during import or reading without an explicit publication grant.", id)
            }
        }

        private fun divider(element: PublicationMarkupElement) {
            val authoredId = element.attribute("id")
            val id = stableBlockId(authoredId, blockOrdinal++)
            val pending = ArrayList<String>()
            while (pendingAnchors.isNotEmpty()) pending += pendingAnchors.removeFirst()
            authoredId?.let(pending::add)
            val anchor = pending.firstOrNull()?.takeIf { it.length <= MAX_READER_ANCHOR_LENGTH }
            blocks += ReaderBlock.Divider(id, anchor)
            registerAnchors(pending.map { it to 0 }, id, 0)
        }

        private fun listing(element: PublicationMarkupElement, ordered: Boolean) {
            val authoredId = element.attribute("id")
            val outerPending = ArrayList<String>()
            while (pendingAnchors.isNotEmpty()) outerPending += pendingAnchors.removeFirst()
            val itemNodes = element.elements().filter { it.name.equals("li", true) }
            if (itemNodes.isEmpty()) {
                issue("EMPTY_LIST", PublicationCompatibilitySeverity.DEGRADED,
                    "An empty list has no readable items.")
                return
            }
            val listId = stableBlockId(authoredId, blockOrdinal++)
            val items = ArrayList<ReaderListItem>()
            for ((index, item) in itemNodes.withIndex()) {
                item.attribute("id")?.let(pendingAnchors::addLast)
                val start = blocks.size
                textBlock(item, "p")
                val itemBlocks = blocks.subList(start, blocks.size).toList()
                blocks.subList(start, blocks.size).clear()
                val fallbackText = collapse(item.textContent())
                val effective = if (itemBlocks.isNotEmpty()) itemBlocks else if (fallbackText.isNotEmpty()) {
                    val itemBlockId = stableBlockId(item.attribute("id"), blockOrdinal++)
                    listOf(ReaderBlock.Paragraph(itemBlockId, fallbackText))
                } else {
                    listOf(ReaderBlock.Unsupported(stableBlockId(item.attribute("id"), blockOrdinal++),
                        "empty-list-item", "This list item has no readable text."))
                }
                items += ReaderListItem("$listId:item:$index", effective)
            }
            val startValue = element.attribute("start")?.toIntOrNull() ?: 1
            if (element.attribute("start") != null && startValue !in 1..1_000_000) {
                issue("LIST_START_UNSUPPORTED", PublicationCompatibilitySeverity.DEGRADED,
                    "An out-of-range ordered-list start value was normalized to 1.")
            }
            val allListAnchors = outerPending + listOfNotNull(authoredId)
            val anchor = allListAnchors.firstOrNull()?.takeIf { it.length <= MAX_READER_ANCHOR_LENGTH }
            blocks += ReaderBlock.Listing(listId, ordered, startValue.coerceIn(1, 1_000_000), items, anchor)
            registerAnchors(allListAnchors.map { it to 0 }, listId, 0)
        }

        private fun note(element: PublicationMarkupElement) {
            val authoredId = element.attribute("id")
            val outerPending = ArrayList<String>()
            while (pendingAnchors.isNotEmpty()) outerPending += pendingAnchors.removeFirst()
            val start = blocks.size
            textBlock(element, "p", includeElementAnchor = false)
            val inner = blocks.subList(start, blocks.size).toList()
            blocks.subList(start, blocks.size).clear()
            val noteId = safeNoteId(authoredId ?: "note-${blockOrdinal}")
            val body = if (inner.isNotEmpty()) inner else {
                val text = collapse(element.textContent())
                if (text.isEmpty()) listOf(ReaderBlock.Unsupported(stableBlockId(authoredId, blockOrdinal++),
                    "empty-note", "This note has no readable text."))
                else listOf(ReaderBlock.Paragraph(stableBlockId(authoredId, blockOrdinal++), text))
            }
            val blockId = stableBlockId(authoredId, blockOrdinal++)
            val all = outerPending + listOfNotNull(authoredId)
            val anchor = all.firstOrNull()?.takeIf { it.length <= MAX_READER_ANCHOR_LENGTH } ?: noteId
            blocks += ReaderBlock.Note(blockId, noteId, element.attribute("epub:label", EPUB_NAMESPACE), body, anchor)
            registerAnchors(all.map { it to 0 }, blockId, 0)
        }

        private fun table(element: PublicationMarkupElement) {
            val authoredId = element.attribute("id")
            val captionNode = element.elements().firstOrNull { it.name.equals("caption", true) }
            val caption = captionNode?.let { collapse(it.textContent()) }
            if (!caption.isNullOrEmpty()) {
                val cap = TextAccumulator().also { it.append(caption) }
                emitText(captionNode, "p", cap)
            }
            val trNodes = descendants(element).filter { it.name.equals("tr", true) }
            checkpoint()
            val rawRows = trNodes.map { row ->
                row.elements().filter { it.name.equals("th", true) || it.name.equals("td", true) }.map { cell ->
                    val accumulator = TextAccumulator()
                    inlineParts(cell.children, emptyList(), accumulator, ArrayList(), false, true, {})
                    val text = accumulator.value()
                    val spans = accumulator.spans()
                    if (cell.attribute("rowspan")?.toIntOrNull()?.let { it > 1 } == true ||
                        cell.attribute("colspan")?.toIntOrNull()?.let { it > 1 } == true) {
                        issue("TABLE_SPAN_DEGRADED", PublicationCompatibilitySeverity.DEGRADED,
                            "Merged table cells were retained as ordinary cells.")
                    }
                    ReaderTableCell(text, spans, cell.name.equals("th", true))
                }
            }.filter(List<ReaderTableCell>::isNotEmpty)
            val columns = rawRows.maxOfOrNull(List<ReaderTableCell>::size) ?: 0
            if (columns == 0 || columns > MAX_TABLE_COLUMNS) {
                unsupported(element, "table-column-limit")
                issue("TABLE_UNSUPPORTED", PublicationCompatibilitySeverity.DEGRADED,
                    "A table exceeded the supported row or column shape and was shown as text.")
                return
            }
            if (rawRows.size > MAX_TABLE_ROWS) fail(PublicationFailureCode.BLOCK_LIMIT, "Table row count exceeds the content budget")
            val rows = rawRows.map { cells -> ReaderTableRow(cells + List(columns - cells.size) { ReaderTableCell("") }) }
            if (rawRows.any { it.size != columns }) {
                issue("TABLE_SHAPE_DEGRADED", PublicationCompatibilitySeverity.DEGRADED,
                    "Uneven table rows were padded to preserve a readable rectangular table.")
            }
            if (rows.isEmpty()) {
                unsupported(element, "empty-table")
                return
            }
            val id = stableBlockId(authoredId, blockOrdinal++)
            val pending = ArrayList<String>()
            while (pendingAnchors.isNotEmpty()) pending += pendingAnchors.removeFirst()
            authoredId?.let(pending::add)
            val anchor = pending.firstOrNull()?.takeIf { it.length <= MAX_READER_ANCHOR_LENGTH }
            blocks += ReaderBlock.Table(id, rows, anchor)
            registerAnchors(pending.map { it to 0 }, id, 0)
        }

        private fun unsupported(element: PublicationMarkupElement, feature: String) {
            val authoredId = element.attribute("id")
            val id = stableBlockId(authoredId, blockOrdinal++)
            val tag = element.name.lowercase(Locale.ROOT)
            val visible = when (tag) {
                "script" -> "Embedded script was not executed."
                "svg" -> element.attribute("aria-label") ?: element.attribute("title")
                    ?: "An SVG image is not supported by this reader."
                "math" -> "Mathematical markup is not supported by this reader."
                "audio", "video" -> "Embedded media is not played by this reader."
                else -> "This publication contains unsupported $feature content."
            }.take(MAX_UNSUPPORTED_TEXT)
            val pending = ArrayList<String>()
            while (pendingAnchors.isNotEmpty()) pending += pendingAnchors.removeFirst()
            authoredId?.let(pending::add)
            val anchor = pending.firstOrNull()?.takeIf { it.length <= MAX_READER_ANCHOR_LENGTH }
            val code = when (tag) {
                "svg" -> "unsupported-svg"
                "math" -> "unsupported-mathml"
                "script" -> "inert-script"
                "audio", "video" -> "unsupported-media"
                else -> "unsupported-markup"
            }
            blocks += ReaderBlock.Unsupported(id, code, visible, anchor)
            registerAnchors(pending.map { it to 0 }, id, visible.codePointCount(0, visible.length))
            issue(code.uppercase(Locale.ROOT).replace('-', '_'), PublicationCompatibilitySeverity.DEGRADED,
                visible, id)
        }


        private fun parseStylesheet(stylesheet: PublicationStylesheet) {
            accountCssWork(stylesheet.text.length.toLong())
            if (stylesheet.text.length > MAX_STYLESHEET_CHARS) {
                issue("STYLESHEET_LIMIT", PublicationCompatibilitySeverity.DEGRADED,
                    "A stylesheet exceeded the supported byte budget and was ignored.")
                return
            }
            val css = CSS_COMMENT.replace(stylesheet.text, "")
            var cursor = 0
            while (cursor < css.length) {
                checkpoint()
                while (cursor < css.length && css[cursor].isWhitespace()) cursor++
                if (cursor >= css.length) break
                val open = css.indexOf('{', cursor)
                if (open < 0) {
                    if (css.substring(cursor).isNotBlank()) unsupportedCss("malformed stylesheet rule")
                    break
                }
                val close = css.indexOf('}', open + 1)
                if (close < 0) {
                    unsupportedCss("malformed stylesheet rule")
                    break
                }
                val selectorText = css.substring(cursor, open).trim()
                val declarations = css.substring(open + 1, close)
                if (selectorText.startsWith('@')) {
                    unsupportedCss("stylesheet at-rule")
                } else {
                    val selectors = selectorText.split(',').map(String::trim)
                    for (selector in selectors) {
                        accountCssWork(selector.length.toLong() + 1)
                        val parsed = CssSelector.parse(selector)
                        if (parsed == null) {
                            unsupportedCss("complex or unsupported selector")
                            continue
                        }
                        val kinds = LinkedHashSet<ReaderInlineKind>()
                        parseDeclarations(declarations, "stylesheet", kinds)
                        if (kinds.isNotEmpty()) styleRules += CssRule(parsed, kinds.toList())
                    }
                }
                cursor = close + 1
            }
        }

        private fun parseDeclarations(text: String, source: String, into: MutableSet<ReaderInlineKind>) {
            accountCssWork(text.length.toLong())
            for (declaration in text.split(';')) {
                checkpoint()
                val colon = declaration.indexOf(':')
                if (colon <= 0) {
                    if (declaration.isNotBlank()) unsupportedCss("malformed $source declaration")
                    continue
                }
                val property = declaration.substring(0, colon).trim().lowercase(Locale.ROOT)
                val value = declaration.substring(colon + 1).trim().lowercase(Locale.ROOT)
                when {
                    property == "font-weight" && value in setOf("bold", "bolder", "600", "700", "800", "900") -> into += ReaderInlineKind.Strong
                    property == "font-style" && value in setOf("italic", "oblique") -> into += ReaderInlineKind.Emphasis
                    property == "text-decoration" -> {
                        val lines = value.split(WHITESPACE)
                        if ("underline" in lines) into += ReaderInlineKind.Underline
                        if (lines.any { it == "line-through" || it == "strike" }) into += ReaderInlineKind.Strike
                    }
                    property in SAFE_IGNORED_CSS_PROPERTIES -> Unit
                    "url(" in value || property == "src" -> unsupportedCss("CSS resource reference")
                    property.isNotEmpty() -> unsupportedCss("CSS property $property")
                }
            }
        }
        private fun issue(code: String, severity: PublicationCompatibilitySeverity, message: String, blockId: String? = null) {
            val key = "$code|${section.contentId}|$blockId"
            findings.putIfAbsent(key, PublicationCompatibilityFinding(code, severity, message, section.contentId, blockId))
        }

        private fun accountCssWork(units: Long) {
            if (units > MAX_SECTION_CSS_WORK_UNITS - cssWorkUnits) {
                fail(PublicationFailureCode.BLOCK_LIMIT, "Section CSS parsing or selector work exceeded the content budget")
            }
            cssWorkUnits += units
            checkpoint()
        }

        private fun checkpoint() {
            if ((workCount++ and 0xff) == 0) cancelled()
        }
        private fun registerAnchors(values: List<Pair<String, Int>>, blockId: String, textLength: Int) {
            for ((anchor, rawOffset) in values) {
                if (anchor.isEmpty()) continue
                if (anchor.codePointCount(0, anchor.length) > MAX_READER_ANCHOR_LENGTH) {
                    issue("ANCHOR_ID_LIMIT", PublicationCompatibilitySeverity.DEGRADED,
                        "An authored anchor exceeded the Reader identity limit and cannot be targeted.")
                    continue
                }
                val offset = rawOffset.coerceIn(0, textLength)
                if (anchors.putIfAbsent(anchor, ReaderAnchorTarget(blockId, offset)) != null) {
                    fail(PublicationFailureCode.DUPLICATE_ANCHOR)
                }
            }
        }

        private fun stableBlockId(authoredId: String?, ordinal: Int): String {
            if (ordinal >= MAX_MARKUP_BLOCKS) fail(PublicationFailureCode.BLOCK_LIMIT)
            return if (!authoredId.isNullOrEmpty() && authoredId.codePointCount(0, authoredId.length) <= MAX_STABLE_ANCHOR_ID) {
                "a:$authoredId"
            } else "b:${ordinal.toString().padStart(6, '0')}"
        }

        private fun unsupportedCss(detail: String) {
            issue("UNSUPPORTED_CSS", PublicationCompatibilitySeverity.DEGRADED,
                "Unsupported stylesheet behavior was ignored ($detail); no stylesheet code or URL was executed.")
        }



        private fun isNote(element: PublicationMarkupElement): Boolean {
            val type = element.attribute("type", EPUB_NAMESPACE).orEmpty().split(WHITESPACE)
            val role = element.attribute("role").orEmpty().split(WHITESPACE)
            return type.any { it == "footnote" || it == "endnote" } || role.any { it == "doc-footnote" || it == "doc-endnote" }
        }

        private fun collectNoteIds(root: PublicationMarkupElement): Map<String, String> = buildMap {
            fun visit(element: PublicationMarkupElement) {
                if (element.name.equals("aside", true) && isNote(element)) {
                    val id = element.attribute("id")
                    if (!id.isNullOrBlank()) put(id, safeNoteId(id))
                }
                element.children.forEach { if (it is PublicationMarkupPart.Element) visit(it.value) }
            }
            visit(root)
        }

        private fun plainText(element: PublicationMarkupElement): TextResult {
            val accumulator = TextAccumulator()
            fun visit(node: PublicationMarkupElement) {
                val name = node.name.lowercase(Locale.ROOT)
                if (name in NON_RENDERED_TAGS || name in WHITESPACE_TAGS) return
                for (part in node.children) when (part) {
                    is PublicationMarkupPart.Text -> accumulator.append(part.value)
                    is PublicationMarkupPart.Element -> visit(part.value)
                }
            }
            visit(element)
            return TextResult(accumulator.value(), emptyList())
        }

        private fun parseDimension(value: String?, field: String): Int? {
            if (value == null) return null
            val parsed = value.trim().removeSuffix("px").toIntOrNull()
            if (parsed == null || parsed !in 1..100_000) {
                issue("INVALID_IMAGE_DIMENSION", PublicationCompatibilitySeverity.DEGRADED,
                    "An invalid image $field hint was ignored.")
                return null
            }
            return parsed
        }

        private fun descendants(root: PublicationMarkupElement): List<PublicationMarkupElement> = buildList {
            fun visit(element: PublicationMarkupElement) {
                for (child in element.children) if (child is PublicationMarkupPart.Element) {
                    add(child.value)
                    visit(child.value)
                }
            }
            visit(root)
        }

        private fun safeNoteId(value: String): String =
            if (NOTE_ID.matches(value)) value else "note-${shortHash(value)}"

        private fun safeTag(value: String) = value.filter { it.isLetterOrDigit() || it == '-' }.take(32)

        private fun fail(code: PublicationFailureCode, message: String = code.name): Nothing =
            throw PublicationParseException(code, message)

        private data class TextResult(val text: String, val spans: List<ReaderSpan>)
        private data class CssRule(val selector: CssSelector, val kinds: List<ReaderInlineKind>)

        private data class CssSelector(val tag: String?, val id: String?, val className: String?) {
            fun matches(element: PublicationMarkupElement): Boolean {
                if (tag != null && !tag.equals(element.name, true)) return false
                if (id != null && element.attribute("id") != id) return false
                if (className != null && className !in element.attribute("class").orEmpty().split(WHITESPACE)) return false
                return true
            }

            companion object {
                fun parse(raw: String): CssSelector? {
                    if (raw == "*") return CssSelector(null, null, null)
                    if (raw.isEmpty() || raw.any { it in " >+~[]:*" }) return null
                    val idAt = raw.indexOf('#')
                    val classAt = raw.indexOf('.')
                    if (idAt >= 0 && classAt >= 0) return null
                    val marker = maxOf(idAt, classAt)
                    val tag = (if (marker >= 0) raw.substring(0, marker) else raw).takeIf(String::isNotEmpty)
                    val suffix = if (marker >= 0) raw.substring(marker + 1) else null
                    if (tag != null && !CSS_NAME.matches(tag)) return null
                    if (suffix != null && !CSS_NAME.matches(suffix)) return null
                    if (marker >= 0 && suffix == null) return null
                    return CssSelector(tag, suffix.takeIf { idAt >= 0 }, suffix.takeIf { classAt >= 0 })
                }
            }
        }

        private inner class TextAccumulator {
            private val text = StringBuilder()
            private val spans = ArrayList<ReaderSpan>()
            private val activeStyles = ArrayList<ActiveStyle>()
            private var pendingSpace = false

            fun append(raw: String, preserve: Boolean = false) {
                var index = 0
                while (index < raw.length) {
                    if ((index and 0x3fff) == 0) checkpoint()
                    val codePoint = raw.codePointAt(index)
                    if (!preserve && (Character.isWhitespace(codePoint) || codePoint == 0x3000)) {
                        pendingSpace = text.isNotEmpty()
                    } else {
                        if (pendingSpace && text.isNotEmpty()) text.append(' ')
                        pendingSpace = false
                        text.appendCodePoint(codePoint)
                    }
                    index += Character.charCount(codePoint)
                }
            }

            fun lineBreak(preserve: Boolean) {
                if (preserve) {
                    pendingSpace = false
                    if (text.isNotEmpty() && text.last() != '\n') text.append('\n')
                } else pendingSpace = text.isNotEmpty()
            }

            fun withKinds(kinds: List<ReaderInlineKind>, action: () -> Unit) {
                val added = kinds.map { ActiveStyle(it, codePointLength()) }
                activeStyles.addAll(added)
                try {
                    action()
                } finally {
                    val end = codePointLength()
                    added.forEach { style ->
                        if (end > style.startCodePoint) spans += ReaderSpan(style.startCodePoint, end, style.kind)
                    }
                    repeat(added.size) { activeStyles.removeAt(activeStyles.lastIndex) }
                }
            }

            fun codePointLength(): Int = text.codePointCount(0, text.length)
            fun hasText(): Boolean = text.isNotEmpty()
            fun value(): String = text.toString().trimEnd()
            fun spans(): List<ReaderSpan> {
                val length = value().codePointCount(0, value().length)
                return (spans + activeStyles.mapNotNull { style ->
                    val end = codePointLength()
                    if (style.startCodePoint < end) ReaderSpan(style.startCodePoint, end, style.kind) else null
                }).mapNotNull { span ->
                    val end = minOf(span.endCodePoint, length)
                    if (span.startCodePoint < end) ReaderSpan(span.startCodePoint, end, span.kind) else null
                }
            }

            fun resetSegment() {
                text.setLength(0)
                spans.clear()
                pendingSpace = false
                activeStyles.forEach { it.startCodePoint = 0 }
            }

        }
        private data class ActiveStyle(val kind: ReaderInlineKind, var startCodePoint: Int)


        companion object {
            private const val XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml"
            private const val EPUB_NAMESPACE = "http://www.idpf.org/2007/ops"
            private const val SVG_NAMESPACE = "http://www.w3.org/2000/svg"
            private const val XLINK_NAMESPACE = "http://www.w3.org/1999/xlink"
            private val SVG_WRAPPER_TAGS = setOf("image", "title", "desc")
            private val SVG_UNSAFE_ATTRIBUTES = setOf("transform", "clip-path", "mask", "filter", "style", "class")
            private const val MAX_MARKUP_BLOCKS = 20_000
            private const val MAX_STYLESHEET_CHARS = 256 * 1024
            private const val MAX_SECTION_STYLESHEET_REFERENCES = 4096
            private const val MAX_SECTION_STYLESHEET_CHARACTERS = 1024L * 1024
            private const val MAX_SECTION_CSS_WORK_UNITS = 8L * 1024 * 1024
            private const val MAX_TABLE_COLUMNS = 16
            private const val MAX_TABLE_ROWS = 20_000
            private const val MAX_IMAGE_PIXELS = 50_000_000L
            private const val MAX_UNSUPPORTED_TEXT = 4096
            private const val MAX_RUBY_CODE_POINTS = 256
            private const val MAX_READER_ANCHOR_LENGTH = 256
            private const val MAX_STABLE_ANCHOR_ID = 512
            private val WHITESPACE = Regex("\\s+")
            private val NOTE_ID = Regex("^[A-Za-z][A-Za-z0-9_.:-]{0,255}$")
            private val CSS_NAME = Regex("^[A-Za-z_][A-Za-z0-9_-]{0,127}$")
            private val CSS_COMMENT = Regex("(?s)/\\*.*?\\*/")
            private val SAFE_IGNORED_CSS_PROPERTIES = setOf(
                "font-family", "font-size", "line-height", "text-align", "text-indent", "margin",
                "margin-top", "margin-bottom", "margin-left", "margin-right", "padding", "padding-top",
                "padding-bottom", "padding-left", "padding-right", "color", "background-color",
            )
            private val BLOCK_TEXT_TAGS = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "blockquote")
            private val BLOCK_BOUNDARY_TAGS = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "blockquote", "ul", "ol", "table", "aside", "hr", "html", "body", "div", "section", "article", "main", "header", "footer", "figure", "figcaption", "nav", "address", "dl", "dt", "dd", "center")
            private val GENERIC_BLOCK_TAGS = setOf(
                "html", "body", "div", "section", "article", "main", "header", "footer", "figure", "figcaption",
                "nav", "address", "dl", "dt", "dd", "center",
            )
            private val UNSUPPORTED_BLOCK_TAGS = setOf("script", "svg", "math", "iframe", "object", "embed", "canvas", "form", "audio", "video", "input", "button")
            private val UNSUPPORTED_INLINE_TAGS = setOf("iframe", "object", "embed", "canvas", "input", "button")
            private val NON_RENDERED_TAGS = setOf("head", "title", "meta", "link", "style", "script")
            private val WHITESPACE_TAGS = setOf("rt", "rp")
        }
    }
}

private fun collapse(value: String): String = value.trim().replace(Regex("[\\s　]+"), " ")
private fun shortHash(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
