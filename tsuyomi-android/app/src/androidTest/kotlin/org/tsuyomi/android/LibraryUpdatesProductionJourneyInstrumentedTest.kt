/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.net.Uri
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.ReadingProgress
import org.tsuyomi.shared.librarydomain.SourceAvailability
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.preferences.LibraryPresentationPreferences
import org.tsuyomi.feature.browse.BrowseUiState
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.librarydomain.UpdateSessionStates
import org.tsuyomi.shared.librarydomain.UpdateSnapshot
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity

/**
 * Exercises signed update transport through the production Library root, exact unresolved batches,
 * ignore/Undo, mirror-only membership, exact completion reconciliation, and zero website writes.
 */
@RunWith(AndroidJUnit4::class)
class LibraryUpdatesProductionJourneyInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val application: TsuyomiApplication
        get() = composeRule.activity.application as TsuyomiApplication
    private val identity = BookIdentity(SOURCE_ID, REMOTE_BOOK_ID)
    private val updateRowTag = "library-book-${identity.sourceId}-${identity.remoteBookId}"
    private val updateActionsTag = "library-update-actions-${identity.sourceId}-${identity.remoteBookId}"
    private lateinit var fixtureBook: LibraryBook
    private lateinit var originalReaderPreferences: PortableReaderPreferences
    private lateinit var originalLibraryPreferences: LibraryPresentationPreferences
    private var preexistingSessionIds = emptySet<String>()
    private var sourceWasExcluded = false
    private var originalSourceAvailability: SourceAvailability? = null
    private lateinit var originalDisplayPreferences: DisplayPreferences
    private var mirrorBindingExisted = false
    private lateinit var fixtureArchive: File

    @Before
    fun setUpFixtureJourney() = runBlocking {
        check(application.packageName == "org.tsuyomi.android.fixture") { "This Journey requires the isolated fixture package" }
        originalDisplayPreferences = application.displayController.preferences.first()
        originalReaderPreferences = application.readerPreferencesRepository.preferences.first()
        originalLibraryPreferences = application.libraryPreferencesRepository.preferences.first()
        application.libraryPreferencesRepository.updateShowUpdatesOnly(false)
        preexistingSessionIds = updateSessionIds()
        originalSourceAvailability = application.libraryRepository.sourceAvailability(SOURCE_ID)
        sourceWasExcluded = sourceIsExcluded()
        mirrorBindingExisted = application.libraryRepository.remoteMirrorBindings()
            .any { it.sourceId == SOURCE_ID }
        deleteFixtureUpdateRows()
        if (sourceWasExcluded) deleteSourceExclusion()
        deleteFixtureReaderState()
        application.libraryRepository.removeRemoteMirrorBook(identity)
        application.libraryRepository.removeFromLibrary(identity)
        installSignedFixture()
        fixtureBook = LibraryBook(
            identity = identity,
            title = "雾港纪事",
            addedAt = Instant.EPOCH,
            metadataUpdatedAt = Instant.EPOCH,
            author = "林川",
            canonicalUrl = "https://www.wenku8.net/book/1234.htm",
        )
        application.libraryRepository.addToLibrary(fixtureBook)
        application.libraryRepository.saveProgress(
            ReadingProgress(
                identity = identity,
                locator = ReaderLocator(
                    document = DocumentIdentity(SOURCE_ID, REMOTE_BOOK_ID, "10001"),
                    blockId = "fixture-resume",
                    characterOffset = 0,
                    chapterProgress = 0.25,
                    bookProgress = 0.25,
                    capturedAt = FIXTURE_TIME,
                ),
            ),
        )
        application.readerPreferencesRepository.update(originalReaderPreferences.copy(flow = "paged"))
        application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
        Phase2SourceGateway.resetOperationCounts()
        composeRule.activityRule.scenario.recreate()
        Unit
    }

    @After
    fun tearDownFixtureJourney() = runBlocking {
        if (application.packageName != "org.tsuyomi.android.fixture") return@runBlocking
        // Opt-in only: retain this signed-fixture run for host-side Android CLI visual inspection.
        if (InstrumentationRegistry.getArguments().getString("keep_p4c_review_state") == "true") return@runBlocking
        Phase2SourceGateway.resetOperationCounts()
        application.libraryRepository.removeRemoteMirrorBook(identity)
        application.libraryRepository.removeFromLibrary(identity)
        deleteFixtureReaderState()
        deleteFixtureUpdateRows()
        deleteSessionsCreatedByThisJourney()
        if (sourceWasExcluded) restoreSourceExclusion()
        if (!mirrorBindingExisted) deleteFixtureMirrorBinding()
        if (::originalLibraryPreferences.isInitialized) {
            application.libraryPreferencesRepository.updateRootNodes(originalLibraryPreferences.rootNodes)
            application.libraryPreferencesRepository.updateShowUpdatesOnly(originalLibraryPreferences.showUpdatesOnly)
        }
        restoreSourceAvailability()
        if (::originalDisplayPreferences.isInitialized) {
            application.displayController.setDisplayPreference(originalDisplayPreferences.displayPreference)
            application.displayController.setColorSchemePreference(originalDisplayPreferences.colorSchemePreference)
            application.displayController.setDynamicColorEnabled(originalDisplayPreferences.dynamicColorEnabled)
        }
        if (::originalReaderPreferences.isInitialized) {
            application.readerPreferencesRepository.update(originalReaderPreferences)
        }
        if (::fixtureArchive.isInitialized) fixtureArchive.delete()
    }

    @Test
    fun signed_fixture_updates_integrate_with_library_root_without_website_writeback() {
        waitForText("书架")
        composeRule.onNodeWithTag("library-update-filter").assertIsDisplayed()

        Phase2SourceGateway.useShortUpdateBaselineDirectory()
        composeRule.onNodeWithContentDescription("刷新").performClick()
        val baseline = awaitTerminalSession(preexistingSessionIds)
        assertTrue(baseline.updates.none { it.identity == identity })
        assertEquals("UNCHANGED", baseline.sessionItems.single { it.identity == identity }.state)
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())

        Phase2SourceGateway.useAppendedUpdateDirectory()
        composeRule.onNodeWithContentDescription("刷新").performClick()
        val appended = awaitTerminalSession(preexistingSessionIds + requireNotNull(baseline.session).id)
        val update = appended.updates.single { it.identity == identity }
        assertEquals(listOf("10002", "10003"), update.newChapterIds)
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())
        waitForTag(updateRowTag)
        composeRule.onNodeWithTag("library-update-filter").performClick()
        composeRule.onNodeWithText("有更新").performClick()
        waitForTag("library-filter-summary-edit")
        composeRule.onNodeWithTag("library-filter-summary-edit").performClick()
        composeRule.onNodeWithText("筛选与排序").assertIsDisplayed()
        composeRule.onNodeWithText("筛选").assertIsDisplayed()
        composeRule.onNodeWithText("排序").assertIsDisplayed()
        composeRule.onNodeWithText("全部").performClick()
        waitForTag(updateRowTag)

        composeRule.onNodeWithTag(updateActionsTag, useUnmergedTree = true).performClick()
        val ignoreLabel = composeRule.activity.getString(org.tsuyomi.feature.library.R.string.updates_ignore_current)
        waitForText(ignoreLabel)
        composeRule.onNodeWithText(ignoreLabel).performClick()
        waitUntil { currentUpdate() == null }
        composeRule.onNodeWithText("撤销").performClick()
        waitUntil { currentUpdate() != null }
        waitForTag(updateRowTag)
        runBlocking {
            application.libraryRepository.ensureRemoteMirrorBinding(SOURCE_ID, "文库8")
            application.libraryRepository.upsertRemoteMirrorBook(fixtureBook, targetId = null)
            application.libraryRepository.removeFromLibrary(identity)
        }
        composeRule.activityRule.scenario.recreate()
        waitUntil {
            composeRule.onAllNodesWithContentDescription("Wenku8", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Wenku8", substring = true).performClick()
        waitForTag(updateRowTag)

        composeRule.onNodeWithTag(updateRowTag).performClick()
        waitForTag("detail-identity-module")
        assertEquals(
            "10001",
            runBlocking { application.libraryRepository.progress(identity)?.locator?.document?.contentId },
        )
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForTag(updateRowTag)

        completeChapter("10003")
        waitUntil {
            runBlocking { application.libraryRepository.completedChapterIds(identity) } == setOf("10003") &&
                currentUpdate() != null
        }

        completeChapter("10002")
        waitUntil {
            runBlocking { application.libraryRepository.completedChapterIds(identity) } == setOf("10002", "10003") &&
                currentUpdate() == null
        }
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())
        composeRule.activityRule.scenario.recreate()
        waitUntil { currentUpdate() == null }
        if (InstrumentationRegistry.getArguments().getString("keep_p4c_review_state") == "true") {
            prepareVisualReviewState()
        }
    }

    private fun completeChapter(chapterId: String) {
        runBlocking {
            application.libraryRepository.markChapterCompleted(identity, chapterId)
            application.updateCoordinator.reconcileCompleted(
                identity,
                application.libraryRepository.completedChapterIds(identity),
            )
        }
    }

    private fun prepareVisualReviewState() {
        runBlocking {
            deleteFixtureReaderState()
            deleteFixtureUpdateRows()
            deleteSessionsCreatedByThisJourney()
        }
        Phase2SourceGateway.useShortUpdateBaselineDirectory()
        val baselinePreviousIds = runBlocking { updateSessionIds() }
        composeRule.onNodeWithContentDescription("刷新").performClick()
        assertEquals(UpdateSessionStates.COMPLETED, awaitTerminalSession(baselinePreviousIds).session?.state)

        Phase2SourceGateway.useAppendedUpdateDirectory()
        val appendPreviousIds = runBlocking { updateSessionIds() }
        composeRule.onNodeWithContentDescription("刷新").performClick()
        assertEquals(UpdateSessionStates.COMPLETED, awaitTerminalSession(appendPreviousIds).session?.state)
        waitForTag(updateRowTag)
        composeRule.onNodeWithText("+2").assertExists()
        assertEquals(0, Phase2SourceGateway.websiteMutationCount())
    }

    private fun awaitTerminalSession(previousIds: Set<String>): UpdateSnapshot {
        var terminal: UpdateSnapshot? = null
        composeRule.waitUntil(timeoutMillis = SESSION_TIMEOUT_MILLIS) {
            val snapshot = runBlocking { application.updateCoordinator.snapshots.first() }
            val session = snapshot.session
            if (session != null && session.id !in previousIds && session.state in TERMINAL_SESSION_STATES) {
                val work = WorkManager.getInstance(application)
                    .getWorkInfosForUniqueWork(WorkManagerUpdateScheduler.ManualWorkName).get()
                // A terminal business snapshot can precede WorkManager completion. Until then,
                // KEEP legitimately coalesces the next refresh instead of starting our next fixture.
                if (work.any { !it.state.isFinished }) {
                    false
                } else {
                    terminal = snapshot
                    true
                }
            } else {
                false
            }
        }
        return requireNotNull(terminal)
    }

    private fun currentUpdate() = runBlocking {
        application.updateCoordinator.snapshots.first().updates.singleOrNull { it.identity == identity }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = SESSION_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        try {
            composeRule.waitUntil(timeoutMillis = SESSION_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError("Missing $tag\n" + composeRule.onAllNodes(isRoot(), useUnmergedTree = true).printToString(), failure)
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = SESSION_TIMEOUT_MILLIS, condition = condition)
    }

    private suspend fun installSignedFixture() {
        val controller = SourceInstallController(composeRule.activity, application.libraryRepository)
        val existing = controller.activateInstalledSource(SOURCE_ID)
        val packageInfo = if (existing?.packageSha256 == FIXTURE_SHA256) {
            existing
        } else {
            fixtureArchive = File(composeRule.activity.cacheDir, "updates-production-fixture.hxp")
            composeRule.activity.assets.open("wenku8-fixture.hxp").use { input ->
                fixtureArchive.outputStream().use(input::copyTo)
            }
            controller.prepare(Uri.fromFile(fixtureArchive), composeRule.activity.contentResolver)
            check(controller.state is BrowseUiState.Approval) {
                "Signed Wenku8 fixture was not prepared: ${controller.state}"
            }
            controller.approve(allowDowngrade = false)
            requireNotNull(controller.activePackage)
        }
        check(packageInfo.packageSha256 == FIXTURE_SHA256) { "Unexpected signed fixture digest" }
        val availability = application.libraryRepository.sourceAvailability(SOURCE_ID)
        application.libraryRepository.setSourceAvailability(
            sourceId = SOURCE_ID,
            version = packageInfo.manifest.version.original,
            available = true,
            generation = (availability?.generation ?: 0L) + 1L,
        )
    }

    private suspend fun deleteFixtureUpdateRows() {
        application.databaseForTesting.withTransaction {
            val database = application.databaseForTesting.openHelper.writableDatabase
            database.execSQL(
                "DELETE FROM update_ignore_undos WHERE source_id = ? AND remote_book_id = ?",
                arrayOf(SOURCE_ID, REMOTE_BOOK_ID),
            )
            database.execSQL(
                "DELETE FROM unresolved_updates WHERE source_id = ? AND remote_book_id = ?",
                arrayOf(SOURCE_ID, REMOTE_BOOK_ID),
            )
            database.execSQL(
                "DELETE FROM update_baselines WHERE source_id = ? AND remote_book_id = ?",
                arrayOf(SOURCE_ID, REMOTE_BOOK_ID),
            )
            database.execSQL(
                "DELETE FROM update_book_exclusions WHERE source_id = ? AND remote_book_id = ?",
                arrayOf(SOURCE_ID, REMOTE_BOOK_ID),
            )
        }
    }

    private suspend fun deleteFixtureReaderState() {
        application.databaseForTesting.withTransaction {
            val database = application.databaseForTesting.openHelper.writableDatabase
            database.execSQL(
                "DELETE FROM completed_chapters WHERE source_id = ? AND remote_book_id = ?",
                arrayOf(SOURCE_ID, REMOTE_BOOK_ID),
            )
            database.execSQL(
                "DELETE FROM reading_progress WHERE source_id = ? AND remote_book_id = ?",
                arrayOf(SOURCE_ID, REMOTE_BOOK_ID),
            )
            database.execSQL(
                "DELETE FROM reader_history WHERE source_id = ? AND remote_book_id = ?",
                arrayOf(SOURCE_ID, REMOTE_BOOK_ID),
            )
        }
    }

    private fun updateSessionIds(): Set<String> = buildSet {
        application.databaseForTesting.openHelper.readableDatabase.query("SELECT session_id FROM update_sessions").use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    private suspend fun deleteSessionsCreatedByThisJourney() {
        val created = updateSessionIds() - preexistingSessionIds
        if (created.isEmpty()) return
        application.databaseForTesting.withTransaction {
            val database = application.databaseForTesting.openHelper.writableDatabase
            created.forEach { sessionId ->
                database.execSQL("DELETE FROM update_session_items WHERE session_id = ?", arrayOf(sessionId))
                database.execSQL("DELETE FROM update_sessions WHERE session_id = ?", arrayOf(sessionId))
            }
        }
    }

    private fun sourceIsExcluded(): Boolean = application.databaseForTesting.openHelper.readableDatabase.query(
        "SELECT 1 FROM update_source_exclusions WHERE source_id = ?",
        arrayOf(SOURCE_ID),
    ).use { it.moveToFirst() }

    private suspend fun deleteSourceExclusion() {
        application.databaseForTesting.withTransaction {
            application.databaseForTesting.openHelper.writableDatabase.execSQL("DELETE FROM update_source_exclusions WHERE source_id = ?", arrayOf(SOURCE_ID))
        }
    }

    private suspend fun restoreSourceExclusion() {
        application.databaseForTesting.withTransaction {
            application.databaseForTesting.openHelper.writableDatabase.execSQL(
                "INSERT OR IGNORE INTO update_source_exclusions(source_id) VALUES (?)",
                arrayOf(SOURCE_ID),
            )
        }
    }
    private suspend fun restoreSourceAvailability() {
        val original = originalSourceAvailability
        if (original != null) {
            application.libraryRepository.setSourceAvailability(
                sourceId = original.sourceId,
                version = original.verifiedVersion,
                available = original.available,
                generation = original.generation,
            )
        } else {
            application.databaseForTesting.withTransaction {
                application.databaseForTesting.openHelper.writableDatabase.execSQL("DELETE FROM source_availability WHERE source_id = ?", arrayOf(SOURCE_ID))
            }
        }
    }


    private suspend fun deleteFixtureMirrorBinding() {
        application.databaseForTesting.withTransaction {
            application.databaseForTesting.openHelper.writableDatabase.execSQL("DELETE FROM remote_mirror_bindings WHERE source_id = ?", arrayOf(SOURCE_ID))
        }
    }

    private fun identityKey(value: BookIdentity): String =
        "${value.sourceId.length}:${value.sourceId}${value.remoteBookId.length}:${value.remoteBookId}"

    private companion object {
        const val SOURCE_ID = "org.tsuyomi.wenku8"
        const val REMOTE_BOOK_ID = "1234"
        const val FIXTURE_SHA256 = "36db147337636ddc1b5bb00a979e42bb82e97f419ab374c6a5cb62ce852d6af6"
        const val SESSION_TIMEOUT_MILLIS = 45_000L
        val FIXTURE_TIME: Instant = Instant.parse("2026-09-07T00:00:00Z")
        val TERMINAL_SESSION_STATES = setOf(
            UpdateSessionStates.COMPLETED,
            UpdateSessionStates.PARTIAL,
            UpdateSessionStates.FAILED,
            UpdateSessionStates.CANCELLED,
        )
    }
}
