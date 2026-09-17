/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.preferences.LibraryPreferencesRepository
import org.tsuyomi.feature.library.SystemLibraryFilter
import org.tsuyomi.feature.library.projectedEntries
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.ReadingProgress
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity

/**
 * `reload` samples `state.filter` when it starts and publishes `entries` for that sample when it
 * finishes, while `selectTab` publishes outside `reloadMutex`. A tab selected while a reload is in
 * flight can therefore be left holding entries computed for the previously selected tab.
 *
 * The Continue tab projects through `CONTINUE -> entry.readerVisitedAt != null`, and root entries
 * carry no reader visit, so such a mix renders an empty Continue list.
 */
@RunWith(AndroidJUnit4::class)
class LibraryTabReloadRaceInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun books_that_other_tests_removed_from_the_library_still_occupy_the_continue_tab() = runBlocking {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val target = BookIdentity("fixture.tab.race", "target")
        val leftovers = (0 until 40).map { BookIdentity("fixture.tab.race", "leftover-$it") }
        try {
            (listOf(target) + leftovers).forEach { identity ->
                val entry = LibraryBook(
                    identity = identity,
                    title = "残留 $identity",
                    addedAt = Instant.EPOCH,
                    metadataUpdatedAt = Instant.EPOCH,
                )
                repository.saveBook(entry)
                assertTrue(repository.addToLibrary(entry))
                repository.saveProgress(
                    ReadingProgress(
                        identity = identity,
                        locator = ReaderLocator(
                            document = DocumentIdentity(identity.sourceId, identity.remoteBookId, "chapter-1"),
                            blockId = "block-1",
                            characterOffset = 10,
                            bookProgress = 0.25,
                            capturedAt = Instant.EPOCH.plusSeconds(600L + identityCount(identity)),
                        ),
                    ),
                )
            }
            // Mirrors what every other suite leaves behind: the membership is gone, the progress is not.
            assertEquals(leftovers.size, repository.removeFromLibrary(leftovers.toSet()))
            assertEquals(
                "removing a book from the library left its reading progress behind",
                leftovers.size.toLong(),
                repository.readerHistoryEntries().count { it.book.identity in leftovers.toSet() }.toLong(),
            )
        } finally {
            repository.removeFromLibrary(leftovers.toSet() + target)
        }
    }

    private fun identityCount(identity: BookIdentity): Long =
        identity.remoteBookId.substringAfterLast('-').toLongOrNull() ?: 0L

    /** The pager pre-composes neighbouring pages, so the surface tag alone is ambiguous. */
    private fun continueSurface() =
        hasTestTag("library-book-surface") and hasAnyAncestor(hasTestTag("library-primary-page-CONTINUE"))

    @Test
    fun the_continue_tab_renders_the_newest_reader_activity_while_older_progress_remains() {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val target = BookIdentity("fixture.tab.race", "target")
        val leftovers = (0 until 40).map { BookIdentity("fixture.tab.race", "leftover-$it") }
        try {
            runBlocking {
                (listOf(target) + leftovers).forEach { identity ->
                    val entry = LibraryBook(
                        identity = identity,
                        title = "样书 ${identity.remoteBookId}",
                        addedAt = Instant.EPOCH,
                        metadataUpdatedAt = Instant.EPOCH,
                    )
                    repository.saveBook(entry)
                    assertTrue(repository.addToLibrary(entry))
                    repository.saveProgress(
                        ReadingProgress(
                            identity = identity,
                            locator = ReaderLocator(
                                document = DocumentIdentity(identity.sourceId, identity.remoteBookId, "chapter-1"),
                                blockId = "block-1",
                                characterOffset = 10,
                                bookProgress = 0.25,
                                capturedAt = Instant.EPOCH.plusSeconds(600L + identityCount(identity)),
                            ),
                        ),
                    )
                }
                assertEquals(leftovers.size, repository.removeFromLibrary(leftovers.toSet()))
                // The Reader admission the journey produces, which outranks every leftover timestamp.
                repository.recordReaderVisit(target, Instant.EPOCH.plusSeconds(60))
            }

            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithTag("tsuyomi-tab-CONTINUE").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("tsuyomi-tab-CONTINUE").performClick()

            val projection = runBlocking {
                val scopeJob = SupervisorJob()
                val preferenceFile = File(composeRule.activity.cacheDir, "tab-projection-${UUID.randomUUID()}.preferences_pb")
                val store = PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(Dispatchers.IO + scopeJob),
                    produceFile = { preferenceFile },
                )
                try {
                    val controller = LibraryFlowController(repository, LibraryPreferencesRepository(store), "FIXTURE_TAB_RACE")
                    controller.reload("failed")
                    controller.selectTab(SystemLibraryFilter.CONTINUE)
                    controller.state.projectedEntries().map { it.book.title }
                } finally {
                    scopeJob.cancel()
                    preferenceFile.delete()
                }
            }

            val cardTitle = "样书 ${target.remoteBookId}"
            assertTrue(
                "the Continue projection lost the recorded book: ${projection.size} entries",
                projection.contains(cardTitle),
            )
            // The corrected premise: the row is reachable, not already inside the first window. The
            // Continue tab legitimately lists records whose shelf entry was removed, so a long list is
            // expected and the surface has to be driven to the row.
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodes(continueSurface()).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(continueSurface()).performScrollToNode(hasText(cardTitle))
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText(cardTitle, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            runBlocking { repository.removeFromLibrary(leftovers.toSet() + target) }
        }
    }

    @Test
    fun a_tab_selected_during_a_reload_keeps_entries_that_belong_to_that_tab() = runBlocking {
        val application = composeRule.activity.application as TsuyomiApplication
        val repository = application.libraryRepository
        val scopeJob = SupervisorJob()
        val preferenceFile = File(composeRule.activity.cacheDir, "tab-race-${UUID.randomUUID()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + scopeJob),
            produceFile = { preferenceFile },
        )
        val visited = BookIdentity("fixture.tab.race", "visited-book")
        val filler = (0 until 60).map { BookIdentity("fixture.tab.race", "filler-$it") }
        try {
            listOf(visited) .plus(filler).forEach { identity ->
                val entry = LibraryBook(
                    identity = identity,
                    title = identity.remoteBookId,
                    addedAt = Instant.EPOCH,
                    metadataUpdatedAt = Instant.EPOCH,
                )
                repository.saveBook(entry)
                assertTrue(repository.addToLibrary(entry))
            }
            val capturedAt = Instant.EPOCH.plusSeconds(60)
            repository.saveProgress(
                ReadingProgress(
                    identity = visited,
                    locator = ReaderLocator(
                        document = DocumentIdentity(visited.sourceId, visited.remoteBookId, "chapter-1"),
                        blockId = "block-1",
                        characterOffset = 10,
                        bookProgress = 0.25,
                        capturedAt = capturedAt,
                    ),
                ),
            )

            val controller = LibraryFlowController(
                repository,
                LibraryPreferencesRepository(store),
                "FIXTURE_TAB_RACE",
            )
            controller.reload("failed")
            assertEquals(SystemLibraryFilter.ALL, controller.state.filter)

            repeat(25) { attempt ->
                assertEquals(SystemLibraryFilter.ALL, controller.state.filter)
                val reloading = launch { controller.reload("failed") }
                delay(attempt.toLong())
                controller.selectTab(SystemLibraryFilter.CONTINUE)
                reloading.join()
                assertEquals(SystemLibraryFilter.CONTINUE, controller.state.filter)
                assertTrue(
                    "attempt $attempt selected CONTINUE but kept entries that belong to another tab",
                    controller.state.entries.all { it.readerVisitedAt != null },
                )
                controller.selectTab(SystemLibraryFilter.ALL)
            }
        } finally {
            repository.removeFromLibrary(filler.toSet() + visited).let { }
            scopeJob.cancel()
            preferenceFile.delete()
        }
    }
}
