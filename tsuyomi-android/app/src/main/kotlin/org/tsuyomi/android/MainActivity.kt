/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.graphics.Color as AndroidColor
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
import org.tsuyomi.feature.library.LibraryScreen
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
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: Routes.Library
    val selectedRoot = when (currentRoute) {
        Routes.Display, Routes.About -> Routes.More
        else -> currentRoute
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
                        BrowseScreen()
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
