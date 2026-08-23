/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasProfile
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute

private const val StandardPortrait = "spec:width=1080px,height=2400px,dpi=420"
private const val EInkPortrait = "spec:width=1080px,height=1920px,dpi=320"

@Composable
private fun StatePreview(route: AtlasRoute, state: AtlasPageState, profile: AtlasProfile) {
    AtlasApp(
        AtlasContext(
            route = route,
            state = state,
            profile = profile,
            reducedMotion = profile == AtlasProfile.EINK,
            simulateSystemUi = true,
            capture = true,
        ),
        runtime = rememberScreenshotRuntime(),
    )
}

@PreviewTest @Preview(name = "library-empty-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun LibraryEmptyStandard() = StatePreview(AtlasRoute.LIBRARY, AtlasPageState.EMPTY, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "library-empty-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun LibraryEmptyEInk() = StatePreview(AtlasRoute.LIBRARY, AtlasPageState.EMPTY, AtlasProfile.EINK)

@PreviewTest @Preview(name = "library-error-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun LibraryErrorStandard() = StatePreview(AtlasRoute.LIBRARY, AtlasPageState.ERROR, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "library-error-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun LibraryErrorEInk() = StatePreview(AtlasRoute.LIBRARY, AtlasPageState.ERROR, AtlasProfile.EINK)

@PreviewTest @Preview(name = "library-selection-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun LibrarySelectionStandard() = StatePreview(AtlasRoute.LIBRARY, AtlasPageState.SELECTION, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "library-selection-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun LibrarySelectionEInk() = StatePreview(AtlasRoute.LIBRARY, AtlasPageState.SELECTION, AtlasProfile.EINK)

@PreviewTest @Preview(name = "search-error-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun SearchErrorStandard() = StatePreview(AtlasRoute.SEARCH, AtlasPageState.ERROR, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "search-error-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun SearchErrorEInk() = StatePreview(AtlasRoute.SEARCH, AtlasPageState.ERROR, AtlasProfile.EINK)

@PreviewTest @Preview(name = "search-offline-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun SearchOfflineStandard() = StatePreview(AtlasRoute.SEARCH, AtlasPageState.OFFLINE, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "search-offline-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun SearchOfflineEInk() = StatePreview(AtlasRoute.SEARCH, AtlasPageState.OFFLINE, AtlasProfile.EINK)


@PreviewTest @Preview(name = "browse-empty-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun BrowseEmptyStandard() = StatePreview(AtlasRoute.BROWSE, AtlasPageState.EMPTY, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "browse-empty-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun BrowseEmptyEInk() = StatePreview(AtlasRoute.BROWSE, AtlasPageState.EMPTY, AtlasProfile.EINK)

@PreviewTest @Preview(name = "browse-error-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun BrowseErrorStandard() = StatePreview(AtlasRoute.BROWSE, AtlasPageState.ERROR, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "browse-error-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun BrowseErrorEInk() = StatePreview(AtlasRoute.BROWSE, AtlasPageState.ERROR, AtlasProfile.EINK)

@PreviewTest @Preview(name = "book-detail-error-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun BookDetailErrorStandard() = StatePreview(AtlasRoute.BOOK_DETAIL, AtlasPageState.ERROR, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "book-detail-error-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun BookDetailErrorEInk() = StatePreview(AtlasRoute.BOOK_DETAIL, AtlasPageState.ERROR, AtlasProfile.EINK)

@PreviewTest @Preview(name = "reader-offline-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun ReaderOfflineStandard() = StatePreview(AtlasRoute.BOOK_READER, AtlasPageState.OFFLINE, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "reader-offline-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun ReaderOfflineEInk() = StatePreview(AtlasRoute.BOOK_READER, AtlasPageState.OFFLINE, AtlasProfile.EINK)
