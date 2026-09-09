/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.app.Application
import android.content.BroadcastReceiver
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import androidx.work.WorkManager
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.tsuyomi.core.database.MIGRATION_1_2
import org.tsuyomi.core.database.MIGRATION_2_3
import org.tsuyomi.core.database.MIGRATION_3_4
import org.tsuyomi.core.database.MIGRATION_4_5
import org.tsuyomi.core.database.MIGRATION_5_6
import org.tsuyomi.core.database.MIGRATION_6_7
import org.tsuyomi.core.database.MIGRATION_7_8
import org.tsuyomi.core.database.MIGRATION_8_9
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.database.RoomTransferRepository
import org.tsuyomi.core.database.TsuyomiDatabase
import org.tsuyomi.core.display.DataStoreDisplayPreferencesRepository
import org.tsuyomi.core.display.DisplayController
import org.tsuyomi.core.display.LocalDeviceClassifier
import org.tsuyomi.core.library.UpdateCoordinator
import org.tsuyomi.shared.librarydomain.UpdateSessionLease
import org.tsuyomi.core.preferences.FeatureIntroductionPreferencesRepository
import org.tsuyomi.core.preferences.InterfacePreferencesResetter
import org.tsuyomi.core.preferences.LibraryPreferencesRepository
import org.tsuyomi.core.preferences.PortableReaderPreferencesRepository
import org.tsuyomi.core.preferences.createAppPreferencesDataStore

class TsuyomiApplication : Application() {
    val preferencesDataStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createAppPreferencesDataStore(applicationContext)
    }
    val displayController: DisplayController by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DisplayController(
            repository = DataStoreDisplayPreferencesRepository(preferencesDataStore),
            classifier = LocalDeviceClassifier(),
        )
    }

    private val database: TsuyomiDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Room.databaseBuilder(applicationContext, TsuyomiDatabase::class.java, "tsuyomi.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
            )
            .build()
    }
    @get:VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val databaseForTesting: TsuyomiDatabase
        get() = database
    val libraryRepository: RoomLibraryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomLibraryRepository(database)
    }
    val transferRepository: RoomTransferRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomTransferRepository(database)
    }
    val libraryPreferencesRepository: LibraryPreferencesRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LibraryPreferencesRepository(preferencesDataStore)
    }
    val readerPreferencesRepository: PortableReaderPreferencesRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PortableReaderPreferencesRepository(preferencesDataStore)
    }
    val featureIntroductionPreferencesRepository: FeatureIntroductionPreferencesRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FeatureIntroductionPreferencesRepository(preferencesDataStore)
    }
    val interfacePreferencesResetter: InterfacePreferencesResetter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        InterfacePreferencesResetter(preferencesDataStore)
    }
    private val updateRuntime: UpdateRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        UpdateRuntime(applicationContext, database, libraryRepository)
    }
    val updateCoordinator: UpdateCoordinator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        updateRuntime.coordinator
    }
    val updateScheduler: UpdateScheduler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        updateRuntime.scheduler
    }
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        updateScope.launch { updateRuntime.restore() }
    }


    internal fun cancelUpdatesFromNotification(
        expectedSessionId: String,
        expectedOwnerToken: String,
        expectedLeaseExpiresAt: Long,
        workId: UUID,
        pendingResult: BroadcastReceiver.PendingResult,
    ) {
        updateScope.launch {
            try {
                val lease = UpdateSessionLease(
                    id = expectedSessionId,
                    ownerToken = expectedOwnerToken,
                    expiresAt = expectedLeaseExpiresAt,
                )
                if (updateCoordinator.cancel(lease)) {
                    WorkManager.getInstance(applicationContext).cancelWorkById(workId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
