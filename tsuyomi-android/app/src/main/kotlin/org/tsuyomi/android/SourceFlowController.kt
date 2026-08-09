/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import org.tsuyomi.core.database.LibraryBook
import org.tsuyomi.core.database.ReadingProgress
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.feature.search.SearchResultState
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.source.extensionmanager.SourceExtensionClient
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

class SourceFlowController(
    private val context: Context,
    private val library: RoomLibraryRepository,
    private val snapshotStore: SourceFlowSnapshotStore,
) {
    private var client: SourceExtensionClient? = null
    private var activePackage: VerifiedHxpPackage? = null

    var query: String by mutableStateOf("")
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
        if (activePackage?.packageSha256 == packageInfo.packageSha256 && client != null) return
        client?.close()
        val gateway = Gate2SourceGateway.create(context, packageInfo)
        client = SourceExtensionClient.open(packageInfo, gateway)
        activePackage = packageInfo
        searchState = SearchResultState.Idle
    }

    fun updateQuery(value: String) {
        query = value.take(100)
    }

    suspend fun reopenWithStoredCredentials() {
        val packageInfo = activePackage ?: return
        client?.close()
        client = null
        activePackage = null
        open(packageInfo)
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
                snapshot.chapter?.let { selectChapter(it) }
            }
        }
    }

    suspend fun search(offlineOnly: Boolean = false) {
        val source = client ?: return setSearchFailure(SourceErrorCode.EXTENSION_RUNTIME_FAILURE, "source-not-open")
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
        snapshotStore.saveBook(book)
        detailState = SourceBookState.Loading
        detailState = try {
            library.saveBook(
                LibraryBook(
                    identity = book.identity,
                    title = book.title,
                    addedAt = Instant.now(),
                    metadataUpdatedAt = Instant.now(),
                ),
            )
            SourceBookState.Content(requireClient().detail(book.identity.remoteBookId, offlineOnly))
        } catch (error: SourceException) {
            SourceBookState.Failure(error.code, error.diagnostic)
        }
    }

    suspend fun loadDirectory(offlineOnly: Boolean = false) {
        val book = selectedBook ?: return
        directoryState = SourceBookState.Loading
        directoryState = try {
            SourceBookState.Content(requireClient().directory(book.identity.remoteBookId, offlineOnly))
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
            requireClient().chapter(chapter, book.identity.remoteBookId, offlineOnly)
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

    private fun requireClient(): SourceExtensionClient = checkNotNull(client) { "Source is not open" }

    private fun setSearchFailure(code: SourceErrorCode, safeId: String) {
        searchState = SearchResultState.Failure(
            code,
            org.tsuyomi.shared.sourcecontract.SourceDiagnostic(
                correlationId = safeId.padEnd(8, '-'),
                stage = "source-open",
                safeCode = safeId,
            ),
        )
    }
}

enum class SourceRestorationTarget { SEARCH, DETAIL, DIRECTORY, READER }
