/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.security.MessageDigest
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.shared.sourcecontract.SourceId

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String>,
    val original: String,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1
        for (index in 0 until maxOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease.getOrNull(index) ?: return -1
            val right = other.prerelease.getOrNull(index) ?: return 1
            if (left == right) continue
            val leftNumeric = left.all { it in '0'..'9' }
            val rightNumeric = right.all { it in '0'..'9' }
            return when {
                leftNumeric && rightNumeric -> compareNumericPrereleaseIdentifier(left, right)
                leftNumeric -> -1
                rightNumeric -> 1
                else -> left.compareTo(right)
            }
        }
        return 0
    }

    companion object {
        private val PATTERN = Regex(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?" +
                "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$",
        )

        fun parse(value: String): SemanticVersion {
            val match = requireNotNull(PATTERN.matchEntire(value)) { "Invalid semantic version" }
            return SemanticVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                prerelease = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.') ?: emptyList(),
                original = value,
            )
        }
    }
}

private fun compareNumericPrereleaseIdentifier(left: String, right: String): Int {
    val normalizedLeft = left.trimStart('0').ifEmpty { "0" }
    val normalizedRight = right.trimStart('0').ifEmpty { "0" }
    return compareValues(normalizedLeft.length, normalizedRight.length)
        .takeIf { it != 0 }
        ?: normalizedLeft.compareTo(normalizedRight)
}

data class HxpNetworkCapability(
    val origins: Set<HttpsOrigin>,
    val maxConcurrentRequests: Int,
    val requestTimeoutMs: Int,
    val maxResponseBytes: Int,
)

data class HxpCookieCapability(val sourceScoped: Boolean, val origins: Set<HttpsOrigin>)
data class HxpWebLoginCapability(val enabled: Boolean, val origins: Set<HttpsOrigin>)
data class HxpHomeCapability(val enabled: Boolean)

data class HxpUpdateCheckPolicy(
    val origin: HttpsOrigin,
    val path: String,
    val referrerPath: String?,
    val parameters: List<HxpRemoteParameter>,
)

data class HxpUpdateCheckCapability(
    val version: Int,
    val policy: HxpUpdateCheckPolicy,
)

enum class RemoteOperation { READ, TARGETS, ADD, REMOVE, MOVE }

sealed interface HxpRemoteParameter {
    val name: String

    data class Fixed(override val name: String, val value: String) : HxpRemoteParameter
    data class RemoteBookId(override val name: String) : HxpRemoteParameter
    data class Cursor(override val name: String) : HxpRemoteParameter
    data class TargetId(override val name: String) : HxpRemoteParameter
}


data class HxpRemoteRedirectTarget(
    val origin: HttpsOrigin,
    val method: NetworkMethod,
    val path: String,
    val referrerPath: String?,
    val parameters: List<HxpRemoteParameter.Fixed>,
)
data class HxpRemoteOperationPolicy(
    val operation: RemoteOperation,
    val origin: HttpsOrigin,
    val method: NetworkMethod,
    val path: String,
    val referrerPath: String?,
    val parameters: List<HxpRemoteParameter>,
    val redirects: List<HxpRemoteRedirectTarget> = emptyList(),
)

data class HxpRemoteLibraryCapability(
    val read: Boolean,
    val writeOperations: Set<String>,
    val policies: Map<RemoteOperation, HxpRemoteOperationPolicy>,
)
data class HxpCapabilities(
    val network: HxpNetworkCapability,
    val cookies: HxpCookieCapability,
    val webLogin: HxpWebLoginCapability,
    val home: HxpHomeCapability,
    val remoteLibrary: HxpRemoteLibraryCapability,
    val storageQuotaBytes: Int,
    val updateCheck: HxpUpdateCheckCapability? = null,
)

data class HxpResourceLimits(val maxExecutionWallTimeMs: Int, val maxMemoryBytes: Int)

data class HxpManifest(
    val sourceId: SourceId,
    val version: SemanticVersion,
    val displayName: String,
    val summary: String,
    val homepage: String?,
    val hostApiMinInclusive: SemanticVersion,
    val hostApiMaxExclusive: SemanticVersion,
    val entry: String,
    val contentDigest: String,
    val files: Map<String, String>,
    val publisherKeyId: String,
    val capabilities: HxpCapabilities,
    val resourceLimits: HxpResourceLimits,
    val updateChannel: String,
)

enum class PublisherTrust { BUILT_IN_OFFICIAL, BUILT_IN_TEST, USER_ADDED }

data class PublisherKey(
    val keyId: String,
    val publicKey: ByteArray,
    val trust: PublisherTrust,
) {
    val fingerprint: String = sha256(publicKey)

    init {
        require(KEY_ID.matches(keyId)) { "Invalid publisher key ID" }
        require(publicKey.size == 32) { "Ed25519 public key must contain 32 bytes" }
    }

    companion object {
        private val KEY_ID = Regex("^[A-Za-z0-9._-]{8,128}$")
    }
}

interface PublisherKeyResolver {
    /** Only explicitly configured official repository roots may revoke without a publisher listing. */
    val hasGlobalRevocationAuthority: Boolean get() = false
    fun resolve(keyId: String): PublisherKey?
    fun isRevokedFingerprint(fingerprint: String): Boolean
    fun isRevokedPackage(packageSha256: String): Boolean

    /** Scoped overloads preserve publisher provenance and explicitly configured root authority. */
    fun isRevokedPublisher(keyId: String, fingerprint: String): Boolean = isRevokedFingerprint(fingerprint)
    fun isRevokedPackage(packageSha256: String, keyId: String, fingerprint: String): Boolean =
        isRevokedPackage(packageSha256)
}

class InMemoryPublisherKeyStore(keys: Iterable<PublisherKey>) : PublisherKeyResolver {
    private val byId = keys.associateBy(PublisherKey::keyId).toMutableMap()
    private val revokedFingerprints = mutableSetOf<String>()
    private val revokedPackages = mutableSetOf<String>()

    override fun resolve(keyId: String): PublisherKey? = byId[keyId]
    override fun isRevokedFingerprint(fingerprint: String): Boolean = fingerprint in revokedFingerprints
    override fun isRevokedPackage(packageSha256: String): Boolean = packageSha256 in revokedPackages

    fun add(key: PublisherKey) {
        val existing = byId[key.keyId]
        require(existing == null || existing.publicKey.contentEquals(key.publicKey)) { "Publisher key ID collision" }
        byId[key.keyId] = key
    }

    fun revokeFingerprint(fingerprint: String) {
        revokedFingerprints += fingerprint
    }

    fun revokePackage(packageSha256: String) {
        revokedPackages += packageSha256
    }
}

enum class HxpVerificationError {
    ARCHIVE_TOO_LARGE,
    TOO_MANY_FILES,
    INVALID_ARCHIVE_ENTRY,
    UNSUPPORTED_COMPRESSION,
    ENCRYPTED_ENTRY,
    SYMLINK_ENTRY,
    FILE_TOO_LARGE,
    COMPRESSION_RATIO_EXCEEDED,
    MISSING_REQUIRED_FILE,
    INVALID_MANIFEST,
    HOST_API_INCOMPATIBLE,
    CAPABILITY_POLICY_VIOLATION,
    INTEGRITY_MISMATCH,
    UNKNOWN_PUBLISHER,
    REVOKED_PUBLISHER,
    REVOKED_PACKAGE,
    INVALID_SIGNATURE,
}

class HxpVerificationException(val error: HxpVerificationError) : Exception(error.name)

class VerifiedHxpPackage(
    val manifest: HxpManifest,
    val packageSha256: String,
    val publisherFingerprint: String,
    /** Origin classification comes from the resolver, never from a package manifest. */
    val publisherTrust: PublisherTrust,
    archiveBytes: ByteArray,
    entryModuleBytes: ByteArray,
) {
    private val storedArchiveBytes = archiveBytes.copyOf()
    private val storedEntryModuleBytes = entryModuleBytes.copyOf()

    val archiveBytes: ByteArray
        get() = storedArchiveBytes.copyOf()

    fun readVerifiedEntryModule(): ByteArray = storedEntryModuleBytes.copyOf()
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { "%02x".format(it) }
