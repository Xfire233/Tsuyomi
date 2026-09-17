/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.network.DirectActionTokenRegistry
import org.tsuyomi.core.network.HostHttpResponse
import org.tsuyomi.core.network.HostHttpTransport
import org.tsuyomi.core.network.HostResponseHeaders
import org.tsuyomi.shared.sourcecontract.SourceErrorCode
import org.tsuyomi.shared.sourcecontract.SourceException
import org.tsuyomi.source.extensionmanager.HxpArchiveVerifier
import org.tsuyomi.source.extensionmanager.InMemoryPublisherKeyStore
import org.tsuyomi.source.extensionmanager.SourceExtensionClient
import org.tsuyomi.source.extensionmanager.VerifiedHxpPackage
import org.tsuyomi.source.extensiontestkit.Phase2TestPublisher

/** Exercises signed HXP verification, QuickJS output, and the app gateway adapter without a fixture-source mock. */
@RunWith(AndroidJUnit4::class)
internal class SignedMalformedSourceOutputInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun signedRuntimeMalformedRootAndRequiredFieldNormalizeToSourceExceptions() = runBlocking {
        check(context.packageName == "org.tsuyomi.android.fixture")
        val (archive, packageInfo) = verifiedFixture()
        val requestedUrls = mutableListOf<String>()
        try {
            val gateway = SourceGatewayFactory.create(
                context = context,
                packageInfo = packageInfo,
                transport = HostHttpTransport { request ->
                    requestedUrls += request.url.toString()
                    HostHttpResponse(
                        status = 200,
                        finalUrl = request.url,
                        headers = HostResponseHeaders.of("content-type" to "text/html; charset=utf-8"),
                        bytes = "fixture response".encodeToByteArray(),
                    )
                },
                directActionTokens = DirectActionTokenRegistry(),
            )
            SourceExtensionClient.open(packageInfo, gateway).use { client ->
                val malformedRoot = sourceFailure { client.search("malformed-root") }
                val missingRequiredField = sourceFailure { client.search("missing-required-field") }

                listOf(malformedRoot, missingRequiredField).forEach { failure ->
                    assertEquals(SourceErrorCode.MALFORMED_SOURCE_RESPONSE, failure.code)
                    assertEquals("search-parse", failure.diagnostic.stage)
                    assertEquals("invalid-search-results", failure.diagnostic.safeCode)
                }
            }
            assertEquals(
                listOf(
                    "https://www.wenku8.net/fixture/search?mode=malformed-root",
                    "https://www.wenku8.net/fixture/search?mode=missing-required-field",
                ),
                requestedUrls,
            )
        } finally {
            archive.delete()
        }
    }

    @Test
    fun adapterPreservesCancellationSecurityAndErrorFromSignedRuntimeRequests() = runBlocking {
        check(context.packageName == "org.tsuyomi.android.fixture")
        assertPreservedThroughAdapter(CancellationException("fixture cancellation"))
        assertPreservedThroughAdapter(SecurityException("fixture security"))
        assertPreservedThroughAdapter(AssertionError("fixture error"))
    }

    private suspend fun assertPreservedThroughAdapter(expected: Throwable) {
        val (archive, packageInfo) = verifiedFixture()
        var transportRequests = 0
        try {
            val gateway = SourceGatewayFactory.create(
                context = context,
                packageInfo = packageInfo,
                transport = HostHttpTransport {
                    transportRequests += 1
                    throw expected
                },
                directActionTokens = DirectActionTokenRegistry(),
            )
            val actual = try {
                SourceExtensionClient.open(packageInfo, gateway).use { client ->
                    client.search("preserve-throwable")
                }
                throw AssertionError("Expected adapter throwable")
            } catch (error: Throwable) {
                error
            }

            assertSame(expected, actual)
            assertEquals(1, transportRequests)
        } finally {
            archive.delete()
        }
    }

    private suspend fun sourceFailure(block: suspend () -> Unit): SourceException = try {
        block()
        throw AssertionError("Expected malformed source response")
    } catch (failure: SourceException) {
        failure
    }

    private fun verifiedFixture(): Pair<File, VerifiedHxpPackage> {
        val archive = File(context.cacheDir, "malformed-output-${System.nanoTime()}.hxp")
        context.assets.open("malformed-output-fixture.hxp").use { input ->
            archive.outputStream().use(input::copyTo)
        }
        return archive to HxpArchiveVerifier(
            InMemoryPublisherKeyStore(listOf(Phase2TestPublisher.key)),
        ).verify(archive)
    }
}
