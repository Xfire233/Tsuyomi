/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.librarydomain

import java.time.Instant
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity

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
)

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
)

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
        require(locator.document.sourceId == identity.sourceId) { "Locator source does not match book" }
        require(locator.document.remoteBookId == identity.remoteBookId) { "Locator book does not match book" }
        require(updatedAt == locator.capturedAt) { "Progress timestamp must equal locator capture time" }
    }
}

enum class ProgressWriteResult {
    APPLIED,
    KEPT_EXISTING,
}
