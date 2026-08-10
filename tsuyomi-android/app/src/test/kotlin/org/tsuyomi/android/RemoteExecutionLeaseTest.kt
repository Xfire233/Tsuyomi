/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteExecutionLeaseTest {
    private val lease = RemoteExecutionLease(
        packageSha256 = "package-a",
        packageVersion = "0.2.0",
        capabilitySetFingerprint = "capability-a",
        sourceGeneration = 7,
        ownerGeneration = 11,
    )

    @Test
    fun every_bound_dimension_must_still_match() {
        assertTrue(lease.matches("package-a", "0.2.0", "0.2.0", "capability-a", 7, 11))
        assertFalse(lease.matches("package-b", "0.2.0", "0.2.0", "capability-a", 7, 11))
        assertFalse(lease.matches("package-a", "0.2.1", "0.2.0", "capability-a", 7, 11))
        assertFalse(lease.matches("package-a", "0.2.0", "0.2.1", "capability-a", 7, 11))
        assertFalse(lease.matches("package-a", "0.2.0", "0.2.0", "capability-b", 7, 11))
        assertFalse(lease.matches("package-a", "0.2.0", "0.2.0", "capability-a", 8, 11))
        assertFalse(lease.matches("package-a", "0.2.0", "0.2.0", "capability-a", 7, 12))
    }

    @Test
    fun missing_active_state_never_matches() {
        assertFalse(lease.matches(null, "0.2.0", "0.2.0", "capability-a", 7, 11))
        assertFalse(lease.matches("package-a", null, "0.2.0", "capability-a", 7, 11))
        assertFalse(lease.matches("package-a", "0.2.0", null, "capability-a", 7, 11))
        assertFalse(lease.matches("package-a", "0.2.0", "0.2.0", null, 7, 11))
        assertFalse(lease.matches("package-a", "0.2.0", "0.2.0", "capability-a", null, 11))
    }
}
