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
    fun signedFixturePullAndDirectAddCompleteEndToEnd() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val controller = controller()
        try {
            val pull = controller.pullRemoteLibrary(packageInfo, SOURCE_FLOW_TEST_TIME)
            assertEquals(RemoteLibraryPullResult.Success(total = 2, newlyAdded = 2), pull)
            assertEquals(setOf("1234", "5678"), library.libraryEntries().map { it.book.identity.remoteBookId }.toSet())

            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            val selected = summary(sourceId, "9999", "远程直加测试")
            controller.selectBook(selected)

            assertEquals(RemoteAddUiResult.Confirmed, controller.addSelectedBook(SOURCE_FLOW_TEST_TIME.plusSeconds(1)))
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
            val pull = async { controller.pullRemoteLibrary(packageInfo, SOURCE_FLOW_TEST_TIME) }
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
            val pull = async { controller.pullRemoteLibrary(packageInfo, SOURCE_FLOW_TEST_TIME) }
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
}
