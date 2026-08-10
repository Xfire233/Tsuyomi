/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

/** The single primary state of a screen. Offline/refreshing are overlays, never this state. */
enum class TsuyomiStateKind {
    LOADING,
    EMPTY,
    ERROR,
}

/**
 * Full-area state surface with stable geometry. Loading uses reserved space and a text status —
 * never a spinner — in every profile. Actions only appear when a real handler is supplied.
 */
@Composable
fun StateView(
    kind: TsuyomiStateKind,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(TsuyomiSpacing.Xl)
            .semantics(mergeDescendants = true) {
                if (kind == TsuyomiStateKind.LOADING) {
                    liveRegion = LiveRegionMode.Polite
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatePlaceholderBlock(eInk = eInk)
        Text(
            text = title,
            modifier = Modifier.padding(top = TsuyomiSpacing.Lg),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Text(
                text = message,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            TsuyomiButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(top = TsuyomiSpacing.Lg),
                style = if (kind == TsuyomiStateKind.ERROR) {
                    TsuyomiButtonStyle.PRIMARY
                } else {
                    TsuyomiButtonStyle.SECONDARY
                },
            )
        }
    }
}

/** Static placeholder geometry shared by all state kinds; identical size in every profile. */
@Composable
private fun StatePlaceholderBlock(eInk: Boolean) {
    val shape = RoundedCornerShape(if (eInk) 4.dp else 16.dp)
    val modifier = if (eInk) {
        Modifier
            .size(96.dp)
            .border(1.5.dp, TsuyomiEInkPalette.Ink, shape)
    } else {
        Modifier
            .size(96.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
    }
    Box(modifier)
}

/**
 * A persistent inline status line (for example the effective display profile and its reason).
 * It is always visible and never auto-dismissed.
 */
@Composable
fun InlineStatus(
    text: String,
    modifier: Modifier = Modifier,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val shape = RoundedCornerShape(if (eInk) 4.dp else 12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (eInk) 1.5.dp else 1.dp,
                color = if (eInk) {
                    TsuyomiEInkPalette.Ink
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A persistent banner for overlay states (offline, save failure). It never replaces content and
 * announces itself through a polite live region. Actions are explicit buttons.
 */
@Composable
fun InfoBanner(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(if (eInk) 4.dp else 12.dp),
        color = if (eInk) {
            TsuyomiEInkPalette.Paper
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (eInk) {
            BorderStroke(1.5.dp, TsuyomiEInkPalette.Ink)
        } else {
            null
        },
    ) {
        Column(Modifier.padding(TsuyomiSpacing.Md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = TsuyomiSpacing.Xs),
                )
            }
            if (primaryActionLabel != null || dismissLabel != null) {
                Row(
                    modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                ) {
                    if (primaryActionLabel != null && onPrimaryAction != null) {
                        TsuyomiButton(
                            text = primaryActionLabel,
                            onClick = onPrimaryAction,
                            style = TsuyomiButtonStyle.PRIMARY,
                        )
                    }
                    if (dismissLabel != null && onDismiss != null) {
                        TsuyomiButton(
                            text = dismissLabel,
                            onClick = onDismiss,
                            style = TsuyomiButtonStyle.TEXT,
                        )
                    }
                }
            }
        }
    }
}
