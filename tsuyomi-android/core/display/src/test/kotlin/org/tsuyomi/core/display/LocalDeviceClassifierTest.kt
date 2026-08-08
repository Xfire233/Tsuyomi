/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDeviceClassifierTest {
    @Test
    fun recognizesKnownSignatureWithoutRetainingRawModel() {
        val result = LocalDeviceClassifier(
            manufacturer = "ONYX International",
            model = "BOOX Note Air4 C",
        ).classify()

        assertTrue(result.recognizedEInk)
        assertEquals("BOOX", result.deviceLabel)
    }

    @Test
    fun rejectsUnknownAndIncompleteSignatures() {
        val unknown = LocalDeviceClassifier("Google", "Pixel 9").classify()
        val incomplete = LocalDeviceClassifier("ONYX", "Android Tablet").classify()

        assertFalse(unknown.recognizedEInk)
        assertEquals(null, unknown.deviceLabel)
        assertFalse(incomplete.recognizedEInk)
        assertEquals(null, incomplete.deviceLabel)
    }
}
