/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.content.Context
import android.provider.Settings
import java.nio.charset.Charset
import org.tsuyomi.core.network.HostHttpResponse
import org.tsuyomi.core.network.HostHttpTransport
import org.tsuyomi.core.network.HostNetworkGateway
import org.tsuyomi.core.network.HostNetworkError
import org.tsuyomi.core.network.HostNetworkException
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage

/** Debug acceptance is deterministic: signed extension code parses sanitized fixture transport. */
internal object Gate2SourceGateway {
    fun create(context: Context, packageInfo: VerifiedHxpPackage): HostNetworkGateway {
        val transport = HostHttpTransport { request ->
            if (Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1) {
                throw HostNetworkException(HostNetworkError.OFFLINE)
            }
            val fixture = when {
                request.url.path == "/modules/article/bookcase.php" && request.method == NetworkMethod.POST ->
                    "remote-add-applied.html"
                request.url.path == "/modules/article/bookcase.php" &&
                    request.url.rawQuery.orEmpty().contains("cursor=page-2") ->
                    "remote-library-page-2.html"
                request.url.path == "/modules/article/bookcase.php" -> "remote-library-page-1.html"
                request.url.rawQuery.orEmpty().contains("searchkey=login") -> "login.html"
                request.url.rawQuery.orEmpty().contains("searchkey=challenge") && request.headers["cookie"].isNullOrBlank() -> "challenge.html"
                request.url.path.contains("search.php") -> "search.html"
                request.url.path.startsWith("/book/") -> "detail.html"
                request.url.path.endsWith("/index.htm") -> "directory.html"
                else -> "chapter.html"
            }
            val text = context.assets.open(fixture).bufferedReader().use { it.readText() }
            HostHttpResponse(
                status = 200,
                finalUrl = request.url,
                headers = mapOf("content-type" to "text/html; charset=gb18030"),
                bytes = text.toByteArray(Charset.forName("GB18030")),
            )
        }
        return SourceGatewayFactory.create(context, packageInfo, transport)
    }
}
