/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.core.network.UrlConnectionHostHttpTransport
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.source.extensionmanager.SourceExtensionClient

/**
 * Explicit non-CI live probe. One run performs exactly one predeclared public search through the
 * production gateway and parser. Raw pages, cookies, user agents, result identities and titles are
 * never persisted. Invoke only with `tsuyomi.liveWenku8Probe=true`.
 */
@RunWith(AndroidJUnit4::class)
class LiveWenku8TransportProbeInstrumentedTest {
    @Test
    fun recordsOneBoundedProductionSearch() = runBlocking {
        assumeTrue(
            "Live Wenku8 probe is opt-in and must never run in normal CI",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storedSession = VerifiedBrowserSessionStore(context)
            .getSnapshot(SourceCredentialPartition(SOURCE_ID, ORIGIN))
            ?.session
        val report = JSONObject()
            .put("schema", "tsuyomi-live-wenku8-transport-probe-v2")
            .put("requestBudget", 1)
            .put("queryKind", "predeclared-public")
            .put("storedSessionPresent", storedSession != null)
        requireNotNull(storedSession) { "Controlled Wenku8 login is required before the live probe" }

        val application = context.applicationContext as TsuyomiApplication
        val installer = SourceInstallController(context, application.libraryRepository)
        installer.restoreInstalled()
        val packageInfo = requireNotNull(installer.activePackage)
        require(packageInfo.manifest.sourceId.value == SOURCE_ID)

        val results = try {
            SourceExtensionClient.open(
                packageInfo,
                SourceGatewayFactory.create(
                    context,
                    packageInfo,
                    transport = UrlConnectionHostHttpTransport(),
                    directActionTokens = DirectActionTokenRegistry(),
                ),
            ).use { client -> client.search(PUBLIC_QUERY) }
        } catch (error: SourceException) {
            report
                .put("classification", "source-error")
                .put("errorCode", error.code.name)
                .put("stage", error.diagnostic.stage)
                .put("safeCode", error.diagnostic.safeCode)
            context.noBackupFilesDir.resolve(OUTPUT_NAME).writeText(report.toString(2))
            throw error
        }

        report
            .put("classification", if (results.isEmpty()) "empty" else "content")
            .put("resultCount", results.size)
        context.noBackupFilesDir.resolve(OUTPUT_NAME).writeText(report.toString(2))
        assertTrue(report.toString(2), results.isNotEmpty())
    }

    private companion object {
        const val ARG_ENABLED = "tsuyomi.liveWenku8Probe"
        const val OUTPUT_NAME = "live-wenku8-transport-probe.json"
        const val SOURCE_ID = "org.tsuyomi.wenku8"
        const val PUBLIC_QUERY = "文学少女"
        val ORIGIN = HttpsOrigin("https://www.wenku8.net")
    }
}
