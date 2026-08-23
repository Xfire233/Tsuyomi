/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasProfile
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.AtlasThemeKind
import org.tsuyomi.prototype.uiatlas.runtime.PrototypeRuntime
private const val StandardPortrait = "spec:width=1080px,height=2400px,dpi=420"
private const val EInkPortrait = "spec:width=1080px,height=1920px,dpi=320"

@Composable
internal fun rememberScreenshotRuntime(): PrototypeRuntime {
    val context = LocalContext.current
    return remember(context) { PrototypeRuntime(context, persistent = false) }
}

@Composable
private fun AtlasRoutePreview(route: AtlasRoute, profile: AtlasProfile) {
    AtlasApp(
        initial = AtlasContext(
            route = route,
            profile = profile,
            theme = AtlasThemeKind.LIGHT,
            reducedMotion = profile == AtlasProfile.EINK,
            simulateSystemUi = true,
            capture = true,
        ),
        runtime = rememberScreenshotRuntime(),
    )
}

@PreviewTest @Preview(name = "library-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun LibraryStandard() = AtlasRoutePreview(AtlasRoute.LIBRARY, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "library-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun LibraryEInk() = AtlasRoutePreview(AtlasRoute.LIBRARY, AtlasProfile.EINK)

@PreviewTest @Preview(name = "history-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun HistoryStandard() = AtlasRoutePreview(AtlasRoute.LIBRARY_HISTORY, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "history-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun HistoryEInk() = AtlasRoutePreview(AtlasRoute.LIBRARY_HISTORY, AtlasProfile.EINK)

@PreviewTest @Preview(name = "updates-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun UpdatesStandard() = AtlasRoutePreview(AtlasRoute.LIBRARY_UPDATES, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "updates-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun UpdatesEInk() = AtlasRoutePreview(AtlasRoute.LIBRARY_UPDATES, AtlasProfile.EINK)


@PreviewTest @Preview(name = "collection-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun CollectionStandard() = AtlasRoutePreview(AtlasRoute.LIBRARY_COLLECTION, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "collection-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun CollectionEInk() = AtlasRoutePreview(AtlasRoute.LIBRARY_COLLECTION, AtlasProfile.EINK)

@PreviewTest @Preview(name = "collection-rule-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun CollectionRuleStandard() = AtlasRoutePreview(AtlasRoute.LIBRARY_COLLECTION_RULE, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "collection-rule-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun CollectionRuleEInk() = AtlasRoutePreview(AtlasRoute.LIBRARY_COLLECTION_RULE, AtlasProfile.EINK)

@PreviewTest @Preview(name = "tags-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun TagsStandard() = AtlasRoutePreview(AtlasRoute.LIBRARY_TAGS, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "tags-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun TagsEInk() = AtlasRoutePreview(AtlasRoute.LIBRARY_TAGS, AtlasProfile.EINK)

@PreviewTest @Preview(name = "mirror-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun MirrorStandard() = AtlasRoutePreview(AtlasRoute.LIBRARY_MIRROR, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "mirror-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun MirrorEInk() = AtlasRoutePreview(AtlasRoute.LIBRARY_MIRROR, AtlasProfile.EINK)

@PreviewTest @Preview(name = "book-detail-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun BookDetailStandard() = AtlasRoutePreview(AtlasRoute.BOOK_DETAIL, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "book-detail-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun BookDetailEInk() = AtlasRoutePreview(AtlasRoute.BOOK_DETAIL, AtlasProfile.EINK)


@PreviewTest @Preview(name = "reader-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun ReaderStandard() = AtlasRoutePreview(AtlasRoute.BOOK_READER, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "reader-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun ReaderEInk() = AtlasRoutePreview(AtlasRoute.BOOK_READER, AtlasProfile.EINK)

@PreviewTest @Preview(name = "browse-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun BrowseStandard() = AtlasRoutePreview(AtlasRoute.BROWSE, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "browse-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun BrowseEInk() = AtlasRoutePreview(AtlasRoute.BROWSE, AtlasProfile.EINK)

@PreviewTest @Preview(name = "search-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun SearchStandard() = AtlasRoutePreview(AtlasRoute.SEARCH, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "search-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun SearchEInk() = AtlasRoutePreview(AtlasRoute.SEARCH, AtlasProfile.EINK)

@PreviewTest @Preview(name = "remote-library-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun RemoteLibraryStandard() = AtlasRoutePreview(AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "remote-library-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun RemoteLibraryEInk() = AtlasRoutePreview(AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY, AtlasProfile.EINK)

@PreviewTest @Preview(name = "verification-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun VerificationStandard() = AtlasRoutePreview(AtlasRoute.SOURCE_VERIFICATION, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "verification-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun VerificationEInk() = AtlasRoutePreview(AtlasRoute.SOURCE_VERIFICATION, AtlasProfile.EINK)

@PreviewTest @Preview(name = "more-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun MoreStandard() = AtlasRoutePreview(AtlasRoute.MORE, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "more-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun MoreEInk() = AtlasRoutePreview(AtlasRoute.MORE, AtlasProfile.EINK)

@PreviewTest @Preview(name = "display-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun DisplayStandard() = AtlasRoutePreview(AtlasRoute.MORE_DISPLAY, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "display-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun DisplayEInk() = AtlasRoutePreview(AtlasRoute.MORE_DISPLAY, AtlasProfile.EINK)

@PreviewTest @Preview(name = "reader-settings-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun ReaderSettingsStandard() = AtlasRoutePreview(AtlasRoute.MORE_READER, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "reader-settings-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun ReaderSettingsEInk() = AtlasRoutePreview(AtlasRoute.MORE_READER, AtlasProfile.EINK)

@PreviewTest @Preview(name = "data-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun DataStandard() = AtlasRoutePreview(AtlasRoute.MORE_DATA, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "data-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun DataEInk() = AtlasRoutePreview(AtlasRoute.MORE_DATA, AtlasProfile.EINK)

@PreviewTest @Preview(name = "data-report-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun DataReportStandard() = AtlasRoutePreview(AtlasRoute.MORE_DATA_REPORT, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "data-report-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun DataReportEInk() = AtlasRoutePreview(AtlasRoute.MORE_DATA_REPORT, AtlasProfile.EINK)

@PreviewTest @Preview(name = "help-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun HelpStandard() = AtlasRoutePreview(AtlasRoute.MORE_HELP, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "help-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun HelpEInk() = AtlasRoutePreview(AtlasRoute.MORE_HELP, AtlasProfile.EINK)

@PreviewTest @Preview(name = "about-standard", device = StandardPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun AboutStandard() = AtlasRoutePreview(AtlasRoute.MORE_ABOUT, AtlasProfile.STANDARD)
@PreviewTest @Preview(name = "about-eink", device = EInkPortrait, locale = "zh-rCN", showSystemUi = false)
@Composable fun AboutEInk() = AtlasRoutePreview(AtlasRoute.MORE_ABOUT, AtlasProfile.EINK)
