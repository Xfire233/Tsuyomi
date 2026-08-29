/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.database

import androidx.room.withTransaction
import java.time.Instant
import org.tsuyomi.core.database.room.BookIdentityRow
import org.tsuyomi.core.database.room.CollectionEntity
import org.tsuyomi.core.database.room.LibraryDao
import org.tsuyomi.core.database.room.ManualCollectionMembershipEntity
import org.tsuyomi.core.database.room.SmartRuleEntity
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.smartshelf.SmartRule
import org.tsuyomi.shared.smartshelf.SmartRuleCodec

internal class RoomCollectionStore(
    private val database: TsuyomiDatabase,
    private val dao: LibraryDao,
    private val catalog: RoomLibraryCatalogStore,
) {
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
        return catalog.entriesFor(identities)
    }

    suspend fun createSmartCollection(collection: LibraryCollection, rule: SmartRule) {
        require(collection.kind == CollectionKind.SMART) { "Smart rule requires a smart collection" }
        database.withTransaction {
            val astJson = SmartRuleCodec.encode(rule)
            SmartShelfQueryCompiler.requireWithinArgumentLimit(rule)
            val parentId = collection.parentCollectionId
            require(parentId == null || dao.collection(parentId) != null) { "Unknown parent collection" }
            require(parentId == null || !wouldCreateCycle(collection.collectionId, parentId)) {
                "Collection hierarchy is cyclic"
            }
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
        val entry = requireNotNull(dao.libraryEntry(identity.sourceId, identity.remoteBookId)) {
            "Book is not in library"
        }
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

    private suspend fun entriesForSmartRule(astJson: String, now: Instant): List<BookIdentityRow> {
        val rule = SmartRuleCodec.decode(astJson).getOrThrow()
        return dao.smartCollectionIdentities(SmartShelfQueryCompiler.compile(rule, now))
    }

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
