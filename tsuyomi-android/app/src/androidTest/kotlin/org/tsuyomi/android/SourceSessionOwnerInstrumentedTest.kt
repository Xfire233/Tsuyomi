/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import kotlinx.coroutines.CompletableDeferred
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
    fun prepared_different_source_keeps_old_session_usable_until_commit_and_rejects_delayed_old_home() = runBlocking {
        val firstPackage = installFixture()
        val installer = SourceInstallController(context, library)
        installer.restoreInstalled()
        val secondPackage = installSignedSwitchOverlay(installer, "source-switch-home")
        val oldHomeStarted = CompletableDeferred<Unit>()
        val releaseOldHome = CompletableDeferred<Unit>()
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
                        identity = BookIdentity(sourceId, "1001"),
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
