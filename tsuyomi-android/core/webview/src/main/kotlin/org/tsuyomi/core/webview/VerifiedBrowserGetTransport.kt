/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import org.tsuyomi.core.network.HostHttpRequest
import org.tsuyomi.core.network.HostHttpResponse
import org.tsuyomi.core.network.HostResponseHeaders
import org.tsuyomi.core.network.HostHttpTransport
import org.tsuyomi.core.network.HostNetworkError
import org.tsuyomi.core.network.HostNetworkException
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.core.security.VerifiedBrowserSession
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import kotlin.coroutines.resume

/**
 * Host-owned GET retry after a user-completed verified session. Direct HttpURLConnection remains
 * first; this transport only runs when classified Home/Search/Detail/Directory/Chapter/shelf reads
 * still see a challenge. HTML never leaves the host, and HXP code never receives the WebView.
 */
class VerifiedBrowserGetTransport(
    context: Context,
    private val sourceId: String,
    private val allowedOrigins: Set<HttpsOrigin>,
) : HostHttpTransport {
    private val appContext = context.applicationContext
    private val credentials = SourceCredentialStore(appContext)
    private var warmView: WebView? = null
    private var nextRequestAt = 0L
    private var pacingMillis = MIN_INTERVAL_MS
    private var lastFetchAt = 0L
    private var burst = 0

    init {
        // The warm WebView costs a whole sandboxed renderer, so release it when the app leaves the
        // foreground. Releasing on an idle timer instead would rebuild it mid-read.
        (appContext as? android.app.Application)?.registerActivityLifecycleCallbacks(
            object : android.app.Application.ActivityLifecycleCallbacks {
                override fun onActivityStopped(activity: android.app.Activity) {
                    warmView?.let(::releaseView)
                }

                override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
                override fun onActivityStarted(activity: android.app.Activity) = Unit
                override fun onActivityResumed(activity: android.app.Activity) = Unit
                override fun onActivityPaused(activity: android.app.Activity) = Unit
                override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
                override fun onActivityDestroyed(activity: android.app.Activity) = Unit
            },
        )
    }

    override suspend fun execute(request: HostHttpRequest): HostHttpResponse {
        if (request.method != NetworkMethod.GET || request.body != null) {
            throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
        }
        val origin = originOf(request.url) ?: throw HostNetworkException(HostNetworkError.DISALLOWED_ORIGIN)
        if (allowedOrigins.none { it.canonical == origin.canonical }) {
            throw HostNetworkException(HostNetworkError.DISALLOWED_ORIGIN)
        }
        val startedAt = SystemClock.uptimeMillis()
        pace()
        val response = ControlledWebLoginSession.withIdleBrowser {
            withContext(Dispatchers.Main) { fetch(request, origin) }
        } ?: throw HostNetworkException(HostNetworkError.TRANSPORT)
        return response
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetch(request: HostHttpRequest, origin: HttpsOrigin): HostHttpResponse {
        val sessions = restoreSessions(origin)
        val userAgent = sessions.firstOrNull { it.first.canonical == origin.canonical }?.second?.userAgent
            ?: sessions.firstOrNull()?.second?.userAgent
        restoreCookies(sessions)
        val view = warmView ?: createView()
        try {
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 1080, 1920)
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = true
                setSupportMultipleWindows(false)
                if (userAgent != null) userAgentString = userAgent
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
            val finished = Channel<Unit>(Channel.CONFLATED)
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, webRequest: WebResourceRequest): Boolean {
                    return originOf(URI(webRequest.url.toString()))?.canonical !in allowedOrigins.map { it.canonical }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    finished.trySend(Unit)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    finished.trySend(Unit)
                }
            }
            val extraHeaders = request.referrer?.let { mapOf("Referer" to it.toASCIIString()) }.orEmpty()
            if (extraHeaders.isEmpty()) view.loadUrl(request.url.toString()) else view.loadUrl(request.url.toString(), extraHeaders)
            awaitSettled(finished, view, request.timeoutMs)
            val html = captureHtml(view, request.maxResponseBytes)
            if (pageMarkersOf(html).contains(ACCESS_DENIED_MARKER)) {
                noteBlocked()
                throw HostNetworkException(HostNetworkError.TRANSPORT)
            }
            noteServed()
            persistCookies(sessions, view.settings.userAgentString.orEmpty())
            val bytes = encode(html, request.decode)
            if (bytes.size > request.maxResponseBytes) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            return HostHttpResponse(
                status = 200,
                finalUrl = request.url,
                headers = HostResponseHeaders.of("content-type" to "text/html; charset=${charsetName(request.decode)}"),
                bytes = bytes,
            )
        } catch (error: Throwable) {
            releaseView(view)
            throw error
        }
    }

    /**
     * The site sits behind a WAF that rejects a sustained burst of browser requests, but one page
     * load legitimately needs several fetches in a row. Throttle only once a burst exceeds the
     * allowed number, so interactive reading stays responsive and a fan-out still backs off.
     */
    private suspend fun pace() {
        val now = SystemClock.uptimeMillis()
        burst = if (now - lastFetchAt <= BURST_WINDOW_MS) burst + 1 else 1
        if (burst > BURST_FREE_FETCHES) {
            val wait = nextRequestAt - now
            if (wait > 0) delay(wait)
        }
        lastFetchAt = SystemClock.uptimeMillis()
        nextRequestAt = lastFetchAt + pacingMillis
    }

    private fun noteBlocked() {
        pacingMillis = (pacingMillis * 2).coerceAtMost(MAX_INTERVAL_MS)
        Log.w(WEBVIEW_TIMING_TAG, "blocked-backoff interval=${pacingMillis}ms")
    }

    private fun noteServed() {
        pacingMillis = (pacingMillis * 3 / 4).coerceAtLeast(MIN_INTERVAL_MS)
    }

    private fun releaseView(view: WebView) {
        if (warmView === view) warmView = null
        runCatching { view.stopLoading() }
        runCatching { view.destroy() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createView(): WebView {
        val view = WebView(appContext).also { warmView = it }
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1080, 1920)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
        return view
    }

    /**
     * The host's own browsing WebView can already hold an admitted session for this origin while the
     * encrypted partition is still empty. Adopt that jar once, so a challenged fetch does not
     * require a separate verification round trip, and record why adoption was impossible.
     */
    private fun adoptBrowserSession(
        origin: HttpsOrigin,
        store: VerifiedBrowserSessionStore,
    ): VerifiedBrowserSession {
        val rawCookie = CookieManager.getInstance().getCookie(origin.canonical)?.takeIf { it.isNotBlank() }
        if (rawCookie == null) {
            Log.w(WEBVIEW_TIMING_TAG, "fallback-unavailable reason=no-session ${origin.canonical}")
            throw HostNetworkException(HostNetworkError.TRANSPORT)
        }
        val session = VerifiedBrowserSession(rawCookie, WebSettings.getDefaultUserAgent(appContext))
        store.put(SourceCredentialPartition(sourceId, origin), session)
        Log.i(WEBVIEW_TIMING_TAG, "adopted-session ${origin.canonical}")
        return session
    }

    private fun pageMarkersOf(html: String): String = buildList {
        if (html.contains("Just a moment", ignoreCase = true)) add("just-a-moment")
        if (html.contains("challenge-platform")) add("challenge-platform")
        if (html.contains("cf_chl_opt")) add("cf-chl-opt")
        if (html.contains("Access denied", ignoreCase = true) || html.contains("restricted access", ignoreCase = true)) {
            add(ACCESS_DENIED_MARKER)
        }
        if (html.contains("登录")) add("login-zh")
        if (html.contains("password", ignoreCase = true)) add("password")
    }.joinToString(",")

    private fun restoreSessions(initial: HttpsOrigin): List<Pair<HttpsOrigin, VerifiedBrowserSession>> {
        val store = VerifiedBrowserSessionStore(credentials)
        val initialSession = store.getSnapshot(SourceCredentialPartition(sourceId, initial))?.session
            ?: adoptBrowserSession(initial, store)
        return buildList {
            add(initial to initialSession)
            allowedOrigins.forEach { origin ->
                if (origin.canonical == initial.canonical) return@forEach
                store.getSnapshot(SourceCredentialPartition(sourceId, origin))
                    ?.session
                    ?.takeIf { it.userAgent == initialSession.userAgent }
                    ?.let { add(origin to it) }
            }
        }
    }

    private suspend fun restoreCookies(sessions: List<Pair<HttpsOrigin, VerifiedBrowserSession>>) {
        val cookies = CookieManager.getInstance()
        sessions.forEach { (origin, session) ->
            session.requestCookies.split(';').forEach { fragment ->
                val pair = fragment.trim()
                if (pair.indexOf('=') > 0) {
                    suspendCancellableCoroutine { continuation ->
                        cookies.setCookie(origin.canonical, "$pair; Path=/; Secure") {
                            continuation.resume(Unit)
                        }
                    }
                }
            }
        }
        cookies.flush()
    }

    private fun persistCookies(sessions: List<Pair<HttpsOrigin, VerifiedBrowserSession>>, userAgent: String) {
        val store = VerifiedBrowserSessionStore(credentials)
        sessions.forEach { (origin, session) ->
            val rawCookie = CookieManager.getInstance().getCookie(origin.canonical) ?: return@forEach
            val agent = userAgent.ifBlank { session.userAgent }
            if (rawCookie == session.requestCookies && agent == session.userAgent) return@forEach
            store.put(SourceCredentialPartition(sourceId, origin), VerifiedBrowserSession(rawCookie, agent))
        }
    }

    private suspend fun awaitSettled(finished: Channel<Unit>, view: WebView, timeoutMs: Int) {
        val budget = timeoutMs.coerceIn(3_000, 25_000).toLong()
        // A challenge interstitial reloads itself when it is solved, and a page reports finished
        // before late scripts mutate the DOM. Keep consuming finish signals inside one bounded
        // budget instead of polling, and never hand a solved-away challenge back to the caller.
        withTimeoutOrNull(budget) {
            while (true) {
                finished.receive()
                delay(SETTLE_MS)
                if (!view.title.orEmpty().contains("Just a moment", ignoreCase = true)) return@withTimeoutOrNull
            }
        }
    }

    private suspend fun captureHtml(view: WebView, maxBytes: Int): String {
        val rawResult = suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(
                """(function(){var root=document.documentElement;var html=root?root.outerHTML:'';return JSON.stringify({html:html.length<=$maxBytes?html:null,oversized:html.length>$maxBytes});})()""",
            ) { value -> continuation.resume(value) }
        }
        val payload = when (val value = JSONTokener(rawResult).nextValue()) {
            is JSONObject -> value
            is String -> JSONObject(value)
            else -> throw HostNetworkException(HostNetworkError.TRANSPORT)
        }
        if (payload.getBoolean("oversized")) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
        val html = payload.optString("html")
        if (html.isBlank()) throw HostNetworkException(HostNetworkError.TRANSPORT)
        return html
    }

    private fun encode(html: String, decode: DecodeMode): ByteArray {
        val charset = when (decode) {
            DecodeMode.AUTO, DecodeMode.UTF8 -> StandardCharsets.UTF_8
            DecodeMode.GB18030 -> Charset.forName("GB18030")
            DecodeMode.BIG5_HKSCS -> Charset.forName("Big5-HKSCS")
        }
        return html.toByteArray(charset)
    }

    private fun charsetName(decode: DecodeMode): String = when (decode) {
        DecodeMode.AUTO, DecodeMode.UTF8 -> "utf-8"
        DecodeMode.GB18030 -> "gb18030"
        DecodeMode.BIG5_HKSCS -> "big5-hkscs"
    }

    private fun originOf(uri: URI): HttpsOrigin? {
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null) {
            return null
        }
        return runCatching {
            HttpsOrigin("https://${uri.host}${if (uri.port in 1..65535 && uri.port != 443) ":${uri.port}" else ""}")
        }.getOrNull()
    }

    private companion object {
        /** Bounded settle after onPageFinished so late DOM mutations are still captured. */
        const val SETTLE_MS = 250L

        /** Sustained fetches inside this window count as one burst. */
        const val BURST_WINDOW_MS = 5_000L

        /** Fetches a single page load may make before throttling starts. */
        const val BURST_FREE_FETCHES = 3

        /** Minimum interval between verified fetches; the WAF rejected a 1.2s cadence after four pages. */
        const val MIN_INTERVAL_MS = 2_000L

        /** Upper bound of the exponential backoff applied after a WAF rejection. */
        const val MAX_INTERVAL_MS = 15_000L

        const val ACCESS_DENIED_MARKER = "access-denied"

        /** Diagnostic timing tag: one line per verified fallback fetch. */
        const val WEBVIEW_TIMING_TAG = "TsuyomiWebView"
    }
}
