/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import java.time.Instant
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity

data class LibraryBook(
    val identity: BookIdentity,
    val title: String,
    val addedAt: Instant,
    val metadataUpdatedAt: Instant,
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
) {
    init {
        require(collectionId.isNotBlank() && collectionId.length <= 128) { "Invalid collection ID" }
        require(title.isNotBlank() && title.length <= 512) { "Invalid collection title" }
        require(parentCollectionId == null || parentCollectionId != collectionId) {
            "A collection cannot parent itself"
        }
    }
}

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
