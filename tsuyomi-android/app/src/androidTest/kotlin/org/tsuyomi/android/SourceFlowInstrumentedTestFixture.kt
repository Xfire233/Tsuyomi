/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Base64
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import java.time.Instant
import org.junit.After
import org.junit.Before
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.database.TsuyomiDatabase
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.VerifiedBrowserSession
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.feature.browse.BrowseUiState
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryRemoveResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryMoveResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryTargetsResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.shared.sourcecontract.SourceHomePage
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
        check(install.state is BrowseUiState.Approval) { "Unexpected fixture preparation state: ${install.state}" }
        install.approve(allowDowngrade = false)
        check(install.state is BrowseUiState.Installed)
        return requireNotNull(install.activePackage)
    }

    protected suspend fun installSignedSwitchOverlay(
        installer: SourceInstallController,
        overlayName: String,
    ): VerifiedHxpPackage {
        val archive = assembleSignedSwitchOverlay(overlayName)
        try {
            installer.prepare(Uri.fromFile(archive), context.contentResolver)
            check(installer.state is BrowseUiState.Approval) {
                "Signed source-switch overlay was not prepared: ${installer.state}"
            }
            installer.approve(allowDowngrade = false)
            return requireNotNull(installer.activePackage) { "Signed source-switch overlay was not activated" }
        } finally {
            archive.delete()
        }
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
        VerifiedBrowserSessionStore(context).put(
            SourceCredentialPartition(sourceId, HttpsOrigin("https://www.wenku8.net")),
            VerifiedBrowserSession("fixture_session=accepted", "fixture-webview-agent/1"),
        )
    }

    protected fun VerifiedHxpPackage.withPackageSha256(value: String) = VerifiedHxpPackage(
        manifest = manifest,
        packageSha256 = value,
        publisherFingerprint = publisherFingerprint,
        publisherTrust = publisherTrust,
        archiveBytes = archiveBytes,
        entryModuleBytes = readVerifiedEntryModule(),
    )

    protected fun assembleSignedSwitchOverlay(overlayName: String): File {
        require(overlayName in setOf("source-switch-home", "source-switch-search", "source-user-consent"))
        val overlay = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("repository/$overlayName.json")
                .bufferedReader()
                .use { it.readText() },
        )
        check(overlay.getString("format") == "tsuyomi-test-source-switch-overlay")
        check(overlay.getBoolean("testOnly"))
        val manifest = overlay.getJSONObject("manifest")
        val replacement = overlay.getJSONObject("entrySourceIdReplacement")
        val fromSourceId = replacement.getString("from")
        val toSourceId = replacement.getString("to")
        check(toSourceId == overlay.getString("sourceId"))
        check(toSourceId == manifest.getString("id"))
        check(overlay.getString("displayName") == manifest.getJSONObject("display").getString("name"))
        val declaredHome = manifest.getJSONObject("capabilities").optJSONObject("home")
            ?.optBoolean("enabled", false) ?: false
        check(overlay.getBoolean("homeAvailable") == declaredHome)
        val expectedEntrySha256 = overlay.getJSONObject("provenance")
            .getJSONObject("entryReplacement")
            .getString("sha256")
        val entryPath = manifest.getString("entry")
        val archive = File(context.cacheDir, "$overlayName-assembled.hxp")
        ZipInputStream(context.assets.open("wenku8-fixture.hxp")).use { input ->
            ZipOutputStream(archive.outputStream()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val bytes = when (entry.name) {
                        "manifest.json" -> manifest.toString().toByteArray(Charsets.UTF_8)
                        "signature.ed25519" -> Base64.getDecoder().decode(overlay.getString("signature"))
                        entryPath -> replaceEntrySourceId(
                            entry = input.readBytes(),
                            fromSourceId = fromSourceId,
                            toSourceId = toSourceId,
                            expectedSha256 = expectedEntrySha256,
                        )
                        else -> input.readBytes()
                    }
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        return archive
    }

    protected fun assemblePendingSourceApproval(): File {
        val candidate = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("repository/replacement-candidate.json")
                .bufferedReader()
                .use { it.readText() },
        )
        val archive = File(context.cacheDir, "source-switch-pending-approval.hxp")
        ZipInputStream(context.assets.open("wenku8-fixture.hxp")).use { input ->
            ZipOutputStream(archive.outputStream()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val bytes = when (entry.name) {
                        "manifest.json" -> candidate.getJSONObject("manifest").toString().toByteArray(Charsets.UTF_8)
                        "signature.ed25519" -> Base64.getDecoder().decode(candidate.getString("signature"))
                        else -> input.readBytes()
                    }
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        return archive
    }

    private fun replaceEntrySourceId(
        entry: ByteArray,
        fromSourceId: String,
        toSourceId: String,
        expectedSha256: String,
    ): ByteArray {
        val source = entry.toString(Charsets.UTF_8)
        val expected = "const SOURCE_ID = '$fromSourceId';"
        val index = source.indexOf(expected)
        check(index >= 0 && source.indexOf(expected, index + expected.length) < 0) {
            "Pinned entry must contain exactly one source identity declaration"
        }
        return source.replaceRange(index, index + expected.length, "const SOURCE_ID = '$toSourceId';")
            .toByteArray(Charsets.UTF_8)
            .also { replaced ->
                val actualSha256 = MessageDigest.getInstance("SHA-256").digest(replaced)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                check(actualSha256 == expectedSha256) { "Overlay entry replacement digest mismatch" }
            }
    }


    protected fun alternateSha(current: String): String = when (current.first()) {
        'a' -> "b" + current.drop(1)
        else -> "a" + current.drop(1)
    }

    protected class FakeSession(
        private val searchResult: suspend (String, Boolean) -> List<SourceBookSummary> = { _, _ -> error("Unexpected search") },
        private val authorSearchResult: suspend (String, Boolean) -> List<SourceBookSummary> = { _, _ ->
            throw SourceException(
                code = SourceErrorCode.EXTENSION_RUNTIME_FAILURE,
                diagnostic = SourceDiagnostic(
                    correlationId = "author-search-unavailable",
                    stage = "author-search",
                    safeCode = "author-search-unavailable",
                ),
            )
        },
        private val authorSearchRequestUrl: suspend (String) -> String = { error("Unexpected author search URL") },
        private val authorSearchVerifiedPage: suspend (String, CapturedVerifiedPage) -> List<SourceBookSummary> = { _, _ -> error("Unexpected author verified-page search") },
        private val homeResult: suspend (Map<String, String>, String?, Boolean) -> SourceHomePage = { _, _, _ ->
            error("Unexpected source Home")
        },
        private val listRemote: suspend (String?) -> RemoteLibraryPage = { error("Unexpected remote list") },
        private val detail: suspend (SourceBookSummary) -> SourceBookDetail = { SourceBookDetail(it, null, emptyList(), null) },
        private val directoryResult: suspend (String) -> SourceDirectory = { error("Unexpected directory") },
        private val chapterResult: suspend (SourceChapter, String) -> ReaderDocument = { _, _ -> error("Unexpected chapter") },
        private val addRemote: suspend (String, String) -> RemoteLibraryAddResult = { _, _ -> error("Unexpected remote add") },
        private val removeRemote: suspend (String, String) -> RemoteLibraryRemoveResult = { _, _ -> error("Unexpected remote remove") },
        private val moveRemote: suspend (String, String, String) -> RemoteLibraryMoveResult = { _, _, _ -> error("Unexpected remote move") },
        private val targetsResult: suspend () -> RemoteLibraryTargetsResult = { RemoteLibraryTargetsResult(SOURCE_FLOW_TEST_SOURCE_ID, emptyList()) },
    ) : SourceFlowSession {
        override suspend fun search(query: String, page: Int, offlineOnly: Boolean): List<SourceBookSummary> =
            searchResult(query, offlineOnly)

        override suspend fun authorSearch(author: String, page: Int, offlineOnly: Boolean): List<SourceBookSummary> =
            authorSearchResult(author, offlineOnly)

        override suspend fun authorSearchRequestUrl(author: String, page: Int): String = authorSearchRequestUrl.invoke(author)

        override suspend fun authorSearchVerifiedPage(
            author: String,
            snapshot: CapturedVerifiedPage,
            page: Int,
        ): List<SourceBookSummary> = authorSearchVerifiedPage.invoke(author, snapshot)

        override suspend fun home(
            selectedFilters: Map<String, String>,
            cursor: String?,
            offlineOnly: Boolean,
        ): SourceHomePage = homeResult(selectedFilters, cursor, offlineOnly)

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
            directoryResult(remoteBookId)

        override suspend fun chapter(
            chapter: SourceChapter,
            remoteBookId: String,
            offlineOnly: Boolean,
        ): ReaderDocument = chapterResult(chapter, remoteBookId)

        override suspend fun listRemoteLibrary(cursor: String?): RemoteLibraryPage = listRemote(cursor)

        override suspend fun addRemoteLibrary(remoteBookId: String, directActionToken: String): RemoteLibraryAddResult =
            addRemote(remoteBookId, directActionToken)

        override suspend fun removeRemoteLibrary(remoteBookId: String, directActionToken: String): RemoteLibraryRemoveResult =
            removeRemote(remoteBookId, directActionToken)

        override suspend fun moveRemoteLibrary(remoteBookId: String, targetId: String, directActionToken: String): RemoteLibraryMoveResult =
            moveRemote(remoteBookId, targetId, directActionToken)

        override suspend fun listRemoteTargets(): RemoteLibraryTargetsResult = targetsResult()

        override fun close() = Unit
    }

    private fun cleanFixtureState() {
        File(context.noBackupFilesDir, "extensions").deleteRecursively()
        File(context.cacheDir, "hxp-staging").deleteRecursively()
        File(context.cacheDir, "source-network-cache").deleteRecursively()
        File(context.noBackupFilesDir, "source-credentials").deleteRecursively()
        val application = context.applicationContext as TsuyomiApplication
        File(context.noBackupFilesDir, "package-trust").resetDirectory()
        File(context.noBackupFilesDir, "repository-subscriptions").resetDirectory()
        application.packageTrust.reload()
        application.repositorySubscriptions.reload()
        File(context.cacheDir, "source-switch-home-assembled.hxp").delete()
        File(context.cacheDir, "source-switch-search-assembled.hxp").delete()
        File(context.cacheDir, "source-switch-pending-approval.hxp").delete()
        File(context.cacheDir, "source-user-consent-assembled.hxp").delete()
        File(context.cacheDir, "wenku8-fixture.hxp").delete()
    }

    private fun File.resetDirectory() {
        deleteRecursively()
        check(mkdirs() || isDirectory) { "Cannot reset fixture directory: $name" }
    }
}
