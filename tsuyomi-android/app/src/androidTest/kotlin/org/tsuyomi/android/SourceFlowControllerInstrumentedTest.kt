/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.core.database.TsuyomiDatabase
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.feature.browse.BrowseUiState
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddOutcome
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

@RunWith(AndroidJUnit4::class)
class SourceFlowControllerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: TsuyomiDatabase
    private lateinit var library: RoomLibraryRepository

    @Before
    fun setUp() {
        cleanFixtureState()
        database = Room.inMemoryDatabaseBuilder(context, TsuyomiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = RoomLibraryRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        cleanFixtureState()
    }

    @Test
    fun signedFixturePullAndDirectAddCompleteEndToEnd() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val controller = controller()
        try {
            val pull = controller.pullRemoteLibrary(packageInfo, FIXED_TIME)
            assertEquals(RemoteLibraryPullResult.Success(total = 2, newlyAdded = 2), pull)
            assertEquals(setOf("1234", "5678"), library.libraryEntries().map { it.book.identity.remoteBookId }.toSet())

            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            val selected = summary(sourceId, "9999", "远程直加测试")
            controller.selectBook(selected)

            assertEquals(RemoteAddUiResult.Confirmed, controller.addSelectedBook(FIXED_TIME.plusSeconds(1)))
            val entry = requireNotNull(library.libraryEntries().firstOrNull { it.book.identity == selected.identity })
            assertEquals(RemoteReconciliationState.CONFIRMED, entry.reconciliation)
        } finally {
            controller.close()
        }
    }

    @Test
    fun packageOwnerChangeDuringPaginationRejectsWholeMerge() = runBlocking {
        val packageInfo = installFixture()
        val secondPageStarted = CompletableDeferred<Unit>()
        val releaseSecondPage = CompletableDeferred<Unit>()
        val firstSession = FakeSession(
            listRemote = { cursor ->
                if (cursor == null) {
                    RemoteLibraryPage(listOf(summary(packageInfo.manifest.sourceId.value, "1001", "第一页")), "page-2", false)
                } else {
                    secondPageStarted.complete(Unit)
                    releaseSecondPage.await()
                    RemoteLibraryPage(listOf(summary(packageInfo.manifest.sourceId.value, "1002", "第二页")), null, true)
                }
            },
        )
        val replacementSession = FakeSession()
        val controller = controller { candidate ->
            if (candidate.packageSha256 == packageInfo.packageSha256) firstSession else replacementSession
        }
        try {
            val pull = async { controller.pullRemoteLibrary(packageInfo, FIXED_TIME) }
            withTimeout(5_000) { secondPageStarted.await() }
            controller.open(packageInfo.withPackageSha256(alternateSha(packageInfo.packageSha256)))
            releaseSecondPage.complete(Unit)

            assertEquals(RemoteLibraryPullResult.Failure("source-changed"), pull.await())
            assertTrue(library.libraryEntries().isEmpty())
        } finally {
            releaseSecondPage.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun sourceRevocationBeforeFinalMergeRejectsWholeMerge() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val secondPageStarted = CompletableDeferred<Unit>()
        val releaseSecondPage = CompletableDeferred<Unit>()
        val session = FakeSession(
            listRemote = { cursor ->
                if (cursor == null) {
                    RemoteLibraryPage(listOf(summary(sourceId, "2001", "第一页")), "page-2", false)
                } else {
                    secondPageStarted.complete(Unit)
                    releaseSecondPage.await()
                    RemoteLibraryPage(listOf(summary(sourceId, "2002", "第二页")), null, true)
                }
            },
        )
        val controller = controller { session }
        try {
            val pull = async { controller.pullRemoteLibrary(packageInfo, FIXED_TIME) }
            withTimeout(5_000) { secondPageStarted.await() }
            val availability = requireNotNull(library.sourceAvailability(sourceId))
            library.setSourceAvailability(sourceId, availability.verifiedVersion, false, availability.generation + 1)
            releaseSecondPage.complete(Unit)

            assertEquals(RemoteLibraryPullResult.Failure("source-changed"), pull.await())
            assertTrue(library.libraryEntries().isEmpty())
        } finally {
            releaseSecondPage.complete(Unit)
            controller.close()
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
                DirectActionTokenRegistry.process.accept(sourceId, remoteBookId, token)
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

            val add = async { controller.addSelectedBook(FIXED_TIME) }
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
                DirectActionTokenRegistry.process.accept(sourceId, remoteBookId, token)
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

            val add = async { controller.addSelectedBook(FIXED_TIME) }
            withTimeout(5_000) { accepted.await() }
            controller.selectBook(secondBook)
            releaseResponse.complete(Unit)

            assertEquals(RemoteAddUiResult.Confirmed, add.await())
            assertEquals(secondBook.identity, controller.selectedBook?.identity)
            assertFalse(controller.selectedBookInLibrary)
            assertEquals(null, controller.selectedBookReconciliation)
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
                DirectActionTokenRegistry.process.accept(sourceId, remoteBookId, token)
                RemoteLibraryAddResult(selected.identity, RemoteLibraryAddOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(selected)

            val add = async { controller.addSelectedBook(FIXED_TIME) }
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
                DirectActionTokenRegistry.process.accept(sourceId, remoteBookId, token)
                RemoteLibraryAddResult(selected.identity, RemoteLibraryAddOutcome.APPLIED)
            },
        )
        val controller = controller { session }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(selected)

            val add = async { controller.addSelectedBook(FIXED_TIME) }
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
    fun localOnlyFallbackClearsStaleDetailWritebackState() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        putCredential(sourceId)
        val selected = summary(sourceId, "4004", "凭证消失")
        val controller = controller { FakeSession() }
        try {
            val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
            assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
            controller.open(packageInfo)
            controller.selectBook(selected)
            assertTrue(controller.selectedBookAddWritesRemote)

            File(context.noBackupFilesDir, "source-credentials").deleteRecursively()

            assertEquals(RemoteAddUiResult.LocalOnly, controller.addSelectedBook(FIXED_TIME))
            assertFalse(controller.selectedBookAddWritesRemote)
            assertTrue(controller.removeSelectedBook())
            assertFalse(controller.selectedBookAddWritesRemote)
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
                DirectActionTokenRegistry.process.accept(sourceId, remoteBookId, token)
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

            val first = async { controller.addSelectedBook(FIXED_TIME) }
            withTimeout(5_000) { transportEntered.await() }
            val second = async { controller.addSelectedBook(FIXED_TIME.plusSeconds(1)) }
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

            val add = async { controller.addSelectedBook(FIXED_TIME) }
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
            assertEquals(RemoteLibraryPullResult.Success(1, 1), controller.pullRemoteLibrary(packageInfo, FIXED_TIME))
            assertEquals(1, controller.remoteLibraryBooks.size)

            controller.open(packageInfo.withPackageSha256(alternateSha(packageInfo.packageSha256)))

            assertTrue(controller.remoteLibraryBooks.isEmpty())
            assertEquals(null, controller.selectedBook)
            assertEquals(null, controller.selectedBookReconciliation)
        } finally {
            controller.close()
        }
    }

    @Test
    fun remoteWritebackRequiresCredentialAndFailsClosedWhenCredentialDisappears() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val install = SourceInstallController(context, library)
        install.restoreInstalled()

        assertFalse(install.remoteAddCredentialReady())
        assertFalse(install.setRemoteAddWritebackEnabled(true))
        putCredential(sourceId)
        assertTrue(install.remoteAddCredentialReady())
        assertTrue(install.setRemoteAddWritebackEnabled(true))

        File(context.noBackupFilesDir, "source-credentials").deleteRecursively()
        assertFalse(requireNotNull(install.remotePolicy()).addWritebackEnabled)
        assertFalse(requireNotNull(library.sourceRemotePolicy(sourceId)).addWritebackEnabled)
        assertEquals(packageInfo.packageSha256, install.activePackage?.packageSha256)
    }

    @Test
    fun invalidInstalledPackageIsMarkedUnavailableDuringRestore() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val before = requireNotNull(library.sourceAvailability(sourceId))
        File(context.noBackupFilesDir, "extensions/active/$sourceId.hxp").writeText("tampered")

        val restore = SourceInstallController(context, library)
        restore.restoreInstalled()

        assertTrue(restore.state is BrowseUiState.Failure)
        assertEquals(null, restore.activePackage)
        val after = requireNotNull(library.sourceAvailability(sourceId))
        assertFalse(after.available)
        assertEquals(before.generation + 1, after.generation)
    }

    private fun controller(
        openSession: (suspend (VerifiedHxpPackage) -> SourceFlowSession)? = null,
    ): SourceFlowController {
        val snapshotStore = SourceFlowSnapshotStore((context.applicationContext as TsuyomiApplication).preferencesDataStore)
        return if (openSession == null) {
            SourceFlowController(context, library, snapshotStore)
        } else {
            SourceFlowController(context, library, snapshotStore, openSession)
        }
    }

    private suspend fun installFixture(): VerifiedHxpPackage {
        val fixture = File(context.cacheDir, "wenku8-fixture.hxp")
        context.assets.open("wenku8-fixture.hxp").use { input ->
            fixture.outputStream().use(input::copyTo)
        }
        val install = SourceInstallController(context, library)
        install.prepare(Uri.fromFile(fixture), context.contentResolver)
        check(install.state is BrowseUiState.Approval)
        install.approve(allowDowngrade = false)
        check(install.state is BrowseUiState.Installed)
        return requireNotNull(install.activePackage)
    }

    private fun cleanFixtureState() {
        File(context.noBackupFilesDir, "extensions").deleteRecursively()
        File(context.cacheDir, "hxp-staging").deleteRecursively()
        File(context.cacheDir, "source-network-cache").deleteRecursively()
        File(context.noBackupFilesDir, "source-credentials").deleteRecursively()
        File(context.cacheDir, "wenku8-fixture.hxp").delete()
    }

    private fun summary(sourceId: String, remoteBookId: String, title: String) = SourceBookSummary(
        identity = BookIdentity(sourceId, remoteBookId),
        title = title,
        author = "测试作者",
        coverUrl = null,
        canonicalUrl = "https://www.wenku8.net/book/$remoteBookId.htm",
    )

    private fun putCredential(sourceId: String) {
        SourceCredentialStore(context).put(
            SourceCredentialPartition(sourceId, HttpsOrigin("https://www.wenku8.net")),
            "fixture_session=accepted".encodeToByteArray(),
        )
    }

    private fun VerifiedHxpPackage.withPackageSha256(value: String) = VerifiedHxpPackage(
        manifest = manifest,
        packageSha256 = value,
        publisherFingerprint = publisherFingerprint,
        archiveBytes = archiveBytes,
        entryModuleBytes = readVerifiedEntryModule(),
    )

    private fun alternateSha(current: String): String = when (current.first()) {
        'a' -> "b" + current.drop(1)
        else -> "a" + current.drop(1)
    }

    private class FakeSession(
        private val listRemote: suspend (String?) -> RemoteLibraryPage = { error("Unexpected remote list") },
        private val detail: suspend (SourceBookSummary) -> SourceBookDetail = { SourceBookDetail(it, null, emptyList(), null) },
        private val addRemote: suspend (String, String) -> RemoteLibraryAddResult = { _, _ -> error("Unexpected remote add") },
    ) : SourceFlowSession {
        override suspend fun search(query: String, page: Int, offlineOnly: Boolean): List<SourceBookSummary> =
            error("Unexpected search")

        override suspend fun detail(remoteBookId: String, offlineOnly: Boolean): SourceBookDetail = detail(
            SourceBookSummary(
                identity = BookIdentity(SOURCE_ID, remoteBookId),
                title = "详情",
                author = "测试作者",
                coverUrl = null,
                canonicalUrl = "https://www.wenku8.net/book/$remoteBookId.htm",
            ),
        )

        override suspend fun directory(remoteBookId: String, offlineOnly: Boolean): SourceDirectory =
            error("Unexpected directory")

        override suspend fun chapter(
            chapter: SourceChapter,
            remoteBookId: String,
            offlineOnly: Boolean,
        ): ReaderDocument = error("Unexpected chapter")

        override suspend fun listRemoteLibrary(cursor: String?): RemoteLibraryPage = listRemote(cursor)

        override suspend fun addRemoteLibrary(remoteBookId: String, directActionToken: String): RemoteLibraryAddResult =
            addRemote(remoteBookId, directActionToken)

        override fun close() = Unit
    }

    private companion object {
        const val SOURCE_ID = "org.tsuyomi.wenku8"
        val FIXED_TIME: Instant = Instant.parse("2026-08-09T00:00:00Z")
    }
}
