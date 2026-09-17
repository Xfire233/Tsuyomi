/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Result of a caller-owned transient message action. */
enum class TsuyomiSnackbarResult { ACTION_PERFORMED, DISMISSED }

/** Core-owned snackbar state facade so features do not import Material transient controls. */
@Stable
class TsuyomiSnackbarState internal constructor(
    internal val delegate: SnackbarHostState,
) {
    suspend fun showMessage(message: String, actionLabel: String? = null): TsuyomiSnackbarResult =
        when (delegate.showSnackbar(message = message, actionLabel = actionLabel)) {
            SnackbarResult.ActionPerformed -> TsuyomiSnackbarResult.ACTION_PERFORMED
            SnackbarResult.Dismissed -> TsuyomiSnackbarResult.DISMISSED
        }
}

@Composable
fun rememberTsuyomiSnackbarState(): TsuyomiSnackbarState {
    val delegate = remember { SnackbarHostState() }
    return remember(delegate) { TsuyomiSnackbarState(delegate) }
}

@Composable
fun TsuyomiSnackbarHost(
    state: TsuyomiSnackbarState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = state.delegate, modifier = modifier)
}
