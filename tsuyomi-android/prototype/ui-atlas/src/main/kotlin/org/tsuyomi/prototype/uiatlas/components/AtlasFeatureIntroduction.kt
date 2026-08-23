/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

/** Fixture-only first-entry explanation. Dismissal records no capability and starts no work. */
@Composable
fun AtlasFeatureIntroduction(
    featureId: String,
    tutorialVersion: Int,
    title: String,
    summary: String,
    points: List<String>,
    onDismiss: () -> Unit,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = !eInk,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = if (eInk) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(AtlasSpacing.Lg).widthIn(max = 560.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (eInk) 1.5.dp else 1.dp, if (eInk) AtlasEInkPalette.Ink else MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(AtlasSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
                ) {
                    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    Text("$featureId · 说明版本 $tutorialVersion", style = MaterialTheme.typography.labelMedium)
                    Text(summary, style = MaterialTheme.typography.bodyLarge)
                    points.forEach { point ->
                        Row {
                            Text("•")
                            Text(point, Modifier.padding(start = AtlasSpacing.Sm), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    AtlasInfoBanner(
                        AtlasBanner(
                            title = "说明不是授权",
                            message = "关闭或标记已读只控制帮助内容；不会授予来源能力、触发网络或写入，也不替代后续确认。",
                        ),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        AtlasButton("稍后再看", onDismiss, style = AtlasButtonStyle.SECONDARY)
                        AtlasButton("知道了", onDismiss, modifier = Modifier.padding(start = AtlasSpacing.Sm))
                    }
                }
            }
        }
    }
}
