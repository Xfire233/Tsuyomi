/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Opaque neutral ramp mandated for the E-ink display profile. */
object TsuyomiEInkPalette {
    val Ink = Color(0xFF000000)
    val N90 = Color(0xFF1A1A1A)
    val N70 = Color(0xFF4D4D4D)
    val N50 = Color(0xFF808080)
    val N30 = Color(0xFFB3B3B3)
    val Paper = Color(0xFFFFFFFF)
}

/** Standard light scheme: warm paper background with ink-teal primary. Never pure black/white. */
val TsuyomiLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2E4A56),
    onPrimary = Color(0xFFFDFCF9),
    primaryContainer = Color(0xFFD3E0E5),
    onPrimaryContainer = Color(0xFF16303B),
    secondary = Color(0xFF55666E),
    onSecondary = Color(0xFFFDFCF9),
    secondaryContainer = Color(0xFFDDE5E8),
    onSecondaryContainer = Color(0xFF2A3A41),
    tertiary = Color(0xFF6A6055),
    onTertiary = Color(0xFFFDFCF9),
    tertiaryContainer = Color(0xFFEFE7DC),
    onTertiaryContainer = Color(0xFF332E26),
    error = Color(0xFFA64445),
    onError = Color(0xFFFDFCF9),
    errorContainer = Color(0xFFF6E0DE),
    onErrorContainer = Color(0xFF5C1A1C),
    background = Color(0xFFFAF8F3),
    onBackground = Color(0xFF1F2A2F),
    surface = Color(0xFFFDFCF9),
    onSurface = Color(0xFF1F2A2F),
    surfaceVariant = Color(0xFFEAE6DE),
    onSurfaceVariant = Color(0xFF4A545A),
    outline = Color(0xFF6E787E),
    outlineVariant = Color(0xFFCFCBC2),
    scrim = Color(0xFF25333A),
    inverseSurface = Color(0xFF2B3337),
    inverseOnSurface = Color(0xFFEDEAE4),
    inversePrimary = Color(0xFF9DB9C5),
    surfaceDim = Color(0xFFDCD9D1),
    surfaceBright = Color(0xFFFDFCF9),
    surfaceContainerLowest = Color(0xFFFDFCF9),
    surfaceContainerLow = Color(0xFFF5F3EE),
    surfaceContainer = Color(0xFFF0EDE7),
    surfaceContainerHigh = Color(0xFFEAE7E0),
    surfaceContainerHighest = Color(0xFFE4E1D9),
)

/** Standard dark scheme. Never pure black/white. */
val TsuyomiDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA9C6D2),
    onPrimary = Color(0xFF12262E),
    primaryContainer = Color(0xFF31454F),
    onPrimaryContainer = Color(0xFFD2E4EC),
    secondary = Color(0xFF93A8B0),
    onSecondary = Color(0xFF16262C),
    secondaryContainer = Color(0xFF2E3E45),
    onSecondaryContainer = Color(0xFFC9D8DE),
    tertiary = Color(0xFFB0A696),
    onTertiary = Color(0xFF262019),
    tertiaryContainer = Color(0xFF3E382F),
    onTertiaryContainer = Color(0xFFDDD3C5),
    error = Color(0xFFE8A9A5),
    onError = Color(0xFF4A1513),
    errorContainer = Color(0xFF6E2B28),
    onErrorContainer = Color(0xFFF6DEDD),
    background = Color(0xFF151A1C),
    onBackground = Color(0xFFDDE3E5),
    surface = Color(0xFF1C2225),
    onSurface = Color(0xFFDDE3E5),
    surfaceVariant = Color(0xFF333D41),
    onSurfaceVariant = Color(0xFFAEB9BD),
    outline = Color(0xFF7E8A8F),
    outlineVariant = Color(0xFF3A4448),
    scrim = Color(0xFF0B1012),
    inverseSurface = Color(0xFFDDE3E5),
    inverseOnSurface = Color(0xFF232B2E),
    inversePrimary = Color(0xFF2E4A56),
    surfaceDim = Color(0xFF101415),
    surfaceBright = Color(0xFF353E42),
    surfaceContainerLowest = Color(0xFF101415),
    surfaceContainerLow = Color(0xFF181E20),
    surfaceContainer = Color(0xFF1C2225),
    surfaceContainerHigh = Color(0xFF242B2E),
    surfaceContainerHighest = Color(0xFF2C3437),
)

/**
 * Fixed high-contrast monochrome scheme for the E-ink profile. Every distinction is carried by
 * opaque fills and explicit borders; no slot relies on translucency.
 */
val TsuyomiEInkColorScheme: ColorScheme = lightColorScheme(
    primary = TsuyomiEInkPalette.Ink,
    onPrimary = TsuyomiEInkPalette.Paper,
    primaryContainer = TsuyomiEInkPalette.Paper,
    onPrimaryContainer = TsuyomiEInkPalette.Ink,
    secondary = TsuyomiEInkPalette.N90,
    onSecondary = TsuyomiEInkPalette.Paper,
    secondaryContainer = TsuyomiEInkPalette.Paper,
    onSecondaryContainer = TsuyomiEInkPalette.Ink,
    tertiary = TsuyomiEInkPalette.N90,
    onTertiary = TsuyomiEInkPalette.Paper,
    tertiaryContainer = TsuyomiEInkPalette.Paper,
    onTertiaryContainer = TsuyomiEInkPalette.Ink,
    error = TsuyomiEInkPalette.Ink,
    onError = TsuyomiEInkPalette.Paper,
    errorContainer = TsuyomiEInkPalette.Paper,
    onErrorContainer = TsuyomiEInkPalette.Ink,
    background = TsuyomiEInkPalette.Paper,
    onBackground = TsuyomiEInkPalette.Ink,
    surface = TsuyomiEInkPalette.Paper,
    onSurface = TsuyomiEInkPalette.Ink,
    surfaceVariant = TsuyomiEInkPalette.Paper,
    onSurfaceVariant = TsuyomiEInkPalette.N70,
    outline = TsuyomiEInkPalette.N90,
    outlineVariant = TsuyomiEInkPalette.N50,
    scrim = TsuyomiEInkPalette.Ink,
    inverseSurface = TsuyomiEInkPalette.Ink,
    inverseOnSurface = TsuyomiEInkPalette.Paper,
    inversePrimary = TsuyomiEInkPalette.Paper,
    surfaceDim = TsuyomiEInkPalette.Paper,
    surfaceBright = TsuyomiEInkPalette.Paper,
    surfaceContainerLowest = TsuyomiEInkPalette.Paper,
    surfaceContainerLow = TsuyomiEInkPalette.Paper,
    surfaceContainer = TsuyomiEInkPalette.Paper,
    surfaceContainerHigh = TsuyomiEInkPalette.Paper,
    surfaceContainerHighest = TsuyomiEInkPalette.Paper,
)
