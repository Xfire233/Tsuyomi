/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.source.extensionmanager.RemoteOperation
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.model.BookIdentity


internal const val VerifiedSearchResultSequenceKey = "source.search.verified-page-sequence"
internal const val VerifiedDetailResultSequenceKey = "source.detail.verified-page-sequence"
internal const val VerifiedDirectoryResultSequenceKey = "source.directory.verified-page-sequence"
internal const val VerifiedChapterResultSequenceKey = "source.chapter.verified-page-sequence"
internal const val ResumeSourceIdKey = "source.resume.source-id"
internal const val ResumeRemoteBookIdKey = "source.resume.remote-book-id"



data class VerifiedPageUseResult(
    val accepted: Boolean,
    val diagnostic: SourceDiagnostic? = null,
)

@Stable
internal class SourceRouteOwner(
    val installer: SourceInstallController,
    val flow: SourceFlowController,
    private val navController: NavHostController,
    private val requestImportAction: () -> Unit,
    private val onLibraryChanged: suspend () -> Unit,
) {
    val remoteLibraryAvailable: Boolean
        get() = installer.activePackage?.manifest?.capabilities?.remoteLibrary?.policies
            ?.containsKey(RemoteOperation.READ) == true
    val sourceHomeAvailable: Boolean
        get() = installer.activePackage?.manifest?.capabilities?.home?.enabled == true


    fun requestImport() {
        requestImportAction()
    }
    fun navigateToSourceHome() {
        navController.navigate(Routes.SourceHome)
    }
    fun navigateToRemoteLibrary() {
        navController.navigate(Routes.RemoteLibrary)
    }
    fun navigateToVerification() {
        navController.navigate(Routes.Verification)
    }

    suspend fun refreshInstalledSources() {
        installer.refreshInstalled()
    }


    suspend fun openInstalledSource() {
        installer.activePackage?.let { packageInfo ->
            flow.open(packageInfo)
            navController.navigate(Routes.Search)
        }
    }
    suspend fun refreshSourceHome() {
        val packageInfo = installer.activePackage ?: return
        flow.home.refresh { filters, cursor ->
            flow.open(packageInfo)
            flow.loadHome(filters, cursor)
        }
    }
    suspend fun openLibraryDetail(entry: LibraryEntry): Boolean {
        val packageInfo = installer.activePackage
            ?.takeIf { it.manifest.sourceId.value == entry.book.identity.sourceId }
            ?: return false
        flow.open(packageInfo)
        val preparedFromCache = flow.prepareDetail(entry.book.identity)
        if (!preparedFromCache) {
            val canonicalUrl = entry.book.canonicalUrl ?: return false
            flow.prepareBook(
                SourceBookSummary(
                    identity = entry.book.identity,
                    title = entry.book.title,
                    author = entry.book.author,
                    coverUrl = entry.book.coverUrl,
                    canonicalUrl = canonicalUrl,
                ),
            )
        } else {
            onLibraryChanged()
        }
        navController.navigate(Routes.Detail)
        return true
    }

    suspend fun resumeReading(entry: LibraryEntry): Boolean {
        val packageInfo = installer.activePackage
            ?.takeIf { it.manifest.sourceId.value == entry.book.identity.sourceId }
            ?: return false
        navController.navigate(Routes.Browse) { launchSingleTop = true }
        navController.getBackStackEntry(Routes.Browse).savedStateHandle.apply {
            this[ResumeSourceIdKey] = packageInfo.manifest.sourceId.value
            this[ResumeRemoteBookIdKey] = entry.book.identity.remoteBookId
        }
        return true
    }
    suspend fun prepareScheduledResume(identity: BookIdentity): Boolean {
        val packageInfo = installer.activePackage
            ?.takeIf { it.manifest.sourceId.value == identity.sourceId }
            ?: return false
        flow.open(packageInfo)
        return flow.prepareResume(identity)
    }

    suspend fun prepareScheduledDetail(identity: BookIdentity): Boolean {
        val packageInfo = installer.activePackage
            ?.takeIf { it.manifest.sourceId.value == identity.sourceId }
            ?: return false
        flow.open(packageInfo)
        return flow.prepareDetail(identity)
    }


    suspend fun openRemoteLibrary() {
        installer.activePackage?.let { packageInfo ->
            flow.open(packageInfo)
            navController.navigate(Routes.RemoteLibrary)
        }
    }


    suspend fun retrySelectedBookRemoteAdd() {
        flow.retrySelectedBookRemoteAdd()
        onLibraryChanged()
    }

    suspend fun addSelectedBook() {
        flow.addSelectedBook()
        onLibraryChanged()
    }

    suspend fun removeSelectedBook() {
        flow.removeSelectedBook()
        onLibraryChanged()
    }
    suspend fun notifyLibraryChanged() {
        onLibraryChanged()
    }

    suspend fun completeVerification() {
        flow.reopenWithStoredCredentials()
        navController.navigateUp()
    }

    suspend fun completeVerifiedPage() {
        flow.reopenAfterVerifiedPage()
        navController.navigateUp()
    }

    suspend fun searchVerifiedPageRequestUrl(): String? = flow.searchVerifiedPageRequestUrl()

    suspend fun useSearchVerifiedPage(snapshot: CapturedVerifiedPage): VerifiedPageUseResult {
        val previous = navController.previousBackStackEntry
            ?.takeIf { it.destination.route == Routes.Search }
            ?: return VerifiedPageUseResult(accepted = false)
        val accepted = flow.searchVerifiedPage(snapshot)
        if (accepted) {
            previous.savedStateHandle[VerifiedSearchResultSequenceKey] =
                (previous.savedStateHandle[VerifiedSearchResultSequenceKey] ?: 0L) + 1L
        }
        return VerifiedPageUseResult(
            accepted = accepted,
            diagnostic = (flow.searchState as? org.tsuyomi.feature.search.SearchResultState.Failure)?.diagnostic,
        )
    }
    suspend fun detailVerifiedPageRequestUrl(): String? = flow.detailVerifiedPageRequestUrl()

    suspend fun useDetailVerifiedPage(snapshot: CapturedVerifiedPage): VerifiedPageUseResult {
        val previous = navController.previousBackStackEntry
            ?.takeIf { it.destination.route == Routes.Detail }
            ?: return VerifiedPageUseResult(accepted = false)
        val accepted = flow.detailVerifiedPage(snapshot)
        if (accepted) {
            previous.savedStateHandle[VerifiedDetailResultSequenceKey] =
                (previous.savedStateHandle[VerifiedDetailResultSequenceKey] ?: 0L) + 1L
        }
        return VerifiedPageUseResult(
            accepted = accepted,
            diagnostic = (flow.detailState as? org.tsuyomi.feature.book.SourceBookState.Failure)?.diagnostic,
        )
    }

    suspend fun directoryVerifiedPageRequestUrl(): String? = flow.directoryVerifiedPageRequestUrl()

    suspend fun useDirectoryVerifiedPage(snapshot: CapturedVerifiedPage): VerifiedPageUseResult {
        val previous = navController.previousBackStackEntry
            ?.takeIf { it.destination.route == Routes.Detail }
            ?: return VerifiedPageUseResult(accepted = false)
        val accepted = flow.directoryVerifiedPage(snapshot)
        if (accepted) {
            previous.savedStateHandle[VerifiedDirectoryResultSequenceKey] =
                (previous.savedStateHandle[VerifiedDirectoryResultSequenceKey] ?: 0L) + 1L
        }
        return VerifiedPageUseResult(
            accepted = accepted,
            diagnostic = (flow.directoryState as? org.tsuyomi.feature.book.SourceBookState.Failure)?.diagnostic,
        )
    }
    suspend fun chapterVerifiedPageRequestUrl(): String? = flow.chapterVerifiedPageRequestUrl()

    suspend fun useChapterVerifiedPage(snapshot: CapturedVerifiedPage): VerifiedPageUseResult {
        val previous = navController.previousBackStackEntry
            ?.takeIf { it.destination.route == Routes.Reader }
            ?: return VerifiedPageUseResult(accepted = false)
        val accepted = flow.chapterVerifiedPage(snapshot)
        if (accepted) {
            previous.savedStateHandle[VerifiedChapterResultSequenceKey] =
                (previous.savedStateHandle[VerifiedChapterResultSequenceKey] ?: 0L) + 1L
        }
        return VerifiedPageUseResult(
            accepted = accepted,
            diagnostic = flow.chapterVerifiedPageDiagnostic(),
        )
    }




}

@Composable
internal fun rememberSourceRouteOwner(
    application: TsuyomiApplication,
    navController: NavHostController,
    currentEntry: NavBackStackEntry?,
    currentRoute: String,
    onLibraryChanged: suspend () -> Unit,
): SourceRouteOwner {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installer = remember {
        SourceInstallController(context.applicationContext, application.libraryRepository)
    }
    val extensionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { document -> scope.launch { installer.prepare(document, context.contentResolver) } }
    }
    LaunchedEffect(installer) { installer.restoreInstalled() }

    val ownsSourceFlow = routeOwnsSourceFlow(currentRoute)
    val sourceFlowEntry = remember(currentEntry) {
        when {
            rootRouteFor(currentRoute) == Routes.Library ->
                runCatching { navController.getBackStackEntry(Routes.Library) }.getOrNull()
            ownsSourceFlow ->
                runCatching { navController.getBackStackEntry(Routes.Browse) }.getOrNull()
                    ?: runCatching { navController.getBackStackEntry(Routes.Library) }.getOrNull()
            else -> null
        }
    }
    val flow = remember(sourceFlowEntry) {
        SourceFlowController(
            context.applicationContext,
            application.libraryRepository,
            SourceFlowSnapshotStore(application.preferencesDataStore),
        )
    }
    DisposableEffect(flow) {
        onDispose(flow::close)
    }

    val currentOnLibraryChanged by rememberUpdatedState(onLibraryChanged)
    val owner = remember(installer, flow, navController) {
        SourceRouteOwner(
            installer = installer,
            flow = flow,
            navController = navController,
            requestImportAction = { extensionPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
            onLibraryChanged = { currentOnLibraryChanged() },
        )
    }
    return owner
}
