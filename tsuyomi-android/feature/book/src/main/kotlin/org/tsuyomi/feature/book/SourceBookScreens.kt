/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceErrorCode

sealed interface SourceBookState<out T> {
    data object Loading : SourceBookState<Nothing>
    data class Content<T>(val value: T) : SourceBookState<T>
    data class Failure(val code: SourceErrorCode, val diagnostic: SourceDiagnostic) : SourceBookState<Nothing>
}

@Composable
fun BookDetailScreen(
    state: SourceBookState<SourceBookDetail>,
    modifier: Modifier = Modifier,
    inLibrary: Boolean = false,
    addWritesRemote: Boolean = false,
    reconciliationLabel: String? = null,
    onAddToLibrary: () -> Unit = {},
    onRemoveFromLibrary: () -> Unit = {},
    onOpenDirectory: () -> Unit,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
) {
    when (state) {
        SourceBookState.Loading -> StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.book_loading_detail),
            modifier = modifier,
        )
        is SourceBookState.Failure -> BookFailure(state, onRetry, onUseOfflineCache, onOpenVerification, modifier)
        is SourceBookState.Content -> {
            val detail = state.value
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(detail.summary.title)
                detail.summary.author?.let { Text(stringResource(R.string.book_author, it)) }
                detail.status?.let { Text(stringResource(R.string.book_status, it)) }
                detail.description?.let { Text(it) }
                if (detail.tags.isNotEmpty()) Text(detail.tags.joinToString(" · "))
                reconciliationLabel?.let { Text(it) }
                TsuyomiButton(
                    text = stringResource(
                        when {
                            inLibrary -> R.string.book_remove_from_library
                            addWritesRemote -> R.string.book_add_and_sync
                            else -> R.string.book_add_to_library
                        },
                    ),
                    onClick = if (inLibrary) onRemoveFromLibrary else onAddToLibrary,
                    modifier = Modifier.fillMaxWidth(),
                    style = if (inLibrary) TsuyomiButtonStyle.SECONDARY else TsuyomiButtonStyle.PRIMARY,
                )
                TsuyomiButton(
                    text = stringResource(R.string.book_open_directory),
                    onClick = onOpenDirectory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun BookDirectoryScreen(
    state: SourceBookState<SourceDirectory>,
    onSelectChapter: (SourceChapter) -> Unit,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SourceBookState.Loading -> StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.book_loading_directory),
            modifier = modifier,
        )
        is SourceBookState.Failure -> BookFailure(state, onRetry, onUseOfflineCache, onOpenVerification, modifier)
        is SourceBookState.Content -> LazyColumn(modifier.fillMaxSize()) {
            items(state.value.chapters, key = SourceChapter::chapterId) { chapter ->
                Text(
                    text = chapter.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectChapter(chapter) }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BookFailure(
    state: SourceBookState.Failure,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.book_source_failure))
        Text(stringResource(R.string.book_error_code, state.code.name))
        Text(stringResource(R.string.book_diagnostic_id, state.diagnostic.correlationId))
        Text(stringResource(R.string.book_diagnostic_stage, state.diagnostic.stage, state.diagnostic.safeCode))
        TsuyomiButton(
            text = stringResource(R.string.book_retry),
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
            style = TsuyomiButtonStyle.PRIMARY,
        )
        TsuyomiButton(
            text = stringResource(R.string.book_offline),
            onClick = onUseOfflineCache,
            modifier = Modifier.padding(top = 8.dp),
            style = TsuyomiButtonStyle.SECONDARY,
        )
        if (state.code == SourceErrorCode.SESSION_REQUIRED || state.code == SourceErrorCode.VERIFICATION_REQUIRED) {
            TsuyomiButton(
                text = stringResource(R.string.book_open_verification),
                onClick = onOpenVerification,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
