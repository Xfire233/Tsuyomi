/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.graphics.Color as AndroidColor
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.display.DisplayController
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayEnvironmentResolver
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.displayRedrawLayer
import org.tsuyomi.core.ui.components.AppScaffold
import org.tsuyomi.core.ui.components.TsuyomiNavigation
import org.tsuyomi.core.ui.components.TsuyomiNavigationItem
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.layout.TsuyomiWindowSize
import org.tsuyomi.core.ui.theme.TsuyomiBootScreen
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.feature.browse.BrowseScreen
import org.tsuyomi.feature.book.BookDetailScreen
import org.tsuyomi.feature.book.BookDirectoryScreen
import org.tsuyomi.feature.library.LibraryScreen
import org.tsuyomi.feature.reader.ReaderScreen
import org.tsuyomi.feature.search.SearchScreen
import org.tsuyomi.feature.settings.AboutScreen
import org.tsuyomi.feature.settings.DisplaySettingsActions
import org.tsuyomi.feature.settings.DisplaySettingsScreen
import org.tsuyomi.feature.settings.DisplaySettingsUiState
import org.tsuyomi.feature.settings.DisplayWriteFailure
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.feature.settings.MoreScreen

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
    const val More = "more"
    const val Display = "settings/display"
    const val About = "about"
    const val Search = "source/search"
    const val Detail = "source/detail"
    const val Directory = "source/directory"
    const val Reader = "source/reader"
    const val Verification = "source/verification"
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
    val scope = rememberCoroutineScope()
    val sourceInstaller = remember { SourceInstallController(context.applicationContext) }
    val application = context.applicationContext as TsuyomiApplication
    val sourceFlow = remember {
        SourceFlowController(
            context.applicationContext,
            application.libraryRepository,
            SourceFlowSnapshotStore(application.preferencesDataStore),
        )
    }
    val extensionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { document -> scope.launch { sourceInstaller.prepare(document, context.contentResolver) } }
    }
    LaunchedEffect(sourceInstaller) { sourceInstaller.restoreInstalled() }
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: Routes.Library
    val selectedRoot = when (currentRoute) {
        Routes.Display, Routes.About -> Routes.More
        Routes.Search, Routes.Detail, Routes.Directory, Routes.Reader, Routes.Verification -> Routes.Browse
        else -> currentRoute
    }
    LaunchedEffect(sourceInstaller.activePackage, currentRoute) {
        val packageInfo = sourceInstaller.activePackage ?: return@LaunchedEffect
        val target = when (currentRoute) {
            Routes.Search -> SourceRestorationTarget.SEARCH
            Routes.Detail -> SourceRestorationTarget.DETAIL
            Routes.Directory -> SourceRestorationTarget.DIRECTORY
            Routes.Reader -> SourceRestorationTarget.READER
            else -> null
        }
        target?.let { sourceFlow.restoreFor(it, packageInfo) }
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
        Routes.Browse -> stringResource(R.string.nav_browse)
        Routes.More -> stringResource(R.string.nav_more)
        Routes.Display -> stringResource(R.string.title_display_settings)
        Routes.About -> stringResource(R.string.title_about)
        Routes.Search -> stringResource(R.string.title_source_search)
        Routes.Detail -> stringResource(R.string.title_book_detail)
        Routes.Directory -> stringResource(R.string.title_book_directory)
        Routes.Reader -> stringResource(R.string.title_reader)
        Routes.Verification -> stringResource(R.string.title_verification)
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
                        LibraryScreen(onNavigateToBrowse = { selectRoot(Routes.Browse) })
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
                            onApproveInstall = { allowDowngrade -> scope.launch { sourceInstaller.approve(allowDowngrade) } },
                            onDismissApproval = sourceInstaller::dismissApproval,
                            onDismissFailure = sourceInstaller::dismissFailure,
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
                        )
                    }
                    composable(Routes.Display) {
                        DisplaySettingsRoute(environment, controller)
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
