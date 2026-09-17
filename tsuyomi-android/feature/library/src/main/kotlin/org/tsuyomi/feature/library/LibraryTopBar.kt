/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.shared.librarydomain.LibraryCollection
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
    updateFilter: LibraryUpdateFilter,
    filterSortPanelExpanded: Boolean,
    onFilterSortPanelExpandedChange: (Boolean) -> Unit,
    onSetUpdateFilter: (LibraryUpdateFilter) -> Unit,
    onNavigateUp: (() -> Unit)?,
    onSearch: () -> Unit,
    onCycleLayout: () -> Unit,
    onRefresh: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenUpdateSettings: () -> Unit,
    onSelectSort: (LibrarySortMode) -> Unit,
    onSelectSortDirection: (Boolean) -> Unit,
    onTags: () -> Unit,
    onCreateCollection: () -> Unit,
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
    val filterLabel = stringResource(
        if (updateFilter == LibraryUpdateFilter.UPDATES_ONLY) {
            R.string.library_filter_unread
        } else {
            R.string.library_filter_all
        },
    )
    val directionLabel = stringResource(
        if (sortDescending) R.string.library_sort_descending else R.string.library_sort_ascending,
    )
    val filterSortLabel = stringResource(
        if (root) R.string.library_action_filter_sort_root else R.string.library_action_filter_sort,
        *if (root) arrayOf(filterLabel, sortMode.label, directionLabel) else arrayOf(sortMode.label, directionLabel),
    )
    val narrowWindow = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp() < 360.dp
    }
    TsuyomiTopBar(
        title = title,
        subtitle = stringResource(R.string.library_count, bookCount),
        onNavigateUp = onNavigateUp,
        maxVisibleActions = if (root) {
            if (narrowWindow) 2 else 4
        } else {
            null
        },
        actions = buildList {
            if (root) {
                add(
                    TsuyomiTopBarAction(
                        icon = TsuyomiIcons.Refresh,
                        label = stringResource(R.string.library_action_refresh),
                        onClick = onCheckUpdates,
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
                    icon = TsuyomiIcons.Filter,
                    label = filterSortLabel,
                    onClick = {},
                    menu = buildList {
                        if (root) {
                            add(
                                TsuyomiOverflowAction(
                                    label = stringResource(R.string.library_filter_all),
                                    onClick = { onSetUpdateFilter(LibraryUpdateFilter.ALL) },
                                    icon = if (updateFilter == LibraryUpdateFilter.ALL) TsuyomiIcons.Selected else null,
                                    section = stringResource(R.string.library_filter_sort_filter_section),
                                    selected = updateFilter == LibraryUpdateFilter.ALL,
                                ),
                            )
                            add(
                                TsuyomiOverflowAction(
                                    label = stringResource(R.string.library_filter_unread),
                                    onClick = { onSetUpdateFilter(LibraryUpdateFilter.UPDATES_ONLY) },
                                    icon = if (updateFilter == LibraryUpdateFilter.UPDATES_ONLY) TsuyomiIcons.Selected else null,
                                    selected = updateFilter == LibraryUpdateFilter.UPDATES_ONLY,
                                ),
                            )
                        }
                        LibrarySortMode.entries.forEachIndexed { index, option ->
                            add(
                                TsuyomiOverflowAction(
                                    label = stringResource(R.string.library_sort_mode_option, option.label),
                                    onClick = { onSelectSort(option) },
                                    icon = if (sortMode == option) TsuyomiIcons.Selected else null,
                                    selected = sortMode == option,
                                    section = if (index == 0) {
                                        stringResource(R.string.library_filter_sort_sort_section)
                                    } else {
                                        null
                                    },
                                ),
                            )
                        }
                        listOf(false, true).forEach { descending ->
                            val optionDirection = stringResource(
                                if (descending) R.string.library_sort_descending else R.string.library_sort_ascending,
                            )
                            add(
                                TsuyomiOverflowAction(
                                    label = stringResource(R.string.library_sort_direction_option, optionDirection),
                                    onClick = { onSelectSortDirection(descending) },
                                    icon = if (sortDescending == descending) TsuyomiIcons.Selected else null,
                                    selected = sortDescending == descending,
                                ),
                            )
                        }
                    },
                    menuTitle = stringResource(R.string.library_filter_sort_panel_title),
                    menuExpanded = filterSortPanelExpanded,
                    onMenuExpandedChange = onFilterSortPanelExpandedChange,
                    testTag = "library-update-filter",
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
            if (root) {
                add(
                    TsuyomiOverflowAction(
                        label = stringResource(R.string.library_action_create_collection),
                        onClick = onCreateCollection,
                        icon = TsuyomiIcons.CreateFolder,
                    ),
                )
            }
            if (root) {
                add(
                    TsuyomiOverflowAction(
                        label = stringResource(R.string.updates_settings),
                        onClick = onOpenUpdateSettings,
                        icon = TsuyomiIcons.Settings,
                    ),
                )
            }
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
fun LibraryTagsTopBar(
    title: String,
    layout: LibraryTagLayout,
    onNavigateUp: () -> Unit,
    onCycleLayout: () -> Unit,
) {
    val layoutLabel = stringResource(
        if (layout == LibraryTagLayout.CHIPS) R.string.library_layout_compact else R.string.library_layout_list,
    )
    TsuyomiTopBar(
        title = title,
        onNavigateUp = onNavigateUp,
        actions = listOf(
            TsuyomiTopBarAction(
                icon = if (layout == LibraryTagLayout.CHIPS) TsuyomiIcons.Grid else TsuyomiIcons.List,
                label = stringResource(R.string.library_action_cycle_layout, layoutLabel),
                onClick = onCycleLayout,
            ),
        ),
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
