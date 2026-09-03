/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.components.TsuyomiActionChip
import org.tsuyomi.core.ui.components.TsuyomiToggleChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    state: ReaderSettingsUiState,
    onAction: (ReaderSettingsAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var expandedContent by rememberSaveable { mutableStateOf(false) }
    val expandedContentState = rememberUpdatedState(expandedContent)
    val dismissingFromExpanded = remember { mutableStateOf(false) }
    val collapsingToQuick = remember { mutableStateOf(false) }
    val onDismissState = rememberUpdatedState(onDismiss)
    val sheetStateHolder = remember { arrayOfNulls<androidx.compose.material3.SheetState>(1) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
        confirmValueChange = { target ->
            if (
                expandedContentState.value &&
                target == SheetValue.PartiallyExpanded &&
                !collapsingToQuick.value
            ) {
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
        },
    )
    LaunchedEffect(sheetState) { sheetStateHolder[0] = sheetState }

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
            expandedContent = true
            sheetState.expand()
        }
    }

    fun showQuickSettings() {
        scope.launch {
            collapsingToQuick.value = true
            try {
                expandedContent = false
                if (sheetState.hasPartiallyExpandedState) sheetState.partialExpand()
            } finally {
                collapsingToQuick.value = false
            }
        }
    }

    LaunchedEffect(sheetState.targetValue) {
        when (sheetState.targetValue) {
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
            onAction = onAction,
            onOpenExpanded = ::showExpandedSettings,
            modifier = Modifier.fillMaxWidth().fillMaxHeight().testTag("reader-settings-content"),
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
    onAction: (ReaderSettingsAction) -> Unit,
    onOpenExpanded: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val dualAvailable = maxWidth >= 600.dp
        val reducedMotion = rememberSystemReducedMotion()
        val motionDuration = if (reducedMotion) 0 else TsuyomiMotion.SELECTION_DURATION_MS
        val enterTransition =
            fadeIn(animationSpec = tween(motionDuration, easing = TsuyomiMotion.Easing)) +
                expandVertically(animationSpec = tween(motionDuration, easing = TsuyomiMotion.Easing))
        val exitTransition =
            fadeOut(animationSpec = tween(motionDuration, easing = TsuyomiMotion.Easing)) +
                shrinkVertically(animationSpec = tween(motionDuration, easing = TsuyomiMotion.Easing))

        Column(
            Modifier
                .fillMaxWidth()
                .height(if (expanded) maxHeight else maxHeight / 2)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = TsuyomiSpacing.Lg)
                .padding(bottom = TsuyomiSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = enterTransition,
                exit = exitTransition,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
                    Text(stringResource(R.string.reader_typography_group), style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                }
            }

            ReaderTypographyControls(
                state = state,
                expanded = expanded,
                onAction = onAction,
                modifier =
                    if (expanded) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.weight(1f).verticalScroll(rememberScrollState())
                    },
            )

            AnimatedVisibility(
                visible = !expanded,
                enter = enterTransition,
                exit = exitTransition,
            ) {
                ReaderQuickActions(state, dualAvailable, onAction)
            }
            AnimatedVisibility(
                visible = !expanded,
                enter = enterTransition,
                exit = exitTransition,
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onOpenExpanded) {
                            Text(stringResource(R.string.reader_all_settings), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(TsuyomiSpacing.Lg))
                }
            }

            AnimatedVisibility(
                visible = expanded,
                modifier = if (expanded) Modifier.weight(1f) else Modifier,
                enter = enterTransition,
                exit = exitTransition,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .testTag("reader-full-settings-scroll"),
                    verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
                ) {
                    ReaderFullSettings(state, dualAvailable, onAction)
                    Spacer(Modifier.height(TsuyomiSpacing.Lg))
                }
            }
        }
    }
}


@Composable
private fun ReaderTypographyControls(
    state: ReaderSettingsUiState,
    expanded: Boolean,
    onAction: (ReaderSettingsAction) -> Unit,
    modifier: Modifier,
) {
    val compactSliderRows = !expanded && LocalDensity.current.fontScale <= 1.3f
    val reducedMotion = rememberSystemReducedMotion()
    val motionDuration = if (reducedMotion) 0 else TsuyomiMotion.SELECTION_DURATION_MS
    Column(
        modifier =
            modifier
                .animateContentSize(
                    animationSpec = tween(motionDuration, easing = TsuyomiMotion.Easing),
                )
                .testTag("reader-typography-controls"),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
    ) {
        ReaderSliderSetting(
            label = stringResource(R.string.reader_font_size),
            valueLabel = "${state.fontSize.toInt()}sp",
            value = state.fontSize,
            valueRange = 12f..32f,
            steps = 19,
            compact = compactSliderRows,
            sliderTag = "reader-typography-font-size-slider",
            onValueChange = { onAction(ReaderSettingsAction.FontSize(it)) },
        )
        ReaderSliderSetting(
            label = stringResource(R.string.reader_line_spacing),
            valueLabel = String.format(Locale.ROOT, "%.1f", state.lineHeight),
            value = state.lineHeight,
            valueRange = 1.2f..2.2f,
            steps = 9,
            compact = compactSliderRows,
            sliderTag = "reader-typography-line-spacing-slider",
            onValueChange = { onAction(ReaderSettingsAction.LineHeight(it)) },
        )
        ReaderSliderSetting(
            label = stringResource(R.string.reader_margin),
            valueLabel = "${state.horizontalMargin.toInt()}dp",
            value = state.horizontalMargin,
            valueRange = 12f..40f,
            steps = 6,
            compact = compactSliderRows,
            sliderTag = "reader-typography-margin-slider",
            onValueChange = { onAction(ReaderSettingsAction.HorizontalMargin(it)) },
        )
        ReaderSliderSetting(
            label = stringResource(R.string.reader_paragraph_spacing),
            valueLabel = "${state.paragraphSpacing.toInt()}dp",
            value = state.paragraphSpacing,
            valueRange = 0f..32f,
            steps = 7,
            compact = compactSliderRows,
            sliderTag = "reader-typography-paragraph-spacing-slider",
            onValueChange = { onAction(ReaderSettingsAction.ParagraphSpacing(it)) },
        )
    }
}

@Composable
private fun ReaderQuickActions(
    state: ReaderSettingsUiState,
    dualAvailable: Boolean,
    onAction: (ReaderSettingsAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
        ) {
            TsuyomiToggleChip(
                text = stringResource(R.string.reader_lock_portrait),
                selected = state.lockPortrait,
                stateDescription = if (state.lockPortrait) "已开启" else "已关闭",
                onClick = { onAction(ReaderSettingsAction.LockPortrait(!state.lockPortrait)) },
                modifier = Modifier.weight(1f).testTag("reader-quick-lock-portrait"),
            )
            TsuyomiToggleChip(
                text = stringResource(R.string.reader_reading_info),
                selected = state.progressVisible,
                stateDescription = if (state.progressVisible) "已开启" else "已关闭",
                onClick = { onAction(ReaderSettingsAction.ProgressVisible(!state.progressVisible)) },
                modifier = Modifier.weight(1f).testTag("reader-quick-reading-info"),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
        ) {
            TsuyomiToggleChip(
                text = stringResource(R.string.reader_immersive),
                selected = state.immersive,
                stateDescription = if (state.immersive) "已开启" else "已关闭",
                onClick = { onAction(ReaderSettingsAction.Immersive(!state.immersive)) },
                modifier = Modifier.weight(1f).testTag("reader-quick-immersive"),
            )
            TsuyomiActionChip(
                text = state.flow.label,
                stateDescription = "阅读方向，当前${state.flow.label}",
                onClick = {
                    onAction(ReaderSettingsAction.Flow(nextQuickFlow(state.flow, dualAvailable)))
                },
                modifier = Modifier.weight(1f).testTag("reader-quick-flow"),
            )
        }
    }
}


private fun nextQuickFlow(current: ReaderFlow, dualAvailable: Boolean): ReaderFlow {
    val options = if (dualAvailable) ReaderFlow.entries else ReaderFlow.entries.filterNot { it == ReaderFlow.DUAL }
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    return options[(currentIndex + 1) % options.size]
}

@Composable
private fun ReaderFullSettings(
    state: ReaderSettingsUiState,
    dualAvailable: Boolean,
    onAction: (ReaderSettingsAction) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("reader-full-settings-groups"),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Lg),
    ) {
        ReaderSettingsSection(stringResource(R.string.reader_page_group)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
                ReaderFlow.entries.forEach { flow ->
                    FilterChip(
                        selected = state.flow == flow,
                        onClick = { onAction(ReaderSettingsAction.Flow(flow)) },
                        enabled = flow != ReaderFlow.DUAL || dualAvailable,
                        label = { Text(flow.label) },
                    )
                }
            }
        }
        ReaderSettingsSection(stringResource(R.string.reader_navigation_group)) {
            ReaderSwitchSetting(
                title = stringResource(R.string.reader_progress_info),
                checked = state.progressVisible,
                onCheckedChange = { onAction(ReaderSettingsAction.ProgressVisible(it)) },
            )
            ReaderSwitchSetting(
                title = stringResource(R.string.reader_immersive),
                checked = state.immersive,
                onCheckedChange = { onAction(ReaderSettingsAction.Immersive(it)) },
            )
        }
        ReaderSettingsSection(stringResource(R.string.reader_device_group)) {
            ReaderSwitchSetting(
                title = stringResource(R.string.reader_keep_awake),
                checked = state.keepAwake,
                onCheckedChange = { onAction(ReaderSettingsAction.KeepAwake(it)) },
            )
            ReaderSwitchSetting(
                title = stringResource(R.string.reader_lock_portrait),
                checked = state.lockPortrait,
                onCheckedChange = { onAction(ReaderSettingsAction.LockPortrait(it)) },
            )
        }
    }
}

@Composable
private fun ReaderSettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
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
    compact: Boolean = false,
    sliderTag: String? = null,
) {
    val reducedMotion = rememberSystemReducedMotion()
    val motionDuration = if (reducedMotion) 0 else TsuyomiMotion.SELECTION_DURATION_MS
    val sliderModifier = if (sliderTag == null) Modifier else Modifier.testTag(sliderTag)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(motionDuration, easing = TsuyomiMotion.Easing),
                ),
    ) {
        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    modifier = Modifier.widthIn(min = 56.dp, max = 72.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Slider(
                    value = value.coerceIn(valueRange),
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    steps = steps,
                    modifier = sliderModifier.weight(1f),
                )
                Text(
                    valueLabel,
                    modifier = Modifier.widthIn(min = 44.dp, max = 52.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = value.coerceIn(valueRange),
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = sliderModifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReaderSwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics { stateDescription = if (checked) "已开启" else "已关闭" },
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = TsuyomiSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
