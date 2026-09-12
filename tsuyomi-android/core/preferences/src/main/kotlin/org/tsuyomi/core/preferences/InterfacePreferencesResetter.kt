/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/** Clears the shared interface-preference store only; Room, credentials, extensions and caches are untouched. */
class InterfacePreferencesResetter(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun resetToConstitutionDefaults() {
        dataStore.edit { values ->
            StringKeys.forEach(values::remove)
            BooleanKeys.forEach(values::remove)
            DoubleKeys.forEach(values::remove)
            StringSetKeys.forEach(values::remove)
        }
    }

    private companion object {
        val StringKeys = setOf(
            stringPreferencesKey("display_preference"),
            stringPreferencesKey("color_scheme_preference"),
            stringPreferencesKey("library_shortcut_order"),
            stringPreferencesKey("library_website_grouping"),
            stringPreferencesKey("library_root_nodes_v1"),
            stringPreferencesKey("library_tab_presentations_v1"),
            stringPreferencesKey("reader_flow"),
            stringPreferencesKey("reader_theme"),
        )
        val BooleanKeys = setOf(
            booleanPreferencesKey("dynamic_color_enabled"),
            booleanPreferencesKey("library_shortcut_locked"),
            booleanPreferencesKey("library_root_v1_migrated"),
            booleanPreferencesKey("library_show_updates_only"),
            booleanPreferencesKey("feature_introductions_enabled"),
            booleanPreferencesKey("reader_lock_portrait"),
            booleanPreferencesKey("reader_progress_visible"),
            booleanPreferencesKey("reader_immersive"),
            booleanPreferencesKey("reader_keep_awake"),
            booleanPreferencesKey("reader_volume_paging"),
        )
        val DoubleKeys = setOf(
            doublePreferencesKey("reader_font_scale"),
            doublePreferencesKey("reader_line_height"),
            doublePreferencesKey("reader_horizontal_margin"),
            doublePreferencesKey("reader_paragraph_spacing"),
        )
        val StringSetKeys = setOf(stringSetPreferencesKey("feature_introduction_seen_versions"))
    }
}
