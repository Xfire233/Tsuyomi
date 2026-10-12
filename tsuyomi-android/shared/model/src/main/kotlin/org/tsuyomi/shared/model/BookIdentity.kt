// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.shared.model


/**
 * Stable origin-qualified identity for a book.
 *
 * Database row IDs, display names, and source implementation details are deliberately excluded.
 */
sealed interface BookIdentity : Comparable<BookIdentity> {
    /** A book owned by an installed or previously installed remote source. */
    data class Remote(
        val sourceId: String,
        val remoteBookId: String,
    ) : BookIdentity {
        init {
            require(SOURCE_ID.matches(sourceId)) { "sourceId must match the protocol source-id grammar" }
            requireRemoteId(remoteBookId, "remoteBookId")
        }

        override val storageKey: String
            get() = "r${sourceId.toByteArray(Charsets.UTF_8).size}:$sourceId${remoteBookId.toByteArray(Charsets.UTF_8).size}:$remoteBookId"

        override fun compareTo(other: BookIdentity): Int = when (other) {
            is Remote -> compareValuesBy(this, other, Remote::sourceId, Remote::remoteBookId)
            is Local -> -1
        }
    }

    /** A host-owned local publication with a portable canonical UUID identity. */
    data class Local(
        val publicationId: String,
    ) : BookIdentity {
        init {
            requireCanonicalUuid(publicationId, "publicationId")
        }

        override val storageKey: String
            get() = "l${publicationId.toByteArray(Charsets.UTF_8).size}:$publicationId"

        override fun compareTo(other: BookIdentity): Int = when (other) {
            is Remote -> 1
            is Local -> publicationId.compareTo(other.publicationId)
        }
    }

    /** Collision-free, origin-tagged key for relational storage. */
    val storageKey: String

    companion object {
        private val SOURCE_ID = Regex("^[a-z0-9](?:[a-z0-9.-]{0,126}[a-z0-9])?$")
        private val CANONICAL_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

        /** Validates a protocol remote identifier without normalizing it. */
        @JvmStatic
        fun requireRemoteId(value: String, fieldName: String = "remoteId") {
            require(value.codePointCount(0, value.length) in 1..1024) {
                "$fieldName must contain 1..1024 Unicode code points"
            }
        }

        /** Validates an opaque UUID without normalization or assigning resource authority. */
        @JvmStatic
        fun requireCanonicalUuid(value: String, fieldName: String) {
            require(CANONICAL_UUID.matches(value)) { "$fieldName must be a canonical lowercase UUID" }
        }
    }
}
