// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.reader.engine

import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.locator.LocatorPrecision
import org.tsuyomi.shared.locator.ReaderLocator

/**
 * Non-durable handoff from a settled source surface to a compatible target
 * presentation. A snapshot is intentionally invalid outside its exact
 * provenance; it is never a progress record.
 */
data class SettledPositionSnapshot(
    val locator: ReaderLocator?,
    val precision: LocatorPrecision,
    val document: DocumentIdentity,
    val documentRevision: String,
    val contentDigest: String,
    val documentEpoch: Long,
    val sessionEpoch: Long,
    val layoutKey: LayoutKey,
    val layoutRevision: Long,
    val navigationEpoch: Long,
    val visualCommitWitness: VisualCommitWitness,
) {
    init {
        require(documentRevision.isNotEmpty()) { "documentRevision must not be empty" }
        require(document.revision == null || document.revision == documentRevision) {
            "document revision must agree with document identity"
        }
        require(layoutRevision >= 0) { "layoutRevision must not be negative" }
        val capturedEpochs = epochs
        require(visualCommitWitness.epochs == capturedEpochs) {
            "visual witness must prove this snapshot's exact provenance"
        }
        when (precision) {
            LocatorPrecision.EXACT -> {
                require(locator != null && locator.precision == LocatorPrecision.EXACT) {
                    "an exact snapshot requires a complete exact locator"
                }
            }
            LocatorPrecision.DEGRADED -> require(locator != null) {
                "a degraded snapshot still requires a locator"
            }
            LocatorPrecision.UNAVAILABLE -> require(locator == null) {
                "an unavailable snapshot must not carry a locator"
            }
        }
        locator?.let {
            require(it.document.namesSameDocumentAs(document)) {
                "a locator cannot be captured for a different document"
            }
            require(it.document.revision == null || it.document.revision == documentRevision) {
                "a locator revision must agree with the captured document revision"
            }
        }
    }

    val epochs: ReaderEpochs
        get() = ReaderEpochs(
            document = document,
            documentRevision = documentRevision,
            contentDigest = contentDigest,
            documentEpoch = documentEpoch,
            sessionEpoch = sessionEpoch,
            layoutKey = layoutKey,
            layoutEpoch = layoutRevision,
            navigationEpoch = navigationEpoch,
        )

    val isExactSettled: Boolean
        get() = precision == LocatorPrecision.EXACT

    fun isCompatibleWith(expected: ReaderEpochs): Boolean = epochs == expected
}

/** Outcome of admitting a capture into the in-memory settled-capture cache. */
enum class CaptureAdmission {
    ACCEPTED,
    REJECTED_BY_EXACT_PRECEDENCE,
}

/**
 * Keeps only the most recent settled capture. For one document/revision, a
 * degraded or unavailable observation never displaces a previously settled
 * exact capture.
 */
class SettledPositionCache {
    private var latest: SettledPositionSnapshot? = null

    fun current(): SettledPositionSnapshot? = latest

    fun record(snapshot: SettledPositionSnapshot): CaptureAdmission {
        val previous = latest
        if (
            previous != null &&
            previous.document.namesSameDocumentAs(snapshot.document) &&
            previous.documentRevision == snapshot.documentRevision &&
            previous.contentDigest == snapshot.contentDigest &&
            previous.documentEpoch == snapshot.documentEpoch &&
            previous.sessionEpoch == snapshot.sessionEpoch &&
            previous.navigationEpoch == snapshot.navigationEpoch &&
            previous.isExactSettled &&
            !snapshot.isExactSettled
        ) {
            return CaptureAdmission.REJECTED_BY_EXACT_PRECEDENCE
        }
        latest = snapshot
        return CaptureAdmission.ACCEPTED
    }

    /**
     * Selects the only snapshot a presentation transaction may restore. The
     * currently mounted exact capture always wins. A cache fallback is legal
     * only while that mounted renderer is rebuilding.
     */
    fun selectForPresentationSwitch(
        source: ReaderEpochs,
        mountedCapture: SettledPositionSnapshot?,
        mountedRendererIsRebuilding: Boolean,
    ): SettledPositionSnapshot? {
        if (mountedCapture?.isExactSettled == true && mountedCapture.isCompatibleWith(source)) {
            return mountedCapture
        }
        if (!mountedRendererIsRebuilding) return null
        return latest?.takeIf { it.isExactSettled && it.isCompatibleWith(source) }
    }
}
