/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.display

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreDisplayPreferencesRepositoryTest {
    @Test
    fun persistedPreferencesRoundTripThroughDataStore() = runBlocking {
        val repository = DataStoreDisplayPreferencesRepository(InMemoryPreferencesDataStore())

        repository.setDisplayPreference(DisplayPreference.EINK)
        repository.setColorSchemePreference(ColorSchemePreference.DARK)
        repository.setDynamicColorEnabled(true)

        assertEquals(
            DisplayPreferences(
                displayPreference = DisplayPreference.EINK,
                colorSchemePreference = ColorSchemePreference.DARK,
                dynamicColorEnabled = true,
            ),
            repository.preferences.first(),
        )
    }
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val values = MutableStateFlow<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = values

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(values.value)
        values.value = updated
        return updated
    }
}
