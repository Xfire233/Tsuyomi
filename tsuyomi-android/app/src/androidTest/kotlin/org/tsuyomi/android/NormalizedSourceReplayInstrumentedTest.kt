/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory

internal class NormalizedSourceReplayInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun validated_detail_directory_and_document_replay_offline_without_raw_transport_cache() = runBlocking {
        val packageInfo = installFixture()
        val identity = BookIdentity(SOURCE_FLOW_TEST_SOURCE_ID, "1234")
        val selected = summary(identity.sourceId, identity.remoteBookId, "搜索结果")
        val chapters = listOf(
            SourceChapter(
                "10001",
                "第一章",
                "https://www.wenku8.net/modules/article/reader.php?aid=1234&cid=10001",
                volumeTitle = "第一卷",
            ),
        )
        val document = ReaderDocument(
            sourceId = identity.sourceId,
            remoteBookId = identity.remoteBookId,
            contentId = "10001",
            revision = null,
            title = "第一章",
            blocks = listOf(ReaderBlock.Paragraph("p-0001", "确定性的离线正文。")),
        )
        val online = controller {
            FakeSession(
                detail = { summary -> SourceBookDetail(summary.copy(title = "规范详情"), "规范简介", listOf("测试"), "连载") },
                directoryResult = { SourceDirectory(identity, chapters) },
                chapterResult = { _, _ -> document },
            )
        }
        online.open(packageInfo)
        online.selectBook(selected)
        online.loadDirectory()
        online.prepareChapter(chapters.single())
        assertEquals(document, online.requestChapter(offlineOnly = false).document)
        assertNull("Viewing source detail must not implicitly add a local book", library.book(identity))
        online.close()

        val offline = controller { FakeSession() }
        offline.open(packageInfo)
        offline.selectBook(selected, offlineOnly = true)
        offline.loadDirectory(offlineOnly = true)
        offline.prepareChapter(chapters.single())
        val offlineReader = offline.requestChapter(offlineOnly = true)

        val detail = (offline.detailState as SourceBookState.Content).value
        val directory = (offline.directoryState as SourceBookState.Content).value
        assertEquals("规范详情", detail.summary.title)
        assertEquals("10001", directory.chapters.single().chapterId)
        assertEquals("第一卷", directory.chapters.single().volumeTitle)
        assertEquals(document, offlineReader.document)
        assertNull(offlineReader.failure)
        offline.close()
    }
}
