/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val APP_PREFERENCES_DATASTORE_NAME = "tsuyomi_preferences"

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = APP_PREFERENCES_DATASTORE_NAME,
)

/** Returns the process-wide DataStore used for non-secret application preferences. */
fun createAppPreferencesDataStore(context: Context): DataStore<Preferences> =
    context.applicationContext.appPreferencesDataStore
