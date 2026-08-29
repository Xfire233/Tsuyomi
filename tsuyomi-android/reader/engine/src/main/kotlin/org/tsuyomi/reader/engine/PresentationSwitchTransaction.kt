// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.reader.engine

/** Lifecycle of a compatible presentation switch. */
enum class PresentationSwitchState {
    AWAITING_TARGET_VISUAL_COMMIT,
    COMMITTED,
    CANCELLED,
}

/** Why a switch could not start or a visual commit was rejected. */
enum class PresentationSwitchRejection {
    NO_EXACT_SETTLED_CAPTURE,
    INCOMPATIBLE_DOCUMENT,
    STALE_EPOCHS,
    STALE_OR_FOREIGN_WITNESS,
    CANCELLED,
    ALREADY_COMMITTED,
}

sealed interface PresentationSwitchStart {
    data class Started(val transaction: PresentationSwitchTransaction) : PresentationSwitchStart
    data class Rejected(val reason: PresentationSwitchRejection) : PresentationSwitchStart
}

sealed interface PresentationSwitchCommit {
    data class Accepted(val snapshot: SettledPositionSnapshot) : PresentationSwitchCommit
    data class Rejected(val reason: PresentationSwitchRejection) : PresentationSwitchCommit
}

/** Complete immutable input for starting one compatible presentation handoff. */
data class PresentationSwitchRequest(
    val transactionId: Long,
    val targetId: String,
    val sourceEpochs: ReaderEpochs,
    val targetEpochs: ReaderEpochs,
    val captures: SettledPositionCache,
    val mountedCapture: SettledPositionSnapshot?,
    val mountedRendererIsRebuilding: Boolean,
)

/**
 * A cancellation-aware handoff across compatible reader presentations.
 *
 * This class has no persistence hook: successful visual commitment merely
 * authorizes the caller to resume normal progress writes and preloading.
 */
class PresentationSwitchTransaction private constructor(
    val transactionId: Long,
    val targetId: String,
    val sourceEpochs: ReaderEpochs,
    val targetEpochs: ReaderEpochs,
    val capture: SettledPositionSnapshot,
) {
    var state: PresentationSwitchState = PresentationSwitchState.AWAITING_TARGET_VISUAL_COMMIT
        private set

    val isActive: Boolean
        get() = state == PresentationSwitchState.AWAITING_TARGET_VISUAL_COMMIT

    /** Rejects future commits. Cancellation is idempotent. */
    fun cancel(): Boolean {
        if (!isActive) return false
        state = PresentationSwitchState.CANCELLED
        return true
    }

    /**
     * Cancels when any document, session, layout, or navigation provenance has
     * changed since target mount.
     */
    fun invalidateIfStale(currentTargetEpochs: ReaderEpochs): Boolean {
        if (!isActive || currentTargetEpochs == targetEpochs) return false
        state = PresentationSwitchState.CANCELLED
        return true
    }

    /**
     * Accepts only the target surface's current witness. A rejected old witness
     * leaves the transaction open so its current target can still commit.
     */
    fun acceptVisualCommit(
        witness: VisualCommitWitness,
        currentTargetEpochs: ReaderEpochs,
    ): PresentationSwitchCommit {
        when (state) {
            PresentationSwitchState.CANCELLED -> {
                return PresentationSwitchCommit.Rejected(PresentationSwitchRejection.CANCELLED)
            }
            PresentationSwitchState.COMMITTED -> {
                return PresentationSwitchCommit.Rejected(PresentationSwitchRejection.ALREADY_COMMITTED)
            }
            PresentationSwitchState.AWAITING_TARGET_VISUAL_COMMIT -> Unit
        }
        if (currentTargetEpochs != targetEpochs) {
            state = PresentationSwitchState.CANCELLED
            return PresentationSwitchCommit.Rejected(PresentationSwitchRejection.STALE_EPOCHS)
        }
        if (
            witness.ownerId != transactionId ||
            witness.targetId != targetId ||
            witness.epochs != targetEpochs
        ) {
            return PresentationSwitchCommit.Rejected(PresentationSwitchRejection.STALE_OR_FOREIGN_WITNESS)
        }
        state = PresentationSwitchState.COMMITTED
        return PresentationSwitchCommit.Accepted(capture)
    }

    companion object {
        /**
         * Freezes a compatible transition. The source and target may use
         * different metric layout keys/epochs, but all document/session/
         * navigation identity must remain identical.
         */
        fun begin(request: PresentationSwitchRequest): PresentationSwitchStart {
            require(request.transactionId >= 0) { "transactionId must not be negative" }
            require(request.targetId.isNotBlank()) { "targetId must not be blank" }
            if (!areCompatiblePresentationEpochs(request.sourceEpochs, request.targetEpochs)) {
                return PresentationSwitchStart.Rejected(PresentationSwitchRejection.INCOMPATIBLE_DOCUMENT)
            }
            val capture = request.captures.selectForPresentationSwitch(
                source = request.sourceEpochs,
                mountedCapture = request.mountedCapture,
                mountedRendererIsRebuilding = request.mountedRendererIsRebuilding,
            ) ?: return PresentationSwitchStart.Rejected(PresentationSwitchRejection.NO_EXACT_SETTLED_CAPTURE)
            return PresentationSwitchStart.Started(
                PresentationSwitchTransaction(
                    transactionId = request.transactionId,
                    targetId = request.targetId,
                    sourceEpochs = request.sourceEpochs,
                    targetEpochs = request.targetEpochs,
                    capture = capture,
                ),
            )
        }

        private fun areCompatiblePresentationEpochs(
            source: ReaderEpochs,
            target: ReaderEpochs,
        ): Boolean =
            source.document.namesSameDocumentAs(target.document) &&
                source.documentRevision == target.documentRevision &&
                source.contentDigest == target.contentDigest &&
                source.documentEpoch == target.documentEpoch &&
                source.sessionEpoch == target.sessionEpoch &&
                source.navigationEpoch == target.navigationEpoch
    }
}
