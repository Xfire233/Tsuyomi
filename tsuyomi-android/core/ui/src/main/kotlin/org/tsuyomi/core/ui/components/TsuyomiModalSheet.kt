/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
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
    val isExpanded: Boolean
        get() = delegate.currentValue == SheetValue.Expanded

    val targetExpanded: Boolean
        get() = delegate.targetValue == SheetValue.Expanded

    internal var programmaticPartial = false
    internal var swipeDismissInFlight = false

    suspend fun expand() = delegate.expand()

    /** Falls back to hiding when layout supplies no partial-expanded anchor. */
    suspend fun partialExpand() {
        programmaticPartial = true
        try {
            if (delegate.hasPartiallyExpandedState) delegate.partialExpand() else delegate.hide()
        } finally {
            programmaticPartial = false
        }
    }

    suspend fun hide() = delegate.hide()
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
    val colorScheme = MaterialTheme.colorScheme
    val resolvedController = controller ?: rememberTsuyomiModalSheetController()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = resolvedController.delegate,
        modifier = modifier,
        containerColor = colorScheme.surfaceContainerLow,
        contentColor = colorScheme.onSurface,
        content = content,
    )
}
