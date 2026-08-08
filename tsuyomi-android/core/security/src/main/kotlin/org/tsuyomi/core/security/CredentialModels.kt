/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.security

import java.net.URI
import java.util.Locale

const val SOURCE_CREDENTIAL_KEY_ALIAS = "org.tsuyomi.android.source-credentials.v1"
internal const val CREDENTIAL_SCHEMA_VERSION = 1
internal const val CREDENTIAL_KEY_VERSION = 1
internal const val GCM_IV_BYTES = 12

/** A source partition has no ambient/global credential namespace. */
data class SourceCredentialPartition(
    val sourceId: String,
    val origin: HttpsOrigin,
) {
    init {
        require(SOURCE_ID_PATTERN.matches(sourceId)) { "Invalid source identity" }
    }

    private companion object {
        val SOURCE_ID_PATTERN = Regex("^[a-z0-9](?:[a-z0-9.-]{0,126}[a-z0-9])?$")
    }
}

/** Canonical, path-free HTTPS origin. Credential AAD binds this exact canonical origin. */
class HttpsOrigin private constructor(val value: String) {
    override fun equals(other: Any?): Boolean = other is HttpsOrigin && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value

    companion object {
        fun parse(raw: String): HttpsOrigin {
            val uri = try {
                URI(raw)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid HTTPS origin")
            }
            require(uri.scheme.equals("https", ignoreCase = true)) { "Origin must use HTTPS" }
            require(uri.userInfo == null) { "Origin cannot include credentials" }
            require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") { "Origin cannot include a path" }
            require(uri.rawQuery == null && uri.rawFragment == null) { "Origin cannot include query or fragment" }
            val host = requireNotNull(uri.host) { "Origin requires a host" }.lowercase(Locale.ROOT)
            val port = uri.port
            require(port in -1..65535) { "Origin port is invalid" }
            val authority = if (port == -1 || port == 443) host else "$host:$port"
            return HttpsOrigin("https://$authority")
        }
    }
}

enum class CredentialStorageError {
    UNAVAILABLE,
    CORRUPT_OR_UNAUTHENTICATED,
    DELETE_FAILED,
}

/** Messages intentionally contain no secret, origin, source ID, cipher, or platform exception. */
class CredentialStorageException(val error: CredentialStorageError) : Exception(
    when (error) {
        CredentialStorageError.UNAVAILABLE -> "Credential storage is unavailable"
        CredentialStorageError.CORRUPT_OR_UNAUTHENTICATED -> "Credential record is unavailable"
        CredentialStorageError.DELETE_FAILED -> "Credential record could not be removed"
    },
)

data class AeadCiphertext(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

/** Narrow port keeps credential partitioning testable without an AndroidKeyStore test double. */
interface AeadPort {
    fun encrypt(plaintext: ByteArray, aad: ByteArray): AeadCiphertext
    fun decrypt(ciphertext: AeadCiphertext, aad: ByteArray): ByteArray
}
