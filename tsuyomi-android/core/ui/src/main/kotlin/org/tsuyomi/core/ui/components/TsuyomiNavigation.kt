/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.R
import org.tsuyomi.core.ui.layout.TsuyomiNavigationLayout
import org.tsuyomi.core.ui.theme.TsuyomiEInkPalette
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.theme.tsuyomiAnimateColorAsState
import org.tsuyomi.core.ui.theme.tsuyomiFocusRing

/** One top-level destination rendered by [TsuyomiNavigation]. Labels must stay within 4 CJK chars. */
data class TsuyomiNavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** Test tag placed on the navigation container; the value names the active layout variant. */
const val TSUYOMI_NAVIGATION_TAG_PREFIX = "tsuyomi_navigation_"

/**
 * Adaptive top-level navigation. The same items, selection, and callbacks render as a bottom bar,
 * a compact bottom bar, or a side rail; switching layouts never recreates screen state because the
 * scaffold keeps the content slot stable.
 *
 * Selection feedback is profile-driven: the standard profile uses a short ease-out tonal
 * transition, while the E-ink profile swaps an opaque inverted indicator immediately. No default
 * Material indicator animation is used in either profile.
 */
@Composable
fun TsuyomiNavigation(
    layout: TsuyomiNavigationLayout,
    items: List<TsuyomiNavigationItem>,
    selectedRoute: String?,
    onSelect: (TsuyomiNavigationItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val environment = LocalDisplayEnvironment.current
    val eInk = environment.effectiveProfile == DisplayProfile.EINK
    val dividerColor = if (eInk) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val tag = TSUYOMI_NAVIGATION_TAG_PREFIX + when (layout) {
        TsuyomiNavigationLayout.BOTTOM_BAR -> "bottom_bar"
        TsuyomiNavigationLayout.COMPACT_BOTTOM_BAR -> "compact_bottom_bar"
        TsuyomiNavigationLayout.RAIL -> "rail"
    }

    when (layout) {
        TsuyomiNavigationLayout.RAIL -> NavigationRailLayout(
            items = items,
            selectedRoute = selectedRoute,
            onSelect = onSelect,
            eInk = eInk,
            dividerColor = dividerColor,
            modifier = modifier.testTag(tag),
        )
        else -> NavigationBarLayout(
            items = items,
            selectedRoute = selectedRoute,
            onSelect = onSelect,
            eInk = eInk,
            compact = layout == TsuyomiNavigationLayout.COMPACT_BOTTOM_BAR,
            dividerColor = dividerColor,
            modifier = modifier.testTag(tag),
        )
    }
}

@Composable
private fun NavigationBarLayout(
    items: List<TsuyomiNavigationItem>,
    selectedRoute: String?,
    onSelect: (TsuyomiNavigationItem) -> Unit,
    eInk: Boolean,
    compact: Boolean,
    dividerColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = (if (eInk) 1.5.dp else 1.dp).toPx()
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, strokeWidth / 2f),
                    end = Offset(size.width, strokeWidth / 2f),
                    strokeWidth = strokeWidth,
                )
            },
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .heightIn(min = if (compact) 56.dp else 64.dp)
                .semantics { collectionInfo = CollectionInfo(1, items.size) },
        ) {
            items.forEachIndexed { index, item ->
                NavigationItemView(
                    item = item,
                    selected = item.route == selectedRoute,
                    onClick = { onSelect(item) },
                    eInk = eInk,
                    compact = compact,
                    itemInfo = CollectionItemInfo(0, 1, index, 1),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavigationRailLayout(
    items: List<TsuyomiNavigationItem>,
    selectedRoute: String?,
    onSelect: (TsuyomiNavigationItem) -> Unit,
    eInk: Boolean,
    dividerColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .drawBehind {
                val strokeWidth = (if (eInk) 1.5.dp else 1.dp).toPx()
                drawLine(
                    color = dividerColor,
                    start = Offset(size.width - strokeWidth / 2f, 0f),
                    end = Offset(size.width - strokeWidth / 2f, size.height),
                    strokeWidth = strokeWidth,
                )
            },
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Start + WindowInsetsSides.Bottom,
                    ),
                )
                .width(88.dp)
                .semantics { collectionInfo = CollectionInfo(items.size, 1) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEachIndexed { index, item ->
                NavigationItemView(
                    item = item,
                    selected = item.route == selectedRoute,
                    onClick = { onSelect(item) },
                    eInk = eInk,
                    compact = false,
                    itemInfo = CollectionItemInfo(index, 1, 0, 1),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NavigationItemView(
    item: TsuyomiNavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
    eInk: Boolean,
    compact: Boolean,
    itemInfo: CollectionItemInfo,
    modifier: Modifier = Modifier,
) {
    val environment = LocalDisplayEnvironment.current
    val instant = environment.instantMotion
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val selectedDescription = stringResource(R.string.coreui_state_selected)
    val unselectedDescription = stringResource(R.string.coreui_state_not_selected)

    val indicatorShape: Shape = RoundedCornerShape(if (eInk) 4.dp else 16.dp)
    val indicatorTarget = when {
        !selected -> Color.Transparent
        eInk -> TsuyomiEInkPalette.Ink
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val indicatorColor = tsuyomiAnimateColorAsState(indicatorTarget, instant, "navIndicator")
    // The icon sits on the indicator pill; the label sits on the bar/rail surface.
    val iconTint = when {
        selected && eInk -> TsuyomiEInkPalette.Paper
        selected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val labelColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .tsuyomiFocusRing(indicatorShape, focused, MaterialTheme.colorScheme.primary)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                stateDescription = if (selected) selectedDescription else unselectedDescription
                collectionItemInfo = itemInfo
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(if (compact) 28.dp else 32.dp)
                .background(indicatorColor, indicatorShape)
                .testTag("tsuyomi_nav_indicator_${item.route}"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = iconTint,
            )
        }
        Text(
            text = item.label,
            modifier = Modifier.padding(top = 2.dp),
            style = if (compact) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
