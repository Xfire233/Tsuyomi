/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.feature.library.UpdateSettingsScreen
import org.tsuyomi.shared.librarydomain.UpdateSnapshot
import org.tsuyomi.shared.model.BookIdentity

/** The only update route: settings and bounded durable scan report. */
internal fun NavGraphBuilder.updateSettingsRoute(
    application: TsuyomiApplication,
    libraryFlow: LibraryFlowController,
) {
    composable(Routes.UpdateSettings) {
        val context = LocalContext.current
        var notificationStateRevision by rememberSaveable { mutableIntStateOf(0) }
        var permissionRequested by rememberSaveable { mutableStateOf(false) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationStateRevision++ }
        val notificationControl = remember(context, notificationStateRevision, permissionRequested) {
            updateNotificationControl(context, permissionRequested)
        }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            permissionRequested = true
            notificationStateRevision++
        }
        val notificationAction = when (notificationControl?.kind) {
            UpdateNotificationControlKind.REQUEST_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                } else {
                    null
                }
            }
            UpdateNotificationControlKind.OPEN_SETTINGS -> {
                { context.startActivity(updateNotificationSettingsIntent(context)) }
            }
            null -> null
        }
        val snapshot by rememberUpdateSnapshot(application)
        if (snapshot == null) {
            StateView(kind = TsuyomiStateKind.LOADING, title = androidx.compose.ui.res.stringResource(R.string.updates_loading_settings))
        } else {
            val currentSnapshot = requireNotNull(snapshot)
            val bookLabels = buildMap {
                libraryFlow.searchableEntries.forEach { entry -> put(entry.book.identity, entry.book.title) }
                libraryFlow.updatePresentationBooks.forEach { (identity, book) -> putIfAbsent(identity, book.title) }
                currentSnapshot.updates.forEach { update -> putIfAbsent(update.identity, update.title) }
            }
            val knownSourceIds = buildSet {
                addAll(libraryFlow.searchableEntries.map { item -> item.book.identity.sourceId })
                addAll(libraryFlow.updatePresentationBooks.keys.map(BookIdentity::sourceId))
                addAll(libraryFlow.state.mirrorShortcuts.map { mirror -> mirror.sourceId })
                addAll(currentSnapshot.updates.map { update -> update.identity.sourceId })
                addAll(currentSnapshot.sessionItems.map { item -> item.identity.sourceId })
                addAll(currentSnapshot.excludedSources)
            }
            UpdateSettingsScreen(
                snapshot = currentSnapshot,
                knownSourceIds = knownSourceIds,
                bookLabels = bookLabels,
                onSetPolicy = application.updateScheduler::setPolicy,
                onExcludeBook = application.updateCoordinator::excludeBook,
                onExcludeSource = application.updateCoordinator::excludeSource,
                onLoadMoreSessionItems = application.updateCoordinator::loadMoreSessionItems,
                notificationsUnavailable = notificationControl != null,
                notificationPermissionRequestable = notificationControl?.kind == UpdateNotificationControlKind.REQUEST_PERMISSION,
                onRequestNotifications = notificationAction,
            )
        }
    }
}

@Composable
private fun rememberUpdateSnapshot(application: TsuyomiApplication) = produceState<UpdateSnapshot?>(
    initialValue = null,
    application.updateCoordinator,
) {
    application.updateCoordinator.snapshots.collect { value = it }
}

private enum class UpdateNotificationControlKind {
    REQUEST_PERMISSION,
    OPEN_SETTINGS,
}

private data class UpdateNotificationControl(val kind: UpdateNotificationControlKind)

private fun updateNotificationControl(context: Context, permissionRequested: Boolean): UpdateNotificationControl? {
    val notificationsEnabled = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return UpdateNotificationControl(
            if (!permissionRequested) UpdateNotificationControlKind.REQUEST_PERMISSION else UpdateNotificationControlKind.OPEN_SETTINGS,
        )
    }
    return UpdateNotificationControl(UpdateNotificationControlKind.OPEN_SETTINGS).takeIf { !notificationsEnabled }
}

private fun updateNotificationSettingsIntent(context: Context): Intent = Intent(
    Settings.ACTION_APP_NOTIFICATION_SETTINGS,
).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
