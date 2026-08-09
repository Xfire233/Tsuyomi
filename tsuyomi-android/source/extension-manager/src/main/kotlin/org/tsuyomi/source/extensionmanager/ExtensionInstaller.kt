/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.tsuyomi.shared.sourcecontract.SourceId

class ExtensionInstaller(
    private val verifier: HxpArchiveVerifier,
    private val store: InstalledExtensionStore,
    private val stagingDirectory: File,
) {
    init {
        require(stagingDirectory.isDirectory || stagingDirectory.mkdirs()) { "Cannot create HXP staging directory" }
    }

    fun prepare(candidateFile: File): PreparedExtensionInstall {
        val candidate = verifier.verify(candidateFile)
        val active = readVerifiedActive(candidate.manifest.sourceId)
        if (active != null) {
            if (candidate.manifest.version == active.manifest.version) {
                throw ExtensionInstallException(ExtensionInstallError.REPLAY_REJECTED)
            }
            if (candidate.manifest.publisherKeyId != active.manifest.publisherKeyId) {
                throw ExtensionInstallException(ExtensionInstallError.KEY_ROTATION_NOT_AUTHORIZED)
            }
        }
        val addedCapabilities = addedCapabilities(candidate.manifest, active?.manifest)
        val resourceLimitIncreases = resourceLimitIncreases(candidate.manifest, active?.manifest)
        return PreparedExtensionInstall(
            candidate = candidate,
            active = active,
            addedCapabilities = addedCapabilities.sorted(),
            resourceLimitIncreases = resourceLimitIncreases,
            capabilityGrantFingerprint = capabilityGrantFingerprint(candidate, addedCapabilities, resourceLimitIncreases),
            isDowngrade = active != null && candidate.manifest.version < active.manifest.version,
        )
    }

    fun activate(prepared: PreparedExtensionInstall, approval: ExtensionInstallApproval) {
        if (approval.packageSha256 != prepared.candidate.packageSha256 ||
            approval.publisherFingerprint != prepared.candidate.publisherFingerprint ||
            approval.capabilityGrantFingerprint != prepared.capabilityGrantFingerprint
        ) {
            throw ExtensionInstallException(ExtensionInstallError.APPROVAL_MISMATCH)
        }
        if (prepared.isDowngrade && !approval.allowLocalDowngrade) {
            throw ExtensionInstallException(ExtensionInstallError.DOWNGRADE_REQUIRES_CONFIRMATION)
        }
        store.writeActive(prepared.candidate)
    }

    fun readVerifiedActive(sourceId: SourceId): VerifiedHxpPackage? {
        val bytes = store.readActive(sourceId) ?: return null
        val temporary = File(stagingDirectory, "${sourceId.value}-${UUID.randomUUID()}.hxp")
        return try {
            temporary.writeBytes(bytes)
            verifier.verify(temporary)
        } catch (error: HxpVerificationException) {
            throw ExtensionInstallException(ExtensionInstallError.INSTALLED_PACKAGE_INVALID, error)
        } finally {
            temporary.delete()
        }
    }

    private fun addedCapabilities(candidate: HxpManifest, active: HxpManifest?): Set<String> = buildSet {
        val activeCapabilities = active?.capabilities
        candidate.capabilities.network.origins
            .filterNot { it in activeCapabilities?.network?.origins.orEmpty() }
            .forEach { add("network:${it.canonical}") }
        if (candidate.capabilities.cookies.sourceScoped && activeCapabilities?.cookies?.sourceScoped != true) {
            add("cookies:sourceScoped")
        }
        candidate.capabilities.cookies.origins
            .filterNot { it in activeCapabilities?.cookies?.origins.orEmpty() }
            .forEach { add("cookies-origin:${it.canonical}") }
        if (candidate.capabilities.webLogin.enabled && activeCapabilities?.webLogin?.enabled != true) add("web-login")
        candidate.capabilities.webLogin.origins
            .filterNot { it in activeCapabilities?.webLogin?.origins.orEmpty() }
            .forEach { add("web-login-origin:${it.canonical}") }
        if (candidate.capabilities.remoteLibrary.read && activeCapabilities?.remoteLibrary?.read != true) {
            add("remote-library:read")
        }
        candidate.capabilities.remoteLibrary.writeOperations
            .filterNot { it in activeCapabilities?.remoteLibrary?.writeOperations.orEmpty() }
            .forEach { add("remote-library:write:$it") }
    }

    private fun resourceLimitIncreases(
        candidate: HxpManifest,
        active: HxpManifest?,
    ): List<ResourceLimitIncrease> {
        val activeCapabilities = active?.capabilities
        val activeResourceLimits = active?.resourceLimits
        return listOf(
            ResourceLimitIncrease(
                ResourceLimit.MAX_EXECUTION_WALL_TIME_MS,
                activeResourceLimits?.maxExecutionWallTimeMs?.toLong() ?: 0L,
                candidate.resourceLimits.maxExecutionWallTimeMs.toLong(),
            ),
            ResourceLimitIncrease(
                ResourceLimit.MAX_MEMORY_BYTES,
                activeResourceLimits?.maxMemoryBytes?.toLong() ?: 0L,
                candidate.resourceLimits.maxMemoryBytes.toLong(),
            ),
            ResourceLimitIncrease(
                ResourceLimit.STORAGE_QUOTA_BYTES,
                activeCapabilities?.storageQuotaBytes?.toLong() ?: 0L,
                candidate.capabilities.storageQuotaBytes.toLong(),
            ),
            ResourceLimitIncrease(
                ResourceLimit.NETWORK_CONCURRENT_REQUESTS,
                activeCapabilities?.network?.maxConcurrentRequests?.toLong() ?: 0L,
                candidate.capabilities.network.maxConcurrentRequests.toLong(),
            ),
            ResourceLimitIncrease(
                ResourceLimit.NETWORK_REQUEST_TIMEOUT_MS,
                activeCapabilities?.network?.requestTimeoutMs?.toLong() ?: 0L,
                candidate.capabilities.network.requestTimeoutMs.toLong(),
            ),
            ResourceLimitIncrease(
                ResourceLimit.NETWORK_RESPONSE_BYTES,
                activeCapabilities?.network?.maxResponseBytes?.toLong() ?: 0L,
                candidate.capabilities.network.maxResponseBytes.toLong(),
            ),
        ).filter { it.candidateValue > it.activeValue }
    }

    private fun capabilityGrantFingerprint(
        candidate: VerifiedHxpPackage,
        addedCapabilities: Set<String>,
        resourceLimitIncreases: List<ResourceLimitIncrease>,
    ): String = sha256(
        buildString {
            append(candidate.packageSha256)
            append('\u0000')
            addedCapabilities.sorted().forEach { capability ->
                append("capability:")
                append(capability)
                append('\n')
            }
            resourceLimitIncreases.forEach { increase ->
                append("resource:")
                append(increase.limit.name)
                append(':')
                append(increase.activeValue)
                append(':')
                append(increase.candidateValue)
                append('\n')
            }
        }.toByteArray(StandardCharsets.UTF_8),
    )
}

data class PreparedExtensionInstall(
    val candidate: VerifiedHxpPackage,
    val active: VerifiedHxpPackage?,
    val addedCapabilities: List<String>,
    val resourceLimitIncreases: List<ResourceLimitIncrease>,
    val capabilityGrantFingerprint: String,
    val isDowngrade: Boolean,
)

enum class ResourceLimit {
    MAX_EXECUTION_WALL_TIME_MS,
    MAX_MEMORY_BYTES,
    STORAGE_QUOTA_BYTES,
    NETWORK_CONCURRENT_REQUESTS,
    NETWORK_REQUEST_TIMEOUT_MS,
    NETWORK_RESPONSE_BYTES,
}

data class ResourceLimitIncrease(
    val limit: ResourceLimit,
    val activeValue: Long,
    val candidateValue: Long,
)

data class ExtensionInstallApproval(
    val packageSha256: String,
    val publisherFingerprint: String,
    val capabilityGrantFingerprint: String,
    val allowLocalDowngrade: Boolean,
) {
    companion object {
        fun approve(prepared: PreparedExtensionInstall, allowLocalDowngrade: Boolean = false) =
            ExtensionInstallApproval(
                packageSha256 = prepared.candidate.packageSha256,
                publisherFingerprint = prepared.candidate.publisherFingerprint,
                capabilityGrantFingerprint = prepared.capabilityGrantFingerprint,
                allowLocalDowngrade = allowLocalDowngrade,
            )
    }
}
