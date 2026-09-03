/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.security

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Browser-derived request state retained only inside one encrypted source/origin partition. */
data class VerifiedBrowserSession(
    val requestCookies: String,
    val userAgent: String,
) {
    init {
        require(requestCookies.isNotBlank()) { "Verified browser cookies are empty" }
        require(requestCookies.toByteArray(StandardCharsets.UTF_8).size <= MAX_COOKIE_BYTES) {
            "Verified browser cookies exceed storage limit"
        }
        require(userAgent.isNotBlank() && '\r' !in userAgent && '\n' !in userAgent) {
            "Verified browser user agent is invalid"
        }
        require(userAgent.toByteArray(StandardCharsets.UTF_8).size <= MAX_USER_AGENT_BYTES) {
            "Verified browser user agent exceeds storage limit"
        }
    }

    companion object {
        internal const val MAX_COOKIE_BYTES = 512 * 1024
        internal const val MAX_USER_AGENT_BYTES = 4 * 1024
    }
}

data class VerifiedBrowserSessionSnapshot(
    val session: VerifiedBrowserSession,
    val cachePartitionId: String,
)
class VerifiedBrowserSessionStore(
    private val credentials: SourceCredentialStore,
) {
    constructor(context: Context) : this(SourceCredentialStore(context))

    fun put(partition: SourceCredentialPartition, session: VerifiedBrowserSession) {
        credentials.put(partition, encode(session))
    }

    fun getSnapshot(partition: SourceCredentialPartition): VerifiedBrowserSessionSnapshot? {
        val encrypted = credentials.getSnapshot(partition) ?: return null
        val session = decode(encrypted.plaintext) ?: run {
            credentials.delete(partition)
            return null
        }
        return VerifiedBrowserSessionSnapshot(session, encrypted.cachePartitionId)
    }

    fun delete(partition: SourceCredentialPartition): Boolean = credentials.delete(partition)

    private fun encode(session: VerifiedBrowserSession): ByteArray {
        val cookies = session.requestCookies.toByteArray(StandardCharsets.UTF_8)
        val userAgent = session.userAgent.toByteArray(StandardCharsets.UTF_8)
        return ByteArrayOutputStream(HEADER_BYTES + cookies.size + userAgent.size).use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(MAGIC)
                data.writeInt(VERSION)
                data.writeInt(cookies.size)
                data.write(cookies)
                data.writeInt(userAgent.size)
                data.write(userAgent)
            }
            output.toByteArray()
        }
    }

    private fun decode(bytes: ByteArray): VerifiedBrowserSession? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
            val cookieSize = input.readInt()
            if (cookieSize !in 1..VerifiedBrowserSession.MAX_COOKIE_BYTES) return null
            val cookies = ByteArray(cookieSize).also(input::readFully)
            val userAgentSize = input.readInt()
            if (userAgentSize !in 1..VerifiedBrowserSession.MAX_USER_AGENT_BYTES) return null
            val userAgent = ByteArray(userAgentSize).also(input::readFully)
            if (input.available() != 0) return null
            VerifiedBrowserSession(
                requestCookies = cookies.toString(StandardCharsets.UTF_8),
                userAgent = userAgent.toString(StandardCharsets.UTF_8),
            )
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val MAGIC = 0x54534253
        const val VERSION = 1
        const val HEADER_BYTES = 16
    }
}
