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
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.tsuyomi.shared.sourcecontract.SourceId

/**
 * A subscription-link proposal. Parsing is local-only; callers must present this root fingerprint
 * before [RepositorySubscriptionRegistry.confirm] makes it durable.
 */
data class RepositorySubscriptionLink(
    val root: RepositoryRoot,
) {
    val repositoryId: String
        get() = root.repositoryId
    val indexUrl: String
        get() = root.indexUrl
    val rootFingerprint: String
        get() = root.signingKey.fingerprint
}

/** Durable third-party repository state exposed to application controllers. */
data class RepositorySubscription(
    val root: RepositoryRoot,
    val enabled: Boolean,
) {
    val repositoryId: String
        get() = root.repositoryId
    val indexUrl: String
        get() = root.indexUrl
    val rootFingerprint: String
        get() = root.signingKey.fingerprint
}

enum class RepositorySubscriptionError {
    INVALID_LINK,
    IDENTITY_REBINDING,
    SUBSCRIPTION_NOT_FOUND,
    INVALID_STATE,
    STORAGE_UNAVAILABLE,
}

class RepositorySubscriptionException(
    val error: RepositorySubscriptionError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

/**
 * Strict parser for a non-official repository link.
 *
 * The index URL is the HTTPS portion before `#`. The fragment is exactly
 * `repositoryId=<source-id>&keyId=<key-id>&publicKey=<raw-ed25519-base64>` in that order. Percent
 * encoding, duplicate fields and alternate encodings are rejected so a displayed root is exactly
 * the root that will later verify a fetched catalog.
 */
fun parseRepositorySubscriptionLink(link: String): RepositorySubscriptionLink {
    try {
        require(link.length in 1..MAX_SUBSCRIPTION_LINK_LENGTH)
        val separator = link.indexOf('#')
        require(separator in 12 until link.lastIndex)
        val indexUrl = link.substring(0, separator)
        val fragment = link.substring(separator + 1)
        require('#' !in fragment && '%' !in fragment)
        val fields = fragment.split('&')
        require(fields.size == 3)
        val expectedNames = listOf("repositoryId", "keyId", "publicKey")
        val values = fields.mapIndexed { index, field ->
            val delimiter = field.indexOf('=')
            require(delimiter > 0)
            require(field.substring(0, delimiter) == expectedNames[index])
            field.substring(delimiter + 1).also { require(it.isNotEmpty()) }
        }
        val repositoryId = SourceId(values[0]).value
        val key = PublisherKey(
            keyId = values[1],
            publicKey = decodeCanonicalEd25519PublicKey(values[2]),
            trust = PublisherTrust.USER_ADDED,
        )
        val canonicalIndex = requireHttpsRepositoryUrl(indexUrl).toASCIIString()
        return RepositorySubscriptionLink(RepositoryRoot(repositoryId, canonicalIndex, key))
    } catch (error: RepositorySubscriptionException) {
        throw error
    } catch (error: Throwable) {
        throw RepositorySubscriptionException(RepositorySubscriptionError.INVALID_LINK, error)
    }
}

/**
 * No-backup registry for user-added roots. Removing a subscription tombstones its root record and
 * never deletes that root's catalog directory, so cached publisher identity, revocations and the
 * anti-rollback high-water journal survive disable/remove/re-add cycles.
 */
class RepositorySubscriptionRegistry(
    storageDirectory: File,
    private val fetcher: RepositoryFetcher = HttpsRepositoryFetcher(),
    private val clock: () -> Instant = { Instant.now() },
) {
    private val directory = storageDirectory.canonicalFile
    private val stateFile: File
    private val lockFile: File
    private val clients = ConcurrentHashMap<String, OfficialRepositoryClient>()

    @Volatile
    private var snapshot = SubscriptionSnapshot.empty()

    @Volatile
    private var retainedResolvers: List<PublisherKeyResolver> = emptyList()

    /**
     * Resolver for all active, disabled and removed roots' authenticated cached trust. Tombstones
     * deliberately participate in revocation checks but cannot be selected through [client].
     */
    val publisherKeys: PublisherKeyResolver = object : PublisherKeyResolver {
        override fun resolve(keyId: String): PublisherKey? {
            val resolvers = retainedResolvers
            return if (resolvers.isEmpty()) null else CompositePublisherKeyResolver(resolvers).resolve(keyId)
        }

        override fun isRevokedFingerprint(fingerprint: String): Boolean =
            retainedResolvers.any { it.isRevokedFingerprint(fingerprint) }

        override fun isRevokedPackage(packageSha256: String): Boolean =
            retainedResolvers.any { it.isRevokedPackage(packageSha256) }

        override fun isRevokedPublisher(keyId: String, fingerprint: String): Boolean =
            resolversFor(keyId, fingerprint).any { it.isRevokedPublisher(keyId, fingerprint) }

        override fun isRevokedPackage(packageSha256: String, keyId: String, fingerprint: String): Boolean =
            resolversFor(keyId, fingerprint).any { it.isRevokedPackage(packageSha256, keyId, fingerprint) }

        /**
         * Only roots that authenticated this exact publisher participate. When identical user
         * material originates from multiple roots, its origin is unknowable at this interface, so
         * [any] keeps revocation conservative.
         */
        private fun resolversFor(keyId: String, fingerprint: String): List<PublisherKeyResolver> =
            retainedResolvers.filter { resolver ->
                resolver.resolve(keyId)?.fingerprint == fingerprint
            }
    }

    init {
        val created = !directory.isDirectory
        require(!created || directory.mkdirs() || directory.isDirectory) { "Repository subscription storage is unavailable" }
        stateFile = File(directory, STATE_FILE_NAME).canonicalFile
        lockFile = File(directory, LOCK_FILE_NAME).canonicalFile
        require(stateFile.parentFile == directory && lockFile.parentFile == directory) { "Repository subscription path is invalid" }
        reload()
    }

    /** Parses a candidate link without I/O or network activity. */
    fun inspect(link: String): RepositorySubscriptionLink = parseRepositorySubscriptionLink(link)

    /** Makes a previously inspected root durable without fetching its catalog. */
    fun confirm(proposal: RepositorySubscriptionLink): RepositorySubscription {
        val record = update { state ->
            val existing = state.records.singleOrNull { it.repositoryId == proposal.repositoryId }
            if (existing != null) {
                if (!existing.matches(proposal.root)) {
                    throw RepositorySubscriptionException(RepositorySubscriptionError.IDENTITY_REBINDING)
                }
                state.copy(records = state.records.filterNot { it.repositoryId == proposal.repositoryId } + existing.copy(
                    enabled = true,
                    removed = false,
                ))
            } else {
                if (state.records.size >= MAX_SUBSCRIPTIONS || state.records.any {
                        it.indexUrl == proposal.indexUrl || it.publicKey == encodeRawKey(proposal.root.signingKey.publicKey)
                    }
                ) {
                    throw RepositorySubscriptionException(RepositorySubscriptionError.IDENTITY_REBINDING)
                }
                state.copy(records = state.records + StoredSubscription.from(proposal.root, enabled = true, removed = false))
            }
        }.records.single { it.repositoryId == proposal.repositoryId }
        return record.toSubscription()
    }

    /** Convenience atomic parse-and-confirm operation. Parsing itself still performs no network work. */
    fun add(link: String): RepositorySubscription = confirm(inspect(link))

    /** Lists current subscriptions; removed tombstones remain private durable state. */
    fun subscriptions(): List<RepositorySubscription> = snapshot.records
        .asSequence()
        .filterNot(StoredSubscription::removed)
        .map(StoredSubscription::toSubscription)
        .sortedBy(RepositorySubscription::repositoryId)
        .toList()

    /** Enables or disables a current subscription without discarding its authenticated cache. */
    fun setEnabled(repositoryId: String, enabled: Boolean): RepositorySubscription {
        SourceId(repositoryId)
        val record = update { state ->
            val existing = state.records.singleOrNull { it.repositoryId == repositoryId && !it.removed }
                ?: throw RepositorySubscriptionException(RepositorySubscriptionError.SUBSCRIPTION_NOT_FOUND)
            state.copy(records = state.records.filterNot { it.repositoryId == repositoryId } + existing.copy(enabled = enabled))
        }.records.single { it.repositoryId == repositoryId }
        return record.toSubscription()
    }

    /** Tombstones a root while retaining its cache directory and identity binding. */
    fun remove(repositoryId: String): Boolean {
        SourceId(repositoryId)
        val changed = booleanArrayOf(false)
        update { state ->
            val existing = state.records.singleOrNull { it.repositoryId == repositoryId } ?: return@update state
            if (!existing.removed) changed[0] = true
            state.copy(records = state.records.filterNot { it.repositoryId == repositoryId } + existing.copy(
                enabled = false,
                removed = true,
            ))
        }
        return changed[0]
    }

    /** Returns a client only for an enabled, non-removed root. */
    fun client(repositoryId: String): OfficialRepositoryClient? = snapshot.records
        .singleOrNull { it.repositoryId == repositoryId && it.enabled && !it.removed }
        ?.let(::clientFor)

    /** Current enabled clients keyed by immutable repository ID. */
    fun activeClients(): Map<String, OfficialRepositoryClient> = snapshot.records
        .asSequence()
        .filter { it.enabled && !it.removed }
        .associate { it.repositoryId to clientFor(it) }

    /** Re-reads registry state and primes every retained client cache without any network request. */
    fun reload() {
        publish(transaction { it })
    }

    private fun update(change: (SubscriptionState) -> SubscriptionState): SubscriptionState {
        val next = transaction { state ->
            val candidate = change(state).validated()
            writeState(candidate)
            candidate
        }
        publish(next)
        return next
    }

    private fun publish(state: SubscriptionState) {
        snapshot = SubscriptionSnapshot(state.records)
        retainedResolvers = state.records.map { record ->
            val client = clientFor(record)
            // Cache loading is local-only. A corrupt retained cache makes that client's resolver
            // deny all, preserving revocation safety without making unrelated roots unavailable.
            runCatching { client.cached() }
            client.publisherKeys
        }
    }

    private fun clientFor(record: StoredSubscription): OfficialRepositoryClient = clients.computeIfAbsent(record.repositoryId) {
        val cacheDirectory = File(File(directory, CATALOGS_DIRECTORY), record.repositoryId).canonicalFile
        val parent = File(directory, CATALOGS_DIRECTORY).canonicalFile
        require(cacheDirectory.parentFile == parent && parent.parentFile == directory) { "Repository cache path is invalid" }
        OfficialRepositoryClient(record.toRoot(), cacheDirectory, fetcher, clock)
    }

    private fun <T> transaction(block: (SubscriptionState) -> T): T {
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
            } catch (error: RepositorySubscriptionException) {
                throw error
            } catch (error: IOException) {
                throw RepositorySubscriptionException(RepositorySubscriptionError.STORAGE_UNAVAILABLE, error)
            }
        }
    }

    private fun readState(): SubscriptionState {
        if (!stateFile.exists()) return SubscriptionState.empty()
        if (!stateFile.isFile) throw RepositorySubscriptionException(RepositorySubscriptionError.STORAGE_UNAVAILABLE)
        val text = try {
            readSubscriptionState(stateFile, MAX_STATE_BYTES)
        } catch (error: IOException) {
            throw RepositorySubscriptionException(RepositorySubscriptionError.STORAGE_UNAVAILABLE, error)
        }
        return try {
            SubscriptionState.decode(text).validated()
        } catch (error: RepositorySubscriptionException) {
            throw error
        } catch (error: Throwable) {
            throw RepositorySubscriptionException(RepositorySubscriptionError.INVALID_STATE, error)
        }
    }

    private fun writeState(state: SubscriptionState) {
        val encoded = state.encode().toByteArray(StandardCharsets.UTF_8)
        if (encoded.size > MAX_STATE_BYTES) throw RepositorySubscriptionException(RepositorySubscriptionError.STORAGE_UNAVAILABLE)
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
            throw RepositorySubscriptionException(RepositorySubscriptionError.STORAGE_UNAVAILABLE, error)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private data class SubscriptionSnapshot(val records: List<StoredSubscription>) {
        companion object {
            fun empty() = SubscriptionSnapshot(emptyList())
        }
    }

    private data class SubscriptionState(val records: List<StoredSubscription>) {
        fun validated(): SubscriptionState {
            if (records.size > MAX_SUBSCRIPTIONS || records.map(StoredSubscription::repositoryId).toSet().size != records.size) {
                throw RepositorySubscriptionException(RepositorySubscriptionError.INVALID_STATE)
            }
            records.forEach { it.toRoot() }
            if (records.any { left -> records.any { right ->
                    left.repositoryId != right.repositoryId &&
                        (left.indexUrl == right.indexUrl || left.publicKey == right.publicKey)
                } }) {
                throw RepositorySubscriptionException(RepositorySubscriptionError.INVALID_STATE)
            }
            return copy(records = records.sortedBy(StoredSubscription::repositoryId))
        }

        fun encode(): String = buildString {
            append("version:1\n")
            records.sortedBy(StoredSubscription::repositoryId).forEach { record ->
                append("subscription:")
                append(record.repositoryId).append(':')
                append(encodeIndexUrl(record.indexUrl)).append(':')
                append(record.keyId).append(':')
                append(record.publicKey).append(':')
                append(if (record.enabled) '1' else '0').append(':')
                append(if (record.removed) '1' else '0').append('\n')
            }
        }

        companion object {
            fun empty() = SubscriptionState(emptyList())

            fun decode(text: String): SubscriptionState {
                val lines = text.split('\n')
                if (lines.size < 2 || lines.last() != "" || lines.first() != "version:1") {
                    throw RepositorySubscriptionException(RepositorySubscriptionError.INVALID_STATE)
                }
                val records = lines.drop(1).dropLast(1).map { line ->
                    val fields = line.split(':')
                    if (fields.size != 7 || fields[0] != "subscription" || fields[5] !in setOf("0", "1") || fields[6] !in setOf("0", "1")) {
                        throw RepositorySubscriptionException(RepositorySubscriptionError.INVALID_STATE)
                    }
                    StoredSubscription(
                        repositoryId = fields[1],
                        indexUrl = decodeIndexUrl(fields[2]),
                        keyId = fields[3],
                        publicKey = fields[4],
                        enabled = fields[5] == "1",
                        removed = fields[6] == "1",
                    )
                }
                return SubscriptionState(records)
            }
        }
    }

    private data class StoredSubscription(
        val repositoryId: String,
        val indexUrl: String,
        val keyId: String,
        val publicKey: String,
        val enabled: Boolean,
        val removed: Boolean,
    ) {
        fun toRoot(): RepositoryRoot = RepositoryRoot(
            repositoryId = SourceId(repositoryId).value,
            indexUrl = requireHttpsRepositoryUrl(indexUrl).toASCIIString(),
            signingKey = PublisherKey(keyId, decodeCanonicalEd25519PublicKey(publicKey), PublisherTrust.USER_ADDED),
        )

        fun toSubscription(): RepositorySubscription = RepositorySubscription(toRoot(), enabled)

        fun matches(root: RepositoryRoot): Boolean =
            repositoryId == root.repositoryId &&
                indexUrl == root.indexUrl &&
                keyId == root.signingKey.keyId &&
                publicKey == encodeRawKey(root.signingKey.publicKey)

        companion object {
            fun from(root: RepositoryRoot, enabled: Boolean, removed: Boolean) = StoredSubscription(
                repositoryId = root.repositoryId,
                indexUrl = root.indexUrl,
                keyId = root.signingKey.keyId,
                publicKey = encodeRawKey(root.signingKey.publicKey),
                enabled = enabled,
                removed = removed,
            )
        }
    }

    private companion object {
        const val STATE_FILE_NAME = "repository-subscriptions-v1.state"
        const val LOCK_FILE_NAME = "repository-subscriptions-v1.lock"
        const val CATALOGS_DIRECTORY = "repository-catalogs"
        const val MAX_STATE_BYTES = 512 * 1024
        const val MAX_SUBSCRIPTIONS = 64
        val DIRECTORY_LOCKS = ConcurrentHashMap<String, Any>()
    }
}

private const val MAX_SUBSCRIPTION_LINK_LENGTH = 4_512

private fun encodeRawKey(key: ByteArray): String = Base64.getEncoder().encodeToString(key)

private fun encodeIndexUrl(value: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

private fun decodeIndexUrl(value: String): String {
    if (value.isEmpty() || value.length > 5_500 || value.any { it !in SUBSCRIPTION_URL_BASE64_CHARS }) {
        throw RepositorySubscriptionException(RepositorySubscriptionError.INVALID_STATE)
    }
    val decoded = try {
        Base64.getUrlDecoder().decode(value)
    } catch (error: IllegalArgumentException) {
        throw RepositorySubscriptionException(RepositorySubscriptionError.INVALID_STATE, error)
    }
    val text = decoded.toString(StandardCharsets.UTF_8)
    if (encodeIndexUrl(text) != value) throw RepositorySubscriptionException(RepositorySubscriptionError.INVALID_STATE)
    return text
}

private fun readSubscriptionState(file: File, maximumBytes: Int): String = FileInputStream(file).use { input ->
    val output = java.io.ByteArrayOutputStream(minOf(maximumBytes, 16 * 1024))
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size() > maximumBytes - count) throw IOException("Subscription state exceeds its bound")
        output.write(buffer, 0, count)
    }
    output.toString(StandardCharsets.UTF_8.name())
}

private val SUBSCRIPTION_URL_BASE64_CHARS =
    ('A'..'Z').toSet() + ('a'..'z').toSet() + ('0'..'9').toSet() + setOf('-', '_')
