/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.tsuyomi.feature.browse.BrowseUiState
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.source.extensionmanager.OfficialRepositoryClient
import org.tsuyomi.source.extensionmanager.PublisherKey
import org.tsuyomi.source.extensionmanager.PublisherTrust
import org.tsuyomi.source.extensionmanager.RepositoryFetcher
import org.tsuyomi.source.extensionmanager.RepositoryRoot
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
internal class SourceInstallControllerInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun remoteWritebackRequiresCredentialAndFailsClosedWhenCredentialDisappears() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val install = SourceInstallController(context, library)
        install.restoreInstalled()

        assertFalse(install.remoteAddCredentialReady())
        assertFalse(install.setRemoteAddWritebackEnabled(true))
        putCredential(sourceId)
        assertTrue(install.remoteAddCredentialReady())
        assertTrue(install.setRemoteAddWritebackEnabled(true))

        File(context.noBackupFilesDir, "source-credentials").deleteRecursively()
        assertFalse(requireNotNull(install.remotePolicy()).addWritebackEnabled)
        assertFalse(requireNotNull(library.sourceRemotePolicy(sourceId)).addWritebackEnabled)
        assertEquals(packageInfo.packageSha256, install.activePackage?.packageSha256)
    }

    @Test
    fun invalidInstalledPackageIsMarkedUnavailableDuringRestore() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val before = requireNotNull(library.sourceAvailability(sourceId))
        File(context.noBackupFilesDir, "extensions/active/$sourceId.hxp").writeText("tampered")

        val restore = SourceInstallController(context, library)
        restore.restoreInstalled()

        assertTrue(restore.state is BrowseUiState.Failure)
        assertEquals(null, restore.activePackage)
        val after = requireNotNull(library.sourceAvailability(sourceId))
        assertFalse(after.available)
        assertEquals(before.generation + 1, after.generation)
    }
    @Test
    fun restorePreservesEveryMatchingWritebackReceipt() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
        assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
        assertTrue(library.setRemoveWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
        assertTrue(library.setMoveWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))

        SourceInstallController(context, library).restoreInstalled()

        val restored = requireNotNull(library.sourceRemotePolicy(sourceId))
        assertTrue(restored.addWritebackEnabled)
        assertTrue(restored.removeWritebackEnabled)
        assertTrue(restored.moveWritebackEnabled)
    }

    @Test
    fun sourceSelectionCannotHideAPendingInstallationApproval() = runBlocking {
        val active = installFixture()
        val install = SourceInstallController(context, library)
        install.restoreInstalled()
        val replacement = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("repository/replacement-candidate.json").bufferedReader().use { it.readText() },
        )
        val candidate = File(context.cacheDir, "source-selection-update.hxp")
        ZipInputStream(context.assets.open("wenku8-fixture.hxp")).use { input ->
            ZipOutputStream(candidate.outputStream()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val bytes = when (entry.name) {
                        "manifest.json" -> replacement.getJSONObject("manifest").toString().toByteArray(Charsets.UTF_8)
                        "signature.ed25519" -> Base64.getDecoder().decode(replacement.getString("signature"))
                        else -> input.readBytes()
                    }
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        install.prepare(Uri.fromFile(candidate), context.contentResolver)
        candidate.delete()

        assertTrue(install.state is BrowseUiState.Approval)
        assertEquals(null, install.activateInstalledSource(active.manifest.sourceId.value))
        assertTrue(install.state is BrowseUiState.Approval)
        install.approve(allowDowngrade = false)
        assertEquals("0.2.31", install.activePackage?.manifest?.version?.original)
        assertTrue(install.state is BrowseUiState.Installed)
    }

    @Test
    fun acceptedRevocationSurvivesCallerCancellationWithoutDeletingSourceOrCredentials() = runBlocking {
        val active = installFixture()
        val sourceId = active.manifest.sourceId.value
        putCredential(sourceId)
        val archive = File(context.noBackupFilesDir, "extensions/active/$sourceId.hxp")
        val originalBytes = archive.readBytes()
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val cache = File(context.cacheDir, "repository-cancellation-regression")
        cache.deleteRecursively()
        val envelope = InstrumentationRegistry.getInstrumentation().context.assets
            .open("repository/revoked-fixture.json").use { it.readBytes() }
        val client = OfficialRepositoryClient(
            root = RepositoryRoot(
                "org.tsuyomi.extensions",
                "https://repository.example/index.json",
                PublisherKey(
                    "repository-cancellation-test",
                    Base64.getDecoder().decode("4W0lStImZafGes+iir7GZroORHjdlbC5HiTDbJgTtwU="),
                    PublisherTrust.BUILT_IN_OFFICIAL,
                ),
            ),
            storageDirectory = cache,
            fetcher = object : RepositoryFetcher {
                override fun fetch(url: String, maxBytes: Int): ByteArray {
                    entered.complete(Unit)
                    check(release.await(10, TimeUnit.SECONDS))
                    return envelope
                }
            },
            clock = { Instant.parse("2026-09-11T12:00:00Z") },
        )
        val install = SourceInstallController(context, library, client)
        install.restoreInstalled()
        assertTrue(requireNotNull(library.sourceAvailability(sourceId)).available)
        var executedSearches = 0
        val keys = OfficialRepositoryConfiguration.publisherKeys(client)
        val flow = SourceFlowController(
            context,
            library,
            SourceFlowSnapshotStore((context.applicationContext as TsuyomiApplication).preferencesDataStore),
            openSession = { FakeSession(searchResult = { _, _ ->
                executedSearches += 1
                listOf(summary(sourceId, "revocation-regression", "Retained source"))
            }) },
            isPackageTrusted = { OfficialRepositoryConfiguration.isTrusted(it, keys) },
        )
        flow.open(active)
        flow.updateQuery("before revocation")
        flow.search()
        assertTrue(flow.searchState is org.tsuyomi.feature.search.SearchResultState.Results)
        val refresh = launch(Dispatchers.Default) { install.catalog.refresh() }
        try {
            withTimeout(10_000) { entered.await() }
            refresh.cancel()
            release.countDown()
            withTimeout(10_000) { refresh.join() }

            assertEquals(null, install.activePackage)
            assertFalse(requireNotNull(library.sourceAvailability(sourceId)).available)
            flow.updateQuery("after revocation")
            flow.search()
            assertTrue(flow.searchState is org.tsuyomi.feature.search.SearchResultState.Failure)
            assertEquals(1, executedSearches)
            assertTrue(originalBytes.contentEquals(archive.readBytes()))
            assertTrue(
                VerifiedBrowserSessionStore(context).getSnapshot(
                    SourceCredentialPartition(sourceId, HttpsOrigin("https://www.wenku8.net")),
                ) != null,
            )
        } finally {
            release.countDown()
            refresh.cancel()
            refresh.join()
            flow.close()
            cache.deleteRecursively()
        }
    }

}
