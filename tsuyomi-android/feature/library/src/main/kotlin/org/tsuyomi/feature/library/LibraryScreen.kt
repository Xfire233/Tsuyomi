/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind

/**
 * Gate 1 library root: an honest empty state. The only action switches to the browse top-level
 * destination; there is no collection management, no fake list, and no placeholder entry.
 */
@Composable
fun LibraryScreen(
    onNavigateToBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StateView(
        kind = TsuyomiStateKind.EMPTY,
        title = stringResource(R.string.library_empty_title),
        message = stringResource(R.string.library_empty_message),
        actionLabel = stringResource(R.string.library_action_go_to_browse),
        onAction = onNavigateToBrowse,
        modifier = modifier,
    )
}
