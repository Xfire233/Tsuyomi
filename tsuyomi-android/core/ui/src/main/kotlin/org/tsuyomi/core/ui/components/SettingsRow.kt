/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.tsuyomiFocusRing
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette

/**
 * A settings row with button semantics. [disabledReason] is both visible text and part of the
 * accessibility state description, so an unavailable control never relies on color alone.
 */
@Composable
fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    SettingsRowFrame(
        title = title,
        summary = summary,
        enabled = enabled,
        disabledReason = disabledReason,
        modifier = modifier,
        interaction = { interactionSource ->
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
        },
        trailing = null,
    )
}

/**
 * A settings row with switch semantics on the whole row; the visual switch is semantics-free so
 * the state is announced exactly once. [checked] always reflects the persisted value.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    val onDescription = stringResource(R.string.coreui_switch_on)
    val offDescription = stringResource(R.string.coreui_switch_off)
    SettingsRowFrame(
        title = title,
        summary = summary,
        enabled = enabled,
        disabledReason = disabledReason,
        modifier = modifier.semantics {
            stateDescription = if (checked) onDescription else offDescription
        },
        interaction = { interactionSource ->
            Modifier.toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
        },
        trailing = {
            TsuyomiSwitchVisual(checked = checked, enabled = enabled, clearSemantics = true)
        },
    )
}

/** A non-interactive settings row that pairs a label with a value. */
@Composable
fun SettingsInfoRow(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    SettingsRowFrame(
        title = title,
        summary = summary,
        enabled = true,
        disabledReason = null,
        modifier = modifier,
        interaction = { Modifier },
        trailing = null,
    )
}

@Composable
private fun SettingsRowFrame(
    title: String,
    summary: String?,
    enabled: Boolean,
    disabledReason: String?,
    modifier: Modifier = Modifier,
    interaction: @Composable (MutableInteractionSource) -> Modifier,
    trailing: (@Composable () -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val disabledDescription = stringResource(R.string.coreui_state_disabled)
    val shape = RoundedCornerShape(8.dp)
    val disabledColor = if (
        LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    ) {
        TsuyomiEInkPalette.N50
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .tsuyomiFocusRing(shape, focused, MaterialTheme.colorScheme.primary)
            .then(interaction(interactionSource))
            .semantics(mergeDescendants = true) {
                if (!enabled) {
                    stateDescription = disabledDescription
                }
            }
            .heightIn(min = if (summary == null && disabledReason == null) 48.dp else 64.dp)
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    disabledColor
                },
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!enabled && disabledReason != null) {
                Text(
                    text = disabledReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = disabledColor,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/** Section header used to group settings rows. */
@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TsuyomiSpacing.Md)
            .padding(top = TsuyomiSpacing.Lg, bottom = TsuyomiSpacing.Sm)
            .semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
