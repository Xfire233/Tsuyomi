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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.media.api.CoverFailureReason
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.ReadingProgress
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage
import org.tsuyomi.shared.sourcecontract.RemoteLibraryTargetsResult
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.feature.book.DetailMutationOperation
import org.tsuyomi.feature.book.DetailMutationPhase
import org.tsuyomi.feature.search.SearchResultState
import org.tsuyomi.feature.book.DetailCacheAction
import org.tsuyomi.feature.book.DetailChapterCachePhase
import org.tsuyomi.feature.search.SearchLayout
import org.tsuyomi.feature.library.remoteLibrarySelectionId
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.locator.namesSameBookmarkPositionAs
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
    fun repeated_submit_while_search_is_running_issues_one_source_request() = runBlocking {
        val packageInfo = installFixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        controller {
            FakeSession(searchResult = { _, _ ->
                requests++
                entered.complete(Unit)
                release.await()
                listOf(summary(SOURCE_FLOW_TEST_SOURCE_ID, "one", "唯一结果"))
            })
        }.use { flow ->
            flow.open(packageInfo)
            val owner = SourceSearchRouteOwner(flow, SavedStateHandle())
            owner.updateQuery("同一次搜索")

            val first = async { owner.submit() }
            entered.await()
            val repeated = async { owner.submit() }
            repeated.await()

            assertEquals(1, requests)
            assertEquals(SearchResultState.Loading, owner.state)
            release.complete(Unit)
            first.await()
            assertEquals("one", (owner.state as SearchResultState.Results).items.single().identity.remoteBookId)
        }
    }

    @Test
    fun cancelled_route_search_settles_failure_without_replaying_on_restore() = runBlocking {
        val packageInfo = installFixture()
        val entered = CompletableDeferred<Unit>()
        var requests = 0
        controller {
            FakeSession(searchResult = { _, _ ->
                requests++
                if (requests == 1) {
                    entered.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }
                listOf(summary(SOURCE_FLOW_TEST_SOURCE_ID, "retry", "显式重试结果"))
            })
        }.use { flow ->
            flow.open(packageInfo)
            val owner = SourceSearchRouteOwner(flow, SavedStateHandle())
            owner.updateQuery("query")
            val pending = async { owner.submit() }
            entered.await()
            pending.cancel()
            pending.join()
            assertTrue(owner.state is SearchResultState.Failure)
            owner.restore(packageInfo)
            assertEquals(1, requests)
            assertTrue(owner.state is SearchResultState.Failure)
            owner.submit()
            assertEquals(2, requests)
            assertEquals("retry", (owner.state as SearchResultState.Results).items.single().identity.remoteBookId)
        }
    }

    @Test
    fun author_submit_uses_the_exact_author_and_matching_verified_page_mode_without_restore_replay() = runBlocking {
        val packageInfo = installFixture()
        val author = "作者 原样"
        val requestUrl = "https://www.wenku8.net/modules/article/search.php?searchtype=author&searchkey=author&page=1"
        var authorRequests = 0
        var verifiedAuthor: String? = null
        val snapshot = CapturedVerifiedPage(requestUrl, requestUrl, "<html></html>")
        controller {
            FakeSession(
                authorSearchResult = { requestedAuthor, offlineOnly ->
                    authorRequests++
                    assertEquals(author, requestedAuthor)
                    assertTrue(!offlineOnly)
                    listOf(summary(SOURCE_FLOW_TEST_SOURCE_ID, "101", "作者结果"))
                },
                authorSearchRequestUrl = { requestedAuthor ->
                    assertEquals(author, requestedAuthor)
                    requestUrl
                },
                authorSearchVerifiedPage = { requestedAuthor, receivedSnapshot ->
                    verifiedAuthor = requestedAuthor
                    assertEquals(snapshot, receivedSnapshot)
                    listOf(summary(SOURCE_FLOW_TEST_SOURCE_ID, "102", "验证作者结果"))
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            val savedState = SavedStateHandle()
            val owner = SourceSearchRouteOwner(flow, savedState)

            owner.submitAuthor(author)

            assertEquals(1, authorRequests)
            assertEquals(author, owner.query)
            assertTrue(owner.authorSearch)
            assertEquals("作者结果", (owner.state as SearchResultState.Results).items.single().title)
            val restoredOwner = SourceSearchRouteOwner(flow, savedState)
            assertEquals(author, restoredOwner.query)
            assertTrue(restoredOwner.authorSearch)
            restoredOwner.restore(packageInfo)
            assertEquals(1, authorRequests)
            assertTrue(owner.authorSearch)
            assertEquals(requestUrl, flow.searchVerifiedPageRequestUrl())
            assertTrue(flow.searchVerifiedPage(snapshot))
            owner.acceptVerifiedPageResult()
            assertEquals(author, verifiedAuthor)
            assertEquals("验证作者结果", (owner.state as SearchResultState.Results).items.single().title)
        }
    }

    @Test
    fun author_retry_and_explicit_offline_cache_retain_author_mode_until_query_edit() = runBlocking {
        val packageInfo = installFixture()
        val requests = mutableListOf<Boolean>()
        controller {
            FakeSession(
                searchResult = { query, offlineOnly ->
                    assertEquals("标题", query)
                    assertTrue(!offlineOnly)
                    listOf(summary(SOURCE_FLOW_TEST_SOURCE_ID, "103", "标题结果"))
                },
                authorSearchResult = { author, offlineOnly ->
                    assertEquals("作者", author)
                    requests += offlineOnly
                    listOf(summary(SOURCE_FLOW_TEST_SOURCE_ID, "104", "作者结果"))
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            val owner = SourceSearchRouteOwner(flow, SavedStateHandle())

            owner.submitAuthor("作者")
            owner.submit(offlineOnly = true)

            assertEquals(listOf(false, true), requests)
            assertTrue(owner.authorSearch)
            owner.updateQuery("标题")
            assertTrue(!owner.authorSearch)
            owner.submit()
            assertEquals("标题结果", (owner.state as SearchResultState.Results).items.single().title)
        }
    }

    @Test
    fun latest_author_invocation_owns_the_retained_working_state_and_results() = runBlocking {
        val packageInfo = installFixture()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        controller {
            FakeSession(
                authorSearchResult = { author, _ ->
                    if (author == "先前作者") {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                        listOf(summary(SOURCE_FLOW_TEST_SOURCE_ID, "105", "过期结果"))
                    } else {
                        assertEquals("当前作者", author)
                        listOf(summary(SOURCE_FLOW_TEST_SOURCE_ID, "106", "当前结果"))
                    }
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            val owner = SourceSearchRouteOwner(flow, SavedStateHandle())

            val first = async { owner.submitAuthor("先前作者") }
            firstStarted.await()
            assertEquals(SearchResultState.Loading, owner.state)
            owner.submitAuthor("当前作者")
            releaseFirst.complete(Unit)
            first.await()

            assertEquals("当前作者", owner.query)
            assertEquals("当前结果", (owner.state as SearchResultState.Results).items.single().title)
        }
    }

    @Test
    fun author_over_the_search_bound_fails_without_truncation_or_network_request() = runBlocking {
        val packageInfo = installFixture()
        var authorRequests = 0
        val author = "a".repeat(101)
        controller {
            FakeSession(
                authorSearchResult = { _, _ ->
                    authorRequests++
                    emptyList()
                },
                authorSearchRequestUrl = {
                    authorRequests++
                    "https://www.wenku8.net/unreachable"
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            val owner = SourceSearchRouteOwner(flow, SavedStateHandle())

            owner.submitAuthor(author)

            assertEquals(0, authorRequests)
            assertNull(flow.searchVerifiedPageRequestUrl())
            assertEquals(0, authorRequests)
            assertEquals(author, owner.query)
            assertTrue(owner.authorSearch)
            assertEquals(
                "author-query-too-long",
                (owner.state as SearchResultState.Failure).diagnostic.safeCode,
            )
        }
    }

    @Test
    fun unavailable_author_search_is_observable_without_title_search_fallback() = runBlocking {
        val packageInfo = installFixture()
        var titleSearches = 0
        controller {
            FakeSession(
                searchResult = { _, _ ->
                    titleSearches++
                    emptyList()
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            val owner = SourceSearchRouteOwner(flow, SavedStateHandle())

            owner.submitAuthor("未支持作者")

            assertEquals(0, titleSearches)
            assertEquals(
                "author-search-unavailable",
                (owner.state as SearchResultState.Failure).diagnostic.safeCode,
            )
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
            val owner = SourceDetailRouteOwner(flow, savedState) {}

            owner.loadAll()

            assertEquals("详情测试", (owner.state as SourceBookState.Content).value.summary.title)
            assertEquals(detailCover, owner.selectedBook?.coverUrl)
            assertEquals("第一章", (owner.directoryState as SourceBookState.Content).value.chapters.single().title)
            assertEquals(false, owner.unreadOnly.value)
            assertEquals(false, owner.descending.value)
            owner.toggleUnreadOnly()
            owner.toggleOrder()
            val restored = SourceDetailRouteOwner(flow, savedState) {}
            assertEquals(true, restored.unreadOnly.value)
            assertEquals(true, restored.descending.value)

            owner.execute(SourceDetailRouteOwner.Command.ADD_TO_LIBRARY.name)
            assertTrue(owner.localState.inLibrary)
            assertEquals(detailCover, library.libraryEntry(book.identity)?.book?.coverUrl)
            assertEquals(setOf("来源标签"), library.libraryEntry(book.identity)?.book?.remoteTags)
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
            assertEquals(4, owner.localState.rating)
            assertEquals(listOf("本地标签"), owner.localState.localTags)
            assertTrue(owner.localState.readLater)
            assertEquals("1", owner.localState.progressChapterId)
            assertTrue(library.libraryEntries().none { it.book.identity == book.identity })
            assertTrue(library.readLaterEntries().any { it.book.identity == book.identity })
            owner.toggleReadLater()
            assertEquals(false, owner.localState.readLater)
            assertEquals(false, owner.localState.inLibrary)
            owner.addTag("取消固定后标签")
            assertTrue(owner.localState.localTags.containsAll(setOf("本地标签", "取消固定后标签")))
            assertTrue(library.libraryEntries().none { it.book.identity == book.identity })
            owner.execute(SourceDetailRouteOwner.Command.ADD_TO_LIBRARY.name)
            assertTrue(owner.localState.inLibrary)
            assertEquals(4, owner.localState.rating)
            assertEquals(setOf("本地标签", "取消固定后标签"), owner.localState.localTags.toSet())
            assertEquals("1", owner.localState.progressChapterId)
        }
    }
    @Test
    fun tag_editor_draft_restores_and_failure_preserves_it_without_pinning() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "tag-editor", "标签编辑")
        val chapter = SourceChapter("1", "第一章", "https://www.wenku8.net/novel/2/200/1.htm")
        controller {
            FakeSession(
                detail = { summary -> SourceBookDetail(summary, "简介", listOf("来源标签"), "连载") },
                directoryResult = { SourceDirectory(book.identity, listOf(chapter)) },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            val savedState = SavedStateHandle()
            val owner = SourceDetailRouteOwner(flow, savedState) {}
            owner.loadAll()
            assertTrue(owner.localState.localTagsEditable)

            owner.openTagEditor()
            owner.updateTagDraft("待恢复标签")
            val restored = SourceDetailRouteOwner(flow, savedState) {}
            assertTrue(restored.tagEditorOpen.value)
            assertEquals("待恢复标签", restored.tagDraft.value)
            restored.loadAll(offlineOnly = true)
            restored.dismissTagEditor()
            assertFalse(restored.tagEditorOpen.value)
            assertEquals("", restored.tagDraft.value)

            restored.openTagEditor()
            restored.updateTagDraft("首次标签")
            restored.confirmTagEditor()
            assertFalse(restored.tagEditorOpen.value)
            restored.openTagEditor()
            restored.updateTagDraft(" 首次标签 ")
            restored.confirmTagEditor()
            assertEquals(setOf("首次标签"), library.libraryEntry(book.identity)?.localTags)
            assertEquals("", restored.tagDraft.value)
            assertFalse(restored.localState.inLibrary)

            library.setLocalTags(book.identity, (1..64).map { "tag-$it" })
            flow.remoteLibrary.refreshSelection(book)
            restored.openTagEditor()
            restored.updateTagDraft("超出上限")
            restored.confirmTagEditor()

            assertTrue(restored.tagEditorOpen.value)
            assertEquals("超出上限", restored.tagDraft.value)
            assertEquals(DetailMutationPhase.ERROR, restored.mutation?.phase)
            assertEquals(64, library.libraryEntry(book.identity)?.localTags?.size)
            assertFalse(restored.localState.inLibrary)
        }
    }

    @Test
    fun cache_detail_selects_without_fetching_then_persists_only_explicit_chapters() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "cache-selected", "选择缓存")
        val first = SourceChapter("cache-1", "第一章", "https://www.wenku8.net/novel/2/200/cache-1.htm")
        val second = SourceChapter("cache-2", "第二章", "https://www.wenku8.net/novel/2/200/cache-2.htm")
        val chapterRequests = mutableListOf<String>()
        var detailRequests = 0
        var directoryRequests = 0
        controller {
            FakeSession(
                detail = { summary ->
                    detailRequests++
                    SourceBookDetail(summary, "缓存简介", emptyList(), "连载")
                },
                directoryResult = {
                    directoryRequests++
                    SourceDirectory(book.identity, listOf(first, second))
                },
                chapterResult = { chapter, remoteBookId ->
                    chapterRequests += chapter.chapterId
                    readerDocument(remoteBookId, chapter)
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            val owner = SourceDetailRouteOwner(flow, SavedStateHandle()) {}
            owner.loadAll()
            flow.prepareChapter(second)
            flow.saveProgress(
                ReaderLocator(
                    document = DocumentIdentity(book.identity.sourceId, book.identity.remoteBookId, second.chapterId),
                    blockId = "p-${second.chapterId}",
                    characterOffset = 3,
                    chapterProgress = 0.3,
                    capturedAt = SOURCE_FLOW_TEST_TIME,
                ),
                LocatorPrecision.EXACT,
            )

            owner.execute(SourceDetailRouteOwner.Command.CACHE_DETAIL.name)

            assertTrue(owner.cacheState.selecting)
            assertEquals(1, detailRequests)
            assertEquals(1, directoryRequests)
            assertEquals(emptyList<String>(), chapterRequests)
            owner.handleCacheAction(DetailCacheAction.Cancel)
            assertEquals(1, detailRequests)
            assertEquals(1, directoryRequests)
            assertEquals(emptyList<String>(), chapterRequests)
            owner.handleCacheAction(DetailCacheAction.Toggle(first.chapterId))
            owner.handleCacheAction(DetailCacheAction.ToggleAll(setOf(second.chapterId, "outside-directory")))
            assertEquals(setOf(first.chapterId, second.chapterId), owner.cacheState.selectedChapterIds)
            owner.handleCacheAction(DetailCacheAction.ToggleAll(setOf(second.chapterId)))
            assertEquals(setOf(first.chapterId), owner.cacheState.selectedChapterIds)
            owner.handleCacheAction(DetailCacheAction.Start)

            assertEquals(listOf(first.chapterId), chapterRequests)
            assertEquals(DetailChapterCachePhase.CACHED, owner.cacheState.chapters[first.chapterId])
            assertEquals(emptySet<String>(), owner.cacheState.selectedChapterIds)
            owner.handleCacheAction(DetailCacheAction.ToggleAll(setOf(first.chapterId, second.chapterId)))
            assertEquals(setOf(second.chapterId), owner.cacheState.selectedChapterIds)
            val restoredOwner = SourceDetailRouteOwner(flow, SavedStateHandle()) {}
            restoredOwner.refreshCachedChapterStatuses()
            assertEquals(DetailChapterCachePhase.CACHED, restoredOwner.cacheState.chapters[first.chapterId])
            assertEquals(null, restoredOwner.cacheState.chapters[second.chapterId])
            assertEquals(null, owner.cacheState.chapters[second.chapterId])
            assertEquals(second, owner.selectedChapter)
            assertEquals(second.chapterId, library.progress(book.identity)?.locator?.document?.contentId)
            assertEquals(
                first.chapterId,
                flow.requestChapter(book, first, offlineOnly = true).document?.contentId,
            )
            assertEquals(
                SourceErrorCode.NETWORK_OFFLINE,
                flow.requestChapter(book, second, offlineOnly = true).failure?.code,
            )
        }
    }

    @Test
    fun refresh_detail_fetches_metadata_and_retains_last_good_content_after_failure() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "refresh-detail", "刷新详情")
        val chapter = SourceChapter("refresh-1", "第一章", "https://www.wenku8.net/novel/2/200/refresh-1.htm")
        var detailRequests = 0
        var directoryRequests = 0
        var failRequests = false
        controller {
            FakeSession(
                detail = { summary ->
                    detailRequests++
                    if (failRequests) throw SourceException(
                        SourceErrorCode.VERIFICATION_REQUIRED,
                        SourceDiagnostic("refresh-detail", "detail", safeCode = "verification-required"),
                    )
                    SourceBookDetail(summary, "缓存简介", emptyList(), "连载")
                },
                directoryResult = {
                    directoryRequests++
                    if (failRequests) throw SourceException(
                        SourceErrorCode.VERIFICATION_REQUIRED,
                        SourceDiagnostic("refresh-directory", "directory", safeCode = "verification-required"),
                    )
                    SourceDirectory(book.identity, listOf(chapter))
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareLocalDetail(book)
            val owner = SourceDetailRouteOwner(flow, SavedStateHandle()) {}

            owner.execute(SourceDetailRouteOwner.Command.CACHE_DETAIL.name)

            assertTrue(owner.cacheState.selecting)
            assertEquals(0, detailRequests)
            assertEquals(0, directoryRequests)
            owner.handleCacheAction(DetailCacheAction.Close)
            owner.execute(SourceDetailRouteOwner.Command.REFRESH_DETAIL.name)

            assertEquals(1, detailRequests)
            assertEquals(1, directoryRequests)
            assertEquals("缓存简介", (owner.state as SourceBookState.Content).value.description)
            assertEquals(chapter, (owner.directoryState as SourceBookState.Content).value.chapters.single())

            failRequests = true
            owner.execute(SourceDetailRouteOwner.Command.REFRESH_DETAIL.name)

            assertEquals(2, detailRequests)
            assertEquals(2, directoryRequests)
            assertEquals("缓存简介", (owner.state as SourceBookState.Content).value.description)
            assertEquals(chapter, (owner.directoryState as SourceBookState.Content).value.chapters.single())
            assertEquals("source-read-failed", owner.mutation?.safeCode)
        }
    }

    @Test
    fun failed_or_cancelled_chapter_cache_never_claims_a_durable_download() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "cache-interrupt", "中断缓存")
        val first = SourceChapter("interrupt-1", "第一章", "https://www.wenku8.net/novel/2/200/interrupt-1.htm")
        val second = SourceChapter("interrupt-2", "第二章", "https://www.wenku8.net/novel/2/200/interrupt-2.htm")
        val third = SourceChapter("interrupt-3", "第三章", "https://www.wenku8.net/novel/2/200/interrupt-3.htm")
        val secondEntered = CompletableDeferred<Unit>()
        var failRetriedRequests = false
        controller {
            FakeSession(
                detail = { summary -> SourceBookDetail(summary, "缓存简介", emptyList(), "连载") },
                directoryResult = { SourceDirectory(book.identity, listOf(first, second, third)) },
                chapterResult = { chapter, remoteBookId ->
                    when (chapter.chapterId) {
                        first.chapterId -> readerDocument(remoteBookId, chapter)
                        second.chapterId -> if (failRetriedRequests) {
                            throw SourceException(
                                SourceErrorCode.NETWORK_TIMEOUT,
                                SourceDiagnostic("cache-retry", "chapter", safeCode = "network-timeout"),
                            )
                        } else {
                            secondEntered.complete(Unit)
                            CompletableDeferred<Unit>().await()
                            error("Cancelled chapter request resumed")
                        }
                        else -> {
                            check(failRetriedRequests) { "Queued chapter must not start after cancellation" }
                            throw SourceException(
                                SourceErrorCode.NETWORK_TIMEOUT,
                                SourceDiagnostic("cache-retry", "chapter", safeCode = "network-timeout"),
                            )
                        }
                    }
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            val owner = SourceDetailRouteOwner(flow, SavedStateHandle()) {}
            owner.loadAll()
            owner.execute(SourceDetailRouteOwner.Command.CACHE_DETAIL.name)
            owner.handleCacheAction(DetailCacheAction.ToggleAll(setOf(first.chapterId, second.chapterId, third.chapterId)))

            val caching = async { owner.handleCacheAction(DetailCacheAction.Start) }
            secondEntered.await()
            owner.handleCacheAction(DetailCacheAction.Cancel)
            try {
                caching.await()
                error("Expected cache cancellation")
            } catch (_: CancellationException) {
                // Cancellation stops the active request and leaves no false cached status.
            }

            assertEquals(DetailChapterCachePhase.CACHED, owner.cacheState.chapters[first.chapterId])
            assertEquals(DetailChapterCachePhase.CANCELLED, owner.cacheState.chapters[second.chapterId])
            assertEquals(DetailChapterCachePhase.CANCELLED, owner.cacheState.chapters[third.chapterId])
            assertEquals(first.chapterId, flow.requestChapter(book, first, offlineOnly = true).document?.contentId)
            assertEquals(SourceErrorCode.NETWORK_OFFLINE, flow.requestChapter(book, second, offlineOnly = true).failure?.code)
            assertEquals(SourceErrorCode.NETWORK_OFFLINE, flow.requestChapter(book, third, offlineOnly = true).failure?.code)

            failRetriedRequests = true
            owner.handleCacheAction(DetailCacheAction.Start)

            assertEquals(DetailChapterCachePhase.FAILED, owner.cacheState.chapters[second.chapterId])
            assertEquals(DetailChapterCachePhase.FAILED, owner.cacheState.chapters[third.chapterId])
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
    fun canonical_detail_repairs_coverless_remote_mirror_and_refresh_preserves_cover() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val book = summary(sourceId, "202", "网站收藏缺少封面")
        val detailCover = "https://www.wenku8.net/image/2/202/202s.jpg"
        val chapters = listOf(SourceChapter("1", "第一章", "https://www.wenku8.net/novel/2/202/1.htm"))
        putCredential(sourceId)

        controller {
            FakeSession(
                listRemote = { RemoteLibraryPage(listOf(book), null, true) },
                detail = { summary ->
                    SourceBookDetail(summary.copy(coverUrl = detailCover), "简介", emptyList(), "连载")
                },
                directoryResult = { SourceDirectory(book.identity, chapters) },
            )
        }.use { flow ->
            flow.open(packageInfo)
            val remote = SourceRemoteLibraryRouteOwner(flow, { packageInfo }, SavedStateHandle())
            remote.refresh()
            assertEquals(null, remote.books.single().coverUrl)

            flow.prepareBook(remote.books.single())
            SourceDetailRouteOwner(flow, SavedStateHandle()) {}.loadAll()

            val restored = SourceRemoteLibraryRouteOwner(flow, { packageInfo }, SavedStateHandle())
            restored.restore(sourceId)
            assertEquals(detailCover, restored.books.single().coverUrl)

            restored.refresh()
            assertEquals(detailCover, restored.books.single().coverUrl)
            assertEquals(detailCover, library.remoteMirrorSnapshot(sourceId)?.books?.single()?.book?.coverUrl)
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
            val owner = SourceDetailRouteOwner(flow, SavedStateHandle()) {}

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
            val owner = SourceDetailRouteOwner(flow, SavedStateHandle()) {}
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
    fun replacement_load_retires_only_its_own_working_mutation() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "working-cleanup", "加载清理")
        val firstDetailEntered = CompletableDeferred<Unit>()
        val releaseFirstDetail = CompletableDeferred<Unit>()
        var detailRequests = 0
        controller {
            FakeSession(
                detail = { summary ->
                    detailRequests++
                    if (detailRequests == 1) {
                        firstDetailEntered.complete(Unit)
                        withContext(NonCancellable) { releaseFirstDetail.await() }
                    }
                    SourceBookDetail(summary, "简介", emptyList(), "连载")
                },
                directoryResult = {
                    SourceDirectory(
                        book.identity,
                        listOf(SourceChapter("1", "第一章", "https://www.wenku8.net/novel/working-cleanup/1.htm")),
                    )
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            val owner = SourceDetailRouteOwner(flow, SavedStateHandle()) {}
            val stale = async { owner.loadAll(operation = DetailMutationOperation.REFRESH_DETAIL) }
            firstDetailEntered.await()

            owner.loadAll()
            assertNull(owner.mutation)
            owner.execute(SourceDetailRouteOwner.Command.ADD_TO_LIBRARY.name)
            assertEquals(DetailMutationOperation.ADD_TO_LIBRARY, owner.mutation?.operation)
            assertEquals(DetailMutationPhase.SUCCESS, owner.mutation?.phase)

            releaseFirstDetail.complete(Unit)
            stale.await()
            assertTrue(owner.localState.inLibrary)
            assertEquals(DetailMutationOperation.ADD_TO_LIBRARY, owner.mutation?.operation)
        }
    }

    @Test
    fun cancelled_detail_load_releases_only_its_own_working_mutation() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "cancelled-load", "取消加载")
        controller {
            FakeSession(
                detail = { throw CancellationException("native request cancelled") },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            val owner = SourceDetailRouteOwner(flow, SavedStateHandle()) {}

            try {
                owner.loadAll(operation = DetailMutationOperation.REFRESH_DETAIL)
                error("Expected structured cancellation")
            } catch (_: CancellationException) {
                // Native cancellation is control flow, not an error state.
            }

            assertNull(owner.mutation)
            assertTrue(owner.state !is SourceBookState.Failure)
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
            val owner = SourceReaderRouteOwner(flow, SavedStateHandle())

            owner.load()

            assertEquals(listOf(first, second), owner.chapters)
            assertEquals(first, owner.currentChapter)
            assertEquals(first.chapterId, owner.document?.contentId)
            assertEquals(false, owner.loading)
            assertNull(owner.failure)
        }
    }

    @Test
    fun reader_bookmark_retry_restores_exact_target_and_same_chapter_jumps_without_refetching() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "305", "语义书签")
        val first = SourceChapter("30501", "第一章", "https://www.wenku8.net/novel/3/305/30501.htm")
        val second = SourceChapter("30502", "第二章", "https://www.wenku8.net/novel/3/305/30502.htm")
        val previous = ReaderLocator(
            document = DocumentIdentity(book.identity.sourceId, book.identity.remoteBookId, first.chapterId),
            blockId = "p-${first.chapterId}",
            characterOffset = 2,
            capturedAt = SOURCE_FLOW_TEST_TIME,
        )
        val target = previous.copy(
            document = previous.document.copy(contentId = second.chapterId),
            blockId = "p-${second.chapterId}",
            characterOffset = 3,
        )
        val earlier = target.copy(characterOffset = 1)
        val requested = mutableListOf<String>()
        var failSecond = true
        controller {
            FakeSession(
                directoryResult = { SourceDirectory(book.identity, listOf(first, second)) },
                chapterResult = { chapter, remoteBookId ->
                    requested += chapter.chapterId
                    if (chapter == second && failSecond) {
                        failSecond = false
                        throw SourceException(
                            SourceErrorCode.NETWORK_TIMEOUT,
                            SourceDiagnostic("bookmark-retry-fixture", "chapter", "timeout"),
                        )
                    }
                    readerDocument(remoteBookId, chapter)
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            flow.prepareChapter(first)
            flow.saveProgress(previous, LocatorPrecision.EXACT)
            flow.toggleBookmark(target)
            flow.toggleBookmark(earlier)
            val savedState = SavedStateHandle()
            val owner = SourceReaderRouteOwner(flow, savedState)
            owner.load()
            owner.selectBookmark(target)
            assertEquals(SourceErrorCode.NETWORK_TIMEOUT, owner.failure?.code)
            owner.dispose()

            val restored = SourceReaderRouteOwner(flow, SavedStateHandle(
                savedState.keys().associateWith { savedState.get<Any?>(it) },
            ))
            restored.load()
            assertEquals(second.chapterId, restored.document?.contentId)
            assertEquals(target, restored.restoredLocator)
            assertEquals(previous, library.progress(book.identity)?.locator)
            restored.selectBookmark(earlier)
            assertEquals(earlier, restored.restoredLocator)
            restored.selectBookmark(target)
            assertEquals(target, restored.restoredLocator)
            assertEquals(listOf(first.chapterId, second.chapterId, second.chapterId), requested)
            assertEquals(previous, library.progress(book.identity)?.locator)
            restored.dispose()
        }
    }

    @Test
    fun reader_cross_chapter_session_return_does_not_require_origin_bookmark() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "305-return", "跨章返回")
        val first = SourceChapter("305r01", "第一章", "https://www.wenku8.net/novel/3/305/305r01.htm")
        val second = SourceChapter("305r02", "第二章", "https://www.wenku8.net/novel/3/305/305r02.htm")
        val origin = ReaderLocator(
            document = DocumentIdentity(book.identity.sourceId, book.identity.remoteBookId, first.chapterId),
            blockId = "p-${first.chapterId}",
            characterOffset = 4,
            capturedAt = SOURCE_FLOW_TEST_TIME,
        )
        val bookmark = origin.copy(
            document = origin.document.copy(contentId = second.chapterId),
            blockId = "p-${second.chapterId}",
            characterOffset = 9,
        )
        val requested = mutableListOf<String>()
        controller {
            FakeSession(
                directoryResult = { SourceDirectory(book.identity, listOf(first, second)) },
                chapterResult = { chapter, remoteBookId ->
                    requested += chapter.chapterId
                    readerDocument(remoteBookId, chapter)
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            flow.prepareChapter(first)
            flow.toggleBookmark(bookmark)
            val owner = SourceReaderRouteOwner(flow, SavedStateHandle())
            owner.load()

            owner.selectBookmark(bookmark)
            assertEquals(second.chapterId, owner.document?.contentId)
            assertEquals(bookmark, owner.restoredLocator)
            assertTrue(flow.bookmarks(book.identity).none { it.namesSameBookmarkPositionAs(origin) })

            owner.selectLocator(origin)

            assertEquals(first.chapterId, owner.document?.contentId)
            assertEquals(origin, owner.restoredLocator)
            assertNull(owner.failure)
            assertEquals(listOf(first.chapterId, second.chapterId), requested)
            owner.dispose()
        }
    }

    @Test
    fun sameChapterBookmarkRestorationRejectsStaleProgressCallbackUntilTheNewGenerationCommits() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "306", "书签回调围栏")
        val chapter = SourceChapter("30601", "第一章", "https://www.wenku8.net/novel/3/306/30601.htm")
        val oldLocator = ReaderLocator(
            document = DocumentIdentity(book.identity.sourceId, book.identity.remoteBookId, chapter.chapterId),
            blockId = "p-${chapter.chapterId}",
            characterOffset = 1,
            capturedAt = SOURCE_FLOW_TEST_TIME,
        )
        val bookmark = oldLocator.copy(characterOffset = 7)
        controller {
            FakeSession(
                directoryResult = { SourceDirectory(book.identity, listOf(chapter)) },
                chapterResult = { requested, remoteBookId -> readerDocument(remoteBookId, requested) },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            flow.prepareChapter(chapter)
            flow.toggleBookmark(bookmark)
            val owner = SourceReaderRouteOwner(flow, SavedStateHandle())
            owner.load()
            val oldDocumentGeneration = owner.documentGeneration

            owner.selectBookmark(bookmark)

            assertEquals(bookmark, owner.restoredLocator)
            assertNull(library.progress(book.identity))
            owner.saveProgress(oldLocator, LocatorPrecision.EXACT, oldDocumentGeneration)
            assertNull(library.progress(book.identity))

            owner.saveProgress(bookmark, LocatorPrecision.EXACT, owner.documentGeneration)
            assertEquals(bookmark, library.progress(book.identity)?.locator)
            owner.dispose()
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
            override fun cached(request: CoverRequest): CoverUiState.Ready? = null
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
            val owner = SourceReaderRouteOwner(flow, SavedStateHandle())
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
    fun reader_preloads_one_adjacent_chapter_and_two_images_then_consumes_memory_cache() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "preload-1", "预加载")
        val first = SourceChapter("preload-101", "第一章", "https://example.test/preload-101")
        val second = SourceChapter("preload-102", "第二章", "https://example.test/preload-102")
        val images = (1..3).map { index ->
            ReaderBlock.Image(
                blockId = "preload-image-$index",
                url = "https://pic.example.test/preload-$index.webp",
                altText = "插图$index",
                width = 900,
                height = 1200,
            )
        }
        val requestedChapters = mutableListOf<String>()
        val mediaRequests = mutableListOf<CoverRequest>()
        val repository = object : CoverRepository {
            override fun cached(request: CoverRequest): CoverUiState.Ready? = null
            override fun observe(request: CoverRequest): Flow<CoverUiState> = flow {
                mediaRequests += request
                emit(CoverUiState.Failed(CoverFailureReason.NETWORK, request.fallback))
            }
        }
        controller {
            FakeSession(
                directoryResult = { SourceDirectory(book.identity, listOf(first, second)) },
                chapterResult = { requested, remoteBookId ->
                    requestedChapters += requested.chapterId
                    readerDocument(remoteBookId, requested).let { document ->
                        if (requested == first) document.copy(blocks = document.blocks + images) else document
                    }
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            flow.prepareChapter(first)
            val owner = SourceReaderRouteOwner(flow, SavedStateHandle())

            owner.load()
            assertEquals(listOf(first.chapterId), requestedChapters)
            owner.preloadAdjacent(
                scope = this,
                enabled = true,
                repository = repository,
                packageRevision = packageInfo.packageSha256,
                credentialRevision = "credential-revision",
                settleDelayMillis = 0L,
            )
            withTimeout(1_000) {
                while (requestedChapters.count { it == second.chapterId } < 1 || mediaRequests.size < 2) yield()
            }

            assertEquals(first.chapterId, owner.document?.contentId)
            assertEquals(1, requestedChapters.count { it == second.chapterId })
            assertEquals(images.take(2).map { it.url }, mediaRequests.map { it.transportUrl })
            assertTrue(owner.imageStates.values.none { it is CoverUiState.Failed })
            assertNull(library.progress(book.identity))

            owner.selectChapter(second)
            owner.load()

            assertEquals(second.chapterId, owner.document?.contentId)
            assertEquals(1, requestedChapters.count { it == second.chapterId })
            assertNull(library.progress(book.identity))
            owner.dispose()
        }
    }

    @Test
    fun reader_preload_failure_is_silent_until_foreground_navigation() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "preload-2", "预加载失败")
        val first = SourceChapter("preload-201", "第一章", "https://example.test/preload-201")
        val second = SourceChapter("preload-202", "第二章", "https://example.test/preload-202")
        val requestedChapters = mutableListOf<String>()
        controller {
            FakeSession(
                directoryResult = { SourceDirectory(book.identity, listOf(first, second)) },
                chapterResult = { requested, remoteBookId ->
                    requestedChapters += requested.chapterId
                    if (requested == second) {
                        throw SourceException(
                            SourceErrorCode.VERIFICATION_REQUIRED,
                            SourceDiagnostic("preload-verification", "reader-preload", "verification-required"),
                        )
                    }
                    readerDocument(remoteBookId, requested)
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            flow.prepareChapter(first)
            val owner = SourceReaderRouteOwner(flow, SavedStateHandle())

            owner.load()
            owner.preloadAdjacent(this, true, null, null, null, settleDelayMillis = 0L)
            withTimeout(1_000) {
                while (requestedChapters.count { it == second.chapterId } < 1) yield()
            }

            assertEquals(first.chapterId, owner.document?.contentId)
            assertNull(owner.failure)
            assertNull(library.progress(book.identity))

            owner.selectChapter(second)
            owner.load()

            assertNull(owner.document)
            assertEquals(SourceErrorCode.VERIFICATION_REQUIRED, owner.failure?.code)
            assertEquals(2, requestedChapters.count { it == second.chapterId })
            owner.dispose()
        }
    }

    @Test
    fun cancelled_reader_preload_cannot_publish_a_late_adjacent_document() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(SOURCE_FLOW_TEST_SOURCE_ID, "preload-3", "预加载取消")
        val first = SourceChapter("preload-301", "第一章", "https://example.test/preload-301")
        val second = SourceChapter("preload-302", "第二章", "https://example.test/preload-302")
        val third = SourceChapter("preload-303", "第三章", "https://example.test/preload-303")
        val adjacentEntered = CompletableDeferred<Unit>()
        val releaseAdjacent = CompletableDeferred<Unit>()
        val requestedChapters = mutableListOf<String>()
        controller {
            FakeSession(
                directoryResult = { SourceDirectory(book.identity, listOf(first, second, third)) },
                chapterResult = { requested, remoteBookId ->
                    requestedChapters += requested.chapterId
                    if (requested == second && requestedChapters.count { it == second.chapterId } == 1) {
                        adjacentEntered.complete(Unit)
                        withContext(NonCancellable) { releaseAdjacent.await() }
                    }
                    readerDocument(remoteBookId, requested)
                },
            )
        }.use { flow ->
            flow.open(packageInfo)
            flow.prepareBook(book)
            flow.prepareChapter(first)
            val owner = SourceReaderRouteOwner(flow, SavedStateHandle())

            owner.load()
            owner.preloadAdjacent(this, true, null, null, null, settleDelayMillis = 0L)
            adjacentEntered.await()
            owner.selectChapter(third)
            releaseAdjacent.complete(Unit)
            yield()
            owner.load()

            assertEquals(third.chapterId, owner.document?.contentId)
            owner.selectChapter(second)
            owner.load()

            assertEquals(second.chapterId, owner.document?.contentId)
            assertEquals(2, requestedChapters.count { it == second.chapterId })
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
            val owner = SourceReaderRouteOwner(flow, SavedStateHandle())
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
            val owner = SourceReaderRouteOwner(flow, SavedStateHandle())
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
        var reads = 0
        var writes = 0
        val first = summary(sourceId, "401", "网站收藏一").copy(canonicalUrl = "", remoteTargetId = "first")
        val second = summary(sourceId, "402", "网站收藏二").copy(canonicalUrl = "", remoteTargetId = "second")
        controller {
            FakeSession(
                listRemote = {
                    reads++
                    RemoteLibraryPage(listOf(first, second), null, true)
                },
                targetsResult = {
                    RemoteLibraryTargetsResult(
                        sourceId,
                        listOf(
                            RemoteTarget("first", "第一分类", null, "folder"),
                            RemoteTarget("second", "第二分类", null, "folder"),
                        ),
                    )
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
            assertEquals(false, owner.loading)
            assertEquals(listOf(first, second), owner.books)
            assertTrue(library.libraryEntries().isEmpty())

            owner.toggleSelection(first)
            val restored = SourceRemoteLibraryRouteOwner(flow, { packageInfo }, savedState)
            assertEquals(setOf(remoteLibrarySelectionId(first)), restored.selectedIds)
            assertEquals(RemoteLibraryRouteStatus.Idle, restored.status)
            assertTrue(restored.books.isEmpty())
            assertEquals(1, reads)

            assertNull(owner.requestCopy())
            assertTrue(owner.copyConfirmationVisible)
            assertEquals(RemoteLibraryCopyResult(total = 1, added = 1), owner.confirmCopy())
            assertTrue(requireNotNull(library.sourceRemotePolicy(sourceId)).firstImportPromptDismissed)
            assertEquals(0, writes)
            assertEquals(setOf(first.identity), library.libraryEntries().map { it.book.identity }.toSet())

            val restoredAfterConfirmation = SourceRemoteLibraryRouteOwner(flow, { packageInfo }, SavedStateHandle())
            restoredAfterConfirmation.restore(sourceId)
            restoredAfterConfirmation.selectTarget("first")
            assertEquals(RemoteLibraryCopyResult(total = 1, added = 0), restoredAfterConfirmation.requestCopy())
            restoredAfterConfirmation.selectTarget(null)
            assertEquals(RemoteLibraryCopyResult(total = 2, added = 1), restoredAfterConfirmation.requestCopy())
            assertEquals(false, restoredAfterConfirmation.copyConfirmationVisible)
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
            assertEquals(false, owner.loading)
            owner.refresh()
            assertEquals(RemoteLibraryRouteStatus.VerificationRequired, owner.status)
            assertEquals(false, owner.loading)
            owner.refresh()
            assertEquals(RemoteLibraryRouteStatus.Cancelled, owner.status)
            assertEquals(false, owner.loading)
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
