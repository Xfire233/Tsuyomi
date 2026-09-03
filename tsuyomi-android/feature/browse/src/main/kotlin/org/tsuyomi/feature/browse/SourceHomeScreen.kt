/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiTabRow
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTabOption
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

/** Standard host-owned source Home pager plus the frozen E-ink presentation. */
@Composable
fun SourceHomeScreen(
    sourceName: String,
    state: SourceHomeViewState,
    remoteLibraryAvailable: Boolean,
    verificationAvailable: Boolean,
    onSelectPrimary: (String) -> Unit,
    onSelectFilters: (Map<String, String>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryReplacement: () -> Unit,
    onSearch: () -> Unit,
    onOpenRemoteLibrary: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    onOpenFeature: (SourceHomeFeature) -> Unit,
    onOpenVerification: () -> Unit,
    onScrollPositionChanged: (primary: String, queryKey: String, index: Int, offset: Int) -> Unit,
    coverState: @Composable (SourceBookSummary) -> CoverUiState,
    modifier: Modifier = Modifier,
) {
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
            modifier = modifier,
        )
        is SourceHomeViewState.Content -> if (
            LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
        ) {
            val active = state.activePageState
            if (active?.page == null) {
                active?.replacementFailure?.let { failure ->
                    SourceHomeFailureView(failure, onRetryReplacement, onOpenVerification, modifier)
                } ?: StateView(
                    kind = TsuyomiStateKind.LOADING,
                    title = stringResource(R.string.source_home_loading),
                    message = stringResource(R.string.source_home_loading_message, sourceName),
                    modifier = modifier.fillMaxSize(),
                )
            } else {
                FrozenEInkSourceHomeContent(
                    pageState = active,
                    remoteLibraryAvailable = remoteLibraryAvailable,
                    verificationAvailable = verificationAvailable,
                    onSelectPrimary = onSelectPrimary,
                    onSelectFilters = onSelectFilters,
                    onRefresh = onRefresh,
                    onLoadMore = onLoadMore,
                    onSearch = onSearch,
                    onOpenRemoteLibrary = onOpenRemoteLibrary,
                    onOpenVerification = onOpenVerification,
                    onOpenBook = onOpenBook,
                    coverState = coverState,
                    modifier = modifier,
                )
            }
        } else {
            SourceHomeStandardContent(
                state = state,
                onSelectPrimary = onSelectPrimary,
                onSelectFilters = onSelectFilters,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onRetryReplacement = onRetryReplacement,
                onOpenVerification = onOpenVerification,
                onOpenBook = onOpenBook,
                onOpenFeature = onOpenFeature,
                onScrollPositionChanged = onScrollPositionChanged,
                coverState = coverState,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun SourceHomeFailureView(
    failure: SourceHomeFailure,
    onRetry: () -> Unit,
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
        message = stringResource(R.string.source_home_failure_message, failure.safeCode),
        actionLabel = stringResource(
            if (verificationRequired) R.string.source_home_open_verification
            else R.string.source_home_retry,
        ),
        onAction = if (verificationRequired) onOpenVerification else onRetry,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun FrozenEInkSourceHomeContent(
    pageState: SourceHomePageViewState,
    remoteLibraryAvailable: Boolean,
    verificationAvailable: Boolean,
    onSelectPrimary: (String) -> Unit,
    onSelectFilters: (Map<String, String>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSearch: () -> Unit,
    onOpenRemoteLibrary: () -> Unit,
    onOpenVerification: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    coverState: @Composable (SourceBookSummary) -> CoverUiState,
    modifier: Modifier,
) {
    val page = requireNotNull(pageState.page)
    val listState = rememberLazyListState()
    val primaryFilter = page.filters.firstOrNull()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "quick-actions") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TsuyomiButton(
                        text = stringResource(R.string.browse_source_search_action),
                        onClick = onSearch,
                        style = TsuyomiButtonStyle.TEXT,
                    )
                    if (remoteLibraryAvailable) {
                        TsuyomiButton(
                            text = stringResource(R.string.browse_remote_library_action),
                            onClick = onOpenRemoteLibrary,
                            style = TsuyomiButtonStyle.TEXT,
                        )
                    }
                    if (verificationAvailable) {
                        TsuyomiButton(
                            text = stringResource(R.string.browse_source_verification_action),
                            onClick = onOpenVerification,
                            style = TsuyomiButtonStyle.TEXT,
                        )
                    }
                    TsuyomiButton(
                        text = stringResource(R.string.source_home_refresh),
                        onClick = onRefresh,
                        style = TsuyomiButtonStyle.SECONDARY,
                    )
                }
            }
            primaryFilter?.let { filter ->
                item(key = "primary-tabs:${filter.id}") {
                    TsuyomiTabRow(
                        options = filter.options.map { TsuyomiTabOption(it.value, it.label) },
                        selectedKey = pageState.selectedFilters[filter.id],
                        onSelect = onSelectPrimary,
                        equalWidthWhenFits = false,
                    )
                }
            }
            page.filters.drop(1).forEach { filter ->
                item(key = "filter:${filter.id}") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = filter.label,
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            filter.options.forEach { option ->
                                TsuyomiButton(
                                    text = option.label,
                                    onClick = {
                                        onSelectFilters(pageState.selectedFilters + (filter.id to option.value))
                                    },
                                    style = if (pageState.selectedFilters[filter.id] == option.value) {
                                        TsuyomiButtonStyle.SECONDARY
                                    } else {
                                        TsuyomiButtonStyle.TEXT
                                    },
                                )
                            }
                        }
                    }
                }
            }
            page.sections.forEach { section ->
                item(key = "section:${section.id}") {
                    Text(
                        text = section.title,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(section.items, key = { "${section.id}:${it.identity.sourceId}:${it.identity.remoteBookId}" }) { book ->
                    SourceHomeBookRow(book, coverState(book), onOpenBook)
                }
            }
            if (!page.complete) {
                item(key = "load-more") {
                    TsuyomiButton(
                        text = if (pageState.appending) {
                            stringResource(R.string.source_home_loading_more)
                        } else {
                            stringResource(R.string.source_home_load_more)
                        },
                        onClick = onLoadMore,
                        enabled = !pageState.appending,
                        style = TsuyomiButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
        TsuyomiAdaptiveListFab(
            state = listState,
            topLabel = stringResource(R.string.source_home_top),
            endLabel = stringResource(R.string.source_home_end),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun SourceHomeBookRow(
    book: SourceBookSummary,
    coverState: CoverUiState,
    onOpenBook: (SourceBookSummary) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clickable(role = Role.Button) { onOpenBook(book) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(coverState, Modifier.size(width = 64.dp, height = 96.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            book.author?.let { author ->
                Text(
                    author,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
