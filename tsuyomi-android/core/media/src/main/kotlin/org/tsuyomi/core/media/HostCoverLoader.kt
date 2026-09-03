/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.media.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.core.media.api.CoverMediaFetcher

private const val DEFAULT_MAX_RESPONSE_BYTES = 8 * 1024 * 1024
private const val MAX_SOURCE_PIXELS = 50_000_000L
private const val MAX_REDIRECTS = 3

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
    private val disk = QuotaFileStore(
        roots = StorageRoots.from(context),
        root = StorageRoot.CACHE,
        namespace = cacheNamespace,
        quota = StorageQuota(maxBytes = 128L * 1024L * 1024L, maxEntries = 4_000),
    )
    private val memory = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val concurrency = Semaphore(2)

    init {
        require(maxResponseBytes in 1..16_777_216) { "Invalid media response limit" }
    }

    suspend fun load(url: String, targetWidthPx: Int, targetHeightPx: Int): Bitmap =
        load(url, referrerUrl = null, targetWidthPx = targetWidthPx, targetHeightPx = targetHeightPx)

    suspend fun load(url: String, referrerUrl: String?, targetWidthPx: Int, targetHeightPx: Int): Bitmap = concurrency.withPermit {
        require(targetWidthPx > 0 && targetHeightPx > 0) { "Target dimensions must be positive" }
        val normalized = policy.requireAllowed(url)
        val memoryKey = "$normalized#$targetWidthPx:$targetHeightPx"
        memory.get(memoryKey)?.let { return@withPermit it }
        val diskPath = "${sha256(normalized)}.image"
        val cached = runCatching { disk.read(diskPath) }.getOrNull()
        if (cached != null) {
            runCatching { decodeValidated(cached, targetWidthPx, targetHeightPx) }.getOrNull()?.let { bitmap ->
                memory.put(memoryKey, bitmap)
                return@withPermit bitmap
            }
            disk.delete(diskPath)
        }
        val response = mediaFetcher?.fetch(normalized, referrerUrl)
            ?.let { EncodedMedia(it.bytes, it.contentType) }
            ?: transport.fetch(normalized, policy, maxResponseBytes)
        if (response.contentType !in setOf("image/jpeg", "image/png")) {
            throw MediaLoadException(MediaFailure.UNSUPPORTED_CONTENT)
        }
        val bitmap = decodeValidated(response.bytes, targetWidthPx, targetHeightPx)
        runCatching { disk.write(diskPath, response.bytes) }
        memory.put(memoryKey, bitmap)
        bitmap
    }

    private suspend fun decodeValidated(bytes: ByteArray, targetWidthPx: Int, targetHeightPx: Int): Bitmap =
        withContext(Dispatchers.Default) {
            if (bytes.isEmpty() || bytes.size > maxResponseBytes) throw MediaLoadException(MediaFailure.RESPONSE_TOO_LARGE)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > MAX_SOURCE_PIXELS) {
                throw MediaLoadException(MediaFailure.DECODE_FAILED)
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(width, height, targetWidthPx, targetHeightPx)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: throw MediaLoadException(MediaFailure.DECODE_FAILED)
        }

    private fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) sample *= 2
        return sample
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
