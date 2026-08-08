/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.files

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.max

/**
 * A private, per-namespace file store with explicit byte/entry limits. Automatic LRU eviction is
 * limited to [StorageRoot.CACHE], where entries are disposable; durable roots fail before deleting
 * existing data. The store never resolves caller paths outside its namespace.
 */
class QuotaFileStore(
    roots: StorageRoots,
    root: StorageRoot,
    namespace: String,
    private val quota: StorageQuota,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val deleteFile: (File) -> Boolean = File::delete,
) {
    private val namespaceDirectory: File
    private var lastAccessMillis: Long
    private val storageRoot = root

    init {
        require(isSinglePathSegment(namespace)) { "Invalid storage namespace" }
        val rootDirectory = roots.directory(root).canonicalFile
        namespaceDirectory = File(rootDirectory, namespace).canonicalFile
        require(isDescendant(rootDirectory, namespaceDirectory)) { "Invalid storage namespace" }
        require(namespaceDirectory.isDirectory || namespaceDirectory.mkdirs()) { "Cannot create storage namespace" }
        lastAccessMillis = scanEntries().maxOfOrNull { it.file.lastModified() } ?: 0L
    }

    @Synchronized
    @Throws(StorageException::class)
    fun write(relativePath: String, bytes: ByteArray): StoredFile {
        if (bytes.size.toLong() > quota.maxBytes) {
            throw StorageException("File exceeds configured byte quota")
        }
        val target = resolve(relativePath)
        val parent = target.parentFile ?: throw StorageException("Invalid storage path")
        if (!parent.isDirectory && !parent.mkdirs()) throw StorageException("Cannot create storage directory")
        if (!isDescendant(namespaceDirectory, parent.canonicalFile)) {
            throw StorageException("Invalid storage path")
        }

        val temporary = File(parent, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            touch(temporary)
            cleanupForWrite(target, temporary, bytes.size.toLong())
            moveAtomically(temporary, target)
        } catch (error: IOException) {
            throw StorageException("Unable to write private storage", error)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        return StoredFile(relativePath, bytes.size.toLong())
    }

    @Synchronized
    @Throws(StorageException::class)
    fun read(relativePath: String): ByteArray? {
        val target = resolve(relativePath)
        if (!target.exists()) return null
        if (!target.isFile || !isDescendant(namespaceDirectory, target.canonicalFile)) {
            throw StorageException("Invalid storage path")
        }
        return try {
            target.readBytes().also { touch(target) }
        } catch (error: IOException) {
            throw StorageException("Unable to read private storage", error)
        }
    }

    @Synchronized
    fun delete(relativePath: String): Boolean {
        val target = resolve(relativePath)
        return target.exists() && target.delete()
    }

    @Synchronized
    fun clear(): CleanupResult {
        val entries = scanEntries()
        var removedBytes = 0L
        var removedEntries = 0
        entries.forEach {
            if (it.file.delete()) {
                removedBytes += it.bytes
                removedEntries++
            }
        }
        namespaceDirectory.walkBottomUp()
            .filter { it != namespaceDirectory && it.isDirectory && isDescendant(namespaceDirectory, it.canonicalFile) }
            .forEach(File::delete)
        val remaining = scanEntries()
        return CleanupResult(
            removedEntries = removedEntries,
            removedBytes = removedBytes,
            remainingEntries = remaining.size,
            remainingBytes = remaining.sumOf { it.bytes },
        )
    }
    /** Automatically evicts LRU entries only from cache storage. Durable roots are diagnostic-only. */
    @Synchronized
    fun cleanup(): CleanupResult {
        val entries = scanEntries()
        val remainingBytes = entries.sumOf { it.bytes }
        if (storageRoot != StorageRoot.CACHE) {
            return CleanupResult(0, 0, entries.size, remainingBytes)
        }
        return cleanupEntries(entries, remainingBytes, entries.size)
    }

    /**
     * Reserves enough capacity for a replacement before it becomes visible. This keeps the
     * previous target intact if undeletable entries prevent the resulting store from fitting.
     */
    private fun cleanupForWrite(target: File, temporary: File, targetBytes: Long) {
        val entries = scanEntries().filter { entry ->
            entry.file.canonicalFile != target && entry.file.canonicalFile != temporary
        }
        val remainingBytes = entries.sumOf { it.bytes } + targetBytes
        val remainingEntries = entries.size + 1
        if (remainingBytes <= quota.maxBytes && remainingEntries <= quota.maxEntries) return
        if (storageRoot != StorageRoot.CACHE) {
            throw StorageException("Durable storage quota cannot be satisfied without data loss")
        }

        val result = cleanupEntries(entries, remainingBytes, remainingEntries)
        if (result.remainingBytes > quota.maxBytes || result.remainingEntries > quota.maxEntries) {
            throw StorageException("Storage quota cannot be satisfied")
        }
    }

    private fun cleanupEntries(
        entries: List<Entry>,
        remainingBytes: Long,
        remainingEntries: Int,
    ): CleanupResult {
        var currentBytes = remainingBytes
        var currentEntries = remainingEntries
        var removedBytes = 0L
        var removedEntries = 0
        for (entry in entries.sortedWith(compareBy<Entry> { it.file.lastModified() }.thenBy { it.relativePath })) {
            if (currentBytes <= quota.maxBytes && currentEntries <= quota.maxEntries) break
            if (deleteFile(entry.file)) {
                currentBytes -= entry.bytes
                currentEntries--
                removedBytes += entry.bytes
                removedEntries++
            }
        }
        return CleanupResult(removedEntries, removedBytes, currentEntries, currentBytes)
    }

    @Synchronized
    fun entries(): List<StoredFile> = scanEntries()
        .sortedBy { it.relativePath }
        .map { StoredFile(it.relativePath, it.bytes) }

    /** Exposed for diagnostics/tests only; callers must not construct paths below this directory. */
    fun directory(): File = namespaceDirectory

    private fun resolve(relativePath: String): File {
        require(isSafeRelativePath(relativePath)) { "Invalid storage path" }
        val target = File(namespaceDirectory, relativePath).canonicalFile
        require(isDescendant(namespaceDirectory, target)) { "Invalid storage path" }
        return target
    }

    private fun touch(file: File) {
        lastAccessMillis = max(lastAccessMillis + 1L, clockMillis())
        if (!file.setLastModified(lastAccessMillis)) throw StorageException("Unable to update storage access time")
    }

    private fun scanEntries(): List<Entry> = namespaceDirectory.walkTopDown()
        .filter { it.isFile && isDescendant(namespaceDirectory, it.canonicalFile) }
        .map { file ->
            Entry(
                file = file,
                relativePath = file.canonicalFile.relativeTo(namespaceDirectory).invariantSeparatorsPath,
                bytes = file.length(),
            )
        }
        .toList()

    private fun moveAtomically(from: File, to: File) {
        try {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: IOException) {
            throw StorageException("Unable to atomically replace private storage", error)
        }
    }

    private data class Entry(val file: File, val relativePath: String, val bytes: Long)
}

internal fun isSafeRelativePath(path: String): Boolean {
    if (path.isBlank() || File(path).isAbsolute) return false
    return path.split('/', '\\').all { segment -> segment.isNotEmpty() && segment != "." && segment != ".." }
}

private fun isSinglePathSegment(value: String): Boolean =
    value.isNotBlank() && isSafeRelativePath(value) && '/' !in value && '\\' !in value

private fun isDescendant(parent: File, candidate: File): Boolean {
    val parentPath = parent.path.trimEnd(File.separatorChar) + File.separatorChar
    return candidate.path == parent.path || candidate.path.startsWith(parentPath)
}
