/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.media.internal

import org.junit.Assert.assertEquals
import org.junit.Test
import org.tsuyomi.shared.sourcecontract.HttpsOrigin

class MediaOriginPolicyTest {
    private val policy = MediaOriginPolicy(
        setOf(HttpsOrigin("https://www.wenku8.net"), HttpsOrigin("https://pic.wenku8.com")),
    )

    @Test
    fun acceptsOnlyExactGrantedHttpsOrigins() {
        assertEquals(
            "https://pic.wenku8.com/files/article/image/12/1234/1234s.jpg",
            policy.requireAllowed("https://pic.wenku8.com/files/article/image/12/1234/1234s.jpg"),
        )
        assertFails(MediaFailure.ORIGIN_NOT_GRANTED) {
            policy.requireAllowed("https://evil.example/files/article/image/12/1234/1234s.jpg")
        }
        assertFails(MediaFailure.INVALID_URL) {
            policy.requireAllowed("http://pic.wenku8.com/files/article/image/12/1234/1234s.jpg")
        }
        assertFails(MediaFailure.ORIGIN_NOT_GRANTED) {
            policy.requireAllowed("https://pic.wenku8.com.evil.example/cover.jpg")
        }
    }

    private fun assertFails(expected: MediaFailure, block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull() as MediaLoadException
        assertEquals(expected, failure.failure)
    }
}
