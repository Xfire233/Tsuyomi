/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.preferences.ColorSchemePreference
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.ui.components.InfoBanner
import org.tsuyomi.core.ui.components.InlineStatus
import org.tsuyomi.core.ui.components.LocalTsuyomiPagerMaxContentHeight
import org.tsuyomi.core.ui.components.SegmentedSelector
import org.tsuyomi.core.ui.components.SettingsActionRow
import org.tsuyomi.core.ui.components.SettingsGroup
import org.tsuyomi.core.ui.components.SettingsSectionHeader
import org.tsuyomi.core.ui.components.SettingsSwitchRow
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.TsuyomiSegment
import org.tsuyomi.core.ui.theme.TsuyomiSpacing
import org.tsuyomi.shared.model.CoverCardPresentation

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
    val onCoverCardPresentationChange: (CoverCardPresentation) -> Unit,
    val onRefreshNow: () -> Unit,
    val onRetryWrite: () -> Unit,
    val onAcknowledgeWriteFailure: () -> Unit,
)

private data class DisplaySettingsItem(
    val key: String,
    val content: @Composable () -> Unit,
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
    val items = mutableListOf<DisplaySettingsItem>()

    state.writeFailure?.let { failure ->
        items += DisplaySettingsItem("write-failure-${failure.id}") {
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
    }

    items += DisplaySettingsItem("profile") {
        DisplaySettingsSection(
            key = "profile",
            title = stringResource(R.string.settings_display_section_profile),
            eInk = eInk,
        ) { index ->
            if (index == 0) {
                SegmentedSelector(
                    options = listOf(
                        TsuyomiSegment(DisplayPreference.AUTO, stringResource(R.string.settings_display_profile_auto)),
                        TsuyomiSegment(DisplayPreference.STANDARD, stringResource(R.string.settings_display_profile_standard)),
                        TsuyomiSegment(DisplayPreference.EINK, stringResource(R.string.settings_display_profile_eink)),
                    ),
                    selected = preferences.displayPreference,
                    onSelect = actions.onDisplayPreferenceChange,
                    modifier = Modifier.padding(TsuyomiSpacing.Md),
                )
            } else {
                InlineStatus(
                    text = displayStatusText(environment),
                    modifier = Modifier.padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
                )
            }
        }
    }
    items += DisplaySettingsItem("appearance") {
        DisplaySettingsSection(
            key = "appearance",
            title = stringResource(R.string.settings_display_section_appearance),
            eInk = eInk,
        ) { index ->
            if (index == 0) {
                SegmentedSelector(
                    options = listOf(
                        TsuyomiSegment(ColorSchemePreference.SYSTEM, stringResource(R.string.settings_display_theme_system)),
                        TsuyomiSegment(ColorSchemePreference.LIGHT, stringResource(R.string.settings_display_theme_light)),
                        TsuyomiSegment(ColorSchemePreference.DARK, stringResource(R.string.settings_display_theme_dark)),
                    ),
                    selected = preferences.colorSchemePreference,
                    enabled = !eInk,
                    disabledReason = if (eInk) stringResource(R.string.settings_display_theme_disabled_eink) else null,
                    onSelect = actions.onColorSchemePreferenceChange,
                    modifier = Modifier.padding(TsuyomiSpacing.Md),
                )
            } else {
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
                )
            }
        }
    }
    if (eInk) {
        items += DisplaySettingsItem("refresh") {
            DisplaySettingsSection(
                key = "refresh",
                title = stringResource(R.string.settings_display_section_refresh),
                eInk = true,
            ) { index ->
                if (index == 0) {
                    InlineStatus(
                        text = stringResource(R.string.settings_display_refresh_note),
                        modifier = Modifier.padding(TsuyomiSpacing.Md),
                    )
                } else {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_display_refresh_now_title),
                        summary = stringResource(R.string.settings_display_refresh_now_summary, environment.redrawEpoch),
                        onClick = actions.onRefreshNow,
                    )
                }
            }
        }
    } else {
        items += DisplaySettingsItem("cover-cards") {
            SettingsSectionHeader(title = stringResource(R.string.settings_display_section_cover_cards))
            SettingsGroup {
                SegmentedSelector(
                    options = listOf(
                        TsuyomiSegment(
                            CoverCardPresentation.STANDARD,
                            stringResource(R.string.settings_display_cover_standard),
                        ),
                        TsuyomiSegment(
                            CoverCardPresentation.WIDE,
                            stringResource(R.string.settings_display_cover_wide),
                        ),
                    ),
                    selected = preferences.coverCardPresentation,
                    onSelect = actions.onCoverCardPresentationChange,
                    enabled = !preferences.coverCardPresentationReadOnly,
                    disabledReason = if (preferences.coverCardPresentationReadOnly) {
                        stringResource(R.string.settings_display_cover_unsupported)
                    } else {
                        null
                    },
                    modifier = Modifier.padding(TsuyomiSpacing.Md),
                )
            }
        }
    }

    CenteredSettingsItems(
        modifier = modifier,
        itemCount = items.size,
        standardTopPadding = 0.dp,
        standardBottomPadding = TsuyomiSpacing.Lg,
        itemKey = { index -> items[index].key },
        itemContent = { index ->
            if (eInk) {
                Box(Modifier.fillMaxWidth().testTag("settings-display-item")) {
                    Box(Modifier.fillMaxWidth().testTag("settings-display-item-${items[index].key}")) {
                        items[index].content()
                    }
                }
            } else {
                items[index].content()
            }
        },
    )
}

/** Keep each heading and its complete controls together, paging only the bounded group overflow. */
@Composable
private fun DisplaySettingsSection(
    key: String,
    title: String,
    eInk: Boolean,
    content: @Composable (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().then(
            if (eInk) Modifier.heightIn(max = requireNotNull(LocalTsuyomiPagerMaxContentHeight.current)) else Modifier,
        ),
    ) {
        SettingsSectionHeader(title)
        SettingsGroup(modifier = if (eInk) Modifier.weight(1f, fill = false) else Modifier) {
            if (eInk) {
                TsuyomiMeasuredPager(
                    itemCount = 2,
                    itemKey = { index -> "$key:$index" },
                    itemContent = content,
                    fillViewport = false,
                )
            } else {
                repeat(2) { content(it) }
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
