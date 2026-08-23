/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.model

/**
 * Display profiles under review (constitution §0). Same behavior, profile-specific presentation:
 * routes, state trees, and fixtures are identical; only rendering and motion policy differ.
 */
enum class AtlasProfile(val extraKey: String) {
    STANDARD("standard"),
    EINK("eink"),
    ;

    companion object {
        fun parse(raw: String?): AtlasProfile? = when (raw?.trim()?.lowercase()) {
            "standard" -> STANDARD
            "eink", "e-ink" -> EINK
            else -> null
        }
    }
}
