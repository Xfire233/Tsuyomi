/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

/**
 * Small non-interactive label chip (source identity, tags, counts). E-ink renders an opaque
 * bordered monochrome chip; Standard renders a tonal or tinted container. Text is always the
 * carrier of meaning — tint is decorative only.
 */
@Composable
fun AtlasChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color? = null,
    content: Color? = null,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val scheme = MaterialTheme.colorScheme
    val resolvedContainer = when {
        eInk -> AtlasEInkPalette.Paper
        container != null -> container
        else -> scheme.surfaceVariant
    }
    val resolvedContent = when {
        eInk -> AtlasEInkPalette.Ink
        content != null -> content
        else -> scheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.heightIn(min = 24.dp),
        shape = MaterialTheme.shapes.small,
        color = resolvedContainer,
        contentColor = resolvedContent,
        border = if (eInk) BorderStroke(1.dp, AtlasEInkPalette.N90) else null,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(horizontal = AtlasSpacing.Sm, vertical = AtlasSpacing.Xs),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
