/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayWriteArbiterTest {
    @Test
    fun staleCompletionCannotOverrideNewerWriteState() {
        val arbiter = DisplayWriteArbiter()
        val stale = arbiter.begin("profile:AUTO")
        val current = arbiter.begin("profile:EINK")

        arbiter.fail(current)
        arbiter.succeed(stale)

        assertTrue(arbiter.hasFailure)
        assertEquals("profile:EINK", arbiter.retryKey)
        assertEquals(1, arbiter.failureSequence)
    }

    @Test
    fun staleFailureCannotReplaceNewerSuccess() {
        val arbiter = DisplayWriteArbiter()
        val stale = arbiter.begin("scheme:DARK")
        val current = arbiter.begin("scheme:LIGHT")

        arbiter.succeed(current)
        arbiter.fail(stale)

        assertFalse(arbiter.hasFailure)
        assertNull(arbiter.retryKey)
        assertEquals(0, arbiter.failureSequence)
    }

    @Test
    fun savedStateRetainsRetryOrderAndGenerations() {
        val arbiter = DisplayWriteArbiter()
        arbiter.fail(arbiter.begin("profile:EINK"))
        arbiter.fail(arbiter.begin("scheme:DARK"))

        val restored = DisplayWriteArbiter.restore(arbiter.savedValues())
        restored.acknowledge()
        val nextProfile = restored.begin("profile:STANDARD")

        assertEquals("scheme:DARK", restored.retryKey)
        assertEquals(3, restored.failureSequence)
        assertEquals(2L, nextProfile.generation)
    }
}
