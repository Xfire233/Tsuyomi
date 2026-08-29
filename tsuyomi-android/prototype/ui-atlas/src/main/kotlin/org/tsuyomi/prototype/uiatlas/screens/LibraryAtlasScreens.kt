/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasFeatureIntroduction
import org.tsuyomi.prototype.uiatlas.components.AtlasChip
import org.tsuyomi.prototype.uiatlas.components.AtlasCoverImage
import org.tsuyomi.prototype.uiatlas.components.AtlasIconButton
import org.tsuyomi.prototype.uiatlas.components.LibraryBookDragPreview
import org.tsuyomi.prototype.uiatlas.components.LibraryBookInsertionGap
import org.tsuyomi.prototype.uiatlas.components.LibraryBookInteractionCapabilities
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortDirection
import org.tsuyomi.prototype.uiatlas.components.LibraryBookSortMode
import org.tsuyomi.prototype.uiatlas.components.libraryDragPreviewSize
import org.tsuyomi.prototype.uiatlas.components.currentLayoutIcon
import org.tsuyomi.prototype.uiatlas.components.layoutToggleContentDescription
import org.tsuyomi.prototype.uiatlas.components.nextAtlasLayout
import org.tsuyomi.prototype.uiatlas.components.summary
import org.tsuyomi.prototype.uiatlas.components.orderedForLibrary
import org.tsuyomi.prototype.uiatlas.components.LibraryDragCoordinator
import org.tsuyomi.prototype.uiatlas.components.LibraryDropDestination
import org.tsuyomi.prototype.uiatlas.components.LibraryDropItemKind
import org.tsuyomi.prototype.uiatlas.components.libraryBookDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryContentDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryDeleteDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryDragOverlayHost
import org.tsuyomi.prototype.uiatlas.components.libraryDragSource
import org.tsuyomi.prototype.uiatlas.components.libraryItemDropTarget
import org.tsuyomi.prototype.uiatlas.components.libraryShelfDropTarget
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasIdentityOption
import org.tsuyomi.prototype.uiatlas.components.AtlasInfoBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationPhase
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationStatus
import org.tsuyomi.prototype.uiatlas.components.AtlasOverflowItem
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasSelectionBar
import org.tsuyomi.prototype.uiatlas.components.AtlasStateKind
import org.tsuyomi.prototype.uiatlas.components.AtlasStateView
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarSelector
import org.tsuyomi.prototype.uiatlas.components.BookGridCard
import org.tsuyomi.prototype.uiatlas.components.BookListItemRow
import org.tsuyomi.prototype.uiatlas.components.CompactBookListItem
import org.tsuyomi.prototype.uiatlas.components.SourceIdentityBand
import org.tsuyomi.prototype.uiatlas.fixtures.LibraryAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasFamily
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.runtime.PrototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasMotion
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

/** Library-family atlas routes #1–11. Book surfaces #12–14 are owned by SourceAtlasScreen. */
@Composable
fun LibraryAtlasScreen(context: AtlasContext, modifier: Modifier = Modifier) {
    when (context.route) {
        AtlasRoute.LIBRARY, AtlasRoute.LIBRARY_SYSTEM -> LibraryRoot(context, modifier)
        AtlasRoute.LIBRARY_HISTORY -> LibraryHistory(context, modifier)
        AtlasRoute.LIBRARY_UPDATES -> LibraryUpdates(context, modifier)
        AtlasRoute.LIBRARY_COLLECTION,
        AtlasRoute.LIBRARY_COLLECTION_CHILD,
        AtlasRoute.LIBRARY_COLLECTION_GRANDCHILD,
        -> CollectionDetail(context, modifier)
        AtlasRoute.LIBRARY_COLLECTION_RULE -> CollectionRule(context, modifier)
        AtlasRoute.LIBRARY_TAGS -> LibraryTags(context, modifier)
        AtlasRoute.LIBRARY_MIRROR,
        AtlasRoute.LIBRARY_MIRROR_FOLDER,
        AtlasRoute.LIBRARY_MIRROR_SUBFOLDER,
        -> LibraryMirror(context, modifier)
        else -> Box(modifier.fillMaxSize())
    }
}

internal fun AtlasContext.isVariant(id: Char, option: String): Boolean =
    variant?.id == id && variant.option == option

internal val AtlasPageState.showsContent: Boolean
    get() = when (this) {
        AtlasPageState.LOADING, AtlasPageState.EMPTY, AtlasPageState.ERROR -> false
        else -> true
    }

@Composable
internal fun StateOrContent(
    state: AtlasPageState,
    emptyTitle: String,
    emptyMessage: String?,
    errorTitle: String,
    errorMessage: String?,
    emptyAction: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val repository = prototypeRepository()
    when (state) {
        AtlasPageState.LOADING -> AtlasStateView(AtlasStateKind.LOADING, AtlasStrings.LOADING)
        AtlasPageState.EMPTY -> AtlasStateView(
            AtlasStateKind.EMPTY,
            emptyTitle,
            message = emptyMessage,
            actionLabel = emptyAction,
            onAction = onEmptyAction,
        )
        AtlasPageState.ERROR -> AtlasStateView(
            AtlasStateKind.ERROR,
            errorTitle,
            message = errorMessage,
            actionLabel = AtlasStrings.RETRY,
            onAction = { repository.record("RetryRequested", "library-state", "queued") },
        )
        else -> content()
    }
}

@Composable
internal fun OverlayState(
    state: AtlasPageState,
    mutation: AtlasMutationStatus?,
    unresolved: AtlasMutationStatus? = null,
) {
    val repository = prototypeRepository()
    when (state) {
        AtlasPageState.OFFLINE -> AtlasInfoBanner(
            AtlasBanner(
                title = AtlasStrings.OFFLINE_TITLE,
                message = "展示本地缓存内容；联网后可刷新。",
                actionLabel = AtlasStrings.RETRY,
                onAction = { repository.record("RetryRequested", "library-offline", "queued") },
            ),
        )
        AtlasPageState.REFRESHING -> AtlasInfoBanner(AtlasBanner(AtlasStrings.REFRESHING_TITLE))
        AtlasPageState.MUTATION -> mutation?.let { AtlasMutationBanner(it) }
        AtlasPageState.UNRESOLVED -> (unresolved ?: mutation)?.let { AtlasMutationBanner(it) }
        else -> mutation?.let { AtlasMutationBanner(it) }
    }
}

@Composable
internal fun FullDialog(
    title: String,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = !destructive),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = if (eInk) BorderStroke(1.5.dp, AtlasEInkPalette.Ink) else null,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(AtlasSpacing.Lg),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(AtlasSpacing.Md))
                content()
            }
        }
    }
}

@Composable
internal fun DialogButtons(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AtlasSpacing.Lg),
        horizontalArrangement = Arrangement.End,
    ) {
        AtlasButton(AtlasStrings.CANCEL, onDismiss, style = AtlasButtonStyle.TEXT)
        Spacer(Modifier.size(AtlasSpacing.Sm))
        AtlasButton(confirmLabel, onConfirm)
    }
}

@Composable
internal fun LibrarySortDialog(
    mode: LibraryBookSortMode,
    direction: LibraryBookSortDirection,
    onModeChange: (LibraryBookSortMode) -> Unit,
    onDirectionChange: (LibraryBookSortDirection) -> Unit,
    onDismiss: () -> Unit,
    allowCustom: Boolean = true,
) {
    FullDialog("排序", onDismiss) {
        Text("排序依据", style = MaterialTheme.typography.titleMedium)
        LibraryBookSortMode.entries
            .filter { allowCustom || it != LibraryBookSortMode.CUSTOM }
            .forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.RadioButton) { onModeChange(option) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == option, onClick = { onModeChange(option) })
                    Text(option.label, modifier = Modifier.padding(start = AtlasSpacing.Sm))
                }
            }
        if (mode != LibraryBookSortMode.CUSTOM) {
            Text(
                "顺序",
                modifier = Modifier.padding(top = AtlasSpacing.Md),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                LibraryBookSortDirection.entries.forEach { option ->
                    FilterChip(
                        selected = direction == option,
                        onClick = { onDirectionChange(option) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = AtlasSpacing.Lg),
            horizontalArrangement = Arrangement.End,
        ) {
            AtlasButton("完成", onDismiss)
        }
    }
}

internal data class PageSlice<T>(
    val items: List<T>,
    val page: Int,
    val pages: Int,
    val setPage: (Int) -> Unit,
)

@Composable
internal fun <T> pageSlice(key: String, values: List<T>, size: Int): PageSlice<T> {
    var page by rememberSaveable(key) { mutableIntStateOf(0) }
    val pages = max(1, (values.size + size - 1) / size)
    LaunchedEffect(key, pages) { if (page >= pages) page = pages - 1 }
    val clamped = page.coerceIn(0, pages - 1)
    return PageSlice(
        values.drop(clamped * size).take(size),
        clamped,
        pages,
    ) { page = it.coerceIn(0, pages - 1) }
}

@Composable
internal fun PaginationFooter(page: Int, pages: Int, setPage: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (LocalAtlasEnvironment.current.eInk) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = AtlasSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            AtlasIconButton(AtlasIcons.Prev, "上一页", { setPage(page - 1) }, enabled = page > 0)
            Text(
                AtlasStrings.pageOf(page + 1, pages),
                modifier = Modifier
                    .padding(horizontal = AtlasSpacing.Lg)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge,
            )
            AtlasIconButton(
                AtlasIcons.Next,
                "下一页",
                { setPage(page + 1) },
                enabled = page < pages - 1,
            )
        }
    }
}

internal fun selectionTopBar(
    selected: Set<String>,
    allIds: Set<String>,
    close: () -> Unit,
    setAll: (Set<String>) -> Unit,
    bulkIcon: androidx.compose.ui.graphics.vector.ImageVector,
    bulkLabel: String,
    bulkAction: () -> Unit,
): AtlasSelectionBar = AtlasSelectionBar(
    count = selected.size,
    onClose = close,
    allSelected = allIds.isNotEmpty() && selected.containsAll(allIds),
    onToggleAll = {
        setAll(if (allIds.isNotEmpty() && selected.containsAll(allIds)) emptySet() else allIds)
    },
    bulkActions = listOf(AtlasTopBarAction(bulkIcon, bulkLabel, bulkAction)),
)
