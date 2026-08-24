/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Phase 1 iconography: simple original glyphs drawn as Compose [ImageVector]s. No legacy project
 * artwork is referenced, copied, or traced.
 */
object TsuyomiIcons {
    /** Back arrow used by child-page navigation. */
    val Back: ImageVector by lazy {
        vector(
            "TsuyomiBack",
            "M20 11H7.8l4.6-4.6L11 5l-7 7 7 7 1.4-1.4L7.8 13H20z",
        )
    }

    /** Three upright books on a shelf. */
    val Shelf: ImageVector by lazy {
        vector(
            "TsuyomiShelf",
            "M4 4h4v16H4z",
            "M10 4h4v16h-4z",
            "M15.6 4.9l4.1 14.4-3.7 1.1-4.2-14.4z",
        )
    }

    /** A compass for browsing. */
    val Compass: ImageVector by lazy {
        vector(
            "TsuyomiCompass",
            "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 3a7 7 0 1 1 0 14 7 7 0 0 1 0-14z",
            "M15.5 8.5l-2.2 4.8-4.8 2.2 2.2-4.8z",
        )
    }

    /** Three horizontal dots for the more destination. */
    val More: ImageVector by lazy {
        vector(
            "TsuyomiMore",
            "M5 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z",
            "M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z",
            "M19 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z",
        )
    }
}

private fun vector(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEach { path ->
        builder.addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }
    return builder.build()
}
