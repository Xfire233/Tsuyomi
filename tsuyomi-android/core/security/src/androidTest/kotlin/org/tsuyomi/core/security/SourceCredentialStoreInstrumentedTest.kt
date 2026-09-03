/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceCredentialStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val first = SourceCredentialPartition("fixture.source", HttpsOrigin("https://one.example"))
    private val second = SourceCredentialPartition("fixture.source", HttpsOrigin("https://two.example"))
    private val store = SourceCredentialStore(context)

    @After
    fun cleanUp() {
        store.delete(first)
        store.delete(second)
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            if (containsAlias(SOURCE_CREDENTIAL_KEY_ALIAS)) deleteEntry(SOURCE_CREDENTIAL_KEY_ALIAS)
        }
    }

    @Test
    fun generatedAesGcmIvsAreTwelveBytesAndUnique() {
        val aead = AndroidKeyStoreAesGcm()
        val aad = first.aad()
        val ivs = (1..64).map { aead.encrypt(byteArrayOf(1), aad).iv }

        assertTrue(ivs.all { it.size == GCM_IV_BYTES })
        assertEquals(ivs.size, ivs.map { it.joinToString(separator = ",") }.toSet().size)
    }

    @Test
    fun copiedCiphertextCannotMoveToAnotherOriginAndOnlyTargetIsCleared() {
        val firstSecret = "session=first-secret".toByteArray(StandardCharsets.UTF_8)
        val secondSecret = "session=second-secret".toByteArray(StandardCharsets.UTF_8)
        store.put(first, firstSecret)
        val firstRecord = requireNotNull(store.noBackupDirectory().listFiles())
            .single { it.isFile && it.name.endsWith(".record") }
        store.put(second, secondSecret)
        val secondRecord = requireNotNull(store.noBackupDirectory().listFiles())
            .single { it.isFile && it.name.endsWith(".record") && it != firstRecord }
        secondRecord.writeBytes(firstRecord.readBytes())

        val failure = assertStorageFailure { store.get(second) }
        assertEquals(CredentialStorageError.CORRUPT_OR_UNAUTHENTICATED, failure.error)

        assertArrayEquals(firstSecret, store.get(first))
        assertNull(store.get(second))
    }

    @Test
    fun unavailableAeadDoesNotInvalidateExistingRecord() {
        val aead = FaultInjectingAead()
        val faultInjectingStore = SourceCredentialStore(context, aead)
        faultInjectingStore.put(first, byteArrayOf())
        val record = requireNotNull(faultInjectingStore.noBackupDirectory().listFiles())
            .single { it.isFile && it.name.endsWith(".record") }
        aead.decryptionFailure = CredentialStorageException(CredentialStorageError.UNAVAILABLE)

        val failure = assertStorageFailure { faultInjectingStore.get(first) }

        assertEquals(CredentialStorageError.UNAVAILABLE, failure.error)
        assertTrue(record.exists())
    }

    @Test
    fun deletingOnePartitionDoesNotAffectAnotherAndRecordsUseNoBackupDirectory() {
        val firstSecret = "session=first-secret".toByteArray(StandardCharsets.UTF_8)
        val secondSecret = "session=second-secret".toByteArray(StandardCharsets.UTF_8)
        store.put(first, firstSecret)
        store.put(second, secondSecret)

        assertTrue(store.noBackupDirectory().canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath))
        assertTrue(store.delete(first))
        assertNull(store.get(first))
        assertArrayEquals(secondSecret, store.get(second))

        val ciphertext = store.noBackupDirectory().listFiles()
            ?.filter { it.isFile && it.name.endsWith(".record") }
            ?.flatMap { it.readBytes().asIterable() }
            ?.toByteArray()
            ?: byteArrayOf()
        assertFalse(String(ciphertext, StandardCharsets.ISO_8859_1).contains("session=second-secret"))
    }
    @Test
    fun snapshot_cache_partition_is_stable_per_record_and_changes_on_write() {
        val secret = "session=opaque-secret".toByteArray(StandardCharsets.UTF_8)
        store.put(first, secret)

        val initial = requireNotNull(store.getSnapshot(first))
        assertArrayEquals(secret, initial.plaintext)
        assertEquals(64, initial.cachePartitionId.length)
        assertFalse(initial.cachePartitionId.contains("opaque-secret"))
        assertEquals(initial.cachePartitionId, requireNotNull(store.getSnapshot(first)).cachePartitionId)

        store.put(first, secret)

        assertNotEquals(initial.cachePartitionId, requireNotNull(store.getSnapshot(first)).cachePartitionId)
    }

    @Test
    fun verified_browser_session_round_trips_inside_one_encrypted_partition() {
        val sessions = VerifiedBrowserSessionStore(store)
        sessions.put(first, VerifiedBrowserSession("session=accepted", "fixture-webview-agent/1"))

        val snapshot = requireNotNull(sessions.getSnapshot(first))

        assertEquals("session=accepted", snapshot.session.requestCookies)
        assertEquals("fixture-webview-agent/1", snapshot.session.userAgent)
        assertEquals(64, snapshot.cachePartitionId.length)
    }

    @Test
    fun untyped_legacy_payload_fails_closed_and_is_cleared() {
        store.put(first, "session=legacy".encodeToByteArray())

        assertNull(VerifiedBrowserSessionStore(store).getSnapshot(first))
        assertNull(store.get(first))
    }

}

    private fun assertStorageFailure(action: () -> Unit): CredentialStorageException = try {
        action()
        throw AssertionError("Expected credential storage failure")
    } catch (failure: CredentialStorageException) {
        failure
    }

    private class FaultInjectingAead : AeadPort {
        var decryptionFailure: CredentialStorageException? = null

        override fun encrypt(plaintext: ByteArray, aad: ByteArray): AeadCiphertext = AeadCiphertext(
            iv = ByteArray(GCM_IV_BYTES),
            ciphertext = ByteArray(16),
        )

        override fun decrypt(ciphertext: AeadCiphertext, aad: ByteArray): ByteArray {
            decryptionFailure?.let { throw it }
            return byteArrayOf()
        }
    }
