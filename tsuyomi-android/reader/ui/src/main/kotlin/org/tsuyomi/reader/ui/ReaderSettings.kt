/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import org.tsuyomi.core.ui.components.tsuyomiAnimateContentSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.core.ui.components.TsuyomiActionChip
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiSwitchVisual
import org.tsuyomi.core.ui.components.TsuyomiModalSheet
import org.tsuyomi.core.ui.components.TsuyomiSlider
import org.tsuyomi.core.ui.components.TsuyomiTextField
import org.tsuyomi.core.ui.components.TsuyomiVisibility
import org.tsuyomi.core.ui.components.TsuyomiVisibilityEdge
import org.tsuyomi.core.ui.components.rememberTsuyomiModalSheetController
import org.tsuyomi.core.ui.components.TsuyomiToggleChip

@Composable
internal fun ReaderSettingsSheet(
    state: ReaderSettingsUiState,
    effectiveFlow: ReaderFlow,
    dualPageEligible: Boolean,
    hasFlowOverride: Boolean,
    onAction: (ReaderSettingsAction) -> Unit,
    onFollowGlobalFlow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val controller = rememberTsuyomiModalSheetController(
        allowPartiallyExpanded = true,
        onExpandedSwipeDismiss = onDismiss,
    )
    val latestOnDismiss by rememberUpdatedState(onDismiss)

    fun dismissSheet() {
        scope.launch {
            controller.hide()
            latestOnDismiss()
        }
    }

    fun showExpandedSettings() {
        scope.launch { controller.expand() }
    }

    fun showQuickSettings() {
        scope.launch { controller.partialExpand() }
    }

    TsuyomiModalSheet(
        onDismissRequest = ::dismissSheet,
        controller = controller,
        modifier = Modifier.testTag("reader-settings-sheet"),
    ) {
        ReaderSettingsContent(
            state = state,
            effectiveFlow = effectiveFlow,
            dualPageEligible = dualPageEligible,
            hasFlowOverride = hasFlowOverride,
            onAction = onAction,
            onFollowGlobalFlow = onFollowGlobalFlow,
            expanded = controller.targetExpanded,
            onOpenExpanded = ::showExpandedSettings,
            modifier = Modifier.fillMaxWidth().fillMaxHeight().testTag("reader-settings-content"),
        )
        BackHandler {
            if (controller.targetExpanded) showQuickSettings() else dismissSheet()
        }
    }
}

@Composable
private fun ReaderSettingsContent(
    state: ReaderSettingsUiState,
    effectiveFlow: ReaderFlow,
    dualPageEligible: Boolean,
    hasFlowOverride: Boolean,
    onAction: (ReaderSettingsAction) -> Unit,
    onFollowGlobalFlow: () -> Unit,
    expanded: Boolean,
    onOpenExpanded: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val quickScrollState = rememberScrollState()
        val fullScrollState = rememberScrollState()
        val scrollState = if (expanded) fullScrollState else quickScrollState
        Column(
            Modifier
                .fillMaxWidth()
                .height(if (expanded) maxHeight else maxHeight / 2)
                .padding(horizontal = TsuyomiSpacing.Lg)
                .padding(bottom = TsuyomiSpacing.Md),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .testTag(if (expanded) "reader-full-settings-scroll" else "reader-quick-settings-scroll"),
                verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
            ) {
                TsuyomiVisibility(
                    visible = expanded,
                    enterFrom = TsuyomiVisibilityEdge.TOP,
                    exitTo = TsuyomiVisibilityEdge.TOP,
                ) {
                    Column(
                        modifier = Modifier.testTag("reader-full-settings-typography"),
                        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
                    ) {
                        Text(stringResource(R.string.reader_typography_group), style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        ReaderTypographyControls(
                            state = state,
                            expanded = true,
                            onAction = onAction,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ReaderTypographyDetails(state, onAction)
                    }
                }
                TsuyomiVisibility(
                    visible = !expanded,
                    enterFrom = TsuyomiVisibilityEdge.BOTTOM,
                    exitTo = TsuyomiVisibilityEdge.BOTTOM,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs)) {
                        ReaderTypographyControls(
                            state = state,
                            expanded = false,
                            onAction = onAction,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ReaderQuickActions(state, dualPageEligible, onAction)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TsuyomiButton(
                                text = stringResource(R.string.reader_all_settings),
                                onClick = onOpenExpanded,
                                style = TsuyomiButtonStyle.TEXT,
                            )
                        }
                        Spacer(Modifier.height(TsuyomiSpacing.Lg))
                    }
                }
                TsuyomiVisibility(
                    visible = expanded,
                    enterFrom = TsuyomiVisibilityEdge.BOTTOM,
                    exitTo = TsuyomiVisibilityEdge.BOTTOM,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs)) {
                        ReaderFullSettings(
                            state = state,
                            effectiveFlow = effectiveFlow,
                            hasFlowOverride = hasFlowOverride,
                            onAction = onAction,
                            onFollowGlobalFlow = onFollowGlobalFlow,
                        )
                        Spacer(Modifier.height(TsuyomiSpacing.Lg))
                    }
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
    val compact = !expanded && LocalDensity.current.fontScale <= 1.3f
    Column(
        modifier = modifier.tsuyomiAnimateContentSize().testTag("reader-typography-controls"),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
    ) {
        ReaderSliderSetting(
            label = stringResource(R.string.reader_font_size),
            valueLabel = "${state.fontSize.toInt()}sp",
            value = state.fontSize,
            valueRange = 12f..32f,
            steps = 19,
            compact = compact,
            sliderTag = "reader-typography-font-size-slider",
            onValueChange = { onAction(ReaderSettingsAction.FontSize(it)) },
        )
        ReaderSliderSetting(
            label = stringResource(R.string.reader_line_spacing),
            valueLabel = String.format(Locale.ROOT, "%.1f", state.lineHeight),
            value = state.lineHeight,
            valueRange = 1.2f..2.2f,
            steps = 9,
            compact = compact,
            sliderTag = "reader-typography-line-spacing-slider",
            onValueChange = { onAction(ReaderSettingsAction.LineHeight(it)) },
        )
        ReaderSliderSetting(
            label = stringResource(R.string.reader_margin),
            valueLabel = "${state.horizontalMargin.toInt()}dp",
            value = state.horizontalMargin,
            valueRange = 12f..40f,
            steps = 6,
            sliderTag = "reader-typography-margin-slider",
            compact = compact,
            onValueChange = { onAction(ReaderSettingsAction.HorizontalMargin(it)) },
        )
        ReaderSliderSetting(
            label = stringResource(R.string.reader_paragraph_spacing),
            valueLabel = "${state.paragraphSpacing.toInt()}dp",
            value = state.paragraphSpacing,
            valueRange = 0f..32f,
            steps = 7,
            compact = compact,
            sliderTag = "reader-typography-paragraph-spacing-slider",
            onValueChange = { onAction(ReaderSettingsAction.ParagraphSpacing(it)) },
        )
    }
}

@Composable
private fun ReaderQuickActions(
    state: ReaderSettingsUiState,
    dualPageEligible: Boolean,
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
                onClick = { onAction(ReaderSettingsAction.Flow(nextQuickFlow(state.flow, dualPageEligible))) },
                modifier = Modifier.weight(1f).testTag("reader-quick-flow"),
            )
        }
    }
}

private fun nextQuickFlow(current: ReaderFlow, dualPageEligible: Boolean): ReaderFlow = when (current) {
    ReaderFlow.PAGED -> ReaderFlow.SCROLL
    ReaderFlow.SCROLL -> if (dualPageEligible) ReaderFlow.DUAL else ReaderFlow.PAGED
    ReaderFlow.DUAL -> ReaderFlow.PAGED
}

@Composable
private fun ReaderFullSettings(
    state: ReaderSettingsUiState,
    effectiveFlow: ReaderFlow,
    hasFlowOverride: Boolean,
    onAction: (ReaderSettingsAction) -> Unit,
    onFollowGlobalFlow: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag("reader-full-settings-groups"),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Lg),
    ) {
        ReaderSettingsSection(stringResource(R.string.reader_page_group)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
                ReaderFlow.entries.forEach { flow ->
                    TsuyomiToggleChip(
                        text = flow.label,
                        selected = state.flow == flow,
                        stateDescription = if (state.flow == flow) "已选择" else "未选择",
                        onClick = { onAction(ReaderSettingsAction.Flow(flow)) },
                    )
                }
            }
            if (state.flow == ReaderFlow.DUAL && effectiveFlow == ReaderFlow.PAGED) {
                Text(
                    stringResource(R.string.reader_dual_effective_paged),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasFlowOverride) {
                TsuyomiButton(
                    text = stringResource(R.string.reader_follow_global_default),
                    onClick = onFollowGlobalFlow,
                    style = TsuyomiButtonStyle.TEXT,
                )
            }
            Text(stringResource(R.string.reader_theme_label), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
                ReaderTheme.entries.forEach { theme ->
                    TsuyomiToggleChip(
                        text = stringResource(theme.labelResource()),
                        selected = state.theme == theme,
                        stateDescription = if (state.theme == theme) "已选择" else "未选择",
                        onClick = { onAction(ReaderSettingsAction.Theme(theme)) },
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
                title = stringResource(R.string.reader_volume_paging),
                checked = state.volumePaging,
                onCheckedChange = { onAction(ReaderSettingsAction.VolumePaging(it)) },
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
private fun ReaderTypographyDetails(
    state: ReaderSettingsUiState,
    onAction: (ReaderSettingsAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
        Text(stringResource(R.string.reader_font_family), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
            listOf(
                "system" to R.string.reader_font_family_system,
                "sans" to R.string.reader_font_family_sans,
                "serif" to R.string.reader_font_family_serif,
                "monospace" to R.string.reader_font_family_monospace,
            ).forEach { (family, label) ->
                TsuyomiToggleChip(
                    text = stringResource(label),
                    selected = state.fontFamily == family,
                    stateDescription = if (state.fontFamily == family) "已选择" else "未选择",
                    onClick = { onAction(ReaderSettingsAction.FontFamily(family)) },
                )
            }
        }
        Text(stringResource(R.string.reader_font_weight), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
            listOf(400 to R.string.reader_font_weight_regular, 500 to R.string.reader_font_weight_medium).forEach { (weight, label) ->
                TsuyomiToggleChip(
                    text = stringResource(label),
                    selected = state.fontWeight == weight,
                    stateDescription = if (state.fontWeight == weight) "已选择" else "未选择",
                    onClick = { onAction(ReaderSettingsAction.FontWeight(weight)) },
                )
            }
        }
        ReaderSliderSetting(
            label = stringResource(R.string.reader_letter_spacing),
            valueLabel = String.format(Locale.ROOT, "%.2fsp", state.letterSpacing),
            value = state.letterSpacing,
            valueRange = -0.05f..0.20f,
            steps = 24,
            sliderTag = "reader-letter-spacing-slider",
            onValueChange = { onAction(ReaderSettingsAction.LetterSpacing(it)) },
        )
        ReaderSliderSetting(
            label = stringResource(R.string.reader_first_line_indent),
            valueLabel = String.format(Locale.ROOT, "%.2fem", state.firstLineIndent),
            value = state.firstLineIndent,
            valueRange = 0f..4f,
            steps = 15,
            sliderTag = "reader-first-line-indent-slider",
            onValueChange = { onAction(ReaderSettingsAction.FirstLineIndent(it)) },
        )
        ReaderSliderSetting(
            label = stringResource(R.string.reader_vertical_margin),
            valueLabel = "${state.verticalMargin.toInt()}dp",
            value = state.verticalMargin,
            valueRange = 0f..64f,
            steps = 15,
            sliderTag = "reader-vertical-margin-slider",
            onValueChange = { onAction(ReaderSettingsAction.VerticalMargin(it)) },
        )
        Text(stringResource(R.string.reader_text_alignment), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
            listOf(
                "start" to R.string.reader_text_alignment_start,
                "justify" to R.string.reader_text_alignment_justify,
                "center" to R.string.reader_text_alignment_center,
                "end" to R.string.reader_text_alignment_end,
            ).forEach { (alignment, label) ->
                TsuyomiToggleChip(
                    text = stringResource(label),
                    selected = state.textAlignment == alignment,
                    stateDescription = if (state.textAlignment == alignment) "已选择" else "未选择",
                    onClick = { onAction(ReaderSettingsAction.TextAlignment(alignment)) },
                )
            }
        }
        ReaderColorOverrideSetting(
            label = stringResource(R.string.reader_foreground_color),
            value = state.foregroundColor,
            tag = "reader-foreground-color",
            onValueChanged = { onAction(ReaderSettingsAction.ForegroundColor(it)) },
        )
        ReaderColorOverrideSetting(
            label = stringResource(R.string.reader_background_color),
            value = state.backgroundColor,
            tag = "reader-background-color",
            onValueChanged = { onAction(ReaderSettingsAction.BackgroundColor(it)) },
        )
    }
}

@Composable
private fun ReaderColorOverrideSetting(
    label: String,
    value: String?,
    tag: String,
    onValueChanged: (String?) -> Unit,
) {
    var draft by rememberSaveable(value) { mutableStateOf(value.orEmpty()) }
    TsuyomiTextField(
        value = draft,
        onValueChange = { candidate ->
            val normalized = candidate.uppercase(Locale.ROOT)
            draft = normalized
            if (normalized.isEmpty()) {
                onValueChanged(null)
            } else if (HEX_COLOR.matches(normalized)) {
                onValueChanged(normalized)
            }
        },
        label = label,
        singleLine = true,
        supportingText = "#RRGGBB",
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

private fun ReaderTheme.labelResource(): Int = when (this) {
    ReaderTheme.PAPER -> R.string.reader_theme_paper
    ReaderTheme.WARM_GRAY -> R.string.reader_theme_warm_gray
    ReaderTheme.NIGHT_INK -> R.string.reader_theme_night_ink
    ReaderTheme.BLACK -> R.string.reader_theme_black
    ReaderTheme.INK_GREEN -> R.string.reader_theme_ink_green
}

private val HEX_COLOR = Regex("^#[0-9A-F]{6}$")


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
    val sliderModifier = if (sliderTag == null) Modifier else Modifier.testTag(sliderTag)
    Column(Modifier.fillMaxWidth().tsuyomiAnimateContentSize()) {
        if (compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.widthIn(min = 56.dp, max = 72.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                TsuyomiSlider(
                    value = value,
                    onValueChange = onValueChange,
                    label = label,
                    valueRange = valueRange,
                    steps = steps,
                    valueDescription = valueLabel,
                    modifier = sliderModifier.weight(1f),
                )
                Text(
                    valueLabel,
                    Modifier.widthIn(min = 44.dp, max = 52.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TsuyomiSlider(
                value = value,
                onValueChange = onValueChange,
                label = label,
                valueRange = valueRange,
                steps = steps,
                valueDescription = valueLabel,
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
            modifier = Modifier.fillMaxWidth().padding(vertical = TsuyomiSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TsuyomiSwitchVisual(checked = checked, enabled = true)
        }
    }
}
