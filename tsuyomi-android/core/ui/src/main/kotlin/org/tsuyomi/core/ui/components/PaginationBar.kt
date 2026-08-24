/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

/**
 * Explicit pagination control: previous/next actions and a persistent page status. Buttons are
 * disabled at the first/last page; loading and error are persistent inline states, never
 * transient. The status is a polite live region reading "第 x 页，共 y 页".
 *
 * Phase 1 validates this component through previews and test hosts only; no fake long list is
 * shipped in the app.
 */
@Composable
fun PaginationBar(
    page: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { collectionInfo = CollectionInfo(1, 3) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TsuyomiButton(
                text = stringResource(R.string.coreui_pagination_previous),
                onClick = onPrevious,
                style = TsuyomiButtonStyle.SECONDARY,
                enabled = page > 1 && !loading,
                modifier = Modifier.semantics {
                    collectionItemInfo = CollectionItemInfo(0, 1, 0, 1)
                },
            )
            Text(
                text = stringResource(R.string.coreui_pagination_page_status, page, pageCount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    collectionItemInfo = CollectionItemInfo(0, 1, 1, 1)
                },
            )
            TsuyomiButton(
                text = stringResource(R.string.coreui_pagination_next),
                onClick = onNext,
                style = TsuyomiButtonStyle.SECONDARY,
                enabled = page < pageCount && !loading,
                modifier = Modifier.semantics {
                    collectionItemInfo = CollectionItemInfo(0, 1, 2, 1)
                },
            )
        }
        if (loading) {
            InlineStatus(
                text = stringResource(R.string.coreui_loading),
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
            )
        }
        if (error != null) {
            InfoBanner(
                title = error,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                primaryActionLabel = if (onRetry != null) {
                    stringResource(R.string.coreui_retry)
                } else {
                    null
                },
                onPrimaryAction = onRetry,
            )
        }
    }
}
