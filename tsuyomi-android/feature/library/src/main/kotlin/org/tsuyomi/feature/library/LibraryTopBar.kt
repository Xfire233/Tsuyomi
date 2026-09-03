/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.ui.components.TsuyomiOverflowAction
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiTopBarAction
import org.tsuyomi.core.ui.icons.TsuyomiIcons

/** Library owns the visible action grammar; the app shell only supplies route commands. */
@Composable
fun LibraryTopBar(
    title: String,
    bookCount: Int,
    layout: LibraryLayout,
    sortMode: LibrarySortMode,
    sortDescending: Boolean,
    refreshing: Boolean,
    root: Boolean,
    onNavigateUp: (() -> Unit)?,
    onSearch: () -> Unit,
    onCycleLayout: () -> Unit,
    onRefresh: () -> Unit,
    onSort: () -> Unit,
    onTags: () -> Unit,
    selectionKind: LibrarySelectionKind? = null,
    selectedCount: Int = 0,
    allVisibleSelected: Boolean = false,
    onClearSelection: () -> Unit = {},
    onToggleAllSelection: () -> Unit = {},
    onCreateCollectionFromSelection: () -> Unit = {},
    onAddSelectionToCollection: () -> Unit = {},
    onRemoveSelection: () -> Unit = {},
) {
    if (selectionKind != null) {
        TsuyomiTopBar(
            title = stringResource(R.string.library_selection_count, selectedCount),
            onNavigateUp = onClearSelection,
            navigationIcon = TsuyomiIcons.Close,
            navigationContentDescription = stringResource(R.string.library_selection_close),
            actions = buildList {
                add(
                    TsuyomiTopBarAction(
                        icon = if (allVisibleSelected) TsuyomiIcons.DeselectAll else TsuyomiIcons.SelectAll,
                        label = stringResource(
                            if (allVisibleSelected) R.string.library_selection_clear_all else R.string.library_selection_select_all,
                        ),
                        onClick = onToggleAllSelection,
                    ),
                )
                if (selectionKind == LibrarySelectionKind.BOOK) {
                    add(
                        TsuyomiTopBarAction(
                            icon = TsuyomiIcons.CreateFolder,
                            label = stringResource(R.string.library_selection_create_collection),
                            onClick = onCreateCollectionFromSelection,
                        ),
                    )
                }
                add(
                    TsuyomiTopBarAction(
                        icon = TsuyomiIcons.MoveToFolder,
                        label = stringResource(R.string.library_selection_add_collection),
                        onClick = onAddSelectionToCollection,
                    ),
                )
                add(
                    TsuyomiTopBarAction(
                        icon = TsuyomiIcons.Delete,
                        label = stringResource(R.string.library_selection_remove),
                        onClick = onRemoveSelection,
                    ),
                )
            },
        )
        return
    }
    val layoutLabel = stringResource(
        when (layout) {
            LibraryLayout.GRID -> R.string.library_layout_grid
            LibraryLayout.LIST -> R.string.library_layout_list
            LibraryLayout.COMPACT -> R.string.library_layout_compact
        },
    )
    val layoutIcon = when (layout) {
        LibraryLayout.GRID -> TsuyomiIcons.Grid
        LibraryLayout.LIST -> TsuyomiIcons.List
        LibraryLayout.COMPACT -> TsuyomiIcons.Compact
    }
    TsuyomiTopBar(
        title = title,
        subtitle = stringResource(R.string.library_count, bookCount),
        onNavigateUp = onNavigateUp,
        actions = buildList {
            if (root) {
                add(
                    TsuyomiTopBarAction(
                        icon = TsuyomiIcons.Refresh,
                        label = stringResource(R.string.library_action_sync_updates),
                        onClick = onRefresh,
                    ),
                )
            }
            add(
                TsuyomiTopBarAction(
                    icon = TsuyomiIcons.Search,
                    label = stringResource(R.string.library_action_search),
                    onClick = onSearch,
                ),
            )
            add(
                TsuyomiTopBarAction(
                    icon = layoutIcon,
                    label = stringResource(R.string.library_action_cycle_layout, layoutLabel),
                    onClick = onCycleLayout,
                ),
            )
        },
        overflow = buildList {
            if (!root) {
                add(
                    TsuyomiOverflowAction(
                        label = stringResource(R.string.library_action_refresh),
                        onClick = onRefresh,
                        icon = TsuyomiIcons.Refresh,
                        enabled = !refreshing,
                    ),
                )
            }
            add(
                TsuyomiOverflowAction(
                    label = stringResource(
                        R.string.library_action_sort,
                        sortMode.label,
                        if (sortDescending) stringResource(R.string.library_sort_descending) else stringResource(R.string.library_sort_ascending),
                    ),
                    onClick = onSort,
                ),
            )
            add(
                TsuyomiOverflowAction(
                    label = stringResource(R.string.library_action_tags),
                    onClick = onTags,
                ),
            )
        },
    )
}

@Composable
fun libraryNodeRouteTitle(
    filterName: String?,
    collectionId: String?,
    collections: List<LibraryCollection>,
): String? {
    val filter = filterName?.let { name -> runCatching { SystemLibraryFilter.valueOf(name) }.getOrNull() }
    return filter?.let { stringResource(it.label()) }
        ?: collections.firstOrNull { it.collectionId == collectionId }?.title
}
