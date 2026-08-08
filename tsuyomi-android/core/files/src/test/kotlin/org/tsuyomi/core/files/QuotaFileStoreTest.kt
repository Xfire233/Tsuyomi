/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.files

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuotaFileStoreTest {
    private lateinit var temporaryRoot: File
    private lateinit var roots: StorageRoots

    @Before
    fun setUp() {
        temporaryRoot = Files.createTempDirectory("tsuyomi-files-test").toFile()
        roots = StorageRoots(
            noBackupDirectory = File(temporaryRoot, "no-backup"),
            cacheDirectory = File(temporaryRoot, "cache"),
        )
    }

    @After
    fun tearDown() {
        temporaryRoot.deleteRecursively()
    }

    @Test
    fun cacheCleanupEvictsLeastRecentlyUsedEntryDeterministically() {
        val store = QuotaFileStore(
            roots = roots,
            root = StorageRoot.CACHE,
            namespace = "chapter-cache",
            quota = StorageQuota(maxBytes = 8, maxEntries = 2),
            clockMillis = { 10L },
        )

        store.write("a", byteArrayOf(1, 1, 1, 1))
        store.write("b", byteArrayOf(2, 2, 2, 2))
        assertArrayEquals(byteArrayOf(1, 1, 1, 1), store.read("a"))
        store.write("c", byteArrayOf(3, 3, 3, 3))

        val remaining = store.entries()
        assertEquals(listOf("a", "c"), remaining.map(StoredFile::relativePath))
        assertEquals(8L, remaining.sumOf(StoredFile::byteCount))
        assertEquals(null, store.read("b"))
    }

    @Test
    fun writeFailsWithoutReplacingDataWhenUndeletableEntryPreventsQuota() {
        val store = QuotaFileStore(
            roots = roots,
            root = StorageRoot.CACHE,
            namespace = "chapter-cache",
            quota = StorageQuota(maxBytes = 8, maxEntries = 2),
            deleteFile = { file -> file.name != "pinned" && file.delete() },
        )
        val originalBytes = byteArrayOf(1, 1, 1, 1)
        val pinnedBytes = byteArrayOf(2, 2, 2, 2)
        store.write("target", originalBytes)
        store.write("pinned", pinnedBytes)

        assertStorageFailure { store.write("target", ByteArray(8)) }

        assertEquals(
            listOf(StoredFile("pinned", 4), StoredFile("target", 4)),
            store.entries(),
        )
        assertArrayEquals(originalBytes, store.read("target"))
        assertArrayEquals(pinnedBytes, store.read("pinned"))
    }

    @Test
    fun cacheQuotaFailureMayDiscardOnlyDisposableEntriesAndKeepsPreviousTarget() {
        val store = QuotaFileStore(
            roots = roots,
            root = StorageRoot.CACHE,
            namespace = "chapter-cache-partial",
            quota = StorageQuota(maxBytes = 12, maxEntries = 3),
            deleteFile = { file -> file.name != "pinned" && file.delete() },
        )
        val target = byteArrayOf(1, 1, 1, 1)
        store.write("target", target)
        store.write("old", byteArrayOf(2, 2, 2, 2))
        store.write("pinned", byteArrayOf(3, 3, 3, 3))

        assertStorageFailure { store.write("target", ByteArray(12)) }

        assertArrayEquals(target, store.read("target"))
        assertEquals(null, store.read("old"))
        assertArrayEquals(byteArrayOf(3, 3, 3, 3), store.read("pinned"))
    }

    @Test
    fun durableQuotaFailureNeverEvictsExistingEntries() {
        val store = QuotaFileStore(
            roots = roots,
            root = StorageRoot.NO_BACKUP,
            namespace = "source-state-durable",
            quota = StorageQuota(maxBytes = 12, maxEntries = 3),
        )
        val target = byteArrayOf(1, 1, 1, 1)
        store.write("target", target)
        store.write("old", byteArrayOf(2, 2, 2, 2))
        store.write("pinned", byteArrayOf(3, 3, 3, 3))

        assertStorageFailure { store.write("target", ByteArray(12)) }

        assertArrayEquals(target, store.read("target"))
        assertArrayEquals(byteArrayOf(2, 2, 2, 2), store.read("old"))
        assertArrayEquals(byteArrayOf(3, 3, 3, 3), store.read("pinned"))
    }


    @Test
    fun durableCleanupReportsOverageWithoutEvictingData() {
        val store = QuotaFileStore(
            roots = roots,
            root = StorageRoot.NO_BACKUP,
            namespace = "source-state-cleanup",
            quota = StorageQuota(maxBytes = 4, maxEntries = 1),
        )
        store.write("kept", byteArrayOf(1, 1, 1, 1))
        File(store.directory(), "legacy").writeBytes(byteArrayOf(2, 2, 2, 2))

        val result = store.cleanup()

        assertEquals(CleanupResult(0, 0, 2, 8), result)
        assertArrayEquals(byteArrayOf(1, 1, 1, 1), store.read("kept"))
        assertArrayEquals(byteArrayOf(2, 2, 2, 2), store.read("legacy"))
    }
    @Test
    fun rejectsEscapingPathsBeforeAnyWrite() {
        val store = QuotaFileStore(roots, StorageRoot.NO_BACKUP, "source-state", StorageQuota(64, 2))

        assertInvalidPath { store.write("../outside", byteArrayOf(1)) }
        assertInvalidPath { store.write("nested/../../outside", byteArrayOf(1)) }
        assertInvalidPath { store.read("./outside") }
        assertFalse(File(temporaryRoot, "outside").exists())
    }

    @Test
    fun noBackupStoreAlwaysUsesConfiguredNoBackupRoot() {
        val store = QuotaFileStore(roots, StorageRoot.NO_BACKUP, "source-state", StorageQuota(64, 2))
        store.write("state", byteArrayOf(7))

        assertTrue(store.directory().canonicalPath.startsWith(roots.noBackupDirectory.canonicalPath))
        assertFalse(store.directory().canonicalPath.startsWith(roots.cacheDirectory.canonicalPath))
    }

    private fun assertInvalidPath(action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected invalid path rejection")
        } catch (_: IllegalArgumentException) {
            // Expected: public path input may not escape the namespace.
        }
    }

    private fun assertStorageFailure(action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected storage quota failure")
        } catch (_: StorageException) {
            // Expected: the write cannot establish the requested quota invariant.
        }
    }
}
