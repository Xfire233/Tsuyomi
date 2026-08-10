/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.browse

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
import androidx.compose.material3.Switch
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
import org.tsuyomi.shared.sourcecontract.SourceBookSummary


@Composable
fun RemoteLibraryScreen(
    books: List<SourceBookSummary>,
    loading: Boolean,
    message: String?,
    writebackAvailable: Boolean,
    writebackEnabled: Boolean,
    onPull: () -> Unit,
    onWritebackChanged: (Boolean) -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (loading) {
        StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.remote_library_loading),
            message = stringResource(R.string.remote_library_loading_message),
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.remote_library_manual_notice))
            if (writebackAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(stringResource(R.string.remote_library_writeback_title))
                        Text(stringResource(R.string.remote_library_writeback_message))
                    }
                    Switch(checked = writebackEnabled, onCheckedChange = onWritebackChanged)
                }
            }
            TsuyomiButton(
                text = stringResource(R.string.remote_library_pull_action),
                onClick = onPull,
                modifier = Modifier.fillMaxWidth(),
                style = TsuyomiButtonStyle.PRIMARY,
            )
            message?.let { Text(it) }
        }
        HorizontalDivider()
        if (books.isEmpty()) {
            StateView(
                kind = TsuyomiStateKind.EMPTY,
                title = stringResource(R.string.remote_library_empty_title),
                message = stringResource(R.string.remote_library_empty_message),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(books, key = SourceBookSummary::canonicalUrl) { book ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenBook(book) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(book.title)
                        book.author?.let { Text(it) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
