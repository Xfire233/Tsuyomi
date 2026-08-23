/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.prototype.uiatlas.model.AtlasCover
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

/**
 * Muted cover palette for procedurally generated covers (Atlas Spec F10). Original values;
 * a cover's color and motif are a pure function of its seed, so captures never change between
 * runs. E-ink renders the same geometry on the grayscale ramp instead of decoding color.
 */
private val coverPalette = listOf(
    Color(0xFF7A8B7A),
    Color(0xFF8B7A6B),
    Color(0xFF6B7A8B),
    Color(0xFF8B6B7A),
    Color(0xFF7A8B8B),
    Color(0xFF94895F),
    Color(0xFF6E7E5E),
    Color(0xFF7E6E8E),
)

private fun coverColor(seed: Long): Color =
    coverPalette[(seed % coverPalette.size).toInt().let { if (it < 0) it + coverPalette.size else it }]

private fun coverMotif(seed: Long): Int = ((seed ushr 8) % 3).toInt()

private fun luminanceOf(color: Color): Float =
    0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

/** Maps a color onto the E-ink grayscale ramp by luminance. */
private fun grayscaleOf(color: Color): Color {
    val lum = luminanceOf(color)
    return Color(lum, lum, lum, 1f)
}

/**
 * Pure cover renderer (constitution §2.3 analog): it receives an already-resolved [AtlasCover]
 * plus the title needed by the deterministic fallback chain, and draws everything with Compose
 * Canvas — no bitmaps, painters, URLs, or I/O. Absent/failed covers render the host fallback
 * (validated source-color or neutral base + host-rendered title); stale covers add a caption.
 */
@Composable
fun AtlasCoverImage(
    cover: AtlasCover,
    title: String,
    modifier: Modifier = Modifier,
    sourceColor: Color? = null,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val shape = MaterialTheme.shapes.small
    Surface(modifier = modifier, shape = shape) {
        when (cover) {
            is AtlasCover.Generated -> GeneratedCover(cover.seed, stale = false, eInk = eInk)
            is AtlasCover.Stale -> GeneratedCover(cover.seed, stale = true, eInk = eInk)
            AtlasCover.Absent, AtlasCover.Failed -> FallbackCover(
                title = title,
                sourceColor = sourceColor,
                eInk = eInk,
            )
        }
    }
}

@Composable
private fun GeneratedCover(seed: Long, stale: Boolean, eInk: Boolean) {
    val base = coverColor(seed)
    val background = if (eInk) grayscaleOf(base) else base
    val ink = if (eInk) {
        if (luminanceOf(background) > 0.5f) AtlasEInkPalette.N90 else AtlasEInkPalette.Paper
    } else {
        Color(0xFFFDFCF9)
    }
    val motif = coverMotif(seed)
    Box {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(background)
            when (motif) {
                // Horizon band.
                0 -> drawRect(
                    color = ink,
                    topLeft = Offset(0f, size.height * 0.62f),
                    size = Size(size.width, size.height * 0.06f),
                )
                // Offset disc.
                1 -> drawCircle(
                    color = ink,
                    radius = size.minDimension * 0.18f,
                    center = Offset(size.width * 0.68f, size.height * 0.3f),
                )
                // Rising diagonal.
                else -> {
                    val stripe = size.width * 0.08f
                    drawRect(
                        color = ink,
                        topLeft = Offset(size.width * 0.2f, size.height * 0.55f),
                        size = Size(stripe, size.height * 0.45f),
                    )
                    drawRect(
                        color = ink,
                        topLeft = Offset(size.width * 0.38f, size.height * 0.38f),
                        size = Size(stripe, size.height * 0.62f),
                    )
                }
            }
        }
        if (stale) {
            if (eInk) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(AtlasSpacing.Xs)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = AtlasEInkPalette.Paper,
                    contentColor = AtlasEInkPalette.Ink,
                    border = BorderStroke(1.5.dp, AtlasEInkPalette.N90),
                ) {
                    Text(
                        text = "旧封面",
                        modifier = Modifier.padding(
                            horizontal = AtlasSpacing.Xs,
                            vertical = AtlasSpacing.Xs,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = "旧封面",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(AtlasSpacing.Xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFDFCF9),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FallbackCover(title: String, sourceColor: Color?, eInk: Boolean) {
    val base = when {
        eInk -> AtlasEInkPalette.Paper
        sourceColor != null -> sourceColor.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surfaceVariant)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (eInk) AtlasEInkPalette.Ink else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(base)
            if (eInk) {
                drawRect(
                    color = AtlasEInkPalette.N90,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.padding(AtlasSpacing.Sm),
            style = MaterialTheme.typography.titleSmall,
            color = foreground,
            textAlign = TextAlign.Center,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
