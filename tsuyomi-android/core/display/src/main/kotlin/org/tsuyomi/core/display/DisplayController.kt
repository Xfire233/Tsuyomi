/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.display

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Coordinates durable preference mutations, fixed local classification, and root redraw requests.
 * It contains no vendor-specific display APIs.
 */
class DisplayController(
    private val repository: DisplayPreferencesRepository,
    classifier: DeviceClassifier,
) {
    val preferences: Flow<DisplayPreferences> = repository.preferences
    val classification: DeviceClassification = classifier.classify()

    private val mutableRedrawEpoch = MutableStateFlow(0L)
    val redrawEpoch: StateFlow<Long> = mutableRedrawEpoch.asStateFlow()

    suspend fun setDisplayPreference(preference: DisplayPreference) =
        repository.setDisplayPreference(preference)

    suspend fun setColorSchemePreference(preference: ColorSchemePreference) =
        repository.setColorSchemePreference(preference)

    suspend fun setDynamicColorEnabled(enabled: Boolean) =
        repository.setDynamicColorEnabled(enabled)


    /** Requests a fresh stable-root composition; this is not a hardware panel refresh command. */
    fun requestRedraw() {
        mutableRedrawEpoch.update { epoch ->
            check(epoch != Long.MAX_VALUE) { "Display redraw epoch exhausted" }
            epoch + 1
        }
    }
}
