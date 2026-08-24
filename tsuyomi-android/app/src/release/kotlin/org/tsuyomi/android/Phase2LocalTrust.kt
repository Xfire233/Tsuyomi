/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import org.tsuyomi.source.extensionmanager.InMemoryPublisherKeyStore
import org.tsuyomi.source.extensionmanager.PublisherKeyResolver

/** Release builds contain no built-in publisher trust; fixture keys never become production trust. */
internal object Phase2LocalTrust {
    fun resolver(): PublisherKeyResolver = InMemoryPublisherKeyStore(emptyList())
}
