/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.model

import androidx.compose.runtime.Immutable

/** M3-backed Library density; remote/search contexts may still constrain available values. */
enum class AtlasLayout(val extraKey: String) {
    LIST("list"),
    COMPACT("compact"),
    GRID("grid"),
    ;

    companion object {
        fun parse(raw: String?): AtlasLayout? = when (raw?.trim()?.lowercase()) {
            "list", "dense" -> LIST
            "compact", "text" -> COMPACT
            "grid" -> GRID
            else -> null
        }
    }
}

/**
 * Stable Library context identifiers. In RC2.1 they describe the node/view currently opened from
 * the mixed Library flow; no selector-only navigation or template manager is implied.
 */
enum class AtlasLibraryView(val extraKey: String, val label: String, val fixtureCount: Int) {
    ALL("all", "全部书籍", 128),
    CONTINUE("continue", "继续阅读", 14),
    RECENT("recent", "最近", 20),
    READ_LATER("read-later", "稍后再读", 6),
    DORMANT("dormant", "休眠来源", 4),
    COLLECTION("collection", "收藏夹", 23),
    MIRROR("mirror", "网站镜像", 31),
    ;

    companion object {
        fun parse(raw: String?): AtlasLibraryView? {
            val value = raw?.trim()?.lowercase().orEmpty()
            if (value.isEmpty()) return null
            return entries.firstOrNull { it.extraKey == value || it.name.lowercase() == value }
        }
    }
}

/**
 * One fixture variant. Legacy A–K values remain readable for rejected RC2 evidence; RC2.1 direct
 * renders use visible [AtlasReviewSpec] context and resolved decisions rather than reopening votes.
 */
@Immutable
data class AtlasVariant(val id: Char, val option: String) {
    init {
        require(id.uppercaseChar() in 'A'..'K') { "variant id must be A–K, was $id" }
    }

    override fun toString(): String = "${id.uppercaseChar()}-${option.lowercase()}"

    companion object {
        /** Parses `H-b`, `h`, `E-2` (digits map to option letters a/b/c/…). */
        fun parse(raw: String?): AtlasVariant? {
            val value = raw?.trim()?.replace("★", "").orEmpty()
            if (value.isEmpty()) return null
            val id = value.first().uppercaseChar()
            if (id !in 'A'..'K') return null
            val suffix = value.drop(1).removePrefix("-").trim().lowercase()
            val option = when {
                suffix.isEmpty() -> "a"
                suffix.all { it.isDigit() } -> ('a' + (suffix.toInt() - 1).coerceIn(0, 25)).toString()
                else -> suffix
            }
            return AtlasVariant(id, option)
        }
    }
}

/** Visible explanation carried by a standalone RC2.1 screenshot. */
@Immutable
data class AtlasReviewSpec(
    val id: String,
    val scenario: String,
    val currentDefault: String,
    val verifies: String,
) {
    init {
        require(id.startsWith("rc21-")) { "RC2.1 review id must start with rc21-" }
    }
}

enum class AtlasReaderSeekPreview {
    CANCEL,
    COMMIT,
    RETURN_ORIGIN,
}

/**
 * The complete immutable review configuration for one atlas frame. Family composables receive an
 * instance of this type and nothing else: identical input must produce a pixel-identical frame
 * (Atlas Spec §1.5).
 *
 * The context is the *initial* configuration for a launch. In interactive review the family
 * screens may keep intra-family navigation state of their own; capture launches (`capture=true`)
 * always render exactly the route named here with no interactive chrome.
 */
@Immutable
data class AtlasContext(
    val route: AtlasRoute,
    val state: AtlasPageState = AtlasPageState.CONTENT,
    val profile: AtlasProfile = AtlasProfile.STANDARD,
    val theme: AtlasThemeKind = AtlasThemeKind.LIGHT,
    val variant: AtlasVariant? = null,
    val layout: AtlasLayout? = null,
    val libraryView: AtlasLibraryView = AtlasLibraryView.ALL,
    /** Atlas-only route argument for Browse's shared search entry; null selects all installed sources. */
    val selectedSearchSourceId: String? = null,
    val reducedMotion: Boolean = false,
    val tutorial: Boolean = false,
    val capture: Boolean = false,
    /** Screenshot-host adapter: render platform modal content through an in-tree M3 sheet. */
    val inlineModalPreview: Boolean = false,
    /** Declarative evidence wrapper: draw deterministic system bars and a centered camera cutout. */
    val simulateSystemUi: Boolean = false,
    /** Reader-only preference; every other route keeps system bars visible. */
    val readerImmersive: Boolean = false,
    /** RC2.1 direct-render explanation; null for ordinary route/state captures. */
    val review: AtlasReviewSpec? = null,
    /** Fixture-only Reader seek overlay state for deterministic direct-render evidence. */
    val readerSeekPreview: AtlasReaderSeekPreview? = null,
) {
    /** The primary state owning the content area; overlays always sit on CONTENT. */
    val primaryState: AtlasPageState
        get() = if (state.primary) state else AtlasPageState.CONTENT

    val showOfflineBanner: Boolean get() = state == AtlasPageState.OFFLINE
    val showRefreshingBanner: Boolean get() = state == AtlasPageState.REFRESHING
    val selectionMode: Boolean get() = state == AtlasPageState.SELECTION
    val showMutationBanner: Boolean get() = state == AtlasPageState.MUTATION
    val showUnresolvedBanner: Boolean get() = state == AtlasPageState.UNRESOLVED
    val showModal: Boolean get() = state == AtlasPageState.MODAL

    /**
     * Effective layout for book-bearing contexts. RC2.1 binds adaptive grid as the default in
     * Standard and E-ink; explicit dense/compact overrides remain available to the reviewer.
     */
    val effectiveLayout: AtlasLayout
        get() = layout ?: AtlasLayout.GRID

    /** True when every transition commits immediately (constitution §11.2). */
    val instantMotion: Boolean
        get() = profile == AtlasProfile.EINK || reducedMotion
}
