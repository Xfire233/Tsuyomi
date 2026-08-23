/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.theme.atlasFocusRing

@Immutable
data class AtlasTopBarAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Immutable
data class AtlasTopBarSelector(
    val text: String,
    val accessibilityLabel: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Immutable
data class AtlasOverflowItem(
    val label: String,
    val onClick: () -> Unit,
)

@Immutable
data class AtlasSelectionBar(
    val count: Int,
    val onClose: () -> Unit,
    val allSelected: Boolean,
    val onToggleAll: () -> Unit,
    val bulkActions: List<AtlasTopBarAction> = emptyList(),
    val overflow: List<AtlasOverflowItem> = emptyList(),
)

@Composable
fun AtlasIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .atlasFocusRing(MaterialTheme.shapes.extraLarge, focused, MaterialTheme.colorScheme.primary),
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Real Material 3 app bar; selection replaces rather than stacks over the normal bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onUp: (() -> Unit)? = null,
    selector: AtlasTopBarSelector? = null,
    actions: List<AtlasTopBarAction> = emptyList(),
    overflow: List<AtlasOverflowItem> = emptyList(),
    selection: AtlasSelectionBar? = null,
    actionBudgetOverride: Int? = null,
) {
    val narrowWindow = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() < 360.dp }
    val actionBudget = actionBudgetOverride ?: if (narrowWindow) 1 else 2
    val visibleActions = actions.take(actionBudget)
    val foldedActions = actions.drop(actionBudget)
    val barTitle = if (selection != null) AtlasStrings.selectedCount(selection.count) else title
    TopAppBar(
        title = {
            Column(Modifier.semantics { heading(); paneTitle = barTitle }) {
                Text(barTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (selection == null && subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth().semantics { paneTitle = barTitle },
        navigationIcon = {
            when {
                selection != null -> AtlasIconButton(AtlasIcons.Close, "退出选择", selection.onClose)
                onUp != null -> AtlasIconButton(AtlasIcons.Back, AtlasStrings.NAVIGATE_UP, onUp)
            }
        },
        actions = {
            if (selection != null) {
                AtlasIconButton(
                    imageVector = if (selection.allSelected) AtlasIcons.Deselect else AtlasIcons.SelectAll,
                    contentDescription = if (selection.allSelected) AtlasStrings.CLEAR_SELECTION else AtlasStrings.SELECT_ALL,
                    onClick = selection.onToggleAll,
                )
                val applicableBulkActions = if (selection.count > 0) selection.bulkActions else emptyList()
                applicableBulkActions.take(actionBudget).forEach { action ->
                    AtlasIconButton(action.icon, action.label, action.onClick)
                }
                AtlasOverflowMenu(applicableBulkActions.drop(actionBudget), if (selection.count > 0) selection.overflow else emptyList())
            } else {
                if (selector != null) {
                    TextButton(onClick = selector.onClick, enabled = selector.enabled) {
                        Text(selector.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                visibleActions.forEach { action -> AtlasIconButton(action.icon, action.label, action.onClick) }
                AtlasOverflowMenu(foldedActions, overflow)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    )
}

@Composable
private fun AtlasOverflowMenu(
    folded: List<AtlasTopBarAction>,
    items: List<AtlasOverflowItem>,
) {
    if (folded.isEmpty() && items.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    AtlasIconButton(AtlasIcons.Overflow, AtlasStrings.OVERFLOW, { expanded = true })
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        folded.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                onClick = { expanded = false; action.onClick() },
                leadingIcon = { Icon(action.icon, contentDescription = null) },
            )
        }
        items.forEach { item ->
            DropdownMenuItem(
                text = { Text(item.label) },
                onClick = { expanded = false; item.onClick() },
            )
        }
    }
}
