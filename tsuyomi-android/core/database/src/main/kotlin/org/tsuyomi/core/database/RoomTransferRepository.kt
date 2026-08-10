/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.withTransaction
import java.time.Instant
import java.util.Locale
import org.tsuyomi.core.database.room.BrowsingHistoryEntity
import org.tsuyomi.core.database.room.CollectionEntity
import org.tsuyomi.core.database.room.ImportSessionEntity
import org.tsuyomi.core.database.room.ImportWarningEntity
import org.tsuyomi.core.database.room.ManualCollectionMembershipEntity
import org.tsuyomi.core.database.room.SearchHistoryEntity
import org.tsuyomi.core.database.room.SmartRuleEntity
import org.tsuyomi.core.database.room.SubscriptionDraftEntity
import org.tsuyomi.shared.backup.ImportKind
import org.tsuyomi.shared.backup.ImportPlan
import org.tsuyomi.shared.backup.ImportSeverity
import org.tsuyomi.shared.backup.ImportWarning
import org.tsuyomi.shared.backup.ImportSummary
import org.tsuyomi.shared.backup.TransferBook
import org.tsuyomi.shared.backup.TransferProgress
import org.tsuyomi.shared.backup.TransferShelf
import org.tsuyomi.shared.backup.TransferSnapshot
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.smartshelf.SmartRuleCodec

enum class ImportSessionStatus {
    PREPARED,
    ROOM_APPLIED,
    PREFERENCES_APPLIED,
    COMPLETED,
    ABORTED,
    ABORTED_CLEANUP_PENDING,
}

data class ImportSession(
    val id: String,
    val kind: ImportKind,
    val planDigest: String,
    val normalizedPlanPath: String,
    val status: ImportSessionStatus,
    val sourceCreatedAt: Instant,
    val startedAt: Instant,
    val completedAt: Instant?,
    val preferencePatchJson: String,
    val summaryJson: String?,
)

class RoomTransferRepository(private val database: TsuyomiDatabase) {
    private val dao = database.libraryDao()
    private val library = RoomLibraryRepository(database)

    suspend fun withDatabaseConflicts(plan: ImportPlan): ImportPlan = database.withTransaction {
        val conflicts = mutableListOf<ImportWarning>()
        val maximumAdditional = (MAX_IMPORT_WARNINGS - plan.warnings.size).coerceAtLeast(0)
        fun conflict(safeCode: String, safeRecordRef: String, fieldName: String) {
            if (conflicts.size >= maximumAdditional) return
            conflicts += ImportWarning(
                ordinal = plan.warnings.size + conflicts.size,
                safeCode = safeCode,
                safeRecordRef = safeRecordRef,
                fieldName = fieldName,
                severity = ImportSeverity.CONFLICT,
            )
        }

        plan.shelves.forEach { incoming ->
            val existing = dao.collection(incoming.id) ?: return@forEach
            if (existing.kind != CollectionKind.MANUAL || existing.title != incoming.name ||
                existing.parentCollectionId != incoming.parentId || existing.displayOrder != incoming.position.toLong()
            ) {
                conflict("existing-shelf-retained", incoming.id, "shelf")
            }
        }
        plan.smartCollections.forEach { incoming ->
            if (dao.collection(incoming.collectionId) != null) {
                conflict("existing-smart-collection-retained", incoming.collectionId, "smartCollection")
            }
        }
        plan.subscriptionDrafts.forEach { incoming ->
            if (dao.collection(incoming.collectionId) != null) {
                conflict("existing-subscription-draft-retained", incoming.collectionId, "subscriptionDraft")
            }
        }
        plan.books.forEach { incoming ->
            val safeRef = "${incoming.identity.sourceId}:${incoming.identity.remoteBookId}"
            val existingBook = library.book(incoming.identity)
            val metadataAccepted = existingBook == null || incoming.updatedAt > existingBook.metadataUpdatedAt
            if (existingBook != null && !metadataAccepted && incoming.metadataDiffersFrom(existingBook)) {
                conflict("existing-book-metadata-retained", safeRef, "metadata")
            }
            val existingEntry = dao.libraryEntry(incoming.identity.sourceId, incoming.identity.remoteBookId)
            val incomingRating = incoming.rating?.takeIf { it > 0.0 }?.toInt()?.coerceIn(1, 5)
            if (incomingRating != null && existingEntry != null && incomingRating != existingEntry.rating && !metadataAccepted) {
                conflict("existing-rating-retained", safeRef, "rating")
            }
            val existingTags = dao.localTags(incoming.identity.sourceId, incoming.identity.remoteBookId)
                .mapNotNull { normalizedLocalTag(it.displayTag)?.first }
                .toSet()
            val incomingTags = incoming.localTags.mapNotNull { normalizedLocalTag(it)?.first }.toSet()
            if ((incomingTags - existingTags).size > (MAX_LOCAL_TAGS - existingTags.size).coerceAtLeast(0)) {
                conflict("local-tags-capacity-conflict", safeRef, "localTags")
            }
            incoming.progress?.let { progress ->
                val existingProgress = dao.progress(incoming.identity.sourceId, incoming.identity.remoteBookId)
                val existingUpdatedAt = existingProgress
                    ?.takeIf { it.isSemanticallyValid() }
                    ?.let { Instant.ofEpochSecond(it.updatedAtEpochSecond, it.updatedAtNano.toLong()) }
                if (existingUpdatedAt != null && progress.updatedAt <= existingUpdatedAt) {
                    conflict("existing-progress-retained", safeRef, "progress")
                }
            }
        }
        plan.copy(warnings = plan.warnings + conflicts)
    }

    suspend fun prepare(
        sessionId: String,
        plan: ImportPlan,
        planDigest: String,
        normalizedPlanPath: String,
        preferencePatchJson: String,
        startedAt: Instant,
    ) = database.withTransaction {
        check(dao.pendingImportSession() == null) { "Another import session is active" }
        dao.insertImportSession(
            ImportSessionEntity(
                id = sessionId,
                kind = plan.kind.name,
                planDigest = planDigest,
                normalizedPlanPath = normalizedPlanPath,
                status = ImportSessionStatus.PREPARED.name,
                sourceCreatedAtEpochSecond = plan.sourceCreatedAt.epochSecond,
                startedAtEpochSecond = startedAt.epochSecond,
                completedAtEpochSecond = null,
                preferencePatchJson = preferencePatchJson,
                summaryJson = null,
            ),
        )
        dao.insertImportWarnings(plan.warnings.map {
            ImportWarningEntity(sessionId, it.ordinal, it.safeCode, it.safeRecordRef, it.fieldName, it.severity.name)
        })
    }

    suspend fun applyRoomPlan(sessionId: String, digest: String, plan: ImportPlan) = database.withTransaction {
        val session = requireNotNull(dao.importSession(sessionId)) { "Unknown import session" }
        require(session.planDigest == digest) { "Import digest mismatch" }
        if (session.status == ImportSessionStatus.ROOM_APPLIED.name) return@withTransaction
        require(session.status == ImportSessionStatus.PREPARED.name) { "Import is not prepared" }

        val shelves = parentFirst(plan.shelves)
        shelves.forEach { shelf ->
            dao.insertCollectionIfAbsent(
                CollectionEntity(
                    shelf.id,
                    CollectionKind.MANUAL,
                    shelf.name,
                    shelf.parentId,
                    shelf.position.toLong(),
                    plan.sourceCreatedAt.epochSecond,
                    plan.sourceCreatedAt.nano,
                    plan.sourceCreatedAt.epochSecond,
                    plan.sourceCreatedAt.nano,
                ),
            )
        }
        plan.smartCollections.sortedBy { it.collectionId }.forEachIndexed { index, smart ->
            val inserted = dao.insertCollectionIfAbsent(
                CollectionEntity(
                    smart.collectionId,
                    CollectionKind.SMART,
                    smart.title,
                    null,
                    (shelves.size + index).toLong(),
                    plan.sourceCreatedAt.epochSecond,
                    plan.sourceCreatedAt.nano,
                    plan.sourceCreatedAt.epochSecond,
                    plan.sourceCreatedAt.nano,
                ),
            )
            if (inserted != -1L) {
                val rule = SmartRuleCodec.decode(smart.astJson).getOrThrow()
                SmartShelfQueryCompiler.requireWithinArgumentLimit(rule)
                dao.upsertSmartRule(SmartRuleEntity(smart.collectionId, rule.version, smart.astJson, 1))
            }
        }
        plan.subscriptionDrafts.sortedBy { it.collectionId }.forEachIndexed { index, draft ->
            val inserted = dao.insertCollectionIfAbsent(
                CollectionEntity(
                    draft.collectionId,
                    CollectionKind.SUBSCRIPTION,
                    draft.title,
                    null,
                    (shelves.size + plan.smartCollections.size + index).toLong(),
                    plan.sourceCreatedAt.epochSecond,
                    plan.sourceCreatedAt.nano,
                    plan.sourceCreatedAt.epochSecond,
                    plan.sourceCreatedAt.nano,
                ),
            )
            if (inserted != -1L) {
                dao.upsertSubscriptionDraft(
                    SubscriptionDraftEntity(
                        draft.collectionId,
                        draft.mode,
                        draft.sourceScopeJson,
                        draft.queryJson,
                        false,
                        sessionId,
                    ),
                )
            }
        }


        plan.books.forEach { incoming ->
            val existing = library.book(incoming.identity)
            val accepted = existing == null || incoming.updatedAt > existing.metadataUpdatedAt
            val book = requireNotNull(if (accepted) incoming.toLibraryBook(plan.sourceCreatedAt) else existing)
            library.saveBook(book)
            val entryInserted = dao.insertLibraryEntry(
                org.tsuyomi.core.database.room.LibraryEntryEntity(
                    incoming.identity.sourceId,
                    incoming.identity.remoteBookId,
                    (incoming.addedAt ?: plan.sourceCreatedAt).epochSecond,
                    (incoming.addedAt ?: plan.sourceCreatedAt).nano,
                    incoming.rating?.takeIf { it > 0.0 }?.toInt()?.coerceIn(1, 5),
                ),
            )
            val incomingRating = incoming.rating
            if (entryInserted == -1L && accepted && incomingRating != null && incomingRating > 0.0) {
                dao.updateRating(incoming.identity.sourceId, incoming.identity.remoteBookId, incomingRating.toInt().coerceIn(1, 5))
            }
            val mergedTags = linkedMapOf<String, String>()
            dao.localTags(incoming.identity.sourceId, incoming.identity.remoteBookId).forEach { tag ->
                normalizedLocalTag(tag.displayTag)?.let { (key, display) -> mergedTags.putIfAbsent(key, display) }
            }
            incoming.localTags.sorted().forEach { tag ->
                normalizedLocalTag(tag)?.let { (key, display) ->
                    if (key in mergedTags || mergedTags.size < MAX_LOCAL_TAGS) mergedTags.putIfAbsent(key, display)
                }
            }
            library.setLocalTags(incoming.identity, mergedTags.values)
            incoming.progress?.let { progress ->
                val existingProgress = dao.progress(incoming.identity.sourceId, incoming.identity.remoteBookId)
                val existingUpdatedAt = existingProgress
                    ?.takeIf { it.isSemanticallyValid() }
                    ?.let { Instant.ofEpochSecond(it.updatedAtEpochSecond, it.updatedAtNano.toLong()) }
                if (existingUpdatedAt == null || progress.updatedAt > existingUpdatedAt) {
                    library.saveProgress(progress.toReadingProgress(incoming.identity))
                }
            }
            incoming.shelfIds.sorted().forEach { shelfId ->
                if (dao.collection(shelfId)?.kind == CollectionKind.MANUAL) {
                    val existingMemberships = dao.manualMemberships(shelfId)
                    if (existingMemberships.none { it.sourceId == incoming.identity.sourceId && it.remoteBookId == incoming.identity.remoteBookId }) {
                        val addedAt = incoming.addedAt ?: plan.sourceCreatedAt
                        dao.insertManualMembership(
                            ManualCollectionMembershipEntity(
                                shelfId,
                                incoming.identity.sourceId,
                                incoming.identity.remoteBookId,
                                addedAt.epochSecond,
                                addedAt.nano,
                                dao.nextManualMembershipOrder(shelfId),
                            ),
                        )
                    }
                }
            }
        }

        plan.searchHistory.forEach {
            val display = it.query.trim().replace(Regex("\\s+"), " ")
            if (display.isNotEmpty()) dao.upsertSearchHistory(
                SearchHistoryEntity(it.sourceId, display.lowercase(Locale.ROOT), display, it.lastUsedAt.epochSecond, it.lastUsedAt.nano),
            )
        }
        plan.browsingHistory.forEach {
            if (dao.book(it.identity.sourceId, it.identity.remoteBookId) != null) dao.upsertBrowsingHistory(
                BrowsingHistoryEntity(it.identity.sourceId, it.identity.remoteBookId, it.lastViewedAt.epochSecond, it.lastViewedAt.nano),
            )
        }
        dao.disableAllAddWriteback()
        check(
            dao.transitionImportSession(
                sessionId,
                digest,
                ImportSessionStatus.PREPARED.name,
                ImportSessionStatus.ROOM_APPLIED.name,
                null,
                null,
            ) == 1,
        )
    }

    suspend fun markPreferencesApplied(sessionId: String, digest: String): Boolean =
        dao.transitionImportSession(sessionId, digest, ImportSessionStatus.ROOM_APPLIED.name, ImportSessionStatus.PREFERENCES_APPLIED.name, null, null) == 1

    suspend fun complete(sessionId: String, digest: String, summary: ImportSummary): Boolean =
        dao.transitionImportSession(
            sessionId,
            digest,
            ImportSessionStatus.PREFERENCES_APPLIED.name,
            ImportSessionStatus.COMPLETED.name,
            summary.completedAt.epochSecond,
            summary.toSafeJson(),
        ) == 1

    suspend fun abort(sessionId: String, digest: String, cleanupPending: Boolean): Boolean =
        dao.transitionImportSession(
            sessionId,
            digest,
            ImportSessionStatus.PREPARED.name,
            if (cleanupPending) ImportSessionStatus.ABORTED_CLEANUP_PENDING.name else ImportSessionStatus.ABORTED.name,
            null,
            null,
        ) == 1

    suspend fun markAbortCleanupComplete(sessionId: String, digest: String): Boolean =
        dao.transitionImportSession(sessionId, digest, ImportSessionStatus.ABORTED_CLEANUP_PENDING.name, ImportSessionStatus.ABORTED.name, null, null) == 1

    suspend fun pending(): ImportSession? = dao.pendingImportSession()?.toDomain()
    suspend fun latest(): ImportSession? = dao.latestImportSession()?.toDomain()

    suspend fun exportSnapshot(createdAt: Instant, readerPreferences: org.tsuyomi.shared.backup.PortableReaderPreferences?): TransferSnapshot =
        database.withTransaction {
            val books = dao.allBooks().associateBy { BookIdentity(it.sourceId, it.remoteBookId) }
            val memberships = dao.allManualMemberships().groupBy { BookIdentity(it.sourceId, it.remoteBookId) }
            val entries = dao.allLibraryEntries().mapNotNull { entry ->
                val identity = BookIdentity(entry.sourceId, entry.remoteBookId)
                val book = books[identity] ?: return@mapNotNull null
                val domain = library.book(identity) ?: return@mapNotNull null
                TransferBook(
                    identity = identity,
                    title = domain.title,
                    authors = domain.authors,
                    canonicalUrl = domain.canonicalUrl,
                    coverUrl = domain.coverUrl,
                    status = domain.status ?: "unknown",
                    remoteTags = domain.remoteTags,
                    localTags = dao.localTags(identity.sourceId, identity.remoteBookId).mapTo(sortedSetOf()) { it.displayTag },
                    shelfIds = memberships[identity].orEmpty().mapTo(sortedSetOf()) { it.collectionId },
                    rating = entry.rating?.toDouble(),
                    addedAt = Instant.ofEpochSecond(entry.addedAtEpochSecond, entry.addedAtNano.toLong()),
                    updatedAt = domain.metadataUpdatedAt,
                    progress = dao.progress(identity.sourceId, identity.remoteBookId)?.let { progress ->
                        TransferProgress(
                            chapterId = progress.contentId,
                            textAnchor = progress.textAnchorDigest,
                            characterOffset = progress.characterOffset,
                            chapterProgress = progress.chapterProgress,
                            bookProgress = progress.bookProgress,
                            updatedAt = Instant.ofEpochSecond(progress.updatedAtEpochSecond, progress.updatedAtNano.toLong()),
                        )
                    },
                )
            }
            val shelves = dao.allCollections().filter { it.kind == CollectionKind.MANUAL }.map {
                TransferShelf(it.collectionId, it.title, it.parentCollectionId, it.displayOrder.toInt())
            }
            TransferSnapshot(createdAt, entries, shelves, readerPreferences)
        }

    private fun parentFirst(shelves: List<TransferShelf>): List<TransferShelf> {
        val remaining = shelves.associateByTo(linkedMapOf()) { it.id }
        val result = mutableListOf<TransferShelf>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.values.filter { it.parentId == null || result.any { parent -> parent.id == it.parentId } }
                .sortedWith(compareBy({ it.parentId ?: "" }, { it.position }, { it.id }))
            require(ready.isNotEmpty()) { "Shelf graph is cyclic" }
            ready.forEach { result += it; remaining.remove(it.id) }
        }
        return result
    }
}

private const val MAX_LOCAL_TAGS = 64
private const val MAX_IMPORT_WARNINGS = 10_000

private fun TransferBook.metadataDiffersFrom(existing: LibraryBook): Boolean =
    title != existing.title ||
        authors != existing.authors ||
        canonicalUrl != existing.canonicalUrl ||
        coverUrl != existing.coverUrl ||
        (status != "unknown" && status != existing.status) ||
        remoteTags != existing.remoteTags


private fun normalizedLocalTag(raw: String): Pair<String, String>? {
    val display = raw.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() } ?: return null
    if (display.codePointCount(0, display.length) > 64) return null
    return display.lowercase(Locale.ROOT) to display
}

private fun TransferBook.toLibraryBook(fallbackAddedAt: Instant) = LibraryBook(
    identity = identity,
    title = title,
    addedAt = addedAt ?: fallbackAddedAt,
    metadataUpdatedAt = updatedAt,
    authors = authors,
    coverUrl = coverUrl,
    canonicalUrl = canonicalUrl,
    status = status,
    remoteTags = remoteTags,
)

private fun TransferProgress.toReadingProgress(identity: BookIdentity): ReadingProgress {
    val blockId = if (textAnchor != null || characterOffset != null) "transfer-anchor" else null
    return ReadingProgress(
        identity,
        ReaderLocator(
            document = DocumentIdentity(identity.sourceId, identity.remoteBookId, chapterId ?: "unknown"),
            blockId = blockId,
            textAnchorDigest = textAnchor,
            characterOffset = characterOffset,
            chapterProgress = chapterProgress,
            bookProgress = bookProgress,
            capturedAt = updatedAt,
        ),
    )
}

private fun ImportSessionEntity.toDomain() = ImportSession(
    id,
    ImportKind.valueOf(kind),
    planDigest,
    normalizedPlanPath,
    ImportSessionStatus.valueOf(status),
    Instant.ofEpochSecond(sourceCreatedAtEpochSecond),
    Instant.ofEpochSecond(startedAtEpochSecond),
    completedAtEpochSecond?.let(Instant::ofEpochSecond),
    preferencePatchJson,
    summaryJson,
)

private fun ImportSummary.toSafeJson(): String =
    "{\"sessionId\":\"${sessionId.replace("\\", "\\\\").replace("\"", "\\\"")}\",\"kind\":\"${kind.name}\",\"importedBooks\":$importedBooks,\"importedShelves\":$importedShelves,\"warningCount\":$warningCount,\"completedAt\":\"$completedAt\"}"
