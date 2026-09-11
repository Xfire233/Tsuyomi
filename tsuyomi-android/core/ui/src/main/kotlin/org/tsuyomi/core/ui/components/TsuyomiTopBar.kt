/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

@Immutable
data class TsuyomiTopBarAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val menu: List<TsuyomiOverflowAction> = emptyList(),
    /** Optional visible title for this action's anchored menu panel. */
    val menuTitle: String? = null,
    val menuExpanded: Boolean? = null,
    val onMenuExpandedChange: ((Boolean) -> Unit)? = null,
    val testTag: String? = null,
)

/** An optional bounded source/context menu attached directly to the compact app-bar title. */
@Immutable
data class TsuyomiTopBarTitleMenu(
    val contentDescription: String,
    val actions: List<TsuyomiOverflowAction>,
    val panelTitle: String? = null,
    val testTag: String? = null,
)

/**
 * Fixed real-Material 3 application top bar. Actions fold into one anchored overflow menu when the
 * current window cannot preserve 48dp targets and readable title space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TsuyomiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onNavigateUp: (() -> Unit)? = null,
    navigationIcon: ImageVector = TsuyomiIcons.Back,
    navigationContentDescription: String? = null,
    actions: List<TsuyomiTopBarAction> = emptyList(),
    overflow: List<TsuyomiOverflowAction> = emptyList(),
    maxVisibleActions: Int? = null,
    titleMenu: TsuyomiTopBarTitleMenu? = null,
) {
    val narrowWindow = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp() < 360.dp
    }
    val actionBudget = maxVisibleActions ?: if (narrowWindow) 2 else 3
    val visibleActions = actions.take(actionBudget)
    val folded = actions.drop(actionBudget)
    val foldedActions = folded.map { action ->
        TsuyomiOverflowAction(action.menuTitle ?: action.label, action.onClick, action.icon, menu = action.menu)
    }
    var overflowExpanded by remember { mutableStateOf(false) }
    val requestedActionIndex = folded.indexOfFirst { it.menuExpanded == true }
    TopAppBar(
        title = {
            val menu = titleMenu?.takeIf { it.actions.isNotEmpty() }
            if (menu == null) {
                TopBarTitle(title, subtitle)
            } else {
                TsuyomiOverflowMenu(
                    actions = menu.actions,
                    contentDescription = menu.contentDescription,
                    modifier = menu.testTag?.let(Modifier::testTag) ?: Modifier,
                    panelTitle = menu.panelTitle,
                    trigger = { openMenu ->
                        Row(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClick = openMenu)
                                .semantics { contentDescription = menu.contentDescription },
                            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TopBarTitle(title, subtitle, Modifier.weight(1f, fill = false))
                            Icon(TsuyomiIcons.Disclosure, contentDescription = null)
                        }
                    },
                )
            }
        },
        modifier = modifier.fillMaxWidth().semantics { paneTitle = title },
        navigationIcon = {
            onNavigateUp?.let {
                TsuyomiIconButton(
                    imageVector = navigationIcon,
                    contentDescription = navigationContentDescription ?: stringResource(R.string.coreui_navigate_up),
                    onClick = it,
                )
            }
        },
        actions = {
            visibleActions.forEach { action ->
                if (action.menu.isEmpty()) {
                    TsuyomiIconButton(action.icon, action.label, action.onClick)
                } else {
                    TsuyomiOverflowMenu(
                        actions = action.menu,
                        contentDescription = action.label,
                        modifier = action.testTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier,
                        triggerIcon = action.icon,
                        expanded = action.menuExpanded,
                        onExpandedChange = action.onMenuExpandedChange,
                        panelTitle = action.menuTitle,
                    )
                }
            }
            TsuyomiOverflowMenu(
                actions = foldedActions + overflow,
                contentDescription = stringResource(R.string.coreui_more_actions),
                expanded = overflowExpanded || requestedActionIndex >= 0,
                requestedSubmenu = foldedActions.getOrNull(requestedActionIndex),
                onExpandedChange = { expanded ->
                    overflowExpanded = expanded
                    if (!expanded) folded.forEach { it.onMenuExpandedChange?.invoke(false) }
                },
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        windowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    )
}

@Composable
private fun TopBarTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.semantics { heading() }) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
