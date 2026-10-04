/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.activeAccent
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.theme.tsuyomiAnimateFloatAsState
import org.tsuyomi.core.ui.theme.tsuyomiFocusRing
import kotlin.math.roundToInt

/** One option of a [SegmentedSelector]. */
data class TsuyomiSegment<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true,
)

/**
 * Single-choice segmented selector with radio semantics: the container exposes collection info,
 * every segment exposes its role, selected flag, position, and a state description, and disabled
 * or error states are announced as text, never color alone.
 *
 * Standard selection moves one raised neutral surface across the track; E-ink retains its
 * immediate opaque inversion and outlined segments.
 */
@Composable
fun <T> SegmentedSelector(
    options: List<TsuyomiSegment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    errorMessage: String? = null,
) {
    val environment = LocalDisplayEnvironment.current
    val eInk = environment.effectiveProfile == DisplayProfile.EINK
    val shape: Shape = RoundedCornerShape(if (eInk) 4.dp else 12.dp)
    val borderColor = if (enabled) TsuyomiEInkPalette.Ink else TsuyomiEInkPalette.N50
    val selectedIndex = options.indexOfFirst { it.value == selected }
    val indicatorPosition = tsuyomiAnimateFloatAsState(
        target = selectedIndex.toFloat(),
        instant = environment.instantMotion,
        label = "segmentIndicator",
    )
    val selectedSurface = if (environment.effectiveDarkTheme) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surface
    }
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge.let { style ->
        if (eInk) style else style.copy(lineBreak = LineBreak.Heading)
    }
    val textMeasurer = rememberTextMeasurer()
    val segmentPaddingPx = with(density) { 2 * TsuyomiSpacing.Sm.roundToPx() + 6.dp.roundToPx() }
    val minSegmentWidthPx = remember(options, labelStyle, density) {
        val textWidth = options.maxOfOrNull { textMeasurer.measure(it.label, style = labelStyle).size.width } ?: 0
        with(density) { (textWidth + segmentPaddingPx).coerceAtLeast(48.dp.roundToPx()) }
    }
    val scrollState = rememberScrollState()
    val overflowing = remember(options, labelStyle, density, viewportWidthPx, eInk) {
        if (eInk || viewportWidthPx == 0 || options.isEmpty()) false else {
            val availableTextWidth = viewportWidthPx / options.size - segmentPaddingPx
            availableTextWidth < 1 || options.any { option ->
                textMeasurer.measure(
                    option.label,
                    style = labelStyle,
                    maxLines = 2,
                    constraints = Constraints(maxWidth = availableTextWidth),
                ).hasVisualOverflow
            }
        }
    }
    LaunchedEffect(overflowing, selectedIndex, trackWidthPx, viewportWidthPx) {
        if (overflowing && selectedIndex >= 0 && trackWidthPx > 0) {
            val segmentWidthPx = trackWidthPx.toFloat() / options.size
            val centered = (segmentWidthPx * (selectedIndex + 0.5f) - viewportWidthPx / 2f)
                .roundToInt().coerceIn(0, scrollState.maxValue)
            scrollState.scrollTo(centered)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = TsuyomiSpacing.Sm),
            )
        }
        Box(Modifier.fillMaxWidth().onSizeChanged { viewportWidthPx = it.width }) {
            Box(
                Modifier.fillMaxWidth()
                    .then(if (overflowing) Modifier.horizontalScroll(scrollState) else Modifier),
            ) {
                Box(
                    modifier = Modifier
                        .then(if (overflowing) Modifier.width(with(density) {
                            (minSegmentWidthPx * options.size).toDp()
                        }) else Modifier.fillMaxWidth())
                        .height(IntrinsicSize.Min)
                        .onSizeChanged { trackWidthPx = it.width }
                        .then(if (eInk) Modifier else Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape))
                        .clip(shape),
                ) {
                    if (!eInk && selectedIndex >= 0) {
                        Box(Modifier.matchParentSize()) {
                            Box(
                                Modifier
                                    .offset {
                                        IntOffset((trackWidthPx * indicatorPosition / options.size).roundToInt(), 0)
                                    }
                                    .fillMaxWidth(1f / options.size)
                                    .fillMaxHeight()
                                    .padding(3.dp)
                                    .shadow(1.dp, RoundedCornerShape(9.dp))
                                    .background(selectedSurface, RoundedCornerShape(9.dp)),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .semantics {
                                collectionInfo = CollectionInfo(1, options.size)
                                if (errorMessage != null) {
                                    error(errorMessage)
                                }
                            }
                            .then(if (eInk) Modifier.border(1.dp, borderColor, shape) else Modifier),
                    ) {
                        options.forEachIndexed { index, option ->
                            if (eInk && index > 0) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .width(1.dp)
                                        .background(borderColor),
                                )
                            }
                            SegmentView(
                                option = option,
                                selected = option.value == selected,
                                onClick = { onSelect(option.value) },
                                enabled = enabled && option.enabled,
                                eInk = eInk,
                                index = index,
                                labelStyle = labelStyle,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
        if (!enabled && disabledReason != null) {
            Text(
                text = disabledReason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
            )
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
            )
        }
    }
}

@Composable
private fun <T> SegmentView(
    option: TsuyomiSegment<T>,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    eInk: Boolean,
    index: Int,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val selectedDescription = stringResource(R.string.coreui_state_selected)
    val unselectedDescription = stringResource(R.string.coreui_state_not_selected)
    val disabledDescription = stringResource(R.string.coreui_state_disabled)

    val containerColor = if (selected && eInk) TsuyomiEInkPalette.Ink else Color.Transparent
    val textColor = when {
        !enabled && eInk -> TsuyomiEInkPalette.N50
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        selected && eInk -> TsuyomiEInkPalette.Paper
        selected -> MaterialTheme.colorScheme.activeAccent
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(if (eInk) 4.dp else 12.dp)

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .tsuyomiFocusRing(shape, focused, MaterialTheme.colorScheme.primary)
            .background(containerColor)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                stateDescription = when {
                    !enabled -> disabledDescription
                    selected -> selectedDescription
                    else -> unselectedDescription
                }
                collectionItemInfo = CollectionItemInfo(0, 1, index, 1)
            }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.label,
            style = labelStyle,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}
