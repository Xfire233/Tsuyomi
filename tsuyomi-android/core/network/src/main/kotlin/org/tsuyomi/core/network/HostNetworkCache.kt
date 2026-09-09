/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.NetworkCacheState
import org.tsuyomi.shared.sourcecontract.SourceNetworkResponse

data class HostNetworkCacheKey(
    val sourceId: String,
    val extensionVersion: String,
    val identity: String,
    val decode: DecodeMode,
)

interface HostNetworkCache {
    fun get(key: HostNetworkCacheKey): SourceNetworkResponse?
    fun put(key: HostNetworkCacheKey, response: SourceNetworkResponse)
    fun remove(key: HostNetworkCacheKey)
}

class InMemoryHostNetworkCache : HostNetworkCache {
    private val responses = ConcurrentHashMap<Lookup, SourceNetworkResponse>()

    override fun get(key: HostNetworkCacheKey): SourceNetworkResponse? = responses[lookup(key)]

    override fun put(key: HostNetworkCacheKey, response: SourceNetworkResponse) {
        responses[lookup(key)] = response
    }

    override fun remove(key: HostNetworkCacheKey) {
        responses.remove(lookup(key))
    }

    private data class Lookup(val sourceId: String, val identity: String, val decode: DecodeMode)

    private fun lookup(key: HostNetworkCacheKey) = Lookup(key.sourceId, key.identity, key.decode)
}

/**
 * Private, bounded process-persistent cache. [partition] is an opaque host-owned revision that
 * prevents anonymous or superseded credential responses from satisfying the current session.
 * Extension version is stored as metadata only; lookup is source, partition, identity and decode.
 */
class FileHostNetworkCache(
    private val files: QuotaFileStore,
    private val partition: String = DEFAULT_PARTITION,
) : HostNetworkCache {
    init {
        require(partition.isNotBlank() && partition.length <= MAX_PARTITION_BYTES && '\u0000' !in partition)
    }
    override fun get(key: HostNetworkCacheKey): SourceNetworkResponse? {
        val canonical = path(key)
        readDecoded(canonical, key, deleteIfCorrupt = true)?.response?.let { return it }
        for (entry in files.entries()) {
            if (entry.relativePath == canonical) continue
            if (!entry.relativePath.startsWith("responses/") || !entry.relativePath.endsWith(".bin")) continue
            val decoded = readDecoded(entry.relativePath, key, deleteIfCorrupt = true) ?: continue
            if (entry.relativePath != legacyVersionedPath(decoded.stored)) continue
            runCatching { files.write(canonical, encode(key, decoded.response)) }
            runCatching { files.delete(entry.relativePath) }
            return decoded.response
        }
        return null
    }

    override fun put(key: HostNetworkCacheKey, response: SourceNetworkResponse) {
        if (response.text == null || response.bytes != null) return
        runCatching { files.write(path(key), encode(key, response)) }
    }

    override fun remove(key: HostNetworkCacheKey) {
        runCatching { files.delete(path(key)) }
    }

    private fun readDecoded(
        path: String,
        key: HostNetworkCacheKey,
        deleteIfCorrupt: Boolean,
    ): DecodedEntry? {
        val bytes = runCatching { files.read(path) }.getOrNull() ?: return null
        return try {
            decode(bytes, key)
        } catch (_: Throwable) {
            if (deleteIfCorrupt) files.delete(path)
            null
        }
    }

    private fun encode(key: HostNetworkCacheKey, response: SourceNetworkResponse): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(FORMAT_VERSION)
            data.writeString(key.sourceId)
            data.writeString(key.extensionVersion)
            data.writeString(key.identity)
            data.writeString(key.decode.name)
            data.writeInt(response.status)
            data.writeString(response.finalUrl)
            data.writeInt(response.headers.size)
            response.headers.toSortedMap().forEach { (name, value) ->
                data.writeString(name)
                data.writeString(value)
            }
            data.writeString(requireNotNull(response.text))
            data.writeString(response.decodeUsed.name)
            data.writeString(response.diagnosticId)
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray, expected: HostNetworkCacheKey): DecodedEntry? {
        require(bytes.size in 1..MAX_ENTRY_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readInt() == MAGIC && data.readInt() == FORMAT_VERSION)
            val stored = HostNetworkCacheKey(
                sourceId = data.readString(MAX_METADATA_BYTES),
                extensionVersion = data.readString(MAX_METADATA_BYTES),
                identity = data.readString(MAX_IDENTITY_BYTES),
                decode = DecodeMode.valueOf(data.readString(MAX_METADATA_BYTES)),
            )
            if (
                stored.sourceId != expected.sourceId ||
                stored.identity != expected.identity ||
                stored.decode != expected.decode
            ) {
                return null
            }
            val status = data.readInt()
            val finalUrl = data.readString(MAX_URL_BYTES)
            val headerCount = data.readInt()
            require(headerCount in 0..32)
            val headers = buildMap {
                repeat(headerCount) {
                    put(data.readString(MAX_METADATA_BYTES), data.readString(MAX_HEADER_BYTES))
                }
            }
            val text = data.readString(MAX_TEXT_BYTES)
            val decodeUsed = DecodeMode.valueOf(data.readString(MAX_METADATA_BYTES))
            val diagnosticId = data.readString(MAX_METADATA_BYTES)
            require(data.available() == 0)
            return DecodedEntry(
                stored = stored,
                response = SourceNetworkResponse(
                    status = status,
                    finalUrl = finalUrl,
                    headers = headers,
                    text = text,
                    bytes = null,
                    decodeUsed = decodeUsed,
                    cacheState = NetworkCacheState.FRESH,
                    diagnosticId = diagnosticId,
                ),
            )
        }
    }

    private fun path(key: HostNetworkCacheKey): String = hashedPath(
        "${key.sourceId}\u0000$partition\u0000${key.identity}\u0000${key.decode.name}",
    )

    private fun legacyVersionedPath(key: HostNetworkCacheKey): String = hashedPath(
        "${key.sourceId}\u0000${key.extensionVersion}\u0000$partition\u0000${key.identity}\u0000${key.decode.name}",
    )

    private fun hashedPath(material: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(material.encodeToByteArray())
        return "responses/${digest.joinToString("") { "%02x".format(it) }}.bin"
    }

    private data class DecodedEntry(val stored: HostNetworkCacheKey, val response: SourceNetworkResponse)

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.encodeToByteArray()
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(maxBytes: Int): String {
        val size = readInt()
        require(size in 0..maxBytes && size <= available())
        return ByteArray(size).also(::readFully).decodeToString(throwOnInvalidSequence = true)
    }

    private companion object {
        const val MAGIC = 0x54535943
        const val FORMAT_VERSION = 1
        const val MAX_ENTRY_BYTES = 17 * 1024 * 1024
        const val MAX_TEXT_BYTES = 16 * 1024 * 1024
        const val MAX_IDENTITY_BYTES = 8 * 1024
        const val MAX_URL_BYTES = 8 * 1024
        const val MAX_HEADER_BYTES = 64 * 1024
        const val MAX_PARTITION_BYTES = 1024
        const val DEFAULT_PARTITION = "anonymous"
        const val MAX_METADATA_BYTES = 1024
    }
}
