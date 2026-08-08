/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.layout

/** The current available window area in dp, measured at runtime (never from device models). */
data class TsuyomiWindowSize(
    val widthDp: Int,
    val heightDp: Int,
)

/** How the top-level navigation chrome is arranged for the current window. */
enum class TsuyomiNavigationLayout {
    /** Bottom navigation bar for compact-width windows with comfortable height. */
    BOTTOM_BAR,

    /** Deterministic compact bottom bar for double-compact windows (labels retained). */
    COMPACT_BOTTOM_BAR,

    /** Side navigation rail for wide windows and compact-height landscapes. */
    RAIL,
}

/**
 * Resolves the navigation chrome from the runtime window only:
 *
 * - `width < 480dp && height < 480dp` → [TsuyomiNavigationLayout.COMPACT_BOTTOM_BAR]
 * - `height < 480dp && width >= 480dp` → [TsuyomiNavigationLayout.RAIL]
 * - `width >= 600dp` → [TsuyomiNavigationLayout.RAIL]
 * - otherwise → [TsuyomiNavigationLayout.BOTTOM_BAR]
 */
fun resolveNavigationLayout(size: TsuyomiWindowSize): TsuyomiNavigationLayout {
    if (size.widthDp < DOUBLE_COMPACT_THRESHOLD_DP && size.heightDp < COMPACT_HEIGHT_THRESHOLD_DP) {
        return TsuyomiNavigationLayout.COMPACT_BOTTOM_BAR
    }
    if (size.heightDp < COMPACT_HEIGHT_THRESHOLD_DP) {
        return TsuyomiNavigationLayout.RAIL
    }
    if (size.widthDp >= WIDE_WIDTH_THRESHOLD_DP) {
        return TsuyomiNavigationLayout.RAIL
    }
    return TsuyomiNavigationLayout.BOTTOM_BAR
}

const val WIDE_WIDTH_THRESHOLD_DP = 600
const val COMPACT_HEIGHT_THRESHOLD_DP = 480
const val DOUBLE_COMPACT_THRESHOLD_DP = 480
