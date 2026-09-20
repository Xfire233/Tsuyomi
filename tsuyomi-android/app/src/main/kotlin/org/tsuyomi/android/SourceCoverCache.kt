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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.tsuyomi.core.media.api.CoverMediaFetcher
import org.tsuyomi.core.media.api.CoverMediaPayload
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRepositoryFactory
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

/**
 * The display-facing identity of one bounded cover-cache partition. None of its fields contains
 * credentials or fetched media bytes.
 */
internal data class SourceCoverCacheState(
    val repository: CoverRepository?,
    val sourceId: String?,
    val packageRevision: String?,
    val credentialRevision: String?,
)

/**
 * Keeps the current source's cover repository alive for the activity's source runtime. The
 * repository itself owns bounded memory and disk caches; this owner retains at most one partition.
 */
internal class SourceCoverCache(
    private val context: Context,
    private val isPackageTrusted: (VerifiedHxpPackage) -> Boolean = OfficialRepositoryConfiguration.admission(context),
) {
    private data class Partition(
        val sourceId: String,
        val packageRevision: String,
        val credentialRevision: String,
    )

    private val directActionTokens = DirectActionTokenRegistry()
    @Volatile
    private var activePartition: Partition? = null
    private var activeState = SourceCoverCacheState(null, null, null, null)

    /** Resolves only an admitted active archive; absence is a real authority boundary. */
    fun resolve(packageInfo: VerifiedHxpPackage?): SourceCoverCacheState {
        if (packageInfo == null || !isPackageTrusted(packageInfo)) {
            clear()
            return activeState
        }
        val credentialRevision = SourceGatewayFactory.mediaCredentialRevision(context, packageInfo)
        val partition = Partition(
            sourceId = packageInfo.manifest.sourceId.value,
            packageRevision = packageInfo.packageSha256,
            credentialRevision = credentialRevision,
        )
        if (partition == activePartition) return activeState

        val gateway = Phase2SourceGateway.create(context, packageInfo, directActionTokens)
        fun requireCurrentAuthority() {
            check(activePartition === partition && isPackageTrusted(packageInfo)) { "source-cover-authority-changed" }
        }
        val fetcher = CoverMediaFetcher { url, referrerUrl ->
            requireCurrentAuthority()
            val payload = gateway.fetchMedia(SourceGatewayFactory.networkGrant(packageInfo), url, referrerUrl)
            requireCurrentAuthority()
            CoverMediaPayload(payload.bytes, payload.contentType)
        }
        val network = packageInfo.manifest.capabilities.network
        activePartition = null
        activeState.repository?.close()
        val repository = CoverRepositoryFactory.create(
            context = context,
            origins = network.origins,
            maxResponseBytes = network.maxResponseBytes,
            sourceId = partition.sourceId,
            packageRevision = partition.packageRevision,
            credentialRevision = partition.credentialRevision,
            mediaFetcher = fetcher,
        )
        activePartition = partition
        activeState = SourceCoverCacheState(
            repository = repository,
            sourceId = partition.sourceId,
            packageRevision = partition.packageRevision,
            credentialRevision = partition.credentialRevision,
        )
        return activeState
    }

    fun clear() {
        activePartition = null
        activeState.repository?.close()
        activeState = SourceCoverCacheState(null, null, null, null)
    }
}

/**
 * Route-owner presentation state for source covers. Composables report visibility and render this
 * state only; they never own repository flows. Recent ready bitmaps remain renderable while a
 * lifecycle restart or recycled item revalidates the same credential-bound request.
 */
internal class SourceCoverPresenter : Closeable {
    private data class RequestKey(
        val identity: BookIdentity,
        val transportUrl: String,
        val referrerUrl: String?,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var repository: CoverRepository? = null
    private var sourceId: String? = null
    private var packageRevision: String? = null
    private var credentialRevision: String? = null
    private val visibleCounts = mutableMapOf<RequestKey, Int>()
    private val visibleBooks = mutableMapOf<RequestKey, SourceBookSummary>()
    private val jobs = mutableMapOf<RequestKey, Job>()
    private val retainedOrder = linkedSetOf<RequestKey>()
    private var states by mutableStateOf<Map<RequestKey, CoverUiState>>(emptyMap())

    fun configure(
        repository: CoverRepository?,
        sourceId: String?,
        packageRevision: String?,
        credentialRevision: String?,
    ) {
        if (this.repository === repository && this.sourceId == sourceId &&
            this.packageRevision == packageRevision && this.credentialRevision == credentialRevision
        ) return
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        retainedOrder.clear()
        states = emptyMap()
        this.repository = repository
        this.sourceId = sourceId
        this.packageRevision = packageRevision
        this.credentialRevision = credentialRevision
        visibleBooks.forEach { (key, book) -> if (visibleCounts.containsKey(key)) start(key, book) }
    }

    fun setVisible(book: SourceBookSummary, visible: Boolean) {
        val key = book.requestKey() ?: return
        if (!visible) {
            val nextCount = (visibleCounts[key] ?: 1) - 1
            if (nextCount > 0) {
                visibleCounts[key] = nextCount
            } else {
                visibleCounts.remove(key)
                visibleBooks.remove(key)
                jobs.remove(key)?.cancel()
                trimRetainedStates()
            }
            return
        }
        val previousCount = visibleCounts[key] ?: 0
        visibleCounts[key] = previousCount + 1
        visibleBooks[key] = book
        retainedOrder.remove(key)
        retainedOrder += key
        if (previousCount == 0) start(key, book)
    }

    fun state(book: SourceBookSummary): CoverUiState {
        val fallback = FallbackSpec(book.title, book.identity.sourceId)
        val key = book.requestKey() ?: return CoverUiState.Absent(fallback)
        return states[key] ?: CoverUiState.Fallback(fallback)
    }

    private fun start(key: RequestKey, book: SourceBookSummary) {
        if (jobs[key]?.isActive == true) return
        val activeRepository = repository
        val activeSourceId = sourceId
        val activePackageRevision = packageRevision
        val activeCredentialRevision = credentialRevision
        val fallback = FallbackSpec(book.title, book.identity.sourceId)
        if (activeRepository == null || activeSourceId != book.identity.sourceId ||
            activePackageRevision == null || activeCredentialRevision == null
        ) {
            retain(key, CoverUiState.Fallback(fallback))
            return
        }
        jobs[key] = scope.launch {
            activeRepository.observe(
                CoverRequest(
                    sourceId = activeSourceId,
                    packageRevision = activePackageRevision,
                    credentialRevision = activeCredentialRevision,
                    transportUrl = key.transportUrl,
                    referrerUrl = key.referrerUrl,
                    targetWidthPx = SOURCE_COVER_WIDTH_PX,
                    targetHeightPx = SOURCE_COVER_HEIGHT_PX,
                    fallback = fallback,
                ),
            ).collect { next -> retain(key, preserveRenderableCover(states[key], next)) }
        }
    }

    private fun retain(key: RequestKey, state: CoverUiState) {
        states = states + (key to state)
        retainedOrder.remove(key)
        retainedOrder += key
        trimRetainedStates()
    }

    private fun trimRetainedStates() {
        while (states.size > MAX_RETAINED_SOURCE_COVERS) {
            val victim = retainedOrder.firstOrNull { it !in visibleCounts } ?: return
            retainedOrder.remove(victim)
            states = states - victim
        }
    }

    private fun SourceBookSummary.requestKey(): RequestKey? = coverUrl
        ?.takeIf(String::isNotBlank)
        ?.let { RequestKey(identity, it, canonicalUrl) }

    override fun close() {
        scope.cancel()
        jobs.clear()
        visibleCounts.clear()
        visibleBooks.clear()
        retainedOrder.clear()
        states = emptyMap()
    }

    private companion object {
        const val SOURCE_COVER_WIDTH_PX = 240
        const val SOURCE_COVER_HEIGHT_PX = 360
        const val MAX_RETAINED_SOURCE_COVERS = 48
    }
}

internal fun preserveRenderableCover(previous: CoverUiState?, next: CoverUiState): CoverUiState = when {
    next is CoverUiState.Loading && previous is CoverUiState.Ready ->
        CoverUiState.StaleReady(previous.bitmap, "route-retained")
    next is CoverUiState.Loading && previous is CoverUiState.StaleReady -> previous
    next is CoverUiState.Failed && previous is CoverUiState.Ready ->
        CoverUiState.StaleReady(previous.bitmap, "reload-failed")
    next is CoverUiState.Failed && previous is CoverUiState.StaleReady -> previous
    else -> next
}