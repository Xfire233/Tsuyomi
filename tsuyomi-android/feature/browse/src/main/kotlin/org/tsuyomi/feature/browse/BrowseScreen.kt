/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.browse

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind

/**
 * Gate 1 browse root: a static, honest statement that no content sources are available. There is
 * no install, search, or help entry in this gate.
 */
@Composable
fun BrowseScreen(modifier: Modifier = Modifier) {
    StateView(
        kind = TsuyomiStateKind.EMPTY,
        title = stringResource(R.string.browse_empty_title),
        message = stringResource(R.string.browse_empty_message),
        modifier = modifier,
    )
}
