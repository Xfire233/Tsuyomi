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
import org.tsuyomi.core.database.LibraryBook
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.RemoteAddRequest
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.database.SourceAvailability
import org.tsuyomi.core.database.SourceRemotePolicy
import org.tsuyomi.core.network.DirectActionBinding
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddOutcome
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
    private var selectedIdentity: BookIdentity? = null

    var selectedLibraryEntry: LibraryEntry? by mutableStateOf(null)
        private set
    var selectedBookInLibrary: Boolean by mutableStateOf(false)
        private set
    var selectedBookReconciliation: RemoteReconciliationState? by mutableStateOf(null)
        private set
    var selectedBookAddWritesRemote: Boolean by mutableStateOf(false)
        private set

    fun reset() {
        selectedIdentity = null
        selectedLibraryEntry = null
        selectedBookInLibrary = false
        selectedBookReconciliation = null
        selectedBookAddWritesRemote = false
    }

    fun beginSelection(identity: BookIdentity) {
        selectedIdentity = identity
        selectedLibraryEntry = null
        selectedBookInLibrary = false
        selectedBookReconciliation = null
        selectedBookAddWritesRemote = false
    }

    suspend fun refreshSelection(summary: SourceBookSummary) {
        if (selectedIdentity != summary.identity) return
        val activePackage = sessionOwner.active()?.packageInfo
        val addPolicy = activePackage?.manifest?.capabilities?.remoteLibrary?.policies?.get(RemoteOperation.ADD)
        val availability = library.sourceAvailability(summary.identity.sourceId)
        val addWritesRemote = library.sourceRemotePolicy(summary.identity.sourceId)?.addWritebackEnabled == true &&
            availability?.available == true && activePackage != null && addPolicy != null &&
            remoteAddCredentialReady(activePackage, addPolicy.origin)
        val entry = library.libraryEntry(summary.identity)
        if (selectedIdentity == summary.identity) {
            selectedLibraryEntry = entry
            selectedBookAddWritesRemote = addWritesRemote
            selectedBookInLibrary = entry != null
            selectedBookReconciliation = entry?.reconciliation
        }
    }

    suspend fun pull(packageInfo: VerifiedHxpPackage): RemoteLibraryPullResult {
        val active = sessionOwner.active()
            ?: return RemoteLibraryPullResult.Failure("source-not-open")
        if (active.packageInfo.packageSha256 != packageInfo.packageSha256) {
            return RemoteLibraryPullResult.Failure("source-changed")
        }
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
            active.ownerGeneration,
        )
        val seenCursors = hashSetOf<String>()
        val summaries = linkedMapOf<BookIdentity, SourceBookSummary>()
        var cursor: String? = null
        var aggregateBytes = 0L
        repeat(MAX_REMOTE_LIBRARY_PAGES) { pageIndex ->
            val page = try {
                sessionOwner.requireClient().listRemoteLibrary(cursor)
            } catch (error: SourceException) {
                return when (error.code) {
                    SourceErrorCode.SESSION_REQUIRED -> RemoteLibraryPullResult.LoginRequired
                    SourceErrorCode.VERIFICATION_REQUIRED -> RemoteLibraryPullResult.VerificationRequired
                    SourceErrorCode.EXTENSION_CANCELLED -> RemoteLibraryPullResult.Cancelled
                    else -> RemoteLibraryPullResult.Failure(error.diagnostic.safeCode)
                }
            }
            page.items.forEach { item ->
                if (item.identity.sourceId != sourceId) return RemoteLibraryPullResult.Failure("source-identity-mismatch")
                aggregateBytes += normalizedSize(item)
                if (aggregateBytes > MAX_REMOTE_LIBRARY_AGGREGATE_BYTES) {
                    return RemoteLibraryPullResult.Failure("aggregate-limit")
                }
                summaries.putIfAbsent(item.identity, item)
                if (summaries.size > MAX_REMOTE_LIBRARY_RECORDS) {
                    return RemoteLibraryPullResult.Failure("record-limit")
                }
            }
            if (page.complete) {
                if (page.nextCursor != null) return RemoteLibraryPullResult.Failure("complete-with-cursor")
                if (!leaseStillValid(sourceId, lease)) return RemoteLibraryPullResult.Failure("source-changed")
                return RemoteLibraryPullResult.Success(summaries.values.toList())
            }
            val next = page.nextCursor ?: return RemoteLibraryPullResult.Failure("incomplete-page")
            if (!seenCursors.add(next)) return RemoteLibraryPullResult.Failure("duplicate-cursor")
            cursor = next
            if (pageIndex == MAX_REMOTE_LIBRARY_PAGES - 1) return RemoteLibraryPullResult.Failure("page-limit")
        }
        return RemoteLibraryPullResult.Failure("page-limit")
    }

    suspend fun copyToLocal(
        summaries: Collection<SourceBookSummary>,
        importedAt: Instant = Instant.now(),
    ): RemoteLibraryCopyResult {
        var added = 0
        summaries.distinctBy(SourceBookSummary::identity).forEach { summary ->
            if (library.addToLibrary(summary.toLibraryBook(importedAt))) added++
        }
        return RemoteLibraryCopyResult(total = summaries.distinctBy(SourceBookSummary::identity).size, added = added)
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
            }
            RemoteAddUiResult.LocalOnly
        }

    suspend fun toggleReadLater(summary: SourceBookSummary?, importedAt: Instant = Instant.now()): Boolean =
        remoteAddMutex.withLock {
            val selected = summary?.takeIf { selectedIdentity == it.identity }
                ?: error("Book is not selected")
            val current = library.libraryEntry(selected.identity)
            val next = !(current?.readLater ?: false)
            if (current == null) {
                library.addToLibrary(selected.toLibraryBook(importedAt))
            }
            library.setReadLater(selected.identity, next)
            val updated = requireNotNull(library.libraryEntry(selected.identity)) { "Book is not in library" }
            if (selectedIdentity == selected.identity) {
                selectedLibraryEntry = updated
                selectedBookInLibrary = true
                selectedBookReconciliation = updated.reconciliation
            }
            next
        }

    suspend fun addBookToWebsite(summary: SourceBookSummary?, importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteAddMutex.withLock {
            val selected = summary?.takeIf { selectedIdentity == it.identity }
                ?: return@withLock RemoteAddUiResult.Failure("book-not-selected")
            if (selectedBookInLibrary) return@withLock RemoteAddUiResult.Failure("book-already-added")
            addBookToWebsiteLocked(selected, importedAt)
        }

    suspend fun retryBook(summary: SourceBookSummary?, importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteAddMutex.withLock {
            val selected = summary?.takeIf { selectedIdentity == it.identity }
                ?: return@withLock RemoteAddUiResult.Failure("book-not-selected")
            val existing = library.book(selected.identity)
                ?: return@withLock RemoteAddUiResult.Failure("book-not-local")
            retryRemoteAddLocked(selected, existing, selectedBookReconciliation, importedAt)
        }

    suspend fun retryLocalBook(book: LibraryBook, importedAt: Instant = Instant.now()): RemoteAddUiResult =
        remoteAddMutex.withLock {
            val activePackage = sessionOwner.active()?.packageInfo
            if (activePackage?.manifest?.sourceId?.value != book.identity.sourceId) {
                return@withLock RemoteAddUiResult.Failure("remote-add-source-not-open")
            }
            val currentEntry = library.libraryEntries().firstOrNull { it.book.identity == book.identity }
                ?: return@withLock RemoteAddUiResult.Failure("book-not-local")
            val currentBook = currentEntry.book
            val summary = SourceBookSummary(
                identity = currentBook.identity,
                title = currentBook.title,
                author = currentBook.author,
                coverUrl = currentBook.coverUrl,
                canonicalUrl = currentBook.canonicalUrl.orEmpty(),
            )
            retryRemoteAddLocked(summary, currentBook, currentEntry.reconciliation, importedAt)
        }

    suspend fun removeBook(summary: SourceBookSummary?): Boolean {
        val identity = summary?.identity ?: return false
        val removed = library.removeFromLibrary(identity)
        if (removed && selectedIdentity == identity) {
            selectedLibraryEntry = null
            selectedBookInLibrary = false
            selectedBookReconciliation = null
            selectedBookAddWritesRemote = false
        }
        return removed
    }

    private suspend fun retryRemoteAddLocked(
        summary: SourceBookSummary,
        existing: LibraryBook,
        reconciliation: RemoteReconciliationState?,
        importedAt: Instant,
    ): RemoteAddUiResult {
        if (reconciliation !in RETRYABLE_RECONCILIATION_STATES) {
            return RemoteAddUiResult.Failure("remote-add-not-retryable")
        }
        val packageInfo = sessionOwner.active()?.packageInfo
            ?: return RemoteAddUiResult.Failure("source-not-open")
        if (packageInfo.manifest.sourceId.value != summary.identity.sourceId) {
            return RemoteAddUiResult.Failure("remote-add-source-not-open")
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
        return executeRemoteAdd(summary, existing, packageInfo, policy, availability, addPolicy, importedAt)
    }

    private suspend fun addBookToWebsiteLocked(summary: SourceBookSummary, importedAt: Instant): RemoteAddUiResult {
        val packageInfo = sessionOwner.active()?.packageInfo
            ?: return RemoteAddUiResult.Failure("source-not-open")
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
        return executeRemoteAdd(summary, book, packageInfo, policy, availability, addPolicy, importedAt)
    }

    private suspend fun executeRemoteAdd(
        summary: SourceBookSummary,
        book: LibraryBook,
        packageInfo: VerifiedHxpPackage,
        policy: SourceRemotePolicy,
        availability: SourceAvailability,
        addPolicy: HxpRemoteOperationPolicy,
        importedAt: Instant,
    ): RemoteAddUiResult {
        val active = sessionOwner.active() ?: return RemoteAddUiResult.Failure("source-not-open")
        val reconciliationId = library.beginRemoteAdd(
            RemoteAddRequest(
                book = book,
                packageDigest = packageInfo.packageSha256,
                packageVersion = packageInfo.manifest.version.original,
                capabilitySetFingerprint = policy.capabilitySetFingerprint,
                registryGeneration = availability.generation,
                startedAt = importedAt,
            ),
        )
        if (selectedIdentity == summary.identity) {
            selectedLibraryEntry = library.libraryEntry(summary.identity)
            selectedBookInLibrary = true
            selectedBookReconciliation = RemoteReconciliationState.PENDING_USER_ACTION
        }
        val lease = RemoteExecutionLease(
            packageInfo.packageSha256,
            packageInfo.manifest.version.original,
            policy.capabilitySetFingerprint,
            availability.generation,
            active.ownerGeneration,
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
            val result = sessionOwner.requireClient().addRemoteLibrary(summary.identity.remoteBookId, token)
            check(result.outcome == RemoteLibraryAddOutcome.APPLIED || result.outcome == RemoteLibraryAddOutcome.ALREADY_PRESENT)
            if (!leaseStillValid(summary.identity.sourceId, lease) ||
                !library.transitionRemoteAdd(
                    reconciliationId,
                    RemoteReconciliationState.IN_FLIGHT,
                    RemoteReconciliationState.CONFIRMED,
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
                settleFailedRemoteAdd(token, reconciliationId, summary.identity, null)
            }
            throw cancelled
        } catch (error: Throwable) {
            settleFailedRemoteAdd(
                token,
                reconciliationId,
                summary.identity,
                (error as? SourceException)?.diagnostic?.correlationId,
            )
        }
    }

    private suspend fun settleFailedRemoteAdd(
        token: String,
        reconciliationId: String,
        identity: BookIdentity,
        diagnosticId: String?,
    ): RemoteAddUiResult = withContext(NonCancellable) {
        sessionOwner.directActionTokens.revoke(token)
        val cancelled = library.transitionRemoteAdd(
            reconciliationId,
            RemoteReconciliationState.PENDING_USER_ACTION,
            RemoteReconciliationState.CANCELLED,
            Instant.now(),
        )
        if (cancelled) {
            updateReconciliation(identity, RemoteReconciliationState.CANCELLED)
            RemoteAddUiResult.Cancelled
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

    private suspend fun leaseStillValid(sourceId: String, lease: RemoteExecutionLease): Boolean {
        val active = sessionOwner.active()
        val availability = library.sourceAvailability(sourceId)
        val policy = library.sourceRemotePolicy(sourceId)
        return lease.matches(
            active?.packageInfo?.packageSha256,
            active?.packageInfo?.manifest?.version?.original,
            availability?.verifiedVersion,
            policy?.capabilitySetFingerprint,
            availability?.generation,
            active?.ownerGeneration ?: -1L,
        ) && availability?.available == true
    }

    private fun updateReconciliation(identity: BookIdentity, state: RemoteReconciliationState) {
        if (selectedIdentity == identity) selectedBookReconciliation = state
    }

    private fun remoteAddCredentialReady(packageInfo: VerifiedHxpPackage, origin: HttpsOrigin): Boolean = runCatching {
        credentialStore.getSnapshot(SourceCredentialPartition(packageInfo.manifest.sourceId.value, origin)) != null
    }.getOrDefault(false)

    private fun normalizedSize(item: SourceBookSummary): Int =
        item.title.encodeToByteArray().size + item.identity.remoteBookId.encodeToByteArray().size +
            item.canonicalUrl.encodeToByteArray().size + (item.author?.encodeToByteArray()?.size ?: 0) +
            (item.coverUrl?.encodeToByteArray()?.size ?: 0)

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
        const val MAX_REMOTE_LIBRARY_PAGES = 100
        const val MAX_REMOTE_LIBRARY_AGGREGATE_BYTES = 8L * 1024 * 1024
        const val MAX_REMOTE_LIBRARY_RECORDS = 5_000
        val RETRYABLE_RECONCILIATION_STATES = setOf(
            RemoteReconciliationState.UNRESOLVED,
            RemoteReconciliationState.CANCELLED,
        )
    }
}

sealed interface RemoteLibraryPullResult {
    data class Success(val books: List<SourceBookSummary>) : RemoteLibraryPullResult
    data class Failure(val safeCode: String) : RemoteLibraryPullResult
    data object LoginRequired : RemoteLibraryPullResult
    data object VerificationRequired : RemoteLibraryPullResult
    data object Cancelled : RemoteLibraryPullResult
}

data class RemoteLibraryCopyResult(val total: Int, val added: Int)

sealed interface RemoteAddUiResult {
    data object LocalOnly : RemoteAddUiResult
    data object Confirmed : RemoteAddUiResult
    data object Unresolved : RemoteAddUiResult
    data object Cancelled : RemoteAddUiResult
    data class Failure(val safeCode: String) : RemoteAddUiResult
}
