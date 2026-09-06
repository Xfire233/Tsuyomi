/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterfacePreferencesResetInstrumentedTest {
    @Test
    fun reset_removes_only_interface_keys() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as TsuyomiApplication
        val dataStore = application.preferencesDataStore
        val original = dataStore.data.first()
        val displayKey = stringPreferencesKey("display_preference")
        val sourceKey = stringPreferencesKey("source_flow_source_id")
        val digestKey = stringPreferencesKey("last_applied_import_digest")
        try {
            dataStore.edit { values ->
                values[displayKey] = "EINK"
                values[sourceKey] = "org.tsuyomi.wenku8"
                values[digestKey] = "a".repeat(64)
            }

            application.interfacePreferencesResetter.resetToConstitutionDefaults()

            val reset = dataStore.data.first()
            assertNull(reset[displayKey])
            assertEquals("org.tsuyomi.wenku8", reset[sourceKey])
            assertEquals("a".repeat(64), reset[digestKey])
        } finally {
            dataStore.updateData { original }
        }
    }
}
