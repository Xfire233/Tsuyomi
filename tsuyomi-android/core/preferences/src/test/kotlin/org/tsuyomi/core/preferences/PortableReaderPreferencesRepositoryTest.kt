/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.model.BookIdentity

class PortableReaderPreferencesRepositoryTest {
    @Test
    fun full_reader_typography_round_trips_and_requested_flow_overrides_fall_back_to_global_default() = runBlocking {
        val repository = PortableReaderPreferencesRepository(InMemoryReaderPreferencesDataStore())
        val global = PortableReaderPreferences(
            flow = "dual",
            fontFamily = "serif",
            fontWeight = 500,
            letterSpacing = 0.1,
            firstLineIndent = 1.25,
            verticalMargin = 32.0,
            textAlignment = "justify",
            foregroundColor = "#112233",
            backgroundColor = "#AABBCC",
        )
        val identity = BookIdentity("fixture.source", "flow-override")

        repository.update(global)
        repository.preferences.first().also { persisted ->
            assertEquals("dual", persisted.flow)
            assertEquals("serif", persisted.fontFamily)
            assertEquals(500, persisted.fontWeight)
            assertEquals(0.1, persisted.letterSpacing)
            assertEquals(1.25, persisted.firstLineIndent)
            assertEquals(32.0, persisted.verticalMargin)
            assertEquals("justify", persisted.textAlignment)
            assertEquals("#112233", persisted.foregroundColor)
            assertEquals("#AABBCC", persisted.backgroundColor)
        }
        assertEquals(RequestedReaderFlow("dual", false), repository.requestedFlow(identity, global).first())

        repository.setFlowOverride(identity, "paged")
        assertEquals(RequestedReaderFlow("paged", true), repository.requestedFlow(identity, global).first())
        val latestGlobal = repository.preferences.first()
        assertEquals("paged", latestGlobal.flow)
        assertEquals(
            RequestedReaderFlow("paged", false),
            repository.requestedFlow(BookIdentity("other.source", "flow-override"), latestGlobal).first(),
        )

        repository.setFlowOverride(identity, null)
        val restored = repository.requestedFlow(identity, repository.preferences.first()).first()
        assertEquals("paged", restored.flow)
        assertFalse(restored.overridden)
        assertTrue(repository.preferences.first().foregroundColor != null)
    }
    @Test
    fun interface_reset_clears_reader_overrides_and_all_reader_presentation_keys() = runBlocking {
        val store = InMemoryReaderPreferencesDataStore()
        val repository = PortableReaderPreferencesRepository(store)
        val identity = BookIdentity("fixture.source", "reset-flow")
        repository.update(
            PortableReaderPreferences(
                flow = "dual",
                fontFamily = "monospace",
                fontWeight = 500,
                letterSpacing = 0.1,
                firstLineIndent = 2.0,
                verticalMargin = 48.0,
                textAlignment = "center",
                foregroundColor = "#112233",
                backgroundColor = "#AABBCC",
            ),
        )
        repository.setFlowOverride(identity, "paged")

        InterfacePreferencesResetter(store).resetToConstitutionDefaults()

        val reset = repository.preferences.first()
        assertEquals("scroll", reset.flow)
        assertEquals("system", reset.fontFamily)
        assertEquals(400, reset.fontWeight)
        assertEquals(0.0, reset.letterSpacing)
        assertEquals(0.0, reset.firstLineIndent)
        assertEquals(24.0, reset.verticalMargin)
        assertEquals("start", reset.textAlignment)
        assertEquals(null, reset.foregroundColor)
        assertEquals(null, reset.backgroundColor)
        assertEquals(RequestedReaderFlow("scroll", false), repository.requestedFlow(identity, reset).first())
    }
}

private class InMemoryReaderPreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val values = MutableStateFlow(initial)
    override val data: Flow<Preferences> = values

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(values.value)
        values.value = updated
        return updated
    }
}
