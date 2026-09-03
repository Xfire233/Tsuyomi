/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.media.internal

import android.content.Context
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.tsuyomi.core.media.api.CoverFailureReason
import org.tsuyomi.core.media.api.CoverMediaFetcher
import org.tsuyomi.core.media.api.CoverRepository
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.shared.sourcecontract.HttpsOrigin

internal class DefaultCoverRepository(
    context: Context,
    origins: Set<HttpsOrigin>,
    maxResponseBytes: Int,
    private val sourceId: String,
    private val packageRevision: String,
    private val credentialRevision: String,
    private val mediaFetcher: CoverMediaFetcher?,
) : CoverRepository {
    private val loader = HostCoverLoader(
        context = context,
        policy = MediaOriginPolicy(origins),
        cacheNamespace = "cover-${partitionHash(sourceId, packageRevision, credentialRevision)}",
        maxResponseBytes = maxResponseBytes,
        mediaFetcher = mediaFetcher,
    )

    override fun observe(request: CoverRequest): Flow<CoverUiState> = flow {
        if (request.sourceId != sourceId || request.packageRevision != packageRevision ||
            request.credentialRevision != credentialRevision
        ) {
            emit(CoverUiState.Failed(CoverFailureReason.INVALID_REFERENCE, request.fallback))
            return@flow
        }
        emit(CoverUiState.Loading(request.fallback))
        val state = try {
            CoverUiState.Ready(
                loader.load(request.transportUrl, request.referrerUrl, request.targetWidthPx, request.targetHeightPx),
            )
        } catch (error: MediaLoadException) {
            CoverUiState.Failed(error.failure.toPublicReason(), request.fallback)
        } catch (_: Throwable) {
            CoverUiState.Failed(CoverFailureReason.NETWORK, request.fallback)
        }
        emit(state)
    }

    private fun MediaFailure.toPublicReason(): CoverFailureReason = when (this) {
        MediaFailure.INVALID_URL -> CoverFailureReason.INVALID_REFERENCE
        MediaFailure.ORIGIN_NOT_GRANTED -> CoverFailureReason.ORIGIN_NOT_GRANTED
        MediaFailure.HTTP_FAILURE, MediaFailure.REDIRECT_LIMIT -> CoverFailureReason.NETWORK
        MediaFailure.RESPONSE_TOO_LARGE, MediaFailure.UNSUPPORTED_CONTENT -> CoverFailureReason.RESPONSE_REJECTED
        MediaFailure.DECODE_FAILED -> CoverFailureReason.DECODE_FAILED
    }

    private companion object {
        fun partitionHash(sourceId: String, packageRevision: String, credentialRevision: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest("$sourceId\u0000$packageRevision\u0000$credentialRevision".toByteArray(Charsets.UTF_8))
                .take(12)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
