/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.reader.ui

import androidx.activity.compose.BackHandler
import org.tsuyomi.core.display.displayGrayscaleLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.Dp
import org.tsuyomi.core.ui.components.LocalTsuyomiPagerMaxContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import org.tsuyomi.core.ui.components.tsuyomiAnimateContentSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
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
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.selected

private enum class ReaderSettingsProject(
    val key: String,
    val labelResource: Int,
    val controlKeys: List<String>,
) {
    TYPOGRAPHY("project:typography", R.string.reader_typography_group, listOf(
        "font-size", "line-spacing", "horizontal-margin", "paragraph-spacing",
        "font-family", "font-weight", "letter-spacing", "first-line-indent", "vertical-margin",
        "text-alignment",
        "foreground-color", "background-color",
    )),
    PAGE("project:page", R.string.reader_page_group, listOf(
        "flow", "theme",
    )),
    NAVIGATION("project:navigation", R.string.reader_navigation_group, listOf("progress", "volume", "immersive")),
    DEVICE("project:device", R.string.reader_device_group, listOf("keep-awake", "lock-portrait")),
}

private class ReaderSettingsPagerStates(
    val projects: TsuyomiMeasuredPagerState,
    private val controls: Map<ReaderSettingsProject, TsuyomiMeasuredPagerState>,
    val foregroundDraft: MutableState<TextFieldValue>,
    val backgroundDraft: MutableState<TextFieldValue>,
) {
    fun controls(project: ReaderSettingsProject): TsuyomiMeasuredPagerState = controls.getValue(project)
}

@Composable
private fun rememberReaderSettingsPagerStates(
    foregroundColor: String?,
    backgroundColor: String?,
): ReaderSettingsPagerStates {
    val projects = rememberTsuyomiMeasuredPagerState(ReaderSettingsProject.TYPOGRAPHY.key)
    val typography = rememberTsuyomiMeasuredPagerState()
    val page = rememberTsuyomiMeasuredPagerState()
    val navigation = rememberTsuyomiMeasuredPagerState()
    val device = rememberTsuyomiMeasuredPagerState()
    val foregroundDraft = rememberSaveable(foregroundColor, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(foregroundColor.orEmpty()))
    }
    val backgroundDraft = rememberSaveable(backgroundColor, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(backgroundColor.orEmpty()))
    }
    return remember(projects, typography, page, navigation, device, foregroundDraft, backgroundDraft) {
        ReaderSettingsPagerStates(
            projects,
            mapOf(
                ReaderSettingsProject.TYPOGRAPHY to typography,
                ReaderSettingsProject.PAGE to page,
                ReaderSettingsProject.NAVIGATION to navigation,
                ReaderSettingsProject.DEVICE to device,
            ),
            foregroundDraft,
            backgroundDraft,
        )
    }
}

/** Both Reader sidebars use their rendered button typography, not the window's width fraction. */
@Composable
internal fun readerNavigationWidth(labels: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge
    return remember(labels, style, density) {
        val labelWidth = labels.maxOfOrNull { measurer.measure(it, style = style).size.width } ?: 0
        with(density) {
            (labelWidth + (TsuyomiSpacing.Sm * 2 + 4.dp).roundToPx())
                .coerceAtLeast(48.dp.roundToPx()).toDp()
        }
    }
}

@Composable
internal fun ReaderSettingsSheet(
    settingsIdentity: String,
    state: ReaderSettingsUiState,
    effectiveFlow: ReaderFlow,
    dualPageEligible: Boolean,
    hasFlowOverride: Boolean,
    onAction: (ReaderSettingsAction) -> Unit,
    onFollowGlobalFlow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedProject = key(settingsIdentity) {
        rememberSaveable { mutableStateOf(ReaderSettingsProject.TYPOGRAPHY) }
    }
    val pagerStates = key(settingsIdentity) {
        rememberReaderSettingsPagerStates(state.foregroundColor, state.backgroundColor)
    }
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        )) {
            Surface(Modifier.fillMaxSize().testTag("reader-settings-page").displayGrayscaleLayer()) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    TsuyomiTopBar(title = stringResource(R.string.reader_all_settings), onNavigateUp = onDismiss)
                    ReaderSettingsContent(
                        state = state,
                        effectiveFlow = effectiveFlow,
                        dualPageEligible = dualPageEligible,
                        hasFlowOverride = hasFlowOverride,
                        onAction = onAction,
                        onFollowGlobalFlow = onFollowGlobalFlow,
                        expanded = true,
                        onOpenExpanded = {},
                        selectedProject = selectedProject.value,
                        onSelectProject = { selectedProject.value = it },
                        pagerStates = pagerStates,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
        return
    }
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
            selectedProject = selectedProject.value,
            onSelectProject = { selectedProject.value = it },
            pagerStates = pagerStates,
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
    selectedProject: ReaderSettingsProject,
    onSelectProject: (ReaderSettingsProject) -> Unit,
    pagerStates: ReaderSettingsPagerStates,
    modifier: Modifier,
) {
    if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
        val navigationWidth = readerNavigationWidth(ReaderSettingsProject.entries.map { stringResource(it.labelResource) })
        val controlKeys = remember(selectedProject, hasFlowOverride) {
            if (selectedProject == ReaderSettingsProject.PAGE && hasFlowOverride) {
                selectedProject.controlKeys + "follow-global"
            } else {
                selectedProject.controlKeys
            }
        }
        Row(
            modifier.fillMaxSize().padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm)
                .testTag("reader-eink-settings-content"),
            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Md),
        ) {
            TsuyomiMeasuredPager(
                itemCount = ReaderSettingsProject.entries.size,
                itemKey = { ReaderSettingsProject.entries[it].key },
                itemContent = { index ->
                    val project = ReaderSettingsProject.entries[index]
                    val selected = selectedProject == project
                    TsuyomiButton(
                        text = stringResource(project.labelResource),
                        onClick = { onSelectProject(project) },
                        modifier = Modifier.fillMaxWidth()
                            .semantics {
                                this.selected = selected
                                stateDescription = if (selected) "当前设置项目" else "打开设置项目"
                            }
                            .testTag("reader-eink-project-${project.name.lowercase(Locale.ROOT)}"),
                        style = if (selected) TsuyomiButtonStyle.PRIMARY else TsuyomiButtonStyle.SECONDARY,
                        contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Sm),
                    )
                },
                state = pagerStates.projects,
                verticalSpacing = TsuyomiSpacing.Sm,
                modifier = Modifier.width(navigationWidth).fillMaxHeight()
                    .testTag("reader-eink-settings-navigation"),
            )
            Column(
                Modifier.weight(1f).fillMaxHeight().testTag("reader-eink-project-content"),
                verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
            ) {
                Text(stringResource(selectedProject.labelResource), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                TsuyomiMeasuredPager(
                    itemCount = controlKeys.size,
                    itemKey = { controlKeys[it] },
                    itemContent = { index ->
                        ReaderEInkSettingsControl(
                            key = controlKeys[index],
                            state = state,
                            effectiveFlow = effectiveFlow,
                            onAction = onAction,
                            onFollowGlobalFlow = onFollowGlobalFlow,
                            foregroundDraft = pagerStates.foregroundDraft,
                            backgroundDraft = pagerStates.backgroundDraft,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("reader-eink-setting-controls"),
                    state = pagerStates.controls(selectedProject),
                    verticalSpacing = TsuyomiSpacing.Md,
                )
            }
        }
        return
    }
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
                        ReaderTypographyDetails(state, onAction, pagerStates.foregroundDraft, pagerStates.backgroundDraft)
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
private fun ReaderEInkSettingsControl(
    key: String,
    state: ReaderSettingsUiState,
    effectiveFlow: ReaderFlow,
    onAction: (ReaderSettingsAction) -> Unit,
    onFollowGlobalFlow: () -> Unit,
    foregroundDraft: MutableState<TextFieldValue>,
    backgroundDraft: MutableState<TextFieldValue>,
) {
    when (key) {
        "font-size" -> ReaderSliderSetting(
            label = stringResource(R.string.reader_font_size), valueLabel = "${state.fontSize.toInt()}sp",
            value = state.fontSize, valueRange = 12f..32f, steps = 19, eInk = true,
            eInkTag = "reader-eink-font-size",
            onValueChange = { onAction(ReaderSettingsAction.FontSize(it)) },
        )
        "line-spacing" -> ReaderSliderSetting(
            label = stringResource(R.string.reader_line_spacing), valueLabel = String.format(Locale.ROOT, "%.1f", state.lineHeight),
            value = state.lineHeight, valueRange = 1.2f..2.2f, steps = 9, eInk = true,
            eInkTag = "reader-eink-line-spacing",
            onValueChange = { onAction(ReaderSettingsAction.LineHeight(it)) },
        )
        "horizontal-margin" -> ReaderSliderSetting(
            label = stringResource(R.string.reader_margin), valueLabel = "${state.horizontalMargin.toInt()}dp",
            value = state.horizontalMargin, valueRange = 12f..40f, steps = 6, eInk = true,
            eInkTag = "reader-eink-horizontal-margin",
            onValueChange = { onAction(ReaderSettingsAction.HorizontalMargin(it)) },
        )
        "paragraph-spacing" -> ReaderSliderSetting(
            label = stringResource(R.string.reader_paragraph_spacing), valueLabel = "${state.paragraphSpacing.toInt()}dp",
            value = state.paragraphSpacing, valueRange = 0f..32f, steps = 7, eInk = true,
            eInkTag = "reader-eink-paragraph-spacing",
            onValueChange = { onAction(ReaderSettingsAction.ParagraphSpacing(it)) },
        )
        "letter-spacing" -> ReaderSliderSetting(
            label = stringResource(R.string.reader_letter_spacing), valueLabel = String.format(Locale.ROOT, "%.2fsp", state.letterSpacing),
            value = state.letterSpacing, valueRange = -0.05f..0.20f, steps = 24, eInk = true,
            eInkTag = "reader-eink-letter-spacing",
            onValueChange = { onAction(ReaderSettingsAction.LetterSpacing(it)) },
        )
        "first-line-indent" -> ReaderSliderSetting(
            label = stringResource(R.string.reader_first_line_indent), valueLabel = String.format(Locale.ROOT, "%.2fem", state.firstLineIndent),
            value = state.firstLineIndent, valueRange = 0f..4f, steps = 15, eInk = true,
            eInkTag = "reader-eink-first-line-indent",
            onValueChange = { onAction(ReaderSettingsAction.FirstLineIndent(it)) },
        )
        "vertical-margin" -> ReaderSliderSetting(
            label = stringResource(R.string.reader_vertical_margin), valueLabel = "${state.verticalMargin.toInt()}dp",
            value = state.verticalMargin, valueRange = 0f..64f, steps = 15, eInk = true,
            eInkTag = "reader-eink-vertical-margin",
            onValueChange = { onAction(ReaderSettingsAction.VerticalMargin(it)) },
        )
        "foreground-color" -> ReaderColorOverrideSetting(
            label = stringResource(R.string.reader_foreground_color),
            tag = "reader-foreground-color", draftState = foregroundDraft,
            onValueChanged = { onAction(ReaderSettingsAction.ForegroundColor(it)) },
        )
        "background-color" -> ReaderColorOverrideSetting(
            label = stringResource(R.string.reader_background_color),
            tag = "reader-background-color", draftState = backgroundDraft,
            onValueChanged = { onAction(ReaderSettingsAction.BackgroundColor(it)) },
        )
        "follow-global" -> TsuyomiButton(
            text = stringResource(R.string.reader_follow_global_default), onClick = onFollowGlobalFlow,
            style = TsuyomiButtonStyle.SECONDARY, modifier = Modifier.testTag("reader-follow-global-flow"),
        )
        "progress" -> ReaderSwitchSetting(
            title = stringResource(R.string.reader_progress_info), checked = state.progressVisible,
            onCheckedChange = { onAction(ReaderSettingsAction.ProgressVisible(it)) },
            eInk = true, eInkTag = "reader-eink-progress",
        )
        "volume" -> ReaderSwitchSetting(
            title = stringResource(R.string.reader_volume_paging), checked = state.volumePaging,
            onCheckedChange = { onAction(ReaderSettingsAction.VolumePaging(it)) },
            eInk = true, eInkTag = "reader-eink-volume-paging",
        )
        "immersive" -> ReaderSwitchSetting(
            title = stringResource(R.string.reader_immersive), checked = state.immersive,
            onCheckedChange = { onAction(ReaderSettingsAction.Immersive(it)) },
            eInk = true, eInkTag = "reader-eink-immersive",
        )
        "keep-awake" -> ReaderSwitchSetting(
            title = stringResource(R.string.reader_keep_awake), checked = state.keepAwake,
            onCheckedChange = { onAction(ReaderSettingsAction.KeepAwake(it)) },
            eInk = true, eInkTag = "reader-eink-keep-awake",
        )
        "lock-portrait" -> ReaderSwitchSetting(
            title = stringResource(R.string.reader_lock_portrait), checked = state.lockPortrait,
            onCheckedChange = { onAction(ReaderSettingsAction.LockPortrait(it)) },
            eInk = true, eInkTag = "reader-eink-lock-portrait",
        )
        "font-family" -> ReaderEInkChoiceGroup(
            label = stringResource(R.string.reader_font_family), currentValue = state.fontFamily,
            options = listOf("system" to stringResource(R.string.reader_font_family_system),
                "sans" to stringResource(R.string.reader_font_family_sans),
                "serif" to stringResource(R.string.reader_font_family_serif),
                "monospace" to stringResource(R.string.reader_font_family_monospace)),
            tag = key, onSelect = { onAction(ReaderSettingsAction.FontFamily(it)) },
        )
        "font-weight" -> ReaderEInkChoiceGroup(
            label = stringResource(R.string.reader_font_weight), currentValue = state.fontWeight.toString(),
            options = listOf("400" to stringResource(R.string.reader_font_weight_regular),
                "500" to stringResource(R.string.reader_font_weight_medium)),
            tag = key, onSelect = { onAction(ReaderSettingsAction.FontWeight(it.toInt())) },
        )
        "text-alignment" -> ReaderEInkChoiceGroup(
            label = stringResource(R.string.reader_text_alignment), currentValue = state.textAlignment,
            options = listOf("start" to stringResource(R.string.reader_text_alignment_start),
                "justify" to stringResource(R.string.reader_text_alignment_justify),
                "center" to stringResource(R.string.reader_text_alignment_center),
                "end" to stringResource(R.string.reader_text_alignment_end)),
            tag = key, onSelect = { onAction(ReaderSettingsAction.TextAlignment(it)) },
        )
        "flow" -> Column(Modifier.heightIn(max = LocalTsuyomiPagerMaxContentHeight.current ?: Dp.Infinity),
            verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs)) {
            ReaderEInkChoiceGroup(
                label = stringResource(R.string.reader_page_group), currentValue = effectiveFlow.name,
                options = listOf(ReaderFlow.PAGED.name to ReaderFlow.PAGED.label, ReaderFlow.DUAL.name to ReaderFlow.DUAL.label),
                tag = key, onSelect = { onAction(ReaderSettingsAction.Flow(ReaderFlow.valueOf(it))) },
                modifier = Modifier.weight(1f, fill = false),
            )
            if (state.flow == ReaderFlow.DUAL && effectiveFlow == ReaderFlow.PAGED) {
                Text(stringResource(R.string.reader_dual_effective_paged), style = MaterialTheme.typography.bodySmall)
            }
        }
        "theme" -> ReaderEInkChoiceGroup(
            label = stringResource(R.string.reader_theme_label), currentValue = state.theme.name,
            options = ReaderTheme.entries.map { it.name to stringResource(it.labelResource()) },
            tag = key, onSelect = { onAction(ReaderSettingsAction.Theme(ReaderTheme.valueOf(it))) },
        )
        else -> error("Unknown E-ink Reader setting: $key")
    }
}

@Composable
private fun ReaderEInkChoiceGroup(
    label: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    tag: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.heightIn(max = LocalTsuyomiPagerMaxContentHeight.current ?: Dp.Infinity)
        .testTag("reader-eink-setting-$tag"), verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.reader_setting_current_value, options.firstOrNull { it.first == currentValue }?.second ?: currentValue),
            style = MaterialTheme.typography.bodySmall)
        TsuyomiMeasuredPager(
            itemCount = options.size, itemKey = { options[it].first }, wrapItems = true,
            fillViewport = false, modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            horizontalSpacing = TsuyomiSpacing.Sm, verticalSpacing = TsuyomiSpacing.Sm,
            itemContent = { index ->
                val (optionKey, optionLabel) = options[index]
                TsuyomiToggleChip(
                    text = optionLabel, selected = optionKey == currentValue,
                    stateDescription = stringResource(if (optionKey == currentValue) R.string.reader_option_selected else R.string.reader_option_not_selected),
                    onClick = { onSelect(optionKey) }, modifier = Modifier.testTag("reader-eink-option-${optionKey.lowercase(Locale.ROOT)}"),
                )
            },
        )
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
    foregroundDraft: MutableState<TextFieldValue>,
    backgroundDraft: MutableState<TextFieldValue>,
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
            tag = "reader-foreground-color",
            draftState = foregroundDraft,
            onValueChanged = { onAction(ReaderSettingsAction.ForegroundColor(it)) },
        )
        ReaderColorOverrideSetting(
            label = stringResource(R.string.reader_background_color),
            tag = "reader-background-color",
            draftState = backgroundDraft,
            onValueChanged = { onAction(ReaderSettingsAction.BackgroundColor(it)) },
        )
    }
}

@Composable
private fun ReaderColorOverrideSetting(
    label: String,
    tag: String,
    draftState: MutableState<TextFieldValue>,
    onValueChanged: (String?) -> Unit,
) {
    TsuyomiTextField(
        value = draftState.value,
        onValueChange = { candidate ->
            val normalized = candidate.copy(text = candidate.text.uppercase(Locale.ROOT))
            draftState.value = normalized
            if (normalized.text.isEmpty()) {
                onValueChanged(null)
            } else if (HEX_COLOR.matches(normalized.text)) {
                onValueChanged(normalized.text)
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
    eInk: Boolean = false,
    eInkTag: String? = null,
) {
    val tag = if (eInk) eInkTag?.let { "$it-slider" } else sliderTag
    val sliderModifier = if (tag == null) Modifier else Modifier.testTag(tag)
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
    eInk: Boolean = false,
    eInkTag: String? = null,
) {
    if (eInk) {
        val stateLabel = stringResource(if (checked) R.string.reader_setting_enabled else R.string.reader_setting_disabled)
        TsuyomiButton(
            text = "$title · $stateLabel",
            onClick = { onCheckedChange(!checked) },
            modifier = Modifier.semantics {
                selected = checked
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                stateDescription = stateLabel
            }.testTag(eInkTag ?: "reader-eink-setting-toggle"),
            style = if (checked) TsuyomiButtonStyle.PRIMARY else TsuyomiButtonStyle.SECONDARY,
        )
        return
    }
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
