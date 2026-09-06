/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.testTag
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.TsuyomiSplitButton
import org.tsuyomi.core.ui.components.TsuyomiOverflowAction
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiTopBarAction
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.theme.link
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceErrorCode

private const val DETAIL_INTRODUCTION_PREVIEW_LINES = 3
private val DetailRatingTargetSize = 36.dp
private val DetailRatingSlotWidth = 28.dp
private val DetailRatingGlyphSize = 20.dp
private val DetailTitleToggleTarget = 48.dp
private val DetailActionHeight = 48.dp

@Composable
fun BookDetailTopBar(
    title: String,
    inLibrary: Boolean,
    onNavigateUp: () -> Unit,
    onCacheDetail: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    remoteRemoveAvailable: Boolean = false,
    remoteMoveAvailable: Boolean = false,
    onRemoveFromRemote: () -> Unit = {},
    onMoveRemote: () -> Unit = {},
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
        if (remoteMoveAvailable) {
            add(TsuyomiOverflowAction(stringResource(R.string.book_move_remote), onMoveRemote))
        }
        if (remoteRemoveAvailable) {
            add(TsuyomiOverflowAction(stringResource(R.string.book_remove_from_remote), onRemoveFromRemote, destructive = true))
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
internal fun DetailIdentityModule(
    detail: SourceBookDetail,
    coverState: CoverUiState,
    localState: DetailLocalState,
    onSetRating: (Int?) -> Unit,
    onSearchAuthor: (String) -> Unit,
    onAddToLibrary: () -> Unit,
    onOpenDestinations: () -> Unit,
    destinationMenuExpanded: Boolean,
    onDestinationMenuExpandedChange: (Boolean) -> Unit,
    destinationMenuContent: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxWidth()
            .padding(start = TsuyomiSpacing.Md, top = TsuyomiSpacing.Md, end = TsuyomiSpacing.Md, bottom = TsuyomiSpacing.Xs)
            .testTag("detail-identity-module"),
        content = {
            CoverImage(
                state = coverState,
                modifier = Modifier.testTag("detail-cover"),
                unresolvedBadge = localState.reconciliation == "UNRESOLVED",
            )
            Box(Modifier.fillMaxWidth().testTag("detail-title-block")) {
                DetailTitle(detail.summary.title)
            }
            DetailAuthor(detail.summary.author, onSearchAuthor)
            DetailSourceMetadata(detail.status, detail.lastUpdatedDate)
            DetailRatingControl(localState, onSetRating)
            DetailLibraryStateButton(
                inLibrary = localState.inLibrary,
                onAddToLibrary = onAddToLibrary,
                onOpenDestinations = onOpenDestinations,
                destinationMenuExpanded = destinationMenuExpanded,
                onDestinationMenuExpandedChange = onDestinationMenuExpandedChange,
                destinationMenuContent = destinationMenuContent,
            )
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val cover = measurables[0].measure(Constraints.fixed(135.dp.roundToPx(), 180.dp.roundToPx()))
        val textX = cover.width + TsuyomiSpacing.Md.roundToPx()
        val textWidth = (width - textX).coerceAtLeast(0)
        val title = measurables[1].measure(Constraints.fixedWidth(textWidth))
        val author = measurables[2].measure(Constraints.fixedWidth(textWidth))
        val metadata = measurables[3].measure(Constraints.fixedWidth(textWidth))
        val horizontalGap = TsuyomiSpacing.Xs.roundToPx()
        val fallbackGap = TsuyomiSpacing.Sm.roundToPx()
        val ratingFrameHeight = DetailRatingTargetSize.roundToPx()
        val splitHeight = DetailActionHeight.roundToPx()
        val ratingMinimum = measurables[4].maxIntrinsicWidth(ratingFrameHeight)
        val splitMinimum = measurables[5].maxIntrinsicWidth(splitHeight)
        val sideContentHeight = title.height + author.height + metadata.height + ratingFrameHeight + splitHeight
        val sidePlacement = textWidth >= maxOf(ratingMinimum, splitMinimum) && sideContentHeight <= cover.height
        val fallbackHorizontal = !sidePlacement && width >= ratingMinimum + horizontalGap + splitMinimum
        val ratingWidth = if (sidePlacement || fallbackHorizontal) ratingMinimum else width
        val splitWidth = if (sidePlacement || fallbackHorizontal) splitMinimum else width
        val rating = measurables[4].measure(Constraints.fixed(ratingWidth, ratingFrameHeight))
        val split = measurables[5].measure(Constraints.fixed(splitWidth, splitHeight))

        if (sidePlacement) {
            val blocks = listOf(1 to title, 2 to author, 3 to metadata, 4 to rating, 5 to split)
                .filter { (_, placeable) -> placeable.height > 0 }
            val remainingHeight = cover.height - blocks.sumOf { (_, placeable) -> placeable.height }
            val gapCount = (blocks.size - 1).coerceAtLeast(1)
            val baseGap = remainingHeight / gapCount
            val extraGapCount = remainingHeight % gapCount
            val blockY = IntArray(6)
            var y = 0
            blocks.forEachIndexed { index, (slot, placeable) ->
                blockY[slot] = y
                y += placeable.height
                if (index < blocks.lastIndex) y += baseGap + if (index < extraGapCount) 1 else 0
            }
            layout(width, cover.height) {
                cover.placeRelative(0, 0)
                title.placeRelative(textX, blockY[1])
                author.placeRelative(textX, blockY[2])
                metadata.placeRelative(textX, blockY[3])
                rating.placeRelative(textX, blockY[4])
                split.placeRelative(textX, blockY[5])
            }
        } else {
            val titleY = 0
            val authorY = titleY + title.height
            val metadataY = authorY + author.height
            val identityBottom = metadataY + metadata.height
            val actionY = maxOf(cover.height, identityBottom) + fallbackGap
            val ratingX = 0
            val splitX = if (fallbackHorizontal) rating.width + horizontalGap else 0
            val ratingY = if (fallbackHorizontal) actionY + (split.height - rating.height) / 2 else actionY
            val splitY = if (fallbackHorizontal) actionY else actionY + rating.height + fallbackGap
            layout(width, maxOf(cover.height, identityBottom, ratingY + rating.height, splitY + split.height)) {
                cover.placeRelative(0, 0)
                title.placeRelative(textX, titleY)
                author.placeRelative(textX, authorY)
                metadata.placeRelative(textX, metadataY)
                rating.placeRelative(ratingX, ratingY)
                split.placeRelative(splitX, splitY)
            }
        }
    }
}

@Composable
private fun DetailAuthor(author: String?, onSearchAuthor: (String) -> Unit) {
    val metadataStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)
    val metadataLineHeight = with(LocalDensity.current) { metadataStyle.lineHeight.toDp() }
    Box(Modifier.fillMaxWidth().testTag("detail-author-row")) {
        author?.takeIf(String::isNotBlank)?.let { value ->
            val linkStyles = TextLinkStyles(
                style = SpanStyle(color = MaterialTheme.colorScheme.link),
            )
            val searchLabel = stringResource(R.string.book_search_author, value)
            Text(
                text = buildAnnotatedString {
                    withLink(LinkAnnotation.Clickable(searchLabel, linkStyles) { onSearchAuthor(value) }) {
                        append(value)
                    }
                },
                modifier = Modifier.heightIn(min = metadataLineHeight).testTag("detail-author"),
                style = metadataStyle,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailSourceMetadata(status: String?, lastUpdatedDate: String?) {
    val metadataStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)
    val metadataLineHeight = with(LocalDensity.current) { metadataStyle.lineHeight.toDp() }
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("detail-metadata-row"),
        horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
    ) {
        status?.trim()?.takeIf(String::isNotEmpty)?.let { value ->
            Text(
                text = value,
                modifier = Modifier.heightIn(min = metadataLineHeight).testTag("detail-publication-status"),
                style = metadataStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        lastUpdatedDate?.let { date ->
            Text(
                text = stringResource(R.string.book_last_updated, date),
                modifier = Modifier.heightIn(min = metadataLineHeight).testTag("detail-last-updated"),
                style = metadataStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailTitle(titleText: String) {
    var expanded by rememberSaveable(titleText) { mutableStateOf(false) }
    var collapsedOverflows by remember(titleText) { mutableStateOf(false) }
    val showToggle = collapsedOverflows || expanded
    val titleStyle = MaterialTheme.typography.titleLarge.copy(lineHeight = 27.sp)
    val actionLabel = stringResource(if (expanded) R.string.book_collapse_full_title else R.string.book_expand_full_title)
    val expansionState = stringResource(if (expanded) R.string.book_title_expanded else R.string.book_title_collapsed)
    val iconSize = with(LocalDensity.current) { titleStyle.fontSize.toDp() }
    Box(Modifier.fillMaxWidth()) {
        Text(
            text = titleText,
            modifier = Modifier.fillMaxWidth()
                .padding(end = if (showToggle) DetailTitleToggleTarget else 0.dp)
                .testTag("detail-title-flow"),
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) collapsedOverflows = result.hasVisualOverflow
            },
            style = titleStyle,
        )
        if (showToggle) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd)
                    .size(DetailTitleToggleTarget)
                    .semantics { stateDescription = expansionState }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .testTag("detail-title-overflow"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TsuyomiIcons.Info,
                    contentDescription = actionLabel,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
private fun DetailRatingControl(localState: DetailLocalState, onSetRating: (Int?) -> Unit) {
    Row(
        modifier = Modifier.testTag("detail-rating-row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.height(DetailRatingTargetSize)
                .padding(end = TsuyomiSpacing.Xs)
                .offset(x = -TsuyomiSpacing.Xs)
                .testTag("detail-rating-band"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(5) { index ->
                val value = index + 1
                val selected = value <= (localState.rating ?: 0)
                IconButton(
                    onClick = { onSetRating(if (localState.rating == value) null else value) },
                    enabled = localState.inLibrary,
                    modifier = Modifier.width(DetailRatingSlotWidth).height(DetailRatingTargetSize)
                        .semantics { this.selected = selected }
                        .testTag("detail-rating-star-$value-touch"),
                ) {
                    Icon(
                        imageVector = if (selected) TsuyomiIcons.Star else TsuyomiIcons.StarOutline,
                        contentDescription = stringResource(R.string.book_rating_description, value),
                        modifier = Modifier.size(DetailRatingGlyphSize).testTag("detail-rating-star-$value-glyph"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DetailLibraryStateButton(
    inLibrary: Boolean,
    onAddToLibrary: () -> Unit,
    onOpenDestinations: () -> Unit,
    destinationMenuExpanded: Boolean,
    onDestinationMenuExpandedChange: (Boolean) -> Unit,
    destinationMenuContent: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = stringResource(if (inLibrary) R.string.book_in_library else R.string.book_not_in_library)
    TsuyomiSplitButton(
        text = stringResource(if (inLibrary) R.string.book_in_library else R.string.book_add_to_library),
        leadingIcon = TsuyomiIcons.Shelf,
        trailingIcon = TsuyomiIcons.Disclosure,
        trailingDescription = "更多加入选项",
        onLeadingClick = onAddToLibrary,
        onMenuOpen = onOpenDestinations,
        menuExpanded = destinationMenuExpanded,
        onMenuExpandedChange = onDestinationMenuExpandedChange,
        leadingEnabled = !inLibrary,
        modifier = modifier
            .semantics {
                selected = inLibrary
                stateDescription = state
            }
            .testTag("detail-library-action"),
        menuContent = destinationMenuContent,
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailTagActionsModule(
    tags: List<String>,
    enabled: Boolean,
    onAddTag: (String) -> Unit,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs).testTag("detail-tag-surface"),
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
internal fun DetailTagLabel(text: String) {
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
internal fun DetailIntroductionModule(description: String) {
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
internal fun DetailMutationBanner(status: DetailMutationStatus) {
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
internal fun mutationMessage(status: DetailMutationStatus): String {
    val operation = when (status.operation) {
        DetailMutationOperation.ADD_TO_LIBRARY -> stringResource(R.string.book_mutation_add)
        DetailMutationOperation.REMOVE_FROM_LIBRARY -> stringResource(R.string.book_mutation_remove)
        DetailMutationOperation.CACHE_DETAIL -> stringResource(R.string.book_mutation_cache)
        DetailMutationOperation.REFRESH_DETAIL -> stringResource(R.string.book_mutation_refresh)
        DetailMutationOperation.SET_RATING -> stringResource(R.string.book_mutation_rating)
        DetailMutationOperation.ADD_TAG -> stringResource(R.string.book_mutation_tag)
        DetailMutationOperation.TOGGLE_READ_LATER -> stringResource(R.string.book_mutation_read_later)
        DetailMutationOperation.REMOVE_FROM_REMOTE -> "从远程书架移除"
        DetailMutationOperation.MOVE_REMOTE -> "移动远程分类"
        DetailMutationOperation.RECONCILE_RETRY -> "重试远端同步"
        DetailMutationOperation.RECONCILE_ACKNOWLEDGE -> "解除锁定状态"
    }
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
