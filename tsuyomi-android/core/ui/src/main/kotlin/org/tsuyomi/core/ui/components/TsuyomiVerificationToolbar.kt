/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion

/** Full-screen verification route's compact unbacked horizontal action row. */
@Composable
fun TsuyomiVerificationToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Compact action for [TsuyomiVerificationToolbar]. While a request is in flight, instant-motion
 * surfaces receive an explicit textual state; standard surfaces retain the action's accessible
 * name while replacing its visual label with a compact progress indicator.
 */
@Composable
fun TsuyomiVerificationAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    working: Boolean = false,
    workingText: String = text,
) {
    val staticMotion = LocalDisplayEnvironment.current.instantMotion || rememberSystemReducedMotion()
    val actionModifier = modifier
        .heightIn(min = 48.dp)
        .semantics { contentDescription = text }
    val content: @Composable RowScope.() -> Unit = {
        if (working && !staticMotion) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(if (working) workingText else text)
        }
    }
    if (tonal) {
        FilledTonalButton(
            onClick = onClick,
            modifier = actionModifier,
            enabled = enabled && !working,
            contentPadding = PaddingValues(horizontal = 10.dp),
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            modifier = actionModifier,
            enabled = enabled && !working,
            contentPadding = PaddingValues(horizontal = 10.dp),
            content = content,
        )
    }
}
