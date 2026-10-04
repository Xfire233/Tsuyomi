/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

import org.tsuyomi.core.ui.theme.activeAccent
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
    var submenuPath by remember(isExpanded) { mutableStateOf(requestedSubmenu?.let(::listOf).orEmpty()) }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val safeInsets = WindowInsets.safeDrawing
    val panelMaxHeight = with(density) {
        val safeTop = safeInsets.getTop(this)
        val safeBottom = safeInsets.getBottom(this)
        val availableBelow = windowHeight - safeBottom - (anchorBounds?.bottom?.toInt() ?: safeTop)
        val availableAbove = (anchorBounds?.top?.toInt() ?: windowHeight) - safeTop
        (maxOf(availableAbove, availableBelow).toDp() - TsuyomiSpacing.Md).coerceAtLeast(48.dp)
    }
    Box(modifier.onGloballyPositioned { if (!isExpanded) anchorBounds = it.boundsInWindow() }) {
        if (trigger == null) {
            TsuyomiIconButton(
                imageVector = triggerIcon,
                contentDescription = contentDescription,
                onClick = { setExpanded(true) },
            )
        } else {
            trigger { setExpanded(true) }
        }
        key(submenuPath.lastOrNull()?.label) {
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = {
                if (submenuPath.isNotEmpty()) {
                    submenuPath = submenuPath.dropLast(1)
                } else {
                    setExpanded(false)
                }
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
            submenuPath.lastOrNull()?.let { parent ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.coreui_menu_back_to,
                                submenuPath.dropLast(1).lastOrNull()?.label ?: panelTitle ?: stringResource(R.string.coreui_more_actions)),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = { submenuPath = submenuPath.dropLast(1) },
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
            (submenuPath.lastOrNull()?.menu ?: actions).forEach { action ->
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
                            maxLines = if (panelTitle != null || submenuPath.isNotEmpty()) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        if (action.menu.isEmpty()) {
                            setExpanded(false)
                            submenuPath = emptyList()
                            action.onClick()
                        } else {
                            submenuPath = submenuPath + action
                        }
                    },
                    leadingIcon = action.icon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = when {
                                    action.destructive -> MaterialTheme.colorScheme.error
                                    action.selected == true -> MaterialTheme.colorScheme.activeAccent
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                                    tint = MaterialTheme.colorScheme.activeAccent,
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
}
