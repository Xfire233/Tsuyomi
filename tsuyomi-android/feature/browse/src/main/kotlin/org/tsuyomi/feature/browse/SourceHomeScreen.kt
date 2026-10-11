/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTabOption
import org.tsuyomi.core.ui.components.TsuyomiTextTabRow
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceHomeFilter
import org.tsuyomi.shared.sourcecontract.SourceHomeFeature
import org.tsuyomi.shared.sourcecontract.SourceHomePage


data class SourceHomeFailure(
    val code: SourceErrorCode?,
    val safeCode: String,
)

data class SourceHomePageViewState(
    val queryKey: String,
    val selectedFilters: Map<String, String>,
    val page: SourceHomePage? = null,
    val replacing: Boolean = false,
    val appending: Boolean = false,
    val replacementFailure: SourceHomeFailure? = null,
    val appendFailure: SourceHomeFailure? = null,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
    val cursorPage: SourceHomePage? = null,
    val cursorPageNumber: Int = 1,
    val cursorPreviousAvailable: Boolean = false,
    val cursorNextAvailable: Boolean = false,
)

sealed interface SourceHomeViewState {
    data object Idle : SourceHomeViewState
    data object Loading : SourceHomeViewState
    data class Content(
        val title: String,
        val primaryFilter: SourceHomeFilter?,
        val selectedPrimary: String,
        val pages: Map<String, SourceHomePageViewState>,
        val featureOpen: Boolean = false,
    ) : SourceHomeViewState {
        val activePageState: SourceHomePageViewState?
            get() = pages[selectedPrimary]

        val activePage: SourceHomePage?
            get() = activePageState?.page
    }
    data class Failure(val code: SourceErrorCode?, val safeCode: String) : SourceHomeViewState
}

/** Standard pager and explicit E-ink cursor-page presentation over the same source state. */
@Composable
fun SourceHomeScreen(
    sourceName: String,
    state: SourceHomeViewState,
    filtersExpanded: Boolean,
    onFiltersExpandedChange: (Boolean) -> Unit,
    onSelectPrimary: (String) -> Unit,
    onSelectFilters: (Map<String, String>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryReplacement: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    onOpenFeature: (SourceHomeFeature) -> Unit,
    onOpenVerification: () -> Unit,
    onScrollPositionChanged: (primary: String, queryKey: String, index: Int, offset: Int) -> Unit,
    coverState: @Composable (SourceBookSummary) -> CoverUiState,
    modifier: Modifier = Modifier,
    onCoverVisibility: (SourceBookSummary, Boolean) -> Unit = { _, _ -> },
    onPreviousPage: () -> Unit = {},
    onNextPage: () -> Unit = onLoadMore,
) {
    val einkPageStateHolder = rememberSaveableStateHolder()
    when (state) {
        SourceHomeViewState.Idle,
        SourceHomeViewState.Loading,
        -> StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.source_home_loading),
            message = stringResource(R.string.source_home_loading_message, sourceName),
            modifier = modifier.fillMaxSize(),
        )
        is SourceHomeViewState.Failure -> SourceHomeFailureView(
            failure = SourceHomeFailure(state.code, state.safeCode),
            onRetry = onRetryReplacement,
            onOpenVerification = onOpenVerification,
            onUseOfflineCache = onUseOfflineCache,
            modifier = modifier,
        )
        is SourceHomeViewState.Content -> if (
            LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
        ) {
            BoxWithConstraints(modifier.fillMaxSize()) {
                val primaryMaxHeight = maxHeight / 3
                Column(Modifier.fillMaxSize()) {
                    if (!state.featureOpen) {
                        state.primaryFilter?.let { filter ->
                            TsuyomiTextTabRow(
                                options = filter.options.map { TsuyomiTabOption(it.value, it.label) },
                                selectedKey = state.selectedPrimary,
                                onSelect = { selected ->
                                    if (selected != state.selectedPrimary) {
                                        onFiltersExpandedChange(false)
                                        onSelectPrimary(selected)
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(max = primaryMaxHeight)
                                    .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs)
                                    .testTag("source-home-primary-tabs"),
                            )
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        val active = state.activePageState
                        val cursorPage = active?.cursorPage
                        if (active == null || cursorPage == null) {
                            active?.replacementFailure?.let { failure ->
                                SourceHomeFailureView(
                                    failure,
                                    onRetryReplacement,
                                    onUseOfflineCache,
                                    onOpenVerification,
                                    Modifier,
                                )
                            } ?: StateView(
                                kind = TsuyomiStateKind.LOADING,
                                title = stringResource(R.string.source_home_loading),
                                message = stringResource(R.string.source_home_loading_message, sourceName),
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            SourceHomeEinkContent(
                                primary = state.selectedPrimary,
                                pageState = active,
                                page = cursorPage,
                                filtersExpanded = filtersExpanded,
                                onFiltersExpandedChange = onFiltersExpandedChange,
                                onSelectFilters = onSelectFilters,
                                onPreviousPage = onPreviousPage,
                                onNextPage = onNextPage,
                                onRetryReplacement = onRetryReplacement,
                                onOpenBook = onOpenBook,
                                onOpenFeature = onOpenFeature,
                                onOpenVerification = onOpenVerification,
                                saveableStateHolder = einkPageStateHolder,
                                coverState = coverState,
                                onCoverVisibility = onCoverVisibility,
                                modifier = Modifier,
                            )
                        }
                    }
                }
            }
        } else {
            SourceHomeStandardContent(
                state = state,
                onSelectPrimary = onSelectPrimary,
                onSelectFilters = onSelectFilters,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onRetryReplacement = onRetryReplacement,
                onUseOfflineCache = onUseOfflineCache,
                onOpenVerification = onOpenVerification,
                onOpenBook = onOpenBook,
                onOpenFeature = onOpenFeature,
                onScrollPositionChanged = onScrollPositionChanged,
                coverState = coverState,
                onCoverVisibility = onCoverVisibility,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun SourceHomeFailureView(
    failure: SourceHomeFailure,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier,
) {
    val verificationRequired = failure.code in setOf(
        SourceErrorCode.SESSION_REQUIRED,
        SourceErrorCode.VERIFICATION_REQUIRED,
    )
    StateView(
        kind = TsuyomiStateKind.ERROR,
        title = stringResource(
            if (verificationRequired) R.string.source_home_verification_title
            else R.string.source_home_failure_title,
        ),
        message = stringResource(
            if (verificationRequired) R.string.source_home_verification_message
            else R.string.source_home_failure_message,
        ),
        actionLabel = stringResource(
            if (verificationRequired) R.string.source_home_open_verification
            else R.string.source_home_retry,
        ),
        onAction = if (verificationRequired) onOpenVerification else onRetry,
        secondaryActionLabel = stringResource(R.string.source_home_use_offline_cache),
        onSecondaryAction = onUseOfflineCache,
        modifier = modifier.fillMaxSize(),
    )
}

