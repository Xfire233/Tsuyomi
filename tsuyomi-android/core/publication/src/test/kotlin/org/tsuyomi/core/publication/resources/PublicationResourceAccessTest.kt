/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication.resources

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageQuota
import org.tsuyomi.core.files.StorageRoot
import org.tsuyomi.core.files.StorageRoots
import org.tsuyomi.core.files.StoredFile
import org.tsuyomi.core.network.HostHttpRequest
import org.tsuyomi.core.network.HostHttpResponse
import org.tsuyomi.core.network.HostResponseHeaders

class PublicationResourceAccessTest {
    @Test
    fun missing_or_mismatched_grant_never_resolves_or_transports() = runBlocking {
        val resolver = RecordingResolver { listOf(PUBLIC_IP) }
        val transport = RecordingTransport { request, _ -> success(request) }
        val grants = InMemoryGrantPersistence()
        val access = access(grants, resolver, transport)

        assertEquals(
            PublicationResourceFetchResult.Rejected(PublicationResourceRejection.NO_GRANT),
            access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL),
        )
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))
        assertEquals(
            PublicationResourceFetchResult.Rejected(PublicationResourceRejection.GRANT_MISMATCH),
            access.fetch(PUBLICATION_ID, OTHER_DIGEST, RESOURCE_URL),
        )
        assertEquals(
            PublicationResourceFetchResult.Rejected(PublicationResourceRejection.ORIGIN_NOT_GRANTED),
            access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, "https://other.example/cover.png"),
        )
        assertEquals(
            PublicationResourceFetchResult.Rejected(PublicationResourceRejection.INVALID_URL),
            access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, "http://assets.example/cover.png"),
        )
        assertEquals(0, resolver.hosts.size)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun grants_are_persisted_privately_and_bound_to_the_original_digest() = runBlocking {
        val root = Files.createTempDirectory("publication-resource-grants").toFile()
        try {
            val roots = StorageRoots(File(root, "no-backup"), File(root, "cache"))
            val firstStore = QuotaFileStore(
                roots = roots,
                root = StorageRoot.NO_BACKUP,
                namespace = "publication-resource-grants",
                quota = StorageQuota(8L * 1024 * 1024, 1),
            )
            val firstPersistence = PrivatePublicationGrantPersistence(firstStore)
            val first = access(firstPersistence, RecordingResolver { listOf(PUBLIC_IP) }, RecordingTransport { request, _ -> success(request) })
            val grant = first.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))

            assertEquals(PublicationResourceGrant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png")), grant)
            assertTrue(firstStore.directory().canonicalPath.startsWith(roots.noBackupDirectory.canonicalPath))

            val restarted = access(
                persistence(roots),
                RecordingResolver { listOf(PUBLIC_IP) },
                RecordingTransport { request, _ -> success(request) },
            )
            assertEquals(grant, restarted.grantSnapshot(PUBLICATION_ID, ORIGINAL_DIGEST))
            assertEquals(setOf(ORIGIN), restarted.origins(PUBLICATION_ID, ORIGINAL_DIGEST))
            assertEquals(null, restarted.grantSnapshot(PUBLICATION_ID, OTHER_DIGEST))
            assertTrue(restarted.revoke(PUBLICATION_ID))
            assertTrue(restarted.origins(PUBLICATION_ID, ORIGINAL_DIGEST).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun grants_reject_private_origins_unusual_ports_and_active_content_types() = runBlocking {
        val access = access()
        val invalidOrigins = listOf(
            "http://assets.example",
            "https://127.0.0.1",
            "https://192.0.2.1",
            "https://assets.example:8443",
            "https://user@assets.example",
            "https://assets.example/",
            "https://inside.local",
        )
        invalidOrigins.forEach { origin ->
            assertIllegalGrant { access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(origin), setOf("image/png")) }
        }
        listOf("image/svg+xml", "text/html", "font/woff2", "image/gif").forEach { mime ->
            assertIllegalGrant { access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf(mime)) }
        }
    }

    @Test
    fun public_dns_pins_are_forwarded_and_successful_rasters_are_cached() = runBlocking {
        val resolver = RecordingResolver { listOf(PUBLIC_IP, PUBLIC_IPV6) }
        val transport = RecordingTransport { request, pins ->
            assertEquals(listOf(PUBLIC_IP, PUBLIC_IPV6), pins)
            success(request)
        }
        val cache = InMemoryResourceCache()
        val access = access(InMemoryGrantPersistence(), resolver, transport, cache)
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))

        val first = access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL) as PublicationResourceFetchResult.Success
        assertEquals("image/png", first.mimeType)
        assertArrayEquals(PNG, first.bytes)
        assertEquals(1, resolver.hosts.size)
        assertEquals(1, transport.requests.size)
        assertTrue(cache.entries().isNotEmpty())

        val cached = access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL) as PublicationResourceFetchResult.Success
        assertArrayEquals(PNG, cached.bytes)
        assertEquals(1, resolver.hosts.size)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun a_mixed_public_and_private_dns_answer_set_is_rejected_before_transport() = runBlocking {
        val resolver = RecordingResolver { listOf(PUBLIC_IP, InetAddress.getByAddress(byteArrayOf(10, 0, 0, 8))) }
        val transport = RecordingTransport { request, _ -> success(request) }
        val access = access(InMemoryGrantPersistence(), resolver, transport)
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))

        assertRejected(access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL), PublicationResourceRejection.NON_PUBLIC_ADDRESS)
        assertEquals(1, resolver.hosts.size)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun every_same_origin_redirect_is_manually_re_resolved_and_pinned() = runBlocking {
        val addressIndex = AtomicInteger()
        val resolver = RecordingResolver { hostname ->
            if (hostname == "assets.example") {
                listOf(if (addressIndex.getAndIncrement() == 0) PUBLIC_IP else SECOND_PUBLIC_IP)
            } else {
                error("unexpected host")
            }
        }
        val hopIndex = AtomicInteger()
        val transport = RecordingTransport { request, _ ->
            if (hopIndex.getAndIncrement() == 0) response(request, 302, "location" to "/images/cover.png") else success(request)
        }
        val access = access(InMemoryGrantPersistence(), resolver, transport)
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))

        val result = access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL)
        assertTrue(result is PublicationResourceFetchResult.Success)
        assertEquals(2, resolver.hosts.size)
        assertEquals(2, transport.requests.size)
        assertEquals(PUBLIC_IP, transport.pins[0].single())
        assertEquals(SECOND_PUBLIC_IP, transport.pins[1].single())
    }

    @Test
    fun cross_origin_downgrade_credentials_and_nonstandard_port_redirects_are_rejected() = runBlocking {
        val locations = listOf(
            "https://other.example/image.png",
            "http://assets.example/image.png",
            "https://assets.example:8443/image.png",
            "https://user@assets.example/image.png",
        )
        locations.forEach { location ->
            val resolver = RecordingResolver { listOf(PUBLIC_IP) }
            val transport = RecordingTransport { request, _ -> response(request, 302, "location" to location) }
            val access = access(InMemoryGrantPersistence(), resolver, transport)
            access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))

            assertRejected(access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL), PublicationResourceRejection.REDIRECT_REJECTED)
            assertEquals(1, transport.requests.size)
        }
    }

    @Test
    fun redirects_are_bounded_before_an_excess_hop_is_requested() = runBlocking {
        val resolver = RecordingResolver { listOf(PUBLIC_IP) }
        val transport = RecordingTransport { request, _ -> response(request, 302, "location" to "/again") }
        val access = access(InMemoryGrantPersistence(), resolver, transport)
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))

        assertRejected(access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL), PublicationResourceRejection.REDIRECT_LIMIT)
        assertEquals(6, transport.requests.size)
        assertEquals(6, resolver.hosts.size)
    }

    @Test
    fun oversized_and_invalid_image_results_do_not_enter_cache_or_consume_the_retry_budget() = runBlocking {
        val resolver = RecordingResolver { listOf(PUBLIC_IP) }
        val responses = AtomicInteger()
        val transport = RecordingTransport { request, _ ->
            if (responses.getAndIncrement() == 0) {
                response(request, 200, "content-type" to "image/png", bytes = PNG + byteArrayOf(0))
            } else {
                success(request)
            }
        }
        val cache = InMemoryResourceCache()
        val access = access(
            InMemoryGrantPersistence(),
            resolver,
            transport,
            cache,
            PublicationResourceBudgets(PNG.size, PNG.size.toLong()),
        )
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))

        assertRejected(access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL), PublicationResourceRejection.RESOURCE_LIMIT)
        assertTrue(cache.entries().isEmpty())
        val retry = access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL)
        assertTrue(retry is PublicationResourceFetchResult.Success)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun image_signature_and_pixel_geometry_are_checked_before_return_or_cache() = runBlocking {
        val resolver = RecordingResolver { listOf(PUBLIC_IP) }
        val transport = RecordingTransport { request, _ ->
            val bytes = if (request.url.path.endsWith("dimensions.png")) createPng(width = 20_000) else byteArrayOf(1, 2, 3)
            response(request, 200, "content-type" to "image/png", bytes = bytes)
        }
        val cache = InMemoryResourceCache()
        val access = access(InMemoryGrantPersistence(), resolver, transport, cache)
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))

        assertRejected(
            access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, "https://assets.example/bad.png"),
            PublicationResourceRejection.INVALID_IMAGE,
        )
        assertRejected(
            access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, "https://assets.example/dimensions.png"),
            PublicationResourceRejection.INVALID_IMAGE,
        )
        assertTrue(cache.entries().isEmpty())
    }

    @Test
    fun aggregate_publication_quota_reserves_inflight_bytes_and_releases_on_failure() = runBlocking {
        val resolver = RecordingResolver { listOf(PUBLIC_IP) }
        val transport = RecordingTransport { request, _ -> success(request) }
        val access = access(
            InMemoryGrantPersistence(),
            resolver,
            transport,
            InMemoryResourceCache(),
            PublicationResourceBudgets(PNG.size, PNG.size.toLong() + PNG.size / 2),
        )
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))

        assertTrue(access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL) is PublicationResourceFetchResult.Success)
        val second = access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, "https://assets.example/second.png")
        assertRejected(second, PublicationResourceRejection.PUBLICATION_QUOTA)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun concurrent_fetches_cannot_overspend_a_publication_resource_budget() = runBlocking {
        val resolver = RecordingResolver { listOf(PUBLIC_IP) }
        val started = CompletableDeferred<HostHttpRequest>()
        val release = CompletableDeferred<HostHttpResponse>()
        val transport = RecordingTransport { request, _ ->
            started.complete(request)
            withContext(NonCancellable) { release.await() }
        }
        val access = access(
            InMemoryGrantPersistence(),
            resolver,
            transport,
            InMemoryResourceCache(),
            PublicationResourceBudgets(PNG.size, PNG.size.toLong()),
        )
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))

        val first = async { access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL) }
        val firstRequest = started.await()
        val second = access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, "https://assets.example/second.png")
        assertRejected(second, PublicationResourceRejection.PUBLICATION_QUOTA)

        release.complete(success(firstRequest))
        assertTrue(first.await() is PublicationResourceFetchResult.Success)
        assertEquals(1, transport.requests.size)
        assertEquals(1, resolver.hosts.size)
    }

    @Test
    fun revocation_discards_a_late_network_result_and_prevents_cache_admission() = runBlocking {
        val resolver = RecordingResolver { listOf(PUBLIC_IP) }
        val started = CompletableDeferred<HostHttpRequest>()
        val release = CompletableDeferred<HostHttpResponse>()
        val transport = RecordingTransport { request, _ ->
            started.complete(request)
            withContext(NonCancellable) { release.await() }
        }
        val cache = InMemoryResourceCache()
        val access = access(InMemoryGrantPersistence(), resolver, transport, cache)
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))
        val pending = async { access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL) }
        val request = started.await()

        assertTrue(access.revoke(PUBLICATION_ID))
        release.complete(success(request))

        assertRejected(pending.await(), PublicationResourceRejection.REVOKED)
        assertTrue(cache.entries().isEmpty())
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun an_original_digest_change_invalidates_late_results_and_cache_admissions() = runBlocking {
        val resolver = RecordingResolver { listOf(PUBLIC_IP) }
        val started = CompletableDeferred<HostHttpRequest>()
        val release = CompletableDeferred<HostHttpResponse>()
        val requestCount = AtomicInteger()
        val transport = RecordingTransport { request, _ ->
            if (requestCount.getAndIncrement() == 0) {
                started.complete(request)
                withContext(NonCancellable) { release.await() }
            } else {
                success(request)
            }
        }
        val cache = InMemoryResourceCache()
        val access = access(InMemoryGrantPersistence(), resolver, transport, cache)
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))
        val pending = async { access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL) }
        val oldRequest = started.await()

        assertRejected(
            access.fetch(PUBLICATION_ID, OTHER_DIGEST, RESOURCE_URL),
            PublicationResourceRejection.GRANT_MISMATCH,
        )
        release.complete(success(oldRequest))
        assertRejected(pending.await(), PublicationResourceRejection.REVOKED)
        assertTrue(cache.entries().isEmpty())

        access.grant(PUBLICATION_ID, OTHER_DIGEST, setOf(ORIGIN), setOf("image/png"))
        assertTrue(access.fetch(PUBLICATION_ID, OTHER_DIGEST, RESOURCE_URL) is PublicationResourceFetchResult.Success)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun caller_cancellation_closes_work_releases_reservation_and_rejects_late_bytes() = runBlocking {
        val resolver = RecordingResolver { listOf(PUBLIC_IP) }
        val started = CompletableDeferred<HostHttpRequest>()
        val release = CompletableDeferred<HostHttpResponse>()
        val first = AtomicInteger()
        val transport = RecordingTransport { request, _ ->
            if (first.getAndIncrement() == 0) {
                started.complete(request)
                withContext(NonCancellable) { release.await() }
            } else {
                success(request)
            }
        }
        val cache = InMemoryResourceCache()
        val access = access(
            InMemoryGrantPersistence(),
            resolver,
            transport,
            cache,
            PublicationResourceBudgets(PNG.size, PNG.size.toLong()),
        )
        access.grant(PUBLICATION_ID, ORIGINAL_DIGEST, setOf(ORIGIN), setOf("image/png"))
        val pending = async { access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL) }
        val firstRequest = started.await()

        pending.cancel()
        release.complete(success(firstRequest))
        pending.cancelAndJoin()
        assertTrue(cache.entries().isEmpty())

        val retry = access.fetch(PUBLICATION_ID, ORIGINAL_DIGEST, RESOURCE_URL)
        assertTrue(retry is PublicationResourceFetchResult.Success)
        assertEquals(2, transport.requests.size)
    }

    private fun access(
        grants: PublicationGrantPersistence = InMemoryGrantPersistence(),
        resolver: RecordingResolver = RecordingResolver { listOf(PUBLIC_IP) },
        transport: RecordingTransport = RecordingTransport { request, _ -> success(request) },
        cache: InMemoryResourceCache = InMemoryResourceCache(),
        budgets: PublicationResourceBudgets = PublicationResourceBudgets(),
    ) = PublicationResourceAccess(grants, resolver, transport, cache, budgets)

    private fun persistence(roots: StorageRoots): PrivatePublicationGrantPersistence =
        PrivatePublicationGrantPersistence(
            QuotaFileStore(
                roots = roots,
                root = StorageRoot.NO_BACKUP,
                namespace = "publication-resource-grants",
                quota = StorageQuota(8L * 1024 * 1024, 1),
            ),
        )

    private fun assertIllegalGrant(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            throw AssertionError("Expected invalid grant to be rejected")
        } catch (_: IllegalArgumentException) {
            // Invalid authorization inputs cannot be persisted.
        }
    }

    private fun assertRejected(result: PublicationResourceFetchResult, status: PublicationResourceRejection) {
        assertEquals(PublicationResourceFetchResult.Rejected(status), result)
    }

    private class InMemoryGrantPersistence : PublicationGrantPersistence {
        private val values = linkedMapOf<String, StoredPublicationResourceGrant>()
        override fun load(): Map<String, StoredPublicationResourceGrant> = values.toMap()
        override fun replace(grants: Map<String, StoredPublicationResourceGrant>) {
            values.clear()
            values.putAll(grants)
        }
    }

    private class InMemoryResourceCache : PublicationResourceCache {
        private val values = linkedMapOf<String, CachedPublicationResource>()
        override fun read(relativePath: String): CachedPublicationResource? =
            values[relativePath]?.let { it.copy(bytes = it.bytes.copyOf()) }
        override fun write(relativePath: String, resource: CachedPublicationResource) {
            values[relativePath] = resource.copy(bytes = resource.bytes.copyOf())
        }
        override fun entries(): List<StoredFile> = values.map { (path, resource) -> StoredFile(path, resource.bytes.size.toLong()) }
        override fun delete(relativePath: String): Boolean = values.remove(relativePath) != null
    }

    private class RecordingResolver(
        private val answer: suspend (String) -> List<InetAddress>,
    ) : PublicationResourceAddressResolver {
        val hosts = mutableListOf<String>()
        override suspend fun resolve(hostname: String): List<InetAddress> {
            hosts += hostname
            return answer(hostname)
        }
    }

    private class RecordingTransport(
        private val answer: suspend (HostHttpRequest, List<InetAddress>) -> HostHttpResponse,
    ) : PublicationPinnedTransport {
        val requests = mutableListOf<HostHttpRequest>()
        val pins = mutableListOf<List<InetAddress>>()
        override suspend fun execute(request: HostHttpRequest, vettedAddresses: List<InetAddress>): HostHttpResponse {
            requests += request
            pins += vettedAddresses.toList()
            return answer(request, vettedAddresses)
        }
    }

    private companion object {
        const val PUBLICATION_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val ORIGINAL_DIGEST = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val OTHER_DIGEST = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        const val ORIGIN = "https://assets.example"
        const val RESOURCE_URL = "$ORIGIN/cover.png"
        val PUBLIC_IP: InetAddress = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
        val SECOND_PUBLIC_IP: InetAddress = InetAddress.getByAddress(byteArrayOf(8, 8, 4, 4))
        val PUBLIC_IPV6: InetAddress = InetAddress.getByAddress(
            byteArrayOf(0x20, 0x01, 0x48, 0x60, 0x48, 0x60, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x88.toByte(), 0x88.toByte()),
        )
        val PNG: ByteArray = createPng()

        fun success(request: HostHttpRequest) = response(request, 200, "content-type" to "image/png")

        fun response(
            request: HostHttpRequest,
            status: Int = 200,
            vararg headers: Pair<String, String>,
            bytes: ByteArray = PNG,
        ) = HostHttpResponse(
            status = status,
            finalUrl = request.url,
            headers = HostResponseHeaders.of(*headers),
            bytes = bytes,
        )

        fun createPng(width: Int = 1, height: Int = 1): ByteArray {
            val image = ByteArrayOutputStream()
            image.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            val header = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
                .putInt(width)
                .putInt(height)
                .put(byteArrayOf(8, 6, 0, 0, 0))
                .array()
            writeChunk(image, "IHDR", header)
            val compressed = ByteArrayOutputStream().also { bytes ->
                DeflaterOutputStream(bytes).use { it.write(byteArrayOf(0, 1, 2, 3, 4)) }
            }.toByteArray()
            writeChunk(image, "IDAT", compressed)
            writeChunk(image, "IEND", byteArrayOf())
            return image.toByteArray()
        }

        fun writeChunk(output: ByteArrayOutputStream, name: String, data: ByteArray) {
            val type = name.toByteArray(Charsets.US_ASCII)
            DataOutputStream(output).writeInt(data.size)
            output.write(type)
            output.write(data)
            val crc = CRC32().apply { update(type); update(data) }.value
            DataOutputStream(output).writeInt(crc.toInt())
        }
    }
}
