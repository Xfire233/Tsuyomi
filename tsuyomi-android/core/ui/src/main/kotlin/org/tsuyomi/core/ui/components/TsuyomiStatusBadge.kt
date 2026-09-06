/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

@Composable
fun rememberTsuyomiStatusBadgeInlineContent(
    text: String,
    modifier: Modifier = Modifier,
): InlineTextContent {
    val textStyle = MaterialTheme.typography.labelSmall
    val textSize = rememberTextMeasurer().measure(
        text = text,
        style = textStyle,
        softWrap = false,
        maxLines = 1,
    ).size
    val placeholder = with(LocalDensity.current) {
        // Material 3 Badge uses a 16dp minimum and 4dp padding on each horizontal edge.
        Placeholder(
            width = maxOf(16.dp.roundToPx(), textSize.width + 2 * 4.dp.roundToPx()).toSp(),
            height = maxOf(16.dp.roundToPx(), textSize.height).toSp(),
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        )
    }
    return remember(text, modifier, textStyle, placeholder) {
        InlineTextContent(placeholder) {
            Badge(
                modifier = modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(text = text, style = textStyle, maxLines = 1)
            }
        }
    }
}
