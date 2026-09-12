/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPreferencesRepositoryTest {
    @Test
    fun legacyMigrationIsAtomicIdempotentAndSeedsFixedTabs() = runBlocking {
        val sourceId = "fixture.source"
        val mirrorId = "mirror:${sourceId.length}:$sourceId"
        val folderId = "mirror-folder:${sourceId.length}:$sourceId${"favorites".length}:favorites"
        val store = InMemoryPreferencesDataStore(
            mutablePreferencesOf(
                LegacyShortcutOrder to encodeLegacyOrder(
                    listOf(
                        "continue",
                        "book:fixture",
                        "collection:manual",
                        mirrorId,
                        folderId,
                        "recent",
                        "updates",
                    ),
                ),
                LegacyShortcutLocked to true,
            ),
        )
        val repository = LibraryPreferencesRepository(store)
        val standardTabs = mapOf(
            "STANDARD:ALL" to LibraryTabPresentationPreferences(sortMode = "SMART"),
            "STANDARD:CONTINUE" to LibraryTabPresentationPreferences(sortMode = "RECENT", sortDescending = true),
            "STANDARD:READ_LATER" to LibraryTabPresentationPreferences(sortMode = "ADDED", sortDescending = true),
        )

        repository.migrateLegacyRootPresentation(standardTabs)

        val migrated = repository.preferences.first()
        assertEquals(listOf("collection:manual", mirrorId), migrated.rootNodes.map { it.id })
        assertEquals(standardTabs, migrated.tabPresentations)
        assertTrue(migrated.websiteGroupingBySource.getValue(sourceId))
        assertNull(store.current()[LegacyShortcutOrder])
        assertNull(store.current()[LegacyShortcutLocked])
        assertTrue(store.current()[RootMigrationComplete] == true)

        val einkTab = mapOf("EINK:ALL" to LibraryTabPresentationPreferences(layout = "LIST"))
        repository.migrateLegacyRootPresentation(einkTab)

        val repeated = repository.preferences.first()
        assertEquals(listOf("collection:manual", mirrorId), repeated.rootNodes.map { it.id })
        assertEquals(LibraryTabPresentationPreferences(layout = "LIST"), repeated.tabPresentations["EINK:ALL"])
        assertEquals(standardTabs["STANDARD:ALL"], repeated.tabPresentations["STANDARD:ALL"])
    }

    @Test
    fun typedRootAndTabPresentationsRoundTripWithoutCrossContamination() = runBlocking {
        val repository = LibraryPreferencesRepository(InMemoryPreferencesDataStore())
        val roots = listOf(
            LibraryRootNodePreference("collection:first", 0),
            LibraryRootNodePreference("mirror:6:source", 3),
        )
        val shelf = LibraryTabPresentationPreferences("COMPACT", "TITLE", true, 8, 24)
        val continueReading = LibraryTabPresentationPreferences("LIST", "RECENT", false, 2, 10)

        repository.updateRootNodes(roots)
        repository.updateTabPresentation("STANDARD:ALL", shelf)
        repository.updateTabPresentation("STANDARD:CONTINUE", continueReading)
        repository.updateShowUpdatesOnly(true)

        val restored = repository.preferences.first()
        assertEquals(roots, restored.rootNodes)
        assertEquals(shelf, restored.tabPresentations["STANDARD:ALL"])
        assertEquals(continueReading, restored.tabPresentations["STANDARD:CONTINUE"])
        assertFalse(restored.tabPresentations.containsKey("STANDARD:READ_LATER"))
        assertTrue(restored.showUpdatesOnly)
    }

    private companion object {
        val LegacyShortcutOrder = stringPreferencesKey("library_shortcut_order")
        val LegacyShortcutLocked = booleanPreferencesKey("library_shortcut_locked")
        val RootMigrationComplete = booleanPreferencesKey("library_root_v1_migrated")
    }
}

private class InMemoryPreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val values = MutableStateFlow(initial)
    override val data: Flow<Preferences> = values

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(values.value)
        values.value = updated
        return updated
    }

    fun current(): Preferences = values.value
}


private fun encodeLegacyOrder(ids: List<String>): String = buildString {
    ids.forEach { id ->
        append(id.length)
        append(':')
        append(id)
    }
}
