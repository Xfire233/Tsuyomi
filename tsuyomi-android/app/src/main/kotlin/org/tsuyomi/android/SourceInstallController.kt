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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.database.SourceRemotePolicy
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.feature.browse.BrowseInstallFailure
import org.tsuyomi.feature.browse.BrowseResourceLimit
import org.tsuyomi.feature.library.LibraryMirrorShortcut
import org.tsuyomi.feature.browse.BrowseResourceLimitIncrease
import org.tsuyomi.feature.browse.BrowseUiState
import org.tsuyomi.source.extensionmanager.ExtensionInstallApproval
import org.tsuyomi.source.extensionmanager.ExtensionInstallException
import org.tsuyomi.source.extensionmanager.ExtensionInstaller
import org.tsuyomi.source.extensionmanager.HxpArchiveVerifier
import org.tsuyomi.source.extensionmanager.HxpVerificationException
import org.tsuyomi.source.extensionmanager.InstalledExtensionStore
import org.tsuyomi.source.extensionmanager.PreparedExtensionInstall
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage
import org.tsuyomi.source.extensionmanager.RemoteOperation
import org.tsuyomi.source.extensionmanager.ResourceLimit
import org.tsuyomi.source.extensionmanager.OfficialRepositoryClient
import org.tsuyomi.source.extensionmanager.RepositoryCatalog

/** App-owned coordinator: the picker grants transient read access; only verified archives become durable. */
class SourceInstallController(
    private val context: Context,
    private val libraryRepository: RoomLibraryRepository,
    private val repositoryClient: OfficialRepositoryClient? = (context.applicationContext as TsuyomiApplication).officialRepository,
) {
    private val credentialStore = VerifiedBrowserSessionStore(context)
    private val stagingDirectory = File(context.cacheDir, "hxp-staging")
    private val mutationMutex = (context.applicationContext as TsuyomiApplication).extensionMutationMutex
    private val publisherKeys = OfficialRepositoryConfiguration.publisherKeys(repositoryClient)
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
    )

    private var selectedPackage: VerifiedHxpPackage? by mutableStateOf(null)
    var activePackage: VerifiedHxpPackage?
        get() = selectedPackage?.takeIf { OfficialRepositoryConfiguration.isTrusted(it, publisherKeys) }
        private set(value) { selectedPackage = value }
    private var prepared: PreparedExtensionInstall? = null
    private var preparedFromRepository = false
    private var installedLoaded = false
    var installedPackages: List<VerifiedHxpPackage> by mutableStateOf(emptyList())
        private set
    internal val catalog = SourceCatalogController(context, repositoryClient, this)
    internal val mutationPending: Boolean
        get() = mutationMutex.isLocked || prepared != null

    var state: BrowseUiState by mutableStateOf(BrowseUiState.Empty)
        private set


    /**
     * Selects one exact installed archive without installing, re-verifying its metadata in Room,
     * or borrowing a different source's foreground session.
     */
    suspend fun activateInstalledSource(sourceId: String): VerifiedHxpPackage? {
        if (prepared != null || !mutationMutex.tryLock()) return null
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

    internal fun dismissRepositoryApproval() {
        if (preparedFromRepository) dismissApproval()
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
        if (prepared != null || !mutationMutex.tryLock()) return
        try {
            loadInstalledPackages()
        } finally {
            mutationMutex.unlock()
        }
    }

    internal suspend fun refreshRepositoryCatalog(): RepositoryCatalog? {
        val client = repositoryClient ?: return null
        if (prepared != null || !mutationMutex.tryLock()) return null
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

    suspend fun prepare(uri: Uri, resolver: ContentResolver) {
        prepareCandidate(uri.lastPathSegment?.takeLast(96)?.ifBlank { "extension.hxp" } ?: "extension.hxp", false) {
            val staged = copyToBoundedStaging(uri, resolver)
            try {
                installer.prepare(staged)
            } finally {
                staged.delete()
            }
        }
    }

    internal suspend fun prepareRepository(sourceId: String) {
        val client = repositoryClient ?: return
        prepareCandidate(sourceId, true) { client.prepare(sourceId, installer) }
    }

    private suspend fun prepareCandidate(name: String, repository: Boolean, load: () -> PreparedExtensionInstall) {
        if (!mutationMutex.tryLock()) return
        try {
            prepared = null
            preparedFromRepository = false
            state = BrowseUiState.Preparing(name)
            val result = withContext(Dispatchers.IO) { load() }
            prepared = result
            preparedFromRepository = repository
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
            )
        } catch (cancelled: CancellationException) {
            prepared = null
            resetToInstalledOrEmpty()
            throw cancelled
        } catch (_: HxpVerificationException) {
            resetToFailure(BrowseInstallFailure.VERIFICATION)
        } catch (_: ExtensionInstallException) {
            resetToFailure(BrowseInstallFailure.INSTALL)
        } catch (_: Exception) {
            resetToFailure(if (repository) BrowseInstallFailure.VERIFICATION else BrowseInstallFailure.FILE_ACCESS)
        } finally {
            mutationMutex.unlock()
        }
    }

    suspend fun approve(allowDowngrade: Boolean, allowLegacyMigration: Boolean = false) {
        if (!mutationMutex.tryLock()) return
        try {
            val candidate = prepared ?: return resetToFailure(BrowseInstallFailure.EXPIRED_APPROVAL)
            withContext(NonCancellable) {
                state = BrowseUiState.Preparing(candidate.candidate.manifest.displayName)
                withContext(Dispatchers.IO) {
                    if (preparedFromRepository) requireNotNull(repositoryClient).validatePreparedRepositoryInstall(candidate)
                    installer.activate(candidate, ExtensionInstallApproval.approve(candidate, allowDowngrade, allowLegacyMigration))
                }
                activePackage = candidate.candidate
                synchronizeVerifiedPackage(candidate.candidate, preserveWriteback = !candidate.isDowngrade)
                installedPackages = (installedPackages.filterNot {
                    it.manifest.sourceId == candidate.candidate.manifest.sourceId
                } + candidate.candidate).sortedBy { it.manifest.displayName }
                installedLoaded = true
                prepared = null
                preparedFromRepository = false
                catalog.onInstalledPackagesChanged()
                showInstalled(candidate.candidate)
            }
        } catch (cancelled: CancellationException) {
            prepared = null
            resetToInstalledOrEmpty()
            installedLoaded = false
            throw cancelled
        } catch (_: Exception) {
            resetToFailure(BrowseInstallFailure.INSTALL)
        } finally {
            mutationMutex.unlock()
        }
    }

    private suspend fun loadInstalledPackages() {
        catalog.restoreCache()
        val previousId = activePackage?.manifest?.sourceId
        val sourceIds = withContext(Dispatchers.IO) { store.installedSourceIds() }
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
        prepared = null
        preparedFromRepository = false
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

    private fun resetToFailure(reason: BrowseInstallFailure) {
        prepared = null
        preparedFromRepository = false
        state = BrowseUiState.Failure(reason)
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
