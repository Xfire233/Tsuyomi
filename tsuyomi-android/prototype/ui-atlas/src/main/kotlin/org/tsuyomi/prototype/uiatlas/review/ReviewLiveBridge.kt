/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.review

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.compose.runtime.Immutable
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.tsuyomi.prototype.uiatlas.BuildConfig

enum class ReviewSubmissionKind(val wireName: String) {
    NODE("node"),
    BATCH_READY("batch_ready"),
}

@Immutable
data class ReviewLiveSubmission(
    val sessionId: String,
    val revision: Long,
    val kind: ReviewSubmissionKind,
    val buildId: String,
    val nodeId: String,
    val route: String,
    val profile: String,
    val reviewSha256: String,
    val submittedAt: String,
)

/**
 * Debug-only, ADB-readable handoff for live human review.
 *
 * The exported review stays in app-private no-backup storage. Logcat carries only an opaque wake-up
 * marker; it never contains review text. The host reads the files with `adb exec-out run-as`.
 */
class ReviewLiveBridge(
    context: Context,
    private val persistent: Boolean,
) {
    private val appContext = context.applicationContext
    private val payloadFile = AtomicFile(appContext.noBackupFilesDir.resolve(PAYLOAD_FILE_NAME))
    private val signalFile = AtomicFile(appContext.noBackupFilesDir.resolve(SIGNAL_FILE_NAME))
    private val sessionFile = AtomicFile(appContext.noBackupFilesDir.resolve(SESSION_FILE_NAME))
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val _lastSubmission = MutableStateFlow(if (persistent) readSignal() else null)

    val lastSubmission: StateFlow<ReviewLiveSubmission?> = _lastSubmission.asStateFlow()
    val enabled: Boolean get() = persistent && BuildConfig.DEBUG

    fun submit(
        snapshot: ReviewSnapshot,
        nodeId: String,
        route: String,
        profile: String,
        kind: ReviewSubmissionKind,
    ): ReviewLiveSubmission {
        check(enabled) { "Live review submission is available only in persistent debug runtime" }
        return synchronized(lock) {
            val reviewBytes = ReviewJsonExporter.export(
                context = appContext,
                snapshot = snapshot,
                includeStaleBuilds = false,
            ).encodeToByteArray()
            val reviewSha256 = sha256(reviewBytes)
            val sessionId = readOrCreateSessionId()
            val previous = readSignal()
            val revision = if (previous?.sessionId == sessionId) previous.revision + 1L else 1L
            val submission = ReviewLiveSubmission(
                sessionId = sessionId,
                revision = revision,
                kind = kind,
                buildId = BuildConfig.PROTOTYPE_BUILD_ID,
                nodeId = nodeId,
                route = route,
                profile = profile,
                reviewSha256 = reviewSha256,
                submittedAt = Instant.now().toString(),
            )

            writeAtomic(payloadFile, reviewBytes)
            writeAtomic(signalFile, encodeSignal(submission).toString().encodeToByteArray())
            _lastSubmission.value = submission
            Log.i(
                LOG_TAG,
                "REVIEW_READY revision=${submission.revision} " +
                    "session=${submission.sessionId} " +
                    "build=${submission.buildId} " +
                    "node=${submission.nodeId} " +
                    "sha256=${submission.reviewSha256}",
            )
            submission
        }
    }

    private fun readOrCreateSessionId(): String {
        if (sessionFile.baseFile.exists()) {
            runCatching {
                val root = json.parseToJsonElement(sessionFile.readFully().decodeToString()).jsonObject
                root["sessionId"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.takeIf(String::isNotBlank)?.let { return it }
        }
        val sessionId = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("schema", JsonPrimitive(SESSION_SCHEMA))
            put("sessionId", JsonPrimitive(sessionId))
            put("createdAt", JsonPrimitive(Instant.now().toString()))
        }
        writeAtomic(sessionFile, payload.toString().encodeToByteArray())
        return sessionId
    }

    private fun readSignal(): ReviewLiveSubmission? {
        if (!signalFile.baseFile.exists()) return null
        return runCatching {
            val root = json.parseToJsonElement(signalFile.readFully().decodeToString()).jsonObject
            if (root.string("schema") != SIGNAL_SCHEMA) return null
            val kind = ReviewSubmissionKind.entries.firstOrNull { it.wireName == root.string("kind") }
                ?: return null
            ReviewLiveSubmission(
                sessionId = root.string("sessionId") ?: return null,
                revision = root["revision"]?.jsonPrimitive?.longOrNull ?: return null,
                kind = kind,
                buildId = root.string("buildId") ?: return null,
                nodeId = root.string("nodeId") ?: return null,
                route = root.string("route") ?: return null,
                profile = root.string("profile") ?: return null,
                reviewSha256 = root.string("reviewSha256") ?: return null,
                submittedAt = root.string("submittedAt") ?: return null,
            )
        }.getOrNull()
    }

    private fun encodeSignal(submission: ReviewLiveSubmission): JsonObject = buildJsonObject {
        put("schema", JsonPrimitive(SIGNAL_SCHEMA))
        put("sessionId", JsonPrimitive(submission.sessionId))
        put("revision", JsonPrimitive(submission.revision))
        put("kind", JsonPrimitive(submission.kind.wireName))
        put("applicationId", JsonPrimitive(BuildConfig.APPLICATION_ID))
        put("buildId", JsonPrimitive(submission.buildId))
        put("nodeId", JsonPrimitive(submission.nodeId))
        put("route", JsonPrimitive(submission.route))
        put("profile", JsonPrimitive(submission.profile))
        put("reviewSha256", JsonPrimitive(submission.reviewSha256))
        put("submittedAt", JsonPrimitive(submission.submittedAt))
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        const val LOG_TAG = "TsuyomiReviewBridge"
        const val PAYLOAD_FILE_NAME = "interactive-review-live-v1.json"
        const val SIGNAL_FILE_NAME = "interactive-review-signal-v1.json"
        private const val SESSION_FILE_NAME = "interactive-review-session-v1.json"
        private const val SIGNAL_SCHEMA = "tsuyomi-live-review-signal-v1"
        private const val SESSION_SCHEMA = "tsuyomi-live-review-session-v1"
    }
}
