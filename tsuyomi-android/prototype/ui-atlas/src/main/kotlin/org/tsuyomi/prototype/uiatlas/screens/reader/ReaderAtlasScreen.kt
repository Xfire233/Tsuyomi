/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens.reader

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
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasIconButton
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasInfoBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasStateKind
import org.tsuyomi.prototype.uiatlas.components.AtlasStateView
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasReaderPresentation
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasMotion
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

private enum class ReaderOverlay {
    AUXILIARY,
    SETTINGS,
}

@Composable
internal fun StandardReaderAtlasScreen(
    context: AtlasContext,
    modifier: Modifier = Modifier,
) {
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val presentation = LocalAtlasReaderPresentation.current
    val environment = LocalAtlasEnvironment.current
    val scope = rememberCoroutineScope()
    val totalChapters = SourceAtlasFixtures.DIRECTORY_TOTAL
    val bookFlowKey = "reader.flow.paper-lantern"

    var chapterNumber by rememberSaveable(runtime.persistent) {
        mutableIntStateOf(
            if (runtime.persistent) {
                repository.int("reader.chapter", SourceAtlasFixtures.CURRENT_CHAPTER)
                    .coerceIn(1, totalChapters)
            } else {
                SourceAtlasFixtures.CURRENT_CHAPTER
            },
        )
    }
    var chapterProgress by rememberSaveable { mutableIntStateOf(6) }
    var readerPosition by remember { mutableStateOf(ReaderPosition.START) }
    val initialFlow = remember {
        val fallback = if (repository.boolean("reader.scrollMode")) ReaderFlow.SCROLL.name else ReaderFlow.PAGED.name
        runCatching {
            ReaderFlow.valueOf(repository.string(bookFlowKey, repository.string("reader.lastFlow", fallback)))
        }.getOrDefault(ReaderFlow.PAGED)
    }
    var settings by remember {
        mutableStateOf(
            ReaderSettingsUiState(
                fontSize = repository.int("reader.fontSize", 18).toFloat(),
                lineHeight = repository.string("reader.lineSpacing", "1.6").toFloatOrNull() ?: 1.6f,
                horizontalMargin = repository.int("reader.margin", 24).toFloat(),
                verticalMargin = repository.int("reader.verticalMargin", 20).toFloat(),
                paragraphSpacing = repository.string("reader.paragraphSpacing", ".8").toFloatOrNull() ?: .8f,
                letterSpacing = repository.string("reader.letterSpacing", "0").toFloatOrNull() ?: 0f,
                firstLineIndent = repository.string("reader.firstLineIndent", "2").toFloatOrNull() ?: 2f,
                fontWeight = repository.string("reader.fontWeight", "常规"),
                alignment = repository.string("reader.alignment", "两端对齐"),
                paper = repository.string("reader.paper", "纸张"),
                flow = initialFlow,
                pageAnimation = repository.boolean("reader.pageAnimation", true),
                volumePaging = repository.boolean("reader.volumePaging"),
                keepAwake = repository.boolean("reader.keepAwake", true),
                lockPortrait = repository.boolean("reader.lockPortrait"),
                immersive = repository.boolean("reader.immersive", context.readerImmersive),
                progressVisible = repository.boolean("reader.progressVisible", true),
            ),
        )
    }
    var chromeVisible by remember(context.capture, context.readerSeekPreview) {
        mutableStateOf(!context.capture || context.review != null || context.readerSeekPreview != null)
    }
    var overlay by rememberSaveable(context.showModal) {
        mutableStateOf(if (context.showModal) ReaderOverlay.SETTINGS else null)
    }
    var auxiliaryTab by rememberSaveable { mutableStateOf(ReaderAuxiliaryTab.CONTENTS) }
    var seekPreview by rememberSaveable(context.readerSeekPreview) {
        mutableStateOf(
            if (context.readerSeekPreview != null) SourceAtlasFixtures.SEEK_TARGET_PROGRESS else null,
        )
    }
    var bookmarks by remember {
        mutableStateOf(repository.stringList("reader.bookmarks").mapNotNull(String::toIntOrNull).toSet())
    }
    var expandedImage by remember { mutableStateOf<ReaderImage?>(null) }
    var contentActionNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var retryResolved by remember(context.state) { mutableStateOf(false) }
    var retryWorking by remember(context.state) { mutableStateOf(false) }
    var retryFailure by remember(context.state) { mutableStateOf<String?>(null) }

    val guarded = context.libraryView == AtlasLibraryView.READ_LATER
    val reading = (context.primaryState == AtlasPageState.CONTENT || retryResolved) && !guarded
    val showChrome = !reading || context.showOfflineBanner || chromeVisible || overlay != null || expandedImage != null
    val document = remember(context.libraryView, chapterNumber) {
        ReaderAtlasFixtures.documentFor(context.libraryView, chapterNumber)
    }
    val chapterTitle = SourceAtlasFixtures.chapters[chapterNumber - 1].title
    val textSettings = remember(settings) {
        ReaderTextSettings(
            fontSize = settings.fontSize,
            lineHeightMultiplier = settings.lineHeight,
            horizontalMargin = settings.horizontalMargin,
            verticalMargin = settings.verticalMargin,
            paragraphSpacing = settings.paragraphSpacing,
            letterSpacing = settings.letterSpacing,
            firstLineIndent = settings.firstLineIndent,
            fontWeight = settings.fontWeight,
            alignment = settings.alignment,
        )
    }
    val readerBackground = when (settings.paper) {
        "纯白" -> Color.White
        "夜间" -> MaterialTheme.colorScheme.inverseSurface
        else -> MaterialTheme.colorScheme.background
    }
    val readerContentColor = when (settings.paper) {
        "夜间" -> MaterialTheme.colorScheme.inverseOnSurface
        else -> MaterialTheme.colorScheme.onBackground
    }
    val activity = LocalActivity.current
    val readerView = LocalView.current

    DisposableEffect(settings.keepAwake, readerView) {
        readerView.keepScreenOn = settings.keepAwake
        onDispose { readerView.keepScreenOn = false }
    }
    val readerFocusRequester = remember { FocusRequester() }
    DisposableEffect(settings.lockPortrait, activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = if (settings.lockPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            if (activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }
    val chromeDuration = AtlasMotion.duration(AtlasMotion.READER_CHROME_MS, environment)
    val topEnter = if (chromeDuration == 0) EnterTransition.None else {
        fadeIn(tween(chromeDuration)) + slideInVertically(tween(chromeDuration)) { -it / 5 }
    }
    val topExit = if (chromeDuration == 0) ExitTransition.None else {
        fadeOut(tween(chromeDuration)) + slideOutVertically(tween(chromeDuration)) { -it / 5 }
    }
    val bottomEnter = if (chromeDuration == 0) EnterTransition.None else {
        fadeIn(tween(chromeDuration)) + slideInVertically(tween(chromeDuration)) { it / 5 }
    }
    val bottomExit = if (chromeDuration == 0) ExitTransition.None else {
        fadeOut(tween(chromeDuration)) + slideOutVertically(tween(chromeDuration)) { it / 5 }
    }

    fun commitChapter(target: Int, event: String, entryProgress: Int? = null) {
        val safeTarget = target.coerceIn(1, totalChapters)
        val nextProgress = entryProgress ?: when {
            safeTarget == chapterNumber -> chapterProgress
            safeTarget < chapterNumber -> 100
            else -> 0
        }
        chapterNumber = safeTarget
        chapterProgress = nextProgress.coerceIn(0, 100)
        readerPosition = ReaderPosition.fromProgress(chapterProgress, readerPosition.pageCount)
        seekPreview = null
        repository.putInt("reader.chapter", safeTarget, event, SourceAtlasFixtures.chapters[safeTarget - 1].title)
    }

    fun commitProgress(target: Int) {
        chapterProgress = target.coerceIn(0, 100)
        seekPreview = null
        repository.record("LocatorCommit", "chapter=$chapterNumber;preview=$chapterProgress%", "success")
    }

    fun turnRenderedPage(direction: Int): Boolean {
        if (settings.flow == ReaderFlow.SCROLL || seekPreview != null) return false
        val target = readerPosition.adjacentPage(direction)
        if (target != null) {
            chapterProgress = target.progress
            readerPosition = target
            repository.record(
                if (direction < 0) "ReaderPagePrevious" else "ReaderPageNext",
                "chapter=$chapterNumber;page=${target.page}/${target.pageCount}",
                "success",
            )
            return true
        }
        if (direction > 0 && chapterNumber < totalChapters && readerPosition.page >= readerPosition.pageCount) {
            commitChapter(chapterNumber + 1, "ReaderPageBoundaryNextChapter", entryProgress = 0)
            return true
        }
        return false
    }

    fun toggleBookmark(target: Int = chapterNumber) {
        bookmarks = if (target in bookmarks) bookmarks - target else bookmarks + target
        repository.putStringList(
            "reader.bookmarks",
            bookmarks.sorted().map(Int::toString),
            if (target in bookmarks) "ReaderBookmarkAdded" else "ReaderBookmarkRemoved",
            target.toString(),
        )
    }

    fun updateSettings(action: ReaderSettingsAction) {
        settings = when (action) {
            is ReaderSettingsAction.FontSize -> settings.copy(fontSize = action.value).also {
                repository.putInt("reader.fontSize", action.value.toInt(), "ReaderFontSizeChanged")
            }
            is ReaderSettingsAction.LineHeight -> settings.copy(lineHeight = action.value).also {
                repository.putString("reader.lineSpacing", action.value.toString(), "ReaderLineSpacingChanged")
            }
            is ReaderSettingsAction.HorizontalMargin -> settings.copy(horizontalMargin = action.value).also {
                repository.putInt("reader.margin", action.value.toInt(), "ReaderMarginChanged")
            }
            is ReaderSettingsAction.VerticalMargin -> settings.copy(verticalMargin = action.value).also {
                repository.putInt("reader.verticalMargin", action.value.toInt(), "ReaderVerticalMarginChanged")
            }
            is ReaderSettingsAction.ParagraphSpacing -> settings.copy(paragraphSpacing = action.value).also {
                repository.putString("reader.paragraphSpacing", action.value.toString(), "ReaderParagraphSpacingChanged")
            }
            is ReaderSettingsAction.LetterSpacing -> settings.copy(letterSpacing = action.value).also {
                repository.putString("reader.letterSpacing", action.value.toString(), "ReaderLetterSpacingChanged")
            }
            is ReaderSettingsAction.FirstLineIndent -> settings.copy(firstLineIndent = action.value).also {
                repository.putString("reader.firstLineIndent", action.value.toString(), "ReaderFirstLineIndentChanged")
            }
            is ReaderSettingsAction.FontWeight -> settings.copy(fontWeight = action.value).also {
                repository.putString("reader.fontWeight", action.value, "ReaderFontWeightChanged")
            }
            is ReaderSettingsAction.Alignment -> settings.copy(alignment = action.value).also {
                repository.putString("reader.alignment", action.value, "ReaderAlignmentChanged")
            }
            is ReaderSettingsAction.Paper -> settings.copy(paper = action.value).also {
                repository.putString("reader.paper", action.value, "ReaderPaperChanged")
            }
            is ReaderSettingsAction.Flow -> settings.copy(flow = action.value).also {
                repository.putString(bookFlowKey, action.value.name, "ReaderBookFlowChanged")
                repository.putString("reader.lastFlow", action.value.name, "ReaderLastFlowChanged")
                repository.putBoolean("reader.scrollMode", action.value == ReaderFlow.SCROLL, "ReaderModeChanged")
            }
            is ReaderSettingsAction.PageAnimation -> settings.copy(pageAnimation = action.value).also {
                repository.putBoolean("reader.pageAnimation", action.value, "ReaderPageAnimationChanged")
            }
            is ReaderSettingsAction.VolumePaging -> settings.copy(volumePaging = action.value).also {
                repository.putBoolean("reader.volumePaging", action.value, "ReaderVolumePagingChanged")
            }
            is ReaderSettingsAction.KeepAwake -> settings.copy(keepAwake = action.value).also {
                repository.putBoolean("reader.keepAwake", action.value, "ReaderKeepAwakeChanged")
            }
            is ReaderSettingsAction.LockPortrait -> settings.copy(lockPortrait = action.value).also {
                repository.putBoolean("reader.lockPortrait", action.value, "ReaderLockPortraitChanged")
            }
            is ReaderSettingsAction.Immersive -> settings.copy(immersive = action.value).also {
                repository.putBoolean("reader.immersive", action.value, "ReaderImmersiveChanged")
                presentation.setImmersive(action.value)
            }
            is ReaderSettingsAction.ProgressVisible -> settings.copy(progressVisible = action.value).also {
                repository.putBoolean("reader.progressVisible", action.value, "ReaderProgressVisibilityChanged")
            }
        }
    }
    LaunchedEffect(settings.volumePaging, overlay) {
        if (settings.volumePaging && overlay == null) readerFocusRequester.requestFocus()
    }

    LaunchedEffect(showChrome) {
        presentation.setChromeVisible(showChrome)
    }
    LaunchedEffect(settings.immersive) {
        presentation.setImmersive(settings.immersive)
    }

    BackHandler(enabled = expandedImage != null) { expandedImage = null }
    BackHandler(enabled = overlay != null) { overlay = null }
    BackHandler(enabled = seekPreview != null) { seekPreview = null }
    BackHandler(enabled = overlay == null && seekPreview == null && expandedImage == null && chromeVisible) {
        chromeVisible = false
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(readerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!settings.volumePaging || overlay != null || event.type != KeyEventType.KeyUp) {
                    false
                } else {
                    when (event.key) {
                        Key.VolumeUp -> turnRenderedPage(-1)
                        Key.VolumeDown -> turnRenderedPage(1)
                        else -> false
                    }
                }
            },
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                context.primaryState == AtlasPageState.LOADING || retryWorking -> AtlasStateView(
                    kind = AtlasStateKind.LOADING,
                    title = if (retryWorking) "正在重试章节…" else "正在加载章节…",
                    modifier = Modifier.fillMaxSize(),
                )
                context.primaryState == AtlasPageState.ERROR && !retryResolved -> AtlasStateView(
                    kind = AtlasStateKind.ERROR,
                    title = "章节加载失败",
                    modifier = Modifier.fillMaxSize(),
                    message = retryFailure ?: "该章节尚未下载；已下载章节仍可离线阅读。",
                    actionLabel = AtlasStrings.RETRY,
                    onAction = {
                        retryWorking = true
                        retryFailure = null
                        scope.launch {
                            val result = runtime.scenarios.run("reader-load", chapterTitle)
                            retryWorking = false
                            if (result.successful) retryResolved = true
                            else retryFailure = "重试未完成：${result.outcome}。"
                        }
                    },
                )
                guarded -> ReaderVerificationRequired()
                else -> Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("reader-content-surface")
                                .readerTapZones(
                                    onPrevious = { turnRenderedPage(-1) },
                                    onCenter = { chromeVisible = !chromeVisible },
                                    onNext = { turnRenderedPage(1) },
                                ),
                            color = readerBackground,
                            contentColor = readerContentColor,
                        ) {
                            ReaderDocumentView(
                                document = document,
                                flow = settings.flow,
                                textSettings = textSettings,
                                progress = seekPreview ?: chapterProgress,
                                seeking = seekPreview != null,
                                onPositionChanged = { position ->
                                    readerPosition = position
                                    if (seekPreview == null && position.progress != chapterProgress) {
                                        chapterProgress = position.progress
                                    }
                                },
                                actions = ReaderContentActions(
                                    onImageClick = { expandedImage = it },
                                    onLinkClick = { destination ->
                                        contentActionNotice = "已打开链接：$destination"
                                        repository.record("ReaderLinkOpened", destination, "success")
                                    },
                                    onAttachmentClick = { attachment ->
                                        contentActionNotice = "已打开附件：${attachment.name}"
                                        repository.record("ReaderAttachmentOpened", attachment.id, "success")
                                    },
                                    onReplyClick = { reference ->
                                        chapterProgress = document.progressForBlock(reference.targetPostId)
                                        seekPreview = null
                                        repository.record("ReaderReplyOpened", reference.targetPostId, "success")
                                        contentActionNotice = "已跳转至 ${reference.floor} · ${reference.author}"
                                    },
                                ),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (context.showOfflineBanner) {
                            AtlasInfoBanner(
                                AtlasBanner(
                                    AtlasStrings.OFFLINE_TITLE,
                                    "本地章节可正常阅读；来源操作已停用。",
                                ),
                            )
                        }
                        contentActionNotice?.let { notice ->
                            AtlasInfoBanner(AtlasBanner("正文操作", notice))
                        }
                    }
                    ReaderReadingInfoBar(
                        chapterTitle = chapterTitle,
                        progress = seekPreview ?: chapterProgress,
                        position = readerPosition,
                        visible = reading && settings.progressVisible,
                    )
                }
            }

            AnimatedVisibility(
                visible = showChrome,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = topEnter,
                exit = topExit,
            ) {
                ReaderTopChrome(
                    chapterTitle = chapterTitle,
                    bookmarked = chapterNumber in bookmarks,
                    onUp = navigation.up,
                    onToggleBookmark = ::toggleBookmark,
                    onOpenSearch = {
                        auxiliaryTab = ReaderAuxiliaryTab.SEARCH
                        overlay = ReaderOverlay.AUXILIARY
                    },
                )
            }
            AnimatedVisibility(
                visible = reading && showChrome,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = bottomEnter,
                exit = bottomExit,
            ) {
                ReaderBottomChrome(
                    chapterNumber = chapterNumber,
                    totalChapters = totalChapters,
                    chapterProgress = chapterProgress,
                    position = readerPosition,
                    seekPreview = seekPreview,
                    onSeekPreview = { seekPreview = it },
                    onSeekCommit = { seekPreview?.let(::commitProgress) },
                    onPreviousChapter = { commitChapter(chapterNumber - 1, "ReaderChapterPrevious") },
                    onOpenContents = {
                        auxiliaryTab = ReaderAuxiliaryTab.CONTENTS
                        overlay = ReaderOverlay.AUXILIARY
                    },
                    onOpenSettings = { overlay = ReaderOverlay.SETTINGS },
                    onNextChapter = { commitChapter(chapterNumber + 1, "ReaderChapterNext") },
                )
            }
        }
    }

    when (overlay) {
        ReaderOverlay.AUXILIARY -> ReaderAuxiliarySheet(
            initialTab = auxiliaryTab,
            currentChapter = chapterNumber,
            bookmarks = bookmarks,
            onDismiss = { overlay = null },
            onSelectChapter = {
                commitChapter(it, "ReaderChapterSelected", entryProgress = 0)
                overlay = null
            },
            onToggleBookmark = ::toggleBookmark,
            onSearchSubmitted = { query -> repository.record("ReaderSearchSubmitted", query, "success") },
        )
        ReaderOverlay.SETTINGS -> ReaderSettingsSheet(
            state = settings,
            onAction = ::updateSettings,
            onDismiss = { overlay = null },
        )
        null -> Unit
    }

    expandedImage?.let { image ->
        ReaderImageDialog(image = image, onDismiss = { expandedImage = null })
    }
}

@Composable
private fun ReaderVerificationRequired() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(AtlasIcons.Verify, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.padding(AtlasSpacing.Xs))
            Text("该章节需要完成验证", style = MaterialTheme.typography.titleMedium)
            Text(
                "返回来源验证后可继续；本地已下载章节不受影响。",
                modifier = Modifier.padding(AtlasSpacing.Md),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ReaderImageDialog(image: ReaderImage, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                RowHeader(title = image.title, onDismiss = onDismiss)
                ReaderIllustration(
                    title = image.title,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(
                    image.caption,
                    modifier = Modifier.padding(AtlasSpacing.Md),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    image.alternative,
                    modifier = Modifier.padding(horizontal = AtlasSpacing.Md).padding(bottom = AtlasSpacing.Lg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RowHeader(title: String, onDismiss: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(AtlasSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtlasIconButton(AtlasIcons.Close, "关闭大图", onDismiss)
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Modifier.readerTapZones(
    onPrevious: () -> Boolean,
    onCenter: () -> Unit,
    onNext: () -> Boolean,
): Modifier {
    val currentPrevious = rememberUpdatedState(onPrevious)
    val currentCenter = rememberUpdatedState(onCenter)
    val currentNext = rememberUpdatedState(onNext)
    return this
        .semantics {
            onClick(label = "显示或隐藏阅读工具栏") {
                currentCenter.value()
                true
            }
            customActions = listOf(
                CustomAccessibilityAction("上一页") { currentPrevious.value() },
                CustomAccessibilityAction("下一页") { currentNext.value() },
            )
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                val pointerId = down.id
                val origin = down.position
                var canceled = down.isConsumed
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (change.isConsumed || (change.position - origin).getDistance() > viewConfiguration.touchSlop) {
                        canceled = true
                    }
                    if (!change.pressed) {
                        if (!canceled) {
                            when {
                                origin.x < size.width / 3f -> currentPrevious.value()
                                origin.x > size.width * 2f / 3f -> currentNext.value()
                                else -> currentCenter.value()
                            }
                        }
                        break
                    }
                }
            }
        }
}
