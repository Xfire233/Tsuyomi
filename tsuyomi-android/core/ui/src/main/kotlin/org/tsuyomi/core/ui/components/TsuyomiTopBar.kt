/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.icons.TsuyomiIcons

@Immutable
data class TsuyomiTopBarAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
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
) {
    val narrowWindow = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp() < 360.dp
    }
    val actionBudget = if (narrowWindow) 2 else 3
    val visibleActions = actions.take(actionBudget)
    val foldedActions = actions.drop(actionBudget).map { action ->
        TsuyomiOverflowAction(action.label, action.onClick, action.icon)
    }
    TopAppBar(
        title = {
            Column(Modifier.semantics { heading(); paneTitle = title }) {
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
                TsuyomiIconButton(action.icon, action.label, action.onClick)
            }
            TsuyomiOverflowMenu(
                actions = foldedActions + overflow,
                contentDescription = stringResource(R.string.coreui_more_actions),
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
