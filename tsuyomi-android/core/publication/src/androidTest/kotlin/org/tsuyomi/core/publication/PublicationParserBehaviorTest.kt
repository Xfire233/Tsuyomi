/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.librarydomain.LocalTextHeading
import org.tsuyomi.shared.librarydomain.LocalTextParseOptions
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderInlineKind
import org.tsuyomi.shared.sourcecontract.ReaderLinkTarget
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import org.tsuyomi.shared.sourcecontract.ReaderDocumentPresentation
import org.tsuyomi.shared.sourcecontract.ReaderReadingDirection
import org.tsuyomi.shared.sourcecontract.targetForAnchor
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PublicationParserBehaviorTest {
    @Test
    fun admitted_fixed_image_reopen_preserves_every_page_fragment_and_direction_without_body_reads() {
        var source = epubSource(3)
        val opf = source.archiveEntries.single { it.first == "OEBPS/package.opf" }.second.toString(StandardCharsets.UTF_8)
            .replace("<metadata>", "<metadata><meta property=\"rendition:layout\">pre-paginated</meta>")
            .replace("<spine>", "<spine page-progression-direction=\"rtl\">")
        source = source.replace("OEBPS/package.opf", opf)
            .replace("OEBPS/Text/chapter-01.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><img id=\"opening\" src=\"../Images/cover.png\" alt=\"page one\"/></body></html>")
            .replace("OEBPS/Text/chapter-02.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><img id=\"scene\" src=\"../Images/cover.png\" alt=\"page two\"/></body></html>")
        val parser = PublicationParser(PUBLICATION_ID, LocalPublicationFormat.EPUB, source)
        val admitted = (parser.parse() as PublicationParseResult.Ready).publication
        val bytes = ByteArrayOutputStream()
        PublicationAdmissionCodec.write(checkNotNull(admitted.admissionRecord), bytes) {}
        val record = PublicationAdmissionCodec.read(ByteArrayInputStream(bytes.toByteArray())) {}
        source.openedEntries.clear()
        val reopened = parser.openAdmitted(record)
        assertEquals(listOf("mimetype"), source.openedEntries)
        assertEquals(ReaderDocumentPresentation.FIXED_IMAGE, reopened.presentation)
        assertEquals(admitted.sections, reopened.sections)
        assertEquals(admitted.tableOfContents, reopened.tableOfContents)
        val section = reopened.sections.single()
        val identity = DocumentIdentity(BookIdentity.Local(PUBLICATION_ID), section.contentId, reopened.contentRevision)
        val document = reopened.loadSection(section.contentId, identity)
        assertEquals(listOf("image:0:0", "image:1:0"), document.blocks.map { it.blockId })
        assertEquals(ReaderReadingDirection.RTL, document.readingDirection)
        assertTrue(document.anchors.keys.containsAll(listOf("page:0:0", "page:1:0")))
        assertEquals(admitted.loadSection(section.contentId, identity), document)
        assertEquals(listOf("mimetype"), source.openedEntries)
    }

    @Test
    fun admitted_epub_reopen_reads_only_requested_body_and_preserves_native_catalog() {
        for (version in listOf(2, 3)) {
            var source = epubSource(version)
            if (version == 3) {
                val language = "x-" + List(520) { "private" }.joinToString("-")
                val unusedManifest = buildString {
                    repeat(8200) { append("<item id=\"unused$it\" href=\"https://example.org/$it.png\" media-type=\"image/png\"/>") }
                }
                val opf = source.archiveEntries.single { it.first == "OEBPS/package.opf" }.second.toString(StandardCharsets.UTF_8)
                    .replace("<dc:language>zh-CN</dc:language>", "<dc:language>$language</dc:language>")
                    .replace("</manifest>", "$unusedManifest</manifest>")
                source = MemorySource(source.archiveEntries.map { (path, bytes) ->
                    path to if (path == "OEBPS/package.opf") opf.toByteArray(StandardCharsets.UTF_8) else bytes
                }, source.sha256)
            }
            val parser = PublicationParser(PUBLICATION_ID, LocalPublicationFormat.EPUB, source)
            val admitted = (parser.parse() as PublicationParseResult.Ready).publication
            val output = ByteArrayOutputStream()
            PublicationAdmissionCodec.write(checkNotNull(admitted.admissionRecord), output) {}
            val record = PublicationAdmissionCodec.read(ByteArrayInputStream(output.toByteArray())) {}
            source.openedEntries.clear()
            val reopened = parser.openAdmitted(record)
            assertEquals(listOf("mimetype"), source.openedEntries)
            assertEquals(admitted.metadata, reopened.metadata)
            assertEquals(admitted.sections, reopened.sections)
            assertEquals(admitted.tableOfContents, reopened.tableOfContents)
            assertEquals(admitted.resources, reopened.resources)
            assertEquals(admitted.compatibility, reopened.compatibility)
            val section = reopened.sections[1]
            val identity = DocumentIdentity(BookIdentity.Local(PUBLICATION_ID), section.contentId, reopened.contentRevision)
            source.openedEntries.clear()
            val document = reopened.loadSection(section.contentId, identity)
            assertEquals(listOf("OEBPS/Text/chapter-02.xhtml"), source.openedEntries)
            assertEquals(section.anchors["scene"], document.targetForAnchor("scene"))
            assertEquals(admitted.loadSection(section.contentId, identity), document)
        }
    }

    @Test
    fun epub2_ncx_and_epub3_nav_preserve_spine_toc_blocks_anchors_and_resources() {
        for (version in listOf(2, 3)) {
            val parser = PublicationParser(PUBLICATION_ID, LocalPublicationFormat.EPUB, epubSource(version))
            val publication = (parser.parse() as PublicationParseResult.Ready).publication

            assertEquals(LocalPublicationFormat.EPUB, publication.metadata.format)
            assertEquals("雨巷中的约定", publication.metadata.title)
            assertEquals(listOf("林青"), publication.metadata.authors)
            assertEquals(2, publication.sections.size)
            assertEquals("第一章", publication.tableOfContents.first().title)
            assertEquals("第二章", publication.tableOfContents.first().children.single().title)
            assertTrue(publication.sections.first().anchors.keys.containsAll(listOf("opening", "note")))
            assertNotNull(publication.metadata.cover)

            val firstSection = publication.sections.first()
            val document = publication.loadSection(
                firstSection.contentId,
                DocumentIdentity(BookIdentity.Local(PUBLICATION_ID), firstSection.contentId, publication.contentRevision),
            )
            assertTrue(document.blocks.any { it is ReaderBlock.Heading })
            assertTrue(document.blocks.any { it is ReaderBlock.Image })
            assertTrue(document.blocks.any { it is ReaderBlock.Listing })
            assertTrue(document.blocks.any { it is ReaderBlock.Table })
            assertTrue(document.blocks.any { it is ReaderBlock.Note })

            val paragraph = document.blocks.filterIsInstance<ReaderBlock.Paragraph>().first { "静" in it.text }
            assertTrue(paragraph.spans.any { it.kind == ReaderInlineKind.Strong })
            assertTrue(paragraph.spans.any { it.kind == ReaderInlineKind.Emphasis })
            assertTrue(paragraph.spans.any { it.kind is ReaderInlineKind.Ruby })
            assertTrue(paragraph.spans.any { (it.kind as? ReaderInlineKind.Link)?.target is ReaderLinkTarget.Internal })
            assertTrue(paragraph.spans.any { it.kind is ReaderInlineKind.NoteReference })

            val localImage = document.blocks.filterIsInstance<ReaderBlock.Image>()
                .mapNotNull { it.resource as? ReaderResourceRef.Local }.single()
            assertTrue(publication.openResource(localImage).use { it.readBytes() }.contentEquals(ONE_PIXEL_PNG))

            val secondSection = publication.sections[1]
            val second = publication.loadSection(
                secondSection.contentId,
                DocumentIdentity(BookIdentity.Local(PUBLICATION_ID), secondSection.contentId, publication.contentRevision),
            )
            assertEquals(secondSection.anchors["scene"], second.targetForAnchor("scene"))
        }
    }

    @Test
    fun txt_bom_preview_heading_correction_and_on_demand_sections_share_stable_identities() {
        val text = "第十二章 雨巷\r\n第一段 😀\r\n第二段\n"
        val bytes = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + text.toByteArray(StandardCharsets.UTF_8)
        val parser = PublicationParser(PUBLICATION_ID, LocalPublicationFormat.TXT, textSource(bytes))
        val first = (parser.parse() as PublicationParseResult.Ready).publication

        assertEquals("UTF-8", first.localMetadata.txtOptions?.charsetName)
        assertEquals("UTF-8", first.txtPreview?.charsetName)
        assertTrue(first.txtPreview?.tableOfContentsSuggestions?.any { it.paragraphOrdinal == 0 } == true)
        assertEquals(1, first.sections.size)
        val before = first.sections.single()
        val identity = DocumentIdentity(BookIdentity.Local(PUBLICATION_ID), before.contentId, first.contentRevision)
        val uncorrectedDocument = first.loadSection(before.contentId, identity)
        assertEquals(3, uncorrectedDocument.blocks.size)
        assertTrue(uncorrectedDocument.blocks.first() is ReaderBlock.Paragraph)

        val segmented = (parser.parse(PublicationOptions(LocalTextParseOptions(charsetName = "UTF-8", paragraphsPerSection = 1)))
            as PublicationParseResult.Ready).publication
        assertEquals(3, segmented.sections.size)
        assertTrue(segmented.tableOfContents.isEmpty())

        val confirmed = PublicationOptions(
            LocalTextParseOptions(charsetName = "UTF-8", headingSelections = listOf(LocalTextHeading(0, "序章"))),
        )
        val corrected = (parser.parse(confirmed) as PublicationParseResult.Ready).publication
        assertEquals("序章", corrected.sections.first().title)
        assertEquals(1, corrected.sections.size)
        assertEquals("UTF-8", corrected.localMetadata.txtOptions?.charsetName)
        assertEquals(listOf("序章"), corrected.tableOfContents.map { it.title })
        val correctedSection = corrected.sections.first()
        val correctedDocument = corrected.loadSection(
            correctedSection.contentId,
            DocumentIdentity(BookIdentity.Local(PUBLICATION_ID), correctedSection.contentId, corrected.contentRevision),
        )
        assertEquals("序章", correctedDocument.title)
        assertTrue(correctedDocument.blocks.first() is ReaderBlock.Heading)
        assertEquals(3, correctedDocument.blocks.size)
        assertEquals(before.contentId, correctedSection.contentId)
    }

    @Test
    fun ambiguous_legacy_bytes_offer_strict_candidates_and_never_replace_invalid_utf8() {
        val encoded = Charset.forName("GB18030").encode("你好")
        val ambiguousBytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
        val parser = PublicationParser(PUBLICATION_ID, LocalPublicationFormat.TXT, textSource(ambiguousBytes))
        val choice = parser.parse() as PublicationParseResult.NeedsCharsetChoice
        assertTrue(choice.candidates.any { it.charsetName == "GB18030" })
        assertTrue(choice.candidates.any { it.charsetName == "Big5" })
        assertTrue(choice.candidates.all { it.preview.isNotEmpty() })

        val explicit = parser.parse(PublicationOptions(LocalTextParseOptions(charsetName = "GB18030")))
            as PublicationParseResult.Ready
        assertEquals("GB18030", explicit.publication.localMetadata.txtOptions?.charsetName)

        val invalid = textSource(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte(), 0xc3.toByte(), 0x28))
        val error = runCatching {
            PublicationParser(PUBLICATION_ID, LocalPublicationFormat.TXT, invalid).parse()
        }.exceptionOrNull() as PublicationParseException
        assertEquals(PublicationFailureCode.INVALID_TEXT_ENCODING, error.code)
    }

    @Test
    fun html_normalization_is_inert_and_exposes_unsupported_content() {
        val normalized = PublicationMarkupNormalizer().normalizeHtml(
            contentId = "mobi:chapter",
            html = """
                <html><head><style>span.hot { font-weight: bold; }</style></head>
                <body><p>甲 &amp; <span class="hot">乙</span></p>
                <script>window.alert(1)</script>
                <ul><li>前<strong>粗体</strong>后</li></ul>
                <table><tr><td><em>单元格</em><script>private_script_payload()</script></td></tr></table>
                <a href="javascript:alert(1)">危险链接</a></body></html>
            """.trimIndent(),
            resolveLink = { null },
            resolveImage = { null },
        )
        val paragraph = normalized.blocks.filterIsInstance<ReaderBlock.Paragraph>().first()
        assertEquals("甲 & 乙", paragraph.text)
        assertTrue(paragraph.spans.any { it.kind == ReaderInlineKind.Strong })
        assertTrue(normalized.blocks.filterIsInstance<ReaderBlock.Unsupported>().any { it.featureCode == "inert-script" })
        assertFalse(normalized.blocks.filterIsInstance<ReaderBlock.Unsupported>().any { "window.alert" in it.visibleText })
        assertTrue(normalized.findings.any { it.code == "UNSUPPORTED_LINK_TARGET" })
        val item = normalized.blocks.filterIsInstance<ReaderBlock.Listing>().single().items.single()
            .blocks.filterIsInstance<ReaderBlock.Paragraph>().single()
        assertEquals("前粗体后", item.text)
        assertTrue(item.spans.any { it.kind == ReaderInlineKind.Strong })
        val cell = normalized.blocks.filterIsInstance<ReaderBlock.Table>().single().rows.single().cells.single()
        assertEquals("单元格", cell.text)
        assertTrue(cell.spans.any { it.kind == ReaderInlineKind.Emphasis })
    }

    @Test
    fun hostile_epub_paths_dtd_fixed_layout_and_protection_are_classified() {
        assertFailure(
            source = epubSource(3, extra = listOf("../outside.txt" to byteArrayOf(1))),
            expected = PublicationFailureCode.UNSAFE_ARCHIVE_PATH,
        )
        val dtd = epubSource(3).replace("META-INF/container.xml", "<!DOCTYPE container [<!ENTITY x 'bad'>]><container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles/></container>")
        assertFailure(dtd, PublicationFailureCode.XML_DTD_FORBIDDEN)
        val utf16Dtd = "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-16LE\"?><!DOCTYPE container [<!ENTITY x 'bad'>]><container/>"
            .toByteArray(Charset.forName("UTF-16LE"))
        assertFailure(epubSource(3).replaceBytes("META-INF/container.xml", utf16Dtd), PublicationFailureCode.XML_DTD_FORBIDDEN)
        assertFailure(epubSource(3).without("OEBPS/Images/cover.png"), PublicationFailureCode.MISSING_RESOURCE)
        assertFailure(epubSource(3, extra = listOf("META-INF/license.lcpl" to byteArrayOf(1))), PublicationFailureCode.PROTECTED_CONTENT)
    }

    private fun assertFailure(source: PublicationSource, expected: PublicationFailureCode) {
        val error = runCatching {
            PublicationParser(PUBLICATION_ID, LocalPublicationFormat.EPUB, source).parse()
        }.exceptionOrNull() as PublicationParseException
        assertEquals(expected, error.code)
    }

    private fun epubSource(version: Int, extra: List<Pair<String, ByteArray>> = emptyList()): MemorySource {
        val cover = if (version == 2) "<meta name=\"cover\" content=\"cover\"/>" else ""
        val navManifest = if (version >= 3) "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>" else "<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>"
        val navSpine = if (version == 2) " toc=\"ncx\"" else ""
        val packageXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:epub="http://www.idpf.org/2007/ops" xmlns:rendition="http://www.idpf.org/vocab/rendition/#" version="$version.0" unique-identifier="book-id">
              <metadata><dc:identifier id="book-id">book-1</dc:identifier><dc:title>雨巷中的约定</dc:title><dc:creator>林青</dc:creator><dc:language>zh-CN</dc:language>$cover</metadata>
              <manifest>
                <item id="chapter2" href="Text/chapter-02.xhtml" media-type="application/xhtml+xml"/>
                <item id="chapter1" href="Text/chapter-01.xhtml" media-type="application/xhtml+xml"/>
                $navManifest
                <item id="cover" href="Images/cover.png" media-type="image/png"${if (version >= 3) " properties=\"cover-image\"" else ""}/>
              </manifest>
              <spine$navSpine><itemref idref="chapter1"/><itemref idref="chapter2"/></spine>
            </package>
        """.trimIndent()
        val firstChapter = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <head><title>第一章</title><style>strong { font-weight: bold; } em { font-style: italic; }</style></head>
              <body>
                <h1 id="opening">第一章</h1>
                <p>雨巷很<strong>静</strong>，夜雨<em>微凉</em>，青<ruby>青<rt>qīng</rt></ruby>。<a epub:type="noteref" href="#note">注</a><a href="chapter-02.xhtml#scene">第二章</a></p>
                <img src="../Images/cover.png" alt="封面"/>
                <ul><li>第一项</li><li><p>第二项</p></li></ul>
                <table id="data"><caption>表一</caption><tr><th>名</th><th>值</th></tr><tr><td>雨</td><td>巷</td></tr></table>
                <aside id="note" epub:type="footnote"><p>这是脚注。</p></aside>
              </body>
            </html>
        """.trimIndent()
        val secondChapter = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>第二章</title></head><body><h1 id="scene">第二章</h1><p>回声。</p></body></html>
        """.trimIndent()
        val navDocument = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <body><nav epub:type="toc"><ol><li><a href="Text/chapter-01.xhtml#opening">第一章</a><ol><li><a href="Text/chapter-02.xhtml#scene">第二章</a></li></ol></li></ol></nav></body>
            </html>
        """.trimIndent()
        val ncxDocument = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
              <navPoint id="one" playOrder="1"><navLabel><text>第一章</text></navLabel><content src="Text/chapter-01.xhtml#opening"/>
                <navPoint id="two" playOrder="2"><navLabel><text>第二章</text></navLabel><content src="Text/chapter-02.xhtml#scene"/></navPoint>
              </navPoint>
            </navMap></ncx>
        """.trimIndent()
        val entries = arrayListOf(
            "mimetype" to "application/epub+zip".toByteArray(StandardCharsets.US_ASCII),
            "META-INF/container.xml" to "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/package.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>".toByteArray(),
            "OEBPS/package.opf" to packageXml.toByteArray(),
            "OEBPS/Text/chapter-02.xhtml" to secondChapter.toByteArray(),
            "OEBPS/Images/cover.png" to ONE_PIXEL_PNG,
            "OEBPS/Text/chapter-01.xhtml" to firstChapter.toByteArray(),
        )
        if (version >= 3) entries += "OEBPS/nav.xhtml" to navDocument.toByteArray()
        else entries += "OEBPS/toc.ncx" to ncxDocument.toByteArray()
        entries += extra
        return MemorySource(entries, sha256 = DIGEST)
    }

    private fun textSource(bytes: ByteArray) = MemorySource(emptyList(), sha256 = DIGEST, textBytes = bytes)

    private class MemorySource(
        val archiveEntries: List<Pair<String, ByteArray>>,
        override val sha256: String,
        private val textBytes: ByteArray = ByteArray(0),
    ) : PublicationSource {
        val openedEntries = mutableListOf<String>()
        override val sizeBytes: Long = if (textBytes.isNotEmpty()) textBytes.size.toLong()
        else archiveEntries.sumOf { it.second.size.toLong() }.coerceAtLeast(1L)
        override fun openInputStream() = ByteArrayInputStream(textBytes)
        override fun openArchive(): PublicationZipArchive = MemoryArchive(archiveEntries) { openedEntries += it }
    }

    private class MemoryArchive(
        private val values: List<Pair<String, ByteArray>>,
        private val onOpen: (String) -> Unit = {},
    ) : PublicationZipArchive {
        private val byName = values.associate { it.first to it.second }
        override fun entries(): Sequence<PublicationZipEntry> = values.asSequence().map { (name, bytes) ->
            PublicationZipEntry(
                name = name,
                sizeBytes = bytes.size.toLong(),
                compressedSizeBytes = bytes.size.coerceAtLeast(1).toLong(),
                compressionMethod = if (name == "mimetype") PublicationZipArchive.STORED else PublicationZipArchive.DEFLATED,
                isDirectory = false,
            )
        }
        override fun openEntry(name: String): ByteArrayInputStream {
            onOpen(name)
            return ByteArrayInputStream(byName[name] ?: throw IOException("Missing entry"))
        }
        override fun close() = Unit
    }

    private fun MemorySource.replace(path: String, replacement: String): MemorySource {
        val changed = archiveEntries.map { (name, bytes) ->
            if (name == path) name to replacement.toByteArray(StandardCharsets.UTF_8) else name to bytes
        }
        return MemorySource(changed, sha256 = DIGEST)
    }

    private fun MemorySource.replaceBytes(path: String, replacement: ByteArray): MemorySource = MemorySource(
        archiveEntries.map { (name, bytes) -> if (name == path) name to replacement else name to bytes },
        sha256 = DIGEST,
    )

    private fun MemorySource.without(path: String): MemorySource = MemorySource(
        archiveEntries.filterNot { it.first == path },
        sha256 = DIGEST,
    )


    companion object {
        private const val PUBLICATION_ID = "00000000-0000-4000-8000-000000000001"
        private const val DIGEST = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private val ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p4cAAAAASUVORK5CYII=",
        )
    }
}
