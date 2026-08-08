/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.display

/** Resolves a single immutable display environment from durable preferences and local inputs. */
class DisplayEnvironmentResolver {
    fun resolve(
        preferences: DisplayPreferences,
        classification: DeviceClassification,
        apiLevel: Int,
        systemDark: Boolean,
        reducedMotion: Boolean,
        redrawEpoch: Long,
    ): DisplayEnvironment {
        val resolution = resolveProfile(preferences.displayPreference, classification)
        val dynamicColorEligible = resolution.profile == DisplayProfile.STANDARD && apiLevel >= 31

        return DisplayEnvironment(
            preferences = preferences,
            effectiveProfile = resolution.profile,
            decisionReason = resolution.reason,
            detectedDeviceLabel = classification.deviceLabel,
            dynamicColorEligible = dynamicColorEligible,
            dynamicColorEffective = dynamicColorEligible && preferences.dynamicColorEnabled,
            effectiveDarkTheme = when (resolution.profile) {
                DisplayProfile.EINK -> false
                DisplayProfile.STANDARD -> when (preferences.colorSchemePreference) {
                    ColorSchemePreference.SYSTEM -> systemDark
                    ColorSchemePreference.LIGHT -> false
                    ColorSchemePreference.DARK -> true
                }
            },
            motionPolicy = if (resolution.profile == DisplayProfile.EINK || reducedMotion) {
                MotionPolicy.INSTANT
            } else {
                MotionPolicy.STANDARD
            },
            redrawEpoch = redrawEpoch,
        )
    }

    private fun resolveProfile(
        preference: DisplayPreference,
        classification: DeviceClassification,
    ): ProfileResolution = when (preference) {
        DisplayPreference.STANDARD -> ProfileResolution(
            profile = DisplayProfile.STANDARD,
            reason = DisplayDecisionReason.MANUAL_STANDARD,
        )
        DisplayPreference.EINK -> ProfileResolution(
            profile = DisplayProfile.EINK,
            reason = DisplayDecisionReason.MANUAL_EINK,
        )
        DisplayPreference.AUTO -> if (classification.recognizedEInk) {
            ProfileResolution(DisplayProfile.EINK, DisplayDecisionReason.RECOGNIZED_EINK)
        } else {
            ProfileResolution(DisplayProfile.STANDARD, DisplayDecisionReason.UNKNOWN_DEVICE)
        }
    }

    private data class ProfileResolution(
        val profile: DisplayProfile,
        val reason: DisplayDecisionReason,
    )
}
