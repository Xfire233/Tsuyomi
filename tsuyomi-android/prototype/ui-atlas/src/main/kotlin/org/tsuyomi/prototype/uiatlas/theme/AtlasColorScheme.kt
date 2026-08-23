/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Opaque neutral ramp mandated for the E-ink display profile (constitution §3.1). */
object AtlasEInkPalette {
    val Ink = Color(0xFF000000)
    val N90 = Color(0xFF1A1A1A)
    val N70 = Color(0xFF4D4D4D)
    val N50 = Color(0xFF808080)
    val N30 = Color(0xFFB3B3B3)
    val Paper = Color(0xFFFFFFFF)
}

/** Standard light: warm paper background, ink-teal primary. Never pure black/white in chrome. */
val AtlasLightColorScheme: ColorScheme = lightColorScheme(
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

/** Standard dark. Never pure black/white in chrome. */
val AtlasDarkColorScheme: ColorScheme = darkColorScheme(
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
 * Deterministic-dynamic schemes (Atlas Spec §6 "deterministic-dynamic (fixture seed)"). The
 * values below were derived offline from ATLAS_SEED = 20260811 (a muted warm-sage seed palette)
 * and are frozen as constants, so the "dynamic" theme is pixel-identical on every run and never
 * consults the host wallpaper or system palette.
 */
val AtlasDynamicLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4A5A3E),
    onPrimary = Color(0xFFFDFCF9),
    primaryContainer = Color(0xFFDDE5D3),
    onPrimaryContainer = Color(0xFF24301B),
    secondary = Color(0xFF5F6655),
    onSecondary = Color(0xFFFDFCF9),
    secondaryContainer = Color(0xFFE3E5DB),
    onSecondaryContainer = Color(0xFF2E3227),
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
    inversePrimary = Color(0xFFA9BE97),
    surfaceDim = Color(0xFFDCD9D1),
    surfaceBright = Color(0xFFFDFCF9),
    surfaceContainerLowest = Color(0xFFFDFCF9),
    surfaceContainerLow = Color(0xFFF5F3EE),
    surfaceContainer = Color(0xFFF0EDE7),
    surfaceContainerHigh = Color(0xFFEAE7E0),
    surfaceContainerHighest = Color(0xFFE4E1D9),
)

/** Dark counterpart of the frozen deterministic-dynamic seed palette. */
val AtlasDynamicDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA9BE97),
    onPrimary = Color(0xFF1C2614),
    primaryContainer = Color(0xFF3A462E),
    onPrimaryContainer = Color(0xFFDDE5D3),
    secondary = Color(0xFFB4BAA6),
    onSecondary = Color(0xFF20251A),
    secondaryContainer = Color(0xFF363B2E),
    onSecondaryContainer = Color(0xFFE3E5DB),
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
    inversePrimary = Color(0xFF4A5A3E),
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
 * opaque fills and explicit borders; no slot relies on translucency (constitution §3.1).
 */
val AtlasEInkColorScheme: ColorScheme = lightColorScheme(
    primary = AtlasEInkPalette.Ink,
    onPrimary = AtlasEInkPalette.Paper,
    primaryContainer = AtlasEInkPalette.Paper,
    onPrimaryContainer = AtlasEInkPalette.Ink,
    secondary = AtlasEInkPalette.N90,
    onSecondary = AtlasEInkPalette.Paper,
    secondaryContainer = AtlasEInkPalette.Paper,
    onSecondaryContainer = AtlasEInkPalette.Ink,
    tertiary = AtlasEInkPalette.N90,
    onTertiary = AtlasEInkPalette.Paper,
    tertiaryContainer = AtlasEInkPalette.Paper,
    onTertiaryContainer = AtlasEInkPalette.Ink,
    error = AtlasEInkPalette.Ink,
    onError = AtlasEInkPalette.Paper,
    errorContainer = AtlasEInkPalette.Paper,
    onErrorContainer = AtlasEInkPalette.Ink,
    background = AtlasEInkPalette.Paper,
    onBackground = AtlasEInkPalette.Ink,
    surface = AtlasEInkPalette.Paper,
    onSurface = AtlasEInkPalette.Ink,
    surfaceVariant = AtlasEInkPalette.Paper,
    onSurfaceVariant = AtlasEInkPalette.N70,
    outline = AtlasEInkPalette.N90,
    outlineVariant = AtlasEInkPalette.N50,
    scrim = AtlasEInkPalette.Ink,
    inverseSurface = AtlasEInkPalette.Ink,
    inverseOnSurface = AtlasEInkPalette.Paper,
    inversePrimary = AtlasEInkPalette.Paper,
    surfaceDim = AtlasEInkPalette.Paper,
    surfaceBright = AtlasEInkPalette.Paper,
    surfaceContainerLowest = AtlasEInkPalette.Paper,
    surfaceContainerLow = AtlasEInkPalette.Paper,
    surfaceContainer = AtlasEInkPalette.Paper,
    surfaceContainerHigh = AtlasEInkPalette.Paper,
    surfaceContainerHighest = AtlasEInkPalette.Paper,
)
