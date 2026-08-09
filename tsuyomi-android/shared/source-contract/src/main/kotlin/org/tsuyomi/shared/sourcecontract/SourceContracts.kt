/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.sourcecontract

import java.net.URI
import java.util.Locale
import org.tsuyomi.shared.model.BookIdentity

private val SOURCE_ID = Regex("^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+$")
private val SHA_256 = Regex("^[a-f0-9]{64}$")

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
) {
    init {
        require(title.codePointCount(0, title.length) in 1..512) { "Invalid book title" }
        author?.let { require(it.codePointCount(0, it.length) in 1..256) { "Invalid author" } }
    }
}

data class SourceBookDetail(
    val summary: SourceBookSummary,
    val description: String?,
    val tags: List<String>,
    val status: String?,
) {
    init {
        require(tags.size <= 128 && tags.distinct() == tags) { "Invalid tags" }
        description?.let { require(it.codePointCount(0, it.length) <= 20_000) { "Description is too long" } }
    }
}

data class SourceChapter(
    val chapterId: String,
    val title: String,
    val url: String,
) {
    init {
        require(chapterId.codePointCount(0, chapterId.length) in 1..256) { "Invalid chapter ID" }
        require(title.codePointCount(0, title.length) in 1..512) { "Invalid chapter title" }
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
