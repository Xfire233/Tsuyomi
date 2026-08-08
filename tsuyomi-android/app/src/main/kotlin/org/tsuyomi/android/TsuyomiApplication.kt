/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.app.Application
import org.tsuyomi.core.display.DataStoreDisplayPreferencesRepository
import org.tsuyomi.core.display.DisplayController
import org.tsuyomi.core.display.LocalDeviceClassifier
import org.tsuyomi.core.preferences.createAppPreferencesDataStore

class TsuyomiApplication : Application() {
    val displayController: DisplayController by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DisplayController(
            repository = DataStoreDisplayPreferencesRepository(
                createAppPreferencesDataStore(applicationContext),
            ),
            classifier = LocalDeviceClassifier(),
        )
    }
}
