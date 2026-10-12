// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BookIdentityTest {
    @Test
    fun remoteIdentityUsesOnlyTheProtocolTuple() {
        val identity = BookIdentity.Remote(sourceId = "org.tsuyomi.example", remoteBookId = "book-42")

        assertEquals("org.tsuyomi.example", identity.sourceId)
        assertEquals("book-42", identity.remoteBookId)
        assertEquals(identity, BookIdentity.Remote("org.tsuyomi.example", "book-42"))
    }

    @Test
    fun remoteProtocolBoundsAreStrict() {
        assertFailsWith<IllegalArgumentException> { BookIdentity.Remote("Uppercase", "book") }
        assertFailsWith<IllegalArgumentException> { BookIdentity.Remote("org.tsuyomi.example", "") }
        assertFailsWith<IllegalArgumentException> {
            BookIdentity.Remote("org.tsuyomi.example", "x".repeat(1025))
        }
    }

    @Test
    fun localPublicationIdsAreCanonicalUuidAndStorageKeysAreOriginQualified() {
        val first = BookIdentity.Local("f2b8ef64-9375-4fc1-b7c9-274b4beafed1")
        val second = BookIdentity.Local("f2b8ef64-9375-4fc1-b7c9-274b4beafed2")
        val remote = BookIdentity.Remote("org.tsuyomi.example", "book")

        assertEquals(first, BookIdentity.Local(first.publicationId))
        assertEquals(false, first.storageKey == second.storageKey)
        assertEquals(false, first.storageKey == remote.storageKey)
        assertEquals(listOf(remote, first, second), listOf(second, first, remote).sorted())
        assertFailsWith<IllegalArgumentException> { BookIdentity.Local("F2B8EF64-9375-4FC1-B7C9-274B4BEAFED1") }
        assertFailsWith<IllegalArgumentException> { BookIdentity.Local("not-a-uuid") }
    }
}
