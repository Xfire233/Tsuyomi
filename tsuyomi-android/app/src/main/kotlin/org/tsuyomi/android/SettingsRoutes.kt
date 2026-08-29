/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.compose.ui.res.stringResource
import org.tsuyomi.core.display.DisplayController
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.feature.settings.AboutScreen
import org.tsuyomi.feature.settings.MoreScreen
import org.tsuyomi.shared.backup.PortableReaderPreferences

internal data class SettingsRouteDependencies(
    val environment: DisplayEnvironment,
    val displayController: DisplayController,
    val transferCoordinator: TransferCoordinator,
    val readerPreferences: PortableReaderPreferences,
)

internal fun NavGraphBuilder.settingsRoutes(
    navController: NavHostController,
    dependencies: SettingsRouteDependencies,
    onImportConfirmed: suspend () -> Unit,
) {
    composable(Routes.More) {
        MoreScreen(
            onOpenDisplaySettings = { navController.navigate(Routes.Display) },
            onOpenAbout = { navController.navigate(Routes.About) },
            onOpenDataTransfer = { navController.navigate(Routes.Transfer) },
        )
    }
    composable(Routes.Display) {
        DisplaySettingsRoute(dependencies.environment, dependencies.displayController)
    }
    composable(Routes.Transfer) {
        TransferRoute(
            coordinator = dependencies.transferCoordinator,
            readerPreferences = dependencies.readerPreferences,
            onImportConfirmed = onImportConfirmed,
        )
    }
    composable(Routes.About) {
        AboutScreen(
            applicationName = stringResource(R.string.app_name),
            versionName = BuildConfig.VERSION_NAME,
        )
    }
}
