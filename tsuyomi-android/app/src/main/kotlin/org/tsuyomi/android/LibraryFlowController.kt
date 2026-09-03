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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import org.tsuyomi.core.database.CollectionKind
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.preferences.LibraryPreferencesRepository
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.feature.library.LibraryUiState
import org.tsuyomi.feature.library.LibrarySelectionDialog
import org.tsuyomi.feature.library.LibrarySelectionKind
import org.tsuyomi.feature.library.LibrarySortMode
import org.tsuyomi.feature.library.libraryBookShortcutId
import org.tsuyomi.feature.library.SmartConditionDraft
import org.tsuyomi.feature.library.SmartField
import org.tsuyomi.feature.library.SystemLibraryFilter
import org.tsuyomi.feature.library.projectedEntries
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.smartshelf.MatchMode
import org.tsuyomi.shared.smartshelf.ProgressState
import org.tsuyomi.shared.smartshelf.PublicationStatus
import org.tsuyomi.shared.smartshelf.SmartPredicate
import org.tsuyomi.shared.smartshelf.SmartRule
import org.tsuyomi.shared.smartshelf.SmartRuleNode
private const val HiddenShortcutPrefix = "hidden:"
private val SystemShortcutIds = listOf("continue", "recent", "read-later", "updates")

@Stable
internal class LibraryFlowController private constructor(
    private val repository: RoomLibraryRepository,
    private val preferencesRepository: LibraryPreferencesRepository,
    initialCollectionId: String?,
    initialTagDraft: String,
    initialLayout: org.tsuyomi.feature.library.LibraryLayout,
    initialSortMode: LibrarySortMode,
    initialSortDescending: Boolean,
) {
    constructor(
        repository: RoomLibraryRepository,
        preferencesRepository: LibraryPreferencesRepository,
    ) : this(
        repository,
        preferencesRepository,
        null,
        "",
        org.tsuyomi.feature.library.LibraryLayout.GRID,
        LibrarySortMode.CUSTOM,
        false,
    )

    var collections by mutableStateOf<List<LibraryCollection>>(emptyList())
        private set
    var collectionMessage by mutableStateOf<String?>(null)
        private set
    var selectedCollectionId by mutableStateOf(initialCollectionId)
        private set
    var state by mutableStateOf(
        LibraryUiState(
            layout = initialLayout,
            sortMode = initialSortMode,
            sortDescending = initialSortDescending,
        ),
    )
        private set
    var selectedEntry by mutableStateOf<LibraryEntry?>(null)
        private set
    var tagDraft by mutableStateOf(initialTagDraft)
        private set
    var remoteRetryMessage by mutableStateOf<String?>(null)
        private set
    var remoteRetryEnabled by mutableStateOf(false)
        private set
    private val reloadMutex = Mutex()
    private var pendingShortcutInsertionIndex: Int? = null
    private var pendingShortcutReplacementIds: Set<String> = emptySet()
    var coverStates by mutableStateOf<Map<BookIdentity, CoverUiState>>(emptyMap())
        private set
    private var coverRepository: CoverRepository? = null
    private var coverSourceId: String? = null
    private var coverPackageRevision: String? = null
    private var coverCredentialRevision: String? = null
    private var coverScope: CoroutineScope? = null
    private val visibleCoverEntries = linkedMapOf<BookIdentity, LibraryEntry>()
    private val coverJobs = mutableMapOf<BookIdentity, Job>()
    private val retainedCoverOrder = linkedSetOf<BookIdentity>()
    private var rootEntries: List<LibraryEntry> = emptyList()
    private var rootLoaded = false
    private val collectionEntryCache = mutableMapOf<String, List<LibraryEntry>>()


    fun configureCoverRepository(
        repository: CoverRepository?,
        sourceId: String?,
        packageRevision: String?,
        credentialRevision: String?,
        scope: CoroutineScope,
    ) {
        if (coverRepository === repository && coverSourceId == sourceId &&
            coverPackageRevision == packageRevision && coverCredentialRevision == credentialRevision
        ) return
        coverJobs.values.forEach(Job::cancel)
        coverJobs.clear()
        retainedCoverOrder.clear()
        coverStates = emptyMap()
        coverRepository = repository
        coverSourceId = sourceId
        coverPackageRevision = packageRevision
        coverCredentialRevision = credentialRevision
        coverScope = scope
        visibleCoverEntries.values.forEach(::startCoverRequest)
    }

    fun setCoverVisible(entry: LibraryEntry, visible: Boolean) {
        val identity = entry.book.identity
        if (!visible) {
            visibleCoverEntries.remove(identity)
            coverJobs.remove(identity)?.cancel()
            trimRetainedCoverStates()
            return
        }
        visibleCoverEntries[identity] = entry
        retainedCoverOrder.remove(identity)
        retainedCoverOrder += identity
        startCoverRequest(entry)
    }

    fun coverState(entry: LibraryEntry): CoverUiState = coverStates[entry.book.identity]
        ?: CoverUiState.Fallback(FallbackSpec(entry.book.title, entry.book.identity.sourceId))

    private fun startCoverRequest(entry: LibraryEntry) {
        val identity = entry.book.identity
        coverJobs.remove(identity)?.cancel()
        val fallback = FallbackSpec(entry.book.title, identity.sourceId)
        val url = entry.book.coverUrl
        val repository = coverRepository
        val sourceId = coverSourceId
        val packageRevision = coverPackageRevision
        val credentialRevision = coverCredentialRevision
        val scope = coverScope
        if (url == null) {
            retainCoverState(identity, CoverUiState.Absent(fallback))
            return
        }
        if (repository == null || sourceId != identity.sourceId || packageRevision == null ||
            credentialRevision == null || scope == null
        ) {
            retainCoverState(identity, CoverUiState.Fallback(fallback))
            return
        }
        coverJobs[identity] = scope.launch {
            repository.observe(
                CoverRequest(
                    sourceId = sourceId,
                    packageRevision = packageRevision,
                    credentialRevision = credentialRevision,
                    transportUrl = url,
                    referrerUrl = entry.book.canonicalUrl,
                    targetWidthPx = 512,
                    targetHeightPx = 768,
                    fallback = fallback,
                ),
            ).collect { state -> retainCoverState(identity, state) }
        }
    }

    private fun retainCoverState(identity: BookIdentity, coverState: CoverUiState) {
        coverStates = coverStates + (identity to coverState)
        retainedCoverOrder.remove(identity)
        retainedCoverOrder += identity
        trimRetainedCoverStates()
    }

    private fun trimRetainedCoverStates() {
        while (coverStates.size > MAX_RETAINED_COVER_STATES) {
            val victim = retainedCoverOrder.firstOrNull { it !in visibleCoverEntries } ?: return
            retainedCoverOrder.remove(victim)
            coverStates = coverStates - victim
        }
    }

    suspend fun reload(failureMessage: String) = reloadMutex.withLock {
        val selectedId = selectedCollectionId
        val hasCurrentProjection = if (selectedId == null) rootLoaded else selectedId in collectionEntryCache
        state = state.copy(
            loading = !hasCurrentProjection,
            refreshing = hasCurrentProjection,
            failure = null,
            refreshFailure = null,
        )
        state = try {
            val presentationPreferences = preferencesRepository.preferences.first()
            val nextCollections = repository.collections()
            val nextRootEntries = repository.libraryEntries()
            val validSelectedId = selectedId?.takeIf { id -> nextCollections.any { it.collectionId == id } }
            val nextEntries = validSelectedId?.let { id ->
                repository.collectionEntries(id).also { collectionEntryCache[id] = it }
            } ?: nextRootEntries
            collections = nextCollections
            state = state.copy(
                shortcutOrder = presentationPreferences.shortcutOrder,
                shortcutLocked = presentationPreferences.shortcutLocked,
            )
            rootEntries = nextRootEntries
            rootLoaded = true
            selectedCollectionId = validSelectedId
            state.copy(entries = nextEntries, loading = false, refreshing = false)
        } catch (_: Throwable) {
            state.copy(
                loading = false,
                refreshing = false,
                failure = failureMessage.takeUnless { hasCurrentProjection },
                refreshFailure = failureMessage.takeIf { hasCurrentProjection },
            )
        }
    }

    fun selectCollection(collectionId: String) {
        clearSelection()
        selectedCollectionId = collectionId
        val cached = collectionEntryCache[collectionId]
        state = state.copy(
            entries = cached.orEmpty(),
            filter = SystemLibraryFilter.ALL,
            loading = cached == null,
            refreshing = false,
            failure = null,
            refreshFailure = null,
        )
    }

    fun selectSystemFilter(filter: SystemLibraryFilter) {
        clearSelection()
        selectedCollectionId = null
        state = state.copy(
            entries = rootEntries,
            filter = filter,
            loading = !rootLoaded,
            refreshing = false,
            failure = null,
            refreshFailure = null,
        )
    }

    fun selectRoot() {
        clearSelection()
        selectedCollectionId = null
        state = state.copy(
            entries = rootEntries,
            filter = SystemLibraryFilter.ALL,
            loading = !rootLoaded,
            refreshing = false,
            failure = null,
            refreshFailure = null,
        )
    }


    fun cycleLayout() {
        state = state.copy(layout = state.layout.next())
    }

    fun openSort() {
        state = state.copy(sortOpen = true)
    }

    fun dismissSort() {
        state = state.copy(sortOpen = false)
    }

    fun selectSort(mode: LibrarySortMode) {
        state = state.copy(sortMode = mode)
    }

    suspend fun setShortcutOrder(order: List<String>) {
        state = state.copy(shortcutOrder = order)
        preferencesRepository.updateShortcutOrder(order)
    }

    suspend fun setShortcutLocked(locked: Boolean) {
        state = state.copy(shortcutLocked = locked)
        preferencesRepository.updateShortcutLocked(locked)
    }

    fun prepareDraggedBooks(identities: Set<BookIdentity>) {
        require(identities.isNotEmpty())
        state = state.copy(
            selectionKind = LibrarySelectionKind.BOOK,
            selectedBookIds = identities,
            selectedCollectionIds = emptySet(),
        )
    }

    suspend fun dropBooksOnShortcutRoot(
        identities: Set<BookIdentity>,
        destinationIndex: Int,
        failureMessage: String,
    ): Boolean {
        if (identities.size != 1) {
            prepareDraggedBooks(identities)
            pendingShortcutInsertionIndex = destinationIndex
            pendingShortcutReplacementIds = emptySet()
            state = state.copy(selectionDialog = LibrarySelectionDialog.CREATE_COLLECTION)
            return true
        }
        return runCatching {
            val identity = identities.single()
            require(rootEntries.any { it.book.identity == identity })
            val id = libraryBookShortcutId(identity)
            val visible = visibleShortcutIds().toMutableList()
            val oldIndex = visible.indexOf(id)
            if (oldIndex >= 0) visible.removeAt(oldIndex)
            val adjusted = (destinationIndex - if (oldIndex in 0 until destinationIndex) 1 else 0)
                .coerceIn(0, visible.size)
            visible.add(adjusted, id)
            persistShortcutOrder(visible, hiddenShortcutIds() - id)
            prepareDraggedBooks(setOf(identity))
        }.onFailure {
            collectionMessage = failureMessage
        }.isSuccess
    }

    suspend fun moveShortcut(
        id: String,
        destinationIndex: Int,
        failureMessage: String,
    ): Boolean = runCatching {
        val visible = visibleShortcutIds().toMutableList()
        val oldIndex = visible.indexOf(id)
        require(oldIndex >= 0)
        visible.removeAt(oldIndex)
        val adjusted = (destinationIndex - if (oldIndex < destinationIndex) 1 else 0).coerceIn(0, visible.size)
        visible.add(adjusted, id)
        persistShortcutOrder(visible, hiddenShortcutIds() - id)
    }.onFailure {
        collectionMessage = failureMessage
    }.isSuccess

    suspend fun removeShortcut(id: String, failureMessage: String): Boolean = runCatching {
        val visible = visibleShortcutIds().filterNot { it == id }
        val hidden = if (id.startsWith("book:")) hiddenShortcutIds() else hiddenShortcutIds() + id
        persistShortcutOrder(visible, hidden)
        clearSelection()
    }.onFailure {
        collectionMessage = failureMessage
    }.isSuccess

    suspend fun removeBookShortcut(identity: BookIdentity, failureMessage: String): Boolean =
        removeShortcut(libraryBookShortcutId(identity), failureMessage)

    fun requestShortcutCollectionCreation(
        moved: Set<BookIdentity>,
        target: BookIdentity? = null,
        insertionIndex: Int,
        replacementShortcutIds: Set<String> = emptySet(),
    ) {
        prepareDraggedBooks(moved + listOfNotNull(target))
        pendingShortcutInsertionIndex = insertionIndex
        pendingShortcutReplacementIds = replacementShortcutIds
        state = state.copy(selectionDialog = LibrarySelectionDialog.CREATE_COLLECTION)
    }

    private fun hiddenShortcutIds(): Set<String> = state.shortcutOrder
        .asSequence()
        .filter { it.startsWith(HiddenShortcutPrefix) }
        .mapTo(linkedSetOf()) { it.removePrefix(HiddenShortcutPrefix) }

    private fun visibleShortcutIds(): List<String> {
        val hidden = hiddenShortcutIds()
        val available = buildList {
            addAll(SystemShortcutIds)
            addAll(collections.map { "collection:${it.collectionId}" })
            state.shortcutOrder.filterTo(this) { id ->
                id.startsWith("book:") && rootEntries.any { libraryBookShortcutId(it.book.identity) == id }
            }
        }.distinct().filterNot(hidden::contains)
        val ordered = state.shortcutOrder.filter { it in available }
        return ordered + available.filterNot(ordered::contains)
    }

    fun shortcutIndex(id: String): Int = visibleShortcutIds().indexOf(id).coerceAtLeast(0)

    private suspend fun persistShortcutOrder(visible: List<String>, hidden: Set<String>) {
        setShortcutOrder(visible.distinct() + hidden.sorted().map { "$HiddenShortcutPrefix$it" })
    }

    fun selectSortDirection(descending: Boolean) {
        state = state.copy(sortDescending = descending)
    }
    fun openOrToggleEntry(entry: LibraryEntry) {
        if (state.selectionKind == LibrarySelectionKind.BOOK) {
            toggleBookSelection(entry.book.identity)
        } else {
            selectedEntry = entry
            tagDraft = entry.localTags.joinToString("，")
        }
    }

    fun longPressBook(identity: BookIdentity) {
        when (state.selectionKind) {
            null, LibrarySelectionKind.BOOK -> {
                if (identity !in state.selectedBookIds) {
                    state = state.copy(
                        selectionKind = LibrarySelectionKind.BOOK,
                        selectedBookIds = state.selectedBookIds + identity,
                    )
                }
            }
            LibrarySelectionKind.COLLECTION -> Unit
        }
    }

    fun toggleBookSelection(identity: BookIdentity) {
        if (state.selectionKind != null && state.selectionKind != LibrarySelectionKind.BOOK) return
        val selected = if (identity in state.selectedBookIds) {
            state.selectedBookIds - identity
        } else {
            state.selectedBookIds + identity
        }
        state = state.copy(
            selectionKind = LibrarySelectionKind.BOOK.takeIf { selected.isNotEmpty() },
            selectedBookIds = selected,
        )
    }

    fun longPressCollection(collectionId: String) {
        if (collections.none { it.collectionId == collectionId && it.kind == CollectionKind.MANUAL }) return
        when (state.selectionKind) {
            null, LibrarySelectionKind.COLLECTION -> {
                if (collectionId !in state.selectedCollectionIds) {
                    state = state.copy(
                        selectionKind = LibrarySelectionKind.COLLECTION,
                        selectedCollectionIds = state.selectedCollectionIds + collectionId,
                    )
                }
            }
            LibrarySelectionKind.BOOK -> Unit
        }
    }

    fun toggleCollectionSelection(collectionId: String) {
        if (state.selectionKind != null && state.selectionKind != LibrarySelectionKind.COLLECTION) return
        if (collections.none { it.collectionId == collectionId && it.kind == CollectionKind.MANUAL }) return
        val selected = if (collectionId in state.selectedCollectionIds) {
            state.selectedCollectionIds - collectionId
        } else {
            state.selectedCollectionIds + collectionId
        }
        state = state.copy(
            selectionKind = LibrarySelectionKind.COLLECTION.takeIf { selected.isNotEmpty() },
            selectedCollectionIds = selected,
        )
    }

    fun clearSelection() {
        state = state.copy(
            selectionKind = null,
            selectedBookIds = emptySet(),
            selectedCollectionIds = emptySet(),
            selectionDialog = null,
        )
        pendingShortcutInsertionIndex = null
        pendingShortcutReplacementIds = emptySet()
    }

    fun toggleAllVisibleSelection() {
        when (state.selectionKind) {
            LibrarySelectionKind.BOOK -> {
                val visible = state.projectedEntries().mapTo(linkedSetOf()) { it.book.identity }
                val allSelected = visible.isNotEmpty() && state.selectedBookIds.containsAll(visible)
                val selected = if (allSelected) state.selectedBookIds - visible else state.selectedBookIds + visible
                state = state.copy(
                    selectionKind = LibrarySelectionKind.BOOK.takeIf { selected.isNotEmpty() },
                    selectedBookIds = selected,
                )
            }
            LibrarySelectionKind.COLLECTION -> {
                val visible = collections.filter { it.kind == CollectionKind.MANUAL }
                    .mapTo(linkedSetOf()) { it.collectionId }
                val allSelected = visible.isNotEmpty() && state.selectedCollectionIds.containsAll(visible)
                val selected = if (allSelected) state.selectedCollectionIds - visible else state.selectedCollectionIds + visible
                state = state.copy(
                    selectionKind = LibrarySelectionKind.COLLECTION.takeIf { selected.isNotEmpty() },
                    selectedCollectionIds = selected,
                )
            }
            null -> Unit
        }
    }

    fun requestSelectionDialog(dialog: LibrarySelectionDialog) {
        if (state.selectedBookIds.isNotEmpty() || state.selectedCollectionIds.isNotEmpty()) {
            state = state.copy(selectionDialog = dialog)
        }
    }

    fun dismissSelectionDialog() {
        state = state.copy(selectionDialog = null)
    }

    fun requestBookDropOnBook(moved: Set<BookIdentity>, target: BookIdentity) {
        pendingShortcutInsertionIndex = null
        pendingShortcutReplacementIds = emptySet()
        state = state.copy(
            selectionKind = LibrarySelectionKind.BOOK,
            selectedBookIds = moved + target,
            selectionDialog = LibrarySelectionDialog.CREATE_COLLECTION,
        )
    }

    suspend fun createCollectionFromSelection(title: String, failureMessage: String): Boolean = runCatching {
        val selected = state.selectedBookIds
        require(selected.isNotEmpty())
        val now = Instant.now()
        val collectionId = UUID.randomUUID().toString()
        repository.createManualCollectionWithMemberships(
            LibraryCollection(
                collectionId = collectionId,
                kind = CollectionKind.MANUAL,
                title = title.trim(),
                parentCollectionId = null,
                displayOrder = collections.size.toLong(),
                createdAt = now,
                updatedAt = now,
            ),
            selected,
        )
        pendingShortcutInsertionIndex?.let { insertionIndex ->
            val visible = visibleShortcutIds().filterNot(pendingShortcutReplacementIds::contains).toMutableList()
            visible.add(insertionIndex.coerceIn(0, visible.size), "collection:$collectionId")
            persistShortcutOrder(visible, hiddenShortcutIds() + pendingShortcutReplacementIds)
        }
        clearSelection()
        reload(failureMessage)
    }.onFailure {
        collectionMessage = failureMessage
    }.isSuccess

    suspend fun addSelectionToCollection(collectionId: String, failureMessage: String): Boolean = runCatching {
        when (state.selectionKind) {
            LibrarySelectionKind.BOOK -> repository.addManualMemberships(collectionId, state.selectedBookIds)
            LibrarySelectionKind.COLLECTION -> {
                require(collectionId !in state.selectedCollectionIds)
                state.selectedCollectionIds.forEachIndexed { index, selectedId ->
                    repository.updateCollectionPresentation(
                        selectedId,
                        collectionId,
                        collections.size.toLong() + index,
                    )
                }
            }
            null -> error("No active selection")
        }
        clearSelection()
        reload(failureMessage)
    }.isSuccess

    suspend fun removeSelection(failureMessage: String): Boolean = runCatching {
        if (state.selectionKind == LibrarySelectionKind.COLLECTION) {
            state.selectedCollectionIds.forEach { repository.deleteCollection(it) }
        } else {
            val selected = state.selectedBookIds
            when {
                selectedCollectionId != null && currentCollection()?.kind == CollectionKind.MANUAL -> {
                    repository.removeManualMemberships(requireNotNull(selectedCollectionId), selected)
                }
                state.filter == SystemLibraryFilter.READ_LATER -> {
                    selected.forEach { repository.setReadLater(it, false) }
                }
                else -> repository.removeFromLibrary(selected)
            }
        }
        clearSelection()
        reload(failureMessage)
    }.isSuccess

    suspend fun reorderBooks(
        moved: Set<BookIdentity>,
        destinationIndex: Int,
        failureMessage: String,
    ): Boolean = runCatching {
        require(state.sortMode == LibrarySortMode.CUSTOM && state.filter == SystemLibraryFilter.ALL)
        val current = state.entries.map { it.book.identity }
        val moving = current.filter { it in moved }
        require(moving.isNotEmpty())
        val remaining = current.filterNot { it in moved }.toMutableList()
        val removedBeforeDestination = current.take(destinationIndex.coerceIn(0, current.size)).count { it in moved }
        val adjustedDestination = (destinationIndex - removedBeforeDestination).coerceIn(0, remaining.size)
        remaining.addAll(adjustedDestination, moving)
        val collectionId = selectedCollectionId
        if (collectionId == null) {
            repository.reorderLibrary(remaining)
        } else {
            require(currentCollection()?.kind == CollectionKind.MANUAL)
            repository.reorderManualMemberships(collectionId, remaining)
        }
        clearSelection()
        reload(failureMessage)
    }.isSuccess

    private fun currentCollection(): LibraryCollection? =
        selectedCollectionId?.let { id -> collections.firstOrNull { it.collectionId == id } }

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

    suspend fun toggleReadLater(failureMessage: String) {
        val entry = selectedEntry ?: return
        repository.setReadLater(entry.book.identity, !entry.readLater)
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
        private const val MAX_RETAINED_COVER_STATES = 24

        fun restored(
            repository: RoomLibraryRepository,
            preferencesRepository: LibraryPreferencesRepository,
            collectionId: String,
            tagDraft: String,
            layoutName: String,
            sortModeName: String,
            sortDescending: String,
        ): LibraryFlowController = LibraryFlowController(
            repository,
            preferencesRepository,
            collectionId.ifEmpty { null },
            tagDraft,
            runCatching { org.tsuyomi.feature.library.LibraryLayout.valueOf(layoutName) }
                .getOrDefault(org.tsuyomi.feature.library.LibraryLayout.GRID),
            runCatching { LibrarySortMode.valueOf(sortModeName) }.getOrDefault(LibrarySortMode.CUSTOM),
            sortDescending.toBooleanStrictOrNull() ?: false,
        )
    }
}

@Composable
internal fun rememberLibraryFlowController(
    repository: RoomLibraryRepository,
    preferencesRepository: LibraryPreferencesRepository,
): LibraryFlowController {
    val saver = remember(repository, preferencesRepository) {
        listSaver<LibraryFlowController, String>(
            save = {
                listOf(
                    it.savedCollectionId(),
                    it.tagDraft,
                    it.state.layout.name,
                    it.state.sortMode.name,
                    it.state.sortDescending.toString(),
                )
            },
            restore = {
                LibraryFlowController.restored(
                    repository,
                    preferencesRepository,
                    it[0],
                    it[1],
                    it[2],
                    it[3],
                    it[4],
                )
            },
        )
    }
    return rememberSaveable(saver = saver) { LibraryFlowController(repository, preferencesRepository) }
}
