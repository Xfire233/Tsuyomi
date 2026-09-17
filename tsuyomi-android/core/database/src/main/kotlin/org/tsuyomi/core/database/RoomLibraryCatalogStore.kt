/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.database

import androidx.room.withTransaction
import java.text.Normalizer
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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

internal class RoomLibraryCatalogStore(
    private val database: TsuyomiDatabase,
    private val dao: LibraryDao,
) {
    suspend fun saveBook(book: LibraryBook) {
        val entity = book.toEntity()
        if (dao.insertBook(entity) == -1L) {
            dao.updateBookMetadata(
                sourceId = entity.sourceId,
                remoteBookId = entity.remoteBookId,
                title = entity.title,
                authorsJson = entity.authorsJson,
                authorSortKey = entity.authorSortKey,
                coverUrl = entity.coverUrl,
                canonicalUrl = entity.canonicalUrl,
                status = entity.status,
                remoteTagsJson = entity.remoteTagsJson,
                metadataUpdatedAtEpochSecond = entity.metadataUpdatedAtEpochSecond,
                metadataUpdatedAtNano = entity.metadataUpdatedAtNano,
            )
        }
    }

    /** Retains metadata for local state without changing an existing entry's pin or presentation. */
    suspend fun ensureRetainedLibraryEntry(book: LibraryBook): Boolean = database.withTransaction {
        saveBook(book)
        dao.insertLibraryEntry(
            LibraryEntryEntity(
                sourceId = book.identity.sourceId,
                remoteBookId = book.identity.remoteBookId,
                addedAtEpochSecond = book.addedAt.epochSecond,
                addedAtNano = book.addedAt.nano,
                rating = null,
                locallyPinned = false,
            ),
        ) != -1L
    }

    suspend fun book(identity: BookIdentity): LibraryBook? =
        dao.book(identity.sourceId, identity.remoteBookId)?.toDomain()

    suspend fun libraryEntries(): List<LibraryEntry> = entriesFor(
        dao.libraryBooks().map { BookIdentityRow(it.sourceId, it.remoteBookId) },
    )

    suspend fun readLaterEntries(): List<LibraryEntry> = entriesFor(
        dao.readLaterBooks().map { BookIdentityRow(it.sourceId, it.remoteBookId) },
    )

    suspend fun readerHistoryEntries(): List<LibraryEntry> = dao.readerHistoryRows().mapNotNull { row ->
        val identity = BookIdentity(row.sourceId, row.remoteBookId)
        val fallbackProgress = if (row.explicitVisit) {
            null
        } else {
            dao.progress(row.sourceId, row.remoteBookId)?.toDomainOrNull() ?: return@mapNotNull null
        }
        entryFor(
            identity = identity,
            readerVisitedAt = Instant.ofEpochSecond(row.visitedAtEpochSecond, row.visitedAtNano.toLong()),
            knownProgress = fallbackProgress,
        )
    }

    suspend fun readerHistoryEntry(identity: BookIdentity): LibraryEntry? {
        val visit = dao.readerHistory(identity.sourceId, identity.remoteBookId)
        val fallbackProgress = if (visit == null) {
            dao.progress(identity.sourceId, identity.remoteBookId)?.toDomainOrNull() ?: return null
        } else {
            null
        }
        val visitedAt = visit?.let {
            Instant.ofEpochSecond(it.lastVisitedAtEpochSecond, it.lastVisitedAtNano.toLong())
        } ?: requireNotNull(fallbackProgress).updatedAt
        return entryFor(identity, visitedAt, fallbackProgress)
    }

    suspend fun libraryEntry(identity: BookIdentity): LibraryEntry? = entriesFor(
        listOf(BookIdentityRow(identity.sourceId, identity.remoteBookId)),
    ).singleOrNull()

    suspend fun entriesFor(identities: List<BookIdentityRow>): List<LibraryEntry> =
        identities.mapNotNull { entryFor(BookIdentity(it.sourceId, it.remoteBookId)) }

    private suspend fun entryFor(
        identity: BookIdentity,
        readerVisitedAt: Instant? = null,
        knownProgress: ReadingProgress? = null,
    ): LibraryEntry? {
        val book = dao.book(identity.sourceId, identity.remoteBookId) ?: return null
        val domainBook = book.toDomain()
        val entry = dao.libraryEntry(identity.sourceId, identity.remoteBookId)
        if (entry == null && readerVisitedAt == null) return null
        val availability = dao.sourceAvailability(identity.sourceId)?.available == true
        val tags = dao.localTags(identity.sourceId, identity.remoteBookId).mapTo(linkedSetOf()) { it.displayTag }
        val currentReconciliation = dao.activeReconciliation(identity.sourceId, identity.remoteBookId)
            ?: dao.latestReconciliation(identity.sourceId, identity.remoteBookId)
        val reconciliation = currentReconciliation?.state
            ?.let { runCatching { RemoteReconciliationState.valueOf(it) }.getOrNull() }
        val reconciliationOperation = currentReconciliation?.operation
        return LibraryEntry(
            book = domainBook,
            libraryAddedAt = entry?.let { Instant.ofEpochSecond(it.addedAtEpochSecond, it.addedAtNano.toLong()) }
                ?: domainBook.addedAt,
            rating = entry?.rating,
            readLater = entry?.readLater ?: false,
            progress = knownProgress ?: dao.progress(identity.sourceId, identity.remoteBookId)?.toDomainOrNull(),
            localTags = tags,
            sourceAvailable = availability,
            reconciliation = reconciliation,
            reconciliationOperation = reconciliationOperation,
            readerVisitedAt = readerVisitedAt,
            localMembership = entry?.locallyPinned ?: false,
        )
    }

    suspend fun addToLibrary(book: LibraryBook): Boolean = database.withTransaction {
        saveBook(book)
        dao.insertLibraryEntry(
            LibraryEntryEntity(
                sourceId = book.identity.sourceId,
                remoteBookId = book.identity.remoteBookId,
                addedAtEpochSecond = book.addedAt.epochSecond,
                addedAtNano = book.addedAt.nano,
                rating = null,
            ),
        ) != -1L || dao.pinLibraryEntry(book.identity.sourceId, book.identity.remoteBookId) != 0
    }

    suspend fun removeFromLibrary(identity: BookIdentity): Boolean = database.withTransaction {
        if (dao.unpinLibraryEntry(identity.sourceId, identity.remoteBookId) == 0) return@withTransaction false
        dao.deleteManualMembershipsForLibraryEntry(identity.sourceId, identity.remoteBookId)
        true
    }

    suspend fun removeFromLibrary(identities: Set<BookIdentity>): Int = database.withTransaction {
        identities.count { identity ->
            if (dao.unpinLibraryEntry(identity.sourceId, identity.remoteBookId) == 0) {
                false
            } else {
                dao.deleteManualMembershipsForLibraryEntry(identity.sourceId, identity.remoteBookId)
                true
            }
        }
    }

    suspend fun reorderLibrary(identities: List<BookIdentity>) = database.withTransaction {
        val current = dao.libraryBooks().map { BookIdentity(it.sourceId, it.remoteBookId) }
        require(identities.size == current.size && identities.toSet() == current.toSet()) {
            "Library reorder must contain every current entry exactly once"
        }
        identities.forEachIndexed { index, identity ->
            check(dao.updateLibraryDisplayOrder(identity.sourceId, identity.remoteBookId, index) == 1)
        }
    }

    suspend fun setRating(identity: BookIdentity, rating: Int?) {
        require(rating == null || rating in 1..5)
        check(dao.updateRating(identity.sourceId, identity.remoteBookId, rating) == 1) { "Book is not in library" }
    }

    suspend fun setReadLater(identity: BookIdentity, readLater: Boolean) {
        check(dao.updateReadLater(identity.sourceId, identity.remoteBookId, readLater) == 1) {
            "Book is not in library"
        }
    }

    suspend fun setLocalTags(identity: BookIdentity, tags: Collection<String>) = database.withTransaction {
        check(dao.libraryEntry(identity.sourceId, identity.remoteBookId) != null) { "Book is not in library" }
        val normalized = tags.mapNotNull { raw ->
            val display = raw.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val key = Normalizer.normalize(display, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            key to display
        }.distinctBy { it.first }
        require(normalized.size <= 64)
        dao.deleteLocalTags(identity.sourceId, identity.remoteBookId)
        dao.insertLocalTags(
            normalized.map { (key, display) ->
                LocalBookTagEntity(identity.sourceId, identity.remoteBookId, key, display)
            },
        )
    }
}

private fun LibraryBook.toEntity(): BookEntity {
    val canonicalAuthors = canonicalStringSet(authors.ifEmpty { author?.let(::setOf).orEmpty() })
    return BookEntity(
        sourceId = identity.sourceId,
        remoteBookId = identity.remoteBookId,
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
    )
}

internal fun BookEntity.toDomain(): LibraryBook {
    val authors = decodeStringSet(authorsJson)
    return LibraryBook(
        identity = BookIdentity(sourceId, remoteBookId),
        title = title,
        author = authors.firstOrNull(),
        authors = authors,
        coverUrl = coverUrl,
        canonicalUrl = canonicalUrl,
        status = status,
        remoteTags = decodeStringSet(remoteTagsJson),
        addedAt = Instant.ofEpochSecond(addedAtEpochSecond, addedAtNano.toLong()),
        metadataUpdatedAt = Instant.ofEpochSecond(metadataUpdatedAtEpochSecond, metadataUpdatedAtNano.toLong()),
    )
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
