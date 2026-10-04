/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.activeAccent
import org.tsuyomi.core.ui.theme.instantMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Immutable
data class TsuyomiFilterCapsuleOption(
    val key: String,
    val label: String,
)

/** Stable source-order filter chips; selection moves the viewport, never the options. */
@Composable
fun TsuyomiFilterCapsuleOptionRow(
    options: List<TsuyomiFilterCapsuleOption>,
    selectedKey: String?,
    expanded: Boolean,
    expandedStateDescription: String,
    collapsedStateDescription: String,
    onToggleExpanded: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    revealSelectionKey: Boolean = true,
) {
    val isEInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val ordered = remember(options, selectedKey, isEInk) {
        if (isEInk) eInkSelectedFirst(options, selectedKey) else options
    }
    val scrollState = rememberScrollState()
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    val chipCoordinates = remember(options) { mutableStateMapOf<String, LayoutCoordinates>() }
    val layoutDirection = LocalLayoutDirection.current
    val fontScale = LocalDensity.current.fontScale
    LaunchedEffect(selectedKey, expanded, viewportWidth, options, isEInk, layoutDirection, fontScale, revealSelectionKey) {
        if (!isEInk && !expanded && selectedKey != null) {
            val viewport = viewportCoordinates ?: return@LaunchedEffect
            val chip = snapshotFlow { chipCoordinates[selectedKey] }.filterNotNull().first()
            if (!viewport.isAttached || !chip.isAttached) return@LaunchedEffect
            val selected = viewport.localBoundingBoxOf(chip, clipBounds = false)
            val delta = when {
                selected.left < 0f -> selected.left
                selected.right > viewport.size.width -> selected.right - viewport.size.width
                else -> 0f
            }
            val direction = if (layoutDirection == LayoutDirection.Rtl) -1 else 1
            scrollState.scrollTo((scrollState.value + direction * delta).roundToInt())
        }
    }
    CapsuleSurface(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned {
                        viewportCoordinates = it
                        viewportWidth = it.size.width
                    }
                    .horizontalScroll(scrollState)
                    .testTag("filter-capsule-scroll")
                    .padding(horizontal = TsuyomiSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ordered.forEach { option ->
                    TsuyomiFilterCapsuleChip(
                        option = option,
                        selected = option.key == selectedKey,
                        onClick = { onSelect(option.key) },
                        modifier = Modifier
                            .onGloballyPositioned { chipCoordinates[option.key] = it },
                    )
                }
            }
            CapsuleArrow(
                expanded = expanded,
                stateDescription = if (expanded) expandedStateDescription else collapsedStateDescription,
                onClick = onToggleExpanded,
            )
        }
    }
}

/** Compact standard Material 3 tonal button showing the current value and opening its panel. */
@Composable
fun TsuyomiFilterCapsuleButton(
    label: String,
    expanded: Boolean,
    expandedStateDescription: String,
    collapsedStateDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val state = if (expanded) expandedStateDescription else collapsedStateDescription
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics { stateDescription = state },
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(start = TsuyomiSpacing.Md, end = TsuyomiSpacing.Sm),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
        )
        CapsuleDisclosureIcon(expanded = expanded)
    }
}

/** Bounded inline panel for the currently expanded capsule. Selection is owned by the caller. */
@Composable
fun TsuyomiFilterCapsulePanel(
    options: List<TsuyomiFilterCapsuleOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val scrollState = rememberScrollState()
    val containPanelScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(x = 0f, y = available.y)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(x = 0f, y = available.y)
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth().testTag("filter-capsule-panel"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .nestedScroll(containPanelScroll)
                .verticalScroll(scrollState)
                .padding(TsuyomiSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(
                space = TsuyomiSpacing.Sm,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
        ) {
            options.forEach { option ->
                TsuyomiFilterCapsuleChip(
                    option = option,
                    selected = option.key == selectedKey,
                    onClick = { onSelect(option.key) },
                    showSelectedMark = isEInk,
                )
            }
        }
    }
}

@Composable
private fun CapsuleSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        content = content,
    )
}

@Composable
private fun TsuyomiFilterCapsuleChip(
    option: TsuyomiFilterCapsuleOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSelectedMark: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val isEInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = if (isEInk) FilterChipDefaults.filterChipColors() else FilterChipDefaults.filterChipColors(
            labelColor = scheme.onSurfaceVariant,
            selectedContainerColor = if (LocalDisplayEnvironment.current.effectiveDarkTheme) {
                scheme.surfaceContainerLow
            } else {
                scheme.surface
            },
            selectedLabelColor = scheme.activeAccent,
        ),
        border = if (isEInk) {
            FilterChipDefaults.filterChipBorder(enabled = true, selected = selected)
        } else {
            BorderStroke(1.dp, if (selected) scheme.activeAccent else scheme.outlineVariant)
        },
        label = {
            Text(
                text = option.label,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
                color = if (isEInk) {
                    if (selected) scheme.activeAccent else scheme.onSurface
                } else {
                    Color.Unspecified
                },
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
        },
        leadingIcon = if (selected && showSelectedMark) {
            {
                Icon(
                    imageVector = TsuyomiIcons.Selected,
                    contentDescription = null,
                    modifier = Modifier.width(20.dp),
                    tint = MaterialTheme.colorScheme.activeAccent,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun CapsuleArrow(
    expanded: Boolean,
    stateDescription: String,
    onClick: () -> Unit,
) {
    val isEInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .width(48.dp)
            .then(if (isEInk) Modifier.fillMaxHeight() else Modifier.heightIn(min = 48.dp))
            .semantics { this.stateDescription = stateDescription },
    ) {
        CapsuleDisclosureIcon(
            expanded = expanded,
            contentDescription = stateDescription,
        )
    }
}

@Composable
private fun CapsuleDisclosureIcon(
    expanded: Boolean,
    contentDescription: String? = null,
) {
    val instant = LocalDisplayEnvironment.current.instantMotion
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (instant) {
            snap()
        } else {
            tween(TsuyomiMotion.SELECTION_DURATION_MS, easing = TsuyomiMotion.Easing)
        },
        label = "filterCapsuleDisclosureRotation",
    )
    Icon(

        imageVector = TsuyomiIcons.Disclosure,
        contentDescription = contentDescription,
        modifier = Modifier.rotate(rotation),
    )
}
private fun eInkSelectedFirst(
    options: List<TsuyomiFilterCapsuleOption>,
    selectedKey: String?,
): List<TsuyomiFilterCapsuleOption> {
    val selected = options.firstOrNull { it.key == selectedKey } ?: return options
    return buildList(options.size) {
        add(selected)
        options.forEach { if (it.key != selectedKey) add(it) }
    }
}

