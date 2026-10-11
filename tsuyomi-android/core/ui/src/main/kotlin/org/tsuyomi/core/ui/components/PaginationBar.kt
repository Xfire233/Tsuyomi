/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Shared threshold for explicit endpoint jumps on pagers without draggable progress. */
const val PAGINATION_JUMP_THRESHOLD = 5

/**
 * Explicit pagination control with a persistent status and bounded previous/next actions.
 * Unknown totals show only the current page; callers supply cursor availability through
 * [hasNextPage]. Loading and errors remain inline and persistent.
 */
@Composable
fun PaginationBar(
    page: Int,
    pageCount: Int?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    hasNextPage: Boolean? = null,
    hasPreviousPage: Boolean? = null,
    onFirst: (() -> Unit)? = null,
    onLast: (() -> Unit)? = null,
) {
    val showEndpoints = pageCount != null && pageCount > PAGINATION_JUMP_THRESHOLD && onFirst != null && onLast != null
    val fullPageStatus = if (pageCount == null) stringResource(R.string.coreui_pagination_current_page, page)
        else stringResource(R.string.coreui_pagination_page_status, page, pageCount)
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Layout(
                modifier = Modifier.fillMaxWidth(),
                content = {
                    val padding = PaddingValues(horizontal = TsuyomiSpacing.Xs, vertical = 0.dp)
                    TsuyomiButton(
                        text = stringResource(R.string.coreui_pagination_previous),
                        onClick = onPrevious,
                        style = TsuyomiButtonStyle.SECONDARY,
                        enabled = (hasPreviousPage ?: (page > 1)) && !loading,
                        contentPadding = padding,
                    )
                    Text(
                        text = if (showEndpoints) stringResource(R.string.coreui_pagination_page_fraction, page, pageCount!!) else fullPageStatus,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                            stateDescription = fullPageStatus
                        },
                    )
                    TsuyomiButton(
                        text = stringResource(R.string.coreui_pagination_next),
                        onClick = onNext,
                        style = TsuyomiButtonStyle.SECONDARY,
                        enabled = (hasNextPage ?: (pageCount?.let { page < it } ?: false)) && !loading,
                        contentPadding = padding,
                    )
                    if (showEndpoints) {
                        TsuyomiButton(
                            text = stringResource(R.string.coreui_pagination_first),
                            onClick = onFirst!!,
                            style = TsuyomiButtonStyle.SECONDARY,
                            enabled = page > 1 && !loading,
                            contentPadding = padding,
                        )
                        TsuyomiButton(
                            text = stringResource(R.string.coreui_pagination_last),
                            onClick = onLast!!,
                            style = TsuyomiButtonStyle.SECONDARY,
                            enabled = page < pageCount!! && !loading,
                            contentPadding = padding,
                        )
                    }
                },
            ) { measurables, constraints ->
                val childConstraints = Constraints(maxWidth = constraints.maxWidth)
                val previous = measurables[0].measure(childConstraints)
                val status = measurables[1].measure(childConstraints)
                val next = measurables[2].measure(childConstraints)
                val first = measurables.getOrNull(3)?.measure(childConstraints)
                val last = measurables.getOrNull(4)?.measure(childConstraints)
                val width = constraints.maxWidth
                val horizontalGap = TsuyomiSpacing.Lg.roundToPx()
                val verticalGap = TsuyomiSpacing.Sm.roundToPx()
                val rowWidth = previous.width + status.width + next.width + horizontalGap * 2
                val actionWidth = previous.width + next.width + horizontalGap
                val actionHeight = maxOf(previous.height, next.height)
                val pairedActions = actionWidth <= width
                val singleRow = rowWidth <= width
                val ordinaryHeight = when {
                    singleRow -> maxOf(actionHeight, status.height)
                    pairedActions -> status.height + verticalGap + actionHeight
                    else -> status.height + previous.height + next.height + verticalGap * 2
                }
                val endpointPairWidth = if (first == null || last == null) 0 else first.width + last.width + horizontalGap
                val pairedEndpoints = endpointPairWidth <= width
                val endpointHeight = if (first == null || last == null) 0 else
                    if (pairedEndpoints) maxOf(first.height, last.height) else first.height + verticalGap + last.height
                val contentHeight = ordinaryHeight + if (first == null) 0 else verticalGap + endpointHeight
                val height = maxOf(constraints.minHeight, contentHeight)
                require(height <= constraints.maxHeight) { "Complete pagination controls exceed the available footer height" }
                layout(width, height) {
                    if (singleRow) {
                        val start = (width - rowWidth) / 2
                        previous.placeRelative(start, (ordinaryHeight - previous.height) / 2)
                        status.placeRelative(start + previous.width + horizontalGap, (ordinaryHeight - status.height) / 2)
                        next.placeRelative(start + previous.width + status.width + horizontalGap * 2, (ordinaryHeight - next.height) / 2)
                    } else {
                        status.placeRelative((width - status.width) / 2, 0)
                        val actionTop = status.height + verticalGap
                        if (pairedActions) {
                            val start = (width - actionWidth) / 2
                            previous.placeRelative(start, actionTop + (actionHeight - previous.height) / 2)
                            next.placeRelative(start + previous.width + horizontalGap, actionTop + (actionHeight - next.height) / 2)
                        } else {
                            previous.placeRelative((width - previous.width) / 2, actionTop)
                            next.placeRelative((width - next.width) / 2, actionTop + previous.height + verticalGap)
                        }
                    }
                    if (first != null && last != null) {
                        val endpointTop = ordinaryHeight + verticalGap
                        if (pairedEndpoints) {
                            val start = (width - endpointPairWidth) / 2
                            first.placeRelative(start, endpointTop + (endpointHeight - first.height) / 2)
                            last.placeRelative(start + first.width + horizontalGap, endpointTop + (endpointHeight - last.height) / 2)
                        } else {
                            first.placeRelative((width - first.width) / 2, endpointTop)
                            last.placeRelative((width - last.width) / 2, endpointTop + first.height + verticalGap)
                        }
                    }
                }
            }
            if (loading) InlineStatus(stringResource(R.string.coreui_loading), Modifier.padding(top = TsuyomiSpacing.Sm))
            if (error != null) InfoBanner(
                title = error,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                primaryActionLabel = if (onRetry == null) null else stringResource(R.string.coreui_retry),
                onPrimaryAction = onRetry,
            )
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { collectionInfo = CollectionInfo(1, 3) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TsuyomiButton(
                text = stringResource(R.string.coreui_pagination_previous),
                onClick = onPrevious,
                style = TsuyomiButtonStyle.SECONDARY,
                enabled = (hasPreviousPage ?: (page > 1)) && !loading,
                modifier = Modifier.semantics {
                    collectionItemInfo = CollectionItemInfo(0, 1, 0, 1)
                },
            )
            Text(
                text = if (pageCount == null) {
                    stringResource(R.string.coreui_pagination_current_page, page)
                } else {
                    stringResource(R.string.coreui_pagination_page_status, page, pageCount)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    collectionItemInfo = CollectionItemInfo(0, 1, 1, 1)
                },
            )
            TsuyomiButton(
                text = stringResource(R.string.coreui_pagination_next),
                onClick = onNext,
                style = TsuyomiButtonStyle.SECONDARY,
                enabled = (hasNextPage ?: (pageCount?.let { page < it } ?: false)) && !loading,
                modifier = Modifier.semantics {
                    collectionItemInfo = CollectionItemInfo(0, 1, 2, 1)
                },
            )
        }
        if (pageCount != null && pageCount > PAGINATION_JUMP_THRESHOLD && onFirst != null && onLast != null) {
            PaginationEndpoints(page, pageCount, loading, onFirst, onLast)
        }
        if (loading) {
            InlineStatus(
                text = stringResource(R.string.coreui_loading),
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
            )
        }
        if (error != null) {
            InfoBanner(
                title = error,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                primaryActionLabel = if (onRetry != null) {
                    stringResource(R.string.coreui_retry)
                } else {
                    null
                },
                onPrimaryAction = onRetry,
            )
        }
    }
}

/** Endpoint actions remain complete, centred and separate from the ordinary page/status group. */
@Composable
private fun PaginationEndpoints(
    page: Int,
    pageCount: Int,
    loading: Boolean,
    onFirst: () -> Unit,
    onLast: () -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxWidth().padding(top = TsuyomiSpacing.Sm),
        content = {
            TsuyomiButton(
                text = stringResource(R.string.coreui_pagination_first),
                onClick = onFirst,
                style = TsuyomiButtonStyle.SECONDARY,
                enabled = page > 1 && !loading,
                contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Xs, vertical = 0.dp),
            )
            TsuyomiButton(
                text = stringResource(R.string.coreui_pagination_last),
                onClick = onLast,
                style = TsuyomiButtonStyle.SECONDARY,
                enabled = page < pageCount && !loading,
                contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Xs, vertical = 0.dp),
            )
        },
    ) { measurables, constraints ->
        val childConstraints = Constraints(maxWidth = constraints.maxWidth)
        val first = measurables[0].measure(childConstraints)
        val last = measurables[1].measure(childConstraints)
        val gap = TsuyomiSpacing.Lg.roundToPx()
        val stacked = first.width + gap + last.width > constraints.maxWidth
        val height = if (stacked) first.height + TsuyomiSpacing.Sm.roundToPx() + last.height else maxOf(first.height, last.height)
        require(height <= constraints.maxHeight) { "Complete pagination endpoints exceed the available footer height" }
        layout(constraints.maxWidth, height) {
            if (stacked) {
                first.placeRelative((constraints.maxWidth - first.width) / 2, 0)
                last.placeRelative((constraints.maxWidth - last.width) / 2, first.height + TsuyomiSpacing.Sm.roundToPx())
            } else {
                val start = (constraints.maxWidth - first.width - gap - last.width) / 2
                first.placeRelative(start, (height - first.height) / 2)
                last.placeRelative(start + first.width + gap, (height - last.height) / 2)
            }
        }
    }
}
