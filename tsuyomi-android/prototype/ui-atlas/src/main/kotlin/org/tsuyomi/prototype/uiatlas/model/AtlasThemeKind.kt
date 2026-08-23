/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.model

/**
 * Theme kinds under review (Atlas Spec §6). [DYNAMIC] is the deterministic-dynamic fixture: the
 * scheme is derived offline from [org.tsuyomi.prototype.uiatlas.ATLAS_SEED] and frozen as
 * constants, so repeat captures are pixel-identical. The E-ink profile always renders the fixed
 * monochrome scheme regardless of the requested kind.
 */
enum class AtlasThemeKind(val extraKey: String) {
    LIGHT("light"),
    DARK("dark"),
    DYNAMIC("dynamic"),
    ;

    companion object {
        fun parse(raw: String?): AtlasThemeKind? = when (raw?.trim()?.lowercase()) {
            "light" -> LIGHT
            "dark" -> DARK
            "dynamic" -> DYNAMIC
            else -> null
        }
    }
}
