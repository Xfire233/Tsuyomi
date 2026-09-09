/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ColumnScope
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
