/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import org.tsuyomi.shared.librarydomain.SourceRemotePolicy
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.RemoteReconciliationState
import org.tsuyomi.shared.librarydomain.RemoteMirrorTargetSnapshot
import org.tsuyomi.shared.librarydomain.RemoteMirrorBookSnapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.shared.model.BookIdentity

@RunWith(AndroidJUnit4::class)
class RoomRemoteLibraryInstrumentedTest {
    private val fixture = RoomLibraryRepositoryInstrumentedTestFixture()
    private val repository get() = fixture.repository

    @After
    fun closeDatabase() = fixture.close()
    @Test
    fun remoteLibraryMergeRejectsStaleOrUnavailableLeaseWithoutPartialRows() = runBlocking {
        val sourceId = "org.tsuyomi.wenku8"
        val identity = BookIdentity(sourceId, "remote-42")
        repository.setSourceAvailability(sourceId, "0.2.0", true, 4)
        repository.saveSourceRemotePolicy(SourceRemotePolicy(sourceId, "publisher", "capability", "https://www.wenku8.net", false, false))
        val book = LibraryBook(identity, "远程收藏", Instant.EPOCH, Instant.EPOCH)
        fun request(
            version: String,
            capability: String,
            generation: Long,
            books: List<LibraryBook> = listOf(book),
        ) = RemoteLibraryMergeRequest(sourceId, books, version, capability, generation, Instant.EPOCH)

        val staleVersion = runCatching { repository.mergeRemoteLibrary(request("0.1.0", "capability", 4)) }
        val staleCapability = runCatching { repository.mergeRemoteLibrary(request("0.2.0", "other", 4)) }
        val staleGeneration = runCatching { repository.mergeRemoteLibrary(request("0.2.0", "capability", 3)) }
        repository.setSourceAvailability(sourceId, "0.2.0", false, 4)
        val unavailableSource = runCatching { repository.mergeRemoteLibrary(request("0.2.0", "capability", 4)) }
        val unavailableEmptySource = runCatching { repository.mergeRemoteLibrary(request("0.2.0", "capability", 4, emptyList())) }

        assertTrue(staleVersion.isFailure)
        assertTrue(staleCapability.isFailure)
        assertTrue(staleGeneration.isFailure)
        assertTrue(unavailableSource.isFailure)
        assertTrue(unavailableEmptySource.isFailure)
        assertTrue(repository.libraryEntries().none { it.book.identity == identity })
        repository.setSourceAvailability(sourceId, "0.2.0", true, 4)
        assertEquals(1, repository.mergeRemoteLibrary(request("0.2.0", "capability", 4)))
        assertEquals(0, repository.mergeRemoteLibrary(request("0.2.0", "capability", 4, emptyList())))
    }

    @Test
    fun remoteAddTransitionNeverTreatsPostAcceptanceFailureAsSafeCancellation() = runBlocking {
        val identity = BookIdentity("org.tsuyomi.wenku8", "add-42")
        val id = repository.beginRemoteAdd(
            RemoteAddRequest(
                book = LibraryBook(identity, "待同步", Instant.EPOCH, Instant.EPOCH),
                packageDigest = "digest",
                packageVersion = "0.2.0",
                capabilitySetFingerprint = "capability",
                registryGeneration = 2,
                startedAt = Instant.EPOCH,
            ),
        )

        assertTrue(repository.transitionRemoteAdd(id, RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.IN_FLIGHT, Instant.EPOCH.plusSeconds(1)))
        assertTrue(repository.transitionRemoteAdd(id, RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.UNRESOLVED, Instant.EPOCH.plusSeconds(2)))
        val illegalCancellation = runCatching {
            repository.transitionRemoteAdd(id, RemoteReconciliationState.UNRESOLVED, RemoteReconciliationState.CANCELLED, Instant.EPOCH.plusSeconds(3))
        }
        assertTrue(illegalCancellation.isFailure)
    }

    @Test
    fun confirmedRemoteAddRetryClosesEveryPriorUnresolvedAttempt() = runBlocking {
        val identity = BookIdentity("org.tsuyomi.wenku8", "retry-add-42")
        fun request(startedAt: Instant) = RemoteAddRequest(
            book = LibraryBook(identity, "重试同步", Instant.EPOCH, Instant.EPOCH),
            packageDigest = "digest",
            packageVersion = "0.2.0",
            capabilitySetFingerprint = "capability",
            registryGeneration = 2,
            startedAt = startedAt,
        )
        val first = repository.beginRemoteAdd(request(Instant.EPOCH))
        assertTrue(repository.transitionRemoteAdd(first, RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.IN_FLIGHT, Instant.EPOCH.plusSeconds(1)))
        assertTrue(repository.transitionRemoteAdd(first, RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.UNRESOLVED, Instant.EPOCH.plusSeconds(2)))

        val cancelledRetry = repository.beginRemoteAdd(request(Instant.EPOCH.plusSeconds(3)), first)
        assertTrue(repository.transitionRemoteAdd(cancelledRetry, RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.CANCELLED, Instant.EPOCH.plusSeconds(4)))
        assertEquals(RemoteReconciliationState.UNRESOLVED, repository.bookReconciliation(identity.sourceId, identity.remoteBookId)?.state)

        val second = repository.beginRemoteAdd(request(Instant.EPOCH.plusSeconds(5)), first)
        assertTrue(first != second)
        assertTrue(repository.transitionRemoteAdd(second, RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.IN_FLIGHT, Instant.EPOCH.plusSeconds(6)))
        assertTrue(repository.confirmRemoteAdd(second, identity, resolvesPriorUnresolved = true, Instant.EPOCH.plusSeconds(7)))

        assertTrue(repository.unresolvedReconciliationsForSource(identity.sourceId).isEmpty())
        assertEquals(RemoteReconciliationState.CONFIRMED, repository.bookReconciliation(identity.sourceId, identity.remoteBookId)?.state)
        assertEquals(RemoteReconciliationState.CONFIRMED, repository.libraryEntry(identity)?.reconciliation)
    }

    @Test
    fun genericMutationRetryPreservesPriorUnresolvedUntilConfirmation() = runBlocking {
        val identity = BookIdentity("org.tsuyomi.wenku8", "retry-move-42")
        fun request(startedAt: Instant) = RemoteMutationRequest(
            book = LibraryBook(identity, "重试移动", Instant.EPOCH, Instant.EPOCH),
            operation = "move",
            targetId = "favorites",
            targetName = "特别收藏",
            packageDigest = "digest",
            packageVersion = "0.2.0",
            capabilitySetFingerprint = "capability",
            registryGeneration = 2,
            startedAt = startedAt,
        )
        val first = repository.beginRemoteMutation(request(Instant.EPOCH))
        assertTrue(repository.transitionRemoteMutation(first, RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.IN_FLIGHT, Instant.EPOCH.plusSeconds(1)))
        assertTrue(repository.transitionRemoteMutation(first, RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.UNRESOLVED, Instant.EPOCH.plusSeconds(2)))

        val cancelledRetry = repository.beginRemoteMutation(request(Instant.EPOCH.plusSeconds(3)), first)
        assertTrue(repository.transitionRemoteMutation(cancelledRetry, RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.CANCELLED, Instant.EPOCH.plusSeconds(4)))
        assertEquals(RemoteReconciliationState.UNRESOLVED, repository.bookReconciliation(identity.sourceId, identity.remoteBookId)?.state)

        val confirmedRetry = repository.beginRemoteMutation(request(Instant.EPOCH.plusSeconds(5)), first)
        assertTrue(repository.transitionRemoteMutation(confirmedRetry, RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.IN_FLIGHT, Instant.EPOCH.plusSeconds(6)))
        assertTrue(repository.confirmRemoteMutation(confirmedRetry, identity, "move", resolvesPriorUnresolved = true, Instant.EPOCH.plusSeconds(7)))

        assertTrue(repository.unresolvedReconciliationsForSource(identity.sourceId).isEmpty())
        assertEquals(RemoteReconciliationState.CONFIRMED, repository.bookReconciliation(identity.sourceId, identity.remoteBookId)?.state)
    }

    @Test
    fun acknowledgementCancelsEveryUnresolvedRetryOfOneOperation() = runBlocking {
        val identity = BookIdentity("org.tsuyomi.wenku8", "ack-move-42")
        fun request(startedAt: Instant) = RemoteMutationRequest(
            book = LibraryBook(identity, "解除移动锁", Instant.EPOCH, Instant.EPOCH),
            operation = "move",
            targetId = "favorites",
            targetName = "特别收藏",
            packageDigest = "digest",
            packageVersion = "0.2.0",
            capabilitySetFingerprint = "capability",
            registryGeneration = 2,
            startedAt = startedAt,
        )
        val first = repository.beginRemoteMutation(request(Instant.EPOCH))
        assertTrue(repository.transitionRemoteMutation(first, RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.IN_FLIGHT, Instant.EPOCH.plusSeconds(1)))
        assertTrue(repository.transitionRemoteMutation(first, RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.UNRESOLVED, Instant.EPOCH.plusSeconds(2)))
        val second = repository.beginRemoteMutation(request(Instant.EPOCH.plusSeconds(3)), first)
        assertTrue(repository.transitionRemoteMutation(second, RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.IN_FLIGHT, Instant.EPOCH.plusSeconds(4)))
        assertTrue(repository.transitionRemoteMutation(second, RemoteReconciliationState.IN_FLIGHT, RemoteReconciliationState.UNRESOLVED, Instant.EPOCH.plusSeconds(5)))

        assertTrue(repository.cancelUnresolvedMutations(identity, "move", Instant.EPOCH.plusSeconds(6)))

        assertTrue(repository.unresolvedReconciliationsForSource(identity.sourceId).isEmpty())
        assertEquals(RemoteReconciliationState.CANCELLED, repository.bookReconciliation(identity.sourceId, identity.remoteBookId)?.state)
    }

    @Test
    fun remoteMirrorSnapshotRestoresTargetsAndAppliesIncrementalWrites() = runBlocking {
        val sourceId = "org.tsuyomi.wenku8"
        val first = LibraryBook(BookIdentity(sourceId, "book-1"), "第一本", Instant.EPOCH, Instant.EPOCH)
        val second = LibraryBook(BookIdentity(sourceId, "book-2"), "第二本", Instant.EPOCH, Instant.EPOCH)
        repository.saveBook(
            first.copy(
                author = "作者甲",
                authors = setOf("作者甲", "作者乙"),
                status = "ongoing",
                remoteTags = setOf("奇幻"),
            ),
        )
        val initialTargets = listOf(
            RemoteMirrorTargetSnapshot(sourceId, "0", "默认书架", kind = "default", updatedAtEpochSecond = 10),
            RemoteMirrorTargetSnapshot(sourceId, "1", "收藏夹", updatedAtEpochSecond = 10),
        )
        repository.saveRemoteMirrorSnapshot(
            RemoteMirrorReplaceRequest(
                sourceId,
                "文库8",
                listOf(RemoteMirrorBookSnapshot(first, "1"), RemoteMirrorBookSnapshot(second, "0")),
                initialTargets,
                Instant.ofEpochSecond(10),
            ),
        )

        val restored = requireNotNull(repository.remoteMirrorSnapshot(sourceId))
        assertEquals(listOf("0", "1"), restored.targets.map { it.targetId }.sorted())
        assertEquals("1", restored.books.single { it.book.identity == first.identity }.targetId)
        val preserved = requireNotNull(repository.book(first.identity))
        assertEquals(setOf("作者甲", "作者乙"), preserved.authors)
        assertEquals("ongoing", preserved.status)
        assertEquals(setOf("奇幻"), preserved.remoteTags)

        assertTrue(repository.updateRemoteMirrorBookTarget(first.identity, "0", Instant.ofEpochSecond(20)))
        assertTrue(repository.removeRemoteMirrorBook(second.identity))
        repository.saveRemoteMirrorSnapshot(
            RemoteMirrorReplaceRequest(
                sourceId,
                "文库8",
                listOf(RemoteMirrorBookSnapshot(first, "0")),
                listOf(initialTargets.first().copy(updatedAtEpochSecond = 30)),
                Instant.ofEpochSecond(30),
            ),
        )

        val refreshed = requireNotNull(repository.remoteMirrorSnapshot(sourceId))
        assertEquals(1, refreshed.books.size)
        assertEquals("0", refreshed.books.single().targetId)
        assertTrue(refreshed.targets.single { it.targetId == "1" }.frozen)
        assertEquals(1, repository.remoteMirrorTargetCount(sourceId, "0"))
    }
}
