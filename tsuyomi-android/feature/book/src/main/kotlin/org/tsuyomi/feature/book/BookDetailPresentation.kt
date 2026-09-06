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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory

@Immutable
data class DetailLocalState(
    val inLibrary: Boolean = false,
    val rating: Int? = null,
    val localTags: List<String> = emptyList(),
    val readLater: Boolean = false,
    val progressChapterId: String? = null,
    val progressChapterFraction: Double? = null,
    val reconciliation: String? = null,
    val remoteRemoveEnabled: Boolean = false,
    val remoteMoveEnabled: Boolean = false,
)

enum class DetailMutationOperation {
    ADD_TO_LIBRARY,
    REMOVE_FROM_LIBRARY,
    CACHE_DETAIL,
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
    onSetRating: (Int?) -> Unit,
    onSearchAuthor: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
    onSelectChapter: (SourceChapter) -> Unit,
    onContinueReading: (SourceChapter) -> Unit,
    onAddToLibrary: () -> Unit,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDestinations: () -> Unit = {},
    destinationMenuExpanded: Boolean = false,
    onDestinationMenuExpandedChange: (Boolean) -> Unit = {},
    destinationMenuContent: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit = { _ -> },
    onRemoveFromRemote: () -> Unit = {},
    onMoveRemote: () -> Unit = {},
    onRetryRemoteReconciliation: () -> Unit = {},
    onAcknowledgeRemoteReconciliation: () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        mutation?.let { DetailMutationBanner(it) }
        if (localState.reconciliation == "UNRESOLVED") {
            UnresolvedReconciliationBanner(
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
                onSetRating = onSetRating,
                onSearchAuthor = onSearchAuthor,
                onAddTag = onAddTag,
                onToggleUnreadOnly = onToggleUnreadOnly,
                onToggleOrder = onToggleOrder,
                onSelectChapter = onSelectChapter,
                onContinueReading = onContinueReading,
                onAddToLibrary = onAddToLibrary,
                onOpenDestinations = onOpenDestinations,
                destinationMenuExpanded = destinationMenuExpanded,
                onDestinationMenuExpandedChange = onDestinationMenuExpandedChange,
                destinationMenuContent = destinationMenuContent,
                onRetryDirectory = onRetry,
                onUseOfflineCache = onUseOfflineCache,
                onOpenVerification = onOpenVerification,
                modifier = Modifier.weight(1f),
            )
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
    onSetRating: (Int?) -> Unit,
    onSearchAuthor: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
    onSelectChapter: (SourceChapter) -> Unit,
    onContinueReading: (SourceChapter) -> Unit,
    onAddToLibrary: () -> Unit,
    onOpenDestinations: () -> Unit,
    destinationMenuExpanded: Boolean,
    onDestinationMenuExpandedChange: (Boolean) -> Unit,
    destinationMenuContent: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit,
    onRetryDirectory: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val atDirectory by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex >= 3 }
    }
    val allChapters = (directoryState as? SourceBookState.Content)?.value?.chapters.orEmpty()
    val currentChapterId = selectedChapterId ?: localState.progressChapterId
    val progressIndex = allChapters.indexOfFirst { it.chapterId == localState.progressChapterId }
    val chapterItems = remember(
        allChapters,
        currentChapterId,
        localState.progressChapterId,
        localState.progressChapterFraction,
        descending,
    ) {
        allChapters
            .mapIndexed { index, chapter ->
                val read = when {
                    progressIndex < 0 -> false
                    index < progressIndex -> true
                    index == progressIndex -> (localState.progressChapterFraction ?: 0.0) >= 1.0
                    else -> false
                }
                DetailChapterItem(
                    chapter = chapter,
                    current = chapter.chapterId == currentChapterId,
                    read = read,
                )
            }
            .let { if (descending) it.reversed() else it }
    }
    val visibleChapters = if (unreadOnly) chapterItems.filter { !it.read!! } else chapterItems
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
    val continueChapter = allChapters.firstOrNull { it.chapterId == currentChapterId } ?: allChapters.firstOrNull()

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
                    onOpenDestinations = onOpenDestinations,
                    destinationMenuExpanded = destinationMenuExpanded,
                    onDestinationMenuExpandedChange = onDestinationMenuExpandedChange,
                    destinationMenuContent = destinationMenuContent,
                )
            }
            item(key = "tags") {
                DetailTagActionsModule(
                    tags = (localState.localTags + detail.tags).distinct(),
                    enabled = localState.inLibrary,
                    onAddTag = onAddTag,
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
                                DetailChapterRow(chapter, onSelectChapter)
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
            if (continueChapter != null) {
                if (atDirectory) {
                    TsuyomiAdaptiveListFab(
                        state = listState,
                        topLabel = stringResource(R.string.book_quick_to_top),
                        endLabel = stringResource(R.string.book_quick_to_bottom),
                    )
                } else {
                    ExtendedFloatingActionButton(
                        onClick = { onContinueReading(continueChapter) },
                        modifier = Modifier.testTag("detail-reading-fab"),
                        icon = { Icon(TsuyomiIcons.ContinueReading, contentDescription = null) },
                        text = { Text(stringResource(R.string.book_continue_reading)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UnresolvedReconciliationBanner(
    onRetry: () -> Unit,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = "远程书架状态不同步 (UNRESOLVED)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "上次远端书架操作未能在服务器成功确认。在解除状态前，该书籍的远端变更已锁定。",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAcknowledge) {
                    Text("解除锁定")
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onRetry) {
                    Text("重试同步")
                }
            }
        }
    }
}
