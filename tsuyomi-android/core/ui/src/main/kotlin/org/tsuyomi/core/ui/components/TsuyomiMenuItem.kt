/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.tsuyomiFocusRing
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

/** A bounded Material menu action for content hosted by a core-owned anchored menu. */
@Composable
fun TsuyomiMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        val shape = RoundedCornerShape(4.dp)
        val foreground = if (selected) TsuyomiEInkPalette.Paper else if (enabled) TsuyomiEInkPalette.Ink else TsuyomiEInkPalette.N50
        val maxHeight = LocalTsuyomiPagerMaxContentHeight.current
        Surface(
            modifier = modifier
                .heightIn(min = 48.dp)
                .then(if (maxHeight == null) Modifier else Modifier.heightIn(max = maxHeight))
                .tsuyomiFocusRing(shape, focused, if (selected) TsuyomiEInkPalette.Paper else TsuyomiEInkPalette.Ink,
                    inset = if (selected) 3.dp else 0.dp)
                .semantics {
                    this.selected = selected
                    contentDescription = label
                }
                .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
            shape = shape,
            color = if (selected) TsuyomiEInkPalette.Ink else TsuyomiEInkPalette.Paper,
            contentColor = foreground,
            border = if (selected) BorderStroke(1.5.dp, TsuyomiEInkPalette.Ink) else null,
        ) {
            TsuyomiMeasuredTextPager(
                text = label,
                modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                fillViewport = false,
                alwaysShowPageControls = false,
                fillWidth = false,
            )
        }
        return
    }
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Md),
    )
}
