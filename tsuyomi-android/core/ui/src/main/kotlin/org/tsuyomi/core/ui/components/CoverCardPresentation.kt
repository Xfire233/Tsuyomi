/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.shared.model.CoverCardPresentation

/**
 * Presentation configuration supplied once by the application root.
 *
 * This is deliberately a visual configuration only: core UI has no dependency on preference
 * storage and callers can supply the effective presentation from any host-owned state source.
 */
val LocalCoverCardPresentation = staticCompositionLocalOf { CoverCardPresentation.STANDARD }

/** Installs the shared card presentation for the current composition subtree. */
@Composable
fun CoverCardPresentationProvider(
    presentation: CoverCardPresentation,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalCoverCardPresentation provides presentation, content = content)
}

/** Geometry shared by book, structural-node, and drag-preview card renderers. */
@Immutable
data class CoverCardLayout(
    val presentation: CoverCardPresentation,
    val gridAspectRatio: Float,
    val artworkAspectRatio: Float,
    val leadingArtworkAspectRatio: Float,
    val usesWideTitleLane: Boolean,
)

/**
 * Resolves the selected Standard presentation, retaining the complete legacy E-ink geometry.
 * E-ink never adopts the user-selected Standard grid/card option.
 */
@Composable
fun currentCoverCardLayout(): CoverCardLayout = if (
    LocalDisplayEnvironment.current.effectiveProfile == DisplayProfile.EINK
) {
    LegacyEInkCoverCardLayout
} else {
    when (LocalCoverCardPresentation.current) {
        CoverCardPresentation.STANDARD -> StandardCoverCardLayout
        CoverCardPresentation.WIDE -> WideCoverCardLayout
    }
}

private val StandardCoverCardLayout = CoverCardLayout(
    presentation = CoverCardPresentation.STANDARD,
    gridAspectRatio = 5f / 7f,
    artworkAspectRatio = 5f / 7f,
    leadingArtworkAspectRatio = 5f / 7f,
    usesWideTitleLane = false,
)

private val WideCoverCardLayout = CoverCardLayout(
    presentation = CoverCardPresentation.WIDE,
    gridAspectRatio = 16f / 9f,
    artworkAspectRatio = 5f / 7f,
    leadingArtworkAspectRatio = 5f / 7f,
    usesWideTitleLane = true,
)

private val LegacyEInkCoverCardLayout = CoverCardLayout(
    presentation = CoverCardPresentation.STANDARD,
    gridAspectRatio = 3f / 4f,
    artworkAspectRatio = 2f / 3f,
    leadingArtworkAspectRatio = 3f / 4f,
    usesWideTitleLane = false,
)
