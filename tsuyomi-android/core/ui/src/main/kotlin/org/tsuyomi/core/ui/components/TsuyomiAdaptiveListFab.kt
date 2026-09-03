/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import org.tsuyomi.core.ui.icons.TsuyomiIcons

/**
 * The initial target follows the current boundary. Scrolling toward later items offers the end;
 * scrolling toward earlier items offers the top. Stopping preserves the latest target. The stable
 * icon-only Material 3 FAB keeps its action available to accessibility without resizing on idle.
 */
@Composable
fun TsuyomiAdaptiveListFab(
    state: LazyListState,
    topLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val show by remember(state) {
        derivedStateOf {
            state.layoutInfo.totalItemsCount > 0 && (state.canScrollBackward || state.canScrollForward)
        }
    }
    var movingToEnd by remember(state) { mutableStateOf(true) }
    val tracker = remember(state) { AdaptiveListFabDirectionTracker(initialMovingToEnd = true) }
    LaunchedEffect(state) {
        snapshotFlow {
            AdaptiveListScrollSample(
                index = state.firstVisibleItemIndex,
                offset = state.firstVisibleItemScrollOffset,
                scrolling = state.isScrollInProgress,
                canScrollForward = state.canScrollForward,
            )
        }.collect { sample ->
            tracker.update(sample)?.let { movingToEnd = it }
            if (!sample.scrolling && !tracker.hasObservedDirection) movingToEnd = sample.canScrollForward
        }
    }
    AdaptiveListFab(
        show = show,
        movingToEnd = movingToEnd,
        topLabel = topLabel,
        endLabel = endLabel,
        onClick = {
            scope.launch {
                val target = if (movingToEnd) state.layoutInfo.totalItemsCount - 1 else 0
                state.animateScrollToItem(target.coerceAtLeast(0))
            }
        },
        modifier = modifier,
    )
}

/** Grid counterpart to [TsuyomiAdaptiveListFab]. */
@Composable
fun TsuyomiAdaptiveListFab(
    state: LazyGridState,
    topLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val show by remember(state) {
        derivedStateOf {
            state.layoutInfo.totalItemsCount > 0 && (state.canScrollBackward || state.canScrollForward)
        }
    }
    var movingToEnd by remember(state) { mutableStateOf(true) }
    val tracker = remember(state) { AdaptiveListFabDirectionTracker(initialMovingToEnd = true) }
    LaunchedEffect(state) {
        snapshotFlow {
            AdaptiveListScrollSample(
                index = state.firstVisibleItemIndex,
                offset = state.firstVisibleItemScrollOffset,
                scrolling = state.isScrollInProgress,
                canScrollForward = state.canScrollForward,
            )
        }.collect { sample ->
            tracker.update(sample)?.let { movingToEnd = it }
            if (!sample.scrolling && !tracker.hasObservedDirection) movingToEnd = sample.canScrollForward
        }
    }
    AdaptiveListFab(
        show = show,
        movingToEnd = movingToEnd,
        topLabel = topLabel,
        endLabel = endLabel,
        onClick = {
            scope.launch {
                val target = if (movingToEnd) state.layoutInfo.totalItemsCount - 1 else 0
                state.animateScrollToItem(target.coerceAtLeast(0))
            }
        },
        modifier = modifier,
    )
}

internal data class AdaptiveListScrollSample(
    val index: Int,
    val offset: Int,
    val scrolling: Boolean,
    val canScrollForward: Boolean,
)

/**
 * Keeps the offered jump aligned with the latest sustained scroll direction. Two consecutive
 * movement samples suppress touch jitter; stopping never resets the chosen target.
 */
internal class AdaptiveListFabDirectionTracker(initialMovingToEnd: Boolean) {
    private var previous: AdaptiveListScrollSample? = null
    private var candidateDirection = 0
    private var candidateSamples = 0
    private var movingToEnd = initialMovingToEnd

    var hasObservedDirection: Boolean = false
        private set
    fun update(sample: AdaptiveListScrollSample): Boolean? {
        val prior = previous
        previous = sample
        if (!sample.scrolling || prior == null) {
            candidateDirection = 0
            candidateSamples = 0
            return null
        }

        val direction = when {
            sample.index > prior.index -> 1
            sample.index < prior.index -> -1
            sample.offset > prior.offset -> 1
            sample.offset < prior.offset -> -1
            else -> 0
        }
        if (direction == 0) return null

        if (direction == candidateDirection) {
            candidateSamples += 1
        } else {
            candidateDirection = direction
            candidateSamples = 1
        }
        if (candidateSamples < 2) return null

        val nextMovingToEnd = direction > 0
        hasObservedDirection = true
        movingToEnd = nextMovingToEnd
        return movingToEnd
    }
}

@Composable
private fun AdaptiveListFab(
    show: Boolean,
    movingToEnd: Boolean,
    topLabel: String,
    endLabel: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    if (!show) return
    val label = if (movingToEnd) endLabel else topLabel
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.testTag("adaptive-list-fab"),
    ) {
        Icon(
            imageVector = if (movingToEnd) TsuyomiIcons.ToBottom else TsuyomiIcons.ToTop,
            contentDescription = label,
        )
    }
}
