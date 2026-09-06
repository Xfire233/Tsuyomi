/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.icons.TsuyomiIcons
import org.tsuyomi.shared.sourcecontract.RemoteTarget

/** One independently selectable local manual-collection destination. */
data class DetailCollectionDestination(
    val id: String,
    val label: String,
    val selected: Boolean,
)

/** Compact actions rendered inside the Detail split button's anchored Material dropdown menu. */
@Composable
fun BookDestinationMenu(
    readLater: Boolean,
    shortcutPinned: Boolean,
    collections: List<DetailCollectionDestination>,
    remoteTargets: List<RemoteTarget>,
    selectedRemoteTargetId: String?,
    loadingRemoteTargets: Boolean,
    websiteGroupingEnabled: Boolean,
    onToggleReadLater: () -> Unit,
    onToggleShortcut: () -> Unit,
    onToggleCollection: (String) -> Unit,
    onApplyWebsite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DestinationMenuItem(
        label = stringResource(R.string.book_read_later),
        selectedStateDescription = stringResource(
            if (readLater) R.string.book_read_later_selected else R.string.book_read_later_unselected,
        ),
        icon = if (readLater) TsuyomiIcons.Bookmark else TsuyomiIcons.BookmarkOutline,
        selected = readLater,
        modifier = Modifier.testTag("detail-read-later-action"),
        onClick = {
            onToggleReadLater()
            onDismiss()
        },
    )
    HorizontalDivider()
    DestinationSectionLabel("加入其它位置")
    DestinationMenuItem(
        label = "快捷书架",
        icon = TsuyomiIcons.Shelf,
        selected = shortcutPinned,
        onClick = {
            onToggleShortcut()
            onDismiss()
        },
    )
    collections.forEach { destination ->
        DestinationMenuItem(
            label = destination.label,
            icon = TsuyomiIcons.Folder,
            selected = destination.selected,
            onClick = {
                onToggleCollection(destination.id)
                onDismiss()
            },
        )
    }
    HorizontalDivider()
    DestinationSectionLabel("网站收藏")
    when {
        loadingRemoteTargets -> DestinationStatusRow(
            if (websiteGroupingEnabled) "正在读取网站目标…" else "正在读取网站书架…",
        )
        remoteTargets.isEmpty() -> DestinationStatusRow(
            if (websiteGroupingEnabled) "没有可用的网站目标" else "网站书架不可用",
        )
        !websiteGroupingEnabled -> {
            val defaultTarget = remoteTargets.firstOrNull { it.targetId == selectedRemoteTargetId }
                ?: remoteTargets.first()
            DestinationMenuItem(
                label = "全部网站收藏",
                icon = TsuyomiIcons.Mirror,
                selected = false,
                modifier = Modifier.testTag("detail-apply-website-destination"),
                onClick = {
                    onApplyWebsite(defaultTarget.targetId)
                    onDismiss()
                },
            )
        }
        else -> remoteTargets.forEach { target ->
            DestinationMenuItem(
                label = target.displayName,
                icon = TsuyomiIcons.Mirror,
                selected = target.targetId == selectedRemoteTargetId,
                modifier = if (target.targetId == selectedRemoteTargetId) {
                    Modifier.testTag("detail-selected-website-destination")
                } else {
                    Modifier
                },
                onClick = {
                    onApplyWebsite(target.targetId)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun DestinationSectionLabel(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DestinationStatusRow(message: String) {
    DropdownMenuItem(
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = {},
        enabled = false,
    )
}

@Composable
private fun DestinationMenuItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedStateDescription: String = if (selected) "已选择" else "未选择",
) {
    DropdownMenuItem(
        text = { Text(label, maxLines = 1) },
        onClick = onClick,
        modifier = modifier.semantics {
            stateDescription = selectedStateDescription
        },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (selected) {
            { Icon(TsuyomiIcons.Selected, contentDescription = null) }
        } else {
            null
        },
    )
}
