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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tsuyomi.core.security.SourceCredentialPartition
import org.tsuyomi.core.security.SourceCredentialStore
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import kotlin.coroutines.resume

/**
 * A user-visible, one-at-a-time login/verification session. Web content is never exposed to an HXP;
 * only declared-origin request cookie pairs are encrypted into their source partition after the user
 * explicitly finishes the session.
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

    init {
        require(sourceId.isNotBlank())
        require(allowedOrigins.isNotEmpty())
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun open(initialUrl: String): WebView {
        check(!active) { "Session is already active" }
        check(GLOBAL_SESSION.compareAndSet(false, true)) { "Another WebView verification session is active" }
        try {
            val initial = requireAllowed(Uri.parse(initialUrl))
            clearGlobalCookies()
            val view = WebView(context)
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = true
                setSupportMultipleWindows(false)
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url
                    return if (isAllowed(target)) false else {
                        onBlockedNavigation(target)
                        true
                    }
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    val target = Uri.parse(url)
                    if (!isAllowed(target)) {
                        onBlockedNavigation(target)
                        view.stopLoading()
                    }
                }
            }
            webView = view
            active = true
            view.loadUrl(initial.toString())
            return view
        } catch (failure: Throwable) {
            GLOBAL_SESSION.set(false)
            throw failure
        }
    }

    /** Explicit user completion only. Copies no cookies from undeclared origins and always clears WebView state. */
    suspend fun finish() {
        try {
            allowedOrigins.forEach { origin ->
                CookieManager.getInstance().getCookie(origin.canonical)?.let { rawCookie ->
                    credentials.put(SourceCredentialPartition(sourceId, origin), rawCookie.encodeToByteArray())
                }
            }
        } finally {
            close()
        }
    }

    suspend fun cancel() = close()

    private suspend fun close() {
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
            active = false
            GLOBAL_SESSION.set(false)
        }
    }

    private fun requireAllowed(uri: Uri): Uri = uri.takeIf(::isAllowed)
        ?: throw IllegalArgumentException("Navigation origin is not declared")

    private fun isAllowed(uri: Uri): Boolean {
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null) return false
        val origin = runCatching {
            HttpsOrigin("https://${uri.host}${if (uri.port in 1..65535 && uri.port != 443) ":${uri.port}" else ""}")
        }.getOrNull() ?: return false
        return allowedOrigins.any { it.canonical == origin.canonical }
    }

    private suspend fun clearGlobalCookies() = suspendCancellableCoroutine { continuation ->
        CookieManager.getInstance().removeAllCookies { continuation.resume(Unit) }
        CookieManager.getInstance().flush()
    }

    private companion object {
        val GLOBAL_SESSION = AtomicBoolean(false)
    }
}
