/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.StateView

sealed interface BrowseUiState {
    data object Empty : BrowseUiState
    data class Preparing(val fileName: String) : BrowseUiState
    data class Approval(
        val sourceName: String,
        val sourceId: String,
        val version: String,
        val publisherFingerprint: String,
        val capabilities: List<String>,
        val isDowngrade: Boolean,
    ) : BrowseUiState
    data class Installed(val sourceName: String, val version: String) : BrowseUiState
    data class Failure(val reason: BrowseInstallFailure) : BrowseUiState
}

enum class BrowseInstallFailure {
    FILE_ACCESS,
    VERIFICATION,
    INSTALL,
    EXPIRED_APPROVAL,
}

/** A local-file import entry whose explicit approval is bound to the verified HXP candidate. */
@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onRequestImport: () -> Unit,
    onOpenInstalledSource: () -> Unit,
    onApproveInstall: (allowDowngrade: Boolean) -> Unit,
    onDismissApproval: () -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        BrowseUiState.Empty -> EmptySourceScreen(onRequestImport, modifier)
        is BrowseUiState.Preparing -> StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.browse_preparing_title),
            message = stringResource(R.string.browse_preparing_message, state.fileName),
            modifier = modifier,
        )
        is BrowseUiState.Installed -> InstalledSourceScreen(state, onOpenInstalledSource, onRequestImport, modifier)
        is BrowseUiState.Failure -> FailureSourceScreen(state, onRequestImport, onDismissFailure, modifier)
        is BrowseUiState.Approval -> SourceApprovalScreen(state, onApproveInstall, onDismissApproval, modifier)
    }
}

@Composable
private fun EmptySourceScreen(onRequestImport: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.browse_empty_title))
        Text(
            text = stringResource(R.string.browse_empty_message),
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        TsuyomiButton(
            text = stringResource(R.string.browse_import_action),
            onClick = onRequestImport,
            style = TsuyomiButtonStyle.PRIMARY,
        )
    }
}

@Composable
private fun InstalledSourceScreen(
    state: BrowseUiState.Installed,
    onOpenSource: () -> Unit,
    onRequestImport: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.browse_installed_title, state.sourceName))
        Text(
            text = stringResource(R.string.browse_installed_message, state.version),
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        TsuyomiButton(
            text = stringResource(R.string.browse_open_source_action),
            onClick = onOpenSource,
            style = TsuyomiButtonStyle.PRIMARY,
        )
        TsuyomiButton(
            text = stringResource(R.string.browse_import_another_action),
            onClick = onRequestImport,
            modifier = Modifier.padding(top = 8.dp),
            style = TsuyomiButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun FailureSourceScreen(
    state: BrowseUiState.Failure,
    onRequestImport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.browse_install_failed_title))
        Text(
            text = stringResource(
                when (state.reason) {
                    BrowseInstallFailure.FILE_ACCESS -> R.string.browse_failure_file_access
                    BrowseInstallFailure.VERIFICATION -> R.string.browse_failure_verification
                    BrowseInstallFailure.INSTALL -> R.string.browse_failure_install
                    BrowseInstallFailure.EXPIRED_APPROVAL -> R.string.browse_failure_expired_approval
                },
            ),
            modifier = Modifier.padding(vertical = 12.dp),
        )
        TsuyomiButton(
            text = stringResource(R.string.browse_try_again_action),
            onClick = onRequestImport,
            style = TsuyomiButtonStyle.PRIMARY,
        )
        TsuyomiButton(
            text = stringResource(R.string.browse_dismiss_action),
            onClick = onDismiss,
            modifier = Modifier.padding(top = 8.dp),
            style = TsuyomiButtonStyle.TEXT,
        )
    }
}

@Composable
private fun SourceApprovalScreen(
    state: BrowseUiState.Approval,
    onApprove: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    var downgradeConfirmed by remember(state.sourceId, state.version) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 24.dp, vertical = 20.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.browse_approval_title, state.sourceName))
        Text(text = stringResource(R.string.browse_approval_identity, state.sourceId, state.version))
        Text(text = stringResource(R.string.browse_approval_publisher, state.publisherFingerprint))
        HorizontalDivider()
        Text(text = stringResource(R.string.browse_approval_capabilities))
        if (state.capabilities.isEmpty()) {
            Text(text = stringResource(R.string.browse_approval_no_new_capabilities))
        } else {
            state.capabilities.forEach { capability -> Text(text = "• $capability") }
        }
        if (state.isDowngrade) {
            CheckboxRow(
                checked = downgradeConfirmed,
                onCheckedChange = { downgradeConfirmed = it },
                label = stringResource(R.string.browse_approval_downgrade),
            )
        }
        TsuyomiButton(
            text = stringResource(R.string.browse_approval_install_action),
            onClick = { onApprove(downgradeConfirmed) },
            enabled = !state.isDowngrade || downgradeConfirmed,
            modifier = Modifier.fillMaxWidth(),
            style = TsuyomiButtonStyle.PRIMARY,
        )
        TsuyomiButton(
            text = stringResource(R.string.browse_cancel_action),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            style = TsuyomiButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun CheckboxRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label)
    }
}
