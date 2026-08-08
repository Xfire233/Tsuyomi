/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.display

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayControllerTest {
    @Test
    fun redrawEpochAdvancesForEveryRequest() {
        val controller = DisplayController(FakeRepository()) {
            DeviceClassification(recognizedEInk = false, deviceLabel = null)
        }

        controller.requestRedraw()
        controller.requestRedraw()

        assertEquals(2L, controller.redrawEpoch.value)
    }

    @Test
    fun settersDelegateToDurableRepository() = runBlocking {
        val repository = FakeRepository()
        val controller = DisplayController(repository) {
            DeviceClassification(recognizedEInk = false, deviceLabel = null)
        }

        controller.setDisplayPreference(DisplayPreference.EINK)
        controller.setColorSchemePreference(ColorSchemePreference.DARK)
        controller.setDynamicColorEnabled(true)

        assertEquals(
            DisplayPreferences(
                displayPreference = DisplayPreference.EINK,
                colorSchemePreference = ColorSchemePreference.DARK,
                dynamicColorEnabled = true,
            ),
            repository.current.value,
        )
    }

    private class FakeRepository : DisplayPreferencesRepository {
        val current = MutableStateFlow(DisplayPreferences())
        override val preferences: Flow<DisplayPreferences> = current

        override suspend fun setDisplayPreference(preference: DisplayPreference) {
            current.update { it.copy(displayPreference = preference) }
        }

        override suspend fun setColorSchemePreference(preference: ColorSchemePreference) {
            current.update { it.copy(colorSchemePreference = preference) }
        }

        override suspend fun setDynamicColorEnabled(enabled: Boolean) {
            current.update { it.copy(dynamicColorEnabled = enabled) }
        }

    }
}
