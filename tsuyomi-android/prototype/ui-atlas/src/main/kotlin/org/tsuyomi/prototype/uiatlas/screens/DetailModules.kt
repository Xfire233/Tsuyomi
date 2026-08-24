/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tsuyomi.prototype.uiatlas.components.AtlasChip
import org.tsuyomi.prototype.uiatlas.components.AtlasCoverImage
import org.tsuyomi.prototype.uiatlas.components.AtlasIconButton
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.fixtures.SourceAtlasFixtures
import org.tsuyomi.prototype.uiatlas.model.AtlasBook
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment

@Composable
internal fun DetailIdentityModule(
    book: AtlasBook,
    sourceColor: Color?,
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(AtlasSpacing.Md)
            .testTag("detail-identity-module"),
        verticalAlignment = Alignment.Top,
    ) {
        AtlasCoverImage(
            cover = book.cover,
            title = book.title,
            modifier = Modifier.size(width = 108.dp, height = 144.dp),
            sourceColor = sourceColor,
        )
        Column(Modifier.weight(1f).padding(start = AtlasSpacing.Md)) {
            Text(book.title, style = MaterialTheme.typography.headlineSmall)
            book.authors?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                book.progressLabel ?: "尚未开始",
                modifier = Modifier.padding(top = AtlasSpacing.Sm),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.padding(top = AtlasSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(5) { index ->
                    val selected = index < rating
                    AtlasIconButton(
                        if (selected) AtlasIcons.Star else AtlasIcons.StarOutline,
                        "${index + 1} 星评分",
                        { onRatingChange(if (rating == index + 1) 0 else index + 1) },
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            if (book.unreadUpdates > 0) {
                Text(
                    "${book.unreadUpdates} 章待读",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailTagActionsModule(
    tags: List<String>,
    readLater: Boolean,
    onAddTag: () -> Unit,
    onToggleReadLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm)
            .testTag("detail-tag-actions-module"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (LocalAtlasEnvironment.current.eInk) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        } else {
            null
        },
    ) {
        Row(
            Modifier
                .height(IntrinsicSize.Min)
                .padding(horizontal = AtlasSpacing.Sm, vertical = AtlasSpacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                maxLines = 2,
                horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs),
                verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs),
            ) {
                tags.forEach { tag -> DetailTagLabel(tag) }
                DetailTagAddButton(onAddTag)
            }
            DetailReadLaterButton(selected = readLater, onClick = onToggleReadLater)
        }
    }
}
@Composable
private fun DetailTagLabel(text: String) {
    val eInk = LocalAtlasEnvironment.current.eInk
    Box(
        modifier = Modifier.height(ButtonDefaults.MinHeight + AtlasSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (eInk) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = if (eInk) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        ) {
            Box(
                modifier = Modifier.height(ButtonDefaults.MinHeight).padding(horizontal = AtlasSpacing.Sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DetailTagAddButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.minimumInteractiveComponentSize().size(ButtonDefaults.MinHeight),
        shape = MaterialTheme.shapes.small,
    ) {
        Icon(AtlasIcons.Add, contentDescription = "添加标签", modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DetailReadLaterButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize().heightIn(min = ButtonDefaults.MinHeight),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = AtlasSpacing.Sm),
        border = BorderStroke(
            if (eInk) 1.5.dp else 1.dp,
            if (eInk) AtlasEInkPalette.Ink else MaterialTheme.colorScheme.outline,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = when {
                eInk -> AtlasEInkPalette.Paper
                selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
            contentColor = if (eInk) AtlasEInkPalette.Ink else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(
            if (selected) "已稍后再读" else "稍后再读",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}


@Composable
internal fun DetailIntroductionModule(
    description: String,
    status: String?,
    modifier: Modifier = Modifier,
) {
    DetailModuleFrame(modifier.testTag("detail-introduction-module")) {
        DetailModuleHeader(
            icon = AtlasIcons.Info,
            title = "简介",
        )
        Text(
            text = description,
            modifier = Modifier.padding(
                start = AtlasSpacing.Md + 20.dp + AtlasSpacing.Sm,
                end = AtlasSpacing.Md,
                bottom = AtlasSpacing.Sm,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (status != null) {
            Text(
                text = status,
                modifier = Modifier.padding(
                    start = AtlasSpacing.Md + 20.dp + AtlasSpacing.Sm,
                    end = AtlasSpacing.Md,
                    bottom = AtlasSpacing.Sm,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DetailDirectoryModule(
    totalChapters: Int,
    chapters: List<SourceAtlasFixtures.AtlasChapter>,
    unreadOnly: Boolean,
    descending: Boolean,
    selected: Boolean,
    onToggleUnreadOnly: () -> Unit,
    onToggleOrder: () -> Unit,
    onOpenChapter: (SourceAtlasFixtures.AtlasChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailModuleFrame(modifier.testTag("detail-directory-module")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = AtlasSpacing.Md, end = AtlasSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AtlasIcons.Chapters,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "全文目录",
                modifier = Modifier
                    .padding(start = AtlasSpacing.Sm)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${totalChapters}章",
                modifier = Modifier.padding(start = AtlasSpacing.Sm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onToggleUnreadOnly,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        stateDescription = if (unreadOnly) "当前筛选：仅看未读" else "当前筛选：全部章节"
                    },
                contentPadding = PaddingValues(horizontal = AtlasSpacing.Xs),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (unreadOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(AtlasIcons.Filter, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("仅看未读", modifier = Modifier.padding(start = AtlasSpacing.Xs), maxLines = 1)
            }
            val order = if (descending) "倒序" else "正序"
            IconButton(
                onClick = onToggleOrder,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (descending) AtlasIcons.Down else AtlasIcons.Up,
                    contentDescription = "当前顺序：$order，点按切换",
                )
            }
        }
        chapters.forEach { chapter ->
            DetailChapterRow(
                chapter = chapter,
                selected = selected,
                onClick = { onOpenChapter(chapter) },
            )
        }
    }
}

@Composable
private fun DetailModuleFrame(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun DetailModuleHeader(
    icon: ImageVector,
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            modifier = Modifier
                .padding(start = AtlasSpacing.Sm)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun DetailChapterMarker(chapter: SourceAtlasFixtures.AtlasChapter) {
    val eInk = LocalAtlasEnvironment.current.eInk
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when {
            chapter.updated && eInk -> Box(
                Modifier
                    .size(8.dp)
                    .border(1.5.dp, AtlasEInkPalette.Ink, CircleShape)
                    .testTag("chapter-update-marker-${chapter.number}"),
            )
            chapter.updated -> Box(
                Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .testTag("chapter-update-marker-${chapter.number}"),
            )
            !chapter.read -> Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (eInk) AtlasEInkPalette.Ink else MaterialTheme.colorScheme.primary,
                        CircleShape,
                    )
                    .testTag("chapter-unread-marker-${chapter.number}"),
            )
        }
    }
}

@Composable
private fun DetailChapterRow(
    chapter: SourceAtlasFixtures.AtlasChapter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val current = chapter.number == SourceAtlasFixtures.CURRENT_CHAPTER
    val statusDescription = buildList {
        if (chapter.updated) add("有更新")
        if (current) add("当前阅读")
        add(if (chapter.read) "已读" else "未读")
        if (chapter.downloaded) add("已下载")
    }.joinToString("，")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                this.selected = selected
                stateDescription = statusDescription
            },
        color = when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            current -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailChapterMarker(chapter)
            Spacer(Modifier.size(AtlasSpacing.Sm))
            Text(
                text = chapter.title,
                modifier = Modifier.weight(1f).testTag("chapter-title-${chapter.number}"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (chapter.read) FontWeight.Normal else FontWeight.Medium,
                ),
                color = if (chapter.read) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chapter.downloaded) {
                    Icon(
                        imageVector = AtlasIcons.Downloaded,
                        contentDescription = "已下载",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (current) AtlasChip("当前")
            }
        }
    }
}
