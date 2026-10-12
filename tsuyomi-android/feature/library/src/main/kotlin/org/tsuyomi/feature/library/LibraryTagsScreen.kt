/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.util.Locale
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiActionChip
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiSelectableRow
import org.tsuyomi.core.ui.components.PaginationBar
import androidx.compose.material3.HorizontalDivider
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.TsuyomiMenuItem
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTabOption
import org.tsuyomi.core.ui.components.TsuyomiTabRow
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.model.BookIdentity

enum class LibraryTagOwnership { LOCAL, SOURCE }

enum class LibraryTagLayout { CHIPS, LIST }

data class LibraryTagDestination(
    val ownership: LibraryTagOwnership,
    val normalizedName: String,
    val sourceId: String? = null,
)

private data class TagCount(
    val destination: LibraryTagDestination,
    val name: String,
    val sourceName: String?,
    val count: Int,
) {
    val saveableKey = "${destination.ownership.name}:${destination.sourceId?.length ?: -1}:${destination.sourceId.orEmpty()}${destination.normalizedName}"
}


@Composable
fun LibraryTagsScreen(
    entries: List<LibraryEntry>,
    sourceLabels: Map<String, String>,
    layout: LibraryTagLayout,
    onOpenTag: (LibraryTagDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var ownership by rememberSaveable { mutableStateOf(LibraryTagOwnership.LOCAL) }
    val tagStateHolder = rememberSaveableStateHolder()
    val tags = when (ownership) {
        LibraryTagOwnership.LOCAL -> localTags(entries)
        LibraryTagOwnership.SOURCE -> sourceTags(entries, sourceLabels)
    }
    Column(modifier.fillMaxSize()) {
        TsuyomiTabRow(
            options = listOf(
                TsuyomiTabOption(LibraryTagOwnership.LOCAL.name, "本地"),
                TsuyomiTabOption(LibraryTagOwnership.SOURCE.name, "来源"),
            ),
            selectedKey = ownership.name,
            onSelect = { selected -> ownership = LibraryTagOwnership.valueOf(selected) },
        )
        if (tags.isEmpty()) {
            StateView(
                kind = TsuyomiStateKind.EMPTY,
                title = if (ownership == LibraryTagOwnership.LOCAL) "还没有本地标签" else "还没有来源标签",
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }
        tagStateHolder.SaveableStateProvider("tags:${ownership.name}:$layout:${LocalDisplayEnvironment.current.effectiveProfile.name}") {
            LibraryTagResultsPage(
                tags = tags,
                ownership = ownership,
                layout = layout,
                onOpenTag = onOpenTag,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LibraryTagResultsPage(
    tags: List<TagCount>,
    ownership: LibraryTagOwnership,
    layout: LibraryTagLayout,
    onOpenTag: (LibraryTagDestination) -> Unit,
    modifier: Modifier,
) {
    val eink = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    if (!eink) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = modifier.fillMaxSize(),
        ) {
            item(key = "tag-header:${ownership.name}") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = TsuyomiSpacing.Md,
                        vertical = TsuyomiSpacing.Sm,
                    ),
                ) {
                    Text(if (ownership == LibraryTagOwnership.LOCAL) "本地标签" else "来源标签")
                    Text("${tags.size} 个标签")
                }
            }
            if (layout == LibraryTagLayout.CHIPS) {
                item(key = "tag-chips:${ownership.name}") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(TsuyomiSpacing.Md),
                        horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                    ) {
                        tags.forEach { tag ->
                            TsuyomiActionChip(
                                text = tag.chipLabel(),
                                stateDescription = tag.accessibilityLabel(),
                                onClick = { onOpenTag(tag.destination) },
                                modifier = Modifier.testTag(tag.testTag()),
                            )
                        }
                    }
                }
            } else {
                items(tags, key = TagCount::saveableKey) { tag ->
                    TsuyomiSelectableRow(
                        selected = false,
                        onClick = { onOpenTag(tag.destination) },
                        onLongClick = null,
                        modifier = Modifier.testTag(tag.testTag()),
                    ) {
                        TagListRowContent(tag)
                    }
                }
            }
        }
        return
    }

    TsuyomiMeasuredPager(itemCount = tags.size, itemKey = { tags[it].saveableKey },
        modifier = modifier.fillMaxSize(), state = rememberTsuyomiMeasuredPagerState(tags.firstOrNull()?.saveableKey),
        itemContent = { index ->
            val tag = tags[index]
            TsuyomiMeasuredPager(itemCount = if (layout == LibraryTagLayout.CHIPS) 1 else 2,
                itemKey = { "${tag.saveableKey}:$it" }, fillViewport = false,
                itemContent = { part ->
                    if (part == 0 && layout == LibraryTagLayout.CHIPS) TsuyomiActionChip(
                        text = tag.chipLabel(), stateDescription = tag.accessibilityLabel(),
                        onClick = { onOpenTag(tag.destination) }, modifier = Modifier.testTag(tag.testTag()))
                    else if (part == 0) TsuyomiMenuItem(tag.chipLabel(), { onOpenTag(tag.destination) },
                        modifier = Modifier.testTag(tag.testTag()).semantics { contentDescription = tag.accessibilityLabel() })
                    else TsuyomiMeasuredTextPager(listOfNotNull(tag.sourceName, "${tag.count} 本").joinToString(" · "), fillViewport = false, alwaysShowPageControls = false)
                })
        }, pageControls = { page, canGoPrevious, canGoNext, previous, next, pageCount, onFirst, onLast ->
            PaginationBar(page = page, pageCount = pageCount, onPrevious = previous, onNext = next,
                hasPreviousPage = canGoPrevious, hasNextPage = canGoNext, onFirst = onFirst, onLast = onLast,
                modifier = Modifier.testTag("library-tags-eink-pagination"))
        })
}

@Composable
private fun RowScope.TagListRowContent(tag: TagCount) {
    Column(
        modifier = Modifier.weight(1f).padding(
            start = TsuyomiSpacing.Md,
            top = TsuyomiSpacing.Sm,
            bottom = TsuyomiSpacing.Sm,
        ),
    ) {
        Text(tag.name)
        tag.sourceName?.let { sourceName -> Text(sourceName) }
    }
    Text(text = "${tag.count} 本", modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md))
}

private fun localTags(entries: List<LibraryEntry>): List<TagCount> = entries
    .flatMap { entry ->
        entry.localTags.mapNotNull { raw -> normalizeLocalLibraryTagName(raw)?.let { key -> entry.book.identity to (key to normalizedTagDisplay(raw)) } }
    }
    .groupBy { (_, tag) -> tag.first }
    .map { (key, matches) ->
        TagCount(
            destination = LibraryTagDestination(LibraryTagOwnership.LOCAL, key),
            name = matches.minOf { it.second.second },
            sourceName = null,
            count = matches.map { it.first }.toSet().size,
        )
    }
    .sortedBy(TagCount::name)

private fun sourceTags(entries: List<LibraryEntry>, sourceLabels: Map<String, String>): List<TagCount> = entries
    .flatMap { entry ->
        val identity = entry.book.identity as? BookIdentity.Remote ?: return@flatMap emptyList()
        entry.book.remoteTags.mapNotNull { raw -> normalizeSourceLibraryTagName(raw)?.let { key ->
            Triple(identity, identity.sourceId, key)
        } }
    }
    .groupBy { (_, sourceId, key) -> sourceId to key }
    .map { (identity, matches) ->
        val (sourceId, key) = identity
        TagCount(
            destination = LibraryTagDestination(LibraryTagOwnership.SOURCE, key, sourceId),
            name = key,
            sourceName = sourceLabels[sourceId]?.takeIf(String::isNotBlank) ?: sourceId,
            count = matches.map { it.first }.toSet().size,
        )
    }
    .sortedWith(compareBy(TagCount::name, TagCount::sourceName))

fun normalizeLocalLibraryTagName(value: String): String? = normalizedTagDisplay(value)
    .takeIf(String::isNotBlank)
    ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC).lowercase(Locale.ROOT) }

fun normalizeSourceLibraryTagName(value: String): String? = normalizedTagDisplay(value).takeIf(String::isNotBlank)

private fun normalizedTagDisplay(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC).replace(Regex("\\s+"), " ").trim()

private fun TagCount.chipLabel(): String = sourceName?.let { "$name · $it" } ?: name

private fun TagCount.accessibilityLabel(): String = sourceName?.let { "$name，$it" } ?: name

private fun TagCount.testTag(): String =
    "library-tag-${destination.ownership.name}-${destination.sourceId.orEmpty()}-${destination.normalizedName}"
