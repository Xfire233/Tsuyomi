/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.tsuyomiFocusRing
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import androidx.compose.material3.CheckboxDefaults
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.activeAccent

/**
 * A labelled checkbox with one merged checkbox action for the complete row.
 *
 * The visual checkbox deliberately has no action or semantics of its own: the row exposes the
 * label, checked state, and [Role.Checkbox] together so its label is an equally valid target.
 */
@Composable
fun TsuyomiCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelContent: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(4.dp)
    val foreground = MaterialTheme.colorScheme.onSurface
    val maxHeight = if (eInk) LocalTsuyomiPagerMaxContentHeight.current else null
    val checkboxColors = CheckboxDefaults.colors(
        checkedColor = MaterialTheme.colorScheme.secondaryContainer,
        checkmarkColor = MaterialTheme.colorScheme.activeAccent,
    )
    CompositionLocalProvider(LocalContentColor provides foreground) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (maxHeight == null) Modifier else Modifier.heightIn(max = maxHeight))
            .tsuyomiFocusRing(shape, focused, MaterialTheme.colorScheme.primary)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = if (eInk) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .semantics(mergeDescendants = true) {
                if (labelContent != null) contentDescription = label
            }
            .padding(horizontal = TsuyomiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (eInk) {
            StaticCheckboxVisual(if (checked) ToggleableState.On else ToggleableState.Off, enabled)
        } else {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
                colors = checkboxColors,
            )
        }
        if (labelContent == null) {
            if (eInk && maxHeight != null) {
                TsuyomiMeasuredTextPager(
                    text = label,
                    modifier = Modifier.padding(start = TsuyomiSpacing.Sm).weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = foreground,
                    fillViewport = false,
                    alwaysShowPageControls = false,
                )
            } else {
                Text(
                    text = label,
                    modifier = Modifier.padding(start = TsuyomiSpacing.Sm).weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Box(Modifier.padding(start = TsuyomiSpacing.Sm).weight(1f).clearAndSetSemantics {}) { labelContent() }
        }
    }
    }
}

/** Intrinsic-width aggregate selection, with one labelled all/partial/none action. */
@Composable
fun TsuyomiTriStateCheckboxRow(
    state: ToggleableState,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(4.dp)
    val foreground = MaterialTheme.colorScheme.onSurface
    val maxHeight = if (eInk) LocalTsuyomiPagerMaxContentHeight.current else null
    val checkboxColors = CheckboxDefaults.colors(
        checkedColor = MaterialTheme.colorScheme.secondaryContainer,
        checkmarkColor = MaterialTheme.colorScheme.activeAccent,
    )
    CompositionLocalProvider(LocalContentColor provides foreground) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (maxHeight == null) Modifier else Modifier.heightIn(max = maxHeight))
            .tsuyomiFocusRing(shape, focused, MaterialTheme.colorScheme.primary)
            .triStateToggleable(
                state = state,
                interactionSource = interactionSource,
                indication = if (eInk) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = TsuyomiSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (eInk) {
            StaticCheckboxVisual(state, enabled)
        } else {
            TriStateCheckbox(
                state = state,
                onClick = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
                colors = checkboxColors,
            )
        }
        if (eInk && maxHeight != null) {
            TsuyomiMeasuredTextPager(
                text = label,
                modifier = Modifier.padding(start = TsuyomiSpacing.Xs),
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                fillViewport = false,
                alwaysShowPageControls = false,
                fillWidth = false,
            )
        } else {
            Text(
                text = label,
                modifier = Modifier.padding(start = TsuyomiSpacing.Xs),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    }
}

/** Only the row is interactive; this is the immediate standard checkbox endpoint symbol. */
@Composable
private fun StaticCheckboxVisual(state: ToggleableState, enabled: Boolean) {
    val selected = state != ToggleableState.Off
    val boundary = if (enabled) TsuyomiEInkPalette.Ink else TsuyomiEInkPalette.N50
    Box(Modifier.size(48.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(20.dp)
                .background(if (selected) TsuyomiEInkPalette.Ink else TsuyomiEInkPalette.Paper, RoundedCornerShape(2.dp))
                .border(1.5.dp, boundary, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                ToggleableState.On -> Icon(TsuyomiIcons.Selected, null, Modifier.size(18.dp), tint = TsuyomiEInkPalette.Paper)
                ToggleableState.Indeterminate -> Box(Modifier.width(12.dp).height(2.dp).background(TsuyomiEInkPalette.Paper))
                ToggleableState.Off -> Unit
            }
        }
    }
}
