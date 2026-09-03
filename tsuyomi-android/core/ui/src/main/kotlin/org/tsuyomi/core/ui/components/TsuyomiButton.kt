/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.tsuyomiFocusRing

/** Visual weight of a [TsuyomiButton]. Only one primary action should be visible per surface. */
enum class TsuyomiButtonStyle {
    PRIMARY,
    SECONDARY,
    TEXT,
}

/**
 * Semantic button honoring the global display profile. Standard uses tonal fills; E-ink uses
 * opaque fill inversion with explicit borders. Disabled states always pair color with a border or
 * fill change, never color alone. Minimum touch target is 48dp.
 */
@Composable
fun TsuyomiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TsuyomiButtonStyle = TsuyomiButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val shape: Shape = RoundedCornerShape(if (eInk) 4.dp else 12.dp)
    val containerColor: Color
    val contentColor: Color
    val border: BorderStroke?
    when {
        !enabled && eInk -> {
            containerColor = TsuyomiEInkPalette.Paper
            contentColor = TsuyomiEInkPalette.N50
            border = BorderStroke(1.5.dp, TsuyomiEInkPalette.N50)
        }
        !enabled -> {
            containerColor = when (style) {
                TsuyomiButtonStyle.PRIMARY -> MaterialTheme.colorScheme.surfaceVariant
                else -> Color.Transparent
            }
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            border = when (style) {
                TsuyomiButtonStyle.SECONDARY ->
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                else -> null
            }
        }
        eInk -> when (style) {
            TsuyomiButtonStyle.PRIMARY -> {
                containerColor = TsuyomiEInkPalette.Ink
                contentColor = TsuyomiEInkPalette.Paper
                border = BorderStroke(1.5.dp, TsuyomiEInkPalette.Ink)
            }
            TsuyomiButtonStyle.SECONDARY -> {
                containerColor = TsuyomiEInkPalette.Paper
                contentColor = TsuyomiEInkPalette.Ink
                border = BorderStroke(1.5.dp, TsuyomiEInkPalette.Ink)
            }
            TsuyomiButtonStyle.TEXT -> {
                containerColor = TsuyomiEInkPalette.Paper
                contentColor = TsuyomiEInkPalette.Ink
                border = null
            }
        }
        else -> when (style) {
            TsuyomiButtonStyle.PRIMARY -> {
                containerColor = MaterialTheme.colorScheme.primary
                contentColor = MaterialTheme.colorScheme.onPrimary
                border = null
            }
            TsuyomiButtonStyle.SECONDARY -> {
                containerColor = Color.Transparent
                contentColor = MaterialTheme.colorScheme.primary
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            }
            TsuyomiButtonStyle.TEXT -> {
                containerColor = Color.Transparent
                contentColor = MaterialTheme.colorScheme.primary
                border = null
            }
        }
    }

    Surface(
        modifier = modifier
            .tsuyomiFocusRing(shape, focused, MaterialTheme.colorScheme.primary),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .heightIn(min = 48.dp)
                .widthIn(min = 64.dp)
                .padding(horizontal = TsuyomiSpacing.Lg, vertical = TsuyomiSpacing.Sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Icon-only button with a mandatory content description and a geometric focus ring. */
@Composable
fun TsuyomiIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(24.dp)
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .tsuyomiFocusRing(shape, focused, MaterialTheme.colorScheme.primary)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/** Standard Material selection chip with an explicit 48dp visual and touch boundary. */
@Composable
fun TsuyomiToggleChip(
    text: String,
    selected: Boolean,
    stateDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { this.stateDescription = stateDescription },
        label = {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        },
    )
}

/** Standard Material action chip for a bounded compact action that is not a selection state. */
@Composable
fun TsuyomiActionChip(
    text: String,
    stateDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { this.stateDescription = stateDescription },
        label = {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        },
    )
}
