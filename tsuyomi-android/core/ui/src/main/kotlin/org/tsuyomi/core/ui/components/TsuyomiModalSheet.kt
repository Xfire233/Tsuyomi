/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.display.displayGrayscaleLayer
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier

/**
 * Core-owned modal sheet state facade. Feature code can preserve partial/expanded transitions
 * without importing Material sheet types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class TsuyomiModalSheetController internal constructor(
    internal val delegate: SheetState,
) {
    internal var instantDismiss: (() -> Unit)? = null
    val isExpanded: Boolean
        get() = instantDismiss != null || delegate.currentValue == SheetValue.Expanded

    val targetExpanded: Boolean
        get() = instantDismiss != null || delegate.targetValue == SheetValue.Expanded

    internal var programmaticPartial = false
    internal var swipeDismissInFlight = false

    suspend fun expand() {
        if (instantDismiss == null) delegate.expand()
    }

    /** Falls back to hiding when layout supplies no partial-expanded anchor. */
    suspend fun partialExpand() {
        instantDismiss?.let { it(); return }
        programmaticPartial = true
        try {
            if (delegate.hasPartiallyExpandedState) delegate.partialExpand() else delegate.hide()
        } finally {
            programmaticPartial = false
        }
    }

    suspend fun hide() {
        val dismiss = instantDismiss
        if (dismiss == null) delegate.hide() else dismiss()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberTsuyomiModalSheetController(
    allowPartiallyExpanded: Boolean = false,
    onExpandedSwipeDismiss: (() -> Unit)? = null,
): TsuyomiModalSheetController {
    val scope = rememberCoroutineScope()
    val latestSwipeDismiss = rememberUpdatedState(onExpandedSwipeDismiss)
    val controllerHolder = remember { arrayOfNulls<TsuyomiModalSheetController>(1) }
    val delegate = rememberModalBottomSheetState(
        skipPartiallyExpanded = !allowPartiallyExpanded,
        confirmValueChange = { target ->
            val current = controllerHolder[0]
            if (target == SheetValue.PartiallyExpanded && latestSwipeDismiss.value != null &&
                current != null && !current.programmaticPartial && (current.isExpanded || current.targetExpanded)
            ) {
                if (!current.swipeDismissInFlight) {
                    current.swipeDismissInFlight = true
                    scope.launch {
                        try {
                            current.hide()
                            latestSwipeDismiss.value?.invoke()
                        } finally {
                            current.swipeDismissInFlight = false
                        }
                    }
                }
                false
            } else true
        },
    )
    val controller = remember(delegate) { TsuyomiModalSheetController(delegate) }
    SideEffect { controllerHolder[0] = controller }
    return controller
}

/** Host-owned Material 3 modal sheet for feature-supplied semantic content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TsuyomiModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    controller: TsuyomiModalSheetController? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val colorScheme = MaterialTheme.colorScheme
    val resolvedController = controller ?: rememberTsuyomiModalSheetController()
    val modalController = LocalTsuyomiModalController.current
    val latestDismiss = rememberUpdatedState(onDismissRequest)
    if (eInk) {
        DisposableEffect(resolvedController) {
            modalController.push()
            resolvedController.instantDismiss = { latestDismiss.value() }
            onDispose {
                resolvedController.instantDismiss = null
                modalController.pop()
            }
        }
        Popup(
            popupPositionProvider = FullWindowPopupPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = false, clippingEnabled = false),
        ) {
            androidx.compose.material3.Surface(
                modifier = modifier.fillMaxSize().displayGrayscaleLayer(),
                color = TsuyomiEInkPalette.Paper,
                contentColor = TsuyomiEInkPalette.Ink,
            ) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                    TsuyomiButton(
                        text = stringResource(R.string.coreui_close),
                        onClick = onDismissRequest,
                        style = TsuyomiButtonStyle.SECONDARY,
                        modifier = Modifier.align(Alignment.End).padding(TsuyomiSpacing.Sm),
                    )
                    Column(Modifier.fillMaxWidth().weight(1f), content = content)
                }
            }
        }
        return
    }
    val sheetShape = if (eInk) RoundedCornerShape(4.dp) else BottomSheetDefaults.ExpandedShape
    val sheetModifier = if (eInk) {
        modifier.border(BorderStroke(1.dp, colorScheme.outline), sheetShape)
    } else {
        modifier
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = resolvedController.delegate,
        modifier = sheetModifier.displayGrayscaleLayer(),
        sheetGesturesEnabled = !eInk,
        shape = sheetShape,
        containerColor = if (eInk) TsuyomiEInkPalette.Paper else colorScheme.surfaceContainerLow,
        contentColor = colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = if (eInk) Color.Transparent else BottomSheetDefaults.ScrimColor,
        dragHandle = if (eInk) null else { { BottomSheetDefaults.DragHandle() } },
        properties = if (eInk) {
            ModalBottomSheetProperties(shouldDismissOnClickOutside = false)
        } else {
            ModalBottomSheetDefaults.properties
        },
        content = content,
    )
}
