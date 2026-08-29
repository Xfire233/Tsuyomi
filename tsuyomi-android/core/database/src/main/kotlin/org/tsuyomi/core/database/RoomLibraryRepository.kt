/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.database

import java.time.Instant
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.smartshelf.SmartRule

/**
 * The only public persistence boundary for the initial library schema. It exposes stable identities
 * and semantic locators, never Room entities or storage row identifiers.
 */
class RoomLibraryRepository(database: TsuyomiDatabase) {
    private val dao = database.libraryDao()
    private val catalog = RoomLibraryCatalogStore(database, dao)
    private val remote = RoomRemoteLibraryStore(database, dao, catalog)
    private val collections = RoomCollectionStore(database, dao, catalog)
    private val progress = RoomReadingProgressStore(database, dao)

    suspend fun saveBook(book: LibraryBook) = catalog.saveBook(book)
    suspend fun book(identity: BookIdentity): LibraryBook? = catalog.book(identity)
    suspend fun libraryEntries(): List<LibraryEntry> = catalog.libraryEntries()
    suspend fun addToLibrary(book: LibraryBook): Boolean = catalog.addToLibrary(book)
    suspend fun removeFromLibrary(identity: BookIdentity): Boolean = catalog.removeFromLibrary(identity)
    suspend fun setRating(identity: BookIdentity, rating: Int?) = catalog.setRating(identity, rating)
    suspend fun setLocalTags(identity: BookIdentity, tags: Collection<String>) = catalog.setLocalTags(identity, tags)

    suspend fun setSourceAvailability(sourceId: String, version: String?, available: Boolean, generation: Long) =
        remote.setSourceAvailability(sourceId, version, available, generation)
    suspend fun sourceAvailability(sourceId: String): SourceAvailability? = remote.sourceAvailability(sourceId)
    suspend fun sourceRemotePolicy(sourceId: String): SourceRemotePolicy? = remote.sourceRemotePolicy(sourceId)
    suspend fun mergeRemoteLibrary(request: RemoteLibraryMergeRequest): Int = remote.merge(request)
    suspend fun dismissFirstRemoteImportPrompt(sourceId: String, capabilityFingerprint: String): Boolean =
        remote.dismissFirstRemoteImportPrompt(sourceId, capabilityFingerprint)
    suspend fun setAddWritebackEnabled(sourceId: String, capabilityFingerprint: String, enabled: Boolean): Boolean =
        remote.setAddWritebackEnabled(sourceId, capabilityFingerprint, enabled)
    suspend fun saveSourceRemotePolicy(policy: SourceRemotePolicy) = remote.saveSourceRemotePolicy(policy)
    suspend fun beginRemoteAdd(request: RemoteAddRequest): String = remote.beginRemoteAdd(request)
    suspend fun transitionRemoteAdd(
        id: String,
        expected: RemoteReconciliationState,
        next: RemoteReconciliationState,
        now: Instant,
        diagnosticId: String? = null,
    ): Boolean = remote.transitionRemoteAdd(id, expected, next, now, diagnosticId)

    suspend fun collections(): List<LibraryCollection> = collections.collections()
    suspend fun collectionEntries(collectionId: String, now: Instant = Instant.now()): List<LibraryEntry> =
        collections.collectionEntries(collectionId, now)
    suspend fun createSmartCollection(collection: LibraryCollection, rule: SmartRule) =
        collections.createSmartCollection(collection, rule)
    suspend fun renameCollection(collectionId: String, title: String, updatedAt: Instant = Instant.now()) =
        collections.renameCollection(collectionId, title, updatedAt)
    suspend fun deleteCollection(collectionId: String): Boolean = collections.deleteCollection(collectionId)
    suspend fun createCollection(collection: LibraryCollection) = collections.createCollection(collection)
    suspend fun updateCollectionPresentation(
        collectionId: String,
        parentCollectionId: String?,
        displayOrder: Long,
    ) = collections.updateCollectionPresentation(collectionId, parentCollectionId, displayOrder)
    suspend fun addManualMembership(collectionId: String, identity: BookIdentity): Boolean =
        collections.addManualMembership(collectionId, identity)
    suspend fun removeManualMembership(collectionId: String, identity: BookIdentity): Boolean =
        collections.removeManualMembership(collectionId, identity)

    suspend fun saveProgress(incoming: ReadingProgress): ProgressWriteResult = progress.saveProgress(incoming)
    suspend fun progress(identity: BookIdentity): ReadingProgress? = progress.progress(identity)
}
