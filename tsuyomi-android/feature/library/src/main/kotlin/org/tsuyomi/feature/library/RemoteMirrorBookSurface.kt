/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.tsuyomi.core.database.LibraryBook
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.shared.sourcecontract.SourceBookSummary

/**
 * Website-mirror adapter over the canonical Library grid/list/compact renderer and drag grammar.
 * Remote mutations remain delegated to the route owner; this surface only emits exact typed intents.
 */
@Composable
fun RemoteMirrorBookSurface(
    sourceId: String,
    sourceName: String,
    books: List<SourceBookSummary>,
    targets: List<RemoteTarget>,
    selectedTargetId: String?,
    groupingEnabled: Boolean,
    selectedBookIds: Set<BookIdentity>,
    unresolvedBookIds: Set<String>,
    layout: LibraryLayout,
    onOpenTarget: (RemoteTarget) -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    onLongPressBook: (SourceBookSummary) -> Unit,
    onToggleBookSelection: (SourceBookSummary) -> Unit,
    onCopyToLocal: (SourceBookSummary) -> Unit,
    onMoveToTarget: (SourceBookSummary, RemoteTarget) -> Unit,
    onRemoveFromWebsite: (SourceBookSummary) -> Unit,
    coverState: @Composable (SourceBookSummary) -> CoverUiState,
    modifier: Modifier = Modifier,
) {
    val summariesByIdentity = remember(books) { books.associateBy { it.identity } }
    val entries = remember(books, unresolvedBookIds) {
        books.map { summary ->
            LibraryEntry(
                book = LibraryBook(
                    identity = summary.identity,
                    title = summary.title,
                    addedAt = Instant.EPOCH,
                    metadataUpdatedAt = Instant.EPOCH,
                    author = summary.author,
                    coverUrl = summary.coverUrl,
                    canonicalUrl = summary.canonicalUrl,
                ),
                libraryAddedAt = Instant.EPOCH,
                rating = null,
                localTags = emptySet(),
                sourceAvailable = true,
                reconciliation = if (summary.identity.remoteBookId in unresolvedBookIds) {
                    RemoteReconciliationState.UNRESOLVED
                } else {
                    null
                },
            )
        }
    }
    val coordinator = remember { LibraryDragCoordinator() }
    coordinator.onLongPress = { identity -> summariesByIdentity[identity]?.let(onLongPressBook) }
    coordinator.onDrop = { payload, destination ->
        val dragged = (payload as? LibraryDragPayload.Books)
            ?.identities
            ?.singleOrNull()
            ?.let(summariesByIdentity::get)
        if (dragged != null) {
            when (destination) {
                is LibraryDropDestination.RemoteMirror -> targets
                    .firstOrNull { it.targetId == destination.targetId }
                    ?.let { onMoveToTarget(dragged, it) }
                LibraryDropDestination.LocalCopy -> onCopyToLocal(dragged)
                LibraryDropDestination.RemoteRemove,
                LibraryDropDestination.Remove,
                -> onRemoveFromWebsite(dragged)
                is LibraryDropDestination.Book,
                is LibraryDropDestination.Collection,
                is LibraryDropDestination.Library,
                is LibraryDropDestination.Root,
                -> Unit
            }
        }
    }
    val state = LibraryUiState(
        entries = entries,
        loading = false,
        layout = layout,
        selectionKind = LibrarySelectionKind.BOOK.takeIf { selectedBookIds.isNotEmpty() },
        selectedBookIds = selectedBookIds,
    )
    Box(modifier.fillMaxSize().libraryDragOverlayHost(coordinator)) {
        LibraryBookSurface(
            entries = entries,
            state = state,
            onOpenBook = { entry -> summariesByIdentity[entry.book.identity]?.let(onOpenBook) },
            onLongPressBook = { identity -> summariesByIdentity[identity]?.let(onLongPressBook) },
            onToggleBookSelection = { identity -> summariesByIdentity[identity]?.let(onToggleBookSelection) },
            dragCoordinator = coordinator,
            dragEnabled = true,
            reorderEnabled = false,
            coverState = { entry ->
                val summary = summariesByIdentity[entry.book.identity]
                if (summary != null) coverState(summary)
                else CoverUiState.Fallback(FallbackSpec(entry.book.title, entry.book.identity.sourceId))
            },
            onCoverVisibility = { _, _ -> },
            header = {
                RemoteMirrorDestinationHeader(
                    sourceId = sourceId,
                    sourceName = sourceName,
                    targets = targets,
                    selectedTargetId = selectedTargetId,
                    groupingEnabled = groupingEnabled,
                    coordinator = coordinator,
                    onOpenTarget = onOpenTarget,
                )
            },
            empty = { Text("网站书架暂无书籍", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxSize(),
        )
        LibraryDragVisualOverlay(
            coordinator = coordinator,
            entries = entries,
            shortcuts = emptyList(),
            layout = layout,
            coverState = { entry ->
                val summary = summariesByIdentity[entry.book.identity]
                if (summary != null) coverState(summary)
                else CoverUiState.Fallback(FallbackSpec(entry.book.title, entry.book.identity.sourceId))
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RemoteMirrorDestinationHeader(
    sourceName: String,
    targets: List<RemoteTarget>,
    sourceId: String,
    selectedTargetId: String?,
    groupingEnabled: Boolean,
    coordinator: LibraryDragCoordinator,
    onOpenTarget: (RemoteTarget) -> Unit,
) {
    val draggingBooks = coordinator.activeBookIds.isNotEmpty()
    if (!groupingEnabled && !draggingBooks) return
    Column(
        Modifier.fillMaxWidth().libraryShelfDropTarget(coordinator).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (coordinator.activeBookIds.size > 1) {
            Text(
                "网站操作仅支持单本",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (draggingBooks) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteDropAction(
                    label = "复制到本地",
                    icon = TsuyomiIcons.Shelf,
                    kind = LibraryShortcutDropKind.LOCAL_COPY,
                    id = "remote-local-copy",
                    index = 0,
                    coordinator = coordinator,
                    modifier = Modifier.weight(1f),
                )
                RemoteDropAction(
                    label = "从网站移除",
                    icon = TsuyomiIcons.Delete,
                    kind = LibraryShortcutDropKind.REMOTE_REMOVE,
                    id = "remote-remove",
                    index = 1,
                    coordinator = coordinator,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val visibleTargets = if (!groupingEnabled || selectedTargetId != null && !draggingBooks) {
            emptyList()
        } else if (selectedTargetId == null) {
            targets
        } else {
            targets.filterNot { it.targetId == selectedTargetId }
        }
        if (visibleTargets.isNotEmpty()) {
            Text(
                if (selectedTargetId == null) "$sourceName 分类" else "移至其他网站分类",
                style = MaterialTheme.typography.titleSmall,
            )
            visibleTargets.forEachIndexed { index, target ->
                val mirror = LibraryMirrorShortcut(
                    sourceId = sourceId,
                    targetId = target.targetId,
                    label = target.displayName,
                    count = 0,
                    frozen = false,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .libraryShortcutDropTarget(
                            coordinator = coordinator,
                            id = "remote-folder:${target.targetId}",
                            index = index + 2,
                            kind = LibraryShortcutDropKind.REMOTE_FOLDER,
                            bookIdentity = null,
                            mirror = mirror,
                        )
                        .testTag("remote-library-folder-${target.targetId}"),
                    onClick = { onOpenTarget(target) },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(TsuyomiIcons.Folder, contentDescription = null)
                        Text(target.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(TsuyomiIcons.Next, contentDescription = "打开${target.displayName}")
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteDropAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    kind: LibraryShortcutDropKind,
    id: String,
    index: Int,
    coordinator: LibraryDragCoordinator,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.libraryShortcutDropTarget(coordinator, id, index, kind, null, null),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
