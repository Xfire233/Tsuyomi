/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import org.tsuyomi.feature.library.LibraryDropDestination
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.RoomLibraryRepository
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.source.extensionmanager.RemoteOperation
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceException


internal const val VerifiedSearchResultSequenceKey = "source.search.verified-page-sequence"
internal const val VerifiedDetailResultSequenceKey = "source.detail.verified-page-sequence"
internal const val VerifiedDirectoryResultSequenceKey = "source.directory.verified-page-sequence"
internal const val VerifiedChapterResultSequenceKey = "source.chapter.verified-page-sequence"
internal const val ResumeSourceIdKey = "source.resume.source-id"
internal const val ResumeRemoteBookIdKey = "source.resume.remote-book-id"
internal const val RemoteBookMembershipKey = "source.detail.remote-book-membership"
internal const val UpdateFocusChapterIdKey = "updates.detail.focus-chapter-id"


internal enum class SourceHomeSwitchResult {
    CURRENT_SOURCE,
    OPENED_HOME,
    OPENED_SEARCH,
    UNAVAILABLE,
}



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
    private val library: RoomLibraryRepository,
    private val onLibraryChanged: suspend () -> Unit,
) {
    private val sourceHomeSwitchMutex = Mutex()

    val remoteLibraryAvailable: Boolean
        get() = installer.activePackage?.manifest?.capabilities?.remoteLibrary?.policies
            ?.containsKey(RemoteOperation.READ) == true
    val sourceHomeAvailable: Boolean
        get() = installer.activePackage?.manifest?.capabilities?.home?.enabled == true


    fun requestImport() {
        requestImportAction()
    }
    suspend fun navigateToSourceHome() {
        installer.activePackage?.let { packageInfo ->
            flow.open(packageInfo)
            navController.navigate(Routes.SourceHome)
        }
    }
    fun navigateToRemoteLibrary() {
        val sourceId = installer.activePackage?.manifest?.sourceId?.value ?: return
        navController.navigate(Routes.libraryMirror(sourceId))
    }
    fun navigateToVerification() {
        navController.navigate(Routes.Verification)
    }

    suspend fun refreshInstalledSources() {
        if (installer.mutationPending) return
        if (installer.catalog.state.status == org.tsuyomi.feature.browse.BrowseCatalogStatus.UNAVAILABLE) {
            installer.refreshInstalled()
        } else {
            installer.catalog.refresh()
        }
    }

    suspend fun uninstallSource(sourceId: String): Boolean = sourceHomeSwitchMutex.withLock {
        val removed = installer.uninstall(sourceId) {
            flow.removeSource(sourceId)
        }
        if (removed) {
            if (navController.currentDestination?.route != Routes.Browse) {
                navController.navigate(Routes.Browse) {
                    popUpTo(Routes.Browse) { inclusive = true }
                    launchSingleTop = true
                }
            }
            onLibraryChanged()
        }
        removed
    }


    suspend fun openInstalledSource() {
        installer.activePackage?.let { packageInfo ->
            flow.open(packageInfo)
            navController.navigate(Routes.Search)
        }
    }

    suspend fun switchSourceHome(sourceId: String): SourceHomeSwitchResult = sourceHomeSwitchMutex.withLock {
        val current = installer.activePackage ?: return@withLock SourceHomeSwitchResult.UNAVAILABLE
        if (current.manifest.sourceId.value == sourceId) return@withLock SourceHomeSwitchResult.CURRENT_SOURCE
        val stagedPackage = installer.resolveInstalledSource(sourceId)
            ?.takeIf { it.manifest.sourceId.value == sourceId }
            ?: return@withLock SourceHomeSwitchResult.UNAVAILABLE
        var prepared = prepareSourceSession(stagedPackage)
            ?: return@withLock SourceHomeSwitchResult.UNAVAILABLE
        try {
            if (!isStillOnSourceHome(current)) return@withLock SourceHomeSwitchResult.UNAVAILABLE

            val selected = installer.activateInstalledSource(sourceId)
                ?.takeIf {
                    it.manifest.sourceId.value == sourceId &&
                        installer.activePackage?.packageSha256 == it.packageSha256
                }
                ?: run {
                    restorePreviousSource(current)
                    return@withLock SourceHomeSwitchResult.UNAVAILABLE
                }
            if (selected.packageSha256 != prepared.packageInfo.packageSha256) {
                restorePreviousSource(current)
                return@withLock SourceHomeSwitchResult.UNAVAILABLE
            }
            if (!isStillOnSourceHome(selected)) {
                restorePreviousSource(current)
                return@withLock SourceHomeSwitchResult.UNAVAILABLE
            }
            if (!flow.commitPreparedSourceSession(prepared)) {
                restorePreviousSource(current)
                return@withLock SourceHomeSwitchResult.UNAVAILABLE
            }

            flow.commitSourceSwitch(selected)
            val destination = if (selected.manifest.capabilities.home.enabled) Routes.SourceHome else Routes.Search
            navController.navigate(destination) {
                popUpTo(Routes.SourceHome) { inclusive = true }
                launchSingleTop = true
            }
            if (destination == Routes.SourceHome) {
                SourceHomeSwitchResult.OPENED_HOME
            } else {
                SourceHomeSwitchResult.OPENED_SEARCH
            }
        } finally {
            flow.discardPreparedSourceSession(prepared)
        }
    }

    private suspend fun prepareSourceSession(packageInfo: VerifiedHxpPackage): PreparedSourceSession? = try {
        flow.prepareSourceSession(packageInfo)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SourceException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    private fun isStillOnSourceHome(packageInfo: VerifiedHxpPackage): Boolean =
        navController.currentDestination?.route == Routes.SourceHome &&
            installer.activePackage?.packageSha256 == packageInfo.packageSha256

    private suspend fun restorePreviousSource(current: VerifiedHxpPackage) {
        installer.activateInstalledSource(current.manifest.sourceId.value)
    }
    suspend fun refreshSourceHome() {
        if (installer.activePackage == null) return
        flow.home.refresh { filters, cursor ->
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
            flow.prepareLocalDetail(
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

    suspend fun openUpdateDetail(identity: BookIdentity, focusChapterId: String?): Boolean {
        return try {
            val book = resolveUpdateBook(identity) ?: return false
            val chapters = prepareUpdateDirectory(book, focusChapterId) ?: return false
            if (focusChapterId != null && chapters.none { it.chapterId == focusChapterId }) return false
            navController.navigate(Routes.Detail)
            focusChapterId?.let { chapterId ->
                navController.currentBackStackEntry?.savedStateHandle?.set(UpdateFocusChapterIdKey, chapterId)
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun openUpdateChapter(identity: BookIdentity, newChapterIds: List<String>): Boolean {
        return try {
            val book = resolveUpdateBook(identity) ?: return false
            val completed = library.completedChapterIds(identity)
            val targetChapterId = newChapterIds.firstOrNull { it !in completed } ?: return false
            val chapters = prepareUpdateDirectory(book, targetChapterId) ?: return false
            val target = chapters.firstOrNull { it.chapterId == targetChapterId } ?: return false
            flow.prepareChapter(target)
            navController.navigate(Routes.Reader)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun resolveUpdateBook(identity: BookIdentity): SourceBookSummary? {
        val book = library.book(identity)
            ?: library.remoteMirrorSnapshot(identity.sourceId)
                ?.books
                ?.firstOrNull { it.book.identity == identity }
                ?.book
            ?: return null
        val canonicalUrl = book.canonicalUrl?.takeIf(String::isNotBlank) ?: return null
        return SourceBookSummary(
            identity = identity,
            title = book.title,
            author = book.author,
            coverUrl = book.coverUrl,
            canonicalUrl = canonicalUrl,
        )
    }

    private suspend fun prepareUpdateDirectory(book: SourceBookSummary, exactChapterId: String?): List<SourceChapter>? {
        val packageInfo = installer.activateInstalledSource(book.identity.sourceId) ?: return null
        flow.open(packageInfo)
        val cacheReady = flow.prepareDetail(book.identity)
        val cachedChapters = (flow.directoryState as? SourceBookState.Content)?.value?.chapters.orEmpty()
        if (cacheReady && (exactChapterId == null || cachedChapters.any { it.chapterId == exactChapterId })) {
            return cachedChapters
        }
        flow.prepareLocalDetail(book)
        val refreshed = flow.requestDirectory()
        return (refreshed as? SourceBookState.Content)?.value?.chapters
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
            navController.navigate(Routes.libraryMirror(packageInfo.manifest.sourceId.value))
        }
    }

    suspend fun openRemoteDestination(entry: LibraryEntry, destination: LibraryDropDestination.RemoteMirror) {
        val packageInfo = installer.activePackage
            ?.takeIf { it.manifest.sourceId.value == entry.book.identity.sourceId }
            ?: return
        flow.open(packageInfo)
        flow.prepareBook(
            org.tsuyomi.shared.sourcecontract.SourceBookSummary(
                identity = entry.book.identity,
                title = entry.book.title,
                author = entry.book.author,
                coverUrl = entry.book.coverUrl,
                canonicalUrl = entry.book.canonicalUrl.orEmpty(),
            ),
        )
        navController.navigate(Routes.Detail)
        navController.currentBackStackEntry?.savedStateHandle?.apply {
            set(RemoteDestinationTargetIdKey, destination.targetId)
            set(RemoteDestinationRequestKey, (get<Long>(RemoteDestinationRequestKey) ?: 0L) + 1L)
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

    suspend fun homeVerifiedPageRequestUrl(): String? = flow.homeVerifiedPageRequestUrl()

    suspend fun useHomeVerifiedPage(snapshot: CapturedVerifiedPage): VerifiedPageUseResult {
        if (navController.previousBackStackEntry?.destination?.route != Routes.SourceHome) {
            return VerifiedPageUseResult(accepted = false)
        }
        val accepted = flow.homeVerifiedPage(snapshot)
        return VerifiedPageUseResult(
            accepted = accepted,
            diagnostic = (flow.homeState as? org.tsuyomi.feature.browse.SourceHomeViewState.Failure)?.let {
                SourceDiagnostic(
                    correlationId = "verified-home-rejected",
                    stage = "home-parse",
                    safeCode = it.safeCode,
                )
            },
        )
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
    currentRoute: String?,
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

    var retainedSourceFlowRoot by remember { mutableStateOf(Routes.Library) }
    val browseAnchored = runCatching { navController.getBackStackEntry(Routes.Browse) }.isSuccess
    val observedSourceFlowRoot = when {
        currentRoute == null -> null
        currentRoute == Routes.Browse -> Routes.Browse
        routeOwnsSourceFlow(currentRoute) -> if (
            browseAnchored || retainedSourceFlowRoot == Routes.Browse
        ) Routes.Browse else Routes.Library
        rootRouteFor(currentRoute) == Routes.Library -> if (
            browseAnchored && retainedSourceFlowRoot == Routes.Browse
        ) Routes.Browse else Routes.Library
        else -> null
    }
    val sourceFlowRoot = observedSourceFlowRoot ?: retainedSourceFlowRoot
    SideEffect {
        if (observedSourceFlowRoot != null && observedSourceFlowRoot != retainedSourceFlowRoot) {
            retainedSourceFlowRoot = observedSourceFlowRoot
        }
    }
    val flow = remember(sourceFlowRoot) {
        SourceFlowController(
            context.applicationContext,
            application.libraryRepository,
            SourceFlowSnapshotStore(application.preferencesDataStore),
        )
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(flow) {
        onDispose { mainHandler.post(flow::close) }
    }

    val currentOnLibraryChanged by rememberUpdatedState(onLibraryChanged)
    val owner = remember(installer, flow, navController) {
        SourceRouteOwner(
            installer = installer,
            flow = flow,
            navController = navController,
            requestImportAction = { extensionPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
            library = application.libraryRepository,
            onLibraryChanged = { currentOnLibraryChanged() },
        )
    }
    return owner
}

internal const val RemoteDestinationRequestKey = "remote-destination-request"
internal const val RemoteDestinationTargetIdKey = "remote-destination-target-id"
internal const val RemoteRemoveRequestKey = "remote-remove-request"
internal const val RemoteMoveRequestKey = "remote-move-request"
