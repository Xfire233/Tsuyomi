/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.source.extensionmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.tsuyomi.shared.sourcecontract.DecodeMode

class SourceExtensionRequestEncodingTest {
    @Test
    fun gb18030QueryMatchesLegacyFormSubmissionBytes() {
        val url = encodeUrlQuery(
            baseUrl = "https://www.wenku8.net/modules/article/search.php",
            query = listOf(
                "searchtype" to "articlename",
                "searchkey" to "文学少女",
                "page" to "1",
            ),
            encoding = DecodeMode.GB18030,
        )

        assertEquals(
            "https://www.wenku8.net/modules/article/search.php?searchtype=articlename&searchkey=%CE%C4%D1%A7%C9%D9%C5%AE&page=1",
            url,
        )
    }

    @Test
    fun structuredQueryRejectsAmbiguousBaseUrlAndAutomaticEncoding() {
        assertThrows(IllegalArgumentException::class.java) {
            encodeUrlQuery(
                "https://www.wenku8.net/search?existing=1",
                listOf("searchkey" to "文学少女"),
                DecodeMode.GB18030,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            encodeUrlQuery(
                "https://www.wenku8.net/search",
                listOf("searchkey" to "文学少女"),
                DecodeMode.AUTO,
            )
        }
    }
}
