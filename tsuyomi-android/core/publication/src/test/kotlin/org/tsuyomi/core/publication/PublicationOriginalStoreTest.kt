/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef

class PublicationOriginalStoreTest {
    @Test
    fun missing_original_deletion_invokes_catalog_once_and_preserves_occupied_slots() = runBlocking {
        val root = temporaryRoot()
        try {
            val store = store(root)
            val missing = uuid()
            val other = uuid()
            val bytes = "unrelated retained original".toByteArray()
            commit(store, other, LocalPublicationFormat.TXT, bytes)
            var calls = 0
            assertEquals(PublicationDeleteStatus.COMPLETED, store.deletePublication(missing) {
                calls++; assertEquals(missing, it.publicationId); true
            })
            assertEquals(PublicationDeleteStatus.CATALOG_REJECTED, store.removeOriginal(missing) { calls++; false })
            try {
                store.deletePublication(missing) { calls++; throw IllegalStateException("catalog interruption") }
                throw AssertionError("Expected callback interruption")
            } catch (error: IllegalStateException) {
                assertEquals("catalog interruption", error.message)
            }
            assertEquals(3, calls)
            val occupied = File(roots(root).noBackupDirectory, "publication-originals/books/$missing")
            assertTrue(occupied.mkdir())
            val retained = File(occupied, "user-owned.data").apply { writeText("retain") }
            try {
                store.deletePublication(missing) { calls++; true }
                throw AssertionError("Expected occupied-slot rejection")
            } catch (_: PublicationStorageException) {
                assertEquals(3, calls)
                assertEquals("retain", retained.readText())
            }
            assertArrayEquals(bytes, store.original(other)!!.openInputStream().use { it.readBytes() })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun original_removal_then_full_delete_invokes_the_second_catalog_scope() = runBlocking {
        val root = temporaryRoot()
        try {
            val store = store(root)
            val id = uuid()
            commit(store, id, LocalPublicationFormat.MOBI, "two deletion scopes".toByteArray())
            val scopes = mutableListOf<String>()
            assertEquals(PublicationDeleteStatus.COMPLETED, store.removeOriginal(id) { scopes += "original"; true })
            assertNull(store.original(id))
            assertEquals(PublicationDeleteStatus.COMPLETED, store.deletePublication(id) { scopes += "catalog"; true })
            assertEquals(listOf("original", "catalog"), scopes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun native_admission_is_staged_bound_persisted_rebuildable_and_removed_with_original() = runBlocking {
        val root = temporaryRoot()
        try {
            val store = store(root)
            val id = uuid()
            val bytes = "private admission original".toByteArray()
            val staged = store.stage(ByteArrayInputStream(bytes))
            try {
                val record = nativeRecord(id, staged)
                staged.addAdmissionRecord(record)
                try {
                    store.commit(staged, uuid(), LocalPublicationFormat.MOBI)
                    throw AssertionError("Expected destination binding rejection")
                } catch (_: PublicationStorageException) {
                    assertArrayEquals(bytes, staged.openInputStream().use { it.readBytes() })
                }
                val receipt = store.commit(staged, id, LocalPublicationFormat.MOBI)
                try {
                    store.admissionRecord(receipt.original)
                    throw AssertionError("Expected unconfirmed original rejection")
                } catch (_: PublicationStorageException) {
                    assertNull(store.original(id))
                }
                assertTrue(receipt.confirmCatalogCommit())
                val original = store.original(id)!!
                assertTrue(store.admissionRecord(original) != null)
                File(root, "cache").deleteRecursively()
                val restarted = PublicationOriginalStore(roots(root))
                val recovered = restarted.original(id)!!
                assertTrue(restarted.admissionRecord(recovered) != null)
                assertArrayEquals(bytes, recovered.openInputStream().use { it.readBytes() })
                assertEquals(PublicationDeleteStatus.COMPLETED, restarted.removeOriginal(id) { true })
                assertFalse(File(roots(root).noBackupDirectory, "publication-originals/books/$id/admission.bin").exists())
            } finally {
                staged.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun interrupted_native_replacement_reclaims_only_owned_temporaries_and_restores_quota() = runBlocking {
        val root = temporaryRoot()
        try {
            fun sparse(file: File, length: Long) {
                java.nio.channels.FileChannel.open(file.toPath(),
                    java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.SPARSE).use { channel ->
                    channel.position(length - 1)
                    channel.write(java.nio.ByteBuffer.wrap(byteArrayOf(0)))
                }
            }
            val store = store(root)
            val id = uuid()
            val bytes = "interrupted native replacement original".toByteArray()
            commit(store, id, LocalPublicationFormat.MOBI, bytes)
            val original = store.original(id)!!
            val record = nativeRecord(id, original)
            store.putAdmissionRecord(original, record)
            val ownedRoot = File(roots(root).noBackupDirectory, "publication-originals")
            val directory = File(ownedRoot, "books/$id")
            val currentRecord = File(directory, "admission.bin").readBytes()
            val stale = File(directory, ".admission.${uuid()}.tmp")
            sparse(stale, PublicationStorageLimits.maxAdmissionRecordBytes)
            val unrelated = File(directory, ".original.meta.${uuid()}.tmp").apply { writeText("retain operation") }
            val invalid = File(directory, ".admission.not-a-uuid.tmp").apply { writeText("retain invalid name") }
            val occupied = File(ownedRoot, "books/${uuid()}").apply { mkdir() }
            val occupiedTemporary = File(occupied, ".admission.${uuid()}.tmp").apply { writeText("retain occupied slot") }
            val used = ownedRoot.walkTopDown().filter(File::isFile).sumOf(File::length)
            val retained = File(directory, "retained-unrelated.data")
            sparse(retained, PublicationStorageLimits.maxOwnedBytes - used - 4096L)
            val retainedLength = retained.length()
            val restarted = PublicationOriginalStore(roots(root))
            restarted.recover(mapOf(id to OriginalCatalogState(original.format, original.sha256, original.sizeBytes, true)))
            assertFalse(stale.exists())
            assertEquals("retain operation", unrelated.readText())
            assertEquals("retain invalid name", invalid.readText())
            assertEquals("retain occupied slot", occupiedTemporary.readText())
            assertEquals(retainedLength, retained.length())
            val recovered = restarted.original(id)!!
            assertArrayEquals(bytes, recovered.openInputStream().use { it.readBytes() })
            assertArrayEquals(currentRecord, File(directory, "admission.bin").readBytes())
            assertTrue(restarted.admissionRecord(recovered) != null)
            restarted.stage(ByteArrayInputStream(ByteArray(32 * 1024))).close()
            // A replacement also reclaims stale native writes before reserving new record bytes.
            val nextStale = File(directory, ".admission.${uuid()}.tmp")
            sparse(nextStale, PublicationStorageLimits.maxAdmissionRecordBytes)
            restarted.putAdmissionRecord(recovered, record)
            assertFalse(nextStale.exists())
            assertArrayEquals(currentRecord, File(directory, "admission.bin").readBytes())
            restarted.stage(ByteArrayInputStream(ByteArray(32 * 1024))).close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun stale_corrupt_admission_and_cancelled_rebuild_preserve_original_and_current_record() = runBlocking {
        val root = temporaryRoot()
        try {
            val store = store(root)
            val id = uuid()
            val bytes = "rebuild original".toByteArray()
            commit(store, id, LocalPublicationFormat.MOBI, bytes)
            val original = store.original(id)!!
            assertNull(store.admissionRecord(original))
            val record = nativeRecord(id, original)
            store.putAdmissionRecord(original, record)
            val file = File(roots(root).noBackupDirectory, "publication-originals/books/$id/admission.bin")
            java.io.RandomAccessFile(file, "rw").use { it.seek(4); it.writeInt(2) }
            assertNull(store.admissionRecord(original))
            store.putAdmissionRecord(original, nativeRecord(id, original))
            val complete = file.readBytes()
            file.writeBytes(complete.copyOf(complete.size - 1))
            assertNull(store.admissionRecord(original))
            store.putAdmissionRecord(original, nativeRecord(id, original))
            try {
                store.putAdmissionRecord(original, record) { throw java.util.concurrent.CancellationException("cancel rebuild") }
                throw AssertionError("Expected rebuild cancellation")
            } catch (_: java.util.concurrent.CancellationException) {
                assertArrayEquals(complete, file.readBytes())
            }
            assertTrue(store.admissionRecord(original) != null)
            assertArrayEquals(bytes, original.openInputStream().use { it.readBytes() })
            assertEquals(PublicationDeleteStatus.COMPLETED, store.removeOriginal(id) { true })
            assertFalse(file.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun nativeRecord(id: String, source: PublicationSource): PublicationAdmissionRecord {
        val section = PublicationSection("mobi:0", "Stored chapter", 0)
        val content = object : MobiPublicationContent {
            override val catalog = MobiPublicationCatalog(LocalPublicationFormat.MOBI, "Stored book", emptyList(),
                sections = listOf(section), tableOfContents = listOf(PublicationTocEntry("Stored chapter", section.contentId, "start")))
            override fun section(contentId: String, cancelled: () -> Unit): MobiPublicationSection {
                cancelled()
                return MobiPublicationSection(contentId, "<body><h1 id=\"start\">Stored chapter</h1><p>Body</p></body>",
                    emptyList(), emptyMap(), emptyList())
            }
            override fun resource(resourceId: String, cancelled: () -> Unit): InputStream = throw AssertionError("No resources")
            override fun resourceMetadata(resourceId: String): MobiPublicationResource = throw AssertionError("No resources")
            override fun close() = Unit
        }
        return checkNotNull(MobiPublicationParser(id, source.sha256, source.sizeBytes, content).parse().admissionRecord)
    }

    @Test
    fun staged_original_is_verified_before_catalog_confirmation_and_duplicate_matching_is_advisory() = runBlocking {
        val root = temporaryRoot()
        try {
            val store = store(root)
            val publicationId = uuid()
            val bytes = "durable local book".toByteArray()
            val input = CloseTrackingInputStream(bytes)
            val staged = store.stage(input)
            try {
                assertFalse(input.closed)
                assertEquals(bytes.size.toLong(), staged.sizeBytes)
                assertEquals(sha256(bytes), staged.sha256)
                assertArrayEquals(bytes, staged.openInputStream().use { it.readBytes() })

                val receipt = store.commit(staged, publicationId, LocalPublicationFormat.TXT)
                assertNull(store.original(publicationId))
                var blocked = false
                try {
                    receipt.original.openInputStream().close()
                } catch (_: PublicationStorageException) {
                    blocked = true
                }
                assertTrue(blocked)
                assertTrue(receipt.confirmCatalogCommit())
                assertArrayEquals(bytes, store.original(publicationId)!!.openInputStream().use { it.readBytes() })

                val duplicate = store.stage(ByteArrayInputStream(bytes))
                try {
                    assertEquals(listOf(publicationId), store.matchingOriginals(duplicate).map { it.publicationId })
                    val expected = OriginalCatalogState(LocalPublicationFormat.TXT, sha256(bytes), bytes.size.toLong(), true)
                    assertTrue(store.verifyReassociation(duplicate, publicationId, expected))
                    val mismatch = OriginalCatalogState(LocalPublicationFormat.TXT, sha256("other".toByteArray()), 5L, true)
                    assertFalse(store.verifyReassociation(duplicate, publicationId, mismatch))
                } finally {
                    duplicate.close()
                }
            } finally {
                staged.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun recovery_confirms_catalogued_promotion_reclaims_staged_jobs_and_preserves_unjournaled_books() = runBlocking {
        val root = temporaryRoot()
        try {
            val roots = roots(root)
            val firstStore = PublicationOriginalStore(roots)
            val publicationId = uuid()
            val bytes = "recovery book".toByteArray()
            val state = OriginalCatalogState(LocalPublicationFormat.EPUB, sha256(bytes), bytes.size.toLong(), true)
            val stagedCommit = firstStore.stage(ByteArrayInputStream(bytes))
            val receipt = firstStore.commit(stagedCommit, publicationId, LocalPublicationFormat.EPUB)

            val abandoned = firstStore.stage(ByteArrayInputStream("unfinished".toByteArray()))
            val unjournaledId = uuid()
            val unjournaled = File(roots.noBackupDirectory, "publication-originals/books/$unjournaledId")
            assertTrue(unjournaled.mkdirs())
            val userData = File(unjournaled, "user-owned.data")
            userData.writeText("leave untouched")

            val restarted = PublicationOriginalStore(roots)
            val results = restarted.recover(mapOf(publicationId to state))
            assertEquals(PublicationJobStatus.COMMITTED, results.single { it.publicationIds == listOf(publicationId) }.status)
            assertEquals(PublicationJobStatus.RECLAIMED, results.single { it.publicationIds.isEmpty() }.status)
            assertArrayEquals(bytes, restarted.original(publicationId)!!.openInputStream().use { it.readBytes() })
            assertTrue(receipt.confirmCatalogCommit())
            assertTrue(userData.isFile)
            abandoned.close()
            stagedCommit.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun recovery_preserves_catalog_claimed_pending_originals_and_unjournaled_jobs() = runBlocking {
        val root = temporaryRoot()
        try {
            val roots = roots(root)
            val store = PublicationOriginalStore(roots)
            val publicationId = uuid()
            val bytes = "catalog-claimed pending original".toByteArray()
            val state = OriginalCatalogState(LocalPublicationFormat.EPUB, sha256(bytes), bytes.size.toLong(), true)
            val staged = store.stage(ByteArrayInputStream(bytes))
            val receipt = store.commit(staged, publicationId, LocalPublicationFormat.EPUB)
            val committedDirectory = File(roots.noBackupDirectory, "publication-originals/books/$publicationId")
            val pendingDirectory = File(roots.noBackupDirectory, "publication-originals/books/.pending-${receipt.jobId}")
            assertTrue(committedDirectory.renameTo(pendingDirectory))

            val orphanJobId = uuid()
            val orphanJob = File(roots.noBackupDirectory, "publication-originals/jobs/$orphanJobId")
            assertTrue(orphanJob.mkdirs())
            val orphanData = File(orphanJob, "user-owned.data")
            orphanData.writeText("preserve unjournaled data")

            val results = PublicationOriginalStore(roots).recover(mapOf(publicationId to state))
            assertEquals(PublicationJobStatus.CONFLICT, results.single { it.jobId == receipt.jobId }.status)
            assertEquals(PublicationJobStatus.CORRUPT, results.single { it.jobId == orphanJobId }.status)
            assertTrue(File(pendingDirectory, "original.bin").isFile)
            assertTrue(orphanData.isFile)
            staged.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun journalLessDeletionOrphanDoesNotBlockUnchangedPublications() = runBlocking {
        val root = temporaryRoot()
        try {
            val roots = roots(root)
            val store = PublicationOriginalStore(roots)
            val firstId = uuid()
            val secondId = uuid()
            val firstBytes = "first unchanged publication".toByteArray()
            val secondBytes = "second unchanged publication".toByteArray()
            commit(store, firstId, LocalPublicationFormat.EPUB, firstBytes)
            commit(store, secondId, LocalPublicationFormat.TXT, secondBytes)
            val orphanId = uuid()
            val orphan = File(roots.noBackupDirectory, "publication-originals/deletions/$orphanId")
            assertTrue(orphan.mkdirs())
            val unidentifiedId = uuid()
            val unidentified = File(roots.noBackupDirectory, "publication-originals/deletions/$unidentifiedId")
            val unidentifiedPayload = File(unidentified, "payload")
            assertTrue(unidentifiedPayload.mkdirs())
            val admitted = File(roots.noBackupDirectory, "publication-originals/books/$firstId")
            File(admitted, "original.bin").copyTo(File(unidentifiedPayload, "original.bin"))
            File(admitted, "original.meta").copyTo(File(unidentifiedPayload, "original.meta"))

            val restarted = PublicationOriginalStore(roots)
            assertArrayEquals(firstBytes, restarted.original(firstId)!!.openInputStream().use { it.readBytes() })
            assertArrayEquals(secondBytes, restarted.original(secondId)!!.openInputStream().use { it.readBytes() })
            val catalog = mapOf(
                firstId to OriginalCatalogState(LocalPublicationFormat.EPUB, sha256(firstBytes), firstBytes.size.toLong(), true),
                secondId to OriginalCatalogState(LocalPublicationFormat.TXT, sha256(secondBytes), secondBytes.size.toLong(), true),
            )

            val recovery = restarted.recover(catalog)
            val orphanResult = recovery.single { it.jobId == orphanId }
            assertEquals(PublicationJobStatus.RECLAIMED, orphanResult.status)
            assertEquals(PublicationJobStatus.CORRUPT, recovery.single { it.jobId == unidentifiedId }.status)
            assertFalse(orphan.exists())
            assertArrayEquals(firstBytes, restarted.original(firstId)!!.openInputStream().use { it.readBytes() })
            assertArrayEquals(secondBytes, restarted.original(secondId)!!.openInputStream().use { it.readBytes() })
            assertArrayEquals(firstBytes, File(unidentifiedPayload, "original.bin").readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun transferRecoveryPreservesOnlyVerifiedPromotedAdmissions() = runBlocking {
        val root = temporaryRoot()
        try {
            val roots = roots(root)
            val store = PublicationOriginalStore(roots)
            val publicationId = uuid()
            val bytes = "promoted archive original".toByteArray()
            val staged = store.stage(ByteArrayInputStream(bytes))
            try {
                store.commit(staged, publicationId, LocalPublicationFormat.EPUB)
                val expected = OriginalCatalogState(LocalPublicationFormat.EPUB, sha256(bytes), bytes.size.toLong(), false)

                assertNull(store.original(publicationId))
                assertTrue(store.verifyOriginalForTransferRecovery(publicationId, expected))
                val result = store.recover(emptyMap(), setOf(publicationId)).single()
                assertEquals(PublicationJobStatus.PRESERVED, result.status)
                assertTrue(store.verifyOriginalForTransferRecovery(publicationId, expected))
                assertNull(store.original(publicationId))

                val original = File(roots.noBackupDirectory, "publication-originals/books/$publicationId/original.bin")
                val corrupted = bytes.copyOf().apply { this[0] = (this[0] + 1).toByte() }
                original.writeBytes(corrupted)
                assertFalse(store.verifyOriginalForTransferRecovery(publicationId, expected))
            } finally {
                staged.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun recovery_preserves_conflicting_catalog_bytes_and_reclaims_unassociated_promotion() = runBlocking {
        val root = temporaryRoot()
        try {
            val conflictingStore = store(File(root, "conflicting"))
            val conflictId = uuid()
            val importedBytes = "new parser-approved original".toByteArray()
            val imported = conflictingStore.stage(ByteArrayInputStream(importedBytes))
            conflictingStore.commit(imported, conflictId, LocalPublicationFormat.EPUB)
            val differentBytes = "different catalog original".toByteArray()
            val conflictState = OriginalCatalogState(
                LocalPublicationFormat.EPUB,
                sha256(differentBytes),
                differentBytes.size.toLong(),
                true,
            )
            val conflictResult = conflictingStore.recover(mapOf(conflictId to conflictState)).single()
            assertEquals(PublicationJobStatus.CONFLICT, conflictResult.status)
            assertTrue(File(root, "conflicting/no-backup/publication-originals/books/$conflictId/original.bin").isFile)

            val restoringStore = store(File(root, "restoring"))
            val restoreId = uuid()
            val restoreBytes = "metadata-only source bytes".toByteArray()
            val staged = restoringStore.stage(ByteArrayInputStream(restoreBytes))
            restoringStore.commit(staged, restoreId, LocalPublicationFormat.TXT)
            val metadataOnly = OriginalCatalogState(LocalPublicationFormat.TXT, sha256(restoreBytes), restoreBytes.size.toLong(), false)
            val restoreResult = restoringStore.recover(mapOf(restoreId to metadataOnly)).single()
            assertEquals(PublicationJobStatus.RECLAIMED, restoreResult.status)
            assertFalse(File(root, "restoring/no-backup/publication-originals/books/$restoreId").exists())
            imported.close()
            staged.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun interrupted_original_removal_restores_only_its_payload_and_full_deletion_is_separate() = runBlocking {
        val root = temporaryRoot()
        try {
            val store = store(root)
            val publicationId = uuid()
            val bytes = "deletion boundary".toByteArray()
            commit(store, publicationId, LocalPublicationFormat.MOBI, bytes)
            val state = OriginalCatalogState(LocalPublicationFormat.MOBI, sha256(bytes), bytes.size.toLong(), true)

            var callbackIdentity: BookIdentity.Local? = null
            try {
                store.removeOriginal(publicationId) { identity ->
                    callbackIdentity = identity
                    throw IllegalStateException("interrupted catalog callback")
                }
                throw AssertionError("Expected deletion callback interruption")
            } catch (_: IllegalStateException) {
                // Recovery decides from the unchanged catalog snapshot and restores the private payload.
            }
            assertEquals(publicationId, callbackIdentity!!.publicationId)
            assertNull(store.original(publicationId))
            var prematureCallbacks = 0
            try {
                store.deletePublication(publicationId) { prematureCallbacks++; true }
                throw AssertionError("Expected pending deletion rejection")
            } catch (_: PublicationStorageException) {
                assertEquals(0, prematureCallbacks)
            }
            val recovered = store.recover(mapOf(publicationId to state)).single { it.kind == PublicationJobKind.REMOVE_ORIGINAL }
            assertEquals(PublicationJobStatus.RESTORED, recovered.status)
            assertArrayEquals(bytes, store.original(publicationId)!!.openInputStream().use { it.readBytes() })

            assertEquals(
                PublicationDeleteStatus.COMPLETED,
                store.deletePublication(publicationId) { it.publicationId == publicationId },
            )
            assertNull(store.original(publicationId))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun local_resource_access_checks_owner_type_size_and_digest() = runBlocking {
        val root = temporaryRoot()
        try {
            val store = store(root)
            val publicationId = uuid()
            val resourceId = uuid()
            val bookBytes = "resource owner".toByteArray()
            val resourceBytes = byteArrayOf(9, 8, 7, 6)
            val staged = store.stage(ByteArrayInputStream(bookBytes))
            try {
                val resource = staged.addResource(resourceId, "image/png", ByteArrayInputStream(resourceBytes))
                val receipt = store.commit(staged, publicationId, LocalPublicationFormat.TXT)
                val reference = ReaderResourceRef.Local(publicationId, resource.resourceId, resource.mimeType, resource.sha256)
                assertNull(store.openResource(reference))
                assertTrue(receipt.confirmCatalogCommit())
                assertArrayEquals(resourceBytes, store.openResource(reference)!!.use { it.readBytes() })

                val diskResource = File(
                    root,
                    "no-backup/publication-originals/books/$publicationId/resources/$resourceId.bin",
                )
                diskResource.writeBytes(byteArrayOf(9, 8, 7, 5))
                assertNull(store.openResource(reference))
            } finally {
                staged.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun committed_resources_are_reused_repaired_and_exposed_as_pathless_references() = runBlocking {
        val root = temporaryRoot()
        try {
            val store = store(root)
            val publicationId = uuid()
            val resourceId = uuid()
            val original = "current publication original".toByteArray()
            val resourceBytes = byteArrayOf(1, 3, 5, 7, 9)
            val resourceSha = sha256(resourceBytes)
            commit(store, publicationId, LocalPublicationFormat.EPUB, original)

            val resourcesDirectory = File(root, "no-backup/publication-originals/books/$publicationId/resources")
            assertTrue(resourcesDirectory.mkdirs())
            val staleTemporary = File(resourcesDirectory, ".$resourceId.${uuid()}.tmp")
            staleTemporary.writeBytes(byteArrayOf(0))

            val stored = store.putResource(
                publicationId,
                resourceId,
                "image/png",
                ByteArrayInputStream(resourceBytes),
                resourceSha,
            )
            assertFalse(staleTemporary.exists())
            val reference = ReaderResourceRef.Local(publicationId, resourceId, "image/png", resourceSha)
            assertEquals(stored, store.resource(publicationId, resourceId))
            val data = File(root, "no-backup/publication-originals/books/$publicationId/resources/$resourceId.bin")
            val part = File(root, "no-backup/publication-originals/books/$publicationId/resources/$resourceId.part")
            assertTrue(data.isFile)
            assertFalse(part.exists())
            assertArrayEquals(resourceBytes, store.openResource(reference)!!.use { it.readBytes() })

            val reused = store.putResource(
                publicationId,
                resourceId,
                "image/png",
                ByteArrayInputStream(byteArrayOf(99)),
                resourceSha,
            )
            assertEquals(stored.sha256, reused.sha256)
            assertArrayEquals(resourceBytes, store.openResource(reference)!!.use { it.readBytes() })

            assertTrue(data.renameTo(part))
            store.putResource(publicationId, resourceId, "image/png", ByteArrayInputStream(byteArrayOf(99)), resourceSha)
            assertTrue(data.isFile)
            assertFalse(part.exists())
            assertArrayEquals(resourceBytes, store.openResource(reference)!!.use { it.readBytes() })

            data.writeBytes(byteArrayOf(1, 3, 5, 7, 8))
            store.putResource(publicationId, resourceId, "image/png", ByteArrayInputStream(resourceBytes), resourceSha)
            assertArrayEquals(resourceBytes, store.openResource(reference)!!.use { it.readBytes() })

            assertTrue(data.delete())
            store.putResource(publicationId, resourceId, "image/png", ByteArrayInputStream(resourceBytes), resourceSha)
            assertTrue(data.isFile)
            assertArrayEquals(resourceBytes, store.openResource(reference)!!.use { it.readBytes() })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun admission_limits_are_fixed_and_partial_quota_writes_are_reclaimed() = runBlocking {
        val root = temporaryRoot()
        try {
            val store = store(root)
            var originalFailure: PublicationLimitException? = null
            try {
                store.stage(GeneratedInputStream(PublicationStorageLimits.maxOriginalBytes + 1L))
            } catch (error: PublicationLimitException) {
                originalFailure = error
            }
            assertEquals(PublicationStorageLimit.ORIGINAL_BYTES, originalFailure?.limit)
            assertTrue(store.recover(emptyMap()).isEmpty())

            val staged = store.stage(ByteArrayInputStream(byteArrayOf(1)))
            try {
                var resourceFailure: PublicationLimitException? = null
                try {
                    staged.addResource(uuid(), "image/png", GeneratedInputStream(PublicationStorageLimits.maxResourceBytes + 1L))
                } catch (error: PublicationLimitException) {
                    resourceFailure = error
                }
                assertEquals(PublicationStorageLimit.RESOURCE_BYTES, resourceFailure?.limit)
                assertArrayEquals(byteArrayOf(1), staged.openInputStream().use { it.readBytes() })
            } finally {
                staged.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }


    private class CloseTrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}

class PublicationOriginalArchiveTest {
    @Test
    fun archive_round_trip_checks_transfer_bytes_and_restores_verified_originals() = runBlocking {
        val root = temporaryRoot()
        try {
            val sourceStore = store(File(root, "source"))
            val destinationStore = store(File(root, "destination"))
            val publicationId = uuid()
            val format = LocalPublicationFormat.AZW3
            val original = "archive source original".toByteArray()
            commit(sourceStore, publicationId, format, original)
            val descriptor = PublicationArchiveOriginal(publicationId, format, sha256(original), original.size.toLong())
            val transferJson = "{\"version\":6}".toByteArray()
            val archiveBytes = ByteArrayOutputStream()
            val sourceArchive = PublicationOriginalArchive(sourceStore)
            val summary = sourceArchive.export(transferJson, listOf(descriptor), archiveBytes)
            assertEquals(sha256(transferJson), summary.transferSha256)
            assertEquals(listOf(descriptor), summary.originals)

            val destinationArchive = PublicationOriginalArchive(destinationStore)
            val inspection = destinationArchive.inspect(ByteArrayInputStream(archiveBytes.toByteArray()))
            try {
                assertEquals(listOf(descriptor), inspection.originals)
                assertEquals(sha256(transferJson), inspection.transferSha256)
                val restored = destinationArchive.restore(inspection, emptyMap()) { payload, originals ->
                    assertArrayEquals(transferJson, payload.readBytes())
                    assertEquals(listOf(PublicationArchiveOriginalStatus.RESTORED), originals.map { it.status })
                    true
                }
                assertTrue(restored.metadataApplied)
                assertFalse(restored.finalizationPending)
                assertEquals(PublicationArchiveOriginalStatus.RESTORED, restored.originals.single().status)
                assertArrayEquals(original, destinationStore.original(publicationId)!!.openInputStream().use { it.readBytes() })
            } finally {
                inspection.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun archive_conflicts_never_replace_existing_bytes_and_metadata_refusal_rolls_back_new_bytes() = runBlocking {
        val root = temporaryRoot()
        try {
            val sourceStore = store(File(root, "source"))
            val publicationId = uuid()
            val original = "archive version".toByteArray()
            commit(sourceStore, publicationId, LocalPublicationFormat.EPUB, original)
            val descriptor = PublicationArchiveOriginal(
                publicationId,
                LocalPublicationFormat.EPUB,
                sha256(original),
                original.size.toLong(),
            )
            val archiveBytes = ByteArrayOutputStream()
            PublicationOriginalArchive(sourceStore).export("{\"version\":6}".toByteArray(), listOf(descriptor), archiveBytes)

            val conflictingStore = store(File(root, "conflicting"))
            val different = "existing user original".toByteArray()
            commit(conflictingStore, publicationId, LocalPublicationFormat.EPUB, different)
            val conflictArchive = PublicationOriginalArchive(conflictingStore)
            val conflictInspection = conflictArchive.inspect(ByteArrayInputStream(archiveBytes.toByteArray()))
            try {
                val conflictState = OriginalCatalogState(
                    LocalPublicationFormat.EPUB,
                    sha256(different),
                    different.size.toLong(),
                    true,
                )
                val result = conflictArchive.restore(conflictInspection, mapOf(publicationId to conflictState)) { _, originals ->
                    assertEquals(PublicationArchiveOriginalStatus.CONFLICT, originals.single().status)
                    true
                }
                assertTrue(result.metadataApplied)
                assertArrayEquals(different, conflictingStore.original(publicationId)!!.openInputStream().use { it.readBytes() })
            } finally {
                conflictInspection.close()
            }

            val refusingStore = store(File(root, "refusing"))
            val refusingArchive = PublicationOriginalArchive(refusingStore)
            val refusingInspection = refusingArchive.inspect(ByteArrayInputStream(archiveBytes.toByteArray()))
            try {
                val result = refusingArchive.restore(refusingInspection, emptyMap()) { _, _ -> false }
                assertFalse(result.metadataApplied)
                assertFalse(result.finalizationPending)
                assertEquals(PublicationArchiveOriginalStatus.ROLLED_BACK, result.originals.single().status)
                assertNull(refusingStore.original(publicationId))
            } finally {
                refusingInspection.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun archive_inspection_rejects_unmanifested_original_entry() = runBlocking {
        val root = temporaryRoot()
        try {
            val sourceStore = store(File(root, "source"))
            val destinationStore = store(File(root, "destination"))
            val publicationId = uuid()
            val original = "manifested bytes".toByteArray()
            commit(sourceStore, publicationId, LocalPublicationFormat.TXT, original)
            val descriptor = PublicationArchiveOriginal(
                publicationId,
                LocalPublicationFormat.TXT,
                sha256(original),
                original.size.toLong(),
            )
            val valid = ByteArrayOutputStream()
            PublicationOriginalArchive(sourceStore).export("{\"version\":6}".toByteArray(), listOf(descriptor), valid)

            val tampered = ByteArrayOutputStream()
            ZipInputStream(ByteArrayInputStream(valid.toByteArray())).use { input ->
                ZipOutputStream(tampered).use { zip ->
                    val buffer = ByteArray(4096)
                    var entry = input.nextEntry
                    while (entry != null) {
                        zip.putNextEntry(ZipEntry(entry.name).apply { time = 0L })
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count > 0) zip.write(buffer, 0, count)
                        }
                        zip.closeEntry()
                        entry = input.nextEntry
                    }
                    zip.putNextEntry(ZipEntry("originals/${uuid()}.bin"))
                    zip.write(byteArrayOf(1))
                    zip.closeEntry()
                }
            }
            var rejected: PublicationArchiveError? = null
            try {
                PublicationOriginalArchive(destinationStore).inspect(ByteArrayInputStream(tampered.toByteArray()))
                throw AssertionError("Expected unmanifested archive entry rejection")
            } catch (error: PublicationArchiveException) {
                rejected = error.reason
            }
            assertEquals(PublicationArchiveError.EXTRA_ENTRY, rejected)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun archive_inspection_rejects_duplicate_entries() = runBlocking {
        val root = temporaryRoot()
        try {
            val store = store(root)
            var rejected: PublicationArchiveError? = null
            try {
                PublicationOriginalArchive(store).inspect(ByteArrayInputStream(duplicateManifestZip()))
                throw AssertionError("Expected duplicate archive entry rejection")
            } catch (error: PublicationArchiveException) {
                rejected = error.reason
            }
            assertEquals(PublicationArchiveError.DUPLICATE_ENTRY, rejected)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun archive_inspection_rejects_absolute_and_traversal_entry_names_before_extraction() = runBlocking {
        val root = temporaryRoot()
        try {
            val store = store(root)
            for (unsafeName in listOf("../outside.bin", "/outside.bin", "C:\\outside.bin")) {
                val bytes = ByteArrayOutputStream()
                ZipOutputStream(bytes).use { zip ->
                    zip.putNextEntry(ZipEntry(unsafeName))
                    zip.write(byteArrayOf(1))
                    zip.closeEntry()
                }
                var rejected: PublicationArchiveError? = null
                try {
                    PublicationOriginalArchive(store).inspect(ByteArrayInputStream(bytes.toByteArray()))
                    throw AssertionError("Expected unsafe archive entry rejection")
                } catch (error: PublicationArchiveException) {
                    rejected = error.reason
                }
                assertEquals(PublicationArchiveError.UNSAFE_ENTRY, rejected)
            }
            assertFalse(File(root, "outside.bin").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}


private fun duplicateManifestZip(): ByteArray {
    val output = ByteArrayOutputStream()
    val name = "manifest.bin".toByteArray(Charsets.UTF_8)
    val entries = listOf(byteArrayOf(1), byteArrayOf(2))
    val offsets = ArrayList<Int>(entries.size)
    val checksums = entries.map { bytes -> CRC32().apply { update(bytes) }.value }

    entries.forEachIndexed { index, bytes ->
        offsets += output.size()
        output.writeZipField(0x04034b50L, 4)
        output.writeZipField(20, 2)
        output.writeZipField(0, 2)
        output.writeZipField(0, 2)
        output.writeZipField(0, 2)
        output.writeZipField(0, 2)
        output.writeZipField(checksums[index], 4)
        output.writeZipField(bytes.size.toLong(), 4)
        output.writeZipField(bytes.size.toLong(), 4)
        output.writeZipField(name.size.toLong(), 2)
        output.writeZipField(0, 2)
        output.write(name)
        output.write(bytes)
    }

    val centralOffset = output.size()
    entries.forEachIndexed { index, bytes ->
        output.writeZipField(0x02014b50L, 4)
        output.writeZipField(20, 2)
        output.writeZipField(20, 2)
        output.writeZipField(0, 2)
        output.writeZipField(0, 2)
        output.writeZipField(0, 2)
        output.writeZipField(0, 2)
        output.writeZipField(checksums[index], 4)
        output.writeZipField(bytes.size.toLong(), 4)
        output.writeZipField(bytes.size.toLong(), 4)
        output.writeZipField(name.size.toLong(), 2)
        output.writeZipField(0, 2)
        output.writeZipField(0, 2)
        output.writeZipField(0, 2)
        output.writeZipField(0, 2)
        output.writeZipField(0, 4)
        output.writeZipField(offsets[index].toLong(), 4)
        output.write(name)
    }
    val centralSize = output.size() - centralOffset
    output.writeZipField(0x06054b50L, 4)
    output.writeZipField(0, 2)
    output.writeZipField(0, 2)
    output.writeZipField(entries.size.toLong(), 2)
    output.writeZipField(entries.size.toLong(), 2)
    output.writeZipField(centralSize.toLong(), 4)
    output.writeZipField(centralOffset.toLong(), 4)
    output.writeZipField(0, 2)
    return output.toByteArray()
}

private fun ByteArrayOutputStream.writeZipField(value: Long, byteCount: Int) {
    repeat(byteCount) { shift ->
        write(((value ushr (shift * 8)) and 0xff).toInt())
    }
}
private fun temporaryRoot(): File = Files.createTempDirectory("publication-originals-test").toFile()

private fun roots(root: File): StorageRoots = StorageRoots(File(root, "no-backup"), File(root, "cache"))

private fun store(root: File): PublicationOriginalStore = PublicationOriginalStore(roots(root))

private suspend fun commit(
    store: PublicationOriginalStore,
    publicationId: String,
    format: LocalPublicationFormat,
    bytes: ByteArray,
) {
    val staged = store.stage(ByteArrayInputStream(bytes))
    try {
        val receipt = store.commit(staged, publicationId, format)
        assertTrue(receipt.confirmCatalogCommit())
    } finally {
        staged.close()
    }
}

private fun uuid(): String = UUID.randomUUID().toString()

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }
private class GeneratedInputStream(private var remaining: Long) : InputStream() {
    override fun read(): Int {
        if (remaining == 0L) return -1
        remaining--
        return 0x41
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return -1
        val count = minOf(length.toLong(), remaining).toInt()
        java.util.Arrays.fill(buffer, offset, offset + count, 0x41.toByte())
        remaining -= count
        return count
    }
}
