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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.MAX_READER_BLOCK_DEPTH
import org.tsuyomi.shared.sourcecontract.MAX_READER_DOCUMENT_BLOCKS
import org.tsuyomi.shared.sourcecontract.MAX_READER_NESTED_BLOCKS
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.ReaderDocumentKind
import org.tsuyomi.shared.sourcecontract.ReaderInlineKind
import org.tsuyomi.shared.sourcecontract.ReaderListItem
import org.tsuyomi.shared.sourcecontract.ReaderSpan
import org.tsuyomi.shared.sourcecontract.ReaderTableCell
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import org.tsuyomi.shared.sourcecontract.nestedBlocks

class ReaderDocumentJsonCodecTest {
    @Test
    fun typedDocumentsBoundMixedNestingBeforeFlattening() {
        val accepted = typedDocument(listOf(typedChain(MAX_READER_BLOCK_DEPTH)))
        assertEquals(MAX_READER_BLOCK_DEPTH, accepted.blocks.single().nestedBlocks().size)
        assertThrows(IllegalArgumentException::class.java) {
            typedDocument(listOf(typedChain(MAX_READER_BLOCK_DEPTH + 1)))
        }
        val post = ReaderBlock.Post("post", "post-id", blocks = accepted.blocks)
        assertThrows(IllegalArgumentException::class.java) { typedDocument(listOf(post)) }
    }

    @Test
    fun remoteAndHostCacheEnforceTheSameBlockDepth() {
        val accepted = rawChain(MAX_READER_BLOCK_DEPTH, mixed = true)
        assertEquals(MAX_READER_BLOCK_DEPTH, decodeRemoteText(protocolText(accepted)).blocks.single().nestedBlocks().size)
        assertEquals(MAX_READER_BLOCK_DEPTH, ReaderDocumentJsonCodec.decodeHost(hostText(accepted)).blocks.single().nestedBlocks().size)
        val rejected = rawChain(MAX_READER_BLOCK_DEPTH + 1, mixed = true)
        assertThrows(IllegalArgumentException::class.java) { decodeRemoteText(protocolText(rejected)) }
        assertThrows(IllegalArgumentException::class.java) { ReaderDocumentJsonCodec.decodeHost(hostText(rejected)) }
    }

    @Test
    fun deepestValidListTableLinkShapeFitsTheSharedRawEnvelope() {
        val table = """{"id":"table","kind":"table","rows":[{"cells":[{"text":"A","spans":[{"start":0,"end":1,"kind":"link","target":{"type":"internal","contentId":"chapter"}}]}]}]}"""
        val blocks = rawChain(MAX_READER_BLOCK_DEPTH, leaf = table)
        val remote = decodeRemoteText(protocolText(blocks))
        val cached = ReaderDocumentJsonCodec.decodeHost(hostText(blocks))
        assertEquals(MAX_READER_BLOCK_DEPTH, remote.blocks.single().nestedBlocks().size)
        assertEquals(remote.blocks, cached.blocks)
    }

    @Test
    fun rawNestingRejectsArraysObjectsAndMixedContainersBeforeCacheParsing() {
        val maximum = MAX_READER_BLOCK_DEPTH * 4 + 6
        val quoted = JsonPrimitive("[]{}\\\"".repeat(500)).toString()
        val accepted = "[".repeat(maximum) + quoted + "]".repeat(maximum)
        requireReaderJsonNesting(accepted)
        Json.parseToJsonElement(accepted)
        for (shape in listOf("array", "object", "mixed")) {
            val raw = rawContainers(maximum + 1, shape)
            assertThrows(IllegalArgumentException::class.java) { requireReaderJsonNesting(raw) }
            val failure = assertThrows(IllegalArgumentException::class.java) {
                ReaderDocumentJsonCodec.decodeHost(raw)
            }
            assertEquals("Reader JSON exceeds nesting limit", failure.message)
        }
    }

    @Test
    fun aggregateReservationsAreSharedAcrossRootsBeforeDecodingTheRejectedBody() {
        val roots = aggregateBlocks(extra = false)
        val remote = decode(protocol(roots))
        assertEquals(MAX_READER_NESTED_BLOCKS, remote.blocks.sumOf { it.nestedBlocks().size })
        assertEquals(remote.blocks, ReaderDocumentJsonCodec.decodeHost(host(roots).toString()).blocks)
        val rejected = aggregateBlocks(extra = true)
        // The last body exceeds the remaining aggregate budget, not its local 5,000-block bound.
        // An invalid first element must not be interpreted before the whole body is reserved.
        val last = rejected.last()
        val body = last.getValue("blocks") as JsonArray
        val invalidLast = JsonObject(last + ("blocks" to JsonArray(listOf(JsonNull) + body.drop(1))))
        val invalidRoots = rejected.dropLast(1) + invalidLast
        val remoteFailure = assertThrows(IllegalArgumentException::class.java) { decode(protocol(invalidRoots)) }
        val cacheFailure = assertThrows(IllegalArgumentException::class.java) {
            ReaderDocumentJsonCodec.decodeHost(host(invalidRoots).toString())
        }
        assertEquals("Reader document exceeds nested block limit", remoteFailure.message)
        assertEquals(remoteFailure.message, cacheFailure.message)
    }

    @Test
    fun typedDocumentAggregateBoundaryIncludesContainersAndEveryRoot() {
        val roots = List(10) { note ->
            ReaderBlock.Note("note-$note", "n$note", null,
                List(4999) { index -> ReaderBlock.Paragraph("p-$note-$index", "A") })
        }
        assertEquals(10, typedDocument(roots).blocks.size)
        val last = roots.last()
        val rejected = roots.dropLast(1) + last.copy(blocks = last.blocks + ReaderBlock.Paragraph("extra", "A"))
        assertThrows(IllegalArgumentException::class.java) { typedDocument(rejected) }
    }

    @Test
    fun bothPublishedWireVersionsAcceptTheirTitleAndRootBlockCeilings() {
        val roots = List(MAX_READER_DOCUMENT_BLOCKS) { paragraph("p-$it") }
        val title = "😀".repeat(4096)
        for (version2 in listOf(false, true)) {
            val document = decode(protocol(roots, version2, title))
            assertEquals(title, document.title)
            assertEquals(50_000, document.blocks.size)
            assertThrows(IllegalArgumentException::class.java) { decode(protocol(roots, version2, title + "A")) }
            assertThrows(IllegalArgumentException::class.java) {
                decode(protocol(roots + paragraph("extra"), version2))
            }
        }
    }

    @Test
    fun proseAndTableTextRespectTheirDistinctPublishedLimits() {
        val prose = "😀".repeat(200_000)
        for (kind in listOf("paragraph", "heading", "quote")) {
            val block = textBlock(kind, prose)
            assertEquals(prose, when (val decoded = decode(protocol(listOf(block))).blocks.single()) {
                is ReaderBlock.Paragraph -> decoded.text
                is ReaderBlock.Heading -> decoded.text
                is ReaderBlock.Quote -> decoded.text
                else -> error("Unexpected text block")
            })
            assertThrows(IllegalArgumentException::class.java) {
                decode(protocol(listOf(textBlock(kind, prose + "A"))))
            }
        }
        ReaderTableCell("A".repeat(100_000))
        assertThrows(IllegalArgumentException::class.java) { ReaderTableCell("A".repeat(100_001)) }
        for ((kind, maximum) in listOf("paragraph" to 65_536, "heading" to 16_384, "quote" to 65_536)) {
            decode(protocol(listOf(textBlock(kind, "A".repeat(maximum))), version2 = false))
            assertThrows(IllegalArgumentException::class.java) {
                decode(protocol(listOf(textBlock(kind, "A".repeat(maximum + 1))), version2 = false))
            }
        }
    }

    @Test
    fun richOptionalTextAndInternalTargetsRetainPublishedBounds() {
        val target = JsonObject(mapOf("type" to JsonPrimitive("internal"), "contentId" to JsonPrimitive("c".repeat(1024))))
        val span = JsonObject(mapOf("start" to JsonPrimitive(0), "end" to JsonPrimitive(1),
            "kind" to JsonPrimitive("link"), "target" to target))
        val blocks = listOf(
            JsonObject(paragraph("p") + ("spans" to JsonArray(listOf(span)))),
            JsonObject(textBlock("quote", "A") + ("attribution" to JsonPrimitive(""))),
            JsonObject(mapOf("id" to JsonPrimitive("image"), "kind" to JsonPrimitive("image"),
                "url" to JsonPrimitive("https://example.com/image.png"), "altText" to JsonPrimitive(""))),
            JsonObject(mapOf("id" to JsonPrimitive("note"), "kind" to JsonPrimitive("note"),
                "noteId" to JsonPrimitive("n"), "label" to JsonPrimitive(""), "blocks" to JsonArray(listOf(paragraph("body"))))),
        )
        assertEquals(4, decode(protocol(blocks)).blocks.size)
        val tooLongTarget = JsonObject(target + ("contentId" to JsonPrimitive("c".repeat(1025))))
        val invalidSpan = JsonObject(span + ("target" to tooLongTarget))
        assertThrows(IllegalArgumentException::class.java) {
            decode(protocol(listOf(JsonObject(paragraph("p") + ("spans" to JsonArray(listOf(invalidSpan)))))))
        }
        val body = List(5000) { paragraph("body-$it") }
        val note = JsonObject(mapOf("id" to JsonPrimitive("note"), "kind" to JsonPrimitive("note"),
            "noteId" to JsonPrimitive("n"), "blocks" to JsonArray(body)))
        decode(protocol(listOf(note)))
        assertThrows(IllegalArgumentException::class.java) {
            decode(protocol(listOf(JsonObject(note + ("blocks" to JsonArray(body + paragraph("extra")))))))
        }
    }

    @Test
    fun largeNoteReferenceSetsRetainForwardLookupAndDuplicateRejection() {
        val reference = ReaderSpan(0, 1, ReaderInlineKind.NoteReference("n9999"))
        val notes = List(10_000) { index ->
            ReaderBlock.Note("note-$index", "n$index", null,
                listOf(ReaderBlock.Paragraph("p-$index", "A", listOf(reference))))
        }
        val document = typedDocument(notes)
        assertEquals(document, ReaderDocumentJsonCodec.decodeHost(ReaderDocumentJsonCodec.encodeHost(document).toString()))
        val wireReference = JsonObject(mapOf("start" to JsonPrimitive(0), "end" to JsonPrimitive(1),
            "kind" to JsonPrimitive("noteReference"), "noteId" to JsonPrimitive("n9999")))
        val wireNotes = notes.map { note -> JsonObject(mapOf(
            "id" to JsonPrimitive(note.blockId), "kind" to JsonPrimitive("note"), "noteId" to JsonPrimitive(note.noteId),
            "blocks" to JsonArray(listOf(JsonObject(paragraph(note.blocks.single().blockId) +
                ("spans" to JsonArray(listOf(wireReference)))))),
        )) }
        assertEquals(notes, decode(protocol(wireNotes)).blocks)
        val dangling = assertThrows(IllegalArgumentException::class.java) { typedDocument(listOf(notes.first())) }
        assertEquals("Reader note reference has no matching note body", dangling.message)
        val duplicate = assertThrows(IllegalArgumentException::class.java) {
            typedDocument(listOf(
                ReaderBlock.Note("first", "n", null, listOf(ReaderBlock.Paragraph("first-p", "A"))),
                ReaderBlock.Note("second", "n", null, listOf(ReaderBlock.Paragraph("second-p", "A"))),
            ))
        }
        assertEquals("Duplicate Reader note identity", duplicate.message)
    }

    @Test
    fun v2OptionalFieldsRejectWireNullWithoutChangingNullableHostCache() {
        val image = JsonObject(mapOf("id" to JsonPrimitive("image"), "kind" to JsonPrimitive("image"),
            "url" to JsonPrimitive("https://example.com/image.png")))
        val list = JsonObject(mapOf("id" to JsonPrimitive("list"), "kind" to JsonPrimitive("list"),
            "ordered" to JsonPrimitive(false), "items" to JsonArray(listOf(JsonObject(mapOf(
                "itemId" to JsonPrimitive("item"), "blocks" to JsonArray(listOf(paragraph("body"))),
            ))))))
        val note = JsonObject(mapOf("id" to JsonPrimitive("note"), "kind" to JsonPrimitive("note"),
            "noteId" to JsonPrimitive("n"), "blocks" to JsonArray(listOf(paragraph("body")))))
        val post = JsonObject(mapOf("id" to JsonPrimitive("post"), "kind" to JsonPrimitive("post"),
            "postId" to JsonPrimitive("post-id"), "blocks" to JsonArray(listOf(paragraph("body")))))
        val cases: List<Pair<JsonObject, Map<String, JsonElement>>> = listOf(
            paragraph("p") to mapOf("spans" to JsonArray(emptyList()), "anchorId" to JsonPrimitive("a")),
            textBlock("heading", "A") to mapOf("spans" to JsonArray(emptyList()), "anchorId" to JsonPrimitive("a")),
            image to mapOf("altText" to JsonPrimitive(""), "width" to JsonPrimitive(10),
                "height" to JsonPrimitive(10), "anchorId" to JsonPrimitive("a")),
            JsonObject(mapOf("id" to JsonPrimitive("divider"), "kind" to JsonPrimitive("divider"))) to
                mapOf("anchorId" to JsonPrimitive("a")),
            textBlock("quote", "A") to mapOf("spans" to JsonArray(emptyList()),
                "attribution" to JsonPrimitive(""), "anchorId" to JsonPrimitive("a")),
            list to mapOf("start" to JsonPrimitive(1), "anchorId" to JsonPrimitive("a")),
            note to mapOf("label" to JsonPrimitive(""), "anchorId" to JsonPrimitive("a")),
            table(JsonObject(mapOf("text" to JsonPrimitive("A")))) to mapOf("anchorId" to JsonPrimitive("a")),
            post to mapOf("authorId" to JsonPrimitive("author"), "authorName" to JsonPrimitive("Author"),
                "floor" to JsonPrimitive(1), "createdAt" to JsonPrimitive("2026-10-09T00:00:00Z"),
                "replyToPostId" to JsonPrimitive("other-post"), "anchorId" to JsonPrimitive("a")),
        )
        for ((block, options) in cases) {
            decode(protocol(listOf(block)))
            for ((name, value) in options) {
                val valid = decode(protocol(listOf(JsonObject(block + (name to value)))))
                assertThrows(IllegalArgumentException::class.java) {
                    decode(protocol(listOf(JsonObject(block + (name to JsonNull)))))
                }
                val cache = ReaderDocumentJsonCodec.encodeHost(valid)
                val nullableBlock = JsonObject(cache.getValue("blocks").jsonArray.single().jsonObject + (name to JsonNull))
                val nullableCache = JsonObject(cache + ("blocks" to JsonArray(listOf(nullableBlock))))
                val restored = ReaderDocumentJsonCodec.decodeHost(nullableCache.toString())
                assertEquals(restored, ReaderDocumentJsonCodec.decodeHost(ReaderDocumentJsonCodec.encodeHost(restored).toString()))
            }
        }
        decode(protocol(listOf(JsonObject(paragraph("p") + ("preformatted" to JsonPrimitive(true))))))
        assertThrows(IllegalStateException::class.java) {
            decode(protocol(listOf(JsonObject(paragraph("p") + ("preformatted" to JsonNull)))))
        }
    }

    @Test
    fun v1OptionalFieldsAndRequiredAuthorsFollowTheirImmutableSchema() {
        val image = JsonObject(mapOf("id" to JsonPrimitive("image"), "kind" to JsonPrimitive("image"),
            "url" to JsonPrimitive("https://example.com/image.png")))
        val post = JsonObject(mapOf("id" to JsonPrimitive("post"), "kind" to JsonPrimitive("post"),
            "postId" to JsonPrimitive("post-id"), "authorId" to JsonPrimitive("author"),
            "authorName" to JsonPrimitive("Author"), "blocks" to JsonArray(listOf(paragraph("body")))))
        val cases: List<Pair<JsonObject, Map<String, JsonElement>>> = listOf(
            image to mapOf("alt" to JsonPrimitive("")),
            textBlock("quote", "A") to mapOf("attribution" to JsonPrimitive("")),
            post to mapOf("floor" to JsonPrimitive(1), "createdAt" to JsonPrimitive("2026-10-09T00:00:00Z"),
                "replyToPostId" to JsonPrimitive("other-post")),
        )
        for ((block, options) in cases) {
            decode(protocol(listOf(block), version2 = false))
            for ((name, value) in options) {
                decode(protocol(listOf(JsonObject(block + (name to value))), version2 = false))
                assertThrows(IllegalArgumentException::class.java) {
                    decode(protocol(listOf(JsonObject(block + (name to JsonNull))), version2 = false))
                }
            }
        }
        for (name in listOf("authorId", "authorName")) {
            for (invalid in listOf(JsonNull, JsonPrimitive(""), JsonPrimitive(1))) {
                assertThrows(IllegalArgumentException::class.java) {
                    decode(protocol(listOf(JsonObject(post + (name to invalid))), version2 = false))
                }
            }
            assertThrows(IllegalArgumentException::class.java) {
                decode(protocol(listOf(JsonObject(post - name)), version2 = false))
            }
        }
        val anchored = JsonObject(post + ("anchorId" to JsonPrimitive("a")))
        assertThrows(IllegalArgumentException::class.java) { decode(protocol(listOf(anchored), version2 = false)) }
        decode(protocol(listOf(anchored)))
        assertThrows(IllegalStateException::class.java) {
            decode(protocol(listOf(JsonObject(image + ("aspectRatio" to JsonNull))), version2 = false))
        }
    }

    @Test
    fun tableCellAndInternalLinkOptionsRejectWireNullButRetainNullableCache() {
        val cell = JsonObject(mapOf("text" to JsonPrimitive("A")))
        val emptySpans = JsonObject(cell + ("spans" to JsonArray(emptyList())))
        val validTable = decode(protocol(listOf(table(emptySpans))))
        assertThrows(IllegalArgumentException::class.java) {
            decode(protocol(listOf(table(JsonObject(cell + ("spans" to JsonNull))))))
        }
        val cache = ReaderDocumentJsonCodec.encodeHost(validTable)
        val cachedTable = cache.getValue("blocks").jsonArray.single().jsonObject
        val cachedRow = cachedTable.getValue("rows").jsonArray.single().jsonObject
        val nullableCell = JsonObject(emptySpans + ("spans" to JsonNull))
        val nullableRow = JsonObject(cachedRow + ("cells" to JsonArray(listOf(nullableCell))))
        val nullableTable = JsonObject(cachedTable + ("rows" to JsonArray(listOf(nullableRow))))
        assertEquals(validTable, ReaderDocumentJsonCodec.decodeHost(
            JsonObject(cache + ("blocks" to JsonArray(listOf(nullableTable)))).toString()))
        decode(protocol(listOf(table(JsonObject(cell + ("header" to JsonPrimitive(true)))))))
        assertThrows(IllegalStateException::class.java) {
            decode(protocol(listOf(table(JsonObject(cell + ("header" to JsonNull))))))
        }
        val target = JsonObject(mapOf("type" to JsonPrimitive("internal"), "contentId" to JsonPrimitive("other")))
        for (fragment in listOf<JsonElement?>(null, JsonPrimitive("fragment"), JsonNull)) {
            val linkTarget = if (fragment == null) target else JsonObject(target + ("fragment" to fragment))
            val span = JsonObject(mapOf("start" to JsonPrimitive(0), "end" to JsonPrimitive(1),
                "kind" to JsonPrimitive("link"), "target" to linkTarget))
            for (kind in listOf("paragraph", "heading", "quote", "table")) {
                val block = if (kind == "table") table(JsonObject(cell + ("spans" to JsonArray(listOf(span)))))
                    else JsonObject(textBlock(kind, "A") + ("spans" to JsonArray(listOf(span))))
                if (fragment is JsonNull) {
                    assertThrows(IllegalArgumentException::class.java) { decode(protocol(listOf(block))) }
                } else {
                    val document = decode(protocol(listOf(block)))
                    assertEquals(document, ReaderDocumentJsonCodec.decodeHost(ReaderDocumentJsonCodec.encodeHost(document).toString()))
                }
            }
        }
    }

    @Test
    fun priorAdapterAndCoreCacheKeepTheirNullableMetadataGrammar() {
        val legacy = """{"sourceId":"org.tsuyomi.fixture","remoteBookId":"book","contentId":"chapter","revision":null,"title":"Reader","blocks":[{"blockId":"image","kind":"image","url":"https://example.com/image.png","altText":null,"width":null,"height":null},{"blockId":"post","kind":"post","postId":"post-id","authorId":null,"authorName":null,"floor":null,"createdAt":null,"replyToPostId":null,"anchorId":null,"blocks":[{"blockId":"body","kind":"paragraph","text":"A"}]}]}"""
        val document = decode(Json.parseToJsonElement(legacy).jsonObject)
        assertEquals(2, document.blocks.size)
        assertEquals(null, (document.blocks[1] as ReaderBlock.Post).authorId)
        assertEquals(document, ReaderDocumentJsonCodec.decodeHost(ReaderDocumentJsonCodec.encodeHost(document).toString()))
        val coreCache = """{"schema":1,"kind":"document","sourceId":"org.tsuyomi.fixture","remoteBookId":"book","contentId":"chapter","revision":null,"title":"Reader","blocks":[{"type":"image","blockId":"image","url":"https://example.com/image.png","altText":null,"width":null,"height":null}]}"""
        assertEquals(null, (ReaderDocumentJsonCodec.decodeHost(coreCache).blocks.single() as ReaderBlock.Image).width)
    }

    @Test
    fun numericAndBooleanFieldsRejectQuotedPrimitiveCoercion() {
        val heading = textBlock("heading", "A")
        decode(protocol(listOf(heading)))
        assertThrows(IllegalStateException::class.java) {
            decode(JsonObject(protocol(listOf(heading)) + ("version" to JsonPrimitive("2"))))
        }
        for ((block, name, value) in listOf(
            Triple(heading, "level", "1"),
            Triple(paragraph("p"), "preformatted", "true"),
            Triple(JsonObject(mapOf("id" to JsonPrimitive("list"), "kind" to JsonPrimitive("list"),
                "ordered" to JsonPrimitive(false), "items" to JsonArray(listOf(JsonObject(mapOf(
                    "itemId" to JsonPrimitive("item"), "blocks" to JsonArray(listOf(paragraph("body"))))))))), "ordered", "false"),
            Triple(JsonObject(mapOf("id" to JsonPrimitive("image"), "kind" to JsonPrimitive("image"),
                "url" to JsonPrimitive("https://example.com/image.png"))), "width", "1"),
        )) {
            assertThrows(IllegalStateException::class.java) {
                decode(protocol(listOf(JsonObject(block + (name to JsonPrimitive(value))))))
            }
        }
        val span = JsonObject(mapOf("start" to JsonPrimitive("0"), "end" to JsonPrimitive(1), "kind" to JsonPrimitive("strong")))
        assertThrows(IllegalStateException::class.java) {
            decode(protocol(listOf(JsonObject(paragraph("p") + ("spans" to JsonArray(listOf(span)))))))
        }
        val cell = JsonObject(mapOf("text" to JsonPrimitive("A"), "header" to JsonPrimitive("true")))
        val table = JsonObject(mapOf("id" to JsonPrimitive("table"), "kind" to JsonPrimitive("table"),
            "rows" to JsonArray(listOf(JsonObject(mapOf("cells" to JsonArray(listOf(cell))))))))
        assertThrows(IllegalStateException::class.java) { decode(protocol(listOf(table))) }
        val image = JsonObject(mapOf("id" to JsonPrimitive("image"), "kind" to JsonPrimitive("image"),
            "url" to JsonPrimitive("https://example.com/image.png"), "aspectRatio" to JsonPrimitive(10.0)))
        decode(protocol(listOf(image), version2 = false))
        assertThrows(IllegalStateException::class.java) {
            decode(protocol(listOf(JsonObject(image + ("aspectRatio" to JsonPrimitive("10.0")))), version2 = false))
        }
        assertThrows(IllegalArgumentException::class.java) {
            decode(protocol(listOf(JsonObject(image + ("aspectRatio" to JsonPrimitive(101.0)))), version2 = false))
        }
    }

    @Test
    fun v1ImageRatioRemainsNativeMetadataWithoutInventingPixelDimensions() {
        val base = JsonObject(mapOf("id" to JsonPrimitive("image"), "kind" to JsonPrimitive("image"),
            "url" to JsonPrimitive("https://example.com/image.png")))
        val missing = decode(protocol(listOf(base), version2 = false)).blocks.single() as ReaderBlock.Image
        assertEquals(null, missing.aspectRatio)
        for (ratio in listOf(1.0, 100.0, Double.MIN_VALUE)) {
            val value = JsonObject(base + ("aspectRatio" to JsonPrimitive(ratio)))
            val image = decode(protocol(listOf(value), version2 = false)).blocks.single() as ReaderBlock.Image
            assertEquals(ratio, image.aspectRatio!!, 0.0)
            assertEquals(null, image.width)
            assertEquals(null, image.height)
            val cached = ReaderDocumentJsonCodec.decodeHost(
                ReaderDocumentJsonCodec.encodeHost(typedDocument(listOf(image))).toString(),
            ).blocks.single() as ReaderBlock.Image
            assertEquals(image, cached)
        }
        for (ratio in listOf(0.0, -1.0, 100.0001)) {
            assertThrows(IllegalArgumentException::class.java) {
                decode(protocol(listOf(JsonObject(base + ("aspectRatio" to JsonPrimitive(ratio)))), version2 = false))
            }
        }
        val resource = ReaderResourceRef.RemoteHttps("https://example.com/image.png")
        for (ratio in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0, 101.0)) {
            assertThrows(IllegalArgumentException::class.java) { ReaderBlock.Image("image", resource, aspectRatio = ratio) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReaderBlock.Image("image", resource, width = 10_000, height = 10_000, aspectRatio = 1.0)
        }
    }

    @Test
    fun spansAcceptLaminarEqualityAndDisjointSiblingsWithoutChangingOrder() {
        val accepted = listOf(
            listOf(0 to 1, 0 to 1),
            listOf(0 to 5, 0 to 3),
            listOf(0 to 5, 1 to 5),
            listOf(0 to 6, 1 to 3, 3 to 5),
            listOf(0 to 1, 1 to 2),
        )
        for (ranges in accepted) {
            val spans = ranges.map { (start, end) -> ReaderSpan(start, end, ReaderInlineKind.Strong) }
            assertEquals(spans, ReaderBlock.Paragraph("p", "😀ABCDE", spans).spans)
            assertEquals(spans, ReaderBlock.Heading("h", "😀ABCDE", 1, spans).spans)
            assertEquals(spans, ReaderBlock.Quote("q", "😀ABCDE", spans).spans)
            assertEquals(spans, ReaderTableCell("😀ABCDE", spans).spans)
        }
        val equal = List(20_000) { ReaderSpan(0, 1, ReaderInlineKind.Strong) }
        assertEquals(20_000, ReaderBlock.Paragraph("p", "A", equal).spans.size)
        assertThrows(IllegalArgumentException::class.java) { ReaderTableCell("A", equal + equal.first()) }
    }

    @Test
    fun spansRejectCrossingReversedOuterOrderAndNoncanonicalOffsets() {
        for (ranges in listOf(listOf(0 to 3, 2 to 4), listOf(0 to 2, 0 to 3), listOf(1 to 2, 0 to 1))) {
            val spans = ranges.map { (start, end) -> ReaderSpan(start, end, ReaderInlineKind.Strong) }
            assertThrows(IllegalArgumentException::class.java) { ReaderBlock.Paragraph("p", "ABCD", spans) }
            assertThrows(IllegalArgumentException::class.java) { ReaderBlock.Heading("h", "ABCD", 1, spans) }
            assertThrows(IllegalArgumentException::class.java) { ReaderBlock.Quote("q", "ABCD", spans) }
            assertThrows(IllegalArgumentException::class.java) { ReaderTableCell("ABCD", spans) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReaderTableCell("😀", listOf(ReaderSpan(0, 2, ReaderInlineKind.Strong)))
        }
    }

    @Test
    fun hostRoundTripAndPriorCacheRetainIdentityAndKind() {
        val remote = decode(protocol(listOf(paragraph("p"))))
        val cached = ReaderDocumentJsonCodec.decodeHost(ReaderDocumentJsonCodec.encodeHost(remote).toString())
        assertEquals(remote, cached)
        val prior = """{"schema":1,"kind":"document","sourceId":"org.tsuyomi.fixture","remoteBookId":"book","contentId":"chapter","title":"Reader","blocks":[{"type":"paragraph","blockId":"p","text":"A"}]}"""
        assertEquals(ReaderDocumentKind.UNKNOWN, ReaderDocumentJsonCodec.decodeHost(prior).kind)
        assertThrows(IllegalStateException::class.java) {
            ReaderDocumentJsonCodec.decodeHost(prior.replace("\"schema\":1", "\"schema\":\"1\""))
        }
    }

    private fun decode(value: JsonObject): ReaderDocument = ReaderDocumentJsonCodec.decodeRemote(
        value, "org.tsuyomi.fixture", "book", "chapter",
    )

    private fun decodeRemoteText(text: String): ReaderDocument {
        requireReaderJsonNesting(text)
        return decode(Json.parseToJsonElement(text).jsonObject)
    }

    private fun protocol(blocks: List<JsonObject>, version2: Boolean = true, title: String = "Reader"): JsonObject = buildJsonObject {
        if (version2) put("version", 2)
        put("kind", "chapter")
        put("identity", buildJsonObject {
            put("sourceId", "org.tsuyomi.fixture"); put("remoteBookId", "book"); put("contentId", "chapter")
        })
        put("title", title); put("revision", "r1"); put("contentDigest", "a".repeat(64))
        put("blocks", JsonArray(blocks))
        if (version2) put("anchors", JsonArray(emptyList()))
    }

    private fun host(blocks: List<JsonObject>): JsonObject = buildJsonObject {
        put("format", "reader-document-host-v2")
        put("identity", buildJsonObject {
            put("book", buildJsonObject {
                put("kind", "remote"); put("sourceId", "org.tsuyomi.fixture"); put("remoteBookId", "book")
            })
            put("contentId", "chapter"); put("revision", "r1")
        })
        put("title", "Reader"); put("blocks", JsonArray(blocks)); put("anchors", JsonArray(emptyList()))
    }

    private fun paragraph(id: String): JsonObject = JsonObject(mapOf(
        "id" to JsonPrimitive(id), "kind" to JsonPrimitive("paragraph"), "text" to JsonPrimitive("A"),
    ))

    private fun textBlock(kind: String, text: String): JsonObject = buildJsonObject {
        put("id", kind); put("kind", kind); put("text", text)
        if (kind == "heading") put("level", 1)
    }

    private fun table(cell: JsonObject): JsonObject = JsonObject(mapOf(
        "id" to JsonPrimitive("table"), "kind" to JsonPrimitive("table"),
        "rows" to JsonArray(listOf(JsonObject(mapOf("cells" to JsonArray(listOf(cell)))))),
    ))

    private fun aggregateBlocks(extra: Boolean): List<JsonObject> = List(10) { note ->
        JsonObject(mapOf("id" to JsonPrimitive("note-$note"), "kind" to JsonPrimitive("note"),
            "noteId" to JsonPrimitive("n$note"), "blocks" to JsonArray(
                List(if (extra && note == 9) 5000 else 4999) { paragraph("p-$note-$it") },
            )))
    }

    private fun typedDocument(blocks: List<ReaderBlock>): ReaderDocument = ReaderDocument(
        DocumentIdentity(BookIdentity.Remote("org.tsuyomi.fixture", "book"), "chapter"), "Reader", blocks,
    )

    private fun typedChain(depth: Int): ReaderBlock {
        var block: ReaderBlock = ReaderBlock.Paragraph("leaf", "A")
        repeat(depth - 1) { index ->
            block = if (index % 2 == 0) ReaderBlock.Listing("list-$index", false,
                items = listOf(ReaderListItem("item-$index", listOf(block))))
            else ReaderBlock.Note("note-$index", "n$index", null, listOf(block))
        }
        return block
    }

    private fun rawChain(depth: Int, mixed: Boolean = false, leaf: String = """{"id":"leaf","kind":"paragraph","text":"A"}"""): String {
        val text = StringBuilder()
        repeat(depth - 1) { index ->
            if (mixed && index % 2 == 1) text.append("""{"id":"note-$index","kind":"note","noteId":"n$index","blocks":[""")
            else text.append("""{"id":"list-$index","kind":"list","ordered":false,"items":[{"itemId":"item-$index","blocks":[""")
        }
        text.append(leaf)
        for (index in depth - 2 downTo 0) text.append(if (mixed && index % 2 == 1) "]}" else "]}]}")
        return text.toString()
    }

    private fun protocolText(block: String): String = """{"version":2,"kind":"chapter","identity":{"sourceId":"org.tsuyomi.fixture","remoteBookId":"book","contentId":"chapter"},"title":"Reader","revision":"r1","contentDigest":"${"a".repeat(64)}","blocks":[$block],"anchors":[]}"""

    private fun hostText(block: String): String = """{"format":"reader-document-host-v2","identity":{"book":{"kind":"remote","sourceId":"org.tsuyomi.fixture","remoteBookId":"book"},"contentId":"chapter","revision":"r1"},"title":"Reader","blocks":[$block],"anchors":[]}"""

    private fun rawContainers(depth: Int, shape: String): String {
        val text = StringBuilder()
        repeat(depth) { index -> text.append(if (shape == "object" || shape == "mixed" && index % 2 == 1) "{\"x\":" else "[") }
        text.append('0')
        for (index in depth - 1 downTo 0) text.append(if (shape == "object" || shape == "mixed" && index % 2 == 1) '}' else ']')
        return text.toString()
    }
}
