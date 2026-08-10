/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

internal data class RemoteExecutionLease(
    val packageSha256: String,
    val packageVersion: String,
    val capabilitySetFingerprint: String,
    val sourceGeneration: Long,
    val ownerGeneration: Long,
) {
    fun matches(
        packageSha256: String?,
        packageVersion: String?,
        verifiedSourceVersion: String?,
        capabilitySetFingerprint: String?,
        sourceGeneration: Long?,
        ownerGeneration: Long,
    ): Boolean =
        this.packageSha256 == packageSha256 &&
            this.packageVersion == packageVersion &&
            this.packageVersion == verifiedSourceVersion &&
            this.capabilitySetFingerprint == capabilitySetFingerprint &&
            this.sourceGeneration == sourceGeneration &&
            this.ownerGeneration == ownerGeneration
}
