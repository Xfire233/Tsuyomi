/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.feature.library.CollectionManagerScreen
import org.tsuyomi.feature.library.LibraryScreen
import org.tsuyomi.feature.library.LibraryDragPayload
import org.tsuyomi.feature.library.LibraryDropDestination
import org.tsuyomi.feature.library.LibrarySelectionDialog
import org.tsuyomi.feature.library.LibraryTagsScreen
import org.tsuyomi.feature.library.SystemLibraryFilter

internal fun NavGraphBuilder.libraryRoutes(
    navController: NavHostController,
    controller: LibraryFlowController,
    coverState: (LibraryEntry) -> CoverUiState,
    resumeReading: suspend (LibraryEntry) -> Boolean,
    openBookDetail: suspend (LibraryEntry) -> Boolean,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
) {
    libraryHomeRoute(navController, controller, coverState, onCoverVisibility, openBookDetail)
    libraryNodeRoutes(navController, controller, coverState, onCoverVisibility, resumeReading, openBookDetail)
    libraryTagsRoutes(navController, controller, coverState, onCoverVisibility, openBookDetail)
    collectionsRoute(controller)
}

private fun NavGraphBuilder.libraryHomeRoute(
    navController: NavHostController,
    controller: LibraryFlowController,
    coverState: (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    openBookDetail: suspend (LibraryEntry) -> Boolean,
) {
    composable(Routes.Library) {
        val scope = rememberCoroutineScope()
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        LibraryScreen(
            state = controller.state,
            collections = controller.collections,
            showNavigationNodes = true,
            coverState = coverState,
            onCoverVisibility = onCoverVisibility,
            onOpenSystemNode = { filter ->
                controller.selectSystemFilter(filter)
                navController.navigate(Routes.librarySystem(filter))
            },
            onOpenCollection = { collection ->
                controller.selectCollection(collection.collectionId)
                navController.navigate(Routes.libraryCollection(collection.collectionId))
            },
            onOpenBook = { entry ->
                controller.openOrToggleEntry(entry)
                scope.launch { openBookDetail(entry) }
            },
            onCreateCollection = { navController.navigate(Routes.Collections) },
            onRetry = { scope.launch { controller.reload(failureMessage) } },
            onDismissSort = controller::dismissSort,
            onSelectSort = controller::selectSort,
            onSelectSortDirection = controller::selectSortDirection,
            onLongPressBook = controller::longPressBook,
            onToggleBookSelection = controller::toggleBookSelection,
            onLongPressCollection = controller::longPressCollection,
            onToggleCollectionSelection = controller::toggleCollectionSelection,
            onDropBooks = { payload, destination ->
                handleLibraryDrop(controller, scope, failureMessage, true, payload, destination)
            },
            reorderEnabled = controller.state.sortMode == org.tsuyomi.feature.library.LibrarySortMode.CUSTOM &&
                controller.state.filter == SystemLibraryFilter.ALL,
            onShortcutLockedChanged = { locked -> scope.launch { controller.setShortcutLocked(locked) } },
            onDismissSelectionDialog = controller::dismissSelectionDialog,
            onCreateCollectionFromSelection = { title -> scope.launch {
                controller.createCollectionFromSelection(title, failureMessage)
            } },
            onAddSelectionToCollection = { id -> scope.launch {
                controller.addSelectionToCollection(id, failureMessage)
            } },
            onRemoveSelection = { scope.launch { controller.removeSelection(failureMessage) } },
        )
    }
}

private fun NavGraphBuilder.libraryNodeRoutes(
    navController: NavHostController,
    controller: LibraryFlowController,
    coverState: (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    resumeReading: suspend (LibraryEntry) -> Boolean,
    openBookDetail: suspend (LibraryEntry) -> Boolean,
) {
    composable(Routes.LibrarySystem) { backStackEntry ->
        val scope = rememberCoroutineScope()
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        val filter = backStackEntry.arguments?.getString("filter")
            ?.let { name -> runCatching { SystemLibraryFilter.valueOf(name) }.getOrNull() }
            ?.takeUnless { it == SystemLibraryFilter.ALL }
        LaunchedEffect(filter) {
            if (filter != null) {
                controller.selectSystemFilter(filter)
                controller.reload(failureMessage)
            }
        }
        LibraryScreen(
            state = controller.state,
            collections = controller.collections,
            showNavigationNodes = false,
            coverState = coverState,
            onCoverVisibility = onCoverVisibility,
            onOpenSystemNode = {},
            onOpenCollection = {},
            onOpenBook = { entry ->
                controller.openOrToggleEntry(entry)
                scope.launch {
                    if (filter == SystemLibraryFilter.CONTINUE && entry.progress != null && entry.sourceAvailable) {
                        if (!resumeReading(entry)) openBookDetail(entry)
                    } else {
                        openBookDetail(entry)
                    }
                }
            },
            onCreateCollection = { navController.navigate(Routes.Collections) },
            onRetry = { scope.launch { controller.reload(failureMessage) } },
            onDismissSort = controller::dismissSort,
            onSelectSort = controller::selectSort,
            onSelectSortDirection = controller::selectSortDirection,
            onLongPressBook = controller::longPressBook,
            onToggleBookSelection = controller::toggleBookSelection,
            onLongPressCollection = controller::longPressCollection,
            onToggleCollectionSelection = controller::toggleCollectionSelection,
            onDropBooks = { payload, destination ->
                handleLibraryDrop(controller, scope, failureMessage, false, payload, destination)
            },
            reorderEnabled = false,
            onShortcutLockedChanged = { locked -> scope.launch { controller.setShortcutLocked(locked) } },
            onDismissSelectionDialog = controller::dismissSelectionDialog,
            onCreateCollectionFromSelection = { title -> scope.launch {
                controller.createCollectionFromSelection(title, failureMessage)
            } },
            onAddSelectionToCollection = { id -> scope.launch {
                controller.addSelectionToCollection(id, failureMessage)
            } },
            onRemoveSelection = { scope.launch { controller.removeSelection(failureMessage) } },
        )
    }
    composable(Routes.LibraryCollection) { backStackEntry ->
        val scope = rememberCoroutineScope()
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        val collectionId = backStackEntry.arguments?.getString("collectionId")
        LaunchedEffect(collectionId) {
            if (!collectionId.isNullOrBlank()) {
                controller.selectCollection(collectionId)
                controller.reload(failureMessage)
            }
        }
        LibraryScreen(
            state = controller.state,
            collections = controller.collections,
            showNavigationNodes = false,
            coverState = coverState,
            onCoverVisibility = onCoverVisibility,
            onOpenSystemNode = {},
            onOpenCollection = {},
            onOpenBook = { entry ->
                controller.openOrToggleEntry(entry)
                scope.launch { openBookDetail(entry) }
            },
            onCreateCollection = { navController.navigate(Routes.Collections) },
            onRetry = { scope.launch { controller.reload(failureMessage) } },
            onDismissSort = controller::dismissSort,
            onSelectSort = controller::selectSort,
            onSelectSortDirection = controller::selectSortDirection,
            onLongPressBook = controller::longPressBook,
            onToggleBookSelection = controller::toggleBookSelection,
            onLongPressCollection = controller::longPressCollection,
            onToggleCollectionSelection = controller::toggleCollectionSelection,
            onDropBooks = { payload, destination ->
                handleLibraryDrop(controller, scope, failureMessage, true, payload, destination)
            },
            reorderEnabled = controller.state.sortMode == org.tsuyomi.feature.library.LibrarySortMode.CUSTOM &&
                controller.state.filter == SystemLibraryFilter.ALL &&
                controller.collections.any {
                    it.collectionId == collectionId && it.kind == org.tsuyomi.core.database.CollectionKind.MANUAL
                },
            onShortcutLockedChanged = { locked -> scope.launch { controller.setShortcutLocked(locked) } },
            onDismissSelectionDialog = controller::dismissSelectionDialog,
            onCreateCollectionFromSelection = { title -> scope.launch {
                controller.createCollectionFromSelection(title, failureMessage)
            } },
            onAddSelectionToCollection = { id -> scope.launch {
                controller.addSelectionToCollection(id, failureMessage)
            } },
            onRemoveSelection = { scope.launch { controller.removeSelection(failureMessage) } },
        )
    }
}

private fun NavGraphBuilder.libraryTagsRoutes(
    navController: NavHostController,
    controller: LibraryFlowController,
    coverState: (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    openBookDetail: suspend (LibraryEntry) -> Boolean,
) {
    composable(Routes.LibraryTags) {
        LibraryTagsScreen(
            entries = controller.state.entries,
            onOpenTag = { tag -> navController.navigate(Routes.libraryTag(tag)) },
        )
    }
    composable(Routes.LibraryTagBooks) { backStackEntry ->
        val tag = backStackEntry.arguments?.getString("tag").orEmpty()
        val scope = rememberCoroutineScope()
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        LibraryScreen(
            state = controller.state.copy(
                entries = controller.state.entries.filter { tag in it.localTags },
                filter = SystemLibraryFilter.ALL,
            ),
            collections = controller.collections,
            coverState = coverState,
            onCoverVisibility = onCoverVisibility,
            showNavigationNodes = false,
            onOpenSystemNode = {},
            onOpenCollection = {},
            onOpenBook = { entry ->
                controller.openOrToggleEntry(entry)
                scope.launch { openBookDetail(entry) }
            },
            onCreateCollection = { navController.navigate(Routes.Collections) },
            onRetry = { scope.launch { controller.reload(failureMessage) } },
            onDismissSort = controller::dismissSort,
            onSelectSort = controller::selectSort,
            onSelectSortDirection = controller::selectSortDirection,
            onLongPressBook = controller::longPressBook,
            onToggleBookSelection = controller::toggleBookSelection,
            onLongPressCollection = controller::longPressCollection,
            onToggleCollectionSelection = controller::toggleCollectionSelection,
            onDropBooks = { payload, destination ->
                handleLibraryDrop(controller, scope, failureMessage, false, payload, destination)
            },
            reorderEnabled = false,
            onShortcutLockedChanged = { locked -> scope.launch { controller.setShortcutLocked(locked) } },
            onDismissSelectionDialog = controller::dismissSelectionDialog,
            onCreateCollectionFromSelection = { title -> scope.launch {
                controller.createCollectionFromSelection(title, failureMessage)
            } },
            onAddSelectionToCollection = { id -> scope.launch {
                controller.addSelectionToCollection(id, failureMessage)
            } },
            onRemoveSelection = { scope.launch { controller.removeSelection(failureMessage) } },
        )
    }
}
private fun handleLibraryDrop(
    controller: LibraryFlowController,
    scope: CoroutineScope,
    failureMessage: String,
    allowLibraryReorder: Boolean,
    payload: LibraryDragPayload,
    destination: LibraryDropDestination,
) {
    when (payload) {
        is LibraryDragPayload.Books -> when (destination) {
            is LibraryDropDestination.Root -> scope.launch {
                controller.dropBooksOnShortcutRoot(payload.identities, destination.index, failureMessage)
            }
            is LibraryDropDestination.Collection -> {
                controller.prepareDraggedBooks(payload.identities)
                scope.launch { controller.addSelectionToCollection(destination.id, failureMessage) }
            }
            is LibraryDropDestination.Book -> {
                val shortcutId = destination.shortcutId
                if (shortcutId != null) {
                    controller.requestShortcutCollectionCreation(
                        moved = payload.identities,
                        target = destination.identity,
                        insertionIndex = controller.shortcutIndex(shortcutId),
                        replacementShortcutIds = buildSet {
                            add(shortcutId)
                            if (payload.fromShortcut) {
                                payload.identities.forEach {
                                    add(org.tsuyomi.feature.library.libraryBookShortcutId(it))
                                }
                            }
                        },
                    )
                } else {
                    controller.requestBookDropOnBook(payload.identities, destination.identity)
                }
            }
            is LibraryDropDestination.Library -> if (allowLibraryReorder && !payload.fromShortcut) {
                scope.launch { controller.reorderBooks(payload.identities, destination.index, failureMessage) }
            }
            LibraryDropDestination.Remove -> {
                if (payload.fromShortcut && payload.identities.size == 1) {
                    scope.launch { controller.removeBookShortcut(payload.identities.single(), failureMessage) }
                } else {
                    controller.prepareDraggedBooks(payload.identities)
                    controller.requestSelectionDialog(LibrarySelectionDialog.CONFIRM_REMOVE)
                }
            }
        }
        is LibraryDragPayload.Shortcut -> when (destination) {
            is LibraryDropDestination.Root -> scope.launch {
                controller.moveShortcut(payload.id, destination.index, failureMessage)
            }
            LibraryDropDestination.Remove -> scope.launch {
                controller.removeShortcut(payload.id, failureMessage)
            }
            is LibraryDropDestination.Book,
            is LibraryDropDestination.Collection,
            is LibraryDropDestination.Library -> Unit
        }
    }
}


private fun NavGraphBuilder.collectionsRoute(controller: LibraryFlowController) {
    composable(Routes.Collections) {
        val scope = rememberCoroutineScope()
        val resources = LocalResources.current
        val failureMessage = resources.getString(R.string.library_read_failure_safe)
        CollectionManagerScreen(
            collections = controller.collections,
            message = controller.collectionMessage,
            onCreateManual = { title ->
                scope.launch {
                    val saved = controller.createManualCollection(title, failureMessage)
                    controller.showCollectionMessage(
                        resources.getString(if (saved) R.string.collection_saved else R.string.collection_invalid),
                    )
                }
            },
            onCreateSmart = { title, matchAll, drafts ->
                scope.launch {
                    val saved = controller.createSmartCollection(
                        title = title,
                        matchAll = matchAll,
                        drafts = drafts,
                        failureMessage = failureMessage,
                    )
                    controller.showCollectionMessage(
                        resources.getString(if (saved) R.string.collection_saved else R.string.collection_invalid),
                    )
                }
            },
            onDelete = { collection ->
                scope.launch {
                    controller.deleteCollection(collection, failureMessage)
                    controller.showCollectionMessage(resources.getString(R.string.collection_deleted))
                }
            },
        )
    }
}
