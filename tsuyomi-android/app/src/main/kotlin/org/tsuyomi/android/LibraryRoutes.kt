/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.feature.library.CollectionManagerScreen
import org.tsuyomi.feature.library.LibraryScreen
import org.tsuyomi.feature.library.LocalBookDetailsScreen
import org.tsuyomi.shared.model.BookIdentity

internal data class LocalRemoteSyncActions(
    val sourceRevision: String?,
    val canRetry: suspend (LibraryEntry?) -> Boolean,
    val retry: suspend (LibraryEntry) -> RemoteAddUiResult,
    val openSource: () -> Unit,
)

internal fun NavGraphBuilder.libraryRoutes(
    navController: NavHostController,
    controller: LibraryFlowController,
    remoteSync: LocalRemoteSyncActions,
) {
    libraryHomeRoute(navController, controller)
    collectionsRoute(controller)
    localBookRoute(navController, controller, remoteSync)
}

private fun NavGraphBuilder.libraryHomeRoute(
    navController: NavHostController,
    controller: LibraryFlowController,
) {
    composable(Routes.Library) {
        val scope = rememberCoroutineScope()
        val failureMessage = stringResource(R.string.library_read_failure_safe)
        LibraryScreen(
            state = controller.state,
            collections = controller.collections,
            selectedCollectionId = controller.selectedCollectionId,
            onCollectionChange = { collectionId ->
                controller.selectCollection(collectionId)
                scope.launch { controller.reload(failureMessage) }
            },
            onQueryChange = controller::updateQuery,
            onFilterChange = controller::updateFilter,
            onOpenBook = { entry ->
                controller.selectEntry(entry)
                navController.navigate(Routes.localBook(entry.book.identity))
            },
            onRetry = { scope.launch { controller.reload(failureMessage) } },
            onManageCollections = { navController.navigate(Routes.Collections) },
        )
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

private fun NavGraphBuilder.localBookRoute(
    navController: NavHostController,
    controller: LibraryFlowController,
    remoteSync: LocalRemoteSyncActions,
) {
    composable(Routes.LocalBook) { backStackEntry ->
        val scope = rememberCoroutineScope()
        val resources = LocalResources.current
        val sourceId = backStackEntry.arguments?.getString("sourceId")
        val remoteBookId = backStackEntry.arguments?.getString("remoteBookId")
        var resolved by remember(sourceId, remoteBookId) { mutableStateOf(false) }
        LaunchedEffect(sourceId, remoteBookId, remoteSync.sourceRevision) {
            val identity = if (sourceId != null && remoteBookId != null) {
                BookIdentity(sourceId, remoteBookId)
            } else {
                null
            }
            controller.resolveEntry(identity)
            val enabled = remoteSync.canRetry(controller.selectedEntry)
            controller.setRemoteRetryState(
                enabled = enabled,
                message = localRemoteRetryUnavailableMessageRes(
                    reconciliation = controller.selectedEntry?.reconciliation,
                    enabled = enabled,
                )?.let(resources::getString),
            )
            resolved = true
        }
        val entry = controller.selectedEntry
        if (entry == null) {
            StateView(
                kind = if (resolved) TsuyomiStateKind.EMPTY else TsuyomiStateKind.LOADING,
                title = stringResource(if (resolved) R.string.local_book_missing else R.string.local_book_loading),
            )
        } else {
            val failureMessage = stringResource(R.string.library_read_failure_safe)
            LocalBookDetailsScreen(
                entry = entry,
                tagDraft = controller.tagDraft,
                onTagDraftChange = controller::updateTagDraft,
                onSaveTags = { scope.launch { controller.saveTags(failureMessage) } },
                onSetRating = { rating -> scope.launch { controller.setRating(rating, failureMessage) } },
                onOpenSource = remoteSync.openSource,
                onRetryRemoteSync = {
                    controller.beginRemoteRetry()
                    scope.launch {
                        val result = runCatching { remoteSync.retry(entry) }
                            .getOrElse { RemoteAddUiResult.Failure("remote-add-source-open-failed") }
                        val failure = result is RemoteAddUiResult.Failure
                        controller.setRemoteRetryState(
                            enabled = false,
                            message = if (failure) resources.getString(R.string.local_remote_retry_unavailable) else null,
                        )
                        controller.reload(failureMessage)
                        controller.refreshSelectedFromVisibleEntries(entry.book.identity)
                        val enabled = remoteSync.canRetry(controller.selectedEntry)
                        controller.setRemoteRetryState(
                            enabled = enabled,
                            message = if (failure) resources.getString(R.string.local_remote_retry_unavailable) else null,
                        )
                    }
                },
                remoteRetryMessage = controller.remoteRetryMessage,
                remoteRetryEnabled = controller.remoteRetryEnabled,
                onRemove = {
                    scope.launch {
                        controller.removeSelected()
                        navController.navigateUp()
                    }
                },
            )
        }
    }
}
