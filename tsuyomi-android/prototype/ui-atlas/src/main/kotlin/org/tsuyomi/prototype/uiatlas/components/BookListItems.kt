/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.model.AtlasBranding
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment
import org.tsuyomi.prototype.uiatlas.theme.atlasFocusRing

/** One task-relevant state; progress is rendered in its own slot. */
private fun stateLabelOf(book: AtlasBook): String? = when {
    book.unreadUpdates > 0 -> AtlasStrings.unreadUpdates(book.unreadUpdates)
    book.dormantSource -> AtlasStrings.DORMANT_SOURCE
    book.readLater -> AtlasStrings.READ_LATER
    else -> null
}

/** Validated source tint for the cover fallback base, when branding is valid. */
private fun fallbackTintOf(book: AtlasBook): Color? =
    (book.source?.branding as? AtlasBranding.Valid)?.color

/** M3 dense-cover row used by Library and mixed result lists. Library callers keep source off. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListItemRow(
    book: AtlasBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSourceChip: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    gesturesHandledExternally: Boolean = false,
    showDivider: Boolean = true,
) {
    val environment = LocalAtlasEnvironment.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val dividerColor = if (environment.eInk) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
    val dividerWidth = if (environment.eInk) 1.5.dp else 1.dp
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val coverHeight = 112.dp
    val coverWidth = coverHeight * .75f

    ListItem(
        headlineContent = {
            Text(book.title, maxLines = if (showSourceChip) 1 else 2, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = coverHeight + AtlasSpacing.Md)
            .atlasFocusRing(MaterialTheme.shapes.small, focused, MaterialTheme.colorScheme.primary)
            .drawBehind {
                if (showDivider) {
                    val stroke = dividerWidth.toPx()
                    drawLine(dividerColor, Offset(0f, size.height - stroke / 2f), Offset(size.width, size.height - stroke / 2f), stroke)
                }
            }
            .semantics(mergeDescendants = true) { this.selected = selected }
            .then(
                if (gesturesHandledExternally) Modifier
                else Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            ),
        overlineContent = book.authors?.let { authors ->
            {
                Text(
                    text = authors,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                book.progressLabel?.let {
                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                stateLabelOf(book)?.let {
                    Text(
                        it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showSourceChip && book.source != null) {
                    AtlasChip(text = book.source.name)
                }
            }
        },
        leadingContent = {
            AtlasCoverImage(
                cover = book.cover,
                title = book.title,
                modifier = Modifier.size(width = coverWidth, height = coverHeight),
                sourceColor = fallbackTintOf(book),
            )
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = containerColor),
    )
}

/** Text-first M3 ListItem for high-density Library scanning; never renders tags or source identity. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactBookListItem(
    book: AtlasBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    gesturesHandledExternally: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val environment = LocalAtlasEnvironment.current
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ListItem(
        headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val line = book.progressLabel ?: book.authors
            if (line != null) Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs)) {
                if (selected) Icon(AtlasIcons.Check, contentDescription = "已选择", modifier = Modifier.size(24.dp))
                if (trailing != null) trailing()
                else book.rating?.let { Text(AtlasStrings.ratingLabel(it), style = MaterialTheme.typography.labelMedium) }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (environment.eInk) Modifier.padding(vertical = 1.dp) else Modifier)
            .atlasFocusRing(MaterialTheme.shapes.small, focused, MaterialTheme.colorScheme.primary)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .then(
                if (gesturesHandledExternally) Modifier
                else Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            ),
        colors = ListItemDefaults.colors(containerColor = containerColor),
    )
}

/** M3 card grid: 3:4 cover, opaque text area, one semantic status line, no Library tags. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridCard(
    book: AtlasBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSourceChip: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    dragDescription: String? = null,
    gesturesHandledExternally: Boolean = false,
) {
    val environment = LocalAtlasEnvironment.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.medium
    val status = book.progressLabel ?: stateLabelOf(book)
    Card(
        modifier = modifier
            .atlasFocusRing(shape, focused, MaterialTheme.colorScheme.primary)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                dragDescription?.let { contentDescription = it }
            }
            .then(
                if (gesturesHandledExternally) Modifier
                else Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = if (environment.eInk) BorderStroke(1.5.dp, AtlasEInkPalette.N90) else null,
    ) {
        Box {
            AtlasCoverImage(
                cover = book.cover,
                title = book.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                sourceColor = fallbackTintOf(book),
            )
            if (book.unreadUpdates > 0) {
                AtlasChip(
                    text = AtlasStrings.unreadUpdates(book.unreadUpdates),
                    modifier = Modifier.align(Alignment.TopStart).padding(AtlasSpacing.Xs),
                    container = if (environment.eInk) AtlasEInkPalette.Paper else MaterialTheme.colorScheme.primaryContainer,
                    content = if (environment.eInk) AtlasEInkPalette.Ink else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (showSourceChip && book.source != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(AtlasSpacing.Xs).size(24.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (environment.eInk) AtlasEInkPalette.Paper else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (environment.eInk) 1.5.dp else 1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    AtlasSourceMarkCanvas(
                        mark = book.source.mark,
                        tint = if (environment.eInk) AtlasEInkPalette.Ink else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
            if (!environment.eInk) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                            ),
                        )
                        .padding(start = AtlasSpacing.Sm, top = 28.dp, end = AtlasSpacing.Sm, bottom = AtlasSpacing.Xs),
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    status?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(AtlasSpacing.Xs).size(32.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (environment.eInk) AtlasEInkPalette.Ink else MaterialTheme.colorScheme.primary,
                    contentColor = if (environment.eInk) AtlasEInkPalette.Paper else MaterialTheme.colorScheme.onPrimary,
                    border = if (environment.eInk) BorderStroke(2.dp, AtlasEInkPalette.Ink) else null,
                ) { Icon(AtlasIcons.Check, contentDescription = "已选择", modifier = Modifier.padding(5.dp)) }
            }
        }
        if (environment.eInk) {
            Column(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = AtlasSpacing.Sm, vertical = AtlasSpacing.Xs)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = status.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
