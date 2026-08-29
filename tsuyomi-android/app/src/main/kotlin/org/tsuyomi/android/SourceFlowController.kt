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
import org.tsuyomi.core.database.ReadingProgress
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.feature.search.SearchResultState
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
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
    directActionTokens: DirectActionTokenRegistry = DirectActionTokenRegistry(),
    openSession: suspend (VerifiedHxpPackage) -> SourceFlowSession =
        SourceSessionOwner.extensionClientFactory(context, directActionTokens),
) : Closeable {
    private val sessionOwner = SourceSessionOwner(directActionTokens, openSession)

    val remoteLibrary = SourceRemoteLibraryCoordinator(context, library, sessionOwner)

    var query: String by mutableStateOf("")
        private set
    var searchState: SearchResultState by mutableStateOf(SearchResultState.Idle)
        private set
    var detailState: SourceBookState<SourceBookDetail> by mutableStateOf(SourceBookState.Loading)
        private set
    var directoryState: SourceBookState<SourceDirectory> by mutableStateOf(SourceBookState.Loading)
        private set
    var readerDocument: ReaderDocument? by mutableStateOf(null)
        private set
    var readerLoading: Boolean by mutableStateOf(false)
        private set
    var readerFailure: SourceException? by mutableStateOf(null)
        private set
    var restoredLocator: ReaderLocator? by mutableStateOf(null)
        private set
    var restorationPrecision: LocatorPrecision? by mutableStateOf(null)
        private set

    var selectedBook: SourceBookSummary? = null
        private set
    var selectedChapter: SourceChapter? = null
        private set

    suspend fun open(packageInfo: VerifiedHxpPackage) {
        val result = sessionOwner.open(packageInfo) {
            resetReadingState()
            remoteLibrary.reset()
        }
        when (result) {
            SourceSessionOpenResult.ALREADY_OPEN -> return
            SourceSessionOpenResult.OPENED, SourceSessionOpenResult.PACKAGE_CHANGED -> {
                searchState = SearchResultState.Idle
            }
        }
    }
    suspend fun pullRemoteLibrary(
        packageInfo: VerifiedHxpPackage,
        importedAt: Instant,
    ): RemoteLibraryPullResult {
        open(packageInfo)
        return remoteLibrary.pull(packageInfo, importedAt)
    }

    suspend fun addSelectedBook(importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteLibrary.addBook(selectedBook, importedAt)

    suspend fun retrySelectedBookRemoteAdd(importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteLibrary.retryBook(selectedBook, importedAt)

    suspend fun removeSelectedBook(): Boolean = remoteLibrary.removeBook(selectedBook)


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

    suspend fun restoreFor(target: SourceRestorationTarget, packageInfo: VerifiedHxpPackage) {
        open(packageInfo)
        if (target == SourceRestorationTarget.SEARCH) return
        val snapshot = snapshotStore.read(packageInfo.manifest.sourceId.value) ?: return
        when (target) {
            SourceRestorationTarget.SEARCH -> Unit
            SourceRestorationTarget.DETAIL -> if (selectedBook == null) selectBook(snapshot.book)
            SourceRestorationTarget.DIRECTORY -> if (selectedBook == null) {
                selectBook(snapshot.book)
                loadDirectory()
            }
            SourceRestorationTarget.READER -> if (readerDocument == null && !readerLoading) {
                selectedBook = snapshot.book
                remoteLibrary.beginSelection(snapshot.book.identity)
                remoteLibrary.refreshSelection(snapshot.book)
                snapshot.chapter?.let { selectChapter(it) }
            }
        }
    }

    fun updateQuery(value: String) {
        query = value.take(100)
    }

    suspend fun search(offlineOnly: Boolean = false) {
        val source = sessionOwner.requireClientOrNull()
            ?: return setSearchFailure(SourceErrorCode.EXTENSION_RUNTIME_FAILURE, "source-not-open")
        if (query.isBlank()) return
        searchState = SearchResultState.Loading
        searchState = try {
            SearchResultState.Results(source.search(query, offlineOnly = offlineOnly))
        } catch (error: SourceException) {
            SearchResultState.Failure(error.code, error.diagnostic)
        }
    }

    suspend fun selectBook(book: SourceBookSummary, offlineOnly: Boolean = false) {
        selectedBook = book
        remoteLibrary.beginSelection(book.identity)
        snapshotStore.saveBook(book)
        detailState = SourceBookState.Loading
        detailState = try {
            val detail = sessionOwner.requireClient().detail(book.identity.remoteBookId, offlineOnly)
            val now = Instant.now()
            library.saveBook(
                LibraryBook(
                    identity = book.identity,
                    title = detail.summary.title,
                    addedAt = now,
                    metadataUpdatedAt = now,
                    author = detail.summary.author,
                    coverUrl = detail.summary.coverUrl,
                    canonicalUrl = detail.summary.canonicalUrl,
                    status = detail.status,
                    remoteTags = detail.tags.toSet(),
                ),
            )
            SourceBookState.Content(detail)
        } catch (error: SourceException) {
            SourceBookState.Failure(error.code, error.diagnostic)
        }
        remoteLibrary.refreshSelection(book)
    }

    suspend fun loadDirectory(offlineOnly: Boolean = false) {
        val book = selectedBook ?: return
        directoryState = SourceBookState.Loading
        directoryState = try {
            SourceBookState.Content(sessionOwner.requireClient().directory(book.identity.remoteBookId, offlineOnly))
        } catch (error: SourceException) {
            SourceBookState.Failure(error.code, error.diagnostic)
        }
    }

    suspend fun selectChapter(chapter: SourceChapter, offlineOnly: Boolean = false) {
        val book = selectedBook ?: return
        selectedChapter = chapter
        snapshotStore.saveChapter(chapter)
        readerLoading = true
        readerFailure = null
        restoredLocator = library.progress(book.identity)?.locator?.takeIf { it.document.contentId == chapter.chapterId }
        readerDocument = try {
            sessionOwner.requireClient().chapter(chapter, book.identity.remoteBookId, offlineOnly)
        } catch (error: SourceException) {
            readerFailure = error
            null
        } finally {
            readerLoading = false
        }
    }

    suspend fun reloadDetail(offlineOnly: Boolean) {
        selectedBook?.let { selectBook(it, offlineOnly) }
    }

    suspend fun reloadChapter(offlineOnly: Boolean) {
        selectedChapter?.let { selectChapter(it, offlineOnly) }
    }

    suspend fun saveProgress(locator: ReaderLocator, precision: LocatorPrecision) {
        val book = selectedBook ?: return
        library.saveBook(
            LibraryBook(
                identity = book.identity,
                title = book.title,
                addedAt = Instant.now(),
                metadataUpdatedAt = Instant.now(),
            ),
        )
        library.saveProgress(ReadingProgress(book.identity, locator))
        restoredLocator = locator
        restorationPrecision = precision
    }

    private fun resetReadingState() {
        query = ""
        searchState = SearchResultState.Idle
        detailState = SourceBookState.Loading
        directoryState = SourceBookState.Loading
        readerDocument = null
        readerLoading = false
        readerFailure = null
        restoredLocator = null
        restorationPrecision = null
        selectedBook = null
        selectedChapter = null
    }

    private fun setSearchFailure(code: SourceErrorCode, safeId: String) {
        searchState = SearchResultState.Failure(
            code,
            SourceDiagnostic(
                correlationId = safeId.padEnd(8, '-'),
                stage = "source-open",
                safeCode = safeId,
            ),
        )
    }

    override fun close() = sessionOwner.close()
}

enum class SourceRestorationTarget { SEARCH, DETAIL, DIRECTORY, READER }
