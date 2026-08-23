/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.model

import androidx.compose.runtime.Immutable

/**
 * Cover render state for the atlas (fixture analog of the §2.3 contract). Every state is pure
 * data; rendering is a deterministic Canvas draw in the components layer. No bitmaps, files, or
 * network payloads exist anywhere in the atlas.
 */
@Immutable
sealed interface AtlasCover {
    /** Procedurally generated flat-color cover keyed by [seed] (Atlas Spec F10). */
    @Immutable
    data class Generated(val seed: Long) : AtlasCover

    /** Generated cover retained past its freshness window; rendered with a stale caption. */
    @Immutable
    data class Stale(val seed: Long) : AtlasCover

    /** No cover exists for this book; the title/source fallback renders. */
    @Immutable
    data object Absent : AtlasCover

    /** Cover resolution failed; the title/source fallback renders (no broken-image glyph). */
    @Immutable
    data object Failed : AtlasCover
}

/**
 * Immutable row/card model rendered by the shared `BookListItemRow` / `BookGridCard` renderers
 * (constitution §5.2/§5.3 information order). All values are pre-formatted fixture strings so
 * renderers never derive, fetch, or format anything themselves.
 */
@Immutable
data class AtlasBookIdentity(val sourceId: String, val remoteBookId: String)

@Immutable
data class AtlasBook(
    val id: String,
    val title: String,
    val authors: String?,
    val cover: AtlasCover,
    /** Owning synthetic source; null for local-only books. */
    val source: AtlasSource?,
    /** Exact source-qualified identity used only for remote/local binding and aggregation. */
    val identity: AtlasBookIdentity? = source?.let { AtlasBookIdentity(it.id, id) },
    /** Semantic progress line, e.g. `读至 第12章 · 43%`; null when unread. */
    val progressLabel: String?,
    /** Unread update count; 0 when the book has no pending chapters. */
    val unreadUpdates: Int = 0,
    val readLater: Boolean = false,
    /** True when the owning source is dormant (degraded provenance shown, never hidden). */
    val dormantSource: Boolean = false,
    /** Last local reading activity; null when this book has never been opened. */
    val lastReadAtEpochMillis: Long? = null,
    /** Local rating 1–5; null when unrated. */
    val rating: Int? = null,
    /** Complete local tags available to the current bounded fixture surface. */
    val tags: List<String> = emptyList(),
) {
    init {
        require(rating == null || rating in 1..5) { "rating must be 1..5, was $rating" }
        require(unreadUpdates >= 0) { "unreadUpdates must be >= 0" }
    }
}
