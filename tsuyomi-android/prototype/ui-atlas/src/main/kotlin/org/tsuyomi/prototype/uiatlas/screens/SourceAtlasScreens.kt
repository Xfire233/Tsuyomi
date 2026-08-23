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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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


private enum class RowActionOption {
    TRAILING, OVERFLOW, SWIPE
}

private fun rowActionOption(variant: AtlasVariant?): RowActionOption {
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
private fun brandingSource(view: AtlasLibraryView): AtlasSource = when (view) {
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
private fun Section(title: String, caption: String? = null) {
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
private fun KeyValue(label: String, value: String) {
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
private fun PaginationBar(
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
private fun ReviewDialog(
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
private fun RowAction(
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
private fun containerWidth(): Dp = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp()
}

@Composable
private fun gridColumns(): Int {
    val width = containerWidth().value.toInt()
    return if (width < 600) 3 else maxOf(4, (width - 32) / 150)
}

// -- #12 canonical detail -------------------------------------------------------------------

@Composable
private fun BookDetail(context: AtlasContext, modifier: Modifier) {
    val dormant = context.libraryView == AtlasLibraryView.DORMANT
    val book = remember(dormant) { SourceAtlasFixtures.detailBook(dormant) }
    val source = book.source ?: AtlasFixtures.sourcePine
    val caller = SourceAtlasFixtures.detailCallerFor(context.libraryView)
    var localRemoveOpen by rememberSaveable { mutableStateOf(false) }
    var remoteOperation by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRemoteTarget by rememberSaveable { mutableStateOf("已收藏 › 长篇") }
    var tutorialOpen by remember(context.tutorial) { mutableStateOf(context.tutorial) }
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val scope = rememberCoroutineScope()
    val runScenario: (String) -> Unit = { key ->
        scope.launch { runtime.scenarios.run(key, book.id) }
    }

    AtlasScaffold(
        modifier = modifier,
        topBar = {
            AtlasTopBar(
                title = "书籍详情",
                subtitle = "来自 ${caller.label}",
                onUp = navigation.up,
                actions = listOf(AtlasTopBarAction(AtlasIcons.Cache, "缓存本书") { runScenario("detail-cache") }),
                overflow = listOf(
                    AtlasOverflowItem("刷新来源数据") { runScenario("detail-refresh") },
                    AtlasOverflowItem("在来源中打开本书") { navigation.navigateInRoot(AtlasFamily.SOURCE, AtlasRoute.BOOK_DETAIL) },
                ),
                selection = if (context.selectionMode) {
                    AtlasSelectionBar(
                        count = 3,
                        onClose = { repository.record("DetailSelectionClosed", book.id, "success") },
                        allSelected = false,
                        onToggleAll = { repository.record("DetailSelectionAll", book.id, "success") },
                        bulkActions = listOf(AtlasTopBarAction(AtlasIcons.Check, "标记已读") {
                            repository.putBoolean("book.${book.id}.read", true, "BookMarkedRead", book.id)
                        }),
                        overflow = listOf(AtlasOverflowItem("下载所选") { runScenario("detail-download-selection") }),
                    )
                } else null,
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (context.showOfflineBanner) {
                AtlasInfoBanner(
                    AtlasBanner(
                        title = AtlasStrings.OFFLINE_TITLE,
                        message = "Room 本地数据不受影响；来源数据为缓存快照。",
                    ),
                )
            }
            if (context.showMutationBanner) {
                AtlasMutationBanner(
                    AtlasMutationStatus(
                        phase = AtlasMutationPhase.SUCCESS,
                        message = "已加入稍后再读：《${book.title}》",
                    ),
                )
            }
            if (context.showUnresolvedBanner) {
                AtlasInfoBanner(
                    AtlasBanner(
                        title = AtlasStrings.UNRESOLVED_TITLE,
                        message = "加入网站收藏未收到确认；不会自动重试，其他远程操作暂时阻止。",
                        actionLabel = "以最新状态重试",
                        onAction = { runScenario("detail-remote-write") },
                        errorTone = true,
                    ),
                )
            }
            when (context.primaryState) {
                AtlasPageState.LOADING -> AtlasStateView(
                    kind = AtlasStateKind.LOADING,
                    title = AtlasStrings.LOADING,
                    modifier = Modifier.weight(1f),
                )
                AtlasPageState.ERROR -> AtlasStateView(
                    kind = AtlasStateKind.ERROR,
                    title = "详情加载失败",
                    message = "本地书架数据不受影响；来源详情获取超时。",
                    actionLabel = AtlasStrings.RETRY,
                    onAction = { runScenario("detail-refresh") },
                    modifier = Modifier.weight(1f),
                )
                else -> DetailContent(
                    context = context,
                    book = book,
                    source = source,
                    dormant = dormant,
                    onRemove = { localRemoveOpen = true },
                    onRemoteRemove = { remoteOperation = "remove" },
                    onRemoteMove = { remoteOperation = "move" },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (tutorialOpen) {
        AtlasFeatureIntroduction(
            featureId = "website-writeback",
            tutorialVersion = 1,
            title = "功能说明：网站写入",
            summary = "加入、移除与移动网站收藏是分开的显式远程操作。",
            points = listOf(
                "每次写入都要求签名能力、授权、凭据和最终确认。",
                "移动目标只能来自刚读取的签名列表。",
                "不确定结果不会显示成功，也不会自动重试。",
                "本地移出书架永远不会触发网站操作。",
            ),
            onDismiss = { tutorialOpen = false },
        )
    }

    if (context.showModal || localRemoveOpen) {
        ReviewDialog("移出书架", onDismiss = { localRemoveOpen = false }, destructive = true) {
            Column {
                Text(
                    "将《${book.title}》移出书架？本地 pin 与直接手动收藏关系会移除；稍后再读、评分、本地标签、阅读进度与历史全部保留。不会在网站上执行任何操作。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                DialogActionRow(
                    confirmLabel = "移出书架",
                    onCancel = { localRemoveOpen = false },
                    onConfirm = {
                        repository.putBoolean("book.${book.id}.inLibrary", false, "BookRemovedFromLibrary", book.id)
                        localRemoveOpen = false
                    },
                    destructive = true,
                )
            }
        }
    }
    if (remoteOperation == "remove") {
        ReviewDialog("从网站移除收藏？", onDismiss = { remoteOperation = null }, destructive = true) {
            Column {
                Text("将向「${source.name}」提交从网站收藏移除《${book.title}》的请求。本地书架、稍后再读、集合、评分、标签与阅读进度保持不变。只有 typed applied / already-absent 回执才显示完成；不确定结果会持续显示并阻止该书其他网站写入。")
                DialogActionRow("确认从网站移除", { remoteOperation = null }, {
                    runScenario("detail-remote-remove")
                    remoteOperation = null
                }, destructive = true)
            }
        }
    }
    if (remoteOperation == "move") {
        ReviewDialog("移动网站收藏", onDismiss = { remoteOperation = null }, destructive = true) {
            Column(verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                Text("目标来自「${source.name}」刚刚读取的签名列表；网站移动不会改变本地收藏夹。失效、未授权或陈旧目标不可选择。")
                listOf("已收藏 › 长篇", "已收藏 › 待读", "已收藏 › 完结").forEach { target ->
                    AtlasButton(
                        text = if (target == selectedRemoteTarget) "已选择：$target" else target,
                        onClick = { selectedRemoteTarget = target },
                        modifier = Modifier.fillMaxWidth(),
                        style = if (target == selectedRemoteTarget) AtlasButtonStyle.PRIMARY else AtlasButtonStyle.SECONDARY,
                    )
                }
                AtlasButton(
                    text = "旧目标（列表已刷新）",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    style = AtlasButtonStyle.SECONDARY,
                    enabled = false,
                )
                Text("最终目标：$selectedRemoteTarget。只有 typed applied / already-at-target 回执才显示完成；取消和失败均保留本地数据。")
                DialogActionRow("确认移动网站收藏", { remoteOperation = null }, {
                    repository.putString("book.${book.id}.remoteTarget", selectedRemoteTarget, "RemoteCollectionTargetChanged", book.id)
                    runScenario("detail-remote-move")
                    remoteOperation = null
                }, destructive = true)
            }
        }
    }
}

@Composable
private fun DialogActionRow(
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = AtlasSpacing.Lg),
        horizontalArrangement = Arrangement.End,
    ) {
        AtlasButton(AtlasStrings.CANCEL, onCancel, style = AtlasButtonStyle.TEXT)
        AtlasButton(
            confirmLabel,
            onConfirm,
            modifier = Modifier.padding(start = AtlasSpacing.Sm),
            style = if (destructive) AtlasButtonStyle.DESTRUCTIVE else AtlasButtonStyle.PRIMARY,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    context: AtlasContext,
    book: AtlasBook,
    source: AtlasSource,
    dormant: Boolean,
    onRemove: () -> Unit,
    onRemoteRemove: () -> Unit,
    onRemoteMove: () -> Unit,
    modifier: Modifier,
) {
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val rating = if (runtime.persistent) repository.int("detail.rating", book.rating ?: 0) else book.rating ?: 0
    val tags = if (runtime.persistent) repository.stringList("detail.tags", book.tags) else book.tags
    val readLater = if (runtime.persistent) repository.boolean("detail.readLater") else false
    val descending = repository.boolean("detail.chapters.descending")
    val unreadOnly = repository.boolean("detail.chapters.unreadOnly")
    val displayedChapters = SourceAtlasFixtures.chapters
        .filter { !unreadOnly || it.statusLabel.isNotEmpty() }
        .let { if (descending) it.reversed() else it }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val atDirectory by remember { derivedStateOf { scroll.value > 240 } }
    Column(modifier) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Row(Modifier.padding(AtlasSpacing.Md), verticalAlignment = Alignment.Top) {
                    AtlasCoverImage(
                        cover = book.cover,
                        title = book.title,
                        modifier = Modifier.size(width = 108.dp, height = 144.dp),
                        sourceColor = (source.branding as? AtlasBranding.Valid)?.color,
                    )
                    Column(Modifier.weight(1f).padding(start = AtlasSpacing.Md)) {
                        Text(book.title, style = MaterialTheme.typography.headlineSmall)
                        book.authors?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(book.progressLabel ?: "尚未开始", modifier = Modifier.padding(top = AtlasSpacing.Sm), style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.padding(top = AtlasSpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(5) { index ->
                                val selected = index < rating
                                AtlasIconButton(
                                    if (selected) AtlasIcons.Star else AtlasIcons.StarOutline,
                                    "${index + 1} 星评分",
                                    {
                                        val next = if (rating == index + 1) 0 else index + 1
                                        repository.putInt("detail.rating", next, "BookRatingChanged", book.id)
                                    },
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                        if (book.unreadUpdates > 0) Text("${book.unreadUpdates} 章待读", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (LocalAtlasEnvironment.current.eInk) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline) else null,
                ) {
                    Row(
                        Modifier
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = AtlasSpacing.Sm, vertical = AtlasSpacing.Xs),
                        horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                    ) {
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs),
                            verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs),
                        ) {
                            tags.forEach { tag -> DetailTagLabel(tag) }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs)) {
                            DetailTagAddButton {
                                val tag = "新标签 ${tags.size - book.tags.size + 1}"
                                repository.putStringList("detail.tags", tags + tag, "BookTagAdded", book.id)
                            }
                            DetailReadLaterButton(
                                selected = readLater,
                                onClick = { repository.putBoolean("detail.readLater", !readLater, "ReadLaterChanged", book.id) },
                            )
                        }
                    }
                }
                Section("简介")
                Text("雾港的旧灯塔再次亮起，记录员沿着失真的航线寻找一段被删去的夜航日志。", modifier = Modifier.padding(horizontal = AtlasSpacing.Md), style = MaterialTheme.typography.bodyMedium)
                if (dormant || context.showOfflineBanner) {
                    Text(if (dormant) "来源休眠；远端操作已停用。" else "正在显示缓存信息。", modifier = Modifier.padding(AtlasSpacing.Md), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Section("全部章节", "${SourceAtlasFixtures.DIRECTORY_TOTAL} 章")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = AtlasSpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                ) {
                    AtlasIconButton(AtlasIcons.Filter, if (unreadOnly) "显示全部章节" else "仅显示有状态章节", {
                        repository.putBoolean("detail.chapters.unreadOnly", !unreadOnly, "ChapterFilterChanged", book.id)
                    })
                    AtlasIconButton(AtlasIcons.Sort, "切换章节正倒序", {
                        repository.putBoolean("detail.chapters.descending", !descending, "ChapterSortChanged", book.id)
                    })
                    AtlasIconButton(AtlasIcons.Jump, "跳到当前章节", { navigation.navigate(AtlasRoute.BOOK_READER) })
                }
                displayedChapters.forEach { chapter ->
                    ChapterRow(chapter, selected = context.selectionMode, onClick = {
                        repository.putInt("reader.page", chapter.number.coerceIn(1, SourceAtlasFixtures.READER_PAGE_COUNT), "ChapterOpened", chapter.number.toString())
                        navigation.navigate(AtlasRoute.BOOK_READER)
                    })
                }
                Row(Modifier.fillMaxWidth().padding(AtlasSpacing.Md), horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                    AtlasButton("移出书架", onRemove, modifier = Modifier.weight(1f), style = AtlasButtonStyle.DESTRUCTIVE)
                    AtlasButton("网站操作", onRemoteMove, modifier = Modifier.weight(1f), style = AtlasButtonStyle.TEXT, enabled = !dormant)
                }
            }
            if (atDirectory) {
                FloatingActionButton(
                    onClick = { scope.launch { if (scroll.canScrollForward) scroll.animateScrollTo(scroll.maxValue) else scroll.animateScrollTo(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(AtlasSpacing.Md),
                ) { Icon(if (scroll.canScrollForward) AtlasIcons.Down else AtlasIcons.Up, contentDescription = if (scroll.canScrollForward) "快速到底" else "快速到顶") }
            } else {
                ExtendedFloatingActionButton(
                    onClick = { navigation.navigate(AtlasRoute.BOOK_READER) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(AtlasSpacing.Md),
                    icon = { Icon(AtlasIcons.Next, contentDescription = null) },
                    text = { Text("继续阅读") },
                )
            }
        }
    }
}


@Composable
private fun DetailTagLabel(text: String) {
    val eInk = LocalAtlasEnvironment.current.eInk
    Box(
        modifier = Modifier.height(ButtonDefaults.MinHeight + AtlasSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (eInk) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = if (eInk) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        ) {
            Box(
                modifier = Modifier.height(ButtonDefaults.MinHeight).padding(horizontal = AtlasSpacing.Sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DetailTagAddButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.minimumInteractiveComponentSize().height(ButtonDefaults.MinHeight),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = AtlasSpacing.Sm),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Icon(AtlasIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Text("添加标签", modifier = Modifier.padding(start = AtlasSpacing.Xs), style = MaterialTheme.typography.labelLarge)
    }
}
@Composable
private fun DetailReadLaterButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val eInk = LocalAtlasEnvironment.current.eInk
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize().heightIn(min = ButtonDefaults.MinHeight),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = AtlasSpacing.Sm),
        border = BorderStroke(
            if (eInk) 1.5.dp else 1.dp,
            if (eInk) AtlasEInkPalette.Ink else MaterialTheme.colorScheme.outline,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = when {
                eInk -> AtlasEInkPalette.Paper
                selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
            contentColor = if (eInk) AtlasEInkPalette.Ink else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(if (selected) "已稍后再读" else "稍后再读", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ChapterRow(
    chapter: SourceAtlasFixtures.AtlasChapter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val current = chapter.number == SourceAtlasFixtures.CURRENT_CHAPTER
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { this.selected = selected },
        color = when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            current -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(chapter.title, style = MaterialTheme.typography.bodyMedium)
                if (chapter.statusLabel.isNotEmpty()) {
                    Text(
                        chapter.statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (current) AtlasChip("当前")
        }
    }
}


// -- #10 Reader -----------------------------------------------------------------------------

@Composable
private fun BookReader(context: AtlasContext, modifier: Modifier) {
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val scope = rememberCoroutineScope()
    val eInk = LocalAtlasEnvironment.current.eInk
    val chapter = SourceAtlasFixtures.readerChapterFor(context.libraryView)
    var page by rememberSaveable(runtime.persistent) {
        mutableIntStateOf(if (runtime.persistent) repository.int("reader.page", SourceAtlasFixtures.READER_DEFAULT_PAGE) else SourceAtlasFixtures.READER_DEFAULT_PAGE)
    }
    var originPage by rememberSaveable { mutableIntStateOf(page) }
    var scrollMode by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("reader.scrollMode") else false) }
    var immersiveMode by rememberSaveable(runtime.persistent) { mutableStateOf(if (runtime.persistent) repository.boolean("reader.immersive") else context.readerImmersive) }
    var chrome by remember(context.capture, context.readerSeekPreview) {
        // Interactive launches expose a usable Up/settings path first; a page tap still hides chrome.
        mutableStateOf(!context.capture || context.review != null || context.readerSeekPreview != null)
    }
    val commitPage: (Int, String) -> Unit = { target, event ->
        page = target.coerceIn(1, SourceAtlasFixtures.READER_PAGE_COUNT)
        repository.putInt("reader.page", page, event, chapter.title)
    }
    var layer by rememberSaveable(context.showModal, context.readerSeekPreview) {
        mutableStateOf(if (context.showModal) "settings" else if (context.readerSeekPreview != null) "seek" else null)
    }
    val activeLayer = layer
    val reading = context.primaryState == AtlasPageState.CONTENT
    val guardedContent = chapter == SourceAtlasFixtures.ReaderChapterKind.VERIFICATION_REQUIRED
    val showChrome = !reading || context.showOfflineBanner || guardedContent || chrome || activeLayer != null
    val readerPresentation = LocalAtlasReaderPresentation.current
    SideEffect { readerPresentation.setChromeVisible(showChrome) }
    BackHandler(enabled = activeLayer in setOf("typography", "navigation", "page", "device")) { layer = "settings" }
    BackHandler(enabled = activeLayer != null) { layer = null }
    BackHandler(enabled = activeLayer == null && chrome) { chrome = false }

    AtlasScaffold(
        modifier = modifier,
        topBar = {
            if (showChrome) {
                AtlasTopBar(
                    title = chapter.title,
                    onUp = navigation.up,
                    actions = listOf(
                        AtlasTopBarAction(AtlasIcons.Chapters, "章节目录", { layer = "drawer" }),
                        AtlasTopBarAction(AtlasIcons.Search, "页内搜索") { repository.record("ReaderSearchOpened", chapter.title, "success") },
                    ),
                    overflow = listOf(
                        AtlasOverflowItem("添加书签") { repository.putBoolean("reader.bookmark.$page", true, "ReaderBookmarkAdded", page.toString()) },
                        AtlasOverflowItem("浏览书签") { repository.record("ReaderBookmarksOpened", chapter.title, "success") },
                    ),
                )
            }
        },
        footer = if (reading && (eInk || showChrome)) {
            {
                ReaderPosition(
                    page = page,
                    chapter = chapter.title,
                    onPrev = { if (page > 1) commitPage(page - 1, "ReaderPagePrevious") },
                    onNext = { if (page < SourceAtlasFixtures.READER_PAGE_COUNT) commitPage(page + 1, "ReaderPageNext") },
                    onSeek = { target ->
                        if (target == null) {
                            originPage = page
                            layer = "seek"
                        } else {
                            commitPage(target, "LocatorCommit")
                        }
                    },
                    onSettings = { layer = "settings" },
                )
            }
        } else null,
    ) {
        when (context.primaryState) {
            AtlasPageState.LOADING -> AtlasStateView(AtlasStateKind.LOADING, "正在加载章节…")
            AtlasPageState.ERROR -> AtlasStateView(AtlasStateKind.ERROR, "章节加载失败", message = "该章节尚未下载；已下载章节仍可离线阅读。", actionLabel = AtlasStrings.RETRY, onAction = {
                scope.launch { runtime.scenarios.run("reader-load", chapter.title) }
            })
            else -> Column(Modifier.fillMaxSize()) {
                if (context.showOfflineBanner) AtlasInfoBanner(AtlasBanner(AtlasStrings.OFFLINE_TITLE, "本地章节可正常阅读；来源操作已停用。"))
                Box(Modifier.weight(1f).fillMaxWidth().clickable(role = Role.Button, onClickLabel = "显示或隐藏阅读工具栏") { chrome = !chrome }) {
                    ReaderPage(chapter, page, scrollMode = scrollMode && !eInk, immersive = immersiveMode && !showChrome, chromeVisible = showChrome)
                }
            }
        }
    }

    when (activeLayer) {
        "settings", "typography", "navigation", "page", "device" -> ReaderSettingsContainer(
            inlinePreview = context.inlineModalPreview,
            eInk = eInk,
            page = activeLayer,
            scrollMode = scrollMode && !eInk,
            immersiveMode = immersiveMode,
            onScrollMode = {
                scrollMode = it && !eInk
                repository.putBoolean("reader.scrollMode", scrollMode, "ReaderModeChanged")
            },
            onImmersiveMode = {
                immersiveMode = it
                repository.putBoolean("reader.immersive", it, "ReaderImmersiveChanged")
                readerPresentation.setImmersive(it)
            },
            onNavigate = { layer = it },
            onDismiss = { layer = null },
        )
        "drawer" -> ReviewDialog("章节目录", onDismiss = { layer = null }) {
            Column {
                SourceAtlasFixtures.drawerChapters.forEachIndexed { index, title ->
                    AtlasButton(title, {
                        commitPage(index + 1, "ReaderChapterSelected")
                        layer = null
                    }, modifier = Modifier.fillMaxWidth(), style = AtlasButtonStyle.TEXT)
                }
            }
        }
        "seek" -> ReaderSeekPreview(
            openingPage = originPage,
            eInk = eInk,
            showReturnOrigin = context.readerSeekPreview == org.tsuyomi.prototype.uiatlas.model.AtlasReaderSeekPreview.RETURN_ORIGIN,
            onCancel = { layer = null },
            onCommit = { commitPage(it, "LocatorCommit"); layer = null },
        )
    }
}

@Composable
private fun ReaderSeekPreview(
    openingPage: Int,
    eInk: Boolean,
    showReturnOrigin: Boolean,
    onCancel: () -> Unit,
    onCommit: (Int) -> Unit,
) {
    var target by rememberSaveable { mutableFloatStateOf((openingPage + 7).coerceAtMost(SourceAtlasFixtures.READER_PAGE_COUNT).toFloat()) }
    val targetPage = target.toInt().coerceIn(1, SourceAtlasFixtures.READER_PAGE_COUNT)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        border = if (eInk) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        Column(Modifier.fillMaxSize()) {
            AtlasInfoBanner(
                AtlasBanner(
                    title = "EXPERIMENTAL / NOT APPROVED",
                    message = "阅读位置预览尚未获得实体设备视觉批准；确认只提交一次 LocatorCommit。",
                ),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ReaderPage(SourceAtlasFixtures.ReaderChapterKind.TEXT, targetPage, scrollMode = false, immersive = false, chromeVisible = true)
            }
            Text("第 $targetPage 页", modifier = Modifier.padding(horizontal = AtlasSpacing.Md), style = MaterialTheme.typography.labelLarge)
            if (eInk) {
                Row(Modifier.fillMaxWidth().padding(AtlasSpacing.Md), horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                    AtlasButton("上一页", { target = (target - 1).coerceAtLeast(1f) }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.SECONDARY)
                    AtlasButton("下一页", { target = (target + 1).coerceAtMost(SourceAtlasFixtures.READER_PAGE_COUNT.toFloat()) }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.SECONDARY)
                }
            } else {
                Slider(
                    value = target,
                    onValueChange = { target = it },
                    valueRange = 1f..SourceAtlasFixtures.READER_PAGE_COUNT.toFloat(),
                    steps = SourceAtlasFixtures.READER_PAGE_COUNT - 2,
                    modifier = Modifier.padding(horizontal = AtlasSpacing.Md),
                )
            }
            Row(Modifier.fillMaxWidth().padding(AtlasSpacing.Md), horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                AtlasButton("取消", onCancel, modifier = Modifier.weight(1f), style = AtlasButtonStyle.TEXT)
                AtlasButton(if (eInk) "确认跳转" else "跳到此处", { onCommit(targetPage) }, modifier = Modifier.weight(1f))
            }
            if (showReturnOrigin) AtlasButton("返回原位置 · 第 $openingPage 页", { onCommit(openingPage) }, modifier = Modifier.fillMaxWidth().padding(horizontal = AtlasSpacing.Md), style = AtlasButtonStyle.SECONDARY)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsContainer(
    inlinePreview: Boolean,
    eInk: Boolean,
    page: String,
    scrollMode: Boolean,
    immersiveMode: Boolean,
    onScrollMode: (Boolean) -> Unit,
    onImmersiveMode: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        ReaderSettingsContent(page, eInk, scrollMode, immersiveMode, onScrollMode, onImmersiveMode, onNavigate)
    }
    when {
        eInk -> EInkReaderSettingsPage(scrollMode, immersiveMode, onScrollMode, onImmersiveMode, onDismiss)
        inlinePreview -> BottomSheetScaffold(sheetContent = { content() }, sheetPeekHeight = 720.dp) {}
        else -> ModalBottomSheet(onDismissRequest = onDismiss) { content() }
    }
}

@Composable
private fun EInkReaderSettingsPage(
    scrollMode: Boolean,
    immersiveMode: Boolean,
    onScrollMode: (Boolean) -> Unit,
    onImmersiveMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val fontSize = if (runtime.persistent) repository.int("reader.fontSize", 18).toFloat() else 18f
    val lineHeight = if (runtime.persistent) repository.string("reader.lineSpacing", "1.7").toFloatOrNull() ?: 1.7f else 1.7f
    val margin = if (runtime.persistent) repository.int("reader.margin", 24).toFloat() else 24f
    val paragraphSpacing = if (runtime.persistent) repository.string("reader.paragraphSpacing", ".8").toFloatOrNull() ?: .8f else .8f
    val pageMargin = if (runtime.persistent) repository.int("reader.pageMargin", 20).toFloat() else 20f
    val volumePaging = if (runtime.persistent) repository.boolean("reader.volumePaging", true) else true
    val keepAwake = if (runtime.persistent) repository.boolean("reader.keepAwake", true) else true
    val lockPortrait = if (runtime.persistent) repository.boolean("reader.lockPortrait") else false
    val wide = containerWidth() >= 600.dp

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxSize()) {
            AtlasTopBar(title = "阅读设置", onUp = onDismiss)
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AtlasSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
            ) {
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Md), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md)) {
                            EInkSettingsSection("排版") {
                                EInkSliderSetting("字号", "${fontSize.toInt()}sp", fontSize, { repository.putInt("reader.fontSize", it.toInt(), "ReaderFontSizeChanged") }, 12f..32f, 19)
                                EInkSliderSetting("行距", "${"%.1f".format(lineHeight)}", lineHeight, { repository.putString("reader.lineSpacing", "%.1f".format(it), "ReaderLineSpacingChanged") }, 1.2f..2.2f, 9)
                                EInkSliderSetting("边距", "${margin.toInt()}dp", margin, { repository.putInt("reader.margin", it.toInt(), "ReaderMarginChanged") }, 12f..40f, 6)
                                EInkSliderSetting("段距", "${"%.1f".format(paragraphSpacing)}em", paragraphSpacing, { repository.putString("reader.paragraphSpacing", "%.1f".format(it), "ReaderParagraphSpacingChanged") }, 0f..1.6f, 7)
                                AtlasButton("系统 CJK 无衬线 · 常规", { repository.record("ReaderFontPickerOpened", "reader", "success") }, modifier = Modifier.fillMaxWidth(), style = AtlasButtonStyle.SECONDARY)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md)) {
                            EInkPageSettings(pageMargin, { repository.putInt("reader.pageMargin", it.toInt(), "ReaderPageMarginChanged") }, scrollMode, onScrollMode)
                            EInkNavigationSettings(volumePaging, { repository.putBoolean("reader.volumePaging", it, "ReaderVolumePagingChanged") })
                            EInkDeviceSettings(
                                keepAwake,
                                { repository.putBoolean("reader.keepAwake", it, "ReaderKeepAwakeChanged") },
                                lockPortrait,
                                { repository.putBoolean("reader.lockPortrait", it, "ReaderLockPortraitChanged") },
                                immersiveMode,
                                onImmersiveMode,
                            )
                        }
                    }
                } else {
                    EInkSettingsSection("排版") {
                        EInkSliderSetting("字号", "${fontSize.toInt()}sp", fontSize, { repository.putInt("reader.fontSize", it.toInt(), "ReaderFontSizeChanged") }, 12f..32f, 19)
                        EInkSliderSetting("行距", "${"%.1f".format(lineHeight)}", lineHeight, { repository.putString("reader.lineSpacing", "%.1f".format(it), "ReaderLineSpacingChanged") }, 1.2f..2.2f, 9)
                        EInkSliderSetting("边距", "${margin.toInt()}dp", margin, { repository.putInt("reader.margin", it.toInt(), "ReaderMarginChanged") }, 12f..40f, 6)
                        EInkSliderSetting("段距", "${"%.1f".format(paragraphSpacing)}em", paragraphSpacing, { repository.putString("reader.paragraphSpacing", "%.1f".format(it), "ReaderParagraphSpacingChanged") }, 0f..1.6f, 7)
                        AtlasButton("系统 CJK 无衬线 · 常规", { repository.record("ReaderFontPickerOpened", "reader", "success") }, modifier = Modifier.fillMaxWidth(), style = AtlasButtonStyle.SECONDARY)
                    }
                    EInkPageSettings(pageMargin, { repository.putInt("reader.pageMargin", it.toInt(), "ReaderPageMarginChanged") }, scrollMode, onScrollMode)
                    EInkNavigationSettings(volumePaging, { repository.putBoolean("reader.volumePaging", it, "ReaderVolumePagingChanged") })
                    EInkDeviceSettings(
                        keepAwake,
                        { repository.putBoolean("reader.keepAwake", it, "ReaderKeepAwakeChanged") },
                        lockPortrait,
                        { repository.putBoolean("reader.lockPortrait", it, "ReaderLockPortraitChanged") },
                        immersiveMode,
                        onImmersiveMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun EInkSettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(AtlasSpacing.Md), verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun EInkSliderSetting(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(44.dp))
        Text(valueLabel, Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge)
        Slider(value, onValueChange, Modifier.weight(1f), valueRange = valueRange, steps = steps)
    }
}

@Composable
private fun EInkSwitchSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked, null)
    }
}

@Composable
private fun EInkPageSettings(pageMargin: Float, onPageMargin: (Float) -> Unit, scrollMode: Boolean, onScrollMode: (Boolean) -> Unit) {
    EInkSettingsSection("页面") {
        EInkSliderSetting("页边", "${pageMargin.toInt()}dp", pageMargin, onPageMargin, 12f..40f, 6)
        Row(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
            FilterChip(selected = true, onClick = {}, label = { Text("黑白") })
            FilterChip(selected = !scrollMode, onClick = { onScrollMode(false) }, label = { Text("分页") })
            FilterChip(selected = scrollMode, onClick = {}, enabled = false, label = { Text("滚动") })
        }
    }
}

@Composable
private fun EInkNavigationSettings(volumePaging: Boolean, onVolumePaging: (Boolean) -> Unit) {
    EInkSettingsSection("导航") {
        Text("点击区域：左侧上一页 · 中间工具栏 · 右侧下一页")
        EInkSwitchSetting("音量键翻页", volumePaging, onVolumePaging)
        Text("进度轨：整书进度 · 显示章节刻度", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EInkDeviceSettings(
    keepAwake: Boolean,
    onKeepAwake: (Boolean) -> Unit,
    lockPortrait: Boolean,
    onLockPortrait: (Boolean) -> Unit,
    immersiveMode: Boolean,
    onImmersiveMode: (Boolean) -> Unit,
) {
    EInkSettingsSection("设备") {
        EInkSwitchSetting("保持屏幕常亮", keepAwake, onKeepAwake)
        EInkSwitchSetting("锁定竖屏", lockPortrait, onLockPortrait)
        EInkSwitchSetting("全屏沉浸", immersiveMode, onImmersiveMode)
    }
}

@Composable
private fun ReaderSettingsContent(
    page: String,
    eInk: Boolean,
    scrollMode: Boolean,
    immersiveMode: Boolean,
    onScrollMode: (Boolean) -> Unit,
    onImmersiveMode: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val fontSize = if (runtime.persistent) repository.int("reader.fontSize", 18).toFloat() else 18f
    val lineHeight = if (runtime.persistent) repository.string("reader.lineSpacing", "1.7").toFloatOrNull() ?: 1.7f else 1.7f
    val margin = if (runtime.persistent) repository.int("reader.margin", 24).toFloat() else 24f
    val paragraphSpacing = if (runtime.persistent) repository.string("reader.paragraphSpacing", ".8").toFloatOrNull() ?: .8f else .8f
    val pageMarginFraction = if (runtime.persistent) repository.string("reader.pageMarginFraction", ".35").toFloatOrNull() ?: .35f else .35f
    val volumePaging = if (runtime.persistent) repository.boolean("reader.volumePaging", true) else true
    val keepAwake = if (runtime.persistent) repository.boolean("reader.keepAwake", true) else true
    val lockPortrait = if (runtime.persistent) repository.boolean("reader.lockPortrait") else false
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AtlasSpacing.Lg)
            .padding(
                top = if (eInk) AtlasSpacing.Xxl + AtlasSpacing.Md else AtlasSpacing.Sm,
                bottom = if (eInk) AtlasSpacing.Lg else AtlasSpacing.Sm,
            ),
        verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
    ) {
        if (page != "settings") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AtlasIconButton(AtlasIcons.Back, "返回快速设置", { onNavigate("settings") })
                Text(
                    when (page) { "typography" -> "排版"; "page" -> "页面"; "navigation" -> "导航"; else -> "设备" },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        when (page) {
            "typography" -> {
                Text("字体与字重", style = MaterialTheme.typography.titleMedium)
                AtlasButton("系统 CJK 无衬线 · 常规", { repository.record("ReaderFontPickerOpened", "reader", "success") }, modifier = Modifier.fillMaxWidth(), style = AtlasButtonStyle.SECONDARY)
                Text("字距 0 · 首行缩进 2 字 · 两端对齐")
            }
            "page" -> {
                Text("上下边距 ${(pageMarginFraction * 57).toInt()}dp", style = MaterialTheme.typography.titleMedium)
                Slider(value = pageMarginFraction, onValueChange = { repository.putString("reader.pageMarginFraction", it.toString(), "ReaderPageMarginChanged") })
                Row(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                    FilterChip(selected = true, onClick = { repository.record("ReaderPaperStyleSelected", if (eInk) "monochrome" else "paper", "success") }, label = { Text(if (eInk) "黑白" else "纸张") })
                    FilterChip(selected = !scrollMode, onClick = { onScrollMode(false) }, label = { Text("分页") })
                    FilterChip(selected = scrollMode, onClick = { onScrollMode(true) }, label = { Text("滚动") }, enabled = !eInk)
                }
            }
            "navigation" -> {
                Text("点击区域：左侧上一页 · 中间工具栏 · 右侧下一页")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("音量键翻页", Modifier.weight(1f))
                    Switch(volumePaging, { repository.putBoolean("reader.volumePaging", it, "ReaderVolumePagingChanged") })
                }
                Text("进度轨：整书进度 · 显示章节刻度")
            }
            "device" -> {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("保持屏幕常亮", Modifier.weight(1f)); Switch(keepAwake, { repository.putBoolean("reader.keepAwake", it, "ReaderKeepAwakeChanged") }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("锁定竖屏", Modifier.weight(1f)); Switch(lockPortrait, { repository.putBoolean("reader.lockPortrait", it, "ReaderLockPortraitChanged") }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("全屏沉浸", Modifier.weight(1f)); Switch(immersiveMode, onImmersiveMode) }
            }
            else -> {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("字号", Modifier.width(44.dp)); Text("${fontSize.toInt()}sp", Modifier.width(58.dp)); Slider(fontSize, { repository.putInt("reader.fontSize", it.toInt(), "ReaderFontSizeChanged") }, Modifier.weight(1f), valueRange = 12f..32f, steps = 19)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("行距", Modifier.width(44.dp)); Text("${"%.1f".format(lineHeight)}", Modifier.width(58.dp)); Slider(lineHeight, { repository.putString("reader.lineSpacing", "%.1f".format(it), "ReaderLineSpacingChanged") }, Modifier.weight(1f), valueRange = 1.2f..2.2f, steps = 9)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("边距", Modifier.width(44.dp)); Text("${margin.toInt()}dp", Modifier.width(58.dp)); Slider(margin, { repository.putInt("reader.margin", it.toInt(), "ReaderMarginChanged") }, Modifier.weight(1f), valueRange = 12f..40f, steps = 6)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("段距", Modifier.width(44.dp)); Text("${"%.1f".format(paragraphSpacing)}em", Modifier.width(58.dp)); Slider(paragraphSpacing, { repository.putString("reader.paragraphSpacing", "%.1f".format(it), "ReaderParagraphSpacingChanged") }, Modifier.weight(1f), valueRange = 0f..1.6f, steps = 7)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs)) {
                    AtlasButton("排版", { onNavigate("typography") }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.TEXT)
                    AtlasButton("页面", { onNavigate("page") }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.TEXT)
                    AtlasButton("导航", { onNavigate("navigation") }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.TEXT)
                    AtlasButton("设备", { onNavigate("device") }, modifier = Modifier.weight(1f), style = AtlasButtonStyle.TEXT)
                }
            }
        }
    }
}

@Composable
private fun ReaderPage(
    kind: SourceAtlasFixtures.ReaderChapterKind,
    page: Int,
    scrollMode: Boolean,
    immersive: Boolean,
    chromeVisible: Boolean,
) {
    val navigation = LocalAtlasNavigation.current
    when (kind) {
        SourceAtlasFixtures.ReaderChapterKind.VERIFICATION_REQUIRED -> Column(
            Modifier.fillMaxSize().padding(AtlasSpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(AtlasIcons.Verify, contentDescription = null, modifier = Modifier.size(48.dp))
            Text("该章节需要完成验证", style = MaterialTheme.typography.titleMedium)
            AtlasButton("前往验证", { navigation.navigateInRoot(AtlasFamily.SOURCE, AtlasRoute.SOURCE_VERIFICATION) }, modifier = Modifier.padding(top = AtlasSpacing.Lg))
        }
        SourceAtlasFixtures.ReaderChapterKind.IMAGE -> ImagePage(page)
        else -> {
            val eInk = LocalAtlasEnvironment.current.eInk
            val pageModifier = if (eInk || !scrollMode) Modifier.fillMaxSize() else Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            val topInset = when {
                immersive -> Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                !chromeVisible -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                else -> Modifier
            }
            Column(topInset.then(pageModifier).padding(AtlasSpacing.Md)) {
                Text(
                    if (eInk) SourceAtlasFixtures.readerEInkPageText(page) else SourceAtlasFixtures.readerPageText(page),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Default),
                )
            }
        }
    }
}

@Composable
private fun ImagePage(page: Int) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(Modifier.fillMaxSize().padding(AtlasSpacing.Md), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(color, Offset(size.width * .1f, size.height * .8f), Offset(size.width * .5f, size.height * .3f), 4f)
                drawLine(color, Offset(size.width * .5f, size.height * .3f), Offset(size.width * .9f, size.height * .7f), 4f)
            }
            Text("图片页 $page", modifier = Modifier.align(Alignment.BottomCenter).padding(AtlasSpacing.Md))
        }
    }
}

@Composable
private fun ReaderPosition(
    page: Int,
    chapter: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int?) -> Unit,
    onSettings: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Column {
        Canvas(
            Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button, onClickLabel = "跳到下一刻度") {
                onSeek((page + 8).coerceAtMost(SourceAtlasFixtures.READER_PAGE_COUNT))
            },
        ) {
            val y = size.height / 2f
            drawLine(outline, Offset(24f, y), Offset(size.width - 24f, y), 3f)
            repeat(7) { tick ->
                val x = 24f + (size.width - 48f) * tick / 6f
                drawLine(ink, Offset(x, y - 8f), Offset(x, y + 8f), if (tick == 2) 5f else 2f)
            }
            val position = 24f + (size.width - 48f) * (page - 1f) / (SourceAtlasFixtures.READER_PAGE_COUNT - 1f)
            drawCircle(accent, 8f, Offset(position, y))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            AtlasIconButton(AtlasIcons.Prev, "上一章", onPrev, enabled = page > 1)
            Column(
                Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "拖动预览阅读位置") { onSeek(null) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(chapter, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("全书 $page / ${SourceAtlasFixtures.READER_PAGE_COUNT}", style = MaterialTheme.typography.labelSmall)
            }
            AtlasIconButton(AtlasIcons.Next, "下一章", onNext, enabled = page < SourceAtlasFixtures.READER_PAGE_COUNT)
            AtlasIconButton(AtlasIcons.Settings, "阅读设置", onSettings)
        }
    }
}

// -- #11 Browse root ------------------------------------------------------------------------

@Composable
private fun BrowseRoot(context: AtlasContext, modifier: Modifier) {
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val scope = rememberCoroutineScope()
    val variant = context.variant
    val useFab = variant != null && variant.id.uppercaseChar() == 'A' && variant.option == "b"
    val importSource: () -> Unit = { scope.launch { runtime.scenarios.run("source-import", "browse") } }
    val refreshSources: () -> Unit = { scope.launch { runtime.scenarios.run("source-refresh", "browse") } }
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            AtlasTopBar(
                title = "浏览",
                subtitle = "已安装 3 · 可安装 1",
                actions = buildList {
                    add(AtlasTopBarAction(AtlasIcons.Search, "聚合搜索") { navigation.navigateSearch(null) })
                    if (!useFab) add(AtlasTopBarAction(AtlasIcons.Add, "导入源", importSource))
                },
                overflow = listOf(AtlasOverflowItem("刷新源列表", refreshSources)),
            )
        },
        floatingAction = if (useFab) {
            {
                FloatingActionButton(onClick = importSource) {
                    Icon(AtlasIcons.Add, contentDescription = "导入源")
                }
            }
        } else null,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (context.showMutationBanner) {
                AtlasMutationBanner(
                    if (context.libraryView == AtlasLibraryView.ALL) {
                        AtlasMutationStatus(
                            AtlasMutationPhase.WORKING,
                            "正在安装 源·苇 v0.3（第 2/3 步：校验签名）",
                        )
                    } else {
                        AtlasMutationStatus(
                            AtlasMutationPhase.ERROR,
                            "安装失败：签名校验未通过，未写入任何数据。",
                            AtlasStrings.RETRY,
                            importSource,
                        )
                    },
                )
            }
            when (context.primaryState) {
                AtlasPageState.LOADING -> AtlasStateView(AtlasStateKind.LOADING, AtlasStrings.LOADING, Modifier.weight(1f))
                AtlasPageState.EMPTY -> AtlasStateView(
                    AtlasStateKind.EMPTY,
                    "还没有安装内容源",
                    Modifier.weight(1f),
                    "导入源扩展包后，即可从统一搜索中预选此来源，并浏览网站收藏。",
                    "导入源扩展包",
                    importSource,
                )
                AtlasPageState.ERROR -> AtlasStateView(
                    AtlasStateKind.ERROR,
                    "源列表加载失败",
                    Modifier.weight(1f),
                    "本地扩展注册表读取异常；已安装源数据未受影响。",
                    AtlasStrings.RETRY,
                    refreshSources,
                )
                else -> BrowseContent(
                    context,
                    navigation,
                    { scope.launch { runtime.scenarios.run("source-install", SourceAtlasFixtures.installableSource.name) } },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BrowseContent(
    context: AtlasContext,
    navigation: org.tsuyomi.prototype.uiatlas.model.AtlasNavigationActions,
    onInstall: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Section("已安装")
        AtlasFixtures.installedSources.forEach { SourceCard(it, navigation) }
        Section("可安装")
        InstallableCard(context, onInstall)
        Spacer(Modifier.height(AtlasSpacing.Lg))
    }
}

@Composable
private fun SourceCard(source: AtlasSource, navigation: org.tsuyomi.prototype.uiatlas.model.AtlasNavigationActions) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val repository = prototypeRepository()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Xs),
        border = if (eInk) BorderStroke(1.5.dp, AtlasEInkPalette.N90) else null,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(AtlasSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AtlasSourceMarkCanvas(
                    source.mark,
                    if (eInk) AtlasEInkPalette.N70 else MaterialTheme.colorScheme.primary,
                    Modifier.size(40.dp),
                )
                Column(Modifier.padding(start = AtlasSpacing.Md)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        source.capabilityLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val usable = !source.dormant && !source.credentialExpired
            if (!usable) {
                Text(
                    if (source.dormant) "休眠：远程功能暂停，本地缓存仍可浏览。" else "凭据过期：重新登录后恢复。",
                    modifier = Modifier.padding(top = AtlasSpacing.Sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                AtlasButton("搜索此来源", { navigation.navigateSearch(source.id) }, style = AtlasButtonStyle.TEXT, enabled = usable)
                AtlasButton("网站收藏", { navigation.navigate(AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY) }, style = AtlasButtonStyle.TEXT, enabled = usable)
                if (source.credentialExpired) {
                    AtlasButton("重新登录", {
                        repository.record("SourceReloginOpened", source.id, "success")
                        navigation.navigate(AtlasRoute.SOURCE_VERIFICATION)
                    }, style = AtlasButtonStyle.SECONDARY)
                }
            }
        }
    }
}

@Composable
private fun InstallableCard(context: AtlasContext, onInstall: () -> Unit) {
    val environment = LocalAtlasEnvironment.current
    val pkg = SourceAtlasFixtures.installableSource
    val variant = context.variant
    val instant = variant != null && variant.id.uppercaseChar() == 'F' && variant.option == "b"
    var expanded by rememberSaveable(variant?.toString()) {
        mutableStateOf(variant?.id?.uppercaseChar() == 'F')
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtlasSpacing.Md),
        border = if (environment.eInk) BorderStroke(1.5.dp, AtlasEInkPalette.N90) else null,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(AtlasSpacing.Md)) {
            Text("${pkg.name} ${pkg.version}", style = MaterialTheme.typography.titleMedium)
            Text(pkg.summary, style = MaterialTheme.typography.bodySmall)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .heightIn(min = 48.dp)
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (expanded) AtlasIcons.Collapse else AtlasIcons.Expand, contentDescription = null)
                Text(
                    if (expanded) "收起能力差异" else "展开能力差异（新增 2 项 · 移除 1 项）",
                    modifier = Modifier.padding(start = AtlasSpacing.Sm),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            val diff: @Composable () -> Unit = {
                Column {
                    pkg.diffAdded.forEach { KeyValue("新增", it) }
                    pkg.diffRemoved.forEach { KeyValue("移除", it) }
                }
            }
            if (instant || environment.instantMotion) {
                if (expanded) diff()
            } else {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(AtlasMotion.EXPAND_MS)) + fadeIn(tween(AtlasMotion.FADE_IN_MS)),
                ) { diff() }
            }
            AtlasButton("安装", onInstall, style = AtlasButtonStyle.SECONDARY)
        }
    }
}

// -- #12 unified basic search ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalSearch(context: AtlasContext, modifier: Modifier) {
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val scope = rememberCoroutineScope()
    val eInk = LocalAtlasEnvironment.current.eInk
    val activeSourceIds = SourceAtlasFixtures.searchDescriptors
        .map { it.source }
        .filter { !it.dormant && !it.credentialExpired }
        .map { it.id }
        .toSet()
    val submittedSourceIds = context.selectedSearchSourceId
        ?.takeIf { it in activeSourceIds }
        ?.let(::setOf)
        ?: activeSourceIds
    var query by rememberSaveable(runtime.persistent) {
        mutableStateOf(if (runtime.persistent) repository.string("search.query", "雾港") else "雾港")
    }
    var submittedQuery by rememberSaveable { mutableStateOf(query) }
    var layout by rememberSaveable(context.layout?.name, runtime.persistent) {
        mutableStateOf(
            if (runtime.persistent) AtlasLayout.entries.firstOrNull { it.name == repository.string("search.layout") }
                ?: (context.layout ?: AtlasLayout.LIST) else context.layout ?: AtlasLayout.LIST,
        )
    }
    val submit: () -> Unit = {
        submittedQuery = query
        repository.putString("search.query", query, "SearchSubmitted", context.selectedSearchSourceId ?: "all")
        scope.launch { runtime.scenarios.run("search", context.selectedSearchSourceId ?: "all") }
    }
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            AtlasTopBar(
                title = "搜索",
                actions = listOf(
                    AtlasTopBarAction(layout.currentLayoutIcon(), layout.layoutToggleContentDescription()) {
                        layout = layout.nextAtlasLayout()
                        repository.putString("search.layout", layout.name, "SearchLayoutChanged")
                    },
                ),
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { if (it.length <= SourceAtlasFixtures.SEARCH_QUERY_CAP) query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
                label = { Text("搜索") },
                trailingIcon = { AtlasIconButton(AtlasIcons.Search, "提交搜索", submit) },
                supportingText = { Text("${query.length} / ${SourceAtlasFixtures.SEARCH_QUERY_CAP}") },
                singleLine = true,
            )
            when (context.primaryState) {
                AtlasPageState.LOADING -> AtlasStateView(AtlasStateKind.LOADING, "正在搜索", Modifier.weight(1f))
                AtlasPageState.EMPTY -> AtlasStateView(AtlasStateKind.EMPTY, "没有找到「$query」", Modifier.weight(1f), "可调整关键词后重新搜索。")
                AtlasPageState.ERROR -> AtlasStateView(AtlasStateKind.ERROR, "部分搜索失败", Modifier.weight(1f), "已完成的结果仍保留。", AtlasStrings.RETRY, submit)
                else -> AggregatedSearchResults(submittedQuery, layout, submittedSourceIds, eInk, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AggregatedSearchResults(
    submittedQuery: String,
    layout: AtlasLayout,
    submittedSourceIds: Set<String>,
    eInk: Boolean,
    modifier: Modifier,
) {
    val navigation = LocalAtlasNavigation.current
    val all = SourceAtlasFixtures.aggregatedSearchResults
    val submitted = submittedQuery.trim()
    val local = all.take(2).filter { submitted.isNotBlank() && it.title.contains(submitted, ignoreCase = true) }
    val remote = all.drop(2).filter {
        submitted.isNotBlank() && it.source?.id in submittedSourceIds && it.title.contains(submitted, ignoreCase = true)
    }
    val merged = buildList {
        addAll(local)
        remote.forEach { candidate ->
            if (candidate.identity == null || none { it.identity == candidate.identity }) add(candidate)
        }
    }.map { it.copy(dormantSource = false) }
    Column(if (eInk) modifier else modifier.verticalScroll(rememberScrollState())) {
        if (submitted.isBlank()) {
            AtlasStateView(AtlasStateKind.EMPTY, "输入关键词后搜索", message = "", modifier = Modifier.fillMaxWidth())
        } else {
            AggregatedSearchGroup(null, merged, layout) { navigation.navigate(AtlasRoute.BOOK_DETAIL) }
        }
    }
}
@Composable
private fun AggregatedSearchGroup(
    title: String?,
    books: List<AtlasBook>,
    layout: AtlasLayout,
    onBook: () -> Unit,
) {
    if (books.isEmpty()) return
    if (title != null) Section(title)
    when (layout) {
        AtlasLayout.LIST -> books.forEach { book -> BookListItemRow(book = book, onClick = onBook, showSourceChip = book.source != null) }
        AtlasLayout.COMPACT -> books.forEach { book -> CompactBookListItem(book = book, onClick = onBook) }
        AtlasLayout.GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns()),
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            contentPadding = PaddingValues(AtlasSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
        ) {
            gridItems(books, key = { it.identity?.let { identity -> "${identity.sourceId}:${identity.remoteBookId}" } ?: it.id }) { book ->
                BookGridCard(book = book, onClick = onBook, showSourceChip = book.source != null)
            }
        }
    }
}

@Composable
private fun RemoteLibrary(context: AtlasContext, modifier: Modifier) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val gate = SourceAtlasFixtures.remoteGateFor(context.libraryView)
    val navigation = LocalAtlasNavigation.current
    val source = brandingSource(context.libraryView)
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val scope = rememberCoroutineScope()
    val gated = gate != SourceAtlasFixtures.RemoteGate.NONE && gate != SourceAtlasFixtures.RemoteGate.DORMANT
    var importOpen by remember(context.state) { mutableStateOf(context.state == AtlasPageState.MODAL) }
    val pageSize = SourceAtlasFixtures.REMOTE_PAGE_SIZE
    val pages = ceil(SourceAtlasFixtures.remoteEntries.size.toDouble() / pageSize).toInt()
    var page by rememberSaveable { mutableIntStateOf(1) }
    val action = rowActionOption(context.variant)
    var layout by rememberSaveable(context.layout?.name, runtime.persistent) {
        mutableStateOf(
            if (runtime.persistent) AtlasLayout.entries.firstOrNull { it.name == repository.string("remote.layout") }
                ?: (context.layout ?: AtlasLayout.LIST) else context.layout ?: AtlasLayout.LIST,
        )
    }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    val refresh: () -> Unit = { scope.launch { runtime.scenarios.run("remote-library-refresh", source.id) } }
    val runImport: () -> Unit = { scope.launch { runtime.scenarios.run("remote-library-import", source.id) } }

    AtlasScaffold(
        modifier = modifier,
        topBar = {
            AtlasTopBar(
                title = if (selected.isEmpty()) source.name else "已选择 ${selected.size} 项",
                subtitle = "网站收藏 · 共 ${SourceAtlasFixtures.IMPORT_TOTAL} 项",
                onUp = if (selected.isEmpty()) navigation.up else ({ selected = emptySet() }),
                actions = if (selected.isEmpty()) {
                    listOf(
                        AtlasTopBarAction(AtlasIcons.Refresh, "刷新列表", refresh),
                        AtlasTopBarAction(AtlasIcons.CopyAll, "全部复制", { importOpen = true }),
                    )
                } else {
                    listOf(AtlasTopBarAction(AtlasIcons.CopyAll, "复制所选", { importOpen = true }))
                },
                overflow = listOf(AtlasOverflowItem(layout.layoutToggleContentDescription()) {
                    layout = layout.nextAtlasLayout()
                    repository.putString("remote.layout", layout.name, "RemoteLibraryLayoutChanged", source.id)
                }),
            )
        },
        footer = if (eInk && !gated && context.primaryState == AtlasPageState.CONTENT) {
            { PaginationBar(page, pages, { if (page > 1) page-- }, { if (page < pages) page++ }) }
        } else null,
    ) {
        Column(Modifier.fillMaxSize()) {
            when {
                gated -> AtlasStateView(
                    AtlasStateKind.EMPTY,
                    gate.title,
                    Modifier.weight(1f),
                    gate.message,
                    gate.actionLabel,
                    gate.actionLabel?.let { { navigation.navigate(AtlasRoute.SOURCE_VERIFICATION) } },
                )
                context.primaryState == AtlasPageState.LOADING -> AtlasStateView(
                    AtlasStateKind.LOADING,
                    AtlasStrings.LOADING,
                    Modifier.weight(1f),
                )
                context.primaryState == AtlasPageState.EMPTY -> AtlasStateView(
                    AtlasStateKind.EMPTY,
                    "网站收藏为空",
                    Modifier.weight(1f),
                    "当前只读列表没有可复制的书籍；刷新不会创建本地 pin 或镜像。",
                    "刷新列表",
                    refresh,
                )
                context.primaryState == AtlasPageState.ERROR -> AtlasStateView(
                    AtlasStateKind.ERROR,
                    "网站收藏读取失败：网络中断",
                    Modifier.weight(1f),
                    "本地书架与网站镜像未改动；可安全重新读取列表。",
                    AtlasStrings.RETRY,
                    refresh,
                )
                else -> RemoteContent(
                    context,
                    gate,
                    action,
                    eInk,
                    page,
                    pageSize,
                    layout,
                    selected,
                    selected.isNotEmpty(),
                    { bookId -> selected = if (bookId in selected) selected - bookId else selected + bookId },
                    Modifier.weight(1f),
                )
            }
        }
    }
    if (importOpen) {
        ReviewDialog("复制网站收藏到本地书架", onDismiss = { importOpen = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                Text("将网站收藏复制到本地书架；已经存在的书籍保持不变。不会创建或校准网站镜像，也不会向网站写入。")
                Text("确认后显示逐项进度；取消只停止尚未处理的项目。")
                DialogActionRow(
                    confirmLabel = "确认复制到本地书架",
                    onCancel = { importOpen = false },
                    onConfirm = {
                        runImport()
                        repository.putInt(
                            "remote.lastImportCount",
                            if (selected.isEmpty()) SourceAtlasFixtures.IMPORT_TOTAL else selected.size,
                            "RemoteLibraryImported",
                            source.id,
                        )
                        importOpen = false
                    },
                    destructive = false,
                )
            }
        }
    }
}

@Composable
private fun RemoteContent(
    context: AtlasContext,
    gate: SourceAtlasFixtures.RemoteGate,
    action: RowActionOption,
    eInk: Boolean,
    page: Int,
    pageSize: Int,
    layout: AtlasLayout,
    selected: Set<String>,
    selectionMode: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    Column(modifier) {
        if (gate == SourceAtlasFixtures.RemoteGate.DORMANT) {
            AtlasInfoBanner(AtlasBanner("来源休眠", "网站收藏列表当前不可读取；本地书架与既有镜像快照不受影响。"))
        }
        if (context.showMutationBanner) {
            AtlasInfoBanner(
                AtlasBanner(
                    "正在复制到本地书架",
                    "已创建 ${SourceAtlasFixtures.IMPORT_DONE} / ${SourceAtlasFixtures.IMPORT_TOTAL} 个本地 pin · 取消只停止后续项目，已完成的本地项保留",
                    AtlasStrings.CANCEL,
                    { repository.record("RemoteImportCancelled", "remote-library", "cancelled") },
                ),
            )
        }
        if (context.showUnresolvedBanner) {
            AtlasInfoBanner(
                AtlasBanner(
                    "本地导入报告可重试",
                    "3 项本地 pin 写入失败；未向网站提交任何写入，也不会阻止网站操作。",
                    "重试失败项",
                    { repository.record("RemoteImportRetryRequested", "remote-library", "queued") },
                    true,
                ),
            )
        }
        if (SourceAtlasFixtures.remoteShowsPartial(context.libraryView)) {
            AtlasMutationBanner(
                AtlasMutationStatus(
                    AtlasMutationPhase.ERROR,
                    "已复制 ${SourceAtlasFixtures.IMPORT_PARTIAL_DONE} 项，${SourceAtlasFixtures.IMPORT_PARTIAL_FAILED} 项本地写入失败",
                ),
            )
            SourceAtlasFixtures.importFailureRows.forEach { (book, cause) -> KeyValue(book, cause) }
            AtlasButton("重试失败项", {
                repository.record("RemoteImportRetryRequested", "remote-library", "queued")
            }, style = AtlasButtonStyle.TEXT)
        }
        val entries = if (eInk) {
            SourceAtlasFixtures.remoteEntries.drop((page - 1) * pageSize).take(pageSize)
        } else SourceAtlasFixtures.remoteEntries
        when (layout) {
            AtlasLayout.GRID -> if (eInk) {
                val columns = gridColumns()
                Column(Modifier.fillMaxSize()) {
                    entries.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                            row.forEach { entry ->
                                BookGridCard(
                                    entry.book,
                                    { if (selectionMode) onSelect(entry.book.id) else navigation.navigate(AtlasRoute.BOOK_DETAIL) },
                                    modifier = Modifier.weight(1f),
                                    selected = entry.book.id in selected,
                                    onLongClick = { onSelect(entry.book.id) },
                                    showSourceChip = false,
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns()),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(AtlasSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                ) {
                    gridItems(entries, key = { it.book.id }) { entry ->
                        BookGridCard(entry.book, { if (selectionMode) onSelect(entry.book.id) else navigation.navigate(AtlasRoute.BOOK_DETAIL) }, selected = entry.book.id in selected, onLongClick = { onSelect(entry.book.id) }, showSourceChip = false)
                    }
                }
            }
            AtlasLayout.COMPACT -> Column(Modifier.fillMaxSize().then(if (eInk) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
                entries.forEach { entry -> CompactBookListItem(entry.book, { if (selectionMode) onSelect(entry.book.id) else navigation.navigate(AtlasRoute.BOOK_DETAIL) }, selected = entry.book.id in selected, onLongClick = { onSelect(entry.book.id) }) }
            }
            AtlasLayout.LIST -> if (eInk) {
                Column(Modifier.fillMaxSize()) {
                    entries.forEach { RemoteRow(it, action, it.book.id in selected, selectionMode) { onSelect(it.book.id) } }
                    Spacer(Modifier.weight(1f))
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.book.id }) { RemoteRow(it, action, it.book.id in selected, selectionMode) { onSelect(it.book.id) } }
                }
            }
        }
    }
}

@Composable
private fun RemoteRow(
    entry: SourceAtlasFixtures.RemoteEntry,
    action: RowActionOption,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onSelect: () -> Unit = {},
) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    BookListItemRow(
        entry.book,
        { if (selectionMode) onSelect() else navigation.navigate(AtlasRoute.BOOK_DETAIL) },
        selected = selected,
        onLongClick = onSelect,
        trailing = {
            RowAction(
                option = action,
                label = if (entry.onShelf) "已在书架" else "加入书架",
                enabled = !entry.onShelf,
                onAction = { repository.putBoolean("book.${entry.book.id}.inLibrary", true, "RemoteBookCopied", entry.book.id) },
                onDetails = { navigation.navigate(AtlasRoute.BOOK_DETAIL) },
            )
        },
    )
}

// -- #18 verification -----------------------------------------------------------------------

@Composable
private fun SourceVerification(context: AtlasContext, modifier: Modifier) {
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val scope = rememberCoroutineScope()
    val retry: () -> Unit = { scope.launch { runtime.scenarios.run("source-verification", "atlas.pine") } }
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            AtlasTopBar(
                title = "登录验证",
                subtitle = "源·松 · atlas.pine",
                onUp = navigation.up,
            )
        },
    ) {
        when (context.primaryState) {
            AtlasPageState.ERROR -> AtlasStateView(
                AtlasStateKind.ERROR,
                "验证页面加载失败",
                message = "页面视图无法加载（无网络）；失败不会产生任何写入。",
                actionLabel = AtlasStrings.RETRY,
                onAction = retry,
            )
            AtlasPageState.LOADING -> AtlasStateView(AtlasStateKind.LOADING, AtlasStrings.LOADING)
            else -> VerificationContent(
                onComplete = {
                    repository.putBoolean("source.atlas.pine.verified", true, "SourceVerificationCompleted", "atlas.pine")
                    navigation.up()
                },
                onCancel = {
                    repository.record("SourceVerificationCancelled", "atlas.pine", "cancelled")
                    navigation.up()
                },
            )
        }
    }
}

@Composable
private fun VerificationContent(onComplete: () -> Unit, onCancel: () -> Unit) {
    val eInk = LocalAtlasEnvironment.current.eInk
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AtlasSpacing.Md),
    ) {
        Surface(
            border = if (eInk) BorderStroke(1.5.dp, AtlasEInkPalette.N90) else null,
            color = if (eInk) AtlasEInkPalette.Paper else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(Modifier.padding(AtlasSpacing.Md)) {
                Icon(AtlasIcons.Info, contentDescription = null)
                Column(Modifier.padding(start = AtlasSpacing.Md)) {
                    Text(SourceAtlasFixtures.VERIFICATION_HOST_NOTICE, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        SourceAtlasFixtures.VERIFICATION_STATUS_WAITING,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        VerificationStub(Modifier.padding(top = AtlasSpacing.Md))
        Text(
            SourceAtlasFixtures.VERIFICATION_STUB_CAPTION,
            modifier = Modifier.padding(top = AtlasSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AtlasButton(
            "我已完成验证",
            onComplete,
            Modifier
                .fillMaxWidth()
                .padding(top = AtlasSpacing.Lg),
        )
        AtlasButton(
            "取消验证",
            onCancel,
            Modifier
                .fillMaxWidth()
                .padding(top = AtlasSpacing.Sm),
            AtlasButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun VerificationStub(modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.padding(AtlasSpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("网页视图占位（合成画面 · 无网络）", style = MaterialTheme.typography.labelLarge)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(top = AtlasSpacing.Md),
            ) {
                val cell = size.height / 3f
                val startX = (size.width - cell * 3f) / 2f
                repeat(3) { row ->
                    repeat(3) { col ->
                        drawRect(
                            ink,
                            Offset(startX + col * cell, row * cell),
                            Size(cell, cell),
                            style = Stroke(2f),
                        )
                    }
                }
                drawRect(ink, Offset(startX + cell, cell), Size(cell, cell))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AtlasIcons.Verify, contentDescription = null)
                Text(
                    "选择包含「灯」的图片（合成校验控件）",
                    modifier = Modifier.padding(start = AtlasSpacing.Sm),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
