/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiAdaptiveListFab
import org.tsuyomi.core.ui.components.TsuyomiStatusBadge
import org.tsuyomi.core.ui.components.TsuyomiOverflowAction
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiTopBarAction
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.shared.sourcecontract.SourceErrorCode

@Immutable
data class DetailLocalState(
    val inLibrary: Boolean = false,
    val rating: Int? = null,
    val localTags: List<String> = emptyList(),
    val readLater: Boolean = false,
    val progressChapterId: String? = null,
    val progressChapterFraction: Double? = null,
)

enum class DetailMutationOperation {
    ADD_TO_LIBRARY,
    REMOVE_FROM_LIBRARY,
    CACHE_DETAIL,
    REFRESH_DETAIL,
    SET_RATING,
    ADD_TAG,
    TOGGLE_READ_LATER,
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

private data class DetailVolumeGroup(
    val key: String,
    val title: String,
    val items: List<DetailChapterItem>,
)
private const val DETAIL_INTRODUCTION_PREVIEW_LINES = 3
private const val PUBLICATION_STATUS_INLINE_ID = "publication-status"
private val DetailRatingLayoutSize = 40.dp
private val DetailRatingGlyphWidth = 20.dp
private val DetailRatingGlyphEnvelopeOffset = (-10).dp
private val DetailRatingIconOffset = (-2).dp



@Composable
fun BookDetailTopBar(
    title: String,
    inLibrary: Boolean,
    onNavigateUp: () -> Unit,
    onCacheDetail: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
) {
    val actions = buildList {
        add(
            TsuyomiTopBarAction(
                icon = TsuyomiIcons.Cache,
                label = stringResource(R.string.book_cache_detail),
                onClick = onCacheDetail,
            ),
        )
    }
    val overflow = buildList {
        add(TsuyomiOverflowAction(stringResource(R.string.book_refresh_detail), onRefresh, TsuyomiIcons.Refresh))
        if (inLibrary) {
            add(
                TsuyomiOverflowAction(
                    stringResource(R.string.book_remove_from_library),
                    onRemoveFromLibrary,
                ),
            )
        }
    }
    TsuyomiTopBar(
        title = title,
        onNavigateUp = onNavigateUp,
        actions = actions,
        overflow = overflow,
    )
}

@Composable
internal fun StandardAtlasBookDetailScreen(
    state: SourceBookState<SourceBookDetail>,
    directoryState: SourceBookState<SourceDirectory>,
    localState: DetailLocalState,
    mutation: DetailMutationStatus?,
    coverState: CoverUiState,
    unreadOnly: Boolean,
    descending: Boolean,
    selectedChapterId: String?,
    onSetRating: (Int?) -> Unit,
    onAddTag: (String) -> Unit,
    onToggleReadLater: () -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
    onSelectChapter: (SourceChapter) -> Unit,
    onContinueReading: (SourceChapter) -> Unit,
    onAddToLibrary: () -> Unit,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        mutation?.let { DetailMutationBanner(it) }
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
                onAddTag = onAddTag,
                onToggleReadLater = onToggleReadLater,
                onToggleUnreadOnly = onToggleUnreadOnly,
                onToggleOrder = onToggleOrder,
                onSelectChapter = onSelectChapter,
                onContinueReading = onContinueReading,
                onAddToLibrary = onAddToLibrary,
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
    onAddTag: (String) -> Unit,
    onToggleReadLater: () -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
    onSelectChapter: (SourceChapter) -> Unit,
    onContinueReading: (SourceChapter) -> Unit,
    onAddToLibrary: () -> Unit,
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
                    onAddToLibrary = onAddToLibrary,
                    onToggleReadLater = onToggleReadLater,
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
                        icon = { Icon(TsuyomiIcons.ContinueReading, contentDescription = null) },
                        text = { Text(stringResource(R.string.book_continue_reading)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailIdentityModule(
    detail: SourceBookDetail,
    coverState: CoverUiState,
    localState: DetailLocalState,
    onSetRating: (Int?) -> Unit,
    onAddToLibrary: () -> Unit,
    onToggleReadLater: () -> Unit,
) {
    val publicationStatus = detail.status?.trim()?.takeIf { it.isNotEmpty() }
    val title = buildAnnotatedString {
        append(detail.summary.title)
        publicationStatus?.let { status ->
            append("\u00A0")
            appendInlineContent(PUBLICATION_STATUS_INLINE_ID, status)
        }
    }
    val inlineContent = publicationStatus?.let { status ->
        val placeholderWidth = (status.length.coerceAtLeast(3) * 0.48f + 0.56f).em
        mapOf(
            PUBLICATION_STATUS_INLINE_ID to InlineTextContent(
                placeholder = Placeholder(
                    width = placeholderWidth,
                    height = 0.86.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                TsuyomiStatusBadge(
                    text = status,
                    modifier = Modifier.fillMaxSize().testTag("detail-publication-status"),
                )
            },
        )
    }.orEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().padding(TsuyomiSpacing.Md).testTag("detail-identity-module"),
        verticalAlignment = Alignment.Top,
    ) {
        CoverImage(
            coverState,
            Modifier.size(width = 135.dp, height = 180.dp).testTag("detail-cover"),
        )
        Column(Modifier.weight(1f).padding(start = TsuyomiSpacing.Md)) {
            Text(
                text = title,
                inlineContent = inlineContent,
                modifier = Modifier.fillMaxWidth().testTag("detail-title-flow"),
                style = MaterialTheme.typography.headlineSmall,
            )
            detail.summary.author?.let {
                Text(
                    it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (localState.progressChapterId == null) {
                    stringResource(R.string.book_not_started)
                } else {
                    stringResource(R.string.book_progress_saved)
                },
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.padding(top = TsuyomiSpacing.Xs).testTag("detail-rating-row"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(5) { index ->
                    val value = index + 1
                    val selected = value <= (localState.rating ?: 0)
                    IconButton(
                        onClick = { onSetRating(if (localState.rating == value) null else value) },
                        enabled = localState.inLibrary,
                        modifier = Modifier
                            .size(DetailRatingLayoutSize)
                            .semantics { this.selected = selected }
                            .testTag("detail-rating-star-$value-touch"),
                    ) {
                        Box(
                            Modifier
                                .size(width = DetailRatingGlyphWidth, height = 24.dp)
                                .offset(x = DetailRatingGlyphEnvelopeOffset)
                                .testTag("detail-rating-star-$value-glyph"),
                        ) {
                            Icon(
                                imageVector = if (selected) TsuyomiIcons.Star else TsuyomiIcons.StarOutline,
                                contentDescription = stringResource(R.string.book_rating_description, value),
                                modifier = Modifier.size(24.dp).offset(x = DetailRatingIconOffset),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = TsuyomiSpacing.Xs),
                horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
            ) {
                DetailLibraryStateButton(
                    inLibrary = localState.inLibrary,
                    onAddToLibrary = onAddToLibrary,
                    modifier = Modifier.weight(1f),
                )
                DetailReadLaterStateButton(
                    selected = localState.readLater,
                    onToggle = onToggleReadLater,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DetailLibraryStateButton(
    inLibrary: Boolean,
    onAddToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = stringResource(if (inLibrary) R.string.book_in_library else R.string.book_not_in_library)
    Button(
        onClick = onAddToLibrary,
        enabled = !inLibrary,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                selected = inLibrary
                stateDescription = state
            }
            .testTag("detail-library-action"),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Sm),
    ) {
        Icon(TsuyomiIcons.Shelf, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(if (inLibrary) R.string.book_in_library else R.string.book_add_to_library),
            modifier = Modifier.padding(start = TsuyomiSpacing.Xs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (inLibrary) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun DetailReadLaterStateButton(
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = stringResource(
        if (selected) R.string.book_read_later_selected else R.string.book_read_later_unselected,
    )
    Button(
        onClick = onToggle,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                this.selected = selected
                stateDescription = state
            }
            .testTag("detail-read-later-action"),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Sm),
    ) {
        Icon(
            imageVector = if (selected) TsuyomiIcons.Bookmark else TsuyomiIcons.BookmarkOutline,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.book_read_later),
            modifier = Modifier.padding(start = TsuyomiSpacing.Xs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailTagActionsModule(
    tags: List<String>,
    enabled: Boolean,
    onAddTag: (String) -> Unit,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Xs)
                .testTag("detail-tag-module"),
            maxLines = 2,
            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
        ) {
            tags.forEach { tag -> DetailTagLabel(tag) }
            FilledTonalIconButton(
                onClick = { dialogOpen = true },
                enabled = enabled,
                modifier = Modifier.minimumInteractiveComponentSize().size(ButtonDefaults.MinHeight),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(
                    TsuyomiIcons.Add,
                    contentDescription = stringResource(R.string.book_add_tag),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(R.string.book_add_tag)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(64) },
                    label = { Text(stringResource(R.string.book_tag_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val tag = draft.trim()
                        if (tag.isNotEmpty()) onAddTag(tag)
                        draft = ""
                        dialogOpen = false
                    },
                    enabled = draft.isNotBlank(),
                ) { Text(stringResource(R.string.book_add_tag_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(R.string.book_cancel))
                }
            },
        )
    }
}

@Composable
private fun DetailTagLabel(text: String) {
    Box(Modifier.height(ButtonDefaults.MinHeight + TsuyomiSpacing.Sm), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(
                modifier = Modifier.height(ButtonDefaults.MinHeight).padding(horizontal = TsuyomiSpacing.Sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DetailIntroductionModule(description: String) {
    var expanded by rememberSaveable(description) { mutableStateOf(false) }
    var overflows by remember(description) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("detail-introduction-module")) {
        DetailModuleHeader(TsuyomiIcons.Info, stringResource(R.string.book_introduction))
        if (description.isNotBlank()) {
            val textModifier = Modifier.padding(
                start = TsuyomiSpacing.Md + 20.dp + TsuyomiSpacing.Sm,
                end = TsuyomiSpacing.Md,
            )
            if (expanded) {
                Text(
                    text = description,
                    modifier = textModifier.testTag("detail-introduction-text"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { expanded = false },
                    modifier = Modifier.padding(
                        start = TsuyomiSpacing.Md + 20.dp,
                        bottom = TsuyomiSpacing.Xs,
                    ).testTag("detail-introduction-collapse"),
                ) {
                    Text(stringResource(R.string.book_collapse_introduction))
                }
            } else {
                Box(modifier = textModifier.testTag("detail-introduction-preview")) {
                    Text(
                        text = description,
                        modifier = Modifier.fillMaxWidth().testTag("detail-introduction-text"),
                        maxLines = DETAIL_INTRODUCTION_PREVIEW_LINES,
                        overflow = TextOverflow.Clip,
                        onTextLayout = { result -> overflows = result.didOverflowHeight },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (overflows) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { expanded = true }
                                .testTag("detail-introduction-expand"),
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            Text(
                                text = "… ${stringResource(R.string.book_expand_introduction)}",
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(start = TsuyomiSpacing.Xs)
                                    .testTag("detail-introduction-expand-label"),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(TsuyomiSpacing.Sm))
            }
        }
    }
}

@Composable
private fun DetailVolumeHeader(
    title: String,
    chapterCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val state = stringResource(if (expanded) R.string.book_volume_expanded else R.string.book_volume_collapsed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .heightIn(min = 52.dp)
            .padding(start = TsuyomiSpacing.Md + 20.dp + TsuyomiSpacing.Sm, end = TsuyomiSpacing.Xs)
            .semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.book_chapter_count, chapterCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = TsuyomiIcons.Disclosure,
            contentDescription = null,
            modifier = Modifier.size(48.dp).padding(12.dp).rotate(if (expanded) 0f else -90f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailDirectoryHeader(
    totalChapters: Int,
    unreadOnly: Boolean,
    unreadFilterAvailable: Boolean,
    descending: Boolean,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
) {
    val filterStateDescription = stringResource(
        if (unreadOnly) R.string.book_unread_filter_active else R.string.book_unread_filter_all,
    )
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = TsuyomiSpacing.Md, end = TsuyomiSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            TsuyomiIcons.List,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.book_full_directory),
            modifier = Modifier.padding(start = TsuyomiSpacing.Sm).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.book_chapter_count, totalChapters),
            modifier = Modifier.padding(start = TsuyomiSpacing.Sm),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onToggleUnreadOnly,
            enabled = unreadFilterAvailable,
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                stateDescription = filterStateDescription
            },
            contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Xs),
        ) {
            Icon(TsuyomiIcons.Filter, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.book_unread_only), modifier = Modifier.padding(start = TsuyomiSpacing.Xs), maxLines = 1)
        }
        IconButton(onClick = onToggleOrder, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = TsuyomiIcons.Back,
                contentDescription = stringResource(
                    if (descending) R.string.book_order_descending else R.string.book_order_ascending,
                ),
                modifier = Modifier.rotate(if (descending) -90f else 90f),
            )
        }
    }
}

@Composable
private fun DetailModuleHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            title,
            modifier = Modifier.padding(start = TsuyomiSpacing.Sm).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun DetailChapterRow(item: DetailChapterItem, onSelectChapter: (SourceChapter) -> Unit) {
    val status = buildList {
        if (item.updated) add(stringResource(R.string.book_chapter_updated))
        item.read?.let { add(stringResource(if (it) R.string.book_chapter_read else R.string.book_chapter_unread)) }
        if (item.downloaded) add(stringResource(R.string.book_chapter_downloaded))
        if (item.current) add(stringResource(R.string.book_chapter_current))
    }.joinToString("，")
    Surface(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            if (status.isNotEmpty()) stateDescription = status
        },
        color = if (item.current) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button) { onSelectChapter(item.chapter) }
                .heightIn(min = 56.dp)
                .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailChapterMarker(item)
            Spacer(Modifier.size(TsuyomiSpacing.Sm))
            Text(
                item.chapter.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (item.read == true) FontWeight.Normal else FontWeight.Medium,
                ),
                color = if (item.read == true) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            if (item.downloaded) {
                Icon(
                    TsuyomiIcons.Downloaded,
                    contentDescription = stringResource(R.string.book_chapter_downloaded),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (item.current) {
                Surface(
                    modifier = Modifier.padding(start = TsuyomiSpacing.Xs),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        stringResource(R.string.book_current_badge),
                        modifier = Modifier.padding(horizontal = TsuyomiSpacing.Sm, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailChapterMarker(item: DetailChapterItem) {
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when {
            item.updated -> Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
            item.read == false -> Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            item.read == true -> Unit
            else -> Box(Modifier.size(8.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }
    }
}

@Composable
private fun DetailMutationBanner(status: DetailMutationStatus) {
    val text = mutationMessage(status)
    val color = when (status.phase) {
        DetailMutationPhase.WORKING -> MaterialTheme.colorScheme.secondaryContainer
        DetailMutationPhase.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        DetailMutationPhase.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    Surface(color = color, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm))
    }
}

@Composable
private fun mutationMessage(status: DetailMutationStatus): String {
    val operation = stringResource(
        when (status.operation) {
            DetailMutationOperation.ADD_TO_LIBRARY -> R.string.book_mutation_add
            DetailMutationOperation.REMOVE_FROM_LIBRARY -> R.string.book_mutation_remove
            DetailMutationOperation.CACHE_DETAIL -> R.string.book_mutation_cache
            DetailMutationOperation.REFRESH_DETAIL -> R.string.book_mutation_refresh
            DetailMutationOperation.SET_RATING -> R.string.book_mutation_rating
            DetailMutationOperation.ADD_TAG -> R.string.book_mutation_tag
            DetailMutationOperation.TOGGLE_READ_LATER -> R.string.book_mutation_read_later
        },
    )
    return when (status.phase) {
        DetailMutationPhase.WORKING -> stringResource(R.string.book_mutation_working, operation)
        DetailMutationPhase.SUCCESS -> stringResource(R.string.book_mutation_success, operation)
        DetailMutationPhase.ERROR -> stringResource(R.string.book_mutation_error, operation, status.safeCode.orEmpty())
    }
}

@Composable
internal fun DetailFailure(
    state: SourceBookState.Failure,
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
    modifier: Modifier,
) {
    val canVerify = state.code == SourceErrorCode.SESSION_REQUIRED ||
        state.code == SourceErrorCode.VERIFICATION_REQUIRED ||
        (
            state.code == SourceErrorCode.EXTENSION_RUNTIME_FAILURE &&
                state.diagnostic.stage.endsWith("-network") &&
                state.diagnostic.safeCode == "transport"
        )
    Column(
        modifier = modifier.padding(TsuyomiSpacing.Lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.book_source_failure))
        Text(stringResource(R.string.book_error_code, state.code.name), modifier = Modifier.padding(top = TsuyomiSpacing.Sm))
        Text(stringResource(R.string.book_diagnostic_id, state.diagnostic.correlationId), modifier = Modifier.padding(top = TsuyomiSpacing.Sm))
        Text(
            stringResource(R.string.book_diagnostic_stage, state.diagnostic.stage, state.diagnostic.safeCode),
            modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
        )
        TextButton(onClick = if (canVerify) onOpenVerification else onRetry) {
            Text(stringResource(if (canVerify) R.string.book_open_verification else R.string.book_retry))
        }
        if (!canVerify) {
            TextButton(onClick = onUseOfflineCache) { Text(stringResource(R.string.book_offline)) }
        }
    }
}
