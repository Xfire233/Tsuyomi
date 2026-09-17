/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.display

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tsuyomi.core.preferences.ColorSchemePreference
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.preferences.DisplayPreferencesRepository
import org.tsuyomi.shared.model.CoverCardPresentation

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


    private class FakeRepository : DisplayPreferencesRepository {
        override val preferences: Flow<DisplayPreferences> = flowOf(DisplayPreferences())

        override suspend fun setDisplayPreference(preference: DisplayPreference): Unit =
            error("A redraw must not change persisted preferences")

        override suspend fun setColorSchemePreference(preference: ColorSchemePreference): Unit =
            error("A redraw must not change persisted preferences")

        override suspend fun setDynamicColorEnabled(enabled: Boolean): Unit =
            error("A redraw must not change persisted preferences")

        override suspend fun setCoverCardPresentation(presentation: CoverCardPresentation): Unit =
            error("A redraw must not change persisted preferences")
    }
}
