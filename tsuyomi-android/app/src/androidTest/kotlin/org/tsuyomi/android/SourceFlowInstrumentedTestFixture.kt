/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import org.junit.After
import org.junit.Before
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.database.TsuyomiDatabase
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.feature.browse.BrowseUiState
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

internal const val SOURCE_FLOW_TEST_SOURCE_ID = "org.tsuyomi.wenku8"
internal val SOURCE_FLOW_TEST_TIME: Instant = Instant.parse("2026-08-09T00:00:00Z")

internal abstract class SourceFlowInstrumentedTestFixture {
    protected val context = InstrumentationRegistry.getInstrumentation().targetContext
    protected lateinit var database: TsuyomiDatabase
    protected lateinit var library: RoomLibraryRepository
    protected lateinit var directActionTokens: DirectActionTokenRegistry

    @Before
    fun setUpSourceFlowFixture() {
        cleanFixtureState()
        database = Room.inMemoryDatabaseBuilder(context, TsuyomiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = RoomLibraryRepository(database)
        directActionTokens = DirectActionTokenRegistry()
    }

    @After
    fun tearDownSourceFlowFixture() {
        database.close()
        cleanFixtureState()
    }

    protected fun controller(
        openSession: (suspend (VerifiedHxpPackage) -> SourceFlowSession)? = null,
    ): SourceFlowController {
        val snapshotStore = SourceFlowSnapshotStore((context.applicationContext as TsuyomiApplication).preferencesDataStore)
        return if (openSession == null) {
            SourceFlowController(
                context = context,
                library = library,
                snapshotStore = snapshotStore,
                directActionTokens = directActionTokens,
            )
        } else {
            SourceFlowController(
                context = context,
                library = library,
                snapshotStore = snapshotStore,
                directActionTokens = directActionTokens,
                openSession = openSession,
            )
        }
    }

    protected suspend fun installFixture(): VerifiedHxpPackage {
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

    protected fun reconciliationState(id: String): String =
        database.openHelper.readableDatabase.query(
            "SELECT state FROM remote_library_reconciliation WHERE id = ?",
            arrayOf(id),
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Missing reconciliation $id" }
            cursor.getString(0)
        }

    protected fun summary(sourceId: String, remoteBookId: String, title: String) = SourceBookSummary(
        identity = BookIdentity(sourceId, remoteBookId),
        title = title,
        author = "测试作者",
        coverUrl = null,
        canonicalUrl = "https://www.wenku8.net/book/$remoteBookId.htm",
    )

    protected fun putCredential(sourceId: String) {
        SourceCredentialStore(context).put(
            SourceCredentialPartition(sourceId, HttpsOrigin("https://www.wenku8.net")),
            "fixture_session=accepted".encodeToByteArray(),
        )
    }

    protected fun VerifiedHxpPackage.withPackageSha256(value: String) = VerifiedHxpPackage(
        manifest = manifest,
        packageSha256 = value,
        publisherFingerprint = publisherFingerprint,
        archiveBytes = archiveBytes,
        entryModuleBytes = readVerifiedEntryModule(),
    )

    protected fun alternateSha(current: String): String = when (current.first()) {
        'a' -> "b" + current.drop(1)
        else -> "a" + current.drop(1)
    }

    protected class FakeSession(
        private val listRemote: suspend (String?) -> RemoteLibraryPage = { error("Unexpected remote list") },
        private val detail: suspend (SourceBookSummary) -> SourceBookDetail = { SourceBookDetail(it, null, emptyList(), null) },
        private val addRemote: suspend (String, String) -> RemoteLibraryAddResult = { _, _ -> error("Unexpected remote add") },
    ) : SourceFlowSession {
        override suspend fun search(query: String, page: Int, offlineOnly: Boolean): List<SourceBookSummary> =
            error("Unexpected search")

        override suspend fun detail(remoteBookId: String, offlineOnly: Boolean): SourceBookDetail = detail(
            SourceBookSummary(
                identity = BookIdentity(SOURCE_FLOW_TEST_SOURCE_ID, remoteBookId),
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

    private fun cleanFixtureState() {
        File(context.noBackupFilesDir, "extensions").deleteRecursively()
        File(context.cacheDir, "hxp-staging").deleteRecursively()
        File(context.cacheDir, "source-network-cache").deleteRecursively()
        File(context.noBackupFilesDir, "source-credentials").deleteRecursively()
        File(context.cacheDir, "wenku8-fixture.hxp").delete()
    }
}
