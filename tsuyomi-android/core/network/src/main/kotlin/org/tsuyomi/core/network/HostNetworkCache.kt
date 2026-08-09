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
}

class InMemoryHostNetworkCache : HostNetworkCache {
    private val responses = ConcurrentHashMap<HostNetworkCacheKey, SourceNetworkResponse>()

    override fun get(key: HostNetworkCacheKey): SourceNetworkResponse? = responses[key]

    override fun put(key: HostNetworkCacheKey, response: SourceNetworkResponse) {
        responses[key] = response
    }
}

/**
 * Private, bounded process-persistent cache. [partition] is an opaque host-owned revision that
 * prevents anonymous or superseded credential responses from satisfying the current session.
 */
class FileHostNetworkCache(
    private val files: QuotaFileStore,
    private val partition: String = DEFAULT_PARTITION,
) : HostNetworkCache {
    init {
        require(partition.isNotBlank() && partition.length <= MAX_PARTITION_BYTES && '\u0000' !in partition)
    }
    override fun get(key: HostNetworkCacheKey): SourceNetworkResponse? {
        val path = path(key)
        val bytes = runCatching { files.read(path) }.getOrNull() ?: return null
        return runCatching { decode(bytes, key) }
            .getOrElse {
                files.delete(path)
                null
            }
    }

    override fun put(key: HostNetworkCacheKey, response: SourceNetworkResponse) {
        if (response.text == null || response.bytes != null) return
        runCatching { files.write(path(key), encode(key, response)) }
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

    private fun decode(bytes: ByteArray, expected: HostNetworkCacheKey): SourceNetworkResponse {
        require(bytes.size in 1..MAX_ENTRY_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readInt() == MAGIC && data.readInt() == FORMAT_VERSION)
            val stored = HostNetworkCacheKey(
                sourceId = data.readString(MAX_METADATA_BYTES),
                extensionVersion = data.readString(MAX_METADATA_BYTES),
                identity = data.readString(MAX_IDENTITY_BYTES),
                decode = DecodeMode.valueOf(data.readString(MAX_METADATA_BYTES)),
            )
            require(stored == expected)
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
            return SourceNetworkResponse(
                status = status,
                finalUrl = finalUrl,
                headers = headers,
                text = text,
                bytes = null,
                decodeUsed = decodeUsed,
                cacheState = NetworkCacheState.FRESH,
                diagnosticId = diagnosticId,
            )
        }
    }

    private fun path(key: HostNetworkCacheKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "${key.sourceId}\u0000${key.extensionVersion}\u0000$partition\u0000${key.identity}\u0000${key.decode.name}"
                .encodeToByteArray(),
        )
        return "responses/${digest.joinToString("") { "%02x".format(it) }}.bin"
    }

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
