/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.media.internal

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
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
    }
}
