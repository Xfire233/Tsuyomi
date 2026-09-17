/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.instantMotion

/** Vertical edge from which a transient surface enters or leaves. */
enum class TsuyomiVisibilityEdge { NONE, TOP, BOTTOM }

/**
 * Policy-aware transient visibility. E-ink and reduced-motion environments snap between states;
 * Standard surfaces use the shared selection duration and easing rather than inventing local motion.
 */
@Composable
fun TsuyomiVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enterFrom: TsuyomiVisibilityEdge = TsuyomiVisibilityEdge.NONE,
    exitTo: TsuyomiVisibilityEdge = enterFrom,
    content: @Composable () -> Unit,
) {
    val instant = LocalDisplayEnvironment.current.instantMotion
    val enter = if (instant) EnterTransition.None else visibilityEnter(enterFrom)
    val exit = if (instant) ExitTransition.None else visibilityExit(exitTo)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = { content() },
    )
}

private fun visibilityEnter(edge: TsuyomiVisibilityEdge): EnterTransition {
    val motion = tween<IntOffset>(TsuyomiMotion.SELECTION_DURATION_MS, easing = TsuyomiMotion.Easing)
    val fade = fadeIn(animationSpec = tween(TsuyomiMotion.SELECTION_DURATION_MS, easing = TsuyomiMotion.Easing))
    return when (edge) {
        TsuyomiVisibilityEdge.NONE -> fade
        TsuyomiVisibilityEdge.TOP -> fade + slideInVertically(animationSpec = motion) { -it }
        TsuyomiVisibilityEdge.BOTTOM -> fade + slideInVertically(animationSpec = motion) { it }
    }
}

private fun visibilityExit(edge: TsuyomiVisibilityEdge): ExitTransition {
    val motion = tween<IntOffset>(TsuyomiMotion.SELECTION_DURATION_MS, easing = TsuyomiMotion.Easing)
    val fade = fadeOut(animationSpec = tween(TsuyomiMotion.SELECTION_DURATION_MS, easing = TsuyomiMotion.Easing))
    return when (edge) {
        TsuyomiVisibilityEdge.NONE -> fade
        TsuyomiVisibilityEdge.TOP -> fade + slideOutVertically(animationSpec = motion) { -it }
        TsuyomiVisibilityEdge.BOTTOM -> fade + slideOutVertically(animationSpec = motion) { it }
    }
}
