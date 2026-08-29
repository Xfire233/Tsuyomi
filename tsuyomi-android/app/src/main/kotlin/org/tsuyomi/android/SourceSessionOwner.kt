/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import java.io.Closeable
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.source.extensionmanager.SourceExtensionClient
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

internal interface SourceFlowSession : Closeable {
    suspend fun search(query: String, page: Int = 1, offlineOnly: Boolean = false): List<SourceBookSummary>
    suspend fun detail(remoteBookId: String, offlineOnly: Boolean = false): SourceBookDetail
    suspend fun directory(remoteBookId: String, offlineOnly: Boolean = false): SourceDirectory
    suspend fun chapter(chapter: SourceChapter, remoteBookId: String, offlineOnly: Boolean = false): ReaderDocument
    suspend fun listRemoteLibrary(cursor: String?): RemoteLibraryPage
    suspend fun addRemoteLibrary(remoteBookId: String, directActionToken: String): RemoteLibraryAddResult
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

internal data class ActiveSourceSession(
    val packageInfo: VerifiedHxpPackage,
    val ownerGeneration: Long,
)
internal enum class SourceSessionOpenResult {
    ALREADY_OPEN,
    OPENED,
    PACKAGE_CHANGED,
}


internal class SourceSessionOwner(
    val directActionTokens: DirectActionTokenRegistry,
    private val openSession: suspend (VerifiedHxpPackage) -> SourceFlowSession,
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
            synchronized(lock) { checkOpen() }
            return SourceSessionOpenResult.ALREADY_OPEN
        }
        return if (packageChanged) SourceSessionOpenResult.PACKAGE_CHANGED else SourceSessionOpenResult.OPENED
    }

    fun active(): ActiveSourceSession? = synchronized(lock) {
        checkOpen()
        activePackage?.let { ActiveSourceSession(it, openGeneration) }
    }

    fun requireClient(): SourceFlowSession = synchronized(lock) {
        checkOpen()
        checkNotNull(client) { "Source is not open" }
    }

    fun requireClientOrNull(): SourceFlowSession? = synchronized(lock) {
        checkOpen()
        client
    }

    suspend fun reopen(): SourceSessionOpenResult? {
        val packageInfo = synchronized(lock) {
            checkOpen()
            activePackage
        } ?: return null
        closeActiveClient()
        return open(packageInfo)
    }

    private fun closeActiveClient() {
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
            ExtensionSourceFlowSession(
                SourceExtensionClient.open(
                    packageInfo,
                    Phase2SourceGateway.create(context, packageInfo, directActionTokens),
                ),
            )
        }
    }
}
