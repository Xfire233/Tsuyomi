/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.erdtman.jcs.JsonCanonicalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tsuyomi.shared.sourcecontract.SourceId

class RepositoryCatalogTest {

    @Test
    fun repositoryVersionFloorOrdersUnboundedNumericPrereleaseIdentifiers() {
        assertTrue(
            SemanticVersion.parse("1.0.0-10000000000") > SemanticVersion.parse("1.0.0-9999999999"),
        )
    }
    @Test
    fun verifiedCatalogPersistsAndPreparesItsExactPackage() {
        val root = repositoryRoot()
        val extension = signedFixture(version = "1.0.0")
        val fetcher = FixtureRepositoryFetcher(
            mapOf(
                INDEX_URL to signedCatalog(root, sequence = 1, entries = listOf(CatalogEntry(extension))),
                DOWNLOAD_URL to extension.bytes,
            ),
        )
        val storage = Files.createTempDirectory("repository-cache").toFile()
        val client = repositoryClient(root, storage, fetcher)
        val installer = newInstaller(
            Files.createTempDirectory("repository-installer").toFile(),
            HxpArchiveVerifier(client.publisherKeys),
        )

        val catalog = client.refresh()
        assertEquals(1L, catalog.sequence)
        assertEquals(extension.publisher.fingerprint, catalog.packages.single().let { catalog.publisher(it.publisherKeyId) }?.fingerprint)
        val prepared = client.prepare("org.tsuyomi.wenku8", installer)
        client.validatePreparedRepositoryInstall(prepared)
        assertFalse(prepared.isLegacyMigration)
        installer.activate(prepared, ExtensionInstallApproval.approve(prepared))
        assertEquals(
            sha256(extension.bytes),
            installer.readVerifiedActive(SourceId("org.tsuyomi.wenku8"))?.packageSha256,
        )

        val restored = repositoryClient(root, storage, fetcher)
        assertEquals(1L, restored.cached()?.sequence)
    }

    @Test
    fun sequenceRollbackAndEqualSequenceEquivocationAreRejected() {
        val root = repositoryRoot()
        val extension = signedFixture(version = "1.0.0")
        val sequenceTwo = signedCatalog(root, sequence = 2, entries = listOf(CatalogEntry(extension)))
        val fetcher = FixtureRepositoryFetcher(mapOf(INDEX_URL to sequenceTwo, DOWNLOAD_URL to extension.bytes))
        val client = repositoryClient(root, Files.createTempDirectory("repository-high-water").toFile(), fetcher)
        assertEquals(2L, client.refresh().sequence)

        fetcher.replace(INDEX_URL, signedCatalog(root, sequence = 1, entries = listOf(CatalogEntry(extension))))
        val rollback = assertThrows(RepositoryCatalogException::class.java) { client.refresh() }
        assertEquals(RepositoryCatalogError.ROLLBACK_REJECTED, rollback.error)

        fetcher.replace(
            INDEX_URL,
            signedCatalog(root, sequence = 2, entries = listOf(CatalogEntry(extension, name = "Different signed metadata"))),
        )
        val equivocation = assertThrows(RepositoryCatalogException::class.java) { client.refresh() }
        assertEquals(RepositoryCatalogError.EQUIVOCATION_REJECTED, equivocation.error)
        assertEquals(2L, client.cached()?.sequence)
    }

    @Test
    fun sharedDirectoryFloorRejectsAStaleClientRefresh() {
        val root = repositoryRoot()
        val extension = signedFixture(version = "1.0.0")
        val storage = Files.createTempDirectory("repository-shared-floor").toFile()
        val staleFetcher = FixtureRepositoryFetcher(
            mapOf(
                INDEX_URL to signedCatalog(root, sequence = 1, entries = listOf(CatalogEntry(extension))),
                DOWNLOAD_URL to extension.bytes,
            ),
        )
        val freshFetcher = FixtureRepositoryFetcher(
            mapOf(
                INDEX_URL to signedCatalog(root, sequence = 3, entries = listOf(CatalogEntry(extension))),
                DOWNLOAD_URL to extension.bytes,
            ),
        )
        val staleClient = repositoryClient(root, storage, staleFetcher)
        val freshClient = repositoryClient(root, storage, freshFetcher)
        assertEquals(1L, staleClient.refresh().sequence)
        assertEquals(3L, freshClient.refresh().sequence)

        staleFetcher.replace(INDEX_URL, signedCatalog(root, sequence = 2, entries = listOf(CatalogEntry(extension))))
        val rejected = assertThrows(RepositoryCatalogException::class.java) { staleClient.refresh() }
        assertEquals(RepositoryCatalogError.ROLLBACK_REJECTED, rejected.error)
        assertEquals(3L, staleClient.cached()?.sequence)
    }

    @Test
    fun recoverySnapshotRetainsSignedTrustWhenStateRenameLagsItsFloor() {
        val root = repositoryRoot()
        val extension = signedFixture(version = "1.0.0")
        val storage = Files.createTempDirectory("repository-recovery-snapshot").toFile()
        val fetcher = FixtureRepositoryFetcher(
            mapOf(
                INDEX_URL to signedCatalog(root, sequence = 1, entries = listOf(CatalogEntry(extension))),
                DOWNLOAD_URL to extension.bytes,
            ),
        )
        val client = repositoryClient(root, storage, fetcher)
        assertEquals(1L, client.refresh().sequence)
        val oldState = File(storage, "repository-catalog-v1.state").readBytes()

        fetcher.replace(INDEX_URL, signedCatalog(root, sequence = 2, entries = listOf(CatalogEntry(extension))))
        assertEquals(2L, client.refresh().sequence)
        File(storage, "repository-catalog-v1.state").writeBytes(oldState)

        assertEquals(2L, repositoryClient(root, storage, fetcher).cached()?.sequence)
    }

    @Test
    fun packageHashFailureAndApprovalTimeRevocationPreserveTheActiveArchive() {
        val root = repositoryRoot()
        val active = signedFixture(version = "1.0.0")
        val candidate = signedFixture(version = "1.1.0")
        val fetcher = FixtureRepositoryFetcher(
            mapOf(
                INDEX_URL to signedCatalog(root, sequence = 1, entries = listOf(CatalogEntry(active))),
                DOWNLOAD_URL to active.bytes,
            ),
        )
        val client = repositoryClient(root, Files.createTempDirectory("repository-revocation").toFile(), fetcher)
        val installer = newInstaller(
            Files.createTempDirectory("repository-revocation-installer").toFile(),
            HxpArchiveVerifier(client.publisherKeys),
        )
        client.refresh()
        val activePrepared = client.prepare("org.tsuyomi.wenku8", installer)
        installer.activate(activePrepared, ExtensionInstallApproval.approve(activePrepared))
        val activeDigest = activePrepared.candidate.packageSha256

        fetcher.replace(
            INDEX_URL,
            signedCatalog(
                root,
                sequence = 2,
                entries = listOf(CatalogEntry(candidate, digest = "0".repeat(64))),
            ),
        )
        fetcher.replace(DOWNLOAD_URL, candidate.bytes)
        client.refresh()
        val badDownload = assertThrows(RepositoryCatalogException::class.java) {
            client.prepare("org.tsuyomi.wenku8", installer)
        }
        assertEquals(RepositoryCatalogError.PACKAGE_DOWNLOAD_INVALID, badDownload.error)
        assertEquals(activeDigest, installer.readVerifiedActive(SourceId("org.tsuyomi.wenku8"))?.packageSha256)

        fetcher.replace(INDEX_URL, signedCatalog(root, sequence = 3, entries = listOf(CatalogEntry(candidate))))
        client.refresh()
        val prepared = client.prepare("org.tsuyomi.wenku8", installer)
        fetcher.replace(
            INDEX_URL,
            signedCatalog(
                root,
                sequence = 4,
                entries = listOf(CatalogEntry(candidate)),
                revokedPackageDigests = setOf(prepared.candidate.packageSha256),
            ),
        )
        client.refresh()
        val revoked = assertThrows(RepositoryCatalogException::class.java) {
            client.validatePreparedRepositoryInstall(prepared)
        }
        assertEquals(RepositoryCatalogError.PACKAGE_REVOKED, revoked.error)
        val recheck = assertThrows(ExtensionInstallException::class.java) {
            installer.activate(prepared, ExtensionInstallApproval.approve(prepared))
        }
        assertEquals(ExtensionInstallError.CANDIDATE_RECHECK_FAILED, recheck.error)
        assertEquals(activeDigest, installer.readVerifiedActive(SourceId("org.tsuyomi.wenku8"))?.packageSha256)
    }

    @Test
    fun rootAuthorizedExactLegacyMigrationRequiresItsOwnApproval() {
        val root = repositoryRoot()
        val previous = signedFixture(version = "1.0.0")
        val replacement = signedFixture(
            version = "1.1.0",
            publisherKeyId = "fixture-new-publisher",
            publisherPrivateKey = ByteArray(32) { (it + 33).toByte() },
        )
        val fetcher = FixtureRepositoryFetcher(
            mapOf(
                INDEX_URL to signedCatalog(
                    root,
                    sequence = 1,
                    entries = listOf(CatalogEntry(replacement)),
                ),
                DOWNLOAD_URL to replacement.bytes,
            ),
        )
        val client = repositoryClient(root, Files.createTempDirectory("repository-migration").toFile(), fetcher)
        val localKeys = InMemoryPublisherKeyStore(listOf(previous.publisher))
        val resolver = object : PublisherKeyResolver {
            override fun resolve(keyId: String): PublisherKey? = client.publisherKeys.resolve(keyId) ?: localKeys.resolve(keyId)
            override fun isRevokedFingerprint(fingerprint: String): Boolean =
                client.publisherKeys.isRevokedFingerprint(fingerprint) || localKeys.isRevokedFingerprint(fingerprint)
            override fun isRevokedPackage(packageSha256: String): Boolean =
                client.publisherKeys.isRevokedPackage(packageSha256) || localKeys.isRevokedPackage(packageSha256)
        }
        val installer = newInstaller(
            Files.createTempDirectory("repository-migration-installer").toFile(),
            HxpArchiveVerifier(resolver),
        )
        val previousPrepared = installer.prepare(previous.writeToTemporaryFile())
        installer.activate(previousPrepared, ExtensionInstallApproval.approve(previousPrepared))
        client.refresh()
        val unauthorized = assertThrows(ExtensionInstallException::class.java) {
            client.prepare("org.tsuyomi.wenku8", installer)
        }
        assertEquals(ExtensionInstallError.KEY_ROTATION_NOT_AUTHORIZED, unauthorized.error)
        fetcher.replace(
            INDEX_URL,
            signedCatalog(
                root,
                sequence = 2,
                entries = listOf(
                    CatalogEntry(
                        replacement,
                        migration = RepositoryLegacyMigration(previous.publisher.fingerprint, sha256(previous.bytes)),
                    ),
                ),
            ),
        )
        client.refresh()

        val prepared = client.prepare("org.tsuyomi.wenku8", installer)
        client.validatePreparedRepositoryInstall(prepared)
        assertTrue(prepared.isLegacyMigration)
        val rejected = assertThrows(ExtensionInstallException::class.java) {
            installer.activate(prepared, ExtensionInstallApproval.approve(prepared))
        }
        assertEquals(ExtensionInstallError.LEGACY_MIGRATION_REQUIRES_CONFIRMATION, rejected.error)
        assertEquals(previousPrepared.candidate.packageSha256, installer.readVerifiedActive(SourceId("org.tsuyomi.wenku8"))?.packageSha256)

        installer.activate(prepared, ExtensionInstallApproval.approve(prepared, allowLegacyMigration = true))
        assertEquals(prepared.candidate.packageSha256, installer.readVerifiedActive(SourceId("org.tsuyomi.wenku8"))?.packageSha256)
    }

    @Test
    fun repositoryPrepareDoesNotTreatRevokedActiveArchiveAsAbsent() {
        val root = repositoryRoot()
        val active = signedFixture(version = "1.0.0")
        val candidate = signedFixture(version = "1.1.0")
        val fetcher = FixtureRepositoryFetcher(
            mapOf(
                INDEX_URL to signedCatalog(root, sequence = 1, entries = listOf(CatalogEntry(active))),
                DOWNLOAD_URL to active.bytes,
            ),
        )
        val client = repositoryClient(root, Files.createTempDirectory("repository-revoked-active").toFile(), fetcher)
        val installer = newInstaller(
            Files.createTempDirectory("repository-revoked-active-installer").toFile(),
            HxpArchiveVerifier(client.publisherKeys),
        )
        client.refresh()
        val installed = client.prepare("org.tsuyomi.wenku8", installer)
        installer.activate(installed, ExtensionInstallApproval.approve(installed))

        fetcher.replace(
            INDEX_URL,
            signedCatalog(
                root,
                sequence = 2,
                entries = listOf(CatalogEntry(candidate)),
                revokedPackageDigests = setOf(installed.candidate.packageSha256),
            ),
        )
        fetcher.replace(DOWNLOAD_URL, candidate.bytes)
        client.refresh()
        val error = assertThrows(ExtensionInstallException::class.java) {
            client.prepare("org.tsuyomi.wenku8", installer)
        }
        assertEquals(ExtensionInstallError.REPOSITORY_ACTIVE_UNVERIFIABLE, error.error)
    }

    @Test
    fun duplicateJsonKeysAreRejectedBeforeSignatureVerification() {
        val root = repositoryRoot()
        val duplicate = """
            {"format":"tsuyomi-repository","version":1,"keyId":"fixture-root-key","signed":{"repositoryId":"org.tsuyomi.extensions","repositoryId":"org.tsuyomi.attacker","sequence":1,"issuedAt":"2026-01-01T00:00:00Z","expiresAt":"2026-01-02T00:00:00Z","publishers":[],"packages":[],"revocations":{"publisherFingerprints":[],"packageDigests":[]}},"signature":"${"A".repeat(86)}=="}
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        val client = repositoryClient(
            root,
            Files.createTempDirectory("repository-duplicate-json").toFile(),
            FixtureRepositoryFetcher(mapOf(INDEX_URL to duplicate)),
        )

        val error = assertThrows(RepositoryCatalogException::class.java) { client.refresh() }
        assertEquals(RepositoryCatalogError.INVALID_CATALOG, error.error)

        val deeplyNested = ("[".repeat(65) + "0" + "]".repeat(65)).toByteArray(StandardCharsets.UTF_8)
        val nestedClient = repositoryClient(
            root,
            Files.createTempDirectory("repository-nested-json").toFile(),
            FixtureRepositoryFetcher(mapOf(INDEX_URL to deeplyNested)),
        )
        val nested = assertThrows(RepositoryCatalogException::class.java) { nestedClient.refresh() }
        assertEquals(RepositoryCatalogError.INVALID_CATALOG, nested.error)
    }

    @Test
    fun expiredCatalogNeverAuthorizesAnInstall() {
        val root = repositoryRoot()
        val extension = signedFixture(version = "1.0.0")
        val client = repositoryClient(
            root,
            Files.createTempDirectory("repository-expired").toFile(),
            FixtureRepositoryFetcher(
                mapOf(
                    INDEX_URL to signedCatalog(
                        root,
                        sequence = 1,
                        entries = listOf(CatalogEntry(extension)),
                        issuedAt = NOW.minusSeconds(2L * 24 * 60 * 60),
                        expiresAt = NOW.minusSeconds(24L * 60 * 60),
                    ),
                    DOWNLOAD_URL to extension.bytes,
                ),
            ),
        )

        val error = assertThrows(RepositoryCatalogException::class.java) { client.refresh() }
        assertEquals(RepositoryCatalogError.CATALOG_EXPIRED, error.error)
        assertEquals(null, client.cached())
    }

    @Test
    fun corruptCacheDoesNotCrashConstructionOrPermitHighWaterReset() {
        val root = repositoryRoot()
        val extension = signedFixture(version = "1.0.0")
        val storage = Files.createTempDirectory("repository-corrupt-cache").toFile()
        File(storage, "repository-catalog-v1.state").writeText("{}")
        val client = repositoryClient(
            root,
            storage,
            FixtureRepositoryFetcher(
                mapOf(
                    INDEX_URL to signedCatalog(root, sequence = 1, entries = listOf(CatalogEntry(extension))),
                    DOWNLOAD_URL to extension.bytes,
                ),
            ),
        )

        val cached = assertThrows(RepositoryCatalogException::class.java) { client.cached() }
        assertEquals(RepositoryCatalogError.INVALID_CATALOG, cached.error)
        val refresh = assertThrows(RepositoryCatalogException::class.java) { client.refresh() }
        assertEquals(RepositoryCatalogError.INVALID_CATALOG, refresh.error)
        assertTrue(client.publisherKeys.isRevokedFingerprint("a".repeat(64)))
        assertTrue(client.publisherKeys.isRevokedPackage("a".repeat(64)))
    }

    @Test
    fun tornJournalRetainsAuthenticatedPackageRevocationAgainstLocalFallback() {
        val root = repositoryRoot()
        val extension = signedFixture(version = "1.0.0")
        val storage = Files.createTempDirectory("repository-torn-journal").toFile()
        val fetcher = FixtureRepositoryFetcher(
            mapOf(
                INDEX_URL to signedCatalog(
                    root,
                    sequence = 1,
                    entries = listOf(CatalogEntry(extension)),
                    revokedPackageDigests = setOf(sha256(extension.bytes)),
                ),
                DOWNLOAD_URL to extension.bytes,
            ),
        )
        assertEquals(1L, repositoryClient(root, storage, fetcher).refresh().sequence)
        File(storage, "repository-catalog-v1.high-water").writeText("torn")

        val coldClient = repositoryClient(root, storage, fetcher)
        val cacheFailure = assertThrows(RepositoryCatalogException::class.java) { coldClient.cached() }
        assertEquals(RepositoryCatalogError.INVALID_CATALOG, cacheFailure.error)
        val localTrust = InMemoryPublisherKeyStore(listOf(extension.publisher))
        val compositeTrust = object : PublisherKeyResolver {
            override fun resolve(keyId: String): PublisherKey? = localTrust.resolve(keyId)
            override fun isRevokedFingerprint(fingerprint: String): Boolean =
                localTrust.isRevokedFingerprint(fingerprint) || coldClient.publisherKeys.isRevokedFingerprint(fingerprint)
            override fun isRevokedPackage(packageSha256: String): Boolean =
                localTrust.isRevokedPackage(packageSha256) || coldClient.publisherKeys.isRevokedPackage(packageSha256)
        }
        val rejected = assertThrows(HxpVerificationException::class.java) {
            HxpArchiveVerifier(compositeTrust).verify(extension.writeToTemporaryFile())
        }
        assertEquals(HxpVerificationError.REVOKED_PACKAGE, rejected.error)
    }

    private fun repositoryClient(root: RootFixture, storage: File, fetcher: FixtureRepositoryFetcher): OfficialRepositoryClient =
        OfficialRepositoryClient(root.root, storage, fetcher) { NOW }

    private fun repositoryRoot(): RootFixture {
        val privateKey = Ed25519PrivateKeyParameters(ByteArray(32) { (it + 91).toByte() }, 0)
        return RootFixture(
            root = RepositoryRoot(
                repositoryId = "org.tsuyomi.extensions",
                indexUrl = INDEX_URL,
                signingKey = PublisherKey(
                    keyId = "fixture-root-key",
                    publicKey = privateKey.generatePublicKey().encoded,
                    trust = PublisherTrust.BUILT_IN_OFFICIAL,
                ),
            ),
            privateKey = privateKey,
        )
    }

    private fun signedCatalog(
        root: RootFixture,
        sequence: Long,
        entries: List<CatalogEntry>,
        revokedPackageDigests: Set<String> = emptySet(),
        issuedAt: Instant = NOW,
        expiresAt: Instant = NOW.plusSeconds(7L * 24 * 60 * 60),
    ): ByteArray {
        val publishers = entries.map(CatalogEntry::fixture).map(SignedFixture::publisher).distinctBy(PublisherKey::keyId)
        val signed = JsonObject(
            linkedMapOf(
                "repositoryId" to JsonPrimitive("org.tsuyomi.extensions"),
                "sequence" to JsonPrimitive(sequence),
                "issuedAt" to JsonPrimitive(issuedAt.toString()),
                "expiresAt" to JsonPrimitive(expiresAt.toString()),
                "publishers" to JsonArray(publishers.map(::publisherJson)),
                "packages" to JsonArray(entries.map(::packageJson)),
                "revocations" to JsonObject(
                    mapOf(
                        "publisherFingerprints" to JsonArray(emptyList()),
                        "packageDigests" to JsonArray(revokedPackageDigests.sorted().map(::JsonPrimitive)),
                    ),
                ),
            ),
        )
        val canonical = JsonCanonicalizer(signed.toString()).encodedUTF8
        val signature = Ed25519Signer().apply {
            init(true, root.privateKey)
            val message = ByteArrayOutputStream().use { output ->
                output.write("tsuyomi-repository-v1\u0000".toByteArray(StandardCharsets.US_ASCII))
                output.write(canonical)
                output.toByteArray()
            }
            update(message, 0, message.size)
        }.generateSignature()
        return JsonObject(
            linkedMapOf(
                "format" to JsonPrimitive("tsuyomi-repository"),
                "version" to JsonPrimitive(1),
                "keyId" to JsonPrimitive(root.root.signingKey.keyId),
                "signed" to signed,
                "signature" to JsonPrimitive(Base64.getEncoder().encodeToString(signature)),
            ),
        ).toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun publisherJson(publisher: PublisherKey): JsonObject = JsonObject(
        linkedMapOf(
            "keyId" to JsonPrimitive(publisher.keyId),
            "publicKey" to JsonPrimitive(Base64.getEncoder().encodeToString(publisher.publicKey)),
            "fingerprint" to JsonPrimitive(publisher.fingerprint),
        ),
    )

    private fun packageJson(entry: CatalogEntry): JsonObject = JsonObject(
        buildMap {
            put("id", JsonPrimitive("org.tsuyomi.wenku8"))
            put("name", JsonPrimitive(entry.name))
            put("version", JsonPrimitive(entry.fixture.version))
            put("summary", JsonPrimitive("Repository test package"))
            put("language", JsonPrimitive("JavaScript"))
            put("license", JsonPrimitive("AGPL-3.0-only"))
            put("sourceUrl", JsonPrimitive("https://fixture.test/source"))
            put("sourceRevision", JsonPrimitive("0123456789abcdef0123456789abcdef01234567"))
            put("downloadUrl", JsonPrimitive(DOWNLOAD_URL))
            put("size", JsonPrimitive(entry.fixture.bytes.size))
            put("sha256", JsonPrimitive(entry.digest ?: sha256(entry.fixture.bytes)))
            put(
                "hostApi",
                JsonObject(mapOf("minInclusive" to JsonPrimitive("1.0.0"), "maxExclusive" to JsonPrimitive("2.0.0"))),
            )
            put("publisherKeyId", JsonPrimitive(entry.fixture.publisher.keyId))
            entry.migration?.let {
                put(
                    "legacyMigration",
                    JsonObject(
                        mapOf(
                            "fromPublisherFingerprint" to JsonPrimitive(it.fromPublisherFingerprint),
                            "fromPackageSha256" to JsonPrimitive(it.fromPackageSha256),
                        ),
                    ),
                )
            }
        },
    )

    private data class RootFixture(val root: RepositoryRoot, val privateKey: Ed25519PrivateKeyParameters)

    private data class CatalogEntry(
        val fixture: SignedFixture,
        val digest: String? = null,
        val name: String = "Repository source",
        val migration: RepositoryLegacyMigration? = null,
    )

    private class FixtureRepositoryFetcher(initial: Map<String, ByteArray>) : RepositoryFetcher {
        private val responses = initial.toMutableMap()

        override fun fetch(url: String, maxBytes: Int): ByteArray = responses[url]
            ?.also { require(it.size <= maxBytes) }
            ?.copyOf()
            ?: error("No fixture response for $url")

        fun replace(url: String, bytes: ByteArray) {
            responses[url] = bytes
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T00:00:00Z")
        const val INDEX_URL = "https://fixture.test/index-v1.json"
        const val DOWNLOAD_URL = "https://fixture.test/wenku8.hxp"
    }
}
