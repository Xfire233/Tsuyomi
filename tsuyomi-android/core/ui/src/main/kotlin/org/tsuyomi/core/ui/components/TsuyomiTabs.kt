/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
