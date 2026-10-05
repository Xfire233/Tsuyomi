/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion

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
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
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
        StateKaomoji(kind = kind, title = title, message = message)
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
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            TsuyomiButton(
                text = secondaryActionLabel,
                onClick = onSecondaryAction,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                style = TsuyomiButtonStyle.SECONDARY,
            )
        }
    }
}

internal const val StateKaomojiTestTag = "state-kaomoji"
internal const val LoadingKaomojiFrameMillis = 700L
internal const val MinimumLoadingKaomojiFrameMillis = 650L
internal const val EmptyKaomoji = "(｡•́︿•̀｡)"

internal data class LoadingKaomojiFrame(
    val leadingAccessory: String = "",
    val mouth: String,
    val trailingAccessory: String = "",
) {
    val text: String = "$leadingAccessory(・$mouth・)$trailingAccessory"
}

internal val LoadingKaomojiFrames = listOf(
    LoadingKaomojiFrame(mouth = "_"),
    LoadingKaomojiFrame(mouth = "_", trailingAccessory = "ノ"),
    LoadingKaomojiFrame(mouth = "ω", trailingAccessory = "ノ本"),
    LoadingKaomojiFrame(leadingAccessory = "本ヽ", mouth = "ω"),
    LoadingKaomojiFrame(leadingAccessory = "ヽ", mouth = "_"),
)

internal val ErrorKaomojiFaces = listOf(
    "(｡•́︿•̀｡)",
    "(╥﹏╥)",
    "(＞﹏＜)",
    "(・へ・)",
    "(；￣Д￣)",
)

/** Decorative state art. The title, message and actions remain the complete accessibility signal. */
@Composable
private fun StateKaomoji(kind: TsuyomiStateKind, title: String, message: String?) {
    val staticMotion = LocalDisplayEnvironment.current.instantMotion ||
        rememberSystemReducedMotion() ||
        LocalInspectionMode.current
    var loadingFrame by remember(kind) { mutableIntStateOf(0) }

    LaunchedEffect(kind, staticMotion) {
        loadingFrame = 0
        if (kind == TsuyomiStateKind.LOADING && !staticMotion) {
            while (isActive) {
                delay(LoadingKaomojiFrameMillis)
                loadingFrame = (loadingFrame + 1) % LoadingKaomojiFrames.size
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag(StateKaomojiTestTag)
            .semantics { hideFromAccessibility() },
        contentAlignment = Alignment.Center,
    ) {
        if (kind == TsuyomiStateKind.LOADING) {
            LoadingKaomoji(LoadingKaomojiFrames[loadingFrame.mod(LoadingKaomojiFrames.size)])
        } else {
            Text(
                text = stateKaomoji(kind, title, message, loadingFrame),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
@Composable
private fun LoadingKaomoji(frame: LoadingKaomojiFrame) {
    val style = MaterialTheme.typography.displaySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.width(64.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(text = frame.leadingAccessory, style = style, color = color, maxLines = 1)
        }
        Text(text = "(・", style = style, color = color, maxLines = 1)
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = frame.mouth, style = style, color = color, maxLines = 1)
        }
        Text(text = "・)", style = style, color = color, maxLines = 1)
        Box(
            modifier = Modifier.width(64.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = frame.trailingAccessory, style = style, color = color, maxLines = 1)
        }
    }
}


internal fun stateKaomoji(
    kind: TsuyomiStateKind,
    title: String,
    message: String?,
    loadingFrame: Int = 0,
): String = when (kind) {
    TsuyomiStateKind.LOADING -> LoadingKaomojiFrames[loadingFrame.mod(LoadingKaomojiFrames.size)].text
    TsuyomiStateKind.EMPTY -> EmptyKaomoji
    TsuyomiStateKind.ERROR -> errorKaomoji(title, message)
}

internal fun errorKaomoji(title: String, message: String?): String {
    val seed = buildString {
        append(title)
        append('\u0000')
        append(message.orEmpty())
    }
    return ErrorKaomojiFaces[(seed.hashCode() and Int.MAX_VALUE) % ErrorKaomojiFaces.size]
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
