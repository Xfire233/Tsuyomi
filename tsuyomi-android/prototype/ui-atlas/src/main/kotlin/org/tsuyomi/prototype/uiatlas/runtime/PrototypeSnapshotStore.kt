/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.runtime

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
import kotlinx.serialization.json.longOrNull

internal class PrototypeSnapshotStore(file: File) {
    private val atomicFile = AtomicFile(file)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(expectedSchemaVersion: Int): PrototypeSnapshot? {
        if (!atomicFile.baseFile.exists()) return null
        return runCatching {
            val root = json.parseToJsonElement(atomicFile.readFully().decodeToString()).jsonObject
            val schemaVersion = root.getValue("schemaVersion").jsonPrimitive.intOrNull ?: return null
            if (schemaVersion != expectedSchemaVersion) return null
            val values = root["values"]?.jsonObject?.mapValues { (_, value) ->
                value.jsonPrimitive.contentOrNull.orEmpty()
            }.orEmpty()
            val events = root["recentEvents"]?.jsonArray?.mapNotNull(::decodeEvent).orEmpty()
            PrototypeSnapshot(
                schemaVersion = schemaVersion,
                revision = root["revision"]?.jsonPrimitive?.longOrNull ?: 0L,
                values = values,
                recentEvents = events.takeLast(PROTOTYPE_EVENT_LIMIT),
            )
        }.getOrNull()
    }

    fun write(snapshot: PrototypeSnapshot) {
        val bytes = encode(snapshot).toString().encodeToByteArray()
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun encode(snapshot: PrototypeSnapshot): JsonObject = buildJsonObject {
        put("schemaVersion", JsonPrimitive(snapshot.schemaVersion))
        put("revision", JsonPrimitive(snapshot.revision))
        put("values", JsonObject(snapshot.values.mapValues { JsonPrimitive(it.value) }))
        put("recentEvents", buildJsonArray {
            snapshot.recentEvents.takeLast(PROTOTYPE_EVENT_LIMIT).forEach { event ->
                add(buildJsonObject {
                    put("sequence", JsonPrimitive(event.sequence))
                    put("action", JsonPrimitive(event.action))
                    put("target", JsonPrimitive(event.target))
                    put("outcome", JsonPrimitive(event.outcome))
                    event.detail?.let { put("detail", JsonPrimitive(it)) }
                })
            }
        })
    }

    private fun decodeEvent(element: kotlinx.serialization.json.JsonElement): PrototypeEvent? {
        val value = element as? JsonObject ?: return null
        return PrototypeEvent(
            sequence = value["sequence"]?.jsonPrimitive?.longOrNull ?: return null,
            action = value["action"]?.jsonPrimitive?.contentOrNull ?: return null,
            target = value["target"]?.jsonPrimitive?.contentOrNull ?: return null,
            outcome = value["outcome"]?.jsonPrimitive?.contentOrNull ?: return null,
            detail = value["detail"]?.jsonPrimitive?.contentOrNull,
        )
    }
}
