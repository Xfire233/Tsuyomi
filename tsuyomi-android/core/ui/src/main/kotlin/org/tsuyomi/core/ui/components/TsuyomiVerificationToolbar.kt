/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Full-screen verification route's always-expanded horizontal Material 3 action dock. */
@Composable
fun TsuyomiVerificationToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        content = content,
    )
}
