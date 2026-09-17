/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.components.TsuyomiToggleChip
import org.tsuyomi.core.ui.components.TsuyomiIconButton
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiCheckboxRow
import org.tsuyomi.core.ui.components.TsuyomiTriStateCheckboxRow
import org.tsuyomi.shared.sourcecontract.SourceChapter

@Composable
internal fun DetailVolumeHeader(
    title: String,
    chapterCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val state = stringResource(if (expanded) R.string.book_volume_expanded else R.string.book_volume_collapsed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .heightIn(min = 52.dp)
            .padding(start = TsuyomiSpacing.Md + 20.dp + TsuyomiSpacing.Sm, end = TsuyomiSpacing.Xs)
            .semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.book_chapter_count, chapterCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = TsuyomiIcons.Disclosure,
            contentDescription = null,
            modifier = Modifier.size(48.dp).padding(12.dp).rotate(if (expanded) 0f else -90f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DetailDirectoryHeader(
    totalChapters: Int,
    unreadOnly: Boolean,
    unreadFilterAvailable: Boolean,
    descending: Boolean,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
) {
    val filterStateDescription = stringResource(
        if (unreadOnly) R.string.book_unread_filter_active else R.string.book_unread_filter_all,
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = TsuyomiSpacing.Md, end = TsuyomiSpacing.Xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                TsuyomiIcons.List,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.book_full_directory),
                modifier = Modifier.padding(start = TsuyomiSpacing.Sm).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.book_chapter_count, totalChapters),
                modifier = Modifier.padding(start = TsuyomiSpacing.Sm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TsuyomiToggleChip(
                text = stringResource(R.string.book_unread_only),
                selected = unreadOnly,
                stateDescription = filterStateDescription,
                onClick = onToggleUnreadOnly,
                enabled = unreadFilterAvailable,
                compact = true,
                leadingIcon = if (unreadOnly) TsuyomiIcons.Selected else TsuyomiIcons.Filter,
                modifier = Modifier.testTag("detail-unread-filter"),
            )
            TsuyomiIconButton(
                imageVector = if (descending) TsuyomiIcons.ToBottom else TsuyomiIcons.ToTop,
                contentDescription = stringResource(
                    if (descending) R.string.book_order_descending else R.string.book_order_ascending,
                ),
                onClick = onToggleOrder,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
internal fun DetailModuleHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            title,
            modifier = Modifier.padding(start = TsuyomiSpacing.Sm).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
internal fun DetailChapterRow(item: DetailChapterItem, onSelectChapter: (SourceChapter) -> Unit) {
    val status = buildList {
        if (item.updated) add(stringResource(R.string.book_chapter_updated))
        item.read?.let { add(stringResource(if (it) R.string.book_chapter_read else R.string.book_chapter_unread)) }
        if (item.downloaded) add(stringResource(R.string.book_chapter_downloaded))
        if (item.current) add(stringResource(R.string.book_chapter_current))
    }.joinToString("，")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (item.current) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button) { onSelectChapter(item.chapter) }
                .testTag("detail-chapter-${item.chapter.chapterId}")
                .semantics(mergeDescendants = true) {
                    if (status.isNotEmpty()) stateDescription = status
                }
                .heightIn(min = 56.dp)
                .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailChapterMarker(item)
            Spacer(Modifier.size(TsuyomiSpacing.Sm))
            Text(
                item.chapter.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (item.read == true) FontWeight.Normal else FontWeight.Medium,
                ),
                color = if (item.read == true) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            if (item.downloaded) {
                Icon(
                    TsuyomiIcons.Downloaded,
                    contentDescription = stringResource(R.string.book_chapter_downloaded),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (item.current) {
                Surface(
                    modifier = Modifier.padding(start = TsuyomiSpacing.Xs),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        stringResource(R.string.book_current_badge),
                        modifier = Modifier.padding(horizontal = TsuyomiSpacing.Sm, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DetailChapterMarker(item: DetailChapterItem) {
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when {
            item.updated -> Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
            item.read == false -> Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            item.read == true -> Unit
            else -> Box(Modifier.size(8.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }
    }
}

@Composable
internal fun DetailCacheSelectionToolbar(
    cacheState: DetailCacheState,
    selectableChapterIds: Set<String>,
    onCacheAction: (DetailCacheAction) -> Unit,
    focusRequester: FocusRequester,
) {
    val selectedInScope = cacheState.selectedChapterIds.count { it in selectableChapterIds }
    val selectedCount = cacheState.selectedChapterIds.count { cacheState.chapters[it] != DetailChapterCachePhase.CACHED }
    val selectionState = when {
        selectedInScope == 0 -> ToggleableState.Off
        selectedInScope == selectableChapterIds.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val selectAllLabel = stringResource(
        if (selectionState == ToggleableState.On) R.string.book_cache_deselect_all else R.string.book_cache_select_all,
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TsuyomiSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (cacheState.working) {
            Text(
                text = stringResource(R.string.book_cache_working, selectedCount),
                modifier = Modifier.heightIn(min = 48.dp),
                style = MaterialTheme.typography.labelLarge,
            )
            TsuyomiButton(
                text = stringResource(R.string.book_cache_cancel),
                onClick = { onCacheAction(DetailCacheAction.Cancel) },
                style = TsuyomiButtonStyle.SECONDARY,
                modifier = Modifier.testTag("detail-cache-cancel"),
            )
        } else {
            TsuyomiTriStateCheckboxRow(
                state = selectionState,
                label = selectAllLabel,
                enabled = selectableChapterIds.isNotEmpty(),
                onClick = { onCacheAction(DetailCacheAction.ToggleAll(selectableChapterIds)) },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusProperties { canFocus = true }
                    .testTag("detail-cache-toggle-all"),
            )
            TsuyomiButton(
                text = stringResource(R.string.book_cache_start_selected, selectedCount),
                onClick = { onCacheAction(DetailCacheAction.Start) },
                modifier = Modifier.testTag("detail-cache-start"),
                enabled = selectedCount > 0,
            )
            TsuyomiIconButton(
                imageVector = TsuyomiIcons.Close,
                contentDescription = stringResource(R.string.book_cache_close_selection),
                onClick = { onCacheAction(DetailCacheAction.Close) },
                modifier = Modifier.testTag("detail-cache-close"),
            )
        }
    }
}


@Composable
internal fun DetailCacheChapterRow(
    item: DetailChapterItem,
    cacheState: DetailCacheState,
    onCacheAction: (DetailCacheAction) -> Unit,
) {
    val phase = cacheState.chapters[item.chapter.chapterId]
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TsuyomiCheckboxRow(
            label = item.chapter.title,
            checked = item.chapter.chapterId in cacheState.selectedChapterIds,
            onCheckedChange = { onCacheAction(DetailCacheAction.Toggle(item.chapter.chapterId)) },
            modifier = Modifier
                .padding(end = 48.dp)
                .testTag("detail-cache-chapter-${item.chapter.chapterId}"),
            enabled = !cacheState.working && phase != DetailChapterCachePhase.CACHED,
        )
        when (phase) {
            DetailChapterCachePhase.QUEUED -> DetailCacheStatusIcon(
                imageVector = TsuyomiIcons.Cache,
                contentDescription = stringResource(R.string.book_cache_queued),
            )
            DetailChapterCachePhase.CACHING -> DetailCacheStatusIcon(
                imageVector = TsuyomiIcons.Cache,
                contentDescription = stringResource(R.string.book_cache_caching),
            )
            DetailChapterCachePhase.CACHED -> DetailCacheStatusIcon(
                imageVector = TsuyomiIcons.Downloaded,
                contentDescription = stringResource(R.string.book_chapter_downloaded),
            )
            DetailChapterCachePhase.FAILED -> DetailCacheStatusIcon(
                imageVector = TsuyomiIcons.Close,
                contentDescription = stringResource(R.string.book_cache_failed),
                tint = MaterialTheme.colorScheme.error,
            )
            DetailChapterCachePhase.CANCELLED -> DetailCacheStatusIcon(
                imageVector = TsuyomiIcons.Close,
                contentDescription = stringResource(R.string.book_cache_cancelled),
            )
            null -> Unit
        }
    }
}

@Composable
private fun DetailCacheStatusIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier
            .padding(end = TsuyomiSpacing.Md)
            .size(20.dp),
        tint = tint,
    )
}
