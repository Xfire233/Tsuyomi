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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.feature.browse.BrowseInstallFailure
import org.tsuyomi.feature.browse.BrowseResourceLimit
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
import org.tsuyomi.source.extensionmanager.ResourceLimit

/** App-owned coordinator: the picker grants transient read access; only verified archives become durable. */
class SourceInstallController(context: Context) {
    private val stagingDirectory = File(context.cacheDir, "hxp-staging")
    private val store = InstalledExtensionStore(
        QuotaFileStore(
            roots = StorageRoots.from(context),
            root = StorageRoot.NO_BACKUP,
            namespace = "extensions",
            quota = StorageQuota(maxBytes = 64L * 1024 * 1024, maxEntries = 16),
        ),
    )
    private val installer = ExtensionInstaller(
        verifier = HxpArchiveVerifier(Gate2LocalTrust.resolver()),
        store = store,
        stagingDirectory = stagingDirectory,
    )

    var activePackage: VerifiedHxpPackage? by mutableStateOf(null)
        private set
    private var prepared: PreparedExtensionInstall? = null

    var state: BrowseUiState by mutableStateOf(BrowseUiState.Empty)
        private set


    suspend fun restoreInstalled() {
        if (activePackage != null) return
        try {
            val restored = withContext(Dispatchers.IO) {
                store.installedSourceIds().firstNotNullOfOrNull(installer::readVerifiedActive)
            } ?: return
            activePackage = restored
            state = BrowseUiState.Installed(
                sourceName = restored.manifest.displayName,
                version = restored.manifest.version.original,
            )
        } catch (_: ExtensionInstallException) {
            resetToFailure(BrowseInstallFailure.VERIFICATION)
        }
    }
    suspend fun prepare(uri: Uri, resolver: ContentResolver) {
        val displayName = uri.lastPathSegment?.takeLast(96)?.ifBlank { "extension.hxp" } ?: "extension.hxp"
        state = BrowseUiState.Preparing(displayName)
        try {
            val staged = withContext(Dispatchers.IO) { copyToBoundedStaging(uri, resolver) }
            val result = withContext(Dispatchers.IO) { installer.prepare(staged) }
            prepared = result
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
            )
        } catch (_: HxpVerificationException) {
            resetToFailure(BrowseInstallFailure.VERIFICATION)
        } catch (_: ExtensionInstallException) {
            resetToFailure(BrowseInstallFailure.INSTALL)
        } catch (_: Exception) {
            resetToFailure(BrowseInstallFailure.FILE_ACCESS)
        }
    }

    suspend fun approve(allowDowngrade: Boolean) {
        val candidate = prepared ?: return resetToFailure(BrowseInstallFailure.EXPIRED_APPROVAL)
        state = BrowseUiState.Preparing(candidate.candidate.manifest.displayName)
        try {
            withContext(Dispatchers.IO) {
                installer.activate(candidate, ExtensionInstallApproval.approve(candidate, allowDowngrade))
            }
            activePackage = candidate.candidate
            prepared = null
            state = BrowseUiState.Installed(
                sourceName = candidate.candidate.manifest.displayName,
                version = candidate.candidate.manifest.version.original,
            )
        } catch (_: ExtensionInstallException) {
            resetToFailure(BrowseInstallFailure.INSTALL)
        }
    }

    fun dismissApproval() {
        prepared = null
        state = BrowseUiState.Empty
    }

    fun dismissFailure() {
        state = BrowseUiState.Empty
    }

    private fun resetToFailure(reason: BrowseInstallFailure) {
        prepared = null
        state = BrowseUiState.Failure(reason)
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
