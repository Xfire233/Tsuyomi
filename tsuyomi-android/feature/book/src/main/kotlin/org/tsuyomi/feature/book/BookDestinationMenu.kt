/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.book

import androidx.compose.foundation.layout.padding
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
import org.tsuyomi.core.ui.components.TsuyomiMenuItem

/** One independently selectable local manual-collection destination. */
data class DetailCollectionDestination(
    val id: String,
    val label: String,
    val selected: Boolean,
)

fun bookDestinationMenuItemCount(
    collections: List<DetailCollectionDestination>,
    remoteTargets: List<RemoteTarget>,
    loadingRemoteTargets: Boolean,
    websiteGroupingEnabled: Boolean,
    websiteAvailable: Boolean,
): Int = 2 + collections.size + if (websiteAvailable) {
    1 + if (loadingRemoteTargets || remoteTargets.isEmpty() || !websiteGroupingEnabled) 1 else remoteTargets.size
} else 0

fun bookDestinationMenuItemKey(
    index: Int,
    collections: List<DetailCollectionDestination>,
    remoteTargets: List<RemoteTarget>,
    loadingRemoteTargets: Boolean,
    websiteGroupingEnabled: Boolean,
    websiteAvailable: Boolean,
): String {
    if (index == 0) return "read-later"
    val websiteRows = if (!websiteAvailable) 0 else
        1 + if (loadingRemoteTargets || remoteTargets.isEmpty() || !websiteGroupingEnabled) 1 else remoteTargets.size
    if (websiteAvailable && index == 1) return "website-heading"
    if (index in 2..websiteRows) return if (loadingRemoteTargets || remoteTargets.isEmpty()) "website-status"
        else if (!websiteGroupingEnabled) "website-default" else "website:${remoteTargets[index - 2].targetId}"
    if (index == 1 + websiteRows) return "local-heading"
    return "collection:${collections[index - websiteRows - 2].id}"
}

/** Compact actions rendered inside the Detail split button's anchored Material dropdown menu. */
@Composable
fun BookDestinationMenu(
    itemIndex: Int,
    readLater: Boolean,
    collections: List<DetailCollectionDestination>,
    remoteTargets: List<RemoteTarget>,
    selectedRemoteTargetId: String?,
    loadingRemoteTargets: Boolean,
    websiteGroupingEnabled: Boolean,
    onToggleReadLater: () -> Unit,
    onToggleCollection: (String) -> Unit,
    onApplyWebsite: ((String) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val websiteRows = if (onApplyWebsite == null) 0 else
        1 + if (loadingRemoteTargets || remoteTargets.isEmpty() || !websiteGroupingEnabled) 1 else remoteTargets.size
    if (itemIndex == 0) {
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
        return
    }
    if (onApplyWebsite != null && itemIndex <= websiteRows) {
        if (itemIndex == 1) {
            DestinationSectionLabel("网站收藏")
            return
        }
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
        else -> {
            val target = remoteTargets[itemIndex - 2]
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
    if (onApplyWebsite != null && itemIndex <= websiteRows) return
    if (itemIndex == 1 + websiteRows) {
        DestinationSectionLabel("本地收藏夹")
        return
    }
    val destination = collections[itemIndex - websiteRows - 2]
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
    TsuyomiMenuItem(
        label = message,
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
    TsuyomiMenuItem(
        label = label,
        onClick = onClick,
        selected = selected,
        modifier = modifier.semantics {
            this.selected = selected
            stateDescription = selectedStateDescription
        },
    )
}
