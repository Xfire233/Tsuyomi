/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.erdtman.jcs.JsonCanonicalizer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.tsuyomi.feature.browse.BrowseUiState
import org.tsuyomi.source.extensionmanager.PackageTrustRegistry
import org.tsuyomi.source.extensionmanager.RepositoryFetcher
import org.tsuyomi.source.extensionmanager.RepositorySubscriptionRegistry

@RunWith(AndroidJUnit4::class)
internal class RepositorySubscriptionInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun subscriptionRequiresConsentInstallsSignedPackageAndRemovalRetainsExecutionAndSecurityHistory() = runBlocking {
        val archive = assembleSignedSwitchOverlay("source-user-consent")
        val directory = File(context.noBackupFilesDir, "subscription-instrumented")
        directory.deleteRecursively()
        val publisher = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("repository/source-user-consent.json").bufferedReader().use { it.readText() })
        val key = Ed25519PrivateKeyParameters(ByteArray(32) { (it + 121).toByte() }, 0)
        val now = Instant.now()
        val bytes = archive.readBytes()
        var catalog = signedCatalog(bytes, publisher, key, now, 1, false)
        val requests = mutableListOf<String>()
        val fetcher = object : RepositoryFetcher {
            override fun fetch(url: String, maxBytes: Int): ByteArray {
                requests += url
                return when (url) {
                    INDEX -> catalog
                    PACKAGE -> bytes
                    else -> error("Unexpected repository request")
                }.also { check(it.size <= maxBytes) }
            }
        }
        val roots = File(directory, "roots")
        val grants = File(directory, "grants")
        val subscriptions = RepositorySubscriptionRegistry(roots, fetcher) { now }
        val trust = PackageTrustRegistry(grants)
        val install = SourceInstallController(context, library, null, subscriptions, trust)
        val link = "$INDEX#repositoryId=$REPOSITORY&keyId=consent-test-root&publicKey=${Base64.getEncoder().encodeToString(key.generatePublicKey().encoded)}"
        try {
            install.catalog.inspectSubscription(link)
            assertTrue(install.catalog.state.subscription?.rootFingerprint != null)
            assertTrue(requests.isEmpty())
            assertTrue(subscriptions.subscriptions().isEmpty())
            install.catalog.cancelSubscription()
            assertTrue(subscriptions.subscriptions().isEmpty())
            install.catalog.inspectSubscription(link)
            install.catalog.confirmSubscription()
            assertEquals(listOf(INDEX), requests)
            assertEquals(REPOSITORY, install.catalog.state.items.single().repositoryId)
            assertFalse(install.catalog.state.items.single().official)
            assertEquals(null, install.activePackage)
            install.catalog.install(SOURCE, REPOSITORY)
            assertTrue((install.state as BrowseUiState.Approval).requiresNonOfficialConsent)
            install.approve(false)
            assertEquals(null, install.activePackage)
            install.approve(false, allowNonOfficial = true)
            val active = requireNotNull(install.activePackage)
            assertTrue(trust.isApproved(active))
            install.catalog.removeSubscription(REPOSITORY)
            assertTrue(install.catalog.state.items.isEmpty())
            val restoredRoots = RepositorySubscriptionRegistry(roots, fetcher) { now }
            val restoredTrust = PackageTrustRegistry(grants)
            val restored = SourceInstallController(context, library, null, restoredRoots, restoredTrust)
            restored.restoreInstalled()
            assertEquals(active.packageSha256, restored.activePackage?.packageSha256)
            assertTrue(restoredRoots.subscriptions().isEmpty())
            restored.catalog.inspectSubscription(link)
            restored.catalog.confirmSubscription()
            catalog = signedCatalog(bytes, publisher, key, now, 2, true)
            restored.catalog.refresh()
            assertEquals(null, restored.activePackage)
            assertFalse(requireNotNull(library.sourceAvailability(SOURCE)).available)
            restored.catalog.removeSubscription(REPOSITORY)
            catalog = signedCatalog(bytes, publisher, key, now, 1, false)
            restored.catalog.inspectSubscription(link)
            restored.catalog.confirmSubscription()
            assertEquals(null, restored.activePackage)
            assertTrue(restoredRoots.publisherKeys.isRevokedPackage(active.packageSha256))
            assertTrue(File(context.noBackupFilesDir, "extensions/active/$SOURCE.hxp").isFile)
        } finally {
            archive.delete()
            directory.deleteRecursively()
        }
    }

    private fun signedCatalog(
        archive: ByteArray,
        descriptor: JSONObject,
        key: Ed25519PrivateKeyParameters,
        now: Instant,
        sequence: Int,
        revoked: Boolean,
    ): ByteArray {
        val manifest = descriptor.getJSONObject("manifest")
        val publisher = descriptor.getJSONObject("provenance").getJSONObject("publisher")
        val publicKey = publisher.getString("publicKeyBase64")
        val signed = JSONObject()
            .put("repositoryId", REPOSITORY)
            .put("sequence", sequence)
            .put("issuedAt", now.minusSeconds(1).toString())
            .put("expiresAt", now.plusSeconds(86400).toString())
            .put("publishers", JSONArray().put(JSONObject()
                .put("keyId", publisher.getString("keyId"))
                .put("publicKey", publicKey)
                .put("fingerprint", digest(Base64.getDecoder().decode(publicKey)))))
            .put("packages", JSONArray().put(JSONObject()
                .put("id", SOURCE).put("name", "非官方测试来源")
                .put("version", manifest.getString("version"))
                .put("summary", "Signed isolated repository replay")
                .put("language", "JavaScript").put("license", "Apache-2.0")
                .put("sourceUrl", "https://repository.example/source")
                .put("sourceRevision", "0123456789abcdef0123456789abcdef01234567")
                .put("downloadUrl", PACKAGE).put("size", archive.size).put("sha256", digest(archive))
                .put("hostApi", manifest.getJSONObject("hostApi"))
                .put("publisherKeyId", publisher.getString("keyId"))))
            .put("revocations", JSONObject()
                .put("publisherFingerprints", JSONArray())
                .put("packageDigests", if (revoked) JSONArray().put(digest(archive)) else JSONArray()))
        val message = "tsuyomi-repository-v1\u0000".toByteArray(Charsets.US_ASCII) + JsonCanonicalizer(signed.toString()).encodedUTF8
        val signature = Ed25519Signer().apply { init(true, key); update(message, 0, message.size) }.generateSignature()
        return JSONObject().put("format", "tsuyomi-repository").put("version", 1)
            .put("keyId", "consent-test-root").put("signed", signed)
            .put("signature", Base64.getEncoder().encodeToString(signature)).toString().toByteArray(Charsets.UTF_8)
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val REPOSITORY = "org.tsuyomi.consent-repository"
        const val SOURCE = "org.tsuyomi.nonofficial-test"
        const val INDEX = "https://repository.example/index-v1.json"
        const val PACKAGE = "https://repository.example/source.hxp"
    }
}
