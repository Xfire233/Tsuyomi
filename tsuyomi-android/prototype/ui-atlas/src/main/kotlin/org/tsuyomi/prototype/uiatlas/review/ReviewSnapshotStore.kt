/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.review

import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class ReviewSnapshotStore(file: File) {
    private val atomicFile = AtomicFile(file)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(expectedSchemaVersion: Int): ReviewSnapshot? {
        if (!atomicFile.baseFile.exists()) return null
        return runCatching {
            val root = json.parseToJsonElement(atomicFile.readFully().decodeToString()).jsonObject
            val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: return null
            if (schemaVersion != expectedSchemaVersion) return null
            val builds = root["builds"]?.jsonObject?.mapValues { (_, element) ->
                decodeBuild(element.jsonObject, schemaVersion)
            }.orEmpty()
            ReviewSnapshot(schemaVersion, builds)
        }.getOrNull()
    }

    fun write(snapshot: ReviewSnapshot) {
        val stream = atomicFile.startWrite()
        try {
            stream.write(encode(snapshot).toString().encodeToByteArray())
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun encode(snapshot: ReviewSnapshot): JsonObject = buildJsonObject {
        put("schemaVersion", JsonPrimitive(snapshot.schemaVersion))
        put("builds", buildJsonObject {
            snapshot.builds.toSortedMap().forEach { (buildId, state) -> put(buildId, encodeBuild(state)) }
        })
    }

    private fun encodeBuild(state: ReviewBuildState): JsonObject = buildJsonObject {
        put("nodeComments", buildJsonObject {
            state.nodeComments.toSortedMap().forEach { (nodeId, comment) ->
                put(nodeId, buildJsonObject {
                    put("nodeId", JsonPrimitive(comment.nodeId))
                    put("route", JsonPrimitive(comment.route))
                    put("comment", JsonPrimitive(comment.comment))
                    put("author", JsonPrimitive(comment.author.name.lowercase()))
                    put("lastEditedContext", encodeContext(comment.lastEditedContext))
                })
            }
        })
        put("wholePrototypeComment", JsonPrimitive(state.wholePrototypeComment))
        put("progress", buildJsonObject {
            state.progress.toSortedMap().forEach { (nodeId, progress) ->
                put(nodeId, buildJsonObject {
                    progress.visitedAt?.let { put("visitedAt", JsonPrimitive(it)) }
                    progress.aiTriagedAt?.let { put("aiTriagedAt", JsonPrimitive(it)) }
                    progress.humanReviewedAt?.let { put("humanReviewedAt", JsonPrimitive(it)) }
                    progress.approvedAt?.let { put("approvedAt", JsonPrimitive(it)) }
                    put("verdict", JsonPrimitive(progress.verdict.name.lowercase()))
                    progress.visualEvidenceHash?.let { put("visualEvidenceHash", JsonPrimitive(it)) }
                    progress.interactionEvidenceHash?.let { put("interactionEvidenceHash", JsonPrimitive(it)) }
                })
            }
        })
        put("controlMode", JsonPrimitive(state.controlMode.name.lowercase()))
    }

    private fun encodeContext(context: ReviewContext): JsonObject = buildJsonObject {
        put("profile", JsonPrimitive(context.profile))
        put("theme", JsonPrimitive(context.theme))
        put("state", JsonPrimitive(context.state))
        context.overlay?.let { put("overlay", JsonPrimitive(it)) }
        context.layout?.let { put("layout", JsonPrimitive(it)) }
        context.libraryView?.let { put("libraryView", JsonPrimitive(it)) }
        context.lastAction?.let { put("lastAction", JsonPrimitive(it)) }
        put("recentEvents", buildJsonArray { context.recentEvents.takeLast(32).forEach { add(JsonPrimitive(it)) } })
        put("updatedAt", JsonPrimitive(context.updatedAt))
    }

    private fun decodeBuild(value: JsonObject, schemaVersion: Int): ReviewBuildState {
        val commentsKey = if (schemaVersion >= 2) "nodeComments" else "pageComments"
        val progressKey = if (schemaVersion >= 2) "progress" else "coverage"
        return ReviewBuildState(
            nodeComments = value[commentsKey]?.jsonObject?.mapNotNull { (key, element) ->
                decodeComment(key, element.jsonObject, schemaVersion)?.let { it.nodeId to it }
            }?.toMap().orEmpty(),
            wholePrototypeComment = value["wholePrototypeComment"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            progress = value[progressKey]?.jsonObject?.mapNotNull { (key, element) ->
                decodeProgress(key, element.jsonObject, schemaVersion)
            }?.toMap().orEmpty(),
            controlMode = value["controlMode"]?.jsonPrimitive?.contentOrNull
                ?.let { raw -> ReviewControlMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
                ?: ReviewControlMode.AUTOMATION,
        )
    }

    private fun decodeComment(key: String, value: JsonObject, schemaVersion: Int): ReviewNodeComment? {
        val route = value["route"]?.jsonPrimitive?.contentOrNull ?: key
        val nodeId = if (schemaVersion >= 2) {
            value["nodeId"]?.jsonPrimitive?.contentOrNull ?: key
        } else {
            ReviewNodeCatalog.defaultForRoutePath(route)?.id ?: return null
        }
        val comment = value["comment"]?.jsonPrimitive?.contentOrNull ?: return null
        val context = value["lastEditedContext"]?.jsonObject?.let(::decodeContext) ?: return null
        val author = value["author"]?.jsonPrimitive?.contentOrNull
            ?.let { raw -> ReviewCommentAuthor.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
            ?: ReviewCommentAuthor.HUMAN
        return ReviewNodeComment(nodeId, route, comment, author, context)
    }

    private fun decodeProgress(key: String, value: JsonObject, schemaVersion: Int): Pair<String, ReviewNodeProgress>? {
        if (schemaVersion < 2) {
            val visitedAt = value["lastVisitedAt"]?.jsonPrimitive?.contentOrNull ?: return null
            val nodeId = ReviewNodeCatalog.defaultForRoutePath(key)?.id ?: return null
            return nodeId to ReviewNodeProgress(visitedAt = visitedAt)
        }
        val verdict = value["verdict"]?.jsonPrimitive?.contentOrNull
            ?.let { raw -> ReviewVerdict.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
            ?: ReviewVerdict.PENDING
        return key to ReviewNodeProgress(
            visitedAt = value["visitedAt"]?.jsonPrimitive?.contentOrNull,
            aiTriagedAt = value["aiTriagedAt"]?.jsonPrimitive?.contentOrNull,
            humanReviewedAt = value["humanReviewedAt"]?.jsonPrimitive?.contentOrNull,
            approvedAt = value["approvedAt"]?.jsonPrimitive?.contentOrNull,
            verdict = verdict,
            visualEvidenceHash = value["visualEvidenceHash"]?.jsonPrimitive?.contentOrNull,
            interactionEvidenceHash = value["interactionEvidenceHash"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun decodeContext(value: JsonObject): ReviewContext = ReviewContext(
        profile = value["profile"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        theme = value["theme"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        state = value["state"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        overlay = value["overlay"]?.jsonPrimitive?.contentOrNull,
        layout = value["layout"]?.jsonPrimitive?.contentOrNull,
        libraryView = value["libraryView"]?.jsonPrimitive?.contentOrNull,
        lastAction = value["lastAction"]?.jsonPrimitive?.contentOrNull,
        recentEvents = value["recentEvents"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
        updatedAt = value["updatedAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )
}
