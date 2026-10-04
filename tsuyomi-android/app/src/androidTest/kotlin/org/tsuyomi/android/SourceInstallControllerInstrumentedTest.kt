/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import java.io.File
import kotlinx.coroutines.runBlocking
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.erdtman.jcs.JsonCanonicalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.tsuyomi.feature.browse.BrowseUiState
import org.tsuyomi.feature.browse.BrowseCatalogAction
import org.tsuyomi.feature.browse.BrowseInstallFailure
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.source.extensionmanager.OfficialRepositoryClient
import org.tsuyomi.source.extensionmanager.PublisherKey
import org.tsuyomi.source.extensionmanager.PublisherTrust
import org.tsuyomi.source.extensionmanager.RepositoryFetcher
import org.tsuyomi.source.extensionmanager.PackageTrustRegistry
import org.tsuyomi.source.extensionmanager.RepositoryFetchError
import org.tsuyomi.source.extensionmanager.RepositoryFetchException
import org.tsuyomi.source.extensionmanager.RepositorySubscriptionRegistry
import org.tsuyomi.source.extensionmanager.RepositoryRoot
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.json.JSONArray

@RunWith(AndroidJUnit4::class)
internal class SourceInstallControllerInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun approvalForAnotherArchiveCannotActivateTheCurrentCandidate() = runBlocking {
        val active = installFixture()
        val install = SourceInstallController(context, library)
        install.restoreInstalled()
        val archive = assemblePendingSourceApproval()
        try {
            install.prepare(Uri.fromFile(archive), context.contentResolver)
            assertTrue(install.state is BrowseUiState.Approval)
            install.approve(false, expectedPackageSha256 = active.packageSha256)
            assertEquals(active.packageSha256, install.activePackage?.packageSha256)
            assertTrue(install.state is BrowseUiState.Failure)
            install.prepare(Uri.fromFile(archive), context.contentResolver)
            val shown = install.state as BrowseUiState.Approval
            install.approve(false, expectedPackageSha256 = shown.packageSha256)
            assertEquals(shown.packageSha256, install.activePackage?.packageSha256)
        } finally {
            archive.delete()
        }
    }

    @Test
    fun unknownLocalPublisherRequiresVerifiedKeyAndExplicitConsentBeforeDurableExecution() = runBlocking {
        val application = context.applicationContext as TsuyomiApplication
        val trustDirectory = File(context.noBackupFilesDir, "package-trust")
        trustDirectory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        application.packageTrust.reload()
        val descriptor = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("repository/source-user-consent.json").bufferedReader().use { it.readText() })
        val publicKey = descriptor.getJSONObject("provenance").getJSONObject("publisher").getString("publicKeyBase64")
        val archive = assembleSignedSwitchOverlay("source-user-consent")
        try {
            val install = SourceInstallController(context, library)
            install.prepare(Uri.fromFile(archive), context.contentResolver)
            assertTrue(install.state is BrowseUiState.PublisherKeyRequired)
            assertEquals(null, install.activePackage)
            install.dismissApproval()
            assertTrue(install.installedPackages.isEmpty())
            install.prepare(Uri.fromFile(archive), context.contentResolver)
            install.providePublisherKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            assertTrue(install.state is BrowseUiState.Failure)
            assertEquals(null, install.activePackage)
            assertEquals(null, application.packageTrust.publisherKeys.resolve("tsuyomi-user-consent-test"))
            install.dismissFailure()
            install.prepare(Uri.fromFile(archive), context.contentResolver)
            install.providePublisherKey(publicKey)
            assertTrue((install.state as BrowseUiState.Approval).requiresNonOfficialConsent)
            install.approve(allowDowngrade = false)
            assertTrue(install.state is BrowseUiState.Approval)
            assertEquals(null, install.activePackage)
            install.dismissApproval()
            assertEquals(null, application.packageTrust.publisherKeys.resolve("tsuyomi-user-consent-test"))
            install.prepare(Uri.fromFile(archive), context.contentResolver)
            install.providePublisherKey(publicKey)
            val occupied = List(16) { File(context.noBackupFilesDir, "extensions/occupied-$it") }
            try {
                occupied.forEach { it.writeText("occupied") }
                install.approve(allowDowngrade = false, allowNonOfficial = true)
                assertEquals(BrowseInstallFailure.STORAGE, (install.state as BrowseUiState.Failure).reason)
                assertEquals(null, install.activePackage)
                assertEquals(null, application.packageTrust.publisherKeys.resolve("tsuyomi-user-consent-test"))
                val afterFailure = PackageTrustRegistry(trustDirectory)
                assertEquals(null, afterFailure.publisherKeys.resolve("tsuyomi-user-consent-test"))
                val candidate = org.tsuyomi.source.extensionmanager.HxpArchiveVerifier(
                    org.tsuyomi.source.extensionmanager.InMemoryPublisherKeyStore(listOf(PublisherKey(
                        "tsuyomi-user-consent-test", Base64.getDecoder().decode(publicKey), PublisherTrust.USER_ADDED,
                    ))),
                ).verify(archive)
                assertFalse(afterFailure.isApproved(candidate))
                val retry = SourceInstallController(context, library, packageTrust = afterFailure)
                retry.restoreInstalled()
                assertEquals(null, retry.activePackage)
                retry.prepare(Uri.fromFile(archive), context.contentResolver)
                assertTrue(retry.state is BrowseUiState.PublisherKeyRequired)
                retry.dismissApproval()
            } finally {
                occupied.forEach { it.delete() }
            }
            install.dismissFailure()
            install.prepare(Uri.fromFile(archive), context.contentResolver)
            assertTrue(install.state is BrowseUiState.PublisherKeyRequired)
            install.providePublisherKey(publicKey)
            install.approve(allowDowngrade = false, allowNonOfficial = true)
            val active = requireNotNull(install.activePackage)
            assertTrue(application.packageTrust.isApproved(active))
            val restoredTrust = org.tsuyomi.source.extensionmanager.PackageTrustRegistry(trustDirectory)
            assertTrue(restoredTrust.isApproved(active))
            val restored = SourceInstallController(context, library)
            restored.restoreInstalled()
            assertEquals(active.packageSha256, restored.activePackage?.packageSha256)
            assertTrue(restored.uninstall(active.manifest.sourceId.value))
            restored.prepare(Uri.fromFile(archive), context.contentResolver)
            assertTrue(restored.state is BrowseUiState.Approval)
            restored.approve(allowDowngrade = false, allowNonOfficial = true)
            assertEquals(active.packageSha256, restored.activePackage?.packageSha256)
        } finally {
            archive.delete()
        }
    }

    @Test
    fun unsignedLocalImportRequiresFreshConsentForEveryExactArchive() = runBlocking {
        val first = assembleUnsignedCandidate("9.9.8")
        val changed = assembleUnsignedCandidate("9.9.9")
        try {
            val install = SourceInstallController(context, library)
            install.prepare(Uri.fromFile(first), context.contentResolver)
            val approval = install.state as BrowseUiState.Approval
            assertEquals(org.tsuyomi.feature.browse.BrowsePublisherIdentity.LOCAL_UNSIGNED, approval.publisherIdentity)
            assertFalse(approval.requiresUnsignedIdentityTransition)
            assertEquals(null, install.activePackage)
            install.approve(false, expectedPackageSha256 = approval.packageSha256)
            assertTrue(install.state is BrowseUiState.Approval)
            assertEquals(null, install.activePackage)
            install.dismissApproval()
            assertFalse((context.applicationContext as TsuyomiApplication).packageTrust
                .isApproved(org.tsuyomi.source.extensionmanager.HxpArchiveVerifier(
                    OfficialRepositoryConfiguration.publisherKeys(null),
                ).verify(first)))

            install.prepare(Uri.fromFile(first), context.contentResolver)
            install.approve(
                allowDowngrade = false,
                allowUnsignedRisk = true,
                expectedPackageSha256 = (install.state as BrowseUiState.Approval).packageSha256,
            )
            val active = requireNotNull(install.activePackage)
            assertEquals(null, active.publisherFingerprint)
            assertTrue((context.applicationContext as TsuyomiApplication).packageTrust.isApproved(active))
            val restored = SourceInstallController(context, library)
            restored.restoreInstalled()
            assertEquals(active.packageSha256, restored.activePackage?.packageSha256)

            restored.prepare(Uri.fromFile(changed), context.contentResolver)
            val changedApproval = restored.state as BrowseUiState.Approval
            assertFalse(changedApproval.packageSha256 == active.packageSha256)
            restored.approve(false, expectedPackageSha256 = changedApproval.packageSha256)
            assertTrue(restored.state is BrowseUiState.Approval)
            assertEquals(active.packageSha256, restored.activePackage?.packageSha256)
            restored.dismissApproval()
            assertEquals(active.packageSha256, restored.activePackage?.packageSha256)
            val activeArchive = File(context.noBackupFilesDir, "extensions/active/${active.manifest.sourceId.value}.hxp")
            activeArchive.writeBytes(changed.readBytes())
            val uncheckedRestore = SourceInstallController(context, library)
            uncheckedRestore.restoreInstalled()
            assertEquals(null, uncheckedRestore.activePackage)
            assertTrue(uncheckedRestore.state is BrowseUiState.Failure)
            assertFalse(requireNotNull(library.sourceAvailability(active.manifest.sourceId.value)).available)
        } finally {
            first.delete()
            changed.delete()
        }
    }

    @Test
    fun signedSourceCannotBeReplacedByUnsignedFileAndTransitionNeedsSeparateApprovalAfterUninstall() = runBlocking {
        val signed = installFixture()
        val sourceId = signed.manifest.sourceId.value
        val book = summary(sourceId, "transition", "保留书籍")
        library.addToLibrary(org.tsuyomi.shared.librarydomain.LibraryBook(
            identity = book.identity, title = book.title, author = book.author,
            coverUrl = book.coverUrl, canonicalUrl = book.canonicalUrl,
            addedAt = SOURCE_FLOW_TEST_TIME, metadataUpdatedAt = SOURCE_FLOW_TEST_TIME,
        ))
        val progress = org.tsuyomi.shared.librarydomain.ReadingProgress(
            book.identity,
            org.tsuyomi.shared.locator.ReaderLocator(
                document = org.tsuyomi.shared.locator.DocumentIdentity(sourceId, "transition", "chapter-1"),
                blockId = "p1", characterOffset = 7, chapterProgress = 0.4,
                capturedAt = SOURCE_FLOW_TEST_TIME,
            ),
        )
        library.saveProgress(progress)
        putCredential(sourceId)
        val partition = SourceCredentialPartition(sourceId, HttpsOrigin("https://www.wenku8.net"))
        val retainedCredentials = VerifiedBrowserSessionStore(context).getSnapshot(partition)?.session
        val candidate = assembleUnsignedCandidate("9.9.8")
        try {
            val install = SourceInstallController(context, library)
            install.restoreInstalled()
            install.prepare(Uri.fromFile(candidate), context.contentResolver)
            assertTrue(install.state is BrowseUiState.Failure)
            assertEquals(BrowseInstallFailure.IDENTITY_CONFLICT, (install.state as BrowseUiState.Failure).reason)
            assertEquals(signed.packageSha256, install.activePackage?.packageSha256)
            assertTrue(install.uninstall(sourceId))
            assertEquals(null, install.activePackage)
            assertEquals(progress, library.progress(book.identity))
            assertEquals(retainedCredentials, VerifiedBrowserSessionStore(context).getSnapshot(partition)?.session)

            install.prepare(Uri.fromFile(candidate), context.contentResolver)
            val approval = install.state as BrowseUiState.Approval
            assertTrue(approval.requiresUnsignedIdentityTransition)
            install.approve(false, allowUnsignedRisk = true, expectedPackageSha256 = approval.packageSha256)
            assertTrue(install.state is BrowseUiState.Approval)
            assertEquals(null, install.activePackage)
            install.dismissApproval()
            assertEquals(null, install.activePackage)

            install.prepare(Uri.fromFile(candidate), context.contentResolver)
            install.approve(
                allowDowngrade = false,
                allowUnsignedRisk = true,
                allowUnsignedIdentityTransition = true,
                expectedPackageSha256 = (install.state as BrowseUiState.Approval).packageSha256,
            )
            val unsigned = requireNotNull(install.activePackage)
            assertEquals(candidate.readBytes().let(::digest), unsigned.packageSha256)
            assertEquals(null, unsigned.publisherFingerprint)
            assertTrue(requireNotNull(library.sourceAvailability(sourceId)).available)
            assertEquals(progress, library.progress(book.identity))
            assertEquals(retainedCredentials, VerifiedBrowserSessionStore(context).getSnapshot(partition)?.session)
            val restored = SourceInstallController(context, library)
            restored.restoreInstalled()
            assertEquals(unsigned.packageSha256, restored.activePackage?.packageSha256)
            assertTrue(restored.uninstall(sourceId))
            val signedArchive = File.createTempFile("signed-return-", ".hxp", context.cacheDir)
            try {
                context.assets.open("wenku8-fixture.hxp").use { input ->
                    signedArchive.outputStream().use(input::copyTo)
                }
                restored.prepare(Uri.fromFile(signedArchive), context.contentResolver)
                val signedApproval = restored.state as BrowseUiState.Approval
                assertTrue(signedApproval.requiresSignedIdentityTransition)
                restored.approve(false, expectedPackageSha256 = signedApproval.packageSha256)
                assertTrue(restored.state is BrowseUiState.Approval)
                assertEquals(null, restored.activePackage)
                restored.approve(
                    allowDowngrade = false,
                    allowLegacyMigration = true,
                    expectedPackageSha256 = signedApproval.packageSha256,
                )
                assertEquals(signed.packageSha256, restored.activePackage?.packageSha256)
                assertEquals(progress, library.progress(book.identity))
                assertEquals(retainedCredentials, VerifiedBrowserSessionStore(context).getSnapshot(partition)?.session)
            } finally {
                signedArchive.delete()
            }
        } finally {
            candidate.delete()
        }
    }

    @Test
    fun unsignedToUnknownSignedPublisherRetainsKeyOnlyAfterSuccessfulActivation() = runBlocking {
        val application = context.applicationContext as TsuyomiApplication
        val descriptor = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("repository/source-user-consent.json").bufferedReader().use { it.readText() })
        val publisher = descriptor.getJSONObject("provenance").getJSONObject("publisher")
        val keyId = publisher.getString("keyId")
        val publicKey = publisher.getString("publicKeyBase64")
        val sourceId = descriptor.getJSONObject("manifest").getString("id")
        val unsigned = assembleUnsignedCandidate("9.9.8", sourceId)
        val signed = assembleSignedSwitchOverlay("source-user-consent")
        try {
            val install = SourceInstallController(context, library)
            install.prepare(Uri.fromFile(unsigned), context.contentResolver)
            install.approve(false, allowUnsignedRisk = true,
                expectedPackageSha256 = (install.state as BrowseUiState.Approval).packageSha256)
            assertEquals(PublisherTrust.LOCAL_UNSIGNED, requireNotNull(install.activePackage).publisherTrust)
            assertTrue(install.uninstall(sourceId))
            assertEquals(null, application.packageTrust.publisherKeys.resolve(keyId))

            install.prepare(Uri.fromFile(signed), context.contentResolver)
            assertTrue(install.state is BrowseUiState.PublisherKeyRequired)
            install.providePublisherKey(publicKey)
            val declined = install.state as BrowseUiState.Approval
            assertTrue(declined.requiresSignedIdentityTransition)
            install.approve(false, allowNonOfficial = true, expectedPackageSha256 = declined.packageSha256)
            assertTrue(install.state is BrowseUiState.Approval)
            install.dismissApproval()
            assertEquals(null, application.packageTrust.publisherKeys.resolve(keyId))
            assertEquals(null, install.activePackage)

            install.prepare(Uri.fromFile(signed), context.contentResolver)
            install.providePublisherKey(publicKey)
            val approval = install.state as BrowseUiState.Approval
            assertEquals(null, application.packageTrust.publisherKeys.resolve(keyId))
            install.approve(false, allowLegacyMigration = true, allowNonOfficial = true,
                expectedPackageSha256 = approval.packageSha256)
            val active = requireNotNull(install.activePackage)
            assertEquals(digest(signed.readBytes()), active.packageSha256)
            assertEquals(PublisherTrust.USER_ADDED, active.publisherTrust)
            assertEquals(digest(Base64.getDecoder().decode(publicKey)),
                application.packageTrust.publisherKeys.resolve(keyId)?.fingerprint)
            val restored = SourceInstallController(context, library)
            restored.restoreInstalled()
            assertEquals(active.packageSha256, restored.activePackage?.packageSha256)
            assertTrue(requireNotNull(library.sourceAvailability(sourceId)).available)
        } finally {
            unsigned.delete()
            signed.delete()
        }
    }

    @Test
    fun unsignedInstalledSourceSurvivesUnrelatedSubscriptionRevocationAndRemoval() = runBlocking {
        val unsigned = assembleUnsignedCandidate("9.9.8")
        val signed = assembleSignedSwitchOverlay("source-user-consent")
        val directory = File(context.noBackupFilesDir, "unsigned-revocation-scope")
        val descriptor = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("repository/source-user-consent.json").bufferedReader().use { it.readText() })
        val root = Ed25519PrivateKeyParameters(ByteArray(32) { (it + 93).toByte() }, 0)
        val now = Instant.now()
        val revokedDigest = digest(unsigned.readBytes())
        val catalog = signedRepositoryCatalog(signed.readBytes(), descriptor, root, now, listOf(revokedDigest))
        val fetcher = object : RepositoryFetcher {
            override fun fetch(url: String, maxBytes: Int): ByteArray {
                check(url == RETRY_INDEX && catalog.size <= maxBytes)
                return catalog
            }
        }
        val official = OfficialRepositoryClient(RepositoryRoot(
            "org.tsuyomi.official-revocation-scope", "https://official.example/index-v1.json",
            PublisherKey("unsigned-official-root", ByteArray(32) { 42 }, PublisherTrust.BUILT_IN_OFFICIAL),
        ), File(directory, "official"))
        val subscriptionsDirectory = File(directory, "subscriptions")
        val grantsDirectory = File(directory, "grants")
        val subscriptions = RepositorySubscriptionRegistry(subscriptionsDirectory, fetcher) { now }
        val trust = PackageTrustRegistry(grantsDirectory)
        val install = SourceInstallController(context, library, official, subscriptions, trust)
        try {
            install.prepare(Uri.fromFile(unsigned), context.contentResolver)
            install.approve(false, allowUnsignedRisk = true,
                expectedPackageSha256 = (install.state as BrowseUiState.Approval).packageSha256)
            val active = requireNotNull(install.activePackage)
            val link = "$RETRY_INDEX#repositoryId=$RETRY_REPOSITORY&keyId=$RETRY_ROOT_KEY_ID&publicKey=${Base64.getEncoder().encodeToString(root.generatePublicKey().encoded)}"
            requireNotNull(subscriptions.client(subscriptions.add(link).repositoryId)).refresh()
            assertTrue(subscriptions.publisherKeys.isRevokedPackage(revokedDigest))
            install.refreshInstalled()
            assertEquals(active.packageSha256, install.activePackage?.packageSha256)
            assertTrue(requireNotNull(library.sourceAvailability(active.manifest.sourceId.value)).available)
            assertTrue(subscriptions.remove(RETRY_REPOSITORY))

            val retainedSubscriptions = RepositorySubscriptionRegistry(subscriptionsDirectory, fetcher) { now }
            assertTrue(retainedSubscriptions.publisherKeys.isRevokedPackage(revokedDigest))
            val restored = SourceInstallController(context, library, official, retainedSubscriptions, PackageTrustRegistry(grantsDirectory))
            restored.restoreInstalled()
            assertEquals(active.packageSha256, restored.activePackage?.packageSha256)
            assertTrue(requireNotNull(library.sourceAvailability(active.manifest.sourceId.value)).available)
        } finally {
            unsigned.delete()
            signed.delete()
            directory.deleteRecursively()
        }
    }

    @Test
    fun uninstallRetainsLibraryProgressCredentialsAndReinstallRestoresAvailability() = runBlocking(Dispatchers.Main) {
        val installed = installFixture()
        val sourceId = installed.manifest.sourceId.value
        val book = summary(sourceId, "1001", "保留的书籍")
        library.addToLibrary(org.tsuyomi.shared.librarydomain.LibraryBook(identity = book.identity, title = book.title, author = book.author,
        coverUrl = book.coverUrl, canonicalUrl = book.canonicalUrl,
        addedAt = SOURCE_FLOW_TEST_TIME, metadataUpdatedAt = SOURCE_FLOW_TEST_TIME,))
        val progress = org.tsuyomi.shared.librarydomain.ReadingProgress(book.identity,
        org.tsuyomi.shared.locator.ReaderLocator(
            document = org.tsuyomi.shared.locator.DocumentIdentity(sourceId, "1001", "chapter-1"),
            blockId = "p1", characterOffset = 7, chapterProgress = 0.4, capturedAt = SOURCE_FLOW_TEST_TIME,
        ),)
        library.saveProgress(progress)
        putCredential(sourceId)
        val partition = SourceCredentialPartition(sourceId, HttpsOrigin("https://www.wenku8.net"))
        val credentials = VerifiedBrowserSessionStore(context).getSnapshot(partition)
        val before = requireNotNull(library.book(book.identity))
        val install = SourceInstallController(context, library)
        install.restoreInstalled()
        controller { FakeSession(searchResult = { _, _ -> listOf(book) }) }.use { flow ->
            flow.open(installed)
            flow.updateQuery("before")
            flow.search()
            assertTrue(flow.searchState is org.tsuyomi.feature.search.SearchResultState.Results)
            assertTrue(install.uninstall(sourceId) { flow.removeSource(sourceId) })
            assertEquals(null, install.activePackage)
            assertEquals(null, install.activateInstalledSource(sourceId))
            assertFalse(requireNotNull(library.sourceAvailability(sourceId)).available)
            assertEquals(before, library.book(book.identity))
            assertEquals(progress, library.progress(book.identity))
            assertEquals(credentials?.session, VerifiedBrowserSessionStore(context).getSnapshot(partition)?.session)
            flow.updateQuery("after")
            flow.search()
            assertTrue(flow.searchState is org.tsuyomi.feature.search.SearchResultState.Failure)
        }
        val restored = installFixture()
        assertEquals(installed.packageSha256, restored.packageSha256)
        assertTrue(requireNotNull(library.sourceAvailability(sourceId)).available)
        assertEquals(before, library.book(book.identity))
        assertEquals(progress, library.progress(book.identity))
        assertEquals(credentials?.session, VerifiedBrowserSessionStore(context).getSnapshot(partition)?.session)
    }

    @Test
    fun pendingApprovalBlocksUninstallAndCancellationKeepsInstalledSource() = runBlocking {
        val active = installFixture()
        val install = SourceInstallController(context, library)
        install.restoreInstalled()
        val archive = assemblePendingSourceApproval()
        try {
            install.prepare(Uri.fromFile(archive), context.contentResolver)
        } finally {
            archive.delete()
        }
        assertTrue(install.state is BrowseUiState.Approval)
        assertFalse(install.uninstall(active.manifest.sourceId.value))
        install.dismissApproval()
        assertEquals(active.packageSha256, install.activateInstalledSource(active.manifest.sourceId.value)?.packageSha256)
        assertTrue(requireNotNull(library.sourceAvailability(active.manifest.sourceId.value)).available)
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

    @Test
    fun missingArchiveIsRestoredDormantWithoutLosingHostDataOrRepeatedInvalidation() = runBlocking {
        val installed = installFixture()
        val sourceId = installed.manifest.sourceId.value
        val book = summary(sourceId, "1001", "保留的书籍")
        library.addToLibrary(org.tsuyomi.shared.librarydomain.LibraryBook(identity = book.identity, title = book.title, author = book.author,
        coverUrl = book.coverUrl, canonicalUrl = book.canonicalUrl,
        addedAt = SOURCE_FLOW_TEST_TIME, metadataUpdatedAt = SOURCE_FLOW_TEST_TIME,))
        val savedBook = library.book(book.identity)
        val before = requireNotNull(library.sourceAvailability(sourceId))
        assertTrue(File(context.noBackupFilesDir, "extensions/active/$sourceId.hxp").delete())

        val restore = SourceInstallController(context, library)
        restore.restoreInstalled()
        val dormant = requireNotNull(library.sourceAvailability(sourceId))
        assertFalse(dormant.available)
        assertEquals(before.generation + 1, dormant.generation)
        assertEquals(null, restore.activePackage)
        assertEquals(savedBook, library.book(book.identity))
        SourceInstallController(context, library).restoreInstalled()
        assertEquals(dormant, library.sourceAvailability(sourceId))
    }
    @Test
    fun restorePreservesEveryMatchingWritebackReceipt() = runBlocking {
        val packageInfo = installFixture()
        val sourceId = packageInfo.manifest.sourceId.value
        val policy = requireNotNull(library.sourceRemotePolicy(sourceId))
        assertTrue(library.setAddWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
        assertTrue(library.setRemoveWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))
        assertTrue(library.setMoveWritebackEnabled(sourceId, policy.capabilitySetFingerprint, true))

        SourceInstallController(context, library).restoreInstalled()

        val restored = requireNotNull(library.sourceRemotePolicy(sourceId))
        assertTrue(restored.addWritebackEnabled)
        assertTrue(restored.removeWritebackEnabled)
        assertTrue(restored.moveWritebackEnabled)
    }

    @Test
    fun sourceSelectionCannotHideAPendingInstallationApproval() = runBlocking {
        val active = installFixture()
        val install = SourceInstallController(context, library)
        install.restoreInstalled()
        val replacement = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("repository/replacement-candidate.json").bufferedReader().use { it.readText() },
        )
        val candidate = File(context.cacheDir, "source-selection-update.hxp")
        ZipInputStream(context.assets.open("wenku8-fixture.hxp")).use { input ->
            ZipOutputStream(candidate.outputStream()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val bytes = when (entry.name) {
                        "manifest.json" -> replacement.getJSONObject("manifest").toString().toByteArray(Charsets.UTF_8)
                        "signature.ed25519" -> Base64.getDecoder().decode(replacement.getString("signature"))
                        else -> input.readBytes()
                    }
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        install.prepare(Uri.fromFile(candidate), context.contentResolver)
        candidate.delete()

        assertTrue(install.state is BrowseUiState.Approval)
        assertEquals(null, install.activateInstalledSource(active.manifest.sourceId.value))
        assertTrue(install.state is BrowseUiState.Approval)
        install.approve(allowDowngrade = false)
        assertEquals("0.2.31", install.activePackage?.manifest?.version?.original)
        assertTrue(install.state is BrowseUiState.Installed)
    }

    @Test
    fun repositoryDownloadFailuresStayActionableAndRejectStaleSubscriptionActions() = runBlocking {
        val archive = assembleSignedSwitchOverlay("source-user-consent")
        val archiveBytes = archive.readBytes()
        val descriptor = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("repository/source-user-consent.json").bufferedReader().use { it.readText() })
        val now = Instant.now()
        val root = Ed25519PrivateKeyParameters(ByteArray(32) { (it + 43).toByte() }, 0)
        val catalog = signedRepositoryCatalog(archiveBytes, descriptor, root, now)
        var packageRequests = 0
        val fetcher = object : RepositoryFetcher {
            override fun fetch(url: String, maxBytes: Int): ByteArray = when (url) {
                RETRY_INDEX -> catalog
                RETRY_PACKAGE -> {
                    packageRequests += 1
                    when (packageRequests) {
                        1, 7 -> throw RepositoryFetchException(RepositoryFetchError.NETWORK)
                        2 -> throw RepositoryFetchException(RepositoryFetchError.TIMEOUT)
                        3 -> throw RepositoryFetchException(RepositoryFetchError.HTTP_STATUS)
                        4 -> throw RepositoryFetchException(RepositoryFetchError.CANCELLED)
                        5 -> archiveBytes
                        6 -> archiveBytes.copyOf().also { bytes ->
                            bytes[0] = (bytes[0].toInt() xor 1).toByte()
                        }
                        else -> error("Unexpected package request")
                    }
                }
                else -> error("Unexpected repository request")
            }.also { bytes -> check(bytes.size <= maxBytes) }
        }
        val directory = File(context.noBackupFilesDir, "repository-retry-regression")
        directory.deleteRecursively()
        val subscriptions = RepositorySubscriptionRegistry(directory, fetcher) { now }
        val trust = PackageTrustRegistry(File(directory, "package-trust"))
        val install = SourceInstallController(context, library, null, subscriptions, trust)
        val action = BrowseCatalogAction.Install(RETRY_SOURCE, RETRY_REPOSITORY)
        val link = "$RETRY_INDEX#repositoryId=$RETRY_REPOSITORY&keyId=$RETRY_ROOT_KEY_ID&publicKey=" +
            Base64.getEncoder().encodeToString(root.generatePublicKey().encoded)
        try {
            install.catalog.inspectSubscription(link)
            install.catalog.confirmSubscription()
            assertEquals(action.repositoryId, install.catalog.state.items.single().repositoryId)

            install.catalog.install(action.sourceId, action.repositoryId)
            val networkFailure = install.state as BrowseUiState.Failure
            assertEquals(BrowseInstallFailure.DOWNLOAD_NETWORK, networkFailure.reason)
            assertEquals(action, networkFailure.repositoryInstall)
            assertEquals(null, install.activePackage)

            install.catalog.install(action.sourceId, action.repositoryId)
            assertEquals(BrowseInstallFailure.DOWNLOAD_TIMEOUT, (install.state as BrowseUiState.Failure).reason)

            install.catalog.install(action.sourceId, action.repositoryId)
            assertEquals(BrowseInstallFailure.DOWNLOAD_SERVER, (install.state as BrowseUiState.Failure).reason)

            install.catalog.install(action.sourceId, action.repositoryId)
            assertEquals(BrowseInstallFailure.DOWNLOAD_CANCELLED, (install.state as BrowseUiState.Failure).reason)

            install.catalog.install(action.sourceId, action.repositoryId)
            assertTrue(install.state is BrowseUiState.Approval)
            assertEquals(null, install.activePackage)

            install.dismissApproval()
            install.catalog.install(action.sourceId, action.repositoryId)
            val digestFailure = install.state as BrowseUiState.Failure
            assertEquals(BrowseInstallFailure.VERIFICATION, digestFailure.reason)
            assertEquals(action, digestFailure.repositoryInstall)
            assertEquals(null, install.activePackage)

            install.catalog.install(action.sourceId, action.repositoryId)
            assertEquals(BrowseInstallFailure.DOWNLOAD_NETWORK, (install.state as BrowseUiState.Failure).reason)
            install.catalog.setSubscriptionEnabled(action.repositoryId, false)
            install.catalog.install(action.sourceId, action.repositoryId)
            assertEquals(7, packageRequests)
            assertEquals(BrowseInstallFailure.REPOSITORY, (install.state as BrowseUiState.Failure).reason)

            install.catalog.removeSubscription(action.repositoryId)
            install.catalog.install(action.sourceId, action.repositoryId)
            assertEquals(7, packageRequests)
            assertEquals(null, install.activePackage)
        } finally {
            archive.delete()
            directory.deleteRecursively()
        }
    }

    @Test
    fun acceptedRevocationSurvivesCallerCancellationWithoutDeletingSourceOrCredentials() = runBlocking {
        val active = installFixture()
        val sourceId = active.manifest.sourceId.value
        putCredential(sourceId)
        val archive = File(context.noBackupFilesDir, "extensions/active/$sourceId.hxp")
        val originalBytes = archive.readBytes()
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val cache = File(context.cacheDir, "repository-cancellation-regression")
        cache.deleteRecursively()
        val envelope = InstrumentationRegistry.getInstrumentation().context.assets
            .open("repository/revoked-fixture.json").use { it.readBytes() }
        val client = OfficialRepositoryClient(
            root = RepositoryRoot(
                "org.tsuyomi.extensions",
                "https://repository.example/index.json",
                PublisherKey(
                    "repository-cancellation-test",
                    Base64.getDecoder().decode("4W0lStImZafGes+iir7GZroORHjdlbC5HiTDbJgTtwU="),
                    PublisherTrust.BUILT_IN_OFFICIAL,
                ),
            ),
            storageDirectory = cache,
            fetcher = object : RepositoryFetcher {
                override fun fetch(url: String, maxBytes: Int): ByteArray {
                    entered.complete(Unit)
                    check(release.await(10, TimeUnit.SECONDS))
                    return envelope
                }
            },
            clock = { Instant.parse("2026-09-11T12:00:00Z") },
        )
        val install = SourceInstallController(context, library, client)
        install.restoreInstalled()
        assertTrue(requireNotNull(library.sourceAvailability(sourceId)).available)
        var executedSearches = 0
        val keys = OfficialRepositoryConfiguration.publisherKeys(client)
        val flow = SourceFlowController(
            context,
            library,
            SourceFlowSnapshotStore((context.applicationContext as TsuyomiApplication).preferencesDataStore),
            openSession = { FakeSession(searchResult = { _, _ ->
                executedSearches += 1
                listOf(summary(sourceId, "revocation-regression", "Retained source"))
            }) },
            isPackageTrusted = { OfficialRepositoryConfiguration.isTrusted(it, keys, (context.applicationContext as TsuyomiApplication).packageTrust) },
        )
        flow.open(active)
        flow.updateQuery("before revocation")
        flow.search()
        assertTrue(flow.searchState is org.tsuyomi.feature.search.SearchResultState.Results)
        val refresh = launch(Dispatchers.Default) { install.catalog.refresh() }
        try {
            withTimeout(10_000) { entered.await() }
            refresh.cancel()
            release.countDown()
            withTimeout(10_000) { refresh.join() }

            assertEquals(null, install.activePackage)
            assertFalse(requireNotNull(library.sourceAvailability(sourceId)).available)
            flow.updateQuery("after revocation")
            flow.search()
            assertTrue(flow.searchState is org.tsuyomi.feature.search.SearchResultState.Failure)
            assertEquals(1, executedSearches)
            assertTrue(originalBytes.contentEquals(archive.readBytes()))
            assertTrue(
                VerifiedBrowserSessionStore(context).getSnapshot(
                    SourceCredentialPartition(sourceId, HttpsOrigin("https://www.wenku8.net")),
                ) != null,
            )
        } finally {
            release.countDown()
            refresh.cancel()
            refresh.join()
            flow.close()
            cache.deleteRecursively()
        }
    }

    private fun assembleUnsignedCandidate(version: String, sourceId: String? = null): File {
        val archive = File.createTempFile("unsigned-local-candidate-", ".hxp", context.cacheDir)
        ZipInputStream(context.assets.open("wenku8-fixture.hxp")).use { input ->
            ZipOutputStream(archive.outputStream()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.name == "signature.ed25519") continue
                    val bytes = if (entry.name == "manifest.json") {
                        val manifest = JSONObject(input.readBytes().toString(Charsets.UTF_8))
                        manifest.put("manifestVersion", 2)
                        manifest.put("version", version)
                        if (sourceId != null) manifest.put("id", sourceId)
                        manifest.put("signing", JSONObject().put("algorithm", "none"))
                        manifest.toString().toByteArray(Charsets.UTF_8)
                    } else {
                        input.readBytes()
                    }
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        return archive
    }

    private fun signedRepositoryCatalog(
        archive: ByteArray,
        descriptor: JSONObject,
        root: Ed25519PrivateKeyParameters,
        now: Instant,
        revokedDigests: List<String> = emptyList(),
    ): ByteArray {
        val manifest = descriptor.getJSONObject("manifest")
        val publisher = descriptor.getJSONObject("provenance").getJSONObject("publisher")
        val publicKey = publisher.getString("publicKeyBase64")
        val signed = JSONObject()
            .put("repositoryId", RETRY_REPOSITORY)
            .put("sequence", 1)
            .put("issuedAt", now.minusSeconds(1).toString())
            .put("expiresAt", now.plusSeconds(86_400).toString())
            .put("publishers", JSONArray().put(JSONObject()
                .put("keyId", publisher.getString("keyId"))
                .put("publicKey", publicKey)
                .put("fingerprint", digest(Base64.getDecoder().decode(publicKey)))))
            .put("packages", JSONArray().put(JSONObject()
                .put("id", RETRY_SOURCE)
                .put("name", "下载重试测试来源")
                .put("version", manifest.getString("version"))
                .put("summary", "Repository install failure classification regression")
                .put("language", "JavaScript")
                .put("license", "Apache-2.0")
                .put("sourceUrl", "https://repository.example/source")
                .put("sourceRevision", "0123456789abcdef0123456789abcdef01234567")
                .put("downloadUrl", RETRY_PACKAGE)
                .put("size", archive.size)
                .put("sha256", digest(archive))
                .put("hostApi", manifest.getJSONObject("hostApi"))
                .put("publisherKeyId", publisher.getString("keyId"))))
            .put("revocations", JSONObject()
                .put("publisherFingerprints", JSONArray())
                .put("packageDigests", JSONArray(revokedDigests)))
        val message = "tsuyomi-repository-v1\u0000".toByteArray(Charsets.US_ASCII) + JsonCanonicalizer(signed.toString()).encodedUTF8
        val signature = Ed25519Signer().apply {
            init(true, root)
            update(message, 0, message.size)
        }.generateSignature()
        return JSONObject()
            .put("format", "tsuyomi-repository")
            .put("version", 1)
            .put("keyId", RETRY_ROOT_KEY_ID)
            .put("signed", signed)
            .put("signature", Base64.getEncoder().encodeToString(signature))
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun digest(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }


    private companion object {
        const val RETRY_SOURCE = "org.tsuyomi.nonofficial-test"
        const val RETRY_REPOSITORY = "org.tsuyomi.repository-retry"
        const val RETRY_INDEX = "https://repository.example/retry-index.json"
        const val RETRY_PACKAGE = "https://repository.example/retry-source.hxp"
        const val RETRY_ROOT_KEY_ID = "repository-retry-root"
    }
}
