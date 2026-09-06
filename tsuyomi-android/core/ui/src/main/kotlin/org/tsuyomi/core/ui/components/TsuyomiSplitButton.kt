/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SplitButtonShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

// Compact Detail action height; Material still supplies the official split-button behavior.
private val SplitContainerHeight = 48.dp
private val SplitInnerCorner = CornerSize(4.dp)

/** Host-owned wrapper around the official Material 3 split button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TsuyomiSplitButton(
    text: String,
    leadingIcon: ImageVector,
    trailingIcon: ImageVector,
    trailingDescription: String,
    onLeadingClick: () -> Unit,
    onMenuOpen: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    leadingEnabled: Boolean,
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit,
) {
    // The extended FAB also resolves CornerLarge from MaterialTheme.shapes.large.
    val outerShape = MaterialTheme.shapes.large
    val leadingShapes = remember(outerShape) {
        val shape = outerShape.copy(topEnd = SplitInnerCorner, bottomEnd = SplitInnerCorner)
        SplitButtonShapes(shape = shape, pressedShape = shape, checkedShape = shape)
    }
    val trailingShapes = remember(outerShape) {
        val shape = outerShape.copy(topStart = SplitInnerCorner, bottomStart = SplitInnerCorner)
        SplitButtonShapes(shape = shape, pressedShape = shape, checkedShape = shape)
    }
    Box(modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onLeadingClick,
                    modifier = Modifier.height(SplitContainerHeight)
                        .layout { measurable, constraints ->
                            val leadingConstraints = if (constraints.hasBoundedWidth) {
                                val available = (constraints.maxWidth - (48.dp + SplitButtonDefaults.Spacing).roundToPx())
                                    .coerceAtLeast(0)
                                constraints.copy(minWidth = available, maxWidth = available)
                            } else constraints
                            val placeable = measurable.measure(leadingConstraints)
                            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                        }
                        .testTag("tsuyomi-split-leading"),
                    enabled = leadingEnabled,
                    shapes = leadingShapes,
                    contentPadding = PaddingValues(start = 16.dp, end = 20.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(text, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    onClick = {
                        onMenuOpen()
                        onMenuExpandedChange(true)
                    },
                    modifier = Modifier
                        .height(SplitContainerHeight)
                        .width(48.dp)
                        .semantics { contentDescription = trailingDescription }
                        .testTag("tsuyomi-split-trailing"),
                    shapes = trailingShapes,
                ) {
                    Icon(
                        trailingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { onMenuExpandedChange(false) },
            modifier = Modifier.widthIn(min = 220.dp, max = 320.dp).testTag("detail-destination-menu"),
            offset = DpOffset(x = 0.dp, y = 4.dp),
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            menuContent { onMenuExpandedChange(false) }
        }
    }
}
