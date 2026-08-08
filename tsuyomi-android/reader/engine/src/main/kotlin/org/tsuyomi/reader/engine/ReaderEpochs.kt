// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.reader.engine

import org.tsuyomi.shared.locator.DocumentIdentity

/** Metric-affecting layout identity. Color-only changes must not create a new key. */
data class LayoutKey(val value: String) {
    init {
        require(value.isNotBlank()) { "layout key must not be blank" }
    }
}

/**
 * Immutable provenance carried by all reader work and visual commits.
 *
 * Any changed member makes an asynchronous result stale. The document revision
 * is explicit even though a durable [DocumentIdentity] may omit it for backward
 * protocol compatibility.
 */
data class ReaderEpochs(
    val document: DocumentIdentity,
    val documentRevision: String,
    val contentDigest: String,
    val documentEpoch: Long,
    val sessionEpoch: Long,
    val layoutKey: LayoutKey,
    val layoutEpoch: Long,
    val navigationEpoch: Long,
) {
    init {
        requireBounded(documentRevision, "documentRevision", 256)
        require(document.revision == null || document.revision == documentRevision) {
            "document revision must agree with document identity when identity supplies one"
        }
        require(SHA_256.matches(contentDigest)) { "contentDigest must be a lowercase SHA-256 digest" }
        require(documentEpoch >= 0) { "documentEpoch must not be negative" }
        require(sessionEpoch >= 0) { "sessionEpoch must not be negative" }
        require(layoutEpoch >= 0) { "layoutEpoch must not be negative" }
        require(navigationEpoch >= 0) { "navigationEpoch must not be negative" }
    }

    /** Architecture terminology for [layoutEpoch]. */
    val layoutRevision: Long
        get() = layoutEpoch

    companion object {
        private val SHA_256 = Regex("^[a-f0-9]{64}$")
    }
}

/** Evidence that a particular target was actually committed to a visual surface. */
data class VisualCommitWitness(
    val ownerId: Long,
    val visualEpoch: Long,
    val targetId: String,
    val epochs: ReaderEpochs,
) {
    init {
        require(ownerId >= 0) { "ownerId must not be negative" }
        require(visualEpoch >= 0) { "visualEpoch must not be negative" }
        require(targetId.isNotBlank()) { "targetId must not be blank" }
    }
}

private fun requireBounded(value: String, fieldName: String, maximumLength: Int) {
    require(value.codePointCount(0, value.length) in 1..maximumLength) {
        "$fieldName must contain 1..$maximumLength Unicode code points"
    }
}
