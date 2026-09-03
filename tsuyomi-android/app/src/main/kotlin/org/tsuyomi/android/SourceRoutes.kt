/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.feature.book.BookDetailScreen
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.feature.book.BookDirectoryScreen
import org.tsuyomi.feature.browse.BrowseInstalledSource
import org.tsuyomi.feature.browse.BrowseScreen
import org.tsuyomi.feature.browse.SourceHomeScreen
import org.tsuyomi.feature.browse.RemoteLibraryScreen
import org.tsuyomi.feature.reader.ReaderScreen
import org.tsuyomi.feature.search.SearchScreen
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.backup.PortableReaderPreferences

internal fun NavGraphBuilder.sourceRoutes(
    navController: NavHostController,
    owner: SourceRouteOwner,
    readerPreferences: PortableReaderPreferences,
    onReaderPreferencesChanged: (PortableReaderPreferences) -> Unit,
    coverRepository: CoverRepository?,
    packageRevision: String?,
    credentialRevision: String?,
) {
    browseRoute(navController, owner)
    sourceHomeRoute(navController, owner, coverRepository, packageRevision, credentialRevision)
    remoteLibraryRoute(navController, owner)
    searchRoute(
        navController = navController,
        owner = owner,
        coverRepository = coverRepository,
        packageRevision = packageRevision,
        credentialRevision = credentialRevision,
    )
    detailRoute(navController, owner, coverRepository, packageRevision, credentialRevision)
    directoryRoute(navController, owner)
    readerRoute(
        navController,
        owner,
        readerPreferences,
        onReaderPreferencesChanged,
        coverRepository,
        packageRevision,
        credentialRevision,
    )
    verificationRoutes(navController, owner)
}

private fun NavGraphBuilder.browseRoute(navController: NavHostController, owner: SourceRouteOwner) {
    composable(Routes.Browse) { entry ->
        val scope = rememberCoroutineScope()
        val packageInfo = owner.installer.activePackage
        val resumeSourceId by entry.savedStateHandle
            .getStateFlow(ResumeSourceIdKey, "")
            .collectAsStateWithLifecycle()
        val resumeRemoteBookId by entry.savedStateHandle
            .getStateFlow(ResumeRemoteBookIdKey, "")
            .collectAsStateWithLifecycle()
        LaunchedEffect(resumeSourceId, resumeRemoteBookId, packageInfo?.packageSha256) {
            if (resumeSourceId.isBlank() || resumeRemoteBookId.isBlank()) return@LaunchedEffect
            val identity = runCatching { BookIdentity(resumeSourceId, resumeRemoteBookId) }.getOrNull()
            val resumed = identity != null && owner.prepareScheduledResume(identity)
            entry.savedStateHandle[ResumeSourceIdKey] = ""
            entry.savedStateHandle[ResumeRemoteBookIdKey] = ""
            if (resumed) {
                navController.navigate(Routes.Reader)
            } else if (identity != null && owner.prepareScheduledDetail(identity)) {
                navController.navigate(Routes.Detail)
            }
        }
        BrowseScreen(
            state = owner.installer.state,
            installedSource = packageInfo?.let { verified ->
                BrowseInstalledSource(
                    sourceId = verified.manifest.sourceId.value,
                    name = verified.manifest.displayName,
                    version = verified.manifest.version.original,
                    summary = verified.manifest.summary,
                    homeAvailable = owner.sourceHomeAvailable,
                    remoteLibraryAvailable = owner.remoteLibraryAvailable,
                    verificationAvailable = verified.manifest.capabilities.webLogin.enabled,
                )
            },
            onRequestImport = owner::requestImport,
            onOpenHome = owner::navigateToSourceHome,
            onOpenInstalledSource = { scope.launch { owner.openInstalledSource() } },
            onOpenRemoteLibrary = { scope.launch { owner.openRemoteLibrary() } },
            onOpenVerification = owner::navigateToVerification,
            onApproveInstall = { allowDowngrade -> scope.launch { owner.installer.approve(allowDowngrade) } },
            onDismissApproval = owner.installer::dismissApproval,
            onDismissFailure = owner.installer::dismissFailure,
        )
    }
}

private fun NavGraphBuilder.sourceHomeRoute(
    navController: NavHostController,
    owner: SourceRouteOwner,
    coverRepository: CoverRepository?,
    packageRevision: String?,
    credentialRevision: String?,
) {
    composable(Routes.SourceHome) {
        val scope = rememberCoroutineScope()
        val flow = owner.flow
        val packageInfo = owner.installer.activePackage
        val activeRevision = packageInfo?.packageSha256
        val load: suspend (Map<String, String>, String?) -> Result<org.tsuyomi.shared.sourcecontract.SourceHomePage> =
            { filters, cursor ->
                if (packageInfo == null) {
                    Result.failure(IllegalStateException("source-not-installed"))
                } else {
                    flow.open(packageInfo)
                    flow.loadHome(filters, cursor)
                }
            }

        LaunchedEffect(activeRevision) {
            flow.home.ensureInitial(activeRevision, load)
        }
        BackHandler(
            enabled = (flow.homeState as? org.tsuyomi.feature.browse.SourceHomeViewState.Content)
                ?.featureOpen == true,
        ) {
            flow.home.navigateBackFromFeature()
        }


        SourceHomeScreen(
            sourceName = packageInfo?.manifest?.displayName.orEmpty(),
            state = flow.homeState,
            remoteLibraryAvailable = owner.remoteLibraryAvailable,
            verificationAvailable = packageInfo?.manifest?.capabilities?.webLogin?.enabled == true,
            onSelectPrimary = { value -> flow.home.selectPrimary(value, load) },
            onSelectFilters = { filters -> flow.home.selectFilters(filters, load) },
            onRefresh = { flow.home.refresh(load) },
            onLoadMore = { flow.home.append(load) },
            onRetryReplacement = { flow.home.retryReplacement(load) },
            onSearch = { scope.launch { owner.openInstalledSource() } },
            onOpenRemoteLibrary = { scope.launch { owner.openRemoteLibrary() } },
            onOpenBook = { book ->
                scope.launch {
                    owner.flow.prepareBook(book)
                    navController.navigate(Routes.Detail)
                }
            },
            onOpenFeature = { feature -> flow.home.openFeature(feature, load) },
            onOpenVerification = owner::navigateToVerification,
            onScrollPositionChanged = flow.home::updateScrollPosition,
            coverState = { book ->
                rememberSourceCoverState(
                    book = book,
                    repository = coverRepository,
                    packageRevision = packageRevision,
                    credentialRevision = credentialRevision,
                )
            },
        )
    }
}


private fun NavGraphBuilder.remoteLibraryRoute(navController: NavHostController, owner: SourceRouteOwner) {
    composable(Routes.RemoteLibrary) { entry ->
        val scope = rememberCoroutineScope()
        val remote = rememberSourceRemoteLibraryRouteOwner(
            entry = entry,
            flow = owner.flow,
            packageProvider = { owner.installer.activePackage },
        )
        val viewState = when (remote.status) {
            RemoteLibraryRouteStatus.Idle -> org.tsuyomi.feature.browse.RemoteLibraryViewState.IDLE
            RemoteLibraryRouteStatus.Loading -> org.tsuyomi.feature.browse.RemoteLibraryViewState.LOADING
            RemoteLibraryRouteStatus.Content -> org.tsuyomi.feature.browse.RemoteLibraryViewState.CONTENT
            RemoteLibraryRouteStatus.Empty -> org.tsuyomi.feature.browse.RemoteLibraryViewState.EMPTY
            RemoteLibraryRouteStatus.LoginRequired -> org.tsuyomi.feature.browse.RemoteLibraryViewState.LOGIN_REQUIRED
            RemoteLibraryRouteStatus.VerificationRequired -> org.tsuyomi.feature.browse.RemoteLibraryViewState.VERIFICATION_REQUIRED
            RemoteLibraryRouteStatus.Cancelled -> org.tsuyomi.feature.browse.RemoteLibraryViewState.CANCELLED
            is RemoteLibraryRouteStatus.Failure -> org.tsuyomi.feature.browse.RemoteLibraryViewState.ERROR
            is RemoteLibraryRouteStatus.Copied -> org.tsuyomi.feature.browse.RemoteLibraryViewState.COPIED
        }
        val message = when (val status = remote.status) {
            is RemoteLibraryRouteStatus.Failure -> status.safeCode
            is RemoteLibraryRouteStatus.Copied -> stringResource(
                R.string.remote_library_copy_result,
                status.total,
                status.added,
            )
            else -> null
        }
        RemoteLibraryScreen(
            sourceName = owner.installer.activePackage?.manifest?.displayName.orEmpty(),
            books = remote.books,
            selectedIds = remote.selectedIds,
            state = viewState,
            message = message,
            copyConfirmationVisible = remote.copyConfirmationVisible,
            onNavigateUp = { navController.navigateUp() },
            onRefresh = { scope.launch { remote.refresh() } },
            onToggleSelection = remote::toggleSelection,
            onClearSelection = remote::clearSelection,
            onRequestCopy = remote::requestCopy,
            onDismissCopy = remote::dismissCopy,
            onConfirmCopy = {
                scope.launch {
                    remote.confirmCopy()
                    owner.notifyLibraryChanged()
                }
            },
            onOpenVerification = owner::navigateToVerification,
            onOpenBook = { book ->
                scope.launch {
                    owner.flow.prepareBook(book)
                    navController.navigate(Routes.Detail)
                }
            },
        )
    }
}

private fun NavGraphBuilder.searchRoute(
    navController: NavHostController,
    owner: SourceRouteOwner,
    coverRepository: CoverRepository?,
    packageRevision: String?,
    credentialRevision: String?,
) {
    composable(Routes.Search) { entry ->
        val scope = rememberCoroutineScope()
        val search = rememberSourceSearchRouteOwner(entry, owner.flow)
        val packageInfo = owner.installer.activePackage
        LaunchedEffect(search, packageInfo?.packageSha256) {
            packageInfo?.let { search.restore(it) }
        }
        val layout by search.layout.collectAsStateWithLifecycle()
        val verifiedPageSequence by entry.savedStateHandle
            .getStateFlow(VerifiedSearchResultSequenceKey, 0L)
            .collectAsStateWithLifecycle()
        LaunchedEffect(verifiedPageSequence) {
            if (verifiedPageSequence > 0L) {
                search.acceptVerifiedPageResult()
                entry.savedStateHandle[VerifiedSearchResultSequenceKey] = 0L
            }
        }
        SearchScreen(
            query = search.query,
            state = search.state,
            layout = layout,
            onQueryChange = search::updateQuery,
            onSearch = { scope.launch { search.submit() } },
            onSelectBook = { book ->
                scope.launch {
                    search.select(book)
                    navController.navigate(Routes.Detail)
                }
            },
            onRetry = { scope.launch { search.submit() } },
            onUseOfflineCache = { scope.launch { search.submit(offlineOnly = true) } },
            onOpenVerification = { navController.navigate(Routes.VerifiedPage) },
            coverState = { book ->
                rememberSourceCoverState(
                    book = book,
                    repository = coverRepository,
                    packageRevision = packageRevision,
                    credentialRevision = credentialRevision,
                )
            },
        )
    }
}

@Composable
private fun rememberSourceCoverState(
    book: org.tsuyomi.shared.sourcecontract.SourceBookSummary,
    repository: CoverRepository?,
    packageRevision: String?,
    credentialRevision: String?,
): CoverUiState {
    val fallback = remember(book.title, book.identity.sourceId) {
        FallbackSpec(book.title, book.identity.sourceId)
    }
    val request = remember(book, packageRevision, credentialRevision) {
        val url = book.coverUrl?.takeIf(String::isNotBlank)
        if (url == null || packageRevision == null || credentialRevision == null) {
            null
        } else {
            CoverRequest(
                sourceId = book.identity.sourceId,
                packageRevision = packageRevision,
                credentialRevision = credentialRevision,
                transportUrl = url,
                referrerUrl = book.canonicalUrl,
                targetWidthPx = 240,
                targetHeightPx = 360,
                fallback = fallback,
            )
        }
    }
    if (repository == null || request == null) return CoverUiState.Fallback(fallback)
    val coverFlow = remember(repository, request) { repository.observe(request) }
    val state by coverFlow.collectAsStateWithLifecycle(
        initialValue = CoverUiState.Loading(fallback),
    )
    return state
}

private fun NavGraphBuilder.detailRoute(
    navController: NavHostController,
    owner: SourceRouteOwner,
    coverRepository: CoverRepository?,
    packageRevision: String?,
    credentialRevision: String?,
) {
    composable(Routes.Detail) { entry ->
        val scope = rememberCoroutineScope()
        val detail = rememberSourceDetailRouteOwner(entry, owner.flow, owner)
        val unreadOnly by detail.unreadOnly.collectAsStateWithLifecycle()
        val descending by detail.descending.collectAsStateWithLifecycle()
        val commandSequenceFlow = remember(entry) {
            entry.savedStateHandle.getStateFlow(SourceDetailRouteOwner.CommandSequenceKey, 0L)
        }
        val commandSequence by commandSequenceFlow.collectAsStateWithLifecycle()
        val packageInfo = owner.installer.activePackage
        val verifiedDetailSequence by entry.savedStateHandle
            .getStateFlow(VerifiedDetailResultSequenceKey, 0L)
            .collectAsStateWithLifecycle()
        val verifiedDirectorySequence by entry.savedStateHandle
            .getStateFlow(VerifiedDirectoryResultSequenceKey, 0L)
            .collectAsStateWithLifecycle()
        LaunchedEffect(detail, packageInfo?.packageSha256, verifiedDetailSequence, verifiedDirectorySequence) {
            if (verifiedDetailSequence > 0L) {
                detail.acceptVerifiedDetailResult()
                detail.resumeDirectoryAfterVerifiedDetail()
                entry.savedStateHandle[VerifiedDetailResultSequenceKey] = 0L
            }
            if (verifiedDirectorySequence > 0L) {
                detail.acceptVerifiedDirectoryResult()
                entry.savedStateHandle[VerifiedDirectoryResultSequenceKey] = 0L
            }
            if (verifiedDetailSequence == 0L && verifiedDirectorySequence == 0L) {
                if (detail.state !is SourceBookState.Content && detail.state !is SourceBookState.Failure) {
                    if (packageInfo != null) detail.restore(packageInfo) else detail.loadAll()
                }
            }
        }
        LaunchedEffect(detail, commandSequence) {
            if (commandSequence == 0L) return@LaunchedEffect
            val command = entry.savedStateHandle.remove<String>(SourceDetailRouteOwner.CommandKey)
                ?: return@LaunchedEffect
            detail.execute(command)
        }
        val summary = (detail.state as? SourceBookState.Content)?.value?.summary ?: detail.selectedBook
        val coverState = summary?.let {
            rememberSourceCoverState(
                book = it,
                repository = coverRepository,
                packageRevision = packageRevision,
                credentialRevision = credentialRevision,
            )
        } ?: CoverUiState.Fallback(FallbackSpec("书籍详情", "source"))
        fun openChapter(chapter: org.tsuyomi.shared.sourcecontract.SourceChapter) {
            scope.launch {
                detail.selectChapter(chapter)
                navController.navigate(Routes.Reader)
            }
        }
        BookDetailScreen(
            state = detail.state,
            directoryState = detail.directoryState,
            localState = detail.localState,
            mutation = detail.mutation,
            coverState = coverState,
            unreadOnly = unreadOnly,
            descending = descending,
            selectedChapterId = detail.selectedChapter?.chapterId,
            onSetRating = { rating -> scope.launch { detail.setRating(rating) } },
            onAddTag = { tag -> scope.launch { detail.addTag(tag) } },
            onToggleReadLater = { scope.launch { detail.toggleReadLater() } },
            onToggleUnreadOnly = detail::toggleUnreadOnly,
            onToggleOrder = detail::toggleOrder,
            onSelectChapter = ::openChapter,
            onContinueReading = ::openChapter,
            onAddToLibrary = {
                scope.launch { detail.execute(SourceDetailRouteOwner.Command.ADD_TO_LIBRARY.name) }
            },
            onRemoveFromLibrary = {
                scope.launch { detail.execute(SourceDetailRouteOwner.Command.REMOVE_FROM_LIBRARY.name) }
            },
            onOpenDirectory = { navController.navigate(Routes.Directory) },
            onRetry = { scope.launch { detail.loadAll() } },
            onUseOfflineCache = { scope.launch { detail.loadAll(offlineOnly = true) } },
            onOpenVerification = {
                navController.navigate(
                    if (detail.state is SourceBookState.Failure) {
                        Routes.VerifiedDetailPage
                    } else {
                        Routes.VerifiedDirectoryPage
                    },
                )
            },
        )
    }
}

private fun NavGraphBuilder.directoryRoute(navController: NavHostController, owner: SourceRouteOwner) {
    composable(Routes.Directory) { entry ->
        val scope = rememberCoroutineScope()
        val detail = rememberSourceDetailRouteOwner(entry, owner.flow, owner)
        val packageInfo = owner.installer.activePackage
        LaunchedEffect(detail, packageInfo?.packageSha256) {
            if (packageInfo != null) {
                detail.restore(packageInfo, SourceRestorationTarget.DIRECTORY)
            } else {
                detail.loadAll()
            }
        }
        BookDirectoryScreen(
            state = detail.directoryState,
            onSelectChapter = { chapter ->
                scope.launch {
                    detail.selectChapter(chapter)
                    navController.navigate(Routes.Reader)
                }
            },
            onRetry = { scope.launch { detail.loadAll() } },
            onUseOfflineCache = { scope.launch { detail.loadAll(offlineOnly = true) } },
            onOpenVerification = { navController.navigate(Routes.Verification) },
        )
    }
}

private fun NavGraphBuilder.readerRoute(
    navController: NavHostController,
    owner: SourceRouteOwner,
    readerPreferences: PortableReaderPreferences,
    onReaderPreferencesChanged: (PortableReaderPreferences) -> Unit,
    coverRepository: CoverRepository?,
    packageRevision: String?,
    credentialRevision: String?,
) {
    composable(Routes.Reader) { entry ->
        val scope = rememberCoroutineScope()
        val reader = rememberSourceReaderRouteOwner(entry, owner.flow)
        val verifiedChapterSequence by entry.savedStateHandle
            .getStateFlow(VerifiedChapterResultSequenceKey, 0L)
            .collectAsStateWithLifecycle()
        val packageInfo = owner.installer.activePackage
        LaunchedEffect(reader, packageInfo?.packageSha256) {
            packageInfo?.let { reader.restore(it) }
        }
        LaunchedEffect(reader.currentChapter?.chapterId, verifiedChapterSequence) {
            if (verifiedChapterSequence > 0L) {
                reader.acceptVerifiedChapterResult()
                entry.savedStateHandle[VerifiedChapterResultSequenceKey] = 0L
            } else if (reader.currentChapter != null && reader.document == null && reader.failure == null) {
                reader.load()
            }
        }
        ReaderScreen(
            document = reader.document,
            loading = reader.loading,
            failure = reader.failure,
            restoredLocator = reader.restoredLocator,
            chapters = reader.chapters,
            currentChapterId = reader.currentChapter?.chapterId.orEmpty(),
            onSelectChapter = { chapter -> scope.launch { reader.selectChapter(chapter) } },
            onNavigateUp = { navController.navigateUp() },
            imageStates = reader.imageStates,
            onImageVisible = { block ->
                reader.loadImage(block, coverRepository, packageRevision, credentialRevision, scope)
            },
            onRetryImage = { block ->
                reader.loadImage(block, coverRepository, packageRevision, credentialRevision, scope, retry = true)
            },
            onLocatorChanged = { locator, precision -> scope.launch { reader.saveProgress(locator, precision) } },
            preferences = readerPreferences,
            onPreferencesChanged = onReaderPreferencesChanged,
            onRetry = { scope.launch { reader.load(offlineOnly = false) } },
            onUseOfflineCache = { scope.launch { reader.load(offlineOnly = true) } },
            onOpenVerification = { navController.navigate(Routes.VerifiedChapterPage) },
        )
    }
}

private enum class VerifiedPageOperation { NONE, SEARCH, DETAIL, DIRECTORY, CHAPTER }

private fun NavGraphBuilder.verificationRoutes(navController: NavHostController, owner: SourceRouteOwner) {
    verificationRoute(navController, owner, Routes.Verification, VerifiedPageOperation.NONE)
    verificationRoute(navController, owner, Routes.VerifiedPage, VerifiedPageOperation.SEARCH)
    verificationRoute(navController, owner, Routes.VerifiedDetailPage, VerifiedPageOperation.DETAIL)
    verificationRoute(navController, owner, Routes.VerifiedDirectoryPage, VerifiedPageOperation.DIRECTORY)
    verificationRoute(navController, owner, Routes.VerifiedChapterPage, VerifiedPageOperation.CHAPTER)
}

private fun NavGraphBuilder.verificationRoute(
    navController: NavHostController,
    owner: SourceRouteOwner,
    route: String,
    operation: VerifiedPageOperation,
) {
    composable(route) {
        val scope = rememberCoroutineScope()
        owner.installer.activePackage?.let { packageInfo ->
            val requestUrl by produceState<String?>(
                initialValue = null,
                operation,
                owner.flow.query,
                owner.flow.selectedBook?.identity?.remoteBookId,
                packageInfo.packageSha256,
                owner.flow.selectedChapter?.chapterId,
                owner.flow.selectedChapter?.url,
            ) {
                value = when (operation) {
                    VerifiedPageOperation.NONE -> null
                    VerifiedPageOperation.SEARCH -> owner.searchVerifiedPageRequestUrl()
                    VerifiedPageOperation.DETAIL -> owner.detailVerifiedPageRequestUrl()
                    VerifiedPageOperation.DIRECTORY -> owner.directoryVerifiedPageRequestUrl()
                    VerifiedPageOperation.CHAPTER -> owner.chapterVerifiedPageRequestUrl()
                }
            }
            val openLabel = when (operation) {
                VerifiedPageOperation.NONE -> null
                VerifiedPageOperation.SEARCH -> stringResource(R.string.verification_open_requested_page)
                VerifiedPageOperation.DETAIL -> stringResource(R.string.verification_open_detail_page)
                VerifiedPageOperation.DIRECTORY -> stringResource(R.string.verification_open_directory_page)
                VerifiedPageOperation.CHAPTER -> stringResource(R.string.verification_open_chapter_page)
            }
            val unboundMessage = when (operation) {
                VerifiedPageOperation.NONE -> null
                VerifiedPageOperation.SEARCH -> stringResource(R.string.verification_snapshot_unbound)
                VerifiedPageOperation.DETAIL -> stringResource(R.string.verification_snapshot_unbound_detail)
                VerifiedPageOperation.DIRECTORY -> stringResource(R.string.verification_snapshot_unbound_directory)
                VerifiedPageOperation.CHAPTER -> stringResource(R.string.verification_snapshot_unbound_chapter)
            }
            ManualVerificationRoute(
                packageInfo = packageInfo,
                onCompleted = { scope.launch { owner.completeVerification() } },
                onVerifiedPageCompleted = { scope.launch { owner.completeVerifiedPage() } },
                onCancel = { navController.navigateUp() },
                verifiedPageRequestUrl = requestUrl,
                onUseVerifiedPage = when (operation) {
                    VerifiedPageOperation.NONE -> null
                    VerifiedPageOperation.SEARCH -> owner::useSearchVerifiedPage
                    VerifiedPageOperation.DETAIL -> owner::useDetailVerifiedPage
                    VerifiedPageOperation.DIRECTORY -> owner::useDirectoryVerifiedPage
                    VerifiedPageOperation.CHAPTER -> owner::useChapterVerifiedPage
                },
                verifiedPageOpenLabel = openLabel,
                verifiedPageUnboundMessage = unboundMessage,
            )
        }
    }
}
