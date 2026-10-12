/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.tsuyomiFocusRing
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
/** Host-owned Material card surface; feature code supplies content and optional activation only. */
@Composable
fun TsuyomiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        val shape = RoundedCornerShape(4.dp)
        val interaction = when {
            onLongClick != null -> {
                requireNotNull(onClick) { "A long-press card must also provide its primary click action." }
                Modifier.combinedClickable(interactionSource = interactionSource, indication = null,
                    role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            }
            onClick != null -> Modifier.clickable(interactionSource = interactionSource, indication = null,
                role = Role.Button, onClick = onClick)
            else -> Modifier
        }
        Surface(
            modifier = modifier.semantics { this.selected = selected }
                .tsuyomiFocusRing(shape, focused, if (selected) TsuyomiEInkPalette.Paper else TsuyomiEInkPalette.Ink,
                    inset = if (selected) 3.dp else 0.dp).then(interaction),
            shape = shape,
            color = if (selected) TsuyomiEInkPalette.Ink else TsuyomiEInkPalette.Paper,
            contentColor = if (selected) TsuyomiEInkPalette.Paper else TsuyomiEInkPalette.Ink,
            border = BorderStroke(1.5.dp, TsuyomiEInkPalette.Ink),
        ) { Column(content = content) }
        return
    }
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    if (onLongClick == null) {
        if (onClick == null) {
            Card(modifier = modifier, colors = colors, content = content)
        } else {
            Card(onClick = onClick, modifier = modifier, colors = colors, content = content)
        }
    } else {
        requireNotNull(onClick) { "A long-press card must also provide its primary click action." }
        Card(
            modifier = modifier
                .semantics { this.selected = selected }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            colors = colors,
            content = content,
        )
    }
}
