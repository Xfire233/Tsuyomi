/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.librarydomain.LocalPublicationMetadata
import org.tsuyomi.shared.librarydomain.LocalTextParseOptions
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.sourcecontract.ReaderAnchorTarget
import org.tsuyomi.shared.sourcecontract.ReaderDocumentPresentation
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef

/** Metadata admitted from one private original; cover references are opaque Reader resource refs. */
data class PublicationMetadata(
    val format: LocalPublicationFormat,
    val title: String,
    val authors: List<String>,
    val language: String? = null,
    val identifier: String? = null,
    val cover: ReaderResourceRef? = null,
)

/** One canonical EPUB spine item/fixed-image sequence, or deterministic TXT segment. */
data class PublicationSection(
    val contentId: String,
    val title: String,
    val ordinal: Int,
    val linear: Boolean = true,
    val anchors: Map<String, ReaderAnchorTarget> = emptyMap(),
    val codePointStart: Long? = null,
) {
    init {
        require(contentId.isNotBlank() && contentId.length <= 1024)
        require(title.isNotBlank() && title.codePointCount(0, title.length) <= 512)
        require(ordinal >= 0)
        require(codePointStart == null || codePointStart >= 0)
    }
}

/** Nested publication navigation row; fragments are resolved to stable section/block identities. */
data class PublicationTocEntry(
    val title: String,
    val contentId: String,
    val fragment: String? = null,
    val children: List<PublicationTocEntry> = emptyList(),
)

/** Internal image payload metadata. Entry names and private paths are never exposed. */
data class PublicationResourceRecord(
    val reference: ReaderResourceRef.Local,
    val byteLength: Long,
    val role: PublicationResourceRole,
)

enum class PublicationResourceRole { COVER, CONTENT }

enum class PublicationCompatibilityLevel { SUPPORTED, DEGRADED }

enum class PublicationCompatibilitySeverity { NOTICE, DEGRADED, RESOURCE_DISABLED }

data class PublicationCompatibilityFinding(
    val code: String,
    val severity: PublicationCompatibilitySeverity,
    val message: String,
    val contentId: String? = null,
    val blockId: String? = null,
)

data class PublicationCompatibilityReport(
    val level: PublicationCompatibilityLevel,
    val findings: List<PublicationCompatibilityFinding>,
)

/** Shared, process-restorable normalization options for TXT parsing. */
data class PublicationOptions(
    val txt: LocalTextParseOptions = LocalTextParseOptions(),
)

/** Correctable chapter-heading suggestion; ordinal counts nonblank normalized paragraphs. */
data class TxtHeadingSuggestion(
    val paragraphOrdinal: Int,
    val title: String,
)

/** A bounded strict-decoding candidate shown to the owner before ambiguous text is admitted. */
data class TxtCharsetCandidate(
    val charsetName: String,
    val preview: String,
    val tableOfContentsSuggestions: List<TxtHeadingSuggestion>,
)

sealed interface PublicationParseResult {
    data class Ready(val publication: ParsedPublication) : PublicationParseResult
    data class NeedsCharsetChoice(val candidates: List<TxtCharsetCandidate>) : PublicationParseResult
}


/** Stable parser-facing failures. These are not generic corruption fallbacks. */
enum class PublicationFailureCode {
    INVALID_ORIGINAL,
    ORIGINAL_LIMIT,
    INVALID_ARCHIVE,
    INVALID_EPUB,
    UNSAFE_ARCHIVE_PATH,
    AMBIGUOUS_ARCHIVE,
    UNSUPPORTED_COMPRESSION,
    ARCHIVE_LIMIT,
    UNSUPPORTED_FORMAT,
    XML_DEPTH_LIMIT,
    XML_TEXT_LIMIT,
    XML_DTD_FORBIDDEN,
    XML_ENTITY_FORBIDDEN,
    MALFORMED_XML,
    XML_FEATURE_UNAVAILABLE,
    XML_LIMIT,
    MALFORMED_HTML,
    HTML_DEPTH_LIMIT,
    HTML_TEXT_LIMIT,
    HTML_BLOCK_LIMIT,
    MALFORMED_PACKAGE,
    MISSING_SPINE_ITEM,
    MISSING_TOC,
    MALFORMED_TOC,
    UNSAFE_RESOURCE_PATH,
    INVALID_HREF,
    MISSING_RESOURCE,
    MISSING_ANCHOR,
    DUPLICATE_ANCHOR,
    UNSUPPORTED_SPINE_CONTENT,
    PROTECTED_CONTENT,
    UNSUPPORTED_FIXED_LAYOUT,
    EXTERNAL_RESOURCE_DENIED,
    UNSUPPORTED_RESOURCE_TYPE,
    INVALID_RESOURCE,
    EMPTY_TEXT,
    INVALID_CHARSET,
    INVALID_TEXT_ENCODING,
    INVALID_TEXT_CONTENT,
    INVALID_HEADING_SELECTION,
    TEXT_LIMIT,
    SECTION_LIMIT,
    BLOCK_LIMIT,
}

class PublicationParseException(
    val code: PublicationFailureCode,
    message: String = code.name,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** Parsed catalog/index. Prose normalizes on demand; fixed sequences retain only image refs/anchors. */
class ParsedPublication internal constructor(
    val publicationId: String,
    val format: LocalPublicationFormat,
    val originalSha256: String,
    val originalSizeBytes: Long,
    val contentRevision: String,
    val options: PublicationOptions,
    val metadata: PublicationMetadata,
    val sections: List<PublicationSection>,
    val tableOfContents: List<PublicationTocEntry>,
    val resources: List<PublicationResourceRecord>,
    val compatibility: PublicationCompatibilityReport,
    val txtPreview: TxtCharsetCandidate? = null,
    val presentation: ReaderDocumentPresentation = ReaderDocumentPresentation.REFLOWABLE,
    val admissionRecord: PublicationAdmissionRecord? = null,
    private val loadSectionBlock: (String, DocumentIdentity, () -> Unit) -> org.tsuyomi.shared.sourcecontract.ReaderDocument,
    private val openResourceStream: (ReaderResourceRef.Local, () -> Unit) -> java.io.InputStream,
) {
    val localMetadata: LocalPublicationMetadata
        get() = LocalPublicationMetadata(
            format, originalSha256, originalSizeBytes, originalAvailable = true,
            txtOptions = options.txt.takeIf { format == LocalPublicationFormat.TXT },
            coverResourceId = (metadata.cover as? ReaderResourceRef.Local)?.resourceId,
        )

    /** Resolve only this canonical reading unit and inject the caller-owned document identity. */
    fun loadSection(
        contentId: String,
        identity: DocumentIdentity,
        cancelled: () -> Unit = {},
    ): org.tsuyomi.shared.sourcecontract.ReaderDocument = loadSectionBlock(contentId, identity, cancelled)

    /** Resolve an opaque Local resource against this publication's immutable private original. */
    fun openResource(
        reference: ReaderResourceRef.Local,
        cancelled: () -> Unit = {},
    ): java.io.InputStream = openResourceStream(reference, cancelled)
}
