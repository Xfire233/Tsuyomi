/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkCacheMode
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.shared.sourcecontract.SourceCookieMode
import org.tsuyomi.shared.sourcecontract.SourceNetworkRequest

internal val addPolicy = RemoteOperationRequestPolicy(
    origin = HttpsOrigin("https://www.wenku8.net"),
    method = NetworkMethod.POST,
    path = "/remote/shelf",
    fixedParameters = mapOf("mode" to "add"),
    remoteBookIdParameter = "bid",
)

internal val grant = SourceNetworkGrant(
    sourceId = "org.tsuyomi.wenku8",
    extensionVersion = "0.1.0",
    origins = setOf(HttpsOrigin("https://www.wenku8.net")),
    cookieMode = SourceCookieMode.SOURCE_SCOPED,
    cookieOrigins = setOf(HttpsOrigin("https://www.wenku8.net")),
    maxConcurrentRequests = 2,
    requestTimeoutMs = 15_000,
    maxResponseBytes = 1_024,
    remoteAddPolicy = addPolicy,
)

internal fun request(
    url: String = "https://www.wenku8.net/book/1234.htm",
    headers: Map<String, String> = emptyMap(),
    decode: DecodeMode = DecodeMode.UTF8,
    cache: NetworkCacheMode = NetworkCacheMode.NETWORK_ONLY,
    semanticCacheKey: String? = null,
) = SourceNetworkRequest(
    url = url,
    method = NetworkMethod.GET,
    headers = headers,
    decode = decode,
    cache = cache,
    semanticCacheKey = semanticCacheKey,
)

internal fun addRequest(form: Map<String, String> = mapOf("mode" to "add", "bid" to "42")) = SourceNetworkRequest(
    url = "https://www.wenku8.net/remote/shelf",
    method = NetworkMethod.POST,
    form = form,
    decode = DecodeMode.UTF8,
    cache = NetworkCacheMode.NETWORK_ONLY,
)

internal suspend fun assertHostFailure(action: suspend () -> Unit): HostNetworkException = try {
    action()
    throw AssertionError("Expected HostNetworkException")
} catch (error: HostNetworkException) {
    error
}

internal class RecordingTransport : HostHttpTransport {
    val requests = mutableListOf<HostHttpRequest>()

    override suspend fun execute(request: HostHttpRequest): HostHttpResponse {
        requests += request
        return HostHttpResponse(
            status = 200,
            finalUrl = request.url,
            headers = mapOf("content-type" to "text/html; charset=utf-8", "set-cookie" to "hidden"),
            bytes = "fixture".encodeToByteArray(),
        )
    }
}
