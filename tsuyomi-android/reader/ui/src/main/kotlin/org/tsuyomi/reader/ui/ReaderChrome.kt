/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiTopBarAction
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.sourcecontract.SourceChapter

@Composable
internal fun ReaderTopChrome(
    chapterTitle: String,
    bookmarked: Boolean,
    onUp: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    TsuyomiTopBar(
        modifier = Modifier.testTag("reader-top-chrome"),
        title = chapterTitle,
        onNavigateUp = onUp,
        actions = listOf(
            TsuyomiTopBarAction(
                icon = if (bookmarked) TsuyomiIcons.Bookmark else TsuyomiIcons.BookmarkOutline,
                label = stringResource(if (bookmarked) R.string.reader_remove_bookmark else R.string.reader_add_bookmark),
                onClick = onToggleBookmark,
            ),
            TsuyomiTopBarAction(
                icon = TsuyomiIcons.Search,
                label = stringResource(R.string.reader_search),
                onClick = onOpenSearch,
            ),
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
        ReaderPosition.fromProgress(progress, position.pageCount, position.pageStep)
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
                Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Sm, vertical = 2.dp),
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
                    stringResource(
                        R.string.reader_page_progress,
                        displayPosition.page,
                        displayPosition.pageCount,
                        progress,
                    ),
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
    chapterIndex: Int,
    chapterCount: Int,
    chapterProgress: Int,
    position: ReaderPosition,
    continuousSeek: Boolean,
    seekPreview: Int?,
    onSeekPreview: (Int) -> Unit,
    onSeekCommit: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onOpenContents: () -> Unit,
    onOpenSettings: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val compactHeightPx = with(LocalDensity.current) { 600.dp.roundToPx() }
    val compact = LocalWindowInfo.current.containerSize.height < compactHeightPx
    val preview = seekPreview ?: chapterProgress
    val previewPosition = if (continuousSeek) {
        ReaderPosition.fromProgress(preview, position.pageCount, position.pageStep)
    } else {
        ReaderPosition.fromSeekProgress(preview, position.pageCount, position.pageStep)
    }
    val selectableStops = position.selectablePageCount
    val sliderSteps = if (continuousSeek) 0 else (selectableStops - 2).coerceAtLeast(0)
    var interactionProgress by remember { mutableIntStateOf(preview) }
    LaunchedEffect(preview) { interactionProgress = preview }
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
                .padding(horizontal = TsuyomiSpacing.Md, vertical = if (compact) 0.dp else TsuyomiSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else TsuyomiSpacing.Xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_chapter_percent, preview),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        if (seekPreview == null) R.string.reader_page_count else R.string.reader_page_count_seeking,
                        previewPosition.page,
                        previewPosition.pageCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (seekPreview == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            Box(Modifier.fillMaxWidth()) {
                if (continuousSeek) {
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
                }
                Slider(
                    value = preview.toFloat(),
                    onValueChange = { value ->
                        val target = value.roundToInt().coerceIn(0, 100)
                        interactionProgress = target
                        onSeekPreview(target)
                    },
                    onValueChangeFinished = { onSeekCommit(interactionProgress) },
                    valueRange = 0f..100f,
                    steps = sliderSteps,
                    enabled = continuousSeek || selectableStops > 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reader-chapter-progress-slider")
                        .semantics {
                            stateDescription = if (continuousSeek) {
                                "本章目标 $preview%"
                            } else {
                                "本章目标第 ${previewPosition.page} 页，共 ${previewPosition.pageCount} 页"
                            }
                            customActions = listOf(
                                CustomAccessibilityAction("确认本章定位") {
                                    onSeekCommit(interactionProgress)
                                    true
                                },
                            )
                        },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ReaderBottomAction(
                    icon = TsuyomiIcons.Previous,
                    label = stringResource(R.string.reader_previous_chapter),
                    compact = compact,
                    enabled = chapterIndex > 0,
                    onClick = onPreviousChapter,
                    modifier = Modifier.weight(1f),
                )
                ReaderBottomAction(
                    icon = TsuyomiIcons.Chapters,
                    label = stringResource(R.string.reader_contents),
                    compact = compact,
                    onClick = onOpenContents,
                    modifier = Modifier.weight(1f),
                )
                ReaderBottomAction(
                    icon = TsuyomiIcons.Settings,
                    label = stringResource(R.string.reader_settings),
                    compact = compact,
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
                ReaderBottomAction(
                    icon = TsuyomiIcons.Next,
                    label = stringResource(R.string.reader_next_chapter),
                    compact = compact,
                    enabled = chapterIndex >= 0 && chapterIndex < chapterCount - 1,
                    onClick = onNextChapter,
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
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
            if (!compact) Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderAuxiliarySheet(
    initialTab: ReaderAuxiliaryTab,
    chapters: List<SourceChapter>,
    currentChapterId: String,
    bookmarks: Set<String>,
    onDismiss: () -> Unit,
    onSelectChapter: (SourceChapter) -> Unit,
    onToggleBookmark: (String) -> Unit,
) {
    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("reader-auxiliary-sheet"),
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            ReaderAuxiliaryTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) },
                    modifier = Modifier.semantics { selected = selectedTab == tab },
                )
            }
        }
        if (selectedTab == ReaderAuxiliaryTab.CONTENTS && sheetState.currentValue != SheetValue.Expanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { scope.launch { sheetState.expand() } },
                    modifier = Modifier.testTag("reader-expand-full-contents"),
                ) {
                    Text(stringResource(R.string.reader_expand_full_contents, chapters.size))
                }
            }
        }
        when (selectedTab) {
            ReaderAuxiliaryTab.CONTENTS -> ReaderChapterList(
                chapters = chapters,
                currentChapterId = currentChapterId,
                bookmarked = bookmarks,
                onSelectChapter = onSelectChapter,
                onToggleBookmark = onToggleBookmark,
                centerCurrentChapter = true,
            )
            ReaderAuxiliaryTab.BOOKMARKS -> ReaderChapterList(
                chapters = chapters.filter { it.chapterId in bookmarks },
                currentChapterId = currentChapterId,
                bookmarked = bookmarks,
                onSelectChapter = onSelectChapter,
                onToggleBookmark = onToggleBookmark,
                emptyLabel = stringResource(R.string.reader_no_bookmarks),
            )
            ReaderAuxiliaryTab.SEARCH -> ReaderSearchTab(chapters, currentChapterId, onSelectChapter)
        }
    }
}

@Composable
private fun ReaderChapterList(
    chapters: List<SourceChapter>,
    currentChapterId: String,
    bookmarked: Set<String>,
    onSelectChapter: (SourceChapter) -> Unit,
    onToggleBookmark: (String) -> Unit,
    emptyLabel: String? = null,
    centerCurrentChapter: Boolean = false,
) {
    if (chapters.isEmpty() && emptyLabel != null) {
        Box(Modifier.fillMaxWidth().padding(TsuyomiSpacing.Xl), contentAlignment = Alignment.Center) {
            Text(emptyLabel, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(chapters, currentChapterId, centerCurrentChapter) {
        if (!centerCurrentChapter) return@LaunchedEffect
        val currentIndex = chapters.indexOfFirst { it.chapterId == currentChapterId }
        if (currentIndex < 0) return@LaunchedEffect
        listState.scrollToItem(currentIndex)
        withFrameNanos { }
        val currentItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            ?: return@LaunchedEffect
        val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
        listState.scrollBy((currentItem.offset + currentItem.size / 2 - viewportCenter).toFloat())
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
    ) {
        items(chapters, key = SourceChapter::chapterId) { chapter ->
            val current = chapter.chapterId == currentChapterId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onSelectChapter(chapter) }
                    .semantics {
                        selected = current
                        stateDescription = if (current) "当前章节" else "可打开章节"
                    }
                    .padding(horizontal = TsuyomiSpacing.Lg, vertical = TsuyomiSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    chapter.title,
                    modifier = Modifier.weight(1f),
                    style = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = { onToggleBookmark(chapter.chapterId) }) {
                    Text(stringResource(if (chapter.chapterId in bookmarked) R.string.reader_unbookmark else R.string.reader_bookmark))
                }
            }
        }
    }
}

@Composable
private fun ReaderSearchTab(
    chapters: List<SourceChapter>,
    currentChapterId: String,
    onSelectChapter: (SourceChapter) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val results = remember(submitted, chapters) {
        if (submitted.isBlank()) emptyList() else chapters.filter { it.title.contains(submitted, ignoreCase = true) }
    }
    Column(Modifier.fillMaxWidth().padding(TsuyomiSpacing.Lg)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(100) },
            label = { Text(stringResource(R.string.reader_search_chapters)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                submitted = draft.trim()
                focusManager.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (submitted.isNotBlank()) {
            ReaderChapterList(
                chapters = results,
                currentChapterId = currentChapterId,
                bookmarked = emptySet(),
                onSelectChapter = onSelectChapter,
                onToggleBookmark = {},
                emptyLabel = stringResource(R.string.reader_no_search_results),
            )
        }
    }
}
