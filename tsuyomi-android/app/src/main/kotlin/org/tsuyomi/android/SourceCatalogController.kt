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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tsuyomi.feature.browse.BrowseCatalogItem
import org.tsuyomi.feature.browse.BrowseCatalogState
import org.tsuyomi.feature.browse.BrowseCatalogStatus
import org.tsuyomi.source.extensionmanager.OfficialRepositoryClient
import org.tsuyomi.source.extensionmanager.RepositoryCatalog
import org.tsuyomi.source.extensionmanager.SemanticVersion

/** Catalog discovery owns no website session and cannot install without the shared approval flow. */
internal class SourceCatalogController(
    private val context: Context,
    private val client: OfficialRepositoryClient?,
    private val installer: SourceInstallController,
) {
    private var snapshot: RepositoryCatalog? = null
    var state by mutableStateOf(
        if (client == null) BrowseCatalogState(
            status = BrowseCatalogStatus.UNAVAILABLE,
            problem = context.getString(R.string.source_repository_unconfigured),
        ) else BrowseCatalogState(),
    )
        private set

    suspend fun restoreCache() {
        val repository = client ?: return
        try {
            snapshot = withContext(Dispatchers.IO) { repository.cached() }
            onInstalledPackagesChanged()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state = state.copy(
                status = BrowseCatalogStatus.ERROR,
                stale = true,
                problem = context.getString(R.string.source_repository_failure),
            )
        }
    }

    suspend fun refresh() {
        val repository = client ?: return
        if (state.status == BrowseCatalogStatus.LOADING || state.busySourceId != null || installer.mutationPending) return
        val previous = state
        state = state.copy(status = BrowseCatalogStatus.LOADING, problem = null, stale = snapshot != null)
        try {
            withContext(kotlinx.coroutines.NonCancellable) {
                val accepted = installer.refreshRepositoryCatalog()
                if (accepted == null) {
                    state = previous
                } else {
                    snapshot = accepted
                    state = state.copy(status = BrowseCatalogStatus.READY, stale = false, problem = null)
                    onInstalledPackagesChanged()
                }
            }
        } catch (cancelled: CancellationException) {
            state = previous
            throw cancelled
        } catch (_: Exception) {
            snapshot = withContext(Dispatchers.IO) { runCatching { repository.cached() }.getOrNull() }
            state = state.copy(
                status = BrowseCatalogStatus.ERROR,
                stale = snapshot != null,
                problem = context.getString(R.string.source_repository_failure),
            )
            onInstalledPackagesChanged()
        }
    }

    suspend fun install(sourceId: String) {
        if (state.status != BrowseCatalogStatus.READY || state.stale || state.busySourceId != null || installer.mutationPending) return
        val item = state.items.singleOrNull { it.sourceId == sourceId } ?: return
        if (!item.compatible || (item.installedVersion != null && !item.updateAvailable)) return
        state = state.copy(busySourceId = sourceId)
        try {
            installer.prepareRepository(sourceId)
        } finally {
            state = state.copy(busySourceId = null)
        }
    }

    fun onInstalledPackagesChanged() {
        val catalog = snapshot ?: return
        val installed = installer.installedPackages.associateBy { it.manifest.sourceId.value }
        state = state.copy(items = catalog.packages.map { entry ->
            val active = installed[entry.id]
            BrowseCatalogItem(
                sourceId = entry.id,
                name = entry.name,
                version = entry.version,
                summary = entry.summary,
                language = entry.language,
                license = entry.license,
                sourceUrl = entry.sourceUrl,
                sourceRevision = entry.sourceRevision,
                publisherFingerprint = client?.publisherKeys?.resolve(entry.publisherKeyId)?.fingerprint.orEmpty(),
                compatible = entry.isCompatible(HOST_API),
                installedVersion = active?.manifest?.version?.original,
                updateAvailable = active != null && SemanticVersion.parse(entry.version) > active.manifest.version,
            )
        }, stale = state.stale || !catalog.expiresAt.isAfter(Instant.now()))
    }

    private companion object {
        val HOST_API = SemanticVersion.parse("1.2.0")
    }
}
