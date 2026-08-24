/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasFeatureIntroduction
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
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
import org.tsuyomi.prototype.uiatlas.fixtures.AtlasFixtures
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasBranding
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasFamily
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.AtlasSource
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing

// -- #12 canonical detail -------------------------------------------------------------------

@Composable
internal fun BookDetail(context: AtlasContext, modifier: Modifier) {
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
                overflow = buildList {
                    add(AtlasOverflowItem("刷新来源数据") { runScenario("detail-refresh") })
                    add(AtlasOverflowItem("在来源中打开本书") { navigation.navigateInRoot(AtlasFamily.SOURCE, AtlasRoute.BOOK_DETAIL) })
                    add(AtlasOverflowItem("移出书架") { localRemoveOpen = true })
                    if (!dormant) {
                        add(AtlasOverflowItem("从网站移除收藏") { remoteOperation = "remove" })
                        add(AtlasOverflowItem("移动网站收藏") { remoteOperation = "move" })
                    }
                },
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
internal fun DialogActionRow(
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

@Composable
private fun DetailContent(
    context: AtlasContext,
    book: AtlasBook,
    source: AtlasSource,
    dormant: Boolean,
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
        .filter { !unreadOnly || !it.read }
        .let { if (descending) it.reversed() else it }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val atDirectory by remember { derivedStateOf { scroll.value > 240 } }
    Column(modifier) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                DetailIdentityModule(
                    book = book,
                    sourceColor = (source.branding as? AtlasBranding.Valid)?.color,
                    rating = rating,
                    onRatingChange = { next ->
                        repository.putInt("detail.rating", next, "BookRatingChanged", book.id)
                    },
                )
                DetailTagActionsModule(
                    tags = tags,
                    readLater = readLater,
                    onAddTag = {
                        val tag = "新标签 ${tags.size - book.tags.size + 1}"
                        repository.putStringList("detail.tags", tags + tag, "BookTagAdded", book.id)
                    },
                    onToggleReadLater = {
                        repository.putBoolean("detail.readLater", !readLater, "ReadLaterChanged", book.id)
                    },
                )
                DetailIntroductionModule(
                    description = "雾港的旧灯塔再次亮起，记录员沿着失真的航线寻找一段被删去的夜航日志。",
                    status = when {
                        dormant -> "来源休眠；远端操作已停用。"
                        context.showOfflineBanner -> "正在显示缓存信息。"
                        else -> null
                    },
                )
                DetailDirectoryModule(
                    totalChapters = SourceAtlasFixtures.DIRECTORY_TOTAL,
                    chapters = displayedChapters,
                    unreadOnly = unreadOnly,
                    descending = descending,
                    selected = context.selectionMode,
                    onToggleUnreadOnly = {
                        repository.putBoolean(
                            "detail.chapters.unreadOnly",
                            !unreadOnly,
                            "ChapterFilterChanged",
                            book.id,
                        )
                    },
                    onToggleOrder = {
                        repository.putBoolean(
                            "detail.chapters.descending",
                            !descending,
                            "ChapterSortChanged",
                            book.id,
                        )
                    },
                    onOpenChapter = { chapter ->
                        repository.putInt(
                            "reader.page",
                            chapter.number.coerceIn(1, SourceAtlasFixtures.READER_PAGE_COUNT),
                            "ChapterOpened",
                            chapter.number.toString(),
                        )
                        navigation.navigate(AtlasRoute.BOOK_READER)
                    },
                )
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
