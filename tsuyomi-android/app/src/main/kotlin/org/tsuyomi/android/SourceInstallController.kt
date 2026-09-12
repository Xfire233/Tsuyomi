/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.database.SourceRemotePolicy
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.feature.browse.BrowseCatalogAction
import org.tsuyomi.feature.browse.BrowseInstallFailure
import org.tsuyomi.feature.browse.BrowseResourceLimit
import org.tsuyomi.feature.browse.BrowseResourceLimitIncrease
import org.tsuyomi.feature.browse.BrowseUiState
import org.tsuyomi.feature.library.LibraryMirrorShortcut
import org.tsuyomi.source.extensionmanager.ExtensionInstallApproval
import org.tsuyomi.source.extensionmanager.ExtensionInstallException
import org.tsuyomi.source.extensionmanager.ExtensionInstallError
import org.tsuyomi.source.extensionmanager.ExtensionInstaller
import org.tsuyomi.source.extensionmanager.HxpArchiveVerifier
import org.tsuyomi.source.extensionmanager.HxpVerificationException
import org.tsuyomi.source.extensionmanager.InstalledExtensionStore
import org.tsuyomi.source.extensionmanager.OfficialRepositoryClient
import org.tsuyomi.source.extensionmanager.PreparedExtensionInstall
import org.tsuyomi.source.extensionmanager.RemoteOperation
import org.tsuyomi.source.extensionmanager.RepositoryCatalog
import org.tsuyomi.source.extensionmanager.RepositoryCatalogError
import org.tsuyomi.source.extensionmanager.RepositoryCatalogException
import org.tsuyomi.source.extensionmanager.RepositoryFetchError
import org.tsuyomi.source.extensionmanager.RepositoryFetchException
import org.tsuyomi.source.extensionmanager.ResourceLimit
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

/** App-owned coordinator: the picker grants transient read access; only verified archives become durable. */
class SourceInstallController(
    private val context: Context,
    private val libraryRepository: RoomLibraryRepository,
    private val repositoryClient: OfficialRepositoryClient? = (context.applicationContext as TsuyomiApplication).officialRepository,
    private val subscriptions: org.tsuyomi.source.extensionmanager.RepositorySubscriptionRegistry =
        (context.applicationContext as TsuyomiApplication).repositorySubscriptions,
    private val packageTrust: org.tsuyomi.source.extensionmanager.PackageTrustRegistry =
        (context.applicationContext as TsuyomiApplication).packageTrust,
) {
    private val credentialStore = VerifiedBrowserSessionStore(context)
    private val stagingDirectory = File(context.cacheDir, "hxp-staging")
    private val mutationMutex = (context.applicationContext as TsuyomiApplication).extensionMutationMutex
    private val publisherKeys = org.tsuyomi.source.extensionmanager.CompositePublisherKeyResolver(
        listOf(OfficialRepositoryConfiguration.publisherKeys(repositoryClient), subscriptions.publisherKeys, packageTrust.publisherKeys),
    )
    private val store = InstalledExtensionStore(
        QuotaFileStore(
            roots = StorageRoots.from(context),
            root = StorageRoot.NO_BACKUP,
            namespace = "extensions",
            quota = StorageQuota(maxBytes = 64L * 1024 * 1024, maxEntries = 16),
        ),
    )
    private val installer = ExtensionInstaller(
        verifier = HxpArchiveVerifier(publisherKeys),
        store = store,
        stagingDirectory = stagingDirectory,
        packageExecutionTrust = packageTrust,
    )

    private var selectedPackage: VerifiedHxpPackage? by mutableStateOf(null)
    var activePackage: VerifiedHxpPackage?
        get() = selectedPackage?.takeIf { OfficialRepositoryConfiguration.isTrusted(it, publisherKeys) }
        private set(value) { selectedPackage = value }
    private var prepared: PreparedExtensionInstall? = null
    private var preparedRepositoryInstall: BrowseCatalogAction.Install? = null
    private var preparedRepository: OfficialRepositoryClient? = null
    private var preparedPublisher: org.tsuyomi.source.extensionmanager.PublisherKey? = null
    private var preparedPublisherFromInput = false
    private var pendingLocalArchive: File? = null
    private var installedLoaded = false
    var installedPackages: List<VerifiedHxpPackage> by mutableStateOf(emptyList())
        private set

    internal val trustedInstalledPackages: List<VerifiedHxpPackage>
        get() = installedPackages.filter { OfficialRepositoryConfiguration.isTrusted(it, publisherKeys) }
    internal val catalog = SourceCatalogController(context, repositoryClient, this, subscriptions)
    internal val mutationPending: Boolean
        get() = mutationMutex.isLocked || prepared != null || pendingLocalArchive != null

    var state: BrowseUiState by mutableStateOf(BrowseUiState.Empty)
        private set

    /**
     * Selects one exact installed archive without installing, re-verifying its metadata in Room,
     * or borrowing a different source's foreground session.
     */
    suspend fun activateInstalledSource(sourceId: String): VerifiedHxpPackage? {
        if (prepared != null || pendingLocalArchive != null || !mutationMutex.tryLock()) return null
        try {
            catalog.restoreCache()
            val restored = try {
                withContext(Dispatchers.IO) {
                    installer.readVerifiedActive(org.tsuyomi.shared.sourcecontract.SourceId(sourceId))
                }
            } catch (_: ExtensionInstallException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } ?: return null
            activePackage = restored
            showInstalled(restored)
            return restored
        } finally {
            mutationMutex.unlock()
        }
    }

    /** Resolves a trusted installed archive without changing the foreground source selection. */
    internal suspend fun resolveInstalledSource(sourceId: String): VerifiedHxpPackage? {
        if (prepared != null || pendingLocalArchive != null || !mutationMutex.tryLock()) return null
        try {
            catalog.restoreCache()
            return try {
                withContext(Dispatchers.IO) {
                    installer.readVerifiedActive(org.tsuyomi.shared.sourcecontract.SourceId(sourceId))
                }
            } catch (_: ExtensionInstallException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }?.takeIf { OfficialRepositoryConfiguration.isTrusted(it, publisherKeys) }
        } finally {
            mutationMutex.unlock()
        }
    }

    internal fun dismissRepositoryApproval() {
        if (preparedRepositoryInstall != null) dismissApproval()
    }

    suspend fun installedRemoteLibraryRoots(): List<LibraryMirrorShortcut> = withContext(Dispatchers.IO) {
        try {
            repositoryClient?.cached()
        } catch (_: org.tsuyomi.source.extensionmanager.RepositoryCatalogException) {
            // Unauthenticated repository identities fail closed; local publishers remain independent.
        }
        store.installedSourceIds().mapNotNull { sourceId ->
            val installed = try {
                installer.readVerifiedActive(sourceId)
            } catch (_: ExtensionInstallException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } ?: return@mapNotNull null
            if (!installed.manifest.capabilities.remoteLibrary.policies.containsKey(RemoteOperation.READ)) {
                return@mapNotNull null
            }
            LibraryMirrorShortcut(
                sourceId = installed.manifest.sourceId.value,
                targetId = null,
                label = installed.manifest.displayName,
                count = 0,
                frozen = false,
            )
        }
    }

    suspend fun restoreInstalled() {
        if (installedLoaded) return
        mutationMutex.lock()
        try {
            if (!installedLoaded) loadInstalledPackages()
        } finally {
            mutationMutex.unlock()
        }
    }

    suspend fun refreshInstalled() {
        if (prepared != null || pendingLocalArchive != null || !mutationMutex.tryLock()) return
        try {
            loadInstalledPackages()
        } finally {
            mutationMutex.unlock()
        }
    }

    /** Availability is fenced before archive removal so background commits reject stale leases. */
    internal suspend fun uninstall(sourceId: String, onRemoved: suspend () -> Unit = {}): Boolean {
        if (prepared != null || pendingLocalArchive != null || !mutationMutex.tryLock()) return false
        try {
            val id = org.tsuyomi.shared.sourcecontract.SourceId(sourceId)
            val installed = installedPackages.singleOrNull { it.manifest.sourceId == id } ?: return false
            return withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    packageTrust.pinInstalled(installed, requireNotNull(publisherKeys.resolve(installed.manifest.publisherKeyId)))
                }
                val previous = libraryRepository.sourceAvailability(sourceId)
                markSourceUnavailable(sourceId)
                try {
                    withContext(Dispatchers.IO) {
                        check(store.remove(id)) { "Installed archive could not be removed" }
                    }
                } catch (error: Exception) {
                    if (previous != null) libraryRepository.setSourceAvailability(
                        sourceId, previous.verifiedVersion, previous.available, previous.generation + 2L,
                    )
                    throw error
                }
                installedPackages = installedPackages.filterNot { it.manifest.sourceId == id }
                if (selectedPackage?.manifest?.sourceId == id) selectedPackage = null
                installedLoaded = true
                catalog.onInstalledPackagesChanged()
                resetToInstalledOrEmpty()
                onRemoved()
                true
            }
        } catch (_: Exception) {
            resetToFailure(BrowseInstallFailure.INSTALL)
            return false
        } finally {
            mutationMutex.unlock()
        }
    }

    internal suspend fun refreshRepositoryCatalog(client: OfficialRepositoryClient? = repositoryClient): RepositoryCatalog? {
        if (client == null) return null
        if (prepared != null || pendingLocalArchive != null || !mutationMutex.tryLock()) return null
        try {
            return withContext(NonCancellable) {
                val accepted = withContext(Dispatchers.IO) { client.refresh() }
                loadInstalledPackages()
                accepted
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    internal suspend fun <T> withRepositoryMutation(block: suspend () -> T): T {
        check(prepared == null && pendingLocalArchive == null && mutationMutex.tryLock()) { "Installation is pending" }
        try {
            return withContext(NonCancellable) { block() }
        } finally {
            mutationMutex.unlock()
        }
    }

    suspend fun prepare(uri: Uri, resolver: ContentResolver) {
        prepareCandidate(uri.lastPathSegment?.takeLast(96)?.ifBlank { "extension.hxp" } ?: "extension.hxp", null) {
            val staged = copyToBoundedStaging(uri, resolver)
            try {
                installer.prepare(staged)
            } catch (error: HxpVerificationException) {
                if (error.error != org.tsuyomi.source.extensionmanager.HxpVerificationError.UNKNOWN_PUBLISHER) throw error
                val identity = packageTrust.inspectLocalArchive(staged, HxpArchiveVerifier(publisherKeys))
                pendingLocalArchive = staged
                throw LocalPublisherKeyNeeded(identity.keyId)
            } finally {
                if (pendingLocalArchive != staged) staged.delete()
            }
        }
    }

    internal fun rejectUnavailableRepositoryInstall(action: BrowseCatalogAction.Install) {
        if (!mutationPending) resetToFailure(BrowseInstallFailure.REPOSITORY, action)
    }

    internal suspend fun prepareRepository(
        action: BrowseCatalogAction.Install,
        client: OfficialRepositoryClient,
    ) {
        prepareCandidate(action.sourceId, action) { client.prepare(action.sourceId, installer) }
        if (prepared != null && preparedRepositoryInstall == action) preparedRepository = client
    }

    suspend fun providePublisherKey(rawBase64: String) {
        val archive = pendingLocalArchive ?: return
        val keyId = (state as? BrowseUiState.PublisherKeyRequired)?.keyId ?: return
        pendingLocalArchive = null
        prepareCandidate(archive.name, null) {
            try {
                val verified = packageTrust.verifyUserAddedArchive(archive, keyId, rawBase64, publisherKeys)
                preparedPublisher = verified.publisher
                preparedPublisherFromInput = true
                val ephemeral = object : org.tsuyomi.source.extensionmanager.PublisherKeyResolver {
                    override fun resolve(keyId: String) = verified.publisher.takeIf { it.keyId == keyId }
                    override fun isRevokedFingerprint(fingerprint: String) = false
                    override fun isRevokedPackage(packageSha256: String) = false
                }
                ExtensionInstaller(
                    HxpArchiveVerifier(org.tsuyomi.source.extensionmanager.CompositePublisherKeyResolver(listOf(publisherKeys, ephemeral))),
                    store, stagingDirectory, packageExecutionTrust = packageTrust,
                ).prepare(archive)
            } finally {
                archive.delete()
            }
        }
    }

    private class LocalPublisherKeyNeeded(val keyId: String) : Exception()

    private suspend fun prepareCandidate(
        name: String,
        repositoryInstall: BrowseCatalogAction.Install?,
        load: () -> PreparedExtensionInstall,
    ) {
        if (!mutationMutex.tryLock()) return
        try {
            clearPreparedState()
            state = BrowseUiState.Preparing(name)
            val result = withContext(Dispatchers.IO) { load() }
            if (preparedPublisher == null) preparedPublisher = publisherKeys.resolve(result.candidate.manifest.publisherKeyId)
            prepared = result
            preparedRepositoryInstall = repositoryInstall
            state = BrowseUiState.Approval(
                sourceName = result.candidate.manifest.displayName,
                sourceId = result.candidate.manifest.sourceId.value,
                version = result.candidate.manifest.version.original,
                publisherFingerprint = result.candidate.publisherFingerprint,
                capabilities = result.addedCapabilities,
                resourceLimitIncreases = result.resourceLimitIncreases.map { increase ->
                    BrowseResourceLimitIncrease(
                        limit = increase.limit.toBrowseResourceLimit(),
                        activeValue = increase.activeValue,
                        candidateValue = increase.candidateValue,
                    )
                },
                isDowngrade = result.isDowngrade,
                isLegacyMigration = result.isLegacyMigration,
                requiresNonOfficialConsent = preparedPublisher?.trust == org.tsuyomi.source.extensionmanager.PublisherTrust.USER_ADDED,
                packageSha256 = result.candidate.packageSha256,
            )
        } catch (cancelled: CancellationException) {
            clearPreparedState()
            resetToInstalledOrEmpty()
            throw cancelled
        } catch (needed: LocalPublisherKeyNeeded) {
            state = BrowseUiState.PublisherKeyRequired(needed.keyId)
        } catch (error: RepositoryCatalogException) {
            resetToFailure(repositoryFailure(error), repositoryInstall)
        } catch (_: HxpVerificationException) {
            resetToFailure(BrowseInstallFailure.VERIFICATION, repositoryInstall)
        } catch (error: ExtensionInstallException) {
            resetToFailure(extensionFailure(error), repositoryInstall)
        } catch (_: Exception) {
            resetToFailure(
                if (repositoryInstall != null) BrowseInstallFailure.REPOSITORY else BrowseInstallFailure.FILE_ACCESS,
                repositoryInstall,
            )
        } finally {
            mutationMutex.unlock()
        }
    }

    suspend fun approve(
        allowDowngrade: Boolean,
        allowLegacyMigration: Boolean = false,
        allowNonOfficial: Boolean = false,
        expectedPackageSha256: String? = null,
    ) {
        if (!mutationMutex.tryLock()) return
        try {
            val candidate = prepared ?: return
            val repositoryInstall = preparedRepositoryInstall
            if (expectedPackageSha256 != null && expectedPackageSha256 != candidate.candidate.packageSha256) {
                return resetToFailure(BrowseInstallFailure.EXPIRED_APPROVAL, repositoryInstall)
            }
            val publisher = preparedPublisher ?: return resetToFailure(BrowseInstallFailure.VERIFICATION, repositoryInstall)
            if (candidate.isDowngrade && !allowDowngrade || candidate.isLegacyMigration && !allowLegacyMigration) return
            if (publisher.trust == org.tsuyomi.source.extensionmanager.PublisherTrust.USER_ADDED && !allowNonOfficial) return
            val repository = preparedRepository
            if (repositoryInstall != null && (
                repository == null || !catalog.isCurrentInstallable(repositoryInstall, repository)
            )) {
                return resetToFailure(BrowseInstallFailure.REPOSITORY, repositoryInstall)
            }
            withContext(NonCancellable) {
                state = BrowseUiState.Preparing(candidate.candidate.manifest.displayName)
                withContext(Dispatchers.IO) {
                    preparedRepository?.validatePreparedRepositoryInstall(candidate)
                    packageTrust.approve(candidate, publisher, retainPublisherKey = preparedPublisherFromInput)
                    installer.activate(candidate, ExtensionInstallApproval.approve(candidate, allowDowngrade, allowLegacyMigration))
                }
                activePackage = candidate.candidate
                synchronizeVerifiedPackage(candidate.candidate, preserveWriteback = !candidate.isDowngrade)
                installedPackages = (installedPackages.filterNot {
                    it.manifest.sourceId == candidate.candidate.manifest.sourceId
                } + candidate.candidate).sortedBy { it.manifest.displayName }
                installedLoaded = true
                clearPreparedState()
                catalog.onInstalledPackagesChanged()
                showInstalled(candidate.candidate)
            }
        } catch (cancelled: CancellationException) {
            clearPreparedState()
            resetToInstalledOrEmpty()
            installedLoaded = false
            throw cancelled
        } catch (error: RepositoryCatalogException) {
            resetToFailure(repositoryFailure(error), preparedRepositoryInstall)
        } catch (error: ExtensionInstallException) {
            resetToFailure(extensionFailure(error), preparedRepositoryInstall)
        } catch (_: HxpVerificationException) {
            resetToFailure(BrowseInstallFailure.VERIFICATION, preparedRepositoryInstall)
        } catch (_: Exception) {
            resetToFailure(
                if (preparedRepositoryInstall != null) BrowseInstallFailure.REPOSITORY else BrowseInstallFailure.INSTALL,
                preparedRepositoryInstall,
            )
        } finally {
            mutationMutex.unlock()
        }
    }

    private suspend fun loadInstalledPackages() {
        catalog.restoreCache()
        val previousId = activePackage?.manifest?.sourceId
        val sourceIds = withContext(Dispatchers.IO) { store.installedSourceIds() }
        libraryRepository.markMissingSourcesUnavailable(sourceIds.map { it.value })
        val packages = mutableListOf<VerifiedHxpPackage>()
        var invalid = false
        for (sourceId in sourceIds) {
            val restored = try {
                withContext(Dispatchers.IO) { installer.readVerifiedActive(sourceId) }
            } catch (_: ExtensionInstallException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            if (restored == null) {
                invalid = true
                markSourceUnavailable(sourceId.value)
            } else {
                synchronizeVerifiedPackage(restored, preserveWriteback = true)
                packages += restored
            }
        }
        installedPackages = packages.sortedBy { it.manifest.displayName }
        activePackage = packages.firstOrNull { it.manifest.sourceId == previousId } ?: packages.firstOrNull()
        installedLoaded = true
        catalog.onInstalledPackagesChanged()
        if (invalid) resetToFailure(BrowseInstallFailure.VERIFICATION) else resetToInstalledOrEmpty()
    }

    fun dismissApproval() {
        if (mutationMutex.isLocked) return
        clearPreparedState()
        resetToInstalledOrEmpty()
    }

    fun dismissFailure() {
        if (mutationMutex.isLocked) return
        resetToInstalledOrEmpty()
    }
    suspend fun remotePolicy(): SourceRemotePolicy? {
        val packageInfo = activePackage ?: return null
        val sourceId = packageInfo.manifest.sourceId.value
        val policy = libraryRepository.sourceRemotePolicy(sourceId) ?: return null
        if (policy.addWritebackEnabled && !remoteAddCredentialReady()) {
            libraryRepository.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, false)
            return policy.copy(addWritebackEnabled = false)
        }
        return policy
    }

    fun remoteAddCredentialReady(): Boolean {
        val packageInfo = activePackage ?: return false
        val addPolicy = packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.ADD] ?: return false
        return runCatching {
            credentialStore.getSnapshot(SourceCredentialPartition(packageInfo.manifest.sourceId.value, addPolicy.origin)) != null
        }.getOrDefault(false)
    }

    suspend fun setRemoteAddWritebackEnabled(enabled: Boolean): Boolean {
        val packageInfo = activePackage ?: return false
        val supportsAdd = packageInfo.manifest.capabilities.remoteLibrary.policies.containsKey(RemoteOperation.ADD)
        if (enabled && (!supportsAdd || !remoteAddCredentialReady())) return false
        return libraryRepository.setAddWritebackEnabled(
            sourceId = packageInfo.manifest.sourceId.value,
            capabilityFingerprint = installer.remoteCapabilitySetFingerprint(packageInfo),
            enabled = enabled,
        )
    }

    private fun resetToFailure(
        reason: BrowseInstallFailure,
        repositoryInstall: BrowseCatalogAction.Install? = null,
    ) {
        clearPreparedState()
        state = BrowseUiState.Failure(reason, repositoryInstall)
    }

    private fun clearPreparedState() {
        prepared = null
        preparedRepositoryInstall = null
        preparedRepository = null
        preparedPublisher = null
        preparedPublisherFromInput = false
        pendingLocalArchive?.delete()
        pendingLocalArchive = null
    }

    private fun repositoryFailure(error: RepositoryCatalogException): BrowseInstallFailure = when (error.error) {
        RepositoryCatalogError.STORAGE_UNAVAILABLE -> BrowseInstallFailure.STORAGE
        RepositoryCatalogError.PACKAGE_DOWNLOAD_INVALID -> fetchFailure(error.cause as? RepositoryFetchException)
            ?: BrowseInstallFailure.VERIFICATION
        RepositoryCatalogError.CATALOG_UNAVAILABLE -> fetchFailure(error.cause as? RepositoryFetchException)
            ?: BrowseInstallFailure.REPOSITORY
        RepositoryCatalogError.INVALID_CATALOG,
        RepositoryCatalogError.INVALID_SIGNATURE,
        RepositoryCatalogError.ROLLBACK_REJECTED,
        RepositoryCatalogError.EQUIVOCATION_REJECTED,
        -> BrowseInstallFailure.VERIFICATION
        RepositoryCatalogError.CATALOG_EXPIRED,
        RepositoryCatalogError.PACKAGE_NOT_FOUND,
        RepositoryCatalogError.PUBLISHER_REVOKED,
        RepositoryCatalogError.PACKAGE_REVOKED,
        RepositoryCatalogError.PACKAGE_BINDING_MISMATCH,
        RepositoryCatalogError.PREPARED_INSTALL_INVALID,
        -> BrowseInstallFailure.REPOSITORY
    }

    private fun fetchFailure(error: RepositoryFetchException?): BrowseInstallFailure? = when (error?.error) {
        RepositoryFetchError.NETWORK,
        RepositoryFetchError.TIMEOUT,
        RepositoryFetchError.HTTP_STATUS,
        RepositoryFetchError.CANCELLED,
        -> BrowseInstallFailure.DOWNLOAD
        RepositoryFetchError.INVALID_REDIRECT,
        RepositoryFetchError.REDIRECT_LIMIT,
        RepositoryFetchError.RESPONSE_TOO_LARGE,
        -> BrowseInstallFailure.VERIFICATION
        null -> null
    }

    private fun extensionFailure(error: ExtensionInstallException): BrowseInstallFailure = when (error.error) {
        ExtensionInstallError.STORAGE_UNAVAILABLE -> BrowseInstallFailure.STORAGE
        ExtensionInstallError.REPOSITORY_BINDING_MISMATCH,
        ExtensionInstallError.REPOSITORY_ACTIVE_UNVERIFIABLE,
        ExtensionInstallError.CANDIDATE_RECHECK_FAILED,
        ExtensionInstallError.INSTALLED_PACKAGE_INVALID,
        -> BrowseInstallFailure.VERIFICATION
        else -> BrowseInstallFailure.INSTALL
    }

    private fun resetToInstalledOrEmpty() {
        activePackage?.let(::showInstalled) ?: run { state = BrowseUiState.Empty }
    }

    private fun showInstalled(packageInfo: VerifiedHxpPackage) {
        state = BrowseUiState.Installed(
            sourceName = packageInfo.manifest.displayName,
            version = packageInfo.manifest.version.original,
        )
    }

    private suspend fun markSourceUnavailable(sourceId: String) {
        val current = libraryRepository.sourceAvailability(sourceId)
        libraryRepository.setSourceAvailability(
            sourceId = sourceId,
            version = current?.verifiedVersion,
            available = false,
            generation = (current?.generation ?: 0L) + 1L,
        )
    }

    private suspend fun synchronizeVerifiedPackage(packageInfo: VerifiedHxpPackage, preserveWriteback: Boolean) {
        val sourceId = packageInfo.manifest.sourceId.value
        val currentAvailability = libraryRepository.sourceAvailability(sourceId)
        val generation = (currentAvailability?.generation ?: 0L) + 1L
        val capabilityFingerprint = installer.remoteCapabilitySetFingerprint(packageInfo)
        val currentPolicy = libraryRepository.sourceRemotePolicy(sourceId)
        val preservesPolicy = preserveWriteback &&
            currentPolicy?.trustedPublisherFingerprint == packageInfo.publisherFingerprint &&
            currentPolicy.capabilitySetFingerprint == capabilityFingerprint
        val readPolicy = packageInfo.manifest.capabilities.remoteLibrary.policies[RemoteOperation.READ]
        libraryRepository.saveSourceRemotePolicy(
            SourceRemotePolicy(
                sourceId = sourceId,
                trustedPublisherFingerprint = packageInfo.publisherFingerprint,
                capabilitySetFingerprint = capabilityFingerprint,
                approvedOrigin = readPolicy?.origin?.canonical.orEmpty(),
                addWritebackEnabled = preservesPolicy && currentPolicy.addWritebackEnabled,
                firstImportPromptDismissed = preservesPolicy && currentPolicy.firstImportPromptDismissed,
                removeWritebackEnabled = preservesPolicy && currentPolicy.removeWritebackEnabled,
                moveWritebackEnabled = preservesPolicy && currentPolicy.moveWritebackEnabled,
            ),
        )
        libraryRepository.setSourceAvailability(sourceId, packageInfo.manifest.version.original, true, generation)
    }
    private fun copyToBoundedStaging(uri: Uri, resolver: ContentResolver): File {
        require(stagingDirectory.isDirectory || stagingDirectory.mkdirs()) { "Cannot create HXP staging directory" }
        val target = File.createTempFile("candidate-", ".hxp", stagingDirectory)
        try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_ARCHIVE_BYTES) throw IllegalArgumentException("Archive exceeds limit")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IllegalArgumentException("Unable to open document")
            return target
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private companion object {
        const val MAX_ARCHIVE_BYTES = 16L * 1024 * 1024
    }
}

private fun ResourceLimit.toBrowseResourceLimit(): BrowseResourceLimit = when (this) {
    ResourceLimit.MAX_EXECUTION_WALL_TIME_MS -> BrowseResourceLimit.MAX_EXECUTION_WALL_TIME_MS
    ResourceLimit.MAX_MEMORY_BYTES -> BrowseResourceLimit.MAX_MEMORY_BYTES
    ResourceLimit.STORAGE_QUOTA_BYTES -> BrowseResourceLimit.STORAGE_QUOTA_BYTES
    ResourceLimit.NETWORK_CONCURRENT_REQUESTS -> BrowseResourceLimit.NETWORK_CONCURRENT_REQUESTS
    ResourceLimit.NETWORK_REQUEST_TIMEOUT_MS -> BrowseResourceLimit.NETWORK_REQUEST_TIMEOUT_MS
    ResourceLimit.NETWORK_RESPONSE_BYTES -> BrowseResourceLimit.NETWORK_RESPONSE_BYTES
}
