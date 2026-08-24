/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import org.tsuyomi.source.extensionmanager.InMemoryPublisherKeyStore
import org.tsuyomi.source.extensionmanager.PublisherKeyResolver
import org.tsuyomi.source.extensiontestkit.Phase2TestPublisher

/** Debug builds alone trust the public, deterministic Phase 2 fixture publisher. */
internal object Phase2LocalTrust {
    fun resolver(): PublisherKeyResolver = InMemoryPublisherKeyStore(listOf(Phase2TestPublisher.key))
}
