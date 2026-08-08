/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.instantMotion

private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val ThumbRadius = 12.dp
private val ThumbPadding = 4.dp
private val TrackBorder = 2.dp

/**
 * Profile-aware switch. The standard profile animates the thumb with a 150ms ease-out tween; the
 * instant motion policy (E-ink / reduced motion) snaps the thumb in one frame. The default
 * Material switch animation is never used.
 */
@Composable
fun TsuyomiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TsuyomiSwitchVisual(
        checked = checked,
        enabled = enabled,
        modifier = modifier.toggleable(
            value = checked,
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        clearSemantics = false,
    )
}

/**
 * The static visual of a switch. With [clearSemantics] it carries no semantics of its own, for
 * use inside rows that already expose the switch role so TalkBack never announces two switches.
 */
@Composable
fun TsuyomiSwitchVisual(
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    clearSemantics: Boolean = true,
) {
    val environment = LocalDisplayEnvironment.current
    val eInk = environment.effectiveProfile == DisplayProfile.EINK
    val instant = environment.instantMotion

    val effectiveFraction = if (instant) {
        if (checked) 1f else 0f
    } else {
        val fraction by animateFloatAsState(
            targetValue = if (checked) 1f else 0f,
            animationSpec = tween(TsuyomiMotion.SWITCH_DURATION_MS, easing = TsuyomiMotion.Easing),
            label = "tsuyomiSwitchThumb",
        )
        fraction
    }

    val scheme = MaterialTheme.colorScheme
    val trackColor: Color
    val trackBorderColor: Color
    val thumbColor: Color
    when {
        eInk && !enabled -> {
            trackColor = TsuyomiEInkPalette.Paper
            trackBorderColor = TsuyomiEInkPalette.N50
            thumbColor = TsuyomiEInkPalette.N50
        }
        eInk && checked -> {
            trackColor = TsuyomiEInkPalette.Ink
            trackBorderColor = TsuyomiEInkPalette.Ink
            thumbColor = TsuyomiEInkPalette.Paper
        }
        eInk -> {
            trackColor = TsuyomiEInkPalette.Paper
            trackBorderColor = TsuyomiEInkPalette.Ink
            thumbColor = TsuyomiEInkPalette.Ink
        }
        !enabled -> {
            trackColor = scheme.surfaceVariant
            trackBorderColor = scheme.outlineVariant
            thumbColor = scheme.outlineVariant
        }
        checked -> {
            trackColor = scheme.primary
            trackBorderColor = scheme.primary
            thumbColor = scheme.onPrimary
        }
        else -> {
            trackColor = scheme.surfaceVariant
            trackBorderColor = scheme.outline
            thumbColor = scheme.outline
        }
    }

    val semanticsModifier = if (clearSemantics) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier
    }
    Canvas(modifier = modifier.then(semanticsModifier).size(TrackWidth, TrackHeight)) {
        val cornerRadius = CornerRadius(TrackHeight.toPx() / 2f)
        drawRoundRect(color = trackColor, cornerRadius = cornerRadius)
        drawRoundRect(
            color = trackBorderColor,
            cornerRadius = cornerRadius,
            style = Stroke(width = TrackBorder.toPx()),
        )
        val minCenter = ThumbPadding.toPx() + ThumbRadius.toPx()
        val maxCenter = size.width - ThumbPadding.toPx() - ThumbRadius.toPx()
        val centerX = minCenter + (maxCenter - minCenter) * effectiveFraction
        drawCircle(
            color = thumbColor,
            radius = ThumbRadius.toPx(),
            center = Offset(centerX, size.height / 2f),
        )
    }
}
