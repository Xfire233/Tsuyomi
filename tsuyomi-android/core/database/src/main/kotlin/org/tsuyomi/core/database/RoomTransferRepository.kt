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
import org.tsuyomi.shared.backup.TransferLocalPublicationMetadata
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.librarydomain.CollectionKind
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.ReadingProgress
import org.tsuyomi.shared.librarydomain.LocalPublicationMetadata
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
            val safeRef = incoming.identity.safeTransferReference()
            val existingBook = library.book(incoming.identity)
            val metadataAccepted = existingBook == null || incoming.updatedAt > existingBook.metadataUpdatedAt
            if (existingBook != null && !metadataAccepted && incoming.metadataDiffersFrom(existingBook)) {
                conflict("existing-book-metadata-retained", safeRef, "metadata")
            }
            incoming.localPublication?.let { original ->
                val current = existingBook?.localPublication ?: return@let
                if (original.format != current.format || original.originalSha256 != current.originalSha256 ||
                    original.originalSizeBytes != current.originalSizeBytes
                ) {
                    conflict("existing-local-original-retained", safeRef, "localPublication")
                }
            }
            val existingEntry = dao.libraryEntryByIdentityKey(incoming.identity.storageKey)
            val incomingRating = incoming.rating?.takeIf { it > 0.0 }?.toInt()?.coerceIn(1, 5)
            if (incomingRating != null && existingEntry != null && incomingRating != existingEntry.rating && !metadataAccepted) {
                conflict("existing-rating-retained", safeRef, "rating")
            }
            val existingTags = dao.localTagsByIdentityKey(incoming.identity.storageKey)
                .mapNotNull { normalizedLocalTag(it.displayTag)?.first }
                .toSet()
            val incomingTags = incoming.localTags.mapNotNull { normalizedLocalTag(it)?.first }.toSet()
            if ((incomingTags - existingTags).size > (MAX_LOCAL_TAGS - existingTags.size).coerceAtLeast(0)) {
                conflict("local-tags-capacity-conflict", safeRef, "localTags")
            }
            incoming.progress?.let { progress ->
                val existingProgress = dao.progressByIdentityKey(incoming.identity.storageKey)
                val existingUpdatedAt = existingProgress?.takeIf { it.isSemanticallyValid() }
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

    suspend fun applyRoomPlan(
        sessionId: String,
        digest: String,
        plan: ImportPlan,
        restoredPublications: Set<BookIdentity.Local> = emptySet(),
    ) = database.withTransaction {
        val session = requireNotNull(dao.importSession(sessionId)) { "Unknown import session" }
        require(session.planDigest == digest) { "Import digest mismatch" }
        if (session.status == ImportSessionStatus.ROOM_APPLIED.name) return@withTransaction
        require(session.status == ImportSessionStatus.PREPARED.name) { "Import is not prepared" }
        require(restoredPublications.all { restored -> plan.books.any { it.identity == restored } }) {
            "Restored original is not part of the reviewed transfer"
        }

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
            val candidate = if (accepted) incoming.toLibraryBook(plan.sourceCreatedAt) else requireNotNull(existing)
            val book = candidate.preserveOriginalDescriptorFrom(existing)
            require(incoming.localPin || incoming.shelfIds.isEmpty()) {
                "Unpinned transfer books cannot have manual collection memberships"
            }
            library.saveBook(book)
            val identity = incoming.identity
            if (identity in restoredPublications) {
                val incomingOriginal = requireNotNull(incoming.localPublication)
                val retainedOriginal = requireNotNull(book.localPublication)
                require(incomingOriginal.format == retainedOriginal.format &&
                    incomingOriginal.originalSha256 == retainedOriginal.originalSha256 &&
                    incomingOriginal.originalSizeBytes == retainedOriginal.originalSizeBytes)
                check(dao.setLocalOriginalAvailable(identity.storageKey, true) == 1)
            }
            val remote = identity as? BookIdentity.Remote
            val addedAt = incoming.addedAt ?: plan.sourceCreatedAt
            val entryInserted = dao.insertLibraryEntry(
                org.tsuyomi.core.database.room.LibraryEntryEntity(
                    identityKey = identity.storageKey,
                    sourceId = remote?.sourceId,
                    remoteBookId = remote?.remoteBookId,
                    addedAtEpochSecond = addedAt.epochSecond,
                    addedAtNano = addedAt.nano,
                    rating = incoming.rating?.takeIf { it > 0.0 }?.toInt()?.coerceIn(1, 5),
                    readLater = incoming.readLater,
                    locallyPinned = incoming.localPin,
                ),
            )
            if (entryInserted == -1L && incoming.localPin) dao.pinLibraryEntryByIdentityKey(identity.storageKey)
            val incomingRating = incoming.rating
            if (entryInserted == -1L && accepted && incomingRating != null && incomingRating > 0.0) {
                dao.updateRatingByIdentityKey(identity.storageKey, incomingRating.toInt().coerceIn(1, 5))
            }
            if (entryInserted == -1L && incoming.readLater) dao.updateReadLaterByIdentityKey(identity.storageKey, true)

            val mergedTags = linkedMapOf<String, String>()
            dao.localTagsByIdentityKey(identity.storageKey).forEach { tag ->
                normalizedLocalTag(tag.displayTag)?.let { (key, display) -> mergedTags.putIfAbsent(key, display) }
            }
            incoming.localTags.sorted().forEach { tag ->
                normalizedLocalTag(tag)?.let { (key, display) ->
                    if (key in mergedTags || mergedTags.size < MAX_LOCAL_TAGS) mergedTags.putIfAbsent(key, display)
                }
            }
            library.setLocalTags(identity, mergedTags.values)
            incoming.progress?.let { progress ->
                val existingProgress = dao.progressByIdentityKey(identity.storageKey)
                val existingUpdatedAt = existingProgress?.takeIf { it.isSemanticallyValid() }
                    ?.let { Instant.ofEpochSecond(it.updatedAtEpochSecond, it.updatedAtNano.toLong()) }
                if (existingUpdatedAt == null || progress.updatedAt > existingUpdatedAt) {
                    library.saveProgress(progress.toReadingProgress(identity))
                }
            }
            incoming.completedChapterIds.sorted().forEach { chapterId ->
                library.markChapterCompleted(identity, chapterId, plan.sourceCreatedAt)
            }
            library.addBookmarks(identity, incoming.bookmarks)
            if (incoming.localPin) {
                incoming.shelfIds.sorted().forEach { shelfId ->
                    if (dao.collection(shelfId)?.kind == CollectionKind.MANUAL) {
                        val existingMemberships = dao.manualMemberships(shelfId)
                        if (existingMemberships.none { it.identityKey == identity.storageKey }) {
                            val remoteIdentity = identity as? BookIdentity.Remote
                            dao.insertManualMembership(
                                ManualCollectionMembershipEntity(
                                    collectionId = shelfId,
                                    identityKey = identity.storageKey,
                                    sourceId = remoteIdentity?.sourceId,
                                    remoteBookId = remoteIdentity?.remoteBookId,
                                    addedAtEpochSecond = addedAt.epochSecond,
                                    addedAtNano = addedAt.nano,
                                    displayOrder = dao.nextManualMembershipOrder(shelfId),
                                ),
                            )
                        }
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
            if (dao.bookByIdentityKey(it.identity.storageKey) != null) dao.upsertBrowsingHistory(
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
            val books = dao.allBooks().associateBy { it.toIdentity() }
            val entries = dao.allLibraryEntries().associateBy { it.identityKey }
            val memberships = dao.allManualMemberships().groupBy { it.identityKey }
            val bookmarkedIdentities = dao.readerBookmarkIdentities().map { it.toIdentity() }
            val localIdentities = books.keys.filterIsInstance<BookIdentity.Local>()
            val retainedIdentities = books.keys.filter { it.storageKey in entries }
            val exportedIdentities = (retainedIdentities + bookmarkedIdentities + localIdentities).toSortedSet()
            val exportedBooks = exportedIdentities.mapNotNull { identity ->
                val domain = books[identity]?.toDomain() ?: return@mapNotNull null
                val entry = entries[identity.storageKey]
                val shelfIds = memberships[identity.storageKey].orEmpty().mapTo(sortedSetOf()) { it.collectionId }
                require(entry?.locallyPinned != false || shelfIds.isEmpty()) {
                    "Unpinned library entries cannot have manual collection memberships"
                }
                val semanticProgress = dao.progressByIdentityKey(identity.storageKey)?.toDomainOrNull(identity)
                TransferBook(
                    identity = identity,
                    title = domain.title,
                    authors = domain.authors,
                    canonicalUrl = domain.canonicalUrl,
                    coverUrl = domain.coverUrl,
                    status = domain.status ?: "unknown",
                    remoteTags = domain.remoteTags,
                    localTags = dao.localTagsByIdentityKey(identity.storageKey).mapTo(sortedSetOf()) { it.displayTag },
                    shelfIds = shelfIds,
                    rating = entry?.rating?.toDouble(),
                    readLater = entry?.readLater ?: false,
                    localPin = entry?.locallyPinned ?: false,
                    addedAt = entry?.let { Instant.ofEpochSecond(it.addedAtEpochSecond, it.addedAtNano.toLong()) } ?: domain.addedAt,
                    updatedAt = domain.metadataUpdatedAt,
                    progress = semanticProgress?.let { progress ->
                        TransferProgress(
                            chapterId = progress.locator.document.contentId,
                            textAnchor = progress.locator.textAnchorDigest,
                            characterOffset = progress.locator.characterOffset,
                            chapterProgress = progress.locator.chapterProgress,
                            bookProgress = progress.locator.bookProgress,
                            updatedAt = progress.updatedAt,
                            revision = progress.locator.document.revision,
                        )
                    },
                    completedChapterIds = dao.completedChapterIdsByIdentityKey(identity.storageKey).toSortedSet(),
                    bookmarks = library.bookmarks(identity),
                    localPublication = domain.localPublication?.let { local ->
                        TransferLocalPublicationMetadata(local.format, local.originalSha256, local.originalSizeBytes, local.txtOptions, local.coverResourceId)
                    },
                )
            }
            val shelves = dao.allCollections().filter { it.kind == CollectionKind.MANUAL }.map {
                TransferShelf(it.collectionId, it.title, it.parentCollectionId, it.displayOrder.toInt())
            }
            TransferSnapshot(createdAt, exportedBooks, shelves, readerPreferences)
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
        remoteTags != existing.remoteTags ||
        localPublication?.let { imported ->
            val stored = existing.localPublication
            stored == null || imported.format != stored.format || imported.originalSha256 != stored.originalSha256 ||
                imported.originalSizeBytes != stored.originalSizeBytes || imported.txtOptions != stored.txtOptions ||
                imported.coverResourceId != stored.coverResourceId
        } == true || (localPublication == null && existing.localPublication != null)

private fun BookIdentity.safeTransferReference(): String = when (this) {
    is BookIdentity.Remote -> "$sourceId:$remoteBookId"
    is BookIdentity.Local -> "local:$publicationId"
}


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
    localPublication = localPublication?.let { portable ->
        LocalPublicationMetadata(
            format = portable.format,
            originalSha256 = portable.originalSha256,
            originalSizeBytes = portable.originalSizeBytes,
            originalAvailable = false,
            txtOptions = portable.txtOptions,
            coverResourceId = portable.coverResourceId,
        )
    },
)

private fun LibraryBook.preserveOriginalDescriptorFrom(existing: LibraryBook?): LibraryBook {
    val incoming = localPublication ?: return this
    val current = existing?.localPublication ?: return this
    val sameOriginal = incoming.format == current.format && incoming.originalSha256 == current.originalSha256 &&
        incoming.originalSizeBytes == current.originalSizeBytes
    if (!sameOriginal) return copy(localPublication = current)
    return if (incoming.originalAvailable == current.originalAvailable) this
    else copy(localPublication = incoming.copy(originalAvailable = current.originalAvailable))
}

private fun TransferProgress.toReadingProgress(identity: BookIdentity): ReadingProgress {
    val blockId = if (textAnchor != null || characterOffset != null) "transfer-anchor" else null
    return ReadingProgress(
        identity,
        ReaderLocator(
            document = DocumentIdentity(identity, chapterId ?: "unknown", revision),
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
