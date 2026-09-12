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
import org.tsuyomi.core.database.LibraryBook
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.preferences.LibraryPreferencesRepository
import org.tsuyomi.core.preferences.LibraryRootNodePreference
import org.tsuyomi.core.preferences.LibraryTabPresentationPreferences
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.feature.library.LibraryDragPayload
import org.tsuyomi.feature.library.LibraryRootItem
import org.tsuyomi.feature.library.LibraryRootNodePlacement
import org.tsuyomi.feature.library.LibraryUiState
import org.tsuyomi.feature.library.LibrarySelectionDialog
import org.tsuyomi.feature.library.LibrarySelectionKind
import org.tsuyomi.feature.library.LibrarySortMode
import org.tsuyomi.feature.library.LibraryMirrorShortcut
import org.tsuyomi.feature.library.SmartConditionDraft
import org.tsuyomi.feature.library.SmartField
import org.tsuyomi.feature.library.LibraryUpdateFilter
import org.tsuyomi.feature.library.buildLibraryRootItems
import org.tsuyomi.feature.library.libraryCollectionRootId
import org.tsuyomi.feature.library.libraryMirrorRootId
import org.tsuyomi.feature.library.projectedEntries
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
    private val preferencesRepository: LibraryPreferencesRepository,
    private val profileName: String,
    initialCollectionId: String?,
    initialTagDraft: String,
    initialFilter: SystemLibraryFilter,
    initialLayout: org.tsuyomi.feature.library.LibraryLayout,
    initialSortMode: LibrarySortMode,
    initialSortDescending: Boolean,
    initialFirstVisibleIndex: Int,
    initialFirstVisibleOffset: Int,
) {
    constructor(
        repository: RoomLibraryRepository,
        preferencesRepository: LibraryPreferencesRepository,
        profileName: String = "STANDARD",
    ) : this(
        repository,
        preferencesRepository,
        profileName,
        null,
        "",
        SystemLibraryFilter.ALL,
        org.tsuyomi.feature.library.LibraryLayout.GRID,
        LibrarySortMode.SMART,
        false,
        0,
        0,
    )

    var collections by mutableStateOf<List<LibraryCollection>>(emptyList())
        private set
    var collectionMessage by mutableStateOf<String?>(null)
        private set
    var selectedCollectionId by mutableStateOf(initialCollectionId)
        private set
    var state by mutableStateOf(
        LibraryUiState(
            filter = initialFilter,
            isRootProjection = initialFilter == SystemLibraryFilter.ALL,
            layout = initialLayout,
            sortMode = initialSortMode,
            sortDescending = initialSortDescending,
            firstVisibleIndex = initialFirstVisibleIndex,
            firstVisibleOffset = initialFirstVisibleOffset,
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
    private var tabPresentations: Map<String, LibraryTabPresentationPreferences> = emptyMap()
    private var callerTab: SystemLibraryFilter = initialFilter
    var filterSortPanelExpanded by mutableStateOf(false)
        private set
    private var installedMirrorRootsProvider: suspend () -> List<LibraryMirrorShortcut> = { emptyList() }
    var coverStates by mutableStateOf<Map<BookIdentity, CoverUiState>>(emptyMap())
        private set
    private var coverRepository: CoverRepository? = null
    private var coverSourceId: String? = null
    private var coverPackageRevision: String? = null
    private var coverCredentialRevision: String? = null
    private var coverScope: CoroutineScope? = null
    private val visibleCoverEntries = linkedMapOf<BookIdentity, LibraryEntry>()
    private val visibleUpdateCoverBooks = linkedMapOf<BookIdentity, LibraryBook>()
    private val coverJobs = mutableMapOf<BookIdentity, Job>()
    private val retainedCoverOrder = linkedSetOf<BookIdentity>()
    private var rootEntries: List<LibraryEntry> = emptyList()
    private var readLaterEntries: List<LibraryEntry> = emptyList()
    var searchableEntries: List<LibraryEntry> = emptyList()
        private set
    var updatePresentationBooks by mutableStateOf<Map<BookIdentity, LibraryBook>>(emptyMap())
        private set
    private var rootLoaded = false
    private val collectionEntryCache = mutableMapOf<String, List<LibraryEntry>>()

    fun configureInstalledMirrorRoots(provider: suspend () -> List<LibraryMirrorShortcut>) {
        installedMirrorRootsProvider = provider
    }

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
        visibleCoverEntries.values.forEach { entry -> startCoverRequest(entry.book) }
        visibleUpdateCoverBooks.values.forEach(::startCoverRequest)
    }

    fun setCoverVisible(entry: LibraryEntry, visible: Boolean) {
        val identity = entry.book.identity
        if (!visible) {
            visibleCoverEntries.remove(identity)
            if (!isCoverVisible(identity)) coverJobs.remove(identity)?.cancel()
            trimRetainedCoverStates()
            return
        }
        visibleCoverEntries[identity] = entry
        retainedCoverOrder.remove(identity)
        retainedCoverOrder += identity
        startCoverRequest(entry.book)
    }

    /** Exposes the same verified image pipeline for a real update/mirror book projection. */
    fun setCoverVisible(book: LibraryBook, visible: Boolean) {
        val identity = book.identity
        if (!visible) {
            visibleUpdateCoverBooks.remove(identity)
            if (!isCoverVisible(identity)) coverJobs.remove(identity)?.cancel()
            trimRetainedCoverStates()
            return
        }
        visibleUpdateCoverBooks[identity] = book
        retainedCoverOrder.remove(identity)
        retainedCoverOrder += identity
        startCoverRequest(book)
    }

    fun coverState(entry: LibraryEntry): CoverUiState = coverState(entry.book)

    fun coverState(book: LibraryBook): CoverUiState = coverStates[book.identity]
        ?: CoverUiState.Fallback(FallbackSpec(book.title, book.identity.sourceId))

    private fun isCoverVisible(identity: BookIdentity): Boolean =
        identity in visibleCoverEntries || identity in visibleUpdateCoverBooks

    private fun startCoverRequest(book: LibraryBook) {
        val identity = book.identity
        coverJobs.remove(identity)?.cancel()
        val fallback = FallbackSpec(book.title, identity.sourceId)
        val url = book.coverUrl
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
                    referrerUrl = book.canonicalUrl,
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
            val victim = retainedCoverOrder.firstOrNull { !isCoverVisible(it) } ?: return
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
            preferencesRepository.migrateLegacyRootPresentation(
                listOf(
                    SystemLibraryFilter.ALL,
                    SystemLibraryFilter.CONTINUE,
                    SystemLibraryFilter.READ_LATER,
                ).associate { filter -> tabKey(filter) to defaultTabPresentation(filter) },
            )
            val presentationPreferences = preferencesRepository.preferences.first()
            tabPresentations = presentationPreferences.tabPresentations
            val nextCollections = repository.collections()
            val nextRootEntries = repository.libraryEntries()
            val nextReadLaterEntries = repository.readLaterEntries()
            val collectionCounts = nextCollections.associate { collection ->
                collection.collectionId to repository.collectionEntries(collection.collectionId).size
            }
            val mirrorSnapshots = repository.remoteMirrorBindings().map { binding ->
                binding to repository.remoteMirrorSnapshot(binding.sourceId)
            }
            val snapshotMirrorShortcuts = mirrorSnapshots.flatMap { (binding, snapshot) ->
                buildList {
                    add(
                        LibraryMirrorShortcut(
                            sourceId = binding.sourceId,
                            targetId = null,
                            label = binding.displayName,
                            count = snapshot?.books?.size ?: 0,
                            frozen = binding.frozen,
                        ),
                    )
                    snapshot?.targets?.forEach { target ->
                        add(
                            LibraryMirrorShortcut(
                                sourceId = binding.sourceId,
                                targetId = target.targetId,
                                label = target.displayName,
                                count = snapshot.books.count { it.targetId == target.targetId },
                                frozen = target.frozen,
                            ),
                        )
                    }
                }
            }
            val installedMirrorRoots = runCatching { installedMirrorRootsProvider() }.getOrDefault(emptyList())
            val snapshotRoots = snapshotMirrorShortcuts.filter { it.targetId == null }.associateBy { it.sourceId }
            val rootMirrors = linkedMapOf<String, LibraryMirrorShortcut>().apply {
                putAll(snapshotRoots)
                installedMirrorRoots.forEach { installed ->
                    val snapshot = snapshotRoots[installed.sourceId]
                    put(installed.sourceId, installed.copy(count = snapshot?.count ?: 0, frozen = false))
                }
            }.values
            val mirrorShortcuts = rootMirrors + snapshotMirrorShortcuts.filter { it.targetId != null }
            val structuralIds = buildList {
                nextCollections.filter { it.parentCollectionId == null }
                    .sortedWith(compareBy<LibraryCollection> { it.displayOrder }.thenBy { it.collectionId })
                    .forEach { add(libraryCollectionRootId(it.collectionId)) }
                mirrorShortcuts.filter { it.targetId == null }
                    .sortedWith(compareBy<LibraryMirrorShortcut> { it.label }.thenBy { it.sourceId })
                    .forEach { add(libraryMirrorRootId(it.sourceId)) }
            }
            val normalizedRootNodes = buildList {
                val seen = hashSetOf<String>()
                presentationPreferences.rootNodes.forEach { preference ->
                    if (preference.id in structuralIds && seen.add(preference.id)) add(preference)
                }
                structuralIds.forEach { id -> if (seen.add(id)) add(LibraryRootNodePreference(id, 0)) }
            }
            if (normalizedRootNodes != presentationPreferences.rootNodes) {
                preferencesRepository.updateRootNodes(normalizedRootNodes)
            }
            val nextUpdatePresentationBooks = linkedMapOf<BookIdentity, LibraryBook>().apply {
                nextRootEntries.forEach { entry -> put(entry.book.identity, entry.book) }
                mirrorSnapshots.forEach { (binding, snapshot) ->
                    if (!binding.frozen) snapshot?.books?.forEach { mirrorBook ->
                        putIfAbsent(mirrorBook.book.identity, mirrorBook.book)
                    }
                }
            }
            val websiteGroupingSourceIds = mirrorShortcuts.asSequence()
                .map(LibraryMirrorShortcut::sourceId)
                .distinct()
                .filterTo(linkedSetOf()) { sourceId ->
                    presentationPreferences.websiteGroupingBySource[sourceId] == true
                }
            val validSelectedId = selectedId?.takeIf { id -> nextCollections.any { it.collectionId == id } }
            val nextEntries = validSelectedId?.let { id ->
                repository.collectionEntries(id).also { collectionEntryCache[id] = it }
            } ?: if (state.filter == SystemLibraryFilter.READ_LATER) nextReadLaterEntries else nextRootEntries
            collections = nextCollections
            rootEntries = nextRootEntries
            readLaterEntries = nextReadLaterEntries
            searchableEntries = nextRootEntries + nextReadLaterEntries.filterNot { it.localMembership }
            updatePresentationBooks = nextUpdatePresentationBooks
            rootLoaded = true
            selectedCollectionId = validSelectedId
            val localIdentities = nextRootEntries.mapTo(hashSetOf()) { it.book.identity }
            val updateOnlyEntries = state.updates.values.mapNotNull { update ->
                nextUpdatePresentationBooks[update.identity]
                    ?.takeIf { it.identity !in localIdentities }
                    ?.let { book ->
                        LibraryEntry(
                            book = book,
                            libraryAddedAt = book.addedAt,
                            rating = null,
                            localTags = emptySet(),
                            sourceAvailable = true,
                            reconciliation = null,
                            localMembership = false,
                        )
                    }
            }
            val restoredTab = if (validSelectedId == null) tabPresentation(state.filter) else null
            state.copy(
                entries = nextEntries,
                rootNodePlacements = normalizedRootNodes.map { LibraryRootNodePlacement(it.id, it.bookOffset) },
                collectionCounts = collectionCounts,
                updateFilter = if (presentationPreferences.showUpdatesOnly) {
                    LibraryUpdateFilter.UPDATES_ONLY
                } else {
                    LibraryUpdateFilter.ALL
                },
                mirrorShortcuts = mirrorShortcuts,
                websiteGroupingSourceIds = websiteGroupingSourceIds,
                layout = restoredTab?.layout?.let { name ->
                    runCatching { org.tsuyomi.feature.library.LibraryLayout.valueOf(name) }.getOrNull()
                } ?: state.layout,
                sortMode = restoredTab?.sortMode?.let { name ->
                    runCatching { LibrarySortMode.valueOf(name) }.getOrNull()
                } ?: state.sortMode,
                sortDescending = restoredTab?.sortDescending ?: state.sortDescending,
                firstVisibleIndex = restoredTab?.firstVisibleIndex ?: state.firstVisibleIndex,
                firstVisibleOffset = restoredTab?.firstVisibleOffset ?: state.firstVisibleOffset,
                isRootProjection = validSelectedId == null && state.filter == SystemLibraryFilter.ALL,
                updateOnlyEntries = updateOnlyEntries,
                loading = false,
                refreshing = false,
            )
        } catch (_: Throwable) {
            state.copy(
                loading = false,
                refreshing = false,
                failure = failureMessage.takeUnless { hasCurrentProjection },
                refreshFailure = failureMessage.takeIf { hasCurrentProjection },
            )
        }
    }

    suspend fun restoreLibraryHome() {
        if (selectedCollectionId != null) selectTab(callerTab)
    }

    fun selectCollection(collectionId: String) {
        if (state.filter in setOf(
                SystemLibraryFilter.ALL,
                SystemLibraryFilter.CONTINUE,
                SystemLibraryFilter.READ_LATER,
            )
        ) callerTab = state.filter
        clearSelection()
        selectedCollectionId = collectionId
        val cached = collectionEntryCache[collectionId]
        state = state.copy(
            entries = cached.orEmpty(),
            filter = SystemLibraryFilter.ALL,
            isRootProjection = false,
            loading = cached == null,
            refreshing = false,
            failure = null,
            refreshFailure = null,
            firstVisibleIndex = 0,
            firstVisibleOffset = 0,
        )
    }

    suspend fun selectTab(requested: SystemLibraryFilter) {
        val filter = requested.takeIf {
            it == SystemLibraryFilter.ALL || it == SystemLibraryFilter.CONTINUE || it == SystemLibraryFilter.READ_LATER
        } ?: SystemLibraryFilter.ALL
        if (selectedCollectionId == null) persistCurrentTabPresentation()
        clearSelection()
        selectedCollectionId = null
        callerTab = filter
        filterSortPanelExpanded = false
        val presentation = tabPresentation(filter)
        state = state.copy(
            entries = if (filter == SystemLibraryFilter.READ_LATER) readLaterEntries else rootEntries,
            filter = filter,
            isRootProjection = filter == SystemLibraryFilter.ALL,
            layout = runCatching { org.tsuyomi.feature.library.LibraryLayout.valueOf(presentation.layout) }
                .getOrDefault(defaultTabPresentation(filter).let { org.tsuyomi.feature.library.LibraryLayout.valueOf(it.layout) }),
            sortMode = runCatching { LibrarySortMode.valueOf(presentation.sortMode) }
                .getOrDefault(LibrarySortMode.valueOf(defaultTabPresentation(filter).sortMode)),
            sortDescending = presentation.sortDescending,
            firstVisibleIndex = presentation.firstVisibleIndex,
            firstVisibleOffset = presentation.firstVisibleOffset,
            loading = !rootLoaded,
            refreshing = false,
            failure = null,
            refreshFailure = null,
        )
    }

    fun updateUnresolved(
        updates: List<org.tsuyomi.shared.librarydomain.UnresolvedUpdate>,
        session: org.tsuyomi.shared.librarydomain.UpdateSessionSummary?,
    ) {
        val byIdentity = updates.associateBy { it.identity }
        val localIdentities = rootEntries.mapTo(hashSetOf()) { it.book.identity }
        val mirrorOnly = updates.mapNotNull { update ->
            updatePresentationBooks[update.identity]
                ?.takeIf { it.identity !in localIdentities }
                ?.let { book ->
                    LibraryEntry(
                        book = book,
                        libraryAddedAt = book.addedAt,
                        rating = null,
                        localTags = emptySet(),
                        sourceAvailable = true,
                        reconciliation = null,
                        localMembership = false,
                    )
                }
        }
        state = state.copy(updates = byIdentity, updateOnlyEntries = mirrorOnly, updateSession = session)
    }

    suspend fun setUpdateFilter(filter: LibraryUpdateFilter) {
        state = state.copy(updateFilter = filter)
        preferencesRepository.updateShowUpdatesOnly(filter == LibraryUpdateFilter.UPDATES_ONLY)
    }

    fun setFilterAndSortPanelExpanded(expanded: Boolean) {
        filterSortPanelExpanded = expanded
    }


    private fun tabKey(filter: SystemLibraryFilter): String = "$profileName:${filter.name}"

    private fun defaultTabPresentation(filter: SystemLibraryFilter): LibraryTabPresentationPreferences = when (filter) {
        SystemLibraryFilter.ALL -> LibraryTabPresentationPreferences(sortMode = LibrarySortMode.SMART.name)
        SystemLibraryFilter.CONTINUE -> LibraryTabPresentationPreferences(
            sortMode = LibrarySortMode.RECENT.name,
            sortDescending = true,
        )
        SystemLibraryFilter.READ_LATER -> LibraryTabPresentationPreferences(
            sortMode = LibrarySortMode.ADDED.name,
            sortDescending = true,
        )
        SystemLibraryFilter.UNREAD, SystemLibraryFilter.DORMANT -> defaultTabPresentation(SystemLibraryFilter.ALL)
    }

    private fun tabPresentation(filter: SystemLibraryFilter): LibraryTabPresentationPreferences =
        tabPresentations[tabKey(filter)] ?: defaultTabPresentation(filter)

    private fun currentTabPresentation(): LibraryTabPresentationPreferences = LibraryTabPresentationPreferences(
        layout = state.layout.name,
        sortMode = state.sortMode.name,
        sortDescending = state.sortDescending,
        firstVisibleIndex = state.firstVisibleIndex,
        firstVisibleOffset = state.firstVisibleOffset,
    )

    private suspend fun persistCurrentTabPresentation() {
        if (selectedCollectionId != null || state.filter !in setOf(
                SystemLibraryFilter.ALL,
                SystemLibraryFilter.CONTINUE,
                SystemLibraryFilter.READ_LATER,
            )
        ) return
        val key = tabKey(state.filter)
        val presentation = currentTabPresentation()
        tabPresentations = tabPresentations + (key to presentation)
        preferencesRepository.updateTabPresentation(key, presentation)
    }

    fun updateViewport(firstVisibleIndex: Int, firstVisibleOffset: Int) {
        state = state.copy(
            firstVisibleIndex = firstVisibleIndex.coerceAtLeast(0),
            firstVisibleOffset = firstVisibleOffset.coerceAtLeast(0),
        )
    }

    suspend fun persistViewport(firstVisibleIndex: Int, firstVisibleOffset: Int) {
        updateViewport(firstVisibleIndex, firstVisibleOffset)
        persistCurrentTabPresentation()
    }

    suspend fun cycleLayout() {
        state = state.copy(layout = state.layout.next(), firstVisibleIndex = 0, firstVisibleOffset = 0)
        persistCurrentTabPresentation()
    }

    suspend fun selectSort(mode: LibrarySortMode) {
        state = state.copy(sortMode = mode, firstVisibleIndex = 0, firstVisibleOffset = 0)
        persistCurrentTabPresentation()
    }

    suspend fun selectSortDirection(descending: Boolean) {
        state = state.copy(sortDescending = descending, firstVisibleIndex = 0, firstVisibleOffset = 0)
        persistCurrentTabPresentation()
    }

    fun prepareDraggedBooks(identities: Set<BookIdentity>) {
        require(identities.isNotEmpty())
        state = state.copy(
            selectionKind = LibrarySelectionKind.BOOK,
            selectedBookIds = identities,
            selectedCollectionIds = emptySet(),
        )
    }

    suspend fun manualCollectionIds(identity: BookIdentity): Set<String> = repository.manualCollectionIds(identity)

    suspend fun applyBookDestinations(
        identity: BookIdentity,
        collectionIds: Set<String>,
        failureMessage: String,
    ): Boolean = runCatching {
        require(repository.libraryEntry(identity)?.localMembership == true) { "Book must exist in the local library" }
        val manualIds = collections.asSequence()
            .filter { it.kind == CollectionKind.MANUAL }
            .mapTo(hashSetOf()) { it.collectionId }
        val currentIds = repository.manualCollectionIds(identity) intersect manualIds
        (currentIds - collectionIds).forEach { collectionId -> repository.removeManualMembership(collectionId, identity) }
        (collectionIds - currentIds).forEach { collectionId -> repository.addManualMembership(collectionId, identity) }
        reload(failureMessage)
    }.onFailure {
        collectionMessage = failureMessage
    }.isSuccess

    fun isWebsiteGroupingEnabled(sourceId: String): Boolean = sourceId in state.websiteGroupingSourceIds

    suspend fun setWebsiteGroupingEnabled(
        sourceId: String,
        enabled: Boolean,
        failureMessage: String,
    ): Boolean = runCatching {
        preferencesRepository.updateWebsiteGrouping(sourceId, enabled)
        state = state.copy(
            websiteGroupingSourceIds = if (enabled) {
                state.websiteGroupingSourceIds + sourceId
            } else {
                state.websiteGroupingSourceIds - sourceId
            },
        )
    }.onFailure {
        collectionMessage = failureMessage
    }.isSuccess

    fun requestRootCollectionCreation(moved: Set<BookIdentity>, target: BookIdentity? = null) {
        prepareDraggedBooks(moved + listOfNotNull(target))
        state = state.copy(selectionDialog = LibrarySelectionDialog.CREATE_COLLECTION)
    }

    private fun currentRootItems(): List<LibraryRootItem> = buildLibraryRootItems(
        entries = state.projectedEntries(),
        collections = collections,
        collectionCounts = state.collectionCounts,
        mirrors = state.mirrorShortcuts.filter { it.targetId == null },
        placements = state.rootNodePlacements,
        customOrder = true,
    )

    suspend fun reorderRootBooks(
        moved: Set<BookIdentity>,
        destinationIndex: Int,
        failureMessage: String,
    ): Boolean = runCatching {
        require(state.isRootProjection && state.sortMode == LibrarySortMode.CUSTOM)
        val current = currentRootItems()
        val moving = current.filterIsInstance<LibraryRootItem.Book>().filter { it.entry.book.identity in moved }
        require(moving.isNotEmpty())
        val remaining = current.filterNot { item ->
            item is LibraryRootItem.Book && item.entry.book.identity in moved
        }.toMutableList()
        val removedBefore = current.take(destinationIndex.coerceIn(0, current.size)).count { item ->
            item is LibraryRootItem.Book && item.entry.book.identity in moved
        }
        remaining.addAll((destinationIndex - removedBefore).coerceIn(0, remaining.size), moving)
        persistRootSequence(remaining)
        clearSelection()
        reload(failureMessage)
    }.onFailure {
        collectionMessage = failureMessage
    }.isSuccess

    suspend fun reorderRootNode(
        id: String,
        destinationIndex: Int,
        failureMessage: String,
    ): Boolean = runCatching {
        require(state.isRootProjection && state.sortMode == LibrarySortMode.CUSTOM)
        val current = currentRootItems()
        val moving = current.single { it.key == id && it !is LibraryRootItem.Book }
        val oldIndex = current.indexOf(moving)
        val remaining = current.filterNot { it.key == id }.toMutableList()
        val adjusted = (destinationIndex - if (oldIndex in 0 until destinationIndex) 1 else 0)
            .coerceIn(0, remaining.size)
        remaining.add(adjusted, moving)
        persistRootSequence(remaining)
        reload(failureMessage)
    }.onFailure {
        collectionMessage = failureMessage
    }.isSuccess

    private suspend fun persistRootSequence(items: List<LibraryRootItem>) {
        val bookOrder = items.filterIsInstance<LibraryRootItem.Book>().map { it.entry.book.identity }
        repository.reorderLibrary(bookOrder)
        var bookOffset = 0
        val placements = buildList {
            items.forEach { item ->
                when (item) {
                    is LibraryRootItem.Book -> bookOffset++
                    is LibraryRootItem.Collection, is LibraryRootItem.Mirror -> add(
                        LibraryRootNodePreference(item.key, bookOffset),
                    )
                }
            }
        }
        preferencesRepository.updateRootNodes(placements)
        state = state.copy(rootNodePlacements = placements.map { LibraryRootNodePlacement(it.id, it.bookOffset) })
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
        requestRootCollectionCreation(moved, target)
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
        val existingPlacements = state.rootNodePlacements
        val newPlacement = LibraryRootNodePlacement(libraryCollectionRootId(collectionId), 0)
        val placements = listOf(newPlacement) + existingPlacements.filterNot { it.id == newPlacement.id }
        preferencesRepository.updateRootNodes(placements.map { LibraryRootNodePreference(it.id, it.bookOffset) })
        state = state.copy(rootNodePlacements = placements)
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
        require(selectedCollectionId != null && state.sortMode == LibrarySortMode.CUSTOM &&
            state.filter == SystemLibraryFilter.ALL)
        val current = state.entries.map { it.book.identity }
        val moving = current.filter { it in moved }
        require(moving.isNotEmpty())
        val remaining = current.filterNot { it in moved }.toMutableList()
        val removedBeforeDestination = current.take(destinationIndex.coerceIn(0, current.size)).count { it in moved }
        val adjustedDestination = (destinationIndex - removedBeforeDestination).coerceIn(0, remaining.size)
        remaining.addAll(adjustedDestination, moving)
        val collectionId = requireNotNull(selectedCollectionId)
        require(currentCollection()?.kind == CollectionKind.MANUAL)
        repository.reorderManualMemberships(collectionId, remaining)
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
                SmartField.UNRESOLVED_UPDATE -> SmartPredicate.HasUnresolvedUpdate
                SmartField.DORMANT_SOURCE -> SmartPredicate.IsDormantSource
            }
            val node = SmartRuleNode.Predicate(predicate)
            if (draft.excluded) SmartRuleNode.Not(node) else node
        }
        return SmartRule(root = if (matchAll) SmartRuleNode.All(children) else SmartRuleNode.Any(children))
    }

    internal fun savedCallerTab(): SystemLibraryFilter = callerTab

    internal companion object {
        private const val MAX_RETAINED_COVER_STATES = 24

        fun restored(
            repository: RoomLibraryRepository,
            preferencesRepository: LibraryPreferencesRepository,
            profileName: String,
            collectionId: String,
            tagDraft: String,
            filterName: String,
            layoutName: String,
            sortModeName: String,

            sortDescending: String,
            firstVisibleIndex: String,
            firstVisibleOffset: String,
        ): LibraryFlowController = LibraryFlowController(
            repository,
            preferencesRepository,
            profileName,
            collectionId.ifEmpty { null },
            tagDraft,
            runCatching { SystemLibraryFilter.valueOf(filterName) }.getOrDefault(SystemLibraryFilter.ALL),
            runCatching { org.tsuyomi.feature.library.LibraryLayout.valueOf(layoutName) }
                .getOrDefault(org.tsuyomi.feature.library.LibraryLayout.GRID),
            runCatching { LibrarySortMode.valueOf(sortModeName) }.getOrDefault(LibrarySortMode.SMART),
            sortDescending.toBooleanStrictOrNull() ?: false,
            firstVisibleIndex.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            firstVisibleOffset.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        )
    }
}

@Composable
internal fun rememberLibraryFlowController(
    repository: RoomLibraryRepository,
    preferencesRepository: LibraryPreferencesRepository,
    profileName: String,
): LibraryFlowController {
    val saver = remember(repository, preferencesRepository, profileName) {
        listSaver<LibraryFlowController, String>(
            save = {
                listOf(
                    it.savedCollectionId(),
                    it.tagDraft,
                    it.savedCallerTab().name,
                    it.state.layout.name,
                    it.state.sortMode.name,
                    it.state.sortDescending.toString(),
                    it.state.firstVisibleIndex.toString(),
                    it.state.firstVisibleOffset.toString(),
                )
            },
            restore = {
                LibraryFlowController.restored(
                    repository,
                    preferencesRepository,
                    profileName,
                    it[0],
                    it[1],
                    it[2],
                    it[3],
                    it[4],
                    it[5],
                    it[6],
                    it[7],
                )
            },
        )
    }
    return rememberSaveable(saver = saver) {
        LibraryFlowController(repository, preferencesRepository, profileName)
    }
}
