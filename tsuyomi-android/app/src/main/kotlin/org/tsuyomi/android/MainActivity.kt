/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import java.util.concurrent.CancellationException
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.launch
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.CollectionKind
import org.tsuyomi.core.database.LibraryCollection
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.display.DisplayController
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.feature.backup.TransferScreen
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayEnvironmentResolver
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.displayRedrawLayer
import org.tsuyomi.core.ui.components.AppScaffold
import org.tsuyomi.core.ui.components.TsuyomiNavigation
import org.tsuyomi.core.ui.components.TsuyomiNavigationItem
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.layout.TsuyomiWindowSize
import org.tsuyomi.feature.browse.RemoteLibraryScreen
import org.tsuyomi.core.ui.theme.TsuyomiBootScreen
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.feature.browse.BrowseScreen
import org.tsuyomi.feature.book.BookDetailScreen
import org.tsuyomi.feature.book.BookDirectoryScreen
import org.tsuyomi.feature.library.CollectionManagerScreen
import org.tsuyomi.feature.library.SmartConditionDraft
import org.tsuyomi.feature.library.SmartField
import org.tsuyomi.feature.library.LibraryScreen
import org.tsuyomi.feature.library.LibraryUiState
import org.tsuyomi.feature.library.LocalBookDetailsScreen
import org.tsuyomi.feature.reader.ReaderScreen
import org.tsuyomi.feature.search.SearchScreen
import org.tsuyomi.feature.settings.AboutScreen
import org.tsuyomi.feature.settings.DisplaySettingsActions
import org.tsuyomi.feature.settings.DisplaySettingsScreen
import org.tsuyomi.feature.settings.DisplaySettingsUiState
import org.tsuyomi.feature.settings.DisplayWriteFailure
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.feature.settings.MoreScreen
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.smartshelf.MatchMode
import org.tsuyomi.shared.smartshelf.ProgressState
import org.tsuyomi.shared.smartshelf.PublicationStatus
import org.tsuyomi.shared.smartshelf.SmartPredicate
import org.tsuyomi.shared.smartshelf.SmartRule
import org.tsuyomi.shared.smartshelf.SmartRuleNode

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

private object Routes {
    const val Library = "library"
    const val Browse = "browse"
    const val LocalBook = "library/book/{sourceId}/{remoteBookId}"
    const val Collections = "library/collections"
    const val More = "more"
    const val Display = "settings/display"
    const val About = "about"
    const val Search = "source/search"
    const val Detail = "source/detail"
    const val Directory = "source/directory"
    const val Reader = "source/reader"
    const val Verification = "source/verification"
    const val RemoteLibrary = "source/remote-library"
    const val Transfer = "settings/transfer"
    fun localBook(identity: BookIdentity): String =
        "library/book/${Uri.encode(identity.sourceId)}/${Uri.encode(identity.remoteBookId)}"
}

@Composable
private fun TsuyomiApplicationRoot(controller: DisplayController) {
    val redrawEpoch by controller.redrawEpoch.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val reducedMotion = rememberSystemReducedMotion()
    val resolver = remember { DisplayEnvironmentResolver() }
    val preferences by controller.preferences.collectAsStateWithLifecycle(initialValue = null)

    val currentPreferences = preferences
    if (currentPreferences == null) {
        TsuyomiBootScreen {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.boot_loading),
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                )
            }
        }
        return
    }

    val environment = resolver.resolve(
        preferences = currentPreferences,
        classification = controller.classification,
        apiLevel = android.os.Build.VERSION.SDK_INT,
        systemDark = systemDark,
        reducedMotion = reducedMotion,
        redrawEpoch = redrawEpoch,
    )

    DisplayEnvironmentProvider(environment) {
        TsuyomiTheme {
            SystemBarPolicy(environment)
            TsuyomiApp(environment, controller)
        }
    }
}

@Composable
private fun TsuyomiApp(
    environment: DisplayEnvironment,
    controller: DisplayController,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as TsuyomiApplication
    val sourceInstaller = remember { SourceInstallController(context.applicationContext, application.libraryRepository) }
    val readerPreferences by application.readerPreferencesRepository.preferences.collectAsStateWithLifecycle(
        PortableReaderPreferences(flow = "scroll", fontScale = 1.0, lineHeight = 1.5, theme = "paper"),
    )
    val transferCoordinator = remember {
        TransferCoordinator(context.applicationContext, application.transferRepository, application.readerPreferencesRepository)
    }
    var preparedExportGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var preparedExportDigest by rememberSaveable { mutableStateOf<String?>(null) }
    val transferImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { document -> scope.launch { transferCoordinator.readForReview(document, context.contentResolver) } }
    }
    val transferExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val ownerGeneration = preparedExportGeneration
        val canonicalDigest = preparedExportDigest
        if (ownerGeneration != null && canonicalDigest != null) {
            scope.launch {
                if (uri == null) {
                    transferCoordinator.cancelPreparedExport(ownerGeneration, canonicalDigest)
                } else {
                    transferCoordinator.writePreparedExport(uri, context.contentResolver, ownerGeneration, canonicalDigest)
                }
                if (preparedExportGeneration == ownerGeneration && preparedExportDigest == canonicalDigest) {
                    preparedExportGeneration = null
                    preparedExportDigest = null
                }
            }
        }
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
    var collections by remember { mutableStateOf<List<LibraryCollection>>(emptyList()) }
    var collectionMessage by remember { mutableStateOf<String?>(null) }
    var selectedCollectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryState by remember { mutableStateOf(LibraryUiState()) }
    var selectedLocalEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var localTagDraft by rememberSaveable { mutableStateOf("") }

    suspend fun reloadLibrary() {
        libraryState = libraryState.copy(loading = true, failure = null)
        libraryState = try {
            collections = application.libraryRepository.collections()
            if (selectedCollectionId != null && collections.none { it.collectionId == selectedCollectionId }) selectedCollectionId = null
            val entries = selectedCollectionId?.let { application.libraryRepository.collectionEntries(it) }
                ?: application.libraryRepository.libraryEntries()
            libraryState.copy(entries = entries, loading = false)
        } catch (_: Throwable) {
            libraryState.copy(loading = false, failure = resources.getString(R.string.library_read_failure_safe))
        }
    }

    var remoteLibraryLoading by remember { mutableStateOf(false) }
    var showPostLoginImportPrompt by remember { mutableStateOf(false) }
    var remoteLibraryMessage by remember { mutableStateOf<String?>(null) }
    var remoteWritebackEnabled by remember { mutableStateOf(false) }
    var remoteWritebackAvailable by remember { mutableStateOf(false) }
    val extensionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { document -> scope.launch { sourceInstaller.prepare(document, context.contentResolver) } }
    }
    LaunchedEffect(sourceInstaller) { sourceInstaller.restoreInstalled() }
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: Routes.Library
    LaunchedEffect(currentRoute, selectedCollectionId, sourceInstaller.activePackage) {
        if (currentRoute == Routes.Library || currentRoute == Routes.LocalBook) {
            reloadLibrary()
            selectedLocalEntry?.book?.identity?.let { identity ->
                selectedLocalEntry = application.libraryRepository.libraryEntries()
                    .firstOrNull { it.book.identity == identity }
            }
        }
    }
    val selectedRoot = when (currentRoute) {
        Routes.LocalBook, Routes.Collections -> Routes.Library
        Routes.Display, Routes.About, Routes.Transfer -> Routes.More
        Routes.Search, Routes.Detail, Routes.Directory, Routes.Reader, Routes.Verification, Routes.RemoteLibrary -> Routes.Browse
        else -> currentRoute
    }
    val ownsSourceFlow = when (currentRoute) {
        Routes.Browse,
        Routes.Search,
        Routes.Detail,
        Routes.Directory,
        Routes.Reader,
        Routes.Verification,
        Routes.RemoteLibrary,
        -> true
        else -> false
    }
    val sourceFlowOwner = remember(currentEntry) {
        if (ownsSourceFlow) navController.getBackStackEntry(Routes.Browse) else null
    }
    val sourceFlow = remember(sourceFlowOwner) {
        SourceFlowController(
            context.applicationContext,
            application.libraryRepository,
            SourceFlowSnapshotStore(application.preferencesDataStore),
        )
    }
    DisposableEffect(sourceFlow) {
        onDispose(sourceFlow::close)
    }
    LaunchedEffect(sourceInstaller.activePackage, currentRoute) {
        val packageInfo = sourceInstaller.activePackage ?: return@LaunchedEffect
        val target = when (currentRoute) {
            Routes.Search -> SourceRestorationTarget.SEARCH
            Routes.Detail -> SourceRestorationTarget.DETAIL
            Routes.Directory -> SourceRestorationTarget.DIRECTORY
            Routes.Reader -> SourceRestorationTarget.READER
            Routes.RemoteLibrary -> SourceRestorationTarget.SEARCH
            else -> null
        }
        target?.let { sourceFlow.restoreFor(it, packageInfo) }
        if (currentRoute == Routes.RemoteLibrary) {
            val remotePolicy = sourceInstaller.remotePolicy()
            remoteWritebackEnabled = remotePolicy?.addWritebackEnabled == true
            remoteWritebackAvailable = sourceInstaller.remoteAddCredentialReady()
        }
    }
    suspend fun performRemotePull() {
        val packageInfo = sourceInstaller.activePackage ?: return
        remoteLibraryLoading = true
        remoteLibraryMessage = when (val result = sourceFlow.pullRemoteLibrary(packageInfo, Instant.now())) {
            is RemoteLibraryPullResult.Success -> resources.getString(R.string.remote_pull_success, result.total, result.newlyAdded)
            RemoteLibraryPullResult.LoginRequired -> {
                navController.navigate(Routes.Verification)
                resources.getString(R.string.remote_pull_login_required)
            }
            RemoteLibraryPullResult.VerificationRequired -> {
                navController.navigate(Routes.Verification)
                resources.getString(R.string.remote_pull_verification_required)
            }
            RemoteLibraryPullResult.Cancelled -> resources.getString(R.string.remote_pull_cancelled)
            is RemoteLibraryPullResult.Failure -> resources.getString(R.string.remote_pull_failed, result.safeCode)
        }
        remoteLibraryLoading = false
        reloadLibrary()
    }
    val navigationItems = listOf(
        TsuyomiNavigationItem(
            route = Routes.Library,
            label = stringResource(R.string.nav_library),
            icon = TsuyomiIcons.Shelf,
        ),
        TsuyomiNavigationItem(
            route = Routes.Browse,
            label = stringResource(R.string.nav_browse),
            icon = TsuyomiIcons.Compass,
        ),
        TsuyomiNavigationItem(
            route = Routes.More,
            label = stringResource(R.string.nav_more),
            icon = TsuyomiIcons.More,
        ),
    )
    val title = when (currentRoute) {
        Routes.Library -> stringResource(R.string.nav_library)
        Routes.LocalBook -> stringResource(R.string.title_local_book)
        Routes.Collections -> stringResource(R.string.title_collections)
        Routes.Browse -> stringResource(R.string.nav_browse)
        Routes.More -> stringResource(R.string.nav_more)
        Routes.Display -> stringResource(R.string.title_display_settings)
        Routes.About -> stringResource(R.string.title_about)
        Routes.Search -> stringResource(R.string.title_source_search)
        Routes.Detail -> stringResource(R.string.title_book_detail)
        Routes.Directory -> stringResource(R.string.title_book_directory)
        Routes.Transfer -> stringResource(R.string.title_data_transfer)
        Routes.Reader -> stringResource(R.string.title_reader)
        Routes.Verification -> stringResource(R.string.title_verification)
        Routes.RemoteLibrary -> stringResource(R.string.title_remote_library)
        else -> stringResource(R.string.app_name)
    }
    val isRoot = currentRoute in setOf(Routes.Library, Routes.Browse, Routes.More)

    fun selectRoot(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun buildSmartRule(matchAll: Boolean, drafts: List<SmartConditionDraft>): SmartRule {
        fun values(raw: String): Set<String> = raw.split(',', '，').mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
        val children = drafts.map { draft ->
            val parsedValues = values(draft.value)
            val predicate: SmartPredicate = when (draft.field) {
                SmartField.SOURCE -> SmartPredicate.SourceIn(parsedValues)
                SmartField.MANUAL_COLLECTION -> SmartPredicate.InManualCollection(parsedValues)
                SmartField.TAG -> SmartPredicate.TagContains(MatchMode.ANY, parsedValues)
                SmartField.TITLE -> SmartPredicate.TitleContains(parsedValues)
                SmartField.AUTHOR -> SmartPredicate.AuthorContains(parsedValues)
                SmartField.STATUS -> SmartPredicate.StatusIn(parsedValues.mapTo(linkedSetOf()) { PublicationStatus.valueOf(it.uppercase()) })
                SmartField.RATING -> {
                    val range = draft.value.split(',', '，').map { it.trim() }
                    SmartPredicate.RatingBetween(range.getOrNull(0)?.toDoubleOrNull(), range.getOrNull(1)?.toDoubleOrNull())
                }
                SmartField.ADDED_WITHIN_DAYS -> SmartPredicate.AddedWithinDays(draft.value.trim().toLong())
                SmartField.LAST_READ_WITHIN_DAYS -> SmartPredicate.LastReadWithinDays(draft.value.trim().toLong())
                SmartField.METADATA_UPDATED_WITHIN_DAYS -> SmartPredicate.MetadataUpdatedWithinDays(draft.value.trim().toLong())
                SmartField.PROGRESS -> SmartPredicate.ProgressIn(parsedValues.mapTo(linkedSetOf()) { ProgressState.valueOf(it.uppercase()) })
                SmartField.UNREAD_UPDATE -> SmartPredicate.HasUnreadUpdate
                SmartField.SOURCE_UPDATE -> SmartPredicate.HasSourceUpdate
                SmartField.DORMANT_SOURCE -> SmartPredicate.IsDormantSource
            }
            val node = SmartRuleNode.Predicate(predicate)
            if (draft.excluded) SmartRuleNode.Not(node) else node
        }
        return SmartRule(root = if (matchAll) SmartRuleNode.All(children) else SmartRuleNode.Any(children))
    }
    if (showPostLoginImportPrompt) {
        AlertDialog(
            onDismissRequest = { showPostLoginImportPrompt = false },
            title = { Text(stringResource(R.string.post_login_import_title)) },
            text = { Text(stringResource(R.string.post_login_import_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPostLoginImportPrompt = false
                        if (currentRoute != Routes.RemoteLibrary) navController.navigate(Routes.RemoteLibrary)
                        scope.launch { performRemotePull() }
                    },
                ) { Text(stringResource(R.string.post_login_import_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPostLoginImportPrompt = false }) {
                    Text(stringResource(R.string.post_login_import_later))
                }
            },
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowSize = TsuyomiWindowSize(
            widthDp = maxWidth.value.toInt(),
            heightDp = maxHeight.value.toInt(),
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
                    TsuyomiTopBar(
                        title = title,
                        onNavigateUp = if (isRoot) null else ({ navController.navigateUp() }),
                    )
                },
                navigation = { layout ->
                    TsuyomiNavigation(
                        layout = layout,
                        items = navigationItems,
                        selectedRoute = selectedRoot,
                        onSelect = { selectRoot(it.route) },
                    )
                },
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.Library,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Routes.Library) {
                        LibraryScreen(
                            state = libraryState,
                            collections = collections,
                            selectedCollectionId = selectedCollectionId,
                            onCollectionChange = { collectionId ->
                                selectedCollectionId = collectionId
                                scope.launch { reloadLibrary() }
                            },
                            onQueryChange = { libraryState = libraryState.copy(query = it) },
                            onFilterChange = { libraryState = libraryState.copy(filter = it) },
                            onOpenBook = { entry ->
                                selectedLocalEntry = entry
                                localTagDraft = entry.localTags.joinToString("，")
                                navController.navigate(Routes.localBook(entry.book.identity))
                            },
                            onRetry = { scope.launch { reloadLibrary() } },
                            onManageCollections = { navController.navigate(Routes.Collections) },
                        )
                    }
                    composable(Routes.Collections) {
                        CollectionManagerScreen(
                            collections = collections,
                            message = collectionMessage,
                            onCreateManual = { title ->
                                scope.launch {
                                    runCatching {
                                        val now = Instant.now()
                                        application.libraryRepository.createCollection(
                                            LibraryCollection(UUID.randomUUID().toString(), CollectionKind.MANUAL, title.trim(), null, collections.size.toLong(), now, now),
                                        )
                                        reloadLibrary()
                                    }.onSuccess {
                                        collectionMessage = resources.getString(R.string.collection_saved)
                                    }.onFailure {
                                        collectionMessage = resources.getString(R.string.collection_invalid)
                                    }
                                }
                            },
                            onCreateSmart = { title, matchAll, drafts ->
                                scope.launch {
                                    runCatching {
                                        val now = Instant.now()
                                        application.libraryRepository.createSmartCollection(
                                            LibraryCollection(UUID.randomUUID().toString(), CollectionKind.SMART, title.trim(), null, collections.size.toLong(), now, now),
                                            buildSmartRule(matchAll, drafts),
                                        )
                                        reloadLibrary()
                                    }.onSuccess {
                                        collectionMessage = resources.getString(R.string.collection_saved)
                                    }.onFailure {
                                        collectionMessage = resources.getString(R.string.collection_invalid)
                                    }
                                }
                            },
                            onDelete = { collection ->
                                scope.launch {
                                    application.libraryRepository.deleteCollection(collection.collectionId)
                                    reloadLibrary()
                                    collectionMessage = resources.getString(R.string.collection_deleted)
                                }
                            },
                        )
                    }
                    composable(Routes.LocalBook) { backStackEntry ->
                        val sourceId = backStackEntry.arguments?.getString("sourceId")
                        val remoteBookId = backStackEntry.arguments?.getString("remoteBookId")
                        var resolved by remember(sourceId, remoteBookId) { mutableStateOf(false) }
                        LaunchedEffect(sourceId, remoteBookId) {
                            val identity = if (sourceId != null && remoteBookId != null) BookIdentity(sourceId, remoteBookId) else null
                            selectedLocalEntry = identity?.let { key ->
                                application.libraryRepository.libraryEntries().firstOrNull { it.book.identity == key }
                            }
                            selectedLocalEntry?.let { localTagDraft = it.localTags.joinToString("，") }
                            resolved = true
                        }
                        val entry = selectedLocalEntry
                        if (entry == null) {
                            StateView(
                                kind = if (resolved) TsuyomiStateKind.EMPTY else TsuyomiStateKind.LOADING,
                                title = stringResource(if (resolved) R.string.local_book_missing else R.string.local_book_loading),
                            )
                        } else {
                            LocalBookDetailsScreen(
                                entry = entry,
                                tagDraft = localTagDraft,
                                onTagDraftChange = { localTagDraft = it },
                                onSaveTags = {
                                    scope.launch {
                                        application.libraryRepository.setLocalTags(entry.book.identity, localTagDraft.split(',', '，'))
                                        reloadLibrary()
                                        selectedLocalEntry = libraryState.entries.firstOrNull { it.book.identity == entry.book.identity }
                                    }
                                },
                                onSetRating = { rating ->
                                    scope.launch {
                                        application.libraryRepository.setRating(entry.book.identity, rating)
                                        reloadLibrary()
                                        selectedLocalEntry = libraryState.entries.firstOrNull { it.book.identity == entry.book.identity }
                                    }
                                },
                                onOpenSource = { selectRoot(Routes.Browse) },
                                onRemove = {
                                    scope.launch {
                                        application.libraryRepository.removeFromLibrary(entry.book.identity)
                                        reloadLibrary()
                                        navController.navigateUp()
                                    }
                                },
                            )
                        }
                    }
                    composable(Routes.Browse) {
                        BrowseScreen(
                            state = sourceInstaller.state,
                            onRequestImport = { extensionPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                            onOpenInstalledSource = {
                                sourceInstaller.activePackage?.let { packageInfo ->
                                    scope.launch {
                                        sourceFlow.open(packageInfo)
                                        navController.navigate(Routes.Search)
                                    }
                                }
                            },
                            remoteLibraryAvailable = sourceInstaller.activePackage?.manifest?.capabilities?.remoteLibrary?.policies?.containsKey(org.tsuyomi.source.extensionmanager.RemoteOperation.READ) == true,
                            onOpenRemoteLibrary = {
                                sourceInstaller.activePackage?.let { packageInfo ->
                                    scope.launch {
                                        sourceFlow.open(packageInfo)
                                        val remotePolicy = sourceInstaller.remotePolicy()
                                        remoteWritebackEnabled = remotePolicy?.addWritebackEnabled == true
                                        remoteWritebackAvailable = sourceInstaller.remoteAddCredentialReady()
                                        remoteLibraryMessage = null
                                        navController.navigate(Routes.RemoteLibrary)
                                    }
                                }
                            },
                            onApproveInstall = { allowDowngrade -> scope.launch { sourceInstaller.approve(allowDowngrade) } },
                            onDismissApproval = sourceInstaller::dismissApproval,
                            onDismissFailure = sourceInstaller::dismissFailure,
                        )
                    }
                    composable(Routes.RemoteLibrary) {
                        RemoteLibraryScreen(
                            books = sourceFlow.remoteLibraryBooks,
                            loading = remoteLibraryLoading,
                            message = remoteLibraryMessage,
                            writebackAvailable = remoteWritebackAvailable,
                            writebackEnabled = remoteWritebackEnabled,
                            onPull = { scope.launch { performRemotePull() } },
                            onWritebackChanged = { enabled ->
                                scope.launch {
                                    val updated = sourceInstaller.setRemoteAddWritebackEnabled(enabled)
                                    val actualPolicy = sourceInstaller.remotePolicy()
                                    remoteWritebackEnabled = if (updated) enabled else actualPolicy?.addWritebackEnabled == true
                                    remoteWritebackAvailable = sourceInstaller.remoteAddCredentialReady()
                                    remoteLibraryMessage = resources.getString(
                                        if (remoteWritebackEnabled) R.string.remote_writeback_enabled else R.string.remote_writeback_disabled,
                                    )
                                }
                            },
                            onOpenBook = { book ->
                                scope.launch {
                                    sourceFlow.selectBook(book)
                                    navController.navigate(Routes.Detail)
                                }
                            },
                        )
                    }
                    composable(Routes.Search) {
                        SearchScreen(
                            query = sourceFlow.query,
                            state = sourceFlow.searchState,
                            onQueryChange = sourceFlow::updateQuery,
                            onSearch = { scope.launch { sourceFlow.search() } },
                            onSelectBook = { book ->
                                scope.launch {
                                    sourceFlow.selectBook(book)
                                    navController.navigate(Routes.Detail)
                                }
                            },
                            onRetry = { scope.launch { sourceFlow.search() } },
                            onUseOfflineCache = { scope.launch { sourceFlow.search(offlineOnly = true) } },
                            onOpenVerification = { navController.navigate(Routes.Verification) },
                        )
                    }
                    composable(Routes.Detail) {
                        BookDetailScreen(
                            state = sourceFlow.detailState,
                            inLibrary = sourceFlow.selectedBookInLibrary,
                            addWritesRemote = sourceFlow.selectedBookAddWritesRemote,
                            reconciliationLabel = sourceFlow.selectedBookReconciliation?.let { state ->
                                stringResource(
                                    when (state) {
                                        RemoteReconciliationState.PENDING_USER_ACTION, RemoteReconciliationState.IN_FLIGHT -> R.string.remote_add_pending
                                        RemoteReconciliationState.CONFIRMED -> R.string.remote_add_confirmed
                                        RemoteReconciliationState.UNRESOLVED -> R.string.remote_add_unresolved
                                        RemoteReconciliationState.CANCELLED -> R.string.remote_add_cancelled
                                    },
                                )
                            },
                            onAddToLibrary = { scope.launch { sourceFlow.addSelectedBook(); reloadLibrary() } },
                            onRemoveFromLibrary = { scope.launch { sourceFlow.removeSelectedBook(); reloadLibrary() } },
                            onOpenDirectory = {
                                scope.launch {
                                    sourceFlow.loadDirectory()
                                    navController.navigate(Routes.Directory)
                                }
                            },
                            onRetry = { scope.launch { sourceFlow.reloadDetail(offlineOnly = false) } },
                            onUseOfflineCache = { scope.launch { sourceFlow.reloadDetail(offlineOnly = true) } },
                            onOpenVerification = { navController.navigate(Routes.Verification) },
                        )
                    }
                    composable(Routes.Directory) {
                        BookDirectoryScreen(
                            state = sourceFlow.directoryState,
                            onSelectChapter = { chapter ->
                                scope.launch {
                                    sourceFlow.selectChapter(chapter)
                                    navController.navigate(Routes.Reader)
                                }
                            },
                            onRetry = { scope.launch { sourceFlow.loadDirectory() } },
                            onUseOfflineCache = { scope.launch { sourceFlow.loadDirectory(offlineOnly = true) } },
                            onOpenVerification = { navController.navigate(Routes.Verification) },
                        )
                    }
                    composable(Routes.Reader) {
                        ReaderScreen(
                            document = sourceFlow.readerDocument,
                            loading = sourceFlow.readerLoading,
                            failure = sourceFlow.readerFailure,
                            restoredLocator = sourceFlow.restoredLocator,
                            onLocatorChanged = { locator, precision -> scope.launch { sourceFlow.saveProgress(locator, precision) } },
                            preferences = readerPreferences,
                            onRetry = { scope.launch { sourceFlow.reloadChapter(offlineOnly = false) } },
                            onUseOfflineCache = { scope.launch { sourceFlow.reloadChapter(offlineOnly = true) } },
                            onOpenVerification = { navController.navigate(Routes.Verification) },
                        )
                    }
                    composable(Routes.Verification) {
                        sourceInstaller.activePackage?.let { packageInfo ->
                            ManualVerificationRoute(
                                packageInfo = packageInfo,
                                onCompleted = {
                                    scope.launch {
                                        sourceFlow.reopenWithStoredCredentials()
                                        val remotePolicy = sourceInstaller.remotePolicy()
                                        remoteWritebackEnabled = remotePolicy?.addWritebackEnabled == true
                                        remoteWritebackAvailable = sourceInstaller.remoteAddCredentialReady()
                                        showPostLoginImportPrompt = sourceInstaller.consumeFirstRemoteImportPrompt()
                                        navController.navigateUp()
                                    }
                                },
                                onCancel = { navController.navigateUp() },
                            )
                        }
                    }
                    composable(Routes.More) {
                        MoreScreen(
                            onOpenDisplaySettings = { navController.navigate(Routes.Display) },
                            onOpenAbout = { navController.navigate(Routes.About) },
                            onOpenDataTransfer = { navController.navigate(Routes.Transfer) },
                        )
                    }
                    composable(Routes.Display) {
                        DisplaySettingsRoute(environment, controller)
                    }
                    composable(Routes.Transfer) {
                        TransferScreen(
                            state = transferCoordinator.state,
                            onChooseImport = { transferImportPicker.launch(arrayOf("application/json", "application/octet-stream")) },
                            onConfirmImport = { scope.launch { transferCoordinator.confirmImport(); reloadLibrary() } },
                            onCancelImport = transferCoordinator::cancelReview,
                            onExport = {
                                if (preparedExportGeneration == null && preparedExportDigest == null) {
                                    scope.launch {
                                        transferCoordinator.prepareExport(readerPreferences)?.let { prepared ->
                                            preparedExportGeneration = prepared.ownerGeneration
                                            preparedExportDigest = prepared.canonicalDigest
                                            transferExportPicker.launch(prepared.suggestedFileName)
                                        }
                                    }
                                }
                            },
                            onDismissResult = transferCoordinator::dismissResult,
                            onRetryRecovery = { scope.launch { transferCoordinator.retryRecovery() } },
                            onAbortRecovery = { scope.launch { transferCoordinator.abortRecovery() } }
                        )
                    }
                    composable(Routes.About) {
                        AboutScreen(
                            applicationName = stringResource(R.string.app_name),
                            versionName = BuildConfig.VERSION_NAME,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplaySettingsRoute(
    environment: DisplayEnvironment,
    controller: DisplayController,
) {
    val scope = rememberCoroutineScope()
    var writeGenerations by rememberSaveable { mutableStateOf(emptyMap<String, Long>()) }
    var failedWrites by rememberSaveable { mutableStateOf(emptyMap<String, String>()) }
    var failureSequence by rememberSaveable { mutableIntStateOf(0) }

    fun submit(key: String, write: suspend () -> Unit) {
        val field = key.substringBefore(':')
        val generation = (writeGenerations[field] ?: 0L) + 1L
        check(generation > 0L) { "Display write generation exhausted for $field" }
        writeGenerations = writeGenerations + (field to generation)

        scope.launch {
            try {
                write()
                if (writeGenerations[field] == generation) {
                    failedWrites = failedWrites - field
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (writeGenerations[field] == generation) {
                    failedWrites = failedWrites + (field to key)
                    failureSequence += 1
                }
            }
        }
    }

    fun retry() {
        val key = failedWrites.values.firstOrNull() ?: return
        when {
            key.startsWith("profile:") -> submit(key) {
                controller.setDisplayPreference(DisplayPreference.valueOf(key.substringAfter(':')))
            }
            key.startsWith("scheme:") -> submit(key) {
                controller.setColorSchemePreference(
                    ColorSchemePreference.valueOf(key.substringAfter(':')),
                )
            }
            key.startsWith("dynamic:") -> submit(key) {
                controller.setDynamicColorEnabled(key.substringAfter(':').toBooleanStrict())
            }
        }
    }

    DisplaySettingsScreen(
        state = DisplaySettingsUiState(
            environment = environment,
            writeFailure = failedWrites.values.firstOrNull()?.let {
                DisplayWriteFailure(failureSequence)
            },
        ),
        actions = DisplaySettingsActions(
            onDisplayPreferenceChange = { preference ->
                submit("profile:${preference.name}") {
                    controller.setDisplayPreference(preference)
                }
            },
            onColorSchemePreferenceChange = { preference ->
                submit("scheme:${preference.name}") {
                    controller.setColorSchemePreference(preference)
                }
            },
            onDynamicColorEnabledChange = { enabled ->
                submit("dynamic:$enabled") {
                    controller.setDynamicColorEnabled(enabled)
                }
            },
            onRefreshNow = controller::requestRedraw,
            onRetryWrite = ::retry,
            onAcknowledgeWriteFailure = {
                val field = failedWrites.keys.firstOrNull()
                if (field != null) {
                    failedWrites = failedWrites - field
                    if (failedWrites.isNotEmpty()) failureSequence += 1
                }
            },
        ),
    )
}

@Composable
private fun SystemBarPolicy(environment: DisplayEnvironment) {
    val activity = LocalActivity.current ?: return
    val background = MaterialTheme.colorScheme.background.toArgb()
    val lightIcons = environment.effectiveDarkTheme
    SideEffect {
        activity.window.statusBarColor = background
        activity.window.navigationBarColor = background
        activity.window.isNavigationBarContrastEnforced = false
        activity.window.isStatusBarContrastEnforced = false
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
        }
    }
}
