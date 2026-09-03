/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.webview

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.core.security.VerifiedBrowserSession
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class ControlledWebLoginSessionInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val origin = HttpsOrigin("https://allowed.example")
    private val partition = SourceCredentialPartition("fixture.source", origin)
    private val otherPartition = SourceCredentialPartition("other.source", origin)
    private val credentials = SourceCredentialStore(context)

    @After
    fun cleanUp() = runBlocking(Dispatchers.Main) {
        credentials.delete(partition)
        credentials.delete(otherPartition)
        clearCookies()
    }

    @Test
    fun explicit_finish_persists_cookie_and_exact_user_agent() = runBlocking(Dispatchers.Main) {
        val session = ControlledWebLoginSession(context, "fixture.source", setOf(origin), credentials)
        val view = session.open("https://allowed.example/login")
        val expectedUserAgent = view.settings.userAgentString
        setCookie(origin.canonical, "session=fixture; Secure")

        session.finish()

        val stored = requireNotNull(VerifiedBrowserSessionStore(credentials).getSnapshot(partition)).session
        assertEquals("session=fixture", stored.requestCookies)
        assertEquals(expectedUserAgent, stored.userAgent)
    }

    @Test
    fun completed_session_rehydrates_cookies_and_exact_user_agent_on_reentry() = runBlocking(Dispatchers.Main) {
        val first = ControlledWebLoginSession(context, "fixture.source", setOf(origin), credentials)
        val firstView = first.open("https://allowed.example/login")
        val expectedUserAgent = firstView.settings.userAgentString
        setCookie(origin.canonical, "session=fixture; Secure")
        setCookie(origin.canonical, "preference=compact; Secure")

        first.finish()

        assertNull(CookieManager.getInstance().getCookie(origin.canonical))
        val second = ControlledWebLoginSession(context, "fixture.source", setOf(origin), credentials)
        val secondView = second.open("https://allowed.example/login")
        try {
            val restoredCookies = requireNotNull(CookieManager.getInstance().getCookie(origin.canonical))
            assertTrue(restoredCookies.contains("session=fixture"))
            assertTrue(restoredCookies.contains("preference=compact"))
            assertEquals(expectedUserAgent, secondView.settings.userAgentString)
        } finally {
            second.cancel()
        }

        assertNull(CookieManager.getInstance().getCookie(origin.canonical))
        val stored = requireNotNull(VerifiedBrowserSessionStore(credentials).getSnapshot(partition)).session
        assertTrue(stored.requestCookies.contains("session=fixture"))
        assertTrue(stored.requestCookies.contains("preference=compact"))
        assertEquals(expectedUserAgent, stored.userAgent)
    }

    @Test
    fun disposed_route_finishes_cleanup_before_immediate_reentry() = runBlocking(Dispatchers.Main) {
        val first = ControlledWebLoginSession(context, "fixture.source", setOf(origin), credentials)
        first.open("https://allowed.example/login")
        first.dispose()

        val second = ControlledWebLoginSession(context, "fixture.source", setOf(origin), credentials)
        val secondView = withTimeout(5_000) {
            second.open("https://allowed.example/login")
        }
        try {
            assertTrue(secondView.settings.javaScriptEnabled)
        } finally {
            second.cancel()
        }
    }

    @Test
    fun reentry_never_imports_another_sources_session() = runBlocking(Dispatchers.Main) {
        VerifiedBrowserSessionStore(credentials).put(
            otherPartition,
            VerifiedBrowserSession("other=secret", "other-agent/1"),
        )
        val session = ControlledWebLoginSession(context, "fixture.source", setOf(origin), credentials)
        val view = session.open("https://allowed.example/login")
        try {
            assertNull(CookieManager.getInstance().getCookie(origin.canonical))
            assertFalse(view.settings.userAgentString == "other-agent/1")
        } finally {
            session.cancel()
        }

        assertTrue(VerifiedBrowserSessionStore(credentials).getSnapshot(otherPartition) != null)
    }

    @Test
    fun opened_page_fits_phone_width_and_keeps_pinch_zoom() = runBlocking(Dispatchers.Main) {
        val session = ControlledWebLoginSession(context, "fixture.source", setOf(origin), credentials)
        val view = session.open("https://allowed.example/login")
        try {
            assertTrue(view.settings.useWideViewPort)
            assertTrue(view.settings.loadWithOverviewMode)
            assertTrue(view.settings.supportZoom())
            assertTrue(view.settings.builtInZoomControls)
            assertFalse(view.settings.displayZoomControls)
        } finally {
            session.cancel()
        }
    }

    @Test
    fun current_allowed_page_snapshot_is_memory_bounded() = runBlocking(Dispatchers.Main) {
        val session = ControlledWebLoginSession(context, "fixture.source", setOf(origin), credentials)
        val view = session.open("https://allowed.example/login")
        try {
            view.loadDataWithBaseURL(
                "https://allowed.example/search?query=fixture",
                "<html><body><h1>Fixture result</h1></body></html>",
                "text/html",
                "utf-8",
                null,
            )
            withTimeout(5_000) {
                while (view.progress < 100) delay(10)
            }

            val snapshot = session.captureCurrentPage(maxBytes = 4_096)
            assertEquals("https://allowed.example/search?query=fixture", snapshot.requestUrl)
            assertEquals("https://allowed.example/search?query=fixture", snapshot.pageUrl)
            assertTrue(snapshot.html.contains("Fixture result"))
            assertTrue(runCatching { session.captureCurrentPage(maxBytes = 16) }.exceptionOrNull() is IllegalArgumentException)
        } finally {
            session.cancel()
        }
    }

    @Test
    fun explicit_verified_page_binding_preserves_redirect_and_rejects_later_navigation() {
        val tracker = VerifiedPageNavigationTracker()
        val requestUrl = "https://allowed.example/search?query=fixture"
        val redirectedPageUrl = "https://allowed.example/book/1234.htm"
        tracker.start(requestUrl)
        tracker.onPageStarted(requestUrl)
        tracker.onMainFrameNavigation(redirectedPageUrl, isRedirect = true, hasGesture = false)
        tracker.onPageStarted(redirectedPageUrl)
        tracker.onPageFinished(redirectedPageUrl)

        assertEquals(
            VerifiedPageNavigationBinding(requestUrl, redirectedPageUrl),
            tracker.bindingFor(redirectedPageUrl),
        )

        tracker.onMainFrameNavigation(
            "https://allowed.example/book/5678.htm",
            isRedirect = false,
            hasGesture = true,
        )
        assertNull(tracker.bindingFor(redirectedPageUrl))
    }

    @Test
    fun explicit_verified_page_binding_preserves_redirect_when_webview_omits_navigation_callback() {
        val tracker = VerifiedPageNavigationTracker()
        val requestUrl = "https://allowed.example/search?query=fixture"
        val redirectedPageUrl = "https://allowed.example/book/1234.htm"
        tracker.start(requestUrl)
        tracker.onPageStarted(requestUrl)
        tracker.onPageStarted(redirectedPageUrl)
        tracker.onPageFinished(redirectedPageUrl)

        assertEquals(
            VerifiedPageNavigationBinding(requestUrl, redirectedPageUrl),
            tracker.bindingFor(redirectedPageUrl),
        )
    }

    @Test
    fun explicit_verified_page_binding_accepts_one_gestureless_redirect_after_request_settles() {
        val tracker = VerifiedPageNavigationTracker()
        val requestUrl = "https://allowed.example/search?query=fixture"
        val redirectedPageUrl = "https://allowed.example/book/1234.htm"
        tracker.start(requestUrl)
        tracker.onPageStarted(requestUrl)
        tracker.onPageFinished(requestUrl)
        tracker.onMainFrameNavigation(redirectedPageUrl, isRedirect = false, hasGesture = false)
        tracker.onPageStarted(redirectedPageUrl)
        tracker.onPageFinished(redirectedPageUrl)

        assertEquals(
            VerifiedPageNavigationBinding(requestUrl, redirectedPageUrl),
            tracker.bindingFor(redirectedPageUrl),
        )

        tracker.onMainFrameNavigation(
            "https://allowed.example/book/5678.htm",
            isRedirect = false,
            hasGesture = false,
        )
        assertNull(tracker.bindingFor(redirectedPageUrl))
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
