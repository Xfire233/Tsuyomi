/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.distinctUntilChanged
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.HostMediaImage
import kotlinx.coroutines.flow.drop
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.reader.engine.ReaderDocumentSession
import org.tsuyomi.reader.engine.ReaderPresentation
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceChapter

private enum class ReaderOverlay {
    AUXILIARY,
    SETTINGS,
}

@Composable
fun ReaderSurface(
    document: ReaderDocument,
    restoredLocator: ReaderLocator?,
    onLocatorChanged: (ReaderLocator, LocatorPrecision) -> Unit,
    chapters: List<SourceChapter>,
    currentChapterId: String,
    onSelectChapter: (SourceChapter) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    imageStates: Map<String, CoverUiState> = emptyMap(),
    onImageVisible: (ReaderBlock.Image) -> Unit = {},
    onRetryImage: (ReaderBlock.Image) -> Unit = {},
    preferences: PortableReaderPreferences = PortableReaderPreferences(
        flow = "scroll",
        fontScale = 1.0,
        lineHeight = 1.5,
        theme = "paper",
    ),
    onPreferencesChanged: (PortableReaderPreferences) -> Unit = {},
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        FrozenEInkReaderSurface(document, restoredLocator, onLocatorChanged, modifier, preferences)
        return
    }
    StandardAtlasReaderSurface(
        document = document,
        restoredLocator = restoredLocator,
        onLocatorChanged = onLocatorChanged,
        chapters = chapters,
        currentChapterId = currentChapterId,
        onSelectChapter = onSelectChapter,
        onNavigateUp = onNavigateUp,
        imageStates = imageStates,
        onImageVisible = onImageVisible,
        onRetryImage = onRetryImage,
        preferences = preferences,
        onPreferencesChanged = onPreferencesChanged,
        modifier = modifier,
    )
}

@Composable
private fun StandardAtlasReaderSurface(
    document: ReaderDocument,
    restoredLocator: ReaderLocator?,
    onLocatorChanged: (ReaderLocator, LocatorPrecision) -> Unit,
    chapters: List<SourceChapter>,
    currentChapterId: String,
    onSelectChapter: (SourceChapter) -> Unit,
    onNavigateUp: () -> Unit,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    preferences: PortableReaderPreferences,
    onPreferencesChanged: (PortableReaderPreferences) -> Unit,
    modifier: Modifier,
) {
    val initialFlow = when (preferences.flow) {
        "scroll" -> ReaderFlow.SCROLL
        "paged" -> ReaderFlow.PAGED
        else -> ReaderFlow.PAGED
    }
    var settings by rememberSaveable(
        document.remoteBookId,
        stateSaver = ReaderSettingsSaver,
    ) {
        mutableStateOf(
            ReaderSettingsUiState(
                fontSize = (18.0 * (preferences.fontScale ?: 1.0)).toFloat(),
                lineHeight = (preferences.lineHeight ?: 1.5).toFloat(),
                flow = initialFlow,
            ),
        )
    }
    val presentation = when (settings.flow) {
        ReaderFlow.SCROLL -> ReaderPresentation.SCROLL
        ReaderFlow.PAGED -> ReaderPresentation.PAGED
        ReaderFlow.DUAL -> ReaderPresentation.DUAL_PAGE
    }
    val session = remember(document.sourceId, document.remoteBookId, document.contentId, document.revision) {
        ReaderDocumentSession(document, restoredLocator, presentation)
    }
    var renderedBlockIndex by remember(session) { mutableIntStateOf(session.position.blockIndex) }
    var renderedCodePointOffset by remember(session) { mutableIntStateOf(session.position.characterOffset) }
    var viewportSize by remember(session) { mutableStateOf(IntSize.Zero) }
    val pageLayout = rememberReaderPageLayout(
        document = document,
        settings = settings,
        viewportSize = viewportSize,
        dual = settings.flow == ReaderFlow.DUAL,
    )
    val renderedPageIndex = pageLayout.pageIndexFor(renderedBlockIndex, renderedCodePointOffset)
    val pageStep = if (settings.flow == ReaderFlow.DUAL) 2 else 1
    val readerPosition = if (settings.flow == ReaderFlow.SCROLL) {
        ReaderPosition.fromPageIndex(renderedBlockIndex, document.blocks.size)
    } else {
        ReaderPosition.fromPageIndex(renderedPageIndex, pageLayout.pages.size.coerceAtLeast(1), pageStep)
    }
    var chromeVisible by rememberSaveable(document.contentId) { mutableStateOf(true) }
    var overlay by rememberSaveable(document.contentId) { mutableStateOf<ReaderOverlay?>(null) }
    var auxiliaryTab by rememberSaveable { mutableStateOf(ReaderAuxiliaryTab.CONTENTS) }
    var seekPreview by rememberSaveable(document.contentId) { mutableStateOf<Int?>(null) }
    var bookmarks by rememberSaveable(document.remoteBookId) { mutableStateOf(emptyList<String>()) }
    val bookmarkedIds = bookmarks.toSet()
    val currentChapterIndex = chapters.indexOfFirst { it.chapterId == currentChapterId }
    val readerFocusRequester = remember { FocusRequester() }
    val readerView = LocalView.current
    val activity = LocalActivity.current
    val latestOnLocatorChanged by rememberUpdatedState(onLocatorChanged)

    fun commitPosition(blockIndex: Int, codePointOffset: Int = 0) {
        val resolved = session.navigateToBlock(blockIndex.coerceIn(document.blocks.indices), codePointOffset)
        renderedBlockIndex = resolved.blockIndex
        renderedCodePointOffset = resolved.characterOffset
        latestOnLocatorChanged(session.capture(), resolved.precision)
    }

    fun commitPage(pageIndex: Int) {
        val page = pageLayout.pages.getOrNull(pageIndex) ?: return
        commitPosition(page.startBlockIndex, page.startCodePointOffset)
    }

    fun selectAdjacentChapter(direction: Int): Boolean {
        val target = chapters.getOrNull(currentChapterIndex + direction) ?: return false
        onSelectChapter(target)
        return true
    }

    fun turnRenderedPage(direction: Int): Boolean {
        if (settings.flow == ReaderFlow.SCROLL || seekPreview != null || pageLayout.pages.isEmpty()) return false
        val target = renderedPageIndex + if (direction < 0) -pageStep else pageStep
        if (target in pageLayout.pages.indices) {
            commitPage(target)
            return true
        }
        return selectAdjacentChapter(direction)
    }

    fun updateSettings(action: ReaderSettingsAction) {
        settings = when (action) {
            is ReaderSettingsAction.FontSize -> settings.copy(fontSize = action.value)
            is ReaderSettingsAction.LineHeight -> settings.copy(lineHeight = action.value)
            is ReaderSettingsAction.HorizontalMargin -> settings.copy(horizontalMargin = action.value)
            is ReaderSettingsAction.ParagraphSpacing -> settings.copy(paragraphSpacing = action.value)
            is ReaderSettingsAction.Flow -> settings.copy(flow = action.value)
            is ReaderSettingsAction.LockPortrait -> settings.copy(lockPortrait = action.value)
            is ReaderSettingsAction.ProgressVisible -> settings.copy(progressVisible = action.value)
            is ReaderSettingsAction.Immersive -> settings.copy(immersive = action.value)
            is ReaderSettingsAction.KeepAwake -> settings.copy(keepAwake = action.value)
        }
        onPreferencesChanged(
            preferences.copy(
                flow = if (settings.flow == ReaderFlow.SCROLL) "scroll" else "paged",
                fontScale = (settings.fontSize / 18f).toDouble(),
                lineHeight = settings.lineHeight.toDouble(),
            ),
        )
    }

    DisposableEffect(settings.keepAwake, readerView) {
        readerView.keepScreenOn = settings.keepAwake
        onDispose { readerView.keepScreenOn = false }
    }
    DisposableEffect(settings.lockPortrait, activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = if (settings.lockPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            if (activity != null && previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }
    DisposableEffect(settings.immersive, chromeVisible, overlay, activity, readerView) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, readerView) }
        if (settings.immersive && !chromeVisible && overlay == null) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(settings.flow) {
        session.switchPresentation(
            when (settings.flow) {
                ReaderFlow.SCROLL -> ReaderPresentation.SCROLL
                ReaderFlow.PAGED -> ReaderPresentation.PAGED
                ReaderFlow.DUAL -> ReaderPresentation.DUAL_PAGE
            },
        )
    }
    LaunchedEffect(settings.keepAwake, overlay) {
        if (overlay == null) readerFocusRequester.requestFocus()
    }

    BackHandler(enabled = overlay != null) { overlay = null }
    BackHandler(enabled = seekPreview != null) { seekPreview = null }
    BackHandler(enabled = overlay == null && seekPreview == null && chromeVisible) { chromeVisible = false }

    val chromeDuration = 140
    val topEnter: EnterTransition = fadeIn(tween(chromeDuration)) + slideInVertically(tween(chromeDuration)) { -it / 5 }
    val topExit: ExitTransition = fadeOut(tween(chromeDuration)) + slideOutVertically(tween(chromeDuration)) { -it / 5 }
    val bottomEnter: EnterTransition = fadeIn(tween(chromeDuration)) + slideInVertically(tween(chromeDuration)) { it / 5 }
    val bottomExit: ExitTransition = fadeOut(tween(chromeDuration)) + slideOutVertically(tween(chromeDuration)) { it / 5 }
    val previewPagePosition = seekPreview?.let {
        ReaderPosition.fromSeekProgress(it, pageLayout.pages.size.coerceAtLeast(1), pageStep)
    }
    val previewPageIndex = previewPagePosition?.page?.minus(1)
    val previewBlockIndex = seekPreview?.let { progressToIndex(it, document.blocks.size) }
    val displayBlockIndex = if (settings.flow == ReaderFlow.SCROLL) previewBlockIndex ?: renderedBlockIndex else renderedBlockIndex
    val displayPageIndex = if (settings.flow == ReaderFlow.SCROLL) 0 else previewPageIndex ?: renderedPageIndex
    val displayPosition = when {
        seekPreview == null -> readerPosition
        settings.flow == ReaderFlow.SCROLL -> ReaderPosition.fromPageIndex(displayBlockIndex, document.blocks.size)
        else -> previewPagePosition ?: readerPosition
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(readerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (overlay != null || event.type != KeyEventType.KeyUp) false else {
                    when (event.key) {
                        Key.VolumeUp, Key.DirectionLeft -> turnRenderedPage(-1)
                        Key.VolumeDown, Key.DirectionRight -> turnRenderedPage(1)
                        else -> false
                    }
                }
            },
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewportSize = it }
                            .testTag("reader-content-surface")
                            .readerTapZones(
                                onPrevious = { turnRenderedPage(-1) },
                                onCenter = { chromeVisible = !chromeVisible },
                                onNext = { turnRenderedPage(1) },
                            ),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        ReaderDocumentBody(
                            document = document,
                            flow = settings.flow,
                            settings = settings,
                            settledIndex = renderedBlockIndex,
                            displayIndex = displayBlockIndex,
                            pageLayout = pageLayout,
                            displayPageIndex = displayPageIndex,
                            seeking = seekPreview != null,
                            onSettledIndexChanged = { commitPosition(it) },
                            imageStates = imageStates,
                            onImageVisible = onImageVisible,
                            onRetryImage = onRetryImage,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                ReaderReadingInfoBar(
                    chapterTitle = document.title,
                    progress = displayPosition.progress,
                    position = displayPosition,
                    visible = settings.progressVisible,
                )
            }
            AnimatedVisibility(
                visible = chromeVisible || overlay != null,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = topEnter,
                exit = topExit,
            ) {
                ReaderTopChrome(
                    chapterTitle = document.title,
                    bookmarked = currentChapterId in bookmarkedIds,
                    onUp = onNavigateUp,
                    onToggleBookmark = {
                        bookmarks = if (currentChapterId in bookmarkedIds) {
                            bookmarks - currentChapterId
                        } else {
                            bookmarks + currentChapterId
                        }
                    },
                    onOpenSearch = {
                        auxiliaryTab = ReaderAuxiliaryTab.SEARCH
                        overlay = ReaderOverlay.AUXILIARY
                    },
                )
            }
            AnimatedVisibility(
                visible = chromeVisible || overlay != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = bottomEnter,
                exit = bottomExit,
            ) {
                ReaderBottomChrome(
                    chapterIndex = currentChapterIndex,
                    chapterCount = chapters.size,
                    chapterProgress = if (settings.flow == ReaderFlow.SCROLL) {
                        readerPosition.progress
                    } else {
                        readerPosition.seekProgress
                    },
                    position = readerPosition,
                    continuousSeek = settings.flow == ReaderFlow.SCROLL,
                    seekPreview = seekPreview,
                    onSeekPreview = { seekPreview = it },
                    onSeekCommit = { targetProgress ->
                        if (settings.flow == ReaderFlow.SCROLL) {
                            commitPosition(progressToIndex(targetProgress, document.blocks.size))
                        } else {
                            val target = ReaderPosition.fromSeekProgress(
                                targetProgress,
                                pageLayout.pages.size.coerceAtLeast(1),
                                pageStep,
                            )
                            commitPage(target.page - 1)
                        }
                        seekPreview = null
                    },
                    onPreviousChapter = { selectAdjacentChapter(-1) },
                    onOpenContents = {
                        auxiliaryTab = ReaderAuxiliaryTab.CONTENTS
                        overlay = ReaderOverlay.AUXILIARY
                    },
                    onOpenSettings = { overlay = ReaderOverlay.SETTINGS },
                    onNextChapter = { selectAdjacentChapter(1) },
                )
            }
        }
    }

    when (overlay) {
        ReaderOverlay.AUXILIARY -> ReaderAuxiliarySheet(
            initialTab = auxiliaryTab,
            chapters = chapters,
            currentChapterId = currentChapterId,
            bookmarks = bookmarkedIds,
            onDismiss = { overlay = null },
            onSelectChapter = { chapter ->
                onSelectChapter(chapter)
                overlay = null
            },
            onToggleBookmark = { chapterId ->
                bookmarks = if (chapterId in bookmarkedIds) bookmarks - chapterId else bookmarks + chapterId
            },
        )
        ReaderOverlay.SETTINGS -> ReaderSettingsSheet(
            state = settings,
            onAction = ::updateSettings,
            onDismiss = { overlay = null },
        )
        null -> Unit
    }
}

@Composable
private fun ReaderDocumentBody(
    document: ReaderDocument,
    flow: ReaderFlow,
    settings: ReaderSettingsUiState,
    settledIndex: Int,
    displayIndex: Int,
    pageLayout: ReaderPageLayout,
    displayPageIndex: Int,
    seeking: Boolean,
    onSettledIndexChanged: (Int) -> Unit,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    modifier: Modifier,
) {
    when (flow) {
        ReaderFlow.SCROLL -> ScrollReaderBody(
            document = document,
            settings = settings,
            settledIndex = settledIndex,
            displayIndex = displayIndex,
            seeking = seeking,
            onSettledIndexChanged = onSettledIndexChanged,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            modifier = modifier,
        )
        ReaderFlow.PAGED -> PagedReaderBody(
            document = document,
            settings = settings,
            pageLayout = pageLayout,
            displayPageIndex = displayPageIndex,
            dual = false,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            modifier = modifier,
        )
        ReaderFlow.DUAL -> PagedReaderBody(
            document = document,
            settings = settings,
            pageLayout = pageLayout,
            displayPageIndex = displayPageIndex,
            dual = true,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            modifier = modifier,
        )
    }
}

@Composable
private fun ScrollReaderBody(
    document: ReaderDocument,
    settings: ReaderSettingsUiState,
    settledIndex: Int,
    displayIndex: Int,
    seeking: Boolean,
    onSettledIndexChanged: (Int) -> Unit,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = settledIndex)
    LaunchedEffect(displayIndex, seeking) {
        if (seeking) listState.scrollToItem(displayIndex)
    }
    LaunchedEffect(listState, seeking) {
        if (!seeking) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .drop(1)
                .collect(onSettledIndexChanged)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.testTag("reader-document-scroll"),
        contentPadding = PaddingValues(
            horizontal = settings.horizontalMargin.dp,
            vertical = settings.horizontalMargin.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacing.dp),
    ) {
        itemsIndexed(document.blocks, key = { _, block -> block.blockId }) { _, block ->
            ReaderBlockView(
                block = block,
                settings = settings,
                imageState = imageStates[block.blockId],
                onImageVisible = onImageVisible,
                onRetryImage = onRetryImage,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PagedReaderBody(
    document: ReaderDocument,
    settings: ReaderSettingsUiState,
    pageLayout: ReaderPageLayout,
    displayPageIndex: Int,
    dual: Boolean,
    modifier: Modifier,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
) {
    Row(
        modifier.padding(
            horizontal = settings.horizontalMargin.dp,
            vertical = settings.horizontalMargin.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(settings.horizontalMargin.dp),
    ) {
        repeat(if (dual) 2 else 1) { column ->
            val page = pageLayout.pages.getOrNull(displayPageIndex + column)
            Column(Modifier.weight(1f)) {
                page?.segments?.forEachIndexed { segmentIndex, segment ->
                    if (segmentIndex > 0) Spacer(Modifier.height(settings.paragraphSpacing.dp))
                    ReaderBlockSegmentView(
                        block = document.blocks[segment.blockIndex],
                        segment = segment,
                        settings = settings,
                        imageState = imageStates[document.blocks[segment.blockIndex].blockId],
                        onImageVisible = onImageVisible,
                        onRetryImage = onRetryImage,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBlockView(
    block: ReaderBlock,
    settings: ReaderSettingsUiState,
    modifier: Modifier,
    imageState: CoverUiState? = null,
    onImageVisible: (ReaderBlock.Image) -> Unit = {},
    onRetryImage: (ReaderBlock.Image) -> Unit = {},
) {
    when (block) {
        is ReaderBlock.Paragraph -> Text(block.text, modifier = modifier, style = readerBodyStyle(settings))
        is ReaderBlock.Heading -> Text(block.text, modifier = modifier, style = readerHeadingStyle(settings))
        is ReaderBlock.Image -> {
            val imageWidth = block.width
            val imageHeight = block.height
            val aspectRatio = if (imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0) {
                imageWidth.toFloat() / imageHeight.toFloat()
            } else {
                3f / 4f
            }
            ReaderImageBlock(
                block = block,
                state = imageState,
                onVisible = onImageVisible,
                onRetry = onRetryImage,
                modifier = modifier.aspectRatio(aspectRatio),
            )
        }
    }
}

@Composable
private fun ReaderBlockSegmentView(
    block: ReaderBlock,
    segment: ReaderPageSegment,
    settings: ReaderSettingsUiState,
    imageState: CoverUiState?,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    modifier: Modifier,
) {
    when (block) {
        is ReaderBlock.Paragraph -> Text(
            block.text.substring(segment.startCharacterIndex, segment.endCharacterIndex),
            modifier = modifier,
            style = readerBodyStyle(settings),
        )
        is ReaderBlock.Heading -> Text(
            block.text.substring(segment.startCharacterIndex, segment.endCharacterIndex),
            modifier = modifier,
            style = readerHeadingStyle(settings),
        )
        is ReaderBlock.Image -> {
            val height = with(LocalDensity.current) { segment.measuredHeightPx.toDp() }
            ReaderImageBlock(block, imageState, onImageVisible, onRetryImage, modifier.height(height))
        }
    }
}

@Composable
private fun ReaderImageBlock(
    block: ReaderBlock.Image,
    state: CoverUiState?,
    onVisible: (ReaderBlock.Image) -> Unit,
    onRetry: (ReaderBlock.Image) -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(block.url) { onVisible(block) }
    HostMediaImage(
        state = state,
        altText = block.altText,
        onRetry = { onRetry(block) },
        modifier = modifier,
    )
}


private fun progressToIndex(progress: Int, blockCount: Int): Int =
    ReaderPosition.fromProgress(progress, blockCount).page - 1

private fun Modifier.readerTapZones(
    onPrevious: () -> Boolean,
    onCenter: () -> Unit,
    onNext: () -> Boolean,
): Modifier = semantics {
    onClick(label = "显示或隐藏阅读控制") {
        onCenter()
        true
    }
    customActions = listOf(
        CustomAccessibilityAction("上一页") { onPrevious() },
        CustomAccessibilityAction("下一页") { onNext() },
    )
}.pointerInput(onPrevious, onCenter, onNext) {
    detectTapGestures { position ->
        val fraction = (position.x / size.width).coerceIn(0f, 1f)
        when {
            fraction < 0.3f -> if (!onPrevious()) onCenter()
            fraction > 0.7f -> if (!onNext()) onCenter()
            else -> onCenter()
        }
    }
}

private val ReaderSettingsSaver = Saver<ReaderSettingsUiState, List<Any>>(
    save = { state ->
        listOf(
            state.fontSize,
            state.lineHeight,
            state.horizontalMargin,
            state.paragraphSpacing,
            state.flow.name,
            state.lockPortrait,
            state.progressVisible,
            state.immersive,
            state.keepAwake,
        )
    },
    restore = { values ->
        ReaderSettingsUiState(
            fontSize = values[0] as Float,
            lineHeight = values[1] as Float,
            horizontalMargin = values[2] as Float,
            paragraphSpacing = values[3] as Float,
            flow = ReaderFlow.valueOf(values[4] as String),
            lockPortrait = values[5] as Boolean,
            progressVisible = values[6] as Boolean,
            immersive = values[7] as Boolean,
            keepAwake = values[8] as Boolean,
        )
    },
)

@Composable
private fun FrozenEInkReaderSurface(
    document: ReaderDocument,
    restoredLocator: ReaderLocator?,
    onLocatorChanged: (ReaderLocator, LocatorPrecision) -> Unit,
    modifier: Modifier,
    preferences: PortableReaderPreferences,
) {
    val session = remember(document.sourceId, document.remoteBookId, document.contentId, document.revision) {
        ReaderDocumentSession(document, restoredLocator, ReaderPresentation.PAGED)
    }
    var index by remember(session) { mutableIntStateOf(session.position.blockIndex) }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
        ReaderBlockView(
            block = document.blocks[index],
            settings = ReaderSettingsUiState(
                fontSize = (18.0 * (preferences.fontScale ?: 1.0)).toFloat(),
                lineHeight = (preferences.lineHeight ?: 1.5).toFloat(),
            ),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${index + 1}/${document.blocks.size}")
            Text("冻结的 E-ink 分页阅读器")
        }
    }
    LaunchedEffect(index) {
        val position = session.navigateToBlock(index)
        onLocatorChanged(session.capture(), position.precision)
    }
}
