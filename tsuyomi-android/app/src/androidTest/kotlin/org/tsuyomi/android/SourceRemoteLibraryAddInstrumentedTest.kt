/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddOutcome
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddResult
import org.tsuyomi.shared.sourcecontract.SourceBookDetail

@RunWith(AndroidJUnit4::class)
internal class SourceRemoteLibraryAddInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun leaseChangeAfterTransportAcceptanceEndsUnresolved() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val accepted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val selected = summary(sourceId, "3001", "接受后状态变化")
        putCredential(sourceId)
        val session = FakeSession(
            detail = { SourceBookDetail(it, "fixture", emptyList(), "连载中") },
            addRemote = { remoteBookId, token ->
                directActionTokens.accept(sourceId, remoteBookId, token)
                accepted.complete(Unit)
                releaseResponse.await()
                RemoteLibraryAddResult(selected.identity, RemoteLibraryAddOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(selected)

            val add = async { controller.addSelectedBookToWebsite(SOURCE_FLOW_TEST_TIME) }
            withTimeout(5_000) { accepted.await() }
            val availability = requireNotNull(library.sourceAvailability(sourceId))
            library.setSourceAvailability(sourceId, availability.verifiedVersion, false, availability.generation + 1)
            releaseResponse.complete(Unit)

            assertEquals(RemoteAddUiResult.Unresolved, add.await())
            val entry = requireNotNull(library.libraryEntries().single { it.book.identity == selected.identity })
            assertEquals(RemoteReconciliationState.UNRESOLVED, entry.reconciliation)
        } finally {
            releaseResponse.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun completedAddDoesNotOverwriteANewerBookSelection() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val accepted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val firstBook = summary(sourceId, "3002", "第一本")
        val secondBook = summary(sourceId, "3003", "第二本")
        val session = FakeSession(
            addRemote = { remoteBookId, token ->
                directActionTokens.accept(sourceId, remoteBookId, token)
                accepted.complete(Unit)
                releaseResponse.await()
                RemoteLibraryAddResult(firstBook.identity, RemoteLibraryAddOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(firstBook)

            val add = async { controller.addSelectedBookToWebsite(SOURCE_FLOW_TEST_TIME) }
            withTimeout(5_000) { accepted.await() }
            controller.selectBook(secondBook)
            releaseResponse.complete(Unit)

            assertEquals(RemoteAddUiResult.Confirmed, add.await())
            assertEquals(secondBook.identity, controller.selectedBook?.identity)
            assertFalse(controller.remoteLibrary.selectedBookInLibrary)
            assertEquals(null, controller.remoteLibrary.selectedBookReconciliation)
            assertEquals(
                RemoteReconciliationState.CONFIRMED,
                library.libraryEntries().single { it.book.identity == firstBook.identity }.reconciliation,
            )
        } finally {
            releaseResponse.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun preAcceptanceLeaseRejectionCancelsDurableOperation() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val transportEntered = CompletableDeferred<Unit>()
        val releaseAcceptance = CompletableDeferred<Unit>()
        val selected = summary(sourceId, "4001", "接受前状态变化")
        val session = FakeSession(
            addRemote = { remoteBookId, token ->
                transportEntered.complete(Unit)
                releaseAcceptance.await()
                directActionTokens.accept(sourceId, remoteBookId, token)
                RemoteLibraryAddResult(selected.identity, RemoteLibraryAddOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(selected)

            val add = async { controller.addSelectedBookToWebsite(SOURCE_FLOW_TEST_TIME) }
            withTimeout(5_000) { transportEntered.await() }
            val availability = requireNotNull(library.sourceAvailability(sourceId))
            library.setSourceAvailability(sourceId, availability.verifiedVersion, false, availability.generation + 1)
            releaseAcceptance.complete(Unit)

            assertEquals(RemoteAddUiResult.Cancelled, add.await())
            val entry = requireNotNull(library.libraryEntries().single { it.book.identity == selected.identity })
            assertEquals(RemoteReconciliationState.CANCELLED, entry.reconciliation)
        } finally {
            releaseAcceptance.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun disablingWritebackBeforeAcceptanceCancelsWithoutRemoteCommit() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val transportEntered = CompletableDeferred<Unit>()
        val releaseAcceptance = CompletableDeferred<Unit>()
        val selected = summary(sourceId, "4003", "接受前关闭写回")
        val session = FakeSession(
            addRemote = { remoteBookId, token ->
                transportEntered.complete(Unit)
                releaseAcceptance.await()
                directActionTokens.accept(sourceId, remoteBookId, token)
                RemoteLibraryAddResult(selected.identity, RemoteLibraryAddOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(selected)

            val add = async { controller.addSelectedBookToWebsite(SOURCE_FLOW_TEST_TIME) }
            withTimeout(5_000) { transportEntered.await() }
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, false))
            releaseAcceptance.complete(Unit)

            assertEquals(RemoteAddUiResult.Cancelled, add.await())
            assertEquals(
                RemoteReconciliationState.CANCELLED,
                library.libraryEntries().single { it.book.identity == selected.identity }.reconciliation,
            )
        } finally {
            releaseAcceptance.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun localAddRemainsLocalWhenWebsiteWritebackIsReady() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val selected = summary(sourceId, "4004", "仅加入本地")
        val controller = controller { FakeSession() }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(selected)
            assertTrue(controller.remoteLibrary.selectedBookAddWritesRemote)

            assertEquals(RemoteAddUiResult.LocalOnly, controller.addSelectedBook(SOURCE_FLOW_TEST_TIME))
            assertTrue(controller.remoteLibrary.selectedBookInLibrary)
            assertEquals(null, controller.remoteLibrary.selectedBookReconciliation)
            assertTrue(controller.removeSelectedBook())
        } finally {
            controller.close()
        }
    }

    @Test
    fun concurrentAddTapsSerializeToOneRemoteOperation() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val transportEntered = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val selected = summary(sourceId, "4005", "并发加入")
        val session = FakeSession(
            addRemote = { remoteBookId, token ->
                calls.incrementAndGet()
                directActionTokens.accept(sourceId, remoteBookId, token)
                transportEntered.complete(Unit)
                releaseResponse.await()
                RemoteLibraryAddResult(selected.identity, RemoteLibraryAddOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(selected)

            val first = async { controller.addSelectedBookToWebsite(SOURCE_FLOW_TEST_TIME) }
            withTimeout(5_000) { transportEntered.await() }
            val second = async { controller.addSelectedBookToWebsite(SOURCE_FLOW_TEST_TIME.plusSeconds(1)) }
            releaseResponse.complete(Unit)

            assertEquals(RemoteAddUiResult.Confirmed, first.await())
            assertEquals(RemoteAddUiResult.Failure("book-already-added"), second.await())
            assertEquals(1, calls.get())
        } finally {
            releaseResponse.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun coroutineCancellationRevokesTokenAndCancelsDurableOperation() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val transportEntered = CompletableDeferred<Unit>()
        val selected = summary(sourceId, "4002", "协程取消")
        val session = FakeSession(
            addRemote = { _, _ ->
                transportEntered.complete(Unit)
                CompletableDeferred<RemoteLibraryAddResult>().await()
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(selected)

            val add = async { controller.addSelectedBookToWebsite(SOURCE_FLOW_TEST_TIME) }
            withTimeout(5_000) { transportEntered.await() }
            add.cancelAndJoin()
            withTimeout(5_000) {
                while (library.libraryEntries().single { it.book.identity == selected.identity }.reconciliation != RemoteReconciliationState.CANCELLED) {
                    kotlinx.coroutines.yield()
                }
            }
            assertEquals(
                RemoteReconciliationState.CANCELLED,
                library.libraryEntries().single { it.book.identity == selected.identity }.reconciliation,
            )
        } finally {
            controller.close()
        }
    }

    @Test
    fun unresolvedRemoteAddRetriesOnlyThroughANewExplicitAction() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val selected = summary(sourceId, "7001", "重试收藏")
        putCredential(sourceId)
        var calls = 0
        val acceptedReconciliationIds = mutableListOf<String>()
        val session = FakeSession(
            detail = { SourceBookDetail(it, "fixture", emptyList(), "连载中") },
            addRemote = { remoteBookId, token ->
                calls += 1
                val binding = directActionTokens.accept(sourceId, remoteBookId, token)
                acceptedReconciliationIds += binding.reconciliationId
                assertEquals(RemoteReconciliationState.IN_FLIGHT.name, reconciliationState(binding.reconciliationId))
                if (calls == 1) error("ambiguous response")
                RemoteLibraryAddResult(selected.identity, RemoteLibraryAddOutcome.ALREADY_PRESENT)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(selected)

            assertEquals(RemoteAddUiResult.Unresolved, controller.addSelectedBookToWebsite(SOURCE_FLOW_TEST_TIME))
            assertEquals(RemoteReconciliationState.UNRESOLVED, controller.remoteLibrary.selectedBookReconciliation)
            File(context.noBackupFilesDir, "source-credentials").deleteRecursively()
            assertEquals(
                RemoteAddUiResult.Failure("remote-add-not-authorized"),
                controller.remoteLibrary.retryLocalBook(
                    requireNotNull(library.book(selected.identity)),
                    SOURCE_FLOW_TEST_TIME.plusMillis(500),
                ),
            )
            assertFalse(requireNotNull(library.sourceRemotePolicy(sourceId)).addWritebackEnabled)
            assertEquals(1, calls)
            putCredential(sourceId)
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            val retryResult = controller.remoteLibrary.retryLocalBook(
                requireNotNull(library.book(selected.identity)),
                SOURCE_FLOW_TEST_TIME.plusSeconds(1),
            )
            assertEquals(
                RemoteAddUiResult.Failure("remote-add-not-retryable"),
                controller.remoteLibrary.retryLocalBook(
                    requireNotNull(library.book(selected.identity)),
                    SOURCE_FLOW_TEST_TIME.plusSeconds(2),
                ),
            )
            assertEquals(RemoteReconciliationState.CONFIRMED.name, reconciliationState(acceptedReconciliationIds.last()))
            assertEquals(RemoteAddUiResult.Confirmed, retryResult)

            assertEquals(2, calls)
            assertEquals(2, acceptedReconciliationIds.distinct().size)
            assertEquals(RemoteReconciliationState.CONFIRMED, controller.remoteLibrary.selectedBookReconciliation)
            assertEquals(
                RemoteReconciliationState.CONFIRMED,
                library.libraryEntries().single { it.book.identity == selected.identity }.reconciliation,
            )
        } finally {
            controller.close()
        }
    }
}
