/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.core.display.DisplayController
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayEnvironmentResolver
import org.tsuyomi.core.display.displayRedrawLayer
import org.tsuyomi.core.ui.components.AppScaffold
import org.tsuyomi.core.ui.components.TsuyomiNavigation
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.layout.TsuyomiWindowSize
import org.tsuyomi.core.ui.theme.TsuyomiBootScreen
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.feature.backup.TransferScreen
import org.tsuyomi.feature.book.BookDetailScreen
import org.tsuyomi.feature.book.BookDirectoryScreen
import org.tsuyomi.feature.browse.BrowseScreen
import org.tsuyomi.feature.browse.RemoteLibraryScreen
import org.tsuyomi.feature.reader.ReaderScreen
import org.tsuyomi.feature.search.SearchScreen
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


internal fun localRemoteRetryUnavailableMessageRes(
    reconciliation: RemoteReconciliationState?,
    enabled: Boolean,
): Int? = if (
    !enabled && reconciliation in setOf(RemoteReconciliationState.UNRESOLVED, RemoteReconciliationState.CANCELLED)
) {
    R.string.local_remote_retry_unavailable
} else {
    null
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

    val libraryFlow = rememberLibraryFlowController(application.libraryRepository)
    suspend fun reloadLibrary() {
        libraryFlow.reload(resources.getString(R.string.library_read_failure_safe))
    }

    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: Routes.Library
    val sourceOwner = rememberSourceRouteOwner(
        application = application,
        navController = navController,
        currentEntry = currentEntry,
        currentRoute = currentRoute,
        onLibraryChanged = ::reloadLibrary,
    )
    LaunchedEffect(currentRoute, libraryFlow.selectedCollectionId, sourceOwner.installer.activePackage) {
        if (currentRoute == Routes.Library || currentRoute == Routes.LocalBook) {
            reloadLibrary()
            libraryFlow.selectedEntry?.book?.identity?.let { libraryFlow.resolveEntry(it) }
        }
    }

    val selectedRoot = rootRouteFor(currentRoute)
    val appNavigationItems = navigationItems()
    val title = routeTitle(currentRoute)
    val isRoot = currentRoute in setOf(Routes.Library, Routes.Browse, Routes.More)
    val settingsDependencies = remember(environment, controller, transferCoordinator, readerPreferences) {
        SettingsRouteDependencies(
            environment = environment,
            displayController = controller,
            transferCoordinator = transferCoordinator,
            readerPreferences = readerPreferences,
        )
    }

    SourceRouteDialogs(sourceOwner, currentRoute)
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
                        items = appNavigationItems,
                        selectedRoute = selectedRoot,
                        onSelect = { navController.selectRoot(it.route) },
                    )
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
                        remoteSync = sourceOwner.localRemoteSync,
                    )
                    sourceRoutes(
                        navController = navController,
                        owner = sourceOwner,
                        readerPreferences = readerPreferences,
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
