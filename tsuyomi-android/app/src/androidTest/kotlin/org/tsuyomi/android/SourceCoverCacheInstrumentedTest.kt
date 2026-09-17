/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.core.media.api.CoverRequest
import org.tsuyomi.core.media.api.CoverUiState
import org.tsuyomi.core.media.api.FallbackSpec

@RunWith(AndroidJUnit4::class)
internal class SourceCoverCacheInstrumentedTest : SourceFlowInstrumentedTestFixture() {
    @Test
    fun ready_covers_are_reused_but_revoked_or_absent_sources_cannot_fetch_through_retained_references() = runBlocking {
        clearCoverCaches()
        val packageInfo = installFixture()
        val encoded = encodedCover()
        val trusted = java.util.concurrent.atomic.AtomicBoolean(true)
        val cache = SourceCoverCache(context, isPackageTrusted = { trusted.get() })
        putCredential(packageInfo.manifest.sourceId.value)
        Phase2SourceGateway.useCredentialCoverFixture(encoded)
        try {
            val first = cache.resolve(packageInfo)
            val repository = requireNotNull(first.repository)
            val request = CoverRequest(
                sourceId = requireNotNull(first.sourceId),
                packageRevision = requireNotNull(first.packageRevision),
                credentialRevision = requireNotNull(first.credentialRevision),
                transportUrl = "https://www.wenku8.net/files/article/image/12/1234/1234s.jpg",
                targetWidthPx = 4,
                targetHeightPx = 6,
                fallback = FallbackSpec("雾港纪事", "Wenku8"),
            )

            repository.observe(request).collect {}
            assertEquals(1, Phase2SourceGateway.acceptedCredentialCoverCount())

            val reentered = cache.resolve(packageInfo)
            assertSame(repository, reentered.repository)
            val reentryStates = mutableListOf<CoverUiState>()
            requireNotNull(reentered.repository).observe(request).collect { reentryStates += it }
            assertEquals(1, reentryStates.size)
            assertTrue(reentryStates.single() is CoverUiState.Ready)
            assertEquals(1, Phase2SourceGateway.acceptedCredentialCoverCount())

            trusted.set(false)
            val revokedStates = mutableListOf<CoverUiState>()
            repository.observe(request.copy(transportUrl = "${request.transportUrl}?revoked=1"))
                .collect { revokedStates += it }
            assertTrue(revokedStates.none { it is CoverUiState.Ready })
            assertEquals(1, Phase2SourceGateway.acceptedCredentialCoverCount())
            assertEquals(null, cache.resolve(packageInfo).repository)

            trusted.set(true)
            val reactivated = requireNotNull(cache.resolve(packageInfo).repository)
            assertEquals(null, cache.resolve(null).repository)
            val absentStates = mutableListOf<CoverUiState>()
            reactivated.observe(request.copy(transportUrl = "${request.transportUrl}?absent=1"))
                .collect { absentStates += it }
            assertTrue(absentStates.none { it is CoverUiState.Ready })
            assertEquals(1, Phase2SourceGateway.acceptedCredentialCoverCount())
        } finally {
            Phase2SourceGateway.clearCredentialCoverFixture()
            clearCoverCaches()
        }
    }

    @Test
    fun in_flight_cover_cannot_regain_authority_after_identical_partition_reactivation() = runBlocking {
        clearCoverCaches()
        val packageInfo = installFixture()
        putCredential(packageInfo.manifest.sourceId.value)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val trusted = java.util.concurrent.atomic.AtomicBoolean(true)
        val cache = SourceCoverCache(context, isPackageTrusted = { trusted.get() })
        Phase2SourceGateway.useCredentialCoverFixture(encodedCover()) {
            started.complete(Unit)
            release.await()
        }
        try {
            val original = cache.resolve(packageInfo)
            val oldRepository = requireNotNull(original.repository)
            val request = CoverRequest(
                sourceId = requireNotNull(original.sourceId),
                packageRevision = requireNotNull(original.packageRevision),
                credentialRevision = requireNotNull(original.credentialRevision),
                transportUrl = "https://www.wenku8.net/files/article/image/12/1234/1234s.jpg",
                targetWidthPx = 4,
                targetHeightPx = 6,
                fallback = FallbackSpec("撤销中的封面", "Wenku8"),
            )
            val oldStates = mutableListOf<CoverUiState>()
            val pending = async { oldRepository.observe(request).collect { oldStates += it } }
            withTimeout(5_000) { started.await() }
            trusted.set(false)
            assertEquals(null, cache.resolve(packageInfo).repository)
            trusted.set(true)
            val currentRepository = requireNotNull(cache.resolve(packageInfo).repository)
            release.complete(Unit)
            withTimeout(5_000) { pending.await() }
            assertTrue(oldStates.none { it is CoverUiState.Ready })
            val staleStates = mutableListOf<CoverUiState>()
            oldRepository.observe(request.copy(transportUrl = "${request.transportUrl}?stale=1"))
                .collect { staleStates += it }
            assertTrue(staleStates.none { it is CoverUiState.Ready })
            assertEquals(1, Phase2SourceGateway.acceptedCredentialCoverCount())
            val currentStates = mutableListOf<CoverUiState>()
            currentRepository.observe(request).collect { currentStates += it }
            assertTrue(currentStates.last() is CoverUiState.Ready)
            assertEquals(2, Phase2SourceGateway.acceptedCredentialCoverCount())
        } finally {
            release.complete(Unit)
            cache.clear()
            Phase2SourceGateway.clearCredentialCoverFixture()
            clearCoverCaches()
        }
    }

    private fun encodedCover(): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(20, 40, 60))
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        bitmap.recycle()
        output.toByteArray()
    }

    private fun clearCoverCaches() {
        context.cacheDir.listFiles()
            ?.filter { file -> file.name.startsWith("cover-") }
            ?.forEach(java.io.File::deleteRecursively)
    }
}