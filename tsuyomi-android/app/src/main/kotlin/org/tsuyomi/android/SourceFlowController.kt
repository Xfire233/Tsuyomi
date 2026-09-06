/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.Closeable
import java.time.Instant
import org.tsuyomi.core.database.LibraryBook
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.ReadingProgress
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.feature.search.SearchResultState
import org.tsuyomi.feature.browse.SourceHomeFailure
import org.tsuyomi.feature.browse.SourceHomeViewState
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceHomePage
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

internal class SourceFlowController(
    context: Context,
    private val library: RoomLibraryRepository,
    private val snapshotStore: SourceFlowSnapshotStore,
    private val normalizedStore: NormalizedSourceStore = NormalizedSourceStore(context.applicationContext),
    directActionTokens: DirectActionTokenRegistry = DirectActionTokenRegistry(),
    openSession: suspend (VerifiedHxpPackage) -> SourceFlowSession =
        SourceSessionOwner.extensionClientFactory(context, directActionTokens),
) : Closeable {
    private val sessionOwner = SourceSessionOwner(directActionTokens, openSession)

    val remoteLibrary = SourceRemoteLibraryCoordinator(context, library, sessionOwner)
    val home = SourceHomeController()
    var query: String by mutableStateOf("")
        private set
    var searchState: SearchResultState by mutableStateOf(SearchResultState.Idle)
        private set
    var authorSearch: Boolean = false
        private set
    var detailState: SourceBookState<SourceBookDetail> by mutableStateOf(SourceBookState.Loading)
        private set
    var directoryState: SourceBookState<SourceDirectory> by mutableStateOf(SourceBookState.Loading)
        private set
    val homeState: SourceHomeViewState
        get() = home.state

    var selectedBook: SourceBookSummary? = null
        private set
    var selectedChapter: SourceChapter? = null
        private set
    var completedChapterIds: Set<String> by mutableStateOf(emptySet())
        private set
    private var verifiedChapterLoad: SourceReaderLoad? = null
    private var preparedResumeLoad: SourceReaderLoad? = null

    private var searchInvocation = 0L
    suspend fun chapterVerifiedPageRequestUrl(): String? {
        val source = sessionOwner.requireClientOrNull() ?: return null
        val book = selectedBook ?: return null
        val chapter = selectedChapter ?: return null
        return runCatching { source.chapterRequestUrl(chapter, book.identity.remoteBookId) }.getOrNull()
    }

    suspend fun chapterVerifiedPage(snapshot: CapturedVerifiedPage): Boolean {
        val source = sessionOwner.requireClientOrNull() ?: return false
        val book = selectedBook ?: return false
        val chapter = selectedChapter ?: return false
        val restored = library.progress(book.identity)?.locator
            ?.takeIf { it.document.contentId == chapter.chapterId }
        verifiedChapterLoad = try {
            SourceReaderLoad(
                document = source.chapterVerifiedPage(chapter, book.identity.remoteBookId, snapshot).also { document ->
                    runCatching { normalizedStore.writeDocument(book.identity, document) }
                },
                restoredLocator = restored,
            )
        } catch (error: SourceException) {
            SourceReaderLoad(failure = error, restoredLocator = restored)
        }
        return verifiedChapterLoad?.document != null
    }

    fun chapterVerifiedPageDiagnostic(): SourceDiagnostic? = verifiedChapterLoad?.failure?.diagnostic

    fun consumeVerifiedChapterLoad(): SourceReaderLoad? =
        verifiedChapterLoad.also { verifiedChapterLoad = null }

    fun consumePreparedResumeLoad(): SourceReaderLoad? =
        preparedResumeLoad.also { preparedResumeLoad = null }


    suspend fun open(packageInfo: VerifiedHxpPackage) {
        val result = sessionOwner.open(packageInfo) {
            resetReadingState()
            remoteLibrary.reset()
        }
        when (result) {
            SourceSessionOpenResult.ALREADY_OPEN -> return
            SourceSessionOpenResult.OPENED -> searchState = SearchResultState.Idle
            SourceSessionOpenResult.PACKAGE_CHANGED -> {
                searchState = SearchResultState.Idle
            }
        }
    }
    suspend fun pullRemoteLibrary(packageInfo: VerifiedHxpPackage): RemoteLibraryPullResult {
        open(packageInfo)
        return remoteLibrary.pull(packageInfo)
    }

    internal suspend fun pullRemoteLibrary(
        packageInfo: VerifiedHxpPackage,
        aggregateLimitBytes: Long,
    ): RemoteLibraryPullResult {
        open(packageInfo)
        return remoteLibrary.pull(packageInfo, aggregateLimitBytes)
    }

    suspend fun copyRemoteLibraryToLocal(books: Collection<SourceBookSummary>): RemoteLibraryCopyResult =
        remoteLibrary.copyToLocal(books)

    suspend fun localCopyConfirmationRequired(sourceId: String): Boolean =
        library.sourceRemotePolicy(sourceId)?.firstImportPromptDismissed != true

    suspend fun acknowledgeLocalCopyConfirmation(sourceId: String): Boolean {
        val policy = library.sourceRemotePolicy(sourceId) ?: return false
        return policy.firstImportPromptDismissed ||
            library.dismissFirstRemoteImportPrompt(sourceId, policy.capabilitySetFingerprint)
    }

    suspend fun addSelectedBook(importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteLibrary.addLocalBook(selectedBook, importedAt)

    suspend fun addSelectedBookToWebsite(importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteLibrary.addBookToWebsite(selectedBook, importedAt)

    suspend fun retrySelectedBookRemoteAdd(importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteLibrary.retryBook(selectedBook, importedAt)

    suspend fun removeSelectedBook(): Boolean = remoteLibrary.removeBook(selectedBook)

    suspend fun removeSelectedBookFromWebsite(importedAt: Instant = Instant.now()): RemoteMutationUiResult =
        remoteLibrary.removeBookFromWebsite(selectedBook, importedAt)

    suspend fun moveSelectedBookOnWebsite(targetId: String, targetName: String, importedAt: Instant = Instant.now()): RemoteMutationUiResult =
        remoteLibrary.moveBookOnWebsite(selectedBook, targetId, targetName, importedAt)

    suspend fun removeBookFromWebsite(book: SourceBookSummary, importedAt: Instant = Instant.now()): RemoteMutationUiResult =
        remoteLibrary.removeBookFromWebsite(book, importedAt)

    suspend fun moveBookOnWebsite(book: SourceBookSummary, targetId: String, targetName: String, importedAt: Instant = Instant.now()): RemoteMutationUiResult =
        remoteLibrary.moveBookOnWebsite(book, targetId, targetName, importedAt)

    suspend fun listRemoteTargets(): List<RemoteTarget> = remoteLibrary.listRemoteTargets()

    suspend fun writebackAuthorized(sourceId: String, operation: String): Boolean =
        remoteLibrary.writebackAuthorized(sourceId, operation)

    suspend fun saveRemoteMirrorSnapshot(
        sourceName: String,
        books: List<SourceBookSummary>,
        targets: List<RemoteTarget>,
    ): List<SourceBookSummary> = remoteLibrary.saveRemoteMirrorSnapshot(sourceName, books, targets)

    suspend fun remoteMirrorSnapshot(sourceId: String) = remoteLibrary.remoteMirrorSnapshot(sourceId)

    suspend fun addBookToWebsiteTarget(
        book: org.tsuyomi.shared.sourcecontract.SourceBookSummary,
        targetId: String,
        targetName: String,
        defaultTargetId: String,
    ) = remoteLibrary.addBookToWebsiteTarget(book, targetId, targetName, defaultTargetId)

    suspend fun retryRemoteMutation(book: SourceBookSummary? = selectedBook, importedAt: Instant = Instant.now()): RemoteMutationUiResult =
        remoteLibrary.retryRemoteMutation(book ?: selectedBook, importedAt)

    suspend fun acknowledgeUnresolved(identity: BookIdentity): Boolean =
        remoteLibrary.acknowledgeUnresolved(identity)

    suspend fun remoteReconciliation(identity: BookIdentity) =
        library.bookReconciliation(identity.sourceId, identity.remoteBookId)

    suspend fun authorizeWriteback(sourceId: String, operation: String, enabled: Boolean): Boolean =
        remoteLibrary.authorizeWriteback(sourceId, operation, enabled)


    suspend fun unresolvedReconciliations() = library.unresolvedReconciliations()

    suspend fun reopenWithStoredCredentials() {
        when (sessionOwner.reopen()) {
            SourceSessionOpenResult.OPENED -> searchState = SearchResultState.Idle
            SourceSessionOpenResult.PACKAGE_CHANGED -> {
                resetReadingState()
                remoteLibrary.reset()
                searchState = SearchResultState.Idle
            }
            SourceSessionOpenResult.ALREADY_OPEN, null -> Unit
        }
    }

    suspend fun reopenAfterVerifiedPage() {
        val parsedSearchState = searchState
        when (sessionOwner.reopen()) {
            SourceSessionOpenResult.OPENED -> searchState = parsedSearchState
            SourceSessionOpenResult.PACKAGE_CHANGED -> {
                resetReadingState()
                remoteLibrary.reset()
            }
            SourceSessionOpenResult.ALREADY_OPEN, null -> Unit
        }
    }

    suspend fun restoreFor(target: SourceRestorationTarget, packageInfo: VerifiedHxpPackage) {
        open(packageInfo)
        if (target == SourceRestorationTarget.SEARCH) return
        val snapshot = snapshotStore.read(packageInfo.manifest.sourceId.value) ?: return
        when (target) {
            SourceRestorationTarget.SEARCH -> Unit
            SourceRestorationTarget.DETAIL -> if (selectedBook == null) prepareBook(snapshot.book)
            SourceRestorationTarget.DIRECTORY -> if (selectedBook == null) prepareBook(snapshot.book)
            SourceRestorationTarget.READER -> if (selectedBook == null || selectedChapter == null) {
                selectedBook = snapshot.book
                remoteLibrary.beginSelection(snapshot.book.identity)
                remoteLibrary.refreshSelection(snapshot.book)
                snapshot.chapter?.let { prepareChapter(it) }
            }
        }
    }

    fun updateQuery(value: String) {
        query = value.take(MaxSearchQueryLength)
        authorSearch = false
        searchInvocation += 1
    }

    fun restoreSearch(query: String, authorSearch: Boolean) {
        this.query = query
        this.authorSearch = authorSearch
    }

    fun resetHomeState() {
        home.reset()
    }

    suspend fun loadHome(
        selectedFilters: Map<String, String>,
        cursor: String? = null,
        offlineOnly: Boolean = false,
    ): Result<SourceHomePage> {
        val source = sessionOwner.requireClientOrNull()
            ?: return Result.failure(IllegalStateException("source-not-open"))
        return runCatching { source.home(selectedFilters, cursor, offlineOnly) }
    }

    suspend fun homeVerifiedPageRequestUrl(): String? {
        val source = sessionOwner.requireClientOrNull() ?: return null
        return runCatching { source.homeRequestUrl(home.selectedFilters, cursor = null) }.getOrNull()
    }

    suspend fun homeVerifiedPage(snapshot: CapturedVerifiedPage): Boolean {
        val source = sessionOwner.requireClientOrNull() ?: return false
        return try {
            home.acceptVerifiedPage(
                source.homeVerifiedPage(home.selectedFilters, cursor = null, snapshot = snapshot),
            )
            true
        } catch (error: SourceException) {
            home.rejectVerifiedPage(SourceHomeFailure(error.code, error.diagnostic.safeCode))
            false
        }
    }

    suspend fun search(offlineOnly: Boolean = false) {
        val title = query
        if (title.isBlank()) return
        val invocation = beginSearch(title, authorSearch = false)
        val source = sessionOwner.requireClientOrNull() ?: run {
            completeSearch(invocation, searchFailure(SourceErrorCode.EXTENSION_RUNTIME_FAILURE, "source-open", "source-not-open"))
            return
        }
        val result = try {
            SearchResultState.Results(source.search(title, offlineOnly = offlineOnly))
        } catch (error: SourceException) {
            SearchResultState.Failure(error.code, error.diagnostic)
        }
        completeSearch(invocation, result)
    }

    suspend fun authorSearch(author: String, offlineOnly: Boolean = false) {
        query = author
        authorSearch = true
        if (author.isBlank()) return
        val invocation = beginSearch(author, authorSearch = true)
        if (author.length > MaxSearchQueryLength) {
            completeSearch(
                invocation,
                searchFailure(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "author-search", "author-query-too-long"),
            )
            return
        }
        val source = sessionOwner.requireClientOrNull() ?: run {
            completeSearch(invocation, searchFailure(SourceErrorCode.EXTENSION_RUNTIME_FAILURE, "source-open", "source-not-open"))
            return
        }
        val result = try {
            SearchResultState.Results(source.authorSearch(author, offlineOnly = offlineOnly))
        } catch (error: SourceException) {
            SearchResultState.Failure(error.code, error.diagnostic)
        }
        completeSearch(invocation, result)
    }

    suspend fun searchVerifiedPageRequestUrl(): String? {
        val requestedQuery = query
        if (requestedQuery.isBlank()) return null
        val requestedAuthorSearch = authorSearch
        if (requestedAuthorSearch && requestedQuery.length > MaxSearchQueryLength) {
            searchState = searchFailure(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "author-search", "author-query-too-long")
            return null
        }
        val source = sessionOwner.requireClientOrNull() ?: return null
        return try {
            if (requestedAuthorSearch) {
                source.authorSearchRequestUrl(requestedQuery)
            } else {
                source.searchRequestUrl(requestedQuery)
            }
        } catch (error: SourceException) {
            if (requestedAuthorSearch) searchState = SearchResultState.Failure(error.code, error.diagnostic)
            null
        }
    }

    suspend fun searchVerifiedPage(snapshot: CapturedVerifiedPage): Boolean {
        val requestedQuery = query
        if (requestedQuery.isBlank()) return false
        val requestedAuthorSearch = authorSearch
        if (requestedAuthorSearch && requestedQuery.length > MaxSearchQueryLength) {
            searchState = searchFailure(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "author-search", "author-query-too-long")
            return false
        }
        val invocation = beginSearch(requestedQuery, requestedAuthorSearch)
        val source = sessionOwner.requireClientOrNull() ?: run {
            completeSearch(invocation, searchFailure(SourceErrorCode.EXTENSION_RUNTIME_FAILURE, "source-open", "source-not-open"))
            return false
        }
        val result = try {
            if (requestedAuthorSearch) {
                SearchResultState.Results(source.authorSearchVerifiedPage(requestedQuery, snapshot))
            } else {
                SearchResultState.Results(source.searchVerifiedPage(requestedQuery, snapshot))
            }
        } catch (error: SourceException) {
            SearchResultState.Failure(error.code, error.diagnostic)
        }
        completeSearch(invocation, result)
        return searchInvocation == invocation && searchState is SearchResultState.Results
    }
    suspend fun detailVerifiedPageRequestUrl(): String? {
        val source = sessionOwner.requireClientOrNull() ?: return null
        val book = selectedBook ?: return null
        return runCatching { source.detailRequestUrl(book.identity.remoteBookId) }.getOrNull()
    }

    suspend fun detailVerifiedPage(snapshot: CapturedVerifiedPage): Boolean {
        val source = sessionOwner.requireClientOrNull() ?: return false
        val book = selectedBook ?: return false
        detailState = SourceBookState.Loading
        detailState = try {
            val detail = source.detailVerifiedPage(book.identity.remoteBookId, snapshot).also { value ->
                runCatching { normalizedStore.writeDetail(value) }
            }
            adoptDetail(detail)
            SourceBookState.Content(detail)
        } catch (error: SourceException) {
            SourceBookState.Failure(error.code, error.diagnostic)
        }
        return detailState is SourceBookState.Content
    }

    suspend fun directoryVerifiedPageRequestUrl(): String? {
        val source = sessionOwner.requireClientOrNull() ?: return null
        val book = selectedBook ?: return null
        return runCatching { source.directoryRequestUrl(book.identity.remoteBookId) }.getOrNull()
    }

    suspend fun directoryVerifiedPage(snapshot: CapturedVerifiedPage): Boolean {
        val source = sessionOwner.requireClientOrNull() ?: return false
        val book = selectedBook ?: return false
        directoryState = SourceBookState.Loading
        directoryState = try {
            SourceBookState.Content(
                source.directoryVerifiedPage(book.identity.remoteBookId, snapshot).also { directory ->
                    runCatching { normalizedStore.writeDirectory(directory) }
                },
            )
        } catch (error: SourceException) {
            SourceBookState.Failure(error.code, error.diagnostic)
        }
        return directoryState is SourceBookState.Content
    }


    suspend fun prepareBook(book: SourceBookSummary) {
        if (selectedBook?.identity != book.identity) {
            detailState = SourceBookState.Loading
            directoryState = SourceBookState.Loading
            selectedChapter = null
            completedChapterIds = emptySet()
        }
        selectedBook = book
        remoteLibrary.beginSelection(book.identity)
        snapshotStore.saveBook(book)
        remoteLibrary.refreshSelection(book)
        val persistedCompleted = library.completedChapterIds(book.identity)
        if (selectedBook?.identity == book.identity) completedChapterIds = persistedCompleted
    }

    suspend fun prepareLocalDetail(book: SourceBookSummary) {
        prepareBook(book)
        detailState = SourceBookState.Content(
            SourceBookDetail(
                summary = book,
                description = null,
                tags = emptyList(),
                status = null,
            ),
        )
    }

    suspend fun selectBook(book: SourceBookSummary, offlineOnly: Boolean = false) {
        prepareBook(book)
        detailState = requestDetail(book, offlineOnly)
    }

    suspend fun requestDetail(
        book: SourceBookSummary = requireNotNull(selectedBook) { "Book is not selected" },
        offlineOnly: Boolean = false,
    ): SourceBookState<SourceBookDetail> = try {
        val detail = loadDetail(book, offlineOnly)
        adoptDetail(detail)
        SourceBookState.Content(detail)
    } catch (error: SourceException) {
        SourceBookState.Failure(error.code, error.diagnostic)
    }

    suspend fun requestDirectory(
        book: SourceBookSummary = requireNotNull(selectedBook) { "Book is not selected" },
        offlineOnly: Boolean = false,
    ): SourceBookState<SourceDirectory> {
        val result = try {
            SourceBookState.Content(loadDirectory(book, offlineOnly))
        } catch (error: SourceException) {
            SourceBookState.Failure(error.code, error.diagnostic)
        }
        if (selectedBook?.identity == book.identity) directoryState = result
        return result
    }

    suspend fun loadDirectory(offlineOnly: Boolean = false) {
        val book = selectedBook ?: return
        directoryState = SourceBookState.Loading
        directoryState = requestDirectory(book, offlineOnly)
    }

    suspend fun prepareDetail(identity: BookIdentity): Boolean {
        val detail = normalizedStore.readDetail(identity) ?: return false
        val directory = normalizedStore.readDirectory(identity) ?: return false
        if (detail.summary.identity != identity || directory.bookIdentity != identity) return false
        prepareBook(detail.summary)
        adoptDetail(detail)
        detailState = SourceBookState.Content(detail)
        directoryState = SourceBookState.Content(directory)
        selectedChapter = library.progress(identity)?.locator?.document?.contentId
            ?.let { contentId -> directory.chapters.firstOrNull { it.chapterId == contentId } }
        selectedChapter?.let { snapshotStore.saveChapter(it) }
        return true
    }

    suspend fun prepareResume(identity: BookIdentity): Boolean {
        val entry = library.libraryEntries().firstOrNull { it.book.identity == identity } ?: return false
        return prepareResume(entry)
    }

    suspend fun prepareResume(entry: LibraryEntry): Boolean {
        val identity = entry.book.identity
        val detail = normalizedStore.readDetail(identity) ?: return false
        val directory = normalizedStore.readDirectory(identity) ?: return false
        val locator = entry.progress?.locator ?: return false
        val chapter = directory.chapters.firstOrNull { it.chapterId == locator.document.contentId } ?: return false
        val document = normalizedStore.readDocument(identity, chapter.chapterId) ?: return false
        if (detail.summary.identity != identity || directory.bookIdentity != identity) return false
        if (document.sourceId != identity.sourceId || document.remoteBookId != identity.remoteBookId ||
            document.contentId != chapter.chapterId
        ) return false

        selectedBook = detail.summary
        detailState = SourceBookState.Content(detail)
        directoryState = SourceBookState.Content(directory)
        remoteLibrary.beginSelection(identity)
        remoteLibrary.refreshSelection(detail.summary)
        prepareChapter(chapter)
        preparedResumeLoad = SourceReaderLoad(document = document, restoredLocator = locator)
        return true
    }

    suspend fun prepareChapter(chapter: SourceChapter) {
        selectedChapter = chapter
        snapshotStore.saveChapter(chapter)
    }

    suspend fun requestChapter(
        book: SourceBookSummary = requireNotNull(selectedBook) { "Book is not selected" },
        chapter: SourceChapter = requireNotNull(selectedChapter) { "Chapter is not selected" },
        offlineOnly: Boolean = false,
    ): SourceReaderLoad {
        val restored = library.progress(book.identity)?.locator
            ?.takeIf { it.document.contentId == chapter.chapterId }
        return try {
            SourceReaderLoad(
                document = loadDocument(book, chapter, offlineOnly),
                restoredLocator = restored,
            )
        } catch (error: SourceException) {
            SourceReaderLoad(failure = error, restoredLocator = restored)
        }
    }

    suspend fun reloadDetail(offlineOnly: Boolean) {
        selectedBook?.let { selectBook(it, offlineOnly) }
    }

    suspend fun setSelectedRating(rating: Int?) {
        val book = requireNotNull(selectedBook) { "Book is not selected" }
        check(remoteLibrary.selectedLibraryEntry != null) { "Book is not in library" }
        library.setRating(book.identity, rating)
        remoteLibrary.refreshSelection(book)
    }

    suspend fun addSelectedLocalTag(tag: String) {
        val book = requireNotNull(selectedBook) { "Book is not selected" }
        val entry = requireNotNull(remoteLibrary.selectedLibraryEntry) { "Book is not in library" }
        library.setLocalTags(book.identity, entry.localTags + tag)
        remoteLibrary.refreshSelection(book)
    }

    suspend fun toggleSelectedReadLater() {
        val book = requireNotNull(selectedBook) { "Book is not selected" }
        remoteLibrary.toggleReadLater(book)
    }


    suspend fun saveProgress(locator: ReaderLocator, precision: LocatorPrecision) {
        val book = selectedBook ?: return
        val updatedAt = Instant.now()
        val persisted = mergeLibraryBook(
            existing = library.book(book.identity),
            summary = book,
            updatedAt = updatedAt,
        )
        library.saveBook(persisted)
        library.saveProgress(ReadingProgress(book.identity, locator))
    }

    suspend fun markChapterCompleted(
        identity: BookIdentity,
        chapterId: String,
        completedAt: Instant = Instant.now(),
    ) {
        if (chapterId.isBlank() || chapterId in completedChapterIds && selectedBook?.identity == identity) return
        if (library.book(identity) == null) {
            val book = selectedBook?.takeIf { it.identity == identity } ?: return
            library.saveBook(mergeLibraryBook(existing = null, summary = book, updatedAt = completedAt))
        }
        library.markChapterCompleted(identity, chapterId, completedAt)
        if (selectedBook?.identity == identity) {
            completedChapterIds = library.completedChapterIds(identity)
        }
    }

    private suspend fun adoptDetail(detail: SourceBookDetail) {
        val summary = detail.summary
        if (selectedBook?.identity != summary.identity) return
        selectedBook = summary
        snapshotStore.saveBook(summary)
        val existing = library.book(summary.identity)
        if (existing != null) {
            val merged = mergeLibraryBook(existing, summary, Instant.now())
            if (merged != existing) library.saveBook(merged)
        }
        remoteLibrary.refreshSelection(summary)
    }

    private fun mergeLibraryBook(
        existing: LibraryBook?,
        summary: SourceBookSummary,
        updatedAt: Instant,
    ): LibraryBook {
        val base = existing ?: LibraryBook(
            identity = summary.identity,
            title = summary.title,
            author = summary.author,
            coverUrl = summary.coverUrl,
            canonicalUrl = summary.canonicalUrl,
            addedAt = updatedAt,
            metadataUpdatedAt = updatedAt,
        )
        val merged = base.copy(
            title = summary.title,
            author = summary.author ?: base.author,
            authors = summary.author?.let(::setOf) ?: base.authors,
            coverUrl = summary.coverUrl ?: base.coverUrl,
            canonicalUrl = summary.canonicalUrl.takeIf(String::isNotBlank) ?: base.canonicalUrl,
        )
        return if (merged == base) base else merged.copy(metadataUpdatedAt = updatedAt)
    }

    private suspend fun loadDetail(book: SourceBookSummary, offlineOnly: Boolean): SourceBookDetail {
        if (offlineOnly) return normalizedStore.readDetail(book.identity) ?: throw normalizedCacheMiss("detail")
        return try {
            sessionOwner.requireClient().detail(book.identity.remoteBookId, offlineOnly = false).also { detail ->
                runCatching { normalizedStore.writeDetail(detail) }
            }
        } catch (error: SourceException) {
            if (error.code != SourceErrorCode.NETWORK_OFFLINE) throw error
            normalizedStore.readDetail(book.identity) ?: throw error
        }
    }

    private suspend fun loadDirectory(book: SourceBookSummary, offlineOnly: Boolean): SourceDirectory {
        if (offlineOnly) return normalizedStore.readDirectory(book.identity) ?: throw normalizedCacheMiss("directory")
        return try {
            sessionOwner.requireClient().directory(book.identity.remoteBookId, offlineOnly = false).also { directory ->
                runCatching { normalizedStore.writeDirectory(directory) }
            }
        } catch (error: SourceException) {
            if (error.code != SourceErrorCode.NETWORK_OFFLINE) throw error
            normalizedStore.readDirectory(book.identity) ?: throw error
        }
    }

    private suspend fun loadDocument(
        book: SourceBookSummary,
        chapter: SourceChapter,
        offlineOnly: Boolean,
    ): ReaderDocument {
        if (offlineOnly) {
            return normalizedStore.readDocument(book.identity, chapter.chapterId) ?: throw normalizedCacheMiss("chapter")
        }
        return try {
            sessionOwner.requireClient().chapter(chapter, book.identity.remoteBookId, offlineOnly = false).also { document ->
                runCatching { normalizedStore.writeDocument(book.identity, document) }
            }
        } catch (error: SourceException) {
            if (error.code != SourceErrorCode.NETWORK_OFFLINE) throw error
            normalizedStore.readDocument(book.identity, chapter.chapterId) ?: throw error
        }
    }

    private fun normalizedCacheMiss(stage: String): SourceException = SourceException(
        code = SourceErrorCode.NETWORK_OFFLINE,
        diagnostic = SourceDiagnostic(
            correlationId = "offline-$stage",
            stage = "normalized-cache",
            safeCode = "$stage-miss",
        ),
    )

    private fun resetReadingState() {
        query = ""
        authorSearch = false
        searchInvocation += 1
        searchState = SearchResultState.Idle
        detailState = SourceBookState.Loading
        directoryState = SourceBookState.Loading
        selectedBook = null
        selectedChapter = null
        completedChapterIds = emptySet()
        verifiedChapterLoad = null
        preparedResumeLoad = null
    }

    private fun beginSearch(query: String, authorSearch: Boolean): Long {
        searchInvocation += 1
        this.query = query
        this.authorSearch = authorSearch
        searchState = SearchResultState.Loading
        return searchInvocation
    }

    private fun completeSearch(invocation: Long, result: SearchResultState) {
        if (searchInvocation == invocation) searchState = result
    }

    private fun searchFailure(code: SourceErrorCode, stage: String, safeCode: String): SearchResultState.Failure =
        SearchResultState.Failure(
            code,
            SourceDiagnostic(
                correlationId = safeCode.padEnd(8, '-'),
                stage = stage,
                safeCode = safeCode,
            ),
        )


    override fun close() {
        home.close()
        sessionOwner.close()
    }
    private companion object {
        const val MaxSearchQueryLength = 100
    }

}

enum class SourceRestorationTarget { SEARCH, DETAIL, DIRECTORY, READER }
