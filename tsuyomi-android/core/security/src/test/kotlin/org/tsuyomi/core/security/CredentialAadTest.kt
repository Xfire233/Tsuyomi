/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CredentialAadTest {
    private val aead = TestAesGcm()

    @Test
    fun sourceSwapIsRejectedByAdditionalAuthenticatedData() {
        val first = SourceCredentialPartition("source.one", HttpsOrigin("https://one.example"))
        val swapped = SourceCredentialPartition("source.two", HttpsOrigin("https://one.example"))
        val encrypted = aead.encrypt("token=private".encodeToByteArray(), first.aad())

        assertArrayEquals("token=private".encodeToByteArray(), aead.decrypt(encrypted, first.aad()))
        assertAuthenticationFailure { aead.decrypt(encrypted, swapped.aad()) }
    }

    @Test
    fun originSwapIsRejectedByAdditionalAuthenticatedData() {
        val first = SourceCredentialPartition("source.one", HttpsOrigin("https://one.example"))
        val swapped = SourceCredentialPartition("source.one", HttpsOrigin("https://two.example"))
        val encrypted = aead.encrypt("token=private".encodeToByteArray(), first.aad())

        assertAuthenticationFailure { aead.decrypt(encrypted, swapped.aad()) }
    }

    private fun assertAuthenticationFailure(action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected authenticated decryption failure")
        } catch (_: Exception) {
            // GCM authentication rejects changed partition AAD.
        }
    }
}

private class TestAesGcm : AeadPort {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val random = SecureRandom()

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): AeadCiphertext {
        val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return AeadCiphertext(iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(ciphertext: AeadCiphertext, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ciphertext.iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext.ciphertext)
    }
}
