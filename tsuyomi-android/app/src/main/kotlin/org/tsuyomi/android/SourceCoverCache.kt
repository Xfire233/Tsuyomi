/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import org.tsuyomi.core.media.api.CoverMediaFetcher
import org.tsuyomi.core.media.api.CoverMediaPayload
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRepositoryFactory
import org.tsuyomi.core.network.DirectActionTokenRegistry
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
        activePartition = partition
        activeState = SourceCoverCacheState(
            repository = CoverRepositoryFactory.create(
                context = context,
                origins = network.origins,
                maxResponseBytes = network.maxResponseBytes,
                sourceId = partition.sourceId,
                packageRevision = partition.packageRevision,
                credentialRevision = partition.credentialRevision,
                mediaFetcher = fetcher,
            ),
            sourceId = partition.sourceId,
            packageRevision = partition.packageRevision,
            credentialRevision = partition.credentialRevision,
        )
        return activeState
    }

    fun clear() {
        activePartition = null
        activeState = SourceCoverCacheState(null, null, null, null)
    }
}