/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec

private val fallbackPalette = listOf(
    Color(0xFF7A8B7A), Color(0xFF8B7A6B), Color(0xFF6B7A8B), Color(0xFF8B6B7A),
    Color(0xFF7A8B8B), Color(0xFF94895F), Color(0xFF6E7E5E), Color(0xFF7E6E8E),
)

/** Pure renderer: no repository reference, request construction, transport, or cache ownership. */
@Composable
fun CoverImage(
    state: CoverUiState,
    modifier: Modifier = Modifier,
    unresolvedBadge: Boolean = false,
) {
    val bitmap = when (state) {
        is CoverUiState.Ready -> state.bitmap
        is CoverUiState.StaleReady -> state.bitmap
        else -> null
    }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                FallbackCover(state.fallbackSpec(), Modifier.fillMaxSize())
            }
            if (unresolvedBadge) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .testTag("cover-unresolved-badge"),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                ) {
                    Text(
                        text = "UNRESOLVED",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackCover(spec: FallbackSpec, modifier: Modifier) {
    val seed = "${spec.title}\u0000${spec.sourceLabel.orEmpty()}".hashCode().toLong()
    val colorIndex = (seed % fallbackPalette.size).toInt().let { if (it < 0) it + fallbackPalette.size else it }
    val background = fallbackPalette[colorIndex]
    val motif = ((seed ushr 8) % 3).toInt()
    Canvas(modifier) {
        drawRect(background)
        val ink = Color(0xFFFDFCF9)
        when (motif) {
            0 -> drawRect(ink, Offset(0f, size.height * 0.62f), Size(size.width, size.height * 0.06f))
            1 -> drawCircle(ink, size.minDimension * 0.18f, Offset(size.width * 0.68f, size.height * 0.3f))
            else -> {
                val stripe = size.width * 0.08f
                drawRect(ink, Offset(size.width * 0.2f, size.height * 0.55f), Size(stripe, size.height * 0.45f))
                drawRect(ink, Offset(size.width * 0.38f, size.height * 0.38f), Size(stripe, size.height * 0.62f))
            }
        }
    }
}

private fun CoverUiState.fallbackSpec(): FallbackSpec = when (this) {
    is CoverUiState.Absent -> fallback
    is CoverUiState.Loading -> fallback
    is CoverUiState.Failed -> fallback
    is CoverUiState.Fallback -> spec
    is CoverUiState.Ready, is CoverUiState.StaleReady -> FallbackSpec("", null)
}
