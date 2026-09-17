/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.ui.components.InlineStatus
import org.tsuyomi.core.ui.components.SegmentedSelector
import org.tsuyomi.core.ui.components.SettingsGroup
import org.tsuyomi.core.ui.components.SettingsSectionHeader
import org.tsuyomi.core.ui.components.SettingsSwitchRow
import org.tsuyomi.core.ui.components.TsuyomiSegment
import org.tsuyomi.core.ui.components.TsuyomiSlider
import org.tsuyomi.core.ui.components.TsuyomiTextField
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
                value = preferences.foregroundColor,
                tag = "settings-reader-foreground-color",
                onValueChanged = { onPreferencesChanged(preferences.copy(foregroundColor = it)) },
            )
            ReaderColorOverrideRow(
                label = stringResource(R.string.settings_reader_background_color),
                value = preferences.backgroundColor,
                tag = "settings-reader-background-color",
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
            } else if (DEFAULTS_HEX_COLOR.matches(normalized)) {
                onValueChanged(normalized)
            }
        },
        label = label,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

private val DEFAULTS_HEX_COLOR = Regex("^#[0-9A-F]{6}$")
