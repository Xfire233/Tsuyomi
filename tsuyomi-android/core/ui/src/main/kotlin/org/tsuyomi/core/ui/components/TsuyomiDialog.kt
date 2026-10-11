/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.BorderStroke
import org.tsuyomi.core.display.displayGrayscaleLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

/**
 * Shared semantic dialog model.
 *
 * Standard profile: an opaque content card inside a platform dialog (scrim allowed), dismiss on
 * back/outside tap unless [destructive].
 *
 * E-ink profile: a fully opaque paper surface covering the whole window in a focusable popup —
 * no scrim, no window dim, no alpha, no blur, no shadow. The background is inert and removed from
 * the accessibility tree by [AppScaffold] while any modal is open. Focus enters the dialog and is
 * restored to [restoreFocusTo] after dismissal. Informational dialogs dismiss via back or the
 * explicit action; destructive confirmations can only leave through an explicit button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TsuyomiDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    destructive: Boolean = false,
    restoreFocusTo: FocusRequester? = null,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val modalController = LocalTsuyomiModalController.current
    DisposableEffect(Unit) {
        modalController.push()
        onDispose {
            modalController.pop()
            if (restoreFocusTo != null) {
                // The trigger may already have left the composition; restoring is best-effort.
                try {
                    restoreFocusTo.requestFocus()
                } catch (_: IllegalStateException) {
                    // Trigger is gone; focus falls back to the default traversal order.
                }
            }
        }
    }

    val effectiveDismissLabel = dismissLabel ?: if (confirmLabel == null || destructive) {
        stringResource(R.string.coreui_close)
    } else {
        null
    }
    val dialogStateHolder = rememberSaveableStateHolder()

    if (eInk) {
        Popup(
            popupPositionProvider = FullWindowPopupPositionProvider,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = !destructive,
                dismissOnClickOutside = false,
                clippingEnabled = false,
            ),
            onDismissRequest = onDismissRequest,
        ) {
            dialogStateHolder.SaveableStateProvider("dialog-pane:EINK") {
                TsuyomiDialogPane(
                    title = title,
                    modifier = modifier.displayGrayscaleLayer(),
                    text = text,
                    body = body,
                    confirmLabel = confirmLabel,
                    onConfirm = onConfirm,
                    confirmEnabled = confirmEnabled,
                    dismissLabel = effectiveDismissLabel,
                    onDismiss = onDismissRequest,
                    destructive = destructive,
                    fullWindow = true,
                )
            }
        }
    } else {
        BasicAlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            properties = DialogProperties(
                dismissOnBackPress = !destructive,
                dismissOnClickOutside = !destructive,
                usePlatformDefaultWidth = false,
            ),
        ) {
            dialogStateHolder.SaveableStateProvider("dialog-pane:STANDARD") {
                TsuyomiDialogPane(
                    title = title,
                    text = text,
                    body = body,
                    confirmLabel = confirmLabel,
                    onConfirm = onConfirm,
                    confirmEnabled = confirmEnabled,
                    dismissLabel = effectiveDismissLabel,
                    onDismiss = onDismissRequest,
                    destructive = destructive,
                    fullWindow = false,
                )
            }
        }
    }
}

/**
 * The shared dialog content used by both profile implementations. Public so previews and tests
 * can render the exact E-ink full-window surface without a window manager.
 */
@Composable
fun TsuyomiDialogPane(
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false,
    fullWindow: Boolean,
) {
    val dismissActionFocus = remember { FocusRequester() }
    val confirmActionFocus = remember { FocusRequester() }
    val shape = RoundedCornerShape(if (fullWindow) 0.dp else 16.dp)

    val containerModifier = if (fullWindow) {
        modifier
            .fillMaxSize()
            .background(TsuyomiEInkPalette.Paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = TsuyomiSpacing.Xl)
    }

    val surfaceColor = if (fullWindow) {
        TsuyomiEInkPalette.Paper
    } else {
        MaterialTheme.colorScheme.surface
    }
    if (fullWindow) {
        var initialFocusRequested by remember { mutableStateOf(false) }
        val textCount = if (text == null) 0 else 1
        val dismissCount = if (dismissLabel != null && onDismiss != null) 1 else 0
        val bodyCount = if (body == null) 0 else 1
        val confirmCount = if (confirmLabel != null && onConfirm != null) 1 else 0
        val textIndex = 1 + dismissCount
        val bodyIndex = textIndex + textCount
        val confirmIndex = bodyIndex + bodyCount
        Surface(
            modifier = containerModifier.semantics { paneTitle = title },
            color = TsuyomiEInkPalette.Paper,
            contentColor = TsuyomiEInkPalette.Ink,
        ) {
            Column(Modifier.fillMaxSize().padding(TsuyomiSpacing.Sm)) {
                TsuyomiMeasuredPager(
                    itemCount = 1 + dismissCount + textCount + bodyCount + confirmCount,
                    itemKey = { index -> when {
                        index == 0 -> "dialog-title:$title"
                        dismissCount == 1 && index == 1 -> "dialog-dismiss:$dismissLabel"
                        textCount == 1 && index == textIndex -> "dialog-text:$text"
                        bodyCount == 1 && index == bodyIndex -> "dialog-body"
                        else -> "dialog-confirm:$confirmLabel"
                    } },
                    itemContent = { index -> when {
                        index == 0 -> TsuyomiMeasuredTextPager(title,
                            style = MaterialTheme.typography.headlineMedium,
                            fillViewport = false, alwaysShowPageControls = false)
                        dismissCount == 1 && index == 1 -> TsuyomiButton(
                            text = dismissLabel.orEmpty(),
                            onClick = requireNotNull(onDismiss),
                            style = TsuyomiButtonStyle.SECONDARY,
                            modifier = Modifier.focusRequester(dismissActionFocus).onGloballyPositioned {
                                if (!initialFocusRequested) {
                                    dismissActionFocus.requestFocus()
                                    initialFocusRequested = true
                                }
                            },
                        )
                        textCount == 1 && index == textIndex -> TsuyomiMeasuredTextPager(text.orEmpty(),
                            fillViewport = false, alwaysShowPageControls = false)
                        bodyCount == 1 && index == bodyIndex -> Column(
                            Modifier.fillMaxWidth().heightIn(max = LocalTsuyomiPagerMaxContentHeight.current ?: androidx.compose.ui.unit.Dp.Unspecified),
                        ) { body?.invoke(this) }
                        confirmCount == 1 && index == confirmIndex -> TsuyomiButton(
                            text = confirmLabel.orEmpty(),
                            onClick = requireNotNull(onConfirm),
                            enabled = confirmEnabled,
                            modifier = Modifier.focusRequester(confirmActionFocus).onGloballyPositioned {
                                if (dismissCount == 0 && !destructive && !initialFocusRequested) {
                                    confirmActionFocus.requestFocus()
                                    initialFocusRequested = true
                                }
                            },
                        )
                    } },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalSpacing = TsuyomiSpacing.Sm,
                )
            }
        }
        return
    }

    Surface(
        modifier = containerModifier.semantics { paneTitle = title },
        shape = shape,
        color = surfaceColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (fullWindow) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Box(
            modifier = if (fullWindow) Modifier.fillMaxSize() else Modifier,
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .then(if (fullWindow) Modifier.fillMaxHeight() else Modifier)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(TsuyomiSpacing.Lg),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(
                    modifier = Modifier
                        .padding(top = TsuyomiSpacing.Md)
                        .then(
                            if (fullWindow) {
                                Modifier.fillMaxWidth().weight(1f)
                            } else {
                                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())
                            },
                        ),
                ) {
                    if (text != null) {
                        if (fullWindow && body == null) {
                            TsuyomiMeasuredTextPager(
                                text = text,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    body?.invoke(this)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TsuyomiSpacing.Lg),
                    horizontalArrangement = Arrangement.spacedBy(
                        TsuyomiSpacing.Sm,
                        Alignment.End,
                    ),
                ) {
                    if (dismissLabel != null && onDismiss != null) {
                        TsuyomiButton(
                            text = dismissLabel,
                            onClick = onDismiss,
                            style = TsuyomiButtonStyle.TEXT,
                            modifier = if (destructive || confirmLabel == null) {
                                Modifier.focusRequester(dismissActionFocus).focusProperties { canFocus = true }
                            } else {
                                Modifier
                            },
                        )
                    }
                    if (confirmLabel != null && onConfirm != null) {
                        TsuyomiButton(
                            text = confirmLabel,
                            onClick = onConfirm,
                            enabled = confirmEnabled,
                            style = TsuyomiButtonStyle.PRIMARY,
                            modifier = if (!destructive || dismissLabel == null || onDismiss == null) {
                                Modifier.focusRequester(confirmActionFocus).focusProperties { canFocus = confirmEnabled }
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(destructive, dismissLabel, onDismiss, confirmLabel, onConfirm) {
        val initialFocus = when {
            destructive && dismissLabel != null && onDismiss != null -> dismissActionFocus
            confirmLabel != null && onConfirm != null -> confirmActionFocus
            dismissLabel != null && onDismiss != null -> dismissActionFocus
            else -> return@LaunchedEffect
        }
        try {
            initialFocus.requestFocus()
        } catch (_: IllegalStateException) {
            // No action button exists; the pane itself receives focus through the popup window.
        }
    }
}

/** Positions a popup at the window origin so its fill-size content covers the whole window. */
internal object FullWindowPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
