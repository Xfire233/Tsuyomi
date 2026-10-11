/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.lifecycle.SavedStateHandle
import java.io.File
import org.tsuyomi.feature.book.SourceBookState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
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
import org.tsuyomi.shared.librarydomain.RemoteReconciliationState
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddOutcome
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage
import org.tsuyomi.shared.sourcecontract.RemoteLibraryMoveOutcome
import org.tsuyomi.shared.sourcecontract.RemoteLibraryMoveResult
import org.tsuyomi.shared.sourcecontract.SourceBookDetail

@RunWith(AndroidJUnit4::class)
internal class SourceRemoteLibraryAddInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun cancellationDuringPostBeginAddAvailabilityLookupCancelsAndUnblocksExplicitAdd() = runBlocking {
        assertPostBeginAddCancellation("source_availability")
    }

    @Test
    fun cancellationDuringPostBeginAddPolicyLookupCancelsAndUnblocksExplicitAdd() = runBlocking {
        assertPostBeginAddCancellation("source_remote_policy")
    }

    @Test
    fun cancellationDuringPostBeginAddSelectionLookupCancelsAndUnblocksExplicitAdd() = runBlocking {
        assertPostBeginAddCancellation("books", selectBook = true)
    }

    private suspend fun assertPostBeginAddCancellation(table: String, selectBook: Boolean = false) =
        kotlinx.coroutines.coroutineScope {
            val packageInfo = installFixture()
            val sourceId = packageInfo.manifest.sourceId.value
            val book = summary(sourceId, "post-begin-add", "取消后显式加入")
            putCredential(sourceId)
            val calls = AtomicInteger()
            val flow = controller {
                FakeSession(addRemote = { remoteBookId, token ->
                    calls.incrementAndGet()
                    directActionTokens.accept(sourceId, remoteBookId, token)
                    RemoteLibraryAddResult(book.identity, RemoteLibraryAddOutcome.APPLIED)
                })
            }
            try {
                val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
                assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
                flow.open(packageInfo)
                if (selectBook) flow.selectBook(book)
                val add = async(start = CoroutineStart.LAZY) {
                    flow.remoteLibrary.addBookToWebsite(book, SOURCE_FLOW_TEST_TIME)
                }
                val cancelledAtLookup = cancelAtPostBeginLookup(table, add)
                add.start()
                withTimeout(5_000) { add.join() }

                assertTrue("The post-begin lookup must be reached", cancelledAtLookup.get())
                assertTrue(add.isCancelled)
                assertEquals(0, calls.get())
                val record = requireNotNull(library.bookReconciliation(sourceId, book.identity.remoteBookId))
                assertEquals(RemoteReconciliationState.CANCELLED, record.state)
                assertEquals("CANCELLED", reconciliationState(record.id))
                assertTrue(library.unresolvedReconciliationsForSource(sourceId).isEmpty())
                assertEquals(
                    RemoteAddUiResult.Confirmed,
                    flow.remoteLibrary.addBookToWebsite(book, SOURCE_FLOW_TEST_TIME.plusSeconds(1)),
                )
                assertEquals(1, calls.get())
                assertEquals(
                    RemoteReconciliationState.CONFIRMED,
                    library.bookReconciliation(sourceId, book.identity.remoteBookId)?.state,
                )
            } finally {
                flow.close()
            }
        }

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
    fun replacementSessionCannotAcceptPendingAddToken() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val replacementPackage = packageInfo.withPackageSha256(alternateSha(packageInfo.packageSha256))
        val selected = summary(sourceId, "3009", "来源切换时加入")
        putCredential(sourceId)
        var websiteWrites = 0
        var replacementCalls = 0
        lateinit var flow: SourceFlowController
        val firstSession = FakeSession(
            addRemote = { remoteBookId, token ->
                flow.open(replacementPackage)
                directActionTokens.accept(sourceId, remoteBookId, token)
                websiteWrites++
                RemoteLibraryAddResult(selected.identity, RemoteLibraryAddOutcome.APPLIED)
            },
        )
        val replacementSession = FakeSession(
            addRemote = { _, _ ->
                replacementCalls++
                error("A pending mutation must not be forwarded to the replacement client")
            },
        )
        flow = controller { candidate ->
            if (candidate.packageSha256 == packageInfo.packageSha256) firstSession else replacementSession
        }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            flow.open(packageInfo)
            flow.selectBook(selected)

            assertEquals(RemoteAddUiResult.Cancelled, flow.addSelectedBookToWebsite(SOURCE_FLOW_TEST_TIME))
            assertEquals(0, websiteWrites)
            assertEquals(0, replacementCalls)
            assertEquals(
                RemoteReconciliationState.CANCELLED,
                library.libraryEntries().single { it.book.identity == selected.identity }.reconciliation,
            )
        } finally {
            flow.close()
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
            library.setSourceAvailability(sourceId, availability.verifiedVersion, true, availability.generation + 2)
            assertEquals(
                RemoteAddUiResult.Confirmed,
                controller.addSelectedBookToWebsite(SOURCE_FLOW_TEST_TIME.plusSeconds(1)),
            )
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

            assertEquals(RemoteAddUiResult.LocalOnly, controller.addSelectedBook((controller.detailState as? SourceBookState.Content)?.value, SOURCE_FLOW_TEST_TIME))
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
        var reads = 0
        val acceptedReconciliationIds = mutableListOf<String>()
        val session = FakeSession(
            detail = { SourceBookDetail(it, "fixture", emptyList(), "连载中") },
            listRemote = {
                reads++
                RemoteLibraryPage(listOf(selected), null, true)
            },
            addRemote = { remoteBookId, token ->
                calls += 1
                if (calls == 2) error("preaccept retry failure")
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
            val detail = SourceDetailRouteOwner(controller, SavedStateHandle()) {}
            assertTrue(detail.resyncRemoteLibrary(packageInfo))
            assertEquals(1, reads)
            assertEquals(1, calls)
            assertEquals(RemoteReconciliationState.UNRESOLVED, controller.remoteLibrary.selectedBookReconciliation)
            assertEquals(RemoteReconciliationState.UNRESOLVED, library.libraryEntry(selected.identity)?.reconciliation)
            assertEquals(1, requireNotNull(controller.remoteMirrorSnapshot(sourceId)).books.size)
            assertEquals(1, reads)
            assertEquals(1, calls)
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
            assertEquals(
                RemoteAddUiResult.Unresolved,
                controller.remoteLibrary.retryLocalBook(
                    requireNotNull(library.book(selected.identity)),
                    SOURCE_FLOW_TEST_TIME.plusSeconds(1),
                ),
            )
            assertEquals(RemoteReconciliationState.UNRESOLVED, controller.remoteLibrary.selectedBookReconciliation)
            assertEquals(RemoteReconciliationState.UNRESOLVED, library.libraryEntry(selected.identity)?.reconciliation)
            val retryResult = withTimeout(2_000) {
                controller.retryRemoteMutation(selected, SOURCE_FLOW_TEST_TIME.plusSeconds(2))
            }
            assertEquals(
                RemoteAddUiResult.Failure("remote-add-not-retryable"),
                controller.remoteLibrary.retryLocalBook(
                    requireNotNull(library.book(selected.identity)),
                    SOURCE_FLOW_TEST_TIME.plusSeconds(3),
                ),
            )
            assertEquals(RemoteReconciliationState.CONFIRMED.name, reconciliationState(acceptedReconciliationIds.last()))
            assertTrue(library.unresolvedReconciliationsForSource(sourceId).isEmpty())
            assertEquals(RemoteMutationUiResult.Confirmed, retryResult)

            assertEquals(3, calls)
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

    @Test
    fun targetedAddKeepsConfirmedAddAndRetriesOnlyMove() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val selected = summary(sourceId, "7002", "分步移动")
        putCredential(sourceId)
        var addCalls = 0
        var moveCalls = 0
        val session = FakeSession(
            addRemote = { remoteBookId, token ->
                addCalls += 1
                directActionTokens.accept(sourceId, remoteBookId, token)
                RemoteLibraryAddResult(selected.identity, RemoteLibraryAddOutcome.APPLIED)
            },
            moveRemote = { remoteBookId, targetId, token ->
                moveCalls += 1
                directActionTokens.accept(sourceId, remoteBookId, token)
                if (moveCalls == 1) error("ambiguous move response")
                RemoteLibraryMoveResult(selected.identity, targetId, RemoteLibraryMoveOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            assertTrue(library.setMoveWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)

            controller.selectBook(selected)
            val initial = controller.addBookToWebsiteTarget(
                selected,
                "favorites",
                "特别收藏",
                "default",
            )
            assertTrue(initial is RemoteTargetedAddResult.Partial)
            assertEquals(1, addCalls)
            assertEquals(1, moveCalls)
            assertEquals(
                "default",
                requireNotNull(library.remoteMirrorSnapshot(sourceId)).books.single().targetId,
            )
            assertEquals(RemoteReconciliationState.UNRESOLVED, controller.remoteLibrary.selectedBookReconciliation)
            assertEquals("move", controller.remoteLibrary.selectedBookReconciliationOperation)

            assertEquals(
                RemoteMutationUiResult.Confirmed,
                controller.retryRemoteMutation(selected, SOURCE_FLOW_TEST_TIME.plusSeconds(1)),
            )
            assertEquals(1, addCalls)
            assertEquals(2, moveCalls)
            assertEquals(
                "favorites",
                requireNotNull(library.remoteMirrorSnapshot(sourceId)).books.single().targetId,
            )
        } finally {
            controller.close()
        }
    }
    @Test
    fun targetedAddResumesMoveAfterCoordinatorRecreationWithoutRepeatingAdd() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val selected = summary(sourceId, "7003", "持久目标移动")
        putCredential(sourceId)
        var addCalls = 0
        var moveCalls = 0
        val session = FakeSession(
            addRemote = { remoteBookId, token ->
                addCalls += 1
                directActionTokens.accept(sourceId, remoteBookId, token)
                RemoteLibraryAddResult(selected.identity, RemoteLibraryAddOutcome.APPLIED)
            },
            moveRemote = { remoteBookId, targetId, token ->
                moveCalls += 1
                directActionTokens.accept(sourceId, remoteBookId, token)
                RemoteLibraryMoveResult(selected.identity, targetId, RemoteLibraryMoveOutcome.APPLIED)
            },
        )
        val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
        assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))

        controller { session }.use { firstController ->
            firstController.open(packageInfo)
            assertTrue(
                firstController.addBookToWebsiteTarget(
                    selected,
                    "favorites",
                    "特别收藏",
                    "default",
                ) is RemoteTargetedAddResult.Partial,
            )
        }

        val pendingMove = requireNotNull(library.bookReconciliation(sourceId, selected.identity.remoteBookId))
        assertEquals("ADD", pendingMove.operation)
        assertEquals(RemoteReconciliationState.CONFIRMED, pendingMove.state)
        assertEquals("favorites", pendingMove.targetId)
        assertEquals(1, addCalls)
        assertEquals(0, moveCalls)

        assertTrue(library.setMoveWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
        controller { session }.use { restoredController ->
            restoredController.open(packageInfo)
            assertEquals(RemoteMutationUiResult.Confirmed, restoredController.retryRemoteMutation(selected))
        }
        assertEquals(1, addCalls)
        assertEquals(1, moveCalls)
        assertEquals("favorites", requireNotNull(library.remoteMirrorSnapshot(sourceId)).books.single().targetId)
    }

}
