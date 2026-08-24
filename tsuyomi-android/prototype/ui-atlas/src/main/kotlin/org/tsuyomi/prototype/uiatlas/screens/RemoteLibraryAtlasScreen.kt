/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlin.math.ceil
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.components.AtlasBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasInfoBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationPhase
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationStatus
import org.tsuyomi.prototype.uiatlas.components.AtlasOverflowItem
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasStateKind
import org.tsuyomi.prototype.uiatlas.components.AtlasStateView
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBarAction
import org.tsuyomi.prototype.uiatlas.components.BookGridCard
import org.tsuyomi.prototype.uiatlas.components.BookListItemRow
import org.tsuyomi.prototype.uiatlas.components.CompactBookListItem
import org.tsuyomi.prototype.uiatlas.components.layoutToggleContentDescription
import org.tsuyomi.prototype.uiatlas.components.nextAtlasLayout
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.prototypeRepository
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment
@Composable
internal fun RemoteLibrary(context: AtlasContext, modifier: Modifier) {
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
