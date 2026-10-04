/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.tsuyomi.core.ui.components.TsuyomiDialog
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.database.CollectionDeletionPolicy
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.feature.library.CollectionRuleScreen
import org.tsuyomi.feature.library.SmartConditionDraft
import org.tsuyomi.feature.library.SmartDraftNode
import org.tsuyomi.feature.library.SmartField
import org.tsuyomi.feature.library.LibraryUiState
import org.tsuyomi.feature.library.invalidConditionPaths
import org.tsuyomi.feature.library.LibraryScreen
import org.tsuyomi.feature.library.LibrarySearchScreen
import org.tsuyomi.feature.library.LibraryDragPayload
import org.tsuyomi.feature.library.LibraryDropDestination
import org.tsuyomi.feature.library.LibrarySelectionKind
import org.tsuyomi.feature.library.LibraryUpdateFilter
import org.tsuyomi.feature.library.LibrarySelectionDialog
import org.tsuyomi.feature.library.LibraryTagsScreen
import org.tsuyomi.feature.library.LibraryTagOwnership
import org.tsuyomi.feature.library.normalizeLocalLibraryTagName
import org.tsuyomi.feature.library.normalizeSourceLibraryTagName
import org.tsuyomi.feature.library.SystemLibraryFilter

internal fun NavGraphBuilder.libraryRoutes(
    navController: NavHostController,
    controller: LibraryFlowController,
    chrome: @Composable (String, NavBackStackEntry, LibraryUiState?) -> Unit,
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
        chrome,
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
    collectionBookPickerRoute(navController, controller, coverState, onCoverVisibility)
    librarySearchRoute(navController, controller, coverState, onCoverVisibility, openBookDetail)
    libraryNodeRoutes(navController, controller, coverState, onCoverVisibility, openBookDetail, openRemoteDestination, chrome)
    libraryTagsRoutes(navController, controller, coverState, onCoverVisibility, openBookDetail, openRemoteDestination)
    collectionRuleRoutes(navController, controller)
}

private fun NavGraphBuilder.libraryHomeRoute(
    navController: NavHostController,
    chrome: @Composable (String, NavBackStackEntry, LibraryUiState?) -> Unit,
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
    appDestination(Routes.Library, instantPopEnter = true) { backStackEntry ->
        val scope = rememberCoroutineScope()
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        val changedMessage = stringResource(R.string.collection_delete_changed)
        LaunchedEffect(Unit) {
            controller.restoreLibraryHome()
            controller.reload(failureMessage)
        }
        val rootState = controller.rootScreenState()
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            chrome(Routes.Library, backStackEntry, rootState)
            LibraryScreen(
                state = rootState,
                modifier = Modifier.weight(1f),
                primaryTabStates = controller.primaryTabStates(),
                collections = controller.collections,
                showNavigationNodes = true,
                coverState = coverState,
                onCoverVisibility = onCoverVisibility,
                onSelectTab = controller::selectTab,
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
                        if (controller.state.filter == SystemLibraryFilter.CONTINUE && entry.sourceAvailable) {
                            if (!resumeReading(entry)) openBookDetail(entry)
                        } else {
                            openBookDetail(entry)
                        }
                    }
                },
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
                deletionFolderCount = controller.deletionPreview?.folderCount,
                deletionMembershipCount = controller.deletionPreview?.membershipCount ?: 0,
                deletionMessage = controller.deletionError,
                deletionConfirmEnabled = controller.deletionConfirmEnabled,
                deletionSubtree = controller.deletionPreview?.policy == CollectionDeletionPolicy.DELETE_SUBTREES,
                onDeletionPolicyChange = { subtree ->
                    controller.deletionPreview?.selectedIds?.let { ids ->
                        controller.pauseDeletionConfirmation()
                        scope.launch {
                            controller.requestCollectionDeletion(ids, failureMessage,
                                if (subtree) CollectionDeletionPolicy.DELETE_SUBTREES else CollectionDeletionPolicy.REPARENT_CHILDREN)
                        }
                    }
                },
                onDismissSelectionDialog = controller::dismissSelectionDialog,
                onCreateCollectionFromSelection = { title -> scope.launch {
                    controller.createCollectionFromSelection(title, failureMessage)
                } },
                onAddSelectionToCollection = { id -> scope.launch {
                    controller.addSelectionToCollection(id, failureMessage)
                } },
                onRemoveSelection = { scope.launch {
                    controller.removeSelection(failureMessage, changedMessage)
                } },
            )
        }
    }
}

private fun NavGraphBuilder.librarySearchRoute(
    navController: NavHostController,
    controller: LibraryFlowController,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
    openBookDetail: suspend (LibraryEntry) -> Boolean,
) {
    appDestination(Routes.LibrarySearch) { entry ->
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
    chrome: @Composable (String, NavBackStackEntry, LibraryUiState?) -> Unit,
) {
    appDestination(Routes.LibrarySystem) { backStackEntry ->
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
    appDestination(Routes.LibraryCollection, instantPopExit = true) { backStackEntry ->
        // Keep an outgoing destination's own projection until NavHost removes its whole layer.
        var childState by remember(backStackEntry.id) { mutableStateOf(controller.state) }
        if (navController.currentBackStackEntryAsState().value?.id == backStackEntry.id) {
            childState = controller.state
        }
        val scope = rememberCoroutineScope()
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        val changedMessage = stringResource(R.string.collection_delete_changed)
        val collectionId = backStackEntry.arguments?.getString("collectionId")
        LaunchedEffect(collectionId) {
            if (!collectionId.isNullOrBlank()) {
                controller.selectCollection(collectionId)
                controller.reload(failureMessage)
            }
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            chrome(Routes.LibraryCollection, backStackEntry, childState)
            LibraryScreen(
                state = childState,
                modifier = Modifier.weight(1f),
                collections = controller.collections,
                currentCollectionId = collectionId,
                showNavigationNodes = false,
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
                onRetry = { scope.launch { controller.reload(failureMessage) } },
                onLongPressBook = controller::longPressBook,
                onToggleBookSelection = controller::toggleBookSelection,
                onLongPressCollection = controller::longPressCollection,
                onToggleCollectionSelection = controller::toggleCollectionSelection,
                onDropBooks = { payload, destination ->
                    handleLibraryDrop(controller, scope, failureMessage, true, payload, destination, openRemoteDestination)
                },
                reorderEnabled = childState.sortMode == org.tsuyomi.feature.library.LibrarySortMode.CUSTOM &&
                    childState.filter == SystemLibraryFilter.ALL &&
                    controller.collections.any {
                        it.collectionId == collectionId && it.kind == org.tsuyomi.shared.librarydomain.CollectionKind.MANUAL
                    },
                deletionFolderCount = controller.deletionPreview?.folderCount,
                deletionMembershipCount = controller.deletionPreview?.membershipCount ?: 0,
                deletionMessage = controller.deletionError,
                deletionConfirmEnabled = controller.deletionConfirmEnabled,
                deletionSubtree = controller.deletionPreview?.policy == CollectionDeletionPolicy.DELETE_SUBTREES,
                onDeletionPolicyChange = { subtree ->
                    controller.deletionPreview?.selectedIds?.let { ids ->
                        controller.pauseDeletionConfirmation()
                        scope.launch {
                            controller.requestCollectionDeletion(ids, failureMessage,
                                if (subtree) CollectionDeletionPolicy.DELETE_SUBTREES else CollectionDeletionPolicy.REPARENT_CHILDREN)
                        }
                    }
                },
                onDismissSelectionDialog = controller::dismissSelectionDialog,
                onCreateCollectionFromSelection = { title -> scope.launch {
                    controller.createCollectionFromSelection(title, failureMessage)
                } },
                onAddSelectionToCollection = { id -> scope.launch {
                    controller.addSelectionToCollection(id, failureMessage)
                } },
                onRemoveSelection = { scope.launch {
                    val deletingCurrent = collectionId in controller.deletionPreview?.selectedIds.orEmpty()
                    if (controller.removeSelection(failureMessage, changedMessage) && deletingCurrent) navController.navigateUp()
                } },
            )
        }
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
    appDestination(Routes.LibraryTags) {
        LibraryTagsScreen(
            entries = controller.fullLocalEntries(),
            sourceLabels = controller.tagSourceLabels(),
            layout = controller.tagLayout,
            onOpenTag = { destination -> navController.navigate(Routes.libraryTag(destination)) },
        )
    }
    appDestination(Routes.LibraryTagBooks) { backStackEntry ->
        val ownership = runCatching {
            LibraryTagOwnership.valueOf(backStackEntry.arguments?.getString("ownership").orEmpty())
        }.getOrDefault(LibraryTagOwnership.LOCAL)
        val sourceId = backStackEntry.arguments?.getString("sourceId").orEmpty().takeUnless { it == "_" }
        val tag = backStackEntry.arguments?.getString("tag").orEmpty()
        val scope = rememberCoroutineScope()
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        LibraryScreen(
            state = controller.tagProjection(ownership, sourceId, tag),
            coverState = coverState,
            onCoverVisibility = onCoverVisibility,
            showNavigationNodes = false,
            onOpenCollection = {},
            onOpenBook = { entry ->
                controller.openOrToggleEntry(entry)
                scope.launch { openBookDetail(entry) }
            },
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
            collections = controller.collections,
        )
    }
}

internal fun LibraryFlowController.tagProjection(
    ownership: LibraryTagOwnership,
    sourceId: String?,
    normalizedTag: String,
) = state.copy(
    entries = fullLocalEntries().filter { entry ->
        when (ownership) {
            LibraryTagOwnership.LOCAL -> entry.localTags.any { normalizeLocalLibraryTagName(it) == normalizedTag }
            LibraryTagOwnership.SOURCE -> entry.book.identity.sourceId == sourceId &&
                entry.book.remoteTags.any { normalizeSourceLibraryTagName(it) == normalizedTag }
        }
    },
    filter = SystemLibraryFilter.ALL,
    updateFilter = org.tsuyomi.feature.library.LibraryUpdateFilter.ALL,
    updateOnlyEntries = emptyList(),
    isRootProjection = false,
)

private fun LibraryFlowController.tagSourceLabels(): Map<String, String> =
    state.mirrorShortcuts
        .filter { it.targetId == null }
        .associate { it.sourceId to it.label } + sourceTagLabels

private fun LibraryFlowController.fullLocalEntries(): List<LibraryEntry> =
    searchableEntries.distinctBy { it.book.identity }
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


private const val RuleUpKey = "collection.rule.up"

internal fun encodeRuleDraft(node: SmartDraftNode): JSONObject = JSONObject().apply {
    put("negations", node.negations)
    when (node) {
        is SmartDraftNode.Group -> {
            put("kind", "group")
            put("all", node.matchAll)
            put("synthetic", node.syntheticRoot)
            put("children", JSONArray().apply { node.children.forEach { put(encodeRuleDraft(it)) } })
        }
        is SmartDraftNode.Condition -> {
            put("kind", "condition")
            put("field", node.draft.field.name)
            put("value", node.draft.value)
            put("tagAll", node.draft.matchAllTags)
            put("facetSource", node.draft.facetSourceId)
            node.originalPredicateJson?.let { put("original", it) }
        }
    }
}

internal fun decodeRuleDraft(value: JSONObject): SmartDraftNode = when (value.getString("kind")) {
    "group" -> {
        val children = value.getJSONArray("children")
        SmartDraftNode.Group(value.getBoolean("all"),
            List(children.length()) { decodeRuleDraft(children.getJSONObject(it)) },
            value.getInt("negations"), value.optBoolean("synthetic"))
    }
    "condition" -> SmartDraftNode.Condition(
        SmartConditionDraft(SmartField.valueOf(value.getString("field")), value.getString("value"),
            matchAllTags = value.getBoolean("tagAll"), facetSourceId = value.getString("facetSource")),
        value.getInt("negations"), value.optString("original").takeIf(String::isNotEmpty),
    )
    else -> error("Unknown rule draft node")
}

private fun SmartDraftNode.Group.nodeAt(path: List<Int>): SmartDraftNode = path.fold(this as SmartDraftNode) { node, index ->
    (node as SmartDraftNode.Group).children[index]
}

private fun NavGraphBuilder.collectionBookPickerRoute(
    navController: NavHostController,
    controller: LibraryFlowController,
    coverState: @Composable (LibraryEntry) -> CoverUiState,
    onCoverVisibility: (LibraryEntry, Boolean) -> Unit,
) {
    appDestination(Routes.CollectionBookPicker) { entry ->
        val context = LocalContext.current
        val createEntry = remember(entry) { navController.previousBackStackEntry }
        val owner = remember(createEntry) { createEntry?.let { collectionRuleDraftOwner(it, context) } }
        LaunchedEffect(owner) { owner?.open(null) { null } }
        val draft = owner?.draft
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        val scope = rememberCoroutineScope()
        BackHandler { owner?.edit { it.copy(selectedBooks = it.booksBeforePick, booksBeforePick = emptySet()) }; navController.navigateUp() }
        if (owner == null || draft == null || owner.failed) {
            StateView(if (owner == null || owner.failed) TsuyomiStateKind.ERROR else TsuyomiStateKind.LOADING,
                failureMessage)
            return@appDestination
        }
        val root = controller.primaryTabStates().getValue(SystemLibraryFilter.ALL)
        LibraryScreen(
            state = root.copy(
                entries = root.entries.filter { it.localMembership },
                isRootProjection = false,
                updateFilter = LibraryUpdateFilter.ALL,
                selectionKind = LibrarySelectionKind.BOOK,
                selectedBookIds = draft.selectedBooks,
                updates = emptyMap(),
            ),
            collections = emptyList(),
            showNavigationNodes = false,
            onOpenCollection = {},
            onOpenBook = { book -> owner.edit { current ->
                current.copy(selectedBooks = current.selectedBooks.toggle(book.book.identity))
            } },
            onLongPressBook = { identity -> owner.edit { current ->
                current.copy(selectedBooks = current.selectedBooks.toggle(identity))
            } },
            onToggleBookSelection = { identity -> owner.edit { current ->
                current.copy(selectedBooks = current.selectedBooks.toggle(identity))
            } },
            onRetry = { scope.launch { controller.reload(failureMessage) } },
            coverState = coverState,
            onCoverVisibility = onCoverVisibility,
        )
    }
}

private fun Set<org.tsuyomi.shared.model.BookIdentity>.toggle(
    identity: org.tsuyomi.shared.model.BookIdentity,
): Set<org.tsuyomi.shared.model.BookIdentity> =
    if (identity in this) this - identity else this + identity

private fun NavGraphBuilder.collectionRuleRoutes(navController: NavHostController, controller: LibraryFlowController) {
    appDestination(Routes.NewCollection) { entry ->
        CollectionRuleRoute(navController, controller, entry, null)
    }
    appDestination(Routes.CollectionRule) { entry ->
        CollectionRuleRoute(navController, controller, entry, entry.arguments?.getString("collectionId"))
    }
}

@Composable
private fun CollectionRuleRoute(
    navController: NavHostController,
    controller: LibraryFlowController,
    entry: androidx.navigation.NavBackStackEntry,
    collectionId: String?,
) {
    val scope = rememberCoroutineScope()
    val failureMessage = stringResource(R.string.library_read_failure_safe)
    val saveFailureMessage = stringResource(R.string.collection_manual_save_failed)
    val saved = entry.savedStateHandle
    val context = LocalContext.current
    val owner = remember(entry) { collectionRuleDraftOwner(entry, context) }
    DisposableEffect(entry, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) owner.flushOnPause()
        }
        entry.lifecycle.addObserver(observer)
        onDispose { entry.lifecycle.removeObserver(observer) }
    }
    val draft = owner.draft
    val upRequested by saved.getStateFlow(RuleUpKey, false).collectAsStateWithLifecycle()
    var showDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    LaunchedEffect(owner, collectionId) {
        owner.open(collectionId) {
            if (collectionId != null) controller.reload(failureMessage)
            if (collectionId == null) CollectionRuleDraft("",
                SmartDraftNode.Group(true, listOf(SmartDraftNode.Condition(SmartConditionDraft(
                    field = SmartField.TAG, value = "",
                )))))
            else controller.collectionRuleDraft(collectionId)
        }
    }
    val focus = draft?.focus.orEmpty()
    val focusedPath = remember(focus) {
        if (focus.isBlank()) emptyList() else focus.split('/').map(String::toInt)
    }
    fun leave() {
        if (saving) return
        val current = owner.draft
        val currentFocus = current?.focus.orEmpty()
        val path = if (currentFocus.isBlank()) emptyList() else currentFocus.split('/').map(String::toInt)
        if (path.isNotEmpty()) {
            owner.edit { it.copy(focus = path.dropLast(1).joinToString("/")) }
            return
        }
        val dirty = current != null && (current.title != current.originalTitle ||
            ruleDraftDigest(current.encodedTree) != current.originalDigest ||
            collectionId == null && (current.selectedBooks.isNotEmpty() || current.advancedExpanded))
        if (dirty || owner.failed) showDiscard = true else {
            owner.discard()
            navController.navigateUp()
        }
    }
    BackHandler(enabled = !showDiscard) { leave() }
    LaunchedEffect(upRequested) {
        if (upRequested) {
            saved[RuleUpKey] = false
            leave()
        }
    }
    if (showDiscard) {
        TsuyomiDialog(
            onDismissRequest = { showDiscard = false },
            title = stringResource(R.string.collection_rule_unsaved_title),
            confirmLabel = stringResource(R.string.collection_rule_discard),
            onConfirm = { showDiscard = false; owner.discard(); navController.navigateUp() },
            dismissLabel = stringResource(R.string.collection_rule_stay),
            destructive = true,
        )
    }
    if (draft == null || owner.failed && draft.revision == 0) {
        StateView(
            kind = if (owner.failed) TsuyomiStateKind.ERROR else TsuyomiStateKind.LOADING,
            title = stringResource(if (owner.failed) R.string.library_read_failure_safe
                else org.tsuyomi.feature.library.R.string.library_loading),
        )
        return
    }
    val tree = draft.tree
    val failing = tree.invalidConditionPaths().filterTo(linkedSetOf()) { path ->
        !controller.unchangedPredicate(tree.nodeAt(path) as SmartDraftNode.Condition)
    }
    val invalid = if (draft.attempted) failing else emptySet()
    val activeFocusedPath = focusedPath.takeIf { path ->
        path.isEmpty() || runCatching { tree.nodeAt(path) is SmartDraftNode.Group }.getOrDefault(false)
    } ?: emptyList<Int>().also { owner.edit { current -> current.copy(focus = "") } }
    CollectionRuleScreen(
        title = draft.title,
        tree = tree,
        collections = controller.collections,
        sourceIds = controller.searchableEntries.map { it.book.identity.sourceId }.distinct(),
        sourceLabels = controller.tagSourceLabels(),
        tagChoices = buildSet {
            for (entry in controller.searchableEntries) {
                for (tag in entry.localTags) if (tag.isNotBlank()) add(tag)
                for (tag in entry.book.remoteTags) if (tag.isNotBlank()) add(tag)
            }
        }.sorted(),
        nameError = draft.attempted && (draft.title.isBlank() || draft.title.trim().length >
            if (collectionId == null && !draft.advancedExpanded) 256 else 512),
        invalidConditions = invalid,
        saving = saving,
        saveFailure = saveFailureMessage.takeIf { saveFailed || owner.failed },
        onTitleChange = { value ->
            if (!saving) {
                saveFailed = false
                owner.edit { it.copy(title = value) }
            }
        },
        onTreeChange = { value ->
            if (!saving) {
                saveFailed = false
                owner.edit { it.copy(tree = value, encodedTree = encodeRuleDraft(value).toString()) }
            }
        },
        focusedPath = activeFocusedPath,
        onFocusChange = { path -> owner.edit { it.copy(focus = path.joinToString("/")) } },
        creation = collectionId == null,
        advancedExpanded = draft.advancedExpanded,
        selectedBookCount = draft.selectedBooks.size,
        onAdvancedExpandedChange = { expanded ->
            if (!saving) owner.edit { it.copy(advancedExpanded = expanded, focus = "", attempted = false) }
        },
        onChooseBooks = {
            if (!saving && !draft.advancedExpanded) {
                owner.edit { it.copy(booksBeforePick = it.selectedBooks) }
                scope.launch {
                    if (owner.flush()) navController.navigate(Routes.CollectionBookPicker)
                    else saveFailed = true
                }
            }
        },
        onSave = {
            val latest = requireNotNull(owner.draft)
            val smart = collectionId != null || latest.advancedExpanded
            val latestFailing = if (smart) latest.tree.invalidConditionPaths().filterTo(linkedSetOf()) { path ->
                !controller.unchangedPredicate(latest.tree.nodeAt(path) as SmartDraftNode.Condition)
            } else emptySet()
            owner.edit { it.copy(attempted = true) }
            if (latestFailing.isNotEmpty()) owner.edit {
                it.copy(focus = latestFailing.first().dropLast(1).joinToString("/"))
            }
            if (latest.title.isNotBlank() && latest.title.trim().length <= (if (smart) 512 else 256)) {
                if (latestFailing.isEmpty() && !saving) {
                    saving = true
                    scope.launch {
                        try {
                            if (!owner.flush()) {
                                saveFailed = true
                            } else if (if (smart) {
                                controller.saveSmartCollection(collectionId, latest.title, latest.tree,
                                    saveFailureMessage, owner.id)
                            } else {
                                controller.createManualCollection(latest.title, latest.selectedBooks,
                                    saveFailureMessage, owner.id)
                            }) {
                                owner.discard()
                                navController.navigateUp()
                            } else {
                                saveFailed = true
                            }
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        },
    )
}
