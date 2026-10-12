/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.publication

import java.io.Closeable
import java.io.InputStream
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.sourcecontract.ReaderLinkTarget

/** Synchronous, path-free bridge to the isolated MOBI/KF8 content service. */
interface MobiPublicationContent : Closeable {
    val catalog: MobiPublicationCatalog

    /** Reopens one bounded section; callers must not retain its source HTML after normalization. */
    fun section(contentId: String, cancelled: () -> Unit = {}): MobiPublicationSection

    /** Opens one private, bounded resource by its opaque service-owned identifier. */
    fun resource(resourceId: String, cancelled: () -> Unit = {}): InputStream

    /** Returns untrusted MIME/size metadata; the host verifies actual bytes and computes SHA-256. */
    fun resourceMetadata(resourceId: String): MobiPublicationResource
}

/** App-injected opener for the isolated runtime; the returned session remains owned until closed. */
fun interface MobiPublicationContentFactory {
    fun open(source: PublicationSource, cancelled: () -> Unit): MobiPublicationContent
}

/** Validated native-parser catalog without source paths, URIs, or native object handles. */
data class MobiPublicationCatalog(
    val format: LocalPublicationFormat,
    val title: String,
    val authors: List<String>,
    val language: String? = null,
    val identifier: String? = null,
    val sections: List<PublicationSection>,
    val tableOfContents: List<PublicationTocEntry>,
    val coverResourceId: String? = null,
    /** Directly supplied titles are authored by default; isolated output excludes generated captions. */
    val authoredSectionIds: Set<String> = sections.mapTo(HashSet()) { it.contentId },
)

/** One inert HTML fragment and its already-resolved host reference tables. */
data class MobiPublicationSection(
    val contentId: String,
    val html: String,
    val stylesheets: List<PublicationStylesheet>,
    val links: Map<String, ReaderLinkTarget.Internal>,
    val resourceIds: List<String>,
)

/** Untrusted resource declaration. Digest and image validity are computed by core from the stream. */
data class MobiPublicationResource(
    val resourceId: String,
    val mimeType: String,
    val sizeBytes: Long,
)
