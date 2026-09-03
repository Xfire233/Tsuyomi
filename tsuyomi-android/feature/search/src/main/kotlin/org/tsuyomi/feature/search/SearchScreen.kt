/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiIconButton
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiTopBarAction
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceErrorCode

sealed interface SearchResultState {
    data object Idle : SearchResultState
    data object Loading : SearchResultState
    data class Results(val items: List<SourceBookSummary>) : SearchResultState
    data class Failure(val code: SourceErrorCode, val diagnostic: SourceDiagnostic) : SearchResultState
}

enum class SearchLayout {
    LIST,
    COMPACT,
    GRID;

    fun next(): SearchLayout = entries[(ordinal + 1) % entries.size]
}

@Composable
fun SearchTopBar(
    layout: SearchLayout,
    onCycleLayout: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val icon = when (layout) {
        SearchLayout.LIST -> TsuyomiIcons.List
        SearchLayout.COMPACT -> TsuyomiIcons.Compact
        SearchLayout.GRID -> TsuyomiIcons.Grid
    }
    val description = stringResource(
        when (layout) {
            SearchLayout.LIST -> R.string.search_layout_list
            SearchLayout.COMPACT -> R.string.search_layout_compact
            SearchLayout.GRID -> R.string.search_layout_grid
        },
    )
    TsuyomiTopBar(
        title = stringResource(R.string.search_topbar_title),
        onNavigateUp = onNavigateUp,
        actions = listOf(TsuyomiTopBarAction(icon, description, onCycleLayout)),
    )
}

@Composable
fun SearchScreen(
    query: String,
    state: SearchResultState,
    layout: SearchLayout,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectBook: (SourceBookSummary) -> Unit,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier = Modifier,
    coverState: @Composable (SourceBookSummary) -> CoverUiState = { book ->
        CoverUiState.Fallback(FallbackSpec(book.title, book.identity.sourceId))
    },
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        FrozenEInkSearchScreen(
            query = query,
            state = state,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onSelectBook = onSelectBook,
            onRetry = onRetry,
            onUseOfflineCache = onUseOfflineCache,
            onOpenVerification = onOpenVerification,
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = TsuyomiSpacing.Md,
                vertical = TsuyomiSpacing.Sm,
            ),
            label = { Text(stringResource(R.string.search_query_label)) },
            trailingIcon = {
                TsuyomiIconButton(
                    imageVector = TsuyomiIcons.Search,
                    contentDescription = stringResource(R.string.search_submit_description),
                    onClick = onSearch,
                    enabled = query.isNotBlank(),
                )
            },
            supportingText = { Text(stringResource(R.string.search_query_count, query.length)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }),
        )
        when (state) {
            SearchResultState.Idle -> StateView(
                kind = TsuyomiStateKind.EMPTY,
                title = stringResource(R.string.search_idle_title),
                modifier = Modifier.weight(1f),
            )
            SearchResultState.Loading -> StateView(
                kind = TsuyomiStateKind.LOADING,
                title = stringResource(R.string.search_loading_title),
                modifier = Modifier.weight(1f),
            )
            is SearchResultState.Results -> if (state.items.isEmpty()) {
                StateView(
                    kind = TsuyomiStateKind.EMPTY,
                    title = stringResource(R.string.search_empty_title, query.trim()),
                    message = stringResource(R.string.search_empty_message),
                    modifier = Modifier.weight(1f),
                )
            } else {
                SearchResults(
                    books = state.items,
                    layout = layout,
                    onSelectBook = onSelectBook,
                    coverState = coverState,
                    modifier = Modifier.weight(1f),
                )
            }
            is SearchResultState.Failure -> SearchFailure(
                state = state,
                onRetry = onRetry,
                onUseOfflineCache = onUseOfflineCache,
                onOpenVerification = onOpenVerification,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SearchResults(
    books: List<SourceBookSummary>,
    layout: SearchLayout,
    onSelectBook: (SourceBookSummary) -> Unit,
    coverState: @Composable (SourceBookSummary) -> CoverUiState,
    modifier: Modifier,
) {
    when (layout) {
        SearchLayout.LIST -> LazyColumn(modifier) {
            listItems(books, key = ::bookKey) { book ->
                SearchListRow(book, onSelectBook, coverState(book))
                HorizontalDivider()
            }
        }
        SearchLayout.COMPACT -> LazyColumn(modifier) {
            listItems(books, key = ::bookKey) { book ->
                SearchCompactRow(book, onSelectBook)
                HorizontalDivider()
            }
        }
        SearchLayout.GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier,
            contentPadding = PaddingValues(TsuyomiSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Md),
        ) {
            gridItems(books, key = ::bookKey) { book ->
                SearchGridCard(book, onSelectBook, coverState(book))
            }
        }
    }
}

private fun bookKey(book: SourceBookSummary): String =
    "${book.identity.sourceId}:${book.identity.remoteBookId}"

@Composable
private fun SearchListRow(
    book: SourceBookSummary,
    onSelectBook: (SourceBookSummary) -> Unit,
    coverState: CoverUiState,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectBook(book) }
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            state = coverState,
            modifier = Modifier.width(72.dp).height(96.dp),
        )
        Column(Modifier.weight(1f).padding(start = TsuyomiSpacing.Md)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            book.author?.let {
                Text(
                    stringResource(R.string.search_author, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.search_source_label, book.identity.sourceId),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchCompactRow(book: SourceBookSummary, onSelectBook: (SourceBookSummary) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectBook(book) }
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(book.author, book.identity.sourceId).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchGridCard(
    book: SourceBookSummary,
    onSelectBook: (SourceBookSummary) -> Unit,
    coverState: CoverUiState,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onSelectBook(book) },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column {
            CoverImage(coverState, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            Column(Modifier.padding(TsuyomiSpacing.Sm)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(
                    book.author ?: book.identity.sourceId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SearchFailure(
    state: SearchResultState.Failure,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canVerify = state.code == SourceErrorCode.SESSION_REQUIRED || state.code == SourceErrorCode.VERIFICATION_REQUIRED
    Column(
        modifier = modifier.fillMaxWidth().padding(TsuyomiSpacing.Lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.search_failure_title))
        Text(text = stringResource(errorMessage(state.code)), modifier = Modifier.padding(top = TsuyomiSpacing.Sm))
        Text(
            text = stringResource(R.string.search_diagnostic_id, state.diagnostic.correlationId),
            modifier = Modifier.padding(top = TsuyomiSpacing.Sm, bottom = TsuyomiSpacing.Md),
        )
        Text(stringResource(R.string.search_diagnostic_stage, state.diagnostic.stage, state.diagnostic.safeCode))
        if (canVerify) {
            TsuyomiButton(
                text = stringResource(R.string.search_open_verification),
                onClick = onOpenVerification,
                style = TsuyomiButtonStyle.PRIMARY,
            )
        } else {
            TsuyomiButton(
                text = stringResource(R.string.search_retry_action),
                onClick = onRetry,
                style = TsuyomiButtonStyle.PRIMARY,
            )
            TsuyomiButton(
                text = stringResource(R.string.search_offline_action),
                onClick = onUseOfflineCache,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                style = TsuyomiButtonStyle.SECONDARY,
            )
        }
    }
}

@Composable
private fun FrozenEInkSearchScreen(
    query: String,
    state: SearchResultState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectBook: (SourceBookSummary) -> Unit,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.search_eink_query_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f),
            )
            TsuyomiButton(
                text = stringResource(R.string.search_action),
                onClick = onSearch,
                enabled = query.isNotBlank(),
            )
        }
        when (state) {
            SearchResultState.Idle -> StateView(
                kind = TsuyomiStateKind.EMPTY,
                title = stringResource(R.string.search_eink_idle_title),
                message = stringResource(R.string.search_eink_idle_message),
            )
            SearchResultState.Loading -> StateView(
                kind = TsuyomiStateKind.LOADING,
                title = stringResource(R.string.search_loading_title),
            )
            is SearchResultState.Results -> if (state.items.isEmpty()) {
                StateView(
                    kind = TsuyomiStateKind.EMPTY,
                    title = stringResource(R.string.search_empty_title, query.trim()),
                    message = stringResource(R.string.search_empty_message),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    listItems(state.items, key = ::bookKey) { book ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBook(book) }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        ) {
                            Text(book.title)
                            book.author?.let { Text(stringResource(R.string.search_author, it)) }
                        }
                        HorizontalDivider()
                    }
                }
            }
            is SearchResultState.Failure -> SearchFailure(
                state = state,
                onRetry = onRetry,
                onUseOfflineCache = onUseOfflineCache,
                onOpenVerification = onOpenVerification,
            )
        }
    }
}

private fun errorMessage(code: SourceErrorCode): Int = when (code) {
    SourceErrorCode.NETWORK_TIMEOUT -> R.string.search_error_timeout
    SourceErrorCode.NETWORK_OFFLINE -> R.string.search_error_offline
    SourceErrorCode.SESSION_REQUIRED -> R.string.search_error_session
    SourceErrorCode.VERIFICATION_REQUIRED -> R.string.search_error_verification
    SourceErrorCode.EMPTY_SOURCE_RESPONSE -> R.string.search_error_empty_response
    SourceErrorCode.MALFORMED_SOURCE_RESPONSE -> R.string.search_error_malformed
    else -> R.string.search_error_generic
}
