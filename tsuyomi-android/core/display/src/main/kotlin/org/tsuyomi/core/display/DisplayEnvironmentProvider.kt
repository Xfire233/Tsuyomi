/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent

/** The immutable global display environment supplied by the application composition root. */
val LocalDisplayEnvironment = staticCompositionLocalOf<DisplayEnvironment> {
    error("DisplayEnvironmentProvider has not been installed")
}

/**
 * Installs [environment] for the complete UI subtree without remounting durable screen state.
 */
@Composable
fun DisplayEnvironmentProvider(
    environment: DisplayEnvironment,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDisplayEnvironment provides environment, content = content)
}

/**
 * Invalidates the attached stable root drawing layer whenever the current redraw epoch changes.
 *
 * Apply this to the root surface below [DisplayEnvironmentProvider], not to a navigation or
 * screen-state host. The modifier redraws without recreating its composition subtree and makes
 * no claim of a device-specific hardware refresh.
 */
@Composable
fun Modifier.displayRedrawLayer(): Modifier {
    val redrawEpoch = LocalDisplayEnvironment.current.redrawEpoch
    return drawWithContent {
        redrawEpoch
        drawContent()
    }
}
