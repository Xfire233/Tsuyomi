/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.media.internal

import android.graphics.Bitmap
import java.util.concurrent.CancellationException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.media.api.CoverMediaFetcher
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec
import org.tsuyomi.shared.sourcecontract.HttpsOrigin

@RunWith(AndroidJUnit4::class)
class HostCoverLoaderInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        java.io.File(context.cacheDir, "cover-media-test").deleteRecursively()
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
    }
}
