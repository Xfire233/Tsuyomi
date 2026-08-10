/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android
import android.net.Uri
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.database.ImportSessionStatus
import org.tsuyomi.core.database.RoomTransferRepository
import org.tsuyomi.core.database.TsuyomiDatabase
import org.tsuyomi.core.database.LibraryBook
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.preferences.PortableReaderPreferencesRepository
import org.tsuyomi.feature.backup.TransferUiState
import org.tsuyomi.shared.backup.ImportKind
import org.tsuyomi.shared.backup.ImportPlan
import org.tsuyomi.shared.backup.ImportPlanCodec
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.backup.TransferBook
import org.tsuyomi.shared.backup.TransferCodec
import org.tsuyomi.shared.backup.TransferSnapshot
import org.tsuyomi.shared.model.BookIdentity

@RunWith(AndroidJUnit4::class)
class TransferCoordinatorInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun recoveryCompletesEveryDurableImportStage() = runBlocking {
        for (stage in listOf(ImportSessionStatus.PREPARED, ImportSessionStatus.ROOM_APPLIED, ImportSessionStatus.PREFERENCES_APPLIED)) {
            val fixture = fixture(stage.name.lowercase())
            try {
                fixture.repository.prepare(
                    fixture.sessionId,
                    fixture.plan,
                    fixture.digest,
                    fixture.planFile.absolutePath,
                    "{}",
                    Instant.EPOCH,
                )
                if (stage != ImportSessionStatus.PREPARED) {
                    fixture.repository.applyRoomPlan(fixture.sessionId, fixture.digest, fixture.plan)
                }
                if (stage == ImportSessionStatus.PREFERENCES_APPLIED) {
                    assertTrue(fixture.repository.markPreferencesApplied(fixture.sessionId, fixture.digest))
                }

                fixture.coordinator.recoverPendingImport()

                assertTrue(fixture.coordinator.recoveryReady)
                assertTrue(fixture.coordinator.state is TransferUiState.Completed)
                assertNull(fixture.repository.pending())
                assertFalse(fixture.planFile.exists())
                assertEquals(1, fixture.repository.exportSnapshot(Instant.EPOCH, null).library.size)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun recoveryFailureCanRetryAfterPlanIsRepaired() = runBlocking {
        val fixture = fixture("retry")
        try {
            fixture.repository.prepare(fixture.sessionId, fixture.plan, fixture.digest, fixture.planFile.absolutePath, "{}", Instant.EPOCH)
            fixture.planFile.writeText("{}")

            fixture.coordinator.recoverPendingImport()

            assertTrue(fixture.coordinator.recoveryReady)
            assertTrue(fixture.coordinator.state is TransferUiState.RecoveryFailure)
            assertTrue((fixture.coordinator.state as TransferUiState.RecoveryFailure).canAbort)
            assertEquals(ImportSessionStatus.PREPARED, fixture.repository.pending()?.status)

            fixture.planFile.writeBytes(ImportPlanCodec.encode(fixture.plan))
            fixture.coordinator.retryRecovery()

            assertTrue(fixture.coordinator.state is TransferUiState.Completed)
            assertNull(fixture.repository.pending())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun recoveryFailureCanAbortOnlyPreparedSession() = runBlocking {
        val fixture = fixture("abort")
        try {
            fixture.repository.prepare(fixture.sessionId, fixture.plan, fixture.digest, fixture.planFile.absolutePath, "{}", Instant.EPOCH)
            fixture.planFile.writeText("{}")

            fixture.coordinator.recoverPendingImport()
            fixture.coordinator.abortRecovery()

            assertTrue(fixture.coordinator.recoveryReady)
            assertEquals(ImportSessionStatus.ABORTED, fixture.repository.latest()?.status)
            assertNull(fixture.repository.pending())
            assertFalse(fixture.planFile.exists())
            assertTrue(fixture.coordinator.state is TransferUiState.Idle)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun recoveryDoesNotOfferAbortAfterRoomWasApplied() = runBlocking {
        val fixture = fixture("abort-guard")
        try {
            fixture.repository.prepare(fixture.sessionId, fixture.plan, fixture.digest, fixture.planFile.absolutePath, "{}", Instant.EPOCH)
            fixture.repository.applyRoomPlan(fixture.sessionId, fixture.digest, fixture.plan)
            fixture.planFile.writeText("{}")

            fixture.coordinator.recoverPendingImport()
            fixture.coordinator.abortRecovery()

            val failure = fixture.coordinator.state as TransferUiState.RecoveryFailure
            assertFalse(failure.canAbort)
            assertEquals(ImportSessionStatus.ROOM_APPLIED, fixture.repository.pending()?.status)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun recoveryBlocksTamperedPlanAndRetainsJournal() = runBlocking {
        val fixture = fixture("tampered")
        try {
            fixture.repository.prepare(fixture.sessionId, fixture.plan, fixture.digest, fixture.planFile.absolutePath, "{}", Instant.EPOCH)
            fixture.planFile.writeText("{}")

            fixture.coordinator.recoverPendingImport()

            assertTrue(fixture.coordinator.recoveryReady)
            assertTrue(fixture.coordinator.state is TransferUiState.RecoveryFailure)
            assertEquals(ImportSessionStatus.PREPARED, fixture.repository.pending()?.status)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cleanupPendingIsDiscoveredAndCanBeRetried() = runBlocking {
        val fixture = fixture("cleanup")
        try {
            fixture.repository.prepare(fixture.sessionId, fixture.plan, fixture.digest, fixture.planFile.absolutePath, "{}", Instant.EPOCH)
            assertTrue(fixture.repository.abort(fixture.sessionId, fixture.digest, cleanupPending = true))
            assertTrue(fixture.planFile.delete())
            assertTrue(fixture.planFile.mkdirs())
            File(fixture.planFile, "blocked").writeText("blocked")

            fixture.coordinator.recoverPendingImport()

            assertTrue(fixture.coordinator.recoveryReady)
            assertEquals(ImportSessionStatus.ABORTED_CLEANUP_PENDING, fixture.repository.pending()?.status)
            assertTrue(fixture.coordinator.state is TransferUiState.RecoveryFailure)
            assertTrue((fixture.coordinator.state as TransferUiState.RecoveryFailure).canRetryCleanup)

            assertTrue(File(fixture.planFile, "blocked").delete())
            assertTrue(fixture.planFile.delete())
            fixture.coordinator.retryRecovery()

            assertNull(fixture.repository.pending())
            assertEquals(ImportSessionStatus.ABORTED, fixture.repository.latest()?.status)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun abortedCleanupRecoveryAcceptsAlreadyMissingPlanFile() = runBlocking {
        val fixture = fixture("missing-cleanup")
        try {
            fixture.repository.prepare(fixture.sessionId, fixture.plan, fixture.digest, fixture.planFile.absolutePath, "{}", Instant.EPOCH)
            assertTrue(fixture.repository.abort(fixture.sessionId, fixture.digest, cleanupPending = true))
            assertTrue(fixture.planFile.delete())

            fixture.coordinator.recoverPendingImport()

            assertTrue(fixture.coordinator.recoveryReady)
            assertNull(fixture.repository.pending())
            assertEquals(ImportSessionStatus.ABORTED, fixture.repository.latest()?.status)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun confirmImportFailureAfterRoomCommitExposesRetryableRecovery() = runBlocking {
        var failPreferences = true
        val fixture = fixture("confirm-post-room") { failPreferences }
        val input = File(context.cacheDir, "confirm-${fixture.sessionId}.json")
        try {
            input.writeBytes(
                TransferCodec.encode(
                    TransferSnapshot(Instant.EPOCH, fixture.plan.books, emptyList(), fixture.plan.readerPreferences),
                ),
            )
            fixture.coordinator.readForReview(Uri.fromFile(input), context.contentResolver)

            fixture.coordinator.confirmImport()

            val failure = fixture.coordinator.state as TransferUiState.RecoveryFailure
            assertFalse(failure.canAbort)
            assertEquals(ImportSessionStatus.ROOM_APPLIED, fixture.repository.pending()?.status)

            failPreferences = false
            fixture.coordinator.retryRecovery()
            assertTrue(fixture.coordinator.state is TransferUiState.Completed)
            assertNull(fixture.repository.pending())
        } finally {
            input.delete()
            fixture.close()
        }
    }

    @Test
    fun reviewSurfacesRoomConflictsBeforeAnyImportMutation() = runBlocking {
        val fixture = fixture("review-conflict")
        val input = File(context.cacheDir, "review-${fixture.sessionId}.json")
        try {
            val identity = fixture.plan.books.single().identity
            val existingAt = Instant.parse("2026-08-09T00:00:00Z")
            val library = RoomLibraryRepository(fixture.database)
            library.addToLibrary(LibraryBook(identity, "本地标题", Instant.EPOCH, existingAt))
            input.writeBytes(
                TransferCodec.encode(
                    TransferSnapshot(
                        createdAt = Instant.EPOCH,
                        library = listOf(TransferBook(identity, "导入标题", updatedAt = existingAt.minusSeconds(1))),
                        shelves = emptyList(),
                        readerPreferences = null,
                    ),
                ),
            )

            fixture.coordinator.readForReview(Uri.fromFile(input), context.contentResolver)

            val review = fixture.coordinator.state as TransferUiState.Review
            assertTrue(review.value.conflictCodes.any { it.startsWith("existing-book-metadata-retained · ") })
            assertEquals("本地标题", library.book(identity)?.title)
            assertNull(fixture.repository.pending())
        } finally {
            input.delete()
            fixture.close()
        }
    }

    @Test
    fun malformedReaderPreferencesFinishInFailureInsteadOfWorking() = runBlocking {
        val fixture = fixture("invalid-preferences")
        val input = File(context.cacheDir, "invalid-${fixture.sessionId}.json")
        try {
            input.writeText(
                """{"format":"tsuyomi-transfer","version":1,"createdAt":"2026-08-08T00:00:00Z","library":[],"shelves":[],"preferences":{"reader":{"flow":1}}}""",
            )

            fixture.coordinator.readForReview(Uri.fromFile(input), context.contentResolver)

            assertTrue(fixture.coordinator.state is TransferUiState.Failure)
            assertNull(fixture.repository.pending())
        } finally {
            input.delete()
            fixture.close()
        }
    }

    @Test
    fun recoveryFailureUsesTheRefreshedDurableStage() = runBlocking {
        val fixture = fixture("recovery-stage") { true }
        try {
            fixture.repository.prepare(
                fixture.sessionId,
                fixture.plan,
                fixture.digest,
                fixture.planFile.absolutePath,
                "{}",
                Instant.EPOCH,
            )

            fixture.coordinator.recoverPendingImport()

            val failure = fixture.coordinator.state as TransferUiState.RecoveryFailure
            assertFalse(failure.canAbort)
            assertEquals(ImportSessionStatus.ROOM_APPLIED, fixture.repository.pending()?.status)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun exportPreflightSurvivesRecreationAndRejectsStaleCallbacks() = runBlocking {
        val fixture = fixture("export")
        try {
            val readerPreferences = requireNotNull(fixture.plan.readerPreferences)
            val first = fixture.coordinator.prepareExport(readerPreferences) ?: error("missing first preflight")
            val second = fixture.coordinator.prepareExport(readerPreferences) ?: error("missing second preflight")
            val firstFile = preflightFile(first)
            val secondFile = preflightFile(second)

            fixture.coordinator.cancelPreparedExport(first.ownerGeneration, first.canonicalDigest)
            val staleOutput = File(context.cacheDir, "stale-${fixture.sessionId}.json")
            fixture.coordinator.writePreparedExport(
                Uri.fromFile(staleOutput),
                context.contentResolver,
                first.ownerGeneration,
                first.canonicalDigest,
            )

            assertFalse(firstFile.exists())
            assertFalse(staleOutput.exists())
            assertTrue(secondFile.exists())

            val recreated = TransferCoordinator(context, fixture.repository, fixture.preferenceRepository)
            val output = File(context.cacheDir, "export-${fixture.sessionId}.json")
            recreated.writePreparedExport(Uri.fromFile(output), context.contentResolver, second.ownerGeneration, second.canonicalDigest)

            assertEquals(second.canonicalDigest, TransferCodec.digest(output.readBytes()))
            assertFalse(secondFile.exists())
            assertTrue(recreated.state is TransferUiState.Exported)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun exportPreflightCleansAfterWriteFailureAndStartupSweep() = runBlocking {
        val fixture = fixture("export-cleanup")
        try {
            val readerPreferences = requireNotNull(fixture.plan.readerPreferences)
            val prepared = fixture.coordinator.prepareExport(readerPreferences) ?: error("missing preflight")
            val preparedFile = preflightFile(prepared)
            fixture.coordinator.writePreparedExport(
                Uri.parse("content://org.tsuyomi.android.missing/export"),
                context.contentResolver,
                prepared.ownerGeneration,
                prepared.canonicalDigest,
            )

            assertFalse(preparedFile.exists())
            assertTrue(fixture.coordinator.state is TransferUiState.Failure)

            val orphanBytes = TransferCodec.encodeBounded(fixture.repository.exportSnapshot(Instant.EPOCH, fixture.plan.readerPreferences))
                ?: error("missing orphan bytes")
            val orphanDigest = TransferCodec.digest(orphanBytes)
            val orphan = File(context.cacheDir, "transfer-export-preflight/preflight-999999-$orphanDigest.json")
            orphan.parentFile?.mkdirs()
            orphan.writeBytes(orphanBytes)

            TransferCoordinator(context, fixture.repository, fixture.preferenceRepository).recoverPendingImport()

            assertFalse(orphan.exists())
        } finally {
            fixture.close()
        }
    }

    private fun fixture(label: String, failPreferenceImports: () -> Boolean = { false }): Fixture {
        val sessionId = "$label-${UUID.randomUUID()}"
        val database = Room.inMemoryDatabaseBuilder(context, TsuyomiDatabase::class.java).allowMainThreadQueries().build()
        val repository = RoomTransferRepository(database)
        val plan = ImportPlan(
            kind = ImportKind.TSUYOMI_TRANSFER,
            sourceCreatedAt = Instant.EPOCH,
            books = listOf(TransferBook(BookIdentity("org.tsuyomi.wenku8", sessionId), "恢复测试", updatedAt = Instant.EPOCH)),
            shelves = emptyList(),
            readerPreferences = PortableReaderPreferences(flow = "paged"),
        )
        val bytes = ImportPlanCodec.encode(plan)
        val digest = TransferCodec.digest(bytes)
        val directory = File(context.noBackupFilesDir, "import-plans").apply { mkdirs() }
        val planFile = File(directory, "$sessionId.json").apply { writeBytes(bytes) }
        val preferenceFile = context.preferencesDataStoreFile("transfer-$sessionId")
        val dataStore = PreferenceDataStoreFactory.create { preferenceFile }
        val preferenceRepository = PortableReaderPreferencesRepository(dataStore)
        val coordinator = TransferCoordinator(context, repository, preferenceRepository) { reader, forceEInk, importDigest ->
            if (failPreferenceImports()) error("injected-preference-failure")
            preferenceRepository.applyImport(reader, forceEInk, importDigest)
        }
        return Fixture(sessionId, database, repository, plan, digest, planFile, preferenceFile, preferenceRepository, coordinator)
    }

    private fun preflightFile(prepared: PreparedExport): File = File(
        context.cacheDir,
        "transfer-export-preflight/preflight-${prepared.ownerGeneration}-${prepared.canonicalDigest}.json",
    )

    private data class Fixture(
        val sessionId: String,
        val database: TsuyomiDatabase,
        val repository: RoomTransferRepository,
        val plan: ImportPlan,
        val digest: String,
        val planFile: File,
        val preferenceFile: File,
        val preferenceRepository: PortableReaderPreferencesRepository,
        val coordinator: TransferCoordinator,
    ) {
        fun close() {
            database.close()
            planFile.delete()
            preferenceFile.delete()
        }
    }
}
