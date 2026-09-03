/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.core.security.VerifiedBrowserSession
import org.tsuyomi.core.security.VerifiedBrowserSessionStore
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

data class CapturedVerifiedPage(
    val requestUrl: String,
    val pageUrl: String,
    val html: String,
) {
    init {
        require(requestUrl.isNotBlank())
        require(pageUrl.isNotBlank())
        require(html.isNotBlank())
    }
}

internal data class VerifiedPageNavigationBinding(
    val requestUrl: String,
    val pageUrl: String,
)

internal class VerifiedPageNavigationTracker {
    private var requestUrl: String? = null
    private var currentPageUrl: String? = null
    private var expectedPageUrl: String? = null
    private var settled = false
    private var automaticRedirectAvailable = false

    fun start(requestUrl: String) {
        require(requestUrl.isNotBlank())
        this.requestUrl = requestUrl
        currentPageUrl = null
        expectedPageUrl = requestUrl
        settled = false
        automaticRedirectAvailable = true
    }

    fun onMainFrameNavigation(targetUrl: String, isRedirect: Boolean, hasGesture: Boolean) {
        val request = requestUrl ?: return
        when {
            settled && automaticRedirectAvailable && !hasGesture -> {
                expectedPageUrl = targetUrl
                settled = false
                automaticRedirectAvailable = false
            }
            settled -> clear()
            currentPageUrl == null && targetUrl == request -> expectedPageUrl = targetUrl
            isRedirect -> {
                expectedPageUrl = targetUrl
                automaticRedirectAvailable = false
            }
            else -> clear()
        }
    }

    fun onPageStarted(targetUrl: String) {
        if (requestUrl == null) return
        when {
            settled && currentPageUrl == targetUrl -> Unit
            settled -> clear()
            expectedPageUrl == targetUrl -> {
                currentPageUrl = targetUrl
                expectedPageUrl = null
            }
            else -> {
                // Android WebView does not consistently call shouldOverrideUrlLoading for server
                // redirects. Until the explicit load settles, another allowed top-frame start is
                // therefore part of that same redirect chain.
                currentPageUrl = targetUrl
                expectedPageUrl = null
                automaticRedirectAvailable = false
            }
        }
    }

    fun onPageFinished(targetUrl: String) {
        if (requestUrl != null && currentPageUrl == targetUrl && expectedPageUrl == null) {
            settled = true
        }
    }

    fun bindingFor(pageUrl: String): VerifiedPageNavigationBinding? {
        val request = requestUrl ?: return null
        return pageUrl
            .takeIf { settled && expectedPageUrl == null && currentPageUrl == it }
            ?.let { VerifiedPageNavigationBinding(request, it) }
    }

    fun clear() {
        requestUrl = null
        currentPageUrl = null
        expectedPageUrl = null
        settled = false
        automaticRedirectAvailable = false
    }
}

/**
 * A user-visible, one-at-a-time login/verification session. Web content is never exposed to an HXP;
 * declared-origin request cookies and the exact current WebView user agent are encrypted into one
 * source/origin partition only after the user explicitly finishes the session.
 */
class ControlledWebLoginSession(
    private val context: Context,
    private val sourceId: String,
    private val allowedOrigins: Set<HttpsOrigin>,
    private val credentials: SourceCredentialStore,
    private val onBlockedNavigation: (Uri) -> Unit = {},
) {
    private var webView: WebView? = null
    private var active = false
    private var ownsGlobalSession = false
    private val verifiedPageNavigation = VerifiedPageNavigationTracker()

    init {
        require(sourceId.isNotBlank())
        require(allowedOrigins.isNotEmpty())
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun open(initialUrl: String): WebView {
        check(!active) { "Session is already active" }
        GLOBAL_SESSION.lock(this)
        ownsGlobalSession = true
        try {
            val initial = requireAllowed(Uri.parse(initialUrl))
            clearGlobalCookies()
            val restoredSessions = verifiedSessionsFor(initial)
            val initialOrigin = requireNotNull(originOf(initial))
            val restoredUserAgent = restoredSessions
                .firstOrNull { (origin, _) -> origin.canonical == initialOrigin.canonical }
                ?.second
                ?.userAgent
            val view = WebView(context)
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = true
                setSupportMultipleWindows(false)
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                if (restoredUserAgent != null) userAgentString = restoredUserAgent
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url
                    if (!isAllowed(target)) {
                        if (request.isForMainFrame) verifiedPageNavigation.clear()
                        onBlockedNavigation(target)
                        return true
                    }
                    if (request.isForMainFrame) {
                        verifiedPageNavigation.onMainFrameNavigation(
                            normalizedAllowedUrl(target),
                            request.isRedirect,
                            request.hasGesture(),
                        )
                    }
                    return false
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    val target = Uri.parse(url)
                    if (!isAllowed(target)) {
                        verifiedPageNavigation.clear()
                        onBlockedNavigation(target)
                        view.stopLoading()
                        return
                    }
                    verifiedPageNavigation.onPageStarted(normalizedAllowedUrl(target))
                }

                override fun onPageFinished(view: WebView, url: String) {
                    val target = Uri.parse(url)
                    if (!isAllowed(target)) {
                        verifiedPageNavigation.clear()
                        return
                    }
                    verifiedPageNavigation.onPageFinished(normalizedAllowedUrl(target))
                }
            }
            webView = view
            restoreCookies(restoredSessions)
            active = true
            view.loadUrl(initial.toString())
            return view
        } catch (failure: Throwable) {
            webView?.destroy()
            webView = null
            verifiedPageNavigation.clear()
            active = false
            releaseGlobalSession()
            throw failure
        }
    }

    /** Explicit user completion only. Stores no undeclared-origin cookies and always clears WebView state. */
    suspend fun finish() {
        try {
            val userAgent = webView?.settings?.userAgentString.orEmpty()
            val sessions = VerifiedBrowserSessionStore(credentials)
            allowedOrigins.forEach { origin ->
                CookieManager.getInstance().getCookie(origin.canonical)?.let { rawCookie ->
                    sessions.put(
                        SourceCredentialPartition(sourceId, origin),
                        VerifiedBrowserSession(rawCookie, userAgent),
                    )
                }
            }
        } finally {
            close()
        }
    }

    suspend fun cancel() = close()

    /** Route disposal must outlive the composition scope so immediate re-entry waits for cleanup. */
    fun dispose() {
        CLEANUP_SCOPE.launch { close() }
    }

    /** Explicitly loads the paused GET so allowed-origin redirects retain auditable provenance. */
    fun openVerifiedPage(requestUrl: String) {
        val view = checkNotNull(webView) { "Session is not active" }
        check(active) { "Session is not active" }
        val normalizedRequest = normalizedAllowedUrl(Uri.parse(requestUrl))
        verifiedPageNavigation.start(normalizedRequest)
        view.loadUrl(normalizedRequest)
    }

    /** Captures only the current allowed top-frame document. The caller must consume it in memory. */
    suspend fun captureCurrentPage(maxBytes: Int): CapturedVerifiedPage {
        require(maxBytes in 1..MAX_SNAPSHOT_BYTES)
        val view = checkNotNull(webView) { "Session is not active" }
        check(active && view.progress == 100) { "Page is not ready" }
        val rawResult = suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(
                """(function(){var root=document.documentElement;var html=root?root.outerHTML:'';var bytes=new Blob([html]).size;return JSON.stringify({url:window.location.href,html:bytes<=$maxBytes?html:null,oversized:bytes>$maxBytes});})()""",
            ) { value -> continuation.resume(value) }
        }
        val payload = when (val value = JSONTokener(rawResult).nextValue()) {
            is JSONObject -> value
            is String -> JSONObject(value)
            else -> throw IllegalStateException("WebView snapshot is unavailable")
        }
        val pageUrl = normalizedAllowedUrl(Uri.parse(payload.getString("url")))
        require(!payload.getBoolean("oversized")) { "WebView snapshot exceeds the response limit" }
        val html = payload.optString("html")
        require(html.isNotBlank()) { "WebView snapshot is empty" }
        require(html.encodeToByteArray().size <= maxBytes) { "WebView snapshot exceeds the response limit" }
        val binding = verifiedPageNavigation.bindingFor(pageUrl)
        return CapturedVerifiedPage(
            requestUrl = binding?.requestUrl ?: pageUrl,
            pageUrl = pageUrl,
            html = html,
        )
    }

    private suspend fun close() {
        if (!ownsGlobalSession && !active && webView == null) return
        try {
            webView?.apply {
                stopLoading()
                clearHistory()
                clearCache(true)
                destroy()
            }
            webView = null
            clearGlobalCookies()
        } finally {
            verifiedPageNavigation.clear()
            active = false
            releaseGlobalSession()
        }
    }

    private fun releaseGlobalSession() {
        if (!ownsGlobalSession) return
        ownsGlobalSession = false
        GLOBAL_SESSION.unlock(this)
    }

    private fun requireAllowed(uri: Uri): Uri = uri.takeIf(::isAllowed)
        ?: throw IllegalArgumentException("Navigation origin is not declared")

    private fun normalizedAllowedUrl(uri: Uri): String = requireAllowed(uri)
        .buildUpon()
        .fragment(null)
        .build()
        .toString()

    private fun isAllowed(uri: Uri): Boolean {
        val origin = originOf(uri) ?: return false
        return allowedOrigins.any { it.canonical == origin.canonical }
    }

    private fun originOf(uri: Uri): HttpsOrigin? {
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null) {
            return null
        }
        return runCatching {
            HttpsOrigin("https://${uri.host}${if (uri.port in 1..65535 && uri.port != 443) ":${uri.port}" else ""}")
        }.getOrNull()
    }

    private fun verifiedSessionsFor(initial: Uri): List<Pair<HttpsOrigin, VerifiedBrowserSession>> {
        val initialOrigin = requireNotNull(originOf(initial))
        val sessions = VerifiedBrowserSessionStore(credentials)
        val initialSession = sessions.getSnapshot(SourceCredentialPartition(sourceId, initialOrigin))?.session
            ?: return emptyList()
        return buildList {
            add(initialOrigin to initialSession)
            allowedOrigins.forEach { origin ->
                if (origin.canonical == initialOrigin.canonical) return@forEach
                sessions.getSnapshot(SourceCredentialPartition(sourceId, origin))
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

    private suspend fun clearGlobalCookies() = suspendCancellableCoroutine { continuation ->
        CookieManager.getInstance().removeAllCookies { continuation.resume(Unit) }
        CookieManager.getInstance().flush()
    }

    private companion object {
        val GLOBAL_SESSION = Mutex()
        val CLEANUP_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        const val MAX_SNAPSHOT_BYTES = 2 * 1024 * 1024
    }
}
