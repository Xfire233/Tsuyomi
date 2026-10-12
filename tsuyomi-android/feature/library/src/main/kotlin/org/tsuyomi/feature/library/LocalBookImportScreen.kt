/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiCheckboxRow
import org.tsuyomi.core.ui.components.TsuyomiChoiceMenu
import org.tsuyomi.core.ui.components.TsuyomiChoiceOption
import org.tsuyomi.core.ui.components.TsuyomiTextField
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

/** Provider access and parsing remain in the host. This state contains presentation data only. */
data class LocalBookImportUiState(
    val busy: Boolean = false,
    val status: String? = null,
    val fileName: String? = null,
    val review: LocalBookImportReview? = null,
    val results: List<LocalBookImportResult> = emptyList(),
)

data class LocalBookImportResult(
    val resultId: String,
    val fileName: String,
    val message: String,
    val publicationId: String? = null,
)

data class LocalBookImportMatch(val publicationId: String, val title: String, val originalAvailable: Boolean)
data class LocalBookImportEncoding(val charsetName: String, val preview: String)
data class LocalBookImportHeading(val paragraphOrdinal: Int, val title: String, val selected: Boolean = true)

data class LocalBookImportReview(
    val title: String,
    val format: String,
    val compatibility: List<String> = emptyList(),
    val matches: List<LocalBookImportMatch> = emptyList(),
    val selectedPublicationId: String? = null,
    val independentCopy: Boolean = false,
    val encodings: List<LocalBookImportEncoding> = emptyList(),
    val selectedCharset: String? = null,
    val headings: List<LocalBookImportHeading> = emptyList(),
    val newHeadingParagraph: String = "",
    val newHeadingTitle: String = "",
    val paragraphsPerSection: String = "128",
    val error: String? = null,
    val ready: Boolean = false,
)

sealed interface LocalBookImportAction {
    data object ChooseFiles : LocalBookImportAction
    data object Cancel : LocalBookImportAction
    data object Confirm : LocalBookImportAction
    data object AddHeading : LocalBookImportAction
    data class OpenBook(val publicationId: String) : LocalBookImportAction
    data class ChooseAssociation(val publicationId: String?) : LocalBookImportAction
    data class ChooseCharset(val charsetName: String) : LocalBookImportAction
    data class EditHeading(val paragraphOrdinal: Int, val title: String, val selected: Boolean) : LocalBookImportAction
    data class EditNewHeadingParagraph(val value: String) : LocalBookImportAction
    data class EditNewHeadingTitle(val value: String) : LocalBookImportAction
    data class EditParagraphsPerSection(val value: String) : LocalBookImportAction
}

@Composable
fun LocalBookImportScreen(
    state: LocalBookImportUiState,
    onAction: (LocalBookImportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val review = state.review
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        LocalBookImportMeasuredScreen(state, onAction, modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(TsuyomiSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
    ) {
        if (state.status != null) {
            item(key = "status") {
                Text(
                    state.status,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        state.fileName?.let { name ->
            item(key = "file") { Text(name, style = MaterialTheme.typography.titleMedium) }
        }
        if (!state.busy && review == null) {
            item(key = "choose") {
                TsuyomiButton(
                    text = stringResource(R.string.local_book_choose_files),
                    onClick = { onAction(LocalBookImportAction.ChooseFiles) },
                )
            }
        }
        if (state.busy || review != null) {
            item(key = "cancel") {
                TsuyomiButton(
                    text = stringResource(R.string.local_book_cancel_current),
                    style = TsuyomiButtonStyle.TEXT,
                    onClick = { onAction(LocalBookImportAction.Cancel) },
                )
            }
        }
        if (review != null) {
            item(key = "identity") {
                Text(review.title, style = MaterialTheme.typography.titleMedium)
                Text(review.format, style = MaterialTheme.typography.bodyMedium)
            }
            items(review.compatibility.indices.toList(), key = { "compatibility-$it" }) { index ->
                Text(review.compatibility[index], style = MaterialTheme.typography.bodyMedium)
            }
            if (review.matches.isNotEmpty()) {
                item(key = "duplicate-scope") {
                    Text(stringResource(R.string.local_book_duplicate_scope), style = MaterialTheme.typography.bodyMedium)
                }
                items(review.matches, key = { "match-${it.publicationId}" }) { match ->
                    TsuyomiCheckboxRow(
                        label = if (match.originalAvailable) match.title else stringResource(R.string.local_book_missing_match, match.title),
                        checked = review.selectedPublicationId == match.publicationId,
                        onCheckedChange = { onAction(LocalBookImportAction.ChooseAssociation(match.publicationId)) },
                        enabled = !state.busy,
                    )
                }
                item(key = "independent") {
                    TsuyomiCheckboxRow(
                        label = stringResource(R.string.local_book_independent_copy),
                        checked = review.independentCopy,
                        onCheckedChange = { onAction(LocalBookImportAction.ChooseAssociation(null)) },
                        enabled = !state.busy,
                    )
                }
            }
            if (review.encodings.isNotEmpty()) {
                item(key = "encoding") {
                    TsuyomiChoiceMenu(
                        label = stringResource(R.string.local_book_encoding),
                        options = listOf(TsuyomiChoiceOption("", stringResource(R.string.local_book_choose_encoding))) +
                            review.encodings.map { TsuyomiChoiceOption(it.charsetName, it.charsetName) },
                        selectedKey = review.selectedCharset.orEmpty(),
                        onSelect = { if (it.isNotEmpty()) onAction(LocalBookImportAction.ChooseCharset(it)) },
                    )
                }
                review.encodings.firstOrNull { it.charsetName == review.selectedCharset }?.let { encoding ->
                    item(key = "preview") { Text(encoding.preview, style = MaterialTheme.typography.bodyLarge) }
                }
                items(review.headings, key = { "heading-${it.paragraphOrdinal}" }) { heading ->
                    TsuyomiCheckboxRow(
                        label = stringResource(R.string.local_book_heading_paragraph, heading.paragraphOrdinal),
                        checked = heading.selected,
                        onCheckedChange = { onAction(LocalBookImportAction.EditHeading(heading.paragraphOrdinal, heading.title, it)) },
                        enabled = !state.busy,
                    )
                    TsuyomiTextField(
                        value = heading.title,
                        label = stringResource(R.string.local_book_heading_title),
                        onValueChange = { onAction(LocalBookImportAction.EditHeading(heading.paragraphOrdinal, it, heading.selected)) },
                        enabled = !state.busy,
                    )
                }
                item(key = "new-heading") {
                    TsuyomiTextField(
                        value = review.newHeadingParagraph,
                        label = stringResource(R.string.local_book_heading_ordinal),
                        onValueChange = { onAction(LocalBookImportAction.EditNewHeadingParagraph(it)) },
                        singleLine = true,
                        enabled = !state.busy,
                    )
                    TsuyomiTextField(
                        value = review.newHeadingTitle,
                        label = stringResource(R.string.local_book_heading_title),
                        onValueChange = { onAction(LocalBookImportAction.EditNewHeadingTitle(it)) },
                        singleLine = true,
                        enabled = !state.busy,
                    )
                    TsuyomiButton(
                        text = stringResource(R.string.local_book_add_heading),
                        style = TsuyomiButtonStyle.TEXT,
                        onClick = { onAction(LocalBookImportAction.AddHeading) },
                        enabled = !state.busy,
                    )
                    TsuyomiTextField(
                        value = review.paragraphsPerSection,
                        label = stringResource(R.string.local_book_segment_paragraphs),
                        supportingText = stringResource(R.string.local_book_segment_scope),
                        onValueChange = { onAction(LocalBookImportAction.EditParagraphsPerSection(it)) },
                        singleLine = true,
                        enabled = !state.busy,
                    )
                }
            }
            review.error?.let { error ->
                item(key = "review-error") { Text(error, style = MaterialTheme.typography.bodyMedium) }
            }
            item(key = "confirm") {
                TsuyomiButton(
                    text = stringResource(R.string.local_book_confirm_import),
                    onClick = { onAction(LocalBookImportAction.Confirm) },
                    enabled = !state.busy && review.ready,
                )
            }
        }
        items(state.results, key = { "result-${it.resultId}" }) { result ->
            Text(result.fileName, style = MaterialTheme.typography.titleSmall)
            Text(result.message, style = MaterialTheme.typography.bodyMedium)
            result.publicationId?.let { publicationId ->
                TsuyomiButton(
                    text = stringResource(R.string.local_book_open_detail),
                    style = TsuyomiButtonStyle.TEXT,
                    modifier = Modifier.testTag("local-book-import-result-${result.resultId}"),
                    onClick = { onAction(LocalBookImportAction.OpenBook(publicationId)) },
                )
            }
        }
    }
}

private data class LocalBookImportPageItem(
    val key: String,
    val content: @Composable () -> Unit,
)

@Composable
private fun AppendImportTextLines(
    items: MutableList<LocalBookImportPageItem>,
    key: String,
    value: String,
    maxWidth: androidx.compose.ui.unit.Dp,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
) {
    items += LocalBookImportPageItem(key) {
        TsuyomiMeasuredTextPager(value, style = style, fillViewport = false, alwaysShowPageControls = false)
    }
}

private fun appendImportCheckboxLines(
    items: MutableList<LocalBookImportPageItem>,
    key: String,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    onCheckedChange: () -> Unit,
) {
    items += LocalBookImportPageItem(key) {
        TsuyomiCheckboxRow(label = label, checked = checked, enabled = enabled, onCheckedChange = { onCheckedChange() })
    }
}

private fun appendImportButton(
    items: MutableList<LocalBookImportPageItem>,
    key: String,
    label: String,
    action: () -> Unit,
    style: TsuyomiButtonStyle = TsuyomiButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
    items += LocalBookImportPageItem(key) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TsuyomiButton(
                text = label,
                onClick = action,
                style = style,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun LocalBookImportMeasuredScreen(
    state: LocalBookImportUiState,
    onAction: (LocalBookImportAction) -> Unit,
    modifier: Modifier,
) {
    val review = state.review
    BoxWithConstraints(modifier.fillMaxSize()) {
        // A new review must not inherit the completed-results anchor; edits keep its page history.
        val pageState = androidx.compose.runtime.key(review != null) {
            rememberTsuyomiMeasuredPagerState()
        }
        val pageItems = mutableListOf<LocalBookImportPageItem>()
        val textWidth = (maxWidth - 16.dp).coerceAtLeast(1.dp)

        state.status?.let { status ->
            pageItems += LocalBookImportPageItem("status") {
                TsuyomiMeasuredTextPager(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fillViewport = false, alwaysShowPageControls = false)
            }
        }
        state.fileName?.let { name ->
            androidx.compose.runtime.key("file") {
                AppendImportTextLines(pageItems, "file", name, textWidth, MaterialTheme.typography.titleMedium)
            }
        }
        if (review != null) {
            androidx.compose.runtime.key("review") {
                pageItems += LocalBookImportPageItem("identity") {
                    Column {
                        TsuyomiMeasuredTextPager(review.title, style = MaterialTheme.typography.titleMedium, fillViewport = false, alwaysShowPageControls = false)
                        Text(review.format, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                pageItems += LocalBookImportPageItem("compatibility") {
                    TsuyomiMeasuredPager(itemCount = review.compatibility.size, itemKey = { "compatibility:$it" }, fillViewport = false,
                        itemContent = { TsuyomiMeasuredTextPager(review.compatibility[it], fillViewport = false, alwaysShowPageControls = false) })
                }
                if (review.matches.isNotEmpty()) {
                    AppendImportTextLines(pageItems, "duplicate-scope", stringResource(R.string.local_book_duplicate_scope), textWidth)
                    pageItems += LocalBookImportPageItem("matches") {
                        TsuyomiMeasuredPager(itemCount = review.matches.size, itemKey = { "match:${review.matches[it].publicationId}" }, fillViewport = false,
                            itemContent = { index ->
                                val match = review.matches[index]
                                TsuyomiCheckboxRow(label = if (match.originalAvailable) match.title else stringResource(R.string.local_book_missing_match, match.title),
                                    checked = review.selectedPublicationId == match.publicationId, enabled = !state.busy,
                                    onCheckedChange = { onAction(LocalBookImportAction.ChooseAssociation(match.publicationId)) })
                            })
                    }
                    appendImportCheckboxLines(
                        items = pageItems,
                        key = "independent",
                        label = stringResource(R.string.local_book_independent_copy),
                        checked = review.independentCopy,
                        enabled = !state.busy,
                        maxWidth = maxWidth,
                        onCheckedChange = { onAction(LocalBookImportAction.ChooseAssociation(null)) },
                    )
                }
                if (review.encodings.isNotEmpty()) {
                    pageItems += LocalBookImportPageItem("encoding") {
                        TsuyomiChoiceMenu(
                            label = stringResource(R.string.local_book_encoding),
                            options = listOf(TsuyomiChoiceOption("", stringResource(R.string.local_book_choose_encoding))) +
                                review.encodings.map { TsuyomiChoiceOption(it.charsetName, it.charsetName) },
                            selectedKey = review.selectedCharset.orEmpty(),
                            onSelect = { if (it.isNotEmpty()) onAction(LocalBookImportAction.ChooseCharset(it)) },
                        )
                    }
                    review.encodings.firstOrNull { it.charsetName == review.selectedCharset }?.let { encoding ->
                        androidx.compose.runtime.key("preview") {
                            AppendImportTextLines(pageItems, "preview", encoding.preview, textWidth, MaterialTheme.typography.bodyLarge)
                        }
                    }
                    pageItems += LocalBookImportPageItem("headings") {
                        TsuyomiMeasuredPager(itemCount = review.headings.size,
                            itemKey = { "heading:${review.headings[it].paragraphOrdinal}" }, fillViewport = false,
                            itemContent = { index ->
                                val heading = review.headings[index]
                                Column {
                                    TsuyomiCheckboxRow(label = stringResource(R.string.local_book_heading_paragraph, heading.paragraphOrdinal),
                                        checked = heading.selected, enabled = !state.busy,
                                        onCheckedChange = { onAction(LocalBookImportAction.EditHeading(heading.paragraphOrdinal, heading.title, it)) })
                                    TsuyomiTextField(value = heading.title, label = stringResource(R.string.local_book_heading_title),
                                        onValueChange = { onAction(LocalBookImportAction.EditHeading(heading.paragraphOrdinal, it, heading.selected)) }, singleLine = true, enabled = !state.busy)
                                }
                            })
                    }
                    pageItems += LocalBookImportPageItem("new-heading:paragraph") {
                        TsuyomiTextField(
                            value = review.newHeadingParagraph,
                            label = stringResource(R.string.local_book_heading_ordinal),
                            onValueChange = { onAction(LocalBookImportAction.EditNewHeadingParagraph(it)) },
                            singleLine = true,
                            enabled = !state.busy,
                        )
                    }
                    pageItems += LocalBookImportPageItem("new-heading:title") {
                        TsuyomiTextField(
                            value = review.newHeadingTitle,
                            label = stringResource(R.string.local_book_heading_title),
                            onValueChange = { onAction(LocalBookImportAction.EditNewHeadingTitle(it)) },
                            singleLine = true,
                            enabled = !state.busy,
                        )
                    }
                    appendImportButton(
                        pageItems,
                        "new-heading:add",
                        stringResource(R.string.local_book_add_heading),
                        action = { onAction(LocalBookImportAction.AddHeading) },
                        style = TsuyomiButtonStyle.TEXT,
                        enabled = !state.busy,
                    )
                    pageItems += LocalBookImportPageItem("segment:paragraphs") {
                        TsuyomiTextField(
                            value = review.paragraphsPerSection,
                            label = stringResource(R.string.local_book_segment_paragraphs),
                            supportingText = stringResource(R.string.local_book_segment_scope),
                            onValueChange = { onAction(LocalBookImportAction.EditParagraphsPerSection(it)) },
                            singleLine = true,
                            enabled = !state.busy,
                        )
                    }
                }
                review.error?.let { error ->
                    androidx.compose.runtime.key("review:error") {
                        AppendImportTextLines(pageItems, "review:error", error, textWidth)
                    }
                }
                pageItems += LocalBookImportPageItem("review:actions") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Lg, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                    ) {
                        TsuyomiButton(
                            text = stringResource(R.string.local_book_cancel_current),
                            onClick = { onAction(LocalBookImportAction.Cancel) },
                            style = TsuyomiButtonStyle.TEXT,
                        )
                        TsuyomiButton(
                            text = stringResource(R.string.local_book_confirm_import),
                            onClick = { onAction(LocalBookImportAction.Confirm) },
                            enabled = !state.busy && review.ready,
                        )
                    }
                }
            }
        }
        pageItems += LocalBookImportPageItem("results") {
            TsuyomiMeasuredPager(itemCount = state.results.size,
                itemKey = { "result:${state.results[it].resultId}" }, fillViewport = false,
                itemContent = { index ->
                    val result = state.results[index]
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(result.fileName, style = MaterialTheme.typography.titleSmall)
                        Text(result.message, style = MaterialTheme.typography.bodyMedium)
                        result.publicationId?.let { publicationId ->
                            TsuyomiButton(stringResource(R.string.local_book_open_detail), { onAction(LocalBookImportAction.OpenBook(publicationId)) }, style = TsuyomiButtonStyle.TEXT,
                                modifier = Modifier.testTag("local-book-import-result-${result.resultId}"))
                        }
                    }
                })
        }

        if (review == null && !state.busy) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                TsuyomiMeasuredPager(
                    itemCount = pageItems.size,
                    itemKey = { pageItems[it].key },
                    itemContent = { pageItems[it].content() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = pageState,
                    verticalSpacing = TsuyomiSpacing.Sm,
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
                TsuyomiButton(
                    text = stringResource(R.string.local_book_choose_files),
                    onClick = { onAction(LocalBookImportAction.ChooseFiles) },
                )
                Spacer(Modifier.weight(1f))
            }
        } else {
            if (review == null) {
                appendImportButton(pageItems, "cancel", stringResource(R.string.local_book_cancel_current),
                    action = { onAction(LocalBookImportAction.Cancel) }, style = TsuyomiButtonStyle.TEXT)
            }
            TsuyomiMeasuredPager(
                itemCount = pageItems.size,
                itemKey = { pageItems[it].key },
                itemContent = { pageItems[it].content() },
                modifier = Modifier.fillMaxSize(),
                state = pageState,
                verticalSpacing = TsuyomiSpacing.Sm,
                horizontalAlignment = Alignment.CenterHorizontally,
            )
        }
    }
}
