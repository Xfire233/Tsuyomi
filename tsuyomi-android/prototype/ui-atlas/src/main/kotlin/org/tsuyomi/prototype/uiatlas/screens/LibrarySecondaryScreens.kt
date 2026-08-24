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
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.*
import org.tsuyomi.prototype.uiatlas.fixtures.LibraryAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.*
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasMotion
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment
// -------------------------------------------------------------------------------------------
// #3 — library/history
// -------------------------------------------------------------------------------------------

private data class HistoryLine(
    val header: String?,
    val entry: LibraryAtlasFixtures.HistoryEntryFixture,
)

@Composable
internal fun LibraryHistory(context: AtlasContext, modifier: Modifier) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    val removedIds = repository.stringList("history.removed").toSet()
    val lines = LibraryAtlasFixtures.historyGroups.flatMap { group ->
        group.entries.mapIndexed { index, entry -> HistoryLine(if (index == 0) group.label else null, entry) }
    }.filterNot { it.entry.book.id in removedIds }
    val books = lines.map { it.entry.book }
    var selected by remember(context.state) {
        mutableStateOf(if (context.state == AtlasPageState.SELECTION) books.take(3).map { it.id }.toSet() else emptySet())
    }
    var selectionActive by remember(context.state) { mutableStateOf(context.state == AtlasPageState.SELECTION) }
    var removeBook by remember(context.state) {
        mutableStateOf(if (context.state == AtlasPageState.MODAL) books.firstOrNull() else null)
    }
    var clearOpen by remember { mutableStateOf(false) }
    var pendingHistoryRemovalIds by remember { mutableStateOf<Set<String>?>(null) }
    BackHandler(selectionActive) {
        selectionActive = false
        selected = emptySet()
    }
    BackHandler(removeBook != null || clearOpen || pendingHistoryRemovalIds != null) {
        removeBook = null
        clearOpen = false
        pendingHistoryRemovalIds = null
    }
    val selectionBar = selectionTopBar(
        selected,
        books.map { it.id }.toSet(),
        { selectionActive = false; selected = emptySet() },
        { selected = it },
        AtlasIcons.Close,
        "移除所选",
    ) {
        pendingHistoryRemovalIds = selected
    }
    val mutation = if (context.state == AtlasPageState.MUTATION) {
        AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "已移除 1 条历史记录；书架与阅读进度未受影响")
    } else {
        null
    }
    val page = pageSlice("history", lines, 8)
    val visible = if (eInk) page.items else lines
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            Column {
                AtlasTopBar(
                    title = "历史",
                    subtitle = "${books.size} 条记录",
                    onUp = navigation.up,
                    selection = if (selectionActive) selectionBar else null,
                    overflow = listOf(AtlasOverflowItem("清空历史", { clearOpen = true })),
                )
                OverlayState(context.state, mutation)
            }
        },
        footer = if (eInk && context.state.showsContent && lines.isNotEmpty()) {
            { PaginationFooter(page.page, page.pages, page.setPage) }
        } else {
            null
        },
    ) {
        StateOrContent(
            context.state,
            "还没有阅读历史",
            "开始阅读后，最近读过的书会按时间分组出现在这里。",
            "历史记录加载失败",
            "本地历史索引不可用；书架与阅读进度未受影响。",
        ) {
            val body: @Composable (HistoryLine) -> Unit = { line ->
                line.header?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val book = line.entry.book
                BookListItemRow(
                    book = book,
                    onClick = {
                        if (selectionActive) {
                            selected = if (book.id in selected) selected - book.id else selected + book.id
                        } else {
                            navigation.navigate(AtlasRoute.BOOK_DETAIL)
                        }
                    },
                    onLongClick = {
                        selectionActive = true
                        selected = selected + book.id
                    },
                    showSourceChip = false,
                    selected = book.id in selected,
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                line.entry.timeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AtlasIconButton(
                                AtlasIcons.Close,
                                "从历史移除《${book.title}》",
                                { removeBook = book },
                            )
                        }
                    },
                )
            }
            if (eInk) {
                Column { visible.forEach { body(it) } }
            } else {
                LazyColumn { items(visible, key = { it.entry.book.id }) { body(it) } }
            }
        }
    }
    removeBook?.let { book ->
        FullDialog("从历史中移除？", { removeBook = null }, destructive = true) {
            Text("《${book.title}》将从历史记录中移除。书架、收藏与阅读进度不受影响。")
            DialogButtons("移除", {
                repository.putStringList(
                    "history.removed",
                    (removedIds + book.id).toList(),
                    "HistoryEntryRemoved",
                    book.id,
                )
                removeBook = null
            }, { removeBook = null })
        }
    }
    pendingHistoryRemovalIds?.let { ids ->
        FullDialog("从历史中移除 ${ids.size} 条记录？", { pendingHistoryRemovalIds = null }, destructive = true) {
            Text("所选历史记录将被移除。书架、收藏与阅读进度不受影响。")
            DialogButtons("移除", {
                repository.putStringList(
                    "history.removed",
                    (removedIds + ids).toList(),
                    "HistoryEntriesRemoved",
                    "history",
                )
                selected = emptySet()
                selectionActive = false
                pendingHistoryRemovalIds = null
            }, { pendingHistoryRemovalIds = null })
        }
    }
    if (clearOpen) {
        FullDialog("清空全部历史？", { clearOpen = false }, destructive = true) {
            Text("${books.size} 条历史记录将被清空，此操作不可撤销。书籍、收藏与阅读进度不受影响。")
            DialogButtons("清空历史", {
                repository.putStringList(
                    "history.removed",
                    (removedIds + books.map { it.id }).toList(),
                    "HistoryCleared",
                    "history",
                )
                clearOpen = false
            }, { clearOpen = false })
        }
    }
}

// -------------------------------------------------------------------------------------------
// #4 — library/updates
// -------------------------------------------------------------------------------------------

@Composable
internal fun LibraryUpdates(context: AtlasContext, modifier: Modifier) {
    val eInk = LocalAtlasEnvironment.current.eInk
    val navigation = LocalAtlasNavigation.current
    val runtime = LocalPrototypeRuntime.current
    val repository = prototypeRepository()
    val coroutineScope = rememberCoroutineScope()
    val runCheck: () -> Unit = {
        coroutineScope.launch { runtime.scenarios.run("updates-check", "library/updates") }
    }
    val updates = LibraryAtlasFixtures.updateEntries
    val excluded = LibraryAtlasFixtures.updateExclusions
    val page = pageSlice("updates", updates, 6)
    val sessionVisible = context.state == AtlasPageState.REFRESHING
    val visible = when {
        sessionVisible -> updates.take(LibraryAtlasFixtures.UPDATE_RUNNING_FOUND)
        eInk -> page.items
        else -> updates
    }
    var layout by rememberSaveable(context.layout?.name) { mutableStateOf(context.layout ?: AtlasLayout.LIST) }
    var settingsOpen by remember(context.state) { mutableStateOf(context.state == AtlasPageState.MODAL) }
    var scheduleEnabled by rememberSaveable { mutableStateOf(false) }
    var tutorialOpen by remember(context.tutorial) { mutableStateOf(context.tutorial) }
    var schedulePeriod by rememberSaveable { mutableStateOf("每日") }
    val mutation = if (context.state == AtlasPageState.MUTATION) {
        AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "已处理 ${updates.size} 条更新；追更待办已清空")
    } else {
        null
    }
    val unresolved = AtlasMutationStatus(
        AtlasMutationPhase.ERROR,
        "本地标记未保存：exact update anchor 仍保持未处理；没有发送网络请求，也不会阻止网站操作。",
        "重试本地保存",
        { repository.record("UpdateAnchorSaveRetried", "library/updates", "queued") },
    )
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            Column {
                AtlasTopBar(
                    title = "追更",
                    subtitle = "${updates.size} 本有更新 · ${excluded.size} 本已排除",
                    onUp = navigation.up,
                    actions = listOf(
                        AtlasTopBarAction(AtlasIcons.Refresh, "检查全部更新", runCheck),
                        AtlasTopBarAction(layout.currentLayoutIcon(), layout.layoutToggleContentDescription()) {
                            layout = layout.nextAtlasLayout()
                            repository.putString("updates.layout", layout.name, "UpdateLayoutChanged")
                        },
                    ),
                    overflow = listOf(
                        AtlasOverflowItem("追更设置") { settingsOpen = true },
                        AtlasOverflowItem("功能说明") { tutorialOpen = true },
                        AtlasOverflowItem("全部确认已看过") { repository.putBoolean("updates.allSeen", true, "AllUpdatesMarkedSeen") },
                    ),
                )
                if (!sessionVisible) OverlayState(context.state, mutation, unresolved)
            }
        },
        footer = if (eInk && context.primaryState == AtlasPageState.CONTENT && updates.isNotEmpty()) {
            { PaginationFooter(page.page, page.pages, page.setPage) }
        } else {
            null
        },
    ) {
        StateOrContent(
            context.state,
            "没有待处理的更新",
            "检查更新后，新章节会出现在这里。打开此页不会自动确认更新。",
            "更新检查失败",
            "源·柏凭据过期；源·松连接超时。已缓存的结果仍可查看。",
            "检查全部更新",
            runCheck,
        ) {
            when (layout) {
                AtlasLayout.GRID -> Column {
                    if (sessionVisible) UpdateSessionSurface()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(AtlasSpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                        horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                    ) {
                        gridItems(visible, key = { it.book.id }) { update ->
                            BookGridCard(
                                update.book.copy(progressLabel = "${update.anchorLabel} · ${update.updatedAtLabel}"),
                                { repository.record("UpdateBookOpened", update.book.id, "success"); navigation.navigate(AtlasRoute.BOOK_DETAIL) },
                                modifier = Modifier.semantics { stateDescription = "${update.newChapters} 章更新 · ${update.anchorLabel} · ${update.updatedAtLabel}" },
                            )
                        }
                    }
                }
                AtlasLayout.COMPACT -> Column {
                    if (sessionVisible) UpdateSessionSurface()
                    visible.forEach { update ->
                        CompactBookListItem(update.book.copy(progressLabel = "${update.anchorLabel} · ${update.updatedAtLabel}"), {
                            repository.record("UpdateBookOpened", update.book.id, "success")
                            navigation.navigate(AtlasRoute.BOOK_DETAIL)
                        })
                    }
                }
                AtlasLayout.LIST -> if (eInk) {
                    Column { if (sessionVisible) UpdateSessionSurface(); visible.forEach { UpdateBookRow(it) }; if (!sessionVisible) UpdateExcluded(excluded) }
                } else {
                    LazyColumn { if (sessionVisible) item { UpdateSessionSurface() }; items(visible, key = { it.book.id }) { UpdateBookRow(it) }; if (!sessionVisible) item { UpdateExcluded(excluded) } }
                }
            }
        }
    }
    if (tutorialOpen) {
        AtlasFeatureIntroduction(
            featureId = "updates",
            tutorialVersion = 1,
            title = "功能说明：追更",
            summary = "打开追更不会自动检查或标记已处理。",
            points = listOf(
                "自动检查默认关闭，只能在追更设置中启用。",
                "隐藏或重建追更视图不会改变调度。",
                "标记已处理只保存本地 exact anchor，不向来源写入。",
                "说明关闭后，手动检查和设置仍需要你的明确操作。",
            ),
            onDismiss = { tutorialOpen = false },
        )
    }
    if (settingsOpen) {
        FullDialog("追更设置", { settingsOpen = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                Text("自动检查默认关闭。隐藏、删除或重建「追更」视图都不会改变这里的调度。")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                        scheduleEnabled = !scheduleEnabled
                        repository.putBoolean("updates.schedule.enabled", scheduleEnabled, "UpdateScheduleChanged")
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(scheduleEnabled, {
                        scheduleEnabled = it
                        repository.putBoolean("updates.schedule.enabled", it, "UpdateScheduleChanged")
                    })
                    Text(if (scheduleEnabled) "自动检查：开启" else "自动检查：关闭", Modifier.padding(start = AtlasSpacing.Md))
                }
                listOf("每 12 小时", "每日", "每 3 天", "每周").forEach { period ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = scheduleEnabled) {
                            schedulePeriod = period
                            repository.putString("updates.schedule.period", period, "UpdateSchedulePeriodChanged")
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(schedulePeriod == period, {
                            schedulePeriod = period
                            repository.putString("updates.schedule.period", period, "UpdateSchedulePeriodChanged")
                        }, enabled = scheduleEnabled)
                        Text(period, Modifier.padding(start = AtlasSpacing.Sm))
                    }
                }
                Text("有效约束：设备联网且系统允许后台任务；不要求充电。通知显示会话计数并提供「取消本次检查」。通知权限被拒绝时，持久的应用内会话状态仍可查看和取消。")
                AtlasButton("保存设置", {
                    repository.record("UpdateSettingsSaved", "library/updates", "success")
                    settingsOpen = false
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun UpdateSessionSurface() {
    val eInk = LocalAtlasEnvironment.current.eInk
    val repository = prototypeRepository()
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(AtlasSpacing.Md),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (eInk) BorderStroke(1.5.dp, AtlasEInkPalette.Ink) else null,
    ) {
        Column(Modifier.padding(AtlasSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (eInk) Icon(AtlasIcons.Refresh, contentDescription = "正在检查", modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(start = if (eInk) AtlasSpacing.Sm else 0.dp)) {
                    Text("正在检查更新", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${LibraryAtlasFixtures.UPDATE_RUNNING_CHECKED} / ${LibraryAtlasFixtures.UPDATE_RUNNING_TOTAL}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AtlasButton(if (expanded) "收起" else "查看详情", { expanded = !expanded }, style = AtlasButtonStyle.TEXT)
            }
            if (!eInk) {
                LinearProgressIndicator(
                    progress = { LibraryAtlasFixtures.UPDATE_RUNNING_CHECKED.toFloat() / LibraryAtlasFixtures.UPDATE_RUNNING_TOTAL },
                    modifier = Modifier.fillMaxWidth().padding(top = AtlasSpacing.Sm),
                )
            }
            if (expanded) {
                Text(
                    "上次部分完成：125 / 128 成功 · ${LibraryAtlasFixtures.UPDATE_PARTIAL_FAILED} 本失败",
                    modifier = Modifier.padding(top = AtlasSpacing.Md),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(LibraryAtlasFixtures.UPDATE_FAILED_SOURCE_LINE, style = MaterialTheme.typography.bodySmall)
                Text(LibraryAtlasFixtures.UPDATE_DORMANT_LINE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                    AtlasButton("重试失败项", {
                        repository.record("FailedUpdatesRetried", "library/updates", "queued")
                    }, style = AtlasButtonStyle.SECONDARY)
                    AtlasButton("取消本次检查", {
                        repository.record("UpdateCheckCancelled", "library/updates", "cancelled")
                    }, style = AtlasButtonStyle.TEXT)
                }
            }
        }
    }
}

@Composable
private fun UpdateBookRow(update: LibraryAtlasFixtures.UpdateEntryFixture) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    BookListItemRow(
        book = update.book,
        onClick = {
            repository.record("UpdateBookOpened", update.book.id, "success")
            navigation.navigate(AtlasRoute.BOOK_DETAIL)
        },
        showSourceChip = false,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(update.anchorLabel, style = MaterialTheme.typography.bodySmall)
                Text(update.updatedAtLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AtlasIconButton(AtlasIcons.Check, "确认已看过", {
                    repository.putBoolean("updates.${update.book.id}.seen", true, "UpdateMarkedSeen", update.book.id)
                })
            }
        },
    )
}

@Composable
private fun UpdateExcluded(excluded: List<LibraryAtlasFixtures.ExcludedBookFixture>) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    Text(
        "已排除（${excluded.size}）",
        modifier = Modifier.padding(AtlasSpacing.Md),
        style = MaterialTheme.typography.titleMedium,
    )
    excluded.forEach { item ->
        BookListItemRow(
            book = item.book,
            onClick = { navigation.navigate(AtlasRoute.BOOK_DETAIL) },
            showSourceChip = false,
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(item.reason, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    AtlasButton("恢复检查", {
                        repository.putBoolean("updates.${item.book.id}.excluded", false, "UpdateCheckRestored", item.book.id)
                    }, style = AtlasButtonStyle.TEXT)
                }
            },
        )
    }
}

// -------------------------------------------------------------------------------------------
// #7/#8 — manual / smart collection detail
// -------------------------------------------------------------------------------------------

@Composable
internal fun CollectionRule(context: AtlasContext, modifier: Modifier) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    var groups by remember(context.state) { mutableStateOf(LibraryAtlasFixtures.ruleGroups) }
    var dirty by remember(context.state) { mutableStateOf(context.state == AtlasPageState.MODAL) }
    var confirmOpen by remember(context.state) { mutableStateOf(context.state == AtlasPageState.MODAL) }
    var tutorialOpen by remember(context.tutorial) { mutableStateOf(context.tutorial) }
    BackHandler(dirty && !confirmOpen) { confirmOpen = true }
    BackHandler(confirmOpen) { confirmOpen = false }
    val count = groups.sumOf { it.conditions.size }
    val mutation = if (context.state == AtlasPageState.MUTATION) {
        AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "规则已保存 · 重新匹配到 9 本书")
    } else {
        null
    }
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            Column {
                AtlasTopBar(
                    title = "规则收藏夹",
                    subtitle = "从预设开始 · 科幻·未读 · 条件 $count / ${LibraryAtlasFixtures.RULE_CONDITION_CAP}",
                    onUp = { if (dirty) confirmOpen = true else navigation.up() },
                    actions = listOf(AtlasTopBarAction(AtlasIcons.Check, "保存") {
                        repository.putStringList(
                            "collection.rule.values",
                            groups.flatMap { group -> group.conditions.map { it.value } },
                            "CollectionRuleSaved",
                            "smart-sci-fi",
                        )
                        dirty = false
                    }),
                    overflow = listOf(
                        AtlasOverflowItem("功能说明") { tutorialOpen = true },
                        AtlasOverflowItem("重置为已保存") {
                            groups = LibraryAtlasFixtures.ruleGroups
                            dirty = false
                            repository.record("CollectionRuleReset", "smart-sci-fi", "success")
                        },
                    ),
                )
                OverlayState(context.state, mutation)
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(AtlasSpacing.Md),
            ) {
                groups.forEachIndexed { groupIndex, group ->
                    if (groupIndex > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            AtlasChip("或（满足任一分组）")
                        }
                    }
                    Text(
                        "分组 ${groupIndex + 1} · 组内「且」",
                        modifier = Modifier.padding(vertical = AtlasSpacing.Sm),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    group.conditions.forEachIndexed { conditionIndex, condition ->
                        OutlinedTextField(
                            value = condition.value,
                            onValueChange = { value ->
                                dirty = true
                                groups = groups.mapIndexed { gi, oldGroup ->
                                    if (gi != groupIndex) oldGroup else oldGroup.copy(
                                        conditions = oldGroup.conditions.mapIndexed { ci, old ->
                                            if (ci == conditionIndex) old.copy(value = value) else old
                                        },
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = AtlasSpacing.Sm),
                            label = { Text("条件 ${groupIndex * 4 + conditionIndex + 1} · ${condition.field} ${condition.operator}") },
                            isError = condition.error != null,
                            supportingText = condition.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            trailingIcon = condition.error?.let {
                                { Icon(AtlasIcons.Warning, "条件错误", tint = MaterialTheme.colorScheme.error) }
                            },
                        )
                    }
                    AtlasButton("从预设添加条件", {
                        dirty = true
                        val preset = LibraryAtlasFixtures.ruleGroups.first().conditions.first()
                        groups = groups.mapIndexed { index, group ->
                            if (index == 0 && count < LibraryAtlasFixtures.RULE_CONDITION_CAP) group.copy(conditions = group.conditions + preset) else group
                        }
                    }, modifier = Modifier.padding(top = AtlasSpacing.Xs), style = AtlasButtonStyle.TEXT)
                    AtlasButton("添加条件", {
                        dirty = true
                        val blank = LibraryAtlasFixtures.RuleConditionFixture("标签", "包含", "")
                        groups = groups.mapIndexed { index, group ->
                            if (index == 0 && count < LibraryAtlasFixtures.RULE_CONDITION_CAP) group.copy(conditions = group.conditions + blank) else group
                        }
                    }, modifier = Modifier.padding(top = AtlasSpacing.Sm), style = AtlasButtonStyle.SECONDARY)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = AtlasSpacing.Lg),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        "条件 $count / ${LibraryAtlasFixtures.RULE_CONDITION_CAP} · 最长值 ${LibraryAtlasFixtures.RULE_LONGEST_VALUE} / ${LibraryAtlasFixtures.RULE_VALUE_CAP} 字符",
                        modifier = Modifier.padding(AtlasSpacing.Md),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (tutorialOpen) {
        AtlasFeatureIntroduction(
            featureId = "smart-rule-editor",
            tutorialVersion = 1,
            title = "功能说明：智能收藏规则",
            summary = "规则是受限、可读的本地筛选，不执行 SQL、脚本或来源代码。",
            points = listOf(
                "本地标签与指定来源标签必须明确选择。",
                "规则变化只改变派生结果，不删除书籍标注。",
                "保存前会显示逐项错误和可读摘要。",
                "离开未保存编辑会再次确认。",
            ),
            onDismiss = { tutorialOpen = false },
        )
    }
    if (confirmOpen) {
        FullDialog("放弃未保存的修改？", { confirmOpen = false }, destructive = true) {
            Text("规则条件有未保存的修改。放弃后将恢复到上次保存的内容。")
            DialogButtons(
                "放弃修改",
                {
                    groups = LibraryAtlasFixtures.ruleGroups
                    dirty = false
                    confirmOpen = false
                },
                { confirmOpen = false },
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// #10 — library/tags
// -------------------------------------------------------------------------------------------

@Composable
internal fun LibraryTags(context: AtlasContext, modifier: Modifier) {
    val navigation = LocalAtlasNavigation.current
    val repository = prototypeRepository()
    var dialog by remember(context.state) {
        mutableStateOf(if (context.state == AtlasPageState.MODAL) "merge" else null)
    }
    var ownership by rememberSaveable { mutableStateOf("local") }
    var layout by rememberSaveable(context.layout?.name) { mutableStateOf(context.layout ?: AtlasLayout.COMPACT) }
    var target by remember { mutableStateOf(LibraryAtlasFixtures.localTags.first()) }
    var renameValue by remember { mutableStateOf(target.name) }
    BackHandler(dialog != null) { dialog = null }
    val mutation = if (context.state == AtlasPageState.MUTATION) {
        AtlasMutationStatus(AtlasMutationPhase.SUCCESS, "标签已合并")
    } else null
    AtlasScaffold(
        modifier = modifier,
        topBar = {
            Column {
                AtlasTopBar(
                    title = "标签",
                    onUp = navigation.up,
                    actions = listOf(
                        AtlasTopBarAction(AtlasIcons.Search, "搜索标签") { repository.record("TagSearchOpened", "library/tags", "success") },
                        AtlasTopBarAction(AtlasIcons.Sort, "排序标签") { repository.record("TagsSorted", "library/tags", "success") },
                        AtlasTopBarAction(layout.currentLayoutIcon(), layout.layoutToggleContentDescription()) {
                            layout = if (layout == AtlasLayout.COMPACT) AtlasLayout.LIST else AtlasLayout.COMPACT
                            repository.putString("tags.layout", layout.name, "TagLayoutChanged")
                        },
                    ),
                    overflow = listOf(AtlasOverflowItem("新建标签") {
                        renameValue = ""
                        dialog = "create"
                    }),
                )
                OverlayState(context.state, mutation)
            }
        },
    ) {
        StateOrContent(
            context.state,
            "还没有本地标签",
            "在书籍详情中添加标签后，会显示在这里。",
            "标签加载失败",
            "本地标签索引不可用；书籍上的标签未受影响。",
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().padding(AtlasSpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                ) {
                    FilterChip(ownership == "local", { ownership = "local"; repository.putString("tags.ownership", "local", "TagOwnershipChanged") }, { Text("本地") }, modifier = Modifier.weight(1f))
                    FilterChip(ownership == "source", { ownership = "source"; repository.putString("tags.ownership", "source", "TagOwnershipChanged") }, { Text("来源") }, modifier = Modifier.weight(1f))
                }
                if (layout == AtlasLayout.COMPACT) {
                    FlowRow(
                        modifier = Modifier.padding(AtlasSpacing.Md),
                        horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                    ) {
                        if (ownership == "local") {
                            LibraryAtlasFixtures.localTags.forEach { tag ->
                                FilterChip(
                                    selected = false,
                                    onClick = { target = tag; renameValue = tag.name; dialog = "rename" },
                                    label = { Text(tag.name) },
                                )
                            }
                        } else {
                            LibraryAtlasFixtures.sourceTagGroups.forEach { group ->
                                group.tags.forEach { tag ->
                                    FilterChip(selected = false, onClick = {}, enabled = false, label = { Text(tag.name) })
                                }
                            }
                        }
                    }
                } else if (ownership == "local") {
                    LibraryAtlasFixtures.localTags.forEach { tag ->
                        TagManagerRow(tag) { action -> target = tag; renameValue = tag.name; dialog = action }
                    }
                } else {
                    LibraryAtlasFixtures.sourceTagGroups.forEach { group ->
                        group.tags.forEach { tag ->
                            ListItem(
                                headlineContent = { Text(tag.name) },
                                supportingContent = { Text("${tag.bookCount} 本") },
                                trailingContent = { AtlasChip("只读") },
                            )
                        }
                    }
                }
            }
        }
    }
    when (dialog) {
        "rename" -> FullDialog("重命名标签", { dialog = null }) {
            OutlinedTextField(
                renameValue,
                { renameValue = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新名称") },
                supportingText = { Text("最多 256 个字符；名称冲突会转为合并确认。") },
            )
            DialogButtons("重命名", {
                repository.putString("tag.${target.id}.name", renameValue, "TagRenamed", target.id)
                dialog = null
            }, { dialog = null })
        }
        "create" -> FullDialog("新建标签", { dialog = null }) {
            OutlinedTextField(renameValue, { renameValue = it }, modifier = Modifier.fillMaxWidth(), label = { Text("名称") })
            DialogButtons("新建", {
                repository.putStringList("tags.created", repository.stringList("tags.created") + renameValue, "TagCreated", renameValue)
                dialog = null
            }, { dialog = null })
        }
        "merge" -> FullDialog("标签名称冲突", { dialog = null }, destructive = true) {
            Text("名称冲突。确认后将使用目标标签；书籍、评分与收藏关系保留。")
            DialogButtons("仍然合并", { repository.record("TagsMerged", target.id, "success"); dialog = null }, { dialog = null })
        }
        "delete" -> FullDialog("删除标签「${target.name}」？", { dialog = null }, destructive = true) {
            Text("书籍、评分与收藏关系保留。")
            DialogButtons("删除", { repository.putBoolean("tag.${target.id}.deleted", true, "TagDeleted", target.id); dialog = null }, { dialog = null })
        }
    }
}

@Composable
private fun TagManagerRow(
    tag: LibraryAtlasFixtures.TagFixture,
    action: (String) -> Unit,
) {
    var open by remember(tag.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = AtlasSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AtlasIcons.Tune, null, modifier = Modifier.size(24.dp))
        Text(tag.name, modifier = Modifier.weight(1f).padding(start = AtlasSpacing.Md))
        Text("${tag.bookCount} 本", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(AtlasSpacing.Sm))
        Box {
            AtlasIconButton(AtlasIcons.Overflow, "标签「${tag.name}」操作", { open = true })
            DropdownMenu(open, { open = false }) {
                DropdownMenuItem({ Text("重命名") }, {
                    open = false
                    action("rename")
                })
                DropdownMenuItem({ Text("合并到…") }, {
                    open = false
                    action("merge")
                })
                DropdownMenuItem({ Text("删除…") }, {
                    open = false
                    action("delete")
                })
            }
        }
    }
}
