/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.network.HostHttpResponse
import org.tsuyomi.core.network.HostHttpTransport
import org.tsuyomi.core.network.HostNetworkError
import org.tsuyomi.core.network.HostNetworkException
import org.tsuyomi.core.network.HostNetworkGateway
import org.tsuyomi.shared.sourcecontract.SourceUpdateOutcome
import org.tsuyomi.source.extensionmanager.SourceExtensionClient

@RunWith(AndroidJUnit4::class)
internal class UpdateSourceDiagnosticsInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun signed_update_failure_retains_network_cause_and_stage_without_payload() = runBlocking {
        check(context.packageName == "org.tsuyomi.android.fixture")
        val packageInfo = installFixture()
        val gateway = HostNetworkGateway(HostHttpTransport {
            throw HostNetworkException(HostNetworkError.TIMEOUT)
        })
        SourceExtensionClient.open(packageInfo, gateway).use { client ->
            val result = client.checkUpdates("1234", previousAnchor = null)
            assertEquals(SourceUpdateOutcome.FAILED, result.outcome)
            assertEquals("source-network-timeout.update-check-network.timeout", result.reason)
            assertTrue(result.chapters.isEmpty())
            assertTrue(result.newChapterIds.isEmpty())
        }
    }

    @Test
    fun signed_standalone_directory_establishes_baseline_then_reports_only_appended_chapters() = runBlocking {
        check(context.packageName == "org.tsuyomi.android.fixture")
        val packageInfo = installFixture()
        var html = context.assets.open("update-directory-dynamic.html").bufferedReader().use { it.readText() }
        val gateway = HostNetworkGateway(HostHttpTransport { request ->
            HostHttpResponse(
                status = 200,
                finalUrl = request.url,
                headers = mapOf("Content-Type" to "text/html; charset=gb18030"),
                bytes = html.toByteArray(Charset.forName("GB18030")),
            )
        })
        SourceExtensionClient.open(packageInfo, gateway).use { client ->
            val baseline = client.checkUpdates("1234", previousAnchor = null)
            assertEquals(baseline.reason, SourceUpdateOutcome.UNCHANGED, baseline.outcome)
            assertEquals(listOf("10001", "10002"), baseline.chapters.map { it.chapterId })
            assertTrue(baseline.newChapterIds.isEmpty())
            html = html.replace(
                "</table>\n<div id=\"adbottom\">",
                "<tr><td class=\"ccss\"><a href=\"/modules/article/reader.php?aid=1234&amp;cid=10003\">第三章 测试新增</a></td></tr></table>\n<div id=\"adbottom\">",
            )
            val appended = client.checkUpdates("1234", previousAnchor = baseline.anchor)
            assertEquals(appended.reason, SourceUpdateOutcome.UPDATED, appended.outcome)
            assertEquals(listOf("10003"), appended.newChapterIds)
        }
    }
}
