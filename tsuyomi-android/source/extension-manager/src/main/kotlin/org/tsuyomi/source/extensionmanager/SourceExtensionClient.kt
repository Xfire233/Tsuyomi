/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.io.Closeable
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID
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
        remoteAddPolicy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.ADD]?.toNetworkPolicy(),
    )

    suspend fun searchRequestUrl(query: String, page: Int = 1): String =
        requestUrl("buildSearchRequest", arrayOf<Any?>(query, page), "search-network")

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
        val response = invokeNetwork("buildSearchRequest", arrayOf<Any?>(query, page), "search-network", offlineOnly)
        classify(response, "search-classify", "search")
        val root = call(
            "parseSearch",
            arrayOf<Any?>(response.text.orEmpty(), response.finalUrl),
            "search-parse",
        ).jsonObject
        return root.requiredArray("items").map { parseSummary(it.jsonObject) }
    }
    suspend fun home(
        selectedFilters: Map<String, String> = emptyMap(),
        cursor: String? = null,
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
        val response = invokeNetwork("buildHomeRequest", arguments, "home-network", offlineOnly = false)
        classify(response, "home-classify", "home")
        return try {
            parseHomePage(
                call(
                    "parseHome",
                    arrayOf<Any?>(response.text.orEmpty(), cursor, selectedFilters),
                    "home-parse",
                ).jsonObject,
            )
        } catch (error: SourceException) {
            throw error
        } catch (_: Throwable) {
            fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "home-parse", "invalid-home-page")
        }
    }


    suspend fun detail(remoteBookId: String, offlineOnly: Boolean = false): SourceBookDetail {
        val response = invokeNetwork("buildDetailRequest", arrayOf<Any?>(remoteBookId), "detail-network", offlineOnly)
        classify(response, "detail-classify", "detail", remoteBookId)
        return parseDetail(call("parseDetail", arrayOf<Any?>(response.text.orEmpty(), remoteBookId), "detail-parse").jsonObject)
    }

    suspend fun directory(remoteBookId: String, offlineOnly: Boolean = false): SourceDirectory {
        val response = invokeNetwork("buildDirectoryRequest", arrayOf<Any?>(remoteBookId), "directory-network", offlineOnly)
        classify(response, "directory-classify", "directory", remoteBookId)
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
        return SourceDirectory(identity, chapters)
    }
    suspend fun chapter(
        chapter: SourceChapter,
        remoteBookId: String,
        offlineOnly: Boolean = false,
    ): ReaderDocument {
        val response = invokeNetwork(
            "buildChapterRequest",
            arrayOf<Any?>(chapter.url, remoteBookId, chapter.chapterId),
            "chapter-network",
            offlineOnly,
        )
        classify(response, "chapter-classify", "chapter", remoteBookId, chapter.chapterId)
        return parseDocument(
            call(
                "parseChapter",
                arrayOf<Any?>(response.text.orEmpty(), remoteBookId, chapter.chapterId, chapter.title),
                "chapter-parse",
            ).jsonObject,
        )
    }

    suspend fun listRemoteLibrary(cursor: String?): RemoteLibraryPage {
        val policy = manifest.capabilities.remoteLibrary.policies[RemoteOperation.READ]
            ?: fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-read", "remote-read-not-granted")
        val response = invokeNetwork(
            "buildRemoteLibraryRequest",
            arrayOf<Any?>(cursor),
            "remote-library-read-network",
            offlineOnly = false,
            operationContext = remoteLibraryReadContext(policy.toNetworkPolicy(), cursor),
        )
        classify(response, "remote-library-read-classify", "remote-library")
        val root = call("parseRemoteLibrary", arrayOf<Any?>(response.text.orEmpty()), "remote-library-read-parse").jsonObject
        val items = root.requiredArray("items").map { parseSummary(it.jsonObject) }
        val nextCursor = root.optionalString("nextCursor")
        val complete = root["complete"]?.jsonPrimitive?.booleanOrNull
            ?: fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-read-parse", "missing-complete")
        return try {
            RemoteLibraryPage(items, nextCursor, complete)
        } catch (_: IllegalArgumentException) {
            fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-read-parse", "invalid-page")
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
        val root = call("parseRemoteLibraryAdd", arrayOf<Any?>(response.text.orEmpty(), remoteBookId), "remote-library-add-parse").jsonObject
        val identity = BookIdentity(root.requiredString("sourceId"), root.requiredString("remoteBookId"))
        if (identity.sourceId != manifest.sourceId.value || identity.remoteBookId != remoteBookId) {
            fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-add-parse", "identity-mismatch")
        }
        val outcome = when (root.requiredString("outcome")) {
            "applied" -> RemoteLibraryAddOutcome.APPLIED
            "already-present" -> RemoteLibraryAddOutcome.ALREADY_PRESENT
            else -> fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, "remote-library-add-parse", "invalid-outcome")
        }
        return RemoteLibraryAddResult(identity, outcome)
    }

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
    } catch (error: SourceException) {
        throw error
    } catch (_: Throwable) {
        fail(SourceErrorCode.EXTENSION_RUNTIME_FAILURE, "$stage-request", "invalid-request-dto")
    }

    private suspend fun classify(
        response: SourceNetworkResponse,
        stage: String,
        operation: String = "generic",
        remoteBookId: String? = null,
        chapterId: String? = null,
    ) {
        val arguments = listOf<Any?>(response.text.orEmpty(), response.finalUrl, operation, remoteBookId, chapterId).toTypedArray()
        when (call("classifyPage", arguments, stage).jsonPrimitive.content) {
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
            val code = when (error.error) {
                QuickJsRuntimeError.EXECUTION_LIMIT -> SourceErrorCode.EXTENSION_TIMEOUT
                QuickJsRuntimeError.CANCELLED -> SourceErrorCode.EXTENSION_CANCELLED
                else -> SourceErrorCode.EXTENSION_RUNTIME_FAILURE
            }
            fail(code, stage, error.error.name.lowercase())
        }
        return try {
            JSON.parseToJsonElement(result)
        } catch (_: Throwable) {
            fail(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, stage, "invalid-json-result")
        }
    }

    override fun close() = runtime.close()

    companion object {
        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }

        suspend fun open(packageInfo: VerifiedHxpPackage, gateway: HostNetworkGateway): SourceExtensionClient {
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
                SourceExtensionClient(packageInfo, gateway, runtime)
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
)

private fun parseDetail(value: JsonObject): SourceBookDetail = SourceBookDetail(
    summary = parseSummary(value.requiredObject("summary")),
    description = value.optionalString("description"),
    tags = value.requiredArray("tags").map { it.jsonPrimitive.content },
    status = value.optionalString("status"),
)
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
