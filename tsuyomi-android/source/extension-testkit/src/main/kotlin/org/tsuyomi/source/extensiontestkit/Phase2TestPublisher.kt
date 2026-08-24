/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensiontestkit

import org.tsuyomi.source.extensionmanager.PublisherKey
import org.tsuyomi.source.extensionmanager.PublisherTrust

/**
 * Deterministic public key for Phase 2 fixtures only. Production trust stores must not register it.
 * The matching seed is intentionally public in tsuyomi-extensions/tools/build-fixture.mjs.
 */
object Phase2TestPublisher {
    const val KEY_ID = "tsuyomi-phase2-fixture"
    const val PUBLIC_KEY_HEX = "79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664"

    val key: PublisherKey = PublisherKey(
        keyId = KEY_ID,
        publicKey = PUBLIC_KEY_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
        trust = PublisherTrust.BUILT_IN_TEST,
    )
}
