/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.TsuyomiCoverCardContent
import org.tsuyomi.core.ui.components.TsuyomiVisibility
import org.tsuyomi.core.ui.components.coverCardHeight
import org.tsuyomi.core.ui.components.currentCoverCardLayout
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.shared.librarydomain.LibraryEntry

@Composable
internal fun LibraryDragVisualOverlay(
    coordinator: LibraryDragCoordinator,
    entries: List<LibraryEntry>,
    layout: LibraryLayout,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    modifier: Modifier = Modifier,
    showRemoveTarget: Boolean = true,
) {
    val payload = coordinator.activePayload
    TsuyomiVisibility(
        visible = payload != null,
        modifier = modifier.testTag("library-drag-overlay").semantics { hideFromAccessibility() },
    ) {
        Box(Modifier.fillMaxSize()) {
            payload?.let { active ->
                val previewSize = dragPreviewSize(active, layout)

                val density = LocalDensity.current
                val host = coordinator.hostTopLeft()
                val previewWidthPx = with(density) { previewSize.width.toPx() }
                val previewHeightPx = with(density) { previewSize.height.toPx() }
                val pointer = coordinator.ghostPositionInWindow
                val x = pointer.x - host.x - previewWidthPx / 2f
                val y = pointer.y - host.y - previewHeightPx * 0.34f
                Box(
                    modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .testTag("library-drag-preview")
                        .graphicsLayer { alpha = 0.94f },
                ) {
                    when (active) {
                        is LibraryDragPayload.Books -> {
                            val activeEntries = entries.filter { it.book.identity in active.identities }
                            LibraryBookDragPreview(activeEntries, layout, coverState)
                        }
                        is LibraryDragPayload.Shortcut -> RootNodeDragPreview(active.id)
                    }
                }
                if (showRemoveTarget && active is LibraryDragPayload.Books) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .heightIn(min = 48.dp)
                            .libraryDeleteDropTarget(coordinator)
                            .testTag("library-delete-drop-target"),
                        color = if (coordinator.isOverDelete) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = if (coordinator.isOverDelete) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.error)
                        } else null,
                        shape = MaterialTheme.shapes.extraLarge,
                        tonalElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(TsuyomiIcons.Delete, contentDescription = null)
                            Text(if (coordinator.isOverDelete) "松开以移出书架" else "拖到这里移出书架")
                        }
                    }
                }
            }
        }
    }
}

private val GridDragPreviewWidth = 200.dp

@Composable
internal fun dragPreviewSize(
    payload: LibraryDragPayload,
    layout: LibraryLayout,
): DpSize = when (payload) {
    is LibraryDragPayload.Shortcut -> DpSize(216.dp, 64.dp)
    is LibraryDragPayload.Books -> when (layout) {
        LibraryLayout.GRID -> DpSize(
            GridDragPreviewWidth,
            coverCardHeight(GridDragPreviewWidth),
        )
        LibraryLayout.LIST -> DpSize(328.dp, 144.dp)
        LibraryLayout.COMPACT -> DpSize(320.dp, 64.dp)
    }
}


@Composable
internal fun LibraryBookDragPreview(
    entries: List<LibraryEntry>,
    layout: LibraryLayout,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
) {
    val lead = entries.firstOrNull() ?: return
    when (layout) {
        LibraryLayout.GRID -> Surface(
            modifier = Modifier
                .width(GridDragPreviewWidth)
                .testTag("library-drag-preview-grid-content"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
        ) {
            TsuyomiCoverCardContent(
                title = lead.book.title,
                supportingText = dragPreviewStatus(lead),
                cover = { CoverImage(coverState(lead), modifier = Modifier.fillMaxSize()) },
                artworkOverlay = {
                    DragBatchBadge(entries.size, Modifier.align(Alignment.TopEnd).padding(6.dp))
                },
            )
        }

        LibraryLayout.LIST -> Surface(
            modifier = Modifier.size(width = 328.dp, height = 144.dp).testTag("library-drag-preview-list-content"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
        ) {
            ListItem(
                headlineContent = { Text(lead.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                overlineContent = lead.book.authors.joinToString("、").takeIf(String::isNotBlank)?.let { authors ->
                    { Text(authors, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                },
                supportingContent = { Text(dragPreviewStatus(lead), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    CoverImage(
                        coverState(lead),
                        modifier = Modifier.height(112.dp)
                            .aspectRatio(currentCoverCardLayout().leadingArtworkAspectRatio)
                            .testTag("library-drag-preview-list-cover"),
                    )
                },
                trailingContent = if (entries.size > 1) {
                    { DragBatchBadge(entries.size) }
                } else null,
                modifier = Modifier.fillMaxSize(),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        LibraryLayout.COMPACT -> Surface(
            modifier = Modifier.size(width = 320.dp, height = 64.dp).testTag("library-drag-preview-compact-content"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
        ) {
            ListItem(
                headlineContent = { Text(lead.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    val supporting = compactDragPreviewSupporting(lead)
                    if (supporting.isNotBlank()) Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailingContent = when {
                    entries.size > 1 -> ({ DragBatchBadge(entries.size) })
                    lead.rating != null -> ({ Text("★ ${lead.rating}") })
                    else -> null
                },
                modifier = Modifier.fillMaxSize(),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}


@Composable
internal fun RootNodeDragPreview(id: String) {
    val collection = id.startsWith("collection:")
    Surface(
        modifier = Modifier.size(width = 216.dp, height = 64.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 52.dp, height = 52.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (collection) TsuyomiIcons.Folder else TsuyomiIcons.Mirror,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                if (collection) "收藏夹" else "网站收藏",
                modifier = Modifier.padding(horizontal = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun dragPreviewStatus(entry: LibraryEntry): String = entry.libraryStatusLabel()

private fun compactDragPreviewSupporting(entry: LibraryEntry): String =
    entry.readingStatusLabel() ?: entry.book.authors.joinToString("、")

@Composable
internal fun DragBatchBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary) {
        Text(
            count.toString(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
