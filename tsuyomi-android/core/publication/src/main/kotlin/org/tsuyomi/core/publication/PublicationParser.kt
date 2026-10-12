/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.ReaderDocumentPresentation
import org.tsuyomi.shared.sourcecontract.ReaderReadingDirection
import org.tsuyomi.shared.sourcecontract.ReaderInlineKind
import org.tsuyomi.shared.sourcecontract.ReaderLinkTarget
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef
import org.tsuyomi.shared.sourcecontract.ReaderAnchorTarget
import org.tsuyomi.shared.sourcecontract.inlineSpans
import org.tsuyomi.shared.sourcecontract.nestedBlocks

private const val MAX_ARCHIVE_BYTES = 32L * 1024 * 1024
private const val MAX_EXPANDED_BYTES = 128L * 1024 * 1024
private const val MAX_INFLATED_READ_BYTES = 256L * 1024 * 1024
private const val MAX_ARCHIVE_ENTRIES = 8192
private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
private const val MAX_XML_BYTES = 2 * 1024 * 1024
private const val MAX_RESOURCE_BYTES = 8 * 1024 * 1024
private const val MAX_COMPRESSION_RATIO = 200.0
private val SHA256 = Regex("^[a-f0-9]{64}$")

/** Dispatches only formats implemented by this publication adapter; no filename or fake fallback. */
class PublicationParser(
    private val publicationId: String,
    private val format: LocalPublicationFormat,
    private val source: PublicationSource,
) {
    init {
        BookIdentity.Local(publicationId)
    }

    fun parse(
        options: PublicationOptions = PublicationOptions(),
        cancelled: () -> Unit = {},
    ): PublicationParseResult {
        checkCancelled(cancelled)
        validateOriginal(source)
        return when (format) {
            LocalPublicationFormat.EPUB -> PublicationParseResult.Ready(EpubPublicationParser(publicationId, source).parse(cancelled))
            LocalPublicationFormat.TXT -> TxtPublicationParser(publicationId, source).parse(options, cancelled)
            LocalPublicationFormat.MOBI, LocalPublicationFormat.AZW3 ->
                throw PublicationParseException(PublicationFailureCode.UNSUPPORTED_FORMAT,
                    "No MOBI/KF8 adapter is selected by this parser.")
        }
    }

    /** Opens a native plan already bound to a completely admitted private original. */
    fun openAdmitted(
        record: PublicationAdmissionRecord,
        options: PublicationOptions = PublicationOptions(),
        cancelled: () -> Unit = {},
    ): ParsedPublication {
        record.requireBinding(publicationId, format, source.sha256, source.sizeBytes, options)
        validateOriginal(source)
        return EpubPublicationParser(publicationId, source).openAdmitted(record, cancelled)
    }

    /** Admits only declared local cover bytes; never reads navigation or spine document bodies. */
    fun recoverEpubCover(
        cancelled: () -> Unit = {},
        persistResource: (ReaderResourceRef.Local, InputStream) -> Unit,
    ): String? {
        checkCancelled(cancelled)
        validateOriginal(source)
        require(format == LocalPublicationFormat.EPUB)
        return EpubPublicationParser(publicationId, source).recoverCover(cancelled, persistResource)
    }
}

internal class EpubPublicationParser(
    private val publicationId: String,
    private val source: PublicationSource,
) {
    private val findings = LinkedHashMap<String, PublicationCompatibilityFinding>()
    private var findingCharacters = 0L
    private val normalizer = PublicationMarkupNormalizer()

    fun parse(cancelled: () -> Unit): ParsedPublication {
        val catalog = withArchive(source, cancelled) { archive -> inspect(archive, cancelled) }
        val record = PublicationAdmissionRecord(publicationId, LocalPublicationFormat.EPUB, source.sha256,
            source.sizeBytes, publicationContentRevision(LocalPublicationFormat.EPUB, source.sha256),
            compatibilityReport(findings.values), epub = catalog)
        return publication(record, catalog)
    }

    fun openAdmitted(record: PublicationAdmissionRecord, cancelled: () -> Unit): ParsedPublication {
        val catalog = record.epub ?: fail(PublicationFailureCode.INVALID_ORIGINAL)
        withArchive(source, cancelled) { archive -> requireEpubArchive(archive, cancelled) }
        return publication(record, catalog)
    }

    private fun publication(record: PublicationAdmissionRecord, catalog: EpubCatalog): ParsedPublication = ParsedPublication(
        publicationId = publicationId,
        format = LocalPublicationFormat.EPUB,
        originalSha256 = source.sha256,
        originalSizeBytes = source.sizeBytes,
        contentRevision = record.contentRevision,
        options = PublicationOptions(),
        metadata = catalog.metadata,
        sections = catalog.sections,
        tableOfContents = catalog.toc,
        resources = catalog.resources,
        compatibility = record.compatibility,
        presentation = if (catalog.fixedSequence != null) ReaderDocumentPresentation.FIXED_IMAGE
            else ReaderDocumentPresentation.REFLOWABLE,
        admissionRecord = record,
        loadSectionBlock = { contentId, identity, check -> loadSection(catalog, contentId, identity, check) },
        openResourceStream = { reference, check -> openResource(catalog, reference, check) },
    )

    fun recoverCover(
        cancelled: () -> Unit,
        persistResource: (ReaderResourceRef.Local, InputStream) -> Unit,
    ): String? = withArchive(source, cancelled) { archive ->
        val (opfPath, opf) = readPackage(archive, cancelled)
        val metadata = opf.elements("metadata", OPF_NAMESPACE).firstOrNull()
            ?: fail(PublicationFailureCode.MALFORMED_PACKAGE)
        val manifestNode = opf.elements("manifest", OPF_NAMESPACE).firstOrNull()
            ?: fail(PublicationFailureCode.MALFORMED_PACKAGE)
        val spine = opf.elements("spine", OPF_NAMESPACE).firstOrNull()
            ?: fail(PublicationFailureCode.MALFORMED_PACKAGE)
        detectProtection(archive, metadata, spine, cancelled)
        val cover = resolveCover(metadata, readManifest(manifestNode, opfPath)) ?: return@withArchive null
        val reference = resolveManifestResource(cover, archive, LinkedHashMap(), PublicationResourceRole.COVER, cancelled)
            as? ReaderResourceRef.Local ?: return@withArchive null
        archive.openEntry(cover.path, cancelled).use { persistResource(reference, it) }
        checkCancelled(cancelled)
        reference.resourceId
    }

    private fun readPackage(archive: BoundedArchive, cancelled: () -> Unit): Pair<String, PublicationMarkupElement> {
        requireEpubArchive(archive, cancelled)
        val container = parseXmlEntry(archive, "META-INF/container.xml", cancelled = cancelled)
        val rootfile = container.descendants().firstOrNull { it.name == "rootfile" }
            ?: fail(PublicationFailureCode.INVALID_EPUB, "The EPUB container has no package document.")
        val opfPath = resolveHref("", rootfile.attribute("full-path").orEmpty()).requireInternal().path
        val opf = parseXmlEntry(archive, opfPath, cancelled = cancelled)
        if (opf.name != "package" || opf.namespace != OPF_NAMESPACE ||
            opf.attribute("version")?.substringBefore('.')?.toIntOrNull() !in 2..3
        ) fail(PublicationFailureCode.MALFORMED_PACKAGE)
        return opfPath to opf
    }

    private fun inspect(archive: BoundedArchive, cancelled: () -> Unit): EpubCatalog {
        val (opfPath, opf) = readPackage(archive, cancelled)
        val version = checkNotNull(opf.attribute("version")).substringBefore('.').toInt()
        val metadataNode = opf.elements("metadata", OPF_NAMESPACE).firstOrNull()
            ?: fail(PublicationFailureCode.MALFORMED_PACKAGE)
        val manifestNode = opf.elements("manifest", OPF_NAMESPACE).firstOrNull()
            ?: fail(PublicationFailureCode.MALFORMED_PACKAGE)
        val spineNode = opf.elements("spine", OPF_NAMESPACE).firstOrNull()
            ?: fail(PublicationFailureCode.MALFORMED_PACKAGE)
        val metadata = readMetadata(metadataNode, version)
        detectProtection(archive, metadataNode, spineNode, cancelled)
        val fixedLayout = isFixedLayout(metadataNode)
        val readingDirection = if (spineNode.attribute("page-progression-direction") == "rtl")
            ReaderReadingDirection.RTL else ReaderReadingDirection.LTR
        val manifest = readManifest(manifestNode, opfPath)
        val spine = readSpine(spineNode, manifest, fixedLayout)
        if (spine.isEmpty() || spine.none { it.linear }) fail(PublicationFailureCode.MALFORMED_PACKAGE)
        val contentIdByPath = LinkedHashMap<String, String>()
        val sectionPlans = spine.mapIndexed { index, item ->
            val manifestItem = manifest.byPath[item.path]
                ?: fail(PublicationFailureCode.MISSING_SPINE_ITEM)
            if (manifestItem.mediaType !in XHTML_TYPES) fail(PublicationFailureCode.UNSUPPORTED_SPINE_CONTENT)
            val contentId = sectionContentId(index, item.path)
            if (contentIdByPath.put(item.path, contentId) != null) {
                fail(PublicationFailureCode.AMBIGUOUS_ARCHIVE, "Two spine entries resolve to one document path.")
            }
            SectionPlan(contentId, item.path, item.linear, title = "", fixedLayout = item.fixedLayout)
        }
        val toc = readNavigation(version, manifest, spineNode, archive, contentIdByPath, cancelled)
        val sectionTitles = LinkedHashMap<String, String>()
        toc.flatten().forEach { item -> sectionTitles.putIfAbsent(item.contentId, item.title) }
        val resourceByPath = LinkedHashMap<String, PublicationResourceRecord>()
        val cover = resolveCover(metadataNode, manifest)
        val coverRef = cover?.let { resolveManifestResource(it, archive, resourceByPath, PublicationResourceRole.COVER, cancelled) }
        if (coverRef is ReaderResourceRef.RemoteHttps) {
            issue("EXTERNAL_HTTPS_RESOURCE_DISABLED", PublicationCompatibilitySeverity.RESOURCE_DISABLED,
                "The cover is an HTTPS resource and remains disabled until this publication receives an explicit grant.")
        }
        val plans = ArrayList<SectionPlan>(sectionPlans.size)
        val anchorsByPath = LinkedHashMap<String, Map<String, ReaderAnchorTarget>>()
        val links = ArrayList<ReaderLinkTarget.Internal>()
        val stylesheetCatalog = StylesheetCatalog()
        val fixedImages = LinkedHashMap<String, List<ReaderBlock.Image>>()
        var totalXmlText = 0L
        var totalXmlNodes = 0L
        var totalBlocks = 0L
        var totalAnchors = 0L
        for (initial in sectionPlans) {
            checkCancelled(cancelled)
            val xmlBudget = PublicationXmlBudget()
            val xhtml = parseXmlEntry(archive, initial.path, xmlBudget, cancelled)
            totalXmlText += xmlBudget.textCharacters
            totalXmlNodes += xmlBudget.nodes
            if (totalXmlText > MAX_CATALOG_XML_TEXT || totalXmlNodes > MAX_CATALOG_XML_NODES) {
                fail(PublicationFailureCode.XML_LIMIT)
            }
            if (xhtml.name != "html" || xhtml.namespace != XHTML_NAMESPACE) fail(PublicationFailureCode.MALFORMED_XML,
                "A spine document is not XHTML.")
            val body = xhtml.descendants().firstOrNull { it.name == "body" && it.namespace == XHTML_NAMESPACE }
                ?: fail(PublicationFailureCode.MALFORMED_XML, "An XHTML spine document has no body.")
            reportInertHeadContent(xhtml)
            val stylesheets = loadStylesheets(xhtml, initial.path, manifest, archive, stylesheetCatalog, cancelled)
            val section = PublicationMarkupSection(
                contentId = initial.contentId,
                body = body,
                stylesheets = stylesheets,
                resolveLink = { raw ->
                    val target = resolveHref(initial.path, raw)
                    if (target.external) {
                        val https = externalHttps(target)
                        if (https != null) ReaderLinkTarget.ExternalHttps(https) else null
                    } else {
                        val targetContentId = contentIdByPath[target.path]
                        if (targetContentId == null) null else ReaderLinkTarget.Internal(
                            targetContentId,
                            target.fragment.takeIf(String::isNotEmpty),
                        )
                    }
                },
                resolveImage = { raw -> resolveImage(raw, initial.path, manifest, archive, resourceByPath, cancelled) },
            )
            val normalized = normalizer.normalize(section, cancelled)
            normalized.findings.forEach { finding ->
                retainFinding("${finding.code}\u0000${finding.contentId}\u0000${finding.blockId}", finding)
            }
            val content = normalized.blocks.ifEmpty {
                listOf(ReaderBlock.Unsupported("empty-section", "empty-section", "This section contains no readable content."))
            }
            totalBlocks += indexInternalLinks(content, links)
            totalAnchors += normalized.anchors.size
            if (totalBlocks > MAX_CATALOG_BLOCKS || totalAnchors > MAX_CATALOG_ANCHORS) {
                fail(PublicationFailureCode.BLOCK_LIMIT)
            }
            if (initial.fixedLayout) {
                if (content.all { it is ReaderBlock.Image && it.resource is ReaderResourceRef.Local }) {
                    fixedImages[initial.contentId] = content.filterIsInstance<ReaderBlock.Image>()
                } else {
                    issue("FIXED_LAYOUT_REFLOWED", PublicationCompatibilitySeverity.DEGRADED,
                        "A fixed-layout section with non-image content is retained in the native reflowable Reader.")
                }
            }
            anchorsByPath[initial.path] = normalized.anchors
            val htmlTitle = xhtml.descendants().firstOrNull { it.name == "title" }?.let { collapse(it.textContent()) }
            val title = sectionTitles[initial.contentId]
                ?: normalized.firstHeading
                ?: htmlTitle?.takeIf(String::isNotEmpty)
                ?: metadata.title
            plans += initial.copy(title = title, anchors = normalized.anchors)
        }
        val pathByContentId = contentIdByPath.entries.associate { it.value to it.key }
        for (target in links) validateInternalTarget(target, pathByContentId, anchorsByPath)
        val resolvedToc = validateToc(toc, pathByContentId, anchorsByPath)
        val fixedSequence = if (plans.all { it.fixedLayout } && fixedImages.size == plans.size)
            fixedImageSequence(plans, fixedImages) else null
        val sections = if (fixedSequence == null) plans.mapIndexed { index, plan ->
            PublicationSection(plan.contentId, plan.title, index, plan.linear, plan.anchors)
        } else listOf(PublicationSection(fixedSequence.contentId, metadata.title, 0, anchors = fixedSequence.anchors))
        return EpubCatalog(
            metadata.copy(cover = coverRef),
            sections,
            fixedSequence?.let { remapFixedToc(resolvedToc, plans, it) } ?: resolvedToc,
            if (fixedSequence == null) plans.associateBy(SectionPlan::contentId)
                else mapOf(fixedSequence.contentId to plans.first()),
            contentIdByPath,
            manifest,
            resourceByPath.toMap(),
            resourceByPath.values.toList(),
            readingDirection,
            fixedSequence,
        )
    }

    private fun fixedImageSequence(
        plans: List<SectionPlan>,
        sections: Map<String, List<ReaderBlock.Image>>,
    ): FixedImageSequence {
        val images = ArrayList<ReaderBlock.Image>()
        val anchors = LinkedHashMap<String, ReaderAnchorTarget>()
        val targets = LinkedHashMap<String, FixedSectionTarget>()
        for ((sectionOrdinal, plan) in plans.withIndex()) {
            val originalImages = sections.getValue(plan.contentId)
            val blockIds = HashMap<String, String>(originalImages.size)
            for ((imageOrdinal, image) in originalImages.withIndex()) {
                if (images.size >= MAX_FIXED_IMAGES) fail(PublicationFailureCode.BLOCK_LIMIT)
                val blockId = "image:$sectionOrdinal:$imageOrdinal"
                val anchor = "page:$sectionOrdinal:$imageOrdinal"
                blockIds[image.blockId] = blockId
                images += image.copy(blockId = blockId, anchorId = anchor)
                anchors[anchor] = ReaderAnchorTarget(blockId, 0)
            }
            val fragments = LinkedHashMap<String, String>()
            for ((fragment, target) in plan.anchors) {
                val alias = "fragment:" + sha256("${plan.contentId}\u0000$fragment".toByteArray(StandardCharsets.UTF_8)).take(32)
                val blockId = blockIds[target.blockId] ?: fail(PublicationFailureCode.MISSING_ANCHOR)
                anchors[alias] = ReaderAnchorTarget(blockId, target.codePointOffset)
                fragments[fragment] = alias
            }
            if (anchors.size > MAX_FIXED_ANCHORS) fail(PublicationFailureCode.BLOCK_LIMIT)
            targets[plan.contentId] = FixedSectionTarget("page:$sectionOrdinal:0", fragments)
        }
        return FixedImageSequence(plans.first().contentId, images, anchors, targets)
    }

    private fun remapFixedToc(
        toc: List<PublicationTocEntry>,
        plans: List<SectionPlan>,
        sequence: FixedImageSequence,
    ): List<PublicationTocEntry> {
        fun remap(entry: PublicationTocEntry): PublicationTocEntry {
            val target = sequence.targets.getValue(entry.contentId)
            val fragment = entry.fragment?.let { target.fragments[it] ?: fail(PublicationFailureCode.MISSING_ANCHOR) }
                ?: target.startAnchor
            return entry.copy(contentId = sequence.contentId, fragment = fragment, children = entry.children.map(::remap))
        }
        val authoredSections = toc.flatten().mapTo(HashSet()) { it.contentId }
        val missingPages = plans.filter { it.contentId !in authoredSections }.map { plan ->
            PublicationTocEntry(plan.title, sequence.contentId, sequence.targets.getValue(plan.contentId).startAnchor)
        }
        // Preserve the authored hierarchy/order verbatim; omitted pages remain directly navigable.
        return toc.map(::remap) + missingPages
    }

    private fun loadSection(
        catalog: EpubCatalog,
        contentId: String,
        identity: DocumentIdentity,
        cancelled: () -> Unit,
    ): ReaderDocument {
        checkCancelled(cancelled)
        checkIdentity(catalog, contentId, identity)
        catalog.fixedSequence?.let { sequence ->
            val document = ReaderDocument(identity, catalog.metadata.title, sequence.images, sequence.anchors,
                ReaderDocumentPresentation.FIXED_IMAGE, catalog.readingDirection, org.tsuyomi.shared.sourcecontract.ReaderDocumentKind.CHAPTER)
            checkCancelled(cancelled)
            return document
        }
        val plan = catalog.plans[contentId] ?: fail(PublicationFailureCode.MISSING_SPINE_ITEM)
        return withArchive(source, cancelled) { archive ->
            val xml = parseXmlEntry(archive, plan.path, cancelled = cancelled)
            if (xml.name != "html" || xml.namespace != XHTML_NAMESPACE) fail(PublicationFailureCode.MALFORMED_XML)
            val body = xml.descendants().firstOrNull { it.name == "body" && it.namespace == XHTML_NAMESPACE }
                ?: fail(PublicationFailureCode.MALFORMED_XML)
            val section = PublicationMarkupSection(
                contentId,
                body,
                loadStylesheets(xml, plan.path, catalog.manifest, archive, StylesheetCatalog(), cancelled),
                resolveLink = { raw -> resolveReaderLink(raw, plan.path, catalog.contentIdByPath) },
                resolveImage = { raw -> resolveReaderImage(raw, plan.path, catalog) },
            )
            val normalized = normalizer.normalize(section, cancelled)
            if (normalized.anchors != plan.anchors) fail(PublicationFailureCode.INVALID_ORIGINAL,
                "EPUB anchors changed after admission.")
            val blocks = normalized.blocks.ifEmpty {
                listOf(ReaderBlock.Unsupported("empty-section", "empty-section", "This section contains no readable content."))
            }
            ReaderDocument(identity, plan.title, blocks, normalized.anchors, readingDirection = catalog.readingDirection,
                kind = org.tsuyomi.shared.sourcecontract.ReaderDocumentKind.CHAPTER)
        }
    }

    private fun openResource(
        catalog: EpubCatalog,
        reference: ReaderResourceRef.Local,
        cancelled: () -> Unit,
    ): InputStream {
        if (reference.publicationId != publicationId) fail(PublicationFailureCode.MISSING_RESOURCE)
        val resource = catalog.resourcesByPath.entries.firstOrNull { it.value.reference == reference }
            ?: fail(PublicationFailureCode.MISSING_RESOURCE)
        val archive = BoundedArchive.open(source, cancelled)
        try {
            return archive.openBounded(resource.key, resource.value, cancelled)
        } catch (error: Throwable) {
            archive.close()
            throw error
        }
    }


    private fun readManifest(node: PublicationMarkupElement, opfPath: String): Manifest {
        val items = ArrayList<ManifestItem>()
        val byId = LinkedHashMap<String, ManifestItem>()
        val byPath = LinkedHashMap<String, ManifestItem>()
        for (item in node.elements("item", OPF_NAMESPACE)) {
            val id = item.attribute("id") ?: fail(PublicationFailureCode.MALFORMED_PACKAGE)
            val href = item.attribute("href") ?: fail(PublicationFailureCode.MALFORMED_PACKAGE)
            val mediaType = item.attribute("media-type")?.lowercase(Locale.ROOT)
                ?: fail(PublicationFailureCode.MALFORMED_PACKAGE)
            if (byId.containsKey(id)) fail(PublicationFailureCode.MALFORMED_PACKAGE, "Duplicate manifest id.")
            val resolved = resolveHref(opfPath, href)
            val record = ManifestItem(id, href, mediaType, item.attribute("properties").orEmpty().split(WHITESPACE).filter(String::isNotEmpty), resolved.path, resolved.external, resolved.externalUrl)
            items += record
            byId[id] = record
            if (!resolved.external && byPath.put(resolved.path, record) != null) {
                fail(PublicationFailureCode.MALFORMED_PACKAGE, "Duplicate canonical manifest path.")
            }
        }
        return Manifest(items, byId, byPath)
    }

    private fun readSpine(node: PublicationMarkupElement, manifest: Manifest, fixedLayout: Boolean): List<SpineItem> {
        val items = node.elements("itemref", OPF_NAMESPACE).map { ref ->
            val idRef = ref.attribute("idref") ?: fail(PublicationFailureCode.MISSING_SPINE_ITEM)
            val item = manifest.byId[idRef] ?: fail(PublicationFailureCode.MISSING_SPINE_ITEM)
            if (item.external) fail(PublicationFailureCode.EXTERNAL_RESOURCE_DENIED, "The reading order points outside the EPUB archive.")
            val properties = ref.attribute("properties").orEmpty().split(WHITESPACE)
            val fixed = when {
                "rendition:layout-reflowable" in properties -> false
                "rendition:layout-pre-paginated" in properties -> true
                else -> fixedLayout
            }
            SpineItem(idRef, item.path, ref.attribute("linear") != "no", fixed)
        }
        if (items.isEmpty()) fail(PublicationFailureCode.MALFORMED_PACKAGE)
        return items
    }

    private fun readMetadata(node: PublicationMarkupElement, version: Int): PublicationMetadata {
        val title = node.descendants().firstOrNull { it.namespace == DC_NAMESPACE && it.name == "title" }
            ?.let { collapse(it.textContent()) }
            ?.takeIf(String::isNotEmpty)
            ?: fail(PublicationFailureCode.MALFORMED_PACKAGE, "The package has no title.")
        if (title.codePointCount(0, title.length) > 512) fail(PublicationFailureCode.MALFORMED_PACKAGE, "The title exceeds the supported length.")
        val authors = node.descendants().filter { it.namespace == DC_NAMESPACE && it.name == "creator" }
            .map { collapse(it.textContent()) }.filter(String::isNotEmpty).distinct().take(64)
        val language = node.descendants().firstOrNull { it.namespace == DC_NAMESPACE && it.name == "language" }
            ?.let { collapse(it.textContent()) }?.takeIf(String::isNotEmpty)
        val identifier = node.descendants().firstOrNull { it.namespace == DC_NAMESPACE && it.name == "identifier" }
            ?.let { collapse(it.textContent()) }?.takeIf(String::isNotEmpty)?.take(1024)
        return PublicationMetadata(LocalPublicationFormat.EPUB, title, authors, language, identifier)
    }

    private fun isFixedLayout(metadata: PublicationMarkupElement): Boolean =
        metadata.descendants().any { element ->
            val property = element.attribute("property").orEmpty()
            val name = element.attribute("name").orEmpty()
            val content = element.attribute("content").orEmpty()
            property == "rendition:layout" && collapse(element.textContent()).equals("pre-paginated", true) ||
                name.equals("fixed-layout", true) && content.equals("true", true)
        }

    private fun detectProtection(
        archive: BoundedArchive,
        metadata: PublicationMarkupElement,
        spine: PublicationMarkupElement,
        cancelled: () -> Unit,
    ) {
        if (archive.has("META-INF/license.lcpl") || archive.has("META-INF/rights.xml")) {
            fail(PublicationFailureCode.PROTECTED_CONTENT, "An EPUB license/rights protection record was found.")
        }
        if (archive.has("META-INF/encryption.xml")) {
            val encryption = parseXmlEntry(archive, "META-INF/encryption.xml", cancelled = cancelled)
            val encrypted = encryption.descendants().filter { it.name == "EncryptedData" }
            for (data in encrypted) {
                checkCancelled(cancelled)
                val method = data.descendants().firstOrNull { it.name == "EncryptionMethod" }
                    ?.attribute("Algorithm").orEmpty()
                val uri = data.descendants().firstOrNull { it.name == "CipherReference" }
                    ?.attribute("URI")
                val path = uri?.let { resolveHref("", it).path }
                val isFontObfuscation = method in FONT_OBFUSCATION_ALGORITHMS && path != null &&
                    path.substringAfterLast('/').substringAfterLast('.').lowercase(Locale.ROOT) in OBFUSCATED_FONT_EXTENSIONS
                if (isFontObfuscation) {
                    issue("FONT_OBFUSCATION_UNSUPPORTED", PublicationCompatibilitySeverity.DEGRADED,
                        "An embedded-font obfuscation algorithm was identified; embedded fonts are not loaded.")
                } else {
                    fail(PublicationFailureCode.PROTECTED_CONTENT, "Encrypted publication content is not supported.")
                }
            }
        }
        val rightsFlags = metadata.descendants().any { element ->
            val name = element.attribute("name").orEmpty().lowercase(Locale.ROOT)
            val property = element.attribute("property").orEmpty().lowercase(Locale.ROOT)
            name.contains("encryption") || property.contains("encrypted")
        }
        if (rightsFlags) fail(PublicationFailureCode.PROTECTED_CONTENT, "Package metadata declares protected content.")
        if (spine.elements("itemref", OPF_NAMESPACE).isEmpty()) fail(PublicationFailureCode.MALFORMED_PACKAGE)
    }

    private fun resolveCover(metadata: PublicationMarkupElement, manifest: Manifest): ManifestItem? {
        manifest.items.firstOrNull { "cover-image" in it.properties }?.let { return it }
        val coverId = metadata.descendants().firstOrNull { it.attribute("name")?.equals("cover", true) == true }
            ?.attribute("content")
        return coverId?.let { manifest.byId[it] ?: fail(PublicationFailureCode.MISSING_RESOURCE) }
    }

    private fun resolveManifestResource(
        item: ManifestItem,
        archive: BoundedArchive,
        resourceByPath: LinkedHashMap<String, PublicationResourceRecord>,
        role: PublicationResourceRole,
        cancelled: () -> Unit,
    ): ReaderResourceRef? {
        if (item.external) {
            val https = validRemoteImage(item.externalUrl.orEmpty(), item.mediaType) ?: run {
                fail(PublicationFailureCode.EXTERNAL_RESOURCE_DENIED, "The external cover is not an allowed HTTPS raster resource.")
            }
            return ReaderResourceRef.RemoteHttps(https)
        }
        return localResource(item.path, item.mediaType, archive, resourceByPath, role, cancelled).reference
    }

    private fun resolveImage(
        raw: String,
        basePath: String,
        manifest: Manifest,
        archive: BoundedArchive,
        resources: LinkedHashMap<String, PublicationResourceRecord>,
        cancelled: () -> Unit,
    ): ReaderResourceRef? {
        val target = resolveHref(basePath, raw)
        if (target.external) {
            val mime = mediaTypeFromUrl(target.externalUrl.orEmpty()) ?: return null
            return validRemoteImage(target.externalUrl.orEmpty(), mime)?.let(ReaderResourceRef::RemoteHttps)
        }
        val item = manifest.byPath[target.path] ?: throw PublicationParseException(
            PublicationFailureCode.MISSING_RESOURCE,
            "An image reference is absent from the package manifest.",
        )
        return localResource(target.path, item.mediaType, archive, resources, PublicationResourceRole.CONTENT, cancelled).reference
    }

    private fun localResource(
        path: String,
        mediaType: String,
        archive: BoundedArchive,
        resources: LinkedHashMap<String, PublicationResourceRecord>,
        role: PublicationResourceRole,
        cancelled: () -> Unit,
    ): PublicationResourceRecord {
        resources[path]?.let { return it }
        if (mediaType !in SUPPORTED_IMAGE_TYPES) fail(PublicationFailureCode.UNSUPPORTED_RESOURCE_TYPE)
        val streamed = archive.digestEntry(path, MAX_RESOURCE_BYTES, cancelled)
        if (!hasValidSignature(mediaType, streamed.prefix)) fail(PublicationFailureCode.INVALID_RESOURCE)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            archive.openEntry(path, cancelled).use { input -> BitmapFactory.decodeStream(input, null, options) }
        } catch (error: RuntimeException) {
            throw PublicationParseException(PublicationFailureCode.INVALID_RESOURCE, cause = error)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth.toLong() * options.outHeight > MAX_IMAGE_PIXELS) {
            fail(PublicationFailureCode.INVALID_RESOURCE, "An image exceeds the supported decoded-pixel budget.")
        }
        val id = stableResourceId(publicationId, path)
        val reference = ReaderResourceRef.Local(publicationId, id, mediaType, streamed.sha256)
        return PublicationResourceRecord(reference, streamed.byteLength, role).also { resources[path] = it }
    }

    private fun loadStylesheets(
        root: PublicationMarkupElement,
        basePath: String,
        manifest: Manifest,
        archive: BoundedArchive,
        catalog: StylesheetCatalog,
        cancelled: () -> Unit,
    ): List<PublicationStylesheet> {
        val styles = ArrayList<PublicationStylesheet>()
        for (element in root.descendants()) {
            checkCancelled(cancelled)
            when (element.name) {
                "style" -> {
                    val text = element.textContent()
                    styles += catalog.inline.getOrPut(text) { catalog.admit(PublicationStylesheet("inline", text)) }
                }
                "link" -> if ("stylesheet" in element.attribute("rel").orEmpty().lowercase(Locale.ROOT).split(WHITESPACE)) {
                    val href = element.attribute("href") ?: continue
                    val target = resolveHref(basePath, href)
                    if (target.external) {
                        issue("EXTERNAL_STYLESHEET_DISABLED", PublicationCompatibilitySeverity.RESOURCE_DISABLED,
                            "External stylesheets are never fetched or executed.")
                    } else {
                        val item = manifest.byPath[target.path] ?: fail(PublicationFailureCode.MISSING_RESOURCE)
                        if (item.mediaType != "text/css") fail(PublicationFailureCode.UNSUPPORTED_RESOURCE_TYPE)
                        styles += catalog.external.getOrPut(target.path) {
                            val bytes = archive.read(target.path, MAX_STYLESHEET_BYTES, cancelled)
                            catalog.admit(PublicationStylesheet("package stylesheet", strictUtf8(bytes, PublicationFailureCode.INVALID_RESOURCE)))
                        }
                    }
                }
                "script" -> issue("INERT_SCRIPT", PublicationCompatibilitySeverity.DEGRADED,
                    "Embedded scripts are never executed.")
            }
            if (styles.size > MAX_SECTION_STYLESHEET_REFERENCES) fail(PublicationFailureCode.ARCHIVE_LIMIT)
        }
        // Charge every application, not just distinct cached sources. Keep occurrence order intact
        // because A/B/A references can carry meaningful CSS cascade ordering.
        if (styles.sumOf { catalog.byteLengths.getValue(it).toLong() } > MAX_SECTION_STYLESHEET_BYTES) {
            fail(PublicationFailureCode.ARCHIVE_LIMIT)
        }
        return styles
    }

    private fun readNavigation(
        version: Int,
        manifest: Manifest,
        spine: PublicationMarkupElement,
        archive: BoundedArchive,
        contentIds: Map<String, String>,
        cancelled: () -> Unit,
    ): List<PublicationTocEntry> {
        val navItem = if (version >= 3) manifest.items.firstOrNull { "nav" in it.properties } else null
        val raw = if (navItem != null) {
            if (navItem.external) fail(PublicationFailureCode.EXTERNAL_RESOURCE_DENIED)
            val root = parseXmlEntry(archive, navItem.path, cancelled = cancelled)
            val nav = root.descendants().firstOrNull { element ->
                element.name == "nav" && element.namespace == XHTML_NAMESPACE &&
                    element.attribute("type", EPUB_NAMESPACE).orEmpty().split(WHITESPACE).contains("toc")
            } ?: fail(PublicationFailureCode.MISSING_TOC)
            val ol = nav.elements("ol", XHTML_NAMESPACE).firstOrNull() ?: fail(PublicationFailureCode.MALFORMED_TOC)
            parseNavList(ol, navItem.path)
        } else {
            val ncxItem = manifest.byId[spine.attribute("toc")]
                ?: manifest.items.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
                ?: fail(PublicationFailureCode.MISSING_TOC)
            if (version >= 3) {
                issue("EPUB3_NCX_NAVIGATION", PublicationCompatibilitySeverity.NOTICE,
                    "The EPUB3 publication uses its authored NCX navigation because no EPUB navigation document is declared.")
            }
            if (ncxItem.external) fail(PublicationFailureCode.EXTERNAL_RESOURCE_DENIED)
            val root = parseXmlEntry(archive, ncxItem.path, cancelled = cancelled)
            val navMap = root.descendants().firstOrNull { it.name == "navMap" }
                ?: fail(PublicationFailureCode.MISSING_TOC)
            parseNcx(navMap, ncxItem.path)
        }
        fun resolve(rows: List<RawToc>): List<PublicationTocEntry> = rows.mapNotNull { item ->
            checkCancelled(cancelled)
            val children = resolve(item.children)
            val target = item.href?.let { resolveHref(item.basePath, it) }
            if (target == null || target.external) {
                if (target?.external == true) fail(PublicationFailureCode.EXTERNAL_RESOURCE_DENIED)
                if (children.isNotEmpty()) children.first().copy(title = item.title, children = children)
                else {
                    issue("TOC_ENTRY_WITHOUT_TARGET", PublicationCompatibilitySeverity.DEGRADED,
                        "A table-of-contents label without a target was omitted.")
                    null
                }
            } else {
                val contentId = contentIds[target.path] ?: fail(PublicationFailureCode.MISSING_SPINE_ITEM,
                    "A table-of-contents target is not present in the spine.")
                PublicationTocEntry(item.title.ifEmpty { "Section" }, contentId,
                    target.fragment.takeIf(String::isNotEmpty), children)
            }
        }
        return resolve(raw)
    }

    private fun parseNavList(list: PublicationMarkupElement, basePath: String): List<RawToc> =
        list.elements("li", XHTML_NAMESPACE).map { li ->
            val link = li.elements().firstOrNull { it.name == "a" || it.name == "span" }
                ?: fail(PublicationFailureCode.MALFORMED_TOC)
            val href = link.attribute("href")
            val nested = li.elements("ol", XHTML_NAMESPACE).firstOrNull()?.let { parseNavList(it, basePath) }.orEmpty()
            RawToc(collapse(link.textContent()), href, basePath, nested)
        }

    private fun parseNcx(navMap: PublicationMarkupElement, basePath: String): List<RawToc> {
        fun parsePoint(point: PublicationMarkupElement): RawToc {
            val label = point.descendants().firstOrNull { it.name == "navLabel" }
                ?.descendants()?.firstOrNull { it.name == "text" }?.let { collapse(it.textContent()) }.orEmpty()
            val href = point.elements("content").firstOrNull()?.attribute("src")
            val children = point.elements("navPoint").map(::parsePoint)
            return RawToc(label, href, basePath, children)
        }
        return navMap.elements("navPoint").map(::parsePoint)
    }

    private fun validateToc(
        entries: List<PublicationTocEntry>,
        pathByContentId: Map<String, String>,
        anchorsByPath: Map<String, Map<String, ReaderAnchorTarget>>,
    ): List<PublicationTocEntry> {
        fun walk(item: PublicationTocEntry): PublicationTocEntry {
            val path = pathByContentId[item.contentId]
                ?: fail(PublicationFailureCode.MISSING_SPINE_ITEM)
            if (item.fragment != null && item.fragment !in anchorsByPath[path].orEmpty()) {
                fail(PublicationFailureCode.MISSING_ANCHOR, "A TOC link names an absent fragment.")
            }
            return item.copy(children = item.children.map(::walk))
        }
        return entries.map(::walk)
    }

    private fun validateInternalTarget(
        target: ReaderLinkTarget.Internal,
        pathByContentId: Map<String, String>,
        anchorsByPath: Map<String, Map<String, ReaderAnchorTarget>>,
    ) {
        val path = pathByContentId[target.contentId]
            ?: fail(PublicationFailureCode.MISSING_SPINE_ITEM)
        if (target.fragment != null && target.fragment !in anchorsByPath[path].orEmpty()) {
            fail(PublicationFailureCode.MISSING_ANCHOR, "An internal link names an absent fragment.")
        }
    }

    private fun indexInternalLinks(blocks: List<ReaderBlock>, targets: MutableList<ReaderLinkTarget.Internal>): Long {
        var nestedCount = 0L
        for (root in blocks) for (block in root.nestedBlocks()) {
            nestedCount++
            for (span in block.inlineSpans()) {
                val link = span.kind as? ReaderInlineKind.Link ?: continue
                val target = link.target as? ReaderLinkTarget.Internal ?: continue
                if (targets.size >= MAX_CATALOG_LINKS) fail(PublicationFailureCode.BLOCK_LIMIT)
                targets += target
            }
        }
        return nestedCount
    }

    private fun resolveReaderLink(raw: String, basePath: String, contentIds: Map<String, String>): ReaderLinkTarget? {
        val target = resolveHref(basePath, raw)
        if (target.external) return externalHttps(target)?.let(ReaderLinkTarget::ExternalHttps)
        val contentId = contentIds[target.path] ?: return null
        return ReaderLinkTarget.Internal(contentId, target.fragment.takeIf(String::isNotEmpty))
    }

    private fun resolveReaderImage(raw: String, basePath: String, catalog: EpubCatalog): ReaderResourceRef? {
        val target = resolveHref(basePath, raw)
        if (target.external) {
            val mediaType = mediaTypeFromUrl(target.externalUrl.orEmpty()) ?: return null
            return validRemoteImage(target.externalUrl.orEmpty(), mediaType)?.let(ReaderResourceRef::RemoteHttps)
        }
        val item = catalog.manifest.byPath[target.path] ?: fail(PublicationFailureCode.MISSING_RESOURCE)
        return catalog.resourcesByPath[target.path]?.reference
            ?: fail(PublicationFailureCode.MISSING_RESOURCE, "Image resource was not admitted during publication indexing.")
    }

    private fun reportInertHeadContent(root: PublicationMarkupElement) {
        for (element in root.descendants()) {
            when (element.name) {
                "script" -> issue("INERT_SCRIPT", PublicationCompatibilitySeverity.DEGRADED,
                    "Embedded scripts are never executed.")
                "iframe", "object", "embed", "form" -> issue("UNSUPPORTED_ACTIVE_CONTENT", PublicationCompatibilitySeverity.DEGRADED,
                    "Embedded active content is not executed or loaded.")
                else -> Unit
            }
            if (element.attribute("xml:base", XML_NAMESPACE) != null) {
                issue("XML_BASE_IGNORED", PublicationCompatibilitySeverity.DEGRADED,
                    "An xml:base declaration is not interpreted; publication links use the spine document base.")
            }
        }
    }

    private fun requireEpubArchive(archive: BoundedArchive, cancelled: () -> Unit) {
        val first = archive.orderedEntries.firstOrNull()
        if (first?.canonicalPath != "mimetype" || first.entry.compressionMethod != PublicationZipArchive.STORED) {
            fail(PublicationFailureCode.INVALID_EPUB, "The EPUB mimetype entry must be first and stored.")
        }
        val value = archive.read("mimetype", MIME_TYPE.length, cancelled)
        if (!value.contentEquals(MIME_TYPE.toByteArray(StandardCharsets.US_ASCII))) {
            fail(PublicationFailureCode.INVALID_EPUB)
        }
    }

    private fun parseXmlEntry(
        archive: BoundedArchive,
        path: String,
        budget: PublicationXmlBudget = PublicationXmlBudget(),
        cancelled: () -> Unit = {},
    ): PublicationMarkupElement = parsePublicationXml(archive.read(path, MAX_XML_BYTES, cancelled), budget, cancelled)

    private fun issue(code: String, severity: PublicationCompatibilitySeverity, message: String, blockId: String? = null) {
        val key = "$code|$blockId"
        retainFinding(key, PublicationCompatibilityFinding(code, severity, message, blockId = blockId))
    }

    private fun retainFinding(key: String, finding: PublicationCompatibilityFinding) {
        if (key in findings) return
        val characters = key.length.toLong() + finding.code.length + finding.message.length +
            (finding.contentId?.length ?: 0) + (finding.blockId?.length ?: 0)
        if (findings.size >= MAX_CATALOG_FINDINGS || characters > MAX_FINDING_CHARACTERS - findingCharacters) {
            fail(PublicationFailureCode.ARCHIVE_LIMIT)
        }
        findingCharacters += characters
        findings[key] = finding
    }

    private fun compatibilityReport(items: Collection<PublicationCompatibilityFinding>): PublicationCompatibilityReport {
        val list = items.toList()
        return PublicationCompatibilityReport(
            if (list.any { it.severity != PublicationCompatibilitySeverity.NOTICE }) PublicationCompatibilityLevel.DEGRADED
            else PublicationCompatibilityLevel.SUPPORTED,
            list,
        )
    }

    private fun checkIdentity(catalog: EpubCatalog, contentId: String, identity: DocumentIdentity) {
        val book = identity.book as? BookIdentity.Local
        if (book?.publicationId != publicationId || identity.contentId != contentId || contentId !in catalog.plans) {
            fail(PublicationFailureCode.MISSING_SPINE_ITEM, "Caller identity does not match this publication section.")
        }
    }

    private fun resolveHref(baseDocument: String, rawHref: String): ResolvedHref {
        val uri = try {
            URI(rawHref)
        } catch (error: Exception) {
            throw PublicationParseException(PublicationFailureCode.INVALID_HREF, cause = error)
        }
        if (uri.scheme != null || uri.rawAuthority != null) {
            val external = uri.toASCIIString()
            return ResolvedHref(external, external, uri.fragment.orEmpty(), true, external)
        }
        val decodedPath = uri.path.orEmpty()
        if ('\\' in decodedPath || '\u0000' in decodedPath || decodedPath.startsWith('/') || DRIVE_PATH.containsMatchIn(decodedPath)) {
            fail(PublicationFailureCode.UNSAFE_RESOURCE_PATH)
        }
        val segments = if (baseDocument.isEmpty()) ArrayDeque() else ArrayDeque(baseDocument.split('/').dropLast(1))
        if (decodedPath.isNotEmpty()) {
            for (part in decodedPath.split('/')) when (part) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) fail(PublicationFailureCode.UNSAFE_RESOURCE_PATH) else segments.removeLast()
                else -> segments.addLast(part)
            }
        } else if (baseDocument.isNotEmpty()) {
            segments.addLast(baseDocument.substringAfterLast('/'))
        }
        val path = segments.joinToString("/")
        if (path.isEmpty()) fail(PublicationFailureCode.UNSAFE_RESOURCE_PATH)
        return ResolvedHref(path, path, uri.fragment.orEmpty(), false, null)
    }

    private fun externalHttps(target: ResolvedHref): String? {
        if (!target.external) return null
        val uri = runCatching { URI(target.externalUrl) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return URI("https", null, uri.host, uri.port, uri.path, uri.query, null).toASCIIString()
    }

    private fun validRemoteImage(url: String, mimeType: String): String? {
        if (mimeType !in REMOTE_IMAGE_TYPES) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return URI("https", null, uri.host, uri.port, uri.path, uri.query, null).toASCIIString()
    }

    private fun mediaTypeFromUrl(url: String): String? = when (
        URI(url).path.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
    ) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> null
    }

    private fun stableResourceId(publicationId: String, path: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$publicationId\u0000$path".toByteArray(StandardCharsets.UTF_8))
        val uuidBytes = bytes.copyOfRange(0, 16)
        uuidBytes[6] = ((uuidBytes[6].toInt() and 0x0f) or 0x50).toByte()
        uuidBytes[8] = ((uuidBytes[8].toInt() and 0x3f) or 0x80).toByte()
        val buffer = ByteBuffer.wrap(uuidBytes)
        return UUID(buffer.long, buffer.long).toString()
    }

    private fun sectionContentId(index: Int, path: String): String = "epub:$index:${sha256(path.toByteArray(StandardCharsets.UTF_8)).take(24)}"
    private fun List<PublicationTocEntry>.flatten(): List<PublicationTocEntry> = buildList {
        fun visit(entries: List<PublicationTocEntry>) {
            for (entry in entries) {
                add(entry)
                visit(entry.children)
            }
        }
        visit(this@flatten)
    }



    private class StylesheetCatalog {
        val external = LinkedHashMap<String, PublicationStylesheet>()
        val inline = LinkedHashMap<String, PublicationStylesheet>()
        val byteLengths = IdentityHashMap<PublicationStylesheet, Int>()
        private var retainedBytes = 0L

        fun admit(stylesheet: PublicationStylesheet): PublicationStylesheet {
            val size = stylesheet.text.toByteArray(StandardCharsets.UTF_8).size
            if (size > MAX_STYLESHEET_BYTES || size.toLong() > MAX_TOTAL_STYLESHEET_BYTES - retainedBytes) {
                fail(PublicationFailureCode.ARCHIVE_LIMIT)
            }
            retainedBytes += size
            byteLengths[stylesheet] = size
            return stylesheet
        }
    }

    internal data class FixedSectionTarget(val startAnchor: String, val fragments: Map<String, String>)
    internal data class FixedImageSequence(
        val contentId: String,
        val images: List<ReaderBlock.Image>,
        val anchors: Map<String, ReaderAnchorTarget>,
        val targets: Map<String, FixedSectionTarget>,
    )

    internal data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: List<String>,
        val path: String,
        val external: Boolean,
        val externalUrl: String?,
    )

    internal data class Manifest(
        val items: List<ManifestItem>,
        val byId: Map<String, ManifestItem>,
        val byPath: Map<String, ManifestItem>,
    )

    private data class SpineItem(val idRef: String, val path: String, val linear: Boolean, val fixedLayout: Boolean)
    internal data class SectionPlan(
        val contentId: String,
        val path: String,
        val linear: Boolean,
        val title: String,
        val fixedLayout: Boolean,
        val anchors: Map<String, ReaderAnchorTarget> = emptyMap(),
    )
    internal data class EpubCatalog(
        val metadata: PublicationMetadata,
        val sections: List<PublicationSection>,
        val toc: List<PublicationTocEntry>,
        val plans: Map<String, SectionPlan>,
        val contentIdByPath: Map<String, String>,
        val manifest: Manifest,
        val resourcesByPath: Map<String, PublicationResourceRecord>,
        val resources: List<PublicationResourceRecord>,
        val readingDirection: ReaderReadingDirection,
        val fixedSequence: FixedImageSequence?,
    )
    private data class RawToc(val title: String, val href: String?, val basePath: String, val children: List<RawToc>)
    private data class ResolvedHref(
        val canonical: String,
        val path: String,
        val fragment: String,
        val external: Boolean,
        val externalUrl: String?,
    ) {
        fun requireInternal() = if (external) fail(PublicationFailureCode.UNSAFE_RESOURCE_PATH) else this
    }

    companion object {
        private const val MIME_TYPE = "application/epub+zip"
        private const val OPF_NAMESPACE = "http://www.idpf.org/2007/opf"
        private const val DC_NAMESPACE = "http://purl.org/dc/elements/1.1/"
        private const val XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml"
        private const val EPUB_NAMESPACE = "http://www.idpf.org/2007/ops"
        private const val XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace"
        private const val NCX_NAMESPACE = "http://www.daisy.org/z3986/2005/ncx/"
        private const val MAX_IMAGE_PIXELS = 50_000_000L
        private const val MAX_STYLESHEET_BYTES = 256 * 1024
        private const val MAX_TOTAL_STYLESHEET_BYTES = 1024 * 1024
        private const val MAX_CATALOG_XML_TEXT = 64L * 1024 * 1024
        private const val MAX_CATALOG_XML_NODES = 2_000_000L
        private const val MAX_CATALOG_BLOCKS = 1_000_000L
        private const val MAX_CATALOG_ANCHORS = 250_000L
        private const val MAX_CATALOG_LINKS = 250_000
        private const val MAX_FIXED_IMAGES = 20_000
        private const val MAX_FIXED_ANCHORS = 50_000
        private const val MAX_CATALOG_FINDINGS = 50_000
        private const val MAX_FINDING_CHARACTERS = 4L * 1024 * 1024
        private const val MAX_SECTION_STYLESHEET_REFERENCES = 4096
        private const val MAX_SECTION_STYLESHEET_BYTES = 1024L * 1024
        private val WHITESPACE = Regex("\\s+")
        private val DRIVE_PATH = Regex("^[A-Za-z]:")
        private val XHTML_TYPES = setOf("application/xhtml+xml", "text/html")
        private val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
        private val REMOTE_IMAGE_TYPES = setOf("image/jpeg", "image/png")
        private val OBFUSCATED_FONT_EXTENSIONS = setOf("otf", "ttf", "woff", "woff2")
        private val FONT_OBFUSCATION_ALGORITHMS = setOf(
            "http://www.idpf.org/2008/embedding",
            "http://ns.adobe.com/pdf/enc#RC",
        )

        private fun fail(code: PublicationFailureCode, message: String = code.name): Nothing =
            throw PublicationParseException(code, message)
    }
}

private class BoundedArchive private constructor(
    private val archive: PublicationZipArchive,
    private val cancelled: () -> Unit,
) {
    data class Entry(val canonicalPath: String, val rawName: String, val entry: PublicationZipEntry)
    data class StreamedEntry(val byteLength: Long, val sha256: String, val prefix: ByteArray)

    val orderedEntries = ArrayList<Entry>()
    private val entriesByPath = LinkedHashMap<String, Entry>()
    private var expandedRead = 0L
    fun close() = archive.close()

    fun has(path: String): Boolean = path in entriesByPath
    fun openEntry(path: String, check: () -> Unit): InputStream {
        val item = entriesByPath[path] ?: throw PublicationParseException(PublicationFailureCode.MISSING_RESOURCE)
        return LimitedEntryStream(archive.openEntry(item.rawName), item.entry.sizeBytes, check) { count ->
            if (count.toLong() > MAX_INFLATED_READ_BYTES - expandedRead) {
                throw PublicationParseException(PublicationFailureCode.ARCHIVE_LIMIT)
            }
            expandedRead += count
        }
    }

    fun digestEntry(path: String, limit: Int, check: () -> Unit): StreamedEntry {
        val item = entriesByPath[path] ?: throw PublicationParseException(PublicationFailureCode.MISSING_RESOURCE)
        if (item.entry.sizeBytes < 0 || item.entry.sizeBytes > limit) {
            throw PublicationParseException(PublicationFailureCode.ARCHIVE_LIMIT)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val prefix = ByteArray(12)
        var prefixSize = 0
        var readBytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            openEntry(path, check).use { input ->
                while (true) {
                    checkCancelled(check)
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (count.toLong() > limit - readBytes) {
                        throw PublicationParseException(PublicationFailureCode.ARCHIVE_LIMIT)
                    }
                    digest.update(buffer, 0, count)
                    val prefixBytes = minOf(count, prefix.size - prefixSize)
                    if (prefixBytes > 0) {
                        buffer.copyInto(prefix, prefixSize, 0, prefixBytes)
                        prefixSize += prefixBytes
                    }
                    readBytes += count
                }
            }
        } catch (error: PublicationParseException) {
            throw error
        } catch (error: IOException) {
            throw PublicationParseException(PublicationFailureCode.INVALID_ARCHIVE, cause = error)
        }
        if (readBytes != item.entry.sizeBytes) throw PublicationParseException(PublicationFailureCode.INVALID_ARCHIVE)
        return StreamedEntry(readBytes, digest.digest().toHex(), prefix.copyOf(prefixSize))
    }

    fun read(path: String, limit: Int, check: () -> Unit): ByteArray {
        val item = entriesByPath[path] ?: throw PublicationParseException(PublicationFailureCode.MISSING_RESOURCE)
        if (item.entry.sizeBytes < 0 || item.entry.sizeBytes > limit) {
            throw PublicationParseException(if (limit == MAX_XML_BYTES) PublicationFailureCode.XML_LIMIT else PublicationFailureCode.ARCHIVE_LIMIT)
        }
        val output = ByteArrayOutputStream(minOf(item.entry.sizeBytes.toInt(), 8192))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            openEntry(path, check).use { input ->
                while (true) {
                    checkCancelled(check)
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (count > limit - output.size()) {
                        throw PublicationParseException(if (limit == MAX_XML_BYTES) PublicationFailureCode.XML_LIMIT else PublicationFailureCode.ARCHIVE_LIMIT)
                    }
                    output.write(buffer, 0, count)
                }
            }
        } catch (error: PublicationParseException) {
            throw error
        } catch (error: IOException) {
            throw PublicationParseException(PublicationFailureCode.INVALID_ARCHIVE, cause = error)
        }
        if (output.size().toLong() != item.entry.sizeBytes) throw PublicationParseException(PublicationFailureCode.INVALID_ARCHIVE)
        return output.toByteArray()
    }

    fun openBounded(path: String, record: PublicationResourceRecord, check: () -> Unit): InputStream {
        val item = entriesByPath[path] ?: throw PublicationParseException(PublicationFailureCode.MISSING_RESOURCE)
        if (item.entry.sizeBytes != record.byteLength || item.entry.sizeBytes > MAX_RESOURCE_BYTES) {
            throw PublicationParseException(PublicationFailureCode.INVALID_RESOURCE)
        }
        return VerifiedResourceStream(archive.openEntry(item.rawName), archive, record, check)
    }

    private fun validateAndReadDirectory() {
        if (orderedEntries.isNotEmpty()) return
        var expanded = 0L
        try {
            for (entry in archive.entries()) {
                checkCancelled(cancelled)
                if (orderedEntries.size >= MAX_ARCHIVE_ENTRIES) throw PublicationParseException(PublicationFailureCode.ARCHIVE_LIMIT)
                val path = canonicalArchivePath(entry.name)
                if (entry.sizeBytes < 0 || entry.sizeBytes > MAX_ENTRY_BYTES || entry.compressedSizeBytes < 0) {
                    throw PublicationParseException(PublicationFailureCode.ARCHIVE_LIMIT)
                }
                if (entry.sizeBytes > 0 && entry.compressedSizeBytes == 0L) throw PublicationParseException(PublicationFailureCode.ARCHIVE_LIMIT)
                if (entry.sizeBytes > 0 && entry.sizeBytes.toDouble() / entry.compressedSizeBytes > MAX_COMPRESSION_RATIO) {
                    throw PublicationParseException(PublicationFailureCode.ARCHIVE_LIMIT)
                }
                if (entry.compressionMethod != PublicationZipArchive.STORED && entry.compressionMethod != PublicationZipArchive.DEFLATED) {
                    throw PublicationParseException(PublicationFailureCode.UNSUPPORTED_COMPRESSION)
                }
                expanded += entry.sizeBytes
                if (expanded > MAX_EXPANDED_BYTES) throw PublicationParseException(PublicationFailureCode.ARCHIVE_LIMIT)
                val item = Entry(path, entry.name, entry)
                if (entriesByPath.put(path, item) != null) throw PublicationParseException(PublicationFailureCode.AMBIGUOUS_ARCHIVE)
                orderedEntries += item
            }
        } catch (error: PublicationParseException) {
            throw error
        } catch (error: Exception) {
            throw PublicationParseException(PublicationFailureCode.INVALID_ARCHIVE, cause = error)
        }
        if (orderedEntries.isEmpty()) throw PublicationParseException(PublicationFailureCode.INVALID_ARCHIVE)
    }

    companion object {
        fun open(source: PublicationSource, cancelled: () -> Unit): BoundedArchive {
            if (source.sizeBytes <= 0 || source.sizeBytes > MAX_ARCHIVE_BYTES) {
                throw PublicationParseException(PublicationFailureCode.ORIGINAL_LIMIT)
            }
            val archive = try {
                source.openArchive()
            } catch (error: IOException) {
                throw PublicationParseException(PublicationFailureCode.INVALID_ARCHIVE, cause = error)
            }
            return try {
                BoundedArchive(archive, cancelled).also(BoundedArchive::validateAndReadDirectory)
            } catch (error: Throwable) {
                archive.close()
                throw error
            }
        }
    }
}

private inline fun <T> withArchive(source: PublicationSource, noinline cancelled: () -> Unit, block: (BoundedArchive) -> T): T {
    val archive = BoundedArchive.open(source, cancelled)
    try {
        return block(archive)
    } finally {
        archive.close()
    }
}


private class LimitedEntryStream(
    input: InputStream,
    private val byteLimit: Long,
    private val cancelled: () -> Unit,
    private val accountRead: (Int) -> Unit,
) : FilterInputStream(input) {
    private var bytesRead = 0L

    override fun read(): Int {
        checkCancelled(cancelled)
        val value = super.read()
        if (value >= 0) account(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkCancelled(cancelled)
        val count = super.read(buffer, offset, length)
        if (count > 0) account(count)
        return count
    }

    override fun skip(count: Long): Long {
        checkCancelled(cancelled)
        val skipped = super.skip(count)
        if (skipped > byteLimit - bytesRead) throw IOException("Archive entry exceeded its declared size")
        if (skipped > 0) account(skipped.toInt())
        return skipped
    }

    private fun account(count: Int) {
        if (count.toLong() > byteLimit - bytesRead) throw IOException("Archive entry exceeded its declared size")
        bytesRead += count
        accountRead(count)
    }
}

private class VerifiedResourceStream(
    input: InputStream,
    private val archive: PublicationZipArchive,
    private val expected: PublicationResourceRecord,
    private val cancelled: () -> Unit,
) : FilterInputStream(input) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var readBytes = 0L
    private var verified = false

    override fun read(): Int {
        checkCancelled(cancelled)
        val value = super.read()
        if (value >= 0) {
            if (++readBytes > expected.byteLength) failResource()
            digest.update(value.toByte())
        } else verifyEnd()
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkCancelled(cancelled)
        val count = super.read(buffer, offset, length)
        if (count > 0) {
            readBytes += count
            if (readBytes > expected.byteLength) failResource()
            digest.update(buffer, offset, count)
        } else if (count < 0) verifyEnd()
        return count
    }

    override fun close() {
        try { super.close() } finally { archive.close() }
    }

    private fun verifyEnd() {
        if (verified) return
        verified = true
        if (readBytes != expected.byteLength || digest.toHex() != expected.reference.sha256) failResource()
    }

    private fun failResource(): Nothing = throw IOException("Publication resource failed its digest or size check")
}

private fun canonicalArchivePath(name: String): String {
    if (name.isEmpty() || name.startsWith('/') || '\\' in name || '\u0000' in name || Regex("^[A-Za-z]:").containsMatchIn(name)) {
        throw PublicationParseException(PublicationFailureCode.UNSAFE_ARCHIVE_PATH)
    }
    val directory = name.endsWith('/')
    val parts = name.split('/').let { if (directory) it.dropLast(1) else it }
    if (parts.isEmpty() || parts.any { it.isEmpty() || it == "." || it == ".." }) {
        throw PublicationParseException(PublicationFailureCode.UNSAFE_ARCHIVE_PATH)
    }
    return parts.joinToString("/")
}

private fun resolveHref(baseDocument: String, rawHref: String): ResolvedHref {
    val uri = try { URI(rawHref) } catch (error: Exception) {
        throw PublicationParseException(PublicationFailureCode.INVALID_HREF, cause = error)
    }
    if (uri.scheme != null || uri.rawAuthority != null) {
        val external = uri.toASCIIString()
        return ResolvedHref(external, external, uri.fragment.orEmpty(), true, external)
    }
    val decodedPath = uri.path.orEmpty()
    if ('\\' in decodedPath || '\u0000' in decodedPath || decodedPath.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(decodedPath)) {
        throw PublicationParseException(PublicationFailureCode.UNSAFE_RESOURCE_PATH)
    }
    val segments = ArrayDeque<String>()
    if (baseDocument.isNotEmpty()) baseDocument.split('/').dropLast(1).forEach(segments::addLast)
    for (part in decodedPath.split('/')) when (part) {
        "", "." -> Unit
        ".." -> if (segments.isEmpty()) throw PublicationParseException(PublicationFailureCode.UNSAFE_RESOURCE_PATH) else segments.removeLast()
        else -> segments.addLast(part)
    }
    if (decodedPath.isEmpty() && baseDocument.isNotEmpty()) segments.addLast(baseDocument.substringAfterLast('/'))
    val path = segments.joinToString("/")
    if (path.isEmpty()) throw PublicationParseException(PublicationFailureCode.UNSAFE_RESOURCE_PATH)
    return ResolvedHref(path, path, uri.fragment.orEmpty(), false, null)
}

private data class ResolvedHref(val canonical: String, val path: String, val fragment: String, val external: Boolean, val externalUrl: String?) {
    fun requireInternal(): ResolvedHref = if (external) throw PublicationParseException(PublicationFailureCode.UNSAFE_RESOURCE_PATH) else this
}

private fun validateOriginal(source: PublicationSource) {
    if (source.sizeBytes <= 0 || source.sizeBytes > 32L * 1024 * 1024 || !SHA256.matches(source.sha256)) {
        throw PublicationParseException(PublicationFailureCode.ORIGINAL_LIMIT)
    }
}

private fun checkCancelled(cancelled: () -> Unit) = cancelled()
private fun strictUtf8(bytes: ByteArray, code: PublicationFailureCode): String = try {
    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
} catch (error: Exception) {
    throw PublicationParseException(code, cause = error)
}
private fun hasValidSignature(mime: String, bytes: ByteArray): Boolean = when (mime) {
    "image/png" -> bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
    "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
    "image/gif" -> bytes.startsWith("GIF87a".toByteArray()) || bytes.startsWith("GIF89a".toByteArray())
    "image/webp" -> bytes.size >= 12 && bytes.startsWith("RIFF".toByteArray()) && bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
    else -> false
}
private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
private fun MessageDigest.toHex(): String = digest().toHex()
private fun collapse(value: String): String = value.trim().replace(Regex("[\\s　]+"), " ")
private fun PublicationMarkupElement.descendants(): List<PublicationMarkupElement> = buildList {
    fun visit(parent: PublicationMarkupElement) {
        for (part in parent.children) if (part is PublicationMarkupPart.Element) {
            add(part.value)
            visit(part.value)
        }
    }
    visit(this@descendants)
}
private fun internalLinksForCompatibility(blocks: List<ReaderBlock>): List<ReaderLinkTarget.Internal> =
    blocks.flatMap(ReaderBlock::nestedBlocks).flatMap { block ->
        block.inlineSpans().mapNotNull { span ->
            val link = span.kind as? ReaderInlineKind.Link ?: return@mapNotNull null
            link.target as? ReaderLinkTarget.Internal
        }
    }
