/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.core.ui.theme.tsuyomiFocusRing

import org.tsuyomi.core.ui.theme.activeAccent
import androidx.compose.ui.semantics.selected
/** Visual weight of a [TsuyomiButton]. Only one primary action should be visible per surface. */
enum class TsuyomiButtonStyle {
    PRIMARY,
    SECONDARY,
    TEXT,
}

/**
 * Semantic button honoring the global display profile. Standard delegates to the Material button
 * family; E-ink retains its opaque, bordered implementation. Minimum touch target is 48dp.
 */
@Composable
fun TsuyomiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TsuyomiButtonStyle = TsuyomiButtonStyle.PRIMARY,
    enabled: Boolean = true,
    contentPadding: PaddingValues = TsuyomiButtonContentPadding,
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        EInkTsuyomiButton(text, onClick, modifier, style, enabled, contentPadding)
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val staticMotion = LocalDisplayEnvironment.current.instantMotion || rememberSystemReducedMotion()
    val shape = MaterialTheme.shapes.medium
    val pressedShape = MaterialTheme.shapes.large
    val buttonModifier = modifier
        .heightIn(min = 48.dp)
        .widthIn(min = 64.dp)
        .tsuyomiFocusRing(shape, focused, MaterialTheme.colorScheme.primary)
    val scheme = MaterialTheme.colorScheme
    val colors = when (style) {
        TsuyomiButtonStyle.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            disabledContainerColor = scheme.surfaceVariant,
            disabledContentColor = scheme.onSurfaceVariant,
        )
        TsuyomiButtonStyle.SECONDARY -> ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = scheme.primary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = scheme.onSurfaceVariant,
        )
        TsuyomiButtonStyle.TEXT -> ButtonDefaults.textButtonColors(
            containerColor = Color.Transparent,
            contentColor = scheme.primary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = scheme.onSurfaceVariant,
        )
    }
    val border = if (style == TsuyomiButtonStyle.SECONDARY) {
        BorderStroke(1.dp, if (enabled) scheme.outline else scheme.outlineVariant)
    } else {
        null
    }

    if (staticMotion) {
        val pressed by interactionSource.collectIsPressedAsState()
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = if (pressed && enabled) pressedShape else shape,
            colors = if (pressed && enabled && style == TsuyomiButtonStyle.TEXT) {
                colors.copy(
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                )
            } else {
                colors
            },
            elevation = if (style == TsuyomiButtonStyle.PRIMARY) ButtonDefaults.buttonElevation() else null,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
        ) {
            TsuyomiButtonLabel(text)
        }
        return
    }
    val shapes = remember(shape, pressedShape) { ButtonShapes(shape, pressedShape) }

    when (style) {
        TsuyomiButtonStyle.PRIMARY -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shapes = shapes,
            colors = colors,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
        ) {
            TsuyomiButtonLabel(text)
        }
        TsuyomiButtonStyle.SECONDARY -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shapes = shapes,
            colors = colors,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
        ) {
            TsuyomiButtonLabel(text)
        }
        TsuyomiButtonStyle.TEXT -> TextButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shapes = shapes,
            colors = colors,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
        ) {
            TsuyomiButtonLabel(text)
        }
    }
}

private val TsuyomiButtonContentPadding = PaddingValues(
    horizontal = TsuyomiSpacing.Lg,
    vertical = TsuyomiSpacing.Sm,
)

@Composable
private fun TsuyomiButtonLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun EInkTsuyomiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    style: TsuyomiButtonStyle,
    enabled: Boolean,
    contentPadding: PaddingValues,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape: Shape = RoundedCornerShape(4.dp)
    val containerColor: Color
    val contentColor: Color
    val border: BorderStroke?
    when {
        !enabled -> {
            containerColor = TsuyomiEInkPalette.Paper
            contentColor = TsuyomiEInkPalette.N50
            border = BorderStroke(if (style == TsuyomiButtonStyle.TEXT) 1.dp else 1.5.dp, TsuyomiEInkPalette.N50)
        }
        else -> when (style) {
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
                border = BorderStroke(1.dp, TsuyomiEInkPalette.N50)
            }
        }
    }

    if (style == TsuyomiButtonStyle.TEXT) {
        Box(
            modifier = modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .tsuyomiFocusRing(shape, focused, TsuyomiEInkPalette.Ink)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = shape, color = containerColor, contentColor = contentColor, border = border) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Xs),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier
            .tsuyomiFocusRing(shape, focused, if (enabled && style == TsuyomiButtonStyle.PRIMARY) TsuyomiEInkPalette.Paper else TsuyomiEInkPalette.Ink,
                inset = if (enabled && style == TsuyomiButtonStyle.PRIMARY) 3.dp else 0.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 64.dp)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
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
    iconModifier: Modifier = Modifier,
    iconTint: Color? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(24.dp)
    val tint = iconTint ?: if (enabled) {
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
            modifier = iconModifier,
            tint = tint,
        )
    }
}
/** Standard tonal icon action with a compact 40dp visual inside a 48dp interaction slot. */
@Composable
fun TsuyomiTonalIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        TsuyomiIconButton(imageVector, contentDescription, onClick, modifier, enabled)
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.small
    val interactionModifier = modifier
        .size(48.dp)
        .tsuyomiFocusRing(shape, focused, MaterialTheme.colorScheme.primary)

    if (!compact) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = interactionModifier,
            enabled = enabled,
            interactionSource = interactionSource,
        ) {
            Icon(imageVector = imageVector, contentDescription = contentDescription)
        }
        return
    }

    FilledTonalIconButton(
        onClick = onClick,
        modifier = interactionModifier,
        enabled = enabled,
        shape = androidx.compose.ui.graphics.RectangleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        interactionSource = interactionSource,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = shape,
            color = if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = imageVector, contentDescription = contentDescription)
            }
        }
    }
}


/** Standard Material selection chip with an optional compact visual inside a 48dp touch slot. */
@Composable
fun TsuyomiToggleChip(
    text: String,
    selected: Boolean,
    stateDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    leadingIcon: ImageVector? = null,
    labelContent: (@Composable () -> Unit)? = null,
    role: Role = Role.Checkbox,
) {
    val isEInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    if (isEInk) {
        EInkSelectionChip(
            text = text,
            selected = selected,
            stateDescription = stateDescription,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            labelContent = labelContent,
            role = role,
        )
        return
    }
    val useCompact = compact && !isEInk
    val scheme = MaterialTheme.colorScheme
    val chipModifier = modifier
        .heightIn(min = if (useCompact) 40.dp else 48.dp)
        .semantics { this.stateDescription = stateDescription }
    val chip: @Composable () -> Unit = {
        FilterChip(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            modifier = chipModifier,
            colors = if (isEInk) FilterChipDefaults.filterChipColors() else FilterChipDefaults.filterChipColors(
                labelColor = scheme.onSurfaceVariant,
                selectedContainerColor = scheme.surfaceVariant,
                selectedLabelColor = scheme.activeAccent,
            ),
            label = {
                if (labelContent != null) {
                    labelContent()
                } else {
                    Text(
                        text = text,
                        modifier = if (useCompact) Modifier else Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            },
            leadingIcon = leadingIcon?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.activeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
    if (useCompact) {
        Box(
            modifier = Modifier.heightIn(min = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            chip()
        }
    } else {
        chip()
    }
}

@Composable
private fun EInkSelectionChip(
    text: String,
    selected: Boolean?,
    stateDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    leadingIcon: ImageVector? = null,
    labelContent: (@Composable () -> Unit)? = null,
    role: Role = Role.Checkbox,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(4.dp)
    val contentMaxHeight = LocalTsuyomiPagerMaxContentHeight.current
    val container = if (selected == true) TsuyomiEInkPalette.Ink else TsuyomiEInkPalette.Paper
    val content = when {
        selected == true -> TsuyomiEInkPalette.Paper
        !enabled -> TsuyomiEInkPalette.N50
        else -> TsuyomiEInkPalette.Ink
    }
    val interaction = if (selected == null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier.toggleable(
            value = selected,
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onValueChange = { onClick() },
        )
    }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (contentMaxHeight == null) Modifier else Modifier.heightIn(max = contentMaxHeight))
            .widthIn(min = 48.dp)
            .clip(shape)
            .background(container, shape)
            .border(BorderStroke(1.5.dp, if (enabled) TsuyomiEInkPalette.Ink else TsuyomiEInkPalette.N50), shape)
            .tsuyomiFocusRing(shape, focused, if (selected == true) TsuyomiEInkPalette.Paper else TsuyomiEInkPalette.Ink,
                inset = if (selected == true) 3.dp else 0.dp)
            .semantics(mergeDescendants = true) {
                this.stateDescription = stateDescription
                selected?.let { this.selected = it }
            }
            .then(interaction)
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = content,
            )
        }
        CompositionLocalProvider(LocalContentColor provides content) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = content)) {
                if (labelContent == null) {
                    if (contentMaxHeight == null) {
                        Text(text = text, color = content, textAlign = TextAlign.Center)
                    } else {
                        TsuyomiMeasuredTextPager(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            color = content,
                            textAlign = TextAlign.Center,
                            fillViewport = false,
                            alwaysShowPageControls = false,
                            fillWidth = false,
                        )
                    }
                } else {
                    labelContent()
                }
            }
        }
    }
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
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        EInkSelectionChip(
            text = text,
            selected = null,
            stateDescription = stateDescription,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
        return
    }
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
