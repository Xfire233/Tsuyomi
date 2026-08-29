/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

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
}
