/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
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
import org.tsuyomi.core.ui.components.TsuyomiOverflowAction
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.layout.TsuyomiWindowSize
import org.tsuyomi.core.ui.theme.TsuyomiBootScreen
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.feature.library.LibrarySelectionDialog
import org.tsuyomi.feature.library.LibrarySelectionKind
import org.tsuyomi.feature.library.LibraryTopBar
import org.tsuyomi.feature.library.projectedEntries
import org.tsuyomi.feature.library.libraryNodeRouteTitle
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.feature.backup.TransferScreen
import org.tsuyomi.feature.book.BookDetailScreen
import org.tsuyomi.feature.book.BookDetailTopBar
import org.tsuyomi.feature.book.BookDirectoryScreen
import org.tsuyomi.feature.browse.BrowseScreen
import org.tsuyomi.feature.browse.BrowseTopBar
import org.tsuyomi.feature.browse.SourceHomeViewState
import org.tsuyomi.feature.browse.RemoteLibraryScreen
import org.tsuyomi.feature.reader.ReaderScreen
import org.tsuyomi.feature.search.SearchLayout
import org.tsuyomi.feature.search.SearchScreen
import org.tsuyomi.feature.search.SearchTopBar
import org.tsuyomi.shared.backup.PortableReaderPreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            TsuyomiApplicationRoot(
                controller = (application as TsuyomiApplication).displayController,
            )
        }
    }
}




@Composable
internal fun TsuyomiApp(
    environment: DisplayEnvironment,
    controller: DisplayController,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as TsuyomiApplication
    val readerPreferences by application.readerPreferencesRepository.preferences.collectAsStateWithLifecycle(
        PortableReaderPreferences(flow = "scroll", fontScale = 1.0, lineHeight = 1.5, theme = "paper"),
    )
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
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val libraryFlow = rememberLibraryFlowController(
        application.libraryRepository,
        application.libraryPreferencesRepository,
    )
    suspend fun reloadLibrary() {
        libraryFlow.reload(resources.getString(R.string.library_read_failure_safe))
    }

    val navController = rememberNavController()
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
    val sourceOwner = rememberSourceRouteOwner(
        application = application,
        navController = navController,
        currentEntry = currentEntry,
        currentRoute = currentRoute,
        onLibraryChanged = ::reloadLibrary,
    )
    var detailRemoveConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    fun issueDetailCommand(command: SourceDetailRouteOwner.Command) {
        val entry = currentEntry?.takeIf { it.destination.route == Routes.Detail } ?: return
        val handle = entry.savedStateHandle
        handle[SourceDetailRouteOwner.CommandKey] = command.name
        handle[SourceDetailRouteOwner.CommandSequenceKey] =
            (handle.get<Long>(SourceDetailRouteOwner.CommandSequenceKey) ?: 0L) + 1L
    }
    LaunchedEffect(currentRoute) {
        if (currentRoute != Routes.Detail) detailRemoveConfirmationVisible = false
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
    LaunchedEffect(currentRoute) {
        if (currentRoute == Routes.Library) libraryFlow.selectRoot()
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
    val isRoot = currentRoute in setOf(Routes.Library, Routes.Browse, Routes.More)
    val settingsDependencies = remember(environment, controller, transferCoordinator, readerPreferences) {
        SettingsRouteDependencies(
            environment = environment,
            displayController = controller,
            transferCoordinator = transferCoordinator,
            readerPreferences = readerPreferences,
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowSize = TsuyomiWindowSize(
            widthDp = maxWidth.value.toInt(),
            heightDp = maxHeight.value.toInt(),
        )
        val routeOwnsChrome = environment.effectiveProfile == DisplayProfile.STANDARD &&
            currentRoute in setOf(Routes.Reader, Routes.RemoteLibrary)
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
                            refreshing = libraryFlow.state.refreshing,
                            root = currentRoute == Routes.Library,
                            onNavigateUp = if (currentRoute == Routes.Library) null else ({ navController.navigateUp() }),
                            onSearch = { navController.navigate(Routes.Search) },
                            onCycleLayout = libraryFlow::cycleLayout,
                            onRefresh = { scope.launch { reloadLibrary() } },
                            onSort = libraryFlow::openSort,
                            onTags = { navController.navigate(Routes.LibraryTags) },
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
                            installedSourceCount = if (sourceOwner.installer.activePackage == null) 0 else 1,
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
                        BookDetailTopBar(
                            title = sourceOwner.flow.selectedBook?.title ?: stringResource(R.string.title_book_detail),
                            inLibrary = sourceOwner.flow.remoteLibrary.selectedBookInLibrary,
                            onNavigateUp = { navController.navigateUp() },
                            onCacheDetail = { issueDetailCommand(SourceDetailRouteOwner.Command.CACHE_DETAIL) },
                            onRefresh = { issueDetailCommand(SourceDetailRouteOwner.Command.REFRESH_DETAIL) },
                            onRemoveFromLibrary = { detailRemoveConfirmationVisible = true },
                        )
                    } else if (
                        currentRoute in setOf(
                            Routes.Verification,
                            Routes.VerifiedPage,
                            Routes.VerifiedDetailPage,
                            Routes.VerifiedDirectoryPage,
                            Routes.VerifiedChapterPage,
                        ) &&
                        environment.effectiveProfile == DisplayProfile.STANDARD &&
                        sourceOwner.installer.activePackage != null
                    ) {
                        ManualVerificationTopBar(
                            packageInfo = requireNotNull(sourceOwner.installer.activePackage),
                            onNavigateUp = { navController.navigateUp() },
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
                            onSelect = { navController.selectRoot(it.route) },
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
                        coverState = libraryFlow::coverState,
                        openBookDetail = sourceOwner::openLibraryDetail,
                        resumeReading = sourceOwner::resumeReading,
                        onCoverVisibility = libraryFlow::setCoverVisible,
                    )
                    sourceRoutes(
                        navController = navController,
                        owner = sourceOwner,
                        readerPreferences = readerPreferences,
                        onReaderPreferencesChanged = { updated ->
                            scope.launch { application.readerPreferencesRepository.update(updated) }
                        },
                        coverRepository = coverRepository,
                        packageRevision = activeSourcePackage?.packageSha256,
                        credentialRevision = activeSourcePackage?.let { coverCredentialRevision },
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
