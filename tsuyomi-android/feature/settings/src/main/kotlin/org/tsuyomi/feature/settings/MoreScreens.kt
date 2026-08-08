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
import org.tsuyomi.core.ui.components.SettingsActionRow
import org.tsuyomi.core.ui.components.SettingsInfoRow
import org.tsuyomi.core.ui.components.SettingsSectionHeader
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

@Composable
fun MoreScreen(
    onOpenDisplaySettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredSettingsColumn(modifier) {
        SettingsActionRow(
            title = stringResource(R.string.settings_more_entry_title),
            summary = stringResource(R.string.settings_more_entry_summary),
            onClick = onOpenDisplaySettings,
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_more_about_title),
            onClick = onOpenAbout,
        )
    }
}

@Composable
fun AboutScreen(
    applicationName: String,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    CenteredSettingsColumn(modifier) {
        SettingsSectionHeader(title = stringResource(R.string.settings_more_about_title))
        SettingsInfoRow(
            title = stringResource(R.string.about_app_name_label),
            summary = applicationName,
        )
        SettingsInfoRow(
            title = stringResource(R.string.about_version_label),
            summary = versionName,
        )
        SettingsInfoRow(
            title = stringResource(R.string.about_license_title),
            summary = stringResource(R.string.about_license_summary),
        )
    }
}

@Composable
private fun CenteredSettingsColumn(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Md),
        ) {
            content()
        }
    }
}
