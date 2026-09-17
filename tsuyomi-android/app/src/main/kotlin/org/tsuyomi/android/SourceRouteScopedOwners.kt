/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.tsuyomi.reader.engine.ReaderDocumentCache
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.feature.book.DetailLocalState
import org.tsuyomi.feature.book.DetailMutationOperation
import org.tsuyomi.feature.book.DetailMutationPhase
import org.tsuyomi.feature.book.DetailMutationStatus
import org.tsuyomi.feature.book.DetailCacheAction
import org.tsuyomi.feature.book.DetailCacheState
import org.tsuyomi.feature.book.DetailChapterCachePhase
import kotlinx.coroutines.flow.StateFlow
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.feature.search.SearchResultState
import org.tsuyomi.feature.search.SearchLayout
import org.tsuyomi.feature.library.JitWritebackPrompt
import org.tsuyomi.feature.library.remoteLibrarySelectionId
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.locator.bookmarkPositionKey
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.source.extensionmanager.RemoteOperation
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

/**
 * The source session is activity-retained runtime state. Detail and Reader screen state belongs to
 * their route entry; typed route operations prevent one route from acquiring another route's
 * mutable presentation state.
 */
@Stable
internal class SourceSearchRouteOwner(
    private val flow: SourceFlowController,
    private val savedState: SavedStateHandle,
    initialState: SearchResultState = SearchResultState.Idle,
) {
    var query by mutableStateOf(savedState[QueryKey] ?: "")
        private set
    var authorSearch by mutableStateOf(savedState[AuthorSearchKey] ?: false)
        private set
    var state: SearchResultState by mutableStateOf(initialState)
        private set
    private var requestGeneration = 0L
    private var activeSubmission: SearchSubmission? = null
    val layout: StateFlow<SearchLayout> = savedState.getStateFlow(LayoutKey, SearchLayout.LIST)

    fun updateQuery(value: String) {
        query = value.take(MaxQueryLength)
        authorSearch = false
        savedState[QueryKey] = query
        savedState[AuthorSearchKey] = false
        flow.updateQuery(query)
    }

    fun cycleLayout() {
        savedState[LayoutKey] = layout.value.next()
    }

    suspend fun restore(packageInfo: VerifiedHxpPackage) {
        flow.restoreFor(SourceRestorationTarget.SEARCH, packageInfo)
        flow.restoreSearch(query, authorSearch)
    }

    suspend fun submit(offlineOnly: Boolean = false) {
        if (query.isBlank()) return
        val submission = SearchSubmission(query, authorSearch, offlineOnly)
        if (activeSubmission == submission) return
        activeSubmission = submission
        val submittedQuery = submission.query
        val submittedAuthorSearch = submission.authorSearch
        val generation = ++requestGeneration
        state = SearchResultState.Loading
        try {
            if (submittedAuthorSearch) {
                flow.authorSearch(submittedQuery, offlineOnly)
            } else {
                flow.updateQuery(submittedQuery)
                flow.search(offlineOnly)
            }
            if (generation == requestGeneration) {
                state = if (query == submittedQuery && authorSearch == submittedAuthorSearch) {
                    flow.searchState
                } else {
                    SearchResultState.Idle
                }
            }
        } catch (cancelled: CancellationException) {
            if (generation == requestGeneration) {
                state = SearchResultState.Failure(
                    SourceErrorCode.EXTENSION_CANCELLED,
                    SourceDiagnostic("search-cancelled", "search", "source-session-interrupted"),
                )
            }
            throw cancelled
        } finally {
            if (activeSubmission == submission) activeSubmission = null
        }
    }

    suspend fun submitAuthor(author: String) {
        query = author
        authorSearch = true
        savedState[QueryKey] = query
        savedState[AuthorSearchKey] = true
        submit()
    }

    private data class SearchSubmission(
        val query: String,
        val authorSearch: Boolean,
        val offlineOnly: Boolean,
    )

    fun acceptVerifiedPageResult() {
        state = flow.searchState
    }

    suspend fun select(book: SourceBookSummary) {
        flow.prepareBook(book)
    }

    companion object {
        private const val QueryKey = "source.search.query"
        private const val AuthorSearchKey = "source.search.author"
        private const val MaxQueryLength = 100
        internal const val LayoutKey = "source.search.layout"
    }
}

@Stable
internal class SourceDetailRouteOwner(
    private val flow: SourceFlowController,
    private val savedState: SavedStateHandle,
    private val onLibraryChanged: suspend () -> Unit,
) {
    var state: SourceBookState<SourceBookDetail> by mutableStateOf(flow.detailState)
        private set
    var directoryState: SourceBookState<SourceDirectory> by mutableStateOf(flow.directoryState)
        private set
    var mutation: DetailMutationStatus? by mutableStateOf(null)
        private set
    val unreadOnly: StateFlow<Boolean> = savedState.getStateFlow(UnreadOnlyKey, false)
    val descending: StateFlow<Boolean> = savedState.getStateFlow(DescendingKey, false)
    val tagEditorOpen: StateFlow<Boolean> = savedState.getStateFlow(TagEditorOpenKey, false)
    val tagDraft: StateFlow<String> = savedState.getStateFlow(TagDraftKey, "")
    private var requestGeneration = 0L
    private var loadMutation: DetailLoadMutationToken? = null

    var cacheState: DetailCacheState by mutableStateOf(DetailCacheState())
        private set
    private var cacheIdentity: org.tsuyomi.shared.model.BookIdentity? = null
    private var cacheGeneration = 0L
    private var cacheJob: Job? = null

    val selectedBook: SourceBookSummary?
        get() = flow.selectedBook
    val selectedChapter: SourceChapter?
        get() = flow.selectedChapter
    private val admittedDetail: SourceBookDetail?
        get() = (state as? SourceBookState.Content)?.value
            ?.takeIf { it.summary.identity == selectedBook?.identity }
    val localState: DetailLocalState
        get() = flow.remoteLibrary.selectedLibraryEntry?.let { entry ->
            DetailLocalState(
                inLibrary = entry.localMembership,
                localTagsEditable = admittedDetail != null,
                rating = entry.rating,
                localTags = entry.localTags.toList(),
                readLater = entry.readLater,
                progressChapterId = entry.progress?.locator?.document?.contentId,
                progressChapterFraction = entry.progress?.locator?.chapterProgress,
                completedChapterIds = flow.completedChapterIds,
                reconciliationOperation = flow.remoteLibrary.selectedBookReconciliationOperation,
                reconciliation = flow.remoteLibrary.selectedBookReconciliation?.name,
                remoteRemoveEnabled = flow.remoteLibrary.selectedBookRemoveWritesRemote,
                remoteMoveEnabled = flow.remoteLibrary.selectedBookMoveWritesRemote,
            )
        } ?: DetailLocalState(
            localTagsEditable = admittedDetail != null,
            reconciliation = flow.remoteLibrary.selectedBookReconciliation?.name,
            reconciliationOperation = flow.remoteLibrary.selectedBookReconciliationOperation,
            completedChapterIds = flow.completedChapterIds,
            remoteRemoveEnabled = flow.remoteLibrary.selectedBookRemoveWritesRemote,
            remoteMoveEnabled = flow.remoteLibrary.selectedBookMoveWritesRemote,
        )

    fun toggleUnreadOnly() {
        savedState[UnreadOnlyKey] = !unreadOnly.value
    }

    fun toggleOrder() {
        savedState[DescendingKey] = !descending.value
    }
    suspend fun restore(
        packageInfo: VerifiedHxpPackage,
        target: SourceRestorationTarget = SourceRestorationTarget.DETAIL,
    ) {
        flow.restoreFor(target, packageInfo)
        invalidateCacheIfBookChanged()
        loadAll()
    }


    suspend fun loadAll(
        offlineOnly: Boolean = false,
        operation: DetailMutationOperation? = null,
    ) {
        val book = selectedBook ?: return
        if (operation != null && mutation?.phase == DetailMutationPhase.WORKING && loadMutation == null) return
        val previousDetail = state as? SourceBookState.Content
        val previousDirectory = directoryState as? SourceBookState.Content
        val libraryBookBefore = flow.remoteLibrary.selectedLibraryEntry?.book
        val generation = ++requestGeneration
        retireLoadMutation()
        val token = operation?.let { DetailLoadMutationToken(generation, book.identity, it) }
        if (token != null) {
            loadMutation = token
            mutation = DetailMutationStatus(token.operation, DetailMutationPhase.WORKING)
        }
        if (previousDetail == null) state = SourceBookState.Loading
        if (previousDirectory == null) directoryState = SourceBookState.Loading
        try {
            val requestedDetail = flow.requestDetail(book, offlineOnly)
            if (!isCurrent(generation, book)) {
                abandonLoadMutation(token)
                return
            }
            val nextDetail = if (requestedDetail is SourceBookState.Failure && previousDetail != null) {
                previousDetail
            } else {
                requestedDetail
            }
            state = nextDetail
            val requestedDirectory = flow.requestDirectory(book, offlineOnly)
            if (!isCurrent(generation, book)) {
                abandonLoadMutation(token)
                return
            }
            val nextDirectory = if (requestedDirectory is SourceBookState.Failure && previousDirectory != null) {
                previousDirectory
            } else {
                requestedDirectory
            }
            directoryState = nextDirectory
            if (libraryBookBefore != flow.remoteLibrary.selectedLibraryEntry?.book) onLibraryChanged()
            token?.let { currentToken ->
                completeLoadMutation(
                    currentToken,
                    if (requestedDetail is SourceBookState.Failure || requestedDirectory is SourceBookState.Failure) {
                        DetailMutationStatus(currentToken.operation, DetailMutationPhase.ERROR, "source-read-failed")
                    } else {
                        DetailMutationStatus(currentToken.operation, DetailMutationPhase.SUCCESS)
                    },
                )
            }
        } catch (cancelled: CancellationException) {
            abandonLoadMutation(token)
            throw cancelled
        } catch (error: Throwable) {
            token?.let {
                completeLoadMutation(it, DetailMutationStatus(it.operation, DetailMutationPhase.ERROR, "source-read-failed"))
            }
            throw error
        }
    }
    suspend fun acceptVerifiedDetailResult() {
        state = flow.detailState
        if (state is SourceBookState.Content && flow.remoteLibrary.selectedLibraryEntry != null) {
            onLibraryChanged()
        }
    }

    suspend fun resumeDirectoryAfterVerifiedDetail() {
        val book = selectedBook ?: return
        directoryState = flow.requestDirectory(book)
    }

    fun acceptVerifiedDirectoryResult() {
        val candidate = flow.directoryState
        directoryState = if (
            candidate is SourceBookState.Content && candidate.value.bookIdentity == selectedBook?.identity
        ) {
            candidate
        } else {
            SourceBookState.Loading
        }
    }


    suspend fun execute(command: String) {
        when (runCatching { Command.valueOf(command) }.getOrNull() ?: return) {
            Command.ADD_TO_LIBRARY -> mutate(DetailMutationOperation.ADD_TO_LIBRARY) { flow.addSelectedBook(admittedDetail) }
            Command.REMOVE_FROM_LIBRARY -> mutate(DetailMutationOperation.REMOVE_FROM_LIBRARY) {
                check(flow.removeSelectedBook()) { "Book is not in library" }
            }
            Command.CACHE_DETAIL -> enterCacheSelection()
            Command.REFRESH_DETAIL -> loadAll(operation = DetailMutationOperation.REFRESH_DETAIL)
        }
    }

    suspend fun setRating(rating: Int?) = mutate(DetailMutationOperation.SET_RATING) {
        flow.setSelectedRating(rating)
    }

    suspend fun addTag(tag: String) = mutate(DetailMutationOperation.ADD_TAG) {
        flow.addSelectedLocalTag(requireNotNull(admittedDetail) { "Book detail is not available for local tags" }, tag)
    }

    fun openTagEditor() {
        if (admittedDetail == null) return
        savedState[TagEditorOpenKey] = true
    }

    fun updateTagDraft(value: String) {
        if (tagEditorOpen.value) savedState[TagDraftKey] = value.take(MaxTagLength)
    }

    fun dismissTagEditor() {
        savedState[TagEditorOpenKey] = false
        savedState[TagDraftKey] = ""
    }

    suspend fun confirmTagEditor() {
        val tag = tagDraft.value.trim()
        if (!tagEditorOpen.value || tag.isEmpty() || mutation?.phase == DetailMutationPhase.WORKING) return
        addTag(tag)
        if (mutation?.operation == DetailMutationOperation.ADD_TAG && mutation?.phase == DetailMutationPhase.SUCCESS) {
            dismissTagEditor()
        }
    }
    suspend fun toggleReadLater() = mutate(DetailMutationOperation.TOGGLE_READ_LATER) {
        flow.toggleSelectedReadLater()
    }

    suspend fun selectChapter(chapter: SourceChapter) = flow.prepareChapter(chapter)

    suspend fun removeSelectedBookFromWebsite() = mutateRemote(DetailMutationOperation.REMOVE_FROM_REMOTE) {
        flow.removeSelectedBookFromWebsite()
    }

    suspend fun moveSelectedBookOnWebsite(targetId: String, targetName: String) =
        mutateRemote(DetailMutationOperation.MOVE_REMOTE) {
            flow.moveSelectedBookOnWebsite(targetId, targetName)
        }

    suspend fun retryRemoteReconciliation() = mutateRemote(DetailMutationOperation.RECONCILE_RETRY) {
        flow.retryRemoteMutation()
    }

    suspend fun acknowledgeRemoteReconciliation() = mutate(DetailMutationOperation.RECONCILE_ACKNOWLEDGE) {
        val book = selectedBook ?: return@mutate
        check(flow.acknowledgeUnresolved(book.identity)) { "Acknowledge failed" }
    }

    fun dispose() {
        requestGeneration++
        retireLoadMutation()
        clearCacheState()
    }

    suspend fun handleCacheAction(action: DetailCacheAction) {
        when (action) {
            is DetailCacheAction.Toggle -> toggleCacheChapter(action.chapterId)
            is DetailCacheAction.ToggleAll -> toggleAllCacheChapters(action.chapterIds)
            DetailCacheAction.Start -> startSelectedChapterCache()
            DetailCacheAction.Cancel -> cancelChapterCache()
            DetailCacheAction.Close -> {
                cancelChapterCache()
                cacheGeneration += 1
                cacheState = cacheState.copy(selecting = false, selectedChapterIds = emptySet())
            }
        }
    }
    suspend fun refreshCachedChapterStatuses() {
        if (cacheState.working) return
        val context = cacheContext() ?: return
        val generation = ++cacheGeneration
        val validChapterIds = context.directory.chapters.mapTo(linkedSetOf()) { it.chapterId }
        val cachedChapterIds = flow.cachedChapterIds(context.book.identity, validChapterIds)
        if (!cacheRunCurrent(generation, context.book)) return
        val retained = cacheState.chapters.filterKeys(validChapterIds::contains)
            .filterValues { it != DetailChapterCachePhase.CACHED }
        cacheState = cacheState.copy(
            selectedChapterIds = cacheState.selectedChapterIds
                .intersect(validChapterIds)
                .minus(cachedChapterIds),
            chapters = retained + cachedChapterIds.associateWith { DetailChapterCachePhase.CACHED },
        )
    }


    private suspend fun enterCacheSelection() {
        if (cacheState.working) return
        val context = cacheContext() ?: run {
            cacheState = DetailCacheState(selecting = true)
            return
        }
        val generation = ++cacheGeneration
        val validChapterIds = context.directory.chapters.mapTo(linkedSetOf()) { it.chapterId }
        val cachedChapterIds = flow.cachedChapterIds(context.book.identity, validChapterIds)
        if (!cacheRunCurrent(generation, context.book)) return
        val retained = cacheState.chapters.filterKeys(validChapterIds::contains)
            .filterValues { it != DetailChapterCachePhase.CACHED }
        cacheState = DetailCacheState(
            selecting = true,
            selectedChapterIds = cacheState.selectedChapterIds
                .intersect(validChapterIds)
                .minus(cachedChapterIds),
            chapters = retained + cachedChapterIds.associateWith { DetailChapterCachePhase.CACHED },
        )
    }

    private fun toggleCacheChapter(chapterId: String) {
        val context = cacheContext() ?: return
        if (
            !cacheState.selecting ||
            cacheState.working ||
            context.directory.chapters.none { it.chapterId == chapterId } ||
            cacheState.chapters[chapterId] == DetailChapterCachePhase.CACHED
        ) {
            return
        }
        cacheState = cacheState.copy(
            selectedChapterIds = cacheState.selectedChapterIds.let { selected ->
                if (chapterId in selected) selected - chapterId else selected + chapterId
            },
        )
    }

    private fun toggleAllCacheChapters(requestedChapterIds: Set<String>) {
        val context = cacheContext() ?: return
        if (!cacheState.selecting || cacheState.working) return
        val cacheableChapterIds = context.directory.chapters
            .asSequence()
            .map(SourceChapter::chapterId)
            .filter { chapterId -> cacheState.chapters[chapterId] != DetailChapterCachePhase.CACHED }
            .toCollection(linkedSetOf())
        val scopedChapterIds = requestedChapterIds.intersect(cacheableChapterIds)
        if (scopedChapterIds.isEmpty()) return
        val selected = cacheState.selectedChapterIds
        cacheState = cacheState.copy(
            selectedChapterIds = if (selected.containsAll(scopedChapterIds)) {
                selected - scopedChapterIds
            } else {
                selected + scopedChapterIds
            },
        )
    }

    private suspend fun startSelectedChapterCache() {
        val context = cacheContext() ?: return
        if (!cacheState.selecting || cacheState.working) return
        val selectedChapters = context.directory.chapters.filter { it.chapterId in cacheState.selectedChapterIds }
        if (selectedChapters.isEmpty()) return

        val queuedChapters = selectedChapters.filter { chapter ->
            cacheState.chapters[chapter.chapterId] != DetailChapterCachePhase.CACHED
        }
        if (queuedChapters.isEmpty()) return

        val generation = ++cacheGeneration
        val job = currentCoroutineContext()[Job]
        cacheJob = job
        cacheState = cacheState.copy(
            chapters = cacheState.chapters + queuedChapters.associate { chapter ->
                chapter.chapterId to DetailChapterCachePhase.QUEUED
            },
        )
        try {
            for (chapter in queuedChapters) {
                if (!cacheRunCurrent(generation, context.book)) {
                    invalidateCacheIfBookChanged()
                    return
                }
                cacheState = cacheState.copy(
                    chapters = cacheState.chapters + (chapter.chapterId to DetailChapterCachePhase.CACHING),
                )
                val phase = when (flow.cacheChapter(context.book, chapter)) {
                    ChapterCacheResult.CACHED -> DetailChapterCachePhase.CACHED
                    ChapterCacheResult.FAILED -> DetailChapterCachePhase.FAILED
                    ChapterCacheResult.CANCELLED -> DetailChapterCachePhase.CANCELLED
                }
                if (!cacheRunCurrent(generation, context.book)) {
                    invalidateCacheIfBookChanged()
                    return
                }
                cacheState = cacheState.copy(
                    selectedChapterIds = if (phase == DetailChapterCachePhase.CACHED) {
                        cacheState.selectedChapterIds - chapter.chapterId
                    } else {
                        cacheState.selectedChapterIds
                    },
                    chapters = cacheState.chapters + (chapter.chapterId to phase),
                )
            }
        } catch (cancelled: CancellationException) {
            if (cacheRunCurrent(generation, context.book)) markWorkingChaptersCancelled()
            throw cancelled
        } finally {
            if (cacheJob === job) cacheJob = null
        }
    }

    private fun cancelChapterCache() {
        if (!cacheState.working) return
        cacheGeneration += 1
        cacheJob?.cancel()
        cacheJob = null
        markWorkingChaptersCancelled()
    }

    private fun markWorkingChaptersCancelled() {
        cacheState = cacheState.copy(
            chapters = cacheState.chapters.mapValues { (_, phase) ->
                when (phase) {
                    DetailChapterCachePhase.QUEUED, DetailChapterCachePhase.CACHING -> DetailChapterCachePhase.CANCELLED
                    else -> phase
                }
            },
        )
    }

    private fun cacheContext(): DetailCacheContext? {
        val book = selectedBook ?: run {
            clearCacheState()
            return null
        }
        val directory = (directoryState as? SourceBookState.Content)?.value
            ?.takeIf { it.bookIdentity == book.identity } ?: run {
            invalidateCacheIfBookChanged()
            return null
        }
        if (cacheIdentity != null && cacheIdentity != book.identity) clearCacheState()
        cacheIdentity = book.identity
        return DetailCacheContext(book, directory)
    }

    private fun cacheRunCurrent(generation: Long, book: SourceBookSummary): Boolean =
        cacheGeneration == generation && cacheIdentity == book.identity && selectedBook?.identity == book.identity

    private fun invalidateCacheIfBookChanged() {
        if (cacheIdentity != null && cacheIdentity != selectedBook?.identity) clearCacheState()
    }

    private fun clearCacheState() {
        cacheGeneration += 1
        cacheJob?.cancel()
        cacheJob = null
        cacheIdentity = null
        cacheState = DetailCacheState()
    }

    private data class DetailCacheContext(
        val book: SourceBookSummary,
        val directory: SourceDirectory,
    )

    private suspend fun mutateRemote(
        operation: DetailMutationOperation,
        block: suspend () -> RemoteMutationUiResult,
    ) {
        if (mutation?.phase == DetailMutationPhase.WORKING) return
        retireLoadMutation()
        mutation = DetailMutationStatus(operation, DetailMutationPhase.WORKING)
        val result = try {
            block()
        } catch (cancelled: CancellationException) {
            abandonMutation(operation)
            throw cancelled
        } catch (_: Exception) {
            RemoteMutationUiResult.Failure("remote-write-failed")
        }
        onLibraryChanged()
        mutation = when (result) {
            RemoteMutationUiResult.Confirmed -> DetailMutationStatus(operation, DetailMutationPhase.SUCCESS)
            RemoteMutationUiResult.Unresolved ->
                DetailMutationStatus(operation, DetailMutationPhase.ERROR, "remote-result-unresolved")
            RemoteMutationUiResult.Cancelled ->
                DetailMutationStatus(operation, DetailMutationPhase.ERROR, "remote-operation-cancelled")
            is RemoteMutationUiResult.Failure ->
                DetailMutationStatus(operation, DetailMutationPhase.ERROR, result.safeCode)
        }
    }

    private suspend fun mutate(operation: DetailMutationOperation, block: suspend () -> Unit) {
        if (mutation?.phase == DetailMutationPhase.WORKING) return
        retireLoadMutation()
        mutation = DetailMutationStatus(operation, DetailMutationPhase.WORKING)
        try {
            block()
            onLibraryChanged()
            mutation = DetailMutationStatus(operation, DetailMutationPhase.SUCCESS)
        } catch (cancelled: CancellationException) {
            abandonMutation(operation)
            throw cancelled
        } catch (_: Exception) {
            mutation = DetailMutationStatus(operation, DetailMutationPhase.ERROR, "local-write-failed")
        }
    }

    private fun isCurrent(generation: Long, book: SourceBookSummary): Boolean =
        generation == requestGeneration && selectedBook?.identity == book.identity

    private fun retireLoadMutation() {
        val token = loadMutation ?: return
        loadMutation = null
        if (mutation?.operation == token.operation && mutation?.phase == DetailMutationPhase.WORKING) {
            mutation = null
        }
    }

    private fun abandonLoadMutation(token: DetailLoadMutationToken?) {
        if (token == null || loadMutation != token) return
        loadMutation = null
        if (mutation?.operation == token.operation && mutation?.phase == DetailMutationPhase.WORKING) {
            mutation = null
        }
    }

    private fun completeLoadMutation(token: DetailLoadMutationToken?, status: DetailMutationStatus) {
        if (token == null || loadMutation != token) return
        loadMutation = null
        if (mutation?.operation == token.operation && mutation?.phase == DetailMutationPhase.WORKING) {
            mutation = status
        }
    }

    private fun abandonMutation(operation: DetailMutationOperation) {
        if (mutation?.operation == operation && mutation?.phase == DetailMutationPhase.WORKING) {
            mutation = null
        }
    }

    private data class DetailLoadMutationToken(
        val generation: Long,
        val identity: org.tsuyomi.shared.model.BookIdentity,
        val operation: DetailMutationOperation,
    )

    enum class Command {
        ADD_TO_LIBRARY,
        REMOVE_FROM_LIBRARY,
        CACHE_DETAIL,
        REFRESH_DETAIL,
    }

    companion object {
        internal const val UnreadOnlyKey = "source.detail.unreadOnly"
        internal const val DescendingKey = "source.detail.descending"
        internal const val CommandKey = "source.detail.command"
        internal const val CommandSequenceKey = "source.detail.commandSequence"
        private const val TagEditorOpenKey = "source.detail.tagEditorOpen"
        private const val TagDraftKey = "source.detail.tagDraft"
        private const val MaxTagLength = 64
    }
}

internal data class SourceReaderLoad(
    val document: ReaderDocument? = null,
    val restoredLocator: ReaderLocator? = null,
    val failure: SourceException? = null,
)

@Stable
internal class SourceReaderRouteOwner(
    private val flow: SourceFlowController,
    private val savedState: SavedStateHandle,
) {
    private val preparedResume = flow.consumePreparedResumeLoad()
    var document: ReaderDocument? by mutableStateOf(preparedResume?.document)
        private set
    var loading: Boolean by mutableStateOf(false)
        private set
    var failure: SourceException? by mutableStateOf(preparedResume?.failure)
        private set
    var restoredLocator: ReaderLocator? by mutableStateOf(preparedResume?.restoredLocator)
        private set
    var restorationGeneration: Long by mutableLongStateOf(0L)
        private set
    var chapters: List<SourceChapter> by mutableStateOf(
        (flow.directoryState as? SourceBookState.Content)?.value?.chapters.orEmpty(),
    )
        private set
    var currentChapter: SourceChapter? by mutableStateOf(flow.selectedChapter)
        private set
    private var requestGeneration = 0L
    var documentGeneration: Long by mutableLongStateOf(0L)
        private set
    private var recordedMountedDocumentGeneration = -1L
    var imageStates: Map<String, CoverUiState> by mutableStateOf(emptyMap())
        private set
    private val imageJobs = mutableMapOf<String, Job>()
    private var imageDocumentId: String? = document?.contentId
    private var pendingLocator: ReaderLocator? = null
    private var prefetchedDocuments = ReaderDocumentCache(capacity = 3)
    private var preloadJob: Job? = null
    private val preloadImageJobs = mutableMapOf<String, Job>()
    private var preloadContext: ReaderPreloadContext? = null
    private var preloadGeneration = 0L
    private var activePackageRevision: String? = null
    private var lastLoadOfflineOnly = false
    suspend fun restore(packageInfo: VerifiedHxpPackage) {
        val packageRevision = packageInfo.packageSha256
        if (activePackageRevision != null && activePackageRevision != packageRevision) {
            cancelPreload()
            prefetchedDocuments = ReaderDocumentCache(capacity = 3)
        }
        activePackageRevision = packageRevision
        flow.restoreFor(SourceRestorationTarget.READER, packageInfo)
        currentChapter = flow.selectedChapter
    }


    suspend fun load(offlineOnly: Boolean = false) {
        val book = flow.selectedBook ?: return
        val chapter = currentChapter ?: flow.selectedChapter ?: return
        val generation = ++requestGeneration
        lastLoadOfflineOnly = offlineOnly
        loading = true
        failure = null
        try {
            val directory = (flow.directoryState as? SourceBookState.Content)
                ?.takeIf { it.value.bookIdentity == book.identity }
                ?: flow.requestDirectory(book, offlineOnly)
            if (!isCurrent(generation, book, chapter)) return
            chapters = (directory as? SourceBookState.Content)?.value?.chapters.orEmpty()
                .ifEmpty { listOf(chapter) }
            val prefetched = prefetchedDocuments.get(
                book.identity.sourceId,
                book.identity.remoteBookId,
                chapter.chapterId,
            )
            val result = flow.requestChapter(
                book = book,
                chapter = chapter,
                offlineOnly = offlineOnly,
                prefetchedDocument = prefetched,
            )
            if (!isCurrent(generation, book, chapter)) return
            applyLoadResult(result, book, chapter, generation)
        } catch (cancelled: CancellationException) {
            if (isCurrent(generation, book, chapter)) loading = false
            throw cancelled
        }
    }
    suspend fun acceptVerifiedChapterResult() {
        val result = flow.consumeVerifiedChapterLoad() ?: return
        val book = flow.selectedBook ?: return
        val chapter = flow.selectedChapter ?: return
        val generation = ++requestGeneration
        currentChapter = chapter
        chapters = (flow.directoryState as? SourceBookState.Content)?.value?.chapters.orEmpty()
            .ifEmpty { listOf(chapter) }
        applyLoadResult(result, book, chapter, generation)
    }


    suspend fun selectChapter(chapter: SourceChapter) {
        val generation = ++requestGeneration
        savedState.remove<String>(PendingBookmarkKey)
        pendingLocator = null
        flow.prepareChapter(chapter)
        if (generation != requestGeneration) return
        currentChapter = chapter
        replaceDocument(null)
        restoredLocator = null
        failure = null
        loading = false
    }

    suspend fun selectBookmark(locator: ReaderLocator) {
        selectLocator(locator, locator.bookmarkPositionKey())
    }

    suspend fun selectLocator(locator: ReaderLocator) {
        selectLocator(locator, bookmarkKey = null)
    }

    private suspend fun selectLocator(locator: ReaderLocator, bookmarkKey: String?) {
        val book = flow.selectedBook ?: return
        if (locator.document.book != book.identity) return
        val generation = ++requestGeneration
        cancelPreload()
        val loaded = document
        if (loaded?.sourceId == book.identity.sourceId && loaded.remoteBookId == book.identity.remoteBookId &&
            loaded.contentId == locator.document.contentId
        ) {
            restoredLocator = locator
            restorationGeneration++
            documentGeneration++
            savedState.remove<String>(PendingBookmarkKey)
            pendingLocator = null
            failure = null
            loading = false
            return
        }
        val chapter = chapters.firstOrNull { it.chapterId == locator.document.contentId }
        if (chapter == null) {
            savedState.remove<String>(PendingBookmarkKey)
            pendingLocator = null
            replaceDocument(null)
            restoredLocator = null
            loading = false
            failure = bookmarkUnavailable()
            return
        }
        if (bookmarkKey == null) {
            savedState.remove<String>(PendingBookmarkKey)
            pendingLocator = locator
        } else {
            savedState[PendingBookmarkKey] = bookmarkKey
            pendingLocator = null
        }
        flow.prepareChapter(chapter)
        if (generation != requestGeneration || flow.selectedBook?.identity != book.identity) return
        currentChapter = chapter
        replaceDocument(null)
        restoredLocator = null
        failure = null
        loading = true
        load()
    }

    private suspend fun applyLoadResult(
        result: SourceReaderLoad,
        book: SourceBookSummary,
        chapter: SourceChapter,
        generation: Long,
    ) {
        val bookmarkKey = savedState.get<String>(PendingBookmarkKey)
        val bookmark = if (bookmarkKey != null && result.document != null) {
            flow.bookmarks(book.identity).firstOrNull { it.bookmarkPositionKey() == bookmarkKey }
        } else null
        val sessionLocator = pendingLocator?.takeIf { locator ->
            result.document != null && locator.document.book == book.identity &&
                locator.document.contentId == result.document.contentId
        }
        val pendingTarget = bookmarkKey != null || pendingLocator != null
        val targetLocator = bookmark ?: sessionLocator
        if (!isCurrent(generation, book, chapter)) return
        if (pendingTarget && result.document != null && targetLocator == null) {
            failure = bookmarkUnavailable()
            loading = false
            return
        }
        result.document?.let(prefetchedDocuments::put)
        replaceDocument(result.document)
        restoredLocator = targetLocator ?: result.restoredLocator
        if (result.document != null) {
            restorationGeneration++
            savedState.remove<String>(PendingBookmarkKey)
            pendingLocator = null
        }
        failure = result.failure
        loading = false
    }

    private fun bookmarkUnavailable() = SourceException(
        SourceErrorCode.EMPTY_SOURCE_RESPONSE,
        SourceDiagnostic("reader-bookmark-unavailable", "reader-bookmark", "bookmark-target-unavailable"),
    )

    fun loadImage(
        block: ReaderBlock.Image,
        repository: CoverRepository?,
        packageRevision: String?,
        credentialRevision: String?,
        scope: CoroutineScope,
        retry: Boolean = false,
        silentFailure: Boolean = false,
    ) {
        val currentDocument = document ?: return
        if (currentDocument.blocks.none { it.blockId == block.blockId && it == block }) return
        if (retry) {
            imageJobs.remove(block.blockId)?.cancel()
            imageStates = imageStates - block.blockId
        } else if (
            imageStates.containsKey(block.blockId) ||
            imageJobs[block.blockId]?.isActive == true
        ) {
            return
        }
        if (repository == null || packageRevision.isNullOrBlank() || credentialRevision.isNullOrBlank()) return
        val chapter = currentChapter ?: return
        val request = CoverRequest(
            sourceId = currentDocument.sourceId,
            packageRevision = packageRevision,
            credentialRevision = credentialRevision,
            transportUrl = block.url,
            referrerUrl = chapter.url,
            targetWidthPx = 1080,
            targetHeightPx = 2400,
            fallback = FallbackSpec(block.altText ?: currentDocument.title, null),
        )
        imageJobs[block.blockId] = scope.launch {
            try {
                repository.observe(request).collect { state ->
                    if (document?.contentId == currentDocument.contentId) {
                        imageStates = if (silentFailure && state is CoverUiState.Failed) {
                            imageStates - block.blockId
                        } else {
                            imageStates + (block.blockId to state)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!silentFailure) throw error
                if (document?.contentId == currentDocument.contentId) imageStates = imageStates - block.blockId
            }
        }
    }

    suspend fun saveProgress(
        locator: ReaderLocator,
        precision: LocatorPrecision,
        expectedDocumentGeneration: Long,
    ) {
        val activeDocument = document ?: return
        val selectedBook = flow.selectedBook ?: return
        if (
            expectedDocumentGeneration != documentGeneration ||
            activeDocument.sourceId != locator.document.sourceId ||
            activeDocument.remoteBookId != locator.document.remoteBookId ||
            activeDocument.contentId != locator.document.contentId ||
            selectedBook.identity != locator.document.book
        ) {
            return
        }
        flow.saveProgress(locator, precision)
    }

    suspend fun recordMountedDocument(mountedDocument: ReaderDocument, expectedDocumentGeneration: Long): Boolean {
        val activeDocument = document ?: return false
        val selectedBook = flow.selectedBook ?: return false
        if (
            expectedDocumentGeneration != documentGeneration ||
            activeDocument != mountedDocument ||
            selectedBook.identity.sourceId != mountedDocument.sourceId ||
            selectedBook.identity.remoteBookId != mountedDocument.remoteBookId
        ) {
            return false
        }
        if (recordedMountedDocumentGeneration == expectedDocumentGeneration) return false
        recordedMountedDocumentGeneration = expectedDocumentGeneration
        var recorded = false
        try {
            recorded = flow.recordReaderVisit(selectedBook.identity)
            return recorded
        } finally {
            if (!recorded && recordedMountedDocumentGeneration == expectedDocumentGeneration) {
                recordedMountedDocumentGeneration = -1L
            }
        }
    }

    fun preloadAdjacent(
        scope: CoroutineScope,
        enabled: Boolean,
        repository: CoverRepository?,
        packageRevision: String?,
        credentialRevision: String?,
        settleDelayMillis: Long = 600L,
    ) {
        val activeDocument = document
        val book = flow.selectedBook
        val chapter = currentChapter
        if (!enabled || lastLoadOfflineOnly || settleDelayMillis < 0L || activeDocument == null || book == null || chapter == null) {
            cancelPreload()
            return
        }
        val expectedDocumentGeneration = documentGeneration
        val context = ReaderPreloadContext(
            documentGeneration = expectedDocumentGeneration,
            sourceId = activeDocument.sourceId,
            remoteBookId = activeDocument.remoteBookId,
            contentId = activeDocument.contentId,
            packageRevision = packageRevision,
            credentialRevision = credentialRevision,
        )
        if (preloadContext == context) return
        cancelPreload()
        preloadContext = context
        val preloadToken = preloadGeneration
        val chapterIndex = chapters.indexOfFirst { it.chapterId == chapter.chapterId }
        val nextChapter = chapterIndex.takeIf { it >= 0 }?.let { chapters.getOrNull(it + 1) }
        preloadJob = scope.launch {
            if (settleDelayMillis > 0L) delay(settleDelayMillis)
            if (!isPreloadCurrent(preloadToken, expectedDocumentGeneration, activeDocument, book, chapter)) return@launch

            activeDocument.blocks.asSequence()
                .filterIsInstance<ReaderBlock.Image>()
                .filter { image ->
                    !imageStates.containsKey(image.blockId) && imageJobs[image.blockId]?.isActive != true
                }
                .take(MaxPrefetchedImages)
                .forEach { image ->
                    loadImage(
                        block = image,
                        repository = repository,
                        packageRevision = packageRevision,
                        credentialRevision = credentialRevision,
                        scope = scope,
                        silentFailure = true,
                    )
                    imageJobs[image.blockId]?.let { preloadImageJobs[image.blockId] = it }
                }

            val adjacent = nextChapter ?: return@launch
            if (prefetchedDocuments.get(book.identity.sourceId, book.identity.remoteBookId, adjacent.chapterId) != null) {
                return@launch
            }
            val prefetched = flow.prefetchChapter(book, adjacent) ?: return@launch
            if (isPreloadCurrent(preloadToken, expectedDocumentGeneration, activeDocument, book, chapter)) {
                prefetchedDocuments.put(prefetched)
            }
        }
    }

    fun dispose() {
        requestGeneration++
        cancelPreload()
        imageJobs.values.forEach(Job::cancel)
        imageJobs.clear()
    }

    private fun replaceDocument(next: ReaderDocument?) {
        if (imageDocumentId != next?.contentId) {
            cancelPreload()
            imageJobs.values.forEach(Job::cancel)
            imageJobs.clear()
            imageStates = emptyMap()
            imageDocumentId = next?.contentId
        }
        document = next
        documentGeneration++
    }

    private fun cancelPreload() {
        preloadGeneration++
        preloadJob?.cancel()
        preloadJob = null
        preloadImageJobs.forEach { (blockId, job) ->
            job.cancel()
            if (imageJobs[blockId] === job) imageJobs.remove(blockId)
            imageStates = imageStates - blockId
        }
        preloadImageJobs.clear()
        preloadContext = null
    }

    private fun isPreloadCurrent(
        preloadToken: Long,
        expectedDocumentGeneration: Long,
        expectedDocument: ReaderDocument,
        expectedBook: SourceBookSummary,
        expectedChapter: SourceChapter,
    ): Boolean = preloadToken == preloadGeneration &&
        expectedDocumentGeneration == documentGeneration &&
        document == expectedDocument &&
        flow.selectedBook?.identity == expectedBook.identity &&
        currentChapter?.chapterId == expectedChapter.chapterId

    private fun isCurrent(
        generation: Long,
        book: SourceBookSummary,
        chapter: SourceChapter,
    ): Boolean = generation == requestGeneration &&
        flow.selectedBook?.identity == book.identity &&
        currentChapter?.chapterId == chapter.chapterId

    private data class ReaderPreloadContext(
        val documentGeneration: Long,
        val sourceId: String,
        val remoteBookId: String,
        val contentId: String,
        val packageRevision: String?,
        val credentialRevision: String?,
    )

    private companion object {
        const val PendingBookmarkKey = "reader-pending-bookmark-position"
        const val MaxPrefetchedImages = 2
    }
}

@Stable
internal class SourceRemoteLibraryRouteOwner(
    private val flow: SourceFlowController,
    private val packageProvider: () -> VerifiedHxpPackage?,
    private val savedState: SavedStateHandle,
) {
    var books by mutableStateOf<List<SourceBookSummary>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var status by mutableStateOf<RemoteLibraryRouteStatus>(RemoteLibraryRouteStatus.Idle)
        private set
    var selectedIds by mutableStateOf(savedState.get<List<String>>(SelectedIdsKey).orEmpty().toSet())
        private set
    var copyConfirmationVisible by mutableStateOf(false)
        private set
    var targets by mutableStateOf<List<RemoteTarget>>(emptyList())
        private set
    var selectedTargetId by mutableStateOf<String?>(null)
        private set
    var unresolvedBookIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var removeConfirmationBook by mutableStateOf<SourceBookSummary?>(null)
        private set
    var moveTargetSelectionBook by mutableStateOf<SourceBookSummary?>(null)
        private set
    var jitPrompt by mutableStateOf<JitWritebackPrompt?>(null)
        private set
    private var pendingOperationBook: SourceBookSummary? = null
    val visibleBooks: List<SourceBookSummary>
        get() = selectedTargetId?.let { targetId -> books.filter { it.remoteTargetId == targetId } } ?: books

    suspend fun restore(sourceId: String) {
        reloadUnresolved(sourceId)
        val snapshot = flow.remoteMirrorSnapshot(sourceId) ?: return
        books = snapshot.books.map { item ->
            SourceBookSummary(
                identity = item.book.identity,
                title = item.book.title,
                author = item.book.author,
                coverUrl = item.book.coverUrl,
                canonicalUrl = item.book.canonicalUrl.orEmpty(),
                remoteTargetId = item.targetId,
            )
        }
        targets = snapshot.targets.map {
            RemoteTarget(it.targetId, it.displayName, it.parentId, it.kind)
        }
        status = if (books.isEmpty()) RemoteLibraryRouteStatus.Empty else RemoteLibraryRouteStatus.Content
    }

    suspend fun refresh() {
        val packageInfo = packageProvider() ?: run {
            status = RemoteLibraryRouteStatus.Failure("source-unavailable")
            return
        }
        loading = true
        status = RemoteLibraryRouteStatus.Loading
        try {
            status = when (val result = flow.pullRemoteLibrary(packageInfo)) {
                is RemoteLibraryPullResult.Success -> {
                    val loadedTargets = flow.listRemoteTargets()
                    if (loadedTargets.isNotEmpty() || targets.isEmpty()) targets = loadedTargets
                    books = flow.saveRemoteMirrorSnapshot(packageInfo.manifest.displayName, result.books, targets)
                    selectedIds = selectedIds.intersect(books.mapTo(hashSetOf(), ::remoteLibrarySelectionId))
                    persistSelection()
                    if (books.isEmpty()) RemoteLibraryRouteStatus.Empty else RemoteLibraryRouteStatus.Content
                }
                RemoteLibraryPullResult.LoginRequired -> RemoteLibraryRouteStatus.LoginRequired
                RemoteLibraryPullResult.VerificationRequired -> RemoteLibraryRouteStatus.VerificationRequired
                RemoteLibraryPullResult.Cancelled -> RemoteLibraryRouteStatus.Cancelled
                is RemoteLibraryPullResult.Failure -> RemoteLibraryRouteStatus.Failure(result.safeCode)
            }
            reloadUnresolved(packageInfo.manifest.sourceId.value)
        } finally {
            loading = false
        }
    }
    fun toggleSelection(book: SourceBookSummary) {
        val selectionId = remoteLibrarySelectionId(book)
        selectedIds = if (selectionId in selectedIds) {
            selectedIds - selectionId
        } else {
            selectedIds + selectionId
        }
        persistSelection()
    }

    fun clearSelection() {
        selectedIds = emptySet()
        persistSelection()
    }

    suspend fun requestCopy(): RemoteLibraryCopyResult? {
        val selected = selectedBooksForCopy()
        if (selected.isEmpty()) return null
        val sourceId = selected.first().identity.sourceId
        if (selected.any { it.identity.sourceId != sourceId }) {
            status = RemoteLibraryRouteStatus.Failure("source-identity-mismatch")
            return null
        }
        if (flow.localCopyConfirmationRequired(sourceId)) {
            copyConfirmationVisible = true
            return null
        }
        return copySelectedBooks(selected)
    }

    fun dismissCopy() {
        copyConfirmationVisible = false
    }

    suspend fun confirmCopy(): RemoteLibraryCopyResult? {
        val selected = selectedBooksForCopy()
        if (selected.isEmpty()) return null
        val sourceId = selected.first().identity.sourceId
        if (!flow.acknowledgeLocalCopyConfirmation(sourceId)) {
            copyConfirmationVisible = false
            status = RemoteLibraryRouteStatus.Failure("copy-confirmation-receipt-failed")
            return null
        }
        return copySelectedBooks(selected)
    }

    private fun selectedBooksForCopy(): List<SourceBookSummary> =
        if (selectedIds.isEmpty()) visibleBooks else books.filter { remoteLibrarySelectionId(it) in selectedIds }
    private suspend fun copySelectedBooks(selected: List<SourceBookSummary>): RemoteLibraryCopyResult {
        val result = flow.copyRemoteLibraryToLocal(selected)
        copyConfirmationVisible = false
        status = RemoteLibraryRouteStatus.Copied(result.total, result.added)
        clearSelection()
        return result
    }

    fun selectTarget(targetId: String?) {
        selectedTargetId = targetId
    }

    suspend fun requestRemove(book: SourceBookSummary) {
        val packageInfo = packageProvider() ?: return
        val policy = packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.REMOVE]
        if (policy == null) return
        if (!flow.writebackAuthorized(book.identity.sourceId, "remove")) {
            jitPrompt = JitWritebackPrompt(
                operation = "remove",
                sourceName = packageInfo.manifest.displayName,
                bookTitle = book.title,
            )
            pendingOperationBook = book
            return
        }
        removeConfirmationBook = book
    }

    fun dismissRemove() {
        removeConfirmationBook = null
    }

    suspend fun confirmRemove(book: SourceBookSummary): RemoteMutationUiResult {
        removeConfirmationBook = null
        val result = flow.removeBookFromWebsite(book)
        if (result is RemoteMutationUiResult.Confirmed) {
            books = books.filterNot { it.identity == book.identity }
            selectedIds = selectedIds - remoteLibrarySelectionId(book)
            persistSelection()
        }
        reloadUnresolved(book.identity.sourceId)
        status = RemoteLibraryRouteStatus.Mutation("remove", result)
        return result
    }

    suspend fun requestMove(book: SourceBookSummary) {
        val packageInfo = packageProvider() ?: return
        val policy = packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.MOVE]
        if (policy == null) return
        if (!flow.writebackAuthorized(book.identity.sourceId, "move")) {
            jitPrompt = JitWritebackPrompt(
                operation = "move",
                sourceName = packageInfo.manifest.displayName,
                bookTitle = book.title,
            )
            pendingOperationBook = book
            return
        }
        moveTargetSelectionBook = book
    }

    fun dismissMove() {
        moveTargetSelectionBook = null
    }

    suspend fun confirmMove(book: SourceBookSummary, targetId: String, targetName: String): RemoteMutationUiResult {
        moveTargetSelectionBook = null
        val result = flow.moveBookOnWebsite(book, targetId, targetName)
        if (result is RemoteMutationUiResult.Confirmed) {
            books = books.map { if (it.identity == book.identity) it.copy(remoteTargetId = targetId) else it }
        }
        reloadUnresolved(book.identity.sourceId)
        status = RemoteLibraryRouteStatus.Mutation("move", result, targetName)
        return result
    }

    suspend fun confirmJitPrompt() {
        val prompt = jitPrompt ?: return
        val packageInfo = packageProvider() ?: return
        val sourceId = packageInfo.manifest.sourceId.value
        flow.authorizeWriteback(sourceId, prompt.operation, true)
        val book = pendingOperationBook
        jitPrompt = null
        pendingOperationBook = null
        if (book != null) {
            if (prompt.operation == "remove") {
                removeConfirmationBook = book
            } else if (prompt.operation == "move") {
                moveTargetSelectionBook = book
            }
        }
    }

    fun dismissJitPrompt() {
        jitPrompt = null
        pendingOperationBook = null
    }

    private suspend fun reloadUnresolved(sourceId: String) {
        unresolvedBookIds = flow.unresolvedReconciliations()
            .asSequence()
            .filter { it.sourceId == sourceId }
            .mapTo(linkedSetOf()) { it.remoteBookId }
    }

    private fun persistSelection() {
        savedState[SelectedIdsKey] = selectedIds.toList()
    }

    companion object {
        const val SelectedIdsKey = "source.remote-library.selected"
    }
}

internal sealed interface RemoteLibraryRouteStatus {
    data object Idle : RemoteLibraryRouteStatus
    data object Loading : RemoteLibraryRouteStatus
    data object Content : RemoteLibraryRouteStatus
    data object Empty : RemoteLibraryRouteStatus
    data object LoginRequired : RemoteLibraryRouteStatus
    data object VerificationRequired : RemoteLibraryRouteStatus
    data object Cancelled : RemoteLibraryRouteStatus
    data class Failure(val safeCode: String) : RemoteLibraryRouteStatus
    data class Copied(val total: Int, val added: Int) : RemoteLibraryRouteStatus
    data class Mutation(
        val operation: String,
        val result: RemoteMutationUiResult,
        val targetName: String? = null,
    ) : RemoteLibraryRouteStatus
}

@Composable
internal fun rememberSourceRemoteLibraryRouteOwner(
    entry: NavBackStackEntry,
    flow: SourceFlowController,
    packageProvider: () -> VerifiedHxpPackage?,
): SourceRemoteLibraryRouteOwner = remember(entry, flow) {
    SourceRemoteLibraryRouteOwner(flow, packageProvider, entry.savedStateHandle)
}

private class SourceSearchRouteOwnerHolder(
    flow: SourceFlowController,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private var boundFlow = flow
    private var owner = SourceSearchRouteOwner(flow, savedState)

    fun forFlow(flow: SourceFlowController): SourceSearchRouteOwner {
        if (boundFlow !== flow) {
            // A replaced backing runtime cannot retain an in-flight source operation.
            val retainedState = if (owner.state == SearchResultState.Loading) {
                SearchResultState.Failure(
                    SourceErrorCode.EXTENSION_CANCELLED,
                    SourceDiagnostic("search-restored", "search-restore", "source-session-interrupted"),
                )
            } else owner.state
            owner = SourceSearchRouteOwner(flow, savedState, retainedState)
            boundFlow = flow
        }
        return owner
    }
}

private class SourceSearchRouteOwnerHolderFactory(
    private val flow: SourceFlowController,
    private val savedState: SavedStateHandle,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == SourceSearchRouteOwnerHolder::class.java)
        @Suppress("UNCHECKED_CAST")
        return SourceSearchRouteOwnerHolder(flow, savedState) as T
    }
}

internal fun sourceSearchRouteOwner(
    entry: NavBackStackEntry,
    flow: SourceFlowController,
): SourceSearchRouteOwner = ViewModelProvider(
    entry,
    SourceSearchRouteOwnerHolderFactory(flow, entry.savedStateHandle),
).get(SourceSearchRouteOwnerHolder::class.java).forFlow(flow)

@Composable
internal fun rememberSourceSearchRouteOwner(
    entry: NavBackStackEntry,
    flow: SourceFlowController,
): SourceSearchRouteOwner = remember(entry, flow) {
    sourceSearchRouteOwner(entry, flow)
}

@Composable
internal fun rememberSourceDetailRouteOwner(
    entry: NavBackStackEntry,
    flow: SourceFlowController,
    sourceRouteOwner: SourceRouteOwner,
): SourceDetailRouteOwner {
    val notifyLibraryChanged = rememberUpdatedState(sourceRouteOwner::notifyLibraryChanged)
    val owner = remember(entry, flow) {
        android.util.Log.i("TsuyomiRoute", "detail-owner create entry=${entry.hashCode()}")
        SourceDetailRouteOwner(flow, entry.savedStateHandle) { notifyLibraryChanged.value.invoke() }
    }
    DisposableEffect(owner) {
        onDispose {
            android.util.Log.i("TsuyomiRoute", "detail-owner dispose entry=${entry.hashCode()}")
            owner.dispose()
        }
    }
    LaunchedEffect(owner, owner.directoryState, owner.selectedBook?.identity) {
        owner.refreshCachedChapterStatuses()
    }
    return owner
}

@Composable
internal fun rememberSourceReaderRouteOwner(
    entry: NavBackStackEntry,
    flow: SourceFlowController,
): SourceReaderRouteOwner {
    val owner = remember(entry, flow) { SourceReaderRouteOwner(flow, entry.savedStateHandle) }
    DisposableEffect(owner) { onDispose(owner::dispose) }
    return owner
}
