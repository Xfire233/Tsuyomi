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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.core.database.RemoteAddRequest
import org.tsuyomi.core.database.RemoteMutationRequest
import org.tsuyomi.shared.librarydomain.RemoteReconciliationState
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.shared.librarydomain.SourceAvailability
import org.tsuyomi.shared.librarydomain.RemoteMirrorBookSnapshot
import org.tsuyomi.core.database.RemoteMirrorReplaceRequest
import org.tsuyomi.shared.librarydomain.RemoteMirrorSnapshot
import org.tsuyomi.shared.librarydomain.RemoteMirrorTargetSnapshot
import org.tsuyomi.shared.librarydomain.SourceRemotePolicy
import org.tsuyomi.core.network.DirectActionBinding
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddOutcome
import org.tsuyomi.shared.sourcecontract.RemoteLibraryRemoveOutcome
import org.tsuyomi.shared.sourcecontract.RemoteLibraryMoveOutcome
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.source.extensionmanager.HxpRemoteOperationPolicy
import org.tsuyomi.source.extensionmanager.RemoteOperation
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

internal class SourceRemoteLibraryCoordinator(
    context: Context,
    private val library: RoomLibraryRepository,
    private val sessionOwner: SourceSessionOwner,
) {
    private val credentialStore = VerifiedBrowserSessionStore(context)
    private val remoteAddMutex = Mutex()
    private val remoteRefreshMutex = Mutex()
    private var selectedIdentity: BookIdentity.Remote? = null
    private data class CapturedRemoteClient(
        val active: ActiveSourceSession,
        val packageInfo: VerifiedHxpPackage,
        val client: SourceFlowSession,
        val sourceId: String,
        val lease: RemoteExecutionLease,
    )

    private sealed interface RemoteClientCapture {
        data class Ready(val session: CapturedRemoteClient) : RemoteClientCapture
        data class Failure(val safeCode: String) : RemoteClientCapture
    }

    var selectedLibraryEntry: LibraryEntry? by mutableStateOf(null)
        private set
    var selectedBookInLibrary: Boolean by mutableStateOf(false)
        private set
    var selectedBookReconciliation: RemoteReconciliationState? by mutableStateOf(null)
        private set
    var selectedBookReconciliationOperation: String? by mutableStateOf(null)
        private set
    var selectedBookAddWritesRemote: Boolean by mutableStateOf(false)
        private set
    var selectedBookRemoveWritesRemote: Boolean by mutableStateOf(false)
        private set
    var selectedBookMoveWritesRemote: Boolean by mutableStateOf(false)
        private set

    fun reset() {
        selectedIdentity = null
        selectedLibraryEntry = null
        selectedBookInLibrary = false
        selectedBookReconciliation = null
        selectedBookReconciliationOperation = null
        selectedBookAddWritesRemote = false
        selectedBookRemoveWritesRemote = false
        selectedBookMoveWritesRemote = false
    }

    fun beginSelection(identity: BookIdentity.Remote) {
        selectedIdentity = identity
        selectedLibraryEntry = null
        selectedBookInLibrary = false
        selectedBookReconciliation = null
        selectedBookReconciliationOperation = null
        selectedBookAddWritesRemote = false
        selectedBookRemoveWritesRemote = false
        selectedBookMoveWritesRemote = false
    }

    suspend fun refreshSelection(summary: SourceBookSummary) {
        if (selectedIdentity != summary.identity) return
        val activePackage = sessionOwner.active()?.packageInfo
        val addPolicy = activePackage?.manifest?.capabilities?.remoteLibrary?.policies?.get(RemoteOperation.ADD)
        val availability = library.sourceAvailability(summary.identity.sourceId)
        val addWritesRemote = library.sourceRemotePolicy(summary.identity.sourceId)?.addWritebackEnabled == true &&
            availability?.available == true && activePackage != null && addPolicy != null &&
            remoteAddCredentialReady(activePackage, addPolicy.origin)
        val removePolicy = activePackage?.manifest?.capabilities?.remoteLibrary?.policies?.get(RemoteOperation.REMOVE)
        val removeWritesRemote = library.sourceRemotePolicy(summary.identity.sourceId)?.removeWritebackEnabled == true &&
            availability?.available == true && activePackage != null && removePolicy != null &&
            remoteAddCredentialReady(activePackage, removePolicy.origin)
        val movePolicy = activePackage?.manifest?.capabilities?.remoteLibrary?.policies?.get(RemoteOperation.MOVE)
        val moveWritesRemote = library.sourceRemotePolicy(summary.identity.sourceId)?.moveWritebackEnabled == true &&
            availability?.available == true && activePackage != null && movePolicy != null &&
            remoteAddCredentialReady(activePackage, movePolicy.origin)
        val entry = library.libraryEntry(summary.identity)
        val reconciliation = library.bookReconciliation(summary.identity.sourceId, summary.identity.remoteBookId)
        if (selectedIdentity == summary.identity) {
            selectedLibraryEntry = entry
            selectedBookAddWritesRemote = addWritesRemote
            selectedBookRemoveWritesRemote = removeWritesRemote
            selectedBookMoveWritesRemote = moveWritesRemote
            selectedBookInLibrary = entry?.localMembership == true
            selectedBookReconciliation = reconciliation?.state
            selectedBookReconciliationOperation = reconciliation?.operation
        }
    }

    suspend fun pull(
        packageInfo: VerifiedHxpPackage,
        aggregateLimitBytes: Long = MAX_REMOTE_LIBRARY_AGGREGATE_BYTES,
    ): RemoteLibraryPullResult {
        require(aggregateLimitBytes in 1..MAX_REMOTE_LIBRARY_AGGREGATE_BYTES)
        return when (val captured = captureRemoteClient(packageInfo)) {
            is RemoteClientCapture.Failure -> RemoteLibraryPullResult.Failure(captured.safeCode)
            is RemoteClientCapture.Ready -> pullCaptured(captured.session, aggregateLimitBytes)
        }
    }

    suspend fun refreshRemoteLibrarySnapshot(
        packageInfo: VerifiedHxpPackage,
    ): RemoteLibrarySnapshotRefreshResult = remoteRefreshMutex.withLock refreshLock@ {
        remoteAddMutex.withLock snapshotLock@ {
            when (val captured = captureRemoteClient(packageInfo)) {
                is RemoteClientCapture.Failure -> return@snapshotLock RemoteLibrarySnapshotRefreshResult.Failure(captured.safeCode)
                is RemoteClientCapture.Ready -> {
                    val session = captured.session
                    val pull = pullCaptured(session, MAX_REMOTE_LIBRARY_AGGREGATE_BYTES)
                    if (pull !is RemoteLibraryPullResult.Success) return@snapshotLock pull.toSnapshotRefreshResult()
                    if (!remoteClientLeaseStillValid(session)) {
                        return@snapshotLock RemoteLibrarySnapshotRefreshResult.Failure("source-changed")
                    }
                    val targetRead = readRemoteTargets(session)
                    val targets = when (targetRead) {
                        is RemoteTargetReadResult.Success -> targetRead.targets
                        RemoteTargetReadResult.Unsupported -> library.remoteMirrorSnapshot(session.sourceId)
                            ?.targets
                            ?.map { RemoteTarget(it.targetId, it.displayName, it.parentId, it.kind) }
                            .orEmpty()
                        RemoteTargetReadResult.LoginRequired -> return@snapshotLock RemoteLibrarySnapshotRefreshResult.LoginRequired
                        RemoteTargetReadResult.VerificationRequired -> return@snapshotLock RemoteLibrarySnapshotRefreshResult.VerificationRequired
                        RemoteTargetReadResult.Cancelled -> return@snapshotLock RemoteLibrarySnapshotRefreshResult.Cancelled
                        is RemoteTargetReadResult.Failure -> return@snapshotLock RemoteLibrarySnapshotRefreshResult.Failure(targetRead.safeCode)
                    }
                    if (!remoteClientLeaseStillValid(session)) {
                        return@snapshotLock RemoteLibrarySnapshotRefreshResult.Failure("source-changed")
                    }
                    val savedBooks = persistRemoteMirrorSnapshot(session, pull.books, targets)
                        ?: return@snapshotLock RemoteLibrarySnapshotRefreshResult.Failure("source-changed")
                    RemoteLibrarySnapshotRefreshResult.Success(savedBooks, targets)
                }
            }
        }
    }

    private suspend fun pullCaptured(
        session: CapturedRemoteClient,
        aggregateLimitBytes: Long,
    ): RemoteLibraryPullResult {
        if (session.packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.READ] == null) {
            return RemoteLibraryPullResult.Failure("remote-read-not-granted")
        }
        val seenCursors = hashSetOf<String>()
        val summaries = linkedMapOf<BookIdentity, SourceBookSummary>()
        var cursor: String? = null
        var aggregateBytes = 0L
        while (true) {
            if (!remoteClientLeaseStillValid(session)) return RemoteLibraryPullResult.Failure("source-changed")
            val page = try {
                session.client.listRemoteLibrary(cursor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SourceException) {
                if (!remoteClientLeaseStillValid(session)) return RemoteLibraryPullResult.Failure("source-changed")
                return error.toPullResult()
            }
            if (!remoteClientLeaseStillValid(session)) return RemoteLibraryPullResult.Failure("source-changed")
            val known = summaries.size
            page.items.forEach { item ->
                if (item.identity.sourceId != session.sourceId) return RemoteLibraryPullResult.Failure("source-identity-mismatch")
                aggregateBytes += normalizedSize(item)
                if (aggregateBytes > aggregateLimitBytes) {
                    return RemoteLibraryPullResult.Failure("aggregate-limit")
                }
                summaries.putIfAbsent(item.identity, item)
                if (summaries.size > MAX_REMOTE_LIBRARY_RECORDS) {
                    return RemoteLibraryPullResult.Failure("record-limit")
                }
            }
            if (page.complete) {
                if (page.nextCursor != null) return RemoteLibraryPullResult.Failure("complete-with-cursor")
                return RemoteLibraryPullResult.Success(summaries.values.toList())
            }
            if (summaries.size == known) return RemoteLibraryPullResult.Failure("no-progress")
            val next = page.nextCursor ?: return RemoteLibraryPullResult.Failure("incomplete-page")
            if (!seenCursors.add(next)) return RemoteLibraryPullResult.Failure("duplicate-cursor")
            cursor = next
        }
    }

    private suspend fun captureRemoteClient(expectedPackage: VerifiedHxpPackage?): RemoteClientCapture {
        val active = sessionOwner.active() ?: return RemoteClientCapture.Failure("source-not-open")
        val packageInfo = active.packageInfo
        if (expectedPackage != null && (
                packageInfo.packageSha256 != expectedPackage.packageSha256 ||
                    packageInfo.manifest.sourceId.value != expectedPackage.manifest.sourceId.value
                )
        ) return RemoteClientCapture.Failure("source-changed")
        val client = sessionOwner.clientFor(active) ?: return RemoteClientCapture.Failure("source-changed")
        val sourceId = packageInfo.manifest.sourceId.value
        val availability = library.sourceAvailability(sourceId)
            ?: return RemoteClientCapture.Failure("source-unavailable")
        val policy = library.sourceRemotePolicy(sourceId)
            ?: return RemoteClientCapture.Failure("remote-policy-missing")
        if (!availability.available || availability.verifiedVersion != packageInfo.manifest.version.original) {
            return RemoteClientCapture.Failure("source-unavailable")
        }
        val lease = RemoteExecutionLease(
            packageInfo.packageSha256,
            packageInfo.manifest.version.original,
            policy.capabilitySetFingerprint,
            availability.generation,
            active.ownerGeneration,
        )
        val captured = CapturedRemoteClient(active, packageInfo, client, sourceId, lease)
        if (!remoteClientLeaseStillValid(captured)) return RemoteClientCapture.Failure("source-changed")
        return RemoteClientCapture.Ready(captured)
    }

    private suspend fun readRemoteTargets(session: CapturedRemoteClient): RemoteTargetReadResult {
        if (session.packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.TARGETS] == null) {
            return if (remoteClientLeaseStillValid(session)) RemoteTargetReadResult.Unsupported
            else RemoteTargetReadResult.Failure("source-changed")
        }
        if (!remoteClientLeaseStillValid(session)) return RemoteTargetReadResult.Failure("source-changed")
        val targets = try {
            session.client.listRemoteTargets()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SourceException) {
            if (!remoteClientLeaseStillValid(session)) return RemoteTargetReadResult.Failure("source-changed")
            return error.toTargetReadResult()
        }
        if (!remoteClientLeaseStillValid(session)) return RemoteTargetReadResult.Failure("source-changed")
        if (targets.sourceId != session.sourceId) return RemoteTargetReadResult.Failure("source-identity-mismatch")
        return RemoteTargetReadResult.Success(session.sourceId, targets.targets)
    }

    private suspend fun persistRemoteMirrorSnapshot(
        session: CapturedRemoteClient,
        books: List<SourceBookSummary>,
        targets: List<RemoteTarget>,
        updatedAt: Instant = Instant.now(),
    ): List<SourceBookSummary>? {
        if (!remoteClientLeaseStillValid(session)) return null
        val normalizedBooks = normalizeRemoteTargetMembership(books, targets)
        val enrichedBooks = normalizedBooks.map { summary ->
            val existing = library.book(summary.identity)
            summary.copy(
                author = summary.author ?: existing?.author,
                coverUrl = summary.coverUrl ?: existing?.coverUrl,
                canonicalUrl = summary.canonicalUrl.takeIf(String::isNotBlank) ?: existing?.canonicalUrl.orEmpty(),
            )
        }
        if (!remoteClientLeaseStillValid(session)) return null
        val saved = library.saveRemoteMirrorSnapshotIfSourceCurrent(
            RemoteMirrorReplaceRequest(
                sourceId = session.sourceId,
                sourceName = session.packageInfo.manifest.displayName,
                books = enrichedBooks.map { RemoteMirrorBookSnapshot(it.toLibraryBook(updatedAt), it.remoteTargetId) },
                targets = targets.map {
                    RemoteMirrorTargetSnapshot(
                        sourceId = session.sourceId,
                        targetId = it.targetId,
                        displayName = it.displayName,
                        parentId = it.parentId,
                        kind = it.kind,
                        updatedAtEpochSecond = updatedAt.epochSecond,
                    )
                },
                updatedAt = updatedAt,
            ),
            expectedVersion = session.lease.packageVersion,
            expectedGeneration = session.lease.sourceGeneration,
            expectedCapabilityFingerprint = session.lease.capabilitySetFingerprint,
        )
        return if (saved && remoteClientLeaseStillValid(session)) enrichedBooks else null
    }

    suspend fun copyToLocal(
        summaries: Collection<SourceBookSummary>,
        importedAt: Instant = Instant.now(),
    ): RemoteLibraryCopyResult {
        val distinct = summaries.distinctBy(SourceBookSummary::identity)
        var added = 0
        distinct.forEach { summary ->
            if (library.addToLibrary(summary.toLibraryBook(importedAt))) added++
        }
        return RemoteLibraryCopyResult(total = distinct.size, added = added)
    }

    suspend fun addLocalBook(summary: SourceBookSummary?, importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteAddMutex.withLock {
            val selected = summary?.takeIf { selectedIdentity == it.identity }
                ?: return@withLock RemoteAddUiResult.Failure("book-not-selected")
            if (selectedBookInLibrary) return@withLock RemoteAddUiResult.Failure("book-already-added")
            library.addToLibrary(selected.toLibraryBook(importedAt))
            if (selectedIdentity == selected.identity) {
                selectedLibraryEntry = library.libraryEntry(selected.identity)
                selectedBookInLibrary = true
                selectedBookReconciliation = null
                selectedBookReconciliationOperation = null
            }
            RemoteAddUiResult.LocalOnly
        }

    suspend fun toggleReadLater(summary: SourceBookSummary?, importedAt: Instant = Instant.now()): Boolean =
        remoteAddMutex.withLock {
            val selected = summary?.takeIf { selectedIdentity == it.identity }
                ?: error("Book is not selected")
            val current = library.libraryEntry(selected.identity)
            val next = !(current?.readLater ?: false)
            if (next && current?.localMembership != true) {
                library.addToLibrary(selected.toLibraryBook(importedAt))
            }
            library.setReadLater(selected.identity, next)
            val updated = requireNotNull(library.libraryEntry(selected.identity)) { "Book is not in library" }
            if (selectedIdentity == selected.identity) {
                selectedLibraryEntry = updated
                selectedBookInLibrary = updated.localMembership
                selectedBookReconciliation = updated.reconciliation
                selectedBookReconciliationOperation = updated.reconciliationOperation
            }
            next
        }

    suspend fun addBookToWebsite(summary: SourceBookSummary?, importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteAddMutex.withLock {
            val selected = summary ?: return@withLock RemoteAddUiResult.Failure("book-not-selected")
            addBookToWebsiteLocked(selected, importedAt)
        }

    suspend fun retryBook(summary: SourceBookSummary?, importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteAddMutex.withLock {
            val selected = summary?.takeIf { selectedIdentity == it.identity }
                ?: return@withLock RemoteAddUiResult.Failure("book-not-selected")
            val session = when (val captured = captureRemoteClient(null)) {
                is RemoteClientCapture.Failure -> return@withLock RemoteAddUiResult.Failure(captured.safeCode)
                is RemoteClientCapture.Ready -> captured.session
            }
            val existing = library.book(selected.identity)
                ?: return@withLock RemoteAddUiResult.Failure("book-not-local")
            retryRemoteAddLocked(selected, existing, importedAt, expectedSession = session)
        }

    suspend fun retryLocalBook(book: LibraryBook, importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteAddMutex.withLock {
            val identity = book.identity as? BookIdentity.Remote
                ?: return@withLock RemoteAddUiResult.Failure("book-not-remote")
            val session = when (val captured = captureRemoteClient(null)) {
                is RemoteClientCapture.Failure -> return@withLock RemoteAddUiResult.Failure(captured.safeCode)
                is RemoteClientCapture.Ready -> captured.session
            }
            if (session.sourceId != identity.sourceId) {
                return@withLock RemoteAddUiResult.Failure("remote-add-source-not-open")
            }
            val currentEntry = library.libraryEntries().firstOrNull { it.book.identity == book.identity }
                ?: return@withLock RemoteAddUiResult.Failure("book-not-local")
            val currentBook = currentEntry.book
            val summary = SourceBookSummary(
                identity = identity,
                title = currentBook.title,
                author = currentBook.author,
                coverUrl = currentBook.coverUrl,
                canonicalUrl = currentBook.canonicalUrl.orEmpty(),
            )
            retryRemoteAddLocked(summary, currentBook, importedAt, expectedSession = session)
        }

    suspend fun removeBook(summary: SourceBookSummary?): Boolean {
        val selected = summary ?: return false
        val removed = library.removeFromLibrary(selected.identity)
        if (removed && selectedIdentity == selected.identity) refreshSelection(selected)
        return removed
    }

    suspend fun authorizeWriteback(sourceId: String, operation: String, enabled: Boolean): Boolean {
        val policy = library.sourceRemotePolicy(sourceId) ?: return false
        val fingerprint = policy.capabilitySetFingerprint
        when (operation) {
            "remove" -> library.setRemoveWritebackEnabled(sourceId, fingerprint, enabled)
            "move" -> library.setMoveWritebackEnabled(sourceId, fingerprint, enabled)
            "add" -> library.setAddWritebackEnabled(sourceId, fingerprint, enabled)
            else -> return false
        }
        selectedIdentity?.let { identity ->
            if (identity.sourceId == sourceId) {
                val entry = library.libraryEntry(identity)
                if (entry != null) {
                    val summary = SourceBookSummary(
                        identity = identity,
                        title = entry.book.title,
                        author = entry.book.author,
                        coverUrl = entry.book.coverUrl,
                        canonicalUrl = entry.book.canonicalUrl.orEmpty(),
                    )
                    refreshSelection(summary)
                }
            }
        }
        return true
    }

    suspend fun writebackAuthorized(sourceId: String, operation: String): Boolean {
        val active = sessionOwner.active() ?: return false
        if (active.packageInfo.manifest.sourceId.value != sourceId) return false
        val policy = library.sourceRemotePolicy(sourceId) ?: return false
        val availability = library.sourceAvailability(sourceId) ?: return false
        if (!availability.available || availability.verifiedVersion != active.packageInfo.manifest.version.original) return false
        val remoteOperation = when (operation) {
            "add" -> RemoteOperation.ADD
            "move" -> RemoteOperation.MOVE
            "remove" -> RemoteOperation.REMOVE
            else -> return false
        }
        val operationPolicy = active.packageInfo.manifest.capabilities.remoteLibrary.policies[remoteOperation] ?: return false
        val receiptEnabled = when (operation) {
            "add" -> policy.addWritebackEnabled
            "move" -> policy.moveWritebackEnabled
            else -> policy.removeWritebackEnabled
        }
        return receiptEnabled && remoteAddCredentialReady(active.packageInfo, operationPolicy.origin)
    }

    suspend fun removeBookFromWebsite(summary: SourceBookSummary?, importedAt: Instant = Instant.now()): RemoteMutationUiResult =
        remoteAddMutex.withLock {
            val selected = summary ?: return@withLock RemoteMutationUiResult.Failure("book-not-selected")
            val session = when (val captured = captureRemoteClient(null)) {
                is RemoteClientCapture.Failure -> return@withLock RemoteMutationUiResult.Failure(captured.safeCode)
                is RemoteClientCapture.Ready -> captured.session
            }
            if (remoteMutationBlocked(selected.identity)) {
                return@withLock RemoteMutationUiResult.Failure("remote-mutation-blocked-unresolved")
            }
            val packageInfo = session.packageInfo
            if (packageInfo.manifest.sourceId.value != selected.identity.sourceId) {
                return@withLock RemoteMutationUiResult.Failure("source-changed")
            }
            val policy = library.sourceRemotePolicy(selected.identity.sourceId)
            val availability = library.sourceAvailability(selected.identity.sourceId)
            val removePolicy = packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.REMOVE]
            val credentialReady = removePolicy != null && remoteAddCredentialReady(packageInfo, removePolicy.origin)
            if (policy?.removeWritebackEnabled != true || availability?.available != true || removePolicy == null ||
                policy.capabilitySetFingerprint.isBlank() || !credentialReady
            ) {
                return@withLock RemoteMutationUiResult.Failure("remote-remove-not-authorized")
            }
            executeRemoteRemove(selected, packageInfo, policy, availability, removePolicy, importedAt, expectedSession = session).also { result ->
                if (result is RemoteMutationUiResult.Confirmed) library.removeRemoteMirrorBook(selected.identity)
            }
        }

    suspend fun moveBookOnWebsite(
        summary: SourceBookSummary?,
        targetId: String,
        targetName: String,
        importedAt: Instant = Instant.now(),
    ): RemoteMutationUiResult = remoteAddMutex.withLock {
        val selected = summary ?: return@withLock RemoteMutationUiResult.Failure("book-not-selected")
        val session = when (val captured = captureRemoteClient(null)) {
            is RemoteClientCapture.Failure -> return@withLock RemoteMutationUiResult.Failure(captured.safeCode)
            is RemoteClientCapture.Ready -> captured.session
        }
        if (remoteMutationBlocked(selected.identity)) {
            return@withLock RemoteMutationUiResult.Failure("remote-mutation-blocked-unresolved")
        }
        executeAuthorizedMoveLocked(selected, targetId, targetName, importedAt, expectedSession = session)
    }

    suspend fun listRemoteTargets(): RemoteTargetReadResult = when (val captured = captureRemoteClient(null)) {
        is RemoteClientCapture.Failure -> RemoteTargetReadResult.Failure(captured.safeCode)
        is RemoteClientCapture.Ready -> readRemoteTargets(captured.session)
    }

    suspend fun remoteMirrorSnapshot(sourceId: String): RemoteMirrorSnapshot? = library.remoteMirrorSnapshot(sourceId)

    suspend fun addBookToWebsiteTarget(
        summary: SourceBookSummary,
        targetId: String,
        targetName: String,
        defaultTargetId: String,
        importedAt: Instant = Instant.now(),
    ): RemoteTargetedAddResult = remoteAddMutex.withLock {
        val session = when (val captured = captureRemoteClient(null)) {
            is RemoteClientCapture.Failure -> return@withLock RemoteTargetedAddResult.Failure(captured.safeCode)
            is RemoteClientCapture.Ready -> captured.session
        }
        if (session.sourceId != summary.identity.sourceId) {
            return@withLock RemoteTargetedAddResult.Failure("source-changed")
        }
        val continuationTargetId = targetId.takeIf { it != defaultTargetId }
        val current = library.bookReconciliation(summary.identity.sourceId, summary.identity.remoteBookId)
        val add = if (current?.operation.equals("ADD", ignoreCase = true) &&
            current?.state == RemoteReconciliationState.CONFIRMED && current.targetId != null
        ) {
            if (current.targetId == continuationTargetId) RemoteAddUiResult.Confirmed
            else RemoteAddUiResult.Failure("targeted-add-destination-mismatch")
        } else {
            addBookToWebsiteLocked(
                summary,
                importedAt,
                continuationTargetId,
                targetName.takeIf { continuationTargetId != null },
                expectedSession = session,
            )
        }
        when (add) {
            RemoteAddUiResult.Confirmed -> {
                library.ensureRemoteMirrorBinding(session.sourceId, session.packageInfo.manifest.displayName, importedAt)
                library.upsertRemoteMirrorBook(summary.toLibraryBook(importedAt), defaultTargetId, importedAt)
                if (continuationTargetId == null) {
                    RemoteTargetedAddResult.Confirmed
                } else {
                    when (val move = executeAuthorizedMoveLocked(summary, targetId, targetName, importedAt, expectedSession = session)) {
                        RemoteMutationUiResult.Confirmed -> RemoteTargetedAddResult.Confirmed
                        RemoteMutationUiResult.Unresolved,
                        RemoteMutationUiResult.Cancelled,
                        -> RemoteTargetedAddResult.Partial(defaultTargetId, targetId, targetName)
                        is RemoteMutationUiResult.Failure -> RemoteTargetedAddResult.Partial(
                            defaultTargetId,
                            targetId,
                            targetName,
                            move.safeCode,
                        )
                    }
                }
            }
            RemoteAddUiResult.Unresolved -> RemoteTargetedAddResult.Unresolved
            RemoteAddUiResult.Cancelled -> RemoteTargetedAddResult.Cancelled
            is RemoteAddUiResult.Failure -> RemoteTargetedAddResult.Failure(add.safeCode)
            RemoteAddUiResult.LocalOnly -> RemoteTargetedAddResult.Failure("local-only")
        }
    }

    suspend fun retryRemoteMutation(
        summary: SourceBookSummary?,
        importedAt: Instant = Instant.now(),
        expectedOperation: RemoteOperation? = null,
    ): RemoteMutationUiResult =
        remoteAddMutex.withLock {
            val selected = summary ?: return@withLock RemoteMutationUiResult.Failure("book-not-selected")
            val capturedSession = when (val captured = captureRemoteClient(null)) {
                is RemoteClientCapture.Failure -> return@withLock RemoteMutationUiResult.Failure(captured.safeCode)
                is RemoteClientCapture.Ready -> captured.session
            }
            if (capturedSession.sourceId != selected.identity.sourceId) {
                return@withLock RemoteMutationUiResult.Failure("source-changed")
            }
            val record = library.bookReconciliation(selected.identity.sourceId, selected.identity.remoteBookId)
                ?: return@withLock RemoteMutationUiResult.Failure("no-reconciliation-record")
            val targetedAddContinuation = record.operation.equals("ADD", ignoreCase = true) &&
                record.state == RemoteReconciliationState.CONFIRMED && record.targetId != null
            val effectiveOperation = if (targetedAddContinuation) "MOVE" else record.operation
            if (expectedOperation != null && !effectiveOperation.equals(expectedOperation.name, ignoreCase = true)) {
                return@withLock RemoteMutationUiResult.Failure("reconciliation-operation-changed")
            }
            if (!targetedAddContinuation && record.state !in RETRYABLE_RECONCILIATION_STATES) {
                return@withLock RemoteMutationUiResult.Failure("reconciliation-not-retryable")
            }
            val retryingUnresolvedId = record.id.takeIf { record.state == RemoteReconciliationState.UNRESOLVED }
            if (selectedIdentity == selected.identity) {
                selectedBookReconciliationOperation = record.operation
            }
            when (record.operation.lowercase()) {
                "remove" -> {
                    val packageInfo = capturedSession.packageInfo
                    val policy = library.sourceRemotePolicy(selected.identity.sourceId)
                    val availability = library.sourceAvailability(selected.identity.sourceId)
                    val removePolicy = packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.REMOVE]
                    val credentialReady = removePolicy != null && remoteAddCredentialReady(packageInfo, removePolicy.origin)
                    if (policy?.removeWritebackEnabled != true || availability?.available != true || removePolicy == null || !credentialReady) {
                        return@withLock RemoteMutationUiResult.Failure("remote-remove-not-authorized")
                    }
                    executeRemoteRemove(
                        selected,
                        packageInfo,
                        policy,
                        availability,
                        removePolicy,
                        importedAt,
                        retryingUnresolvedId,
                        expectedSession = capturedSession,
                    ).also { result ->
                        if (result is RemoteMutationUiResult.Confirmed) library.removeRemoteMirrorBook(selected.identity)
                    }
                }
                "move" -> {
                    val targetId = record.targetId ?: return@withLock RemoteMutationUiResult.Failure("missing-target-id")
                    executeAuthorizedMoveLocked(
                        selected,
                        targetId,
                        record.targetName ?: targetId,
                        importedAt,
                        retryingUnresolvedId,
                        expectedSession = capturedSession,
                    )
                }
                "add" -> {
                    val targetId = record.targetId
                    if (record.state == RemoteReconciliationState.CONFIRMED && targetId != null) {
                        executeAuthorizedMoveLocked(
                            selected,
                            targetId,
                            record.targetName ?: targetId,
                            importedAt,
                            expectedSession = capturedSession,
                        )
                    } else {
                        val existing = library.book(selected.identity)
                            ?: return@withLock RemoteMutationUiResult.Failure("book-not-local")
                        when (val addResult = retryRemoteAddLocked(selected, existing, importedAt, expectedSession = capturedSession)) {
                            is RemoteAddUiResult.Confirmed -> if (targetId == null) {
                                RemoteMutationUiResult.Confirmed
                            } else {
                                executeAuthorizedMoveLocked(
                                    selected,
                                    targetId,
                                    record.targetName ?: targetId,
                                    importedAt,
                                    expectedSession = capturedSession,
                                )
                            }
                            is RemoteAddUiResult.Unresolved -> RemoteMutationUiResult.Unresolved
                            is RemoteAddUiResult.Cancelled -> RemoteMutationUiResult.Cancelled
                            is RemoteAddUiResult.Failure -> RemoteMutationUiResult.Failure(addResult.safeCode)
                            is RemoteAddUiResult.LocalOnly -> RemoteMutationUiResult.Failure("local-only")
                        }
                    }
                }
                else -> RemoteMutationUiResult.Failure("unknown-reconciliation-operation")
            }
        }

    suspend fun acknowledgeUnresolved(identity: BookIdentity.Remote): Boolean = remoteAddMutex.withLock {
        val record = library.bookReconciliation(identity.sourceId, identity.remoteBookId) ?: return@withLock false
        if (record.state != RemoteReconciliationState.UNRESOLVED || record.operation.equals("ADD", ignoreCase = true)) {
            return@withLock false
        }
        val ok = library.cancelUnresolvedMutations(
            identity,
            record.operation,
            Instant.now(),
        )
        if (ok) updateReconciliation(identity, RemoteReconciliationState.CANCELLED)
        ok
    }
    private suspend fun executeAuthorizedMoveLocked(
        selected: SourceBookSummary,
        targetId: String,
        targetName: String,
        importedAt: Instant,
        retryingUnresolvedId: String? = null,
        expectedSession: CapturedRemoteClient? = null,
    ): RemoteMutationUiResult {
        val session = expectedSession ?: when (val captured = captureRemoteClient(null)) {
            is RemoteClientCapture.Failure -> return RemoteMutationUiResult.Failure(captured.safeCode)
            is RemoteClientCapture.Ready -> captured.session
        }
        if (session.sourceId != selected.identity.sourceId || !remoteClientLeaseStillValid(session)) {
            return RemoteMutationUiResult.Failure("source-changed")
        }
        val packageInfo = session.packageInfo
        val policy = library.sourceRemotePolicy(selected.identity.sourceId)
        val availability = library.sourceAvailability(selected.identity.sourceId)
        val movePolicy = packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.MOVE]
        val credentialReady = movePolicy != null && remoteAddCredentialReady(packageInfo, movePolicy.origin)
        if (policy?.moveWritebackEnabled != true || availability?.available != true || movePolicy == null ||
            policy.capabilitySetFingerprint.isBlank() || !credentialReady
        ) {
            return RemoteMutationUiResult.Failure("remote-move-not-authorized")
        }
        return executeRemoteMove(
            selected,
            targetId,
            targetName,
            packageInfo,
            policy,
            availability,
            movePolicy,
            importedAt,
            retryingUnresolvedId,
            session,
        ).also { result ->
            if (result is RemoteMutationUiResult.Confirmed &&
                !library.updateRemoteMirrorBookTarget(selected.identity, targetId, importedAt)
            ) {
                library.upsertRemoteMirrorBook(selected.toLibraryBook(importedAt), targetId, importedAt)
            }
        }
    }


    private suspend fun executeRemoteRemove(
        summary: SourceBookSummary,
        packageInfo: VerifiedHxpPackage,
        policy: SourceRemotePolicy,
        availability: SourceAvailability,
        removePolicy: HxpRemoteOperationPolicy,
        importedAt: Instant,
        retryingUnresolvedId: String? = null,
        expectedSession: CapturedRemoteClient? = null,
    ): RemoteMutationUiResult {
        val session = expectedSession ?: when (val captured = captureRemoteClient(packageInfo)) {
            is RemoteClientCapture.Failure -> return RemoteMutationUiResult.Failure(captured.safeCode)
            is RemoteClientCapture.Ready -> captured.session
        }
        if (session.packageInfo.packageSha256 != packageInfo.packageSha256 || session.sourceId != summary.identity.sourceId) {
            return RemoteMutationUiResult.Failure("source-changed")
        }
        val active = session.active
        val client = session.client
        val lease = session.lease
        if (!remoteClientLeaseStillValid(session)) return RemoteMutationUiResult.Failure("source-changed")
        val reconciliationId = library.beginRemoteMutation(
            RemoteMutationRequest(
                book = summary.toLibraryBook(importedAt),
                operation = "remove",
                packageDigest = packageInfo.packageSha256,
                packageVersion = packageInfo.manifest.version.original,
                capabilitySetFingerprint = policy.capabilitySetFingerprint,
                registryGeneration = availability.generation,
                startedAt = importedAt,
            ),
            retryingUnresolvedId,
        )
        if (selectedIdentity == summary.identity) {
            selectedBookReconciliation = RemoteReconciliationState.PENDING_USER_ACTION
            selectedBookReconciliationOperation = "remove"
        }
        val token = sessionOwner.directActionTokens.mint(
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
            if (!leaseStillValid(summary.identity.sourceId, lease) ||
                library.sourceRemotePolicy(summary.identity.sourceId)?.removeWritebackEnabled != true ||
                !remoteAddCredentialReady(packageInfo, removePolicy.origin)
            ) return@mint false
            library.transitionRemoteMutation(
                reconciliationId,
                RemoteReconciliationState.PENDING_USER_ACTION,
                RemoteReconciliationState.IN_FLIGHT,
                Instant.now(),
            )
        }
        return try {
            if (sessionOwner.clientFor(active) !== client || !leaseStillValid(summary.identity.sourceId, lease)) {
                return settleFailedRemoteMutation(
                    token,
                    reconciliationId,
                    summary.identity,
                    null,
                    retryingUnresolvedId != null,
                )
            }
            val result = client.removeRemoteLibrary(summary.identity.remoteBookId, token)
            check(result.outcome == RemoteLibraryRemoveOutcome.APPLIED || result.outcome == RemoteLibraryRemoveOutcome.ALREADY_ABSENT)
            if (!leaseStillValid(summary.identity.sourceId, lease) ||
                !library.confirmRemoteMutation(
                    reconciliationId,
                    summary.identity,
                    "remove",
                    retryingUnresolvedId != null,
                    Instant.now(),
                )
            ) {
                library.transitionRemoteMutation(
                    reconciliationId,
                    RemoteReconciliationState.IN_FLIGHT,
                    RemoteReconciliationState.UNRESOLVED,
                    Instant.now(),
                )
                updateReconciliation(summary.identity, RemoteReconciliationState.UNRESOLVED)
                RemoteMutationUiResult.Unresolved
            } else {
                updateReconciliation(summary.identity, RemoteReconciliationState.CONFIRMED)
                RemoteMutationUiResult.Confirmed
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                settleFailedRemoteMutation(
                    token,
                    reconciliationId,
                    summary.identity,
                    null,
                    retryingUnresolvedId != null,
                )
            }
            throw cancelled
        } catch (error: Throwable) {
            settleFailedRemoteMutation(
                token,
                reconciliationId,
                summary.identity,
                (error as? SourceException)?.diagnostic?.correlationId,
                retryingUnresolvedId != null,
            )
        }
    }

    private suspend fun executeRemoteMove(
        summary: SourceBookSummary,
        targetId: String,
        targetName: String,
        packageInfo: VerifiedHxpPackage,
        policy: SourceRemotePolicy,
        availability: SourceAvailability,
        movePolicy: HxpRemoteOperationPolicy,
        importedAt: Instant,
        retryingUnresolvedId: String? = null,
        expectedSession: CapturedRemoteClient? = null,
    ): RemoteMutationUiResult {
        val session = expectedSession ?: when (val captured = captureRemoteClient(packageInfo)) {
            is RemoteClientCapture.Failure -> return RemoteMutationUiResult.Failure(captured.safeCode)
            is RemoteClientCapture.Ready -> captured.session
        }
        if (session.packageInfo.packageSha256 != packageInfo.packageSha256 || session.sourceId != summary.identity.sourceId) {
            return RemoteMutationUiResult.Failure("source-changed")
        }
        val active = session.active
        val client = session.client
        val lease = session.lease
        if (!remoteClientLeaseStillValid(session)) return RemoteMutationUiResult.Failure("source-changed")
        val reconciliationId = library.beginRemoteMutation(
            RemoteMutationRequest(
                book = summary.toLibraryBook(importedAt),
                operation = "move",
                targetId = targetId,
                targetName = targetName,
                packageDigest = packageInfo.packageSha256,
                packageVersion = packageInfo.manifest.version.original,
                capabilitySetFingerprint = policy.capabilitySetFingerprint,
                registryGeneration = availability.generation,
                startedAt = importedAt,
            ),
            retryingUnresolvedId,
        )
        if (selectedIdentity == summary.identity) {
            selectedBookReconciliation = RemoteReconciliationState.PENDING_USER_ACTION
            selectedBookReconciliationOperation = "move"
        }
        val token = sessionOwner.directActionTokens.mint(
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
            if (!leaseStillValid(summary.identity.sourceId, lease) ||
                library.sourceRemotePolicy(summary.identity.sourceId)?.moveWritebackEnabled != true ||
                !remoteAddCredentialReady(packageInfo, movePolicy.origin)
            ) return@mint false
            library.transitionRemoteMutation(
                reconciliationId,
                RemoteReconciliationState.PENDING_USER_ACTION,
                RemoteReconciliationState.IN_FLIGHT,
                Instant.now(),
            )
        }
        return try {
            if (sessionOwner.clientFor(active) !== client || !leaseStillValid(summary.identity.sourceId, lease)) {
                return settleFailedRemoteMutation(
                    token,
                    reconciliationId,
                    summary.identity,
                    null,
                    retryingUnresolvedId != null,
                )
            }
            val result = client.moveRemoteLibrary(summary.identity.remoteBookId, targetId, token)
            check(result.outcome == RemoteLibraryMoveOutcome.APPLIED || result.outcome == RemoteLibraryMoveOutcome.ALREADY_AT_TARGET)
            if (!leaseStillValid(summary.identity.sourceId, lease) ||
                !library.confirmRemoteMutation(
                    reconciliationId,
                    summary.identity,
                    "move",
                    retryingUnresolvedId != null,
                    Instant.now(),
                )
            ) {
                library.transitionRemoteMutation(
                    reconciliationId,
                    RemoteReconciliationState.IN_FLIGHT,
                    RemoteReconciliationState.UNRESOLVED,
                    Instant.now(),
                )
                updateReconciliation(summary.identity, RemoteReconciliationState.UNRESOLVED)
                RemoteMutationUiResult.Unresolved
            } else {
                updateReconciliation(summary.identity, RemoteReconciliationState.CONFIRMED)
                RemoteMutationUiResult.Confirmed
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                settleFailedRemoteMutation(
                    token,
                    reconciliationId,
                    summary.identity,
                    null,
                    retryingUnresolvedId != null,
                )
            }
            throw cancelled
        } catch (error: Throwable) {
            settleFailedRemoteMutation(
                token,
                reconciliationId,
                summary.identity,
                (error as? SourceException)?.diagnostic?.correlationId,
                retryingUnresolvedId != null,
            )
        }
    }

    private suspend fun settleFailedRemoteMutation(
        token: String,
        reconciliationId: String,
        identity: BookIdentity.Remote,
        diagnosticId: String?,
        preservesPriorUnresolved: Boolean,
    ): RemoteMutationUiResult = withContext(NonCancellable) {
        sessionOwner.directActionTokens.revoke(token)
        val cancelled = library.transitionRemoteMutation(
            reconciliationId,
            RemoteReconciliationState.PENDING_USER_ACTION,
            RemoteReconciliationState.CANCELLED,
            Instant.now(),
        )
        if (cancelled) {
            if (preservesPriorUnresolved) {
                updateReconciliation(identity, RemoteReconciliationState.UNRESOLVED)
                RemoteMutationUiResult.Unresolved
            } else {
                updateReconciliation(identity, RemoteReconciliationState.CANCELLED)
                RemoteMutationUiResult.Cancelled
            }
        } else {
            library.transitionRemoteMutation(
                reconciliationId,
                RemoteReconciliationState.IN_FLIGHT,
                RemoteReconciliationState.UNRESOLVED,
                Instant.now(),
                diagnosticId,
            )
            updateReconciliation(identity, RemoteReconciliationState.UNRESOLVED)
            RemoteMutationUiResult.Unresolved
        }
    }

    private suspend fun retryRemoteAddLocked(
        summary: SourceBookSummary,
        existing: LibraryBook,
        importedAt: Instant,
        expectedSession: CapturedRemoteClient? = null,
    ): RemoteAddUiResult {
        val session = expectedSession ?: when (val captured = captureRemoteClient(null)) {
            is RemoteClientCapture.Failure -> return RemoteAddUiResult.Failure(captured.safeCode)
            is RemoteClientCapture.Ready -> captured.session
        }
        val reconciliation = library.bookReconciliation(summary.identity.sourceId, summary.identity.remoteBookId)
        if (reconciliation?.operation?.uppercase() != "ADD" ||
            reconciliation.state !in RETRYABLE_RECONCILIATION_STATES
        ) {
            return RemoteAddUiResult.Failure("remote-add-not-retryable")
        }
        val packageInfo = session.packageInfo
        if (session.sourceId != summary.identity.sourceId || !remoteClientLeaseStillValid(session)) {
            return RemoteAddUiResult.Failure("source-changed")
        }
        val policy = library.sourceRemotePolicy(summary.identity.sourceId)
        val availability = library.sourceAvailability(summary.identity.sourceId)
        val addPolicy = packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.ADD]
        val credentialReady = addPolicy != null && remoteAddCredentialReady(packageInfo, addPolicy.origin)
        if (policy?.addWritebackEnabled == true && !credentialReady) {
            library.setAddWritebackEnabled(summary.identity.sourceId, policy.capabilitySetFingerprint, false)
        }
        if (policy?.addWritebackEnabled != true || availability?.available != true || addPolicy == null ||
            policy.capabilitySetFingerprint.isBlank() || !credentialReady
        ) {
            if (selectedIdentity == summary.identity) selectedBookAddWritesRemote = false
            return RemoteAddUiResult.Failure("remote-add-not-authorized")
        }
        return executeRemoteAdd(
            summary,
            existing,
            packageInfo,
            policy,
            availability,
            addPolicy,
            importedAt,
            retryingUnresolvedAddId = reconciliation.id.takeIf {
                reconciliation.state == RemoteReconciliationState.UNRESOLVED
            },
            continuationTargetId = reconciliation.targetId,
            continuationTargetName = reconciliation.targetName,
            expectedSession = session,
        )
    }

    private suspend fun addBookToWebsiteLocked(
        summary: SourceBookSummary,
        importedAt: Instant,
        continuationTargetId: String? = null,
        continuationTargetName: String? = null,
        expectedSession: CapturedRemoteClient? = null,
    ): RemoteAddUiResult {
        val session = expectedSession ?: when (val captured = captureRemoteClient(null)) {
            is RemoteClientCapture.Failure -> return RemoteAddUiResult.Failure(captured.safeCode)
            is RemoteClientCapture.Ready -> captured.session
        }
        val reconciliation = library.bookReconciliation(summary.identity.sourceId, summary.identity.remoteBookId)
        if (reconciliation?.operation?.uppercase() == "ADD") {
            when (reconciliation.state) {
                RemoteReconciliationState.CONFIRMED -> return RemoteAddUiResult.Failure("book-already-added")
                RemoteReconciliationState.CANCELLED -> Unit
                else -> return RemoteAddUiResult.Failure("remote-add-not-retryable")
            }
        }
        if (reconciliation?.state in BLOCKING_RECONCILIATION_STATES) {
            return RemoteAddUiResult.Failure("remote-mutation-blocked-unresolved")
        }
        val packageInfo = session.packageInfo
        if (session.sourceId != summary.identity.sourceId || !remoteClientLeaseStillValid(session)) {
            return RemoteAddUiResult.Failure("source-changed")
        }
        val book = summary.toLibraryBook(importedAt)
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
            if (selectedIdentity == summary.identity) selectedBookAddWritesRemote = false
            return RemoteAddUiResult.Failure("remote-add-not-authorized")
        }
        return executeRemoteAdd(
            summary,
            book,
            packageInfo,
            policy,
            availability,
            addPolicy,
            importedAt,
            continuationTargetId = continuationTargetId,
            continuationTargetName = continuationTargetName,
            expectedSession = session,
        )
    }

    private suspend fun executeRemoteAdd(
        summary: SourceBookSummary,
        book: LibraryBook,
        packageInfo: VerifiedHxpPackage,
        policy: SourceRemotePolicy,
        availability: SourceAvailability,
        addPolicy: HxpRemoteOperationPolicy,
        importedAt: Instant,
        retryingUnresolvedAddId: String? = null,
        continuationTargetId: String? = null,
        continuationTargetName: String? = null,
        expectedSession: CapturedRemoteClient? = null,
    ): RemoteAddUiResult {
        val session = expectedSession ?: when (val captured = captureRemoteClient(packageInfo)) {
            is RemoteClientCapture.Failure -> return RemoteAddUiResult.Failure(captured.safeCode)
            is RemoteClientCapture.Ready -> captured.session
        }
        if (session.packageInfo.packageSha256 != packageInfo.packageSha256 || session.sourceId != summary.identity.sourceId) {
            return RemoteAddUiResult.Failure("source-changed")
        }
        val active = session.active
        val client = session.client
        val lease = session.lease
        if (!remoteClientLeaseStillValid(session)) return RemoteAddUiResult.Failure("source-changed")
        val reconciliationId = library.beginRemoteAdd(
            RemoteAddRequest(
                book = book,
                targetId = continuationTargetId,
                targetName = continuationTargetName,
                packageDigest = packageInfo.packageSha256,
                packageVersion = packageInfo.manifest.version.original,
                capabilitySetFingerprint = policy.capabilitySetFingerprint,
                registryGeneration = availability.generation,
                startedAt = importedAt,
            ),
            retryingUnresolvedAddId,
        )
        val token = sessionOwner.directActionTokens.mint(
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
            if (!leaseStillValid(summary.identity.sourceId, lease) ||
                library.sourceRemotePolicy(summary.identity.sourceId)?.addWritebackEnabled != true ||
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
            if (selectedIdentity == summary.identity) {
                selectedLibraryEntry = library.libraryEntry(summary.identity)
                selectedBookInLibrary = true
                selectedBookReconciliation = RemoteReconciliationState.PENDING_USER_ACTION
                selectedBookReconciliationOperation = "add"
            }
            if (sessionOwner.clientFor(active) !== client || !leaseStillValid(summary.identity.sourceId, lease)) {
                return settleFailedRemoteAdd(
                    token,
                    reconciliationId,
                    summary.identity,
                    null,
                    retryingUnresolvedAddId != null,
                )
            }
            val result = client.addRemoteLibrary(summary.identity.remoteBookId, token)
            check(result.outcome == RemoteLibraryAddOutcome.APPLIED || result.outcome == RemoteLibraryAddOutcome.ALREADY_PRESENT)
            if (!leaseStillValid(summary.identity.sourceId, lease) ||
                !library.confirmRemoteAdd(
                    reconciliationId,
                    summary.identity,
                    retryingUnresolvedAddId != null,
                    Instant.now(),
                )
            ) {
                library.transitionRemoteAdd(
                    reconciliationId,
                    RemoteReconciliationState.IN_FLIGHT,
                    RemoteReconciliationState.UNRESOLVED,
                    Instant.now(),
                )
                updateReconciliation(summary.identity, RemoteReconciliationState.UNRESOLVED)
                RemoteAddUiResult.Unresolved
            } else {
                updateReconciliation(summary.identity, RemoteReconciliationState.CONFIRMED)
                RemoteAddUiResult.Confirmed
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                settleFailedRemoteAdd(
                    token,
                    reconciliationId,
                    summary.identity,
                    null,
                    retryingUnresolvedAddId != null,
                )
            }
            throw cancelled
        } catch (error: Throwable) {
            settleFailedRemoteAdd(
                token,
                reconciliationId,
                summary.identity,
                (error as? SourceException)?.diagnostic?.correlationId,
                retryingUnresolvedAddId != null,
            )
        }
    }

    private suspend fun settleFailedRemoteAdd(
        token: String,
        reconciliationId: String,
        identity: BookIdentity.Remote,
        diagnosticId: String?,
        preservesPriorUnresolved: Boolean,
    ): RemoteAddUiResult = withContext(NonCancellable) {
        sessionOwner.directActionTokens.revoke(token)
        val cancelled = library.transitionRemoteAdd(
            reconciliationId,
            RemoteReconciliationState.PENDING_USER_ACTION,
            RemoteReconciliationState.CANCELLED,
            Instant.now(),
        )
        if (cancelled) {
            if (preservesPriorUnresolved) {
                updateReconciliation(identity, RemoteReconciliationState.UNRESOLVED)
                RemoteAddUiResult.Unresolved
            } else {
                updateReconciliation(identity, RemoteReconciliationState.CANCELLED)
                RemoteAddUiResult.Cancelled
            }
        } else {
            library.transitionRemoteAdd(
                reconciliationId,
                RemoteReconciliationState.IN_FLIGHT,
                RemoteReconciliationState.UNRESOLVED,
                Instant.now(),
                diagnosticId,
            )
            updateReconciliation(identity, RemoteReconciliationState.UNRESOLVED)
            RemoteAddUiResult.Unresolved
        }
    }

    private suspend fun remoteMutationBlocked(identity: BookIdentity.Remote): Boolean =
        library.bookReconciliation(identity.sourceId, identity.remoteBookId)?.state in BLOCKING_RECONCILIATION_STATES

    private suspend fun remoteClientLeaseStillValid(session: CapturedRemoteClient): Boolean =
        sessionOwner.clientFor(session.active) === session.client &&
            leaseStillValid(session.sourceId, session.lease)

    private suspend fun leaseStillValid(sourceId: String, lease: RemoteExecutionLease): Boolean {
        val availability = library.sourceAvailability(sourceId)
        val policy = library.sourceRemotePolicy(sourceId)
        val active = sessionOwner.active()
        return active != null && active.packageInfo.manifest.sourceId.value == sourceId && lease.matches(
            active.packageInfo.packageSha256,
            active.packageInfo.manifest.version.original,
            availability?.verifiedVersion,
            policy?.capabilitySetFingerprint,
            availability?.generation,
            active.ownerGeneration,
        ) && availability?.available == true
    }
    private fun SourceException.toPullResult(): RemoteLibraryPullResult = when (code) {
        SourceErrorCode.SESSION_REQUIRED -> RemoteLibraryPullResult.LoginRequired
        SourceErrorCode.VERIFICATION_REQUIRED -> RemoteLibraryPullResult.VerificationRequired
        SourceErrorCode.EXTENSION_CANCELLED -> RemoteLibraryPullResult.Cancelled
        else -> RemoteLibraryPullResult.Failure(diagnostic.safeCode)
    }

    private fun SourceException.toTargetReadResult(): RemoteTargetReadResult = when (code) {
        SourceErrorCode.SESSION_REQUIRED -> RemoteTargetReadResult.LoginRequired
        SourceErrorCode.VERIFICATION_REQUIRED -> RemoteTargetReadResult.VerificationRequired
        SourceErrorCode.EXTENSION_CANCELLED -> RemoteTargetReadResult.Cancelled
        else -> RemoteTargetReadResult.Failure(diagnostic.safeCode)
    }

    private fun RemoteLibraryPullResult.toSnapshotRefreshResult(): RemoteLibrarySnapshotRefreshResult = when (this) {
        is RemoteLibraryPullResult.Failure -> RemoteLibrarySnapshotRefreshResult.Failure(safeCode)
        RemoteLibraryPullResult.LoginRequired -> RemoteLibrarySnapshotRefreshResult.LoginRequired
        RemoteLibraryPullResult.VerificationRequired -> RemoteLibrarySnapshotRefreshResult.VerificationRequired
        RemoteLibraryPullResult.Cancelled -> RemoteLibrarySnapshotRefreshResult.Cancelled
        is RemoteLibraryPullResult.Success -> RemoteLibrarySnapshotRefreshResult.Failure("invalid-refresh-state")
    }

    private fun updateReconciliation(identity: BookIdentity.Remote, state: RemoteReconciliationState) {
        if (selectedIdentity == identity) selectedBookReconciliation = state
    }

    private fun remoteAddCredentialReady(packageInfo: VerifiedHxpPackage, origin: HttpsOrigin): Boolean = runCatching {
        credentialStore.getSnapshot(SourceCredentialPartition(packageInfo.manifest.sourceId.value, origin)) != null
    }.getOrDefault(false)

    private fun normalizedSize(item: SourceBookSummary): Int =
        item.title.encodeToByteArray().size + item.identity.remoteBookId.encodeToByteArray().size +
            item.canonicalUrl.encodeToByteArray().size + (item.author?.encodeToByteArray()?.size ?: 0) +
            (item.coverUrl?.encodeToByteArray()?.size ?: 0) +
            (item.remoteTargetId?.encodeToByteArray()?.size ?: 0)

    private fun SourceBookSummary.toLibraryBook(importedAt: Instant) = LibraryBook(
        identity = identity,
        title = title,
        addedAt = importedAt,
        metadataUpdatedAt = importedAt,
        author = author,
        coverUrl = coverUrl,
        canonicalUrl = canonicalUrl,
    )

    private companion object {
        const val MAX_REMOTE_LIBRARY_AGGREGATE_BYTES = 8L * 1024 * 1024
        const val MAX_REMOTE_LIBRARY_RECORDS = 5_000
        val BLOCKING_RECONCILIATION_STATES = setOf(
            RemoteReconciliationState.PENDING_USER_ACTION,
            RemoteReconciliationState.IN_FLIGHT,
            RemoteReconciliationState.UNRESOLVED,
        )
        val RETRYABLE_RECONCILIATION_STATES = setOf(
            RemoteReconciliationState.UNRESOLVED,
            RemoteReconciliationState.CANCELLED,
        )
    }
}

internal fun defaultRemoteTarget(targets: List<RemoteTarget>): RemoteTarget? =
    targets.firstOrNull { it.kind == "default" || it.displayName.contains("默认") }
        ?: targets.firstOrNull()

internal fun normalizeRemoteTargetMembership(
    books: List<SourceBookSummary>,
    targets: List<RemoteTarget>,
): List<SourceBookSummary> {
    val defaultTargetId = defaultRemoteTarget(targets)?.targetId ?: return books
    return books.map { book ->
        if (book.remoteTargetId == null) book.copy(remoteTargetId = defaultTargetId) else book
    }
}

sealed interface RemoteLibraryPullResult {
    data class Success(val books: List<SourceBookSummary>) : RemoteLibraryPullResult
    data class Failure(val safeCode: String) : RemoteLibraryPullResult
    data object LoginRequired : RemoteLibraryPullResult
    data object VerificationRequired : RemoteLibraryPullResult
    data object Cancelled : RemoteLibraryPullResult
}

internal sealed interface RemoteTargetReadResult {
    data class Success(val sourceId: String, val targets: List<RemoteTarget>) : RemoteTargetReadResult
    data object Unsupported : RemoteTargetReadResult
    data class Failure(val safeCode: String) : RemoteTargetReadResult
    data object LoginRequired : RemoteTargetReadResult
    data object VerificationRequired : RemoteTargetReadResult
    data object Cancelled : RemoteTargetReadResult
}

internal sealed interface RemoteLibrarySnapshotRefreshResult {
    data class Success(val books: List<SourceBookSummary>, val targets: List<RemoteTarget>) : RemoteLibrarySnapshotRefreshResult
    data class Failure(val safeCode: String) : RemoteLibrarySnapshotRefreshResult
    data object LoginRequired : RemoteLibrarySnapshotRefreshResult
    data object VerificationRequired : RemoteLibrarySnapshotRefreshResult
    data object Cancelled : RemoteLibrarySnapshotRefreshResult
}

data class RemoteLibraryCopyResult(val total: Int, val added: Int)

sealed interface RemoteAddUiResult {
    data object LocalOnly : RemoteAddUiResult
    data object Confirmed : RemoteAddUiResult
    data object Unresolved : RemoteAddUiResult
    data object Cancelled : RemoteAddUiResult
    data class Failure(val safeCode: String) : RemoteAddUiResult
}

sealed interface RemoteTargetedAddResult {
    data object Confirmed : RemoteTargetedAddResult
    data class Partial(
        val defaultTargetId: String,
        val requestedTargetId: String,
        val requestedTargetName: String,
        val safeCode: String? = null,
    ) : RemoteTargetedAddResult
    data object Unresolved : RemoteTargetedAddResult
    data object Cancelled : RemoteTargetedAddResult
    data class Failure(val safeCode: String) : RemoteTargetedAddResult
}

sealed interface RemoteMutationUiResult {
    data object Confirmed : RemoteMutationUiResult
    data object Unresolved : RemoteMutationUiResult
    data object Cancelled : RemoteMutationUiResult
    data class Failure(val safeCode: String) : RemoteMutationUiResult
}
