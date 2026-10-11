/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.Role
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.LocalTsuyomiPagerItemVisible
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import org.tsuyomi.shared.sourcecontract.SourceHomeSection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import org.tsuyomi.core.ui.components.TsuyomiToggleChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.components.TsuyomiAnimatedContent
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiCoverGridCard
import org.tsuyomi.core.ui.components.TsuyomiCollapsibleChrome
import org.tsuyomi.core.ui.components.TsuyomiEnterAlwaysChromeState
import org.tsuyomi.core.ui.components.rememberTsuyomiEnterAlwaysChromeState
import org.tsuyomi.core.ui.components.TsuyomiFilterCapsuleButton
import org.tsuyomi.core.ui.components.TsuyomiFilterCapsuleOption
import org.tsuyomi.core.ui.components.TsuyomiFilterCapsuleOptionRow
import org.tsuyomi.core.ui.components.TsuyomiFilterCapsulePanel
import org.tsuyomi.core.ui.components.TsuyomiNavigationCard
import org.tsuyomi.core.ui.components.PaginationBar
import org.tsuyomi.core.ui.components.TsuyomiPullToRefresh
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTabOption
import org.tsuyomi.core.ui.components.TsuyomiTextTabRow
import org.tsuyomi.core.ui.components.TsuyomiTextTabPagerDefaults
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceHomeFilter
import org.tsuyomi.shared.sourcecontract.SourceHomeFilterOption
import org.tsuyomi.shared.sourcecontract.SourceHomeFeature
import org.tsuyomi.shared.sourcecontract.SourceHomePage

private const val APPEND_THRESHOLD_ITEMS = 6

 
private fun sourceHomeBookKey(book: SourceBookSummary): String =
    "${book.identity.sourceId}:${book.identity.remoteBookId}"

@Composable
internal fun SourceHomeStandardContent(
    state: SourceHomeViewState.Content,
    onSelectPrimary: (String) -> Unit,
    onSelectFilters: (Map<String, String>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryReplacement: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    onOpenFeature: (SourceHomeFeature) -> Unit,
    onScrollPositionChanged: (primary: String, queryKey: String, index: Int, offset: Int) -> Unit,
    coverState: @Composable (SourceBookSummary) -> CoverUiState,
    onCoverVisibility: (SourceBookSummary, Boolean) -> Unit,
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
                onUseOfflineCache = onUseOfflineCache,
                onOpenVerification = onOpenVerification,
                onOpenBook = onOpenBook,
                onOpenFeature = onOpenFeature,
                onScrollPositionChanged = onScrollPositionChanged,
                coverState = coverState,
                onCoverVisibility = onCoverVisibility,
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
    val instantMotion = LocalDisplayEnvironment.current.instantMotion || rememberSystemReducedMotion()
    var primaryTransitionJob by remember { mutableStateOf<Job?>(null) }
    var primaryChromeHeight by remember { mutableIntStateOf(0) }
    var pageChromeHeight by remember { mutableIntStateOf(0) }
    var panelExpanded by remember { mutableStateOf(false) }
    val chrome = rememberTsuyomiEnterAlwaysChromeState {
        !panelExpanded && pagerState.currentPageOffsetFraction == 0f &&
            pagerState.currentPage == pagerState.settledPage && state.activePageState?.replacing != true
    }
    val showMenus = stringResource(R.string.source_home_show_menus)

    // A child's vertical fling can toggle Pager's scrolling flag without moving a page.
    LaunchedEffect(pagerState) {
        snapshotFlow {
            pagerState.currentPageOffsetFraction != 0f || pagerState.currentPage != pagerState.settledPage
        }.collect { changingPage ->
            if (changingPage) chrome.reveal()
        }
    }
    LaunchedEffect(state.selectedPrimary) { chrome.reveal() }

    LaunchedEffect(state.selectedPrimary, primaryValues, instantMotion) {
        val target = primaryValues.indexOf(state.selectedPrimary)
        if (target >= 0 && pagerState.settledPage != target) {
            if (instantMotion) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState, primaryValues) {
        snapshotFlow {
            pagerState.settledPage.takeIf {
                !pagerState.isScrollInProgress && pagerState.currentPageOffsetFraction == 0f
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { index -> primaryValues.getOrNull(index)?.let(onSelectPrimary) }
    }

    Column(
        modifier.fillMaxSize().nestedScroll(chrome.nestedScrollConnection)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp && !chrome.isFullyExpanded) {
                    chrome.reveal()
                    true
                } else false
            },
    ) {
        state.primaryFilter?.let { filter ->
            TsuyomiCollapsibleChrome(
                state = chrome,
                onExpandedHeightChanged = { height ->
                    primaryChromeHeight = height
                    chrome.updateExpandedHeight(height + pageChromeHeight)
                },
            ) {
                TsuyomiTextTabRow(
                    options = filter.options.map { TsuyomiTabOption(it.value, it.label) },
                    selectedKey = primaryValues.getOrNull(pagerState.currentPage),
                    onSelect = { selected ->
                        chrome.reveal()
                        val target = primaryValues.indexOf(selected)
                        if (target >= 0 &&
                            (target != pagerState.settledPage || pagerState.isScrollInProgress)
                        ) {
                            primaryTransitionJob?.cancel()
                            primaryTransitionJob = scope.launch {
                                if (instantMotion) pagerState.scrollToPage(target)
                                else pagerState.animateScrollToPage(target)
                            }
                        }
                    },
                    modifier = Modifier.testTag("source-home-primary-tabs"),
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            flingBehavior = TsuyomiTextTabPagerDefaults.flingBehavior(pagerState),
            key = { index -> primaryValues[index] },
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("source-home-pager")
                .semantics {
                    if (!chrome.isFullyExpanded) expand(showMenus) {
                        chrome.reveal()
                        true
                    }
                },
        ) { pageIndex ->
            val primary = primaryValues[pageIndex]
            val pageState = state.pages[primary] ?: return@HorizontalPager
            SourceHomeCatalogPage(
                primary = primary,
                active = pageIndex == pagerState.settledPage,
                pageState = pageState,
                onSelectFilters = onSelectFilters,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onRetryReplacement = onRetryReplacement,
                onUseOfflineCache = onUseOfflineCache,
                onOpenVerification = onOpenVerification,
                onOpenBook = onOpenBook,
                onScrollPositionChanged = onScrollPositionChanged,
                onOpenFeature = onOpenFeature,
                coverState = coverState,
                onCoverVisibility = onCoverVisibility,
                chrome = chrome,
                onControlsHeightChanged = { height ->
                    pageChromeHeight = height
                    chrome.updateExpandedHeight(primaryChromeHeight + height)
                },
                onPanelExpandedChange = { panelExpanded = it },
            )
        }
    }
}


@Composable
internal fun SourceHomeEinkContent(
    primary: String, pageState: SourceHomePageViewState, page: SourceHomePage,
    filtersExpanded: Boolean, onFiltersExpandedChange: (Boolean) -> Unit,
    onSelectFilters: (Map<String, String>) -> Unit,
    onPreviousPage: () -> Unit, onNextPage: () -> Unit, onRetryReplacement: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit, onOpenFeature: (SourceHomeFeature) -> Unit,
    onOpenVerification: () -> Unit, saveableStateHolder: SaveableStateHolder,
    coverState: @Composable (SourceBookSummary) -> CoverUiState,
    onCoverVisibility: (SourceBookSummary, Boolean) -> Unit, modifier: Modifier,
) {
    val primaryId = page.filters.firstOrNull()?.id
    val filters = page.filters.filterNot { it.id == primaryId }
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp }
    var previousCursorPage by rememberSaveable(primary, pageState.queryKey) {
        mutableIntStateOf(pageState.cursorPageNumber)
    }
    LaunchedEffect(pageState.cursorPageNumber) {
        if (previousCursorPage != pageState.cursorPageNumber) onFiltersExpandedChange(false)
        previousCursorPage = pageState.cursorPageNumber
    }
    fun turnPage(turn: () -> Unit) {
        onFiltersExpandedChange(false)
        turn()
    }
    Column(modifier.fillMaxSize()) {
        if (filters.isNotEmpty()) {
            TsuyomiToggleChip(
                text = stringResource(
                    if (filtersExpanded) R.string.source_home_collapse_filters
                    else R.string.source_home_expand_filters,
                ),
                selected = filtersExpanded,
                stateDescription = stringResource(
                    if (filtersExpanded) R.string.source_home_filters_expanded
                    else R.string.source_home_filters_collapsed,
                ),
                onClick = { onFiltersExpandedChange(!filtersExpanded) },
                leadingIcon = if (filtersExpanded) TsuyomiIcons.ToTop else TsuyomiIcons.ToBottom,
                role = Role.Button,
                modifier = Modifier
                    .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs)
                    .testTag("source-home-toggle-filters"),
            )
        }
    saveableStateHolder.SaveableStateProvider("$primary:${pageState.queryKey}:${pageState.cursorPageNumber}") {
        // Register the semantic anchor in its query scope, not the retained measurement subcomposition.
        val contentState = rememberTsuyomiMeasuredPagerState()
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).testTag("source-home-book-grid-$primary")
                .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs),
        ) {
            val density = LocalDensity.current
            var controlsHeight by remember { mutableIntStateOf(0) }
            var catalogTop by remember { mutableFloatStateOf(0f) }
            var filterPanelHeight by remember { mutableIntStateOf(0) }
            val columns = if (wide) {
                ((maxWidth + TsuyomiSpacing.Sm) / (150.dp + TsuyomiSpacing.Sm)).toInt().coerceAtLeast(1)
            } else 3
            val sectionCount = page.sections.sumOf { 1 + (it.items.size + columns - 1) / columns }
            val statusStart = sectionCount + page.features.size
            val statusCount = (if (pageState.replacing) 1 else 0) +
                (if (pageState.replacementFailure != null) 1 else 0) +
                (if (pageState.appendFailure != null) 1 else 0)
            fun sectionAt(index: Int): Pair<SourceHomeSection, Int> {
                var remaining = index
                for (section in page.sections) {
                    val size = 1 + (section.items.size + columns - 1) / columns
                    if (remaining < size) return section to remaining
                    remaining -= size
                }
                error("Section index outside source projection")
            }
            fun itemKey(index: Int): String = when {
                index < sectionCount -> sectionAt(index).let { (section, row) ->
                    if (row == 0) "section:${section.id}"
                    else "book:${section.id}:${sourceHomeBookKey(section.items[(row - 1) * columns])}"
                }
                index < statusStart -> "feature:${page.features[index - sectionCount].id}"
                else -> "status:${index - statusStart}"
            }
            val count = statusStart + statusCount
            TsuyomiMeasuredPager(
                itemCount = count,
                itemKey = ::itemKey,
                state = contentState,
                modifier = Modifier.fillMaxSize().onGloballyPositioned { catalogTop = it.positionInRoot().y },
                verticalSpacing = TsuyomiSpacing.Sm,
                alwaysShowPageControls = pageState.cursorPreviousAvailable || pageState.cursorNextAvailable,
                itemContent = { index ->
                    var itemTop by remember { mutableFloatStateOf(Float.NEGATIVE_INFINITY) }
                    // Only rows touched by the bounded panel lose focus and semantics.
                    val covered = filtersExpanded && filters.isNotEmpty() &&
                        (filterPanelHeight == 0 || itemTop < catalogTop + filterPanelHeight)
                    Box(
                        Modifier.fillMaxWidth()
                            .onGloballyPositioned { itemTop = it.positionInRoot().y }
                            .focusProperties { canFocus = !covered }
                            .then(if (covered) Modifier.clearAndSetSemantics {} else Modifier),
                    ) {
                        when {
                            index < sectionCount -> {
                                val (section, row) = sectionAt(index)
                                if (row == 0) SourceHomeSectionHeading(section.title, section.items.size)
                                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
                                    val first = (row - 1) * columns
                                    repeat(columns) { column ->
                                        Box(Modifier.weight(1f)) {
                                            section.items.getOrNull(first + column)?.let { book ->
                                                SourceHomeBookCard(book, true, coverState, onCoverVisibility, onOpenBook)
                                            }
                                        }
                                    }
                                }
                            }
                            index < statusStart -> {
                                val feature = page.features[index - sectionCount]
                                TsuyomiNavigationCard(
                                    title = feature.title,
                                    supportingText = feature.supportingText,
                                    onClick = { onOpenFeature(feature) },
                                    modifier = Modifier.testTag("source-home-feature-${feature.id}"),
                                )
                            }
                            else -> {
                                var status = index - statusStart
                                if (pageState.replacing && status == 0) SourceHomeFooterMessage(stringResource(R.string.source_home_updating))
                                else {
                                    if (pageState.replacing) status--
                                    val replacement = pageState.replacementFailure != null && status == 0
                                    val failure = if (replacement) pageState.replacementFailure else pageState.appendFailure
                                    val verification = failure?.code == SourceErrorCode.SESSION_REQUIRED || failure?.code == SourceErrorCode.VERIFICATION_REQUIRED
                                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        SourceHomePagedText(stringResource(if (verification) R.string.source_home_inline_verification else R.string.source_home_inline_failure))
                                        TsuyomiButton(
                                            stringResource(if (verification) R.string.source_home_open_verification else R.string.source_home_retry),
                                            if (verification) onOpenVerification else if (replacement) onRetryReplacement else ({ turnPage(onNextPage) }),
                                            style = TsuyomiButtonStyle.TEXT,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                pageControls = { localPage, canGoPrevious, canGoNext, previous, next, pageCount, first, last ->
                    Column(
                        Modifier.fillMaxWidth().testTag("source-home-content-page-controls")
                            .onSizeChanged { controlsHeight = it.height },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.source_home_cursor_page, pageState.cursorPageNumber), style = MaterialTheme.typography.labelMedium)
                        PaginationBar(
                            page = localPage, pageCount = pageCount,
                            onPrevious = { turnPage(if (canGoPrevious) previous else onPreviousPage) },
                            onNext = { turnPage(if (canGoNext) next else onNextPage) },
                            loading = pageState.appending,
                            hasPreviousPage = canGoPrevious || pageState.cursorPreviousAvailable,
                            hasNextPage = canGoNext || (pageState.cursorNextAvailable && pageState.appendFailure == null),
                            onFirst = { turnPage(first) },
                            onLast = { turnPage(last) },
                        )
                    }
                },
            )
            if (filtersExpanded && filters.isNotEmpty()) {
                // Toggle only this natural-height overlay; the catalog viewport and anchor stay fixed.
                val catalogHeight = (maxHeight - with(density) { controlsHeight.toDp() }).coerceAtLeast(0.dp)
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = catalogHeight / 2)
                        .onSizeChanged { filterPanelHeight = it.height }
                        .testTag("source-home-eink-filter-panel"),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    SourceHomeEinkFilters(
                        filters = filters,
                        selectedFilters = pageState.selectedFilters,
                        onSelectFilters = { onFiltersExpandedChange(false); onSelectFilters(it) },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun SourceHomeEinkFilters(
    filters: List<SourceHomeFilter>,
    selectedFilters: Map<String, String>,
    onSelectFilters: (Map<String, String>) -> Unit,
) {
    TsuyomiMeasuredPager(
        itemCount = filters.size,
        itemKey = { "filter:${filters[it].id}" },
        verticalSpacing = TsuyomiSpacing.Sm,
        fillViewport = false,
        modifier = Modifier.fillMaxWidth(),
        itemContent = { index ->
            val filter = filters[index]
            TsuyomiMeasuredPager(
                itemCount = filter.options.size + 1,
                itemKey = { if (it == 0) "${filter.id}:heading" else "${filter.id}:${filter.options[it - 1].value}" },
                fillViewport = false,
                wrapItems = true,
                horizontalSpacing = TsuyomiSpacing.Sm,
                verticalSpacing = TsuyomiSpacing.Sm,
                modifier = Modifier.fillMaxWidth(),
                itemContent = { optionIndex ->
                    if (optionIndex == 0) SourceHomePagedText(filter.label)
                    else {
                        val option = filter.options[optionIndex - 1]
                        val selected = selectedFilters[filter.id] == option.value
                        TsuyomiToggleChip(
                            option.label, selected, if (selected) "已选择" else "未选择",
                            {
                                if (!selected) {
                                    onSelectFilters(selectedFilters + (filter.id to option.value))
                                }
                            },
                            modifier = Modifier.testTag("source-home-filter-choice-${filter.id}-${option.value}"),
                            role = Role.RadioButton,
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun SourceHomeBookCard(
    book: SourceBookSummary,
    active: Boolean,
    coverState: @Composable (SourceBookSummary) -> CoverUiState,
    onCoverVisibility: (SourceBookSummary, Boolean) -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
) {
    val visible = active && LocalTsuyomiPagerItemVisible.current
    DisposableEffect(book.identity, book.coverUrl, book.canonicalUrl, visible) {
        if (visible) onCoverVisibility(book, true)
        onDispose { if (visible) onCoverVisibility(book, false) }
    }
    TsuyomiCoverGridCard(
        title = book.title,
        supportingText = null,
        onClick = { onOpenBook(book) },
        cover = { CoverImage(coverState(book), Modifier.fillMaxSize()) },
        modifier = Modifier.testTag("source-home-book-${book.identity.remoteBookId}"),
        titleInsideCover = true,
    )
}

@Composable
private fun SourceHomePagedText(text: String) {
    TsuyomiMeasuredTextPager(text, Modifier.fillMaxWidth(), fillViewport = false, alwaysShowPageControls = false)
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
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    onOpenFeature: (SourceHomeFeature) -> Unit,
    onScrollPositionChanged: (primary: String, queryKey: String, index: Int, offset: Int) -> Unit,
    coverState: @Composable (SourceBookSummary) -> CoverUiState,
    onCoverVisibility: (SourceBookSummary, Boolean) -> Unit,
    chrome: TsuyomiEnterAlwaysChromeState? = null,
    onControlsHeightChanged: (Int) -> Unit = {},
    onPanelExpandedChange: (Boolean) -> Unit = {},
) {
    val page = pageState.page
    if (page == null) {
        SideEffect {
            if (active) {
                onControlsHeightChanged(0)
                onPanelExpandedChange(false)
            }
        }
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
                onAction = if (verificationRequired) onOpenVerification else onRetryReplacement,
                secondaryActionLabel = stringResource(R.string.source_home_use_offline_cache),
                onSecondaryAction = onUseOfflineCache,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp }
    val hasPageControls = page.filters.drop(1).isNotEmpty() ||
        pageState.replacing || pageState.replacementFailure != null

    key(pageState.queryKey) {
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = pageState.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = pageState.firstVisibleItemScrollOffset,
    )
    var controlsHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(active, controlsHeight) {
        if (active) onControlsHeightChanged(controlsHeight)
    }
    LaunchedEffect(active, pageState.replacing, pageState.replacementFailure) {
        if (active && (pageState.replacing || pageState.replacementFailure != null)) chrome?.reveal()
    }
    LaunchedEffect(gridState, primary, pageState.queryKey, active) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                if (active) onScrollPositionChanged(primary, pageState.queryKey, index, offset)
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

    Column(Modifier.fillMaxSize()) {
        if (hasPageControls) {
            if (chrome == null) {
                SourceHomePageControls(pageState, onSelectFilters, onRetryReplacement, onOpenVerification, active)
            } else {
                TsuyomiCollapsibleChrome(
                    state = chrome,
                    onExpandedHeightChanged = { controlsHeight = it },
                ) {
                    SourceHomePageControls(
                        pageState, onSelectFilters, onRetryReplacement, onOpenVerification, active,
                        onPanelExpandedChange = { if (active) onPanelExpandedChange(it) },
                    )
                }
            }
        } else {
            SideEffect { if (active) onPanelExpandedChange(false) }
        }
    TsuyomiPullToRefresh(
        isRefreshing = pageState.replacing,
        onRefresh = onRefresh,
        enabled = chrome == null && active && !pageState.replacing,
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = if (wide) GridCells.Adaptive(150.dp) else GridCells.Fixed(3),
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
                        SourceHomeBookCard(book, active, coverState, onCoverVisibility, onOpenBook)
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
                                text = stringResource(R.string.source_home_append_failure),
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
    }
}

@Composable
private fun SourceHomePageControls(
    pageState: SourceHomePageViewState,
    onSelectFilters: (Map<String, String>) -> Unit,
    onRetryReplacement: () -> Unit,
    onOpenVerification: () -> Unit,
    active: Boolean,
    onPanelExpandedChange: (Boolean) -> Unit = {},
    page: SourceHomePage? = null,
) {
    val activePage = page ?: requireNotNull(pageState.page ?: pageState.cursorPage)
    val primaryId = activePage.filters.firstOrNull()?.id
    val secondaryFilters = activePage.filters.filterNot { it.id == primaryId }
    val leadingFilter = secondaryFilters.firstOrNull()
    val compactFilters = secondaryFilters.drop(1)
    var expandedFilterId by rememberSaveable(pageState.queryKey) { mutableStateOf<String?>(null) }
    SideEffect { onPanelExpandedChange(expandedFilterId != null) }

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
            .padding(horizontal = TsuyomiSpacing.Md)
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
                    revealSelectionKey = active,
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

        TsuyomiAnimatedContent(
            targetState = expandedFilterId,
            modifier = Modifier.fillMaxWidth(),
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
            pageState.replacementFailure != null -> {
                val verificationRequired = pageState.replacementFailure.code in setOf(
                    SourceErrorCode.SESSION_REQUIRED,
                    SourceErrorCode.VERIFICATION_REQUIRED,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (verificationRequired) R.string.source_home_inline_verification
                            else R.string.source_home_inline_failure,
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TsuyomiButton(
                        text = stringResource(
                            if (verificationRequired) R.string.source_home_open_verification
                            else R.string.source_home_retry,
                        ),
                        onClick = if (verificationRequired) onOpenVerification else onRetryReplacement,
                        style = TsuyomiButtonStyle.TEXT,
                    )
                }
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
