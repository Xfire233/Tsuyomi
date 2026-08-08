/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.ui.components.InfoBanner
import org.tsuyomi.core.ui.components.InlineStatus
import org.tsuyomi.core.ui.components.SegmentedSelector
import org.tsuyomi.core.ui.components.SettingsActionRow
import org.tsuyomi.core.ui.components.SettingsSectionHeader
import org.tsuyomi.core.ui.components.SettingsSwitchRow
import org.tsuyomi.core.ui.components.TsuyomiSegment
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

/** A durable write that failed; surfaced as recoverable state with a stable id. */
data class DisplayWriteFailure(val id: Int)

/** Everything the display settings screen renders; derived from the root display environment. */
data class DisplaySettingsUiState(
    val environment: DisplayEnvironment,
    val writeFailure: DisplayWriteFailure? = null,
)

/** Real callbacks into the shared display controller; every visible control uses one of these. */
class DisplaySettingsActions(
    val onDisplayPreferenceChange: (DisplayPreference) -> Unit,
    val onColorSchemePreferenceChange: (ColorSchemePreference) -> Unit,
    val onDynamicColorEnabledChange: (Boolean) -> Unit,
    val onRefreshNow: () -> Unit,
    val onRetryWrite: () -> Unit,
    val onAcknowledgeWriteFailure: () -> Unit,
)

/**
 * Display settings. Persisted values drive every control's state; effective values are only ever
 * shown as explanatory status text. Content is capped at 560dp and centered on wide windows.
 */
@Composable
fun DisplaySettingsScreen(
    state: DisplaySettingsUiState,
    actions: DisplaySettingsActions,
    modifier: Modifier = Modifier,
) {
    val environment = state.environment
    val preferences = environment.preferences
    val eInk = environment.effectiveProfile == DisplayProfile.EINK

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TsuyomiSpacing.Md)
                .padding(bottom = TsuyomiSpacing.Lg),
        ) {
            val writeFailure = state.writeFailure
            if (writeFailure != null) {
                InfoBanner(
                    title = stringResource(R.string.settings_write_failure_title),
                    message = stringResource(R.string.settings_write_failure_message),
                    primaryActionLabel = stringResource(org.tsuyomi.core.ui.R.string.coreui_retry),
                    onPrimaryAction = actions.onRetryWrite,
                    dismissLabel = stringResource(R.string.settings_write_failure_acknowledge),
                    onDismiss = actions.onAcknowledgeWriteFailure,
                    modifier = Modifier.padding(top = TsuyomiSpacing.Md),
                )
            }

            SettingsSectionHeader(
                title = stringResource(R.string.settings_display_section_profile),
            )
            SegmentedSelector(
                options = listOf(
                    TsuyomiSegment(
                        DisplayPreference.AUTO,
                        stringResource(R.string.settings_display_profile_auto),
                    ),
                    TsuyomiSegment(
                        DisplayPreference.STANDARD,
                        stringResource(R.string.settings_display_profile_standard),
                    ),
                    TsuyomiSegment(
                        DisplayPreference.EINK,
                        stringResource(R.string.settings_display_profile_eink),
                    ),
                ),
                selected = preferences.displayPreference,
                onSelect = actions.onDisplayPreferenceChange,
            )
            InlineStatus(
                text = displayStatusText(environment),
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
            )

            SettingsSectionHeader(
                title = stringResource(R.string.settings_display_section_appearance),
            )
            SegmentedSelector(
                options = listOf(
                    TsuyomiSegment(
                        ColorSchemePreference.SYSTEM,
                        stringResource(R.string.settings_display_theme_system),
                    ),
                    TsuyomiSegment(
                        ColorSchemePreference.LIGHT,
                        stringResource(R.string.settings_display_theme_light),
                    ),
                    TsuyomiSegment(
                        ColorSchemePreference.DARK,
                        stringResource(R.string.settings_display_theme_dark),
                    ),
                ),
                selected = preferences.colorSchemePreference,
                onSelect = actions.onColorSchemePreferenceChange,
                enabled = !eInk,
                disabledReason = if (eInk) {
                    stringResource(R.string.settings_display_theme_disabled_eink)
                } else {
                    null
                },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_display_dynamic_title),
                summary = stringResource(R.string.settings_display_dynamic_summary),
                checked = preferences.dynamicColorEnabled,
                onCheckedChange = actions.onDynamicColorEnabledChange,
                enabled = environment.dynamicColorEligible,
                disabledReason = when {
                    environment.dynamicColorEligible -> null
                    eInk -> stringResource(R.string.settings_display_dynamic_disabled_eink)
                    else -> stringResource(R.string.settings_display_dynamic_disabled_api)
                },
                modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
            )

            if (eInk) {
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_display_section_refresh),
                )
                InlineStatus(
                    text = stringResource(R.string.settings_display_refresh_note),
                    modifier = Modifier.padding(top = TsuyomiSpacing.Sm),
                )
                SettingsActionRow(
                    title = stringResource(R.string.settings_display_refresh_now_title),
                    summary = stringResource(
                        R.string.settings_display_refresh_now_summary,
                        environment.redrawEpoch,
                    ),
                    onClick = actions.onRefreshNow,
                )
            }
        }
    }
}

@Composable
private fun displayStatusText(environment: DisplayEnvironment): String =
    when (environment.decisionReason) {
        DisplayDecisionReason.MANUAL_STANDARD ->
            stringResource(R.string.settings_display_status_manual_standard)
        DisplayDecisionReason.MANUAL_EINK ->
            stringResource(R.string.settings_display_status_manual_eink)
        DisplayDecisionReason.RECOGNIZED_EINK -> {
            val label = environment.detectedDeviceLabel
            if (label != null) {
                stringResource(R.string.settings_display_status_auto_recognized, label)
            } else {
                stringResource(R.string.settings_display_status_auto_recognized_unknown_label)
            }
        }
        DisplayDecisionReason.UNKNOWN_DEVICE ->
            stringResource(R.string.settings_display_status_auto_unknown)
    }
