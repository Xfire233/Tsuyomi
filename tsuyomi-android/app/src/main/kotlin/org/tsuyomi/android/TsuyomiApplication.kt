/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.app.Application
import androidx.room.Room
import org.tsuyomi.core.display.DataStoreDisplayPreferencesRepository
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.database.TsuyomiDatabase
import org.tsuyomi.core.display.DisplayController
import org.tsuyomi.core.display.LocalDeviceClassifier
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

    val libraryRepository: RoomLibraryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomLibraryRepository(
            Room.databaseBuilder(applicationContext, TsuyomiDatabase::class.java, "tsuyomi.db").build(),
        )
    }
}
