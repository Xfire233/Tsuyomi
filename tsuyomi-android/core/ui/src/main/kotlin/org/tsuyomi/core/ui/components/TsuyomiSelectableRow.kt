/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.tsuyomiFocusRing
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
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(4.dp)
    val interactionModifier = if (onLongClick == null) {
        Modifier.clickable(interactionSource = interactionSource, indication = if (eInk) null else LocalIndication.current, role = Role.Button, onClick = onClick)
    } else {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = if (eInk) null else LocalIndication.current,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (eInk) Modifier.heightIn(min = 48.dp).tsuyomiFocusRing(shape, focused,
                if (selected) TsuyomiEInkPalette.Paper else TsuyomiEInkPalette.Ink, inset = if (selected) 3.dp else 0.dp) else Modifier)
            .semantics { this.selected = selected }
            .then(interactionModifier),
        color = when {
            eInk && selected -> TsuyomiEInkPalette.Ink
            eInk -> TsuyomiEInkPalette.Paper
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        contentColor = if (eInk && selected) TsuyomiEInkPalette.Paper else MaterialTheme.colorScheme.onSurface,
        border = if (eInk) BorderStroke(1.5.dp, TsuyomiEInkPalette.Ink) else null,
    ) {
        Row(
            modifier = contentModifier.fillMaxWidth(),
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
}
