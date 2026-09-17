/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.core.ui.components.TsuyomiExtendedFab

@Immutable
data class DetailLocalState(
    val inLibrary: Boolean = false,
    val rating: Int? = null,
    val localTags: List<String> = emptyList(),
    val localTagsEditable: Boolean = false,
    val readLater: Boolean = false,
    val progressChapterId: String? = null,
    val progressChapterFraction: Double? = null,
    val completedChapterIds: Set<String> = emptySet(),
    val updatedChapterIds: Set<String> = emptySet(),
    val reconciliationOperation: String? = null,
    val reconciliation: String? = null,
    val remoteRemoveEnabled: Boolean = false,
    val remoteMoveEnabled: Boolean = false,
)

enum class DetailMutationOperation {
    ADD_TO_LIBRARY,
    REMOVE_FROM_LIBRARY,
    REFRESH_DETAIL,
    SET_RATING,
    ADD_TAG,
    TOGGLE_READ_LATER,
    REMOVE_FROM_REMOTE,
    MOVE_REMOTE,
    RECONCILE_RETRY,
    RECONCILE_ACKNOWLEDGE,
}

enum class DetailMutationPhase { WORKING, SUCCESS, ERROR }

@Immutable
data class DetailMutationStatus(
    val operation: DetailMutationOperation,
    val phase: DetailMutationPhase,
    val safeCode: String? = null,
)

@Immutable
data class DetailChapterItem(
    val chapter: SourceChapter,
    val current: Boolean = false,
    val read: Boolean? = null,
    val updated: Boolean = false,
    val downloaded: Boolean = false,
)
@Immutable
enum class DetailChapterCachePhase {
    QUEUED,
    CACHING,
    CACHED,
    FAILED,
    CANCELLED,
}

@Immutable
data class DetailCacheState(
    val selecting: Boolean = false,
    val selectedChapterIds: Set<String> = emptySet(),
    val chapters: Map<String, DetailChapterCachePhase> = emptyMap(),
) {
    val working: Boolean
        get() = chapters.values.any { it == DetailChapterCachePhase.QUEUED || it == DetailChapterCachePhase.CACHING }
}

sealed interface DetailCacheAction {
    data class Toggle(val chapterId: String) : DetailCacheAction
    data class ToggleAll(val chapterIds: Set<String>) : DetailCacheAction
    data object Start : DetailCacheAction
    data object Cancel : DetailCacheAction
    data object Close : DetailCacheAction
}


internal data class DetailVolumeGroup(
    val key: String,
    val title: String,
    val items: List<DetailChapterItem>,
)

@Composable
internal fun StandardBookDetailScreen(
    state: SourceBookState<SourceBookDetail>,
    directoryState: SourceBookState<SourceDirectory>,
    localState: DetailLocalState,
    mutation: DetailMutationStatus?,
    coverState: CoverUiState,
    unreadOnly: Boolean,
    descending: Boolean,
    selectedChapterId: String?,
    cacheState: DetailCacheState,
    onCacheAction: (DetailCacheAction) -> Unit,
    onSetRating: (Int?) -> Unit,
    onSearchAuthor: (String) -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
    onSelectChapter: (SourceChapter) -> Unit,
    onContinueReading: (SourceChapter) -> Unit,
    onAddToLibrary: () -> Unit,
    onRequestRemoveFromLibrary: () -> Unit,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier = Modifier,
    tagEditorOpen: Boolean = false,
    tagDraft: String = "",
    onOpenTagEditor: () -> Unit = {},
    onTagDraftChange: (String) -> Unit = {},
    onDismissTagEditor: () -> Unit = {},
    onConfirmTag: () -> Unit = {},
    onOpenDestinations: () -> Unit = {},
    destinationMenuExpanded: Boolean = false,
    onDestinationMenuExpandedChange: (Boolean) -> Unit = {},
    destinationMenuContent: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit = { _ -> },
    destinationMessage: String? = null,
    partialMoveTargetName: String? = null,
    onRetryMoveOnly: () -> Unit = {},
    onKeepDefaultLibrary: () -> Unit,
    onRetryRemoteReconciliation: () -> Unit = {},
    onAcknowledgeRemoteReconciliation: () -> Unit = {},
    focusChapterId: String? = null,
    onFocusHandled: () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        mutation?.let { DetailMutationBanner(it) }
        destinationMessage?.let {
            DestinationFeedbackBanner(
                message = it,
                partialMoveTargetName = partialMoveTargetName,
                onRetryMoveOnly = onRetryMoveOnly,
                onKeepDefaultLibrary = onKeepDefaultLibrary,
            )
        }
        if (localState.reconciliation == "UNRESOLVED") {
            UnresolvedReconciliationBanner(
                operation = localState.reconciliationOperation,
                onRetry = onRetryRemoteReconciliation,
                onAcknowledge = onAcknowledgeRemoteReconciliation,
            )
        }
        when (state) {
            SourceBookState.Loading -> StateView(
                kind = TsuyomiStateKind.LOADING,
                title = stringResource(R.string.book_loading_detail),
                modifier = Modifier.weight(1f),
            )
            is SourceBookState.Failure -> DetailFailure(
                state = state,
                onRetry = onRetry,
                onUseOfflineCache = onUseOfflineCache,
                onOpenVerification = onOpenVerification,
                modifier = Modifier.weight(1f),
            )
            is SourceBookState.Content -> DetailContent(
                detail = state.value,
                directoryState = directoryState,
                localState = localState,
                coverState = coverState,
                unreadOnly = unreadOnly,
                descending = descending,
                selectedChapterId = selectedChapterId,
                cacheState = cacheState,
                onCacheAction = onCacheAction,
                onSetRating = onSetRating,
                onSearchAuthor = onSearchAuthor,
                tagEditorOpen = tagEditorOpen,
                tagDraft = tagDraft,
                onOpenTagEditor = onOpenTagEditor,
                onTagDraftChange = onTagDraftChange,
                onDismissTagEditor = onDismissTagEditor,
                onConfirmTag = onConfirmTag,
                tagMutation = mutation,
                onToggleUnreadOnly = onToggleUnreadOnly,
                onToggleOrder = onToggleOrder,
                onSelectChapter = onSelectChapter,
                onContinueReading = onContinueReading,
                onAddToLibrary = onAddToLibrary,
                onRequestRemoveFromLibrary = onRequestRemoveFromLibrary,
                primaryActionEnabled = mutation?.phase != DetailMutationPhase.WORKING,
                onOpenDestinations = onOpenDestinations,
                destinationMenuExpanded = destinationMenuExpanded,
                onDestinationMenuExpandedChange = onDestinationMenuExpandedChange,
                destinationMenuContent = destinationMenuContent,
                onRetryDirectory = onRetry,
                onUseOfflineCache = onUseOfflineCache,
                onOpenVerification = onOpenVerification,
                focusChapterId = focusChapterId,
                onFocusHandled = onFocusHandled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
@Composable
private fun DestinationFeedbackBanner(
    message: String,
    partialMoveTargetName: String?,
    onRetryMoveOnly: () -> Unit,
    onKeepDefaultLibrary: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            partialMoveTargetName?.let { targetName ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TsuyomiButton(
                        text = stringResource(R.string.book_keep_default_library),
                        onClick = onKeepDefaultLibrary,
                        style = TsuyomiButtonStyle.TEXT,
                    )
                    TsuyomiButton(
                        text = stringResource(R.string.book_retry_move_only, targetName),
                        onClick = onRetryMoveOnly,
                        style = TsuyomiButtonStyle.TEXT,
                    )
                }
            }
        }
    }
}


@Composable
private fun DetailContent(
    detail: SourceBookDetail,
    directoryState: SourceBookState<SourceDirectory>,
    localState: DetailLocalState,
    coverState: CoverUiState,
    unreadOnly: Boolean,
    descending: Boolean,
    selectedChapterId: String?,
    cacheState: DetailCacheState,
    onCacheAction: (DetailCacheAction) -> Unit,
    onSetRating: (Int?) -> Unit,
    onSearchAuthor: (String) -> Unit,
    tagEditorOpen: Boolean,
    tagDraft: String,
    onOpenTagEditor: () -> Unit,
    onTagDraftChange: (String) -> Unit,
    onDismissTagEditor: () -> Unit,
    onConfirmTag: () -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
    onSelectChapter: (SourceChapter) -> Unit,
    onContinueReading: (SourceChapter) -> Unit,
    onAddToLibrary: () -> Unit,
    onRequestRemoveFromLibrary: () -> Unit,
    primaryActionEnabled: Boolean,
    onOpenDestinations: () -> Unit,
    destinationMenuExpanded: Boolean,
    onDestinationMenuExpandedChange: (Boolean) -> Unit,
    destinationMenuContent: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit,
    onRetryDirectory: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    focusChapterId: String?,
    onFocusHandled: () -> Unit,
    tagMutation: DetailMutationStatus?,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val cacheSelectionFocusRequester = remember { FocusRequester() }
    val atDirectory by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex >= 3 }
    }
    val allChapters = (directoryState as? SourceBookState.Content)?.value?.chapters.orEmpty()
    val currentChapterId = selectedChapterId ?: localState.progressChapterId
    val chapterItems = remember(
        allChapters,
        currentChapterId,
        localState.completedChapterIds,
        localState.updatedChapterIds,
        descending,
        cacheState.chapters,
    ) {
        allChapters
            .map { chapter ->
                DetailChapterItem(
                    chapter = chapter,
                    current = chapter.chapterId == currentChapterId,
                    read = chapter.chapterId in localState.completedChapterIds,
                    updated = chapter.chapterId in localState.updatedChapterIds,
                    downloaded = cacheState.chapters[chapter.chapterId] == DetailChapterCachePhase.CACHED,
                )
            }
            .let { if (descending) it.reversed() else it }
    }
    val visibleChapters = if (unreadOnly) chapterItems.filter { it.read != true } else chapterItems
    val cacheSelectableChapterIds = remember(visibleChapters, cacheState.chapters) {
        visibleChapters
            .filter { cacheState.chapters[it.chapter.chapterId] != DetailChapterCachePhase.CACHED }
            .mapTo(linkedSetOf()) { it.chapter.chapterId }
    }
    val unnamedVolume = stringResource(R.string.book_ungrouped_volume)
    val volumeGroups = remember(visibleChapters, unnamedVolume) {
        val grouped = linkedMapOf<String, MutableList<DetailChapterItem>>()
        visibleChapters.forEach { item ->
            grouped.getOrPut(item.chapter.volumeTitle ?: "") { mutableListOf() } += item
        }
        grouped.map { (key, items) -> DetailVolumeGroup(key, key.ifBlank { unnamedVolume }, items) }
    }
    val initialVolumeKey = chapterItems.firstOrNull { it.current }?.chapter?.volumeTitle.orEmpty()
        .takeIf { key -> volumeGroups.any { it.key == key } }
        ?: volumeGroups.firstOrNull()?.key
    var expandedVolumeKeys by rememberSaveable(detail.summary.identity.sourceId, detail.summary.identity.remoteBookId) {
        mutableStateOf(listOfNotNull(initialVolumeKey))
    }
    LaunchedEffect(volumeGroups.map { it.key }) {
        if (volumeGroups.isNotEmpty() && expandedVolumeKeys.none { key -> volumeGroups.any { it.key == key } }) {
            expandedVolumeKeys = listOf(volumeGroups.first().key)
        }
    }
    LaunchedEffect(cacheState.selecting, directoryState) {
        if (cacheState.selecting && directoryState is SourceBookState.Content) {
            listState.scrollToItem(3)
            cacheSelectionFocusRequester.requestFocus()
        }
    }
    val focusVolumeKey = focusChapterId
        ?.let { chapterId -> visibleChapters.firstOrNull { it.chapter.chapterId == chapterId } }
        ?.chapter
        ?.volumeTitle
        .orEmpty()
        .takeIf { key -> volumeGroups.any { it.key == key } }
    LaunchedEffect(focusChapterId, focusVolumeKey) {
        if (focusVolumeKey != null && focusVolumeKey !in expandedVolumeKeys) {
            expandedVolumeKeys = expandedVolumeKeys + focusVolumeKey
        }
    }
    val focusItemIndex = focusChapterId?.let { chapterId ->
        val volumeIndex = volumeGroups.indexOfFirst { volume ->
            volume.items.any { item -> item.chapter.chapterId == chapterId }
        }
        if (volumeIndex < 0 || volumeGroups[volumeIndex].key !in expandedVolumeKeys) {
            null
        } else {
            val chapterIndex = volumeGroups[volumeIndex].items.indexOfFirst { item -> item.chapter.chapterId == chapterId }
            4 + volumeGroups.take(volumeIndex).sumOf { volume ->
                1 + if (volume.key in expandedVolumeKeys) volume.items.size else 0
            } + 1 + chapterIndex
        }
    }
    LaunchedEffect(focusChapterId, focusItemIndex) {
        if (focusChapterId != null && focusItemIndex != null) {
            listState.scrollToItem(focusItemIndex)
            onFocusHandled()
        }
    }
    val savedProgressChapter = localState.progressChapterId?.let { progressChapterId ->
        allChapters.firstOrNull { it.chapterId == progressChapterId }
    }
    val readingChapter = savedProgressChapter ?: allChapters.firstOrNull()

    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("book-detail-scroll"),
        ) {
            item(key = "identity") {
                DetailIdentityModule(
                    detail = detail,
                    coverState = coverState,
                    localState = localState,
                    onSetRating = onSetRating,
                    onSearchAuthor = onSearchAuthor,
                    onAddToLibrary = onAddToLibrary,
                    onRequestRemoveFromLibrary = onRequestRemoveFromLibrary,
                    primaryActionEnabled = primaryActionEnabled,
                    onOpenDestinations = onOpenDestinations,
                    destinationMenuExpanded = destinationMenuExpanded,
                    onDestinationMenuExpandedChange = onDestinationMenuExpandedChange,
                    destinationMenuContent = destinationMenuContent,
                )
            }
            item(key = "tags") {
                DetailTagActionsModule(
                    tags = (localState.localTags + detail.tags).distinct(),
                    enabled = localState.localTagsEditable,
                    mutation = tagMutation,
                    tagEditorOpen = tagEditorOpen,
                    tagDraft = tagDraft,
                    onOpenTagEditor = onOpenTagEditor,
                    onTagDraftChange = onTagDraftChange,
                    onDismissTagEditor = onDismissTagEditor,
                    onConfirmTag = onConfirmTag,
                )
            }
            item(key = "introduction") {
                DetailIntroductionModule(description = detail.description.orEmpty())
            }
            when (directoryState) {
                SourceBookState.Loading -> item(key = "directory-loading") {
                    StateView(
                        kind = TsuyomiStateKind.LOADING,
                        title = stringResource(R.string.book_loading_directory),
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    )
                }
                is SourceBookState.Failure -> item(key = "directory-failure") {
                    DetailFailure(
                        state = directoryState,
                        onRetry = onRetryDirectory,
                        onUseOfflineCache = onUseOfflineCache,
                        onOpenVerification = onOpenVerification,
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                    )
                }
                is SourceBookState.Content -> {
                    item(key = "directory-header") {
                        DetailDirectoryHeader(
                            totalChapters = directoryState.value.chapters.size,
                            unreadOnly = unreadOnly,
                            unreadFilterAvailable = chapterItems.isNotEmpty(),
                            descending = descending,
                            onToggleUnreadOnly = onToggleUnreadOnly,
                            onToggleOrder = onToggleOrder,
                        )
                    }
                    if (cacheState.selecting) {
                        item(key = "cache-selection") {
                            DetailCacheSelectionToolbar(
                                cacheState = cacheState,
                                selectableChapterIds = cacheSelectableChapterIds,
                                onCacheAction = onCacheAction,
                                focusRequester = cacheSelectionFocusRequester,
                            )
                        }
                    }
                    volumeGroups.forEach { volume ->
                        val expanded = volume.key in expandedVolumeKeys
                        item(key = "volume:${volume.key}") {
                            DetailVolumeHeader(
                                title = volume.title,
                                chapterCount = volume.items.size,
                                expanded = expanded,
                                onToggle = {
                                    expandedVolumeKeys = if (expanded) {
                                        expandedVolumeKeys - volume.key
                                    } else {
                                        expandedVolumeKeys + volume.key
                                    }
                                },
                            )
                        }
                        if (expanded) {
                            items(volume.items, key = { it.chapter.chapterId }) { chapter ->
                                if (cacheState.selecting) {
                                    DetailCacheChapterRow(
                                        item = chapter,
                                        cacheState = cacheState,
                                        onCacheAction = onCacheAction,
                                    )
                                } else {
                                    DetailChapterRow(chapter, onSelectChapter)
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(TsuyomiSpacing.Md),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
        ) {
            if (!cacheState.selecting && readingChapter != null) {
                if (atDirectory) {
                    TsuyomiAdaptiveListFab(
                        state = listState,
                        topLabel = stringResource(R.string.book_quick_to_top),
                        endLabel = stringResource(R.string.book_quick_to_bottom),
                        hideWhileScrolling = true,
                    )
                } else {
                    val readingLabel = stringResource(
                        if (savedProgressChapter == null) R.string.book_start_reading else R.string.book_continue_reading,
                    )
                    TsuyomiExtendedFab(
                        text = readingLabel,
                        imageVector = TsuyomiIcons.ContinueReading,
                        contentDescription = readingLabel,
                        onClick = { onContinueReading(readingChapter) },
                        modifier = Modifier.testTag("detail-reading-fab"),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnresolvedReconciliationBanner(
    operation: String?,
    onRetry: () -> Unit,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val operationKey = operation?.uppercase()
    val operationLabel = when (operationKey) {
        "ADD" -> "加入网站收藏"
        "MOVE" -> "移动网站收藏"
        "REMOVE" -> "从网站收藏移除"
        else -> "网站收藏操作"
    }
    val canAcknowledge = operationKey != "ADD"
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("book-detail-unresolved-banner"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${operationLabel}结果待确认",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = if (canAcknowledge) {
                    "上次${operationLabel}已发送，但服务器结果未能确认。重试会继续同一操作；解除锁定只允许再次操作，不代表网站已成功更新。"
                } else {
                    "上次${operationLabel}已发送，但服务器结果未能确认。请重试同一幂等操作；在网站状态确认前不会解除锁定。"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canAcknowledge) {
                    TsuyomiButton(
                        text = "仅解除锁定",
                        onClick = onAcknowledge,
                        style = TsuyomiButtonStyle.TEXT,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                TsuyomiButton(
                    text = "重试${operationLabel}",
                    onClick = onRetry,
                    style = TsuyomiButtonStyle.SECONDARY,
                )
            }
        }
    }
}
