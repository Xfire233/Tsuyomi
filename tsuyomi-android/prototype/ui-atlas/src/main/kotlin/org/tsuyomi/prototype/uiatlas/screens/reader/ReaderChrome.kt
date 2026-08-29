/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import kotlin.math.roundToInt

@Composable
internal fun ReaderTopChrome(
    chapterTitle: String,
    bookmarked: Boolean,
    onUp: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    AtlasTopBar(
        modifier = Modifier.testTag("reader-top-chrome"),
        title = chapterTitle,
        onUp = onUp,
        actions = listOf(
            AtlasTopBarAction(
                icon = if (bookmarked) AtlasIcons.Bookmarked else AtlasIcons.ReadLater,
                label = if (bookmarked) "移除书签" else "添加书签",
                onClick = onToggleBookmark,
            ),
            AtlasTopBarAction(AtlasIcons.Search, "搜索", onOpenSearch),
        ),
    )

}

@Composable
internal fun ReaderReadingInfoBar(
    chapterTitle: String,
    progress: Int,
    position: ReaderPosition,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val displayPosition = if (progress == position.progress) position else {
        ReaderPosition.fromProgress(progress, position.pageCount)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .testTag("reader-reading-info")
            .semantics(mergeDescendants = true) {
                stateDescription = "本章进度 $progress%，第 ${displayPosition.page} / ${displayPosition.pageCount} 页"
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 1.dp,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AtlasSpacing.Sm, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    chapterTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${displayPosition.page}/${displayPosition.pageCount} 页 · $progress%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }
    }
}

@Composable
internal fun ReaderBottomChrome(
    chapterNumber: Int,
    totalChapters: Int,
    chapterProgress: Int,
    position: ReaderPosition,
    seekPreview: Int?,
    onSeekPreview: (Int) -> Unit,
    onSeekCommit: () -> Unit,
    onPreviousChapter: () -> Unit,
    onOpenContents: () -> Unit,
    onOpenSettings: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val compactHeightPx = with(LocalDensity.current) { 600.dp.roundToPx() }
    val compact = LocalWindowInfo.current.containerSize.height < compactHeightPx
    val preview = seekPreview ?: chapterProgress
    val previewPosition = ReaderPosition.fromProgress(preview, position.pageCount)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        if (compact) WindowInsetsSides.Horizontal else WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(horizontal = AtlasSpacing.Md, vertical = if (compact) 0.dp else AtlasSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else AtlasSpacing.Xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本章 $preview%",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${previewPosition.page}/${previewPosition.pageCount} 页${if (seekPreview == null) "" else " · 松手定位"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (seekPreview == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    repeat(5) { index ->
                        Box(
                            Modifier
                                .size(if (index == 0 || index == 4) 5.dp else 3.dp)
                                .background(MaterialTheme.colorScheme.outline, CircleShape),
                        )
                    }
                }
                Slider(
                    value = preview.toFloat(),
                    onValueChange = { onSeekPreview(it.roundToInt().coerceIn(0, 100)) },
                    onValueChangeFinished = onSeekCommit,
                    valueRange = 0f..100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reader-chapter-progress-slider")
                        .semantics {
                            stateDescription = "本章目标 $preview%"
                        },
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ReaderBottomAction(
                    icon = AtlasIcons.Prev,
                    label = "上一章",
                    compact = compact,
                    enabled = chapterNumber > 1,
                    onClick = onPreviousChapter,
                    modifier = Modifier.weight(1f),
                )
                ReaderBottomAction(
                    icon = AtlasIcons.Chapters,
                    label = "目录",
                    compact = compact,
                    onClick = onOpenContents,
                    modifier = Modifier.weight(1f),
                )
                ReaderBottomAction(
                    icon = AtlasIcons.Settings,
                    label = "设置",
                    onClick = onOpenSettings,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                )
                ReaderBottomAction(
                    icon = AtlasIcons.Next,
                    label = "下一章",
                    enabled = chapterNumber < totalChapters,
                    onClick = onNextChapter,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier
            .heightIn(min = if (compact) 48.dp else 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderAuxiliarySheet(
    initialTab: ReaderAuxiliaryTab,
    currentChapter: Int,
    bookmarks: Set<Int>,
    onDismiss: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onSearchSubmitted: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("reader-auxiliary-sheet"),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.88f)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                ReaderAuxiliaryTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                    )
                }
            }
            when (selectedTab) {
                ReaderAuxiliaryTab.CONTENTS -> ReaderContentsTab(currentChapter, onSelectChapter)
                ReaderAuxiliaryTab.BOOKMARKS -> ReaderBookmarksTab(bookmarks, currentChapter, onSelectChapter, onToggleBookmark)
                ReaderAuxiliaryTab.SEARCH -> ReaderSearchTab(onSelectChapter, onSearchSubmitted)
            }
        }
    }
}

@Composable
private fun ReaderContentsTab(currentChapter: Int, onSelectChapter: (Int) -> Unit) {
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var descending by rememberSaveable { mutableStateOf(false) }
    val chapters = remember(unreadOnly, descending, currentChapter) {
        SourceAtlasFixtures.chapters
            .filter { !unreadOnly || it.number >= currentChapter }
            .let { if (descending) it.reversed() else it }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(chapters, currentChapter) {
        val currentIndex = chapters.indexOfFirst { it.number == currentChapter }
        if (currentIndex >= 0) listState.scrollToItem(currentIndex)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { unreadOnly = !unreadOnly },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Icon(AtlasIcons.Filter, contentDescription = null)
                Spacer(Modifier.width(AtlasSpacing.Xs))
                Text("仅看未读")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { descending = !descending }) {
                Icon(
                    if (descending) AtlasIcons.Down else AtlasIcons.Up,
                    contentDescription = "当前顺序：${if (descending) "倒序" else "正序"}，点按切换",
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(chapters, key = { it.number }) { chapter ->
                ReaderChapterRow(chapter.number, chapter.title, chapter.number == currentChapter, onSelectChapter)
            }
        }
    }
}

@Composable
private fun ReaderChapterRow(
    number: Int,
    title: String,
    current: Boolean,
    onSelectChapter: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onSelectChapter(number) }
            .semantics {
                selected = current
                stateDescription = if (current) "当前阅读" else if (number < SourceAtlasFixtures.CURRENT_CHAPTER) "已读" else "未读"
            }
            .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
                if (number >= SourceAtlasFixtures.CURRENT_CHAPTER) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
            Spacer(Modifier.width(AtlasSpacing.Sm))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (number < SourceAtlasFixtures.CURRENT_CHAPTER && !current) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (current) Text("当前", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    HorizontalDivider()
}

@Composable
private fun ReaderBookmarksTab(
    bookmarks: Set<Int>,
    currentChapter: Int,
    onSelectChapter: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(AtlasSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(AtlasIcons.ReadLater, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(AtlasSpacing.Md))
            Text("还没有书签", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onToggleBookmark(currentChapter) }) { Text("标记当前章节") }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(bookmarks.sorted(), key = { it }) { number ->
            val chapter = SourceAtlasFixtures.chapters[number - 1]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onSelectChapter(number) }
                    .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(chapter.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        ReaderAtlasFixtures.previewSnippet(number),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onToggleBookmark(number) }) {
                    Icon(AtlasIcons.Delete, contentDescription = "移除书签")
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ReaderSearchTab(
    onSelectChapter: (Int) -> Unit,
    onSearchSubmitted: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val results = remember(submitted) {
        if (submitted.isBlank()) emptyList()
        else SourceAtlasFixtures.chapters.filter { it.title.contains(submitted, ignoreCase = true) }.take(30)
    }
    val focusManager = LocalFocusManager.current
    val submit = {
        val query = draft.trim()
        if (query.isNotBlank()) {
            submitted = query
            onSearchSubmitted(query)
            focusManager.clearFocus()
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(80) },
            modifier = Modifier.fillMaxWidth().padding(AtlasSpacing.Md),
            label = { Text("搜索本书") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            trailingIcon = {
                IconButton(
                    onClick = submit,
                    modifier = Modifier.testTag("reader-search-submit"),
                    enabled = draft.isNotBlank(),
                ) {
                    Icon(AtlasIcons.Search, contentDescription = "搜索")
                }
            },
        )
        when {
            submitted.isBlank() -> Text(
                "输入章节名或正文关键词，然后点按搜索。",
                modifier = Modifier.padding(horizontal = AtlasSpacing.Md),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            results.isEmpty() -> Text(
                "未找到“$submitted”",
                modifier = Modifier.padding(horizontal = AtlasSpacing.Md),
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.number }) { chapter ->
                    ReaderChapterRow(chapter.number, chapter.title, current = false, onSelectChapter)
                }
            }
        }
    }
}
