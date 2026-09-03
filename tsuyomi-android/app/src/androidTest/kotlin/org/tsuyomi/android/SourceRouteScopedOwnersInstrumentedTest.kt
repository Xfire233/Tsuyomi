/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.media.api.CoverFailureReason
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.database.LibraryBook
import org.tsuyomi.core.database.ReadingProgress
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.feature.search.SearchResultState
import org.tsuyomi.feature.search.SearchLayout
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory

@RunWith(AndroidJUnit4::class)
internal class SourceRouteScopedOwnersInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun search_draft_is_route_scoped_and_never_queries_before_explicit_submit() = runBlocking {
        val packageInfo = installFixture()
        var requests = 0
        controller {
            FakeSession(
                searchResult = { query, offlineOnly ->
                    requests++
                    assertEquals("Wenku8", query)
                    assertTrue(offlineOnly)
                    listOf(summary(SOURCE_FLOW_TEST_SOURCE_ID, "100", "结果"))
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            val savedState = SavedStateHandle()
            val owner = SourceSearchRouteOwner(flow, savedState)

            owner.updateQuery("Wenku8")

            assertEquals(0, requests)
            assertEquals(SearchResultState.Idle, owner.state)
            owner.submit(offlineOnly = true)

            assertEquals(1, requests)
            assertEquals(1, (owner.state as SearchResultState.Results).items.size)
            assertEquals("Wenku8", SourceSearchRouteOwner(flow, savedState).query)
            assertEquals(SearchLayout.LIST, owner.layout.value)
            owner.cycleLayout()
            assertEquals(SearchLayout.COMPACT, SourceSearchRouteOwner(flow, savedState).layout.value)
        }
    }
    @Test
    fun detail_state_and_local_metadata_belong_to_the_route_entry() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "200", "详情测试")
        val detailCover = "https://www.wenku8.net/image/2/200/200s.jpg"
        val chapters = listOf(SourceChapter("1", "第一章", "https://www.wenku8.net/novel/2/200/1.htm"))
        controller {
            FakeSession(
                detail = { summary ->
                    SourceBookDetail(summary.copy(title = "详情测试", coverUrl = detailCover), "简介", listOf("来源标签"), "连载")
                },
                directoryResult = { SourceDirectory(book.identity, chapters) },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            val savedState = SavedStateHandle()
            val owner = SourceDetailRouteOwner(flow, savedState)

            owner.loadAll()

            assertEquals("详情测试", (owner.state as SourceBookState.Content).value.summary.title)
            assertEquals(detailCover, owner.selectedBook?.coverUrl)
            assertEquals("第一章", (owner.directoryState as SourceBookState.Content).value.chapters.single().title)
            assertEquals(false, owner.unreadOnly.value)
            assertEquals(false, owner.descending.value)
            owner.toggleUnreadOnly()
            owner.toggleOrder()
            val restored = SourceDetailRouteOwner(flow, savedState)
            assertEquals(true, restored.unreadOnly.value)
            assertEquals(true, restored.descending.value)

            owner.execute(SourceDetailRouteOwner.Command.ADD_TO_LIBRARY.name)
            assertTrue(owner.localState.inLibrary)
            assertEquals(detailCover, library.libraryEntry(book.identity)?.book?.coverUrl)
            owner.setRating(4)
            owner.addTag("本地标签")
            owner.toggleReadLater()
            assertEquals(4, owner.localState.rating)
            assertEquals(listOf("本地标签"), owner.localState.localTags)
            assertTrue(owner.localState.readLater)
            flow.saveProgress(
                ReaderLocator(
                    document = DocumentIdentity(SOURCE_FLOW_TEST_SOURCE_ID, book.identity.remoteBookId, "1"),
                    blockId = "paragraph-1",
                    characterOffset = 0,
                    chapterProgress = 0.1,
                    capturedAt = Instant.EPOCH,
                ),
                LocatorPrecision.DEGRADED,
            )
            assertEquals(detailCover, library.libraryEntry(book.identity)?.book?.coverUrl)
            owner.execute(SourceDetailRouteOwner.Command.REMOVE_FROM_LIBRARY.name)
            assertEquals(false, owner.localState.inLibrary)
        }
    }
    @Test
    fun canonical_detail_repairs_coverless_existing_library_metadata() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "201", "缺少封面的书架条目")
        val detailCover = "https://www.wenku8.net/image/2/201/201s.jpg"
        val chapters = listOf(SourceChapter("1", "第一章", "https://www.wenku8.net/novel/2/201/1.htm"))
        library.addToLibrary(
            LibraryBook(
                identity = book.identity,
                title = book.title,
                author = book.author,
                coverUrl = null,
                canonicalUrl = book.canonicalUrl,
                addedAt = Instant.EPOCH,
                metadataUpdatedAt = Instant.EPOCH,
            ),
        )
        var libraryChanges = 0

        controller {
            FakeSession(
                detail = { summary ->
                    SourceBookDetail(summary.copy(coverUrl = detailCover), "简介", emptyList(), "连载")
                },
                directoryResult = { SourceDirectory(book.identity, chapters) },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            val owner = SourceDetailRouteOwner(flow, SavedStateHandle()) { libraryChanges++ }

            owner.loadAll()

            assertEquals(detailCover, library.libraryEntry(book.identity)?.book?.coverUrl)
            assertEquals(detailCover, owner.selectedBook?.coverUrl)
            assertEquals(1, libraryChanges)
        }
    }


    @Test
    fun verified_detail_never_inherits_a_previous_books_directory() = runBlocking {
        val packageInfo = installFixture()
        val firstBook = summary(SOURCE_FLOW_TEST_SOURCE_ID, "210", "旧目录")
        val secondBook = summary(SOURCE_FLOW_TEST_SOURCE_ID, "211", "新目录")
        val firstChapter = SourceChapter("21001", "旧章", "https://www.wenku8.net/novel/2/210/21001.htm")
        val secondChapter = SourceChapter("21101", "新章", "https://www.wenku8.net/novel/2/211/21101.htm")
        controller {
            FakeSession(
                directoryResult = { remoteBookId ->
                    if (remoteBookId == firstBook.identity.remoteBookId) {
                        SourceDirectory(firstBook.identity, listOf(firstChapter))
                    } else {
                        SourceDirectory(secondBook.identity, listOf(secondChapter))
                    }
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(firstBook)
            flow.requestDirectory(firstBook)
            flow.prepareBook(secondBook)
            val owner = SourceDetailRouteOwner(flow, SavedStateHandle())

            owner.acceptVerifiedDirectoryResult()

            assertTrue(owner.directoryState is SourceBookState.Loading)
            owner.resumeDirectoryAfterVerifiedDetail()
            val directory = (owner.directoryState as SourceBookState.Content).value
            assertEquals(secondBook.identity, directory.bookIdentity)
            assertEquals(listOf(secondChapter), directory.chapters)
        }
    }

    @Test
    fun stale_detail_request_cannot_replace_the_current_route_book() = runBlocking {
        val packageInfo = installFixture()
        val firstBook = summary(SOURCE_FLOW_TEST_SOURCE_ID, "201", "旧详情")
        val secondBook = summary(SOURCE_FLOW_TEST_SOURCE_ID, "202", "新详情")
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        controller {
            FakeSession(
                detail = { summary ->
                    if (summary.identity == firstBook.identity) {
                        firstEntered.complete(Unit)
                        releaseFirst.await()
                    }
                    val title = if (summary.identity == firstBook.identity) firstBook.title else secondBook.title
                    SourceBookDetail(summary.copy(title = title), null, emptyList(), null)
                },
                directoryResult = { remoteBookId ->
                    val book = if (remoteBookId == firstBook.identity.remoteBookId) firstBook else secondBook
                    SourceDirectory(
                        book.identity,
                        listOf(
                            SourceChapter(
                                "1",
                                "第一章",
                                "https://www.wenku8.net/novel/2/${book.identity.remoteBookId}/1.htm",
                            ),
                        ),
                    )
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(firstBook)
            val owner = SourceDetailRouteOwner(flow, SavedStateHandle())
            val stale = async { owner.loadAll() }
            firstEntered.await()

            flow.prepareBook(secondBook)
            owner.loadAll()
            releaseFirst.complete(Unit)
            stale.await()

            assertEquals("新详情", (owner.state as SourceBookState.Content).value.summary.title)
            assertEquals(secondBook.identity, owner.selectedBook?.identity)
        }
    }

    @Test
    fun reader_owner_loads_exact_chapter_and_directory_sequence() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "301", "阅读器详情")
        val first = SourceChapter("30101", "第一章", "https://www.wenku8.net/novel/3/301/30101.htm")
        val second = SourceChapter("30102", "第二章", "https://www.wenku8.net/novel/3/301/30102.htm")
        controller {
            FakeSession(
                directoryResult = { SourceDirectory(book.identity, listOf(first, second)) },
                chapterResult = { chapter, remoteBookId -> readerDocument(remoteBookId, chapter) },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            flow.prepareChapter(first)
            val owner = SourceReaderRouteOwner(flow)

            owner.load()

            assertEquals(listOf(first, second), owner.chapters)
            assertEquals(first, owner.currentChapter)
            assertEquals(first.chapterId, owner.document?.contentId)
            assertEquals(false, owner.loading)
            assertNull(owner.failure)
        }
    }

    @Test
    fun reader_images_use_host_media_context_and_require_explicit_retry() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "304", "插图章节")
        val chapter = SourceChapter("30401", "插图", "https://www.wenku8.net/novel/3/304/30401.htm")
        val image = ReaderBlock.Image(
            blockId = "image-1",
            url = "https://pic.example.test/30401.webp",
            altText = "章节插图",
            width = 900,
            height = 1200,
        )
        val document = ReaderDocument(
            sourceId = book.identity.sourceId,
            remoteBookId = book.identity.remoteBookId,
            contentId = chapter.chapterId,
            revision = null,
            title = chapter.title,
            blocks = listOf(image),
        )
        val requests = mutableListOf<CoverRequest>()
        val repository = object : CoverRepository {
            override fun observe(request: CoverRequest): Flow<CoverUiState> = flow {
                requests += request
                emit(CoverUiState.Failed(CoverFailureReason.NETWORK, request.fallback))
            }
        }
        controller {
            FakeSession(
                directoryResult = { SourceDirectory(book.identity, listOf(chapter)) },
                chapterResult = { _, _ -> document },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            flow.prepareChapter(chapter)
            val owner = SourceReaderRouteOwner(flow)
            owner.load()

            owner.loadImage(image, repository, "package-revision", "credential-revision", this)
            withTimeout(1_000) {
                while (owner.imageStates[image.blockId] !is CoverUiState.Failed) yield()
            }
            assertEquals(1, requests.size)
            assertEquals(image.url, requests.single().transportUrl)
            assertEquals(chapter.url, requests.single().referrerUrl)

            owner.loadImage(image, repository, "package-revision", "credential-revision", this)
            yield()
            assertEquals(1, requests.size)

            owner.loadImage(image, repository, "package-revision", "credential-revision", this, retry = true)
            withTimeout(1_000) {
                while (requests.size < 2) yield()
            }
            assertEquals(2, requests.size)
            owner.dispose()
        }
    }

    @Test
    fun stale_reader_request_cannot_replace_selected_chapter() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "302", "章节切换")
        val first = SourceChapter("30201", "第一章", "https://www.wenku8.net/novel/3/302/30201.htm")
        val second = SourceChapter("30202", "第二章", "https://www.wenku8.net/novel/3/302/30202.htm")
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        controller {
            FakeSession(
                directoryResult = { SourceDirectory(book.identity, listOf(first, second)) },
                chapterResult = { chapter, remoteBookId ->
                    if (chapter.chapterId == first.chapterId) {
                        firstEntered.complete(Unit)
                        releaseFirst.await()
                    }
                    readerDocument(remoteBookId, chapter)
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            flow.prepareChapter(first)
            val owner = SourceReaderRouteOwner(flow)
            val stale = async { owner.load() }
            firstEntered.await()

            owner.selectChapter(second)
            owner.load()
            releaseFirst.complete(Unit)
            stale.await()

            assertEquals(second, owner.currentChapter)
            assertEquals(second.chapterId, owner.document?.contentId)
            assertEquals(false, owner.loading)
        }
    }

    @Test
    fun disposed_reader_owner_rejects_in_flight_result() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "303", "离开阅读器")
        val chapter = SourceChapter("30301", "第一章", "https://www.wenku8.net/novel/3/303/30301.htm")
        val chapterEntered = CompletableDeferred<Unit>()
        val releaseChapter = CompletableDeferred<Unit>()
        controller {
            FakeSession(
                directoryResult = { SourceDirectory(book.identity, listOf(chapter)) },
                chapterResult = { requested, remoteBookId ->
                    chapterEntered.complete(Unit)
                    releaseChapter.await()
                    readerDocument(remoteBookId, requested)
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            flow.prepareChapter(chapter)
            val owner = SourceReaderRouteOwner(flow)
            val inFlight = async { owner.load() }
            chapterEntered.await()

            owner.dispose()
            releaseChapter.complete(Unit)
            inFlight.await()

            assertNull(owner.document)
        }
    }

    @Test
    fun remote_library_refresh_and_local_copy_are_explicit_and_route_scoped() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val first = summary(sourceId, "401", "网站收藏一")
        val second = summary(sourceId, "402", "网站收藏二")
        var reads = 0
        var writes = 0
        controller {
            FakeSession(
                listRemote = {
                    reads++
                    RemoteLibraryPage(listOf(first, second), null, true)
                },
                addRemote = { _, _ ->
                    writes++
                    error("Website mutation must not run during local copy")
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            val savedState = SavedStateHandle()
            val owner = SourceRemoteLibraryRouteOwner(flow, { packageInfo }, savedState)

            assertEquals(RemoteLibraryRouteStatus.Idle, owner.status)
            assertEquals(0, reads)
            assertTrue(library.libraryEntries().isEmpty())

            owner.refresh()

            assertEquals(1, reads)
            assertEquals(RemoteLibraryRouteStatus.Content, owner.status)
            assertEquals(listOf(first, second), owner.books)
            assertTrue(library.libraryEntries().isEmpty())

            owner.toggleSelection(first)
            val restored = SourceRemoteLibraryRouteOwner(flow, { packageInfo }, savedState)
            assertEquals(setOf(first.canonicalUrl), restored.selectedIds)
            assertEquals(RemoteLibraryRouteStatus.Idle, restored.status)
            assertTrue(restored.books.isEmpty())
            assertEquals(1, reads)

            owner.requestCopy()
            assertTrue(owner.copyConfirmationVisible)
            assertEquals(RemoteLibraryCopyResult(total = 1, added = 1), owner.confirmCopy())
            assertEquals(0, writes)
            assertEquals(setOf(first.identity), library.libraryEntries().map { it.book.identity }.toSet())

            owner.requestCopy()
            assertEquals(RemoteLibraryCopyResult(total = 2, added = 1), owner.confirmCopy())
            assertEquals(0, writes)
            assertEquals(setOf(first.identity, second.identity), library.libraryEntries().map { it.book.identity }.toSet())
        }
    }

    @Test
    fun remote_library_owner_exposes_safe_transport_states() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val failures = mutableListOf(
            SourceErrorCode.SESSION_REQUIRED to "session-required",
            SourceErrorCode.VERIFICATION_REQUIRED to "verification-required",
            SourceErrorCode.EXTENSION_CANCELLED to "cancelled",
            SourceErrorCode.NETWORK_TIMEOUT to "network-timeout",
        )
        controller {
            FakeSession(
                listRemote = {
                    val (code, safeCode) = failures.removeAt(0)
                    throw SourceException(
                        code,
                        SourceDiagnostic(
                            correlationId = "remote-state-test",
                            stage = "remote-list",
                            safeCode = safeCode,
                        ),
                    )
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            val owner = SourceRemoteLibraryRouteOwner(flow, { packageInfo }, SavedStateHandle())

            owner.refresh()
            assertEquals(RemoteLibraryRouteStatus.LoginRequired, owner.status)
            owner.refresh()
            assertEquals(RemoteLibraryRouteStatus.VerificationRequired, owner.status)
            owner.refresh()
            assertEquals(RemoteLibraryRouteStatus.Cancelled, owner.status)
            owner.refresh()
            assertEquals(RemoteLibraryRouteStatus.Failure("network-timeout"), owner.status)
            assertEquals(false, owner.loading)
        }
    }

    @Test
    fun persisted_locator_rehydrates_exact_book_chapter_and_directory() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "resume-book", "恢复测试")
        val first = SourceChapter("resume-1", "第一章", "https://www.wenku8.net/novel/2/300/resume-1.htm")
        val second = SourceChapter("resume-2", "第二章", "https://www.wenku8.net/novel/2/300/resume-2.htm")
        val detail = SourceBookDetail(book, "简介", emptyList(), "连载")
        val directory = SourceDirectory(book.identity, listOf(first, second))
        NormalizedSourceStore(context).apply {
            writeDetail(detail)
            writeDirectory(directory)
            writeDocument(book.identity, readerDocument(book.identity.remoteBookId, second))
        }
        val capturedAt = Instant.parse("2026-08-31T12:00:00Z")
        library.addToLibrary(
            LibraryBook(
                identity = book.identity,
                title = book.title,
                author = book.author,
                coverUrl = book.coverUrl,
                canonicalUrl = book.canonicalUrl,
                addedAt = capturedAt,
                metadataUpdatedAt = capturedAt,
            ),
        )
        library.saveProgress(
            ReadingProgress(
                identity = book.identity,
                locator = ReaderLocator(
                    document = DocumentIdentity(book.identity.sourceId, book.identity.remoteBookId, second.chapterId),
                    blockId = "p-resume-2",
                    characterOffset = 7,
                    chapterProgress = 0.4,
                    capturedAt = capturedAt,
                ),
            ),
        )
        val entry = library.libraryEntries().single { it.book.identity == book.identity }

        controller { FakeSession() }.use { flow ->
            flow.open(packageInfo)
            assertTrue(flow.prepareResume(entry))
            assertEquals(book.identity, flow.selectedBook?.identity)
            assertEquals(second, flow.selectedChapter)
            assertEquals(directory, (flow.directoryState as SourceBookState.Content).value)
            val prepared = requireNotNull(flow.consumePreparedResumeLoad())
            assertEquals(second.chapterId, prepared.document?.contentId)
            assertEquals(7, prepared.restoredLocator?.characterOffset)
        }
    }

    private fun readerDocument(remoteBookId: String, chapter: SourceChapter) = ReaderDocument(
        sourceId = SOURCE_FLOW_TEST_SOURCE_ID,
        remoteBookId = remoteBookId,
        contentId = chapter.chapterId,
        revision = null,
        title = chapter.title,
        blocks = listOf(ReaderBlock.Paragraph("p-${chapter.chapterId}", "${chapter.title}正文")),
    )

}
