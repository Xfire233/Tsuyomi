/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import java.time.Instant
import kotlinx.coroutines.launch
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.database.RemoteReconciliationState
import org.tsuyomi.source.extensionmanager.RemoteOperation

@Stable
internal class SourceRouteUiState {
    var remoteLibraryLoading by mutableStateOf(false)
        private set
    var postLoginImportPromptVisible by mutableStateOf(false)
        private set
    var writebackConfirmationVisible by mutableStateOf(false)
        private set
    var remoteLibraryMessage by mutableStateOf<String?>(null)
        private set
    var remoteWritebackEnabled by mutableStateOf(false)
        private set
    var remoteWritebackAvailable by mutableStateOf(false)
        private set

    fun beginRemotePull() {
        remoteLibraryLoading = true
    }

    fun finishRemotePull(message: String) {
        remoteLibraryMessage = message
        remoteLibraryLoading = false
    }

    fun clearRemoteLibraryMessage() {
        remoteLibraryMessage = null
    }

    fun showPostLoginImportPrompt() {
        postLoginImportPromptVisible = true
    }

    fun dismissPostLoginImportPrompt() {
        postLoginImportPromptVisible = false
    }

    fun showWritebackConfirmation() {
        writebackConfirmationVisible = true
    }

    fun dismissWritebackConfirmation() {
        writebackConfirmationVisible = false
    }

    fun updateWriteback(enabled: Boolean, available: Boolean, message: String? = remoteLibraryMessage) {
        remoteWritebackEnabled = enabled
        remoteWritebackAvailable = available
        remoteLibraryMessage = message
    }
}

@Stable
internal class SourceRouteOwner(
    val installer: SourceInstallController,
    val flow: SourceFlowController,
    val ui: SourceRouteUiState,
    private val resources: Resources,
    private val navController: NavHostController,
    private val requestImportAction: () -> Unit,
    private val onLibraryChanged: suspend () -> Unit,
) {
    val remoteLibraryAvailable: Boolean
        get() = installer.activePackage?.manifest?.capabilities?.remoteLibrary?.policies
            ?.containsKey(RemoteOperation.READ) == true

    val localRemoteSync = LocalRemoteSyncActions(
        sourceRevision = installer.activePackage?.packageSha256,
        canRetry = ::canRetryLocalRemote,
        retry = ::retryLocalRemote,
        openSource = { navController.selectRoot(Routes.Browse) },
    )

    fun requestImport() {
        requestImportAction()
    }
    fun navigateToRemoteLibrary() {
        navController.navigate(Routes.RemoteLibrary)
    }


    suspend fun openInstalledSource() {
        installer.activePackage?.let { packageInfo ->
            flow.open(packageInfo)
            navController.navigate(Routes.Search)
        }
    }

    suspend fun openRemoteLibrary() {
        installer.activePackage?.let { packageInfo ->
            flow.open(packageInfo)
            refreshRemotePolicy()
            ui.clearRemoteLibraryMessage()
            navController.navigate(Routes.RemoteLibrary)
        }
    }

    suspend fun pullRemoteLibrary() {
        val packageInfo = installer.activePackage ?: return
        ui.beginRemotePull()
        val message = when (val result = flow.pullRemoteLibrary(packageInfo, Instant.now())) {
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
        ui.finishRemotePull(message)
        onLibraryChanged()
    }

    fun requestWritebackChange(enabled: Boolean) {
        if (enabled) ui.showWritebackConfirmation()
    }

    suspend fun disableWriteback() {
        installer.setRemoteAddWritebackEnabled(false)
        ui.updateWriteback(
            enabled = false,
            available = installer.remoteAddCredentialReady(),
            message = resources.getString(R.string.remote_writeback_disabled),
        )
    }

    suspend fun enableWriteback() {
        val updated = installer.setRemoteAddWritebackEnabled(true)
        val actualPolicy = installer.remotePolicy()
        val enabled = updated || actualPolicy?.addWritebackEnabled == true
        ui.updateWriteback(
            enabled = enabled,
            available = installer.remoteAddCredentialReady(),
            message = resources.getString(
                if (enabled) R.string.remote_writeback_enabled else R.string.remote_writeback_disabled,
            ),
        )
    }

    suspend fun retrySelectedBookRemoteAdd() {
        flow.retrySelectedBookRemoteAdd()
        onLibraryChanged()
    }

    suspend fun addSelectedBook() {
        flow.addSelectedBook()
        onLibraryChanged()
    }

    suspend fun removeSelectedBook() {
        flow.removeSelectedBook()
        onLibraryChanged()
    }

    suspend fun completeVerification() {
        flow.reopenWithStoredCredentials()
        refreshRemotePolicy()
        if (installer.consumeFirstRemoteImportPrompt()) ui.showPostLoginImportPrompt()
        navController.navigateUp()
    }

    suspend fun refreshRemotePolicy() {
        val policy = installer.remotePolicy()
        ui.updateWriteback(
            enabled = policy?.addWritebackEnabled == true,
            available = installer.remoteAddCredentialReady(),
        )
    }

    private suspend fun canRetryLocalRemote(entry: LibraryEntry?): Boolean {
        val current = entry ?: return false
        if (current.reconciliation !in setOf(RemoteReconciliationState.UNRESOLVED, RemoteReconciliationState.CANCELLED)) {
            return false
        }
        val packageInfo = installer.activePackage
        val remotePolicy = installer.remotePolicy()
        return current.sourceAvailable &&
            packageInfo?.manifest?.sourceId?.value == current.book.identity.sourceId &&
            remotePolicy?.addWritebackEnabled == true &&
            installer.remoteAddCredentialReady()
    }

    private suspend fun retryLocalRemote(entry: LibraryEntry): RemoteAddUiResult {
        val packageInfo = installer.activePackage
        return if (packageInfo?.manifest?.sourceId?.value == entry.book.identity.sourceId) {
            flow.open(packageInfo)
            flow.remoteLibrary.retryLocalBook(entry.book)
        } else {
            RemoteAddUiResult.Failure("remote-add-source-not-open")
        }
    }
}

@Composable
internal fun rememberSourceRouteOwner(
    application: TsuyomiApplication,
    navController: NavHostController,
    currentEntry: NavBackStackEntry?,
    currentRoute: String,
    onLibraryChanged: suspend () -> Unit,
): SourceRouteOwner {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val installer = remember {
        SourceInstallController(context.applicationContext, application.libraryRepository)
    }
    val ui = remember { SourceRouteUiState() }
    val extensionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { document -> scope.launch { installer.prepare(document, context.contentResolver) } }
    }
    LaunchedEffect(installer) { installer.restoreInstalled() }

    val ownsSourceFlow = routeOwnsSourceFlow(currentRoute)
    val sourceFlowEntry = remember(currentEntry) {
        if (ownsSourceFlow) navController.getBackStackEntry(Routes.Browse) else null
    }
    val flow = remember(sourceFlowEntry) {
        SourceFlowController(
            context.applicationContext,
            application.libraryRepository,
            SourceFlowSnapshotStore(application.preferencesDataStore),
        )
    }
    DisposableEffect(flow) {
        onDispose(flow::close)
    }

    val owner = SourceRouteOwner(
        installer = installer,
        flow = flow,
        ui = ui,
        resources = resources,
        navController = navController,
        requestImportAction = { extensionPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
        onLibraryChanged = onLibraryChanged,
    )
    LaunchedEffect(installer.activePackage, currentRoute) {
        val packageInfo = installer.activePackage ?: return@LaunchedEffect
        restorationTargetForRoute(currentRoute)?.let { flow.restoreFor(it, packageInfo) }
        if (currentRoute == Routes.RemoteLibrary) owner.refreshRemotePolicy()
    }
    return owner
}

@Composable
internal fun SourceRouteDialogs(owner: SourceRouteOwner, currentRoute: String) {
    val scope = rememberCoroutineScope()
    if (owner.ui.postLoginImportPromptVisible) {
        AlertDialog(
            onDismissRequest = owner.ui::dismissPostLoginImportPrompt,
            title = { Text(stringResource(R.string.post_login_import_title)) },
            text = { Text(stringResource(R.string.post_login_import_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        owner.ui.dismissPostLoginImportPrompt()
                        if (currentRoute != Routes.RemoteLibrary) owner.navigateToRemoteLibrary()
                        scope.launch { owner.pullRemoteLibrary() }
                    },
                ) { Text(stringResource(R.string.post_login_import_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = owner.ui::dismissPostLoginImportPrompt) {
                    Text(stringResource(R.string.post_login_import_later))
                }
            },
        )
    }
    if (owner.ui.writebackConfirmationVisible) {
        val sourceName = owner.installer.activePackage?.manifest?.displayName
            ?: owner.installer.activePackage?.manifest?.sourceId?.value.orEmpty()
        AlertDialog(
            onDismissRequest = owner.ui::dismissWritebackConfirmation,
            title = { Text(stringResource(R.string.remote_writeback_confirm_title, sourceName)) },
            text = { Text(stringResource(R.string.remote_writeback_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        owner.ui.dismissWritebackConfirmation()
                        scope.launch { owner.enableWriteback() }
                    },
                ) { Text(stringResource(R.string.remote_writeback_confirm_enable)) }
            },
            dismissButton = {
                TextButton(onClick = owner.ui::dismissWritebackConfirmation) {
                    Text(stringResource(R.string.remote_writeback_confirm_cancel))
                }
            },
        )
    }
}
