/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity

internal class RoomLibraryRepositoryInstrumentedTestFixture {
    val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        TsuyomiDatabase::class.java,
    ).allowMainThreadQueries().build()
    val repository = RoomLibraryRepository(database)

    fun close() = database.close()
}

internal fun progress(
    identity: BookIdentity,
    contentId: String,
    offset: Int,
    bookProgress: Double,
    at: Instant,
): ReadingProgress = ReadingProgress(
    identity = identity,
    locator = ReaderLocator(
        document = DocumentIdentity(identity.sourceId, identity.remoteBookId, contentId),
        blockId = "block-1",
        characterOffset = offset,
        bookProgress = bookProgress,
        capturedAt = at,
    ),
)

internal suspend fun <T> concurrently(vararg actions: suspend () -> T): List<T> = coroutineScope {
    val start = CompletableDeferred<Unit>()
    val jobs = actions.map { action ->
        async(Dispatchers.IO) {
            start.await()
            action()
        }
    }
    start.complete(Unit)
    jobs.awaitAll()
}
