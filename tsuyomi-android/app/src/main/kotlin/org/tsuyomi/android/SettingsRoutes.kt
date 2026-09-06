/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import org.tsuyomi.core.display.DisplayController
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.preferences.FeatureIntroductionPreferences
import org.tsuyomi.feature.settings.AboutScreen
import org.tsuyomi.feature.settings.HelpScreen
import org.tsuyomi.feature.settings.MoreScreen
import org.tsuyomi.feature.settings.ReaderDefaultsScreen
import org.tsuyomi.shared.backup.PortableReaderPreferences

internal data class SettingsRouteDependencies(
    val environment: DisplayEnvironment,
    val displayController: DisplayController,
    val transferCoordinator: TransferCoordinator,
    val readerPreferences: PortableReaderPreferences,
    val application: TsuyomiApplication,
)

internal fun NavGraphBuilder.settingsRoutes(
    navController: NavHostController,
    dependencies: SettingsRouteDependencies,
    onImportConfirmed: suspend () -> Unit,
) {
    composable(Routes.More) {
        MoreScreen(
            onOpenDisplaySettings = { navController.navigate(Routes.Display) },
            onOpenReaderSettings = { navController.navigate(Routes.ReaderSettings) },
            onOpenDataTransfer = { navController.navigate(Routes.Transfer) },
            onOpenHelp = { navController.navigate(Routes.Help) },
            onOpenAbout = { navController.navigate(Routes.About) },
        )
    }
    composable(Routes.Display) {
        DisplaySettingsRoute(
            environment = dependencies.environment,
            controller = dependencies.displayController,
            onResetInterfacePreferences = dependencies.application.interfacePreferencesResetter::resetToConstitutionDefaults,
        )
    }
    composable(Routes.ReaderSettings) {
        val scope = rememberCoroutineScope()
        val preferences by dependencies.application.readerPreferencesRepository.preferences
            .collectAsStateWithLifecycle(initialValue = dependencies.readerPreferences)
        ReaderDefaultsScreen(
            preferences = preferences,
            environment = dependencies.environment,
            onPreferencesChanged = { updated ->
                scope.launch { dependencies.application.readerPreferencesRepository.update(updated) }
            },
        )
    }
    composable(Routes.Transfer) {
        TransferRoute(
            coordinator = dependencies.transferCoordinator,
            readerPreferences = dependencies.readerPreferences,
            onImportConfirmed = onImportConfirmed,
        )
    }
    composable(Routes.Help) {
        val scope = rememberCoroutineScope()
        val preferences by dependencies.application.featureIntroductionPreferencesRepository.preferences
            .collectAsStateWithLifecycle(initialValue = FeatureIntroductionPreferences())
        HelpScreen(
            introductionsEnabled = preferences.enabled,
            seenVersions = preferences.seenVersions,
            onIntroductionsEnabledChanged = { enabled ->
                scope.launch { dependencies.application.featureIntroductionPreferencesRepository.setEnabled(enabled) }
            },
            onIntroductionSeen = { id, version ->
                scope.launch { dependencies.application.featureIntroductionPreferencesRepository.markSeen(id, version) }
            },
            onResetSeenVersions = {
                scope.launch { dependencies.application.featureIntroductionPreferencesRepository.resetSeenVersions() }
            },
            onOpenDisplayReset = { navController.navigate(Routes.Display) },
        )
    }
    composable(Routes.About) {
        val resources = LocalResources.current
        val licenseText = remember(resources) {
            resources.openRawResource(org.tsuyomi.feature.settings.R.raw.apache_license_2_0)
                .bufferedReader()
                .use { it.readText() }
        }
        AboutScreen(
            applicationName = stringResource(R.string.app_name),
            versionName = BuildConfig.VERSION_NAME,
            licenseText = licenseText,
        )
    }
}
