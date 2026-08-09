/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.webview

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class ControlledWebLoginSessionInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val origin = HttpsOrigin("https://allowed.example")
    private val partition = SourceCredentialPartition("fixture.source", origin)
    private val credentials = SourceCredentialStore(context)

    @After
    fun cleanUp() = runBlocking(Dispatchers.Main) {
        credentials.delete(partition)
        clearCookies()
    }

    @Test
    fun explicit_finish_persists_only_allowed_origin_cookie() = runBlocking(Dispatchers.Main) {
        val session = ControlledWebLoginSession(context, "fixture.source", setOf(origin), credentials)
        session.open("https://allowed.example/login")
        setCookie(origin.canonical, "session=fixture; Secure")

        session.finish()

        assertEquals("session=fixture", credentials.get(partition)?.decodeToString())
    }

    @Test
    fun cancel_discards_webview_cookie() = runBlocking(Dispatchers.Main) {
        val session = ControlledWebLoginSession(context, "fixture.source", setOf(origin), credentials)
        session.open("https://allowed.example/login")
        setCookie(origin.canonical, "session=discarded; Secure")

        session.cancel()

        assertNull(credentials.get(partition))
    }

    @Test(expected = IllegalArgumentException::class)
    fun undeclared_initial_origin_is_blocked() {
        runBlocking(Dispatchers.Main) {
            ControlledWebLoginSession(context, "fixture.source", setOf(origin), credentials)
                .open("https://blocked.example/login")
        }
    }

    private suspend fun setCookie(url: String, cookie: String) = suspendCancellableCoroutine { continuation ->
        CookieManager.getInstance().setCookie(url, cookie) { continuation.resume(Unit) }
        CookieManager.getInstance().flush()
    }

    private suspend fun clearCookies() = suspendCancellableCoroutine { continuation ->
        CookieManager.getInstance().removeAllCookies { continuation.resume(Unit) }
        CookieManager.getInstance().flush()
    }
}
