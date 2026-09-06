/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.shared.sourcecontract.RemoteLibraryMoveOutcome
import org.tsuyomi.shared.sourcecontract.RemoteLibraryMoveResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryRemoveOutcome
import org.tsuyomi.shared.sourcecontract.RemoteLibraryRemoveResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryTargetsResult
import org.tsuyomi.shared.sourcecontract.RemoteTarget

@RunWith(AndroidJUnit4::class)
internal class SourceRemoteLibraryMutationInstrumentedTest : SourceFlowInstrumentedTestFixture() {

    @Test
    fun removeBookFromWebsiteConfirmsReconciliationAndRetainsLocalData() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val book = summary(sourceId, "4001", "删除测试")
        putCredential(sourceId)
        val session = FakeSession(
            removeRemote = { remoteBookId, token ->
                directActionTokens.accept(sourceId, remoteBookId, token)
                RemoteLibraryRemoveResult(book.identity, RemoteLibraryRemoveOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setRemoveWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(book)
            // Add book to local library first
            controller.addSelectedBook(SOURCE_FLOW_TEST_TIME)
            assertTrue(library.libraryEntries().any { it.book.identity == book.identity })

            // Remove from remote website
            val result = controller.removeSelectedBookFromWebsite(SOURCE_FLOW_TEST_TIME)
            assertEquals(RemoteMutationUiResult.Confirmed, result)

            // Verify local entry still exists (D15: remote remove never deletes local data)
            val entry = requireNotNull(library.libraryEntries().firstOrNull { it.book.identity == book.identity })
            assertEquals(RemoteReconciliationState.CONFIRMED, entry.reconciliation)
            val record = requireNotNull(library.bookReconciliation(sourceId, book.identity.remoteBookId))
            assertEquals("remove", record.operation)
            assertEquals(RemoteReconciliationState.CONFIRMED, record.state)
        } finally {
            controller.close()
        }
    }

    @Test
    fun moveBookOnWebsiteConfirmsReconciliationWithTargetBinding() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val book = summary(sourceId, "4002", "移动测试")
        putCredential(sourceId)
        val session = FakeSession(
            moveRemote = { remoteBookId, targetId, token ->
                directActionTokens.accept(sourceId, remoteBookId, token)
                assertEquals("favorites", targetId)
                RemoteLibraryMoveResult(book.identity, targetId, RemoteLibraryMoveOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setMoveWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(book)
            controller.addSelectedBook(SOURCE_FLOW_TEST_TIME)

            val result = controller.moveSelectedBookOnWebsite("favorites", "特别收藏", SOURCE_FLOW_TEST_TIME)
            assertEquals(RemoteMutationUiResult.Confirmed, result)

            val record = requireNotNull(library.bookReconciliation(sourceId, book.identity.remoteBookId))
            assertEquals("move", record.operation)
            assertEquals("favorites", record.targetId)
            assertEquals("特别收藏", record.targetName)
            assertEquals(RemoteReconciliationState.CONFIRMED, record.state)
        } finally {
            controller.close()
        }
    }

    @Test
    fun jitAuthorizationEnablesWritebackOnDemand() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val book = summary(sourceId, "4003", "JIT授权测试")
        putCredential(sourceId)
        val session = FakeSession(
            removeRemote = { remoteBookId, token ->
                directActionTokens.accept(sourceId, remoteBookId, token)
                RemoteLibraryRemoveResult(book.identity, RemoteLibraryRemoveOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            controller.open(packageInfo)
            controller.selectBook(book)

            // Initially removeWritebackEnabled is false -> mutation fails with not-authorized
            val unauthorizedResult = controller.removeSelectedBookFromWebsite(SOURCE_FLOW_TEST_TIME)
            assertEquals(RemoteMutationUiResult.Failure("remote-remove-not-authorized"), unauthorizedResult)

            // JIT grant: user authorizes remove writeback
            assertTrue(controller.authorizeWriteback(sourceId, "remove", true))

            // Mutation now succeeds
            val authorizedResult = controller.removeSelectedBookFromWebsite(SOURCE_FLOW_TEST_TIME)
            assertEquals(RemoteMutationUiResult.Confirmed, authorizedResult)
        } finally {
            controller.close()
        }
    }

    @Test
    fun unresolvedStateBlocksMutationsAndAcknowledgeUnlocks() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val book = summary(sourceId, "4004", "锁定测试")
        putCredential(sourceId)
        val accepted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val session = FakeSession(
            removeRemote = { remoteBookId, token ->
                directActionTokens.accept(sourceId, remoteBookId, token)
                accepted.complete(Unit)
                releaseResponse.await()
                RemoteLibraryRemoveResult(book.identity, RemoteLibraryRemoveOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setRemoveWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            assertTrue(library.setMoveWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(book)
            controller.addSelectedBook(SOURCE_FLOW_TEST_TIME)

            // Trigger remote remove and force lease change to produce UNRESOLVED state
            val removeJob = async { controller.removeSelectedBookFromWebsite(SOURCE_FLOW_TEST_TIME) }
            withTimeout(5_000) { accepted.await() }
            val availability = requireNotNull(library.sourceAvailability(sourceId))
            library.setSourceAvailability(sourceId, availability.verifiedVersion, false, availability.generation + 1)
            releaseResponse.complete(Unit)

            assertEquals(RemoteMutationUiResult.Unresolved, removeJob.await())
            assertEquals(RemoteReconciliationState.UNRESOLVED, controller.remoteLibrary.selectedBookReconciliation)

            // Restore source availability for subsequent operations
            library.setSourceAvailability(sourceId, availability.verifiedVersion, true, availability.generation + 2)
            controller.open(packageInfo)
            controller.selectBook(book)

            // Attempting another mutation while UNRESOLVED must be BLOCKED (D17)
            val blockedResult = controller.moveSelectedBookOnWebsite("favorites", "特别收藏", SOURCE_FLOW_TEST_TIME)
            assertEquals(RemoteMutationUiResult.Failure("remote-mutation-blocked-unresolved"), blockedResult)

            // Acknowledge unresolved -> unlocks the book
            assertTrue(controller.acknowledgeUnresolved(book.identity))
            assertEquals(RemoteReconciliationState.CANCELLED, controller.remoteLibrary.selectedBookReconciliation)

            // Now mutations are unblocked
            val unblockedSession = FakeSession(
                moveRemote = { remoteBookId, targetId, token ->
                    directActionTokens.accept(sourceId, remoteBookId, token)
                    RemoteLibraryMoveResult(book.identity, targetId, RemoteLibraryMoveOutcome.APPLIED)
                },
            )
            val secondController = controller { unblockedSession }
            try {
                secondController.open(packageInfo)
                secondController.selectBook(book)
                val movedResult = secondController.moveSelectedBookOnWebsite("favorites", "特别收藏", SOURCE_FLOW_TEST_TIME)
                assertEquals(RemoteMutationUiResult.Confirmed, movedResult)
            } finally {
                secondController.close()
            }
        } finally {
            releaseResponse.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun addAndMoveAuthorizationReceiptsRemainOperationSpecificAcrossControllers() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val first = controller { FakeSession() }
        try {
            first.open(packageInfo)
            assertFalse(first.writebackAuthorized(sourceId, "add"))
            assertFalse(first.writebackAuthorized(sourceId, "move"))
            assertTrue(first.authorizeWriteback(sourceId, "add", true))
            assertTrue(first.writebackAuthorized(sourceId, "add"))
            assertFalse(first.writebackAuthorized(sourceId, "move"))
            assertTrue(first.authorizeWriteback(sourceId, "move", true))
        } finally {
            first.close()
        }

        val restored = controller { FakeSession() }
        try {
            restored.open(packageInfo)
            assertTrue(restored.writebackAuthorized(sourceId, "add"))
            assertTrue(restored.writebackAuthorized(sourceId, "move"))
            assertTrue(restored.authorizeWriteback(sourceId, "add", false))
            assertFalse(restored.writebackAuthorized(sourceId, "add"))
            assertTrue(restored.writebackAuthorized(sourceId, "move"))
        } finally {
            restored.close()
        }
    }

    @Test
    fun listRemoteTargetsReturnsTypedHierarchy() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val targetsList = listOf(
            RemoteTarget("default", "默认书架", null, "folder"),
            RemoteTarget("favorites", "特别收藏", null, "folder"),
            RemoteTarget("finished", "已读完", null, "folder"),
        )
        val session = FakeSession(
            targetsResult = { RemoteLibraryTargetsResult(sourceId, targetsList) },
        )
        val controller = controller { session }
        try {
            controller.open(packageInfo)
            val targets = controller.listRemoteTargets()
            assertEquals(3, targets.size)
            assertEquals("default", targets[0].targetId)
            assertEquals("特别收藏", targets[1].displayName)
        } finally {
            controller.close()
        }
    }
}
