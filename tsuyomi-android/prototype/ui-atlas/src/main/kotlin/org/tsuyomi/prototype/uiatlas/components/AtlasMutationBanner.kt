/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

/**
 * Mutation phases under review (constitution §9.2/§9.3 taxonomy). `idle` never renders a banner;
 * every other phase is persistent until the next explicit state, never a transient toast.
 */
enum class AtlasMutationPhase {
    WORKING,
    SUCCESS,
    ERROR,
    CANCELLED,
    UNRESOLVED,
}

/**
 * Immutable mutation feedback: the phase, a message naming the affected target, and an optional
 * retry affordance (error/unresolved only). Working never carries a spinner — the text is the
 * state signal (constitution §9.1).
 */
@Immutable
data class AtlasMutationStatus(
    val phase: AtlasMutationPhase,
    val message: String,
    val retryLabel: String? = null,
    val onRetry: (() -> Unit)? = null,
)

/**
 * Persistent inline mutation result banner (constitution §9.2 MutationResultBanner contract).
 * Outcomes pair a glyph with text and announce through a polite live region; partial failure is
 * expressed by the message itself (`已更新 3 项，1 项失败`), a first-class distinct state.
 */
@Composable
fun AtlasMutationBanner(
    status: AtlasMutationStatus,
    modifier: Modifier = Modifier,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val scheme = MaterialTheme.colorScheme

    val icon: ImageVector
    val container: Color
    val content: Color
    when (status.phase) {
        AtlasMutationPhase.WORKING -> {
            icon = AtlasIcons.Info
            container = if (eInk) AtlasEInkPalette.Paper else scheme.surfaceContainerHigh
            content = if (eInk) AtlasEInkPalette.Ink else scheme.onSurface
        }
        AtlasMutationPhase.SUCCESS -> {
            icon = AtlasIcons.Check
            container = if (eInk) AtlasEInkPalette.Paper else scheme.primaryContainer
            content = if (eInk) AtlasEInkPalette.Ink else scheme.onPrimaryContainer
        }
        AtlasMutationPhase.ERROR,
        AtlasMutationPhase.UNRESOLVED,
        -> {
            icon = AtlasIcons.Warning
            container = if (eInk) AtlasEInkPalette.Paper else scheme.errorContainer
            content = if (eInk) AtlasEInkPalette.Ink else scheme.onErrorContainer
        }
        AtlasMutationPhase.CANCELLED -> {
            icon = AtlasIcons.Info
            container = if (eInk) AtlasEInkPalette.Paper else scheme.surfaceVariant
            content = if (eInk) AtlasEInkPalette.Ink else scheme.onSurfaceVariant
        }
    }

    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = container,
        contentColor = content,
        border = if (eInk) BorderStroke(1.5.dp, AtlasEInkPalette.Ink) else null,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = phaseLabel(status.phase),
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AtlasSpacing.Md),
            ) {
                Text(text = phaseLabel(status.phase), style = MaterialTheme.typography.labelLarge)
                Text(text = status.message, style = MaterialTheme.typography.bodySmall)
            }
            if (status.retryLabel != null && status.onRetry != null) {
                AtlasButton(
                    text = status.retryLabel,
                    onClick = status.onRetry,
                    style = AtlasButtonStyle.TEXT,
                )
            }
        }
    }
}

private fun phaseLabel(phase: AtlasMutationPhase): String = when (phase) {
    AtlasMutationPhase.WORKING -> AtlasStrings.MUTATION_WORKING
    AtlasMutationPhase.SUCCESS -> "已完成"
    AtlasMutationPhase.ERROR -> "操作失败"
    AtlasMutationPhase.CANCELLED -> "已取消"
    AtlasMutationPhase.UNRESOLVED -> AtlasStrings.UNRESOLVED_TITLE
}
