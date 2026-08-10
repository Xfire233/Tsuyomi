/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.tsuyomi.core.database.RemoteReconciliationState

class LocalRemoteRetryPresentationTest {
    @Test
    fun retryable_failure_explains_why_retry_is_disabled() {
        assertEquals(
            R.string.local_remote_retry_unavailable,
            localRemoteRetryUnavailableMessageRes(RemoteReconciliationState.UNRESOLVED, enabled = false),
        )
        assertEquals(
            R.string.local_remote_retry_unavailable,
            localRemoteRetryUnavailableMessageRes(RemoteReconciliationState.CANCELLED, enabled = false),
        )
    }

    @Test
    fun enabled_or_terminal_state_has_no_unavailable_explanation() {
        assertNull(localRemoteRetryUnavailableMessageRes(RemoteReconciliationState.UNRESOLVED, enabled = true))
        assertNull(localRemoteRetryUnavailableMessageRes(RemoteReconciliationState.CONFIRMED, enabled = false))
        assertNull(localRemoteRetryUnavailableMessageRes(null, enabled = false))
    }
}
