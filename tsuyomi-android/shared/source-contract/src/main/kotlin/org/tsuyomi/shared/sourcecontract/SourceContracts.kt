/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.sourcecontract

import java.net.URI
import java.time.LocalDate
import java.util.Locale
import org.tsuyomi.shared.model.BookIdentity

private val SOURCE_ID = Regex("^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+$")
private val SHA_256 = Regex("^[a-f0-9]{64}$")
private val SOURCE_CALENDAR_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
private const val MAX_UPDATE_CHAPTERS = 20_000
private const val MAX_UPDATE_ANCHOR_LENGTH = 128
private val UPDATE_ANCHOR = Regex("^[A-Za-z0-9._:-]{1,$MAX_UPDATE_ANCHOR_LENGTH}$")
private val UPDATE_REASON = Regex("^[a-z][a-z0-9._-]{0,127}$")

@JvmInline
value class SourceId(val value: String) {
    init {
        require(value.length <= 128 && SOURCE_ID.matches(value)) { "Invalid source ID" }
    }

    override fun toString(): String = value
}

@JvmInline
value class HttpsOrigin(val value: String) {
    init {
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri != null && uri.scheme.equals("https", true)) { "Origin must use HTTPS" }
        require(uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "Invalid HTTPS origin" }
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") { "Origin cannot contain a path" }
        require(!uri.host.isNullOrBlank()) { "Origin requires a host" }
        require(uri.port in -1..65535) { "Invalid origin port" }
    }

    val canonical: String
        get() {
            val uri = URI(value)
            val host = uri.host.lowercase(Locale.ROOT)
            return if (uri.port == -1 || uri.port == 443) "https://$host" else "https://$host:${uri.port}"
        }

    override fun toString(): String = canonical
}

enum class DecodeMode(val wireValue: String) {
    AUTO("auto"),
    UTF8("utf-8"),
    GB18030("gb18030"),
    BIG5_HKSCS("big5-hkscs"),
}

enum class NetworkMethod { GET, HEAD, POST }

enum class NetworkCacheMode(val wireValue: String) {
    DEFAULT("default"),
    NETWORK_ONLY("network-only"),
    VALIDATE("validate"),
    OFFLINE_ONLY("offline-only"),
}

/** Cookie capability modes declared by a verified source manifest. */
enum class SourceCookieMode {
    NONE,
    SOURCE_SCOPED,
}

data class SourceNetworkRequest(
    val url: String,
    val method: NetworkMethod,
    val headers: Map<String, String> = emptyMap(),
    val form: Map<String, String>? = null,
    val utf8Body: String? = null,
    val decode: DecodeMode = DecodeMode.AUTO,
    val cache: NetworkCacheMode = NetworkCacheMode.DEFAULT,
    val semanticCacheKey: String? = null,
    val referrerUrl: String? = null,
) {
    init {
        require(url.length in 1..4096) { "Request URL is invalid" }
        require(headers.size <= 32) { "Too many request headers" }
        require(form == null || utf8Body == null) { "Request cannot contain form and UTF-8 body" }
        require(method == NetworkMethod.POST || form == null && utf8Body == null) { "Only POST can contain a body" }
        require(method != NetworkMethod.POST || cache == NetworkCacheMode.NETWORK_ONLY) { "POST must bypass cache" }
        semanticCacheKey?.let { require(it.matches(Regex("^[A-Za-z0-9._:-]{1,160}$"))) { "Invalid semantic cache key" } }
    }
}

enum class NetworkCacheState(val wireValue: String) {
    FRESH("fresh"),
    VALIDATED("validated"),
    STALE_OFFLINE("stale-offline"),
    MISS("miss"),
    BYPASSED("bypassed"),
}

data class SourceNetworkResponse(
    val status: Int,
    val finalUrl: String,
    val headers: Map<String, String>,
    val text: String?,
    val bytes: ByteArray?,
    val decodeUsed: DecodeMode,
    val cacheState: NetworkCacheState,
    val diagnosticId: String,
) {
    init {
        require(status in 100..599) { "Invalid HTTP status" }
        require((text == null) xor (bytes == null)) { "Response must contain exactly one body representation" }
        require(diagnosticId.matches(Regex("^[A-Za-z0-9_-]{8,128}$"))) { "Invalid diagnostic ID" }
    }
}

enum class SourceErrorCode {
    NETWORK_TIMEOUT,
    NETWORK_OFFLINE,
    NETWORK_REDIRECT_DISALLOWED,
    NETWORK_RESPONSE_TOO_LARGE,
    ORIGIN_NOT_GRANTED,
    SESSION_REQUIRED,
    VERIFICATION_REQUIRED,
    MALFORMED_SOURCE_RESPONSE,
    EMPTY_SOURCE_RESPONSE,
    EXTENSION_RUNTIME_FAILURE,
    EXTENSION_TIMEOUT,
    EXTENSION_CANCELLED,
}

data class SourceDiagnostic(
    val correlationId: String,
    val stage: String,
    val safeCode: String,
    val status: Int? = null,
    val origin: String? = null,
    val redirectCount: Int = 0,
    val decode: DecodeMode? = null,
    val cacheState: NetworkCacheState? = null,
) {
    init {
        require(correlationId.length in 8..128) { "Invalid correlation ID" }
        require(stage.length in 1..64 && safeCode.length in 1..128) { "Invalid diagnostic" }
        require(redirectCount in 0..5) { "Invalid redirect count" }
    }
}

class SourceException(
    val code: SourceErrorCode,
    val diagnostic: SourceDiagnostic,
) : Exception(code.name)

data class SourceBookSummary(
    val identity: BookIdentity,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val canonicalUrl: String,
    val remoteTargetId: String? = null,
) {
    init {
        require(title.codePointCount(0, title.length) in 1..512) { "Invalid book title" }
        author?.let { require(it.codePointCount(0, it.length) in 1..256) { "Invalid author" } }
        remoteTargetId?.let {
            require(it.isNotBlank() && it.length <= 128) { "Invalid remote target ID" }
        }
    }
}

data class SourceHomeFilterOption(
    val value: String,
    val label: String,
) {
    init {
        require(value.matches(Regex("^[A-Za-z0-9._-]{1,64}$"))) { "Invalid home filter option" }
        require(label.codePointCount(0, label.length) in 1..128) { "Invalid home filter label" }
    }
}

data class SourceHomeFilter(
    val id: String,
    val label: String,
    val options: List<SourceHomeFilterOption>,
) {
    init {
        require(id.matches(Regex("^[A-Za-z0-9._-]{1,64}$"))) { "Invalid home filter ID" }
        require(label.codePointCount(0, label.length) in 1..128) { "Invalid home filter label" }
        require(options.isNotEmpty() && options.size <= 32) { "Invalid home filter options" }
        require(options.map { it.value }.distinct().size == options.size) { "Duplicate home filter option" }
    }
}

data class SourceHomeFeature(
    val id: String,
    val title: String,
    val supportingText: String?,
    val selectedFilters: Map<String, String>,
) {
    init {
        require(id.matches(Regex("^[A-Za-z0-9._-]{1,64}$"))) { "Invalid home feature ID" }
        require(title.codePointCount(0, title.length) in 1..256) { "Invalid home feature title" }
        supportingText?.let {
            require(it.codePointCount(0, it.length) in 1..256) { "Invalid home feature supporting text" }
        }
        require(selectedFilters.isNotEmpty() && selectedFilters.size <= 16) { "Invalid home feature selection" }
        require(selectedFilters.all { (key, value) ->
            key.matches(Regex("^[A-Za-z0-9._-]{1,64}$")) &&
                value.matches(Regex("^[A-Za-z0-9._-]{1,64}$"))
        }) { "Invalid home feature selection" }
    }
}

data class SourceHomeSection(
    val id: String,
    val title: String,
    val items: List<SourceBookSummary>,
) {
    init {
        require(id.matches(Regex("^[A-Za-z0-9._-]{1,64}$"))) { "Invalid home section ID" }
        require(title.codePointCount(0, title.length) in 1..256) { "Invalid home section title" }
        require(items.isNotEmpty() && items.size <= 100) { "Invalid home section items" }
    }
}

data class SourceHomePage(
    val title: String,
    val schemaVersion: Int,
    val filters: List<SourceHomeFilter>,
    val selectedFilters: Map<String, String>,
    val sections: List<SourceHomeSection>,
    val features: List<SourceHomeFeature> = emptyList(),
    val nextCursor: String?,
    val complete: Boolean,
) {
    init {
        require(title.codePointCount(0, title.length) in 1..256) { "Invalid source home title" }
        require(schemaVersion == 1) { "Unsupported source home schema version" }
        require(filters.size <= 16 && filters.map { it.id }.distinct().size == filters.size) { "Invalid source home filters" }
        require(selectedFilters.size <= filters.size) { "Invalid selected home filters" }
        selectedFilters.forEach { (id, value) ->
            val filter = filters.singleOrNull { it.id == id } ?: error("Unknown selected home filter")
            require(filter.options.any { it.value == value }) { "Invalid selected home filter option" }
        }
        require(sections.isNotEmpty() && sections.size <= 16) { "Invalid source home sections" }
        require(sections.map { it.id }.distinct().size == sections.size) { "Duplicate source home section" }
        require(features.size <= 4 && features.map { it.id }.distinct().size == features.size) {
            "Invalid source home features"
        }
        require(sections.sumOf { it.items.size } <= 100) { "Source home page is too large" }
        require(nextCursor == null || nextCursor.matches(Regex("^[A-Za-z0-9._-]{1,128}$"))) { "Invalid source home cursor" }
        require(complete || nextCursor != null) { "Incomplete source home page requires a cursor" }
    }
}

data class RemoteLibraryPage(
    val items: List<SourceBookSummary>,
    val nextCursor: String?,
    val complete: Boolean,
) {
    init {
        require(items.size <= 100) { "Remote page is too large" }
        require(nextCursor == null || nextCursor.isNotBlank()) { "Invalid remote cursor" }
        require(complete || nextCursor != null) { "Incomplete page requires a cursor" }
    }
}

enum class RemoteLibraryAddOutcome { APPLIED, ALREADY_PRESENT }

data class RemoteLibraryAddResult(
    val identity: BookIdentity,
    val outcome: RemoteLibraryAddOutcome,
)

enum class RemoteLibraryRemoveOutcome { APPLIED, ALREADY_ABSENT }

data class RemoteLibraryRemoveResult(
    val identity: BookIdentity,
    val outcome: RemoteLibraryRemoveOutcome,
)

enum class RemoteLibraryMoveOutcome { APPLIED, ALREADY_AT_TARGET }

data class RemoteLibraryMoveResult(
    val identity: BookIdentity,
    val targetId: String,
    val outcome: RemoteLibraryMoveOutcome,
)

data class RemoteTarget(
    val targetId: String,
    val displayName: String,
    val parentId: String? = null,
    val kind: String = "folder",
) {
    init {
        require(targetId.isNotBlank() && targetId.length <= 128) { "Invalid targetId" }
        require(displayName.isNotBlank() && displayName.length <= 128) { "Invalid displayName" }
    }
}

data class RemoteLibraryTargetsResult(
    val sourceId: String,
    val targets: List<RemoteTarget>,
)

data class SourceBookDetail(
    val summary: SourceBookSummary,
    val description: String?,
    val tags: List<String>,
    val status: String?,
    val lastUpdatedDate: String? = null,
) {
    init {
        require(tags.size <= 128 && tags.distinct() == tags) { "Invalid tags" }
        description?.let { require(it.codePointCount(0, it.length) <= 20_000) { "Description is too long" } }
        lastUpdatedDate?.let { date ->
            require(SOURCE_CALENDAR_DATE.matches(date) && runCatching { LocalDate.parse(date) }.isSuccess) {
                "Invalid source calendar date"
            }
        }
    }
}

data class SourceChapter(
    val chapterId: String,
    val title: String,
    val url: String,
    val volumeTitle: String? = null,
) {
    init {
        require(chapterId.codePointCount(0, chapterId.length) in 1..256) { "Invalid chapter ID" }
        require(title.codePointCount(0, title.length) in 1..512) { "Invalid chapter title" }
        volumeTitle?.let {
            require(it.isNotBlank() && it.codePointCount(0, it.length) <= 512) { "Invalid volume title" }
        }
    }
}

data class SourceDirectory(
    val bookIdentity: BookIdentity,
    val chapters: List<SourceChapter>,
) {
    init {
        require(chapters.isNotEmpty()) { "Directory cannot be empty" }
        require(chapters.map { it.chapterId }.distinct().size == chapters.size) { "Duplicate chapter identity" }
    }
}

data class SourceUpdateChapter(
    val chapterId: String,
    val title: String,
) {
    init {
        require(chapterId.codePointCount(0, chapterId.length) in 1..256) { "Invalid update chapter ID" }
        require(title.codePointCount(0, title.length) in 1..512) { "Invalid update chapter title" }
    }
}

enum class SourceUpdateOutcome {
    UNCHANGED,
    UPDATED,
    UNAVAILABLE,
    FAILED,
}

/**
 * Host-admitted, normalized update evidence from one verified source package.
 *
 * Anchors are opaque, bounded adapter-owned values. They must prove the complete prior ordered
 * chapter prefix; callers persist no source URL, HTML, or chapter text in this result.
 */
data class SourceUpdateProbeResult(
    val identity: BookIdentity,
    val sourceVersion: String,
    val packageSha256: String,
    val checkedAt: Long,
    val outcome: SourceUpdateOutcome,
    val previousAnchor: String?,
    val anchor: String?,
    val chapters: List<SourceUpdateChapter>,
    val newChapterIds: List<String>,
    val lastUpdatedDate: String?,
    val reason: String?,
) {
    init {
        require(sourceVersion.codePointCount(0, sourceVersion.length) in 1..128) { "Invalid source version" }
        require(SHA_256.matches(packageSha256)) { "Invalid source package digest" }
        require(checkedAt >= 0) { "Invalid update checked time" }
        previousAnchor?.let { require(UPDATE_ANCHOR.matches(it)) { "Invalid previous update anchor" } }
        anchor?.let { require(UPDATE_ANCHOR.matches(it)) { "Invalid update anchor" } }
        require(chapters.size in 0..MAX_UPDATE_CHAPTERS) { "Invalid update chapter count" }
        val chapterIds = chapters.map(SourceUpdateChapter::chapterId)
        val uniqueChapterIds = chapterIds.toHashSet()
        require(uniqueChapterIds.size == chapters.size) { "Duplicate update chapter identity" }
        val uniqueNewChapterIds = newChapterIds.toHashSet()
        require(newChapterIds.size <= chapters.size && uniqueNewChapterIds.size == newChapterIds.size) {
            "Invalid update chapter delta"
        }
        require(newChapterIds.all { it in uniqueChapterIds }) {
            "Unknown update chapter delta"
        }
        lastUpdatedDate?.let { date ->
            require(SOURCE_CALENDAR_DATE.matches(date) && runCatching { LocalDate.parse(date) }.isSuccess) {
                "Invalid source update calendar date"
            }
        }
        reason?.let { require(UPDATE_REASON.matches(it)) { "Invalid update reason" } }
        when (outcome) {
            SourceUpdateOutcome.UNCHANGED -> {
                require(anchor != null && chapters.isNotEmpty() && newChapterIds.isEmpty() && reason == null) {
                    "Invalid unchanged update result"
                }
            }
            SourceUpdateOutcome.UPDATED -> {
                require(previousAnchor != null && anchor != null && chapters.isNotEmpty() && newChapterIds.isNotEmpty() && reason == null) {
                    "Invalid updated update result"
                }
            }
            SourceUpdateOutcome.UNAVAILABLE,
            SourceUpdateOutcome.FAILED -> require(anchor == null && chapters.isEmpty() && newChapterIds.isEmpty() && lastUpdatedDate == null && reason != null) {
                "Invalid unavailable update result"
            }
        }
    }
}

sealed interface ReaderBlock {
    val blockId: String

    data class Paragraph(
        override val blockId: String,
        val text: String,
    ) : ReaderBlock {
        init {
            requireValidBlockId(blockId)
            require(text.isNotBlank() && text.codePointCount(0, text.length) <= 100_000) { "Invalid paragraph" }
        }
    }

    data class Heading(
        override val blockId: String,
        val text: String,
        val level: Int,
    ) : ReaderBlock {
        init {
            requireValidBlockId(blockId)
            require(text.isNotBlank() && level in 1..6) { "Invalid heading" }
        }
    }

    data class Image(
        override val blockId: String,
        val url: String,
        val altText: String?,
        val width: Int?,
        val height: Int?,
    ) : ReaderBlock {
        init {
            requireValidBlockId(blockId)
            require(url.length in 1..4096) { "Invalid image URL" }
            require(width == null || width > 0)
            require(height == null || height > 0)
        }
    }
}

data class ReaderDocument(
    val sourceId: String,
    val remoteBookId: String,
    val contentId: String,
    val revision: String?,
    val title: String,
    val blocks: List<ReaderBlock>,
) {
    init {
        SourceId(sourceId)
        BookIdentity(sourceId, remoteBookId)
        require(contentId.codePointCount(0, contentId.length) in 1..256) { "Invalid content ID" }
        revision?.let { require(it.length <= 256) { "Invalid document revision" } }
        require(title.codePointCount(0, title.length) in 1..512) { "Invalid document title" }
        require(blocks.isNotEmpty() && blocks.size <= 20_000) { "Invalid document blocks" }
        require(blocks.map { it.blockId }.distinct().size == blocks.size) { "Duplicate block identity" }
    }

    val fingerprint: String?
        get() = revision?.takeIf(SHA_256::matches)
}

private fun requireValidBlockId(blockId: String) {
    require(blockId.codePointCount(0, blockId.length) in 1..1024) { "Invalid block ID" }
}
