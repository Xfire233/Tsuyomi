/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.tsuyomi.core.preferences.ColorSchemePreference
import org.tsuyomi.core.preferences.DataStoreDisplayPreferencesRepository
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.preferences.InterfacePreferencesResetter
import org.tsuyomi.shared.model.CoverCardPresentation

@RunWith(AndroidJUnit4::class)
class DisplayPreferencesPersistenceInstrumentedTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun persistedPreferencesSurviveClosingAndReopeningTheDataStore() = runBlocking {
        val file = File(temporaryFolder.root, "display.preferences_pb")
        withDiskStore(file) { store ->
            val repository = DataStoreDisplayPreferencesRepository(store)
            repository.setDisplayPreference(DisplayPreference.STANDARD)
            repository.setColorSchemePreference(ColorSchemePreference.DARK)
            repository.setDynamicColorEnabled(true)
            repository.setCoverCardPresentation(CoverCardPresentation.WIDE)
        }
        withDiskStore(file) { store ->
            assertEquals(
                DisplayPreferences(
                    displayPreference = DisplayPreference.STANDARD,
                    colorSchemePreference = ColorSchemePreference.DARK,
                    dynamicColorEnabled = true,
                    coverCardPresentation = CoverCardPresentation.WIDE,
                ),
                DataStoreDisplayPreferencesRepository(store).preferences.first(),
            )
        }
    }

    @Test
    fun unsupportedCoverPresentationPreservesStoredBytesUntilExplicitReset() = runBlocking {
        val file = File(temporaryFolder.root, "display.preferences_pb")
        withDiskStore(file) { store ->
            store.edit { it[stringPreferencesKey("cover_card_presentation_v1")] = "FUTURE" }
        }
        val originalBytes = file.readBytes()
        withDiskStore(file) { store ->
            val repository = DataStoreDisplayPreferencesRepository(store)
            val effective = repository.preferences.first()
            assertEquals(CoverCardPresentation.STANDARD, effective.coverCardPresentation)
            assertTrue(effective.coverCardPresentationReadOnly)
            try {
                repository.setCoverCardPresentation(CoverCardPresentation.WIDE)
                fail("Unsupported cover preferences must not be overwritten")
            } catch (_: IllegalStateException) {
                assertArrayEquals(originalBytes, file.readBytes())
            }
            InterfacePreferencesResetter(store).resetToConstitutionDefaults()
            assertFalse(repository.preferences.first().coverCardPresentationReadOnly)
            assertEquals(CoverCardPresentation.STANDARD, repository.preferences.first().coverCardPresentation)
            repository.setCoverCardPresentation(CoverCardPresentation.WIDE)
        }
        withDiskStore(file) { store ->
            assertEquals(
                CoverCardPresentation.WIDE,
                DataStoreDisplayPreferencesRepository(store).preferences.first().coverCardPresentation,
            )
        }
    }

    @Test
    fun interfaceResetRestoresTheDefaultCoverPresentation() = runBlocking {
        val file = File(temporaryFolder.root, "display.preferences_pb")
        withDiskStore(file) { store ->
            val repository = DataStoreDisplayPreferencesRepository(store)
            repository.setCoverCardPresentation(CoverCardPresentation.WIDE)
            InterfacePreferencesResetter(store).resetToConstitutionDefaults()
        }
        withDiskStore(file) { store ->
            assertEquals(
                CoverCardPresentation.STANDARD,
                DataStoreDisplayPreferencesRepository(store).preferences.first().coverCardPresentation,
            )
        }
    }

    private suspend fun withDiskStore(file: File, block: suspend (DataStore<Preferences>) -> Unit) {
        val job = SupervisorJob()
        try {
            block(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(Dispatchers.IO + job),
                    produceFile = { file },
                ),
            )
        } finally {
            job.cancelAndJoin()
        }
    }
}
