/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.erdtman.jcs.JsonCanonicalizer
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.shared.sourcecontract.SourceId

internal data class ParsedHxpManifest(
    val manifest: HxpManifest,
    val canonicalBytes: ByteArray,
)

internal object HxpManifestParser {
    private const val MAX_MANIFEST_BYTES = 128 * 1024
    private val SHA_256 = Regex("^[a-f0-9]{64}$")
    private val KEY_ID = Regex("^[A-Za-z0-9._-]{8,128}$")
    private val JSON = Json { isLenient = false; ignoreUnknownKeys = false }

    fun parse(bytes: ByteArray, hostApiVersion: SemanticVersion): ParsedHxpManifest {
        if (bytes.size > MAX_MANIFEST_BYTES) fail(HxpVerificationError.INVALID_MANIFEST)
        val text = decodeUtf8(bytes)
        val canonicalBytes = runCatching { JsonCanonicalizer(text).encodedUTF8 }
            .getOrElse { fail(HxpVerificationError.INVALID_MANIFEST) }
        val root = runCatching { JSON.parseToJsonElement(text).asObject() }
            .getOrElse { fail(HxpVerificationError.INVALID_MANIFEST) }
        root.requireKeys(
            required = setOf(
                "format", "manifestVersion", "id", "version", "display", "hostApi", "entry",
                "integrity", "signing", "capabilities", "resourceLimits", "update",
            ),
        )
        if (root.string("format") != "tsuyomi-hxp" || root.int("manifestVersion") != 1) {
            fail(HxpVerificationError.INVALID_MANIFEST)
        }

        val sourceId = runCatching { SourceId(root.string("id")) }
            .getOrElse { fail(HxpVerificationError.INVALID_MANIFEST) }
        val version = semver(root.string("version"))

        val display = root.obj("display").also {
            it.requireKeys(setOf("name", "summary"), setOf("homepage"))
        }
        val displayName = display.string("name").bounded(1, 128)
        val summary = display.string("summary").bounded(1, 512)
        val homepage = display.optionalString("homepage")?.bounded(1, 4096)

        val hostApi = root.obj("hostApi").also {
            it.requireKeys(setOf("minInclusive", "maxExclusive"))
        }
        val hostMin = semver(hostApi.string("minInclusive"))
        val hostMax = semver(hostApi.string("maxExclusive"))
        if (hostMin >= hostMax || hostApiVersion < hostMin || hostApiVersion >= hostMax) {
            fail(HxpVerificationError.HOST_API_INCOMPATIBLE)
        }

        val entry = root.string("entry")
        if (!isSafeArchivePath(entry) || !entry.endsWith(".mjs") || entry.length > 512) {
            fail(HxpVerificationError.INVALID_MANIFEST)
        }

        val integrity = root.obj("integrity").also {
            it.requireKeys(setOf("algorithm", "contentDigest", "files"))
        }
        if (integrity.string("algorithm") != "sha256") fail(HxpVerificationError.INVALID_MANIFEST)
        val contentDigest = integrity.string("contentDigest").also {
            if (!SHA_256.matches(it)) fail(HxpVerificationError.INVALID_MANIFEST)
        }
        val filesObject = integrity.obj("files")
        if (filesObject.isEmpty()) fail(HxpVerificationError.INVALID_MANIFEST)
        val files = filesObject.entries.associate { (path, digest) ->
            if (!isSafeArchivePath(path) || path == "manifest.json" || path == "signature.ed25519") {
                fail(HxpVerificationError.INVALID_MANIFEST)
            }
            val digestString = digest.asPrimitive().stringValue()
            if (!SHA_256.matches(digestString)) fail(HxpVerificationError.INVALID_MANIFEST)
            path to digestString
        }
        if (entry !in files) fail(HxpVerificationError.INVALID_MANIFEST)

        val signing = root.obj("signing").also {
            it.requireKeys(setOf("algorithm", "keyId", "signatureFile"))
        }
        if (signing.string("algorithm") != "Ed25519" || signing.string("signatureFile") != "signature.ed25519") {
            fail(HxpVerificationError.INVALID_MANIFEST)
        }
        val keyId = signing.string("keyId").also {
            if (!KEY_ID.matches(it)) fail(HxpVerificationError.INVALID_MANIFEST)
        }

        val capabilities = parseCapabilities(root.obj("capabilities"))
        val resourceLimits = root.obj("resourceLimits").also {
            it.requireKeys(setOf("maxExecutionWallTimeMs", "maxMemoryBytes"))
        }.let {
            HxpResourceLimits(
                maxExecutionWallTimeMs = it.int("maxExecutionWallTimeMs").inRange(100, 30_000),
                maxMemoryBytes = it.int("maxMemoryBytes").inRange(1_048_576, 67_108_864),
            )
        }
        val update = root.obj("update").also { it.requireKeys(setOf("channel")) }
        val channel = update.string("channel")
        if (channel != "stable" && channel != "beta") fail(HxpVerificationError.INVALID_MANIFEST)

        return ParsedHxpManifest(
            manifest = HxpManifest(
                sourceId = sourceId,
                version = version,
                displayName = displayName,
                summary = summary,
                homepage = homepage,
                hostApiMinInclusive = hostMin,
                hostApiMaxExclusive = hostMax,
                entry = entry,
                contentDigest = contentDigest,
                files = files,
                publisherKeyId = keyId,
                capabilities = capabilities,
                resourceLimits = resourceLimits,
                updateChannel = channel,
            ),
            canonicalBytes = canonicalBytes,
        )
    }

    private fun parseCapabilities(value: JsonObject): HxpCapabilities {
        value.requireKeys(setOf("network", "cookies", "webLogin", "remoteLibrary", "storage"), setOf("home"))
        val network = value.obj("network").also {
            it.requireKeys(setOf("origins", "maxConcurrentRequests", "requestTimeoutMs", "maxResponseBytes"))
        }
        val networkOrigins = network.originSet("origins", requireNonEmpty = true)
        val networkCapability = HxpNetworkCapability(
            origins = networkOrigins,
            maxConcurrentRequests = network.int("maxConcurrentRequests").inRange(1, 8),
            requestTimeoutMs = network.int("requestTimeoutMs").inRange(1_000, 120_000),
            maxResponseBytes = network.int("maxResponseBytes").inRange(1_024, 16_777_216),
        )

        val cookies = value.obj("cookies").also { it.requireKeys(setOf("mode", "origins")) }
        val cookieMode = cookies.string("mode")
        if (cookieMode != "none" && cookieMode != "sourceScoped") fail(HxpVerificationError.INVALID_MANIFEST)
        val cookieOrigins = cookies.originSet("origins")
        if (cookieMode == "none" && cookieOrigins.isNotEmpty()) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
        if (!networkOrigins.containsAll(cookieOrigins)) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)

        val webLogin = value.obj("webLogin").also { it.requireKeys(setOf("enabled", "origins")) }
        val webLoginEnabled = webLogin.bool("enabled")
        val webLoginOrigins = webLogin.originSet("origins")
        if (!webLoginEnabled && webLoginOrigins.isNotEmpty()) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
        if (!networkOrigins.containsAll(webLoginOrigins)) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
        val homeEnabled = value["home"]?.asObject()?.also {
            it.requireKeys(setOf("enabled"))
        }?.bool("enabled") ?: false

        val remoteLibrary = value.obj("remoteLibrary").also {
            it.requireKeys(setOf("read", "writeOperations"), setOf("policies"))
        }
        val read = remoteLibrary.bool("read")
        val writes = remoteLibrary.array("writeOperations").map { it.asPrimitive().stringValue() }.toSet()
        if (writes.size != remoteLibrary.array("writeOperations").size || writes.any { it !in setOf("add", "remove", "move") }) {
            fail(HxpVerificationError.INVALID_MANIFEST)
        }
        val policies = parseRemotePolicies(remoteLibrary, networkOrigins, read, writes)
        val storage = value.obj("storage").also { it.requireKeys(setOf("quotaBytes")) }
        return HxpCapabilities(
            network = networkCapability,
            cookies = HxpCookieCapability(sourceScoped = cookieMode == "sourceScoped", origins = cookieOrigins),
            webLogin = HxpWebLoginCapability(enabled = webLoginEnabled, origins = webLoginOrigins),
            home = HxpHomeCapability(enabled = homeEnabled),
            remoteLibrary = HxpRemoteLibraryCapability(read = read, writeOperations = writes, policies = policies),
            storageQuotaBytes = storage.int("quotaBytes").inRange(0, 10_485_760),
        )
    }

    private fun parseRemotePolicies(
        remoteLibrary: JsonObject,
        networkOrigins: Set<HttpsOrigin>,
        read: Boolean,
        writes: Set<String>,
    ): Map<RemoteOperation, HxpRemoteOperationPolicy> {
        val required = buildSet {
            if (read) add("read")
            if ("add" in writes) add("add")
        }
        val raw = remoteLibrary["policies"] ?: return if (required.isEmpty()) emptyMap() else fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
        val objectValue = raw.asObject()
        if (objectValue.keys != required) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
        return objectValue.entries.associate { (name, value) ->
            val operation = when (name) {
                "read" -> RemoteOperation.READ
                "add" -> RemoteOperation.ADD
                else -> fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
            }
            operation to parseRemotePolicy(operation, value.asObject(), networkOrigins)
        }
    }

    private fun parseRemotePolicy(
        operation: RemoteOperation,
        value: JsonObject,
        networkOrigins: Set<HttpsOrigin>,
    ): HxpRemoteOperationPolicy {
        value.requireKeys(setOf("origin", "method", "path", "parameters"), setOf("referrerPath", "redirects"))
        val origin = runCatching { HttpsOrigin(value.string("origin")) }
            .getOrElse { fail(HxpVerificationError.INVALID_MANIFEST) }
        if (origin !in networkOrigins) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
        val method = runCatching { NetworkMethod.valueOf(value.string("method")) }
            .getOrElse { fail(HxpVerificationError.INVALID_MANIFEST) }
        if ((operation == RemoteOperation.READ && method != NetworkMethod.GET) ||
            (operation == RemoteOperation.ADD && method != NetworkMethod.POST)
        ) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
        val path = value.string("path")
        if (!path.startsWith('/') || '?' in path || '#' in path || path.length > 1024) fail(HxpVerificationError.INVALID_MANIFEST)
        val referrerPath = value.optionalString("referrerPath")
        if (referrerPath != null && (!referrerPath.startsWith('/') || '?' in referrerPath || '#' in referrerPath || referrerPath.length > 1024)) {
            fail(HxpVerificationError.INVALID_MANIFEST)
        }
        val parameters = value.obj("parameters").entries.sortedBy { it.key }.map { (name, rule) ->
            if (name.isBlank() || name.codePointCount(0, name.length) > 256) fail(HxpVerificationError.INVALID_MANIFEST)
            val ruleObject = rule.asObject()
            when (ruleObject.string("kind")) {
                "fixed" -> {
                    ruleObject.requireKeys(setOf("kind", "value"))
                    HxpRemoteParameter.Fixed(name, ruleObject.string("value").bounded(0, 8192))
                }
                "remoteBookId" -> {
                    ruleObject.requireKeys(setOf("kind"))
                    if (operation != RemoteOperation.ADD) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
                    HxpRemoteParameter.RemoteBookId(name)
                }
                "cursor" -> {
                    ruleObject.requireKeys(setOf("kind"))
                    if (operation != RemoteOperation.READ || name != "cursor") fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
                    HxpRemoteParameter.Cursor(name)
                }
                else -> fail(HxpVerificationError.INVALID_MANIFEST)
            }
        }
        if (parameters.count { it is HxpRemoteParameter.RemoteBookId } != (if (operation == RemoteOperation.ADD) 1 else 0) ||
            parameters.count { it is HxpRemoteParameter.Cursor } > 1
        ) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
        val redirects: List<HxpRemoteRedirectTarget> = when (val rawRedirects = value["redirects"]) {
            null -> emptyList()
            is JsonArray -> rawRedirects.map { redirect -> parseRemoteRedirect(redirect.asObject(), networkOrigins) }
            else -> fail(HxpVerificationError.INVALID_MANIFEST)
        }
        if (redirects.size > 5 || redirects.distinct() != redirects) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
        return HxpRemoteOperationPolicy(operation, origin, method, path, referrerPath, parameters, redirects)
    }

    private fun parseRemoteRedirect(value: JsonObject, networkOrigins: Set<HttpsOrigin>): HxpRemoteRedirectTarget {
        value.requireKeys(setOf("origin", "method", "path", "parameters"), setOf("referrerPath"))
        val origin = runCatching { HttpsOrigin(value.string("origin")) }.getOrElse { fail(HxpVerificationError.INVALID_MANIFEST) }
        if (origin !in networkOrigins) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
        if (value.string("method") != NetworkMethod.GET.name) fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
        val path = value.string("path")
        if (!path.startsWith('/') || '?' in path || '#' in path || path.length > 1024) fail(HxpVerificationError.INVALID_MANIFEST)
        val referrerPath = value.optionalString("referrerPath")
        if (referrerPath != null && (!referrerPath.startsWith('/') || '?' in referrerPath || '#' in referrerPath || referrerPath.length > 1024)) {
            fail(HxpVerificationError.INVALID_MANIFEST)
        }
        val parameters = value.obj("parameters").entries.sortedBy { it.key }.map { (name, rule) ->
            if (name.isBlank() || name.codePointCount(0, name.length) > 256) fail(HxpVerificationError.INVALID_MANIFEST)
            val ruleObject = rule.asObject()
            ruleObject.requireKeys(setOf("kind", "value"))
            if (ruleObject.string("kind") != "fixed") fail(HxpVerificationError.CAPABILITY_POLICY_VIOLATION)
            HxpRemoteParameter.Fixed(name, ruleObject.string("value").bounded(0, 8192))
        }
        return HxpRemoteRedirectTarget(origin, NetworkMethod.GET, path, referrerPath, parameters)
    }

    private fun JsonObject.originSet(name: String, requireNonEmpty: Boolean = false): Set<HttpsOrigin> {
        val array = array(name)
        if (requireNonEmpty && array.isEmpty()) fail(HxpVerificationError.INVALID_MANIFEST)
        val origins = array.map { element ->
            val raw = element.asPrimitive().stringValue()
            val canonical = runCatching { HttpsOrigin(raw).canonical }
                .getOrElse { fail(HxpVerificationError.INVALID_MANIFEST) }
            HttpsOrigin(canonical)
        }.toSet()
        if (origins.size != array.size) fail(HxpVerificationError.INVALID_MANIFEST)
        return origins
    }

    private fun decodeUtf8(bytes: ByteArray): String = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrElse { fail(HxpVerificationError.INVALID_MANIFEST) }

    private fun semver(value: String): SemanticVersion = runCatching { SemanticVersion.parse(value) }
        .getOrElse { fail(HxpVerificationError.INVALID_MANIFEST) }

    private fun JsonObject.requireKeys(required: Set<String>, optional: Set<String> = emptySet()) {
        if (!keys.containsAll(required) || keys.any { it !in required && it !in optional }) {
            fail(HxpVerificationError.INVALID_MANIFEST)
        }
    }

    private fun JsonObject.obj(name: String): JsonObject = get(name)?.asObject()
        ?: fail(HxpVerificationError.INVALID_MANIFEST)
    private fun JsonObject.array(name: String): JsonArray = get(name) as? JsonArray
        ?: fail(HxpVerificationError.INVALID_MANIFEST)
    private fun JsonObject.string(name: String): String = get(name)?.asPrimitive()?.stringValue()
        ?: fail(HxpVerificationError.INVALID_MANIFEST)
    private fun JsonObject.optionalString(name: String): String? = get(name)?.asPrimitive()?.stringValue()
    private fun JsonObject.int(name: String): Int = get(name)?.asPrimitive()?.intValue()
        ?: fail(HxpVerificationError.INVALID_MANIFEST)
    private fun JsonObject.bool(name: String): Boolean = get(name)?.asPrimitive()?.boolValue()
        ?: fail(HxpVerificationError.INVALID_MANIFEST)

    private fun JsonElement.asObject(): JsonObject = this as? JsonObject
        ?: fail(HxpVerificationError.INVALID_MANIFEST)
    private fun JsonElement.asPrimitive(): JsonPrimitive = this as? JsonPrimitive
        ?: fail(HxpVerificationError.INVALID_MANIFEST)
    private fun JsonPrimitive.stringValue(): String = takeIf { isString }?.content
        ?: fail(HxpVerificationError.INVALID_MANIFEST)
    private fun JsonPrimitive.intValue(): Int? = if (!isString) content.toIntOrNull() else null
    private fun JsonPrimitive.boolValue(): Boolean? = if (!isString) {
        when (content) { "true" -> true; "false" -> false; else -> null }
    } else null

    private fun String.bounded(minimum: Int, maximum: Int): String = also {
        val count = codePointCount(0, length)
        if (count !in minimum..maximum) fail(HxpVerificationError.INVALID_MANIFEST)
    }

    private fun Int.inRange(minimum: Int, maximum: Int): Int = also {
        if (it !in minimum..maximum) fail(HxpVerificationError.INVALID_MANIFEST)
    }
}

internal fun isSafeArchivePath(path: String): Boolean {
    if (path.isBlank() || path.startsWith('/') || '\\' in path || path.length > 1024) return false
    if (java.text.Normalizer.normalize(path, java.text.Normalizer.Form.NFC) != path) return false
    return path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
}

private fun fail(error: HxpVerificationError): Nothing = throw HxpVerificationException(error)
