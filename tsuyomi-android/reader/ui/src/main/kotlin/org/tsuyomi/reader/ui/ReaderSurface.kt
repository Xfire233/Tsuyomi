/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.HostMediaImage
import org.tsuyomi.core.ui.components.TsuyomiContentDirection
import org.tsuyomi.core.ui.components.TsuyomiDirectionalAnimatedContent
import org.tsuyomi.core.ui.components.TsuyomiExtendedFab
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.core.ui.theme.instantMotion
import org.tsuyomi.core.ui.components.TsuyomiVisibility
import org.tsuyomi.core.ui.components.TsuyomiVisibilityEdge
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.TsuyomiDarkColorScheme
import org.tsuyomi.core.ui.theme.TsuyomiLightColorScheme
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.reader.engine.FrozenPreviewPlan
import org.tsuyomi.reader.engine.LayoutKey
import org.tsuyomi.core.ui.components.animateTsuyomiPageTurn
import org.tsuyomi.reader.engine.PreviewRelease
import org.tsuyomi.reader.engine.PreviewSession
import org.tsuyomi.reader.engine.PreviewTarget
import org.tsuyomi.reader.engine.PreviewVisualWitness
import org.tsuyomi.reader.engine.ReaderEpochs
import org.tsuyomi.reader.engine.ReaderPresentation
import org.tsuyomi.reader.engine.ReaderDocumentSession
import org.tsuyomi.reader.engine.effectiveReaderPresentation
import org.tsuyomi.shared.locator.namesSameBookmarkPositionAs
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

private enum class ScrollSeekPhase {
    IDLE,
    PREVIEWING,
    RESTORING,
}

@Stable
class ReaderReturnAnchorState internal constructor() {
    var anchor by mutableStateOf<ReaderLocator?>(null)
        private set

    private var pendingOrigin: ReaderLocator? = null
    private var returning = false
    private var ordinaryAdvanceCount = 0

    internal fun commitInternalJump(origin: ReaderLocator, target: ReaderLocator) {
        if (anchor == null && !origin.namesSameBookmarkPositionAs(target)) {
            anchor = origin
            ordinaryAdvanceCount = 0
        }
    }

    internal fun requestExternalJump(origin: ReaderLocator) {
        if (anchor == null && pendingOrigin == null) pendingOrigin = origin
    }

    internal fun navigationMounted() {
        when {
            returning -> clear()
            pendingOrigin != null -> {
                anchor = pendingOrigin
                pendingOrigin = null
                ordinaryAdvanceCount = 0
            }
        }
    }

    fun navigationFailed() {
        pendingOrigin = null
    }

    internal fun ordinaryAdvance() {
        if (anchor == null) return
        ordinaryAdvanceCount += 1
        if (ordinaryAdvanceCount >= 3) clear()
    }

    internal fun returnToOrigin(navigate: (ReaderLocator) -> Unit) {
        val target = anchor ?: return
        returning = true
        navigate(target)
    }

    private fun clear() {
        anchor = null
        pendingOrigin = null
        returning = false
        ordinaryAdvanceCount = 0
    }
}

@Composable
fun rememberReaderReturnAnchorState(sourceId: String, remoteBookId: String): ReaderReturnAnchorState =
    remember(sourceId, remoteBookId) { ReaderReturnAnchorState() }

@Composable
fun ReaderSurface(
    document: ReaderDocument,
    auxiliarySheetState: ReaderAuxiliarySheetState,
    restoredLocator: ReaderLocator?,
    restorationGeneration: Long,
    onLocatorChanged: (ReaderLocator, LocatorPrecision) -> Unit,
    chapters: List<SourceChapter>,
    currentChapterId: String,
    bookmarks: List<ReaderLocator>,
    onToggleBookmark: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
    onSelectBookmark: (ReaderLocator) -> Unit,
    requestedFlow: String,
    hasFlowOverride: Boolean,
    onFlowOverrideChanged: (String?) -> Unit,
    onSelectChapter: (SourceChapter) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    onChapterCompleted: (String) -> Unit = {},
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
    onReturnToOrigin: (ReaderLocator) -> Unit = onSelectBookmark,
    returnAnchorState: ReaderReturnAnchorState? = null,
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        FrozenEInkReaderSurface(document, restoredLocator, restorationGeneration, onLocatorChanged, modifier, preferences)
        return
    }
    val effectiveReturnAnchorState = returnAnchorState
        ?: rememberReaderReturnAnchorState(document.sourceId, document.remoteBookId)
    var activeTheme by remember(document.sourceId, document.remoteBookId) { mutableStateOf(readerTheme(preferences.theme)) }
    var activeForegroundColor by remember(document.sourceId, document.remoteBookId) { mutableStateOf(preferences.foregroundColor) }
    var activeBackgroundColor by remember(document.sourceId, document.remoteBookId) { mutableStateOf(preferences.backgroundColor) }
    LaunchedEffect(preferences.theme, preferences.foregroundColor, preferences.backgroundColor) {
        activeTheme = readerTheme(preferences.theme)
        activeForegroundColor = preferences.foregroundColor
        activeBackgroundColor = preferences.backgroundColor
    }
    MaterialTheme(
        colorScheme = readerColorScheme(activeTheme, activeForegroundColor, activeBackgroundColor),
    ) {
        ReaderSurfaceContent(
            document = document,
            auxiliarySheetState = auxiliarySheetState,
            restoredLocator = restoredLocator,
            restorationGeneration = restorationGeneration,
            onLocatorChanged = onLocatorChanged,
            chapters = chapters,
            currentChapterId = currentChapterId,
            bookmarks = bookmarks,
            onToggleBookmark = onToggleBookmark,
            onRemoveBookmark = onRemoveBookmark,
            onSelectBookmark = onSelectBookmark,
            onReturnToOrigin = onReturnToOrigin,
            returnAnchorState = effectiveReturnAnchorState,
            requestedFlow = requestedFlow,
            hasFlowOverride = hasFlowOverride,
            onFlowOverrideChanged = onFlowOverrideChanged,
            onSelectChapter = onSelectChapter,
            onNavigateUp = onNavigateUp,
            onChapterCompleted = onChapterCompleted,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            preferences = preferences,
            onPreferencesChanged = onPreferencesChanged,
            onReaderAppearanceChanged = { theme, foreground, background ->
                activeTheme = theme
                activeForegroundColor = foreground
                activeBackgroundColor = background
            },
            modifier = modifier,
        )
    }
}

private fun PortableReaderPreferences.readerSettings(requestedFlow: String) = ReaderSettingsUiState(
    fontSize = (18.0 * (fontScale ?: 1.0)).toFloat(),
    lineHeight = (lineHeight ?: 1.5).toFloat(),
    horizontalMargin = (horizontalMargin ?: 24.0).toFloat(),
    paragraphSpacing = (paragraphSpacing ?: 12.0).toFloat(),
    fontFamily = fontFamily ?: "system",
    fontWeight = fontWeight ?: 400,
    letterSpacing = (letterSpacing ?: 0.0).toFloat(),
    firstLineIndent = (firstLineIndent ?: 0.0).toFloat(),
    verticalMargin = (verticalMargin ?: 24.0).toFloat(),
    textAlignment = textAlignment ?: "start",
    foregroundColor = foregroundColor,
    backgroundColor = backgroundColor,
    flow = requestedFlow.readerFlow(),
    theme = readerTheme(theme),
    lockPortrait = lockPortrait ?: false,
    progressVisible = progressVisible ?: true,
    immersive = immersive ?: false,
    keepAwake = keepAwake ?: true,
    volumePaging = volumePaging ?: true,
)

@Composable
private fun ReaderSurfaceContent(
    document: ReaderDocument,
    auxiliarySheetState: ReaderAuxiliarySheetState,
    restoredLocator: ReaderLocator?,
    restorationGeneration: Long,
    onLocatorChanged: (ReaderLocator, LocatorPrecision) -> Unit,
    chapters: List<SourceChapter>,
    currentChapterId: String,
    bookmarks: List<ReaderLocator>,
    onToggleBookmark: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
    onSelectBookmark: (ReaderLocator) -> Unit,
    onReturnToOrigin: (ReaderLocator) -> Unit,
    returnAnchorState: ReaderReturnAnchorState,
    requestedFlow: String,
    hasFlowOverride: Boolean,
    onFlowOverrideChanged: (String?) -> Unit,
    onSelectChapter: (SourceChapter) -> Unit,
    onNavigateUp: () -> Unit,
    onChapterCompleted: (String) -> Unit,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    preferences: PortableReaderPreferences,
    onPreferencesChanged: (PortableReaderPreferences) -> Unit,
    onReaderAppearanceChanged: (ReaderTheme, String?, String?) -> Unit,
    modifier: Modifier,
) {
    var settings by rememberSaveable(
        document.sourceId,
        document.remoteBookId,
        stateSaver = ReaderSettingsSaver,
    ) {
        mutableStateOf(preferences.readerSettings(requestedFlow))
    }
    LaunchedEffect(preferences, requestedFlow) {
        settings = preferences.readerSettings(requestedFlow)
    }
    val requestedPresentation = settings.flow.presentation()
    var viewportSize by remember(document.sourceId, document.remoteBookId, document.contentId, document.revision) {
        mutableStateOf(IntSize.Zero)
    }
    val density = LocalDensity.current
    val horizontalMarginPx = with(density) { settings.horizontalMargin.dp.roundToPx() }
    val minimumDualWindowPx = with(density) { 600.dp.roundToPx() }
    val minimumDualColumnPx = with(density) { 260.dp.roundToPx() }
    val dualPageEligible =
        viewportSize.width >= minimumDualWindowPx &&
            viewportSize.width - horizontalMarginPx * 3 >= minimumDualColumnPx * 2
    val effectivePresentation = effectiveReaderPresentation(requestedPresentation, dualPageEligible)
    val effectiveFlow = effectivePresentation.flow()
    val session = remember(
        document.sourceId,
        document.remoteBookId,
        document.contentId,
        document.revision,
        restorationGeneration,
    ) {
        ReaderDocumentSession(document, restoredLocator, effectivePresentation)
    }
    val previewLayoutKey = LayoutKey(
        "${effectivePresentation.name}:${viewportSize.width}x${viewportSize.height}:" +
            "${settings.fontSize}:${settings.lineHeight}:${settings.horizontalMargin}:${settings.verticalMargin}:" +
            "${settings.paragraphSpacing}:${settings.fontFamily}:${settings.fontWeight}:${settings.letterSpacing}:" +
            "${settings.firstLineIndent}:${settings.textAlignment}",
    )
    val previewEpochs = remember(session, previewLayoutKey) {
        ReaderEpochs(
            document = session.identity,
            documentRevision = session.documentRevision,
            contentDigest = session.contentDigest,
            documentEpoch = 0,
            sessionEpoch = 0,
            layoutKey = previewLayoutKey,
            layoutEpoch = previewLayoutKey.value.hashCode().toLong().let { if (it < 0L) -it else it },
            navigationEpoch = 0,
        )
    }
    var renderedBlockIndex by remember(session) { mutableIntStateOf(session.position.blockIndex) }
    var renderedCodePointOffset by remember(session) { mutableIntStateOf(session.position.characterOffset) }
    var pageTurnGeneration by remember(session) { mutableLongStateOf(0L) }
    var pageTurnTargetIndex by remember(session) { mutableIntStateOf(-1) }
    val pageLayout = rememberReaderPageLayout(
        document = document,
        settings = settings,
        viewportSize = viewportSize,
        dual = effectivePresentation == ReaderPresentation.DUAL_PAGE,
        enabled = effectivePresentation != ReaderPresentation.SCROLL,
    )
    val renderedPageIndex = pageLayout.pageIndexFor(renderedBlockIndex, renderedCodePointOffset)
    val pageStep = if (effectivePresentation == ReaderPresentation.DUAL_PAGE) 2 else 1
    var pageDragOffsetPx by remember(session) { mutableFloatStateOf(0f) }
    var pageDragSettling by remember(session) { mutableStateOf(false) }
    val pageDragScope = rememberCoroutineScope()
    val displayEnvironment = LocalDisplayEnvironment.current
    val pageDragInteractive = displayEnvironment.effectiveProfile == DisplayProfile.STANDARD
    val instantPageMotion = displayEnvironment.instantMotion || rememberSystemReducedMotion()
    val readerPosition = if (effectivePresentation == ReaderPresentation.SCROLL) {
        ReaderPosition.fromPageIndex(renderedBlockIndex, document.blocks.size)
    } else {
        ReaderPosition.fromPageIndex(renderedPageIndex, pageLayout.pages.size.coerceAtLeast(1), pageStep)
    }
    var scrollViewportAtEnd by remember(document.contentId) { mutableStateOf(false) }
    val atChapterEnd = if (effectivePresentation == ReaderPresentation.SCROLL) {
        scrollViewportAtEnd
    } else {
        readerPosition.page + readerPosition.pageStep - 1 >= readerPosition.pageCount
    }
    var chapterCompletionReported by remember(document.contentId) { mutableStateOf(false) }
    var chromeVisible by rememberSaveable(document.contentId) { mutableStateOf(true) }
    var overlay by rememberSaveable(document.contentId) { mutableStateOf<ReaderOverlay?>(null) }
    var scrollPreviewSession by remember(document.contentId) { mutableStateOf<PreviewSession?>(null) }
    var scrollPreviewWitness by remember(document.contentId) { mutableStateOf<PreviewVisualWitness?>(null) }
    var scrollPreviewSessionId by remember(document.contentId) { mutableLongStateOf(0L) }
    var scrollPreviewVisualEpoch by remember(document.contentId) { mutableLongStateOf(0L) }
    var pendingScrollPreviewRelease by remember(document.contentId) { mutableStateOf<Int?>(null) }
    var seekPreview by rememberSaveable(document.contentId) { mutableStateOf<Int?>(null) }
    var scrollSeekPhase by remember(document.contentId) { mutableStateOf(ScrollSeekPhase.IDLE) }
    var readingInfoHeightPx by remember(document.contentId) { mutableIntStateOf(0) }
    var bottomChromeHeightPx by remember(document.contentId) { mutableIntStateOf(0) }
    val currentChapterIndex = chapters.indexOfFirst { it.chapterId == currentChapterId }
    val readerFocusRequester = remember { FocusRequester() }
    val readerView = LocalView.current
    val activity = LocalActivity.current
    val latestOnLocatorChanged by rememberUpdatedState(onLocatorChanged)
    val committedBookmarkLocator = remember(session, renderedBlockIndex, renderedCodePointOffset) {
        session.previewPositionAtBlock(renderedBlockIndex, renderedCodePointOffset).locator
    }

    fun commitPosition(
        blockIndex: Int,
        codePointOffset: Int = 0,
        ordinaryAdvance: Boolean = true,
    ): ReaderLocator {
        val resolved = session.navigateToBlock(blockIndex.coerceIn(document.blocks.indices), codePointOffset)
        renderedBlockIndex = resolved.blockIndex
        renderedCodePointOffset = resolved.characterOffset
        val locator = session.capture()
        latestOnLocatorChanged(locator, resolved.precision)
        if (ordinaryAdvance) returnAnchorState.ordinaryAdvance()
        return locator
    }

    fun commitPage(pageIndex: Int, ordinaryAdvance: Boolean = true): ReaderLocator? {
        val page = pageLayout.pages.getOrNull(pageIndex) ?: return null
        return commitPosition(page.startBlockIndex, page.startCodePointOffset, ordinaryAdvance)
    }

    fun cancelSeek() {
        if (seekPreview == null) return
        scrollPreviewSession?.cancel()
        scrollPreviewSession = null
        scrollPreviewWitness = null
        pendingScrollPreviewRelease = null
        if (scrollSeekPhase != ScrollSeekPhase.IDLE) scrollSeekPhase = ScrollSeekPhase.RESTORING
        seekPreview = null
    }

    fun releaseScrollPreviewIfReady() {
        val pendingProgress = pendingScrollPreviewRelease ?: return
        val preview = scrollPreviewSession ?: return
        val expectedIndex = progressToIndex(pendingProgress, document.blocks.size)
        val request = preview.currentRequest ?: return
        if (request.target.id != expectedIndex.toString()) return
        val witness = scrollPreviewWitness ?: return
        when (val release = preview.release(witness, previewEpochs, preview.plan.revision)) {
            is PreviewRelease.Committed -> {
                val blockIndex = release.target.id.toIntOrNull() ?: return
                val origin = session.previewPositionAtBlock(renderedBlockIndex, renderedCodePointOffset).locator
                val target = commitPosition(blockIndex, ordinaryAdvance = false)
                returnAnchorState.commitInternalJump(origin, target)
                scrollPreviewSession = null
                scrollPreviewWitness = null
                pendingScrollPreviewRelease = null
                scrollSeekPhase = ScrollSeekPhase.IDLE
                seekPreview = null
            }
            is PreviewRelease.Rejected -> cancelSeek()
        }
    }

    fun recordScrollPreviewVisualCommit() {
        val preview = scrollPreviewSession ?: return
        val currentProgress = seekPreview ?: return
        val request = preview.currentRequest ?: return
        if (request.target.id != progressToIndex(currentProgress, document.blocks.size).toString()) return
        scrollPreviewVisualEpoch += 1
        scrollPreviewWitness = PreviewVisualWitness(
            sessionId = preview.sessionId,
            target = request.target,
            epochs = preview.epochs,
            planRevision = preview.plan.revision,
            requestGeneration = request.generation,
            visualEpoch = scrollPreviewVisualEpoch,
        )
        releaseScrollPreviewIfReady()
    }

    fun startScrollPreview(progress: Int) {
        val targetIndex = progressToIndex(progress, document.blocks.size)
        val existing = scrollPreviewSession?.takeIf {
            it.epochs == previewEpochs && it.plan.revision == previewEpochs.layoutEpoch
        }
        if (existing == null) scrollPreviewSession?.cancel()
        val preview = existing ?: PreviewSession(
            sessionId = scrollPreviewSessionId + 1,
            epochs = previewEpochs,
            plan = FrozenPreviewPlan(
                revision = previewEpochs.layoutEpoch,
                targets = (0..100).map { targetProgress ->
                    val blockIndex = progressToIndex(targetProgress, document.blocks.size)
                    PreviewTarget(blockIndex.toString(), session.previewPositionAtBlock(blockIndex).locator)
                },
            ),
        ).also {
            scrollPreviewSessionId = it.sessionId
            scrollPreviewSession = it
        }
        val target = preview.plan.targets.firstOrNull { it.id == targetIndex.toString() } ?: return
        val previousRequest = preview.currentRequest
        preview.requestTarget(target)
        if (preview.currentRequest != previousRequest) scrollPreviewWitness = null
        pendingScrollPreviewRelease = null
        scrollSeekPhase = ScrollSeekPhase.PREVIEWING
        seekPreview = progress
    }

    fun reportChapterCompletion() {
        if (atChapterEnd && !chapterCompletionReported) {
            chapterCompletionReported = true
            onChapterCompleted(currentChapterId)
        }
    }

    fun selectAdjacentChapter(direction: Int): Boolean {
        if (direction > 0) reportChapterCompletion()
        val target = chapters.getOrNull(currentChapterIndex + direction) ?: return false
        returnAnchorState.ordinaryAdvance()
        onSelectChapter(target)
        return true
    }

    fun turnRenderedPage(direction: Int): Boolean {
        if (effectivePresentation == ReaderPresentation.SCROLL || seekPreview != null || pageLayout.pages.isEmpty()) return false
        val target = renderedPageIndex + if (direction < 0) -pageStep else pageStep
        if (target in pageLayout.pages.indices) {
            pageTurnGeneration += 1
            pageTurnTargetIndex = target
            commitPage(target)
            return true
        }
        return selectAdjacentChapter(direction)
    }
    fun pageTarget(direction: Int): Int? =
        (renderedPageIndex + if (direction < 0) -pageStep else pageStep).takeIf(pageLayout.pages.indices::contains)

    fun updatePageDrag(dragAmount: Float) {
        if (!pageDragInteractive || pageDragSettling || seekPreview != null || pageLayout.pages.isEmpty()) return
        val proposed = (pageDragOffsetPx + dragAmount).coerceIn(-viewportSize.width.toFloat(), viewportSize.width.toFloat())
        val direction = if (proposed < 0f) 1 else -1
        pageDragOffsetPx = if (proposed == 0f || pageTarget(direction) != null) proposed else 0f
    }

    fun settlePageDrag(commit: Boolean) {
        if (pageDragSettling || pageDragOffsetPx == 0f) return
        val openingOffset = pageDragOffsetPx
        val direction = if (openingOffset < 0f) 1 else -1
        val targetPage = pageTarget(direction)
        val shouldCommit = commit && targetPage != null
        val targetOffset = if (shouldCommit) {
            if (direction > 0) -viewportSize.width.toFloat() else viewportSize.width.toFloat()
        } else {
            0f
        }
        pageDragSettling = true
        pageDragScope.launch {
            if (instantPageMotion) {
                pageDragOffsetPx = targetOffset
            } else {
                animateTsuyomiPageTurn(
                    from = openingOffset,
                    to = targetOffset,
                    onFrame = { pageDragOffsetPx = it },
                )
            }
            if (shouldCommit) {
                pageTurnTargetIndex = -1
                commitPage(requireNotNull(targetPage))
            }
            pageDragOffsetPx = 0f
            pageDragSettling = false
        }
    }

    fun updateSettings(action: ReaderSettingsAction) {
        val updated = when (action) {
            is ReaderSettingsAction.FontSize -> settings.copy(fontSize = action.value)
            is ReaderSettingsAction.LineHeight -> settings.copy(lineHeight = action.value)
            is ReaderSettingsAction.HorizontalMargin -> settings.copy(horizontalMargin = action.value)
            is ReaderSettingsAction.ParagraphSpacing -> settings.copy(paragraphSpacing = action.value)
            is ReaderSettingsAction.FontFamily -> settings.copy(fontFamily = action.value)
            is ReaderSettingsAction.FontWeight -> settings.copy(fontWeight = action.value)
            is ReaderSettingsAction.LetterSpacing -> settings.copy(letterSpacing = action.value)
            is ReaderSettingsAction.FirstLineIndent -> settings.copy(firstLineIndent = action.value)
            is ReaderSettingsAction.VerticalMargin -> settings.copy(verticalMargin = action.value)
            is ReaderSettingsAction.TextAlignment -> settings.copy(textAlignment = action.value)
            is ReaderSettingsAction.ForegroundColor -> settings.copy(foregroundColor = action.value)
            is ReaderSettingsAction.BackgroundColor -> settings.copy(backgroundColor = action.value)
            is ReaderSettingsAction.Theme -> settings.copy(theme = action.value)
            is ReaderSettingsAction.Flow -> settings.copy(flow = action.value)
            is ReaderSettingsAction.LockPortrait -> settings.copy(lockPortrait = action.value)
            is ReaderSettingsAction.ProgressVisible -> settings.copy(progressVisible = action.value)
            is ReaderSettingsAction.Immersive -> settings.copy(immersive = action.value)
            is ReaderSettingsAction.KeepAwake -> settings.copy(keepAwake = action.value)
            is ReaderSettingsAction.VolumePaging -> settings.copy(volumePaging = action.value)
        }
        settings = updated
        onReaderAppearanceChanged(updated.theme, updated.foregroundColor, updated.backgroundColor)
        if (action is ReaderSettingsAction.Flow) {
            onFlowOverrideChanged(updated.flow.portableValue())
        } else {
            onPreferencesChanged(
                preferences.copy(
                    flow = null,
                    fontScale = (updated.fontSize / 18f).toDouble(),
                    lineHeight = updated.lineHeight.toDouble(),
                    horizontalMargin = updated.horizontalMargin.toDouble(),
                    paragraphSpacing = updated.paragraphSpacing.toDouble(),
                    fontFamily = updated.fontFamily,
                    fontWeight = updated.fontWeight,
                    letterSpacing = updated.letterSpacing.toDouble(),
                    firstLineIndent = updated.firstLineIndent.toDouble(),
                    verticalMargin = updated.verticalMargin.toDouble(),
                    textAlignment = updated.textAlignment,
                    foregroundColor = updated.foregroundColor,
                    backgroundColor = updated.backgroundColor,
                    lockPortrait = updated.lockPortrait,
                    theme = updated.theme.portableValue(),
                    progressVisible = updated.progressVisible,
                    immersive = updated.immersive,
                    keepAwake = updated.keepAwake,
                    volumePaging = updated.volumePaging,
                ),
            )
        }
    }

    fun followGlobalFlow() {
        onFlowOverrideChanged(null)
        settings = settings.copy(flow = preferences.flow.readerFlow())
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
    LaunchedEffect(effectivePresentation) {
        session.switchPresentation(effectivePresentation)
        if (effectivePresentation != ReaderPresentation.SCROLL && scrollSeekPhase == ScrollSeekPhase.RESTORING) {
            scrollSeekPhase = ScrollSeekPhase.IDLE
        }
    }
    LaunchedEffect(
        document.contentId,
        document.revision,
        effectivePresentation,
        viewportSize,
        settings.fontSize,
        settings.lineHeight,
        settings.horizontalMargin,
        settings.paragraphSpacing,
        settings.fontFamily,
        settings.fontWeight,
        settings.letterSpacing,
        settings.firstLineIndent,
        settings.verticalMargin,
        settings.textAlignment,
    ) {
        if (seekPreview != null) {
            scrollPreviewSession?.invalidateIfStale(previewEpochs, previewEpochs.layoutEpoch)
            cancelSeek()
        }
    }
    LaunchedEffect(settings.keepAwake, overlay) {
        if (overlay == null) readerFocusRequester.requestFocus()
    }
    LaunchedEffect(returnAnchorState, document.contentId, document.revision, restorationGeneration) {
        returnAnchorState.navigationMounted()
    }
    LaunchedEffect(settings.progressVisible) {
        if (!settings.progressVisible) readingInfoHeightPx = 0
    }

    BackHandler(enabled = overlay != null) { overlay = null }
    BackHandler(enabled = seekPreview != null) { cancelSeek() }
    BackHandler(enabled = overlay == null && seekPreview == null && chromeVisible) { chromeVisible = false }

    val previewPagePosition = seekPreview?.let {
        ReaderPosition.fromSeekProgress(it, pageLayout.pages.size.coerceAtLeast(1), pageStep)
    }
    val previewPageIndex = previewPagePosition?.page?.minus(1)
    val previewBlockIndex = seekPreview?.let { progressToIndex(it, document.blocks.size) }
    val displayBlockIndex = if (effectiveFlow == ReaderFlow.SCROLL) previewBlockIndex ?: renderedBlockIndex else renderedBlockIndex
    val displayPageIndex = if (effectiveFlow == ReaderFlow.SCROLL) 0 else previewPageIndex ?: renderedPageIndex
    val pageTurnMotionToken = if (seekPreview == null && pageTurnTargetIndex == displayPageIndex) {
        pageTurnGeneration
    } else {
        0L
    }
    val displayPosition = when {
        seekPreview == null -> readerPosition
        effectiveFlow == ReaderFlow.SCROLL -> ReaderPosition.fromPageIndex(displayBlockIndex, document.blocks.size)
        else -> previewPagePosition ?: readerPosition
    }
    val contentInsets = if (settings.immersive) WindowInsets.displayCutout else WindowInsets.safeDrawing
    val chromeVisibleForLayout = chromeVisible || overlay != null
    val bottomChromeOverlapPx = if (chromeVisibleForLayout) {
        (bottomChromeHeightPx - readingInfoHeightPx).coerceAtLeast(0)
    } else {
        0
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(readerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (overlay != null) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.VolumeUp -> if (settings.volumePaging) {
                        if (event.type == KeyEventType.KeyUp) turnRenderedPage(-1)
                        true
                    } else {
                        false
                    }
                    Key.VolumeDown -> if (settings.volumePaging) {
                        if (event.type == KeyEventType.KeyUp) turnRenderedPage(1)
                        true
                    } else {
                        false
                    }
                    Key.DirectionLeft -> event.type == KeyEventType.KeyUp && turnRenderedPage(-1)
                    Key.DirectionRight -> event.type == KeyEventType.KeyUp && turnRenderedPage(1)
                    else -> false
                }
            },
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(contentInsets),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewportSize = it }
                            .testTag("reader-content-surface")
                            .readerTapZones(
                                pagingEnabled = effectiveFlow != ReaderFlow.SCROLL && seekPreview == null,
                                onPrevious = { turnRenderedPage(-1) },
                                onCenter = { chromeVisible = !chromeVisible },
                                onNext = { turnRenderedPage(1) },
                                onDrag = ::updatePageDrag,
                                onDragEnd = {
                                    settlePageDrag(
                                        abs(pageDragOffsetPx) >= viewportSize.width * ReaderPageSwipeThresholdFraction,
                                    )
                                },
                                onDragCancel = { settlePageDrag(commit = false) },
                            ),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        key(session, previewLayoutKey, density.density, density.fontScale) {
                            ReaderDocumentBody(
                                document = document,
                                flow = effectiveFlow,
                                settings = settings,
                                settledIndex = renderedBlockIndex,
                                settledCodePointOffset = renderedCodePointOffset,
                                viewportWidthPx = viewportSize.width,
                                displayIndex = displayBlockIndex,
                                pageLayout = pageLayout,
                                displayPageIndex = displayPageIndex,
                                pageTurnMotionToken = pageTurnMotionToken,
                                pageDragOffsetPx = pageDragOffsetPx,
                                dragPageIndex = when {
                                    pageDragOffsetPx < 0f -> pageTarget(1)
                                    pageDragOffsetPx > 0f -> pageTarget(-1)
                                    else -> null
                                },
                                seeking = seekPreview != null,
                                scrollSeekPhase = scrollSeekPhase,
                                onSettledPositionChanged = ::commitPosition,
                                onScrollPreviewRestored = { scrollSeekPhase = ScrollSeekPhase.IDLE },
                                onScrollPreviewVisualCommit = ::recordScrollPreviewVisualCommit,
                                onScrollAtEndChanged = { scrollViewportAtEnd = it },
                                imageStates = imageStates,
                                onImageVisible = onImageVisible,
                                onRetryImage = onRetryImage,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    if (returnAnchorState.anchor != null && overlay == null) {
                        TsuyomiExtendedFab(
                            text = stringResource(R.string.reader_return_to_origin),
                            imageVector = TsuyomiIcons.Recent,
                            contentDescription = stringResource(R.string.reader_return_to_origin),
                            onClick = { returnAnchorState.returnToOrigin(onReturnToOrigin) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = TsuyomiSpacing.Md,
                                    bottom = with(density) { bottomChromeOverlapPx.toDp() } + TsuyomiSpacing.Md,
                                )
                                .testTag("reader-return-origin"),
                        )
                    }
                }
                ReaderReadingInfoBar(
                    chapterTitle = document.title,
                    progress = displayPosition.progress,
                    position = displayPosition,
                    continuous = effectivePresentation == ReaderPresentation.SCROLL,
                    visible = settings.progressVisible,
                    modifier = Modifier.onSizeChanged { readingInfoHeightPx = it.height },
                )
            }
            TsuyomiVisibility(
                visible = chromeVisible || overlay != null,
                modifier = Modifier.align(Alignment.TopCenter),
                enterFrom = TsuyomiVisibilityEdge.TOP,
                exitTo = TsuyomiVisibilityEdge.TOP,
            ) {
                ReaderTopChrome(
                    chapterTitle = document.title,
                    bookmarked = bookmarks.any { it.namesSameBookmarkPositionAs(committedBookmarkLocator) },
                    onUp = onNavigateUp,
                    onToggleBookmark = { onToggleBookmark(committedBookmarkLocator) },
                    onOpenSearch = {
                        auxiliarySheetState.select(ReaderAuxiliaryTab.SEARCH)
                        overlay = ReaderOverlay.AUXILIARY
                    },
                )
            }
            TsuyomiVisibility(
                visible = chromeVisible || overlay != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomChromeHeightPx = it.height },
                enterFrom = TsuyomiVisibilityEdge.BOTTOM,
                exitTo = TsuyomiVisibilityEdge.BOTTOM,
            ) {
                ReaderBottomChrome(
                    chapterIndex = currentChapterIndex,
                    chapterCount = chapters.size,
                    chapterProgress = if (effectivePresentation == ReaderPresentation.SCROLL) {
                        readerPosition.progress
                    } else {
                        readerPosition.seekProgress
                    },
                    position = readerPosition,
                    continuousSeek = effectivePresentation == ReaderPresentation.SCROLL,
                    seekPreview = seekPreview,
                    onSeekPreview = { target ->
                        if (effectivePresentation == ReaderPresentation.SCROLL) {
                            startScrollPreview(target)
                        } else {
                            pageTurnTargetIndex = -1
                            seekPreview = target
                        }
                    },
                    onSeekCommit = { targetProgress ->
                        if (effectivePresentation == ReaderPresentation.SCROLL) {
                            pendingScrollPreviewRelease = targetProgress
                            releaseScrollPreviewIfReady()
                        } else if (seekPreview != null) {
                            val target = ReaderPosition.fromSeekProgress(
                                targetProgress,
                                pageLayout.pages.size.coerceAtLeast(1),
                                pageStep,
                            )
                            val origin = session.previewPositionAtBlock(renderedBlockIndex, renderedCodePointOffset).locator
                            val committed = commitPage(target.page - 1, ordinaryAdvance = false)
                            if (committed != null) returnAnchorState.commitInternalJump(origin, committed)
                            seekPreview = null
                        }
                    },
                    onPreviousChapter = { selectAdjacentChapter(-1) },
                    onOpenContents = { overlay = ReaderOverlay.AUXILIARY },
                    onOpenSettings = { overlay = ReaderOverlay.SETTINGS },
                    onNextChapter = { selectAdjacentChapter(1) },
                )
            }
        }
    }

    when (overlay) {
        ReaderOverlay.AUXILIARY -> ReaderAuxiliarySheet(
            state = auxiliarySheetState,
            chapters = chapters,
            currentChapterId = currentChapterId,
            bookmarks = bookmarks,
            onDismiss = { overlay = null },
            onSelectChapter = { chapter ->
                returnAnchorState.requestExternalJump(committedBookmarkLocator)
                onSelectChapter(chapter)
                overlay = null
            },
            onSelectBookmark = { locator ->
                if (!locator.namesSameBookmarkPositionAs(committedBookmarkLocator)) {
                    returnAnchorState.requestExternalJump(committedBookmarkLocator)
                }
                onSelectBookmark(locator)
                overlay = null
            },
            onRemoveBookmark = onRemoveBookmark,
        )
        ReaderOverlay.SETTINGS -> ReaderSettingsSheet(
            state = settings,
            effectiveFlow = effectiveFlow,
            dualPageEligible = dualPageEligible,
            hasFlowOverride = hasFlowOverride,
            onAction = ::updateSettings,
            onFollowGlobalFlow = ::followGlobalFlow,
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
    settledCodePointOffset: Int,
    viewportWidthPx: Int,
    displayIndex: Int,
    pageLayout: ReaderPageLayout,
    displayPageIndex: Int,
    pageTurnMotionToken: Long,
    pageDragOffsetPx: Float,
    dragPageIndex: Int?,
    seeking: Boolean,
    scrollSeekPhase: ScrollSeekPhase,
    onSettledPositionChanged: (Int, Int) -> Unit,
    onScrollPreviewRestored: () -> Unit,
    onScrollPreviewVisualCommit: () -> Unit,
    onScrollAtEndChanged: (Boolean) -> Unit,
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
            settledCodePointOffset = settledCodePointOffset,
            viewportWidthPx = viewportWidthPx,
            displayIndex = displayIndex,
            seeking = seeking,
            seekPhase = scrollSeekPhase,
            onSettledPositionChanged = onSettledPositionChanged,
            onPreviewRestored = onScrollPreviewRestored,
            onPreviewVisualCommit = onScrollPreviewVisualCommit,
            onAtEndChanged = onScrollAtEndChanged,
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
            pageTurnMotionToken = pageTurnMotionToken,
            pageDragOffsetPx = pageDragOffsetPx,
            dragPageIndex = dragPageIndex,
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
            pageTurnMotionToken = pageTurnMotionToken,
            pageDragOffsetPx = pageDragOffsetPx,
            dragPageIndex = dragPageIndex,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            modifier = modifier,
        )
    }
}

private data class ScrollViewport(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

private data class ScrollSemanticPosition(val blockIndex: Int, val codePointOffset: Int)

@Composable
private fun ScrollReaderBody(
    document: ReaderDocument,
    settings: ReaderSettingsUiState,
    settledIndex: Int,
    settledCodePointOffset: Int,
    viewportWidthPx: Int,
    displayIndex: Int,
    seeking: Boolean,
    seekPhase: ScrollSeekPhase,
    onSettledPositionChanged: (Int, Int) -> Unit,
    onPreviewRestored: () -> Unit,
    onPreviewVisualCommit: () -> Unit,
    onAtEndChanged: (Boolean) -> Unit,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val horizontalMarginPx = with(density) { settings.horizontalMargin.dp.roundToPx() }
    val verticalMarginPx = with(density) { settings.verticalMargin.dp.roundToPx() }
    val textWidth = viewportWidthPx - horizontalMarginPx * 2
    if (textWidth <= 0) {
        Box(modifier)
        return
    }
    val textMeasurer = rememberTextMeasurer(cacheSize = 1)
    val block = remember { document.blocks[settledIndex] }
    val initialStyle = if (block is ReaderBlock.Heading) readerHeadingStyle(settings) else readerBodyStyle(settings)
    val initialOffset = remember {
        val text = when (block) {
            is ReaderBlock.Paragraph -> block.text
            is ReaderBlock.Heading -> block.text
            is ReaderBlock.Image -> null
        }
        if (text == null || settledCodePointOffset == 0) {
            0
        } else {
            val layout = textMeasurer.measure(
                text = text,
                style = initialStyle,
                constraints = Constraints(maxWidth = textWidth),
            )
            val offset = text.offsetByCodePoints(0, settledCodePointOffset.coerceIn(0, text.codePointCount(0, text.length)))
            val top = layout.getLineTop(layout.getLineForOffset(offset)).roundToInt()
            if (top > 0) top + verticalMarginPx else 0
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = settledIndex,
        initialFirstVisibleItemScrollOffset = initialOffset,
    )
    val textLayouts = remember { mutableStateMapOf<Int, TextLayoutResult>() }
    var openingViewport by remember(listState) { mutableStateOf<ScrollViewport?>(null) }
    LaunchedEffect(seekPhase, seeking, displayIndex) {
        when (seekPhase) {
            ScrollSeekPhase.PREVIEWING -> if (seeking) {
                if (openingViewport == null) {
                    openingViewport = ScrollViewport(
                        firstVisibleItemIndex = listState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                    )
                }
                listState.scrollToItem(displayIndex)
                onPreviewVisualCommit()
            }
            ScrollSeekPhase.RESTORING -> {
                val opening = openingViewport ?: ScrollViewport(settledIndex, 0)
                listState.scrollToItem(opening.firstVisibleItemIndex, opening.firstVisibleItemScrollOffset)
                openingViewport = null
                onPreviewRestored()
            }
            ScrollSeekPhase.IDLE -> openingViewport = null
        }
    }
    LaunchedEffect(listState, seekPhase) {
        if (seekPhase == ScrollSeekPhase.IDLE) {
            snapshotFlow {
                if (listState.isScrollInProgress) return@snapshotFlow null
                val index = listState.firstVisibleItemIndex
                if (document.blocks[index] is ReaderBlock.Image) {
                    ScrollSemanticPosition(index, 0)
                } else {
                    val layout = textLayouts[index] ?: return@snapshotFlow null
                    val visibleTop = (listState.firstVisibleItemScrollOffset + listState.layoutInfo.viewportStartOffset)
                        .coerceAtLeast(0).toFloat()
                    val lineStart = layout.getLineStart(layout.getLineForVerticalPosition(visibleTop))
                    ScrollSemanticPosition(index, layout.layoutInput.text.text.codePointCount(0, lineStart))
                }
            }
                .filterNotNull()
                .distinctUntilChanged()
                // Presentation/restoration is not a user navigation or a durable progress write.
                .drop(1)
                .collect { position -> onSettledPositionChanged(position.blockIndex, position.codePointOffset) }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
            layout.totalItemsCount > 0 &&
                lastVisible?.index == layout.totalItemsCount - 1 &&
                lastVisible.offset + lastVisible.size <= layout.viewportEndOffset
        }.distinctUntilChanged().collect(onAtEndChanged)
    }
    LazyColumn(
        state = listState,
        userScrollEnabled = seekPhase == ScrollSeekPhase.IDLE,
        modifier = modifier.testTag("reader-document-scroll"),
        contentPadding = PaddingValues(
            horizontal = settings.horizontalMargin.dp,
            vertical = settings.verticalMargin.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacing.dp),
    ) {
        itemsIndexed(document.blocks, key = { _, block -> block.blockId }) { index, block ->
            DisposableEffect(index) {
                onDispose { textLayouts.remove(index) }
            }
            ReaderBlockView(
                block = block,
                settings = settings,
                imageState = imageStates[block.blockId],
                onImageVisible = onImageVisible,
                onRetryImage = onRetryImage,
                onTextLayout = { textLayouts[index] = it },
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
    pageTurnMotionToken: Long,
    pageDragOffsetPx: Float,
    dragPageIndex: Int?,
    dual: Boolean,
    modifier: Modifier,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
) {
    if (pageDragOffsetPx != 0f && dragPageIndex != null) {
        BoxWithConstraints(modifier.clipToBounds()) {
            val widthPx = constraints.maxWidth
            val adjacentOrigin = if (dragPageIndex > displayPageIndex) widthPx else -widthPx
            ReaderPageSpread(
                document = document,
                settings = settings,
                pageLayout = pageLayout,
                pageIndex = displayPageIndex,
                dual = dual,
                imageStates = imageStates,
                onImageVisible = onImageVisible,
                onRetryImage = onRetryImage,
                modifier = Modifier.fillMaxSize().offset {
                    IntOffset(pageDragOffsetPx.roundToInt(), 0)
                },
            )
            ReaderPageSpread(
                document = document,
                settings = settings,
                pageLayout = pageLayout,
                pageIndex = dragPageIndex,
                dual = dual,
                imageStates = imageStates,
                onImageVisible = onImageVisible,
                onRetryImage = onRetryImage,
                modifier = Modifier.fillMaxSize().offset {
                    IntOffset(adjacentOrigin + pageDragOffsetPx.roundToInt(), 0)
                },
            )
        }
        return
    }

    TsuyomiDirectionalAnimatedContent(
        targetState = ReaderPagePresentation(displayPageIndex, pageTurnMotionToken),
        transitionDirection = { initial, target ->
            when {
                target.motionToken == 0L || initial.pageIndex == target.pageIndex -> null
                target.pageIndex > initial.pageIndex -> TsuyomiContentDirection.FORWARD
                else -> TsuyomiContentDirection.BACKWARD
            }
        },
        modifier = modifier,
    ) { presentation ->
        ReaderPageSpread(
            document = document,
            settings = settings,
            pageLayout = pageLayout,
            pageIndex = presentation.pageIndex,
            dual = dual,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ReaderPageSpread(
    document: ReaderDocument,
    settings: ReaderSettingsUiState,
    pageLayout: ReaderPageLayout,
    pageIndex: Int,
    dual: Boolean,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier.padding(
            horizontal = settings.horizontalMargin.dp,
            vertical = settings.verticalMargin.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(settings.horizontalMargin.dp),
    ) {
        repeat(if (dual) 2 else 1) { column ->
            val page = pageLayout.pages.getOrNull(pageIndex + column)
            Column(Modifier.weight(1f).testTag("reader-page-column-$column")) {
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
    onTextLayout: (TextLayoutResult) -> Unit,
    imageState: CoverUiState? = null,
    onImageVisible: (ReaderBlock.Image) -> Unit = {},
    onRetryImage: (ReaderBlock.Image) -> Unit = {},
) {
    when (block) {
        is ReaderBlock.Paragraph -> Text(block.text, modifier = modifier, style = readerBodyStyle(settings), onTextLayout = onTextLayout)
        is ReaderBlock.Heading -> Text(block.text, modifier = modifier, style = readerHeadingStyle(settings), onTextLayout = onTextLayout)
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

private data class ReaderPagePresentation(
    val pageIndex: Int,
    val motionToken: Long,
)

private fun Modifier.readerTapZones(
    pagingEnabled: Boolean,
    onPrevious: () -> Boolean,
    onCenter: () -> Unit,
    onNext: () -> Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier = semantics {
    onClick(label = "显示或隐藏阅读控制") {
        onCenter()
        true
    }
    customActions = listOf(
        CustomAccessibilityAction("上一页") { onPrevious() },
        CustomAccessibilityAction("下一页") { onNext() },
    )
}.then(
    if (pagingEnabled) {
        Modifier.pointerInput(onDrag, onDragEnd, onDragCancel) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    onDrag(dragAmount)
                    change.consume()
                },
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
        }
    } else {
        Modifier
    },
).pointerInput(onPrevious, onCenter, onNext) {
    detectTapGestures { position ->
        val fraction = (position.x / size.width).coerceIn(0f, 1f)
        when {
            fraction < 0.3f -> if (!onPrevious()) onCenter()
            fraction > 0.7f -> if (!onNext()) onCenter()
            else -> onCenter()
        }
    }
}

private const val ReaderPageSwipeThresholdFraction = 0.18f

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
            state.volumePaging,
            state.theme.name,
            state.fontFamily,
            state.fontWeight,
            state.letterSpacing,
            state.firstLineIndent,
            state.verticalMargin,
            state.textAlignment,
            state.foregroundColor.orEmpty(),
            state.backgroundColor.orEmpty(),
        )
    },
    restore = { values ->
        ReaderSettingsUiState(
            fontSize = values[0] as Float,
            lineHeight = values[1] as Float,
            horizontalMargin = values[2] as Float,
            paragraphSpacing = values[3] as Float,
            flow = values.getOrNull(4)?.let { runCatching { ReaderFlow.valueOf(it as String) }.getOrNull() }
                ?: ReaderFlow.PAGED,
            lockPortrait = values.getOrNull(5) as? Boolean ?: false,
            progressVisible = values.getOrNull(6) as? Boolean ?: true,
            immersive = values.getOrNull(7) as? Boolean ?: false,
            keepAwake = values.getOrNull(8) as? Boolean ?: true,
            volumePaging = values.getOrNull(9) as? Boolean ?: true,
            theme = values.getOrNull(10)?.let { runCatching { ReaderTheme.valueOf(it as String) }.getOrNull() }
                ?: ReaderTheme.PAPER,
            fontFamily = values.getOrNull(11) as? String ?: "system",
            fontWeight = (values.getOrNull(12) as? Int).takeIf { it in setOf(400, 500) } ?: 400,
            letterSpacing = values.getOrNull(13) as? Float ?: 0f,
            firstLineIndent = values.getOrNull(14) as? Float ?: 0f,
            verticalMargin = values.getOrNull(15) as? Float ?: 24f,
            textAlignment = values.getOrNull(16) as? String ?: "start",
            foregroundColor = (values.getOrNull(17) as? String)?.takeIf { it.isNotEmpty() },
            backgroundColor = (values.getOrNull(18) as? String)?.takeIf { it.isNotEmpty() },
        )
    },
)

private fun readerColorScheme(
    theme: ReaderTheme,
    foregroundColor: String?,
    backgroundColor: String?,
) = when (theme) {
    ReaderTheme.PAPER -> TsuyomiLightColorScheme.copy(
        background = Color(0xFFFFFBF2),
        surface = Color(0xFFFFFBF2),
        onBackground = Color(0xFF292723),
        onSurface = Color(0xFF292723),
        onSurfaceVariant = Color(0xFF5F5B53),
    )
    ReaderTheme.WARM_GRAY -> TsuyomiLightColorScheme.copy(
        background = Color(0xFFF0EEE9),
        surface = Color(0xFFF0EEE9),
        onBackground = Color(0xFF292825),
        onSurface = Color(0xFF292825),
        onSurfaceVariant = Color(0xFF5D5A54),
    )
    ReaderTheme.NIGHT_INK -> TsuyomiDarkColorScheme.copy(
        background = Color(0xFF202124),
        surface = Color(0xFF202124),
        onBackground = Color(0xFFE8EAED),
        onSurface = Color(0xFFE8EAED),
        onSurfaceVariant = Color(0xFFBDC1C6),
    )
    ReaderTheme.BLACK -> TsuyomiDarkColorScheme.copy(
        background = Color.Black,
        surface = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFD0D0D0),
    )
    ReaderTheme.INK_GREEN -> TsuyomiLightColorScheme.copy(
        background = Color(0xFFEFF4E8),
        surface = Color(0xFFEFF4E8),
        onBackground = Color(0xFF1B2A1F),
        onSurface = Color(0xFF1B2A1F),
        onSurfaceVariant = Color(0xFF506055),
    )
}.let { themed ->
    val background = backgroundColor.readerColor() ?: themed.background
    val foreground = foregroundColor.readerColor() ?: themed.onBackground
    themed.copy(
        background = background,
        onBackground = foreground,
    )
}

private fun String?.readerColor(): Color? =
    this?.takeIf { it.matches(Regex("^#[0-9A-F]{6}$")) }
        ?.substring(1)
        ?.toLongOrNull(16)
        ?.let { rgb -> Color(0xFF000000L or rgb) }

private fun readerTheme(value: String?): ReaderTheme = when (value) {
    "warmGray" -> ReaderTheme.WARM_GRAY
    "nightInk" -> ReaderTheme.NIGHT_INK
    "black" -> ReaderTheme.BLACK
    "inkGreen" -> ReaderTheme.INK_GREEN
    else -> ReaderTheme.PAPER
}

private fun String?.readerFlow(): ReaderFlow = when (this) {
    "scroll" -> ReaderFlow.SCROLL
    "dual" -> ReaderFlow.DUAL
    else -> ReaderFlow.PAGED
}

private fun ReaderFlow.presentation(): ReaderPresentation = when (this) {
    ReaderFlow.SCROLL -> ReaderPresentation.SCROLL
    ReaderFlow.PAGED -> ReaderPresentation.PAGED
    ReaderFlow.DUAL -> ReaderPresentation.DUAL_PAGE
}

private fun ReaderPresentation.flow(): ReaderFlow = when (this) {
    ReaderPresentation.SCROLL -> ReaderFlow.SCROLL
    ReaderPresentation.PAGED -> ReaderFlow.PAGED
    ReaderPresentation.DUAL_PAGE -> ReaderFlow.DUAL
}

private fun ReaderFlow.portableValue(): String = when (this) {
    ReaderFlow.SCROLL -> "scroll"
    ReaderFlow.PAGED -> "paged"
    ReaderFlow.DUAL -> "dual"
}

private fun ReaderTheme.portableValue(): String = when (this) {
    ReaderTheme.PAPER -> "paper"
    ReaderTheme.WARM_GRAY -> "warmGray"
    ReaderTheme.NIGHT_INK -> "nightInk"
    ReaderTheme.BLACK -> "black"
    ReaderTheme.INK_GREEN -> "inkGreen"
}


@Composable
private fun FrozenEInkReaderSurface(
    document: ReaderDocument,
    restoredLocator: ReaderLocator?,
    restorationGeneration: Long,
    onLocatorChanged: (ReaderLocator, LocatorPrecision) -> Unit,
    modifier: Modifier,
    preferences: PortableReaderPreferences,
) {
    val session = remember(
        document.sourceId,
        document.remoteBookId,
        document.contentId,
        document.revision,
        restorationGeneration,
    ) {
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
            onTextLayout = {},
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${index + 1}/${document.blocks.size}")
            Text("冻结的 E-ink 分页阅读器")
        }
    }
    LaunchedEffect(session, index) {
        val position = session.navigateToBlock(index)
        onLocatorChanged(session.capture(), position.precision)
    }
}
