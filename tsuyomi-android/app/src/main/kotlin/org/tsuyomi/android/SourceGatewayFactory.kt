/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import java.security.MessageDigest
import org.tsuyomi.core.network.HostHttpTransport
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.core.network.HostNetworkGateway
import org.tsuyomi.core.network.FileHostNetworkCache
import org.tsuyomi.core.network.SourceNetworkGrant
import org.tsuyomi.shared.sourcecontract.SourceCookieMode
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

internal object SourceGatewayFactory {
    fun create(
        context: Context,
        packageInfo: VerifiedHxpPackage,
        transport: HostHttpTransport,
    ): HostNetworkGateway {
        val manifest = packageInfo.manifest
        val grant = SourceNetworkGrant(
            sourceId = manifest.sourceId.value,
            extensionVersion = manifest.version.original,
            origins = manifest.capabilities.network.origins,
            cookieMode = if (manifest.capabilities.cookies.sourceScoped) {
                SourceCookieMode.SOURCE_SCOPED
            } else {
                SourceCookieMode.NONE
            },
            cookieOrigins = manifest.capabilities.cookies.origins,
            maxConcurrentRequests = manifest.capabilities.network.maxConcurrentRequests,
            requestTimeoutMs = manifest.capabilities.network.requestTimeoutMs,
            maxResponseBytes = manifest.capabilities.network.maxResponseBytes,
        )
        val credentials = SourceCredentialStore(context)
        val credentialSnapshots = manifest.capabilities.cookies.origins.mapNotNull { origin ->
            credentials.getSnapshot(SourceCredentialPartition(manifest.sourceId.value, origin))
                ?.let { snapshot -> origin to snapshot }
        }
        val cachePartition = if (credentialSnapshots.isEmpty()) {
            "anonymous"
        } else {
            sha256(
                buildString {
                    credentialSnapshots.sortedBy { it.first.canonical }.forEach { (origin, snapshot) ->
                        append(origin.canonical)
                        append('\u0000')
                        append(snapshot.cachePartitionId)
                        append('\n')
                    }
                }.encodeToByteArray(),
            )
        }
        val gateway = HostNetworkGateway(
            transport,
            FileHostNetworkCache(
                files = QuotaFileStore(
                    roots = StorageRoots.from(context),
                    root = StorageRoot.CACHE,
                    namespace = "source-network-cache",
                    quota = StorageQuota(maxBytes = 64L * 1024 * 1024, maxEntries = 512),
                ),
                partition = cachePartition,
            ),
        )
        credentialSnapshots.forEach { (origin, snapshot) ->
            gateway.importSourceCookies(
                grant,
                origin,
                snapshot.plaintext.decodeToString(throwOnInvalidSequence = true),
            )
        }
        return gateway
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
