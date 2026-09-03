/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.junit.runner.RunWith
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.source.extensionmanager.HxpArchiveVerifier
import org.tsuyomi.source.extensionmanager.InMemoryPublisherKeyStore
import org.tsuyomi.source.extensionmanager.SourceExtensionClient
import org.tsuyomi.source.extensiontestkit.Phase2TestPublisher

@RunWith(AndroidJUnit4::class)
class VerifiedPageRedirectInstrumentedTest {
    @Test
    fun preservesRequestIdentityAndExposesFinalPageToParser() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "wenku8-redirect-${System.nanoTime()}.hxp")
        try {
            context.assets.open("wenku8-fixture.hxp").use { input ->
                fixture.outputStream().use(input::copyTo)
            }
            val packageInfo = HxpArchiveVerifier(
                InMemoryPublisherKeyStore(listOf(Phase2TestPublisher.key)),
            ).verify(fixture)
            val requestUrl =
                "https://www.wenku8.net/modules/article/search.php?searchtype=articlename&searchkey=%CE%C4%D1%A7%C9%D9%C5%AE&page=1"
            val finalPageUrl = "https://www.wenku8.net/modules/article/articleinfo.php?id=1234"
            val detailHtml = context.assets.open("detail.html").bufferedReader().use { it.readText() }

            val results = SourceExtensionClient.open(
                packageInfo,
                Phase2SourceGateway.createVerifiedPage(
                    context = context,
                    packageInfo = packageInfo,
                    snapshot = CapturedVerifiedPage(
                        requestUrl = requestUrl,
                        pageUrl = finalPageUrl,
                        html = detailHtml,
                    ),
                    directActionTokens = DirectActionTokenRegistry(),
                ),
            ).use { client ->
                assertEquals(requestUrl, client.searchRequestUrl("文学少女"))
                client.search("文学少女")
            }

            assertEquals(1, results.size)
            assertEquals("1234", results.single().identity.remoteBookId)
            assertEquals("雾港纪事", results.single().title)
        } finally {
            fixture.delete()
        }
    }

    @Test
    fun verifiedDetailUsesTheExactPausedGetAndSignedParser() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "wenku8-detail-${System.nanoTime()}.hxp")
        try {
            context.assets.open("wenku8-fixture.hxp").use { input ->
                fixture.outputStream().use(input::copyTo)
            }
            val packageInfo = HxpArchiveVerifier(
                InMemoryPublisherKeyStore(listOf(Phase2TestPublisher.key)),
            ).verify(fixture)
            val requestUrl = "https://www.wenku8.net/book/1234.htm"
            val detailHtml = context.assets.open("detail.html").bufferedReader().use { it.readText() }

            val detail = SourceExtensionClient.open(
                packageInfo,
                Phase2SourceGateway.createVerifiedPage(
                    context = context,
                    packageInfo = packageInfo,
                    snapshot = CapturedVerifiedPage(
                        requestUrl = requestUrl,
                        pageUrl = "https://www.wenku8.net/book/1234.htm",
                        html = detailHtml,
                    ),
                    directActionTokens = DirectActionTokenRegistry(),
                ),
            ).use { client ->
                assertEquals(requestUrl, client.detailRequestUrl("1234"))
                client.detail("1234")
            }

            assertEquals("1234", detail.summary.identity.remoteBookId)
            assertEquals("雾港纪事", detail.summary.title)
        } finally {
            fixture.delete()
        }
    }

    @Test
    fun verifiedChapterUsesTheExactPausedGetAndSignedParser() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "wenku8-chapter-${System.nanoTime()}.hxp")
        try {
            context.assets.open("wenku8-fixture.hxp").use { input ->
                fixture.outputStream().use(input::copyTo)
            }
            val packageInfo = HxpArchiveVerifier(
                InMemoryPublisherKeyStore(listOf(Phase2TestPublisher.key)),
            ).verify(fixture)
            val requestUrl = "https://www.wenku8.net/modules/article/reader.php?aid=1234&cid=10001"
            val chapter = SourceChapter(
                chapterId = "10001",
                title = "第一章 雾中的灯塔",
                url = requestUrl,
            )
            val chapterHtml = context.assets.open("chapter.html").bufferedReader().use { it.readText() }

            val document = SourceExtensionClient.open(
                packageInfo,
                Phase2SourceGateway.createVerifiedPage(
                    context = context,
                    packageInfo = packageInfo,
                    snapshot = CapturedVerifiedPage(
                        requestUrl = requestUrl,
                        pageUrl = requestUrl,
                        html = chapterHtml,
                    ),
                    directActionTokens = DirectActionTokenRegistry(),
                ),
            ).use { client ->
                assertEquals(requestUrl, client.chapterRequestUrl(chapter, "1234"))
                client.chapter(chapter, "1234")
            }

            assertEquals("10001", document.contentId)
            assertEquals("第一章 雾中的灯塔", document.title)
            assertEquals(2, document.blocks.size)
        } finally {
            fixture.delete()
        }
    }
}
