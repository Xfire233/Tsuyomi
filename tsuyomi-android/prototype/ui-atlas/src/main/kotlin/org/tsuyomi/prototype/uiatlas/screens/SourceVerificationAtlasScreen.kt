/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasStateKind
import org.tsuyomi.prototype.uiatlas.components.AtlasStateView
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment
// -- #18 verification -----------------------------------------------------------------------

@Composable
internal fun SourceVerification(context: AtlasContext, modifier: Modifier) {
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val scope = rememberCoroutineScope()
    val retry: () -> Unit = { scope.launch { runtime.scenarios.run("source-verification", "atlas.pine") } }
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            AtlasTopBar(
                title = "登录验证",
                subtitle = "源·松 · atlas.pine",
                onUp = navigation.up,
            )
        },
    ) {
        when (context.primaryState) {
            AtlasPageState.ERROR -> AtlasStateView(
                AtlasStateKind.ERROR,
                "验证页面加载失败",
                message = "页面视图无法加载（无网络）；失败不会产生任何写入。",
                actionLabel = AtlasStrings.RETRY,
                onAction = retry,
            )
            AtlasPageState.LOADING -> AtlasStateView(AtlasStateKind.LOADING, AtlasStrings.LOADING)
            else -> VerificationContent(
                onComplete = {
                    repository.putBoolean("source.atlas.pine.verified", true, "SourceVerificationCompleted", "atlas.pine")
                    navigation.up()
                },
                onCancel = {
                    repository.record("SourceVerificationCancelled", "atlas.pine", "cancelled")
                    navigation.up()
                },
            )
        }
    }
}

@Composable
private fun VerificationContent(onComplete: () -> Unit, onCancel: () -> Unit) {
    val eInk = LocalAtlasEnvironment.current.eInk
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AtlasSpacing.Md),
    ) {
        Surface(
            border = if (eInk) BorderStroke(1.5.dp, AtlasEInkPalette.N90) else null,
            color = if (eInk) AtlasEInkPalette.Paper else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(Modifier.padding(AtlasSpacing.Md)) {
                Icon(AtlasIcons.Info, contentDescription = null)
                Column(Modifier.padding(start = AtlasSpacing.Md)) {
                    Text(SourceAtlasFixtures.VERIFICATION_HOST_NOTICE, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        SourceAtlasFixtures.VERIFICATION_STATUS_WAITING,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        VerificationStub(Modifier.padding(top = AtlasSpacing.Md))
        Text(
            SourceAtlasFixtures.VERIFICATION_STUB_CAPTION,
            modifier = Modifier.padding(top = AtlasSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AtlasButton(
            "我已完成验证",
            onComplete,
            Modifier
                .fillMaxWidth()
                .padding(top = AtlasSpacing.Lg),
        )
        AtlasButton(
            "取消验证",
            onCancel,
            Modifier
                .fillMaxWidth()
                .padding(top = AtlasSpacing.Sm),
            AtlasButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun VerificationStub(modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.padding(AtlasSpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("网页视图占位（合成画面 · 无网络）", style = MaterialTheme.typography.labelLarge)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(top = AtlasSpacing.Md),
            ) {
                val cell = size.height / 3f
                val startX = (size.width - cell * 3f) / 2f
                repeat(3) { row ->
                    repeat(3) { col ->
                        drawRect(
                            ink,
                            Offset(startX + col * cell, row * cell),
                            Size(cell, cell),
                            style = Stroke(2f),
                        )
                    }
                }
                drawRect(ink, Offset(startX + cell, cell), Size(cell, cell))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AtlasIcons.Verify, contentDescription = null)
                Text(
                    "选择包含「灯」的图片（合成校验控件）",
                    modifier = Modifier.padding(start = AtlasSpacing.Sm),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
