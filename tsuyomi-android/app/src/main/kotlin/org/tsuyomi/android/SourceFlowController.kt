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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tsuyomi.core.database.LibraryBook
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.core.database.ReadingProgress
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.network.DirectActionBinding
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.feature.search.SearchResultState
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddOutcome
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.source.extensionmanager.SourceExtensionClient
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage
import org.tsuyomi.source.extensionmanager.RemoteOperation
internal interface SourceFlowSession : Closeable {
    suspend fun search(query: String, page: Int = 1, offlineOnly: Boolean = false): List<SourceBookSummary>
    suspend fun detail(remoteBookId: String, offlineOnly: Boolean = false): SourceBookDetail
    suspend fun directory(remoteBookId: String, offlineOnly: Boolean = false): SourceDirectory
    suspend fun chapter(chapter: SourceChapter, remoteBookId: String, offlineOnly: Boolean = false): ReaderDocument
    suspend fun listRemoteLibrary(cursor: String?): org.tsuyomi.shared.sourcecontract.RemoteLibraryPage
    suspend fun addRemoteLibrary(remoteBookId: String, directActionToken: String): org.tsuyomi.shared.sourcecontract.RemoteLibraryAddResult
}

private class ExtensionSourceFlowSession(
    private val delegate: SourceExtensionClient,
) : SourceFlowSession {
    override suspend fun search(query: String, page: Int, offlineOnly: Boolean) = delegate.search(query, page, offlineOnly)
    override suspend fun detail(remoteBookId: String, offlineOnly: Boolean) = delegate.detail(remoteBookId, offlineOnly)
    override suspend fun directory(remoteBookId: String, offlineOnly: Boolean) = delegate.directory(remoteBookId, offlineOnly)
    override suspend fun chapter(chapter: SourceChapter, remoteBookId: String, offlineOnly: Boolean) =
        delegate.chapter(chapter, remoteBookId, offlineOnly)
    override suspend fun listRemoteLibrary(cursor: String?) = delegate.listRemoteLibrary(cursor)
    override suspend fun addRemoteLibrary(remoteBookId: String, directActionToken: String) =
        delegate.addRemoteLibrary(remoteBookId, directActionToken)
    override fun close() = delegate.close()
}


internal class SourceFlowController(
    private val context: Context,
    private val library: RoomLibraryRepository,
    private val snapshotStore: SourceFlowSnapshotStore,
    private val openSession: suspend (VerifiedHxpPackage) -> SourceFlowSession = { packageInfo ->
        ExtensionSourceFlowSession(SourceExtensionClient.open(packageInfo, Gate2SourceGateway.create(context, packageInfo)))
    },
) : Closeable {
    private val credentialStore = SourceCredentialStore(context)
    private val remoteAddMutex = Mutex()
    private val clientLock = Any()
    private var client: SourceFlowSession? = null
    private var activePackage: VerifiedHxpPackage? = null
    private var openGeneration = 0L
    private var statePackageSha256: String? = null
    private var closed = false

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

    var selectedBookInLibrary: Boolean by mutableStateOf(false)
        private set
    var selectedBookReconciliation: RemoteReconciliationState? by mutableStateOf(null)
        private set
    var selectedBookAddWritesRemote: Boolean by mutableStateOf(false)
        private set
    var remoteLibraryBooks: List<SourceBookSummary> by mutableStateOf(emptyList())
        private set

    suspend fun open(packageInfo: VerifiedHxpPackage) {
        val (previousClient, operationGeneration, shouldResetState) = synchronized(clientLock) {
            checkOpen()
            if (activePackage?.packageSha256 == packageInfo.packageSha256 && client != null) return
            openGeneration += 1
            val previous = client
            client = null
            activePackage = null
            val resetState = statePackageSha256 != packageInfo.packageSha256
            statePackageSha256 = packageInfo.packageSha256
            Triple(previous, openGeneration, resetState)
        }
        previousClient?.close()
        if (shouldResetState) resetSourceState()

        val openedClient = openSession(packageInfo)
        val retained = synchronized(clientLock) {
            if (closed || openGeneration != operationGeneration) {
                false
            } else {
                client = openedClient
                activePackage = packageInfo
                true
            }
        }
        if (!retained) {
            openedClient.close()
            synchronized(clientLock) { checkOpen() }
        }
        searchState = SearchResultState.Idle
    }

    suspend fun pullRemoteLibrary(packageInfo: VerifiedHxpPackage, importedAt: Instant): RemoteLibraryPullResult {
        open(packageInfo)
        val operationGeneration = synchronized(clientLock) { openGeneration }
        val sourceId = packageInfo.manifest.sourceId.value
        val availability = library.sourceAvailability(sourceId)
            ?: return RemoteLibraryPullResult.Failure("source-unavailable")
        val policy = library.sourceRemotePolicy(sourceId)
            ?: return RemoteLibraryPullResult.Failure("remote-policy-missing")
        if (!availability.available || availability.verifiedVersion != packageInfo.manifest.version.original ||
            packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.READ] == null
        ) return RemoteLibraryPullResult.Failure("remote-read-not-granted")
        val lease = RemoteExecutionLease(
            packageInfo.packageSha256,
            packageInfo.manifest.version.original,
            policy.capabilitySetFingerprint,
            availability.generation,
            operationGeneration,
        )
        val seenCursors = hashSetOf<String>()
        val books = linkedMapOf<BookIdentity, LibraryBook>()
        val summaries = linkedMapOf<BookIdentity, SourceBookSummary>()
        var cursor: String? = null
        var aggregateBytes = 0L
        repeat(100) { pageIndex ->
            val page = try { requireClient().listRemoteLibrary(cursor) } catch (error: SourceException) {
                return when (error.code) {
                    SourceErrorCode.SESSION_REQUIRED -> RemoteLibraryPullResult.LoginRequired
                    SourceErrorCode.VERIFICATION_REQUIRED -> RemoteLibraryPullResult.VerificationRequired
                    SourceErrorCode.EXTENSION_CANCELLED -> RemoteLibraryPullResult.Cancelled
                    else -> RemoteLibraryPullResult.Failure(error.diagnostic.safeCode)
                }
            }
            page.items.forEach { item ->
                if (item.identity.sourceId != sourceId) return RemoteLibraryPullResult.Failure("source-identity-mismatch")
                val normalizedBytes = item.title.encodeToByteArray().size + item.identity.remoteBookId.encodeToByteArray().size +
                    item.canonicalUrl.encodeToByteArray().size + (item.author?.encodeToByteArray()?.size ?: 0) +
                    (item.coverUrl?.encodeToByteArray()?.size ?: 0)
                aggregateBytes += normalizedBytes
                if (aggregateBytes > 8L * 1024 * 1024) return RemoteLibraryPullResult.Failure("aggregate-limit")
                summaries.putIfAbsent(item.identity, item)
                books.putIfAbsent(
                    item.identity,
                    LibraryBook(
                        identity = item.identity,
                        title = item.title,
                        addedAt = importedAt,
                        metadataUpdatedAt = importedAt,
                        author = item.author,
                        coverUrl = item.coverUrl,
                        canonicalUrl = item.canonicalUrl,
                    ),
                )
                if (books.size > 5_000) return RemoteLibraryPullResult.Failure("record-limit")
            }
            if (page.complete) {
                if (page.nextCursor != null) return RemoteLibraryPullResult.Failure("complete-with-cursor")
                val (activeAtMerge, ownerAtMerge) = synchronized(clientLock) { activePackage to openGeneration }
                val availabilityAtMerge = library.sourceAvailability(sourceId)
                val policyAtMerge = library.sourceRemotePolicy(sourceId)
                if (!lease.matches(
                        activeAtMerge?.packageSha256,
                        activeAtMerge?.manifest?.version?.original,
                        availabilityAtMerge?.verifiedVersion,
                        policyAtMerge?.capabilitySetFingerprint,
                        availabilityAtMerge?.generation,
                        ownerAtMerge,
                    ) || availabilityAtMerge?.available != true
                ) return RemoteLibraryPullResult.Failure("source-changed")
                val added = try {
                    library.mergeRemoteLibrary(
                        sourceId,
                        books.values.toList(),
                        lease.packageVersion,
                        lease.capabilitySetFingerprint,
                        lease.sourceGeneration,
                        importedAt,
                    )
                } catch (_: IllegalStateException) {
                    return RemoteLibraryPullResult.Failure("source-changed")
                }
                remoteLibraryBooks = summaries.values.toList()
                return RemoteLibraryPullResult.Success(books.size, added)
            }
            val next = page.nextCursor ?: return RemoteLibraryPullResult.Failure("incomplete-page")
            if (!seenCursors.add(next)) return RemoteLibraryPullResult.Failure("duplicate-cursor")
            cursor = next
            if (pageIndex == 99) return RemoteLibraryPullResult.Failure("page-limit")
        }
        return RemoteLibraryPullResult.Failure("page-limit")
    }

    suspend fun addSelectedBook(importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteAddMutex.withLock { addSelectedBookLocked(importedAt) }

    private suspend fun addSelectedBookLocked(importedAt: Instant): RemoteAddUiResult {
        if (selectedBookInLibrary) return RemoteAddUiResult.Failure("book-already-added")
        val summary = selectedBook ?: return RemoteAddUiResult.Failure("book-not-selected")
        val packageInfo = synchronized(clientLock) { activePackage } ?: return RemoteAddUiResult.Failure("source-not-open")
        val book = LibraryBook(summary.identity, summary.title, importedAt, importedAt, summary.author, coverUrl = summary.coverUrl, canonicalUrl = summary.canonicalUrl)
        val policy = library.sourceRemotePolicy(summary.identity.sourceId)
        val availability = library.sourceAvailability(summary.identity.sourceId)
        val addPolicy = packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.ADD]
        val credentialReady = addPolicy != null && remoteAddCredentialReady(packageInfo, addPolicy.origin)
        if (policy?.addWritebackEnabled != true || availability?.available != true || addPolicy == null ||
            policy.capabilitySetFingerprint.isBlank() || !credentialReady
        ) {
            if (policy?.addWritebackEnabled == true && !credentialReady) {
                library.setAddWritebackEnabled(summary.identity.sourceId, policy.capabilitySetFingerprint, false)
            }
            library.addToLibrary(book)
            if (selectedBook?.identity == summary.identity) {
                selectedBookAddWritesRemote = false
                selectedBookInLibrary = true
            }
            return RemoteAddUiResult.LocalOnly
        }
        val reconciliationId = library.beginRemoteAdd(
            book,
            packageInfo.packageSha256,
            packageInfo.manifest.version.original,
            policy.capabilitySetFingerprint,
            availability.generation,
            importedAt,
        )
        if (selectedBook?.identity == summary.identity) {
            selectedBookInLibrary = true
            selectedBookReconciliation = RemoteReconciliationState.PENDING_USER_ACTION
        }
        val operationGeneration = synchronized(clientLock) { openGeneration }
        val lease = RemoteExecutionLease(
            packageInfo.packageSha256,
            packageInfo.manifest.version.original,
            policy.capabilitySetFingerprint,
            availability.generation,
            operationGeneration,
        )
        val token = DirectActionTokenRegistry.process.mint(
            DirectActionBinding(
                summary.identity.sourceId,
                summary.identity.remoteBookId,
                reconciliationId,
                lease.packageSha256,
                lease.packageVersion,
                lease.capabilitySetFingerprint,
                lease.sourceGeneration,
                lease.ownerGeneration,
            ),
        ) {
            val (activeAtAccept, ownerAtAccept) = synchronized(clientLock) { activePackage to openGeneration }
            val availabilityAtAccept = library.sourceAvailability(summary.identity.sourceId)
            val policyAtAccept = library.sourceRemotePolicy(summary.identity.sourceId)
            if (!lease.matches(
                    activeAtAccept?.packageSha256,
                    activeAtAccept?.manifest?.version?.original,
                    availabilityAtAccept?.verifiedVersion,
                    policyAtAccept?.capabilitySetFingerprint,
                    availabilityAtAccept?.generation,
                    ownerAtAccept,
                ) || availabilityAtAccept?.available != true || policyAtAccept?.addWritebackEnabled != true ||
                !remoteAddCredentialReady(packageInfo, addPolicy.origin)
            ) return@mint false
            library.transitionRemoteAdd(
                reconciliationId,
                RemoteReconciliationState.PENDING_USER_ACTION,
                RemoteReconciliationState.IN_FLIGHT,
                Instant.now(),
            )
        }
        return try {
            val result = requireClient().addRemoteLibrary(summary.identity.remoteBookId, token)
            check(result.outcome == RemoteLibraryAddOutcome.APPLIED || result.outcome == RemoteLibraryAddOutcome.ALREADY_PRESENT)
            val (activeAfterResponse, ownerAfterResponse) = synchronized(clientLock) { activePackage to openGeneration }
            val availabilityAfterResponse = library.sourceAvailability(summary.identity.sourceId)
            val policyAfterResponse = library.sourceRemotePolicy(summary.identity.sourceId)
            val leaseStillValid = lease.matches(
                activeAfterResponse?.packageSha256,
                activeAfterResponse?.manifest?.version?.original,
                availabilityAfterResponse?.verifiedVersion,
                policyAfterResponse?.capabilitySetFingerprint,
                availabilityAfterResponse?.generation,
                ownerAfterResponse,
            ) && availabilityAfterResponse?.available == true
            if (!leaseStillValid || !library.transitionRemoteAdd(reconciliationId, RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.CONFIRMED, Instant.now())) {
                library.transitionRemoteAdd(reconciliationId, RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.UNRESOLVED, Instant.now())
                if (selectedBook?.identity == summary.identity) selectedBookReconciliation = RemoteReconciliationState.UNRESOLVED
                RemoteAddUiResult.Unresolved
            } else {
                if (selectedBook?.identity == summary.identity) selectedBookReconciliation = RemoteReconciliationState.CONFIRMED
                RemoteAddUiResult.Confirmed
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                settleFailedRemoteAdd(token, reconciliationId, summary.identity, null)
            }
            throw cancelled
        } catch (error: Throwable) {
            settleFailedRemoteAdd(token, reconciliationId, summary.identity, (error as? SourceException)?.diagnostic?.correlationId)
        }
    }

    suspend fun removeSelectedBook(): Boolean {
        val identity = selectedBook?.identity ?: return false
        val removed = library.removeFromLibrary(identity)
        if (removed) {
            selectedBookInLibrary = false
            selectedBookReconciliation = null
        }
        return removed
    }

    fun updateQuery(value: String) {
        query = value.take(100)
    }

    suspend fun reopenWithStoredCredentials() {
        val packageInfo = synchronized(clientLock) {
            checkOpen()
            activePackage
        } ?: return
        closeActiveClient()
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
        val source = requireClientOrNull() ?: return setSearchFailure(SourceErrorCode.EXTENSION_RUNTIME_FAILURE, "source-not-open")
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
        val active = synchronized(clientLock) { activePackage }
        val addPolicy = active?.manifest?.capabilities?.remoteLibrary?.policies?.get(RemoteOperation.ADD)
        val availability = library.sourceAvailability(book.identity.sourceId)
        selectedBookAddWritesRemote = library.sourceRemotePolicy(book.identity.sourceId)?.addWritebackEnabled == true &&
            availability?.available == true && active != null && addPolicy != null && remoteAddCredentialReady(active, addPolicy.origin)
        detailState = try {
            val detail = requireClient().detail(book.identity.remoteBookId, offlineOnly)
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
        val entry = library.libraryEntries().firstOrNull { it.book.identity == book.identity }
        selectedBookInLibrary = entry != null
        selectedBookReconciliation = entry?.reconciliation
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

    private suspend fun settleFailedRemoteAdd(
        token: String,
        reconciliationId: String,
        identity: BookIdentity,
        diagnosticId: String?,
    ): RemoteAddUiResult = withContext(NonCancellable) {
        DirectActionTokenRegistry.process.revoke(token)
        val cancelled = library.transitionRemoteAdd(
            reconciliationId,
            RemoteReconciliationState.PENDING_USER_ACTION,
            RemoteReconciliationState.CANCELLED,
            Instant.now(),
        )
        if (cancelled) {
            if (selectedBook?.identity == identity) selectedBookReconciliation = RemoteReconciliationState.CANCELLED
            RemoteAddUiResult.Cancelled
        } else {
            library.transitionRemoteAdd(
                reconciliationId,
                RemoteReconciliationState.IN_FLIGHT,
                RemoteReconciliationState.UNRESOLVED,
                Instant.now(),
                diagnosticId,
            )
            if (selectedBook?.identity == identity) selectedBookReconciliation = RemoteReconciliationState.UNRESOLVED
            RemoteAddUiResult.Unresolved
        }
    }

    private fun remoteAddCredentialReady(
        packageInfo: VerifiedHxpPackage,
        origin: org.tsuyomi.shared.sourcecontract.HttpsOrigin,
    ): Boolean = runCatching {
        credentialStore.getSnapshot(SourceCredentialPartition(packageInfo.manifest.sourceId.value, origin)) != null
    }.getOrDefault(false)

    private fun resetSourceState() {
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
        selectedBookInLibrary = false
        selectedBookReconciliation = null
        selectedBookAddWritesRemote = false
        remoteLibraryBooks = emptyList()
    }

    private fun requireClient(): SourceFlowSession = synchronized(clientLock) {
        checkOpen()
        checkNotNull(client) { "Source is not open" }
    }

    private fun requireClientOrNull(): SourceFlowSession? = synchronized(clientLock) {
        checkOpen()
        client
    }

    private fun closeActiveClient() {
        val activeClient = synchronized(clientLock) {
            checkOpen()
            openGeneration += 1
            val previous = client
            client = null
            activePackage = null
            previous
        }
        activeClient?.close()
    }

    override fun close() {
        val activeClient = synchronized(clientLock) {
            if (closed) return
            closed = true
            openGeneration += 1
            val previous = client
            client = null
            activePackage = null
            previous
        }
        activeClient?.close()
    }

    private fun checkOpen() {
        check(!closed) { "Source flow is closed" }
    }

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

sealed interface RemoteLibraryPullResult {
    data class Success(val total: Int, val newlyAdded: Int) : RemoteLibraryPullResult
    data class Failure(val safeCode: String) : RemoteLibraryPullResult
    data object LoginRequired : RemoteLibraryPullResult
    data object VerificationRequired : RemoteLibraryPullResult
    data object Cancelled : RemoteLibraryPullResult
}

sealed interface RemoteAddUiResult {
    data object LocalOnly : RemoteAddUiResult
    data object Confirmed : RemoteAddUiResult
    data object Unresolved : RemoteAddUiResult
    data object Cancelled : RemoteAddUiResult
    data class Failure(val safeCode: String) : RemoteAddUiResult
}

enum class SourceRestorationTarget { SEARCH, DETAIL, DIRECTORY, READER }
