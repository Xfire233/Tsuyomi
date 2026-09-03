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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.platform.testTag
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.ui.components.CoverImage
import org.tsuyomi.core.ui.components.TsuyomiOverflowAction
import org.tsuyomi.core.ui.components.TsuyomiStatusBadge
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.core.ui.components.TsuyomiTopBarAction
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceErrorCode

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
internal fun DetailIdentityModule(
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
internal fun DetailLibraryStateButton(
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
internal fun DetailReadLaterStateButton(
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
internal fun DetailTagActionsModule(
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
