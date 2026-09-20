/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.media.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.core.media.api.CoverMediaFetcher
import org.tsuyomi.core.media.api.MediaKind
import org.tsuyomi.shared.sourcecontract.HttpsOrigin

private const val DEFAULT_MAX_RESPONSE_BYTES = 8 * 1024 * 1024
private const val MAX_SOURCE_PIXELS = 50_000_000L
private const val MAX_REDIRECTS = 3
private const val COVER_MEMORY_BYTES = 32 * 1024 * 1024
private const val READER_MEMORY_BYTES = 24 * 1024 * 1024

/** Exact HTTPS-origin grant derived from a verified source manifest. */
internal class MediaOriginPolicy(origins: Set<HttpsOrigin>) {
    private val allowed = origins.mapTo(linkedSetOf()) { it.canonical }

    init {
        require(allowed.isNotEmpty()) { "Media policy requires at least one origin" }
    }

    fun requireAllowed(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: throw MediaLoadException(MediaFailure.INVALID_URL)
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) {
            throw MediaLoadException(MediaFailure.INVALID_URL)
        }
        val origin = HttpsOrigin(
            if (uri.port == -1 || uri.port == 443) "https://${uri.host}" else "https://${uri.host}:${uri.port}",
        ).canonical
        if (origin !in allowed) throw MediaLoadException(MediaFailure.ORIGIN_NOT_GRANTED)
        return uri.toASCIIString()
    }
}

internal enum class MediaFailure {
    INVALID_URL,
    ORIGIN_NOT_GRANTED,
    HTTP_FAILURE,
    REDIRECT_LIMIT,
    RESPONSE_TOO_LARGE,
    UNSUPPORTED_CONTENT,
    DECODE_FAILED,
}
internal class MediaLoadException(val failure: MediaFailure, cause: Throwable? = null) : Exception(failure.name, cause)


internal data class EncodedMedia(val bytes: ByteArray, val contentType: String)

internal interface MediaTransport {
    suspend fun fetch(url: String, policy: MediaOriginPolicy, maxBytes: Int): EncodedMedia
}

internal class UrlConnectionMediaTransport : MediaTransport {
    override suspend fun fetch(url: String, policy: MediaOriginPolicy, maxBytes: Int): EncodedMedia = withContext(Dispatchers.IO) {
        var current = policy.requireAllowed(url)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "image/jpeg,image/png;q=0.9")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    if (redirectCount == MAX_REDIRECTS) throw MediaLoadException(MediaFailure.REDIRECT_LIMIT)
                    val location = connection.getHeaderField("Location")
                        ?: throw MediaLoadException(MediaFailure.HTTP_FAILURE)
                    current = policy.requireAllowed(URL(URL(current), location).toString())
                    return@repeat
                }
                if (status !in 200..299) throw MediaLoadException(MediaFailure.HTTP_FAILURE)
                val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                if (!contentType.startsWith("image/")) throw MediaLoadException(MediaFailure.UNSUPPORTED_CONTENT)
                val declaredLength = connection.contentLengthLong
                if (declaredLength > maxBytes) throw MediaLoadException(MediaFailure.RESPONSE_TOO_LARGE)
                return@withContext EncodedMedia(readBounded(connection.inputStream, maxBytes), contentType)
            } finally {
                connection.disconnect()
            }
        }
        throw MediaLoadException(MediaFailure.REDIRECT_LIMIT)
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray = input.use { stream ->
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw MediaLoadException(MediaFailure.RESPONSE_TOO_LARGE)
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}

internal class HostCoverLoader(
    context: Context,
    private val policy: MediaOriginPolicy,
    cacheNamespace: String,
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
    private val mediaFetcher: CoverMediaFetcher? = null,
    private val transport: MediaTransport = UrlConnectionMediaTransport(),
) {
    private data class LoadKey(
        val normalizedUrl: String,
        val normalizedReferrerUrl: String?,
        val targetWidthPx: Int,
        val targetHeightPx: Int,
        val mediaKind: MediaKind,
    )

    private class InFlightLoad(
        val deferred: Deferred<Bitmap>,
        var observers: Int,
    )

    private val disk = QuotaFileStore(
        roots = StorageRoots.from(context),
        root = StorageRoot.CACHE,
        namespace = cacheNamespace,
        quota = StorageQuota(maxBytes = 128L * 1024L * 1024L, maxEntries = 4_000),
    )
    private val coverMemory = bitmapCache(COVER_MEMORY_BYTES)
    private val readerMemory = bitmapCache(READER_MEMORY_BYTES)
    private val closed = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<LoadKey, InFlightLoad>()
    /**
     * Bounds concurrent exchanges with the source. It deliberately covers the network request only:
     * memory hits, decoding and disk writes run outside it, so a slow decode or a cache lookup can
     * never hold a network slot while a visible cover waits for one.
     *
     * The bound is paced against the reference reader's image fetcher, which runs ten concurrent
     * cover downloads and releases its slot after the disk write. Two slots made a visible grid
     * arrive pair by pair; on a high-latency link the last of twelve covers waited six rounds.
     */
    private val networkConcurrency = Semaphore(8)

    init {
        require(maxResponseBytes in 1..16_777_216) { "Invalid media response limit" }
    }

    fun cached(
        url: String,
        targetWidthPx: Int,
        targetHeightPx: Int,
        mediaKind: MediaKind = MediaKind.COVER,
    ): Bitmap? {
        require(targetWidthPx > 0 && targetHeightPx > 0)
        if (closed.get()) return null
        val normalized = policy.requireAllowed(url)
        return memoryFor(mediaKind).get(memoryKey(normalized, targetWidthPx, targetHeightPx))
    }

    suspend fun load(url: String, targetWidthPx: Int, targetHeightPx: Int): Bitmap =
        load(
            url = url,
            referrerUrl = null,
            targetWidthPx = targetWidthPx,
            targetHeightPx = targetHeightPx,
            mediaKind = MediaKind.COVER,
        )

    suspend fun load(
        url: String,
        referrerUrl: String?,
        targetWidthPx: Int,
        targetHeightPx: Int,
        mediaKind: MediaKind = MediaKind.COVER,
    ): Bitmap {
        require(targetWidthPx > 0 && targetHeightPx > 0)
        if (closed.get()) throw CancellationException("media-loader-closed")
        val normalized = policy.requireAllowed(url)
        val normalizedReferrer = referrerUrl?.let(policy::requireAllowed)
        val memoryKey = memoryKey(normalized, targetWidthPx, targetHeightPx)
        memoryFor(mediaKind).get(memoryKey)?.let { return it }
        val key = LoadKey(normalized, normalizedReferrer, targetWidthPx, targetHeightPx, mediaKind)
        return coalescedLoad(key)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel(CancellationException("media-loader-closed"))
        coverMemory.evictAll()
        readerMemory.evictAll()
    }

    private suspend fun coalescedLoad(key: LoadKey): Bitmap {
        val entry = inFlightMutex.withLock {
            if (closed.get()) throw CancellationException("media-loader-closed")
            inFlight[key]?.also { it.observers += 1 } ?: InFlightLoad(
                deferred = scope.async(start = CoroutineStart.LAZY) { loadUnshared(key) },
                observers = 1,
            ).also { inFlight[key] = it }
        }
        entry.deferred.start()
        try {
            return entry.deferred.await()
        } finally {
            val cancelUnused = withContext(NonCancellable) {
                inFlightMutex.withLock {
                    entry.observers -= 1
                    if (entry.observers == 0) {
                        if (inFlight[key] === entry) inFlight.remove(key)
                        entry.deferred.isActive
                    } else {
                        false
                    }
                }
            }
            if (cancelUnused) entry.deferred.cancel(CancellationException("media-load-unobserved"))
        }
    }

    private suspend fun loadUnshared(key: LoadKey): Bitmap {
        val memory = memoryFor(key.mediaKind)
        val memoryKey = memoryKey(key.normalizedUrl, key.targetWidthPx, key.targetHeightPx)
        memory.get(memoryKey)?.let { return it }
        val diskPath = "${sha256(key.normalizedUrl)}.image"
        val cached = readDisk(diskPath)
        if (cached != null) {
            try {
                return decodeValidated(cached, key.targetWidthPx, key.targetHeightPx).also {
                    memory.put(memoryKey, it)
                }
            } catch (_: MediaLoadException) {
                deleteDisk(diskPath)
            }
        }
        val response = fetchBoundedToSource(key.normalizedUrl, key.normalizedReferrerUrl)
        if (response.contentType !in setOf("image/jpeg", "image/png")) {
            throw MediaLoadException(MediaFailure.UNSUPPORTED_CONTENT)
        }
        val bitmap = decodeValidated(response.bytes, key.targetWidthPx, key.targetHeightPx)
        writeDisk(diskPath, response.bytes)
        memory.put(memoryKey, bitmap)
        return bitmap
    }

    private suspend fun fetchBoundedToSource(normalized: String, referrerUrl: String?): EncodedMedia =
        networkConcurrency.withPermit {
            mediaFetcher?.fetch(normalized, referrerUrl)
                ?.let { EncodedMedia(it.bytes, it.contentType) }
                ?: transport.fetch(normalized, policy, maxResponseBytes)
        }

    private suspend fun decodeValidated(bytes: ByteArray, targetWidthPx: Int, targetHeightPx: Int): Bitmap =
        withContext(Dispatchers.Default) {
            if (bytes.isEmpty() || bytes.size > maxResponseBytes) throw MediaLoadException(MediaFailure.RESPONSE_TOO_LARGE)
            try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    val width = info.size.width
                    val height = info.size.height
                    if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > MAX_SOURCE_PIXELS) {
                        throw MediaLoadException(MediaFailure.DECODE_FAILED)
                    }
                    val scale = minOf(
                        targetWidthPx.toDouble() / width.toDouble(),
                        targetHeightPx.toDouble() / height.toDouble(),
                        1.0,
                    )
                    val decodedWidth = floor(width * scale).toInt().coerceIn(1, targetWidthPx)
                    val decodedHeight = floor(height * scale).toInt().coerceIn(1, targetHeightPx)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSize(decodedWidth, decodedHeight)
                }
            } catch (error: MediaLoadException) {
                throw error
            } catch (error: java.io.IOException) {
                throw MediaLoadException(MediaFailure.DECODE_FAILED, error)
            } catch (error: IllegalArgumentException) {
                throw MediaLoadException(MediaFailure.DECODE_FAILED, error)
            } catch (error: IllegalStateException) {
                throw MediaLoadException(MediaFailure.DECODE_FAILED, error)
            }
        }

    private suspend fun readDisk(path: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            disk.read(path)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (security: SecurityException) {
            throw security
        } catch (error: Error) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun writeDisk(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            disk.write(path, bytes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (security: SecurityException) {
            throw security
        } catch (error: Error) {
            throw error
        } catch (_: Exception) {
            Unit
        }
    }

    private suspend fun deleteDisk(path: String) = withContext(Dispatchers.IO) {
        try {
            disk.delete(path)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (security: SecurityException) {
            throw security
        } catch (error: Error) {
            throw error
        } catch (_: Exception) {
            Unit
        }
    }

    private fun memoryFor(mediaKind: MediaKind): LruCache<String, Bitmap> = when (mediaKind) {
        MediaKind.COVER -> coverMemory
        MediaKind.READER_ILLUSTRATION -> readerMemory
    }

    private fun bitmapCache(maxBytes: Int) = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    private fun memoryKey(normalizedUrl: String, targetWidthPx: Int, targetHeightPx: Int): String =
        "$normalizedUrl#$targetWidthPx:$targetHeightPx"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
