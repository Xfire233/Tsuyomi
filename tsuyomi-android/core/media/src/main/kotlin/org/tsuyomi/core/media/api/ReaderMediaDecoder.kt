/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.media.api

import org.tsuyomi.core.media.internal.MediaLoadException
import org.tsuyomi.core.media.internal.decodeValidatedBitmap

/** Host-owned inert bytes; no URL, provider URI, platform handle or network capability. */
data class ReaderMediaPayload(val bytes: ByteArray, val mimeType: String)

object ReaderMediaDecoder {
    private val imageTypes = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
    const val MaxEncodedBytes = 8 * 1024 * 1024

    suspend fun decode(
        payload: ReaderMediaPayload,
        targetWidthPx: Int,
        targetHeightPx: Int,
        fallback: FallbackSpec,
    ): CoverUiState {
        require(targetWidthPx in 1..4096 && targetHeightPx in 1..4096)
        if (payload.mimeType !in imageTypes) return CoverUiState.Failed(CoverFailureReason.RESPONSE_REJECTED, fallback)
        return try {
            CoverUiState.Ready(decodeValidatedBitmap(
                payload.bytes, targetWidthPx, targetHeightPx, MaxEncodedBytes, payload.mimeType,
            ))
        } catch (_: MediaLoadException) {
            CoverUiState.Failed(CoverFailureReason.DECODE_FAILED, fallback)
        }
    }
}
