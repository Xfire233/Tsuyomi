/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.database.CollectionKind
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.instantMotion

data class LibraryRootNodePlacement(
    val id: String,
    val bookOffset: Int,
)

sealed interface LibraryRootItem {
    val key: String

    data class Book(val entry: LibraryEntry) : LibraryRootItem {
        override val key: String = "book:${entryKey(entry)}"
    }

    data class Collection(
        val collection: LibraryCollection,
        val count: Int,
    ) : LibraryRootItem {
        override val key: String = libraryCollectionRootId(collection.collectionId)
    }

    data class Mirror(val mirror: LibraryMirrorShortcut) : LibraryRootItem {
        override val key: String = libraryMirrorRootId(mirror.sourceId)
    }
}

fun libraryCollectionRootId(collectionId: String): String = "collection:$collectionId"

fun libraryMirrorRootId(sourceId: String): String = "mirror:${sourceId.length}:$sourceId"

fun buildLibraryRootItems(
    entries: List<LibraryEntry>,
    collections: List<LibraryCollection>,
    collectionCounts: Map<String, Int>,
    mirrors: List<LibraryMirrorShortcut>,
    placements: List<LibraryRootNodePlacement>,
    customOrder: Boolean,
): List<LibraryRootItem> {
    val structuralById = linkedMapOf<String, LibraryRootItem>()
    collections.asSequence()
        .filter { it.parentCollectionId == null }
        .sortedWith(compareBy<LibraryCollection> { it.displayOrder }.thenBy { it.collectionId })
        .forEach { collection ->
            structuralById[libraryCollectionRootId(collection.collectionId)] = LibraryRootItem.Collection(
                collection,
                collectionCounts[collection.collectionId] ?: 0,
            )
        }
    mirrors.asSequence()
        .filter { it.targetId == null }
        .sortedWith(compareBy<LibraryMirrorShortcut> { it.label }.thenBy { it.sourceId })
        .forEach { mirror -> structuralById[libraryMirrorRootId(mirror.sourceId)] = LibraryRootItem.Mirror(mirror) }

    val orderedPlacements = buildList {
        val seen = hashSetOf<String>()
        placements.forEach { placement ->
            if (placement.id in structuralById && seen.add(placement.id)) add(placement)
        }
        structuralById.keys.forEach { id -> if (seen.add(id)) add(LibraryRootNodePlacement(id, 0)) }
    }
    val books = entries.distinctBy { it.book.identity }.map { LibraryRootItem.Book(it) }

    val nodesByOffset = orderedPlacements.groupBy { it.bookOffset.coerceIn(0, books.size) }
    return buildList(books.size + orderedPlacements.size) {
        for (bookOffset in 0..books.size) {
            nodesByOffset[bookOffset].orEmpty().forEach { placement -> structuralById[placement.id]?.let(::add) }
            if (bookOffset < books.size) add(books[bookOffset])
        }
    }
}

@Composable
internal fun LibraryRootNodeGridCard(
    item: LibraryRootItem,
    index: Int,
    selected: Boolean,
    selectionActive: Boolean,
    reorderEnabled: Boolean,
    dragCoordinator: LibraryDragCoordinator,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenMirror: (LibraryMirrorShortcut) -> Unit,
    onLongPressCollection: (String) -> Unit,
    onToggleCollectionSelection: (String) -> Unit,
) {
    val model = item.nodeModel() ?: return
    val targeted = when (item) {
        is LibraryRootItem.Collection -> dragCoordinator.collectionTargetId == item.collection.collectionId
        is LibraryRootItem.Mirror -> (dragCoordinator.externalDestination as? LibraryDropDestination.RemoteMirror)
            ?.sourceId == item.mirror.sourceId
        is LibraryRootItem.Book -> false
    }
    val instant = LocalDisplayEnvironment.current.instantMotion
    val scale by animateFloatAsState(
        targetValue = if (targeted) 1.025f else 1f,
        animationSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS, easing = TsuyomiMotion.Easing),
        label = "libraryRootNodeTargetScale",
    )
    Card(
        modifier = Modifier.fillMaxWidth()
            .testTag("library-root-node-${item.key}")
            .rootNodeTargets(item, index, dragCoordinator)
            .rootNodeGestures(
                item = item,
                selected = selected,
                selectionActive = selectionActive,
                reorderEnabled = reorderEnabled,
                dragCoordinator = dragCoordinator,
                onOpenCollection = onOpenCollection,
                onOpenMirror = onOpenMirror,
                onLongPressCollection = onLongPressCollection,
                onToggleCollectionSelection = onToggleCollectionSelection,
            )
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected || targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(2.dp, if (selected || targeted) MaterialTheme.colorScheme.primary else Color.Transparent),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                .background(model.containerColor()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(model.icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = model.contentColor())
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                    .padding(start = TsuyomiSpacing.Sm, top = 28.dp, end = TsuyomiSpacing.Sm, bottom = 6.dp)
                    .testTag("library-root-node-metadata-${item.key}"),
            ) {
                Text(
                    model.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    model.supporting,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun LibraryRootNodeListRow(
    item: LibraryRootItem,
    index: Int,
    compact: Boolean,
    selected: Boolean,
    selectionActive: Boolean,
    reorderEnabled: Boolean,
    dragCoordinator: LibraryDragCoordinator,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenMirror: (LibraryMirrorShortcut) -> Unit,
    onLongPressCollection: (String) -> Unit,
    onToggleCollectionSelection: (String) -> Unit,
) {
    val model = item.nodeModel() ?: return
    val targeted = when (item) {
        is LibraryRootItem.Collection -> dragCoordinator.collectionTargetId == item.collection.collectionId
        is LibraryRootItem.Mirror -> (dragCoordinator.externalDestination as? LibraryDropDestination.RemoteMirror)
            ?.sourceId == item.mirror.sourceId
        is LibraryRootItem.Book -> false
    }
    ListItem(
        headlineContent = { Text(model.title, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(model.supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(model.icon, contentDescription = null, modifier = Modifier.size(32.dp)) },
        trailingContent = if (selected) ({ Icon(TsuyomiIcons.Selected, contentDescription = "已选择") }) else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 52.dp else 72.dp)
            .testTag("library-root-node-${item.key}")
            .rootNodeTargets(item, index, dragCoordinator)
            .rootNodeGestures(
                item = item,
                selected = selected,
                selectionActive = selectionActive,
                reorderEnabled = reorderEnabled,
                dragCoordinator = dragCoordinator,
                onOpenCollection = onOpenCollection,
                onOpenMirror = onOpenMirror,
                onLongPressCollection = onLongPressCollection,
                onToggleCollectionSelection = onToggleCollectionSelection,
            ),
        colors = ListItemDefaults.colors(
            containerColor = if (selected || targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun Modifier.rootNodeTargets(
    item: LibraryRootItem,
    index: Int,
    coordinator: LibraryDragCoordinator,
): Modifier = when (item) {
    is LibraryRootItem.Collection -> this
        .libraryShortcutDropTarget(
            coordinator,
            item.key,
            index,
            LibraryShortcutDropKind.COLLECTION,
            null,
            null,
        )
        .libraryCollectionDropTarget(coordinator, item.collection.collectionId)
    is LibraryRootItem.Mirror -> libraryShortcutDropTarget(
        coordinator,
        item.key,
        index,
        LibraryShortcutDropKind.REMOTE_MIRROR,
        null,
        item.mirror,
    )
    is LibraryRootItem.Book -> this
}

@Composable
private fun Modifier.rootNodeGestures(
    item: LibraryRootItem,
    selected: Boolean,
    selectionActive: Boolean,
    reorderEnabled: Boolean,
    dragCoordinator: LibraryDragCoordinator,
    onOpenCollection: (LibraryCollection) -> Unit,
    onOpenMirror: (LibraryMirrorShortcut) -> Unit,
    onLongPressCollection: (String) -> Unit,
    onToggleCollectionSelection: (String) -> Unit,
): Modifier {
    val onTap = {
        when (item) {
            is LibraryRootItem.Collection -> {
                if (selectionActive && item.collection.kind == CollectionKind.MANUAL) {
                    onToggleCollectionSelection(item.collection.collectionId)
                } else {
                    onOpenCollection(item.collection)
                }
            }
            is LibraryRootItem.Mirror -> onOpenMirror(item.mirror)
            is LibraryRootItem.Book -> Unit
        }
    }
    val onLongPress = {
        if (item is LibraryRootItem.Collection && item.collection.kind == CollectionKind.MANUAL) {
            onLongPressCollection(item.collection.collectionId)
        }
    }
    val nodeDescription = item.nodeModel()?.let { "${it.title}，${it.supporting}" }.orEmpty()
    return semantics {
        this.selected = selected
        role = Role.Button
        contentDescription = nodeDescription
        onClick { onTap(); true }
    }.libraryShortcutGestures(
        subjectKey = item.key,
        coordinator = dragCoordinator,
        payload = { LibraryDragPayload.Shortcut(item.key) },
        selected = selected,
        selectionActive = selectionActive,
        dragEnabled = reorderEnabled,
        canRemove = false,
        reorderSource = true,
        scrollOrientation = Orientation.Vertical,
        onTap = onTap,
        onLongPress = onLongPress,
    )
}

@Composable
internal fun LibraryFilterSummary(
    count: Int,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth().testTag("library-filter-summary"), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = TsuyomiSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit)
                    .testTag("library-filter-summary-edit")
                    .padding(vertical = TsuyomiSpacing.Sm),
            ) {
                Text("有更新", style = MaterialTheme.typography.labelLarge)
                Text("$count 本", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                Icon(TsuyomiIcons.Close, contentDescription = "清除筛选")
            }
        }
    }
}

@Composable
internal fun LibraryDragDestinationRail(
    collections: List<LibraryCollection>,
    mirrors: List<LibraryMirrorShortcut>,
    coordinator: LibraryDragCoordinator,
    modifier: Modifier = Modifier,
) {
    val sourceId = coordinator.activeBookIds.singleOrNull()?.sourceId
    val eligibleMirrors = mirrors.filter { it.targetId == null && sourceId != null && it.sourceId == sourceId && !it.frozen }
    Surface(
        modifier = modifier.fillMaxWidth().libraryShelfDropTarget(coordinator, allowsBookRoot = false)
            .testTag("library-drag-destination-rail"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
        ) {
            item(key = "create-collection") {
                DestinationChip(
                    label = "新建收藏夹",
                    icon = TsuyomiIcons.CreateFolder,
                    modifier = Modifier.libraryShortcutDropTarget(
                        coordinator,
                        "create-collection",
                        0,
                        LibraryShortcutDropKind.CREATE_COLLECTION,
                        null,
                        null,
                    ),
                )
            }
            collections.filter { it.kind == CollectionKind.MANUAL && it.parentCollectionId == null }.forEachIndexed { index, collection ->
                item(key = libraryCollectionRootId(collection.collectionId)) {
                    DestinationChip(
                        label = collection.title,
                        icon = TsuyomiIcons.Folder,
                        modifier = Modifier.libraryShortcutDropTarget(
                            coordinator,
                            libraryCollectionRootId(collection.collectionId),
                            index + 1,
                            LibraryShortcutDropKind.COLLECTION,
                            null,
                            null,
                        ),
                    )
                }
            }
            eligibleMirrors.forEachIndexed { index, mirror ->
                item(key = libraryMirrorRootId(mirror.sourceId)) {
                    DestinationChip(
                        label = mirror.label,
                        icon = TsuyomiIcons.Mirror,
                        modifier = Modifier.libraryShortcutDropTarget(
                            coordinator,
                            libraryMirrorRootId(mirror.sourceId),
                            collections.size + index + 1,
                            LibraryShortcutDropKind.REMOTE_MIRROR,
                            null,
                            mirror,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class RootNodeModel(
    val title: String,
    val supporting: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val mirror: Boolean,
)

@Composable
private fun LibraryRootItem.nodeModel(): RootNodeModel? = when (this) {
    is LibraryRootItem.Collection -> RootNodeModel(
        title = collection.title,
        supporting = when (collection.kind) {
            CollectionKind.MANUAL -> "收藏夹 · $count 本"
            CollectionKind.SMART -> "智能收藏夹 · $count 本"
            CollectionKind.SUBSCRIPTION -> "订阅收藏夹 · $count 本"
        },
        icon = when (collection.kind) {
            CollectionKind.MANUAL -> TsuyomiIcons.Folder
            CollectionKind.SMART -> TsuyomiIcons.SmartCollection
            CollectionKind.SUBSCRIPTION -> TsuyomiIcons.Compass
        },
        mirror = false,
    )
    is LibraryRootItem.Mirror -> RootNodeModel(
        title = mirror.label,
        supporting = if (mirror.frozen) "网站收藏 · 来源不可用" else "网站收藏 · ${mirror.count} 本",
        icon = TsuyomiIcons.Mirror,
        mirror = true,
    )
    is LibraryRootItem.Book -> null
}

@Composable
private fun RootNodeModel.containerColor(): Color = if (mirror) {
    MaterialTheme.colorScheme.tertiaryContainer
} else {
    MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun RootNodeModel.contentColor(): Color = if (mirror) {
    MaterialTheme.colorScheme.onTertiaryContainer
} else {
    MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
internal fun Modifier.optionalAnimateItem(scope: LazyItemScope): Modifier {
    val instant = LocalDisplayEnvironment.current.instantMotion
    return if (LocalInspectionMode.current) this else with(scope) {
        this@optionalAnimateItem.animateItem(
            fadeInSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS, easing = TsuyomiMotion.Easing),
            placementSpec = if (instant) snap() else tween(TsuyomiMotion.EXPAND_DURATION_MS, easing = TsuyomiMotion.Easing),
            fadeOutSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS, easing = TsuyomiMotion.Easing),
        )
    }
}

@Composable
internal fun Modifier.optionalAnimateItem(scope: LazyGridItemScope): Modifier {
    val instant = LocalDisplayEnvironment.current.instantMotion
    return if (LocalInspectionMode.current) this else with(scope) {
        this@optionalAnimateItem.animateItem(
            fadeInSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS, easing = TsuyomiMotion.Easing),
            placementSpec = if (instant) snap() else tween(TsuyomiMotion.EXPAND_DURATION_MS, easing = TsuyomiMotion.Easing),
            fadeOutSpec = if (instant) snap() else tween(TsuyomiMotion.SWITCH_DURATION_MS, easing = TsuyomiMotion.Easing),
        )
    }
}
