/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiFilterCapsuleButton
import org.tsuyomi.core.ui.components.TsuyomiFilterCapsuleOption
import org.tsuyomi.core.ui.components.TsuyomiFilterCapsuleOptionRow
import org.tsuyomi.core.ui.components.TsuyomiFilterCapsulePanel
import org.tsuyomi.core.ui.components.TsuyomiCoverGridCard
import org.tsuyomi.core.ui.components.TsuyomiNavigationCard
import org.tsuyomi.core.ui.components.TsuyomiPullToRefresh
import org.tsuyomi.core.ui.components.TsuyomiTabRow
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTabOption
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceHomeFilter
import org.tsuyomi.shared.sourcecontract.SourceHomeFeature

private const val APPEND_THRESHOLD_ITEMS = 6

@Composable
internal fun SourceHomeStandardContent(
    state: SourceHomeViewState.Content,
    onSelectPrimary: (String) -> Unit,
    onSelectFilters: (Map<String, String>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryReplacement: () -> Unit,
    onOpenVerification: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    onOpenFeature: (SourceHomeFeature) -> Unit,
    onScrollPositionChanged: (primary: String, queryKey: String, index: Int, offset: Int) -> Unit,
    coverState: @Composable (SourceBookSummary) -> CoverUiState,
    modifier: Modifier,
) {
    if (state.featureOpen) {
        state.activePageState?.let { pageState ->
            SourceHomeCatalogPage(
                primary = state.selectedPrimary,
                active = true,
                pageState = pageState,
                onSelectFilters = onSelectFilters,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onRetryReplacement = onRetryReplacement,
                onOpenVerification = onOpenVerification,
                onOpenBook = onOpenBook,
                onOpenFeature = onOpenFeature,
                onScrollPositionChanged = onScrollPositionChanged,
                coverState = coverState,
            )
        }
        return
    }

    val primaryOptions = state.primaryFilter?.options.orEmpty()
    val primaryValues = if (primaryOptions.isEmpty()) {
        listOf(state.selectedPrimary)
    } else {
        primaryOptions.map { it.value }
    }
    val initialPage = primaryValues.indexOf(state.selectedPrimary).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = primaryValues::size)
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.selectedPrimary, primaryValues) {
        val target = primaryValues.indexOf(state.selectedPrimary)
        if (target >= 0 && pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    LaunchedEffect(pagerState, primaryValues) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { index -> primaryValues.getOrNull(index)?.let(onSelectPrimary) }
    }

    Column(modifier.fillMaxSize()) {
        state.primaryFilter?.let { filter ->
            TsuyomiTabRow(
                options = filter.options.map { TsuyomiTabOption(it.value, it.label) },
                selectedKey = primaryValues.getOrNull(pagerState.currentPage),
                onSelect = { selected ->
                    val target = primaryValues.indexOf(selected)
                    if (target >= 0) scope.launch { pagerState.animateScrollToPage(target) }
                },
                modifier = Modifier.testTag("source-home-primary-tabs"),
            )
        }
        HorizontalPager(
            state = pagerState,
            key = { index -> primaryValues[index] },
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("source-home-pager"),
        ) { pageIndex ->
            val primary = primaryValues[pageIndex]
            val pageState = state.pages[primary] ?: return@HorizontalPager
            SourceHomeCatalogPage(
                primary = primary,
                active = pageIndex == pagerState.currentPage,
                pageState = pageState,
                onSelectFilters = onSelectFilters,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onRetryReplacement = onRetryReplacement,
                onOpenVerification = onOpenVerification,
                onOpenBook = onOpenBook,
                onScrollPositionChanged = onScrollPositionChanged,
                onOpenFeature = onOpenFeature,
                coverState = coverState,
            )
        }
    }
}

@Composable
private fun SourceHomeCatalogPage(
    primary: String,
    active: Boolean,
    pageState: SourceHomePageViewState,
    onSelectFilters: (Map<String, String>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryReplacement: () -> Unit,
    onOpenVerification: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    onOpenFeature: (SourceHomeFeature) -> Unit,
    onScrollPositionChanged: (primary: String, queryKey: String, index: Int, offset: Int) -> Unit,
    coverState: @Composable (SourceBookSummary) -> CoverUiState,
) {
    val page = pageState.page
    if (page == null) {
        val failure = pageState.replacementFailure
        if (failure == null) {
            StateView(
                kind = TsuyomiStateKind.LOADING,
                title = stringResource(R.string.source_home_loading_section),
                message = stringResource(R.string.source_home_loading_section_message),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val verificationRequired = failure.code in setOf(
                SourceErrorCode.SESSION_REQUIRED,
                SourceErrorCode.VERIFICATION_REQUIRED,
            )
            StateView(
                kind = TsuyomiStateKind.ERROR,
                title = stringResource(R.string.source_home_failure_title),
                message = stringResource(R.string.source_home_failure_message, failure.safeCode),
                actionLabel = stringResource(
                    if (verificationRequired) R.string.source_home_open_verification
                    else R.string.source_home_retry,
                ),
                onAction = if (verificationRequired) onOpenVerification else onRetryReplacement,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    val hasPageControls = page.filters.drop(1).isNotEmpty() ||
        pageState.replacing || pageState.replacementFailure != null

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = pageState.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = pageState.firstVisibleItemScrollOffset,
    )
    LaunchedEffect(gridState, primary, pageState.queryKey) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                onScrollPositionChanged(primary, pageState.queryKey, index, offset)
            }
    }
    LaunchedEffect(gridState, active, page.nextCursor, pageState.appending, pageState.appendFailure) {
        if (!active || page.complete || pageState.appending || pageState.appendFailure != null) return@LaunchedEffect
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - APPEND_THRESHOLD_ITEMS
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }

    TsuyomiPullToRefresh(
        isRefreshing = pageState.replacing,
        onRefresh = onRefresh,
        enabled = active && !pageState.replacing,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize().testTag("source-home-book-grid-$primary"),
                contentPadding = PaddingValues(
                    start = TsuyomiSpacing.Md,
                    top = TsuyomiSpacing.Xs,
                    end = TsuyomiSpacing.Md,
                    bottom = 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
            ) {
                if (hasPageControls) {
                    item(key = "controls:${pageState.queryKey}", span = { GridItemSpan(maxLineSpan) }) {
                        SourceHomePageControls(
                            pageState = pageState,
                            onSelectFilters = onSelectFilters,
                            onRetryReplacement = onRetryReplacement,
                        )
                    }
                }
                page.sections.forEach { section ->
                    item(
                        key = "section:${section.id}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        SourceHomeSectionHeading(section.title, section.items.size)
                    }
                    items(
                        items = section.items,
                        key = { book -> "${section.id}:${book.identity.sourceId}:${book.identity.remoteBookId}" },
                    ) { book ->
                        TsuyomiCoverGridCard(
                            title = book.title,
                            supportingText = book.author,
                            onClick = { onOpenBook(book) },
                            cover = { CoverImage(coverState(book), Modifier.fillMaxSize()) },
                            modifier = Modifier.testTag("source-home-book-${book.identity.remoteBookId}"),
                        )
                    }
                }
                page.features.forEach { feature ->
                    item(key = "feature:${feature.id}", span = { GridItemSpan(maxLineSpan) }) {
                        TsuyomiNavigationCard(
                            title = feature.title,
                            supportingText = feature.supportingText,
                            onClick = { onOpenFeature(feature) },
                            modifier = Modifier.testTag("source-home-feature-${feature.id}"),
                        )
                    }
                }
                when {
                    pageState.appending -> item(key = "append-loading", span = { GridItemSpan(maxLineSpan) }) {
                        SourceHomeFooterMessage(stringResource(R.string.source_home_loading_more))
                    }
                    pageState.appendFailure != null -> item(key = "append-error", span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = TsuyomiSpacing.Sm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.source_home_append_failure,
                                    pageState.appendFailure.safeCode,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TsuyomiButton(
                                text = stringResource(R.string.source_home_retry_append),
                                onClick = onLoadMore,
                                style = TsuyomiButtonStyle.TEXT,
                            )
                        }
                    }
                }
            }
            TsuyomiAdaptiveListFab(
                state = gridState,
                topLabel = stringResource(R.string.source_home_top),
                endLabel = stringResource(R.string.source_home_end),
                modifier = Modifier.align(Alignment.BottomEnd).padding(TsuyomiSpacing.Md),
            )
        }
    }
}

@Composable
private fun SourceHomePageControls(
    pageState: SourceHomePageViewState,
    onSelectFilters: (Map<String, String>) -> Unit,
    onRetryReplacement: () -> Unit,
) {
    val page = requireNotNull(pageState.page)
    val primaryId = page.filters.firstOrNull()?.id
    val secondaryFilters = page.filters.filterNot { it.id == primaryId }
    val leadingFilter = secondaryFilters.firstOrNull()
    val compactFilters = secondaryFilters.drop(1)
    var expandedFilterId by rememberSaveable(pageState.queryKey) { mutableStateOf<String?>(null) }

    fun toggle(filter: SourceHomeFilter) {
        expandedFilterId = if (expandedFilterId == filter.id) null else filter.id
    }

    fun select(filter: SourceHomeFilter, value: String) {
        expandedFilterId = null
        if (pageState.selectedFilters[filter.id] != value) {
            onSelectFilters(pageState.selectedFilters + (filter.id to value))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TsuyomiSpacing.Xs)
            .testTag("source-home-page-controls"),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
    ) {
        if (leadingFilter != null) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("source-home-filter-row"),
                horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TsuyomiFilterCapsuleOptionRow(
                    options = leadingFilter.options.map { TsuyomiFilterCapsuleOption(it.value, it.label) },
                    selectedKey = pageState.selectedFilters[leadingFilter.id],
                    expanded = expandedFilterId == leadingFilter.id,
                    expandedStateDescription = stringResource(
                        R.string.source_home_filter_collapse,
                        leadingFilter.label,
                    ),
                    collapsedStateDescription = stringResource(
                        R.string.source_home_filter_expand,
                        leadingFilter.label,
                    ),
                    onToggleExpanded = { toggle(leadingFilter) },
                    onSelect = { value -> select(leadingFilter, value) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("source-home-primary-filter-capsule"),
                )
                compactFilters.forEachIndexed { index, filter ->
                    val selectedLabel = filter.options
                        .firstOrNull { it.value == pageState.selectedFilters[filter.id] }
                        ?.label
                        .orEmpty()
                    TsuyomiFilterCapsuleButton(
                        label = selectedLabel.ifBlank { filter.label },
                        expanded = expandedFilterId == filter.id,
                        expandedStateDescription = stringResource(
                            R.string.source_home_filter_collapse,
                            filter.label,
                        ),
                        collapsedStateDescription = stringResource(
                            R.string.source_home_filter_expand,
                            filter.label,
                        ),
                        onClick = { toggle(filter) },
                        modifier = Modifier
                            .width(144.dp)
                            .testTag("source-home-secondary-filter-capsule-$index"),
                        emphasized = true,
                    )
                }
            }
        }

        val instantMotion = LocalDisplayEnvironment.current.instantMotion
        AnimatedContent(
            targetState = expandedFilterId,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                if (instantMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (expandVertically(
                        animationSpec = tween(
                            TsuyomiMotion.SELECTION_DURATION_MS,
                            easing = TsuyomiMotion.Easing,
                        ),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(
                        tween(TsuyomiMotion.SELECTION_DURATION_MS, easing = TsuyomiMotion.Easing),
                    )) togetherWith (shrinkVertically(
                        animationSpec = tween(
                            TsuyomiMotion.SELECTION_DURATION_MS,
                            easing = TsuyomiMotion.Easing,
                        ),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(
                        tween(TsuyomiMotion.SELECTION_DURATION_MS, easing = TsuyomiMotion.Easing),
                    ))
                }
            },
            label = "sourceHomeFilterPanel",
        ) { filterId ->
            val expandedFilter = secondaryFilters.firstOrNull { it.id == filterId }
            if (expandedFilter != null) {
                TsuyomiFilterCapsulePanel(
                    options = expandedFilter.options.map { TsuyomiFilterCapsuleOption(it.value, it.label) },
                    selectedKey = pageState.selectedFilters[expandedFilter.id],
                    onSelect = { value -> select(expandedFilter, value) },
                    modifier = Modifier.testTag("source-home-filter-panel"),
                )
            }
        }

        when {
            pageState.replacing -> SourceHomeFooterMessage(stringResource(R.string.source_home_updating))
            pageState.replacementFailure != null -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.source_home_inline_failure,
                        pageState.replacementFailure.safeCode,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TsuyomiButton(
                    text = stringResource(R.string.source_home_retry),
                    onClick = onRetryReplacement,
                    style = TsuyomiButtonStyle.TEXT,
                )
            }
        }
    }
}

@Composable
private fun SourceHomeFooterMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.fillMaxWidth().padding(vertical = TsuyomiSpacing.Sm),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SourceHomeSectionHeading(title: String, itemCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TsuyomiSpacing.Xs)
            .testTag("source-home-section-heading"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.source_home_section_count, itemCount),
            modifier = Modifier.padding(start = TsuyomiSpacing.Sm),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
