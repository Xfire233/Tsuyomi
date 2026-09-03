/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import android.provider.Settings
import android.webkit.WebSettings
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.tsuyomi.core.network.HostHttpResponse
import org.tsuyomi.core.network.HostHttpTransport
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.core.network.HostNetworkGateway
import org.tsuyomi.core.network.HostNetworkError
import org.tsuyomi.core.network.HostNetworkException
import org.tsuyomi.core.network.UrlConnectionHostHttpTransport
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.core.webview.CapturedVerifiedPage
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

/** Debug acceptance is deterministic: signed extension code parses sanitized fixture transport. */
internal object Phase2SourceGateway {
    private val sourceRequests = AtomicInteger()
    private val searchRequests = AtomicInteger()
    private val homeRequests = AtomicInteger()
    private val detailRequests = AtomicInteger()
    private val directoryRequests = AtomicInteger()
    private val chapterRequests = AtomicInteger()
    private val liveTransportRequests = AtomicInteger()
    private val remoteLibraryReads = AtomicInteger()
    private val requireNextDetailVerification = AtomicBoolean()
    private val requireNextChapterVerification = AtomicBoolean()
    private val websiteMutations = AtomicInteger()

    fun resetOperationCounts() {
        sourceRequests.set(0)
        searchRequests.set(0)
        homeRequests.set(0)
        detailRequests.set(0)
        directoryRequests.set(0)
        chapterRequests.set(0)
        liveTransportRequests.set(0)
        remoteLibraryReads.set(0)
        requireNextDetailVerification.set(false)
        requireNextChapterVerification.set(false)
        websiteMutations.set(0)
    }

    fun remoteLibraryReadCount(): Int = remoteLibraryReads.get()


    fun sourceRequestCount(): Int = sourceRequests.get()
    fun searchRequestCount(): Int = searchRequests.get()
    fun homeRequestCount(): Int = homeRequests.get()
    fun detailRequestCount(): Int = detailRequests.get()
    fun directoryRequestCount(): Int = directoryRequests.get()
    fun chapterRequestCount(): Int = chapterRequests.get()
    fun liveTransportRequestCount(): Int = liveTransportRequests.get()
    fun websiteMutationCount(): Int = websiteMutations.get()

    fun requireVerificationForNextDetailRequest() {
        requireNextDetailVerification.set(true)
    }

    fun requireVerificationForNextChapterRequest() {
        requireNextChapterVerification.set(true)
    }

    fun preparePriorFailedSearchForLiveContinuation(context: Context, expectedUrl: String) {
        require(URI(expectedUrl).let { uri ->
            uri.scheme.equals("https", ignoreCase = true) && uri.host == "www.wenku8.net" &&
                uri.path == "/modules/article/search.php"
        })
        liveTransportMarker(context).writeText("enabled")
        priorFailedSearchMarker(context).writeText(expectedUrl)
    }

    fun clearLiveValidationMode(context: Context) {
        liveTransportMarker(context).delete()
        priorFailedSearchMarker(context).delete()
    }

    fun create(
        context: Context,
        packageInfo: VerifiedHxpPackage,
        directActionTokens: DirectActionTokenRegistry,
    ): HostNetworkGateway {
        val liveTransport = UrlConnectionHostHttpTransport()
        val transport = HostHttpTransport { request ->
            sourceRequests.incrementAndGet()
            if (request.url.path == "/modules/article/search.php") searchRequests.incrementAndGet()
            if (
                request.url.path in setOf("/index.php", "/modules/article/toplist.php", "/modules/article/tags.php") ||
                request.url.path.startsWith("/zt/sugoi/")
            ) homeRequests.incrementAndGet()
            if (request.url.path.startsWith("/book/")) detailRequests.incrementAndGet()
            if (
                request.url.path.endsWith("/index.htm") ||
                request.url.path == "/modules/article/reader.php" &&
                !request.url.rawQuery.orEmpty().contains("cid=")
            ) {
                directoryRequests.incrementAndGet()
            }
            val chapterRequest = request.url.path == "/modules/article/reader.php" &&
                request.url.rawQuery.orEmpty().split('&').any { it.startsWith("cid=") }
            if (chapterRequest) chapterRequests.incrementAndGet()
            val priorFailedSearch = priorFailedSearchMarker(context).takeIf { it.isFile }
            if (priorFailedSearch != null) {
                if (request.method != NetworkMethod.GET || priorFailedSearch.readText() != request.url.toString()) {
                    throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
                }
                check(priorFailedSearch.delete()) { "Prior failed search continuation was already consumed" }
                return@HostHttpTransport fixtureResponse(context, request.url, "login.html")
            }
            if (liveTransportMarker(context).isFile) {
                liveTransportRequests.incrementAndGet()
                return@HostHttpTransport liveTransport.execute(request)
            }
            if (request.url.path == "/modules/article/bookcase.php") {
                if (request.method == NetworkMethod.POST) {
                    websiteMutations.incrementAndGet()
                } else {
                    remoteLibraryReads.incrementAndGet()
                }
            }
            if (Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1) {
                throw HostNetworkException(HostNetworkError.OFFLINE)
            }
            if (
                request.url.path.startsWith("/book/") &&
                requireNextDetailVerification.compareAndSet(true, false)
            ) {
                return@HostHttpTransport fixtureResponse(context, request.url, "challenge.html")
            }
            if (chapterRequest && requireNextChapterVerification.compareAndSet(true, false)) {
                return@HostHttpTransport fixtureResponse(context, request.url, "challenge.html")
            }
            val fixture = when {
                request.url.path == "/modules/article/bookcase.php" && request.method == NetworkMethod.POST ->
                    "remote-add-applied.html"
                request.url.path == "/modules/article/bookcase.php" &&
                    request.url.rawQuery.orEmpty().contains("cursor=page-2") ->
                    "remote-library-page-2.html"
                request.url.path == "/modules/article/bookcase.php" -> "remote-library-page-1.html"
                request.url.rawQuery.orEmpty().contains("searchkey=login") -> "login.html"
                request.url.rawQuery.orEmpty().contains("searchkey=challenge") &&
                    (request.headers["cookie"].isNullOrBlank() ||
                        request.headers["User-Agent"] != WebSettings.getDefaultUserAgent(context)) -> "challenge.html"
                request.url.path == "/index.php" -> "home-index.html"
                request.url.path == "/zt/sugoi/2026.php" -> "home-sugoi-2026.html"
                request.url.path in setOf("/modules/article/toplist.php", "/modules/article/tags.php") -> "home.html"
                request.url.path.contains("search.php") -> "search.html"
                request.url.path == "/modules/article/articleinfo.php" -> "detail.html"
                request.url.path == "/modules/article/reader.php" &&
                    request.url.rawQuery.orEmpty().split('&').any { it == "cid=10002" } -> "chapter-2.html"
                request.url.path == "/modules/article/reader.php" &&
                    request.url.rawQuery.orEmpty().contains("cid=") -> "chapter.html"
                request.url.path == "/modules/article/reader.php" -> "directory.html"
                request.url.path.startsWith("/book/") -> "detail.html"
                request.url.path.endsWith("/index.htm") -> "directory.html"
                else -> "chapter.html"
            }
            fixtureResponse(context, request.url, fixture)
        }
        return SourceGatewayFactory.create(context, packageInfo, transport, directActionTokens)
    }

    fun createVerifiedPage(
        context: Context,
        packageInfo: VerifiedHxpPackage,
        snapshot: CapturedVerifiedPage,
        directActionTokens: DirectActionTokenRegistry,
    ): HostNetworkGateway = SourceGatewayFactory.createVerifiedPage(
        context = context,
        packageInfo = packageInfo,
        snapshot = snapshot,
        directActionTokens = directActionTokens,
    )

    private fun fixtureResponse(context: Context, url: URI, fixture: String): HostHttpResponse {
        val text = context.assets.open(fixture).bufferedReader().use { it.readText() }
        return HostHttpResponse(
            status = 200,
            finalUrl = url,
            headers = mapOf("content-type" to "text/html; charset=gb18030"),
            bytes = text.toByteArray(Charset.forName("GB18030")),
        )
    }

    private fun liveTransportMarker(context: Context) =
        context.noBackupFilesDir.resolve(LIVE_TRANSPORT_MARKER)

    private fun priorFailedSearchMarker(context: Context) =
        context.noBackupFilesDir.resolve(PRIOR_FAILED_SEARCH_MARKER)

    private const val LIVE_TRANSPORT_MARKER = "live-wenku8-transport.enabled"
    private const val PRIOR_FAILED_SEARCH_MARKER = "live-wenku8-prior-search.url"
}
