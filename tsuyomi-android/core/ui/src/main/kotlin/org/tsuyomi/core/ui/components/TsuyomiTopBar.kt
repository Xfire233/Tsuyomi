/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.R

/**
 * Fixed application top bar. It never collapses and never reacts to scroll. At most one action
 * slot exists by construction, which satisfies the double-compact window chrome rule.
 */
@Composable
fun TsuyomiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val dividerColor = if (eInk) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = (if (eInk) 1.5.dp else 1.dp).toPx()
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, size.height - strokeWidth / 2f),
                    end = Offset(size.width, size.height - strokeWidth / 2f),
                    strokeWidth = strokeWidth,
                )
            }
            .semantics { paneTitle = title },
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onNavigateUp != null) {
                TsuyomiIconButton(
                    imageVector = TsuyomiIcons.Back,
                    contentDescription = stringResource(R.string.coreui_navigate_up),
                    onClick = onNavigateUp,
                )
            } else {
                Box(Modifier.size(48.dp))
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (action != null) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { action() }
            } else {
                Box(Modifier.size(48.dp))
            }
        }
    }
}
