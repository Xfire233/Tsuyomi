/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.display.displayGrayscaleLayer

/** Indexed anchored menu: Standard keeps Material behavior; E-ink exposes complete items by page. */
@Composable
fun TsuyomiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    itemCount: Int,
    itemKey: (index: Int) -> String,
    itemContent: @Composable ColumnScope.(index: Int) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    pageControls: (@Composable (
        page: Int,
        canGoPrevious: Boolean,
        canGoNext: Boolean,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
        pageCount: Int?,
        onFirst: () -> Unit,
        onLast: () -> Unit,
    ) -> Unit)? = null,
) {
    require(itemCount >= 0) { "itemCount must not be negative" }
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    if (!eInk) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier.displayGrayscaleLayer(),
            offset = offset,
            shape = MenuDefaults.shape,
            containerColor = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
        ) {
            for (index in 0 until itemCount) {
                key(itemKey(index)) { itemContent(index) }
            }
        }
        return
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowSize = LocalWindowInfo.current.containerSize
    val safeInsets = WindowInsets.safeDrawing.union(WindowInsets.ime)
    val safeLeft = safeInsets.getLeft(density, layoutDirection)
    val safeTop = safeInsets.getTop(density)
    val safeRight = safeInsets.getRight(density, layoutDirection)
    val safeBottom = safeInsets.getBottom(density)
    val maxWidth = with(density) {
        (windowSize.width - safeLeft - safeRight).coerceAtLeast(1).toDp()
    }
    val maxHeight = with(density) {
        (windowSize.height - safeTop - safeBottom).coerceAtLeast(1).toDp()
    }
    val popupPositionProvider = remember(
        offset,
        safeLeft,
        safeTop,
        safeRight,
        safeBottom,
        layoutDirection,
        density,
    ) {
        TsuyomiAnchoredMenuPositionProvider(
            offset = with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) },
            safeInsets = IntRect(safeLeft, safeTop, safeRight, safeBottom),
            layoutDirection = layoutDirection,
        )
    }
    val state = rememberTsuyomiMeasuredPagerState(
        initialAnchorKey = if (itemCount > 0) itemKey(0) else null,
    )
    if (expanded) {
        Popup(
            popupPositionProvider = popupPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = true,
            ),
        ) {
            Surface(
                modifier = modifier
                    .widthIn(min = 176.dp.coerceAtMost(maxWidth), max = 320.dp.coerceAtMost(maxWidth))
                    .heightIn(max = maxHeight)
                    .displayGrayscaleLayer(),
                shape = RoundedCornerShape(4.dp),
                color = org.tsuyomi.core.ui.theme.TsuyomiEInkPalette.Paper,
                contentColor = org.tsuyomi.core.ui.theme.TsuyomiEInkPalette.Ink,
                border = BorderStroke(1.5.dp, org.tsuyomi.core.ui.theme.TsuyomiEInkPalette.Ink),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                TsuyomiMeasuredPager(
                    itemCount = itemCount,
                    itemKey = itemKey,
                    itemContent = { index ->
                        Column(Modifier.fillMaxWidth()) { itemContent(index) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    state = state,
                    pageControls = pageControls,
                    fillViewport = false,
                )
            }
        }
    }
}

private class TsuyomiAnchoredMenuPositionProvider(
    private val offset: IntOffset,
    private val safeInsets: IntRect,
    private val layoutDirection: LayoutDirection,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val left = safeInsets.left
        val top = safeInsets.top
        val right = (windowSize.width - safeInsets.right - popupContentSize.width).coerceAtLeast(left)
        val bottom = (windowSize.height - safeInsets.bottom - popupContentSize.height).coerceAtLeast(top)
        val x = if (this.layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left + offset.x
        } else {
            anchorBounds.right - popupContentSize.width + offset.x
        }.coerceIn(left, right)
        val below = anchorBounds.bottom + offset.y
        val above = anchorBounds.top - popupContentSize.height + offset.y
        val y = when {
            below + popupContentSize.height <= windowSize.height - safeInsets.bottom -> below
            above >= top -> above
            else -> top
        }.coerceIn(top, bottom)
        return IntOffset(x, y)
    }
}
