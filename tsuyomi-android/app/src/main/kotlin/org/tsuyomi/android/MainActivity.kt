/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import java.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.display.DisplayController
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayEnvironmentResolver
import org.tsuyomi.core.display.displayRedrawLayer
import org.tsuyomi.core.media.api.CoverRepositoryFactory
import org.tsuyomi.core.media.api.CoverMediaFetcher
import org.tsuyomi.core.ui.components.AppScaffold
import org.tsuyomi.core.ui.components.TsuyomiNavigation
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiTopBarAction
import org.tsuyomi.core.ui.components.TsuyomiTopBarTitleMenu
import org.tsuyomi.core.ui.components.TsuyomiOverflowAction
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.layout.TsuyomiWindowSize
import org.tsuyomi.core.ui.theme.TsuyomiBootScreen
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.feature.library.LibrarySelectionDialog
import org.tsuyomi.feature.library.LibrarySelectionKind
import org.tsuyomi.feature.library.LibraryTopBar
import org.tsuyomi.feature.library.SystemLibraryFilter
import org.tsuyomi.feature.library.projectedEntries
import org.tsuyomi.feature.library.libraryNodeRouteTitle
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.feature.backup.TransferScreen
import org.tsuyomi.feature.book.BookDetailScreen
import org.tsuyomi.feature.book.BookDetailTopBar
import org.tsuyomi.feature.book.BookDirectoryScreen
import org.tsuyomi.feature.browse.BrowseScreen
import org.tsuyomi.feature.browse.BrowseTopBar
import org.tsuyomi.feature.settings.FeatureIntroductionDialog
import org.tsuyomi.feature.settings.featureIntroductionDefinition
import org.tsuyomi.feature.browse.SourceHomeViewState
import org.tsuyomi.feature.reader.ReaderScreen
import org.tsuyomi.feature.search.SearchLayout
import org.tsuyomi.feature.search.SearchScreen
import org.tsuyomi.feature.search.SearchTopBar
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.source.extensionmanager.RemoteOperation
import org.tsuyomi.shared.librarydomain.UpdateSnapshot

class MainActivity : ComponentActivity() {
    private var updateNavigationSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            TsuyomiApplicationRoot(
                controller = (application as TsuyomiApplication).displayController,
                updateNavigationSignal = updateNavigationSignal,
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.action == UpdateNotificationOpenAction) updateNavigationSignal++
    }
}




@Composable
internal fun TsuyomiApp(
    environment: DisplayEnvironment,
    controller: DisplayController,
    updateNavigationSignal: Int = 0,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as TsuyomiApplication
    val updateSnapshot by produceState<UpdateSnapshot?>(initialValue = null, application.updateCoordinator) {
        application.updateCoordinator.snapshots.collect { value = it }
    }
    val readerPreferences by application.readerPreferencesRepository.preferences.collectAsStateWithLifecycle(
        PortableReaderPreferences(flow = "scroll", fontScale = 1.0, lineHeight = 1.5, theme = "paper"),
    )
    val introductionPreferences: org.tsuyomi.core.preferences.FeatureIntroductionPreferences? by
        application.featureIntroductionPreferencesRepository.preferences.collectAsStateWithLifecycle(initialValue = null)
    val transferCoordinator = remember {
        TransferCoordinator(context.applicationContext, application.transferRepository, application.readerPreferencesRepository)
    }
    LaunchedEffect(transferCoordinator) { transferCoordinator.recoverPendingImport() }
    if (!transferCoordinator.recoveryReady) {
        TransferScreen(
            state = transferCoordinator.state,
            onChooseImport = {},
            onConfirmImport = {},
            onCancelImport = {},
            onExport = {},
            onDismissResult = {},
            onRetryRecovery = { scope.launch { transferCoordinator.retryRecovery() } },
            onAbortRecovery = { scope.launch { transferCoordinator.abortRecovery() } },
            onResetInterfacePreferences = {},
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val libraryFlow = rememberLibraryFlowController(
        application.libraryRepository,
        application.libraryPreferencesRepository,
        environment.effectiveProfile.name,
    )
    suspend fun reloadLibrary() {
        libraryFlow.reload(resources.getString(R.string.library_read_failure_safe))
    }

    val navController = rememberNavController()
    fun requestManualUpdate() {
        application.updateScheduler.enqueueManual()
    }
    LaunchedEffect(updateSnapshot) {
        libraryFlow.updateUnresolved(updateSnapshot?.updates.orEmpty(), updateSnapshot?.session)
    }
    LaunchedEffect(updateNavigationSignal) {
        if (updateNavigationSignal > 0) {
            libraryFlow.selectTab(SystemLibraryFilter.ALL)
            libraryFlow.setUpdateFilter(org.tsuyomi.feature.library.LibraryUpdateFilter.UPDATES_ONLY)
            navController.navigate(Routes.Library) { launchSingleTop = true }
        }
    }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: Routes.Library
    val searchLayoutFlow = remember(currentEntry) {
        currentEntry
            ?.takeIf { it.destination.route == Routes.Search }
            ?.savedStateHandle
            ?.getStateFlow(SourceSearchRouteOwner.LayoutKey, SearchLayout.LIST)
            ?: MutableStateFlow(SearchLayout.LIST)
    }
    val searchLayout by searchLayoutFlow.collectAsStateWithLifecycle()
    val detailRemoteMembershipFlow = remember(currentEntry) {
        currentEntry
            ?.takeIf { it.destination.route == Routes.Detail }
            ?.savedStateHandle
            ?.getStateFlow(RemoteBookMembershipKey, false)
            ?: MutableStateFlow(false)
    }
    val detailBookInRemoteLibrary by detailRemoteMembershipFlow.collectAsStateWithLifecycle()
    val sourceOwner = rememberSourceRouteOwner(
        application = application,
        navController = navController,
        currentEntry = currentEntry,
        currentRoute = currentRoute,
        onLibraryChanged = ::reloadLibrary,
    )
    libraryFlow.configureInstalledMirrorRoots(sourceOwner.installer::installedRemoteLibraryRoots)
    var pendingIntroductionId by rememberSaveable { mutableStateOf<String?>(null) }
    val introductionRouteId = when (currentRoute) {
        Routes.LibraryMirror, Routes.LibraryMirrorFolder -> "website-mirror"
        Routes.Collections -> "smart-collection"
        Routes.RemoteLibrary -> "website-writeback"
        Routes.Transfer -> "data-transfer"
        else -> null
    }
    LaunchedEffect(currentRoute, introductionRouteId, introductionPreferences) {
        val preferences = introductionPreferences ?: return@LaunchedEffect
        pendingIntroductionId = introductionRouteId?.takeIf { id ->
            preferences.enabled && "$id:1" !in preferences.seenVersions
        }
    }
    var detailRemoveConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    fun issueDetailCommand(command: SourceDetailRouteOwner.Command) {
        val entry = currentEntry?.takeIf { it.destination.route == Routes.Detail } ?: return
        val handle = entry.savedStateHandle
        handle[SourceDetailRouteOwner.CommandKey] = command.name
        handle[SourceDetailRouteOwner.CommandSequenceKey] =
            (handle.get<Long>(SourceDetailRouteOwner.CommandSequenceKey) ?: 0L) + 1L
    }
    fun issueDetailRequest(key: String) {
        val entry = currentEntry?.takeIf { it.destination.route == Routes.Detail } ?: return
        val handle = entry.savedStateHandle
        handle[key] = (handle.get<Long>(key) ?: 0L) + 1L
    }
    LaunchedEffect(currentRoute) {
        if (currentRoute != Routes.Detail) detailRemoveConfirmationVisible = false
        libraryFlow.setFilterAndSortPanelExpanded(false)
    }
    val activeSourcePackage = sourceOwner.installer.activePackage
    val coverGateway = remember(activeSourcePackage?.packageSha256) {
        activeSourcePackage?.let { packageInfo ->
            Phase2SourceGateway.create(context, packageInfo, org.tsuyomi.core.network.DirectActionTokenRegistry())
        }
    }
    val coverCredentialRevision = remember(activeSourcePackage?.packageSha256) {
        activeSourcePackage?.let { SourceGatewayFactory.mediaCredentialRevision(context, it) } ?: "anonymous"
    }
    val coverMediaFetcher = remember(coverGateway, activeSourcePackage?.packageSha256) {
        val packageInfo = activeSourcePackage
        if (coverGateway == null || packageInfo == null) null else CoverMediaFetcher { url, referrerUrl ->
            coverGateway.fetchMedia(SourceGatewayFactory.networkGrant(packageInfo), url, referrerUrl)
                .let { org.tsuyomi.core.media.api.CoverMediaPayload(it.bytes, it.contentType) }
        }
    }
    val coverRepository = remember(activeSourcePackage?.packageSha256) {
        activeSourcePackage?.let { packageInfo ->
            val network = packageInfo.manifest.capabilities.network
            CoverRepositoryFactory.create(
                context = context.applicationContext,
                origins = network.origins,
                maxResponseBytes = network.maxResponseBytes,
                sourceId = packageInfo.manifest.sourceId.value,
                packageRevision = packageInfo.packageSha256,
                credentialRevision = coverCredentialRevision,
                mediaFetcher = coverMediaFetcher,
            )
        }
    }
    LaunchedEffect(coverRepository, activeSourcePackage?.packageSha256) {
        libraryFlow.configureCoverRepository(
            repository = coverRepository,
            sourceId = activeSourcePackage?.manifest?.sourceId?.value,
            packageRevision = activeSourcePackage?.packageSha256,
            credentialRevision = activeSourcePackage?.let { coverCredentialRevision },
            scope = scope,
        )
    }
    LaunchedEffect(activeSourcePackage?.packageSha256) {
        reloadLibrary()
    }
    BackHandler(
        enabled = currentRoute in setOf(
            Routes.Library,
            Routes.LibrarySystem,
            Routes.LibraryCollection,
            Routes.LibraryTagBooks,
        ) && libraryFlow.state.selectionKind != null,
    ) {
        libraryFlow.clearSelection()
    }
    BackHandler(
        enabled = currentRoute == Routes.Library &&
            libraryFlow.state.selectionKind == null &&
            libraryFlow.state.filter != SystemLibraryFilter.ALL,
    ) {
        scope.launch {
            libraryFlow.selectTab(SystemLibraryFilter.ALL)
        }
    }

    val selectedRoot = rootRouteFor(currentRoute)
    val appNavigationItems = navigationItems()
    val title = if (currentRoute == Routes.LibrarySystem || currentRoute == Routes.LibraryCollection) {
        libraryNodeRouteTitle(
            filterName = currentEntry?.arguments?.getString("filter"),
            collectionId = currentEntry?.arguments?.getString("collectionId"),
            collections = libraryFlow.collections,
        ) ?: routeTitle(currentRoute)
    } else {
        routeTitle(currentRoute)
    }
    val sourceHomeContent = sourceOwner.flow.homeState as? SourceHomeViewState.Content
    val sourceHomeSourceName = activeSourcePackage?.manifest?.displayName.orEmpty()
    val sourceHomeSources = sourceOwner.installer.trustedInstalledPackages
    var sourceSwitchUnavailable by remember(currentRoute) { mutableStateOf(false) }
    val isRoot = currentRoute in setOf(Routes.Library, Routes.Browse, Routes.More)
    val settingsDependencies = remember(environment, controller, transferCoordinator, readerPreferences, application) {
        SettingsRouteDependencies(
            environment = environment,
            displayController = controller,
            transferCoordinator = transferCoordinator,
            readerPreferences = readerPreferences,
            application = application,
        )
    }

    if (detailRemoveConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { detailRemoveConfirmationVisible = false },
            title = { Text(stringResource(R.string.detail_remove_confirm_title)) },
            text = { Text(stringResource(R.string.detail_remove_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        detailRemoveConfirmationVisible = false
                        issueDetailCommand(SourceDetailRouteOwner.Command.REMOVE_FROM_LIBRARY)
                    },
                ) { Text(stringResource(R.string.detail_remove_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { detailRemoveConfirmationVisible = false }) {
                    Text(stringResource(R.string.detail_remove_cancel))
                }
            },
        )
    }
    pendingIntroductionId?.let { id ->
        featureIntroductionDefinition(id)?.let { introduction ->
            FeatureIntroductionDialog(
                introduction = introduction,
                onAcknowledged = {
                    pendingIntroductionId = null
                    scope.launch {
                        application.featureIntroductionPreferencesRepository.markSeen(
                            introduction.id,
                            introduction.version,
                        )
                    }
                },
                onDismiss = { pendingIntroductionId = null },
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowSize = TsuyomiWindowSize(
            widthDp = maxWidth.value.toInt(),
            heightDp = maxHeight.value.toInt(),
        )
        val routeOwnsChrome = environment.effectiveProfile == DisplayProfile.STANDARD && currentRoute in setOf(
            Routes.Reader,
            Routes.RemoteLibrary,
            Routes.LibraryMirror,
            Routes.LibraryMirrorFolder,
            Routes.Verification,
            Routes.VerifiedHomePage,
            Routes.VerifiedPage,
            Routes.VerifiedDetailPage,
            Routes.VerifiedDirectoryPage,
            Routes.VerifiedChapterPage,
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .displayRedrawLayer(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppScaffold(
                windowSize = windowSize,
                topBar = {
                    if (routeOwnsChrome) {
                        Unit
                    } else if (currentRoute in setOf(
                        Routes.Library,
                        Routes.LibrarySystem,
                        Routes.LibraryCollection,
                        Routes.LibraryTagBooks,
                    )) {
                        LibraryTopBar(
                            title = title,
                            bookCount = libraryFlow.state.projectedEntries().size,
                            layout = libraryFlow.state.layout,
                            sortMode = libraryFlow.state.sortMode,
                            sortDescending = libraryFlow.state.sortDescending,
                            root = currentRoute == Routes.Library && libraryFlow.state.filter == SystemLibraryFilter.ALL,
                            refreshing = if (
                                currentRoute == Routes.Library && libraryFlow.state.filter == SystemLibraryFilter.ALL
                            ) {
                                libraryFlow.state.updateSession?.state in setOf(
                                    org.tsuyomi.shared.librarydomain.UpdateSessionStates.QUEUED,
                                    org.tsuyomi.shared.librarydomain.UpdateSessionStates.RUNNING,
                                )
                            } else {
                                libraryFlow.state.refreshing
                            },
                            updateFilter = libraryFlow.state.updateFilter,
                            filterSortPanelExpanded = libraryFlow.filterSortPanelExpanded,
                            onFilterSortPanelExpandedChange = libraryFlow::setFilterAndSortPanelExpanded,
                            onSetUpdateFilter = { filter -> scope.launch { libraryFlow.setUpdateFilter(filter) } },
                            onNavigateUp = if (currentRoute == Routes.Library) null else ({ navController.navigateUp() }),
                            onSearch = { navController.navigate(Routes.LibrarySearch) },
                            onCycleLayout = { scope.launch { libraryFlow.cycleLayout() } },
                            onCheckUpdates = ::requestManualUpdate,
                            onRefresh = { scope.launch { reloadLibrary() } },
                            onOpenUpdateSettings = { navController.navigate(Routes.UpdateSettings) },
                            onSelectSort = { mode -> scope.launch { libraryFlow.selectSort(mode) } },
                            onSelectSortDirection = { descending -> scope.launch {
                                libraryFlow.selectSortDirection(descending)
                            } },
                            onTags = { navController.navigate(Routes.LibraryTags) },
                            onCreateCollection = { navController.navigate(Routes.Collections) },
                            selectionKind = libraryFlow.state.selectionKind,
                            selectedCount = libraryFlow.state.selectedBookIds.size + libraryFlow.state.selectedCollectionIds.size,
                            allVisibleSelected = when (libraryFlow.state.selectionKind) {
                                LibrarySelectionKind.BOOK -> libraryFlow.state.projectedEntries().let { visible ->
                                    visible.isNotEmpty() && libraryFlow.state.selectedBookIds.containsAll(
                                        visible.map { it.book.identity },
                                    )
                                }
                                LibrarySelectionKind.COLLECTION -> libraryFlow.collections
                                    .filter { it.kind == org.tsuyomi.core.database.CollectionKind.MANUAL }
                                    .let { visible ->
                                        visible.isNotEmpty() && libraryFlow.state.selectedCollectionIds.containsAll(
                                            visible.map { it.collectionId },
                                        )
                                    }
                                null -> false
                            },
                            onClearSelection = libraryFlow::clearSelection,
                            onToggleAllSelection = libraryFlow::toggleAllVisibleSelection,
                            onCreateCollectionFromSelection = {
                                libraryFlow.requestSelectionDialog(LibrarySelectionDialog.CREATE_COLLECTION)
                            },
                            onAddSelectionToCollection = {
                                libraryFlow.requestSelectionDialog(LibrarySelectionDialog.ADD_TO_COLLECTION)
                            },
                            onRemoveSelection = {
                                libraryFlow.requestSelectionDialog(LibrarySelectionDialog.CONFIRM_REMOVE)
                            },
                        )
                    } else if (
                        currentRoute == Routes.Browse &&
                        environment.effectiveProfile == DisplayProfile.STANDARD
                    ) {
                        BrowseTopBar(
                            installedSourceCount = sourceOwner.installer.installedPackages.size,
                            onSearch = { navController.navigate(Routes.Search) },
                            onImport = sourceOwner::requestImport,
                            onRefreshSources = { scope.launch { sourceOwner.refreshInstalledSources() } },
                        )
                    } else if (
                        currentRoute == Routes.SourceHome &&
                        environment.effectiveProfile == DisplayProfile.STANDARD
                    ) {
                        TsuyomiTopBar(
                            title = sourceHomeContent?.title?.takeIf(String::isNotBlank)
                                ?: sourceHomeSourceName.ifBlank { stringResource(R.string.title_source_home_standard_fallback) },
                            subtitle = if (sourceSwitchUnavailable) {
                                stringResource(R.string.source_home_switch_unavailable)
                            } else {
                                null
                            },
                            titleMenu = sourceHomeSources.takeIf { it.size > 1 }?.let { sources ->
                                TsuyomiTopBarTitleMenu(
                                    contentDescription = stringResource(R.string.source_home_switch_sources),
                                    panelTitle = stringResource(R.string.source_home_switch_sources_title),
                                    testTag = "source-home-switcher",
                                    actions = sources.map { source ->
                                        TsuyomiOverflowAction(
                                            label = if (source.manifest.capabilities.home.enabled) {
                                                source.manifest.displayName
                                            } else {
                                                stringResource(
                                                    R.string.source_home_switch_search_only,
                                                    source.manifest.displayName,
                                                )
                                            },
                                            selected = source.manifest.sourceId == activeSourcePackage?.manifest?.sourceId,
                                            onClick = {
                                                scope.launch {
                                                    sourceSwitchUnavailable = sourceOwner.switchSourceHome(
                                                        source.manifest.sourceId.value,
                                                    ) == SourceHomeSwitchResult.UNAVAILABLE
                                                }
                                            },
                                        )
                                    },
                                )
                            },
                            onNavigateUp = {
                                if (!sourceOwner.flow.home.navigateBackFromFeature()) navController.navigateUp()
                            },
                            actions = listOf(
                                TsuyomiTopBarAction(
                                    icon = TsuyomiIcons.Search,
                                    label = stringResource(R.string.title_source_home_search),
                                    onClick = { scope.launch { sourceOwner.openInstalledSource() } },
                                ),
                            ),
                            overflow = buildList {
                                if (sourceOwner.remoteLibraryAvailable) {
                                    add(
                                        TsuyomiOverflowAction(
                                            label = stringResource(R.string.title_source_home_remote_library),
                                            icon = TsuyomiIcons.Bookmark,
                                            onClick = { scope.launch { sourceOwner.openRemoteLibrary() } },
                                        ),
                                    )
                                }
                                if (activeSourcePackage?.manifest?.capabilities?.webLogin?.enabled == true) {
                                    add(
                                        TsuyomiOverflowAction(
                                            label = stringResource(R.string.title_source_home_verification),
                                            icon = TsuyomiIcons.Verify,
                                            onClick = sourceOwner::navigateToVerification,
                                        ),
                                    )
                                }
                                add(
                                    TsuyomiOverflowAction(
                                        label = stringResource(R.string.title_source_home_refresh),
                                        icon = TsuyomiIcons.Refresh,
                                        onClick = { scope.launch { sourceOwner.refreshSourceHome() } },
                                    ),
                                )
                            },
                        )
                    } else if (
                        currentRoute == Routes.Search &&
                        environment.effectiveProfile == DisplayProfile.STANDARD
                    ) {
                        SearchTopBar(
                            layout = searchLayout,
                            onCycleLayout = {
                                currentEntry?.savedStateHandle?.set(
                                    SourceSearchRouteOwner.LayoutKey,
                                    searchLayout.next(),
                                )
                            },
                            onNavigateUp = { navController.navigateUp() },
                        )
                    } else if (
                        currentRoute == Routes.Detail &&
                        environment.effectiveProfile == DisplayProfile.STANDARD
                    ) {
                        val selectedBook = sourceOwner.flow.selectedBook
                        val selectedPackage = sourceOwner.installer.activePackage
                        val sameActiveSource = selectedBook != null &&
                            selectedPackage?.manifest?.sourceId?.value == selectedBook.identity.sourceId
                        val remotePolicies = selectedPackage?.manifest?.capabilities?.remoteLibrary?.policies
                        BookDetailTopBar(
                            title = stringResource(R.string.title_book_detail),
                            inLibrary = sourceOwner.flow.remoteLibrary.selectedBookInLibrary,
                            onNavigateUp = { navController.navigateUp() },
                            onCacheDetail = { issueDetailCommand(SourceDetailRouteOwner.Command.CACHE_DETAIL) },
                            onRefresh = { issueDetailCommand(SourceDetailRouteOwner.Command.REFRESH_DETAIL) },
                            onRemoveFromLibrary = { detailRemoveConfirmationVisible = true },
                            remoteRemoveAvailable = detailBookInRemoteLibrary && sameActiveSource &&
                                remotePolicies?.containsKey(RemoteOperation.REMOVE) == true,
                            remoteMoveAvailable = detailBookInRemoteLibrary && sameActiveSource &&
                                libraryFlow.isWebsiteGroupingEnabled(selectedBook.identity.sourceId) &&
                                remotePolicies?.containsKey(RemoteOperation.MOVE) == true,
                            onRemoveFromRemote = { issueDetailRequest(RemoteRemoveRequestKey) },
                            onMoveRemote = { issueDetailRequest(RemoteMoveRequestKey) },
                            updateChecksExcluded = selectedBook?.identity in updateSnapshot?.excludedBooks.orEmpty(),
                            onToggleUpdateChecksExcluded = {
                                selectedBook?.identity?.let { identity ->
                                    scope.launch {
                                        application.updateCoordinator.excludeBook(
                                            identity,
                                            identity !in updateSnapshot?.excludedBooks.orEmpty(),
                                        )
                                    }
                                }
                            },
                        )
                    } else {
                        TsuyomiTopBar(
                            title = title,
                            onNavigateUp = if (isRoot) null else ({ navController.navigateUp() }),
                        )
                    }
                },
                navigation = { layout ->
                    if (!routeOwnsChrome) {
                        TsuyomiNavigation(
                            layout = layout,
                            items = appNavigationItems,
                            selectedRoute = selectedRoot,
                            onSelect = { item ->
                                if (item.route == Routes.Library) {
                                    scope.launch {
                                        libraryFlow.selectTab(SystemLibraryFilter.ALL)
                                    }
                                }
                                navController.selectRoot(item.route)
                            }
                        )
                    }
                },
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.Library,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    libraryRoutes(
                        navController = navController,
                        controller = libraryFlow,
                        coverState = { entry -> libraryFlow.coverState(entry) },
                        openBookDetail = sourceOwner::openLibraryDetail,
                        resumeReading = sourceOwner::resumeReading,
                        onCoverVisibility = libraryFlow::setCoverVisible,
                        openRemoteDestination = sourceOwner::openRemoteDestination,
                        onManualUpdate = ::requestManualUpdate,
                        onCancelUpdate = { scope.launch { application.updateScheduler.cancel() } },
                        onOpenUpdateSettings = { navController.navigate(Routes.UpdateSettings) },
                        onIgnoreUpdate = { update ->
                            application.updateCoordinator.ignore(update.identity, update.anchor)
                        },
                        onUndoUpdate = application.updateCoordinator::undo,
                    )
                    updateSettingsRoute(application, libraryFlow)
                    sourceRoutes(
                        navController = navController,
                        owner = sourceOwner,
                        libraryFlow = libraryFlow,
                        readerPreferences = readerPreferences,
                        onReaderPreferencesChanged = { updated ->
                            scope.launch { application.readerPreferencesRepository.update(updated) }
                        },
                        coverRepository = coverRepository,
                        packageRevision = activeSourcePackage?.packageSha256,
                        credentialRevision = activeSourcePackage?.let { coverCredentialRevision },
                        onRequestRemoveFromLibrary = { detailRemoveConfirmationVisible = true },
                        onExactChapterCompleted = { identity ->
                            application.updateCoordinator.reconcileCompleted(
                                identity,
                                application.libraryRepository.completedChapterIds(identity),
                            )
                        },
                    )
                    settingsRoutes(
                        navController = navController,
                        dependencies = settingsDependencies,
                        onImportConfirmed = { reloadLibrary() },
                    )
                }
            }
        }
    }
}
