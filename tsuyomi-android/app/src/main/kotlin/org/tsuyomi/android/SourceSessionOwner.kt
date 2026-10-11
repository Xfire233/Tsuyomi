/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryRemoveResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryMoveResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryTargetsResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceHomePage
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.source.extensionmanager.SourceExtensionClient
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

internal interface SourceFlowSession : Closeable {
    suspend fun search(query: String, page: Int = 1, offlineOnly: Boolean = false): List<SourceBookSummary>
    suspend fun searchRequestUrl(query: String, page: Int = 1): String =
        error("Search request inspection is unavailable")
    suspend fun searchVerifiedPage(
        query: String,
        snapshot: CapturedVerifiedPage,
        page: Int = 1,
    ): List<SourceBookSummary> = error("Verified-page search is unavailable")
    suspend fun authorSearch(author: String, page: Int = 1, offlineOnly: Boolean = false): List<SourceBookSummary> =
        authorSearchUnavailable()
    suspend fun authorSearchRequestUrl(author: String, page: Int = 1): String = authorSearchUnavailable()
    suspend fun authorSearchVerifiedPage(
        author: String,
        snapshot: CapturedVerifiedPage,
        page: Int = 1,
    ): List<SourceBookSummary> = authorSearchUnavailable()
    suspend fun home(
        selectedFilters: Map<String, String>,
        cursor: String?,
        offlineOnly: Boolean = false,
    ): SourceHomePage = error("Source Home is unavailable")
    suspend fun homeRequestUrl(
        selectedFilters: Map<String, String>,
        cursor: String?,
    ): String = error("Source Home request inspection is unavailable")
    suspend fun homeVerifiedPage(
        selectedFilters: Map<String, String>,
        cursor: String?,
        snapshot: CapturedVerifiedPage,
    ): SourceHomePage = error("Verified-page Source Home is unavailable")
    suspend fun detail(remoteBookId: String, offlineOnly: Boolean = false): SourceBookDetail
    suspend fun detailRequestUrl(remoteBookId: String): String = error("Detail request inspection is unavailable")
    suspend fun detailVerifiedPage(
        remoteBookId: String,
        snapshot: CapturedVerifiedPage,
    ): SourceBookDetail = error("Verified-page detail is unavailable")
    suspend fun directory(remoteBookId: String, offlineOnly: Boolean = false): SourceDirectory
    suspend fun directoryRequestUrl(remoteBookId: String): String = error("Directory request inspection is unavailable")
    suspend fun directoryVerifiedPage(
        remoteBookId: String,
        snapshot: CapturedVerifiedPage,
    ): SourceDirectory = error("Verified-page directory is unavailable")
    suspend fun chapterRequestUrl(chapter: SourceChapter, remoteBookId: String): String =
        error("Chapter request inspection is unavailable")
    suspend fun chapterVerifiedPage(
        chapter: SourceChapter,
        remoteBookId: String,
        snapshot: CapturedVerifiedPage,
    ): ReaderDocument = error("Verified-page chapter is unavailable")
    suspend fun chapter(chapter: SourceChapter, remoteBookId: String, offlineOnly: Boolean = false): ReaderDocument
    suspend fun listRemoteLibrary(cursor: String?): RemoteLibraryPage
    suspend fun addRemoteLibrary(remoteBookId: String, directActionToken: String): RemoteLibraryAddResult
    suspend fun removeRemoteLibrary(remoteBookId: String, directActionToken: String): RemoteLibraryRemoveResult
    suspend fun moveRemoteLibrary(remoteBookId: String, targetId: String, directActionToken: String): RemoteLibraryMoveResult
    suspend fun listRemoteTargets(): RemoteLibraryTargetsResult
}

private fun authorSearchUnavailable(): Nothing = throw SourceException(
    code = SourceErrorCode.EXTENSION_RUNTIME_FAILURE,
    diagnostic = SourceDiagnostic(
        correlationId = "author-search-unavailable",
        stage = "author-search",
        safeCode = "author-search-unavailable",
    ),
)

private class ExtensionSourceFlowSession(
    private val delegate: SourceExtensionClient,
    private val verifiedPageClient: suspend (CapturedVerifiedPage) -> SourceExtensionClient,
) : SourceFlowSession {
    override suspend fun search(query: String, page: Int, offlineOnly: Boolean) = delegate.search(query, page, offlineOnly)
    override suspend fun searchRequestUrl(query: String, page: Int) = delegate.searchRequestUrl(query, page)
    override suspend fun searchVerifiedPage(
        query: String,
        snapshot: CapturedVerifiedPage,
        page: Int,
    ): List<SourceBookSummary> = verifiedPageClient(snapshot).use { client ->
        client.search(query, page, offlineOnly = false)
    }
    override suspend fun authorSearch(author: String, page: Int, offlineOnly: Boolean) =
        delegate.authorSearch(author, page, offlineOnly)
    override suspend fun authorSearchRequestUrl(author: String, page: Int) = delegate.authorSearchRequestUrl(author, page)
    override suspend fun authorSearchVerifiedPage(
        author: String,
        snapshot: CapturedVerifiedPage,
        page: Int,
    ): List<SourceBookSummary> = verifiedPageClient(snapshot).use { client ->
        client.authorSearch(author, page, offlineOnly = false)
    }
    override suspend fun home(selectedFilters: Map<String, String>, cursor: String?, offlineOnly: Boolean) =
        delegate.home(selectedFilters, cursor, offlineOnly)
    override suspend fun homeRequestUrl(selectedFilters: Map<String, String>, cursor: String?) =
        delegate.homeRequestUrl(selectedFilters, cursor)
    override suspend fun homeVerifiedPage(
        selectedFilters: Map<String, String>,
        cursor: String?,
        snapshot: CapturedVerifiedPage,
    ): SourceHomePage = verifiedPageClient(snapshot).use { client ->
        client.home(selectedFilters, cursor, offlineOnly = false)
    }
    override suspend fun detail(remoteBookId: String, offlineOnly: Boolean) = delegate.detail(remoteBookId, offlineOnly)
    override suspend fun detailRequestUrl(remoteBookId: String) = delegate.detailRequestUrl(remoteBookId)
    override suspend fun detailVerifiedPage(remoteBookId: String, snapshot: CapturedVerifiedPage) =
        verifiedPageClient(snapshot).use { client -> client.detail(remoteBookId, offlineOnly = false) }
    override suspend fun directory(remoteBookId: String, offlineOnly: Boolean) = delegate.directory(remoteBookId, offlineOnly)
    override suspend fun directoryRequestUrl(remoteBookId: String) = delegate.directoryRequestUrl(remoteBookId)
    override suspend fun directoryVerifiedPage(remoteBookId: String, snapshot: CapturedVerifiedPage) =
        verifiedPageClient(snapshot).use { client -> client.directory(remoteBookId, offlineOnly = false) }
    override suspend fun chapterRequestUrl(chapter: SourceChapter, remoteBookId: String) =
        delegate.chapterRequestUrl(chapter, remoteBookId)
    override suspend fun chapterVerifiedPage(
        chapter: SourceChapter,
        remoteBookId: String,
        snapshot: CapturedVerifiedPage,
    ) = verifiedPageClient(snapshot).use { client ->
        client.chapter(chapter, remoteBookId, offlineOnly = false)
    }
    override suspend fun chapter(chapter: SourceChapter, remoteBookId: String, offlineOnly: Boolean) =
        delegate.chapter(chapter, remoteBookId, offlineOnly)
    override suspend fun listRemoteLibrary(cursor: String?) = delegate.listRemoteLibrary(cursor)
    override suspend fun addRemoteLibrary(remoteBookId: String, directActionToken: String) =
        delegate.addRemoteLibrary(remoteBookId, directActionToken)
    override suspend fun removeRemoteLibrary(remoteBookId: String, directActionToken: String) =
        delegate.removeRemoteLibrary(remoteBookId, directActionToken)
    override suspend fun moveRemoteLibrary(remoteBookId: String, targetId: String, directActionToken: String) =
        delegate.moveRemoteLibrary(remoteBookId, targetId, directActionToken)
    override suspend fun listRemoteTargets() = delegate.listRemoteTargets()
    override fun close() = delegate.close()
}

internal data class ActiveSourceSession(
    val packageInfo: VerifiedHxpPackage,
    val ownerGeneration: Long,
)
internal enum class SourceSessionOpenResult {
    ALREADY_OPEN,
    OPENED,
    PACKAGE_CHANGED,
    SUPERSEDED,
    UNAVAILABLE,
}

private enum class PendingOpenOutcome { READY, CANCELLED, SUPERSEDED, UNAVAILABLE }

private data class PendingSourceOpen(
    val packageInfo: VerifiedHxpPackage,
    val generation: Long,
    val completion: CompletableDeferred<PendingOpenOutcome> = CompletableDeferred(),
)

private sealed interface SourceOpenRequest {
    data class Start(
        val pending: PendingSourceOpen,
        val previousClient: SourceFlowSession?,
        val superseded: PendingSourceOpen?,
        val packageChanged: Boolean,
    ) : SourceOpenRequest

    data class Wait(val pending: PendingSourceOpen) : SourceOpenRequest
    data class Unavailable(
        val previousClient: SourceFlowSession?,
        val invalidated: PendingSourceOpen?,
    ) : SourceOpenRequest
}

internal class PreparedSourceSession internal constructor(
    internal val owner: SourceSessionOwner,
    internal val packageInfo: VerifiedHxpPackage,
    internal val expectedPackageSha256: String?,
    internal val expectedGeneration: Long,
    internal val client: SourceFlowSession,
) {
    internal var consumed = false
}


internal class SourceSessionOwner(
    val directActionTokens: DirectActionTokenRegistry,
    private val openSession: suspend (VerifiedHxpPackage) -> SourceFlowSession,
    private val isPackageTrusted: (VerifiedHxpPackage) -> Boolean,
) : Closeable {
    private val lock = Any()
    private var client: SourceFlowSession? = null
    private var activePackage: VerifiedHxpPackage? = null
    private var openGeneration = 0L
    private var statePackageSha256: String? = null
    private var closed = false
    private var opening: PendingSourceOpen? = null
    private var requestedPackageSha256: String? = null

    suspend fun open(
        packageInfo: VerifiedHxpPackage,
        onPackageChanged: () -> Unit = {},
        forceReopen: Boolean = false,
        expectedGeneration: Long? = null,
    ): SourceSessionOpenResult {
        while (true) {
            val request = synchronized(lock) {
                checkOpen()
                if (expectedGeneration != null && openGeneration != expectedGeneration) {
                    return SourceSessionOpenResult.SUPERSEDED
                }
                if (forceReopen && activePackage?.packageSha256 != packageInfo.packageSha256) {
                    return SourceSessionOpenResult.SUPERSEDED
                }
                requestedPackageSha256 = packageInfo.packageSha256
                if (!isPackageTrusted(packageInfo)) {
                    openGeneration += 1
                    val invalidated = opening
                    opening = null
                    val previous = client
                    client = null
                    activePackage = null
                    SourceOpenRequest.Unavailable(previous, invalidated)
                } else if (!forceReopen && readyClientLocked(packageInfo) != null) {
                    return SourceSessionOpenResult.ALREADY_OPEN
                } else {
                    val currentOpening = opening
                    if (currentOpening?.packageInfo?.packageSha256 == packageInfo.packageSha256) {
                        SourceOpenRequest.Wait(currentOpening)
                    } else {
                        openGeneration += 1
                        val pending = PendingSourceOpen(packageInfo, openGeneration)
                        val previous = client
                        val superseded = opening
                        client = null
                        activePackage = null
                        opening = pending
                        SourceOpenRequest.Start(
                            pending = pending,
                            previousClient = previous,
                            superseded = superseded,
                            packageChanged = statePackageSha256 != packageInfo.packageSha256,
                        )
                    }
                }
            }

            when (request) {
                is SourceOpenRequest.Unavailable -> {
                    request.invalidated?.completion?.complete(PendingOpenOutcome.UNAVAILABLE)
                    request.previousClient?.close()
                    return SourceSessionOpenResult.UNAVAILABLE
                }
                is SourceOpenRequest.Wait -> {
                    val result = awaitOpen(packageInfo, request.pending)
                    if (result != null) return result
                }
                is SourceOpenRequest.Start -> {
                    request.superseded?.completion?.complete(PendingOpenOutcome.SUPERSEDED)
                    request.previousClient?.close()
                    if (request.packageChanged) {
                        try {
                            if (!performWhenOpening(request.pending) { onPackageChanged() }) {
                                request.pending.completion.complete(PendingOpenOutcome.SUPERSEDED)
                                return SourceSessionOpenResult.SUPERSEDED
                            }
                        } catch (error: Throwable) {
                            if (failPendingOpen(request.pending, error, cancelled = false)) throw error
                            return SourceSessionOpenResult.SUPERSEDED
                        }
                    }
                    val openedClient = try {
                        openSession(packageInfo)
                    } catch (cancelled: CancellationException) {
                        failPendingOpen(request.pending, cancelled, cancelled = true)
                        throw cancelled
                    } catch (error: Throwable) {
                        if (failPendingOpen(request.pending, error, cancelled = false)) throw error
                        return SourceSessionOpenResult.SUPERSEDED
                    }

                    val outcome = try {
                        synchronized(lock) {
                            when {
                                closed -> PendingOpenOutcome.UNAVAILABLE
                                openGeneration != request.pending.generation || opening !== request.pending ->
                                    PendingOpenOutcome.SUPERSEDED
                                !isPackageTrusted(packageInfo) -> {
                                    opening = null
                                    PendingOpenOutcome.UNAVAILABLE
                                }
                                else -> {
                                    client = openedClient
                                    activePackage = packageInfo
                                    statePackageSha256 = packageInfo.packageSha256
                                    opening = null
                                    request.pending.completion.complete(PendingOpenOutcome.READY)
                                    PendingOpenOutcome.READY
                                }
                            }
                        }
                    } catch (error: Throwable) {
                        openedClient.close()
                        if (failPendingOpen(request.pending, error, cancelled = false)) throw error
                        return SourceSessionOpenResult.SUPERSEDED
                    }

                    if (outcome != PendingOpenOutcome.READY) {
                        openedClient.close()
                        request.pending.completion.complete(outcome)
                        val result = awaitOpen(packageInfo, request.pending)
                        if (result != null) return result
                    }

                    return synchronized(lock) {
                        when {
                            readyClientLocked(packageInfo) == null && closed -> SourceSessionOpenResult.UNAVAILABLE
                            readyClientLocked(packageInfo) == null -> SourceSessionOpenResult.SUPERSEDED
                            request.packageChanged -> SourceSessionOpenResult.PACKAGE_CHANGED
                            else -> SourceSessionOpenResult.OPENED
                        }
                    }
                }
            }
        }
    }

    private suspend fun awaitOpen(
        packageInfo: VerifiedHxpPackage,
        pending: PendingSourceOpen,
    ): SourceSessionOpenResult? {
        val outcome = pending.completion.await()
        return synchronized(lock) {
            if (readyClientLocked(packageInfo) != null) {
                SourceSessionOpenResult.ALREADY_OPEN
            } else when {
                closed || outcome == PendingOpenOutcome.UNAVAILABLE || !isPackageTrusted(packageInfo) ->
                    SourceSessionOpenResult.UNAVAILABLE
                outcome == PendingOpenOutcome.CANCELLED &&
                    (openGeneration == pending.generation || opening?.packageInfo?.packageSha256 == packageInfo.packageSha256) -> null
                outcome == PendingOpenOutcome.SUPERSEDED || openGeneration != pending.generation ->
                    SourceSessionOpenResult.SUPERSEDED
                else -> SourceSessionOpenResult.UNAVAILABLE
            }
        }
    }

    private fun failPendingOpen(
        pending: PendingSourceOpen,
        failure: Throwable,
        cancelled: Boolean,
    ): Boolean {
        val current = synchronized(lock) {
            if (opening === pending) {
                opening = null
                true
            } else false
        }
        if (current) {
            if (cancelled) pending.completion.complete(PendingOpenOutcome.CANCELLED)
            else pending.completion.completeExceptionally(failure)
        } else {
            pending.completion.complete(PendingOpenOutcome.SUPERSEDED)
        }
        return current
    }

    private fun performWhenOpening(pending: PendingSourceOpen, action: () -> Unit): Boolean =
        synchronized(lock) {
            if (closed || openGeneration != pending.generation || opening !== pending) return@synchronized false
            action()
            true
        }

    suspend fun prepare(packageInfo: VerifiedHxpPackage): PreparedSourceSession? {
        if (!isPackageTrusted(packageInfo)) return null
        val (expectedPackageSha256, expectedGeneration) = synchronized(lock) {
            checkOpen()
            activePackage?.packageSha256 to openGeneration
        }
        val preparedClient = openSession(packageInfo)
        val retained = synchronized(lock) { !closed && isPackageTrusted(packageInfo) }
        if (!retained) {
            preparedClient.close()
            synchronized(lock) { checkOpen() }
            return null
        }
        return PreparedSourceSession(
            owner = this,
            packageInfo = packageInfo,
            expectedPackageSha256 = expectedPackageSha256,
            expectedGeneration = expectedGeneration,
            client = preparedClient,
        )
    }

    fun commitPrepared(prepared: PreparedSourceSession): Boolean {
        val committed: Pair<SourceFlowSession?, PendingSourceOpen?>? = synchronized(lock) {
            check(prepared.owner === this) { "Prepared session belongs to another owner" }
            checkOpen()
            if (
                prepared.consumed ||
                !isPackageTrusted(prepared.packageInfo) ||
                activePackage?.packageSha256 != prepared.expectedPackageSha256 ||
                openGeneration != prepared.expectedGeneration
            ) {
                null
            } else {
                prepared.consumed = true
                openGeneration += 1
                val previous = client
                val invalidated = opening
                opening = null
                requestedPackageSha256 = prepared.packageInfo.packageSha256
                client = prepared.client
                activePackage = prepared.packageInfo
                statePackageSha256 = prepared.packageInfo.packageSha256
                previous to invalidated
            }
        }
        val (previousClient, invalidated) = committed ?: return false
        invalidated?.completion?.complete(PendingOpenOutcome.SUPERSEDED)
        previousClient?.close()
        return true
    }

    fun discardPrepared(prepared: PreparedSourceSession) {
        val client = synchronized(lock) {
            check(prepared.owner === this) { "Prepared session belongs to another owner" }
            if (prepared.consumed) null else {
                prepared.consumed = true
                prepared.client
            }
        }
        client?.close()
    }

    fun active(): ActiveSourceSession? = synchronized(lock) {
        checkOpen()
        val installed = activePackage ?: return@synchronized null
        if (client == null || !isPackageTrusted(installed)) return@synchronized null
        ActiveSourceSession(installed, openGeneration)
    }

    fun clientFor(active: ActiveSourceSession): SourceFlowSession? = synchronized(lock) {
        clientForLocked(active)
    }

    fun performWithCurrentClient(
        active: ActiveSourceSession,
        expectedClient: SourceFlowSession,
        action: () -> Unit,
    ): Boolean = synchronized(lock) {
        if (clientForLocked(active) !== expectedClient) return@synchronized false
        action()
        true
    }

    private fun clientForLocked(active: ActiveSourceSession): SourceFlowSession? {
        if (closed || active.ownerGeneration != openGeneration) return null
        val installed = activePackage ?: return null
        if (
            installed.packageSha256 != active.packageInfo.packageSha256 ||
            installed.manifest.sourceId.value != active.packageInfo.manifest.sourceId.value ||
            !isPackageTrusted(installed)
        ) return null
        return client
    }

    fun performWhenReady(packageInfo: VerifiedHxpPackage, action: () -> Unit): Boolean =
        synchronized(lock) {
            if (readyClientLocked(packageInfo) == null) return@synchronized false
            action()
            true
        }

    fun performForLatestRequest(packageInfo: VerifiedHxpPackage, action: () -> Unit): Boolean =
        synchronized(lock) {
            if (closed || requestedPackageSha256 != packageInfo.packageSha256) return@synchronized false
            action()
            true
        }

    private fun readyClientLocked(packageInfo: VerifiedHxpPackage): SourceFlowSession? {
        if (closed) return null
        val installed = activePackage ?: return null
        if (
            installed.packageSha256 != packageInfo.packageSha256 ||
            installed.manifest.sourceId.value != packageInfo.manifest.sourceId.value ||
            !isPackageTrusted(installed)
        ) return null
        return client
    }

    fun requireClient(): SourceFlowSession = requireClientOrNull() ?: throw SourceException(
        code = SourceErrorCode.EXTENSION_RUNTIME_FAILURE,
        diagnostic = SourceDiagnostic("source-unavailable", "source-session", "source-untrusted-or-closed"),
    )

    fun requireClientOrNull(): SourceFlowSession? = synchronized(lock) {
        checkOpen()
        val installed = activePackage ?: return@synchronized null
        client?.takeIf { isPackageTrusted(installed) }
    }

    fun hasUntrustedActivePackage(): Boolean = synchronized(lock) {
        checkOpen()
        activePackage?.let { !isPackageTrusted(it) } == true
    }

    suspend fun reopen(): SourceSessionOpenResult? {
        val (packageInfo, generation) = synchronized(lock) {
            checkOpen()
            val installed = activePackage?.takeIf { client != null } ?: return null
            installed to openGeneration
        }
        return open(packageInfo, forceReopen = true, expectedGeneration = generation)
    }

    /** Invalidates pending preparations without permanently closing the reusable session owner. */
    fun removeSource(sourceId: String): Boolean {
        val removed: Pair<SourceFlowSession?, PendingSourceOpen?>? = synchronized(lock) {
            checkOpen()
            val active = activePackage
            val activeMatches = active?.manifest?.sourceId?.value == sourceId
            val pending = opening?.takeIf { it.packageInfo.manifest.sourceId.value == sourceId }
            if (!activeMatches && pending == null) return false
            openGeneration += 1
            val previous = if (activeMatches) client else null
            if (activeMatches) {
                client = null
                activePackage = null
            }
            if (pending != null) opening = null
            statePackageSha256 = null
            if (requestedPackageSha256 == active?.packageSha256 || pending != null) {
                requestedPackageSha256 = null
            }
            previous to pending
        }
        val (previousClient, invalidated) = removed ?: return false
        invalidated?.completion?.complete(PendingOpenOutcome.UNAVAILABLE)
        previousClient?.close()
        return true
    }

    fun closeActiveClient() {
        val (activeClient, invalidated) = synchronized(lock) {
            checkOpen()
            openGeneration += 1
            val previous = client
            client = null
            activePackage = null
            val pending = opening
            opening = null
            requestedPackageSha256 = null
            previous to pending
        }
        invalidated?.completion?.complete(PendingOpenOutcome.UNAVAILABLE)
        activeClient?.close()
    }

    override fun close() {
        val (activeClient, invalidated) = synchronized(lock) {
            if (closed) return
            closed = true
            openGeneration += 1
            val previous = client
            client = null
            activePackage = null
            val pending = opening
            opening = null
            requestedPackageSha256 = null
            previous to pending
        }
        invalidated?.completion?.complete(PendingOpenOutcome.UNAVAILABLE)
        activeClient?.close()
    }

    private fun checkOpen() {
        check(!closed) { "Source flow is closed" }
    }

    companion object {
        fun extensionClientFactory(
            context: android.content.Context,
            directActionTokens: DirectActionTokenRegistry,
        ): suspend (VerifiedHxpPackage) -> SourceFlowSession = { packageInfo ->
            val (native, verifiedGet) = Phase2SourceGateway.createSession(context, packageInfo, directActionTokens)
            ExtensionSourceFlowSession(
                delegate = SourceExtensionClient.open(packageInfo, native, verifiedGet),
                verifiedPageClient = { snapshot ->
                    SourceExtensionClient.open(
                        packageInfo,
                        Phase2SourceGateway.createVerifiedPage(
                            context = context,
                            packageInfo = packageInfo,
                            snapshot = snapshot,
                            directActionTokens = directActionTokens,
                        ),
                    )
                },
            )
        }
    }
}
