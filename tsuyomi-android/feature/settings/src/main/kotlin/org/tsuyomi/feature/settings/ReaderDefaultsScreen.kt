/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.Dp
import org.tsuyomi.core.ui.components.LocalTsuyomiPagerMaxContentHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.TextFieldValue
import java.util.Locale
import kotlin.math.roundToInt
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.ui.components.InlineStatus
import org.tsuyomi.core.ui.components.PAGINATION_JUMP_THRESHOLD
import org.tsuyomi.core.ui.components.SegmentedSelector
import org.tsuyomi.core.ui.components.SettingsGroup
import org.tsuyomi.core.ui.components.SettingsSectionHeader
import org.tsuyomi.core.ui.components.SettingsSwitchRow
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.components.TsuyomiSegment
import org.tsuyomi.core.ui.components.TsuyomiSlider
import org.tsuyomi.core.ui.components.TsuyomiTextField
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.backup.PortableReaderPreferences

@Composable
fun ReaderDefaultsScreen(
    preferences: PortableReaderPreferences,
    environment: DisplayEnvironment,
    onPreferencesChanged: (PortableReaderPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val eInk = environment.effectiveProfile == DisplayProfile.EINK
    var selectedProjectKey by rememberSaveable {
        mutableStateOf(ReaderDefaultsProject.TYPOGRAPHY.key)
    }
    val selectedProject = ReaderDefaultsProject.values().first { it.key == selectedProjectKey }
    var focusedColorField by rememberSaveable { mutableStateOf<String?>(null) }
    val projectStateHolder = rememberSaveableStateHolder()
    val controlStateHolder = rememberSaveableStateHolder()
    val projectPagerState = rememberTsuyomiMeasuredPagerState(selectedProject.key)
    var foregroundDraft by rememberSaveable(
        preferences.foregroundColor,
        stateSaver = TextFieldValue.Saver,
    ) {
        mutableStateOf(TextFieldValue(preferences.foregroundColor.orEmpty()))
    }
    var backgroundDraft by rememberSaveable(
        preferences.backgroundColor,
        stateSaver = TextFieldValue.Saver,
    ) {
        mutableStateOf(TextFieldValue(preferences.backgroundColor.orEmpty()))
    }
    if (eInk) {
        EInkReaderDefaultsContent(
            modifier = modifier,
            preferences = preferences,
            selectedProject = selectedProject,
            onProjectSelected = { project ->
                selectedProjectKey = project.key
            },
            onPreferencesChanged = onPreferencesChanged,
            foregroundDraft = foregroundDraft,
            onForegroundDraftChanged = { foregroundDraft = it },
            backgroundDraft = backgroundDraft,
            onBackgroundDraftChanged = { backgroundDraft = it },
            focusedColorField = focusedColorField,
            onColorFieldFocused = { focusedColorField = it },
            projectPagerState = projectPagerState,
            projectStateHolder = projectStateHolder,
            controlStateHolder = controlStateHolder,
        )
        return
    }

    CenteredSettingsColumn(modifier) {
        SettingsSectionHeader(stringResource(R.string.settings_reader_section_typography))
        SettingsGroup {
            ReaderSliderRow(
                label = stringResource(R.string.settings_reader_font_size),
                valueLabel = "${(18.0 * (preferences.fontScale ?: 1.0)).toInt()}sp",
                value = (18.0 * (preferences.fontScale ?: 1.0)).toFloat(),
                valueRange = 12f..32f,
                steps = 19,
                onValueChange = { onPreferencesChanged(preferences.copy(fontScale = (it / 18f).toDouble())) },
            )
            ReaderSliderRow(
                label = stringResource(R.string.settings_reader_line_height),
                valueLabel = String.format(Locale.ROOT, "%.1f", preferences.lineHeight ?: 1.5),
                value = (preferences.lineHeight ?: 1.5).toFloat(),
                valueRange = 1.2f..2.2f,
                steps = 9,
                onValueChange = { onPreferencesChanged(preferences.copy(lineHeight = it.toDouble())) },
            )
            ReaderSliderRow(
                label = stringResource(R.string.settings_reader_margin),
                valueLabel = "${(preferences.horizontalMargin ?: 24.0).toInt()}dp",
                value = (preferences.horizontalMargin ?: 24.0).toFloat(),
                valueRange = 12f..40f,
                steps = 6,
                onValueChange = { onPreferencesChanged(preferences.copy(horizontalMargin = it.toDouble())) },
            )
            ReaderSliderRow(
                label = stringResource(R.string.settings_reader_paragraph_spacing),
                valueLabel = "${(preferences.paragraphSpacing ?: 12.0).toInt()}dp",
                value = (preferences.paragraphSpacing ?: 12.0).toFloat(),
                valueRange = 0f..32f,
                steps = 7,
                onValueChange = { onPreferencesChanged(preferences.copy(paragraphSpacing = it.toDouble())) },
            )
            Text(
                stringResource(R.string.settings_reader_font_family),
                modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md),
                style = MaterialTheme.typography.bodyLarge,
            )
            SegmentedSelector(
                options = listOf(
                    TsuyomiSegment("system", stringResource(R.string.settings_reader_font_family_system)),
                    TsuyomiSegment("sans", stringResource(R.string.settings_reader_font_family_sans)),
                    TsuyomiSegment("serif", stringResource(R.string.settings_reader_font_family_serif)),
                    TsuyomiSegment("monospace", stringResource(R.string.settings_reader_font_family_monospace)),
                ),
                selected = preferences.fontFamily ?: "system",
                onSelect = { onPreferencesChanged(preferences.copy(fontFamily = it)) },
                modifier = Modifier.padding(TsuyomiSpacing.Md),
            )
            Text(
                stringResource(R.string.settings_reader_font_weight),
                modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md),
                style = MaterialTheme.typography.bodyLarge,
            )
            SegmentedSelector(
                options = listOf(
                    TsuyomiSegment("400", stringResource(R.string.settings_reader_font_weight_regular)),
                    TsuyomiSegment("500", stringResource(R.string.settings_reader_font_weight_medium)),
                ),
                selected = (preferences.fontWeight ?: 400).toString(),
                onSelect = { onPreferencesChanged(preferences.copy(fontWeight = it.toInt())) },
                modifier = Modifier.padding(TsuyomiSpacing.Md),
            )
            ReaderSliderRow(
                label = stringResource(R.string.settings_reader_letter_spacing),
                valueLabel = String.format(Locale.ROOT, "%.2fsp", preferences.letterSpacing ?: 0.0),
                value = (preferences.letterSpacing ?: 0.0).toFloat(),
                valueRange = -0.05f..0.20f,
                steps = 24,
                onValueChange = { onPreferencesChanged(preferences.copy(letterSpacing = it.toDouble())) },
            )
            ReaderSliderRow(
                label = stringResource(R.string.settings_reader_first_line_indent),
                valueLabel = String.format(Locale.ROOT, "%.2fem", preferences.firstLineIndent ?: 0.0),
                value = (preferences.firstLineIndent ?: 0.0).toFloat(),
                valueRange = 0f..4f,
                steps = 15,
                onValueChange = { onPreferencesChanged(preferences.copy(firstLineIndent = it.toDouble())) },
            )
            ReaderSliderRow(
                label = stringResource(R.string.settings_reader_vertical_margin),
                valueLabel = "${(preferences.verticalMargin ?: 24.0).toInt()}dp",
                value = (preferences.verticalMargin ?: 24.0).toFloat(),
                valueRange = 0f..64f,
                steps = 15,
                onValueChange = { onPreferencesChanged(preferences.copy(verticalMargin = it.toDouble())) },
            )
            Text(
                stringResource(R.string.settings_reader_text_alignment),
                modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md),
                style = MaterialTheme.typography.bodyLarge,
            )
            SegmentedSelector(
                options = listOf(
                    TsuyomiSegment("start", stringResource(R.string.settings_reader_text_alignment_start)),
                    TsuyomiSegment("justify", stringResource(R.string.settings_reader_text_alignment_justify)),
                    TsuyomiSegment("center", stringResource(R.string.settings_reader_text_alignment_center)),
                    TsuyomiSegment("end", stringResource(R.string.settings_reader_text_alignment_end)),
                ),
                selected = preferences.textAlignment ?: "start",
                onSelect = { onPreferencesChanged(preferences.copy(textAlignment = it)) },
                modifier = Modifier.padding(TsuyomiSpacing.Md),
            )
            ReaderColorOverrideRow(
                label = stringResource(R.string.settings_reader_foreground_color),
                draft = foregroundDraft,
                onDraftChanged = { foregroundDraft = it },
                tag = "settings-reader-foreground-color",
                focusedColorField = focusedColorField,
                onColorFieldFocused = { focusedColorField = it },
                onValueChanged = { onPreferencesChanged(preferences.copy(foregroundColor = it)) },
            )
            ReaderColorOverrideRow(
                label = stringResource(R.string.settings_reader_background_color),
                draft = backgroundDraft,
                onDraftChanged = { backgroundDraft = it },
                tag = "settings-reader-background-color",
                focusedColorField = focusedColorField,
                onColorFieldFocused = { focusedColorField = it },
                onValueChanged = { onPreferencesChanged(preferences.copy(backgroundColor = it)) },
            )
        }

        SettingsSectionHeader(stringResource(R.string.settings_reader_section_page))
        SettingsGroup(modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md)) {
            SegmentedSelector(
                options = listOf(
                    TsuyomiSegment("scroll", stringResource(R.string.settings_reader_flow_scroll)),
                    TsuyomiSegment("paged", stringResource(R.string.settings_reader_flow_paged)),
                    TsuyomiSegment("dual", stringResource(R.string.settings_reader_flow_dual)),
                ),
                selected = preferences.flow ?: "scroll",
                onSelect = { onPreferencesChanged(preferences.copy(flow = it)) },
                enabled = !eInk,
                disabledReason = if (eInk) stringResource(R.string.settings_reader_flow_eink_constraint) else null,
                modifier = Modifier.padding(TsuyomiSpacing.Md),
            )
            SegmentedSelector(
                options = listOf(
                    TsuyomiSegment("paper", stringResource(R.string.settings_reader_theme_paper)),
                    TsuyomiSegment("warmGray", stringResource(R.string.settings_reader_theme_warm)),
                    TsuyomiSegment("nightInk", stringResource(R.string.settings_reader_theme_night)),
                    TsuyomiSegment("black", stringResource(R.string.settings_reader_theme_black)),
                    TsuyomiSegment("inkGreen", stringResource(R.string.settings_reader_theme_ink_green)),
                ),
                selected = preferences.theme ?: "paper",
                onSelect = { onPreferencesChanged(preferences.copy(theme = it)) },
                enabled = !eInk,
                disabledReason = if (eInk) stringResource(R.string.settings_reader_theme_eink_constraint) else null,
                modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
            )
        }
        if (eInk) {
            InlineStatus(
                text = stringResource(R.string.settings_reader_eink_effective),
                modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
            )
        }

        SettingsSectionHeader(stringResource(R.string.settings_reader_section_navigation))
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_reader_volume_paging),
                summary = stringResource(R.string.settings_reader_volume_paging_summary),
                checked = preferences.volumePaging ?: true,
                onCheckedChange = { onPreferencesChanged(preferences.copy(volumePaging = it)) },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_reader_progress),
                checked = preferences.progressVisible ?: true,
                onCheckedChange = { onPreferencesChanged(preferences.copy(progressVisible = it)) },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_reader_immersive),
                checked = preferences.immersive ?: false,
                onCheckedChange = { onPreferencesChanged(preferences.copy(immersive = it)) },
            )
        }

        SettingsSectionHeader(stringResource(R.string.settings_reader_section_device))
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_reader_keep_awake),
                checked = preferences.keepAwake ?: true,
                onCheckedChange = { onPreferencesChanged(preferences.copy(keepAwake = it)) },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_reader_lock_portrait),
                checked = preferences.lockPortrait ?: false,
                onCheckedChange = { onPreferencesChanged(preferences.copy(lockPortrait = it)) },
            )
        }
    }
}

private enum class ReaderDefaultsProject(val key: String, val labelRes: Int) {
    TYPOGRAPHY("typography", R.string.settings_reader_section_typography),
    PAGE("page", R.string.settings_reader_section_page),
    NAVIGATION("navigation", R.string.settings_reader_section_navigation),
    DEVICE("device", R.string.settings_reader_section_device),
}

private data class EInkReaderSettingItem(
    val key: String,
    val content: @Composable () -> Unit,
)

@Composable
private fun EInkReaderDefaultsContent(
    modifier: Modifier,
    preferences: PortableReaderPreferences,
    selectedProject: ReaderDefaultsProject,
    onProjectSelected: (ReaderDefaultsProject) -> Unit,
    onPreferencesChanged: (PortableReaderPreferences) -> Unit,
    foregroundDraft: TextFieldValue,
    onForegroundDraftChanged: (TextFieldValue) -> Unit,
    backgroundDraft: TextFieldValue,
    onBackgroundDraftChanged: (TextFieldValue) -> Unit,
    focusedColorField: String?,
    onColorFieldFocused: (String) -> Unit,
    projectPagerState: TsuyomiMeasuredPagerState,
    projectStateHolder: SaveableStateHolder,
    controlStateHolder: SaveableStateHolder,
) {
    val projects = ReaderDefaultsProject.values()
    val projectLabels = projects.map { stringResource(it.labelRes) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge
    val navigationWidth = remember(projectLabels, labelStyle, density) {
        val labelWidth = projectLabels.maxOf { textMeasurer.measure(it, style = labelStyle).size.width }
        with(density) {
            (labelWidth + (TsuyomiSpacing.Sm * 2 + 4.dp).roundToPx())
                .coerceAtLeast(48.dp.roundToPx()).toDp()
        }
    }
    val controls = readerDefaultsControls(
        project = selectedProject,
        preferences = preferences,
        onPreferencesChanged = onPreferencesChanged,
        foregroundDraft = foregroundDraft,
        onForegroundDraftChanged = onForegroundDraftChanged,
        backgroundDraft = backgroundDraft,
        onBackgroundDraftChanged = onBackgroundDraftChanged,
        focusedColorField = focusedColorField,
        onColorFieldFocused = onColorFieldFocused,
    )
    Row(
        modifier = modifier.fillMaxSize().padding(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
    ) {
        TsuyomiMeasuredPager(
            itemCount = projects.size,
            itemKey = { index -> projects[index].key },
            itemContent = { index ->
                val project = projects[index]
                val isSelected = project == selectedProject
                TsuyomiButton(
                    text = stringResource(project.labelRes),
                    onClick = { onProjectSelected(project) },
                    style = if (isSelected) TsuyomiButtonStyle.PRIMARY else TsuyomiButtonStyle.SECONDARY,
                    contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Sm, vertical = TsuyomiSpacing.Sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { selected = isSelected }
                        .testTag("settings-reader-project-${project.key}"),
                )
            },
            modifier = Modifier.width(navigationWidth).fillMaxHeight().testTag("settings-reader-project-pager"),
            state = projectPagerState,
            verticalSpacing = TsuyomiSpacing.Sm,
            pageControls = { page, canGoPrevious, canGoNext, onPrevious, onNext, pageCount, onFirst, onLast ->
                ReaderDefaultsPageControls(
                    page = page,
                    canGoPrevious = canGoPrevious,
                    canGoNext = canGoNext,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    pageCount = pageCount,
                    onFirst = onFirst,
                    onLast = onLast,
                    testTag = "settings-reader-project-pagination",
                )
            },
        )

        val controlsModifier = Modifier.weight(1f).fillMaxHeight().testTag("settings-reader-controls")
        projectStateHolder.SaveableStateProvider(selectedProject.key) {
            val controlsPagerState = rememberTsuyomiMeasuredPagerState(controls.firstOrNull()?.key)
            TsuyomiMeasuredPager(
                itemCount = controls.size,
                itemKey = { index -> controls[index].key },
                itemContent = { index ->
                    val item = controls[index]
                    controlStateHolder.SaveableStateProvider("${selectedProject.key}:${item.key}") {
                        Column(
                            modifier = Modifier.fillMaxWidth().testTag("settings-reader-control-item"),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().testTag(
                                    "settings-reader-control-item-${selectedProject.key}-${item.key}",
                                ),
                            ) {
                                item.content()
                            }
                        }
                    }
                },
                modifier = controlsModifier,
                state = controlsPagerState,
                verticalSpacing = TsuyomiSpacing.Md,
                pageControls = { page, canGoPrevious, canGoNext, onPrevious, onNext, pageCount, onFirst, onLast ->
                    ReaderDefaultsPageControls(
                        page = page,
                        canGoPrevious = canGoPrevious,
                        canGoNext = canGoNext,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        pageCount = pageCount,
                        onFirst = onFirst,
                        onLast = onLast,
                        testTag = "settings-reader-controls-pagination",
                    )
                },
            )
        }
    }
}

@Composable
private fun ReaderDefaultsPageControls(
    page: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    pageCount: Int?,
    onFirst: () -> Unit,
    onLast: () -> Unit,
    testTag: String,
) {
    val pageDescription = stringResource(org.tsuyomi.core.ui.R.string.coreui_pagination_current_page, page)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = TsuyomiSpacing.Sm).testTag(testTag),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
    ) {
        Text(
            text = page.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = pageDescription },
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Lg, androidx.compose.ui.Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm),
        ) {
            if (pageCount != null && pageCount > PAGINATION_JUMP_THRESHOLD) {
                TsuyomiButton(
                    text = stringResource(org.tsuyomi.core.ui.R.string.coreui_pagination_first),
                    onClick = onFirst,
                    enabled = canGoPrevious,
                    modifier = Modifier.testTag("$testTag-first"),
                    style = TsuyomiButtonStyle.SECONDARY,
                    contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Xs, vertical = TsuyomiSpacing.Sm),
                )
            }
            TsuyomiButton(
                text = stringResource(org.tsuyomi.core.ui.R.string.coreui_pagination_previous),
                onClick = onPrevious,
                enabled = canGoPrevious,
                modifier = Modifier.testTag("$testTag-previous"),
                style = TsuyomiButtonStyle.SECONDARY,
                contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Xs, vertical = TsuyomiSpacing.Sm),
            )
            TsuyomiButton(
                text = stringResource(org.tsuyomi.core.ui.R.string.coreui_pagination_next),
                onClick = onNext,
                enabled = canGoNext,
                modifier = Modifier.testTag("$testTag-next"),
                style = TsuyomiButtonStyle.SECONDARY,
                contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Xs, vertical = TsuyomiSpacing.Sm),
            )
            if (pageCount != null && pageCount > PAGINATION_JUMP_THRESHOLD) {
                TsuyomiButton(
                    text = stringResource(org.tsuyomi.core.ui.R.string.coreui_pagination_last),
                    onClick = onLast,
                    enabled = canGoNext,
                    modifier = Modifier.testTag("$testTag-last"),
                    style = TsuyomiButtonStyle.SECONDARY,
                    contentPadding = PaddingValues(horizontal = TsuyomiSpacing.Xs, vertical = TsuyomiSpacing.Sm),
                )
            }
        }
    }
}

@Composable
private fun readerDefaultsControls(
    project: ReaderDefaultsProject,
    preferences: PortableReaderPreferences,
    onPreferencesChanged: (PortableReaderPreferences) -> Unit,
    foregroundDraft: TextFieldValue,
    onForegroundDraftChanged: (TextFieldValue) -> Unit,
    backgroundDraft: TextFieldValue,
    onBackgroundDraftChanged: (TextFieldValue) -> Unit,
    focusedColorField: String?,
    onColorFieldFocused: (String) -> Unit,
): List<EInkReaderSettingItem> = when (project) {
    ReaderDefaultsProject.TYPOGRAPHY -> listOf(
        rangeControl(ReaderRangeField.FONT_SIZE, preferences, onPreferencesChanged),
        rangeControl(ReaderRangeField.LINE_HEIGHT, preferences, onPreferencesChanged),
        rangeControl(ReaderRangeField.HORIZONTAL_MARGIN, preferences, onPreferencesChanged),
        rangeControl(ReaderRangeField.PARAGRAPH_SPACING, preferences, onPreferencesChanged),
    ) + readerChoiceControls(ReaderChoiceField.FONT_FAMILY, preferences, onPreferencesChanged) +
        readerChoiceControls(ReaderChoiceField.FONT_WEIGHT, preferences, onPreferencesChanged) + listOf(
            rangeControl(ReaderRangeField.LETTER_SPACING, preferences, onPreferencesChanged),
            rangeControl(ReaderRangeField.FIRST_LINE_INDENT, preferences, onPreferencesChanged),
            rangeControl(ReaderRangeField.VERTICAL_MARGIN, preferences, onPreferencesChanged),
        ) + readerChoiceControls(ReaderChoiceField.TEXT_ALIGNMENT, preferences, onPreferencesChanged) + listOf(
            EInkReaderSettingItem("foreground-color") {
                ReaderColorOverrideRow(
                    label = stringResource(R.string.settings_reader_foreground_color),
                    draft = foregroundDraft,
                    onDraftChanged = onForegroundDraftChanged,
                    tag = "settings-reader-foreground-color",
                    focusedColorField = focusedColorField,
                    onColorFieldFocused = onColorFieldFocused,
                    onValueChanged = { onPreferencesChanged(preferences.copy(foregroundColor = it)) },
                )
            },
            EInkReaderSettingItem("background-color") {
                ReaderColorOverrideRow(
                    label = stringResource(R.string.settings_reader_background_color),
                    draft = backgroundDraft,
                    onDraftChanged = onBackgroundDraftChanged,
                    tag = "settings-reader-background-color",
                    focusedColorField = focusedColorField,
                    onColorFieldFocused = onColorFieldFocused,
                    onValueChanged = { onPreferencesChanged(preferences.copy(backgroundColor = it)) },
                )
            },
        )
    ReaderDefaultsProject.PAGE -> readerChoiceControls(
        ReaderChoiceField.FLOW,
        preferences,
        onPreferencesChanged,
    ) + listOf(
        textItem("flow-constraint") {
            TsuyomiMeasuredTextPager(
                text = stringResource(R.string.settings_reader_flow_eink_constraint),
                fillViewport = false,
                alwaysShowPageControls = false,
            )
        },
    ) + readerChoiceControls(
        ReaderChoiceField.THEME,
        preferences,
        onPreferencesChanged,
    ) + listOf(
        textItem("theme-constraint") {
            TsuyomiMeasuredTextPager(
                text = stringResource(R.string.settings_reader_theme_eink_constraint),
                fillViewport = false,
                alwaysShowPageControls = false,
            )
        },
        textItem("effective-constraints") {
            TsuyomiMeasuredTextPager(
                text = stringResource(R.string.settings_reader_eink_effective),
                fillViewport = false,
                alwaysShowPageControls = false,
            )
        },
    )
    ReaderDefaultsProject.NAVIGATION -> listOf(
        toggleControl(ReaderToggleField.VOLUME_PAGING, preferences, onPreferencesChanged),
        toggleControl(ReaderToggleField.PROGRESS, preferences, onPreferencesChanged),
        toggleControl(ReaderToggleField.IMMERSIVE, preferences, onPreferencesChanged),
    )
    ReaderDefaultsProject.DEVICE -> listOf(
        toggleControl(ReaderToggleField.KEEP_AWAKE, preferences, onPreferencesChanged),
        toggleControl(ReaderToggleField.LOCK_PORTRAIT, preferences, onPreferencesChanged),
    )
}

private enum class ReaderRangeField(
    val key: String,
    val labelRes: Int,
    val minimum: Double,
    val maximum: Double,
    val step: Double,
) {
    FONT_SIZE("font-size", R.string.settings_reader_font_size, 12.0, 32.0, 1.0),
    LINE_HEIGHT("line-height", R.string.settings_reader_line_height, 1.2, 2.2, 0.1),
    HORIZONTAL_MARGIN("horizontal-margin", R.string.settings_reader_margin, 12.0, 40.0, 4.0),
    PARAGRAPH_SPACING("paragraph-spacing", R.string.settings_reader_paragraph_spacing, 0.0, 32.0, 4.0),
    LETTER_SPACING("letter-spacing", R.string.settings_reader_letter_spacing, -0.05, 0.20, 0.01),
    FIRST_LINE_INDENT("first-line-indent", R.string.settings_reader_first_line_indent, 0.0, 4.0, 0.25),
    VERTICAL_MARGIN("vertical-margin", R.string.settings_reader_vertical_margin, 0.0, 64.0, 4.0),
    ;

    fun value(preferences: PortableReaderPreferences): Double = when (this) {
        FONT_SIZE -> 18.0 * (preferences.fontScale ?: 1.0)
        LINE_HEIGHT -> preferences.lineHeight ?: 1.5
        HORIZONTAL_MARGIN -> preferences.horizontalMargin ?: 24.0
        PARAGRAPH_SPACING -> preferences.paragraphSpacing ?: 12.0
        LETTER_SPACING -> preferences.letterSpacing ?: 0.0
        FIRST_LINE_INDENT -> preferences.firstLineIndent ?: 0.0
        VERTICAL_MARGIN -> preferences.verticalMargin ?: 24.0
    }


    fun update(preferences: PortableReaderPreferences, value: Double): PortableReaderPreferences = when (this) {
        FONT_SIZE -> preferences.copy(fontScale = value / 18.0)
        LINE_HEIGHT -> preferences.copy(lineHeight = value)
        HORIZONTAL_MARGIN -> preferences.copy(horizontalMargin = value)
        PARAGRAPH_SPACING -> preferences.copy(paragraphSpacing = value)
        LETTER_SPACING -> preferences.copy(letterSpacing = value)
        FIRST_LINE_INDENT -> preferences.copy(firstLineIndent = value)
        VERTICAL_MARGIN -> preferences.copy(verticalMargin = value)
    }

    fun display(value: Double): String = when (this) {
        FONT_SIZE -> "${value.roundToInt()}sp"
        LINE_HEIGHT -> String.format(Locale.ROOT, "%.1f", value)
        HORIZONTAL_MARGIN, PARAGRAPH_SPACING, VERTICAL_MARGIN -> "${value.roundToInt()}dp"
        LETTER_SPACING -> String.format(Locale.ROOT, "%.2fsp", value)
        FIRST_LINE_INDENT -> String.format(Locale.ROOT, "%.2fem", value)
    }
}

private data class ReaderChoiceOption(val key: String, val value: String, val labelRes: Int)

private enum class ReaderChoiceField(
    val key: String,
    val labelRes: Int,
    val disabledInEInk: Boolean,
    val options: List<ReaderChoiceOption>,
) {
    FONT_FAMILY(
        "font-family",
        R.string.settings_reader_font_family,
        false,
        listOf(
            ReaderChoiceOption("system", "system", R.string.settings_reader_font_family_system),
            ReaderChoiceOption("sans", "sans", R.string.settings_reader_font_family_sans),
            ReaderChoiceOption("serif", "serif", R.string.settings_reader_font_family_serif),
            ReaderChoiceOption("monospace", "monospace", R.string.settings_reader_font_family_monospace),
        ),
    ),
    FONT_WEIGHT(
        "font-weight",
        R.string.settings_reader_font_weight,
        false,
        listOf(
            ReaderChoiceOption("regular", "400", R.string.settings_reader_font_weight_regular),
            ReaderChoiceOption("medium", "500", R.string.settings_reader_font_weight_medium),
        ),
    ),
    TEXT_ALIGNMENT(
        "text-alignment",
        R.string.settings_reader_text_alignment,
        false,
        listOf(
            ReaderChoiceOption("start", "start", R.string.settings_reader_text_alignment_start),
            ReaderChoiceOption("justify", "justify", R.string.settings_reader_text_alignment_justify),
            ReaderChoiceOption("center", "center", R.string.settings_reader_text_alignment_center),
            ReaderChoiceOption("end", "end", R.string.settings_reader_text_alignment_end),
        ),
    ),
    FLOW(
        "flow",
        R.string.settings_reader_flow_label,
        true,
        listOf(
            ReaderChoiceOption("scroll", "scroll", R.string.settings_reader_flow_scroll),
            ReaderChoiceOption("paged", "paged", R.string.settings_reader_flow_paged),
            ReaderChoiceOption("dual", "dual", R.string.settings_reader_flow_dual),
        ),
    ),
    THEME(
        "theme",
        R.string.settings_reader_theme_label,
        true,
        listOf(
            ReaderChoiceOption("paper", "paper", R.string.settings_reader_theme_paper),
            ReaderChoiceOption("warm", "warmGray", R.string.settings_reader_theme_warm),
            ReaderChoiceOption("night", "nightInk", R.string.settings_reader_theme_night),
            ReaderChoiceOption("black", "black", R.string.settings_reader_theme_black),
            ReaderChoiceOption("ink-green", "inkGreen", R.string.settings_reader_theme_ink_green),
        ),
    ),
    ;

    fun value(preferences: PortableReaderPreferences): String = when (this) {
        FONT_FAMILY -> preferences.fontFamily ?: "system"
        FONT_WEIGHT -> (preferences.fontWeight ?: 400).toString()
        TEXT_ALIGNMENT -> preferences.textAlignment ?: "start"
        FLOW -> preferences.flow ?: "scroll"
        THEME -> preferences.theme ?: "paper"
    }

    fun update(preferences: PortableReaderPreferences, value: String): PortableReaderPreferences = when (this) {
        FONT_FAMILY -> preferences.copy(fontFamily = value)
        FONT_WEIGHT -> preferences.copy(fontWeight = value.toInt())
        TEXT_ALIGNMENT -> preferences.copy(textAlignment = value)
        FLOW -> preferences.copy(flow = value)
        THEME -> preferences.copy(theme = value)
    }
}

@Composable
private fun readerChoiceControls(
    field: ReaderChoiceField,
    preferences: PortableReaderPreferences,
    onPreferencesChanged: (PortableReaderPreferences) -> Unit,
): List<EInkReaderSettingItem> {
    val currentValue = field.value(preferences)
    val fieldLabel = stringResource(field.labelRes)
    val currentOption = field.options.firstOrNull { it.value == currentValue }
    val selectedLabel = currentOption?.let { stringResource(it.labelRes) } ?: currentValue
    return listOf(EInkReaderSettingItem(field.key) {
        Column(Modifier.heightIn(max = LocalTsuyomiPagerMaxContentHeight.current ?: Dp.Infinity),
            verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Sm)) {
            Text(fieldLabel, style = MaterialTheme.typography.bodyLarge)
            InlineStatus(text = stringResource(R.string.settings_reader_current_value, fieldLabel, selectedLabel))
            TsuyomiMeasuredPager(
                itemCount = field.options.size, itemKey = { field.options[it].key },
                wrapItems = true, fillViewport = false,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                horizontalSpacing = TsuyomiSpacing.Sm, verticalSpacing = TsuyomiSpacing.Sm,
                itemContent = { index ->
                    val option = field.options[index]
                    val isSelected = currentValue == option.value
                    TsuyomiButton(
                        text = stringResource(option.labelRes),
                        onClick = { onPreferencesChanged(field.update(preferences, option.value)) },
                        modifier = Modifier.semantics { selected = isSelected }
                            .testTag("settings-reader-choice-${field.key}-${option.key}"),
                        style = if (isSelected) TsuyomiButtonStyle.PRIMARY else TsuyomiButtonStyle.SECONDARY,
                        enabled = !field.disabledInEInk,
                    )
                },
            )
        }
    })
}

private fun rangeControl(
    field: ReaderRangeField,
    preferences: PortableReaderPreferences,
    onPreferencesChanged: (PortableReaderPreferences) -> Unit,
) = EInkReaderSettingItem(field.key) {
    ReaderRangeSettingControl(field, preferences, onPreferencesChanged)
}

@Composable
private fun ReaderRangeSettingControl(
    field: ReaderRangeField,
    preferences: PortableReaderPreferences,
    onPreferencesChanged: (PortableReaderPreferences) -> Unit,
) {
    val label = stringResource(field.labelRes)
    val currentValue = field.value(preferences)
    val valueLabel = field.display(currentValue)
    Column(verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(valueLabel, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("settings-reader-value-${field.key}"))
        TsuyomiSlider(
            value = currentValue.toFloat(),
            onValueChange = { value ->
                val index = ((value.toDouble() - field.minimum) / field.step).roundToInt()
                val target = (field.minimum + index * field.step).coerceIn(field.minimum, field.maximum)
                onPreferencesChanged(field.update(preferences, target))
            },
            label = label,
            valueRange = field.minimum.toFloat()..field.maximum.toFloat(),
            steps = ((field.maximum - field.minimum) / field.step).roundToInt() - 1,
            valueDescription = valueLabel,
            modifier = Modifier.testTag("settings-reader-slider-${field.key}"),
        )
    }
}

private enum class ReaderToggleField(
    val key: String,
    val titleRes: Int,
    val summaryRes: Int? = null,
) {
    VOLUME_PAGING("volume-paging", R.string.settings_reader_volume_paging, R.string.settings_reader_volume_paging_summary),
    PROGRESS("progress", R.string.settings_reader_progress),
    IMMERSIVE("immersive", R.string.settings_reader_immersive),
    KEEP_AWAKE("keep-awake", R.string.settings_reader_keep_awake),
    LOCK_PORTRAIT("lock-portrait", R.string.settings_reader_lock_portrait),
    ;

    fun value(preferences: PortableReaderPreferences): Boolean = when (this) {
        VOLUME_PAGING -> preferences.volumePaging ?: true
        PROGRESS -> preferences.progressVisible ?: true
        IMMERSIVE -> preferences.immersive ?: false
        KEEP_AWAKE -> preferences.keepAwake ?: true
        LOCK_PORTRAIT -> preferences.lockPortrait ?: false
    }

    fun update(preferences: PortableReaderPreferences, value: Boolean): PortableReaderPreferences = when (this) {
        VOLUME_PAGING -> preferences.copy(volumePaging = value)
        PROGRESS -> preferences.copy(progressVisible = value)
        IMMERSIVE -> preferences.copy(immersive = value)
        KEEP_AWAKE -> preferences.copy(keepAwake = value)
        LOCK_PORTRAIT -> preferences.copy(lockPortrait = value)
    }
}

private fun toggleControl(
    field: ReaderToggleField,
    preferences: PortableReaderPreferences,
    onPreferencesChanged: (PortableReaderPreferences) -> Unit,
) = EInkReaderSettingItem(field.key) {
    SettingsSwitchRow(
        title = stringResource(field.titleRes),
        summary = field.summaryRes?.let { stringResource(it) },
        checked = field.value(preferences),
        onCheckedChange = { onPreferencesChanged(field.update(preferences, it)) },
    )
}

private fun textItem(key: String, content: @Composable () -> Unit) = EInkReaderSettingItem(key, content)

@Composable
private fun ReaderSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        TsuyomiSlider(
            value = value,
            onValueChange = onValueChange,
            label = label,
            valueRange = valueRange,
            steps = steps,
            valueDescription = valueLabel,
        )
        Text(valueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReaderColorOverrideRow(
    label: String,
    draft: TextFieldValue,
    onDraftChanged: (TextFieldValue) -> Unit,
    tag: String,
    focusedColorField: String?,
    onColorFieldFocused: (String) -> Unit,
    onValueChanged: (String?) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var isPlaced by remember { mutableStateOf(false) }
    val restoreFocus = focusedColorField == tag
    LaunchedEffect(restoreFocus, isPlaced) {
        if (restoreFocus && isPlaced) focusRequester.requestFocus()
    }
    TsuyomiTextField(
        value = draft,
        onValueChange = { candidate ->
            val normalized = candidate.copy(text = candidate.text.uppercase(Locale.ROOT))
            onDraftChanged(normalized)
            when {
                normalized.text.isEmpty() -> onValueChanged(null)
                DEFAULTS_HEX_COLOR.matches(normalized.text) -> onValueChanged(normalized.text)
            }
        },
        label = label,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onColorFieldFocused(tag) }
            .onGloballyPositioned { if (!isPlaced) isPlaced = true }
            .testTag(tag),
    )
}


private val DEFAULTS_HEX_COLOR = Regex("^#[0-9A-F]{6}$")

