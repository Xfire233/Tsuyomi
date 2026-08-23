/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tsuyomi.prototype.uiatlas.theme.AtlasStateArt
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment
import org.tsuyomi.prototype.uiatlas.theme.atlasFocusRing

/** The three primary page states (constitution §9.1); overlays never use this surface. */
enum class AtlasStateKind {
    LOADING,
    EMPTY,
    ERROR,
}

/**
 * Full-area state surface with stable geometry (constitution §9.1). Loading reserves the same
 * footprint and shows a text status — never a spinner — in every profile. EMPTY always pairs a
 * reason with at most one primary action; ERROR pairs a readable cause with an explicit retry.
 * Actions render only when a real handler is supplied.
 */
@Composable
fun AtlasStateView(
    kind: AtlasStateKind,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val environment = LocalAtlasEnvironment.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(AtlasSpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (environment.stateArt) {
            AtlasStateArt.TYPOGRAPHIC -> Unit
            AtlasStateArt.EMOTICON -> Text("(｡•́︿•̀｡)", style = MaterialTheme.typography.displaySmall)
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = AtlasSpacing.Lg),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Text(
                text = message,
                modifier = Modifier.padding(top = AtlasSpacing.Sm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            AtlasButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(top = AtlasSpacing.Lg),
                style = AtlasButtonStyle.PRIMARY,
            )
        }
    }
}

/** Visual and semantic intent of an [AtlasButton]; only one primary action is visible per surface. */
enum class AtlasButtonStyle {
    PRIMARY,
    SECONDARY,
    TEXT,
    DESTRUCTIVE,
}

/**
 * Semantic button wrapper. Standard delegates directly to the real Material 3 button family;
 * E-ink keeps the same public semantics while supplying an opaque high-contrast palette.
 */
@Composable
fun AtlasButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AtlasButtonStyle = AtlasButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val buttonModifier = modifier.heightIn(min = 48.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (style == AtlasButtonStyle.DESTRUCTIVE) {
            Icon(
                imageVector = AtlasIcons.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(text = text, textAlign = TextAlign.Center)
    }
    when (style) {
        AtlasButtonStyle.PRIMARY -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            colors = if (eInk) {
                ButtonDefaults.buttonColors(
                    containerColor = AtlasEInkPalette.Ink,
                    contentColor = AtlasEInkPalette.Paper,
                    disabledContainerColor = AtlasEInkPalette.Paper,
                    disabledContentColor = AtlasEInkPalette.N50,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            content = content,
        )
        AtlasButtonStyle.SECONDARY -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            border = BorderStroke(
                if (eInk) 1.5.dp else 1.dp,
                if (eInk) AtlasEInkPalette.Ink else MaterialTheme.colorScheme.outline,
            ),
            colors = if (eInk) {
                ButtonDefaults.outlinedButtonColors(
                    contentColor = AtlasEInkPalette.Ink,
                    disabledContentColor = AtlasEInkPalette.N50,
                )
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
            content = content,
        )
        AtlasButtonStyle.TEXT -> TextButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            colors = if (eInk) {
                ButtonDefaults.textButtonColors(
                    contentColor = AtlasEInkPalette.Ink,
                    disabledContentColor = AtlasEInkPalette.N50,
                )
            } else {
                ButtonDefaults.textButtonColors()
            },
            content = content,
        )
        AtlasButtonStyle.DESTRUCTIVE -> FilledTonalButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            colors = if (eInk) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = AtlasEInkPalette.Paper,
                    contentColor = AtlasEInkPalette.Ink,
                    disabledContainerColor = AtlasEInkPalette.Paper,
                    disabledContentColor = AtlasEInkPalette.N50,
                )
            } else {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            content = content,
        )
    }
}

/** Immutable banner model for persistent overlay feedback (offline / refreshing / mutation). */
@Immutable
data class AtlasBanner(
    val title: String,
    val message: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val errorTone: Boolean = false,
)

/**
 * A persistent banner pinned under the app bar for overlay states (constitution §4.1/§9.1): it
 * never replaces content and announces itself through a polite live region. Meaning is carried by
 * icon + text, never color alone.
 */
@Composable
fun AtlasInfoBanner(
    banner: AtlasBanner,
    modifier: Modifier = Modifier,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val scheme = MaterialTheme.colorScheme
    val container = when {
        eInk -> AtlasEInkPalette.Paper
        banner.errorTone -> scheme.errorContainer
        else -> scheme.surfaceContainerHigh
    }
    val content = when {
        eInk -> AtlasEInkPalette.Ink
        banner.errorTone -> scheme.onErrorContainer
        else -> scheme.onSurface
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
                imageVector = if (banner.errorTone) AtlasIcons.Warning else AtlasIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AtlasSpacing.Md),
            ) {
                Text(text = banner.title, style = MaterialTheme.typography.labelLarge)
                if (banner.message != null) {
                    Text(text = banner.message, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (banner.actionLabel != null && banner.onAction != null) {
                AtlasButton(
                    text = banner.actionLabel,
                    onClick = banner.onAction,
                    style = AtlasButtonStyle.TEXT,
                )
            }
        }
    }
}
