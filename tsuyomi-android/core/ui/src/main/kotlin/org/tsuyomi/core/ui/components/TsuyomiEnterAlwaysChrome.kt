/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.tsuyomi.core.ui.components

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import kotlin.math.roundToInt

/** Remembers Material's enter-always scroll behavior for a coordinated, collapsible header. */
@Composable
fun rememberTsuyomiEnterAlwaysChromeState(
    canScroll: () -> Boolean,
): TsuyomiEnterAlwaysChromeState {
    val currentCanScroll = rememberUpdatedState(canScroll)
    val canScrollGate = remember {
        { currentCanScroll.value() }
    }
    val materialState = rememberTopAppBarState()
    val staticMotion = LocalDisplayEnvironment.current.instantMotion || rememberSystemReducedMotion()
    val scrollBehavior = if (staticMotion) {
        TopAppBarDefaults.enterAlwaysScrollBehavior(
            state = materialState,
            canScroll = canScrollGate,
            snapAnimationSpec = snap(),
            flingAnimationSpec = null,
        )
    } else {
        TopAppBarDefaults.enterAlwaysScrollBehavior(
            state = materialState,
            canScroll = canScrollGate,
        )
    }
    val chromeState = remember(materialState) {
        TsuyomiEnterAlwaysChromeState(materialState, canScrollGate)
    }

    SideEffect {
        chromeState.updateScrollBehavior(scrollBehavior)
    }
    LaunchedEffect(chromeState) {
        snapshotFlow { canScrollGate() }.collect { enabled ->
            if (!enabled) chromeState.cancelNativeFling()
        }
    }

    return chromeState
}

/**
 * Shared visibility state for linked header rows. The native Material state owns scroll physics;
 * this wrapper only guards callbacks, coordinates explicit reveals, and keeps the measured height
 * limit valid while its descendants are temporarily removed.
 */
@Stable
class TsuyomiEnterAlwaysChromeState internal constructor(
    private val materialState: TopAppBarState,
    private val canScroll: () -> Boolean,
) {
    private var expandedHeightPx = 0
    private var scrollBehavior: TopAppBarScrollBehavior? = null
    private var activeNativeFling: Deferred<Velocity>? = null
    private var verticalScrollObserved = false

    /** Nested-scroll connection to attach above the vertically scrolling directory. */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!canHandleScroll()) return Offset.Zero
            if (available.y == 0f) {
                if (available.x != 0f) verticalScrollObserved = false
                return Offset.Zero
            }
            verticalScrollObserved = true
            val connection = scrollBehavior?.nestedScrollConnection ?: return Offset.Zero
            val consumed = connection.onPreScroll(Offset(0f, available.y), source)
            return Offset(0f, consumed.y)
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (!canHandleScroll()) return Offset.Zero
            if (consumed.y == 0f && available.y == 0f) {
                if (consumed.x != 0f || available.x != 0f) verticalScrollObserved = false
                return Offset.Zero
            }
            verticalScrollObserved = true
            val connection = scrollBehavior?.nestedScrollConnection ?: return Offset.Zero
            val result = connection.onPostScroll(
                consumed = Offset(0f, consumed.y),
                available = Offset(0f, available.y),
                source = source,
            )
            return Offset(0f, result.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!canHandleScroll()) return Velocity.Zero
            if (available.y == 0f) return Velocity.Zero
            verticalScrollObserved = true
            val connection = scrollBehavior?.nestedScrollConnection ?: return Velocity.Zero
            val consumed = connection.onPreFling(Velocity(0f, available.y))
            return Velocity(0f, consumed.y)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (!canHandleScroll()) {
                verticalScrollObserved = false
                return Velocity.Zero
            }
            if (consumed.y == 0f && available.y == 0f && !verticalScrollObserved) return Velocity.Zero
            verticalScrollObserved = false
            val connection = scrollBehavior?.nestedScrollConnection ?: return Velocity.Zero

            return coroutineScope {
                val nativeFling = async {
                    connection.onPostFling(
                        consumed = Velocity(0f, consumed.y),
                        available = Velocity(0f, available.y),
                    )
                }
                activeNativeFling = nativeFling
                try {
                    val result = nativeFling.await()
                    if (canHandleScroll()) Velocity(0f, result.y) else Velocity.Zero
                } catch (cancelled: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    Velocity.Zero
                } finally {
                    if (activeNativeFling === nativeFling) activeNativeFling = null
                }
            }
        }
    }

    /** Current Material enter-always collapse fraction: zero is expanded and one is collapsed. */
    val collapsedFraction: Float
        get() = materialState.collapsedFraction.coerceIn(0f, 1f)

    val isFullyExpanded: Boolean
        get() = collapsedFraction == 0f

    val isFullyCollapsed: Boolean
        get() = collapsedFraction == 1f

    /** Immediately reveals the header and cancels a native settling fling already in progress. */
    fun reveal() {
        cancelNativeFling()
        verticalScrollObserved = false
        materialState.heightOffset = 0f
    }

    /**
     * Updates the shared Material height limit from the natural, expanded header measurement.
     * Zero-size measurements are ignored so removing collapsed descendants cannot erase the limit.
     */
    fun updateExpandedHeight(height: Int) {
        if (height <= 0 || height == expandedHeightPx) return

        val fraction = if (expandedHeightPx > 0) collapsedFraction else 0f
        expandedHeightPx = height
        materialState.heightOffsetLimit = -height.toFloat()
        materialState.heightOffset = -height.toFloat() * fraction
    }

    internal fun updateScrollBehavior(behavior: TopAppBarScrollBehavior) {
        if (scrollBehavior !== behavior) cancelNativeFling()
        scrollBehavior = behavior
    }

    internal fun cancelNativeFling() {
        activeNativeFling?.cancel()
        activeNativeFling = null
    }

    private fun canHandleScroll(): Boolean {
        if (canScroll()) return true
        cancelNativeFling()
        verticalScrollObserved = false
        return false
    }
}

/**
 * Measures its content at natural expanded height, then clips and allocates only the portion
 * remaining at the shared collapse fraction. Fully collapsed content leaves composition entirely;
 * its last natural measurement remains available to the parent-owned height aggregation.
 */
@Composable
fun TsuyomiCollapsibleChrome(
    state: TsuyomiEnterAlwaysChromeState,
    modifier: Modifier = Modifier,
    onExpandedHeightChanged: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val fullyCollapsed by remember(state) {
        derivedStateOf { state.isFullyCollapsed }
    }
    val currentOnExpandedHeightChanged = rememberUpdatedState(onExpandedHeightChanged)

    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            if (!fullyCollapsed) {
                Box(
                    modifier = Modifier.onSizeChanged { size ->
                        if (size.height > 0) currentOnExpandedHeightChanged.value(size.height)
                    },
                    content = { content() },
                )
            }
        },
    ) { measurables, constraints ->
        val child = measurables.singleOrNull()?.measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )
        val naturalHeight = child?.height ?: 0
        val fraction = state.collapsedFraction
        val visibleHeight = if (child == null || fraction >= 1f) {
            0
        } else {
            (naturalHeight * (1f - fraction)).roundToInt()
        }
        val measuredWidth = child?.width ?: if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            constraints.minWidth
        }
        val width = constraints.constrainWidth(measuredWidth)
        val height = constraints.constrainHeight(visibleHeight)

        layout(width, height) {
            child?.placeRelative(0, 0)
        }
    }
}
