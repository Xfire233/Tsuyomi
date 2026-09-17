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
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
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
        val isLegacyMigration: Boolean = false,
        val requiresNonOfficialConsent: Boolean = false,
        val packageSha256: String = "",
    ) : BrowseUiState
    data class PublisherKeyRequired(val keyId: String, val problem: String? = null) : BrowseUiState
    data class Installed(val sourceName: String, val version: String) : BrowseUiState
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
)

enum class BrowseInstallFailure {
    FILE_ACCESS,
    DOWNLOAD,
    REPOSITORY,
    STORAGE,
    VERIFICATION,
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

/**
 * Standard Browse is the repository-backed installed/available split. The explicitly frozen
 * E-ink renderer receives the active installed source, or the first installed source as fallback.
 */
@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onRequestImport: () -> Unit,
    onApproveInstall: (allowDowngrade: Boolean, allowLegacyMigration: Boolean, allowNonOfficial: Boolean) -> Unit,
    onDismissApproval: () -> Unit,
    onDismissFailure: () -> Unit,
    installedSources: List<BrowseInstalledSource>,
    modifier: Modifier = Modifier,
    activeSourceId: String? = null,
    catalog: BrowseCatalogState,
    onCatalogAction: (BrowseCatalogAction) -> Unit,
    onSourceAction: (BrowseSourceAction) -> Unit,
    onProvidePublisherKey: (String) -> Unit = {},
    onDismissPublisherKey: () -> Unit = {},
) {
    val activeSource = installedSources.firstOrNull { it.sourceId == activeSourceId } ?: installedSources.firstOrNull()
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        FrozenEInkBrowseScreen(
            state = state,
            onRequestImport = onRequestImport,
            onOpenInstalledSource = {
                activeSource?.let { onSourceAction(BrowseSourceAction.Search(it.sourceId)) }
            },
            onApproveInstall = { allowDowngrade -> onApproveInstall(allowDowngrade, false, false) },
            onDismissApproval = onDismissApproval,
            onDismissFailure = onDismissFailure,
            onDismissPublisherKey = onDismissPublisherKey,
            modifier = modifier,
            remoteLibraryAvailable = activeSource?.remoteLibraryAvailable == true,
            onOpenRemoteLibrary = {
                activeSource?.let { onSourceAction(BrowseSourceAction.OpenRemoteLibrary(it.sourceId)) }
            },
        )
        return
    }

    BrowseScreenContent(
        state = state,
        installedSources = installedSources,
        catalog = catalog,
        onRequestImport = onRequestImport,
        onCatalogAction = onCatalogAction,
        onSourceAction = onSourceAction,
        onApproveInstall = onApproveInstall,
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
            is BrowseUiState.Approval -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                ApprovalSourceCard(state, onApproveInstall, onDismissApproval)
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
                    repositoryInstall != null && failed.reason == BrowseInstallFailure.DOWNLOAD -> TsuyomiButton(
                        text = stringResource(R.string.browse_retry_download_action),
                        onClick = { onCatalogAction(repositoryInstall) },
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
    var menuExpanded by rememberSaveable(source.sourceId) { mutableStateOf(false) }
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
                source.version,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TsuyomiSplitButton(
            text = stringResource(R.string.browse_source_enter_action),
            trailingIcon = TsuyomiIcons.Disclosure,
            trailingDescription = stringResource(R.string.browse_source_more_actions, source.name),
            onLeadingClick = primaryAction,
            onMenuOpen = {},
            menuExpanded = menuExpanded,
            onMenuExpandedChange = { menuExpanded = it },
            leadingEnabled = true,
            modifier = Modifier.testTag("browse-source-entry-${source.sourceId}"),
        ) { dismissMenu ->
            if (source.homeAvailable) {
                TsuyomiMenuItem(
                    label = stringResource(R.string.browse_source_search_action),
                    onClick = {
                        dismissMenu()
                        onSearch()
                    },
                )
            }
            if (source.remoteLibraryAvailable) {
                TsuyomiMenuItem(
                    label = stringResource(R.string.browse_remote_library_action),
                    onClick = {
                        dismissMenu()
                        onOpenRemoteLibrary()
                    },
                )
            }
            TsuyomiMenuItem(
                label = stringResource(R.string.browse_source_details_title),
                onClick = {
                    dismissMenu()
                    onOpenDetails()
                },
            )
            TsuyomiMenuItem(
                label = stringResource(R.string.browse_source_uninstall_action),
                onClick = {
                    dismissMenu()
                    onRequestUninstall()
                },
            )
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = TsuyomiSpacing.Md))
}

@Composable
private fun InstalledSourceDetailsDialog(source: BrowseInstalledSource, onDismiss: () -> Unit) {
    TsuyomiDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.browse_source_details_title),
        dismissLabel = stringResource(R.string.browse_close_action),
        body = {
            SourceDetailField(stringResource(R.string.browse_source_details_name), source.name)
            SourceDetailField(stringResource(R.string.browse_source_details_id), source.sourceId)
            SourceDetailField(stringResource(R.string.browse_source_details_version), source.version)
            SourceDetailField(stringResource(R.string.browse_source_details_summary), source.summary)
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
    onDismiss: () -> Unit,
) {
    var expanded by rememberSaveable(state.sourceId, state.version) { mutableStateOf(false) }
    var downgradeConfirmed by remember(state.sourceId, state.packageSha256, state.publisherFingerprint) { mutableStateOf(false) }
    var legacyMigrationConfirmed by remember(state.sourceId, state.packageSha256, state.publisherFingerprint) { mutableStateOf(false) }
    var nonOfficialConfirmed by remember(state.sourceId, state.packageSha256, state.publisherFingerprint) { mutableStateOf(false) }
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
            TsuyomiVisibility(
                visible = expanded,
                enterFrom = TsuyomiVisibilityEdge.BOTTOM,
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
                TsuyomiCheckboxRow(
                    checked = downgradeConfirmed,
                    onCheckedChange = { downgradeConfirmed = it },
                    label = stringResource(R.string.browse_approval_downgrade),
                )
            }
            if (state.isLegacyMigration) {
                TsuyomiCheckboxRow(
                    checked = legacyMigrationConfirmed,
                    onCheckedChange = { legacyMigrationConfirmed = it },
                    label = stringResource(R.string.browse_approval_legacy_migration),
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
            TsuyomiButton(
                text = stringResource(R.string.browse_approval_install_action),
                onClick = {
                    onApprove(downgradeConfirmed, legacyMigrationConfirmed, nonOfficialConfirmed)
                },
                enabled = (!state.isDowngrade || downgradeConfirmed) &&
                    (!state.isLegacyMigration || legacyMigrationConfirmed) &&
                    (!state.requiresNonOfficialConsent || nonOfficialConfirmed),
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
    onDismissPublisherKey: () -> Unit,
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
        is BrowseUiState.PublisherKeyRequired -> FrozenEInkFailureSourceScreen(
            state = BrowseUiState.Failure(BrowseInstallFailure.VERIFICATION),
            onRequestImport = onRequestImport,
            onDismiss = onDismissPublisherKey,
            modifier = modifier,
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
            TsuyomiCheckboxRow(
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
        BrowseInstallFailure.DOWNLOAD -> R.string.browse_failure_download
        BrowseInstallFailure.REPOSITORY -> R.string.browse_failure_repository
        BrowseInstallFailure.STORAGE -> R.string.browse_failure_storage
        BrowseInstallFailure.VERIFICATION -> R.string.browse_failure_verification
        BrowseInstallFailure.INSTALL -> R.string.browse_failure_install
        BrowseInstallFailure.EXPIRED_APPROVAL -> R.string.browse_failure_expired_approval
    }
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
