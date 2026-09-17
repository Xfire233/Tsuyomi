/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import android.util.Log
import java.io.Closeable
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.tsuyomi.core.network.HostNetworkError
import org.tsuyomi.core.network.HostNetworkException
import org.tsuyomi.core.network.HostNetworkGateway
import org.tsuyomi.core.network.SourceNetworkGrant
import org.tsuyomi.core.network.RemoteOperationRequestPolicy
import org.tsuyomi.core.network.RemoteOperationRedirectPolicy
import org.tsuyomi.core.network.SourceOperationContext
import org.tsuyomi.core.network.remoteLibraryAddContext
import org.tsuyomi.core.network.remoteLibraryReadContext
import org.tsuyomi.core.network.remoteLibraryTargetsContext
import org.tsuyomi.core.network.remoteLibraryRemoveContext
import org.tsuyomi.core.network.remoteLibraryMoveContext
import org.tsuyomi.core.network.updateCheckContext
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.NetworkCacheMode
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddOutcome
import org.tsuyomi.shared.sourcecontract.RemoteLibraryAddResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryRemoveOutcome
import org.tsuyomi.shared.sourcecontract.RemoteLibraryRemoveResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryMoveOutcome
import org.tsuyomi.shared.sourcecontract.RemoteLibraryMoveResult
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.shared.sourcecontract.RemoteLibraryTargetsResult
import org.tsuyomi.shared.sourcecontract.RemoteLibraryPage
import org.tsuyomi.shared.sourcecontract.SourceHomeFilter
import org.tsuyomi.shared.sourcecontract.SourceHomeFeature
import org.tsuyomi.shared.sourcecontract.SourceHomeFilterOption
import org.tsuyomi.shared.sourcecontract.SourceHomePage
import org.tsuyomi.shared.sourcecontract.SourceHomeSection
import org.tsuyomi.shared.sourcecontract.SourceChapter
import org.tsuyomi.shared.sourcecontract.SourceDiagnostic
import org.tsuyomi.shared.sourcecontract.SourceDirectory
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.shared.sourcecontract.SourceNetworkRequest
import org.tsuyomi.shared.sourcecontract.SourceNetworkResponse
import org.tsuyomi.shared.sourcecontract.SourceCookieMode
import org.tsuyomi.shared.sourcecontract.SourceUpdateChapter
import org.tsuyomi.shared.sourcecontract.SourceUpdateOutcome
import org.tsuyomi.shared.sourcecontract.SourceUpdateProbeResult
import org.tsuyomi.source.quickjsruntime.QuickJsRuntimeError
import org.tsuyomi.source.quickjsruntime.QuickJsRuntimeException
import org.tsuyomi.source.quickjsruntime.QuickJsRuntimeLane
import org.tsuyomi.source.quickjsruntime.QuickJsRuntimeLimits

/**
 * Host-owned orchestration for one verified extension version. JavaScript builds requests and parses
 * bounded text; Android validates transport and converts JSON into strict protocol DTOs.
 */
class SourceExtensionClient private constructor(
    private val packageInfo: VerifiedHxpPackage,
    private val gateway: HostNetworkGateway,
    private val runtime: QuickJsRuntimeLane,
    private val verifiedGet: HostNetworkGateway? = null,
) : Closeable {
    private val manifest = packageInfo.manifest
    private val grant = SourceNetworkGrant(
        sourceId = manifest.sourceId.value,
        extensionVersion = manifest.version.original,
        origins = manifest.capabilities.network.origins,
        maxConcurrentRequests = manifest.capabilities.network.maxConcurrentRequests,
        requestTimeoutMs = manifest.capabilities.network.requestTimeoutMs,
        cookieMode = if (manifest.capabilities.cookies.sourceScoped) {
            SourceCookieMode.SOURCE_SCOPED
        } else {
            SourceCookieMode.NONE
        },
        cookieOrigins = manifest.capabilities.cookies.origins,
        maxResponseBytes = manifest.capabilities.network.maxResponseBytes,
        remoteReadPolicy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.READ]?.toNetworkPolicy(),
        remoteTargetsPolicy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.TARGETS]?.toNetworkPolicy(),
        remoteAddPolicy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.ADD]?.toNetworkPolicy(),
        remoteRemovePolicy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.REMOVE]?.toNetworkPolicy(),
        remoteMovePolicy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.MOVE]?.toNetworkPolicy(),
        updateCheckPolicy = manifest.capabilities.updateCheck?.policy?.toNetworkPolicy(),
    )

    val supportsUpdateChecks: Boolean
        get() = manifest.capabilities.updateCheck?.version == 2

    suspend fun searchRequestUrl(query: String, page: Int = 1): String =
        requestUrl("buildSearchRequest", arrayOf<Any?>(query, page), "search-network")

    suspend fun authorSearchRequestUrl(author: String, page: Int = 1): String =
        requestUrl("buildAuthorSearchRequest", arrayOf<Any?>(author, page), "author-search-network")

    suspend fun detailRequestUrl(remoteBookId: String): String =
        requestUrl("buildDetailRequest", arrayOf<Any?>(remoteBookId), "detail-network")

    suspend fun directoryRequestUrl(remoteBookId: String): String =
        requestUrl("buildDirectoryRequest", arrayOf<Any?>(remoteBookId), "directory-network")
    suspend fun chapterRequestUrl(chapter: SourceChapter, remoteBookId: String): String =
        requestUrl(
            "buildChapterRequest",
            arrayOf<Any?>(chapter.url, remoteBookId, chapter.chapterId),
            "chapter-network",
        )


    suspend fun search(query: String, page: Int = 1, offlineOnly: Boolean = false): List<SourceBookSummary> {
        val response = invokeClassified(
            "buildSearchRequest",
            arrayOf<Any?>(query, page),
            "search-network",
            "search-classify",
            "search",
            offlineOnly,
        )
        return malformedResponse("search-parse", "invalid-search-results") {
            val root = call(
                "parseSearch",
                arrayOf<Any?>(response.text.orEmpty(), response.finalUrl),
                "search-parse",
            ).jsonObject
            root.requiredArray("items").map { parseSummary(it.jsonObject) }
        }
    }

    suspend fun authorSearch(author: String, page: Int = 1, offlineOnly: Boolean = false): List<SourceBookSummary> {
        val response = invokeClassified(
            "buildAuthorSearchRequest",
            arrayOf<Any?>(author, page),
            "author-search-network",
            "author-search-classify",
            "search",
            offlineOnly,
        )
        return malformedResponse("author-search-parse", "invalid-search-results") {
            val root = call(
                "parseSearch",
                arrayOf<Any?>(response.text.orEmpty(), response.finalUrl),
                "author-search-parse",
            ).jsonObject
            root.requiredArray("items").map { parseSummary(it.jsonObject) }
        }
    }
    suspend fun homeRequestUrl(
        selectedFilters: Map<String, String> = emptyMap(),
        cursor: String? = null,
    ): String = requestUrl(
        "buildHomeRequest",
        arrayOf<Any?>(cursor, selectedFilters),
        "home-network",
    )

    suspend fun home(
        selectedFilters: Map<String, String> = emptyMap(),
        cursor: String? = null,
        offlineOnly: Boolean = false,
    ): SourceHomePage {
        if (!manifest.capabilities.home.enabled) {
            fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "home", "home-not-granted")
        }
        if (selectedFilters.size > 16 || selectedFilters.any { (key, value) ->
                !key.matches(Regex("^[A-Za-z0-9._-]{1,64}$")) ||
                    !value.matches(Regex("^[A-Za-z0-9._-]{1,64}$"))
            }
        ) {
            fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "home", "invalid-home-filters")
        }
        val arguments = arrayOf<Any?>(cursor, selectedFilters)
        val response = invokeClassified(
            "buildHomeRequest",
            arguments,
            "home-network",
            "home-classify",
            "home",
            offlineOnly,
        )
        return malformedResponse("home-parse", "invalid-home-page") {
            parseHomePage(
                call(
                    "parseHome",
                    arrayOf<Any?>(response.text.orEmpty(), cursor, selectedFilters),
                    "home-parse",
                ).jsonObject,
            )
        }
    }


    suspend fun detail(remoteBookId: String, offlineOnly: Boolean = false): SourceBookDetail {
        val response = invokeClassified(
            "buildDetailRequest",
            arrayOf<Any?>(remoteBookId),
            "detail-network",
            "detail-classify",
            "detail",
            offlineOnly,
            remoteBookId = remoteBookId,
        )
        return malformedResponse("detail-parse", "invalid-detail") {
            parseDetail(call("parseDetail", arrayOf<Any?>(response.text.orEmpty(), remoteBookId), "detail-parse").jsonObject)
        }
    }

    suspend fun directory(remoteBookId: String, offlineOnly: Boolean = false): SourceDirectory {
        val response = invokeClassified(
            "buildDirectoryRequest",
            arrayOf<Any?>(remoteBookId),
            "directory-network",
            "directory-classify",
            "directory",
            offlineOnly,
            remoteBookId = remoteBookId,
        )
        return malformedResponse("directory-parse", "invalid-directory") {
            val root = call("parseDirectory", arrayOf<Any?>(response.text.orEmpty(), remoteBookId), "directory-parse").jsonObject
            val identity = BookIdentity(root.requiredString("sourceId"), root.requiredString("remoteBookId"))
            val chapters = root.requiredArray("chapters").map { chapter ->
                val value = chapter.jsonObject
                SourceChapter(
                    chapterId = value.requiredString("chapterId"),
                    title = value.requiredString("title"),
                    url = value.requiredString("url"),
                    volumeTitle = value.optionalString("volumeTitle"),
                )
            }
            SourceDirectory(identity, chapters)
        }
    }

    suspend fun checkUpdates(remoteBookId: String, previousAnchor: String?): SourceUpdateProbeResult {
        val identity = BookIdentity(manifest.sourceId.value, remoteBookId)
        val checkedAt = System.currentTimeMillis()
        if (!supportsUpdateChecks) {
            return updateProbeResult(
                identity = identity,
                checkedAt = checkedAt,
                previousAnchor = previousAnchor,
                outcome = SourceUpdateOutcome.UNAVAILABLE,
                reason = "update-check-not-granted",
            )
        }
        val policy = requireNotNull(manifest.capabilities.updateCheck).policy.toNetworkPolicy()
        return try {
            val response = invokeClassified(
                function = "buildUpdateCheckV2Request",
                arguments = arrayOf(remoteBookId),
                networkStage = "update-check-network",
                classifyStage = "update-check-classify",
                operation = "update-check",
                offlineOnly = false,
                remoteBookId = remoteBookId,
                operationContext = updateCheckContext(policy, remoteBookId),
                allowOfflineFallback = false,
            )
            val parsed = malformedResponse("update-check-parse", "invalid-update-check") {
                parseUpdateCheck(
                    call(
                        "parseUpdateCheckV2",
                        arrayOf(response.text.orEmpty(), remoteBookId),
                        "update-check-parse",
                    ).jsonObject,
                )
            }
            val admitted = admitSourceUpdateCheck(identity, previousAnchor, parsed)
            updateProbeResult(
                identity = identity,
                checkedAt = checkedAt,
                previousAnchor = previousAnchor,
                outcome = admitted.outcome,
                anchor = admitted.anchor,
                chapters = admitted.chapters,
                newChapterIds = admitted.newChapterIds,
                lastUpdatedDate = admitted.lastUpdatedDate,
                reason = admitted.reason,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: SourceException) {
            if (error.code == SourceErrorCode.EXTENSION_CANCELLED) {
                throw CancellationException("Source update check cancelled").apply { initCause(error) }
            }
            val unavailable = error.code in setOf(SourceErrorCode.SESSION_REQUIRED, SourceErrorCode.VERIFICATION_REQUIRED)
            updateProbeResult(
                identity = identity,
                checkedAt = checkedAt,
                previousAnchor = previousAnchor,
                outcome = if (unavailable) SourceUpdateOutcome.UNAVAILABLE else SourceUpdateOutcome.FAILED,
                reason = "source-${error.code.name.lowercase().replace('_', '-')}" +
                    ".${error.diagnostic.stage.takeIf(UPDATE_DIAGNOSTIC_STAGE::matches) ?: "unknown"}" +
                    ".${error.diagnostic.safeCode.takeIf(UPDATE_DIAGNOSTIC_CODE::matches) ?: "unknown"}",
            )
        } catch (error: SecurityException) {
            throw error
        } catch (error: Error) {
            throw error
        } catch (_: Throwable) {
            updateProbeResult(
                identity = identity,
                checkedAt = checkedAt,
                previousAnchor = previousAnchor,
                outcome = SourceUpdateOutcome.FAILED,
                reason = "update-check-invalid-result",
            )
        }
    }
    suspend fun chapter(
        chapter: SourceChapter,
        remoteBookId: String,
        offlineOnly: Boolean = false,
    ): ReaderDocument {
        val response = invokeClassified(
            "buildChapterRequest",
            arrayOf<Any?>(chapter.url, remoteBookId, chapter.chapterId),
            "chapter-network",
            "chapter-classify",
            "chapter",
            offlineOnly,
            remoteBookId = remoteBookId,
            chapterId = chapter.chapterId,
        )
        return malformedResponse("chapter-parse", "invalid-document") {
            parseDocument(
                call(
                    "parseChapter",
                    arrayOf<Any?>(response.text.orEmpty(), remoteBookId, chapter.chapterId, chapter.title),
                    "chapter-parse",
                ).jsonObject,
            )
        }
    }

    suspend fun listRemoteLibrary(cursor: String?): RemoteLibraryPage {
        val policy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.READ]
            ?: fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-read", "remote-read-not-granted")
        val response = invokeClassified(
            "buildRemoteLibraryRequest",
            arrayOf<Any?>(cursor),
            "remote-library-read-network",
            "remote-library-read-classify",
            "remote-library",
            offlineOnly = false,
            operationContext = remoteLibraryReadContext(policy.toNetworkPolicy(), cursor),
        )
        return malformedResponse("remote-library-read-parse", "invalid-page") {
            val root = call("parseRemoteLibrary", arrayOf<Any?>(response.text.orEmpty()), "remote-library-read-parse").jsonObject
            val items = root.requiredArray("items").map { parseSummary(it.jsonObject) }
            val nextCursor = root.optionalString("nextCursor")
            val complete = root["complete"]?.jsonPrimitive?.booleanOrNull
                ?: fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-read-parse", "missing-complete")
            RemoteLibraryPage(items, nextCursor, complete)
        }
    }

    suspend fun addRemoteLibrary(remoteBookId: String, directActionToken: String): RemoteLibraryAddResult {
        val policy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.ADD]
            ?: fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-add", "remote-add-not-granted")
        val response = invokeNetwork(
            "buildRemoteLibraryAddRequest",
            arrayOf<Any?>(remoteBookId),
            "remote-library-add-network",
            offlineOnly = false,
            operationContext = remoteLibraryAddContext(policy.toNetworkPolicy(), remoteBookId, directActionToken),
        )
        classify(response, "remote-library-add-classify")
        return malformedResponse("remote-library-add-parse", "invalid-result") {
            val root = call(
                "parseRemoteLibraryAdd",
                arrayOf<Any?>(response.text.orEmpty(), remoteBookId, response.finalUrl),
                "remote-library-add-parse",
            ).jsonObject
            val identity = BookIdentity(root.requiredString("sourceId"), root.requiredString("remoteBookId"))
            if (identity.sourceId != manifest.sourceId.value || identity.remoteBookId != remoteBookId) {
                fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-add-parse", "identity-mismatch")
            }
            val outcome = when (root.requiredString("outcome")) {
                "applied" -> RemoteLibraryAddOutcome.APPLIED
                "already-present" -> RemoteLibraryAddOutcome.ALREADY_PRESENT
                else -> fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-add-parse", "invalid-outcome")
            }
            RemoteLibraryAddResult(identity, outcome)
        }
    }

    suspend fun removeRemoteLibrary(remoteBookId: String, directActionToken: String): RemoteLibraryRemoveResult {
        val policy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.REMOVE]
            ?: fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-remove", "remote-remove-not-granted")
        val response = invokeNetwork(
            "buildRemoteLibraryRemoveRequest",
            arrayOf<Any?>(remoteBookId),
            "remote-library-remove-network",
            offlineOnly = false,
            operationContext = remoteLibraryRemoveContext(policy.toNetworkPolicy(), remoteBookId, directActionToken),
        )
        classify(response, "remote-library-remove-classify")
        return malformedResponse("remote-library-remove-parse", "invalid-result") {
            val root = call("parseRemoteLibraryRemove", arrayOf<Any?>(response.text.orEmpty(), remoteBookId), "remote-library-remove-parse").jsonObject
            val identity = BookIdentity(root.requiredString("sourceId"), root.requiredString("remoteBookId"))
            if (identity.sourceId != manifest.sourceId.value || identity.remoteBookId != remoteBookId) {
                fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-remove-parse", "identity-mismatch")
            }
            val outcome = when (root.requiredString("outcome")) {
                "applied" -> RemoteLibraryRemoveOutcome.APPLIED
                "already-absent" -> RemoteLibraryRemoveOutcome.ALREADY_ABSENT
                else -> fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-remove-parse", "invalid-outcome")
            }
            RemoteLibraryRemoveResult(identity, outcome)
        }
    }

    suspend fun moveRemoteLibrary(remoteBookId: String, targetId: String, directActionToken: String): RemoteLibraryMoveResult {
        val policy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.MOVE]
            ?: fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-move", "remote-move-not-granted")
        val response = invokeNetwork(
            "buildRemoteLibraryMoveRequest",
            arrayOf<Any?>(remoteBookId, targetId),
            "remote-library-move-network",
            offlineOnly = false,
            operationContext = remoteLibraryMoveContext(policy.toNetworkPolicy(), remoteBookId, targetId, directActionToken),
        )
        classify(response, "remote-library-move-classify")
        return malformedResponse("remote-library-move-parse", "invalid-result") {
            val root = call("parseRemoteLibraryMove", arrayOf<Any?>(response.text.orEmpty(), remoteBookId, targetId), "remote-library-move-parse").jsonObject
            val identity = BookIdentity(root.requiredString("sourceId"), root.requiredString("remoteBookId"))
            val returnedTargetId = root.requiredString("targetId")
            if (identity.sourceId != manifest.sourceId.value || identity.remoteBookId != remoteBookId || returnedTargetId != targetId) {
                fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-move-parse", "identity-mismatch")
            }
            val outcome = when (root.requiredString("outcome")) {
                "applied" -> RemoteLibraryMoveOutcome.APPLIED
                "already-at-target" -> RemoteLibraryMoveOutcome.ALREADY_AT_TARGET
                else -> fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-move-parse", "invalid-outcome")
            }
            RemoteLibraryMoveResult(identity, targetId, outcome)
        }
    }

    suspend fun listRemoteTargets(): RemoteLibraryTargetsResult {
        val policy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.TARGETS]
            ?: fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-targets", "remote-targets-not-granted")
        val response = invokeClassified(
            "buildRemoteLibraryTargetsRequest",
            emptyArray(),
            "remote-library-targets-network",
            "remote-library-targets-classify",
            "generic",
            offlineOnly = false,
            operationContext = remoteLibraryTargetsContext(policy.toNetworkPolicy()),
        )
        return malformedResponse("remote-library-targets-parse", "invalid-targets") {
            decodeRemoteTargets(
                call(
                    "parseRemoteLibraryTargets",
                    arrayOf<Any?>(response.text.orEmpty()),
                    "remote-library-targets-parse",
                ).jsonObject,
                manifest.sourceId.value,
            )
        }
    }

    private fun updateProbeResult(
        identity: BookIdentity,
        checkedAt: Long,
        previousAnchor: String?,
        outcome: SourceUpdateOutcome,
        anchor: String? = null,
        chapters: List<SourceUpdateChapter> = emptyList(),
        newChapterIds: List<String> = emptyList(),
        lastUpdatedDate: String? = null,
        reason: String?,
    ) = SourceUpdateProbeResult(
        identity = identity,
        sourceVersion = manifest.version.original,
        packageSha256 = packageInfo.packageSha256,
        checkedAt = checkedAt,
        outcome = outcome,
        previousAnchor = previousAnchor?.takeIf(OPAQUE_UPDATE_ANCHOR::matches),
        anchor = anchor,
        chapters = chapters,
        newChapterIds = newChapterIds,
        lastUpdatedDate = lastUpdatedDate,
        reason = reason,
    )

    private suspend fun invokeNetwork(
        function: String,
        arguments: Array<out Any?>,
        stage: String,
        offlineOnly: Boolean,
        operationContext: SourceOperationContext? = null,
    ): SourceNetworkResponse {
        val request = buildNetworkRequest(function, arguments, stage, offlineOnly)
        return try {
            gateway.request(grant, request, operationContext)
        } catch (error: HostNetworkException) {
            fail(mapNetworkError(error.error), stage, error.error.name.lowercase(), error.diagnosticId)
        }
    }

    private suspend fun invokeClassified(
        function: String,
        arguments: Array<out Any?>,
        networkStage: String,
        classifyStage: String,
        operation: String,
        offlineOnly: Boolean,
        remoteBookId: String? = null,
        chapterId: String? = null,
        operationContext: SourceOperationContext? = null,
        allowOfflineFallback: Boolean = true,
    ): SourceNetworkResponse {
        val request = buildNetworkRequest(function, arguments, networkStage, offlineOnly)
        val response = try {
            gateway.request(grant, request, operationContext)
        } catch (error: HostNetworkException) {
            fail(mapNetworkError(error.error), networkStage, error.error.name.lowercase(), error.diagnosticId)
        }
        try {
            classify(response, classifyStage, operation, remoteBookId, chapterId)
            if (!offlineOnly) gateway.rememberLastGood(grant, request, response)
            return response
        } catch (error: SourceException) {
            if (
                offlineOnly ||
                error.code != SourceErrorCode.SESSION_REQUIRED &&
                error.code != SourceErrorCode.VERIFICATION_REQUIRED
            ) {
                throw error
            }
            val retried = if (verifiedGet == null) {
                Log.w("TsuyomiWebView", "fallback-unavailable reason=no-verified-transport")
                null
            } else {
                try {
                    verifiedGet.request(grant, request.copy(cache = NetworkCacheMode.NETWORK_ONLY), operationContext)
                } catch (failure: HostNetworkException) {
                    Log.w("TsuyomiWebView", "fallback-failed code=${failure.error}")
                    null
                }
            }
            if (retried != null) {
                try {
                    classify(retried, classifyStage, operation, remoteBookId, chapterId)
                    gateway.rememberLastGood(grant, request, retried)
                    return retried
                } catch (_: SourceException) {
                    Unit
                }
            }
            if (!allowOfflineFallback) {
                throw error
            }
            val cached = try {
                gateway.request(grant, request.copy(cache = NetworkCacheMode.OFFLINE_ONLY), operationContext)
            } catch (_: HostNetworkException) {
                throw error
            }
            try {
                classify(cached, classifyStage, operation, remoteBookId, chapterId)
                return cached
            } catch (_: SourceException) {
                gateway.forgetLastGood(grant, request)
                throw error
            }
        }
    }

    private suspend fun requestUrl(
        function: String,
        arguments: Array<out Any?>,
        stage: String,
    ): String {
        val request = buildNetworkRequest(function, arguments, stage, offlineOnly = false)
        if (request.method != NetworkMethod.GET || request.form != null || request.utf8Body != null) {
            fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "$stage-request", "verified-page-request-not-get")
        }
        return request.url
    }

    private suspend fun buildNetworkRequest(
        function: String,
        arguments: Array<out Any?>,
        stage: String,
        offlineOnly: Boolean,
    ): SourceNetworkRequest = try {
        parseRequest(call(function, arguments, "$stage-request").jsonObject).let { built ->
            if (offlineOnly) built.copy(cache = NetworkCacheMode.OFFLINE_ONLY) else built
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: SourceException) {
        throw error
    } catch (error: SecurityException) {
        throw error
    } catch (error: Error) {
        throw error
    } catch (_: Throwable) {
        fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "$stage-request", "invalid-request-dto")
    }

    private suspend fun <T> malformedResponse(
        stage: String,
        safeCode: String,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: SourceException) {
        throw error
    } catch (error: SecurityException) {
        throw error
    } catch (error: Error) {
        throw error
    } catch (_: Throwable) {
        fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, stage, safeCode)
    }

    private suspend fun classify(
        response: SourceNetworkResponse,
        stage: String,
        operation: String = "generic",
        remoteBookId: String? = null,
        chapterId: String? = null,
    ) {
        val arguments = listOf<Any?>(response.text.orEmpty(), response.finalUrl, operation, remoteBookId, chapterId).toTypedArray()
        val classification = malformedResponse(stage, "invalid-page-classification") {
            call("classifyPage", arguments, stage).jsonPrimitive.content
        }
        when (classification) {
            "ok" -> Unit
            "session-required" -> fail(SourceErrorCode.SESSION_REQUIRED, stage, "session-required")
            "verification-required" -> fail(SourceErrorCode.VERIFICATION_REQUIRED, stage, "verification-required")
            "malformed" -> fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, stage, "wrong-page")
            else -> fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, stage, "invalid-page-classification")
        }
    }

    private suspend fun call(function: String, arguments: Array<out Any?>, stage: String): JsonElement {
        val encoded = JsonArray(arguments.map(::jsonValue)).toString()
        val result = try {
            runtime.callJson(function, encoded)
        } catch (error: QuickJsRuntimeException) {
            if (error.error == QuickJsRuntimeError.CANCELLED) {
                throw CancellationException("Source operation cancelled").apply { initCause(error) }
            }
            val code = when (error.error) {
                QuickJsRuntimeError.EXECUTION_LIMIT -> SourceErrorCode.EXTENSION_TIMEOUT
                else -> SourceErrorCode.EXTENSION_RUNTIME_FAILURE
            }
            fail(code, stage, error.error.name.lowercase())
        }
        return malformedResponse(stage, "invalid-json-result") {
            JSON.parseToJsonElement(result)
        }
    }

    override fun close() = runtime.close()

    companion object {
        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }
        // Only bounded host tokens enter the durable report; never exception messages or source payloads.
        private val UPDATE_DIAGNOSTIC_STAGE = Regex("^[a-z][a-z0-9_-]{0,31}$")
        private val UPDATE_DIAGNOSTIC_CODE = Regex("^[a-z][a-z0-9_-]{0,47}$")

        suspend fun open(
            packageInfo: VerifiedHxpPackage,
            gateway: HostNetworkGateway,
            verifiedGet: HostNetworkGateway? = null,
        ): SourceExtensionClient {
            val manifest = packageInfo.manifest
            val runtime = QuickJsRuntimeLane(
                label = "${manifest.sourceId.value}-${manifest.version.original}",
                limits = QuickJsRuntimeLimits(
                    maxMemoryBytes = manifest.resourceLimits.maxMemoryBytes.toLong(),
                    maxExecutionWallTimeMs = manifest.resourceLimits.maxExecutionWallTimeMs,
                ),
            )
            return try {
                runtime.evaluateModule(packageInfo.readVerifiedEntryModule(), manifest.entry)
                SourceExtensionClient(packageInfo, gateway, runtime, verifiedGet)
            } catch (failure: Throwable) {
                runtime.close()
                throw failure
            }
        }
    }
}


private fun parseRequest(value: JsonObject): SourceNetworkRequest {
    val baseUrl = value.requiredString("url")
    val query = value["query"]
        ?.takeUnless { it is JsonNull }
        ?.jsonArray
        ?.map { parameter ->
            parameter.jsonObject.let { it.requiredString("name") to it.requiredString("value") }
        }
    val queryEncoding = value.optionalString("queryEncoding")
        ?.let { wireValue -> DecodeMode.entries.single { it.wireValue == wireValue } }
    require((query == null) == (queryEncoding == null)) { "Query and query encoding must be specified together" }
    val url = if (query == null) {
        baseUrl
    } else {
        encodeUrlQuery(baseUrl, query, requireNotNull(queryEncoding))
    }
    return SourceNetworkRequest(
        url = url,
        method = NetworkMethod.valueOf(value.requiredString("method")),
        headers = value["headers"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
        form = value["form"]?.takeUnless { it is JsonNull }?.jsonObject?.mapValues { it.value.jsonPrimitive.content },
        utf8Body = value.optionalString("utf8Body"),
        decode = DecodeMode.entries.single { it.wireValue == value.requiredString("decode") },
        cache = NetworkCacheMode.entries.single { it.wireValue == value.requiredString("cache") },
        semanticCacheKey = value.optionalString("semanticCacheKey"),
        referrerUrl = value.optionalString("referrerUrl"),
    )
}

internal fun encodeUrlQuery(
    baseUrl: String,
    query: List<Pair<String, String>>,
    encoding: DecodeMode,
): String {
    require(query.isNotEmpty() && query.size <= 64) { "Query is invalid" }
    val uri = URI(baseUrl)
    require(uri.rawQuery == null && uri.rawFragment == null) { "Structured query requires a query-free URL" }
    val charset = when (encoding) {
        DecodeMode.AUTO -> throw IllegalArgumentException("Query encoding must be explicit")
        DecodeMode.UTF8 -> StandardCharsets.UTF_8
        DecodeMode.GB18030 -> Charset.forName("GB18030")
        DecodeMode.BIG5_HKSCS -> Charset.forName("Big5-HKSCS")
    }
    val encoded = query.joinToString("&") { (name, value) ->
        require(name.isNotEmpty() && name.length <= 256 && value.length <= 2048) { "Query parameter is invalid" }
        "${URLEncoder.encode(name, charset.name())}=${URLEncoder.encode(value, charset.name())}"
    }
    return "${uri.toASCIIString()}?$encoded"
}

private fun parseSummary(value: JsonObject): SourceBookSummary = SourceBookSummary(
    identity = BookIdentity(value.requiredString("sourceId"), value.requiredString("remoteBookId")),
    title = value.requiredString("title"),
    author = value.optionalString("author"),
    coverUrl = value.optionalString("coverUrl"),
    canonicalUrl = value.requiredString("canonicalUrl"),
    remoteTargetId = value.optionalString("remoteTargetId"),
)

private fun parseDetail(value: JsonObject): SourceBookDetail = SourceBookDetail(
    summary = parseSummary(value.requiredObject("summary")),
    description = value.optionalString("description"),
    tags = value.requiredArray("tags").map { it.jsonPrimitive.content },
    status = value.optionalString("status"),
    lastUpdatedDate = value.optionalString("lastUpdatedDate"),
)

private const val MAX_UPDATE_CHECK_CHAPTERS = 20_000
private val OPAQUE_UPDATE_ANCHOR = Regex("^[A-Za-z0-9._:-]{1,128}$")

private fun parseUpdateCheck(value: JsonObject): ParsedSourceUpdateCheck {
    value.requireExactKeys("sourceId", "remoteBookId", "complete", "order", "chapters", "lastUpdatedDate")
    require(value["complete"]?.jsonPrimitive?.booleanOrNull == true) { "Incomplete update check evidence" }
    require(value.requiredLiteralString("order") == "source") { "Invalid update check chapter order" }
    val chapters = value.requiredArray("chapters")
    require(chapters.size in 1..MAX_UPDATE_CHECK_CHAPTERS) { "Invalid update check chapter count" }
    return ParsedSourceUpdateCheck(
        identity = BookIdentity(value.requiredLiteralString("sourceId"), value.requiredLiteralString("remoteBookId")),
        chapters = chapters.map { element ->
            element.jsonObject.also { it.requireExactKeys("chapterId", "title") }.let { chapter ->
                SourceUpdateChapter(
                    chapterId = chapter.requiredLiteralString("chapterId"),
                    title = chapter.requiredLiteralString("title"),
                )
            }
        },
        lastUpdatedDate = value.optionalLiteralString("lastUpdatedDate"),
    )
}
private fun parseHomePage(value: JsonObject): SourceHomePage = SourceHomePage(
    schemaVersion = value["schemaVersion"]?.jsonPrimitive?.int
        ?: throw IllegalArgumentException("Missing home schema version"),
    title = value.requiredString("title"),
    filters = value.requiredArray("filters").map { element ->
        val filter = element.jsonObject
        SourceHomeFilter(
            id = filter.requiredString("id"),
            label = filter.requiredString("label"),
            options = filter.requiredArray("options").map { optionElement ->
                val option = optionElement.jsonObject
                SourceHomeFilterOption(
                    value = option.requiredString("value"),
                    label = option.requiredString("label"),
                )
            },
        )
    },
    selectedFilters = value.requiredObject("selectedFilters").mapValues { it.value.jsonPrimitive.content },
    sections = value.requiredArray("sections").map { element ->
        val section = element.jsonObject
        SourceHomeSection(
            id = section.requiredString("id"),
            title = section.requiredString("title"),
            items = section.requiredArray("items").map { parseSummary(it.jsonObject) },
        )
    },
    features = value["features"]
        ?.takeUnless { it is JsonNull }
        ?.jsonArray
        ?.map { element ->
            val feature = element.jsonObject
            SourceHomeFeature(
                id = feature.requiredString("id"),
                title = feature.requiredString("title"),
                supportingText = feature.optionalString("supportingText"),
                selectedFilters = feature.requiredObject("selectedFilters")
                    .mapValues { it.value.jsonPrimitive.content },
            )
        }
        .orEmpty(),
    nextCursor = value.optionalString("nextCursor"),
    complete = value["complete"]?.jsonPrimitive?.booleanOrNull
        ?: throw IllegalArgumentException("Missing home completion state"),
)


private fun parseDocument(value: JsonObject): ReaderDocument = ReaderDocument(
    sourceId = value.requiredString("sourceId"),
    remoteBookId = value.requiredString("remoteBookId"),
    contentId = value.requiredString("contentId"),
    revision = value.optionalString("revision"),
    title = value.requiredString("title"),
    blocks = value.requiredArray("blocks").map { element ->
        val block = element.jsonObject
        when (block.requiredString("kind")) {
            "paragraph" -> ReaderBlock.Paragraph(block.requiredString("blockId"), block.requiredString("text"))
            "heading" -> ReaderBlock.Heading(
                block.requiredString("blockId"),
                block.requiredString("text"),
                block["level"]!!.jsonPrimitive.int,
            )
            "image" -> ReaderBlock.Image(
                blockId = block.requiredString("blockId"),
                url = block.requiredString("url"),
                altText = block.optionalString("altText"),
                width = block["width"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                height = block["height"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
            )
            else -> throw IllegalArgumentException("Unsupported reader block")
        }
    },
)

private fun HxpRemoteOperationPolicy.toNetworkPolicy(): RemoteOperationRequestPolicy = RemoteOperationRequestPolicy(
    origin = origin,
    method = method,
    path = path,
    fixedParameters = parameters.filterIsInstance<HxpRemoteParameter.Fixed>().associate { it.name to it.value },
    remoteBookIdParameter = parameters.filterIsInstance<HxpRemoteParameter.RemoteBookId>().singleOrNull()?.name,
    targetIdParameter = parameters.filterIsInstance<HxpRemoteParameter.TargetId>().singleOrNull()?.name,
    cursorParameter = parameters.filterIsInstance<HxpRemoteParameter.Cursor>().singleOrNull()?.name,
    referrerPath = referrerPath,
    redirects = redirects.map { redirect ->
        RemoteOperationRedirectPolicy(
            origin = redirect.origin,
            method = redirect.method,
            path = redirect.path,
            fixedParameters = redirect.parameters.associate { it.name to it.value },
            referrerPath = redirect.referrerPath,
        )
    },
)

private fun HxpUpdateCheckPolicy.toNetworkPolicy(): RemoteOperationRequestPolicy = RemoteOperationRequestPolicy(
    origin = origin,
    method = NetworkMethod.GET,
    path = path,
    fixedParameters = parameters.filterIsInstance<HxpRemoteParameter.Fixed>().associate { it.name to it.value },
    remoteBookIdParameter = parameters.filterIsInstance<HxpRemoteParameter.RemoteBookId>().singleOrNull()?.name,
    referrerPath = referrerPath,
)

private const val MAX_REMOTE_LIBRARY_TARGETS = 128

internal fun decodeRemoteTargets(root: JsonObject, expectedSourceId: String): RemoteLibraryTargetsResult {
    val sourceId = root.requiredLiteralString("sourceId")
    require(sourceId == expectedSourceId) { "Remote target source mismatch" }
    val rawTargets = root.requiredArray("targets")
    require(rawTargets.isNotEmpty() && rawTargets.size <= MAX_REMOTE_LIBRARY_TARGETS) {
        "Invalid remote target count"
    }
    val targets = rawTargets.map { element ->
        val value = element.jsonObject
        RemoteTarget(
            targetId = value.requiredLiteralString("targetId"),
            displayName = value.requiredLiteralString("displayName"),
            parentId = value.optionalLiteralString("parentId"),
            kind = value.requiredLiteralString("kind"),
        ).also { target -> require(target.kind == "folder") { "Invalid remote target kind" } }
    }
    val targetIds = targets.mapTo(hashSetOf(), RemoteTarget::targetId)
    require(targetIds.size == targets.size) { "Duplicate remote target" }
    require(targets.all { target ->
        target.parentId == null || target.parentId != target.targetId && target.parentId in targetIds
    }) { "Invalid remote target parent" }
    return RemoteLibraryTargetsResult(sourceId, targets)
}

private fun JsonObject.requiredLiteralString(name: String): String {
    val value = requireNotNull(this[name] as? JsonPrimitive) { "Missing string: $name" }
    require(value.isString) { "Invalid string: $name" }
    return value.content
}

private fun JsonObject.optionalLiteralString(name: String): String? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    require(value is JsonPrimitive && value.isString) { "Invalid string: $name" }
    return value.content
}

private fun JsonObject.requireExactKeys(vararg required: String) {
    require(keys == required.toSet()) { "Unexpected update check result shape" }
}

private fun JsonObject.requiredString(name: String): String = requireNotNull(this[name]?.jsonPrimitive?.contentOrNull)
private fun JsonObject.optionalString(name: String): String? = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
private fun JsonObject.requiredArray(name: String): JsonArray = requireNotNull(this[name]).jsonArray
private fun JsonObject.requiredObject(name: String): JsonObject = requireNotNull(this[name]).jsonObject

private fun jsonValue(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { (key, item) ->
        require(key is String && item is String) { "Unsupported host map argument" }
        key to JsonPrimitive(item)
    })
    else -> throw IllegalArgumentException("Unsupported host argument")
}

private fun mapNetworkError(error: HostNetworkError): SourceErrorCode = when (error) {
    HostNetworkError.TIMEOUT -> SourceErrorCode.NETWORK_TIMEOUT
    HostNetworkError.OFFLINE -> SourceErrorCode.NETWORK_OFFLINE
    HostNetworkError.REDIRECT_DISALLOWED, HostNetworkError.REDIRECT_LIMIT -> SourceErrorCode.NETWORK_REDIRECT_DISALLOWED
    HostNetworkError.RESPONSE_LIMIT -> SourceErrorCode.NETWORK_RESPONSE_TOO_LARGE
    HostNetworkError.DISALLOWED_ORIGIN -> SourceErrorCode.ORIGIN_NOT_GRANTED
    HostNetworkError.OFFLINE_MISS -> SourceErrorCode.NETWORK_OFFLINE
    else -> SourceErrorCode.EXTENSION_RUNTIME_FAILURE
}

private fun fail(
    code: SourceErrorCode,
    stage: String,
    safeCode: String,
    correlationId: String = UUID.randomUUID().toString(),
): Nothing = throw SourceException(
    code,
    SourceDiagnostic(
        correlationId = correlationId,
        stage = stage.take(64),
        safeCode = safeCode.take(128),
    ),
)
