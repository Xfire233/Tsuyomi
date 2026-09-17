/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.display

import org.tsuyomi.core.preferences.DisplayPreferences


/** The resolved global display profile consumed by the UI. */
enum class DisplayProfile {
    STANDARD,
    EINK,
}



/** Why the resolver selected the current effective display profile. */
enum class DisplayDecisionReason {
    MANUAL_STANDARD,
    MANUAL_EINK,
    RECOGNIZED_EINK,
    UNKNOWN_DEVICE,
}


/** A local-only device-classification result. This value is never persisted or uploaded. */
data class DeviceClassification(
    val recognizedEInk: Boolean,
    val deviceLabel: String?,
)

/** Ephemeral platform state sampled together for one display-environment resolution. */
data class DisplaySystemState(
    val apiLevel: Int,
    val systemDark: Boolean,
    val reducedMotion: Boolean,
    val redrawEpoch: Long,
)

/** Motion behavior that semantic UI components must use for a composition snapshot. */
enum class MotionPolicy {
    STANDARD,
    INSTANT,
}

/**
 * Immutable, root-provided display state. Effective values are derived from persisted preferences
 * and local runtime inputs; only [preferences] is durable.
 */
data class DisplayEnvironment(
    val preferences: DisplayPreferences,
    val effectiveProfile: DisplayProfile,
    val decisionReason: DisplayDecisionReason,
    val detectedDeviceLabel: String?,
    val dynamicColorEligible: Boolean,
    val dynamicColorEffective: Boolean,
    val effectiveDarkTheme: Boolean,
    val motionPolicy: MotionPolicy,
    val redrawEpoch: Long,
)
