/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.core.library.UpdateCoordinator
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.shared.librarydomain.UpdateCandidate
import org.tsuyomi.shared.librarydomain.UpdateProbe
import org.tsuyomi.shared.librarydomain.UpdateProbeResult
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.shared.sourcecontract.SourceId
import org.tsuyomi.shared.sourcecontract.SourceUpdateOutcome
import org.tsuyomi.shared.sourcecontract.SourceUpdateProbeResult
import org.tsuyomi.source.extensionmanager.ExtensionInstallException
import org.tsuyomi.source.extensionmanager.ExtensionInstaller
import org.tsuyomi.source.extensionmanager.HxpArchiveVerifier
import org.tsuyomi.source.extensionmanager.InstalledExtensionStore
import org.tsuyomi.source.extensionmanager.SourceExtensionClient
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

/**
 * Background-safe source owner. It never borrows an Activity-owned source session: every run reads
 * the active signed archive, while the probe captures a current lease that the coordinator rechecks
 * before durable commits.
 */
internal class UpdateSourceRuntime(
    context: Context,
    private val libraryRepository: RoomLibraryRepository,
) {
    private val applicationContext = context.applicationContext
    private val application = applicationContext as TsuyomiApplication
    private val repositoryClient = application.officialRepository
    private val installer = ExtensionInstaller(
        verifier = HxpArchiveVerifier(OfficialRepositoryConfiguration.publisherKeys(repositoryClient, application)),
        store = InstalledExtensionStore(
            QuotaFileStore(
                roots = StorageRoots.from(applicationContext),
                root = StorageRoot.NO_BACKUP,
                namespace = "extensions",
                quota = StorageQuota(maxBytes = 64L * 1024 * 1024, maxEntries = 16),
            ),
        ),
        stagingDirectory = File(applicationContext.cacheDir, "hxp-update-staging"),
        packageExecutionTrust = application.packageTrust,
    )

    /** Includes only local library books and books in enabled (not frozen) mirror bindings. */
    suspend fun candidates(): List<UpdateCandidate> {
        val scoped = LinkedHashMap<BookIdentity.Remote, String>()
        libraryRepository.libraryEntries().forEach { entry ->
            (entry.book.identity as? BookIdentity.Remote)?.let { scoped.putIfAbsent(it, entry.book.title) }
        }
        for (binding in libraryRepository.remoteMirrorBindings()) {
            if (binding.frozen) continue
            libraryRepository.remoteMirrorSnapshot(binding.sourceId)?.books?.forEach { mirrorBook ->
                (mirrorBook.book.identity as? BookIdentity.Remote)?.let { scoped.putIfAbsent(it, mirrorBook.book.title) }
            }
        }

        val sourceStatuses = LinkedHashMap<String, UpdateSourceStatus>()
        for (sourceId in scoped.keys.asSequence().map(BookIdentity.Remote::sourceId).distinct()) {
            sourceStatuses[sourceId] = sourceStatus(sourceId)
        }
        return scoped.map { (identity, title) ->
            when (val status = requireNotNull(sourceStatuses[identity.sourceId])) {
                is UpdateSourceStatus.Ready -> UpdateCandidate(identity, title)
                is UpdateSourceStatus.Unavailable -> UpdateCandidate(
                    identity = identity,
                    title = title,
                    eligible = false,
                    reason = status.reason,
                )
            }
        }
    }

    suspend fun currentSource(sourceId: String): UpdateSource? = when (val status = sourceStatus(sourceId)) {
        is UpdateSourceStatus.Ready -> status.source
        is UpdateSourceStatus.Unavailable -> null
    }


    internal suspend fun sourceStatus(sourceId: String): UpdateSourceStatus {
        val packageInfo = readVerifiedPackage(sourceId) ?: return UpdateSourceStatus.Unavailable("source-not-installed")
        val availability = libraryRepository.sourceAvailability(sourceId)
            ?: return UpdateSourceStatus.Unavailable("source-unavailable")
        if (!availability.available) return UpdateSourceStatus.Unavailable("source-unavailable")
        if (availability.verifiedVersion != packageInfo.manifest.version.original) {
            return UpdateSourceStatus.Unavailable("source-unverified")
        }
        val credentialRevision = try {
            SourceGatewayFactory.mediaCredentialRevision(applicationContext, packageInfo)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return UpdateSourceStatus.Unavailable("source-credentials-unavailable")
        }
        return UpdateSourceStatus.Ready(
            UpdateSource(
                packageInfo = packageInfo,
                lease = UpdateSourceLease(
                    sourceId = sourceId,
                    sourceVersion = packageInfo.manifest.version.original,
                    packageSha256 = packageInfo.packageSha256,
                    sourceGeneration = availability.generation,
                    credentialRevision = credentialRevision,
                ),
            ),
        )
    }

    private suspend fun readVerifiedPackage(sourceId: String): VerifiedHxpPackage? = withContext(Dispatchers.IO) {
        try {
            repositoryClient?.cached()
        } catch (_: org.tsuyomi.source.extensionmanager.RepositoryCatalogException) {
            // Unknown repository publishers remain unavailable without disabling independent local keys.
        }
        try {
            installer.readVerifiedActive(SourceId(sourceId))
        } catch (_: ExtensionInstallException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** Runtime evidence that must remain unchanged through [UpdateCoordinator]'s commit boundaries. */
internal data class UpdateSourceLease(
    val sourceId: String,
    val sourceVersion: String,
    val packageSha256: String,
    val sourceGeneration: Long,
    val credentialRevision: String,
)

internal data class UpdateSource(
    val packageInfo: VerifiedHxpPackage,
    val lease: UpdateSourceLease,
)

internal sealed interface UpdateSourceStatus {
    data class Ready(val source: UpdateSource) : UpdateSourceStatus
    data class Unavailable(val reason: String) : UpdateSourceStatus
}

/** Turns one signed source adapter check into storage-admissible update evidence. */
internal class UpdateSourceProbe(
    private val context: Context,
    private val runtime: UpdateSourceRuntime,
) : UpdateProbe {
    private val applicationContext = context.applicationContext
    private val checkedLease = AtomicReference<Pair<BookIdentity.Remote, UpdateSourceLease>?>(null)

    override suspend fun check(candidate: UpdateCandidate, previousAnchor: String?): UpdateProbeResult {
        checkedLease.set(null)
        val source = when (val status = runtime.sourceStatus(candidate.identity.sourceId)) {
            is UpdateSourceStatus.Ready -> status.source
            is UpdateSourceStatus.Unavailable -> return UpdateProbeResult.Unavailable(candidate.identity, status.reason)
        }
        checkedLease.set(candidate.identity to source.lease)
        return try {
            val (native, verifiedGet) = Phase2SourceGateway.createSession(
                applicationContext,
                source.packageInfo,
                DirectActionTokenRegistry(),
            )
            SourceExtensionClient.open(source.packageInfo, native, verifiedGet).use { client ->
                if (!client.supportsUpdateChecks) {
                    unavailable(candidate, previousAnchor, source.lease, "updates-not-supported")
                } else {
                    client.checkUpdates(candidate.identity.remoteBookId, previousAnchor).let { result ->
                        if (
                            result.identity != candidate.identity ||
                            result.sourceVersion != source.lease.sourceVersion ||
                            result.packageSha256 != source.lease.packageSha256
                        ) {
                            unavailable(candidate, previousAnchor, source.lease, "source-evidence-mismatch")
                        } else {
                            UpdateProbeResult.Checked(result)
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SourceException) {
            val outcome = when (failure.code) {
                SourceErrorCode.NETWORK_TIMEOUT,
                SourceErrorCode.NETWORK_OFFLINE,
                SourceErrorCode.SESSION_REQUIRED,
                SourceErrorCode.VERIFICATION_REQUIRED,
                SourceErrorCode.EXTENSION_CANCELLED,
                -> SourceUpdateOutcome.UNAVAILABLE
                else -> SourceUpdateOutcome.FAILED
            }
            UpdateProbeResult.Checked(terminal(candidate, previousAnchor, source.lease, outcome, failure.code.reason()))
        } catch (_: Throwable) {
            UpdateProbeResult.Checked(
                terminal(candidate, previousAnchor, source.lease, SourceUpdateOutcome.FAILED, "source-check-failed"),
            )
        }
    }


    override suspend fun isStillValid(candidate: UpdateCandidate): Boolean {
        val (identity, checked) = checkedLease.get() ?: return false
        return identity == candidate.identity && runtime.currentSource(identity.sourceId)?.lease == checked
    }

    private fun unavailable(
        candidate: UpdateCandidate,
        previousAnchor: String?,
        lease: UpdateSourceLease,
        reason: String,
    ): UpdateProbeResult.Checked = UpdateProbeResult.Checked(
        terminal(candidate, previousAnchor, lease, SourceUpdateOutcome.UNAVAILABLE, reason),
    )

    private fun terminal(
        candidate: UpdateCandidate,
        previousAnchor: String?,
        lease: UpdateSourceLease,
        outcome: SourceUpdateOutcome,
        reason: String,
    ) = SourceUpdateProbeResult(
        identity = candidate.identity,
        sourceVersion = lease.sourceVersion,
        packageSha256 = lease.packageSha256,
        checkedAt = System.currentTimeMillis(),
        outcome = outcome,
        previousAnchor = previousAnchor,
        anchor = null,
        chapters = emptyList(),
        newChapterIds = emptyList(),
        lastUpdatedDate = null,
        reason = reason,
    )

    private fun SourceErrorCode.reason(): String = "source-${name.lowercase().replace('_', '-')}"
}
