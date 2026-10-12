/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.tsuyomi.core.media.api.CoverFailureReason
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.media.api.ReaderMediaDecoder
import org.tsuyomi.core.media.api.ReaderMediaPayload
import org.tsuyomi.core.publication.ParsedPublication
import org.tsuyomi.core.publication.resources.PublicationResourceFetchResult
import org.tsuyomi.core.publication.resources.PublicationResourceRejection
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.model.BookIdentity

/** UI state belongs to this host owner, never to the renderer or a book's scripts. */
internal class PublicationImages(private val publications: LocalPublicationManager) {
    private val imageStates = ReaderImageStates()
    val states get() = imageStates.values
    private val jobs = mutableMapOf<String, Job>()
    private val owners = mutableMapOf<String, ImageOwner>()
    private val concurrency = Semaphore(2)
    private var epoch = 0L

    fun clear() {
        epoch++
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        owners.clear()
        imageStates.clear()
    }

    fun release(block: ReaderBlock.Image, generation: Long) {
        val owner = owners[block.blockId] ?: return
        if (owner.generation != generation || owner.resource != block.resource) return
        owners.remove(block.blockId)
        jobs.remove(block.blockId)?.cancel()
        imageStates.remove(block.blockId)
    }
    fun load(
        block: ReaderBlock.Image,
        publication: ParsedPublication,
        scope: CoroutineScope,
        width: Int,
        height: Int,
        retry: Boolean = false,
        generation: Long = -1L,
    ) {
        val previousOwner = owners[block.blockId]
        if (retry || previousOwner?.resource?.let { it != block.resource } == true) {
            jobs.remove(block.blockId)?.cancel()
            imageStates.remove(block.blockId)
        }
        owners[block.blockId] = ImageOwner(block.resource, generation)
        if (!retry && imageStates.contains(block.blockId)) return
        val fence = epoch
        val fallback = FallbackSpec(block.altText ?: publication.metadata.title, null)
        imageStates.put(block.blockId, CoverUiState.Loading(fallback))
        val request = scope.launch(start = CoroutineStart.LAZY) {
            val context = currentCoroutineContext()
            try {
                val state = concurrency.withPermit {
                    try {
                        val payload = withContext(Dispatchers.IO) { payload(block.resource, publication) }
                        context.ensureActive()
                        ReaderMediaDecoder.decode(payload, width.coerceIn(1, 2048), height.coerceIn(1, 2048), fallback)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (rejected: ResourceRejected) {
                        CoverUiState.Failed(rejected.reason, fallback)
                    } catch (_: Exception) {
                        CoverUiState.Failed(CoverFailureReason.RESPONSE_REJECTED, fallback)
                    }
                }
                context.ensureActive()
                if (fence == epoch && jobs[block.blockId] === context.job) imageStates.put(block.blockId, state)
            } finally {
                if (jobs[block.blockId] === context.job) jobs.remove(block.blockId)
            }
        }
        jobs[block.blockId] = request
        request.start()
    }

    private suspend fun payload(reference: ReaderResourceRef, publication: ParsedPublication): ReaderMediaPayload = when (reference) {
        is ReaderResourceRef.RemoteHttps -> when (val result = publications.resources.fetch(
            publication.publicationId, publication.originalSha256, reference.url,
        )) {
            is PublicationResourceFetchResult.Success -> ReaderMediaPayload(result.bytes, result.mimeType)
            is PublicationResourceFetchResult.Rejected -> throw ResourceRejected(when (result.status) {
                PublicationResourceRejection.NO_GRANT, PublicationResourceRejection.GRANT_MISMATCH,
                PublicationResourceRejection.ORIGIN_NOT_GRANTED, PublicationResourceRejection.REVOKED -> CoverFailureReason.ORIGIN_NOT_GRANTED
                PublicationResourceRejection.NETWORK_FAILURE, PublicationResourceRejection.TIMEOUT,
                PublicationResourceRejection.DNS_UNRESOLVED -> CoverFailureReason.NETWORK
                else -> CoverFailureReason.RESPONSE_REJECTED
            })
        }
        is ReaderResourceRef.Local -> {
            if (reference.publicationId != publication.publicationId) throw IOException("Wrong publication owner")
            val length = publication.resources.firstOrNull { it.reference == reference }?.byteLength
                ?: throw IOException("Unknown publication resource")
            if (length !in 1..ReaderMediaDecoder.MaxEncodedBytes.toLong()) throw IOException("Image byte limit")
            val context = currentCoroutineContext()
            val bytes = ByteArray(length.toInt())
            val input = publications.originals.openResource(reference)
                ?: publication.openResource(reference, context::ensureActive)
            input.use { stream ->
                var offset = 0
                while (offset < bytes.size) {
                    context.ensureActive()
                    val count = stream.read(bytes, offset, bytes.size - offset)
                    if (count <= 0) throw IOException("Truncated publication image")
                    offset += count
                }
                if (stream.read() != -1) throw IOException("Publication image byte count changed")
            }
            context.ensureActive()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            if (digest != reference.sha256) throw IOException("Publication image digest changed")
            ReaderMediaPayload(bytes, reference.mimeType)
        }
    }

    private data class ImageOwner(val resource: ReaderResourceRef, val generation: Long)

    private class ResourceRejected(val reason: CoverFailureReason) : IOException()
}

/** Decodes an admitted sidecar, lazily repairing only missing EPUB cover metadata through its owner. */
internal suspend fun loadPublicationCover(book: LibraryBook, publications: LocalPublicationManager): CoverUiState {
    val fallback = FallbackSpec(book.title, null)
    val identity = book.identity as BookIdentity.Local
    val metadata = checkNotNull(book.localPublication)
    if (!metadata.originalAvailable) return CoverUiState.Absent(fallback)
    return try {
        val payload = withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            context.ensureActive()
            val resourceId = metadata.coverResourceId ?: publications.recoverCoverResourceId(book) ?: return@withContext null
            val resource = publications.originals.resource(identity.publicationId, resourceId)
                ?: throw IOException("Publication cover unavailable")
            val reference = ReaderResourceRef.Local(identity.publicationId, resourceId, resource.mimeType, resource.sha256)
            if (resource.sizeBytes !in 1..ReaderMediaDecoder.MaxEncodedBytes.toLong()) throw IOException("Cover byte limit")
            val bytes = ByteArray(resource.sizeBytes.toInt())
            publications.originals.openResource(reference)?.use { stream ->
                var offset = 0
                while (offset < bytes.size) {
                    context.ensureActive()
                    val count = stream.read(bytes, offset, bytes.size - offset)
                    if (count <= 0) throw IOException("Truncated publication cover")
                    offset += count
                }
                if (stream.read() != -1) throw IOException("Cover byte count changed")
            } ?: throw IOException("Publication cover unavailable")
            context.ensureActive()
            ReaderMediaPayload(bytes, reference.mimeType)
        }
        if (payload == null) CoverUiState.Absent(fallback) else ReaderMediaDecoder.decode(payload, 384, 576, fallback)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CoverUiState.Failed(CoverFailureReason.RESPONSE_REJECTED, fallback)
    }
}
