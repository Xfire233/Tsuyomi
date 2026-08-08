/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.MotionPolicy

/** Shared motion constants. The standard profile never exceeds 250ms ease-out transitions. */
object TsuyomiMotion {
    const val SELECTION_DURATION_MS = 200
    const val SWITCH_DURATION_MS = 150
    val Easing = EaseOut
}

/** True when the current environment replaces all state transitions with immediate commits. */
val DisplayEnvironment.instantMotion: Boolean
    get() = motionPolicy == MotionPolicy.INSTANT

/**
 * Observes the platform reduced-motion signal exactly as the display contract defines it: the
 * Compose [MotionDurationScale] scale factor reported by the composition coroutine context.
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val scope = rememberCoroutineScope()
    return scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f
}

/**
 * Animates a semantic color only under the standard motion policy; instant policy commits the
 * target value in the same frame so E-ink and reduced-motion never render intermediate frames.
 */
@Composable
fun tsuyomiAnimateColorAsState(target: Color, instant: Boolean, label: String): Color {
    if (instant) return target

    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(TsuyomiMotion.SELECTION_DURATION_MS, easing = TsuyomiMotion.Easing),
        label = label,
    )
    return animated
}

/**
 * Immediate, fully opaque press feedback for the instant motion policy. It never fades, expands,
 * or uses translucency, so it is safe for E-ink panels.
 */
class TsuyomiInstantIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        InstantIndicationNode(interactionSource, color)

    override fun equals(other: Any?): Boolean =
        other is TsuyomiInstantIndication && other.color == color

    override fun hashCode(): Int = color.hashCode()
}

private class InstantIndicationNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {
    private var pressed = false
    private var hovered = false
    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                    is HoverInteraction.Enter -> hovered = true
                    is HoverInteraction.Exit -> hovered = false
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    else -> Unit
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (pressed || hovered || focused) {
            drawRect(color)
        }
        drawContent()
    }
}

/**
 * Draws a 2dp focus ring around the component while [focused] is true. The ring is a geometric
 * stroke, never a color-only state change. Callers obtain [focused] from the component
 * interaction source, and pass the active scheme's primary color as [color].
 */
fun Modifier.tsuyomiFocusRing(shape: Shape, focused: Boolean, color: Color): Modifier =
    drawWithContent {
        drawContent()
        if (focused) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, color, style = Stroke(2.dp.toPx()))
        }
    }
