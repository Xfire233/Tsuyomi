/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiOverflowAction
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiTopBarAction
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

sealed interface BrowseUiState {
    data object Empty : BrowseUiState
    data class Preparing(val fileName: String) : BrowseUiState
    data class Approval(
        val sourceName: String,
        val sourceId: String,
        val version: String,
        val publisherFingerprint: String,
        val capabilities: List<String>,
        val resourceLimitIncreases: List<BrowseResourceLimitIncrease>,
        val isDowngrade: Boolean,
    ) : BrowseUiState
    data class Installed(val sourceName: String, val version: String) : BrowseUiState
    data class Failure(val reason: BrowseInstallFailure) : BrowseUiState
}

data class BrowseInstalledSource(
    val sourceId: String,
    val name: String,
    val version: String,
    val summary: String,
    val homeAvailable: Boolean,
    val remoteLibraryAvailable: Boolean,
    val verificationAvailable: Boolean,
)

enum class BrowseInstallFailure {
    FILE_ACCESS,
    VERIFICATION,
    INSTALL,
    EXPIRED_APPROVAL,
}

/** Atlas-root action grammar backed by real source install and navigation callbacks. */
@Composable
fun BrowseTopBar(
    installedSourceCount: Int,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onRefreshSources: () -> Unit,
) {
    TsuyomiTopBar(
        title = stringResource(R.string.browse_topbar_title),
        subtitle = stringResource(R.string.browse_topbar_installed_count, installedSourceCount),
        actions = listOf(
            TsuyomiTopBarAction(
                icon = TsuyomiIcons.Search,
                label = stringResource(R.string.browse_topbar_search),
                onClick = onSearch,
            ),
            TsuyomiTopBarAction(
                icon = TsuyomiIcons.Add,
                label = stringResource(R.string.browse_topbar_import),
                onClick = onImport,
            ),
        ),
        overflow = listOf(
            TsuyomiOverflowAction(
                label = stringResource(R.string.browse_topbar_refresh),
                onClick = onRefreshSources,
                icon = TsuyomiIcons.Refresh,
            ),
        ),
    )
}

/**
 * Production-owned Atlas Browse composition. Standard replaces fixture source rows with the
 * verified active package and the staged install candidate. The previous renderer remains only
 * for the explicitly frozen E-ink profile.
 */
@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onRequestImport: () -> Unit,
    onOpenInstalledSource: () -> Unit,
    onApproveInstall: (allowDowngrade: Boolean) -> Unit,
    onDismissApproval: () -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
    installedSource: BrowseInstalledSource? = null,
    onOpenRemoteLibrary: () -> Unit = {},
    onOpenHome: () -> Unit = {},
    onOpenVerification: () -> Unit = {},
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        FrozenEInkBrowseScreen(
            state = state,
            onRequestImport = onRequestImport,
            onOpenInstalledSource = onOpenInstalledSource,
            onApproveInstall = onApproveInstall,
            onDismissApproval = onDismissApproval,
            onDismissFailure = onDismissFailure,
            modifier = modifier,
            remoteLibraryAvailable = installedSource?.remoteLibraryAvailable == true,
            onOpenRemoteLibrary = onOpenRemoteLibrary,
        )
        return
    }

    StandardAtlasBrowseScreen(
        state = state,
        installedSource = installedSource,
        onRequestImport = onRequestImport,
        onOpenInstalledSource = onOpenInstalledSource,
        onOpenHome = onOpenHome,
        onOpenRemoteLibrary = onOpenRemoteLibrary,
        onOpenVerification = onOpenVerification,
        onApproveInstall = onApproveInstall,
        onDismissApproval = onDismissApproval,
        modifier = modifier,
    )
}

@Composable
private fun StandardAtlasBrowseScreen(
    state: BrowseUiState,
    installedSource: BrowseInstalledSource?,
    onRequestImport: () -> Unit,
    onOpenInstalledSource: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenRemoteLibrary: () -> Unit,
    onOpenVerification: () -> Unit,
    onApproveInstall: (Boolean) -> Unit,
    onDismissApproval: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        InstallMutationBanner(state, onRequestImport)
        val candidate = state as? BrowseUiState.Approval
        when {
            installedSource != null || candidate != null -> Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                installedSource?.let { source ->
                    BrowseSection(stringResource(R.string.browse_section_installed))
                    InstalledSourceCard(
                        source = source,
                        onSearch = onOpenInstalledSource,
                        onOpenHome = onOpenHome,
                        onOpenRemoteLibrary = onOpenRemoteLibrary,
                        onOpenVerification = onOpenVerification,
                    )
                }
                candidate?.let {
                    BrowseSection(stringResource(R.string.browse_section_installable))
                    ApprovalSourceCard(it, onApproveInstall, onDismissApproval)
                }
                Spacer(Modifier.height(TsuyomiSpacing.Lg))
            }
            state is BrowseUiState.Preparing -> StateView(
                kind = TsuyomiStateKind.LOADING,
                title = stringResource(R.string.browse_preparing_title),
                message = stringResource(R.string.browse_preparing_message, state.fileName),
                modifier = Modifier.weight(1f),
            )
            else -> StateView(
                kind = TsuyomiStateKind.EMPTY,
                title = stringResource(R.string.browse_empty_title),
                message = stringResource(R.string.browse_empty_message),
                actionLabel = stringResource(R.string.browse_import_action),
                onAction = onRequestImport,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InstallMutationBanner(state: BrowseUiState, onRetry: () -> Unit) {
    val text = when (state) {
        is BrowseUiState.Preparing -> stringResource(R.string.browse_install_progress, state.fileName)
        is BrowseUiState.Failure -> stringResource(
            R.string.browse_install_failure_banner,
            installFailureMessage(state.reason),
        )
        else -> return
    }
    val failure = state is BrowseUiState.Failure
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (failure) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(TsuyomiSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (failure) {
                TsuyomiButton(
                    text = stringResource(R.string.browse_try_again_action),
                    onClick = onRetry,
                    style = TsuyomiButtonStyle.TEXT,
                )
            }
        }
    }
}

@Composable
private fun BrowseSection(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(
            start = TsuyomiSpacing.Md,
            end = TsuyomiSpacing.Md,
            top = TsuyomiSpacing.Lg,
            bottom = TsuyomiSpacing.Sm,
        ).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun InstalledSourceCard(
    source: BrowseInstalledSource,
    onSearch: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenRemoteLibrary: () -> Unit,
    onOpenVerification: () -> Unit,
) {
    val primaryAction = if (source.homeAvailable) onOpenHome else onSearch
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs)
            .clickable(role = Role.Button, onClick = primaryAction),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(TsuyomiSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TsuyomiIcons.Compass,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Column(Modifier.padding(start = TsuyomiSpacing.Md)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.browse_source_identity, source.sourceId, source.version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                source.summary,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                sourceCapabilityLabel(source),
                modifier = Modifier.padding(top = TsuyomiSpacing.Xs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = TsuyomiSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
            ) {
                TsuyomiButton(
                    text = stringResource(R.string.browse_source_search_action),
                    onClick = onSearch,
                    style = if (source.homeAvailable) TsuyomiButtonStyle.TEXT else TsuyomiButtonStyle.PRIMARY,
                )
                if (source.remoteLibraryAvailable) {
                    TsuyomiButton(
                        text = stringResource(R.string.browse_remote_library_action),
                        onClick = onOpenRemoteLibrary,
                        style = TsuyomiButtonStyle.TEXT,
                    )
                }
                if (source.verificationAvailable) {
                    TsuyomiButton(
                        text = stringResource(R.string.browse_source_verification_action),
                        onClick = onOpenVerification,
                        style = TsuyomiButtonStyle.SECONDARY,
                    )
                }
            }
        }
    }
}

@Composable
private fun sourceCapabilityLabel(source: BrowseInstalledSource): String = buildList {
    add(stringResource(R.string.browse_source_capabilities_search))
    if (source.homeAvailable) add(stringResource(R.string.browse_source_capabilities_home))
    if (source.remoteLibraryAvailable) add(stringResource(R.string.browse_source_capabilities_remote_library))
    if (source.verificationAvailable) add(stringResource(R.string.browse_source_capabilities_verification))
}.joinToString(stringResource(R.string.browse_source_capabilities_separator))

@Composable
private fun ApprovalSourceCard(
    state: BrowseUiState.Approval,
    onApprove: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by rememberSaveable(state.sourceId, state.version) { mutableStateOf(false) }
    var downgradeConfirmed by remember(state.sourceId, state.version) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(TsuyomiSpacing.Md)) {
            Text("${state.sourceName} ${state.version}", style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.browse_install_candidate_summary, state.sourceId),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .heightIn(min = 48.dp)
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (expanded) TsuyomiIcons.Compact else TsuyomiIcons.List,
                    contentDescription = null,
                )
                Text(
                    if (expanded) {
                        stringResource(R.string.browse_install_diff_collapse)
                    } else {
                        stringResource(
                            R.string.browse_install_diff_expand,
                            state.capabilities.size,
                            state.resourceLimitIncreases.size,
                        )
                    },
                    modifier = Modifier.padding(start = TsuyomiSpacing.Sm),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(180)) + fadeIn(tween(120)),
            ) {
                Column {
                    Text(
                        stringResource(R.string.browse_install_publisher),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        state.publisherFingerprint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.capabilities.forEach { capability ->
                        Text(stringResource(R.string.browse_install_added_capability, capability))
                    }
                    state.resourceLimitIncreases.forEach { increase ->
                        Text(
                            stringResource(
                                R.string.browse_install_limit_increase,
                                resourceLimitIncreaseLabel(increase),
                            ),
                        )
                    }
                }
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
                style = TsuyomiButtonStyle.SECONDARY,
            )
            TsuyomiButton(
                text = stringResource(R.string.browse_cancel_action),
                onClick = onDismiss,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                style = TsuyomiButtonStyle.TEXT,
            )
        }
    }
}

@Composable
private fun FrozenEInkBrowseScreen(
    state: BrowseUiState,
    onRequestImport: () -> Unit,
    onOpenInstalledSource: () -> Unit,
    onApproveInstall: (allowDowngrade: Boolean) -> Unit,
    onDismissApproval: () -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier,
    remoteLibraryAvailable: Boolean,
    onOpenRemoteLibrary: () -> Unit,
) {
    when (state) {
        BrowseUiState.Empty -> FrozenEInkEmptySourceScreen(onRequestImport, modifier)
        is BrowseUiState.Preparing -> StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.browse_preparing_title),
            message = stringResource(R.string.browse_preparing_message, state.fileName),
            modifier = modifier,
        )
        is BrowseUiState.Installed -> FrozenEInkInstalledSourceScreen(
            state,
            onOpenInstalledSource,
            onRequestImport,
            remoteLibraryAvailable,
            onOpenRemoteLibrary,
            modifier,
        )
        is BrowseUiState.Failure -> FrozenEInkFailureSourceScreen(state, onRequestImport, onDismissFailure, modifier)
        is BrowseUiState.Approval -> FrozenEInkSourceApprovalScreen(
            state,
            onApproveInstall,
            onDismissApproval,
            modifier,
        )
    }
}

@Composable
private fun FrozenEInkEmptySourceScreen(onRequestImport: () -> Unit, modifier: Modifier) {
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
private fun FrozenEInkInstalledSourceScreen(
    state: BrowseUiState.Installed,
    onOpenSource: () -> Unit,
    onRequestImport: () -> Unit,
    remoteLibraryAvailable: Boolean,
    onOpenRemoteLibrary: () -> Unit,
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
        if (remoteLibraryAvailable) {
            TsuyomiButton(
                text = stringResource(R.string.browse_remote_library_action),
                onClick = onOpenRemoteLibrary,
                modifier = Modifier.padding(top = 8.dp),
                style = TsuyomiButtonStyle.SECONDARY,
            )
        }
        TsuyomiButton(
            text = stringResource(R.string.browse_import_another_action),
            onClick = onRequestImport,
            modifier = Modifier.padding(top = 8.dp),
            style = TsuyomiButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun FrozenEInkFailureSourceScreen(
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
        Text(text = installFailureMessage(state.reason), modifier = Modifier.padding(vertical = 12.dp))
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
private fun FrozenEInkSourceApprovalScreen(
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
        if (state.capabilities.isNotEmpty()) {
            Text(text = stringResource(R.string.browse_approval_capabilities))
            state.capabilities.forEach { capability -> Text(text = "• $capability") }
        }
        if (state.resourceLimitIncreases.isNotEmpty()) {
            Text(text = stringResource(R.string.browse_approval_resource_limits))
            state.resourceLimitIncreases.forEach { increase -> Text(text = "• ${resourceLimitIncreaseLabel(increase)}") }
        }
        if (state.capabilities.isEmpty() && state.resourceLimitIncreases.isEmpty()) {
            Text(text = stringResource(R.string.browse_approval_no_new_grants))
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
private fun installFailureMessage(reason: BrowseInstallFailure): String = stringResource(
    when (reason) {
        BrowseInstallFailure.FILE_ACCESS -> R.string.browse_failure_file_access
        BrowseInstallFailure.VERIFICATION -> R.string.browse_failure_verification
        BrowseInstallFailure.INSTALL -> R.string.browse_failure_install
        BrowseInstallFailure.EXPIRED_APPROVAL -> R.string.browse_failure_expired_approval
    },
)

enum class BrowseResourceLimit {
    MAX_EXECUTION_WALL_TIME_MS,
    MAX_MEMORY_BYTES,
    STORAGE_QUOTA_BYTES,
    NETWORK_CONCURRENT_REQUESTS,
    NETWORK_REQUEST_TIMEOUT_MS,
    NETWORK_RESPONSE_BYTES,
}

data class BrowseResourceLimitIncrease(
    val limit: BrowseResourceLimit,
    val activeValue: Long,
    val candidateValue: Long,
)

@Composable
private fun resourceLimitIncreaseLabel(increase: BrowseResourceLimitIncrease): String = stringResource(
    when (increase.limit) {
        BrowseResourceLimit.MAX_EXECUTION_WALL_TIME_MS -> R.string.browse_approval_limit_execution_wall_time
        BrowseResourceLimit.MAX_MEMORY_BYTES -> R.string.browse_approval_limit_memory
        BrowseResourceLimit.STORAGE_QUOTA_BYTES -> R.string.browse_approval_limit_storage
        BrowseResourceLimit.NETWORK_CONCURRENT_REQUESTS -> R.string.browse_approval_limit_concurrent_requests
        BrowseResourceLimit.NETWORK_REQUEST_TIMEOUT_MS -> R.string.browse_approval_limit_request_timeout
        BrowseResourceLimit.NETWORK_RESPONSE_BYTES -> R.string.browse_approval_limit_response_bytes
    },
    increase.activeValue,
    increase.candidateValue,
)

@Composable
private fun CheckboxRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label)
    }
}
