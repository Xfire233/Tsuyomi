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
            remoteCapabilitySetFingerprint = remoteCapabilitySetFingerprint(candidate.manifest, candidate.publisherFingerprint),
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

    fun remoteCapabilitySetFingerprint(packageInfo: VerifiedHxpPackage): String =
        remoteCapabilitySetFingerprint(packageInfo.manifest, packageInfo.publisherFingerprint)

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
        if (candidate.capabilities.home.enabled && activeCapabilities?.home?.enabled != true) add("source-home:read")
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

    private fun remoteCapabilitySetFingerprint(manifest: HxpManifest, publisherFingerprint: String): String = sha256(
        buildString {
            fun field(name: String, value: String) {
                append(name).append(':').append(value.length).append(':').append(value).append('\n')
            }
            field("publisher", publisherFingerprint)
            field("publisher-key-id", manifest.publisherKeyId)
            field("source", manifest.sourceId.value)
            field("remote-read", manifest.capabilities.remoteLibrary.read.toString())
            manifest.capabilities.remoteLibrary.writeOperations.sorted().forEach { field("remote-write", it) }
            manifest.capabilities.remoteLibrary.policies.toSortedMap(compareBy { it.name }).values.forEach { policy ->
                append("remote-policy\n")
                field("operation", policy.operation.name)
                field("origin", policy.origin.canonical)
                field("method", policy.method.name)
                field("path", policy.path)
                field("referrer", policy.referrerPath.orEmpty())
                policy.parameters.sortedWith(
                    compareBy<HxpRemoteParameter>({ it.name }, { it.canonicalKind() }, { it.canonicalValue() }),
                ).forEach { parameter ->
                    append("remote-parameter\n")
                    field("kind", parameter.canonicalKind())
                    field("name", parameter.name)
                    field("value", parameter.canonicalValue())
                }
                policy.redirects.sortedWith(
                    compareBy<HxpRemoteRedirectTarget>(
                        { it.origin.canonical },
                        { it.path },
                        { it.referrerPath.orEmpty() },
                        { redirect -> redirect.parameters.sortedBy(HxpRemoteParameter.Fixed::name).joinToString("\u0000") { "${it.name.length}:${it.name}${it.value.length}:${it.value}" } },
                    ),
                ).forEach { redirect ->
                    append("remote-redirect\n")
                    field("origin", redirect.origin.canonical)
                    field("method", redirect.method.name)
                    field("path", redirect.path)
                    field("referrer", redirect.referrerPath.orEmpty())
                    redirect.parameters.sortedBy { it.name }.forEach { parameter ->
                        append("remote-redirect-parameter\n")
                        field("name", parameter.name)
                        field("value", parameter.value)
                    }
                }
            }
        }.toByteArray(StandardCharsets.UTF_8),
    )
    private fun capabilityGrantFingerprint(
        candidate: VerifiedHxpPackage,
        addedCapabilities: Set<String>,
        resourceLimitIncreases: List<ResourceLimitIncrease>,
    ): String = sha256(
        buildString {
            append(candidate.packageSha256)
            append('\u0000')
            addedCapabilities.sorted().forEach { append("capability:").append(it).append('\n') }
            resourceLimitIncreases.forEach { increase ->
                append("resource:").append(increase.limit.name).append(':').append(increase.activeValue).append(':').append(increase.candidateValue).append('\n')
            }
        }.toByteArray(StandardCharsets.UTF_8),
    )
}

private fun HxpRemoteParameter.canonicalKind(): String = when (this) {
    is HxpRemoteParameter.Fixed -> "fixed"
    is HxpRemoteParameter.RemoteBookId -> "remote-book-id"
    is HxpRemoteParameter.Cursor -> "cursor"
}

private fun HxpRemoteParameter.canonicalValue(): String = when (this) {
    is HxpRemoteParameter.Fixed -> value
    is HxpRemoteParameter.RemoteBookId, is HxpRemoteParameter.Cursor -> ""
}

data class PreparedExtensionInstall(
    val candidate: VerifiedHxpPackage,
    val active: VerifiedHxpPackage?,
    val addedCapabilities: List<String>,
    val resourceLimitIncreases: List<ResourceLimitIncrease>,
    val capabilityGrantFingerprint: String,
    val remoteCapabilitySetFingerprint: String,
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
