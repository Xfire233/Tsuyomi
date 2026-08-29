/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.review

import android.content.Context
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tsuyomi.prototype.uiatlas.BuildConfig

class ReviewRepository(context: Context, private val persistent: Boolean) {
    val liveBridge = ReviewLiveBridge(context.applicationContext, persistent)
    private val schemaVersion = BuildConfig.PROTOTYPE_REVIEW_SCHEMA_VERSION
    private val buildId = BuildConfig.PROTOTYPE_BUILD_ID
    private val currentFile = File(context.noBackupFilesDir, "interactive-review-comments-v$schemaVersion.json")
    private val store = ReviewSnapshotStore(currentFile)
    private val legacyStore = ReviewSnapshotStore(
        File(context.noBackupFilesDir, "interactive-review-comments-v${schemaVersion - 1}.json"),
    )
    private val lock = Any()
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")
    private val _snapshot = MutableStateFlow(
        if (persistent) loadSnapshot().ensureBuild(buildId)
        else ReviewSnapshot(schemaVersion, emptyMap()).ensureBuild(buildId),
    )

    val snapshot: StateFlow<ReviewSnapshot> = _snapshot.asStateFlow()

    fun active(): ReviewBuildState = _snapshot.value.builds[buildId] ?: ReviewBuildState()

    fun comment(nodeId: String): ReviewNodeComment? = active().nodeComments[nodeId]

    fun progress(nodeId: String): ReviewNodeProgress = active().progress[nodeId] ?: ReviewNodeProgress()

    fun staleBuildIds(): List<String> = _snapshot.value.builds.keys.filterNot { it == buildId }.sorted()

    fun markVisited(nodeId: String) = updateProgress(nodeId) { progress ->
        progress.copy(visitedAt = Instant.now().toString())
    }

    fun markAiTriaged(nodeId: String) = updateProgress(nodeId) { progress ->
        progress.copy(aiTriagedAt = Instant.now().toString())
    }

    fun markHumanReviewed(nodeId: String) = updateActive { state ->
        if (state.controlMode != ReviewControlMode.HUMAN || !canFinalizeInAtlas(nodeId)) return@updateActive state
        val current = state.progress[nodeId] ?: ReviewNodeProgress()
        state.copy(
            progress = state.progress + (
                nodeId to current.copy(humanReviewedAt = Instant.now().toString())
            ),
        )
    }

    fun setVerdict(nodeId: String, verdict: ReviewVerdict) = updateActive { state ->
        if (state.controlMode != ReviewControlMode.HUMAN || !canFinalizeInAtlas(nodeId)) return@updateActive state
        val current = state.progress[nodeId] ?: ReviewNodeProgress()
        val now = Instant.now().toString()
        state.copy(
            progress = state.progress + (
                nodeId to current.copy(
                    humanReviewedAt = current.humanReviewedAt ?: now,
                    approvedAt = if (verdict == ReviewVerdict.ACCEPT) now else null,
                    verdict = verdict,
                )
            ),
        )
    }

    fun attachEvidence(nodeId: String, visualHash: String?, interactionHash: String?) = updateProgress(nodeId) { progress ->
        val normalizedVisual = visualHash?.trim()?.lowercase()?.takeIf(sha256Pattern::matches)
        val normalizedInteraction = interactionHash?.trim()?.lowercase()?.takeIf(sha256Pattern::matches)
        progress.copy(
            visualEvidenceHash = normalizedVisual,
            interactionEvidenceHash = normalizedInteraction,
        )
    }

    fun setControlMode(mode: ReviewControlMode) = updateActive { state ->
        state.copy(controlMode = mode)
    }

    fun saveNodeComment(
        nodeId: String,
        route: String,
        comment: String,
        author: ReviewCommentAuthor,
        context: ReviewContext,
    ) = updateActive { state ->
        val comments = state.nodeComments.toMutableMap()
        if (comment.isBlank()) {
            comments.remove(nodeId)
        } else {
            comments[nodeId] = ReviewNodeComment(nodeId, route, comment.trimEnd(), author, context)
        }
        state.copy(nodeComments = comments.toMap())
    }

    fun saveWholePrototypeComment(comment: String) = updateActive { state ->
        state.copy(wholePrototypeComment = comment.trimEnd())
    }

    fun clearActiveComments() = updateActive { state ->
        state.copy(nodeComments = emptyMap(), wholePrototypeComment = "")
    }

    private fun canFinalizeInAtlas(nodeId: String): Boolean =
        ReviewNodeCatalog.byId[nodeId]?.evidenceStage == ReviewEvidenceStage.ATLAS_UI

    private fun updateProgress(nodeId: String, transform: (ReviewNodeProgress) -> ReviewNodeProgress) = updateActive { state ->
        val current = state.progress[nodeId] ?: ReviewNodeProgress()
        state.copy(progress = state.progress + (nodeId to transform(current)))
    }

    private fun updateActive(transform: (ReviewBuildState) -> ReviewBuildState) {
        synchronized(lock) {
            val current = _snapshot.value.ensureBuild(buildId)
            val updated = current.copy(builds = current.builds + (buildId to transform(current.builds.getValue(buildId))))
            _snapshot.value = updated
            if (persistent) store.write(updated)
        }
    }

    private fun loadSnapshot(): ReviewSnapshot {
        store.read(schemaVersion)?.let { return it }
        if (schemaVersion > 1) {
            legacyStore.read(schemaVersion - 1)?.let { legacy ->
                val migrated = legacy.copy(schemaVersion = schemaVersion)
                store.write(migrated)
                return migrated
            }
        }
        return ReviewSnapshot(schemaVersion, emptyMap())
    }

    private fun ReviewSnapshot.ensureBuild(id: String): ReviewSnapshot =
        if (id in builds) this else copy(builds = builds + (id to ReviewBuildState()))
}
