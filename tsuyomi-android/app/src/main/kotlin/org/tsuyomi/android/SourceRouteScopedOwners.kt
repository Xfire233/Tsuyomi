/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.feature.book.DetailLocalState
import org.tsuyomi.feature.book.DetailMutationOperation
import org.tsuyomi.feature.book.DetailMutationPhase
import org.tsuyomi.feature.book.DetailMutationStatus
import kotlinx.coroutines.flow.StateFlow
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.feature.search.SearchResultState
import org.tsuyomi.feature.search.SearchLayout
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

/**
 * A source session belongs to the Browse back-stack entry. Screen state belongs to the route entry
 * that renders it. These owners deliberately expose typed route operations rather than the session
 * coordinator, so a route cannot accidentally acquire another route's mutable state.
 */
@Stable
internal class SourceSearchRouteOwner(
    private val flow: SourceFlowController,
    private val savedState: SavedStateHandle,
) {
    var query by mutableStateOf(savedState[QueryKey] ?: "")
        private set
    var state: SearchResultState by mutableStateOf(SearchResultState.Idle)
        private set
    val layout: StateFlow<SearchLayout> = savedState.getStateFlow(LayoutKey, SearchLayout.LIST)

    fun updateQuery(value: String) {
        query = value.take(MaxQueryLength)
        savedState[QueryKey] = query
    }

    fun cycleLayout() {
        savedState[LayoutKey] = layout.value.next()
    }

    suspend fun restore(packageInfo: VerifiedHxpPackage) {
        flow.restoreFor(SourceRestorationTarget.SEARCH, packageInfo)
    }

    suspend fun submit(offlineOnly: Boolean = false) {
        flow.updateQuery(query)
        flow.search(offlineOnly)
        state = flow.searchState
    }

    fun acceptVerifiedPageResult() {
        state = flow.searchState
    }

    suspend fun select(book: SourceBookSummary) {
        flow.prepareBook(book)
    }

    companion object {
        private const val QueryKey = "source.search.query"
        private const val MaxQueryLength = 100
        internal const val LayoutKey = "source.search.layout"
    }
}

@Stable
internal class SourceDetailRouteOwner(
    private val flow: SourceFlowController,
    private val savedState: SavedStateHandle,
    private val onLibraryChanged: suspend () -> Unit = {},
) {
    var state: SourceBookState<SourceBookDetail> by mutableStateOf(SourceBookState.Loading)
        private set
    var directoryState: SourceBookState<SourceDirectory> by mutableStateOf(SourceBookState.Loading)
        private set
    var mutation: DetailMutationStatus? by mutableStateOf(null)
        private set
    val unreadOnly: StateFlow<Boolean> = savedState.getStateFlow(UnreadOnlyKey, false)
    val descending: StateFlow<Boolean> = savedState.getStateFlow(DescendingKey, false)
    private var requestGeneration = 0L

    val selectedBook: SourceBookSummary?
        get() = flow.selectedBook
    val selectedChapter: SourceChapter?
        get() = flow.selectedChapter
    val localState: DetailLocalState
        get() = flow.remoteLibrary.selectedLibraryEntry?.let { entry ->
            DetailLocalState(
                inLibrary = true,
                rating = entry.rating,
                localTags = entry.localTags.toList(),
                readLater = entry.readLater,
                progressChapterId = entry.progress?.locator?.document?.contentId,
                progressChapterFraction = entry.progress?.locator?.chapterProgress,
            )
        } ?: DetailLocalState()

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
        loadAll()
    }


    suspend fun loadAll(
        offlineOnly: Boolean = false,
        operation: DetailMutationOperation? = null,
    ) {
        val book = selectedBook ?: return
        val libraryBookBefore = flow.remoteLibrary.selectedLibraryEntry?.book
        val generation = ++requestGeneration
        operation?.let { mutation = DetailMutationStatus(it, DetailMutationPhase.WORKING) }
        state = SourceBookState.Loading
        directoryState = SourceBookState.Loading
        val nextDetail = flow.requestDetail(book, offlineOnly)
        if (!isCurrent(generation, book)) return
        state = nextDetail
        val nextDirectory = flow.requestDirectory(book, offlineOnly)
        if (!isCurrent(generation, book)) return
        directoryState = nextDirectory
        if (libraryBookBefore != flow.remoteLibrary.selectedLibraryEntry?.book) onLibraryChanged()
        operation?.let {
            mutation = if (nextDetail is SourceBookState.Failure || nextDirectory is SourceBookState.Failure) {
                DetailMutationStatus(it, DetailMutationPhase.ERROR, "source-read-failed")
            } else {
                DetailMutationStatus(it, DetailMutationPhase.SUCCESS)
            }
        }
    }
    suspend fun acceptVerifiedDetailResult() {
        state = flow.detailState
        if (state is SourceBookState.Content && flow.remoteLibrary.selectedLibraryEntry != null) {
            onLibraryChanged()
        }
    }

    suspend fun resumeDirectoryAfterVerifiedDetail() {
        if (directoryState !is SourceBookState.Loading) return
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
            Command.ADD_TO_LIBRARY -> mutate(DetailMutationOperation.ADD_TO_LIBRARY) { flow.addSelectedBook() }
            Command.REMOVE_FROM_LIBRARY -> mutate(DetailMutationOperation.REMOVE_FROM_LIBRARY) {
                check(flow.removeSelectedBook()) { "Book is not in library" }
            }
            Command.CACHE_DETAIL -> loadAll(operation = DetailMutationOperation.CACHE_DETAIL)
            Command.REFRESH_DETAIL -> loadAll(operation = DetailMutationOperation.REFRESH_DETAIL)
        }
    }

    suspend fun setRating(rating: Int?) = mutate(DetailMutationOperation.SET_RATING) {
        flow.setSelectedRating(rating)
    }

    suspend fun addTag(tag: String) = mutate(DetailMutationOperation.ADD_TAG) {
        flow.addSelectedLocalTag(tag)
    }

    suspend fun toggleReadLater() = mutate(DetailMutationOperation.TOGGLE_READ_LATER) {
        flow.toggleSelectedReadLater()
    }

    suspend fun selectChapter(chapter: SourceChapter) = flow.prepareChapter(chapter)

    fun dispose() {
        requestGeneration++
    }

    private suspend fun mutate(operation: DetailMutationOperation, block: suspend () -> Unit) {
        if (mutation?.phase == DetailMutationPhase.WORKING) return
        mutation = DetailMutationStatus(operation, DetailMutationPhase.WORKING)
        try {
            block()
            onLibraryChanged()
            mutation = DetailMutationStatus(operation, DetailMutationPhase.SUCCESS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutation = DetailMutationStatus(operation, DetailMutationPhase.ERROR, "local-write-failed")
        }
    }

    private fun isCurrent(generation: Long, book: SourceBookSummary): Boolean =
        generation == requestGeneration && selectedBook?.identity == book.identity

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
    var chapters: List<SourceChapter> by mutableStateOf(
        (flow.directoryState as? SourceBookState.Content)?.value?.chapters.orEmpty(),
    )
        private set
    var currentChapter: SourceChapter? by mutableStateOf(flow.selectedChapter)
        private set
    private var requestGeneration = 0L
    var imageStates: Map<String, CoverUiState> by mutableStateOf(emptyMap())
        private set
    private val imageJobs = mutableMapOf<String, Job>()
    private var imageDocumentId: String? = document?.contentId
    suspend fun restore(packageInfo: VerifiedHxpPackage) {
        flow.restoreFor(SourceRestorationTarget.READER, packageInfo)
        currentChapter = flow.selectedChapter
    }


    suspend fun load(offlineOnly: Boolean = false) {
        val book = flow.selectedBook ?: return
        val chapter = currentChapter ?: flow.selectedChapter ?: return
        val generation = ++requestGeneration
        loading = true
        failure = null
        val directory = (flow.directoryState as? SourceBookState.Content)
            ?.takeIf { it.value.bookIdentity == book.identity }
            ?: flow.requestDirectory(book, offlineOnly)
        if (!isCurrent(generation, book, chapter)) return
        chapters = (directory as? SourceBookState.Content)?.value?.chapters.orEmpty()
            .ifEmpty { listOf(chapter) }
        val result = flow.requestChapter(book, chapter, offlineOnly)
        if (!isCurrent(generation, book, chapter)) return
        replaceDocument(result.document)
        restoredLocator = result.restoredLocator
        failure = result.failure
        loading = false
    }
    fun acceptVerifiedChapterResult() {
        val result = flow.consumeVerifiedChapterLoad() ?: return
        currentChapter = flow.selectedChapter
        chapters = (flow.directoryState as? SourceBookState.Content)?.value?.chapters.orEmpty()
            .ifEmpty { currentChapter?.let(::listOf).orEmpty() }
        replaceDocument(result.document)
        restoredLocator = result.restoredLocator
        failure = result.failure
        loading = false
    }


    suspend fun selectChapter(chapter: SourceChapter) {
        requestGeneration++
        flow.prepareChapter(chapter)
        currentChapter = chapter
        replaceDocument(null)
        restoredLocator = null
        failure = null
        loading = false
    }

    fun loadImage(
        block: ReaderBlock.Image,
        repository: CoverRepository?,
        packageRevision: String?,
        credentialRevision: String?,
        scope: CoroutineScope,
        retry: Boolean = false,
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
            repository.observe(request).collect { state ->
                if (document?.contentId == currentDocument.contentId) {
                    imageStates = imageStates + (block.blockId to state)
                }
            }
        }
    }

    suspend fun saveProgress(locator: ReaderLocator, precision: LocatorPrecision) {
        if (document?.contentId == locator.document.contentId) flow.saveProgress(locator, precision)
    }

    fun dispose() {
        requestGeneration++
        imageJobs.values.forEach(Job::cancel)
        imageJobs.clear()
    }

    private fun replaceDocument(next: ReaderDocument?) {
        if (imageDocumentId != next?.contentId) {
            imageJobs.values.forEach(Job::cancel)
            imageJobs.clear()
            imageStates = emptyMap()
            imageDocumentId = next?.contentId
        }
        document = next
    }

    private fun isCurrent(
        generation: Long,
        book: SourceBookSummary,
        chapter: SourceChapter,
    ): Boolean = generation == requestGeneration &&
        flow.selectedBook?.identity == book.identity &&
        currentChapter?.chapterId == chapter.chapterId
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

    suspend fun refresh() {
        val packageInfo = packageProvider() ?: run {
            status = RemoteLibraryRouteStatus.Failure("source-unavailable")
            return
        }
        loading = true
        status = RemoteLibraryRouteStatus.Loading
        status = when (val result = flow.pullRemoteLibrary(packageInfo)) {
            is RemoteLibraryPullResult.Success -> {
                books = result.books
                selectedIds = selectedIds.intersect(books.mapTo(hashSetOf(), SourceBookSummary::canonicalUrl))
                persistSelection()
                if (books.isEmpty()) RemoteLibraryRouteStatus.Empty else RemoteLibraryRouteStatus.Content
            }
            RemoteLibraryPullResult.LoginRequired -> RemoteLibraryRouteStatus.LoginRequired
            RemoteLibraryPullResult.VerificationRequired -> RemoteLibraryRouteStatus.VerificationRequired
            RemoteLibraryPullResult.Cancelled -> RemoteLibraryRouteStatus.Cancelled
            is RemoteLibraryPullResult.Failure -> RemoteLibraryRouteStatus.Failure(result.safeCode)
        }
        loading = false
    }
    fun toggleSelection(book: SourceBookSummary) {
        selectedIds = if (book.canonicalUrl in selectedIds) {
            selectedIds - book.canonicalUrl
        } else {
            selectedIds + book.canonicalUrl
        }
        persistSelection()
    }

    fun clearSelection() {
        selectedIds = emptySet()
        persistSelection()
    }

    fun requestCopy() {
        if (books.isNotEmpty()) copyConfirmationVisible = true
    }

    fun dismissCopy() {
        copyConfirmationVisible = false
    }

    suspend fun confirmCopy(): RemoteLibraryCopyResult {
        val selected = if (selectedIds.isEmpty()) books else books.filter { it.canonicalUrl in selectedIds }
        val result = flow.copyRemoteLibraryToLocal(selected)
        copyConfirmationVisible = false
        status = RemoteLibraryRouteStatus.Copied(result.total, result.added)
        clearSelection()
        return result
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
}

@Composable
internal fun rememberSourceRemoteLibraryRouteOwner(
    entry: NavBackStackEntry,
    flow: SourceFlowController,
    packageProvider: () -> VerifiedHxpPackage?,
): SourceRemoteLibraryRouteOwner = remember(entry, flow) {
    SourceRemoteLibraryRouteOwner(flow, packageProvider, entry.savedStateHandle)
}

@Composable
internal fun rememberSourceSearchRouteOwner(
    entry: NavBackStackEntry,
    flow: SourceFlowController,
): SourceSearchRouteOwner = remember(entry, flow) {
    SourceSearchRouteOwner(flow, entry.savedStateHandle)
}

@Composable
internal fun rememberSourceDetailRouteOwner(
    entry: NavBackStackEntry,
    flow: SourceFlowController,
    sourceRouteOwner: SourceRouteOwner,
): SourceDetailRouteOwner {
    val owner = remember(entry, flow, sourceRouteOwner) {
        SourceDetailRouteOwner(flow, entry.savedStateHandle, sourceRouteOwner::notifyLibraryChanged)
    }
    DisposableEffect(owner) { onDispose(owner::dispose) }
    return owner
}

@Composable
internal fun rememberSourceReaderRouteOwner(
    entry: NavBackStackEntry,
    flow: SourceFlowController,
): SourceReaderRouteOwner {
    val owner = remember(entry, flow) { SourceReaderRouteOwner(flow) }
    DisposableEffect(owner) { onDispose(owner::dispose) }
    return owner
}
