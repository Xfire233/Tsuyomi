/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
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
import org.tsuyomi.prototype.uiatlas.model.AtlasProfile
import org.tsuyomi.prototype.uiatlas.model.AtlasThemeKind

enum class AtlasStateArt { TYPOGRAPHIC, EMOTICON }
/**
 * Immutable display environment snapshot resolved once at the atlas root (constitution §0).
 * Components read it via [LocalAtlasEnvironment]; no feature parameter threads profile booleans.
 */
@Immutable
data class AtlasEnvironment(
    val profile: AtlasProfile,
    val theme: AtlasThemeKind,
    val reducedMotion: Boolean,
    val stateArt: AtlasStateArt = AtlasStateArt.TYPOGRAPHIC,
) {
    val eInk: Boolean get() = profile == AtlasProfile.EINK

    /** Constitution §11.2: E-ink or reduced-motion replaces every transition with one frame. */
    val instantMotion: Boolean get() = eInk || reducedMotion
}

/**
 * Standard-profile motion token set (constitution §11.1, restrained functional character,
 * ≤ 250ms ceiling). Under the INSTANT policy every duration resolves to 0 — components ask
 * [AtlasEnvironment.instantMotion] and skip animation entirely rather than animating fast.
 */
object AtlasMotion {
    const val IMMEDIATE_MS = 40
    const val FADE_IN_MS = 120
    const val FADE_OUT_MS = 90
    const val EXPAND_MS = 220
    const val SPATIAL_MS = 220
    const val BACKDROP_MS = 100
    const val SHEET_MS = 220
    const val VISIBILITY_MS = 200

    /** Token-resolved duration: 0 under the INSTANT policy, the Standard token otherwise. */
    fun duration(standardMs: Int, environment: AtlasEnvironment): Int =
        if (environment.instantMotion) 0 else standardMs
}

/**
 * Immediate, fully opaque press feedback for the INSTANT motion policy. It never fades, expands,
 * or uses translucency, so it is safe for E-ink panels.
 */
class AtlasInstantIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        AtlasInstantIndicationNode(interactionSource, color)

    override fun equals(other: Any?): Boolean =
        other is AtlasInstantIndication && other.color == color

    override fun hashCode(): Int = color.hashCode()
}

private class AtlasInstantIndicationNode(
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
 * Draws a 2dp geometric focus ring while [focused] is true — a stroke, never a color-only state
 * change (constitution §3.3/§21).
 */
fun Modifier.atlasFocusRing(shape: Shape, focused: Boolean, color: Color): Modifier =
    drawWithContent {
        drawContent()
        if (focused) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, color, style = Stroke(2.dp.toPx()))
        }
    }
