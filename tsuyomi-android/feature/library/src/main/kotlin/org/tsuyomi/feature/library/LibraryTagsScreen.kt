/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.database.LibraryEntry
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiStateKind

private enum class TagLayout { CHIPS, LIST }

private data class TagCount(val name: String, val count: Int)

@Composable
fun LibraryTagsScreen(
    entries: List<LibraryEntry>,
    onOpenTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var layout by rememberSaveable { mutableStateOf(TagLayout.CHIPS) }
    val tags = entries
        .flatMap(LibraryEntry::localTags)
        .groupingBy(String::trim)
        .eachCount()
        .filterKeys(String::isNotBlank)
        .map { TagCount(it.key, it.value) }
        .sortedBy(TagCount::name)
    if (tags.isEmpty()) {
        StateView(
            kind = TsuyomiStateKind.EMPTY,
            title = "还没有本地标签",
            message = "在书籍详情中添加标签后会显示在这里。",
            modifier = modifier,
        )
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text("本地标签") },
                supportingContent = { Text("${tags.size} 个标签") },
                trailingContent = {
                    Text(
                        if (layout == TagLayout.CHIPS) "紧凑" else "列表",
                        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) {
                            layout = if (layout == TagLayout.CHIPS) TagLayout.LIST else TagLayout.CHIPS
                        }.padding(horizontal = 12.dp, vertical = 14.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }
        if (layout == TagLayout.CHIPS) {
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        AssistChip(onClick = { onOpenTag(tag.name) }, label = { Text(tag.name) })
                    }
                }
            }
        } else {
            items(tags, key = TagCount::name) { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    trailingContent = { Text("${tag.count} 本") },
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenTag(tag.name) },
                )
            }
        }
    }
}
