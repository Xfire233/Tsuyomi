/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.files

import android.content.Context
import java.io.File

enum class StorageRoot {
    NO_BACKUP,
    CACHE,
}

/** Explicit roots keep durable private state out of Android automatic backup. */
data class StorageRoots(
    val noBackupDirectory: File,
    val cacheDirectory: File,
) {
    init {
        require(noBackupDirectory.isDirectory || noBackupDirectory.mkdirs()) { "Cannot create no-backup root" }
        require(cacheDirectory.isDirectory || cacheDirectory.mkdirs()) { "Cannot create cache root" }
    }

    fun directory(root: StorageRoot): File = when (root) {
        StorageRoot.NO_BACKUP -> noBackupDirectory
        StorageRoot.CACHE -> cacheDirectory
    }

    companion object {
        fun from(context: Context): StorageRoots = StorageRoots(
            noBackupDirectory = context.noBackupFilesDir,
            cacheDirectory = context.cacheDir,
        )
    }
}

data class StorageQuota(
    val maxBytes: Long,
    val maxEntries: Int,
) {
    init {
        require(maxBytes > 0) { "Byte quota must be positive" }
        require(maxEntries > 0) { "Entry quota must be positive" }
    }
}

data class StoredFile(
    val relativePath: String,
    val byteCount: Long,
)

data class CleanupResult(
    val removedEntries: Int,
    val removedBytes: Long,
    val remainingEntries: Int,
    val remainingBytes: Long,
)

class StorageException internal constructor(message: String, cause: Throwable? = null) : Exception(message, cause)
