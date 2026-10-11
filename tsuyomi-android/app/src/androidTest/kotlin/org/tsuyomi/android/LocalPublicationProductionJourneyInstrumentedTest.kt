/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.feature.backup.TransferUiState
import org.tsuyomi.feature.library.LocalBookImportAction
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.backup.TransferBook
import org.tsuyomi.shared.backup.TransferCodec
import org.tsuyomi.shared.backup.TransferLocalPublicationMetadata
import org.tsuyomi.shared.backup.TransferSnapshot
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.core.publication.PublicationDeleteStatus
import org.tsuyomi.feature.book.DetailMutationOperation
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.core.publication.PublicationParseResult
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderInlineKind

/** Actual provider grants, MainActivity intake, canonical Detail/Reader and durable Room state. */
@RunWith(AndroidJUnit4::class)
class LocalPublicationProductionJourneyInstrumentedTest {
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val journeyRules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        // Restore shared preferences only after Activity and composition coroutines are disposed.
        override fun after() {
            cleanup()
        }
    }).around(composeRule)

    private val application get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TsuyomiApplication
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context
    private val imports get() = ViewModelProvider(composeRule.activity)[PublicationImportCoordinator::class.java]
    private val owned = linkedSetOf<BookIdentity.Local>()
    private val inputs = mutableListOf<File>()
    private lateinit var originalDisplay: DisplayPreference
    private lateinit var originalReader: PortableReaderPreferences
    private lateinit var baseline: Set<BookIdentity>
    private lateinit var launchIntent: Intent
    private val readingAction = hasContentDescription("开始阅读") or hasContentDescription("继续阅读")
    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            // ActivityScenario matches Intent filters before accepting lifecycle events.
            // Real onNewIntent intake has already run; retain only the test's launch identity.
            if (activity is MainActivity) activity.intent = launchIntent
        }
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    @Before fun prepare() {
        check(application.packageName == "org.tsuyomi.android.fixture") { "Disposable fixture target only" }
        runBlocking {
            originalDisplay = application.displayController.preferences.first().displayPreference
            originalReader = application.readerPreferencesRepository.preferences.first()
            baseline = application.libraryRepository.localPublications().map { it.identity }.toSet()
            application.displayController.setDisplayPreference(DisplayPreference.STANDARD)
            application.readerPreferencesRepository.update(originalReader.copy(flow = "scroll"))
        }
        launchIntent = Intent(composeRule.activity.intent)
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        waitFor("书架")
    }

    @After fun closeReader() {
        application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        if (::baseline.isInitialized) {
            runBlocking(Dispatchers.Main) {
                val flow = ViewModelProvider(composeRule.activity)[LocalBookFlow::class.java]
                flow.close()
            }
        }
    }

    private fun cleanup() {
        if (::baseline.isInitialized) {
            runBlocking {
                application.libraryRepository.localPublications().map { it.identity as BookIdentity.Local }
                    .filter { it !in baseline }.forEach { identity ->
                        application.localPublications.deletePublication(identity, originalOnly = false)
                    }
                application.readerPreferencesRepository.update(originalReader)
                application.displayController.setDisplayPreference(originalDisplay)
            }
            inputs.forEach(File::delete)
        }
    }

    @Test fun system_open_and_share_read_all_four_formats_through_the_canonical_reader() {
        val txt = input("journey.txt", "生产书籍 ${UUID.randomUUID()}\n雨巷中保留的正文。\n第二段 😀。".toByteArray())
        val epub = epub()
        val mobi = nativeFixture(kf8 = false)
        val kf8 = nativeFixture(kf8 = true)
        val hybrid = input("hybrid.azw3", testContext.assets.open("publications/synthetic-hybrid.azw3").use { it.readBytes() })
        for ((index, book) in listOf(
            Triple(txt, LocalPublicationFormat.TXT, "雨巷"),
            Triple(epub, LocalPublicationFormat.EPUB, "雨巷"),
            Triple(mobi.file, LocalPublicationFormat.MOBI, "序章：中文 mixed"),
            Triple(kf8.file, LocalPublicationFormat.AZW3, "前节样式正文"),
            Triple(hybrid, LocalPublicationFormat.AZW3, "正文：繁體與简体"),
        ).withIndex()) {
            deliver(book.first, if (index == 0) Intent.ACTION_VIEW else Intent.ACTION_SEND)
            confirm()
            val identity = newestBook()
            assertEquals(book.second, runBlocking { application.libraryRepository.book(identity) }!!.localPublication!!.format)
            openLatestResult()
            startReading(book.third)
            composeRule.onNodeWithTag("reader-content-surface").assertIsDisplayed()
            waitFor(book.third, substring = true)
            when (book.first) {
                mobi.file -> assertNativeLaterReopen(mobi, identity)
                kf8.file -> assertNativeLaterReopen(kf8, identity)
            }
            composeRule.waitUntil(10_000) { runBlocking { application.libraryRepository.progress(identity) != null } }
            val before = runBlocking { application.libraryRepository.progress(identity) }!!.locator
            assertEquals(identity, before.document.book)
            assertTrue(application.localPublications.originals.original(identity.publicationId)!!.openInputStream()
                .use { it.readBytes() }.contentEquals(book.first.readBytes()))
            composeRule.activityRule.scenario.recreate()
            try {
                waitFor(book.third, substring = true)
            } catch (error: Throwable) {
                val reader = ViewModelProvider(composeRule.activity)[LocalBookFlow::class.java]
                throw AssertionError("${book.second} recreation: saved=$before; identity=${reader.identity}; " +
                    "document=${reader.document?.identity}; publication=${reader.publication?.publicationId}; " +
                    "loading=${reader.loading}; failure=${reader.failure}", error)
            }
            composeRule.onNodeWithTag("reader-content-surface").assertIsDisplayed()
            assertEquals(before.document, runBlocking { application.libraryRepository.progress(identity) }!!.locator.document)
            back()
            back()
        }
    }

    @Test fun two_distinct_local_flow_owners_reject_retired_callbacks_without_affecting_unrelated_books() {
        val unrelated = importWithReadingState(epub())
        val unrelatedBook = runBlocking { application.libraryRepository.book(unrelated) }
        val unrelatedBookmarks = runBlocking { application.libraryRepository.bookmarks(unrelated) }
        val unrelatedOriginal = checkNotNull(application.localPublications.originals.original(unrelated.publicationId))
        for (originalOnly in listOf(true, false)) {
            val identity = importWithReadingState(epub())
            startReading()
            val primary = ViewModelProvider(composeRule.activity)[LocalBookFlow::class.java]
            val secondaryStore = ViewModelStore()
            val secondary = LocalBookFlow(application, SavedStateHandle()).also { secondaryStore.put("secondary-local-flow", it) }
            try {
                runBlocking(Dispatchers.Main) { withTimeout(15_000) { secondary.open(identity.publicationId)?.join() } }
                val firstOwner = checkNotNull(primary.lifecycleOwner)
                val secondOwner = checkNotNull(secondary.lifecycleOwner)
                assertTrue(primary !== secondary)
                assertTrue(firstOwner !== secondOwner && firstOwner.job !== secondOwner.job)
                assertTrue(firstOwner.job.isActive && secondOwner.job.isActive)
                val bookmark = runBlocking { application.libraryRepository.bookmarks(identity).single() }
                val progress = checkNotNull(runBlocking { application.libraryRepository.progress(identity) })
                val epoch = primary.documentGeneration
                runBlocking(Dispatchers.Main) {
                    assertEquals(PublicationDeleteStatus.COMPLETED, secondary.delete(originalOnly, secondOwner))
                    assertRetiredCallbacks(primary, firstOwner, bookmark, epoch)
                    assertRetiredCallbacks(secondary, secondOwner, bookmark, epoch)
                }
                assertEquals(null, primary.document)
                assertEquals(null, primary.publication)
                assertEquals(null, application.localPublications.originals.original(identity.publicationId))
                assertFalse(runBlocking { application.readerPreferencesRepository.requestedFlow(identity, globalDefault = originalReader).first() }.overridden)
                if (originalOnly) {
                    assertFalse(checkNotNull(runBlocking { application.libraryRepository.book(identity) }).localPublication!!.originalAvailable)
                    assertEquals(listOf(bookmark), runBlocking { application.libraryRepository.bookmarks(identity) })
                    assertEquals(progress.locator.document, runBlocking { application.libraryRepository.progress(identity) }!!.locator.document)
                } else {
                    assertEquals(null, runBlocking { application.libraryRepository.book(identity) })
                    assertTrue(runBlocking { application.libraryRepository.bookmarks(identity).isEmpty() })
                    assertEquals(null, runBlocking { application.libraryRepository.progress(identity) })
                }
                assertEquals(unrelatedBook, runBlocking { application.libraryRepository.book(unrelated) })
                assertEquals(unrelatedBookmarks, runBlocking { application.libraryRepository.bookmarks(unrelated) })
                assertEquals(unrelatedOriginal.sha256, application.localPublications.originals.original(unrelated.publicationId)!!.sha256)
            } finally {
                runBlocking(Dispatchers.Main) { secondary.close(); secondaryStore.clear() }
            }
        }
    }

    @Test fun paused_kindle_duplicate_review_releases_native_lease_and_target_delete_preserves_unrelated_reader() {
        val firstInput = input("paused-review.mobi", testContext.assets.open("publications/synthetic-mobi7.mobi").use { it.readBytes() })
        deliver(firstInput, Intent.ACTION_VIEW)
        confirm()
        val target = newestBook()
        val otherInput = input("unrelated-reader.azw3", testContext.assets.open("publications/synthetic-kf8.azw3").use { it.readBytes() })
        deliver(otherInput, Intent.ACTION_SEND)
        confirm()
        val unrelated = newestBook()
        val original = checkNotNull(application.localPublications.originals.original(unrelated.publicationId))
        deliver(firstInput, Intent.ACTION_SEND)
        val pending = checkNotNull(imports.state.review)
        composeRule.runOnUiThread { imports.edit(LocalBookImportAction.ChooseAssociation(target.publicationId)) }
        openLatestResult()
        startReading("正文：繁體與简体")
        val primary = ViewModelProvider(composeRule.activity)[LocalBookFlow::class.java]
        assertEquals(pending.matches, checkNotNull(imports.state.review).matches)
        val secondaryStore = ViewModelStore()
        val secondary = LocalBookFlow(application, SavedStateHandle()).also { secondaryStore.put("stopped-reader-successor", it) }
        try {
            // This is one genuinely stopped Activity plus a distinct production flow, not two Activities.
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking(Dispatchers.Main) { withTimeout(15_000) { secondary.open(target.publicationId)?.join() } }
            assertNotNull(secondary.publication)
            runBlocking(Dispatchers.Main) { secondary.close() }
        } finally {
            runBlocking(Dispatchers.Main) { secondary.close(); secondaryStore.clear() }
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        }
        waitFor("正文：繁體與简体", substring = true)
        val unrelatedOwner = checkNotNull(primary.lifecycleOwner)
        runBlocking(Dispatchers.Main) { assertEquals(PublicationDeleteStatus.COMPLETED, application.localPublications.deletePublication(target, false)) }
        composeRule.waitUntil(15_000) { !imports.state.busy && imports.state.review == null }
        composeRule.runOnUiThread { imports.edit(LocalBookImportAction.Confirm) }
        assertTrue(unrelatedOwner.job.isActive)
        assertEquals(unrelated, primary.identity)
        composeRule.onNodeWithTag("reader-content-surface").assertIsDisplayed()
        assertEquals(null, runBlocking { application.libraryRepository.book(target) })
        assertEquals(null, application.localPublications.originals.original(target.publicationId))
        assertTrue(checkNotNull(runBlocking { application.libraryRepository.book(unrelated) }).localPublication!!.originalAvailable)
        assertEquals(original.sha256, application.localPublications.originals.original(unrelated.publicationId)!!.sha256)
        assertNotNull(runBlocking { application.libraryRepository.progress(unrelated) })
    }

    private suspend fun assertRetiredCallbacks(flow: LocalBookFlow, owner: LocalPublicationManager.LocalOwner, locator: ReaderLocator, epoch: Long) {
        assertFalse(owner.job.isActive)
        assertEquals(null, flow.addToLibrary(owner))
        assertEquals(null, flow.removeFromLibrary(owner))
        assertEquals(null, flow.rate(4, owner))
        assertEquals(null, flow.tags(listOf("迟到"), owner))
        assertEquals(null, flow.toggleReadLater(owner))
        var invoked = false
        assertEquals(null, flow.mutate(DetailMutationOperation.ADD_TO_LIBRARY, owner) { invoked = true })
        assertFalse(invoked)
        assertFalse(flow.mounted(epoch, owner))
        assertFalse(flow.saveProgress(locator, locator.precision, epoch, owner))
        assertFalse(flow.toggleBookmark(locator, owner))
        assertFalse(flow.removeBookmark(locator, owner))
        assertFalse(flow.complete(locator.document.contentId, epoch, owner))
        assertFalse(flow.grantResources(setOf("https://assets.example"), owner))
        assertFalse(flow.revokeResources(owner))
        assertFalse(flow.setFlowOverride("paged", owner))
        assertEquals(null, flow.read(owner = owner))
    }

    @Test fun duplicate_association_and_two_level_deletion_preserve_reading_state_until_confirmed_full_delete() {
        val file = epub()
        deliver(file, Intent.ACTION_VIEW)
        confirm()
        val identity = newestBook()
        openLatestResult()
        startReading()
        waitFor("雨巷", substring = true)
        composeRule.onNodeWithContentDescription("添加书签").performClick()
        composeRule.waitUntil(10_000) { runBlocking { application.libraryRepository.bookmarks(identity).size == 1 } }
        val bookmark = runBlocking { application.libraryRepository.bookmarks(identity).single() }
        back()
        back()
        deliver(file, Intent.ACTION_SEND)
        waitFor("已有相同原书", substring = true)
        composeRule.onNode(hasText("生产 EPUB") and hasClickAction()).performClick()
        confirm()
        assertEquals(setOf(identity), runBlocking { application.libraryRepository.localPublications().map { it.identity }.toSet() } - baseline)
        assertEquals(listOf(bookmark), runBlocking { application.libraryRepository.bookmarks(identity) })
        openLatestResult()
        confirmDelete(originalOnly = true)
        assertFalse(runBlocking { application.libraryRepository.book(identity) }!!.localPublication!!.originalAvailable)
        assertEquals(listOf(bookmark), runBlocking { application.libraryRepository.bookmarks(identity) })
        assertNotNull(runBlocking { application.libraryRepository.progress(identity) })
        back()
        deliver(file, Intent.ACTION_SEND)
        waitFor("生产 EPUB（重新关联原书）")
        composeRule.onNodeWithText("生产 EPUB（重新关联原书）").performClick()
        confirm()
        assertTrue(runBlocking { application.libraryRepository.book(identity) }!!.localPublication!!.originalAvailable)
        assertEquals(listOf(bookmark), runBlocking { application.libraryRepository.bookmarks(identity) })
        openLatestResult()
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("彻底删除该书").performClick()
        composeRule.onNodeWithText("取消").performClick()
        assertNotNull(runBlocking { application.libraryRepository.book(identity) })
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("彻底删除该书").performClick()
        composeRule.onNodeWithText("确认").performClick()
        composeRule.waitUntil(10_000) { runBlocking { application.libraryRepository.book(identity) == null } }
        assertEquals(emptyList<Any>(), runBlocking { application.libraryRepository.bookmarks(identity) })
        assertEquals(null, runBlocking { application.libraryRepository.progress(identity) })
        assertEquals(null, application.localPublications.originals.original(identity.publicationId))
    }

    @Test fun missing_original_full_delete_removes_catalog_and_reading_state() {
        val identity = importWithReadingState(epub())
        confirmDelete(originalOnly = true)
        assertFalse(checkNotNull(runBlocking { application.libraryRepository.book(identity) }?.localPublication).originalAvailable)
        assertNotNull(runBlocking { application.libraryRepository.progress(identity) })
        assertEquals(1, runBlocking { application.libraryRepository.bookmarks(identity).size })
        confirmDelete(originalOnly = false)
        assertEquals(null, runBlocking { application.libraryRepository.book(identity) })
        assertEquals(null, runBlocking { application.libraryRepository.progress(identity) })
        assertTrue(runBlocking { application.libraryRepository.bookmarks(identity).isEmpty() })
        assertTrue(runBlocking { application.libraryRepository.completedChapterIds(identity).isEmpty() })
        assertEquals(null, runBlocking { application.libraryRepository.readerHistoryEntry(identity) })
        assertEquals(null, application.localPublications.originals.original(identity.publicationId))
    }

    @Test fun pending_duplicate_confirmation_cannot_undo_full_delete() {
        val file = epub()
        val identity = importWithReadingState(file)
        deliver(file, Intent.ACTION_SEND)
        composeRule.onNode(hasText("生产 EPUB") and hasClickAction()).performClick()
        back()
        confirmDelete(originalOnly = false)
        // Replay the old owner action even when deletion has already cancelled its deferred review.
        composeRule.runOnUiThread { imports.edit(LocalBookImportAction.Confirm) }
        composeRule.waitUntil(15_000) { imports.state.review == null && !imports.state.busy }
        assertEquals(null, runBlocking { application.libraryRepository.book(identity) })
        assertEquals(null, runBlocking { application.libraryRepository.progress(identity) })
        assertTrue(runBlocking { application.libraryRepository.bookmarks(identity).isEmpty() })
        assertEquals(null, application.localPublications.originals.original(identity.publicationId))
        assertTrue(imports.state.results.none { it.publicationId == identity.publicationId })
    }

    @Test fun pending_duplicate_confirmation_cannot_undo_original_only_delete() {
        val file = epub()
        val identity = importWithReadingState(file)
        val progress = runBlocking { application.libraryRepository.progress(identity) }
        val bookmarks = runBlocking { application.libraryRepository.bookmarks(identity) }
        deliver(file, Intent.ACTION_SEND)
        composeRule.onNode(hasText("生产 EPUB") and hasClickAction()).performClick()
        back()
        confirmDelete(originalOnly = true)
        composeRule.runOnUiThread { imports.edit(LocalBookImportAction.Confirm) }
        composeRule.waitUntil(15_000) { imports.state.review == null && !imports.state.busy }
        assertFalse(checkNotNull(runBlocking { application.libraryRepository.book(identity) }?.localPublication).originalAvailable)
        assertEquals(progress, runBlocking { application.libraryRepository.progress(identity) })
        assertEquals(bookmarks, runBlocking { application.libraryRepository.bookmarks(identity) })
        assertEquals(null, application.localPublications.originals.original(identity.publicationId))
        assertEquals(null, imports.state.results.last().publicationId)
    }

    @Test fun deleting_an_unrelated_book_preserves_the_pending_import() {
        val victim = importWithReadingState(epub())
        deliver(epub(), Intent.ACTION_SEND)
        val pending = checkNotNull(imports.state.review)
        assertTrue(pending.matches.isEmpty())
        back()
        confirmDelete(originalOnly = false)
        assertEquals(pending, imports.state.review)
        confirm()
        val imported = newestBook()
        assertTrue(imported != victim)
        assertEquals(null, runBlocking { application.libraryRepository.book(victim) })
        assertTrue(checkNotNull(runBlocking { application.libraryRepository.book(imported) }?.localPublication).originalAvailable)
        assertNotNull(application.localPublications.originals.original(imported.publicationId))
    }

    @Test fun admitted_epub_reopen_and_corrupt_record_readmission_preserve_reading_state() {
        val identity = importWithReadingState(epub())
        val book = checkNotNull(runBlocking { application.libraryRepository.book(identity) })
        val progress = runBlocking { application.libraryRepository.progress(identity) }
        val bookmarks = runBlocking { application.libraryRepository.bookmarks(identity) }
        val originals = application.localPublications.originals
        val original = checkNotNull(originals.original(identity.publicationId))
        assertNotNull(originals.admissionRecord(original))
        runBlocking(Dispatchers.Main) {
            ViewModelProvider(composeRule.activity)[LocalBookFlow::class.java].close(retainRoute = true)
        }
        runBlocking {
            application.localPublications.openPublication(book).use { assertFalse(it.admissionRebuilt) }
        }
        val record = File(application.noBackupFilesDir, "publication-originals/books/${identity.publicationId}/admission.bin")
        assertTrue(record.isFile)
        record.writeBytes(byteArrayOf(0, 1, 2))
        assertEquals(null, originals.admissionRecord(original))
        runBlocking {
            application.localPublications.openPublication(book).use { assertTrue(it.admissionRebuilt) }
            application.localPublications.openPublication(book).use { assertFalse(it.admissionRebuilt) }
        }
        assertNotNull(originals.admissionRecord(checkNotNull(originals.original(identity.publicationId))))
        assertEquals(original.sha256, checkNotNull(originals.original(identity.publicationId)).sha256)
        assertEquals(book, runBlocking { application.libraryRepository.book(identity) })
        assertEquals(progress, runBlocking { application.libraryRepository.progress(identity) })
        assertEquals(bookmarks, runBlocking { application.libraryRepository.bookmarks(identity) })
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(15_000) {
            ViewModelProvider(composeRule.activity)[LocalBookFlow::class.java].publication?.publicationId == identity.publicationId
        }
        startReading()
        assertEquals(bookmarks, runBlocking { application.libraryRepository.bookmarks(identity) })
    }

    @Test fun native_cover_readmission_preserves_healthy_legacy_sidecar_and_repairs_original_only_archive() {
        for ((asset, suffix, marker) in listOf(
            Triple("synthetic-mobi7.mobi", "mobi", "序章：中文 mixed"),
            Triple("synthetic-kf8.azw3", "azw3", "正文：繁體與简体"),
        )) {
            val source = input("legacy-cover.$suffix", testContext.assets.open("publications/$asset").use { it.readBytes() })
            deliver(source, Intent.ACTION_VIEW)
            confirm()
            val identity = newestBook()
            openLatestResult()
            startReading(marker)
            composeRule.onNodeWithContentDescription("添加书签").performClick()
            composeRule.waitUntil(10_000) { runBlocking { application.libraryRepository.bookmarks(identity).size == 1 } }
            exitReaderToImport()
            val originals = application.localPublications.originals
            val original = checkNotNull(originals.original(identity.publicationId))
            val imported = checkNotNull(runBlocking { application.libraryRepository.book(identity) })
            val metadata = checkNotNull(imported.localPublication)
            val cover = checkNotNull(originals.resource(identity.publicationId, checkNotNull(metadata.coverResourceId)))
            val reference = ReaderResourceRef.Local(identity.publicationId, cover.resourceId, cover.mimeType, cover.sha256)
            val coverBytes = checkNotNull(originals.openResource(reference)).use { it.readBytes() }
            val legacyId = UUID.randomUUID().toString()
            runBlocking(Dispatchers.IO) {
                originals.putResource(identity.publicationId, legacyId, cover.mimeType, coverBytes.inputStream(), cover.sha256, original)
                assertTrue(application.libraryRepository.compareAndSetLocalCoverResourceId(identity, metadata.format,
                    metadata.originalSha256, metadata.originalSizeBytes, metadata.coverResourceId, legacyId))
                application.libraryRepository.setRating(identity, 3)
                application.libraryRepository.setLocalTags(identity, listOf("保留资料"))
            }
            val legacyBook = checkNotNull(runBlocking { application.libraryRepository.book(identity) })
            val readingEntry = runBlocking { application.libraryRepository.libraryEntry(identity) }
            val bookmarks = runBlocking { application.libraryRepository.bookmarks(identity) }
            val progress = checkNotNull(runBlocking { application.libraryRepository.progress(identity) })
            val record = File(application.noBackupFilesDir, "publication-originals/books/${identity.publicationId}/admission.bin")
            assertTrue(record.delete())
            runBlocking {
                application.localPublications.openPublication(legacyBook).use { assertTrue(it.admissionRebuilt) }
                application.localPublications.openPublication(legacyBook).use { assertFalse(it.admissionRebuilt) }
            }
            assertEquals(legacyBook, runBlocking { application.libraryRepository.book(identity) })
            assertEquals(readingEntry, runBlocking { application.libraryRepository.libraryEntry(identity) })
            assertTrue(checkNotNull(originals.openResource(ReaderResourceRef.Local(identity.publicationId, legacyId,
                cover.mimeType, cover.sha256))).use { it.readBytes() }.contentEquals(coverBytes))
            val archive = input("legacy-cover-originals.zip", byteArrayOf())
            val transfer = application.transferCoordinator
            runBlocking {
                val prepared = checkNotNull(transfer.prepareExport(originalReader, includesOriginals = true))
                transfer.writePreparedExport(Uri.fromFile(archive), application.contentResolver,
                    prepared.ownerGeneration, prepared.canonicalDigest, prepared.includesOriginals)
                assertEquals(TransferUiState.Exported, transfer.state)
                assertEquals(PublicationDeleteStatus.COMPLETED, application.localPublications.deletePublication(identity, false))
                transfer.readForReview(Uri.fromFile(archive), application.contentResolver)
                assertTrue(transfer.state is TransferUiState.Review)
                transfer.confirmImport()
                assertTrue("Original archive restore failed: ${transfer.state}", transfer.state is TransferUiState.Completed)
                transfer.dismissResult()
            }
            val restored = checkNotNull(runBlocking { application.libraryRepository.book(identity) })
            val restoredEntry = runBlocking { application.libraryRepository.libraryEntry(identity) }
            val restoredProgress = checkNotNull(runBlocking { application.libraryRepository.progress(identity) })
            // Portable progress reconstructs parser-local block IDs; every portable position field must survive.
            assertEquals(progress.identity, restoredProgress.identity)
            assertEquals(progress.locator.document, restoredProgress.locator.document)
            assertEquals(progress.locator.textAnchorDigest, restoredProgress.locator.textAnchorDigest)
            assertEquals(progress.locator.characterOffset, restoredProgress.locator.characterOffset)
            assertEquals(progress.locator.chapterProgress, restoredProgress.locator.chapterProgress)
            assertEquals(progress.locator.bookProgress, restoredProgress.locator.bookProgress)
            assertEquals(progress.updatedAt, restoredProgress.updatedAt)
            assertEquals(legacyId, restored.localPublication!!.coverResourceId)
            assertEquals(null, originals.resource(identity.publicationId, legacyId))
            val repairedId = runBlocking {
                application.localPublications.openPublication(restored).use { handle ->
                    assertTrue(handle.admissionRebuilt)
                    val parsed = (handle.result as PublicationParseResult.Ready).publication
                    val rebuiltCover = parsed.metadata.cover as ReaderResourceRef.Local
                    assertFalse(rebuiltCover.resourceId == legacyId)
                    assertTrue(checkNotNull(originals.openResource(rebuiltCover)).use { it.readBytes() }.contentEquals(coverBytes))
                    rebuiltCover.resourceId
                }
            }
            val repaired = checkNotNull(runBlocking { application.libraryRepository.book(identity) })
            assertEquals(restored.copy(localPublication = restored.localPublication!!.copy(coverResourceId = repairedId)), repaired)
            assertEquals(restoredEntry?.copy(book = repaired), runBlocking { application.libraryRepository.libraryEntry(identity) })
            assertEquals(restoredProgress, runBlocking { application.libraryRepository.progress(identity) })
            assertEquals(bookmarks, runBlocking { application.libraryRepository.bookmarks(identity) })
            assertEquals(original.sha256, originals.original(identity.publicationId)!!.sha256)
            runBlocking { application.localPublications.openPublication(repaired).use { assertFalse(it.admissionRebuilt) } }
            openLatestResult()
            startReading(marker)
            val reopened = ViewModelProvider(composeRule.activity)[LocalBookFlow::class.java]
            assertEquals(progress.locator.document, checkNotNull(reopened.document).identity)
            assertEquals(restoredProgress.locator, reopened.restoredLocator)
            composeRule.onNodeWithTag("reader-content-surface").assertIsDisplayed()
            exitReaderToImport()
        }
    }

    @Test fun original_archive_restores_a_deleted_publication_and_its_semantic_reading_state() {
        val source = epub()
        deliver(source, Intent.ACTION_VIEW)
        confirm()
        val identity = newestBook()
        openLatestResult()
        startReading()
        composeRule.onNodeWithContentDescription("添加书签").performClick()
        composeRule.waitUntil(10_000) { runBlocking { application.libraryRepository.bookmarks(identity).size == 1 } }
        back()
        back()
        val bookmark = runBlocking { application.libraryRepository.bookmarks(identity).single() }
        val progress = checkNotNull(runBlocking { application.libraryRepository.progress(identity) })
        val original = checkNotNull(application.localPublications.originals.original(identity.publicationId))
        val archive = input("originals.zip", byteArrayOf())
        val coordinator = application.transferCoordinator
        runBlocking {
            coordinator.recoverPendingImport()
            val prepared = checkNotNull(coordinator.prepareExport(originalReader, includesOriginals = true))
            coordinator.writePreparedExport(Uri.fromFile(archive), application.contentResolver,
                prepared.ownerGeneration, prepared.canonicalDigest, prepared.includesOriginals)
            assertEquals(TransferUiState.Exported, coordinator.state)
            application.localPublications.originals.deletePublication(identity.publicationId,
                application.libraryRepository::deleteLocalPublication)
            assertEquals(null, application.libraryRepository.book(identity))
            assertEquals(null, application.localPublications.originals.original(identity.publicationId))
            assertTrue(source.delete())
            coordinator.readForReview(Uri.fromFile(archive), application.contentResolver)
            assertTrue("Archive review failed: ${coordinator.state}", coordinator.state is TransferUiState.Review)
            coordinator.confirmImport()
            assertTrue("Archive restore failed: ${coordinator.state}", coordinator.state is TransferUiState.Completed)
            assertTrue(checkNotNull(application.libraryRepository.book(identity)?.localPublication).originalAvailable)
            assertEquals(original.sha256, checkNotNull(application.localPublications.originals.original(identity.publicationId)).sha256)
            val restoredProgress = checkNotNull(application.libraryRepository.progress(identity))
            assertEquals(progress.locator.document, restoredProgress.locator.document)
            assertEquals(progress.locator.characterOffset, restoredProgress.locator.characterOffset)
            assertEquals(progress.locator.textAnchorDigest, restoredProgress.locator.textAnchorDigest)
            val restoredBookmark = application.libraryRepository.bookmarks(identity).single()
            assertEquals(bookmark.document, restoredBookmark.document)
            assertEquals(bookmark.characterOffset, restoredBookmark.characterOffset)
            assertEquals(bookmark.textAnchorDigest, restoredBookmark.textAnchorDigest)
            coordinator.dismissResult()
            val retained = checkNotNull(application.libraryRepository.book(identity)?.localPublication)
            val conflicting = TransferBook(
                identity = identity,
                title = "较新的导入资料",
                updatedAt = Instant.now().plusSeconds(1),
                localPublication = TransferLocalPublicationMetadata(
                    retained.format, "b".repeat(64), retained.originalSizeBytes, retained.txtOptions, retained.coverResourceId,
                ),
            )
            val metadata = input("conflicting-metadata.json", TransferCodec.encode(
                TransferSnapshot(Instant.now(), listOf(conflicting), emptyList()),
            ))
            coordinator.readForReview(Uri.fromFile(metadata), application.contentResolver)
            val review = coordinator.state as TransferUiState.Review
            assertTrue(review.value.conflictCodes.any { it.startsWith("existing-local-original-retained") })
            coordinator.confirmImport()
            assertTrue(coordinator.state is TransferUiState.Completed)
            val merged = checkNotNull(application.libraryRepository.book(identity))
            assertEquals(conflicting.title, merged.title)
            assertEquals(retained, merged.localPublication)
            assertEquals(original.sha256, checkNotNull(application.localPublications.originals.original(identity.publicationId)).sha256)
            coordinator.dismissResult()
        }
        startReading()
    }

    private fun importWithReadingState(file: File): BookIdentity.Local {
        deliver(file, Intent.ACTION_VIEW)
        confirm()
        val identity = newestBook()
        openLatestResult()
        startReading()
        composeRule.onNodeWithContentDescription("添加书签").performClick()
        composeRule.waitUntil(10_000) { runBlocking { application.libraryRepository.bookmarks(identity).size == 1 } }
        exitReaderToDetail()
        return identity
    }

    private fun confirmDelete(originalOnly: Boolean) {
        val flow = ViewModelProvider(composeRule.activity)[LocalBookFlow::class.java]
        val identity = checkNotNull(flow.identity)
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText(if (originalOnly) "删除私有原书" else "彻底删除该书").performClick()
        composeRule.onNodeWithText("确认").performClick()
        if (originalOnly) composeRule.waitUntil(15_000) {
            runBlocking { application.libraryRepository.book(identity)?.localPublication?.originalAvailable == false } &&
                flow.identity == identity && flow.admittedRouteEntryId != null && !flow.loading &&
                flow.book?.localPublication?.originalAvailable == false
        } else composeRule.waitUntil(15_000) {
            runBlocking { application.libraryRepository.book(identity) == null } &&
                imports.state.results.none { it.publicationId == identity.publicationId }
        }
    }

    private data class NativeFixture(val file: File, val firstImage: ByteArray, val laterImage: ByteArray, val laterMarker: String, val styled: Boolean)

    /** Derives only CC0 synthetic PalmDB records; real isolated producer/consumer paths remain unchanged. */
    private fun nativeFixture(kf8: Boolean): NativeFixture {
        val source = testContext.assets.open("publications/${if (kf8) "synthetic-kf8.azw3" else "synthetic-mobi7.mobi"}").use { it.readBytes() }
        val sourceHeader = ByteBuffer.wrap(source).order(ByteOrder.BIG_ENDIAN)
        val count = sourceHeader.getShort(76).toInt() and 0xffff
        val offsets = IntArray(count) { sourceHeader.getInt(78 + 8 * it) }
        val records = MutableList(count) { index -> source.copyOfRange(offsets[index], if (index + 1 < count) offsets[index + 1] else source.size) }
        val laterImage = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAEElEQVR4nGNgaPj/H4xhDABS0gn5PEa22gAAAABJRU5ErkJggg==", Base64.DEFAULT)
        val firstImage = records[if (kf8) 12 else 2]
        val header = ByteBuffer.wrap(records[0]).order(ByteOrder.BIG_ENDIAN)
        if (!kf8) {
            records[1] = records[1].toString(Charsets.UTF_8)
                .replace("</body>", "<p>后章资源<img recindex=\"2\"/></p></body>").toByteArray(Charsets.UTF_8)
            records[3] = laterImage
        } else {
            val early = "<html><head><link rel=\"stylesheet\" href=\"kindle:flow:0001?mime=text/css\"/></head><body><h1 id=\"early\">前节资源</h1><p class=\"early\">前节样式正文</p><img src=\"kindle:embed:0001?mime=image/png\"/></body></html>".toByteArray(Charsets.UTF_8)
            val later = "<html><head><link rel=\"stylesheet\" href=\"kindle:flow:0002?mime=text/css\"/></head><body><h1 id=\"later\">后节资源</h1><p class=\"late\">后节样式正文</p><img src=\"kindle:embed:0002?mime=image/png\"/></body></html>".toByteArray(Charsets.UTF_8)
            val firstCss = ".early{font-style:italic;background-image:url(kindle:embed:0001?mime=image/png)}".toByteArray(Charsets.UTF_8)
            val laterCss = ".late{font-weight:bold;background-image:url(kindle:embed:0002?mime=image/png)}".toByteArray(Charsets.UTF_8)
            records[1] = ByteArrayOutputStream().apply { write(early); write(later); write(firstCss); write(laterCss) }.toByteArray()
            val htmlEnd = early.size + later.size
            val firstCssEnd = htmlEnd + firstCss.size
            records[2] = ByteBuffer.allocate(36).order(ByteOrder.BIG_ENDIAN)
                .put("FDST".toByteArray(Charsets.US_ASCII)).putInt(12).putInt(3)
                .putInt(0).putInt(htmlEnd).putInt(htmlEnd).putInt(firstCssEnd)
                .putInt(firstCssEnd).putInt(records[1].size).array()
            // Each unchanged HTML section is one fragment inserted into an empty skeleton.
            records[4] = indexRecord(records[4],
                indexEntry("SKEL0000000000", 3, 1, 0, 0),
                indexEntry("SKEL0000000001", 3, 1, early.size, 0))
            ByteBuffer.wrap(records[3]).order(ByteOrder.BIG_ENDIAN).putInt(36, 2)
            val earlySelector = "P-//*[@id='early']".toByteArray(Charsets.UTF_8)
            val laterSelector = "P-//*[@id='later']".toByteArray(Charsets.UTF_8)
            val earlySelectorLength = indexedUInt(earlySelector.size)
            records[8] = ByteArrayOutputStream().apply {
                write(earlySelectorLength); write(earlySelector)
                write(indexedUInt(laterSelector.size)); write(laterSelector)
            }.toByteArray()
            records[7] = indexRecord(records[7],
                indexEntry("0", 15, 0, 0, 0, 0, early.size),
                indexEntry(early.size.toString(), 15, earlySelectorLength.size + earlySelector.size, 0, 1, 0, later.size))
            ByteBuffer.wrap(records[6]).order(ByteOrder.BIG_ENDIAN).putInt(36, 2)
            val earlyTitle = "前节资源".toByteArray(Charsets.UTF_8)
            val laterTitle = "后节资源".toByteArray(Charsets.UTF_8)
            val earlyTitleLength = indexedUInt(earlyTitle.size)
            records[11] = ByteArrayOutputStream().apply {
                write(earlyTitleLength); write(earlyTitle)
                write(indexedUInt(laterTitle.size)); write(laterTitle)
            }.toByteArray()
            records[10] = indexRecord(records[10],
                indexEntry("entry0", 31, 0, early.size, 0, 0, 0, 0),
                indexEntry("entry1", 31, early.size, later.size, earlyTitleLength.size + earlyTitle.size, 0, 1, 0))
            ByteBuffer.wrap(records[9]).order(ByteOrder.BIG_ENDIAN).putInt(36, 2)
            header.putInt(196, 3)
            records[13] = laterImage
        }
        header.putInt(4, records[1].size)
        val rebuilt = ByteArray(offsets[0] + records.sumOf { it.size })
        source.copyInto(rebuilt, endIndex = offsets[0])
        val table = ByteBuffer.wrap(rebuilt).order(ByteOrder.BIG_ENDIAN)
        var position = offsets[0]
        records.forEachIndexed { index, record ->
            table.putInt(78 + 8 * index, position)
            record.copyInto(rebuilt, position)
            position += record.size
        }
        return NativeFixture(input(if (kf8) "two-section.azw3" else "later-image.mobi", rebuilt), firstImage, laterImage,
            if (kf8) "后节样式正文" else "后章资源", kf8)
    }

    private fun indexRecord(template: ByteArray, vararg entries: ByteArray): ByteArray {
        val headerSize = ByteBuffer.wrap(template).order(ByteOrder.BIG_ENDIAN).getInt(4)
        val idxt = headerSize + entries.sumOf { it.size }
        val record = ByteArray(idxt + 4 + entries.size * 2)
        template.copyInto(record, endIndex = headerSize)
        var offset = headerSize
        entries.forEach { entry -> entry.copyInto(record, offset); offset += entry.size }
        ByteBuffer.wrap(record).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(20, idxt); putInt(24, entries.size); position(idxt)
            put("IDXT".toByteArray(Charsets.US_ASCII))
            offset = headerSize
            entries.forEach { entry -> putShort(offset.toShort()); offset += entry.size }
        }
        return record
    }

    private fun indexEntry(name: String, controlByte: Int, vararg values: Int): ByteArray = ByteArrayOutputStream().apply {
        val encodedName = name.toByteArray(Charsets.US_ASCII)
        write(encodedName.size); write(encodedName); write(controlByte)
        values.forEach { write(indexedUInt(it)) }
    }.toByteArray()

    private fun indexedUInt(value: Int): ByteArray {
        var width = 1
        var remaining = value ushr 7
        while (remaining != 0) { width++; remaining = remaining ushr 7 }
        val result = ByteArray(width) { index -> ((value ushr ((width - index - 1) * 7)) and 0x7f).toByte() }
        result[result.lastIndex] = (result.last().toInt() or 0x80).toByte()
        return result
    }

    private fun assertNativeLaterReopen(fixture: NativeFixture, identity: BookIdentity.Local) {
        val flow = ViewModelProvider(composeRule.activity)[LocalBookFlow::class.java]
        val sections = checkNotNull(flow.publication).sections
        assertEquals(2, sections.size)
        assertNativeSection(flow, fixture.firstImage, if (fixture.styled) ReaderInlineKind.Emphasis else null)
        runBlocking(Dispatchers.Main) { withTimeout(15_000) { flow.read(sections[1].contentId, sectionStart = true)?.join() } }
        waitFor(fixture.laterMarker, substring = true)
        assertNativeSection(flow, fixture.laterImage, if (fixture.styled) ReaderInlineKind.Strong else null)
        composeRule.waitUntil(10_000) {
            runBlocking { application.libraryRepository.progress(identity)?.locator?.document?.contentId == sections[1].contentId }
        }
        val retained = checkNotNull(runBlocking { application.libraryRepository.progress(identity) }).locator
        exitReaderToImport()
        openLatestResult()
        startReading(fixture.laterMarker)
        assertEquals(retained.document, checkNotNull(flow.document).identity)
        assertNativeSection(flow, fixture.laterImage, if (fixture.styled) ReaderInlineKind.Strong else null)
        runBlocking(Dispatchers.Main) { withTimeout(15_000) { flow.read(sections[0].contentId, sectionStart = true)?.join() } }
        waitFor(if (fixture.styled) "前节样式正文" else "序章：中文 mixed", substring = true)
        assertNativeSection(flow, fixture.firstImage, if (fixture.styled) ReaderInlineKind.Emphasis else null)
    }

    private fun assertNativeSection(flow: LocalBookFlow, expectedImage: ByteArray, style: ReaderInlineKind?) {
        val document = checkNotNull(flow.document)
        val image = document.blocks.filterIsInstance<ReaderBlock.Image>().single()
        val reference = image.resource as ReaderResourceRef.Local
        assertTrue(runBlocking(Dispatchers.IO) { checkNotNull(flow.publication).openResource(reference).use { it.readBytes() } }.contentEquals(expectedImage))
        if (style != null) assertTrue(document.blocks.filterIsInstance<ReaderBlock.Paragraph>().any { paragraph -> paragraph.spans.any { it.kind == style } })
        composeRule.onNodeWithTag("reader-content-surface").assertIsDisplayed()
    }

    private fun input(name: String, bytes: ByteArray): File {
        val directory = File(application.cacheDir, "publications").apply { mkdirs() }
        return File(directory, "${UUID.randomUUID()}-$name").apply { writeBytes(bytes); inputs += this }
    }

    private fun epub(): File {
        val file = input("journey.epub", byteArrayOf())
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, text) in linkedMapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0" unique-identifier="id"><metadata><dc:identifier id="id">${UUID.randomUUID()}</dc:identifier><dc:title>生产 EPUB</dc:title><dc:language>zh</dc:language></metadata><manifest><item id="body" href="body.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="body"/></spine></package>""",
                "nav.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="body.xhtml#opening">序章</a></li></ol></nav></body></html>""",
                "body.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1 id="opening">序章</h1><p>雨巷中的<strong>正文</strong>与<ruby>书<rt>shū</rt></ruby>页。</p><p>第二段。</p></body></html>""",
            )) {
                val bytes = text.toByteArray()
                val entry = ZipEntry(name)
                if (name == "mimetype") {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = java.util.zip.CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
            }
        }
        return file
    }

    private fun deliver(file: File, action: String) {
        val previousResults = imports.state.results.size
        val uri = FileProvider.getUriForFile(application, "${application.packageName}.publications", file)
        val intent = Intent(composeRule.activity, MainActivity::class.java).apply {
            component = android.content.ComponentName(application.packageName, MainActivity::class.java.name)
            this.action = action
            setDataAndType(uri, if (file.name.endsWith(".txt")) "text/plain" else "application/octet-stream")
            if (action == Intent.ACTION_SEND) putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("book", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        composeRule.activity.startActivity(intent)
        composeRule.waitUntil(15_000) { imports.state.review != null || imports.state.results.size != previousResults }
        assertNotNull("Import ${file.name} ended before confirmation: ${imports.state.status}; ${imports.state.results.lastOrNull()?.message}", imports.state.review)
    }

    private fun confirm() {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("确认导入"))
        composeRule.onNodeWithText("确认导入").performClick()
        composeRule.waitUntil(15_000) { imports.state.review == null && !imports.state.busy }
        checkNotNull(imports.state.results.last().publicationId) { imports.state.results.last().message }
    }

    private fun newestBook(): BookIdentity.Local = runBlocking {
        val identity = application.libraryRepository.localPublications().map { it.identity as BookIdentity.Local }
            .single { it !in baseline && it !in owned }
        owned += identity
        identity
    }
    private fun openLatestResult() {
        val tag = "local-book-import-result-${imports.state.results.last().resultId}"
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performClick()
        try {
            composeRule.waitUntil(15_000) { composeRule.onAllNodes(readingAction).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            val reader = ViewModelProvider(composeRule.activity)[LocalBookFlow::class.java]
            throw AssertionError("Open ${imports.state.results.last().publicationId}: identity=${reader.identity}; " +
                "publication=${reader.publication?.publicationId}; loading=${reader.loading}; failure=${reader.failure}", error)
        }
    }
    private fun startReading(visibleText: String = "雨巷") {
        composeRule.onNode(readingAction).performClick()
        waitFor(visibleText, substring = true)
    }
    private fun exitReaderToDetail() {
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitFor("书籍详情")
    }

    private fun exitReaderToImport() {
        exitReaderToDetail()
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitFor("导入本地书籍")
        val flow = ViewModelProvider(composeRule.activity)[LocalBookFlow::class.java]
        composeRule.waitUntil(15_000) { flow.admittedRouteEntryId == null && flow.publication == null }
    }

    private fun back() { composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }; composeRule.waitForIdle() }
    private fun waitFor(text: String, substring: Boolean = false, alternative: String? = null) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() ||
                alternative?.let { composeRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() } == true
        }
    }
}
