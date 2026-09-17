/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.library

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.tsuyomi.shared.model.BookIdentity

class LibraryDragCoordinatorTest {
    @Test
    fun remoteMirrorAcceptsExactlyOneBookFromTheSameSource() {
        val destination = destinationFor(
            identities = setOf(BookIdentity("source-a", "book-1")),
            kind = LibraryShortcutDropKind.REMOTE_FOLDER,
            mirror = LibraryMirrorShortcut("source-a", "folder-1", "收藏", 1, false),
        )

        assertEquals(LibraryDropDestination.RemoteMirror("source-a", "folder-1", "收藏"), destination)
    }

    @Test
    fun remoteMirrorRejectsMultiBookAndCrossSourcePayloads() {
        val mirror = LibraryMirrorShortcut("source-a", "folder-1", "收藏", 1, false)

        assertNull(
            destinationFor(
                identities = setOf(BookIdentity("source-a", "book-1"), BookIdentity("source-a", "book-2")),
                kind = LibraryShortcutDropKind.REMOTE_FOLDER,
                mirror = mirror,
            ),
        )
        assertNull(
            destinationFor(
                identities = setOf(BookIdentity("source-b", "book-1")),
                kind = LibraryShortcutDropKind.REMOTE_FOLDER,
                mirror = mirror,
            ),
        )
    }

    @Test
    fun remoteRemoveRejectsMultiBookPayloads() {
        assertEquals(
            LibraryDropDestination.RemoteRemove,
            destinationFor(
                identities = setOf(BookIdentity("source-a", "book-1")),
                kind = LibraryShortcutDropKind.REMOTE_REMOVE,
            ),
        )
        assertNull(
            destinationFor(
                identities = setOf(BookIdentity("source-a", "book-1"), BookIdentity("source-a", "book-2")),
                kind = LibraryShortcutDropKind.REMOTE_REMOVE,
            ),
        )
    }

    @Test
    fun remoteMirrorRootAcceptsOneSameSourceBook() {
        val destination = destinationFor(
            identities = setOf(BookIdentity("source-a", "book-1")),
            kind = LibraryShortcutDropKind.REMOTE_MIRROR,
            mirror = LibraryMirrorShortcut("source-a", null, "网站书架", 3, false),
        )

        assertEquals(LibraryDropDestination.RemoteMirror("source-a", null, "网站书架"), destination)
    }

    @Test
    fun localCopyAcceptsSingleAndMultiBookPayloads() {
        assertEquals(
            LibraryDropDestination.LocalCopy,
            destinationFor(
                identities = setOf(BookIdentity("source-a", "book-1")),
                kind = LibraryShortcutDropKind.LOCAL_COPY,
            ),
        )
        assertEquals(
            LibraryDropDestination.LocalCopy,
            destinationFor(
                identities = setOf(BookIdentity("source-a", "book-1"), BookIdentity("source-a", "book-2")),
                kind = LibraryShortcutDropKind.LOCAL_COPY,
            ),
        )
    }

    @Test
    fun disposing_a_visible_target_removes_it_from_the_active_drag_snapshot() {
        val coordinator = LibraryDragCoordinator()
        val source = BookIdentity("source-a", "book-1")
        val mirror = LibraryMirrorShortcut("source-a", "folder-1", "收藏", 1, false)
        coordinator.registerSource("subject", Rect(0f, 0f, 20f, 20f))
        coordinator.registerShelf(coordinator.allocateShelfTargetId(), Rect(0f, 0f, 120f, 100f), true, null)
        coordinator.registerShortcut(
            id = "target",
            index = 0,
            kind = LibraryShortcutDropKind.REMOTE_FOLDER,
            bookIdentity = null,
            bounds = Rect(40f, 0f, 100f, 80f),
            mirror = mirror,
        )
        coordinator.start(
            subjectKey = "subject",
            localPosition = Offset(10f, 10f),
            payload = LibraryDragPayload.Books(setOf(source)),
            canRemove = false,
            libraryReorderSource = false,
        )
        coordinator.moveBy(Offset(50f, 0f))
        assertEquals(LibraryDropDestination.RemoteMirror("source-a", "folder-1", "收藏"), coordinator.externalDestination)

        coordinator.unregisterShortcut("target")

        assertNull(coordinator.externalDestination)
    }


    private fun destinationFor(
        identities: Set<BookIdentity>,
        kind: LibraryShortcutDropKind,
        mirror: LibraryMirrorShortcut? = null,
    ): LibraryDropDestination? {
        val coordinator = LibraryDragCoordinator()
        val sourceBounds = Rect(0f, 0f, 20f, 20f)
        val targetBounds = Rect(40f, 0f, 100f, 80f)
        coordinator.registerSource("subject", sourceBounds)
        coordinator.registerShelf(coordinator.allocateShelfTargetId(), Rect(0f, 0f, 120f, 100f), true, null)
        coordinator.registerShortcut("target", 0, kind, null, targetBounds, mirror)
        coordinator.start(
            subjectKey = "subject",
            localPosition = Offset(10f, 10f),
            payload = LibraryDragPayload.Books(identities),
            canRemove = false,
            libraryReorderSource = false,
        )
        coordinator.moveBy(Offset(50f, 0f))
        return coordinator.externalDestination
    }
}
