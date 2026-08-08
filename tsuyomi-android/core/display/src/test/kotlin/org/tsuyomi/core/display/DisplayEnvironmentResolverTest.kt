/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayEnvironmentResolverTest {
    private val resolver = DisplayEnvironmentResolver()
    private val recognized = DeviceClassification(recognizedEInk = true, deviceLabel = "BOOX")
    private val unknown = DeviceClassification(recognizedEInk = false, deviceLabel = null)

    @Test
    fun manualProfileTakesPriorityOverRecognition() {
        val environment = resolve(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.STANDARD),
            classification = recognized,
        )

        assertEquals(DisplayProfile.STANDARD, environment.effectiveProfile)
        assertEquals(DisplayDecisionReason.MANUAL_STANDARD, environment.decisionReason)
    }

    @Test
    fun autoUnknownDeviceFallsBackToStandard() {
        val environment = resolve(
            preferences = DisplayPreferences(displayPreference = DisplayPreference.AUTO),
            classification = unknown,
        )

        assertEquals(DisplayProfile.STANDARD, environment.effectiveProfile)
        assertEquals(DisplayDecisionReason.UNKNOWN_DEVICE, environment.decisionReason)
        assertEquals(null, environment.detectedDeviceLabel)
    }

    @Test
    fun dynamicColorRequiresStandardProfileAndroid12AndPersistedOptIn() {
        val enabled = DisplayPreferences(dynamicColorEnabled = true)

        val standardOnAndroid12 = resolve(enabled, unknown, apiLevel = 31)
        val standardOnAndroid11 = resolve(enabled, unknown, apiLevel = 30)
        val einkOnAndroid12 = resolve(
            enabled.copy(displayPreference = DisplayPreference.EINK),
            unknown,
            apiLevel = 31,
        )

        assertTrue(standardOnAndroid12.dynamicColorEligible)
        assertTrue(standardOnAndroid12.dynamicColorEffective)
        assertFalse(standardOnAndroid11.dynamicColorEligible)
        assertFalse(standardOnAndroid11.dynamicColorEffective)
        assertFalse(einkOnAndroid12.dynamicColorEligible)
        assertFalse(einkOnAndroid12.dynamicColorEffective)
    }

    @Test
    fun standardThemePreferenceIsRestoredAfterEink() {
        val persisted = DisplayPreferences(
            displayPreference = DisplayPreference.EINK,
            colorSchemePreference = ColorSchemePreference.DARK,
        )

        val eink = resolve(persisted, unknown)
        val restored = resolve(persisted.copy(displayPreference = DisplayPreference.STANDARD), unknown)

        assertFalse(eink.effectiveDarkTheme)
        assertTrue(restored.effectiveDarkTheme)
    }

    @Test
    fun einkAndReducedMotionUseInstantMotion() {
        assertEquals(
            MotionPolicy.INSTANT,
            resolve(DisplayPreferences(displayPreference = DisplayPreference.EINK), unknown).motionPolicy,
        )
        assertEquals(
            MotionPolicy.INSTANT,
            resolve(DisplayPreferences(), unknown, reducedMotion = true).motionPolicy,
        )
        assertEquals(
            MotionPolicy.STANDARD,
            resolve(DisplayPreferences(), unknown, reducedMotion = false).motionPolicy,
        )
    }

    private fun resolve(
        preferences: DisplayPreferences,
        classification: DeviceClassification,
        apiLevel: Int = 36,
        reducedMotion: Boolean = false,
    ): DisplayEnvironment = resolver.resolve(
        preferences = preferences,
        classification = classification,
        apiLevel = apiLevel,
        systemDark = false,
        reducedMotion = reducedMotion,
        redrawEpoch = 0,
    )
}
