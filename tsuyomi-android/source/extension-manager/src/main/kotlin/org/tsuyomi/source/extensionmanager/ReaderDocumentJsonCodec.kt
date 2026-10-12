/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.MAX_READER_BLOCK_DEPTH
import org.tsuyomi.shared.sourcecontract.MAX_READER_BODY_BLOCKS
import org.tsuyomi.shared.sourcecontract.MAX_READER_DOCUMENT_BLOCKS
import org.tsuyomi.shared.sourcecontract.MAX_READER_NESTED_BLOCKS
import org.tsuyomi.shared.sourcecontract.ReaderAnchorTarget
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.ReaderDocumentKind
import org.tsuyomi.shared.sourcecontract.ReaderInlineKind
import org.tsuyomi.shared.sourcecontract.ReaderLinkTarget
import org.tsuyomi.shared.sourcecontract.ReaderListItem
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import org.tsuyomi.shared.sourcecontract.ReaderSpan
import org.tsuyomi.shared.sourcecontract.ReaderTableCell
import org.tsuyomi.shared.sourcecontract.ReaderTableRow
import org.tsuyomi.shared.sourcecontract.nestedBlocks

/** Strict source-wire decoding and host-cache encoding for the unified Reader DTO. */
object ReaderDocumentJsonCodec {
    private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }

    /**
     * Accept immutable v1, rich v2, and the previously deployed remote-adapter shape.
     * Only the classified legacy operation owner may supply missing source-kind provenance;
     * an explicit protocol kind always wins, and old host caches remain unknown.
     */
    fun decodeRemote(
        value: JsonObject,
        expectedSourceId: String,
        expectedRemoteBookId: String,
        expectedContentId: String,
        legacyOperationKind: ReaderDocumentKind = ReaderDocumentKind.UNKNOWN,
    ): ReaderDocument {
        val version = value["version"]?.strictInt()
        val document = when {
            version == 2 -> decodeProtocolDocument(value, version2 = true)
            version == null && "identity" in value -> decodeProtocolDocument(value, version2 = false)
            version == null -> decodeLegacyRemote(value, legacyOperationKind)
            else -> error("Unsupported Reader document version")
        }
        val book = document.identity.book as? BookIdentity.Remote
            ?: error("Source contract cannot create a local Reader identity")
        require(book.sourceId == expectedSourceId && book.remoteBookId == expectedRemoteBookId) {
            "Reader document remote identity mismatch"
        }
        require(document.identity.contentId == expectedContentId) { "Reader document content identity mismatch" }
        require(document.blocks.flatMap(ReaderBlock::nestedBlocks).none { it is ReaderBlock.Unsupported }) {
            "Source contract cannot create host compatibility notices"
        }
        return document
    }

    /** Host-only versioned cache representation; never serialize this shape to a source. */
    fun encodeHost(document: ReaderDocument): JsonObject = buildJsonObject {
        put("format", JsonPrimitive("reader-document-host-v2"))
        put("identity", encodeHostIdentity(document.identity))
        put("title", JsonPrimitive(document.title))
        put("documentKind", JsonPrimitive(document.kind.name))
        put("anchors", encodeAnchors(document.anchors))
        put("blocks", buildJsonArray { document.blocks.forEach { add(encodeBlock(it, host = true)) } })
    }

    /** Reads the current host cache and the prior remote-only normalized-source cache. */
    fun decodeHost(text: String): ReaderDocument {
        requireReaderJsonNesting(text)
        val value = JSON.parseToJsonElement(text).jsonObject
        return when {
            value["format"]?.strictString() == "reader-document-host-v2" -> {
                value.requireFields(setOf("format", "identity", "title", "blocks", "anchors"),
                    setOf("format", "identity", "title", "blocks", "anchors", "documentKind"))
                ReaderDocument(
                    identity = decodeHostIdentity(value.requiredObject("identity")),
                    title = value.requiredString("title"),
                    blocks = decodeDocumentBlocks(value.requiredArray("blocks"), version2 = true, host = true),
                    anchors = decodeAnchors(value.requiredArray("anchors")),
                    kind = value["documentKind"]?.strictString()?.let(ReaderDocumentKind::valueOf) ?: ReaderDocumentKind.UNKNOWN,
                )
            }
            value["schema"]?.strictInt() == 1 && value["kind"]?.strictString() == "document" -> {
                value.requireFields(
                    required = setOf("schema", "kind", "sourceId", "remoteBookId", "contentId", "title", "blocks"),
                    allowed = setOf("schema", "kind", "sourceId", "remoteBookId", "contentId", "revision", "title", "blocks"),
                )
                val book = BookIdentity.Remote(value.requiredString("sourceId"), value.requiredString("remoteBookId"))
                val blocks = value.requiredArray("blocks")
                require(blocks.size in 1..MAX_READER_DOCUMENT_BLOCKS) { "Invalid Reader document block count" }
                ReaderDocument(
                    identity = DocumentIdentity(book, value.requiredString("contentId"), value.optionalString("revision")),
                    title = value.requiredString("title"),
                    blocks = blocks.map(::decodeLegacyCacheBlock),
                    kind = ReaderDocumentKind.UNKNOWN,
                )
            }
            else -> error("Unsupported host Reader document cache format")
        }
    }

    private fun decodeProtocolDocument(value: JsonObject, version2: Boolean): ReaderDocument {
        val required = setOf("kind", "identity", "title", "revision", "contentDigest", "blocks") +
            if (version2) setOf("anchors") else emptySet()
        val allowed = if (version2) required + "version" else required
        value.requireFields(required = required, allowed = allowed)
        if (version2) require(value.requiredInt("version") == 2)
        require(value.requiredString("kind") in setOf("chapter", "threadPage"))
        requireSha256(value.requiredString("contentDigest"))
        val identityValue = value.requiredObject("identity")
        identityValue.requireExactKeys("sourceId", "remoteBookId", "contentId")
        val book = BookIdentity.Remote(identityValue.requiredString("sourceId"), identityValue.requiredString("remoteBookId"))
        return ReaderDocument(
            identity = DocumentIdentity(book, identityValue.requiredString("contentId"), value.requiredString("revision")),
            title = value.requiredString("title"),
            blocks = decodeDocumentBlocks(value.requiredArray("blocks"), version2, host = false),
            anchors = if (version2) decodeAnchors(value.requiredArray("anchors")) else emptyMap(),
            kind = if (value.requiredString("kind") == "chapter") ReaderDocumentKind.CHAPTER else ReaderDocumentKind.THREAD_PAGE,
        )
    }

    private fun decodeLegacyRemote(value: JsonObject, operationKind: ReaderDocumentKind): ReaderDocument {
        value.requireFields(
            required = setOf("sourceId", "remoteBookId", "contentId", "title", "blocks"),
            allowed = setOf("sourceId", "remoteBookId", "contentId", "revision", "title", "blocks"),
        )
        val book = BookIdentity.Remote(value.requiredString("sourceId"), value.requiredString("remoteBookId"))
        return ReaderDocument(
            identity = DocumentIdentity(book, value.requiredString("contentId"), value.optionalString("revision")),
            title = value.requiredString("title"),
            blocks = decodeDocumentBlocks(value.requiredArray("blocks"), version2 = false, host = false, legacy = true),
            kind = operationKind,
        )
    }

    private fun decodeDocumentBlocks(
        value: JsonArray,
        version2: Boolean,
        host: Boolean,
        legacy: Boolean = false,
    ): List<ReaderBlock> = decodeBlocks(value, version2, host, legacy, 1, ReaderBlockDecodeBudget())

    private fun decodeBlocks(
        value: JsonArray,
        version2: Boolean,
        host: Boolean,
        legacy: Boolean,
        depth: Int,
        budget: ReaderBlockDecodeBudget,
    ): List<ReaderBlock> {
        budget.admit(depth, value.size)
        return value.map { decodeBlock(it.jsonObject, version2, host, legacy, depth, budget) }
    }

    private fun decodeBlock(
        value: JsonObject,
        version2: Boolean,
        host: Boolean,
        legacy: Boolean,
        depth: Int,
        budget: ReaderBlockDecodeBudget,
    ): ReaderBlock {
        val idField = if (legacy) "blockId" else "id"
        val blockId = value.requiredString(idField)
        val kind = value.requiredString("kind")
        val allowNull = host || legacy
        return when (kind) {
            "paragraph" -> {
                val base = setOf(idField, "kind", "text")
                value.requireFields(base, if (version2) base + setOf("spans", "anchorId", "preformatted") else base)
                ReaderBlock.Paragraph(
                    blockId = blockId,
                    text = value.requiredBlockText(version2, legacy, 65_536),
                    spans = if (version2) value.optionalArray("spans", allowNull).orEmpty().map { decodeSpan(it.jsonObject, host) } else emptyList(),
                    anchorId = if (version2) value.optionalString("anchorId", allowNull) else null,
                    preformatted = version2 && value["preformatted"]?.strictBoolean() == true,
                )
            }
            "heading" -> {
                val base = setOf(idField, "kind", "level", "text")
                value.requireFields(base, if (version2) base + setOf("spans", "anchorId") else base)
                ReaderBlock.Heading(
                    blockId = blockId,
                    text = value.requiredBlockText(version2, legacy, 16_384),
                    level = value.requiredInt("level"),
                    spans = if (version2) value.optionalArray("spans", allowNull).orEmpty().map { decodeSpan(it.jsonObject, host) } else emptyList(),
                    anchorId = if (version2) value.optionalString("anchorId", allowNull) else null,
                )
            }
            "image" -> {
                if (host) return decodeHostImage(value)
                val base = setOf(idField, "kind", "url")
                val extras = when {
                    version2 -> setOf("altText", "width", "height", "anchorId")
                    legacy -> setOf("alt", "altText", "width", "height", "aspectRatio")
                    else -> setOf("alt", "aspectRatio")
                }
                value.requireFields(base, base + extras)
                ReaderBlock.Image(
                    blockId = blockId,
                    resource = ReaderResourceRef.RemoteHttps(value.requiredString("url")),
                    altText = value.optionalString("altText", allowNull) ?: value.optionalString("alt", allowNull),
                    width = value.optionalInt("width", allowNull),
                    height = value.optionalInt("height", allowNull),
                    anchorId = if (version2) value.optionalString("anchorId", allowNull) else null,
                    aspectRatio = value["aspectRatio"]?.strictDouble(),
                )
            }
            "divider" -> {
                val base = setOf(idField, "kind")
                value.requireFields(base, if (version2) base + "anchorId" else base)
                ReaderBlock.Divider(blockId, if (version2) value.optionalString("anchorId", allowNull) else null)
            }
            "quote" -> {
                val base = setOf(idField, "kind", "text")
                value.requireFields(base, if (version2) base + setOf("spans", "attribution", "anchorId") else base + "attribution")
                ReaderBlock.Quote(
                    blockId = blockId,
                    text = value.requiredBlockText(version2, legacy, 65_536),
                    spans = if (version2) value.optionalArray("spans", allowNull).orEmpty().map { decodeSpan(it.jsonObject, host) } else emptyList(),
                    attribution = value.optionalString("attribution", allowNull),
                    anchorId = if (version2) value.optionalString("anchorId", allowNull) else null,
                )
            }
            "list" -> {
                require(version2) { "List requires Reader document v2" }
                val required = setOf("id", "kind", "ordered", "items")
                value.requireFields(required, required + setOf("start", "anchorId"))
                ReaderBlock.Listing(
                    blockId = blockId,
                    ordered = value.requiredBoolean("ordered"),
                    start = value.optionalInt("start", allowNull) ?: 1,
                    items = value.requiredArray("items").map { element ->
                        val item = element.jsonObject
                        item.requireExactKeys("itemId", "blocks")
                        ReaderListItem(
                            itemId = item.requiredString("itemId"),
                            blocks = decodeBlocks(item.requiredArray("blocks"), true, host, false, depth + 1, budget),
                        )
                    },
                    anchorId = value.optionalString("anchorId", allowNull),
                )
            }
            "note" -> {
                require(version2) { "Note requires Reader document v2" }
                val required = setOf("id", "kind", "noteId", "blocks")
                value.requireFields(required, required + setOf("label", "anchorId"))
                ReaderBlock.Note(
                    blockId = blockId,
                    noteId = value.requiredString("noteId"),
                    label = value.optionalString("label", allowNull),
                    blocks = decodeBlocks(value.requiredArray("blocks"), true, host, false, depth + 1, budget),
                    anchorId = value.optionalString("anchorId", allowNull) ?: value.requiredString("noteId"),
                )
            }
            "table" -> {
                require(version2) { "Table requires Reader document v2" }
                value.requireFields(setOf("id", "kind", "rows"), setOf("id", "kind", "rows", "anchorId"))
                ReaderBlock.Table(
                    blockId = blockId,
                    rows = value.requiredArray("rows").map { rowElement ->
                        val row = rowElement.jsonObject
                        row.requireExactKeys("cells")
                        ReaderTableRow(row.requiredArray("cells").map { cellElement ->
                            val cell = cellElement.jsonObject
                            cell.requireFields(setOf("text"), setOf("text", "spans", "header"))
                            ReaderTableCell(
                                cell.requiredString("text"),
                                cell.optionalArray("spans", host).orEmpty().map { decodeSpan(it.jsonObject, host) },
                                cell["header"]?.strictBoolean() ?: false,
                            )
                        })
                    },
                    anchorId = value.optionalString("anchorId", allowNull),
                )
            }
            "post" -> {
                val required = setOf(idField, "kind", "postId", "blocks") +
                    if (version2) emptySet() else setOf("authorId", "authorName")
                val allowed = required + setOf("authorId", "authorName", "floor", "createdAt", "replyToPostId") +
                    if (version2 || host || legacy) setOf("anchorId") else emptySet()
                value.requireFields(required, allowed)
                val postId = value.requiredString("postId")
                ReaderBlock.Post(
                    blockId = blockId,
                    postId = postId,
                    authorId = if (!version2 && !host && !legacy) value.requiredString("authorId") else value.optionalString("authorId", allowNull),
                    authorName = if (!version2 && !host && !legacy) value.requiredString("authorName") else value.optionalString("authorName", allowNull),
                    floor = value.optionalInt("floor", allowNull),
                    createdAt = value.optionalString("createdAt", allowNull),
                    replyToPostId = value.optionalString("replyToPostId", allowNull),
                    blocks = decodeBlocks(value.requiredArray("blocks"), version2, host, legacy, depth + 1, budget),
                    anchorId = if (version2) value.optionalString("anchorId", allowNull) else null,
                )
            }
            "unsupported" -> {
                require(host) { "Source contract cannot create unsupported-content notices" }
                value.requireFields(setOf("id", "kind", "featureCode", "visibleText"), setOf("id", "kind", "featureCode", "visibleText", "anchorId"))
                ReaderBlock.Unsupported(blockId, value.requiredString("featureCode"), value.requiredString("visibleText"), value.optionalString("anchorId"))
            }
            else -> error("Unsupported Reader block kind")
        }
    }

    private fun decodeSpan(value: JsonObject, host: Boolean): ReaderSpan {
        val kind = value.requiredString("kind")
        val start = value.requiredInt("start")
        val end = value.requiredInt("end")
        val inline = when (kind) {
            "emphasis" -> { value.requireExactKeys("start", "end", "kind"); ReaderInlineKind.Emphasis }
            "strong" -> { value.requireExactKeys("start", "end", "kind"); ReaderInlineKind.Strong }
            "underline" -> { value.requireExactKeys("start", "end", "kind"); ReaderInlineKind.Underline }
            "strike" -> { value.requireExactKeys("start", "end", "kind"); ReaderInlineKind.Strike }
            "code" -> { value.requireExactKeys("start", "end", "kind"); ReaderInlineKind.Code }
            "ruby" -> { value.requireExactKeys("start", "end", "kind", "annotation"); ReaderInlineKind.Ruby(value.requiredString("annotation")) }
            "noteReference" -> { value.requireExactKeys("start", "end", "kind", "noteId"); ReaderInlineKind.NoteReference(value.requiredString("noteId")) }
            "link" -> {
                value.requireExactKeys("start", "end", "kind", "target")
                val target = value.requiredObject("target")
                val link = when (target.requiredString("type")) {
                    "internal" -> {
                        target.requireFields(setOf("type", "contentId"), setOf("type", "contentId", "fragment"))
                        ReaderLinkTarget.Internal(target.requiredString("contentId"), target.optionalString("fragment", host))
                    }
                    "externalHttps" -> {
                        target.requireExactKeys("type", "url")
                        ReaderLinkTarget.ExternalHttps(target.requiredString("url"))
                    }
                    else -> error("Unsupported Reader link target")
                }
                ReaderInlineKind.Link(link)
            }
            else -> error("Unsupported Reader inline span")
        }
        return ReaderSpan(start, end, inline)
    }

    private fun decodeHostIdentity(value: JsonObject): DocumentIdentity {
        value.requireExactKeys("book", "contentId", "revision")
        val bookValue = value.requiredObject("book")
        val book = when (bookValue.requiredString("kind")) {
            "remote" -> {
                bookValue.requireExactKeys("kind", "sourceId", "remoteBookId")
                BookIdentity.Remote(bookValue.requiredString("sourceId"), bookValue.requiredString("remoteBookId"))
            }
            "local" -> {
                bookValue.requireExactKeys("kind", "publicationId")
                BookIdentity.Local(bookValue.requiredString("publicationId"))
            }
            else -> error("Unsupported host book origin")
        }
        return DocumentIdentity(book, value.requiredString("contentId"), value.optionalString("revision"))
    }

    private fun encodeHostIdentity(identity: DocumentIdentity): JsonObject = buildJsonObject {
        put("book", when (val book = identity.book) {
            is BookIdentity.Remote -> buildJsonObject {
                put("kind", JsonPrimitive("remote")); put("sourceId", JsonPrimitive(book.sourceId)); put("remoteBookId", JsonPrimitive(book.remoteBookId))
            }
            is BookIdentity.Local -> buildJsonObject {
                put("kind", JsonPrimitive("local")); put("publicationId", JsonPrimitive(book.publicationId))
            }
        })
        put("contentId", JsonPrimitive(identity.contentId))
        put("revision", identity.revision?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun decodeHostImage(value: JsonObject): ReaderBlock.Image {
        value.requireFields(setOf("id", "kind", "resource"), setOf("id", "kind", "resource", "altText", "width", "height", "anchorId", "aspectRatio"))
        val resource = value.requiredObject("resource")
        val reference = when (resource.requiredString("kind")) {
            "remoteHttps" -> {
                resource.requireExactKeys("kind", "url")
                ReaderResourceRef.RemoteHttps(resource.requiredString("url"))
            }
            "local" -> {
                resource.requireExactKeys("kind", "publicationId", "resourceId", "mimeType", "sha256")
                ReaderResourceRef.Local(resource.requiredString("publicationId"), resource.requiredString("resourceId"), resource.requiredString("mimeType"), resource.requiredString("sha256"))
            }
            else -> error("Unsupported cached Reader resource")
        }
        return ReaderBlock.Image(
            value.requiredString("id"), reference, value.optionalString("altText"), value.optionalInt("width"),
            value.optionalInt("height"), value.optionalString("anchorId"), value.optionalDouble("aspectRatio"),
        )
    }

    private fun decodeLegacyCacheBlock(element: JsonElement): ReaderBlock {
        val value = element.jsonObject
        return when (value.requiredString("type")) {
            "paragraph" -> {
                value.requireExactKeys("type", "blockId", "text")
                ReaderBlock.Paragraph(value.requiredString("blockId"), value.requiredString("text"))
            }
            "heading" -> {
                value.requireExactKeys("type", "blockId", "text", "level")
                ReaderBlock.Heading(value.requiredString("blockId"), value.requiredString("text"), value.requiredInt("level"))
            }
            "image" -> {
                value.requireFields(setOf("type", "blockId", "url"), setOf("type", "blockId", "url", "altText", "width", "height"))
                ReaderBlock.Image(value.requiredString("blockId"), ReaderResourceRef.RemoteHttps(value.requiredString("url")), value.optionalString("altText"), value.optionalInt("width"), value.optionalInt("height"))
            }
            else -> error("Unsupported legacy cached Reader block")
        }
    }

    private fun encodeBlock(block: ReaderBlock, host: Boolean): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(block.blockId))
        put("kind", JsonPrimitive(block.wireKind()))
        when (block) {
            is ReaderBlock.Paragraph -> {
                put("text", JsonPrimitive(block.text)); put("spans", encodeSpans(block.spans)); putNullable("anchorId", block.anchorId)
                put("preformatted", JsonPrimitive(block.preformatted))
            }
            is ReaderBlock.Heading -> {
                put("text", JsonPrimitive(block.text)); put("level", JsonPrimitive(block.level)); put("spans", encodeSpans(block.spans)); putNullable("anchorId", block.anchorId)
            }
            is ReaderBlock.Image -> {
                if (host) {
                    put("resource", encodeResource(block.resource))
                    putNullable("altText", block.altText); putNullable("width", block.width); putNullable("height", block.height); putNullable("anchorId", block.anchorId)
                    block.aspectRatio?.let { put("aspectRatio", JsonPrimitive(it)) }
                } else {
                    val resource = block.resource as? ReaderResourceRef.RemoteHttps ?: error("Local resources cannot be encoded as source data")
                    put("url", JsonPrimitive(resource.url)); putNullable("altText", block.altText)
                    putNullable("width", block.width); putNullable("height", block.height); putNullable("anchorId", block.anchorId)
                }
            }
            is ReaderBlock.Divider -> putNullable("anchorId", block.anchorId)
            is ReaderBlock.Quote -> {
                put("text", JsonPrimitive(block.text)); put("spans", encodeSpans(block.spans)); putNullable("attribution", block.attribution); putNullable("anchorId", block.anchorId)
            }
            is ReaderBlock.Listing -> {
                put("ordered", JsonPrimitive(block.ordered)); put("start", JsonPrimitive(block.start)); putNullable("anchorId", block.anchorId)
                put("items", buildJsonArray { block.items.forEach { item -> add(buildJsonObject {
                    put("itemId", JsonPrimitive(item.itemId)); put("blocks", buildJsonArray { item.blocks.forEach { add(encodeBlock(it, host)) } })
                }) } })
            }
            is ReaderBlock.Note -> {
                put("noteId", JsonPrimitive(block.noteId)); putNullable("label", block.label); putNullable("anchorId", block.anchorId)
                put("blocks", buildJsonArray { block.blocks.forEach { add(encodeBlock(it, host)) } })
            }
            is ReaderBlock.Table -> {
                putNullable("anchorId", block.anchorId)
                put("rows", buildJsonArray { block.rows.forEach { row -> add(buildJsonObject {
                    put("cells", buildJsonArray { row.cells.forEach { cell -> add(buildJsonObject {
                        put("text", JsonPrimitive(cell.text)); put("spans", encodeSpans(cell.spans)); put("header", JsonPrimitive(cell.header))
                    }) } })
                }) } })
            }
            is ReaderBlock.Post -> {
                put("postId", JsonPrimitive(block.postId)); putNullable("authorId", block.authorId); putNullable("authorName", block.authorName)
                putNullable("floor", block.floor); putNullable("createdAt", block.createdAt); putNullable("replyToPostId", block.replyToPostId)
                putNullable("anchorId", block.anchorId); put("blocks", buildJsonArray { block.blocks.forEach { add(encodeBlock(it, host)) } })
            }
            is ReaderBlock.Unsupported -> {
                require(host) { "Unsupported-content notices are host-only" }
                put("featureCode", JsonPrimitive(block.featureCode)); put("visibleText", JsonPrimitive(block.visibleText)); putNullable("anchorId", block.anchorId)
            }
        }
    }

    private fun decodeAnchors(value: JsonArray): Map<String, ReaderAnchorTarget> {
        val anchors = LinkedHashMap<String, ReaderAnchorTarget>(value.size)
        value.forEach { element ->
            val entry = element.jsonObject
            entry.requireExactKeys("id", "blockId", "codePointOffset")
            val id = entry.requiredString("id")
            val target = ReaderAnchorTarget(entry.requiredString("blockId"), entry.requiredInt("codePointOffset"))
            require(anchors.putIfAbsent(id, target) == null) { "Duplicate Reader anchor identity" }
        }
        return anchors
    }

    private fun encodeAnchors(anchors: Map<String, ReaderAnchorTarget>): JsonArray = buildJsonArray {
        anchors.toSortedMap().forEach { (id, target) -> add(buildJsonObject {
            put("id", JsonPrimitive(id))
            put("blockId", JsonPrimitive(target.blockId))
            put("codePointOffset", JsonPrimitive(target.codePointOffset))
        }) }
    }

    private fun encodeResource(resource: ReaderResourceRef): JsonObject = buildJsonObject {
        when (resource) {
            is ReaderResourceRef.RemoteHttps -> { put("kind", JsonPrimitive("remoteHttps")); put("url", JsonPrimitive(resource.url)) }
            is ReaderResourceRef.Local -> {
                put("kind", JsonPrimitive("local")); put("publicationId", JsonPrimitive(resource.publicationId))
                put("resourceId", JsonPrimitive(resource.resourceId)); put("mimeType", JsonPrimitive(resource.mimeType)); put("sha256", JsonPrimitive(resource.sha256))
            }
        }
    }

    private fun ReaderBlock.wireKind(): String = when (this) {
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
    }

    private fun encodeSpans(spans: List<ReaderSpan>): JsonArray = buildJsonArray {
        spans.forEach { span -> add(buildJsonObject {
            put("start", JsonPrimitive(span.startCodePoint)); put("end", JsonPrimitive(span.endCodePoint))
            when (val kind = span.kind) {
                ReaderInlineKind.Emphasis -> put("kind", JsonPrimitive("emphasis"))
                ReaderInlineKind.Strong -> put("kind", JsonPrimitive("strong"))
                ReaderInlineKind.Underline -> put("kind", JsonPrimitive("underline"))
                ReaderInlineKind.Strike -> put("kind", JsonPrimitive("strike"))
                ReaderInlineKind.Code -> put("kind", JsonPrimitive("code"))
                is ReaderInlineKind.Ruby -> { put("kind", JsonPrimitive("ruby")); put("annotation", JsonPrimitive(kind.annotation)) }
                is ReaderInlineKind.NoteReference -> { put("kind", JsonPrimitive("noteReference")); put("noteId", JsonPrimitive(kind.noteId)) }
                is ReaderInlineKind.Link -> {
                    put("kind", JsonPrimitive("link")); put("target", when (val target = kind.target) {
                        is ReaderLinkTarget.Internal -> buildJsonObject {
                            put("type", JsonPrimitive("internal")); put("contentId", JsonPrimitive(target.contentId)); putNullable("fragment", target.fragment)
                        }
                        is ReaderLinkTarget.ExternalHttps -> buildJsonObject { put("type", JsonPrimitive("externalHttps")); put("url", JsonPrimitive(target.url)) }
                    })
                }
            }
        }) }
    }

    private fun requireSha256(value: String) = require(value.matches(Regex("^[a-f0-9]{64}$"))) { "Invalid Reader content digest" }
}

// A depth-96 list chain ending in a table-cell link requires 4 * 96 + 6 JSON containers.
private const val MAX_READER_JSON_CONTAINER_DEPTH = MAX_READER_BLOCK_DEPTH * 4 + 6

/** Bounds parser recursion without allocating or interpreting strings as container syntax. */
internal fun requireReaderJsonNesting(text: String) {
    var depth = 0
    var inString = false
    var escaped = false
    for (character in text) {
        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = false
            }
        } else {
            when (character) {
                '"' -> inString = true
                '{', '[' -> {
                    depth++
                    require(depth <= MAX_READER_JSON_CONTAINER_DEPTH) { "Reader JSON exceeds nesting limit" }
                }
                '}', ']' -> {
                    depth--
                    require(depth >= 0) { "Unbalanced Reader JSON" }
                }
            }
        }
    }
    require(depth == 0 && !inString) { "Unclosed Reader JSON" }
}

private class ReaderBlockDecodeBudget {
    private var remainingBlocks = MAX_READER_NESTED_BLOCKS

    fun admit(depth: Int, count: Int) {
        require(depth in 1..MAX_READER_BLOCK_DEPTH) { "Reader document exceeds block depth limit" }
        val limit = if (depth == 1) MAX_READER_DOCUMENT_BLOCKS else MAX_READER_BODY_BLOCKS
        require(count in 1..limit) { "Invalid Reader block count" }
        require(count <= remainingBlocks) { "Reader document exceeds nested block limit" }
        remainingBlocks -= count
    }
}

private fun JsonObject.requiredBlockText(version2: Boolean, legacy: Boolean, maximumV1: Int): String {
    val text = requiredString("text")
    if (!version2 && !legacy) {
        require(text.codePointCount(0, text.length) <= maximumV1) { "Reader text exceeds v1 limit" }
    }
    return text
}

private fun JsonObject.requireExactKeys(vararg names: String) = requireFields(names.toSet())

private fun JsonObject.requireFields(required: Set<String>, allowed: Set<String> = required) {
    require(required.all(keys::contains) && keys.all(allowed::contains)) { "Unexpected Reader JSON fields" }
}

private fun JsonObject.requiredObject(name: String): JsonObject = getValue(name).jsonObject
private fun JsonObject.requiredArray(name: String): JsonArray = getValue(name).jsonArray
private fun JsonObject.optionalArray(name: String, allowNull: Boolean = true): JsonArray? = optionalValue(name, allowNull)?.jsonArray
private fun JsonObject.requiredString(name: String): String = getValue(name).strictString()
private fun JsonObject.optionalString(name: String, allowNull: Boolean = true): String? = optionalValue(name, allowNull)?.strictString()
private fun JsonObject.requiredInt(name: String): Int = getValue(name).strictInt()
private fun JsonObject.optionalInt(name: String, allowNull: Boolean = true): Int? = optionalValue(name, allowNull)?.strictInt()
private fun JsonObject.optionalDouble(name: String): Double? = get(name)?.takeUnless { it is JsonNull }?.strictDouble()
private fun JsonObject.requiredBoolean(name: String): Boolean = getValue(name).strictBoolean()
/** Wire options may be absent, whereas only host caches and prior adapters admit explicit nulls. */
private fun JsonObject.optionalValue(name: String, allowNull: Boolean): JsonElement? {
    val value = get(name) ?: return null
    require(allowNull || value !is JsonNull) { "Reader wire field $name must not be null" }
    return value.takeUnless { it is JsonNull }
}
private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: String?) =
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: Int?) =
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
private fun JsonElement.strictString(): String {
    val primitive = jsonPrimitive
    require(primitive.isString) { "Expected JSON string" }
    return primitive.content
}
private fun JsonElement.strictInt(): Int = jsonPrimitive.takeUnless { it.isString }?.intOrNull
    ?: error("Expected JSON integer")
private fun JsonElement.strictDouble(): Double = jsonPrimitive.takeUnless { it.isString }?.doubleOrNull
    ?.takeIf { it.isFinite() } ?: error("Expected JSON number")
private fun JsonElement.strictBoolean(): Boolean = jsonPrimitive.takeUnless { it.isString }?.booleanOrNull
    ?: error("Expected JSON boolean")
