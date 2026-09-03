/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory

/**
 * Durable admission point for source data that has already passed extension decoding and host DTO
 * validation. Raw HTML and transport responses never enter this store.
 */
internal class NormalizedSourceStore(context: Context) {
    private val files = QuotaFileStore(
        roots = StorageRoots.from(context),
        root = StorageRoot.NO_BACKUP,
        namespace = "normalized-source-content",
        quota = StorageQuota(maxBytes = 256L * 1024L * 1024L, maxEntries = 20_000),
    )

    fun writeDetail(detail: SourceBookDetail) {
        files.write(detailPath(detail.summary.identity), SourceValueCodec.encodeDetail(detail))
    }

    fun readDetail(identity: BookIdentity): SourceBookDetail? = runCatching {
        files.read(detailPath(identity))?.let(SourceValueCodec::decodeDetail)
    }.getOrNull()

    fun writeDirectory(directory: SourceDirectory) {
        files.write(directoryPath(directory.bookIdentity), SourceValueCodec.encodeDirectory(directory))
    }

    fun readDirectory(identity: BookIdentity): SourceDirectory? = runCatching {
        files.read(directoryPath(identity))?.let(SourceValueCodec::decodeDirectory)
    }.getOrNull()

    fun writeDocument(identity: BookIdentity, document: ReaderDocument) {
        require(document.sourceId == identity.sourceId && document.remoteBookId == identity.remoteBookId) {
            "Document identity does not match storage identity"
        }
        files.write(documentPath(identity, document.contentId), SourceValueCodec.encodeDocument(document))
    }

    fun readDocument(identity: BookIdentity, contentId: String): ReaderDocument? = runCatching {
        files.read(documentPath(identity, contentId))?.let(SourceValueCodec::decodeDocument)
    }.getOrNull()

    private fun detailPath(identity: BookIdentity): String = "detail/${key(identity)}.json"

    private fun directoryPath(identity: BookIdentity): String = "directory/${key(identity)}.json"

    private fun documentPath(identity: BookIdentity, contentId: String): String =
        "document/${key(identity, contentId)}.json"

    private fun key(identity: BookIdentity, suffix: String = ""): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${identity.sourceId}\u0000${identity.remoteBookId}\u0000$suffix".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

private object SourceValueCodec {
    private const val SCHEMA = 1
    private val json = Json { ignoreUnknownKeys = false }

    fun encodeDetail(value: SourceBookDetail): ByteArray = encode(
        buildJsonObject {
            put("schema", JsonPrimitive(SCHEMA))
            put("kind", JsonPrimitive("detail"))
            put("summary", encodeSummary(value.summary))
            putNullableString("description", value.description)
            put("tags", buildJsonArray { value.tags.forEach { add(JsonPrimitive(it)) } })
            putNullableString("status", value.status)
        },
    )

    fun decodeDetail(bytes: ByteArray): SourceBookDetail {
        val value = decode(bytes, "detail")
        return SourceBookDetail(
            summary = decodeSummary(value.requiredObject("summary")),
            description = value.nullableString("description"),
            tags = value.requiredArray("tags").map { it.jsonPrimitive.content },
            status = value.nullableString("status"),
        )
    }

    fun encodeDirectory(value: SourceDirectory): ByteArray = encode(
        buildJsonObject {
            put("schema", JsonPrimitive(SCHEMA))
            put("kind", JsonPrimitive("directory"))
            put("identity", encodeIdentity(value.bookIdentity))
            put("chapters", buildJsonArray {
                value.chapters.forEach { chapter ->
                    add(buildJsonObject {
                        put("chapterId", JsonPrimitive(chapter.chapterId))
                        put("title", JsonPrimitive(chapter.title))
                        put("url", JsonPrimitive(chapter.url))
                        putNullableString("volumeTitle", chapter.volumeTitle)
                    })
                }
            })
        },
    )

    fun decodeDirectory(bytes: ByteArray): SourceDirectory {
        val value = decode(bytes, "directory")
        return SourceDirectory(
            bookIdentity = decodeIdentity(value.requiredObject("identity")),
            chapters = value.requiredArray("chapters").map { element ->
                val chapter = element.jsonObject
                SourceChapter(
                    chapterId = chapter.requiredString("chapterId"),
                    title = chapter.requiredString("title"),
                    url = chapter.requiredString("url"),
                    volumeTitle = chapter.nullableString("volumeTitle"),
                )
            },
        )
    }

    fun encodeDocument(value: ReaderDocument): ByteArray = encode(
        buildJsonObject {
            put("schema", JsonPrimitive(SCHEMA))
            put("kind", JsonPrimitive("document"))
            put("sourceId", JsonPrimitive(value.sourceId))
            put("remoteBookId", JsonPrimitive(value.remoteBookId))
            put("contentId", JsonPrimitive(value.contentId))
            putNullableString("revision", value.revision)
            put("title", JsonPrimitive(value.title))
            put("blocks", buildJsonArray { value.blocks.forEach { add(encodeBlock(it)) } })
        },
    )

    fun decodeDocument(bytes: ByteArray): ReaderDocument {
        val value = decode(bytes, "document")
        return ReaderDocument(
            sourceId = value.requiredString("sourceId"),
            remoteBookId = value.requiredString("remoteBookId"),
            contentId = value.requiredString("contentId"),
            revision = value.nullableString("revision"),
            title = value.requiredString("title"),
            blocks = value.requiredArray("blocks").map(::decodeBlock),
        )
    }

    private fun encodeSummary(value: SourceBookSummary): JsonObject = buildJsonObject {
        put("identity", encodeIdentity(value.identity))
        put("title", JsonPrimitive(value.title))
        putNullableString("author", value.author)
        putNullableString("coverUrl", value.coverUrl)
        put("canonicalUrl", JsonPrimitive(value.canonicalUrl))
    }

    private fun decodeSummary(value: JsonObject): SourceBookSummary = SourceBookSummary(
        identity = decodeIdentity(value.requiredObject("identity")),
        title = value.requiredString("title"),
        author = value.nullableString("author"),
        coverUrl = value.nullableString("coverUrl"),
        canonicalUrl = value.requiredString("canonicalUrl"),
    )

    private fun encodeIdentity(value: BookIdentity): JsonObject = buildJsonObject {
        put("sourceId", JsonPrimitive(value.sourceId))
        put("remoteBookId", JsonPrimitive(value.remoteBookId))
    }

    private fun decodeIdentity(value: JsonObject): BookIdentity = BookIdentity(
        sourceId = value.requiredString("sourceId"),
        remoteBookId = value.requiredString("remoteBookId"),
    )

    private fun encodeBlock(value: ReaderBlock): JsonObject = when (value) {
        is ReaderBlock.Paragraph -> buildJsonObject {
            put("type", JsonPrimitive("paragraph"))
            put("blockId", JsonPrimitive(value.blockId))
            put("text", JsonPrimitive(value.text))
        }
        is ReaderBlock.Heading -> buildJsonObject {
            put("type", JsonPrimitive("heading"))
            put("blockId", JsonPrimitive(value.blockId))
            put("text", JsonPrimitive(value.text))
            put("level", JsonPrimitive(value.level))
        }
        is ReaderBlock.Image -> buildJsonObject {
            put("type", JsonPrimitive("image"))
            put("blockId", JsonPrimitive(value.blockId))
            put("url", JsonPrimitive(value.url))
            putNullableString("altText", value.altText)
            putNullableInt("width", value.width)
            putNullableInt("height", value.height)
        }
    }

    private fun decodeBlock(element: JsonElement): ReaderBlock {
        val value = element.jsonObject
        return when (value.requiredString("type")) {
            "paragraph" -> ReaderBlock.Paragraph(
                blockId = value.requiredString("blockId"),
                text = value.requiredString("text"),
            )
            "heading" -> ReaderBlock.Heading(
                blockId = value.requiredString("blockId"),
                text = value.requiredString("text"),
                level = value.requiredInt("level"),
            )
            "image" -> ReaderBlock.Image(
                blockId = value.requiredString("blockId"),
                url = value.requiredString("url"),
                altText = value.nullableString("altText"),
                width = value.nullableInt("width"),
                height = value.nullableInt("height"),
            )
            else -> error("Unsupported reader block type")
        }
    }

    private fun encode(value: JsonObject): ByteArray = json.encodeToString(JsonObject.serializer(), value)
        .toByteArray(Charsets.UTF_8)

    private fun decode(bytes: ByteArray, expectedKind: String): JsonObject {
        val value = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(value.requiredInt("schema") == SCHEMA) { "Unsupported normalized source schema" }
        require(value.requiredString("kind") == expectedKind) { "Normalized source kind mismatch" }
        return value
    }
}

private fun JsonObject.requiredObject(name: String): JsonObject = getValue(name).jsonObject
private fun JsonObject.requiredArray(name: String): JsonArray = getValue(name).jsonArray
private fun JsonObject.requiredString(name: String): String = getValue(name).jsonPrimitive.content
private fun JsonObject.requiredInt(name: String): Int =
    getValue(name).jsonPrimitive.intOrNull ?: error("Expected integer: $name")
private fun JsonObject.nullableString(name: String): String? =
    get(name)?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
private fun JsonObject.nullableInt(name: String): Int? =
    get(name)?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(name: String, value: String?) {
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
}
private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableInt(name: String, value: Int?) {
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
}
