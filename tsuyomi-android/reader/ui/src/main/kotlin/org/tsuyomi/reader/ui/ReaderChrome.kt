/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
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
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiModalSheet
import org.tsuyomi.core.ui.components.TsuyomiSlider
import org.tsuyomi.core.ui.components.TsuyomiTabOption
import org.tsuyomi.core.ui.components.TsuyomiTabRow
import org.tsuyomi.core.ui.components.TsuyomiTextField
import org.tsuyomi.core.ui.components.rememberTsuyomiModalSheetController
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.locator.bookmarkPositionKey
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
    continuous: Boolean,
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
            .testTag("reader-reading-info")
            .semantics(mergeDescendants = true) {
                stateDescription = if (continuous) {
                    "本章进度 $progress%"
                } else {
                    "本章进度 $progress%，第 ${displayPosition.page} / ${displayPosition.pageCount} 页"
                }
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
                    if (continuous) {
                        stringResource(R.string.reader_chapter_percent, progress)
                    } else {
                        stringResource(
                            R.string.reader_page_progress,
                            displayPosition.page,
                            displayPosition.pageCount,
                            progress,
                        )
                    },
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
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth(),
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
            if (!continuousSeek) {
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
            } else {
                Text(
                    stringResource(R.string.reader_chapter_percent, preview),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                TsuyomiSlider(
                    value = preview.toFloat(),
                    onValueChange = { value ->
                        val target = value.roundToInt().coerceIn(0, 100)
                        interactionProgress = target
                        onSeekPreview(target)
                    },
                    label = stringResource(R.string.reader_chapter_percent, preview),
                    onValueChangeFinished = { onSeekCommit(interactionProgress) },
                    valueRange = 0f..100f,
                    steps = sliderSteps,
                    enabled = continuousSeek || selectableStops > 1,
                    valueDescription = if (continuousSeek) {
                        "本章目标 $preview%"
                    } else {
                        "本章目标第 ${previewPosition.page} 页，共 ${previewPosition.pageCount} 页"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reader-chapter-progress-slider")
                        .semantics {
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
    val availabilityDescription = stringResource(
        if (enabled) R.string.reader_action_available else R.string.reader_action_unavailable,
    )
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = availabilityDescription
            }
            .testTag("reader-bottom-action-$label"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
        if (!compact) Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Stable
class ReaderAuxiliarySheetState internal constructor(
    private val selectedTabState: MutableState<ReaderAuxiliaryTab>,
    val contentsListState: LazyListState,
    val bookmarksListState: LazyListState,
    val searchResultsListState: LazyListState,
    private val searchDraftState: MutableState<String>,
    private val submittedSearchState: MutableState<String>,
    private val centeredDirectoryTargetState: MutableState<String?>,
) {
    var selectedTab: ReaderAuxiliaryTab
        get() = selectedTabState.value
        private set(value) {
            selectedTabState.value = value
        }

    var searchDraft: String
        get() = searchDraftState.value
        set(value) {
            searchDraftState.value = value
        }

    var submittedSearch: String
        get() = submittedSearchState.value
        set(value) {
            submittedSearchState.value = value
        }

    var centeredDirectoryTarget: String?
        get() = centeredDirectoryTargetState.value
        set(value) {
            centeredDirectoryTargetState.value = value
        }

    fun select(tab: ReaderAuxiliaryTab) {
        selectedTab = tab
    }
}

@Composable
fun rememberReaderAuxiliarySheetState(
    sourceId: String,
    remoteBookId: String,
    initialTab: ReaderAuxiliaryTab = ReaderAuxiliaryTab.CONTENTS,
): ReaderAuxiliarySheetState {
    val selectedTab = rememberSaveable(sourceId, remoteBookId) { mutableStateOf(initialTab) }
    val contentsList = rememberSaveable(sourceId, remoteBookId, saver = LazyListState.Saver) { LazyListState() }
    val bookmarksList = rememberSaveable(sourceId, remoteBookId, saver = LazyListState.Saver) { LazyListState() }
    val searchResultsList = rememberSaveable(sourceId, remoteBookId, saver = LazyListState.Saver) { LazyListState() }
    val searchDraft = rememberSaveable(sourceId, remoteBookId) { mutableStateOf("") }
    val submittedSearch = rememberSaveable(sourceId, remoteBookId) { mutableStateOf("") }
    val centeredDirectoryTarget = rememberSaveable(sourceId, remoteBookId) { mutableStateOf<String?>(null) }
    return remember(sourceId, remoteBookId) {
        ReaderAuxiliarySheetState(
            selectedTabState = selectedTab,
            contentsListState = contentsList,
            bookmarksListState = bookmarksList,
            searchResultsListState = searchResultsList,
            searchDraftState = searchDraft,
            submittedSearchState = submittedSearch,
            centeredDirectoryTargetState = centeredDirectoryTarget,
        )
    }
}

@Composable
internal fun ReaderAuxiliarySheet(
    state: ReaderAuxiliarySheetState,
    chapters: List<SourceChapter>,
    currentChapterId: String,
    bookmarks: List<ReaderLocator>,
    onDismiss: () -> Unit,
    onSelectChapter: (SourceChapter) -> Unit,
    onSelectBookmark: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
) {
    val controller = rememberTsuyomiModalSheetController(allowPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    TsuyomiModalSheet(
        onDismissRequest = onDismiss,
        controller = controller,
        modifier = Modifier
            .fillMaxHeight(ReaderAuxiliaryExpandedHeightFraction)
            .testTag("reader-auxiliary-sheet"),
    ) {
        BackHandler {
            scope.launch {
                controller.hide()
                latestOnDismiss()
            }
        }
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        LaunchedEffect(imeVisible, state.selectedTab) {
            if (imeVisible && state.selectedTab == ReaderAuxiliaryTab.SEARCH) controller.expand()
        }
        TsuyomiTabRow(
            options = ReaderAuxiliaryTab.entries.map { TsuyomiTabOption(it.name, it.label) },
            selectedKey = state.selectedTab.name,
            onSelect = { state.select(ReaderAuxiliaryTab.valueOf(it)) },
        )
        if (state.selectedTab == ReaderAuxiliaryTab.CONTENTS && !controller.isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md),
                horizontalArrangement = Arrangement.End,
            ) {
                TsuyomiButton(
                    text = stringResource(R.string.reader_expand_full_contents, chapters.size),
                    onClick = { scope.launch { controller.expand() } },
                    style = TsuyomiButtonStyle.TEXT,
                    modifier = Modifier.testTag("reader-expand-full-contents"),
                )
            }
        }
        when (state.selectedTab) {
            ReaderAuxiliaryTab.CONTENTS -> ReaderChapterList(
                chapters = chapters,
                currentChapterId = currentChapterId,
                onSelectChapter = onSelectChapter,
                centerCurrentChapter = true,
                listTag = "reader-directory-list",
                listState = state.contentsListState,
                centeredTarget = state,
                showVolumeBoundaries = true,
            )
            ReaderAuxiliaryTab.BOOKMARKS -> ReaderBookmarkList(
                chapters = chapters,
                bookmarks = bookmarks,
                onSelectBookmark = onSelectBookmark,
                onRemoveBookmark = onRemoveBookmark,
                listState = state.bookmarksListState,
            )
            ReaderAuxiliaryTab.SEARCH -> ReaderSearchTab(
                chapters = chapters,
                currentChapterId = currentChapterId,
                onSelectChapter = onSelectChapter,
                state = state,
            )
        }
    }
}


private const val ReaderAuxiliaryExpandedHeightFraction = 0.9f

private data class ReaderDirectoryItem(
    val key: String,
    val chapter: SourceChapter? = null,
    val volumeTitle: String? = null,
)

@Composable
private fun ColumnScope.ReaderChapterList(
    chapters: List<SourceChapter>,
    currentChapterId: String,
    onSelectChapter: (SourceChapter) -> Unit,
    listState: LazyListState,
    emptyLabel: String? = null,
    centerCurrentChapter: Boolean = false,
    centeredTarget: ReaderAuxiliarySheetState? = null,
    showVolumeBoundaries: Boolean = false,
    listTag: String,
) {
    if (chapters.isEmpty() && emptyLabel != null) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xl),
            contentAlignment = Alignment.Center,
        ) {
            Text(emptyLabel, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val ungroupedVolume = stringResource(R.string.reader_ungrouped_volume)
    val directoryItems = remember(chapters, showVolumeBoundaries, ungroupedVolume) {
        buildList {
            var previousVolume: String? = null
            chapters.forEachIndexed { index, chapter ->
                val volume = chapter.volumeTitle ?: ungroupedVolume
                if (showVolumeBoundaries && volume != previousVolume) {
                    add(ReaderDirectoryItem(key = "volume:$index:$volume", volumeTitle = volume))
                }
                add(ReaderDirectoryItem(key = "chapter:${chapter.chapterId}", chapter = chapter))
                previousVolume = volume
            }
        }
    }
    val currentIndex = directoryItems.indexOfFirst { it.chapter?.chapterId == currentChapterId }
    val centerKey = remember(chapters, currentChapterId, showVolumeBoundaries) {
        "$currentChapterId:${chapters.joinToString(separator = "\u001f") { chapter ->
            "${chapter.chapterId}\u001e${chapter.volumeTitle.orEmpty()}"
        }}"
    }
    val needsCentering = centerCurrentChapter && currentIndex >= 0 && centeredTarget?.centeredDirectoryTarget != centerKey
    var centeredForDisplay by remember(centerKey, needsCentering) { mutableStateOf(!needsCentering) }
    var listBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    var listLayoutHeight by remember { mutableIntStateOf(0) }
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val obscuredBottomPadding = with(LocalDensity.current) {
        listBoundsInWindow?.let { bounds ->
            val visibleHeight = (minOf(bounds.bottom, windowHeight) - maxOf(bounds.top, 0f)).coerceAtLeast(0f)
            (listLayoutHeight - visibleHeight).coerceAtLeast(0f).toDp()
        } ?: 0.dp
    }

    LaunchedEffect(
        centerKey,
        currentIndex,
        needsCentering,
        listBoundsInWindow,
        windowHeight,
    ) {
        val bounds = listBoundsInWindow ?: return@LaunchedEffect
        if (!needsCentering || windowHeight <= 0f) return@LaunchedEffect
        listState.scrollToItem(currentIndex)
        withFrameNanos { }
        val currentItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            ?: return@LaunchedEffect
        val visibleTop = maxOf(bounds.top, 0f)
        val visibleBottom = minOf(bounds.bottom, windowHeight)
        if (visibleBottom <= visibleTop) return@LaunchedEffect
        val itemCenterInWindow = bounds.top +
            currentItem.offset - listState.layoutInfo.viewportStartOffset + currentItem.size / 2f
        listState.scrollBy(itemCenterInWindow - (visibleTop + visibleBottom) / 2f)
        centeredTarget?.centeredDirectoryTarget = centerKey
        centeredForDisplay = true
    }
    LazyColumn(
        state = listState,
        // The partial sheet clips the full-height list; keep its final row above that clipped area.
        contentPadding = PaddingValues(bottom = obscuredBottomPadding),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .alpha(if (centeredForDisplay) 1f else 0f)
            .onGloballyPositioned {
                listBoundsInWindow = it.boundsInWindow()
                listLayoutHeight = it.size.height
            }
            .testTag(if (centeredForDisplay) listTag else "$listTag-positioning"),
    ) {
        itemsIndexed(directoryItems, key = { _, item -> item.key }) { _, item ->
            val chapter = item.chapter
            if (chapter == null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                        .testTag("reader-volume-boundary-${item.key}"),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 1.dp,
                ) {
                    Column {
                        Text(
                            text = requireNotNull(item.volumeTitle),
                            modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        HorizontalDivider(
                            thickness = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        )
                    }
                }
            } else {
                val current = chapter.chapterId == currentChapterId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { onSelectChapter(chapter) }
                        .semantics {
                            selected = current
                            stateDescription = if (current) "当前章节" else "可打开章节"
                        }
                        .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs)
                        .testTag("reader-chapter-row-${chapter.chapterId}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        chapter.title,
                        style = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ReaderBookmarkList(
    chapters: List<SourceChapter>,
    bookmarks: List<ReaderLocator>,
    onSelectBookmark: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
    listState: LazyListState,
) {
    if (bookmarks.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xl),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.reader_no_bookmarks), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val removeBookmarkLabel = stringResource(R.string.reader_remove_bookmark)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .testTag("reader-bookmark-list"),
    ) {
        items(bookmarks, key = ReaderLocator::bookmarkPositionKey) { bookmark ->
            val chapterTitle = chapters.firstOrNull { it.chapterId == bookmark.document.contentId }?.title
                ?: bookmark.document.contentId
            val position = bookmarkPositionLabel(bookmark)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onSelectBookmark(bookmark) }
                    .semantics { stateDescription = "可打开书签" }
                    .testTag("reader-bookmark-row-${bookmark.bookmarkPositionKey()}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = TsuyomiSpacing.Md,
                            end = TsuyomiSpacing.Xs,
                            top = TsuyomiSpacing.Xs,
                            bottom = TsuyomiSpacing.Xs,
                        ),
                ) {
                    Text(
                        chapterTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        position,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(role = Role.Button) { onRemoveBookmark(bookmark) }
                        .semantics { contentDescription = removeBookmarkLabel }
                        .testTag("reader-bookmark-remove-${bookmark.bookmarkPositionKey()}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        TsuyomiIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun bookmarkPositionLabel(bookmark: ReaderLocator): String {
    val blockId = bookmark.blockId
    val characterOffset = bookmark.characterOffset
    val chapterProgress = bookmark.chapterProgress
    return when {
        blockId == null && chapterProgress == 0.0 && bookmark.capturedAt == java.time.Instant.EPOCH ->
            stringResource(R.string.reader_bookmark_legacy_chapter_start)
        blockId != null && characterOffset != null ->
            stringResource(R.string.reader_bookmark_exact_position, blockId, characterOffset + 1)
        chapterProgress != null ->
            stringResource(R.string.reader_bookmark_degraded_progress, (chapterProgress * 100).roundToInt())
        else ->
            stringResource(R.string.reader_bookmark_degraded_position)
    }
}

@Composable
private fun ColumnScope.ReaderSearchTab(
    chapters: List<SourceChapter>,
    currentChapterId: String,
    onSelectChapter: (SourceChapter) -> Unit,
    state: ReaderAuxiliarySheetState,
) {
    val focusManager = LocalFocusManager.current
    val results = remember(state.submittedSearch, chapters) {
        if (state.submittedSearch.isBlank()) emptyList() else {
            chapters.filter { it.title.contains(state.submittedSearch, ignoreCase = true) }
        }
    }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .padding(
                horizontal = TsuyomiSpacing.Md,
                vertical = if (imeVisible) 0.dp else TsuyomiSpacing.Md,
            ),
    ) {
        TsuyomiTextField(
            value = state.searchDraft,
            onValueChange = { state.searchDraft = it.take(100) },
            label = stringResource(R.string.reader_search_chapters),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                state.submittedSearch = state.searchDraft.trim()
                focusManager.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth().testTag("reader-search-input"),
        )
        if (state.submittedSearch.isNotBlank()) {
            ReaderChapterList(
                chapters = results,
                currentChapterId = currentChapterId,
                onSelectChapter = onSelectChapter,
                listState = state.searchResultsListState,
                emptyLabel = stringResource(R.string.reader_no_search_results),
                listTag = "reader-search-results",
            )
        }
    }
}
