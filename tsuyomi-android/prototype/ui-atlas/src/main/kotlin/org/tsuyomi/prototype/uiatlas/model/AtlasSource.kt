/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Original geometric source marks drawn with Compose Canvas only (Atlas Spec §4.6/F9: synthetic,
 * never copied from any reference project; §11 non-goal: no copied or traced artwork).
 */
enum class AtlasSourceMark {
    /** Three stacked chevrons — 源·松. */
    PINE,

    /** Three upright bars of graded height — 源·柏. */
    CYPRESS,

    /** Two segmented stalks — 源·竹. */
    BAMBOO,

    /** Diamond in a ring — generic host fallback mark for invalid/missing branding. */
    GENERIC,
}

/** Why a branding payload failed host validation (constitution §15.3 reject classes). */
enum class AtlasBrandInvalidity(val label: String) {
    SCRIPT("内嵌脚本"),
    REMOTE_REF("远程引用"),
    OVERSIZE("超出体积上限"),
}

/**
 * The atlas fixture analog of the §15 validated-branding pipeline output. `Valid` carries the one
 * opaque bounded color the pipeline accepted; the mark itself is host-side fixture data, so the
 * component layer never sees raw payloads. `Invalid` and `Missing` resolve to the generic host
 * fallback deterministically.
 */
@Immutable
sealed interface AtlasBranding {
    @Immutable
    data class Valid(val color: Color) : AtlasBranding

    @Immutable
    data class Invalid(val reason: AtlasBrandInvalidity) : AtlasBranding

    @Immutable
    data object Missing : AtlasBranding
}

/**
 * A synthetic source (Atlas Spec F1/F8: `源·松`, `源·柏`, `源·竹` and dormant/credential-expired
 * capability states). Everything user-visible is pre-formatted fixture text.
 */
@Immutable
data class AtlasSource(
    val id: String,
    val name: String,
    val mark: AtlasSourceMark,
    val branding: AtlasBranding,
    val version: String,
    val dormant: Boolean = false,
    val credentialExpired: Boolean = false,
) {
    /** Capability summary line for source cards (e.g. `v1.4 · 搜索 / 网站收藏`). */
    val capabilityLabel: String
        get() = buildString {
            append(version)
            when {
                dormant -> append(" · 休眠")
                credentialExpired -> append(" · 凭据过期")
                else -> append(" · 搜索 / 网站收藏")
            }
        }
}
