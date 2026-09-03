/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.instantMotion

@Composable
internal fun LibraryDragVisualOverlay(
    coordinator: LibraryDragCoordinator,
    entries: List<LibraryEntry>,
    shortcuts: List<ProductionShortcut>,
    layout: LibraryLayout,
    coverState: (LibraryEntry) -> CoverUiState,
    modifier: Modifier = Modifier,
) {
    val payload = coordinator.activePayload
    val instant = LocalDisplayEnvironment.current.instantMotion
    AnimatedVisibility(
        visible = payload != null,
        modifier = modifier.testTag("library-drag-overlay").semantics { hideFromAccessibility() },
        enter = fadeIn(if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS)) +
            scaleIn(if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS), initialScale = 0.96f),
        exit = fadeOut(if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS)) +
            scaleOut(if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS), targetScale = 0.96f),
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
                            if (active.fromShortcut && activeEntries.size == 1) {
                                ShortcutBookDragPreview(activeEntries.first(), coverState)
                            } else {
                                LibraryBookDragPreview(activeEntries, layout, coverState)
                            }
                        }
                        is LibraryDragPayload.Shortcut -> {
                            shortcuts.firstOrNull { it.id == active.id }?.let {
                                ShortcutDragPreview(it, coverState)
                            }
                        }
                    }
                }
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
                        val shortcutRemoval = active is LibraryDragPayload.Shortcut ||
                            (active is LibraryDragPayload.Books && active.fromShortcut)
                        Text(
                            when {
                                coordinator.isOverDelete && shortcutRemoval -> "松开以移出快捷书架"
                                coordinator.isOverDelete -> "松开以移出书架"
                                shortcutRemoval -> "拖到这里移出快捷书架"
                                else -> "拖到这里移出书架"
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun dragPreviewSize(payload: LibraryDragPayload, layout: LibraryLayout): DpSize = when (payload) {
    is LibraryDragPayload.Shortcut -> DpSize(84.dp, 116.dp)
    is LibraryDragPayload.Books -> if (payload.fromShortcut) {
        DpSize(84.dp, 116.dp)
    } else when (layout) {
        LibraryLayout.GRID -> DpSize(132.dp, 180.dp)
        LibraryLayout.LIST -> DpSize(292.dp, 104.dp)
        LibraryLayout.COMPACT -> DpSize(272.dp, 64.dp)
    }
}

@Composable
internal fun LibraryBookDragPreview(
    entries: List<LibraryEntry>,
    layout: LibraryLayout,
    coverState: (LibraryEntry) -> CoverUiState,
) {
    val lead = entries.firstOrNull() ?: return
    when (layout) {
        LibraryLayout.GRID -> Surface(
            modifier = Modifier.size(width = 132.dp, height = 180.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
        ) {
            Box {
                CoverImage(coverState(lead), modifier = Modifier.fillMaxSize())
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))))
                        .padding(start = 8.dp, top = 30.dp, end = 8.dp, bottom = 8.dp),
                ) {
                    Text(lead.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
                }
                DragBatchBadge(entries.size, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        LibraryLayout.LIST -> Surface(
            modifier = Modifier.size(width = 292.dp, height = 104.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
        ) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(coverState(lead), modifier = Modifier.size(width = 68.dp, height = 92.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(lead.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(lead.book.authors.joinToString("、"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DragBatchBadge(entries.size, Modifier.padding(end = 6.dp))
            }
        }
        LibraryLayout.COMPACT -> Surface(
            modifier = Modifier.size(width = 272.dp, height = 64.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
        ) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(lead.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                DragBatchBadge(entries.size)
            }
        }
    }
}

@Composable
internal fun ShortcutBookDragPreview(entry: LibraryEntry, coverState: (LibraryEntry) -> CoverUiState) {
    Surface(
        modifier = Modifier.size(width = 84.dp, height = 116.dp),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(4.dp)) {
            CoverImage(coverState(entry), modifier = Modifier.fillMaxWidth().height(76.dp))
            Text(entry.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun ShortcutDragPreview(shortcut: ProductionShortcut, coverState: (LibraryEntry) -> CoverUiState) {
    Surface(
        modifier = Modifier.size(width = 84.dp, height = 116.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(4.dp)) {
            Box(
                Modifier.fillMaxWidth().height(76.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                shortcut.entry?.let { CoverImage(coverState(it), modifier = Modifier.fillMaxSize()) }
                    ?: Icon(shortcut.icon, contentDescription = null)
            }
            Text(shortcut.label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
}

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
