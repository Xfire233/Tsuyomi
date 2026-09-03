/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage

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
}
