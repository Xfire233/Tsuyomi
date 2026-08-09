/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.android

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.tsuyomi.shared.model.BookIdentity
import org.tsuyomi.shared.sourcecontract.SourceBookSummary
import org.tsuyomi.shared.sourcecontract.SourceChapter

data class SourceFlowSnapshot(
    val book: SourceBookSummary,
    val chapter: SourceChapter?,
)

/** Non-secret navigation target. Content and progress remain in their dedicated stores. */
class SourceFlowSnapshotStore(private val dataStore: DataStore<Preferences>) {
    suspend fun saveBook(book: SourceBookSummary) {
        dataStore.edit { values ->
            values[SourceId] = book.identity.sourceId
            values[RemoteBookId] = book.identity.remoteBookId
            values[BookTitle] = book.title
            putOptional(values, BookAuthor, book.author)
            putOptional(values, CoverUrl, book.coverUrl)
            values[CanonicalUrl] = book.canonicalUrl
            values.remove(ChapterId)
            values.remove(ChapterTitle)
            values.remove(ChapterUrl)
        }
    }

    suspend fun saveChapter(chapter: SourceChapter) {
        dataStore.edit { values ->
            values[ChapterId] = chapter.chapterId
            values[ChapterTitle] = chapter.title
            values[ChapterUrl] = chapter.url
        }
    }

    suspend fun read(sourceId: String): SourceFlowSnapshot? = dataStore.data.map { values ->
        if (values[SourceId] != sourceId) return@map null
        val remoteBookId = values[RemoteBookId] ?: return@map null
        val title = values[BookTitle] ?: return@map null
        val canonicalUrl = values[CanonicalUrl] ?: return@map null
        val book = SourceBookSummary(
            identity = BookIdentity(sourceId, remoteBookId),
            title = title,
            author = values[BookAuthor],
            coverUrl = values[CoverUrl],
            canonicalUrl = canonicalUrl,
        )
        val chapterId = values[ChapterId]
        val chapter = if (chapterId == null) null else SourceChapter(
            chapterId = chapterId,
            title = values[ChapterTitle] ?: return@map null,
            url = values[ChapterUrl] ?: return@map null,
        )
        SourceFlowSnapshot(book, chapter)
    }.first()

    private fun putOptional(values: androidx.datastore.preferences.core.MutablePreferences, key: Preferences.Key<String>, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    private companion object {
        val SourceId = stringPreferencesKey("source_flow_source_id")
        val RemoteBookId = stringPreferencesKey("source_flow_remote_book_id")
        val BookTitle = stringPreferencesKey("source_flow_book_title")
        val BookAuthor = stringPreferencesKey("source_flow_book_author")
        val CoverUrl = stringPreferencesKey("source_flow_cover_url")
        val CanonicalUrl = stringPreferencesKey("source_flow_canonical_url")
        val ChapterId = stringPreferencesKey("source_flow_chapter_id")
        val ChapterTitle = stringPreferencesKey("source_flow_chapter_title")
        val ChapterUrl = stringPreferencesKey("source_flow_chapter_url")
    }
}
