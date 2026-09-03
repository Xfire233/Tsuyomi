/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import org.tsuyomi.core.ui.layout.TsuyomiNavigationLayout
import org.tsuyomi.core.ui.layout.TsuyomiWindowSize
import org.tsuyomi.core.ui.layout.resolveNavigationLayout

/**
 * Tracks how many modal surfaces are currently open so the scaffold can make the background inert
 * (pointer and accessibility) for the duration of any modal, standard or E-ink.
 */
class TsuyomiModalController {
    var openModalCount by mutableIntStateOf(0)
        private set

    val modalActive: Boolean
        get() = openModalCount > 0

    fun push() {
        openModalCount += 1
    }

    fun pop() {
        openModalCount = (openModalCount - 1).coerceAtLeast(0)
    }
}

val LocalTsuyomiModalController = compositionLocalOf { TsuyomiModalController() }

/**
 * While any modal is open, hides this subtree from the accessibility tree and consumes every
 * pointer event so the background is inert. Applied by [AppScaffold] to the whole window content.
 */
@Composable
fun Modifier.tsuyomiModalBackground(): Modifier {
    val active = LocalTsuyomiModalController.current.modalActive
    return if (active) {
        semantics { hideFromAccessibility() }.pointerInput(active) {
            while (true) {
                awaitPointerEventScope {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                }
            }
        }
    } else {
        this
    }
}

/**
 * Fixed-chrome application scaffold: a non-collapsing top bar, adaptive navigation chrome, and a
 * single content area (the app's only NavHost).
 *
 * The three slots are always composed in the same order and measured by a single layout pass, so
 * crossing a window breakpoint at runtime moves chrome without recreating the content subtree:
 * the current route, per-destination back stacks, scroll positions, and focus survive.
 *
 * The navigation slot receives the resolved [TsuyomiNavigationLayout] so it can render the
 * matching bar or rail variant.
 */
@Composable
fun AppScaffold(
    windowSize: TsuyomiWindowSize,
    topBar: @Composable () -> Unit,
    navigation: @Composable (TsuyomiNavigationLayout) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layout = resolveNavigationLayout(windowSize)
    val modalController = remember { TsuyomiModalController() }
    CompositionLocalProvider(
        LocalTsuyomiModalController provides modalController,
    ) {
        Layout(
            modifier = modifier.tsuyomiModalBackground(),
            content = {
                Box { topBar() }
                Box { navigation(layout) }
                Box { content() }
            },
        ) { measurables, constraints ->
            val topBarPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
            val contentTop = topBarPlaceable.height
            val remainingHeight = (constraints.maxHeight - contentTop).coerceAtLeast(0)

            when (layout) {
                TsuyomiNavigationLayout.BOTTOM_BAR,
                TsuyomiNavigationLayout.COMPACT_BOTTOM_BAR,
                -> {
                    val navPlaceable = measurables[1].measure(
                        constraints.copy(minWidth = 0, minHeight = 0, maxHeight = remainingHeight),
                    )
                    val contentPlaceable = measurables[2].measure(
                        Constraints.fixed(
                            width = constraints.maxWidth,
                            height = (remainingHeight - navPlaceable.height).coerceAtLeast(0),
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        topBarPlaceable.place(0, 0)
                        contentPlaceable.place(0, contentTop)
                        navPlaceable.place(0, constraints.maxHeight - navPlaceable.height)
                    }
                }

                TsuyomiNavigationLayout.RAIL -> {
                    val navPlaceable = measurables[1].measure(
                        constraints.copy(minWidth = 0, minHeight = 0, maxHeight = remainingHeight),
                    )
                    val contentPlaceable = measurables[2].measure(
                        Constraints.fixed(
                            width = (constraints.maxWidth - navPlaceable.width).coerceAtLeast(0),
                            height = remainingHeight,
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        topBarPlaceable.place(0, 0)
                        navPlaceable.place(0, contentTop)
                        contentPlaceable.place(navPlaceable.width, contentTop)
                    }
                }
            }
        }
    }
}
