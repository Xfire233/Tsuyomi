/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment

/**
 * Standard-only official Material pull-to-refresh container. E-ink keeps the same command through an
 * explicit labelled action and never receives a gesture-only path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TsuyomiPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val standard = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.STANDARD
    if (standard && enabled) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
        ) { content() }
    } else {
        Box(modifier.fillMaxSize()) { content() }
    }
}
