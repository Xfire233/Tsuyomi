/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.backup

import java.net.URI
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.tsuyomi.shared.smartshelf.HikariSmartCondition
import org.tsuyomi.shared.smartshelf.HikariSmartRuleTranslation
import org.tsuyomi.shared.smartshelf.HikariSmartRuleTranslator
import org.tsuyomi.shared.smartshelf.SmartRuleCodec
import org.tsuyomi.shared.model.BookIdentity

internal object HikariBackupCodec {
    fun parse(root: JsonObject): ImportParseResult = try {
        if (root.int("schemaVersion") != 1) return ImportParseResult.Fatal("unsupported-version")
        val createdAt = root.instant("createdAt") ?: return ImportParseResult.Fatal("invalid-created-at")
        val payload = root.obj("payload") ?: return ImportParseResult.Fatal("invalid-payload")
        if (!withinRecordBounds(payload)) return ImportParseResult.Fatal("record-limit")
        val warnings = mutableListOf<ImportWarning>()
        warnSecrets(payload, warnings)
        val shelves = parseShelves(payload.obj("bookshelf"), warnings)
        if (hasShelfParentCycle(shelves)) return ImportParseResult.Fatal("shelf-parent-cycle")
        val progress = parseProgress(payload.obj("readingData"), createdAt, warnings)
        val books = parseBooks(payload.obj("bookshelf"), createdAt, progress, shelves, warnings)
        val reader = parseReaderPreferences(payload.obj("readerSettings"), warnings)
        val manualEInk = payload.obj("appSettings")?.let { settings ->
            settings.bool("browsingEInkMode") == true || payload.obj("readerSettings")?.bool("readerEInkMode") == true
        } == true
        val search = parseSearchHistory(payload.obj("readingData"), createdAt, warnings)
        val browsing = parseBrowsingHistory(payload.obj("readingData"), createdAt, warnings)
        val (smartCollections, subscriptionDrafts) = parseSmartSettings(payload.obj("appSettings"), warnings)
        val plan = ImportPlan(
            kind = ImportKind.HIKARI_BACKUP,
            sourceCreatedAt = createdAt,
            books = books,
            shelves = shelves,
            readerPreferences = reader,
            forceManualEInk = manualEInk,
            searchHistory = search,
            browsingHistory = browsing,
            warnings = warnings,
            smartCollections = smartCollections,
            subscriptionDrafts = subscriptionDrafts,
        )
        ImportParseResult.Ready(plan, TransferCodec.digest(canonicalPlanDigestInput(plan)))
    } catch (_: WarningLimitExceeded) {
        ImportParseResult.Fatal("warning-limit")
    }

    private fun withinRecordBounds(payload: JsonObject): Boolean {
        val bookshelf = payload.obj("bookshelf")
        val readingData = payload.obj("readingData")
        val appSettings = payload.obj("appSettings")
        val counts = listOf(
            bookshelf.recordCount("items") to MAX_HIKARI_BOOKS,
            bookshelf.recordCount("folders") to MAX_HIKARI_FOLDERS,
            readingData.recordCount("readHistory") to MAX_HIKARI_PROGRESS,
            readingData.recordCount("searchHistory") to MAX_HIKARI_SEARCH_HISTORY,
            readingData.recordCount("browsingHistory") to MAX_HIKARI_BROWSING_HISTORY,
            appSettings.recordCount("smartShelfMemberships") to MAX_HIKARI_SMART_RECORDS,
            appSettings.recordCount("smartShelfSyncMetadata") to MAX_HIKARI_SUBSCRIPTION_RECORDS,
        )
        return counts.all { (count, maximum) -> count <= maximum } &&
            counts.sumOf { it.first }.toLong() <= MAX_HIKARI_RECORDS.toLong()
    }

    private fun parseBooks(
        bookshelf: JsonObject?,
        createdAt: Instant,
        progress: Map<BookIdentity, TransferProgress>,
        shelves: List<TransferShelf>,
        warnings: MutableList<ImportWarning>,
    ): List<TransferBook> {
        val items = bookshelf?.array("items") ?: JsonArray(emptyList())
        val knownShelves = shelves.mapTo(hashSetOf()) { it.id }
        val books = linkedMapOf<BookIdentity, TransferBook>()
        items.forEachIndexed { index, element ->
            val item = element as? JsonObject
            val identity = item?.string("aid")?.let(::legacyIdentity)
            val title = item?.firstString("title", "name", "bookName")?.trim()?.takeIf { it.length in 1..4096 }
            if (identity == null || title == null) {
                warnings += warning(warnings, "invalid-book-record", "bookshelf.items[$index]")
                return@forEachIndexed
            }
            if (identity in books) {
                warnings += warning(warnings, "duplicate-book-identity", identity.safeRef(), severity = ImportSeverity.CONFLICT)
                return@forEachIndexed
            }
            val updatedAt = item.instant("updatedAt") ?: item.instant("lastUpdate") ?: createdAt
            val folder = item.firstString("folderId", "shelfId")
            val localTags = item.stringListLenient("localTags", warnings, "bookshelf.items[$index].localTags", 64, 64)
            val remoteTags = item.stringListLenient("tags", warnings, "bookshelf.items[$index].tags", 128, 256)
            val canonicalUrl = item.firstString("url", "canonicalUrl").portableUriOrNull(warnings, "bookshelf.items[$index].canonicalUrl")
            val coverUrl = item.firstString("coverUrl", "cover").portableUriOrNull(warnings, "bookshelf.items[$index].coverUrl")
            val rating = item.primitive("rating")?.doubleOrNull?.takeIf { it > 0.0 && it <= 5.0 }
            books[identity] = TransferBook(
                identity = identity,
                title = title,
                authors = item.stringListLenient("authors", warnings, "bookshelf.items[$index].authors", 32, 1024).toSet(),
                canonicalUrl = canonicalUrl,
                coverUrl = coverUrl,
                status = normalizeStatus(item.string("status")),
                remoteTags = remoteTags.toSet(),
                localTags = localTags.toSet(),
                shelfIds = setOfNotNull(folder?.takeIf { it in knownShelves }),
                rating = rating,
                addedAt = item.instant("addedAt") ?: createdAt,
                updatedAt = updatedAt,
                progress = progress[identity],
            )
        }
        return books.values.sortedWith(compareBy({ it.identity.sourceId }, { it.identity.remoteBookId }))
    }

    private fun parseShelves(
        bookshelf: JsonObject?,
        warnings: MutableList<ImportWarning>,
    ): List<TransferShelf> {
        val folders = bookshelf?.array("folders") ?: return emptyList()
        val result = linkedMapOf<String, TransferShelf>()
        folders.forEachIndexed { index, element ->
            val folder = element as? JsonObject
            val id = folder?.firstString("id", "folderId")?.trim()?.takeIf { it.length in 1..128 }
            val name = folder?.firstString("name", "title")?.trim()?.takeIf { it.length in 1..256 }
            val parentId = folder?.string("parentId")?.trim()?.takeIf { it.length in 1..128 }
            val rawParentId = folder?.string("parentId")
            if (id == null || name == null || result.containsKey(id) || rawParentId != null && parentId == null) {
                warnings += warning(warnings, "invalid-manual-shelf", "bookshelf.folders[$index]")
            } else {
                result[id] = TransferShelf(
                    id = id,
                    name = name,
                    parentId = parentId,
                    position = folder.primitive("position")?.intOrNull?.coerceAtLeast(0) ?: index,
                )
            }
        }
        val ids = result.keys
        return result.values.map { shelf ->
            if (shelf.parentId != null && shelf.parentId !in ids) {
                warnings += warning(warnings, "dangling-shelf-parent", shelf.id, "parentId")
                shelf.copy(parentId = null)
            } else shelf
        }
    }

    private fun parseProgress(
        readingData: JsonObject?,
        createdAt: Instant,
        warnings: MutableList<ImportWarning>,
    ): Map<BookIdentity, TransferProgress> {
        val rows = readingData?.array("readHistory") ?: return emptyMap()
        val result = linkedMapOf<BookIdentity, TransferProgress>()
        rows.forEachIndexed { index, element ->
            val row = element as? JsonObject
            val identity = row?.string("aid")?.let(::legacyIdentity)
            if (identity == null) {
                warnings += warning(warnings, "invalid-progress-identity", "readingData.readHistory[$index]")
                return@forEachIndexed
            }
            val semantic = row.string("locatorJson")?.let(::parseSemanticLocator)
            val candidate = semantic ?: run {
                val rawChapterId = row.firstString("cid", "chapterId")
                val chapterId = rawChapterId?.trim()?.takeIf { it.length in 1..1024 }
                val offset = row.primitive("location")?.intOrNull?.takeIf { it >= 0 }
                val bookProgress = row.primitive("progress")?.doubleOrNull?.takeIf { it.isFinite() && it in 0.0..1.0 }
                if (offset == null && bookProgress == null || rawChapterId != null && chapterId == null) {
                    warnings += warning(warnings, "invalid-progress-record", identity.safeRef())
                    return@forEachIndexed
                }
                warnings += warning(warnings, "reduced-progress-time-precision", identity.safeRef(), "updatedAt")
                TransferProgress(chapterId, characterOffset = offset, bookProgress = bookProgress, updatedAt = createdAt)
            }
            result.putIfAbsent(identity, candidate.copy(updatedAt = createdAt))
        }
        return result
    }

    private fun parseSemanticLocator(encoded: String): TransferProgress? {
        if (encoded.length > MAX_HIKARI_LOCATOR_JSON_LENGTH) return null
        val locator = runCatching { Json.parseToJsonElement(encoded) as? JsonObject }.getOrNull() ?: return null
        if (locator.strictInt("v") != 1) return null
        return when (locator.string("kind")) {
            "chapter" -> parseChapterLocator(locator)
            "yamiboReply" -> parseYamiboLocator(locator)
            else -> null
        }
    }

    private fun parseChapterLocator(locator: JsonObject): TransferProgress? {
        if (locator.keys.any { it !in CHAPTER_LOCATOR_FIELDS }) return null
        val chapterId = locator.string("chapterId")?.takeIf(::isHikariIdentifier) ?: return null
        val paragraph = locator.strictInt("paragraphIndex")?.takeIf { it in 0..MAX_HIKARI_LOCATOR_INDEX } ?: return null
        val offset = locator.optionalStrictInt("characterOffset")
        if ("characterOffset" in locator && offset == null || offset != null && offset !in 0..MAX_HIKARI_LOCATOR_INDEX) return null
        val chunkProgress = locator.optionalStrictDouble("chunkProgress")
        if ("chunkProgress" in locator && chunkProgress == null || chunkProgress != null && chunkProgress !in 0.0..1.0) return null
        val fallbackProgress = locator.optionalStrictDouble("fallbackProgress")
        if ("fallbackProgress" in locator && fallbackProgress == null || fallbackProgress != null && fallbackProgress !in 0.0..100.0) return null
        return TransferProgress(
            chapterId = chapterId,
            textAnchor = semanticAnchor("chapter", chapterId, paragraph.toString()),
            characterOffset = offset,
            chapterProgress = chunkProgress,
            bookProgress = fallbackProgress?.div(100.0),
            updatedAt = Instant.EPOCH,
        )
    }

    private fun parseYamiboLocator(locator: JsonObject): TransferProgress? {
        if (locator.keys.any { it !in YAMIBO_LOCATOR_FIELDS }) return null
        val threadId = locator.string("threadId")?.takeIf(::isHikariIdentifier) ?: return null
        val physicalPage = locator.strictInt("physicalPage")?.takeIf { it in 1..MAX_HIKARI_LOCATOR_INDEX } ?: return null
        val postId = locator.optionalIdentifier("postId")
        if ("postId" in locator && postId == null) return null
        val floor = locator.optionalStrictInt("floorNumber")
        if ("floorNumber" in locator && floor == null || floor != null && floor !in 1..MAX_HIKARI_LOCATOR_INDEX || postId == null && floor == null) return null
        val blockProgress = locator.optionalStrictDouble("blockProgress")
        if ("blockProgress" in locator && blockProgress == null || blockProgress != null && blockProgress !in 0.0..1.0) return null
        val fallbackProgress = locator.optionalStrictDouble("fallbackProgress")
        if ("fallbackProgress" in locator && fallbackProgress == null || fallbackProgress != null && fallbackProgress !in 0.0..100.0) return null
        val chapterId = "yamibo:$threadId:$physicalPage"
        return TransferProgress(
            chapterId = chapterId,
            textAnchor = semanticAnchor("yamiboReply", threadId, physicalPage.toString(), postId ?: floor.toString()),
            chapterProgress = blockProgress,
            bookProgress = fallbackProgress?.div(100.0),
            updatedAt = Instant.EPOCH,
        )
    }

    private fun semanticAnchor(vararg segments: String): String =
        TransferCodec.digest(segments.joinToString("\u0000").toByteArray(Charsets.UTF_8))

    private fun parseSearchHistory(readingData: JsonObject?, createdAt: Instant, warnings: MutableList<ImportWarning>): List<SourceSearchHistory> =
        readingData?.array("searchHistory").orEmpty().mapIndexedNotNull { index, element ->
            val row = element as? JsonObject
            val sourceId = row?.firstString("sourceId", "source")?.let(::legacySourceId)
            val query = row?.firstString("query", "keyword")?.trim()
            if (sourceId == null || query.isNullOrEmpty()) {
                warnings += warning(warnings, "invalid-search-history", "readingData.searchHistory[$index]")
                null
            } else SourceSearchHistory(sourceId, query.take(256), row.instant("lastUsedAt") ?: createdAt)
        }

    private fun parseBrowsingHistory(readingData: JsonObject?, createdAt: Instant, warnings: MutableList<ImportWarning>): List<SourceBrowsingHistory> =
        readingData?.array("browsingHistory").orEmpty().mapIndexedNotNull { index, element ->
            val row = element as? JsonObject
            val identity = row?.string("aid")?.let(::legacyIdentity)
            if (identity == null) {
                warnings += warning(warnings, "invalid-browsing-history", "readingData.browsingHistory[$index]")
                null
            } else SourceBrowsingHistory(identity, row.instant("lastViewedAt") ?: createdAt)
        }

    private fun parseReaderPreferences(settings: JsonObject?, warnings: MutableList<ImportWarning>): PortableReaderPreferences? {
        settings ?: return null
        val flow = when (settings.firstString("flow", "readingMode")?.lowercase()) {
            "scroll", "vertical" -> "scroll"
            "paged", "page", "horizontal" -> "paged"
            null -> null
            else -> { warnings += warning(warnings, "unknown-reader-flow", fieldName = "flow"); null }
        }
        val fontScale = settings.primitive("fontScale")?.doubleOrNull?.takeIf { it in 0.5..3.0 }
        val lineHeight = settings.primitive("lineHeight")?.doubleOrNull?.takeIf { it in 0.8..3.0 }
        val theme = settings.string("theme")?.takeIf { it in setOf("paper", "warmGray", "nightInk", "black", "inkGreen") }
        return PortableReaderPreferences(flow, fontScale, lineHeight, theme)
    }

    private fun parseSmartSettings(
        settings: JsonObject?,
        warnings: MutableList<ImportWarning>,
    ): Pair<List<ImportedSmartCollection>, List<ImportedSubscriptionDraft>> {
        settings ?: return emptyList<ImportedSmartCollection>() to emptyList()
        val smart = mutableListOf<ImportedSmartCollection>()
        val drafts = mutableListOf<ImportedSubscriptionDraft>()
        settings.array("smartShelfMemberships").orEmpty().forEachIndexed { index, element ->
            val row = element as? JsonObject
            val id = row?.firstString("id", "shelfId")?.trim()?.takeIf { it.length in 1..128 }
            val title = row?.firstString("name", "title")?.trim()?.takeIf { it.length in 1..256 }
            val conditions = row?.array("conditions")
            if (id == null || title == null || conditions == null || conditions.size > 128) {
                warnings += warning(warnings, "invalid-smart-rule", "appSettings.smartShelfMemberships[$index]")
                return@forEachIndexed
            }
            val parsedConditions = conditions.mapNotNull { conditionElement ->
                val condition = conditionElement as? JsonObject ?: return@mapNotNull null
                val field = condition.firstString("field", "type") ?: return@mapNotNull null
                val values = condition.array("values")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.take(64)
                    ?: listOfNotNull(condition.firstString("value", "term"))
                HikariSmartCondition(
                    field = field,
                    values = values,
                    matchAll = condition.bool("matchAll") == true || condition.string("match") == "all",
                    minimum = condition.primitive("minimum")?.doubleOrNull,
                    maximum = condition.primitive("maximum")?.doubleOrNull,
                    days = condition.primitive("days")?.longOrNull,
                    excluded = condition.bool("excluded") == true,
                )
            }
            if (parsedConditions.size != conditions.size) {
                warnings += warning(warnings, "invalid-smart-condition", id)
                drafts += ImportedSubscriptionDraft(id, title, "disabled-smart", "[]", "{}")
                return@forEachIndexed
            }
            when (val translated = HikariSmartRuleTranslator.translate(row.bool("matchAll") != false, parsedConditions)) {
                is HikariSmartRuleTranslation.Compatible -> smart += ImportedSmartCollection(id, title, SmartRuleCodec.encode(translated.rule))
                is HikariSmartRuleTranslation.DisabledDraft -> {
                    warnings += warning(warnings, translated.warningCode, id)
                    drafts += ImportedSubscriptionDraft(id, title, "disabled-smart", "[]", "{}")
                }
            }
        }
        if (settings["smartShelfSyncMetadata"]?.isNonEmpty() == true) {
            warnings += warning(warnings, "subscription-imported-disabled", fieldName = "appSettings.smartShelfSyncMetadata")
            drafts += ImportedSubscriptionDraft("hikari-subscriptions", "Hikari 订阅草稿", "disabled", "[]", "{}")
        }
        return smart to drafts
    }

    private fun warnSecrets(payload: JsonObject, warnings: MutableList<ImportWarning>) {
        fun visit(element: JsonElement, path: String) {
            when (element) {
                is JsonObject -> element.forEach { (name, value) ->
                    val childPath = if (path.isEmpty()) name else "$path.$name"
                    if (name.normalizedFieldName() in EXCLUDED_HIKARI_FIELD_NAMES && value.isNonEmpty()) {
                        warnings += warning(warnings, "credential-field-skipped", fieldName = childPath)
                    } else {
                        visit(value, childPath)
                    }
                }
                is JsonArray -> element.forEachIndexed { index, value -> visit(value, "$path[$index]") }
                else -> Unit
            }
        }
        visit(payload, "payload")
    }

    private fun legacyIdentity(aid: String): BookIdentity? {
        val value = aid.trim().takeIf { it.isNotEmpty() } ?: return null
        val (sourceId, remoteBookId) = when {
            value.startsWith("esj:") && value.length > 4 -> "org.tsuyomi.esjzone" to value.removePrefix("esj:")
            value.startsWith("yamibo:") && value.length > 7 -> "org.tsuyomi.yamibo" to value.removePrefix("yamibo:")
            ':' !in value -> "org.tsuyomi.wenku8" to value
            else -> return null
        }
        if (remoteBookId.codePointCount(0, remoteBookId.length) !in 1..1024) return null
        return BookIdentity(sourceId, remoteBookId)
    }

    private fun legacySourceId(value: String): String? = when (value.trim().lowercase()) {
        "wenku8", "org.tsuyomi.wenku8" -> "org.tsuyomi.wenku8"
        "esj", "esjzone", "org.tsuyomi.esjzone" -> "org.tsuyomi.esjzone"
        "yamibo", "org.tsuyomi.yamibo" -> "org.tsuyomi.yamibo"
        else -> null
    }

    private fun normalizeStatus(value: String?): String = when (value?.lowercase()) {
        "ongoing", "completed", "hiatus", "cancelled" -> value.lowercase()
        else -> "unknown"
    }

    private fun canonicalPlanDigestInput(plan: ImportPlan): ByteArray = buildString {
        append(plan.kind.name).append('\n').append(plan.sourceCreatedAt).append('\n')
        plan.books.forEach { append(it.identity.sourceId).append('\u0000').append(it.identity.remoteBookId).append('\u0000').append(it.updatedAt).append('\n') }
        plan.shelves.sortedBy { it.id }.forEach { append(it.id).append('\u0000').append(it.parentId.orEmpty()).append('\n') }
        plan.warnings.forEach { append(it.safeCode).append('\u0000').append(it.safeRecordRef.orEmpty()).append('\u0000').append(it.fieldName.orEmpty()).append('\n') }
    }.toByteArray(Charsets.UTF_8)

    private fun warning(
        current: List<ImportWarning>,
        code: String,
        ref: String? = null,
        fieldName: String? = null,
        severity: ImportSeverity = ImportSeverity.WARNING,
    ): ImportWarning {
        if (current.size >= MAX_HIKARI_WARNINGS) throw WarningLimitExceeded
        return ImportWarning(current.size, code, ref, fieldName, severity)
    }
}

internal const val MAX_HIKARI_BOOKS = 20_000
internal const val MAX_HIKARI_FOLDERS = 5_000
internal const val MAX_HIKARI_PROGRESS = 20_000
internal const val MAX_HIKARI_SEARCH_HISTORY = 10_000
internal const val MAX_HIKARI_BROWSING_HISTORY = 20_000
internal const val MAX_HIKARI_SMART_RECORDS = 128
internal const val MAX_HIKARI_SUBSCRIPTION_RECORDS = 1_000
internal const val MAX_HIKARI_RECORDS = 50_000
internal const val MAX_HIKARI_WARNINGS = 10_000
private const val MAX_HIKARI_LOCATOR_JSON_LENGTH = 2_048
private const val MAX_HIKARI_LOCATOR_INDEX = 1_000_000_000

private object WarningLimitExceeded : RuntimeException()

private val CHAPTER_LOCATOR_FIELDS = setOf("v", "kind", "chapterId", "paragraphIndex", "characterOffset", "chunkProgress", "fallbackProgress")
private val YAMIBO_LOCATOR_FIELDS = setOf("v", "kind", "threadId", "physicalPage", "postId", "floorNumber", "blockProgress", "fallbackProgress")
private val EXCLUDED_HIKARI_FIELD_NAMES = setOf(
    "account", "assistedhtmlcache", "cache", "cookie", "cookies", "credential", "daybgimage", "devicerefreshstate",
    "esjcookie", "fontfilepath", "fontpath", "nightbgimage", "password", "readerpredictivepreloadstats",
    "readerttsengine", "readerttsvoice", "readertextfamily", "readertextstylefilepath", "refreshstate",
    "screenrefreshstate", "smartshelfsyncmetadata", "smartsubscriptionaddstosourceshelf", "smartsubscriptionminsyncintervalseconds",
    "sourcecache", "sourcelocalhiddenaids", "sourcesyncconfigs", "sourcetagusecounts", "textfamily", "textstylefilepath",
    "token", "ttsengine", "ttsvoice", "userinfo", "webview", "webviewcookies", "webviewstate", "webviewstorage",
    "wenku8userinfo", "wenku8useragent", "yamibocookie", "yamiboownercatalogue", "yamiboownercataloguefailures", "yamiboownercataloguekeys",
)

private fun JsonObject?.recordCount(name: String): Int = when (val value = this?.get(name)) {
    is JsonArray -> value.size
    is JsonObject -> value.size
    null -> 0
    else -> 1
}

private fun JsonObject.strictInt(name: String): Int? = primitive(name)?.takeUnless { it.isString }?.intOrNull
private fun JsonObject.optionalStrictInt(name: String): Int? = if (name in this) strictInt(name) else null
private fun JsonObject.optionalStrictDouble(name: String): Double? =
    if (name in this) primitive(name)?.takeUnless { it.isString }?.doubleOrNull else null
private fun JsonObject.optionalIdentifier(name: String): String? = if (name in this) string(name)?.takeIf(::isHikariIdentifier) else null
private fun isHikariIdentifier(value: String): Boolean =
    value.length <= 256 && value.trim().isNotEmpty() && !value.contains("://") && value.none { it < ' ' || it == '\u007f' }
private fun String.normalizedFieldName(): String = lowercase().filter(Char::isLetterOrDigit)

private fun JsonObject.bool(name: String): Boolean? = primitive(name)?.booleanOrNull
private fun JsonObject.firstString(vararg names: String): String? = names.firstNotNullOfOrNull(::string)
private fun JsonObject.stringListLenient(
    name: String,
    warnings: MutableList<ImportWarning>,
    field: String,
    maxItems: Int,
    maxCodePoints: Int,
): List<String> {
    val value = this[name] ?: return emptyList()
    val array = value as? JsonArray
    if (array == null) {
        if (warnings.size >= MAX_HIKARI_WARNINGS) throw WarningLimitExceeded
        warnings += ImportWarning(warnings.size, "invalid-tag-list", fieldName = field)
        return emptyList()
    }
    val valid = array.mapNotNull {
        (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { text ->
            text.isNotEmpty() && text.codePointCount(0, text.length) <= maxCodePoints
        }
    }.distinct()
    if (valid.size != array.size || valid.size > maxItems) {
        if (warnings.size >= MAX_HIKARI_WARNINGS) throw WarningLimitExceeded
        warnings += ImportWarning(warnings.size, "invalid-tag-list", fieldName = field)
    }
    return valid.take(maxItems)
}

private fun String?.portableUriOrNull(
    warnings: MutableList<ImportWarning>,
    field: String,
): String? {
    this ?: return null
    val valid = runCatching { URI(this) }.getOrNull()?.isAbsolute == true
    if (valid) return this
    if (warnings.size >= MAX_HIKARI_WARNINGS) throw WarningLimitExceeded
    warnings += ImportWarning(warnings.size, "invalid-book-uri", fieldName = field)
    return null
}
private fun JsonElement.isNonEmpty(): Boolean = when (this) {
    is JsonObject -> isNotEmpty()
    is JsonArray -> isNotEmpty()
    is JsonPrimitive -> contentOrNull?.isNotBlank() == true
    else -> false
}
private fun BookIdentity.safeRef(): String = "$sourceId:$remoteBookId"
private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
