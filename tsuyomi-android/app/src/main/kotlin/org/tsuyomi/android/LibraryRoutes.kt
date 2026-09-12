/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.feature.library.CollectionManagerScreen
import org.tsuyomi.feature.library.LibraryScreen
import org.tsuyomi.feature.library.LibrarySearchScreen
import org.tsuyomi.feature.library.LibraryDragPayload
import org.tsuyomi.feature.library.LibraryDropDestination
import org.tsuyomi.feature.library.LibrarySelectionDialog
import org.tsuyomi.feature.library.LibraryTagsScreen
import org.tsuyomi.feature.library.SystemLibraryFilter

internal fun NavGraphBuilder.libraryRoutes(
    navController: NavHostController,
    controller: LibraryFlowController,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    resumeReading: suspend (LibraryEntry) -> Boolean,
    openBookDetail: suspend (LibraryEntry) -> Boolean,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    openRemoteDestination: suspend (LibraryEntry, LibraryDropDestination.RemoteMirror) -> Unit,
    onManualUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onOpenUpdateSettings: () -> Unit,
    onIgnoreUpdate: suspend (org.tsuyomi.shared.librarydomain.UnresolvedUpdate) -> org.tsuyomi.shared.librarydomain.UpdateUndo?,
    onUndoUpdate: suspend (org.tsuyomi.shared.librarydomain.UpdateUndo) -> Unit,
) {
    libraryHomeRoute(
        navController,
        controller,
        coverState,
        onCoverVisibility,
        resumeReading,
        openBookDetail,
        openRemoteDestination,
        onManualUpdate,
        onCancelUpdate,
        onOpenUpdateSettings,
        onIgnoreUpdate,
        onUndoUpdate,
    )
    librarySearchRoute(navController, controller, coverState, onCoverVisibility, openBookDetail)
    libraryNodeRoutes(navController, controller, coverState, onCoverVisibility, openBookDetail, openRemoteDestination)
    libraryTagsRoutes(navController, controller, coverState, onCoverVisibility, openBookDetail, openRemoteDestination)
    collectionsRoute(controller)
}

private fun NavGraphBuilder.libraryHomeRoute(
    navController: NavHostController,
    controller: LibraryFlowController,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    resumeReading: suspend (LibraryEntry) -> Boolean,
    openBookDetail: suspend (LibraryEntry) -> Boolean,
    openRemoteDestination: suspend (LibraryEntry, LibraryDropDestination.RemoteMirror) -> Unit,
    onManualUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onOpenUpdateSettings: () -> Unit,
    onIgnoreUpdate: suspend (org.tsuyomi.shared.librarydomain.UnresolvedUpdate) -> org.tsuyomi.shared.librarydomain.UpdateUndo?,
    onUndoUpdate: suspend (org.tsuyomi.shared.librarydomain.UpdateUndo) -> Unit,
) {
    composable(Routes.Library) {
        val scope = rememberCoroutineScope()
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        LaunchedEffect(Unit) { controller.restoreLibraryHome() }
        LibraryScreen(
            state = controller.state,
            collections = controller.collections,
            showNavigationNodes = true,
            coverState = coverState,
            onCoverVisibility = onCoverVisibility,
            onSelectTab = { filter -> scope.launch { controller.selectTab(filter) } },
            onOpenSystemNode = { filter -> scope.launch {
                controller.selectTab(filter)
                navController.navigate(Routes.Library) { launchSingleTop = true }
            } },
            onOpenCollection = { collection ->
                controller.selectCollection(collection.collectionId)
                navController.navigate(Routes.libraryCollection(collection.collectionId))
            },
            onOpenMirror = { mirror ->
                navController.navigate(
                    mirror.targetId?.let { Routes.libraryMirrorFolder(mirror.sourceId, it) }
                        ?: Routes.libraryMirror(mirror.sourceId),
                )
            },
            onOpenUpdateSettings = onOpenUpdateSettings,
            onCancelUpdateScan = onCancelUpdate,
            onRefreshUpdates = onManualUpdate,
            onEditFilter = { controller.setFilterAndSortPanelExpanded(true) },
            onClearFilter = { scope.launch {
                controller.setUpdateFilter(org.tsuyomi.feature.library.LibraryUpdateFilter.ALL)
            } },
            onOpenBook = { entry ->
                controller.openOrToggleEntry(entry)
                scope.launch {
                    if (controller.state.filter == SystemLibraryFilter.CONTINUE && entry.progress != null && entry.sourceAvailable) {
                        if (!resumeReading(entry)) openBookDetail(entry)
                    } else {
                        openBookDetail(entry)
                    }
                }
            },
            onCreateCollection = { navController.navigate(Routes.Collections) },
            onRetry = { scope.launch { controller.reload(failureMessage) } },
            onIgnoreUpdate = onIgnoreUpdate,
            onUndoUpdate = onUndoUpdate,
            onLongPressBook = controller::longPressBook,
            onToggleBookSelection = controller::toggleBookSelection,
            onLongPressCollection = controller::longPressCollection,
            onToggleCollectionSelection = controller::toggleCollectionSelection,
            onDropBooks = { payload, destination ->
                handleLibraryDrop(controller, scope, failureMessage, true, payload, destination, openRemoteDestination)
            },
            onViewportChanged = { viewport ->
                controller.updateViewport(viewport.firstVisibleIndex, viewport.firstVisibleOffset)
            },
            onViewportSettled = { index, offset -> controller.persistViewport(index, offset) },
            reorderEnabled = controller.state.sortMode == org.tsuyomi.feature.library.LibrarySortMode.CUSTOM &&
                controller.state.filter == SystemLibraryFilter.ALL &&
                controller.state.updateFilter == org.tsuyomi.feature.library.LibraryUpdateFilter.ALL,
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

private fun NavGraphBuilder.librarySearchRoute(
    navController: NavHostController,
    controller: LibraryFlowController,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    openBookDetail: suspend (LibraryEntry) -> Boolean,
) {
    composable(Routes.LibrarySearch) { entry ->
        val scope = rememberCoroutineScope()
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        val query by entry.savedStateHandle
            .getStateFlow(LibrarySearchQueryKey, "")
            .collectAsStateWithLifecycle()
        val searchQuery by entry.savedStateHandle
            .getStateFlow(LibrarySearchEffectiveQueryKey, "")
            .collectAsStateWithLifecycle()
        val preferredCollectionId by entry.savedStateHandle
            .getStateFlow(LibrarySearchCallerCollectionKey, controller.selectedCollectionId)
            .collectAsStateWithLifecycle()
        LaunchedEffect(query) {
            val nextQuery = query.trim()
            if (nextQuery.isNotEmpty()) delay(LibrarySearchDebounceMillis)
            entry.savedStateHandle[LibrarySearchEffectiveQueryKey] = nextQuery
        }
        LibrarySearchScreen(
            query = query,
            searchQuery = searchQuery,
            books = controller.searchableEntries,
            collections = controller.collections,
            preferredCollectionId = preferredCollectionId,
            loading = controller.state.loading,
            failure = controller.state.failure,
            onQueryChange = { value -> entry.savedStateHandle[LibrarySearchQueryKey] = value },
            onSearch = {
                entry.savedStateHandle[LibrarySearchEffectiveQueryKey] = query.trim()
            },
            onRetry = { scope.launch { controller.reload(failureMessage) } },
            onOpenCollection = { collection ->
                controller.selectCollection(collection.collectionId)
                navController.navigate(Routes.libraryCollection(collection.collectionId))
            },
            onOpenBook = { book ->
                controller.openOrToggleEntry(book)
                scope.launch { openBookDetail(book) }
            },
            coverState = coverState,
            onCoverVisibility = onCoverVisibility,
        )
    }
}

private const val LibrarySearchQueryKey = "library.search.query"
private const val LibrarySearchEffectiveQueryKey = "library.search.effective-query"
private const val LibrarySearchCallerCollectionKey = "library.search.caller-collection"
private const val LibrarySearchDebounceMillis = 120L

private fun NavGraphBuilder.libraryNodeRoutes(
    navController: NavHostController,
    controller: LibraryFlowController,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    openBookDetail: suspend (LibraryEntry) -> Boolean,
    openRemoteDestination: suspend (LibraryEntry, LibraryDropDestination.RemoteMirror) -> Unit,
) {
    composable(Routes.LibrarySystem) { backStackEntry ->
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        val filter = backStackEntry.arguments?.getString("filter")
            ?.let { name -> runCatching { SystemLibraryFilter.valueOf(name) }.getOrNull() }
            ?.takeIf { it == SystemLibraryFilter.CONTINUE || it == SystemLibraryFilter.READ_LATER }
            ?: SystemLibraryFilter.ALL
        LaunchedEffect(filter) {
            controller.selectTab(filter)
            navController.navigate(Routes.Library) {
                popUpTo(Routes.Library) { inclusive = false }
                launchSingleTop = true
            }
        }
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
            onOpenSystemNode = {},
            onOpenCollection = { collection ->
                controller.selectCollection(collection.collectionId)
                navController.navigate(Routes.libraryCollection(collection.collectionId))
            },
            coverState = coverState,
            onCoverVisibility = onCoverVisibility,
            onOpenBook = { entry ->
                controller.openOrToggleEntry(entry)
                scope.launch { openBookDetail(entry) }
            },
            onCreateCollection = { navController.navigate(Routes.Collections) },
            onRetry = { scope.launch { controller.reload(failureMessage) } },
            onLongPressBook = controller::longPressBook,
            onToggleBookSelection = controller::toggleBookSelection,
            onLongPressCollection = controller::longPressCollection,
            onToggleCollectionSelection = controller::toggleCollectionSelection,
            onDropBooks = { payload, destination ->
                handleLibraryDrop(controller, scope, failureMessage, true, payload, destination, openRemoteDestination)
            },
            reorderEnabled = controller.state.sortMode == org.tsuyomi.feature.library.LibrarySortMode.CUSTOM &&
                controller.state.filter == SystemLibraryFilter.ALL &&
                controller.collections.any {
                    it.collectionId == collectionId && it.kind == org.tsuyomi.core.database.CollectionKind.MANUAL
                },
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
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    openBookDetail: suspend (LibraryEntry) -> Boolean,
    openRemoteDestination: suspend (LibraryEntry, LibraryDropDestination.RemoteMirror) -> Unit,
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
            onLongPressBook = controller::longPressBook,
            onToggleBookSelection = controller::toggleBookSelection,
            onLongPressCollection = controller::longPressCollection,
            onToggleCollectionSelection = controller::toggleCollectionSelection,
            onDropBooks = { payload, destination ->
                handleLibraryDrop(controller, scope, failureMessage, false, payload, destination, openRemoteDestination)
            },
            reorderEnabled = false,
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
    openRemoteDestination: suspend (LibraryEntry, LibraryDropDestination.RemoteMirror) -> Unit,
) {
    when (payload) {
        is LibraryDragPayload.Books -> when (destination) {
            LibraryDropDestination.CreateCollection -> {
                controller.requestRootCollectionCreation(payload.identities)
            }
            is LibraryDropDestination.Collection -> {
                controller.prepareDraggedBooks(payload.identities)
                scope.launch { controller.addSelectionToCollection(destination.id, failureMessage) }
            }
            is LibraryDropDestination.Book -> {
                controller.requestRootCollectionCreation(payload.identities, destination.identity)
            }
            is LibraryDropDestination.Library -> if (allowLibraryReorder) {
                scope.launch {
                    if (controller.state.isRootProjection) {
                        controller.reorderRootBooks(payload.identities, destination.index, failureMessage)
                    } else {
                        controller.reorderBooks(payload.identities, destination.index, failureMessage)
                    }
                }
            }
            is LibraryDropDestination.RemoteMirror -> payload.identities.singleOrNull()?.let { identity ->
                controller.state.entries.firstOrNull { it.book.identity == identity }?.let { entry ->
                    scope.launch { openRemoteDestination(entry, destination) }
                }
            }
            is LibraryDropDestination.Root,
            LibraryDropDestination.LocalCopy,
            LibraryDropDestination.RemoteRemove,
            -> Unit
            LibraryDropDestination.Remove -> {
                controller.prepareDraggedBooks(payload.identities)
                controller.requestSelectionDialog(LibrarySelectionDialog.CONFIRM_REMOVE)
            }
        }
        is LibraryDragPayload.Shortcut -> when (destination) {
            is LibraryDropDestination.Library -> scope.launch {
                controller.reorderRootNode(payload.id, destination.index, failureMessage)
            }
            is LibraryDropDestination.Root -> scope.launch {
                controller.reorderRootNode(payload.id, destination.index, failureMessage)
            }
            is LibraryDropDestination.Book,
            is LibraryDropDestination.Collection,
            LibraryDropDestination.CreateCollection,
            is LibraryDropDestination.RemoteMirror,
            LibraryDropDestination.LocalCopy,
            LibraryDropDestination.RemoteRemove,
            LibraryDropDestination.Remove,
            -> Unit
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
