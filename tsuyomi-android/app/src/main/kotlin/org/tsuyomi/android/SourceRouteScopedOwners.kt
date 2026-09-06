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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import org.tsuyomi.feature.library.JitWritebackPrompt
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
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
 * A source session belongs to the Browse back-stack entry. Screen state belongs to the route entry
 * that renders it. These owners deliberately expose typed route operations rather than the session
 * coordinator, so a route cannot accidentally acquire another route's mutable state.
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
        if (authorSearch) {
            submitAuthor(query, offlineOnly)
            return
        }
        flow.updateQuery(query)
        if (query.isNotBlank()) state = SearchResultState.Loading
        flow.search(offlineOnly)
        state = flow.searchState
    }

    suspend fun submitAuthor(author: String) {
        submitAuthor(author, offlineOnly = false)
    }

    private suspend fun submitAuthor(author: String, offlineOnly: Boolean) {
        query = author
        authorSearch = true
        savedState[QueryKey] = query
        savedState[AuthorSearchKey] = true
        if (author.isNotBlank()) state = SearchResultState.Loading
        flow.authorSearch(author, offlineOnly)
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
        private const val AuthorSearchKey = "source.search.author"
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
                reconciliation = flow.remoteLibrary.selectedBookReconciliation?.name,
                remoteRemoveEnabled = flow.remoteLibrary.selectedBookRemoveWritesRemote,
                remoteMoveEnabled = flow.remoteLibrary.selectedBookMoveWritesRemote,
            )
        } ?: DetailLocalState(
            reconciliation = flow.remoteLibrary.selectedBookReconciliation?.name,
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

    suspend fun removeSelectedBookFromWebsite() = mutate(DetailMutationOperation.REMOVE_FROM_REMOTE) {
        val result = flow.removeSelectedBookFromWebsite()
        if (result is RemoteMutationUiResult.Failure) error(result.safeCode)
    }

    suspend fun moveSelectedBookOnWebsite(targetId: String, targetName: String) = mutate(DetailMutationOperation.MOVE_REMOTE) {
        val result = flow.moveSelectedBookOnWebsite(targetId, targetName)
        if (result is RemoteMutationUiResult.Failure) error(result.safeCode)
    }

    suspend fun retryRemoteReconciliation() = mutate(DetailMutationOperation.RECONCILE_RETRY) {
        val result = flow.retryRemoteMutation()
        if (result is RemoteMutationUiResult.Failure) error(result.safeCode)
    }

    suspend fun acknowledgeRemoteReconciliation() = mutate(DetailMutationOperation.RECONCILE_ACKNOWLEDGE) {
        val book = selectedBook ?: return@mutate
        check(flow.acknowledgeUnresolved(book.identity)) { "Acknowledge failed" }
    }

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
                    val loadedTargets = runCatching { flow.listRemoteTargets() }.getOrDefault(emptyList())
                    if (loadedTargets.isNotEmpty() || targets.isEmpty()) targets = loadedTargets
                    books = flow.saveRemoteMirrorSnapshot(packageInfo.manifest.displayName, result.books, targets)
                    selectedIds = selectedIds.intersect(books.mapTo(hashSetOf(), SourceBookSummary::canonicalUrl))
                    persistSelection()
                    unresolvedBookIds = runCatching {
                        flow.unresolvedReconciliations().map { it.remoteBookId }.toSet()
                    }.getOrDefault(emptySet())
                    if (books.isEmpty()) RemoteLibraryRouteStatus.Empty else RemoteLibraryRouteStatus.Content
                }
                RemoteLibraryPullResult.LoginRequired -> RemoteLibraryRouteStatus.LoginRequired
                RemoteLibraryPullResult.VerificationRequired -> RemoteLibraryRouteStatus.VerificationRequired
                RemoteLibraryPullResult.Cancelled -> RemoteLibraryRouteStatus.Cancelled
                is RemoteLibraryPullResult.Failure -> RemoteLibraryRouteStatus.Failure(result.safeCode)
            }
        } finally {
            loading = false
        }
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
            selectedIds = selectedIds - book.canonicalUrl
            persistSelection()
        }
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

private class SourceSearchRouteOwnerHolder(
    flow: SourceFlowController,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private var boundFlow = flow
    private var owner = SourceSearchRouteOwner(flow, savedState)

    fun forFlow(flow: SourceFlowController): SourceSearchRouteOwner {
        if (boundFlow !== flow) {
            // The back-stack ViewModel survives Activity recreation; the source session does not.
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
        SourceDetailRouteOwner(flow, entry.savedStateHandle) { notifyLibraryChanged.value.invoke() }
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
