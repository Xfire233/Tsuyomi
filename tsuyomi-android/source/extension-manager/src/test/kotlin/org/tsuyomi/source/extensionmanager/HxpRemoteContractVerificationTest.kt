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
                        ),
                    ),
                ),
            )
        }
        val publisher = signedFixture().publisher
        val verifier = HxpArchiveVerifier(InMemoryPublisherKeyStore(listOf(publisher)))

        val verified = verifier.verify(signedFixture(remoteLibrary = remoteLibrary("GET")).writeToTemporaryFile())
        val postFailure = assertThrows(HxpVerificationException::class.java) {
            verifier.verify(signedFixture(remoteLibrary = remoteLibrary("POST")).writeToTemporaryFile())
        }
        val bindingFailure = assertThrows(HxpVerificationException::class.java) {
            verifier.verify(signedFixture(remoteLibrary = remoteLibrary("GET", "cursor")).writeToTemporaryFile())
        }

        assertEquals("/remote/complete", verified.manifest.capabilities.remoteLibrary.policies.getValue(RemoteOperation.READ).redirects.single().path)
        assertEquals(HxpVerificationError.CAPABILITY_POLICY_VIOLATION, postFailure.error)
        assertEquals(HxpVerificationError.INVALID_MANIFEST, bindingFailure.error)
    }
}
