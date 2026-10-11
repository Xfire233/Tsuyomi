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
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.SettingsActionRow
import org.tsuyomi.core.ui.components.SettingsGroup
import org.tsuyomi.core.ui.components.SettingsInfoRow
import org.tsuyomi.core.ui.components.SettingsSectionHeader
import org.tsuyomi.core.ui.components.TsuyomiDialog
import org.tsuyomi.core.ui.components.TsuyomiMeasuredPager
import org.tsuyomi.core.ui.components.TsuyomiMeasuredTextPager
import org.tsuyomi.core.ui.components.rememberTsuyomiMeasuredPagerState
import org.tsuyomi.core.ui.theme.TsuyomiSpacing

private data class MoreActionItem(
    val title: String,
    val summary: String? = null,
    val onClick: () -> Unit,
)

@Composable
fun MoreScreen(
    onOpenDisplaySettings: () -> Unit,
    onOpenReaderSettings: () -> Unit,
    onOpenDataTransfer: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        MoreActionItem(
            title = stringResource(R.string.settings_more_display_title),
            summary = stringResource(R.string.settings_more_display_summary),
            onClick = onOpenDisplaySettings,
        ),
        MoreActionItem(
            title = stringResource(R.string.settings_more_reader_title),
            summary = stringResource(R.string.settings_more_reader_summary),
            onClick = onOpenReaderSettings,
        ),
        MoreActionItem(
            title = stringResource(R.string.settings_more_transfer_title),
            summary = stringResource(R.string.settings_more_transfer_summary),
            onClick = onOpenDataTransfer,
        ),
        MoreActionItem(
            title = stringResource(R.string.settings_more_help_title),
            summary = stringResource(R.string.settings_more_help_summary),
            onClick = onOpenHelp,
        ),
        MoreActionItem(
            title = stringResource(R.string.settings_more_about_title),
            onClick = onOpenAbout,
        ),
    )
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    CenteredSettingsItems(
        modifier = modifier,
        groupItems = true,
        itemCount = actions.size,
        itemKey = { index -> "more:${actions[index].title}" },
        itemContent = { index ->
            if (eInk) {
                if (index > 0) HorizontalDivider()
                val action = actions[index]
                SettingsActionRow(
                    title = action.title,
                    summary = action.summary,
                    onClick = action.onClick,
                )
            } else if (index == 0) {
                SettingsGroup {
                    actions.forEachIndexed { itemIndex, action ->
                        SettingsActionRow(
                            title = action.title,
                            summary = action.summary,
                            onClick = action.onClick,
                        )
                        if (itemIndex < actions.lastIndex) HorizontalDivider()
                    }
                }
            }
        },
    )
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
    val licensePagerState = rememberTsuyomiMeasuredPagerState(initialAnchorKey = "text:0")
    CenteredSettingsItems(
        modifier = modifier,
        itemCount = 2,
        itemKey = { index -> "about:$index" },
        itemContent = { index ->
            if (index == 0) {
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
            } else {
                SettingsSectionHeader(title = stringResource(R.string.about_license_section))
                SettingsGroup {
                    SettingsActionRow(
                        title = stringResource(R.string.about_license_title),
                        summary = stringResource(R.string.about_license_summary),
                        onClick = { onLicenseVisibilityChanged(true) },
                    )
                }
            }
        },
    )
    if (licenseVisible) {
        TsuyomiDialog(
            onDismissRequest = { onLicenseVisibilityChanged(false) },
            title = stringResource(R.string.about_license_title),
            body = {
                if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK) {
                    TsuyomiMeasuredTextPager(
                        text = licenseText,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        state = licensePagerState,
                    )
                } else {
                    Text(licenseText)
                }
            },
            confirmLabel = stringResource(R.string.about_license_close),
            onConfirm = { onLicenseVisibilityChanged(false) },
        )
    }
}

@Composable
internal fun CenteredSettingsItems(
    modifier: Modifier,
    itemCount: Int,
    itemKey: (Int) -> String,
    itemContent: @Composable (Int) -> Unit,
    groupItems: Boolean = false,
) {
    val eInk = LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
    val pagerState = rememberTsuyomiMeasuredPagerState(
        initialAnchorKey = if (itemCount == 0) null else itemKey(0),
    )
    Box(modifier.fillMaxSize()) {
        if (eInk) {
            Box(
                Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxWidth()
                    .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
            ) {
                val pages: @Composable () -> Unit = {
                    TsuyomiMeasuredPager(
                        itemCount = itemCount,
                        itemKey = itemKey,
                        itemContent = itemContent,
                        modifier = Modifier.fillMaxWidth(),
                        state = pagerState,
                        fillViewport = false,
                        verticalSpacing = if (groupItems) 0.dp else TsuyomiSpacing.Sm,
                    )
                }
                if (groupItems) SettingsGroup { pages() } else pages()
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = TsuyomiSpacing.Md, vertical = TsuyomiSpacing.Sm),
            ) {
                repeat(itemCount) { itemContent(it) }
            }
        }
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

