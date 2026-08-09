/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import org.tsuyomi.core.files.QuotaFileStore
import org.tsuyomi.core.files.StorageException
import org.tsuyomi.shared.sourcecontract.SourceId

/** Stores only the active verified archive for each source. Replacements are atomic in [QuotaFileStore]. */
class InstalledExtensionStore(private val files: QuotaFileStore) {
    fun writeActive(verified: VerifiedHxpPackage) {
        try {
            files.write(path(verified.manifest.sourceId), verified.archiveBytes)
        } catch (error: StorageException) {
            throw ExtensionInstallException(ExtensionInstallError.STORAGE_UNAVAILABLE, error)
        }
    }

    fun readActive(sourceId: SourceId): ByteArray? = try {
        files.read(path(sourceId))
    } catch (error: StorageException) {
        throw ExtensionInstallException(ExtensionInstallError.STORAGE_UNAVAILABLE, error)
    }

    fun remove(sourceId: SourceId): Boolean = files.delete(path(sourceId))

    fun installedSourceIds(): List<SourceId> = files.entries()
        .mapNotNull { stored ->
            stored.relativePath.removePrefix("active/")
                .removeSuffix(".hxp")
                .takeIf { stored.relativePath == "active/$it.hxp" }
                ?.let { runCatching { SourceId(it) }.getOrNull() }
        }
        .sortedBy { it.value }

    private fun path(sourceId: SourceId): String = "active/${sourceId.value}.hxp"
}

enum class ExtensionInstallError {
    STORAGE_UNAVAILABLE,
    APPROVAL_MISMATCH,
    DOWNGRADE_REQUIRES_CONFIRMATION,
    REPLAY_REJECTED,
    KEY_ROTATION_NOT_AUTHORIZED,
    CAPABILITY_GRANT_REQUIRED,
    INSTALLED_PACKAGE_INVALID,
}

class ExtensionInstallException(
    val error: ExtensionInstallError,
    cause: Throwable? = null,
) : Exception(error.name, cause)
