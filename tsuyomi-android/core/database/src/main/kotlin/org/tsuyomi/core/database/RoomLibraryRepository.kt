/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.withTransaction
import java.time.Instant
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.tsuyomi.core.database.room.BookEntity
import org.tsuyomi.core.database.room.BookIdentityRow
import org.tsuyomi.core.database.room.CollectionEntity
import org.tsuyomi.core.database.room.LibraryDao
import org.tsuyomi.core.database.room.LibraryEntryEntity
import org.tsuyomi.core.database.room.LocalBookTagEntity
import org.tsuyomi.core.database.room.ManualCollectionMembershipEntity
import org.tsuyomi.core.database.room.SmartRuleEntity
import org.tsuyomi.core.database.room.ReadingProgressEntity
import org.tsuyomi.core.database.room.RemoteLibraryReconciliationEntity
import org.tsuyomi.core.database.room.SourceAvailabilityEntity
import org.tsuyomi.core.database.room.SourceRemotePolicyEntity
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.smartshelf.SmartRule
import org.tsuyomi.shared.smartshelf.SmartRuleCodec
import org.tsuyomi.shared.model.BookIdentity

/**
 * The only public persistence boundary for the initial library schema. It exposes stable identities
 * and semantic locators, never Room entities or storage row identifiers.
 */
class RoomLibraryRepository(private val database: TsuyomiDatabase) {
    private val dao: LibraryDao = database.libraryDao()

    suspend fun saveBook(book: LibraryBook) {
        val entity = book.toEntity()
        if (dao.insertBook(entity) == -1L) {
            dao.updateBookMetadata(
                sourceId = entity.sourceId,
                remoteBookId = entity.remoteBookId,
                title = entity.title,
                authorsJson = entity.authorsJson,
                authorSortKey = entity.authorSortKey,
                coverUrl = entity.coverUrl,
                canonicalUrl = entity.canonicalUrl,
                status = entity.status,
                remoteTagsJson = entity.remoteTagsJson,
                sourceUpdateKey = entity.sourceUpdateKey,
                hasUnreadUpdate = entity.hasUnreadUpdate,
                metadataUpdatedAtEpochSecond = entity.metadataUpdatedAtEpochSecond,
                metadataUpdatedAtNano = entity.metadataUpdatedAtNano,
            )
        }
    }

    suspend fun book(identity: BookIdentity): LibraryBook? =
        dao.book(identity.sourceId, identity.remoteBookId)?.toDomain()

    suspend fun libraryEntries(): List<LibraryEntry> = entriesFor(
        dao.libraryBooks().map { BookIdentityRow(it.sourceId, it.remoteBookId) },
    )

    private suspend fun entriesFor(identities: List<BookIdentityRow>): List<LibraryEntry> = identities.mapNotNull { identity ->
        val book = dao.book(identity.sourceId, identity.remoteBookId) ?: return@mapNotNull null
        val entry = dao.libraryEntry(identity.sourceId, identity.remoteBookId) ?: return@mapNotNull null
        val availability = dao.sourceAvailability(identity.sourceId)?.available == true
        val tags = dao.localTags(identity.sourceId, identity.remoteBookId).mapTo(linkedSetOf()) { it.displayTag }
        val reconciliation = dao.latestReconciliation(identity.sourceId, identity.remoteBookId)?.state
            ?.let { runCatching { RemoteReconciliationState.valueOf(it) }.getOrNull() }
        LibraryEntry(
            book = book.toDomain(),
            libraryAddedAt = Instant.ofEpochSecond(entry.addedAtEpochSecond, entry.addedAtNano.toLong()),
            rating = entry.rating,
            progress = dao.progress(identity.sourceId, identity.remoteBookId)?.toDomainOrNull(),
            localTags = tags,
            sourceAvailable = availability,
            reconciliation = reconciliation,
        )
    }

    suspend fun addToLibrary(book: LibraryBook): Boolean = database.withTransaction {
        saveBook(book)
        dao.insertLibraryEntry(
            LibraryEntryEntity(
                sourceId = book.identity.sourceId,
                remoteBookId = book.identity.remoteBookId,
                addedAtEpochSecond = book.addedAt.epochSecond,
                addedAtNano = book.addedAt.nano,
                rating = null,
            ),
        ) != -1L
    }

    suspend fun removeFromLibrary(identity: BookIdentity): Boolean =
        dao.deleteLibraryEntry(identity.sourceId, identity.remoteBookId) != 0

    suspend fun setRating(identity: BookIdentity, rating: Int?) {
        require(rating == null || rating in 1..5)
        check(dao.updateRating(identity.sourceId, identity.remoteBookId, rating) == 1) { "Book is not in library" }
    }

    suspend fun setLocalTags(identity: BookIdentity, tags: Collection<String>) = database.withTransaction {
        check(dao.libraryEntry(identity.sourceId, identity.remoteBookId) != null) { "Book is not in library" }
        val normalized = tags.mapNotNull { raw ->
            val display = raw.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            require(display.codePointCount(0, display.length) <= 64)
            display.lowercase(Locale.ROOT) to display
        }.distinctBy { it.first }.sortedBy { it.first }
        require(normalized.size <= 64)
        dao.deleteLocalTags(identity.sourceId, identity.remoteBookId)
        dao.insertLocalTags(normalized.map { (key, display) -> LocalBookTagEntity(identity.sourceId, identity.remoteBookId, key, display) })
    }

    suspend fun setSourceAvailability(sourceId: String, version: String?, available: Boolean, generation: Long) {
        dao.upsertSourceAvailability(SourceAvailabilityEntity(sourceId, version, available, generation))
    }


    suspend fun sourceAvailability(sourceId: String): SourceAvailability? = dao.sourceAvailability(sourceId)?.let {
        SourceAvailability(it.sourceId, it.verifiedVersion, it.available, it.generation)
    }
    suspend fun sourceRemotePolicy(sourceId: String): SourceRemotePolicy? = dao.sourceRemotePolicy(sourceId)?.let {
        SourceRemotePolicy(it.sourceId, it.trustedPublisherFingerprint, it.capabilitySetFingerprint, it.approvedOrigin, it.addWritebackEnabled, it.firstImportPromptDismissed)
    }

    suspend fun mergeRemoteLibrary(
        sourceId: String,
        books: List<LibraryBook>,
        expectedVersion: String,
        expectedCapabilityFingerprint: String,
        expectedGeneration: Long,
        importedAt: Instant,
    ): Int = database.withTransaction {
        require(sourceId.isNotBlank()) { "Remote library source is required" }
        require(books.map { it.identity }.distinct().size == books.size) { "Duplicate remote library identity" }
        require(books.all { it.identity.sourceId == sourceId }) { "Remote library source mismatch" }
        suspend fun leaseValid(): Boolean {
            val availability = dao.sourceAvailability(sourceId) ?: return false
            val policy = dao.sourceRemotePolicy(sourceId) ?: return false
            return availability.available && availability.verifiedVersion == expectedVersion &&
                availability.generation == expectedGeneration &&
                policy.capabilitySetFingerprint == expectedCapabilityFingerprint
        }
        check(leaseValid()) { "Source changed before remote merge" }
        var added = 0
        books.forEach { book ->
            saveBook(book)
            if (dao.insertLibraryEntry(LibraryEntryEntity(book.identity.sourceId, book.identity.remoteBookId, importedAt.epochSecond, importedAt.nano, null)) != -1L) added++
        }
        check(leaseValid()) { "Source changed during remote merge" }
        added
    }
    suspend fun dismissFirstRemoteImportPrompt(sourceId: String, capabilityFingerprint: String): Boolean =
        dao.dismissFirstImportPrompt(sourceId, capabilityFingerprint) == 1


    suspend fun setAddWritebackEnabled(sourceId: String, capabilityFingerprint: String, enabled: Boolean): Boolean =
        dao.setAddWritebackEnabled(sourceId, capabilityFingerprint, enabled) == 1

    suspend fun saveSourceRemotePolicy(policy: SourceRemotePolicy) {
        dao.upsertSourceRemotePolicy(
            SourceRemotePolicyEntity(
                policy.sourceId,
                policy.trustedPublisherFingerprint,
                policy.capabilitySetFingerprint,
                policy.approvedOrigin,
                policy.addWritebackEnabled,
                policy.firstImportPromptDismissed,
            ),
        )
    }

    suspend fun beginRemoteAdd(
        book: LibraryBook,
        packageDigest: String,
        packageVersion: String,
        capabilitySetFingerprint: String,
        registryGeneration: Long,
        now: Instant,
    ): String = database.withTransaction {
        saveBook(book)
        dao.insertLibraryEntry(LibraryEntryEntity(book.identity.sourceId, book.identity.remoteBookId, now.epochSecond, now.nano, null))
        check(dao.activeReconciliation(book.identity.sourceId, book.identity.remoteBookId) == null) { "Remote add already active" }
        val id = UUID.randomUUID().toString()
        dao.insertReconciliation(
            RemoteLibraryReconciliationEntity(
                id, book.identity.sourceId, book.identity.remoteBookId, packageDigest, packageVersion,
                capabilitySetFingerprint, registryGeneration, RemoteReconciliationState.PENDING_USER_ACTION.name,
                now.epochSecond, now.epochSecond, null,
            ),
        )
        id
    }

    suspend fun transitionRemoteAdd(
        id: String,
        expected: RemoteReconciliationState,
        next: RemoteReconciliationState,
        now: Instant,
        diagnosticId: String? = null,
    ): Boolean {
        require(next in expected.allowedNextStates()) { "Invalid reconciliation transition: $expected -> $next" }
        return dao.transitionReconciliation(id, expected.name, next.name, now.epochSecond, diagnosticId) == 1
    }

    suspend fun collections(): List<LibraryCollection> = dao.allCollections().map { entity ->
        LibraryCollection(
            entity.collectionId,
            entity.kind,
            entity.title,
            entity.parentCollectionId,
            entity.displayOrder,
            Instant.ofEpochSecond(entity.createdAtEpochSecond, entity.createdAtNano.toLong()),
            Instant.ofEpochSecond(entity.updatedAtEpochSecond, entity.updatedAtNano.toLong()),
        )
    }
    suspend fun collectionEntries(collectionId: String, now: Instant = Instant.now()): List<LibraryEntry> {
        val collection = requireNotNull(dao.collection(collectionId)) { "Unknown collection" }
        val identities = when (collection.kind) {
            CollectionKind.MANUAL -> dao.manualCollectionIdentities(collectionId)
            CollectionKind.SMART -> {
                val rule = requireNotNull(dao.smartRule(collectionId)) { "Smart collection has no rule" }
                entriesForSmartRule(rule.astJson, now)
            }
            CollectionKind.SUBSCRIPTION -> emptyList()
        }
        return entriesFor(identities)
    }

    private suspend fun entriesForSmartRule(astJson: String, now: Instant): List<BookIdentityRow> {
        val rule = SmartRuleCodec.decode(astJson).getOrThrow()
        return dao.smartCollectionIdentities(SmartShelfQueryCompiler.compile(rule, now))
    }


    suspend fun createSmartCollection(collection: LibraryCollection, rule: SmartRule) {
        require(collection.kind == CollectionKind.SMART) { "Smart rule requires a smart collection" }
        database.withTransaction {
            val astJson = SmartRuleCodec.encode(rule)
            SmartShelfQueryCompiler.requireWithinArgumentLimit(rule)
            val parentId = collection.parentCollectionId
            require(parentId == null || dao.collection(parentId) != null) { "Unknown parent collection" }
            require(parentId == null || !wouldCreateCycle(collection.collectionId, parentId)) { "Collection hierarchy is cyclic" }
            dao.insertCollection(collection.toEntity())
            dao.upsertSmartRule(SmartRuleEntity(collection.collectionId, rule.version, astJson, 1))
        }
    }

    suspend fun renameCollection(collectionId: String, title: String, updatedAt: Instant = Instant.now()) {
        val normalized = title.trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotEmpty() && normalized.length <= 512) { "Invalid collection title" }
        check(dao.renameCollection(collectionId, normalized, updatedAt.epochSecond, updatedAt.nano) == 1)
    }

    suspend fun deleteCollection(collectionId: String): Boolean = database.withTransaction {
        val deleted = dao.collection(collectionId) ?: return@withTransaction false
        val formerParentId = deleted.parentCollectionId
        dao.reparentChildren(collectionId, null)
        check(dao.deleteCollection(collectionId) == 1)
        compactCollectionOrders(formerParentId)
        if (formerParentId != null) compactCollectionOrders(null)
        true
    }

    suspend fun createCollection(collection: LibraryCollection) {
        database.withTransaction {
            val parentId = collection.parentCollectionId
            require(parentId == null || dao.collection(parentId) != null) { "Unknown parent collection" }
            require(parentId == null || !wouldCreateCycle(collection.collectionId, parentId)) {
                "Collection hierarchy is cyclic or exceeds its depth bound"
            }
            dao.insertCollection(collection.toEntity())
        }
    }

    /** Changes only presentation hierarchy; it never changes collection membership semantics. */
    suspend fun updateCollectionPresentation(
        collectionId: String,
        parentCollectionId: String?,
        displayOrder: Long,
    ) {
        database.withTransaction {
            val collection = requireNotNull(dao.collection(collectionId)) { "Unknown collection" }
            require(parentCollectionId != collectionId) { "A collection cannot parent itself" }
            if (parentCollectionId != null) {
                require(dao.collection(parentCollectionId) != null) { "Unknown parent collection" }
                require(!wouldCreateCycle(collectionId, parentCollectionId)) { "Collection parent cycle" }
            }
            check(dao.updateCollectionPresentation(collection.collectionId, parentCollectionId, displayOrder) == 1)
        }
    }

    suspend fun addManualMembership(collectionId: String, identity: BookIdentity): Boolean = database.withTransaction {
        val collection = requireNotNull(dao.collection(collectionId)) { "Unknown collection" }
        require(collection.kind == CollectionKind.MANUAL) { "Only manual collections have stored membership" }
        val entry = requireNotNull(dao.libraryEntry(identity.sourceId, identity.remoteBookId)) { "Book is not in library" }
        dao.insertManualMembership(
            ManualCollectionMembershipEntity(
                collectionId,
                identity.sourceId,
                identity.remoteBookId,
                entry.addedAtEpochSecond,
                entry.addedAtNano,
                dao.nextManualMembershipOrder(collectionId),
            ),
        ) != -1L
    }

    suspend fun removeManualMembership(collectionId: String, identity: BookIdentity): Boolean =
        dao.deleteManualMembership(collectionId, identity.sourceId, identity.remoteBookId) != 0

    /**
     * Applies a semantic capture only when its [ReadingProgress.updatedAt] is strictly newer. A
     * timestamp tie deliberately retains the existing valid record; neither percentage nor offset
     * is used as a tie-breaker because a user may intentionally read backwards.
     */
    suspend fun saveProgress(incoming: ReadingProgress): ProgressWriteResult {
        require(incoming.isSemanticallyValid()) { "Invalid semantic progress" }
        val entity = incoming.toEntity()
        return database.withTransaction {
            when (val existing = dao.progress(entity.sourceId, entity.remoteBookId)) {
                null -> if (dao.insertProgressIfAbsent(entity) != -1L) {
                    ProgressWriteResult.APPLIED
                } else {
                    ProgressWriteResult.KEPT_EXISTING
                }

                else -> if (!existing.isSemanticallyValid()) {
                    check(dao.replaceProgress(entity) == 1)
                    ProgressWriteResult.APPLIED
                } else if (
                    dao.updateProgressIfNewer(
                        sourceId = entity.sourceId,
                        remoteBookId = entity.remoteBookId,
                        contentId = entity.contentId,
                        revision = entity.revision,
                        blockId = entity.blockId,
                        textAnchorDigest = entity.textAnchorDigest,
                        characterOffset = entity.characterOffset,
                        chapterProgress = entity.chapterProgress,
                        bookProgress = entity.bookProgress,
                        updatedAtEpochSecond = entity.updatedAtEpochSecond,
                        updatedAtNano = entity.updatedAtNano,
                    ) == 1
                ) {
                    ProgressWriteResult.APPLIED
                } else {
                    ProgressWriteResult.KEPT_EXISTING
                }
            }
        }
    }

    suspend fun progress(identity: BookIdentity): ReadingProgress? =
        dao.progress(identity.sourceId, identity.remoteBookId)?.toDomainOrNull()

    private suspend fun wouldCreateCycle(collectionId: String, prospectiveParentId: String): Boolean {
        var cursor: String? = prospectiveParentId
        var ancestorCount = 0
        while (cursor != null) {
            if (cursor == collectionId || ancestorCount >= MAX_COLLECTION_DEPTH - 1) return true
            cursor = dao.parentCollectionId(cursor)
            ancestorCount++
        }
        return false
    }

    private suspend fun compactCollectionOrders(parentCollectionId: String?) {
        dao.collectionSiblings(parentCollectionId).forEachIndexed { index, sibling ->
            if (sibling.displayOrder != index.toLong()) {
                check(dao.updateCollectionDisplayOrder(sibling.collectionId, index.toLong()) == 1)
            }
        }
    }

    private companion object {
        const val MAX_COLLECTION_DEPTH = 32
    }
}

private fun LibraryBook.toEntity(): BookEntity {
    val canonicalAuthors = canonicalStringSet(authors.ifEmpty { author?.let(::setOf).orEmpty() })
    return BookEntity(
        sourceId = identity.sourceId,
        remoteBookId = identity.remoteBookId,
        title = title,
        authorsJson = encodeStringSet(canonicalAuthors),
        authorSortKey = authorSortKey(canonicalAuthors),
        coverUrl = coverUrl,
        canonicalUrl = canonicalUrl,
        status = status,
        remoteTagsJson = encodeStringSet(canonicalStringSet(remoteTags)),
        sourceUpdateKey = sourceUpdateKey,
        hasUnreadUpdate = hasUnreadUpdate,
        addedAtEpochSecond = addedAt.epochSecond,
        addedAtNano = addedAt.nano,
        metadataUpdatedAtEpochSecond = metadataUpdatedAt.epochSecond,
        metadataUpdatedAtNano = metadataUpdatedAt.nano,
    )
}

private fun BookEntity.toDomain(): LibraryBook {
    val authors = decodeStringSet(authorsJson)
    return LibraryBook(
        identity = BookIdentity(sourceId, remoteBookId),
        title = title,
        author = authors.firstOrNull(),
        authors = authors,
        coverUrl = coverUrl,
        canonicalUrl = canonicalUrl,
        status = status,
        remoteTags = decodeStringSet(remoteTagsJson),
        sourceUpdateKey = sourceUpdateKey,
        hasUnreadUpdate = hasUnreadUpdate,
        addedAt = Instant.ofEpochSecond(addedAtEpochSecond, addedAtNano.toLong()),
        metadataUpdatedAt = Instant.ofEpochSecond(metadataUpdatedAtEpochSecond, metadataUpdatedAtNano.toLong()),
    )
}

private fun LibraryCollection.toEntity() = CollectionEntity(
    collectionId = collectionId,
    kind = kind,
    title = title,
    parentCollectionId = parentCollectionId,
    displayOrder = displayOrder,
    createdAtEpochSecond = createdAt.epochSecond,
    createdAtNano = createdAt.nano,
    updatedAtEpochSecond = updatedAt.epochSecond,
    updatedAtNano = updatedAt.nano,
)

private fun ReadingProgress.toEntity() = ReadingProgressEntity(
    sourceId = identity.sourceId,
    remoteBookId = identity.remoteBookId,
    contentId = locator.document.contentId,
    revision = locator.document.revision,
    blockId = locator.blockId,
    textAnchorDigest = locator.textAnchorDigest,
    characterOffset = locator.characterOffset,
    chapterProgress = locator.chapterProgress,
    bookProgress = locator.bookProgress,
    updatedAtEpochSecond = updatedAt.epochSecond,
    updatedAtNano = updatedAt.nano,
)


private fun ReadingProgressEntity.timestamp(): Instant =
    Instant.ofEpochSecond(updatedAtEpochSecond, updatedAtNano.toLong())

internal fun ReadingProgressEntity.isSemanticallyValid(): Boolean = toDomainOrNull() != null

private fun ReadingProgressEntity.toDomainOrNull(): ReadingProgress? = runCatching {
    val timestamp = timestamp()
    ReadingProgress(
        identity = BookIdentity(sourceId, remoteBookId),
        locator = ReaderLocator(
            document = DocumentIdentity(sourceId, remoteBookId, contentId, revision),
            blockId = blockId,
            textAnchorDigest = textAnchorDigest,
            characterOffset = characterOffset,
            chapterProgress = chapterProgress,
            bookProgress = bookProgress,
            capturedAt = timestamp,
        ),
        updatedAt = timestamp,
    )
}.getOrNull()

private fun ReadingProgress.isSemanticallyValid(): Boolean = runCatching {
    val locator = locator
    val characterOffset = locator.characterOffset
    val chapterProgress = locator.chapterProgress
    val bookProgress = locator.bookProgress
    require(locator.document.sourceId == identity.sourceId)
    require(locator.document.remoteBookId == identity.remoteBookId)
    require(updatedAt == locator.capturedAt)
    require(characterOffset == null || characterOffset >= 0)
    require(chapterProgress == null || chapterProgress.isFinite() && chapterProgress in 0.0..1.0)
    require(bookProgress == null || bookProgress.isFinite() && bookProgress in 0.0..1.0)
    require(
        (locator.blockId != null && locator.characterOffset != null) ||
            (locator.blockId != null && locator.textAnchorDigest != null) ||
            locator.chapterProgress != null ||
            locator.bookProgress != null,
    )
}.isSuccess

private fun RemoteReconciliationState.allowedNextStates(): Set<RemoteReconciliationState> = when (this) {
    RemoteReconciliationState.PENDING_USER_ACTION -> setOf(RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.CANCELLED)
    RemoteReconciliationState.IN_FLIGHT -> setOf(RemoteReconciliationState.CONFIRMED, RemoteReconciliationState.UNRESOLVED)
    RemoteReconciliationState.CONFIRMED, RemoteReconciliationState.UNRESOLVED, RemoteReconciliationState.CANCELLED -> emptySet()
}

private val storageJson = Json

private fun canonicalStringSet(values: Collection<String>): Set<String> = values.mapNotNull { raw ->
    Normalizer.normalize(raw, Normalizer.Form.NFKC).replace(Regex("\\s+"), " ").trim().takeIf(String::isNotEmpty)
}.distinct().sortedWith(::compareUnicodeScalars).toCollection(linkedSetOf())

private fun compareUnicodeScalars(left: String, right: String): Int {
    val leftPoints = left.codePoints().iterator()
    val rightPoints = right.codePoints().iterator()
    while (leftPoints.hasNext() && rightPoints.hasNext()) {
        val comparison = leftPoints.nextInt().compareTo(rightPoints.nextInt())
        if (comparison != 0) return comparison
    }
    return leftPoints.hasNext().compareTo(rightPoints.hasNext())
}

private fun encodeStringSet(values: Set<String>): String = storageJson.encodeToString(
    JsonArray.serializer(),
    JsonArray(values.map(::JsonPrimitive)),
)

private fun decodeStringSet(value: String): Set<String> = runCatching {
    storageJson.parseToJsonElement(value).let { it as JsonArray }.mapTo(linkedSetOf()) { (it as JsonPrimitive).content }
}.getOrDefault(emptySet())

private fun authorSortKey(authors: Set<String>): ByteArray? {
    if (authors.isEmpty()) return null
    val bytes = ArrayList<Byte>()
    authors.forEach { author ->
        author.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8).forEach { byte ->
            if (byte == 0.toByte()) { bytes += 0; bytes += 0xFF.toByte() } else bytes += byte
        }
        bytes += 0; bytes += 0
    }
    return bytes.toByteArray()
}
