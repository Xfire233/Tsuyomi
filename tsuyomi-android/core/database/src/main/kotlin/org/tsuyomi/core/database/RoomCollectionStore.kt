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
import org.tsuyomi.shared.librarydomain.CollectionKind
import org.tsuyomi.shared.librarydomain.LibraryCollection
import org.tsuyomi.shared.librarydomain.LibraryEntry

enum class CollectionDeletionPolicy { REPARENT_CHILDREN, DELETE_SUBTREES }

data class CollectionDeletionPlan(
    val selectedIds: Set<String>,
    val policy: CollectionDeletionPolicy,
    val folderCount: Int,
    val membershipCount: Int,
)

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
    suspend fun smartRule(collectionId: String): SmartRule? =
        dao.smartRule(collectionId)?.let { SmartRuleCodec.decode(it.astJson).getOrThrow() }

    suspend fun updateSmartCollection(collectionId: String, title: String, rule: SmartRule, now: Instant = Instant.now()) {
        val normalized = title.trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotEmpty() && normalized.length <= 512) { "Invalid collection title" }
        database.withTransaction {
            require(dao.collection(collectionId)?.kind == CollectionKind.SMART) { "Not a smart collection" }
            val astJson = SmartRuleCodec.encode(rule)
            SmartShelfQueryCompiler.requireWithinArgumentLimit(rule)
            check(dao.renameCollection(collectionId, normalized, now.epochSecond, now.nano) == 1)
            dao.upsertSmartRule(SmartRuleEntity(collectionId, rule.version, astJson, 1))
        }
    }


    suspend fun renameCollection(collectionId: String, title: String, updatedAt: Instant = Instant.now()) {
        val normalized = title.trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotEmpty() && normalized.length <= 512) { "Invalid collection title" }
        check(dao.renameCollection(collectionId, normalized, updatedAt.epochSecond, updatedAt.nano) == 1)
    }

    suspend fun previewDeletion(ids: Set<String>, policy: CollectionDeletionPolicy): CollectionDeletionPlan =
        database.withTransaction {
            val rows = dao.allCollections()
            require(ids.isNotEmpty() && rows.map { it.collectionId }.containsAll(ids)) { "Unknown collection" }
            val removed = deletionIds(rows, ids, policy)
            CollectionDeletionPlan(
                ids.toSet(), policy, removed.size,
                dao.allManualMemberships().count { it.collectionId in removed },
            )
        }

    suspend fun deleteCollections(plan: CollectionDeletionPlan): Boolean = database.withTransaction {
        val rows = dao.allCollections()
        if (!rows.map { it.collectionId }.containsAll(plan.selectedIds) || plan.selectedIds.isEmpty()) {
            return@withTransaction false
        }
        val removed = deletionIds(rows, plan.selectedIds, plan.policy)
        if (removed.size != plan.folderCount ||
            dao.allManualMemberships().count { it.collectionId in removed } != plan.membershipCount
        ) return@withTransaction false

        val byId = rows.associateBy { it.collectionId }
        val children = rows.groupBy { it.parentCollectionId }.mapValues { (_, siblings) ->
            siblings.sortedWith(compareBy<CollectionEntity> { it.displayOrder }.thenBy { it.collectionId })
        }
        if (plan.policy == CollectionDeletionPolicy.REPARENT_CHILDREN) {
            // Expand selected slots in place so children retain their relative position at the nearest surviving parent.
            fun survivingAt(parentId: String?): List<CollectionEntity> = children[parentId].orEmpty().flatMap { row ->
                if (row.collectionId in removed) survivingAt(row.collectionId) else listOf(row)
            }
            val affectedParents = removed.mapTo(linkedSetOf<String?>()) { id ->
                var parent = byId.getValue(id).parentCollectionId
                while (parent != null && parent in removed) parent = byId.getValue(parent).parentCollectionId
                parent
            }
            affectedParents.forEach { parent ->
                survivingAt(parent).forEachIndexed { index, row ->
                    check(dao.updateCollectionPresentation(row.collectionId, parent, index.toLong()) == 1)
                }
            }
        }
        removed.forEach { check(dao.deleteCollection(it) == 1) }
        if (plan.policy == CollectionDeletionPolicy.DELETE_SUBTREES) {
            removed.mapTo(linkedSetOf()) { byId.getValue(it).parentCollectionId }
                .filterNot { it in removed }.forEach { compactCollectionOrders(it) }
        }
        true
    }

    suspend fun deleteCollection(collectionId: String): Boolean {
        if (dao.collection(collectionId) == null) return false
        return deleteCollections(previewDeletion(setOf(collectionId), CollectionDeletionPolicy.REPARENT_CHILDREN))
    }

    private fun deletionIds(
        rows: List<CollectionEntity>, ids: Set<String>, policy: CollectionDeletionPolicy,
    ): Set<String> {
        if (policy == CollectionDeletionPolicy.REPARENT_CHILDREN) return ids
        val children = rows.groupBy { it.parentCollectionId }
        val removed = linkedSetOf<String>()
        fun visit(id: String) {
            if (removed.add(id)) children[id].orEmpty().forEach { visit(it.collectionId) }
        }
        ids.forEach(::visit)
        return removed
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

    suspend fun createManualCollectionWithMemberships(
        collection: LibraryCollection,
        identities: Set<BookIdentity>,
    ) {
        require(collection.kind == CollectionKind.MANUAL) { "Stored membership requires a manual collection" }
        database.withTransaction {
            createCollection(collection)
            identities.forEachIndexed { index, identity ->
                val entry = requireNotNull(dao.libraryEntry(identity.sourceId, identity.remoteBookId)?.takeIf { it.locallyPinned }) {
                    "Book is not in library"
                }
                check(
                    dao.insertManualMembership(
                        ManualCollectionMembershipEntity(
                            collection.collectionId,
                            identity.sourceId,
                            identity.remoteBookId,
                            entry.addedAtEpochSecond,
                            entry.addedAtNano,
                            index.toLong(),
                        ),
                    ) != -1L,
                )
            }
        }
    }

    suspend fun manualCollectionIds(identity: BookIdentity): Set<String> =
        dao.manualCollectionIds(identity.sourceId, identity.remoteBookId).toCollection(linkedSetOf())

    suspend fun addManualMemberships(collectionId: String, identities: Set<BookIdentity>): Int =
        database.withTransaction {
            val collection = requireNotNull(dao.collection(collectionId)) { "Unknown collection" }
            require(collection.kind == CollectionKind.MANUAL) { "Only manual collections have stored membership" }
            var nextOrder = dao.nextManualMembershipOrder(collectionId)
            identities.count { identity ->
                val entry = requireNotNull(dao.libraryEntry(identity.sourceId, identity.remoteBookId)?.takeIf { it.locallyPinned }) {
                    "Book is not in library"
                }
                val inserted = dao.insertManualMembership(
                    ManualCollectionMembershipEntity(
                        collectionId,
                        identity.sourceId,
                        identity.remoteBookId,
                        entry.addedAtEpochSecond,
                        entry.addedAtNano,
                        nextOrder,
                    ),
                ) != -1L
                if (inserted) nextOrder++
                inserted
            }
        }

    suspend fun removeManualMemberships(collectionId: String, identities: Set<BookIdentity>): Int =
        database.withTransaction {
            identities.count { identity ->
                dao.deleteManualMembership(collectionId, identity.sourceId, identity.remoteBookId) != 0
            }.also { compactManualMembershipOrders(collectionId) }
        }

    suspend fun reorderManualMemberships(collectionId: String, identities: List<BookIdentity>) =
        database.withTransaction {
            val current = dao.manualCollectionIdentities(collectionId).map { BookIdentity(it.sourceId, it.remoteBookId) }
            require(identities.size == current.size && identities.toSet() == current.toSet()) {
                "Manual collection reorder must contain every current member exactly once"
            }
            identities.forEachIndexed { index, identity ->
                check(
                    dao.updateManualMembershipDisplayOrder(
                        collectionId,
                        identity.sourceId,
                        identity.remoteBookId,
                        index.toLong(),
                    ) == 1,
                )
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
        val entry = requireNotNull(dao.libraryEntry(identity.sourceId, identity.remoteBookId)?.takeIf { it.locallyPinned }) {
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

    private suspend fun compactManualMembershipOrders(collectionId: String) {
        dao.manualMemberships(collectionId).forEachIndexed { index, membership ->
            if (membership.displayOrder != index.toLong()) {
                check(
                    dao.updateManualMembershipDisplayOrder(
                        collectionId,
                        membership.sourceId,
                        membership.remoteBookId,
                        index.toLong(),
                    ) == 1,
                )
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
