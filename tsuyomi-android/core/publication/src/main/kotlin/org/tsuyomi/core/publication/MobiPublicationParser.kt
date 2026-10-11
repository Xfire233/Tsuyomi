/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import android.graphics.BitmapFactory
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.locator.DocumentIdentity
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.ReaderAnchorTarget
import org.tsuyomi.shared.sourcecontract.ReaderBlock
import org.tsuyomi.shared.sourcecontract.ReaderDocument
import org.tsuyomi.shared.sourcecontract.ReaderLinkTarget
import org.tsuyomi.shared.sourcecontract.ReaderResourceRef

/** Admits isolated MOBI/KF8 HTML into the same inert Reader grammar used by EPUB. */
class MobiPublicationParser(
    private val publicationId: String,
    private val originalSha256: String,
    private val originalSizeBytes: Long,
    private val content: MobiPublicationContent,
) {
    private val normalizer = PublicationMarkupNormalizer()

    /** Reuses completed host admission; isolated raw format reconstruction remains unchanged. */
    fun openAdmitted(
        record: PublicationAdmissionRecord,
        options: PublicationOptions = PublicationOptions(),
        cancelled: () -> Unit = {},
    ): ParsedPublication {
        checkCancelled(cancelled)
        validateOriginal()
        val catalog = transportCall(PublicationFailureCode.INVALID_ORIGINAL) { content.catalog }
        validateCatalog(catalog)
        record.requireBinding(publicationId, catalog.format, originalSha256, originalSizeBytes, options)
        val admitted = record.mobi ?: fail(PublicationFailureCode.INVALID_ORIGINAL)
        if (catalog != admitted.transportCatalog) fail(PublicationFailureCode.INVALID_ORIGINAL,
            "The MOBI catalog changed after admission.")
        return publication(record, admitted)
    }

    private fun publication(record: PublicationAdmissionRecord, admitted: MobiAdmissionCatalog): ParsedPublication {
        val sectionPlans = admitted.plans
        val resourcesById = admitted.resources
        val resourcePlansByReference = resourcesById.values.associateBy { it.record.reference }
        return ParsedPublication(
            publicationId, record.format, originalSha256, originalSizeBytes, record.contentRevision,
            PublicationOptions(), admitted.metadata,
            sectionPlans.values.map { plan -> PublicationSection(plan.contentId, plan.title, plan.ordinal,
                plan.linear, plan.anchors, plan.codePointStart) },
            admitted.toc, resourcesById.values.map(ResourcePlan::record), record.compatibility,
            admissionRecord = record,
            loadSectionBlock = { contentId, identity, check ->
                loadSection(sectionPlans, resourcesById, contentId, identity, record.contentRevision, check)
            },
            openResourceStream = { reference, check -> openResource(resourcePlansByReference, reference, check) },
        )
    }

    fun parse(cancelled: () -> Unit = {}): ParsedPublication {
        var retained = false
        var stage = "original_validation"
        var sectionOrdinal = -1
        try {
            checkCancelled(cancelled)
            validateOriginal()
            stage = "catalog_transport"
            val catalog = transportCall(PublicationFailureCode.INVALID_ORIGINAL) { content.catalog }
            stage = "catalog_validation"
            validateCatalog(catalog)

            val sectionPlans = LinkedHashMap<String, SectionPlan>(catalog.sections.size)
            val resourcesById = LinkedHashMap<String, ResourcePlan>()
            var totalTextBytes = 0L
            var totalResourceBytes = 0L
            val stylesheetCatalog = StylesheetCatalog()
            var totalAnchorCount = 0
            val findings = LinkedHashMap<String, PublicationCompatibilityFinding>()
            val internalLinkTargets = ArrayList<ReaderLinkTarget.Internal>()

            catalog.coverResourceId?.let { resourceId ->
                stage = "cover_resource_validation"
                totalResourceBytes = ensureResource(resourcesById, resourceId,
                    PublicationResourceRole.COVER, totalResourceBytes, cancelled)
            }

            for ((index, catalogSection) in catalog.sections.withIndex()) {
                checkCancelled(cancelled)
                sectionOrdinal = index
                stage = "section_transport"
                val section = transportCall(PublicationFailureCode.INVALID_ORIGINAL) {
                    content.section(catalogSection.contentId, cancelled)
                }
                stage = "section_payload_validation"
                validateSectionPayload(section, catalogSection.contentId, stylesheetCatalog)
                if (internalLinkTargets.size > MAX_TOTAL_INTERNAL_LINKS - section.links.size) {
                    fail(PublicationFailureCode.BLOCK_LIMIT, "The MOBI publication exceeded the internal-link budget.")
                }
                internalLinkTargets.addAll(section.links.values)
                val sectionBytes = sectionTransportBytes(section)
                if (sectionBytes > MAX_SECTION_JSON_BYTES) fail(PublicationFailureCode.BLOCK_LIMIT,
                    "A MOBI section exceeded the transport output budget.")

                val declaredResources = LinkedHashMap<String, Unit>(section.resourceIds.size)
                stage = "section_resource_validation"
                for (resourceId in section.resourceIds) {
                    checkCancelled(cancelled)
                    validateResourceId(resourceId)
                    if (declaredResources.put(resourceId, Unit) != null) fail(PublicationFailureCode.INVALID_ORIGINAL,
                        "A MOBI section repeats a resource identifier.")
                    val role = if (resourceId == catalog.coverResourceId) PublicationResourceRole.COVER
                    else PublicationResourceRole.CONTENT
                    totalResourceBytes = ensureResource(resourcesById, resourceId, role, totalResourceBytes, cancelled)
                }

                val sectionDigest = digestSection(section)
                stage = "markup_normalization"
                val normalized = normalizer.normalizeHtml(
                    contentId = section.contentId,
                    html = section.html,
                    stylesheets = section.stylesheets,
                    resolveLink = { raw ->
                        checkCancelled(cancelled)
                        section.links[raw] ?: safeExternalLink(raw)
                    },
                    resolveImage = { raw ->
                        checkCancelled(cancelled)
                        val localId = localResourceId(raw)
                        if (localId != null) {
                            if (localId !in declaredResources) fail(PublicationFailureCode.MISSING_RESOURCE,
                                "A MOBI image target is absent from the section resource allowlist.")
                            resourcesById[localId]?.record?.reference
                                ?: fail(PublicationFailureCode.MISSING_RESOURCE)
                        } else {
                            safeExternalImage(raw)
                        }
                    },
                    cancelled = cancelled,
                )
                stage = "normalized_section_validation"
                normalized.findings.forEach { finding ->
                    findings.putIfAbsent(findingKey(finding), finding)
                }
                val blocks = readableBlocks(normalized.blocks, section.contentId, findings)
                val sectionTextBytes = blocks.sumOf(::readerTextBytes)
                if (sectionTextBytes > MAX_SECTION_TEXT_BYTES) fail(PublicationFailureCode.TEXT_LIMIT,
                    "A MOBI section exceeded the normalized text budget.")
                totalTextBytes += sectionTextBytes
                if (totalTextBytes > MAX_TOTAL_TEXT_BYTES) fail(PublicationFailureCode.TEXT_LIMIT,
                    "The MOBI publication exceeded the aggregate expanded-text budget.")

                if (normalized.anchors.size > MAX_READER_ANCHORS) fail(PublicationFailureCode.BLOCK_LIMIT,
                    "A MOBI section exceeded the Reader anchor budget.")
                totalAnchorCount += normalized.anchors.size
                if (totalAnchorCount > MAX_TOTAL_ANCHORS) fail(PublicationFailureCode.BLOCK_LIMIT,
                    "The MOBI publication exceeded the aggregate anchor budget.")

                val plan = SectionPlan(
                    contentId = catalogSection.contentId,
                    title = collapse(if (catalogSection.contentId in catalog.authoredSectionIds) catalogSection.title
                        else normalized.firstHeading ?: catalogSection.title),
                    ordinal = index,
                    linear = catalogSection.linear,
                    codePointStart = catalogSection.codePointStart,
                    inputDigest = sectionDigest,
                    anchors = normalized.anchors,
                )
                if (sectionPlans.put(plan.contentId, plan) != null) fail(PublicationFailureCode.INVALID_ORIGINAL,
                    "The MOBI catalog contains duplicate section identifiers.")
            }

            sectionOrdinal = -1
            stage = "toc_anchor_validation"
            val validatedToc = validateToc(catalog.tableOfContents, sectionPlans)
            stage = "internal_anchor_validation"
            internalLinkTargets.forEach { target -> validateTarget(target, sectionPlans) }
            val cover = catalog.coverResourceId?.let { resourceId ->
                resourcesById[resourceId]?.record?.reference ?: fail(PublicationFailureCode.MISSING_RESOURCE)
            }
            val metadata = PublicationMetadata(
                format = catalog.format,
                title = collapse(catalog.title),
                authors = catalog.authors.map(::collapse),
                language = catalog.language?.let(::collapse),
                identifier = catalog.identifier?.let(::collapse),
                cover = cover,
            )
            val admitted = MobiAdmissionCatalog(catalog, metadata, validatedToc, sectionPlans, resourcesById)
            val record = PublicationAdmissionRecord(publicationId, catalog.format, originalSha256,
                originalSizeBytes, publicationContentRevision(catalog.format, originalSha256),
                compatibilityReport(findings.values), mobi = admitted)
            val parsed = publication(record, admitted)
            retained = true
            return parsed
        } catch (error: PublicationParseException) {
            throw PublicationParseException(error.code,
                "MOBI_ADMISSION stage=$stage sectionOrdinal=$sectionOrdinal code=${error.code.name}", error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            throw IOException("MOBI_ADMISSION stage=$stage sectionOrdinal=$sectionOrdinal code=TRANSPORT_FAILURE", error)
        } catch (error: Exception) {
            throw PublicationParseException(PublicationFailureCode.INVALID_ORIGINAL,
                "MOBI_ADMISSION stage=$stage sectionOrdinal=$sectionOrdinal code=INVALID_ORIGINAL", error)
        } finally {
            if (!retained) runCatching { content.close() }
        }
    }

    private fun loadSection(
        sectionPlans: Map<String, SectionPlan>,
        resourcesById: Map<String, ResourcePlan>,
        contentId: String,
        identity: DocumentIdentity,
        expectedRevision: String,
        cancelled: () -> Unit,
    ): ReaderDocument {
        checkCancelled(cancelled)
        val plan = sectionPlans[contentId] ?: fail(PublicationFailureCode.MISSING_SPINE_ITEM)
        val book = identity.book as? BookIdentity.Local
        if (book?.publicationId != publicationId || identity.contentId != contentId ||
            (identity.revision != null && identity.revision != expectedRevision)) {
            fail(PublicationFailureCode.MISSING_SPINE_ITEM, "Caller identity does not match this MOBI section.")
        }
        val section = transportCall(PublicationFailureCode.INVALID_ORIGINAL) {
            content.section(contentId, cancelled)
        }
        validateSectionPayload(section, contentId, null)
        if (digestSection(section) != plan.inputDigest) fail(PublicationFailureCode.INVALID_ORIGINAL,
            "The MOBI section changed after it was admitted.")
        val allowed = section.resourceIds.toHashSet()
        for (resourceId in allowed) {
            if (resourcesById[resourceId] == null) fail(PublicationFailureCode.MISSING_RESOURCE)
        }
        val normalized = normalizer.normalizeHtml(
            contentId = section.contentId,
            html = section.html,
            stylesheets = section.stylesheets,
            resolveLink = { raw ->
                checkCancelled(cancelled)
                section.links[raw] ?: safeExternalLink(raw)
            },
            resolveImage = { raw ->
                checkCancelled(cancelled)
                val localId = localResourceId(raw)
                if (localId != null) {
                    if (localId !in allowed) fail(PublicationFailureCode.MISSING_RESOURCE)
                    resourcesById[localId]?.record?.reference ?: fail(PublicationFailureCode.MISSING_RESOURCE)
                } else safeExternalImage(raw)
            },
            cancelled = cancelled,
        )
        if (normalized.anchors != plan.anchors) fail(PublicationFailureCode.INVALID_ORIGINAL,
            "MOBI anchors changed after admission.")
        val blocks = readableBlocks(normalized.blocks, contentId, null)
        return ReaderDocument(identity, plan.title, blocks, normalized.anchors, kind = org.tsuyomi.shared.sourcecontract.ReaderDocumentKind.CHAPTER)
    }

    private fun openResource(
        resourcesByReference: Map<ReaderResourceRef.Local, ResourcePlan>,
        reference: ReaderResourceRef.Local,
        cancelled: () -> Unit,
    ): InputStream {
        checkCancelled(cancelled)
        if (reference.publicationId != publicationId) fail(PublicationFailureCode.MISSING_RESOURCE)
        val plan = resourcesByReference[reference] ?: fail(PublicationFailureCode.MISSING_RESOURCE)
        val metadata = transportCall(PublicationFailureCode.INVALID_RESOURCE) {
            content.resourceMetadata(plan.metadata.resourceId)
        }
        if (metadata != plan.metadata) fail(PublicationFailureCode.INVALID_RESOURCE,
            "MOBI resource metadata changed after admission.")
        val input = transportCall(PublicationFailureCode.INVALID_RESOURCE) {
            content.resource(plan.metadata.resourceId, cancelled)
        }
        return MobiVerifiedResourceStream(input, plan.record, cancelled)
    }

    private fun ensureResource(
        resourcesById: LinkedHashMap<String, ResourcePlan>,
        resourceId: String,
        role: PublicationResourceRole,
        totalResourceBytes: Long,
        cancelled: () -> Unit,
    ): Long {
        resourcesById[resourceId]?.let { existing ->
            if (role == PublicationResourceRole.COVER && existing.record.role != PublicationResourceRole.COVER) {
                resourcesById[resourceId] = existing.copy(
                    record = existing.record.copy(role = PublicationResourceRole.COVER),
                )
            }
            return totalResourceBytes
        }
        if (resourcesById.size >= MAX_RESOURCE_ENTRIES) fail(PublicationFailureCode.ARCHIVE_LIMIT,
            "The MOBI publication exceeded the resource-count budget.")
        val metadata = transportCall(PublicationFailureCode.INVALID_RESOURCE) {
            content.resourceMetadata(resourceId)
        }
        validateResourceMetadata(resourceId, metadata)
        if (metadata.sizeBytes > MAX_TOTAL_RESOURCE_BYTES - totalResourceBytes) fail(PublicationFailureCode.ARCHIVE_LIMIT,
            "The MOBI publication exceeded the aggregate resource budget.")
        val verified = readAndVerifyResource(metadata, cancelled)
        val readerReference = ReaderResourceRef.Local(
            publicationId = publicationId,
            resourceId = stableReaderResourceId(publicationId, resourceId),
            mimeType = metadata.mimeType,
            sha256 = verified,
        )
        resourcesById[resourceId] = ResourcePlan(
            metadata = metadata,
            record = PublicationResourceRecord(readerReference, metadata.sizeBytes, role),
        )
        return totalResourceBytes + metadata.sizeBytes
    }

    private fun readAndVerifyResource(metadata: MobiPublicationResource, cancelled: () -> Unit): String {
        val bytes = ByteArray(metadata.sizeBytes.toInt())
        val digest = MessageDigest.getInstance("SHA-256")
        var offset = 0
        try {
            transportCall(PublicationFailureCode.INVALID_RESOURCE) {
                content.resource(metadata.resourceId, cancelled).use { input ->
                    while (offset < bytes.size) {
                        checkCancelled(cancelled)
                        val count = input.read(bytes, offset, bytes.size - offset)
                        when {
                            count < 0 -> fail(PublicationFailureCode.INVALID_RESOURCE,
                                "A MOBI resource ended before its declared size.")
                            count == 0 -> {
                                val value = input.read()
                                if (value < 0) fail(PublicationFailureCode.INVALID_RESOURCE,
                                    "A MOBI resource ended before its declared size.")
                                bytes[offset++] = value.toByte()
                                digest.update(value.toByte())
                            }
                            else -> {
                                digest.update(bytes, offset, count)
                                offset += count
                            }
                        }
                    }
                    checkCancelled(cancelled)
                    if (input.read() != -1) fail(PublicationFailureCode.INVALID_RESOURCE,
                        "A MOBI resource exceeded its declared size.")
                }
            }
        } catch (error: PublicationParseException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw PublicationParseException(PublicationFailureCode.INVALID_RESOURCE,
                "A MOBI resource could not be verified.", error)
        }
        if (!hasValidImageSignature(metadata.mimeType, bytes)) fail(PublicationFailureCode.INVALID_RESOURCE,
            "A MOBI image does not match its declared image type.")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (error: RuntimeException) {
            throw PublicationParseException(PublicationFailureCode.INVALID_RESOURCE,
                "A MOBI image could not be decoded safely.", error)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0 ||
            options.outWidth.toLong() * options.outHeight > MAX_IMAGE_PIXELS) {
            fail(PublicationFailureCode.INVALID_RESOURCE, "A MOBI image exceeds the supported pixel budget.")
        }
        return digest.digest().toHex()
    }

    private fun validateOriginal() {
        try {
            BookIdentity.Local(publicationId)
        } catch (error: IllegalArgumentException) {
            throw PublicationParseException(PublicationFailureCode.INVALID_ORIGINAL,
                "The MOBI publication identity is invalid.", error)
        }
        if (!SHA256_PATTERN.matches(originalSha256)) fail(PublicationFailureCode.INVALID_ORIGINAL,
            "The private original digest is invalid.")
        if (originalSizeBytes !in 1..MAX_ORIGINAL_BYTES) fail(PublicationFailureCode.ORIGINAL_LIMIT)
    }

    private fun validateCatalog(catalog: MobiPublicationCatalog) {
        if (catalog.format != LocalPublicationFormat.MOBI && catalog.format != LocalPublicationFormat.AZW3) {
            fail(PublicationFailureCode.UNSUPPORTED_FORMAT,
                "The isolated catalog did not identify a supported MOBI/KF8 format.")
        }
        if (catalog.sections.isEmpty()) fail(PublicationFailureCode.MALFORMED_PACKAGE,
            "The MOBI catalog has no reading sections.")
        if (catalog.sections.size > MAX_SECTIONS) fail(PublicationFailureCode.SECTION_LIMIT)
        if (catalog.sections.none(PublicationSection::linear)) fail(PublicationFailureCode.MALFORMED_PACKAGE,
            "The MOBI catalog has no linear reading section.")
        validateVisibleText(catalog.title, MAX_METADATA_CODE_POINTS, PublicationFailureCode.MALFORMED_PACKAGE)
        if (collapse(catalog.title).isEmpty()) fail(PublicationFailureCode.MALFORMED_PACKAGE)
        if (catalog.authors.size > MAX_AUTHORS) fail(PublicationFailureCode.MALFORMED_PACKAGE)
        catalog.authors.forEach { author ->
            validateVisibleText(author, MAX_METADATA_CODE_POINTS, PublicationFailureCode.MALFORMED_PACKAGE)
            if (collapse(author).isEmpty()) fail(PublicationFailureCode.MALFORMED_PACKAGE)
        }
        catalog.language?.let { language ->
            validateVisibleText(language, MAX_LANGUAGE_CODE_POINTS, PublicationFailureCode.MALFORMED_PACKAGE)
            if (collapse(language).isEmpty()) fail(PublicationFailureCode.MALFORMED_PACKAGE)
        }
        catalog.identifier?.let { identifier ->
            validateVisibleText(identifier, MAX_IDENTIFIER_CODE_POINTS, PublicationFailureCode.MALFORMED_PACKAGE)
            if (collapse(identifier).isEmpty()) fail(PublicationFailureCode.MALFORMED_PACKAGE)
        }
        catalog.coverResourceId?.let(::validateResourceId)

        val sectionIds = HashSet<String>(catalog.sections.size)
        var catalogBytes = 256L
        catalogBytes = boundedAdd(catalogBytes, jsonStringBytes(catalog.title), MAX_CATALOG_BYTES)
        catalog.authors.forEach { catalogBytes = boundedAdd(catalogBytes, jsonStringBytes(it) + 2, MAX_CATALOG_BYTES) }
        catalog.language?.let { catalogBytes = boundedAdd(catalogBytes, jsonStringBytes(it), MAX_CATALOG_BYTES) }
        catalog.identifier?.let { catalogBytes = boundedAdd(catalogBytes, jsonStringBytes(it), MAX_CATALOG_BYTES) }
        for ((index, section) in catalog.sections.withIndex()) {
            if (section.ordinal != index || !sectionIds.add(section.contentId)) {
                fail(PublicationFailureCode.MALFORMED_PACKAGE,
                    "The MOBI section order or identifiers are invalid.")
            }
            validateContentId(section.contentId)
            validateVisibleText(section.title, MAX_METADATA_CODE_POINTS, PublicationFailureCode.MALFORMED_PACKAGE)
            if (collapse(section.title).isEmpty()) fail(PublicationFailureCode.MALFORMED_PACKAGE)
            if (section.anchors.isNotEmpty()) fail(PublicationFailureCode.INVALID_ORIGINAL,
                "The MOBI transport cannot supply trusted Reader anchors.")
            catalogBytes = boundedAdd(catalogBytes, 64 + jsonStringBytes(section.contentId) + jsonStringBytes(section.title), MAX_CATALOG_BYTES)
        }
        if (catalog.authoredSectionIds.any { it !in sectionIds }) fail(PublicationFailureCode.MALFORMED_PACKAGE,
            "The MOBI authored titles reference an unknown section.")
        if (catalog.tableOfContents.isEmpty()) fail(PublicationFailureCode.MISSING_TOC,
            "The MOBI publication has no navigable table of contents.")
        var tocCount = 0
        fun visit(entries: List<PublicationTocEntry>, depth: Int) {
            if (depth > MAX_TOC_DEPTH) fail(PublicationFailureCode.MALFORMED_TOC,
                "The MOBI table of contents exceeds its nesting budget.")
            for (entry in entries) {
                if (++tocCount > MAX_TOC_ENTRIES) fail(PublicationFailureCode.BLOCK_LIMIT,
                    "The MOBI table of contents exceeds its entry budget.")
                validateVisibleText(entry.title, MAX_METADATA_CODE_POINTS, PublicationFailureCode.MALFORMED_TOC)
                if (collapse(entry.title).isEmpty()) fail(PublicationFailureCode.MALFORMED_TOC)
                validateContentId(entry.contentId)
                if (entry.contentId !in sectionIds) fail(PublicationFailureCode.MALFORMED_TOC,
                    "A MOBI table-of-contents entry targets an unknown section.")
                entry.fragment?.let { fragment ->
                    validateAnchorId(fragment, PublicationFailureCode.MALFORMED_TOC)
                }
                catalogBytes = boundedAdd(catalogBytes,
                    64 + jsonStringBytes(entry.title) + jsonStringBytes(entry.contentId) +
                        (entry.fragment?.let(::jsonStringBytes) ?: 4L), MAX_CATALOG_BYTES)
                visit(entry.children, depth + 1)
            }
        }
        visit(catalog.tableOfContents, 1)
        catalog.coverResourceId?.let { catalogBytes = boundedAdd(catalogBytes, jsonStringBytes(it), MAX_CATALOG_BYTES) }
        if (catalogBytes > MAX_CATALOG_BYTES) fail(PublicationFailureCode.BLOCK_LIMIT,
            "The MOBI catalog exceeded its transport output budget.")
    }

    private fun validateSectionPayload(
        section: MobiPublicationSection,
        expectedContentId: String,
        stylesheetCatalog: StylesheetCatalog?,
    ) {
        if (section.contentId != expectedContentId) fail(PublicationFailureCode.MISSING_SPINE_ITEM,
            "The isolated service returned a different section identifier.")
        validateContentId(section.contentId)
        validateUnicode(section.html, PublicationFailureCode.INVALID_TEXT_CONTENT)
        if (utf8Length(section.html) > MAX_SECTION_JSON_BYTES) fail(PublicationFailureCode.TEXT_LIMIT,
            "A MOBI section exceeded the HTML byte budget.")
        if (section.stylesheets.size > MAX_STYLESHEETS_PER_SECTION) fail(PublicationFailureCode.ARCHIVE_LIMIT)
        section.stylesheets.forEach { stylesheet ->
            if (stylesheet.sourceName.isEmpty() || stylesheet.sourceName.contains('/') ||
                '\\' in stylesheet.sourceName || ':' in stylesheet.sourceName) {
                fail(PublicationFailureCode.INVALID_ORIGINAL, "A MOBI stylesheet label is invalid.")
            }
            validateVisibleText(stylesheet.sourceName, MAX_STYLESHEET_NAME_CODE_POINTS,
                PublicationFailureCode.INVALID_ORIGINAL)
            validateUnicode(stylesheet.text, PublicationFailureCode.INVALID_RESOURCE)
            val byteLength = utf8Length(stylesheet.text)
            if (byteLength > MAX_STYLESHEET_BYTES) fail(PublicationFailureCode.ARCHIVE_LIMIT,
                "A MOBI stylesheet exceeded its byte budget.")
            stylesheetCatalog?.admit(stylesheet, byteLength)
        }
        if (section.links.size > MAX_LINKS_PER_SECTION) fail(PublicationFailureCode.BLOCK_LIMIT)
        for ((href, target) in section.links) {
            validateVisibleText(href, MAX_HREF_CODE_POINTS, PublicationFailureCode.INVALID_HREF)
            if (href.isEmpty() || href.any(Char::isISOControl)) fail(PublicationFailureCode.INVALID_HREF)
            validateInternalHref(href)
            validateContentId(target.contentId)
            target.fragment?.let { validateAnchorId(it, PublicationFailureCode.INVALID_HREF) }
        }
        if (section.resourceIds.size > MAX_RESOURCE_ENTRIES) fail(PublicationFailureCode.ARCHIVE_LIMIT)
    }

    private fun sectionTransportBytes(section: MobiPublicationSection): Long {
        var bytes = 2L
        var firstMember = true
        fun member(name: String, valueBytes: Long) {
            if (!firstMember) bytes = boundedAdd(bytes, 1, MAX_SECTION_JSON_BYTES)
            firstMember = false
            bytes = boundedAdd(bytes, jsonStringBytes(name) + 1 + valueBytes, MAX_SECTION_JSON_BYTES)
        }

        member("contentId", jsonStringBytes(section.contentId))
        member("html", jsonStringBytes(section.html))

        var stylesheetsBytes = 2L
        for ((index, stylesheet) in section.stylesheets.withIndex()) {
            if (index > 0) stylesheetsBytes = boundedAdd(stylesheetsBytes, 1, MAX_SECTION_JSON_BYTES)
            stylesheetsBytes = boundedAdd(stylesheetsBytes,
                2 + jsonStringBytes("sourceName") + 1 + jsonStringBytes(stylesheet.sourceName) +
                    1 + jsonStringBytes("text") + 1 + jsonStringBytes(stylesheet.text), MAX_SECTION_JSON_BYTES)
        }
        member("stylesheets", stylesheetsBytes)

        var linksBytes = 2L
        for ((index, entry) in section.links.entries.withIndex()) {
            if (index > 0) linksBytes = boundedAdd(linksBytes, 1, MAX_SECTION_JSON_BYTES)
            val target = entry.value
            val targetBytes = 2 + jsonStringBytes("contentId") + 1 + jsonStringBytes(target.contentId) +
                1 + jsonStringBytes("fragment") + 1 + (target.fragment?.let(::jsonStringBytes) ?: 4L)
            linksBytes = boundedAdd(linksBytes,
                jsonStringBytes(entry.key) + 1 + targetBytes + 16, MAX_SECTION_JSON_BYTES)
        }
        member("links", linksBytes)

        var resourceIdsBytes = 2L
        for ((index, resourceId) in section.resourceIds.withIndex()) {
            if (index > 0) resourceIdsBytes = boundedAdd(resourceIdsBytes, 1, MAX_SECTION_JSON_BYTES)
            resourceIdsBytes = boundedAdd(resourceIdsBytes, jsonStringBytes(resourceId), MAX_SECTION_JSON_BYTES)
        }
        member("resourceIds", resourceIdsBytes)
        return boundedAdd(bytes, 1, MAX_SECTION_JSON_BYTES)
    }

    private fun validateResourceMetadata(expectedId: String, metadata: MobiPublicationResource) {
        if (metadata.resourceId != expectedId) fail(PublicationFailureCode.MISSING_RESOURCE,
            "The isolated service returned metadata for a different resource.")
        validateResourceId(metadata.resourceId)
        if (metadata.mimeType !in SUPPORTED_IMAGE_TYPES) fail(PublicationFailureCode.UNSUPPORTED_RESOURCE_TYPE)
        if (metadata.sizeBytes !in 1..MAX_RESOURCE_BYTES) fail(PublicationFailureCode.ARCHIVE_LIMIT,
            "A MOBI resource exceeded its byte budget.")
    }


    private fun validateToc(
        entries: List<PublicationTocEntry>,
        plans: Map<String, SectionPlan>,
    ): List<PublicationTocEntry> {
        fun copyAndValidate(entry: PublicationTocEntry): PublicationTocEntry {
            val plan = plans[entry.contentId] ?: fail(PublicationFailureCode.MALFORMED_TOC)
            entry.fragment?.let { fragment ->
                if (plan.anchors[fragment] == null) fail(PublicationFailureCode.MISSING_ANCHOR,
                    "A MOBI table-of-contents fragment has no exact authored anchor.")
            }
            return PublicationTocEntry(
                title = collapse(entry.title),
                contentId = entry.contentId,
                fragment = entry.fragment,
                children = entry.children.map(::copyAndValidate),
            )
        }
        return entries.map(::copyAndValidate)
    }

    private fun validateTarget(target: ReaderLinkTarget.Internal, plans: Map<String, SectionPlan>) {
        val plan = plans[target.contentId] ?: fail(PublicationFailureCode.INVALID_HREF,
            "An internal MOBI link targets an unknown section.")
        target.fragment?.let { fragment ->
            if (plan.anchors[fragment] == null) fail(PublicationFailureCode.MISSING_ANCHOR,
                "An internal MOBI link fragment has no exact authored anchor.")
        }
    }

    private fun readableBlocks(
        normalized: List<ReaderBlock>,
        contentId: String,
        findings: MutableMap<String, PublicationCompatibilityFinding>?,
    ): List<ReaderBlock> {
        if (normalized.isNotEmpty()) return normalized
        val finding = PublicationCompatibilityFinding(
            code = "EMPTY_MOBI_SECTION",
            severity = PublicationCompatibilitySeverity.DEGRADED,
            message = "This MOBI section contains no supported readable content.",
            contentId = contentId,
        )
        findings?.putIfAbsent(findingKey(finding), finding)
        return listOf(ReaderBlock.Unsupported(
            blockId = "empty-section",
            featureCode = "empty-section",
            visibleText = finding.message,
        ))
    }

    private fun validateInternalHref(raw: String) {
        if (MOBI_POSITION_HREF.matches(raw)) return
        val slash = raw.indexOf('/')
        val query = raw.indexOf('?')
        val fragment = raw.indexOf('#')
        val firstSeparator = minOf(
            if (slash < 0) raw.length else slash,
            if (query < 0) raw.length else query,
            if (fragment < 0) raw.length else fragment,
        )
        val colon = raw.indexOf(':')
        if (raw.startsWith("//") || '\\' in raw || (colon >= 0 && colon < firstSeparator)) {
            fail(PublicationFailureCode.INVALID_HREF, "An internal MOBI link is not a format position or relative reference.")
        }
    }
    private fun safeExternalLink(raw: String): ReaderLinkTarget? = safeHttps(raw)?.let(ReaderLinkTarget::ExternalHttps)

    private fun safeExternalImage(raw: String): ReaderResourceRef? = safeHttps(raw)?.let(ReaderResourceRef::RemoteHttps)

    private fun safeHttps(raw: String): String? {
        if (codePointCount(raw) !in 1..MAX_HREF_CODE_POINTS) return null
        val parsed = runCatching { java.net.URI(raw) }.getOrNull() ?: return null
        if (!parsed.scheme.equals("https", ignoreCase = true) || parsed.host.isNullOrBlank() ||
            parsed.rawUserInfo != null || parsed.port !in -1..65535) return null
        return runCatching {
            java.net.URI("https", null, parsed.host, parsed.port, parsed.path, parsed.query, null).toASCIIString()
        }.getOrNull()
    }

    private fun localResourceId(raw: String): String? {
        if (!raw.startsWith(RESOURCE_URI_PREFIX)) {
            if (raw.startsWith(RESOURCE_SCHEME_PREFIX, ignoreCase = true)) fail(PublicationFailureCode.INVALID_HREF,
                "A MOBI resource URI is malformed.")
            return null
        }
        val id = raw.substring(RESOURCE_URI_PREFIX.length)
        validateResourceId(id)
        return id
    }

    private fun digestSection(section: MobiPublicationSection): String {
        val digest = BoundedUtf8Digest()
        digest.value("mobi-section-v1")
        digest.value(section.contentId)
        digest.value(section.html)
        digest.number(section.stylesheets.size.toLong())
        section.stylesheets.forEach { stylesheet ->
            digest.value(stylesheet.sourceName)
            digest.value(stylesheet.text)
        }
        val links = section.links.entries.sortedBy { it.key }
        digest.number(links.size.toLong())
        links.forEach { (href, target) ->
            digest.value(href)
            digest.value(target.contentId)
            digest.optional(target.fragment)
        }
        val resourceIds = section.resourceIds.sorted()
        digest.number(resourceIds.size.toLong())
        resourceIds.forEach(digest::value)
        return digest.finish()
    }


    private fun compatibilityReport(findings: Collection<PublicationCompatibilityFinding>) =
        PublicationCompatibilityReport(
            level = if (findings.any { it.severity != PublicationCompatibilitySeverity.NOTICE }) {
                PublicationCompatibilityLevel.DEGRADED
            } else PublicationCompatibilityLevel.SUPPORTED,
            findings = findings.toList(),
        )

    private fun validateContentId(value: String) {
        validateUnicode(value, PublicationFailureCode.INVALID_ORIGINAL)
        if (value.isEmpty() || codePointCount(value) > MAX_CONTENT_ID_CODE_POINTS ||
            value.any { it.isISOControl() || it == '/' || it == '\\' || it == '#' || it == '?' }) {
            fail(PublicationFailureCode.INVALID_ORIGINAL, "A MOBI content identifier is invalid.")
        }
    }

    private fun validateResourceId(value: String) {
        if (!RESOURCE_ID_PATTERN.matches(value)) fail(PublicationFailureCode.INVALID_RESOURCE,
            "A MOBI resource identifier is invalid.")
    }

    private fun validateAnchorId(value: String, code: PublicationFailureCode) {
        validateUnicode(value, code)
        if (value.isEmpty() || codePointCount(value) > MAX_ANCHOR_ID_CODE_POINTS || value.any(Char::isISOControl)) {
            fail(code, "A MOBI anchor identifier is invalid.")
        }
    }

    private fun validateVisibleText(value: String, maxCodePoints: Int, code: PublicationFailureCode) {
        validateUnicode(value, code)
        if (codePointCount(value) > maxCodePoints || value.any { it.isISOControl() && it !in '\t'..'\r' }) {
            fail(code, "A MOBI text field exceeded its supported bounds.")
        }
    }

    private fun validateUnicode(value: String, code: PublicationFailureCode) {
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current.isHighSurrogate()) {
                if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) fail(code,
                    "A MOBI text field contains malformed Unicode.")
                index += 2
            } else {
                if (current.isLowSurrogate()) fail(code, "A MOBI text field contains malformed Unicode.")
                index++
            }
        }
    }

    private fun <T> transportCall(code: PublicationFailureCode, block: () -> T): T = try {
        block()
    } catch (error: PublicationParseException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        throw error
    } catch (error: Exception) {
        throw PublicationParseException(code, "The isolated MOBI content service failed.", error)
    }

    private fun checkCancelled(cancelled: () -> Unit) = cancelled()

    private fun codePointCount(value: String): Int = value.codePointCount(0, value.length)

    private fun boundedAdd(left: Long, right: Long, maximum: Long): Long {
        if (right > maximum - left) fail(PublicationFailureCode.BLOCK_LIMIT)
        return left + right
    }

    private fun jsonStringBytes(value: String): Long {
        var bytes = 2L
        var index = 0
        while (index < value.length) {
            val current = value[index]
            bytes += when {
                current == '"' || current == '\\' -> 2
                current.code < 0x20 -> 6
                current.isHighSurrogate() -> {
                    if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) {
                        fail(PublicationFailureCode.INVALID_TEXT_CONTENT, "A MOBI field contains malformed Unicode.")
                    }
                    index++
                    4
                }
                current.isLowSurrogate() -> fail(PublicationFailureCode.INVALID_TEXT_CONTENT,
                    "A MOBI field contains malformed Unicode.")
                current.code <= 0x7f -> 1
                current.code <= 0x7ff -> 2
                else -> 3
            }
            index++
        }
        return bytes
    }

    private fun readerTextBytes(block: ReaderBlock): Long = when (block) {
        is ReaderBlock.Paragraph -> utf8Length(block.text)
        is ReaderBlock.Heading -> utf8Length(block.text)
        is ReaderBlock.Image -> block.altText?.let(::utf8Length) ?: 0L
        is ReaderBlock.Divider -> 0L
        is ReaderBlock.Quote -> utf8Length(block.text) + (block.attribution?.let { 1L + utf8Length(it) } ?: 0L)
        is ReaderBlock.Listing -> block.items.sumOf { item -> item.blocks.sumOf(::readerTextBytes) }
        is ReaderBlock.Note -> block.blocks.sumOf(::readerTextBytes)
        is ReaderBlock.Table -> block.rows.sumOf { row -> row.cells.sumOf { utf8Length(it.text) } }
        is ReaderBlock.Post -> block.blocks.sumOf(::readerTextBytes)
        is ReaderBlock.Unsupported -> utf8Length(block.visibleText)
    }

    private fun collapse(value: String): String = value.trim().replace(WHITESPACE, " ")
    private fun findingKey(finding: PublicationCompatibilityFinding): String =
        "${finding.code}\u0000${finding.contentId}\u0000${finding.blockId}"


    private fun fail(code: PublicationFailureCode, message: String = code.name): Nothing =
        throw PublicationParseException(code, message)

    internal data class SectionPlan(
        val contentId: String,
        val title: String,
        val ordinal: Int,
        val linear: Boolean,
        val codePointStart: Long?,
        val inputDigest: String,
        val anchors: Map<String, ReaderAnchorTarget>,
    )

    internal data class ResourcePlan(
        val metadata: MobiPublicationResource,
        val record: PublicationResourceRecord,
    )

    /** Charge retained sources once, without changing per-section cascade occurrence order. */
    private class StylesheetCatalog {
        private val sources = LinkedHashMap<String, String>()
        private var retainedBytes = 0L

        fun admit(stylesheet: PublicationStylesheet, byteLength: Long) {
            val retained = sources[stylesheet.sourceName]
            if (retained != null) {
                if (retained != stylesheet.text) throw PublicationParseException(
                    PublicationFailureCode.INVALID_ORIGINAL,
                    "An admitted MOBI stylesheet source changed its content.",
                )
                return
            }
            if (sources.size >= MAX_RESOURCE_ENTRIES || byteLength > MAX_TOTAL_STYLESHEET_BYTES - retainedBytes) {
                throw PublicationParseException(PublicationFailureCode.ARCHIVE_LIMIT,
                    "The MOBI publication exceeded the distinct stylesheet resource budget.")
            }
            sources[stylesheet.sourceName] = stylesheet.text
            retainedBytes += byteLength
        }
    }

    companion object {
        private const val MAX_ORIGINAL_BYTES = 32L * 1024 * 1024
        private const val MAX_SECTIONS = 10_000
        private const val MAX_CATALOG_BYTES = 4L * 1024 * 1024
        private const val MAX_SECTION_JSON_BYTES = 4L * 1024 * 1024
        private const val MAX_SECTION_TEXT_BYTES = 2L * 1024 * 1024
        private const val MAX_TOTAL_TEXT_BYTES = 64L * 1024 * 1024
        private const val MAX_RESOURCE_ENTRIES = 512
        private const val MAX_RESOURCE_BYTES = 8L * 1024 * 1024
        private const val MAX_TOTAL_RESOURCE_BYTES = 32L * 1024 * 1024
        private const val MAX_IMAGE_PIXELS = 50_000_000L
        private const val MAX_STYLESHEET_BYTES = 256L * 1024
        private const val MAX_TOTAL_STYLESHEET_BYTES = 1024L * 1024
        private const val MAX_STYLESHEETS_PER_SECTION = 64
        private const val MAX_AUTHORS = 64
        private const val MAX_METADATA_CODE_POINTS = 512
        private const val MAX_LANGUAGE_CODE_POINTS = 128
        private const val MAX_IDENTIFIER_CODE_POINTS = 1024
        private const val MAX_STYLESHEET_NAME_CODE_POINTS = 128
        private const val MAX_CONTENT_ID_CODE_POINTS = 256
        private const val MAX_ANCHOR_ID_CODE_POINTS = 256
        private const val MAX_HREF_CODE_POINTS = 4096
        private const val MAX_LINKS_PER_SECTION = 50_000
        private const val MAX_TOC_ENTRIES = 20_000
        private const val MAX_TOC_DEPTH = 64
        private const val MAX_READER_ANCHORS = 50_000
        private const val MAX_TOTAL_ANCHORS = 100_000
        private const val MAX_TOTAL_INTERNAL_LINKS = 100_000
        private const val RESOURCE_URI_PREFIX = "tsuyomi-resource://"
        private const val RESOURCE_SCHEME_PREFIX = "tsuyomi-resource:"
        private val SHA256_PATTERN = Regex("^[a-f0-9]{64}$")
        private val RESOURCE_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,128}$")
        private val MOBI_POSITION_HREF = Regex("(?:filepos:[0-9]+|kindle:pos:fid:[0-9A-Va-v]+:off:[0-9A-Va-v]+)")
        private val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
        private val WHITESPACE = Regex("[\\s　]+")
    }
}

private class MobiVerifiedResourceStream(
    input: InputStream,
    private val expected: PublicationResourceRecord,
    private val cancelled: () -> Unit,
) : FilterInputStream(input) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var bytesRead = 0L
    private var verified = false

    override fun read(): Int {
        cancelled()
        val value = super.read()
        if (value >= 0) {
            if (++bytesRead > expected.byteLength) failResource()
            digest.update(value.toByte())
        } else verifyEnd()
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        cancelled()
        val count = super.read(buffer, offset, length)
        if (count > 0) {
            bytesRead += count
            if (bytesRead > expected.byteLength) failResource()
            digest.update(buffer, offset, count)
        } else if (count < 0) verifyEnd()
        return count
    }

    private fun verifyEnd() {
        if (verified) return
        if (bytesRead != expected.byteLength || digest.digest().toHex() != expected.reference.sha256) failResource()
        verified = true
    }

    private fun failResource(): Nothing = throw IOException("MOBI resource failed its size or SHA-256 check")
}

private class BoundedUtf8Digest {
    private val digest = MessageDigest.getInstance("SHA-256")
    private val encoder = StandardCharsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    private val bytes = ByteBuffer.allocate(8192)

    fun value(value: String) {
        val length = utf8Length(value)
        number(length)
        encoder.reset()
        val chars = CharBuffer.wrap(value)
        var endOfInput = false
        while (!endOfInput) {
            val result = encoder.encode(chars, bytes, true)
            bytes.flip()
            digest.update(bytes)
            bytes.clear()
            if (result.isError) result.throwException()
            endOfInput = result.isUnderflow
        }
        while (true) {
            val result = encoder.flush(bytes)
            bytes.flip()
            digest.update(bytes)
            bytes.clear()
            if (result.isError) result.throwException()
            if (result.isUnderflow) break
        }
    }

    fun optional(value: String?) {
        digest.update(if (value == null) 0.toByte() else 1.toByte())
        if (value != null) value(value)
    }

    fun number(value: Long) {
        for (shift in 56 downTo 0 step 8) digest.update((value ushr shift).toByte())
    }

    fun finish(): String = digest.digest().toHex()
}


private fun stableReaderResourceId(publicationId: String, resourceId: String): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest("$publicationId\u0000mobi-resource\u0000$resourceId".toByteArray(StandardCharsets.UTF_8))
        .copyOfRange(0, 16)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val buffer = ByteBuffer.wrap(bytes)
    return UUID(buffer.long, buffer.long).toString()
}

private fun hasValidImageSignature(mimeType: String, bytes: ByteArray): Boolean = when (mimeType) {
    "image/png" -> bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4e.toByte() && bytes[3] == 0x47.toByte() && bytes[4] == 0x0d.toByte() &&
        bytes[5] == 0x0a.toByte() && bytes[6] == 0x1a.toByte() && bytes[7] == 0x0a.toByte()
    "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
    "image/gif" -> bytes.size >= 6 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte() &&
        (bytes[4] == 0x37.toByte() || bytes[4] == 0x39.toByte()) && bytes[5] == 0x61.toByte()
    "image/webp" -> bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() && bytes[8] == 0x57.toByte() &&
        bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
    else -> false
}

private fun utf8Length(value: String): Long {
    var bytes = 0L
    var index = 0
    while (index < value.length) {
        val current = value[index]
        bytes += when {
            current.code <= 0x7f -> 1
            current.code <= 0x7ff -> 2
            current.isHighSurrogate() -> {
                if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) {
                    throw PublicationParseException(PublicationFailureCode.INVALID_TEXT_CONTENT,
                        "A MOBI text field contains malformed Unicode.")
                }
                index++
                4
            }
            current.isLowSurrogate() -> throw PublicationParseException(PublicationFailureCode.INVALID_TEXT_CONTENT,
                "A MOBI text field contains malformed Unicode.")
            else -> 3
        }
        index++
    }
    return bytes
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8)).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
