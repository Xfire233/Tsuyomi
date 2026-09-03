/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import org.tsuyomi.source.extensionmanager.InMemoryPublisherKeyStore
import org.tsuyomi.source.extensionmanager.PublisherKeyResolver
import org.tsuyomi.source.extensiontestkit.Phase2TestPublisher

/** Online development accepts the installed signed development source; it bundles no fixture content or transport. */
internal object Phase2LocalTrust {
    fun resolver(): PublisherKeyResolver = InMemoryPublisherKeyStore(listOf(Phase2TestPublisher.key))
}
