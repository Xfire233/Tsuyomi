/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.display

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Durable storage boundary for the user-controlled display preferences. */
interface DisplayPreferencesRepository {
    val preferences: Flow<DisplayPreferences>

    suspend fun setDisplayPreference(preference: DisplayPreference)

    suspend fun setColorSchemePreference(preference: ColorSchemePreference)

    suspend fun setDynamicColorEnabled(enabled: Boolean)

}

/** DataStore-backed implementation of [DisplayPreferencesRepository]. */
class DataStoreDisplayPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : DisplayPreferencesRepository {
    override val preferences: Flow<DisplayPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map(::toDisplayPreferences)

    override suspend fun setDisplayPreference(preference: DisplayPreference) {
        dataStore.edit { it[DISPLAY_PREFERENCE] = preference.name }
    }

    override suspend fun setColorSchemePreference(preference: ColorSchemePreference) {
        dataStore.edit { it[COLOR_SCHEME_PREFERENCE] = preference.name }
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR_ENABLED] = enabled }
    }


    private fun toDisplayPreferences(values: Preferences): DisplayPreferences = DisplayPreferences(
        displayPreference = values[DISPLAY_PREFERENCE].toEnumOrDefault(DisplayPreference.AUTO),
        colorSchemePreference = values[COLOR_SCHEME_PREFERENCE]
            .toEnumOrDefault(ColorSchemePreference.SYSTEM),
        dynamicColorEnabled = values[DYNAMIC_COLOR_ENABLED] ?: false,
    )

    private companion object {
        val DISPLAY_PREFERENCE = stringPreferencesKey("display_preference")
        val COLOR_SCHEME_PREFERENCE = stringPreferencesKey("color_scheme_preference")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
