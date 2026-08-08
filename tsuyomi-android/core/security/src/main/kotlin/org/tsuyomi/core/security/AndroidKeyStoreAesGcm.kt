/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AndroidKeyStore-backed AES-256-GCM implementation fixed by ADR 0018. */
class AndroidKeyStoreAesGcm(
    private val alias: String = SOURCE_CREDENTIAL_KEY_ALIAS,
    private val random: SecureRandom = SecureRandom(),
) : AeadPort {
    override fun encrypt(plaintext: ByteArray, aad: ByteArray): AeadCiphertext = try {
        val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        AeadCiphertext(iv, cipher.doFinal(plaintext))
    } catch (_: Exception) {
        throw CredentialStorageException(CredentialStorageError.UNAVAILABLE)
    }

    override fun decrypt(ciphertext: AeadCiphertext, aad: ByteArray): ByteArray {
        if (ciphertext.iv.size != GCM_IV_BYTES || ciphertext.ciphertext.size < GCM_TAG_BYTES) {
            throw CredentialStorageException(CredentialStorageError.CORRUPT_OR_UNAUTHENTICATED)
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, ciphertext.iv))
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext.ciphertext)
        } catch (_: AEADBadTagException) {
            throw CredentialStorageException(CredentialStorageError.CORRUPT_OR_UNAUTHENTICATED)
        } catch (_: Exception) {
            throw CredentialStorageException(CredentialStorageError.UNAVAILABLE)
        }
    }

    @Synchronized
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    // SecureRandom supplies a distinct 96-bit GCM IV for each record.
                    .setRandomizedEncryptionRequired(false)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val GCM_TAG_BYTES = 16
    }
}
