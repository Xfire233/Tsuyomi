/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class LibraryPresentationPreferences(
    val shortcutOrder: List<String> = emptyList(),
    val shortcutLocked: Boolean = false,
)

class LibraryPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<LibraryPresentationPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { values ->
            LibraryPresentationPreferences(
                shortcutOrder = decodeOrder(values[ShortcutOrder].orEmpty()),
                shortcutLocked = values[ShortcutLocked] ?: false,
            )
        }

    suspend fun updateShortcutOrder(order: List<String>) {
        require(order.size <= MaxShortcutCount && order.distinct().size == order.size)
        require(order.all { it.length <= MaxShortcutIdLength })
        dataStore.edit { values -> values[ShortcutOrder] = encodeOrder(order) }
    }

    suspend fun updateShortcutLocked(locked: Boolean) {
        dataStore.edit { values -> values[ShortcutLocked] = locked }
    }

    private companion object {
        const val MaxShortcutCount = 256
        const val MaxShortcutIdLength = 2304
        val ShortcutOrder = stringPreferencesKey("library_shortcut_order")
        val ShortcutLocked = booleanPreferencesKey("library_shortcut_locked")
    }
}

private fun encodeOrder(order: List<String>): String = buildString {
    order.forEach { id ->
        append(id.length)
        append(':')
        append(id)
    }
}

private fun decodeOrder(encoded: String): List<String> {
    if (encoded.isEmpty()) return emptyList()
    val result = ArrayList<String>()
    var cursor = 0
    while (cursor < encoded.length && result.size < 256) {
        val separator = encoded.indexOf(':', cursor)
        if (separator <= cursor) return emptyList()
        val length = encoded.substring(cursor, separator).toIntOrNull() ?: return emptyList()
        if (length !in 0..2304) return emptyList()
        val start = separator + 1
        val end = start + length
        if (end > encoded.length) return emptyList()
        result += encoded.substring(start, end)
        cursor = end
    }
    return result.takeIf { cursor == encoded.length && result.distinct().size == result.size }.orEmpty()
}
