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
                sourceUpdateKey = entity.sourceUpdateKey,
                hasUnreadUpdate = entity.hasUnreadUpdate,
                metadataUpdatedAtEpochSecond = entity.metadataUpdatedAtEpochSecond,
                metadataUpdatedAtNano = entity.metadataUpdatedAtNano,
            )
        }
    }

    suspend fun book(identity: BookIdentity): LibraryBook? =
        dao.book(identity.sourceId, identity.remoteBookId)?.toDomain()

    suspend fun libraryEntries(): List<LibraryEntry> = entriesFor(
        dao.libraryBooks().map { BookIdentityRow(it.sourceId, it.remoteBookId) },
    )

    suspend fun entriesFor(identities: List<BookIdentityRow>): List<LibraryEntry> = identities.mapNotNull { identity ->
        val book = dao.book(identity.sourceId, identity.remoteBookId) ?: return@mapNotNull null
        val entry = dao.libraryEntry(identity.sourceId, identity.remoteBookId) ?: return@mapNotNull null
        val availability = dao.sourceAvailability(identity.sourceId)?.available == true
        val tags = dao.localTags(identity.sourceId, identity.remoteBookId).mapTo(linkedSetOf()) { it.displayTag }
        val reconciliation = dao.latestReconciliation(identity.sourceId, identity.remoteBookId)?.state
            ?.let { runCatching { RemoteReconciliationState.valueOf(it) }.getOrNull() }
        LibraryEntry(
            book = book.toDomain(),
            libraryAddedAt = Instant.ofEpochSecond(entry.addedAtEpochSecond, entry.addedAtNano.toLong()),
            rating = entry.rating,
            progress = dao.progress(identity.sourceId, identity.remoteBookId)?.toDomainOrNull(),
            localTags = tags,
            sourceAvailable = availability,
            reconciliation = reconciliation,
        )
    }

    suspend fun addToLibrary(book: LibraryBook): Boolean = database.withTransaction {
        saveBook(book)
        dao.insertLibraryEntry(
            LibraryEntryEntity(
                book.identity.sourceId,
                book.identity.remoteBookId,
                book.addedAt.epochSecond,
                book.addedAt.nano,
                null,
            ),
        ) != -1L
    }

    suspend fun removeFromLibrary(identity: BookIdentity): Boolean =
        dao.deleteLibraryEntry(identity.sourceId, identity.remoteBookId) != 0

    suspend fun setRating(identity: BookIdentity, rating: Int?) {
        require(rating == null || rating in 1..5)
        check(dao.updateRating(identity.sourceId, identity.remoteBookId, rating) == 1) { "Book is not in library" }
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
        sourceUpdateKey = sourceUpdateKey,
        hasUnreadUpdate = hasUnreadUpdate,
        addedAtEpochSecond = addedAt.epochSecond,
        addedAtNano = addedAt.nano,
        metadataUpdatedAtEpochSecond = metadataUpdatedAt.epochSecond,
        metadataUpdatedAtNano = metadataUpdatedAt.nano,
    )
}

private fun BookEntity.toDomain(): LibraryBook {
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
        sourceUpdateKey = sourceUpdateKey,
        hasUnreadUpdate = hasUnreadUpdate,
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
