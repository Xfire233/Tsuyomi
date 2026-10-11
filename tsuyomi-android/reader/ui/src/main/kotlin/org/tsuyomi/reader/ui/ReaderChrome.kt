/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
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
import org.tsuyomi.core.ui.components.PaginationBar
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.display.displayGrayscaleLayer
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.locator.bookmarkPositionKey
import org.tsuyomi.shared.sourcecontract.ReaderNavigationTarget

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
    eInk: Boolean,
    canPreviousPage: Boolean,
    canNextPage: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    continuousSeek: Boolean,
    seekPreview: Int?,
    onSeekPreview: (Int) -> Unit,
    onSeekCommit: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onOpenContents: () -> Unit,
    onOpenSettings: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
    onSeekCancel: () -> Unit = {},
    singlePosition: Boolean = !continuousSeek && position.selectablePageCount <= 1,
) {
    val compactHeightPx = with(LocalDensity.current) { 600.dp.roundToPx() }
    val compact = LocalWindowInfo.current.containerSize.height < compactHeightPx
    val preview = seekPreview ?: chapterProgress
    val previewPosition = when {
        eInk -> ReaderPosition.fromPageIndex(preview, position.pageCount, position.pageStep)
        continuousSeek -> ReaderPosition.fromProgress(preview, position.pageCount, position.pageStep)
        else -> ReaderPosition.fromSeekProgress(preview, position.pageCount, position.pageStep)
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
            if (eInk) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TsuyomiButton(
                        text = stringResource(R.string.reader_previous_page),
                        onClick = onPreviousPage,
                        modifier = Modifier.weight(1f).testTag("reader-page-previous"),
                        style = TsuyomiButtonStyle.SECONDARY,
                        enabled = canPreviousPage,
                    )
                    Text(
                        stringResource(R.string.reader_page_progress, previewPosition.page, position.pageCount, previewPosition.progress),
                        modifier = Modifier.weight(1.2f).testTag("reader-page-progress"),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                    TsuyomiButton(
                        text = stringResource(R.string.reader_next_page),
                        onClick = onNextPage,
                        modifier = Modifier.weight(1f).testTag("reader-page-next"),
                        style = TsuyomiButtonStyle.SECONDARY,
                        enabled = canNextPage,
                    )
                }
            } else {
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
            }
            if (singlePosition) {
                Text(
                    stringResource(R.string.reader_page_count, position.page, position.pageCount),
                    modifier = Modifier.fillMaxWidth().testTag("reader-chapter-position").semantics {
                        stateDescription = "本章第 ${position.page} 页，共 ${position.pageCount} 页"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
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
                        value = if (eInk) ((previewPosition.page - 1) / position.pageStep).toFloat() else preview.toFloat(),
                        onValueChange = { value ->
                            val target = if (eInk) {
                                value.roundToInt().coerceIn(0, selectableStops - 1) * position.pageStep
                            } else {
                                value.roundToInt().coerceIn(0, 100)
                            }
                            interactionProgress = target
                            onSeekPreview(target)
                        },
                        label = stringResource(R.string.reader_chapter_percent, if (eInk) previewPosition.progress else preview),
                        onValueChangeFinished = { onSeekCommit(interactionProgress) },
                        onValueChangeCancelled = onSeekCancel,
                        valueRange = if (eInk) 0f..(selectableStops - 1).toFloat() else 0f..100f,
                        steps = sliderSteps,
                        enabled = true,
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
            }
            @Composable
            fun chapterActions(actionModifier: Modifier) {
                ReaderBottomAction(
                    icon = TsuyomiIcons.Previous,
                    label = stringResource(R.string.reader_previous_chapter),
                    compact = compact,
                    enabled = chapterIndex > 0,
                    eInk = eInk,
                    onClick = onPreviousChapter,
                    modifier = actionModifier,
                )
                ReaderBottomAction(
                    icon = TsuyomiIcons.Chapters,
                    label = stringResource(R.string.reader_contents),
                    compact = compact,
                    eInk = eInk,
                    onClick = onOpenContents,
                    modifier = actionModifier,
                )
                ReaderBottomAction(
                    icon = TsuyomiIcons.Settings,
                    label = stringResource(R.string.reader_settings),
                    compact = compact,
                    eInk = eInk,
                    onClick = onOpenSettings,
                    modifier = actionModifier,
                )
                ReaderBottomAction(
                    icon = TsuyomiIcons.Next,
                    label = stringResource(R.string.reader_next_chapter),
                    compact = compact,
                    enabled = chapterIndex >= 0 && chapterIndex < chapterCount - 1,
                    eInk = eInk,
                    onClick = onNextChapter,
                    modifier = actionModifier,
                )
            }
            if (eInk) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Lg, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                ) {
                    chapterActions(Modifier)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    chapterActions(Modifier.weight(1f))
                }
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
    eInk: Boolean = false,
) {
    val availabilityDescription = stringResource(
        if (enabled) R.string.reader_action_available else R.string.reader_action_unavailable,
    )
    if (eInk) {
        TsuyomiButton(
            text = label,
            onClick = onClick,
            modifier = modifier
                .semantics { stateDescription = availabilityDescription }
                .testTag("reader-bottom-action-$label"),
            style = TsuyomiButtonStyle.SECONDARY,
            enabled = enabled,
        )
        return
    }
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
    val contentsPagerState: TsuyomiMeasuredPagerState,
    val bookmarksPagerState: TsuyomiMeasuredPagerState,
    val searchResultsPagerState: TsuyomiMeasuredPagerState,
    internal val modePagerState: TsuyomiMeasuredPagerState,
    private val searchDraftState: MutableState<TextFieldValue>,
    private val submittedSearchState: MutableState<String>,
    private val centeredDirectoryTargetState: MutableState<String?>,
) {
    var selectedTab: ReaderAuxiliaryTab
        get() = selectedTabState.value
        private set(value) {
            selectedTabState.value = value
        }

    var searchDraft: String
        get() = searchDraftState.value.text
        set(value) {
            searchDraftState.value = TextFieldValue(value, TextRange(value.length))
        }

    var searchDraftValue: TextFieldValue
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
    bookIdentity: BookIdentity?,
    initialTab: ReaderAuxiliaryTab = ReaderAuxiliaryTab.CONTENTS,
): ReaderAuxiliarySheetState {
    val identityKey = bookIdentity?.storageKey ?: "reader-no-identity"
    val selectedTab = rememberSaveable(identityKey) { mutableStateOf(initialTab) }
    val contentsList = rememberSaveable(identityKey, saver = LazyListState.Saver) { LazyListState() }
    val bookmarksList = rememberSaveable(identityKey, saver = LazyListState.Saver) { LazyListState() }
    val searchResultsList = rememberSaveable(identityKey, saver = LazyListState.Saver) { LazyListState() }
    val contentsPager = key(identityKey) { rememberTsuyomiMeasuredPagerState() }
    val bookmarksPager = key(identityKey) { rememberTsuyomiMeasuredPagerState() }
    val searchResultsPager = key(identityKey) { rememberTsuyomiMeasuredPagerState() }
    val modePager = key(identityKey) { rememberTsuyomiMeasuredPagerState() }
    val searchDraft = rememberSaveable(identityKey, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val submittedSearch = rememberSaveable(identityKey) { mutableStateOf("") }
    val centeredDirectoryTarget = rememberSaveable(identityKey) { mutableStateOf<String?>(null) }
    return remember(identityKey) {
        ReaderAuxiliarySheetState(
            selectedTabState = selectedTab,
            contentsListState = contentsList,
            bookmarksListState = bookmarksList,
            searchResultsListState = searchResultsList,
            contentsPagerState = contentsPager,
            bookmarksPagerState = bookmarksPager,
            searchResultsPagerState = searchResultsPager,
            modePagerState = modePager,
            searchDraftState = searchDraft,
            submittedSearchState = submittedSearch,
            centeredDirectoryTargetState = centeredDirectoryTarget,
        )
    }
}

private fun readerNavigationKey(contentId: String, fragment: String?): String =
    "$contentId\u001f${fragment.orEmpty()}"

private val ReaderNavigationTarget.navigationKey: String
    get() = readerNavigationKey(contentId, fragment)

private val ReaderNavigationTarget.navigationTag: String
    get() = if (fragment == null) contentId else "$contentId#$fragment"

@Composable
internal fun ReaderAuxiliarySheet(
    state: ReaderAuxiliarySheetState,
    navigationTargets: List<ReaderNavigationTarget>,
    currentContentId: String,
    bookmarks: List<ReaderLocator>,
    onDismiss: () -> Unit,
    onSelectContent: (ReaderNavigationTarget) -> Unit,
    onSelectBookmark: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
    currentFragment: String? = null,
) {
    val currentTargetKey = readerNavigationKey(currentContentId, currentFragment)
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        ReaderEInkAuxiliaryDialog(
            state = state,
            navigationTargets = navigationTargets,
            currentTargetKey = currentTargetKey,
            bookmarks = bookmarks,
            onDismiss = onDismiss,
            onSelectContent = onSelectContent,
            onSelectBookmark = onSelectBookmark,
            onRemoveBookmark = onRemoveBookmark,
        )
        return
    }
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
                    text = stringResource(R.string.reader_expand_full_contents, navigationTargets.size),
                    onClick = { scope.launch { controller.expand() } },
                    style = TsuyomiButtonStyle.TEXT,
                    modifier = Modifier.testTag("reader-expand-full-contents"),
                )
            }
        }
        when (state.selectedTab) {
            ReaderAuxiliaryTab.CONTENTS -> ReaderContentList(
                navigationTargets = navigationTargets,
                currentTargetKey = currentTargetKey,
                onSelectContent = onSelectContent,
                centerCurrentContent = true,
                listTag = "reader-directory-list",
                listState = state.contentsListState,
                centeredTarget = state,
                showSectionBoundaries = true,
            )
            ReaderAuxiliaryTab.BOOKMARKS -> ReaderBookmarkList(
                navigationTargets = navigationTargets,
                bookmarks = bookmarks,
                onSelectBookmark = onSelectBookmark,
                onRemoveBookmark = onRemoveBookmark,
                listState = state.bookmarksListState,
            )
            ReaderAuxiliaryTab.SEARCH -> ReaderSearchTab(
                navigationTargets = navigationTargets,
                currentTargetKey = currentTargetKey,
                onSelectContent = onSelectContent,
                state = state,
            )
        }
    }
}


private data class ReaderEInkPageItem(
    val key: String,
    val label: String,
    val fullLabel: String,
    val target: ReaderNavigationTarget? = null,
    val bookmark: ReaderLocator? = null,
    val selected: Boolean? = null,
    val heading: Boolean = false,
    val removeBookmark: Boolean = false,
    val testTag: String? = null,
)

private data class ReaderEInkTextLine(val start: Int, val text: String)

private fun readerEInkTextLines(
    text: String,
    widthPx: Int,
    style: TextStyle,
    textMeasurer: TextMeasurer,
): List<ReaderEInkTextLine> {
    if (text.isEmpty()) return listOf(ReaderEInkTextLine(0, ""))
    val layout = textMeasurer.measure(
        text = text,
        style = style,
        constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)),
        softWrap = true,
    )
    if (layout.lineCount == 0) return listOf(ReaderEInkTextLine(0, text))
    return (0 until layout.lineCount).map { lineIndex ->
        val start = layout.getLineStart(lineIndex)
        val end = layout.getLineEnd(lineIndex, visibleEnd = false)
        ReaderEInkTextLine(start, text.substring(start, end))
    }
}

private fun addReaderEInkTextItems(
    destination: MutableList<ReaderEInkPageItem>,
    keyPrefix: String,
    text: String,
    fullLabel: String,
    widthPx: Int,
    style: TextStyle,
    textMeasurer: TextMeasurer,
    target: ReaderNavigationTarget? = null,
    bookmark: ReaderLocator? = null,
    selected: Boolean? = null,
    heading: Boolean = false,
    firstTag: String? = null,
    tagPrefix: String,
) {
    readerEInkTextLines(text, widthPx, style, textMeasurer).forEachIndexed { lineIndex, line ->
        destination += ReaderEInkPageItem(
            key = "$keyPrefix:${line.start}",
            label = line.text,
            fullLabel = fullLabel,
            target = target,
            bookmark = bookmark,
            selected = selected,
            heading = heading,
            testTag = if (lineIndex == 0) firstTag else "$tagPrefix-${line.start}",
        )
    }
}

@Composable
private fun ReaderEInkAuxiliaryDialog(
    state: ReaderAuxiliarySheetState,
    navigationTargets: List<ReaderNavigationTarget>,
    currentTargetKey: String,
    bookmarks: List<ReaderLocator>,
    onDismiss: () -> Unit,
    onSelectContent: (ReaderNavigationTarget) -> Unit,
    onSelectBookmark: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.selectedTab) {
        if (state.selectedTab != ReaderAuxiliaryTab.SEARCH) focusManager.clearFocus()
    }
    val navigationWidth = readerNavigationWidth(ReaderAuxiliaryTab.entries.map { it.label })
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            Modifier.fillMaxSize().displayGrayscaleLayer().testTag("reader-auxiliary-sheet"),
        ) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
            ) {
                TsuyomiTopBar(title = state.selectedTab.label, onNavigateUp = onDismiss)
                Row(
                    Modifier.fillMaxWidth().weight(1f)
                        .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Md),
                ) {
                    TsuyomiMeasuredPager(
                        itemCount = ReaderAuxiliaryTab.entries.size,
                        itemKey = { ReaderAuxiliaryTab.entries[it].name },
                        state = state.modePagerState,
                        modifier = Modifier.width(navigationWidth).fillMaxHeight()
                            .testTag("reader-eink-auxiliary-navigation"),
                        verticalSpacing = TsuyomiSpacing.Sm,
                        itemContent = { index ->
                            val tab = ReaderAuxiliaryTab.entries[index]
                            val selected = tab == state.selectedTab
                            TsuyomiButton(
                                text = tab.label,
                                onClick = { state.select(tab) },
                                style = if (selected) TsuyomiButtonStyle.PRIMARY else TsuyomiButtonStyle.SECONDARY,
                                contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Sm),
                                modifier = Modifier.fillMaxWidth()
                                    .semantics { this.selected = selected }
                                    .testTag("reader-eink-mode-${tab.name.lowercase()}"),
                            )
                        },
                    )
                    Column(Modifier.weight(1f).fillMaxHeight().testTag("reader-eink-auxiliary-content")) {
                        when (state.selectedTab) {
                            ReaderAuxiliaryTab.CONTENTS -> ReaderEInkDirectoryPager(
                                navigationTargets = navigationTargets,
                                currentTargetKey = currentTargetKey,
                                state = state,
                                onSelectContent = onSelectContent,
                            )
                            ReaderAuxiliaryTab.BOOKMARKS -> ReaderEInkBookmarksPager(
                                navigationTargets = navigationTargets,
                                bookmarks = bookmarks,
                                state = state,
                                onSelectBookmark = onSelectBookmark,
                                onRemoveBookmark = onRemoveBookmark,
                            )
                            ReaderAuxiliaryTab.SEARCH -> ReaderEInkSearchPane(
                                navigationTargets = navigationTargets,
                                currentTargetKey = currentTargetKey,
                                state = state,
                                onSelectContent = onSelectContent,
                            )
                        }
                    }
                }
            }
        }
        BackHandler(onBack = onDismiss)
    }
}

@Composable
private fun ColumnScope.ReaderEInkDirectoryPager(
    navigationTargets: List<ReaderNavigationTarget>,
    currentTargetKey: String,
    state: ReaderAuxiliarySheetState,
    onSelectContent: (ReaderNavigationTarget) -> Unit,
) {
    if (navigationTargets.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.reader_no_contents), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val ungroupedSection = stringResource(R.string.reader_ungrouped_volume)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val buttonTextInsetPx = with(density) { (TsuyomiSpacing.Lg * 2 + 4.dp).roundToPx() }
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).testTag("reader-directory-pager")) {
        val textWidthPx = with(density) { maxWidth.roundToPx() } - buttonTextInsetPx
        val buttonStyle = MaterialTheme.typography.labelLarge
        val headingStyle = MaterialTheme.typography.titleSmall
        val items = remember(navigationTargets, currentTargetKey, ungroupedSection, textWidthPx, buttonStyle, headingStyle) {
            buildList {
                var previousSection: String? = null
                navigationTargets.forEachIndexed { index, target ->
                    val section = target.sectionTitle ?: ungroupedSection
                    if (section != previousSection) {
                        addReaderEInkTextItems(
                            destination = this,
                            keyPrefix = "section:$index:line",
                            text = section,
                            fullLabel = section,
                            widthPx = textWidthPx,
                            style = headingStyle,
                            textMeasurer = textMeasurer,
                            heading = true,
                            tagPrefix = "reader-eink-directory-section-$index",
                        )
                    }
                    addReaderEInkTextItems(
                        destination = this,
                        keyPrefix = "content:$index:${target.navigationKey}:line",
                        text = target.title,
                        fullLabel = target.title,
                        widthPx = textWidthPx,
                        style = buttonStyle,
                        textMeasurer = textMeasurer,
                        target = target,
                        selected = target.navigationKey == currentTargetKey,
                        firstTag = "reader-chapter-row-${target.navigationTag}",
                        tagPrefix = "reader-eink-chapter-${index}",
                    )
                    previousSection = section
                }
            }
        }
        val centerKey = remember(navigationTargets, currentTargetKey) {
            "$currentTargetKey:${navigationTargets.joinToString(separator = "\u001f") { target ->
                "${target.navigationKey}\u001e${target.sectionTitle.orEmpty()}"
            }}"
        }
        val itemKeys = remember(items) { items.mapTo(HashSet(items.size)) { it.key } }
        SideEffect {
            val anchorKey = state.contentsPagerState.currentAnchorKey
            if (state.centeredDirectoryTarget != centerKey || anchorKey == null) {
                val firstCurrent = items.firstOrNull { it.target?.navigationKey == currentTargetKey }
                state.contentsPagerState.moveToAnchor((firstCurrent ?: items.first()).key)
                state.centeredDirectoryTarget = centerKey
            } else if (anchorKey !in itemKeys) {
                val target = navigationTargets.withIndex().firstOrNull {
                    anchorKey.startsWith("content:${it.index}:${it.value.navigationKey}:line:")
                }?.value
                val oldOffset = anchorKey.substringAfterLast(':').toIntOrNull() ?: 0
                val replacement = items.asSequence()
                    .filter { it.target == target }
                    .lastOrNull { (it.key.substringAfterLast(':').toIntOrNull() ?: 0) <= oldOffset }
                    ?: items.firstOrNull { it.target == target }
                    ?: items.first()
                state.contentsPagerState.moveToAnchor(replacement.key)
            }
        }
        ReaderEInkPager(
            items = items,
            state = state.contentsPagerState,
            onSelectContent = onSelectContent,
            onSelectBookmark = {},
            onRemoveBookmark = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ReaderEInkPager(
    items: List<ReaderEInkPageItem>,
    state: TsuyomiMeasuredPagerState,
    onSelectContent: (ReaderNavigationTarget) -> Unit,
    onSelectBookmark: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
    modifier: Modifier = Modifier,
) {
    TsuyomiMeasuredPager(
        itemCount = items.size,
        itemKey = { items[it].key },
        itemContent = { index ->
            ReaderEInkPageItemView(
                item = items[index],
                onSelectContent = onSelectContent,
                onSelectBookmark = onSelectBookmark,
                onRemoveBookmark = onRemoveBookmark,
            )
        },
        pageControls = { page, canGoPrevious, canGoNext, onPrevious, onNext, pageCount, onFirst, onLast ->
            PaginationBar(
                page = page,
                pageCount = pageCount,
                onPrevious = onPrevious,
                onNext = onNext,
                hasPreviousPage = canGoPrevious,
                hasNextPage = canGoNext,
                onFirst = onFirst,
                onLast = onLast,
                modifier = Modifier.testTag("reader-eink-pagination-controls"),
            )
        },
        modifier = modifier,
        state = state,
    )
}

@Composable
private fun ReaderEInkPageItemView(
    item: ReaderEInkPageItem,
    onSelectContent: (ReaderNavigationTarget) -> Unit,
    onSelectBookmark: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
) {
    if (item.heading) {
        Column(Modifier.fillMaxWidth().padding(vertical = TsuyomiSpacing.Xs)) {
            Text(
                item.label,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    val selected = item.selected == true
    val selectedDescription = stringResource(
        if (selected) R.string.reader_option_selected else R.string.reader_option_not_selected,
    )
    TsuyomiButton(
        text = item.label,
        onClick = {
            when {
                item.removeBookmark -> item.bookmark?.let(onRemoveBookmark)
                item.target != null -> onSelectContent(item.target)
                item.bookmark != null -> onSelectBookmark(item.bookmark)
            }
        },
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = item.fullLabel
            if (item.selected != null) {
                this.selected = selected
                stateDescription = selectedDescription
            }
        }.testTag(item.testTag ?: "reader-eink-list-item-${item.key}"),
        style = if (selected) TsuyomiButtonStyle.PRIMARY else TsuyomiButtonStyle.SECONDARY,
    )
}

@Composable
private fun ColumnScope.ReaderEInkBookmarksPager(
    navigationTargets: List<ReaderNavigationTarget>,
    bookmarks: List<ReaderLocator>,
    state: ReaderAuxiliarySheetState,
    onSelectBookmark: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.reader_no_bookmarks), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val bookmarkLabels = bookmarks.map { bookmark ->
        val chapterTitle = navigationTargets.firstOrNull { it.contentId == bookmark.document.contentId }?.title
            ?: bookmark.document.contentId
        Triple(bookmark, chapterTitle, bookmarkPositionLabel(bookmark))
    }
    val removeBookmarkLabel = stringResource(R.string.reader_remove_bookmark)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val buttonTextInsetPx = with(density) { (TsuyomiSpacing.Lg * 2 + 4.dp).roundToPx() }
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).testTag("reader-bookmark-pager")) {
        val textWidthPx = with(density) { maxWidth.roundToPx() } - buttonTextInsetPx
        val textStyle = MaterialTheme.typography.labelLarge
        val items = remember(bookmarkLabels, removeBookmarkLabel, textWidthPx, textStyle) {
            buildList {
                bookmarkLabels.forEach { (bookmark, chapterTitle, position) ->
                    val fullLabel = "$chapterTitle · $position"
                    val key = bookmark.bookmarkPositionKey()
                    addReaderEInkTextItems(
                        destination = this,
                        keyPrefix = "bookmark:$key:title:line",
                        text = chapterTitle,
                        fullLabel = fullLabel,
                        widthPx = textWidthPx,
                        style = textStyle,
                        textMeasurer = textMeasurer,
                        bookmark = bookmark,
                        firstTag = "reader-bookmark-row-$key",
                        tagPrefix = "reader-eink-bookmark-$key-title",
                    )
                    addReaderEInkTextItems(
                        destination = this,
                        keyPrefix = "bookmark:$key:position:line",
                        text = position,
                        fullLabel = fullLabel,
                        widthPx = textWidthPx,
                        style = textStyle,
                        textMeasurer = textMeasurer,
                        bookmark = bookmark,
                        tagPrefix = "reader-eink-bookmark-$key-position",
                    )
                    add(
                        ReaderEInkPageItem(
                            key = "bookmark:$key:remove",
                            label = removeBookmarkLabel,
                            fullLabel = "$removeBookmarkLabel · $chapterTitle",
                            bookmark = bookmark,
                            removeBookmark = true,
                            testTag = "reader-bookmark-remove-$key",
                        ),
                    )
                }
            }
        }
        val itemKeys = remember(items) { items.mapTo(HashSet(items.size)) { it.key } }
        SideEffect {
            val anchorKey = state.bookmarksPagerState.currentAnchorKey
            if (anchorKey != null && anchorKey !in itemKeys) {
                val bookmark = bookmarks.firstOrNull { anchorKey.startsWith("bookmark:${it.bookmarkPositionKey()}:") }
                val oldOffset = anchorKey.substringAfterLast(':').toIntOrNull() ?: 0
                val replacement = items.asSequence()
                    .filter { it.bookmark == bookmark }
                    .lastOrNull { (it.key.substringAfterLast(':').toIntOrNull() ?: 0) <= oldOffset }
                    ?: items.firstOrNull { it.bookmark == bookmark }
                    ?: items.first()
                state.bookmarksPagerState.moveToAnchor(replacement.key)
            }
        }
        ReaderEInkPager(
            items = items,
            state = state.bookmarksPagerState,
            onSelectContent = {},
            onSelectBookmark = onSelectBookmark,
            onRemoveBookmark = onRemoveBookmark,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ColumnScope.ReaderEInkSearchPane(
    navigationTargets: List<ReaderNavigationTarget>,
    currentTargetKey: String,
    state: ReaderAuxiliarySheetState,
    onSelectContent: (ReaderNavigationTarget) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val results = remember(state.submittedSearch, navigationTargets) {
        if (state.submittedSearch.isBlank()) emptyList()
        else navigationTargets.filter { it.title.contains(state.submittedSearch, ignoreCase = true) }
    }
    fun submitSearch() {
        state.searchResultsPagerState.reset()
        state.submittedSearch = state.searchDraft.trim()
        focusManager.clearFocus()
    }
    Column(
        Modifier.fillMaxWidth().weight(1f).padding(horizontal = TsuyomiSpacing.Md),
    ) {
        val fieldLabel = stringResource(R.string.reader_search_chapters)
        val actionLabel = stringResource(R.string.reader_search)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val fieldStyle = MaterialTheme.typography.bodyLarge
        val actionStyle = MaterialTheme.typography.labelLarge
        val rowWidth = remember(fieldLabel, actionLabel, fieldStyle, actionStyle, density) {
            measurer.measure(fieldLabel, style = fieldStyle).size.width +
                measurer.measure(actionLabel, style = actionStyle).size.width +
                with(density) { (56.dp + TsuyomiSpacing.Lg * 2 + TsuyomiSpacing.Xs).roundToPx() }
        }
        @Composable
        fun searchField(modifier: Modifier) {
            TsuyomiTextField(
                value = state.searchDraftValue,
                onValueChange = { value ->
                    state.searchDraftValue = if (value.text.length <= 100) value else {
                        TextFieldValue(value.text.take(100), TextRange(100))
                    }
                },
                label = fieldLabel,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                modifier = modifier.testTag("reader-search-input"),
            )
        }
        @Composable
        fun searchAction() {
            TsuyomiButton(
                text = actionLabel,
                onClick = ::submitSearch,
                style = TsuyomiButtonStyle.PRIMARY,
                modifier = Modifier.testTag("reader-eink-search-submit"),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = TsuyomiSpacing.Sm)) {
            if (constraints.maxWidth >= rowWidth) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    searchField(Modifier.weight(1f))
                    searchAction()
                }
            } else {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                ) {
                    searchField(Modifier.fillMaxWidth())
                    searchAction()
                }
            }
        }
        when {
            state.submittedSearch.isBlank() -> Unit
            results.isEmpty() -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.reader_no_search_results), style = MaterialTheme.typography.bodyLarge)
            }
            else -> {
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val buttonTextInsetPx = with(density) { (TsuyomiSpacing.Lg * 2 + 4.dp).roundToPx() }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).testTag("reader-search-results-pager")) {
                    val textWidthPx = with(density) { maxWidth.roundToPx() } - buttonTextInsetPx
                    val textStyle = MaterialTheme.typography.labelLarge
                    val items = remember(results, currentTargetKey, textWidthPx, textStyle) {
                        buildList {
                            results.forEachIndexed { index, target ->
                                addReaderEInkTextItems(
                                    destination = this,
                                    keyPrefix = "content:$index:${target.navigationKey}:line",
                                    text = target.title,
                                    fullLabel = target.title,
                                    widthPx = textWidthPx,
                                    style = textStyle,
                                    textMeasurer = textMeasurer,
                                    target = target,
                                    selected = target.navigationKey == currentTargetKey,
                                    firstTag = "reader-search-result-${target.navigationTag}",
                                    tagPrefix = "reader-eink-search-result-$index",
                                )
                            }
                        }
                    }
                    val itemKeys = remember(items) { items.mapTo(HashSet(items.size)) { it.key } }
                    SideEffect {
                        val anchorKey = state.searchResultsPagerState.currentAnchorKey
                        if (anchorKey != null && anchorKey !in itemKeys) {
                            val target = results.withIndex().firstOrNull {
                                anchorKey.startsWith("content:${it.index}:${it.value.navigationKey}:line:")
                            }?.value
                            val oldOffset = anchorKey.substringAfterLast(':').toIntOrNull() ?: 0
                            val replacement = items.asSequence()
                                .filter { it.target == target }
                                .lastOrNull { (it.key.substringAfterLast(':').toIntOrNull() ?: 0) <= oldOffset }
                                ?: items.firstOrNull { it.target == target }
                                ?: items.first()
                            state.searchResultsPagerState.moveToAnchor(replacement.key)
                        }
                    }
                    ReaderEInkPager(
                        items = items,
                        state = state.searchResultsPagerState,
                        onSelectContent = onSelectContent,
                        onSelectBookmark = {},
                        onRemoveBookmark = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private const val ReaderAuxiliaryExpandedHeightFraction = 0.9f

private data class ReaderDirectoryItem(
    val key: String,
    val target: ReaderNavigationTarget? = null,
    val sectionTitle: String? = null,
)

@Composable
private fun ColumnScope.ReaderContentList(
    navigationTargets: List<ReaderNavigationTarget>,
    currentTargetKey: String,
    onSelectContent: (ReaderNavigationTarget) -> Unit,
    listState: LazyListState,
    emptyLabel: String? = null,
    centerCurrentContent: Boolean = false,
    centeredTarget: ReaderAuxiliarySheetState? = null,
    showSectionBoundaries: Boolean = false,
    listTag: String,
) {
    if (navigationTargets.isEmpty() && emptyLabel != null) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xl),
            contentAlignment = Alignment.Center,
        ) {
            Text(emptyLabel, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val ungroupedSection = stringResource(R.string.reader_ungrouped_volume)
    val directoryItems = remember(navigationTargets, showSectionBoundaries, ungroupedSection) {
        buildList {
            var previousSection: String? = null
            navigationTargets.forEachIndexed { index, target ->
                val section = target.sectionTitle ?: ungroupedSection
                if (showSectionBoundaries && section != previousSection) {
                    add(ReaderDirectoryItem(key = "section:$index:$section", sectionTitle = section))
                }
                add(ReaderDirectoryItem(key = "content:$index:${target.navigationKey}", target = target))
                previousSection = section
            }
        }
    }
    val currentIndex = directoryItems.indexOfFirst { it.target?.navigationKey == currentTargetKey }
    val centerKey = remember(navigationTargets, currentTargetKey, showSectionBoundaries) {
        "$currentTargetKey:${navigationTargets.joinToString(separator = "\u001f") { target ->
            "${target.navigationKey}\u001e${target.sectionTitle.orEmpty()}"
        }}"
    }
    val needsCentering = centerCurrentContent && currentIndex >= 0 && centeredTarget?.centeredDirectoryTarget != centerKey
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
            val target = item.target
            if (target == null) {
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
                            text = requireNotNull(item.sectionTitle),
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
                val current = target.navigationKey == currentTargetKey
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { onSelectContent(target) }
                        .semantics {
                            selected = current
                            stateDescription = if (current) "当前章节" else "可打开章节"
                        }
                        .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs)
                        .testTag("reader-chapter-row-${target.navigationTag}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        target.title,
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
    navigationTargets: List<ReaderNavigationTarget>,
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
            val chapterTitle = navigationTargets.firstOrNull { it.contentId == bookmark.document.contentId }?.title
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
    navigationTargets: List<ReaderNavigationTarget>,
    currentTargetKey: String,
    onSelectContent: (ReaderNavigationTarget) -> Unit,
    state: ReaderAuxiliarySheetState,
) {
    val focusManager = LocalFocusManager.current
    val results = remember(state.submittedSearch, navigationTargets) {
        if (state.submittedSearch.isBlank()) emptyList() else {
            navigationTargets.filter { it.title.contains(state.submittedSearch, ignoreCase = true) }
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
            ReaderContentList(
                navigationTargets = results,
                currentTargetKey = currentTargetKey,
                onSelectContent = onSelectContent,
                listState = state.searchResultsListState,
                emptyLabel = stringResource(R.string.reader_no_search_results),
                listTag = "reader-search-results",
            )
        }
    }
}
