/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment

/** Opaque neutral ramp mandated for the E-ink display profile. */
object TsuyomiEInkPalette {
    val Ink = Color(0xFF000000)
    val N90 = Color(0xFF1A1A1A)
    val N70 = Color(0xFF4D4D4D)
    val N50 = Color(0xFF808080)
    val N30 = Color(0xFFB3B3B3)
    val Paper = Color(0xFFFFFFFF)
}

/** Neutral links remain distinct from ordinary body text without becoming a palette accent. */
val ColorScheme.link: Color
    get() = if (surface.luminance() > 0.5f) Color(0xFF3F3F3F) else Color(0xFFD0D0D0)

/**
 * Foreground-only activation marker. E-ink and effective Dynamic Color defer to their active
 * scheme; static Standard uses the restrained coral selected by the UI constitution.
 */
val ColorScheme.activeAccent: Color
    @Composable get() {
        val environment = LocalDisplayEnvironment.current
        return when {
            environment.effectiveProfile == DisplayProfile.EINK || environment.dynamicColorEffective -> primary
            environment.effectiveDarkTheme -> Color(0xFFFF6B7A)
            else -> Color(0xFFCC2B46)
        }
    }

/** Standard light scheme: neutral chrome and grayscale ordinary actions. */
val TsuyomiLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF424242),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E8E8),
    onPrimaryContainer = Color(0xFF1C1C1C),
    secondary = Color(0xFF5F5F5F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color(0xFF1C1C1C),
    tertiary = Color(0xFF505050),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5E5E5),
    onTertiaryContainer = Color(0xFF1C1C1C),
    error = Color(0xFFA64445),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6E0DE),
    onErrorContainer = Color(0xFF5C1A1C),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFFDFDFD),
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Color(0xFF747474),
    outlineVariant = Color(0xFFC6C6C6),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2B2B2B),
    inverseOnSurface = Color(0xFFF0F0F0),
    inversePrimary = Color(0xFFB8B8B8),
    surfaceDim = Color(0xFFDCDCDC),
    surfaceBright = Color(0xFFFDFDFD),
    surfaceContainerLowest = Color(0xFFFDFDFD),
    surfaceContainerLow = Color(0xFFF8F8F8),
    surfaceContainer = Color(0xFFF4F4F4),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFEAEAEA),
)

/** Standard dark scheme: neutral chrome and grayscale ordinary actions. */
val TsuyomiDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFD0D0D0),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF303030),
    onPrimaryContainer = Color(0xFFF0F0F0),
    secondary = Color(0xFFC8C8C8),
    onSecondary = Color(0xFF1B1B1B),
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color(0xFFE8E8E8),
    tertiary = Color(0xFFBDBDBD),
    onTertiary = Color(0xFF1C1C1C),
    tertiaryContainer = Color(0xFF353535),
    onTertiaryContainer = Color(0xFFE8E8E8),
    error = Color(0xFFE8A9A5),
    onError = Color(0xFF4A1513),
    errorContainer = Color(0xFF6E2B28),
    onErrorContainer = Color(0xFFF6DEDD),
    background = Color(0xFF181818),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF222222),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF353535),
    onSurfaceVariant = Color(0xFFC4C4C4),
    outline = Color(0xFF949494),
    outlineVariant = Color(0xFF484848),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color(0xFF242424),
    inversePrimary = Color(0xFF5F5F5F),
    surfaceDim = Color(0xFF141414),
    surfaceBright = Color(0xFF303030),
    surfaceContainerLowest = Color(0xFF101010),
    surfaceContainerLow = Color(0xFF1D1D1D),
    surfaceContainer = Color(0xFF222222),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF333333),
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
