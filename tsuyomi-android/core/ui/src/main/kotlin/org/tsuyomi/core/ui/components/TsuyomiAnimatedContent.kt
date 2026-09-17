/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.core.ui.theme.policyMotionSpec

/** Content swap honoring the global and platform instant-motion policies. */
@Composable
fun <T> TsuyomiAnimatedContent(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val staticMotion = LocalDisplayEnvironment.current.instantMotion || rememberSystemReducedMotion()
    val transition = if (staticMotion) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        fadeIn(tween(TsuyomiMotion.SELECTION_DURATION_MS, easing = TsuyomiMotion.Easing)) togetherWith
            fadeOut(tween(TsuyomiMotion.SELECTION_DURATION_MS, easing = TsuyomiMotion.Easing))
    }
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = { transition },
        content = { state -> content(state) },
    )
}


/** Logical direction for a full-surface content replacement. */
enum class TsuyomiContentDirection { FORWARD, BACKWARD }

/**
 * Replaces full-surface content with a bounded directional slide, or an immediate swap when
 * motion is disabled. A null [transitionDirection] result deliberately suppresses motion for
 * non-navigational replacements such as previews.
 */
@Composable
fun <T> TsuyomiDirectionalAnimatedContent(
    targetState: T,
    transitionDirection: (initialState: T, targetState: T) -> TsuyomiContentDirection?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val staticMotion = LocalDisplayEnvironment.current.instantMotion || rememberSystemReducedMotion()
    if (staticMotion) {
        Box(modifier) { content(targetState) }
        return
    }
    val pageMotion = policyMotionSpec<IntOffset>(staticMotion, TsuyomiMotion.PAGE_TURN_DURATION_MS)
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            when (if (staticMotion) null else transitionDirection(initialState, targetState)) {
                TsuyomiContentDirection.FORWARD -> {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = pageMotion,
                    ) togetherWith slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = pageMotion,
                    )
                }
                TsuyomiContentDirection.BACKWARD -> {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = pageMotion,
                    ) togetherWith slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = pageMotion,
                    )
                }
                null -> EnterTransition.None togetherWith ExitTransition.None
            }
        },
        content = { state -> content(state) },
    )
}
/** Animates a directly manipulated page from its current offset to the final bounded position. */
suspend fun animateTsuyomiPageTurn(
    from: Float,
    to: Float,
    onFrame: (Float) -> Unit,
) {
    animate(
        initialValue = from,
        targetValue = to,
        animationSpec = tween(TsuyomiMotion.PAGE_TURN_DURATION_MS, easing = TsuyomiMotion.Easing),
    ) { value, _ -> onFrame(value) }
}


/** Resize the same content using the shared duration, or snap when motion is disabled. */
@Composable
fun Modifier.tsuyomiAnimateContentSize(): Modifier {
    val reducedMotion = rememberSystemReducedMotion()
    return if (LocalDisplayEnvironment.current.instantMotion || reducedMotion) this else animateContentSize(
        animationSpec = tween(TsuyomiMotion.SELECTION_DURATION_MS, easing = TsuyomiMotion.Easing),
    )
}
