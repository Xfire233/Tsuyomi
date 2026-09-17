/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.preferences

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
import org.tsuyomi.shared.model.CoverCardPresentation

/** Durable storage boundary for the user-controlled display preferences. */
interface DisplayPreferencesRepository {
    val preferences: Flow<DisplayPreferences>

    suspend fun setDisplayPreference(preference: DisplayPreference)

    suspend fun setColorSchemePreference(preference: ColorSchemePreference)

    suspend fun setDynamicColorEnabled(enabled: Boolean)

    suspend fun setCoverCardPresentation(presentation: CoverCardPresentation)

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

    override suspend fun setCoverCardPresentation(presentation: CoverCardPresentation) {
        dataStore.edit { values ->
            val stored = values[COVER_CARD_PRESENTATION_V1]
            check(stored == null || CoverCardPresentation.entries.any { it.name == stored }) {
                "cover-presentation-not-supported"
            }
            values[COVER_CARD_PRESENTATION_V1] = presentation.name
        }
    }

    private fun toDisplayPreferences(values: Preferences): DisplayPreferences = DisplayPreferences(
        displayPreference = values[DISPLAY_PREFERENCE].toEnumOrDefault(DisplayPreference.AUTO),
        colorSchemePreference = values[COLOR_SCHEME_PREFERENCE]
            .toEnumOrDefault(ColorSchemePreference.SYSTEM),
        dynamicColorEnabled = values[DYNAMIC_COLOR_ENABLED] ?: false,
        coverCardPresentation = values[COVER_CARD_PRESENTATION_V1]
            .toEnumOrDefault(CoverCardPresentation.STANDARD),
        coverCardPresentationReadOnly = values[COVER_CARD_PRESENTATION_V1]?.let { stored ->
            CoverCardPresentation.entries.none { it.name == stored }
        } ?: false,
    )

    private companion object {
        val DISPLAY_PREFERENCE = stringPreferencesKey("display_preference")
        val COLOR_SCHEME_PREFERENCE = stringPreferencesKey("color_scheme_preference")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val COVER_CARD_PRESENTATION_V1 = stringPreferencesKey("cover_card_presentation_v1")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
