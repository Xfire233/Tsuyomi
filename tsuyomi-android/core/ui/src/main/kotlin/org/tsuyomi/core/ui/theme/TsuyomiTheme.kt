/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.theme

import android.os.Build
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment

/**
 * Applies the Tsuyomi Ink design system for one immutable [DisplayEnvironment] snapshot.
 *
 * The E-ink profile always uses the fixed high-contrast monochrome scheme. A standard profile
 * with an effective dynamic-color preference uses an injected deterministic scheme when supplied,
 * otherwise the Android system dynamic scheme; other standard states use static light/dark palettes.
 *
 * Under [org.tsuyomi.core.display.MotionPolicy.INSTANT] the theme removes every animated default
 * that Material could leak: ripples are replaced by an immediate opaque indication, the Material
 * ripple configuration is disabled, and overscroll effects are removed entirely.
 *
 * The provider structure is identical for every profile so switching profiles never remounts the
 * content subtree (routes, scroll positions, and focus are preserved).
 */
@Composable
fun TsuyomiTheme(
    environment: DisplayEnvironment = LocalDisplayEnvironment.current,
    dynamicColorScheme: ColorScheme? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        environment.effectiveProfile == DisplayProfile.EINK -> TsuyomiEInkColorScheme
        environment.dynamicColorEffective && dynamicColorScheme != null -> dynamicColorScheme
        environment.dynamicColorEffective && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (environment.effectiveDarkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        environment.effectiveDarkTheme -> TsuyomiDarkColorScheme
        else -> TsuyomiLightColorScheme
    }
    val instant = environment.instantMotion
    val indication: Indication = if (instant) {
        remember { TsuyomiInstantIndication(TsuyomiEInkPalette.N30) }
    } else {
        ripple()
    }
    val rippleConfiguration: RippleConfiguration? = if (instant) {
        null
    } else {
        LocalRippleConfiguration.current
    }
    val overscrollFactory = if (instant) null else LocalOverscrollFactory.current

    CompositionLocalProvider(
        LocalIndication provides indication,
        LocalRippleConfiguration provides rippleConfiguration,
        LocalOverscrollFactory provides overscrollFactory,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TsuyomiTypography,
            shapes = TsuyomiShapes,
            content = content,
        )
    }
}

/**
 * Static boot surface shown only while the first display environment is being resolved from
 * durable storage. Uses the fixed light tokens; it contains no interaction and no animation.
 */
@Composable
fun TsuyomiBootScreen(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) TsuyomiDarkColorScheme else TsuyomiLightColorScheme,
        typography = TsuyomiTypography,
        shapes = TsuyomiShapes,
        content = content,
    )
}
