/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.review

import androidx.compose.runtime.Immutable

@Immutable
data class ReviewContext(
    val profile: String,
    val theme: String,
    val state: String,
    val overlay: String?,
    val layout: String?,
    val libraryView: String?,
    val lastAction: String?,
    val recentEvents: List<String>,
    val updatedAt: String,
)

enum class ReviewCommentAuthor {
    AI,
    HUMAN,
    MIXED,
}

enum class ReviewVerdict {
    PENDING,
    ACCEPT,
    REVISE,
    BLOCKED,
    NOT_APPLICABLE,
}

enum class ReviewControlMode {
    AUTOMATION,
    PAUSED,
    HUMAN,
}

@Immutable
data class ReviewNodeComment(
    val nodeId: String,
    val route: String,
    val comment: String,
    val author: ReviewCommentAuthor,
    val lastEditedContext: ReviewContext,
)

@Immutable
data class ReviewNodeProgress(
    val visitedAt: String? = null,
    val aiTriagedAt: String? = null,
    val humanReviewedAt: String? = null,
    val approvedAt: String? = null,
    val verdict: ReviewVerdict = ReviewVerdict.PENDING,
    val visualEvidenceHash: String? = null,
    val interactionEvidenceHash: String? = null,
)

@Immutable
data class ReviewBuildState(
    val nodeComments: Map<String, ReviewNodeComment> = emptyMap(),
    val wholePrototypeComment: String = "",
    val progress: Map<String, ReviewNodeProgress> = emptyMap(),
    val controlMode: ReviewControlMode = ReviewControlMode.AUTOMATION,
)

@Immutable
data class ReviewSnapshot(
    val schemaVersion: Int,
    val builds: Map<String, ReviewBuildState>,
)
