/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceErrorCode

sealed interface SearchResultState {
    data object Idle : SearchResultState
    data object Loading : SearchResultState
    data class Results(val items: List<SourceBookSummary>) : SearchResultState
    data class Failure(val code: SourceErrorCode, val diagnostic: SourceDiagnostic) : SearchResultState
}

@Composable
fun SearchScreen(
    query: String,
    state: SearchResultState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectBook: (SourceBookSummary) -> Unit,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier = Modifier,
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
                label = { Text(stringResource(R.string.search_query_label)) },
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
                title = stringResource(R.string.search_idle_title),
                message = stringResource(R.string.search_idle_message),
            )
            SearchResultState.Loading -> StateView(
                kind = TsuyomiStateKind.LOADING,
                title = stringResource(R.string.search_loading_title),
            )
            is SearchResultState.Results -> if (state.items.isEmpty()) {
                StateView(
                    kind = TsuyomiStateKind.EMPTY,
                    title = stringResource(R.string.search_empty_title),
                    message = stringResource(R.string.search_empty_message),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { "${it.identity.sourceId}:${it.identity.remoteBookId}" }) { book ->
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

@Composable
private fun SearchFailure(
    state: SearchResultState.Failure,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
) {
    val canVerify = state.code == SourceErrorCode.SESSION_REQUIRED || state.code == SourceErrorCode.VERIFICATION_REQUIRED
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.search_failure_title))
        Text(
            text = stringResource(errorMessage(state.code)),
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.search_diagnostic_id, state.diagnostic.correlationId),
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
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
                modifier = Modifier.padding(top = 8.dp),
                style = TsuyomiButtonStyle.SECONDARY,
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
