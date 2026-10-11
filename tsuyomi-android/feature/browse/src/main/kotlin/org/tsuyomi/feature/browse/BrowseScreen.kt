/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.components.InfoBanner
import org.tsuyomi.core.ui.components.TsuyomiCheckboxRow
import org.tsuyomi.core.ui.components.TsuyomiDialog
import org.tsuyomi.core.ui.components.InlineStatus
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiIconButton
import org.tsuyomi.core.ui.components.TsuyomiMenuItem
import org.tsuyomi.core.ui.components.TsuyomiOverflowAction
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiSplitButton
import org.tsuyomi.core.ui.components.TsuyomiTabOption
import org.tsuyomi.core.ui.components.TsuyomiTabRow
import org.tsuyomi.core.ui.components.TsuyomiTextField
import org.tsuyomi.core.ui.components.TsuyomiVisibility
import org.tsuyomi.core.ui.components.TsuyomiVisibilityEdge
import org.tsuyomi.core.ui.components.TsuyomiTopBarAction
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState

enum class BrowsePublisherIdentity { SIGNED, LOCAL_UNSIGNED }

sealed interface BrowseUiState {
    data object Empty : BrowseUiState
    data class Preparing(val fileName: String) : BrowseUiState
    data class Approval(
        val sourceName: String,
        val sourceId: String,
        val version: String,
        val publisherFingerprint: String?,
        val capabilities: List<String>,
        val resourceLimitIncreases: List<BrowseResourceLimitIncrease>,
        val isDowngrade: Boolean,
        val isLegacyMigration: Boolean = false,
        val requiresNonOfficialConsent: Boolean = false,
        val packageSha256: String = "",
        val publisherIdentity: BrowsePublisherIdentity = BrowsePublisherIdentity.SIGNED,
        val requiresUnsignedIdentityTransition: Boolean = false,
        val requiresSignedIdentityTransition: Boolean = false,
    ) : BrowseUiState
    data class PublisherKeyRequired(val keyId: String, val problem: String? = null) : BrowseUiState
    data class Installed(
        val sourceName: String,
        val version: String,
        val publisherIdentity: BrowsePublisherIdentity = BrowsePublisherIdentity.SIGNED,
    ) : BrowseUiState
    data class Failure(
        val reason: BrowseInstallFailure,
        val repositoryInstall: BrowseCatalogAction.Install? = null,
    ) : BrowseUiState
}

data class BrowseInstalledSource(
    val sourceId: String,
    val name: String,
    val version: String,
    val summary: String,
    val homeAvailable: Boolean,
    val remoteLibraryAvailable: Boolean,
    val verificationAvailable: Boolean,
    val publisherIdentity: BrowsePublisherIdentity = BrowsePublisherIdentity.SIGNED,
)

enum class BrowseInstallFailure {
    FILE_ACCESS,
    DOWNLOAD_NETWORK,
    DOWNLOAD_TIMEOUT,
    DOWNLOAD_SERVER,
    DOWNLOAD_CANCELLED,
    REPOSITORY,
    STORAGE,
    VERIFICATION,
    IDENTITY_CONFLICT,
    INSTALL,
    EXPIRED_APPROVAL,
}

enum class BrowseCatalogStatus {
    IDLE,
    LOADING,
    READY,
    UNAVAILABLE,
    ERROR,
}

data class BrowseCatalogState(
    val status: BrowseCatalogStatus = BrowseCatalogStatus.IDLE,
    val items: List<BrowseCatalogItem> = emptyList(),
    val problem: String? = null,
    val stale: Boolean = false,
    val busySourceId: String? = null,
    val repositories: List<BrowseRepository> = emptyList(),
    val subscription: BrowseSubscriptionState? = null,
)

data class BrowseRepository(
    val id: String,
    val name: String,
    val indexUrl: String,
    val rootFingerprint: String,
    val official: Boolean,
    val enabled: Boolean,
)

data class BrowseSubscriptionState(
    val link: String,
    val repositoryId: String? = null,
    val indexUrl: String? = null,
    val rootFingerprint: String? = null,
    val problem: String? = null,
    val busy: Boolean = false,
)

data class BrowseCatalogItem(
    val sourceId: String,
    val name: String,
    val version: String,
    val summary: String,
    val language: String,
    val license: String,
    val sourceUrl: String,
    val sourceRevision: String,
    val publisherFingerprint: String,
    val compatible: Boolean,
    val installedVersion: String?,
    val updateAvailable: Boolean,
    val repositoryId: String = "official",
    val repositoryName: String = "官方仓库",
    val official: Boolean = true,
    val installable: Boolean = true,
    val identityConflict: Boolean = false,
)

sealed interface BrowseCatalogAction {
    data object Refresh : BrowseCatalogAction
    data class Install(val sourceId: String, val repositoryId: String = "official") : BrowseCatalogAction
    data class OpenSourceCode(val url: String) : BrowseCatalogAction
    data class InspectSubscription(val link: String) : BrowseCatalogAction
    data object ConfirmSubscription : BrowseCatalogAction
    data object CancelSubscription : BrowseCatalogAction
    data class RemoveSubscription(val repositoryId: String) : BrowseCatalogAction
    data class SetSubscriptionEnabled(val repositoryId: String, val enabled: Boolean) : BrowseCatalogAction
}

sealed interface BrowseSourceAction {
    data class OpenHome(val sourceId: String) : BrowseSourceAction
    data class Search(val sourceId: String) : BrowseSourceAction
    data class OpenRemoteLibrary(val sourceId: String) : BrowseSourceAction
    data class Uninstall(val sourceId: String) : BrowseSourceAction
}

private enum class BrowseSection {
    INSTALLED,
    AVAILABLE,
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

@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onRequestImport: () -> Unit,
    onApproveInstall: (allowDowngrade: Boolean, allowLegacyMigration: Boolean, allowNonOfficial: Boolean) -> Unit,
    onApproveUnsigned: (allowDowngrade: Boolean, allowRisk: Boolean, allowIdentityTransition: Boolean) -> Unit,
    onDismissApproval: () -> Unit,
    onDismissFailure: () -> Unit,
    installedSources: List<BrowseInstalledSource>,
    modifier: Modifier = Modifier,
    catalog: BrowseCatalogState,
    onCatalogAction: (BrowseCatalogAction) -> Unit,
    onSourceAction: (BrowseSourceAction) -> Unit,
    onProvidePublisherKey: (String) -> Unit = {},
    onDismissPublisherKey: () -> Unit = {},
) {

    BrowseScreenContent(
        state = state,
        installedSources = installedSources,
        catalog = catalog,
        onRequestImport = onRequestImport,
        onCatalogAction = onCatalogAction,
        onSourceAction = onSourceAction,
        onApproveInstall = onApproveInstall,
        onApproveUnsigned = onApproveUnsigned,
        onDismissApproval = onDismissApproval,
        onDismissFailure = onDismissFailure,
        onProvidePublisherKey = onProvidePublisherKey,
        onDismissPublisherKey = onDismissPublisherKey,
        modifier = modifier,
    )
}

@Composable
private fun BrowseScreenContent(
    state: BrowseUiState,
    installedSources: List<BrowseInstalledSource>,
    catalog: BrowseCatalogState,
    onRequestImport: () -> Unit,
    onCatalogAction: (BrowseCatalogAction) -> Unit,
    onSourceAction: (BrowseSourceAction) -> Unit,
    onApproveInstall: (Boolean, Boolean, Boolean) -> Unit,
    onApproveUnsigned: (Boolean, Boolean, Boolean) -> Unit,
    onDismissApproval: () -> Unit,
    onDismissFailure: () -> Unit,
    onProvidePublisherKey: (String) -> Unit,
    onDismissPublisherKey: () -> Unit,
    modifier: Modifier,
) {
    var selectedKey by rememberSaveable { mutableStateOf(BrowseSection.INSTALLED.name) }
    val selectedSection = BrowseSection.entries.firstOrNull { it.name == selectedKey } ?: BrowseSection.INSTALLED
    val installedScrollState = rememberLazyListState()
    var installedDetailsSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    val catalogScrollState = rememberLazyListState()
    var catalogQuery by rememberSaveable { mutableStateOf("") }
    var detailsSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var uninstallSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var repositoryManagerVisible by rememberSaveable { mutableStateOf(false) }
    var pendingRemovalRepositoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val repositoryManagerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(selectedSection) {
        if (
            selectedSection == BrowseSection.AVAILABLE &&
            state !is BrowseUiState.Approval &&
            catalog.status == BrowseCatalogStatus.IDLE
        ) {
            onCatalogAction(BrowseCatalogAction.Refresh)
        }
    }

    Column(modifier.fillMaxSize()) {
        InstallMutationBanner(state, onRequestImport, onCatalogAction, onDismissFailure)
        TsuyomiTabRow(
            options = listOf(
                TsuyomiTabOption(BrowseSection.INSTALLED.name, stringResource(R.string.browse_section_installed)),
                TsuyomiTabOption(BrowseSection.AVAILABLE.name, stringResource(R.string.browse_section_installable)),
            ),
            selectedKey = selectedSection.name,
            onSelect = { selectedKey = it },
            modifier = Modifier.fillMaxWidth(),
        )
        when (state) {
            is BrowseUiState.Approval -> Column(Modifier.weight(1f).then(if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
                ApprovalSourceCard(state, onApproveInstall, onApproveUnsigned, onDismissApproval)
                Spacer(Modifier.height(TsuyomiSpacing.Lg))
            }
            is BrowseUiState.PublisherKeyRequired -> PublisherKeyRequiredCard(
                state = state,
                onProvidePublisherKey = onProvidePublisherKey,
                onDismiss = onDismissPublisherKey,
                modifier = Modifier.weight(1f),
            )
            else -> when (selectedSection) {
                BrowseSection.INSTALLED -> InstalledSourcesContent(
                    state = state,
                    installedSources = installedSources,
                    onRequestImport = onRequestImport,
                    onSourceAction = onSourceAction,
                    onRequestUninstall = { uninstallSourceId = it },
                    scrollState = installedScrollState,
                    detailsSourceId = installedDetailsSourceId,
                    onDetailsSourceIdChange = { installedDetailsSourceId = it },
                    modifier = Modifier.weight(1f),
                )
                BrowseSection.AVAILABLE -> BrowseCatalogContent(
                    catalog = catalog,
                    installationAllowed =
                        catalog.status == BrowseCatalogStatus.READY &&
                            !catalog.stale &&
                            catalog.busySourceId == null &&
                            state !is BrowseUiState.Preparing,
                    refreshAllowed =
                        catalog.busySourceId == null &&
                            state !is BrowseUiState.Preparing,
                    onCatalogAction = onCatalogAction,
                    onManageRepositories = { repositoryManagerVisible = true },
                    repositoryManagerFocusRequester = repositoryManagerFocusRequester,
                    modifier = Modifier.weight(1f),
                    scrollState = catalogScrollState,
                    catalogQuery = catalogQuery,
                    onCatalogQueryChange = { catalogQuery = it },
                    detailsSourceId = detailsSourceId,
                    onDetailsSourceIdChange = { detailsSourceId = it },
                )
            }
        }
    }
    uninstallSourceId?.let { sourceId ->
        InstalledSourceUninstallDialog(
            source = installedSources.firstOrNull { it.sourceId == sourceId },
            onConfirm = {
                onSourceAction(BrowseSourceAction.Uninstall(sourceId))
                uninstallSourceId = null
            },
            onDismiss = { uninstallSourceId = null },
        )
    }
    pendingRemovalRepositoryId?.let { repositoryId ->
        val repository = catalog.repositories.firstOrNull { it.id == repositoryId }
        if (repository == null) {
            LaunchedEffect(repositoryId) {
                pendingRemovalRepositoryId = null
                repositoryManagerVisible = true
            }
        } else {
            TsuyomiDialog(
                onDismissRequest = {
                    pendingRemovalRepositoryId = null
                    repositoryManagerVisible = true
                },
                title = stringResource(R.string.browse_repository_remove_title, repository.name),
                text = stringResource(R.string.browse_repository_remove_message),
                confirmLabel = stringResource(R.string.browse_repository_remove_action),
                onConfirm = {
                    onCatalogAction(BrowseCatalogAction.RemoveSubscription(repository.id))
                    pendingRemovalRepositoryId = null
                    repositoryManagerVisible = true
                },
                dismissLabel = stringResource(R.string.browse_cancel_action),
                destructive = true,
            )
        }
    }
    if (repositoryManagerVisible && pendingRemovalRepositoryId == null) {
        RepositoryManagementDialog(
            catalog = catalog,
            onCatalogAction = onCatalogAction,
            onRequestRemove = { repositoryId ->
                repositoryManagerVisible = false
                pendingRemovalRepositoryId = repositoryId
            },
            onDismiss = {
                if (catalog.subscription != null) onCatalogAction(BrowseCatalogAction.CancelSubscription)
                repositoryManagerVisible = false
            },
            restoreFocusTo = repositoryManagerFocusRequester,
        )
    }
}

@Composable
private fun InstalledSourcesContent(
    state: BrowseUiState,
    installedSources: List<BrowseInstalledSource>,
    onRequestImport: () -> Unit,
    onSourceAction: (BrowseSourceAction) -> Unit,
    onRequestUninstall: (String) -> Unit,
    scrollState: LazyListState,
    detailsSourceId: String?,
    onDetailsSourceIdChange: (String?) -> Unit,
    modifier: Modifier,
) {
    if (installedSources.isEmpty()) {
        if (state is BrowseUiState.Preparing) {
            StateView(
                kind = TsuyomiStateKind.LOADING,
                title = stringResource(R.string.browse_preparing_title),
                message = stringResource(R.string.browse_preparing_message, state.fileName),
                modifier = modifier,
            )
        } else {
            StateView(
                kind = TsuyomiStateKind.EMPTY,
                title = stringResource(R.string.browse_empty_title),
                message = stringResource(R.string.browse_empty_message),
                actionLabel = stringResource(R.string.browse_import_action),
                onAction = onRequestImport,
                modifier = modifier,
            )
        }
        return
    }

    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        TsuyomiMeasuredPager(itemCount = installedSources.size, itemKey = { installedSources[it].sourceId }, modifier = modifier.fillMaxSize(),
            itemContent = { index ->
                val source = installedSources[index]
                InstalledSourceRow(source, { onSourceAction(BrowseSourceAction.Search(source.sourceId)) },
                    { onSourceAction(BrowseSourceAction.OpenHome(source.sourceId)) }, { onSourceAction(BrowseSourceAction.OpenRemoteLibrary(source.sourceId)) },
                    { onDetailsSourceIdChange(source.sourceId) }, { onRequestUninstall(source.sourceId) })
            })
    } else {
    LazyColumn(
        modifier = modifier,
        state = scrollState,
        contentPadding = PaddingValues(bottom = TsuyomiSpacing.Lg),
    ) {
        items(
            items = installedSources,
            key = { source -> source.sourceId },
            contentType = { "installed-source" },
        ) { source ->
            InstalledSourceRow(
                source = source,
                onSearch = { onSourceAction(BrowseSourceAction.Search(source.sourceId)) },
                onOpenHome = { onSourceAction(BrowseSourceAction.OpenHome(source.sourceId)) },
                onOpenRemoteLibrary = { onSourceAction(BrowseSourceAction.OpenRemoteLibrary(source.sourceId)) },
                onOpenDetails = { onDetailsSourceIdChange(source.sourceId) },
                onRequestUninstall = { onRequestUninstall(source.sourceId) },
            )
        }
    }
    }
    installedSources.firstOrNull { it.sourceId == detailsSourceId }?.let { source ->
        InstalledSourceDetailsDialog(
            source = source,
            onDismiss = { onDetailsSourceIdChange(null) },
        )
    }
}

@Composable
private fun InstallMutationBanner(
    state: BrowseUiState,
    onRequestImport: () -> Unit,
    onCatalogAction: (BrowseCatalogAction) -> Unit,
    onDismissFailure: () -> Unit,
) {
    val text = when (state) {
        is BrowseUiState.Preparing -> stringResource(R.string.browse_install_progress, state.fileName)
        is BrowseUiState.Failure -> stringResource(
            R.string.browse_install_failure_banner,
            installFailureMessage(state.reason),
        )
        else -> return
    }
    val failure = state as? BrowseUiState.Failure
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (failure != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(TsuyomiSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            failure?.let { failed ->
                val repositoryInstall = failed.repositoryInstall
                when {
                    repositoryInstall != null && failed.reason.isRetryableDownloadFailure -> TsuyomiButton(
                        text = stringResource(R.string.browse_retry_download_action),
                        onClick = { onCatalogAction(repositoryInstall) },
                        style = TsuyomiButtonStyle.TEXT,
                    )
                    failed.reason == BrowseInstallFailure.IDENTITY_CONFLICT -> TsuyomiButton(
                        text = stringResource(R.string.browse_dismiss_failure_action),
                        onClick = onDismissFailure,
                        style = TsuyomiButtonStyle.TEXT,
                    )
                    repositoryInstall != null -> TsuyomiButton(
                        text = stringResource(R.string.browse_return_to_catalog_action),
                        onClick = onDismissFailure,
                        style = TsuyomiButtonStyle.TEXT,
                    )
                    else -> TsuyomiButton(
                        text = stringResource(R.string.browse_try_again_action),
                        onClick = onRequestImport,
                        style = TsuyomiButtonStyle.TEXT,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledSourceRow(
    source: BrowseInstalledSource,
    onSearch: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenRemoteLibrary: () -> Unit,
    onOpenDetails: () -> Unit,
    onRequestUninstall: () -> Unit,
) {
    val primaryAction = if (source.homeAvailable) onOpenHome else onSearch
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        TsuyomiMeasuredPager(itemCount = 3, itemKey = { "${source.sourceId}:$it" }, fillViewport = false,
            itemContent = { index -> when (index) {
                0 -> TsuyomiMenuItem(source.name, primaryAction, modifier = Modifier.testTag("browse-source-identity-${source.sourceId}"))
                1 -> BrowsePagedText(if (source.publisherIdentity == BrowsePublisherIdentity.LOCAL_UNSIGNED) stringResource(R.string.browse_installed_unsigned_version, source.version) else source.version)
                else -> InstalledSourceEntryButton(source, primaryAction, onSearch, onOpenRemoteLibrary, onOpenDetails, onRequestUninstall)
            } })
        return
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 120.dp)
                .weight(1f)
                .clickable(role = Role.Button, onClick = primaryAction)
                .testTag("browse-source-identity-${source.sourceId}"),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                source.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (source.publisherIdentity == BrowsePublisherIdentity.LOCAL_UNSIGNED) {
                    stringResource(R.string.browse_installed_unsigned_version, source.version)
                } else {
                    source.version
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        InstalledSourceEntryButton(source, primaryAction, onSearch, onOpenRemoteLibrary, onOpenDetails, onRequestUninstall)
    }
    HorizontalDivider(Modifier.padding(horizontal = TsuyomiSpacing.Md))
}

@Composable
private fun InstalledSourceDetailsDialog(source: BrowseInstalledSource, onDismiss: () -> Unit) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        val values = listOf(source.name, source.sourceId, source.version, source.summary,
            if (source.publisherIdentity == BrowsePublisherIdentity.LOCAL_UNSIGNED) stringResource(R.string.browse_source_details_unsigned_origin) else "") + sourceCapabilityLabels(source)
        TsuyomiDialog(onDismissRequest = onDismiss, title = stringResource(R.string.browse_source_details_title), dismissLabel = stringResource(R.string.browse_close_action),
            body = { TsuyomiMeasuredPager(itemCount = values.size, itemKey = { "${source.sourceId}:details:$it" }, itemContent = { BrowsePagedText(values[it]) }, modifier = Modifier.fillMaxWidth().weight(1f)) })
        return
    }
    TsuyomiDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.browse_source_details_title),
        dismissLabel = stringResource(R.string.browse_close_action),
        body = {
            SourceDetailField(stringResource(R.string.browse_source_details_name), source.name)
            SourceDetailField(stringResource(R.string.browse_source_details_id), source.sourceId)
            SourceDetailField(stringResource(R.string.browse_source_details_version), source.version)
            SourceDetailField(stringResource(R.string.browse_source_details_summary), source.summary)
            if (source.publisherIdentity == BrowsePublisherIdentity.LOCAL_UNSIGNED) {
                SourceDetailField(
                    stringResource(R.string.browse_source_details_origin),
                    stringResource(R.string.browse_source_details_unsigned_origin),
                )
            }
            Text(
                stringResource(R.string.browse_source_details_capabilities),
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                style = MaterialTheme.typography.labelLarge,
            )
            sourceCapabilityLabels(source).forEach { capability ->
                Text(
                    capability,
                    modifier = Modifier.padding(top = TsuyomiSpacing.Xs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun SourceDetailField(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Text(
        value,
        modifier = Modifier.padding(bottom = TsuyomiSpacing.Sm),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun sourceCapabilityLabels(source: BrowseInstalledSource): List<String> = buildList {
    add(stringResource(R.string.browse_source_capabilities_search))
    if (source.homeAvailable) add(stringResource(R.string.browse_source_capabilities_home))
    if (source.remoteLibraryAvailable) add(stringResource(R.string.browse_source_capabilities_remote_library))
    if (source.verificationAvailable) add(stringResource(R.string.browse_source_capabilities_verification))
}

@Composable
private fun BrowseCatalogContent(
    catalog: BrowseCatalogState,
    installationAllowed: Boolean,
    refreshAllowed: Boolean,
    onCatalogAction: (BrowseCatalogAction) -> Unit,
    repositoryManagerFocusRequester: FocusRequester,
    onManageRepositories: () -> Unit,
    scrollState: LazyListState,
    catalogQuery: String,
    onCatalogQueryChange: (String) -> Unit,
    detailsSourceId: String?,
    onDetailsSourceIdChange: (String?) -> Unit,
    modifier: Modifier,
) {
    if (catalog.items.isEmpty()) {
        Column(modifier) {
            RepositoryManagementAction(onManageRepositories, repositoryManagerFocusRequester)
            CatalogEmptyState(
                catalog = catalog,
                refreshAllowed = refreshAllowed,
                onCatalogAction = onCatalogAction,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    val visibleItems = if (catalogQuery.isBlank()) {
        catalog.items
    } else {
        catalog.items.filter { item ->
            item.name.contains(catalogQuery, ignoreCase = true) ||
                item.summary.contains(catalogQuery, ignoreCase = true) ||
                item.language.contains(catalogQuery, ignoreCase = true) ||
                item.sourceId.contains(catalogQuery, ignoreCase = true) ||
                item.repositoryName.contains(catalogQuery, ignoreCase = true)
        }
    }
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        TsuyomiMeasuredPager(itemCount = 4 + visibleItems.size, itemKey = { if (it < 4) "catalog-control:$it" else catalogItemKey(visibleItems[it - 4]) },
            modifier = modifier.fillMaxSize(), itemContent = { index -> when (index) {
                0 -> RepositoryManagementAction(onManageRepositories, repositoryManagerFocusRequester)
                1 -> TsuyomiTextField(catalogQuery, onCatalogQueryChange, label = stringResource(R.string.browse_catalog_search_label), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("browse-catalog-search"))
                2 -> TsuyomiButton(stringResource(R.string.browse_catalog_refresh_action), { onCatalogAction(BrowseCatalogAction.Refresh) }, enabled = refreshAllowed)
                3 -> CatalogStatusNotice(catalog, refreshAllowed, onCatalogAction)
                else -> {
                    val item = visibleItems[index - 4]
                    BrowseCatalogItemRow(item, catalog.busySourceId == catalogItemKey(item) || (item.repositoryId == "official" && catalog.busySourceId == item.sourceId),
                        installationAllowed, { onDetailsSourceIdChange(catalogItemKey(item)) }, { onCatalogAction(BrowseCatalogAction.Install(item.sourceId, item.repositoryId)) })
                }
            } })
    } else {
    LazyColumn(
        modifier = modifier,
        state = scrollState,
        contentPadding = PaddingValues(bottom = TsuyomiSpacing.Lg),
    ) {
        item(key = "catalog-repositories", contentType = "repository-action") {
            RepositoryManagementAction(onManageRepositories, repositoryManagerFocusRequester)
        }
        item(key = "catalog-search", contentType = "search") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(TsuyomiSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TsuyomiTextField(
                    value = catalogQuery,
                    onValueChange = onCatalogQueryChange,
                    label = stringResource(R.string.browse_catalog_search_label),
                    modifier = Modifier.weight(1f).testTag("browse-catalog-search"),
                    singleLine = true,
                )
                if (catalog.status == BrowseCatalogStatus.READY || catalog.status == BrowseCatalogStatus.IDLE) {
                    TsuyomiIconButton(
                        imageVector = TsuyomiIcons.Refresh,
                        contentDescription = stringResource(R.string.browse_catalog_refresh_action),
                        onClick = { onCatalogAction(BrowseCatalogAction.Refresh) },
                        enabled = refreshAllowed,
                        modifier = Modifier.padding(start = TsuyomiSpacing.Sm),
                    )
                }
            }
        }
        item(key = "catalog-status", contentType = "status") {
            CatalogStatusNotice(catalog, refreshAllowed, onCatalogAction)
        }
        if (visibleItems.isEmpty()) {
            item(key = "catalog-empty", contentType = "status") {
                Text(
                    stringResource(R.string.browse_catalog_search_empty),
                    modifier = Modifier.padding(TsuyomiSpacing.Md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(visibleItems, key = ::catalogItemKey, contentType = { "source" }) { item ->
                BrowseCatalogItemRow(
                    item = item,
                    busy = catalog.busySourceId == catalogItemKey(item) ||
                        (item.repositoryId == "official" && catalog.busySourceId == item.sourceId),
                    installationAllowed = installationAllowed,
                    onOpenDetails = { onDetailsSourceIdChange(catalogItemKey(item)) },
                    onInstall = {
                        onCatalogAction(BrowseCatalogAction.Install(item.sourceId, item.repositoryId))
                    },
                )
            }
        }
    }
    }
    catalog.items.firstOrNull { catalogItemKey(it) == detailsSourceId }?.let { item ->
        BrowseCatalogDetailsDialog(
            item = item,
            onOpenSourceCode = { onCatalogAction(BrowseCatalogAction.OpenSourceCode(item.sourceUrl)) },
            onDismiss = { onDetailsSourceIdChange(null) },
        )
    }
}

@Composable
private fun RepositoryManagementAction(onClick: () -> Unit, focusRequester: FocusRequester) {
    TsuyomiButton(
        text = stringResource(R.string.browse_repository_manage_action),
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm)
            .testTag("browse-repository-manage"),
        style = TsuyomiButtonStyle.TEXT,
    )
}
@Composable
private fun RepositoryManagementDialog(
    catalog: BrowseCatalogState,
    onCatalogAction: (BrowseCatalogAction) -> Unit,
    onRequestRemove: (String) -> Unit,
    onDismiss: () -> Unit,
    restoreFocusTo: FocusRequester,
) {
    val subscription = catalog.subscription
    var subscriptionLink by rememberSaveable { mutableStateOf(subscription?.link.orEmpty()) }
    LaunchedEffect(subscription?.link) {
        subscription?.link?.let { subscriptionLink = it }
    }

    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        TsuyomiDialog(onDismissRequest = onDismiss, title = stringResource(R.string.browse_repository_manage_title), dismissLabel = stringResource(R.string.browse_close_action), restoreFocusTo = restoreFocusTo,
            body = {
                val focusManager = LocalFocusManager.current
                val fixedCount = 10
                TsuyomiMeasuredPager(itemCount = fixedCount + catalog.repositories.size,
                    itemKey = { if (it < fixedCount) "subscription:$it" else "repository:${catalog.repositories[it - fixedCount].id}" },
                    modifier = Modifier.fillMaxWidth().weight(1f), itemContent = { index -> when (index) {
                        0 -> BrowsePagedText(stringResource(R.string.browse_repository_manage_message))
                        1 -> TsuyomiTextField(subscriptionLink, { subscriptionLink = it }, label = stringResource(R.string.browse_repository_link_label), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("browse-subscription-link"))
                        2 -> TsuyomiButton(stringResource(R.string.browse_repository_inspect_action), { focusManager.clearFocus(); onCatalogAction(BrowseCatalogAction.InspectSubscription(subscriptionLink)) },
                            enabled = subscriptionLink.isNotBlank() && subscription?.busy != true, modifier = Modifier.testTag("browse-subscription-inspect"))
                        3 -> if (subscription?.problem != null) BrowsePagedText(stringResource(R.string.browse_repository_inspect_error))
                        4 -> if (subscription?.busy == true) BrowsePagedText(stringResource(R.string.browse_repository_inspecting))
                        5 -> subscription?.indexUrl?.let { BrowsePagedText(stringResource(R.string.browse_repository_confirm_index_url) + "\n" + it) }
                        6 -> subscription?.rootFingerprint?.let { BrowsePagedText(stringResource(R.string.browse_repository_confirm_root_fingerprint) + "\n" + it) }
                        7 -> if (subscription?.rootFingerprint != null) BrowsePagedText(stringResource(R.string.browse_repository_confirm_message))
                        8 -> if (subscription?.rootFingerprint != null && subscription.indexUrl != null) TsuyomiButton(stringResource(R.string.browse_repository_confirm_action), { onCatalogAction(BrowseCatalogAction.ConfirmSubscription) }, enabled = !subscription.busy, modifier = Modifier.testTag("browse-subscription-confirm"))
                        9 -> if (subscription?.rootFingerprint != null) TsuyomiButton(stringResource(R.string.browse_repository_cancel_subscription_action), { onCatalogAction(BrowseCatalogAction.CancelSubscription) }, enabled = !subscription.busy, modifier = Modifier.testTag("browse-subscription-cancel"))
                        else -> {
                            val repository = catalog.repositories[index - fixedCount]
                            RepositoryRegistryRow(repository, { onCatalogAction(BrowseCatalogAction.SetSubscriptionEnabled(repository.id, it)) }, { onRequestRemove(repository.id) })
                        }
                    } })
            })
        return
    }
    TsuyomiDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.browse_repository_manage_title),
        dismissLabel = stringResource(R.string.browse_close_action),
        restoreFocusTo = restoreFocusTo,
        body = {
            val focusManager = LocalFocusManager.current
            Text(
                stringResource(R.string.browse_repository_manage_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            catalog.repositories.forEach { repository ->
                RepositoryRegistryRow(
                    repository = repository,
                    onSetEnabled = { enabled ->
                        onCatalogAction(BrowseCatalogAction.SetSubscriptionEnabled(repository.id, enabled))
                    },
                    onRequestRemove = { onRequestRemove(repository.id) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = TsuyomiSpacing.Md))
            Text(
                stringResource(R.string.browse_repository_add_title),
                style = MaterialTheme.typography.titleSmall,
            )
            TsuyomiTextField(
                value = subscriptionLink,
                onValueChange = { subscriptionLink = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TsuyomiSpacing.Sm)
                    .testTag("browse-subscription-link"),
                label = stringResource(R.string.browse_repository_link_label),
                singleLine = true,
            )
            TsuyomiButton(
                text = stringResource(R.string.browse_repository_inspect_action),
                onClick = {
                    focusManager.clearFocus()
                    onCatalogAction(BrowseCatalogAction.InspectSubscription(subscriptionLink))
                },
                enabled = subscriptionLink.isNotBlank() && subscription?.busy != true,
                modifier = Modifier
                    .padding(top = TsuyomiSpacing.Sm)
                    .testTag("browse-subscription-inspect"),
                style = TsuyomiButtonStyle.SECONDARY,
            )
            if (subscription?.problem != null) {
                Text(
                    stringResource(R.string.browse_repository_inspect_error),
                    modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (subscription?.busy == true) {
                InlineStatus(stringResource(R.string.browse_repository_inspecting))
            }
            subscription?.rootFingerprint?.let { rootFingerprint ->
                val indexUrl = subscription.indexUrl ?: return@let
                HorizontalDivider(Modifier.padding(vertical = TsuyomiSpacing.Md))
                Text(
                    stringResource(R.string.browse_repository_confirm_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                CatalogDetailField(
                    stringResource(R.string.browse_repository_confirm_index_url),
                    indexUrl,
                )
                CatalogDetailField(
                    stringResource(R.string.browse_repository_confirm_root_fingerprint),
                    rootFingerprint,
                )
                Text(
                    stringResource(R.string.browse_repository_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TsuyomiButton(
                    text = stringResource(R.string.browse_repository_confirm_action),
                    onClick = { onCatalogAction(BrowseCatalogAction.ConfirmSubscription) },
                    enabled = subscription.busy != true,
                    modifier = Modifier
                        .padding(top = TsuyomiSpacing.Sm)
                        .testTag("browse-subscription-confirm"),
                    style = TsuyomiButtonStyle.PRIMARY,
                )
                TsuyomiButton(
                    text = stringResource(R.string.browse_repository_cancel_subscription_action),
                    onClick = { onCatalogAction(BrowseCatalogAction.CancelSubscription) },
                    enabled = subscription.busy != true,
                    modifier = Modifier
                        .padding(top = TsuyomiSpacing.Sm)
                        .testTag("browse-subscription-cancel"),
                    style = TsuyomiButtonStyle.TEXT,
                )
            }
        },
    )
}

private fun catalogItemKey(item: BrowseCatalogItem): String = "${item.repositoryId}\u0000${item.sourceId}"

@Composable
private fun CatalogEmptyState(
    catalog: BrowseCatalogState,
    refreshAllowed: Boolean,
    onCatalogAction: (BrowseCatalogAction) -> Unit,
    modifier: Modifier,
) {
    when (catalog.status) {
        BrowseCatalogStatus.LOADING -> StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.browse_catalog_loading),
            modifier = modifier,
        )
        BrowseCatalogStatus.UNAVAILABLE -> StateView(
            kind = TsuyomiStateKind.ERROR,
            title = stringResource(R.string.browse_catalog_unavailable_title),
            message = stringResource(R.string.browse_catalog_unavailable_message),
        )
        BrowseCatalogStatus.ERROR -> StateView(
            kind = TsuyomiStateKind.ERROR,
            title = stringResource(R.string.browse_catalog_error_title),
            message = stringResource(R.string.browse_catalog_error_message),
            onAction = if (refreshAllowed) {
                { onCatalogAction(BrowseCatalogAction.Refresh) }
            } else {
                null
            },
            modifier = modifier,
        )
        BrowseCatalogStatus.IDLE,
        BrowseCatalogStatus.READY -> StateView(
            kind = TsuyomiStateKind.EMPTY,
            title = stringResource(R.string.browse_catalog_empty_title),
            message = stringResource(R.string.browse_catalog_empty_message),
            actionLabel = if (refreshAllowed) stringResource(R.string.browse_catalog_refresh_action) else null,
            onAction = if (refreshAllowed) {
                { onCatalogAction(BrowseCatalogAction.Refresh) }
            } else {
                null
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun CatalogStatusNotice(
    catalog: BrowseCatalogState,
    refreshAllowed: Boolean,
    onCatalogAction: (BrowseCatalogAction) -> Unit,
) {
    when (catalog.status) {
        BrowseCatalogStatus.LOADING -> InlineStatus(stringResource(R.string.browse_catalog_loading))
        BrowseCatalogStatus.ERROR -> InfoBanner(
            title = stringResource(R.string.browse_catalog_error_title),
            message = stringResource(R.string.browse_catalog_error_message),
            primaryActionLabel = if (refreshAllowed) stringResource(R.string.browse_catalog_refresh_action) else null,
            onPrimaryAction = if (refreshAllowed) {
                { onCatalogAction(BrowseCatalogAction.Refresh) }
            } else {
                null
            },
        )
        BrowseCatalogStatus.UNAVAILABLE -> InfoBanner(
            title = stringResource(R.string.browse_catalog_unavailable_title),
            message = stringResource(R.string.browse_catalog_unavailable_message),
        )
        BrowseCatalogStatus.IDLE,
        BrowseCatalogStatus.READY -> Unit
    }
    if (catalog.stale) {
        InlineStatus(stringResource(R.string.browse_catalog_stale))
    }
}

@Composable
private fun BrowseCatalogItemRow(
    item: BrowseCatalogItem,
    busy: Boolean,
    installationAllowed: Boolean,
    onOpenDetails: () -> Unit,
    onInstall: () -> Unit,
) {
    val action = catalogItemAction(item, busy, installationAllowed)
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        TsuyomiMeasuredPager(itemCount = 4, itemKey = { "${catalogItemKey(item)}:$it" }, fillViewport = false,
            itemContent = { index -> when (index) {
                0 -> TsuyomiMenuItem(item.name, onOpenDetails)
                1 -> BrowsePagedText(stringResource(R.string.browse_catalog_row_metadata, item.version, item.language, item.repositoryName,
                    stringResource(if (item.official) R.string.browse_catalog_repository_official else R.string.browse_catalog_repository_third_party)))
                2 -> BrowsePagedText(item.summary)
                else -> TsuyomiButton(stringResource(action.label), onInstall, enabled = action.enabled, style = action.style,
                    modifier = Modifier.testTag("browse-catalog-install-${item.repositoryId}-${item.sourceId}"))
            } })
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpenDetails)
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.browse_catalog_row_metadata,
                        item.version,
                        item.language,
                        item.repositoryName,
                        stringResource(
                            if (item.official) R.string.browse_catalog_repository_official
                            else R.string.browse_catalog_repository_third_party,
                        ),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.summary,
                    modifier = Modifier.padding(top = TsuyomiSpacing.Xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TsuyomiButton(
                text = stringResource(action.label),
                onClick = onInstall,
                enabled = action.enabled,
                modifier = Modifier
                    .padding(start = TsuyomiSpacing.Md)
                    .testTag("browse-catalog-install-${item.repositoryId}-${item.sourceId}"),
                style = action.style,
            )
        }
        HorizontalDivider(Modifier.padding(top = TsuyomiSpacing.Sm))
    }
}

private data class CatalogItemAction(
    val label: Int,
    val enabled: Boolean,
    val style: TsuyomiButtonStyle,
)

private fun catalogItemAction(
    item: BrowseCatalogItem,
    busy: Boolean,
    installationAllowed: Boolean,
): CatalogItemAction = when {
    busy -> CatalogItemAction(R.string.browse_catalog_preparing, false, TsuyomiButtonStyle.TEXT)
    item.identityConflict -> CatalogItemAction(R.string.browse_catalog_identity_conflict, false, TsuyomiButtonStyle.TEXT)
    !item.installable -> CatalogItemAction(
        R.string.browse_catalog_repository_unavailable,
        false,
        TsuyomiButtonStyle.TEXT,
    )
    !item.compatible -> CatalogItemAction(R.string.browse_catalog_incompatible, false, TsuyomiButtonStyle.TEXT)
    item.installedVersion == null -> CatalogItemAction(
        R.string.browse_catalog_install_action,
        installationAllowed,
        TsuyomiButtonStyle.PRIMARY,
    )
    item.updateAvailable -> CatalogItemAction(
        R.string.browse_catalog_update_action,
        installationAllowed,
        TsuyomiButtonStyle.PRIMARY,
    )
    else -> CatalogItemAction(R.string.browse_catalog_installed, false, TsuyomiButtonStyle.TEXT)
}

@Composable
private fun BrowseCatalogDetailsDialog(
    item: BrowseCatalogItem,
    onOpenSourceCode: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        val values = listOf(item.name, "${item.version} ${item.language}", item.summary,
            stringResource(R.string.browse_catalog_repository) + "\n" + item.repositoryName,
            stringResource(R.string.browse_catalog_publisher) + "\n" + item.publisherFingerprint,
            if (item.identityConflict) stringResource(R.string.browse_catalog_identity_conflict_message) else "",
            stringResource(R.string.browse_catalog_license) + "\n" + item.license,
            stringResource(R.string.browse_catalog_source_revision) + "\n" + item.sourceRevision,
            stringResource(R.string.browse_catalog_source_url) + "\n" + item.sourceUrl)
        TsuyomiDialog(onDismissRequest = onDismiss, title = stringResource(R.string.browse_catalog_details_title),
            confirmLabel = stringResource(R.string.browse_catalog_open_source_code), onConfirm = onOpenSourceCode, dismissLabel = stringResource(R.string.browse_close_action),
            body = { TsuyomiMeasuredPager(itemCount = values.size, itemKey = { "${catalogItemKey(item)}:details:$it" }, itemContent = { BrowsePagedText(values[it]) }, modifier = Modifier.fillMaxWidth().weight(1f)) })
        return
    }
    TsuyomiDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.browse_catalog_details_title),
        body = {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text("${item.version}  ${item.language}", style = MaterialTheme.typography.labelMedium)
            Text(
                item.summary,
                modifier = Modifier.padding(vertical = TsuyomiSpacing.Md),
                style = MaterialTheme.typography.bodyMedium,
            )
            CatalogDetailField(
                stringResource(R.string.browse_catalog_repository),
                stringResource(
                    R.string.browse_catalog_repository_provenance,
                    item.repositoryName,
                    stringResource(
                        if (item.official) R.string.browse_catalog_repository_official
                        else R.string.browse_catalog_repository_third_party,
                    ),
                ),
            )
            CatalogDetailField(stringResource(R.string.browse_catalog_publisher), item.publisherFingerprint)
            if (item.identityConflict) {
                Text(
                    stringResource(R.string.browse_catalog_identity_conflict_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            CatalogDetailField(stringResource(R.string.browse_catalog_license), item.license)
            CatalogDetailField(stringResource(R.string.browse_catalog_source_revision), item.sourceRevision)
            CatalogDetailField(stringResource(R.string.browse_catalog_source_url), item.sourceUrl)
        },
        confirmLabel = stringResource(R.string.browse_catalog_open_source_code),
        onConfirm = onOpenSourceCode,
        dismissLabel = stringResource(R.string.browse_close_action),
    )
}

@Composable
private fun CatalogDetailField(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Text(
        value,
        modifier = Modifier.padding(bottom = TsuyomiSpacing.Md),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InstalledSourceUninstallDialog(
    source: BrowseInstalledSource?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TsuyomiDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.browse_source_uninstall_title),
        text = stringResource(
            R.string.browse_source_uninstall_message,
            source?.name ?: source?.sourceId.orEmpty(),
        ),
        confirmLabel = stringResource(R.string.browse_source_uninstall_action),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.browse_cancel_action),
    )
}


@Composable
private fun RepositoryRegistryRow(
    repository: BrowseRepository,
    onSetEnabled: (Boolean) -> Unit,
    onRequestRemove: () -> Unit,
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        TsuyomiMeasuredPager(itemCount = 5, itemKey = { "repository:${repository.id}:$it" }, fillViewport = false,
            itemContent = { index -> when (index) {
                0 -> BrowsePagedText(repository.name)
                1 -> BrowsePagedText(repository.indexUrl)
                2 -> BrowsePagedText(stringResource(R.string.browse_repository_root_fingerprint, repository.rootFingerprint))
                3 -> if (repository.official) BrowsePagedText(stringResource(R.string.browse_repository_official_locked))
                    else TsuyomiCheckboxRow(checked = repository.enabled, onCheckedChange = onSetEnabled, label = stringResource(R.string.browse_repository_enabled), modifier = Modifier.testTag("browse-repository-enabled-${repository.id}"))
                else -> if (!repository.official) TsuyomiButton(stringResource(R.string.browse_repository_remove_action), onRequestRemove, modifier = Modifier.testTag("browse-repository-remove-${repository.id}"))
            } })
        return
    }
    Column(Modifier.padding(top = TsuyomiSpacing.Md)) {
        Text(repository.name, style = MaterialTheme.typography.titleSmall)
        Text(
            repository.indexUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.browse_repository_root_fingerprint, repository.rootFingerprint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (repository.official) {
            Text(
                stringResource(R.string.browse_repository_official_locked),
                modifier = Modifier.padding(top = TsuyomiSpacing.Xs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TsuyomiCheckboxRow(
                checked = repository.enabled,
                onCheckedChange = onSetEnabled,
                label = stringResource(R.string.browse_repository_enabled),
                modifier = Modifier.testTag("browse-repository-enabled-${repository.id}"),
            )
            TsuyomiButton(
                text = stringResource(R.string.browse_repository_remove_action),
                onClick = onRequestRemove,
                modifier = Modifier.testTag("browse-repository-remove-${repository.id}"),
                style = TsuyomiButtonStyle.TEXT,
            )
        }
    }
}

@Composable
private fun PublisherKeyRequiredCard(
    state: BrowseUiState.PublisherKeyRequired,
    onProvidePublisherKey: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    var publicKey by rememberSaveable(state.keyId) { mutableStateOf("") }
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        TsuyomiMeasuredPager(itemCount = 6, itemKey = { "publisher:${state.keyId}:$it" }, modifier = modifier.fillMaxSize(),
            itemContent = { index -> when (index) {
                0 -> BrowsePagedText(stringResource(R.string.browse_publisher_key_message))
                1 -> BrowsePagedText(stringResource(R.string.browse_publisher_key_id) + "\n" + state.keyId)
                2 -> BrowsePagedText(state.problem.orEmpty())
                3 -> TsuyomiTextField(publicKey, { publicKey = it }, label = stringResource(R.string.browse_publisher_key_input_label), singleLine = true)
                4 -> TsuyomiButton(stringResource(R.string.browse_publisher_key_verify_action), { onProvidePublisherKey(publicKey) }, enabled = publicKey.isNotBlank(), modifier = Modifier.testTag("browse-publisher-key-verify"))
                else -> TsuyomiButton(stringResource(R.string.browse_cancel_action), onDismiss)
            } })
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(TsuyomiSpacing.Md),
    ) {
        Text(
            stringResource(R.string.browse_publisher_key_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.browse_publisher_key_message),
            modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CatalogDetailField(stringResource(R.string.browse_publisher_key_id), state.keyId)
        state.problem?.let { problem ->
            Text(
                problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TsuyomiTextField(
            value = publicKey,
            onValueChange = { publicKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.browse_publisher_key_input_label),
            singleLine = true,
        )
        TsuyomiButton(
            text = stringResource(R.string.browse_publisher_key_verify_action),
            onClick = { onProvidePublisherKey(publicKey) },
            enabled = publicKey.isNotBlank(),
            modifier = Modifier
                .padding(top = TsuyomiSpacing.Md)
                .testTag("browse-publisher-key-verify"),
            style = TsuyomiButtonStyle.PRIMARY,
        )
        TsuyomiButton(
            text = stringResource(R.string.browse_cancel_action),
            onClick = onDismiss,
            modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
            style = TsuyomiButtonStyle.TEXT,
        )
    }
}

@Composable
private fun ApprovalSourceCard(
    state: BrowseUiState.Approval,
    onApprove: (Boolean, Boolean, Boolean) -> Unit,
    onApproveUnsigned: (Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val unsigned = state.publisherIdentity == BrowsePublisherIdentity.LOCAL_UNSIGNED
    var expanded by rememberSaveable(state.sourceId, state.version) { mutableStateOf(false) }
    var downgradeConfirmed by remember(state.sourceId, state.packageSha256, state.publisherFingerprint) { mutableStateOf(false) }
    var legacyMigrationConfirmed by remember(state.sourceId, state.packageSha256, state.publisherFingerprint) { mutableStateOf(false) }
    var nonOfficialConfirmed by remember(state.sourceId, state.packageSha256, state.publisherFingerprint) { mutableStateOf(false) }
    var unsignedRiskConfirmed by remember(state.sourceId, state.packageSha256) { mutableStateOf(false) }
    var identityTransitionConfirmed by remember(state.sourceId, state.packageSha256) { mutableStateOf(false) }
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        val diffCount = if (expanded) state.capabilities.size + state.resourceLimitIncreases.size else 0
        TsuyomiMeasuredPager(itemCount = 13 + diffCount, itemKey = { "approval:${state.sourceId}:$it" }, modifier = Modifier.fillMaxSize(),
            itemContent = { index -> when {
                index == 0 -> BrowsePagedText("${state.sourceName} ${state.version}")
                index == 1 -> BrowsePagedText(stringResource(if (unsigned) R.string.browse_install_unsigned_candidate_summary else R.string.browse_install_candidate_summary, state.sourceId))
                index == 2 -> if (unsigned) BrowsePagedText(stringResource(R.string.browse_install_unsigned_warning))
                index == 3 -> BrowsePagedText(if (unsigned) stringResource(R.string.browse_install_unsigned_digest, state.packageSha256) else state.publisherFingerprint.orEmpty())
                index == 4 -> TsuyomiButton(stringResource(if (expanded) R.string.browse_install_diff_collapse else R.string.browse_install_diff_expand, state.capabilities.size, state.resourceLimitIncreases.size), { expanded = !expanded })
                index in 5 until 5 + diffCount -> {
                    val diffIndex = index - 5
                    BrowsePagedText(if (diffIndex < state.capabilities.size) stringResource(R.string.browse_install_added_capability, state.capabilities[diffIndex])
                        else stringResource(R.string.browse_install_limit_increase, resourceLimitIncreaseLabel(state.resourceLimitIncreases[diffIndex - state.capabilities.size])))
                }
                else -> when (index - diffCount) {
                    5 -> if (state.isDowngrade) TsuyomiCheckboxRow(checked = downgradeConfirmed, onCheckedChange = { downgradeConfirmed = it }, label = stringResource(R.string.browse_approval_downgrade))
                    6 -> if (state.isLegacyMigration || state.requiresSignedIdentityTransition) TsuyomiCheckboxRow(checked = legacyMigrationConfirmed, onCheckedChange = { legacyMigrationConfirmed = it }, label = stringResource(if (state.requiresSignedIdentityTransition) R.string.browse_approval_signed_transition else R.string.browse_approval_legacy_migration))
                    7 -> if (state.requiresNonOfficialConsent) TsuyomiCheckboxRow(checked = nonOfficialConfirmed, onCheckedChange = { nonOfficialConfirmed = it }, label = stringResource(R.string.browse_approval_nonofficial), modifier = Modifier.testTag("browse-approval-nonofficial"))
                    8 -> if (unsigned) TsuyomiCheckboxRow(checked = unsignedRiskConfirmed, onCheckedChange = { unsignedRiskConfirmed = it }, label = stringResource(R.string.browse_approval_unsigned_risk), modifier = Modifier.testTag("browse-approval-unsigned-risk"))
                    9 -> if (state.requiresUnsignedIdentityTransition) TsuyomiCheckboxRow(checked = identityTransitionConfirmed, onCheckedChange = { identityTransitionConfirmed = it }, label = stringResource(R.string.browse_approval_unsigned_transition), modifier = Modifier.testTag("browse-approval-unsigned-transition"))
                    10 -> TsuyomiButton(stringResource(R.string.browse_approval_install_action), {
                        if (unsigned) onApproveUnsigned(downgradeConfirmed, unsignedRiskConfirmed, identityTransitionConfirmed) else onApprove(downgradeConfirmed, legacyMigrationConfirmed, nonOfficialConfirmed)
                    }, enabled = (!state.isDowngrade || downgradeConfirmed) && (!(state.isLegacyMigration || state.requiresSignedIdentityTransition) || legacyMigrationConfirmed) &&
                        (!state.requiresNonOfficialConsent || nonOfficialConfirmed) && (!unsigned || unsignedRiskConfirmed) && (!state.requiresUnsignedIdentityTransition || identityTransitionConfirmed))
                    11 -> TsuyomiButton(stringResource(R.string.browse_cancel_action), onDismiss)
                    else -> Unit
                }
            } })
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(TsuyomiSpacing.Md)) {
            Text("${state.sourceName} ${state.version}", style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    if (unsigned) R.string.browse_install_unsigned_candidate_summary
                    else R.string.browse_install_candidate_summary,
                    state.sourceId,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (unsigned) {
                Text(
                    stringResource(R.string.browse_install_unsigned_warning),
                    modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(R.string.browse_install_unsigned_digest, state.packageSha256),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            TsuyomiVisibility(
                visible = expanded,
                enterFrom = TsuyomiVisibilityEdge.BOTTOM,
            ) {
                Column {
                    if (!unsigned) {
                        Text(
                            stringResource(R.string.browse_install_publisher),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            requireNotNull(state.publisherFingerprint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                TsuyomiCheckboxRow(
                    checked = downgradeConfirmed,
                    onCheckedChange = { downgradeConfirmed = it },
                    label = stringResource(R.string.browse_approval_downgrade),
                )
            }
            if (state.isLegacyMigration || state.requiresSignedIdentityTransition) {
                TsuyomiCheckboxRow(
                    checked = legacyMigrationConfirmed,
                    onCheckedChange = { legacyMigrationConfirmed = it },
                    label = stringResource(
                        if (state.requiresSignedIdentityTransition) R.string.browse_approval_signed_transition
                        else R.string.browse_approval_legacy_migration,
                    ),
                )
            }
            if (state.requiresNonOfficialConsent) {
                TsuyomiCheckboxRow(
                    checked = nonOfficialConfirmed,
                    onCheckedChange = { nonOfficialConfirmed = it },
                    label = stringResource(R.string.browse_approval_nonofficial),
                    modifier = Modifier.testTag("browse-approval-nonofficial"),
                )
            }
            if (unsigned) {
                TsuyomiCheckboxRow(
                    checked = unsignedRiskConfirmed,
                    onCheckedChange = { unsignedRiskConfirmed = it },
                    label = stringResource(R.string.browse_approval_unsigned_risk),
                    modifier = Modifier.testTag("browse-approval-unsigned-risk"),
                )
                if (state.requiresUnsignedIdentityTransition) {
                    TsuyomiCheckboxRow(
                        checked = identityTransitionConfirmed,
                        onCheckedChange = { identityTransitionConfirmed = it },
                        label = stringResource(R.string.browse_approval_unsigned_transition),
                        modifier = Modifier.testTag("browse-approval-unsigned-transition"),
                    )
                }
            }
            TsuyomiButton(
                text = stringResource(R.string.browse_approval_install_action),
                onClick = {
                    if (unsigned) onApproveUnsigned(downgradeConfirmed, unsignedRiskConfirmed, identityTransitionConfirmed)
                    else onApprove(downgradeConfirmed, legacyMigrationConfirmed, nonOfficialConfirmed)
                },
                enabled = (!state.isDowngrade || downgradeConfirmed) &&
                    (!(state.isLegacyMigration || state.requiresSignedIdentityTransition) || legacyMigrationConfirmed) &&
                    (!state.requiresNonOfficialConsent || nonOfficialConfirmed) &&
                    (!unsigned || unsignedRiskConfirmed) &&
                    (!state.requiresUnsignedIdentityTransition || identityTransitionConfirmed),
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
private fun installFailureMessage(reason: BrowseInstallFailure): String = stringResource(
    when (reason) {
        BrowseInstallFailure.FILE_ACCESS -> R.string.browse_failure_file_access
        BrowseInstallFailure.DOWNLOAD_NETWORK -> R.string.browse_failure_download_network
        BrowseInstallFailure.DOWNLOAD_TIMEOUT -> R.string.browse_failure_download_timeout
        BrowseInstallFailure.DOWNLOAD_SERVER -> R.string.browse_failure_download_server
        BrowseInstallFailure.DOWNLOAD_CANCELLED -> R.string.browse_failure_download_cancelled
        BrowseInstallFailure.REPOSITORY -> R.string.browse_failure_repository
        BrowseInstallFailure.STORAGE -> R.string.browse_failure_storage
        BrowseInstallFailure.VERIFICATION -> R.string.browse_failure_verification
        BrowseInstallFailure.IDENTITY_CONFLICT -> R.string.browse_failure_identity_conflict
        BrowseInstallFailure.INSTALL -> R.string.browse_failure_install
        BrowseInstallFailure.EXPIRED_APPROVAL -> R.string.browse_failure_expired_approval
    }
)

private val BrowseInstallFailure.isRetryableDownloadFailure: Boolean
    get() = this == BrowseInstallFailure.DOWNLOAD_NETWORK ||
        this == BrowseInstallFailure.DOWNLOAD_TIMEOUT ||
        this == BrowseInstallFailure.DOWNLOAD_SERVER

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
private fun BrowsePagedText(text: String) {
    TsuyomiMeasuredTextPager(text, Modifier.fillMaxWidth(), fillViewport = false, alwaysShowPageControls = false)
}

@Composable
private fun InstalledSourceEntryButton(source: BrowseInstalledSource, primaryAction: () -> Unit, onSearch: () -> Unit,
    onOpenRemoteLibrary: () -> Unit, onOpenDetails: () -> Unit, onRequestUninstall: () -> Unit) {
    var expanded by rememberSaveable(source.sourceId) { mutableStateOf(false) }
    val searchCount = if (source.homeAvailable) 1 else 0
    val remoteCount = if (source.remoteLibraryAvailable) 1 else 0
    TsuyomiSplitButton(text = stringResource(R.string.browse_source_enter_action), trailingIcon = TsuyomiIcons.Disclosure,
        trailingDescription = stringResource(R.string.browse_source_more_actions, source.name), onLeadingClick = primaryAction,
        onMenuOpen = {}, menuExpanded = expanded, onMenuExpandedChange = { expanded = it }, leadingEnabled = true,
        modifier = Modifier.testTag("browse-source-entry-${source.sourceId}"),
        menuItemCount = searchCount + remoteCount + 2,
        menuItemKey = { index -> when { index < searchCount -> "search"; index < searchCount + remoteCount -> "remote"; index == searchCount + remoteCount -> "details"; else -> "uninstall" } },
        menuItemContent = { index, dismiss ->
            val label = when { index < searchCount -> R.string.browse_source_search_action; index < searchCount + remoteCount -> R.string.browse_remote_library_action;
                index == searchCount + remoteCount -> R.string.browse_source_details_title; else -> R.string.browse_source_uninstall_action }
            TsuyomiMenuItem(stringResource(label), { dismiss(); when {
                index < searchCount -> onSearch(); index < searchCount + remoteCount -> onOpenRemoteLibrary(); index == searchCount + remoteCount -> onOpenDetails(); else -> onRequestUninstall()
            } })
        })
}
