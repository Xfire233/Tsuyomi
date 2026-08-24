/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasChip
import org.tsuyomi.prototype.uiatlas.components.AtlasFeatureIntroduction
import org.tsuyomi.prototype.uiatlas.components.AtlasCoverImage
import org.tsuyomi.prototype.uiatlas.components.AtlasIconButton
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.currentLayoutIcon
import org.tsuyomi.prototype.uiatlas.components.layoutToggleContentDescription
import org.tsuyomi.prototype.uiatlas.components.nextAtlasLayout
import org.tsuyomi.prototype.uiatlas.components.AtlasIdentityOption
import org.tsuyomi.prototype.uiatlas.components.AtlasInfoBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationPhase
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationStatus
import org.tsuyomi.prototype.uiatlas.components.AtlasOverflowItem
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasSelectionBar
import org.tsuyomi.prototype.uiatlas.components.AtlasSourceMarkCanvas
import org.tsuyomi.prototype.uiatlas.components.AtlasStateKind
import org.tsuyomi.prototype.uiatlas.components.AtlasStateView
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.components.BookGridCard
import org.tsuyomi.prototype.uiatlas.components.BookListItemRow
import org.tsuyomi.prototype.uiatlas.components.CompactBookListItem
import org.tsuyomi.prototype.uiatlas.components.AtlasSourceIcon
import org.tsuyomi.prototype.uiatlas.components.SourceIdentityBand
import org.tsuyomi.prototype.uiatlas.fixtures.AtlasFixtures
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.screens.reader.StandardReaderAtlasScreen
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasBranding
import org.tsuyomi.prototype.uiatlas.model.AtlasFamily
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasReaderPresentation
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.AtlasSource
import org.tsuyomi.prototype.uiatlas.model.AtlasVariant
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasMotion
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

/** Full-screen atlas family for routes #12–18. */
@Composable
fun SourceAtlasScreen(context: AtlasContext, modifier: Modifier = Modifier) {
    when (context.route) {
        AtlasRoute.BOOK_DETAIL -> BookDetail(context, modifier)
        AtlasRoute.BOOK_READER -> BookReader(context, modifier)
        AtlasRoute.BROWSE -> BrowseRoot(context, modifier)
        AtlasRoute.SEARCH -> GlobalSearch(context, modifier)
        AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY -> RemoteLibrary(context, modifier)
        AtlasRoute.SOURCE_VERIFICATION -> SourceVerification(context, modifier)
        else -> AtlasStateView(
            kind = AtlasStateKind.EMPTY,
            title = "该路由不属于来源图册族",
            modifier = modifier,
        )
    }
}


// -- Variant helpers ------------------------------------------------------------------------


internal enum class RowActionOption {
    TRAILING, OVERFLOW, SWIPE
}

internal fun rowActionOption(variant: AtlasVariant?): RowActionOption {
    if (variant == null || variant.id.uppercaseChar() != 'B') {
        return RowActionOption.TRAILING
    }
    return when (variant.option) {
        "b" -> RowActionOption.OVERFLOW
        "c" -> RowActionOption.SWIPE
        else -> RowActionOption.TRAILING
    }
}


/** `view=collection` selects invalid branding; `view=mirror` selects missing branding. */
internal fun brandingSource(view: AtlasLibraryView): AtlasSource = when (view) {
    AtlasLibraryView.COLLECTION -> AtlasFixtures.sourcePine.copy(
        id = "atlas.pine.invalid",
        branding = AtlasFixtures.brandingInvalidScript,
    )
    AtlasLibraryView.MIRROR -> AtlasFixtures.sourcePine.copy(
        id = "atlas.pine.missing",
        branding = AtlasFixtures.brandingMissing,
    )
    else -> AtlasFixtures.sourcePine
}

// -- Shared pieces --------------------------------------------------------------------------

@Composable
internal fun Section(title: String, caption: String? = null) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtlasSpacing.Md)
            .padding(top = AtlasSpacing.Lg, bottom = AtlasSpacing.Sm),
        style = MaterialTheme.typography.titleMedium,
    )
    if (caption != null) {
        Text(
            text = caption,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun KeyValue(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Xs),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun PaginationBar(
    page: Int,
    pages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtlasIconButton(AtlasIcons.Prev, "上一页", onPrev, enabled = page > 1)
                Text(
                    text = AtlasStrings.pageOf(page, pages),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
                AtlasIconButton(AtlasIcons.Next, "下一页", onNext, enabled = page < pages)
            }
        }
    }
}

@Composable
internal fun ReviewDialog(
    title: String,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    inlinePreview: Boolean = false,
    content: @Composable () -> Unit,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val surface: @Composable () -> Unit = {
        Surface(
            modifier = if (eInk) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(AtlasSpacing.Lg).widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = if (eInk) BorderStroke(1.5.dp, AtlasEInkPalette.Ink) else null,
        ) {
            Column(Modifier.padding(AtlasSpacing.Lg)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Box(Modifier.padding(top = AtlasSpacing.Md)) { content() }
            }
        }
    }
    if (inlinePreview) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { surface() }
    } else {
        Dialog(onDismissRequest = { if (!destructive) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = !destructive)) { surface() }
    }
}

@Composable
internal fun RowAction(
    option: RowActionOption,
    label: String,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onAction: () -> Unit = {},
    onDetails: () -> Unit = {},
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    when (option) {
        RowActionOption.TRAILING -> AtlasButton(
            text = label,
            onClick = onAction,
            style = AtlasButtonStyle.TEXT,
            enabled = enabled,
        )
        RowActionOption.OVERFLOW -> {
            var open by remember { mutableStateOf(false) }
            Box {
                AtlasIconButton(AtlasIcons.Overflow, "更多操作", { open = true })
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { open = false; onAction() },
                        enabled = enabled,
                    )
                    DropdownMenuItem(text = { Text("查看详情") }, onClick = { open = false; onDetails() })
                }
            }
        }
        RowActionOption.SWIPE -> Column(horizontalAlignment = Alignment.End) {
            AtlasButton(text = label, onClick = onAction, style = AtlasButtonStyle.TEXT, enabled = enabled)
            Text(
                text = if (eInk) "电子墨水屏使用可见操作" else "或向左滑动（快捷）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (!enabled && disabledReason != null) {
        Text(
            text = disabledReason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun containerWidth(): Dp = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp()
}

@Composable
internal fun gridColumns(): Int {
    val width = containerWidth().value.toInt()
    return if (width < 600) 3 else maxOf(4, (width - 32) / 150)
}
