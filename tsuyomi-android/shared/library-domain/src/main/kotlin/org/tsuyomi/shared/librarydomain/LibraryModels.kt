/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.librarydomain

import java.time.Instant
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity


enum class LocalPublicationFormat { EPUB, TXT, MOBI, AZW3 }
data class LocalTextHeading(
    val paragraphOrdinal: Int,
    val title: String,
) {
    init {
        require(paragraphOrdinal in 0..20_000) { "TXT heading paragraph ordinal is out of bounds" }
        require(title.isNotBlank()) { "TXT heading title must not be blank" }
        require(title.codePointCount(0, title.length) in 1..512) { "TXT heading title must contain 1..512 code points" }
    }
}

data class LocalTextParseOptions(
    val charsetName: String? = null,
    val headingSelections: List<LocalTextHeading> = emptyList(),
    val paragraphsPerSection: Int = DEFAULT_PARAGRAPHS_PER_SECTION,
) {
    init {
        require(charsetName == null || charsetName in CHARSET_NAMES) { "Unsupported canonical TXT charset" }
        require(headingSelections.size <= MAX_HEADING_SELECTIONS) { "Too many TXT heading selections" }
        require(headingSelections.zipWithNext().all { (left, right) -> left.paragraphOrdinal < right.paragraphOrdinal }) {
            "TXT heading selections must be strictly ordered and unique"
        }
        require(paragraphsPerSection in 1..4096) { "TXT paragraphs per section is out of bounds" }
    }

    companion object {
        const val DEFAULT_PARAGRAPHS_PER_SECTION = 128
        const val MAX_HEADING_SELECTIONS = 20_000
        private val CHARSET_NAMES = setOf("UTF-8", "UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE", "GB18030", "Big5")
    }
}

/** Verified metadata for host-owned original bytes; availability is local, non-portable state. */
data class LocalPublicationMetadata(
    val format: LocalPublicationFormat,
    val originalSha256: String,
    val originalSizeBytes: Long,
    val originalAvailable: Boolean,
    val txtOptions: LocalTextParseOptions? = null,
    val coverResourceId: String? = null,
) {
    init {
        require(originalSha256.matches(Regex("^[0-9a-f]{64}$"))) { "Invalid original SHA-256" }
        require(originalSizeBytes > 0) { "Original size must be positive" }
        coverResourceId?.let { BookIdentity.requireCanonicalUuid(it, "coverResourceId") }
        require((format == LocalPublicationFormat.TXT) == (txtOptions != null)) {
            "TXT parse options must be present only for TXT publications"
        }
    }
}
data class LibraryBook(
    val identity: BookIdentity,
    val title: String,
    val addedAt: Instant,
    val metadataUpdatedAt: Instant,
    val author: String? = null,
    val authors: Set<String> = author?.let(::setOf) ?: emptySet(),
    val coverUrl: String? = null,
    val canonicalUrl: String? = null,
    val status: String? = null,
    val remoteTags: Set<String> = emptySet(),
    val localPublication: LocalPublicationMetadata? = null,
) {
    init {
        require((identity is BookIdentity.Local) == (localPublication != null)) {
            "Local identity and local-publication metadata must be present together"
        }
        require(identity !is BookIdentity.Local || (coverUrl == null && canonicalUrl == null)) {
            "Local publications cannot carry remote cover or canonical URLs"
        }
    }
}

enum class CollectionKind {
    MANUAL,
    SMART,
    SUBSCRIPTION,
}

data class LibraryCollection(
    val collectionId: String,
    val kind: CollectionKind,
    val title: String,
    val parentCollectionId: String?,
    val displayOrder: Long,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = createdAt,
) {
    init {
        require(collectionId.isNotBlank() && collectionId.length <= 128) { "Invalid collection ID" }
        require(title.isNotBlank() && title.length <= 512) { "Invalid collection title" }
        require(parentCollectionId == null || parentCollectionId != collectionId) {
            "A collection cannot parent itself"
        }
    }
}

data class LibraryEntry(
    val book: LibraryBook,
    val libraryAddedAt: Instant,
    val rating: Int?,
    val localTags: Set<String>,
    val readLater: Boolean = false,
    val sourceAvailable: Boolean,
    val reconciliation: RemoteReconciliationState?,
    val reconciliationOperation: String? = null,
    val progress: ReadingProgress? = null,
    /** Actual successful Reader admission, or a preserved legacy semantic progress capture. */
    val readerVisitedAt: Instant? = null,
    val localMembership: Boolean = true,
)

enum class RemoteReconciliationState {
    PENDING_USER_ACTION,
    IN_FLIGHT,
    CONFIRMED,
    UNRESOLVED,
    CANCELLED,
}

data class RemoteReconciliationRecord(
    val id: String,
    val sourceId: String,
    val remoteBookId: String,
    val state: RemoteReconciliationState,
    val operation: String,
    val targetId: String? = null,
    val targetName: String? = null,
    val diagnosticId: String? = null,
    val updatedAtEpochSecond: Long,
)

data class SourceRemotePolicy(
    val sourceId: String,
    val trustedPublisherFingerprint: String,
    val capabilitySetFingerprint: String,
    val approvedOrigin: String,
    val addWritebackEnabled: Boolean,
    val firstImportPromptDismissed: Boolean,
    val removeWritebackEnabled: Boolean = false,
    val moveWritebackEnabled: Boolean = false,
)

data class RemoteMirrorBinding(
    val sourceId: String,
    val displayName: String,
    val frozen: Boolean,
    val updatedAtEpochSecond: Long,
)

data class RemoteMirrorTargetSnapshot(
    val sourceId: String,
    val targetId: String,
    val displayName: String,
    val parentId: String? = null,
    val kind: String = "folder",
    val frozen: Boolean = false,
    val updatedAtEpochSecond: Long,
)

data class RemoteMirrorBookSnapshot(
    val book: LibraryBook,
    val targetId: String?,
) {
    init {
        require(book.identity is BookIdentity.Remote) { "Remote mirrors cannot contain local publications" }
    }
}

data class RemoteMirrorSnapshot(
    val binding: RemoteMirrorBinding,
    val targets: List<RemoteMirrorTargetSnapshot>,
    val books: List<RemoteMirrorBookSnapshot>,
)
data class SourceAvailability(
    val sourceId: String,
    val verifiedVersion: String?,
    val available: Boolean,
    val generation: Long,
)

/**
 * The durable semantic reader position. [updatedAt] and [locator.capturedAt] must be identical:
 * a capture is one logical update, not two independently mergeable clocks.
 */
data class ReadingProgress(
    val identity: BookIdentity,
    val locator: ReaderLocator,
    val updatedAt: Instant = locator.capturedAt,
) {
    init {
        require(locator.document.book == identity) { "Locator book does not match book" }
        require(updatedAt == locator.capturedAt) { "Progress timestamp must equal locator capture time" }
    }
}

enum class ProgressWriteResult {
    APPLIED,
    KEPT_EXISTING,
}
