/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiDialog
import org.tsuyomi.core.ui.components.TsuyomiTextField
import org.tsuyomi.core.ui.components.TsuyomiIconButton
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
    updateChecksExcluded: Boolean = false,
    onToggleUpdateChecksExcluded: () -> Unit = {},
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
        add(
            TsuyomiOverflowAction(
                label = stringResource(
                    if (updateChecksExcluded) R.string.book_resume_update_checks else R.string.book_stop_update_checks,
                ),
                onClick = onToggleUpdateChecksExcluded,
                icon = TsuyomiIcons.Updates,
            ),
        )
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
    onRequestRemoveFromLibrary: () -> Unit,
    primaryActionEnabled: Boolean,
    onOpenDestinations: () -> Unit,
    destinationMenuExpanded: Boolean,
    onDestinationMenuExpandedChange: (Boolean) -> Unit,
    destinationMenuContent: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit,
) {
    var titleExpanded by rememberSaveable(detail.summary.identity, detail.summary.title) { mutableStateOf(false) }
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
                DetailTitle(detail.summary.title, titleExpanded) { titleExpanded = !titleExpanded }
            }
            DetailAuthor(detail.summary.author, onSearchAuthor)
            DetailSourceMetadata(detail.status, detail.lastUpdatedDate)
            DetailRatingControl(localState, onSetRating)
            DetailLibraryStateButton(
                inLibrary = localState.inLibrary,
                onAddToLibrary = onAddToLibrary,
                onRequestRemoveFromLibrary = onRequestRemoveFromLibrary,
                primaryActionEnabled = primaryActionEnabled,
                onOpenDestinations = onOpenDestinations,
                destinationMenuExpanded = destinationMenuExpanded,
                onDestinationMenuExpandedChange = onDestinationMenuExpandedChange,
                destinationMenuContent = destinationMenuContent,
            )
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val cover = measurables[0].measure(Constraints.fixed(135.dp.roundToPx(), 189.dp.roundToPx()))
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
        val sidePlacement = textWidth >= maxOf(ratingMinimum, splitMinimum) &&
            (sideContentHeight <= cover.height || (!titleExpanded && fontScale <= 1f))
        val fallbackHorizontal = !sidePlacement && width >= ratingMinimum + horizontalGap + splitMinimum
        val ratingWidth = if (sidePlacement || fallbackHorizontal) ratingMinimum else width
        val splitWidth = splitMinimum.coerceAtMost(width)
        val rating = measurables[4].measure(Constraints.fixed(ratingWidth, ratingFrameHeight))
        val split = measurables[5].measure(Constraints.fixed(splitWidth, splitHeight))

        if (sidePlacement) {
            val columnHeight = maxOf(cover.height, sideContentHeight)
            val blocks = listOf(1 to title, 2 to author, 3 to metadata, 4 to rating, 5 to split)
                .filter { (_, placeable) -> placeable.height > 0 }
            val remainingHeight = columnHeight - blocks.sumOf { (_, placeable) -> placeable.height }
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
            layout(width, columnHeight) {
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
private fun DetailTitle(titleText: String, expanded: Boolean, onToggleExpanded: () -> Unit) {
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
                    .clickable(role = Role.Button, onClick = onToggleExpanded)
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
                TsuyomiIconButton(
                    imageVector = if (selected) TsuyomiIcons.Star else TsuyomiIcons.StarOutline,
                    contentDescription = stringResource(R.string.book_rating_description, value),
                    onClick = { onSetRating(if (localState.rating == value) null else value) },
                    enabled = localState.inLibrary,
                    iconModifier = Modifier.size(DetailRatingGlyphSize).testTag("detail-rating-star-$value-glyph"),
                    modifier = Modifier.width(DetailRatingSlotWidth).height(DetailRatingTargetSize)
                        .semantics { this.selected = selected }
                        .testTag("detail-rating-star-$value-touch"),
                )
            }
        }
    }
}

@Composable
internal fun DetailLibraryStateButton(
    inLibrary: Boolean,
    onAddToLibrary: () -> Unit,
    onRequestRemoveFromLibrary: () -> Unit,
    onOpenDestinations: () -> Unit,
    primaryActionEnabled: Boolean,
    destinationMenuExpanded: Boolean,
    onDestinationMenuExpandedChange: (Boolean) -> Unit,
    destinationMenuContent: @Composable ColumnScope.(dismissMenu: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = stringResource(if (inLibrary) R.string.book_in_library else R.string.book_not_in_library)
    TsuyomiSplitButton(
        text = stringResource(if (inLibrary) R.string.book_in_library else R.string.book_add_to_library),
        trailingIcon = TsuyomiIcons.Disclosure,
        trailingDescription = "更多加入选项",
        onLeadingClick = if (inLibrary) onRequestRemoveFromLibrary else onAddToLibrary,
        onMenuOpen = onOpenDestinations,
        menuExpanded = destinationMenuExpanded,
        onMenuExpandedChange = onDestinationMenuExpandedChange,
        leadingEnabled = primaryActionEnabled,
        modifier = modifier
            .semantics {
                selected = inLibrary
                stateDescription = state
            }
            .testTag("detail-library-action"),
        menuContent = destinationMenuContent,
    )
}


@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DetailTagActionsModule(
    tags: List<String>,
    enabled: Boolean,
    mutation: DetailMutationStatus? = null,
    tagEditorOpen: Boolean,
    tagDraft: String,
    onOpenTagEditor: () -> Unit,
    onTagDraftChange: (String) -> Unit,
    onDismissTagEditor: () -> Unit,
    onConfirmTag: () -> Unit,
) {
    val addTagMutation = mutation?.takeIf { it.operation == DetailMutationOperation.ADD_TAG }
    val addTagWorking = addTagMutation?.phase == DetailMutationPhase.WORKING
    val addTagError = addTagMutation?.takeIf { it.phase == DetailMutationPhase.ERROR }

    val addTagLabel = stringResource(R.string.book_add_tag)
    val addGlyphSize = 16.dp
    val legendBackground = MaterialTheme.colorScheme.background
    val legendColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Xs)
            .testTag("detail-tag-surface"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .border(1.dp, legendColor, MaterialTheme.shapes.small)
                .testTag("detail-tag-region")
                .padding(
                    start = TsuyomiSpacing.Sm,
                    top = TsuyomiSpacing.Sm + TsuyomiSpacing.Xs,
                    end = TsuyomiSpacing.Sm,
                    bottom = TsuyomiSpacing.Xs,
                ),
        ) {
            JustifiedTagFlow(
                modifier = Modifier.fillMaxWidth().testTag("detail-tag-module"),
                minHorizontalGap = TsuyomiSpacing.Xs,
                verticalGap = TsuyomiSpacing.Xs,
            ) {
                tags.forEach { tag -> DetailTagLabel(tag) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("detail-tag-header"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(start = TsuyomiSpacing.Sm + TsuyomiSpacing.Xs)
                    .background(legendBackground)
                    .padding(horizontal = TsuyomiSpacing.Xs),
            ) {
                Text(
                    text = stringResource(R.string.book_tags),
                    modifier = Modifier.testTag("detail-tag-title"),
                    style = MaterialTheme.typography.labelLarge,
                    color = legendColor,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 24.dp, height = 20.dp)
                        .background(legendBackground)
                        .testTag("detail-add-tag-gap"),
                )
                TsuyomiIconButton(
                    imageVector = TsuyomiIcons.Add,
                    contentDescription = addTagLabel,
                    onClick = onOpenTagEditor,
                    enabled = enabled && !addTagWorking,
                    iconModifier = Modifier
                        .size(addGlyphSize)
                        .testTag("detail-add-tag-glyph"),
                    modifier = Modifier.testTag("detail-add-tag"),
                    iconTint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (tagEditorOpen) {
        TsuyomiDialog(
            onDismissRequest = { if (!addTagWorking) onDismissTagEditor() },
            title = stringResource(R.string.book_add_tag),
            body = {
                TsuyomiTextField(
                    value = tagDraft,
                    onValueChange = { onTagDraftChange(it.take(64)) },
                    label = stringResource(R.string.book_tag_name),
                    enabled = !addTagWorking,
                    isError = addTagError != null,
                    supportingText = addTagError?.let { mutationMessage(it) },
                    singleLine = true,
                    modifier = Modifier.testTag("detail-add-tag-input"),
                )
            },
            confirmLabel = stringResource(R.string.book_add_tag_confirm),
            onConfirm = onConfirmTag,
            confirmEnabled = tagDraft.isNotBlank() && !addTagWorking,
            dismissLabel = stringResource(R.string.book_cancel),
        )
    }
}

/**
 * Resolves one horizontal gap for the whole tag block, then places every row with it.
 *
 * The block is packed at the minimum gap to find the smallest achievable row count.
 * A row can then be *distributed* across the full inner width only while it holds
 * at least two tags and a gap no wider than its own narrowest chip remains
 * available, so the cap is derived from that narrowest chip rather than fixed:
 * a gap wider than a whole chip reads as two detached columns instead of one tag
 * group. Within that bound the largest gap preserving the row count is chosen, so
 * the tags are as far apart as they can be without pushing one onto a further row.
 *
 * Rows that can be distributed use the resolved gap and reach both inner edges;
 * rows that cannot (a single tag, or too little remaining width) keep a compact
 * default gap and stay on the leading edge. The whole block shares one gap
 * whenever it can be distributed, so adjacent rows never show different spacing.
 */
@Composable
private fun JustifiedTagFlow(
    minHorizontalGap: androidx.compose.ui.unit.Dp,
    verticalGap: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val minGapPx = with(LocalDensity.current) { minHorizontalGap.roundToPx() }
    val vGapPx = with(LocalDensity.current) { verticalGap.roundToPx() }
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }

        fun pack(gap: Int): List<List<androidx.compose.ui.layout.Placeable>> {
            val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
            var rowWidth = 0
            placeables.forEach { placeable ->
                val needed = if (rows.isEmpty() || rows.last().isEmpty()) placeable.width else placeable.width + gap
                if (rows.isEmpty() || rowWidth + needed > constraints.maxWidth) {
                    rows += mutableListOf(placeable)
                    rowWidth = placeable.width
                } else {
                    rows.last() += placeable
                    rowWidth += needed
                }
            }
            return rows
        }

        // Rows must be packed at the very gap they will be placed with. Packing at a
        // smaller gap than the one used for placement lets a row that only just fitted
        // overflow once the wider gap is applied, and because the wrap decision was
        // already taken at the smaller gap the row never wraps: the tags run past the
        // region edge. minHorizontalGap is the smallest spacing any multi-tag row shows,
        // so it is both the packing gap and the gap a non-justified row keeps. Using a
        // larger default gap here would silently reduce how many tags fit per row - the
        // row capacity and the default spacing are the same decision, not two.
        val rows = pack(minGapPx)
        // Packing starts a new row only when the next tag no longer fits, so every row but
        // the last is full by construction. Those full rows share ONE justified gap: the
        // smallest capacity any of them can absorb, so none overflows and none drifts
        // against its neighbours. Two rows sharing a start edge diverge by the gap
        // difference at every tag, which is visible by the fourth tag, so a per-row gap is
        // not an option. When the full rows have equal content width - identical tags
        // wrapping - that shared gap also fills each of them to the trailing edge, so
        // justification and column alignment hold together rather than competing.
        val justifiedGap = rows.dropLast(1)
            .filter { it.size > 1 }
            .minOfOrNull { row -> (constraints.maxWidth - row.sumOf { it.width }) / (row.size - 1) }
            ?.coerceAtLeast(minGapPx)
        // The trailing row lines up with the justified rows when it can afford their gap.
        // When nothing justified - or it cannot afford the shared gap - it keeps the
        // minimum gap, which is what a sparse block should show.
        val trailingRow = rows.lastOrNull()
        val trailingAdoptsJustified = justifiedGap != null && trailingRow != null &&
            trailingRow.size > 1 &&
            (constraints.maxWidth - trailingRow.sumOf { it.width }) / (trailingRow.size - 1) >=
            justifiedGap
        // Reached only when the block has more than one row; a single row never justifies.
        val fullRowGap = justifiedGap ?: minGapPx
        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        val totalHeight = rowHeights.sum() + vGapPx * (rows.size - 1).coerceAtLeast(0)
        // An empty tag list produces no rows; the block then contributes only padding,
        // and an inverted constraint range must not throw during measure.
        val height = totalHeight.coerceAtLeast(constraints.minHeight).coerceAtMost(constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                val gap = when {
                    // A lone tag occupies its row alone, so there is no gap to place.
                    row.size <= 1 -> 0
                    rowIndex < rows.lastIndex -> fullRowGap
                    trailingAdoptsJustified -> justifiedGap ?: minGapPx
                    else -> minGapPx
                }
                var x = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y + (rowHeights[rowIndex] - placeable.height) / 2)
                    x += placeable.width + gap
                }
                y += rowHeights[rowIndex] + vGapPx
            }
        }
    }
}

@Composable
internal fun DetailTagLabel(text: String) {
    Box(
        modifier = Modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.heightIn(min = 40.dp).testTag("detail-tag-label"),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = TsuyomiSpacing.Sm),
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
                TsuyomiButton(
                    text = stringResource(R.string.book_collapse_introduction),
                    onClick = { expanded = false },
                    modifier = Modifier
                        .padding(
                            start = TsuyomiSpacing.Md + 20.dp,
                            bottom = TsuyomiSpacing.Xs,
                        )
                        .testTag("detail-introduction-collapse"),
                    style = TsuyomiButtonStyle.TEXT,
                )
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
        TsuyomiButton(
            text = stringResource(if (canVerify) R.string.book_open_verification else R.string.book_retry),
            onClick = if (canVerify) onOpenVerification else onRetry,
            style = TsuyomiButtonStyle.TEXT,
        )
        if (!canVerify) {
            TsuyomiButton(
                text = stringResource(R.string.book_offline),
                onClick = onUseOfflineCache,
                style = TsuyomiButtonStyle.TEXT,
            )
        }
    }
}
