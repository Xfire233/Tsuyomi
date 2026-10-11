/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.tsuyomi.core.media.api.CoverUiState

/** Repositories' LRU limits alone cannot bound bitmap references retained by route UI state. */
internal class ReaderImageStates {
    var values by mutableStateOf<Map<String, CoverUiState>>(emptyMap())
        private set
    private val sizes = LinkedHashMap<String, Int>(16, 0.75f, true)
    private var bytes = 0L

    fun contains(key: String): Boolean {
        sizes[key] // Touch successfully displayed media, not just the transport cache.
        return key in values
    }

    fun put(key: String, state: CoverUiState) {
        sizes.remove(key)?.let { bytes -= it }
        val bitmap = when (state) {
            is CoverUiState.Ready -> state.bitmap
            is CoverUiState.StaleReady -> state.bitmap
            else -> null
        }
        var next = values + (key to state)
        if (bitmap != null) {
            val size = bitmap.allocationByteCount
            sizes[key] = size
            bytes += size
            while (bytes > MaxBytes) {
                val iterator = sizes.entries.iterator()
                val oldest = iterator.next()
                bytes -= oldest.value
                next = next - oldest.key
                iterator.remove()
            }
        }
        values = next
    }

    fun remove(key: String) {
        sizes.remove(key)?.let { bytes -= it }
        values = values - key
    }

    fun clear() {
        sizes.clear()
        bytes = 0L
        values = emptyMap()
    }

    private companion object { const val MaxBytes = 24L * 1024 * 1024 }
}
