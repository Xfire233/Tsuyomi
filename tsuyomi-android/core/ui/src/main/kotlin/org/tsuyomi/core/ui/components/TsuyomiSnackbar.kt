/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
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
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        val data = state.delegate.currentSnackbarData ?: return
        val accessibilityManager = LocalAccessibilityManager.current
        LaunchedEffect(data) {
            val timeout = when (data.visuals.duration) {
                SnackbarDuration.Short -> 4_000L
                SnackbarDuration.Long -> 10_000L
                SnackbarDuration.Indefinite -> Long.MAX_VALUE
            }
            val accessibleTimeout = accessibilityManager?.calculateRecommendedTimeoutMillis(
                timeout, containsIcons = false, containsText = true, containsControls = true,
            ) ?: timeout
            delay(accessibleTimeout)
            data.dismiss()
        }
        val actionLabel = data.visuals.actionLabel
        val actionCount = if (actionLabel == null) 0 else 1
        Surface(modifier, color = TsuyomiEInkPalette.Paper, contentColor = TsuyomiEInkPalette.Ink,
            border = BorderStroke(1.5.dp, TsuyomiEInkPalette.Ink)) {
            TsuyomiMeasuredPager(
                itemCount = 2 + actionCount,
                itemKey = { index -> "snackbar:${data.visuals.message}:$index" },
                itemContent = { index ->
                    when {
                        index == 0 -> TsuyomiMeasuredTextPager(data.visuals.message, fillViewport = false, alwaysShowPageControls = false)
                        index == 1 && actionCount == 1 -> TsuyomiButton(actionLabel.orEmpty(), data::performAction)
                        else -> TsuyomiButton(stringResource(R.string.coreui_close), data::dismiss, style = TsuyomiButtonStyle.SECONDARY)
                    }
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                fillViewport = false,
            )
        }
        return
    }
    SnackbarHost(hostState = state.delegate, modifier = modifier)
}
