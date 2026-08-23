/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Atlas-only navigation sink used to exercise the product Back/Up/root-stack contract. */
@Immutable
data class AtlasNavigationActions(
    val navigate: (AtlasRoute) -> Unit,
    /** Opens the single shared search route, optionally preselecting one source lane. */
    val navigateSearch: (String?) -> Unit,
    val navigateInRoot: (AtlasFamily, AtlasRoute) -> Unit,
    val up: () -> Unit,
    val selectLibraryView: (AtlasLibraryView) -> Unit,
    val selectRoot: (AtlasFamily) -> Unit,
)

val LocalAtlasNavigation = staticCompositionLocalOf {
    AtlasNavigationActions(
        navigate = {},
        navigateSearch = {},
        navigateInRoot = { _, _ -> },
        up = {},
        selectLibraryView = {},
        selectRoot = {},
    )
}

/** Atlas-only presentation sink: Reader restores system bars whenever its chrome is visible. */
@Immutable
data class AtlasReaderPresentationActions(
    val setImmersive: (Boolean) -> Unit,
    val setChromeVisible: (Boolean) -> Unit,
)

val LocalAtlasReaderPresentation = staticCompositionLocalOf {
    AtlasReaderPresentationActions(
        setImmersive = {},
        setChromeVisible = {},
    )
}
