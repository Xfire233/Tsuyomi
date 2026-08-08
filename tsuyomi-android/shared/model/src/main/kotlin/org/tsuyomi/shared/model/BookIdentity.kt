// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.shared.model

/**
 * Stable, host-owned identity for a remotely sourced book.
 *
 * Database row IDs, display names, and source implementation details are deliberately excluded.
 */
data class BookIdentity(
    val sourceId: String,
    val remoteBookId: String,
) : Comparable<BookIdentity> {
    init {
        require(SOURCE_ID.matches(sourceId)) { "sourceId must match the protocol source-id grammar" }
        requireRemoteId(remoteBookId, "remoteBookId")
    }

    override fun compareTo(other: BookIdentity): Int =
        compareValuesBy(this, other, BookIdentity::sourceId, BookIdentity::remoteBookId)

    companion object {
        private val SOURCE_ID = Regex("^[a-z0-9](?:[a-z0-9.-]{0,126}[a-z0-9])?$")

        /** Validates a protocol remote identifier without normalizing it. */
        @JvmStatic
        fun requireRemoteId(value: String, fieldName: String = "remoteId") {
            require(value.codePointCount(0, value.length) in 1..1024) {
                "$fieldName must contain 1..1024 Unicode code points"
            }
        }
    }
}
