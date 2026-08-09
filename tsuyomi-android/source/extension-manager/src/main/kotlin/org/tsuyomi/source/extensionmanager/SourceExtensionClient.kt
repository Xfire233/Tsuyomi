/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.io.Closeable
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
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.NetworkCacheMode
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.SourceBookDetail
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
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
    )

    suspend fun search(query: String, page: Int = 1, offlineOnly: Boolean = false): List<SourceBookSummary> {
        val response = invokeNetwork("buildSearchRequest", arrayOf<Any?>(query, page), "search-network", offlineOnly)
        classify(response, "search-classify")
        val root = call("parseSearch", arrayOf<Any?>(response.text.orEmpty()), "search-parse").jsonObject
        return root.requiredArray("items").map { parseSummary(it.jsonObject) }
    }

    suspend fun detail(remoteBookId: String, offlineOnly: Boolean = false): SourceBookDetail {
        val response = invokeNetwork("buildDetailRequest", arrayOf<Any?>(remoteBookId), "detail-network", offlineOnly)
        classify(response, "detail-classify")
        return parseDetail(call("parseDetail", arrayOf<Any?>(response.text.orEmpty(), remoteBookId), "detail-parse").jsonObject)
    }

    suspend fun directory(remoteBookId: String, offlineOnly: Boolean = false): SourceDirectory {
        val response = invokeNetwork("buildDirectoryRequest", arrayOf<Any?>(remoteBookId), "directory-network", offlineOnly)
        classify(response, "directory-classify")
        val root = call("parseDirectory", arrayOf<Any?>(response.text.orEmpty(), remoteBookId), "directory-parse").jsonObject
        val identity = BookIdentity(root.requiredString("sourceId"), root.requiredString("remoteBookId"))
        val chapters = root.requiredArray("chapters").map { chapter ->
            val value = chapter.jsonObject
            SourceChapter(
                chapterId = value.requiredString("chapterId"),
                title = value.requiredString("title"),
                url = value.requiredString("url"),
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
        classify(response, "chapter-classify")
        return parseDocument(
            call(
                "parseChapter",
                arrayOf<Any?>(response.text.orEmpty(), remoteBookId, chapter.chapterId, chapter.title),
                "chapter-parse",
            ).jsonObject,
        )
    }

    private suspend fun invokeNetwork(
        function: String,
        arguments: Array<out Any?>,
        stage: String,
        offlineOnly: Boolean,
    ): SourceNetworkResponse {
        val request = try {
            parseRequest(call(function, arguments, "$stage-request").jsonObject).let { built ->
                if (offlineOnly) built.copy(cache = NetworkCacheMode.OFFLINE_ONLY) else built
            }
        } catch (error: SourceException) {
            throw error
        } catch (_: Throwable) {
            fail(SourceErrorCode.EXTENSION_RUNTIME_FAILURE, "$stage-request", "invalid-request-dto")
        }
        return try {
            gateway.request(grant, request)
        } catch (error: HostNetworkException) {
            fail(mapNetworkError(error.error), stage, error.error.name.lowercase(), error.diagnosticId)
        }
    }

    private suspend fun classify(response: SourceNetworkResponse, stage: String) {
        when (call("classifyPage", arrayOf<Any?>(response.text.orEmpty()), stage).jsonPrimitive.content) {
            "ok" -> Unit
            "session-required" -> fail(SourceErrorCode.SESSION_REQUIRED, stage, "session-required")
            "verification-required" -> fail(SourceErrorCode.VERIFICATION_REQUIRED, stage, "verification-required")
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


private fun parseRequest(value: JsonObject): SourceNetworkRequest = SourceNetworkRequest(
    url = value.requiredString("url"),
    method = NetworkMethod.valueOf(value.requiredString("method")),
    headers = value["headers"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
    form = value["form"]?.takeUnless { it is JsonNull }?.jsonObject?.mapValues { it.value.jsonPrimitive.content },
    utf8Body = value.optionalString("utf8Body"),
    decode = DecodeMode.entries.single { it.wireValue == value.requiredString("decode") },
    cache = NetworkCacheMode.entries.single { it.wireValue == value.requiredString("cache") },
    semanticCacheKey = value.optionalString("semanticCacheKey"),
    referrerUrl = value.optionalString("referrerUrl"),
)

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
