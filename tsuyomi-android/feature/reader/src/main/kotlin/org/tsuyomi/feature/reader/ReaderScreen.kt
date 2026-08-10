/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.reader.ui.ReaderSurface
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException

@Composable
fun ReaderScreen(
    document: ReaderDocument?,
    loading: Boolean,
    failure: SourceException?,
    restoredLocator: ReaderLocator?,
    onLocatorChanged: (ReaderLocator, LocatorPrecision) -> Unit,
    modifier: Modifier = Modifier,
    preferences: PortableReaderPreferences = PortableReaderPreferences(flow = "scroll", fontScale = 1.0, lineHeight = 1.5, theme = "paper"),
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
) {
    when {
        loading -> StateView(
            kind = TsuyomiStateKind.LOADING,
            title = stringResource(R.string.reader_loading_chapter),
            modifier = modifier,
        )
        document != null -> ReaderSurface(document, restoredLocator, onLocatorChanged, modifier = modifier, preferences = preferences)
        failure != null -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.reader_diagnostic_stage, failure.diagnostic.stage, failure.diagnostic.safeCode))
            Text(stringResource(R.string.reader_chapter_failure))
            Text(stringResource(R.string.reader_error_code, failure.code.name))
            Text(stringResource(R.string.reader_diagnostic_id, failure.diagnostic.correlationId))
            TsuyomiButton(
                text = stringResource(R.string.reader_retry),
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp),
            )
            TsuyomiButton(
                text = stringResource(R.string.reader_offline),
                onClick = onUseOfflineCache,
                modifier = Modifier.padding(top = 8.dp),
                style = TsuyomiButtonStyle.SECONDARY,
            )
            if (failure.code == SourceErrorCode.SESSION_REQUIRED || failure.code == SourceErrorCode.VERIFICATION_REQUIRED) {
                TsuyomiButton(
                    text = stringResource(R.string.reader_open_verification),
                    onClick = onOpenVerification,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        else -> StateView(
            kind = TsuyomiStateKind.EMPTY,
            title = stringResource(R.string.reader_no_document),
            modifier = modifier,
        )
    }
}
