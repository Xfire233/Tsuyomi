/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.coverCardTitle

/** Cover-led Material card with external metadata or a bounded in-cover title treatment. */
@Composable
fun TsuyomiCoverGridCard(
    title: String,
    supportingText: String?,
    onClick: () -> Unit,
    cover: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    titleInsideCover: Boolean = false,
) {
    val layout = currentCoverCardLayout()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (titleInsideCover || layout.usesWideTitleLane) {
            TsuyomiCoverCardContent(title, supportingText, cover)
        } else {
            val titleStyle = MaterialTheme.typography.titleSmall
            val supportingStyle = MaterialTheme.typography.labelSmall
            val titleLineHeight = titleStyle.lineHeightOrFontSize()
            val supportingLineHeight = supportingStyle.lineHeightOrFontSize()
            val metadataMinimumHeight = with(LocalDensity.current) {
                TsuyomiSpacing.Xs * 2 + titleLineHeight.toDp() * 2 + supportingLineHeight.toDp()
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(layout.artworkAspectRatio),
                content = cover,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = metadataMinimumHeight)
                    .padding(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Xs),
            ) {
                Text(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = titleStyle.copy(lineBreak = LineBreak.Heading),
                )
                if (supportingText.isNullOrBlank()) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        text = supportingText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = supportingStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Effective card height shared by resting cards and positioned drag previews. */
@Composable
fun coverCardHeight(width: Dp): Dp {
    val layout = currentCoverCardLayout()
    val naturalHeight = width / layout.gridAspectRatio
    if (!layout.usesWideTitleLane) return naturalHeight
    val lineHeight = MaterialTheme.typography.coverCardTitle.lineHeightOrFontSize()
    return with(LocalDensity.current) {
        maxOf(naturalHeight, TsuyomiSpacing.Sm * 2 + lineHeight.toDp() * 2)
    }
}

/**
 * Pure cover/title content shared by book cards, structural cards, and their grid drag previews.
 * The wide treatment dedicates a side lane to text and uses [CoverImage]'s complete-artwork fit.
 */
@Composable
fun TsuyomiCoverCardContent(
    title: String,
    supportingText: String?,
    cover: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    metadataModifier: Modifier = Modifier,
    artworkOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val layout = currentCoverCardLayout()
    if (!layout.usesWideTitleLane) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(layout.gridAspectRatio)
                .clipToBounds(),
        ) {
            cover()
            artworkOverlay()
            TsuyomiCoverTitleOverlay(title, supportingText, modifier = metadataModifier)
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val titleStyle = MaterialTheme.typography.coverCardTitle
        val supportingStyle = MaterialTheme.typography.labelMedium
        val titleLineHeight = titleStyle.lineHeightOrFontSize()
        val supportingLineHeight = supportingStyle.lineHeightOrFontSize()
        val cardHeight = coverCardHeight(maxWidth)
        val titleMaxLines = with(LocalDensity.current) {
            val supportingHeight = if (supportingText.isNullOrBlank()) 0.dp else supportingLineHeight.toDp()
            val availableHeight = cardHeight - TsuyomiSpacing.Sm * 2 - supportingHeight
            (availableHeight / titleLineHeight.toDp()).toInt().coerceIn(1, 2)
        }
        // At large font scales cap the artwork lane before it can starve the readable text lane.
        val artworkWidth = minOf(cardHeight * layout.artworkAspectRatio, maxWidth * 0.42f)

        Box(Modifier.fillMaxWidth().height(cardHeight)) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(artworkWidth)
                        .clipToBounds(),
                ) {
                    cover()
                }
                CoverCardTitleLane(
                    title = title,
                    supportingText = supportingText,
                    titleMaxLines = titleMaxLines,
                    modifier = metadataModifier.fillMaxHeight().weight(1f),
                )
            }
            // Artwork fitting must not clip the overlay's accessible interaction lane.
            Box(Modifier.width(maxOf(artworkWidth, 48.dp)).fillMaxHeight()) {
                artworkOverlay()
            }
        }
    }
}
@Composable
private fun CoverCardTitleLane(
    title: String,
    supportingText: String?,
    titleMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(TsuyomiSpacing.Sm),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.coverCardTitle.copy(lineBreak = LineBreak.Heading),
        )
        supportingText?.takeIf(String::isNotBlank)?.let { text ->
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shared in-cover metadata treatment; callers own artwork, state, and gestures. */
@Composable
fun BoxScope.TsuyomiCoverTitleOverlay(
    title: String,
    supportingText: String?,
    modifier: Modifier = Modifier,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val expandedFontScale = LocalDensity.current.fontScale > 1.5f
    val topPadding = if (!eInk && expandedFontScale) TsuyomiSpacing.Xs else 28.dp
    val titleStyle = if (eInk) {
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.coverCardTitle
    }
    Column(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .drawWithCache {
                val fadeEnd = (topPadding.toPx() / size.height).coerceIn(0f, 1f)
                val gradient = Brush.verticalGradient(
                    0f to Color.Transparent,
                    fadeEnd to Color.Black.copy(alpha = 0.65f),
                    1f to Color.Black.copy(alpha = 0.82f),
                )
                onDrawBehind { drawRect(gradient) }
            }
            .padding(
                start = TsuyomiSpacing.Sm,
                top = topPadding,
                end = TsuyomiSpacing.Sm,
                bottom = 6.dp,
            ),
    ) {
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = titleStyle.copy(lineBreak = LineBreak.Heading),
            color = Color.White,
        )
        supportingText?.takeIf(String::isNotBlank)?.let { text ->
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

private fun TextStyle.lineHeightOrFontSize(): TextUnit =
    lineHeight.takeIf { it != TextUnit.Unspecified } ?: fontSize