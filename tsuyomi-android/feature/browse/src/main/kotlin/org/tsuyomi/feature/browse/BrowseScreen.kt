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
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.tsuyomi.core.ui.components.InlineStatus
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiIconButton
import org.tsuyomi.core.ui.components.TsuyomiOverflowAction
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiTabOption
import org.tsuyomi.core.ui.components.TsuyomiTabRow
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
)

sealed interface BrowseCatalogAction {
    data object Refresh : BrowseCatalogAction
    data class Install(val sourceId: String) : BrowseCatalogAction
    data class OpenSourceCode(val url: String) : BrowseCatalogAction
}

sealed interface BrowseSourceAction {
    data class OpenHome(val sourceId: String) : BrowseSourceAction
    data class Search(val sourceId: String) : BrowseSourceAction
    data class OpenRemoteLibrary(val sourceId: String) : BrowseSourceAction
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
    onApproveInstall: (allowDowngrade: Boolean, allowLegacyMigration: Boolean) -> Unit,
    onDismissApproval: () -> Unit,
    onDismissFailure: () -> Unit,
    installedSources: List<BrowseInstalledSource>,
    activeSourceId: String? = null,
    catalog: BrowseCatalogState,
    onCatalogAction: (BrowseCatalogAction) -> Unit,
    onSourceAction: (BrowseSourceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSource = installedSources.firstOrNull { it.sourceId == activeSourceId } ?: installedSources.firstOrNull()
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        FrozenEInkBrowseScreen(
            state = state,
            onRequestImport = onRequestImport,
            onOpenInstalledSource = {
                activeSource?.let { onSourceAction(BrowseSourceAction.Search(it.sourceId)) }
            },
            onApproveInstall = { allowDowngrade -> onApproveInstall(allowDowngrade, false) },
            onDismissApproval = onDismissApproval,
            onDismissFailure = onDismissFailure,
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
    onApproveInstall: (Boolean, Boolean) -> Unit,
    onDismissApproval: () -> Unit,
    modifier: Modifier,
) {
    var selectedKey by rememberSaveable { mutableStateOf(BrowseSection.INSTALLED.name) }
    val selectedSection = BrowseSection.entries.firstOrNull { it.name == selectedKey } ?: BrowseSection.INSTALLED
    val installedScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val catalogScrollState = rememberLazyListState()
    var catalogQuery by rememberSaveable { mutableStateOf("") }
    var detailsSourceId by rememberSaveable { mutableStateOf<String?>(null) }
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
        InstallMutationBanner(state, onRequestImport)
        TsuyomiTabRow(
            options = listOf(
                TsuyomiTabOption(BrowseSection.INSTALLED.name, stringResource(R.string.browse_section_installed)),
                TsuyomiTabOption(BrowseSection.AVAILABLE.name, stringResource(R.string.browse_section_installable)),
            ),
            selectedKey = selectedSection.name,
            onSelect = { selectedKey = it },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state is BrowseUiState.Approval) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                ApprovalSourceCard(state, onApproveInstall, onDismissApproval)
                Spacer(Modifier.height(TsuyomiSpacing.Lg))
            }
        } else {
            when (selectedSection) {
                BrowseSection.INSTALLED -> InstalledSourcesContent(
                    state = state,
                    installedSources = installedSources,
                    onRequestImport = onRequestImport,
                    onSourceAction = onSourceAction,
                    scrollState = installedScrollState,
                    modifier = Modifier.weight(1f),
                )
                BrowseSection.AVAILABLE -> BrowseCatalogContent(
                    catalog = catalog,
                    installationAllowed =
                        catalog.status == BrowseCatalogStatus.READY &&
                            !catalog.stale &&
                            catalog.busySourceId == null &&
                            state !is BrowseUiState.Preparing &&
                            state !is BrowseUiState.Approval,
                    refreshAllowed =
                        catalog.busySourceId == null &&
                            state !is BrowseUiState.Preparing &&
                            state !is BrowseUiState.Approval,
                    onCatalogAction = onCatalogAction,
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
}

@Composable
private fun InstalledSourcesContent(
    state: BrowseUiState,
    installedSources: List<BrowseInstalledSource>,
    onRequestImport: () -> Unit,
    onSourceAction: (BrowseSourceAction) -> Unit,
    scrollState: ScrollState,
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

    Column(modifier.verticalScroll(scrollState)) {
        installedSources.forEach { source ->
            InstalledSourceCard(
                source = source,
                onSearch = { onSourceAction(BrowseSourceAction.Search(source.sourceId)) },
                onOpenHome = { onSourceAction(BrowseSourceAction.OpenHome(source.sourceId)) },
                onOpenRemoteLibrary = { onSourceAction(BrowseSourceAction.OpenRemoteLibrary(source.sourceId)) },
            )
        }
        Spacer(Modifier.height(TsuyomiSpacing.Lg))
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
private fun InstalledSourceCard(
    source: BrowseInstalledSource,
    onSearch: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenRemoteLibrary: () -> Unit,
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
                if (source.homeAvailable) {
                    TsuyomiButton(
                        text = stringResource(R.string.browse_source_home_action),
                        onClick = onOpenHome,
                        style = TsuyomiButtonStyle.TEXT,
                    )
                }
                TsuyomiButton(
                    text = stringResource(R.string.browse_source_search_action),
                    onClick = onSearch,
                    modifier = Modifier.testTag("browse-source-search-${source.sourceId}"),
                    style = if (source.homeAvailable) TsuyomiButtonStyle.TEXT else TsuyomiButtonStyle.PRIMARY,
                )
                if (source.remoteLibraryAvailable) {
                    TsuyomiButton(
                        text = stringResource(R.string.browse_remote_library_action),
                        onClick = onOpenRemoteLibrary,
                        style = TsuyomiButtonStyle.TEXT,
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
}.joinToString("、")

@Composable
private fun BrowseCatalogContent(
    catalog: BrowseCatalogState,
    installationAllowed: Boolean,
    refreshAllowed: Boolean,
    onCatalogAction: (BrowseCatalogAction) -> Unit,
    scrollState: LazyListState,
    catalogQuery: String,
    onCatalogQueryChange: (String) -> Unit,
    detailsSourceId: String?,
    onDetailsSourceIdChange: (String?) -> Unit,
    modifier: Modifier,
) {
    if (catalog.items.isEmpty()) {
        CatalogEmptyState(catalog, refreshAllowed, onCatalogAction, modifier)
        return
    }

    val visibleItems = if (catalogQuery.isBlank()) {
        catalog.items
    } else {
        catalog.items.filter { item ->
            item.name.contains(catalogQuery, ignoreCase = true) ||
                item.summary.contains(catalogQuery, ignoreCase = true) ||
                item.language.contains(catalogQuery, ignoreCase = true) ||
                item.sourceId.contains(catalogQuery, ignoreCase = true)
        }
    }
    LazyColumn(
        modifier = modifier,
        state = scrollState,
        contentPadding = PaddingValues(bottom = TsuyomiSpacing.Lg),
    ) {
        item(key = "catalog-search", contentType = "search") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(TsuyomiSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = catalogQuery,
                    onValueChange = onCatalogQueryChange,
                    modifier = Modifier.weight(1f).testTag("browse-catalog-search"),
                    label = { Text(stringResource(R.string.browse_catalog_search_label)) },
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
            items(visibleItems, key = { it.sourceId }, contentType = { "source" }) { item ->
                BrowseCatalogItemRow(
                    item = item,
                    busy = catalog.busySourceId == item.sourceId,
                    installationAllowed = installationAllowed,
                    onOpenDetails = { onDetailsSourceIdChange(item.sourceId) },
                    onInstall = { onCatalogAction(BrowseCatalogAction.Install(item.sourceId)) },
                )
            }
        }
    }
    catalog.items.firstOrNull { it.sourceId == detailsSourceId }?.let { item ->
        BrowseCatalogDetailsDialog(
            item = item,
            onOpenSourceCode = { onCatalogAction(BrowseCatalogAction.OpenSourceCode(item.sourceUrl)) },
            onDismiss = { onDetailsSourceIdChange(null) },
        )
    }
}

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
            message = catalog.problem ?: stringResource(R.string.browse_catalog_unavailable_message),
            modifier = modifier,
        )
        BrowseCatalogStatus.ERROR -> StateView(
            kind = TsuyomiStateKind.ERROR,
            title = stringResource(R.string.browse_catalog_error_title),
            message = catalog.problem ?: stringResource(R.string.browse_catalog_error_message),
            actionLabel = if (refreshAllowed) stringResource(R.string.browse_catalog_refresh_action) else null,
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
            message = catalog.problem ?: stringResource(R.string.browse_catalog_error_message),
            primaryActionLabel = if (refreshAllowed) stringResource(R.string.browse_catalog_refresh_action) else null,
            onPrimaryAction = if (refreshAllowed) {
                { onCatalogAction(BrowseCatalogAction.Refresh) }
            } else {
                null
            },
        )
        BrowseCatalogStatus.UNAVAILABLE -> InfoBanner(
            title = stringResource(R.string.browse_catalog_unavailable_title),
            message = catalog.problem ?: stringResource(R.string.browse_catalog_unavailable_message),
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
                    "${item.version}  ${item.language}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
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
                modifier = Modifier.padding(start = TsuyomiSpacing.Md),
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browse_catalog_details_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text("${item.version}  ${item.language}", style = MaterialTheme.typography.labelMedium)
                Text(
                    item.summary,
                    modifier = Modifier.padding(vertical = TsuyomiSpacing.Md),
                    style = MaterialTheme.typography.bodyMedium,
                )
                CatalogDetailField(stringResource(R.string.browse_catalog_publisher), item.publisherFingerprint)
                CatalogDetailField(stringResource(R.string.browse_catalog_license), item.license)
                CatalogDetailField(stringResource(R.string.browse_catalog_source_revision), item.sourceRevision)
                CatalogDetailField(stringResource(R.string.browse_catalog_source_url), item.sourceUrl)
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSourceCode) {
                Text(stringResource(R.string.browse_catalog_open_source_code))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.browse_close_action))
            }
        },
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
private fun ApprovalSourceCard(
    state: BrowseUiState.Approval,
    onApprove: (Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by rememberSaveable(state.sourceId, state.version) { mutableStateOf(false) }
    var downgradeConfirmed by remember(state.sourceId, state.version) { mutableStateOf(false) }
    var legacyMigrationConfirmed by remember(state.sourceId, state.version) { mutableStateOf(false) }
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
            if (state.isLegacyMigration) {
                CheckboxRow(
                    checked = legacyMigrationConfirmed,
                    onCheckedChange = { legacyMigrationConfirmed = it },
                    label = stringResource(R.string.browse_approval_legacy_migration),
                )
            }
            TsuyomiButton(
                text = stringResource(R.string.browse_approval_install_action),
                onClick = { onApprove(downgradeConfirmed, legacyMigrationConfirmed) },
                enabled = (!state.isDowngrade || downgradeConfirmed) &&
                    (!state.isLegacyMigration || legacyMigrationConfirmed),
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
