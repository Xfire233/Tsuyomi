/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.model

/**
 * Review states of a route (Atlas Spec §2.2 state legend). Exactly one *primary* state owns the
 * content area at a time (constitution §9.1); the remaining entries are overlays or content
 * modifiers captured on top of CONTENT.
 */
enum class AtlasPageState(val extraKey: String, val primary: Boolean) {
    LOADING("loading", primary = true),
    CONTENT("content", primary = true),
    EMPTY("empty", primary = true),
    ERROR("error", primary = true),

    /** Offline overlay banner over cached content (Off). */
    OFFLINE("offline", primary = false),

    /** Refreshing overlay banner over content (Ref). */
    REFRESHING("refreshing", primary = false),

    /** Selection mode with the replacement SelectionAppBar (Sel). */
    SELECTION("selection", primary = false),

    /** Mutation working/success banner over content (Mut). */
    MUTATION("mutation", primary = false),

    /** Unresolved remote-operation banner (Unr). */
    UNRESOLVED("unresolved", primary = false),

    /** The route's modal layer rendered open (Mod). */
    MODAL("modal", primary = false),
    ;

    companion object {
        fun parse(raw: String?): AtlasPageState? {
            val value = raw?.trim()?.lowercase().orEmpty()
            if (value.isEmpty()) return null
            return entries.firstOrNull { it.extraKey == value || it.name.lowercase() == value }
        }
    }
}
