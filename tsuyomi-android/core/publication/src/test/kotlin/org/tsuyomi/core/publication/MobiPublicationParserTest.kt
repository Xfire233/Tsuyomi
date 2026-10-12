/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderInlineKind
import org.tsuyomi.shared.sourcecontract.ReaderLinkTarget
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef

class MobiPublicationParserTest {
    @Test
    fun admittedReopenPreservesCatalogAndNormalizesOnlyRequestedSection() {
        val original = parser(contentFixture()).parse()
        val output = ByteArrayOutputStream()
        PublicationAdmissionCodec.write(checkNotNull(original.admissionRecord), output) {}
        val record = PublicationAdmissionCodec.read(ByteArrayInputStream(output.toByteArray())) {}
        val content = contentFixture()
        val reopened = parser(content).openAdmitted(record)
        assertTrue(content.sectionCalls.isEmpty())
        assertEquals(0, content.resourceOpenCalls)
        assertEquals(original.metadata, reopened.metadata)
        assertEquals(original.sections, reopened.sections)
        assertEquals(original.tableOfContents, reopened.tableOfContents)
        assertEquals(original.compatibility, reopened.compatibility)
        val identity = identity(original, "mobi:1")
        assertEquals(original.loadSection("mobi:1", identity), reopened.loadSection("mobi:1", identity))
        assertEquals(mapOf("mobi:1" to 1), content.sectionCalls)
    }

    @Test
    fun admittedReopenRejectsChangedCatalogAndRequestedPayload() {
        val original = parser(contentFixture()).parse()
        val record = checkNotNull(original.admissionRecord)
        val changed = contentFixture(tocFragment = "changed")
        assertFailure(PublicationFailureCode.INVALID_ORIGINAL) { parser(changed).openAdmitted(record) }
        assertTrue(changed.sectionCalls.isEmpty())
        val content = contentFixture()
        val reopened = parser(content).openAdmitted(record)
        content.sections["mobi:1"] = content.sections.getValue("mobi:1").copy(html = "<p>changed</p>")
        assertFailure(PublicationFailureCode.INVALID_ORIGINAL) {
            reopened.loadSection("mobi:1", identity(reopened, "mobi:1"))
        }
        assertEquals(mapOf("mobi:1" to 1), content.sectionCalls)
    }

    @Test
    fun normalizesRichHtmlIntoSharedReaderGrammarAndExactNavigation() {
        val content = contentFixture()
        val publication = parser(content).parse()

        assertEquals(LocalPublicationFormat.MOBI, publication.format)
        assertEquals(listOf("mobi:0", "mobi:1"), publication.sections.map(PublicationSection::contentId))
        assertEquals("mobi:1", publication.tableOfContents.single().children.single().contentId)
        assertEquals("target", publication.tableOfContents.single().children.single().fragment)
        assertFalse(content.closed)

        val identity = identity(publication, "mobi:0")
        val document = publication.loadSection("mobi:0", identity)
        assertEquals(publication.sections.first().anchors, document.anchors)
        assertEquals(2, document.anchors.getValue("inside").codePointOffset)
        assertTrue(document.blocks.any { it is ReaderBlock.Heading && it.text == "Opening 📚" })
        assertTrue(document.blocks.any { it is ReaderBlock.Listing })
        assertTrue(document.blocks.any { it is ReaderBlock.Table })

        val paragraphs = document.blocks.filterIsInstance<ReaderBlock.Paragraph>()
        assertTrue(paragraphs.any { paragraph -> paragraph.spans.any { it.kind == ReaderInlineKind.Strong } })
        assertTrue(paragraphs.any { paragraph -> paragraph.spans.any {
            (it.kind as? ReaderInlineKind.Ruby)?.annotation == "かん"
        } })
        assertTrue(paragraphs.any { paragraph -> paragraph.spans.any {
            (it.kind as? ReaderInlineKind.Link)?.target == ReaderLinkTarget.Internal("mobi:1", "target")
        } })
        val remoteImage = document.blocks.filterIsInstance<ReaderBlock.Image>().single().resource
        assertEquals(ReaderResourceRef.RemoteHttps("https://example.org/art.png"), remoteImage)
        assertTrue(publication.compatibility.findings.any { it.code == "EXTERNAL_HTTPS_RESOURCE_DISABLED" })

        val second = publication.loadSection("mobi:1", identity(publication, "mobi:1"))
        assertTrue(second.anchors.containsKey("target"))
        assertEquals(2, content.sectionCalls.getValue("mobi:1"))
    }

    @Test
    fun keepsEmptyAndUnsupportedSectionsVisibleInsteadOfDroppingThem() {
        val content = contentFixture(emptySecondSection = true)
        val publication = parser(content).parse()

        assertEquals(2, publication.sections.size)
        assertEquals("mobi:1", publication.tableOfContents.single().children.single().contentId)
        assertEquals(PublicationCompatibilityLevel.DEGRADED, publication.compatibility.level)
        val second = publication.loadSection("mobi:1", identity(publication, "mobi:1"))
        assertEquals(1, second.blocks.size)
        assertTrue(second.blocks.single() is ReaderBlock.Unsupported)
    }

    @Test
    fun rejectsTocAndInternalLinkFragmentsThatDoNotNameExactAuthoredAnchors() {
        val tocContent = contentFixture(tocFragment = "missing")
        assertFailure(PublicationFailureCode.MISSING_ANCHOR) { parser(tocContent).parse() }
        assertTrue(tocContent.closed)

        val linkContent = contentFixture(linkFragment = "missing")
        assertFailure(PublicationFailureCode.MISSING_ANCHOR) { parser(linkContent).parse() }
        assertTrue(linkContent.closed)
    }

    @Test
    fun intrinsicMobiAndKf8PositionsNavigateToVerifiedAuthoredAnchors() {
        for (href in listOf("filepos:00000123", "kindle:pos:fid:0:off:14")) {
            val publication = parser(contentFixture(linkHref = href)).parse()
            val document = publication.loadSection("mobi:0", identity(publication, "mobi:0"))
            assertTrue(document.blocks.filterIsInstance<ReaderBlock.Paragraph>().any { paragraph ->
                paragraph.spans.any { (it.kind as? ReaderInlineKind.Link)?.target == ReaderLinkTarget.Internal("mobi:1", "target") }
            })
        }
    }

    @Test
    fun rejectsExternalUrlsAdvertisedAsInternalTargets() {
        for (href in listOf("https://example.org/external", "kindle://example.org/external", "filepos:-1", "kindle:pos:fid:0:off:0:extra")) {
            val content = contentFixture(linkHref = href)
            assertFailure(PublicationFailureCode.INVALID_HREF) { parser(content).parse() }
            assertTrue(content.closed)
        }
    }

    @Test
    fun doesNotAdmitPartialBookWhenALaterSectionCannotBeRead() {
        val content = contentFixture()
        content.sectionFailureContentId = "mobi:1"

        assertFailure(PublicationFailureCode.MISSING_SPINE_ITEM) { parser(content).parse() }
        assertEquals(1, content.sectionCalls.getValue("mobi:0"))
        assertTrue(content.closed)
    }

    @Test
    fun rejectsLocalImagesOutsideTheSectionAllowlistAndOverBudgetResources() {
        val unlisted = contentFixture(localImage = true)
        assertFailure(PublicationFailureCode.MISSING_RESOURCE) { parser(unlisted).parse() }

        val oversized = contentFixture(localImage = true, listLocalImage = true)
        oversized.resourceMetadataOverrides["r000001"] = MobiPublicationResource(
            resourceId = "r000001",
            mimeType = "image/png",
            sizeBytes = 8L * 1024 * 1024 + 1,
        )
        assertFailure(PublicationFailureCode.ARCHIVE_LIMIT) { parser(oversized).parse() }
        assertEquals(0, oversized.resourceOpenCalls)
    }

    @Test
    fun rejectsInvalidImageBytesBeforeCreatingAReaderResourceReference() {
        val content = contentFixture(localImage = true, listLocalImage = true)
        content.resourceBytes["r000001"] = byteArrayOf(0, 1, 2, 3)

        assertFailure(PublicationFailureCode.INVALID_RESOURCE) { parser(content).parse() }
    }

    @Test
    fun lazyReloadRejectsSectionContentChangesAndWrongCallerIdentity() {
        val content = contentFixture()
        val publication = parser(content).parse()
        val wrongIdentity = DocumentIdentity(
            book = BookIdentity.Local(PUBLICATION_ID),
            contentId = "mobi:1",
            revision = publication.contentRevision,
        )
        assertFailure(PublicationFailureCode.MISSING_SPINE_ITEM) {
            publication.loadSection("mobi:0", wrongIdentity)
        }

        val original = content.sections.getValue("mobi:0")
        content.sections["mobi:0"] = original.copy(html = "<body><p>Changed after admission</p></body>")
        assertFailure(PublicationFailureCode.INVALID_ORIGINAL) {
            publication.loadSection("mobi:0", identity(publication, "mobi:0"))
        }
    }

    private fun parser(content: MemoryContent) = MobiPublicationParser(
        publicationId = PUBLICATION_ID,
        originalSha256 = ORIGINAL_SHA256,
        originalSizeBytes = 256,
        content = content,
    )

    private fun identity(publication: ParsedPublication, contentId: String) = DocumentIdentity(
        book = BookIdentity.Local(PUBLICATION_ID),
        contentId = contentId,
        revision = publication.contentRevision,
    )

    private fun contentFixture(
        emptySecondSection: Boolean = false,
        tocFragment: String = "target",
        linkFragment: String = "target",
        linkHref: String = "chapter2.html#target",
        localImage: Boolean = false,
        listLocalImage: Boolean = false,
    ): MemoryContent {
        val firstId = "mobi:0"
        val secondId = "mobi:1"
        val firstHtml = if (emptySecondSection) "<body><h1 id=\"start\">Opening</h1></body>" else """
            <!doctype html><html><head><title>Opening</title></head><body>
              <h1 id="start">Opening 📚</h1>
              <p>字😀<span id="inside">猫</span> <strong>bold</strong>
                <a href="$linkHref">next</a>
                <ruby>漢<rt>かん</rt></ruby></p>
              <ul><li>one</li><li>two</li></ul>
              <table><tr><th>head</th><td>cell</td></tr></table>
              <img src="https://example.org/art.png" alt="remote art">
              ${if (localImage) "<img src=\"tsuyomi-resource://r000001\" alt=\"local art\">" else ""}
            </body></html>
        """.trimIndent()
        val first = MobiPublicationSection(
            contentId = firstId,
            html = firstHtml,
            stylesheets = listOf(PublicationStylesheet("mobi-style-0", "strong { font-weight: bold; }")),
            links = if (emptySecondSection) emptyMap() else mapOf(
                linkHref to ReaderLinkTarget.Internal(secondId, linkFragment),
            ),
            resourceIds = if (listLocalImage) listOf("r000001") else emptyList(),
        )
        val second = MobiPublicationSection(
            contentId = secondId,
            html = if (emptySecondSection) "<body></body>" else "<body><h2 id=\"target\">Second chapter</h2></body>",
            stylesheets = emptyList(),
            links = emptyMap(),
            resourceIds = emptyList(),
        )
        val catalog = MobiPublicationCatalog(
            format = LocalPublicationFormat.MOBI,
            title = "A deterministic book",
            authors = listOf("Example Author"),
            sections = listOf(
                PublicationSection(firstId, "Opening", 0),
                PublicationSection(secondId, "Second chapter", 1),
            ),
            tableOfContents = listOf(
                PublicationTocEntry("Opening", firstId, "start", listOf(
                    PublicationTocEntry("Second chapter", secondId, if (emptySecondSection) null else tocFragment),
                )),
            ),
        )
        return MemoryContent(catalog, linkedMapOf(firstId to first, secondId to second))
    }

    private fun assertFailure(code: PublicationFailureCode, action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected publication failure $code")
        } catch (error: PublicationParseException) {
            assertEquals(code, error.code)
        }
    }

    private class MemoryContent(
        override val catalog: MobiPublicationCatalog,
        val sections: MutableMap<String, MobiPublicationSection>,
    ) : MobiPublicationContent {
        val sectionCalls = LinkedHashMap<String, Int>()
        val resourceBytes = LinkedHashMap<String, ByteArray>()
        val resourceMetadataOverrides = LinkedHashMap<String, MobiPublicationResource>()
        var resourceOpenCalls = 0
        var closed = false
            private set
        var sectionFailureContentId: String? = null

        override fun section(contentId: String, cancelled: () -> Unit): MobiPublicationSection {
            cancelled()
            sectionCalls[contentId] = sectionCalls.getOrDefault(contentId, 0) + 1
            if (contentId == sectionFailureContentId) {
                throw PublicationParseException(PublicationFailureCode.MISSING_SPINE_ITEM)
            }
            return sections[contentId] ?: throw PublicationParseException(PublicationFailureCode.MISSING_SPINE_ITEM)
        }

        override fun resource(resourceId: String, cancelled: () -> Unit): ByteArrayInputStream {
            cancelled()
            resourceOpenCalls++
            return ByteArrayInputStream(resourceBytes[resourceId] ?: byteArrayOf())
        }

        override fun resourceMetadata(resourceId: String): MobiPublicationResource =
            resourceMetadataOverrides[resourceId] ?: MobiPublicationResource(
                resourceId = resourceId,
                mimeType = "image/png",
                sizeBytes = resourceBytes[resourceId]?.size?.toLong() ?: 0L,
            )

        override fun close() {
            closed = true
        }
    }

    companion object {
        private const val PUBLICATION_ID = "00000000-0000-0000-0000-000000000001"
        private val ORIGINAL_SHA256 = "0123456789abcdef".repeat(4)
    }
}
