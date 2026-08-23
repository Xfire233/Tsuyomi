/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing

/**
 * Fixed-chrome screen scaffold for the atlas (constitution §4.1): AppBar → Content → optional
 * PersistentFooter, in that order, with nothing collapsing or reacting to scroll. The optional
 * [floatingAction] slot exists for comparison variant A (creation placement: app bar vs FAB); it
 * is hidden by the caller whenever the action is invalid.
 */
@Composable
fun AtlasScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    floatingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                topBar()
                Box(
                    Modifier
                        .weight(1f)
                        .then(
                            if (footer == null) {
                                Modifier.windowInsetsPadding(
                                    WindowInsets.safeDrawing
                                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                        .union(WindowInsets.safeGestures.only(WindowInsetsSides.Bottom)),
                                )
                            } else {
                                Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            },
                        ),
                ) {
                    content()
                }
                if (footer != null) {
                    Box(
                        Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                .union(WindowInsets.safeGestures.only(WindowInsetsSides.Bottom)),
                        ),
                    ) {
                        footer()
                    }
                }
            }
            if (floatingAction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                .union(WindowInsets.safeGestures.only(WindowInsetsSides.Bottom)),
                        )
                        .padding(AtlasSpacing.Md),
                ) {
                    floatingAction()
                }
            }
        }
    }
}
