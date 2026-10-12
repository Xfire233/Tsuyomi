/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.preferences

import org.tsuyomi.shared.model.CoverCardPresentation

/** The user's persisted choice for resolving the global display profile. */
enum class DisplayPreference {
    AUTO,
    STANDARD,
    EINK,
}

/** The user's persisted standard-profile color-scheme preference. */
enum class ColorSchemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Effective display preferences and compatibility state for retained values. */
data class DisplayPreferences(
    val displayPreference: DisplayPreference = DisplayPreference.AUTO,
    val colorSchemePreference: ColorSchemePreference = ColorSchemePreference.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    /** Persisted Standard grid/card presentation; E-ink uses a fixed monochrome 5:7 card. */
    val coverCardPresentation: CoverCardPresentation = CoverCardPresentation.STANDARD,
    val coverCardPresentationReadOnly: Boolean = false,
)
