/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.HostMediaImage
import org.tsuyomi.core.ui.components.EInkGrayscaleColorFilter
import org.tsuyomi.core.ui.components.TsuyomiContentDirection
import org.tsuyomi.core.ui.components.TsuyomiDirectionalAnimatedContent
import org.tsuyomi.core.ui.components.TsuyomiExtendedFab
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
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
import org.tsuyomi.shared.sourcecontract.ReaderInlineKind
import org.tsuyomi.shared.sourcecontract.ReaderLinkTarget
import org.tsuyomi.shared.sourcecontract.targetForAnchor
import org.tsuyomi.shared.sourcecontract.targetForNote
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.canonicalAnchorText
import org.tsuyomi.core.ui.theme.TsuyomiEInkColorScheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderNavigationTarget
import org.tsuyomi.shared.sourcecontract.ReaderReadingDirection
import org.tsuyomi.shared.sourcecontract.ReaderDocumentPresentation
private class ReaderImageVisibilityOwner(private val scope: CoroutineScope) {
    private class MountedImage(val block: ReaderBlock.Image, var count: Int, var release: (ReaderBlock.Image) -> Unit)
    private val mounted = HashMap<String, MountedImage>()
    private var closed = false
    private var hiddenRelease: Job? = null

    fun mount(block: ReaderBlock.Image, request: (ReaderBlock.Image) -> Unit, release: (ReaderBlock.Image) -> Unit) {
        if (closed) return
        val previous = mounted[block.blockId]
        if (previous != null) {
            if (previous.count == 0) {
                previous.release = release
                request(block)
            }
            previous.count++
        } else {
            mounted[block.blockId] = MountedImage(block, 1, release)
            request(block)
        }
    }

    fun unmount(block: ReaderBlock.Image) {
        if (closed) return
        val current = mounted[block.blockId] ?: return
        current.count--
        if (current.count == 0 && hiddenRelease == null) {
            hiddenRelease = scope.launch {
                withFrameNanos { }
                hiddenRelease = null
                flushHidden()
            }
        }
    }

    // A same-frame reflow replacement is not a resource leaving the viewport.
    private fun flushHidden() {
        if (closed) return
        val iterator = mounted.values.iterator()
        while (iterator.hasNext()) {
            val current = iterator.next()
            if (current.count == 0) {
                iterator.remove()
                current.release(current.block)
            }
        }
    }

    fun close() {
        closed = true
        hiddenRelease?.cancel()
        mounted.values.forEach { it.release(it.block) }
        mounted.clear()
    }
}

private val LocalReaderImageVisibilityOwner = staticCompositionLocalOf<ReaderImageVisibilityOwner?> { null }
private val LocalReaderImageHidden = staticCompositionLocalOf<(ReaderBlock.Image) -> Unit> { {} }


@Stable
private class ReaderArtworkZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var fittedSize: IntSize = IntSize.Zero

    val zoomed: Boolean get() = scale > 1f

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun transform(zoom: Float, pan: Offset, centroid: Offset, viewport: IntSize) {
        val nextScale = (scale * zoom).coerceIn(1f, 5f)
        val focus = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
        val nextOffset = (offset - focus) * (nextScale / scale) + focus + pan
        val extent = fittedSize.takeUnless { it.width <= 0 || it.height <= 0 } ?: viewport
        val limitX = ((extent.width * nextScale - viewport.width) / 2f).coerceAtLeast(0f)
        val limitY = ((extent.height * nextScale - viewport.height) / 2f).coerceAtLeast(0f)
        offset = Offset(nextOffset.x.coerceIn(-limitX, limitX), nextOffset.y.coerceIn(-limitY, limitY))
        scale = nextScale
    }
}

/** Only artwork owns multitouch; an unzoomed one-finger gesture remains with scroll/page input. */
private fun Modifier.readerArtworkGestures(zoom: ReaderArtworkZoom, enabled: Boolean): Modifier =
    if (!enabled) this else pointerInput(zoom) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (zoom.zoomed) down.consume()
            var transforming = zoom.zoomed
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) transforming = true
                if (transforming) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (centroid != Offset.Unspecified) {
                        zoom.transform(event.calculateZoom(), event.calculatePan(), centroid, size)
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

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
        returning = false
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
fun rememberReaderReturnAnchorState(bookIdentity: BookIdentity?): ReaderReturnAnchorState =
    remember(bookIdentity) { ReaderReturnAnchorState() }

@Composable
fun ReaderSurface(
    document: ReaderDocument,
    auxiliarySheetState: ReaderAuxiliarySheetState,
    restoredLocator: ReaderLocator?,
    restorationGeneration: Long,
    onLocatorChanged: (ReaderLocator, LocatorPrecision) -> Unit,
    navigationTargets: List<ReaderNavigationTarget>,
    currentContentId: String,
    bookmarks: List<ReaderLocator>,
    onToggleBookmark: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
    onSelectBookmark: (ReaderLocator) -> Unit,
    requestedFlow: String,
    hasFlowOverride: Boolean,
    onFlowOverrideChanged: (String?) -> Unit,
    onSelectContent: (ReaderNavigationTarget) -> Unit,
    onNavigateLink: (ReaderLinkTarget) -> Unit,
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
    onReturnToOrigin: (ReaderLocator) -> Unit,
    returnAnchorState: ReaderReturnAnchorState? = null,
    onImageHidden: (ReaderBlock.Image) -> Unit = {},
    onSelectAdjacentContent: ((ReaderNavigationTarget, Boolean) -> Unit)? = null,
    chapterNavigationEnabled: Boolean = true,
) {
    val effectiveReturnAnchorState = returnAnchorState
        ?: rememberReaderReturnAnchorState(document.identity.book)
    val imageVisibilityScope = rememberCoroutineScope()
    val imageVisibilityOwner = remember(document) { ReaderImageVisibilityOwner(imageVisibilityScope) }
    DisposableEffect(imageVisibilityOwner) { onDispose { imageVisibilityOwner.close() } }
    var activeTheme by remember(document.identity.book) { mutableStateOf(readerTheme(preferences.theme)) }
    var activeForegroundColor by remember(document.identity.book) { mutableStateOf(preferences.foregroundColor) }
    var activeBackgroundColor by remember(document.identity.book) { mutableStateOf(preferences.backgroundColor) }
    LaunchedEffect(preferences.theme, preferences.foregroundColor, preferences.backgroundColor) {
        activeTheme = readerTheme(preferences.theme)
        activeForegroundColor = preferences.foregroundColor
        activeBackgroundColor = preferences.backgroundColor
    }
    MaterialTheme(
        colorScheme = if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
            TsuyomiEInkColorScheme
        } else readerColorScheme(activeTheme, activeForegroundColor, activeBackgroundColor),
    ) {
        CompositionLocalProvider(
            LocalReaderImageVisibilityOwner provides imageVisibilityOwner,
            LocalReaderImageHidden provides onImageHidden,
        ) {
        ReaderSurfaceContent(
            document = document,
            auxiliarySheetState = auxiliarySheetState,
            restoredLocator = restoredLocator,
            restorationGeneration = restorationGeneration,
            onLocatorChanged = onLocatorChanged,
            navigationTargets = navigationTargets,
            currentContentId = currentContentId,
            bookmarks = bookmarks,
            onToggleBookmark = onToggleBookmark,
            onRemoveBookmark = onRemoveBookmark,
            onSelectBookmark = onSelectBookmark,
            onReturnToOrigin = onReturnToOrigin,
            returnAnchorState = effectiveReturnAnchorState,
            requestedFlow = requestedFlow,
            hasFlowOverride = hasFlowOverride,
            onFlowOverrideChanged = onFlowOverrideChanged,
            onSelectContent = onSelectContent,
            onSelectAdjacentContent = onSelectAdjacentContent,
            chapterNavigationEnabled = chapterNavigationEnabled,
            onNavigateLink = onNavigateLink,
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
    navigationTargets: List<ReaderNavigationTarget>,
    currentContentId: String,
    bookmarks: List<ReaderLocator>,
    onToggleBookmark: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
    onSelectBookmark: (ReaderLocator) -> Unit,
    onReturnToOrigin: (ReaderLocator) -> Unit,
    returnAnchorState: ReaderReturnAnchorState,
    requestedFlow: String,
    hasFlowOverride: Boolean,
    onFlowOverrideChanged: (String?) -> Unit,
    onSelectContent: (ReaderNavigationTarget) -> Unit,
    onSelectAdjacentContent: ((ReaderNavigationTarget, Boolean) -> Unit)?,
    chapterNavigationEnabled: Boolean,
    onNavigateLink: (ReaderLinkTarget) -> Unit,
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
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    var eInkFlow by rememberSaveable(document.identity.book.storageKey) { mutableStateOf<String?>(null) }
    val profileRequestedFlow = if (eInk) (eInkFlow ?: "paged").takeUnless { it == "scroll" } ?: "paged" else requestedFlow
    var settings by rememberSaveable(
        document.identity.book.storageKey,
        stateSaver = ReaderSettingsSaver,
    ) {
        mutableStateOf(preferences.readerSettings(profileRequestedFlow))
    }
    LaunchedEffect(preferences, profileRequestedFlow) {
        settings = preferences.readerSettings(profileRequestedFlow)
    }
    val requestedPresentation = if (eInk && settings.flow == ReaderFlow.SCROLL) {
        ReaderPresentation.PAGED
    } else {
        settings.flow.presentation()
    }
    var viewportSize by remember(document.identity) {
        mutableStateOf(IntSize.Zero)
    }
    val density = LocalDensity.current
    val artworkOnly = remember(document) {
        document.identity.book is BookIdentity.Local && document.blocks.all { it is ReaderBlock.Image }
    }
    val horizontalMarginPx = if (artworkOnly) 0 else with(density) { settings.horizontalMargin.dp.roundToPx() }
    val minimumDualWindowPx = with(density) { 600.dp.roundToPx() }
    val minimumDualColumnPx = with(density) { 260.dp.roundToPx() }
    val dualPageEligible =
        viewportSize.width >= minimumDualWindowPx &&
            viewportSize.width - horizontalMarginPx * 3 >= minimumDualColumnPx * 2
    val effectivePresentation = effectiveReaderPresentation(requestedPresentation, dualPageEligible)
    val effectiveFlow = effectivePresentation.flow()
    val session = remember(
        document,
        restorationGeneration,
    ) {
        ReaderDocumentSession(document, restoredLocator, effectivePresentation)
    }
    val previewLayoutKey = LayoutKey(
        "$eInk:${document.presentation}:${document.readingDirection}:${effectivePresentation.name}:" +
            "${viewportSize.width}x${viewportSize.height}:" +
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
    var renderedPositionIsExact by remember(session) { mutableStateOf(true) }
    var internalNavigationGeneration by remember(session) { mutableLongStateOf(0L) }
    var scrollStep by remember(session) { mutableStateOf(ReaderScrollStep(0L, 0)) }
    var pageTurnGeneration by remember(session) { mutableLongStateOf(0L) }
    var pageTurnTargetIndex by remember(session) { mutableIntStateOf(-1) }
    val rawLayoutItems = rememberReaderLayoutItems(document)
    val rightToLeft = document.readingDirection == ReaderReadingDirection.RTL
    val physicalForward = if (rightToLeft) -1 else 1
    val artworkZoom = remember(session, previewLayoutKey, internalNavigationGeneration) { ReaderArtworkZoom() }
    LaunchedEffect(renderedBlockIndex, renderedCodePointOffset) {
        if (effectivePresentation != ReaderPresentation.SCROLL) artworkZoom.reset()
    }
    val zoomInLabel = stringResource(R.string.reader_zoom_in_artwork)
    val zoomOutLabel = stringResource(R.string.reader_zoom_out_artwork)
    val resetArtworkLabel = stringResource(R.string.reader_reset_artwork)
    val panLeftLabel = stringResource(R.string.reader_pan_artwork_left)
    val panRightLabel = stringResource(R.string.reader_pan_artwork_right)
    val panUpLabel = stringResource(R.string.reader_pan_artwork_up)
    val panDownLabel = stringResource(R.string.reader_pan_artwork_down)
    val artworkActions = remember(artworkOnly, artworkZoom, viewportSize, zoomInLabel, zoomOutLabel,
        resetArtworkLabel, panLeftLabel, panRightLabel, panUpLabel, panDownLabel) {
        if (!artworkOnly) emptyList() else {
            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
            val step = minOf(viewportSize.width, viewportSize.height) / 8f
            listOf(
                CustomAccessibilityAction(zoomInLabel) { artworkZoom.transform(1.25f, Offset.Zero, center, viewportSize); true },
                CustomAccessibilityAction(zoomOutLabel) { artworkZoom.transform(0.8f, Offset.Zero, center, viewportSize); true },
                CustomAccessibilityAction(resetArtworkLabel) { artworkZoom.reset(); true },
                CustomAccessibilityAction(panLeftLabel) { artworkZoom.transform(1f, Offset(step, 0f), center, viewportSize); true },
                CustomAccessibilityAction(panRightLabel) { artworkZoom.transform(1f, Offset(-step, 0f), center, viewportSize); true },
                CustomAccessibilityAction(panUpLabel) { artworkZoom.transform(1f, Offset(0f, step), center, viewportSize); true },
                CustomAccessibilityAction(panDownLabel) { artworkZoom.transform(1f, Offset(0f, -step), center, viewportSize); true },
            )
        }
    }
    val imageSizesPx = remember(imageStates) {
        buildMap {
            for ((blockId, state) in imageStates) {
                val bitmap = when (state) {
                    is CoverUiState.Ready -> state.bitmap
                    is CoverUiState.StaleReady -> state.bitmap
                    else -> null
                } ?: continue
                put(blockId, IntSize(bitmap.width, bitmap.height))
            }
        }
    }
    val pageLayout = rememberReaderPageLayout(
        document = document,
        items = rawLayoutItems,
        imageSizesPx = imageSizesPx,
        settings = settings,
        viewportSize = viewportSize,
        dual = effectivePresentation == ReaderPresentation.DUAL_PAGE,
        enabled = effectivePresentation != ReaderPresentation.SCROLL,
    )
    val layoutItems = pageLayout.items
    // Transient visual position disambiguates metadata-only pages sharing a canonical anchor.
    // It is discarded on reflow and is never persisted instead of the semantic locator.
    var renderedPageOverride by remember(session, pageLayout) { mutableStateOf<Int?>(null) }
    val pageStep = if (effectivePresentation == ReaderPresentation.DUAL_PAGE &&
        (!artworkOnly || document.presentation == ReaderDocumentPresentation.FIXED_IMAGE)
    ) 2 else 1
    val renderedPageIndex = renderedPageOverride ?:
        (pageLayout.pageIndexFor(renderedBlockIndex, renderedCodePointOffset) / pageStep) * pageStep
    SideEffect {
        val image = document.blocks.getOrNull(renderedBlockIndex) as? ReaderBlock.Image
        val size = image?.let {
            val width = it.width
            val height = it.height
            imageSizesPx[it.blockId] ?: if (width != null && height != null) IntSize(width, height) else null
        }
        artworkZoom.fittedSize = if (artworkOnly && pageStep == 1 && size != null && size.width > 0 && size.height > 0) {
            val fit = minOf(viewportSize.width.toFloat() / size.width, viewportSize.height.toFloat() / size.height)
            IntSize((size.width * fit).roundToInt(), (size.height * fit).roundToInt())
        } else viewportSize
    }
    var pageDragOffsetPx by remember(session) { mutableFloatStateOf(0f) }
    var pageDragSettling by remember(session) { mutableStateOf(false) }
    val pageDragScope = rememberCoroutineScope()
    var pageDragJob by remember(session) { mutableStateOf<Job?>(null) }
    val currentPageLayout by rememberUpdatedState(pageLayout)
    val currentSession by rememberUpdatedState(session)
    DisposableEffect(session, pageLayout, effectiveFlow) {
        onDispose {
            pageDragJob?.cancel()
            pageDragOffsetPx = 0f
            pageDragSettling = false
        }
    }
    val displayEnvironment = LocalDisplayEnvironment.current
    val pageDragInteractive = displayEnvironment.effectiveProfile == DisplayProfile.STANDARD
    val instantPageMotion = displayEnvironment.effectiveProfile == DisplayProfile.EINK ||
        displayEnvironment.instantMotion || rememberSystemReducedMotion()
    val readerPosition = if (effectivePresentation == ReaderPresentation.SCROLL) {
        ReaderPosition.fromPageIndex(renderedBlockIndex, document.blocks.size)
    } else {
        ReaderPosition.fromPageIndex(renderedPageIndex, pageLayout.pages.size.coerceAtLeast(1), pageStep)
    }
    var scrollViewportAtEnd by remember(document.identity) { mutableStateOf(false) }
    val atChapterEnd = if (effectivePresentation == ReaderPresentation.SCROLL) {
        scrollViewportAtEnd
    } else {
        pageLayout.pages.isNotEmpty() && readerPosition.page + readerPosition.pageStep - 1 >= readerPosition.pageCount
    }
    var chapterCompletionReported by remember(document.identity) { mutableStateOf(false) }
    var chromeVisible by rememberSaveable(document.identity) { mutableStateOf(true) }
    var overlay by rememberSaveable(document.identity) { mutableStateOf<ReaderOverlay?>(null) }
    val seekSessionState = remember(session) { mutableStateOf<PreviewSession?>(null) }
    var seekSession by seekSessionState
    val seekTargets = remember(session) { HashMap<Int, PreviewTarget>() }
    var seekWitness by remember(session) { mutableStateOf<PreviewVisualWitness?>(null) }
    var seekSessionId by remember(session) { mutableLongStateOf(0L) }
    var seekVisualEpoch by remember(session) { mutableLongStateOf(0L) }
    var seekGestureGeneration by remember(session) { mutableLongStateOf(0L) }
    var pendingSeekRelease by remember(session) { mutableStateOf<Int?>(null) }
    var seekPreview by remember(session) { mutableStateOf<Int?>(null) }
    var pagedSeekLayout by remember(session) { mutableStateOf<ReaderPageLayout?>(null) }
    var scrollSeekPhase by remember(session) { mutableStateOf(ScrollSeekPhase.IDLE) }
    val seekCallbackLifetime = remember(session, pageLayout, eInk, seekGestureGeneration) { ReaderCallbackLifetime() }
    val currentSeekCallbackLifetime by rememberUpdatedState(seekCallbackLifetime)
    DisposableEffect(seekCallbackLifetime) {
        onDispose { seekCallbackLifetime.active = false }
    }
    DisposableEffect(seekSessionState) {
        onDispose { seekSessionState.value?.cancel() }
    }
    var readingInfoHeightPx by remember(document.identity) { mutableIntStateOf(0) }
    var bottomChromeHeightPx by remember(document.identity) { mutableIntStateOf(0) }
    val tableScrollStates = remember(session, previewLayoutKey, pageLayout, internalNavigationGeneration, density.density, density.fontScale) {
        mutableMapOf<String, ScrollState>()
    }
    val directoryPositions = remember(document, navigationTargets, currentContentId) {
        navigationTargets.map { target ->
            if (target.contentId != currentContentId) null else {
                val anchor = target.fragment?.let(document::targetForAnchor)
                val blockIndex = anchor?.let { value -> document.blocks.indexOfFirst { it.blockId == value.blockId } } ?: 0
                if (blockIndex < 0) null else blockIndex to (anchor?.codePointOffset ?: 0)
            }
        }
    }
    val currentContentIndex = remember(directoryPositions, renderedBlockIndex, renderedCodePointOffset) {
        var result = -1
        var bestBlock = -1
        var bestOffset = -1
        directoryPositions.forEachIndexed { index, position ->
            if (position != null && (position.first < renderedBlockIndex ||
                    position.first == renderedBlockIndex && position.second <= renderedCodePointOffset) &&
                (position.first > bestBlock || position.first == bestBlock && position.second > bestOffset)
            ) {
                result = index
                bestBlock = position.first
                bestOffset = position.second
            }
        }
        if (result >= 0) result else navigationTargets.indexOfFirst { it.contentId == currentContentId }
    }
    val readerFocusRequester = remember { FocusRequester() }
    val readerView = LocalView.current
    val activity = LocalActivity.current
    val latestOnLocatorChanged by rememberUpdatedState(onLocatorChanged)
    val visiblePositionIsExact = renderedPositionIsExact && if (effectivePresentation == ReaderPresentation.SCROLL) {
        readerPositionIsExact(layoutItems, renderedBlockIndex, renderedCodePointOffset)
    } else pageLayout.pages.getOrNull(renderedPageIndex)?.positionIsExact != false
    val committedBookmarkLocator = remember(session, renderedBlockIndex, renderedCodePointOffset, visiblePositionIsExact) {
        session.previewPositionAtBlock(renderedBlockIndex, renderedCodePointOffset, exact = visiblePositionIsExact).locator
    }
    val matchedBookmark = remember(document, bookmarks, committedBookmarkLocator, renderedBlockIndex) {
        bookmarks.firstOrNull { it.namesSameBookmarkPositionAs(committedBookmarkLocator) }
            ?: if (document.identity.book is BookIdentity.Local && committedBookmarkLocator.precision == LocatorPrecision.EXACT) {
                val renderedBlockId = document.blocks[renderedBlockIndex.coerceIn(document.blocks.indices)].blockId
                // Reuse the exact resolved capture; digest-only relocation must not select a legacy mark.
                bookmarks.firstOrNull { bookmark ->
                    bookmark.document.namesSameDocumentAs(document.identity) &&
                        !bookmark.document.namesSameRevisionAs(document.identity) &&
                        (bookmark.blockId == committedBookmarkLocator.blockId || bookmark.blockId == renderedBlockId) &&
                        bookmark.precision == LocatorPrecision.EXACT &&
                        bookmark.characterOffset == committedBookmarkLocator.characterOffset &&
                        bookmark.textAnchorDigest == committedBookmarkLocator.textAnchorDigest
                }
            } else null
    }

    fun commitPosition(
        blockIndex: Int,
        codePointOffset: Int = 0,
        ordinaryAdvance: Boolean = true,
        exact: Boolean = true,
    ): ReaderLocator {
        val isExact = exact && readerPositionIsExact(layoutItems, blockIndex, codePointOffset)
        val resolved = session.navigateToBlock(blockIndex.coerceIn(document.blocks.indices), codePointOffset, isExact)
        renderedPageOverride = null
        renderedBlockIndex = resolved.blockIndex
        renderedCodePointOffset = resolved.characterOffset
        renderedPositionIsExact = resolved.precision == LocatorPrecision.EXACT
        val locator = session.capture()
        latestOnLocatorChanged(locator, resolved.precision)
        if (ordinaryAdvance) returnAnchorState.ordinaryAdvance()
        return locator
    }
    fun jumpWithinDocument(blockIndex: Int, codePointOffset: Int, persist: Boolean = true) {
        val origin = committedBookmarkLocator
        val target = if (persist) {
            commitPosition(blockIndex, codePointOffset, ordinaryAdvance = false)
        } else {
            val preview = session.previewPositionAtBlock(blockIndex, codePointOffset,
                exact = readerPositionIsExact(layoutItems, blockIndex, codePointOffset))
            renderedBlockIndex = preview.blockIndex
            renderedCodePointOffset = preview.characterOffset
            renderedPositionIsExact = preview.precision == LocatorPrecision.EXACT
            renderedPageOverride = null
            preview.locator
        }
        returnAnchorState.commitInternalJump(origin, target)
        internalNavigationGeneration++
    }

    fun dispatchInlineAction(kind: ReaderInlineKind) {
        when (kind) {
            is ReaderInlineKind.Link -> when (val target = kind.target) {
                is ReaderLinkTarget.Internal -> {
                    if (target.contentId != document.identity.contentId) {
                        returnAnchorState.requestExternalJump(committedBookmarkLocator)
                        onNavigateLink(target)
                    } else if (target.fragment == null) {
                        jumpWithinDocument(0, 0)
                    } else {
                        val anchor = document.targetForAnchor(requireNotNull(target.fragment))
                        val targetBlockIndex = anchor?.let { value -> document.blocks.indexOfFirst { it.blockId == value.blockId } } ?: -1
                        if (anchor != null && targetBlockIndex >= 0) {
                            jumpWithinDocument(targetBlockIndex, anchor.codePointOffset)
                        } else {
                            onNavigateLink(target)
                        }
                    }
                }
                is ReaderLinkTarget.ExternalHttps -> onNavigateLink(target)
            }
            is ReaderInlineKind.NoteReference -> {
                val anchor = document.targetForNote(kind.noteId) ?: return
                val targetBlockIndex = document.blocks.indexOfFirst { it.blockId == anchor.blockId }
                if (targetBlockIndex >= 0) {
                    jumpWithinDocument(targetBlockIndex, anchor.codePointOffset, persist = false)
                }
            }
            else -> Unit
        }
    }

    fun commitPage(pageIndex: Int, ordinaryAdvance: Boolean = true): ReaderLocator? {
        val page = pageLayout.pages.getOrNull(pageIndex) ?: return null
        val locator = commitPosition(page.startBlockIndex, page.startCodePointOffset, ordinaryAdvance, exact = page.positionIsExact)
        renderedPageOverride = pageIndex
        return locator
    }

    fun cancelSeek() {
        if (seekPreview == null) return
        seekSession?.cancel()
        seekCallbackLifetime.active = false
        seekSession = null
        seekWitness = null
        pendingSeekRelease = null
        pagedSeekLayout = null
        if (scrollSeekPhase != ScrollSeekPhase.IDLE) scrollSeekPhase = ScrollSeekPhase.RESTORING
        seekPreview = null
        seekTargets.clear()
        seekGestureGeneration += 1
    }

    fun releaseScrollPreviewIfReady() {
        if (!seekCallbackLifetime.active || currentSeekCallbackLifetime !== seekCallbackLifetime) return
        val pendingTarget = pendingSeekRelease ?: return
        val preview = seekSession ?: return
        val expectedIndex = progressToIndex(pendingTarget, document.blocks.size)
        val request = preview.currentRequest ?: return
        if (request.target.id != expectedIndex.toString()) return
        val witness = seekWitness ?: return
        when (val release = preview.release(witness, previewEpochs, previewEpochs.layoutEpoch)) {
            is PreviewRelease.Committed -> {
                val index = release.target.id.toIntOrNull() ?: return
                val origin = committedBookmarkLocator
                val target = commitPosition(index, ordinaryAdvance = false)
                returnAnchorState.commitInternalJump(origin, target)
                seekSession = null
                seekWitness = null
                pendingSeekRelease = null
                pagedSeekLayout = null
                scrollSeekPhase = ScrollSeekPhase.IDLE
                seekTargets.clear()
                seekPreview = null
            }
            is PreviewRelease.Rejected -> cancelSeek()
        }
    }

    fun recordScrollPreviewVisualCommit() {
        val preview = seekSession ?: return
        val request = preview.currentRequest ?: return
        if (!seekCallbackLifetime.active || currentSeekCallbackLifetime !== seekCallbackLifetime ||
            seekSession !== preview || preview.currentRequest != request || preview.epochs != previewEpochs
        ) return
        val currentTarget = seekPreview ?: return
        val index = progressToIndex(currentTarget, document.blocks.size)
        if (request.target.id != index.toString()) return
        seekVisualEpoch += 1
        seekWitness = PreviewVisualWitness(
            sessionId = preview.sessionId,
            target = request.target,
            epochs = preview.epochs,
            planRevision = preview.plan.revision,
            requestGeneration = request.generation,
            visualEpoch = seekVisualEpoch,
        )
        releaseScrollPreviewIfReady()
    }


    fun startScrollPreview(targetValue: Int) {
        if (!seekCallbackLifetime.active || currentSeekCallbackLifetime !== seekCallbackLifetime) return
        val targetIndex = progressToIndex(targetValue, document.blocks.size)
        val existing = seekSession?.takeIf {
            it.epochs == previewEpochs && it.plan.revision == previewEpochs.layoutEpoch
        }
        if (existing == null) {
            seekSession?.cancel()
            seekTargets.clear()
            for (progress in 0..100) {
                val index = progressToIndex(progress, document.blocks.size)
                if (index !in seekTargets) {
                    seekTargets[index] = PreviewTarget(index.toString(), session.previewPositionAtBlock(index).locator)
                }
            }
        }
        val preview = existing ?: PreviewSession(
            sessionId = seekSessionId + 1,
            epochs = previewEpochs,
            plan = FrozenPreviewPlan(previewEpochs.layoutEpoch, seekTargets.values),
        ).also {
            seekSessionId = it.sessionId
            seekSession = it
        }
        val target = seekTargets[targetIndex] ?: return
        val previousRequest = preview.currentRequest
        preview.requestTarget(target)
        if (preview.currentRequest != previousRequest) seekWitness = null
        pendingSeekRelease = null
        scrollSeekPhase = ScrollSeekPhase.PREVIEWING
        seekPreview = targetValue
    }

    fun reportChapterCompletion() {
        if (atChapterEnd && !chapterCompletionReported) {
            chapterCompletionReported = true
            onChapterCompleted(currentContentId)
        }
    }

    fun selectAdjacentChapter(direction: Int, edgeGesture: Boolean = false): Boolean {
        if (!chapterNavigationEnabled) return false
        if (direction > 0 && !edgeGesture) reportChapterCompletion()
        if (currentContentIndex < 0) return false
        val currentTarget = navigationTargets[currentContentIndex]
        var targetIndex = currentContentIndex + if (direction < 0) -1 else 1
        while (targetIndex in navigationTargets.indices) {
            val candidate = navigationTargets[targetIndex]
            if (candidate.contentId != currentTarget.contentId || candidate.fragment != currentTarget.fragment) break
            targetIndex += if (direction < 0) -1 else 1
        }
        val target = navigationTargets.getOrNull(targetIndex) ?: return false
        val fragment = target.fragment
        if (target.contentId == currentContentId && fragment != null) {
            val anchor = document.targetForAnchor(fragment) ?: return false
            val blockIndex = document.blocks.indexOfFirst { it.blockId == anchor.blockId }
            if (blockIndex < 0) return false
            if (edgeGesture && direction < 0) {
                val currentStart = directoryPositions.getOrNull(currentContentIndex) ?: return false
                if (currentStart.second > 0) {
                    commitPosition(currentStart.first, currentStart.second - 1, ordinaryAdvance = false)
                } else {
                    val endBlock = currentStart.first - 1
                    if (endBlock < blockIndex) return false
                    val text = document.blocks[endBlock].canonicalAnchorText()
                    commitPosition(endBlock, text.codePointCount(0, text.length), ordinaryAdvance = false)
                }
            } else commitPosition(blockIndex, anchor.codePointOffset, ordinaryAdvance = false)
            internalNavigationGeneration++
        } else if (edgeGesture && onSelectAdjacentContent != null) {
            onSelectAdjacentContent(target, direction < 0)
        } else onSelectContent(target)
        if (!edgeGesture || target.contentId == currentContentId) returnAnchorState.ordinaryAdvance()
        return true
    }

    fun pageTarget(direction: Int): Int? {
        val target = renderedPageIndex + if (direction < 0) -pageStep else pageStep
        if (direction < 0 && renderedPageIndex > 0 && target < 0) return 0
        return target.takeIf(pageLayout.pages.indices::contains)
    }

    fun turnRenderedPage(direction: Int): Boolean {
        if (seekPreview != null || artworkZoom.zoomed) return false
        if (effectivePresentation == ReaderPresentation.SCROLL) {
            scrollStep = ReaderScrollStep(scrollStep.generation + 1L, if (direction < 0) -1 else 1)
            return true
        }
        if (pageLayout.pages.isEmpty()) return false
        val target = pageTarget(direction)
        if (target != null) {
            pageTurnGeneration += 1
            pageTurnTargetIndex = target
            commitPage(target)
            return true
        }
        return selectAdjacentChapter(direction)
    }

    fun updatePageDrag(dragAmount: Float) {
        if (!pageDragInteractive || pageDragSettling || seekPreview != null || pageLayout.pages.isEmpty()) return
        val proposed = (pageDragOffsetPx + dragAmount).coerceIn(-viewportSize.width.toFloat(), viewportSize.width.toFloat())
        val direction = (if (proposed < 0f) 1 else -1) * physicalForward
        pageDragOffsetPx = if (proposed == 0f || pageTarget(direction) != null) proposed else 0f
    }

    fun settlePageDrag(commit: Boolean) {
        if (pageDragSettling || pageDragOffsetPx == 0f) return
        val openingOffset = pageDragOffsetPx
        val direction = (if (openingOffset < 0f) 1 else -1) * physicalForward
        val targetPage = pageTarget(direction)
        val shouldCommit = commit && targetPage != null
        val targetOffset = if (shouldCommit) {
            if (openingOffset < 0f) -viewportSize.width.toFloat() else viewportSize.width.toFloat()
        } else {
            0f
        }
        pageDragSettling = true
        pageDragJob = pageDragScope.launch {
            if (instantPageMotion) {
                pageDragOffsetPx = targetOffset
            } else {
                animateTsuyomiPageTurn(
                    from = openingOffset,
                    to = targetOffset,
                    onFrame = { pageDragOffsetPx = it },
                )
            }
            currentCoroutineContext().ensureActive()
            if (currentPageLayout !== pageLayout || currentSession !== session) return@launch
            if (shouldCommit) {
                pageTurnTargetIndex = -1
                commitPage(requireNotNull(targetPage))
            }
            pageDragOffsetPx = 0f
            pageDragSettling = false
        }
    }

    fun updateSettings(action: ReaderSettingsAction) {
        if (eInk && action is ReaderSettingsAction.Flow && action.value == ReaderFlow.SCROLL) return
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
            if (eInk) eInkFlow = updated.flow.portableValue()
            else onFlowOverrideChanged(updated.flow.portableValue())
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
        if (eInk) {
            eInkFlow = null
            settings = settings.copy(flow = ReaderFlow.PAGED)
        } else {
            onFlowOverrideChanged(null)
            settings = settings.copy(flow = requestedFlow.readerFlow())
        }
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
        session,
        eInk,
        document.identity,
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
        pageLayout,
    ) {
        if (seekPreview != null) {
            seekSession?.invalidateIfStale(previewEpochs, previewEpochs.layoutEpoch)
            cancelSeek()
        }
    }
    LaunchedEffect(settings.keepAwake, overlay) {
        if (overlay == null) readerFocusRequester.requestFocus()
    }
    LaunchedEffect(returnAnchorState, document.identity, restorationGeneration) {
        returnAnchorState.navigationMounted()
    }
    LaunchedEffect(settings.progressVisible) {
        if (!settings.progressVisible) readingInfoHeightPx = 0
    }

    BackHandler(enabled = overlay != null) { overlay = null }
    BackHandler(enabled = seekPreview != null) { cancelSeek() }
    BackHandler(enabled = overlay == null && seekPreview == null && chromeVisible) { chromeVisible = false }
    BackHandler(enabled = overlay == null && seekPreview == null && artworkZoom.zoomed) { artworkZoom.reset() }
    LaunchedEffect(seekPreview, overlay) {
        if (seekPreview != null || overlay != null) artworkZoom.reset()
    }

    val previewPagePosition = seekPreview?.let {
        if (eInk) ReaderPosition.fromPageIndex(it, pageLayout.pages.size.coerceAtLeast(1), pageStep)
        else ReaderPosition.fromSeekProgress(it, pageLayout.pages.size.coerceAtLeast(1), pageStep)
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
                if (artworkOnly && seekPreview == null) {
                    val handled = when (event.key) {
                        Key.Equals -> { if (event.type == KeyEventType.KeyUp) artworkZoom.transform(1.25f, Offset.Zero,
                            Offset(viewportSize.width / 2f, viewportSize.height / 2f), viewportSize); true }
                        Key.Minus -> { if (event.type == KeyEventType.KeyUp) artworkZoom.transform(0.8f, Offset.Zero,
                            Offset(viewportSize.width / 2f, viewportSize.height / 2f), viewportSize); true }
                        Key.Escape -> if (artworkZoom.zoomed) { if (event.type == KeyEventType.KeyUp) artworkZoom.reset(); true } else false
                        Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> if (artworkZoom.zoomed) {
                            if (event.type == KeyEventType.KeyUp) {
                                val step = viewportSize.width.coerceAtMost(viewportSize.height) / 8f
                                val pan = when (event.key) {
                                    Key.DirectionLeft -> Offset(step, 0f)
                                    Key.DirectionRight -> Offset(-step, 0f)
                                    Key.DirectionUp -> Offset(0f, step)
                                    else -> Offset(0f, -step)
                                }
                                artworkZoom.transform(1f, pan, Offset.Zero, viewportSize)
                            }
                            true
                        } else false
                        else -> false
                    }
                    if (handled) return@onPreviewKeyEvent true
                }
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
                    Key.DirectionLeft -> event.type == KeyEventType.KeyUp && turnRenderedPage(-physicalForward)
                    Key.DirectionRight -> event.type == KeyEventType.KeyUp && turnRenderedPage(physicalForward)
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
                                pagingEnabled = !eInk && effectiveFlow != ReaderFlow.SCROLL && seekPreview == null,
                                tapPagingEnabled = effectiveFlow != ReaderFlow.SCROLL && seekPreview == null,
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
                                rightToLeft = rightToLeft,
                                artworkActions = if (seekPreview == null && overlay == null) artworkActions else emptyList(),
                            ),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        key(session, previewLayoutKey, pageLayout, internalNavigationGeneration, density.density, density.fontScale) {
                            val callbackLifetime = remember { ReaderCallbackLifetime() }
                            DisposableEffect(callbackLifetime) { onDispose { callbackLifetime.active = false } }
                            ReaderDocumentBody(
                                document = document,
                                layoutItems = layoutItems,
                                flow = effectiveFlow,
                                tableScrollStates = tableScrollStates,
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
                                    pageDragOffsetPx < 0f -> pageTarget(physicalForward)
                                    pageDragOffsetPx > 0f -> pageTarget(-physicalForward)
                                    else -> null
                                },
                                seeking = seekPreview != null,
                                scrollSeekPhase = scrollSeekPhase,
                                scrollStep = scrollStep,
                                onSettledPositionChanged = { index, offset, exact ->
                                    if (callbackLifetime.active) commitPosition(index, offset, exact = exact)
                                },
                                onScrollPreviewRestored = { if (callbackLifetime.active) scrollSeekPhase = ScrollSeekPhase.IDLE },
                                onScrollPreviewVisualCommit = { if (callbackLifetime.active) recordScrollPreviewVisualCommit() },
                                onScrollAtEndChanged = { if (callbackLifetime.active) scrollViewportAtEnd = it },
                                chapterEdgeEnabled = !eInk && overlay == null && seekPreview == null &&
                                    !artworkZoom.zoomed && chapterNavigationEnabled &&
                                    document.kind == org.tsuyomi.shared.sourcecontract.ReaderDocumentKind.CHAPTER,
                                onChapterEdge = { selectAdjacentChapter(it, edgeGesture = true) },
                                canPreviousChapter = currentContentIndex > 0,
                                canNextChapter = currentContentIndex in 0 until navigationTargets.lastIndex,
                                imageStates = imageStates,
                                onImageVisible = { if (callbackLifetime.active) onImageVisible(it) },
                                onRetryImage = { if (callbackLifetime.active) onRetryImage(it) },
                                onInlineAction = {
                                    if (callbackLifetime.active) {
                                        cancelSeek()
                                        dispatchInlineAction(it)
                                    }
                                },
                                modifier = Modifier.fillMaxSize().clipToBounds()
                                    .readerArtworkGestures(artworkZoom, artworkOnly && seekPreview == null && overlay == null)
                                    .graphicsLayer {
                                        scaleX = artworkZoom.scale
                                        scaleY = artworkZoom.scale
                                        translationX = artworkZoom.offset.x
                                        translationY = artworkZoom.offset.y
                                    },
                            )
                        }
                    }
                    if (artworkOnly && artworkZoom.zoomed && overlay == null) {
                        TsuyomiButton(
                            text = stringResource(R.string.reader_reset_artwork),
                            onClick = artworkZoom::reset,
                            style = TsuyomiButtonStyle.PRIMARY,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(bottom = with(density) { bottomChromeOverlapPx.toDp() } + TsuyomiSpacing.Md)
                                .testTag("reader-artwork-reset"),
                        )
                    }
                    if (returnAnchorState.anchor != null && overlay == null &&
                        (!artworkOnly || chromeVisible) && !(eInk && seekPreview != null)
                    ) {
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
                visible = (chromeVisible || overlay != null) && !(eInk && seekPreview != null),
                modifier = Modifier.align(Alignment.TopCenter),
                enterFrom = TsuyomiVisibilityEdge.TOP,
                exitTo = TsuyomiVisibilityEdge.TOP,
            ) {
                ReaderTopChrome(
                    chapterTitle = document.title,
                    bookmarked = matchedBookmark != null,
                    onUp = onNavigateUp,
                    onToggleBookmark = {
                        val bookmark = matchedBookmark
                        if (bookmark != null && !bookmark.document.namesSameRevisionAs(document.identity)) {
                            onRemoveBookmark(bookmark)
                        } else {
                            onToggleBookmark(committedBookmarkLocator)
                        }
                    },
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
                key(seekCallbackLifetime) {
                ReaderBottomChrome(
                    chapterIndex = currentContentIndex,
                    chapterCount = navigationTargets.size,
                    chapterProgress = when {
                        eInk -> renderedPageIndex
                        effectivePresentation == ReaderPresentation.SCROLL -> readerPosition.progress
                        else -> readerPosition.seekProgress
                    },
                    modifier = Modifier.testTag("reader-bottom-chrome"),
                    position = readerPosition,
                    eInk = eInk,
                    canPreviousPage = seekPreview == null && pageTarget(-1) != null,
                    canNextPage = seekPreview == null && pageTarget(1) != null,
                    onPreviousPage = { turnRenderedPage(-1) },
                    onNextPage = { turnRenderedPage(1) },
                    continuousSeek = effectivePresentation == ReaderPresentation.SCROLL,
                    seekPreview = seekPreview,
                    onSeekPreview = preview@{ target ->
                        if (!seekCallbackLifetime.active || currentSeekCallbackLifetime !== seekCallbackLifetime) return@preview
                        if (effectivePresentation == ReaderPresentation.SCROLL) {
                            startScrollPreview(target)
                        } else {
                            pageTurnTargetIndex = -1
                            if (seekPreview == null) pagedSeekLayout = pageLayout
                            seekPreview = target
                        }
                    },
                    onSeekCommit = commit@{ targetProgress ->
                        if (!seekCallbackLifetime.active || currentSeekCallbackLifetime !== seekCallbackLifetime) return@commit
                        if (effectivePresentation != ReaderPresentation.SCROLL &&
                            (currentPageLayout !== pageLayout || pagedSeekLayout !== pageLayout)
                        ) {
                            cancelSeek()
                            return@commit
                        }
                        if (effectivePresentation == ReaderPresentation.SCROLL) {
                            pendingSeekRelease = targetProgress
                            releaseScrollPreviewIfReady()
                        } else if (seekPreview != null) {
                            val target = if (eInk) {
                                ReaderPosition.fromPageIndex(targetProgress, pageLayout.pages.size.coerceAtLeast(1), pageStep)
                            } else {
                                ReaderPosition.fromSeekProgress(targetProgress, pageLayout.pages.size.coerceAtLeast(1), pageStep)
                            }
                            val origin = committedBookmarkLocator
                            val committed = commitPage(target.page - 1, ordinaryAdvance = false)
                            if (committed != null) returnAnchorState.commitInternalJump(origin, committed)
                            pagedSeekLayout = null
                            seekPreview = null
                        }
                    },
                    onSeekCancel = {
                        if (seekCallbackLifetime.active && currentSeekCallbackLifetime === seekCallbackLifetime) cancelSeek()
                    },
                    onPreviousChapter = { cancelSeek(); selectAdjacentChapter(-1) },
                    onOpenContents = { cancelSeek(); overlay = ReaderOverlay.AUXILIARY },
                    onOpenSettings = { cancelSeek(); overlay = ReaderOverlay.SETTINGS },
                    onNextChapter = { cancelSeek(); selectAdjacentChapter(1) },
                )
                }
            }
        }
    }

    when (overlay) {
        ReaderOverlay.AUXILIARY -> ReaderAuxiliarySheet(
            state = auxiliarySheetState,
            navigationTargets = navigationTargets,
            currentContentId = currentContentId,
            currentFragment = navigationTargets.getOrNull(currentContentIndex)?.fragment,
            bookmarks = bookmarks,
            onDismiss = { overlay = null },
            onSelectContent = { target ->
                cancelSeek()
                if (target.contentId == currentContentId && target.fragment != null) {
                    dispatchInlineAction(ReaderInlineKind.Link(ReaderLinkTarget.Internal(target.contentId, target.fragment)))
                } else {
                    returnAnchorState.requestExternalJump(committedBookmarkLocator)
                    onSelectContent(target)
                }
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
            settingsIdentity = document.identity.book.storageKey,
            state = settings,
            effectiveFlow = effectiveFlow,
            dualPageEligible = dualPageEligible,
            hasFlowOverride = if (eInk) eInkFlow != null else hasFlowOverride,
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
    layoutItems: List<ReaderLayoutItem>,
    flow: ReaderFlow,
    settings: ReaderSettingsUiState,
    settledIndex: Int,
    settledCodePointOffset: Int,
    viewportWidthPx: Int,
    displayIndex: Int,
    tableScrollStates: MutableMap<String, ScrollState>,
    pageLayout: ReaderPageLayout,
    displayPageIndex: Int,
    pageTurnMotionToken: Long,
    pageDragOffsetPx: Float,
    dragPageIndex: Int?,
    seeking: Boolean,
    scrollSeekPhase: ScrollSeekPhase,
    scrollStep: ReaderScrollStep,
    onSettledPositionChanged: (Int, Int, Boolean) -> Unit,
    onScrollPreviewRestored: () -> Unit,
    onScrollPreviewVisualCommit: () -> Unit,
    onScrollAtEndChanged: (Boolean) -> Unit,
    chapterEdgeEnabled: Boolean,
    onChapterEdge: (Int) -> Unit,
    canPreviousChapter: Boolean,
    canNextChapter: Boolean,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    onInlineAction: (ReaderInlineKind) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val bodyStyle = readerBodyStyle(settings)
    val initialItem = layoutItems.getOrNull(readerLayoutItemIndexForPosition(layoutItems, settledIndex, settledCodePointOffset))
    if (initialItem?.kind == ReaderLayoutItemKind.TABLE_ROW && initialItem.block.blockId !in tableScrollStates) {
        val margin = with(density) { settings.horizontalMargin.dp.roundToPx() }
        val width = viewportWidthPx - margin * 2
        val columnWidth = readerTableColumnWidthPx(initialItem, if (flow == ReaderFlow.DUAL) (width - margin) / 2 else width,
            bodyStyle, density)
        val cells = initialItem.tableCells.orEmpty()
        var offset = (settledCodePointOffset - initialItem.rootCodePointStart).coerceAtLeast(0)
        var column = 0
        while (column < cells.lastIndex) {
            val length = cells[column].sourceCodePointLength
            if (offset <= length) break
            offset -= length + 1
            column++
        }
        tableScrollStates[initialItem.block.blockId] = ScrollState(column * columnWidth)
    }
    when (flow) {
        ReaderFlow.SCROLL -> ScrollReaderBody(
            document = document,
            layoutItems = layoutItems,
            tableScrollStates = tableScrollStates,
            settings = settings,
            settledIndex = settledIndex,
            settledCodePointOffset = settledCodePointOffset,
            viewportWidthPx = viewportWidthPx,
            displayIndex = displayIndex,
            seeking = seeking,
            seekPhase = scrollSeekPhase,
            scrollStep = scrollStep,
            onSettledPositionChanged = onSettledPositionChanged,
            onPreviewRestored = onScrollPreviewRestored,
            onPreviewVisualCommit = onScrollPreviewVisualCommit,
            onAtEndChanged = onScrollAtEndChanged,
            chapterEdgeEnabled = chapterEdgeEnabled,
            onChapterEdge = onChapterEdge,
            canPreviousChapter = canPreviousChapter,
            canNextChapter = canNextChapter,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            onInlineAction = onInlineAction,
            modifier = modifier,
        )
        ReaderFlow.PAGED -> PagedReaderBody(
            document = document,
            settings = settings,
            pageLayout = pageLayout,
            tableScrollStates = tableScrollStates,
            displayPageIndex = displayPageIndex,
            seeking = seeking,
            pageTurnMotionToken = pageTurnMotionToken,
            pageDragOffsetPx = pageDragOffsetPx,
            dragPageIndex = dragPageIndex,
            dual = false,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            onInlineAction = onInlineAction,
            modifier = modifier,
        )
        ReaderFlow.DUAL -> PagedReaderBody(
            document = document,
            settings = settings,
            pageLayout = pageLayout,
            tableScrollStates = tableScrollStates,
            displayPageIndex = displayPageIndex,
            seeking = seeking,
            dual = true,
            pageTurnMotionToken = pageTurnMotionToken,
            pageDragOffsetPx = pageDragOffsetPx,
            dragPageIndex = dragPageIndex,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            onInlineAction = onInlineAction,
            modifier = modifier,
        )
    }
}

private class ReaderCallbackLifetime { var active = true }

private data class ScrollViewport(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

private data class ReaderScrollStep(val generation: Long, val direction: Int)

private data class ScrollSemanticPosition(val blockIndex: Int, val codePointOffset: Int, val exact: Boolean = true)

@Composable
private fun ScrollReaderBody(
    document: ReaderDocument,
    layoutItems: List<ReaderLayoutItem>,
    tableScrollStates: MutableMap<String, ScrollState>,
    settings: ReaderSettingsUiState,
    settledIndex: Int,
    settledCodePointOffset: Int,
    viewportWidthPx: Int,
    displayIndex: Int,
    seeking: Boolean,
    seekPhase: ScrollSeekPhase,
    scrollStep: ReaderScrollStep,
    onSettledPositionChanged: (Int, Int, Boolean) -> Unit,
    onPreviewRestored: () -> Unit,
    onPreviewVisualCommit: () -> Unit,
    onAtEndChanged: (Boolean) -> Unit,
    chapterEdgeEnabled: Boolean,
    onChapterEdge: (Int) -> Unit,
    canPreviousChapter: Boolean,
    canNextChapter: Boolean,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    onInlineAction: (ReaderInlineKind) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val artworkOnly = remember(document, layoutItems) {
        document.identity.book is BookIdentity.Local &&
            layoutItems.isNotEmpty() && layoutItems.all { it.block is ReaderBlock.Image }
    }
    val horizontalMarginPx = if (artworkOnly) 0 else with(density) { settings.horizontalMargin.dp.roundToPx() }
    val verticalMarginPx = if (artworkOnly) 0 else with(density) { settings.verticalMargin.dp.roundToPx() }
    val textWidth = viewportWidthPx - horizontalMarginPx * 2
    if (textWidth <= 0) {
        Box(modifier)
        return
    }
    val textMeasurer = rememberTextMeasurer(cacheSize = 1)
    val bodyStyle = readerBodyStyle(settings)
    val headingStyle = readerHeadingStyle(settings)
    val initialItemIndex = readerLayoutItemIndexForPosition(layoutItems, settledIndex, settledCodePointOffset)
    val initialOffset = remember(layoutItems, initialItemIndex, settledCodePointOffset, textWidth, verticalMarginPx, bodyStyle, headingStyle) {
        val item = layoutItems.getOrNull(initialItemIndex)
        val richText = item?.richText
        val paragraphInset = if (initialItemIndex > 0 && item?.continuation == false && !artworkOnly)
            with(density) { settings.paragraphSpacing.dp.roundToPx() } else 0
        if (item?.kind == ReaderLayoutItemKind.TABLE_ROW) {
            val table = item.block as ReaderBlock.Table
            val cells = item.tableCells.orEmpty()
            val cellPadding = with(density) { 4.dp.roundToPx() }
            val cellWidth = readerTableColumnWidthPx(item, textWidth, bodyStyle, density) - cellPadding * 2
            var localOffset = (settledCodePointOffset - item.rootCodePointStart).coerceAtLeast(0)
            var cellIndex = 0
            while (cellIndex < cells.lastIndex) {
                val length = cells[cellIndex].sourceCodePointLength
                if (localOffset <= length) break
                localOffset -= length + 1
                cellIndex++
            }
            val cell = cells[cellIndex]
            val style = bodyStyle.copy(fontWeight = if (table.rows[item.tableRowIndex!!].cells[cellIndex].header) {
                FontWeight.SemiBold
            } else bodyStyle.fontWeight)
            val layout = measureReaderText(cell, 0, cell.displayText.length, style, textMeasurer, density, cellWidth).body
            val top = layout.getLineTop(layout.getLineForOffset(cell.displayOffsetAt(localOffset))).roundToInt()
            if (top > 0) top + cellPadding + verticalMarginPx + paragraphInset else paragraphInset
        } else if (item == null || richText == null) {
            0
        } else {
            val style = when {
                item.block is ReaderBlock.Heading -> headingStyle
                item.kind == ReaderLayoutItemKind.NOTE_HEADER || item.kind == ReaderLayoutItemKind.POST_HEADER ->
                    bodyStyle.copy(fontWeight = FontWeight.SemiBold)
                item.block is ReaderBlock.Paragraph && item.block.preformatted -> bodyStyle.copy(fontFamily = FontFamily.Monospace)
                else -> bodyStyle
            }
            val quotePadding = if (item.block is ReaderBlock.Quote) with(density) { 16.dp.roundToPx() } else 0
            val markerWidth = readerListMarkerWidthPx(item, style, textMeasurer, density)
            val layout = measureReaderText(richText, 0, richText.displayText.length, style,
                textMeasurer, density, textWidth - quotePadding - markerWidth).body
            val localCodePointOffset = (settledCodePointOffset - item.rootCodePointStart).coerceAtLeast(0)
            val displayOffset = richText.displayOffsetAt(localCodePointOffset)
            val top = layout.getLineTop(layout.getLineForOffset(displayOffset)).roundToInt()
            if (top > 0) top + verticalMarginPx + quotePadding / 2 + paragraphInset else paragraphInset
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialItemIndex,
        initialFirstVisibleItemScrollOffset = initialOffset,
    )
    val textLayouts = remember { mutableStateMapOf<Int, TextLayoutResult>() }
    val tableLayouts = remember { mutableStateMapOf<Pair<Int, Int>, TextLayoutResult>() }
    var openingViewport by remember(listState) { mutableStateOf<ScrollViewport?>(null) }
    var consumedScrollStepGeneration by remember { mutableLongStateOf(scrollStep.generation) }
    LaunchedEffect(scrollStep) {
        if (scrollStep.generation > consumedScrollStepGeneration && seekPhase == ScrollSeekPhase.IDLE) {
            consumedScrollStepGeneration = scrollStep.generation
            val viewport = listState.layoutInfo
            val screenHeight = (viewport.viewportEndOffset - viewport.viewportStartOffset).coerceAtLeast(1)
            listState.scrollBy(screenHeight.toFloat() * scrollStep.direction)
            currentCoroutineContext().ensureActive()
        }
    }
    val previewItemIndex = remember(layoutItems, displayIndex) {
        readerLayoutItemIndexForPosition(layoutItems, displayIndex, 0)
    }
    LaunchedEffect(seekPhase, seeking, previewItemIndex) {
        when (seekPhase) {
            ScrollSeekPhase.PREVIEWING -> if (seeking) {
                if (openingViewport == null) {
                    openingViewport = ScrollViewport(
                        firstVisibleItemIndex = listState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                    )
                }
                listState.scrollToItem(previewItemIndex)
                currentCoroutineContext().ensureActive()
                onPreviewVisualCommit()
            }
            ScrollSeekPhase.RESTORING -> {
                val opening = openingViewport ?: ScrollViewport(initialItemIndex, initialOffset)
                listState.scrollToItem(opening.firstVisibleItemIndex, opening.firstVisibleItemScrollOffset)
                openingViewport = null
                onPreviewRestored()
            }
            ScrollSeekPhase.IDLE -> openingViewport = null
        }
    }
    LaunchedEffect(listState, seekPhase, layoutItems) {
        if (seekPhase == ScrollSeekPhase.IDLE) {
            snapshotFlow {
                if (listState.isScrollInProgress) return@snapshotFlow null
                val index = listState.firstVisibleItemIndex
                val item = layoutItems.getOrNull(index) ?: return@snapshotFlow null
                val paragraphInset = if (index > 0 && !item.continuation && !artworkOnly)
                    with(density) { settings.paragraphSpacing.dp.roundToPx() } else 0
                if (item.kind == ReaderLayoutItemKind.TABLE_ROW) {
                    val top = (listState.firstVisibleItemScrollOffset + listState.layoutInfo.viewportStartOffset -
                        with(density) { 4.dp.roundToPx() } - paragraphInset).coerceAtLeast(0).toFloat()
                    var cellOffset = item.rootCodePointStart
                    val columnWidth = readerTableColumnWidthPx(item, textWidth, bodyStyle, density)
                    val tableScrollState = tableScrollStates[item.block.blockId]
                    if (tableScrollState?.isScrollInProgress == true) return@snapshotFlow null
                    val firstVisibleColumn = (tableScrollState?.value ?: 0) / columnWidth
                    var position: ScrollSemanticPosition? = null
                    for ((cellIndex, cell) in item.tableCells.orEmpty().withIndex()) {
                        val layout = tableLayouts[index to cellIndex] ?: return@snapshotFlow null
                        if (cellIndex >= firstVisibleColumn && position == null && layout.size.height > top) {
                            val lineStart = layout.getLineStart(layout.getLineForVerticalPosition(top))
                            position = ScrollSemanticPosition(item.rootBlockIndex, cellOffset + cell.codePointOffsetAt(lineStart),
                                (!item.continuation || cell.displayText.isNotEmpty()) && cell.hasExactPositionAt(lineStart))
                        }
                        cellOffset += cell.sourceCodePointLength + 1
                    }
                    position ?: ScrollSemanticPosition(item.rootBlockIndex, cellOffset - 1)
                } else if (item.kind != ReaderLayoutItemKind.CONTENT || item.richText == null) {
                    ScrollSemanticPosition(item.rootBlockIndex, item.rootCodePointStart)
                } else {
                    val layout = textLayouts[index] ?: return@snapshotFlow null
                    val quoteInset = if (item.block is ReaderBlock.Quote) with(density) { 8.dp.roundToPx() } else 0
                    val visibleTop = (listState.firstVisibleItemScrollOffset + listState.layoutInfo.viewportStartOffset - quoteInset - paragraphInset)
                        .coerceAtLeast(0).toFloat()
                    val lineStart = layout.getLineStart(layout.getLineForVerticalPosition(visibleTop))
                    val codePointOffset = item.rootCodePointStart + item.richText.codePointOffsetAt(lineStart)
                    ScrollSemanticPosition(item.rootBlockIndex, codePointOffset, item.richText.hasExactPositionAt(lineStart))
                }
            }
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .collect { position -> onSettledPositionChanged(position.blockIndex, position.codePointOffset, position.exact) }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.totalItemsCount > 0 && !listState.canScrollForward
        }.distinctUntilChanged().collect(onAtEndChanged)
    }
    var edgeCue by remember(listState) { mutableIntStateOf(0) }
    val latestChapterEdge by rememberUpdatedState(onChapterEdge)
    val latestCanPreviousChapter by rememberUpdatedState(canPreviousChapter)
    val latestCanNextChapter by rememberUpdatedState(canNextChapter)
    val edgeThresholdPx = with(density) { 64.dp.toPx() }
    BoxWithConstraints(modifier) {
    val artworkHeightPx = constraints.maxHeight
    LazyColumn(
        state = listState,
        userScrollEnabled = seekPhase == ScrollSeekPhase.IDLE,
        modifier = Modifier.fillMaxSize().testTag("reader-document-scroll")
            .pointerInput(listState, chapterEdgeEnabled, seekPhase, edgeThresholdPx) {
                if (!chapterEdgeEnabled || seekPhase != ScrollSeekPhase.IDLE) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val atStart = !listState.canScrollBackward
                    val atEnd = !listState.canScrollForward
                    if (down.isConsumed || listState.isScrollInProgress || (!atStart && !atEnd) ||
                        listState.layoutInfo.totalItemsCount == 0
                    ) return@awaitEachGesture
                    // Child handlers run first in Main; parent tap-zone consumption runs afterward.
                    val childDown = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull { it.id == down.id }
                    if (childDown == null || childDown.isConsumed) return@awaitEachGesture
                    var claimed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
                            val delta = change.position - down.position
                            val outward = when {
                                delta.y < 0f && atEnd && latestCanNextChapter -> 1
                                delta.y > 0f && atStart && latestCanPreviousChapter -> -1
                                else -> 0
                            }
                            if (abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) >= abs(delta.y)) break
                            if (!claimed && change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) break
                            if (outward == 0 && abs(delta.y) > viewConfiguration.touchSlop) break
                            if (outward != 0 && abs(delta.y) > viewConfiguration.touchSlop) claimed = true
                            if (claimed) {
                                val ready = outward != 0 && abs(delta.y) >= edgeThresholdPx
                                edgeCue = if (ready) outward else 0
                                if (!change.pressed) {
                                    val released = change.previousPressed && ready
                                    change.consume()
                                    if (released) latestChapterEdge(outward)
                                    break
                                }
                                change.consume()
                            } else if (!change.pressed) break
                        }
                    } finally {
                        edgeCue = 0
                    }
                }
            },
        contentPadding = PaddingValues(
            horizontal = if (artworkOnly) 0.dp else settings.horizontalMargin.dp,
            vertical = if (artworkOnly) 0.dp else settings.verticalMargin.dp,
        ),
        verticalArrangement = Arrangement.Top,
    ) {
        itemsIndexed(layoutItems, key = { _, item -> item.key }) { index, item ->
            DisposableEffect(index) {
                onDispose {
                    textLayouts.remove(index)
                    item.tableCells?.indices?.forEach { tableLayouts.remove(index to it) }
                }
            }
            ReaderLayoutItemView(
                item = item,
                tableScrollStates = tableScrollStates,
                settings = settings,
                imageState = (item.block as? ReaderBlock.Image)?.let { imageStates[it.blockId] },
                onImageVisible = onImageVisible,
                onRetryImage = onRetryImage,
                onInlineAction = onInlineAction,
                onTextLayout = { textLayouts[index] = it },
                onTableCellLayout = { cellIndex, layout -> tableLayouts[index to cellIndex] = layout },
                modifier = Modifier.fillMaxWidth().padding(top =
                    if (index > 0 && !item.continuation && !artworkOnly) settings.paragraphSpacing.dp else 0.dp),
                maximumImageHeightPx = artworkHeightPx,
                imageHeightPx = artworkHeightPx.takeIf { artworkOnly },
                artwork = artworkOnly,
            )
        }
    }
    if (edgeCue != 0) {
        Surface(
            modifier = Modifier.align(if (edgeCue < 0) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(TsuyomiSpacing.Sm).testTag("reader-chapter-edge-cue"),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Text(
                stringResource(if (edgeCue < 0) R.string.reader_release_previous_chapter else R.string.reader_release_next_chapter),
                modifier = Modifier.padding(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Xs),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
    }
}

private fun readerLayoutItemIndexForPosition(
    items: List<ReaderLayoutItem>,
    blockIndex: Int,
    codePointOffset: Int,
): Int {
    if (items.isEmpty()) return 0
    var candidate = -1
    items.forEachIndexed { index, item ->
        if (item.rootBlockIndex != blockIndex) return@forEachIndexed
        val local = codePointOffset - item.rootCodePointStart
        val first = item.rootCodePointStart + (item.richText?.codePointOffsetAt(0) ?: 0)
        if (first == codePointOffset && item.kind != ReaderLayoutItemKind.TABLE_ROW) return index
        if (item.kind == ReaderLayoutItemKind.CONTENT && item.richText?.containsCodePointOffset(local) == true) return index
        if (item.kind == ReaderLayoutItemKind.TABLE_ROW) {
            var cellOffset = 0
            for (cell in item.tableCells.orEmpty()) {
                if (cellOffset + cell.codePointOffsetAt(0) == local && cell.displayText.isNotEmpty()) return index
                if (cell.containsCodePointOffset(local - cellOffset)) return index
                cellOffset += cell.sourceCodePointLength + 1
            }
        }
        val start = item.rootCodePointStart + (item.richText?.codePointOffsetAt(0) ?: 0)
        if (start <= codePointOffset && (candidate < 0 || start > items[candidate].rootCodePointStart +
                (items[candidate].richText?.codePointOffsetAt(0) ?: 0))) candidate = index
    }
    return if (candidate >= 0) candidate else items.indexOfFirst { it.rootBlockIndex >= blockIndex }.takeIf { it >= 0 } ?: items.lastIndex
}

private fun readerPositionIsExact(items: List<ReaderLayoutItem>, blockIndex: Int, codePointOffset: Int): Boolean {
    val item = items.getOrNull(readerLayoutItemIndexForPosition(items, blockIndex, codePointOffset)) ?: return true
    var local = codePointOffset - item.rootCodePointStart
    item.richText?.let { return it.hasExactCodePointPosition(local) }
    for (cell in item.tableCells.orEmpty()) {
        if (local <= cell.sourceCodePointLength) return (!item.continuation || cell.displayText.isNotEmpty()) &&
            cell.hasExactCodePointPosition(local)
        local -= cell.sourceCodePointLength + 1
    }
    return true
}

@Composable
private fun PagedReaderBody(
    document: ReaderDocument,
    settings: ReaderSettingsUiState,
    pageLayout: ReaderPageLayout,
    tableScrollStates: MutableMap<String, ScrollState>,
    displayPageIndex: Int,
    seeking: Boolean,
    pageTurnMotionToken: Long,
    pageDragOffsetPx: Float,
    dragPageIndex: Int?,
    dual: Boolean,
    modifier: Modifier,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    onInlineAction: (ReaderInlineKind) -> Unit,
) {
    if (!seeking && pageDragOffsetPx != 0f && dragPageIndex != null) {
        BoxWithConstraints(modifier.clipToBounds()) {
            val widthPx = constraints.maxWidth
            val adjacentOrigin = if (pageDragOffsetPx < 0f) widthPx else -widthPx
            ReaderPageSpread(
                settings = settings,
                pageLayout = pageLayout,
                tableScrollStates = tableScrollStates,
                pageIndex = displayPageIndex,
                dual = dual,
                imageStates = imageStates,
                onImageVisible = onImageVisible,
                onRetryImage = onRetryImage,
                onInlineAction = onInlineAction,
                modifier = Modifier.fillMaxSize().offset { IntOffset(pageDragOffsetPx.roundToInt(), 0) },
            )
            ReaderPageSpread(
                settings = settings,
                pageLayout = pageLayout,
                tableScrollStates = tableScrollStates,
                pageIndex = dragPageIndex,
                dual = dual,
                imageStates = imageStates,
                onImageVisible = onImageVisible,
                onRetryImage = onRetryImage,
                onInlineAction = onInlineAction,
                modifier = Modifier.fillMaxSize().offset {
                    IntOffset(adjacentOrigin + pageDragOffsetPx.roundToInt(), 0)
                },
            )
        }
        return
    }

    // Live scrub draws one spread directly; AnimatedContent must not retain an outgoing page layer.
    if (seeking || LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        ReaderPageSpread(
            settings = settings,
            pageLayout = pageLayout,
            tableScrollStates = tableScrollStates,
            pageIndex = displayPageIndex,
            dual = dual,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            onInlineAction = onInlineAction,
            modifier = modifier,
        )
        return
    }
    TsuyomiDirectionalAnimatedContent(
        targetState = ReaderPagePresentation(displayPageIndex, pageTurnMotionToken),
        transitionDirection = { initial, target ->
            when {
                target.motionToken == 0L || initial.pageIndex == target.pageIndex -> null
                (target.pageIndex > initial.pageIndex) != pageLayout.rightToLeft -> TsuyomiContentDirection.FORWARD
                else -> TsuyomiContentDirection.BACKWARD
            }
        },
        modifier = modifier,
    ) { presentation ->
        ReaderPageSpread(
            settings = settings,
            pageLayout = pageLayout,
            tableScrollStates = tableScrollStates,
            pageIndex = presentation.pageIndex,
            dual = dual,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            onInlineAction = onInlineAction,
            modifier = Modifier.fillMaxSize(),
        )
    }
}



@Composable
private fun ReaderPageSpread(
    settings: ReaderSettingsUiState,
    pageLayout: ReaderPageLayout,
    tableScrollStates: MutableMap<String, ScrollState>,
    pageIndex: Int,
    dual: Boolean,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    onInlineAction: (ReaderInlineKind) -> Unit,
    modifier: Modifier,
) {
    val artworkOnly = pageLayout.artworkOnly
    val columns = if (dual && (!artworkOnly || pageLayout.pairArtwork && pageIndex + 1 < pageLayout.pages.size)) 2 else 1
    Row(
        if (artworkOnly) modifier else modifier.padding(horizontal = settings.horizontalMargin.dp, vertical = settings.verticalMargin.dp),
        horizontalArrangement = Arrangement.spacedBy(if (artworkOnly) 0.dp else settings.horizontalMargin.dp),
    ) {
        repeat(columns) { visualColumn ->
            val column = if (pageLayout.rightToLeft) columns - 1 - visualColumn else visualColumn
            val page = pageLayout.pages.getOrNull(pageIndex + column)
            Column(Modifier.weight(1f).fillMaxSize().testTag("reader-page-column-$column")) {
                page?.segments?.forEachIndexed { segmentIndex, segment ->
                    if (segmentIndex > 0) Spacer(Modifier.height(settings.paragraphSpacing.dp))
                    val item = pageLayout.items.getOrNull(segment.itemIndex) ?: return@forEachIndexed
                    ReaderBlockSegmentView(
                        item = item,
                        segment = segment,
                        tableScrollStates = tableScrollStates,
                        settings = settings,
                        imageState = (item.block as? ReaderBlock.Image)?.let { imageStates[it.blockId] },
                        onImageVisible = onImageVisible,
                        onRetryImage = onRetryImage,
                        onInlineAction = onInlineAction,
                        modifier = Modifier.fillMaxWidth(),
                        artwork = artworkOnly,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderLayoutItemView(
    item: ReaderLayoutItem,
    settings: ReaderSettingsUiState,
    tableScrollStates: MutableMap<String, ScrollState>,
    imageState: CoverUiState?,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    onInlineAction: (ReaderInlineKind) -> Unit,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier,
    maximumImageHeightPx: Int,
    displayStart: Int = 0,
    displayEnd: Int? = null,
    imageHeightPx: Int? = null,
    measuredText: MeasuredReaderText? = null,
    tableSegments: List<ReaderTableSegmentCell>? = null,
    onTableCellLayout: (Int, TextLayoutResult) -> Unit = { _, _ -> },
    artwork: Boolean = false,
) {
    when {
        item.kind == ReaderLayoutItemKind.TABLE_ROW -> {
            val table = item.block as ReaderBlock.Table
            val row = table.rows[item.tableRowIndex ?: return]
            val columnCount = item.tableColumnCount
            val density = LocalDensity.current
            val bodyStyle = readerBodyStyle(settings)
            val scrollState = tableScrollStates.getOrPut(table.blockId) { ScrollState(0) }
            BoxWithConstraints(modifier.fillMaxWidth()) {
                val columnWidthPx = readerTableColumnWidthPx(item, constraints.maxWidth, bodyStyle, density)
                Row(Modifier.horizontalScroll(scrollState)) {
                repeat(columnCount) { index ->
                    val cell = row.cells.getOrNull(index)
                    val cellSegment = tableSegments?.getOrNull(index)
                    val text = item.tableCells?.getOrNull(index) ?: ReaderRichText.create("", emptyList())
                    Box(
                        Modifier.width(with(density) { columnWidthPx.toDp() })
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            .padding(4.dp),
                    ) {
                        ReaderInlineText(
                            richText = text,
                            style = readerBodyStyle(settings).copy(
                                fontWeight = if (cell?.header == true) FontWeight.SemiBold else FontWeight(settings.fontWeight),
                            ),
                            onInlineAction = onInlineAction,
                            start = cellSegment?.start ?: 0,
                            end = cellSegment?.end ?: text.displayText.length,
                            measured = cellSegment?.measurement,
                            onTextLayout = { layout -> onTableCellLayout(index, layout) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            }
        }
        item.block is ReaderBlock.Image -> {
            val block = item.block
            val bitmap = when (imageState) {
                is CoverUiState.Ready -> imageState.bitmap
                is CoverUiState.StaleReady -> imageState.bitmap
                else -> null
            }
            val sourceWidth = bitmap?.width ?: block.width
            val sourceHeight = bitmap?.height ?: block.height
            val aspectRatio = if (sourceWidth != null && sourceHeight != null && sourceWidth > 0 && sourceHeight > 0) {
                sourceWidth.toFloat() / sourceHeight.toFloat()
            } else {
                3f / 4f
            }
            val ratioHint = block.aspectRatio?.takeIf { sourceWidth == null || sourceHeight == null }
            if (imageHeightPx == null && ratioHint != null) {
                BoxWithConstraints(modifier.fillMaxWidth()) {
                    val heightPx = (constraints.maxWidth.toDouble() / ratioHint)
                        .coerceIn(1.0, maximumImageHeightPx.coerceAtLeast(1).toDouble()).toInt()
                    ReaderImageBlock(block, imageState, onImageVisible, onRetryImage,
                        Modifier.fillMaxWidth().height(with(LocalDensity.current) { heightPx.toDp() }), artwork)
                }
            } else {
                val imageModifier = if (imageHeightPx != null) {
                    modifier.height(with(LocalDensity.current) { imageHeightPx.toDp() })
                } else {
                    modifier.aspectRatio(aspectRatio)
                }
                ReaderImageBlock(block, imageState, onImageVisible, onRetryImage, imageModifier, artwork)
            }
        }
        item.block is ReaderBlock.Divider -> HorizontalDivider(modifier = modifier)
        item.richText != null -> {
            val style = when {
                item.block is ReaderBlock.Heading -> readerHeadingStyle(settings)
                item.kind == ReaderLayoutItemKind.NOTE_HEADER || item.kind == ReaderLayoutItemKind.POST_HEADER ->
                    readerBodyStyle(settings).copy(fontWeight = FontWeight.SemiBold)
                item.block is ReaderBlock.Paragraph && item.block.preformatted -> readerBodyStyle(settings).copy(fontFamily = FontFamily.Monospace)
                else -> readerBodyStyle(settings)
            }
            val fragmentStyle = if (item.continuation || displayStart > 0) style.copy(textIndent = androidx.compose.ui.text.style.TextIndent.None) else style
            val richText = item.richText
            val end = displayEnd ?: richText.displayText.length
            val textModifier = if (item.block is ReaderBlock.Quote) {
                modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant).padding(8.dp)
            } else modifier
            if (item.listMarker != null) {
                val density = LocalDensity.current
                val markerMeasurer = rememberTextMeasurer()
                val markerWidth = readerListMarkerWidthPx(item, style, markerMeasurer, density) -
                    with(density) { ((item.listDepth - 1).coerceAtLeast(0) * 18).dp.roundToPx() }
                Row(textModifier.fillMaxWidth()) {
                    if (item.listDepth > 1) Spacer(Modifier.width(((item.listDepth - 1) * 18).dp))
                    if (displayStart == 0 && !item.continuation) {
                        Text(item.listMarker, modifier = Modifier.width(with(density) { markerWidth.toDp() }),
                            style = style.copy(textIndent = androidx.compose.ui.text.style.TextIndent.None), maxLines = 1)
                    } else {
                        Spacer(Modifier.width(with(density) { markerWidth.toDp() }))
                    }
                    ReaderInlineText(
                        richText = richText,
                        start = displayStart,
                        end = end,
                        measured = measuredText,
                        style = fragmentStyle,
                        onInlineAction = onInlineAction,
                        onTextLayout = onTextLayout,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                ReaderInlineText(
                    richText = richText,
                    start = displayStart,
                    end = end,
                    measured = measuredText,
                    style = fragmentStyle,
                    onInlineAction = onInlineAction,
                    onTextLayout = onTextLayout,
                    modifier = textModifier.fillMaxWidth(),
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun ReaderInlineText(
    richText: ReaderRichText,
    style: androidx.compose.ui.text.TextStyle,
    onInlineAction: (ReaderInlineKind) -> Unit,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier,
    start: Int = 0,
    end: Int = richText.displayText.length,
    measured: MeasuredReaderText? = null,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val foreground = MaterialTheme.colorScheme.onBackground
    val latestAction by rememberUpdatedState(onInlineAction)
    BoxWithConstraints(modifier) {
        val measurement = measured ?: remember(richText, start, end, style, constraints.maxWidth, density) {
            measureReaderText(richText, start, end, style, measurer, density, constraints.maxWidth)
        }
        val layout = measurement.body
        val actions = remember(richText, start, end) { richText.actions(start, end) }
        val annotationText = remember(measurement) {
            measurement.rubies.flatMap { it.annotations }.joinToString("\n") { it.layout.layoutInput.text.text }
        }
        SideEffect { onTextLayout(layout) }
        Canvas(Modifier.fillMaxWidth().height(with(density) { layout.size.height.toDp() })
            .semantics {
                text = AnnotatedString(richText.accessibleText(start, end))
                if (annotationText.isNotEmpty()) contentDescription = annotationText
                getTextLayoutResult { result -> result.add(layout); true }
                customActions = actions.map { (label, kind) -> CustomAccessibilityAction(label) { latestAction(kind); true } }
            }
            .pointerInput(richText, start, end, measurement) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val action = richText.actionAt(down.position, measurement, start)
                    if (action != null) {
                        down.consume()
                        val up = waitForUpOrCancellation()
                        if (up != null) { up.consume(); latestAction(action) }
                    }
                }
            }) {
            drawText(layout, color = foreground)
            measurement.rubies.forEachIndexed { index, ruby ->
                val bounds = layout.placeholderRects.getOrNull(index) ?: return@forEachIndexed
                ruby.annotations.forEach { annotation ->
                    drawText(annotation.layout, color = foreground,
                        topLeft = Offset(bounds.left + annotation.x, bounds.top + annotation.y))
                }
                ruby.base?.let { base ->
                    drawText(base, color = foreground, topLeft = Offset(
                        bounds.left + (bounds.width - base.size.width) / 2f, bounds.top + ruby.baseTopPx))
                }
            }
        }
    }
}

@Composable
private fun ReaderBlockSegmentView(
    item: ReaderLayoutItem,
    segment: ReaderPageSegment,
    tableScrollStates: MutableMap<String, ScrollState>,
    settings: ReaderSettingsUiState,
    imageState: CoverUiState?,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    onInlineAction: (ReaderInlineKind) -> Unit,
    modifier: Modifier,
    artwork: Boolean,
) {
    ReaderLayoutItemView(
        item = item,
        tableScrollStates = tableScrollStates,
        settings = settings,
        imageState = imageState,
        onImageVisible = onImageVisible,
        onRetryImage = onRetryImage,
        onInlineAction = onInlineAction,
        onTextLayout = {},
        modifier = modifier,
        maximumImageHeightPx = segment.measuredHeightPx,
        displayStart = segment.startCharacterIndex,
        displayEnd = segment.endCharacterIndex,
        imageHeightPx = segment.measuredHeightPx.takeIf { item.block is ReaderBlock.Image },
        measuredText = segment.measuredText,
        tableSegments = segment.tableCells.takeIf { item.kind == ReaderLayoutItemKind.TABLE_ROW },
        artwork = artwork,
    )
}

@Composable
private fun ReaderImageBlock(
    block: ReaderBlock.Image,
    state: CoverUiState?,
    onVisible: (ReaderBlock.Image) -> Unit,
    onRetry: (ReaderBlock.Image) -> Unit,
    modifier: Modifier,
    artwork: Boolean = false,
) {
    val visibilityOwner = LocalReaderImageVisibilityOwner.current
    val onHidden = LocalReaderImageHidden.current
    DisposableEffect(block.resource, block.blockId, visibilityOwner) {
        // Visible and Hidden retain the same mount-time route epoch, even after callback replacement.
        if (visibilityOwner != null) visibilityOwner.mount(block, onVisible, onHidden) else onVisible(block)
        onDispose { if (visibilityOwner != null) visibilityOwner.unmount(block) else onHidden(block) }
    }
    val bitmap = when (state) {
        is CoverUiState.Ready -> state.bitmap
        is CoverUiState.StaleReady -> state.bitmap
        else -> null
    }
    if (artwork && bitmap != null) {
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = block.altText,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            colorFilter = if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) EInkGrayscaleColorFilter else null,
        )
        return
    }
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
    tapPagingEnabled: Boolean,
    onPrevious: () -> Boolean,
    onCenter: () -> Unit,
    onNext: () -> Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    rightToLeft: Boolean,
    artworkActions: List<CustomAccessibilityAction> = emptyList(),
): Modifier = semantics {
    onClick(label = "显示或隐藏阅读控制") {
        onCenter()
        true
    }
    customActions = listOf(
        CustomAccessibilityAction("上一页") { onPrevious() },
        CustomAccessibilityAction("下一页") { onNext() },
    ) + artworkActions
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
).pointerInput(onPrevious, onCenter, onNext, tapPagingEnabled, rightToLeft) {
    detectTapGestures { position ->
        val fraction = (position.x / size.width).coerceIn(0f, 1f)
        if (!tapPagingEnabled) {
            onCenter()
            return@detectTapGestures
        }
        when {
            fraction < 0.3f -> if (!(if (rightToLeft) onNext() else onPrevious())) onCenter()
            fraction > 0.7f -> if (!(if (rightToLeft) onPrevious() else onNext())) onCenter()
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

