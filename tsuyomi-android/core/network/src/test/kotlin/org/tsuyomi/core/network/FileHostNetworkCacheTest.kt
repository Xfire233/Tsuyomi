/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.network

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.shared.sourcecontract.DecodeMode
import org.tsuyomi.shared.sourcecontract.HttpsOrigin
import org.tsuyomi.shared.sourcecontract.NetworkCacheMode
import org.tsuyomi.shared.sourcecontract.NetworkCacheState
import org.tsuyomi.shared.sourcecontract.NetworkMethod
import org.tsuyomi.shared.sourcecontract.SourceNetworkResponse
import org.tsuyomi.shared.sourcecontract.SourceNetworkRequest

class FileHostNetworkCacheTest {
    @Test
    fun survives_cache_recreation_and_isolates_versions_and_credential_partitions() {
        val directory = Files.createTempDirectory("source-cache").toFile()
        try {
            val files = QuotaFileStore(
                roots = StorageRoots(directory.resolve("no-backup"), directory.resolve("cache")),
                root = StorageRoot.CACHE,
                namespace = "network",
                quota = StorageQuota(1024 * 1024, 16),
            )
            val keyV1 = HostNetworkCacheKey("org.tsuyomi.wenku8", "1.0.0", "search:fixture:1", DecodeMode.GB18030)
            val keyV2 = keyV1.copy(extensionVersion = "2.0.0")
            FileHostNetworkCache(files, partition = "credential-revision-a").put(keyV1, response())

            val restored = FileHostNetworkCache(files, partition = "credential-revision-a").get(keyV1)
            assertEquals("fixture response", restored?.text)
            assertEquals(NetworkCacheState.FRESH, restored?.cacheState)
            assertNull(FileHostNetworkCache(files, partition = "credential-revision-a").get(keyV2))
            assertNull(FileHostNetworkCache(files, partition = "credential-revision-b").get(keyV1))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corrupt_entry_is_deleted_and_treated_as_a_miss() {
        val directory = Files.createTempDirectory("source-cache-corrupt").toFile()
        try {
            val files = files(directory)
            val key = HostNetworkCacheKey("org.tsuyomi.wenku8", "1.0.0", "detail:1", DecodeMode.GB18030)
            val cache = FileHostNetworkCache(files)
            cache.put(key, response())
            files.write(files.entries().single().relativePath, byteArrayOf(0x01, 0x02))

            assertNull(cache.get(key))
            assertEquals(emptyList<Any>(), files.entries())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun file_store_quota_bounds_cache_entries() {
        val directory = Files.createTempDirectory("source-cache-quota").toFile()
        try {
            val files = files(directory, maxEntries = 2)
            val cache = FileHostNetworkCache(files)
            repeat(3) { index ->
                cache.put(
                    HostNetworkCacheKey("org.tsuyomi.wenku8", "1.0.0", "chapter:$index", DecodeMode.GB18030),
                    response(),
                )
            }

            assertEquals(2, files.entries().size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun recreated_gateway_reads_offline_cache_without_transport() = runBlocking {
        val directory = Files.createTempDirectory("source-cache-gateway").toFile()
        try {
            val files = files(directory)
            val grant = SourceNetworkGrant(
                sourceId = "org.tsuyomi.wenku8",
                extensionVersion = "1.0.0",
                origins = setOf(HttpsOrigin("https://www.wenku8.net")),
                maxConcurrentRequests = 1,
                requestTimeoutMs = 1_000,
                maxResponseBytes = 1_024,
            )
            val request = SourceNetworkRequest(
                url = "https://www.wenku8.net/book/1.htm",
                decode = DecodeMode.GB18030,
                method = NetworkMethod.GET,
                semanticCacheKey = "detail:1",
            )
            HostNetworkGateway(
                HostHttpTransport {
                    HostHttpResponse(200, it.url, emptyMap(), "fixture response".encodeToByteArray())
                },
                FileHostNetworkCache(files),
            ).request(grant, request)

            val offline = HostNetworkGateway(
                HostHttpTransport { error("Transport must not run for offline cache reads") },
                FileHostNetworkCache(files),
            ).request(grant, request.copy(cache = NetworkCacheMode.OFFLINE_ONLY))

            assertEquals("fixture response", offline.text)
            assertEquals(NetworkCacheState.STALE_OFFLINE, offline.cacheState)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun files(directory: java.io.File, maxEntries: Int = 16) = QuotaFileStore(
        roots = StorageRoots(directory.resolve("no-backup"), directory.resolve("cache")),
        root = StorageRoot.CACHE,
        namespace = "network",
        quota = StorageQuota(1024 * 1024, maxEntries),
    )

    private fun response() = SourceNetworkResponse(
        status = 200,
        finalUrl = "https://www.wenku8.net/modules/article/search.php",
        headers = mapOf("content-type" to "text/html; charset=gb18030"),
        text = "fixture response",
        bytes = null,
        decodeUsed = DecodeMode.GB18030,
        cacheState = NetworkCacheState.VALIDATED,
        diagnosticId = "fixture-cache-id",
    )
}
