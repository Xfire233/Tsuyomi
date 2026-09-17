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
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.MAX_READER_BOOKMARKS_PER_BOOK
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.locator.bookmarkPositionKey

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
            put("version", CURRENT_VERSION)
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
                        preferences.horizontalMargin?.let { put("horizontalMargin", it) }
                        preferences.paragraphSpacing?.let { put("paragraphSpacing", it) }
                        preferences.lockPortrait?.let { put("lockPortrait", it) }
                        preferences.progressVisible?.let { put("progressVisible", it) }
                        preferences.immersive?.let { put("immersive", it) }
                        preferences.keepAwake?.let { put("keepAwake", it) }
                        preferences.volumePaging?.let { put("volumePaging", it) }
                        preferences.fontFamily?.let { put("fontFamily", it) }
                        preferences.fontWeight?.let { put("fontWeight", it) }
                        preferences.letterSpacing?.let { put("letterSpacing", it) }
                        preferences.firstLineIndent?.let { put("firstLineIndent", it) }
                        preferences.verticalMargin?.let { put("verticalMargin", it) }
                        preferences.textAlignment?.let { put("textAlignment", it) }
                        preferences.foregroundColor?.let { put("foregroundColor", it) }
                        preferences.backgroundColor?.let { put("backgroundColor", it) }
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

        if (!append("{\"format\":\"tsuyomi-transfer\",\"version\":$CURRENT_VERSION,\"createdAt\":")) return null
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
                    preferences.horizontalMargin?.let { put("horizontalMargin", it) }
                    preferences.paragraphSpacing?.let { put("paragraphSpacing", it) }
                    preferences.lockPortrait?.let { put("lockPortrait", it) }
                    preferences.progressVisible?.let { put("progressVisible", it) }
                    preferences.immersive?.let { put("immersive", it) }
                    preferences.keepAwake?.let { put("keepAwake", it) }
                    preferences.volumePaging?.let { put("volumePaging", it) }
                    preferences.fontFamily?.let { put("fontFamily", it) }
                    preferences.fontWeight?.let { put("fontWeight", it) }
                    preferences.letterSpacing?.let { put("letterSpacing", it) }
                    preferences.firstLineIndent?.let { put("firstLineIndent", it) }
                    preferences.verticalMargin?.let { put("verticalMargin", it) }
                    preferences.textAlignment?.let { put("textAlignment", it) }
                    preferences.foregroundColor?.let { put("foregroundColor", it) }
                    preferences.backgroundColor?.let { put("backgroundColor", it) }
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
        val version = root.int("version") ?: return ImportParseResult.Fatal("unsupported-version")
        if (version !in 1..CURRENT_VERSION) return ImportParseResult.Fatal("unsupported-version")
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
            val book = parseBook(item as? JsonObject ?: return ImportParseResult.Fatal("invalid-book"), version)
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
        val preferencesObject = root["preferences"]?.let { value ->
            value as? JsonObject ?: return ImportParseResult.Fatal("invalid-reader-preferences")
        }
        if (preferencesObject != null && preferencesObject.keys.any { it != "reader" }) {
            return ImportParseResult.Fatal("invalid-reader-preferences")
        }
        val readerObject = preferencesObject?.get("reader")?.let { value ->
            value as? JsonObject ?: return ImportParseResult.Fatal("invalid-reader-preferences")
        }
        val preferences = runCatching { readerObject?.let { parseReaderPreferences(it, version) } }
            .getOrElse { return ImportParseResult.Fatal("invalid-reader-preferences") }
        val snapshot = TransferSnapshot(createdAt, books, shelves, preferences)
        val canonical = runCatching { encode(snapshot) }.getOrElse { return ImportParseResult.Fatal("invalid-transfer") }
        return ImportParseResult.Ready(
            ImportPlan(ImportKind.TSUYOMI_TRANSFER, createdAt, books, shelves, preferences),
            digest(canonical),
        )
    }

    private fun parseBook(value: JsonObject, version: Int): TransferBook? = runCatching {
        require(value.keys.all { key -> key in when (version) {
            1 -> BOOK_FIELDS_V1
            2 -> BOOK_FIELDS_V2
            3 -> BOOK_FIELDS_V3
            4 -> BOOK_FIELDS_V4
            else -> BOOK_FIELDS_V5
        } })
        val identityObject = requireNotNull(value.obj("identity"))
        require(identityObject.keys == setOf("sourceId", "remoteBookId"))
        val identity = BookIdentity(
            sourceId = if (version == 5) requireNotNull(identityObject.strictString("sourceId")) else requireNotNull(identityObject.string("sourceId")),
            remoteBookId = if (version == 5) requireNotNull(identityObject.strictString("remoteBookId")) else requireNotNull(identityObject.string("remoteBookId")),
        )
        require(identity.sourceId.matches(SOURCE_ID))
        val title = requireNotNull(value.string("title")); require(title.length in 1..4096)
        val updatedAt = requireNotNull(value.instant("updatedAt"))
        val authors = value.stringSet("authors", 32, 1024)
        val remoteTags = value.stringSet("remoteTags", 128, 256)
        val localTags = value.stringSet("localTags", 64, 64)
        val shelfIds = value.stringSet("shelfIds", 512, 128)
        val completedChapterIds = if (version == 1) emptySet() else value.completedChapterIdSet("completedChapterIds")
        val bookmarks = when (version) {
            5 -> value.readerLocatorList("bookmarks", identity)
            4 -> value.legacyBookmarkedChapterIdSet("bookmarkedChapterIds").map { chapterId ->
                legacyChapterStartBookmark(identity, chapterId)
            }
            else -> emptyList()
        }
        val status = value.string("status") ?: "unknown"; require(status in STATUSES)
        val rating = value.primitive("rating")?.doubleOrNull; require(rating == null || rating in 0.0..5.0)
        val canonicalUrl = value.string("canonicalUrl")?.also(::requireUri)
        val coverUrl = value.string("coverUrl")?.also(::requireUri)
        TransferBook(
            identity = identity,
            title = title,
            authors = authors,
            canonicalUrl = canonicalUrl,
            coverUrl = coverUrl,
            status = status,
            remoteTags = remoteTags,
            localTags = localTags,
            shelfIds = shelfIds,
            rating = rating,
            readLater = version != 1 && (value.primitive("readLater")?.booleanOrNull ?: false),
            localPin = if (version >= 3) {
                requireNotNull(value.primitive("localPin")?.takeUnless { it.isString }?.booleanOrNull)
            } else {
                true
            }.also { localPin -> require(localPin || shelfIds.isEmpty()) },
            addedAt = value.instant("addedAt"),
            updatedAt = updatedAt,
            progress = value.obj("progress")?.let(::parseProgress),
            completedChapterIds = completedChapterIds,
            bookmarks = bookmarks,
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

    private fun parseReaderPreferences(value: JsonObject, version: Int): PortableReaderPreferences {
        val allowedFields = when (version) {
            1 -> READER_FIELDS_V1
            2, 3 -> READER_FIELDS_V2
            else -> READER_FIELDS_V4
        }
        require(value.keys.all { it in allowedFields })
        fun string(name: String, allowed: Set<String>): String? {
            val element = value[name] ?: return null
            require(element is JsonPrimitive && element.isString)
            return requireNotNull(element.contentOrNull).also { require(it in allowed) }
        }
        fun number(name: String, range: ClosedFloatingPointRange<Double>): Double? {
            val element = value[name] ?: return null
            require(element is JsonPrimitive && !element.isString)
            return requireNotNull(element.doubleOrNull).also { require(it.isFinite() && it in range) }
        }
        fun integer(name: String, range: IntRange): Int? {
            val element = value[name] ?: return null
            require(element is JsonPrimitive && !element.isString)
            return requireNotNull(element.intOrNull).also { require(it in range) }
        }
        fun color(name: String): String? {
            val element = value[name] ?: return null
            require(element is JsonPrimitive && element.isString)
            return requireNotNull(element.contentOrNull).also { require(HEX_COLOR.matches(it)) }
        }
        fun boolean(name: String): Boolean? {
            val element = value[name] ?: return null
            require(element is JsonPrimitive && !element.isString)
            return requireNotNull(element.booleanOrNull)
        }
        return PortableReaderPreferences(
            flow = string("flow", if (version >= 4) READER_FLOWS_V4 else READER_FLOWS_LEGACY),
            fontScale = number("fontScale", 0.5..3.0),
            lineHeight = number("lineHeight", 0.8..3.0),
            theme = string("theme", THEMES),
            horizontalMargin = number("horizontalMargin", 12.0..40.0),
            paragraphSpacing = number("paragraphSpacing", 0.0..32.0),
            lockPortrait = boolean("lockPortrait"),
            progressVisible = boolean("progressVisible"),
            immersive = boolean("immersive"),
            keepAwake = boolean("keepAwake"),
            volumePaging = boolean("volumePaging"),
            fontFamily = string("fontFamily", FONT_FAMILIES),
            fontWeight = integer("fontWeight", 400..500)?.also { require(it in FONT_WEIGHTS) },
            letterSpacing = number("letterSpacing", -0.05..0.20),
            firstLineIndent = number("firstLineIndent", 0.0..4.0),
            verticalMargin = number("verticalMargin", 0.0..64.0),
            textAlignment = string("textAlignment", TEXT_ALIGNMENTS),
            foregroundColor = color("foregroundColor"),
            backgroundColor = color("backgroundColor"),
        )
    }

    private fun bookJson(book: TransferBook): JsonObject {
        require(book.localPin || book.shelfIds.isEmpty()) {
            "Unpinned transfer books cannot have manual collection memberships"
        }
        require(book.bookmarks.size <= MAX_READER_BOOKMARKS_PER_BOOK)
        val bookmarkKeys = book.bookmarks.map { locator ->
            require(locator.document.book == book.identity) { "Bookmark document must belong to its book" }
            locator.bookmarkPositionKey()
        }
        require(bookmarkKeys.distinct().size == bookmarkKeys.size) { "Duplicate bookmark position" }
        return buildJsonObject {
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
            if (book.readLater) put("readLater", true)
            put("localPin", book.localPin)
            book.addedAt?.let { put("addedAt", it.toString()) }
            put("updatedAt", book.updatedAt.toString())
            book.progress?.let { put("progress", progressJson(it)) }
            putStringSet("completedChapterIds", book.completedChapterIds)
            put("bookmarks", buildJsonArray {
                book.bookmarks.sortedBy(ReaderLocator::bookmarkPositionKey).forEach { add(readerLocatorJson(it)) }
            })
        }
    }

    private fun progressJson(progress: TransferProgress): JsonObject = buildJsonObject {
        progress.chapterId?.let { put("chapterId", it) }
        progress.textAnchor?.let { put("textAnchor", it) }
        progress.characterOffset?.let { put("characterOffset", it) }
        progress.chapterProgress?.let { put("chapterProgress", it) }
        progress.bookProgress?.let { put("bookProgress", it) }
        put("updatedAt", progress.updatedAt.toString())
    }

    private fun readerLocatorJson(locator: ReaderLocator): JsonObject = buildJsonObject {
        put("document", buildJsonObject {
            put("sourceId", locator.document.sourceId)
            put("remoteBookId", locator.document.remoteBookId)
            put("contentId", locator.document.contentId)
            locator.document.revision?.let { put("revision", it) }
        })
        locator.blockId?.let { put("blockId", it) }
        locator.textAnchorDigest?.let { put("textAnchorDigest", it) }
        locator.characterOffset?.let { put("characterOffset", it) }
        locator.chapterProgress?.let { put("chapterProgress", it) }
        locator.bookProgress?.let { put("bookProgress", it) }
        put("capturedAt", locator.capturedAt.toString())
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
    private val BOOK_FIELDS_V1 = setOf("identity", "title", "authors", "canonicalUrl", "coverUrl", "status", "remoteTags", "localTags", "shelfIds", "rating", "addedAt", "updatedAt", "progress")
    private val BOOK_FIELDS_V2 = BOOK_FIELDS_V1 + setOf("readLater", "completedChapterIds")
    private val BOOK_FIELDS_V3 = BOOK_FIELDS_V2 + "localPin"
    private val BOOK_FIELDS_V4 = BOOK_FIELDS_V3 + "bookmarkedChapterIds"
    private val BOOK_FIELDS_V5 = BOOK_FIELDS_V3 + "bookmarks"
    private val READER_FIELDS_V1 = setOf("flow", "fontScale", "lineHeight", "theme")
    private val READER_FIELDS_V2 = READER_FIELDS_V1 + setOf("horizontalMargin", "paragraphSpacing", "lockPortrait", "progressVisible", "immersive", "keepAwake", "volumePaging")
    private val READER_FIELDS_V4 = READER_FIELDS_V2 + setOf("fontFamily", "fontWeight", "letterSpacing", "firstLineIndent", "verticalMargin", "textAlignment", "foregroundColor", "backgroundColor")
    private val READER_FLOWS_LEGACY = setOf("scroll", "paged")
    private val READER_FLOWS_V4 = READER_FLOWS_LEGACY + "dual"
    private val THEMES = setOf("paper", "warmGray", "nightInk", "black", "inkGreen")
    private val FONT_FAMILIES = setOf("system", "sans", "serif", "monospace")
    private val TEXT_ALIGNMENTS = setOf("start", "justify", "center", "end")
    private val HEX_COLOR = Regex("^#[0-9A-F]{6}$")
    private const val CURRENT_VERSION = 5
    private val FONT_WEIGHTS = setOf(400, 500)
    internal const val MAX_COMPLETED_CHAPTERS_PER_BOOK = 20_000
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
    return values.map {
        requireNotNull((it as? JsonPrimitive)?.contentOrNull).also { value ->
            require(value.codePointCount(0, value.length) <= maxCodePoints)
        }
    }.toSortedSet()
}
internal fun JsonObject.completedChapterIdSet(name: String): Set<String> {
    val raw = this[name] ?: return emptySet()
    val values = raw as? JsonArray ?: error("Invalid completed chapter IDs")
    require(values.size <= TransferCodec.MAX_COMPLETED_CHAPTERS_PER_BOOK)
    val strings = values.map { element ->
        val value = element as? JsonPrimitive ?: error("Invalid completed chapter ID")
        require(value.isString)
        value.content.also { chapterId ->
            require(chapterId.isNotBlank())
            require(chapterId.codePointCount(0, chapterId.length) <= 512)
        }
    }
    require(strings.toSet().size == strings.size)
    return strings.toSortedSet()
}
internal fun JsonObject.legacyBookmarkedChapterIdSet(name: String): Set<String> {
    val raw = requireNotNull(this[name]) { "Missing bookmarked chapter IDs" }
    val values = raw as? JsonArray ?: error("Invalid bookmarked chapter IDs")
    require(values.size <= MAX_READER_BOOKMARKS_PER_BOOK)
    val strings = values.map { element ->
        val value = element as? JsonPrimitive ?: error("Invalid bookmarked chapter ID")
        require(value.isString)
        value.content.also { chapterId ->
            require(chapterId.isNotBlank())
            require(chapterId.codePointCount(0, chapterId.length) <= 512)
        }
    }
    require(strings.toSet().size == strings.size)
    return strings.toSortedSet()
}

internal fun JsonObject.readerLocatorList(name: String, identity: BookIdentity): List<ReaderLocator> {
    val values = requireNotNull(this[name]) { "Missing bookmarks" } as? JsonArray
        ?: error("Invalid bookmarks")
    require(values.size <= MAX_READER_BOOKMARKS_PER_BOOK)
    val locators = values.map { element ->
        (element as? JsonObject ?: error("Invalid bookmark")).toReaderLocator(identity)
    }
    require(locators.map(ReaderLocator::bookmarkPositionKey).distinct().size == locators.size) {
        "Duplicate bookmark position"
    }
    return locators.sortedBy(ReaderLocator::bookmarkPositionKey)
}

private fun JsonObject.toReaderLocator(identity: BookIdentity): ReaderLocator {
    require(keys.all { it in READER_LOCATOR_FIELDS })
    val documentJson = requireNotNull(obj("document"))
    require(documentJson.keys.all { it in READER_LOCATOR_DOCUMENT_FIELDS })
    val document = DocumentIdentity(
        sourceId = requireNotNull(documentJson.strictString("sourceId")),
        remoteBookId = requireNotNull(documentJson.strictString("remoteBookId")),
        contentId = requireNotNull(documentJson.strictString("contentId")),
        revision = documentJson.strictString("revision"),
    )
    require(document.book == identity) { "Bookmark document must belong to its book" }
    return ReaderLocator(
        document = document,
        blockId = strictString("blockId"),
        textAnchorDigest = strictString("textAnchorDigest"),
        characterOffset = strictInt("characterOffset"),
        chapterProgress = strictDouble("chapterProgress"),
        bookProgress = strictDouble("bookProgress"),
        capturedAt = requireNotNull(strictInstant("capturedAt")),
    )
}

private fun JsonObject.strictString(name: String): String? {
    val value = this[name] ?: return null
    require(value is JsonPrimitive && value.isString)
    return value.content
}

private fun JsonObject.strictInt(name: String): Int? {
    val value = this[name] ?: return null
    require(value is JsonPrimitive && !value.isString)
    return requireNotNull(value.intOrNull)
}

private fun JsonObject.strictDouble(name: String): Double? {
    val value = this[name] ?: return null
    require(value is JsonPrimitive && !value.isString)
    return requireNotNull(value.doubleOrNull)
}

private fun JsonObject.strictInstant(name: String): Instant? = strictString(name)?.let { value ->
    requireNotNull(runCatching { Instant.parse(value) }.getOrNull())
}

private fun legacyChapterStartBookmark(identity: BookIdentity, chapterId: String): ReaderLocator = ReaderLocator(
    document = DocumentIdentity(identity.sourceId, identity.remoteBookId, chapterId),
    chapterProgress = 0.0,
    capturedAt = Instant.EPOCH,
)

private val READER_LOCATOR_FIELDS = setOf(
    "document", "blockId", "textAnchorDigest", "characterOffset", "chapterProgress", "bookProgress", "capturedAt",
)
private val READER_LOCATOR_DOCUMENT_FIELDS = setOf("sourceId", "remoteBookId", "contentId", "revision")
internal fun kotlinx.serialization.json.JsonObjectBuilder.putStringSet(name: String, values: Set<String>) {
    if (values.isNotEmpty()) put(name, buildJsonArray { values.sorted().forEach { add(JsonPrimitive(it)) } })
}
