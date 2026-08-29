/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.feature.book.BookDetailScreen
import org.tsuyomi.feature.book.BookDirectoryScreen
import org.tsuyomi.feature.browse.BrowseScreen
import org.tsuyomi.feature.browse.RemoteLibraryScreen
import org.tsuyomi.feature.reader.ReaderScreen
import org.tsuyomi.feature.search.SearchScreen
import org.tsuyomi.shared.backup.PortableReaderPreferences

internal fun NavGraphBuilder.sourceRoutes(
    navController: NavHostController,
    owner: SourceRouteOwner,
    readerPreferences: PortableReaderPreferences,
) {
    browseRoute(navController, owner)
    remoteLibraryRoute(navController, owner)
    searchRoute(navController, owner)
    detailRoute(navController, owner)
    directoryRoute(navController, owner)
    readerRoute(navController, owner, readerPreferences)
    verificationRoute(navController, owner)
}

private fun NavGraphBuilder.browseRoute(navController: NavHostController, owner: SourceRouteOwner) {
    composable(Routes.Browse) {
        val scope = rememberCoroutineScope()
        BrowseScreen(
            state = owner.installer.state,
            onRequestImport = owner::requestImport,
            onOpenInstalledSource = { scope.launch { owner.openInstalledSource() } },
            remoteLibraryAvailable = owner.remoteLibraryAvailable,
            onOpenRemoteLibrary = { scope.launch { owner.openRemoteLibrary() } },
            onApproveInstall = { allowDowngrade -> scope.launch { owner.installer.approve(allowDowngrade) } },
            onDismissApproval = owner.installer::dismissApproval,
            onDismissFailure = owner.installer::dismissFailure,
        )
    }
}

private fun NavGraphBuilder.remoteLibraryRoute(navController: NavHostController, owner: SourceRouteOwner) {
    composable(Routes.RemoteLibrary) {
        val scope = rememberCoroutineScope()
        RemoteLibraryScreen(
            books = owner.flow.remoteLibrary.books,
            loading = owner.ui.remoteLibraryLoading,
            message = owner.ui.remoteLibraryMessage,
            writebackAvailable = owner.ui.remoteWritebackAvailable,
            writebackEnabled = owner.ui.remoteWritebackEnabled,
            onPull = { scope.launch { owner.pullRemoteLibrary() } },
            onWritebackChanged = { enabled ->
                if (enabled) owner.requestWritebackChange(true) else scope.launch { owner.disableWriteback() }
            },
            onOpenBook = { book ->
                scope.launch {
                    owner.flow.selectBook(book)
                    navController.navigate(Routes.Detail)
                }
            },
        )
    }
}

private fun NavGraphBuilder.searchRoute(navController: NavHostController, owner: SourceRouteOwner) {
    composable(Routes.Search) {
        val scope = rememberCoroutineScope()
        SearchScreen(
            query = owner.flow.query,
            state = owner.flow.searchState,
            onQueryChange = owner.flow::updateQuery,
            onSearch = { scope.launch { owner.flow.search() } },
            onSelectBook = { book ->
                scope.launch {
                    owner.flow.selectBook(book)
                    navController.navigate(Routes.Detail)
                }
            },
            onRetry = { scope.launch { owner.flow.search() } },
            onUseOfflineCache = { scope.launch { owner.flow.search(offlineOnly = true) } },
            onOpenVerification = { navController.navigate(Routes.Verification) },
        )
    }
}

private fun NavGraphBuilder.detailRoute(navController: NavHostController, owner: SourceRouteOwner) {
    composable(Routes.Detail) {
        val scope = rememberCoroutineScope()
        val reconciliation = owner.flow.remoteLibrary.selectedBookReconciliation
        BookDetailScreen(
            state = owner.flow.detailState,
            inLibrary = owner.flow.remoteLibrary.selectedBookInLibrary,
            addWritesRemote = owner.flow.remoteLibrary.selectedBookAddWritesRemote,
            reconciliationLabel = reconciliation?.let { state ->
                stringResource(
                    when (state) {
                        RemoteReconciliationState.PENDING_USER_ACTION,
                        RemoteReconciliationState.IN_FLIGHT,
                        -> R.string.remote_add_pending
                        RemoteReconciliationState.CONFIRMED -> R.string.remote_add_confirmed
                        RemoteReconciliationState.UNRESOLVED -> R.string.remote_add_unresolved
                        RemoteReconciliationState.CANCELLED -> R.string.remote_add_cancelled
                    },
                )
            },
            canRetryRemoteSync = owner.flow.remoteLibrary.selectedBookAddWritesRemote &&
                reconciliation in setOf(RemoteReconciliationState.UNRESOLVED, RemoteReconciliationState.CANCELLED),
            onRetryRemoteSync = { scope.launch { owner.retrySelectedBookRemoteAdd() } },
            onAddToLibrary = { scope.launch { owner.addSelectedBook() } },
            onRemoveFromLibrary = { scope.launch { owner.removeSelectedBook() } },
            onOpenDirectory = {
                scope.launch {
                    owner.flow.loadDirectory()
                    navController.navigate(Routes.Directory)
                }
            },
            onRetry = { scope.launch { owner.flow.reloadDetail(offlineOnly = false) } },
            onUseOfflineCache = { scope.launch { owner.flow.reloadDetail(offlineOnly = true) } },
            onOpenVerification = { navController.navigate(Routes.Verification) },
        )
    }
}

private fun NavGraphBuilder.directoryRoute(navController: NavHostController, owner: SourceRouteOwner) {
    composable(Routes.Directory) {
        val scope = rememberCoroutineScope()
        BookDirectoryScreen(
            state = owner.flow.directoryState,
            onSelectChapter = { chapter ->
                scope.launch {
                    owner.flow.selectChapter(chapter)
                    navController.navigate(Routes.Reader)
                }
            },
            onRetry = { scope.launch { owner.flow.loadDirectory() } },
            onUseOfflineCache = { scope.launch { owner.flow.loadDirectory(offlineOnly = true) } },
            onOpenVerification = { navController.navigate(Routes.Verification) },
        )
    }
}

private fun NavGraphBuilder.readerRoute(
    navController: NavHostController,
    owner: SourceRouteOwner,
    readerPreferences: PortableReaderPreferences,
) {
    composable(Routes.Reader) {
        val scope = rememberCoroutineScope()
        ReaderScreen(
            document = owner.flow.readerDocument,
            loading = owner.flow.readerLoading,
            failure = owner.flow.readerFailure,
            restoredLocator = owner.flow.restoredLocator,
            onLocatorChanged = { locator, precision -> scope.launch { owner.flow.saveProgress(locator, precision) } },
            preferences = readerPreferences,
            onRetry = { scope.launch { owner.flow.reloadChapter(offlineOnly = false) } },
            onUseOfflineCache = { scope.launch { owner.flow.reloadChapter(offlineOnly = true) } },
            onOpenVerification = { navController.navigate(Routes.Verification) },
        )
    }
}

private fun NavGraphBuilder.verificationRoute(navController: NavHostController, owner: SourceRouteOwner) {
    composable(Routes.Verification) {
        val scope = rememberCoroutineScope()
        owner.installer.activePackage?.let { packageInfo ->
            ManualVerificationRoute(
                packageInfo = packageInfo,
                onCompleted = { scope.launch { owner.completeVerification() } },
                onCancel = { navController.navigateUp() },
            )
        }
    }
}
