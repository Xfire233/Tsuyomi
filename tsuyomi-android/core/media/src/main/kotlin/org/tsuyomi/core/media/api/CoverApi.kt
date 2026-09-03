/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.media.api

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import org.tsuyomi.core.media.internal.DefaultCoverRepository
import org.tsuyomi.shared.sourcecontract.HttpsOrigin

data class CoverMediaPayload(val bytes: ByteArray, val contentType: String)

fun interface CoverMediaFetcher {
    suspend fun fetch(url: String, referrerUrl: String?): CoverMediaPayload
}

data class CoverRequest(
    val sourceId: String,
    val packageRevision: String,
    val credentialRevision: String,
    val transportUrl: String,
    val referrerUrl: String? = null,
    val targetWidthPx: Int,
    val targetHeightPx: Int,
    val fallback: FallbackSpec,
) {
    init {
        require(sourceId.isNotBlank())
        require(packageRevision.isNotBlank())
        require(credentialRevision.isNotBlank())
        require(targetWidthPx > 0 && targetHeightPx > 0)
    }
}

data class FallbackSpec(val title: String, val sourceLabel: String?)

enum class CoverFailureReason {
    INVALID_REFERENCE,
    ORIGIN_NOT_GRANTED,
    NETWORK,
    RESPONSE_REJECTED,
    DECODE_FAILED,
}

sealed interface CoverUiState {
    data class Absent(val fallback: FallbackSpec) : CoverUiState
    data class Loading(val fallback: FallbackSpec) : CoverUiState
    data class Ready(val bitmap: Bitmap) : CoverUiState
    data class StaleReady(val bitmap: Bitmap, val provenance: String) : CoverUiState
    data class Failed(val reason: CoverFailureReason, val fallback: FallbackSpec) : CoverUiState
    data class Fallback(val spec: FallbackSpec) : CoverUiState
}

interface CoverRepository {
    fun observe(request: CoverRequest): Flow<CoverUiState>
}

object CoverRepositoryFactory {
    fun create(
        context: Context,
        origins: Set<HttpsOrigin>,
        maxResponseBytes: Int,
        sourceId: String,
        packageRevision: String,
        credentialRevision: String,
        mediaFetcher: CoverMediaFetcher? = null,
    ): CoverRepository = DefaultCoverRepository(
        context = context.applicationContext,
        origins = origins,
        maxResponseBytes = maxResponseBytes,
        sourceId = sourceId,
        packageRevision = packageRevision,
        credentialRevision = credentialRevision,
        mediaFetcher = mediaFetcher,
    )
}
