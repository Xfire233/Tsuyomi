/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.prototype.uiatlas.model.AtlasBranding
import org.tsuyomi.prototype.uiatlas.model.AtlasSource
import org.tsuyomi.prototype.uiatlas.model.AtlasSourceMark
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

/** Source identity is compact context, never a page-sized branding treatment. */
enum class AtlasIdentityOption(val label: String) {
    ICON_ONLY("仅标记"),
    COMPACT("标记与名称"),
}

/** Tone-maps a validated source color into the low-saturation identity role on [surface]. */
private fun toneMap(source: Color, surface: Color, inkAmount: Float): Color = Color(
    red = surface.red + (source.red - surface.red) * inkAmount,
    green = surface.green + (source.green - surface.green) * inkAmount,
    blue = surface.blue + (source.blue - surface.blue) * inkAmount,
    alpha = 1f,
)

/**
 * Original geometric source marks drawn with Compose Canvas only — no SVG, no assets, nothing
 * copied or traced (Atlas Spec F9). The mark is decorative; callers exclude it from the
 * accessibility tree and carry identity in visible text.
 */
@Composable
fun AtlasSourceMarkCanvas(
    mark: AtlasSourceMark,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        when (mark) {
            AtlasSourceMark.PINE -> {
                // Three stacked chevrons of decreasing width.
                val layers = 3
                for (i in 0 until layers) {
                    val half = w * (0.40f - 0.09f * i)
                    val apex = h * (0.12f + 0.24f * i)
                    val base = apex + h * 0.22f
                    val path = Path().apply {
                        moveTo(w / 2f, apex)
                        lineTo(w / 2f - half, base)
                        lineTo(w / 2f + half, base)
                        close()
                    }
                    drawPath(path, tint)
                }
            }
            AtlasSourceMark.CYPRESS -> {
                // Three upright bars of graded height.
                val barWidth = w * 0.16f
                val gap = w * 0.10f
                val total = barWidth * 3 + gap * 2
                val start = (w - total) / 2f
                val heights = listOf(0.62f, 0.88f, 0.72f)
                heights.forEachIndexed { i, fraction ->
                    val barHeight = h * fraction
                    drawRect(
                        color = tint,
                        topLeft = Offset(start + i * (barWidth + gap), h - barHeight - h * 0.06f),
                        size = Size(barWidth, barHeight),
                    )
                }
            }
            AtlasSourceMark.BAMBOO -> {
                // Two segmented stalks with node gaps.
                val stalkWidth = w * 0.14f
                val segments = 3
                val gapY = h * 0.05f
                val segHeight = (h * 0.9f - gapY * (segments - 1)) / segments
                listOf(0.26f, 0.60f).forEachIndexed { stalkIndex, xFraction ->
                    val heightScale = if (stalkIndex == 0) 1f else 0.82f
                    for (s in 0 until segments) {
                        val top = h * 0.05f + s * (segHeight + gapY) * heightScale
                        drawRect(
                            color = tint,
                            topLeft = Offset(w * xFraction, top),
                            size = Size(stalkWidth, segHeight * heightScale),
                        )
                    }
                }
            }
            AtlasSourceMark.GENERIC -> {
                // Diamond inside a ring: the generic host fallback mark.
                drawCircle(color = tint, radius = w * 0.42f, style = Stroke(width = w * 0.07f))
                val path = Path().apply {
                    moveTo(w / 2f, h * 0.24f)
                    lineTo(w * 0.76f, h / 2f)
                    lineTo(w / 2f, h * 0.76f)
                    lineTo(w * 0.24f, h / 2f)
                    close()
                }
                drawPath(path, tint)
            }
        }
    }
}

/** Compact mark used where the page title already establishes the source-owned context. */
@Composable
fun AtlasSourceIcon(
    source: AtlasSource,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val scheme = MaterialTheme.colorScheme
    val valid = source.branding as? AtlasBranding.Valid
    val mark = if (valid != null) source.mark else AtlasSourceMark.GENERIC
    val tint = if (eInk || valid == null) scheme.onSurfaceVariant else toneMap(valid.color, scheme.onSurface, 0.72f)
    AtlasSourceMarkCanvas(
        mark = mark,
        tint = tint,
        modifier = modifier
            .size(24.dp)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    )
}

/** Compact source label for mixed results or a source-specific secondary section. */
@Composable
fun AtlasSourceChip(
    source: AtlasSource,
    modifier: Modifier = Modifier,
    monochrome: Boolean = false,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val scheme = MaterialTheme.colorScheme
    val containerColor = (source.branding as? AtlasBranding.Valid)
        ?.takeUnless { eInk || monochrome }
        ?.let { toneMap(it.color, scheme.surface, 0.24f) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AtlasSourceIcon(source, contentDescription = "来源 ${source.name}", modifier = Modifier.size(18.dp))
        AtlasChip(
            text = source.name,
            modifier = Modifier.padding(start = AtlasSpacing.Xs),
            container = containerColor,
            content = if (containerColor != null) scheme.onSurface else null,
        )
    }
}

/**
 * Compact source context row. The old full-width colored band was rejected because the route,
 * app-bar title or enclosing section already tells the user where they are. [ICON_ONLY] is used
 * when that visible text already names the source; [COMPACT] adds the name only when needed.
 */
@Composable
fun SourceIdentityBand(
    source: AtlasSource,
    modifier: Modifier = Modifier,
    option: AtlasIdentityOption = AtlasIdentityOption.COMPACT,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option == AtlasIdentityOption.ICON_ONLY) {
            AtlasSourceIcon(source, contentDescription = "来源 ${source.name}")
        } else {
            AtlasSourceChip(source = source)
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                modifier = Modifier.padding(start = AtlasSpacing.Sm).weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
