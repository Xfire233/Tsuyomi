/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.app.Application
import androidx.room.Room
import org.tsuyomi.core.display.DataStoreDisplayPreferencesRepository
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.database.RoomTransferRepository
import org.tsuyomi.core.database.MIGRATION_1_2
import org.tsuyomi.core.database.TsuyomiDatabase
import org.tsuyomi.core.display.DisplayController
import org.tsuyomi.core.display.LocalDeviceClassifier
import org.tsuyomi.core.preferences.createAppPreferencesDataStore
import org.tsuyomi.core.preferences.PortableReaderPreferencesRepository

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
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    val libraryRepository: RoomLibraryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomLibraryRepository(database)
    }
    val transferRepository: RoomTransferRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomTransferRepository(database)
    }
    val readerPreferencesRepository: PortableReaderPreferencesRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PortableReaderPreferencesRepository(preferencesDataStore)
    }
}
