/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasProfile
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.AtlasVariant

private const val StandardPortrait = "spec:width=1080px,height=2400px,dpi=420"
private const val EInkPortrait = "spec:width=1080px,height=1920px,dpi=320"

@Composable
private fun DecisionPreview(
    route: AtlasRoute,
    profile: AtlasProfile = AtlasProfile.STANDARD,
    state: AtlasPageState = AtlasPageState.CONTENT,
    variant: String? = null,
    layout: AtlasLayout? = null,
    inlineModalPreview: Boolean = false,
    readerImmersive: Boolean = false,
) {
    AtlasApp(
        AtlasContext(
            route = route,
            profile = profile,
            state = state,
            variant = AtlasVariant.parse(variant),
            layout = layout,
            reducedMotion = profile == AtlasProfile.EINK,
            inlineModalPreview = inlineModalPreview,
            simulateSystemUi = true,
            capture = true,
            readerImmersive = readerImmersive,
        ),
        runtime = rememberScreenshotRuntime(),
    )
}

@PreviewTest
@Preview(name = "library-decision-e-dense-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun DecisionLibraryDense() = DecisionPreview(AtlasRoute.LIBRARY, variant = "E-a", layout = AtlasLayout.LIST)

@PreviewTest
@Preview(name = "library-decision-e-compact-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun DecisionLibraryCompact() = DecisionPreview(AtlasRoute.LIBRARY, variant = "E-b", layout = AtlasLayout.COMPACT)

@PreviewTest
@Preview(name = "library-decision-e-grid-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun DecisionLibraryGrid() = DecisionPreview(AtlasRoute.LIBRARY, variant = "E-c", layout = AtlasLayout.GRID)

@PreviewTest
@Preview(name = "library-decision-g-type-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun DecisionEmptyType() = DecisionPreview(AtlasRoute.LIBRARY, state = AtlasPageState.EMPTY, variant = "G-a")

@PreviewTest
@Preview(name = "library-decision-g-emoticon-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun DecisionEmptyEmoticon() = DecisionPreview(AtlasRoute.LIBRARY, state = AtlasPageState.EMPTY, variant = "G-b")

@PreviewTest
@Preview(name = "remote-library-decision-b-trailing-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun DecisionTrailingAction() = DecisionPreview(AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY, variant = "B-a")

@PreviewTest
@Preview(name = "remote-library-decision-b-overflow-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun DecisionOverflowAction() = DecisionPreview(AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY, variant = "B-b")

@PreviewTest
@Preview(name = "remote-library-decision-b-swipe-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun DecisionSwipeAction() = DecisionPreview(AtlasRoute.BROWSE_SOURCE_REMOTE_LIBRARY, variant = "B-c")

@PreviewTest
@Preview(name = "browse-decision-a-appbar-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun DecisionCreationAppBar() = DecisionPreview(AtlasRoute.BROWSE, variant = "A-a")

@PreviewTest
@Preview(name = "browse-decision-a-fab-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun DecisionCreationFab() = DecisionPreview(AtlasRoute.BROWSE, variant = "A-b")

@PreviewTest
@Preview(name = "reader-converged-settings-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun ConvergedReaderSettingsStandard() = DecisionPreview(
    route = AtlasRoute.BOOK_READER,
    state = AtlasPageState.MODAL,
    inlineModalPreview = true,
)

@PreviewTest
@Preview(name = "reader-converged-settings-eink", device = EInkPortrait, locale = "zh-rCN")
@Composable
fun ConvergedReaderSettingsEInk() = DecisionPreview(
    route = AtlasRoute.BOOK_READER,
    profile = AtlasProfile.EINK,
    state = AtlasPageState.MODAL,
)

@PreviewTest
@Preview(name = "reader-converged-immersive-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun ConvergedReaderImmersiveStandard() = DecisionPreview(
    route = AtlasRoute.BOOK_READER,
    readerImmersive = true,
)

@PreviewTest
@Preview(name = "reader-converged-immersive-settings-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun ConvergedReaderImmersiveSettingsStandard() = DecisionPreview(
    route = AtlasRoute.BOOK_READER,
    state = AtlasPageState.MODAL,
    inlineModalPreview = true,
    readerImmersive = true,
)

@PreviewTest
@Preview(name = "library-converged-selection-standard", device = StandardPortrait, locale = "zh-rCN")
@Composable
fun ConvergedSelectionBar() = DecisionPreview(AtlasRoute.LIBRARY, state = AtlasPageState.SELECTION, variant = "D-a")
