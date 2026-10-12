/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.PaginationBar
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiCheckboxRow
import org.tsuyomi.core.ui.components.TsuyomiExtendedFab
import org.tsuyomi.core.ui.components.TsuyomiIconButton
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import org.tsuyomi.core.ui.components.TsuyomiMenuItem
import org.tsuyomi.core.ui.components.TsuyomiTriStateCheckboxRow
import org.tsuyomi.core.ui.components.LocalTsuyomiPagerMaxContentHeight
import org.tsuyomi.core.ui.components.TsuyomiDialog
import org.tsuyomi.core.ui.components.TsuyomiTextField
import androidx.compose.ui.semantics.selected
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderNavigationTarget

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
    val progressFragment: String? = null,
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
    val chapter: ReaderNavigationTarget,
    val current: Boolean = false,
    val read: Boolean? = null,
    val updated: Boolean = false,
    val downloaded: Boolean = false,
    val navigationOrdinal: Int = 0,
    val authoredOccurrence: Boolean = false,
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
    val totalChapterCount: Int = items.size,
)


@Composable
internal fun StandardBookDetailScreen(
    state: BookScreenState<BookDetailContent>,
    directoryState: BookScreenState<BookDirectory>,
    localState: DetailLocalState,
    mutation: DetailMutationStatus?,
    coverState: CoverUiState,
    unreadOnly: Boolean,
    descending: Boolean,
    selectedChapterId: String?,
    cacheState: DetailCacheState,
    onCacheAction: (DetailCacheAction) -> Unit,
    onSetRating: (Int?) -> Unit,
    onSearchAuthor: ((String) -> Unit)?,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
    onSelectChapter: (ReaderNavigationTarget) -> Unit,
    onContinueReading: (ReaderNavigationTarget) -> Unit,
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
    destinationMenuItemCount: Int = 0,
    destinationMenuItemKey: (Int) -> String = { it.toString() },
    destinationMenuItemContent: @Composable ColumnScope.(Int, dismissMenu: () -> Unit) -> Unit = { _, _ -> },
    destinationMessage: String? = null,
    partialMoveTargetName: String? = null,
    onRetryMoveOnly: () -> Unit = {},
    onKeepDefaultLibrary: () -> Unit,
    onRetryRemoteReconciliation: () -> Unit = {},
    onResyncRemoteLibrary: () -> Unit = {},
    resyncMessage: String? = null,
    focusChapterId: String? = null,
    onFocusHandled: () -> Unit = {},
    einkPagination: Boolean = false,
) {
    val detailStateHolder = rememberSaveableStateHolder()
    val detailIdentity = (state as? BookScreenState.Content)?.value?.identity?.storageKey ?: "status"
    val detailContent = @Composable { contentModifier: Modifier ->
        // Each profile has a different composition tree; neither may replace the other's saved page/volume state.
        detailStateHolder.SaveableStateProvider("detail:$detailIdentity:${if (einkPagination) "EINK" else "STANDARD"}") {
        when (state) {
            BookScreenState.Loading -> StateView(
                kind = TsuyomiStateKind.LOADING,
                title = stringResource(R.string.book_loading_detail),
                modifier = contentModifier,
            )
            is BookScreenState.Failure -> DetailFailure(
                state = state,
                onRetry = onRetry,
                onUseOfflineCache = onUseOfflineCache,
                onOpenVerification = onOpenVerification,
                modifier = contentModifier,
            )
            is BookScreenState.Content -> DetailContent(
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
                destinationMenuItemCount = destinationMenuItemCount,
                destinationMenuItemKey = destinationMenuItemKey,
                destinationMenuItemContent = destinationMenuItemContent,
                onRetryDirectory = onRetry,
                onUseOfflineCache = onUseOfflineCache,
                onOpenVerification = onOpenVerification,
                focusChapterId = focusChapterId,
                onFocusHandled = onFocusHandled,
                einkPagination = einkPagination,
                modifier = contentModifier,
            )
        }
        }
    }
    val unresolvedMutation = mutation?.operation == DetailMutationOperation.REMOVE_FROM_REMOTE ||
        mutation?.operation == DetailMutationOperation.MOVE_REMOTE ||
        mutation?.operation == DetailMutationOperation.RECONCILE_RETRY
    val mutationBanner = @Composable {
        if (localState.reconciliation != "UNRESOLVED" || !unresolvedMutation) {
            mutation?.let { DetailMutationBanner(it) }
        }
    }
    val destinationBanner = @Composable {
        if (localState.reconciliation != "UNRESOLVED") {
            destinationMessage?.let {
                DestinationFeedbackBanner(it, partialMoveTargetName, onRetryMoveOnly, onKeepDefaultLibrary)
            }
        } else {
            UnresolvedReconciliationBanner(
                operation = localState.reconciliationOperation,
                onRetry = onRetryRemoteReconciliation,
                resyncMessage = resyncMessage,
                onResync = onResyncRemoteLibrary,
            )
        }
    }
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
        mutationBanner()
        destinationBanner()
        detailContent(Modifier.weight(1f))
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
    detail: BookDetailContent,
    directoryState: BookScreenState<BookDirectory>,
    localState: DetailLocalState,
    coverState: CoverUiState,
    unreadOnly: Boolean,
    descending: Boolean,
    selectedChapterId: String?,
    cacheState: DetailCacheState,
    onCacheAction: (DetailCacheAction) -> Unit,
    onSetRating: (Int?) -> Unit,
    onSearchAuthor: ((String) -> Unit)?,
    tagEditorOpen: Boolean,
    tagDraft: String,
    onOpenTagEditor: () -> Unit,
    onTagDraftChange: (String) -> Unit,
    onDismissTagEditor: () -> Unit,
    onConfirmTag: () -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
    onSelectChapter: (ReaderNavigationTarget) -> Unit,
    onContinueReading: (ReaderNavigationTarget) -> Unit,
    onAddToLibrary: () -> Unit,
    onRequestRemoveFromLibrary: () -> Unit,
    primaryActionEnabled: Boolean,
    onOpenDestinations: () -> Unit,
    destinationMenuExpanded: Boolean,
    onDestinationMenuExpandedChange: (Boolean) -> Unit,
    destinationMenuItemCount: Int,
    destinationMenuItemKey: (Int) -> String,
    destinationMenuItemContent: @Composable ColumnScope.(Int, dismissMenu: () -> Unit) -> Unit,
    onRetryDirectory: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    focusChapterId: String?,
    onFocusHandled: () -> Unit,
    tagMutation: DetailMutationStatus?,
    einkPagination: Boolean,
    modifier: Modifier,
) {
    val measuredPagerState = key(detail.identity.storageKey) {
        rememberTsuyomiMeasuredPagerState(
            initialAnchorKey = detailPagerItemKey(detail.identity, "identity"),
        )
    }
    if (einkPagination) {
        EinkBookDetailContent(
            detail = detail,
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
            onToggleUnreadOnly = onToggleUnreadOnly,
            onToggleOrder = onToggleOrder,
            onSelectChapter = onSelectChapter,
            onContinueReading = onContinueReading,
            onAddToLibrary = onAddToLibrary,
            onRequestRemoveFromLibrary = onRequestRemoveFromLibrary,
            primaryActionEnabled = primaryActionEnabled,
            onOpenDestinations = onOpenDestinations,
            destinationMenuExpanded = destinationMenuExpanded,
            onDestinationMenuExpandedChange = onDestinationMenuExpandedChange,
            destinationMenuItemCount = destinationMenuItemCount,
            destinationMenuItemKey = destinationMenuItemKey,
            destinationMenuItemContent = destinationMenuItemContent,
            onRetryDirectory = onRetryDirectory,
            onUseOfflineCache = onUseOfflineCache,
            onOpenVerification = onOpenVerification,
            focusChapterId = focusChapterId,
            onFocusHandled = onFocusHandled,
            tagMutation = tagMutation,
            pagerState = measuredPagerState,
            modifier = modifier,
        )
        return
    }
    val listState = rememberLazyListState()
    val cacheSelectionFocusRequester = remember { FocusRequester() }
    val atDirectory by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex >= 3 }
    }
    val resolvedDirectoryState: BookScreenState<BookDirectory> = remember(directoryState, detail.identity) {
        val loaded = (directoryState as? BookScreenState.Content)?.value
        if (loaded != null && loaded.identity != detail.identity) {
            BookScreenState.Failure(offlineCacheAvailable = false)
        } else {
            directoryState
        }
    }
    val allChapters = (resolvedDirectoryState as? BookScreenState.Content)?.value?.navigationTargets.orEmpty()
    val authoredNavigation = (resolvedDirectoryState as? BookScreenState.Content)?.value?.authoredNavigation == true
    val currentChapterId = selectedChapterId ?: localState.progressChapterId
    val chapterItems = remember(
        allChapters,
        authoredNavigation,
        currentChapterId,
        localState.progressFragment,
        localState.completedChapterIds,
        localState.updatedChapterIds,
        descending,
        cacheState.chapters,
    ) {
        allChapters
            .mapIndexed { ordinal, chapter ->
                DetailChapterItem(
                    chapter = chapter,
                    current = chapter.contentId == currentChapterId && chapter.fragment == localState.progressFragment,
                    read = chapter.contentId in localState.completedChapterIds,
                    updated = chapter.contentId in localState.updatedChapterIds,
                    downloaded = cacheState.chapters[chapter.contentId] == DetailChapterCachePhase.CACHED,
                    navigationOrdinal = ordinal,
                    authoredOccurrence = authoredNavigation,
                )
            }
            .let { if (descending) it.reversed() else it }
    }
    val visibleChapters = remember(chapterItems, unreadOnly) {
        if (unreadOnly) chapterItems.filter { it.read != true } else chapterItems
    }
    val cacheSelectableChapterIds = remember(visibleChapters, cacheState.chapters) {
        visibleChapters
            .filter { cacheState.chapters[it.chapter.contentId] != DetailChapterCachePhase.CACHED }
            .mapTo(linkedSetOf()) { it.chapter.contentId }
    }
    val unnamedVolume = stringResource(R.string.book_ungrouped_volume)
    val volumeGroups = remember(visibleChapters, unnamedVolume) {
        detailVolumeGroups(visibleChapters, unnamedVolume)
    }
    val initialVolumeKey = volumeGroups.firstOrNull { volume -> volume.items.any { it.current } }?.key
        ?: volumeGroups.firstOrNull()?.key
    var expandedVolumeKeys by rememberSaveable(detail.identity.storageKey) {
        mutableStateOf(listOfNotNull(initialVolumeKey))
    }
    LaunchedEffect(volumeGroups.map { it.key }) {
        if (volumeGroups.isNotEmpty() && expandedVolumeKeys.none { key -> volumeGroups.any { it.key == key } }) {
            expandedVolumeKeys = listOf(volumeGroups.first().key)
        }
    }
    LaunchedEffect(cacheState.selecting, resolvedDirectoryState) {
        if (cacheState.selecting && resolvedDirectoryState is BookScreenState.Content) {
            listState.scrollToItem(3)
            cacheSelectionFocusRequester.requestFocus()
        }
    }
    val pageVolumeGroups = volumeGroups
    val focusVolumeKey = focusChapterId?.let { chapterId ->
        volumeGroups.firstOrNull { volume -> volume.items.any { it.chapter.contentId == chapterId } }?.key
    }
    LaunchedEffect(focusChapterId, focusVolumeKey) {
        if (focusVolumeKey != null && focusVolumeKey !in expandedVolumeKeys) {
            expandedVolumeKeys = expandedVolumeKeys + focusVolumeKey
        }
    }
    val focusItemIndex = focusChapterId?.let { chapterId ->
        val volumeIndex = pageVolumeGroups.indexOfFirst { volume ->
            volume.items.any { item -> item.chapter.contentId == chapterId }
        }
        if (volumeIndex < 0 || pageVolumeGroups[volumeIndex].key !in expandedVolumeKeys) {
            null
        } else {
            val chapterIndex = pageVolumeGroups[volumeIndex].items.indexOfFirst { item ->
                item.chapter.contentId == chapterId
            }
            4 + (if (cacheState.selecting) 1 else 0) + pageVolumeGroups.take(volumeIndex).sumOf { volume ->
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
        allChapters.firstOrNull { it.contentId == progressChapterId }
    }
    val readingChapter = savedProgressChapter ?: allChapters.firstOrNull()
    Column(modifier) {
        Box(Modifier.weight(1f)) {
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
                    destinationMenuItemCount = destinationMenuItemCount,
                    destinationMenuItemKey = destinationMenuItemKey,
                    destinationMenuItemContent = destinationMenuItemContent,
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
            when (resolvedDirectoryState) {
                BookScreenState.Loading -> item(key = "directory-loading") {
                    StateView(
                        kind = TsuyomiStateKind.LOADING,
                        title = stringResource(R.string.book_loading_directory),
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    )
                }
                is BookScreenState.Failure -> item(key = "directory-failure") {
                    DetailFailure(
                        state = resolvedDirectoryState,
                        onRetry = onRetryDirectory,
                        onUseOfflineCache = onUseOfflineCache,
                        onOpenVerification = onOpenVerification,
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                    )
                }
                is BookScreenState.Content -> {
                    item(key = "directory-header") {
                        DetailDirectoryHeader(
                            totalChapters = resolvedDirectoryState.value.navigationTargets.size,
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
                    pageVolumeGroups.forEach { volume ->
                        val expanded = volume.key in expandedVolumeKeys
                        item(key = "volume:${volume.key}") {
                            DetailVolumeHeader(
                                title = volume.title,
                                chapterCount = volume.totalChapterCount,
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
                            items(volume.items, key = { detailChapterOccurrenceKey(it) }) { chapter ->
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
                if (atDirectory && !einkPagination) {
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
}

@Composable
private fun UnresolvedReconciliationBanner(
    operation: String?,
    onRetry: () -> Unit,
    resyncMessage: String?,
    onResync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val operationKey = operation?.uppercase()
    val operationLabel = when (operationKey) {
        "ADD" -> "加入网站收藏"
        "MOVE" -> "移动网站收藏"
        "REMOVE" -> "从网站收藏移除"
        else -> "网站收藏操作"
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("book-detail-unresolved-banner")
            .semantics { liveRegion = LiveRegionMode.Polite },
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
                text = stringResource(R.string.book_reconciliation_resync_note),
                style = MaterialTheme.typography.bodySmall,
            )
            resyncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TsuyomiButton(
                    text = stringResource(R.string.book_reconciliation_resync),
                    onClick = onResync,
                    style = TsuyomiButtonStyle.TEXT,
                )
                Spacer(Modifier.width(8.dp))
                TsuyomiButton(
                    text = "重试${operationLabel}",
                    onClick = onRetry,
                    style = TsuyomiButtonStyle.SECONDARY,
                )
            }
        }
    }
}

private fun detailPagerItemKey(identity: BookIdentity, part: String) = "${identity.storageKey}:$part"
private fun detailChapterPagerItemKey(identity: BookIdentity, chapterId: String) = detailPagerItemKey(identity, "chapter:$chapterId")
private fun detailChapterOccurrenceKey(item: DetailChapterItem): String =
    if (!item.authoredOccurrence && item.chapter.fragment == null) item.chapter.contentId
    else "${item.navigationOrdinal}:${item.chapter.contentId}:${item.chapter.fragment}"

private fun detailVolumeGroups(items: List<DetailChapterItem>, unnamedVolume: String): List<DetailVolumeGroup> {
    if (items.any { it.authoredOccurrence || it.chapter.fragment != null }) return buildList {
        var start = 0
        while (start < items.size) {
            val title = items[start].chapter.sectionTitle.orEmpty()
            var end = start + 1
            while (end < items.size && items[end].chapter.sectionTitle.orEmpty() == title) end++
            add(DetailVolumeGroup("toc-volume:${items[start].navigationOrdinal}", title.ifBlank { unnamedVolume }, items.subList(start, end)))
            start = end
        }
    }
    val grouped = linkedMapOf<String, MutableList<DetailChapterItem>>()
    items.forEach { item -> grouped.getOrPut(item.chapter.sectionTitle.orEmpty()) { mutableListOf() } += item }
    return grouped.map { (key, rows) -> DetailVolumeGroup(key, key.ifBlank { unnamedVolume }, rows) }
}

@Composable
private fun EinkBookDetailContent(
    detail: BookDetailContent, directoryState: BookScreenState<BookDirectory>, localState: DetailLocalState,
    coverState: CoverUiState, unreadOnly: Boolean, descending: Boolean, selectedChapterId: String?,
    cacheState: DetailCacheState, onCacheAction: (DetailCacheAction) -> Unit, onSetRating: (Int?) -> Unit,
    onSearchAuthor: ((String) -> Unit)?, tagEditorOpen: Boolean, tagDraft: String,
    onOpenTagEditor: () -> Unit, onTagDraftChange: (String) -> Unit, onDismissTagEditor: () -> Unit, onConfirmTag: () -> Unit,
    onToggleUnreadOnly: () -> Unit, onToggleOrder: () -> Unit, onSelectChapter: (ReaderNavigationTarget) -> Unit,
    onContinueReading: (ReaderNavigationTarget) -> Unit, onAddToLibrary: () -> Unit, onRequestRemoveFromLibrary: () -> Unit,
    primaryActionEnabled: Boolean, onOpenDestinations: () -> Unit, destinationMenuExpanded: Boolean,
    onDestinationMenuExpandedChange: (Boolean) -> Unit, destinationMenuItemCount: Int, destinationMenuItemKey: (Int) -> String,
    destinationMenuItemContent: @Composable ColumnScope.(Int, dismissMenu: () -> Unit) -> Unit,
    onRetryDirectory: () -> Unit, onUseOfflineCache: () -> Unit, onOpenVerification: () -> Unit,
    focusChapterId: String?, onFocusHandled: () -> Unit, tagMutation: DetailMutationStatus?,
    pagerState: TsuyomiMeasuredPagerState, modifier: Modifier,
) {
    val directory = (directoryState as? BookScreenState.Content)?.value?.takeIf { it.identity == detail.identity }
    val chapters = directory?.navigationTargets.orEmpty()
    val visible = remember(chapters, directory?.authoredNavigation, unreadOnly, descending, localState.completedChapterIds,
        localState.updatedChapterIds, localState.progressChapterId, localState.progressFragment, selectedChapterId, cacheState.chapters) {
        val rows = chapters.mapIndexed { ordinal, chapter ->
            DetailChapterItem(chapter,
                current = chapter.contentId == (selectedChapterId ?: localState.progressChapterId) && chapter.fragment == localState.progressFragment,
                read = chapter.contentId in localState.completedChapterIds,
                updated = chapter.contentId in localState.updatedChapterIds,
                downloaded = cacheState.chapters[chapter.contentId] == DetailChapterCachePhase.CACHED,
                navigationOrdinal = ordinal,
                authoredOccurrence = directory?.authoredNavigation == true)
        }
        val filtered = if (unreadOnly) rows.filter { it.read != true } else rows
        if (descending) filtered.asReversed() else filtered
    }
    val unnamedVolume = stringResource(R.string.book_ungrouped_volume)
    val volumes = remember(visible, unnamedVolume) { detailVolumeGroups(visible, unnamedVolume) }
    var expandedVolumes by rememberSaveable(detail.identity.storageKey) {
        mutableStateOf(listOfNotNull(volumes.firstOrNull { it.items.any { row -> row.current } }?.key ?: volumes.firstOrNull()?.key))
    }
    var directoryInitialized by rememberSaveable(detail.identity.storageKey) { mutableStateOf(chapters.isNotEmpty()) }
    LaunchedEffect(volumes) {
        if (!directoryInitialized && volumes.isNotEmpty()) {
            expandedVolumes = listOf((volumes.firstOrNull { it.items.any { row -> row.current } } ?: volumes.first()).key)
            directoryInitialized = true
        }
    }
    val metadataKeys = remember(directory != null, cacheState.selecting) {
        buildList {
            add("identity")
            add("tags")
            add("introduction")
            if (directory != null) add("directory")
            if (cacheState.selecting) add("cache-selection")
        }
    }
    val metadataCount = metadataKeys.size
    val directoryCount = if (directory == null) 1 else volumes.sumOf { 1 + if (it.key in expandedVolumes) it.items.size else 0 }
    fun directoryEntry(index: Int): Pair<DetailVolumeGroup, DetailChapterItem?> {
        var remaining = index
        for (volume in volumes) {
            if (remaining == 0) return volume to null
            remaining--
            if (volume.key in expandedVolumes) {
                if (remaining < volume.items.size) return volume to volume.items[remaining]
                remaining -= volume.items.size
            }
        }
        error("Directory index outside visible projection")
    }
    fun itemKey(index: Int): String = when {
        index < metadataCount -> detailPagerItemKey(detail.identity, metadataKeys[index])
        directory == null -> detailPagerItemKey(detail.identity, "directory-status:${index - metadataCount}")
        else -> directoryEntry(index - metadataCount).let { (volume, chapter) ->
            chapter?.let { detailChapterPagerItemKey(detail.identity, detailChapterOccurrenceKey(it)) } ?: detailPagerItemKey(detail.identity, "volume:${volume.key}")
        }
    }
    LaunchedEffect(focusChapterId, visible) {
        if (focusChapterId != null) {
            visible.firstOrNull { it.chapter.contentId == focusChapterId }?.let { item ->
                val volume = volumes.first { group -> group.items.any { it.navigationOrdinal == item.navigationOrdinal } }
                if (volume.key !in expandedVolumes) expandedVolumes += volume.key
                pagerState.moveToAnchor(detailChapterPagerItemKey(detail.identity, detailChapterOccurrenceKey(item)))
                onFocusHandled()
            }
        }
    }
    LaunchedEffect(cacheState.selecting) {
        if (cacheState.selecting) pagerState.moveToAnchor(detailPagerItemKey(detail.identity, "cache-selection"))
    }
    val boundedModifier = LocalTsuyomiPagerMaxContentHeight.current?.let { modifier.heightIn(max = it) } ?: modifier
    Column(boundedModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        TsuyomiMeasuredPager(
            itemCount = metadataCount + directoryCount, itemKey = ::itemKey, state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("book-detail-pages"),
            itemContent = { index ->
                when {
                metadataKeys.getOrNull(index) == "identity" -> DetailIdentityModule(detail, coverState, localState, onSetRating, onSearchAuthor,
                    onAddToLibrary, onRequestRemoveFromLibrary, primaryActionEnabled, onOpenDestinations,
                    destinationMenuExpanded, onDestinationMenuExpandedChange, destinationMenuItemCount,
                    destinationMenuItemKey, destinationMenuItemContent)
                metadataKeys.getOrNull(index) == "tags" -> DetailTagActionsModule(tags = (localState.localTags + detail.tags).distinct(),
                    enabled = localState.localTagsEditable, mutation = tagMutation, tagEditorOpen = tagEditorOpen,
                    tagDraft = tagDraft, onOpenTagEditor = onOpenTagEditor, onTagDraftChange = onTagDraftChange,
                    onDismissTagEditor = onDismissTagEditor, onConfirmTag = onConfirmTag)
                metadataKeys.getOrNull(index) == "introduction" -> DetailIntroductionModule(detail.description.orEmpty())
                metadataKeys.getOrNull(index) == "directory" -> DetailDirectoryHeader(chapters.size, unreadOnly, chapters.isNotEmpty(), descending,
                    onToggleUnreadOnly, onToggleOrder)
                metadataKeys.getOrNull(index) == "cache-selection" -> {
                    val selectable = visible.filter { cacheState.chapters[it.chapter.contentId] != DetailChapterCachePhase.CACHED }.mapTo(linkedSetOf()) { it.chapter.contentId }
                    DetailCacheSelectionToolbar(cacheState, selectable, onCacheAction, remember { FocusRequester() })
                }
                directory == null -> when (directoryState) {
                    is BookScreenState.Failure -> DetailFailure(directoryState, onRetryDirectory, onUseOfflineCache,
                        onOpenVerification, Modifier.fillMaxWidth().height(minOf(280.dp, requireNotNull(LocalTsuyomiPagerMaxContentHeight.current))))
                    else -> StateView(TsuyomiStateKind.LOADING, stringResource(R.string.book_loading_directory),
                        Modifier.fillMaxWidth().height(minOf(240.dp, requireNotNull(LocalTsuyomiPagerMaxContentHeight.current))))
                }
                else -> {
                    val (volume, chapterItem) = directoryEntry(index - metadataCount)
                    if (chapterItem == null) DetailVolumeHeader(volume.title,
                        volume.items.size, volume.key in expandedVolumes, {
                            expandedVolumes = if (volume.key in expandedVolumes) expandedVolumes - volume.key else expandedVolumes + volume.key
                        })
                    else {
                        val chapter = chapterItem.chapter
                        val chapterModifier = Modifier.testTag("detail-chapter-${chapter.contentId}").semantics {
                            stateDescription = listOfNotNull(cacheState.chapters[chapter.contentId]?.name,
                                if (chapter.contentId in localState.completedChapterIds) "已读" else "未读",
                                "当前阅读".takeIf { chapterItem.current },
                                "有更新".takeIf { chapter.contentId in localState.updatedChapterIds }).joinToString("，")
                        }
                        if (cacheState.selecting) TsuyomiCheckboxRow(label = chapter.title,
                            checked = chapter.contentId in cacheState.selectedChapterIds,
                            onCheckedChange = { onCacheAction(DetailCacheAction.Toggle(chapter.contentId)) },
                            enabled = !cacheState.working && cacheState.chapters[chapter.contentId] != DetailChapterCachePhase.CACHED,
                            modifier = chapterModifier.testTag("detail-cache-chapter-${chapter.contentId}"))
                        else DetailChapterRow(chapterItem, onSelectChapter)
                    }
                }
            } },
        )
        if (!cacheState.selecting) {
            val readingChapter = chapters.firstOrNull { it.contentId == localState.progressChapterId } ?: chapters.firstOrNull()
            if (readingChapter != null) TsuyomiButton(stringResource(if (localState.progressChapterId == null) R.string.book_start_reading else R.string.book_continue_reading),
                { onContinueReading(readingChapter) }, modifier = Modifier.padding(vertical = TsuyomiSpacing.Sm).testTag("detail-reading-fab"))
        }
    }
}

@Composable
private fun DetailPagedText(text: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium) {
    TsuyomiMeasuredTextPager(text, Modifier.fillMaxWidth(), style = style, fillViewport = false, alwaysShowPageControls = false)
}
