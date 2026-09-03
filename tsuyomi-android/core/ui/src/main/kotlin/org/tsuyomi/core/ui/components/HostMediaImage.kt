/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

/** Pure host-media renderer. Transport and visibility ownership remain outside Compose UI. */
@Composable
fun HostMediaImage(
    state: CoverUiState?,
    altText: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = when (state) {
        is CoverUiState.Ready -> state.bitmap
        is CoverUiState.StaleReady -> state.bitmap
        else -> null
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = altText,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            state is CoverUiState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(TsuyomiSpacing.Md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = altText ?: stringResource(R.string.coreui_image),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TsuyomiButton(
                    text = stringResource(R.string.coreui_retry_image),
                    onClick = onRetry,
                    modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                    style = TsuyomiButtonStyle.SECONDARY,
                )
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
