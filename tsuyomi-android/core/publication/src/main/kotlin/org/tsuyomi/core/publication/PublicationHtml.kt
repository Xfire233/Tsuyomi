/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.util.ArrayDeque
import java.util.Locale

internal data class InertHtmlDocument(
    val body: PublicationMarkupElement,
    val stylesheets: List<PublicationStylesheet>,
    val findings: List<PublicationCompatibilityFinding>,
)

/** A small, bounded HTML tokenizer. It never resolves entities as external data or executes markup. */
internal class PublicationHtmlTokenizer(
    private val html: String,
    private val cancelled: () -> Unit,
) {
    private val root = MutableElement("#document")
    private val stack = ArrayDeque<MutableElement>().apply { addLast(root) }
    private val findings = LinkedHashMap<String, PublicationCompatibilityFinding>()
    private var cursor = 0
    private var textCodePoints = 0
    private var nodeCount = 0
    private var steps = 0

    fun parse(): InertHtmlDocument {
        if (html.length > MAX_HTML_CHARS) fail(PublicationFailureCode.HTML_TEXT_LIMIT, "HTML source exceeds its character budget.")
        while (cursor < html.length) {
            checkpoint()
            if (html[cursor] != '<') {
                appendTextUntilTag()
            } else when {
                html.startsWith("<!--", cursor) -> skipComment()
                html.startsWithIgnoreCase("<!doctype", cursor) -> skipDoctype()
                html.startsWith("<![CDATA[", cursor) -> appendCdata()
                html.startsWith("</", cursor) -> readEndTag()
                html.startsWith("<!", cursor) || html.startsWith("<?", cursor) -> skipDeclaration()
                else -> readStartTag()
            }
        }
        val htmlElement = root.children.filterIsInstance<MutablePart.Element>().firstOrNull { it.value.name == "html" }?.value ?: root
        val bodyElement = htmlElement.descendants().firstOrNull { it.name == "body" } ?: htmlElement
        val styles = ArrayList<PublicationStylesheet>()
        var styleOrdinal = 0
        for (element in root.descendants().filter { it.name == "style" }) {
            styles += PublicationStylesheet("html-style-${styleOrdinal++}", element.textContent())
        }
        for (element in root.descendants().filter { it.name == "link" }) {
            if (element.attributes["rel"].orEmpty().split(WHITESPACE).any { it.equals("stylesheet", true) }) {
                finding("EXTERNAL_STYLESHEET_DISABLED", PublicationCompatibilitySeverity.RESOURCE_DISABLED,
                    "An external stylesheet was not fetched; only embedded inert style rules are considered.")
            }
        }
        val bodyChildren = bodyElement.children.filterNot { it is MutablePart.Element && it.value.name == "head" }
        val body = PublicationMarkupElement(
            XHTML_NAMESPACE,
            "body",
            bodyElement.publicAttributes(),
            bodyChildren.map { it.freeze() },
        )
        return InertHtmlDocument(body, styles, findings.values.toList())
    }

    private fun appendTextUntilTag() {
        var end = cursor
        while (end < html.length && html[end] != '<') {
            if ((end and 0x3ff) == 0) checkpoint()
            end++
        }
        addText(html.substring(cursor, end))
        cursor = end
    }

    private fun readStartTag() {
        val start = cursor++
        if (cursor >= html.length || !isNameStart(html[cursor])) {
            addText("<")
            return
        }
        val name = readName().lowercase(Locale.ROOT)
        val attributes = LinkedHashMap<String, String>()
        var selfClosing = false
        var attributeCount = 0
        while (cursor < html.length) {
            checkpoint()
            skipWhitespace()
            if (cursor >= html.length) fail(PublicationFailureCode.MALFORMED_HTML, "Unterminated start tag.")
            when (html[cursor]) {
                '>' -> { cursor++; break }
                '/' -> {
                    cursor++
                    skipWhitespace()
                    if (cursor >= html.length || html[cursor] != '>') fail(PublicationFailureCode.MALFORMED_HTML, "Malformed self-closing tag.")
                    cursor++
                    selfClosing = true
                    break
                }
                else -> {
                    val attributeName = readName().lowercase(Locale.ROOT)
                    if (attributeName.isEmpty()) {
                        cursor++
                        continue
                    }
                    if (++attributeCount > MAX_ATTRIBUTES_PER_ELEMENT) {
                        fail(PublicationFailureCode.HTML_BLOCK_LIMIT, "HTML element has too many attributes.")
                    }
                    skipWhitespace()
                    val value = if (cursor < html.length && html[cursor] == '=') {
                        cursor++
                        skipWhitespace()
                        readAttributeValue()
                    } else ""
                    if (!attributeName.startsWith("on") && !attributeName.startsWith("data-") || attributeName == "id" || attributeName == "class" || attributeName == "style" || attributeName == "href" || attributeName == "src" || attributeName == "alt" || attributeName == "title" || attributeName == "type" || attributeName == "role" || attributeName == "start" || attributeName == "rowspan" || attributeName == "colspan" || attributeName == "width" || attributeName == "height" || attributeName == "epub:type" || attributeName == "xml:lang" || attributeName == "aria-label") {
                        if (attributes.putIfAbsent(attributeName, value) != null) {
                            finding("DUPLICATE_HTML_ATTRIBUTE", PublicationCompatibilitySeverity.DEGRADED,
                                "A duplicate HTML attribute was ignored.")
                        }
                    }
                }
            }
        }
        if (cursor <= start) fail(PublicationFailureCode.MALFORMED_HTML)
        autoCloseFor(name)
        val element = MutableElement(name, attributes)
        stack.last().children += MutablePart.Element(element)
        if (++nodeCount > MAX_HTML_NODES) fail(PublicationFailureCode.HTML_BLOCK_LIMIT, "HTML element count exceeds its budget.")
        if (name !in VOID_TAGS && !selfClosing) {
            if (stack.size >= MAX_HTML_DEPTH) fail(PublicationFailureCode.HTML_DEPTH_LIMIT)
            stack.addLast(element)
            if (name in RAW_TEXT_TAGS) consumeRawText(element, name)
        }
    }

    private fun readEndTag() {
        cursor += 2
        skipWhitespace()
        val name = readName().lowercase(Locale.ROOT)
        while (cursor < html.length && html[cursor] != '>') {
            checkpoint()
            cursor++
        }
        if (cursor >= html.length) fail(PublicationFailureCode.MALFORMED_HTML, "Unterminated end tag.")
        cursor++
        val match = stack.indexOfLast { it.name == name }
        if (match > 0) repeat(stack.size - match) { stack.removeLast() }
    }

    private fun skipComment() {
        var end = cursor + 4
        while (end + 2 < html.length) {
            if ((end and 0x3ff) == 0) checkpoint()
            if (html[end] == '-' && html[end + 1] == '-' && html[end + 2] == '>') {
                cursor = end + 3
                return
            }
            end++
        }
        cursor = html.length
    }

    private fun skipDoctype() {
        val end = scanTagEnd(cursor + 2)
        val declaration = html.substring(cursor, end + 1)
        if ('[' in declaration || declaration.contains("<!ENTITY", true)) {
            fail(PublicationFailureCode.XML_DTD_FORBIDDEN, "An internal DTD/entity declaration is forbidden.")
        }
        cursor = end + 1
    }

    private fun appendCdata() {
        val end = html.indexOf("]]>", cursor + 9)
        if (end < 0) fail(PublicationFailureCode.MALFORMED_HTML, "Unterminated CDATA section.")
        addText(html.substring(cursor + 9, end))
        cursor = end + 3
    }

    private fun skipDeclaration() {
        val end = scanTagEnd(cursor + 2)
        cursor = end + 1
    }

    private fun scanTagEnd(from: Int): Int {
        var quote: Char? = null
        var index = from
        while (index < html.length) {
            if ((index and 0x3ff) == 0) checkpoint()
            val char = html[index]
            if (quote != null) {
                if (char == quote) quote = null
            } else when (char) {
                '\'', '"' -> quote = char
                '>' -> return index
            }
            index++
        }
        fail(PublicationFailureCode.MALFORMED_HTML, "Unterminated HTML declaration.")
    }

    private fun readName(): String {
        val start = cursor
        while (cursor < html.length && isNamePart(html[cursor])) {
            if ((cursor and 0x3ff) == 0) checkpoint()
            cursor++
        }
        return html.substring(start, cursor)
    }

    private fun readAttributeValue(): String {
        if (cursor >= html.length) return ""
        val quote = html[cursor]
        if (quote == '\'' || quote == '"') {
            cursor++
            val start = cursor
            while (cursor < html.length && html[cursor] != quote) {
                if ((cursor and 0x3ff) == 0) checkpoint()
                cursor++
            }
            val value = html.substring(start, cursor++)
            if (value.length > MAX_ATTRIBUTE_CHARS) fail(PublicationFailureCode.HTML_TEXT_LIMIT, "HTML attribute exceeds its budget.")
            return decodeEntities(value)
        }
        val start = cursor
        while (cursor < html.length && !html[cursor].isWhitespace() && html[cursor] != '>' && !(html[cursor] == '/' && cursor + 1 < html.length && html[cursor + 1] == '>')) {
            if ((cursor and 0x3ff) == 0) checkpoint()
            cursor++
        }
        val value = html.substring(start, cursor)
        if (value.length > MAX_ATTRIBUTE_CHARS) fail(PublicationFailureCode.HTML_TEXT_LIMIT, "HTML attribute exceeds its budget.")
        return decodeEntities(value)
    }

    private fun consumeRawText(element: MutableElement, name: String) {
        val closeStart = findRawClose(name, cursor)
        val end = if (closeStart < 0) html.length else closeStart
        val content = html.substring(cursor, end)
        addText(content, decode = name !in setOf("script", "style"))
        if (closeStart < 0) {
            while (stack.last() !== element) stack.removeLast()
            stack.removeLast()
            cursor = html.length
        } else {
            cursor = closeStart
            readEndTag()
        }
    }

    private fun findRawClose(name: String, from: Int): Int {
        val target = "</$name"
        var index = from
        while (index + target.length <= html.length) {
            if ((index and 0x3ff) == 0) checkpoint()
            if (html.regionMatches(index, target, 0, target.length, ignoreCase = true)) {
                val next = index + target.length
                if (next >= html.length || html[next].isWhitespace() || html[next] == '>') return index
            }
            index++
        }
        return -1
    }

    private fun addText(value: String, decode: Boolean = true) {
        if (value.isEmpty()) return
        val text = if (decode) decodeEntities(value) else value
        textCodePoints += text.codePointCount(0, text.length)
        if (textCodePoints > MAX_HTML_TEXT_CODE_POINTS) fail(PublicationFailureCode.HTML_TEXT_LIMIT)
        val last = stack.last().children.lastOrNull()
        if (last is MutablePart.Text) last.value.append(text) else stack.last().children += MutablePart.Text(StringBuilder(text))
    }

    private fun decodeEntities(value: String): String {
        if ('&' !in value) return value
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if ((index and 0x3ff) == 0) checkpoint()
            if (value[index] != '&') {
                output.append(value[index++])
                continue
            }
            val semi = value.indexOf(';', index + 1)
            if (semi < 0 || semi - index > MAX_ENTITY_CHARS) {
                output.append('&')
                index++
                continue
            }
            val entity = value.substring(index + 1, semi)
            val decoded = decodeEntity(entity)
            if (decoded == null) {
                output.append(value, index, semi + 1)
            } else {
                output.appendCodePoint(decoded)
            }
            index = semi + 1
        }
        return output.toString()
    }

    private fun decodeEntity(entity: String): Int? {
        if (entity.startsWith("#x", true)) {
            return entity.substring(2).toIntOrNull(16)?.takeIf(::isScalar)
                ?: fail(PublicationFailureCode.MALFORMED_HTML, "Invalid numeric character reference.")
        }
        if (entity.startsWith('#')) {
            return entity.substring(1).toIntOrNull()?.takeIf(::isScalar)
                ?: fail(PublicationFailureCode.MALFORMED_HTML, "Invalid numeric character reference.")
        }
        return NAMED_ENTITIES[entity]
    }

    private fun autoCloseFor(name: String) {
        val close = when {
            name == "li" -> setOf("li")
            name == "dt" || name == "dd" -> setOf("dt", "dd")
            name == "tr" -> setOf("tr")
            name == "td" || name == "th" -> setOf("td", "th")
            name == "thead" || name == "tbody" || name == "tfoot" -> setOf("thead", "tbody", "tfoot")
            name == "option" -> setOf("option")
            name in BLOCK_OPENERS -> setOf("p")
            name == "p" -> setOf("p")
            else -> emptySet()
        }
        if (close.isNotEmpty() && stack.last().name in close) stack.removeLast()
    }

    private fun skipWhitespace() {
        while (cursor < html.length && html[cursor].isWhitespace()) cursor++
    }

    private fun checkpoint() {
        if ((steps++ and 0x3ff) == 0) cancelled()
    }

    private fun finding(code: String, severity: PublicationCompatibilitySeverity, message: String) {
        findings.putIfAbsent(code, PublicationCompatibilityFinding(code, severity, message))
    }

    private fun fail(code: PublicationFailureCode, message: String = code.name): Nothing =
        throw PublicationParseException(code, message)

    private fun isNameStart(char: Char) = char.isLetter() || char == '_' || char == ':'
    private fun isNamePart(char: Char) = char.isLetterOrDigit() || char == '_' || char == ':' || char == '-'
    private fun isScalar(codePoint: Int) = codePoint in 1..0x10ffff && codePoint !in 0xd800..0xdfff

    private fun String.startsWithIgnoreCase(prefix: String, start: Int) =
        regionMatches(start, prefix, 0, prefix.length, ignoreCase = true)

    private fun MutableElement.publicAttributes(): List<PublicationMarkupAttribute> = attributes.mapNotNull { (key, value) ->
        val colon = key.indexOf(':')
        val prefix = if (colon < 0) "" else key.substring(0, colon)
        val name = if (colon < 0) key else key.substring(colon + 1)
        val namespace = when (prefix) {
            "epub" -> EPUB_NAMESPACE
            "xml" -> XML_NAMESPACE
            "xlink" -> XLINK_NAMESPACE
            else -> ""
        }
        PublicationMarkupAttribute(namespace, name, value)
    }

    private fun MutableElement.freeze(): PublicationMarkupElement = PublicationMarkupElement(
        XHTML_NAMESPACE,
        name,
        publicAttributes(),
        children.map { it.freeze() },
    )

    private fun MutablePart.freeze(): PublicationMarkupPart = when (this) {
        is MutablePart.Text -> PublicationMarkupPart.Text(value.toString())
        is MutablePart.Element -> PublicationMarkupPart.Element(value.freeze())
    }

    private fun MutableElement.descendants(): Sequence<MutableElement> = sequence {
        for (child in children) if (child is MutablePart.Element) {
            yield(child.value)
            yieldAll(child.value.descendants())
        }
    }

    private fun MutableElement.textContent(): String = buildString {
        fun visit(element: MutableElement) {
            for (child in element.children) when (child) {
                is MutablePart.Text -> append(child.value)
                is MutablePart.Element -> visit(child.value)
            }
        }
        visit(this@textContent)
    }

    private class MutableElement(
        val name: String,
        val attributes: LinkedHashMap<String, String> = LinkedHashMap(),
        val children: MutableList<MutablePart> = ArrayList(),
    )

    private sealed interface MutablePart {
        data class Text(val value: StringBuilder) : MutablePart
        data class Element(val value: MutableElement) : MutablePart
    }

    companion object {
        private const val XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml"
        private const val EPUB_NAMESPACE = "http://www.idpf.org/2007/ops"
        private const val XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace"
        private const val XLINK_NAMESPACE = "http://www.w3.org/1999/xlink"
        private const val MAX_HTML_CHARS = 8 * 1024 * 1024
        private const val MAX_HTML_DEPTH = 96
        private const val MAX_HTML_NODES = 100_000
        private const val MAX_HTML_TEXT_CODE_POINTS = 8_000_000
        private const val MAX_ATTRIBUTES_PER_ELEMENT = 64
        private const val MAX_ATTRIBUTE_CHARS = 4096
        private const val MAX_ENTITY_CHARS = 32
        private val WHITESPACE = Regex("\\s+")
        private val RAW_TEXT_TAGS = setOf("script", "style", "textarea", "title")
        private val VOID_TAGS = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
        private val BLOCK_OPENERS = setOf("address", "article", "aside", "blockquote", "div", "dl", "fieldset", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "main", "nav", "ol", "p", "pre", "section", "table", "ul")
        private val NAMED_ENTITIES = mapOf(
            "amp" to 38, "lt" to 60, "gt" to 62, "quot" to 34, "apos" to 39,
            "nbsp" to 0x00a0, "ensp" to 0x2002, "emsp" to 0x2003, "thinsp" to 0x2009,
            "copy" to 0x00a9, "reg" to 0x00ae, "trade" to 0x2122, "hellip" to 0x2026,
            "mdash" to 0x2014, "ndash" to 0x2013, "lsquo" to 0x2018, "rsquo" to 0x2019,
            "ldquo" to 0x201c, "rdquo" to 0x201d, "bull" to 0x2022, "middot" to 0x00b7,
            "eacute" to 0x00e9, "Eacute" to 0x00c9, "agrave" to 0x00e0, "uuml" to 0x00fc,
        )
    }
}

