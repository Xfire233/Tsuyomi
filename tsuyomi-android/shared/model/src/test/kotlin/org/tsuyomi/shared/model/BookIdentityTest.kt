// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BookIdentityTest {
    @Test
    fun stableIdentityUsesOnlyProtocolTuple() {
        val identity = BookIdentity(sourceId = "org.tsuyomi.example", remoteBookId = "book-42")

        assertEquals("org.tsuyomi.example", identity.sourceId)
        assertEquals("book-42", identity.remoteBookId)
        assertEquals(identity, BookIdentity("org.tsuyomi.example", "book-42"))
    }

    @Test
    fun protocolBoundsAreStrict() {
        assertFailsWith<IllegalArgumentException> { BookIdentity("Uppercase", "book") }
        assertFailsWith<IllegalArgumentException> { BookIdentity("org.tsuyomi.example", "") }
        assertFailsWith<IllegalArgumentException> {
            BookIdentity("org.tsuyomi.example", "x".repeat(1025))
        }
    }
}
