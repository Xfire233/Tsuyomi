/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.tsuyomi.shared.backup.PortableReaderPreferences

class PortableReaderPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<PortableReaderPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { values ->
            PortableReaderPreferences(
                flow = values[FLOW] ?: "scroll",
                fontScale = values[FONT_SCALE] ?: 1.0,
                lineHeight = values[LINE_HEIGHT] ?: 1.5,
                theme = values[THEME] ?: "paper",
                horizontalMargin = values[HORIZONTAL_MARGIN] ?: 24.0,
                paragraphSpacing = values[PARAGRAPH_SPACING] ?: 12.0,
                lockPortrait = values[LOCK_PORTRAIT] ?: false,
                progressVisible = values[PROGRESS_VISIBLE] ?: true,
                immersive = values[IMMERSIVE] ?: false,
                keepAwake = values[KEEP_AWAKE] ?: true,
                volumePaging = values[VOLUME_PAGING] ?: true,
            )
        }

    val lastAppliedImportDigest: Flow<String?> = dataStore.data.map { it[LAST_APPLIED_IMPORT_DIGEST] }

    suspend fun update(preferences: PortableReaderPreferences) {
        dataStore.edit { values -> applyReader(values, preferences) }
    }

    suspend fun applyImport(
        preferences: PortableReaderPreferences?,
        forceManualEInk: Boolean,
        digest: String,
    ) {
        dataStore.edit { values ->
            preferences?.let { applyReader(values, it) }
            if (forceManualEInk) values[DISPLAY_PREFERENCE] = "EINK"
            values[LAST_APPLIED_IMPORT_DIGEST] = digest
        }
    }

    private fun applyReader(values: androidx.datastore.preferences.core.MutablePreferences, preferences: PortableReaderPreferences) {
        preferences.flow?.let { require(it in setOf("scroll", "paged")); values[FLOW] = it }
        preferences.fontScale?.let { require(it in 0.5..3.0); values[FONT_SCALE] = it }
        preferences.lineHeight?.let { require(it in 0.8..3.0); values[LINE_HEIGHT] = it }
        preferences.theme?.let { require(it in THEMES); values[THEME] = it }
        preferences.horizontalMargin?.let { require(it in 12.0..40.0); values[HORIZONTAL_MARGIN] = it }
        preferences.paragraphSpacing?.let { require(it in 0.0..32.0); values[PARAGRAPH_SPACING] = it }
        preferences.lockPortrait?.let { values[LOCK_PORTRAIT] = it }
        preferences.progressVisible?.let { values[PROGRESS_VISIBLE] = it }
        preferences.immersive?.let { values[IMMERSIVE] = it }
        preferences.keepAwake?.let { values[KEEP_AWAKE] = it }
        preferences.volumePaging?.let { values[VOLUME_PAGING] = it }
    }

    private companion object {
        val FLOW = stringPreferencesKey("reader_flow")
        val FONT_SCALE = doublePreferencesKey("reader_font_scale")
        val LINE_HEIGHT = doublePreferencesKey("reader_line_height")
        val THEME = stringPreferencesKey("reader_theme")
        val HORIZONTAL_MARGIN = doublePreferencesKey("reader_horizontal_margin")
        val PARAGRAPH_SPACING = doublePreferencesKey("reader_paragraph_spacing")
        val LOCK_PORTRAIT = booleanPreferencesKey("reader_lock_portrait")
        val PROGRESS_VISIBLE = booleanPreferencesKey("reader_progress_visible")
        val IMMERSIVE = booleanPreferencesKey("reader_immersive")
        val KEEP_AWAKE = booleanPreferencesKey("reader_keep_awake")
        val VOLUME_PAGING = booleanPreferencesKey("reader_volume_paging")
        val DISPLAY_PREFERENCE = stringPreferencesKey("display_preference")
        val LAST_APPLIED_IMPORT_DIGEST = stringPreferencesKey("last_applied_import_digest")
        val THEMES = setOf("paper", "warmGray", "nightInk", "black", "inkGreen")
    }
}
