// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.reader.engine

import org.tsuyomi.shared.locator.ReaderLocator

/** A semantic target inside an immutable preview plan. */
data class PreviewTarget(
    val id: String,
    val locator: ReaderLocator,
) {
    init {
        require(id.isNotBlank()) { "preview target id must not be blank" }
    }
}

/** A preview target request, identified within its session by [generation]. */
data class PreviewRequest(
    val target: PreviewTarget,
    val generation: Long,
) {
    init {
        require(generation > 0) { "preview request generation must be positive" }
    }
}

/**
 * Frozen measured geometry available to one preview session.
 *
 * The defensive copy prevents a UI-owned mutable collection from changing the
 * plan after session creation.
 */
class FrozenPreviewPlan(
    val revision: Long,
    targets: Collection<PreviewTarget>,
) {
    val targets: Set<PreviewTarget> = targets.toSet()

    init {
        require(revision >= 0) { "preview plan revision must not be negative" }
    }

    fun contains(target: PreviewTarget): Boolean = target in targets
}

sealed interface PreviewState {
    data object Idle : PreviewState
    data class Preparing(val target: PreviewTarget) : PreviewState
    data class Ready(val target: PreviewTarget) : PreviewState
    data object Released : PreviewState
    data object Cancelled : PreviewState
}

enum class PreviewReleaseRejection {
    NO_TARGET,
    TARGET_NOT_READY,
    STALE_EPOCHS,
    STALE_OR_FOREIGN_WITNESS,
    CANCELLED,
    ALREADY_RELEASED,
}

sealed interface PreviewRelease {
    /** The caller may now perform exactly one semantic navigation to [target]. */
    data class Committed(val target: PreviewTarget) : PreviewRelease
    data class Rejected(val reason: PreviewReleaseRejection) : PreviewRelease
}

/** Evidence that the independent preview surface visually committed a target. */
data class PreviewVisualWitness(
    val sessionId: Long,
    val target: PreviewTarget,
    val epochs: ReaderEpochs,
    val planRevision: Long,
    val requestGeneration: Long,
    val visualEpoch: Long,
) {
    init {
        require(sessionId >= 0) { "sessionId must not be negative" }
        require(planRevision >= 0) { "planRevision must not be negative" }
        require(requestGeneration > 0) { "requestGeneration must be positive" }
        require(visualEpoch >= 0) { "visualEpoch must not be negative" }
    }
}

/**
 * Transient, frozen-plan scrub state. It deliberately owns neither active
 * reader position nor persistent history; only [release] authorizes a single
 * semantic navigation for its latest visually committed target.
 */
class PreviewSession(
    val sessionId: Long,
    val epochs: ReaderEpochs,
    val plan: FrozenPreviewPlan,
) {
    private var latestInputFrame: Long = -1
    private var latestRequest: PreviewRequest? = null

    var state: PreviewState = PreviewState.Idle
        private set

    val latestTarget: PreviewTarget?
        get() = latestRequest?.target

    /** The current request, including the generation a visual witness must carry. */
    val currentRequest: PreviewRequest?
        get() = latestRequest

    init {
        require(sessionId >= 0) { "sessionId must not be negative" }
        plan.targets.forEach(::requireTargetForSession)
    }

    /**
     * Coalesces pointer input by frame: later updates in the same frame replace
     * earlier ones, while an obsolete frame cannot move the preview backward.
     * Each accepted logical target change receives the next request generation;
     * value-equal repeats retain their current visual witness.
     */
    fun offerTarget(inputFrame: Long, target: PreviewTarget): PreviewState {
        require(inputFrame >= 0) { "inputFrame must not be negative" }
        if (state == PreviewState.Cancelled || state == PreviewState.Released) return state
        if (inputFrame < latestInputFrame) return state
        requireTargetForSession(target)
        latestInputFrame = inputFrame
        if (latestRequest?.target != target) {
            val previousGeneration = latestRequest?.generation ?: 0
            check(previousGeneration < Long.MAX_VALUE) { "preview request generation exhausted" }
            latestRequest = PreviewRequest(target, previousGeneration + 1)
        }
        state = if (plan.contains(target)) PreviewState.Ready(target) else PreviewState.Preparing(target)
        return state
    }

    /** Convenience overload for clients which already coalesce input per frame. */
    fun requestTarget(target: PreviewTarget): PreviewState = offerTarget(latestInputFrame + 1, target)

    /** User interaction with the active reader always wins over preview work. */
    fun cancelForUserInteraction(): Boolean = cancel()

    /** Explicit cancellation is idempotent and makes visual witnesses unusable. */
    fun cancel(): Boolean {
        if (state == PreviewState.Cancelled || state == PreviewState.Released) return false
        state = PreviewState.Cancelled
        return true
    }

    /** Cancels when document, session, layout, navigation, or frozen plan changed. */
    fun invalidateIfStale(currentEpochs: ReaderEpochs, currentPlanRevision: Long): Boolean {
        if (state == PreviewState.Cancelled || state == PreviewState.Released) return false
        if (currentEpochs == epochs && currentPlanRevision == plan.revision) return false
        state = PreviewState.Cancelled
        return true
    }

    /**
     * Performs no persistence itself. A successful result simply grants the
     * coordinator one release-time semantic navigation; pointer movement never
     * emits a commit.
     */
    fun release(
        witness: PreviewVisualWitness?,
        currentEpochs: ReaderEpochs,
        currentPlanRevision: Long,
    ): PreviewRelease {
        when (state) {
            PreviewState.Cancelled -> return PreviewRelease.Rejected(PreviewReleaseRejection.CANCELLED)
            PreviewState.Released -> return PreviewRelease.Rejected(PreviewReleaseRejection.ALREADY_RELEASED)
            PreviewState.Idle -> return PreviewRelease.Rejected(PreviewReleaseRejection.NO_TARGET)
            is PreviewState.Preparing -> return PreviewRelease.Rejected(PreviewReleaseRejection.TARGET_NOT_READY)
            is PreviewState.Ready -> Unit
        }
        if (currentEpochs != epochs || currentPlanRevision != plan.revision) {
            state = PreviewState.Cancelled
            return PreviewRelease.Rejected(PreviewReleaseRejection.STALE_EPOCHS)
        }
        val request = latestRequest ?: return PreviewRelease.Rejected(PreviewReleaseRejection.NO_TARGET)
        val target = request.target
        if (
            witness == null ||
            witness.sessionId != sessionId ||
            witness.target != target ||
            witness.epochs != epochs ||
            witness.planRevision != plan.revision ||
            witness.requestGeneration != request.generation
        ) {
            return PreviewRelease.Rejected(PreviewReleaseRejection.STALE_OR_FOREIGN_WITNESS)
        }
        state = PreviewState.Released
        return PreviewRelease.Committed(target)
    }

    private fun requireTargetForSession(target: PreviewTarget) {
        require(target.locator.document.namesSameDocumentAs(epochs.document)) {
            "preview target must belong to the active document"
        }
        require(
            target.locator.document.revision == null ||
                target.locator.document.revision == epochs.documentRevision,
        ) { "preview target revision must match the frozen session" }
    }
}
