/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderAnchorTarget
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderReadingDirection
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef

private val ADMISSION_OPTIONS = PublicationOptions()
private const val EPUB_NORMALIZER_REVISION = "normalizer-3"
private const val MOBI_PARSER_REVISION = "mobi-parser-5"
private const val MOBI_NORMALIZER_REVISION = "publication-markup-2"

/** Opaque completed native admission, never portable input, prose, layout or network authority. */
class PublicationAdmissionRecord internal constructor(
    internal val publicationId: String,
    internal val format: LocalPublicationFormat,
    internal val originalSha256: String,
    internal val originalSizeBytes: Long,
    internal val contentRevision: String,
    internal val compatibility: PublicationCompatibilityReport,
    internal val epub: EpubPublicationParser.EpubCatalog? = null,
    internal val mobi: MobiAdmissionCatalog? = null,
) {
    internal fun requireBinding(id: String, expectedFormat: LocalPublicationFormat, sha: String, size: Long,
        options: PublicationOptions = ADMISSION_OPTIONS) {
        if (id != publicationId || format != expectedFormat || sha != originalSha256 || size != originalSizeBytes ||
            format == LocalPublicationFormat.TXT || size !in 1..32L * 1024 * 1024 ||
            options != ADMISSION_OPTIONS || contentRevision != publicationContentRevision(format, sha) ||
            (format == LocalPublicationFormat.EPUB) != (epub != null) || (epub == null) != (mobi != null)) {
            throw PublicationParseException(PublicationFailureCode.INVALID_ORIGINAL, "Native admission binding is obsolete or mismatched.")
        }
    }
}

internal data class MobiAdmissionCatalog(
    val transportCatalog: MobiPublicationCatalog,
    val metadata: PublicationMetadata,
    val toc: List<PublicationTocEntry>,
    val plans: Map<String, MobiPublicationParser.SectionPlan>,
    val resources: Map<String, MobiPublicationParser.ResourcePlan>,
)

internal fun publicationContentRevision(format: LocalPublicationFormat, sha: String): String {
    val input = if (format == LocalPublicationFormat.EPUB) "$sha|epub|$EPUB_NORMALIZER_REVISION"
        else "publication-core\u0000${format.name}\u0000$sha\u0000$MOBI_PARSER_REVISION\u0000$MOBI_NORMALIZER_REVISION"
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** Independent of the unchanged 4096-byte original/resource/journal record decoder. */
internal object PublicationAdmissionCodec {
    const val MAX_BYTES = 64L * 1024 * 1024
    private const val MAGIC = 0x5459504e // TYPN
    private const val VERSION = 1
    private val HASH = Regex("[a-f0-9]{64}")
    private val RESOURCE_ID = Regex("[A-Za-z0-9_-]{1,128}")
    private val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")
    private fun parserId(format: LocalPublicationFormat): String = if (format == LocalPublicationFormat.EPUB)
        "epub-native-1" else "lingo-reader@0.4.6:bc912542d9c09f144257205943c6bd3e421d8743:$MOBI_PARSER_REVISION"
    private fun normalizerId(format: LocalPublicationFormat): String = if (format == LocalPublicationFormat.EPUB)
        EPUB_NORMALIZER_REVISION else MOBI_NORMALIZER_REVISION

    fun write(record: PublicationAdmissionRecord, stream: OutputStream, cancelled: () -> Unit) {
        record.requireBinding(record.publicationId, record.format, record.originalSha256, record.originalSizeBytes)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = DigestOutputStream(stream, digest)
        val writer = Writer(DataOutputStream(hashed), cancelled)
        with(writer) {
            out.writeInt(MAGIC); out.writeInt(VERSION)
            string(record.publicationId); string(record.format.name); string(record.originalSha256)
            out.writeLong(record.originalSizeBytes); string(record.contentRevision)
            string(parserId(record.format)); string(normalizerId(record.format))
            out.writeInt(0) // EPUB/MOBI have no selectable normalization options.
            findings(record.compatibility)
            if (record.epub != null) epub(record.epub) else mobi(checkNotNull(record.mobi))
            out.flush(); hashed.on(false); out.write(digest.digest()); out.flush()
        }
        cancelled()
    }

    fun read(stream: InputStream, cancelled: () -> Unit): PublicationAdmissionRecord {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = DigestInputStream(stream, digest)
        val reader = Reader(DataInputStream(hashed), cancelled)
        return with(reader) {
            require(input.readInt() == MAGIC && input.readInt() == VERSION)
            val id = string(64).also { BookIdentity.Local(it) }
            val format = LocalPublicationFormat.valueOf(string(16))
            require(format != LocalPublicationFormat.TXT)
            val sha = hash(); val size = input.readLong().also { require(it in 1..32L * 1024 * 1024) }
            val revision = hash()
            require(string(256) == parserId(format) && string(64) == normalizerId(format))
            require(input.readInt() == 0)
            val report = findings()
            val record = if (format == LocalPublicationFormat.EPUB)
                PublicationAdmissionRecord(id, format, sha, size, revision, report, epub = epub(id))
            else PublicationAdmissionRecord(id, format, sha, size, revision, report, mobi = mobi(id, format))
            hashed.on(false)
            val expected = ByteArray(32); input.readFully(expected)
            require(MessageDigest.isEqual(expected, digest.digest()) && input.read() == -1)
            record.requireBinding(id, format, sha, size)
            cancelled()
            record
        }
    }

    private class Writer(val out: DataOutputStream, val cancelled: () -> Unit) {
        fun string(value: String) {
            cancelled()
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            require(bytes.size <= 8 * 1024 * 1024)
            out.writeInt(bytes.size); out.write(bytes)
        }
        fun optional(value: String?) { out.writeBoolean(value != null); if (value != null) string(value) }
        fun strings(values: Collection<String>) { out.writeInt(values.size); values.forEach(::string) }
        fun anchors(values: Map<String, ReaderAnchorTarget>) {
            out.writeInt(values.size)
            values.forEach { (key, value) -> string(key); string(value.blockId); out.writeInt(value.codePointOffset) }
        }
        fun reference(value: ReaderResourceRef?) {
            when (value) {
                null -> out.writeByte(0)
                is ReaderResourceRef.Local -> {
                    out.writeByte(1); string(value.publicationId); string(value.resourceId); string(value.mimeType); string(value.sha256)
                }
                is ReaderResourceRef.RemoteHttps -> { out.writeByte(2); string(value.url) }
            }
        }
        fun resource(value: PublicationResourceRecord) {
            reference(value.reference); out.writeLong(value.byteLength); string(value.role.name)
        }
        fun metadata(value: PublicationMetadata) {
            string(value.format.name); string(value.title); strings(value.authors); optional(value.language)
            optional(value.identifier); reference(value.cover)
        }
        fun toc(values: List<PublicationTocEntry>) {
            out.writeInt(values.size)
            values.forEach { string(it.title); string(it.contentId); optional(it.fragment); toc(it.children) }
        }
        fun findings(value: PublicationCompatibilityReport) {
            string(value.level.name); out.writeInt(value.findings.size)
            value.findings.forEach { string(it.code); string(it.severity.name); string(it.message); optional(it.contentId); optional(it.blockId) }
        }
        fun section(value: PublicationSection, includeAnchors: Boolean = true) {
            string(value.contentId); string(value.title); out.writeInt(value.ordinal); out.writeBoolean(value.linear)
            out.writeBoolean(value.codePointStart != null); value.codePointStart?.let(out::writeLong)
            if (includeAnchors) anchors(value.anchors)
        }
        fun epub(value: EpubPublicationParser.EpubCatalog) {
            metadata(value.metadata); toc(value.toc); string(value.readingDirection.name)
            out.writeInt(value.manifest.items.size)
            value.manifest.items.forEach { item ->
                string(item.id); string(item.href); string(item.mediaType); strings(item.properties)
                string(item.path); out.writeBoolean(item.external); optional(item.externalUrl)
            }
            out.writeInt(value.contentIdByPath.size)
            value.contentIdByPath.forEach { (path, id) -> string(path); string(id) }
            out.writeInt(value.resourcesByPath.size)
            value.resourcesByPath.forEach { (path, record) -> string(path); resource(record) }
            out.writeInt(value.plans.size)
            value.plans.forEach { (key, plan) ->
                string(key); string(plan.contentId); string(plan.path); out.writeBoolean(plan.linear)
                string(plan.title); out.writeBoolean(plan.fixedLayout); anchors(plan.anchors)
            }
            val fixed = value.fixedSequence
            out.writeBoolean(fixed != null)
            if (fixed != null) {
                string(fixed.contentId); anchors(fixed.anchors); out.writeInt(fixed.images.size)
                fixed.images.forEach { image ->
                    require(image.aspectRatio == null) { "Native publication admission does not produce source-wire ratio hints" }
                    string(image.blockId); reference(image.resource); optional(image.altText)
                    out.writeInt(image.width ?: 0); out.writeInt(image.height ?: 0); optional(image.anchorId)
                }
                out.writeInt(fixed.targets.size)
                fixed.targets.forEach { (key, target) ->
                    string(key); string(target.startAnchor); out.writeInt(target.fragments.size)
                    target.fragments.forEach { (fragment, alias) -> string(fragment); string(alias) }
                }
            }
            out.writeInt(value.sections.size); value.sections.forEach { section(it, includeAnchors = false) }
        }
        fun mobi(value: MobiAdmissionCatalog) {
            val catalog = value.transportCatalog
            string(catalog.title); strings(catalog.authors); optional(catalog.language); optional(catalog.identifier)
            optional(catalog.coverResourceId); strings(catalog.authoredSectionIds)
            out.writeInt(catalog.sections.size); catalog.sections.forEach { section(it) }; toc(catalog.tableOfContents)
            metadata(value.metadata); toc(value.toc)
            out.writeInt(value.plans.size)
            value.plans.forEach { (key, plan) ->
                string(key); section(PublicationSection(plan.contentId, plan.title, plan.ordinal, plan.linear, plan.anchors, plan.codePointStart))
                string(plan.inputDigest)
            }
            out.writeInt(value.resources.size)
            value.resources.forEach { (key, plan) ->
                string(key); string(plan.metadata.resourceId); string(plan.metadata.mimeType); out.writeLong(plan.metadata.sizeBytes)
                resource(plan.record)
            }
        }
    }

    private class Reader(val input: DataInputStream, val cancelled: () -> Unit) {
        private var tocCount = 0
        private var anchorCount = 0
        fun count(maximum: Int): Int = input.readInt().also { require(it in 0..maximum); cancelled() }
        fun string(maximum: Int = 8 * 1024 * 1024): String {
            val length = count(maximum)
            val bytes = ByteArray(length); input.readFully(bytes)
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        }
        fun optional(maximum: Int = 8 * 1024 * 1024): String? = if (input.readBoolean()) string(maximum) else null
        fun strings(maximum: Int): List<String> = List(count(maximum)) { string() }
        fun hash(): String = string(64).also { require(HASH.matches(it)) }
        fun path(): String = string(64 * 1024).also { value ->
            require(value.isNotEmpty() && !value.startsWith('/') && '\\' !in value && '\u0000' !in value &&
                ':' !in value.substringBefore('/') && value.split('/').none { it.isEmpty() || it == "." || it == ".." })
        }
        fun anchors(maximum: Int = 250_000): Map<String, ReaderAnchorTarget> {
            val size = count(maximum); anchorCount += size; require(anchorCount <= 300_000)
            val result = LinkedHashMap<String, ReaderAnchorTarget>(size)
            repeat(size) {
                val key = string(1024); require(key.isNotEmpty() && key.codePointCount(0, key.length) <= 256)
                val block = string(4096); require(block.isNotEmpty())
                val offset = input.readInt().also { require(it >= 0) }
                require(result.put(key, ReaderAnchorTarget(block, offset)) == null)
            }
            return result
        }
        fun reference(id: String): ReaderResourceRef? = when (input.readUnsignedByte()) {
            0 -> null
            1 -> ReaderResourceRef.Local(string(64).also { require(it == id) }, string(64), string(128), hash())
            2 -> ReaderResourceRef.RemoteHttps(string(16 * 1024))
            else -> error("Invalid admission reference")
        }
        fun resource(id: String): PublicationResourceRecord {
            val ref = reference(id) as? ReaderResourceRef.Local ?: error("Expected local resource")
            require(ref.mimeType in IMAGE_TYPES)
            val size = input.readLong().also { require(it in 1..8L * 1024 * 1024) }
            return PublicationResourceRecord(ref, size, PublicationResourceRole.valueOf(string(16)))
        }
        fun metadata(id: String, format: LocalPublicationFormat): PublicationMetadata {
            require(LocalPublicationFormat.valueOf(string(16)) == format)
            val title = string(4096); require(title.isNotBlank() && title.codePointCount(0, title.length) <= 512)
            return PublicationMetadata(format, title, strings(64), optional(), optional(4096), reference(id))
        }
        fun toc(depth: Int = 0): List<PublicationTocEntry> {
            require(depth <= 96)
            val size = count(250_000); tocCount += size; require(tocCount <= 500_000)
            return List(size) { PublicationTocEntry(string(), string(4096), optional(1024), toc(depth + 1)) }
        }
        fun findings(): PublicationCompatibilityReport {
            val level = PublicationCompatibilityLevel.valueOf(string(16))
            var characters = 0L
            val values = List(count(2_000_000)) {
                val finding = PublicationCompatibilityFinding(string(4096), PublicationCompatibilitySeverity.valueOf(string(32)),
                    string(), optional(4096), optional(4096))
                characters += finding.code.length + finding.message.length + (finding.contentId?.length ?: 0) + (finding.blockId?.length ?: 0)
                require(characters <= MAX_BYTES)
                finding
            }
            require(level == if (values.any { it.severity != PublicationCompatibilitySeverity.NOTICE })
                PublicationCompatibilityLevel.DEGRADED else PublicationCompatibilityLevel.SUPPORTED)
            return PublicationCompatibilityReport(level, values)
        }
        fun section(includeAnchors: Boolean = true): PublicationSection {
            val id = string(4096); val title = string(); val ordinal = input.readInt(); val linear = input.readBoolean()
            val start = if (input.readBoolean()) input.readLong() else null
            return PublicationSection(id, title, ordinal, linear, if (includeAnchors) anchors() else emptyMap(), start)
        }
        fun validateToc(values: List<PublicationTocEntry>, sections: Map<String, Map<String, ReaderAnchorTarget>>) {
            values.forEach { row ->
                require(row.contentId in sections && (row.fragment == null || row.fragment in sections.getValue(row.contentId)))
                validateToc(row.children, sections)
            }
        }
        fun epub(id: String): EpubPublicationParser.EpubCatalog {
            val metadata = metadata(id, LocalPublicationFormat.EPUB); val toc = toc()
            val direction = ReaderReadingDirection.valueOf(string(8))
            val items = List(count(PublicationXmlBudget.MAX_XML_NODES)) {
                val key = string(16 * 1024); val href = string(16 * 1024); val mime = string(16 * 1024); val properties = strings(4096)
                val itemPath = string(64 * 1024); val external = input.readBoolean(); val url = optional(16 * 1024)
                if (!external) require(itemPath == safePath(itemPath))
                EpubPublicationParser.ManifestItem(key, href, mime, properties, itemPath, external, url)
            }
            val byId = items.associateBy { it.id }; val byPath = items.filterNot { it.external }.associateBy { it.path }
            require(byId.size == items.size && byPath.size == items.count { !it.external })
            val contentIds = LinkedHashMap<String, String>()
            repeat(count(8192)) { require(contentIds.put(path(), string(4096)) == null) }
            require(contentIds.values.toSet().size == contentIds.size)
            val resources = LinkedHashMap<String, PublicationResourceRecord>()
            repeat(count(8192)) { val key = path(); require(key in byPath && resources.put(key, resource(id)) == null) }
            val plans = LinkedHashMap<String, EpubPublicationParser.SectionPlan>()
            repeat(count(8192)) {
                val key = string(4096); val contentId = string(4096); val documentPath = path()
                require(key == contentId && contentIds[documentPath] == contentId && documentPath in byPath)
                val linear = input.readBoolean(); val title = string(); val fixed = input.readBoolean(); val anchors = anchors()
                require(plans.put(key, EpubPublicationParser.SectionPlan(contentId, documentPath, linear, title, fixed, anchors)) == null)
            }
            val resourceReferences = resources.values.mapTo(HashSet()) { it.reference }
            val fixed = if (input.readBoolean()) {
                val contentId = string(4096); val anchors = anchors(50_000)
                val images = List(count(20_000)) {
                    val block = string(4096); val ref = reference(id) as? ReaderResourceRef.Local ?: error("Expected fixed image")
                    require(ref in resourceReferences)
                    val alt = optional(); val width = input.readInt(); val height = input.readInt(); val anchor = optional(1024)
                    ReaderBlock.Image(block, ref, alt, width.takeIf { it != 0 }, height.takeIf { it != 0 }, anchor)
                }
                val targets = LinkedHashMap<String, EpubPublicationParser.FixedSectionTarget>()
                repeat(count(8192)) {
                    val key = string(4096); val start = string(1024); require(start in anchors && key in contentIds.values)
                    val fragments = LinkedHashMap<String, String>()
                    repeat(count(50_000)) { val fragment = string(1024); val alias = string(1024); require(alias in anchors && fragments.put(fragment, alias) == null) }
                    require(targets.put(key, EpubPublicationParser.FixedSectionTarget(start, fragments)) == null)
                }
                val imageIds = images.mapTo(HashSet()) { it.blockId }
                require(imageIds.size == images.size && images.isNotEmpty() && targets.keys == contentIds.values.toSet() &&
                    anchors.values.all { it.blockId in imageIds })
                EpubPublicationParser.FixedImageSequence(contentId, images, anchors, targets)
            } else null
            val sections = List(count(8192)) {
                val descriptor = section(includeAnchors = false)
                descriptor.copy(anchors = if (fixed != null) fixed.anchors else plans.getValue(descriptor.contentId).anchors)
            }
            require(sections.isNotEmpty() && sections.withIndex().all { it.index == it.value.ordinal } && sections.any { it.linear })
            require(if (fixed == null) sections.map { it.contentId }.toSet() == plans.keys
                else sections.size == 1 && sections.single().contentId == fixed.contentId && fixed.contentId in plans)
            validateToc(toc, sections.associate { it.contentId to it.anchors })
            return EpubPublicationParser.EpubCatalog(metadata, sections, toc, plans, contentIds,
                EpubPublicationParser.Manifest(items, byId, byPath), resources, resources.values.toList(), direction, fixed)
        }
        private fun safePath(value: String): String {
            require(value.isNotEmpty() && !value.startsWith('/') && '\\' !in value && '\u0000' !in value &&
                ':' !in value.substringBefore('/') && value.split('/').none { it.isEmpty() || it == "." || it == ".." })
            return value
        }
        fun mobi(id: String, format: LocalPublicationFormat): MobiAdmissionCatalog {
            val title = string(4096); val authors = strings(64); val language = optional(4096); val identifier = optional(4096)
            val cover = optional(128); val authored = strings(10_000).toSet()
            val transportSections = List(count(10_000)) { section() }; val transportToc = toc()
            require(transportSections.all { it.anchors.isEmpty() })
            val transport = MobiPublicationCatalog(format, title, authors, language, identifier, transportSections, transportToc, cover, authored)
            val metadata = metadata(id, format); val toc = toc()
            val plans = LinkedHashMap<String, MobiPublicationParser.SectionPlan>()
            repeat(count(10_000)) {
                val key = string(1024); val section = section(); val digest = hash()
                require(key == section.contentId && plans.put(key, MobiPublicationParser.SectionPlan(section.contentId,
                    section.title, section.ordinal, section.linear, section.codePointStart, digest, section.anchors)) == null)
            }
            val resources = LinkedHashMap<String, MobiPublicationParser.ResourcePlan>()
            var resourceBytes = 0L
            repeat(count(512)) {
                val key = string(128); val resourceId = string(128); val mime = string(128); val size = input.readLong()
                val record = resource(id); resourceBytes += size
                require(key == resourceId && RESOURCE_ID.matches(key) && size == record.byteLength && mime == record.reference.mimeType)
                require(resources.put(key, MobiPublicationParser.ResourcePlan(MobiPublicationResource(resourceId, mime, size), record)) == null)
            }
            require(resourceBytes <= 32L * 1024 * 1024 && plans.isNotEmpty() && plans.values.withIndex().all { it.index == it.value.ordinal })
            require(transportSections.map { it.contentId } == plans.keys.toList() && authored.all { it in plans })
            require(cover == null || cover in resources)
            validateToc(toc, plans.mapValues { it.value.anchors })
            return MobiAdmissionCatalog(transport, metadata, toc, plans, resources)
        }
    }
}
