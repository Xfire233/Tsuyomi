/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import org.tsuyomi.prototype.uiatlas.model.AtlasProfile
import org.tsuyomi.prototype.uiatlas.model.AtlasThemeKind

/** The environment resolved once at the atlas root; default keeps previews Standard light. */
val LocalAtlasEnvironment = compositionLocalOf {
    AtlasEnvironment(
        profile = AtlasProfile.STANDARD,
        theme = AtlasThemeKind.LIGHT,
        reducedMotion = false,
    )
}

/**
 * Applies the atlas fork of the constitution visual system for one immutable [AtlasEnvironment].
 *
 * The E-ink profile always uses the fixed monochrome scheme. The deterministic-dynamic kind uses
 * the frozen seed-derived schemes (never the host wallpaper). Under the INSTANT motion policy
 * every animated default Material could leak is removed: ripples become an immediate opaque
 * indication, the ripple configuration is disabled, and overscroll is removed entirely.
 *
 * The provider structure is identical for every profile so switching profiles never remounts the
 * content subtree (routes, scroll positions, and focus are preserved).
 */
@Composable
fun AtlasTheme(
    environment: AtlasEnvironment,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        environment.eInk -> AtlasEInkColorScheme
        environment.theme == AtlasThemeKind.DYNAMIC ->
            if (isSystemInDarkTheme()) AtlasDynamicDarkColorScheme else AtlasDynamicLightColorScheme
        environment.theme == AtlasThemeKind.DARK -> AtlasDarkColorScheme
        else -> AtlasLightColorScheme
    }
    val instant = environment.instantMotion
    val indication: Indication = if (instant) {
        remember { AtlasInstantIndication(AtlasEInkPalette.N30) }
    } else {
        ripple()
    }

    CompositionLocalProvider(
        LocalAtlasEnvironment provides environment,
        LocalIndication provides indication,
        LocalRippleConfiguration provides if (instant) null else LocalRippleConfiguration.current,
        LocalOverscrollFactory provides if (instant) null else LocalOverscrollFactory.current,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AtlasTypography,
            shapes = if (environment.eInk) AtlasEInkShapes else AtlasShapes,
            content = content,
        )
    }
}
