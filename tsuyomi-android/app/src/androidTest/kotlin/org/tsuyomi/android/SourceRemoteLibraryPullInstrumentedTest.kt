/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage

@RunWith(AndroidJUnit4::class)
internal class SourceRemoteLibraryPullInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun signedFixturePullCopiesLocallyOnlyAfterExplicitCommand() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val controller = controller()
        try {
            val pull = controller.pullRemoteLibrary(packageInfo) as RemoteLibraryPullResult.Success
            assertEquals(setOf("1234", "5678"), pull.books.map { it.identity.remoteBookId }.toSet())
            assertTrue(library.libraryEntries().isEmpty())
            assertEquals(RemoteLibraryCopyResult(total = 2, added = 2), controller.copyRemoteLibraryToLocal(pull.books))
            assertEquals(setOf("1234", "5678"), library.libraryEntries().map { it.book.identity.remoteBookId }.toSet())

            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            val selected = summary(sourceId, "9999", "远程直加测试")
            controller.selectBook(selected)

            assertEquals(RemoteAddUiResult.Confirmed, controller.addSelectedBookToWebsite(SOURCE_FLOW_TEST_TIME.plusSeconds(1)))
            val entry = requireNotNull(library.libraryEntries().firstOrNull { it.book.identity == selected.identity })
            assertEquals(RemoteReconciliationState.CONFIRMED, entry.reconciliation)
        } finally {
            controller.close()
        }
    }

    @Test
    fun packageOwnerChangeDuringPaginationRejectsWholeMerge() = runBlocking {
        val packageInfo = installFixture()
        val secondPageStarted = CompletableDeferred<Unit>()
        val releaseSecondPage = CompletableDeferred<Unit>()
        val firstSession = FakeSession(
            listRemote = { cursor ->
                if (cursor == null) {
                    RemoteLibraryPage(listOf(summary(packageInfo.manifest.sourceId.value, "1001", "第一页")), "page-2", false)
                } else {
                    secondPageStarted.complete(Unit)
                    releaseSecondPage.await()
                    RemoteLibraryPage(listOf(summary(packageInfo.manifest.sourceId.value, "1002", "第二页")), null, true)
                }
            },
        )
        val replacementSession = FakeSession()
        val controller = controller { candidate ->
            if (candidate.packageSha256 == packageInfo.packageSha256) firstSession else replacementSession
        }
        try {
            val pull = async { controller.pullRemoteLibrary(packageInfo) }
            withTimeout(5_000) { secondPageStarted.await() }
            controller.open(packageInfo.withPackageSha256(alternateSha(packageInfo.packageSha256)))
            releaseSecondPage.complete(Unit)

            assertEquals(RemoteLibraryPullResult.Failure("source-changed"), pull.await())
            assertTrue(library.libraryEntries().isEmpty())
        } finally {
            releaseSecondPage.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun sourceRevocationBeforeFinalMergeRejectsWholeMerge() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val secondPageStarted = CompletableDeferred<Unit>()
        val releaseSecondPage = CompletableDeferred<Unit>()
        val session = FakeSession(
            listRemote = { cursor ->
                if (cursor == null) {
                    RemoteLibraryPage(listOf(summary(sourceId, "2001", "第一页")), "page-2", false)
                } else {
                    secondPageStarted.complete(Unit)
                    releaseSecondPage.await()
                    RemoteLibraryPage(listOf(summary(sourceId, "2002", "第二页")), null, true)
                }
            },
        )
        val controller = controller { session }
        try {
            val pull = async { controller.pullRemoteLibrary(packageInfo) }
            withTimeout(5_000) { secondPageStarted.await() }
            val availability = requireNotNull(library.sourceAvailability(sourceId))
            library.setSourceAvailability(sourceId, availability.verifiedVersion, false, availability.generation + 1)
            releaseSecondPage.complete(Unit)

            assertEquals(RemoteLibraryPullResult.Failure("source-changed"), pull.await())
            assertTrue(library.libraryEntries().isEmpty())
        } finally {
            releaseSecondPage.complete(Unit)
            controller.close()
        }
    }
    @Test
    fun duplicateCursorIsRejectedWithoutLocalWrites() = runBlocking {
        val packageInfo = installFixture()
        val controller = controller {
            FakeSession(listRemote = { RemoteLibraryPage(emptyList(), "duplicate", false) })
        }
        try {
            controller.open(packageInfo)
            assertEquals(RemoteLibraryPullResult.Failure("duplicate-cursor"), controller.pullRemoteLibrary(packageInfo))
            assertTrue(library.libraryEntries().isEmpty())
        } finally {
            controller.close()
        }
    }

    @Test
    fun pageLimitIsRejectedWithoutLocalWrites() = runBlocking {
        val packageInfo = installFixture()
        var page = 0
        val controller = controller {
            FakeSession(listRemote = { RemoteLibraryPage(emptyList(), "page-${++page}", false) })
        }
        try {
            controller.open(packageInfo)
            assertEquals(RemoteLibraryPullResult.Failure("page-limit"), controller.pullRemoteLibrary(packageInfo))
            assertEquals(100, page)
            assertTrue(library.libraryEntries().isEmpty())
        } finally {
            controller.close()
        }
    }

    @Test
    fun recordLimitIsRejectedWithoutLocalWrites() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        var page = 0
        val controller = controller {
            FakeSession(
                listRemote = {
                    page++
                    val start = (page - 1) * 100 + 1
                    val count = if (page == 51) 1 else 100
                    val records = (start until start + count).map { summary(sourceId, it.toString(), "收藏 $it") }
                    RemoteLibraryPage(records, if (page == 51) null else "records-$page", page == 51)
                },
            )
        }
        try {
            controller.open(packageInfo)
            assertEquals(RemoteLibraryPullResult.Failure("record-limit"), controller.pullRemoteLibrary(packageInfo))
            assertTrue(library.libraryEntries().isEmpty())
        } finally {
            controller.close()
        }
    }

    @Test
    fun aggregateLimitIsRejectedWithoutLocalWrites() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val oversized = summary(sourceId, "oversized", "超大收藏").copy(
            canonicalUrl = "https://www.wenku8.net/book/" + "a".repeat(8 * 1024 * 1024),
        )
        val controller = controller {
            FakeSession(listRemote = { RemoteLibraryPage(listOf(oversized), null, true) })
        }
        try {
            controller.open(packageInfo)
            assertEquals(RemoteLibraryPullResult.Failure("aggregate-limit"), controller.pullRemoteLibrary(packageInfo))
            assertTrue(library.libraryEntries().isEmpty())
        } finally {
            controller.close()
        }
    }

}
