/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        }

        SettingsSectionHeader(stringResource(R.string.settings_reader_section_page))
        SettingsGroup(modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md)) {
            SegmentedSelector(
                options = listOf(
                    TsuyomiSegment("scroll", stringResource(R.string.settings_reader_flow_scroll)),
                    TsuyomiSegment("paged", stringResource(R.string.settings_reader_flow_paged)),
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(TsuyomiSpacing.Xs),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
