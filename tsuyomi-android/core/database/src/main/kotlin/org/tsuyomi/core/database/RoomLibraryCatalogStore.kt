/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.database

import androidx.room.withTransaction
import java.text.Normalizer
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.tsuyomi.core.database.room.BookEntity
import org.tsuyomi.core.database.room.BookIdentityRow
import org.tsuyomi.core.database.room.LibraryDao
import org.tsuyomi.core.database.room.LibraryEntryEntity
import org.tsuyomi.core.database.room.LocalBookTagEntity
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.librarydomain.LibraryBook
import org.tsuyomi.shared.librarydomain.LibraryEntry
import org.tsuyomi.shared.librarydomain.ReadingProgress
import org.tsuyomi.shared.librarydomain.RemoteReconciliationState
import org.tsuyomi.shared.librarydomain.LocalPublicationFormat
import org.tsuyomi.shared.librarydomain.LocalPublicationMetadata
import org.tsuyomi.shared.librarydomain.LocalTextHeading
import org.tsuyomi.shared.librarydomain.LocalTextParseOptions

internal class RoomLibraryCatalogStore(
    private val database: TsuyomiDatabase,
    private val dao: LibraryDao,
) {
    suspend fun saveBook(book: LibraryBook) {
        val entity = book.toEntity()
        if (dao.insertBook(entity) == -1L) {
            check(
                dao.updateBookByIdentityKey(
                    identityKey = entity.identityKey,
                    title = entity.title,
                    authorsJson = entity.authorsJson,
                    authorSortKey = entity.authorSortKey,
                    coverUrl = entity.coverUrl,
                    canonicalUrl = entity.canonicalUrl,
                    status = entity.status,
                    remoteTagsJson = entity.remoteTagsJson,
                    metadataUpdatedAtEpochSecond = entity.metadataUpdatedAtEpochSecond,
                    metadataUpdatedAtNano = entity.metadataUpdatedAtNano,
                    localFormat = entity.localFormat,
                    localOriginalSha256 = entity.localOriginalSha256,
                    localOriginalSizeBytes = entity.localOriginalSizeBytes,
                    localOriginalAvailable = entity.localOriginalAvailable,
                    localTxtOptionsJson = entity.localTxtOptionsJson,
                    localCoverResourceId = entity.localCoverResourceId,
                ) == 1,
            ) { "Could not update book metadata" }
        }
    }

    suspend fun ensureRetainedLibraryEntry(book: LibraryBook): Boolean = database.withTransaction {
        saveBook(book)
        dao.insertLibraryEntry(
            LibraryEntryEntity(
                identityKey = book.identity.storageKey,
                sourceId = (book.identity as? BookIdentity.Remote)?.sourceId,
                remoteBookId = (book.identity as? BookIdentity.Remote)?.remoteBookId,
                addedAtEpochSecond = book.addedAt.epochSecond,
                addedAtNano = book.addedAt.nano,
                rating = null,
                locallyPinned = false,
            ),
        ) != -1L
    }

    suspend fun book(identity: BookIdentity): LibraryBook? =
        dao.bookByIdentityKey(identity.storageKey)?.toDomain()

    suspend fun localPublications(): List<LibraryBook> = dao.localPublicationBooks().map(BookEntity::toDomain)

    suspend fun setLocalOriginalAvailable(identity: BookIdentity.Local, available: Boolean): Boolean = database.withTransaction {
        val current = dao.bookByIdentityKey(identity.storageKey) ?: return@withTransaction false
        if (current.origin != "local") return@withTransaction false
        dao.setLocalOriginalAvailable(identity.storageKey, available) == 1
    }

    /** A cover repair changes no unrelated metadata or reading state and cannot insert a deleted book. */
    suspend fun compareAndSetLocalCoverResourceId(
        identity: BookIdentity.Local,
        format: LocalPublicationFormat,
        originalSha256: String,
        originalSizeBytes: Long,
        expectedResourceId: String?,
        resourceId: String,
    ): Boolean = database.withTransaction {
        BookIdentity.requireCanonicalUuid(resourceId, "resourceId")
        expectedResourceId?.let { BookIdentity.requireCanonicalUuid(it, "expectedResourceId") }
        val updated = dao.compareAndSetLocalCoverResourceId(identity.storageKey, format.name, originalSha256, originalSizeBytes, expectedResourceId, resourceId)
        currentCoroutineContext().ensureActive()
        updated == 1
    }

    suspend fun deleteLocalPublication(identity: BookIdentity.Local): Boolean = database.withTransaction {
        val current = dao.bookByIdentityKey(identity.storageKey) ?: return@withTransaction false
        if (current.origin != "local") return@withTransaction false
        dao.deleteReaderBookmarksByIdentityKey(identity.storageKey)
        dao.deleteLocalPublicationBook(identity.storageKey) == 1
    }

    suspend fun libraryEntries(): List<LibraryEntry> = dao.libraryBooks().mapNotNull { entryFor(it.toIdentity()) }

    suspend fun readLaterEntries(): List<LibraryEntry> = dao.readLaterBooks().mapNotNull { entryFor(it.toIdentity()) }

    suspend fun readerHistoryEntries(): List<LibraryEntry> = dao.readerHistoryRows().mapNotNull { row ->
        val identity = row.toIdentity()
        val fallbackProgress = if (row.explicitVisit) null else {
            dao.progressByIdentityKey(identity.storageKey)?.toDomainOrNull(identity) ?: return@mapNotNull null
        }
        entryFor(
            identity = identity,
            readerVisitedAt = Instant.ofEpochSecond(row.visitedAtEpochSecond, row.visitedAtNano.toLong()),
            knownProgress = fallbackProgress,
        )
    }

    suspend fun readerHistoryEntry(identity: BookIdentity): LibraryEntry? {
        val visit = dao.readerHistoryByIdentityKey(identity.storageKey)
        val fallbackProgress = if (visit == null) {
            dao.progressByIdentityKey(identity.storageKey)?.toDomainOrNull(identity) ?: return null
        } else null
        val visitedAt = visit?.let { Instant.ofEpochSecond(it.lastVisitedAtEpochSecond, it.lastVisitedAtNano.toLong()) }
            ?: requireNotNull(fallbackProgress).updatedAt
        return entryFor(identity, visitedAt, fallbackProgress)
    }

    suspend fun libraryEntry(identity: BookIdentity): LibraryEntry? = entryFor(identity)

    suspend fun entriesFor(identities: List<BookIdentityRow>): List<LibraryEntry> =
        identities.mapNotNull { entryFor(it.toIdentity()) }

    private suspend fun entryFor(
        identity: BookIdentity,
        readerVisitedAt: Instant? = null,
        knownProgress: ReadingProgress? = null,
    ): LibraryEntry? {
        val stored = dao.bookByIdentityKey(identity.storageKey) ?: return null
        val domainBook = stored.toDomain()
        val entry = dao.libraryEntryByIdentityKey(identity.storageKey)
        if (entry == null && readerVisitedAt == null) return null
        val remote = identity as? BookIdentity.Remote
        val availability = remote?.let { dao.sourceAvailability(it.sourceId)?.available == true } ?: false
        val tags = dao.localTagsByIdentityKey(identity.storageKey).mapTo(linkedSetOf()) { it.displayTag }
        val currentReconciliation = remote?.let {
            dao.activeReconciliation(it.sourceId, it.remoteBookId) ?: dao.latestReconciliation(it.sourceId, it.remoteBookId)
        }
        val reconciliation = currentReconciliation?.state?.let { runCatching { RemoteReconciliationState.valueOf(it) }.getOrNull() }
        return LibraryEntry(
            book = domainBook,
            libraryAddedAt = entry?.let { Instant.ofEpochSecond(it.addedAtEpochSecond, it.addedAtNano.toLong()) } ?: domainBook.addedAt,
            rating = entry?.rating,
            readLater = entry?.readLater ?: false,
            progress = knownProgress ?: dao.progressByIdentityKey(identity.storageKey)?.toDomainOrNull(identity),
            localTags = tags,
            sourceAvailable = availability,
            reconciliation = reconciliation,
            reconciliationOperation = currentReconciliation?.operation,
            readerVisitedAt = readerVisitedAt,
            localMembership = entry?.locallyPinned ?: false,
        )
    }

    suspend fun addToLibrary(book: LibraryBook): Boolean = database.withTransaction {
        saveBook(book)
        val identity = book.identity
        val inserted = dao.insertLibraryEntry(
            LibraryEntryEntity(
                identityKey = identity.storageKey,
                sourceId = (identity as? BookIdentity.Remote)?.sourceId,
                remoteBookId = (identity as? BookIdentity.Remote)?.remoteBookId,
                addedAtEpochSecond = book.addedAt.epochSecond,
                addedAtNano = book.addedAt.nano,
                rating = null,
            ),
        )
        inserted != -1L || dao.pinLibraryEntryByIdentityKey(identity.storageKey) != 0
    }

    suspend fun removeFromLibrary(identity: BookIdentity): Boolean = database.withTransaction {
        if (dao.unpinLibraryEntryByIdentityKey(identity.storageKey) == 0) return@withTransaction false
        dao.deleteManualMembershipsByIdentityKey(identity.storageKey)
        true
    }

    suspend fun removeFromLibrary(identities: Set<BookIdentity>): Int = database.withTransaction {
        identities.count { identity ->
            if (dao.unpinLibraryEntryByIdentityKey(identity.storageKey) == 0) false else {
                dao.deleteManualMembershipsByIdentityKey(identity.storageKey)
                true
            }
        }
    }

    suspend fun reorderLibrary(identities: List<BookIdentity>) = database.withTransaction {
        val current = dao.libraryBooks().map(BookEntity::toIdentity)
        require(identities.size == current.size && identities.toSet() == current.toSet()) {
            "Library reorder must contain every current entry exactly once"
        }
        identities.forEachIndexed { index, identity ->
            check(dao.updateLibraryDisplayOrderByIdentityKey(identity.storageKey, index) == 1)
        }
    }

    suspend fun setRating(identity: BookIdentity, rating: Int?) {
        require(rating == null || rating in 1..5)
        check(dao.updateRatingByIdentityKey(identity.storageKey, rating) == 1) { "Book is not in library" }
    }

    suspend fun setReadLater(identity: BookIdentity, readLater: Boolean) {
        check(dao.updateReadLaterByIdentityKey(identity.storageKey, readLater) == 1) { "Book is not in library" }
    }

    suspend fun setLocalTags(identity: BookIdentity, tags: Collection<String>) = database.withTransaction {
        check(dao.libraryEntryByIdentityKey(identity.storageKey) != null) { "Book is not in library" }
        val normalized = tags.mapNotNull { raw ->
            val display = raw.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Normalizer.normalize(display, Normalizer.Form.NFKC).lowercase(Locale.ROOT) to display
        }.distinctBy { it.first }
        require(normalized.size <= 64)
        dao.deleteLocalTagsByIdentityKey(identity.storageKey)
        val remote = identity as? BookIdentity.Remote
        dao.insertLocalTags(normalized.map { (key, display) ->
            LocalBookTagEntity(identity.storageKey, remote?.sourceId, remote?.remoteBookId, key, display)
        })
    }
}

private fun LibraryBook.toEntity(): BookEntity {
    val canonicalAuthors = canonicalStringSet(authors.ifEmpty { author?.let(::setOf).orEmpty() })
    val remote = identity as? BookIdentity.Remote
    val local = localPublication
    return BookEntity(
        identityKey = identity.storageKey,
        origin = if (remote != null) "remote" else "local",
        sourceId = remote?.sourceId,
        remoteBookId = remote?.remoteBookId,
        publicationId = (identity as? BookIdentity.Local)?.publicationId,
        title = title,
        authorsJson = encodeStringSet(canonicalAuthors),
        authorSortKey = authorSortKey(canonicalAuthors),
        coverUrl = coverUrl,
        canonicalUrl = canonicalUrl,
        status = status,
        remoteTagsJson = encodeStringSet(canonicalStringSet(remoteTags)),
        legacySourceUpdateKey = null,
        legacyHasUnreadUpdate = false,
        addedAtEpochSecond = addedAt.epochSecond,
        addedAtNano = addedAt.nano,
        metadataUpdatedAtEpochSecond = metadataUpdatedAt.epochSecond,
        metadataUpdatedAtNano = metadataUpdatedAt.nano,
        localFormat = local?.format?.name,
        localOriginalSha256 = local?.originalSha256,
        localOriginalSizeBytes = local?.originalSizeBytes,
        localOriginalAvailable = local?.originalAvailable,
        localTxtOptionsJson = local?.txtOptions?.toStorageJson(),
        localCoverResourceId = local?.coverResourceId,
    )
}

internal fun BookEntity.toIdentity(): BookIdentity = when (origin) {
    "remote" -> BookIdentity.Remote(requireNotNull(sourceId), requireNotNull(remoteBookId)).also { check(publicationId == null) }
    "local" -> BookIdentity.Local(requireNotNull(publicationId)).also { check(sourceId == null && remoteBookId == null) }
    else -> error("Invalid stored book origin")
}.also { check(it.storageKey == identityKey) }

internal fun BookIdentityRow.toIdentity(): BookIdentity = storedIdentity(identityKey, origin, sourceId, remoteBookId)

internal fun org.tsuyomi.core.database.room.ReaderHistoryRow.toIdentity(): BookIdentity =
    storedIdentity(identityKey, origin, sourceId, remoteBookId)

private fun storedIdentity(identityKey: String, origin: String, sourceId: String?, remoteBookId: String?): BookIdentity =
    when (origin) {
        "remote" -> BookIdentity.Remote(requireNotNull(sourceId), requireNotNull(remoteBookId))
        "local" -> BookIdentity.Local(identityKey.substringAfter(':')).also { check(sourceId == null && remoteBookId == null) }
        else -> error("Invalid stored book origin")
    }.also { check(it.storageKey == identityKey) }

internal fun BookEntity.toDomain(): LibraryBook {
    val authors = decodeStringSet(authorsJson)
    val identity = toIdentity()
    val localPublication = if (identity is BookIdentity.Local) {
        val format = LocalPublicationFormat.valueOf(requireNotNull(localFormat))
        check((format == LocalPublicationFormat.TXT) == (localTxtOptionsJson != null))
        LocalPublicationMetadata(
            format = format,
            originalSha256 = requireNotNull(localOriginalSha256),
            originalSizeBytes = requireNotNull(localOriginalSizeBytes),
            originalAvailable = requireNotNull(localOriginalAvailable),
            coverResourceId = localCoverResourceId,
            txtOptions = if (format == LocalPublicationFormat.TXT) {
                requireNotNull(localTxtOptionsJson).parseTextOptions()
            } else null,
        )
    } else {
        check(localFormat == null && localOriginalSha256 == null && localOriginalSizeBytes == null && localOriginalAvailable == null && localTxtOptionsJson == null && localCoverResourceId == null)
        null
    }
    return LibraryBook(
        identity = identity,
        title = title,
        author = authors.firstOrNull(),
        authors = authors,
        coverUrl = coverUrl,
        canonicalUrl = canonicalUrl,
        status = status,
        remoteTags = decodeStringSet(remoteTagsJson),
        localPublication = localPublication,
        addedAt = Instant.ofEpochSecond(addedAtEpochSecond, addedAtNano.toLong()),
        metadataUpdatedAt = Instant.ofEpochSecond(metadataUpdatedAtEpochSecond, metadataUpdatedAtNano.toLong()),
    )
}

private fun LocalTextParseOptions.toStorageJson(): String = JsonObject(
    mapOf(
        "charsetName" to (charsetName?.let(::JsonPrimitive) ?: JsonNull),
        "headingSelections" to JsonArray(headingSelections.map { heading ->
            JsonObject(
                mapOf(
                    "paragraphOrdinal" to JsonPrimitive(heading.paragraphOrdinal),
                    "title" to JsonPrimitive(heading.title),
                ),
            )
        }),
        "paragraphsPerSection" to JsonPrimitive(paragraphsPerSection),
    ),
).toString()

private fun String.parseTextOptions(): LocalTextParseOptions {
    val root = storageJson.parseToJsonElement(this) as? JsonObject ?: error("Invalid TXT options")
    require(root.keys == setOf("charsetName", "headingSelections", "paragraphsPerSection")) { "Invalid TXT options fields" }
    val charsetElement = requireNotNull(root["charsetName"])
    val charsetName = if (charsetElement == JsonNull) null else {
        (charsetElement as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("Invalid TXT charset")
    }
    val headings = (root["headingSelections"] as? JsonArray ?: error("Invalid TXT headings" )).map { item ->
        val heading = item as? JsonObject ?: error("Invalid TXT heading")
        require(heading.keys == setOf("paragraphOrdinal", "title")) { "Invalid TXT heading fields" }
        val ordinal = (heading["paragraphOrdinal"] as? JsonPrimitive)?.takeIf { !it.isString }
            ?.content?.toIntOrNull() ?: error("Invalid TXT heading ordinal")
        val title = (heading["title"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: error("Invalid TXT heading title")
        LocalTextHeading(ordinal, title)
    }
    val perSection = (root["paragraphsPerSection"] as? JsonPrimitive)?.takeIf { !it.isString }
        ?.content?.toIntOrNull() ?: error("Invalid TXT section size")
    return LocalTextParseOptions(charsetName, headings, perSection)
}

private val storageJson = Json

private fun canonicalStringSet(values: Collection<String>): Set<String> = values.mapNotNull { raw ->
    Normalizer.normalize(raw, Normalizer.Form.NFKC).replace(Regex("\\s+"), " ").trim().takeIf(String::isNotEmpty)
}.distinct().sortedWith(::compareUnicodeScalars).toCollection(linkedSetOf())

private fun compareUnicodeScalars(left: String, right: String): Int {
    val leftPoints = left.codePoints().iterator()
    val rightPoints = right.codePoints().iterator()
    while (leftPoints.hasNext() && rightPoints.hasNext()) {
        val comparison = leftPoints.nextInt().compareTo(rightPoints.nextInt())
        if (comparison != 0) return comparison
    }
    return leftPoints.hasNext().compareTo(rightPoints.hasNext())
}

private fun encodeStringSet(values: Set<String>): String = storageJson.encodeToString(
    JsonArray.serializer(),
    JsonArray(values.map(::JsonPrimitive)),
)

private fun decodeStringSet(value: String): Set<String> = runCatching {
    storageJson.parseToJsonElement(value).let { it as JsonArray }.mapTo(linkedSetOf()) { (it as JsonPrimitive).content }
}.getOrDefault(emptySet())

private fun authorSortKey(authors: Set<String>): ByteArray? {
    if (authors.isEmpty()) return null
    val bytes = ArrayList<Byte>()
    authors.forEach { author ->
        author.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8).forEach { byte ->
            if (byte == 0.toByte()) {
                bytes += 0
                bytes += 0xFF.toByte()
            } else {
                bytes += byte
            }
        }
        bytes += 0
        bytes += 0
    }
    return bytes.toByteArray()
}
