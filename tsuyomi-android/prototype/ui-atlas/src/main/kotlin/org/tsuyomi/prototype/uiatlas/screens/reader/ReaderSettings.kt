/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.theme.AtlasMotion
import org.tsuyomi.prototype.uiatlas.theme.LocalAtlasEnvironment
import org.tsuyomi.prototype.uiatlas.theme.AtlasSpacing

@Immutable
internal data class ReaderSettingsUiState(
    val fontSize: Float,
    val lineHeight: Float,
    val horizontalMargin: Float,
    val verticalMargin: Float,
    val paragraphSpacing: Float,
    val letterSpacing: Float,
    val firstLineIndent: Float,
    val fontWeight: String,
    val alignment: String,
    val paper: String,
    val flow: ReaderFlow,
    val pageAnimation: Boolean,
    val volumePaging: Boolean,
    val keepAwake: Boolean,
    val lockPortrait: Boolean,
    val immersive: Boolean,
    val progressVisible: Boolean,
)

internal sealed interface ReaderSettingsAction {
    data class FontSize(val value: Float) : ReaderSettingsAction
    data class LineHeight(val value: Float) : ReaderSettingsAction
    data class HorizontalMargin(val value: Float) : ReaderSettingsAction
    data class VerticalMargin(val value: Float) : ReaderSettingsAction
    data class ParagraphSpacing(val value: Float) : ReaderSettingsAction
    data class LetterSpacing(val value: Float) : ReaderSettingsAction
    data class FirstLineIndent(val value: Float) : ReaderSettingsAction
    data class FontWeight(val value: String) : ReaderSettingsAction
    data class Alignment(val value: String) : ReaderSettingsAction
    data class Paper(val value: String) : ReaderSettingsAction
    data class Flow(val value: ReaderFlow) : ReaderSettingsAction
    data class PageAnimation(val value: Boolean) : ReaderSettingsAction
    data class VolumePaging(val value: Boolean) : ReaderSettingsAction
    data class KeepAwake(val value: Boolean) : ReaderSettingsAction
    data class LockPortrait(val value: Boolean) : ReaderSettingsAction
    data class Immersive(val value: Boolean) : ReaderSettingsAction
    data class ProgressVisible(val value: Boolean) : ReaderSettingsAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    state: ReaderSettingsUiState,
    onAction: (ReaderSettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val environment = LocalAtlasEnvironment.current
    val expansionDuration = AtlasMotion.duration(AtlasMotion.EXPAND_MS, environment)
    val scope = rememberCoroutineScope()
    var expandedContent by rememberSaveable { mutableStateOf(false) }
    val expandedContentState = rememberUpdatedState(expandedContent)
    val dismissingFromExpanded = remember { mutableStateOf(false) }
    val onDismissState = rememberUpdatedState(onDismiss)
    val sheetStateHolder = remember { arrayOfNulls<androidx.compose.material3.SheetState>(1) }
    val confirmValueChange: (SheetValue) -> Boolean = remember(scope) {
        { target ->
            if (expandedContentState.value && target == SheetValue.PartiallyExpanded) {
                if (!dismissingFromExpanded.value) {
                    dismissingFromExpanded.value = true
                    scope.launch {
                        sheetStateHolder[0]?.hide()
                        onDismissState.value()
                    }
                }
                false
            } else {
                true
            }
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
        confirmValueChange = confirmValueChange,
    )
    SideEffect { sheetStateHolder[0] = sheetState }

    fun dismissSheet() {
        if (dismissingFromExpanded.value) return
        dismissingFromExpanded.value = true
        scope.launch {
            sheetState.hide()
            onDismissState.value()
        }
    }

    fun showExpandedSettings() {
        scope.launch {
            sheetState.expand()
            expandedContent = true
        }
    }

    fun showQuickSettings() {
        scope.launch {
            expandedContent = false
            if (expansionDuration > 0) delay(expansionDuration.toLong())
            if (sheetState.hasPartiallyExpandedState) sheetState.partialExpand()
        }
    }

    LaunchedEffect(sheetState.currentValue) {
        when (sheetState.currentValue) {
            SheetValue.Expanded -> expandedContent = true
            SheetValue.PartiallyExpanded -> if (!dismissingFromExpanded.value) expandedContent = false
            SheetValue.Hidden -> Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = ::dismissSheet,
        sheetState = sheetState,
        modifier = Modifier.testTag("reader-settings-sheet"),
    ) {
        ReaderSettingsContent(
            state = state,
            expanded = expandedContent,
            expansionDuration = expansionDuration,
            onAction = onAction,
            onOpenExpanded = ::showExpandedSettings,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .testTag("reader-settings-content"),
        )
        BackHandler {
            if (expandedContent) showQuickSettings() else dismissSheet()
        }
    }
}

@Composable
private fun ReaderSettingsContent(
    state: ReaderSettingsUiState,
    expanded: Boolean,
    expansionDuration: Int,
    onAction: (ReaderSettingsAction) -> Unit,
    onOpenExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val dualAvailable = maxWidth >= 600.dp
        val expandedEnter = if (expansionDuration == 0) EnterTransition.None else {
            expandVertically(
                animationSpec = tween(expansionDuration),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(expansionDuration))
        }
        val expandedExit = if (expansionDuration == 0) ExitTransition.None else {
            shrinkVertically(
                animationSpec = tween(expansionDuration),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(expansionDuration / 2))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = AtlasSpacing.Lg)
                .padding(bottom = AtlasSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs),
        ) {
            ReaderQuickControls(state, dualAvailable, onAction)
            AnimatedVisibility(
                visible = !expanded,
                enter = if (expansionDuration == 0) EnterTransition.None else fadeIn(tween(expansionDuration / 2)),
                exit = expandedExit,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onOpenExpanded) {
                        Text("全部设置", style = MaterialTheme.typography.labelMedium)
                        Icon(AtlasIcons.Next, contentDescription = null)
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandedEnter,
                exit = expandedExit,
            ) {
                ReaderExpandedSettings(
                    state = state,
                    dualAvailable = dualAvailable,
                    onAction = onAction,
                )
            }
            Spacer(Modifier.height(AtlasSpacing.Lg))
        }
    }
}

@Composable
private fun ReaderQuickControls(
    state: ReaderSettingsUiState,
    dualAvailable: Boolean,
    onAction: (ReaderSettingsAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs)) {
        ReaderSliderSetting(
            label = "字号",
            valueLabel = "${state.fontSize.toInt()}sp",
            value = state.fontSize,
            valueRange = 12f..32f,
            steps = 19,
            onValueChange = { onAction(ReaderSettingsAction.FontSize(it)) },
        )
        ReaderSliderSetting(
            label = "行距",
            valueLabel = String.format(Locale.ROOT, "%.1f", state.lineHeight),
            value = state.lineHeight,
            valueRange = 1.2f..2.2f,
            steps = 9,
            onValueChange = { onAction(ReaderSettingsAction.LineHeight(it)) },
        )
        ReaderSliderSetting(
            label = "边距",
            valueLabel = "${state.horizontalMargin.toInt()}dp",
            value = state.horizontalMargin,
            valueRange = 12f..40f,
            steps = 6,
            onValueChange = { onAction(ReaderSettingsAction.HorizontalMargin(it)) },
        )
        ReaderSliderSetting(
            label = "段距",
            valueLabel = String.format(Locale.ROOT, "%.1fem", state.paragraphSpacing),
            value = state.paragraphSpacing,
            valueRange = 0f..1.6f,
            steps = 7,
            onValueChange = { onAction(ReaderSettingsAction.ParagraphSpacing(it)) },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Xs),
        ) {
            ReaderQuickToggleButton(
                label = "锁定竖屏",
                active = state.lockPortrait,
                stateDescription = if (state.lockPortrait) "已开启" else "已关闭",
                onClick = { onAction(ReaderSettingsAction.LockPortrait(!state.lockPortrait)) },
                modifier = Modifier.weight(1f).testTag("reader-quick-lock-portrait"),
            )
            ReaderQuickToggleButton(
                label = "阅读信息",
                active = state.progressVisible,
                stateDescription = if (state.progressVisible) "已开启" else "已关闭",
                onClick = { onAction(ReaderSettingsAction.ProgressVisible(!state.progressVisible)) },
                modifier = Modifier.weight(1f).testTag("reader-quick-reading-info"),
            )
            ReaderQuickToggleButton(
                label = "全屏沉浸",
                active = state.immersive,
                stateDescription = if (state.immersive) "已开启" else "已关闭",
                onClick = { onAction(ReaderSettingsAction.Immersive(!state.immersive)) },
                modifier = Modifier.weight(1f).testTag("reader-quick-immersive"),
            )
            ReaderQuickToggleButton(
                label = state.flow.label,
                active = null,
                stateDescription = "阅读方向，当前${state.flow.label}",
                onClick = { onAction(ReaderSettingsAction.Flow(nextQuickFlow(state.flow, dualAvailable))) },
                modifier = Modifier.weight(1f).testTag("reader-quick-flow"),
            )
        }
    }
}
@Composable
private fun ReaderQuickToggleButton(
    label: String,
    active: Boolean?,
    stateDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = active == true
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                if (active != null) selected = active
                this.stateDescription = stateDescription
            },
        shape = MaterialTheme.shapes.small,
        color = if (activeColor) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (activeColor) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = AtlasSpacing.Xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

private fun nextQuickFlow(current: ReaderFlow, dualAvailable: Boolean): ReaderFlow {
    val options = if (dualAvailable) ReaderFlow.entries else ReaderFlow.entries.filterNot { it == ReaderFlow.DUAL }
    val currentIndex = options.indexOf(current)
    return options[(currentIndex + 1).mod(options.size)]
}


@Composable
private fun ReaderExpandedSettings(
    state: ReaderSettingsUiState,
    dualAvailable: Boolean,
    onAction: (ReaderSettingsAction) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("reader-settings-expanded-content"),
        verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Md),
    ) {
        HorizontalDivider(Modifier.padding(top = AtlasSpacing.Xs))
        Text("全部阅读设置", style = MaterialTheme.typography.titleLarge)
        ReaderSettingsSection("排版") {
            Text("系统 CJK 无衬线", style = MaterialTheme.typography.bodyMedium)
            ReaderSliderSetting("字重", state.fontWeight, if (state.fontWeight == "常规") 0f else 1f, 0f..1f, 0) {
                onAction(ReaderSettingsAction.FontWeight(if (it < .5f) "常规" else "中等"))
            }
            ReaderSliderSetting("字距", String.format(Locale.ROOT, "%.1fsp", state.letterSpacing), state.letterSpacing, 0f..2f, 7) {
                onAction(ReaderSettingsAction.LetterSpacing(it))
            }
            ReaderSliderSetting("缩进", String.format(Locale.ROOT, "%.1f字", state.firstLineIndent), state.firstLineIndent, 0f..4f, 7) {
                onAction(ReaderSettingsAction.FirstLineIndent(it))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                listOf("左对齐", "两端对齐").forEach { option ->
                    FilterChip(
                        selected = state.alignment == option,
                        onClick = { onAction(ReaderSettingsAction.Alignment(option)) },
                        label = { Text(option) },
                    )
                }
            }
        }
        ReaderSettingsSection("页面") {
            ReaderSliderSetting("上下边", "${state.verticalMargin.toInt()}dp", state.verticalMargin, 8f..40f, 7) {
                onAction(ReaderSettingsAction.VerticalMargin(it))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
                listOf("纸张", "纯白", "夜间").forEach { option ->
                    FilterChip(
                        selected = state.paper == option,
                        onClick = { onAction(ReaderSettingsAction.Paper(option)) },
                        label = { Text(option) },
                    )
                }
            }
            if (!dualAvailable && state.flow == ReaderFlow.DUAL) {
                Text(
                    "双页需要至少 600dp 可用宽度；偏好会保留，但当前窗口不启用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ReaderSwitchSetting("分页动效", "Standard 使用克制的页面切换；降低动画时即时提交。", state.pageAnimation) {
                onAction(ReaderSettingsAction.PageAnimation(it))
            }
        }
        ReaderSettingsSection("导航") {
            Text("点击区域：左侧上一章 · 中间工具栏 · 右侧下一章", style = MaterialTheme.typography.bodyMedium)
            ReaderSwitchSetting("音量键翻页", "屏幕按钮始终保留为可见等价路径。", state.volumePaging) {
                onAction(ReaderSettingsAction.VolumePaging(it))
            }
        }
        ReaderSettingsSection("设备") {
            ReaderSwitchSetting("保持屏幕常亮", null, state.keepAwake) {
                onAction(ReaderSettingsAction.KeepAwake(it))
            }
        }
    }
}

@Composable
private fun ReaderSettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AtlasSpacing.Sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        content()
    }
}

@Composable
private fun ReaderSliderSetting(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium)
        Text(valueLabel, Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            valueRange = valueRange,
            steps = steps,
        )
    }
}


@Composable
private fun ReaderSwitchSetting(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
