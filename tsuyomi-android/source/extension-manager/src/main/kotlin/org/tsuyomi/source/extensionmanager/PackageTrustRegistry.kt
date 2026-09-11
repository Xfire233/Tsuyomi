/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Admission boundary between a verified package and executable installed code.
 *
 * Signature verification establishes who signed bytes. Implementations of this interface decide
 * whether that exact package is user-authorized to execute.
 */
fun interface PackageExecutionTrust {
    @Throws(ExtensionInstallException::class)
    fun requireExecutable(packageInfo: VerifiedHxpPackage)
}

/** Optional durable identity commit called only after an archive replacement succeeds. */
interface PackageActivationTrust : PackageExecutionTrust {
    fun activationSucceeded(prepared: PreparedExtensionInstall)
}

/** Default-safe gate for callers that do not persist user-added package approvals. */
object BuiltInPackageExecutionTrust : PackageExecutionTrust {
    override fun requireExecutable(packageInfo: VerifiedHxpPackage) {
        if (packageInfo.publisherTrust == PublisherTrust.USER_ADDED) {
            throw ExtensionInstallException(ExtensionInstallError.PACKAGE_GRANT_REQUIRED)
        }
    }
}

/** A bounded, untrusted manifest identity shown while requesting a local publisher key. */
data class LocalPublisherIdentity(val keyId: String)

/** Result of a full cryptographic verification with a not-yet-persisted, user-entered key. */
data class VerifiedUserAddedArchive(
    val packageInfo: VerifiedHxpPackage,
    val publisher: PublisherKey,
)

/** Durable approval tied to one source, publisher identity, and complete archive digest. */
data class PackageExecutionGrant(
    val sourceId: String,
    val publisherKeyId: String,
    val publisherFingerprint: String,
    val packageSha256: String,
)

enum class PackageTrustError {
    INVALID_PUBLIC_KEY,
    PUBLISHER_KEY_CONFLICT,
    SOURCE_IDENTITY_CONFLICT,
    INVALID_STATE,
    STORAGE_UNAVAILABLE,
}

class PackageTrustException(
    val error: PackageTrustError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

/**
 * Persistent exact-package grants for non-official publishers.
 *
 * The registry deliberately stores a source publisher pin independently from active archives, so
 * uninstalling an extension cannot erase the identity required to reject a later takeover. A grant
 * never authorizes another archive, another source, or another publisher. Repository-root keys are
 * intentionally not stored here: an authenticated repository cache remains the authority for them.
 */
class PackageTrustRegistry(storageDirectory: File) : PackageActivationTrust {
    private val directory = storageDirectory.canonicalFile
    private val stateFile: File
    private val lockFile: File

    @Volatile
    private var snapshot = Snapshot.empty()

    /** User-entered keys retained only after a verified package receives an explicit grant. */
    val publisherKeys: PublisherKeyResolver = object : PublisherKeyResolver {
        override fun resolve(keyId: String): PublisherKey? = snapshot.keys[keyId]?.let(::copyPublisherKey)
        override fun isRevokedFingerprint(fingerprint: String): Boolean = false
        override fun isRevokedPackage(packageSha256: String): Boolean = false
    }

    init {
        val created = !directory.isDirectory
        require(!created || directory.mkdirs() || directory.isDirectory) { "Package trust storage is unavailable" }
        stateFile = File(directory, STATE_FILE_NAME).canonicalFile
        lockFile = File(directory, LOCK_FILE_NAME).canonicalFile
        require(stateFile.parentFile == directory && lockFile.parentFile == directory) { "Package trust path is invalid" }
        reload()
    }

    /** Re-reads persistent grants after another app component has changed them. */
    fun reload() {
        snapshot = transaction { state -> state.toSnapshot() }
    }

    /**
     * Extracts only the manifest key ID through bounded archive inspection. The result is not a
     * signature verdict and must never be treated as an approval or trusted key declaration.
     */
    fun inspectLocalArchive(candidateFile: File, verifier: HxpArchiveVerifier): LocalPublisherIdentity =
        LocalPublisherIdentity(verifier.inspectPublisherKeyId(candidateFile))

    /**
     * Verifies the complete HXP archive with a raw Ed25519 key supplied for the displayed key ID.
     * The ephemeral key is never persisted here; [approve] performs persistence after visible
     * package consent.
     */
    fun verifyUserAddedArchive(
        candidateFile: File,
        keyId: String,
        encodedPublicKey: String,
        additionalTrust: PublisherKeyResolver,
    ): VerifiedUserAddedArchive {
        val key = try {
            PublisherKey(
                keyId = keyId,
                publicKey = decodeCanonicalEd25519PublicKey(encodedPublicKey),
                trust = PublisherTrust.USER_ADDED,
            )
        } catch (error: IllegalArgumentException) {
            throw PackageTrustException(PackageTrustError.INVALID_PUBLIC_KEY, error)
        }
        val resolver = CompositePublisherKeyResolver(
            listOf(InMemoryPublisherKeyStore(listOf(key)), additionalTrust),
        )
        val verified = HxpArchiveVerifier(resolver).verify(candidateFile)
        if (verified.publisherTrust != PublisherTrust.USER_ADDED || verified.publisherFingerprint != key.fingerprint) {
            throw HxpVerificationException(HxpVerificationError.UNKNOWN_PUBLISHER)
        }
        return VerifiedUserAddedArchive(verified, copyPublisherKey(key))
    }

    /**
     * Records visible consent for one exact package. Set [retainPublisherKey] only for a local key
     * that [verifyUserAddedArchive] has verified; repository publishers remain rooted in their
     * authenticated catalog cache and must not be duplicated as a second user authority.
     */
    fun approve(
        prepared: PreparedExtensionInstall,
        publisher: PublisherKey,
        retainPublisherKey: Boolean = false,
    ): PackageExecutionGrant {
        val candidate = prepared.candidate
        if (publisher.keyId != candidate.manifest.publisherKeyId ||
            publisher.fingerprint != candidate.publisherFingerprint ||
            publisher.trust != candidate.publisherTrust
        ) {
            throw PackageTrustException(PackageTrustError.PUBLISHER_KEY_CONFLICT)
        }
        val grant = PackageExecutionGrant(
            sourceId = candidate.manifest.sourceId.value,
            publisherKeyId = publisher.keyId,
            publisherFingerprint = publisher.fingerprint,
            packageSha256 = candidate.packageSha256,
        )
        snapshot = transaction { state ->
            val encodedKey = Base64.getEncoder().encodeToString(publisher.publicKey)
            val existingKey = state.keys.singleOrNull { it.keyId == publisher.keyId }
            if (retainPublisherKey && existingKey != null && existingKey.publicKey != encodedKey) {
                throw PackageTrustException(PackageTrustError.PUBLISHER_KEY_CONFLICT)
            }
            val pin = state.pins.singleOrNull { it.sourceId == grant.sourceId }
            val pinChangesPublisher = pin != null &&
                (pin.publisherKeyId != grant.publisherKeyId || pin.publisherFingerprint != grant.publisherFingerprint)
            if (pinChangesPublisher &&
                !(prepared.isLegacyMigration && candidate.publisherTrust == PublisherTrust.BUILT_IN_OFFICIAL)
            ) {
                throw PackageTrustException(PackageTrustError.SOURCE_IDENTITY_CONFLICT)
            }
            val migrationReceipt = if (pinChangesPublisher) {
                StoredMigrationReceipt(
                    sourceId = grant.sourceId,
                    fromPublisherKeyId = pin.publisherKeyId,
                    fromPublisherFingerprint = pin.publisherFingerprint,
                    toPublisherKeyId = grant.publisherKeyId,
                    toPublisherFingerprint = grant.publisherFingerprint,
                    packageSha256 = grant.packageSha256,
                )
            } else {
                null
            }
            val next = state.copy(
                keys = if (retainPublisherKey && candidate.publisherTrust == PublisherTrust.USER_ADDED && existingKey == null) {
                    state.keys + StoredUserPublisherKey(publisher.keyId, encodedKey)
                } else {
                    state.keys
                },
                grants = if (candidate.publisherTrust == PublisherTrust.USER_ADDED && state.grants.none { it.matches(grant) }) {
                    state.grants + StoredPackageGrant.from(grant)
                } else {
                    state.grants
                },
                migrationReceipts = if (migrationReceipt != null && state.migrationReceipts.none { it == migrationReceipt }) {
                    state.migrationReceipts + migrationReceipt
                } else {
                    state.migrationReceipts
                },
            ).validated()
            writeState(next)
            next.toSnapshot()
        }
        return grant
    }

    override fun activationSucceeded(prepared: PreparedExtensionInstall) {
        snapshot = transaction { state ->
            val candidate = prepared.candidate
            val pin = state.pins.singleOrNull { it.sourceId == candidate.manifest.sourceId.value }
            val changesPublisher = pin != null &&
                (pin.publisherKeyId != candidate.manifest.publisherKeyId || pin.publisherFingerprint != candidate.publisherFingerprint)
            if (changesPublisher && !state.migrationReceipts.any { it.matches(pin, candidate) }) {
                throw PackageTrustException(PackageTrustError.SOURCE_IDENTITY_CONFLICT)
            }
            if (pin == null || changesPublisher) {
                val next = state.copy(
                    pins = state.pins.filterNot { it.sourceId == candidate.manifest.sourceId.value } + StoredSourcePin(
                        sourceId = candidate.manifest.sourceId.value,
                        publisherKeyId = candidate.manifest.publisherKeyId,
                        publisherFingerprint = candidate.publisherFingerprint,
                    ),
                    migrationReceipts = state.migrationReceipts.filterNot { it.matchesCandidate(candidate) },
                ).validated()
                writeState(next)
                next.toSnapshot()
            } else {
                state.toSnapshot()
            }
        }
    }

    /**
     * Retains an identity before app-owned uninstall. A differing key is accepted only when this
     * verified active archive matches a durable, explicitly approved migration receipt, allowing
     * recovery if a process died after swapping the archive but before pin persistence.
     */
    fun pinInstalled(packageInfo: VerifiedHxpPackage, publisher: PublisherKey) {
        if (publisher.keyId != packageInfo.manifest.publisherKeyId || publisher.fingerprint != packageInfo.publisherFingerprint) {
            throw PackageTrustException(PackageTrustError.PUBLISHER_KEY_CONFLICT)
        }
        snapshot = transaction { state ->
            val pin = state.pins.singleOrNull { it.sourceId == packageInfo.manifest.sourceId.value }
            val changesPublisher = pin != null &&
                (pin.publisherKeyId != publisher.keyId || pin.publisherFingerprint != publisher.fingerprint)
            if (changesPublisher && !state.migrationReceipts.any { it.matches(pin, packageInfo) }) {
                throw PackageTrustException(PackageTrustError.SOURCE_IDENTITY_CONFLICT)
            }
            if (pin == null || changesPublisher) {
                val next = state.copy(
                    pins = state.pins.filterNot { it.sourceId == packageInfo.manifest.sourceId.value } + StoredSourcePin(
                        sourceId = packageInfo.manifest.sourceId.value,
                        publisherKeyId = publisher.keyId,
                        publisherFingerprint = publisher.fingerprint,
                    ),
                    migrationReceipts = state.migrationReceipts.filterNot { it.matchesCandidate(packageInfo) },
                ).validated()
                writeState(next)
                next.toSnapshot()
            } else {
                state.toSnapshot()
            }
        }
    }

    /** Built-in roots execute under their configured policy; user roots require an exact grant. */
    fun isApproved(packageInfo: VerifiedHxpPackage): Boolean {
        if (packageInfo.publisherTrust != PublisherTrust.USER_ADDED) return true
        return PackageExecutionGrant(
            sourceId = packageInfo.manifest.sourceId.value,
            publisherKeyId = packageInfo.manifest.publisherKeyId,
            publisherFingerprint = packageInfo.publisherFingerprint,
            packageSha256 = packageInfo.packageSha256,
        ) in snapshot.grants
    }

    override fun requireExecutable(packageInfo: VerifiedHxpPackage) {
        if (!isApproved(packageInfo)) throw ExtensionInstallException(ExtensionInstallError.PACKAGE_GRANT_REQUIRED)
    }

    private fun <T> transaction(block: (PackageTrustState) -> T): T {
        val monitor = DIRECTORY_LOCKS.computeIfAbsent(directory.path) { Any() }
        return synchronized(monitor) {
            try {
                java.io.RandomAccessFile(lockFile, "rw").channel.use { channel ->
                    val fileLock = channel.lock()
                    try {
                        block(readState())
                    } finally {
                        fileLock.release()
                    }
                }
            } catch (error: PackageTrustException) {
                throw error
            } catch (error: IOException) {
                throw PackageTrustException(PackageTrustError.STORAGE_UNAVAILABLE, error)
            }
        }
    }

    private fun readState(): PackageTrustState {
        if (!stateFile.exists()) return PackageTrustState.empty()
        if (!stateFile.isFile) throw PackageTrustException(PackageTrustError.STORAGE_UNAVAILABLE)
        val bytes = try {
            readBounded(stateFile, MAX_STATE_BYTES)
        } catch (error: IOException) {
            throw PackageTrustException(PackageTrustError.STORAGE_UNAVAILABLE, error)
        }
        return try {
            PackageTrustState.decode(bytes.toString(StandardCharsets.UTF_8)).validated()
        } catch (error: PackageTrustException) {
            throw error
        } catch (error: Throwable) {
            throw PackageTrustException(PackageTrustError.INVALID_STATE, error)
        }
    }

    private fun writeState(state: PackageTrustState) {
        val encoded = state.encode().toByteArray(StandardCharsets.UTF_8)
        if (encoded.size > MAX_STATE_BYTES) throw PackageTrustException(PackageTrustError.STORAGE_UNAVAILABLE)
        val temporary = File(directory, ".${STATE_FILE_NAME}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(encoded)
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                stateFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: IOException) {
            throw PackageTrustException(PackageTrustError.STORAGE_UNAVAILABLE, error)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private data class PackageTrustState(
        val version: Int,
        val keys: List<StoredUserPublisherKey>,
        val pins: List<StoredSourcePin>,
        val grants: List<StoredPackageGrant>,
        val migrationReceipts: List<StoredMigrationReceipt> = emptyList(),
    ) {
        fun validated(): PackageTrustState {
            if (version != 1 || keys.size > MAX_KEYS || pins.size > MAX_PINS || grants.size > MAX_GRANTS ||
                migrationReceipts.size > MAX_MIGRATION_RECEIPTS
            ) {
                throw PackageTrustException(PackageTrustError.INVALID_STATE)
            }
            if (keys.map(StoredUserPublisherKey::keyId).toSet().size != keys.size ||
                pins.map(StoredSourcePin::sourceId).toSet().size != pins.size ||
                grants.map { grant ->
                    listOf(grant.sourceId, grant.publisherKeyId, grant.publisherFingerprint, grant.packageSha256)
                }.toSet().size != grants.size ||
                migrationReceipts.toSet().size != migrationReceipts.size
            ) {
                throw PackageTrustException(PackageTrustError.INVALID_STATE)
            }
            keys.forEach { record ->
                try {
                    PublisherKey(record.keyId, decodeCanonicalEd25519PublicKey(record.publicKey), PublisherTrust.USER_ADDED)
                } catch (error: IllegalArgumentException) {
                    throw PackageTrustException(PackageTrustError.INVALID_STATE, error)
                }
            }
            pins.forEach { pin ->
                try {
                    org.tsuyomi.shared.sourcecontract.SourceId(pin.sourceId)
                    PublisherKey(pin.publisherKeyId, ByteArray(32), PublisherTrust.USER_ADDED)
                } catch (error: IllegalArgumentException) {
                    throw PackageTrustException(PackageTrustError.INVALID_STATE, error)
                }
                if (!KEY_FINGERPRINT.matches(pin.publisherFingerprint)) throw PackageTrustException(PackageTrustError.INVALID_STATE)
            }
            grants.forEach { grant ->
                try {
                    org.tsuyomi.shared.sourcecontract.SourceId(grant.sourceId)
                    PublisherKey(grant.publisherKeyId, ByteArray(32), PublisherTrust.USER_ADDED)
                } catch (error: IllegalArgumentException) {
                    throw PackageTrustException(PackageTrustError.INVALID_STATE, error)
                }
                if (!KEY_FINGERPRINT.matches(grant.publisherFingerprint) || !KEY_FINGERPRINT.matches(grant.packageSha256)) {
                    throw PackageTrustException(PackageTrustError.INVALID_STATE)
                }
            }
            migrationReceipts.forEach { receipt ->
                try {
                    org.tsuyomi.shared.sourcecontract.SourceId(receipt.sourceId)
                    PublisherKey(receipt.fromPublisherKeyId, ByteArray(32), PublisherTrust.BUILT_IN_OFFICIAL)
                    PublisherKey(receipt.toPublisherKeyId, ByteArray(32), PublisherTrust.BUILT_IN_OFFICIAL)
                } catch (error: IllegalArgumentException) {
                    throw PackageTrustException(PackageTrustError.INVALID_STATE, error)
                }
                if (!KEY_FINGERPRINT.matches(receipt.fromPublisherFingerprint) ||
                    !KEY_FINGERPRINT.matches(receipt.toPublisherFingerprint) ||
                    !KEY_FINGERPRINT.matches(receipt.packageSha256)
                ) {
                    throw PackageTrustException(PackageTrustError.INVALID_STATE)
                }
            }
            return copy(
                keys = keys.sortedBy(StoredUserPublisherKey::keyId),
                pins = pins.sortedBy(StoredSourcePin::sourceId),
                grants = grants.sortedWith(compareBy(StoredPackageGrant::sourceId, StoredPackageGrant::publisherFingerprint, StoredPackageGrant::packageSha256)),
                migrationReceipts = migrationReceipts.sortedWith(compareBy(StoredMigrationReceipt::sourceId, StoredMigrationReceipt::packageSha256)),
            )
        }

        fun encode(): String = buildString {
            append("version:1\n")
            keys.forEach { append("key:").append(it.keyId).append(':').append(it.publicKey).append('\n') }
            pins.forEach {
                append("pin:").append(it.sourceId).append(':').append(it.publisherKeyId).append(':')
                    .append(it.publisherFingerprint).append('\n')
            }
            grants.forEach {
                append("grant:").append(it.sourceId).append(':').append(it.publisherKeyId).append(':')
                    .append(it.publisherFingerprint).append(':').append(it.packageSha256).append('\n')
            }
            migrationReceipts.forEach {
                append("migration:").append(it.sourceId).append(':').append(it.fromPublisherKeyId).append(':')
                    .append(it.fromPublisherFingerprint).append(':').append(it.toPublisherKeyId).append(':')
                    .append(it.toPublisherFingerprint).append(':').append(it.packageSha256).append('\n')
            }
        }

        fun toSnapshot(): Snapshot = Snapshot(
            keys = keys.associate { record ->
                record.keyId to PublisherKey(record.keyId, decodeCanonicalEd25519PublicKey(record.publicKey), PublisherTrust.USER_ADDED)
            },
            grants = grants.map { grant ->
                PackageExecutionGrant(grant.sourceId, grant.publisherKeyId, grant.publisherFingerprint, grant.packageSha256)
            }.toSet(),
        )

        companion object {
            fun empty() = PackageTrustState(version = 1, keys = emptyList(), pins = emptyList(), grants = emptyList())

            fun decode(text: String): PackageTrustState {
                val lines = text.split('\n')
                if (lines.size < 2 || lines.first() != "version:1" || lines.last() != "") {
                    throw PackageTrustException(PackageTrustError.INVALID_STATE)
                }
                val keys = mutableListOf<StoredUserPublisherKey>()
                val pins = mutableListOf<StoredSourcePin>()
                val grants = mutableListOf<StoredPackageGrant>()
                val migrations = mutableListOf<StoredMigrationReceipt>()
                lines.drop(1).dropLast(1).forEach { line ->
                    val fields = line.split(':')
                    when (fields.firstOrNull()) {
                        "key" -> if (fields.size == 3) keys += StoredUserPublisherKey(fields[1], fields[2]) else invalidState()
                        "pin" -> if (fields.size == 4) pins += StoredSourcePin(fields[1], fields[2], fields[3]) else invalidState()
                        "grant" -> if (fields.size == 5) grants += StoredPackageGrant(fields[1], fields[2], fields[3], fields[4]) else invalidState()
                        "migration" -> if (fields.size == 7) migrations += StoredMigrationReceipt(
                            fields[1], fields[2], fields[3], fields[4], fields[5], fields[6],
                        ) else invalidState()
                        else -> invalidState()
                    }
                }
                return PackageTrustState(1, keys, pins, grants, migrations)
            }

            private fun invalidState(): Nothing = throw PackageTrustException(PackageTrustError.INVALID_STATE)
        }
    }

    private data class StoredUserPublisherKey(val keyId: String, val publicKey: String)

    private data class StoredSourcePin(
        val sourceId: String,
        val publisherKeyId: String,
        val publisherFingerprint: String,
    )

    private data class StoredPackageGrant(
        val sourceId: String,
        val publisherKeyId: String,
        val publisherFingerprint: String,
        val packageSha256: String,
    ) {
        fun matches(grant: PackageExecutionGrant): Boolean =
            sourceId == grant.sourceId &&
                publisherKeyId == grant.publisherKeyId &&
                publisherFingerprint == grant.publisherFingerprint &&
                packageSha256 == grant.packageSha256

        companion object {
            fun from(grant: PackageExecutionGrant) = StoredPackageGrant(
                sourceId = grant.sourceId,
                publisherKeyId = grant.publisherKeyId,
                publisherFingerprint = grant.publisherFingerprint,
                packageSha256 = grant.packageSha256,
            )
        }
    }

    private data class StoredMigrationReceipt(
        val sourceId: String,
        val fromPublisherKeyId: String,
        val fromPublisherFingerprint: String,
        val toPublisherKeyId: String,
        val toPublisherFingerprint: String,
        val packageSha256: String,
    ) {
        fun matches(active: StoredSourcePin, candidate: VerifiedHxpPackage): Boolean =
            sourceId == candidate.manifest.sourceId.value &&
                fromPublisherKeyId == active.publisherKeyId &&
                fromPublisherFingerprint == active.publisherFingerprint &&
                toPublisherKeyId == candidate.manifest.publisherKeyId &&
                toPublisherFingerprint == candidate.publisherFingerprint &&
                packageSha256 == candidate.packageSha256

        fun matchesCandidate(candidate: VerifiedHxpPackage): Boolean =
            sourceId == candidate.manifest.sourceId.value &&
                toPublisherKeyId == candidate.manifest.publisherKeyId &&
                toPublisherFingerprint == candidate.publisherFingerprint &&
                packageSha256 == candidate.packageSha256
    }

    private data class Snapshot(
        val keys: Map<String, PublisherKey>,
        val grants: Set<PackageExecutionGrant>,
    ) {
        companion object {
            fun empty() = Snapshot(emptyMap(), emptySet())
        }
    }

    private companion object {
        const val STATE_FILE_NAME = "package-trust-v1.state"
        const val LOCK_FILE_NAME = "package-trust-v1.lock"
        const val MAX_STATE_BYTES = 512 * 1024
        const val MAX_KEYS = 128
        const val MAX_PINS = 2_048
        const val MAX_GRANTS = 4_096
        const val MAX_MIGRATION_RECEIPTS = 2_048
        val KEY_FINGERPRINT = Regex("^[a-f0-9]{64}$")
        val DIRECTORY_LOCKS = ConcurrentHashMap<String, Any>()
}
}

/**
 * Combines key authorities by built-in precedence, rejecting conflicting bytes within that tier.
 * Scoped revocations preserve provenance and official root authority through nested resolvers.
 * User-added roots cannot replace or revoke built-in identities.
 */
class CompositePublisherKeyResolver(
    private val resolvers: List<PublisherKeyResolver>,
) : PublisherKeyResolver {
    init {
        require(resolvers.isNotEmpty()) { "At least one publisher resolver is required" }
    }

    override val hasGlobalRevocationAuthority: Boolean
        get() = resolvers.any { it.hasGlobalRevocationAuthority }

    override fun resolve(keyId: String): PublisherKey? {
        val candidates = resolvers.mapNotNull { it.resolve(keyId) }
        val trust = when {
            candidates.any { it.trust == PublisherTrust.BUILT_IN_OFFICIAL } -> PublisherTrust.BUILT_IN_OFFICIAL
            candidates.any { it.trust == PublisherTrust.BUILT_IN_TEST } -> PublisherTrust.BUILT_IN_TEST
            else -> PublisherTrust.USER_ADDED
        }
        val authorities = candidates.filter { it.trust == trust }
        val first = authorities.firstOrNull() ?: return null
        if (authorities.any { !it.publicKey.contentEquals(first.publicKey) }) return null
        return copyPublisherKey(first)
    }

    override fun isRevokedPublisher(keyId: String, fingerprint: String): Boolean =
        scopedResolvers(keyId, fingerprint).any { it.isRevokedPublisher(keyId, fingerprint) }

    override fun isRevokedPackage(packageSha256: String, keyId: String, fingerprint: String): Boolean =
        scopedResolvers(keyId, fingerprint).any { it.isRevokedPackage(packageSha256, keyId, fingerprint) }

    /** Callers without provenance retain conservative, fail-closed behavior. */
    override fun isRevokedFingerprint(fingerprint: String): Boolean = resolvers.any { it.isRevokedFingerprint(fingerprint) }
    override fun isRevokedPackage(packageSha256: String): Boolean = resolvers.any { it.isRevokedPackage(packageSha256) }

    private fun scopedResolvers(keyId: String, fingerprint: String): Sequence<PublisherKeyResolver> {
        val selected = resolve(keyId)
        return resolvers.asSequence().filter { resolver ->
            resolver.hasGlobalRevocationAuthority || resolver.resolve(keyId)?.let { key ->
                key.fingerprint == fingerprint && key.trust == selected?.trust
            } == true
        }
    }
}

internal fun decodeCanonicalEd25519PublicKey(value: String): ByteArray {
    if (value.length != 44 || !value.endsWith('=') || value.any { it !in CANONICAL_BASE64_CHARS }) {
        throw IllegalArgumentException("Ed25519 public key must be canonical Base64")
    }
    val decoded = Base64.getDecoder().decode(value)
    require(decoded.size == 32 && Base64.getEncoder().encodeToString(decoded) == value) {
        "Ed25519 public key must be 32 raw bytes"
    }
    return decoded
}

private val CANONICAL_BASE64_CHARS =
    ('A'..'Z').toSet() + ('a'..'z').toSet() + ('0'..'9').toSet() + setOf('+', '/', '=')

private fun readBounded(file: File, maximumBytes: Int): ByteArray = FileInputStream(file).use { input ->
    val output = java.io.ByteArrayOutputStream(minOf(maximumBytes, 16 * 1024))
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size() > maximumBytes - count) throw IOException("State exceeds its bound")
        output.write(buffer, 0, count)
    }
    output.toByteArray()
}
