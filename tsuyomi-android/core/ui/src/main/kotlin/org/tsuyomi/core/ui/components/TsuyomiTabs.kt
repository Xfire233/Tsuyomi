/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.libraryTabSelected
import org.tsuyomi.core.ui.theme.libraryTabUnselected

data class TsuyomiTabOption(
    val key: String,
    val label: String,
)

@Composable
fun TsuyomiTabRow(
    options: List<TsuyomiTabOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    equalWidthWhenFits: Boolean = true,
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val tabs: @Composable () -> Unit = {
        options.forEach { option ->
            Tab(
                selected = option.key == selectedKey,
                onClick = { onSelect(option.key) },
                modifier = Modifier.testTag("tsuyomi-tab-${option.key}"),
                text = { Text(option.label) },
            )
        }
    }
    if (equalWidthWhenFits && options.size <= 4) {
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {},
            tabs = tabs,
        )
    } else {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {},
            tabs = tabs,
        )
    }
}

@Composable
fun TsuyomiLibraryTabRow(
    options: List<TsuyomiTabOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        TsuyomiTabRow(options, selectedKey, onSelect, modifier)
        return
    }
    val selectedStyle = MaterialTheme.typography.libraryTabSelected
    val unselectedStyle = MaterialTheme.typography.libraryTabUnselected
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Row(
        modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = TsuyomiSpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
    ) {
        options.forEach { option ->
            val selected = option.key == selectedKey
            val selectedLayout = textMeasurer.measure(option.label, selectedStyle, softWrap = false, maxLines = 1)
            val baseline = with(density) { selectedLayout.firstBaseline.toDp() }
            val descent = with(density) { (selectedLayout.size.height - selectedLayout.firstBaseline).toDp() }
            Tab(
                selected = selected,
                onClick = { onSelect(option.key) },
                modifier = Modifier.testTag("tsuyomi-tab-${option.key}")
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                selectedContentColor = MaterialTheme.colorScheme.libraryTabSelected,
                unselectedContentColor = MaterialTheme.colorScheme.libraryTabUnselected,
            ) {
                Text(
                    text = option.label,
                    modifier = Modifier.padding(horizontal = TsuyomiSpacing.Sm)
                        .paddingFromBaseline(top = baseline + TsuyomiSpacing.Sm, bottom = descent + TsuyomiSpacing.Sm),
                    style = if (selected) selectedStyle else unselectedStyle,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
