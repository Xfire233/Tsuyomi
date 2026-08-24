/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import org.tsuyomi.prototype.uiatlas.AtlasStrings
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tsuyomi.prototype.uiatlas.components.AtlasBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasButton
import org.tsuyomi.prototype.uiatlas.components.AtlasChip
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.components.AtlasButtonStyle
import org.tsuyomi.prototype.uiatlas.components.AtlasInfoBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationBanner
import org.tsuyomi.prototype.uiatlas.components.AtlasMutationStatus
import org.tsuyomi.prototype.uiatlas.components.AtlasScaffold
import org.tsuyomi.prototype.uiatlas.components.AtlasOverflowItem
import org.tsuyomi.prototype.uiatlas.components.AtlasStateKind
import org.tsuyomi.prototype.uiatlas.components.AtlasStateView
import org.tsuyomi.prototype.uiatlas.components.AtlasTopBar
import org.tsuyomi.prototype.uiatlas.fixtures.MoreTransferIssueFixture
import org.tsuyomi.prototype.uiatlas.fixtures.MoreFeatureIntroductionFixture
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasProfile
import org.tsuyomi.prototype.uiatlas.theme.AtlasEInkPalette
import org.tsuyomi.prototype.uiatlas.theme.AtlasMotion
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment
import org.tsuyomi.prototype.uiatlas.theme.atlasFocusRing
@Composable
internal fun MorePage(
    context: AtlasContext,
    title: String,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)?,
    onResolvePrimary: () -> Unit,
    subtitle: String? = null,
    mutation: AtlasMutationStatus? = null,
    overflow: List<AtlasOverflowItem> = emptyList(),
    pinnedBanner: AtlasBanner? = null,
    content: @Composable () -> Unit,
) {
    AtlasScaffold(
        modifier = modifier,
        topBar = { AtlasTopBar(title = title, subtitle = subtitle, onUp = onUp, overflow = overflow) },
    ) {
        when (context.primaryState) {
            AtlasPageState.LOADING -> AtlasStateView(
                kind = AtlasStateKind.LOADING,
                title = AtlasStrings.LOADING,
                message = "正在准备“$title”的固定中文样例。",
            )
            AtlasPageState.EMPTY -> AtlasStateView(
                kind = AtlasStateKind.EMPTY,
                title = "当前没有可显示的内容",
                message = "可返回更多页面选择其他任务，或重新载入本页。",
                actionLabel = if (onUp != null) "返回更多" else "重新载入",
                onAction = onUp ?: onResolvePrimary,
            )
            AtlasPageState.ERROR -> AtlasStateView(
                kind = AtlasStateKind.ERROR,
                title = "无法打开$title",
                message = "页面内容暂时不可用；重试不会访问网络或文件。",
                actionLabel = AtlasStrings.RETRY,
                onAction = onResolvePrimary,
            )
            else -> Column(Modifier.fillMaxSize()) {
                if (context.showOfflineBanner) {
                    AtlasInfoBanner(AtlasBanner(AtlasStrings.OFFLINE_TITLE, "本页固定内容仍可使用；没有任务会在后台发起网络请求。"))
                }
                if (context.showRefreshingBanner) {
                    AtlasInfoBanner(AtlasBanner(AtlasStrings.REFRESHING_TITLE, "正在重新载入；现有内容保持可见。"))
                }
                if (context.showUnresolvedBanner) {
                    AtlasInfoBanner(
                        AtlasBanner(
                            AtlasStrings.UNRESOLVED_TITLE,
                            "结果尚未确认；不会自动重试或显示为成功。",
                            errorTone = true,
                        ),
                    )
                }
                if (pinnedBanner != null) AtlasInfoBanner(pinnedBanner)
                if (mutation != null) AtlasMutationBanner(mutation)
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

@Composable
internal fun MoreScrollableContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
        ) {
            content()
            Spacer(Modifier.size(AtlasSpacing.Md))
        }
    }
}

@Composable
internal fun MoreSectionHeader(title: String) {
    Text(text = title, modifier = Modifier.padding(top = AtlasSpacing.Sm), style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun MoreRowGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = if (LocalAtlasEnvironment.current.eInk) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column { content() } }
}

@Composable
internal fun MoreActionRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    showDivider: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = {
                val supporting = disabledReason ?: summary
                if (supporting.isNotBlank()) Text(supporting)
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value != null) Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(AtlasIcons.Next, contentDescription = null, modifier = Modifier.padding(start = AtlasSpacing.Sm))
                }
            },
            modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        if (showDivider) HorizontalDivider()
    }
}

@Composable
internal fun MoreSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    val explanation = disabledReason ?: supportingText
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(summary)
            if (explanation != null) Text(explanation, style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        modifier = modifier.fillMaxWidth().toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ).semantics {
            stateDescription = when {
                !enabled -> "已停用：${disabledReason ?: "当前不可用"}"
                checked -> "已开启"
                else -> "已关闭"
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
internal fun MoreSegmentedSelector(
    title: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledOptions: Set<String> = emptySet(),
    disabledReason: String? = null,
    supportingText: String? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = AtlasSpacing.Sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.padding(top = AtlasSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                    enabled = enabled && option !in disabledOptions,
                )
            }
        }
        val explanation = disabledReason ?: supportingText
        if (explanation != null) {
            Text(
                explanation,
                modifier = Modifier.padding(top = AtlasSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MoreInfoPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    errorTone: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val eInk = LocalAtlasEnvironment.current.eInk
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = when {
            eInk -> AtlasEInkPalette.Paper
            errorTone -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = when {
            eInk -> AtlasEInkPalette.Ink
            errorTone -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        border = when {
            eInk -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
            errorTone -> BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(Modifier.padding(AtlasSpacing.Md)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(body, Modifier.padding(top = AtlasSpacing.Xs), style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                AtlasButton(actionLabel, onAction, Modifier.padding(top = AtlasSpacing.Md), AtlasButtonStyle.SECONDARY)
            }
        }
    }
}

@Composable
internal fun MoreInfoRow(title: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = if (LocalAtlasEnvironment.current.eInk) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(AtlasSpacing.Md)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                value,
                Modifier.padding(top = AtlasSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MoreIssueRow(issue: MoreTransferIssueFixture) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = if (LocalAtlasEnvironment.current.eInk) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(Modifier.padding(AtlasSpacing.Md), verticalAlignment = Alignment.Top) {
            AtlasChip(issue.code)
            Column(Modifier.padding(start = AtlasSpacing.Md).weight(1f)) {
                Text(issue.title, style = MaterialTheme.typography.labelLarge)
                Text(issue.detail, Modifier.padding(top = AtlasSpacing.Xs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun MoreExpanderRow(expanded: Boolean, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        modifier = Modifier.fillMaxWidth().atlasFocusRing(MaterialTheme.shapes.small, focused, MaterialTheme.colorScheme.primary),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = if (LocalAtlasEnvironment.current.eInk) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ).semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                .heightIn(min = AtlasSpacing.Xxl)
                .padding(horizontal = AtlasSpacing.Md, vertical = AtlasSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (expanded) AtlasIcons.Expand else AtlasIcons.Next, contentDescription = null)
            Text(label, Modifier.padding(start = AtlasSpacing.Sm), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun ExpandableIssueRows(context: AtlasContext, visible: Boolean, issues: List<MoreTransferIssueFixture>) {
    val animatedVariant = context.variant?.id?.uppercaseChar() != 'F' || context.variant.option.lowercase() == "a"
    val animate = animatedVariant && !context.instantMotion
    if (animate) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(tween(AtlasMotion.EXPAND_MS), expandFrom = Alignment.Top) + fadeIn(tween(AtlasMotion.EXPAND_MS)),
            exit = shrinkVertically(tween(AtlasMotion.EXPAND_MS), shrinkTowards = Alignment.Top) + fadeOut(tween(AtlasMotion.EXPAND_MS)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md)) {
                issues.forEach { issue -> MoreIssueRow(issue) }
            }
        }
    } else if (visible) {
        Column(verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md)) {
            issues.forEach { issue -> MoreIssueRow(issue) }
        }
    }
}

@Composable
internal fun MoreVariantNote(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        border = if (LocalAtlasEnvironment.current.eInk) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline) else null,
    ) { Text(text, Modifier.padding(AtlasSpacing.Md), style = MaterialTheme.typography.bodySmall) }
}

@Composable
internal fun MoreDialog(
    title: String,
    dismissOnOutside: Boolean,
    onDismiss: () -> Unit,
    safeLabel: String,
    onSafe: () -> Unit,
    confirmLabel: String?,
    onConfirm: (() -> Unit)?,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val environment = LocalAtlasEnvironment.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnOutside,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = if (environment.eInk) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().padding(AtlasSpacing.Lg)
            },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = if (environment.eInk) Modifier.fillMaxSize() else Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                shape = if (environment.eInk) MaterialTheme.shapes.small else MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (environment.eInk) 1.5.dp else 1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(rememberScrollState())
                        .padding(AtlasSpacing.Lg),
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.padding(top = AtlasSpacing.Md), verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md)) { content() }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = AtlasSpacing.Lg),
                        horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                    ) {
                        AtlasButton(safeLabel, onSafe, style = AtlasButtonStyle.SECONDARY)
                        if (secondaryLabel != null && onSecondary != null) AtlasButton(secondaryLabel, onSecondary, style = AtlasButtonStyle.SECONDARY)
                        if (confirmLabel != null && onConfirm != null) AtlasButton(confirmLabel, onConfirm, style = AtlasButtonStyle.PRIMARY)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MoreDialogParagraph(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun FeatureIntroductionOverlay(
    context: AtlasContext,
    introduction: MoreFeatureIntroductionFixture,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    val environment = LocalAtlasEnvironment.current
    val variantC = context.variant?.id?.uppercaseChar() == 'C'
    val sheetPerProfile = !variantC || context.variant.option.lowercase() == "a"
    val fullWindow = environment.eInk || !sheetPerProfile
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = !fullWindow,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = if (fullWindow) Alignment.Center else Alignment.BottomCenter) {
            Surface(
                modifier = if (fullWindow) Modifier.fillMaxSize()
                else Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(0.9f),
                shape = if (fullWindow) MaterialTheme.shapes.small else MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = if (environment.eInk) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                        .verticalScroll(rememberScrollState()).padding(AtlasSpacing.Lg),
                ) {
                    if (!fullWindow) {
                        Surface(
                            Modifier.align(Alignment.CenterHorizontally).size(width = AtlasSpacing.Xl, height = AtlasSpacing.Xs),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ) {}
                        Spacer(Modifier.size(AtlasSpacing.Md))
                    }
                    MoreVariantNote(
                        if (sheetPerProfile) "变体 C-A ★：标准为底部选择面，电子墨水为不透明全窗口面板。"
                        else "变体 C-B：标准与电子墨水都使用全窗口对话框。"
                    )
                    Text(introduction.title, Modifier.padding(top = AtlasSpacing.Lg), style = MaterialTheme.typography.displayMedium)
                    Text(introduction.version, Modifier.padding(top = AtlasSpacing.Xs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(introduction.summary, Modifier.padding(top = AtlasSpacing.Md), style = MaterialTheme.typography.bodyLarge)
                    Column(Modifier.padding(top = AtlasSpacing.Lg), verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md)) {
                        introduction.points.forEach { point ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("•", style = MaterialTheme.typography.bodyLarge)
                                Text(point, Modifier.padding(start = AtlasSpacing.Sm), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    MoreInfoPanel(
                        "说明不是能力批准",
                        "关闭或标记已读只控制帮助内容；不会授予来源权限、触发远程操作或改变书架数据。",
                        Modifier.padding(top = AtlasSpacing.Lg),
                    )
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = AtlasSpacing.Lg),
                        horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm),
                    ) {
                        AtlasButton("稍后再看", onDismiss, style = AtlasButtonStyle.SECONDARY)
                        AtlasButton("知道了", onAcknowledge, style = AtlasButtonStyle.PRIMARY)
                    }
                }
            }
        }
    }
}

internal fun profileLabel(profile: AtlasProfile): String = if (profile == AtlasProfile.STANDARD) "标准" else "电子墨水"
internal fun onOff(value: Boolean): String = if (value) "开启" else "关闭"
internal fun disclosureVariantLabel(context: AtlasContext): String {
    val variantF = context.variant?.id?.uppercaseChar() == 'F'
    val optionA = !variantF || context.variant.option.lowercase() == "a"
    return if (optionA) {
        if (context.instantMotion) "变体 F-A ★：标准使用 220ms 顶部锚定展开；当前为电子墨水或减少动效，按契约即时切换。"
        else "变体 F-A ★：警告列表使用 220ms 顶部锚定展开与淡入；再次点击可反向收起。"
    } else "变体 F-B：所有显示配置都即时交换完整警告列表，不生成中间帧。"
}
