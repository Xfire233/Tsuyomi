/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.feature.browse.BrowseUiState
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.core.security.VerifiedBrowserSession
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.source.extensionmanager.SourceExtensionClient
import org.tsuyomi.shared.sourcecontract.HttpsOrigin

/** Installs the signed Wenku8 fixture into an explicitly prepared development APK for live WebView verification. */
@RunWith(AndroidJUnit4::class)
class LiveWenku8VerificationSetupInstrumentedTest {
    @Test
    fun installsSignedSourceWithoutCleaningAfterward() = runBlocking {
        assumeLiveSetup()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Phase2SourceGateway.clearLiveValidationMode(context)
        File(context.noBackupFilesDir, "extensions").deleteRecursively()
        File(context.noBackupFilesDir, "source-credentials").deleteRecursively()
        File(context.cacheDir, "source-network-cache").deleteRecursively()
        File(context.cacheDir, "hxp-staging").deleteRecursively()
        val fixture = File(context.cacheDir, "wenku8-live-probe.hxp")
        context.assets.open("wenku8-fixture.hxp").use { input ->
            fixture.outputStream().use(input::copyTo)
        }
        val application = context.applicationContext as TsuyomiApplication
        val controller = SourceInstallController(context, application.libraryRepository)
        controller.prepare(Uri.fromFile(fixture), context.contentResolver)
        check(controller.state is BrowseUiState.Approval) { "Signed Wenku8 fixture was not prepared" }
        controller.approve(allowDowngrade = false)
        assertTrue(controller.state is BrowseUiState.Installed)
    }

    @Test
    fun updatesSignedSourceWithoutClearingVerifiedSession() = runBlocking {
        assumeLiveSetup()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val partition = SourceCredentialPartition(SOURCE_ID, ORIGIN)
        val sessions = VerifiedBrowserSessionStore(context)
        val before = requireNotNull(sessions.getSnapshot(partition)).session
        val fixture = File(context.cacheDir, "wenku8-live-update.hxp")
        context.assets.open("wenku8-fixture.hxp").use { input ->
            fixture.outputStream().use(input::copyTo)
        }
        val application = context.applicationContext as TsuyomiApplication
        val controller = SourceInstallController(context, application.libraryRepository)
        controller.restoreInstalled()
        controller.prepare(Uri.fromFile(fixture), context.contentResolver)
        check(controller.state is BrowseUiState.Approval) { "Updated Wenku8 fixture was not prepared" }
        val candidateVersion = (controller.state as BrowseUiState.Approval).version
        controller.approve(allowDowngrade = false)

        assertTrue(controller.state is BrowseUiState.Installed)
        assertEquals(candidateVersion, controller.activePackage?.manifest?.version?.original)
        val restored = SourceInstallController(context, application.libraryRepository)
        restored.restoreInstalled()
        assertEquals(candidateVersion, restored.activePackage?.manifest?.version?.original)
        assertEquals(before, requireNotNull(sessions.getSnapshot(partition)).session)
    }

    @Test
    fun persistsCurrentControlledSessionForUnattendedValidation() {
        assumeLiveSetup()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val partition = SourceCredentialPartition(SOURCE_ID, ORIGIN)
        val rawCookie = requireNotNull(CookieManager.getInstance().getCookie(ORIGIN.canonical)) {
            "No current Wenku8 controlled-WebView session is available"
        }
        val session = VerifiedBrowserSession(rawCookie, WebSettings.getDefaultUserAgent(context))
        val sessions = VerifiedBrowserSessionStore(context)
        sessions.put(partition, session)

        assertEquals(session, requireNotNull(sessions.getSnapshot(partition)).session)
    }

    @Test
    fun armsPriorFailedSearchForVisibleJourney() {
        assumeLiveSetup()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Phase2SourceGateway.resetOperationCounts()
        Phase2SourceGateway.preparePriorFailedSearchForLiveContinuation(context, PUBLIC_SEARCH_URL)
        assertEquals(0, Phase2SourceGateway.sourceRequestCount())
        assertEquals(0, Phase2SourceGateway.liveTransportRequestCount())
    }

    @Test
    fun preparesPriorFailedSearchForLiveContinuationWithoutNativeRequest() = runBlocking {
        assumeLiveSetup()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TsuyomiApplication
        val installer = SourceInstallController(context, application.libraryRepository)
        installer.restoreInstalled()
        val packageInfo = requireNotNull(installer.activePackage)
        Phase2SourceGateway.resetOperationCounts()
        Phase2SourceGateway.preparePriorFailedSearchForLiveContinuation(context, PUBLIC_SEARCH_URL)

        val failure = SourceExtensionClient.open(
            packageInfo,
            Phase2SourceGateway.create(context, packageInfo, DirectActionTokenRegistry()),
        ).use { client -> runCatching { client.search(PUBLIC_QUERY) }.exceptionOrNull() }

        assertEquals(SourceErrorCode.SESSION_REQUIRED, (failure as SourceException).code)
        assertEquals(1, Phase2SourceGateway.sourceRequestCount())
        assertEquals(0, Phase2SourceGateway.liveTransportRequestCount())
    }

    @Test
    fun wrapsCurrentLiveCookieWithTheExactWebViewUserAgent() {
        assumeLiveSetup()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val partition = SourceCredentialPartition(SOURCE_ID, ORIGIN)
        val credentials = SourceCredentialStore(context)
        val rawCookie = requireNotNull(credentials.getSnapshot(partition))
            .plaintext
            .decodeToString(throwOnInvalidSequence = true)
        VerifiedBrowserSessionStore(credentials).put(
            partition,
            VerifiedBrowserSession(rawCookie, WebSettings.getDefaultUserAgent(context)),
        )
    }

    private fun assumeLiveSetup() {
        assumeTrue(
            "Live Wenku8 setup is opt-in and must never run in normal CI",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
    }

    private companion object {
        const val SOURCE_ID = "org.tsuyomi.wenku8"
        val ORIGIN = HttpsOrigin("https://www.wenku8.net")
        const val ARG_ENABLED = "tsuyomi.liveWenku8Setup"
        const val PUBLIC_QUERY = "文学少女"
        const val PUBLIC_SEARCH_URL =
            "https://www.wenku8.net/modules/article/search.php?searchtype=articlename&searchkey=%CE%C4%D1%A7%C9%D9%C5%AE&page=1"
    }
}
