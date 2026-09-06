/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import org.junit.Assert.assertEquals
import org.junit.Test
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.RemoteTarget
import org.tsuyomi.shared.sourcecontract.SourceBookSummary

class RemoteMirrorMembershipTest {
    @Test
    fun missingMembershipUsesVisibleDefaultTargetWithoutOverwritingExplicitFolders() {
        val targets = listOf(
            RemoteTarget("0", "默认书架", null, "folder"),
            RemoteTarget("1", "第1组书架", null, "folder"),
        )
        val books = listOf(
            book("default-book", null),
            book("folder-book", "1"),
        )

        val normalized = normalizeRemoteTargetMembership(books, targets)

        assertEquals(listOf("0", "1"), normalized.map(SourceBookSummary::remoteTargetId))
    }

    @Test
    fun missingMembershipRemainsUnknownWhenNoTargetsExist() {
        val book = book("unclassified", null)

        assertEquals(listOf(book), normalizeRemoteTargetMembership(listOf(book), emptyList()))
    }

    private fun book(remoteBookId: String, targetId: String?) = SourceBookSummary(
        identity = BookIdentity("org.tsuyomi.wenku8", remoteBookId),
        title = remoteBookId,
        author = null,
        coverUrl = null,
        canonicalUrl = "https://www.wenku8.net/book/$remoteBookId.htm",
        remoteTargetId = targetId,
    )
}
