/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class FeatureIntroductionPreferences(
    val enabled: Boolean = true,
    val seenVersions: Set<String> = emptySet(),
)

class FeatureIntroductionPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<FeatureIntroductionPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { values ->
            FeatureIntroductionPreferences(
                enabled = values[ENABLED] ?: true,
                seenVersions = values[SEEN_VERSIONS].orEmpty(),
            )
        }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun markSeen(id: String, version: Int) {
        require(id.matches(Regex("^[a-z0-9-]{1,64}$")))
        require(version > 0)
        dataStore.edit { values ->
            values[SEEN_VERSIONS] = values[SEEN_VERSIONS].orEmpty() + "$id:$version"
        }
    }

    suspend fun resetSeenVersions() {
        dataStore.edit { it.remove(SEEN_VERSIONS) }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("feature_introductions_enabled")
        val SEEN_VERSIONS = stringSetPreferencesKey("feature_introduction_seen_versions")
    }
}
