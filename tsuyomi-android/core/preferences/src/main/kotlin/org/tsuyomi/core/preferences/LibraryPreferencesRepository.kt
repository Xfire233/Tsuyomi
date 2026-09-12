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

data class LibraryRootNodePreference(
    val id: String,
    val bookOffset: Int = 0,
)

data class LibraryTabPresentationPreferences(
    val layout: String = "GRID",
    val sortMode: String = "SMART",
    val sortDescending: Boolean = false,
    val firstVisibleIndex: Int = 0,
    val firstVisibleOffset: Int = 0,
)

data class LibraryPresentationPreferences(
    val rootNodes: List<LibraryRootNodePreference> = emptyList(),
    val tabPresentations: Map<String, LibraryTabPresentationPreferences> = emptyMap(),
    val websiteGroupingBySource: Map<String, Boolean> = emptyMap(),
    val showUpdatesOnly: Boolean = false,
)

class LibraryPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<LibraryPresentationPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { values ->
            LibraryPresentationPreferences(
                rootNodes = decodeRootNodes(values[RootNodes].orEmpty()),
                tabPresentations = decodeTabPresentations(values[TabPresentations].orEmpty()),
                websiteGroupingBySource = decodeWebsiteGrouping(values[WebsiteGrouping].orEmpty()),
                showUpdatesOnly = values[ShowUpdatesOnly] ?: false,
            )
        }

    suspend fun migrateLegacyRootPresentation(
        initialTabPresentations: Map<String, LibraryTabPresentationPreferences>,
    ) {
        dataStore.edit { values ->
            val tabPresentations = decodeTabPresentations(values[TabPresentations].orEmpty()).toMutableMap()
            initialTabPresentations.forEach { (key, presentation) -> tabPresentations.putIfAbsent(key, presentation) }
            require(tabPresentations.size <= MaxTabPresentationCount)
            values[TabPresentations] = encodeTabPresentations(tabPresentations)
            if (values[RootMigrationComplete] == true) return@edit
            val legacyOrder = decodeOrder(values[LegacyShortcutOrder].orEmpty())
            val rootNodes = legacyOrder.mapNotNull { id ->
                id.takeIf { it.startsWith("collection:") || it.startsWith("mirror:") }
            }.distinct().map { id -> LibraryRootNodePreference(id) }
            val grouping = decodeWebsiteGrouping(values[WebsiteGrouping].orEmpty()).toMutableMap()
            legacyOrder.mapNotNull(::legacyMirrorFolderSourceId).forEach { sourceId -> grouping[sourceId] = true }
            values[RootNodes] = encodeRootNodes(rootNodes)
            values[WebsiteGrouping] = encodeWebsiteGrouping(grouping)
            values.remove(LegacyShortcutOrder)
            values.remove(LegacyShortcutLocked)
            values[RootMigrationComplete] = true
        }
    }

    suspend fun updateRootNodes(nodes: List<LibraryRootNodePreference>) {
        require(nodes.size <= MaxStructuralNodeCount && nodes.map { it.id }.distinct().size == nodes.size)
        require(nodes.all { it.id.isNotBlank() && it.id.length <= MaxEncodedItemLength && it.bookOffset >= 0 })
        dataStore.edit { values -> values[RootNodes] = encodeRootNodes(nodes) }
    }

    suspend fun updateTabPresentation(key: String, presentation: LibraryTabPresentationPreferences) {
        require(key.isNotBlank() && key.length <= MaxTabKeyLength)
        require(presentation.firstVisibleIndex >= 0 && presentation.firstVisibleOffset >= 0)
        dataStore.edit { values ->
            val updated = decodeTabPresentations(values[TabPresentations].orEmpty()).toMutableMap()
            updated[key] = presentation
            require(updated.size <= MaxTabPresentationCount)
            values[TabPresentations] = encodeTabPresentations(updated)
        }
    }

    suspend fun updateShowUpdatesOnly(show: Boolean) {
        dataStore.edit { values -> values[ShowUpdatesOnly] = show }
    }

    suspend fun updateWebsiteGrouping(sourceId: String, enabled: Boolean) {
        require(sourceId.isNotEmpty() && sourceId.length <= MaxEncodedItemLength - 1)
        dataStore.edit { values ->
            val updated = decodeWebsiteGrouping(values[WebsiteGrouping].orEmpty()).toMutableMap()
            updated[sourceId] = enabled
            require(updated.size <= MaxStructuralNodeCount)
            values[WebsiteGrouping] = encodeWebsiteGrouping(updated)
        }
    }

    suspend fun clearWebsiteGroupingOverride(sourceId: String) {
        dataStore.edit { values ->
            val updated = decodeWebsiteGrouping(values[WebsiteGrouping].orEmpty()).toMutableMap()
            updated.remove(sourceId)
            values[WebsiteGrouping] = encodeWebsiteGrouping(updated)
        }
    }

    private companion object {
        const val MaxStructuralNodeCount = 256
        const val MaxEncodedItemLength = 2304
        const val MaxTabKeyLength = 96
        const val MaxTabPresentationCount = 12
        val LegacyShortcutOrder = stringPreferencesKey("library_shortcut_order")
        val LegacyShortcutLocked = booleanPreferencesKey("library_shortcut_locked")
        val RootNodes = stringPreferencesKey("library_root_nodes_v1")
        val TabPresentations = stringPreferencesKey("library_tab_presentations_v1")
        val RootMigrationComplete = booleanPreferencesKey("library_root_v1_migrated")
        val WebsiteGrouping = stringPreferencesKey("library_website_grouping")
        val ShowUpdatesOnly = booleanPreferencesKey("library_show_updates_only")
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

private fun encodeRootNodes(nodes: List<LibraryRootNodePreference>): String = encodeOrder(
    nodes.map { node -> "${node.bookOffset}\u0001${node.id}" },
)

private fun decodeRootNodes(encoded: String): List<LibraryRootNodePreference> = decodeOrder(encoded).mapNotNull { item ->
    val separator = item.indexOf('\u0001')
    if (separator <= 0) return@mapNotNull null
    val offset = item.substring(0, separator).toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
    val id = item.substring(separator + 1).takeIf(String::isNotBlank) ?: return@mapNotNull null
    LibraryRootNodePreference(id, offset)
}.distinctBy(LibraryRootNodePreference::id)

private fun encodeTabPresentations(values: Map<String, LibraryTabPresentationPreferences>): String = encodeOrder(
    values.entries.sortedBy { it.key }.map { (key, value) ->
        listOf(
            key,
            value.layout,
            value.sortMode,
            value.sortDescending.toString(),
            value.firstVisibleIndex.toString(),
            value.firstVisibleOffset.toString(),
        ).joinToString("\u0001")
    },
)

private fun decodeTabPresentations(encoded: String): Map<String, LibraryTabPresentationPreferences> = buildMap {
    decodeOrder(encoded).forEach { item ->
        val fields = item.split('\u0001')
        if (fields.size != 6 || fields[0].isBlank()) return@forEach
        val index = fields[4].toIntOrNull()?.takeIf { it >= 0 } ?: return@forEach
        val offset = fields[5].toIntOrNull()?.takeIf { it >= 0 } ?: return@forEach
        put(
            fields[0],
            LibraryTabPresentationPreferences(
                layout = fields[1],
                sortMode = fields[2],
                sortDescending = fields[3].toBooleanStrictOrNull() ?: false,
                firstVisibleIndex = index,
                firstVisibleOffset = offset,
            ),
        )
    }
}

private fun encodeWebsiteGrouping(values: Map<String, Boolean>): String = encodeOrder(
    values.entries.sortedBy { it.key }.map { (sourceId, enabled) ->
        (if (enabled) "1" else "0") + sourceId
    },
)

private fun decodeWebsiteGrouping(encoded: String): Map<String, Boolean> = buildMap {
    decodeOrder(encoded).forEach { item ->
        val enabled = when (item.firstOrNull()) {
            '1' -> true
            '0' -> false
            else -> return@forEach
        }
        val sourceId = item.drop(1)
        if (sourceId.isNotEmpty()) put(sourceId, enabled)
    }
}

private fun legacyMirrorFolderSourceId(id: String): String? {
    val payload = id.removePrefix("mirror-folder:").takeIf { id.startsWith("mirror-folder:") } ?: return null
    val separator = payload.indexOf(':')
    if (separator <= 0) return null
    val sourceLength = payload.substring(0, separator).toIntOrNull() ?: return null
    val sourceStart = separator + 1
    val sourceEnd = sourceStart + sourceLength
    return payload.substring(sourceStart, sourceEnd.coerceAtMost(payload.length)).takeIf { sourceEnd <= payload.length }
}
