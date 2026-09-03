/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.backup

import java.time.Instant
import org.tsuyomi.shared.model.BookIdentity

const val MAX_TRANSFER_BYTES: Int = 32 * 1024 * 1024

enum class ImportKind { TSUYOMI_TRANSFER, HIKARI_BACKUP }
enum class ImportSeverity { WARNING, CONFLICT }

data class PortableReaderPreferences(
    val flow: String? = null,
    val fontScale: Double? = null,
    val lineHeight: Double? = null,
    val theme: String? = null,
)

data class TransferProgress(
    val chapterId: String? = null,
    val textAnchor: String? = null,
    val characterOffset: Int? = null,
    val chapterProgress: Double? = null,
    val bookProgress: Double? = null,
    val updatedAt: Instant,
)

data class TransferBook(
    val identity: BookIdentity,
    val title: String,
    val authors: Set<String> = emptySet(),
    val canonicalUrl: String? = null,
    val coverUrl: String? = null,
    val status: String = "unknown",
    val remoteTags: Set<String> = emptySet(),
    val localTags: Set<String> = emptySet(),
    val shelfIds: Set<String> = emptySet(),
    val rating: Double? = null,
    val readLater: Boolean = false,
    val addedAt: Instant? = null,
    val updatedAt: Instant,
    val progress: TransferProgress? = null,
)

data class TransferShelf(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val position: Int = 0,
)

data class TransferSnapshot(
    val createdAt: Instant,
    val library: List<TransferBook>,
    val shelves: List<TransferShelf>,
    val readerPreferences: PortableReaderPreferences? = null,
)
data class ImportedSmartCollection(
    val collectionId: String,
    val title: String,
    val astJson: String,
)

data class ImportedSubscriptionDraft(
    val collectionId: String,
    val title: String,
    val mode: String,
    val sourceScopeJson: String,
    val queryJson: String,
)


data class ImportWarning(
    val ordinal: Int,
    val safeCode: String,
    val safeRecordRef: String? = null,
    val fieldName: String? = null,
    val severity: ImportSeverity = ImportSeverity.WARNING,
)

data class ImportPlan(
    val kind: ImportKind,
    val sourceCreatedAt: Instant,
    val books: List<TransferBook>,
    val shelves: List<TransferShelf>,
    val readerPreferences: PortableReaderPreferences?,
    val forceManualEInk: Boolean = false,
    val searchHistory: List<SourceSearchHistory> = emptyList(),
    val browsingHistory: List<SourceBrowsingHistory> = emptyList(),
    val warnings: List<ImportWarning> = emptyList(),
    val smartCollections: List<ImportedSmartCollection> = emptyList(),
    val subscriptionDrafts: List<ImportedSubscriptionDraft> = emptyList(),
)

data class SourceSearchHistory(val sourceId: String, val query: String, val lastUsedAt: Instant)
data class SourceBrowsingHistory(val identity: BookIdentity, val lastViewedAt: Instant)

data class ImportSummary(
    val sessionId: String,
    val kind: ImportKind,
    val importedBooks: Int,
    val importedShelves: Int,
    val warningCount: Int,
    val completedAt: Instant,
)

sealed interface ImportParseResult {
    data class Ready(val plan: ImportPlan, val canonicalDigest: String) : ImportParseResult
    data class Fatal(val safeCode: String) : ImportParseResult
}
