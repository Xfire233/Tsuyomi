/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.media.internal

import android.graphics.Bitmap
import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.media.api.CoverMediaFetcher
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.core.media.api.MediaKind
import org.tsuyomi.shared.sourcecontract.HttpsOrigin

@RunWith(AndroidJUnit4::class)
class HostCoverLoaderInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("cover-media-test") }
            ?.forEach(java.io.File::deleteRecursively)
    }

    @Test
    fun validates_decodes_and_reuses_admitted_cover_bytes() = runBlocking {
        val encoded = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888).run {
                eraseColor(android.graphics.Color.rgb(20, 40, 60))
                assertTrue(compress(Bitmap.CompressFormat.PNG, 100, output))
                recycle()
            }
            output.toByteArray()
        }
        var requests = 0
        val transport = object : MediaTransport {
            override suspend fun fetch(url: String, policy: MediaOriginPolicy, maxBytes: Int): EncodedMedia {
                requests++
                policy.requireAllowed(url)
                return EncodedMedia(encoded, "image/png")
            }
        }
        val loader = HostCoverLoader(
            context = context,
            policy = MediaOriginPolicy(setOf(HttpsOrigin("https://pic.wenku8.com"))),
            cacheNamespace = "cover-media-test",
            transport = transport,
        )

        val first = loader.load("https://pic.wenku8.com/fixture.png", 4, 6)
        val second = loader.load("https://pic.wenku8.com/fixture.png", 4, 6)

        assertEquals(4, first.width)
        assertEquals(6, first.height)
        assertEquals(first, second)
        assertEquals("The second request must use the validated memory/disk admission", 1, requests)
        assertEquals(first, loader.cached("https://pic.wenku8.com/fixture.png", 4, 6))
        assertEquals(null, loader.cached("https://pic.wenku8.com/fixture.png", 8, 12))
        loader.close()
    }

    @Test
    fun repository_propagates_fetch_cancellation_without_emitting_a_failure() = runBlocking {
        val fallback = FallbackSpec("Cancelled cover", "Fixture source")
        val repository = DefaultCoverRepository(
            context = context,
            origins = setOf(HttpsOrigin("https://pic.wenku8.com")),
            maxResponseBytes = 1_024,
            sourceId = "org.tsuyomi.fixture",
            packageRevision = "fixture-package",
            credentialRevision = "fixture-credentials",
            mediaFetcher = CoverMediaFetcher { _, _ -> throw CancellationException("test cancellation") },
        )
        val states = mutableListOf<CoverUiState>()

        val cancellation = runCatching {
            repository.observe(
                CoverRequest(
                    sourceId = "org.tsuyomi.fixture",
                    packageRevision = "fixture-package",
                    credentialRevision = "fixture-credentials",
                    transportUrl = "https://pic.wenku8.com/cancelled.png",
                    targetWidthPx = 4,
                    targetHeightPx = 6,
                    fallback = fallback,
                ),
            ).collect { state -> states += state }
        }.exceptionOrNull()

        assertTrue(cancellation is CancellationException)
        assertEquals(listOf(CoverUiState.Loading(fallback)), states)
        repository.close()
    }

    @Test
    fun ready_cover_reentry_never_emits_loading_or_crosses_credentials() = runBlocking {
        val encoded = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
        val repository = DefaultCoverRepository(
            context, setOf(HttpsOrigin("https://pic.wenku8.com")), 1_024,
            "fixture", "package", "credentials",
            CoverMediaFetcher { _, _ -> org.tsuyomi.core.media.api.CoverMediaPayload(encoded, "image/png") },
        )
        val request = CoverRequest(
            "fixture", "package", "credentials", "https://pic.wenku8.com/reentry.png",
            targetWidthPx = 4, targetHeightPx = 6, fallback = FallbackSpec("Cover", null),
        )
        repository.observe(request).collect {}
        assertTrue(repository.cached(request) is CoverUiState.Ready)
        val reentry = mutableListOf<CoverUiState>()
        repository.observe(request).collect { reentry += it }
        assertEquals(1, reentry.size)
        assertTrue(reentry.single() is CoverUiState.Ready)
        assertEquals(null, repository.cached(request.copy(credentialRevision = "other-session")))
        repository.close()
    }

    @Test
    fun cache_file_work_stays_off_main_and_warm_disk_reuses_target_bound_decode() = runBlocking {
        val encoded = encodedBitmap(2_000, 3_000, Bitmap.CompressFormat.JPEG)
        val requests = AtomicInteger(0)
        val transport = object : MediaTransport {
            override suspend fun fetch(url: String, policy: MediaOriginPolicy, maxBytes: Int): EncodedMedia {
                requests.incrementAndGet()
                policy.requireAllowed(url)
                return EncodedMedia(encoded, "image/jpeg")
            }
        }
        val namespace = "cover-media-test-off-main-${System.nanoTime()}"
        val policy = MediaOriginPolicy(setOf(HttpsOrigin("https://pic.wenku8.com")))
        val violations = CopyOnWriteArrayList<Throwable>()
        val listenerExecutor = Executors.newSingleThreadExecutor()

        suspend fun loadOnMain(loader: HostCoverLoader): Bitmap = withContext(Dispatchers.Main) {
            val previous = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder(previous)
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyListener(listenerExecutor) { violation -> violations += violation }
                    .build(),
            )
            try {
                loader.load("https://pic.wenku8.com/large.jpg", 1_080, 2_400)
            } finally {
                StrictMode.setThreadPolicy(previous)
            }
        }

        val coldLoader = HostCoverLoader(context, policy, namespace, transport = transport)
        val cold = loadOnMain(coldLoader)
        coldLoader.close()
        val warmLoader = HostCoverLoader(context, policy, namespace, transport = transport)
        val warm = loadOnMain(warmLoader)
        warmLoader.close()
        listenerExecutor.shutdown()
        assertTrue(listenerExecutor.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(1_080, cold.width)
        assertEquals(1_620, cold.height)
        assertEquals(1_080, warm.width)
        assertEquals(1_620, warm.height)
        assertEquals("Warm disk reuse must not fetch again", 1, requests.get())
        assertTrue(
            "QuotaFileStore work must not violate the main-thread disk policy: $violations",
            violations.none { violation ->
                violation.stackTrace.any { frame -> frame.className.startsWith("org.tsuyomi.core.files.QuotaFileStore") }
            },
        )
    }

    @Test
    fun reader_memory_pressure_cannot_evict_cover_working_set() = runBlocking {
        val coverBytes = encodedBitmap(240, 360, Bitmap.CompressFormat.PNG)
        val readerBytes = encodedBitmap(2_000, 3_000, Bitmap.CompressFormat.JPEG)
        val loader = HostCoverLoader(
            context = context,
            policy = MediaOriginPolicy(setOf(HttpsOrigin("https://pic.wenku8.com"))),
            cacheNamespace = "cover-media-test-isolation-${System.nanoTime()}",
            transport = object : MediaTransport {
                override suspend fun fetch(url: String, policy: MediaOriginPolicy, maxBytes: Int): EncodedMedia {
                    policy.requireAllowed(url)
                    return if ("/reader/" in url) {
                        EncodedMedia(readerBytes, "image/jpeg")
                    } else {
                        EncodedMedia(coverBytes, "image/png")
                    }
                }
            },
        )

        repeat(12) { index ->
            loader.load("https://pic.wenku8.com/cover/$index.png", null, 240, 360, MediaKind.COVER)
        }
        val readers = (0 until 4).map { index ->
            loader.load(
                "https://pic.wenku8.com/reader/$index.jpg",
                null,
                1_080,
                2_400,
                MediaKind.READER_ILLUSTRATION,
            )
        }

        assertTrue(readers.all { it.width == 1_080 && it.height == 1_620 })
        assertTrue(loader.cached("https://pic.wenku8.com/cover/0.png", 240, 360, MediaKind.COVER) != null)
        assertNull(
            "The bounded Reader pool should evict its oldest large image",
            loader.cached(
                "https://pic.wenku8.com/reader/0.jpg",
                1_080,
                2_400,
                MediaKind.READER_ILLUSTRATION,
            ),
        )
        assertTrue(
            loader.cached(
                "https://pic.wenku8.com/reader/3.jpg",
                1_080,
                2_400,
                MediaKind.READER_ILLUSTRATION,
            ) != null,
        )
        loader.close()
    }

    @Test
    fun identical_in_flight_loads_share_fetch_without_coupling_observer_cancellation() = runBlocking {
        val encoded = encodedBitmap(240, 360, Bitmap.CompressFormat.PNG)
        val requests = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val loader = HostCoverLoader(
            context = context,
            policy = MediaOriginPolicy(setOf(HttpsOrigin("https://pic.wenku8.com"))),
            cacheNamespace = "cover-media-test-coalescing-${System.nanoTime()}",
            transport = object : MediaTransport {
                override suspend fun fetch(url: String, policy: MediaOriginPolicy, maxBytes: Int): EncodedMedia {
                    requests.incrementAndGet()
                    policy.requireAllowed(url)
                    started.complete(Unit)
                    release.await()
                    return EncodedMedia(encoded, "image/png")
                }
            },
        )

        val first = async { loader.load("https://pic.wenku8.com/shared.png", 240, 360) }
        started.await()
        val second = async { loader.load("https://pic.wenku8.com/shared.png", 240, 360) }
        delay(100)
        assertEquals(1, requests.get())
        first.cancelAndJoin()
        release.complete(Unit)
        val retained = second.await()

        assertEquals(240, retained.width)
        assertEquals(360, retained.height)
        assertEquals("Identical observers must share one source exchange", 1, requests.get())
        loader.close()
    }

    private fun encodedBitmap(
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
    ): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).run {
            eraseColor(android.graphics.Color.rgb(20, 40, 60))
            assertTrue(compress(format, 90, output))
            recycle()
        }
        output.toByteArray()
    }
}
