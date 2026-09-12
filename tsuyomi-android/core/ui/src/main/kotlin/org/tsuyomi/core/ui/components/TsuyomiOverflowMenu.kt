/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

@Immutable
data class TsuyomiOverflowAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val menu: List<TsuyomiOverflowAction> = emptyList(),
    /** Optional non-interactive heading rendered before this item in an anchored menu. */
    val section: String? = null,
    val selected: Boolean? = null,
)

/** Material 3 action menu anchored to its own 48dp overflow trigger. */
@Composable
fun TsuyomiOverflowMenu(
    actions: List<TsuyomiOverflowAction>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    triggerIcon: ImageVector = TsuyomiIcons.Overflow,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    requestedSubmenu: TsuyomiOverflowAction? = null,
    panelTitle: String? = null,
    trigger: @Composable ((onClick: () -> Unit) -> Unit)? = null,
) {
    if (actions.isEmpty()) return
    var internalExpanded by remember { mutableStateOf(false) }
    val isExpanded = expanded ?: internalExpanded
    fun setExpanded(value: Boolean) {
        if (onExpandedChange == null) internalExpanded = value else onExpandedChange(value)
    }
    var submenu by remember(isExpanded) { mutableStateOf(requestedSubmenu) }
    val panelMaxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height.toDp() - 96.dp).coerceAtLeast(48.dp)
    }
    Box(modifier) {
        if (trigger == null) {
            TsuyomiIconButton(
                imageVector = triggerIcon,
                contentDescription = contentDescription,
                onClick = { setExpanded(true) },
            )
        } else {
            trigger { setExpanded(true) }
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = {
                setExpanded(false)
                submenu = null
            },
            modifier = Modifier.widthIn(min = 176.dp, max = 320.dp).heightIn(max = panelMaxHeight),
            offset = DpOffset(x = 0.dp, y = TsuyomiSpacing.Xs),
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            panelTitle?.let { title ->
                Text(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                        .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                HorizontalDivider()
            }
            submenu?.let { parent ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = parent.label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = if (panelTitle != null || submenu != null) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = { submenu = null },
                    leadingIcon = {
                        Icon(
                            imageVector = TsuyomiIcons.Back,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Md),
                )
                HorizontalDivider()
            }
            var previousSection: String? = null
            (submenu?.menu ?: actions).forEach { action ->
                action.section?.takeIf { it != previousSection }?.let { section ->
                    if (previousSection != null) HorizontalDivider()
                    Text(
                        text = section,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { heading() }
                            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    previousSection = section
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (action.destructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = if (panelTitle != null || submenu != null) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        if (action.menu.isEmpty()) {
                            setExpanded(false)
                            submenu = null
                            action.onClick()
                        } else {
                            submenu = action
                        }
                    },
                    leadingIcon = action.icon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (action.destructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                    trailingIcon = when {
                        action.selected == true -> {
                            {
                                Icon(
                                    imageVector = TsuyomiIcons.Selected,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        action.menu.isNotEmpty() -> {
                            {
                                Icon(
                                    imageVector = TsuyomiIcons.Next,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        else -> null
                    },
                    enabled = action.enabled,
                    modifier = Modifier.semantics { action.selected?.let { selected = it } },
                    contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Md),
                )
            }
        }
    }
}
