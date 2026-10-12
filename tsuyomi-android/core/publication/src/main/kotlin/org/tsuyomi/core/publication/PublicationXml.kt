/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import android.util.Xml
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.ArrayDeque
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

internal class PublicationXmlBudget {
    var textCharacters: Long = 0
        private set
    var nodes: Int = 0
        private set

    fun addText(length: Int) {
        textCharacters += length
        if (textCharacters > MAX_XML_TEXT_CHARACTERS) {
            throw PublicationParseException(PublicationFailureCode.XML_TEXT_LIMIT)
        }
    }

    fun addNode() {
        nodes++
        if (nodes > MAX_XML_NODES) throw PublicationParseException(PublicationFailureCode.XML_LIMIT)
    }

    companion object {
        private const val MAX_XML_TEXT_CHARACTERS = 2L * 1024 * 1024
        const val MAX_XML_NODES = 100_000
    }
}

internal fun parsePublicationXml(
    bytes: ByteArray,
    budget: PublicationXmlBudget,
    cancelled: () -> Unit = {},
): PublicationMarkupElement {
    if (bytes.size > MAX_XML_BYTES) throw PublicationParseException(PublicationFailureCode.XML_LIMIT)
    if (containsXmlDeclaration(bytes, "<!ENTITY", cancelled)) {
        throw PublicationParseException(PublicationFailureCode.XML_DTD_FORBIDDEN)
    }
    // Strip syntax-only doctypes before the pull parser sees them. No DTD is ever processed,
    // no external identifier is fetched, and internal subsets/entity declarations stay forbidden.
    val inertBytes = stripInertDoctype(bytes, cancelled)
    val parser = Xml.newPullParser()
    try {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        if (!parser.getFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES) ||
            parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)
        ) throw PublicationParseException(PublicationFailureCode.XML_FEATURE_UNAVAILABLE)
        parser.setInput(ByteArrayInputStream(inertBytes), null)
        val stack = ArrayDeque<MutableMarkupElement>()
        var root: MutableMarkupElement? = null
        var tokenCount = 0
        while (true) {
            if ((tokenCount++ and 0xff) == 0) cancelled()
            when (parser.nextToken()) {
                XmlPullParser.START_TAG -> {
                    if (parser.depth > MAX_XML_DEPTH) throw PublicationParseException(PublicationFailureCode.XML_DEPTH_LIMIT)
                    budget.addNode()
                    if (parser.attributeCount > MAX_XML_ATTRIBUTES) throw PublicationParseException(PublicationFailureCode.XML_LIMIT)
                    val attrs = ArrayList<PublicationMarkupAttribute>(parser.attributeCount)
                    for (index in 0 until parser.attributeCount) {
                        val name = parser.getAttributeName(index)
                        val value = parser.getAttributeValue(index)
                        if (value.length > MAX_XML_ATTRIBUTE_CHARS) throw PublicationParseException(PublicationFailureCode.XML_TEXT_LIMIT)
                        budget.addText(name.length + value.length)
                        attrs += PublicationMarkupAttribute(
                            parser.getAttributeNamespace(index).orEmpty(),
                            name,
                            value,
                        )
                    }
                    val node = MutableMarkupElement(parser.namespace.orEmpty(), parser.name, attrs, ArrayList())
                    if (root == null) root = node else {
                        if (stack.isEmpty()) throw PublicationParseException(PublicationFailureCode.MALFORMED_XML)
                        stack.getLast().children += MutableMarkupPart.Element(node)
                    }
                    stack.addLast(node)
                }
                XmlPullParser.END_TAG -> {
                    if (stack.isEmpty()) throw PublicationParseException(PublicationFailureCode.MALFORMED_XML)
                    stack.removeLast()
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.IGNORABLE_WHITESPACE -> {
                    val value = parser.text.orEmpty()
                    budget.addText(value.length)
                    if (stack.isNotEmpty() && value.isNotEmpty()) {
                        stack.getLast().children += MutableMarkupPart.Text(value)
                    }
                }
                XmlPullParser.ENTITY_REF -> {
                    val value = parser.text ?: throw PublicationParseException(PublicationFailureCode.XML_ENTITY_FORBIDDEN)
                    budget.addText(value.length)
                    if (stack.isNotEmpty()) stack.getLast().children += MutableMarkupPart.Text(value)
                }
                XmlPullParser.DOCDECL -> throw PublicationParseException(PublicationFailureCode.XML_DTD_FORBIDDEN)
                XmlPullParser.END_DOCUMENT -> break
                else -> Unit
            }
        }
        if (stack.isNotEmpty() || root == null) throw PublicationParseException(PublicationFailureCode.MALFORMED_XML)
        return root.freeze()
    } catch (error: PublicationParseException) {
        throw error
    } catch (error: XmlPullParserException) {
        throw PublicationParseException(PublicationFailureCode.MALFORMED_XML, cause = error)
    } catch (error: IOException) {
        throw PublicationParseException(PublicationFailureCode.MALFORMED_XML, cause = error)
    } catch (error: IllegalArgumentException) {
        throw PublicationParseException(PublicationFailureCode.XML_FEATURE_UNAVAILABLE, cause = error)
    }
}

private sealed interface MutableMarkupPart {
    data class Text(val value: String) : MutableMarkupPart
    data class Element(val value: MutableMarkupElement) : MutableMarkupPart
}

private class MutableMarkupElement(
    private val namespace: String,
    private val name: String,
    private val attributes: List<PublicationMarkupAttribute>,
    val children: MutableList<MutableMarkupPart>,
) {
    fun freeze(): PublicationMarkupElement = PublicationMarkupElement(
        namespace,
        name,
        attributes.toList(),
        children.map { part ->
            when (part) {
                is MutableMarkupPart.Text -> PublicationMarkupPart.Text(part.value)
                is MutableMarkupPart.Element -> PublicationMarkupPart.Element(part.value.freeze())
            }
        },
    )
}

private fun stripInertDoctype(bytes: ByteArray, cancelled: () -> Unit): ByteArray {
    val encoding = xmlCodeUnitWidth(bytes)
    val width = kotlin.math.abs(encoding)
    val littleEndian = encoding > 0
    val start = prologDoctypeOffset(bytes, width, littleEndian, cancelled)
    if (start < 0) return bytes
    val declaration = StringBuilder()
    var cursor = start + "<!DOCTYPE".length * width
    var quote = 0
    while (cursor + width <= bytes.size && declaration.length <= MAX_XML_ATTRIBUTE_CHARS) {
        cancelled()
        val valueIndex = cursor + if (littleEndian) 0 else width - 1
        val value = bytes[valueIndex].toInt() and 0xff
        if (value > 0x7f || (0 until width).any { it != valueIndex - cursor && bytes[cursor + it].toInt() != 0 }) {
            throw PublicationParseException(PublicationFailureCode.XML_DTD_FORBIDDEN)
        }
        cursor += width
        if (quote == 0 && value == '>'.code) {
            if (!INERT_DOCTYPE.matches(declaration.toString())) {
                throw PublicationParseException(PublicationFailureCode.XML_DTD_FORBIDDEN)
            }
            val stripped = ByteArray(bytes.size - (cursor - start))
            bytes.copyInto(stripped, 0, 0, start)
            bytes.copyInto(stripped, start, cursor)
            if (prologDoctypeOffset(stripped, width, littleEndian, cancelled) >= 0) {
                throw PublicationParseException(PublicationFailureCode.XML_DTD_FORBIDDEN)
            }
            return stripped
        }
        if (quote == 0 && (value == '\''.code || value == '"'.code)) quote = value
        else if (quote == value) quote = 0
        declaration.append(value.toChar())
    }
    throw PublicationParseException(PublicationFailureCode.XML_DTD_FORBIDDEN)
}

private fun prologDoctypeOffset(bytes: ByteArray, width: Int, littleEndian: Boolean, cancelled: () -> Unit): Int {
    var offset = when {
        width == 1 && bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte() -> 3
        width > 1 && bytes.size >= width && (bytes[0] == 0xff.toByte() || bytes[0] == 0xfe.toByte() ||
            width == 4 && bytes[2] == 0xfe.toByte()) -> width
        else -> 0
    }
    fun matches(marker: String): Boolean = marker.indices.all { index ->
        val position = offset + index * width + if (littleEndian) 0 else width - 1
        position < bytes.size && (bytes[position].toInt() and 0xff) == marker[index].code
    }
    while (offset + width <= bytes.size) {
        cancelled()
        val value = bytes[offset + if (littleEndian) 0 else width - 1].toInt() and 0xff
        if (value == 0x20 || value == 0x09 || value == 0x0a || value == 0x0d) {
            offset += width
            continue
        }
        val endMarker = when {
            matches("<?") -> "?>"
            matches("<!--") -> "-->"
            matches("<!DOCTYPE") -> return offset
            else -> return -1
        }
        val end = encodedMarkerOffset(bytes, endMarker, width, littleEndian, cancelled, offset)
        if (end < 0) return -1 // The strict XML parser owns malformed comments/instructions.
        offset = end + endMarker.length * width
    }
    return -1
}

private val INERT_DOCTYPE = Regex("""\s+(?:html|ncx)(?:\s+(?:SYSTEM\s+(?:"[^"<>\[\]]*"|'[^'<>\[\]]*')|PUBLIC\s+(?:"[^"<>\[\]]*"|'[^'<>\[\]]*')\s+(?:"[^"<>\[\]]*"|'[^'<>\[\]]*')))?\s*""")

private fun containsXmlDeclaration(bytes: ByteArray, marker: String, cancelled: () -> Unit): Boolean {
    val encoding = xmlCodeUnitWidth(bytes)
    return encodedMarkerOffset(bytes, marker, kotlin.math.abs(encoding), encoding > 0, cancelled) >= 0
}

/** XML's BOM/initial-markup encoding signature; negative widths mean big endian. */
private fun xmlCodeUnitWidth(bytes: ByteArray): Int {
    if (bytes.size < 4) return 1
    val first = bytes[0].toInt() and 0xff
    val second = bytes[1].toInt() and 0xff
    val third = bytes[2].toInt() and 0xff
    val fourth = bytes[3].toInt() and 0xff
    return when {
        first == 0 && second == 0 -> -4
        first == 0xfe && second == 0xff -> -2
        first == 0xff && second == 0xfe -> if (third == 0 && fourth == 0) 4 else 2
        first == 0 -> -2
        second == 0 && third == 0 && fourth == 0 -> 4
        second == 0 -> 2
        else -> 1
    }
}

private fun encodedMarkerOffset(bytes: ByteArray, marker: String, width: Int, littleEndian: Boolean, cancelled: () -> Unit, offset: Int = 0): Int {
    val required = marker.length.toLong() * width
    if (required > bytes.size) return -1
    val maxOffset = bytes.size - required.toInt()
    for (start in offset..maxOffset step width) {
        if ((start and 0xfff) == 0) cancelled()
        var matched = true
        for (characterIndex in marker.indices) {
            val base = start + characterIndex * width
            val character = marker[characterIndex].code
            val valueByte = if (width == 1 || littleEndian) base else base + width - 1
            if ((bytes[valueByte].toInt() and 0xff) != character) {
                matched = false
                break
            }
            val zeroStart = if (width == 1) 0 else if (littleEndian) 1 else 0
            val zeroEnd = if (littleEndian) width else width - 1
            for (paddingIndex in zeroStart until zeroEnd) {
                val byteIndex = base + paddingIndex
                if (byteIndex != valueByte && bytes[byteIndex].toInt() != 0) {
                    matched = false
                    break
                }
            }
            if (!matched) break
        }
        if (matched) return start
    }
    return -1
}

private const val MAX_XML_BYTES = 2 * 1024 * 1024
private const val MAX_XML_DEPTH = 96

private const val MAX_XML_ATTRIBUTES = 64
private const val MAX_XML_ATTRIBUTE_CHARS = 4096
