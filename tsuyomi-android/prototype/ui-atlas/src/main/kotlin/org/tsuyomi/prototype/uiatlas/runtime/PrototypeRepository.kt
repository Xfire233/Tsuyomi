/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.runtime

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tsuyomi.prototype.uiatlas.BuildConfig

class PrototypeRepository(
    context: Context,
    private val persistent: Boolean,
) {
    private val schemaVersion = BuildConfig.PROTOTYPE_DATA_SCHEMA_VERSION
    private val store = PrototypeSnapshotStore(File(context.noBackupFilesDir, "interactive-prototype-state-v$schemaVersion.json"))
    private val lock = Any()
    private val _snapshot = MutableStateFlow(store.read(schemaVersion) ?: seed())

    val snapshot: StateFlow<PrototypeSnapshot> = _snapshot.asStateFlow()

    fun string(key: String, default: String = ""): String = _snapshot.value.values[key] ?: default

    fun boolean(key: String, default: Boolean = false): Boolean =
        _snapshot.value.values[key]?.toBooleanStrictOrNull() ?: default

    fun int(key: String, default: Int = 0): Int = _snapshot.value.values[key]?.toIntOrNull() ?: default

    fun stringList(key: String, default: List<String> = emptyList()): List<String> =
        _snapshot.value.values[key]?.split(UNIT_SEPARATOR)?.filter(String::isNotEmpty) ?: default

    fun dispatch(action: PrototypeAction) {
        synchronized(lock) {
            val current = _snapshot.value
            val next = reduce(current, action)
            _snapshot.value = next
            if (persistent) store.write(next)
        }
    }

    fun putString(key: String, value: String, eventName: String, target: String = key) =
        dispatch(PrototypeAction.Put(key, value, eventName, target))

    fun putBoolean(key: String, value: Boolean, eventName: String, target: String = key) =
        putString(key, value.toString(), eventName, target)

    fun putInt(key: String, value: Int, eventName: String, target: String = key) =
        putString(key, value.toString(), eventName, target)

    fun putStringList(key: String, values: List<String>, eventName: String, target: String = key) =
        putString(key, values.joinToString(UNIT_SEPARATOR), eventName, target)

    fun record(eventName: String, target: String, outcome: String, detail: String? = null) =
        dispatch(PrototypeAction.Record(eventName, target, outcome, detail))

    fun resetFakeData() = dispatch(PrototypeAction.ResetFakeData)

    private fun reduce(current: PrototypeSnapshot, action: PrototypeAction): PrototypeSnapshot {
        if (action == PrototypeAction.ResetFakeData) {
            val reset = seed(revision = current.revision + 1)
            return reset.copy(
                recentEvents = appendEvent(
                    current.recentEvents,
                    PrototypeEvent(reset.revision, action.eventName, action.target, "success"),
                ),
            )
        }

        val values = current.values.toMutableMap()
        val outcome: String
        val detail: String?
        when (action) {
            is PrototypeAction.Put -> {
                values[action.key] = action.value
                outcome = "success"
                detail = action.value.take(160)
            }
            is PrototypeAction.Remove -> {
                values.remove(action.key)
                outcome = "success"
                detail = null
            }
            is PrototypeAction.Toggle -> {
                values[action.key] = (!(values[action.key]?.toBooleanStrictOrNull() ?: action.default)).toString()
                outcome = "success"
                detail = values[action.key]
            }
            is PrototypeAction.Increment -> {
                values[action.key] = ((values[action.key]?.toIntOrNull() ?: action.default) + action.amount).toString()
                outcome = "success"
                detail = values[action.key]
            }
            is PrototypeAction.Record -> {
                outcome = action.outcome
                detail = action.detail
            }
            PrototypeAction.ResetFakeData -> error("handled above")
        }
        val revision = current.revision + 1
        return current.copy(
            revision = revision,
            values = values.toMap(),
            recentEvents = appendEvent(
                current.recentEvents,
                PrototypeEvent(revision, action.eventName, action.target, outcome, detail),
            ),
        )
    }

    private fun seed(revision: Long = 0): PrototypeSnapshot = PrototypeSnapshot(
        schemaVersion = schemaVersion,
        revision = revision,
        values = mapOf(
            "library.layout" to "GRID",
            "library.view" to "ALL",
            "detail.rating" to "4",
            "detail.readLater" to "false",
            "reader.page" to "4",
            "reader.immersive" to "false",
            "display.profile" to "STANDARD",
            "display.theme" to "LIGHT",
            "display.dynamic" to "false",
            "display.einkRedraw" to "true",
            "reader.fontSize" to "18",
            "reader.lineSpacing" to "1.6",
            "reader.pageAnimation" to "true",
            "reader.volumePaging" to "false",
            "reader.volumeMedia" to "true",
            "reader.progressVisible" to "true",
        ),
        recentEvents = emptyList(),
    )

    private fun appendEvent(events: List<PrototypeEvent>, event: PrototypeEvent): List<PrototypeEvent> =
        (events + event).takeLast(PROTOTYPE_EVENT_LIMIT)

    private companion object {
        const val UNIT_SEPARATOR = "\u001f"
    }
}
