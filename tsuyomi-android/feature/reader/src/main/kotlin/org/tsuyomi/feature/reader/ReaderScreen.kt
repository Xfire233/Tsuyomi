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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.components.StateView
import org.tsuyomi.core.ui.components.TsuyomiButton
import org.tsuyomi.core.ui.components.TsuyomiButtonStyle
import org.tsuyomi.core.ui.components.TsuyomiStateKind
import org.tsuyomi.core.ui.components.TsuyomiTopBar
import org.tsuyomi.reader.ui.ReaderSurface
import org.tsuyomi.reader.ui.rememberReaderAuxiliarySheetState
import org.tsuyomi.reader.ui.rememberReaderReturnAnchorState
import org.tsuyomi.shared.backup.PortableReaderPreferences
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException

@Composable
fun ReaderScreen(
    document: ReaderDocument?,
    bookIdentity: BookIdentity?,
    loading: Boolean,
    failure: SourceException?,
    restoredLocator: ReaderLocator?,
    restorationGeneration: Long,
    chapters: List<SourceChapter>,
    currentChapterId: String,
    bookmarks: List<ReaderLocator>,
    onToggleBookmark: (ReaderLocator) -> Unit,
    onSelectBookmark: (ReaderLocator) -> Unit,
    onReturnToOrigin: (ReaderLocator) -> Unit,
    onRemoveBookmark: (ReaderLocator) -> Unit,
    requestedFlow: String,
    hasFlowOverride: Boolean,
    onFlowOverrideChanged: (String?) -> Unit,
    onSelectChapter: (SourceChapter) -> Unit,
    onNavigateUp: () -> Unit,
    imageStates: Map<String, CoverUiState>,
    onImageVisible: (ReaderBlock.Image) -> Unit,
    onRetryImage: (ReaderBlock.Image) -> Unit,
    onLocatorChanged: (ReaderLocator, LocatorPrecision) -> Unit,
    modifier: Modifier = Modifier,
    onChapterCompleted: (String) -> Unit = {},
    preferences: PortableReaderPreferences = PortableReaderPreferences(
        flow = "scroll",
        fontScale = 1.0,
        lineHeight = 1.5,
        theme = "paper",
    ),
    onPreferencesChanged: (PortableReaderPreferences) -> Unit = {},
    onRetry: () -> Unit,
    onUseOfflineCache: () -> Unit,
    onOpenVerification: () -> Unit,
) {
    val auxiliarySheetState = rememberReaderAuxiliarySheetState(
        sourceId = bookIdentity?.sourceId.orEmpty(),
        remoteBookId = bookIdentity?.remoteBookId.orEmpty(),
    )
    val returnAnchorState = rememberReaderReturnAnchorState(
        sourceId = bookIdentity?.sourceId.orEmpty(),
        remoteBookId = bookIdentity?.remoteBookId.orEmpty(),
    )
    LaunchedEffect(failure) {
        if (failure != null) returnAnchorState.navigationFailed()
    }
    when {
        loading -> ReaderRouteState(onNavigateUp, modifier) {
            StateView(
                kind = TsuyomiStateKind.LOADING,
                title = stringResource(R.string.reader_loading_chapter),
                modifier = Modifier.fillMaxSize(),
            )
        }
        document != null -> ReaderSurface(
            document = document,
            auxiliarySheetState = auxiliarySheetState,
            restoredLocator = restoredLocator,
            restorationGeneration = restorationGeneration,
            onLocatorChanged = onLocatorChanged,
            chapters = chapters,
            currentChapterId = currentChapterId,
            bookmarks = bookmarks,
            onToggleBookmark = onToggleBookmark,
            onSelectBookmark = onSelectBookmark,
            onRemoveBookmark = onRemoveBookmark,
            requestedFlow = requestedFlow,
            hasFlowOverride = hasFlowOverride,
            onFlowOverrideChanged = onFlowOverrideChanged,
            onSelectChapter = onSelectChapter,
            onNavigateUp = onNavigateUp,
            onChapterCompleted = onChapterCompleted,
            imageStates = imageStates,
            onImageVisible = onImageVisible,
            onRetryImage = onRetryImage,
            modifier = modifier,
            preferences = preferences,
            onPreferencesChanged = onPreferencesChanged,
            returnAnchorState = returnAnchorState,
            onReturnToOrigin = onReturnToOrigin,
        )
        failure != null -> ReaderRouteState(onNavigateUp, modifier) {
            val message = stringResource(readerFailureMessage(failure.code))
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.reader_chapter_failure))
                Text(message)
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
        }
        else -> ReaderRouteState(onNavigateUp, modifier) {
            StateView(
                kind = TsuyomiStateKind.EMPTY,
                title = stringResource(R.string.reader_no_document),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun readerFailureMessage(code: SourceErrorCode): Int = when (code) {
    SourceErrorCode.NETWORK_TIMEOUT,
    SourceErrorCode.NETWORK_OFFLINE,
    SourceErrorCode.NETWORK_REDIRECT_DISALLOWED,
    SourceErrorCode.NETWORK_RESPONSE_TOO_LARGE -> R.string.reader_failure_network
    SourceErrorCode.ORIGIN_NOT_GRANTED -> R.string.reader_failure_permission
    SourceErrorCode.SESSION_REQUIRED,
    SourceErrorCode.VERIFICATION_REQUIRED -> R.string.reader_failure_verification
    SourceErrorCode.MALFORMED_SOURCE_RESPONSE,
    SourceErrorCode.EMPTY_SOURCE_RESPONSE -> R.string.reader_failure_source
    SourceErrorCode.EXTENSION_RUNTIME_FAILURE,
    SourceErrorCode.EXTENSION_TIMEOUT,
    SourceErrorCode.EXTENSION_CANCELLED -> R.string.reader_failure_runtime
}

@Composable
private fun ReaderRouteState(
    onNavigateUp: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        if (LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.STANDARD) {
            TsuyomiTopBar(
                title = stringResource(R.string.reader_title),
                onNavigateUp = onNavigateUp,
            )
        }
        Column(Modifier.weight(1f), content = { content() })
    }
}
