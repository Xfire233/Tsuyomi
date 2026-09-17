/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.tsuyomi.core.ui.components.SettingsActionRow
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.components.SettingsGroup
import org.tsuyomi.core.ui.components.SettingsInfoRow
import org.tsuyomi.core.ui.components.SettingsSectionHeader
import org.tsuyomi.core.ui.components.TsuyomiDialog
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

@Composable
fun MoreScreen(
    onOpenDisplaySettings: () -> Unit,
    onOpenReaderSettings: () -> Unit,
    onOpenDataTransfer: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredSettingsColumn(modifier) {
        SettingsGroup {
            SettingsActionRow(
                title = stringResource(R.string.settings_more_display_title),
                summary = stringResource(R.string.settings_more_display_summary),
                onClick = onOpenDisplaySettings,
            )
            HorizontalDivider()
            SettingsActionRow(
                title = stringResource(R.string.settings_more_reader_title),
                summary = stringResource(R.string.settings_more_reader_summary),
                onClick = onOpenReaderSettings,
            )
            HorizontalDivider()
            SettingsActionRow(
                title = stringResource(R.string.settings_more_transfer_title),
                summary = stringResource(R.string.settings_more_transfer_summary),
                onClick = onOpenDataTransfer,
            )
            HorizontalDivider()
            SettingsActionRow(
                title = stringResource(R.string.settings_more_help_title),
                summary = stringResource(R.string.settings_more_help_summary),
                onClick = onOpenHelp,
            )
            HorizontalDivider()
            SettingsActionRow(
                title = stringResource(R.string.settings_more_about_title),
                onClick = onOpenAbout,
            )
        }
    }
}

@Composable
fun AboutScreen(
    applicationName: String,
    versionName: String,
    licenseText: String,
    licenseVisible: Boolean,
    onLicenseVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredSettingsColumn(modifier) {
        SettingsSectionHeader(title = stringResource(R.string.settings_more_about_title))
        SettingsGroup {
            SettingsInfoRow(
                title = stringResource(R.string.about_app_name_label),
                summary = applicationName,
            )
            HorizontalDivider()
            SettingsInfoRow(
                title = stringResource(R.string.about_version_label),
                summary = versionName,
            )
        }
        SettingsSectionHeader(title = stringResource(R.string.about_license_section))
        SettingsGroup {
            SettingsActionRow(
                title = stringResource(R.string.about_license_title),
                summary = stringResource(R.string.about_license_summary),
                onClick = { onLicenseVisibilityChanged(true) },
            )
        }
    }
    if (licenseVisible) {
        TsuyomiDialog(
            onDismissRequest = { onLicenseVisibilityChanged(false) },
            title = stringResource(R.string.about_license_title),
            body = { Text(licenseText) },
            confirmLabel = stringResource(R.string.about_license_close),
            onConfirm = { onLicenseVisibilityChanged(false) },
        )
    }
}

@Composable
internal fun CenteredSettingsColumn(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
            content = content,
        )
    }
}
