/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import kotlinx.coroutines.CancellationException
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
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
                detail = { summary -> SourceBookDetail(summary.copy(title = "规范详情"), "规范简介", listOf("测试"), "连载", lastUpdatedDate = "2026-02-03") },
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
        assertEquals("2026-02-03", detail.lastUpdatedDate)
        assertEquals("10001", directory.chapters.single().chapterId)
        assertEquals("第一卷", directory.chapters.single().volumeTitle)
        assertEquals(document, offlineReader.document)
        assertNull(offlineReader.failure)
        offline.close()
    }

    @Test
    fun history_only_visit_resumes_first_cached_chapter_without_creating_progress_or_pin() = runBlocking {
        val identity = BookIdentity(SOURCE_FLOW_TEST_SOURCE_ID, "history-only")
        val book = summary(identity.sourceId, identity.remoteBookId, "历史续读")
        val first = SourceChapter("first", "第一章", "https://www.wenku8.net/book/history-only/first")
        val second = SourceChapter("second", "第二章", "https://www.wenku8.net/book/history-only/second")
        val firstDocument = ReaderDocument(
            sourceId = identity.sourceId,
            remoteBookId = identity.remoteBookId,
            contentId = first.chapterId,
            revision = null,
            title = first.title,
            blocks = listOf(ReaderBlock.Paragraph("first-p", "历史首章")),
        )
        NormalizedSourceStore(context).apply {
            writeDetail(SourceBookDetail(book, null, emptyList(), null))
            writeDirectory(SourceDirectory(identity, listOf(first, second)))
            writeDocument(identity, firstDocument)
        }
        controller().use { admittingFlow ->
            admittingFlow.prepareBook(book)
            assertTrue(admittingFlow.recordReaderVisit(identity, java.time.Instant.EPOCH))
        }
        assertNull(library.progress(identity))
        assertNull(library.libraryEntry(identity))

        controller().use { resumedFlow ->
            assertTrue(resumedFlow.prepareResume(identity))
            val load = requireNotNull(resumedFlow.consumePreparedResumeLoad())
            assertEquals(firstDocument, load.document)
            assertNull(load.restoredLocator)
            assertEquals(first, resumedFlow.selectedChapter)
        }
        assertNull(library.progress(identity))
        assertNull(library.libraryEntry(identity))
    }


    @Test
    fun partial_summary_never_narrows_richer_authors_but_detail_remains_authoritative() = runBlocking {
        val packageInfo = installFixture()
        val identity = BookIdentity(SOURCE_FLOW_TEST_SOURCE_ID, "author-preservation")
        library.saveBook(
            LibraryBook(
                identity = identity,
                title = "本地作者元数据",
                author = "作者甲",
                authors = setOf("作者甲", "作者乙"),
                addedAt = SOURCE_FLOW_TEST_TIME,
                metadataUpdatedAt = SOURCE_FLOW_TEST_TIME,
            ),
        )
        val summary = summary(identity.sourceId, identity.remoteBookId, "部分摘要").copy(author = "摘要作者")
        val flow = controller {
            FakeSession(
                detail = { source ->
                    SourceBookDetail(source.copy(author = "详情作者"), null, emptyList(), null)
                },
            )
        }
        try {
            flow.open(packageInfo)
            flow.prepareBook(summary)
            flow.saveProgress(
                ReaderLocator(
                    document = DocumentIdentity(identity.sourceId, identity.remoteBookId, "chapter-1"),
                    blockId = "block-1",
                    characterOffset = 0,
                    capturedAt = SOURCE_FLOW_TEST_TIME,
                ),
                precision = org.tsuyomi.shared.locator.LocatorPrecision.DEGRADED,
            )
            assertEquals(setOf("作者甲", "作者乙"), requireNotNull(library.book(identity)).authors)

            flow.selectBook(summary)
            assertEquals(setOf("详情作者"), requireNotNull(library.book(identity)).authors)
        } finally {
            flow.close()
        }
    }

    @Test
    fun reader_bookmark_toggle_persists_without_creating_a_local_pin() = runBlocking {
        val identity = BookIdentity(SOURCE_FLOW_TEST_SOURCE_ID, "bookmark-flow")
        val bookmark = ReaderLocator(
            document = DocumentIdentity(identity.sourceId, identity.remoteBookId, "chapter-1"),
            blockId = "paragraph-1",
            characterOffset = 7,
            textAnchorDigest = "a".repeat(64),
            capturedAt = java.time.Instant.EPOCH,
        )
        val flow = controller()
        try {
            flow.prepareBook(summary(identity.sourceId, identity.remoteBookId, "Reader 书签"))
            assertEquals(true, flow.toggleBookmark(bookmark))
            assertEquals(listOf(bookmark), flow.observeBookmarks(identity).first())
            assertNull(library.libraryEntry(identity))
            flow.removeSource(identity.sourceId)
            assertEquals(listOf(bookmark), library.bookmarks(identity))
        } finally {
            flow.close()
        }
    }

    @Test
    fun source_cancellation_is_not_converted_to_a_failed_result() = runBlocking {
        val packageInfo = installFixture()
        val flow = controller {
            FakeSession(homeResult = { _, _, _ -> throw CancellationException("fixture cancellation") })
        }
        try {
            flow.open(packageInfo)
            var cancelled = false
            try {
                flow.loadHome(emptyMap())
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        } finally {
            flow.close()
        }
    }
    @Test
    fun normalized_detail_preserves_update_date_and_decodes_legacy_detail() {
        val storedIdentity = BookIdentity(SOURCE_FLOW_TEST_SOURCE_ID, "1234")
        val legacyIdentity = BookIdentity(SOURCE_FLOW_TEST_SOURCE_ID, "5678")
        val store = NormalizedSourceStore(context)
        store.writeDetail(
            SourceBookDetail(
                summary = summary(storedIdentity.sourceId, storedIdentity.remoteBookId, "缓存详情"),
                description = null,
                tags = emptyList(),
                status = null,
                lastUpdatedDate = "2026-02-03",
            ),
        )

        legacyDetailFile(legacyIdentity).apply {
            parentFile?.mkdirs()
            writeText(
                """{"schema":1,"kind":"detail","summary":{"identity":{"sourceId":"org.tsuyomi.wenku8","remoteBookId":"5678"},"title":"旧缓存详情","author":null,"coverUrl":null,"canonicalUrl":"https://www.wenku8.net/book/5678.htm"},"description":null,"tags":[],"status":null}""",
            )
        }

        assertEquals("2026-02-03", store.readDetail(storedIdentity)?.lastUpdatedDate)
        val legacy = store.readDetail(legacyIdentity)
        assertEquals("旧缓存详情", legacy?.summary?.title)
        assertNull(legacy?.lastUpdatedDate)
    }

    private fun legacyDetailFile(identity: BookIdentity): File {
        val key = MessageDigest.getInstance("SHA-256")
            .digest("${identity.sourceId}\u0000${identity.remoteBookId}\u0000".toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return File(context.noBackupFilesDir, "normalized-source-content/detail/$key.json")
    }
}
