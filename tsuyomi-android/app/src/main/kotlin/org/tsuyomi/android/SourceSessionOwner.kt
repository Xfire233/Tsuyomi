/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import java.io.Closeable
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
    UNAVAILABLE,
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

    suspend fun open(
        packageInfo: VerifiedHxpPackage,
        onPackageChanged: () -> Unit = {},
    ): SourceSessionOpenResult {
        if (!isPackageTrusted(packageInfo)) {
            closeActiveClient()
            return SourceSessionOpenResult.UNAVAILABLE
        }
        val (previousClient, operationGeneration, packageChanged) = synchronized(lock) {
            checkOpen()
            if (activePackage?.packageSha256 == packageInfo.packageSha256 && client != null) {
                return SourceSessionOpenResult.ALREADY_OPEN
            }
            openGeneration += 1
            val previous = client
            client = null
            activePackage = null
            val changed = statePackageSha256 != packageInfo.packageSha256
            statePackageSha256 = packageInfo.packageSha256
            Triple(previous, openGeneration, changed)
        }
        previousClient?.close()
        if (packageChanged) onPackageChanged()

        val openedClient = openSession(packageInfo)
        val retained = synchronized(lock) {
            if (closed || openGeneration != operationGeneration || !isPackageTrusted(packageInfo)) {
                false
            } else {
                client = openedClient
                activePackage = packageInfo
                true
            }
        }
        if (!retained) {
            openedClient.close()
            synchronized(lock) { checkOpen() }
            if (!isPackageTrusted(packageInfo)) return SourceSessionOpenResult.UNAVAILABLE
            return SourceSessionOpenResult.ALREADY_OPEN
        }
        return if (packageChanged) SourceSessionOpenResult.PACKAGE_CHANGED else SourceSessionOpenResult.OPENED
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
        val committed: Pair<SourceFlowSession?, Boolean>? = synchronized(lock) {
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
                client = prepared.client
                activePackage = prepared.packageInfo
                val changed = statePackageSha256 != prepared.packageInfo.packageSha256
                statePackageSha256 = prepared.packageInfo.packageSha256
                previous to changed
            }
        }
        val (previousClient, _) = committed ?: return false
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
        activePackage?.takeIf(isPackageTrusted)?.let { ActiveSourceSession(it, openGeneration) }
    }


    fun requireClient(): SourceFlowSession = requireClientOrNull() ?: throw SourceException(
        code = SourceErrorCode.EXTENSION_RUNTIME_FAILURE,
        diagnostic = SourceDiagnostic("source-unavailable", "source-session", "source-untrusted-or-closed"),
    )

    fun requireClientOrNull(): SourceFlowSession? = synchronized(lock) {
        checkOpen()
        client.takeIf { activePackage?.let(isPackageTrusted) == true }
    }

    fun hasUntrustedActivePackage(): Boolean = synchronized(lock) {
        checkOpen()
        activePackage?.let { !isPackageTrusted(it) } == true
    }

    suspend fun reopen(): SourceSessionOpenResult? {
        val packageInfo = synchronized(lock) {
            checkOpen()
            activePackage
        } ?: return null
        closeActiveClient()
        return open(packageInfo)
    }

    /** Invalidates pending preparations without permanently closing the reusable session owner. */
    fun removeSource(sourceId: String): Boolean {
        val removed = synchronized(lock) {
            checkOpen()
            openGeneration += 1
            if (activePackage?.manifest?.sourceId?.value != sourceId) return false
            val previous = client
            client = null
            activePackage = null
            statePackageSha256 = null
            previous
        }
        removed?.close()
        return true
    }

    fun closeActiveClient() {
        val activeClient = synchronized(lock) {
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
        val activeClient = synchronized(lock) {
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
