/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.review

import android.content.Context
import android.os.Build
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.tsuyomi.prototype.uiatlas.BuildConfig

object ReviewJsonExporter {
    fun export(context: Context, snapshot: ReviewSnapshot, includeStaleBuilds: Boolean): String {
        val active = snapshot.builds[BuildConfig.PROTOTYPE_BUILD_ID] ?: ReviewBuildState()
        val metrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        val staleIds = snapshot.builds.keys.filterNot { it == BuildConfig.PROTOTYPE_BUILD_ID }.sorted()
        return buildJsonObject {
            put("schema", JsonPrimitive("tsuyomi-interactive-prototype-review-v2"))
            put("provisional", JsonPrimitive(true))
            put("productionAuthorized", JsonPrimitive(false))
            put("build", buildJsonObject {
                put("applicationId", JsonPrimitive(BuildConfig.APPLICATION_ID))
                put("versionName", JsonPrimitive(BuildConfig.VERSION_NAME))
                put("versionCode", JsonPrimitive(BuildConfig.VERSION_CODE))
                put("buildId", JsonPrimitive(BuildConfig.PROTOTYPE_BUILD_ID))
                put("designRulesSha256", JsonPrimitive(BuildConfig.DESIGN_RULES_SHA256))
                put("dataSchemaVersion", JsonPrimitive(BuildConfig.PROTOTYPE_DATA_SCHEMA_VERSION))
                put("reviewSchemaVersion", JsonPrimitive(BuildConfig.PROTOTYPE_REVIEW_SCHEMA_VERSION))
                put("reviewCatalogVersion", JsonPrimitive(ReviewNodeCatalog.VERSION))
            })
            put("device", buildJsonObject {
                put("sdk", JsonPrimitive(Build.VERSION.SDK_INT))
                put("widthPx", JsonPrimitive(metrics.widthPixels))
                put("heightPx", JsonPrimitive(metrics.heightPixels))
                put("densityDpi", JsonPrimitive(metrics.densityDpi))
                put("fontScale", JsonPrimitive(configuration.fontScale))
                put("locale", JsonPrimitive(configuration.locales[0].toLanguageTag()))
            })
            put("reviewCatalog", buildJsonArray {
                ReviewNodeCatalog.nodes.forEach { node ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(node.id))
                        put("title", JsonPrimitive(node.title))
                        put("family", JsonPrimitive(node.family.name.lowercase()))
                        put("kind", JsonPrimitive(node.kind.name.lowercase()))
                        node.route?.let { put("route", JsonPrimitive(it.path)) }
                        put("requiredStates", buildJsonArray {
                            node.requiredStates.sortedBy { it.ordinal }.forEach { add(JsonPrimitive(it.extraKey)) }
                        })
                        put("operations", buildJsonArray { node.operations.forEach { add(JsonPrimitive(it)) } })
                        put("visualChecks", buildJsonArray { node.visualChecks.forEach { add(JsonPrimitive(it)) } })
                        put("humanOnlyChecks", buildJsonArray { node.humanOnlyChecks.forEach { add(JsonPrimitive(it)) } })
                    })
                }
            })
            put("nodeComments", buildJsonArray {
                active.nodeComments.toSortedMap().values.filter { it.comment.isNotBlank() }.forEach { comment ->
                    add(encodeComment(comment))
                }
                if (includeStaleBuilds) {
                    staleIds.forEach { staleId ->
                        snapshot.builds.getValue(staleId).nodeComments.toSortedMap().values
                            .filter { it.comment.isNotBlank() }
                            .forEach { comment -> add(encodeComment(comment, staleId)) }
                    }
                }
            })
            active.wholePrototypeComment.takeIf(String::isNotBlank)?.let {
                put("wholePrototypeComment", JsonPrimitive(it))
            }
            put("progress", buildJsonObject {
                active.progress.toSortedMap().forEach { (nodeId, progress) ->
                    put(nodeId, encodeProgress(progress))
                }
            })
            put("summary", buildJsonObject {
                put("totalNodes", JsonPrimitive(ReviewNodeCatalog.nodes.size))
                put("visited", JsonPrimitive(active.progress.count { it.value.visitedAt != null }))
                put("aiTriaged", JsonPrimitive(active.progress.count { it.value.aiTriagedAt != null }))
                put("humanReviewed", JsonPrimitive(active.progress.count { it.value.humanReviewedAt != null }))
                put("approved", JsonPrimitive(active.progress.count { it.value.approvedAt != null }))
            })
            put("controlMode", JsonPrimitive(active.controlMode.name.lowercase()))
            if (includeStaleBuilds && staleIds.isNotEmpty()) {
                put("includedStaleBuilds", buildJsonArray { staleIds.forEach { add(JsonPrimitive(it)) } })
            }
            put("exportedAt", JsonPrimitive(Instant.now().toString()))
        }.toString()
    }

    private fun encodeComment(comment: ReviewNodeComment, staleBuildId: String? = null): JsonObject = buildJsonObject {
        put("nodeId", JsonPrimitive(comment.nodeId))
        put("route", JsonPrimitive(comment.route))
        put("author", JsonPrimitive(comment.author.name.lowercase()))
        put("comment", JsonPrimitive(comment.comment))
        staleBuildId?.let { put("staleBuildId", JsonPrimitive(it)) }
        put("lastEditedContext", buildJsonObject {
            val value = comment.lastEditedContext
            put("profile", JsonPrimitive(value.profile))
            put("theme", JsonPrimitive(value.theme))
            put("state", JsonPrimitive(value.state))
            value.overlay?.let { put("overlay", JsonPrimitive(it)) }
            value.layout?.let { put("layout", JsonPrimitive(it)) }
            value.libraryView?.let { put("libraryView", JsonPrimitive(it)) }
            value.lastAction?.let { put("lastAction", JsonPrimitive(it)) }
            put("recentEvents", buildJsonArray { value.recentEvents.takeLast(32).forEach { add(JsonPrimitive(it)) } })
            put("updatedAt", JsonPrimitive(value.updatedAt))
        })
    }

    private fun encodeProgress(progress: ReviewNodeProgress): JsonObject = buildJsonObject {
        progress.visitedAt?.let { put("visitedAt", JsonPrimitive(it)) }
        progress.aiTriagedAt?.let { put("aiTriagedAt", JsonPrimitive(it)) }
        progress.humanReviewedAt?.let { put("humanReviewedAt", JsonPrimitive(it)) }
        progress.approvedAt?.let { put("approvedAt", JsonPrimitive(it)) }
        put("verdict", JsonPrimitive(progress.verdict.name.lowercase()))
        progress.visualEvidenceHash?.let { put("visualEvidenceHash", JsonPrimitive(it)) }
        progress.interactionEvidenceHash?.let { put("interactionEvidenceHash", JsonPrimitive(it)) }
    }
}
