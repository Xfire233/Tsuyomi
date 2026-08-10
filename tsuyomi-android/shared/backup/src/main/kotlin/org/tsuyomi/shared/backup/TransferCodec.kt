/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.backup

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import java.time.Instant
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
import org.tsuyomi.shared.model.BookIdentity

object TransferCodec {
    private val json = Json { explicitNulls = false }

    fun parse(bytes: ByteArray): ImportParseResult {
        if (bytes.size > MAX_TRANSFER_BYTES) return ImportParseResult.Fatal("transfer-too-large")
        val text = runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrElse { return ImportParseResult.Fatal("invalid-utf8") }
        val root = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { return ImportParseResult.Fatal("invalid-json") }
        return when (root.string("format")) {
            "tsuyomi-transfer" -> parseTransfer(root)
            "hikari_novel_backup" -> HikariBackupCodec.parse(root)
            else -> ImportParseResult.Fatal("unsupported-format")
        }
    }

    fun encode(snapshot: TransferSnapshot): ByteArray {
        val orderedBooks = snapshot.library.sortedWith(compareBy({ it.identity.sourceId }, { it.identity.remoteBookId }))
        require(orderedBooks.map { it.identity }.distinct().size == orderedBooks.size) { "Duplicate book identity" }
        val orderedShelves = canonicalShelves(snapshot.shelves)
        val root = buildJsonObject {
            put("format", "tsuyomi-transfer")
            put("version", 1)
            put("createdAt", snapshot.createdAt.toString())
            put("library", buildJsonArray { orderedBooks.forEach { add(bookJson(it)) } })
            put("shelves", buildJsonArray { orderedShelves.forEach { add(shelfJson(it)) } })
            snapshot.readerPreferences?.let { preferences ->
                put("preferences", buildJsonObject {
                    put("reader", buildJsonObject {
                        preferences.flow?.let { put("flow", it) }
                        preferences.fontScale?.let { put("fontScale", it) }
                        preferences.lineHeight?.let { put("lineHeight", it) }
                        preferences.theme?.let { put("theme", it) }
                    })
                })
            }
        }
        return json.encodeToString(JsonElement.serializer(), root).toByteArray(StandardCharsets.UTF_8)
    }
    fun encodeBounded(snapshot: TransferSnapshot, maximumBytes: Int = MAX_TRANSFER_BYTES): ByteArray? {
        require(maximumBytes >= 0)
        val sentinelLimit = maximumBytes.toLong() + 1L
        val orderedBooks = snapshot.library.sortedWith(compareBy({ it.identity.sourceId }, { it.identity.remoteBookId }))
        require(orderedBooks.map { it.identity }.distinct().size == orderedBooks.size) { "Duplicate book identity" }
        val orderedShelves = canonicalShelves(snapshot.shelves)
        val output = ByteArrayOutputStream(minOf(maximumBytes, 64 * 1024))

        fun append(value: String): Boolean {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            val remaining = sentinelLimit - output.size().toLong()
            if (remaining > 0L) output.write(bytes, 0, minOf(bytes.size.toLong(), remaining).toInt())
            return output.size().toLong() <= maximumBytes.toLong()
        }
        fun encoded(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)

        if (!append("{\"format\":\"tsuyomi-transfer\",\"version\":1,\"createdAt\":")) return null
        if (!append(encoded(JsonPrimitive(snapshot.createdAt.toString())))) return null
        if (!append(",\"library\":[")) return null
        orderedBooks.forEachIndexed { index, book ->
            if (index != 0 && !append(",")) return null
            if (!append(encoded(bookJson(book)))) return null
        }
        if (!append("],\"shelves\":[")) return null
        orderedShelves.forEachIndexed { index, shelf ->
            if (index != 0 && !append(",")) return null
            if (!append(encoded(shelfJson(shelf)))) return null
        }
        if (!append("]")) return null
        snapshot.readerPreferences?.let { preferences ->
            val value = buildJsonObject {
                put("reader", buildJsonObject {
                    preferences.flow?.let { put("flow", it) }
                    preferences.fontScale?.let { put("fontScale", it) }
                    preferences.lineHeight?.let { put("lineHeight", it) }
                    preferences.theme?.let { put("theme", it) }
                })
            }
            if (!append(",\"preferences\":${encoded(value)}")) return null
        }
        if (!append("}")) return null
        return output.toByteArray()
    }

    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun parseTransfer(root: JsonObject): ImportParseResult {
        if (root.int("version") != 1) return ImportParseResult.Fatal("unsupported-version")
        if (!root.keys.all { it in setOf("format", "version", "createdAt", "library", "shelves", "preferences") }) {
            return ImportParseResult.Fatal("unknown-root-field")
        }
        val createdAt = root.instant("createdAt") ?: return ImportParseResult.Fatal("invalid-created-at")
        val library = root.array("library") ?: return ImportParseResult.Fatal("invalid-library")
        val shelvesJson = root.array("shelves") ?: return ImportParseResult.Fatal("invalid-shelves")
        if (library.size > 100_000 || shelvesJson.size > 5_000) return ImportParseResult.Fatal("record-limit")
        val books = ArrayList<TransferBook>(library.size)
        val seenBooks = HashSet<BookIdentity>()
        for (item in library) {
            val book = parseBook(item as? JsonObject ?: return ImportParseResult.Fatal("invalid-book"))
                ?: return ImportParseResult.Fatal("invalid-book")
            if (!seenBooks.add(book.identity)) return ImportParseResult.Fatal("duplicate-book-identity")
            books += book
        }
        val shelves = ArrayList<TransferShelf>(shelvesJson.size)
        val seenShelves = HashSet<String>()
        for (item in shelvesJson) {
            val shelf = parseShelf(item as? JsonObject ?: return ImportParseResult.Fatal("invalid-shelf"))
                ?: return ImportParseResult.Fatal("invalid-shelf")
            if (!seenShelves.add(shelf.id)) return ImportParseResult.Fatal("duplicate-shelf-id")
            shelves += shelf
        }
        val shelfIds = shelves.mapTo(hashSetOf()) { it.id }
        if (shelves.any { it.parentId != null && it.parentId !in shelfIds } || books.any { book -> book.shelfIds.any { it !in shelfIds } }) {
            return ImportParseResult.Fatal("dangling-shelf-reference")
        }
        if (hasShelfParentCycle(shelves)) return ImportParseResult.Fatal("shelf-parent-cycle")
        val preferences = root.obj("preferences")?.obj("reader")?.let(::parseReaderPreferences)
        val snapshot = TransferSnapshot(createdAt, books, shelves, preferences)
        val canonical = runCatching { encode(snapshot) }.getOrElse { return ImportParseResult.Fatal("invalid-transfer") }
        return ImportParseResult.Ready(
            ImportPlan(ImportKind.TSUYOMI_TRANSFER, createdAt, books, shelves, preferences),
            digest(canonical),
        )
    }

    private fun parseBook(value: JsonObject): TransferBook? = runCatching {
        require(value.keys.all { it in BOOK_FIELDS })
        val identityObject = requireNotNull(value.obj("identity"))
        require(identityObject.keys == setOf("sourceId", "remoteBookId"))
        val identity = BookIdentity(requireNotNull(identityObject.string("sourceId")), requireNotNull(identityObject.string("remoteBookId")))
        require(identity.sourceId.matches(SOURCE_ID))
        val title = requireNotNull(value.string("title")); require(title.length in 1..4096)
        val updatedAt = requireNotNull(value.instant("updatedAt"))
        val authors = value.stringSet("authors", 32, 1024)
        val remoteTags = value.stringSet("remoteTags", 128, 256)
        val localTags = value.stringSet("localTags", 64, 64)
        val shelfIds = value.stringSet("shelfIds", 512, 128)
        val status = value.string("status") ?: "unknown"; require(status in STATUSES)
        val rating = value.primitive("rating")?.doubleOrNull; require(rating == null || rating in 0.0..5.0)
        val canonicalUrl = value.string("canonicalUrl")?.also(::requireUri)
        val coverUrl = value.string("coverUrl")?.also(::requireUri)
        TransferBook(
            identity, title, authors, canonicalUrl, coverUrl, status, remoteTags, localTags, shelfIds,
            rating, value.instant("addedAt"), updatedAt, value.obj("progress")?.let(::parseProgress),
        )
    }.getOrNull()

    private fun parseProgress(value: JsonObject): TransferProgress {
        require(value.keys.all { it in PROGRESS_FIELDS })
        val chapterId = value.string("chapterId")
        val textAnchor = value.string("textAnchor")
        val offset = value.int("characterOffset")
        val chapterProgress = value.primitive("chapterProgress")?.doubleOrNull
        val bookProgress = value.primitive("bookProgress")?.doubleOrNull
        require(textAnchor != null || offset != null || chapterProgress != null || bookProgress != null)
        require(chapterId == null || chapterId.length in 1..1024)
        require(textAnchor == null || SHA_256.matches(textAnchor))
        require(offset == null || offset >= 0)
        require(chapterProgress == null || chapterProgress.isFinite() && chapterProgress in 0.0..1.0)
        require(bookProgress == null || bookProgress.isFinite() && bookProgress in 0.0..1.0)
        return TransferProgress(chapterId, textAnchor, offset, chapterProgress, bookProgress, requireNotNull(value.instant("updatedAt")))
    }

    private fun parseShelf(value: JsonObject): TransferShelf? = runCatching {
        require(value.keys.all { it in setOf("id", "name", "parentId", "position") })
        val id = requireNotNull(value.string("id")); require(id.length in 1..128)
        val name = requireNotNull(value.string("name")); require(name.length in 1..256)
        val parent = value.string("parentId")?.also { require(it.length in 1..128) }
        val position = value.int("position") ?: 0; require(position >= 0)
        TransferShelf(id, name, parent, position)
    }.getOrNull()

    private fun parseReaderPreferences(value: JsonObject): PortableReaderPreferences {
        require(value.keys.all { it in setOf("flow", "fontScale", "lineHeight", "theme") })
        val flow = value.string("flow"); require(flow == null || flow in setOf("scroll", "paged"))
        val fontScale = value.primitive("fontScale")?.doubleOrNull; require(fontScale == null || fontScale in 0.5..3.0)
        val lineHeight = value.primitive("lineHeight")?.doubleOrNull; require(lineHeight == null || lineHeight in 0.8..3.0)
        val theme = value.string("theme"); require(theme == null || theme in setOf("paper", "warmGray", "nightInk", "black", "inkGreen"))
        return PortableReaderPreferences(flow, fontScale, lineHeight, theme)
    }

    private fun bookJson(book: TransferBook): JsonObject = buildJsonObject {
        put("identity", buildJsonObject { put("sourceId", book.identity.sourceId); put("remoteBookId", book.identity.remoteBookId) })
        put("title", book.title)
        putStringSet("authors", book.authors)
        book.canonicalUrl?.let { put("canonicalUrl", it) }
        book.coverUrl?.let { put("coverUrl", it) }
        if (book.status != "unknown") put("status", book.status)
        putStringSet("remoteTags", book.remoteTags)
        putStringSet("localTags", book.localTags)
        putStringSet("shelfIds", book.shelfIds)
        book.rating?.let { put("rating", it) }
        book.addedAt?.let { put("addedAt", it.toString()) }
        put("updatedAt", book.updatedAt.toString())
        book.progress?.let { put("progress", progressJson(it)) }
    }

    private fun progressJson(progress: TransferProgress): JsonObject = buildJsonObject {
        progress.chapterId?.let { put("chapterId", it) }
        progress.textAnchor?.let { put("textAnchor", it) }
        progress.characterOffset?.let { put("characterOffset", it) }
        progress.chapterProgress?.let { put("chapterProgress", it) }
        progress.bookProgress?.let { put("bookProgress", it) }
        put("updatedAt", progress.updatedAt.toString())
    }

    private fun shelfJson(shelf: TransferShelf): JsonObject = buildJsonObject {
        put("id", shelf.id); put("name", shelf.name)
        shelf.parentId?.let { put("parentId", it) }
        put("position", shelf.position)
    }

    private fun canonicalShelves(shelves: List<TransferShelf>): List<TransferShelf> {
        require(shelves.map { it.id }.distinct().size == shelves.size) { "Duplicate shelf id" }
        require(!hasShelfParentCycle(shelves)) { "Shelf parent cycle" }
        return shelves.sortedWith(compareBy({ it.parentId ?: "" }, { it.position }, { it.id }))
    }


    private fun requireUri(value: String) { require(runCatching { URI(value) }.getOrNull()?.isAbsolute == true) }

    private val SOURCE_ID = Regex("^[a-z0-9](?:[a-z0-9.-]{0,126}[a-z0-9])?$")
    private val STATUSES = setOf("unknown", "ongoing", "completed", "hiatus", "cancelled")
    private val BOOK_FIELDS = setOf("identity", "title", "authors", "canonicalUrl", "coverUrl", "status", "remoteTags", "localTags", "shelfIds", "rating", "addedAt", "updatedAt", "progress")
    private val PROGRESS_FIELDS = setOf("chapterId", "textAnchor", "characterOffset", "chapterProgress", "bookProgress", "updatedAt")
}

internal fun hasShelfParentCycle(shelves: List<TransferShelf>): Boolean {
    val parents = shelves.associate { it.id to it.parentId }
    return shelves.any { shelf ->
        val visited = hashSetOf<String>()
        var cursor: String? = shelf.id
        while (cursor != null && visited.add(cursor)) cursor = parents[cursor]
        cursor != null
    }
}

internal val SHA_256 = Regex("^[a-f0-9]{64}$")

internal fun JsonObject.string(name: String): String? = primitive(name)?.contentOrNull
internal fun JsonObject.int(name: String): Int? = primitive(name)?.intOrNull
internal fun JsonObject.instant(name: String): Instant? = string(name)?.let { runCatching { Instant.parse(it) }.getOrNull() }
internal fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
internal fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray
internal fun JsonObject.primitive(name: String): JsonPrimitive? = this[name] as? JsonPrimitive
internal fun JsonObject.stringSet(name: String, maxItems: Int, maxCodePoints: Int): Set<String> {
    val values = array(name) ?: return emptySet()
    require(values.size <= maxItems)
    val strings = values.map {
        requireNotNull((it as? JsonPrimitive)?.contentOrNull).also { value ->
            require(value.codePointCount(0, value.length) in 1..maxCodePoints)
        }
    }
    require(strings.toSet().size == strings.size)
    return strings.toSortedSet()
}
internal fun kotlinx.serialization.json.JsonObjectBuilder.putStringSet(name: String, values: Set<String>) {
    if (values.isNotEmpty()) put(name, buildJsonArray { values.sorted().forEach { add(JsonPrimitive(it)) } })
}
