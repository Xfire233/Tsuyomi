/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
            IntKeys.forEach(values::remove)
            DoubleKeys.forEach(values::remove)
            StringSetKeys.forEach(values::remove)
        }
    }

    private companion object {
        val StringKeys = setOf(
            stringPreferencesKey("display_preference"),
            stringPreferencesKey("color_scheme_preference"),
            stringPreferencesKey("cover_card_presentation_v1"),
            stringPreferencesKey("library_shortcut_order"),
            stringPreferencesKey("library_website_grouping"),
            stringPreferencesKey("library_root_nodes_v1"),
            stringPreferencesKey("library_tab_presentations_v1"),
            stringPreferencesKey("reader_flow"),
            stringPreferencesKey("reader_theme"),
            stringPreferencesKey("reader_font_family"),
            stringPreferencesKey("reader_text_alignment"),
            stringPreferencesKey("reader_foreground_color"),
            stringPreferencesKey("reader_background_color"),
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
            doublePreferencesKey("reader_letter_spacing"),
            doublePreferencesKey("reader_first_line_indent"),
            doublePreferencesKey("reader_vertical_margin"),
        )
        val IntKeys = setOf(intPreferencesKey("reader_font_weight"))
        val StringSetKeys = setOf(
            stringSetPreferencesKey("feature_introduction_seen_versions"),
            stringSetPreferencesKey("reader_flow_overrides_v1"),
        )
    }
}
