/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

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
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = TsuyomiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = TsuyomiSpacing.Sm).weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
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
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .triStateToggleable(
                state = state,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = TsuyomiSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = state,
            onClick = null,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = TsuyomiSpacing.Xs),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
