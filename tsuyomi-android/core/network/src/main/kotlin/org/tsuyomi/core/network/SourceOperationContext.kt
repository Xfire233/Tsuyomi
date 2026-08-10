/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import java.net.URI
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.shared.sourcecontract.SourceNetworkRequest

/** Host-minted policy for one remote-library transport operation. */
enum class SourceOperationKind { REMOTE_LIBRARY_READ, REMOTE_LIBRARY_ADD }

data class RemoteOperationRequestPolicy(
    val origin: HttpsOrigin,
    val method: NetworkMethod,
    val path: String,
    val fixedParameters: Map<String, String>,
    val remoteBookIdParameter: String? = null,
    val cursorParameter: String? = null,
    val referrerPath: String? = null,
) {
    init {
        require(path.startsWith('/') && '?' !in path && '#' !in path)
        require(remoteBookIdParameter == null || remoteBookIdParameter !in fixedParameters)
        require(cursorParameter == null || (cursorParameter !in fixedParameters && cursorParameter != remoteBookIdParameter))
        require(fixedParameters.keys.all { it.isNotBlank() })
        require(referrerPath == null || (referrerPath.startsWith('/') && '?' !in referrerPath && '#' !in referrerPath))
    }
}

/**
 * Only host code may create this after resolving immutable manifest policy and direct user intent.
 * [cursor] is null on the first page and becomes the opaque host-observed cursor thereafter.
 */
class SourceOperationContext internal constructor(
    val kind: SourceOperationKind,
    val policy: RemoteOperationRequestPolicy,
    val cursor: String? = null,
    val remoteBookId: String? = null,
    val addToken: String? = null,
) {
    init {
        require(kind != SourceOperationKind.REMOTE_LIBRARY_ADD || !addToken.isNullOrBlank())
        require(kind != SourceOperationKind.REMOTE_LIBRARY_ADD || !remoteBookId.isNullOrBlank())
        require(kind != SourceOperationKind.REMOTE_LIBRARY_ADD || cursor == null)
        require(kind != SourceOperationKind.REMOTE_LIBRARY_READ || remoteBookId == null)
        require(cursor == null || cursor.isNotBlank())
    }

    internal fun validate(request: SourceNetworkRequest) {
        if (request.method != policy.method || request.utf8Body != null) throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
        val uri = runCatching { URI(request.url) }.getOrElse { throw HostNetworkException(HostNetworkError.INVALID_REQUEST) }
        if (uri.scheme != "https" || uri.host == null || uri.path != policy.path || uri.fragment != null) {
            throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
        }
        val origin = HttpsOrigin("https://${uri.host}${if (uri.port in 1..65535 && uri.port != 443) ":${uri.port}" else ""}")
        if (origin.canonical != policy.origin.canonical) throw HostNetworkException(HostNetworkError.DISALLOWED_ORIGIN)
        val expected = buildMap {
            putAll(policy.fixedParameters)
            policy.cursorParameter?.let { name -> cursor?.let { put(name, it) } }
            policy.remoteBookIdParameter?.let { name -> remoteBookId?.let { put(name, it) } }
        }
        val actual = when (request.method) {
            NetworkMethod.GET, NetworkMethod.HEAD -> decodeQuery(uri.rawQuery)
            NetworkMethod.POST -> request.form ?: emptyMap()
        }
        if (actual != expected) throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
        val expectedReferrer = policy.referrerPath?.let { URI(policy.origin.canonical + it).toString() }
        if (request.referrerUrl != expectedReferrer) throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
    }

    private fun decodeQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        val values = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { pair ->
            val equals = pair.indexOf('=')
            if (equals <= 0) throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
            val name = java.net.URLDecoder.decode(pair.substring(0, equals), Charsets.UTF_8.name())
            val value = java.net.URLDecoder.decode(pair.substring(equals + 1), Charsets.UTF_8.name())
            if (values.put(name, value) != null) throw HostNetworkException(HostNetworkError.INVALID_REQUEST)
        }
        return values
    }
}

fun remoteLibraryReadContext(policy: RemoteOperationRequestPolicy, cursor: String?): SourceOperationContext =
    SourceOperationContext(SourceOperationKind.REMOTE_LIBRARY_READ, policy, cursor = cursor)

fun remoteLibraryAddContext(
    policy: RemoteOperationRequestPolicy,
    remoteBookId: String,
    addToken: String,
): SourceOperationContext = SourceOperationContext(
    SourceOperationKind.REMOTE_LIBRARY_ADD,
    policy,
    remoteBookId = remoteBookId,
    addToken = addToken,
)
