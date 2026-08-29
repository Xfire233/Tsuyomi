/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.time.Instant
import java.util.UUID
import org.tsuyomi.core.database.CollectionKind
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.feature.library.LibraryUiState
import org.tsuyomi.feature.library.SmartConditionDraft
import org.tsuyomi.feature.library.SmartField
import org.tsuyomi.feature.library.SystemLibraryFilter
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.smartshelf.MatchMode
import org.tsuyomi.shared.smartshelf.ProgressState
import org.tsuyomi.shared.smartshelf.PublicationStatus
import org.tsuyomi.shared.smartshelf.SmartPredicate
import org.tsuyomi.shared.smartshelf.SmartRule
import org.tsuyomi.shared.smartshelf.SmartRuleNode

@Stable
internal class LibraryFlowController private constructor(
    private val repository: RoomLibraryRepository,
    initialCollectionId: String?,
    initialTagDraft: String,
) {
    constructor(repository: RoomLibraryRepository) : this(repository, null, "")

    var collections by mutableStateOf<List<LibraryCollection>>(emptyList())
        private set
    var collectionMessage by mutableStateOf<String?>(null)
        private set
    var selectedCollectionId by mutableStateOf(initialCollectionId)
        private set
    var state by mutableStateOf(LibraryUiState())
        private set
    var selectedEntry by mutableStateOf<LibraryEntry?>(null)
        private set
    var tagDraft by mutableStateOf(initialTagDraft)
        private set
    var remoteRetryMessage by mutableStateOf<String?>(null)
        private set
    var remoteRetryEnabled by mutableStateOf(false)
        private set

    suspend fun reload(failureMessage: String) {
        state = state.copy(loading = true, failure = null)
        state = try {
            collections = repository.collections()
            if (selectedCollectionId != null && collections.none { it.collectionId == selectedCollectionId }) {
                selectedCollectionId = null
            }
            val entries = selectedCollectionId?.let { repository.collectionEntries(it) }
                ?: repository.libraryEntries()
            state.copy(entries = entries, loading = false)
        } catch (_: Throwable) {
            state.copy(loading = false, failure = failureMessage)
        }
    }

    fun selectCollection(collectionId: String?) {
        selectedCollectionId = collectionId
    }

    fun updateQuery(query: String) {
        state = state.copy(query = query)
    }

    fun updateFilter(filter: SystemLibraryFilter) {
        state = state.copy(filter = filter)
    }

    fun selectEntry(entry: LibraryEntry) {
        selectedEntry = entry
        tagDraft = entry.localTags.joinToString("，")
    }

    fun updateTagDraft(value: String) {
        tagDraft = value
    }

    suspend fun resolveEntry(identity: BookIdentity?) {
        selectedEntry = identity?.let { key ->
            repository.libraryEntries().firstOrNull { it.book.identity == key }
        }
        selectedEntry?.let { tagDraft = it.localTags.joinToString("，") }
    }

    suspend fun createManualCollection(title: String, failureMessage: String): Boolean = runCatching {
        val now = Instant.now()
        repository.createCollection(
            LibraryCollection(
                collectionId = UUID.randomUUID().toString(),
                kind = CollectionKind.MANUAL,
                title = title.trim(),
                parentCollectionId = null,
                displayOrder = collections.size.toLong(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        reload(failureMessage)
    }.isSuccess

    suspend fun createSmartCollection(
        title: String,
        matchAll: Boolean,
        drafts: List<SmartConditionDraft>,
        failureMessage: String,
    ): Boolean = runCatching {
        val now = Instant.now()
        repository.createSmartCollection(
            LibraryCollection(
                collectionId = UUID.randomUUID().toString(),
                kind = CollectionKind.SMART,
                title = title.trim(),
                parentCollectionId = null,
                displayOrder = collections.size.toLong(),
                createdAt = now,
                updatedAt = now,
            ),
            buildSmartRule(matchAll, drafts),
        )
        reload(failureMessage)
    }.isSuccess

    suspend fun deleteCollection(collection: LibraryCollection, failureMessage: String) {
        repository.deleteCollection(collection.collectionId)
        reload(failureMessage)
    }
    fun showCollectionMessage(message: String) {
        collectionMessage = message
    }

    suspend fun saveTags(failureMessage: String) {
        val entry = selectedEntry ?: return
        repository.setLocalTags(entry.book.identity, tagDraft.split(',', '，'))
        reload(failureMessage)
        refreshSelectedFromVisibleEntries(entry.book.identity)
    }

    suspend fun setRating(rating: Int?, failureMessage: String) {
        val entry = selectedEntry ?: return
        repository.setRating(entry.book.identity, rating)
        reload(failureMessage)
        refreshSelectedFromVisibleEntries(entry.book.identity)
    }

    suspend fun removeSelected() {
        selectedEntry?.let { repository.removeFromLibrary(it.book.identity) }
    }

    fun setRemoteRetryState(enabled: Boolean, message: String?) {
        remoteRetryEnabled = enabled
        remoteRetryMessage = message
    }

    fun beginRemoteRetry() {
        remoteRetryEnabled = false
    }

    fun refreshSelectedFromVisibleEntries(identity: BookIdentity) {
        selectedEntry = state.entries.firstOrNull { it.book.identity == identity }
    }

    internal fun savedCollectionId(): String = selectedCollectionId.orEmpty()

    private fun buildSmartRule(matchAll: Boolean, drafts: List<SmartConditionDraft>): SmartRule {
        fun values(raw: String): Set<String> = raw.split(',', '，')
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .toSet()

        val children = drafts.map { draft ->
            val parsedValues = values(draft.value)
            val predicate: SmartPredicate = when (draft.field) {
                SmartField.SOURCE -> SmartPredicate.SourceIn(parsedValues)
                SmartField.MANUAL_COLLECTION -> SmartPredicate.InManualCollection(parsedValues)
                SmartField.TAG -> SmartPredicate.TagContains(MatchMode.ANY, parsedValues)
                SmartField.TITLE -> SmartPredicate.TitleContains(parsedValues)
                SmartField.AUTHOR -> SmartPredicate.AuthorContains(parsedValues)
                SmartField.STATUS -> SmartPredicate.StatusIn(
                    parsedValues.mapTo(linkedSetOf()) { PublicationStatus.valueOf(it.uppercase()) },
                )
                SmartField.RATING -> {
                    val range = draft.value.split(',', '，').map { it.trim() }
                    SmartPredicate.RatingBetween(range.getOrNull(0)?.toDoubleOrNull(), range.getOrNull(1)?.toDoubleOrNull())
                }
                SmartField.ADDED_WITHIN_DAYS -> SmartPredicate.AddedWithinDays(draft.value.trim().toLong())
                SmartField.LAST_READ_WITHIN_DAYS -> SmartPredicate.LastReadWithinDays(draft.value.trim().toLong())
                SmartField.METADATA_UPDATED_WITHIN_DAYS -> SmartPredicate.MetadataUpdatedWithinDays(draft.value.trim().toLong())
                SmartField.PROGRESS -> SmartPredicate.ProgressIn(
                    parsedValues.mapTo(linkedSetOf()) { ProgressState.valueOf(it.uppercase()) },
                )
                SmartField.UNREAD_UPDATE -> SmartPredicate.HasUnreadUpdate
                SmartField.SOURCE_UPDATE -> SmartPredicate.HasSourceUpdate
                SmartField.DORMANT_SOURCE -> SmartPredicate.IsDormantSource
            }
            val node = SmartRuleNode.Predicate(predicate)
            if (draft.excluded) SmartRuleNode.Not(node) else node
        }
        return SmartRule(root = if (matchAll) SmartRuleNode.All(children) else SmartRuleNode.Any(children))
    }

    internal companion object {
        fun restored(repository: RoomLibraryRepository, collectionId: String, tagDraft: String): LibraryFlowController =
            LibraryFlowController(repository, collectionId.ifEmpty { null }, tagDraft)
    }
}

@Composable
internal fun rememberLibraryFlowController(repository: RoomLibraryRepository): LibraryFlowController {
    val saver = remember(repository) {
        listSaver<LibraryFlowController, String>(
            save = { listOf(it.savedCollectionId(), it.tagDraft) },
            restore = { LibraryFlowController.restored(repository, it[0], it[1]) },
        )
    }
    return rememberSaveable(saver = saver) { LibraryFlowController(repository) }
}
