/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.display.displayGrayscaleLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.key
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SplitButtonShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

// Detail retains 48dp segments; pinned Material measures the trailing segment before the leading one.
private val SplitContainerHeight = 48.dp
private val SplitInnerCorner = CornerSize(4.dp)

/** Host-owned wrapper around the official Material 3 split button. */
@Composable
fun TsuyomiSplitButton(
    text: String,
    trailingIcon: ImageVector,
    trailingDescription: String,
    onLeadingClick: () -> Unit,
    onMenuOpen: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    leadingEnabled: Boolean,
    modifier: Modifier = Modifier,
    menuItemCount: Int,
    menuItemKey: (index: Int) -> String,
    menuItemContent: @Composable ColumnScope.(index: Int, dismissMenu: () -> Unit) -> Unit,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    require(menuItemCount >= 0) { "menuItemCount must not be negative" }
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
        if (eInk) {
            Row(horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs)) {
                TsuyomiButton(
                    text = text,
                    onClick = onLeadingClick,
                    enabled = leadingEnabled,
                    modifier = Modifier.weight(1f, fill = false).testTag("tsuyomi-split-leading"),
                )
                TsuyomiIconButton(
                    imageVector = trailingIcon,
                    contentDescription = trailingDescription,
                    onClick = { onMenuOpen(); onMenuExpandedChange(true) },
                    modifier = Modifier.testTag("tsuyomi-split-trailing"),
                )
            }
            TsuyomiDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) },
                itemCount = menuItemCount,
                itemKey = menuItemKey,
                itemContent = { index -> menuItemContent(index) { onMenuExpandedChange(false) } },
                modifier = Modifier.widthIn(min = 220.dp, max = 320.dp).testTag("detail-destination-menu"),
                offset = DpOffset(x = 0.dp, y = 4.dp),
            )
            return@Box
        }
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onLeadingClick,
                    modifier = Modifier.height(SplitContainerHeight)
                        .testTag("tsuyomi-split-leading"),
                    enabled = leadingEnabled,
                    shapes = leadingShapes,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(text, maxLines = 1, style = MaterialTheme.typography.labelLarge)
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
            modifier = Modifier.widthIn(min = 220.dp, max = 320.dp).testTag("detail-destination-menu").displayGrayscaleLayer(),
            offset = DpOffset(x = 0.dp, y = 4.dp),
            shape = if (eInk) RoundedCornerShape(4.dp) else MaterialTheme.shapes.medium,
            containerColor = if (eInk) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = if (eInk) 0.dp else 3.dp,
            shadowElevation = if (eInk) 0.dp else 6.dp,
            border = if (eInk) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        ) {
            for (index in 0 until menuItemCount) {
                key(menuItemKey(index)) { menuItemContent(index) { onMenuExpandedChange(false) } }
            }
        }
    }
}
