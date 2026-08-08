/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.display

import android.os.Build
import java.util.Locale

/** Provides a local-only classification of the current device. */
fun interface DeviceClassifier {
    fun classify(): DeviceClassification
}

/**
 * Conservative shipped signatures for Android E-ink product families.
 *
 * Matching requires both a known manufacturer and an explicit E-ink family marker. The raw
 * manufacturer and model never leave this process and are not retained in [DeviceClassification].
 */
class LocalDeviceClassifier(
    private val manufacturer: String = Build.MANUFACTURER,
    private val model: String = Build.MODEL,
) : DeviceClassifier {
    override fun classify(): DeviceClassification {
        val normalizedManufacturer = manufacturer.normalized()
        val normalizedModel = model.normalized()
        val label = knownFamilyLabel(normalizedManufacturer, normalizedModel)
        return DeviceClassification(
            recognizedEInk = label != null,
            deviceLabel = label,
        )
    }

    private fun knownFamilyLabel(manufacturer: String, model: String): String? = when {
        manufacturer in ONYX_MANUFACTURERS && model.containsAnyToken(ONYX_FAMILY_MARKERS) -> "BOOX"
        manufacturer in MEEBOOK_MANUFACTURERS && model.containsToken("meebook") -> "Meebook"
        manufacturer in BIGME_MANUFACTURERS && model.containsToken("bigme") -> "Bigme"
        manufacturer in IREADER_MANUFACTURERS && model.containsToken("ireader") -> "iReader"
        manufacturer in HANVON_MANUFACTURERS && model.containsAnyToken(HANVON_FAMILY_MARKERS) -> "Hanvon"
        else -> null
    }

    private companion object {
        val ONYX_MANUFACTURERS = setOf("onyx", "onyx international", "boox")
        val MEEBOOK_MANUFACTURERS = setOf("boyue", "meebook")
        val BIGME_MANUFACTURERS = setOf("bigme", "bigme technology")
        val IREADER_MANUFACTURERS = setOf("ireader", "zhangyue", "beijing zhangyue")
        val HANVON_MANUFACTURERS = setOf("hanvon", "hanwang")
        val ONYX_FAMILY_MARKERS = setOf("boox", "note", "tab", "leaf", "palma", "poke", "go")
        val HANVON_FAMILY_MARKERS = setOf("clear", "n10", "n7", "n8")
    }
}

private fun String.normalized(): String = trim().lowercase(Locale.ROOT)

private fun String.containsAnyToken(markers: Set<String>): Boolean = markers.any(::containsToken)

private fun String.containsToken(marker: String): Boolean =
    Regex("(?:^|[^a-z0-9])${Regex.escape(marker)}(?:$|[^a-z0-9])").containsMatchIn(this)
