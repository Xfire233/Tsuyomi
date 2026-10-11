/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat

@RunWith(AndroidJUnit4::class)
class MobiPublicationResourceInstrumentedTest {
    @Test
    fun derivesReaderDigestFromImageBytesAndRejectsLaterResourceMutation() {
        val content = ImageContent()
        val parsed = MobiPublicationParser(
            publicationId = PUBLICATION_ID,
            originalSha256 = "0123456789abcdef".repeat(4),
            originalSizeBytes = 128,
            content = content,
        ).parse()
        val reference = parsed.resources.single().reference

        assertEquals("image/png", reference.mimeType)
        assertEquals(sha256(content.bytes), reference.sha256)
        parsed.openResource(reference).use { opened ->
            assertArrayEquals(content.bytes, opened.readBytes())
        }

        content.bytes = content.bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        var rejected = false
        try {
            parsed.openResource(reference).use { it.readBytes() }
        } catch (error: IOException) {
            rejected = true
        }
        assertTrue("A changed MOBI image must fail host SHA-256 verification", rejected)
    }

    private class ImageContent : MobiPublicationContent {
        var bytes = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADUlEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC",
        )

        override val catalog = MobiPublicationCatalog(
            format = LocalPublicationFormat.MOBI,
            title = "One-pixel image",
            authors = emptyList(),
            sections = listOf(PublicationSection(CONTENT_ID, "Image", 0)),
            tableOfContents = listOf(PublicationTocEntry("Image", CONTENT_ID)),
            coverResourceId = RESOURCE_ID,
        )

        override fun section(contentId: String, cancelled: () -> Unit): MobiPublicationSection {
            cancelled()
            return MobiPublicationSection(
                contentId = contentId,
                html = "<body><img id=\"art\" src=\"tsuyomi-resource://$RESOURCE_ID\" alt=\"pixel\"></body>",
                stylesheets = emptyList(),
                links = emptyMap(),
                resourceIds = listOf(RESOURCE_ID),
            )
        }

        override fun resource(resourceId: String, cancelled: () -> Unit): ByteArrayInputStream {
            cancelled()
            require(resourceId == RESOURCE_ID)
            return ByteArrayInputStream(bytes)
        }

        override fun resourceMetadata(resourceId: String) = MobiPublicationResource(
            resourceId = resourceId,
            mimeType = "image/png",
            sizeBytes = bytes.size.toLong(),
        )

        override fun close() = Unit
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private const val PUBLICATION_ID = "00000000-0000-0000-0000-000000000002"
        private const val CONTENT_ID = "mobi:0"
        private const val RESOURCE_ID = "r000001"
    }
}
