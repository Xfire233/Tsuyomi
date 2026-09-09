/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import org.tsuyomi.core.network.HostHttpRequest
import org.tsuyomi.core.network.HostHttpResponse
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

    override suspend fun execute(request: HostHttpRequest): HostHttpResponse {
        if (request.method != NetworkMethod.GET || request.body != null) {
            throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
        }
        val origin = originOf(request.url) ?: throw HostNetworkException(HostNetworkError.DISALLOWED_ORIGIN)
        if (allowedOrigins.none { it.canonical == origin.canonical }) {
            throw HostNetworkException(HostNetworkError.DISALLOWED_ORIGIN)
        }
        return ControlledWebLoginSession.withIdleBrowser {
            withContext(Dispatchers.Main) { fetch(request, origin) }
        } ?: throw HostNetworkException(HostNetworkError.TRANSPORT)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetch(request: HostHttpRequest, origin: HttpsOrigin): HostHttpResponse {
        val sessions = restoreSessions(origin)
        val userAgent = sessions.firstOrNull { it.first.canonical == origin.canonical }?.second?.userAgent
            ?: sessions.firstOrNull()?.second?.userAgent
        restoreCookies(sessions)
        val view = WebView(appContext)
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
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, webRequest: WebResourceRequest): Boolean {
                    return originOf(URI(webRequest.url.toString()))?.canonical !in allowedOrigins.map { it.canonical }
                }
            }
            val extraHeaders = request.referrer?.let { mapOf("Referer" to it.toASCIIString()) }.orEmpty()
            if (extraHeaders.isEmpty()) view.loadUrl(request.url.toString()) else view.loadUrl(request.url.toString(), extraHeaders)
            awaitSettled(view, request.timeoutMs)
            val html = captureHtml(view, request.maxResponseBytes)
            persistCookies(sessions, view.settings.userAgentString.orEmpty())
            val bytes = encode(html, request.decode)
            if (bytes.size > request.maxResponseBytes) throw HostNetworkException(HostNetworkError.RESPONSE_LIMIT)
            return HostHttpResponse(
                status = 200,
                finalUrl = request.url,
                headers = mapOf("content-type" to "text/html; charset=${charsetName(request.decode)}"),
                bytes = bytes,
            )
        } finally {
            view.stopLoading()
            view.destroy()
        }
    }

    private fun restoreSessions(initial: HttpsOrigin): List<Pair<HttpsOrigin, VerifiedBrowserSession>> {
        val store = VerifiedBrowserSessionStore(credentials)
        val initialSession = store.getSnapshot(SourceCredentialPartition(sourceId, initial))?.session
            ?: throw HostNetworkException(HostNetworkError.TRANSPORT)
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
        val agent = userAgent.ifBlank { sessions.first().second.userAgent }
        sessions.forEach { (origin, _) ->
            CookieManager.getInstance().getCookie(origin.canonical)?.let { rawCookie ->
                store.put(SourceCredentialPartition(sourceId, origin), VerifiedBrowserSession(rawCookie, agent))
            }
        }
    }

    private suspend fun awaitSettled(view: WebView, timeoutMs: Int) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs.coerceIn(3_000, 25_000)
        while (SystemClock.uptimeMillis() < deadline) {
            val title = view.title.orEmpty()
            if (view.progress >= 100 && title.isNotBlank() && !title.contains("Just a moment", ignoreCase = true)) {
                delay(350)
                if (!view.title.orEmpty().contains("Just a moment", ignoreCase = true)) return
            }
            delay(250)
        }
    }

    private suspend fun captureHtml(view: WebView, maxBytes: Int): String {
        val rawResult = suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(
                """(function(){var root=document.documentElement;var html=root?root.outerHTML:'';var bytes=new Blob([html]).size;return JSON.stringify({html:bytes<=$maxBytes?html:null,oversized:bytes>$maxBytes});})()""",
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
}
