/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import java.nio.file.Files
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkMethod

class HxpRemoteContractVerificationTest {
    @Test
    fun remoteFingerprintCanonicalizesParameterOrderAndIncludesPolicyValues() {
        val fixture = signedFixture()
        val verifier = HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(fixture.publisher)))
        val verified = verifier.verify(fixture.writeToTemporaryFile())
        val root = Files.createTempDirectory("remote-fingerprint").toFile()
        val installer = newInstaller(root, verifier)
        val parameters = listOf(
            HxpRemoteParameter.Fixed("mode", "add"),
            HxpRemoteParameter.RemoteBookId("aid"),
        )
        fun packageWith(
            parameters: List<HxpRemoteParameter>,
            publisherKeyId: String = verified.manifest.publisherKeyId,
            redirects: List<HxpRemoteRedirectTarget> = emptyList(),
        ): VerifiedHxpPackage {
            val policy = HxpRemoteOperationPolicy(
                operation = RemoteOperation.ADD,
                origin = HttpsOrigin("https://www.wenku8.net"),
                method = NetworkMethod.POST,
                path = "/modules/article/bookcase.php",
                referrerPath = "/modules/article/articleinfo.php?id={remoteBookId}",
                parameters = parameters,
                redirects = redirects,
            )
            val manifest = verified.manifest.copy(
                publisherKeyId = publisherKeyId,
                capabilities = verified.manifest.capabilities.copy(
                    remoteLibrary = HxpRemoteLibraryCapability(
                        read = false,
                        writeOperations = setOf("add"),
                        policies = mapOf(RemoteOperation.ADD to policy),
                    ),
                ),
            )
            return VerifiedHxpPackage(
                manifest,
                verified.packageSha256,
                verified.publisherFingerprint,
                verified.archiveBytes,
                verified.readVerifiedEntryModule(),
            )
        }

        val original = installer.remoteCapabilitySetFingerprint(packageWith(parameters))
        val reordered = installer.remoteCapabilitySetFingerprint(packageWith(parameters.reversed()))
        val altered = installer.remoteCapabilitySetFingerprint(
            packageWith(listOf(HxpRemoteParameter.Fixed("mode", "remove"), HxpRemoteParameter.RemoteBookId("aid"))),
        )
        val remappedKeyId = installer.remoteCapabilitySetFingerprint(packageWith(parameters, publisherKeyId = "tsuyomi-fixture-key-remapped"))
        val redirectParameters = listOf(HxpRemoteParameter.Fixed("status", "ok"), HxpRemoteParameter.Fixed("view", "compact"))
        val redirected = installer.remoteCapabilitySetFingerprint(
            packageWith(
                parameters,
                redirects = listOf(
                    HxpRemoteRedirectTarget(
                        origin = HttpsOrigin("https://www.wenku8.net"),
                        method = NetworkMethod.GET,
                        path = "/modules/article/complete.php",
                        referrerPath = null,
                        parameters = redirectParameters,
                    ),
                ),
            ),
        )
        val redirectedReordered = installer.remoteCapabilitySetFingerprint(
            packageWith(
                parameters,
                redirects = listOf(
                    HxpRemoteRedirectTarget(
                        origin = HttpsOrigin("https://www.wenku8.net"),
                        method = NetworkMethod.GET,
                        path = "/modules/article/complete.php",
                        referrerPath = null,
                        parameters = redirectParameters.reversed(),
                    ),
                ),
            ),
        )

        assertEquals(original, reordered)
        assertNotEquals(original, altered)
        assertNotEquals(original, remappedKeyId)
        assertNotEquals(original, redirected)
        assertEquals(redirected, redirectedReordered)
    }

    @Test
    fun signedManifestAcceptsOnlyFixedGetRedirectTargets() {
        fun remoteLibrary(method: String, parameterKind: String = "fixed"): JsonObject {
            val parameter = if (parameterKind == "fixed") {
                JsonObject(mapOf("kind" to JsonPrimitive("fixed"), "value" to JsonPrimitive("ok")))
            } else {
                JsonObject(mapOf("kind" to JsonPrimitive(parameterKind)))
            }
            val redirect = JsonObject(
                mapOf(
                    "origin" to JsonPrimitive("https://www.wenku8.net"),
                    "method" to JsonPrimitive(method),
                    "path" to JsonPrimitive("/remote/complete"),
                    "parameters" to JsonObject(mapOf("status" to parameter)),
                ),
            )
            return JsonObject(
                mapOf(
                    "read" to JsonPrimitive(true),
                    "writeOperations" to JsonArray(emptyList()),
                    "policies" to JsonObject(
                        mapOf(
                            "read" to JsonObject(
                                mapOf(
                                    "origin" to JsonPrimitive("https://www.wenku8.net"),
                                    "method" to JsonPrimitive("GET"),
                                    "path" to JsonPrimitive("/remote/shelf"),
                                    "parameters" to JsonObject(emptyMap()),
                                    "redirects" to JsonArray(listOf(redirect)),
                                ),
                            ),
                            "targets" to JsonObject(
                                mapOf(
                                    "origin" to JsonPrimitive("https://www.wenku8.net"),
                                    "method" to JsonPrimitive("GET"),
                                    "path" to JsonPrimitive("/remote/targets"),
                                    "parameters" to JsonObject(emptyMap()),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        val publisher = signedFixture().publisher
        val verifier = HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(publisher)))

        val missingTargets = remoteLibrary("GET").let { capability ->
            val policies = capability.getValue("policies") as JsonObject
            JsonObject(capability + ("policies" to JsonObject(policies.filterKeys { it != "targets" })))
        }
        val verified = verifier.verify(signedFixture(remoteLibrary = remoteLibrary("GET")).writeToTemporaryFile())
        val postFailure = assertThrows(HxpVerificationException::class.java) {
            verifier.verify(signedFixture(remoteLibrary = remoteLibrary("POST")).writeToTemporaryFile())
        }
        val bindingFailure = assertThrows(HxpVerificationException::class.java) {
            verifier.verify(signedFixture(remoteLibrary = remoteLibrary("GET", "cursor")).writeToTemporaryFile())
        }
        val missingTargetsFailure = assertThrows(HxpVerificationException::class.java) {
            verifier.verify(signedFixture(remoteLibrary = missingTargets).writeToTemporaryFile())
        }

        assertEquals("/remote/complete", verified.manifest.capabilities.remoteLibrary.policies.getValue(RemoteOperation.READ).redirects.single().path)
        assertEquals(HxpVerificationError.CAPABILITY_POLICY_VIOLATION, postFailure.error)
        assertEquals(HxpVerificationError.INVALID_MANIFEST, bindingFailure.error)
        assertEquals(HxpVerificationError.CAPABILITY_POLICY_VIOLATION, missingTargetsFailure.error)
    }

    @Test
    fun signedManifestAllowsGetOrPostForAddButRequiresPostForDestructiveWrites() {
        fun remoteLibrary(operation: String, method: String): JsonObject = JsonObject(
            mapOf(
                "read" to JsonPrimitive(false),
                "writeOperations" to JsonArray(listOf(JsonPrimitive(operation))),
                "policies" to JsonObject(
                    mapOf(
                        operation to JsonObject(
                            mapOf(
                                "origin" to JsonPrimitive("https://www.wenku8.net"),
                                "method" to JsonPrimitive(method),
                                "path" to JsonPrimitive("/remote/$operation"),
                                "parameters" to JsonObject(
                                    buildMap {
                                        put("book", JsonObject(mapOf("kind" to JsonPrimitive("remoteBookId"))))
                                        if (operation == "move") {
                                            put("target", JsonObject(mapOf("kind" to JsonPrimitive("targetId"))))
                                        }
                                    },
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val publisher = signedFixture().publisher
        val verifier = HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(publisher)))

        listOf("GET", "POST").forEach { method ->
            val verified = verifier.verify(signedFixture(remoteLibrary = remoteLibrary("add", method)).writeToTemporaryFile())
            assertEquals(NetworkMethod.valueOf(method), verified.manifest.capabilities.remoteLibrary.policies.getValue(RemoteOperation.ADD).method)
        }
        listOf("remove", "move").forEach { operation ->
            val failure = assertThrows(HxpVerificationException::class.java) {
                verifier.verify(signedFixture(remoteLibrary = remoteLibrary(operation, "GET")).writeToTemporaryFile())
            }
            assertEquals(HxpVerificationError.CAPABILITY_POLICY_VIOLATION, failure.error)
        }
    }
    @Test
    fun remoteTargetDecoderRejectsMalformedAndAmbiguousCollections() {
        fun target(id: String, name: String = "目标", parentId: String? = null): JsonObject = JsonObject(
            buildMap {
                put("targetId", JsonPrimitive(id))
                put("displayName", JsonPrimitive(name))
                put("kind", JsonPrimitive("folder"))
                parentId?.let { put("parentId", JsonPrimitive(it)) }
            },
        )
        fun root(targets: List<JsonObject>): JsonObject = JsonObject(
            mapOf(
                "sourceId" to JsonPrimitive("org.tsuyomi.wenku8"),
                "targets" to JsonArray(targets),
            ),
        )

        assertEquals(
            listOf("default", "favorites"),
            decodeRemoteTargets(
                root(listOf(target("default", "全部"), target("favorites", "特别", "default"))),
                "org.tsuyomi.wenku8",
            ).targets.map { it.targetId },
        )
        listOf(
            root(emptyList()),
            root(listOf(target(""))),
            root(listOf(target("same"), target("same"))),
            root(listOf(target("child", parentId = "missing"))),
            JsonObject(
                mapOf(
                    "sourceId" to JsonPrimitive("org.tsuyomi.wenku8"),
                    "targets" to JsonArray(
                        listOf(
                            JsonObject(
                                mapOf(
                                    "targetId" to JsonPrimitive(123),
                                    "displayName" to JsonPrimitive("数字"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).forEach { malformed ->
            assertThrows(IllegalArgumentException::class.java) {
                decodeRemoteTargets(malformed, "org.tsuyomi.wenku8")
            }
        }
    }

}
