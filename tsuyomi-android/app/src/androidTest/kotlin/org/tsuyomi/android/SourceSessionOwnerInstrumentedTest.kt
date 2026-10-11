/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assert.assertFalse
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.feature.search.SearchResultState
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceHomeSection

@RunWith(AndroidJUnit4::class)
internal class SourceSessionOwnerInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun openingReplacementPackageClearsPreviousSourceState() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val firstSession = FakeSession(
            listRemote = { RemoteLibraryPage(listOf(summary(sourceId, "5001", "旧来源")), null, true) },
        )
        val controller = controller { candidate ->
            if (candidate.packageSha256 == packageInfo.packageSha256) firstSession else FakeSession()
        }
        try {
            val pull = controller.pullRemoteLibrary(packageInfo) as RemoteLibraryPullResult.Success
            assertEquals(1, pull.books.size)
            controller.selectBook(pull.books.single())

            controller.open(packageInfo.withPackageSha256(alternateSha(packageInfo.packageSha256)))

            assertEquals(null, controller.selectedBook)
            assertEquals(null, controller.remoteLibrary.selectedBookReconciliation)
        } finally {
            controller.close()
        }
    }

    @Test
    fun failedReplacementOpenStillClearsPreviousSourceState() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val replacement = packageInfo.withPackageSha256(alternateSha(packageInfo.packageSha256))
        val firstSession = FakeSession(
            listRemote = { RemoteLibraryPage(listOf(summary(sourceId, "5002", "旧来源")), null, true) },
        )
        val controller = controller { candidate ->
            if (candidate.packageSha256 == packageInfo.packageSha256) firstSession else error("replacement-open-failed")
        }
        try {
            val pull = controller.pullRemoteLibrary(packageInfo) as RemoteLibraryPullResult.Success
            controller.selectBook(pull.books.single())

            try {
                controller.open(replacement)
                throw AssertionError("Expected replacement open to fail")
            } catch (error: IllegalStateException) {
                assertEquals("replacement-open-failed", error.message)
            }

            assertEquals(null, controller.selectedBook)
            assertEquals(null, controller.remoteLibrary.selectedBookReconciliation)
        } finally {
            controller.close()
        }
    }
    @Test
    fun sameSourceOpenObserversWaitForReadyClientBeforeSearchAndDetail() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val book = summary(sourceId, "ready-race", "Ready race")
        val chapter = SourceChapter("ready-1", "第一章", "https://www.wenku8.net/novel/ready-race/ready-1.htm")
        val opening = CompletableDeferred<Unit>()
        val releaseOpen = CompletableDeferred<Unit>()
        var openCalls = 0
        val session = FakeSession(
            searchResult = { _, _ -> listOf(book) },
            detail = { requested -> SourceBookDetail(requested, "简介", listOf("标签"), "连载") },
            directoryResult = { SourceDirectory(book.identity, listOf(chapter)) },
        )
        val controller = controller {
            openCalls += 1
            opening.complete(Unit)
            releaseOpen.await()
            session
        }
        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) { controller.open(packageInfo) }
            opening.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { controller.open(packageInfo) }
            assertFalse("same-source observer must wait for installed session", second.isCompleted)

            releaseOpen.complete(Unit)
            first.await()
            assertEquals(SourceSessionOpenResult.ALREADY_OPEN, second.await())
            assertEquals(1, openCalls)

            controller.updateQuery("Ready race")
            controller.search()
            val results = controller.searchState as SearchResultState.Results
            assertEquals(listOf(book), results.items)
            val selected = results.items.single()
            controller.prepareBook(selected)
            val detail = controller.requestDetail(selected)
            assertTrue(detail is SourceBookState.Content)
            assertEquals(book.identity, (detail as SourceBookState.Content).value.summary.identity)
            val directory = controller.requestDirectory(selected)
            assertTrue(directory is SourceBookState.Content)
            assertEquals(listOf(chapter), (directory as SourceBookState.Content).value.chapters)
        } finally {
            releaseOpen.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun cancelledOpenPromotesWaitingSameSourceObserver() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(packageInfo.manifest.sourceId.value, "cancel-open", "取消打开")
        val opening = CompletableDeferred<Unit>()
        var openCalls = 0
        val controller = controller {
            openCalls += 1
            if (openCalls == 1) {
                opening.complete(Unit)
                awaitCancellation()
            }
            FakeSession(searchResult = { _, _ -> listOf(book) })
        }
        try {
            val cancelled = async(start = CoroutineStart.UNDISPATCHED) { controller.open(packageInfo) }
            opening.await()
            val observer = async(start = CoroutineStart.UNDISPATCHED) { controller.open(packageInfo) }
            assertFalse(observer.isCompleted)

            cancelled.cancelAndJoin()
            assertEquals(SourceSessionOpenResult.PACKAGE_CHANGED, observer.await())
            assertEquals(2, openCalls)
            controller.updateQuery("取消打开")
            controller.search()
            assertEquals(listOf(book), (controller.searchState as SearchResultState.Results).items)
        } finally {
            controller.close()
        }
    }

    @Test
    fun cancellingSameSourceWaiterLeavesSurvivingHomeInitializationReady() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(packageInfo.manifest.sourceId.value, "surviving-home", "存活来源")
        val opening = CompletableDeferred<Unit>()
        val releaseOpen = CompletableDeferred<Unit>()
        val flow = controller {
            opening.complete(Unit)
            releaseOpen.await()
            FakeSession(searchResult = { _, _ -> listOf(book) })
        }
        try {
            val home = async(start = CoroutineStart.UNDISPATCHED) { flow.openHome(packageInfo) }
            opening.await()
            val cancelledWaiter = async(start = CoroutineStart.UNDISPATCHED) { flow.open(packageInfo) }
            cancelledWaiter.cancelAndJoin()
            releaseOpen.complete(Unit)
            assertTrue(home.await() in setOf(SourceSessionOpenResult.OPENED, SourceSessionOpenResult.PACKAGE_CHANGED, SourceSessionOpenResult.ALREADY_OPEN))
            flow.updateQuery("存活来源")
            flow.search()
            assertEquals(listOf(book), (flow.searchState as SearchResultState.Results).items)
        } finally {
            releaseOpen.complete(Unit)
            flow.close()
        }
    }

    @Test
    fun allSurvivingSameSourceWaitersJoinReplacementAfterInitializerCancellation() = runBlocking {
        val packageInfo = installFixture()
        val book = summary(packageInfo.manifest.sourceId.value, "replacement", "替代会话")
        val firstOpening = CompletableDeferred<Unit>()
        val replacementOpening = CompletableDeferred<Unit>()
        val releaseReplacement = CompletableDeferred<Unit>()
        var opens = 0
        val flow = controller {
            if (++opens == 1) {
                firstOpening.complete(Unit)
                awaitCancellation()
            }
            replacementOpening.complete(Unit)
            releaseReplacement.await()
            FakeSession(searchResult = { _, _ -> listOf(book) })
        }
        try {
            val initializer = async(start = CoroutineStart.UNDISPATCHED) { flow.open(packageInfo) }
            firstOpening.await()
            val first = async(start = CoroutineStart.UNDISPATCHED) { flow.open(packageInfo) }
            val second = async(start = CoroutineStart.UNDISPATCHED) { flow.open(packageInfo) }
            initializer.cancelAndJoin()
            replacementOpening.await()
            assertFalse(first.isCompleted)
            assertFalse(second.isCompleted)
            releaseReplacement.complete(Unit)
            val ready = setOf(SourceSessionOpenResult.OPENED, SourceSessionOpenResult.PACKAGE_CHANGED, SourceSessionOpenResult.ALREADY_OPEN)
            assertTrue(first.await() in ready)
            assertTrue(second.await() in ready)
            flow.updateQuery("替代会话")
            flow.search()
            assertEquals(listOf(book), (flow.searchState as SearchResultState.Results).items)
        } finally {
            releaseReplacement.complete(Unit)
            flow.close()
        }
    }

    @Test
    fun newerDifferentSourceOpenDoesNotWaitForOrGetOverwrittenByStaleOpen() = runBlocking {
        val firstPackage = installFixture()
        val installer = SourceInstallController(context, library)
        installer.restoreInstalled()
        val secondPackage = installSignedSwitchOverlay(installer, "source-switch-search")
        val firstBook = summary(firstPackage.manifest.sourceId.value, "old-result", "旧来源结果")
        val secondBook = summary(secondPackage.manifest.sourceId.value, "new-result", "新来源结果")
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val controller = controller { candidate ->
            if (candidate.packageSha256 == firstPackage.packageSha256) {
                firstEntered.complete(Unit)
                releaseFirst.await()
                FakeSession(searchResult = { _, _ -> listOf(firstBook) })
            } else {
                FakeSession(searchResult = { _, _ -> listOf(secondBook) })
            }
        }
        try {
            val oldOpen = async(start = CoroutineStart.UNDISPATCHED) { controller.open(firstPackage) }
            firstEntered.await()
            val newOpen = async(start = CoroutineStart.UNDISPATCHED) { controller.open(secondPackage) }
            assertEquals(SourceSessionOpenResult.PACKAGE_CHANGED, newOpen.await())

            controller.updateQuery("新来源")
            controller.search()
            assertEquals(listOf(secondBook), (controller.searchState as SearchResultState.Results).items)
            releaseFirst.complete(Unit)
            assertEquals(SourceSessionOpenResult.SUPERSEDED, oldOpen.await())
            assertEquals(listOf(secondBook), (controller.searchState as SearchResultState.Results).items)
        } finally {
            releaseFirst.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun trustRevocationDuringOpenCannotReportReadinessToWaiter() = runBlocking {
        val packageInfo = installFixture()
        val opening = CompletableDeferred<Unit>()
        val releaseOpen = CompletableDeferred<Unit>()
        var trusted = true
        val owner = SourceSessionOwner(
            directActionTokens = DirectActionTokenRegistry(),
            openSession = {
                opening.complete(Unit)
                releaseOpen.await()
                FakeSession()
            },
            isPackageTrusted = { trusted },
        )
        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) { owner.open(packageInfo) }
            opening.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { owner.open(packageInfo) }
            assertFalse(second.isCompleted)
            trusted = false
            releaseOpen.complete(Unit)
            assertEquals(SourceSessionOpenResult.UNAVAILABLE, first.await())
            assertEquals(SourceSessionOpenResult.UNAVAILABLE, second.await())
            assertEquals(null, owner.active())
        } finally {
            releaseOpen.complete(Unit)
            owner.close()
        }
    }

    @Test
    fun removingSourceDuringOpenFencesLateSession() = runBlocking {
        val packageInfo = installFixture()
        val opening = CompletableDeferred<Unit>()
        val releaseOpen = CompletableDeferred<Unit>()
        val controller = controller {
            opening.complete(Unit)
            releaseOpen.await()
            FakeSession()
        }
        try {
            val pending = async(start = CoroutineStart.UNDISPATCHED) { controller.open(packageInfo) }
            opening.await()
            controller.removeSource(packageInfo.manifest.sourceId.value)
            releaseOpen.complete(Unit)
            assertEquals(SourceSessionOpenResult.UNAVAILABLE, pending.await())
        } finally {
            releaseOpen.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun closeDuringOpenDoesNotPublishReadiness() = runBlocking {
        val packageInfo = installFixture()
        val opening = CompletableDeferred<Unit>()
        val releaseOpen = CompletableDeferred<Unit>()
        val owner = SourceSessionOwner(
            directActionTokens = DirectActionTokenRegistry(),
            openSession = {
                opening.complete(Unit)
                releaseOpen.await()
                FakeSession()
            },
            isPackageTrusted = { true },
        )
        val pending = async(start = CoroutineStart.UNDISPATCHED) { owner.open(packageInfo) }
        opening.await()
        owner.close()
        releaseOpen.complete(Unit)
        assertEquals(SourceSessionOpenResult.UNAVAILABLE, pending.await())
    }

    @Test
    fun prepared_different_source_keeps_old_session_usable_until_commit_and_rejects_delayed_old_home() = runBlocking {
        val firstPackage = installFixture()
        val installer = SourceInstallController(context, library)
        installer.restoreInstalled()
        val secondPackage = installSignedSwitchOverlay(installer, "source-switch-home")
        val oldHomeStarted = CompletableDeferred<Unit>()
        val releaseOldHome = CompletableDeferred<Unit>()
        var oldRequest: kotlinx.coroutines.Job? = null
        val openedSourceIds = mutableListOf<String>()
        val searchedSourceIds = mutableListOf<String>()
        val controller = controller { candidate ->
            openedSourceIds += candidate.manifest.sourceId.value
            FakeSession(
                searchResult = { _, _ ->
                    searchedSourceIds += candidate.manifest.sourceId.value
                    emptyList()
                },
                homeResult = { _, _, _ ->
                    if (candidate.manifest.sourceId == firstPackage.manifest.sourceId) {
                        oldRequest = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                        oldHomeStarted.complete(Unit)
                        withContext(NonCancellable) { releaseOldHome.await() }
                        homePage("旧来源", candidate.manifest.sourceId.value)
                    } else {
                        homePage("新来源", candidate.manifest.sourceId.value)
                    }
                },
            )
        }
        try {
            controller.open(firstPackage)
            withContext(Dispatchers.Main) {
                controller.home.ensureInitial(firstPackage.manifest.sourceId.value, firstPackage.packageSha256) { filters, cursor ->
                    controller.loadHome(filters, cursor)
                }
            }
            oldHomeStarted.await()

            val prepared = requireNotNull(controller.prepareSourceSession(secondPackage))
            controller.updateQuery("still-first")
            controller.search()
            assertEquals(listOf(firstPackage.manifest.sourceId.value), searchedSourceIds)

            assertTrue(controller.commitPreparedSourceSession(prepared))
            controller.commitSourceSwitch(secondPackage)
            withContext(Dispatchers.Main) {
                controller.home.ensureInitial(secondPackage.manifest.sourceId.value, secondPackage.packageSha256) { filters, cursor ->
                    controller.loadHome(filters, cursor)
                }
            }
            val targetPublished = withTimeoutOrNull(5_000) {
                while (controller.home.activePage?.title != "新来源") delay(10)
                true
            } ?: false
            check(targetPublished) { "Prepared target Home was not published: ${controller.home.state}" }
            releaseOldHome.complete(Unit)
            kotlinx.coroutines.withTimeout(5_000) { requireNotNull(oldRequest).join() }

            assertEquals("新来源", controller.home.activePage?.title)
            assertEquals(
                listOf(firstPackage.manifest.sourceId.value, secondPackage.manifest.sourceId.value),
                openedSourceIds,
            )
        } finally {
            releaseOldHome.complete(Unit)
            controller.close()
        }
    }

    private fun homePage(title: String, sourceId: String) = org.tsuyomi.shared.sourcecontract.SourceHomePage(
        title = title,
        schemaVersion = 1,
        filters = emptyList(),
        selectedFilters = emptyMap(),
        sections = listOf(
            SourceHomeSection(
                id = "catalog",
                title = title,
                items = listOf(
                    SourceBookSummary(
                        identity = BookIdentity.Remote(sourceId, "1001"),
                        title = "测试书目",
                        author = "测试作者",
                        coverUrl = null,
                        canonicalUrl = "https://example.invalid/book/1001",
                    ),
                ),
            ),
        ),
        nextCursor = null,
        complete = true,
    )
}
