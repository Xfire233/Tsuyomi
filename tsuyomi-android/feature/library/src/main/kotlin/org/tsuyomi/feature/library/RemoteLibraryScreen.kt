/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiTopBarAction
import org.tsuyomi.core.ui.components.TsuyomiOverflowAction
import org.tsuyomi.core.ui.components.TsuyomiDialog
import org.tsuyomi.core.ui.components.TsuyomiSelectableRow
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.feature.library.LibraryLayout
import org.tsuyomi.feature.library.RemoteMirrorBookSurface
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.shared.sourcecontract.SourceBookSummary

data class JitWritebackPrompt(
    val operation: String,
    val sourceName: String,
    val bookTitle: String,
)

fun remoteLibrarySelectionId(book: SourceBookSummary): String =
    "${book.identity.sourceId.length}:${book.identity.sourceId}${book.identity.remoteBookId}"

enum class RemoteLibraryViewState {
    IDLE,
    LOADING,
    CONTENT,
    EMPTY,
    ERROR,
    LOGIN_REQUIRED,
    VERIFICATION_REQUIRED,
    CANCELLED,
    COPIED,
}

@Composable
fun RemoteLibraryScreen(
    sourceId: String,
    sourceName: String,
    books: List<SourceBookSummary>,
    selectedIds: Set<String>,
    state: RemoteLibraryViewState,
    message: String?,
    copyConfirmationVisible: Boolean,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSelection: (SourceBookSummary) -> Unit,
    onClearSelection: () -> Unit,
    onRequestCopy: () -> Unit,
    onDismissCopy: () -> Unit,
    onConfirmCopy: () -> Unit,
    onOpenVerification: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    modifier: Modifier = Modifier,
    coverState: @Composable (SourceBookSummary) -> org.tsuyomi.core.media.api.CoverUiState = { book ->
        org.tsuyomi.core.media.api.CoverUiState.Fallback(
            org.tsuyomi.core.media.api.FallbackSpec(book.title, book.identity.sourceId),
        )
    },
    targets: List<RemoteTarget> = emptyList(),
    selectedTargetId: String? = null,
    onSelectTarget: (String?) -> Unit = {},
    onOpenTarget: (String) -> Unit = {},
    groupingEnabled: Boolean = false,
    onGroupingEnabledChange: (Boolean) -> Unit = {},
    unresolvedBookIds: Set<String> = emptySet(),
    removeConfirmationBook: SourceBookSummary? = null,
    onDismissRemoveConfirmation: () -> Unit = {},
    onConfirmRemove: (SourceBookSummary) -> Unit = {},
    moveTargetSelectionBook: SourceBookSummary? = null,
    onDismissMoveSelection: () -> Unit = {},
    onConfirmMove: (SourceBookSummary, targetId: String, targetName: String) -> Unit = { _, _, _ -> },
    jitPrompt: JitWritebackPrompt? = null,
    onConfirmJitPrompt: () -> Unit = {},
    onDismissJitPrompt: () -> Unit = {},
    onRequestRemoveBook: (SourceBookSummary) -> Unit = {},
    onRequestMoveBook: (SourceBookSummary) -> Unit = {},
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        FrozenEInkRemoteLibraryScreen(books, state, message, onRefresh, onOpenBook, modifier)
        return
    }

    val selectedCount = selectedIds.size
    var layout by remember { mutableStateOf(LibraryLayout.GRID) }
    val selectedBook = books.firstOrNull { remoteLibrarySelectionId(it) in selectedIds }
    val allVisibleSelected = books.isNotEmpty() && books.all { remoteLibrarySelectionId(it) in selectedIds }
    val overflowActions = if (selectedCount == 0) {
        buildList {
            if (targets.size > 1 || groupingEnabled) {
                add(
                    TsuyomiOverflowAction(
                        label = if (groupingEnabled) "停用网站分组" else "启用网站分组",
                        onClick = { onGroupingEnabledChange(!groupingEnabled) },
                        icon = TsuyomiIcons.Folder,
                    ),
                )
            }
            if (books.isNotEmpty()) {
                add(
                    TsuyomiOverflowAction(
                        label = stringResource(R.string.remote_library_copy_all),
                        onClick = onRequestCopy,
                        icon = TsuyomiIcons.Copy,
                    ),
                )
            }
        }
    } else {
        emptyList()
    }
    val selectionActions = if (selectedCount == 0) {
        emptyList()
    } else {
        buildList {
            add(
                TsuyomiTopBarAction(
                    icon = if (allVisibleSelected) TsuyomiIcons.DeselectAll else TsuyomiIcons.SelectAll,
                    label = if (allVisibleSelected) "取消全选" else "全选",
                    onClick = {
                        if (allVisibleSelected) onClearSelection()
                        else books.filterNot { remoteLibrarySelectionId(it) in selectedIds }.forEach(onToggleSelection)
                    },
                ),
            )
            add(TsuyomiTopBarAction(TsuyomiIcons.Copy, stringResource(R.string.remote_library_copy_selected), onRequestCopy))
            if (selectedCount == 1 && selectedBook != null) {
                if (groupingEnabled) {
                    add(TsuyomiTopBarAction(TsuyomiIcons.MoveToFolder, "移至网站分类", onClick = { onRequestMoveBook(selectedBook) }))
                }
                add(TsuyomiTopBarAction(TsuyomiIcons.Delete, "从网站收藏移除", onClick = { onRequestRemoveBook(selectedBook) }))
            }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TsuyomiTopBar(
                title = if (selectedCount != 0) {
                    stringResource(R.string.remote_library_selected_title, selectedCount)
                } else if (!groupingEnabled && selectedTargetId == null) {
                    "网站收藏"
                } else {
                    targets.firstOrNull { it.targetId == selectedTargetId }?.displayName ?: sourceName
                },
                subtitle = if (selectedCount != 0) {
                    "批量复制；网站移动/移除仅限单本"
                } else if (!groupingEnabled && selectedTargetId == null && sourceName.isNotBlank()) {
                    "$sourceName · ${books.size} 本"
                } else {
                    stringResource(R.string.remote_library_subtitle, books.size)
                },
                onNavigateUp = if (selectedCount == 0) onNavigateUp else onClearSelection,
                navigationIcon = if (selectedCount == 0) TsuyomiIcons.Back else TsuyomiIcons.Close,
                navigationContentDescription = if (selectedCount == 0) null else "退出选择",
                actions = if (selectedCount != 0) {
                    selectionActions
                } else {
                    listOf(
                        TsuyomiTopBarAction(TsuyomiIcons.Refresh, stringResource(R.string.remote_library_refresh), onRefresh),
                        TsuyomiTopBarAction(
                            icon = when (layout) {
                                LibraryLayout.GRID -> TsuyomiIcons.Grid
                                LibraryLayout.LIST -> TsuyomiIcons.List
                                LibraryLayout.COMPACT -> TsuyomiIcons.Compact
                            },
                            label = "切换布局",
                            onClick = { layout = layout.next() },
                        ),
                    )
                },
                overflow = overflowActions,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).testTag("remote-library-surface"),
        ) {
            message?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (state) {
                RemoteLibraryViewState.LOADING -> StateView(
                    kind = TsuyomiStateKind.LOADING,
                    title = stringResource(R.string.remote_library_loading),
                    message = stringResource(R.string.remote_library_loading_message),
                    modifier = Modifier.fillMaxSize(),
                )
                RemoteLibraryViewState.IDLE -> StateView(
                    kind = TsuyomiStateKind.EMPTY,
                    title = stringResource(R.string.remote_library_idle_title),
                    message = stringResource(R.string.remote_library_idle_message),
                    actionLabel = stringResource(R.string.remote_library_refresh),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                )
                RemoteLibraryViewState.EMPTY -> StateView(
                    kind = TsuyomiStateKind.EMPTY,
                    title = stringResource(R.string.remote_library_empty_title),
                    message = stringResource(R.string.remote_library_read_only_empty_message),
                    actionLabel = stringResource(R.string.remote_library_refresh),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                )
                RemoteLibraryViewState.LOGIN_REQUIRED,
                RemoteLibraryViewState.VERIFICATION_REQUIRED,
                -> StateView(
                    kind = TsuyomiStateKind.EMPTY,
                    title = stringResource(R.string.remote_library_login_title),
                    message = stringResource(R.string.remote_library_login_message),
                    actionLabel = stringResource(R.string.remote_library_open_verification),
                    onAction = onOpenVerification,
                    modifier = Modifier.fillMaxSize(),
                )
                RemoteLibraryViewState.ERROR,
                RemoteLibraryViewState.CANCELLED,
                -> StateView(
                    kind = TsuyomiStateKind.ERROR,
                    title = stringResource(R.string.remote_library_error_title),
                    message = stringResource(R.string.remote_library_error_message),
                    actionLabel = stringResource(R.string.remote_library_retry),
                    onAction = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                )
                RemoteLibraryViewState.CONTENT,
                RemoteLibraryViewState.COPIED,
                -> RemoteMirrorBookSurface(
                    sourceId = sourceId,
                    sourceName = sourceName,
                    books = books,
                    targets = if (groupingEnabled) targets else emptyList(),
                    selectedTargetId = selectedTargetId.takeIf { groupingEnabled },
                    groupingEnabled = groupingEnabled,
                    selectedBookIds = books.filter { remoteLibrarySelectionId(it) in selectedIds }.mapTo(linkedSetOf()) { it.identity },
                    unresolvedBookIds = unresolvedBookIds,
                    layout = layout,
                    onOpenTarget = { onOpenTarget(it.targetId) },
                    onOpenBook = onOpenBook,
                    onLongPressBook = onToggleSelection,
                    onToggleBookSelection = onToggleSelection,
                    onCopyToLocal = { book ->
                        if (remoteLibrarySelectionId(book) !in selectedIds) onToggleSelection(book)
                        onRequestCopy()
                    },
                    onMoveToTarget = { book, target ->
                        onSelectTarget(target.targetId)
                        onRequestMoveBook(book)
                    },
                    onRemoveFromWebsite = onRequestRemoveBook,
                    coverState = coverState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (copyConfirmationVisible) {
        TsuyomiDialog(
            onDismissRequest = onDismissCopy,
            title = stringResource(R.string.remote_library_copy_dialog_title),
            text = stringResource(R.string.remote_library_copy_dialog_message, if (selectedCount == 0) books.size else selectedCount),
            confirmLabel = stringResource(R.string.remote_library_copy_confirm),
            onConfirm = onConfirmCopy,
            dismissLabel = stringResource(R.string.remote_library_copy_cancel),
        )
    }

    if (removeConfirmationBook != null) {
        TsuyomiDialog(
            onDismissRequest = onDismissRemoveConfirmation,
            title = "从网站收藏移除",
            text = "确定要从网站收藏中移除《${removeConfirmationBook.title}》吗？\n此操作只修改网站收藏；本地书架、稍后再读、评分、标签和阅读进度均保留。",
            confirmLabel = "从网站收藏移除",
            onConfirm = { onConfirmRemove(removeConfirmationBook) },
            dismissLabel = "取消",
            destructive = true,
        )
    }

    if (groupingEnabled && moveTargetSelectionBook != null) {
        var pickedTargetId by remember(moveTargetSelectionBook, selectedTargetId) {
            mutableStateOf(selectedTargetId ?: targets.firstOrNull()?.targetId.orEmpty())
        }
        TsuyomiDialog(
            onDismissRequest = onDismissMoveSelection,
            title = "移至分类 / 目标",
            body = {
                Text("选择要移动至的目标分类：")
                targets.forEach { target ->
                    TsuyomiSelectableRow(
                        selected = pickedTargetId == target.targetId,
                        onClick = { pickedTargetId = target.targetId },
                        onLongClick = null,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Text(target.displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmLabel = "确认移动",
            onConfirm = {
                targets.firstOrNull { it.targetId == pickedTargetId }?.let {
                    onConfirmMove(moveTargetSelectionBook, it.targetId, it.displayName)
                }
            },
            confirmEnabled = pickedTargetId.isNotEmpty(),
            dismissLabel = "取消",
        )
    }

    if (jitPrompt != null) {
        val opName = when (jitPrompt.operation) {
            "add" -> "加入"
            "remove" -> "删除"
            else -> "移动"
        }
        TsuyomiDialog(
            onDismissRequest = onDismissJitPrompt,
            title = "授权远端回写操作",
            text = "您即将对《${jitPrompt.bookTitle}》执行远端书架${opName}操作。\nTsuyomi 将代表您向「${jitPrompt.sourceName}」发送远端变更。\n是否授权并继续？",
            confirmLabel = "授权并执行",
            onConfirm = onConfirmJitPrompt,
            dismissLabel = "取消",
        )
    }
}

@Composable
private fun FrozenEInkRemoteLibraryScreen(
    books: List<SourceBookSummary>,
    state: RemoteLibraryViewState,
    message: String?,
    onRefresh: () -> Unit,
    onOpenBook: (SourceBookSummary) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.remote_library_manual_notice))
        TsuyomiButton(
            text = stringResource(R.string.remote_library_refresh),
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            style = TsuyomiButtonStyle.PRIMARY,
        )
        message?.let { Text(it) }
        if (state == RemoteLibraryViewState.LOADING) {
            Text(stringResource(R.string.remote_library_loading))
        } else {
            books.forEach { book ->
                Text(book.title, Modifier.fillMaxWidth().clickable { onOpenBook(book) }.padding(vertical = 12.dp))
            }
        }
    }
}
