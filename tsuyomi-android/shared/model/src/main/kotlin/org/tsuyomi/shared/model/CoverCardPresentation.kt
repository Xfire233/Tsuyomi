/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.shared.model

/** User-selected presentation for shared Standard book grid cards. */
enum class CoverCardPresentation {
    /** Portrait-led card with a 5:7 outer geometry. */
    STANDARD,

    /** Landscape card with a complete portrait artwork lane and title lane. */
    WIDE,
}
