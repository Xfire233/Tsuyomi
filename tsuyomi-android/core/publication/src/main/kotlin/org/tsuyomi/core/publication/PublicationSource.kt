/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.io.Closeable
import java.io.InputStream

/** Private original access supplied by the publication owner; no provider URI or filesystem path. */
interface PublicationSource {
    val sha256: String
    val sizeBytes: Long

    fun openInputStream(): InputStream
    fun openArchive(): PublicationZipArchive
}

/** A single ZIP central-directory entry. [name] is consumed only by the publication parser. */
data class PublicationZipEntry(
    val name: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val compressionMethod: Int,
    val isDirectory: Boolean,
)

/** Path-free, reopenable view over the owner's private ZIP file. */
interface PublicationZipArchive : Closeable {
    fun entries(): Sequence<PublicationZipEntry>
    fun openEntry(name: String): InputStream

    companion object {
        const val STORED = 0
        const val DEFLATED = 8
    }
}
