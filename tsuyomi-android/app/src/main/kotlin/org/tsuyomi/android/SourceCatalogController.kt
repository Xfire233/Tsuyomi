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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.tsuyomi.feature.browse.BrowseCatalogItem
import org.tsuyomi.feature.browse.BrowseCatalogAction
import org.tsuyomi.feature.browse.BrowseCatalogState
import org.tsuyomi.feature.browse.BrowseCatalogStatus
import org.tsuyomi.feature.browse.BrowseRepository
import org.tsuyomi.feature.browse.BrowseSubscriptionState
import org.tsuyomi.source.extensionmanager.OfficialRepositoryClient
import org.tsuyomi.source.extensionmanager.RepositoryCatalog
import org.tsuyomi.source.extensionmanager.RepositorySubscriptionLink
import org.tsuyomi.source.extensionmanager.SemanticVersion

/** Discovery is independent of source sessions; every activation still uses installer approval. */
internal class SourceCatalogController(
    private val context: Context,
    private val client: OfficialRepositoryClient?,
    private val installer: SourceInstallController,
    private val subscriptions: org.tsuyomi.source.extensionmanager.RepositorySubscriptionRegistry,
) {
    private val snapshots = linkedMapOf<String, RepositoryCatalog>()
    private val failedRepositories = mutableSetOf<String>()
    private var proposed: RepositorySubscriptionLink? = null
    var state by mutableStateOf(BrowseCatalogState())
        private set

    internal fun repository(id: String): OfficialRepositoryClient? =
        if (id == OFFICIAL_ID) client else subscriptions.client(id)

    private fun repositories(): Map<String, OfficialRepositoryClient> = buildMap {
        client?.let { put(OFFICIAL_ID, it) }
        for (subscription in subscriptions.subscriptions()) {
            if (subscription.enabled) subscriptions.client(subscription.root.repositoryId)?.let {
                put(subscription.root.repositoryId, it)
            }
        }
    }

    suspend fun restoreCache() {
        val repositories = withContext(Dispatchers.IO) { repositories() }
        snapshots.keys.retainAll(repositories.keys)
        for ((id, repository) in repositories) {
            try {
                withContext(Dispatchers.IO) { repository.cached() }?.let { snapshots[id] = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failedRepositories += id
            }
        }
        onInstalledPackagesChanged()
    }

    suspend fun refresh() {
        if (state.status == BrowseCatalogStatus.LOADING || state.busySourceId != null || installer.mutationPending) return
        state = state.copy(status = BrowseCatalogStatus.LOADING, problem = null)
        try {
            withContext(NonCancellable) {
                val repositories = withContext(Dispatchers.IO) { repositories() }
                snapshots.keys.retainAll(repositories.keys)
                for ((id, repository) in repositories) {
                    try {
                        val accepted = installer.refreshRepositoryCatalog(repository)
                        if (accepted != null) {
                            snapshots[id] = accepted
                            failedRepositories -= id
                        }
                    } catch (_: Exception) {
                        failedRepositories += id
                    }
                }
                state = state.copy(status = BrowseCatalogStatus.IDLE)
                onInstalledPackagesChanged()
            }
        } catch (cancelled: CancellationException) {
            state = state.copy(status = BrowseCatalogStatus.IDLE)
            throw cancelled
        }
    }

    suspend fun install(sourceId: String, repositoryId: String = OFFICIAL_ID) {
        if (state.busySourceId != null || installer.mutationPending) return
        val action = BrowseCatalogAction.Install(sourceId, repositoryId)
        val repository = resolveCurrentInstall(action) ?: run {
            installer.rejectUnavailableRepositoryInstall(action)
            return
        }
        state = state.copy(busySourceId = "$repositoryId\u0000$sourceId")
        try {
            installer.prepareRepository(action, repository)
        } finally {
            state = state.copy(busySourceId = null)
        }
    }

    internal fun isCurrentInstallable(
        action: BrowseCatalogAction.Install,
        expectedRepository: OfficialRepositoryClient,
    ): Boolean = resolveCurrentInstall(action) === expectedRepository

    private fun resolveCurrentInstall(action: BrowseCatalogAction.Install): OfficialRepositoryClient? {
        onInstalledPackagesChanged()
        val item = state.items.singleOrNull {
            it.sourceId == action.sourceId && it.repositoryId == action.repositoryId
        } ?: return null
        if (!item.installable || !item.compatible || (item.installedVersion != null && !item.updateAvailable)) return null
        return repository(action.repositoryId)
    }

    suspend fun inspectSubscription(link: String) {
        if (installer.mutationPending) return
        proposed = null
        try {
            val proposal = withContext(Dispatchers.IO) { subscriptions.inspect(link) }
            require(proposal.root.repositoryId != OFFICIAL_ID && proposal.root.repositoryId != "org.tsuyomi.extensions")
            proposed = proposal
            state = state.copy(subscription = BrowseSubscriptionState(
                link = link, repositoryId = proposal.root.repositoryId, indexUrl = proposal.root.indexUrl,
                rootFingerprint = proposal.root.signingKey.fingerprint,
            ))
        } catch (_: Exception) {
            state = state.copy(subscription = BrowseSubscriptionState(link, problem = context.getString(R.string.source_subscription_invalid)))
        }
    }

    suspend fun confirmSubscription() {
        val proposal = proposed ?: return
        if (installer.mutationPending) return
        state = state.copy(subscription = state.subscription?.copy(busy = true))
        try {
            installer.withRepositoryMutation {
                withContext(Dispatchers.IO) { subscriptions.confirm(proposal) }
            }
            proposed = null
            state = state.copy(subscription = null)
            refresh()
        } catch (_: Exception) {
            state = state.copy(subscription = state.subscription?.copy(
                busy = false, problem = context.getString(R.string.source_subscription_invalid),
            ))
        }
    }

    fun cancelSubscription() {
        if (state.subscription?.busy == true) return
        proposed = null
        state = state.copy(subscription = null)
    }

    suspend fun removeSubscription(id: String) {
        changeSubscription(id) { subscriptions.remove(id) }
    }

    suspend fun setSubscriptionEnabled(id: String, enabled: Boolean) {
        changeSubscription(id) { subscriptions.setEnabled(id, enabled) }
    }

    private suspend fun changeSubscription(id: String, change: () -> Unit) {
        if (id == OFFICIAL_ID || installer.mutationPending) return
        try {
            installer.withRepositoryMutation { withContext(Dispatchers.IO) { change() } }
            snapshots.remove(id)
            failedRepositories -= id
            restoreCache()
            refresh()
        } catch (_: Exception) {
            state = state.copy(problem = context.getString(R.string.source_repository_failure))
        }
    }

    fun onInstalledPackagesChanged() {
        val records = subscriptions.subscriptions()
        val enabled = records.filter { it.enabled }.mapTo(mutableSetOf()) { it.root.repositoryId }
        if (client != null) enabled += OFFICIAL_ID
        val installed = installer.installedPackages.associateBy { it.manifest.sourceId.value }
        val now = Instant.now()
        val usable = snapshots.filter { (id, catalog) -> id in enabled && id !in failedRepositories && catalog.expiresAt.isAfter(now) }.keys
        val items = snapshots.filterKeys { it in enabled }.flatMap { (id, catalog) ->
            catalog.packages.map { entry ->
                val active = installed[entry.id]
                BrowseCatalogItem(
                    sourceId = entry.id, name = entry.name, version = entry.version, summary = entry.summary,
                    language = entry.language, license = entry.license, sourceUrl = entry.sourceUrl,
                    sourceRevision = entry.sourceRevision,
                    publisherFingerprint = repository(id)?.publisherKeys?.resolve(entry.publisherKeyId)?.fingerprint.orEmpty(),
                    compatible = entry.isCompatible(HOST_API), installedVersion = active?.manifest?.version?.original,
                    updateAvailable = active != null && SemanticVersion.parse(entry.version) > active.manifest.version,
                    repositoryId = id, repositoryName = if (id == OFFICIAL_ID) context.getString(R.string.source_repository_official) else id,
                    official = id == OFFICIAL_ID, installable = id in usable,
                )
            }
        }
        val repositoryRows = buildList {
            if (client != null) add(BrowseRepository(
                OFFICIAL_ID, context.getString(R.string.source_repository_official),
                "https://raw.githubusercontent.com/Chachaanteng/tsuyomi-extensions/repository/index-v1.json",
                if (BuildConfig.OFFICIAL_REPOSITORY_PUBLIC_KEY.isEmpty()) "" else org.tsuyomi.source.extensionmanager.PublisherKey(
                    BuildConfig.OFFICIAL_REPOSITORY_KEY_ID,
                    java.util.Base64.getDecoder().decode(BuildConfig.OFFICIAL_REPOSITORY_PUBLIC_KEY),
                    org.tsuyomi.source.extensionmanager.PublisherTrust.BUILT_IN_OFFICIAL,
                ).fingerprint,
                official = true, enabled = true,
            ))
            records.forEach { record -> add(BrowseRepository(
                record.root.repositoryId, record.root.repositoryId, record.root.indexUrl,
                record.root.signingKey.fingerprint, official = false, enabled = record.enabled,
            )) }
        }
        val problem = when {
            failedRepositories.any { it in enabled } -> context.getString(R.string.source_repository_failure)
            enabled.isEmpty() -> context.getString(R.string.source_repository_unconfigured)
            else -> null
        }
        state = state.copy(
            items = items, repositories = repositoryRows, problem = problem,
            stale = usable.isEmpty() && snapshots.isNotEmpty(),
            status = when {
                state.status == BrowseCatalogStatus.LOADING -> BrowseCatalogStatus.LOADING
                usable.isNotEmpty() -> BrowseCatalogStatus.READY
                failedRepositories.any { it in enabled } -> BrowseCatalogStatus.ERROR
                enabled.isEmpty() -> BrowseCatalogStatus.UNAVAILABLE
                else -> BrowseCatalogStatus.IDLE
            },
        )
    }

    private companion object {
        const val OFFICIAL_ID = "official"
        val HOST_API = SemanticVersion.parse("1.2.0")
    }
}
