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
    updateFilter: LibraryUpdateFilter,
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
            if (root) {
                add(
                    TsuyomiTopBarAction(
                        icon = TsuyomiIcons.Filter,
                        label = stringResource(R.string.library_action_filter, filterLabel),
                        onClick = {},
                        menu = listOf(
                            TsuyomiOverflowAction(
                                label = stringResource(R.string.library_filter_all),
                                onClick = { onSetUpdateFilter(LibraryUpdateFilter.ALL) },
                                icon = if (updateFilter == LibraryUpdateFilter.ALL) {
                                    TsuyomiIcons.Selected
                                } else {
                                    null
                                },
                            ),
                            TsuyomiOverflowAction(
                                label = stringResource(R.string.library_filter_unread),
                                onClick = { onSetUpdateFilter(LibraryUpdateFilter.UPDATES_ONLY) },
                                icon = if (updateFilter == LibraryUpdateFilter.UPDATES_ONLY) {
                                    TsuyomiIcons.Selected
                                } else {
                                    null
                                },
                            ),
                        ),
                        testTag = "library-update-filter",
                    ),
                )
            }
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
                    label = stringResource(
                        R.string.library_action_sort,
                        sortMode.label,
                        if (sortDescending) {
                            stringResource(R.string.library_sort_descending)
                        } else {
                            stringResource(R.string.library_sort_ascending)
                        },
                    ),
                    onClick = {},
                    menu = buildList {
                        LibrarySortMode.entries.forEach { option ->
                            add(
                                TsuyomiOverflowAction(
                                    label = stringResource(R.string.library_sort_basis_option, option.label),
                                    onClick = { onSelectSort(option) },
                                    icon = if (sortMode == option) TsuyomiIcons.Selected else null,
                                ),
                            )
                        }
                        listOf(false, true).forEach { descending ->
                            val directionLabel = stringResource(
                                if (descending) R.string.library_sort_descending else R.string.library_sort_ascending,
                            )
                            add(
                                TsuyomiOverflowAction(
                                    label = stringResource(R.string.library_sort_direction_option, directionLabel),
                                    onClick = { onSelectSortDirection(descending) },
                                    icon = if (sortDescending == descending) TsuyomiIcons.Selected else null,
                                ),
                            )
                        }
                    },
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
