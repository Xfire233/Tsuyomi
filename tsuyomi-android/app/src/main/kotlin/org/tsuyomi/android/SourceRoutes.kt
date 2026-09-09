/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.feature.book.BookDetailScreen
import org.tsuyomi.feature.book.BookDestinationMenu
import org.tsuyomi.feature.book.DetailCollectionDestination
import org.tsuyomi.feature.book.SourceBookState
import org.tsuyomi.feature.book.BookDirectoryScreen
import org.tsuyomi.feature.browse.BrowseInstalledSource
import org.tsuyomi.feature.browse.BrowseScreen
import org.tsuyomi.feature.browse.SourceHomeScreen
import org.tsuyomi.feature.library.RemoteLibraryScreen
import org.tsuyomi.feature.reader.ReaderScreen
import org.tsuyomi.feature.library.RemoteLibraryViewState
import org.tsuyomi.feature.search.SearchScreen
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.backup.PortableReaderPreferences

internal fun NavGraphBuilder.sourceRoutes(
    navController: NavHostController,
    owner: SourceRouteOwner,
    libraryFlow: LibraryFlowController,
    readerPreferences: PortableReaderPreferences,
    onReaderPreferencesChanged: (PortableReaderPreferences) -> Unit,
    coverRepository: CoverRepository?,
    packageRevision: String?,
    credentialRevision: String?,
    onExactChapterCompleted: suspend (BookIdentity) -> Unit,
) {
    browseRoute(navController, owner)
    sourceHomeRoute(navController, owner, coverRepository, packageRevision, credentialRevision)
    remoteLibraryRoute(navController, owner, libraryFlow, coverRepository, packageRevision, credentialRevision)
    searchRoute(
        navController = navController,
        owner = owner,
        coverRepository = coverRepository,
        packageRevision = packageRevision,
        credentialRevision = credentialRevision,
    )
    detailRoute(navController, owner, libraryFlow, coverRepository, packageRevision, credentialRevision)
    directoryRoute(navController, owner)
    readerRoute(
        navController,
        owner,
        readerPreferences,
        onReaderPreferencesChanged,
        coverRepository,
        packageRevision,
        credentialRevision,
        onExactChapterCompleted,
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
        fun loader(offlineOnly: Boolean): suspend (Map<String, String>, String?) -> Result<org.tsuyomi.shared.sourcecontract.SourceHomePage> =
            { filters, cursor ->
                if (packageInfo == null) {
                    Result.failure(IllegalStateException("source-not-installed"))
                } else {
                    flow.open(packageInfo)
                    flow.loadHome(filters, cursor, offlineOnly)
                }
            }
        val load = loader(offlineOnly = false)
        val loadOffline = loader(offlineOnly = true)

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
            onUseOfflineCache = { flow.home.useOfflineCache(loadOffline) },
            onSearch = { scope.launch { owner.openInstalledSource() } },
            onOpenRemoteLibrary = { scope.launch { owner.openRemoteLibrary() } },
            onOpenBook = { book ->
                scope.launch {
                    owner.flow.prepareBook(book)
                    navController.navigate(Routes.Detail)
                }
            },
            onOpenFeature = { feature -> flow.home.openFeature(feature, load) },
            onOpenVerification = { navController.navigate(Routes.VerifiedHomePage) },
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


private fun NavGraphBuilder.remoteLibraryRoute(
    navController: NavHostController,
    owner: SourceRouteOwner,
    libraryFlow: LibraryFlowController,
    coverRepository: CoverRepository?,
    packageRevision: String?,
    credentialRevision: String?,
) {
    listOf(Routes.RemoteLibrary, Routes.LibraryMirror, Routes.LibraryMirrorFolder).forEach { routePattern ->
        composable(routePattern) { entry ->
        val scope = rememberCoroutineScope()
        val mirrorBindingId = entry.arguments?.getString("bindingId")
        val mirrorTargetId = entry.arguments?.getString("targetId")
        val remote = rememberSourceRemoteLibraryRouteOwner(
            entry = entry,
            flow = owner.flow,
            packageProvider = {
                owner.installer.activePackage
                    ?.takeIf { mirrorBindingId == null || it.manifest.sourceId.value == mirrorBindingId }
            },
        )
        val groupingEnabled = mirrorBindingId?.let(libraryFlow::isWebsiteGroupingEnabled) == true
        val visibleTargetId = mirrorTargetId.takeIf { groupingEnabled }
        val activePackage = owner.installer.activePackage
        LaunchedEffect(remote, mirrorBindingId, visibleTargetId, activePackage) {
            activePackage
                ?.takeIf { mirrorBindingId == null || it.manifest.sourceId.value == mirrorBindingId }
                ?.let { owner.flow.open(it) }
            mirrorBindingId?.let {
                remote.restore(it)
                remote.selectTarget(visibleTargetId)
            }
        }
        val viewState = when (remote.status) {
            RemoteLibraryRouteStatus.Idle -> RemoteLibraryViewState.IDLE
            RemoteLibraryRouteStatus.Loading -> RemoteLibraryViewState.LOADING
            RemoteLibraryRouteStatus.Content -> RemoteLibraryViewState.CONTENT
            RemoteLibraryRouteStatus.Empty -> RemoteLibraryViewState.EMPTY
            RemoteLibraryRouteStatus.LoginRequired -> RemoteLibraryViewState.LOGIN_REQUIRED
            RemoteLibraryRouteStatus.VerificationRequired -> RemoteLibraryViewState.VERIFICATION_REQUIRED
            RemoteLibraryRouteStatus.Cancelled -> RemoteLibraryViewState.CANCELLED
            is RemoteLibraryRouteStatus.Failure -> RemoteLibraryViewState.ERROR
            is RemoteLibraryRouteStatus.Copied -> RemoteLibraryViewState.COPIED
            is RemoteLibraryRouteStatus.Mutation ->
                if (remote.books.isEmpty()) RemoteLibraryViewState.EMPTY else RemoteLibraryViewState.CONTENT
        }
        val message = when (val status = remote.status) {
            is RemoteLibraryRouteStatus.Failure -> status.safeCode
            is RemoteLibraryRouteStatus.Copied -> stringResource(
                R.string.remote_library_copy_result,
                status.total,
                status.added,
            )
            is RemoteLibraryRouteStatus.Mutation -> {
                val operation = if (status.operation == "remove") "从网站收藏移除" else "移动网站收藏"
                when (val result = status.result) {
                    RemoteMutationUiResult.Confirmed -> status.targetName?.let { "已${operation}至「$it」" } ?: "已完成${operation}"
                    RemoteMutationUiResult.Unresolved -> "${operation}结果待确认"
                    RemoteMutationUiResult.Cancelled -> "${operation}已取消"
                    is RemoteMutationUiResult.Failure -> "${operation}失败：${result.safeCode}"
                }
            }
            else -> null
        }
        val pinFailureMessage = stringResource(R.string.library_read_failure_safe)
        RemoteLibraryScreen(
            sourceId = mirrorBindingId ?: owner.installer.activePackage?.manifest?.sourceId?.value.orEmpty(),
            sourceName = owner.installer.activePackage?.manifest?.displayName.orEmpty(),
            books = if (visibleTargetId == null) remote.books else remote.visibleBooks,
            selectedIds = remote.selectedIds,
            state = viewState,
            message = message,
            copyConfirmationVisible = remote.copyConfirmationVisible,
            onNavigateUp = { navController.navigateUp() },
            onRefresh = {
                scope.launch {
                    remote.refresh()
                    libraryFlow.reload(pinFailureMessage)
                }
            },
            onToggleSelection = remote::toggleSelection,
            onClearSelection = remote::clearSelection,
            onRequestCopy = {
                scope.launch {
                    if (remote.requestCopy() != null) owner.notifyLibraryChanged()
                }
            },
            onDismissCopy = remote::dismissCopy,
            onConfirmCopy = {
                scope.launch {
                    if (remote.confirmCopy() != null) owner.notifyLibraryChanged()
                }
            },
            onOpenVerification = owner::navigateToVerification,
            onOpenBook = { book ->
                scope.launch {
                    owner.flow.prepareBook(book)
                    navController.navigate(Routes.Detail)
                }
            },
            onOpenTarget = { targetId ->
                if (groupingEnabled) navController.navigate(Routes.libraryMirrorFolder(mirrorBindingId.orEmpty(), targetId))
            },
            groupingEnabled = groupingEnabled,
            onGroupingEnabledChange = { enabled ->
                mirrorBindingId?.let { sourceId ->
                    scope.launch {
                        libraryFlow.setWebsiteGroupingEnabled(sourceId, enabled, pinFailureMessage)
                        if (!enabled) remote.selectTarget(null)
                    }
                }
            },
            targets = remote.targets,
            selectedTargetId = remote.selectedTargetId,
            onSelectTarget = remote::selectTarget,
            unresolvedBookIds = remote.unresolvedBookIds,
            removeConfirmationBook = remote.removeConfirmationBook,
            onDismissRemoveConfirmation = remote::dismissRemove,
            onConfirmRemove = { book ->
                scope.launch {
                    remote.confirmRemove(book)
                    owner.notifyLibraryChanged()
                }
            },
            onRequestRemoveBook = { book -> scope.launch { remote.requestRemove(book) } },
            onRequestMoveBook = { book ->
                if (groupingEnabled) scope.launch { remote.requestMove(book) }
            },
            moveTargetSelectionBook = remote.moveTargetSelectionBook,
            onDismissMoveSelection = remote::dismissMove,
            onConfirmMove = { book, targetId, targetName ->
                scope.launch {
                    remote.confirmMove(book, targetId, targetName)
                    owner.notifyLibraryChanged()
                }
            },
            jitPrompt = remote.jitPrompt,
            onConfirmJitPrompt = { scope.launch { remote.confirmJitPrompt() } },
            onDismissJitPrompt = remote::dismissJitPrompt,
            coverState = { book ->
                rememberSourceCoverState(book, coverRepository, packageRevision, credentialRevision)
            },
        )
        }
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
            authorSearch = search.authorSearch,
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
    libraryFlow: LibraryFlowController,
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
        val remoteDestinationRequest by remember(entry) {
            entry.savedStateHandle.getStateFlow(RemoteDestinationRequestKey, 0L)
        }.collectAsStateWithLifecycle()
        val remoteRemoveRequest by remember(entry) {
            entry.savedStateHandle.getStateFlow(RemoteRemoveRequestKey, 0L)
        }.collectAsStateWithLifecycle()
        val remoteMoveRequest by remember(entry) {
            entry.savedStateHandle.getStateFlow(RemoteMoveRequestKey, 0L)
        }.collectAsStateWithLifecycle()
        val updateFocusChapterId by remember(entry) {
            entry.savedStateHandle.getStateFlow<String?>(UpdateFocusChapterIdKey, null)
        }.collectAsStateWithLifecycle()
        val summary = (detail.state as? SourceBookState.Content)?.value?.summary ?: detail.selectedBook
        val websiteGroupingEnabled = summary?.identity?.sourceId?.let(libraryFlow::isWebsiteGroupingEnabled) == true
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
        var destinationMenuExpanded by remember { mutableStateOf(false) }
        var destinationTargets by remember { mutableStateOf<List<org.tsuyomi.shared.sourcecontract.RemoteTarget>>(emptyList()) }
        var loadingDestinationTargets by remember { mutableStateOf(false) }
        var selectedDestinationCollections by remember { mutableStateOf<Set<String>>(emptySet()) }
        var remoteRemoveConfirmationVisible by remember { mutableStateOf(false) }
        var remoteMoveTargetSelectionVisible by remember { mutableStateOf(false) }
        var pendingRemoteMoveOnly by remember { mutableStateOf(false) }
        var selectedWebsiteTargetId by remember { mutableStateOf<String?>(null) }
        var destinationMessage by remember { mutableStateOf<String?>(null) }
        var pendingWebsiteAuthorizationOperation by remember { mutableStateOf<String?>(null) }
        var pendingWebsiteTargetId by remember { mutableStateOf<String?>(null) }
        var partialMoveTargetName by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(summary?.identity) {
            val identity = summary?.identity
            if (identity == null) {
                entry.savedStateHandle[RemoteBookMembershipKey] = false
                return@LaunchedEffect
            }
            entry.savedStateHandle[RemoteBookMembershipKey] = owner.flow.remoteMirrorSnapshot(identity.sourceId)
                ?.books
                ?.any { it.book.identity == identity } == true
            val record = owner.flow.remoteReconciliation(identity) ?: return@LaunchedEffect
            val pendingTarget = record.targetName ?: record.targetId
            val continuationPending = pendingTarget != null && when (record.operation.uppercase()) {
                "ADD" -> record.state == RemoteReconciliationState.CONFIRMED
                "MOVE" -> record.state in setOf(
                    RemoteReconciliationState.CANCELLED,
                    RemoteReconciliationState.UNRESOLVED,
                )
                else -> false
            }
            if (continuationPending) {
                partialMoveTargetName = pendingTarget
                destinationMessage = if (record.state == RemoteReconciliationState.UNRESOLVED) {
                    "移动结果待确认，可安全重试目标移动"
                } else {
                    "已加入默认书架，目标移动尚未完成"
                }
            }
        }
        LaunchedEffect(remoteRemoveRequest) {
            if (remoteRemoveRequest <= 0L) return@LaunchedEffect
            remoteRemoveConfirmationVisible = true
            entry.savedStateHandle[RemoteRemoveRequestKey] = 0L
        }
        LaunchedEffect(remoteMoveRequest) {
            if (remoteMoveRequest <= 0L) return@LaunchedEffect
            destinationTargets = owner.flow.listRemoteTargets()
            if (destinationTargets.isEmpty()) {
                destinationMessage = "网站目标不可用"
            } else {
                remoteMoveTargetSelectionVisible = true
            }
            entry.savedStateHandle[RemoteMoveRequestKey] = 0L
        }
        val localDestinationFailure = stringResource(R.string.library_read_failure_safe)
        suspend fun selectedManualDestinations(identity: org.tsuyomi.shared.model.BookIdentity): Set<String> {
            val manualIds = libraryFlow.collections.asSequence()
                .filter { it.kind == org.tsuyomi.core.database.CollectionKind.MANUAL }
                .mapTo(hashSetOf()) { it.collectionId }
            return libraryFlow.manualCollectionIds(identity).filterTo(linkedSetOf()) { it in manualIds }
        }
        suspend fun applyLocalDestinations(
            book: org.tsuyomi.shared.sourcecontract.SourceBookSummary,
            collectionIds: Set<String>,
        ) {
            if (!detail.localState.inLibrary) {
                detail.execute(SourceDetailRouteOwner.Command.ADD_TO_LIBRARY.name)
                libraryFlow.reload(localDestinationFailure)
            }
            val applied = libraryFlow.applyBookDestinations(
                book.identity,
                collectionIds,
                localDestinationFailure,
            )
            destinationMessage = if (applied) "本地位置已更新" else localDestinationFailure
        }
        fun selectedWebsiteTarget(targetId: String?): org.tsuyomi.shared.sourcecontract.RemoteTarget? =
            if (websiteGroupingEnabled) {
                destinationTargets.firstOrNull { it.targetId == targetId }
            } else {
                defaultRemoteTarget(destinationTargets)
            }
        suspend fun applyWebsiteDestination(
            book: org.tsuyomi.shared.sourcecontract.SourceBookSummary,
            targetId: String?,
        ) {
            partialMoveTargetName = null
            val target = selectedWebsiteTarget(targetId)
            val defaultTarget = defaultRemoteTarget(destinationTargets)
            if (target == null || defaultTarget == null) {
                destinationMessage = "网站目标不可用"
                return
            }
            when (val result = owner.flow.addBookToWebsiteTarget(
                book,
                target.targetId,
                target.displayName,
                defaultTarget.targetId,
            )) {
                RemoteTargetedAddResult.Confirmed -> {
                    destinationMessage = if (websiteGroupingEnabled) "已加入${target.displayName}" else "已加入网站收藏"
                    partialMoveTargetName = null
                    entry.savedStateHandle[RemoteBookMembershipKey] = true
                }
                is RemoteTargetedAddResult.Partial -> {
                    destinationMessage = "已加入默认书架，移动尚未完成"
                    partialMoveTargetName = result.requestedTargetName
                    entry.savedStateHandle[RemoteBookMembershipKey] = true
                }
                RemoteTargetedAddResult.Unresolved -> destinationMessage = "网站结果待确认"
                RemoteTargetedAddResult.Cancelled -> destinationMessage = "操作已取消"
                is RemoteTargetedAddResult.Failure -> destinationMessage = result.safeCode
            }
            owner.notifyLibraryChanged()
        }
        suspend fun missingWebsiteAuthorization(
            book: org.tsuyomi.shared.sourcecontract.SourceBookSummary,
            targetId: String?,
        ): String? {
            if (!owner.flow.writebackAuthorized(book.identity.sourceId, "add")) return "add"
            val target = selectedWebsiteTarget(targetId)
            val defaultTarget = defaultRemoteTarget(destinationTargets)
            return if (
                websiteGroupingEnabled && target != null && defaultTarget != null && target.targetId != defaultTarget.targetId &&
                !owner.flow.writebackAuthorized(book.identity.sourceId, "move")
            ) "move" else null
        }
        LaunchedEffect(remoteDestinationRequest) {
            if (remoteDestinationRequest <= 0L) return@LaunchedEffect
            destinationMenuExpanded = true
            selectedDestinationCollections = summary?.identity?.let { selectedManualDestinations(it) }.orEmpty()
            loadingDestinationTargets = true
            val requestedTargetId = entry.savedStateHandle.remove<String>(RemoteDestinationTargetIdKey)
            destinationTargets = owner.flow.listRemoteTargets()
            selectedWebsiteTargetId = if (websiteGroupingEnabled) {
                requestedTargetId?.takeIf { requested -> destinationTargets.any { it.targetId == requested } }
                    ?: defaultRemoteTarget(destinationTargets)?.targetId
            } else {
                defaultRemoteTarget(destinationTargets)?.targetId
            }
            loadingDestinationTargets = false
            entry.savedStateHandle[RemoteDestinationRequestKey] = 0L
        }
        BookDetailScreen(
            state = detail.state,
            directoryState = detail.directoryState,
            localState = detail.localState,
            mutation = detail.mutation,
            coverState = coverState,
            unreadOnly = unreadOnly,
            descending = descending,
            focusChapterId = updateFocusChapterId,
            onFocusHandled = { entry.savedStateHandle[UpdateFocusChapterIdKey] = null },
            selectedChapterId = detail.selectedChapter?.chapterId,
            onSetRating = { rating -> scope.launch { detail.setRating(rating) } },
            onSearchAuthor = { author ->
                navController.navigate(Routes.Search)
                val searchEntry = requireNotNull(navController.currentBackStackEntry)
                searchEntry.lifecycleScope.launch {
                    sourceSearchRouteOwner(searchEntry, owner.flow).submitAuthor(author)
                }
            },
            onAddTag = { tag -> scope.launch { detail.addTag(tag) } },
            onToggleUnreadOnly = detail::toggleUnreadOnly,
            onToggleOrder = detail::toggleOrder,
            onSelectChapter = ::openChapter,
            onContinueReading = ::openChapter,
            onAddToLibrary = {
                scope.launch { detail.execute(SourceDetailRouteOwner.Command.ADD_TO_LIBRARY.name) }
            },
            onOpenDestinations = {
                loadingDestinationTargets = true
                scope.launch {
                    selectedDestinationCollections = summary?.identity?.let { selectedManualDestinations(it) }.orEmpty()
                    destinationTargets = owner.flow.listRemoteTargets()
                    selectedWebsiteTargetId = defaultRemoteTarget(destinationTargets)?.targetId
                    loadingDestinationTargets = false
                }
            },
            destinationMenuExpanded = destinationMenuExpanded,
            onDestinationMenuExpandedChange = { destinationMenuExpanded = it },
            destinationMenuContent = { dismissMenu ->
                BookDestinationMenu(
                    readLater = detail.localState.readLater,
                    collections = libraryFlow.collections
                        .filter { it.kind == org.tsuyomi.core.database.CollectionKind.MANUAL }
                        .map {
                            DetailCollectionDestination(
                                it.collectionId,
                                it.title,
                                it.collectionId in selectedDestinationCollections,
                            )
                        },
                    remoteTargets = destinationTargets,
                    selectedRemoteTargetId = selectedWebsiteTargetId,
                    loadingRemoteTargets = loadingDestinationTargets,
                    websiteGroupingEnabled = websiteGroupingEnabled,
                    onToggleReadLater = { scope.launch { detail.toggleReadLater() } },
                    onToggleCollection = { id ->
                        val nextCollections = if (id in selectedDestinationCollections) {
                            selectedDestinationCollections - id
                        } else {
                            selectedDestinationCollections + id
                        }
                        selectedDestinationCollections = nextCollections
                        summary?.let { book ->
                            scope.launch {
                                applyLocalDestinations(book, nextCollections)
                            }
                        }
                    },
                    onApplyWebsite = { targetId ->
                        selectedWebsiteTargetId = targetId
                        pendingWebsiteTargetId = targetId
                        summary?.let { book ->
                            scope.launch {
                                val missingOperation = missingWebsiteAuthorization(book, targetId)
                                if (missingOperation != null) {
                                    pendingWebsiteAuthorizationOperation = missingOperation
                                } else {
                                    applyWebsiteDestination(book, targetId)
                                }
                            }
                        }
                    },
                    onDismiss = dismissMenu,
                )
            },
            destinationMessage = destinationMessage,
            partialMoveTargetName = partialMoveTargetName.takeIf { websiteGroupingEnabled },
            onRetryMoveOnly = {
                summary?.let { book ->
                    scope.launch {
                        when (owner.flow.retryRemoteMutation(book)) {
                            RemoteMutationUiResult.Confirmed -> {
                                destinationMessage = "已完成移动"
                                partialMoveTargetName = null
                            }
                            RemoteMutationUiResult.Unresolved -> destinationMessage = "移动结果仍待确认"
                            RemoteMutationUiResult.Cancelled -> destinationMessage = "移动未执行，可再次重试"
                            is RemoteMutationUiResult.Failure -> destinationMessage = "移动重试失败"
                        }
                    }
                }
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

            onRetryRemoteReconciliation = {
                scope.launch {
                    detail.retryRemoteReconciliation()
                    owner.notifyLibraryChanged()
                }
            },
            onAcknowledgeRemoteReconciliation = {
                scope.launch {
                    detail.acknowledgeRemoteReconciliation()
                    owner.notifyLibraryChanged()
                }
            },
        )
        val pendingOperation = pendingWebsiteAuthorizationOperation
        if (pendingOperation != null && summary != null) {
            val operationLabel = pendingOperation.uppercase()
            AlertDialog(
                onDismissRequest = {
                    pendingWebsiteAuthorizationOperation = null
                    pendingWebsiteTargetId = null
                    pendingRemoteMoveOnly = false
                },
                title = { Text(if (pendingOperation == "add") "授权加入网站书架" else "授权移动网站书籍") },
                text = {
                    Text(
                        "Tsuyomi 将代表您对《${summary.title}》执行 $operationLabel。此授权仅用于 $operationLabel；后续每次操作仍会重新检查来源、目标并签发单次令牌。",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingWebsiteAuthorizationOperation = null
                        scope.launch {
                            if (!owner.flow.authorizeWriteback(summary.identity.sourceId, pendingOperation, true)) {
                                destinationMessage = "$operationLabel 授权未能保存"
                                return@launch
                            }
                            if (pendingRemoteMoveOnly) {
                                val target = destinationTargets.firstOrNull { it.targetId == pendingWebsiteTargetId }
                                pendingRemoteMoveOnly = false
                                pendingWebsiteTargetId = null
                                if (target == null) {
                                    destinationMessage = "网站目标不可用"
                                } else {
                                    detail.moveSelectedBookOnWebsite(target.targetId, target.displayName)
                                    owner.notifyLibraryChanged()
                                }
                            } else {
                                val nextOperation = missingWebsiteAuthorization(summary, pendingWebsiteTargetId)
                                if (nextOperation != null) {
                                    pendingWebsiteAuthorizationOperation = nextOperation
                                } else {
                                    applyWebsiteDestination(summary, pendingWebsiteTargetId)
                                    pendingWebsiteTargetId = null
                                }
                            }
                        }
                    }) { Text("授权") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingWebsiteAuthorizationOperation = null
                        pendingWebsiteTargetId = null
                        pendingRemoteMoveOnly = false
                    }) { Text("取消") }
                },
            )
        }
        if (remoteMoveTargetSelectionVisible && summary != null) {
            AlertDialog(
                onDismissRequest = { remoteMoveTargetSelectionVisible = false },
                title = { Text("移动网站收藏") },
                text = {
                    Column {
                        destinationTargets.forEach { target ->
                            TextButton(onClick = {
                                remoteMoveTargetSelectionVisible = false
                                pendingWebsiteTargetId = target.targetId
                                pendingRemoteMoveOnly = true
                                scope.launch {
                                    if (!owner.flow.writebackAuthorized(summary.identity.sourceId, "move")) {
                                        pendingWebsiteAuthorizationOperation = "move"
                                    } else {
                                        pendingRemoteMoveOnly = false
                                        pendingWebsiteTargetId = null
                                        detail.moveSelectedBookOnWebsite(target.targetId, target.displayName)
                                        owner.notifyLibraryChanged()
                                    }
                                }
                            }) { Text(target.displayName) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { remoteMoveTargetSelectionVisible = false }) { Text("取消") }
                },
            )
        }
        if (remoteRemoveConfirmationVisible && summary != null) {
            AlertDialog(
                onDismissRequest = { remoteRemoveConfirmationVisible = false },
                title = { Text("从网站书架移除") },
                text = { Text("将从当前网站书架移除《${summary.title}》。本地书架与本地阅读数据不会被删除。") },
                confirmButton = {
                    TextButton(onClick = {
                        remoteRemoveConfirmationVisible = false
                        scope.launch {
                            val sourceId = summary.identity.sourceId
                            val admitted = owner.flow.writebackAuthorized(sourceId, "remove") ||
                                owner.flow.authorizeWriteback(sourceId, "remove", true)
                            if (admitted) {
                                detail.removeSelectedBookFromWebsite()
                                owner.notifyLibraryChanged()
                                if (detail.mutation?.phase == org.tsuyomi.feature.book.DetailMutationPhase.SUCCESS) {
                                    entry.savedStateHandle[RemoteBookMembershipKey] = false
                                }
                            }
                        }
                    }) { Text("确认移除") }
                },
                dismissButton = {
                    TextButton(onClick = { remoteRemoveConfirmationVisible = false }) { Text("取消") }
                },
            )
        }
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
    onExactChapterCompleted: suspend (BookIdentity) -> Unit,
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
            onChapterCompleted = { chapterId ->
                val activeDocument = reader.document ?: return@ReaderScreen
                val identity = BookIdentity(activeDocument.sourceId, activeDocument.remoteBookId)
                scope.launch {
                    owner.flow.markChapterCompleted(identity, chapterId)
                    onExactChapterCompleted(identity)
                }
            },
            preferences = readerPreferences,
            onPreferencesChanged = onReaderPreferencesChanged,
            onRetry = { scope.launch { reader.load(offlineOnly = false) } },
            onUseOfflineCache = { scope.launch { reader.load(offlineOnly = true) } },
            onOpenVerification = { navController.navigate(Routes.VerifiedChapterPage) },
        )
    }
}

private enum class VerifiedPageOperation { NONE, HOME, SEARCH, DETAIL, DIRECTORY, CHAPTER }

private data class VerificationPageRequest(
    val resolved: Boolean,
    val url: String?,
)

private fun NavGraphBuilder.verificationRoutes(navController: NavHostController, owner: SourceRouteOwner) {
    verificationRoute(navController, owner, Routes.Verification, VerifiedPageOperation.NONE)
    verificationRoute(navController, owner, Routes.VerifiedHomePage, VerifiedPageOperation.HOME)
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
            val pageRequest by produceState(
                initialValue = VerificationPageRequest(
                    resolved = operation == VerifiedPageOperation.NONE,
                    url = null,
                ),
                operation,
                owner.flow.query,
                owner.flow.selectedBook?.identity?.remoteBookId,
                owner.flow.home.selectedFilters,
                packageInfo.packageSha256,
                owner.flow.selectedChapter?.chapterId,
                owner.flow.selectedChapter?.url,
            ) {
                value = VerificationPageRequest(
                    resolved = true,
                    url = when (operation) {
                        VerifiedPageOperation.NONE -> null
                        VerifiedPageOperation.HOME -> owner.homeVerifiedPageRequestUrl()
                        VerifiedPageOperation.SEARCH -> owner.searchVerifiedPageRequestUrl()
                        VerifiedPageOperation.DETAIL -> owner.detailVerifiedPageRequestUrl()
                        VerifiedPageOperation.DIRECTORY -> owner.directoryVerifiedPageRequestUrl()
                        VerifiedPageOperation.CHAPTER -> owner.chapterVerifiedPageRequestUrl()
                    },
                )
            }
            val openLabel = when (operation) {
                VerifiedPageOperation.NONE -> null
                VerifiedPageOperation.SEARCH -> stringResource(R.string.verification_open_requested_page)
                VerifiedPageOperation.HOME -> stringResource(R.string.verification_open_home_page)
                VerifiedPageOperation.DETAIL -> stringResource(R.string.verification_open_detail_page)
                VerifiedPageOperation.DIRECTORY -> stringResource(R.string.verification_open_directory_page)
                VerifiedPageOperation.CHAPTER -> stringResource(R.string.verification_open_chapter_page)
            }
            val unboundMessage = when (operation) {
                VerifiedPageOperation.NONE -> null
                VerifiedPageOperation.SEARCH -> stringResource(R.string.verification_snapshot_unbound)
                VerifiedPageOperation.DETAIL -> stringResource(R.string.verification_snapshot_unbound_detail)
                VerifiedPageOperation.HOME -> stringResource(R.string.verification_snapshot_unbound)
                VerifiedPageOperation.DIRECTORY -> stringResource(R.string.verification_snapshot_unbound_directory)
                VerifiedPageOperation.CHAPTER -> stringResource(R.string.verification_snapshot_unbound_chapter)
            }
            ManualVerificationRoute(
                packageInfo = packageInfo,
                onCompleted = { scope.launch { owner.completeVerification() } },
                onVerifiedPageCompleted = { owner.completeVerifiedPage() },
                onCancel = { navController.navigateUp() },
                verifiedPageRequestUrl = pageRequest.url,
                verifiedPageRequestResolved = pageRequest.resolved,
                onUseVerifiedPage = when (operation) {
                    VerifiedPageOperation.NONE -> null
                    VerifiedPageOperation.SEARCH -> owner::useSearchVerifiedPage
                    VerifiedPageOperation.HOME -> owner::useHomeVerifiedPage
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
