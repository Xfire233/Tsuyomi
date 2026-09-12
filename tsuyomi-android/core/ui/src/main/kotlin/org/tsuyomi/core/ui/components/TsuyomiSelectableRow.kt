/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
/**
 * Host-owned Material 3 list-row interaction with explicit selection semantics and a long-press
 * affordance. Feature surfaces supply their own content without recreating interactive Material
 * primitives.
 */
@Composable
fun TsuyomiSelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionModifier = if (onLongClick == null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier.combinedClickable(
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .then(interactionModifier),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = contentModifier.fillMaxWidth(),
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}
